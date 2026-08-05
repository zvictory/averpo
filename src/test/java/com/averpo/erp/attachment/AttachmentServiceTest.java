package com.averpo.erp.attachment;

import com.averpo.erp.attachment.domain.Attachment;
import com.averpo.erp.attachment.repo.AttachmentRepository;
import com.averpo.erp.attachment.service.AttachmentService;
import com.averpo.erp.attachment.service.AttachmentService.Download;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.inventory.domain.StockAdjustment;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.Estimate;
import com.averpo.erp.sales.service.EstimateService;
import com.averpo.erp.sales.service.EstimateService.EstimateData;
import com.averpo.erp.sales.service.EstimateService.LineData;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.tax.service.TaxRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AttachmentService тестлари (docs/modules/attachments.md «Тестлар»
 * мажбурий рўйхати - роллардан бошқаси; 5-банд web тестда). Target
 * ҳужжат сифатида Estimate ишлатилади (GL'сиз - journal_entry сонига
 * таъсир қилмайди, шунда 7-банд assert'и тоза). PostingService
 * УМУМАН ишлатилмайди (attachment модули GL'сизлиги).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttachmentServiceTest {

    /** Барча fixture санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired AttachmentService attachmentService;
    @Autowired AttachmentRepository attachmentRepository;
    @Autowired EstimateService estimateService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired TaxRateService taxRateService;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;

    /** Тест профилидаги илова каталоги (9-банд текшируви + диск assert'лари). */
    @Value("${app.attachments.dir}")
    String attachmentsDir;

    /** Илова бириктириладиган мавжуд target ҳужжат. */
    private Estimate target;

    /** Chart + мижоз + SERVICE item + ставка + битта estimate тайёрлайди. */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Илова тест мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        Item service = itemService.create(ItemType.SERVICE, new ItemData(
                "Илова хизмати", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        var vat = taxRateService.create("VATATT", "ҚҚС (att)", new BigDecimal("12"));
        target = estimateService.create(new EstimateData(customer.getId(), DATE,
                DATE.plusDays(30), null, null, "target", false,
                List.of(new LineData(service.getId(), BigDecimal.ONE,
                        new BigDecimal("1000"), null, vat.getId(), null))));
    }

    /** pdf файлни ясаш ёрдамчиси. */
    private MultipartFile pdf(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }

    /** Расм файлни ясаш ёрдамчиси (аватар/лого тестлари). */
    private MultipartFile image(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    /** Метамаълумотдан диск йўлини қайта тиклайди (сервисдаги resolve нақши). */
    private Path resolved(Attachment attachment) {
        return Path.of(attachmentsDir).toAbsolutePath().normalize()
                .resolve(attachment.getStoredPath()).normalize();
    }

    /**
     * Arbitr-052 (041): resolve() иккинчи (defense-in-depth) traversal
     * ҳимояси - хавфли stored_path'ли ёзув (кўп ../) билан download ҲАМ
     * delete ҲАМ BR-ATT-002 отади (normalize + base'дан чиқмаслик текшируви).
     * Одатда storedPath сервер UUID, лекин бузилган ёзув/миграцияда база
     * ичига хавфли йўл тушса ҳам файл тизимидан чиқиб кетиб бўлмайди.
     */
    @Test
    void maliciousStoredPath_downloadAndDelete_rejectedAtt002() {
        Attachment evil = attachmentRepository.saveAndFlush(new Attachment(
                DocumentType.ESTIMATE, target.getId(), "evil.pdf",
                "../../../../../../etc/passwd", "application/pdf", 10));

        assertThatThrownBy(() -> attachmentService.download(evil.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-ATT-002"));
        assertThatThrownBy(() -> attachmentService.delete(evil.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-ATT-002"));
    }

    /** Spec 1-банд: upload → дискда файл + база ёзуви тўғри; download асл ном. */
    @Test
    void upload_writesDiskAndDbRow_downloadReturnsOriginalName() throws Exception {
        byte[] content = "salom pdf".getBytes();
        Attachment saved = attachmentService.upload(DocumentType.ESTIMATE, target.getId(),
                pdf("hisob-faktura.pdf", content));

        // База ёзуви тўғри
        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.ESTIMATE);
        assertThat(saved.getDocumentId()).isEqualTo(target.getId());
        assertThat(saved.getOriginalName()).isEqualTo("hisob-faktura.pdf");
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getSizeBytes()).isEqualTo(content.length);
        assertThat(attachmentRepository.findById(saved.getId())).isPresent();
        assertThat(attachmentService.list(DocumentType.ESTIMATE, target.getId()))
                .extracting(Attachment::getId).containsExactly(saved.getId());

        // Дискда файл айнан шу мазмун билан бор
        assertThat(Files.exists(resolved(saved))).isTrue();
        assertThat(Files.readAllBytes(resolved(saved))).isEqualTo(content);

        // download асл ном + мазмунни қайтаради
        Download download = attachmentService.download(saved.getId());
        assertThat(download.filename()).isEqualTo("hisob-faktura.pdf");
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.resource().getContentAsByteArray()).isEqualTo(content);
    }

    /** Spec 2-банд: 20MB дан катта файл рад (BR-ATT-001). */
    @Test
    void oversizeFile_rejected() {
        // getSize() ни override - 20MB массив ажратмасдан катта ҳажм имитацияси
        MultipartFile huge = new MockMultipartFile("file", "katta.pdf",
                "application/pdf", new byte[]{1}) {
            @Override
            public long getSize() {
                return AttachmentService.MAX_BYTES + 1;
            }
        };
        assertThatThrownBy(() -> attachmentService.upload(DocumentType.ESTIMATE,
                target.getId(), huge))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-ATT-001"));
    }

    /** Spec 3-банд: рухсатсиз кенгайтма рад (BR-ATT-002). */
    @Test
    void disallowedExtension_rejected() {
        MultipartFile exe = new MockMultipartFile("file", "virus.exe",
                "application/octet-stream", "MZ".getBytes());
        assertThatThrownBy(() -> attachmentService.upload(DocumentType.ESTIMATE,
                target.getId(), exe))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-ATT-002"));
    }

    /** Spec 4-банд: мавжуд бўлмаган target рад (BR-ATT-003). */
    @Test
    void missingTarget_rejected() {
        assertThatThrownBy(() -> attachmentService.upload(DocumentType.ESTIMATE,
                UUID.randomUUID(), pdf("x.pdf", "x".getBytes())))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-ATT-003"));
    }

    /** Spec 6-банд: delete базадан ҲАМ дискдан ҲАМ ўчиради. */
    @Test
    void delete_removesDbAndDisk() {
        Attachment saved = attachmentService.upload(DocumentType.ESTIMATE,
                target.getId(), pdf("shartnoma.pdf", "hujjat".getBytes()));
        Path path = resolved(saved);
        assertThat(Files.exists(path)).isTrue();

        attachmentService.delete(saved.getId());

        assertThat(attachmentRepository.findById(saved.getId())).isEmpty();
        assertThat(Files.exists(path)).isFalse();
    }

    /** Spec 7-банд: upload/delete'да journal_entry сони ЎЗГАРМАЙДИ (GL'сизлик). */
    @Test
    void uploadDelete_journalEntryCountUnchanged() {
        long entriesBefore = entryRepository.count();

        Attachment saved = attachmentService.upload(DocumentType.ESTIMATE,
                target.getId(), pdf("skan.pdf", "skan".getBytes()));
        assertThat(entryRepository.count()).isEqualTo(entriesBefore);

        attachmentService.delete(saved.getId());
        assertThat(entryRepository.count()).isEqualTo(entriesBefore);
    }

    /**
     * Spec 8-банд: original_name'да {@code ../} бўлса ҳам stored_path
     * сервер UUID қолипида (path traversal ҳимояси) - фойдаланувчи номи
     * диск йўлига мутлақо кирмайди.
     */
    @Test
    void pathTraversal_originalNameNeverEntersStoredPath() {
        Attachment saved = attachmentService.upload(DocumentType.ESTIMATE, target.getId(),
                pdf("../../../etc/passwd.pdf", "evil".getBytes()));

        // stored_path - фақат йил/ой/UUID.ext
        assertThat(saved.getStoredPath()).matches(
                "\\d{4}/\\d{2}/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.pdf");
        assertThat(saved.getStoredPath()).doesNotContain("..").doesNotContain("passwd");
        // Асл номдаги йўл қисмлари тозаланган (base ном қолади)
        assertThat(saved.getOriginalName()).isEqualTo("passwd.pdf");
        // Диск файли базавий каталог ичида (java.nio startsWith - соф
        // лексик, AssertJ Path assert toRealPath қилиб IO'га тегади)
        Path base = Path.of(attachmentsDir).toAbsolutePath().normalize();
        assertThat(resolved(saved).startsWith(base)).isTrue();
    }

    /** Spec 9-банд: тест профилида каталог build/ ости (лойиҳа папкаси тоза). */
    @Test
    void testProfile_attachmentsDirUnderBuild() {
        assertThat(attachmentsDir).startsWith("build");
        // Path.startsWith - соф лексик (файл тизимига тегмайди); AssertJ'нинг
        // assertThat(Path).startsWith'и toRealPath қилиб мавжуд бўлмаган
        // каталогда NoSuchFileException берарди
        Path base = Path.of(attachmentsDir).toAbsolutePath().normalize();
        Path buildDir = Path.of("build").toAbsolutePath().normalize();
        assertThat(base.startsWith(buildDir)).isTrue();
    }

    /**
     * Arbitr-048: янги уланган турлар DOCUMENT_TABLES'да бор ва жадвал
     * номи ҲАҚИҚИЙ (EXISTS сўрови ишлайди). Мавжуд бўлмаган target →
     * BR-ATT-003 (жадвал номи типоси бўлса SQL exception берарди, BR
     * эмас). Ижобий upload→list→download йўли мавжуд ESTIMATE тести
     * билан бир хил - DOCUMENT_TABLES жадвал номидан бошқа фарқ йўқ.
     */
    @ParameterizedTest
    @EnumSource(names = {"SALES_RECEIPT", "RECEIPT", "PAYMENT",
            "LANDED_COST", "PAYROLL_RUN", "PAYROLL_PAYMENT",
            "STOCK_ADJUSTMENT", "STOCK_TRANSFER"})
    void newDocumentTypes_mappedAndTableQueryable(DocumentType type) {
        assertThat(AttachmentService.supportedDocumentTypes()).contains(type);
        assertThatThrownBy(() -> attachmentService.upload(type, UUID.randomUUID(),
                pdf("x.pdf", "x".getBytes())))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-ATT-003"));
    }

    /**
     * Arbitr-093: РЕАЛ инвентаризация актига upload муваффақияти -
     * STOCK_ADJUSTMENT → stock_adjustment map тўғри (target мавжуд,
     * BR-ATT-003 ОТИЛМАЙДИ). Юқоридаги параметрланган тест манфий
     * (мавжуд эмас target)ни, бу тест ижобий йўлни қоплайди.
     */
    @Test
    void upload_toRealStockAdjustment_succeeds() {
        ItemService.DefaultAccounts inv = itemService.defaultsFor(ItemType.INVENTORY);
        Item invItem = itemService.create(ItemType.INVENTORY, new ItemData(
                "Илова омбор товари", null, null, null, null, null,
                inv.income(), null, null, inv.expense(), inv.inventoryAsset(), null));
        Warehouse warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName())).findFirst().orElseThrow();
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "SEED", null, null);
        StockAdjustment act = inventoryService.adjustDocument(
                new InventoryService.DocumentAdjustData(warehouse.getId(), DATE, null,
                        List.of(new InventoryService.AdjustLineData(invItem.getId(),
                                new BigDecimal("12"), null, null))));

        Attachment saved = attachmentService.upload(DocumentType.STOCK_ADJUSTMENT,
                act.getId(), pdf("akt.pdf", "akt".getBytes()));

        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.STOCK_ADJUSTMENT);
        assertThat(saved.getDocumentId()).isEqualTo(act.getId());
        assertThat(saved.getOriginalName()).isEqualTo("akt.pdf");
    }

    /**
     * Arbitr-101/112: uploadImage тўғри png'ни сақлайди - content_type
     * ва диск номи кенгайтмаси мос. Target ESTIMATE (расм валидацияси
     * target-агностик - BR-ATT-005/006 target текширувидан ОЛДИН);
     * реал аватар/лого оқими UserService/CompanyInfoController тестларида.
     */
    @Test
    void uploadImage_validPng_succeeds() throws Exception {
        Attachment saved = attachmentService.uploadImage(DocumentType.ESTIMATE,
                target.getId(), image("avatar.png", "image/png", pngBytes(64, 64)));

        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getStoredPath()).endsWith(".png");
        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.ESTIMATE);
    }

    /** Arbitr-101/112: SVG рад (BR-ATT-005 - inline XSS ҳимояси). */
    @Test
    void uploadImage_svg_rejectedAtt005() {
        assertThatThrownBy(() -> attachmentService.uploadImage(DocumentType.ESTIMATE,
                target.getId(), image("logo.svg", "image/svg+xml", "<svg/>".getBytes())))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-ATT-005"));
    }

    /** Arbitr-101/112: png кенгайтма, лекин MIME png эмас - BR-ATT-005 (тур текшируви). */
    @Test
    void uploadImage_wrongContentType_rejectedAtt005() {
        assertThatThrownBy(() -> attachmentService.uploadImage(DocumentType.ESTIMATE,
                target.getId(), image("avatar.png", "application/pdf", "x".getBytes())))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-ATT-005"));
    }

    /** Arbitr-101/112: 2MB дан катта расм рад (BR-ATT-006). */
    @Test
    void uploadImage_oversize_rejectedAtt006() {
        MultipartFile huge = new MockMultipartFile("file", "big.png",
                "image/png", new byte[]{1}) {
            @Override
            public long getSize() {
                return AttachmentService.MAX_IMAGE_BYTES + 1;
            }
        };
        assertThatThrownBy(() -> attachmentService.uploadImage(DocumentType.ESTIMATE,
                target.getId(), huge))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-ATT-006"));
    }

    /** Arbitr-101/112: APP_USER ва COMPANY турлари DOCUMENT_TABLES'да (тип-харита свипи). */
    @Test
    void profileAndCompanyTypes_mappedToTables() {
        assertThat(AttachmentService.supportedDocumentTypes())
                .contains(DocumentType.APP_USER, DocumentType.COMPANY);
    }

    /** Реал png bytes (рефайнмент ImageIO ўлчов текшируви учун). */
    private byte[] pngBytes(int w, int h) throws Exception {
        var img = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    /** Рефайнмент BR-ATT-007: тўғри ўлчовли png ўтади. */
    @Test
    void uploadImage_validDimensions_succeeds() throws Exception {
        Attachment saved = attachmentService.uploadImage(DocumentType.ESTIMATE,
                target.getId(), image("ok.png", "image/png", pngBytes(64, 64)));
        assertThat(saved.getContentType()).isEqualTo("image/png");
    }

    /** Рефайнмент BR-ATT-007: жуда кичик расм рад (< 16px). */
    @Test
    void uploadImage_tooSmall_rejectedAtt007() throws Exception {
        var tiny = image("tiny.png", "image/png", pngBytes(8, 8));
        assertThatThrownBy(() -> attachmentService.uploadImage(
                DocumentType.ESTIMATE, target.getId(), tiny))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-ATT-007"));
    }

    /** Рефайнмент BR-ATT-007: жуда катта расм рад (> 4096px). */
    @Test
    void uploadImage_tooLarge_rejectedAtt007() throws Exception {
        var huge = image("huge.png", "image/png", pngBytes(4097, 64));
        assertThatThrownBy(() -> attachmentService.uploadImage(
                DocumentType.ESTIMATE, target.getId(), huge))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-ATT-007"));
    }

    /** Рефайнмент BR-ATT-007: бузуқ png (расм эмас) рад. */
    @Test
    void uploadImage_corruptPng_rejectedAtt007() {
        var bad = image("bad.png", "image/png", "not-an-image".getBytes());
        assertThatThrownBy(() -> attachmentService.uploadImage(
                DocumentType.ESTIMATE, target.getId(), bad))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-ATT-007"));
    }

    /**
     * Рефайнмент тузоқ: WEBP'ни ImageIO ўqiy olmaydi (плагин йўқ) - ўлчов
     * текширилмай ўтади (content-type/ҳажм текширилган). Fake bytes бўлса
     * ҳам муваффақият - webp рад бўлмайди.
     */
    @Test
    void uploadImage_webp_skipsDimensionCheck() {
        Attachment saved = attachmentService.uploadImage(DocumentType.ESTIMATE,
                target.getId(), image("logo.webp", "image/webp", "fake-webp".getBytes()));
        assertThat(saved.getContentType()).isEqualTo("image/webp");
    }
}
