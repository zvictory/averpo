// Контакт валютаси ҳужжат валютасини белгилайди (QBO ҚАТЪИЙ, DEC-087,
// docs/modules/multi-currency.md «Контакт валютаси»): vendor/customer
// танланиши билан валюта select контакт валютасига ўтади ва қулфланади -
// ҳужжатда ўзгартирилмайди (бошқа валюта керак бўлса янги контакт очилади).
//
// Wiring (ҳужжат формаларида): контакт combobox'ининг value input'ига
//   data-currency-target="<валюта select id>" data-home-currency="UZS"
// қўйилади (shared/combobox.jte hiddenAttrs); контакт варианти li'сида
// data-currency бор - pgCombo танловда уни value input'га кўчиради
// (бўлмаса home деб олинади - quick-add янги контакти home билан яратилади).
//
// Тузоқлар ҳисобга олинган:
// - программ .value ёзуви ҳодиса чиқармайди (086 сабоғи) - change МАЖБУРАН
//   dispatch қилинади (bubbles) ва мавжуд занжир ўзи ишлайди: curtag,
//   курс prefill (DEC-029), курс майдони кўриниши (DEC-088).
// - disabled select submit ҚИЛМАЙДИ - бу атайлаб: server валютани барибир
//   контактдан ўзи олади (BR-SINV-011 оиласи гаровлари), бўш келгани мосдир.
// - Бошланғич apply alpine:initialized'дан КЕЙИН: value input'даги
//   data-currency'ни pgCombo init (Alpine) танланган li'дан кўчиради -
//   ундан олдин ўқилса home деб янглишарди (draft edit'да чет валюта
//   контакти бор ҳолат).
(function () {
    function wire(contactField) {
        if (contactField.__contactCurrency) return;
        var currencySelect = document.getElementById(
                contactField.getAttribute('data-currency-target'));
        if (!currencySelect) return;
        contactField.__contactCurrency = true;
        var home = contactField.getAttribute('data-home-currency') || '';
        function apply() {
            if (!contactField.value) {
                // Контакт танланмаган (янги форма) - валюта очиқ туради;
                // server контакт келгач барибир ўзиникини ёзади
                currencySelect.disabled = false;
                return;
            }
            var code = contactField.dataset.currency || home;
            if (currencySelect.value !== code) {
                currencySelect.value = code;
                currencySelect.dispatchEvent(new Event('change', { bubbles: true }));
            }
            currencySelect.disabled = true;
        }
        contactField.addEventListener('change', apply);
        // Саҳифа очилишида ҳам (draft edit, PO→Bill/Estimate→Invoice
        // конверти): валюта контактга тортилади; қиймат аллақачон мос
        // бўлса change отилмайди - сақланган курс prefill билан устидан
        // ёзилмайди.
        //
        // DEC-157: бошланғич apply'ни ишончли чақириш. Бу оддий скрипт
        // (defer'сиз) wireAll'ни DOMContentLoaded'да ишлатади, Alpine эса
        // (defer) parse тугагач DOMContentLoaded'дан ОЛДИН start бўлиб
        // 'alpine:initialized'ни отиб бўлади - ўшанда {once} листенер
        // ўтиб кетиб, олдиндан-танланган контакт оқимларида қулф тушмасди.
        // Alpine аллақачон ишга тушган бўлса apply'ни ДАРҲОЛ (server
        // data-currency ва select value рендерда тайёр - барча ҳужжат
        // формаси dataCurrency беради), акс ҳолда init бўлганда чақирамиз.
        // apply() идемпотент: икки нуқтадан ҳам ишласа disabled=true
        // қайта, мос қийматда change йўқ.
        if (window.Alpine) {
            apply();
        } else {
            document.addEventListener('alpine:initialized', apply, { once: true });
        }
    }
    function wireAll() {
        document.querySelectorAll('[data-currency-target]').forEach(wire);
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', wireAll);
    } else {
        wireAll();
    }
    // DEC-162 (превентив): HTMX partial билан юкланган формада ҳам
    // контакт-валюта қулфи уланиши учун - wireAll фақат DOMContentLoaded'да
    // ишлайди, HTMX swap'да отилмайди. Ҳозирча ҳеч бир swap
    // [data-currency-target] (контакт combobox) юкламайди: контакт/ходим
    // drawer'ида combobox йўқ, bill/invoice тўлиқ саҳифа - демак бу тингловчи
    // дормант (зарарсиз), лекин 157 verify NIT'и (мўрт DOMContentLoaded-init
    // нақши) money-input.js билан изчил ёпилади. wire() __contactCurrency
    // guard'и билан идемпотент; доира swap target (money-input.js нақши).
    document.body.addEventListener('htmx:afterSwap', function (e) {
        var scope = e.target;
        if (!scope || !scope.querySelectorAll) return;
        scope.querySelectorAll('[data-currency-target]').forEach(wire);
    });
})();
