@import gg.jte.Content
@param Content c
@param String cls = ""
@param String wrap = ""
<%-- tableStyle - ФАҚАТ динамик қиймат учун (JTE null атрибутни
     ташлаб юборади): Tailwind arbitrary қиймат runtime ифодани
     кўрмайди, шунинг учун ҳисобланадиган min-width инлайн қолади
     (ягона жой: reports/profitLossByClass). Статик энга min-w-[...]. --%>
@param String tableStyle = null

<%-- Penguin table канони (Arbitr-120; реф: docs/penguin-refs/table.html,
     каталог: docs/ui-style-guide.md «1. Жадвал (Penguin)»). Ҳамма
     жадвал шу фрагмент орқали чиқади - узун utility йиғими БИР жойда
     (янги CSS класс/@apply тақиқ, шунинг учун фрагмент).
     - Ўрам эски .table-wrap ўрни: overflow-x-auto (мобилда жадвал
       скролл бўлади, CLAUDE.md) + Penguin border/rounded.
     - th/td катакларга класс ёзилмайди: зич ERP паддинги ва ранглар
       table тегидаги [&_th]/[&_td] arbitrary variant'ларда.
     - Матн аниқлиги (фойдаланувчи 2026-07-17): thead on-surface-strong,
       оддий катак on-surface; пул/сон/қиймат катагига экранда
       text-right whitespace-nowrap tabular-nums text-on-surface-strong
       (+ dark жуфти) ёзилади - канон каталогда.
     - zebra + hover + data-href (QBO одати) БИР нуқтада, ҳар tr'га
       эмас. zebra'даги :where - hover (спецификлиги баланд) zebra
       фонидан устун туриши учун; data-href hover эса оддий hover'дан
       кучлироқ (кўкроқ - босса бўлади дегани).
     - cls - қўшимча table класслари (одатда min-w-[NNNpx]);
       wrap - ўрамга қўшимча (масалан max-w-*).
     - tfoot: жами қатори канони (кучли матн + surface-alt фон).
     - [&_td>a] - катакдаги оддий ҳавола (ҳужжат рақами) иккала режимда
       ўқилсин: тўқда компенсация кўки (#1a56db) кўринмас эди. Фақат
       БЕВОСИТА бола - ⋮ панел бандлари (чуқурроқ) қамралмайди. --%>
<div class="overflow-hidden w-full overflow-x-auto rounded-radius border border-outline dark:border-outline-dark ${wrap}">
    <table class="w-full bg-surface text-left text-sm text-on-surface dark:bg-surface-dark dark:text-on-surface-dark [&_th]:px-3 [&_th]:py-2 [&_td]:px-3 [&_td]:py-2 [&_thead_th]:font-semibold [&_thead]:border-b [&_thead]:border-outline [&_thead]:bg-surface-alt [&_thead]:text-on-surface-strong dark:[&_thead]:border-outline-dark dark:[&_thead]:bg-surface-dark-alt dark:[&_thead]:text-on-surface-dark-strong [&_tbody]:divide-y [&_tbody]:divide-outline dark:[&_tbody]:divide-outline-dark [&_tbody_tr:where(:nth-child(even))]:bg-primary/5 dark:[&_tbody_tr:where(:nth-child(even))]:bg-primary-dark/10 [&_tbody_tr:hover]:bg-surface-alt dark:[&_tbody_tr:hover]:bg-surface-dark-alt [&_tr[data-href]]:cursor-pointer [&_tbody_tr[data-href]:hover]:bg-primary/10 dark:[&_tbody_tr[data-href]:hover]:bg-primary-dark/20 [&_tfoot]:border-t [&_tfoot]:border-outline [&_tfoot]:bg-surface-alt [&_tfoot]:font-semibold [&_tfoot]:text-on-surface-strong dark:[&_tfoot]:border-outline-dark dark:[&_tfoot]:bg-surface-dark-alt dark:[&_tfoot]:text-on-surface-dark-strong [&_td>a]:font-medium [&_td>a]:text-primary dark:[&_td>a]:text-primary-dark ${cls}" style="${tableStyle}">${c}</table>
</div>
