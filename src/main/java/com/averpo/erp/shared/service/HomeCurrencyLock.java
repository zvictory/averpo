package com.averpo.erp.shared.service;

/**
 * Home currency ўзгартиришни қулфлаш порти.
 *
 * <p>Боғлиқлик йўналиши ledger → shared бўлгани учун shared ledger'дан
 * «POSTED проводка борми?» деб сўрай олмайди. Шу интерфейс тескари
 * боғлиқликни ечади: ledger буни bean сифатида имплементация қилади,
 * {@code CompanySettingsService} эса мавжуд bean'ларни сўраб чиқади.
 *
 * @author Zafar
 */
public interface HomeCurrencyLock {

    /** {@code true} - home currency энди ўзгартирилмайди. */
    boolean locked();
}
