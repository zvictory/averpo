package com.averpo.erp.payroll.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.payroll.domain.PayrollRun;
import com.averpo.erp.payroll.service.PayrollRunService;
import com.averpo.erp.payroll.service.PayrollRunService.LineData;
import com.averpo.erp.payroll.service.PayrollRunService.RunData;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.web.Fmt;
import com.averpo.erp.shared.web.FormParsers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Иш ҳақи ҳисоблаши экранлари (payroll.md): саҳифаланган рўйхат
 * (period DESC), FULL форма («Ходимларни тўлдириш» prefill + жонли
 * ҳисоб), кўриш (JE ҳавола + reverse). Ҳамма ёзиш PayrollRunService
 * орқали - контроллер юпқа. VIEWER ҳимояси SecurityConfig'даги
 * POST /** қоидасида (BR-ATT-004 нақши).
 */
@Controller
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class PayrollRunController {

    /** Ҳисоблашнинг ягона public API'си. */
    private final PayrollRunService payrollRunService;

    /** Ходим select ва номлари учун. */
    private final ContactService contactService;

    /** Ставкалар (жонли ҳисоб учун) ва home currency. */
    private final CompanySettingsService settingsService;

    /** Йўналиш select'и (class-tracking.md) - shared каталог. */
    private final com.averpo.erp.shared.service.TxnClassService txnClassService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - саҳифаланган, period DESC; тўлиқ филтр қатори
     * (Arbitr-068): давр/статус/матн, саҳифа линклари филтрни сақлайди
     * (audit қолипи). Контакт филтри йўқ - ходим run САТРИДА.
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "payroll");
        var runPage = payrollRunService.list(new PayrollRunService.ListFilter(
                from, to, parseStatusSafe(status), q), page, size);
        var runs = runPage.getContent();
        model.addAttribute("runs", runs);
        model.addAttribute("page", runPage);
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Жами gross/net - JPQL агрегатдан (рўйхат сатри lazy lines'ни айланмасин,
        // open-in-view=false → LazyInitializationException, Arbitr-054)
        model.addAttribute("totals", payrollRunService.totalsByRun(
                runs.stream().map(PayrollRun::getId).toList()));
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("filterQuery", new com.averpo.erp.shared.web.FilterQuery()
                .add("from", from).add("to", to).add("status", status)
                .add("q", q).toString());
        return "payroll/payrollRuns";
    }

    /** Query қийматидан статусни хавфсиз парслайди (бузуқ қиймат - филтрсиз). */
    private static PayrollRun.Status parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PayrollRun.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Янги форма - бўш; сатрлар «Ходимларни тўлдириш» билан келади. */
    @GetMapping("/new")
    public String createForm(Model model) {
        PayrollRunForm form = PayrollRunForm.empty(3);
        // Sanjar-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId ×2/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        // Default сана + period ойи - компания zoneId'даги «бугун» (JVM tz эмас,
        // қоида 12; сана Arbitr-044, period ойи Arbitr-055)
        form.setRunDate(LocalDate.now(settings.zoneId()));
        form.setPeriod(java.time.YearMonth.now(settings.zoneId()).toString());
        fillFormModel(model, form, settings);
        return "payroll/payrollRunForm";
    }

    /** Мавжуд DRAFT'ни таҳрирлаш формаси. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model,
                           RedirectAttributes redirect) {
        PayrollRun run = payrollRunService.getWithLines(id);
        if (run.getStatus() != PayrollRun.Status.DRAFT) {
            redirect.addFlashAttribute("error", msg.get("prun.onlyDraftEditable"));
            return "redirect:/payroll/" + id;
        }
        fillFormModel(model, PayrollRunForm.from(run), settingsService.get());
        return "payroll/payrollRunForm";
    }

    /** HTMX partial: формага битта бўш сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model, settingsService.get());
        return "payroll/payrollRunLineRow";
    }

    /**
     * HTMX partial: «Ходимларни тўлдириш» - фаол EMPLOYEE'лар oklad
     * билан (tbody тўлиқ алмашади, аввалги сатрлар ўчади).
     */
    @GetMapping("/prefill-rows")
    public String prefillRows(Model model) {
        List<PayrollRunForm.LineForm> lines = new ArrayList<>();
        for (LineData data : payrollRunService.prefillLines()) {
            PayrollRunForm.LineForm lf = new PayrollRunForm.LineForm();
            lf.setEmployeeId(data.employeeId().toString());
            lf.setGross(data.gross() == null ? null : Fmt.n(data.gross()));
            lines.add(lf);
        }
        model.addAttribute("prefillLines", lines);
        fillLineRefs(model, settingsService.get());
        return "payroll/payrollRunPrefillRows";
    }

    /** Сақлаш: action=draft - фақат сақлаш, action=post - сақлаш + post. */
    @PostMapping
    public String save(@ModelAttribute PayrollRunForm form,
                       @RequestParam String action,
                       Model model, RedirectAttributes redirect) {
        // Sanjar-005: битта snapshot toData'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            UUID id = FormParsers.uuid(form.getId(), BusinessRule.NOT_FOUND, "Ҳисоблаш");
            PayrollRun run = payrollRunService.saveDraft(id, toData(form, settings));
            if ("post".equals(action)) {
                run = payrollRunService.post(run.getId());
            }
            redirect.addFlashAttribute("message", msg.get("prun.saved",
                    run.getRunNumber(), msg.get("status." + run.getStatus().name())));
            return "redirect:/payroll/" + run.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "payroll/payrollRunForm";
        }
    }

    /** Кўриш: сатрлар + жамилар + JE ҳаваласи + post/reverse. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        PayrollRun run = payrollRunService.getWithLines(id);
        // Sanjar-005: созламалар snapshot'и - оқимда битта SELECT
        CompanySettings settings = settingsService.get();
        model.addAttribute("run", run);
        model.addAttribute("employeeNames", employeeNames(run));
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        model.addAttribute("today",
                LocalDate.now(settings.zoneId()).toString());
        return "payroll/payrollRunView";
    }

    /** Draft'ни post қилиш. */
    @PostMapping("/{id}/post")
    public String post(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            PayrollRun run = payrollRunService.post(id);
            redirect.addFlashAttribute("message",
                    msg.get("prun.posted", run.getRunNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/payroll/" + id;
    }

    /** POSTED ҳисоблашни сторно қилиш. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            PayrollRun run = payrollRunService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("prun.reversed", run.getRunNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/payroll/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'и: сатр ставкалари жонли ҳисоб учун JS'га узатилади -
     * settings оқим бошидаги snapshot (Sanjar-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, PayrollRunForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        // Жонли ҳисоб фақат кўриниш - ҳақиқий snapshot service'да
        model.addAttribute("incomeTaxRate", settings.getIncomeTaxRate().toPlainString());
        model.addAttribute("pensionRate", settings.getPensionRate().toPlainString());
        model.addAttribute("socialTaxRate", settings.getSocialTaxRate().toPlainString());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        fillLineRefs(model, settings);
    }

    /** Сатр select'лари: фаол ходимлар + Йўналишлар. */
    private void fillLineRefs(Model model, CompanySettings settings) {
        model.addAttribute("employees",
                contactService.byType(ContactType.EMPLOYEE, false));
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** Формани service маълумотига айлантиради (бўш сатрлар ташланади). */
    private RunData toData(PayrollRunForm form, CompanySettings settings) {
        // PER_TXN (class-tracking.md): сарлавҳадаги битта Йўналиш ҳамма
        // сатрга тарқатилади - схема ягона, class доим сатрда туради
        boolean perTxn = settings.getTrackClasses()
                == com.averpo.erp.shared.domain.ClassTrackingMode.PER_TXN;
        UUID headerClass = FormParsers.uuid(form.getClassId(),
                BusinessRule.BR_CLS_001, "Йўналиш");
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (PayrollRunForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(
                    FormParsers.uuid(lf.getEmployeeId(), BusinessRule.BR_PYR_003,
                            no + "-сатр: ходим"),
                    FormParsers.decimal(lf.getGross(), BusinessRule.BR_PYR_003,
                            no + "-сатр: gross"),
                    perTxn ? headerClass
                            : FormParsers.uuid(lf.getClassId(), BusinessRule.BR_CLS_001,
                                    no + "-сатр: Йўналиш"),
                    lf.getMemo()));
        }
        return new RunData(form.getPeriod(), form.getRunDate(), form.getMemo(), lines);
    }

    /**
     * Run сатрларидаги ходим номлари - фақат керакли id'лар byIds/IN
     * сўровда (ARBITR-105б, Ulugbek-003 §1); нофаоллар ҳам келади -
     * тарихий ҳисоблашда ном кўриниши шарт.
     */
    private Map<UUID, String> employeeNames(PayrollRun run) {
        List<UUID> ids = new ArrayList<>();
        for (var line : run.getLines()) {
            if (line.getEmployeeId() != null) {
                ids.add(line.getEmployeeId());
            }
        }
        return contactService.namesByIds(ids);
    }
}
