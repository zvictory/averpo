package com.averpo.erp.shared.web;

import com.averpo.erp.shared.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * JTE шаблонлар учун форматлаш ёрдамчиси - ФАҚАТ кўрсатиш учун,
 * ҳисоб-китобга ишлатиш ТАҚИҚ.
 *
 * <p>Яхлитлаш фақат экранга чиқаришда бўлади: сақланадиган ва
 * ҳисобланадиган қийматлар тўлиқ аниқликда қолади (Money scale 4,
 * курс scale 12). Қоидалар манбаи:
 * docs/modules/ui-navigation-display.md, C қисм.
 *
 * <p>Умумий кўриниш: бутун қисм ҳар 3 хонадан NBSP билан бўлинади
 * (сумма сатр ичида иккига синмайди), ўнлик ажратгич - НУҚТА (QBO
 * стандарти, Arbitr-011): {@code 12 600.50}.
 */
public final class Fmt {

    /**
     * Пул кўрсатиш аниқлиги: қатъий 2 хона (HALF_UP) - QBO стандарти
     * (Arbitr-011 қарори). Сақланадиган қийматлар тўлиқ аниқликда
     * (Money scale 4) қолади - бу фақат экран.
     */
    public static final int MONEY_DISPLAY_SCALE = 2;

    /** Миқдор кўрсатишда энг кўп ўнлик хона - ортиғи HALF_UP яхлитланади. */
    private static final int QTY_MAX_SCALE = 4;

    /**
     * Курс >= 1 бўлганда кўрсатишдаги қатъий хоналар сони (Arbitr-135) -
     * пул кўриниши билан бир хил 2 хона: катта курслар суммалардек
     * ўқилади ({@code 12 090.45}).
     */
    private static final int RATE_WHOLE_SCALE = 2;

    /**
     * Курс < 1 бўлганда кўрсатишдаги энг кўп ўнлик хона (Arbitr-135):
     * аввалги ягона «макс 6» тескари курсларда маъноли қисмни кесарди
     * ({@code 0.00008334}да охирги рақамлар йўқоларди) - 8 хона уларни
     * тўлиқ кўрсатади. Сақланадиган scale 12 лигича қолади.
     */
    private static final int RATE_SMALL_MAX_SCALE = 8;

    /** Минг ажратгич - NBSP: сумма сатр охирида иккига бўлиниб қолмайди. */
    private static final char NBSP = 0x00A0;

    /** Instant'ни экранга чиқариш формати: 2026-07-05 21:45. */
    private static final java.time.format.DateTimeFormatter DT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Utility класс - instance яратилмайди. */
    private Fmt() { }

    /**
     * Сонни ортиқча нолларсиз, ХОМ кўринишда қайтаради (null - бўш сатр).
     *
     * <p>Бу кўрсатиш формати ЭМАС - форма prefill'лари учун: қиймат
     * серверга қайтиб parse қилинади, шунинг учун гуруҳлаш ва вергул
     * ишлатилмайди. Экранга чиқаришда {@link #money(Money)},
     * {@link #qty(BigDecimal, String)} ёки {@link #rate(BigDecimal)}
     * ишлатилсин.
     */
    public static String n(BigDecimal value) {
        if (value == null) return "";
        BigDecimal stripped = value.stripTrailingZeros();
        // 1E+6 каби илмий кўринишга тушиб қолмаслик учун toPlainString
        return stripped.toPlainString();
    }

    /**
     * Пул суммаси: минг ажратгич + қатъий {@link #MONEY_DISPLAY_SCALE}
     * хона (HALF_UP): {@code 12 600.50}. null - бўш сатр.
     *
     * <p>Валютасиз «яланғоч» кўриниш фақат валюта контекстдан аниқ
     * жойларда жоиз (масалан, тепасида «Барча суммалар ... да» ёзилган
     * ҳисобот катаклари); ҳужжат суммалари {@link #money(Money)} билан
     * чиқади.
     */
    public static String money(BigDecimal value) {
        if (value == null) return "";
        return group(value.setScale(MONEY_DISPLAY_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Ҳужжат суммаси валюта коди билан: {@code 12 600.50 USD} -
     * «пул ҳеч қачон яланғоч кўрсатилмайди» конвенциясининг асосий
     * ифодаси. Ҳужжат валютасидаги {@code amount} чиқади; home
     * эквиваленти керак бўлса чақирувчи {@code getBaseAmount()}'ни
     * алоҳида кўрсатади. null - бўш сатр.
     */
    public static String money(Money value) {
        if (value == null) return "";
        return money(value.getAmount()) + NBSP + value.getCurrency();
    }

    /**
     * Миқдор: trailing нолсиз, энг кўпи {@code 4} хона (HALF_UP):
     * {@code 10}, {@code 5.5}. null - бўш сатр.
     */
    public static String qty(BigDecimal value) {
        if (value == null) return "";
        return group(strip(value, QTY_MAX_SCALE));
    }

    /**
     * Миқдор бирлиги билан: {@code 10 дона} - «миқдор доим бирлиги
     * билан» конвенцияси. Бирлик String бўлиб келади: shared модул
     * item модулдаги Unit entity'га боғланмаслиги учун (темир қоида
     * №6, модуллараро мурожаат чекловлари). Бирлик null/бўш бўлса
     * фақат миқдор қайтади.
     */
    public static String qty(BigDecimal value, String unit) {
        String qty = qty(value);
        if (qty.isEmpty() || unit == null || unit.isBlank()) return qty;
        return qty + NBSP + unit;
    }

    /**
     * Валюта курси (Arbitr-135, фойдаланувчи қоидаси): қиймат >= 1 -
     * қатъий {@value #RATE_WHOLE_SCALE} хона (HALF_UP, минг ажратгич
     * NBSP): {@code 12 090.45}; қиймат < 1 - trailing нолсиз энг кўпи
     * {@value #RATE_SMALL_MAX_SCALE} хона (HALF_UP): {@code 0.00008334},
     * {@code 0.5}. null - бўш сатр.
     *
     * <p>Тармоқ танлови яхлитлашдан ОЛДИН, шу боис {@code 0.999999995}
     * 8 хонада 1 га яхлитланиб стрипдан кейин {@code 1} кўринади -
     * ҳужжатланган чегара хулқи. Сақланадиган курс scale 12 да
     * ўзгармайди - бу фақат экран.
     *
     * <p>JS кўзгуси: money-input.js даги {@code averpoRateFmt} - иккиси
     * доим айнан бир хил натижа бериши шарт (акс ҳолда prefill/recalc
     * да қиймат «сакраб» кўринади).
     */
    public static String rate(BigDecimal value) {
        if (value == null) return "";
        if (value.compareTo(BigDecimal.ONE) >= 0) {
            return group(value.setScale(RATE_WHOLE_SCALE, RoundingMode.HALF_UP));
        }
        return group(strip(value, RATE_SMALL_MAX_SCALE));
    }

    /**
     * Курс йўналтиришда «кучли» валюталар устуворлиги (spec:
     * ui-navigation-display.md, E қисм X; default, созламасиз) -
     * рўйхатда олдинроғи базис (бирлик) томонга ўтади. Рўйхатда йўқ
     * жуфтликлар учун {@link #orient} қиймати &gt;= 1 чиқадиган
     * йўналишни танлайди.
     */
    private static final java.util.List<String> RATE_BASE_PRIORITY =
            java.util.List.of("USD", "EUR", "RUB", "CNY");

    /**
     * Тескари (1/x) курснинг ҳисоб аниқлиги - сақланадиган курс scale'и
     * (12) билан бир хил: экранга {@link #rate} 6 хонагача қисқартиради,
     * ҳисобда эса маъноли хоналар йўқолмасин.
     */
    private static final int RATE_INVERT_SCALE = 12;

    /**
     * Кўрсатиш учун йўналтирилган курс: {@code 1 base = value quote}.
     *
     * <p>Фақат экран учун - сақланадиган Money/каталог қийматларига
     * алоқаси йўқ. JTE'да {@code Fmt.orient(...)} орқали олинади,
     * қиймат {@link Fmt#rate(BigDecimal)} билан форматланади.
     *
     * @param base  базис (бирлик) валюта коди - «кучли» томон
     * @param quote котировка валюта коди - «1 base» неча quote туриши
     * @param value 1 base'нинг quote'даги қиймати (тескарида 1/x)
     */
    public record OrientedRate(String base, String quote, BigDecimal value) { }

    /**
     * Курсни «кучли валюта базис» қоидасига йўналтиради - ФАҚАТ кўрсатиш
     * учун (фойдаланувчи талаби 2026-07-10, spec E қисм X): «1 USD =
     * 12 600 UZS», ҳеч қачон «1 UZS = 0.00008 USD» эмас - киритиш
     * йўналишидан қатъи назар бир хил кўринади.
     *
     * <p>Кирувчи маъно ҳужжат/каталог конвенцияси: {@code 1 codeA =
     * rate codeB}. Базис танлови: {@link #RATE_BASE_PRIORITY}'даги
     * валюта (иккиси ҳам рўйхатда бўлса - олдинроғи, USD/EUR учун ҳам
     * қиймат &lt; 1 бўлишига қарамай USD базис); ҳеч бири бўлмаса -
     * қиймати &gt;= 1 чиқадиган йўналиш (фолбэк). Тескари йўналишда 1/x
     * scale {@value #RATE_INVERT_SCALE} (HALF_UP) билан ҳисобланади -
     * сақланадиган қийматга тегилмайди.
     *
     * <p>Ҳимоя: null/нол/манфий курс ёки тенг/null кодларда агдармасдан
     * ўз ҳолича қайтади - JTE ичидаги exception бутун саҳифани
     * йиқитади, шунинг учун helper ҳар қандай киришга чидамли.
     */
    public static OrientedRate orient(String codeA, String codeB, BigDecimal rate) {
        boolean flippable = rate != null && rate.signum() > 0
                && codeA != null && codeB != null && !codeA.equals(codeB);
        if (flippable && !baseIsA(codeA, codeB, rate)) {
            return new OrientedRate(codeB, codeA,
                    BigDecimal.ONE.divide(rate, RATE_INVERT_SCALE, RoundingMode.HALF_UP));
        }
        return new OrientedRate(codeA, codeB, rate);
    }

    /**
     * codeA базис бўлиши кераклигини айтади: аввал устуворлик рўйхати,
     * рўйхатда ҳеч бири бўлмаса - қиймати &gt;= 1 йўналиш қолсин
     * (кирувчи rate айнан codeA базисдаги қиймат бўлгани учун).
     */
    private static boolean baseIsA(String codeA, String codeB, BigDecimal rate) {
        int pa = RATE_BASE_PRIORITY.indexOf(codeA);
        int pb = RATE_BASE_PRIORITY.indexOf(codeB);
        if (pa >= 0 && pb >= 0) return pa < pb;
        if (pa >= 0 || pb >= 0) return pa >= 0;
        return rate.compareTo(BigDecimal.ONE) >= 0;
    }

    /**
     * Курс блоки компонентининг (rateBlock.jte, Arbitr-097) КИРИТИШ
     * майдони учун ориентация - {@link #orient} нинг форма-инпут ўрами.
     *
     * <p>САҚЛАНАДИГАН каноник курс {@code 1 doc = canonical home} - у
     * hidden {@code name="exchangeRate"}'да ЎЗГАРМАЙ қолади (server/servis
     * тегилмайди). Кўринадиган input эса кучли-валюта базисида:
     * флип бўлса {@code visible = 1/canonical}, ёрлиқ «1 home = ? doc»;
     * қиймати {@link #rate(BigDecimal)} кўринишида (Arbitr-135) - хом
     * 12-хонали дум ўрнига фойдаланувчи кўрадиган формат. rate-block.js
     * АЙНАН шу қоидани такрорлайди - биринчи кўриниш JS'siz ҳам тўғри
     * бўлсин ва init'даги JS қайта ёзуви қийматни «сакратмасин».
     *
     * @param docCode      ҳужжат валютаси (null/home - блок яширин)
     * @param homeCode     home валюта
     * @param canonicalStr сақланган каноник курс матни (бўш/бузуқ - 1)
     */
    public static RateInput orientInput(String docCode, String homeCode, String canonicalStr) {
        boolean foreign = docCode != null && homeCode != null
                && !docCode.equalsIgnoreCase(homeCode);
        if (!foreign) {
            // Валюта home'га тенг (ёки танланмаган): блок яширин, курс 1
            return new RateInput(homeCode, homeCode, "1", "1", false, false);
        }
        BigDecimal canonical = parseRateOrOne(canonicalStr);
        OrientedRate oriented = orient(docCode, homeCode, canonical);
        boolean flipped = !oriented.base().equalsIgnoreCase(docCode);
        return new RateInput(oriented.base(), oriented.quote(),
                rate(oriented.value()), n(canonical), flipped, true);
    }

    /**
     * Курс блоки компонентининг server-томон ориентация ҳолати.
     *
     * @param base      кўринадиган ёрлиқ базиси (кучли валюта)
     * @param quote     кўринадиган ёрлиқ котировкаси
     * @param visible   кўринадиган input қиймати (ориентацияли, {@link #rate(BigDecimal)} форматида)
     * @param canonical hidden {@code exchangeRate} қиймати (home-per-doc, хом)
     * @param flipped   визуал йўналиш каноникдан тескарими (1/x)
     * @param foreign   курс блоки умуман кўринадими (doc != home)
     */
    public record RateInput(String base, String quote, String visible,
                            String canonical, boolean flipped, boolean foreign) { }

    /** Курс матнини BigDecimal'га - бўш/бузуқ/нолманфий бўлса 1 (ориентация ҳимояси). */
    private static BigDecimal parseRateOrOne(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ONE;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.strip().replace(',', '.'));
            return parsed.signum() > 0 ? parsed : BigDecimal.ONE;
        } catch (NumberFormatException e) {
            return BigDecimal.ONE;
        }
    }

    /**
     * Файл ҳажмини ўқилувчи кўринишда: {@code 512 B}, {@code 3.4 KB},
     * {@code 12.6 MB} (1024 асос, макс 1 ўнлик - trailing нолсиз).
     * Илова рўйхатида {@code size_bytes} шу орқали чиқади
     * (docs/modules/attachments.md). Рақам ва бирлик орасида NBSP -
     * сатр охирида иккига бўлинмайди.
     */
    public static String fileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + Character.toString(NBSP) + "B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes / 1024.0;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return strip(BigDecimal.valueOf(value), 1).toPlainString()
                + NBSP + units[unit];
    }

    /**
     * UTC Instant'ни компания вақт минтақасида кўрсатади (темир қоида №12).
     * Zone контроллердан келади - CompanySettingsService.zoneId().
     */
    public static String dt(java.time.Instant instant, java.time.ZoneId zone) {
        if (instant == null) return "";
        return DT.format(instant.atZone(zone));
    }

    /** maxScale'га HALF_UP яхлитлаб, trailing нолларни олиб ташлайди. */
    private static BigDecimal strip(BigDecimal value, int maxScale) {
        BigDecimal stripped = value.setScale(maxScale, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (stripped.scale() < 0) {
            // stripTrailingZeros 25000000'ни 2.5E+7 га айлантиради - қайтарамиз
            stripped = stripped.setScale(0);
        }
        return stripped;
    }

    /**
     * Бутун қисмни ҳар 3 хонадан NBSP билан бўлади; ўнлик ажратгич
     * НУҚТА - toPlainString'ники сақланади, Locale'га боғлиқ formatter
     * атайлаб ишлатилмайди (Arbitr-011). Каср қисми гуруҳланмайди -
     * бухгалтерия амалиётида фақат бутун қисм гуруҳланади.
     */
    private static String group(BigDecimal value) {
        String plain = value.toPlainString();
        String sign = "";
        if (plain.startsWith("-")) {
            sign = "-";
            plain = plain.substring(1);
        }
        int dot = plain.indexOf('.');
        String intPart = dot < 0 ? plain : plain.substring(0, dot);
        String fraction = dot < 0 ? "" : "." + plain.substring(dot + 1);
        StringBuilder grouped = new StringBuilder(intPart.length() + 8);
        int len = intPart.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                grouped.append(NBSP);
            }
            grouped.append(intPart.charAt(i));
        }
        return sign + grouped + fraction;
    }
}
