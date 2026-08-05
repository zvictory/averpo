package com.averpo.erp.plugins.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Telegram long polling фон вазифаси (docs/modules/user-profile.md
 * 3-бўлим): ботга келган {@code /start <код>} хабарларини олиб
 * {@link TelegramService}га узатади. Webhook ЭМАС (спец қарори:
 * webhook оммавий HTTPS endpoint талаб қилади ва dev/локалда
 * ишламайди; polling ҳар муҳитда бир хил).
 *
 * <p><b>Нега {@code @Scheduled} эмас, ўз thread'и</b> (карта тузоқ 2):
 * long polling 25 сониягача thread'ни ушлайди, Boot'нинг default
 * scheduler pool'и эса БИТТА thread - ЦБ курс крони (10:00/16:00,
 * ExchangeRateScheduler) шу poller ортида навбат кутиб қоларди.
 * {@link SmartLifecycle} + битта daemon thread: контекст билан бирга
 * тоза старт/стоп, битта instance (тузоқ 1 - иккита poller бир вақтда
 * getUpdates қилса Telegram хабарни иккига бўлиб берарди).
 *
 * <p><b>Нега {@code @Profile("dev")}</b> (Arbitr-138): polling ФАҚАТ
 * локал дев'да - Telegram localhost'га ета олмайди, шунга дев webhook
 * қура олмайди. Профилсиз prod webhook ишлатади (TelegramWebhookRegistrar),
 * тест профилда эса фон thread'и умуман керак эмас (у ҳар айланишда
 * базани ўқиб тестларни ифлослар эди). Ишлов мантиқи {@link TelegramService}да -
 * у тўлиқ тестланади.
 *
 * <p>Плагин ўчиқ ёки бот созланмаган бўлса - Telegram'га УМУМАН
 * чиқилмайди (pollTarget бўш → уйқу): ўчиқ плагин трафик ҳосил
 * қилмайди (plugins.md гейти).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class TelegramPoller implements SmartLifecycle {

    /** Плагин ўчиқ/бот созланмаган ҳолатда навбатдаги текширувгача уйқу. */
    private static final long IDLE_SLEEP_MS = 10_000;

    /** Хатодан кейинги биринчи кутиш (карта тузоқ 5: backoff). */
    private static final long BACKOFF_START_MS = 5_000;

    /** Backoff юқори чегараси - узилиш узоқ давом этса ҳам босим ортмасин. */
    private static final long BACKOFF_MAX_MS = 60_000;

    /** Ишлов мантиғи ва созлама ҳолати (транзакциялар ўша ерда). */
    private final TelegramService telegramService;

    /** Bot API порти - тармоқ чақируви АЙНАН шу ерда (транзакциядан ташқарида). */
    private final TelegramBotClient client;

    /** Loop давом этсинми - stop() уни ўчиради (volatile: бошқа thread ўқийди). */
    private volatile boolean running;

    /** Битта daemon thread (тузоқ 1) - контекст ёпилганда тўхтайди. */
    private ExecutorService executor;

    /** {@inheritDoc} */
    @Override
    public void start() {
        running = true;
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "telegram-poller");
            // Daemon: кутилмаган ҳолатда ҳам JVM'нинг ёпилишига тўсқинлик қилмасин
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::loop);
        log.info("Telegram poller ишга тушди");
    }

    /**
     * Тоза тўхташ (тузоқ 2): байроқ ўчади, thread узилади (шу пайтда
     * getUpdates'да блокланган бўлса - HttpClient interrupt'да
     * қайтади) ва 5 сониягача кутилади.
     */
    @Override
    public void stop() {
        running = false;
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Telegram poller 5 сонияда тўхтамади - контекст барибир ёпилади");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Асосий айланиш: гейт → getUpdates → ҳар хабарни ишлаш → курсорни
     * силжитиш. Ҳеч бир хато loop'ни тўхтатмайди (карта тузоқ 5) -
     * тармоқ узилиши/429/5xx да backoff билан давом этади.
     */
    private void loop() {
        long backoff = BACKOFF_START_MS;
        // Prod'дан дев'га ўтилса (ёки prod webhook қолиб кетган бўлса)
        // Telegram'да webhook турса getUpdates 409 берарди - иккови
        // ўзаро истисно. Биринчи муваффақиятли токенда БИР МАРТА
        // webhook'ни ўчириб polling'га йўл очамиз (тузоқ 2).
        boolean webhookCleared = false;
        while (running) {
            try {
                Optional<TelegramService.PollTarget> target = telegramService.pollTarget();
                if (target.isEmpty()) {
                    // Плагин ўчиқ ёки бот созланмаган - тармоққа чиқмаймиз
                    if (!sleep(IDLE_SLEEP_MS)) {
                        return;
                    }
                    continue;
                }
                String token = target.get().token();
                if (!webhookCleared) {
                    client.deleteWebhook(token);
                    webhookCleared = true;
                }
                Optional<List<TelegramBotClient.Update>> updates = client.getUpdates(
                        token, target.get().offset(), TelegramService.POLL_TIMEOUT_SECONDS);
                if (updates.isEmpty()) {
                    // Тармоқ/API хатоси (клиент WARN ёзган - токенсиз): backoff
                    if (!sleep(backoff)) {
                        return;
                    }
                    backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
                    continue;
                }
                backoff = BACKOFF_START_MS; // соғлом жавоб - backoff нолланади
                process(token, target.get().offset(), updates.get());
            } catch (Exception e) {
                // Кутилмаган хато (масалан база узилиши) - loop ЙИҚИЛМАЙДИ.
                // ДИҚҚАТ: getMessage() ёзилмайди - хабарда токенли URL
                // бўлиши мумкин (PengradTelegramClient изоҳи)
                log.warn("Telegram poller айланишида хато: {}", e.getClass().getSimpleName());
                if (!sleep(backoff)) {
                    return;
                }
                backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
            }
        }
    }

    /**
     * Партияни ишлайди ва курсорни силжитади. Битта хабарнинг хатоси
     * (масалан жавоб юборилмади) қолганларини ҳам, offset'ни ҳам
     * тўхтатмаслиги учун ҳар бири алоҳида ўралади - акс ҳолда ўша
     * хабар ҳар айланишда қайта келиб loop'ни қотириб қўярди.
     */
    private void process(String token, long offset, List<TelegramBotClient.Update> updates) {
        long maxUpdateId = offset - 1;
        for (TelegramBotClient.Update update : updates) {
            try {
                telegramService.handleUpdate(token, update);
            } catch (Exception e) {
                log.warn("Telegram хабарини ишлаб бўлмади (update {}): {}",
                        update.updateId(), e.getClass().getSimpleName());
            }
            maxUpdateId = Math.max(maxUpdateId, update.updateId());
        }
        if (!updates.isEmpty()) {
            telegramService.advanceOffset(maxUpdateId + 1);
        }
    }

    /**
     * Узилиши мумкин бўлган уйқу.
     *
     * @return {@code false} - thread узилди, loop дарҳол тугаши керак
     */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
