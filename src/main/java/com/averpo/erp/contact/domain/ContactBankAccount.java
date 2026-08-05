package com.averpo.erp.contact.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Контактнинг банк реквизити (old-erp-ideas §3). Ҳисоб рақами контакт
 * ичида unique (BR-CON-010, DB constraint ҳам бор). Эски лойиҳадаги
 * status enum атайлаб олинмаган - қатор ўчириб қайта қўшилади (MVP);
 * Payment реквизитга боғлангач ўчиришга guard қўшилади.
 */
@Entity
@Table(name = "contact_bank_account",
       uniqueConstraints = @UniqueConstraint(columnNames = {"contact_id", "account_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactBankAccount extends BaseEntity {

    /** Эгаси. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    /** Банк номи - мажбурий (BR-CON-009). */
    @Column(name = "bank_name", nullable = false, length = 200)
    private String bankName;

    /** Банк коди (МФО). */
    @Column(name = "bank_code", length = 20)
    private String bankCode;

    /** Ҳисоб рақами - мажбурий, контакт ичида unique. */
    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    /**
     * Ҳисоб валютаси - Currency каталогига ManyToOne (темир қоида №11).
     * null - home currency. EAGER: реквизитлар рўйхати шаблонда
     * кўрсатилганда lazy хатоси бўлмасин (қаторлар оз).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id")
    private Currency currency;

    /** Default реквизит белгиси - контактда биттагина. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultAccount;

    /** Янги реквизит (валидация service'да). */
    public ContactBankAccount(Contact contact, String bankName, String bankCode,
                              String accountNumber, Currency currency,
                              boolean defaultAccount) {
        this.contact = contact;
        this.bankName = bankName;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.defaultAccount = defaultAccount;
    }

    /** Янги default келганда эскисини бўшатиш учун (фақат service чақиради). */
    public void clearDefault() { this.defaultAccount = false; }
}
