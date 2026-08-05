package com.averpo.erp.security.web;

import com.averpo.erp.security.domain.AppUser;
import lombok.Getter;
import lombok.Setter;

/**
 * /users формаси - яратиш ва таҳрир учун умумий. Парол майдонлари
 * фақат яратишда тўлдирилади (таҳрирда парол АЛОҲИДА карта орқали
 * алмашади - тасодифан бирга submit бўлмасин, spec); username таҳрирда
 * read-only, лекин hidden эмас - tampered қиймат BR-USR-003 текширувига
 * service'да учрайди.
 */
@Getter
@Setter
public class UserForm {

    /** Таҳрирда user id'си; яратишда бўш. */
    private String id;

    /** Логин - яратишда киритилади, кейин ўзгармайди (BR-USR-003). */
    private String username;

    /** Экранда кўрсатиладиган ном. */
    private String displayName;

    /** Роль номи (UserRole) - select'дан келади. */
    private String role;

    /** Фаоллик - фақат таҳрир формасида кўринади. */
    private boolean active = true;

    /**
     * Уланган ходим контакт id'си (DEC-101 4-бўлим): super-admin
     * таҳрирда танлайди - фақат type=EMPLOYEE контактлар; бўш - уланмаган.
     */
    private String employeeContactId;

    /**
     * Email (DEC-101 рефайнмент банд 4): super-admin таҳрирда ихтиёрий
     * киритади - профилдаги (self-service) email билан айнан бир майдон.
     */
    private String email;

    /** Парол (фақат яратишда). */
    private String password;

    /** Парол такрори (фақат яратишда) - мослик server'да ҳам текширилади. */
    private String passwordConfirm;

    /** Таҳрир формасини мавжуд user'дан тўлдиради (парол майдонлари бўш). */
    public static UserForm from(AppUser user) {
        UserForm form = new UserForm();
        form.setId(user.getId().toString());
        form.setUsername(user.getUsername());
        form.setDisplayName(user.getDisplayName());
        form.setRole(user.getRole().name());
        form.setActive(user.isActive());
        form.setEmployeeContactId(user.getEmployeeContactId() == null
                ? null : user.getEmployeeContactId().toString());
        form.setEmail(user.getEmail());
        return form;
    }
}
