package com.averpo.erp.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 (RFC 9562) генератор - лойиҳадаги барча PK'лар шундан олинади.
 *
 * <p>UUIDv4 ўрнига v7 танланган, чунки биринчи 48 бит - миллисекунд
 * timestamp: қийматлар вақт бўйича ўсиб боради ва PostgreSQL B-tree
 * индексида тартибли жойлашади (random v4 индексни парчалаб ташлайди).
 *
 * <p>МОНОТОНЛИК КАФОЛАТИ (RFC 9562 §6.2, counter услуби): бир JVM
 * ичида кетма-кет чақириқлар ҚАТЪИЙ ўсувчи id қайтаради - бир
 * миллисекунд ичида rand_a 12-бит counter сифатида ошади. Бу шарт:
 * FIFO cost layer тартиби (received_date, id) айнан id ўсишига
 * таянади - random rand_a'да бир мс ичидаги икки партия тартиби
 * тасодифан ағдарилар эди (2026-07-06 да тестда ушланган flake).
 *
 * <p>Id Hibernate генератори орқали эмас, entity конструкторида қўлда
 * тайинланади - шу туфайли {@code equals}/{@code hashCode} persist'дан
 * олдин ҳам ишлайди. Янгилик аниқлаш {@link BaseEntity}'даги
 * Persistable {@code isNew} байроғи орқали ҳал қилинади.
 *
 * @author Zafar
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Охирги ишлатилган millisекунд - монотонлик учун. */
    private static long lastTimestamp = -1;

    /** Жорий мс ичидаги 12-бит counter (rand_a ўрнида). */
    private static long counter;

    private Uuid7() { }

    /**
     * Янги UUIDv7 қайтаради: 48 бит unix-миллисекунд + 4 бит версия (7)
     * + 12 бит counter (бир мс ичида ошади) + 2 бит variant + 62 бит
     * random. Бир JVM ичида қатъий ўсувчи (Postgres byte-тартибида ҳам).
     */
    public static synchronized UUID next() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) {
            // Соат ортга сурилса ҳам монотонлик бузилмайди
            ts = lastTimestamp;
        }
        if (ts == lastTimestamp) {
            counter++;
            if (counter > 0x0FFFL) {
                // Counter тўлди (бир мс ичида 2048+ id) - кейинги мс'га ўтамиз
                ts++;
                lastTimestamp = ts;
                counter = RANDOM.nextLong() & 0x07FFL;
            }
        } else {
            lastTimestamp = ts;
            // 11 бит random старт - қолган ярим диапазон ўсишга жой
            counter = RANDOM.nextLong() & 0x07FFL;
        }
        long msb = (ts << 16) | 0x7000L | counter;
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
