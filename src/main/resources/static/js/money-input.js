// Пул киритиш формати (доимий қоида): input.money майдонларида ёзиш
// пайтида бутун қисм ҳар 3 хонадан NBSP билан бўлинади (Fmt экран
// формати билан бир хил - DEC-011), каср ажратгичи НУҚТА (терилган
// вергул нуқтага айланади), submit'да бўшлиқлар олиб ташланади -
// серверга тоза сон боради.
//
// DEC-135: window.averpoRateFmt - Fmt.rate'нинг ЯГОНА JS кўзгуси
// (курс КЎРСАТИШ формати). Шу файлда, чунки money-input.js иккала
// layout ҳам улайдиган ягона умумий скрипт (127 прецеденти) ва иккала
// layout'да rate-block.js'дан ОЛДИН юкланади - rate-block.js,
// transferForm Alpine'и ва қуйидаги калькулятор учта нусха ўрнига
// биттадан фойдаланади (нусхалар вақт ўтиб фарқланиб кетарди).
(function () {
    'use strict';

    // Fmt.group кўзгуси: бутун қисм ҳар 3 хонадан NBSP билан
    // (rate-block.js fmt() идиоми - кўринмас литерал белги ўрнига)
    function groupInt(intPart) {
        return intPart.replace(/\B(?=(\d{3})+(?!\d))/g, String.fromCharCode(160));
    }

    // Белгисиз ўнлик матнни places хонага HALF_UP яхлитлайди - СТРИНГ
    // рақамларида. toFixed АТАЙЛАБ эмас: float'да 1.005 аслида
    // 1.00499... бўлиб toFixed(2)="1.00" чиқади - Java BigDecimal
    // HALF_UP ("1.01") билан фарқ қиларди, карта эса Java/JS айнан
    // бир хиллигини талаб қилади.
    function roundHalfUp(str, places) {
        var dot = str.indexOf('.');
        var intPart = dot < 0 ? str : str.slice(0, dot);
        var frac = dot < 0 ? '' : str.slice(dot + 1);
        if (intPart === '') intPart = '0';
        if (frac.length <= places) {
            while (frac.length < places) frac += '0';
            return intPart + '.' + frac;
        }
        // Сақланадиган рақамлар қатори + кесилган биринчи рақам >= 5
        // бўлса охиридан кўтарилувчи +1 (тўлиқ 9'ларда олдинга "1")
        var digits = (intPart + frac.slice(0, places)).split('');
        if (frac.charCodeAt(places) >= 53) {
            var i = digits.length - 1;
            for (; i >= 0; i--) {
                if (digits[i] === '9') { digits[i] = '0'; }
                else { digits[i] = String(+digits[i] + 1); break; }
            }
            if (i < 0) digits.unshift('1');
        }
        var joined = digits.join('');
        return joined.slice(0, joined.length - places) + '.' + joined.slice(joined.length - places);
    }

    // Кирувчини (сон ёки хом матн - NBSP/бўшлиқ/вергул чидамли) белгили
    // ўнлик матнга нормаллайди; бузуқ/чексиз қиймат - null
    function normalize(value) {
        var s;
        if (typeof value === 'number') {
            if (!isFinite(value)) return null;
            // 12 хона - каноник scale кўзгуси (Fmt.RATE_INVERT_SCALE);
            // 1e21 дан катталарда toFixed илмий кўринишга ўтади - бузуқ
            s = value.toFixed(12);
            if (s.indexOf('e') >= 0 || s.indexOf('E') >= 0) return null;
        } else {
            s = String(value == null ? '' : value).replace(/\s/g, '').replace(',', '.');
            if (!/^-?(\d+\.?\d*|\.\d+)$/.test(s)) return null;
        }
        return s;
    }

    // Курс КЎРСАТИШ формати (Fmt.rate кўзгуси, DEC-135): қиймат >= 1
    // → қатъий 2 хона + NBSP минг ажратгич (12 090.45); қиймат < 1 →
    // макс 8 хона, trailing нолсиз (0.00008334; 0.5 → 0.5). Тармоқ
    // танлови яхлитлашдан ОЛДИН - 0.999999995 → «1» (Fmt.rate билан
    // бир хил ҳужжатланган чегара). Бузуқ қиймат - бўш сатр.
    window.averpoRateFmt = function (value) {
        var s = normalize(value);
        if (s === null) return '';
        var places = parseFloat(s) >= 1 ? 2 : 8;
        var neg = s.charAt(0) === '-';
        var r = roundHalfUp(neg ? s.slice(1) : s, places);
        if (places === 8) {
            // trailing нолсиз - каср бутунлай тушиб қолиши мумкин (0.999999995 → 1)
            r = r.replace(/0+$/, '').replace(/\.$/, '');
        }
        var d = r.indexOf('.');
        var intPart = d < 0 ? r : r.slice(0, d);
        return (neg ? '-' : '') + groupInt(intPart) + (d < 0 ? '' : r.slice(d));
    };

    // Пул СУММА кўрсатиш формати (Fmt.money кўзгуси): қатъий 2 хона +
    // NBSP минг ажратгич (1 506 125.00). Программ ҳисобланган сумма
    // (масалан transfer'да Манзил сумма) x-model орқали ёзилади -
    // reformat фақат клавиатура input событиесида ишлагани учун формат
    // шу кўзгу орқали берилади; акс ҳолда авто-қиймат хом кўринарди.
    window.averpoMoneyFmt = function (value) {
        var s = normalize(value);
        if (s === null) return '';
        var neg = s.charAt(0) === '-';
        var r = roundHalfUp(neg ? s.slice(1) : s, 2);
        var d = r.indexOf('.');
        return (neg ? '-' : '') + groupInt(r.slice(0, d)) + r.slice(d);
    };
})();
//
// Мини-калькулятор (DEC-064, spec: ui-navigation-display.md W):
// қиймат «=» билан бошланса Excel услуби ифода режими - фақат + - * /
// (аввал */ , кейин +-), blur/Enter'да натижа форматланиб ёзилади.
// eval() АТАЙЛАБ ишлатилмайди (хавфсизлик) - қўлда токенлаш. Хато
// ифода ёки нолга бўлиш - қиймат ўз ҳолича қолади (alert йўқ),
// server валидацияси ушлайди.
(function () {
    function reformat(el) {
        // Каретка тиклаш бирлиги: рақам ВА каср ажратгич (нуқта/вергул).
        // Ажратгич саналмаса «12.» терилганда каретка нуқта ОЛДИГА тушиб,
        // кейинги рақам «125.» бўлиб кетади (нуқта рақам эмаслигидан
        // тиклаш уни четлаб ўтарди).
        var caretUnits = 0, sel = el.selectionStart || 0;
        for (var i = 0; i < sel; i++) {
            if (/[0-9.,]/.test(el.value.charAt(i))) caretUnits++;
        }
        var raw = el.value.replace(/[\s ]/g, '');
        var neg = raw.charAt(0) === '-';
        // Вергул ҳам ўнлик ажратгич сифатида қабул қилинади
        raw = raw.replace(/[^0-9.,]/g, '').replace(/,/g, '.');
        var dot = raw.indexOf('.');
        var intPart = dot < 0 ? raw : raw.slice(0, dot);
        var frac = dot < 0 ? '' : '.' + raw.slice(dot + 1).replace(/\./g, '');
        // NBSP (U+00A0) - Fmt экран формати билан бир хил минг ажратгич
        var grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
        el.value = (neg ? '-' : '') + grouped + frac;
        // Caret жойи: аввалги бирликлар (рақам + ажратгич) сонига қараб
        // қайта топилади; янги матнда вергул йўқ - у нуқтага айланган
        var pos = 0, cnt = 0;
        while (pos < el.value.length && cnt < caretUnits) {
            if (/[0-9.]/.test(el.value.charAt(pos))) cnt++;
            pos++;
        }
        if (el.setSelectionRange && document.activeElement === el) {
            el.setSelectionRange(pos, pos);
        }
    }
    // «=» ифодасини eval'СИЗ ҳисоблайди: токенлаш (сон/оператор), икки
    // босқич - аввал */ (чапдан ўнгга), кейин +-. Оператордан кейинги
    // ёки бошдаги «-» унар минус сифатида сонга ёпишади («=5*-2» ҳам
    // ишлайди). Хато (нотўғри токен, охири оператор, нолга бўлиш,
    // Infinity/NaN) - null: чақирувчи қийматни ўз ҳолича қолдиради.
    function calc(expr) {
        var s = expr.replace(/[\s ]/g, '').replace(/,/g, '.');
        if (!s) return null;
        var tokens = [], i = 0, prevIsNumber = false;
        while (i < s.length) {
            var ch = s.charAt(i);
            if (ch === '+' || ch === '*' || ch === '/'
                    || (ch === '-' && prevIsNumber)) {
                tokens.push(ch); i++; prevIsNumber = false; continue;
            }
            var m = /^-?(\d+(\.\d*)?|\.\d+)/.exec(s.slice(i));
            if (!m) return null;
            tokens.push(parseFloat(m[0]));
            i += m[0].length;
            prevIsNumber = true;
        }
        if (!tokens.length || typeof tokens[tokens.length - 1] !== 'number') {
            return null;
        }
        // 1-босқич: * ва / жойида ҳисобланиб рўйхат қисқаради
        var flat = [tokens[0]];
        for (var j = 1; j < tokens.length; j += 2) {
            var op = tokens[j], num = tokens[j + 1];
            if (typeof op !== 'string' || typeof num !== 'number') return null;
            if (op === '*' || op === '/') {
                if (op === '/' && num === 0) return null;
                var prev = flat.pop();
                flat.push(op === '*' ? prev * num : prev / num);
            } else {
                flat.push(op, num);
            }
        }
        // 2-босқич: қолган + ва - чапдан ўнгга
        var result = flat[0];
        for (var k = 1; k < flat.length; k += 2) {
            result = flat[k] === '+' ? result + flat[k + 1]
                                     : result - flat[k + 1];
        }
        return isFinite(result) ? result : null;
    }

    // «=» режимидаги майдонни ҳисоблаб форматлаб ёзади. float қолдиқ
    // думи (0.1+0.2 ҳолати) toFixed(10) билан кесилади - пул/миқдор
    // аниқлигига бемалол етади; exponential кўриниш (жуда катта сон)
    // хато ҳисобланади. Курс майдонида (data-rate-field, DEC-135)
    // натижа averpoRateFmt билан курс кўрсатиш қоидасида ёзилади -
    // экрандаги қиймат submit'га айнан тенг (WYSIWYG); пул/миқдор
    // майдонлари аввалгидек хом қолади. false = «=» режими эмас.
    function applyCalc(el) {
        if (el.value.charAt(0) !== '=') return false;
        var r = calc(el.value.slice(1));
        if (r !== null) {
            var isRate = el.matches && el.matches('[data-rate-field]');
            var v = isRate ? window.averpoRateFmt(r)
                           : String(Number(r.toFixed(10)));
            if (v && v.indexOf('e') < 0 && v.indexOf('E') < 0) {
                el.value = v;
                reformat(el);
                // DEC-086: программатик .value ёзуви input чиқармайди -
                // жонли ҳисоблар (transfer recalc x-on:input, ҳужжат
                // формаларининг body-delegation итоглари) шу event'га
                // қарайди, шусиз музлаб қолади. bubbles шарт - ҳамма
                // тингловчи delegation'да. Ўз document-level input
                // тингловчимиз ҳам қайта ишлайди - қиймат энди «=» билан
                // бошланмагани учун idempotent reformat, зарарсиз.
                // DOMContentLoaded reformat'ига АТАЙЛАБ қўшилмаган -
                // server қийматини безашда event юборилса rate autofill
                // байроқлари сохта «қўлда ўзгартирилган» бўлиб қоларди.
                el.dispatchEvent(new Event('input', {bubbles: true}));
            }
        }
        return true;
    }

    document.addEventListener('input', function (e) {
        var el = e.target;
        if (el && el.matches && el.matches('input.money')) {
            // «=» режимида формат аралашмайди - ифода эркин терилади
            if (el.value.charAt(0) === '=') return;
            reformat(el);
        }
    });
    // blur ўрнига focusout - у bubble қилади (delegation ишлайди)
    document.addEventListener('focusout', function (e) {
        var el = e.target;
        if (el && el.matches && el.matches('input.money')) applyCalc(el);
    });
    // «=» режимида Enter: submit ТЎХТАТИЛАДИ - аввал ҳисоб (spec W0)
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Enter') return;
        var el = e.target;
        if (el && el.matches && el.matches('input.money')
                && el.value.charAt(0) === '=') {
            e.preventDefault();
            applyCalc(el);
        }
    });
    // capture=true: HTMX/oddiy submit'дан олдин тозалаш кафолатланади
    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!form || !form.querySelectorAll) return;
        form.querySelectorAll('input.money').forEach(function (el) {
            // Ҳисобланмай қолган «=» ифода (масалан submit тугма
            // click'ида focusout'дан аввал) - тозалашдан олдин уриниш
            applyCalc(el);
            el.value = el.value.replace(/[\s ]/g, '');
        });
    }, true);
    // Edit формада серверда келган қиймат ҳам форматланиб кўрсатилади
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('input.money').forEach(function (el) {
            if (el.value) reformat(el);
        });
    });
    // DEC-162: HTMX partial (drawer таҳрири, payroll prefill сатрлари)
    // билан юкланган формада DOMContentLoaded ОТИЛМАЙДИ - server пул
    // қиймати хом (гуруҳланмаган) қоларди. Swap тугагач swap target
    // доирасидаги input.money'ларни худди юқоридаги DOMContentLoaded блоки
    // каби форматлаймиз. МУҲИМ: input event ЮБОРМАЙМИЗ (203-213 изоҳ: server
    // қийматда event rate autofill байроқларини сохта «қўлда» қиларди -
    // сақланган курс бузиларди). reformat идемпотент (бўшлиқ олиб қайта
    // гуруҳлаш - барқарор, DOMContentLoaded + afterSwap бирга зарарсиз).
    // Доира ФАҚАТ swap target (глобал эмас): бошқа жойда «=» терилаётган
    // майдон реформатланиб ифода бузилмаслиги учун. Прецедент: drawer.jte
    // htmx:afterSwap, e.target = swap target; htmx 2 innerHTML/beforeend'да
    // контент e.target ичида (attachment outerHTML'да пул майдони йўқ).
    document.body.addEventListener('htmx:afterSwap', function (e) {
        var scope = e.target;
        if (!scope || !scope.querySelectorAll) return;
        scope.querySelectorAll('input.money').forEach(function (el) {
            // «=» калькулятор ифодасини reformat четлаб ўтади (жонли input
            // тингловчи 219-226 айнан шундай): beforeend add-row swap'да
            // scope=#lineRows МАВЖУД сатрларни ҳам қамрайди - фойдаланувчи
            // ярим терган ёки бузуқ ифода («=100*», «=10/0» - focusout'да
            // applyCalc null қайтариб «=» ҳолича қолдиради) reformat билан
            // «100»га жимгина бузилмасин. Server рендер қиймати ҳеч қачон
            // «=» билан бошланмайди - реал буг мақсадига таъсирсиз.
            if (el.value && el.value.charAt(0) !== '=') reformat(el);
        });
    });
})();

// DEC-127: HTMX partial сўровида server хатоси (4xx/5xx). htmx 2
// default'да бундай жавобни swap қилмайди - тўлиқ error саҳифа фрагмент
// ичига сира тушмайди, лекин хато фойдаланувчига ЖИМ йўқолади. Server
// HX-Request'га ихчам alert (shared/errorAlert.jte) + X-Averpo-Error
// белгиси + HX-Reswap: afterbegin қайтаради; swap ФАҚАТ шу белгили
// жавобга очилади - бегона хато жавоблари эскича (swap'сиз) қолади.
// Тингловчи шу файлда, чунки money-input.js иккала layout ҳам улайдиган
// ягона умумий скрипт - layout файлларига янги <script> қўшиш 127
// доирасида тақиқланган.
(function () {
    document.body.addEventListener('htmx:beforeSwap', function (e) {
        var xhr = e.detail && e.detail.xhr;
        if (!xhr || xhr.getResponseHeader('X-Averpo-Error') !== '1') return;
        // Аввалги хато alert'и тозаланади - қайта уринишларда тўпланмасин
        if (e.detail.target && e.detail.target.querySelectorAll) {
            e.detail.target.querySelectorAll('[data-hx-error]').forEach(function (n) {
                n.remove();
            });
        }
        e.detail.shouldSwap = true;
    });
})();
