package com.averpo.erp.shared.web;

import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Форма матнларини парслашнинг ЯГОНА сиёсати (web қатлам учун).
 *
 * <p>Нега бор: сон/UUID parse қоидалари controller'лар бўйлаб такрорланиб
 * ажралиб кетган эди - бирида NBSP қабул қилинарди, бошқасида йўқ; бирида
 * бузуқ UUID формага тушунарли хато берарди, бошқасида хом
 * IllegalArgumentException билан 400 саҳифага учарди. Энди normalize
 * қоидаси (пробел U+0020, NBSP U+00A0 - money-input.js минг ажратгичи,
 * вергул→нуқта) ва хато қолипи битта жойда туради; controller фақат
 * ҳужжатига хос {@link BusinessRule} ва майдон номини беради.
 *
 * <p>Хато хабарига фойдаланувчининг хом киритган қиймати ЁЗИЛМАЙДИ -
 * reflected маълумот гигиенаси (Eldor-003/Beruniy-006 қарорлари билан
 * бир қолип): қиймат керак бўлса log'га ёзилади, экранга эмас.
 */
public final class FormParsers {

    /** Utility класс - instance ясалмайди. */
    private FormParsers() { }

    /**
     * Сонни нормаллаштиради: strip + минг ажратгичлар (оддий пробел ва
     * NBSP - Fmt/money-input.js экранидан кўчириб қўйилган қийматлар
     * учун) олиб ташланади, ўнлик вергул нуқтага айланади.
     */
    private static String normalizeNumber(String text) {
        return text.strip()
                .replace(" ", "")       // оддий пробел (U+0020)
                .replace(" ", "")  // NBSP - минг ажратгич
                .replace(",", ".");
    }

    /**
     * Ихтиёрий сон (сумма, нарх, миқдор, курс): бўш → null (мажбурийликни
     * service ҳал қилади), бузуқ → берилган қоида билан рад.
     *
     * @param text  форма майдонидаги хом матн
     * @param rule  бузуқ форматда отиладиган ҳужжатга хос қоида
     * @param label фойдаланувчига кўрсатиладиган майдон номи
     *              (масалан «Курс», «3-сатр: сумма»)
     * @throws BusinessRuleException rule коди билан - сон эмас бўлса
     */
    public static BigDecimal decimal(String text, BusinessRule rule, String label) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalizeNumber(text));
        } catch (NumberFormatException e) {
            throw new BusinessRuleException(rule,
                    label + " сон форматида киритилиши шарт");
        }
    }

    /**
     * Ихтиёрий сана (ISO {@code йил-ой-кун}): бўш → null (мажбурийликни
     * service ҳал қилади), бузуқ формат → берилган қоида билан рад.
     *
     * <p>Нега шу ерда: {@code @RequestParam LocalDate} бузуқ матнда хом 400
     * (DateTimeParseException) саҳифасига учиради - decimal/uuid каби бу ҳам
     * ягона парс сиёсатидан ўтиб, экранга тушунарли кириллча BR хабари
     * берсин учун (масалан /settings давр ёпилиш санаси). Экран
     * {@code input[type=date]} ISO юборади, шунинг учун {@link LocalDate#parse(CharSequence)}.
     *
     * @param text  форма майдонидаги хом матн
     * @param rule  бузуқ форматда отиладиган ҳужжатга хос қоида
     * @param label фойдаланувчига кўрсатиладиган майдон номи (масалан «Давр ёпилиш санаси»)
     * @throws BusinessRuleException rule коди билан - ISO сана эмас бўлса
     */
    public static LocalDate localDate(String text, BusinessRule rule, String label) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException(rule, label + " нотўғри форматда (йил-ой-кун)");
        }
    }

    /**
     * Ихтиёрий UUID (select/hidden майдонлар): бўш → null, бузуқ →
     * берилган қоида билан рад - tampered request 500/хом хабар бермайди.
     *
     * @throws BusinessRuleException rule коди билан - UUID формати бузуқ бўлса
     */
    public static UUID uuid(String text, BusinessRule rule, String label) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.strip());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(rule, label + ": нотўғри танлов қиймати");
        }
    }

    /**
     * Мажбурий UUID: бўш ҳам, бузуқ ҳам берилган қоида билан рад -
     * майдон танланмаган ҳолат service'га етиб бормасин деган жойларда.
     *
     * @throws BusinessRuleException rule коди билан - бўш ёки бузуқ бўлса
     */
    public static UUID requireUuid(String text, BusinessRule rule, String label) {
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException(rule, label + " танланиши шарт");
        }
        return uuid(text, rule, label);
    }
}
