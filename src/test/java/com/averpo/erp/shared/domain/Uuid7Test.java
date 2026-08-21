package com.averpo.erp.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uuid7 монотонлик кафолати тести: FIFO cost layer тартиби
 * (received_date, id) айнан id ўсишига таянади - бир миллисекунд
 * ичидаги чақириқлар ҳам қатъий ўсувчи бўлиши ШАРТ (RFC 9562 §6.2
 * counter услуби). Random rand_a'да бу бузилар эди -
 * InvoiceServiceTest'нинг FIFO reverse тестида flake сифатида ушланган.
 */
class Uuid7Test {

    @Test
    void next_isStrictlyMonotonic_withinSameMillisecond() {
        // 10 000 та кетма-кет id - кўпи бир мс ичига тушади.
        // UUID toString - байт кетма-кетлигининг hex'и: лексикографик
        // солиштириш Postgres uuid тартибига айнан мос.
        UUID previous = Uuid7.next();
        for (int i = 0; i < 10_000; i++) {
            UUID current = Uuid7.next();
            assertThat(current.toString())
                    .as("id %s аввалгисидан (%s) катта бўлиши шарт", current, previous)
                    .isGreaterThan(previous.toString());
            previous = current;
        }
    }

    @Test
    void next_hasVersion7AndVariant2() {
        UUID id = Uuid7.next();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }
}
