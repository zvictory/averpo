package com.averpo.erp.ledger.domain;

import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Журнал проводкаси - GL'даги ягона ёзув бирлиги.
 *
 * <p>Ҳаёт цикли: DRAFT → POSTED → (REVERSED). ТЕМИР ҚОИДА №3: POSTED
 * ҳужжат ўзгартирилмайди - ҳар бир мутатор {@link #requireDraft()}
 * орқали буни entity даражасида ҳимоя қилади, service'даги
 * текширувга ишониб қолмайди.
 *
 * @author Zafar
 */
@Entity
@Table(name = "journal_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry extends BaseEntity {

    /** Кетма-кет ҳужжат рақами: JE-2026-000001 (sequence'дан). */
    @Column(name = "entry_number", nullable = false, unique = true, length = 20)
    private String entryNumber;

    /** Проводка санаси - ҳисоботлар шу сана бўйича. */
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** Эркин тавсиф. */
    @Column(columnDefinition = "text")
    private String description;

    /** Ҳаёт цикли ҳолати. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryStatus status = EntryStatus.DRAFT;

    /** Манба модул: MANUAL, SALES, PURCHASE... */
    @Column(name = "source_module", length = 30)
    private String sourceModule;

    /** Манба ҳужжат id'си (қўлда проводкада null). */
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    /** Бу entry'ни сторно қилган entry (REVERSED бўлса). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_by_id")
    private JournalEntry reversedBy;

    /**
     * Сторно entry учун: қайси POSTED entry'ни бекор қилади (тескари
     * йўналишдаги ҳавола). DB'даги ux_je_source_active partial unique
     * index сторнони айнан шу устун орқали истисно қилади - сторно асл
     * ҳужжат билан бир хил (sourceModule, sourceDocumentId) сақлайди.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private JournalEntry reversalOf;

    /** Post қилинган вақт (UTC). */
    @Column(name = "posted_at")
    private Instant postedAt;

    /** Проводка сатрлари - lineNo тартибида. */
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<JournalEntryLine> lines = new ArrayList<>();

    /** Янги DRAFT entry - сатрлар кейин addLine билан қўшилади. */
    public JournalEntry(String entryNumber, LocalDate entryDate, String description,
                        String sourceModule, UUID sourceDocumentId) {
        this.entryNumber = entryNumber;
        this.entryDate = entryDate;
        this.description = description;
        this.sourceModule = sourceModule;
        this.sourceDocumentId = sourceDocumentId;
    }

    /** Ташқарига ўзгармас нусха - рўйхатни четлаб ўтиб бузиш мумкин эмас. */
    public List<JournalEntryLine> getLines() { return List.copyOf(lines); }

    /** DRAFT бўлмаса ҳар қандай ўзгартириш тақиқ (ТЕМИР ҚОИДА №3). */
    private void requireDraft() {
        if (status != EntryStatus.DRAFT) {
            throw new com.averpo.erp.shared.exception.BusinessRuleException(BusinessRule.BR_LED_013, "POSTED/REVERSED entry ўзгартирилмайди: " + entryNumber);
        }
    }

    /** Янги сатр қўшади - фақат DRAFT ҳолатда (class'сиз - назорат/техник сатрлар). */
    public JournalEntryLine addLine(Account account, com.averpo.erp.shared.domain.Money debit,
                                    com.averpo.erp.shared.domain.Money credit, UUID contactId,
                                    UUID warehouseId, UUID itemId, String memo) {
        return addLine(account, debit, credit, contactId, warehouseId, itemId, memo, null);
    }

    /** Янги сатр Class теги билан (class-tracking.md) - фақат DRAFT ҳолатда. */
    public JournalEntryLine addLine(Account account, com.averpo.erp.shared.domain.Money debit,
                                    com.averpo.erp.shared.domain.Money credit, UUID contactId,
                                    UUID warehouseId, UUID itemId, String memo, UUID classId) {
        requireDraft();
        JournalEntryLine line = new JournalEntryLine(
                this, lines.size() + 1, account, debit, credit,
                contactId, warehouseId, itemId, memo, classId);
        lines.add(line);
        return line;
    }

    /** Сарлавҳа майдонларини янгилайди - фақат DRAFT ҳолатда. */
    public void updateHeader(LocalDate entryDate, String description) {
        requireDraft();
        this.entryDate = entryDate;
        this.description = description;
    }

    /** Барча сатрларни ўчиради - фақат DRAFT ҳолатда. */
    public void clearLines() {
        requireDraft();
        lines.clear();
    }

    /**
     * Фақат PostingService чақиради - сторно яратилаётганда асл entry'га
     * боғлайди. markPosted'дан ОЛДИН чақирилиши шарт: reversal_of_id
     * INSERT пайтидаёқ тўлдирилган бўлмаса partial unique index сторнони
     * дубликат деб йиқитади.
     */
    public void linkReversalOf(JournalEntry original) {
        requireDraft();
        this.reversalOf = original;
    }

    /** Фақат PostingService чақиради - валидациядан кейин. */
    public void markPosted(Instant when) {
        requireDraft();
        this.status = EntryStatus.POSTED;
        this.postedAt = when;
    }

    /** Фақат PostingService чақиради - сторно entry post бўлгандан кейин. */
    public void markReversed(JournalEntry storno) {
        if (status != EntryStatus.POSTED) {
            throw new com.averpo.erp.shared.exception.BusinessRuleException(BusinessRule.BR_LED_009, "Фақат POSTED entry reverse қилинади: " + entryNumber);
        }
        this.status = EntryStatus.REVERSED;
        this.reversedBy = storno;
    }
}
