package com.averpo.erp.shared.exception;

/**
 * Ёзув топилмади - id/код бўйича қидирув бўш қайтганда.
 *
 * <p>BusinessRuleException'дан мерос: контроллерлардаги умумий
 * catch'лар иккаласини ҳам ушлайди, web қатлам эса буни алоҳида
 * 404 (Not Found) сифатида қайтаради ({@link BusinessRule#NOT_FOUND}).
 *
 * @author Zafar
 */
public class NotFoundException extends BusinessRuleException {

    /** Нима топилмагани ҳақидаги хабар билан. */
    public NotFoundException(String message) {
        super(BusinessRule.NOT_FOUND, message);
    }
}
