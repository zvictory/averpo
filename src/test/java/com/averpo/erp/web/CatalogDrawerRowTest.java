package com.averpo.erp.web;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arbitr-037 (+ Arbitr-002 янгиланиши): каталог рўйхатларида қатор
 * босилса ⋮ «Таҳрир» билан бир хил ўнг drawer'да очилиши. Смок:
 * қатор data-drawer белгисини олади (T0 handler htmx.ajax → #drawerBody),
 * edit route HX-Request'да drawer partial, HX'сиз тўлиқ саҳифа (fallback).
 *
 * <p>Arbitr-002 дан кейин drawer хулқи ХОДИМ (employee) қаторида қолди;
 * МИЖОЗ/ТАЪМИНОТЧИ қатори эса энди КОНТАКТ КАРТОЧКАСига (кўриш) боради -
 * шу файлда иккиси ҳам текширилади.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class CatalogDrawerRowTest {

    @Autowired WebApplicationContext context;
    @Autowired ContactService contactService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private Contact customer(String name) {
        return contactService.create(ContactType.CUSTOMER, new ContactData(
                name, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void employeesRoute_rendersEmployeeList() throws Exception {
        // Payroll 23а: /employees kind (ContactController) - «Ходимлар»
        // сарлавҳа (3-йўлли title) ва ходим қатори renders
        contactService.create(ContactType.EMPLOYEE, new ContactData(
                "Ходим Смок", null, null, null, null, null, null, null, null, null, null));
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ходимлар")))
                .andExpect(content().string(containsString("Ходим Смок")));
    }

    @Test
    void employeeRow_carriesDataDrawerAndEditHref() throws Exception {
        // Arbitr-002: мижоз/таъминотчи қатори ЭНДИ карточкага (drawer эмас,
        // ContactCardControllerTest'да текширилади); ходим қатори эса эски
        // drawer таҳрир хулқини сақлайди (ходим картаси 2-босқич)
        Contact e = contactService.create(ContactType.EMPLOYEE, new ContactData(
                "Дроер ходим", null, null, null, null, null, null, null, null, null, null));
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-drawer")))
                .andExpect(content().string(containsString("/employees/" + e.getId() + "/edit")));
    }

    @Test
    void customerRow_pointsToContactCard_noDrawer() throws Exception {
        Contact c = customer("Карточка мижоз");
        // Arbitr-002: мижоз қатори карточка кўриш саҳифасига (data-drawer'сиз
        // → T0 handler тўлиқ навигация қилади, drawer эмас)
        mockMvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-href=\"/customers/" + c.getId() + "\"")));
    }

    @Test
    void editRoute_hxRequest_returnsDrawerPartial() throws Exception {
        Contact c = customer("Дроер таҳрир");
        // HX-Request → drawer partial (drawer-body чроми), тўлиқ layout эмас.
        // Arbitr-121: drawer-head класси Penguin утилиталарга ўтган -
        // барқарор маркер drawer-body (компенсация селектори учун сақланган)
        mockMvc.perform(get("/customers/" + c.getId() + "/edit").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("drawer-body")));
    }

    @Test
    void editRoute_withoutHx_returnsFullPageFallback() throws Exception {
        Contact c = customer("Дроер тўлиқ");
        // HX'сиз → тўлиқ саҳифа (drawer partial чроми йўқ) - JS/htmx'сиз
        // fallback. Тўлиқ саҳифада ҳам layout drawer'и (#drawerBody) бор -
        // маркер PARTIAL'нинг ✕ тугмаси (drawerClose onclick'и partial
        // drawer-head'ида, тўлиқ саҳифа формасида йўқ)
        mockMvc.perform(get("/customers/" + c.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("onclick=\"drawerClose()\" aria-label"))))
                .andExpect(content().string(containsString("Дроер тўлиқ")));
    }
}
