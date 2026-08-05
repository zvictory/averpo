package com.averpo.erp.attachment.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.domain.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Attachment - транзакция ҳужжатига бириктирилган файл (docs/modules/
 * attachments.md, QBO Attachments). GL'га тегмайди - соф ҳужжат иловаси
 * (темир қоида №3 тегишли эмас: POSTED ҳужжатга ҳам бириктириш/ўчириш
 * мумкин). Target'га полиморф боғланади ({@code documentType} +
 * {@code documentId}), FK йўқ - {@code AttachmentService} мавжудликни
 * ўзи текширади (BR-ATT-003), шунда attachment модули бошқа модул
 * жадвалларига боғланиб қолмайди (темир қоида №6).
 *
 * <p>Файлнинг ўзи локал дискда (app.attachments.dir) - базада фақат
 * метамаълумот. {@code storedPath} диск номи ФАҚАТ сервер яратган UUID
 * (йил/ой/UUID.ext): фойдаланувчи киритган {@code originalName} диск
 * йўлига ҲЕЧ ҚАЧОН кирмайди (path traversal ҳимояси) - асл ном фақат
 * шу ерда сақланади ва юклаб олишда Content-Disposition'да қайтарилади.
 */
@Entity
@Table(name = "attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment extends BaseEntity {

    /** Бириктирилган ҳужжат тури (INVOICE, BILL, ...) - полиморф боғланиш. */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    /** Target ҳужжат id'си (полиморф, FK'сиз - мавжудлик BR-ATT-003'да). */
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** Фойдаланувчи файл номи - кўрсатиш/юклаб олишда (диск йўлига КИРМАЙДИ). */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** app.attachments.dir'га нисбий диск йўли (йил/ой/UUID.ext) - фақат сервер UUID. */
    @Column(name = "stored_path", nullable = false, length = 255)
    private String storedPath;

    /** MIME тури (browser'дан) - юклаб олишда Content-Type учун. */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** Файл ҳажми (byte) - рўйхатда Fmt билан кўрсатилади. */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Янги илова - валидация ва диск ёзуви {@code AttachmentService}'да. */
    public Attachment(DocumentType documentType, UUID documentId, String originalName,
                      String storedPath, String contentType, long sizeBytes) {
        this.documentType = documentType;
        this.documentId = documentId;
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }
}
