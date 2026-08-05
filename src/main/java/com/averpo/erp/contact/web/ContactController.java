package com.averpo.erp.contact.web;

import com.averpo.erp.contact.domain.AddressType;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.i18n.Msg;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.PaymentTermService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Мижозлар ва етказувчилар экранлари - иккала рўйхат битта контроллерда
 * ({kind} path сегменти орқали), чунки QBO услубида улар битта contact
 * жадвалининг иккита кўриниши (spec: docs/modules/contact.md).
 */
@Controller
@RequestMapping("/{kind:customers|vendors|employees}")
@RequiredArgsConstructor
public class ContactController {

    /** Контактлар service. */
    private final ContactService contactService;

    /** Валюта select'и учун каталог. */
    private final CurrencyService currencyService;

    /** Тўлов шарти select'и учун каталог. */
    private final PaymentTermService paymentTermService;

    /** Credit limit валютаси default'и (home) учун (U5). */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Path сегментидан контакт типини аниқлайди. */
    private ContactType type(String kind) {
        return switch (kind) {
            case "customers" -> ContactType.CUSTOMER;
            case "vendors" -> ContactType.VENDOR;
            default -> ContactType.EMPLOYEE; // employees
        };
    }

    /**
     * Рўйхат - стандарт каталог филтри (Arbitr-068): фаоллик (фаол/
     * нофаол/ҳаммаси, default фаол - QBO услуби) ва матн (ном/компания).
     * Эски showInactive=true линклари «ҳаммаси» деб тушунилади.
     */
    @GetMapping
    public String list(@PathVariable String kind,
                       @RequestParam(required = false) String activity,
                       @RequestParam(defaultValue = "false") boolean showInactive,
                       @RequestParam(required = false) String q,
                       Model model) {
        String act = activity != null && !activity.isBlank()
                ? activity : (showInactive ? "ALL" : "ACTIVE");
        Boolean active = switch (act) {
            case "INACTIVE" -> Boolean.FALSE;
            case "ALL" -> null;
            default -> Boolean.TRUE;
        };
        model.addAttribute("kind", kind);
        model.addAttribute("isCustomer", type(kind) == ContactType.CUSTOMER);
        model.addAttribute("isEmployee", type(kind) == ContactType.EMPLOYEE);
        model.addAttribute("contacts", contactService.list(
                new ContactService.ListFilter(type(kind), active, q)));
        model.addAttribute("activity", active == null ? "ALL"
                : (active ? "ACTIVE" : "INACTIVE"));
        model.addAttribute("q", q == null ? "" : q);
        // Ходимлар рўйхатидаги «Ойлик» устуни учун - oklad доим home валютада
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "contact/list";
    }

    /** Янги контакт формаси - HTMX'да drawer partial, оддийда тўлиқ саҳифа (fallback). */
    @GetMapping("/new")
    public String createForm(@PathVariable String kind, Model model,
                             jakarta.servlet.http.HttpServletRequest request) {
        fillFormModel(model, kind, new ContactForm(), null);
        return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                ? "contact/formDrawer" : "contact/form";
    }

    /** Янги контакт сақлаш. */
    @PostMapping
    public String create(@PathVariable String kind, @ModelAttribute ContactForm form,
                         Model model, RedirectAttributes redirect,
                         jakarta.servlet.http.HttpServletRequest request,
                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            contactService.create(type(kind), form.toData());
        } catch (BusinessRuleException e) {
            fillFormModel(model, kind, form, null);
            model.addAttribute("error", e.displayMessage());
            // Arbitr-024: хато drawer ичида қайта render бўлади
            return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                    ? "contact/formDrawer" : "contact/form";
        }
        if (com.averpo.erp.shared.web.Htmx.isHtmx(request)) {
            return com.averpo.erp.shared.web.Htmx.redirect(request, response,
                    "/" + kind, "message", msg.get("contact.created", form.getDisplayName()));
        }
        redirect.addFlashAttribute("message",
                msg.get("contact.created", form.getDisplayName()));
        return "redirect:/" + kind;
    }

    /**
     * Таҳрир формаси - HTMX'да drawer partial, оддийда тўлиқ саҳифа (fallback).
     *
     * <p>Arbitr-100: {@code ?open=<section>} параметри мос accordion
     * бўлимини (addresses/persons/bank) очиқ render қилади - бўлим
     * қўшиш/ўчириш POST'и шу параметр билан қайтади, акс ҳолда default
     * ёпиқ accordion'да янгидан қўшилган ёзув кўринмай қоларди.
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String kind, @PathVariable UUID id,
                           @RequestParam(required = false) String open, Model model,
                           jakarta.servlet.http.HttpServletRequest request) {
        Contact contact = contactService.get(id);
        fillFormModel(model, kind, ContactForm.from(contact), id);
        model.addAttribute("openSection", open);
        return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                ? "contact/formDrawer" : "contact/form";
    }

    /** Таҳрирни сақлаш. */
    @PostMapping("/{id}")
    public String update(@PathVariable String kind, @PathVariable UUID id,
                         @ModelAttribute ContactForm form,
                         Model model, RedirectAttributes redirect,
                         jakarta.servlet.http.HttpServletRequest request,
                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            contactService.update(id, form.toData(), form.isActive());
        } catch (BusinessRuleException e) {
            fillFormModel(model, kind, form, id);
            model.addAttribute("error", e.displayMessage());
            // Arbitr-024: хато drawer ичида қайта render бўлади
            return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                    ? "contact/formDrawer" : "contact/form";
        }
        if (com.averpo.erp.shared.web.Htmx.isHtmx(request)) {
            return com.averpo.erp.shared.web.Htmx.redirect(request, response,
                    "/" + kind, "message", msg.get("contact.updated", form.getDisplayName()));
        }
        redirect.addFlashAttribute("message",
                msg.get("contact.updated", form.getDisplayName()));
        return "redirect:/" + kind;
    }

    // ---- Манзил / шахс / банк реквизити бўлимлари (фақат таҳрирда) ----

    /**
     * Таҳрир саҳифасига redirect - {@code ?open=<section>} билан, шунда
     * бўлим POST'идан кейин ўша accordion очиқ қайтади (Arbitr-100 2-банд:
     * акс ҳолда redirect ҳамма блокни ёпиб, эндигина қўшилган/ўчирилган
     * ёзув кўринмай қоларди). Валидация хатоси оқими ҳам шу йўлдан ўтади.
     *
     * @param section accordion бўлими - addresses / persons / bank
     */
    private String redirectToEdit(String kind, UUID id, String section) {
        return "redirect:/" + kind + "/" + id + "/edit?open=" + section;
    }

    /** Янги манзил қўшиш - таҳрир саҳифасидаги бўлим формаси. */
    @PostMapping("/{id}/addresses")
    public String addAddress(@PathVariable String kind, @PathVariable UUID id,
                             @RequestParam(required = false) String type,
                             @RequestParam(required = false) String line1,
                             @RequestParam(required = false) String line2,
                             @RequestParam(required = false) String city,
                             @RequestParam(required = false) String region,
                             @RequestParam(required = false) String postalCode,
                             @RequestParam(required = false) String countryCode,
                             @RequestParam(defaultValue = "false") boolean defaultAddress,
                             RedirectAttributes redirect) {
        try {
            contactService.addAddress(id, new ContactService.AddressData(
                    parseAddressType(type), line1, line2, city, region,
                    postalCode, countryCode, defaultAddress));
            redirect.addFlashAttribute("message", msg.get("contact.address.added"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return redirectToEdit(kind, id, "addresses");
    }

    /** Манзилни ўчириш. */
    @PostMapping("/{id}/addresses/{addressId}/delete")
    public String deleteAddress(@PathVariable String kind, @PathVariable UUID id,
                                @PathVariable UUID addressId, RedirectAttributes redirect) {
        contactService.deleteAddress(addressId);
        redirect.addFlashAttribute("message", msg.get("contact.address.deleted"));
        return redirectToEdit(kind, id, "addresses");
    }

    /** Янги масъул шахс қўшиш. */
    @PostMapping("/{id}/persons")
    public String addPerson(@PathVariable String kind, @PathVariable UUID id,
                            @RequestParam(required = false) String fullName,
                            @RequestParam(required = false) String position,
                            @RequestParam(required = false) String phone,
                            @RequestParam(required = false) String email,
                            @RequestParam(defaultValue = "false") boolean primary,
                            RedirectAttributes redirect) {
        try {
            contactService.addPerson(id, new ContactService.PersonData(
                    fullName, position, phone, email, primary));
            redirect.addFlashAttribute("message", msg.get("contact.person.added"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return redirectToEdit(kind, id, "persons");
    }

    /** Шахсни ўчириш. */
    @PostMapping("/{id}/persons/{personId}/delete")
    public String deletePerson(@PathVariable String kind, @PathVariable UUID id,
                               @PathVariable UUID personId, RedirectAttributes redirect) {
        contactService.deletePerson(personId);
        redirect.addFlashAttribute("message", msg.get("contact.person.deleted"));
        return redirectToEdit(kind, id, "persons");
    }

    /** Янги банк реквизити қўшиш. */
    @PostMapping("/{id}/bank-accounts")
    public String addBankAccount(@PathVariable String kind, @PathVariable UUID id,
                                 @RequestParam(required = false) String bankName,
                                 @RequestParam(required = false) String bankCode,
                                 @RequestParam(required = false) String accountNumber,
                                 @RequestParam(required = false) String currency,
                                 @RequestParam(defaultValue = "false") boolean defaultAccount,
                                 RedirectAttributes redirect) {
        try {
            contactService.addBankAccount(id, new ContactService.BankAccountData(
                    bankName, bankCode, accountNumber, currency, defaultAccount));
            redirect.addFlashAttribute("message", msg.get("contact.bank.added"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return redirectToEdit(kind, id, "bank");
    }

    /** Банк реквизитини ўчириш. */
    @PostMapping("/{id}/bank-accounts/{bankAccountId}/delete")
    public String deleteBankAccount(@PathVariable String kind, @PathVariable UUID id,
                                    @PathVariable UUID bankAccountId,
                                    RedirectAttributes redirect) {
        contactService.deleteBankAccount(bankAccountId);
        redirect.addFlashAttribute("message", msg.get("contact.bank.deleted"));
        return redirectToEdit(kind, id, "bank");
    }

    /** Манзил турини парслайди - бўш/бузуқ (tampered) қийматга BR-CON-007. */
    private AddressType parseAddressType(String value) {
        if (value == null || value.isBlank()) {
            return null; // ContactService BR-CON-007 билан рад этади
        }
        try {
            return AddressType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(BusinessRule.BR_CON_007,
                    "Нотўғри манзил тури: " + value);
        }
    }

    /** Форма model'ини тўлдиради; таҳрирда бўлим маълумотлари ҳам юкланади. */
    private void fillFormModel(Model model, String kind, ContactForm form, UUID editId) {
        model.addAttribute("kind", kind);
        model.addAttribute("isCustomer", type(kind) == ContactType.CUSTOMER);
        model.addAttribute("isEmployee", type(kind) == ContactType.EMPLOYEE);
        model.addAttribute("form", form);
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("paymentTerms", paymentTermService.active());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        model.addAttribute("editId", editId == null ? null : editId.toString());
        if (editId != null) {
            model.addAttribute("addresses", contactService.addresses(editId));
            model.addAttribute("persons", contactService.persons(editId));
            model.addAttribute("bankAccounts", contactService.bankAccounts(editId));
            // BR-CON-012 (Arbitr-087): POSTED тарихли контакт валютаси
            // формада қулф - select read-only + сабаб матни
            model.addAttribute("currencyLocked",
                    contactService.isCurrencyLocked(contactService.get(editId)));
        }
    }
}
