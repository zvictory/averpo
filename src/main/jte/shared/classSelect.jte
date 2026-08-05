@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.service.TxnClassService.ClassOption
@import java.util.List
@param List<ClassOption> classes
@param String name
@param Msg msg
@param String selected = null
<%-- Кўриниш Penguin НАЗОРАТ канонида (DEC-121, ui-style-guide
     «2. Форма»); default - line-item зич варианти (py-1.5), ҳужжат
     бош майдонида чақирувчи тўлиқ канонни (mt-1 w-full ...py-2...)
     ўзи беради. --%>
@param String cls = "min-w-[140px] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"

<%-- Йўналиш (Class) танлагичи - «Ота:Бола» тўлиқ ном билан
     (class-tracking.md). Бўш танлов - тегсиз («Кўрсатилмаган»). --%>
<select name="${name}" class="${cls}">
    <option value="">${msg.localize("cls.none")}</option>
    @for(ClassOption option : classes)
        <option value="${option.id().toString()}"
                selected="${option.id().toString().equals(selected)}">
            ${option.fullName()}
        </option>
    @endfor
</select>
