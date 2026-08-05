// Arbitr-144: сана майдонлари учун QBO услубидаги клавиатура
// ёрлиқлари. Фокус <input type="date">да турганда битта ҳарф сана
// қийматини силжитади: t=бугун, +/=(асос+1), -(асос-1), w=ҳафта боши
// (душанба, UZ/ISO), k=ҳафта охири (якшанба), m=ой боши, h=ой охири,
// y=йил боши, r=йил охири.
//
// Битта делегацияланган document-даражасидаги тингловчи - HTMX орқали
// кейин келган фрагментлардаги date input'ларга ҳам алоҳида улашсиз
// ишлайди (money-input.js delegation прецеденти). Фақат event.target
// сана майдони бўлгандагина аралашади - оддий матн майдонида «w»
// ёзувга халал бермайди (фойдаланувчи талаби).
//
// «Бугун» браузернинг local санаси: сана майдонлари фойдаланувчи ўз
// қуршовида тўлдирадиган киритиш майдони, native date input'нинг ўзи
// ҳам local'да ишлайди - CompanySettings timezone'ини сервердан
// узатиш бу ерда аниқлик қўшмай мураккаблик қўшарди (Arbitr-144
// 6-тузоқ бўйича ижрочи танлови, журналда изоҳланган).
(function () {
    'use strict';

    // Икки хонага тўлдириш - ISO (YYYY-MM-DD) сегментлари учун
    function pad(n) {
        return String(n).padStart(2, '0');
    }

    // Date -> ISO матн. toISOString АТАЙЛАБ эмас: у UTC'га ўгиради -
    // яримтундан кейинги соатларда local сана бир кунга сурилиб кетарди
    function iso(d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    }

    // ISO матн -> local Date; бузуқ/бўш қиймат - null (асос бугун бўлади).
    // new Date("YYYY-MM-DD") АТАЙЛАБ эмас: у санани UTC ярим тун деб
    // ўқийди - мусбат timezone'ларда бир кун орқага қайтарди.
    function parseIso(v) {
        var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v || '');
        return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null;
    }

    document.addEventListener('keydown', function (e) {
        var t = e.target;
        if (!t || !t.matches || !t.matches('input[type=date]')) return;
        // Браузер/тизим комбинациялари (Ctrl+C каби) ўз ҳолича қолади
        if (e.ctrlKey || e.altKey || e.metaKey) return;
        // readonly майдон қиймати ёрлиқ билан ҳам ўзгармайди
        if (t.readOnly || t.disabled) return;

        // Shift билан ҳам ишлайди (T, W...) - кичикка келтириб таққосланади
        var key = e.key.toLowerCase();
        // Асос: майдонда сана турган бўлса ундан (айниқса +/- учун),
        // бўш бўлса бугундан
        var base = parseIso(t.value) || new Date();
        var d;
        switch (key) {
            case 't':
                d = new Date();
                break;
            // «+» кўп клавиатурада Shift+«=» - иккиси ҳам қабул
            case '+':
            case '=':
                d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + 1);
                break;
            case '-':
                d = new Date(base.getFullYear(), base.getMonth(), base.getDate() - 1);
                break;
            case 'w': {
                // Ҳафта боши душанба (UZ/ISO): getDay() 0=якшанба
                var dow = base.getDay();
                d = new Date(base.getFullYear(), base.getMonth(),
                        base.getDate() + (dow === 0 ? -6 : 1 - dow));
                break;
            }
            case 'k': {
                // Ҳафта охири якшанба = душанба + 6
                var dk = base.getDay();
                d = new Date(base.getFullYear(), base.getMonth(),
                        base.getDate() + (dk === 0 ? 0 : 7 - dk));
                break;
            }
            case 'm':
                d = new Date(base.getFullYear(), base.getMonth(), 1);
                break;
            case 'h':
                // Кейинги ойнинг 0-куни = жорий ой охири (28/29/30/31 ўзи топади)
                d = new Date(base.getFullYear(), base.getMonth() + 1, 0);
                break;
            case 'y':
                d = new Date(base.getFullYear(), 0, 1);
                break;
            case 'r':
                d = new Date(base.getFullYear(), 11, 31);
                break;
            default:
                return;
        }
        // Ёрлиқ таниладигина native киритиш тўхтатилади - бошқа тугмалар
        // (масалан рақам териш, Tab, стрелкалар) ўз ҳолича қолади
        e.preventDefault();
        t.value = iso(d);
        // Программатик .value ёзуви event чиқармайди (Arbitr-086
        // прецеденти) - Alpine x-model, сана филтрлари ва HTMX
        // тингловчилари янгиланиши учун иккиси ҳам қўлда юборилади
        t.dispatchEvent(new Event('input', {bubbles: true}));
        t.dispatchEvent(new Event('change', {bubbles: true}));
    });
})();
