package com.averpo.erp.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Битта ҳужжат турининг рақамлаш ҳолати (docs/modules/document-sequence.md).
 *
 * <p>DB sequence объекти эмас, оддий жадвал қатори - шунда prefix/padding
 * тур бўйича созланади ва рақам ҳужжат транзакцияси билан бирга rollback
 * бўлади (gap қолмайди). Parallel хавфсизлик қатор қулфи орқали -
 * {@code DocumentSequenceRepository.lockByDocumentType}.
 *
 * <p>Қаторлар фақат Liquibase seed орқали яратилади (014-02) - runtime'да
 * яратиш/ўчириш API'си атайлаб йўқ.
 */
@Entity
@Table(name = "document_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentSequence extends BaseEntity {

    /** Қайси ҳужжат турига тегишли - unique, enum string сифатида. */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, unique = true, length = 30)
    private DocumentType documentType;

    /** Рақам олдидаги белги: JE, INV, BILL, PAY. */
    @Column(nullable = false, length = 10)
    private String prefix;

    /** Рақамда йил кўрсатиладими: JE-2026-000001 ёки JE-000001. */
    @Column(name = "include_year", nullable = false)
    private boolean includeYear;

    /** Рақам қисмининг минимал узунлиги: 5 бўлса 00001. */
    @Column(nullable = false)
    private int padding;

    /** Кейинги бериладиган рақам - фақат олдинга юради. */
    @Column(name = "next_number", nullable = false)
    private long nextNumber;

    /**
     * Навбатдаги рақамни ажратиб форматлайди: {@code JE-2026-000123}.
     * Рақам йилга боғлиқ ЭМАС - йил фақат кўрсатиш учун, йил алмашганда
     * рақам узилмай давом этади (QBO услуби, дубликат хавфи ҳам йўқ).
     *
     * <p>Фақат қулфланган қаторда чақирилади (service кафолатлайди) -
     * акс ҳолда parallel икки ҳужжат бир рақам олиши мумкин эди.
     *
     * @param year ҳужжат санасининг йили (include_year бўлмаса эътиборсиз)
     */
    public String allocate(int year) {
        long number = nextNumber++;
        String digits = String.format("%0" + padding + "d", number);
        return includeYear
                ? prefix + "-" + year + "-" + digits
                : prefix + "-" + digits;
    }
}
