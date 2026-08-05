package com.averpo.erp.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

/**
 * Компания созламалари - тизимда битта қатор (QBO Company Settings услуби).
 *
 * <p>Home currency шу ерда: ledger айнан шу валютада балансланади.
 * Биринчи POSTED проводкадан кейин валютани ўзгартириш тақиқ - акс
 * ҳолда мавжуд baseAmount'лар маъносини йўқотади. Қулф текшируви
 * {@code HomeCurrencyLock} порти орқали ledger'да туради, чунки shared
 * модули ledger'га боғлана олмайди.
 *
 * <p>Timezone ҳам шу ерда (темир қоида №12): базада вақтлар UTC,
 * экранга чиқаришда шу минтақага ўгирилади.
 *
 * @author Zafar
 */
@Entity
@Table(name = "company_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanySettings extends BaseEntity {

    /** Янги тизимнинг default home валютаси. */
    public static final String DEFAULT_HOME_CURRENCY = "UZS";

    /** Янги тизимнинг default вақт минтақаси. */
    public static final String DEFAULT_TIMEZONE = "Asia/Tashkent";

    /** Компания номи - ҳужжат ва ҳисоботлар сарлавҳасида. */
    @Column(nullable = false)
    private String name;

    /**
     * Home валюта - Currency каталогига ManyToOne. Ledger шу валютада
     * балансланади. EAGER: созламалар битта қатор, шаблонда JOIN'сиз
     * lazy хатоси бўлмаслиги учун.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "home_currency_id", nullable = false)
    private Currency homeCurrency;

    /** IANA timezone id (Asia/Tashkent) - вақтларни кўрсатиш минтақаси. */
    @Column(nullable = false, length = 50)
    private String timezone;

    /**
     * Inventory баҳолаш методи (AVCO/FIFO) - компания даражасида,
     * биринчи омбор ҳаракатидан кейин қулфланади (5-босқич порти).
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "inventory_valuation", nullable = false, length = 10)
    private InventoryValuationMethod inventoryValuation = InventoryValuationMethod.AVCO;

    /**
     * Давр ёпилиш санаси (QBO closing date): шу санага тенг ёки олдинги
     * санага янги GL ҳаракати тақиқ - BR-LED-020, текширув
     * PostingService'да. NULL - қулф йўқ.
     */
    @Column(name = "closing_date")
    private java.time.LocalDate closingDate;

    /**
     * Молия йили бошланиш ойи (1..12, default 1 - январь) - QBO «First
     * month of fiscal year». Balance Sheet'даги Тақсимланмаган фойда /
     * Соф фойда бўлиниши шу ойга қараб ҳисобланади. Фақат ҳисобот
     * кўринишига таъсир қилади - қулф йўқ (BR-SET-004 фақат оралиқ).
     */
    @Column(name = "fiscal_year_start_month", nullable = false)
    private int fiscalYearStartMonth = 1;

    /**
     * Class tracking режими (docs/modules/class-tracking.md, QBO
     * Preferences ClassTracking* кўзгуси). Қулфланмайди - исталган
     * пайт алмаштирилади, эски ҳужжатлар ўзгармайди (режим фақат
     * UI'ни бошқаради, схема ягона - class ҳар доим сатрда).
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "track_classes", nullable = false, length = 10)
    private ClassTrackingMode trackClasses = ClassTrackingMode.OFF;

    /**
     * Даромад солиғи ставкаси (фоиз) - PayrollRun ходимдан ушланмани
     * ҳисоблайди. ADMIN /settings'да таҳрирлайди (0..100 - BR-SET-005).
     * Ҳужжатда ҳисобланган СУММА snapshot сақланади, кейин ставка ўзгарса
     * эски POSTED run ўзгармайди (payroll.md «Ставкалар CompanySettings»).
     */
    @Column(name = "income_tax_rate", nullable = false, precision = 9, scale = 4)
    private java.math.BigDecimal incomeTaxRate = new java.math.BigDecimal("12");

    /**
     * Жамғариб бориладиган пенсия бадали ставкаси (фоиз, ходимдан
     * ушланма). Default 0.1.
     */
    @Column(name = "pension_rate", nullable = false, precision = 9, scale = 4)
    private java.math.BigDecimal pensionRate = new java.math.BigDecimal("0.1");

    /**
     * Ижтимоий солиқ ставкаси (фоиз, иш берувчи устига - ходим net'ига
     * таъсир қилмайди). Default 12.
     */
    @Column(name = "social_tax_rate", nullable = false, precision = 9, scale = 4)
    private java.math.BigDecimal socialTaxRate = new java.math.BigDecimal("12");

    // --- Компания реквизит майдонлари (Arbitr-112, changeset 054) - соф
    // кўрсатиш маълумоти, GL/posting'га таъсирсиз; чоп сарлавҳаси ва
    // document-print (29) ЎҚИЙДИ. Ҳаммаси nullable/ихтиёрий ---

    /** Тўлиқ юридик ном (чоп сарлавҳаси). null - кўрсатилмаган. */
    @Column(name = "legal_name", length = 255)
    private String legalName;

    /** Манзил (кўп қатор матн). */
    @Column(length = 1000)
    private String address;

    /** Телефон. */
    @Column(length = 50)
    private String phone;

    /** Email - тўлдирилса формат текширилади (BR-SET-007). */
    @Column(length = 255)
    private String email;

    /** Веб-сайт. */
    @Column(length = 255)
    private String website;

    /** СТИР/ИНН (матн - рақамли ID эмас, форматлаш сақлансин). */
    @Column(name = "tax_id", length = 50)
    private String taxId;

    /** Банк номи (реквизит). */
    @Column(name = "bank_name", length = 255)
    private String bankName;

    /** Ҳисоб рақами. */
    @Column(name = "bank_account", length = 50)
    private String bankAccount;

    /** Банк МФО коди. */
    @Column(name = "bank_mfo", length = 20)
    private String bankMfo;

    /** Директор ФИШ (ҳужжат имзоси). */
    @Column(name = "director_name", length = 255)
    private String directorName;

    /** Директор лавозими. */
    @Column(name = "director_position", length = 255)
    private String directorPosition;

    /**
     * Компания логоси - {@code attachment} id'сига soft ref (DB FK,
     * ON DELETE SET NULL; 101 аватар нақши - JPA'да оддий UUID,
     * модуллараро entity боғланиш йўқ, темир қоида №6). null - лого йўқ.
     * Бу логотип ҲУЖЖАТ/ЧОП сарлавҳаси учун (document-print 29 ўқийди).
     */
    @Column(name = "logo_attachment_id")
    private java.util.UUID logoAttachmentId;

    /**
     * Бренд логоси (Arbitr-112 рефайнмент, changeset 066) - топбар
     * WHITE-LABEL учун. {@link #logoAttachmentId}'дан ФАРҚли: у ҳужжат/чоп
     * сарлавҳаси учун, бу UI топбар учун (login'дан кейин ҳар роль кўради,
     * «AVERPO» ўрнига компания бренди). Soft ref (ON DELETE SET NULL -
     * лого ўчса топбар синмайди, fallback «AVERPO»). null - созланмаган.
     */
    @Column(name = "brand_logo_attachment_id")
    private java.util.UUID brandLogoAttachmentId;

    /**
     * Онбординг флаги (Arbitr-056): компания созламалари камида бир марта
     * сақланганми. Явный флаг керак - name'ни default 'Компания'га
     * солиштириш мўрт (ном ростдан шундай бўлиши мумкин, шунда фойдаланувчи
     * ҳар киришда setup'га тушарди). Биринчи муваффақиятли update()'да true
     * бўлади; login success handler ADMIN'ни false бўлса /settings?setup=1
     * га йўналтиради.
     */
    @Column(name = "setup_done", nullable = false)
    private boolean setupDone = false;

    /** Жадвалда биттагина қатор бўлишини DB unique constraint кафолатлайди. */
    @Column(name = "singleton_guard", nullable = false)
    private boolean singletonGuard = true;

    /** Биринчи ишга туширишда default қийматлар билан яратилади. */
    public CompanySettings(String name, Currency homeCurrency, String timezone) {
        this.name = name;
        this.homeCurrency = homeCurrency;
        this.timezone = timezone;
    }

    /** Экранга чиқариш учун тайёр ZoneId. */
    public ZoneId zoneId() { return ZoneId.of(timezone); }

    /** Home валютанинг ISO коди - Money'лар шу код билан солиштирилади. */
    public String homeCurrencyCode() { return homeCurrency.getCode(); }

    /** Компания номини янгилайди. */
    public void rename(String name) { this.name = name; }

    /**
     * Компания реквизитларини янгилайди (Arbitr-112) - валидация
     * (email BR-SET-007) CompanySettingsService'да. Реквизитлар соф
     * кўрсатиш маълумоти: GL/posting'га таъсир қилмайди, шунинг учун
     * қулф йўқ (исталган пайт таҳрирланади). `name` шу метод ичида
     * эмас - у мавжуд update() оқимида (settings асосий формаси).
     */
    public void updateCompanyInfo(String legalName, String address, String phone,
                                  String email, String website, String taxId,
                                  String bankName, String bankAccount, String bankMfo,
                                  String directorName, String directorPosition) {
        this.legalName = legalName;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.website = website;
        this.taxId = taxId;
        this.bankName = bankName;
        this.bankAccount = bankAccount;
        this.bankMfo = bankMfo;
        this.directorName = directorName;
        this.directorPosition = directorPosition;
    }

    /**
     * Лого attachment id'сини ўрнатади (null - олиб ташлаш). Эски логони
     * (agar бўлса) ўчириш чақирувчида (AttachmentService.delete), бу
     * метод фақат FK'ни алмаштиради - 101 аватар нақшининг айнан ўзи.
     */
    public void setLogoAttachmentId(java.util.UUID logoAttachmentId) {
        this.logoAttachmentId = logoAttachmentId;
    }

    /**
     * Бренд логоси (топбар) attachment id'сини ўрнатади (null - олиб
     * ташлаш; Arbitr-112 рефайнмент). {@link #setLogoAttachmentId} нақши -
     * эски файлни ўчириш чақирувчида (CompanyInfoService), бу метод фақат FK.
     */
    public void setBrandLogoAttachmentId(java.util.UUID brandLogoAttachmentId) {
        this.brandLogoAttachmentId = brandLogoAttachmentId;
    }

    /** Фақат CompanySettingsService чақиради - қулф текшируви ўша ерда. */
    public void changeHomeCurrency(Currency currency) { this.homeCurrency = currency; }

    /** Вақт минтақасини янгилайди (валидация service'да). */
    public void changeTimezone(String timezone) { this.timezone = timezone; }

    /** Фақат CompanySettingsService чақиради - қулф текшируви ўша ерда. */
    public void changeInventoryValuation(InventoryValuationMethod method) {
        this.inventoryValuation = method;
    }

    /** Ёпилиш санасини янгилайди; null - қулфни олиб ташлаш (ADMIN ихтиёри). */
    public void changeClosingDate(java.time.LocalDate closingDate) {
        this.closingDate = closingDate;
    }

    /** Молия йили бошланиш ойини янгилайди (1..12 текшируви service'да - BR-SET-004). */
    public void changeFiscalYearStartMonth(int month) {
        this.fiscalYearStartMonth = month;
    }

    /**
     * Онбординг тугаганини белгилайди (Arbitr-056) - CompanySettingsService
     * муваффақиятли сақлагач чақиради. Ортга қайтариш йўқ: бир марта
     * созлангач фойдаланувчи қайта setup'га йўналтирилмайди.
     */
    public void markSetupDone() {
        this.setupDone = true;
    }

    /** Class tracking режимини алмаштиради (қулф йўқ - QBO услуби). */
    public void changeTrackClasses(ClassTrackingMode mode) {
        this.trackClasses = mode;
    }

    /**
     * Payroll ставкаларини янгилайди (фоиз). 0..100 текшируви
     * CompanySettingsService'да - BR-SET-005 (қулф йўқ: ставка ўзгарса
     * эски POSTED run'лар snapshot сумма туфайли ўзгармайди).
     */
    public void changePayrollRates(java.math.BigDecimal incomeTaxRate,
                                   java.math.BigDecimal pensionRate,
                                   java.math.BigDecimal socialTaxRate) {
        this.incomeTaxRate = incomeTaxRate;
        this.pensionRate = pensionRate;
        this.socialTaxRate = socialTaxRate;
    }

    /**
     * Берилган сана тегишли бўлган молия йилининг биринчи куни.
     * Масалан бошланиш ойи 7 (июль) бўлса: 2026-06-30 → 2025-07-01,
     * 2026-07-02 → 2026-07-01. Balance Sheet RE/NI бўлиниши шунга қараб.
     */
    public java.time.LocalDate fiscalYearStart(java.time.LocalDate onDate) {
        int year = onDate.getMonthValue() >= fiscalYearStartMonth
                ? onDate.getYear() : onDate.getYear() - 1;
        return java.time.LocalDate.of(year, fiscalYearStartMonth, 1);
    }
}
