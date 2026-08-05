package com.averpo.erp.security.config;

import com.averpo.erp.security.domain.Permission;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * URL → соҳа харитаси (docs/modules/user-roles.md «Ҳимоя» 1-қатлам) -
 * ЯГОНА манба: SecurityConfig authorization қоидаларини ҳам,
 * GlobalModelAttributes'нинг соҳага-сезгир canEdit'ини ҳам ШУ рўйхат
 * беради. Иккови ажралса UI тугмаси билан server ҳақиқати мос келмай
 * қолар эди - шунинг учун харита алоҳида класс.
 *
 * <p>⚠ ТАРТИБ КРИТИК (биринчи мос келган ютади):
 * <ul>
 *   <li>/settings/warehouses|units|price-lists (INVENTORY) -
 *       /settings/** (SETTINGS)дан ОЛДИН, акс ҳолда омбор менежери ўз
 *       каталогига кира олмайди;</li>
 *   <li>соҳа ҳисоботлари (/reports/ar-aging каби) - /reports/**
 *       (FIN_REPORTS)дан ОЛДИН: sales manager ўз AR ҳисоботини SALES
 *       орқали кўради, P&L эса унга ёпиқ.</li>
 * </ul>
 *
 * @author Zafar
 */
public final class UrlPermissionMap {

    private UrlPermissionMap() {
    }

    /** Битта соҳага тегишли URL нақшлари гуруҳи (тартибли рўйхат банди). */
    public record Rule(Permission area, List<String> patterns) {
        /** Матчер рўйхатини SecurityConfig'га массив кўринишида беради. */
        public String[] patternArray() {
            return patterns.toArray(String[]::new);
        }
    }

    /**
     * Тартибли қоидалар. Бир соҳа бир неча бандда келиши мумкин
     * (INVENTORY: settings каталоглари алоҳида, олдинроқ туриши учун).
     * Бланк префикслар ("/settings") PathPattern'да "/settings/**"
     * билан ҳам мос тушади, лекин эски конфигурация услубига содиқ
     * қолиб иккаласи ҳам ёзилади - матчер кутубхонаси алмашса ҳам
     * хулқ ўзгармасин.
     */
    public static final List<Rule> RULES = List.of(
            // INVENTORY каталоглари /settings ичида яшайди - SETTINGS'дан ОЛДИН!
            new Rule(Permission.INVENTORY, List.of(
                    "/settings/warehouses", "/settings/warehouses/**",
                    "/settings/units", "/settings/units/**",
                    "/settings/price-lists", "/settings/price-lists/**")),
            new Rule(Permission.SETTINGS, List.of(
                    "/settings", "/settings/**")),
            new Rule(Permission.USERS, List.of(
                    "/users", "/users/**",
                    "/audit-log", "/audit-log/**")),
            // Соҳа ҳисоботлари - умумий /reports/** (FIN_REPORTS)дан ОЛДИН
            new Rule(Permission.SALES, List.of(
                    "/reports/ar-aging", "/reports/statement")),
            new Rule(Permission.PURCHASE, List.of(
                    "/reports/ap-aging")),
            new Rule(Permission.INVENTORY, List.of(
                    "/reports/inventory-valuation")),
            new Rule(Permission.PAYROLL, List.of(
                    "/reports/payroll-register")),
            new Rule(Permission.FIN_REPORTS, List.of(
                    "/reports", "/reports/**")),
            // /price-lists/lookup - сотув формаларининг нарх prefill'и
            // (ScreenSmoke прецеденти: invoice киритувчи ҳар роль етсин) -
            // каталог бошқаруви эса /settings/price-lists (INVENTORY)да
            new Rule(Permission.SALES, List.of(
                    "/invoices", "/invoices/**",
                    "/invoice-payments", "/invoice-payments/**",
                    "/estimates", "/estimates/**",
                    "/credit-memos", "/credit-memos/**",
                    "/refund-receipts", "/refund-receipts/**",
                    "/sales-receipts", "/sales-receipts/**",
                    "/customers", "/customers/**",
                    "/price-lists/lookup")),
            // /expenses спецда PURCHASE соҳасида (QBO Expenses) - BANKING эмас
            new Rule(Permission.PURCHASE, List.of(
                    "/bills", "/bills/**",
                    "/payments", "/payments/**",
                    "/purchase-orders", "/purchase-orders/**",
                    "/vendor-credits", "/vendor-credits/**",
                    "/expenses", "/expenses/**",
                    "/landed-costs", "/landed-costs/**",
                    "/vendors", "/vendors/**")),
            // /warehouses/quick* - combobox quick-add (066) /settings
            // namespace'идан ТАШҚАРИДА туради - алоҳида киритилмаса
            // харитадан тушиб қолади (соҳа қоидаси тузоғи)
            new Rule(Permission.INVENTORY, List.of(
                    "/items", "/items/**",
                    "/item-categories", "/item-categories/**",
                    "/inventory", "/inventory/**",
                    "/warehouses/quick-form", "/warehouses/quick")),
            new Rule(Permission.BANKING, List.of(
                    "/bank-transactions", "/bank-transactions/**",
                    "/transfers", "/transfers/**",
                    "/reconciliation", "/reconciliation/**")),
            new Rule(Permission.GL, List.of(
                    "/accounts", "/accounts/**",
                    "/journal-entries", "/journal-entries/**")),
            new Rule(Permission.PAYROLL, List.of(
                    "/payroll", "/payroll/**",
                    "/employees", "/employees/**")));

    /** areaOf учун олдиндан компиляция қилинган нақшлар (тартиб сақланади). */
    private static final List<Map.Entry<PathPattern, Permission>> COMPILED = compile();

    /** RULES'ни PathPattern'ларга бир марта компиляция қилади. */
    private static List<Map.Entry<PathPattern, Permission>> compile() {
        PathPatternParser parser = new PathPatternParser();
        List<Map.Entry<PathPattern, Permission>> compiled = new ArrayList<>();
        for (Rule rule : RULES) {
            for (String pattern : rule.patterns()) {
                compiled.add(Map.entry(parser.parse(pattern), rule.area()));
            }
        }
        return List.copyOf(compiled);
    }

    /**
     * Request path'нинг соҳаси - биринчи мос келган қоида ютади (худди
     * SecurityConfig'даги матчер тартиби каби). Харитага кирмаган йўллар
     * (дашборд, /search, /profile, /attachments) учун бўш Optional -
     * улар «соҳасиз» саҳифалар.
     */
    public static Optional<Permission> areaOf(String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (Map.Entry<PathPattern, Permission> entry : COMPILED) {
            if (entry.getKey().matches(container)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
