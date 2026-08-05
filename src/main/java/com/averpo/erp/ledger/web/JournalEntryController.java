package com.averpo.erp.ledger.web;

import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingException;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.web.Fmt;
import com.averpo.erp.shared.web.FormParsers;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Журнал проводкалари экранлари: рўйхат (филтр билан), қўлда киритиш
 * формаси (HTMX сатр қўшиш), кўриш, post/reverse/draft-delete.
 * GL ёзувлари фақат PostingService орқали (ТЕМИР ҚОИДА №2) -
 * бу контроллер repository'дан фақат ўқийди ва draft ўчиради.
 */
@Controller
@RequestMapping("/journal-entries")
@lombok.RequiredArgsConstructor
public class JournalEntryController {

    /** GL ёзишнинг ягона нуқтаси. */
    private final PostingService postingService;

    /** Счёт select'лари учун. */
    private final AccountService accountService;

    /** Home currency ва timezone учун. */
    private final CompanySettingsService settingsService;

    /** Header валюта select каталоги (DEC-107 QBO parity). */
    private final com.averpo.erp.shared.service.CurrencyService currencyService;

    /** Ўқиш ва draft ўчириш - ўз модулимиз ичида рухсат. */
    private final JournalEntryRepository entryRepository;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /** Йўналиш select'и (class-tracking.md) - shared каталог. */
    private final com.averpo.erp.shared.service.TxnClassService txnClassService;

    /** Рўйхат саҳифаси ҳажми (PERF-perf1 1-босқич). */
    private static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - аввалги ORDER BY'га айнан мос (сана, кейин рақам
     * камайиши) - саҳифалашга ўтишда экрандаги тартиб ўзгармасин.
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("entryDate"),
                    org.springframework.data.domain.Sort.Order.desc("entryNumber"));

    /**
     * Устун саралаш WHITELIST'и (DEC-105б): th калити → entity
     * property - хом параметр Sort'га тушмайди (TableSort). JE рўйхати
     * repo билан тўғридан ишлагани учун харита controller'да (бошқа
     * рўйхатларда service'да). description йўқ: узун эркин матн бўйича
     * саралаш маъносиз.
     */
    private static final java.util.Map<String, String> SORT_KEYS = java.util.Map.of(
            "number", "entryNumber",
            "date", "entryDate",
            "source", "sourceModule",
            "status", "status");

    /**
     * Рўйхат: сана оралиғи (default - шу йил, мавжуд хатти-ҳаракат
     * сақланган), статус ва матн (рақам/тавсиф, DEC-068) филтри.
     * Саҳифаланган (PERF-perf1) - ?page=, филтрлар линкларда
     * сақланади. Устун саралаш (DEC-105б): ?sort=/&dir= whitelist
     * орқали; саҳифа линклари филтр+sort'ни бирга ташийди.
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        // OPT-005: созламалар snapshot'и - оқимда битта SELECT
        java.time.ZoneId zone = settingsService.get().zoneId();
        // Давр default'и - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/DEC-055)
        LocalDate f = from != null ? from
                : LocalDate.now(zone).withDayOfYear(1);
        LocalDate t = to != null ? to : LocalDate.now(zone);
        EntryStatus st = parseStatusSafe(status);
        // DEC-105: саҳифа ҳажми ?size=/cookie'дан (PageSizeResolver)
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "journal-entries");
        // DEC-105б: хом sort/dir whitelist орқали (Sort'га тушмайди)
        var sorted = com.averpo.erp.shared.web.TableSort.resolve(
                sort, dir, SORT_KEYS, LIST_SORT);
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size, sorted.sort());
        // DEC-068: филтр комбинацияси Specification'да (audit услуби);
        // ledger ўз repo'сини ишлатади - қоида 6 бузилмайди
        org.springframework.data.domain.Page<JournalEntry> entryPage =
                entryRepository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("entryDate", f),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("entryDate", t),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", st),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(q,
                                "entryNumber", "description")), pageable);
        model.addAttribute("entries", entryPage.getContent());
        model.addAttribute("page", entryPage);
        // Саҳифа линклари жорий филтрларни сақлайди (audit қолипи);
        // th саралаш линклари учун sort'сиз, pager учун sort билан
        String filterQuery = new com.averpo.erp.shared.web.FilterQuery()
                .add("from", f).add("to", t).add("status", status).add("q", q).toString();
        model.addAttribute("filterQuery", filterQuery);
        model.addAttribute("pageQuery", filterQuery + sorted.query());
        model.addAttribute("sortKey", sorted.key());
        model.addAttribute("sortDir", sorted.dir());
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        return "ledger/journalEntries";
    }

    /** Янги проводка формаси - 8 та бўш сатр билан (QBO parity, DEC-107). */
    @GetMapping("/new")
    public String createForm(Model model) {
        JournalEntryForm form = JournalEntryForm.empty(8);
        // OPT-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/DEC-044)
        form.setEntryDate(LocalDate.now(settings.zoneId()));
        fillFormModel(model, form, settings);
        return "ledger/journalEntryForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        // DEC-014: тўлиқ рўйхат - группа счётлар select'да disabled жилд
        // бўлиб кўринади (нофаолларни accountOptions partial'и ташлайди)
        model.addAttribute("accounts", accountService.all());
        // DEC-107: валюта/курс энди header'да - сатрга homeCurrency керак эмас
        fillClassModel(model, settingsService.get());
        return "ledger/lineRow";
    }

    /** Сақлаш: action=draft - фақат draft, action=post - draft + post. */
    @PostMapping
    public String save(@ModelAttribute JournalEntryForm form,
                       @RequestParam String action,
                       Model model, RedirectAttributes redirect) {
        // OPT-005: битта snapshot toRequest'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            JournalEntryRequest request = toRequest(form, settings);
            JournalEntry entry = "post".equals(action)
                    ? postingService.createAndPost(request)
                    : postingService.createDraft(request);
            redirect.addFlashAttribute("message", msg.get("entries.saved",
                    entry.getEntryNumber(),
                    msg.get("status." + entry.getStatus().name())));
            return "redirect:/journal-entries/" + entry.getId();
        } catch (BusinessRuleException e) {
            // Кенг тип: PostingException'дан ташқари BR-CUR каби қўшни
            // каталог хатолари ҳам формага қайтади - global error page эмас
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "ledger/journalEntryForm";
        }
    }

    /**
     * Манба ҳужжатдан унинг GL ёзувига ўтиш (DEC-080, 063 симметрияси):
     * ҳужжат кўриш саҳифаларидаги «GL ёзуви →» линки шу ерга келади, бу
     * эса энг охирги асл JE view'ига redirect қилади. Статик URL - линк
     * рендер қилинганда JE рақами учун ортиқча сўров йўқ (063 принципи).
     *
     * <p>Репост қилинган манбада (REVERSED асл + POSTED репост) энг охирги
     * ёзув олинади (repo метод изоҳи). Топилмаса тушунарли 404 - манба
     * ҳали post қилинмаган ёки docId бегона.
     *
     * @throws NotFoundException манба бўйича асл JE топилмаса (404)
     */
    @GetMapping("/by-source/{module}/{docId}")
    public String bySource(@PathVariable String module, @PathVariable UUID docId) {
        JournalEntry entry = entryRepository
                .findFirstBySourceModuleAndSourceDocumentIdAndReversalOfIsNullOrderByCreatedAtDescIdDesc(
                        module, docId)
                .orElseThrow(() -> new NotFoundException(
                        "Манба бўйича GL ёзуви топилмади: " + module + "/" + docId));
        return "redirect:/journal-entries/" + entry.getId();
    }

    /** Битта проводкани кўриш. */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String view(@PathVariable UUID id, Model model) {
        JournalEntry entry = entryRepository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Entry топилмади: " + id));
        // OPT-005: созламалар snapshot'и - оқимда битта SELECT
        CompanySettings settings = settingsService.get();
        model.addAttribute("entry", entry);
        model.addAttribute("postedAtText",
                Fmt.dt(entry.getPostedAt(), settings.zoneId()));
        model.addAttribute("reversedByNumber", entry.getReversedBy() == null
                ? null : entry.getReversedBy().getEntryNumber());
        // DEC-063: сторно линклари иккала йўналишда - рақам ёнида id ҳам
        model.addAttribute("reversedById", entry.getReversedBy() == null
                ? null : entry.getReversedBy().getId().toString());
        model.addAttribute("reversalOfId", entry.getReversalOf() == null
                ? null : entry.getReversalOf().getId().toString());
        model.addAttribute("reversalOfNumber", entry.getReversalOf() == null
                ? null : entry.getReversalOf().getEntryNumber());
        // DEC-063: манба ҳужжатга «очиш» линки - mapping ЛОКАЛ (қоида 6)
        model.addAttribute("sourceUrl", SourceDocLinks.url(
                entry.getSourceModule(), entry.getSourceDocumentId()));
        model.addAttribute("today", LocalDate.now(settings.zoneId()).toString());
        // Сатр курсини фақат чет валютада кўрсатиш ва home жамидаги ёрлиқ учун
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        return "ledger/journalEntryView";
    }

    /** Draft'ни post қилиш. */
    @PostMapping("/{id}/post")
    public String post(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            JournalEntry entry = postingService.post(id);
            redirect.addFlashAttribute("message",
                    msg.get("entries.posted", entry.getEntryNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/journal-entries/" + id;
    }

    /** POSTED entry'ни сторно қилиш. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            JournalEntry storno = postingService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("entries.reversed", storno.getEntryNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/journal-entries/" + id;
    }

    /** Draft'ни ўчириш - қоида PostingService.deleteDraft'да (қоида №3). */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            JournalEntry entry = postingService.deleteDraft(id);
            redirect.addFlashAttribute("message",
                    msg.get("entries.deleted", entry.getEntryNumber()));
            return "redirect:/journal-entries";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/journal-entries/" + id;
        }
    }

    /** Форма model'ини тўлдиради - settings оқим бошидаги snapshot
     * (OPT-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, JournalEntryForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        // DEC-014: тўлиқ рўйхат - группа счётлар select'да disabled жилд
        model.addAttribute("accounts", accountService.all());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        // DEC-107: header валюта select каталоги (rateBlock компоненти учун)
        model.addAttribute("currencies", currencyService.active());
        fillClassModel(model, settings);
    }

    /**
     * Class tracking model'и (class-tracking.md): режим UI'ни бошқаради -
     * OFF'да рўйхат сўралмайди ҳам (майдонлар умуман render бўлмайди).
     */
    private void fillClassModel(Model model, CompanySettings settings) {
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** Формани PostingService request'ига айлантиради (бўш сатрлар ташланади). */
    private JournalEntryRequest toRequest(JournalEntryForm form, CompanySettings settings) {
        String home = settings.homeCurrencyCode();
        // PER_TXN: сарлавҳадаги битта Йўналиш ҳамма сатрга тарқатилади
        // (class-tracking.md - схема ягона, class доим сатрда туради)
        boolean perTxn = settings.getTrackClasses()
                == com.averpo.erp.shared.domain.ClassTrackingMode.PER_TXN;
        UUID headerClass = FormParsers.uuid(form.getClassId(),
                BusinessRule.BR_CLS_001, "Йўналиш");
        // DEC-107 (QBO parity): валюта/курс энди header'да - бутун проводка
        // битта валютада. Header қиймати ҲАММА сатрга тарқатилади, шунда домен
        // (сатр Money'си) ва BR валидациялари ЎЗГАРМАЙДИ (server тегилмади).
        String headerCurrency = form.getCurrency();
        String headerRate = form.getExchangeRate();
        List<JournalEntryRequest.Line> lines = new ArrayList<>();
        int no = 0;
        for (JournalEntryForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue; // тўлиқ бўш сатр - HTMX қўшиб ишлатилмаган қатор
            }
            UUID accountId = FormParsers.requireUuid(lf.getAccountId(),
                    BusinessRule.BR_LED_016, no + "-сатр: счёт");
            Money debit = money(no, lf.getDebitAmount(), headerCurrency,
                    headerRate, home);
            Money credit = money(no, lf.getCreditAmount(), headerCurrency,
                    headerRate, home);
            UUID lineClass = perTxn ? headerClass
                    : FormParsers.uuid(lf.getClassId(), BusinessRule.BR_CLS_001,
                            no + "-сатр: Йўналиш");
            lines.add(new JournalEntryRequest.Line(
                    accountId, debit, credit,
                    null, null, null, Strings.blankToNull(lf.getMemo()), lineClass));
        }
        return JournalEntryRequest.manual(form.getEntryDate(),
                Strings.blankToNull(form.getDescription()), lines);
    }

    /** Сумма+валюта+курсдан Money ясайди; сумма бўш бўлса null. */
    private Money money(int lineNo, String amountText, String currencyText,
                        String rateText, String home) {
        BigDecimal amount = parseNumber(lineNo, amountText, "сумма");
        if (amount == null) {
            return null;
        }
        String currency = currencyText == null || currencyText.isBlank()
                ? home : currencyText.strip().toUpperCase();
        if (currency.equals(home)) {
            return Money.ofBase(amount, home);
        }
        BigDecimal rate = parseNumber(lineNo, rateText, "курс");
        if (rate == null || rate.signum() <= 0) {
            throw new PostingException(BusinessRule.BR_LED_018, lineNo + "-сатр: " + currency
                    + " валютаси учун курс киритилиши шарт");
        }
        return Money.of(amount, currency, rate);
    }

    /** Сон парси - normalize қоидаси FormParsers'да (NBSP ҳам олинади). */
    private BigDecimal parseNumber(int lineNo, String text, String field) {
        return FormParsers.decimal(text, BusinessRule.BR_LED_019,
                lineNo + "-сатр: " + field);
    }


    /**
     * Query параметрдан статусни хавфсиз парслайди - нотўғри қиймат
     * (?status=abc) 500 эмас, «Ҳаммаси» филтрига тушади.
     */
    private static EntryStatus parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return EntryStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
