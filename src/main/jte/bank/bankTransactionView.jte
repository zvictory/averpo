@import com.averpo.erp.bank.domain.BankTransaction
@import com.averpo.erp.bank.domain.BankTransactionLine
@import com.averpo.erp.bank.domain.BankTransactionType
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param BankTransaction txn
@param Map<UUID, String> accountNames
@param Map<UUID, String> contactNames
@param String homeCurrency
@param String today
@param String counterpartCurrency = null
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = txn.getTxnNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "bankTransaction") ${txn.getTxnNumber()}</h1>
        <a href="/bank-transactions" class="text-primary underline dark:text-primary-dark">${msg.localize("bt.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Arbitr-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.type")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${msg.localize("bt.type." + txn.getType().name())}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${txn.getType() == BankTransactionType.TRANSFER ? msg.lookup("bt.form.fromBank") : msg.lookup("bt.form.bank")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${accountNames.getOrDefault(txn.getBankAccountId(), "-")}</span>
        </div>
        @if(txn.getType() == BankTransactionType.TRANSFER && txn.getCounterpartAccountId() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.form.toBank")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${accountNames.getOrDefault(txn.getCounterpartAccountId(), "-")}</span>
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
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.total")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(txn.getTotal())} ${txn.getCurrency().getCode()}</span>
        </div>
        @if(!txn.getCurrency().getCode().equals(homeCurrency))
            <%-- Arbitr-065: курс доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
            !{var rateOr = Fmt.orient(txn.getCurrency().getCode(), homeCurrency, txn.getExchangeRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.form.rate")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())}</span>
            </div>
        @endif
        @if(txn.getType() == BankTransactionType.TRANSFER && txn.getCounterpartAmount() != null)
            <%-- Манзил сумма манзил банк валютасида (Alisa-003) - кодсиз
                 конверсия рақами кўчирма билан солиштиришда адаштиради --%>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.form.toAmount")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(txn.getCounterpartAmount())}@if(counterpartCurrency != null) ${counterpartCurrency}@endif
                    <%-- Манзил курс фақат манзил ЧЕТ валютада - home курси 1 шовқин (Arbitr-065) --%>
                    @if(counterpartCurrency != null && !counterpartCurrency.equals(homeCurrency) && txn.getCounterpartRate() != null && txn.getCounterpartRate().compareTo(txn.getExchangeRate()) != 0)
                        !{var toOr = Fmt.orient(counterpartCurrency, homeCurrency, txn.getCounterpartRate());}
                        (${msg.localize("bt.form.toRate")}: ${msg.localize("rate.oriented", toOr.base(), Fmt.rate(toOr.value()), toOr.quote())})
                    @endif</span>
            </div>
        @endif
        @if(txn.getContactId() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.form.contact")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${contactNames.getOrDefault(txn.getContactId(), "-")}</span>
            </div>
        @endif
    </div>

    <%-- Arbitr-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да --%>
    @if(txn.getStatus() == BankTransaction.Status.POSTED || txn.getStatus() == BankTransaction.Status.REVERSED)
        @template.shared.jeLink(sourceType = "BANK_TXN", id = txn.getId().toString(), msg = msg)
    @endif

    @if(txn.getMemo() != null)
        <p class="mb-4">${txn.getMemo()}</p>
    @endif

    @if(txn.getType() != BankTransactionType.TRANSFER)
        @template.shared.table(cls = "min-w-[640px]", wrap = "mb-4", c = @`
            <thead>
            <tr>
                <th>#</th>
                <th>${msg.localize("bt.row.account")}</th>
                <th class="text-right">${msg.localize("bt.row.amount")}</th>
                <th>${msg.localize("bt.row.contact")}</th>
                <th>${msg.localize("bt.row.memo")}</th>
            </tr>
            </thead>
            <tbody>
            @for(BankTransactionLine line : txn.getLines())
                <tr>
                    <td>${line.getLineNo()}</td>
                    <td>${accountNames.getOrDefault(line.getAccountId(), "-")}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getAmount())} ${txn.getCurrency().getCode()}</td>
                    <td>
                        @if(line.getContactId() != null)
                            ${contactNames.getOrDefault(line.getContactId(), "-")}
                        @endif
                    </td>
                    <td>${line.getMemo()}</td>
                </tr>
            @endfor
            </tbody>
            <tfoot>
            <tr>
                <td colspan="2" class="text-right font-bold">${msg.localize("bt.col.total")}:</td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(txn.getTotal())} ${txn.getCurrency().getCode()}</td>
                <td colspan="2"></td>
            </tr>
            </tfoot>
        `)
    @endif

    @if(canEdit != null && canEdit && txn.getStatus() == BankTransaction.Status.POSTED)
        @template.shared.reverseForm(action = "/bank-transactions/" + txn.getId().toString() + "/reverse",
            confirmKey = "bt.view.reverseConfirm",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/BANK_TXN/${txn.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
