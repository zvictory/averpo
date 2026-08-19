package com.averpo.erp.purchase.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Landed cost тақсимот ҳужжати (docs/modules/purchases.md «Landed
 * cost»). Клирингдан ЭРКИН сумма (bill'га боғланмаган - лойиҳа
 * қарори) танланган receipt'ларга қиймат нисбатида тарқатилади.
 * DRAFT йўқ: яратилди = POSTED (тўлов модели), тузатиш reverse орқали.
 * Суммалар home валютада - омбор қийматлари home'да юритилади.
 */
@Entity
@Table(name = "landed_cost_allocation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandedCostAllocation extends BaseEntity {

    /** Ҳужжат ҳолати - DRAFT йўқ (тўлов модели). */
    public enum Status {
        /** Ўтказилган - GL ва омбор қийматларида акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган - қийматлар ортга қайтарилган. */
        REVERSED
    }

    /** Ҳужжат рақами - DocumentSequence LC-2026-NNNNN (unique). */
    @Column(name = "allocation_number", nullable = false, unique = true, length = 20)
    private String allocationNumber;

    /** Тақсимот санаси - GL проводка санаси ҳам шу. */
    @Column(name = "allocation_date", nullable = false)
    private LocalDate allocationDate;

    /** Тарқатилаётган жами сумма (home валютада, клирингдан). */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    /** Ҳолат. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.POSTED;

    /**
     * Сторно санаси (GL сторно JE санаси билан бир хил) - inventory
     * valuation «санага» ҳисоботида улуш қайси кундан кучда эмаслигини
     * шу белгилайди. POSTED'да null.
     */
    @Column(name = "reversal_date")
    private LocalDate reversalDate;

    /** Эркин изоҳ (қайси харажат эканини ёзиб қўйиш учун). */
    @Column(length = 500)
    private String memo;

    /** Янги тақсимот - дарҳол POSTED (валидация service'да). */
    public LandedCostAllocation(String allocationNumber, LocalDate allocationDate,
                                BigDecimal totalAmount, String memo) {
        this.allocationNumber = allocationNumber;
        this.allocationDate = allocationDate;
        this.totalAmount = totalAmount;
        this.memo = memo;
    }

    /** POSTED'дан REVERSED'га ўтказади (фақат LandedCostService чақиради). */
    public void markReversed(LocalDate reversalDate) {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_LC_005,
                    "Фақат POSTED тақсимот reverse қилинади: " + allocationNumber
                    + " - " + status);
        }
        this.status = Status.REVERSED;
        this.reversalDate = reversalDate;
    }
}
