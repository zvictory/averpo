package com.averpo.erp.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Учала messages файлининг калит паритети (Arbitr-088 тест кутилмаси).
 *
 * <p>UI уч тилда (uz default / en / ru) - калит фақат биттасига қўшилса
 * қолган тилларда MessageSource хом калит кодини кўрсатади ва бу қўлда
 * очмагунча сезилмайди. Шу тест ҳар қандай калит фарқини прогонда
 * ушлайди. Spring контексти керак эмас - файллар classpath'дан ўқилади.
 *
 * <p>ДИҚҚАТ: {@link Properties#load(Reader)} UTF-8 reader билан
 * чақирилади - файллар хом кириллда сақланади (native2ascii эмас),
 * default ISO-8859-1 оқимида қийматлар бузиларди.
 *
 * @author Zafar
 */
class MessagesParityTest {

    /** Битта messages файлини classpath'дан UTF-8 сифатида ўқийди. */
    private static Properties load(String name) throws IOException {
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(
                        MessagesParityTest.class.getResourceAsStream("/" + name),
                        name + " classpath'да топилмади"),
                StandardCharsets.UTF_8)) {
            Properties props = new Properties();
            props.load(reader);
            return props;
        }
    }

    /** Калит тўпламлари учала тилда айнан бир хил бўлиши шарт. */
    @Test
    void keySets_identicalAcrossThreeLanguages() throws IOException {
        Set<String> uz = load("messages.properties").stringPropertyNames();
        Set<String> en = load("messages_en.properties").stringPropertyNames();
        Set<String> ru = load("messages_ru.properties").stringPropertyNames();

        assertThat(uz).as("uz калитлари бўш эмас").isNotEmpty();
        assertThat(en).as("en тўплами uz билан бир хил").isEqualTo(uz);
        assertThat(ru).as("ru тўплами uz билан бир хил").isEqualTo(uz);
    }

    /**
     * 088 3-банд: vendorInvoice ёрлиқлари аралаш тил/ғализ термин
     * эмас - uz «Таъминотчи фактура(си/рақами)» (QBO Bill no. мазмуни);
     * 4-банд tax.totalHome калити уч тилда мавжуд ва параметрли.
     */
    @Test
    void arbitr088Keys_uzTerminology_andTotalHomePresent() throws IOException {
        Properties uz = load("messages.properties");
        Properties en = load("messages_en.properties");
        Properties ru = load("messages_ru.properties");

        assertThat(uz.getProperty("bill.form.vendorInvoice"))
                .isEqualTo("Таъминотчи фактура рақами");
        assertThat(uz.getProperty("bill.col.vendorInvoice"))
                .isEqualTo("Таъминотчи фактураси");

        // Home жами қатори ёрлиғи: параметр - home валюта коди
        assertThat(uz.getProperty("tax.totalHome")).isEqualTo("Жами ({0})");
        assertThat(en.getProperty("tax.totalHome")).contains("{0}");
        assertThat(ru.getProperty("tax.totalHome")).contains("{0}");
    }
}
