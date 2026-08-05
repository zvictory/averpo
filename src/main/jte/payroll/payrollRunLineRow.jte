@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.web.PayrollRunForm
@import com.averpo.erp.shared.service.TxnClassService.ClassOption
@import java.util.List
@param int index
@param List<Contact> employees
@param Msg msg
@param PayrollRunForm.LineForm line = null
@param String classMode = "OFF"
@param List<ClassOption> classes = java.util.List.of()

<%-- Ҳисоблаш сатри: ходим + gross киритилади; ушланма/солиқ/net
     устунлари ЖОНЛИ кўрсатилади (payrollRunForm скрипти, data-*
     белгилар орқали) - ҳақиқий snapshot service'да ҳисобланади. --%>
<tr class="line" data-index="${index}">
    <td class="text-center text-current/60">${index + 1}</td>
    <td>
        @template.shared.combobox(name = "lines[" + index + "].employeeId", msg = msg,
                value = line != null ? line.getEmployeeId() : null,
                placeholder = msg.lookup("prun.row.selectEmployee"),
                dense = true,
                cls = "w-full min-w-[180px]",
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("prun.row.selectEmployee"))
                    @for(Contact employee : employees)
                        @template.shared.comboOption(value = employee.getId().toString(),
                                label = employee.getDisplayName(),
                                selected = line != null && employee.getId().toString().equals(line.getEmployeeId()))
                    @endfor
                `)
    </td>
    <td><input name="lines[${index}].gross" size="11" class="money text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" data-gross
               value="${line != null && line.getGross() != null ? line.getGross() : ""}"
              ></td>
    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong" data-income-tax></td>
    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong" data-pension></td>
    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong" data-social-tax></td>
    <td class="text-right font-semibold whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong" data-net></td>
    @if("PER_LINE".equals(classMode))
        <td>
            @template.shared.classSelect(classes = classes, msg = msg,
                    name = "lines[" + index + "].classId",
                    selected = line != null ? line.getClassId() : null)
        </td>
    @endif
    <td><input name="lines[${index}].memo"
               value="${line != null && line.getMemo() != null ? line.getMemo() : ""}"
               class="w-full min-w-[100px] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
    <td class="text-center">
        <button type="button" onclick="this.closest('tr').remove(); document.dispatchEvent(new Event('payroll-recalc'))"
                class="cursor-pointer rounded-radius px-2 py-0.5 text-base leading-none text-on-surface/60 transition hover:bg-danger/10 hover:text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger dark:text-on-surface-dark/60"
                aria-label="${msg.lookup("common.deleteLine")}">✕</button>
    </td>
</tr>
