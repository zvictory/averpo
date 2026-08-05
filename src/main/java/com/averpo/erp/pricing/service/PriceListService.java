package com.averpo.erp.pricing.service;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.pricing.domain.PriceList;
import com.averpo.erp.pricing.domain.PriceListCustomer;
import com.averpo.erp.pricing.domain.PriceListItem;
import com.averpo.erp.pricing.repo.PriceListCustomerRepository;
import com.averpo.erp.pricing.repo.PriceListItemRepository;
import com.averpo.erp.pricing.repo.PriceListRepository;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Нарх рўйхатларининг ягона public API'си (docs/modules/price-list.md):
 * каталог CRUD, поғонали нархлар, мижоз бириктируви ва invoice prefill
 * учун {@link #resolvePrice}. Ҳужжатларга ҳавола сақланмайди - фақат
 * prefill манбаси, GL/posting'га таъсир йўқ.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PriceListService {

    /**
     * Рўйхат сарлавҳаси формаси маълумотлари - create/update учун умумий.
     * {@code active} фақат update'да ишлатилади: create ҳамиша фаол
     * яратади (каталог қолипи - tax/unit нақши, Arbitr-030 7-банд).
     */
    public record PriceListData(String name, String currencyCode,
                                LocalDate validFrom, LocalDate validTo,
                                boolean defaultList, boolean active) { }

    /** Рўйхатлар репозиторийси. */
    private final PriceListRepository repository;

    /** Поғоналар репозиторийси. */
    private final PriceListItemRepository itemRepository;

    /** Бириктирувлар репозиторийси. */
    private final PriceListCustomerRepository customerRepository;

    /** Рўйхат валютаси каталогдан (BR-CUR-*). */
    private final CurrencyService currencyService;

    /** Item мавжудлик/фаоллик текшируви (BR-PL-007) - item public API. */
    private final ItemService itemService;

    /** Мижоз текшируви (BR-PL-008) - contact public API. */
    private final ContactService contactService;

    /** resolvePrice'да сана берилмаса «бугун» компания зонасида олинади. */
    private final CompanySettingsService settingsService;

    // ---- каталог ----

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public PriceList get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Нарх рўйхати топилмади: " + id));
    }

    /** Созламалар экрани учун ҳаммаси. */
    @Transactional(readOnly = true)
    public List<PriceList> all() {
        return repository.findAllByOrderByName();
    }

    /**
     * Янги рўйхат.
     *
     * @throws BusinessRuleException BR-PL-001, BR-PL-004 ва BR-CUR-*
     */
    public PriceList create(PriceListData data) {
        validateHeader(data, null);
        Currency currency = currencyService.require(data.currencyCode());
        if (data.defaultList()) {
            releaseDefault(null);
        }
        // Каталог қолипи (tax/unit нақши): янги ёзув ҲАМИША фаол -
        // data.active() фақат update'да ишлайди (Arbitr-030 7-банд)
        return saveGuarded(new PriceList(data.name().strip(), currency,
                data.validFrom(), data.validTo(), data.defaultList()));
    }

    /** Сарлавҳани янгилайди (default алмашуви автоматик - BR-PL-003). */
    public PriceList update(UUID id, PriceListData data) {
        PriceList list = get(id);
        validateHeader(data, id);
        Currency currency = currencyService.require(data.currencyCode());
        if (data.defaultList() && !list.isDefaultList()) {
            releaseDefault(id);
        }
        list.update(data.name().strip(), currency, data.validFrom(),
                data.validTo(), data.defaultList(), data.active());
        // saveGuarded орқали: параллел иккинчи default commit'да эмас,
        // шу ерда ушланиб BR-PL-003 га таржима қилинади (Arbitr-030 3-банд)
        return saveGuarded(list);
    }

    // ---- поғоналар ----

    /** Рўйхат поғоналари - экран/тестлар учун (min_quantity тартибида). */
    @Transactional(readOnly = true)
    public List<PriceListItem> pricesOf(UUID priceListId) {
        return itemRepository.findByPriceListIdOrderByMinQuantityAsc(priceListId);
    }

    /**
     * Поғона қўшади.
     *
     * @throws BusinessRuleException BR-PL-002 (сонлар), BR-PL-005
     *         (дубликат поғона), BR-PL-007 (item нофаол/йўқ)
     */
    public PriceListItem addPrice(UUID priceListId, UUID itemId,
                                  BigDecimal minQuantity, BigDecimal price) {
        PriceList list = get(priceListId);
        Item item = itemService.get(itemId);
        if (!item.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_PL_007,
                    "Нофаол item'га поғона киритилмайди: «" + item.getName() + "»");
        }
        BigDecimal qty = minQuantity == null ? BigDecimal.ONE : minQuantity;
        if (qty.signum() <= 0 || price == null || price.signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_PL_002,
                    "min_quantity мусбат, нарх манфий эмас бўлиши керак");
        }
        itemRepository.findByPriceListIdAndItemIdAndMinQuantity(priceListId, itemId, qty)
                .ifPresent(existing -> {
                    throw new BusinessRuleException(BusinessRule.BR_PL_005,
                            "«" + item.getName() + "» учун " + qty
                            + " поғонаси аллақачон бор");
                });
        return itemRepository.save(new PriceListItem(list, itemId, qty, price));
    }

    /**
     * Поғонани ўчиради (ҳужжатларга ҳавола йўқ - фақат prefill манбаси).
     * Scope текширилади: поғона айнан шу рўйхатники бўлиши шарт - акс
     * ҳолда URL'даги {id} бузиб бошқа рўйхатнинг поғонаси ўчиши мумкин
     * эди (Arbitr-030 4-банд; BR эмас - URL бузиш, бизнес қоида эмас).
     */
    public void removePrice(UUID priceListId, UUID priceItemId) {
        PriceListItem price = itemRepository.findById(priceItemId)
                .filter(p -> p.getPriceList().getId().equals(priceListId))
                .orElseThrow(() -> new NotFoundException(
                        "Поғона бу рўйхатда топилмади: " + priceItemId));
        itemRepository.delete(price);
    }

    // ---- мижоз бириктируви ----

    /** Рўйхат мижозлари - экран учун. */
    @Transactional(readOnly = true)
    public List<PriceListCustomer> customersOf(UUID priceListId) {
        return customerRepository.findByPriceListIdOrderByCreatedAtAsc(priceListId);
    }

    /**
     * Мижозни рўйхатга бириктиради; бошқа рўйхатда бўлса КЎЧИРАДИ
     * (BR-PL-006 семантикаси - мижозга биттагина рўйхат).
     *
     * @throws BusinessRuleException BR-PL-008 - CUSTOMER эмас/нофаол
     */
    public PriceListCustomer assignCustomer(UUID priceListId, UUID customerId) {
        PriceList list = get(priceListId);
        Contact customer = contactService.get(customerId);
        if (customer.getType() != ContactType.CUSTOMER || !customer.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_PL_008,
                    "Фақат CUSTOMER типдаги фаол контакт бириктирилади: "
                    + customer.getDisplayName());
        }
        Optional<PriceListCustomer> existing =
                customerRepository.findByCustomerId(customerId);
        if (existing.isPresent()) {
            existing.get().moveTo(list);
            return existing.get();
        }
        // saveGuarded нақши (Arbitr-030 1-банд): параллел иккита assign
        // иккиси ҳам юқоридаги текширувдан ўтиши мумкин - ҳақиқий кафолат
        // uq_price_list_customer, у 500 эмас BR-PL-006 бўлиб қайтади
        try {
            return customerRepository.saveAndFlush(new PriceListCustomer(list, customerId));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t.getMessage() != null
                        && t.getMessage().contains("uq_price_list_customer")) {
                    throw new BusinessRuleException(BusinessRule.BR_PL_006,
                            "Мижоз аллақачон рўйхатга бириктирилган: "
                            + customer.getDisplayName());
                }
            }
            throw e;
        }
    }

    /**
     * Мижоз бириктирувини олиб ташлайди (рўйхатсиз - default'га қайтади).
     * Scope текширилади: бириктирув айнан шу рўйхатники бўлиши шарт -
     * акс ҳолда URL'даги {id} бузиб бошқа рўйхатнинг бириктируви
     * ўчиши мумкин эди (Arbitr-030 4-банд).
     */
    public void unassignCustomer(UUID priceListId, UUID customerId) {
        PriceListCustomer assignment = customerRepository.findByCustomerId(customerId)
                .filter(a -> a.getPriceList().getId().equals(priceListId))
                .orElseThrow(() -> new NotFoundException(
                        "Бириктирув бу рўйхатда топилмади: " + customerId));
        customerRepository.delete(assignment);
    }

    // ---- ечиш (invoice prefill public API'си) ----

    /**
     * Мижоз/санага мос нархни ечади (docs/modules/price-list.md
     * «Ечиш тартиби»): мижоз рўйхати → default рўйхат; ҳар номзодда
     * фаоллик + валюта + давр текширилади, item поғоналаридан
     * min_quantity <= baseQty бўлган ЭНГ КАТТАСИ олинади. Ҳеч қаерда
     * топилмаса empty - чақирувчи каталог нархга қайтади.
     *
     * @param baseQty миқдор item BASE бирлигида; null/нол - 1 деб олинади
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> resolvePrice(UUID customerId, UUID itemId,
                                             BigDecimal baseQty,
                                             String currencyCode, LocalDate date) {
        BigDecimal qty = baseQty == null || baseQty.signum() <= 0
                ? BigDecimal.ONE : baseQty;
        // Кириш нормализацияси (Arbitr-030 5/6-бандлар): сана берилмаса
        // компания зонасида бугун (аввал appliesTo NPE берарди); валюта
        // коди catalog'дагидек катта ҳарфда солиштирилади (defensive)
        LocalDate at = date != null ? date
                : LocalDate.now(settingsService.zoneId());
        String code = currencyCode == null ? null
                : currencyCode.strip().toUpperCase();
        for (PriceList candidate : candidates(customerId)) {
            if (!candidate.appliesTo(code, at)) {
                continue;
            }
            Optional<BigDecimal> price = tierPrice(candidate.getId(), itemId, qty);
            if (price.isPresent()) {
                return price;
            }
        }
        return Optional.empty();
    }

    /**
     * Номзодлар тартиби: мижоз рўйхати → default (такрорсиз).
     * Иккала номзод валютаси билан JOIN FETCH'ли сўровларда келади
     * (Beruniy-018): аввал бириктирув + lazy рўйхат + EAGER валюта
     * алоҳида SELECT'лар эди - битта lookup 5-6 сўров қиларди,
     * энди кўпи билан 3 (номзодлар 2 + поғоналар 1).
     */
    private List<PriceList> candidates(UUID customerId) {
        List<PriceList> result = new ArrayList<>();
        if (customerId != null) {
            customerRepository.findPriceListForResolve(customerId)
                    .ifPresent(result::add);
        }
        repository.findDefaultForResolve()
                .filter(def -> result.stream()
                        .noneMatch(l -> l.getId().equals(def.getId())))
                .ifPresent(result::add);
        return result;
    }

    /** Item поғоналаридан миқдорга мос энг каттасининг нархи. */
    private Optional<BigDecimal> tierPrice(UUID priceListId, UUID itemId,
                                           BigDecimal qty) {
        List<PriceListItem> tiers = itemRepository
                .findByPriceListIdAndItemIdOrderByMinQuantityAsc(priceListId, itemId);
        PriceListItem best = null;
        for (PriceListItem tier : tiers) {
            if (tier.getMinQuantity().compareTo(qty) <= 0) {
                best = tier; // тартибланган - охирги моси энг каттаси
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.getPrice());
    }

    // ---- ички ёрдамчилар ----

    /** Сарлавҳа валидацияси: ном unique (BR-PL-001), сана тартиби (BR-PL-004). */
    private void validateHeader(PriceListData data, UUID selfId) {
        if (data.name() == null || data.name().isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_PL_001,
                    "Рўйхат номи бўш бўлиши мумкин эмас");
        }
        repository.findByName(data.name().strip())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_PL_001,
                            "Бу ном банд: " + data.name().strip());
                });
        if (data.validFrom() != null && data.validTo() != null
                && data.validFrom().isAfter(data.validTo())) {
            throw new BusinessRuleException(BusinessRule.BR_PL_004,
                    "valid_from valid_to дан кейин: " + data.validFrom()
                    + " > " + data.validTo());
        }
    }

    /** Мавжуд default'ни бўшатади (BR-PL-003 алмашуви). */
    private void releaseDefault(UUID exceptId) {
        repository.findByDefaultListTrue()
                .filter(list -> !list.getId().equals(exceptId))
                .ifPresent(list -> {
                    list.clearDefault();
                    // ux_price_list_default (Beruniy-010 дарси): Hibernate
                    // flush'да INSERT UPDATE'дан олдин кетади - эски default
                    // аввал DB'да бўшатилиши шарт, акс ҳолда янгиси partial
                    // unique'га урилади
                    repository.flush();
                });
    }

    /**
     * saveAndFlush + DB partial unique пойга ҳимояси: параллел иккита
     * default киритилса service текшируви ўтиб кетиши мумкин - ҳақиқий
     * кафолат ux_price_list_default, у 500 эмас BR-PL-003 бўлиб қайтади
     * (saveGuarded паттерни). create ҳам, update ҳам шу ердан ўтади.
     */
    private PriceList saveGuarded(PriceList list) {
        try {
            return repository.saveAndFlush(list);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t.getMessage() != null
                        && t.getMessage().contains("ux_price_list_default")) {
                    throw new BusinessRuleException(BusinessRule.BR_PL_003,
                            "Default рўйхат аллақачон бор");
                }
            }
            throw e;
        }
    }
}
