package com.averpo.erp.bank.service;

import com.averpo.erp.bank.domain.BankReconciliation;
import com.averpo.erp.bank.domain.BankReconciliationMatch;
import com.averpo.erp.bank.repo.BankReconciliationMatchRepository;
import com.averpo.erp.bank.repo.BankReconciliationRepository;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.AccountTransactionsService;
import com.averpo.erp.ledger.service.AccountTransactionsService.ReconcilableLine;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Банк reconciliation'ининг ягона public API'си - QBO Reconcile модели
 * (docs/modules/banking.md): давр + якуний қолдиқ киритилади, GL
 * сатрлари бир-бир белгиланади, фарқ айнан 0 бўлганда якунланади.
 *
 * <p>GL сатрлари ledger'нинг public read методи
 * ({@link AccountTransactionsService#reconcilableLines}) орқали
 * ўқилади - ledger схемасига тегилмайди (қоида №6). Match'да сумма
 * snapshot'и сақланади: POSTED сатр ўзгармас, фарқ ҳисоби ledger'га
 * қайта мурожаатсиз чиқади.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReconciliationService {

    /**
     * Экран/тест учун номзод сатр: GL маълумоти + белги ҳолати.
     *
     * @param line             GL сатри (ledger read record'и)
     * @param matchedHere      шу reconciliation'да белгиланганми
     * @param matchedElsewhere бошқа reconciliation'да банд - экранда
     *                         кўрсатилмайди/ўчиқ бўлади
     */
    public record Candidate(ReconcilableLine line, boolean matchedHere,
                            boolean matchedElsewhere) { }

    /**
     * Ишчи экраннинг тўлиқ маълумоти БИТТА read-only транзакцияда
     * (Sanjar-006): аввал controller reconciliation'ни уч марта, счётни
     * икки марта, match рўйхатини икки марта алоҳида сўровларда ўқирди
     * (жами 9 SELECT). Энди reconciliation/match бир марта юкланиб,
     * candidates ҳам, difference ҳам шу нусхадан ҳисобланади.
     *
     * @param accountName     счёт номи (controller'даги иккинчи get ўрнига)
     * @param accountCurrency счёт валютаси коди; null - счёт home'да
     *                        юритилади, fallback чақирувчида
     */
    public record ReconciliationView(BankReconciliation reconciliation,
                                     String accountName, String accountCurrency,
                                     List<Candidate> candidates,
                                     BigDecimal difference) { }

    /** Reconciliation ҳужжатлари репозиторийси. */
    private final BankReconciliationRepository repository;

    /** Белгилар репозиторийси. */
    private final BankReconciliationMatchRepository matchRepository;

    /** Банк счёти валидацияси. */
    private final AccountService accountService;

    /** GL сатрлари - ledger'нинг public read методи. */
    private final AccountTransactionsService accountTransactionsService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public BankReconciliation get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reconciliation топилмади: " + id));
    }

    /**
     * Рўйхат тартиби - янгидан эскига (statementDate, createdAt) + id
     * tie-breaker (саҳифалашга барқарор тартиб, ARBITR-105 3-босқич).
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("statementDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /** Рўйхат экрани - саҳифаланган (ARBITR-105 3-босқич), янгидан эскига. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BankReconciliation> list(int page, int size) {
        return repository.findAll(org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size, LIST_SORT));
    }

    /**
     * Янги reconciliation бошлайди (IN_PROGRESS). Opening автоматик:
     * счётнинг ШУ САНАДАН ОЛДИНГИ охирги COMPLETED reconciliation'ининг
     * closing'и; ундай давр бўлмаса киритилган қиймат (бўш бўлса 0).
     *
     * <p>Zumrad-003: тартибсиз (орқага) бошлаш очиқ тақиқланади -
     * янги санадан КЕЙИНГИ давр аллақачон COMPLETED бўлса BR-RCN-008:
     * ўтказиб юборилган даврнинг opening'и «глобал охирги» closing'дан
     * олиниб, фарқ ҳеч қачон нолга тушмас ёки ёлғон COMPLETED ҳосил
     * бўлар эди. Кейинги даврлар бекор қилинмайди (COMPLETED ўзгармас,
     * BR-RCN-004) - фойдаланувчи уларни ҳисобга олган ҳолда киритади.
     *
     * @throws BusinessRuleException BR-RCN-001..003, BR-RCN-008
     */
    public BankReconciliation start(UUID accountId, LocalDate statementDate,
                                    BigDecimal closingBalance,
                                    BigDecimal openingBalanceIfFirst) {
        Account account = requireBankAccount(accountId);
        if (statementDate == null || closingBalance == null) {
            throw new BusinessRuleException(BusinessRule.BR_RCN_002,
                    "Кўчирма санаси ва якуний қолдиқ киритилиши шарт");
        }
        if (repository.existsByAccountIdAndStatementDate(accountId, statementDate)) {
            throw new BusinessRuleException(BusinessRule.BR_RCN_003,
                    "Бу счёт ва санага reconciliation аллақачон бор: "
                    + account.getName() + ", " + statementDate);
        }
        if (repository.existsByAccountIdAndStatusAndStatementDateGreaterThan(
                accountId, BankReconciliation.Status.COMPLETED, statementDate)) {
            throw new BusinessRuleException(BusinessRule.BR_RCN_008,
                    "Бу санадан кейинги давр аллақачон reconcile қилинган: "
                    + account.getName() + ", " + statementDate);
        }
        BigDecimal opening = repository
                .findTopByAccountIdAndStatusAndStatementDateLessThanOrderByStatementDateDesc(
                        accountId, BankReconciliation.Status.COMPLETED, statementDate)
                .map(BankReconciliation::getClosingBalance)
                .orElse(openingBalanceIfFirst == null ? BigDecimal.ZERO
                        : openingBalanceIfFirst);
        return repository.saveAndFlush(new BankReconciliation(
                accountId, statementDate, opening, closingBalance));
    }

    /**
     * GL сатрини белгилайди ёки (аллақачон белгиланган бўлса) ечади -
     * QBO reconcile checkbox'ининг ўзи.
     *
     * @throws BusinessRuleException BR-RCN-004, 006, 007
     */
    public void toggle(UUID reconciliationId, UUID journalEntryLineId) {
        BankReconciliation reconciliation = get(reconciliationId);
        reconciliation.requireInProgress();

        var existing = matchRepository.findByReconciliationIdAndJournalEntryLineId(
                reconciliationId, journalEntryLineId);
        if (existing.isPresent()) {
            matchRepository.delete(existing.get());
            matchRepository.flush();
            return;
        }
        if (matchRepository.existsByJournalEntryLineId(journalEntryLineId)) {
            throw new BusinessRuleException(BusinessRule.BR_RCN_006,
                    "GL сатри аллақачон бошқа reconciliation'да белгиланган");
        }
        // Сатр айнан шу счётники ва кўчирма давригача эканини ledger
        // read методи орқали текширамиз (BR-RCN-007). Якка-сатр
        // варианти атайлаб: рўйхат методи бутун тарихни ўқийди, toggle
        // эса сессияда 50-100 марта чақирилади (Beruniy-perf2)
        ReconcilableLine line = accountTransactionsService
                .reconcilableLine(reconciliation.getAccountId(), journalEntryLineId,
                        reconciliation.getStatementDate())
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_RCN_007,
                        "Сатр бу счётнинг кўчирма давригача бўлган GL сатри эмас: "
                        + journalEntryLineId));
        matchRepository.saveAndFlush(new BankReconciliationMatch(
                reconciliation, journalEntryLineId, line.signedAmount()));
    }

    /**
     * Жонли фарқ: closing - opening - Σ(белгиланган ишорали суммалар).
     * Якунлаш шарти - айнан 0.
     */
    @Transactional(readOnly = true)
    public BigDecimal difference(UUID reconciliationId) {
        return differenceOf(get(reconciliationId),
                matchRepository.findByReconciliationIdOrderByCreatedAtAsc(reconciliationId));
    }

    /**
     * Фарқ формуласи юкланган нусхалардан (Sanjar-006): view/complete
     * оқимлари reconciliation ва match'ларни қайта ўқимасдан ҳисоблайди.
     */
    private static BigDecimal differenceOf(BankReconciliation reconciliation,
                                           List<BankReconciliationMatch> matches) {
        BigDecimal cleared = BigDecimal.ZERO;
        for (BankReconciliationMatch match : matches) {
            cleared = cleared.add(match.getAmount());
        }
        return reconciliation.getClosingBalance()
                .subtract(reconciliation.getOpeningBalance())
                .subtract(cleared);
    }

    /**
     * Якунлайди - фарқ айнан 0 бўлса COMPLETED (ўзгармас).
     *
     * @throws BusinessRuleException BR-RCN-004, BR-RCN-005
     */
    public BankReconciliation complete(UUID reconciliationId) {
        BankReconciliation reconciliation = get(reconciliationId);
        reconciliation.requireInProgress();
        // Sanjar-006: ички difference(id) reconciliation'ни яна ўқирди -
        // энди юкланган нусха + бир марта ўқилган match'лар ишлатилади
        BigDecimal difference = differenceOf(reconciliation,
                matchRepository.findByReconciliationIdOrderByCreatedAtAsc(reconciliationId));
        if (difference.signum() != 0) {
            throw new BusinessRuleException(BusinessRule.BR_RCN_005,
                    "Фарқ 0 эмас: " + difference.stripTrailingZeros().toPlainString()
                    + " - ҳамма сатр белгиланганини ва якуний қолдиқни текширинг");
        }
        reconciliation.markCompleted(Instant.now());
        return reconciliation;
    }

    /**
     * IN_PROGRESS'ни бекор қилади: белгилар ўчади, ҳужжат ўзи ҳам -
     * (счёт, сана) ўрни бўшайди. COMPLETED бекор қилинмайди (BR-RCN-004).
     */
    public void cancel(UUID reconciliationId) {
        BankReconciliation reconciliation = get(reconciliationId);
        reconciliation.requireInProgress();
        matchRepository.deleteByReconciliationId(reconciliationId);
        repository.delete(reconciliation);
    }

    /**
     * Экран учун номзодлар: счётнинг кўчирма давригача бўлган GL
     * сатрлари, ҳар бирида белги ҳолати (шу ерда / бошқа ҳужжатда).
     */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(UUID reconciliationId) {
        return candidatesOf(get(reconciliationId),
                matchRepository.findByReconciliationIdOrderByCreatedAtAsc(reconciliationId));
    }

    /**
     * Ишчи экраннинг тўлиқ маълумоти - reconciliation, счёт ном/валютаси,
     * номзодлар ва фарқ битта read-only транзакцияда (Sanjar-006).
     * Битта сессия ичида ledger'нинг ички account ўқишлари ҳам биринчи
     * даражали кэшдан ҳал бўлади - счёт учун битта SELECT қолади.
     */
    @Transactional(readOnly = true)
    public ReconciliationView view(UUID reconciliationId) {
        BankReconciliation reconciliation = get(reconciliationId);
        Account account = accountService.get(reconciliation.getAccountId());
        List<BankReconciliationMatch> matches =
                matchRepository.findByReconciliationIdOrderByCreatedAtAsc(reconciliationId);
        return new ReconciliationView(reconciliation, account.getName(),
                account.getCurrency() != null ? account.getCurrency().getCode() : null,
                candidatesOf(reconciliation, matches),
                differenceOf(reconciliation, matches));
    }

    /**
     * Номзодлар рўйхати юкланган нусхалардан (Sanjar-006). Elsewhere
     * текшируви жорий номзодлар КЕСИМИДА битта query - бутун match
     * тарихи хотирага юкланмайди; membership фақат номзод id'лари учун
     * сўралгани сабабли натижа аввалги тўлиқ тўпламдагидек.
     */
    private List<Candidate> candidatesOf(BankReconciliation reconciliation,
                                         List<BankReconciliationMatch> matches) {
        Set<UUID> matchedHere = new HashSet<>();
        for (BankReconciliationMatch match : matches) {
            matchedHere.add(match.getJournalEntryLineId());
        }
        List<ReconcilableLine> lines = accountTransactionsService
                .reconcilableLines(reconciliation.getAccountId(),
                        reconciliation.getStatementDate());
        Set<UUID> matchedAnywhere = lines.isEmpty() ? Set.of()
                : matchRepository.findMatchedLineIdsIn(
                        lines.stream().map(ReconcilableLine::lineId).toList());
        return lines.stream()
                .map(line -> {
                    boolean here = matchedHere.contains(line.lineId());
                    boolean elsewhere = !here
                            && matchedAnywhere.contains(line.lineId());
                    return new Candidate(line, here, elsewhere);
                })
                .toList();
    }

    /** BR-RCN-001: счёт BANK туридан ва фаол. */
    private Account requireBankAccount(UUID accountId) {
        if (accountId == null) {
            throw new BusinessRuleException(BusinessRule.BR_RCN_001,
                    "Банк счёти танланиши шарт");
        }
        Account account = accountService.get(accountId);
        if (account.getType() != AccountType.BANK || !account.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_RCN_001,
                    "Reconciliation фақат BANK туридаги фаол счёт учун: "
                    + account.getName());
        }
        return account;
    }
}
