@import com.averpo.erp.i18n.Msg
@param String sourceType
@param String id
@param Msg msg

<%-- DEC-098: ҳужжат view'идан GL ёзувига (JE) ҳавола блоки
     (DEC-080 қолипи, 14 view'да айнан бир хил эди). Чақирувчи POSTED/
     REVERSED шартини ўзи текширади - DRAFT'да проводка йўқ, блок чиқмайди.
     sourceType - JournalEntry.sourceType қиймати (INVOICE, BILL...),
     id - ҳужжат UUID матни (чақирувчи .toString() беради - фрагмент
     domain турига боғланмайди; фрагмент қоидаси:
     docs/modules/ui-framework.md «Компонентлар қоидаси»). --%>
<p class="mb-4"><a href="/journal-entries/by-source/${sourceType}/${id}" class="text-primary underline dark:text-primary-dark">${msg.localize("entries.view.openJe")} →</a></p>
