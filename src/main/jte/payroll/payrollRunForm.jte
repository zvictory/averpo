@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.web.PayrollRunForm
@import java.util.List
@param PayrollRunForm form
@param List<Contact> employees
@param String incomeTaxRate
@param String pensionRate
@param String socialTaxRate
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param String classMode = "OFF"
@param List<com.averpo.erp.shared.service.TxnClassService.ClassOption> classes = java.util.List.of()

<%-- Иш ҳақи ҳисоблаши FULL формаси (payroll.md): period + run_date +
     сатрлар. «Ходимларни тўлдириш» - HTMX prefill (tbody алмашади);
     ушланма/net устунлари ва Жами сатри ЖОНЛИ (қуйидаги скрипт,
     ставкалар кўриниш учун - ҳақиқий snapshot service'да). --%>
@template.layout.form(title = msg.lookup("prun.form.title"), closeHref = "/payroll", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="prunForm" method="post" action="/payroll">
        @template.shared.csrf(csrf = csrf)
        @if(form.getId() != null && !form.getId().isBlank())
            <input type="hidden" name="id" value="${form.getId()}">
        @endif
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1">${msg.localize("prun.col.period")} @template.shared.help(key = "field.payrollPeriod", msg = msg)</span>
                <input type="month" name="period" required
                       value="${form.getPeriod() != null ? form.getPeriod() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("prun.col.runDate")}
                <%-- data-autofill фақат ЯНГИ формада: controller қўйган
                     default сана period'га эргашади; edit'да сақланган
                     сана фойдаланувчиники - тегилмайди (DEC-071) --%>
                <input type="date" name="runDate" required
                       data-autofill="${form.getId() == null || form.getId().isBlank() ? "1" : null}"
                       value="${form.getRunDate() != null ? form.getRunDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            @if("PER_TXN".equals(classMode))
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("cls.label")}
                    @template.shared.classSelect(classes = classes, msg = msg,
                            name = "classId", selected = form.getClassId(),
                            cls = "mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark")
                </label>
            @endif
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("sinv.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <p class="mb-2 text-sm text-current/60">
            ${msg.localize("prun.form.ratesNote", incomeTaxRate, pensionRate, socialTaxRate)}
        </p>
        <%-- Барча устун ва Жами home валютада - валюта фақат gross
             сарлавҳасида кўринарди, ушланма/net/Жами яланғоч эди («пул
             ҳеч қачон яланғоч эмас» - Fmt.java:74). payrollRunView нақши:
             жадвал тепасида «Барча суммалар <home> да» изоҳи. --%>
        <p class="mb-2 text-sm text-current/60">${msg.localize("prun.view.amountsIn", homeCurrency)}</p>

        @template.shared.table(cls = "min-w-[960px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th class="w-10">#</th>
                <th class="min-w-[200px]">${msg.localize("prun.row.employee")}</th>
                <th>${msg.localize("prun.col.gross")} <span class="font-normal">${homeCurrency}</span></th>
                <th class="text-right">${msg.localize("prun.col.incomeTax")}</th>
                <th class="text-right">${msg.localize("prun.col.pension")}</th>
                <th class="text-right">${msg.localize("prun.col.socialTax")}</th>
                <th class="text-right">${msg.localize("prun.col.net")}</th>
                @if("PER_LINE".equals(classMode))
                    <th>${msg.localize("cls.label")}</th>
                @endif
                <th>${msg.localize("sinv.row.memo")}</th>
                <th class="w-10"></th>
            </tr>
            </thead>
            <tbody id="lineRows">
            @for(var i = 0; i < form.getLines().size(); i++)
                @template.payroll.payrollRunLineRow(index = i, employees = employees,
                        msg = msg, line = form.getLines().get(i),
                        classMode = classMode, classes = classes)
            @endfor
            </tbody>
            <tfoot>
            <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                <td colspan="2" class="text-right">${msg.localize("prun.form.total")}:</td>
                <td class="text-right" data-total-gross></td>
                <td class="text-right" data-total-income-tax></td>
                <td class="text-right" data-total-pension></td>
                <td class="text-right" data-total-social-tax></td>
                <td class="text-right" data-total-net></td>
                <td colspan="3"></td>
            </tr>
            </tfoot>
        `)
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;margin-bottom:1rem;">
            <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-info bg-info px-4 py-2 text-center text-sm font-medium tracking-wide text-on-info transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75"
                    hx-get="/payroll/prefill-rows"
                    hx-target="#lineRows" hx-swap="innerHTML">${msg.localize("prun.form.prefill")}</button>
            <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark"
                    hx-get="/payroll/line-row"
                    hx-vals='js:{index: Math.max(0, ...[...document.querySelectorAll("#lineRows tr.line")].map(r => 1 + Number(r.dataset.index || 0)))}'
                    hx-target="#lineRows" hx-swap="beforeend">${msg.localize("bt.form.addRow")}</button>
        </div>

        <p class="text-sm text-current/60">
            ${msg.localize("prun.form.note")}
        </p>
    </form>

    <script>
        (function () {
            // Жонли ҳисоб - ФАҚАТ кўриниш (ҳақиқий snapshot server'да,
            // HALF_UP 2 хона шу ерда ҳам такрорланади)
            var RATES = {
                income: Number("${incomeTaxRate}"),
                pension: Number("${pensionRate}"),
                social: Number("${socialTaxRate}")
            };
            function fmt(v) {
                return v.toLocaleString("en-US", {minimumFractionDigits: 2, maximumFractionDigits: 2})
                        .replace(/,/g, " ");
            }
            function pct(gross, rate) {
                return Math.round(gross * rate) / 100;
            }
            function recalc() {
                var totals = {g: 0, i: 0, p: 0, s: 0, n: 0};
                document.querySelectorAll("#lineRows tr.line").forEach(function (row) {
                    var raw = (row.querySelector("[data-gross]") || {value: ""}).value || "";
                    var gross = Number(raw.replace(/[\s ]/g, "").replace(",", "."));
                    if (!isFinite(gross) || gross <= 0) {
                        ["data-income-tax", "data-pension", "data-social-tax", "data-net"].forEach(function (a) {
                            var cell = row.querySelector("[" + a + "]");
                            if (cell) cell.textContent = "";
                        });
                        return;
                    }
                    var income = pct(gross, RATES.income);
                    var pension = pct(gross, RATES.pension);
                    var social = pct(gross, RATES.social);
                    var net = gross - income - pension;
                    row.querySelector("[data-income-tax]").textContent = fmt(income);
                    row.querySelector("[data-pension]").textContent = fmt(pension);
                    row.querySelector("[data-social-tax]").textContent = fmt(social);
                    row.querySelector("[data-net]").textContent = fmt(net);
                    totals.g += gross; totals.i += income; totals.p += pension;
                    totals.s += social; totals.n += net;
                });
                function set(a, v) {
                    var cell = document.querySelector("[" + a + "]");
                    if (cell) cell.textContent = v > 0 ? fmt(v) : "";
                }
                set("data-total-gross", totals.g);
                set("data-total-income-tax", totals.i);
                set("data-total-pension", totals.p);
                set("data-total-social-tax", totals.s);
                set("data-total-net", totals.n);
            }
            document.addEventListener("input", function (e) {
                if (e.target && e.target.hasAttribute("data-gross")) recalc();
            });
            // Сатр ўчирилганда ва HTMX prefill/қўшишдан кейин қайта ҳисоб
            document.addEventListener("payroll-recalc", recalc);
            document.body.addEventListener("htmx:afterSwap", recalc);
            recalc();
        })();

        // period ↔ run_date синхрони (DEC-071/BA-042, srRate
        // data-autofill нақши): period ўзгарса, фойдаланувчи run_date'га
        // қўлда тегмаган бўлса, сана period ойига мосланади - жорий ойда
        // бугунга (формага server қўйган default), бошқа ойда ой охирига.
        // Акс ҳолда default run_date BR-PYR-004 билан кафолатли рад берарди.
        // Server текшируви (validateHeader) ўз ўрнида қолади.
        (function () {
            var periodInput = document.querySelector('input[name="period"]');
            var dateInput = document.querySelector('input[name="runDate"]');
            if (!periodInput || !dateInput) return;
            // Дастлабки қийматлар server'дан компания вақт минтақасида
            // келган: initDate = бугун, initPeriod = жорий ой (/new)
            var initPeriod = periodInput.value;
            var initDate = dateInput.value;
            dateInput.addEventListener('input', function () { dateInput.dataset.autofill = ''; });
            periodInput.addEventListener('change', function () {
                if (dateInput.dataset.autofill !== '1') return;
                var p = periodInput.value;
                if (!p) return;
                if (p === initPeriod && initDate) { dateInput.value = initDate; return; }
                var parts = p.split('-');
                var lastDay = new Date(Number(parts[0]), Number(parts[1]), 0).getDate();
                dateInput.value = p + '-' + String(lastDay).padStart(2, '0');
            });
        })();
    </script>
`, actions = @`
    <a href="/payroll" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" type="submit" form="prunForm" name="action" value="draft">${msg.localize("prun.form.saveDraft")}</button>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="prunForm" name="action" value="post">
            ${msg.localize("prun.form.savePost")}</button>
    </div>
`)
