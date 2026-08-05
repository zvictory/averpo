@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.web.PayrollPaymentForm
@import java.util.List
@param List<PayrollPaymentForm.LineForm> prefillLines
@param List<Contact> employees
@param Msg msg

<%-- «Очиқ қолдиқни тўлдириш» natijasi: фаол ходимларнинг очиқ clearing
     қолдиғи (owed > 0) сумма билан - tbody тўлиқ алмашади (innerHTML). --%>
@for(var i = 0; i < prefillLines.size(); i++)
    @template.payroll.payrollPaymentLineRow(index = i, employees = employees,
            msg = msg, line = prefillLines.get(i))
@endfor
@if(prefillLines.isEmpty())
    <tr><td colspan="4" class="text-current/60">${msg.localize("payp.form.noUnpaid")}</td></tr>
@endif
