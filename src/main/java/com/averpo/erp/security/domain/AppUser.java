package com.averpo.erp.security.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Тизим фойдаланувчиси - form login учун. Парол фақат bcrypt hash
 * кўринишида сақланади, очиқ парол ҳеч қаерда ёзилмайди.
 *
 * <p>Ўчириш ЙЎҚ - фақат {@code active=false} (createdBy аудит излари
 * сақланиши учун, CoA/Contact қоидасининг айнан ўзи); username
 * яратилгандан кейин ЎЗГАРМАЙДИ (BR-USR-003) - шунинг учун унга setter
 * ҳам, update методида параметр ҳам йўқ
 * (docs/modules/user-management.md).
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseEntity {

    /** Логин - unique, lower-case нормализацияда сақланади (BR-USR-001). */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt hash - PasswordEncoder орқали яратилади. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Экранда кўрсатиладиган ном. */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Роль - рухсатларни белгилайди. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** Нофаол фойдаланувчи кира олмайди (ўчириш ўрнига). */
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Кетма-кет муваффақиятсиз login уринишлари сони (BR-USR-009) -
     * муваффақиятли киришда ёки қулф муддати ўтгач нолланади.
     */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    /**
     * Шу пайтгача login тақиқ (BR-USR-009); {@code null} - қулф йўқ.
     * UTC (темир қоида №12) - экранда компания минтақасида кўрсатилади.
     */
    @Column(name = "locked_until")
    private java.time.Instant lockedUntil;

    // --- Профиль шахсий майдонлари (Arbitr-101, changeset 057) - ҳаммаси
    // ихтиёрий/nullable, login'га таъсир қилмайди, фақат маълумот ---

    /**
     * Профиль email - ФАҚАТ маълумот майдони, login username ЭМАС
     * (username ўзгармайди, BR-USR-003). Тўлдирилса формат текширилади
     * (BR-USR-013). null - кўрсатилмаган.
     */
    @Column(length = 255)
    private String email;

    /** Жинс (MALE/FEMALE); null - кўрсатилмаган. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    /**
     * Туғилган сана; null - кўрсатилмаган. Келажак сана бўлмайди
     * (BR-USR-014). Экранда компания timezone қоидасига мос
     * (LocalDate - вақт компоненти йўқ, зона ўзгартирмайди).
     */
    @Column
    private java.time.LocalDate birthdate;

    /** Телефон - оддий матн (контакт phone нақши, қатъий формат мажбурланмайди). */
    @Column(length = 50)
    private String phone;

    /**
     * Профиль расми (аватар) - {@code attachment} id'сига soft ref
     * (DB FK, ON DELETE SET NULL; payroll employee_id нақши: JPA'да
     * оддий UUID, модуллараро entity боғланиш йўқ - темир қоида №6).
     * null - аватар йўқ (placeholder кўрсатилади).
     */
    @Column(name = "profile_image_id")
    private java.util.UUID profileImageId;

    /**
     * Уланган ходим - {@code contact} (type=EMPLOYEE) id'сига soft ref
     * (DB FK, ON DELETE SET NULL). super-admin /users'да ўрнатади
     * (self-service эмас, 4-бўлим); профилда read-only кўринади. Битта
     * контакт фақат битта фаол user'да (BR-USR-015). null - уланмаган.
     */
    @Column(name = "employee_contact_id")
    private java.util.UUID employeeContactId;

    /**
     * Паролни алмаштириш зарурлиги (Arbitr-101 рефайнмент, changeset 066):
     * admin парол қўйганда (create) ёки reset қилганда true бўлади -
     * фойдаланувчи биринчи login'дан кейин ЎЗ паролини алмаштиргач false.
     * Оддий версия: banner + login redirect (мажбурий эмас); парол-муддати
     * механизми (auth-security-policy) кейин мажбурий қилиб улашади.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    // --- Telegram улаш (Arbitr-103, changeset 059) - user-profile.md
    // 3-бўлим. Мақсад ҳозирча ФАҚАТ улаш; билдиришнома турлари 2-босқич ---

    /**
     * Уланган Telegram чати id'си ёки null (уланмаган). BIGINT: Telegram
     * id'лари 32-бит чегарасидан ошган. Билдиришномалар келажакда шу
     * чатга кетади (auth-security-policy lockout огоҳлантириши).
     */
    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    /**
     * Уланган ҳисобнинг @username'и (@'сиз) ёки null - профилда
     * «Уланган: @username» деб кўрсатиш учун снапшот. Telegram'да
     * username кейин ўзгарса бизда эскиси қолади - чат id'си ЎЗГАРМАЙДИ,
     * канал ишлайверади (кўрсатиш учун холос).
     */
    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    /**
     * Улашнинг бир марталик коди ёки null. Профилда «улаш» босилганда
     * яратилади, ботда {@code /start <код>} келганда ишлатилади ва
     * ДАРҲОЛ null'га тушади - қайта ишламайди (карта тузоқ 4). Жадвал
     * ЭМАС, устун: «бир фойдаланувчида бир код» устига ёзиш билан
     * таъминланади, тозалаш вазифаси керак эмас.
     */
    @Column(name = "telegram_link_code", length = 64)
    private String telegramLinkCode;

    /** Улаш коди амал қилиш чегараси (UTC, TTL 10 дақиқа); null - код йўқ. */
    @Column(name = "telegram_link_expires_at")
    private java.time.Instant telegramLinkExpiresAt;

    /** Янги фойдаланувчи - hash тайёр ҳолда келади. */
    public AppUser(String username, String passwordHash, String displayName, UserRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    /** Таҳрирланадиган майдонлар битта жойда - username йўқ (BR-USR-003). */
    public void update(String displayName, UserRole role, boolean active) {
        this.displayName = displayName;
        this.role = role;
        this.active = active;
    }

    /**
     * ФАҚАТ кўрсатиладиган номни ўзгартиради (Arbitr-148, /profile
     * self-service): фойдаланувчи ўз исмини таҳрирлайди - роль/active
     * ТЕГИЛМАЙДИ (admin {@link #update} нақшидан фарқли, у учаласини
     * бирга ёзади). Бўш эмаслик гарови (BR-USR-004) UserService'да.
     */
    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Парол фақат тайёр hash кўринишида алмашади (BR-USR-005 текшируви service'да). */
    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    /**
     * Паролни алмаштириш зарур деб белгилайди (Arbitr-101 рефайнмент):
     * admin create ёки reset'да - фойдаланувчи биринчи кириши олдида
     * banner кўради. {@code true} - қайта паролгача.
     */
    public void requirePasswordChange() {
        this.mustChangePassword = true;
    }

    /**
     * Паролни алмаштириш зарурлигини олиб ташлайди - фойдаланувчи ЎЗ
     * паролини алмаштиргач (changeOwnPassword) чақирилади.
     */
    public void clearPasswordChangeRequirement() {
        this.mustChangePassword = false;
    }

    /**
     * Email'ни алоҳида ўрнатади (Arbitr-101 рефайнмент): super-admin
     * /users/edit'да ходим/фойдаланувчи email'ини киритади (self-service
     * {@link #updateProfileInfo}'дан фарқли - у gender/birthdate/phone'ни
     * ҳам ўзгартиради, admin эса фақат email). Формат текшируви UserService'да.
     */
    public void setEmailAddress(String email) {
        this.email = email;
    }

    /**
     * Ўз профилининг шахсий майдонларини янгилайди (Arbitr-101) -
     * фойдаланувчи /profile'да ўзгартиради. Формат/қоида текшируви
     * (email BR-USR-013, birthdate BR-USR-014) UserService'да; entity
     * фақат ҳолат юритади. Барчаси битта жойда - update() нақши.
     */
    public void updateProfileInfo(String email, Gender gender,
                                  java.time.LocalDate birthdate, String phone) {
        this.email = email;
        this.gender = gender;
        this.birthdate = birthdate;
        this.phone = phone;
    }

    /**
     * Аватар attachment id'сини ўрнатади (null - олиб ташлаш). Эски
     * расмни (agar бўлса) ўчириш чақирувчида (AttachmentService.delete),
     * бу метод фақат FK'ни алмаштиради.
     */
    public void setProfileImageId(java.util.UUID profileImageId) {
        this.profileImageId = profileImageId;
    }

    /**
     * Уланган ходим контактини ўрнатади (super-admin, null - узиш) -
     * BR-USR-015 (1:1 фаол) текшируви UserService'да.
     */
    public void setEmployeeContactId(java.util.UUID employeeContactId) {
        this.employeeContactId = employeeContactId;
    }

    /**
     * Хато уринишни санайди ва янги қийматни қайтаради - lockout
     * қарори (BR-USR-009 бўсағаси) чақирувчида, entity фақат ҳолат
     * юритади.
     */
    public int registerFailedAttempt() {
        return ++failedAttempts;
    }

    /** Қулф қўяди - шу пайтгача login тақиқ (BR-USR-009). */
    public void lockUntil(java.time.Instant until) {
        this.lockedUntil = until;
    }

    /** Счётчик ва қулфни нолга туширади (муваффақиятли кириш/муддат ўтиши). */
    public void resetLock() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    /** Шу пайтда қулфланганми - JpaUserDetailsService accountNonLocked учун. */
    public boolean lockedAt(java.time.Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Улаш кодини (ва унинг муддатини) ўрнатади - эскиси УСТИДАН
     * ёзилади: фойдаланувчи «улаш»ни қайта босса аввалги код дарҳол
     * ишламай қолади (карта тузоқ 4 - бир фойдаланувчида бир код).
     */
    public void startTelegramLink(String code, java.time.Instant expiresAt) {
        this.telegramLinkCode = code;
        this.telegramLinkExpiresAt = expiresAt;
    }

    /**
     * Улашни якунлайди: чат боғланади ва код ДАРҲОЛ ўчади - ўша код
     * билан иккинчи ҳисоб уланмайди (бир марталик).
     */
    public void completeTelegramLink(Long chatId, String telegramUsername) {
        this.telegramChatId = chatId;
        this.telegramUsername = telegramUsername;
        this.telegramLinkCode = null;
        this.telegramLinkExpiresAt = null;
    }

    /**
     * Telegram'ни узади - чат ва кутиб турган код ҳам тозаланади
     * (узишдан кейин эски линк билан қайта уланиб қолмасин).
     */
    public void unlinkTelegram() {
        this.telegramChatId = null;
        this.telegramUsername = null;
        this.telegramLinkCode = null;
        this.telegramLinkExpiresAt = null;
    }

    /** Telegram уланганми - профил блоки ва билдиришнома канали шарти. */
    public boolean telegramLinked() {
        return telegramChatId != null;
    }

    /**
     * Кутиб турган улаш коди шу пайтда амал қиладими (BR-TG-002
     * текшируви): код бор ва муддати ўтмаган.
     */
    public boolean telegramLinkCodeValidAt(java.time.Instant now) {
        return telegramLinkCode != null && telegramLinkExpiresAt != null
                && telegramLinkExpiresAt.isAfter(now);
    }
}
