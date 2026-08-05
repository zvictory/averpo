package com.averpo.erp.security.web;

import com.averpo.erp.attachment.service.AttachmentService;
import com.averpo.erp.attachment.service.AttachmentService.Download;
import com.averpo.erp.i18n.Msg;
import com.averpo.erp.security.domain.Gender;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.web.FormParsers;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Фойдаланувчи профили (docs/modules/user-profile.md 1-бўлим,
 * Arbitr-101): ҳамма роллар (VIEWER_AUDITOR ҳам) ЎЗ профилини кўради ва
 * бошқаради - user id сессиядан олинади ({id} йўқ). {@code /profile}
 * тўлақонли бўлимга айланди: шахсий майдонлар (email/gender/birthdate/
 * phone), аватар (png/jpeg/webp, 2MB), парол алмаштириш блоки. 2FA/
 * Telegram блоклари учун жой қолдирилган (102/103 fragment'лари плаг
 * қилади).
 *
 * <p>ХАВФСИЗЛИК ТУЗОҚИ (092 мероси): бу контроллернинг ҳар янги POST'и
 * UrlPermissionMap'га КИРМАЙДИ (соҳасиз /profile), шунинг учун
 * SecurityConfig'да АНИҚ {@code authenticated()} қилинган - акс ҳолда
 * POST-catchall уларни соҳа EDIT талабига ташлаб, VIEWER_AUDITOR ўз
 * профилини сақлай олмасди. Валидация/сақлаш мантиқи {@link UserService}
 * ва {@link AttachmentService}'да - контроллер юпқа.
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    /** Фойдаланувчилар public API'си - жорий user'ни ўзи аниқлайди. */
    private final UserService userService;

    /** Аватар inline кўрсатиш (жорий user расми) учун илова API'си. */
    private final AttachmentService attachmentService;

    /** Топбар бренд логоси id'си (соҳасиз view - ҳар роль кўради). */
    private final CompanySettingsService settingsService;

    /**
     * Telegram блоки (Arbitr-103): гейт ҳолати ва бот созланганлиги.
     * Плагиннинг PUBLIC service'и - repo'сига тегилмайди (темир қоида 6);
     * контроллер юпқа (иккита boolean ўқийди), боғланиш йўналиши
     * {@code security.web → plugins.telegram.service → security.service} -
     * ациклик, bean ҳалқаси йўқ.
     */
    private final com.averpo.erp.plugins.telegram.service.TelegramService telegramService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /**
     * Профиль саҳифаси: шахсий майдонлар + аватар + парол + Telegram блоки.
     *
     * <p>Telegram (Arbitr-103): блок ФАҚАТ плагин ёқиқ бўлса чиқади
     * (Arbitr-113 гейти - ўчиқда route'лар ҳам 404); ичида бот
     * созланмаган бўлса огоҳлантириш, созланган бўлса улаш/узиш.
     * Улаш коди flash'дан келади (POST /profile/telegram/link).
     */
    @GetMapping("/profile")
    public String profile(Model model) {
        var current = userService.current();
        model.addAttribute("user", current);
        model.addAttribute("genders", Gender.values());
        // Уланган ходим номи (4-бўлим) - read-only; уланмаган бўлса null
        model.addAttribute("employeeName",
                userService.employeeName(current.getEmployeeContactId()));
        boolean telegramEnabled = telegramService.enabled();
        model.addAttribute("telegramEnabled", telegramEnabled);
        model.addAttribute("telegramConfigured",
                telegramEnabled && telegramService.configured());
        return "security/profile";
    }

    /**
     * Маълумотларни сақлайди (Arbitr-101 + Arbitr-148): кўрсатиладиган
     * ном + email/gender/birthdate/phone битта форма, битта транзакция.
     * Валидация UserService'да (BR-USR-004 ном, BR-USR-013 email,
     * BR-USR-014 сана); хато бўлса flash билан ўша саҳифага қайтади
     * (қиймат сақланиш - form қайта юкланганда user'дан ўқилади).
     */
    @PostMapping("/profile")
    public String saveProfile(@RequestParam(required = false) String displayName,
                              @RequestParam(required = false) String email,
                              @RequestParam(required = false) String gender,
                              @RequestParam(required = false) String birthdate,
                              @RequestParam(required = false) String phone,
                              RedirectAttributes redirect) {
        try {
            LocalDate bd = FormParsers.localDate(birthdate,
                    BusinessRule.BR_USR_014, "Туғилган сана");
            userService.updateOwnProfile(displayName, email, parseGender(gender), bd, phone);
            redirect.addFlashAttribute("message", msg.get("profile.saved"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/profile";
    }

    /** Аватар юклаш (png/jpeg/webp, 2MB - BR-ATT-005/006 UserService'да). */
    @PostMapping("/profile/image")
    public String uploadImage(@RequestParam("file") MultipartFile file,
                              RedirectAttributes redirect) {
        try {
            userService.setOwnProfileImage(file);
            redirect.addFlashAttribute("message", msg.get("profile.image.uploaded"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/profile";
    }

    /** Аватарни ўчиради (placeholder бош ҳарфли доирага қайтади). */
    @PostMapping("/profile/image/delete")
    public String deleteImage(RedirectAttributes redirect) {
        userService.removeOwnProfileImage();
        redirect.addFlashAttribute("message", msg.get("profile.image.deleted"));
        return "redirect:/profile";
    }

    /**
     * Жорий фойдаланувчи аватарини браузерда inline кўрсатади (Arbitr-101,
     * 094 view нақши): Content-Disposition inline + X-Content-Type-Options
     * nosniff. Расм доим png/jpeg/webp (upload'да BR-ATT-005 текширилган -
     * SVG умуман сақланмайди), шунинг учун inline хавфсиз. Аватар йўқ бўлса
     * 404 (саҳифа placeholder кўрсатади, бу endpoint'ни чақирмайди).
     */
    @GetMapping("/profile/image")
    public ResponseEntity<Resource> image() {
        UUID imageId = userService.currentProfileImageId();
        if (imageId == null) {
            return ResponseEntity.notFound().build();
        }
        Download download = attachmentService.download(imageId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(imageMediaType(download.contentType()))
                .body(download.resource());
    }

    /**
     * Компания бренд логоси (Arbitr-112 рефайнмент, топбар WHITE-LABEL) -
     * СОҲАСИЗ view: ҳар authenticated роль login'дан кейин топбарда кўради
     * (SecurityConfig anyRequest().authenticated() қамрайди - SETTINGS
     * соҳаси эмас, чунки VIEWER ҳам топбарни кўради). Inline+nosniff (094
     * нақши); созланмаса 404 (топбар fallback «AVERPO»). Upload/delete
     * эса SUPER_ADMIN'да (/settings/company/brand-logo).
     */
    @GetMapping("/company/brand-logo")
    public ResponseEntity<Resource> brandLogo() {
        UUID logoId = settingsService.brandLogoAttachmentId();
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

    /** Эски GET /profile/password → /profile (парол блоки шу саҳифага кўчди). */
    @GetMapping("/profile/password")
    public String passwordRedirect() {
        return "redirect:/profile";
    }

    /**
     * Парол алмаштириш (мавжуд оқим): эски парол текшируви service'да
     * (BR-USR-005/006). Муваффақият/хато flash билан /profile'даги парол
     * блокига қайтади (сессия узилмайди).
     */
    @PostMapping("/profile/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String newPasswordConfirm,
                                 RedirectAttributes redirect) {
        if (!Objects.equals(newPassword, newPasswordConfirm)) {
            redirect.addFlashAttribute("error", msg.get("user.form.passwordMismatch"));
            return "redirect:/profile";
        }
        try {
            userService.changeOwnPassword(oldPassword, newPassword);
            redirect.addFlashAttribute("message", msg.get("user.passwordChanged"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/profile";
    }

    // ---- ички ёрдамчилар ----

    /**
     * gender параметрини enum'га: бўш - null («кўрсатилмаган»); номаълум
     * қиймат (форма tampering) ҳам жимгина null - хом 400 бермайди.
     */
    private Gender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Gender.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
