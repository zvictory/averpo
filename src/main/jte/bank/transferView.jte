@import com.averpo.erp.bank.domain.BankTransaction
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param BankTransaction txn
@param Map<UUID, String> accountNames
@param String counterpartCurrency = null
@param String homeCurrency
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

<%-- Otabek-003: ўтказманинг ЎЗ кўриш саҳифаси - «bank» номи фақат ички
     (spec transfer.md): label'лар Манбадан/Манзилга, орқага /transfers,
     сторно шу ерда (ёзувчи мантиқ - мавжуд bank service reverse'и). --%>
@template.layout.main(title = txn.getTxnNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "transfer") ${txn.getTxnNumber()}</h1>
        <a href="/transfers" class="text-primary underline dark:text-primary-dark">${msg.localize("transfer.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Arbitr-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("transfer.form.from")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${accountNames.getOrDefault(txn.getBankAccountId(), "-")}</span>
        </div>
        @if(txn.getCounterpartAccountId() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("transfer.form.to")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${accountNames.getOrDefault(txn.getCounterpartAccountId(), "-")}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.form.fromAmount")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(txn.getTotal())} ${txn.getCurrency().getCode()}</span>
        </div>
        @if(txn.getCounterpartAmount() != null && counterpartCurrency != null && !txn.getCurrency().getCode().equals(counterpartCurrency))
            <%-- Манзил сумма фақат кросс-валютада (Alisa-007, рўйхатдаги
                 Alisa-005 нақши): бир хил валютада «Сумма» билан айнан
                 такрор бўлиб чалғитарди; кросс-валютада иккала томон ҳам
                 кўчирма билан солиштирилади --%>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.form.toAmount")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(txn.getCounterpartAmount())} ${counterpartCurrency}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${txn.getTxnDate().toString()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = txn.getStatus().name(), msg = msg)</span>
        </div>
        @if(!txn.getCurrency().getCode().equals(homeCurrency))
            <%-- Arbitr-065: курс доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
            !{var fromOr = Fmt.orient(txn.getCurrency().getCode(), homeCurrency, txn.getExchangeRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.form.rate")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${msg.localize("rate.oriented", fromOr.base(), Fmt.rate(fromOr.value()), fromOr.quote())}</span>
            </div>
        @endif
        <%-- Манзил курс фақат манзил ЧЕТ валютада: home томон курси доим 1 -
             «1 UZS = 1 UZS» шовқини чиқмасин (Arbitr-065) --%>
        @if(counterpartCurrency != null && !counterpartCurrency.equals(homeCurrency) && txn.getCounterpartRate() != null && txn.getCounterpartRate().compareTo(txn.getExchangeRate()) != 0)
            !{var toOr = Fmt.orient(counterpartCurrency, homeCurrency, txn.getCounterpartRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.form.toRate")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${msg.localize("rate.oriented", toOr.base(), Fmt.rate(toOr.value()), toOr.quote())}</span>
            </div>
        @endif
    </div>

    <%-- Arbitr-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да --%>
    @if(txn.getStatus() == BankTransaction.Status.POSTED || txn.getStatus() == BankTransaction.Status.REVERSED)
        @template.shared.jeLink(sourceType = "BANK_TXN", id = txn.getId().toString(), msg = msg)
    @endif

    @if(txn.getMemo() != null)
        <p style="margin-bottom:1rem;white-space:pre-line;">${txn.getMemo()}</p>
    @endif

    @if(canEdit != null && canEdit && txn.getStatus() == BankTransaction.Status.POSTED)
        @template.shared.reverseForm(action = "/transfers/" + txn.getId().toString() + "/reverse",
            confirmKey = "bt.view.reverseConfirm",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/BANK_TXN/${txn.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
