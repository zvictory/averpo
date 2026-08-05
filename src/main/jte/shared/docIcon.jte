@param String type

<%-- DEC-089: ҳужжат/транзакция view сарлавҳаси (h1) бошидаги entity
     икони - фойдаланувчи қайси саҳифада турганини бир қарашда ажратиб
     олади. Глифлар main.jte сайдбар нақшига мос (viewBox 24, stroke
     currentColor, қалинлик 2); учтаси айнан реюз: journalEntry -
     book-open, bankTransaction - bank, payrollRun - users; қолганлари
     шу услубда чизилган. Икон декоратив: сарлавҳада матн бор, шунга
     aria-hidden ва янги i18n калитсиз (карта шарти). Ранг хира кулранг:
     статус ранглари билан рақобатлашмасин. vertical-align:middle h1
     стилига тегмасдан вертикал марказлайди; flex-shrink:0 - 375px да
     сарлавҳа сиқилганда икон кичраймайди. type - ҳужжат тури калити;
     номаълум калитда умумий file глифи чиқади, саҳифа синмайди. --%>
<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" class="mr-[.15em] inline-block shrink-0 align-middle text-current/60">
@if("invoice".equals(type))
    <%-- file-text: сотув оиласи, сатрли ҳужжат --%>
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
@elseif("bill".equals(type))
    <%-- file + пастга ўқ: таъминотчидан кирувчи ҳужжат --%>
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="11.5" x2="12" y2="17"/><polyline points="9.5 14.5 12 17 14.5 14.5"/>
@elseif("creditMemo".equals(type))
    <%-- file-minus: мижоз қарзини камайтиради --%>
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="9" y1="15" x2="15" y2="15"/>
@elseif("vendorCredit".equals(type))
    <%-- file + чапга ўқ: таъминотчига қайтариш --%>
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="15" y1="14.5" x2="9.5" y2="14.5"/><polyline points="12 12 9.5 14.5 12 17"/>
@elseif("refundReceipt".equals(type))
    <%-- чек + чапга ўқ: пул қайтарилди --%>
    <path d="M5 2h14v19.5l-2.33-1.6-2.34 1.6-2.33-1.6-2.33 1.6-2.34-1.6L5 21.5z"/><line x1="15" y1="10.5" x2="9.5" y2="10.5"/><polyline points="12 8 9.5 10.5 12 13"/>
@elseif("salesReceipt".equals(type))
    <%-- чек (пастки чети тишли) + сатрлар: сотув чеки --%>
    <path d="M5 2h14v19.5l-2.33-1.6-2.34 1.6-2.33-1.6-2.33 1.6-2.34-1.6L5 21.5z"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="9" y1="12" x2="15" y2="12"/>
@elseif("estimate".equals(type))
    <%-- clipboard: таклиф/смета ҳали ҳужжат эмас --%>
    <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
@elseif("purchaseOrder".equals(type))
    <%-- truck: буюртма қилинган юк йўлда --%>
    <rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/>
@elseif("journalEntry".equals(type))
    <%-- book-open (main.jte реюз): бухгалтерия дафтари --%>
    <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
@elseif("transfer".equals(type))
    <%-- repeat: икки счёт ораси айланма --%>
    <polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/>
@elseif("stockAdjustment".equals(type))
    <%-- DEC-093: sliders - инвентаризация (қолдиқни созлаш) --%>
    <line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/>
@elseif("stockTransfer".equals(type))
    <%-- DEC-093: омборлараро юк кўчиши (иккита қути + ўқ) --%>
    <rect x="2" y="7" width="7" height="7" rx="1"/><rect x="15" y="10" width="7" height="7" rx="1"/><polyline points="10 5 13 8 10 11"/><path d="M13 8H8"/>
@elseif("bankTransaction".equals(type))
    <%-- bank (main.jte реюз): банк биноси --%>
    <line x1="3" y1="21" x2="21" y2="21"/><line x1="3" y1="10" x2="21" y2="10"/><polyline points="5 10 12 3 19 10"/><line x1="5" y1="14" x2="5" y2="17"/><line x1="9.5" y1="14" x2="9.5" y2="17"/><line x1="14.5" y1="14" x2="14.5" y2="17"/><line x1="19" y1="14" x2="19" y2="17"/>
@elseif("expense".equals(type))
    <%-- credit-card: харажат тўлови --%>
    <rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/>
@elseif("landedCost".equals(type))
    <%-- anchor: етказиб келиш (landed) харажати --%>
    <circle cx="12" cy="5" r="3"/><line x1="12" y1="22" x2="12" y2="8"/><path d="M5 12H2a10 10 0 0 0 20 0h-3"/>
@elseif("payrollRun".equals(type))
    <%-- users (main.jte реюз): жамоа ойлик ҳисоби --%>
    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
@elseif("payrollPayment".equals(type))
    <%-- user-check: ходимга тўлов ўтди --%>
    <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><polyline points="17 11 19 13 23 9"/>
@elseif("invoicePayment".equals(type))
    <%-- пастга ўқ доирада: кирим (пул қабул қилинди) --%>
    <circle cx="12" cy="12" r="10"/><polyline points="8 12 12 16 16 12"/><line x1="12" y1="8" x2="12" y2="16"/>
@elseif("billPayment".equals(type))
    <%-- тепага ўқ доирада: чиқим (пул тўланди) --%>
    <circle cx="12" cy="12" r="10"/><polyline points="16 12 12 8 8 12"/><line x1="12" y1="16" x2="12" y2="8"/>
@elseif("reconciliation".equals(type))
    <%-- check-square: солиштирилди/тасдиқланди --%>
    <polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
@else
    <%-- номаълум тур: умумий file глифи - саҳифа синмайди --%>
    <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/>
@endif
</svg>
