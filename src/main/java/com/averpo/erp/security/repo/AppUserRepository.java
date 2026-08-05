package com.averpo.erp.security.repo;

import com.averpo.erp.security.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Фойдаланувчилар репозиторийси - фақат security модули ичида.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Login пайтида қидириш. */
    Optional<AppUser> findByUsername(String username);

    /** BR-USR-002: username бандлигини текшириш (DB unique ҳам бор). */
    boolean existsByUsername(String username);

    /** /users рўйхати - username тартибида (spec). */
    java.util.List<AppUser> findAllByOrderByUsernameAsc();

    /**
     * BR-USR-007 гарови: шу user'дан БОШҚА фаол admin'лар сони -
     * нолга тушса охирги фаол admin demote/deactivate қилинмайди.
     */
    long countByRoleAndActiveTrueAndIdNot(
            com.averpo.erp.security.domain.UserRole role, UUID id);

    /**
     * BR-USR-015 гарови: ходим контакти шу user'дан БОШҚА ФАОЛ user'да
     * банд эмаслигини текшириш (1:1 ихтиёрий улаш - DB partial unique
     * билан бир мантиқ). Ходим уланмаган (null) ҳолда чақирилмайди.
     */
    boolean existsByEmployeeContactIdAndActiveTrueAndIdNot(UUID employeeContactId, UUID id);

    /**
     * Telegram улаш коди бўйича топади (Arbitr-103): poller ботдан келган
     * {@code /start <код>}ни шу орқали эгасига боғлайди. Код DB'да
     * partial unique (changeset 059) - иккита эга бўлиши мумкин эмас.
     * Муддат текшируви чақирувчида (BR-TG-002).
     */
    Optional<AppUser> findByTelegramLinkCode(String telegramLinkCode);

    /**
     * Хато уриниш счётчигини АТОМАР оширади (Beruniy-014): иккита
     * параллел хато login entity орқали юритилса optimistic lock
     * тўқнашуви бериб фойдаланувчига 500 чиқарарди; SQL даражасидаги
     * UPDATE row lock билан ўз-ўзидан навбатлашади ва иккала уриниш
     * ҳам саналади. Муддати ўтган эски қулф шу ерда тозаланиб счётчик
     * янги сериядан (1 дан) бошланади - аввалги resetLock хулқи.
     * Native: HQL'да CASE ичида NULL'га ишониб бўлмайди, жадвал ўз
     * модулимизники.
     *
     * @return янгиланган қаторлар сони - 0 бўлса username мавжуд эмас
     */
    @org.springframework.data.jpa.repository.Modifying(
            clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = """
            update app_user set
              failed_attempts = case when locked_until is not null and locked_until <= :now
                                     then 1 else failed_attempts + 1 end,
              locked_until    = case when locked_until is not null and locked_until <= :now
                                     then null else locked_until end
            where username = :username
            """, nativeQuery = true)
    int incrementFailedAttempts(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
