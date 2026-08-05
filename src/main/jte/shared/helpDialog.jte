@import com.averpo.erp.i18n.Msg
@param Msg msg = null
<%-- Arbitr-079: контекстли ёрдам - битта глобал диалог + (?) delegation
     тингловчиси БИТТА умумий partial'да. ИККАЛА layout охирида уланади
     (main.jte ВА form.jte) - ҳужжат формаларидаги майдон (?) лари ҳам
     ишлаши шарт (spec «Дизайн қарорлари», 2026-07-11 фойдаланувчи
     топилмаси), дубляж эса тақиқ. msg null бўлиши мумкин (form.jte
     қолипи) - fallback ўша файлдаги услубда.
     Тугмалар (.helpbtn - JS delegation МАРКЕРИ, кўриниши тугма ўз
     жойида утилитада: shared/help.jte ва layout page-тугмаси) матнни
     data-help-title/body атрибутда олиб юради; delegation (T0 row-click
     қолипи - HTMX partial ичидаги тугмаларда ҳам init'сиз ишлайди)
     help-open event юборади, Alpine window листенери диалогни очади.
     Матн x-text орқали чиқади - HTML сифатида талқин қилинмайди (XSS
     йўқ), абзацлар whitespace-pre-line билан. Кўриниш Penguin
     утилиталарда (Arbitr-122): мобилда bottom-sheet max-md: жуфтларда.
     Ёпиш: Esc / ✕ / ташқи click. --%>
<div x-data="{helpOpen:false, helpTitle:'', helpBody:''}"
     x-on:help-open.window="helpTitle=$event.detail.title; helpBody=$event.detail.body; helpOpen=true"
     x-on:keydown.escape.window="helpOpen=false">
    <div x-show="helpOpen" x-cloak>
        <div class="fixed inset-0 z-[140] bg-surface-dark/45" x-on:click="helpOpen=false"></div>
        <div class="fixed left-1/2 top-1/2 z-[141] max-h-[80vh] w-[min(480px,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-radius bg-surface px-5 pb-5 pt-4 text-on-surface shadow-xl max-md:bottom-0 max-md:left-0 max-md:right-0 max-md:top-auto max-md:max-h-[75vh] max-md:w-full max-md:translate-x-0 max-md:translate-y-0 max-md:rounded-b-none max-md:rounded-t-2xl dark:bg-surface-dark dark:text-on-surface-dark"
             role="dialog" aria-modal="true" aria-labelledby="helpDlgTitle">
            <div class="mb-2.5 flex items-start justify-between gap-3">
                <b id="helpDlgTitle" class="text-[1.05rem] text-on-surface-strong dark:text-on-surface-dark-strong" x-text="helpTitle"></b>
                <button type="button" class="cursor-pointer rounded-radius border-0 bg-transparent px-2 py-1 text-[1.3rem] leading-none text-current/60 transition hover:bg-surface-alt hover:text-on-surface-strong dark:hover:bg-surface-dark-alt dark:hover:text-on-surface-dark-strong" x-on:click="helpOpen=false"
                        aria-label="${msg != null ? msg.lookup("common.close") : "Ёпиш"}">✕</button>
            </div>
            <div class="whitespace-pre-line text-[.95rem]" x-text="helpBody"></div>
        </div>
    </div>
</div>
<script>
    // (?) тугмалари учун document-level delegation - тугманинг
    // data-help-* атрибутлари ўқилиб глобал диалогга event юборилади.
    document.addEventListener('click', function (e) {
        if (!e.target || !e.target.closest) return;
        var btn = e.target.closest('.helpbtn');
        if (!btn) return;
        window.dispatchEvent(new CustomEvent('help-open', {detail: {
            title: btn.getAttribute('data-help-title') || '',
            body: btn.getAttribute('data-help-body') || ''
        }}));
    });
</script>
