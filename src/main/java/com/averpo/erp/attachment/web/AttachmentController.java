package com.averpo.erp.attachment.web;

import com.averpo.erp.attachment.domain.Attachment;
import com.averpo.erp.attachment.service.AttachmentService;
import com.averpo.erp.attachment.service.AttachmentService.Download;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Attachment web қатлами (docs/modules/attachments.md). «Иловалар»
 * бўлими ҳар транзакция КЎРИШ экранига HTMX фрагмент сифатида уланади
 * (view'да {@code <div hx-get="/attachments/{type}/{id}">}) - шу сабаб
 * 8 та ҳужжат контроллерига тегмасдан, ягона жойда рендер бўлади
 * (attachment модули изоляцияси). Юклаш/ўчириш hx-post билан ўша
 * бўлимни жойида янгилайди.
 *
 * <p>Роллар (BR-ATT-004): юклаш ва ўчириш POST - SecurityConfig'даги
 * {@code POST /**} қоидаси уларни камида битта соҳада EDIT борларга чеклайди (view-only роллар
 * → 403); бўлимни кўриш ва юклаб олиш GET - VIEWER'га ҳам очиқ. Шу
 * контроллерда алоҳида роль текшируви керак эмас.
 */
@Controller
@RequestMapping("/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    /**
     * Браузерда inline кўрсатишга ХАВФСИЗ MIME турлари (ARBITR-094).
     * Фақат статик расм + PDF: булар {@code <img>}/{@code <iframe>} ичида
     * скрипт бажармайди. image/svg+xml ва text/html АТАЙЛАБ ЙЎҚ - SVG/HTML
     * ичида {@code <script>} бўлиши мумкин, inline кўрсатилса XSS (юклаган
     * ходим бошқа фойдаланувчи браузерида код ишга туширади). Рўйхатдан
     * ташқари ҳар тур download'га йўналтирилади. Бу - ҳақиқат манбаи:
     * shared/attachments.jte «Кўриш» тугмасини шу рўйхат бўйича кўрсатади,
     * лекин UI алдангани билан ҳам view endpoint барибир текширади
     * (himoya-in-depth). Қиймат lower-case: солиштиришда нормаллаштирилади.
     */
    static final Set<String> INLINE_VIEWABLE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

    /** Иловаларнинг ягона public API'си. */
    private final AttachmentService attachmentService;

    /** createdBy → ким (displayName) кўрсатиш учун. */
    private final UserService userService;

    /** Юклаш вақтини компания зонасида кўрсатиш учун (Fmt.dt). */
    private final CompanySettingsService settingsService;

    /** «Иловалар» бўлими фрагменти (кўриш экрани hx-get билан юклайди). */
    @GetMapping("/{type}/{id}")
    public String section(@PathVariable DocumentType type, @PathVariable UUID id,
                          Model model) {
        return renderSection(type, id, model, null);
    }

    /**
     * Файл юклаш (multipart). Муваффақият ёки BR-ATT хатоси ўша бўлим
     * фрагментида қайтади - hx-post уни жойида алмаштиради.
     */
    @PostMapping("/{type}/{id}")
    public String upload(@PathVariable DocumentType type, @PathVariable UUID id,
                         @RequestParam("file") MultipartFile file, Model model) {
        String error = null;
        try {
            attachmentService.upload(type, id, file);
        } catch (BusinessRuleException e) {
            error = e.displayMessage();
        }
        return renderSection(type, id, model, error);
    }

    /** Юклаб олиш - асл ном билан (Content-Disposition attachment). */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Download download = attachmentService.download(id);
        // Асл ном UTF-8 кодланади - кирилл/бўшлиқли номлар ҳам тўғри
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(parseContentType(download.contentType()))
                .body(download.resource());
    }

    /**
     * Жойида кўриш (ARBITR-094): расм/PDF'ни браузер ойнасида очиш учун
     * Content-Disposition <b>inline</b> билан беради. Даунлоаддан фарқи
     * иккита: (1) inline (браузер сақламай кўрсатади), (2) ФАҚАТ
     * {@link #INLINE_VIEWABLE_TYPES} рўйхатидаги хавфсиз турлар - бошқаси
     * (жумладан SVG - XSS хавфи) даунлоадга 302 йўналтирилади, ҳеч қачон
     * inline берилмайди. {@code X-Content-Type-Options: nosniff} браузер
     * сақланган MIME'ни ўзгартириб талқин қилишининг олдини олади (масалан
     * матнни HTML деб ўйлаб скрипт ишга туширмасин). VIEWER ҳам кўради -
     * GET, download билан бир хил очиқлик (BR-ATT-004: view-only очиқ).
     */
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable UUID id) {
        Attachment attachment = attachmentService.get(id);
        if (!isInlineViewable(attachment.getContentType())) {
            // Хавфли/номаълум тур - inline бермаймиз, даунлоадга ўтказамиз
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/attachments/" + id + "/download"))
                    .build();
        }
        Download download = attachmentService.download(id);
        // Асл ном UTF-8 - «Янги ойнада» очилганда браузер табида кўринади
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(parseContentType(download.contentType()))
                .body(download.resource());
    }

    /** Ўчириш - шу ҳужжат учун янгиланган бўлим фрагменти қайтади. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, Model model) {
        // Ўчиришдан ОЛДИН тур/ҳужжатни оламиз - фрагмент қайси ҳужжатники
        // эканини билиш учун (ўчгач attachment йўқолади)
        Attachment attachment = attachmentService.get(id);
        DocumentType type = attachment.getDocumentType();
        UUID documentId = attachment.getDocumentId();
        attachmentService.delete(id);
        return renderSection(type, documentId, model, null);
    }

    // ---- ички ёрдамчилар ----

    /** Бўлим model'ини тўлдиради ва фрагмент шаблонини қайтаради. */
    private String renderSection(DocumentType type, UUID documentId, Model model,
                                 String error) {
        List<Attachment> attachments = attachmentService.list(type, documentId);
        model.addAttribute("attachments", attachments);
        model.addAttribute("documentType", type.name());
        model.addAttribute("documentId", documentId);
        model.addAttribute("uploaderNames", userService.namesById());
        model.addAttribute("zoneId", settingsService.zoneId());
        model.addAttribute("attError", error);
        return "shared/attachments";
    }

    /**
     * Сақланган MIME inline кўрсатишга хавфсизми (ARBITR-094). Параметрлар
     * ({@code ;charset=...}) ташланади, регистр нормаллаштирилади - шунда
     * «image/png» ва «image/png; charset=utf-8» бир хил кўрилади. null/бўш
     * → хавфсиз эмас (даунлоадга ўтади).
     */
    private boolean isInlineViewable(String contentType) {
        if (contentType == null) {
            return false;
        }
        String base = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return INLINE_VIEWABLE_TYPES.contains(base);
    }

    /** Сақланган MIME'ни MediaType'га; нотўғри бўлса octet-stream. */
    private MediaType parseContentType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
