@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.item.domain.UnitGroup
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<UnitGroup> groups
@param Map<UUID, List<Unit>> unitsByGroup
@param List<Unit> freeUnits
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.unitGroups", title = msg.lookup("unit.groups"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("unit.groups")}</h1>
        <div class="flex items-center gap-3">
            <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
        </div>
    </div>

    <%-- Таблар: бирликлар | гуруҳлар (жорий) --%>
    <div class="mb-4 flex gap-1 border-b-2 border-outline dark:border-outline-dark">
        <a class="px-4 py-2 text-current/60 no-underline" href="/settings/units">${msg.localize("unit.tab.units")}</a>
        <a class="-mb-0.5 border-b-2 border-primary px-4 py-2 font-semibold text-primary no-underline dark:border-primary-dark dark:text-primary-dark" href="/settings/units/groups">${msg.localize("unit.tab.groups")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:640px;display:grid;gap:1rem;">
        <p class="text-[.9rem] text-current/60">${msg.localize("unit.factorHint")} @template.shared.help(key = "field.unitFactor", msg = msg)</p>

        <%-- Янги гуруҳ --%>
        <form method="post" action="/settings/units/groups"
              class="flex gap-2 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <input name="name" required placeholder="${msg.lookup("unit.group.name")}"
                   class="flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("unit.group.add")}</button>
        </form>

        @if(groups.isEmpty())
            <p class="text-current/60">${msg.localize("unit.group.noGroups")}</p>
        @endif

        <%-- Ҳар гуруҳ битта карта: сарлавҳа (✎ билан номлаш), бирликлар,
             битта қўшиш формаси. Base автоматик - биринчи бирлик. --%>
        @for(UnitGroup group : groups)
            !{List<Unit> groupUnits = unitsByGroup.get(group.getId());}
            !{Unit baseUnit = groupUnits.isEmpty() ? null : (groupUnits.get(0).isBase() ? groupUnits.get(0) : null);}
            <div x-data="{edit:false}"
                 class="grid gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark px-5 py-4">
                <div style="display:flex;align-items:center;gap:.5rem;">
                    <h3 x-show="!edit" style="font-size:1.05rem;">${group.getName()}</h3>
                    <button type="button" x-show="!edit" x-on:click="edit = true"
                            title="${msg.lookup("unit.group.rename")}"
                            class="cursor-pointer border-0 bg-transparent p-0 text-current/50">✎</button>
                    <form x-show="edit" x-cloak method="post"
                          action="/settings/units/groups/${group.getId().toString()}"
                          style="display:flex;gap:.5rem;flex:1;">
                        @template.shared.csrf(csrf = csrf)
                        <input name="name" value="${group.getName()}" required
                               class="flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
                    </form>
                </div>

                @if(groupUnits.isEmpty())
                    <p class="text-[.9rem] text-current/60">${msg.localize("unit.group.empty")}</p>
                @else
                    @if(baseUnit != null)
                        <div style="display:flex;gap:.5rem;align-items:center;">
                            <span class="text-[.9rem] text-current/60">${msg.localize("unit.group.baseIs")}:</span>
                            <b>${baseUnit.getName()}</b>
                            <%-- Base фақат ёлғиз қолганда чиқарилади (акс ҳолда гуруҳ «етим») --%>
                            @if(groupUnits.size() == 1)
                                <form method="post" action="/settings/units/group-assign" style="margin-left:auto;">
                                    @template.shared.csrf(csrf = csrf)
                                    <input type="hidden" name="unitId" value="${baseUnit.getId().toString()}">
                                    <button type="submit"
                                            class="cursor-pointer border-0 bg-transparent p-0 text-[.85rem] text-current/50 underline">
                                        ${msg.localize("unit.group.removeUnit")}
                                    </button>
                                </form>
                            @endif
                        </div>
                    @endif
                    @for(Unit unit : groupUnits)
                        @if(!unit.isBase())
                            <%-- Ҳар бирлик битта ўқиладиган қатор: 1 гр = [0,001] кг --%>
                            <div class="flex flex-wrap items-center gap-2 border-t border-outline pt-2.5 dark:border-outline-dark">
                                <form method="post" action="/settings/units/group-assign"
                                      style="display:flex;gap:.5rem;align-items:center;flex-wrap:wrap;">
                                    @template.shared.csrf(csrf = csrf)
                                    <input type="hidden" name="unitId" value="${unit.getId().toString()}">
                                    <input type="hidden" name="groupId" value="${group.getId().toString()}">
                                    <span>1 <b>${unit.getName()}</b>@if(!unit.isActive()) <span class="text-[.8rem] text-current/50">(${msg.localize("accounts.inactive")})</span>@endif =</span>
                                    <input name="factor" inputmode="decimal" required
                                           value="${unit.getFactor().stripTrailingZeros().toPlainString()}"
                                           class="w-[100px] text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                    <span>${baseUnit != null ? baseUnit.getName() : ""}</span>
                                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
                                </form>
                                <form method="post" action="/settings/units/group-assign" style="margin-left:auto;">
                                    @template.shared.csrf(csrf = csrf)
                                    <input type="hidden" name="unitId" value="${unit.getId().toString()}">
                                    <button type="submit"
                                            class="cursor-pointer border-0 bg-transparent p-0 text-[.85rem] text-current/50 underline">
                                        ${msg.localize("unit.group.removeUnit")}
                                    </button>
                                </form>
                            </div>
                        @endif
                    @endfor
                @endif

                <%-- Битта қўшиш формаси: ном (мавжуд гуруҳсиз бирлик бўлса
                     киради, бўлмаса яратилади) + factor (биринчисида керакмас) --%>
                <form method="post" action="/settings/units/groups/${group.getId().toString()}/add-unit"
                      class="flex flex-wrap items-center gap-2 border-t border-outline pt-2.5 dark:border-outline-dark">
                    @template.shared.csrf(csrf = csrf)
                    <span class="text-[.9rem] text-current/60">${msg.localize("unit.group.addUnit")}:</span>
                    <input name="name" required list="freeUnits${group.getId().toString()}"
                           placeholder="${msg.lookup("unit.name")}"
                           class="min-w-[110px] flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <datalist id="freeUnits${group.getId().toString()}">
                        @for(Unit free : freeUnits)
                            <option value="${free.getName()}"></option>
                        @endfor
                    </datalist>
                    @if(baseUnit != null)
                        <span>=</span>
                        <input name="factor" inputmode="decimal" required
                               placeholder="${msg.lookup("unit.factor")}"
                               class="w-[100px] text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                        <span>${baseUnit.getName()}</span>
                    @endif
                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("unit.add")}</button>
                </form>
            </div>
        @endfor
    </div>
`)
