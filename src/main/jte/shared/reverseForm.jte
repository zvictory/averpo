@import com.averpo.erp.i18n.Msg
@param String action
@param String confirmKey
@param String today
@param org.springframework.security.web.csrf.CsrfToken csrf
@param Msg msg
@param String labelPrefix = "bt.view"

<%-- ARBITR-098 (UI-LIB): сторно формаси (сана + сабаб + confirm) - 15
     view'да айнан бир хил эди. action - POST манзили; confirmKey -
     confirm() матни калити (оилага хос: sinv.view.reverseConfirm...);
     today - сана prefill (контроллер беради); labelPrefix - учта label
     калитининг префикси: <prefix>.reversalDate / .reason / .reverse.
     Мавжуд калит тарқалиши: bt.view (9 view, шунга default), bill.view,
     sinv.view, entries.view. Қўшимча майдонли вариант чиқса - у ўз
     ҳолида қолади, зўрлаб умумлаштирилмайди (карта қоидаси). --%>
<form method="post" action="${action}"
      onsubmit="return confirm('${msg.lookup(confirmKey)}');"
      class="flex max-w-[640px] flex-wrap items-end gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
    @template.shared.csrf(csrf = csrf)
    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize(labelPrefix + ".reversalDate")}
        <input type="date" name="reversalDate" value="${today}" required
               class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
    </label>
    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-w-[200px] flex-1">${msg.localize(labelPrefix + ".reason")}
        <input name="reason" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
    </label>
    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-warning bg-warning px-4 py-2 text-center text-sm font-medium tracking-wide text-on-warning transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-warning active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize(labelPrefix + ".reverse")}</button>
</form>
