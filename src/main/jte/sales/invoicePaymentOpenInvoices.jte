@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.sales.domain.Invoice
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<Invoice> openInvoices
@param Msg msg

<%-- HTMX partial: танланган мижознинг очиқ invoice қаторлари. Тушум
     формасида ҳам, аванс ишлатишда ҳам ишлатилади - name'лар
     InvoicePaymentForm.allocations[i] binding'ига мос. --%>
@if(openInvoices.isEmpty())
    <tr>
        <td colspan="6" class="text-current/60">${msg.localize("rcpt.form.noOpenInvoices")}</td>
    </tr>
@else
    @for(var i = 0; i < openInvoices.size(); i++)
        !{var invoice = openInvoices.get(i);}
        <tr>
            <td>
                <input type="hidden" name="allocations[${i}].invoiceId" value="${invoice.getId().toString()}">
                <a href="/invoices/${invoice.getId().toString()}" target="_blank">${invoice.getInvoiceNumber()}</a>
            </td>
            <td>${invoice.getInvoiceDate().toString()}</td>
            <td>${invoice.getDueDate() != null ? invoice.getDueDate().toString() : ""}</td>
            <td class="text-right">${Fmt.money(invoice.getTotal())} ${invoice.getCurrency().getCode()}</td>
            <td class="text-right">${Fmt.money(invoice.getBalanceDue())} ${invoice.getCurrency().getCode()}</td>
            <td>
                <input name="allocations[${i}].amount" size="12" class="money text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
                       placeholder="0">
            </td>
        </tr>
    @endfor
@endif
