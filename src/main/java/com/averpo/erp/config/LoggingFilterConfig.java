package com.averpo.erp.config;

import com.averpo.erp.web.RequestLogFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link RequestLogFilter}'ни Spring Security занжиридан КЕЙИН (ичкарида)
 * рўйхатга олади (docs/modules/logging.md, DEC-099 1-тузоқ).
 *
 * <p>Тартиб = {@link #FILTER_ORDER} (-99): Spring Security'нинг
 * {@code FilterChainProxy}'си servlet занжирида -100 (SecurityProperties.
 * DEFAULT_FILTER_ORDER) да туради; ундан КАТТАРОҚ order = занжир ичида
 * (нест) - filter ишлаганда {@code SecurityContextHolderFilter}
 * контекстни аллақачон юклаган, {@code user} MDC'га тушади. Кичикроқ
 * order турса контекст ҳали бўш бўларди. Константа айнан ёзилган:
 * SecurityProperties autoconfigure модулида (compile classpath'да эмас,
 * runtime), шунга рақам билан боғланиб, изоҳда манбаси кўрсатилди.
 *
 * <p>@Component ЭМАС - акс ҳолда Spring Boot уни default (энг паст)
 * тартибда иккинчи марта авто-рўйхатга оларди. Ягона рўйхат = шу bean.
 */
@Configuration
public class LoggingFilterConfig {

    /** Security FilterChainProxy (-100) дан бир поғона ичкарида. */
    private static final int FILTER_ORDER = -99;

    /** Сўров изи filter'и - security'дан кейин, барча йўлларга. */
    @Bean
    public FilterRegistrationBean<RequestLogFilter> requestLogFilter() {
        FilterRegistrationBean<RequestLogFilter> registration =
                new FilterRegistrationBean<>(new RequestLogFilter());
        registration.setOrder(FILTER_ORDER);
        registration.addUrlPatterns("/*");
        registration.setName("requestLogFilter");
        return registration;
    }
}
