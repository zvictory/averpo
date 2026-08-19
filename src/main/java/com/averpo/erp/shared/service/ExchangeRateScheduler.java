package com.averpo.erp.shared.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * ЦБ курсини автоматик тортиб турувчи фон вазифаси - фойдаланувчи
 * талаби: кунига икки марта fetch, лекин қўлда киритиш
 * имкони сақланади.
 *
 * <p>Вақтлар Тошкент минтақасида қатъий (компания timezone'идан
 * мустақил): ЦБ Ўзбекистон банки, курсни маҳаллий кун бўйича эълон
 * қилади. Хато иловани йиқитмайди - фақат warn log: офлайн муҳит
 * нормал ҳолат, кейинги уриниш ёки қўлда киритиш қоплайди.
 *
 * <p>Ҳар fetch якунида (муваффақият ЁКИ хато) {@link ExchangeRateImportedEvent}
 * эълон қилинади - audit модули уни /audit-log'га ёзади, фойдаланувчи авто
 * янгиланишни кўрсин (DEC-164). {@link #importDaily} АТАЙЛАБ
 * {@code @Transactional} эмас: event importFromCbu ЎЗ транзакциясидан
 * ТАШҚАРИДА (у аллақачон қайтган ёки throw бўлган) эълон қилинади, шунда
 * хато импортнинг rollback'и аудит ёзувини ютмайди (тингловчи ҳам
 * REQUIRES_NEW билан ҳимояланган).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateScheduler {

    /** ЦБ кун ҳисоби учун минтақа - доим Тошкент. */
    private static final ZoneId CBU_ZONE = ZoneId.of("Asia/Tashkent");

    /** Импорт мантиғи - scheduler фақат вақтни беради. */
    private final ExchangeRateService exchangeRateService;

    /** Импорт изини аудитга етказиш учун (SharedAuditListener тинглайди). */
    private final ApplicationEventPublisher events;

    /** Кунига икки марта (Тошкент 10:00 ва 16:00) бугунги курсни тортади. */
    @Scheduled(cron = "0 0 10,16 * * *", zone = "Asia/Tashkent")
    public void importDaily() {
        ExchangeRateService.ImportResult result;
        try {
            result = exchangeRateService.importFromCbu(LocalDate.now(CBU_ZONE));
        } catch (Exception e) {
            // BR-FX-003 (home ЦБ рўйхатида йўқ - pivot имконсиз, DEC-067)
            // ҳам шу ерга тушади - жимгина ўтказилади (қўлда киритиш очиқ)
            log.warn("ЦБ курс импорти амалга ошмади (қўлда киритиш очиқ): {}",
                    e.getMessage());
            events.publishEvent(ExchangeRateImportedEvent.failure(e.getMessage()));
            return;
        }
        log.info("ЦБ курс импорти: {} валюта текширилди, {} ўзгарди, {} ўтказилди",
                result.checked(), result.changed(), result.skipped());
        // Success publish АТАЙЛАБ try/catch'дан ТАШҚАРИДА (DEC-168,
        // Асрорхўжа-017): аудит тингловчиси REQUIRES_NEW commit хатоси
        // (pool тугаши, lock timeout) шу ерда чиқса, юқоридаги catch уни
        // ушлаб «амалга ошмади» ёзиб қўярди - импорт эса T1'да аллақачон
        // commit бўлган. Аудит хатоси муваффақ импортни FAILURE қилмасин
        events.publishEvent(ExchangeRateImportedEvent.success(
                result.checked(), result.changed(), result.skipped()));
    }
}
