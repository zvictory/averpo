package com.averpo.erp.tax.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ҚҚС ставкаси - QBO global (VAT) услубидаги солиқ каталоги
 * (docs/modules/tax.md). Ўзбекистон MVP: ҚҚС 12% ва ҚҚСсиз (seed).
 *
 * <p>Ставка ЎЧИРИЛМАЙДИ - фақат {@code active=false} (каталог қоидаси,
 * CoA/Contact билан бир хил). Ставка ҚИЙМАТИ таҳрирланиши мумкин -
 * тарихий ҳужжатлар сатрга snapshot сақлагани учун бузилмайди
 * (BillLine/InvoiceLine.tax_rate_value). {@code rate} - фоиз:
 * {@code 12} = 12%, DB CHECK 0..100 (BR-TAX-002).
 */
@Entity
@Table(name = "tax_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxRate extends BaseEntity {

    /** Қисқа код - unique: «QQS12», «NO_TAX» (BR-TAX-001). */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** Кўрсатиладиган ном: «ҚҚС 12%» (BR-TAX-005). */
    @Column(nullable = false, length = 100)
    private String name;

    /** Фоиз ставка: 12 = 12%. 0..100 (BR-TAX-002). */
    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal rate;

    /** Нофаол ставка янги ҳужжатда танланмайди (BR-TAX-003). */
    @Column(nullable = false)
    private boolean active = true;

    /** Янги ставка (валидация service'да). */
    public TaxRate(String code, String name, BigDecimal rate) {
        this.code = code;
        this.name = name;
        this.rate = rate;
    }

    /** Барча таҳрирланадиган майдонларни янгилайди (валидация service'да). */
    public void update(String code, String name, BigDecimal rate, boolean active) {
        this.code = code;
        this.name = name;
        this.rate = rate;
        this.active = active;
    }
}
