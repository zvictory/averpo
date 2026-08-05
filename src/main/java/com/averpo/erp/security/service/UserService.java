package com.averpo.erp.security.service;

import com.averpo.erp.attachment.domain.Attachment;
import com.averpo.erp.attachment.service.AttachmentService;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.service.AuditLogService;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.security.domain.Gender;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.repo.AppUserRepository;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.EmailFormat;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Фойдаланувчилар бошқарувининг ягона public API'си
 * (docs/modules/user-management.md). Бошқа модуллар фақат шу орқали
 * мурожаат қилади (темир қоида №6); web қатлам /users йўлларини USERS
 * соҳасига (амалда SUPER_ADMIN) чеклайди, create/update эса BR-USR-011
 * service гарови билан ҲАМ ҳимояланган (user-roles.md 2-қатлам:
 * бошқа оқимдан чақирилса ҳам роль тайинлашга рухсат текширилади).
 *
 * <p>Парол ҲЕЧ ҚАЧОН очиқ сақланмайди/логланмайди - фақат bcrypt hash;
 * хато хабарларига ҳам парол қиймати ёзилмайди.
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    /** BR-USR-005: минимал парол узунлиги. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** BR-USR-001: lower-case нормализациядан КЕЙИНГИ рухсат этилган формат. */
    private static final Pattern USERNAME_FORMAT = Pattern.compile("[a-z0-9._-]{3,50}");

    /** Фойдаланувчилар репозиторийси - ўз модулимиз ичида. */
    private final AppUserRepository repository;

    /** Парол hash'лагич (bcrypt, SecurityConfig'даги ягона bean). */
    private final PasswordEncoder passwordEncoder;

    /**
     * USER_* аудит ёзувлари учун (audit-log.md): service қатламида -
     * admin экрани ҳам, /profile оқими ҳам шу методлардан ўтади,
     * ёзиш нуқтаси битта. Тафсилотга парол ҲЕЧ ҚАЧОН ёзилмайди.
     */
    private final AuditLogService auditLogService;

    /**
     * Аватар (профиль расми) сақлаш - мавжуд Attachment инфраси қайта
     * ишлатилади (Arbitr-101; модуллараро public service, темир қоида
     * №6). Расм-специфик валидация (png/jpeg/webp, 2MB) шу service'да.
     */
    private final AttachmentService attachmentService;

    /**
     * Туғилган сана «келажак эмас» текшируви бугунни компания
     * timezone'ида олади (BR-USR-014, темир қоида №12). security → shared
     * рухсат этилган йўналиш (shared ҳеч кимга боғлиқ эмас).
     */
    private final CompanySettingsService settingsService;

    /**
     * Ходим улаш (Arbitr-101 4-бўлим): EMPLOYEE контактлар рўйхати ва
     * номини public service орқали олади (темир қоида №6 - contact
     * repository'сига тегмайди). «Ходим» = Contact type=EMPLOYEE (payroll
     * employeeId нақши, алоҳида Employee entity йўқ).
     */
    private final ContactService contactService;

    /** Рўйхат экрани - username тартибида (spec). */
    @Transactional(readOnly = true)
    public List<AppUser> all() {
        return repository.findAllByOrderByUsernameAsc();
    }

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public AppUser get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Фойдаланувчи топилмади: " + id));
    }

    /**
     * Янги фойдаланувчи яратади - username lower-case'га нормализация
     * қилинади ва шу ҳолида умрбод ўзгармайди (BR-USR-003 update'да).
     *
     * @throws BusinessRuleException BR-USR-001 (формат), BR-USR-002
     *         (дубликат, 409), BR-USR-004 (ном бўш), BR-USR-005 (парол),
     *         BR-USR-011 (актор рухсати)
     */
    public AppUser create(String username, String displayName,
                          UserRole role, String rawPassword) {
        requireUsersEditActor();
        String normalized = requireUsername(username);
        if (repository.existsByUsername(normalized)) {
            throw new BusinessRuleException(BusinessRule.BR_USR_002,
                    "Бу username банд: " + normalized);
        }
        requirePasswordPolicy(rawPassword);
        AppUser user = repository.save(new AppUser(normalized,
                passwordEncoder.encode(rawPassword),
                requireDisplayName(displayName), requireRole(role)));
        // Рефайнмент (066): admin қўйган паролни фойдаланувчи биринчи
        // киришда ЎЗ пароли билан алмаштиради (banner)
        user.requirePasswordChange();
        auditLogService.record(AuditEventType.USER_CREATED,
                AuditLogService.currentUsername(), null, null,
                user.getUsername() + " (" + user.getRole().name() + ")", null);
        return user;
    }

    /**
     * Таҳрирлайди - username параметри ФАҚАТ ўзгармаганини текшириш
     * учун қабул қилинади (BR-USR-003: форма read-only, лекин tampered
     * POST бошқа қиймат юбориши мумкин - текширув service'да, spec'даги
     * имзога шу сабаб username қўшилган).
     *
     * @throws BusinessRuleException BR-USR-003 (username ўзгартиришга
     *         уриниш), BR-USR-004 (ном бўш), BR-USR-007 (охирги фаол
     *         super admin demote/deactivate), BR-USR-008 (ўзини нофаол
     *         қилиш), BR-USR-011 (актор рухсати), BR-USR-012 (ўзига
     *         SUPER_ADMIN'ни пасайтириш)
     */
    public AppUser update(UUID id, String username, String displayName,
                          UserRole role, boolean active) {
        // Ходим боғланиши ва email ўзгармайди (эски имзо чақирувчилари
        // учун - мавжуд қиймат сақланади, тасодифан узилмайди)
        AppUser existing = get(id);
        return update(id, username, displayName, role, active,
                existing.getEmployeeContactId(), existing.getEmail());
    }

    /**
     * Тўлиқ таҳрир - ходим боғланиши ва email билан (Arbitr-101 4-бўлим +
     * рефайнмент, super-admin /users'да). {@code employeeContactId} null -
     * боғланиш узилади; қиймат бўлса BR-USR-015 текширилади. {@code email}
     * ихтиёрий (admin киритади) - тўлдирилса формат текширилади
     * (BR-USR-013). Қолган майдонлар эски update() билан бир хил.
     *
     * @throws BusinessRuleException BR-USR-003/004/007/008/011/012/013 ва
     *         BR-USR-015 (ходим боғланиши)
     */
    public AppUser update(UUID id, String username, String displayName,
                          UserRole role, boolean active, UUID employeeContactId,
                          String email) {
        requireUsersEditActor();
        AppUser user = get(id);
        if (username != null && !user.getUsername().equals(normalize(username))) {
            throw new BusinessRuleException(BusinessRule.BR_USR_003,
                    "username ўзгартирилмайди: " + user.getUsername());
        }
        String name = requireDisplayName(displayName);
        UserRole newRole = requireRole(role);
        // Admin email (рефайнмент банд 4): ихтиёрий, тўлдирилса текширилади
        String cleanEmail = blankToNull(email);
        if (cleanEmail != null && !EmailFormat.isValid(cleanEmail)) {
            throw new BusinessRuleException(BusinessRule.BR_USR_013, "Email формати нотўғри");
        }
        if (!active && isCurrentUser(user)) {
            throw new BusinessRuleException(BusinessRule.BR_USR_008,
                    "Фойдаланувчи ўзини нофаол қила олмайди: " + user.getUsername());
        }
        // BR-USR-012: бошқа фаол super admin БОР бўлса ҳам ўзига
        // пасайтириш тақиқ (BR-USR-008 нақши) - сессия ўртасида ўзини
        // USERS соҳасидан қулфлаб қўйиш чалкашлигининг олдини олади
        if (isCurrentUser(user) && user.getRole() == UserRole.SUPER_ADMIN
                && newRole != UserRole.SUPER_ADMIN) {
            throw new BusinessRuleException(BusinessRule.BR_USR_012,
                    "Ўз SUPER_ADMIN ролини пасайтириш тақиқ: " + user.getUsername());
        }
        requireRemainingActiveAdmin(user, newRole, active);
        requireValidEmployeeLink(user, employeeContactId, active);
        user.setEmployeeContactId(employeeContactId);
        user.setEmailAddress(cleanEmail);
        user.update(name, newRole, active);
        auditLogService.record(AuditEventType.USER_UPDATED,
                AuditLogService.currentUsername(), null, null,
                user.getUsername() + " (" + newRole.name() + ", "
                + (active ? "фаол" : "нофаол") + ")", null);
        return user;
    }

    /**
     * ADMIN ҳар кимнинг паролини алмаштиради - эски парол сўралмайди
     * (қутқариш оқими: парол йўқолганда ҳам ишлаши шарт).
     *
     * @throws BusinessRuleException BR-USR-005 - парол сиёсати
     */
    public void changePassword(UUID id, String newRawPassword) {
        requirePasswordPolicy(newRawPassword);
        AppUser user = get(id);
        user.changePasswordHash(passwordEncoder.encode(newRawPassword));
        // Рефайнмент (066): admin reset - фойдаланувчи ўз пароли билан
        // алмаштиргунча banner (must_change=true)
        user.requirePasswordChange();
        auditLogService.record(AuditEventType.PASSWORD_CHANGED,
                AuditLogService.currentUsername(), null, null,
                user.getUsername(), null);
    }

    /**
     * Жорий фойдаланувчи ЎЗ паролини алмаштиради - эски парол bcrypt
     * билан текширилади.
     *
     * @throws BusinessRuleException BR-USR-005 (янги парол сиёсати),
     *         BR-USR-006 (эски парол нотўғри)
     * @throws NotFoundException auth контекстидаги username базада
     *         топилмаса (сессия эскирган аномал ҳолат)
     */
    public void changeOwnPassword(String oldRawPassword, String newRawPassword) {
        AppUser user = requireCurrentUser();
        if (oldRawPassword == null
                || !passwordEncoder.matches(oldRawPassword, user.getPasswordHash())) {
            throw new BusinessRuleException(BusinessRule.BR_USR_006, "Эски парол нотўғри");
        }
        requirePasswordPolicy(newRawPassword);
        user.changePasswordHash(passwordEncoder.encode(newRawPassword));
        // Рефайнмент (066): фойдаланувчи ЎЗ паролини алмаштирди - banner
        // тушади (must_change=false)
        user.clearPasswordChangeRequirement();
        // /profile оқими (ProfileController → шу метод): эга = актор
        auditLogService.record(AuditEventType.PASSWORD_CHANGED,
                AuditLogService.currentUsername(), null, null,
                user.getUsername(), null);
    }

    /** Жорий фойдаланувчи - профиль саҳифаси (/profile) кўрсатиши учун. */
    @Transactional(readOnly = true)
    public AppUser current() {
        return requireCurrentUser();
    }

    /**
     * Username бўйича топади (LayoutInfo топбар/аватар ва login оқими
     * учун) - топилмаса бўш Optional (NotFound эмас, кўриниш контексти).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AppUser> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    /**
     * Улаш учун фаол EMPLOYEE контактлар (Arbitr-101 4-бўлим): userForm
     * танлагичи ФАҚАТ шуларни кўрсатади. Public service орқали (темир
     * қоида №6). ContactRef - contact модулининг public record'и.
     */
    @Transactional(readOnly = true)
    public List<ContactService.ContactRef> employeeContactRefs() {
        return contactService.activeRefsByType(ContactType.EMPLOYEE);
    }

    /**
     * Уланган ходим номи (профилда read-only кўрсатиш учун). null -
     * уланмаган ёки контакт ўчган (ON DELETE SET NULL кэши эскирган) -
     * жимгина null, профиль синмайди.
     */
    @Transactional(readOnly = true)
    public String employeeName(UUID contactId) {
        if (contactId == null) {
            return null;
        }
        try {
            return contactService.get(contactId).getDisplayName();
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Жорий фойдаланувчи ЎЗ профилининг маълумотларини янгилайди
     * (Arbitr-101 + Arbitr-148): кўрсатиладиган ном (BR-USR-004 - бўш
     * эмас), email (BR-USR-013 формат), gender, birthdate (BR-USR-014 -
     * келажак эмас), phone. ФАҚАТ SecurityContext'даги ўзи - бошқа ҳеч
     * кимни ўзгартирмайди (id параметри ЙЎҚ), роль/username/парол
     * тегилмайди. Ном ва қолган майдонлар БИТТА транзакцияда - валидация
     * (масалан email) отса ном ҳам сақланмайди (ярим-ёзилиш бўлмайди,
     * профил формаси битта «Сақлаш» тугмаси).
     *
     * <p>Ном ЎЗГАРСА USER_UPDATED аудити ёзилади (ким ўзини ўзгартиргани
     * изланиши учун - admin {@link #update} билан бир оила); ном
     * ўзгармаса шовқин йўқ. Қолган майдонлар (Arbitr-101 семантикаси)
     * аудитсиз - фақат маълумот.
     *
     * @throws BusinessRuleException BR-USR-004 (ном бўш), BR-USR-013
     *         (email), BR-USR-014 (сана)
     * @throws NotFoundException auth контекстидаги username базада
     *         топилмаса (сессия эскирган аномал ҳолат)
     */
    public void updateOwnProfile(String displayName, String email, Gender gender,
                                 LocalDate birthdate, String phone) {
        AppUser user = requireCurrentUser();
        String name = requireDisplayName(displayName);
        String cleanEmail = blankToNull(email);
        if (cleanEmail != null && !EmailFormat.isValid(cleanEmail)) {
            throw new BusinessRuleException(BusinessRule.BR_USR_013, "Email формати нотўғри");
        }
        if (birthdate != null && birthdate.isAfter(LocalDate.now(settingsService.zoneId()))) {
            throw new BusinessRuleException(BusinessRule.BR_USR_014,
                    "Туғилган сана келажакда бўла олмайди");
        }
        // Ном ЎЗГАРСА гина ёзамиз - битта форма ҳар «Сақлаш»да USER_UPDATED
        // шовқини бермасин (валидациялардан кейин, tampered бўш ном ўтмайди)
        if (!name.equals(user.getDisplayName())) {
            user.changeDisplayName(name);
            auditLogService.record(AuditEventType.USER_UPDATED,
                    AuditLogService.currentUsername(), null, null,
                    user.getUsername() + " (ном: " + name + ")", null);
        }
        user.updateProfileInfo(cleanEmail, gender, birthdate, blankToNull(phone));
    }

    /**
     * Аватар юклаш (Arbitr-101): расм {@link AttachmentService#uploadImage}
     * орқали сақланади (BR-ATT-005/006 - png/jpeg/webp, 2MB), эски
     * аватар (agar бўлса) ўчирилади - orphan қолмасин. Ҳаммаси битта
     * транзакцияда: янги FK flush'дан кейин эски ўчади (ON DELETE SET
     * NULL янги боғланишни бузмайди).
     *
     * @throws BusinessRuleException BR-ATT-005/006 (расм тури/ҳажми)
     */
    public void setOwnProfileImage(MultipartFile file) {
        AppUser user = requireCurrentUser();
        UUID oldImageId = user.getProfileImageId();
        Attachment attachment = attachmentService.uploadImage(
                DocumentType.APP_USER, user.getId(), file);
        user.setProfileImageId(attachment.getId());
        repository.flush();
        if (oldImageId != null) {
            attachmentService.delete(oldImageId);
        }
    }

    /** Аватарни ўчиради (placeholder'га қайтади) - файл ва база ёзуви бирга кетади. */
    public void removeOwnProfileImage() {
        AppUser user = requireCurrentUser();
        UUID imageId = user.getProfileImageId();
        if (imageId != null) {
            user.setProfileImageId(null);
            repository.flush();
            attachmentService.delete(imageId);
        }
    }

    /** Жорий user аватар attachment id'си (GET /profile/image inline учун; null - йўқ). */
    @Transactional(readOnly = true)
    public UUID currentProfileImageId() {
        return requireCurrentUser().getProfileImageId();
    }

    // ---- Telegram улаш (Arbitr-103) - app_user эгаси шу модул бўлгани
    // учун код/чат майдонлари шу ерда юритилади; ботнинг ЎЗи билан
    // мулоқот TelegramService'да (у бу методларни чақиради - тескари
    // йўналиш ЙЎҚ, шунга bean ҳалқаси ҳам йўқ) ----

    /**
     * Жорий фойдаланувчига янги улаш коди ёзади (TTL - чақирувчи беради).
     * Эски код УСТИДАН ёзилади: «улаш»ни қайта босиш аввалги линкни
     * дарҳол ўлдиради (карта тузоқ 4). Кодни TelegramService яратади -
     * у ерда deep link ҳам қурилади.
     */
    public void setTelegramLinkCode(String code, java.time.Instant expiresAt) {
        requireCurrentUser().startTelegramLink(code, expiresAt);
    }

    /**
     * Ботдан келган код бўйича улашни якунлайди (poller оқими): кодни
     * эгасига боғлаб чат id'сини сақлайди, код дарҳол ўчади.
     *
     * <p>Нега BR-TG кодли хато шу ерда (security): код ва чат -
     * {@code app_user} майдонлари, уларнинг ҳақиқати шу модулники;
     * TelegramService хатони тутиб ботда кириллча жавоб беради
     * («Код нотўғри ёки эскирган»). Хато ЎЗ транзакциясида отилади -
     * чақирувчида транзакция йўқ (TelegramService.handleUpdate атайлаб
     * @Transactional эмас), шунинг учун tx rollback-only бўлиб қолмайди.
     *
     * @return уланган фойдаланувчи (жавоб хабари учун)
     * @throws BusinessRuleException BR-TG-002 - код топилмади ёки муддати ўтган
     */
    public AppUser completeTelegramLink(String code, long chatId, String telegramUsername) {
        AppUser user = repository.findByTelegramLinkCode(code)
                .filter(candidate -> candidate.telegramLinkCodeValidAt(java.time.Instant.now()))
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_TG_002,
                        "Код нотўғри ёки эскирган"));
        user.completeTelegramLink(chatId, telegramUsername);
        return user;
    }

    /** Жорий фойдаланувчи Telegram'ни узади (чат ва кутаётган код тозаланади). */
    public void unlinkOwnTelegram() {
        requireCurrentUser().unlinkTelegram();
    }

    /**
     * Жорий фойдаланувчининг уланган Telegram чати id'си ёки null
     * (уланмаган) - билдиришнома юбориш канали учун ягона ўқиш нуқтаси:
     * plugins.telegram {@code app_user}га ўзи тегмайди (темир қоида 6),
     * чат id'сини шу метод беради.
     */
    @Transactional(readOnly = true)
    public Long telegramChatId() {
        return requireCurrentUser().getTelegramChatId();
    }

    /**
     * id → displayName луғати - createdBy'ни экранда кўрсатиш учун
     * (contactNames паттерни, экран қатлами чақиради). Нофаоллар ҳам
     * киради: эски ёзувларнинг изи кўринишда қолиши шарт.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesById() {
        Map<UUID, String> names = new HashMap<>();
        for (AppUser user : repository.findAll()) {
            names.put(user.getId(), user.getDisplayName());
        }
        return names;
    }

    // ---- ички ёрдамчилар ----

    /** BR-USR-001: нормализация (strip + lower-case) ва формат текшируви. */
    private String requireUsername(String username) {
        String normalized = normalize(username);
        if (normalized == null || !USERNAME_FORMAT.matcher(normalized).matches()) {
            throw new BusinessRuleException(BusinessRule.BR_USR_001,
                    "username 3-50 белги, фақат a-z 0-9 . _ - бўлиши шарт");
        }
        return normalized;
    }

    /** username нормализацияси: strip + lower-case (null ўтади). */
    private String normalize(String username) {
        return username == null ? null : username.strip().toLowerCase();
    }

    /** Бўш/фақат-бўшлиқ матнни null'га айлантиради (ихтиёрий майдонлар учун). */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** BR-USR-004: displayName бўш эмас. */
    private String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_USR_004,
                    "Кўрсатиладиган ном киритилиши шарт");
        }
        return displayName.strip();
    }

    /** Роль форма tampering'ида null келиши мумкин - аниқ хато (BR-USR-004 оиласи эмас, формат). */
    private UserRole requireRole(UserRole role) {
        if (role == null) {
            throw new BusinessRuleException(BusinessRule.BR_USR_004,
                    "Роль танланиши шарт");
        }
        return role;
    }

    /** BR-USR-005: парол сиёсати - камида {@value #MIN_PASSWORD_LENGTH} белги. */
    private void requirePasswordPolicy(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException(BusinessRule.BR_USR_005,
                    "Парол камида " + MIN_PASSWORD_LENGTH + " белги бўлиши шарт");
        }
    }

    /**
     * BR-USR-007: user фаол SUPER_ADMIN бўлиб, амал натижасида ундан
     * чиқса (роль пасайди ёки нофаол бўлди) - тизимда БОШҚА фаол
     * super admin қолганини талаб қилади (user-roles.md: қоида эски
     * ADMIN'дан SUPER_ADMIN семантикасига кўчган).
     */
    private void requireRemainingActiveAdmin(AppUser user, UserRole newRole, boolean active) {
        boolean losesAdmin = user.getRole() == UserRole.SUPER_ADMIN && user.isActive()
                && (newRole != UserRole.SUPER_ADMIN || !active);
        if (losesAdmin && repository.countByRoleAndActiveTrueAndIdNot(
                UserRole.SUPER_ADMIN, user.getId()) == 0) {
            throw new BusinessRuleException(BusinessRule.BR_USR_007,
                    "Тизимда камида битта фаол SUPER_ADMIN қолиши шарт: " + user.getUsername());
        }
    }

    /**
     * BR-USR-011: create/update акторининг USERS EDIT рухсати (амалда
     * SUPER_ADMIN) текширилади - web қатлам /users'ни аллақачон чеклаган,
     * бу гаров бошқа оқимдан (келгуси кодда хато билан) чақирилса ҳам
     * роль тайинлашни ёпади (user-roles.md 2-қатлам). Auth контексти
     * умуман ЙЎҚ ҳолат (bootstrap runner, service-даража тестлари,
     * миграция) тизим оқими ҳисобланади - қоида қўлланмайди; anonymous
     * токен эса authority'сиз бўлгани учун рад этилади.
     */
    private void requireUsersEditActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return;
        }
        String required = com.averpo.erp.security.domain.RolePermissions
                .editAuthority(com.averpo.erp.security.domain.Permission.USERS);
        boolean allowed = auth.getAuthorities().stream()
                .anyMatch(granted -> required.equals(granted.getAuthority()));
        if (!allowed) {
            throw new BusinessRuleException(BusinessRule.BR_USR_011,
                    "Фойдаланувчи бошқаруви учун USERS EDIT рухсати керак: " + auth.getName());
        }
    }

    /**
     * BR-USR-015: ходим боғланиши тўғрилиги - контакт мавжуд ва
     * type=EMPLOYEE, ҳамда бошқа ФАОЛ user'да банд эмас (1:1). null -
     * боғланиш узилди/уланмаган, текширув йўқ. Текширув фақат user ФАОЛ
     * бўлганда бандликка тегишли (нофаол user контактни эгалламайди -
     * DB partial unique билан бир мантиқ).
     */
    private void requireValidEmployeeLink(AppUser user, UUID contactId, boolean active) {
        if (contactId == null) {
            return;
        }
        Contact contact = contactService.get(contactId);
        if (contact.getType() != ContactType.EMPLOYEE) {
            throw new BusinessRuleException(BusinessRule.BR_USR_015,
                    "Фақат ходим (EMPLOYEE) контакти уланади: " + contact.getDisplayName());
        }
        if (active && repository.existsByEmployeeContactIdAndActiveTrueAndIdNot(
                contactId, user.getId())) {
            throw new BusinessRuleException(BusinessRule.BR_USR_015,
                    "Бу ходим аллақачон бошқа фаол фойдаланувчига уланган: "
                    + contact.getDisplayName());
        }
    }

    /** Амал қилинаётган user жорий сессия эгасими (BR-USR-008 учун). */
    private boolean isCurrentUser(AppUser user) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && user.getUsername().equals(auth.getName());
    }

    /** Жорий сессия эгаси - ўз паролини алмаштириш оқими учун. */
    private AppUser requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new NotFoundException("Жорий фойдаланувчи аниқланмади");
        }
        return repository.findByUsername(auth.getName())
                .orElseThrow(() -> new NotFoundException(
                        "Фойдаланувчи топилмади: " + auth.getName()));
    }
}
