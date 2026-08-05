package com.averpo.erp.shared;

import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.ExcelImportService;
import com.averpo.erp.shared.service.ExcelImportService.ImportError;
import com.averpo.erp.shared.service.ExcelImportService.ImportPreview;
import com.averpo.erp.shared.service.ExcelImportService.ImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Excel import - spec: docs/modules/import-excel.md (тестлар 1-5, 7).
 * Web smoke (6) - {@code ImportWebTest}. Каталог (units/tax/currency)
 * Liquibase seed'дан келади; default chart @BeforeEach импорт қилинади
 * (item default счётлари учун).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExcelImportServiceTest {

    @Autowired ExcelImportService importService;
    @Autowired AccountService accountService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;

    /** IMPORT_EXCEL аудит ёзуви текшируви учун (DEC-062). */
    @Autowired AuditEventRepository auditRepository;

    /** Item default счётлари тизим detail type'дан ечилиши учун. */
    @BeforeEach
    void chart() {
        accountService.importDefaultChart();
    }

    /** Тест 1: тоза файл - ҳар туркумдан яратилади, қийматлар тўғри. */
    @Test
    void cleanFile_createsAllCategories() {
        Map<String, List<List<String>>> sheets = new LinkedHashMap<>();
        sheets.put("Контактлар", List.of(
                row("Номи", "Тури", "Телефон", "Email", "ИНН", "Изоҳ"),
                row("Алфа Савдо", "МИЖОЗ", "+998901112233", "alfa@x.uz", "301111111", "Мижоз"),
                row("Бета Таъминот", "таъминотчи", null, null, null, null)));
        sheets.put("Ходимлар", List.of(
                row("Номи", "Телефон", "Ойлик (гросс)", "Изоҳ"),
                row("Валиев Пўлат", "+998900000000", "5000000", "Менежер")));
        sheets.put("Товарлар", List.of(
                row("Номи", "Тури", "Бирлик", "SKU", "Сотув нархи", "Харид нархи", "ҚҚС ставкаси"),
                row("Гамма товар", "ТОВАР", "дона", "G-1", "25000", "20000", "ҚҚС 12%"),
                row("Дельта хизмат", "ХИЗМАТ", null, null, "50000", null, "ҚҚСсиз")));
        sheets.put("Омборлар", List.of(
                row("Номи", "Код"),
                row("Жасур омбори", "OMB-9")));
        sheets.put("Счётлар", List.of(
                row("Номи", "Тури", "Валюта", "Код"),
                row("Тижорат банки", "БАНК", "UZS", "9110"),
                row("Кичик касса", "КАССА", null, "9010")));

        ImportPreview preview = importService.parse(file("clean.xlsx", sheets));
        assertThat(preview.hasErrors()).isFalse();

        ImportResult result = importService.apply(preview);
        assertThat(result.contactsCreated()).isEqualTo(2);
        assertThat(result.employeesCreated()).isEqualTo(1);
        assertThat(result.itemsCreated()).isEqualTo(2);
        assertThat(result.warehousesCreated()).isEqualTo(1);
        assertThat(result.accountsCreated()).isEqualTo(2);
        assertThat(result.totalSkipped()).isZero();

        // Ходим ойлиги (гросс) сақланди
        Contact employee = contactService.byType(ContactType.EMPLOYEE, true).stream()
                .filter(c -> c.getDisplayName().equals("Валиев Пўлат")).findFirst().orElseThrow();
        assertThat(employee.getMonthlySalary()).isEqualByComparingTo("5000000");

        // Счёт тури ва валютаси
        List<Account> accounts = accountService.all();
        Account bank = accounts.stream().filter(a -> a.getName().equals("Тижорат банки"))
                .findFirst().orElseThrow();
        assertThat(bank.getDetailType()).isEqualTo(AccountDetailType.CHECKING);
        assertThat(bank.getCurrency().getCode()).isEqualTo("UZS");
        Account cash = accounts.stream().filter(a -> a.getName().equals("Кичик касса"))
                .findFirst().orElseThrow();
        assertThat(cash.getDetailType()).isEqualTo(AccountDetailType.CASH_ON_HAND);

        // Товар бирлиги ва ҚҚС ставкаси каталогдан ечилди
        Item goods = itemService.list(null, true).stream()
                .filter(i -> i.getName().equals("Гамма товар")).findFirst().orElseThrow();
        assertThat(goods.getUnit().getName()).isEqualTo("дона");
        assertThat(goods.getSalesTaxRateId()).isNotNull();

        // Аудит (DEC-062): apply IMPORT_EXCEL ёзуви - details'да
        // туркумлаб сонлар (spec намунаси кўриниши)
        var importEvents = auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.IMPORT_EXCEL).toList();
        assertThat(importEvents).hasSize(1);
        assertThat(importEvents.get(0).getDetails()).isEqualTo(
                "яратилди: 2 контакт, 1 ходим, 2 товар, 1 омбор, 2 счёт; ўтказилди: 0");
    }

    /**
     * Тест 2: хатоли сатр - ҳеч нарса ёзилмайди, хато сатр рақами билан.
     * DEC-073 билан кенгайган: манфий Ойлик/нарх ва тизимда банд омбор/
     * счёт коди ҳам parse рўйхатида (apply умуман юрмайди); катта-кичик
     * фарқли «ДОНА» энди хато ЭМАС (каталог lookup case-insensitive).
     */
    @Test
    void errorRow_nothingWritten() {
        // Банд счёт коди сценарийси учун олдиндан кодли счёт (default
        // chart кодсиз келади)
        accountService.create("Жасур банд кодли счёт", AccountDetailType.CHECKING,
                "9450", null, null, true, null);

        Map<String, List<List<String>>> sheets = new LinkedHashMap<>();
        sheets.put("Контактлар", List.of(
                row("Номи", "Тури", "Телефон", "Email", "ИНН", "Изоҳ"),
                row("Тўғри Мижоз", "МИЖОЗ", null, null, null, null),
                row("Нотўғри Тур", "КЛИЕНТ", null, null, null, null)));
        sheets.put("Ходимлар", List.of(
                row("Номи", "Телефон", "Ойлик (гросс)", "Изоҳ"),
                row("Жасур Манфий Ходим", null, "-100000", null)));
        sheets.put("Товарлар", List.of(
                row("Номи", "Тури", "Бирлик", "SKU", "Сотув нархи", "Харид нархи", "ҚҚС ставкаси"),
                row("Жасур Манфий Товар", "ТОВАР", "ДОНА", null, "-5000", "1000", null)));
        sheets.put("Омборлар", List.of(
                row("Номи", "Код"),
                row("Жасур Банд Код Омбор", "main")));
        sheets.put("Счётлар", List.of(
                row("Номи", "Тури", "Валюта", "Код"),
                row("Жасур Янги Счёт", "БАНК", null, "9450")));

        ImportPreview preview = importService.parse(file("bad.xlsx", sheets));
        assertThat(preview.hasErrors()).isTrue();
        // Хато айнан 3-сатрда (нотанилган тур)
        assertThat(preview.errors()).anySatisfy(e -> {
            assertThat(e.sheet()).isEqualTo("Контактлар");
            assertThat(e.row()).isEqualTo(3);
        });
        // Манфий Ойлик - варақ/сатр контексти билан (BA-045)
        assertThat(preview.errors()).anySatisfy(e -> {
            assertThat(e.sheet()).isEqualTo("Ходимлар");
            assertThat(e.row()).isEqualTo(2);
            assertThat(e.message()).contains("манфий");
        });
        // Манфий Сотув нархи ҳам (айни сатрдаги «ДОНА» бирлиги ХАТО ЭМАС)
        assertThat(preview.errors()).anySatisfy(e -> {
            assertThat(e.sheet()).isEqualTo("Товарлар");
            assertThat(e.row()).isEqualTo(2);
            assertThat(e.message()).contains("манфий");
        });
        assertThat(preview.errors()).noneSatisfy(e ->
                assertThat(e.message()).contains("каталогда йўқ"));
        // Тизимда банд омбор коди - 'main' seed MAIN'га катта-кичик
        // фарқсиз урилади (BA-051)
        assertThat(preview.errors()).anySatisfy(e -> {
            assertThat(e.sheet()).isEqualTo("Омборлар");
            assertThat(e.row()).isEqualTo(2);
            assertThat(e.message()).contains("банд");
        });
        // Тизимда банд счёт коди
        assertThat(preview.errors()).anySatisfy(e -> {
            assertThat(e.sheet()).isEqualTo("Счётлар");
            assertThat(e.row()).isEqualTo(2);
            assertThat(e.message()).contains("банд");
        });

        // Хато бор - apply умуман чақирилмайди (controller нақши): ҳеч
        // нарса ёзилмаганини тасдиқлаймиз (тўғри сатр ҳам эмас)
        assertThat(contactService.byType(ContactType.CUSTOMER, true)).isEmpty();
        assertThat(contactService.byType(ContactType.EMPLOYEE, true)).isEmpty();
        assertThat(itemService.list(null, true)).isEmpty();
    }

    /** Тест 3: дубликат - файл ичи хато; базада мавжуд ўтказилади. */
    @Test
    void duplicates_inFileError_dbExistingSkipped() {
        // (а) файл ичида такрор ном - хато
        Map<String, List<List<String>>> dup = new LinkedHashMap<>();
        dup.put("Контактлар", List.of(
                row("Номи", "Тури", "Телефон", "Email", "ИНН", "Изоҳ"),
                row("Такрор Ном", "МИЖОЗ", null, null, null, null),
                row("Такрор Ном", "ТАЪМИНОТЧИ", null, null, null, null)));
        ImportPreview dupPreview = importService.parse(file("dup.xlsx", dup));
        assertThat(dupPreview.errors()).anySatisfy(e ->
                assertThat(e.message()).contains("такрорланган"));

        // (б) базада мавжуд ном - хато эмас, ўтказилади
        contactService.create(ContactType.CUSTOMER, new ContactData(
                "Мавжуд Мижоз", null, null, null, null, null, null, null, null, null, null));
        Map<String, List<List<String>>> mixed = new LinkedHashMap<>();
        mixed.put("Контактлар", List.of(
                row("Номи", "Тури", "Телефон", "Email", "ИНН", "Изоҳ"),
                row("Мавжуд Мижоз", "МИЖОЗ", null, null, null, null),
                row("Янги Мижоз", "МИЖОЗ", null, null, null, null)));
        ImportPreview mixedPreview = importService.parse(file("mixed.xlsx", mixed));
        assertThat(mixedPreview.hasErrors()).isFalse();
        ImportResult result = importService.apply(mixedPreview);
        assertThat(result.contactsCreated()).isEqualTo(1);
        assertThat(result.contactsSkipped()).isEqualTo(1);
    }

    /** Тест 4: нотанилган варақли файл - тушунарли хато. */
    @Test
    void unrecognizedSheet_understandableError() {
        Map<String, List<List<String>>> sheets = new LinkedHashMap<>();
        sheets.put("Йўриқнома", List.of(row("Матн"), row("бирор изоҳ")));

        ImportPreview preview = importService.parse(file("guide-only.xlsx", sheets));
        assertThat(preview.hasErrors()).isTrue();
        assertThat(preview.errors()).anySatisfy(e ->
                assertThat(e.message()).contains("танилган варақ"));
    }

    /** Тест 5: бирлик каталогда топилмади (BR-IMP-005). */
    @Test
    void unitNotFound_isImp005() {
        Map<String, List<List<String>>> sheets = new LinkedHashMap<>();
        sheets.put("Товарлар", List.of(
                row("Номи", "Тури", "Бирлик", "SKU", "Сотув нархи", "Харид нархи", "ҚҚС ставкаси"),
                row("Ноаниқ бирлик товар", "ТОВАР", "йўқбирлик", null, null, null, null)));

        ImportPreview preview = importService.parse(file("badunit.xlsx", sheets));
        assertThat(preview.hasErrors()).isTrue();
        assertThat(preview.errors()).anySatisfy(e -> {
            assertThat(e.sheet()).isEqualTo("Товарлар");
            assertThat(e.message()).contains("Бирлик").contains("каталогда йўқ");
        });
    }

    /**
     * DEC-073 (BA-050): бирлик/ҚҚС номи катта-кичик фарқсиз
     * каталогдан ечилади - «ДОНА»/«ққс 12%» тоза парс бўлиб apply'да
     * тўғри каталог ёзувига боғланади.
     */
    @Test
    void caseVariantCatalogNames_resolve() {
        Map<String, List<List<String>>> sheets = new LinkedHashMap<>();
        sheets.put("Товарлар", List.of(
                row("Номи", "Тури", "Бирлик", "SKU", "Сотув нархи", "Харид нархи", "ҚҚС ставкаси"),
                row("Жасур Кейс Товар", "ТОВАР", "ДОНА", null, "1000", "800", "ққс 12%")));

        ImportPreview preview = importService.parse(file("case.xlsx", sheets));
        assertThat(preview.hasErrors()).isFalse();
        importService.apply(preview);

        Item item = itemService.list(null, true).stream()
                .filter(i -> i.getName().equals("Жасур Кейс Товар")).findFirst().orElseThrow();
        assertThat(item.getUnit().getName()).isEqualTo("дона");
        assertThat(item.getSalesTaxRateId()).isNotNull();
    }

    /**
     * TST-052: BR-IMP-001 - requireValidFile учала тармоғи (бўш файл,
     * 5MB'дан катта, кенгайтма .xlsx эмас) айнан код билан рад этилади.
     */
    @Test
    void invalidFile_isImp001() {
        String mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        assertThatThrownBy(() -> importService.parse(
                new MockMultipartFile("file", "bosh.xlsx", mime, new byte[0])))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-IMP-001"));

        byte[] big = new byte[5 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> importService.parse(
                new MockMultipartFile("file", "katta.xlsx", mime, big)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-IMP-001"));

        assertThatThrownBy(() -> importService.parse(
                new MockMultipartFile("file", "jadval.csv", "text/csv",
                        "Номи;Тури".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-IMP-001"));
    }

    /**
     * TST-052: BR-IMP-004 - 2000 сатрдан кўп варақ. Exception эмас,
     * варақ даражасидаги хато PREVIEW рўйхатида (спец: хатолар битта
     * жавобда тўлиқ рўйхат) ва варақ типлаштирилмай ўтказилади.
     */
    @Test
    void tooManyRows_isImp004() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("Номи"));
        for (int i = 1; i <= 2001; i++) {
            rows.add(row("Жасур кўп омбор " + i));
        }
        Map<String, List<List<String>>> sheets = new LinkedHashMap<>();
        sheets.put("Омборлар", rows);

        ImportPreview preview = importService.parse(file("kop.xlsx", sheets));
        assertThat(preview.hasErrors()).isTrue();
        assertThat(preview.errors()).anySatisfy(e -> {
            assertThat(e.sheet()).isEqualTo("Омборлар");
            assertThat(e.message()).contains("2000");
        });
        assertThat(preview.warehouses()).isEmpty();
    }

    /** Тест 7: тайёр шаблон (арбитр) - round-trip, парс + apply. */
    @Test
    void templateRoundTrip() throws IOException {
        MultipartFile template = new MockMultipartFile("file", "import-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ClassPathResource("static/import-template.xlsx").getInputStream());

        ImportPreview preview = importService.parse(template);
        assertThat(preview.hasErrors()).as("шаблон тоза парс бўлиши шарт").isFalse();

        ImportResult result = importService.apply(preview);
        // Шаблонда ҳар варақда 2 намуна сатр. Ном seed билан тўқнашса
        // ўтказилади (масалан «Касса» default chart'да, «Асосий омбор»
        // 017-inventory seed'да) - шу боис created+skipped=2 деб текширамиз
        // (seed'га боғлиқ бўлмаган мустаҳкам assert). Жами 10 сатр ҳисобга
        // олинади: тартибсиз тушиб қолмайди
        assertThat(result.contactsCreated() + result.contactsSkipped()).isEqualTo(2);
        assertThat(result.employeesCreated() + result.employeesSkipped()).isEqualTo(2);
        assertThat(result.itemsCreated() + result.itemsSkipped()).isEqualTo(2);
        assertThat(result.warehousesCreated() + result.warehousesSkipped()).isEqualTo(2);
        assertThat(result.accountsCreated() + result.accountsSkipped()).isEqualTo(2);
        assertThat(result.totalCreated() + result.totalSkipped()).isEqualTo(10);
        // Камида бир нарса аниқ яратилди (тоза round-trip - фақат skip эмас)
        assertThat(result.totalCreated()).isPositive();

        // Идемпотентлик: айни шаблонни қайта apply - энди ҳаммаси ўтказилади
        ImportResult second = importService.apply(importService.parse(template2()));
        assertThat(second.totalCreated()).isZero();
        assertThat(second.totalSkipped()).isEqualTo(10);
    }

    // ---- ёрдамчилар ----

    /** Шаблонни иккинчи марта ўқиш учун (stream бир марта ишлатилади). */
    private MultipartFile template2() throws IOException {
        return new MockMultipartFile("file", "import-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ClassPathResource("static/import-template.xlsx").getInputStream());
    }

    /** null катакларга йўл қўядиган сатр (List.of null'ни рад қилади). */
    private static List<String> row(String... cells) {
        return Arrays.asList(cells);
    }

    /** Варақ хариталаридан xlsx MultipartFile ясайди (STRING катаклар). */
    private MultipartFile file(String filename, Map<String, List<List<String>>> sheets) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            sheets.forEach((sheetName, rows) -> {
                Sheet sheet = wb.createSheet(sheetName);
                for (int r = 0; r < rows.size(); r++) {
                    Row row = sheet.createRow(r);
                    List<String> cells = rows.get(r);
                    for (int c = 0; c < cells.size(); c++) {
                        String v = cells.get(c);
                        if (v != null) {
                            row.createCell(c).setCellValue(v);
                        }
                    }
                }
            });
            wb.write(bos);
            return new MockMultipartFile("file", filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
