package com.averpo.erp.audit.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Аудит журнали ёзуви (docs/modules/audit-log.md) - ЎЗГАРМАС
 * (append-only): setter'лар атайлаб йўқ, update/delete API умуман
 * мавжуд эмас - ADMIN ҳам ўчира олмайди, аудит изи маъноси шу.
 *
 * <p>Ҳодиса вақти = {@code createdAt} (BaseEntity, UTC); бир
 * транзакция ичидаги тартиб UUIDv7 id билан аниқланади (курс тарихи
 * нақши). Ёзишнинг ягона йўли -
 * {@link com.averpo.erp.audit.service.AuditLogService#record}.
 */
@Entity
@Table(name = "audit_event")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AuditEvent extends BaseEntity {

    /**
     * Ҳаракат эгаси: LOGIN_FAILURE'да уринилган username (principal
     * йўқ), auth контекстисиз жараёнларда {@code system}. createdBy
     * UUID'идан ташқари сақланади - экран JOIN'сиз ўқийди.
     */
    @Column(nullable = false, length = 50)
    private String username;

    /** Ҳодиса тури - фақат enum қийматлари (STRING + NOT NULL). */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    /**
     * GL ҳодисаларида тегишли JournalEntry id'си (DB'да FK). Entity
     * даражасида UUID - dimension паттерни: audit ledger domain'ига
     * компиляция боғлиқлигисиз қолади (JE ҳеч қачон ўчмагани учун
     * FK хавфсиз).
     */
    @Column(name = "entry_id")
    private UUID entryId;

    /** JE entry_number снапшоти - экранда JOIN'сиз кўрсатиш учун. */
    @Column(name = "doc_number", length = 30)
    private String docNumber;

    /** Одам ўқийдиган тафсилот (JE description, қулф муддати...). */
    @Column(length = 500)
    private String details;

    /**
     * Клиент манзили (IPv6 сиғади) - Arbitr-062 дан бери web контекстдаги
     * ҲАММА ҳодисага ёзилади (RequestContextHolder орқали), фон
     * жараёнларда null.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Cloudflare Pseudo IPv4 (Cf-Pseudo-IPv4 header, changeset 053) - IPv6
     * уланишларда CF берадиган СИНТЕТИК барқарор IPv4 (240.0.0.0/4
     * диапазони), мижознинг реал IPv4'и ЭМАС - ёзувларни таниб олиш ва
     * ўзаро боғлашга хизмат қилади; IPv4 уланишда header келмайди - null.
     * ИШОНЧ ЧЕГАРАСИ: header фақат Cloudflare орқали келганда ишончли
     * (origin тўғридан очиқ бўлса сохталаш мумкин) - шу боис аудит-only
     * майдон, хавфсизлик/бизнес қарорларга асос бўлмайди (Arbitr-091).
     */
    @Column(name = "ip_v4", length = 15)
    private String ipV4;

    /**
     * Мижоз давлат коди (CF-IPCountry header, ISO 3166-1 alpha-2: UZ, RU...)
     * - CF IP Geolocation'дан бепул келади. Ишонч чегараси {@link #ipV4}
     * билан бир хил: аудит-only. Dev/фон жараёнда null.
     */
    @Column(length = 2)
    private String country;

    /**
     * Клиент User-Agent қатори (қайси браузер/дастурдан - Arbitr-062,
     * changeset 050). Web контекстда ёзилади, фонда null; 255 га қирқилади
     * - хом UA қаторлари узунроқ бўлиши мумкин, аудит ёзуви асосий амални
     * йиқитмасин.
     */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /**
     * Тўлиқ ёзув - фақат AuditLogService чақиради. details/username
     * устун узунликларига мослаб қирқилади: аудит ёзуви ҳеч қачон
     * асосий амални DataIntegrityViolation билан йиқитмасин.
     */
    public AuditEvent(AuditEventType eventType, String username, UUID entryId,
                      String docNumber, String details, String ipAddress,
                      String userAgent, String ipV4, String country) {
        this.eventType = eventType;
        this.username = truncate(username == null || username.isBlank()
                ? "system" : username, 50);
        this.entryId = entryId;
        this.docNumber = truncate(docNumber, 30);
        this.details = truncate(details, 500);
        this.ipAddress = truncate(ipAddress, 45);
        this.userAgent = truncate(userAgent, 255);
        this.ipV4 = truncate(ipV4, 15);
        this.country = truncate(country, 2);
    }

    /** Устун чегарасидан узун қийматни жимгина қирқади (null ўтади). */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
