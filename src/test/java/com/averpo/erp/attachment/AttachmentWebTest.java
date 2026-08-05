package com.averpo.erp.attachment;

import com.averpo.erp.attachment.domain.Attachment;
import com.averpo.erp.attachment.service.AttachmentService;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.Estimate;
import com.averpo.erp.sales.service.EstimateService;
import com.averpo.erp.sales.service.EstimateService.EstimateData;
import com.averpo.erp.sales.service.EstimateService.LineData;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.testsupport.TestRoles;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Attachment web қатлами тестлари (docs/modules/attachments.md 5-банд:
 * роллар BR-ATT-004) + HTTP юклаш/юклаб олиш оқими. Роль ҳимояси
 * SecurityConfig'даги {@code POST /**} қоидасидан келади (attachment
 * контроллерида алоҳида текширув йўқ) - шу қоида айнан шу endpoint'ларда
 * VIEWER'ни тўсишини тасдиқлайди.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class AttachmentWebTest {

    /** Барча fixture санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired WebApplicationContext context;
    @Autowired AttachmentService attachmentService;
    @Autowired EstimateService estimateService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired com.averpo.erp.tax.service.TaxRateService taxRateService;

    private MockMvc mockMvc;

    /** Илова бириктириладиган target ҳужжат. */
    private Estimate target;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        accountService.importDefaultChart();
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Илова web мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        Item service = itemService.create(ItemType.SERVICE, new ItemData(
                "Илова web хизмати", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        var vat = taxRateService.create("VATATTW", "ҚҚС (attw)", new BigDecimal("12"));
        target = estimateService.create(new EstimateData(customer.getId(), DATE,
                DATE.plusDays(30), null, null, "target", false,
                List.of(new LineData(service.getId(), BigDecimal.ONE,
                        new BigDecimal("1000"), null, vat.getId(), null))));
    }

    /** pdf файл ясаш ёрдамчиси. */
    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "salom".getBytes());
    }

    /**
     * Spec 5-банд (BR-ATT-004): VIEWER юклай/ўчира олмайди (403), лекин
     * рўйхатни кўради ва юклаб олади (200). ADMIN/ACCOUNTANT эса юклайди.
     */
    @Test
    void viewer_uploadDeleteForbidden_viewDownloadAllowed() throws Exception {
        // Мавжуд илова (ADMIN контекстда сервис орқали - delete гарови учун)
        Attachment existing = attachmentService.upload(DocumentType.ESTIMATE,
                target.getId(), pdf("mavjud.pdf"));

        // VIEWER юклаш → 403 (csrf берилган, демак роль тўсиғи)
        mockMvc.perform(multipart("/attachments/ESTIMATE/" + target.getId())
                        .file(pdf("yangi.pdf")).with(csrf())
                        .with(TestRoles.as("kuzatuvchi", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isForbidden());

        // VIEWER ўчириш → 403
        mockMvc.perform(post("/attachments/" + existing.getId() + "/delete").with(csrf())
                        .with(TestRoles.as("kuzatuvchi", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isForbidden());

        // VIEWER бўлимни кўради (200), лекин юклаш тугмаси йўқ (canEdit=false)
        mockMvc.perform(get("/attachments/ESTIMATE/" + target.getId())
                        .with(TestRoles.as("kuzatuvchi", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Иловалар")))
                .andExpect(content().string(containsString("mavjud.pdf")))
                .andExpect(content().string(not(containsString("Юклаш"))));

        // VIEWER юклаб олади (200) - GET очиқ
        mockMvc.perform(get("/attachments/" + existing.getId() + "/download")
                        .with(TestRoles.as("kuzatuvchi", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("mavjud.pdf")));

        // VIEWER жойида кўради (200) - view ҳам download каби GET, очиқ
        // (ARBITR-094: read-only роль иловани кўра олади)
        mockMvc.perform(get("/attachments/" + existing.getId() + "/view")
                        .with(TestRoles.as("kuzatuvchi", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    /**
     * ARBITR-094 хавфсизлик: view endpoint ФАҚАТ whitelist турни inline
     * беради. (1) PDF - inline + nosniff; (2) сақланган MIME image/svg+xml
     * (кенгайтма рухсат этилган png, лекин browser svg+xml юборган) inline
     * ЭМАС - download'га 302 (SVG ичида script → XSS). Бу view'нинг
     * ҳақиқат манбаи эканини (UI тугмасидан мустақил) тасдиқлайди.
     *
     * <p>ARBITR-128: жавобда X-Frame-Options: SAMEORIGIN ҳам текширилади -
     * default DENY бўлса модалдаги PDF iframe'ни браузернинг ўзи блоклайди
     * (SecurityConfig frameOptions sameOrigin шу тестга боғланган).
     */
    @Test
    void view_pdfInlineWithNosniff_svgContentTypeRedirectsToDownload() throws Exception {
        // PDF - хавфсиз: inline кўрсатилади + nosniff header
        Attachment pdfAtt = attachmentService.upload(DocumentType.ESTIMATE,
                target.getId(), pdf("hisob.pdf"));
        mockMvc.perform(get("/attachments/" + pdfAtt.getId() + "/view").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Disposition", containsString("hisob.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Content-Type", containsString("application/pdf")));

        // Кенгайтма png (BR-ATT-002 ўтади), лекин сақланган MIME svg+xml -
        // inline ТАҚИҚ, download'га 302 йўналтирилади
        Attachment svgAtt = attachmentService.upload(DocumentType.ESTIMATE, target.getId(),
                new MockMultipartFile("file", "rasm.png", "image/svg+xml",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()));
        mockMvc.perform(get("/attachments/" + svgAtt.getId() + "/view").with(csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/download")));
    }

    /**
     * HTTP юклаш оқими (spec 1-банднинг web томони): ADMIN multipart POST
     * → 200 + янгиланган бўлим фрагментида файл номи; кейин юклаб олиш
     * асл ном билан қайтади.
     */
    @Test
    void admin_uploadViaHttp_returnsSectionFragment_thenDownload() throws Exception {
        mockMvc.perform(multipart("/attachments/ESTIMATE/" + target.getId())
                        .file(pdf("kvitansiya.pdf")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Иловалар")))
                .andExpect(content().string(containsString("kvitansiya.pdf")))
                .andExpect(content().string(containsString("Юклаш")));

        Attachment saved = attachmentService.list(DocumentType.ESTIMATE, target.getId())
                .get(0);
        mockMvc.perform(get("/attachments/" + saved.getId() + "/download").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("kvitansiya.pdf")))
                .andExpect(content().string("salom"));
    }
}
