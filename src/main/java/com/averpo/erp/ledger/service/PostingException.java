package com.averpo.erp.ledger.service;

import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;

/**
 * Проводка инвариантлари бузилганда отилади (BR-LED-* қоидалари,
 * каталог: {@link BusinessRule}).
 *
 * <p>BusinessRuleException'дан мерос - умумий web қатлам ва бошқа
 * модуллар уни бошқа бизнес хатолар қатори ушлай олади, ledger'га
 * хос catch'лар эса аниқ типни ишлатади.
 */
public class PostingException extends BusinessRuleException {

    /** Қоида ва контекстли хабар билан. */
    public PostingException(BusinessRule rule, String message) {
        super(rule, message);
    }
}
