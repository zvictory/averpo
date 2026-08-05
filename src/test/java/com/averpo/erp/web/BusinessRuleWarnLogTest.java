package com.averpo.erp.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * БР ради WARN даражада логланиши тести (logging.md «Тестлар» 2-банд,
 * DEC-099): аввал DEBUG эди - default INFO'да умуман кўринмасди;
 * энди {@link GlobalExceptionHandler} БР радини WARN'да код+йўл билан
 * ёзади (error.log триажига тушади). ListAppender'ни handler логгерига
 * улаб текширамиз - файлга боғлиқ эмас.
 */
@SpringBootTest
@ActiveProfiles("test")
class BusinessRuleWarnLogTest {

    /** Ҳақиқий handler (GlobalModelAttributes уланган) - error.jte модели тайёр бўлсин. */
    @Autowired GlobalExceptionHandler handler;

    @Test
    void businessRuleRejection_loggedAtWarn_withCodeAndPath() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invoices");
            request.setRequestURI("/invoices");
            String view = handler.businessRule(
                    new BusinessRuleException(BusinessRule.BR_LED_001, "камида 2 сатр"),
                    new ExtendedModelMap(), request, new MockHttpServletResponse());
            // Хулқ ўзгармаган: error.jte render (логгинг оқимга таъсир қилмайди)
            assertThat(view).isEqualTo("shared/error");
        } finally {
            logger.detachAppender(appender);
        }

        // WARN даражада, код ва йўл билан (DEBUG эмас - default'да кўринади)
        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("BR-LED-001")
                        && e.getFormattedMessage().contains("/invoices"));
    }
}
