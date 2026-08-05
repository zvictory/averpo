// Курс блоки (DEC-097): валюта танлови + курс prefill (DEC-029) +
// кўриниш/home жами (DEC-088) + КУЧЛИ-ВАЛЮТА ОРИЕНТАЦИЯ (Fmt.orient
// қоидасининг JS кўзгуси) - ҲАММАСИ БИР ЖОЙДА. Форма id'ларига боғланмайди:
// data-атрибутлар билан form ичида scoped (rateBlock.jte маркапи).
//
// КРИТИК инвариант: САҚЛАНАДИГАН курс КАНОНИК (1 doc = rate home) -
// hidden name="exchangeRate" (data-rate-canonical). Server/servis/тестлар
// ТЕГИЛМАЙДИ. Кўринадиган input (data-rate-input, name'сиз) - кучли-валюта
// базисидаги ориентацияли қиймат; флипда visible = 1/canonical; кўриниши
// курс кўрсатиш қоидасида (averpoRateFmt - money-input.js, DEC-135).
(function () {
    'use strict';

    // Fmt.RATE_BASE_PRIORITY кўзгуси - ўзгарса иккала жойда бирга (спец E)
    var RATE_BASE_PRIORITY = ['USD', 'EUR', 'RUB', 'CNY'];
    // Fmt.RATE_INVERT_SCALE кўзгуси: тескари курс аниқлиги (сақланадиган scale)
    var INVERT_SCALE = 12;

    // Хом сон парси (бўшлиқ/вергул) - recompute IIFE'даги num() айнан
    function num(value) {
        var raw = (value || '').replace(/\s/g, '').replace(',', '.');
        var n = parseFloat(raw);
        return isNaN(n) ? 0 : n;
    }

    // NBSP + 2 хона кўрсатиш (home жами) - мавжуд fmt() айнан
    function fmt(n) {
        var sign = n < 0 ? '-' : '';
        var parts = Math.abs(n).toFixed(2).split('.');
        return sign + parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, String.fromCharCode(160)) + '.' + parts[1];
    }

    // Сонни хом матнга: trailing нолсиз (Fmt.n кўзгуси) - фақат hidden
    // каноник ёзувлари учун; кўринадиган қиймат averpoRateFmt'дан ўтади
    function plain(n) {
        if (!isFinite(n)) return '';
        // 12 хонагача - каноник scale; сўнг trailing нол қирқилади
        var s = n.toFixed(INVERT_SCALE);
        if (s.indexOf('.') >= 0) s = s.replace(/0+$/, '').replace(/\.$/, '');
        return s;
    }

    // Fmt.orient/baseIsA кўзгуси: 1 codeA = rate codeB → базис codeA'ми?
    function baseIsDoc(docCode, homeCode, canonical) {
        var pa = RATE_BASE_PRIORITY.indexOf(docCode);
        var pb = RATE_BASE_PRIORITY.indexOf(homeCode);
        if (pa >= 0 && pb >= 0) return pa < pb;
        if (pa >= 0 || pb >= 0) return pa >= 0;
        return canonical >= 1;
    }

    function init(block) {
        var form = block.closest('form');
        if (!form || block.__rateBlock) return;
        block.__rateBlock = true;

        var HOME = (block.dataset.home || '').trim().toUpperCase();
        var select = form.querySelector('[data-rate-currency]');
        var visibleInput = block.querySelector('[data-rate-input]');
        var hidden = block.querySelector('[data-rate-canonical]');
        var baseTag = block.querySelector('[data-rate-base]');
        var quoteTag = block.querySelector('[data-rate-quote]');
        var dateInput = block.dataset.date
                ? form.querySelector('input[name="' + block.dataset.date + '"]') : null;
        // Жами кўриниши (088) - Б гуруҳ формаларида йўқ (шунда шу қисм ишламайди)
        var totalEl = form.querySelector('[data-rate-total]');
        var homeRow = form.querySelector('[data-rate-home-row]');
        var homeEl = form.querySelector('[data-rate-home]');
        if (!select || !visibleInput || !hidden) return;

        function docCode() { return (select.value || HOME).trim().toUpperCase(); }
        function isForeign() { return docCode() !== HOME; }

        // Каноник (hidden)дан кўринишни (visible + ёрлиқ + флип) ҳосил қилади.
        // hidden'га ТЕГМАЙДИ - у авторитет. Хом каноник ҳеч қачон экранга чиқмайди.
        function orientFromCanonical() {
            var doc = docCode();
            if (doc === HOME) return;
            var canonical = num(hidden.value) || 1;
            var flipped = !baseIsDoc(doc, HOME, canonical);
            block.dataset.rateFlipped = flipped ? '1' : '0';
            if (baseTag) baseTag.textContent = flipped ? HOME : doc;
            if (quoteTag) quoteTag.textContent = flipped ? doc : HOME;
            // DEC-135: visible курс КЎРСАТИШ форматида (averpoRateFmt -
            // Fmt.rate/orientInput кўзгуси; server биринчи бўяш билан айнан
            // тенг бўлмаса қиймат «сакраб» кўринади). Флипсизда каноник
            // STRING узатилади - float думи артефактисиз стринг яхлитлаш;
            // флипда 1/x табиатан float - кўзгу 12 хонага кесиб олади.
            // hidden ХОМлигича қолади - яхлитлаш фақат экранда.
            var raw = hidden.value.trim();
            visibleInput.value = flipped ? window.averpoRateFmt(1 / canonical)
                    : (raw ? window.averpoRateFmt(raw) : '');
        }

        // Кўринадиган қийматдан каноникни (hidden) қайта ҳисоблайди -
        // фойдаланувчи visible'ни таҳрирлаганда. visible'га ТЕГМАЙДИ.
        function canonicalFromVisible() {
            var visible = num(visibleInput.value);
            var flipped = block.dataset.rateFlipped === '1';
            if (visible <= 0) { hidden.value = ''; return; }
            hidden.value = flipped ? plain(1 / visible) : plain(visible);
        }

        // --- Кўриниш (DEC-088): rate wrap кўрсат/яшир + home жами ---
        // home жами = doc жами × КАНОНИК (hidden'дан - флип математикасидан холи)
        function refresh() {
            var foreign = isForeign();
            block.style.display = foreign ? '' : 'none';
            if (homeRow) homeRow.style.display = foreign ? 'flex' : 'none';
            // DEC-107: форма ичидаги ташқи жами скриптлари (мас. JE'нинг
            // икки томонлама Дт/Кт home жамиси) каноник курс ўзгаришидан
            // хабардор бўлсин - refresh ҳар нуқтада (init/select/visible input/
            // prefill/сана) чақирилади. 8 ҳужжат формаси бу событиени
            // тингламайди (no-op) - маркап/хулқи ЎЗГАРМАЙДИ.
            form.dispatchEvent(new CustomEvent('rateblock:refresh'));
            if (!foreign || !totalEl || !homeEl) return;
            var canonical = num(hidden.value) || 1;
            homeEl.textContent = fmt(num(totalEl.textContent) * canonical);
        }

        // --- Prefill (DEC-029): /exchange-rates/lookup КАНОНИК қайтаради ---
        function canAutofill() {
            var v = visibleInput.value.trim();
            // «1» нейтрал қиймат DEC-135 дан бери форматланган («1.00»)
            // кўринишда ҳам келади - шунга num() орқали таққосланади
            return v === '' || num(v) === 1 || visibleInput.dataset.autofill === '1';
        }
        function prefillRate(force) {
            var code = docCode();
            if (code === HOME) {
                hidden.value = '1';
                visibleInput.value = '1';
                visibleInput.dataset.autofill = '1';
                return;
            }
            if (!canAutofill()) return;
            if (!force && visibleInput.value.trim() === '') return;
            var date = dateInput ? dateInput.value : '';
            fetch('/exchange-rates/lookup?currency=' + encodeURIComponent(code)
                    + '&date=' + encodeURIComponent(date))
                .then(function (r) { return r.ok ? r.text() : ''; })
                .then(function (canon) {
                    if (!canAutofill()) return;
                    var trimmed = (canon || '').trim();
                    // ФАҚАТ мусбат сон қабул қилинади: сессия эскириб lookup
                    // /login'га redirect бўлса HTML қайтаради - уни каноник
                    // деб ёзиб қўймаслик учун (мустаҳкамлик, хом нусхада йўқ эди)
                    if (trimmed && /^[0-9]+([.,][0-9]+)?$/.test(trimmed)) {
                        hidden.value = trimmed;         // КАНОНИК (home-per-doc)
                        orientFromCanonical();          // visible ориентацияли
                        visibleInput.dataset.autofill = '1';
                        refresh();
                    } else {
                        // DEC-154: lookup бўш/хато қайтарса каноник ТОЗАЛАНАДИ -
                        // «1» fallback билан сақланиб қолмасин (foreign ҳужжат 1:1
                        // курс → home қиймати бузиларди, прод BILL-2026-00002 айби).
                        // Бўш каноник server ҳимоясига (BR-BILL-009 оиласи -
                        // requireDocumentRate) тушиб рад этилади. Бу шохга ФАҚАТ
                        // canAutofill=true бўлса етилади (юқоридаги guard) - реал
                        // курсли таҳрир тегилмайди. Аввал шарт autofill==='1' эди -
                        // init'даги биринчи prefill'да у ҳали қўйилмагани учун
                        // «1» жимгина қолиб кетарди (шу тешик ёпилди).
                        hidden.value = '';
                        visibleInput.value = '';
                        visibleInput.dataset.autofill = '';
                        refresh();
                    }
                });
        }

        // Фойдаланувчи visible'ни ўзгартирса: autofill белгиси кетади, каноник
        // қайта ҳисобланади, home жами янгиланади (086 dispatchEvent сабоғи -
        // input event шу ерда ушланади)
        visibleInput.addEventListener('input', function () {
            visibleInput.dataset.autofill = '';
            canonicalFromVisible();
            refresh();
        });

        select.addEventListener('change', function () {
            var code = docCode();
            // Нарх/жами устунлари data-curtag - ДОК валюта (ориентация ЭМАС)
            form.querySelectorAll('[data-curtag]').forEach(function (el) { el.textContent = code; });
            if (code === HOME) {
                hidden.value = '1';
                visibleInput.value = '1';
                visibleInput.dataset.autofill = '1';
            } else {
                orientFromCanonical();   // янги doc + жорий каноник (prefill тузатади)
            }
            prefillRate(true);
            refresh();
        });

        if (dateInput) {
            dateInput.addEventListener('change', function () { prefillRate(false); });
        }

        // Жами матни recompute()'дан келади - MutationObserver билан кузатилади
        // (сатр қўшиш/ўчириш/HTMX оқимлари ҳам қамралади)
        if (totalEl) {
            new MutationObserver(refresh).observe(totalEl,
                    { childList: true, characterData: true, subtree: true });
        }

        // Бошланғич: server аллақачон ориентациялаган, лекин флип белгиси ва
        // ёрлиқларни JS ҳам бир хил ҳисоблаб мослигини кафолатлайди.
        // DEC-154 (HOTFIX): олдиндан foreign валюта қўйилган форма (PO→Bill
        // конверти, prefill) курс бўш/«1» билан юкланса init'да ҳужжат
        // САНАСИГА курс АВТО-тортилади (QBO: ҳужжат ўз санасида ўз курсини
        // олади) - акс ҳолда курс жимгина «1» бўлиб foreign ҳужжат 1:1 POSTED
        // бўлар, home/inventory қиймати бузиларди. canAutofill true фақат
        // курс йўқ/нейтрал (1) ёки авто-тўлдирилган ҳолатда - мавжуд ҳужжат
        // таҳрирининг РЕАЛ курси (≠1) сақланади (prefill ишламайди).
        if (isForeign() && canAutofill()) prefillRate(true);
        else if (isForeign()) orientFromCanonical();
        refresh();
    }

    document.querySelectorAll('[data-rate-block]').forEach(init);
})();
