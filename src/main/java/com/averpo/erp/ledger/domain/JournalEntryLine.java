package com.averpo.erp.ledger.domain;

import com.averpo.erp.shared.domain.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Проводка сатри: битта счёт, дебет ЁКИ кредит (XOR - 2-инвариант).
 *
 * <p>Иккала томон ҳам {@link Money} embedded: валюта суммаси + home
 * валютадаги baseAmount. Ишлатилмаган томон null. contact/warehouse/item
 * - dimension'лар, ҳисоботда кесим бериш учун (FK эмас, чунки ўша
 * модуллар кейинги босқичларда қурилади).
 */
@Entity
@Table(name = "journal_entry_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"entry_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntryLine extends com.averpo.erp.shared.domain.BaseEntity {

    /**
     * Ота проводка - сатр мустақил яшамайди: фақат
     * {@link JournalEntry#addLine} орқали туғилади ва entry lifecycle'и
     * (POSTED ўзгармаслиги, сторно) унга тўлиқ тааллуқли.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id")
    private JournalEntry entry;

    /**
     * Entry ичидаги тартиб рақами - сатрлар киритилган тартибини сақлайди
     * (Set эмас, детерминик кўрсатиш учун); (entry_id, line_no) DB unique.
     */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /**
     * Сатр ёзиладиган счёт. Фаол ва postable экани сақлашда эмас,
     * post/reverse пайтида текширилади (BR-LED-004/005) - счёт кейин
     * нофаол бўлса тарихий сатр бузилмайди.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    /**
     * Дебет томони - кредит билан XOR: сатрда фақат биттаси, мусбат
     * (BR-LED-002), ишлатилмагани null. Home баланс {@code baseAmount}
     * устида текширилади (4-темир қоида).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount",
                column = @Column(name = "debit_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency",
                column = @Column(name = "debit_currency", length = 3)),
        @AttributeOverride(name = "baseAmount",
                column = @Column(name = "debit_base_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "exchangeRate",
                column = @Column(name = "debit_exchange_rate", precision = 24, scale = 12))
    })
    private Money debit;

    /**
     * Кредит томони - {@link #debit} нинг кўзгуси (XOR, BR-LED-002).
     * Иккисининг айни жуфт устун тузилмаси ҳисобот SQL'ларини
     * CASE'сиз оддий SUM билан ёзиш имконини беради.
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount",
                column = @Column(name = "credit_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency",
                column = @Column(name = "credit_currency", length = 3)),
        @AttributeOverride(name = "baseAmount",
                column = @Column(name = "credit_base_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "exchangeRate",
                column = @Column(name = "credit_exchange_rate", precision = 24, scale = 12))
    })
    private Money credit;

    /**
     * Ихтиёрий контакт dimension'и - AR/AP субледжер кесими (aging,
     * statement, payroll ходим кесими) шу устундан ўқилади. FK эмас:
     * ledger contact модулини билмайди (қоида №6), id хом UUID туради.
     */
    @Column(name = "contact_id")
    private UUID contactId;

    /**
     * Ихтиёрий омбор dimension'и - inventory GL сатрини омбор кесимида
     * StockMovement билан солиштириш учун. FK эмас (қоида №6).
     */
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    /**
     * Ихтиёрий item dimension'и - COGS/inventory сатри қайси товардан
     * келганини изоҳлайди (valuation текшируви). FK эмас (қоида №6).
     */
    @Column(name = "item_id")
    private UUID itemId;

    /**
     * Ихтиёрий Class/Йўналиш теги (class-tracking.md) - dimension
     * паттерни: DB'да FK txn_class, JPA'да UUID. GL суммаларига таъсир
     * қилмайди - P&L by Class ҳисоботи шу устундан кесади (idx_jel_class).
     */
    @Column(name = "class_id")
    private UUID classId;

    /**
     * Сатр даражасидаги эркин изоҳ - қўлда JE'да «бу сатр нима учун»
     * жавоби; entry'нинг умумий description'идан фарқли ҳар сатрники ўзи.
     */
    @Column(length = 500)
    private String memo;

    /** Фақат JournalEntry.addLine чақиради - тартиб рақамини у беради. */
    JournalEntryLine(JournalEntry entry, int lineNo, Account account,
                     Money debit, Money credit, UUID contactId,
                     UUID warehouseId, UUID itemId, String memo, UUID classId) {
        this.entry = entry;
        this.lineNo = lineNo;
        this.account = account;
        this.debit = debit;
        this.credit = credit;
        this.contactId = contactId;
        this.warehouseId = warehouseId;
        this.itemId = itemId;
        this.memo = memo;
        this.classId = classId;
    }
}
