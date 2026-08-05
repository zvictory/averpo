package com.averpo.erp.audit.service;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Аудит журналининг ягона public API'си (docs/modules/audit-log.md):
 * {@link #record} - ёзишнинг бирдан-бир йўли, {@link #page} - экран
 * ўқиши. Update/delete умуман йўқ - append-only инвариант API
 * даражасида таъминланади.
 *
 * <p>Транзакция семантикаси атайлаб REQUIRED (default): ledger
 * ҳодисалари чақирувчи (PostingService) транзакциясига қўшилади -
 * rollback бўлса аудит ёзуви ҳам йўқолади (журнал фақат ҳақиқатан
 * содир бўлган ишни акс эттиради); auth ҳодисаларида ташқи транзакция
 * бўлмаса ўзи очади.
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuditLogService {

    /**
     * Cloudflare Pseudo IPv4 header номи (Arbitr-091): IPv6 уланишларда
     * CF қўшадиган синтетик барқарор IPv4 (240.0.0.0/4). Public - logout
     * handler (SecurityConfig) header'ни request'дан ўзи ўқийди, ном бир
     * жойда турсин. getHeader номга case-insensitive.
     */
    public static final String CF_PSEUDO_IPV4 = "Cf-Pseudo-IPv4";

    /** Cloudflare IP Geolocation давлат коди header номи (Arbitr-091). */
    public static final String CF_IP_COUNTRY = "CF-IPCountry";

    /**
     * Фон жараёнлари (scheduler/bootstrap) actor маркери (Arbitr-164):
     * SecurityContext бўш ёки pool thread'да эски request қолдиғи бўлгани
     * учун {@link #currentUsername()} ноаниқ - контекстсиз жараён «биз
     * тизиммиз»ни ТАСОДИФСИЗ шу константа билан билдиради. Қиймат
     * AuditEvent'нинг «auth йўқ» default'и билан бир хил ({@code system});
     * UI уни i18n «Тизим» кўрсатади. currentUsername() ЎРНИГА ишлатилади.
     */
    public static final String SYSTEM_ACTOR = "system";

    /** Аудит ёзувлари репозиторийси - фақат шу модул ичида. */
    private final AuditEventRepository repository;

    /**
     * Ҳодиса ёзади - барча ёзиш нуқталари (ledger/shared listener'лар,
     * auth listener, UserService) фақат шуни чақиради. Arbitr-062: web
     * контекстда IP (параметр null бўлса) ва User-Agent ҲАММА ҳодисага
     * жорий request'дан автоматик олинади - чақирувчилар ўзгармайди;
     * фон жараёнларда (scheduler/bootstrap) иккиси null қолади.
     * Arbitr-091: CF header'ларидан Pseudo IPv4 ва давлат коди ҳам шу
     * йўлда олинади - login ҳодисаларида ҳам ишлайди, чунки
     * RequestContextFilter security filter chain'дан олдин туради.
     *
     * @param type      ҳодиса тури (null эмас)
     * @param username  ҳаракат эгаси; null/бўш келса {@code system}
     * @param entryId   GL ҳодисаларида JE id'си, бошқаларда null
     * @param docNumber JE рақами снапшоти ёки null
     * @param details   одам ўқийдиган тафсилот ёки null
     * @param ip        клиент манзили (auth event'дан аниқ келса) ёки
     *                  null - жорий web request'дан олинади
     */
    public void record(AuditEventType type, String username, UUID entryId,
                       String docNumber, String details, String ip) {
        record(type, username, entryId, docNumber, details,
                ip != null ? ip : requestIp(), requestHeader("User-Agent"),
                requestHeader(CF_PSEUDO_IPV4), requestHeader(CF_IP_COUNTRY));
    }

    /**
     * Тўлиқ вариант - IP, User-Agent ва CF header қийматлари аниқ қўлда
     * берилади. Request контексти servlet оқимидан ташқарида ишончсиз
     * бўлган нуқталар учун (масалан logout handler - у request'ни қўлида
     * тутади ва header'ларни ўзи ўқийди).
     */
    public void record(AuditEventType type, String username, UUID entryId,
                       String docNumber, String details, String ip, String userAgent,
                       String ipV4, String country) {
        repository.save(new AuditEvent(type, username, entryId, docNumber,
                details, ip, userAgent, ipV4, country));
    }

    /**
     * Жорий web request'даги клиент IP'си ёки null (фон жараён).
     * RemoteIpValve туфайли proxy ортида ҳам ҳақиқий клиент манзили
     * (Arbitr-058 - AuthAuditListener изоҳига қаранг).
     */
    private static String requestIp() {
        return RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getRemoteAddr() : null;
    }

    /**
     * Жорий web request'даги header қиймати ёки null (фон жараён/header
     * йўқ). Dev муҳитда CF header'лар келмайди - null нормал ҳолат.
     */
    private static String requestHeader(String name) {
        return RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getHeader(name) : null;
    }

    /**
     * Экран рўйхати: ихтиёрий филтрлар комбинацияси, доим янгидан
     * эскига (created_at, кейин UUIDv7 id - бир транзакция ичидаги
     * тартиб ҳам тўғри чиқади). Сана оралиғи Instant'да келади -
     * контроллер компания минтақасидаги кун чегараларини UTC'га
     * ўзи ўгиради (қоида №12).
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> page(AuditFilter filter, Pageable pageable) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("eventType"), filter.type()));
            }
            if (filter.username() != null && !filter.username().isBlank()) {
                // Регистрга сезгисиз (U-008): «Admin» ёзуви «admin»
                // филтрида ҳам чиқади - акс ҳолда чала натижа тўлиқдек
                // кўринарди. Ёзиш томони НОРМАЛЛАШТИРИЛМАЙДИ: терилган
                // кўриниш форензика қиймати сифатида сақланади.
                predicates.add(cb.equal(cb.lower(root.get("username")),
                        filter.username().strip().toLowerCase()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    /**
     * Жорий auth контекстидаги username ёки {@code system} - ledger
     * listener ва user-management ёзувлари «ҳаракат эгаси»ни шу орқали
     * аниқлайди (scheduler/bootstrap каби контекстсиз жараёнларда
     * сохта атрибуция қилинмайди, spec username изоҳи).
     */
    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "system" : auth.getName();
    }

    /**
     * Экран филтри: барча майдонлар ихтиёрий (null - филтр йўқ).
     * {@code to} - эксклюзив юқори чегара (контроллер «кун + 1» беради).
     */
    public record AuditFilter(Instant from, Instant to,
                              AuditEventType type, String username) {
    }
}
