package com.averpo.erp.pricing;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.repo.ContactRepository;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.pricing.domain.PriceList;
import com.averpo.erp.pricing.domain.PriceListCustomer;
import com.averpo.erp.pricing.repo.PriceListCustomerRepository;
import com.averpo.erp.pricing.repo.PriceListRepository;
import com.averpo.erp.pricing.service.PriceListService;
import com.averpo.erp.pricing.service.PriceListService.PriceListData;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CurrencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * saveGuarded таржималарининг ҲАҚИҚИЙ икки-транзакцияли пойга тестлари
 * (Arbitr-030 1/3-бандлар). Битта транзакция/сессия ичида бу пойгани
 * қайта тиклаб бўлмайди: service'нинг олдиндан текшируви DB ҳақиқатини
 * кўради ва auto-flush ҳамма нарсани фош қилади - шунинг учун бу синф
 * АТАЙЛАБ rollback'сиз: 1-транзакция ёзиб туриб commit'ни ушлаб туради,
 * 2-оқимдаги service чақируви текширувдан «тоза» ўтиб DB unique'да
 * блокланади, ғолиб commit қилгач ютқазган DataIntegrityViolation олади
 * ва у 500 эмас, BR кодга таржима қилиниши текширилади. Committed
 * маълумот finally'да қўлда тозаланади (номлар шу синфга хос unique).
 */
@SpringBootTest
@ActiveProfiles("test")
class PriceListRaceTest {

    /**
     * Ғолиб транзакция commit'ни шунча ушлаб туради - ютқазган оқим шу
     * ойнада ўз текширувидан ўтиб unique index блокига етиб бориши
     * керак (бир-икки SELECT - миллисекундлар; катта заҳира билан).
     */
    private static final long HOLD_MS = 800;

    @Autowired PriceListService priceListService;
    @Autowired CurrencyService currencyService;
    @Autowired ContactService contactService;
    @Autowired PriceListRepository listRepository;
    @Autowired PriceListCustomerRepository customerRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void parallelAssign_loserTranslatedToBrPl006() throws Exception {
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "PL race мижози (тест)", null, null, null, null, null,
                null, null, null, null, null));
        PriceList first = priceListService.create(new PriceListData(
                "PL race A (тест)", "UZS", null, null, false, true));
        PriceList second = priceListService.create(new PriceListData(
                "PL race B (тест)", "UZS", null, null, false, true));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> loser = new AtomicReference<>();
        AtomicReference<Future<?>> raced = new AtomicReference<>();
        CountDownLatch loserEntered = new CountDownLatch(1);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // 1-транзакция: текширувдан ўтди, ёзди - commit ҳали ЙЎҚ
                customerRepository.saveAndFlush(new PriceListCustomer(
                        listRepository.getReferenceById(first.getId()),
                        customer.getId()));
                // 2-оқим: findByCustomerId бўш кўради (биз uncommitted),
                // insert'и uq_price_list_customer'да бизни кутиб блокланади
                raced.set(pool.submit(() -> {
                    try {
                        loserEntered.countDown(); // хизмат чақирувига кирдим
                        priceListService.assignCustomer(second.getId(), customer.getId());
                    } catch (Throwable t) {
                        loser.set(t);
                    }
                }));
                // Arbitr-052 (004): ютқазган оқим хизматга КИРГАНИНИ кутамиз -
                // thread startup jitter таймбюджетдан чиқади; кейин hold() фақат
                // pre-check + INSERT unique блокига етиши учун (детерминистикроқ)
                awaitEntered(loserEntered);
                hold();
            }); // commit - ютқазган оқим уйғониб violation олади

            raced.get().get(10, TimeUnit.SECONDS);
            assertThat(loser.get())
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BR-PL-006"));
        } finally {
            pool.shutdownNow();
            cleanup(() -> {
                customerRepository.findByCustomerId(customer.getId())
                        .ifPresent(customerRepository::delete);
                listRepository.deleteById(first.getId());
                listRepository.deleteById(second.getId());
                contactRepository.deleteById(customer.getId());
            });
        }
    }

    @Test
    void parallelDefaultUpdate_loserTranslatedToBrPl003() throws Exception {
        PriceList rival = priceListService.create(new PriceListData(
                "PL race D-B (тест)", "UZS", null, null, false, true));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> loser = new AtomicReference<>();
        AtomicReference<Future<?>> raced = new AtomicReference<>();
        AtomicReference<UUID> winnerId = new AtomicReference<>();
        CountDownLatch loserEntered = new CountDownLatch(1);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // 1-транзакция: янги default ёзди - commit ҳали ЙЎҚ
                Currency uzs = currencyService.require("UZS");
                PriceList winner = listRepository.saveAndFlush(new PriceList(
                        "PL race D-C (тест)", uzs, null, null, true));
                winnerId.set(winner.getId());
                // 2-оқим: releaseDefault committed default кўрмайди (биз
                // uncommitted), update'и ux_price_list_default'да блокланади
                raced.set(pool.submit(() -> {
                    try {
                        loserEntered.countDown(); // хизмат чақирувига кирдим
                        priceListService.update(rival.getId(), new PriceListData(
                                "PL race D-B (тест)", "UZS", null, null, true, true));
                    } catch (Throwable t) {
                        loser.set(t);
                    }
                }));
                // Arbitr-052 (004): ютқазган оқим хизматга КИРГАНИНИ кутамиз
                // (thread startup jitter'сиз), кейин hold() блокка етиши учун
                awaitEntered(loserEntered);
                hold();
            }); // commit - ютқазган оқим violation олади

            raced.get().get(10, TimeUnit.SECONDS);
            assertThat(loser.get())
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BR-PL-003"));
        } finally {
            pool.shutdownNow();
            cleanup(() -> {
                if (winnerId.get() != null) {
                    listRepository.deleteById(winnerId.get());
                }
                listRepository.deleteById(rival.getId());
            });
        }
    }

    /** Ғолиб транзакция ичида ушлаб туриш - interrupt'га чидамли. */
    private static void hold() {
        try {
            Thread.sleep(HOLD_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ютқазган оқим хизмат чақирувига кирганини (countDown) кутади -
     * thread startup jitter'ни ғолибнинг hold() таймбюджетидан чиқаради
     * (Arbitr-052/004: fixed sleep детерминизмсизлигини камайтириш).
     */
    private static void awaitEntered(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Ютқазган оқим хизматга кирмади (5s)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Committed тест маълумотини алоҳида транзакцияда тозалайди. */
    private void cleanup(Runnable work) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> work.run());
    }
}
