@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.web.PayrollPaymentForm
@import java.util.List
@param int index
@param List<Contact> employees
@param Msg msg
@param PayrollPaymentForm.LineForm line = null

<%-- Тўлов сатри: ходим + сумма (home валютада). Жами сатр жонли
     йиғилади (payrollPaymentForm скрипти, data-amount орқали). --%>
<tr class="line" data-index="${index}">
    <td class="text-center text-current/60">${index + 1}</td>
    <td>
        @template.shared.combobox(name = "lines[" + index + "].employeeId", msg = msg,
                value = line != null ? line.getEmployeeId() : null,
                placeholder = msg.lookup("prun.row.selectEmployee"),
                dense = true,
                cls = "w-full min-w-[200px]",
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("prun.row.selectEmployee"))
                    @for(Contact employee : employees)
                        @template.shared.comboOption(value = employee.getId().toString(),
                                label = employee.getDisplayName(),
                                selected = line != null && employee.getId().toString().equals(line.getEmployeeId()))
                    @endfor
                `)
    </td>
    <td><input name="lines[${index}].amount" size="13" class="money text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" data-amount
               value="${line != null && line.getAmount() != null ? line.getAmount() : ""}"
              ></td>
    <td class="text-center">
        <button type="button" onclick="this.closest('tr').remove(); document.dispatchEvent(new Event('payroll-pay-recalc'))"
                class="cursor-pointer rounded-radius px-2 py-0.5 text-base leading-none text-on-surface/60 transition hover:bg-danger/10 hover:text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger dark:text-on-surface-dark/60"
                aria-label="${msg.lookup("common.deleteLine")}">✕</button>
    </td>
</tr>
