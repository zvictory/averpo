package com.averpo.erp.payroll.repo;

import com.averpo.erp.payroll.domain.PayrollPayment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ведомость (PayrollRegisterService) run кесими ўқиши - payroll модули
 * ичида. gross/солиқлар/net POSTED run сатрларидан ходим кесимида. Clearing
 * давр боши/охири owed ва «даврда тўланган» бу ерда ЭМАС - улар ТЎЛИҚ GL'дан
 * (LedgerDashboardService контакт-кесими агрегати, GL ҳақиқат манбаи,
 * DEC-047; домен status'дан фарқли, reverse'да инвариант сақланади).
 *
 * <p>Repository домен тури техник ({@link PayrollPayment}) - @Query ўз
 * entity'сини (PayrollRunLine) танлайди, faqat ўқиш.
 */
public interface PayrollRegisterRepository extends Repository<PayrollPayment, UUID> {

    /**
     * Давр (period «YYYY-MM») POSTED run сатрлари ходим кесимида:
     * ҳисобланган gross ва солиқ/net snapshot'лари. Битта ойга биттагина
     * POSTED run (BR-PYR-002), лекин group by ходим - умумий, хавфсиз.
     */
    @Query("""
            select l.employeeId as employeeId, sum(l.gross) as gross,
                   sum(l.incomeTax) as incomeTax, sum(l.pension) as pension,
                   sum(l.net) as net
            from PayrollRunLine l
            where l.payrollRun.status = com.averpo.erp.payroll.domain.PayrollRun.Status.POSTED
              and l.payrollRun.period = :period
            group by l.employeeId
            """)
    List<Accrual> accrualByEmployee(@Param("period") String period);

    /** Run кесими проекцияси (ходим + ҳисобланган суммалар). */
    interface Accrual {
        UUID getEmployeeId();
        BigDecimal getGross();
        BigDecimal getIncomeTax();
        BigDecimal getPension();
        BigDecimal getNet();
    }
}
