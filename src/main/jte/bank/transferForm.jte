@import com.averpo.erp.bank.web.BankTransactionForm
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import java.util.List
@param BankTransactionForm form
@param List<Account> bsAccounts
@param java.util.Map<String, String> bankBalances
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null

@template.layout.form(title = msg.lookup("transfer.form.title"), closeHref = "/transfers", lang = lang, content = @`
    @template.shared.alert(error = error)


    <%-- init() методини Alpine ЎЗИ чақиради (auto-init) - x-init ёзилмайди.
         Счёт танлагичлари shared/combobox (Arbitr-123): танлов value
         input'да, валюта/қолдиқ унинг data-cur/data-bal'ида - onPick
         change'ни тинглаб ҳолатни (from/to/cur/bal) янгилайди. --%>
    <form id="transferForm" method="post" action="/transfers"
          x-data="transferForm('${homeCurrency}')"
          x-on:change="onPick($event)"
          class="max-w-[660px] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        @template.shared.csrf(csrf = csrf)

        <div class="mb-5 flex flex-wrap gap-4">
            <%-- Т1: Манбадан - гуруҳли (счёт тури) combobox --%>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-w-[250px] flex-1">
                ${msg.localize("transfer.form.from")}
                !{var selFrom = bsAccounts.stream().filter(a -> a.getId().toString().equals(form.getBankAccountId())).findFirst().orElse(null);}
                @template.shared.combobox(name = "bankAccountId", msg = msg,
                        value = form.getBankAccountId(),
                        placeholder = msg.lookup("transfer.form.select"),
                        required = true,
                        cls = "mt-1 w-full",
                        dataCur = selFrom != null ? (selFrom.getCurrency() != null ? selFrom.getCurrency().getCode() : homeCurrency) : null,
                        dataBal = selFrom != null ? bankBalances.getOrDefault(selFrom.getId().toString(), "") : null,
                        options = @`
                            !{String lastGroupFrom = null;}
                            @for(Account account : bsAccounts)
                                !{var groupFrom = msg.lookup(account.getType().titleKey());}
                                @if(!groupFrom.equals(lastGroupFrom))
                                    @template.shared.comboGroup(label = groupFrom)
                                    !{lastGroupFrom = groupFrom;}
                                @endif
                                @template.shared.comboOption(value = account.getId().toString(),
                                        label = (account.getCode() != null ? account.getCode() + " " : "") + account.getName() + " - " + (account.getCurrency() != null ? account.getCurrency().getCode() : homeCurrency),
                                        selected = account.getId().toString().equals(form.getBankAccountId()),
                                        dataCur = account.getCurrency() != null ? account.getCurrency().getCode() : homeCurrency,
                                        dataBal = bankBalances.getOrDefault(account.getId().toString(), ""))
                            @endfor
                        `)
                <span x-show="fromBal" x-cloak class="text-sm text-current/60">
                    ${msg.localize("bt.form.balance")}: <b x-text="fromBal"></b></span>
            </label>

            <%-- Т1: Манзилга - Т2: бир счёт икки томонга танланса иккинчиси
                 тозаланади (onPick) --%>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-w-[250px] flex-1">
                ${msg.localize("transfer.form.to")}
                !{var selTo = bsAccounts.stream().filter(a -> a.getId().toString().equals(form.getToBankAccountId())).findFirst().orElse(null);}
                @template.shared.combobox(name = "toBankAccountId", msg = msg,
                        value = form.getToBankAccountId(),
                        placeholder = msg.lookup("transfer.form.select"),
                        required = true,
                        cls = "mt-1 w-full",
                        dataCur = selTo != null ? (selTo.getCurrency() != null ? selTo.getCurrency().getCode() : homeCurrency) : null,
                        dataBal = selTo != null ? bankBalances.getOrDefault(selTo.getId().toString(), "") : null,
                        options = @`
                            !{String lastGroupTo = null;}
                            @for(Account account : bsAccounts)
                                !{var groupTo = msg.lookup(account.getType().titleKey());}
                                @if(!groupTo.equals(lastGroupTo))
                                    @template.shared.comboGroup(label = groupTo)
                                    !{lastGroupTo = groupTo;}
                                @endif
                                @template.shared.comboOption(value = account.getId().toString(),
                                        label = (account.getCode() != null ? account.getCode() + " " : "") + account.getName() + " - " + (account.getCurrency() != null ? account.getCurrency().getCode() : homeCurrency),
                                        selected = account.getId().toString().equals(form.getToBankAccountId()),
                                        dataCur = account.getCurrency() != null ? account.getCurrency().getCode() : homeCurrency,
                                        dataBal = bankBalances.getOrDefault(account.getId().toString(), ""))
                            @endfor
                        `)
                <span x-show="toBal" x-cloak class="text-sm text-current/60">
                    ${msg.localize("bt.form.balance")}: <b x-text="toBal"></b></span>
            </label>
        </div>

        <div class="mb-3 flex flex-wrap items-end gap-4">
            <%-- 1-майдон: манба сумма. Каптион битта nowrap span'да -
                 flex-col label болакларни алоҳида flex item қилиб вертикал
                 синдирарди (Arbitr-134 Rate қолипи) --%>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1 whitespace-nowrap">${msg.localize("bt.form.fromAmount")} (<span x-text="fromCur"></span>)</span>
                <input name="fromAmount" size="14" class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" required
                       x-model="fromAmount" x-on:input="recalc('amount')"
                      >
            </label>
            <%-- 2-майдон: курс (битта чет валюта бўлганда - линкли) --%>
            <label x-show="distinctForeign === 1" x-cloak class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <%-- Arbitr-097б: кучли-валюта ориентация (base/quote динамик) -
                     «1 UZS = 0.000083 USD» эмас, «1 USD = ? UZS». Arbitr-108:
                     кўринадиган input оддий буфер (rateBuf) + class="money"
                     (калькулятор) + onRateInput (буфер→каноник); каноник rate
                     ва recalc ЎЗГАРМАЙДИ. --%>
                <span class="inline-flex items-center gap-1 whitespace-nowrap">${msg.localize("bt.form.rate")} (1 <span x-text="rateBase"></span> = ? <span x-text="rateQuote"></span>) @template.shared.help(key = "field.exchangeRate", msg = msg)</span>
                <%-- Arbitr-108: class="money" (мини-калькулятор) + оддий буфер
                     x-model="rateBuf" + x-on:input (буфер→каноник). fromAmount/
                     toAmount айнан шу нақш - курсор сакрамайди. data-rate-field
                     (Arbitr-135): калькулятор натижаси курс кўрсатиш форматида. --%>
                <input size="12" inputmode="decimal" data-rate-field class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" x-model="rateBuf"
                       x-on:input="onRateInput()" x-ref="rateInput"
                      >
            </label>
            <%-- 3-майдон: манзил сумма (валюта фарқли бўлганда). Каптион
                 nowrap span'да - 1-майдон изоҳидаги сабаб --%>
            <label x-show="cross" x-cloak class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1 whitespace-nowrap">${msg.localize("bt.form.toAmount")} (<span x-text="toCur"></span>)</span>
                <input name="toAmount" size="14" class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
                       x-model="toAmount" x-on:input="recalc('to')"
                      >
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("bt.form.date")}
                <input type="date" name="txnDate" required
                       value="${form.getTxnDate() != null ? form.getTxnDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <%-- Икки хил чет валюта (USD→EUR): кросс-курс home-нисбий эмас,
             ҳозирча иккита алоҳида курс қўлда (курс жадвалга ёзилмайди) --%>
        <div x-show="distinctForeign === 2" x-cloak class="mb-3 flex flex-wrap gap-4">
            <%-- Arbitr-097б: икки чет валюта - ҳар курс ўз жуфтига қараб
                 ориентацияли (base/quote динамик). Arbitr-108: кўринадиган input
                 оддий буфер (from2Buf/to2Buf) + class="money" + onFrom2/To2Input;
                 name'СИЗ - submit каноник fromRate2/toRate2 hidden орқали (пастда). --%>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1 whitespace-nowrap">${msg.localize("bt.form.rate")} (1 <span x-text="from2Base"></span> = ? <span x-text="from2Quote"></span>) @template.shared.help(key = "field.exchangeRate", msg = msg)</span>
                <input size="12" inputmode="decimal" data-rate-field class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" x-model="from2Buf"
                       x-on:input="onFrom2Input()" x-ref="from2Input"
                       x-bind:disabled="distinctForeign !== 2"
                      >
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1 whitespace-nowrap">${msg.localize("bt.form.toRate")} (1 <span x-text="to2Base"></span> = ? <span x-text="to2Quote"></span>) @template.shared.help(key = "field.exchangeRate", msg = msg)</span>
                <input size="12" inputmode="decimal" data-rate-field class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" x-model="to2Buf"
                       x-on:input="onTo2Input()" x-ref="to2Input"
                       x-bind:disabled="distinctForeign !== 2"
                      >
            </label>
            <p class="basis-full text-sm text-current/60">${msg.localize("transfer.form.bothForeignNote")}</p>
        </div>

        <%-- Курснинг ягона ҳақиқат манбаи - ориентацияли курс input'и
             (лабелида «1 USD = ? UZS»); бу ерда фақат conversionNote
             ҳинти (Arbitr-136) --%>
        <p x-show="cross" x-cloak class="mb-4 text-sm text-current/60">
            ${msg.localize("bt.form.conversionNote")}
        </p>

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark max-w-[520px]">
            ${msg.localize("bt.form.memo")}
            <%-- Otabek-004: кўп қаторли изоҳ (QBO/spec transfer.md) -
                 inventory формалари қолипи --%>
            <textarea name="memo" rows="3" maxlength="500"
                      class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">${form.getMemo() != null ? form.getMemo() : ""}</textarea>
        </label>

        <%-- Счёт қийматлари combobox value input'ларида (bankAccountId/
             toBankAccountId) - service мавжуд TransferData ўзгармаган --%>
        <%-- Курс POST майдонлари - ҲАР ДОИМ КАНОНИК («1 чет = ? home»),
             Arbitr-097б: кўринадиган input ориентацияли бўлса ҳам server
             айнан шу каноникни олади (submit ЎЗГАРМАЙДИ). Битта чет
             валютада курс ўша (чет) томонга; икки чет валютада ҳар икки
             канониик fromRate2/toRate2. home тарафи бўш (service курс=1). --%>
        <input type="hidden" name="exchangeRate"
               x-bind:value="distinctForeign === 2 ? fromRate2 : (fromForeign ? rate : '')">
        <input type="hidden" name="toRate"
               x-bind:value="distinctForeign === 2 ? toRate2 : (toForeign ? rate : '')">
    </form>

    <script>
        function transferForm(home) {
            return {
                home: home,
                from: '', to: '',
                fromCur: home, toCur: home,
                fromBal: '', toBal: '',
                fromAmount: '${form.getFromAmount() != null ? form.getFromAmount() : ""}',
                toAmount: '${form.getToAmount() != null ? form.getToAmount() : ""}',
                rate: '${form.getExchangeRate() != null ? form.getExchangeRate() : (form.getToRate() != null ? form.getToRate() : "")}',
                fromRate2: '${form.getExchangeRate() != null ? form.getExchangeRate() : ""}',
                toRate2: '${form.getToRate() != null ? form.getToRate() : ""}',
                <%-- Arbitr-108: кўринадиган курс буферлари (ориентацияли, оддий
                     string - computed ЭМАС). Ёзаётганда буфер қайта ёзилмайди
                     (курсор жойида); onRateInput каноникни синхронлайди,
                     syncRateBuffers фақат ташқи ўзгаришда буферни янгилайди. --%>
                rateBuf: '', from2Buf: '', to2Buf: '',

                init() {
                    // Бошланғич ҳолат combobox value input'ларидан (server
                    // value + data-cur/data-bal'ни render қилган - Alpine
                    // init тартибидан мустақил ўқилади)
                    this.syncSide('bankAccountId');
                    this.syncSide('toBankAccountId');
                    var self = this;
                    // Arbitr-108: бошланғич буферлар (round-trip каноникдан
                    // ориентацияли) - $nextTick: x-ref'лар шу пайтга тайёр
                    this.$nextTick(function () { self.syncRateBuffers(); });
                },

                // Танланган счёт ҳолати value input'дан ўқилади (қиймат +
                // data-cur/data-bal - pgCombo танловда li'дан кўчиради)
                syncSide(name) {
                    var input = this.$root.querySelector('input[name=' + name + ']');
                    var v = input ? input.value : '';
                    var cur = v && input.dataset.cur ? input.dataset.cur : this.home;
                    var bal = v ? (input.dataset.bal || '') : '';
                    if (name === 'bankAccountId') {
                        this.from = v; this.fromCur = cur; this.fromBal = bal;
                    } else {
                        this.to = v; this.toCur = cur; this.toBal = bal;
                    }
                },

                // Combobox change'и (form даражасида тингланади): ҳолат
                // янгиланади; Т2 - бир счёт икки томонга танланса иккинчиси
                // тозаланади (change қайта отилиб onPick ўзи синхронлайди)
                onPick(e) {
                    var name = e.target && e.target.name;
                    if (name !== 'bankAccountId' && name !== 'toBankAccountId') return;
                    this.syncSide(name);
                    if (this.from && this.from === this.to) {
                        var otherName = name === 'bankAccountId' ? 'toBankAccountId' : 'bankAccountId';
                        var other = this.$root.querySelector('input[name=' + otherName + ']');
                        other.value = '';
                        other.dispatchEvent(new Event('change', { bubbles: true }));
                        return;
                    }
                    this.prefillRate();
                    this.recalc('amount');
                    // Валюта алмашди - буферларни янги ориентацияга мослаш
                    // (prefill async'и келганда яна синхронланади)
                    this.syncRateBuffers();
                },

                get fromForeign() { return this.from && this.fromCur !== this.home; },
                get toForeign() { return this.to && this.toCur !== this.home; },
                get cross() { return this.from && this.to && this.fromCur !== this.toCur; },
                // қатнашаётган ноёб чет валюталар сони (0/1/2)
                get distinctForeign() {
                    var s = {};
                    if (this.fromForeign) s[this.fromCur] = 1;
                    if (this.toForeign) s[this.toCur] = 1;
                    return Object.keys(s).length;
                },
                // битта чет валюта режимида курс қайси валютага тегишли
                get rateCur() { return this.fromForeign ? this.fromCur : this.toCur; },

                num(v) {
                    if (!v) return NaN;
                    return parseFloat(String(v).replace(/[\s ]/g, '').replace(',', '.'));
                },

                // Arbitr-097б: кучли-валюта ориентация (Fmt.orient JS кўзгуси,
                // 097 rate-block.js нақши). ФАҚАТ кўрсатиш қатлами - каноник
                // моделлар (rate/fromRate2/toRate2 = «1 чет = ? home») ва
                // recalc/submit ЎЗГАРМАЙДИ.
                RATE_PRIORITY: ['USD', 'EUR', 'RUB', 'CNY'],
                // 1 codeA = rateStr codeB → {base, quote, value(хом), flipped}
                orient(codeA, codeB, rateStr) {
                    var rate = this.num(rateStr);
                    if (!rate || rate <= 0 || !codeA || !codeB || codeA === codeB) {
                        return { base: codeA, quote: codeB, value: rateStr || '', flipped: false };
                    }
                    var pa = this.RATE_PRIORITY.indexOf(codeA);
                    var pb = this.RATE_PRIORITY.indexOf(codeB);
                    var baseIsA;
                    if (pa >= 0 && pb >= 0) baseIsA = pa < pb;
                    else if (pa >= 0 || pb >= 0) baseIsA = pa >= 0;
                    else baseIsA = rate >= 1;
                    if (baseIsA) return { base: codeA, quote: codeB, value: rateStr, flipped: false };
                    return { base: codeB, quote: codeA, value: this.invert(rate), flipped: true };
                },
                // 1/x, 12 хона (каноник scale, 097 тузоғи) - trailing нолсиз
                invert(rate) {
                    var v = 1 / this.num(rate);
                    if (!isFinite(v)) return '';
                    var s = v.toFixed(12);
                    if (s.indexOf('.') >= 0) s = s.replace(/0+$/, '').replace(/\.$/, '');
                    return s;
                },
                // Курс ёрлиғи base/quote - фақат КОД (кучли валюта ориентацияси,
                // 097б). Кўринадиган ҚИЙМАТ энди computed ЭМАС - оддий буфер
                // (rateBuf/from2Buf/to2Buf), Arbitr-108: computed getter флипда
                // invert(invert(v)) float шовқини билан инпутни қайта ёзиб
                // курсорни охирига сакратар эди. Буфер↔каноник синхрони
                // onRateInput (ёзишда) + syncRateBuffers (ташқи ўзгаришда).
                get rateBase() { return this.orient(this.rateCur, this.home, this.rate).base; },
                get rateQuote() { return this.orient(this.rateCur, this.home, this.rate).quote; },
                get from2Base() { return this.orient(this.fromCur, this.home, this.fromRate2).base; },
                get from2Quote() { return this.orient(this.fromCur, this.home, this.fromRate2).quote; },
                get to2Base() { return this.orient(this.toCur, this.home, this.toRate2).base; },
                get to2Quote() { return this.orient(this.toCur, this.home, this.toRate2).quote; },

                // Кўрсатиш формати - Fmt.rate/averpoRateFmt кўзгуси (Arbitr-135):
                // >= 1 → қатъий 2 хона NBSP гуруҳ билан, < 1 → макс 8 хона
                // trailing нолсиз. Кўзгуга СТРИНГ узатилади (cleanNum) - каноник
                // аниқлиги float думига йўқолмасин. ФАҚАТ ташқи буфер
                // тўлдиришда (syncRateBuffers) - ёзиш пайтида ЭМАС (ёзаётганда
                // фойдаланувчи қиймати айнан сақланади).
                fmtRate(v) {
                    var n = this.num(v);
                    if (!isFinite(n) || isNaN(n) || n <= 0) return v == null ? '' : String(v);
                    return window.averpoRateFmt(this.cleanNum(v));
                },
                // Пул форматидаги NBSP (JS \s уни қамрайди) ва вергулни тозалаб
                // хом сон STRING - каноник аниқлиги сақланади (num float қиларди)
                cleanNum(v) { return String(v == null ? '' : v).replace(/\s/g, '').replace(',', '.'); },
                // Кўринадиган (ориентацияли) буфер каноникка ТЕСКАРИ ёзиладими:
                // кучли валюта home тарафда бўлса (валюта ЖУФТИ бўйича - курс
                // қийматига боғлиқ ЭМАС, шунда ёзаётганда барқарор). Иккови
                // priority'да бўлмаса кўринадиган ≥1 → base=doc (fallback).
                rateFlipped(docCur, visibleVal) {
                    var pa = this.RATE_PRIORITY.indexOf(docCur);
                    var pb = this.RATE_PRIORITY.indexOf(this.home);
                    if (pa >= 0 && pb >= 0) return pa > pb;
                    if (pa >= 0 || pb >= 0) return pb >= 0;
                    var n = this.num(visibleVal);
                    return isFinite(n) && n > 0 ? n < 1 : false;
                },
                // Кўринадиган буфердан каноник курс («1 чет = ? home»): флипда
                // 1/буфер (12 хона), акс ҳолда буфер айнан (аниқлик сақланади).
                canonicalFromBuf(docCur, buf) {
                    var n = this.num(buf);
                    if (!isFinite(n) || isNaN(n) || n <= 0) return '';
                    return this.rateFlipped(docCur, buf) ? this.invert(n) : this.cleanNum(buf);
                },
                // Каноникдан кўринадиган буферни (ориентацияли, курс кўрсатиш
                // форматида) қайта тиклайди - счёт/валюта алмашганда, prefill
                // келганда, init'да, тескари ҳисобда (recalc 'to', Arbitr-136).
                // ФАҚАТ майдон ФОКУСДА БЎЛМАСА ($refs vs activeElement) -
                // фойдаланувчи ёзаётган буфер ҲЕЧ ҚАЧОН қайта ёзилмайди.
                syncRateBuffers() {
                    var a = document.activeElement;
                    if (this.$refs.rateInput !== a) {
                        this.rateBuf = this.fmtRate(this.orient(this.rateCur, this.home, this.rate).value);
                    }
                    if (this.$refs.from2Input !== a) {
                        this.from2Buf = this.fmtRate(this.orient(this.fromCur, this.home, this.fromRate2).value);
                    }
                    if (this.$refs.to2Input !== a) {
                        this.to2Buf = this.fmtRate(this.orient(this.toCur, this.home, this.toRate2).value);
                    }
                },
                // Ҳар 'input'да: кўринадиган буфердан каноникни синхронлайди.
                // Буфер ЎЗГАРМАЙДИ (курсор жойида қолади). recalc/hidden каноник
                // билан ишлайверади (097б дизайни).
                onRateInput() { this.rate = this.canonicalFromBuf(this.rateCur, this.rateBuf); this.recalc('rate'); },
                onFrom2Input() { this.fromRate2 = this.canonicalFromBuf(this.fromCur, this.from2Buf); },
                onTo2Input() { this.toRate2 = this.canonicalFromBuf(this.toCur, this.to2Buf); },

                // Курс prefill: битта чет валюта режимида, курс бўш бўлса
                // каталогдан ҳужжат санасига олинади
                prefillRate() {
                    if (this.distinctForeign !== 1) return;
                    if (this.rate && String(this.rate).trim() !== '') return;
                    var code = this.rateCur, self = this;
                    var date = (document.querySelector('input[name=txnDate]') || {}).value || '';
                    fetch('/exchange-rates/lookup?currency=' + encodeURIComponent(code)
                            + '&date=' + encodeURIComponent(date))
                        .then(function (r) { return r.ok ? r.text() : ''; })
                        .then(function (rate) {
                            // Arbitr-097 guard: фақат мусбат сон қабул (сессия
                            // эскириб /login HTML қайтарса каноникка ёзилмасин)
                            var t = (rate || '').trim();
                            if (t && /^[0-9]+([.,][0-9]+)?$/.test(t)) {
                                self.rate = t; self.recalc('rate'); self.syncRateBuffers();
                            }
                        });
                },

                // Т3: уч майдон боғланиши (битта чет валюта, бир томони home)
                recalc(src) {
                    if (!this.cross) { this.toAmount = this.fromAmount; return; }
                    if (this.distinctForeign !== 1) return; // икки чет валюта - қўлда
                    var fa = this.num(this.fromAmount), r = this.num(this.rate), ta = this.num(this.toAmount);
                    var fromIsHome = !this.fromForeign; // манба home бўлса, курс манзил (чет)га
                    if (src === 'to') {
                        if (fa > 0 && ta > 0) {
                            var nr = fromIsHome ? (fa / ta) : (ta / fa);
                            this.rate = nr.toFixed(6).replace(/\.?0+$/, '');
                            // Кўринадиган курс буфери ҳам янги каноникдан
                            // тикланади (Arbitr-136) - фойдаланувчи сумма
                            // майдонида ёзяпти, курс input фокусда эмас,
                            // шунга activeElement ҳимояси (108) уни ёзади
                            this.syncRateBuffers();
                        }
                    } else {
                        if (fa > 0 && r > 0) {
                            // Авто-ҳисобланган сумма пул форматида ёзилади
                            // (averpoMoneyFmt: 2 хона + NBSP гуруҳ) - x-model
                            // программ ёзувини reformat кўрмайди (у фақат
                            // клавиатура input'ида ишлайди), шунга формат
                            // манбада берилади; num()/submit NBSP'га чидамли
                            this.toAmount = window.averpoMoneyFmt(fromIsHome ? (fa / r) : (fa * r));
                        }
                    }
                }
            };
        }
    </script>
`, actions = @`
    <a href="/transfers" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="transferForm">
            ${msg.localize("bt.form.save")}</button>
    </div>
`)
