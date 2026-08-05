@import com.averpo.erp.bank.web.BankTransactionForm
@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.shared.service.TxnClassService.ClassOption
@import java.util.List
@param int index
@param List<Account> lineAccounts
@param List<Contact> contacts
@param Msg msg
@param BankTransactionForm.LineForm line = null
@param String classMode = "OFF"
@param List<ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

<%-- DEPOSIT/EXPENSE сатри: счёт + сумма + ихтиёрий контакт + изоҳ.
     Суммалар банк валютасида (сарлавҳадаги data-curtag кўрсатади). --%>
<tr class="line" data-index="${index}">
    <td class="text-center text-current/60">${index + 1}</td>
    <td>
        <%-- DEC-014: QBO tree - гуруҳ сарлавҳалари comboGroup бўлиб чиқади --%>
        @template.shared.combobox(name = "lines[" + index + "].accountId", msg = msg,
                value = line != null ? line.getAccountId() : null,
                placeholder = msg.lookup("bt.row.selectAccount"),
                dense = true,
                cls = "w-full min-w-[180px]",
                addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("bt.row.selectAccount"))
                    @template.shared.comboAccountOptions(accounts = lineAccounts, msg = msg,
                            selected = line != null ? line.getAccountId() : null)
                `)
    </td>
    <td><input name="lines[${index}].amount" size="12" class="money text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
               value="${line != null && line.getAmount() != null ? line.getAmount() : ""}"
              ></td>
    <td>
        @template.shared.combobox(name = "lines[" + index + "].contactId", msg = msg,
                value = line != null ? line.getContactId() : null,
                placeholder = msg.lookup("bt.row.noContact"),
                dense = true,
                cls = "min-w-[140px]",
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("bt.row.noContact"))
                    @for(Contact contact : contacts)
                        @template.shared.comboOption(value = contact.getId().toString(),
                                label = contact.getDisplayName(),
                                selected = line != null && contact.getId().toString().equals(line.getContactId()))
                    @endfor
                `)
    </td>
    @if("PER_LINE".equals(classMode))
        <%-- Сатр даражасидаги Йўналиш (class-tracking.md PER_LINE) --%>
        <td>
            @template.shared.classSelect(classes = classes, msg = msg,
                    name = "lines[" + index + "].classId",
                    selected = line != null ? line.getClassId() : null)
        </td>
    @endif
    <td><input name="lines[${index}].memo"
               value="${line != null && line.getMemo() != null ? line.getMemo() : ""}"
               class="w-full min-w-[110px] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
    <td class="text-center">
        <button type="button" onclick="this.closest('tr').remove()"
                class="cursor-pointer rounded-radius px-2 py-0.5 text-base leading-none text-on-surface/60 transition hover:bg-danger/10 hover:text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger dark:text-on-surface-dark/60"
                aria-label="${msg.lookup("common.deleteLine")}">✕</button>
    </td>
</tr>
