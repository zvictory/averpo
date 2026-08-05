package com.averpo.erp.dashboard.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.inventory.service.InventoryValuationService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.ledger.service.LedgerDashboardService.ExpenseSlice;
import com.averpo.erp.ledger.service.LedgerDashboardService.MonthCashFlow;
import com.averpo.erp.ledger.service.LedgerDashboardService.MonthPl;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Бош саҳифа - QBO home услубидаги dashboard. Ҳисоб-китоблар
 * модулларнинг public service'ларида; бу ерда фақат кўрсатиш учун
 * масштаблаш (SVG устун баландликлари, бар улушлари).
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    /** Графикдаги ойлар сони (жорий ой билан). */
    private static final int CHART_MONTHS = 6;

    /** Харажатлар картасидаги алоҳида кўрсатиладиган счётлар сони. */
    private static final int TOP_EXPENSES = 5;

    /**
     * SVG графикнинг битта ой устуни (баландликлар 0..100 px'да
     * тайёрлаб берилади - шаблонда арифметика қолмайди).
     */
    public record ChartBar(String label, int incomeHeight, int expenseHeight) { }

    /** Харажатлар картасининг битта бари (percent - энг каттасига нисбатан). */
    public record ExpenseBar(String name, BigDecimal amount, int percent) { }

    /** GL карталари (P&L ойлари, банк қолдиқлари, харажатлар). */
    private final LedgerDashboardService ledgerDashboard;

    /** AR карта - sales public API. */
    private final InvoiceService invoiceService;

    /** AP карта - purchase public API. */
    private final BillService billService;

    /** Inventory картаси: захира қиймати - мавжуд ҳисоб ҚАЙТА ишлатилади. */
    private final InventoryValuationService valuationService;

    /** Inventory картаси: омборлар сони - inventory public API. */
    private final WarehouseService warehouseService;

    /** Home валюта ва компания вақт минтақаси. */
    private final CompanySettingsService settingsService;

    /** Ой номлари учун i18n (график тагидаги қисқа белгилар). */
    private final Msg msg;

    /** Dashboard'ни йиғади - барча карталар бир саҳифада. */
    @GetMapping("/")
    public String index(Model model) {
        // OPT-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId ×2 + homeCurrency) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        YearMonth current = YearMonth.now(settings.zoneId());
        LocalDate today = LocalDate.now(settings.zoneId());

        // P&L карта: жорий ой рақамлари + охирги 6 ой графиги
        List<MonthPl> months = ledgerDashboard.monthlyPl(
                current.minusMonths(CHART_MONTHS - 1), current);
        MonthPl thisMonth = months.get(months.size() - 1);
        model.addAttribute("plIncome", thisMonth.income());
        model.addAttribute("plExpense", thisMonth.expense());
        model.addAttribute("plNet", thisMonth.income().subtract(thisMonth.expense()));
        model.addAttribute("chartBars", chartBars(months,
                MonthPl::month, MonthPl::income, MonthPl::expense));

        // Cash flow картаси (DEC-036): BANK счётлар оқими - жорий ой
        // рақамлари + P&L графиги нақшидаги қўш-устун SVG
        List<MonthCashFlow> cashMonths = ledgerDashboard.monthlyCashFlow(
                current.minusMonths(CHART_MONTHS - 1), current);
        MonthCashFlow cashNow = cashMonths.get(cashMonths.size() - 1);
        model.addAttribute("cashIn", cashNow.inflow());
        model.addAttribute("cashOut", cashNow.outflow());
        model.addAttribute("cashNet", cashNow.inflow().subtract(cashNow.outflow()));
        model.addAttribute("cashBars", chartBars(cashMonths,
                MonthCashFlow::month, MonthCashFlow::inflow, MonthCashFlow::outflow));

        // Банк қолдиқлари
        var banks = ledgerDashboard.bankBalances();
        model.addAttribute("banks", banks);
        model.addAttribute("banksTotal", banks.stream()
                .map(LedgerDashboardService.BankBalance::baseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // AR/AP карталар: очиқ жами + муддати ўтган (current'дан ташқари ҳаммаси)
        BigDecimal[] ar = openAndOverdue(invoiceService.arAging(today).stream()
                .map(r -> new BigDecimal[]{r.total(), r.current()}).toList());
        BigDecimal[] ap = openAndOverdue(billService.apAging(today).stream()
                .map(r -> new BigDecimal[]{r.total(), r.current()}).toList());
        model.addAttribute("arOpen", ar[0]);
        model.addAttribute("arOverdue", ar[1]);
        model.addAttribute("apOpen", ap[0]);
        model.addAttribute("apOverdue", ap[1]);
        // AR кенгайтмаси (DEC-036): охирги 30 кунда тўланган тушумлар
        model.addAttribute("arPaidLast30",
                invoiceService.paidTotal(today.minusDays(30), today));

        // Inventory картаси (DEC-036): мавжуд valuation ҳисоби ҚАЙТА
        // ишлатилади (янги ҳисоб ёзилмайди), омборлар сони фаоллари
        model.addAttribute("inventoryValue",
                valuationService.build(today, null).companyValue());
        model.addAttribute("warehouseCount", warehouseService.all().stream()
                .filter(com.averpo.erp.inventory.domain.Warehouse::isActive)
                .count());

        // Харажатлар тақсимоти: жорий ой топ-5 + бошқалар
        model.addAttribute("expenseBars", expenseBars(
                ledgerDashboard.expenseBreakdown(current.atDay(1), current.atEndOfMonth())));

        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        return "dashboard/dashboard";
    }

    /**
     * Ой устунлари: энг катта қийматга нисбатан 0..100 px масштаб.
     * Умумий - P&L (даромад/харажат) ҳам, cash flow (кирим/чиқим) ҳам
     * шу қолипдан ўтади (DEC-036), extractor'лар қатор турига қараб
     * берилади.
     */
    private <T> List<ChartBar> chartBars(List<T> rows,
                                         Function<T, YearMonth> month,
                                         Function<T, BigDecimal> first,
                                         Function<T, BigDecimal> second) {
        BigDecimal max = rows.stream()
                .flatMap(r -> java.util.stream.Stream.of(
                        first.apply(r), second.apply(r)))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<ChartBar> bars = new ArrayList<>();
        for (T row : rows) {
            bars.add(new ChartBar(
                    msg.get("month." + month.apply(row).getMonthValue()).substring(0, 3),
                    scale(first.apply(row), max), scale(second.apply(row), max)));
        }
        return bars;
    }

    /** Қийматни 0..100 оралиғига масштаблайди (max 0 бўлса 0). */
    private static int scale(BigDecimal value, BigDecimal max) {
        if (max.signum() <= 0 || value.signum() <= 0) {
            return 0;
        }
        return value.multiply(new BigDecimal("100"))
                .divide(max, 0, RoundingMode.HALF_UP).intValue();
    }

    /** Aging қаторларидан [очиқ жами, муддати ўтган] жуфтини йиғади. */
    private static BigDecimal[] openAndOverdue(List<BigDecimal[]> rows) {
        BigDecimal open = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        for (BigDecimal[] row : rows) {
            open = open.add(row[0]);
            overdue = overdue.add(row[0].subtract(row[1]));
        }
        return new BigDecimal[]{open, overdue};
    }

    /** Топ-5 харажат бари + «Бошқалар» (энг каттасига нисбатан улушлар). */
    private List<ExpenseBar> expenseBars(List<ExpenseSlice> slices) {
        if (slices.isEmpty()) {
            return List.of();
        }
        BigDecimal max = slices.get(0).amount();
        List<ExpenseBar> bars = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_EXPENSES, slices.size()); i++) {
            ExpenseSlice slice = slices.get(i);
            bars.add(new ExpenseBar(slice.name(), slice.amount(),
                    scale(slice.amount(), max)));
        }
        if (slices.size() > TOP_EXPENSES) {
            BigDecimal others = slices.subList(TOP_EXPENSES, slices.size()).stream()
                    .map(ExpenseSlice::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            bars.add(new ExpenseBar(msg.get("dash.others"), others, scale(others, max)));
        }
        return bars;
    }
}
