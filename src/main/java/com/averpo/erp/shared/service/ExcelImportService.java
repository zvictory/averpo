package com.averpo.erp.shared.service;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.domain.Unit;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.DefaultAccounts;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.web.FormParsers;
import com.averpo.erp.tax.domain.TaxRate;
import com.averpo.erp.tax.service.TaxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Excel'дан бошланғич import (docs/modules/import-excel.md, 1-босқич).
 *
 * <p>Янги компания БИТТА .xlsx файл билан минимал маълумотни киритади:
 * контактлар, ходимлар, товарлар, омборлар ва пул счётлари (банк/касса).
 * Эталон - QBO Import data, лекин бир кўп-варақли файл (битта қадам).
 *
 * <p>Икки босқич: {@link #parse(MultipartFile)} файлни СОФ ўқийди
 * (базага ёзмайди) ва хатоларни ТЎЛИҚ рўйхат қилиб қайтаради -
 * биринчи хатода тўхтамайди, фойдаланувчи бир юришда ҳаммасини тузатади.
 * {@link #apply(ImportPreview)} эса @Transactional: ҳаммаси-ёки-ҳеч-нарса.
 * Ёзиш ФАҚАТ public service'лар орқали (ТЕМИР ҚОИДА №6:
 * repository'ларга тегилмайди); GL'га тегмайди (PostingService умуман
 * чақирилмайди - 1-босқич).
 *
 * <p>Идемпотентлик: базада мавжуд ном хато ЭМАС - сатр ЎТКАЗИЛАДИ. Шу
 * сабаб apply() олдин мавжуд номларни (public read орқали) тўплаб, фақат
 * янгиларини яратади: мавжудга {@code create()} умуман чақирилмайди -
 * бу «marked rollback-only» тузоғидан ҳам сақлайди (BR-*-банд хатоси
 * @Transactional service ичида отилса, чақирувчи уни тутса ҳам
 * транзакция rollback'га белгиланиб қоларди).
 */
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    /** Файл ҳажми чегараси - BR-IMP-001 (5MB). */
    static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Ҳар варақдаги маълумот сатрлари чегараси - BR-IMP-004. */
    static final int MAX_ROWS = 2000;

    // Варақ номлари (шаблон - арбитр 2026-07-09; рўйхатдан ташқари варақ,
    // масалан «Йўриқнома», индамай ўтказилади)
    private static final String SHEET_CONTACTS = "Контактлар";
    private static final String SHEET_EMPLOYEES = "Ходимлар";
    private static final String SHEET_ITEMS = "Товарлар";
    private static final String SHEET_WAREHOUSES = "Омборлар";
    private static final String SHEET_ACCOUNTS = "Счётлар";

    /** Файл даражасидаги хато учун «варақ» ўрни (танилган варақ йўқ). */
    private static final String SHEET_FILE = "(файл)";

    // Устун сарлавҳалари (1-қатор, айнан шу матн - шаблон билан мос)
    private static final String COL_NAME = "Номи";
    private static final String COL_TYPE = "Тури";
    private static final String COL_PHONE = "Телефон";
    private static final String COL_EMAIL = "Email";
    private static final String COL_TAXID = "ИНН";
    private static final String COL_NOTES = "Изоҳ";
    private static final String COL_SALARY = "Ойлик (гросс)";
    private static final String COL_UNIT = "Бирлик";
    private static final String COL_SKU = "SKU";
    private static final String COL_SALES_PRICE = "Сотув нархи";
    private static final String COL_PURCHASE_COST = "Харид нархи";
    private static final String COL_TAX = "ҚҚС ставкаси";
    private static final String COL_CURRENCY = "Валюта";
    private static final String COL_CODE = "Код";

    /** Контактлар public API (ёзиш нуқтаси). */
    private final ContactService contactService;

    /** Товар/хизмат public API. */
    private final ItemService itemService;

    /** Омборлар public API. */
    private final WarehouseService warehouseService;

    /** Пул счётлари public API. */
    private final AccountService accountService;

    /** Бирлик каталоги - ном бўйича lookup (Товарлар Бирлик устуни). */
    private final UnitService unitService;

    /** ҚҚС каталоги - ном бўйича lookup (Товарлар ҚҚС ставкаси устуни). */
    private final TaxRateService taxRateService;

    /** Валюта каталоги - ISO код бўйича lookup (Счётлар Валюта устуни). */
    private final CurrencyService currencyService;

    /**
     * Аудит event'и учун (Arbitr-062): shared audit'ни import қила олмайди
     * (цикл), шунга apply ўз event'ини эълон қилади - синхрон listener
     * IMPORT_EXCEL ёзувини apply транзакциясида киритади.
     */
    private final ApplicationEventPublisher eventPublisher;

    // ---- натижа/оралиқ моделлар ----

    /**
     * Битта import хатоси: қайси варақ, қайси сатр, тушунтириш. row&le;0 -
     * файл/варақ даражасидаги хато (аниқ сатрга боғланмаган).
     */
    public record ImportError(String sheet, int row, String message) { }

    /** Контакт сатри (парслангандан кейин). */
    private record ContactRow(int rowNo, String name, ContactType type, String phone,
                              String email, String taxId, String notes) { }

    /** Ходим сатри - ContactType.EMPLOYEE яратилади. */
    private record EmployeeRow(int rowNo, String name, String phone,
                               BigDecimal salary, String notes) { }

    /** Товар/хизмат сатри - unitId/taxRateId каталогдан ечилган. */
    private record ItemRow(int rowNo, String name, ItemType type, UUID unitId,
                           String sku, BigDecimal salesPrice, BigDecimal purchaseCost,
                           UUID taxRateId) { }

    /** Омбор сатри. */
    private record WarehouseRow(int rowNo, String name, String code) { }

    /** Пул счёти сатри - currency ISO код (ёки null = home). */
    private record AccountRow(int rowNo, String name, AccountDetailType detailType,
                              String currency, String code) { }

    /**
     * Ўқиш натижаси: типлаштирилган сатрлар + хатолар. Хато бўлса
     * {@link #apply} умуман чақирилмайди (controller текширади).
     */
    public record ImportPreview(List<ContactRow> contacts, List<EmployeeRow> employees,
                                List<ItemRow> items, List<WarehouseRow> warehouses,
                                List<AccountRow> accounts, List<ImportError> errors) {
        /** Хато борми - controller apply'дан олдин шуни текширади. */
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    /**
     * Import якуни: ҳар туркумдан нечта яратилди ва нечтаси мавжудлиги
     * учун ўтказилди. Экранда жами кўрсатилади, тестда туркумлаб текширилади.
     */
    public record ImportResult(int contactsCreated, int contactsSkipped,
                               int employeesCreated, int employeesSkipped,
                               int itemsCreated, int itemsSkipped,
                               int warehousesCreated, int warehousesSkipped,
                               int accountsCreated, int accountsSkipped) {
        /** Жами яратилган ёзувлар. */
        public int totalCreated() {
            return contactsCreated + employeesCreated + itemsCreated
                    + warehousesCreated + accountsCreated;
        }

        /** Жами ўтказилган (мавжуд) ёзувлар. */
        public int totalSkipped() {
            return contactsSkipped + employeesSkipped + itemsSkipped
                    + warehousesSkipped + accountsSkipped;
        }
    }

    // ---- 1-босқич: ЎҚИШ ----

    /**
     * Файлни ўқиб типлаштиради ва хатоларни тўплайди - базага ёзмайди.
     *
     * @throws BusinessRuleException BR-IMP-001 - файл .xlsx эмас, 5MB'дан
     *         катта ёки workbook бузуқ (бу ҳолда сатр хатолари йиғилмайди)
     */
    public ImportPreview parse(MultipartFile file) {
        requireValidFile(file);

        List<ContactRow> contacts = new ArrayList<>();
        List<EmployeeRow> employees = new ArrayList<>();
        List<ItemRow> items = new ArrayList<>();
        List<WarehouseRow> warehouses = new ArrayList<>();
        List<AccountRow> accounts = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        // Контакт+ходим ном namespace'и умумий (display name ягона) -
        // такрор текшируви иккала варақ бўйлаб битта тўпламда
        Set<String> contactNames = new HashSet<>();
        Set<String> itemNames = new HashSet<>();
        Set<String> warehouseNames = new HashSet<>();
        Set<String> accountNames = new HashSet<>();

        Workbook workbook = openWorkbook(file);
        try (workbook) {
            FormulaEvaluator eval = workbook.getCreationHelper().createFormulaEvaluator();
            boolean anyRecognized = false;

            Sheet sheet = workbook.getSheet(SHEET_CONTACTS);
            if (sheet != null) {
                anyRecognized = true;
                parseContacts(sheet, eval, contacts, contactNames, errors);
            }
            sheet = workbook.getSheet(SHEET_EMPLOYEES);
            if (sheet != null) {
                anyRecognized = true;
                parseEmployees(sheet, eval, employees, contactNames, errors);
            }
            sheet = workbook.getSheet(SHEET_ITEMS);
            if (sheet != null) {
                anyRecognized = true;
                parseItems(sheet, eval, items, itemNames, errors);
            }
            sheet = workbook.getSheet(SHEET_WAREHOUSES);
            if (sheet != null) {
                anyRecognized = true;
                parseWarehouses(sheet, eval, warehouses, warehouseNames, errors);
            }
            sheet = workbook.getSheet(SHEET_ACCOUNTS);
            if (sheet != null) {
                anyRecognized = true;
                parseAccounts(sheet, eval, accounts, accountNames, errors);
            }

            if (!anyRecognized) {
                errors.add(new ImportError(SHEET_FILE, 0,
                        "Файлда танилган варақ йўқ (керак: Контактлар, Ходимлар, "
                        + "Товарлар, Омборлар, Счётлар)"));
            }
        } catch (IOException e) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_001,
                    "Файл ўқишда хато - .xlsx workbook бузуқ бўлиши мумкин");
        }

        return new ImportPreview(contacts, employees, items, warehouses, accounts, errors);
    }

    // ---- 2-босқич: ЁЗИШ (ҳаммаси-ёки-ҳеч-нарса) ----

    /**
     * Тоза preview'ни базага қўллайди. Мавжуд номлар ўтказилади,
     * янгилари public service'лар орқали яратилади. Битта create хато
     * берса @Transactional бутун import'ни rollback қилади (all-or-nothing).
     *
     * <p>Чақирувчи бунга ФАҚАТ {@code preview.hasErrors()==false} бўлганда
     * киради - шу боис бу ерда parse хатолари қайта текширилмайди.
     */
    @Transactional
    public ImportResult apply(ImportPreview preview) {
        // Мавжуд номлар (public read) - идемпотент ўтказиш учун. Contact
        // namespace умумий: customer+vendor+employee битта тўпламда
        Set<String> existingContacts = new HashSet<>();
        contactService.byType(ContactType.CUSTOMER, true).forEach(c -> existingContacts.add(c.getDisplayName()));
        contactService.byType(ContactType.VENDOR, true).forEach(c -> existingContacts.add(c.getDisplayName()));
        contactService.byType(ContactType.EMPLOYEE, true).forEach(c -> existingContacts.add(c.getDisplayName()));
        Set<String> existingItems = new HashSet<>();
        itemService.list(null, true).forEach(i -> existingItems.add(i.getName()));
        Set<String> existingWarehouses = new HashSet<>();
        warehouseService.all().forEach(w -> existingWarehouses.add(w.getName()));
        Set<String> existingAccounts = new HashSet<>();
        accountService.all().forEach(a -> existingAccounts.add(a.getName()));

        int contactsCreated = 0, contactsSkipped = 0;
        for (ContactRow row : preview.contacts()) {
            if (existingContacts.contains(row.name())) {
                contactsSkipped++;
                continue;
            }
            contactService.create(row.type(), new ContactData(
                    row.name(), null, null, null, row.email(), row.phone(),
                    null, null, row.taxId(), null, null, row.notes()));
            existingContacts.add(row.name());
            contactsCreated++;
        }

        int employeesCreated = 0, employeesSkipped = 0;
        for (EmployeeRow row : preview.employees()) {
            if (existingContacts.contains(row.name())) {
                employeesSkipped++;
                continue;
            }
            contactService.create(ContactType.EMPLOYEE, new ContactData(
                    row.name(), null, null, null, null, row.phone(),
                    null, null, null, null, row.salary(), row.notes()));
            existingContacts.add(row.name());
            employeesCreated++;
        }

        int itemsCreated = 0, itemsSkipped = 0;
        for (ItemRow row : preview.items()) {
            if (existingItems.contains(row.name())) {
                itemsSkipped++;
                continue;
            }
            // Счёт боғлашлар import'да сўралмайди - тип бўйича тизим
            // default'лари (invoice/bill оқимидаги нақш)
            DefaultAccounts def = itemService.defaultsFor(row.type());
            UUID inventoryAsset = row.type() == ItemType.INVENTORY ? def.inventoryAsset() : null;
            itemService.create(row.type(), new ItemData(
                    row.name(), row.sku(), null, row.unitId(), row.salesPrice(), null,
                    def.income(), row.purchaseCost(), null, def.expense(),
                    inventoryAsset, null, null, null, row.taxRateId(), row.taxRateId()));
            existingItems.add(row.name());
            itemsCreated++;
        }

        int warehousesCreated = 0, warehousesSkipped = 0;
        for (WarehouseRow row : preview.warehouses()) {
            if (existingWarehouses.contains(row.name())) {
                warehousesSkipped++;
                continue;
            }
            warehouseService.create(row.name(), row.code());
            existingWarehouses.add(row.name());
            warehousesCreated++;
        }

        int accountsCreated = 0, accountsSkipped = 0;
        for (AccountRow row : preview.accounts()) {
            if (existingAccounts.contains(row.name())) {
                accountsSkipped++;
                continue;
            }
            // Пул счёти - postable=true, ота йўқ; валюта null=home
            accountService.create(row.name(), row.detailType(), row.code(),
                    null, null, true, row.currency());
            existingAccounts.add(row.name());
            accountsCreated++;
        }

        ImportResult result = new ImportResult(contactsCreated, contactsSkipped,
                employeesCreated, employeesSkipped, itemsCreated, itemsSkipped,
                warehousesCreated, warehousesSkipped, accountsCreated, accountsSkipped);
        // Аудит (Arbitr-062): синхрон listener - rollback'да ёзув ҳам йўқолади
        eventPublisher.publishEvent(new ExcelImportedEvent(result));
        return result;
    }

    // ---- варақ парсерлари ----

    /** «Контактлар»: Номи*, Тури*, Телефон, Email, ИНН, Изоҳ. */
    private void parseContacts(Sheet sheet, FormulaEvaluator eval, List<ContactRow> out,
                               Set<String> names, List<ImportError> errors) {
        Map<String, Integer> h = headerMap(sheet, eval);
        Integer cName = h.get(COL_NAME);
        Integer cType = h.get(COL_TYPE);
        if (missingRequired(SHEET_CONTACTS, errors, cName, COL_NAME, cType, COL_TYPE)) {
            return;
        }
        if (tooManyRows(SHEET_CONTACTS, sheet, errors)) {
            return;
        }
        Integer cPhone = h.get(COL_PHONE), cEmail = h.get(COL_EMAIL),
                cTax = h.get(COL_TAXID), cNotes = h.get(COL_NOTES);
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int rowNo = r + 1;
            String name = cell(row, cName, eval), typeRaw = cell(row, cType, eval),
                    phone = cell(row, cPhone, eval), email = cell(row, cEmail, eval),
                    tax = cell(row, cTax, eval), notes = cell(row, cNotes, eval);
            if (allBlank(name, typeRaw, phone, email, tax, notes)) {
                continue;
            }
            try {
                requireName(name);
                ContactType type = contactType(typeRaw);
                requireUniqueInFile(names, name);
                out.add(new ContactRow(rowNo, name, type, phone, email, tax, notes));
            } catch (BusinessRuleException e) {
                errors.add(new ImportError(SHEET_CONTACTS, rowNo, e.displayMessage()));
            }
        }
    }

    /** «Ходимлар»: Номи*, Телефон, Ойлик (гросс), Изоҳ. */
    private void parseEmployees(Sheet sheet, FormulaEvaluator eval, List<EmployeeRow> out,
                                Set<String> names, List<ImportError> errors) {
        Map<String, Integer> h = headerMap(sheet, eval);
        Integer cName = h.get(COL_NAME);
        if (missingRequired(SHEET_EMPLOYEES, errors, cName, COL_NAME)) {
            return;
        }
        if (tooManyRows(SHEET_EMPLOYEES, sheet, errors)) {
            return;
        }
        Integer cPhone = h.get(COL_PHONE), cSalary = h.get(COL_SALARY), cNotes = h.get(COL_NOTES);
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int rowNo = r + 1;
            String name = cell(row, cName, eval), phone = cell(row, cPhone, eval),
                    salaryRaw = cell(row, cSalary, eval), notes = cell(row, cNotes, eval);
            if (allBlank(name, phone, salaryRaw, notes)) {
                continue;
            }
            try {
                requireName(name);
                BigDecimal salary = nonNegativeDecimal(salaryRaw, COL_SALARY);
                requireUniqueInFile(names, name);
                out.add(new EmployeeRow(rowNo, name, phone, salary, notes));
            } catch (BusinessRuleException e) {
                errors.add(new ImportError(SHEET_EMPLOYEES, rowNo, e.displayMessage()));
            }
        }
    }

    /** «Товарлар»: Номи*, Тури*, Бирлик*(ТОВАРда), SKU, Сотув/Харид нархи, ҚҚС ставкаси. */
    private void parseItems(Sheet sheet, FormulaEvaluator eval, List<ItemRow> out,
                            Set<String> names, List<ImportError> errors) {
        Map<String, Integer> h = headerMap(sheet, eval);
        Integer cName = h.get(COL_NAME);
        Integer cType = h.get(COL_TYPE);
        if (missingRequired(SHEET_ITEMS, errors, cName, COL_NAME, cType, COL_TYPE)) {
            return;
        }
        if (tooManyRows(SHEET_ITEMS, sheet, errors)) {
            return;
        }
        Integer cUnit = h.get(COL_UNIT), cSku = h.get(COL_SKU),
                cSales = h.get(COL_SALES_PRICE), cCost = h.get(COL_PURCHASE_COST),
                cTax = h.get(COL_TAX);
        // Каталог бир марта харитага олинади (ном бўйича) - ҳар сатрда
        // қайта сўров бўлмасин. Калит lowercase (Arbitr-073): «Дона» ≠
        // «дона» деб BR-IMP-005 отилмасин - Java String.toLowerCase()
        // кириллни тўғри folds (grep -i'дан фарқли).
        Map<String, Unit> unitsByName = new HashMap<>();
        unitService.all().forEach(u -> unitsByName.putIfAbsent(u.getName().toLowerCase(), u));
        Map<String, TaxRate> taxByName = new HashMap<>();
        taxRateService.all().forEach(t -> taxByName.putIfAbsent(t.getName().toLowerCase(), t));
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int rowNo = r + 1;
            String name = cell(row, cName, eval), typeRaw = cell(row, cType, eval),
                    unitRaw = cell(row, cUnit, eval), sku = cell(row, cSku, eval),
                    salesRaw = cell(row, cSales, eval), costRaw = cell(row, cCost, eval),
                    taxRaw = cell(row, cTax, eval);
            if (allBlank(name, typeRaw, unitRaw, sku, salesRaw, costRaw, taxRaw)) {
                continue;
            }
            try {
                requireName(name);
                ItemType type = itemType(typeRaw);
                UUID unitId = resolveUnit(type, unitRaw, unitsByName);
                BigDecimal salesPrice = nonNegativeDecimal(salesRaw, COL_SALES_PRICE);
                BigDecimal purchaseCost = nonNegativeDecimal(costRaw, COL_PURCHASE_COST);
                UUID taxRateId = resolveTax(taxRaw, taxByName);
                requireUniqueInFile(names, name);
                out.add(new ItemRow(rowNo, name, type, unitId, sku, salesPrice, purchaseCost, taxRateId));
            } catch (BusinessRuleException e) {
                errors.add(new ImportError(SHEET_ITEMS, rowNo, e.displayMessage()));
            }
        }
    }

    /** «Омборлар»: Номи*, Код. */
    private void parseWarehouses(Sheet sheet, FormulaEvaluator eval, List<WarehouseRow> out,
                                 Set<String> names, List<ImportError> errors) {
        Map<String, Integer> h = headerMap(sheet, eval);
        Integer cName = h.get(COL_NAME);
        if (missingRequired(SHEET_WAREHOUSES, errors, cName, COL_NAME)) {
            return;
        }
        if (tooManyRows(SHEET_WAREHOUSES, sheet, errors)) {
            return;
        }
        Integer cCode = h.get(COL_CODE);
        // Тизимдаги банд кодлар parse'да (Arbitr-073): apply бу ҳолда
        // BR-WH-002 билан бутун import'ни rollback қиларди - энди preview
        // аниқ сатр билан кўрсатади. Номлар ҳам керак: номи мавжуд сатр
        // apply'да ЎТКАЗИЛади (идемпотент) - унинг коди хато эмас.
        Set<String> existingNames = new HashSet<>();
        Set<String> existingCodes = new HashSet<>();
        warehouseService.all().forEach(w -> {
            existingNames.add(w.getName());
            if (w.getCode() != null) {
                existingCodes.add(w.getCode().toUpperCase());
            }
        });
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int rowNo = r + 1;
            String name = cell(row, cName, eval), code = cell(row, cCode, eval);
            if (allBlank(name, code)) {
                continue;
            }
            try {
                requireName(name);
                requireCodeFree(name, code, existingNames, existingCodes, BusinessRule.BR_WH_002);
                requireUniqueInFile(names, name);
                out.add(new WarehouseRow(rowNo, name, code));
            } catch (BusinessRuleException e) {
                errors.add(new ImportError(SHEET_WAREHOUSES, rowNo, e.displayMessage()));
            }
        }
    }

    /** «Счётлар» (фақат пул счётлари): Номи*, Тури*(БАНК/КАССА), Валюта, Код. */
    private void parseAccounts(Sheet sheet, FormulaEvaluator eval, List<AccountRow> out,
                               Set<String> names, List<ImportError> errors) {
        Map<String, Integer> h = headerMap(sheet, eval);
        Integer cName = h.get(COL_NAME);
        Integer cType = h.get(COL_TYPE);
        if (missingRequired(SHEET_ACCOUNTS, errors, cName, COL_NAME, cType, COL_TYPE)) {
            return;
        }
        if (tooManyRows(SHEET_ACCOUNTS, sheet, errors)) {
            return;
        }
        Integer cCurrency = h.get(COL_CURRENCY), cCode = h.get(COL_CODE);
        // Тизимдаги банд счёт кодлари parse'да (Arbitr-073) - BR-COA-002
        // apply'га етмасин (тафсилот parseWarehouses'даги изоҳда).
        Set<String> existingNames = new HashSet<>();
        Set<String> existingCodes = new HashSet<>();
        accountService.all().forEach(a -> {
            existingNames.add(a.getName());
            if (a.getCode() != null) {
                existingCodes.add(a.getCode().toUpperCase());
            }
        });
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int rowNo = r + 1;
            String name = cell(row, cName, eval), typeRaw = cell(row, cType, eval),
                    currencyRaw = cell(row, cCurrency, eval), code = cell(row, cCode, eval);
            if (allBlank(name, typeRaw, currencyRaw, code)) {
                continue;
            }
            try {
                requireName(name);
                AccountDetailType detailType = accountDetailType(typeRaw);
                String currency = resolveCurrency(currencyRaw);
                requireCodeFree(name, code, existingNames, existingCodes, BusinessRule.BR_COA_002);
                requireUniqueInFile(names, name);
                out.add(new AccountRow(rowNo, name, detailType, currency, code));
            } catch (BusinessRuleException e) {
                errors.add(new ImportError(SHEET_ACCOUNTS, rowNo, e.displayMessage()));
            }
        }
    }

    // ---- қиймат ечувчилар (BR-IMP-002/005) ----

    /** BR-IMP-002: Номи бўш бўлмайди. */
    private void requireName(String name) {
        if (name == null) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_002, "«" + COL_NAME + "» бўш бўлмаслиги шарт");
        }
    }

    /** МИЖОЗ/ТАЪМИНОТЧИ (катта-кичик фарқсиз) - BR-IMP-002. */
    private ContactType contactType(String raw) {
        if (raw == null) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_002, "«" + COL_TYPE + "» бўш бўлмаслиги шарт");
        }
        return switch (raw.strip().toUpperCase()) {
            case "МИЖОЗ" -> ContactType.CUSTOMER;
            case "ТАЪМИНОТЧИ" -> ContactType.VENDOR;
            default -> throw new BusinessRuleException(BusinessRule.BR_IMP_002,
                    "«" + COL_TYPE + "» нотанилган (МИЖОЗ ёки ТАЪМИНОТЧИ): «" + raw + "»");
        };
    }

    /** ТОВАР→INVENTORY / ХИЗМАТ→SERVICE (катта-кичик фарқсиз) - BR-IMP-002. */
    private ItemType itemType(String raw) {
        if (raw == null) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_002, "«" + COL_TYPE + "» бўш бўлмаслиги шарт");
        }
        return switch (raw.strip().toUpperCase()) {
            case "ТОВАР" -> ItemType.INVENTORY;
            case "ХИЗМАТ" -> ItemType.SERVICE;
            default -> throw new BusinessRuleException(BusinessRule.BR_IMP_002,
                    "«" + COL_TYPE + "» нотанилган (ТОВАР ёки ХИЗМАТ): «" + raw + "»");
        };
    }

    /** БАНК→CHECKING / КАССА→CASH_ON_HAND (катта-кичик фарқсиз) - BR-IMP-002. */
    private AccountDetailType accountDetailType(String raw) {
        if (raw == null) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_002, "«" + COL_TYPE + "» бўш бўлмаслиги шарт");
        }
        return switch (raw.strip().toUpperCase()) {
            case "БАНК" -> AccountDetailType.CHECKING;
            case "КАССА" -> AccountDetailType.CASH_ON_HAND;
            default -> throw new BusinessRuleException(BusinessRule.BR_IMP_002,
                    "«" + COL_TYPE + "» нотанилган (БАНК ёки КАССА): «" + raw + "»");
        };
    }

    /**
     * Бирлик номини каталогдан ечади. ТОВАР учун мажбурий (BR-IMP-002);
     * берилган бўлса каталогда бўлиши шарт (BR-IMP-005) - жимгина
     * яратилмайди. ХИЗМАТ учун бўш бўлса null.
     */
    private UUID resolveUnit(ItemType type, String unitRaw, Map<String, Unit> unitsByName) {
        if (unitRaw == null) {
            if (type == ItemType.INVENTORY) {
                throw new BusinessRuleException(BusinessRule.BR_IMP_002,
                        "«" + COL_UNIT + "» ТОВАР учун мажбурий");
            }
            return null;
        }
        Unit unit = unitsByName.get(unitRaw.toLowerCase());
        if (unit == null) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_005,
                    "«" + COL_UNIT + "» каталогда йўқ: «" + unitRaw + "»");
        }
        return unit.getId();
    }

    /** ҚҚС ставкасини НОМИ бўйича ечади; бўш → null (солиқсиз) - BR-IMP-005. */
    private UUID resolveTax(String taxRaw, Map<String, TaxRate> taxByName) {
        if (taxRaw == null) {
            return null;
        }
        TaxRate rate = taxByName.get(taxRaw.toLowerCase());
        if (rate == null) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_005,
                    "«" + COL_TAX + "» каталогда йўқ: «" + taxRaw + "»");
        }
        return rate.getId();
    }

    /** Валюта ISO кодини текширади; бўш → null (home) - BR-IMP-005. */
    private String resolveCurrency(String currencyRaw) {
        if (currencyRaw == null) {
            return null;
        }
        if (currencyService.byCode(currencyRaw).isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_005,
                    "«" + COL_CURRENCY + "» каталогда йўқ: «" + currencyRaw + "»");
        }
        return currencyRaw.strip();
    }

    /**
     * Сон майдонни парслаб МАНФИЙ эмаслигини ҳам текширади (Arbitr-073):
     * манфий Ойлик/нарх apply'даги BR-CON-011/BR-ITM-009'га етиб биттадан,
     * варақ/сатрсиз йиқиларди - энди parse тўлиқ рўйхатда, контекст билан
     * ушлайди (спец ваъдаси import-excel.md:117-118). Бўш → null.
     */
    private BigDecimal nonNegativeDecimal(String raw, String label) {
        BigDecimal value = FormParsers.decimal(raw, BusinessRule.BR_IMP_002, label);
        if (value != null && value.signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_002,
                    "«" + label + "» манфий бўлмаслиги шарт");
        }
        return value;
    }

    /**
     * Тизимда банд кодни parse'да ушлайди (Arbitr-073) - apply бу ҳолда
     * BR-COA-002/BR-WH-002 билан бутун import'ни rollback қиларди. Хато
     * apply семантикасини АЙНАН такрорлайди: номи мавжуд сатр apply'да
     * ўтказилади (идемпотентлик) - унинг коди текширилмайди; кодлар
     * иккала service'дагидек катта-кичик фарқсиз (uppercase) солиштирилади.
     */
    private void requireCodeFree(String name, String code, Set<String> existingNames,
                                 Set<String> existingCodes, BusinessRule rule) {
        if (code == null || existingNames.contains(name)) {
            return;
        }
        if (existingCodes.contains(code.strip().toUpperCase())) {
            throw new BusinessRuleException(rule,
                    "«" + COL_CODE + "» тизимда банд: «" + code + "»");
        }
    }

    /** BR-IMP-003: файл ичида ном такрорланмайди. */
    private void requireUniqueInFile(Set<String> seen, String name) {
        if (!seen.add(name)) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_003,
                    "Ном файл ичида такрорланган: «" + name + "»");
        }
    }

    // ---- POI ёрдамчилари ----

    /** BR-IMP-001: файл мавжуд, .xlsx ва 5MB'дан кичик. */
    private void requireValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_001, "Файл танланмади");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_001, "Файл 5MB дан катта бўлмаслиги шарт");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessRuleException(BusinessRule.BR_IMP_001, "Файл .xlsx форматида бўлиши шарт");
        }
    }

    /** Workbook очади; POI хатоси BR-IMP-001'га айланади (бузуқ/нотўғри файл). */
    private Workbook openWorkbook(MultipartFile file) {
        try {
            return WorkbookFactory.create(file.getInputStream());
        } catch (IOException | RuntimeException e) {
            // POI бузуқ/нотўғри форматда IOException ёки unchecked
            // (NotOfficeXmlFileException, EmptyFileException) отади
            throw new BusinessRuleException(BusinessRule.BR_IMP_001,
                    "Файл .xlsx workbook сифатида очилмади");
        }
    }

    /** 1-қатор (сарлавҳа) - устун номи → индекс. */
    private Map<String, Integer> headerMap(Sheet sheet, FormulaEvaluator eval) {
        Map<String, Integer> map = new HashMap<>();
        Row header = sheet.getRow(0);
        if (header == null) {
            return map;
        }
        for (Cell c : header) {
            String text = cellString(c, eval);
            if (text != null) {
                map.putIfAbsent(text, c.getColumnIndex());
            }
        }
        return map;
    }

    /**
     * Мажбурий устунлар борлигини текширади. Йўқ бўлса битта сатр-даража
     * хато қўшади (BR-IMP-002) ва true қайтаради - варақ ўтказилади.
     * Аргументлар жуфт-жуфт: (индекс, устун номи).
     */
    private boolean missingRequired(String sheet, List<ImportError> errors, Object... colAndLabel) {
        for (int i = 0; i < colAndLabel.length; i += 2) {
            if (colAndLabel[i] == null) {
                errors.add(new ImportError(sheet, 1,
                        "«" + colAndLabel[i + 1] + "» устуни (1-қатор) топилмади"));
                return true;
            }
        }
        return false;
    }

    /** BR-IMP-004: 2000 сатрдан кўп бўлса хато қўшади ва true қайтаради. */
    private boolean tooManyRows(String sheet, Sheet poiSheet, List<ImportError> errors) {
        if (poiSheet.getLastRowNum() > MAX_ROWS) {
            errors.add(new ImportError(sheet, 0,
                    "Варақда " + MAX_ROWS + " сатрдан кўп маълумот"));
            return true;
        }
        return false;
    }

    /** Устун индекси бўйича катакни трим қилинган матн сифатида ўқийди. */
    private String cell(Row row, Integer col, FormulaEvaluator eval) {
        return col == null ? null : cellString(row.getCell(col), eval);
    }

    /**
     * Катакни матнга айлантиради: STRING трим; NUMERIC trailing нолсиз
     * plain (масофа/минг ажратгичсиз - кейин FormParsers парслайди);
     * FORMULA эса ҳисоблаб қайта туркумланади. Бўш/ERROR → null.
     */
    private String cellString(Cell c, FormulaEvaluator eval) {
        if (c == null) {
            return null;
        }
        CellType type = c.getCellType();
        if (type == CellType.FORMULA) {
            CellValue v = eval.evaluate(c);
            if (v == null) {
                return null;
            }
            return switch (v.getCellType()) {
                case STRING -> blankToNull(v.getStringValue());
                case NUMERIC -> numberToString(v.getNumberValue());
                case BOOLEAN -> String.valueOf(v.getBooleanValue());
                default -> null;
            };
        }
        return switch (type) {
            case STRING -> blankToNull(c.getStringCellValue());
            case NUMERIC -> numberToString(c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> null;
        };
    }

    /** Сон катакни trailing нолсиз аниқ матн - «25000», «21000.5». */
    private String numberToString(double d) {
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    /** Бўш/фақат-пробел → null, акс ҳолда трим. */
    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    /** Барча қиймат null'ми - бутунлай бўш сатр индамай ўтказилади. */
    private boolean allBlank(String... values) {
        for (String v : values) {
            if (v != null) {
                return false;
            }
        }
        return true;
    }
}
