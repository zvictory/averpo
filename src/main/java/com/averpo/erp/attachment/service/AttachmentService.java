package com.averpo.erp.attachment.service;

import com.averpo.erp.attachment.domain.Attachment;
import com.averpo.erp.attachment.repo.AttachmentRepository;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.Uuid7;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AttachmentService - ҳужжат иловаларининг ягона public API'си
 * (docs/modules/attachments.md). GL'га мутлақо тегмайди: PostingService
 * умуман import ҚИЛИНМАЙДИ (spec'нинг review нуқтаси) - илова соф ҳужжат
 * иловаси, темир қоида №3 бузилмайди.
 *
 * <p>Сақлаш иккига бўлинган: файлнинг ЎЗИ локал дискда
 * ({@code app.attachments.dir}), базада фақат метамаълумот -
 * backup/қувват оғирлашмайди. Диск номи ({@code storedPath}) ФАҚАТ
 * сервер яратган UUID (йил/ой/UUID.ext): фойдаланувчи киритган ном диск
 * йўлига ҲЕЧ ҚАЧОН кирмайди (path traversal ҳимояси) - {@link #resolve}
 * ҳар йўлни базавий каталог ичида қолишини қўшимча текширади.
 *
 * <p>Target ҳужжат мавжудлиги (BR-ATT-003) ҳар модул repository'сига
 * қўл узатмасдан текширилади: DocumentType жадвал номига map қилинади ва
 * битта JdbcClient {@code SELECT EXISTS} юборилади (LedgerDashboardService
 * хом SQL прецеденти, темир қоида №6 - модуллараро боғланиш йўқ).
 *
 * @author Zafar
 */
@Service
@Transactional
public class AttachmentService {

    /** Илова юклаш чегараси: 20MB (QBO паритети, BR-ATT-001). */
    public static final long MAX_BYTES = 20L * 1024 * 1024;

    /**
     * Расм (аватар/лого) юклаш чегараси: 2MB (BR-ATT-006, Arbitr-101/112) -
     * умумий 20MB'дан қатъийроқ, расмлар кичик бўлгани учун.
     */
    public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

    /**
     * Расм учун рухсат этилган MIME турлар (BR-ATT-005): inline
     * кўрсатилгани учун фақат хавфсиз растр - SVG АТАЙЛАБ ЙЎҚ ({@code
     * <script>} XSS хавфи, AttachmentController.INLINE_VIEWABLE_TYPES
     * билан бир мантиқ). Лоуер-кейс солиштирилади.
     */
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp");

    /** Расм учун рухсат этилган кенгайтмалар (диск номи + defense-in-depth). */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp");

    /**
     * Расм ўлчови қабул диапазони (BR-ATT-007): жуда кичик/катта рад.
     * PUBLIC - JTE helper матнлари максимумни ({@link #MAX_IMAGE_PX})
     * шу манбадан ўқийди (drift'сиз, қўлда «4096» такрорланмайди).
     */
    public static final int MIN_IMAGE_PX = 16;
    public static final int MAX_IMAGE_PX = 4096;

    /**
     * Рухсат этилган кенгайтмалар (регистрсиз, BR-ATT-002) -
     * attachments.md «Чеклов»даги рўйхат. Бажарувчи файллар (exe, sh...)
     * атайлаб йўқ.
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "webp", "gif",
            "xlsx", "xls", "docx", "doc", "csv", "txt", "zip");

    /**
     * DocumentType → DB жадвал номи (target мавжудлиги, BR-ATT-003).
     * Жадвал номи ФАҚАТ шу картадан хом SQL'га қўшилади - фойдаланувчи
     * стринги ҲЕЧ ҚАЧОН тушмайди (тур enum {@code @PathVariable}
     * сифатида келади, номаълуми web қатламида рад бўлади): SQL инъекция
     * майдони йўқ. Картада йўқ тур - бириктириб бўлмайди (BR-ATT-003).
     */
    private static final Map<DocumentType, String> DOCUMENT_TABLES = Map.ofEntries(
            Map.entry(DocumentType.INVOICE, "invoice"),
            Map.entry(DocumentType.BILL, "bill"),
            Map.entry(DocumentType.JOURNAL_ENTRY, "journal_entry"),
            Map.entry(DocumentType.BANK_TXN, "bank_transaction"),
            Map.entry(DocumentType.CREDIT_MEMO, "credit_memo"),
            Map.entry(DocumentType.VENDOR_CREDIT, "vendor_credit"),
            Map.entry(DocumentType.REFUND_RECEIPT, "refund_receipt"),
            Map.entry(DocumentType.ESTIMATE, "estimate"),
            Map.entry(DocumentType.PURCHASE_ORDER, "purchase_order"),
            // Arbitr-048: қолган транзакция турлари (кўришларига «Иловалар»
            // уланди). Жадвал номлари entity @Table'дан: LANDED_COST →
            // landed_cost_allocation (LC ҳужжати = тақсимот), RECEIPT →
            // invoice_payment (мижоз тушуми), PAYMENT → bill_payment.
            Map.entry(DocumentType.SALES_RECEIPT, "sales_receipt"),
            Map.entry(DocumentType.RECEIPT, "invoice_payment"),
            Map.entry(DocumentType.PAYMENT, "bill_payment"),
            Map.entry(DocumentType.LANDED_COST, "landed_cost_allocation"),
            Map.entry(DocumentType.PAYROLL_RUN, "payroll_run"),
            Map.entry(DocumentType.PAYROLL_PAYMENT, "payroll_payment"),
            // Arbitr-093: ҳужжатли инвентаризация/кўчириш актлари (view'ларига
            // «Иловалар» уланган - жадвал номлари entity @Table'дан).
            Map.entry(DocumentType.STOCK_ADJUSTMENT, "stock_adjustment"),
            Map.entry(DocumentType.STOCK_TRANSFER, "stock_transfer"),
            // Arbitr-101/112: профиль расми (аватар) ва компания логоси -
            // Attachment инфрасини қайта ишлатади (disk storage + path
            // traversal ҳимояси). Target жадваллари: app_user (ҳар user'да
            // profile_image_id FK), company_settings (singleton logo).
            Map.entry(DocumentType.APP_USER, "app_user"),
            Map.entry(DocumentType.COMPANY, "company_settings"));

    /**
     * Илова бириктириш қўллаб-қувватланадиган ҳужжат турлари
     * (DOCUMENT_TABLES калитлари) - тест map қамровини текшириши учун
     * (бошқа пакетдаги тестдан кўринсин).
     */
    public static java.util.Set<DocumentType> supportedDocumentTypes() {
        return DOCUMENT_TABLES.keySet();
    }

    /** Метамаълумот сақлагич. */
    private final AttachmentRepository repository;

    /** Target мавжудлиги учун хом SQL EXISTS (LedgerDashboardService прецеденти). */
    private final JdbcClient jdbc;

    /** Файллар сақланадиган базавий каталог (серверда конфиг билан алоҳида йўл). */
    private final String attachmentsDir;

    /**
     * @param attachmentsDir {@code app.attachments.dir} (default
     *        {@code ./attachments}) - @RequiredArgsConstructor ўрнига
     *        қўлда: @Value final майдонга конструктор орқали киради.
     */
    public AttachmentService(AttachmentRepository repository, JdbcClient jdbc,
                             @Value("${app.attachments.dir:./attachments}") String attachmentsDir) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.attachmentsDir = attachmentsDir;
    }

    /**
     * Юклаб олиш натижаси: диск ресурси + Content-Disposition учун асл
     * ном + Content-Type (метамаълумотдан). Ресурс дискдан ўқилади -
     * tx ёпилгач ҳам ишлайди (lazy JPA эмас).
     */
    public record Download(Resource resource, String filename, String contentType) { }

    /** Ҳужжат иловалари, янгидан эскига (кўриш экранининг «Иловалар» бўлими). */
    @Transactional(readOnly = true)
    public List<Attachment> list(DocumentType documentType, UUID documentId) {
        return repository.findByDocumentTypeAndDocumentIdOrderByCreatedAtDesc(
                documentType, documentId);
    }

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Attachment get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Илова топилмади: " + id));
    }

    /**
     * Файл юклаш: валидация (BR-ATT-002 кенгайтма, BR-ATT-001 ҳажм,
     * BR-ATT-003 target) → метамаълумот сақлаш → дискка ёзиш. Диск
     * ёзуви ОХИРГИ қадам: IO хатоси @Transactional орқали DB ёзувини
     * ортга қайтаради (осилиб қолган ёзув бўлмайди).
     *
     * @throws BusinessRuleException BR-ATT-001/002/003
     */
    public Attachment upload(DocumentType documentType, UUID documentId, MultipartFile file) {
        String originalName = cleanOriginalName(file);
        String extension = requireAllowedExtension(originalName);
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_001,
                    "Файл ҳажми 20MB дан ошмаслиги шарт: " + originalName);
        }
        if (!targetExists(documentType, documentId)) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_003,
                    "Илова бириктириладиган ҳужжат мавжуд эмас: "
                    + documentType + " " + documentId);
        }
        // Диск номи фақат сервер UUID - фойдаланувчи номи йўлга кирмайди
        String storedPath = buildStoredPath(extension);
        Attachment attachment = new Attachment(documentType, documentId, originalName,
                storedPath, contentTypeOrDefault(file), file.getSize());
        repository.saveAndFlush(attachment);
        writeToDisk(file, storedPath);
        return attachment;
    }

    /**
     * Расм юклаш (профиль аватари / компания логоси, Arbitr-101/112):
     * умумий {@link #upload}'дан ТОР allowlist билан фарқ қилади - фақат
     * png/jpeg/webp (BR-ATT-005: SVG inline XSS'дан ҳимоя) ва 2MB
     * (BR-ATT-006). Қолган оқим айнан бир хил: BR-ATT-003 target
     * текшируви, диск ёзуви path traversal ҳимояси билан. Натижавий
     * {@link Attachment}'нинг id'сини чақирувчи эга entity FK'сига
     * (profile_image_id / logo_attachment_id) ёзади; эски расм бўлса
     * уни алоҳида {@link #delete} қилади (orphan қолмасин).
     *
     * @throws BusinessRuleException BR-ATT-005 (тур/кенгайтма),
     *         BR-ATT-006 (ҳажм), BR-ATT-003 (target йўқ)
     */
    public Attachment uploadImage(DocumentType documentType, UUID documentId,
                                  MultipartFile file) {
        String originalName = cleanOriginalName(file);
        String extension = requireImageExtension(originalName);
        String contentType = requireImageType(file);
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_006,
                    "Расм ҳажми 2MB дан ошмаслиги шарт: " + originalName);
        }
        requireImageDimensions(file, contentType);
        if (!targetExists(documentType, documentId)) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_003,
                    "Илова бириктириладиган ҳужжат мавжуд эмас: "
                    + documentType + " " + documentId);
        }
        String storedPath = buildStoredPath(extension);
        Attachment attachment = new Attachment(documentType, documentId, originalName,
                storedPath, contentType, file.getSize());
        repository.saveAndFlush(attachment);
        writeToDisk(file, storedPath);
        return attachment;
    }

    /**
     * Юклаб олиш: диск ресурси + асл ном. Файл дискда йўқ бўлса (база
     * ёзуви бор, файл ўчган) 404 - «бор» деб ёлғон қатор бермайди.
     */
    @Transactional(readOnly = true)
    public Download download(UUID id) {
        Attachment attachment = get(id);
        Path path = resolve(attachment.getStoredPath());
        if (!Files.isReadable(path)) {
            throw new NotFoundException(
                    "Илова файли дискда топилмади: " + attachment.getOriginalName());
        }
        return new Download(new FileSystemResource(path), attachment.getOriginalName(),
                attachment.getContentType());
    }

    /**
     * Ўчириш қатъий: база ёзуви ва диск файли БИРГА кетади. DB ёзуви
     * аввал flush қилинади - диск ўчиши хато берса @Transactional ёзувни
     * қайтаради (икковининг мувофиқлиги сақланади).
     */
    public void delete(UUID id) {
        Attachment attachment = get(id);
        repository.delete(attachment);
        repository.flush();
        deleteFromDisk(attachment.getStoredPath());
    }

    // ---- ички ёрдамчилар ----

    /**
     * Target мавжудлиги: DocumentType жадвалига map қилиниб битта
     * EXISTS юборилади. Картада йўқ тур - қўллаб-қувватланмайди
     * (false → BR-ATT-003).
     */
    private boolean targetExists(DocumentType documentType, UUID documentId) {
        String table = DOCUMENT_TABLES.get(documentType);
        if (table == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc
                .sql("SELECT EXISTS(SELECT 1 FROM " + table + " WHERE id = :id)")
                .param("id", documentId)
                .query(Boolean.class)
                .single());
    }

    /**
     * Асл файл номини тозалайди: йўл қисмлари (папка/{@code ../})
     * ташланади, фақат base ном қолади - Content-Disposition'да ва
     * кўрсатишда хавфсиз. Бўш бўлса BR-ATT-002.
     */
    private String cleanOriginalName(MultipartFile file) {
        String raw = file.getOriginalFilename();
        String cleaned = StringUtils.getFilename(
                StringUtils.cleanPath(raw == null ? "" : raw));
        if (cleaned == null || cleaned.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_002, "Файл номи бўш");
        }
        return cleaned;
    }

    /** Кенгайтма allowlist'да бўлиши шарт (регистрсиз) - BR-ATT-002. */
    private String requireAllowedExtension(String originalName) {
        String ext = StringUtils.getFilenameExtension(originalName);
        ext = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_002,
                    "Рухсат этилмаган файл тури: " + originalName);
        }
        return ext;
    }

    /** Расм кенгайтмаси png/jpg/jpeg/webp бўлиши шарт (BR-ATT-005) - диск номи учун. */
    private String requireImageExtension(String originalName) {
        String ext = StringUtils.getFilenameExtension(originalName);
        ext = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(ext)) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_005,
                    "Расм кенгайтмаси png/jpg/jpeg/webp бўлиши шарт: " + originalName);
        }
        return ext;
    }

    /**
     * MIME тури png/jpeg/webp бўлиши шарт (BR-ATT-005) - inline
     * кўрсатишда хавфсиз. Параметрлар ({@code ;charset=}) ташланади,
     * регистр нормаллаштирилади; null/бўш - рад. Тасдиқланган қийматни
     * қайтаради (устун 100 ичида, барчаси қисқа).
     */
    private String requireImageType(MultipartFile file) {
        String raw = file.getContentType();
        String base = raw == null ? "" : raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(base)) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_005,
                    "Расм тури PNG, JPEG ёки WEBP бўлиши шарт: " + raw);
        }
        return base;
    }

    /**
     * Расм ЎЛЧОВини текширади (BR-ATT-007, Arbitr-101/112 рефайнмент):
     * ImageIO билан ўқиб кенглик×баландлик диапазонда эканини (жуда кичик/
     * катта эмас) кафолатлайди. Умумий валидатор - аватар, company logo,
     * brand logo учун бир хил.
     *
     * <p>ТУЗОҚ: стандарт Java ImageIO WEBP'ни ЎҚИЙ ОЛМАЙДИ (плагин йўқ,
     * build.gradle тегилмайди). Шунинг учун WEBP null қайтарса рад ЭМАС -
     * content-type ва ҳажм (BR-ATT-005/006) аллақачон текширилган, ўлчов
     * текширилмай ўтади. PNG/JPEG null бўлса эса бузуқ/расм эмас - рад.
     */
    private void requireImageDimensions(MultipartFile file, String contentType) {
        java.awt.image.BufferedImage image;
        try (InputStream in = file.getInputStream()) {
            image = javax.imageio.ImageIO.read(in);
        } catch (IOException e) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_007,
                    "Расмни ўқиб бўлмади");
        }
        if (image == null) {
            if ("image/webp".equals(contentType)) {
                return; // WEBP - ImageIO плагинсиз ўқий олмайди, ўтказамиз
            }
            throw new BusinessRuleException(BusinessRule.BR_ATT_007,
                    "Файл расм эмас ёки бузуқ");
        }
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < MIN_IMAGE_PX || h < MIN_IMAGE_PX
                || w > MAX_IMAGE_PX || h > MAX_IMAGE_PX) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_007,
                    "Расм ўлчови " + MIN_IMAGE_PX + ".." + MAX_IMAGE_PX
                    + "px оралиғида бўлиши шарт: " + w + "x" + h);
        }
    }

    /**
     * Диск йўлини ясайди: йил/ой/UUID.ext. UUID сервер тарафда
     * генерацияланади (Uuid7) - фойдаланувчи номидан мутлақо мустақил,
     * шунда path traversal имконсиз ва номлар тўқнашмайди.
     */
    private String buildStoredPath(String extension) {
        // Бакетлаш санаси - UTC (created_at UTC билан мос); соф диск
        // ташкили, бизнес санаси эмас
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return String.format("%04d/%02d/%s.%s", today.getYear(),
                today.getMonthValue(), Uuid7.next(), extension);
    }

    /** MIME тури (browser'дан); йўқ/узун бўлса default/қирқилган - устун 100. */
    private String contentTypeOrDefault(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.length() > 100 ? contentType.substring(0, 100) : contentType;
    }

    /**
     * Нисбий йўлни абсолют қилади ва базавий каталог ИЧИДА қолишини
     * текширади (defense in depth: storedPath доим сервер UUID бўлса-да,
     * ҳар қандай {@code ../} чиқиб кетишни рад қилади).
     */
    private Path resolve(String storedPath) {
        Path base = Path.of(attachmentsDir).toAbsolutePath().normalize();
        Path target = base.resolve(storedPath).normalize();
        if (!target.startsWith(base)) {
            throw new BusinessRuleException(BusinessRule.BR_ATT_002,
                    "Хавфсиз бўлмаган файл йўли");
        }
        return target;
    }

    /** Дискка ёзади (папкаларни яратиб). IO хатоси - unchecked (tx rollback). */
    private void writeToDisk(MultipartFile file, String storedPath) {
        Path target = resolve(storedPath);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Илова файлини дискка ёзиб бўлмади: " + storedPath, e);
        }
    }

    /** Диск файлини ўчиради (йўқ бўлса жим ўтади). IO хатоси - unchecked (tx rollback). */
    private void deleteFromDisk(String storedPath) {
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Илова файлини дискдан ўчириб бўлмади: " + storedPath, e);
        }
    }
}
