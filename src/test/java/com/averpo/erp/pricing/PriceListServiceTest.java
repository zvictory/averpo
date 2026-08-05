package com.averpo.erp.pricing;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.pricing.domain.PriceList;
import com.averpo.erp.pricing.service.PriceListService;
import com.averpo.erp.pricing.service.PriceListService.PriceListData;
import com.averpo.erp.pricing.domain.PriceListItem;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Нарх рўйхатлари: каталог инвариантлари, поғона танлаш ва ечиш
 * тартиби (docs/modules/price-list.md).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PriceListServiceTest {

    /** Тест санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 7);

    @Autowired PriceListService priceListService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;

    /** Тест мижози. */
    private Contact customer;

    /** Тест товари. */
    private Item item;

    /** Chart + мижоз + item тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "PL тест мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        item = itemService.create(ItemType.SERVICE, new ItemData(
                "PL тест хизмати", null, null, null,
                new BigDecimal("15000"), null,
                defaults.income(), null, null, defaults.expense(), null, null));
    }

    /** UZS рўйхат ясагич. */
    private PriceList list(String name, boolean isDefault) {
        return priceListService.create(new PriceListData(
                name, "UZS", null, null, isDefault, true));
    }

    @Test
    void header_validation_andDefaultSwap() {
        PriceList first = list("Улгуржи (тест)", true);
        assertThat(first.isDefaultList()).isTrue();

        // Ном банд - BR-PL-001
        assertThatThrownBy(() -> list("Улгуржи (тест)", false))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-PL-001"));

        // Сана тартиби - BR-PL-004
        assertThatThrownBy(() -> priceListService.create(new PriceListData(
                "Даврли (тест)", "UZS", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 1), false, true)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-PL-004"));

        // Иккинчи default - эскиси автоматик бўшайди (BR-PL-003 алмашуви)
        PriceList second = list("VIP (тест)", true);
        assertThat(second.isDefaultList()).isTrue();
        assertThat(priceListService.get(first.getId()).isDefaultList()).isFalse();
    }

    @Test
    void prices_tierValidation() {
        PriceList list = list("Поғона (тест)", false);
        priceListService.addPrice(list.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("10000"));

        // Дубликат поғона - BR-PL-005
        assertThatThrownBy(() -> priceListService.addPrice(list.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("9500")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-PL-005"));

        // Сонлар - BR-PL-002
        assertThatThrownBy(() -> priceListService.addPrice(list.getId(), item.getId(),
                BigDecimal.ZERO, new BigDecimal("100")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-PL-002"));
        assertThatThrownBy(() -> priceListService.addPrice(list.getId(), item.getId(),
                new BigDecimal("5"), new BigDecimal("-1")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-PL-002"));

        // Нофаол item - BR-PL-007
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        Item inactive = itemService.create(ItemType.SERVICE, new ItemData(
                "PL нофаол (тест)", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        itemService.update(inactive.getId(), new ItemData(
                "PL нофаол (тест)", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null), false);
        assertThatThrownBy(() -> priceListService.addPrice(list.getId(),
                inactive.getId(), BigDecimal.ONE, new BigDecimal("100")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-PL-007"));
    }

    @Test
    void assignCustomer_movesBetweenLists() {
        PriceList first = list("Биринчи (тест)", false);
        PriceList second = list("Иккинчи (тест)", false);

        priceListService.assignCustomer(first.getId(), customer.getId());
        assertThat(priceListService.customersOf(first.getId())).hasSize(1);

        // Бошқа рўйхатга бириктирилса - КЎЧАДИ (BR-PL-006 семантикаси)
        priceListService.assignCustomer(second.getId(), customer.getId());
        assertThat(priceListService.customersOf(first.getId())).isEmpty();
        assertThat(priceListService.customersOf(second.getId())).hasSize(1);

        // VENDOR бириктирилмайди - BR-PL-008
        Contact vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "PL тест етказувчиси", null, null, null, null, null,
                null, null, null, null, null));
        assertThatThrownBy(() -> priceListService.assignCustomer(
                second.getId(), vendor.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-PL-008"));

        // Scope (Arbitr-030): бегона рўйхат id'си билан бириктирув ўчмайди
        assertThatThrownBy(() -> priceListService.unassignCustomer(
                first.getId(), customer.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(priceListService.customersOf(second.getId())).hasSize(1);

        priceListService.unassignCustomer(second.getId(), customer.getId());
        assertThat(priceListService.customersOf(second.getId())).isEmpty();
    }

    @Test
    void resolvePrice_tiersAndFallbacks() {
        // Мижоз рўйхати: 1+ → 10 000, 100+ → 9 000
        PriceList mine = list("Мижозники (тест)", false);
        priceListService.addPrice(mine.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("10000"));
        priceListService.addPrice(mine.getId(), item.getId(),
                new BigDecimal("100"), new BigDecimal("9000"));
        priceListService.assignCustomer(mine.getId(), customer.getId());

        // Default рўйхат: 1+ → 12 000
        PriceList def = list("Default (тест)", true);
        priceListService.addPrice(def.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("12000"));

        // Поғона танлаш: 1 дона → 10 000; 150 дона → 9 000
        assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                BigDecimal.ONE, "UZS", DATE).orElseThrow())
                .isEqualByComparingTo("10000");
        assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                new BigDecimal("150"), "UZS", DATE).orElseThrow())
                .isEqualByComparingTo("9000");

        // Мижозсиз (null) - default рўйхатдан
        assertThat(priceListService.resolvePrice(null, item.getId(),
                BigDecimal.ONE, "UZS", DATE).orElseThrow())
                .isEqualByComparingTo("12000");

        // Сана берилмаса - NPE эмас, компания зонасида бугун деб олинади
        // (Arbitr-030 5-банд; даврсиз рўйхатлар ҳар қандай санага мос)
        assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                BigDecimal.ONE, "UZS", null).orElseThrow())
                .isEqualByComparingTo("10000");

        // Валюта коди кичик ҳарф/бўшлиқ билан келса ҳам натижа бир хил
        // (Arbitr-030 6-банд, defensive normalize)
        assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                BigDecimal.ONE, " uzs ", DATE).orElseThrow())
                .isEqualByComparingTo("10000");

        // Валюта мос эмас - мижозники ўтади, default ҳам UZS - ҳеч бири мос эмас
        assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                BigDecimal.ONE, "USD", DATE)).isEmpty();

        // Мижоз рўйхатида item йўқ - default'дагиси олинади
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        Item other = itemService.create(ItemType.SERVICE, new ItemData(
                "PL бошқа хизмат (тест)", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        priceListService.addPrice(def.getId(), other.getId(),
                BigDecimal.ONE, new BigDecimal("7000"));
        assertThat(priceListService.resolvePrice(customer.getId(), other.getId(),
                BigDecimal.ONE, "UZS", DATE).orElseThrow())
                .isEqualByComparingTo("7000");

        // Ҳеч қаерда йўқ item - empty (чақирувчи каталог нархга қайтади)
        Item absent = itemService.create(ItemType.SERVICE, new ItemData(
                "PL рўйхатсиз (тест)", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        assertThat(priceListService.resolvePrice(customer.getId(), absent.getId(),
                BigDecimal.ONE, "UZS", DATE)).isEmpty();
    }

    @Test
    void resolvePrice_queryCount_bounded() {
        // Beruniy-018: битта lookup'даги SQL сони чегараланади - мижоз
        // рўйхати (валютаси билан JOIN FETCH) 1 + default (валютаси
        // билан) 1 + поғоналар 1 = кўпи билан 3. Аввал бириктирув, lazy
        // рўйхат ва EAGER валюта алоҳида SELECT'ларда келарди.
        PriceList mine = list("Query сони мижозники (тест)", false);
        priceListService.addPrice(mine.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("10000"));
        priceListService.assignCustomer(mine.getId(), customer.getId());
        PriceList def = list("Query сони default (тест)", true);
        priceListService.addPrice(def.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("12000"));
        // Тоза ўлчов: аввалги insert'лар flush қилинади, контекст
        // бўшатилади - resolvePrice ҳамма нарсани базадан ўқийди
        em.flush();
        em.clear();

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                    BigDecimal.ONE, "UZS", DATE).orElseThrow())
                    .isEqualByComparingTo("10000");
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test
    void create_alwaysActive_catalogPattern() {
        // Arbitr-030 7-банд: create'даги active тармоғи ўлик эди - энди
        // каталог қолипи очиқ: янги рўйхат ҲАМИША фаол, data.active
        // фақат update'да ишлайди
        PriceList created = priceListService.create(new PriceListData(
                "Актив қолип (тест)", "UZS", null, null, false, false));
        assertThat(created.isActive()).isTrue();

        priceListService.update(created.getId(), new PriceListData(
                "Актив қолип (тест)", "UZS", null, null, false, false));
        assertThat(priceListService.get(created.getId()).isActive()).isFalse();
    }

    @Test
    void removePrice_scopedToOwnList() {
        // Arbitr-030 4-банд: бегона рўйхат id'си билан поғона ўчмайди
        PriceList mine = list("Scope A (тест)", false);
        PriceList other = list("Scope B (тест)", false);
        PriceListItem tier = priceListService.addPrice(mine.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("5000"));

        assertThatThrownBy(() -> priceListService.removePrice(
                other.getId(), tier.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(priceListService.pricesOf(mine.getId())).hasSize(1);

        // Ўз рўйхати билан - ўчади
        priceListService.removePrice(mine.getId(), tier.getId());
        assertThat(priceListService.pricesOf(mine.getId())).isEmpty();
    }

    @Test
    void resolvePrice_dateWindow_andInactiveSkipped() {
        // Мижоз рўйхати фақат июнда амалда
        PriceList june = priceListService.create(new PriceListData(
                "Июнь (тест)", "UZS", LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30), false, true));
        priceListService.addPrice(june.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("8000"));
        priceListService.assignCustomer(june.getId(), customer.getId());

        PriceList def = list("Default давр (тест)", true);
        priceListService.addPrice(def.getId(), item.getId(),
                BigDecimal.ONE, new BigDecimal("12000"));

        // Июнда - мижозники; июлда давр ташқарисида - default
        assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                BigDecimal.ONE, "UZS", LocalDate.of(2026, 6, 15)).orElseThrow())
                .isEqualByComparingTo("8000");
        assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                BigDecimal.ONE, "UZS", DATE).orElseThrow())
                .isEqualByComparingTo("12000");

        // Default нофаол қилинса - у ҳам ўтказиб юборилади
        priceListService.update(def.getId(), new PriceListData(
                "Default давр (тест)", "UZS", null, null, true, false));
        assertThat(priceListService.resolvePrice(customer.getId(), item.getId(),
                BigDecimal.ONE, "UZS", DATE)).isEmpty();
    }
}
