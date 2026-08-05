package com.averpo.erp.ledger.web;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Счёт танлаш select'лари учун QBO услубидаги иерархик option рўйхатини
 * тайёрлайди (Arbitr-014): тур бўйича optgroup, ота-бола бўғинлари
 * чуқурлик бўйича NBSP жилд билан, option матни «код ном (валюта)».
 * Шаблон томони: shared/accountOptions.jte.
 *
 * <p>Соф кўрсатиш helper'и - бизнес мантиқ эмас (Fmt паттерни), ҳисоб
 * ёки валидацияга ишлатиш ТАҚИҚ. Танлаб бўлмайдиган (disabled) ота
 * счётлар фақат UX қулайлиги - ҳақиқий гаров server BR'ларида
 * (масалан BR-LED-005: группа счётига проводка тақиқ).
 *
 * <p>{@code Account.parent} LAZY бўлгани учун иерархия фақат id'лар
 * орқали қурилади - proxy'дан {@code getId()} олиш уни инициализация
 * қилмайди, шаблон транзакциядан ташқарида ҳам ишлайверади
 * (open-in-view=false). Чуқурлик рўйхатда ҲОЗИР БОР аждодлар сони:
 * филтрланган рўйхатда (масалан фақат EXPENSE) ота киритилмаган бўлса
 * бола илдиз даражасида кўринади - бу атайлаб шундай.
 */
public final class AccountOptionGroups {

    /** Бўғин жилди - 3 та NBSP (native select option'ида CSS padding ишламайди). */
    private static final String INDENT = String.valueOf((char) 0x00A0).repeat(3);

    /** Utility класс - instance яратилмайди. */
    private AccountOptionGroups() { }

    /**
     * Битта танлов қатори.
     *
     * @param id          счёт id'си - String, form қийматлари (String) билан
     *                    тўғридан-тўғри солиштириш учун
     * @param label       жилд + код + ном + (валюта коди, счётда валюта бўлса)
     * @param disabled    танлаб бўлмайди (postable=false ота счёт)
     * @param currency    счёт валютаси коди ёки {@code null} - home
     *                    (шаблонда data-cur; Arbitr-070 валюта филтри
     *                    JS'и шундан ўқийди)
     * @param undeposited {@code true} - UNDEPOSITED_FUNDS клиринг чўнтаги:
     *                    валюта филтри уни яширмайди (чет валюта тўлов
     *                    QBO'дагидек undeposited орқали қабул қилинади)
     * @param type        {@code AccountType.name()} - шаблонда data-acctype;
     *                    item формаси харажат филтри (COGS/EXPENSE) шундан ўқийди
     */
    public record Option(String id, String label, boolean disabled,
                         String currency, boolean undeposited, String type) { }

    /**
     * Битта optgroup - счёт тури.
     *
     * @param titleKey тур номининг i18n калити ({@code AccountType.titleKey()})
     * @param options  тур ичидаги счётлар DFS (ота → болалар) тартибида
     */
    public record Group(String titleKey, List<Option> options) { }

    /**
     * Рўйхатни тур бўйича optgroup'ларга ажратади. Нофаол счётлар ташлаб
     * юборилади (уларга проводка тақиқ - BR-LED-004, select'да кўриниши
     * фақат чалғитарди). Тартиб: тур ordinal → ота-бола DFS → код, ном.
     *
     * @param accounts хом рўйхат - тўлиқ ({@code AccountService.all()})
     *                 ёки филтрланган кесим, иккиси ҳам бўлаверади
     * @param disableNonPostable {@code true} - postable=false счётлар
     *        танланмайди (проводка/ҳужжат select'лари); {@code false} -
     *        ҳаммаси танланади (масалан ота счёт танлаш - у ерда айнан
     *        группа счётлар керак)
     */
    public static List<Group> build(List<Account> accounts, boolean disableNonPostable) {
        List<Account> active = accounts.stream().filter(Account::isActive).toList();
        Map<UUID, Account> byId = new HashMap<>();
        for (Account account : active) {
            byId.put(account.getId(), account);
        }
        Comparator<Account> siblingOrder = Comparator
                .comparing((Account a) -> a.getCode() == null || a.getCode().isBlank())
                .thenComparing(a -> a.getCode() == null ? "" : a.getCode())
                .thenComparing(Account::getName);
        Map<UUID, List<Account>> children = new HashMap<>();
        List<Account> roots = new ArrayList<>();
        for (Account account : active) {
            UUID parentId = parentId(account);
            if (parentId != null && byId.containsKey(parentId)) {
                children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(account);
            } else {
                roots.add(account);
            }
        }
        children.values().forEach(list -> list.sort(siblingOrder));
        roots.sort(Comparator.comparing((Account a) -> a.getType().ordinal())
                .thenComparing(siblingOrder));
        // EnumMap ordinal тартибни сақлайди - optgroup'лар CoA тартибида чиқади
        Map<AccountType, List<Option>> grouped = new EnumMap<>(AccountType.class);
        for (Account root : roots) {
            appendSubtree(root, 0,
                    grouped.computeIfAbsent(root.getType(), k -> new ArrayList<>()),
                    children, disableNonPostable);
        }
        List<Group> groups = new ArrayList<>(grouped.size());
        grouped.forEach((type, options) -> groups.add(new Group(type.titleKey(), options)));
        return groups;
    }

    /**
     * Ота ва барча авлодлари битта optgroup ичида DFS тартибида қўшилади -
     * sub-account доим ўз отаси остида туради (QBO кўриниши).
     */
    private static void appendSubtree(Account account, int depth, List<Option> target,
                                      Map<UUID, List<Account>> children,
                                      boolean disableNonPostable) {
        target.add(option(account, depth, disableNonPostable));
        for (Account child : children.getOrDefault(account.getId(), List.of())) {
            appendSubtree(child, depth + 1, target, children, disableNonPostable);
        }
    }

    /** Option матни: жилд + код + ном + (валюта) - QBO кўриниши. */
    private static Option option(Account account, int depth, boolean disableNonPostable) {
        StringBuilder label = new StringBuilder(INDENT.repeat(depth));
        if (account.getCode() != null && !account.getCode().isBlank()) {
            label.append(account.getCode()).append(' ');
        }
        label.append(account.getName());
        if (account.getCurrency() != null) {
            label.append(" (").append(account.getCurrency().getCode()).append(')');
        }
        return new Option(account.getId().toString(), label.toString(),
                disableNonPostable && !account.isPostable(),
                account.getCurrency() != null ? account.getCurrency().getCode() : null,
                account.getDetailType() == AccountDetailType.UNDEPOSITED_FUNDS,
                account.getType().name());
    }

    /** LAZY parent proxy'дан фақат id олинади - инициализация бўлмайди. */
    private static UUID parentId(Account account) {
        return account.getParent() == null ? null : account.getParent().getId();
    }
}
