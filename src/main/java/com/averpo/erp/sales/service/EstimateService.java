package com.averpo.erp.sales.service;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.sales.domain.Estimate;
import com.averpo.erp.sales.domain.EstimateStatus;
import com.averpo.erp.sales.repo.EstimateRepository;
import com.averpo.erp.shared.BatchLookup;
import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.DocumentSequenceService;
import com.averpo.erp.tax.service.TaxAmounts;
import com.averpo.erp.tax.service.TaxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Estimate'нинг ягона public API'си (docs/modules/estimates-po.md).
 * GL'СИЗ ҳужжат: PostingService/InventoryService умуман import
 * қилинмайди (spec'нинг review нуқтаси) - ҳужжат фақат сақланади,
 * ҳисоб-китоб (ҚҚС net/tax) кўрсатиш учун tax.md механизмида.
 * Айлантириш оқими: InvoiceController prefill формани очади,
 * сақлангач {@link #markConverted} чақиради.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EstimateService {

    /**
     * Битта estimate формаси маълумотлари - create/update учун умумий
     * (InvoiceData қолипи, GL'сиз майдонларгина).
     */
    public record EstimateData(UUID customerId, LocalDate estimateDate,
                               LocalDate expirationDate, String currency,
                               BigDecimal exchangeRate, String memo,
                               boolean amountsInclusive, List<LineData> lines) { }

    /** Битта сатр маълумотлари - омбор/даромад счёти йўқ (GL'сиз). */
    public record LineData(UUID itemId, BigDecimal quantity, BigDecimal unitPrice,
                           UUID unitId, UUID taxRateId, String memo) { }

    /** Estimate репозиторийси. */
    private final EstimateRepository repository;

    /** Ҳужжат рақамлари (EST-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Customer текшируви - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Item фаоллиги текшируви - item модулининг public API'си. */
    private final ItemService itemService;

    /** Валюта каталоги. */
    private final CurrencyService currencyService;

    /** Home currency - курс валидацияси учун. */
    private final CompanySettingsService settingsService;

    /** ҚҚС ставкаси snapshot/фаоллик - tax модулининг public API'си. */
    private final TaxRateService taxRateService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Estimate get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Estimate топилмади: " + id));
    }

    /** Кўриш/таҳрир/prefill учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public Estimate getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Estimate топилмади: " + id));
    }

    /** Рўйхат саҳифаси ҳажми (PERF-perf1 2-босқич). */
    public static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - аввалги ORDER BY'га айнан мос (янгидан эскига,
     * тенг санада яратилиш вақти) - саҳифалашга ўтишда экран тартиби
     * ўзгармасин (PERF-perf1). A-тўлқин рўйхати retrofit'и.
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("estimateDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /**
     * Рўйхат филтри (DEC-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - рақам/изоҳ contains
     * (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             EstimateStatus status, UUID customerId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (PERF-perf1), тўлиқ филтр
     * (DEC-068): давр/статус/мижоз/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Estimate> list(ListFilter filter, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size, LIST_SORT);
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                com.averpo.erp.shared.repo.ListSpecs.dateFrom("estimateDate", filter.from()),
                com.averpo.erp.shared.repo.ListSpecs.dateTo("estimateDate", filter.to()),
                com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                com.averpo.erp.shared.repo.ListSpecs.eq("customerId", filter.customerId()),
                com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                        "estimateNumber", "memo")), pageable);
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (DEC-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Estimate> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /** Invoice кўришидаги «Estimate'дан» белгиси учун - linked манба. */
    @Transactional(readOnly = true)
    public Optional<Estimate> findByInvoiceId(UUID invoiceId) {
        return repository.findByInvoiceId(invoiceId);
    }

    /**
     * Янги PENDING estimate яратади - рақам DocumentSequence'дан.
     *
     * @throws BusinessRuleException BR-EST-001
     */
    public Estimate create(EstimateData data) {
        Normalized normalized = validate(data);
        Estimate estimate = new Estimate(
                sequenceService.next(DocumentType.ESTIMATE, data.estimateDate()),
                data.customerId(), data.estimateDate(), data.expirationDate(),
                normalized.currency(), normalized.rate(), data.amountsInclusive(),
                Strings.blankToNull(data.memo()));
        applyLines(estimate, normalized.lines());
        return repository.saveAndFlush(estimate);
    }

    /** Таҳрир: сарлавҳа + сатрлар қайта терилади (BR-EST-002 entity'да). */
    public Estimate update(UUID id, EstimateData data) {
        Estimate estimate = getWithLines(id);
        Normalized normalized = validate(data);
        estimate.updateHeader(data.customerId(), data.estimateDate(),
                data.expirationDate(), normalized.currency(), normalized.rate(),
                data.amountsInclusive(), Strings.blankToNull(data.memo()));
        estimate.clearLines();
        // uq_estimate_line_no билан: Hibernate flush'да INSERT DELETE'дан
        // олдин бажарилади - эски сатрлар аввал ўчирилиши шарт
        // (InvoiceService updateDraft'даги PERF-010 сабоғи)
        repository.flush();
        applyLines(estimate, normalized.lines());
        return repository.saveAndFlush(estimate);
    }

    /** Ўчириш - фақат айлантирилмаган ҳужжат (BR-EST-003 entity'да). */
    public void delete(UUID id) {
        Estimate estimate = getWithLines(id);
        estimate.requireDeletable();
        repository.delete(estimate);
    }

    /** Status ўтиши (ўтиш қоидалари Estimate.changeStatus'да). */
    public Estimate changeStatus(UUID id, EstimateStatus status) {
        Estimate estimate = get(id);
        estimate.changeStatus(status);
        return estimate;
    }

    /**
     * Айлантириш олдидан текширув: сатрлари билан қайтаради (prefill
     * учун), айлантириб бўлмаса BR-EST-002/003 отади.
     */
    @Transactional(readOnly = true)
    public Estimate requireConvertible(UUID id) {
        Estimate estimate = getWithLines(id);
        estimate.requireConvertible();
        return estimate;
    }

    /**
     * Айлантирилди: invoice сақлангандан кейин InvoiceController
     * чақиради - estimate CLOSED + linked invoice id (BR-EST-002/003
     * entity'да қайта текширилади).
     */
    public Estimate markConverted(UUID estimateId, UUID invoiceId) {
        Estimate estimate = get(estimateId);
        estimate.markConverted(invoiceId);
        return estimate;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate,
                              List<NormalizedLine> lines) { }

    /** Нормаллашган сатр - net/tax ҳисобланган, ставка snapshot'ли. */
    private record NormalizedLine(UUID itemId, BigDecimal quantity, BigDecimal unitPrice,
                                  UUID unitId, BigDecimal amount, UUID taxRateId,
                                  BigDecimal taxRateValue, BigDecimal taxAmount,
                                  String memo) { }

    /**
     * Тўлиқ валидация (BR-EST-001, валюта мижоздан - BR-EST-004,
     * DEC-087) + нормализация: ҚҚС бўлиниши tax.md механизмида
     * (net/tax фақат кўрсатиш учун сақланади - GL йўқ).
     */
    private Normalized validate(EstimateData data) {
        if (data.customerId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_EST_001,
                    "Мижоз танланиши шарт");
        }
        Contact customer = contactService.get(data.customerId());
        if (customer.getType() != ContactType.CUSTOMER || !customer.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_EST_001,
                    "Мижоз фаол CUSTOMER типдаги контакт бўлиши шарт: "
                    + customer.getDisplayName());
        }
        if (data.estimateDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_EST_001,
                    "Estimate санаси киритилиши шарт");
        }
        // Валюта ҳақиқат манбаи - мижоз контакти (QBO қатъий, DEC-087):
        // client қиймати фақат мосликка текширилади, ҳужжатга контактники ёзилади
        Currency currency = currencyService.require(contactService
                .requireDocumentCurrency(customer, data.currency(), BusinessRule.BR_EST_004));
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_EST_001);
        if (data.lines() == null || data.lines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_EST_001,
                    "Estimate'да камида битта сатр бўлиши шарт");
        }
        // Батч lookup (DEC-045 findAllById, OPT-003): сатр-циклда
        // item биттадан ўқилмасин - битта IN сўров билан Map'га олинади
        Map<UUID, Item> items = BatchLookup.byId(
                itemService.findAllById(BatchLookup.ids(data.lines(), LineData::itemId)));
        List<NormalizedLine> lines = new ArrayList<>();
        int no = 0;
        for (LineData line : data.lines()) {
            no++;
            lines.add(validateLine(no, line, data.amountsInclusive(), items));
        }
        return new Normalized(currency, rate, lines);
    }

    /**
     * Сатр валидацияси: item фаол, сонлар мусбат, net/tax бўлиниши.
     * item олдиндан юкланган батч Map'дан ўқилади (OPT-003) -
     * топилмаса {@link NotFoundException} (аввалги get() хулқи айнан).
     */
    private NormalizedLine validateLine(int no, LineData line, boolean inclusive,
                                        Map<UUID, Item> items) {
        if (line.itemId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_EST_001,
                    no + "-сатр: item танланиши шарт");
        }
        Item item = items.get(line.itemId());
        if (item == null) {
            throw new NotFoundException("Item топилмади: " + line.itemId());
        }
        if (!item.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_EST_001,
                    no + "-сатр: нофаол item таклиф қилинмайди: «" + item.getName() + "»");
        }
        if (line.quantity() == null || line.quantity().signum() <= 0
                || line.unitPrice() == null || line.unitPrice().signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_EST_001,
                    no + "-сатр: миқдор мусбат, нарх манфий эмас бўлиши шарт");
        }
        BigDecimal raw = line.quantity().multiply(line.unitPrice())
                .setScale(4, RoundingMode.HALF_UP);
        // Ставка snapshot/фаоллик (BR-TAX-003/004) + net/tax бўлиниши -
        // фақат кўрсатиш учун (GL'га ҳеч нарса ёзилмайди)
        BigDecimal taxValue = taxRateService.documentRateValue(line.taxRateId(), null);
        TaxAmounts ta = TaxAmounts.of(raw, taxValue, inclusive);
        if (ta.net().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_EST_001,
                    no + "-сатр: нетто сумма мусбат бўлиши шарт");
        }
        return new NormalizedLine(line.itemId(), line.quantity(), line.unitPrice(),
                line.unitId(), ta.net(), line.taxRateId(), taxValue, ta.tax(),
                Strings.blankToNull(line.memo()));
    }

    /** Сатрларни estimate'га теради (қийматлар validate'да нормаллашган). */
    private void applyLines(Estimate estimate, List<NormalizedLine> lines) {
        for (NormalizedLine line : lines) {
            estimate.addLine(line.itemId(), line.quantity(), line.unitPrice(),
                    line.unitId(), line.amount(), line.taxRateId(),
                    line.taxRateValue(), line.taxAmount(), line.memo());
        }
    }
}
