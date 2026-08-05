package com.averpo.erp.security.web;

import com.averpo.erp.attachment.service.AttachmentService;
import com.averpo.erp.attachment.service.AttachmentService.Download;
import com.averpo.erp.i18n.Msg;
import com.averpo.erp.security.service.CompanyInfoService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
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
import java.util.UUID;

/**
 * Компания маълумотлари экрани (Arbitr-112, docs/modules/company-info.md):
 * реквизитлар (юридик ном/манзил/алоқа/СТИР/банк/директор) ва лого.
 * SUPER_ADMIN - компаниянинг ўзлигини бошқаради; чоп сарлавҳаси ва
 * document-print (29) шундан ўқийди.
 *
 * <p>Йўллар {@code /settings/company*} - SETTINGS соҳаси остида, шунинг
 * учун 092 UrlPermissionMap автоматик SUPER_ADMIN'га чеклайди
 * (SecurityConfig'га алоҳида қатор КЕРАК ЭМАС, 101 профилдан фарқли -
 * у соҳасиз /profile эди). Контроллер юпқа: реквизит валидацияси
 * {@link CompanySettingsService}'да, лого оркестрацияси
 * {@link CompanyInfoService}'да.
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/settings/company")
@RequiredArgsConstructor
public class CompanyInfoController {

    /** Реквизит ўқиш/сақлаш - shared public service. */
    private final CompanySettingsService settingsService;

    /** Лого upload/delete оркестрацияси (security - shared'нинг attachment ўрнига). */
    private final CompanyInfoService companyInfoService;

    /** Лого inline кўрсатиш (файл download) учун. */
    private final AttachmentService attachmentService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Компания маълумотлари формаси (реквизит + лого блоки). */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("settings", settingsService.get());
        return "shared/company";
    }

    /**
     * Реквизитларни сақлайди (Arbitr-112): email тўлдирилса формат
     * текширилади (BR-SET-007). `name` бу формада эмас - у /settings
     * асосий формасида (компания асосий номи).
     */
    @PostMapping
    public String save(@RequestParam String name,
                       @RequestParam(required = false) String legalName,
                       @RequestParam(required = false) String address,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String website,
                       @RequestParam(required = false) String taxId,
                       @RequestParam(required = false) String bankName,
                       @RequestParam(required = false) String bankAccount,
                       @RequestParam(required = false) String bankMfo,
                       @RequestParam(required = false) String directorName,
                       @RequestParam(required = false) String directorPosition,
                       RedirectAttributes redirect) {
        try {
            settingsService.updateCompanyInfo(name, legalName, address, phone, email, website,
                    taxId, bankName, bankAccount, bankMfo, directorName, directorPosition);
            redirect.addFlashAttribute("message", msg.get("company.saved"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/company";
    }

    /**
     * Логони браузерда inline кўрсатади (Arbitr-112, 094 нақши): inline +
     * nosniff. Лого доим png/jpeg/webp (upload'да BR-ATT-005 текширилган -
     * SVG сақланмайди), шунинг учун inline хавфсиз. Лого йўқ бўлса 404
     * (саҳифа placeholder кўрсатади).
     */
    @GetMapping("/logo")
    public ResponseEntity<Resource> logo() {
        UUID logoId = settingsService.logoAttachmentId();
        if (logoId == null) {
            return ResponseEntity.notFound().build();
        }
        Download download = attachmentService.download(logoId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(imageMediaType(download.contentType()))
                .body(download.resource());
    }

    /** Лого юклаш (png/jpeg/webp, 2MB - BR-ATT-005/006 uploadImage'да). */
    @PostMapping("/logo")
    public String uploadLogo(@RequestParam("file") MultipartFile file,
                             RedirectAttributes redirect) {
        try {
            companyInfoService.uploadLogo(file);
            redirect.addFlashAttribute("message", msg.get("company.logo.uploaded"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/company";
    }

    /** Логони ўчиради (placeholder'га қайтади). */
    @PostMapping("/logo/delete")
    public String deleteLogo(RedirectAttributes redirect) {
        companyInfoService.removeLogo();
        redirect.addFlashAttribute("message", msg.get("company.logo.deleted"));
        return "redirect:/settings/company";
    }

    /**
     * Бренд логоси юклаш (Arbitr-112 рефайнмент, топбар WHITE-LABEL) -
     * SUPER_ADMIN. Ҳужжат логосидан ФАРҚли иккинчи расм (png/jpeg/webp,
     * 2MB, ўлчов диапазони - BR-ATT-005/006/007). Топбарда ҳар роль
     * кўради (view /company/brand-logo соҳасиз).
     */
    @PostMapping("/brand-logo")
    public String uploadBrandLogo(@RequestParam("file") MultipartFile file,
                                  RedirectAttributes redirect) {
        try {
            companyInfoService.uploadBrandLogo(file);
            redirect.addFlashAttribute("message", msg.get("company.brandLogo.uploaded"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/company";
    }

    /** Бренд логосини ўчиради (топбар fallback «AVERPO»'га қайтади). */
    @PostMapping("/brand-logo/delete")
    public String deleteBrandLogo(RedirectAttributes redirect) {
        companyInfoService.removeBrandLogo();
        redirect.addFlashAttribute("message", msg.get("company.brandLogo.deleted"));
        return "redirect:/settings/company";
    }

    /** Сақланган MIME'ни MediaType'га; нотўғри бўлса octet-stream. */
    private MediaType imageMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
