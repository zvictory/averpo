@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.purchase.domain.Bill
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<Bill> openBills
@param Msg msg

<%-- HTMX partial: танланган vendor'нинг очиқ bill қаторлари. Тўлов
     формасида ҳам, аванс ишлатишда ҳам ишлатилади - name'лар
     BillPaymentForm.allocations[i] binding'ига мос. --%>
@if(openBills.isEmpty())
    <tr>
        <td colspan="6" class="text-current/60">${msg.localize("pay.form.noOpenBills")}</td>
    </tr>
@else
    @for(var i = 0; i < openBills.size(); i++)
        !{var bill = openBills.get(i);}
        <tr>
            <td>
                <input type="hidden" name="allocations[${i}].billId" value="${bill.getId().toString()}">
                <a href="/bills/${bill.getId().toString()}" target="_blank">${bill.getBillNumber()}</a>
            </td>
            <td>${bill.getBillDate().toString()}</td>
            <td>${bill.getDueDate() != null ? bill.getDueDate().toString() : ""}</td>
            <td class="text-right">${Fmt.money(bill.getTotal())} ${bill.getCurrency().getCode()}</td>
            <td class="text-right">${Fmt.money(bill.getBalanceDue())} ${bill.getCurrency().getCode()}</td>
            <td>
                <input name="allocations[${i}].amount" size="12" class="money text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
                       placeholder="0">
            </td>
        </tr>
    @endfor
@endif
