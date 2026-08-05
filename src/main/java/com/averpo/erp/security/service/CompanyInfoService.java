package com.averpo.erp.security.service;

import com.averpo.erp.attachment.domain.Attachment;
import com.averpo.erp.attachment.service.AttachmentService;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Компания логоси оркестрацияси (Arbitr-112): CompanySettings (shared)
 * ни лого attachment (attachment модул) билан улайди.
 *
 * <p>НЕГА security модулда: shared модул attachment'га боғланмайди
 * (қатлам қоидаси - shared энг паст, ҳамма унга боғланади). Шунинг учун
 * логонинг файл томони (upload/delete) shared'даги CompanySettingsService
 * ичида эмас, балки шу оркестрацияда - security → shared ва security →
 * attachment иккиси ҳам рухсатли йўналиш. CompanySettingsService фақат
 * FK (UUID) ва email валидациясини сақлайди. Бу - 101 профиль аватари
 * (UserService.setOwnProfileImage) нақшининг айнан ўзи, расм-специфик
 * валидация {@link AttachmentService#uploadImage}'да (BR-ATT-005/006).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CompanyInfoService {

    /** Реквизит ва лого FK - shared public service. */
    private final CompanySettingsService settingsService;

    /** Лого файл сақлаш - мавжуд Attachment инфраси. */
    private final AttachmentService attachmentService;

    /**
     * Лого юклаш (png/jpeg/webp, 2MB): расм {@code uploadImage} орқали
     * (COMPANY target - company_settings singleton'да), эски лого (agar
     * бўлса) ўчирилади (orphan қолмасин). Битта транзакцияда:
     * setLogoAttachmentId flush қилгач эски ўчади (ON DELETE SET NULL
     * янги боғланишни бузмайди).
     */
    public void uploadLogo(MultipartFile file) {
        // persistedId() - company_settings'ни DB'га flush қилиб id беради:
        // uploadImage jdbc EXISTS билан текширгани учун қатор кўринмаса
        // BR-ATT-003 отиларди (ёзувчи tx, get() read-only тузоғисиз)
        UUID settingsId = settingsService.persistedId();
        UUID oldLogo = settingsService.logoAttachmentId();
        Attachment attachment = attachmentService.uploadImage(
                DocumentType.COMPANY, settingsId, file);
        settingsService.setLogoAttachmentId(attachment.getId());
        if (oldLogo != null) {
            attachmentService.delete(oldLogo);
        }
    }

    /** Логони ўчиради - файл ва FK бирга кетади (placeholder'га қайтади). */
    public void removeLogo() {
        UUID oldLogo = settingsService.logoAttachmentId();
        if (oldLogo != null) {
            settingsService.setLogoAttachmentId(null);
            attachmentService.delete(oldLogo);
        }
    }

    /**
     * Бренд логоси (топбар WHITE-LABEL, Arbitr-112 рефайнмент) юклаш -
     * {@link #uploadLogo} нақшининг айнан ўзи, лекин FK
     * brand_logo_attachment_id (ҳужжат логосидан ФАРҚли иккинчи расм).
     * COMPANY target (company_settings singleton); эски бренд логоси
     * ўчирилади (orphan йўқ).
     */
    public void uploadBrandLogo(MultipartFile file) {
        UUID settingsId = settingsService.persistedId();
        UUID oldLogo = settingsService.brandLogoAttachmentId();
        Attachment attachment = attachmentService.uploadImage(
                DocumentType.COMPANY, settingsId, file);
        settingsService.setBrandLogoAttachmentId(attachment.getId());
        if (oldLogo != null) {
            attachmentService.delete(oldLogo);
        }
    }

    /** Бренд логосини ўчиради - топбар fallback «AVERPO»'га қайтади. */
    public void removeBrandLogo() {
        UUID oldLogo = settingsService.brandLogoAttachmentId();
        if (oldLogo != null) {
            settingsService.setBrandLogoAttachmentId(null);
            attachmentService.delete(oldLogo);
        }
    }
}
