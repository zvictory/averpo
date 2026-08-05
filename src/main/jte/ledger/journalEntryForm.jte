@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.ledger.web.JournalEntryForm
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.shared.service.TxnClassService.ClassOption
@import java.util.List
@param JournalEntryForm form
@param List<Account> accounts
@param String homeCurrency
@param List<Currency> currencies
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param String classMode = "OFF"
@param List<ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

@template.layout.form(title = msg.lookup("entries.form.title"), closeHref = "/journal-entries", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="jeForm" method="post" action="/journal-entries">
        @template.shared.csrf(csrf = csrf)
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("entries.form.date")}
                <input type="date" name="entryDate" required
                       value="${form.getEntryDate() != null ? form.getEntryDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <%-- Arbitr-107 (QBO parity): ҲУЖЖАТ валютаси + курс header'да -
                 shared.rateBlock реюзи (097). Ориентация/prefill/home жами
                 rate-block.js'да; hidden name="exchangeRate" КАНОНИК (server
                 айнан шуни олади). Валюта home бўлса курс блоки яширин. --%>
            @template.shared.rateBlock(currencies = currencies, selected = form.getCurrency(),
                    exchangeRate = form.getExchangeRate(), homeCurrency = homeCurrency,
                    dateInputName = "entryDate", labelKey = "entries.form.currency",
                    rateKey = "entries.form.rate", msg = msg)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("entries.form.description")}
                <input name="description"
                       value="${form.getDescription() != null ? form.getDescription() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            @if("PER_TXN".equals(classMode))
                <%-- PER_TXN (class-tracking.md): битта Йўналиш - сатрларга тарқатилади --%>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("cls.label")}
                    @template.shared.classSelect(classes = classes, msg = msg,
                            name = "classId", selected = form.getClassId(),
                            cls = "mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark")
                </label>
            @endif
        </div>

        @template.shared.table(cls = "min-w-[640px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th class="w-10">#</th>
                <th class="min-w-[220px]">${msg.localize("entries.col.account")}</th>
                <%-- Arbitr-107: Дт/Кт сарлавҳасида жорий ҳужжат валютаси коди
                     жонли (QBO «DEBITS (USD)»); data-curtag'ни rate-block.js
                     валюта танланганда янгилайди. --%>
                <th>${msg.localize("entries.col.debit")} <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></th>
                <th>${msg.localize("entries.col.credit")} <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></th>
                @if("PER_LINE".equals(classMode))
                    <th>${msg.localize("cls.label")}</th>
                @endif
                <th>${msg.localize("entries.col.memo")}</th>
                <th class="w-10"></th>
            </tr>
            </thead>
            <tbody id="lineRows">
            @for(var i = 0; i < form.getLines().size(); i++)
                @template.ledger.lineRow(index = i, accounts = accounts,
                        msg = msg, line = form.getLines().get(i),
                        classMode = classMode, classes = classes, canEdit = canEdit)
            @endfor
            </tbody>
        `)

        <div style="display:flex;gap:.5rem;margin-bottom:1rem;flex-wrap:wrap;">
            <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark"
                    hx-get="/journal-entries/line-row"
                    hx-vals='js:{index: Math.max(0, ...[...document.querySelectorAll("#lineRows tr")].map(r => 1 + Number(r.dataset.index || 0)))}'
                    hx-target="#lineRows" hx-swap="beforeend">${msg.localize("entries.form.addRow")}</button>
            <%-- Arbitr-107 (QBO «Clear all lines»): сатрлар олиб ташланмайди,
                 фақат қийматлари бўшатилади; жамилар дарҳол қайта ҳисобланади. --%>
            <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0" id="jeClearLines">${msg.localize("entries.form.clearLines")}</button>
        </div>

        <%-- Arbitr-088 1-банд: Дт/Кт жамилари ва баланс фарқи жадвал скролл ўрами
             скролл зонасидан ТАШҚАРИДА - кенг жадвалда ҳам доим кўз олдида.
             Nargiza-005 жонли скрипти id бўйича ёзади - jeDtLive/jeCtLive/
             jeDiffLive айнан сақланган, скрипт ўзгармаган. --%>
        <div style="display:flex;justify-content:flex-end;margin-bottom:1rem;">
            <div class="min-w-[260px] rounded-radius border border-outline bg-surface-alt px-4 py-3 font-semibold dark:border-outline-dark dark:bg-surface-dark-alt">
                <%-- Arbitr-107 (QBO «Total USD»): ҲУЖЖАТ валютасидаги жами -
                     фақат чет валютада кўринади (home бўлса doc==home). Дт/Кт
                     сатр суммаларининг оддий йиғиндиси (курссиз). --%>
                <div data-je-doc-row class="mb-2 border-b border-dashed border-outline pb-2 dark:border-outline-dark" style="${form.getCurrency() != null && !form.getCurrency().equals(homeCurrency) ? null : "display:none"}">
                    <div style="margin-bottom:.25rem;">${msg.localize("entries.view.total")} (<span data-curtag>${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span>)</div>
                    <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                        <span>${msg.localize("entries.col.debit")}</span>
                        <span id="jeDtDocLive" style="font-variant-numeric:tabular-nums;">0.00</span>
                    </div>
                    <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                        <span>${msg.localize("entries.col.credit")}</span>
                        <span id="jeCtDocLive" style="font-variant-numeric:tabular-nums;">0.00</span>
                    </div>
                </div>
                <%-- HOME валютадаги жами - ДОИМ (ledger home'да балансланади,
                     BR-LED-006). jeDtLive/jeCtLive/jeDiffLive id'лари АЙНАН
                     сақланган (DocFormPolishWebTest + жонли баланс контракти);
                     энди home = doc суммаси × КАНОНИК курс (hidden'дан). --%>
                <div style="margin-bottom:.25rem;">${msg.localize("entries.view.totalBase", homeCurrency)}</div>
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("entries.col.debit")}</span>
                    <span id="jeDtLive" style="font-variant-numeric:tabular-nums;">0.00</span>
                </div>
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("entries.col.credit")}</span>
                    <span id="jeCtLive" style="font-variant-numeric:tabular-nums;">0.00</span>
                </div>
                <div id="jeDiffLive" style="text-align:right;white-space:nowrap;"></div>
            </div>
        </div>

        <p class="text-sm text-current/60">
            ${msg.localize("entries.form.homeNote", homeCurrency)}
        </p>
        <%-- Arbitr-107 иловалар банди: янги (id'сиз) формада файл бириктириш
             учун аввал Draft сақлаш керак (QBO қолипи) - сақлагач КЎРИШ
             саҳифасида иловалар секцияси кўринади (journalEntryView). --%>
        <p class="text-sm text-current/60">
            📎 ${msg.localize("entries.form.attachHint")}
        </p>
    </form>

    <script>
        // Arbitr-107 (QBO parity): валюта/курс header'да - Дт/Кт жамилари
        // ҲУЖЖАТ валютасида (оддий йиғинди) ҲАМДА home валютада (× КАНОНИК
        // курс, hidden data-rate-canonical'дан) жонли кўрсатилади. Баланс
        // фарқи home'да (ledger шунда балансланади, BR-LED-006) - фақат
        // кўрсатиш, ҳақиқий текширув server'да. Сатр курс prefill'ини энди
        // rate-block.js қилади (сатр prefill IIFE'си ОЛИБ ТАШЛАНДИ -
        // валюта/курс битта, header'да).
        (function () {
            var form = document.getElementById('jeForm');
            var body = document.getElementById('lineRows');
            if (!form || !body) return;
            var hidden = form.querySelector('[data-rate-canonical]');  // header КАНОНИК курс
            var select = form.querySelector('[data-rate-currency]');   // header валюта
            var dtEl = document.getElementById('jeDtLive');            // home Дт
            var ctEl = document.getElementById('jeCtLive');            // home Кт
            var diffEl = document.getElementById('jeDiffLive');        // home баланс фарқи
            var dtDocEl = document.getElementById('jeDtDocLive');      // ҳужжат валюта Дт
            var ctDocEl = document.getElementById('jeCtDocLive');      // ҳужжат валюта Кт
            var docRow = form.querySelector('[data-je-doc-row]');
            if (!dtEl || !ctEl || !diffEl) return;
            var HOME = "${homeCurrency}";
            var DIFF_LABEL = "${msg.lookup("entries.form.diff")}";
            function num(value) {
                // \s NBSP'ни ҳам қамрайди (money-input гуруҳлаши), вергул ҳам қабул
                var raw = (value || '').replace(/\s/g, '').replace(',', '.');
                var n = parseFloat(raw);
                return isNaN(n) ? 0 : n;
            }
            function fmt(n) {
                // Fmt.money кўзгуси: 2 хона, нуқта каср, NBSP минг ажратгич
                var sign = n < 0 ? '-' : '';
                var parts = Math.abs(n).toFixed(2).split('.');
                return sign + parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, String.fromCharCode(160)) + '.' + parts[1];
            }
            function recompute() {
                // Битта КАНОНИК курс (флип математикасидан холи - hidden'дан)
                var canonical = hidden ? (num(hidden.value) || 1) : 1;
                var foreign = select ? ((select.value || HOME).trim().toUpperCase() !== HOME) : false;
                var docDt = 0, docCt = 0;
                body.querySelectorAll('tr.line').forEach(function (row) {
                    docDt += num((row.querySelector('input[name$=".debitAmount"]') || {}).value);
                    docCt += num((row.querySelector('input[name$=".creditAmount"]') || {}).value);
                });
                var homeDt = docDt * canonical, homeCt = docCt * canonical;
                if (dtDocEl) dtDocEl.textContent = fmt(docDt);
                if (ctDocEl) ctDocEl.textContent = fmt(docCt);
                if (docRow) docRow.style.display = foreign ? 'block' : 'none';
                dtEl.textContent = fmt(homeDt);
                ctEl.textContent = fmt(homeCt);
                var diff = homeDt - homeCt;
                // 2 хона кўрсатишда кўринадиган энг кичик фарқ - 0.005
                if (Math.abs(diff) >= 0.005) {
                    diffEl.textContent = DIFF_LABEL + ': ' + fmt(diff);
                    diffEl.style.color = 'var(--color-danger)';
                } else {
                    diffEl.textContent = homeDt > 0 ? '✓' : '';
                    diffEl.style.color = 'var(--color-success)';
                }
            }
            body.addEventListener('input', recompute);
            body.addEventListener('change', recompute);
            // Сатр ўчирилганда (✕ tr.remove) ҳам жамилар янгиланади
            body.addEventListener('click', function () { setTimeout(recompute, 0); });
            document.body.addEventListener('htmx:afterSwap', recompute);
            // Валюта/курс ўзгарса rate-block.js refresh()'да 'rateblock:refresh'
            // отилади - home жами янги каноник билан қайта ҳисобланади (async
            // prefill тугагач ҳам шу событие орқали янгиланади)
            form.addEventListener('rateblock:refresh', recompute);
            // Ҳаммасини тозалаш (QBO «Clear all lines»): сатрлар қолади, фақат
            // қийматлари бўшатилади. Комбонинг қидирув input'и ҳам <tr> ичида -
            // querySelectorAll('input') уни ҳам тозалайди; select index 0 га
            // (иккови бўш = мос, combo wiring'га тегилмайди)
            var clearBtn = document.getElementById('jeClearLines');
            if (clearBtn) clearBtn.addEventListener('click', function () {
                body.querySelectorAll('tr.line').forEach(function (row) {
                    row.querySelectorAll('input').forEach(function (el) { el.value = ''; });
                    row.querySelectorAll('select').forEach(function (el) { el.selectedIndex = 0; });
                });
                recompute();
            });
            recompute();
        })();
    </script>
`, actions = @`
    <a href="/journal-entries" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" type="submit" form="jeForm" name="action" value="draft">${msg.localize("entries.form.saveDraft")}</button>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="jeForm" name="action" value="post">
            ${msg.localize("entries.form.savePost")}</button>
    </div>
`)
