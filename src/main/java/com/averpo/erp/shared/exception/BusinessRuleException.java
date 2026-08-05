package com.averpo.erp.shared.exception;

import lombok.Getter;

/**
 * Бизнес қоида бузилиши (ТЕМИР ҚОИДА №13) - service'лардаги ягона
 * хато тури (IllegalArgument/IllegalState тақиқ).
 *
 * <p>Фақат {@link BusinessRule} enum'ини қабул қилади - каталогда йўқ
 * код билан хато отиб бўлмайди. HTTP status қоиданинг ўзидан келади,
 * web қатлам ({@code GlobalExceptionHandler}) шуни ишлатади;
 * контроллерлар форма контекстида ушлаб фойдаланувчига жойида
 * кўрсатади.
 *
 * @author Zafar
 */
@Getter
public class BusinessRuleException extends RuntimeException {

    /** Бузилган қоида - код, status ва default хабар шундан олинади. */
    private final BusinessRule rule;

    /** Контекстсиз ҳолат: хабар қоиданинг default матни бўлади. */
    public BusinessRuleException(BusinessRule rule) {
        super(rule.getDefaultMessage());
        this.rule = rule;
    }

    /** Контекстли хабар билан (масалан қайси счёт/ном айнан). */
    public BusinessRuleException(BusinessRule rule, String message) {
        super(message);
        this.rule = rule;
    }

    /** Қоида коди (BR-LED-006 каби) - экранда хабар олдида кўрсатилади. */
    public String getCode() { return rule.getCode(); }

    /**
     * Экранга чиқариладиган ягона формат: «[BR-КОД] хабар». Global
     * handler ҳам, форма контекстидаги controller catch'лар ҳам шуни
     * ишлатади - фойдаланувчи хатони қаерда кўрса ҳам код доим ёнида
     * бўлади (қўллаб-қувватлашга мурожаатда айнан шу код айтилади).
     */
    public String displayMessage() { return "[" + getCode() + "] " + getMessage(); }

    /** Web қатлам қайтарадиган HTTP status. */
    public int getHttpStatus() { return rule.getHttpStatus(); }
}
