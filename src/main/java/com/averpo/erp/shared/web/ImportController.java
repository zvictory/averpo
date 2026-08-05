package com.averpo.erp.shared.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.ExcelImportService;
import com.averpo.erp.shared.service.ExcelImportService.ImportPreview;
import com.averpo.erp.shared.service.ExcelImportService.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

/**
 * Excel'дан бошланғич import экрани (docs/modules/import-excel.md).
 *
 * <p>{@code /settings/import} остида - SecurityConfig'даги
 * {@code /settings/**} qoidasi уни SETTINGS соҳасига (SUPER_ADMIN) чеклайди,
 * шу боис алоҳида роль текшируви керак эмас. Контроллер юпқа: файлни
 * {@link ExcelImportService}'га беради, натижа/хатони экранга узатади.
 *
 * <p>Оқим (spec: алоҳида preview-тасдиқ қадами ЙЎҚ - минималлик): юклаш →
 * parse → хато бўлса рўйхат ўша экранда; тоза бўлса дарҳол apply + якун
 * flash хабари билан redirect (PRG - қайта юборишда такрор import бўлмайди).
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/settings/import")
@RequiredArgsConstructor
public class ImportController {

    /** Тайёр шаблон classpath'да (static/) - юклаб олиш учун. */
    private static final String TEMPLATE_RESOURCE = "static/import-template.xlsx";

    /** .xlsx MIME - юклаб олишда Content-Type. */
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** Import public API - ягона ўқиш/ёзиш нуқтаси. */
    private final ExcelImportService importService;

    /** Flash хабарлар (якун) учун i18n. */
    private final Msg msg;

    /** Import формаси (шаблон линки + upload). Flash message/натижа авто-моделда. */
    @GetMapping
    public String show() {
        return "shared/import";
    }

    /** Тайёр шаблонни (арбитр ясаган) асл ном билан беради. */
    @GetMapping("/template")
    public ResponseEntity<Resource> template() {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("import-template.xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(XLSX)
                .body(new ClassPathResource(TEMPLATE_RESOURCE));
    }

    /**
     * Файлни юклаш: хато бўлса рўйхат ўша экранда (redirect'сиз - хатолар
     * flash'да йўқолмасин); тоза бўлса apply + redirect + якун хабари.
     */
    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file,
                         Model model, RedirectAttributes redirect) {
        try {
            ImportPreview preview = importService.parse(file);
            if (preview.hasErrors()) {
                model.addAttribute("errors", preview.errors());
                return "shared/import";
            }
            ImportResult result = importService.apply(preview);
            redirect.addFlashAttribute("message",
                    msg.get("import.done", result.totalCreated(), result.totalSkipped()));
            return "redirect:/settings/import";
        } catch (BusinessRuleException e) {
            // BR-IMP-001 (файл ўзи бузуқ) - тушунарли хато ўша экранда
            model.addAttribute("error", e.displayMessage());
            return "shared/import";
        }
    }
}
