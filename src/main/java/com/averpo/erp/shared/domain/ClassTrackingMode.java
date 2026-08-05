package com.averpo.erp.shared.domain;

/**
 * Class tracking режими (docs/modules/class-tracking.md; QBO
 * Preferences ClassTrackingPerTxn/ClassTrackingPerTxnLine кўзгуси).
 *
 * <p>Режим фақат UI'ни бошқаради, service'ни эмас: class ҳар доим
 * САТРДА сақланади, service келган classId'ни режимдан қатъи назар
 * қабул қилади - режим алмашганда эски ҳужжатлар бузилмайди.
 *
 * @author Zafar
 */
public enum ClassTrackingMode {

    /** Кузатув ўчиқ (default) - формаларда class майдонлари кўринмайди. */
    OFF,

    /** Ҳужжат даражасида: формада битта select, controller сатрларга тарқатади. */
    PER_TXN,

    /** Сатр даражасида: ҳар сатрда ўз select'и. */
    PER_LINE;

    /** i18n калити: messages*.properties'даги cls.mode.OFF ва ҳ.к. */
    public String titleKey() {
        return "cls.mode." + name();
    }
}
