package com.averpo.erp.ledger.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Мижоз кўчирмаси (Statement, QBO паритети) - битта мижознинг [from, to]
 * даврдаги AR ҳаракатлари: давр боши қолдиқ + хронологик ҳаракатлар
 * (invoice заряди, тўлов, credit memo, курс фарқи) running balance билан
 * + давр охири қолдиқ. Барча суммалар home валютада.
 *
 * <p>GL'га мутлақо тегмайди - соф ҳисобот ({@link LedgerDashboardService}
 * ва {@link AccountTransactionsService} прецеденти): ledger жадвалларига
 * SQL фақат шу қатламда (қоида №6). Ҳисоб AR контрол счётининг GL
 * сатрларидан: ҳар AR проводка сатри {@code journal_entry_line.contact_id}
 * устунига мижоз id'сини ёзади (invoice/тўлов/CM/курс фарқи - ҳаммаси),
 * шунинг учун мижоз кесими манба ҳужжат жадвалларига join'сиз, тўғри
 * GL'дан олинади. {@code source_module} фақат ҳаракат ТУРини ёрлиқлаш
 * ва ҳужжат рақамини топиш учун ишлатилади.
 *
 * <p>Инвариант (тестда текширилади): опенинг + давр ҳаракатлари
 * йиғиндиси == клозинг - бу GL'даги AR қолдиғининг ўзи (accrual), шунинг
 * учун ҳисобот AR subledger билан бир хил гапиради.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatementService {

    /** SQL агрегат учун JdbcClient - Hibernate'ни четлаб ўтади. */
    private final JdbcClient jdbc;

    /**
     * Битта кўчирма ҳаракати (running balance билан).
     *
     * @param date           ҳужжат санаси (entry_date)
     * @param sourceModule   ҳаракат тури (INVOICE/INVOICE_PAYMENT/CREDIT_MEMO/
     *                       RECEIPT_ALLOCATION/CREDIT_APPLICATION) - view ёрлиқлайди
     * @param documentNumber ҳужжат рақами (INV-/RCPT-/CM-) ёки курс фарқи
     *                       сатрларида null (унда entryNumber кўрсатилади)
     * @param entryNumber    JE рақами (documentNumber йўқ ҳолатда fallback)
     * @param amount         home валютада, ишорали: мусбат - заряд (Dr AR,
     *                       invoice), манфий - тўлов/кредит (Cr AR)
     * @param balance        шу сатрдан кейинги running қолдиқ (home)
     * @param entryId        JE id'си - рақам JE кўришига линк бўлади
     *                       (Arbitr-063)
     * @param documentId     манба ҳужжат id'си (source_document_id) -
     *                       documentNumber линки шу орқали ясалади
     */
    public record Row(LocalDate date, String sourceModule, String documentNumber,
                      String entryNumber, BigDecimal amount, BigDecimal balance,
                      UUID entryId, UUID documentId) { }

    /**
     * Тўлиқ кўчирма.
     *
     * @param opening давр боши қолдиқ (from'дан олдинги AR нетто)
     * @param rows    давр ҳаракатлари (хронологик, running balance билан)
     * @param closing давр охири қолдиқ (opening + ҳаракатлар йиғиндиси)
     */
    public record Statement(BigDecimal opening, List<Row> rows, BigDecimal closing) { }

    /**
     * Мижознинг [from, to] давр кўчирмаси. AR счёти йўқ (chart тузилмаган)
     * ёки ҳаракат бўлмаса - нол опенинг, бўш рўйхат, нол клозинг.
     */
    public Statement statement(UUID customerId, LocalDate from, LocalDate to) {
        UUID arAccountId = arAccountId();
        if (arAccountId == null) {
            return new Statement(BigDecimal.ZERO, List.of(), BigDecimal.ZERO);
        }
        BigDecimal opening = openingBalance(arAccountId, customerId, from);
        List<Row> rows = new ArrayList<>();
        // running - callback ичида ўзгаради (LedgerDashboardService услуби)
        BigDecimal[] running = {opening};
        jdbc.sql("""
                SELECT je.entry_date AS d, je.entry_number AS entry_number,
                       je.id AS entry_id,
                       je.source_document_id AS document_id,
                       je.source_module AS source_module,
                       COALESCE(l.debit_base_amount, 0) - COALESCE(l.credit_base_amount, 0) AS amount,
                       inv.invoice_number AS invoice_number,
                       pay.receipt_number AS receipt_number,
                       cm.cm_number AS cm_number
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                LEFT JOIN invoice inv
                    ON je.source_module = 'INVOICE' AND inv.id = je.source_document_id
                LEFT JOIN invoice_payment pay
                    ON je.source_module = 'INVOICE_PAYMENT' AND pay.id = je.source_document_id
                LEFT JOIN credit_memo cm
                    ON je.source_module = 'CREDIT_MEMO' AND cm.id = je.source_document_id
                WHERE l.account_id = :ar
                  AND l.contact_id = :cust
                  AND je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date >= :from AND je.entry_date <= :to
                ORDER BY je.entry_date, je.posted_at, je.entry_number, l.line_no
                """)
                .param("ar", arAccountId)
                .param("cust", customerId)
                .param("from", from)
                .param("to", to)
                .query(rs -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    running[0] = running[0].add(amount);
                    rows.add(new Row(
                            rs.getObject("d", LocalDate.class),
                            rs.getString("source_module"),
                            firstNonBlank(rs.getString("invoice_number"),
                                    rs.getString("receipt_number"),
                                    rs.getString("cm_number")),
                            rs.getString("entry_number"),
                            amount, running[0],
                            rs.getObject("entry_id", UUID.class),
                            rs.getObject("document_id", UUID.class)));
                });
        return new Statement(opening, rows, running[0]);
    }

    /**
     * Таъминотчи (vendor) [from, to] давр кўчирмаси - {@link #statement}
     * (AR) нинг AP кўзгуси. Фарқлар: (1) AP контрол счёти
     * ({@code detail_type='ACCOUNTS_PAYABLE'}); (2) ишора ТЕСКАРИ -
     * {@code amount = credit_base - debit_base}: bill қарзимизни ОШИРАДИ
     * (Cr AP → мусбат), тўлов КАМАЙТИРАДИ (Dr AP → манфий), шунда running
     * баланс «қарзимиз» сифатида мусбат кўринади (QBO vendor balance
     * конвенцияси; аванс = дебет AP = манфий); (3) манба ҳужжатлар
     * bill/bill_payment/vendor_credit. {@link Row} ва {@link Statement}
     * record реюз (JTE иккисига бир хил render қилади: мусбат amount →
     * «ҳисобланган», манфий → «тўланган»). Барча суммалар home валютада.
     * AP счёти йўқ ёки ҳаракат бўлмаса - нол.
     *
     * <p>Инвариант ({@code StatementService} AR билан бир хил): опенинг +
     * давр ҳаракатлари == клозинг - бу GL'даги AP қолдиғининг ўзи, шунинг
     * учун vendor карточкаси кўчирмаси AP subledger билан гаплашади.
     */
    public Statement vendorStatement(UUID vendorId, LocalDate from, LocalDate to) {
        UUID apAccountId = apAccountId();
        if (apAccountId == null) {
            return new Statement(BigDecimal.ZERO, List.of(), BigDecimal.ZERO);
        }
        // AP опенинг = Cr-Dt (қарзимиз мусбат) = -(Dt-Cr): openingBalance
        // генерик Dt-Cr қайтаради, AP нормал ишораси учун инкор қиламиз
        BigDecimal opening = openingBalance(apAccountId, vendorId, from).negate();
        List<Row> rows = new ArrayList<>();
        // running - callback ичида ўзгаради (statement() услуби)
        BigDecimal[] running = {opening};
        jdbc.sql("""
                SELECT je.entry_date AS d, je.entry_number AS entry_number,
                       je.id AS entry_id,
                       je.source_document_id AS document_id,
                       je.source_module AS source_module,
                       COALESCE(l.credit_base_amount, 0) - COALESCE(l.debit_base_amount, 0) AS amount,
                       bill.bill_number AS bill_number,
                       pay.payment_number AS payment_number,
                       vc.vc_number AS vc_number
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                LEFT JOIN bill bill
                    ON je.source_module = 'BILL' AND bill.id = je.source_document_id
                LEFT JOIN bill_payment pay
                    ON je.source_module = 'BILL_PAYMENT' AND pay.id = je.source_document_id
                LEFT JOIN vendor_credit vc
                    ON je.source_module = 'VENDOR_CREDIT' AND vc.id = je.source_document_id
                WHERE l.account_id = :ap
                  AND l.contact_id = :vend
                  AND je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date >= :from AND je.entry_date <= :to
                ORDER BY je.entry_date, je.posted_at, je.entry_number, l.line_no
                """)
                .param("ap", apAccountId)
                .param("vend", vendorId)
                .param("from", from)
                .param("to", to)
                .query(rs -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    running[0] = running[0].add(amount);
                    rows.add(new Row(
                            rs.getObject("d", LocalDate.class),
                            rs.getString("source_module"),
                            firstNonBlank(rs.getString("bill_number"),
                                    rs.getString("payment_number"),
                                    rs.getString("vc_number")),
                            rs.getString("entry_number"),
                            amount, running[0],
                            rs.getObject("entry_id", UUID.class),
                            rs.getObject("document_id", UUID.class)));
                });
        return new Statement(opening, rows, running[0]);
    }

    // ---- ички ёрдамчилар ----

    /**
     * AR контрол счёти id'си: ягона фаол+postable
     * {@code detail_type='ACCOUNTS_RECEIVABLE'} счёт. Chart тузилмаган
     * бўлса null (ҳисобот бўш қайтади).
     */
    private UUID arAccountId() {
        return jdbc.sql("""
                SELECT id FROM account
                WHERE detail_type = 'ACCOUNTS_RECEIVABLE' AND active AND postable
                ORDER BY created_at
                LIMIT 1
                """)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    /**
     * AP контрол счёти id'си: ягона фаол+postable
     * {@code detail_type='ACCOUNTS_PAYABLE'} счёт ({@link #arAccountId}
     * кўзгуси). Chart тузилмаган бўлса null (кўчирма бўш қайтади).
     */
    private UUID apAccountId() {
        return jdbc.sql("""
                SELECT id FROM account
                WHERE detail_type = 'ACCOUNTS_PAYABLE' AND active AND postable
                ORDER BY created_at
                LIMIT 1
                """)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    /**
     * from'дан ОЛДИНГИ AR нетто (davr боши қолдиқ) - мижоз кесимида,
     * home валютада (Dt-Cr base). POSTED+REVERSED (сторно жуфти нетто 0).
     */
    private BigDecimal openingBalance(UUID arAccountId, UUID customerId, LocalDate from) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                        - COALESCE(l.credit_base_amount, 0)), 0)
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                WHERE l.account_id = :ar
                  AND l.contact_id = :cust
                  AND je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date < :from
                """)
                .param("ar", arAccountId)
                .param("cust", customerId)
                .param("from", from)
                .query(BigDecimal.class)
                .single();
    }

    /** Биринчи бўш бўлмаган қиймат ёки null (ҳужжат рақами танлови). */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
