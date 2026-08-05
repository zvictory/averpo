package com.averpo.erp.shared.service;

/**
 * ЦБ авто-курс импорти якунланди (Arbitr-164) - {@link ExchangeRateScheduler}
 * ҳар fetch'дан кейин эълон қилади: муваффақият ҲАМ, хато ҲАМ. Тингловчи
 * audit модулида (SharedAuditListener -> EXCHANGE_RATE_IMPORTED): shared
 * audit'ни import қила олмайди (қоида №6 - audit ўзи BaseEntity орқали
 * shared'га боғлиқ, тескари йўналиш цикл чиқарарди), шунинг учун event
 * орқали (PluginToggledEvent/CompanySettingsChangedEvent нақши).
 *
 * <p>Муваффақият ва хато битта ҳодиса тури билан фарқланади:
 * {@code errorMessage} null бўлса муваффақият (санагичлар тўла), акс ҳолда
 * хато (санагичлар маъносиз). {@link #success}/{@link #failure} factory'лар
 * ва {@link #isFailure()} шу контрактни ўқилувчи қилади - чақирувчи
 * майдонларни қўлда null билан тўлдирмайди.
 *
 * <p>Arbitr-168: {@code checked} (текширилган валюта) {@code changed}
 * (қиймати ўзгарган)дан алоҳида - дам олишда ЦБ курсни ўзгартирмайди,
 * аудит/хабар шуни ҳалол акс эттириши учун.
 *
 * @param checked      муваффақиятда: ЦБ'да курси топилиб текширилган валюта сони (хатода 0)
 * @param changed      муваффақиятда: қиймати аввалгидан ўзгарган валюта сони (хатода 0)
 * @param skipped      муваффақиятда: ЦБ рўйхатида йўқлиги учун ўтказилган сони (хатода 0)
 * @param errorMessage хато сабаби; муваффақиятда null
 *
 * @author Zafar
 */
public record ExchangeRateImportedEvent(int checked, int changed, int skipped,
                                        String errorMessage) {

    /** Муваффақиятли импорт - errorMessage йўқ, санагичлар тўла. */
    public static ExchangeRateImportedEvent success(int checked, int changed, int skipped) {
        return new ExchangeRateImportedEvent(checked, changed, skipped, null);
    }

    /** Импорт хатоси - сабаб билан, санагичлар 0 (маъносиз). */
    public static ExchangeRateImportedEvent failure(String errorMessage) {
        return new ExchangeRateImportedEvent(0, 0, 0, errorMessage);
    }

    /** Хато ҳодисасими - listener details матнини шунга қараб танлайди. */
    public boolean isFailure() {
        return errorMessage != null;
    }
}
