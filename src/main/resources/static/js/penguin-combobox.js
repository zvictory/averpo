// Penguin Combobox - Alpine.data компоненти (Arbitr-123).
// Маркап: src/main/jte/shared/combobox.jte (реф: docs/penguin-refs/
// combobox.html «with search»); мантиқ Penguin сайтининг ўз minimal
// услубида БИР ЖОЙДА рўйхатдан ўтади - 70+ нусхада x-data дубли йўқ.
//
// Penguin канонидан фарқлар (реф файл изоҳи + журнал):
// - Вариантлар x-for массиви эмас - JTE server render қилган li'лар
//   (карта 1-қолип); филтр li.style.display'ни бошқаради, ТАШҚИ
//   скрипт филтрлари (мас. тўлов формаларининг валюта филтри) li.hidden
//   ишлатади - иккиси бир-бирини бузмайди.
// - Танланган li'нинг data-* атрибутлари value input'га КЎЧАДИ ва change
//   (bubbles) отилади - эски select'нинг selectedOptions[0].dataset
//   ўқувчилари энди input.dataset ўқийди, HTMX hx-trigger="change" ва
//   Alpine занжирлари ўзгаришсиз ишлайди.
// - Панел fixed + position() (066 сабоғи: .table-wrap overflow ва drawer
//   transform клипидан қочиш); пастга сиғмаса тепага очилади.
// - Ташқи программ ёзув API'си: input.value = x; input.dispatchEvent(
//   new Event('change', {bubbles:true})) - компонент кўринишни ўзи
//   синхронлайди (контакт-валюта/transfer чиқариб ташлаш занжирлари).
// - Quick-add: рўйхат пастида доимий «+ Янги ...» банди - мавжуд
//   quickForm оқими (GET <url>-form → form[data-combo-quick] → POST
//   Accept:json → {id,label[,currency]}), механика combobox.md (066)
//   билан айнан; модал қобиғи фрагмент ичида (Penguin modal).
document.addEventListener('alpine:init', function () {
    Alpine.data('pgCombo', function () {
        return {
            open: false,
            openedWithKeyboard: false,
            label: '',        // тугмада кўринадиган танланган ёрлиқ
            addOpen: false,   // quick-add модали очиқми
            addTitle: '',     // модал сарлавҳаси (data-combo-title'дан)
            panelStyle: '',   // fixed панел жойлашуви (position() ҳисоблайди)
            optKeys: [],      // li'ларда учраган data-* калитлар (тозалаш учун)
            ownChange: false, // choose() ўз change'ини syncFromValue'дан ажратади

            init() {
                var keys = {};
                this.optionEls().forEach(function (li) {
                    Object.keys(li.dataset).forEach(function (k) {
                        if (k !== 'value') keys[k] = 1;
                    });
                });
                this.optKeys = Object.keys(keys);
                // Бошланғич танлов: server aria-selected render қилган li
                var sel = this.$refs.list.querySelector('[role="option"][aria-selected="true"]');
                if (sel) {
                    this.label = this.labelOf(sel);
                    this.copyData(sel);
                }
                // Ташқи программ ёзув (value + change dispatch) кўринишга
                // қайта синхронланади - ўз change'имиз (ownChange) эмас
                var self = this;
                this.$refs.value.addEventListener('change', function () {
                    if (!self.ownChange) self.syncFromValue();
                });
            },

            // ---- ёрдамчилар ----

            optionEls() {
                return Array.prototype.slice.call(
                        this.$refs.list.querySelectorAll('[role="option"][data-value]'));
            },
            // Қидирув нормализацияси: NBSP → бўшлиқ, кетма-кет бўшлиқ битта,
            // катта-кичик фарқсиз (кирилл toLowerCase тўғри ишлайди)
            norm(s) {
                return (s || '').replace(/ /g, ' ').replace(/\s+/g, ' ').trim().toLowerCase();
            },
            labelOf(li) {
                return (li.textContent || '').replace(/\s+/g, ' ').trim();
            },
            // Танланган li data-* атрибутлари value input'га кўчади: аввал
            // эски вариант калитлари ўчади (маркер/конфиг атрибутларга,
            // мас. data-itemsel ёки data-currency-target, ТЕГИЛМАЙДИ -
            // улар ҳеч қачон li'да учрамайди)
            copyData(li) {
                var input = this.$refs.value;
                this.optKeys.forEach(function (k) { delete input.dataset[k]; });
                if (!li) return;
                Object.keys(li.dataset).forEach(function (k) {
                    if (k !== 'value') input.dataset[k] = li.dataset[k];
                });
            },
            markSelected(li) {
                var prev = this.$refs.list.querySelector('[aria-selected="true"]');
                if (prev && prev !== li) prev.removeAttribute('aria-selected');
                if (li) li.setAttribute('aria-selected', 'true');
            },

            // ---- очиш/ёпиш/жойлашув ----

            toggle() {
                if (this.open || this.openedWithKeyboard) { this.close(); return; }
                this.openPanel();
            },
            openKb() {
                if (!this.open && !this.openedWithKeyboard) {
                    this.prepare();
                    this.openedWithKeyboard = true;
                }
            },
            openPanel() {
                this.prepare();
                this.open = true;
            },
            // Ҳар очилишда тоза рўйхат (эски терма қолмайди) + жойлашув
            prepare() {
                if (this.$refs.search) { this.$refs.search.value = ''; }
                this.filter('');
                this.position();
                // Танланган банд кўриниб турсин
                var sel = this.$refs.list.querySelector('[aria-selected="true"]');
                var list = this.$refs.list;
                if (sel) this.$nextTick(function () {
                    if (sel.scrollIntoView) sel.scrollIntoView({ block: 'nearest' });
                    else list.scrollTop = 0;
                });
            },
            close() {
                this.open = false;
                this.openedWithKeyboard = false;
            },
            // Панел fixed: триггер тагида, сиғмаса тепасида; ён томондан
            // viewport ичида қолади (066 position() мероси). Fixed'нинг
            // containing block'и viewport ЭМАС бўлиши мумкин: transform/
            // translate/filter/perspective/contain'ли аждод (мас. drawer'нинг
            // Tailwind v4 translate-x'и - transform ЭМАС, айнан translate:
            // property, 156). Ундай ота бўлса панел ўшанга нисбатан жойлашади,
            // шунга viewport координатасидан отанинг чап/тепа/паст offset'ини
            // айирамиз. Ота топилмаса cbL/cbT=0, cbB=vh - эски хулқ айнан.
            // (DOM'ни teleport ҚИЛМАЙМИЗ - у ташқи li.hidden филтрларни -
            // валюта 070, item харажат 156 - combobox root'дан узиб қўярди.)
            position() {
                var r = this.$refs.btn.getBoundingClientRect();
                var vw = window.innerWidth, vh = window.innerHeight;
                var cbL = 0, cbT = 0, cbB = vh;
                for (var el = this.$root; el && el.nodeType === 1; el = el.parentElement) {
                    var cs = getComputedStyle(el);
                    if (cs.transform !== 'none'
                            || (cs.translate && cs.translate !== 'none')
                            || (cs.rotate && cs.rotate !== 'none')
                            || (cs.scale && cs.scale !== 'none')
                            || cs.perspective !== 'none'
                            || cs.filter !== 'none'
                            || (cs.willChange && /transform|perspective|filter/.test(cs.willChange))
                            || (cs.contain && /\b(paint|layout|strict|content)\b/.test(cs.contain))) {
                        var cbr = el.getBoundingClientRect();
                        cbL = cbr.left; cbT = cbr.top; cbB = cbr.bottom;
                        break;
                    }
                }
                var width = Math.min(Math.max(r.width, 220), vw - 16);
                var left = Math.max(8, Math.min(r.left, vw - width - 8));
                var below = vh - r.bottom - 12;
                var above = r.top - 12;
                var css = 'left:' + (left - cbL) + 'px;width:' + width + 'px;';
                if (below < 180 && above > below) {
                    css += 'bottom:' + (cbB - r.top + 4) + 'px;top:auto;max-height:' + Math.min(320, above) + 'px;';
                } else {
                    css += 'top:' + (r.bottom + 4 - cbT) + 'px;bottom:auto;max-height:' + Math.min(320, below) + 'px;';
                }
                this.panelStyle = css;
            },
            reposition() {
                if (this.open || this.openedWithKeyboard) this.position();
            },
            // Esc: очиқ дропдаун/модал аввал ёпилади - drawer'нинг document
            // даражасидаги Esc тингловчисига етиб бормайди (066 хулқи)
            escKey(e) {
                if (this.addOpen) {
                    e.stopPropagation();
                    this.closeAdd();
                    return;
                }
                if (this.open || this.openedWithKeyboard) {
                    e.stopPropagation();
                    this.close();
                    this.$refs.btn.focus();
                }
            },
            // Ҳарф/рақам/Backspace босилса фокус қидирувга қайтади
            // (Penguin handleKeydownOnOptions; event.key - кирилл ҳам қамралади)
            typeAhead(e) {
                if (this.addOpen || (!this.open && !this.openedWithKeyboard)) return;
                if (!this.$refs.search || e.target === this.$refs.search) return;
                if (e.ctrlKey || e.metaKey || e.altKey) return;
                if (e.key === 'Backspace' || (e.key && e.key.length === 1)) {
                    this.$refs.search.focus();
                }
            },
            // Required бўш қолса браузер validation 1px input'ни фокуслайди -
            // дропдаун очилиб фойдаланувчига рўйхат кўрсатилади (bubble устида)
            fromValidation() {
                if (!this.open) this.openPanel();
            },

            // ---- филтр/танлов ----

            // Клиент филтр: ФАҚАТ style.display бошқарилади; ташқи скрипт
            // яширган (li.hidden) бандлар қидирувда ҳам чиқмайди
            filter(query) {
                var q = this.norm(query);
                var visible = 0;
                var self = this;
                this.optionEls().forEach(function (li) {
                    var match = !q || self.norm(li.textContent).indexOf(q) >= 0;
                    li.style.display = match ? '' : 'none';
                    if (match && !li.hidden) visible++;
                });
                if (this.$refs.empty) {
                    this.$refs.empty.classList.toggle('hidden', visible > 0);
                }
            },
            choose(li) {
                if (!li || li.hidden) return;
                var input = this.$refs.value;
                input.value = li.dataset.value;
                this.copyData(li);
                this.markSelected(li);
                this.label = li.dataset.value ? this.labelOf(li) : '';
                this.close();
                // change (bubbles) - HTMX/Alpine/делегация занжирлари учун;
                // ownChange байроғи ўз syncFromValue'имизни четлаб ўтади
                this.ownChange = true;
                input.dispatchEvent(new Event('change', { bubbles: true }));
                this.ownChange = false;
                this.$refs.btn.focus();
            },
            // Қидирув инпутида Enter: биринчи кўринаётган банд танланади
            // (Enter'нинг форма submit'и олдини олинган - эски хулқ парити)
            chooseFirst() {
                var first = this.optionEls().find(function (li) {
                    return !li.hidden && li.style.display !== 'none';
                });
                if (first) this.choose(first);
            },
            // Ташқи ёзувдан кейин кўриниш синхрони (transfer чиқариб ташлаш,
            // тозалаш ва ҳ.к.): value → li топилади → ёрлиқ/белги/dataset
            syncFromValue() {
                var v = this.$refs.value.value;
                var li = null;
                if (v) {
                    li = this.optionEls().find(function (el) { return el.dataset.value === v; }) || null;
                }
                this.copyData(li);
                this.markSelected(li);
                this.label = li ? this.labelOf(li) : '';
            },

            // ---- Quick-add («+ Янги ...», механика combobox.md 066) ----

            openAdd(url) {
                this.close();
                this.addOpen = true;
                this.addTitle = '';
                var body = this.$refs.addBody;
                body.innerHTML = '';
                var self = this;
                function afterLoad() {
                    var form = body.querySelector('form[data-combo-quick]');
                    if (!form) return;
                    self.addTitle = form.getAttribute('data-combo-title') || '';
                    var first = form.querySelector('input:not([type="hidden"]), select');
                    if (first) first.focus();
                }
                // HTMX бор бўлса fragment у орқали (spec оқими), йўқса fetch
                if (window.htmx) {
                    window.htmx.ajax('GET', url + '-form', { target: body, swap: 'innerHTML' })
                            .then(afterLoad);
                } else {
                    fetch(url + '-form').then(function (r) { return r.text(); })
                            .then(function (html) { body.innerHTML = html; afterLoad(); });
                }
            },
            closeAdd() {
                this.addOpen = false;
                this.$refs.addBody.innerHTML = '';
                this.$refs.btn.focus();
            },
            // Fragment формаси AJAX билан юборилади: жавоб JSON {id,label}
            // ёки BR хато {message} - хато модал ичида кўрсатилади
            addSubmit(e) {
                var form = e.target;
                if (!form || !form.matches || !form.matches('form[data-combo-quick]')) return;
                e.preventDefault();
                var submitBtn = form.querySelector('button[type="submit"]');
                if (submitBtn) submitBtn.disabled = true;
                var self = this;
                fetch(form.getAttribute('action'), {
                    method: 'POST',
                    body: new FormData(form),
                    headers: { 'Accept': 'application/json' }
                }).then(function (r) {
                    return r.json().then(function (j) { return { ok: r.ok, j: j }; });
                }).then(function (res) {
                    if (submitBtn) submitBtn.disabled = false;
                    if (res.ok && res.j && res.j.id) self.applyCreated(res.j);
                    else self.showAddError(form, res.j && res.j.message);
                }).catch(function () {
                    if (submitBtn) submitBtn.disabled = false;
                    self.showAddError(form, null);
                });
            },
            // Модал ичидаги «Бекор» (data-combo-close) тугмаси
            addClick(e) {
                if (e.target && e.target.closest && e.target.closest('[data-combo-close]')) {
                    this.closeAdd();
                }
            },
            showAddError(form, message) {
                var errorEl = form.querySelector('[data-combo-error]');
                if (!errorEl) return;
                errorEl.textContent = message || (this.$root.dataset.errorText || 'Error');
                errorEl.hidden = false;
            },
            // Муваффақият: янги банд рўйхат охирига (мавжуд li клонидан -
            // класс йиғими БИР жойда қолади) қўшилиб ТАНЛАНАДИ
            applyCreated(created) {
                var list = this.$refs.list;
                var tpl = this.optionEls().find(function (li) { return li.dataset.value; });
                if (tpl) {
                    var li = tpl.cloneNode(true);
                    li.removeAttribute('aria-selected');
                    li.style.display = '';
                    Object.keys(li.dataset).forEach(function (k) { delete li.dataset[k]; });
                    li.dataset.value = created.id;
                    // Жавобдаги currency data'га кўчади (Arbitr-087 занжири
                    // янги контактда ҳам ишлайди)
                    if (created.currency) li.dataset.currency = created.currency;
                    var labelEl = li.querySelector('[data-label]') || li;
                    labelEl.textContent = created.label;
                    list.appendChild(li);
                    this.closeAdd();
                    this.choose(li);
                } else {
                    // Каталог бўш (клонга li йўқ) - танлов барибир ёзилади
                    var input = this.$refs.value;
                    input.value = created.id;
                    this.copyData(null);
                    if (created.currency) input.dataset.currency = created.currency;
                    this.label = created.label;
                    this.closeAdd();
                    this.ownChange = true;
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    this.ownChange = false;
                }
            }
        };
    });
});
