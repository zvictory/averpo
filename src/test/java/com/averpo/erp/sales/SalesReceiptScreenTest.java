package com.averpo.erp.sales;

import com.averpo.erp.ledger.service.AccountService;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сотув чеки экранлари смок (24-банд «смок экранлар»): рўйхат ва FULL
 * форма 200 қайтариб JTE шаблон белги матни билан render бўлиши. Форма -
 * тўлов счёти жонли Balance select'и (data-bal) бор.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class SalesReceiptScreenTest {

    @Autowired WebApplicationContext context;
    @Autowired AccountService accountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void list_rendersWithTitle() throws Exception {
        mockMvc.perform(get("/sales-receipts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Сотув чеклари")));
    }

    @Test
    void newForm_rendersBankSelectWithLiveBalance() throws Exception {
        // FULL форма: тўлов счёти select + жонли Balance data-bal белгиси
        mockMvc.perform(get("/sales-receipts/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Тўлов счёти")))
                .andExpect(content().string(containsString("data-bal")));
    }
}
