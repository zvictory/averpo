package com.averpo.erp.item.config;

import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Янги (fresh) ўрнатишда стандарт UOM гуруҳларини автоматик ўрнатади
 * (DEC-147) - {@link com.averpo.erp.ledger.config.DefaultChartInitializer}
 * кўзгуси. Фойдаланувчи дона/оғирлик/узунлик... каби кундалик ўлчов
 * гуруҳларини қўлда киритиб ўтирмасин - default chart каби тайёр келади.
 * {@code DefaultChartInstaller} каби порт орқали ({@link UnitService}
 * имплементацияси).
 *
 * <p><b>Гейт (нега икки шарт):</b> чарт «account бўш» билан fresh install'ни
 * аниқлайди (мавжуд DB'да чарт бор). Бирликлар эса ҲАР базада seed
 * (changeset 008), «unit бўш» сигнали йўқ; «гуруҳ бўш» эса мавжуд dev/prod
 * DB'да ҳам рост (feature янги) - шунга ёлғиз бўлмайди. Гейт: <b>гуруҳ бўш
 * ВА setup тугамаган</b> ({@code !setupDone}). Мавжуд ўрнатилган DB
 * (setupDone=true) АВТОМАТИК тегилмайди (карта талаби: dev/prod'га авто
 * эмас); fresh install (setupDone=false)да гуруҳлар ўрнатилади.
 * {@code setupDone} company_settings'да барқарор - ApplicationRunner
 * тартибидан мустақил (account-emptiness чарт-init'дан кейин йўқолар эди).
 *
 * <p><b>{@code @Profile("!test")}:</b> DefaultChartInitializer каби тест
 * профилида АТАЙЛАБ ишламайди - ApplicationRunner тест транзакциясидан
 * ташқари, ҳар контекст кўтарилишида умумий тест базасини ифлослар эди
 * (seed бирликларни гуруҳлаб, «дона гуруҳсиз» инвариантига таянган
 * тестларни синдирарди). Логика {@code DefaultUnitsInitializerTest}'да
 * bean қўлда қуриб текширилади.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DefaultUnitsInitializer implements ApplicationRunner {

    /** Бирлик каталоги public API'си - гуруҳ бўшлиги + idempotent ўрнатиш. */
    private final UnitService unitService;

    /** Fresh install гарови - setup тугамаган база «янги» ҳисобланади. */
    private final CompanySettingsService settingsService;

    /** Fresh install бўлса стандарт UOM гуруҳларини ўрнатади; акс ҳолда ўтади. */
    @Override
    public void run(ApplicationArguments args) {
        if (!unitService.groups().isEmpty()) {
            return; // гуруҳлар бор - тегилмайди (idempotent, такрор старт)
        }
        if (settingsService.isSetupDone()) {
            return; // мавжуд ўрнатилган DB - авто тегмайди (карта #3)
        }
        unitService.installDefaultUnits();
        log.info("Янги ўрнатиш: стандарт UOM гуруҳлари автоматик ўрнатилди");
    }
}
