package com.averpo.erp.ledger;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Statement экрани смок (25-банд «Тестлар» - оддий + print кўриниши
 * render): мижоз танланмаса форма, танланса кўчирма жадвали + shared
 * print қатлами (.no-print/.print-only + «Чоп этиш» тугмаси)
 * маркировкаси чиқади.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class StatementWebTest {

    @Autowired WebApplicationContext context;
    @Autowired InvoiceService invoiceService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private MockMvc mockMvc;

    /** Кўчирма ҳисобланадиган мижоз (July 2026 да POSTED invoice'и бор). */
    private Contact customer;

    /** POSTED invoice - Arbitr-063 href асserti учун id сақланади. */
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Кўчирма веб мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        Item service = itemService.create(ItemType.SERVICE, new ItemData(
                "Кўчирма веб хизмати", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        Invoice draft = invoiceService.createDraft(new InvoiceData(customer.getId(),
                LocalDate.of(2026, 7, 5), null, null, null, null,
                List.of(new InvoiceService.LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("50000"), null, null))));
        invoice = invoiceService.post(draft.getId());
        // raw SQL (StatementService) JPA ёзувларини кўрсин - тест tx ичида
        // Hibernate auto-flush JdbcClient'да ишламайди (prod'да алоҳида tx)
        entityManager.flush();
    }

    /** Мижоз танланмаса - форма + мижоз select'да мижоз номи. */
    @Test
    void noCustomer_rendersForm() throws Exception {
        mockMvc.perform(get("/reports/statement"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Мижоз кўчирмаси")))
                .andExpect(content().string(containsString("Кўчирма учун мижоз танланг")))
                .andExpect(content().string(containsString("Кўчирма веб мижози")));
    }

    /** Мижоз танланса - кўчирма жадвали (боши/охири) + print қатлами. */
    @Test
    void withCustomer_rendersStatementAndPrintLayer() throws Exception {
        mockMvc.perform(get("/reports/statement")
                        .param("customerId", customer.getId().toString())
                        .param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Давр боши қолдиқ")))
                .andExpect(content().string(containsString("Давр охири қолдиқ")))
                .andExpect(content().string(containsString("INV-2026")))
                // Arbitr-063 банд 4: сатр рақами манба ҳужжатга линк
                .andExpect(content().string(containsString("/invoices/" + invoice.getId())))
                .andExpect(content().string(containsString("Чоп этиш")))
                // Print қатлами Tailwind print: утилиталарида (Arbitr-122):
                // чоп-фақат блок ва экран-фақат қисм маркерлари
                .andExpect(content().string(containsString("hidden print:block")))
                .andExpect(content().string(containsString("print:hidden")));
    }
}
