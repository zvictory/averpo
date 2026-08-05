package com.averpo.erp.security;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.security.config.AdminUserInitializer;
import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.security.domain.Gender;
import com.averpo.erp.security.domain.RolePermissions;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.repo.AppUserRepository;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserService тестлари: docs/modules/user-management.md → «Тестлар».
 * Bootstrap admin (AdminUserInitializer) контекст кўтарилишида
 * яратилган бўлади - BR-USR-007 сценарийлари шуни ҳисобга олади.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    /** Тестларда ишлатиладиган намунавий кучли парол. */
    private static final String PASSWORD = "kuchli-parol-123";

    @Autowired UserService userService;
    @Autowired AppUserRepository repository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ContactService contactService;

    /** SecurityContext излари кейинги тестга ўтмасин. */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Жорий сессияни SUPER_ADMIN сифатида симуляция қилади (одатий актор). */
    private void authenticateAs(String username) {
        authenticateAs(username, UserRole.SUPER_ADMIN);
    }

    /**
     * Жорий сессияни берилган роль билан симуляция қилади - authority'лар
     * продакшн матрицасидан (DEC-092: BR-USR-011 гарови USERS EDIT
     * authority'сига қарайди, шунчаки ROLE_* етмайди).
     */
    private void authenticateAs(String username, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        RolePermissions.authorities(role)));
    }

    /** Қисқа ясагич. */
    private AppUser create(String username, UserRole role) {
        return userService.create(username, "Тест " + username, role, PASSWORD);
    }

    /** Bootstrap admin (контекст кўтарилишида яратилган). */
    private AppUser bootstrapAdmin() {
        return repository.findByUsername(AdminUserInitializer.ADMIN_USERNAME).orElseThrow();
    }

    @Test
    void create_normalizesUsername_storesBcryptOnly() {
        AppUser user = userService.create("  MixedCase.User_1  ", "Синов", UserRole.VIEWER_AUDITOR, PASSWORD);

        // Upper-case ва бўшлиқлар нормализацияда кетади (BR-USR-001)
        assertThat(user.getUsername()).isEqualTo("mixedcase.user_1");
        // Парол фақат bcrypt hash - очиқ қиймат ҳеч қаерда сақланмаган
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void create_validation_guards() {
        // BR-USR-001: қисқа
        assertThatThrownBy(() -> create("ab", UserRole.VIEWER_AUDITOR))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-001"));
        // BR-USR-001: тақиқланган белги (бўшлиқ ва кирилл)
        assertThatThrownBy(() -> create("user name", UserRole.VIEWER_AUDITOR))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-001"));
        assertThatThrownBy(() -> create("фойдаланувчи", UserRole.VIEWER_AUDITOR))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-001"));

        // BR-USR-002: дубликат (нормализациядан кейин ҳам) - 409
        create("takror", UserRole.VIEWER_AUDITOR);
        assertThatThrownBy(() -> create("TAKROR", UserRole.VIEWER_AUDITOR))
                .satisfies(e -> {
                    assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-USR-002");
                    assertThat(((BusinessRuleException) e).getRule().getHttpStatus()).isEqualTo(409);
                });

        // BR-USR-004: бўш ном
        assertThatThrownBy(() -> userService.create("nomsiz", " ", UserRole.VIEWER_AUDITOR, PASSWORD))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-004"));

        // BR-USR-005: қисқа парол
        assertThatThrownBy(() -> userService.create("qisqa", "Синов", UserRole.VIEWER_AUDITOR, "1234567"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-005"));
    }

    @Test
    void update_changesFields_rejectsUsernameChange() {
        AppUser user = create("tahrir", UserRole.VIEWER_AUDITOR);

        AppUser updated = userService.update(user.getId(), "tahrir",
                "Янги ном", UserRole.ACCOUNTANT, true);
        assertThat(updated.getDisplayName()).isEqualTo("Янги ном");
        assertThat(updated.getRole()).isEqualTo(UserRole.ACCOUNTANT);

        // Бир хил username бошқа регистрда - нормализацияда тенг, хато эмас
        userService.update(user.getId(), "TAHRIR", "Янги ном", UserRole.ACCOUNTANT, true);

        // BR-USR-003: бошқа username юборилди (read-only майдон tampered)
        assertThatThrownBy(() -> userService.update(user.getId(), "boshqa",
                "Янги ном", UserRole.ACCOUNTANT, true))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-003"));
    }

    @Test
    void update_lastActiveAdmin_protected() {
        AppUser bootstrap = bootstrapAdmin();
        AppUser admin2 = create("admin2", UserRole.SUPER_ADMIN);

        // Иккита фаол admin бор - бирини пасайтириш OK
        authenticateAs(AdminUserInitializer.ADMIN_USERNAME);
        userService.update(admin2.getId(), "admin2", "Тест admin2", UserRole.ACCOUNTANT, true);
        // Қайтариб admin қиламиз ва bootstrap'ни нофаол қиламиз (admin2 сессиясидан)
        userService.update(admin2.getId(), "admin2", "Тест admin2", UserRole.SUPER_ADMIN, true);
        authenticateAs("admin2");
        userService.update(bootstrap.getId(), null, bootstrap.getDisplayName(),
                UserRole.SUPER_ADMIN, false);

        // Энди admin2 - охирги фаол ADMIN: demote ҲАМ deactivate ҲАМ тақиқ
        authenticateAs(AdminUserInitializer.ADMIN_USERNAME);
        assertThatThrownBy(() -> userService.update(admin2.getId(), "admin2",
                "Тест admin2", UserRole.ACCOUNTANT, true))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-007"));
        assertThatThrownBy(() -> userService.update(admin2.getId(), "admin2",
                "Тест admin2", UserRole.SUPER_ADMIN, false))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-007"));
    }

    @Test
    void update_selfDeactivation_rejected() {
        AppUser admin2 = create("selfadmin", UserRole.SUPER_ADMIN);
        authenticateAs("selfadmin");

        // Бошқа фаол admin (bootstrap) бор бўлса ҳам ўзини нофаол қилиш тақиқ
        assertThatThrownBy(() -> userService.update(admin2.getId(), "selfadmin",
                "Тест selfadmin", UserRole.SUPER_ADMIN, false))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-008"));
    }

    @Test
    void update_selfSuperAdminDemotion_rejected() {
        // BR-USR-012 (user-roles.md): бошқа фаол super admin (bootstrap)
        // БОР бўлса ҳам ўзига SUPER_ADMIN'ни пасайтириш тақиқ - сессия
        // ўртасида ўзини USERS соҳасидан қулфлаш олдини олади
        AppUser self = create("ozdemote", UserRole.SUPER_ADMIN);
        authenticateAs("ozdemote");

        assertThatThrownBy(() -> userService.update(self.getId(), "ozdemote",
                "Тест ozdemote", UserRole.CHIEF_ACCOUNTANT, true))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-012"));
    }

    @Test
    void create_update_withoutUsersEdit_rejected() {
        // BR-USR-011 (user-roles.md): актор USERS EDIT'сиз (SALES_MANAGER) -
        // create ҳам update ҳам 403 билан рад этилади; auth контекстисиз
        // (bootstrap/тизим оқими) эса қоида қўлланмайди - юқоридаги
        // тестлардаги create() ясагичлар шунга таянади
        AppUser target = create("brusr011nishon", UserRole.VIEWER_AUDITOR);
        authenticateAs("sotuvchi092", UserRole.SALES_MANAGER);

        assertThatThrownBy(() -> create("brusr011yangi", UserRole.VIEWER_AUDITOR))
                .satisfies(e -> {
                    assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-USR-011");
                    assertThat(((BusinessRuleException) e).getRule().getHttpStatus()).isEqualTo(403);
                });
        assertThatThrownBy(() -> userService.update(target.getId(), "brusr011nishon",
                "Янги ном", UserRole.SALES_MANAGER, true))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-011"));
    }

    @Test
    void changePassword_admin_replacesHash() {
        AppUser user = create("parolli", UserRole.VIEWER_AUDITOR);
        String oldHash = user.getPasswordHash();

        userService.changePassword(user.getId(), "yangi-parol-456");

        AppUser reloaded = userService.get(user.getId());
        assertThat(reloaded.getPasswordHash()).isNotEqualTo(oldHash);
        // Эски парол ишламайди, янгиси ишлайди
        assertThat(passwordEncoder.matches(PASSWORD, reloaded.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches("yangi-parol-456", reloaded.getPasswordHash())).isTrue();

        // BR-USR-005 алмаштиришда ҳам ишлайди
        assertThatThrownBy(() -> userService.changePassword(user.getId(), "qisqa"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-005"));
    }

    /**
     * DEC-148: жорий фойдаланувчи (VIEWER_AUDITOR ҳам) ЎЗ
     * кўрсатиладиган номини профил формасидан ўзгартиради - фақат ЎЗИ
     * (роль/username/парол тегилмайди); бўш ном BR-USR-004. Ном
     * updateOwnProfile'нинг биринчи майдони (битта форма, битта тx).
     */
    @Test
    void updateOwnProfile_changesNameSelfOnly_rejectsBlank() {
        AppUser me = create("nomozgar", UserRole.VIEWER_AUDITOR);
        AppUser other = create("boshqasi", UserRole.VIEWER_AUDITOR);
        UserRole meRole = me.getRole();
        String meUsername = me.getUsername();
        String meHash = me.getPasswordHash();
        String otherName = other.getDisplayName();
        authenticateAs("nomozgar", UserRole.VIEWER_AUDITOR);

        userService.updateOwnProfile("  Янги Исм  ", null, null, null, null);
        AppUser reloaded = userService.get(me.getId());
        // Ном strip'ланиб ёзилди
        assertThat(reloaded.getDisplayName()).isEqualTo("Янги Исм");
        // Роль/username/парол ТЕГИЛМАДИ
        assertThat(reloaded.getRole()).isEqualTo(meRole);
        assertThat(reloaded.getUsername()).isEqualTo(meUsername);
        assertThat(reloaded.getPasswordHash()).isEqualTo(meHash);
        // Бошқа фойдаланувчи ЎЗГАРМАДИ (фақат ўзи)
        assertThat(userService.get(other.getId()).getDisplayName()).isEqualTo(otherName);

        // BR-USR-004: бўш ном рад этилади
        assertThatThrownBy(() -> userService.updateOwnProfile("   ", null, null, null, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-004"));
    }

    @Test
    void changeOwnPassword_checksOldPassword() {
        AppUser user = create("ozparol", UserRole.VIEWER_AUDITOR);
        authenticateAs("ozparol");

        // BR-USR-006: эски парол хато
        assertThatThrownBy(() -> userService.changeOwnPassword("notogri", "yangi-parol-456"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-006"));

        // Муваффақиятли оқим
        userService.changeOwnPassword(PASSWORD, "yangi-parol-456");
        assertThat(passwordEncoder.matches("yangi-parol-456",
                userService.get(user.getId()).getPasswordHash())).isTrue();
    }

    @Test
    void namesById_returnsDisplayNames() {
        AppUser user = create("nomlar", UserRole.VIEWER_AUDITOR);

        var names = userService.namesById();
        assertThat(names).containsEntry(user.getId(), "Тест nomlar");
        assertThat(names).containsEntry(bootstrapAdmin().getId(),
                bootstrapAdmin().getDisplayName());
    }

    @Test
    void createdBy_filledInAuthContext_nullWithout() {
        // Auth контекстида: жорий user id ёзилади (SecurityAuditorAware)
        authenticateAs(AdminUserInitializer.ADMIN_USERNAME);
        AppUser created = create("izli", UserRole.VIEWER_AUDITOR);
        assertThat(created.getCreatedBy()).isEqualTo(bootstrapAdmin().getId());

        // Контекстсиз (scheduler/bootstrap симуляцияси): NULL - сохта
        // атрибуция қилинмайди
        SecurityContextHolder.clearContext();
        AppUser system = create("izsiz", UserRole.VIEWER_AUDITOR);
        assertThat(system.getCreatedBy()).isNull();
    }

    @Test
    void get_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> userService.get(UUID.randomUUID()))
                .isInstanceOf(com.averpo.erp.shared.exception.NotFoundException.class);
    }

    /** Қисқа контакт ясагич (профиль/ходим тестлари учун). */
    private Contact contact(ContactType type, String name) {
        return contactService.create(type, new ContactService.ContactData(
                name, null, null, null, null, null, null, null, null, null, null));
    }

    /** Реал png bytes (аватар ImageIO ўлчов текшируви ўтиши учун). */
    private byte[] pngBytes(int w, int h) throws Exception {
        var img = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    /**
     * DEC-101 + DEC-148: жорий фойдаланувчи (VIEWER_AUDITOR ҳам) ЎЗ
     * маълумотларини сақлайди - round-trip (ном ҳам); email формати
     * (BR-USR-013) ва келажак сана (BR-USR-014) рад этилади. Ном ҳар
     * чақирувда валид узатилади (BR-USR-004 биринчи гаров) - қолган
     * майдон валидацияларини алоҳида текшириш учун.
     */
    @Test
    void updateOwnProfile_savesFields_rejectsInvalid() {
        AppUser user = create("profil", UserRole.VIEWER_AUDITOR);
        authenticateAs("profil", UserRole.VIEWER_AUDITOR);

        userService.updateOwnProfile("Профил Эгаси", "user@example.com", Gender.MALE,
                LocalDate.of(1990, 5, 15), "+998901234567");
        AppUser reloaded = userService.get(user.getId());
        assertThat(reloaded.getDisplayName()).isEqualTo("Профил Эгаси");
        assertThat(reloaded.getEmail()).isEqualTo("user@example.com");
        assertThat(reloaded.getGender()).isEqualTo(Gender.MALE);
        assertThat(reloaded.getBirthdate()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(reloaded.getPhone()).isEqualTo("+998901234567");

        // BR-USR-013: email формати нотўғри (ном валид - гаровдан ўтади)
        assertThatThrownBy(() -> userService.updateOwnProfile("Ном", "notanemail", null, null, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-013"));
        // BR-USR-014: туғилган сана келажакда
        assertThatThrownBy(() -> userService.updateOwnProfile("Ном", null, null,
                LocalDate.now().plusDays(2), null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-014"));
    }

    /**
     * DEC-101: аватар юклаш FK'ни ўрнатади; янги расм эски orphan'ни
     * ўчириб алмаштиради; removeOwnProfileImage placeholder'га (null) қайтаради.
     */
    @Test
    void profileImage_uploadReplacesAndRemove() throws Exception {
        AppUser user = create("rasmli", UserRole.VIEWER_AUDITOR);
        authenticateAs("rasmli", UserRole.VIEWER_AUDITOR);

        userService.setOwnProfileImage(
                new MockMultipartFile("file", "a.png", "image/png", pngBytes(64, 64)));
        UUID firstImage = userService.get(user.getId()).getProfileImageId();
        assertThat(firstImage).isNotNull();

        userService.setOwnProfileImage(
                new MockMultipartFile("file", "b.png", "image/png", pngBytes(48, 48)));
        UUID secondImage = userService.get(user.getId()).getProfileImageId();
        assertThat(secondImage).isNotNull().isNotEqualTo(firstImage);

        userService.removeOwnProfileImage();
        assertThat(userService.get(user.getId()).getProfileImageId()).isNull();
    }

    /**
     * DEC-101 4-бўлим: super-admin app_user'ни EMPLOYEE контактга
     * улайди; BR-USR-015 - битта ходим иккинчи фаол user'га уланмайди ва
     * EMPLOYEE эмас контакт уланмайди.
     */
    @Test
    void employeeLink_validAndGuards() {
        AppUser user = create("xodimli", UserRole.VIEWER_AUDITOR);
        Contact emp = contact(ContactType.EMPLOYEE, "Ходим Бир");
        authenticateAs(AdminUserInitializer.ADMIN_USERNAME);

        userService.update(user.getId(), "xodimli", "Тест xodimli",
                UserRole.VIEWER_AUDITOR, true, emp.getId(), null);
        assertThat(userService.get(user.getId()).getEmployeeContactId()).isEqualTo(emp.getId());
        assertThat(userService.employeeName(emp.getId())).isEqualTo("Ходим Бир");

        // BR-USR-015: ходим бошқа фаол user'га уланмайди
        AppUser user2 = create("xodimli2", UserRole.VIEWER_AUDITOR);
        assertThatThrownBy(() -> userService.update(user2.getId(), "xodimli2",
                "Тест xodimli2", UserRole.VIEWER_AUDITOR, true, emp.getId(), null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-015"));

        // BR-USR-015: EMPLOYEE эмас контакт уланмайди
        Contact cust = contact(ContactType.CUSTOMER, "Мижоз Бир");
        assertThatThrownBy(() -> userService.update(user2.getId(), "xodimli2",
                "Тест xodimli2", UserRole.VIEWER_AUDITOR, true, cust.getId(), null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-015"));
    }

    /**
     * Рефайнмент банд 5 (066): must_change_password - create'да (admin
     * қўйган парол) ва reset'да true; фойдаланувчи ЎЗ паролини алмаштиргач
     * false.
     */
    @Test
    void mustChangePassword_setOnCreateAndReset_clearedOnOwnChange() {
        AppUser user = create("mustuser", UserRole.VIEWER_AUDITOR);
        assertThat(userService.get(user.getId()).isMustChangePassword()).isTrue();

        // Фойдаланувчи ЎЗ паролини алмаштиради → флаг тушади
        authenticateAs("mustuser", UserRole.VIEWER_AUDITOR);
        userService.changeOwnPassword(PASSWORD, "yangi-parol-456");
        assertThat(userService.get(user.getId()).isMustChangePassword()).isFalse();

        // Admin reset → яна true
        authenticateAs(AdminUserInitializer.ADMIN_USERNAME);
        userService.changePassword(user.getId(), "reset-parol-789");
        assertThat(userService.get(user.getId()).isMustChangePassword()).isTrue();
    }

    /**
     * Рефайнмент банд 4: admin /users/edit'да email киритади (ихтиёрий) -
     * сақланади; формат нотўғри бўлса BR-USR-013.
     */
    @Test
    void update_adminEmail_savedAndValidated() {
        AppUser user = create("emailuser", UserRole.VIEWER_AUDITOR);
        authenticateAs(AdminUserInitializer.ADMIN_USERNAME);

        userService.update(user.getId(), "emailuser", "Тест emailuser",
                UserRole.VIEWER_AUDITOR, true, null, "admin@example.com");
        assertThat(userService.get(user.getId()).getEmail()).isEqualTo("admin@example.com");

        assertThatThrownBy(() -> userService.update(user.getId(), "emailuser",
                "Тест emailuser", UserRole.VIEWER_AUDITOR, true, null, "notanemail"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-USR-013"));
    }
}
