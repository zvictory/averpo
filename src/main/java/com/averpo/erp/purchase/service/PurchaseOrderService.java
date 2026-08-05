package com.averpo.erp.purchase.service;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.purchase.domain.PurchaseOrder;
import com.averpo.erp.purchase.domain.PurchaseOrderStatus;
import com.averpo.erp.purchase.repo.PurchaseOrderRepository;
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
 * PurchaseOrder'нинг ягона public API'си (docs/modules/estimates-po.md)
 * - EstimateService'нинг харид томонидаги кўзгуси. GL'СИЗ ҳужжат:
 * PostingService/InventoryService умуман import қилинмайди (spec'нинг
 * review нуқтаси). Айлантириш оқими: BillController prefill формани
 * очади, сақлангач {@link #markConverted} чақиради.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseOrderService {

    /** Битта буюртма формаси маълумотлари - create/update учун умумий. */
    public record PurchaseOrderData(UUID vendorId, LocalDate poDate,
                                    LocalDate expectedDate, String currency,
                                    BigDecimal exchangeRate, String memo,
                                    boolean amountsInclusive, List<LineData> lines) { }

    /** Битта сатр маълумотлари - омбор/счёт йўқ (GL'сиз, item буюртмаси). */
    public record LineData(UUID itemId, BigDecimal quantity, BigDecimal unitPrice,
                           UUID unitId, UUID taxRateId, String memo) { }

    /** Буюртмалар репозиторийси. */
    private final PurchaseOrderRepository repository;

    /** Ҳужжат рақамлари (PO-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Vendor текшируви - contact модулининг public API'си. */
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
    public PurchaseOrder get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Буюртма топилмади: " + id));
    }

    /** Кўриш/таҳрир/prefill учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public PurchaseOrder getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Буюртма топилмади: " + id));
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
                    org.springframework.data.domain.Sort.Order.desc("poDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /**
     * Рўйхат филтри (DEC-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - рақам/изоҳ contains
     * (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             PurchaseOrderStatus status, UUID vendorId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (PERF-perf1), тўлиқ филтр
     * (DEC-068): давр/статус/vendor/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PurchaseOrder> list(ListFilter filter, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size, LIST_SORT);
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                com.averpo.erp.shared.repo.ListSpecs.dateFrom("poDate", filter.from()),
                com.averpo.erp.shared.repo.ListSpecs.dateTo("poDate", filter.to()),
                com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                com.averpo.erp.shared.repo.ListSpecs.eq("vendorId", filter.vendorId()),
                com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                        "poNumber", "memo")), pageable);
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (DEC-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PurchaseOrder> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /** Bill кўришидаги «Буюртмадан» белгиси учун - linked манба. */
    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> findByBillId(UUID billId) {
        return repository.findByBillId(billId);
    }

    /**
     * Янги OPEN буюртма яратади - рақам DocumentSequence'дан.
     *
     * @throws BusinessRuleException BR-PO-001
     */
    public PurchaseOrder create(PurchaseOrderData data) {
        Normalized normalized = validate(data);
        PurchaseOrder po = new PurchaseOrder(
                sequenceService.next(DocumentType.PURCHASE_ORDER, data.poDate()),
                data.vendorId(), data.poDate(), data.expectedDate(),
                normalized.currency(), normalized.rate(), data.amountsInclusive(),
                Strings.blankToNull(data.memo()));
        applyLines(po, normalized.lines());
        return repository.saveAndFlush(po);
    }

    /** Таҳрир: сарлавҳа + сатрлар қайта терилади (BR-PO-002 entity'да). */
    public PurchaseOrder update(UUID id, PurchaseOrderData data) {
        PurchaseOrder po = getWithLines(id);
        Normalized normalized = validate(data);
        po.updateHeader(data.vendorId(), data.poDate(), data.expectedDate(),
                normalized.currency(), normalized.rate(), data.amountsInclusive(),
                Strings.blankToNull(data.memo()));
        po.clearLines();
        // uq_po_line_no билан: эски сатрлар аввал ўчирилиши шарт
        // (InvoiceService updateDraft'даги PERF-010 сабоғи)
        repository.flush();
        applyLines(po, normalized.lines());
        return repository.saveAndFlush(po);
    }

    /** Ўчириш - фақат айлантирилмаган буюртма (BR-PO-003 entity'да). */
    public void delete(UUID id) {
        PurchaseOrder po = getWithLines(id);
        po.requireDeletable();
        repository.delete(po);
    }

    /** Status ўтиши (ўтиш қоидалари PurchaseOrder.changeStatus'да). */
    public PurchaseOrder changeStatus(UUID id, PurchaseOrderStatus status) {
        PurchaseOrder po = get(id);
        po.changeStatus(status);
        return po;
    }

    /**
     * Айлантириш олдидан текширув: сатрлари билан қайтаради (prefill
     * учун), айлантириб бўлмаса BR-PO-002/003 отади.
     */
    @Transactional(readOnly = true)
    public PurchaseOrder requireConvertible(UUID id) {
        PurchaseOrder po = getWithLines(id);
        po.requireConvertible();
        return po;
    }

    /**
     * Айлантирилди: bill сақлангандан кейин BillController чақиради -
     * буюртма CLOSED + linked bill id (BR-PO-002/003 қайта текширилади).
     */
    public PurchaseOrder markConverted(UUID poId, UUID billId) {
        PurchaseOrder po = get(poId);
        po.markConverted(billId);
        return po;
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
     * Тўлиқ валидация (BR-PO-001, валюта vendor'дан - BR-PO-004,
     * DEC-087) + нормализация: ҚҚС бўлиниши tax.md механизмида -
     * фақат кўрсатиш учун (GL йўқ).
     */
    private Normalized validate(PurchaseOrderData data) {
        if (data.vendorId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_PO_001,
                    "Таъминотчи танланиши шарт");
        }
        Contact vendor = contactService.get(data.vendorId());
        if (vendor.getType() != ContactType.VENDOR || !vendor.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_PO_001,
                    "Таъминотчи фаол VENDOR типдаги контакт бўлиши шарт: "
                    + vendor.getDisplayName());
        }
        if (data.poDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_PO_001,
                    "Буюртма санаси киритилиши шарт");
        }
        // Валюта ҳақиқат манбаи - таъминотчи контакти (QBO қатъий, DEC-087):
        // client қиймати фақат мосликка текширилади, ҳужжатга контактники ёзилади
        Currency currency = currencyService.require(contactService
                .requireDocumentCurrency(vendor, data.currency(), BusinessRule.BR_PO_004));
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_PO_001);
        if (data.lines() == null || data.lines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_PO_001,
                    "Буюртмада камида битта сатр бўлиши шарт");
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
            throw new BusinessRuleException(BusinessRule.BR_PO_001,
                    no + "-сатр: item танланиши шарт");
        }
        Item item = items.get(line.itemId());
        if (item == null) {
            throw new NotFoundException("Item топилмади: " + line.itemId());
        }
        if (!item.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_PO_001,
                    no + "-сатр: нофаол item буюртма қилинмайди: «" + item.getName() + "»");
        }
        if (line.quantity() == null || line.quantity().signum() <= 0
                || line.unitPrice() == null || line.unitPrice().signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_PO_001,
                    no + "-сатр: миқдор мусбат, нарх манфий эмас бўлиши шарт");
        }
        BigDecimal raw = line.quantity().multiply(line.unitPrice())
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal taxValue = taxRateService.documentRateValue(line.taxRateId(), null);
        TaxAmounts ta = TaxAmounts.of(raw, taxValue, inclusive);
        if (ta.net().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_PO_001,
                    no + "-сатр: нетто сумма мусбат бўлиши шарт");
        }
        return new NormalizedLine(line.itemId(), line.quantity(), line.unitPrice(),
                line.unitId(), ta.net(), line.taxRateId(), taxValue, ta.tax(),
                Strings.blankToNull(line.memo()));
    }

    /** Сатрларни буюртмага теради (қийматлар validate'да нормаллашган). */
    private void applyLines(PurchaseOrder po, List<NormalizedLine> lines) {
        for (NormalizedLine line : lines) {
            po.addLine(line.itemId(), line.quantity(), line.unitPrice(),
                    line.unitId(), line.amount(), line.taxRateId(),
                    line.taxRateValue(), line.taxAmount(), line.memo());
        }
    }
}
