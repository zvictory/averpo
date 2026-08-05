package com.averpo.erp.contact;

import com.averpo.erp.contact.domain.AddressType;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.AddressData;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arbitr-100 (контакт пастки блоклари accordion) web render + redirect
 * тестлари.
 *
 * <p>Нима гарантияланади:
 * <ul>
 *   <li>таҳрир саҳифасидаги учала бўлим native {@code <details>} -
 *     default ЁПИҚ ({@code open} атрибути йўқ), summary'да сон белгиси
 *     («Манзиллар (N)»);</li>
 *   <li>{@code ?open=<section>} параметри айнан ўша бўлимни очади,
 *     қолгани ёпиқ;</li>
 *   <li><b>КРИТИК ТУЗОҚ (карта 2-банд)</b>: бўлим қўшиш/ўчириш POST'и
 *     {@code ?open=<section>} билан redirect қилади - шунда default
 *     ёпиқ accordion эндигина ўзгарган бўлимни очиқ кўрсатади;</li>
 *   <li>валидация хатоси оқими ҳам ўша бўлимни очиқ қайтаради (карта
 *     3-банд).</li>
 * </ul>
 *
 * <p>JTE {@code open="${boolean}"} қолипи: false'да атрибут умуман
 * чиқмайди - шунга ёпиқ details очилиш теги class ёпилиши билан ({@code
 * dark:bg-surface-dark">}), очиғи {@code dark:bg-surface-dark" open>}
 * билан тугайди (Arbitr-121: details Penguin card утилиталарида,
 * охирги утилита dark:bg-surface-dark); тест шу икки маркерни фарқлайди.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class ContactAccordionWebTest {

    /** Details ёпилиш теги охири - open атрибути йўқ (default ёпиқ). */
    private static final String CLOSED = "dark:bg-surface-dark\">";

    /** Details ёпилиш теги охири - open атрибути бор (server очган). */
    private static final String OPEN = "dark:bg-surface-dark\" open>";

    @Autowired WebApplicationContext context;
    @Autowired ContactService contactService;

    /** Security filter chain уланган MockMvc (CSRF POST'лар учун ҳам). */
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
    }

    /** Тайёр customer (номи бетакрор - параллел тест изоляцияси). */
    private Contact customer(String name) {
        return contactService.create(ContactType.CUSTOMER, new ContactData(
                name, null, null, null, null, null, null, null, null, null, null));
    }

    /** Битта манзил қўшади (сон белгиси ва default очиқ текширувлари учун). */
    private void addAddress(UUID contactId, String line1) {
        contactService.addAddress(contactId, new AddressData(
                AddressType.BILLING, line1, null, null, null, null, null, false));
    }

    /** Route HTML'ини қайтаради (200 кутилади). */
    private String html(String route) throws Exception {
        return mockMvc.perform(get(route))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Default (open параметрисиз) - учала details ёпиқ (ҳеч бирида open
     * атрибути йўқ) ва summary'да сон белгиси кўринади.
     */
    @Test
    void accordion_closedByDefault_showsCounts() throws Exception {
        Contact c = customer("Accordion default мижоз");
        addAddress(c.getId(), "Тошкент, Амир Темур 1");
        addAddress(c.getId(), "Тошкент, Навоий 10");

        String body = html("/customers/" + c.getId() + "/edit");

        // Ёпиқ details очилиш теги бор, очиқники ЙЎҚ (учаласи ҳам ёпиқ)
        assertThat(body).as("details ёпиқ ҳолатда").contains(CLOSED);
        assertThat(body).as("ҳеч бир details очиқ эмас").doesNotContain(OPEN);

        // Summary сон белгиси - ёпиқ ҳолатда ҳам мазмун беради
        assertThat(body).contains("Манзиллар (2)");
        assertThat(body).contains("Масъул шахслар (0)");
        assertThat(body).contains("Банк реквизитлари (0)");
    }

    /**
     * {@code ?open=addresses} фақат Манзиллар бўлимини очади (open теги
     * Масъул шахслар summary'сидан олдин = айнан addresses блокида),
     * қолган иккиси ёпиқ.
     */
    @Test
    void openParam_addresses_expandsOnlyThatSection() throws Exception {
        Contact c = customer("Accordion open мижоз");
        addAddress(c.getId(), "Очиқ манзил");

        String body = html("/customers/" + c.getId() + "/edit?open=addresses");

        // Камида бир очиқ details бор
        int openPos = body.indexOf(OPEN);
        assertThat(openPos).as("бир details очиқ").isGreaterThan(-1);
        // Очиқ details Манзиллар блокида - Масъул шахслар summary'сидан олдин
        int personsPos = body.indexOf("Масъул шахслар (");
        assertThat(openPos).as("очилган details = Манзиллар").isLessThan(personsPos);
        // Фақат биттаси очиқ (иккинчи OPEN маркер йўқ)
        assertThat(body.indexOf(OPEN, openPos + 1))
                .as("фақат битта details очиқ").isEqualTo(-1);
    }

    /**
     * КРИТИК ТУЗОҚ: манзил қўшиш POST'и {@code ?open=addresses} билан
     * redirect қилади - акс ҳолда redirect ҳамма блокни ёпиб, эндигина
     * қўшилган ёзув кўринмай қоларди (карта 2-банд).
     */
    @Test
    void addAddress_redirectsWithOpenAddresses() throws Exception {
        Contact c = customer("Манзил қўшиш мижоз");

        mockMvc.perform(post("/customers/" + c.getId() + "/addresses")
                        .param("type", "BILLING")
                        .param("line1", "Янги манзил, 5-уй")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/customers/" + c.getId() + "/edit?open=addresses"));

        // Ёзув ҳақиқатан қўшилди (redirect'дан кейин очиқ блокда кўринади)
        assertThat(contactService.addresses(c.getId())).hasSize(1);
    }

    /**
     * Валидация хатоси оқими ҳам ўша бўлимни очиқ қайтаради (карта
     * 3-банд): бўш line1 → BR-CON-007, лекин redirect'да {@code
     * ?open=addresses} сақланади (флеш error + очиқ блок).
     */
    @Test
    void addAddress_validationError_stillOpensAddresses() throws Exception {
        Contact c = customer("Хато манзил мижоз");

        mockMvc.perform(post("/customers/" + c.getId() + "/addresses")
                        .param("type", "BILLING")
                        .param("line1", "   ")           // бўш - BR-CON-007
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/customers/" + c.getId() + "/edit?open=addresses"));

        // Хато туфайли ёзув қўшилмади
        assertThat(contactService.addresses(c.getId())).isEmpty();
    }

    /**
     * Шахс ва банк реквизити POST'лари ҳам мос {@code ?open=<section>}
     * билан redirect қилади - учала бўлим бир хил механизмда.
     */
    @Test
    void addPerson_and_addBank_redirectWithMatchingSection() throws Exception {
        Contact c = customer("Шахс-банк мижоз");

        mockMvc.perform(post("/customers/" + c.getId() + "/persons")
                        .param("fullName", "Алиев Вали").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/customers/" + c.getId() + "/edit?open=persons"));

        mockMvc.perform(post("/customers/" + c.getId() + "/bank-accounts")
                        .param("bankName", "Капиталбанк")
                        .param("accountNumber", "20208000123456789001")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/customers/" + c.getId() + "/edit?open=bank"));
    }

    /**
     * Ўчириш POST'лари ҳам мос бўлимни очиқ қайтаради - қўшишдаги
     * механизмнинг айнан ўзи (deleteAddress намунаси).
     */
    @Test
    void deleteAddress_redirectsWithOpenAddresses() throws Exception {
        Contact c = customer("Манзил ўчириш мижоз");
        addAddress(c.getId(), "Ўчириладиган манзил");
        UUID addressId = contactService.addresses(c.getId()).get(0).getId();

        mockMvc.perform(post("/customers/" + c.getId()
                        + "/addresses/" + addressId + "/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/customers/" + c.getId() + "/edit?open=addresses"));

        assertThat(contactService.addresses(c.getId())).isEmpty();
    }

    /**
     * Ходим (employees) kind'ида ҳам учала accordion бўлими бир хил
     * render бўлади - шаблон битта, ўзгариш ҳам битта жойда (карта
     * 4-банд).
     */
    @Test
    void employees_accordion_rendersAllSections() throws Exception {
        Contact e = contactService.create(ContactType.EMPLOYEE, new ContactData(
                "Accordion ходим", null, null, null, null, null,
                null, null, null, null, null));

        String body = html("/employees/" + e.getId() + "/edit");

        assertThat(body).contains("Манзиллар (0)");
        assertThat(body).contains("Масъул шахслар (0)");
        assertThat(body).contains("Банк реквизитлари (0)");
        // Default ёпиқ ходимда ҳам
        assertThat(body).doesNotContain(OPEN);
    }
}
