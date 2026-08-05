@import com.averpo.erp.dashboard.web.DashboardController.ChartBar
@import com.averpo.erp.dashboard.web.DashboardController.ExpenseBar
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.service.LedgerDashboardService.BankBalance
@import com.averpo.erp.security.domain.Permission
@import com.averpo.erp.shared.web.Fmt
@import com.averpo.erp.web.Perms
@import java.math.BigDecimal
@import java.util.List
@param BigDecimal plIncome
@param BigDecimal plExpense
@param BigDecimal plNet
@param List<ChartBar> chartBars
@param BigDecimal cashIn
@param BigDecimal cashOut
@param BigDecimal cashNet
@param List<ChartBar> cashBars
@param List<BankBalance> banks
@param BigDecimal banksTotal
@param BigDecimal arOpen
@param BigDecimal arOverdue
@param BigDecimal arPaidLast30
@param BigDecimal apOpen
@param BigDecimal apOverdue
@param List<ExpenseBar> expenseBars
@param BigDecimal inventoryValue
@param Long warehouseCount
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.dashboard", title = msg.lookup("dash.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <%-- DEC-119: карталар Penguin card utility йиғимида (реф:
         docs/penguin-refs/card.html; каталог: ui-style-guide.md) -
         локал style блоки керак эмас. График SVG'лари ва inline
         ранглар (яшил/қизил кўрсаткичлар) эски ҳолида - ўз тўлқинида. --%>
    <h1 class="mb-4 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("dash.title")}</h1>

    <div class="grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
        <%-- P&L карта: жорий ой + охирги 6 ой SVG бар-график --%>
        <div class="rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
            <h2 class="mb-3 text-sm font-semibold">${msg.localize("dash.plCard")}</h2>
            <div class="mb-3 flex flex-wrap gap-6">
                <div>
                    <div class="text-sm text-current/60">${msg.localize("dash.income")}</div>
                    <div class="text-success text-[1.35rem] font-bold">${Fmt.money(plIncome)} ${homeCurrency}</div>
                </div>
                <div>
                    <div class="text-sm text-current/60">${msg.localize("dash.expense")}</div>
                    <div class="text-danger text-[1.35rem] font-bold">${Fmt.money(plExpense)} ${homeCurrency}</div>
                </div>
                <div>
                    <div class="text-sm text-current/60">${msg.localize("dash.net")}</div>
                    <div class="text-[1.35rem] font-bold">${Fmt.money(plNet)} ${homeCurrency}</div>
                </div>
            </div>
            <%-- График server-side чизилади - JS chart кутубхонаси йўқ (spec) --%>
            <svg viewBox="0 0 340 150" class="w-full max-w-[420px]" role="img" aria-label="${msg.lookup("dash.last6")}">
                <line x1="6" y1="120" x2="334" y2="120" class="stroke-outline dark:stroke-outline-dark" stroke-width="1"/>
                @for(int i = 0; i < chartBars.size(); i++)
                    <rect x="${10 + i * 55}" y="${120 - chartBars.get(i).incomeHeight()}"
                          width="20" height="${chartBars.get(i).incomeHeight()}" class="fill-success" rx="2"/>
                    <rect x="${32 + i * 55}" y="${120 - chartBars.get(i).expenseHeight()}"
                          width="20" height="${chartBars.get(i).expenseHeight()}" class="fill-danger" rx="2"/>
                    <text x="${31 + i * 55}" y="135" font-size="10" class="fill-current text-current/60" text-anchor="middle">${chartBars.get(i).label()}</text>
                @endfor
            </svg>
            <div class="mt-1 text-sm text-current/60">
                <span class="text-success">●</span> ${msg.localize("dash.income")}
                <span class="ml-3 text-danger">●</span> ${msg.localize("dash.expense")}
                · ${msg.localize("dash.last6")}
            </div>
            <a class="mt-2 inline-block text-primary underline dark:text-primary-dark" href="/reports/profit-loss">${msg.localize("dash.details")} →</a>
        </div>

        <%-- Cash flow картаси (DEC-036): BANK счётлар пул оқими --%>
        <div class="rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
            <h2 class="mb-3 text-sm font-semibold">${msg.localize("dash.cashFlow")}</h2>
            <div class="mb-3 flex flex-wrap gap-6">
                <div>
                    <div class="text-sm text-current/60">${msg.localize("rcn.col.inflow")}</div>
                    <div class="text-success text-[1.35rem] font-bold">${Fmt.money(cashIn)} ${homeCurrency}</div>
                </div>
                <div>
                    <div class="text-sm text-current/60">${msg.localize("rcn.col.outflow")}</div>
                    <div class="text-danger text-[1.35rem] font-bold">${Fmt.money(cashOut)} ${homeCurrency}</div>
                </div>
                <div>
                    <div class="text-sm text-current/60">${msg.localize("dash.cashNet")}</div>
                    <div class="text-[1.35rem] font-bold">${Fmt.money(cashNet)} ${homeCurrency}</div>
                </div>
            </div>
            <%-- P&L графиги билан бир хил server-side SVG нақши --%>
            <svg viewBox="0 0 340 150" class="w-full max-w-[420px]" role="img" aria-label="${msg.lookup("dash.last6")}">
                <line x1="6" y1="120" x2="334" y2="120" class="stroke-outline dark:stroke-outline-dark" stroke-width="1"/>
                @for(int i = 0; i < cashBars.size(); i++)
                    <rect x="${10 + i * 55}" y="${120 - cashBars.get(i).incomeHeight()}"
                          width="20" height="${cashBars.get(i).incomeHeight()}" class="fill-success" rx="2"/>
                    <rect x="${32 + i * 55}" y="${120 - cashBars.get(i).expenseHeight()}"
                          width="20" height="${cashBars.get(i).expenseHeight()}" class="fill-danger" rx="2"/>
                    <text x="${31 + i * 55}" y="135" font-size="10" class="fill-current text-current/60" text-anchor="middle">${cashBars.get(i).label()}</text>
                @endfor
            </svg>
            <div class="mt-1 text-sm text-current/60">
                <span class="text-success">●</span> ${msg.localize("rcn.col.inflow")}
                <span class="ml-3 text-danger">●</span> ${msg.localize("rcn.col.outflow")}
                · ${msg.localize("dash.last6")}
            </div>
            <a class="mt-2 inline-block text-primary underline dark:text-primary-dark" href="/bank-transactions">${msg.localize("dash.details")} →</a>
        </div>

        <%-- Банк қолдиқлари картаси --%>
        <div class="rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
            <h2 class="mb-3 text-sm font-semibold">${msg.localize("dash.banks")}</h2>
            @if(banks.isEmpty())
                <p class="text-sm text-current/60">${msg.localize("dash.noBanks")}</p>
            @else
                <div class="grid gap-2">
                    @for(BankBalance bank : banks)
                        <div class="flex items-baseline justify-between gap-3">
                            <%-- Drill-down (DEC-036): счёт регистрига --%>
                            <a href="/accounts/${bank.accountId().toString()}/transactions" class="text-current underline">${bank.name()}</a>
                            <span class="text-right">
                                <b>${Fmt.money(bank.amount())} ${bank.currencyCode() == null ? homeCurrency : bank.currencyCode()}</b>
                                @if(bank.currencyCode() != null)
                                    <span class="block text-sm text-current/60">${Fmt.money(bank.baseAmount())} ${homeCurrency}</span>
                                @endif
                            </span>
                        </div>
                    @endfor
                    <div class="flex justify-between border-t border-outline pt-2 font-bold dark:border-outline-dark">
                        <span>${msg.localize("dash.banksTotal")}</span>
                        <span>${Fmt.money(banksTotal)} ${homeCurrency}</span>
                    </div>
                </div>
            @endif
        </div>

        <%-- AR картаси --%>
        <div class="rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
            <h2 class="mb-3 text-sm font-semibold">${msg.localize("dash.ar")}</h2>
            <div class="text-sm text-current/60">${msg.localize("dash.open")}</div>
            <div class="text-[1.35rem] font-bold">${Fmt.money(arOpen)} ${homeCurrency}</div>
            <div class="mt-2 text-sm text-current/60">${msg.localize("dash.overdue")}</div>
            <div class="text-[1.35rem] font-bold ${arOverdue.signum() > 0 ? "text-danger" : "text-success"}">${Fmt.money(arOverdue)} ${homeCurrency}</div>
            <%-- QBO Invoices widget паритети (DEC-036) --%>
            <div class="mt-2 text-sm text-current/60">${msg.localize("dash.paidLast30")}</div>
            <div class="text-success text-[1.35rem] font-bold">${Fmt.money(arPaidLast30)} ${homeCurrency}</div>
            <a class="mt-3 inline-block text-primary underline dark:text-primary-dark" href="/reports/ar-aging">${msg.localize("dash.viewAging")} →</a>
        </div>

        <%-- AP картаси --%>
        <div class="rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
            <h2 class="mb-3 text-sm font-semibold">${msg.localize("dash.ap")}</h2>
            <div class="text-sm text-current/60">${msg.localize("dash.open")}</div>
            <div class="text-[1.35rem] font-bold">${Fmt.money(apOpen)} ${homeCurrency}</div>
            <div class="mt-2 text-sm text-current/60">${msg.localize("dash.overdue")}</div>
            <div class="text-[1.35rem] font-bold ${apOverdue.signum() > 0 ? "text-danger" : "text-success"}">${Fmt.money(apOverdue)} ${homeCurrency}</div>
            <a class="mt-3 inline-block text-primary underline dark:text-primary-dark" href="/reports/ap-aging">${msg.localize("dash.viewAging")} →</a>
        </div>

        <%-- Харажатлар тақсимоти картаси --%>
        <div class="rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
            <h2 class="mb-3 text-sm font-semibold">${msg.localize("dash.expensesCard")}</h2>
            @if(expenseBars.isEmpty())
                <p class="text-sm text-current/60">${msg.localize("dash.noExpenses")}</p>
            @else
                <div class="grid gap-2.5">
                    @for(ExpenseBar bar : expenseBars)
                        <div>
                            <div class="flex justify-between gap-3 text-[.9rem]">
                                <span>${bar.name()}</span>
                                <span>${Fmt.money(bar.amount())}</span>
                            </div>
                            <div class="mt-1 h-2 rounded bg-outline dark:bg-outline-dark">
                                <div class="h-2 rounded bg-primary dark:bg-primary-dark" style="width:${bar.percent()}%;"></div>
                            </div>
                        </div>
                    @endfor
                </div>
            @endif
            <a class="mt-3 inline-block text-primary underline dark:text-primary-dark" href="/reports/profit-loss">${msg.localize("dash.details")} →</a>
        </div>

        <%-- Inventory картаси (DEC-036, multi-warehouse кенгайтмамиз):
             қиймат мавжуд valuation ҳисобидан - янги ҳисоб ёзилмаган --%>
        <div class="rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
            <h2 class="mb-3 text-sm font-semibold">${msg.localize("dash.inventoryCard")}</h2>
            <div class="text-sm text-current/60">${msg.localize("dash.inventoryValue")}</div>
            <div class="text-[1.35rem] font-bold">${Fmt.money(inventoryValue)} ${homeCurrency}</div>
            <div class="mt-2 text-sm text-current/60">${msg.localize("warehouse.title")}</div>
            <div class="text-[1.35rem] font-bold">${warehouseCount}</div>
            <a class="mt-3 inline-block text-primary underline dark:text-primary-dark" href="/inventory/balances">${msg.localize("dash.details")} →</a>
        </div>

        <%-- Тез амаллар картаси (DEC-036) - фақат ёза оладиганларга.
             DEC-092: ҳар тугма ўз соҳаси EDIT'и бўйича (Perms статик
             ҳелпери, main.jte нақши) - роль 403 оладиган тугмани кўрмасин --%>
        !{var perms = Perms.current();}
        @if(perms.anyEdit())
            <div class="rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
                <h2 class="mb-3 text-sm font-semibold">${msg.localize("dash.quickActions")}</h2>
                <div class="grid gap-2 [grid-template-columns:repeat(auto-fit,minmax(130px,1fr))]">
                    @if(perms.edit(Permission.SALES))
                        <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/invoices/new">+ ${msg.localize("plusNew.invoice")}</a>
                    @endif
                    @if(perms.edit(Permission.PURCHASE))
                        <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/expenses/new">+ ${msg.localize("plusNew.expense")}</a>
                    @endif
                    @if(perms.edit(Permission.BANKING))
                        <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/bank-transactions/new">+ ${msg.localize("bt.type.DEPOSIT")}</a>
                        <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/transfers/new">+ ${msg.localize("plusNew.accountTransfer")}</a>
                    @endif
                    @if(perms.edit(Permission.PURCHASE))
                        <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/bills/new">+ ${msg.localize("plusNew.bill")}</a>
                    @endif
                    @if(perms.edit(Permission.GL))
                        <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/journal-entries/new">+ ${msg.localize("plusNew.entry")}</a>
                    @endif
                </div>
            </div>
        @endif
    </div>
`)
