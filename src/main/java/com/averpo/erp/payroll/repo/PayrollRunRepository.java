package com.averpo.erp.payroll.repo;

import com.averpo.erp.payroll.domain.PayrollRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Иш ҳақи ҳисоблашлари репозиторийси - фақат payroll модули ичида.
 * JpaSpecificationExecutor - рўйхат филтри учун (DEC-068).
 */
public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<PayrollRun> {

    /** Кўриш/post учун - сатрлари билан (open-in-view=false, lazy йўқ). */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<PayrollRun> findWithLinesById(UUID id);

    /** BR-PYR-002: шу ойга POSTED run борми (DB partial unique'га қўшимча). */
    boolean existsByPeriodAndStatus(String period, PayrollRun.Status status);

    /**
     * Рўйхат экрани учун run кесимида жами gross/net - JPQL агрегат
     * (DEC-054). Рўйхат сатрида {@code run.totalGross()} lazy {@code lines}
     * коллекциясини айланарди; render транзакциядан ташқарида
     * (open-in-view=false) → LazyInitializationException. Бу агрегат сатрларни
     * юкламай DB'да йиғади. @EntityGraph(lines)+Pageable ЙЎЛИ РАД: коллекция
     * fetch'ида Hibernate саҳифалашни хотирада қилади (HHH90003004).
     * Сатрсиз run натижада бўлмайди (group by) - чақирувчи 0 сифатида олади.
     */
    @Query("""
            select l.payrollRun.id as runId, sum(l.gross) as gross, sum(l.net) as net
            from PayrollRunLine l
            where l.payrollRun.id in :runIds
            group by l.payrollRun.id
            """)
    List<RunTotalRow> totalsByRun(@Param("runIds") Collection<UUID> runIds);

    /** Run кесими жами проекцияси (рўйхат устунлари - gross/net йиғиндиси). */
    interface RunTotalRow {
        /** Run id'си. */
        UUID getRunId();
        /** Жами gross (сатрлар йиғиндиси). */
        BigDecimal getGross();
        /** Жами net (сатрлар йиғиндиси). */
        BigDecimal getNet();
    }
}
