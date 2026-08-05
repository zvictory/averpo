@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.web.PayrollRunForm
@import com.averpo.erp.shared.service.TxnClassService.ClassOption
@import java.util.List
@param List<PayrollRunForm.LineForm> prefillLines
@param List<Contact> employees
@param Msg msg
@param String classMode = "OFF"
@param List<ClassOption> classes = java.util.List.of()

<%-- «Ходимларни тўлдириш» natijasi: фаол EMPLOYEE'лар oklad билан -
     tbody тўлиқ алмашади (hx-swap="innerHTML"). --%>
@for(var i = 0; i < prefillLines.size(); i++)
    @template.payroll.payrollRunLineRow(index = i, employees = employees,
            msg = msg, line = prefillLines.get(i),
            classMode = classMode, classes = classes)
@endfor
@if(prefillLines.isEmpty())
    <tr><td colspan="10" class="text-current/60">${msg.localize("prun.form.noEmployees")}</td></tr>
@endif
