package com.averpo.erp.shared.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Заводга қайтариш (Factory reset, docs/modules/factory-reset.md): бутун
 * тизимни янги ўрнатилган ҳолатга келтиради - демо/синов маълумоти
 * тозаланади, seed каталоглар ва жорий admin қолади. QBO «Purge company»
 * эталони.
 *
 * <p><b>Нега PostingService четлаб ўтилади (темир қоида 2/3):</b> бу
 * МАЪМУРИЙ амал - ҳужжат lifecycle эмас. Ҳеч бир POSTED ҳужжат
 * ЎЗГАРТИРИЛМАЯПТИ (update/delete эмас) - бутун база бир йўла бошланғич
 * ҳолатга келтириляпти. Шунинг учун JdbcClient билан TRUNCATE/DELETE
 * қилинади; истисно business-rules.md BR-RST бўлимида ҳужжатланган.
 *
 * <p><b>Битта транзакция:</b> барча ўчириш + default chart қайта ўрнатиш
 * ({@link DefaultChartInstaller} порти орқали - shared ledger'га боғлана
 * олмайди) битта {@code @Transactional} ичида - барчаси ёки ҳеч нарса.
 * Диск иловалари эса фақат COMMIT'дан КЕЙИН ўчирилади (DB rollback бўлса
 * файллар жойида қолсин); ўчириш хатоси reset'ни йиқитмайди (WARN).
 *
 * <p><b>Модул мустақиллиги:</b> shared бошқа модул repository'сига қўл
 * узатмайди - жадвал номлари хом SQL'да, фиксирланган seed UUID'лар шу
 * ердаги константаларда (манба changeset изоҳда). app_user ҳам JdbcClient
 * билан ўчирилади (security модулига боғланмасдан).
 */
@Service
@RequiredArgsConstructor
public class FactoryResetService {

    /** Диск иловаларини ўчиришдаги хатони WARN билан ёзиш учун. */
    private static final Logger log = LoggerFactory.getLogger(FactoryResetService.class);

    /**
     * Тўлиқ TRUNCATE қилинадиган иш жадваллари (ҳужжатлар, GL, инвентар,
     * тўловлар, application'лар, контакт, товар, нарх, class, илова, аудит,
     * валюта курслари). {@code account} ҳам киради - кейин default chart
     * қайта ўрнатилади. Барчаси битта statement'да: Postgres интра-рўйхат
     * FK'ларни ўзи ечади; рўйхатдан ташқари бирор жадвал шулардан бирига
     * FK берса TRUNCATE ЯҚҚОЛ хато беради (интеграцион тест тутади) -
     * жимгина маълумот қолиб кетмайди. {@code unit_group} бу ерда ЙЎҚ:
     * seed {@code unit}'лар ундан FK - аввал detach қилиниб алоҳида
     * тозаланади. {@code databasechangelog(lock)} ҲЕЧ ҚАЧОН тегилмайди.
     */
    private static final String TRUNCATE_WORK_TABLES = """
            TRUNCATE TABLE
              journal_entry_line, journal_entry,
              invoice_payment_allocation, invoice_payment, invoice_line, invoice,
              bill_payment_allocation, bill_payment, bill_line, bill,
              estimate_line, estimate, purchase_order_line, purchase_order,
              credit_application, credit_memo_line, credit_memo,
              vendor_credit_application, vendor_credit_line, vendor_credit,
              refund_receipt_line, refund_receipt,
              sales_receipt_line, sales_receipt,
              payroll_run_line, payroll_run, payroll_payment_line, payroll_payment,
              landed_cost_allocation_line, landed_cost_allocation,
              bank_transaction_line, bank_transaction,
              bank_reconciliation_match, bank_reconciliation,
              stock_adjustment_line, stock_adjustment,
              stock_transfer_line, stock_transfer,
              cost_layer_consumption, cost_layer, stock_movement, stock_balance,
              exchange_rate,
              contact_person, contact_bank_account, contact_address,
              price_list_item, price_list_customer, price_list,
              item, item_category,
              txn_class,
              audit_event,
              account
            """;

    /** «Асосий омбор» seed қатори (changeset 017-02-warehouse-seed). */
    private static final UUID WAREHOUSE_MAIN_ID =
            UUID.fromString("019f348b-81c5-7d1f-a7a2-d863f0f5fd26");

    /** Бирлик seed id'лари (changeset 008-item.sql, 014-unit-seed). */
    private static final String UNIT_SEED_IDS = """
            '019f337a-8414-7469-80d4-c091bbb42b2a'::uuid,
            '019f337a-8415-7d1a-a7c3-877ad2ae3fd6'::uuid,
            '019f337a-8415-7659-ac04-cb9434ea40f1'::uuid,
            '019f337a-8415-7a64-a93a-f849358814d3'::uuid,
            '019f337a-8415-7c02-9512-2322dd148b18'::uuid,
            '019f337a-8415-77ad-bb54-002766e1b043'::uuid
            """;

    /** Тўлов шарти seed id'лари (changeset 007-contact.sql, 011-payment-term-seed). */
    private static final String PAYMENT_TERM_SEED_IDS = """
            '019f337a-8364-7af7-b6f9-cdbb2205cb60'::uuid,
            '019f337a-840a-7911-b618-ead606bae900'::uuid,
            '019f337a-840a-731d-9e61-c977d33ee0fc'::uuid,
            '019f337a-840a-78b7-900f-1eaab7041c0c'::uuid
            """;

    /** Тўлов усули seed id'лари (changeset 036-payment-method.sql). */
    private static final String PAYMENT_METHOD_SEED_IDS = """
            '019f8c30-0001-7c11-9e01-0000000000c1'::uuid,
            '019f8c30-0002-7c22-9e02-0000000000c2'::uuid,
            '019f8c30-0003-7c33-9e03-0000000000c3'::uuid
            """;

    /**
     * ҚҚС ставкаси seed id'лари (changeset 032-tax.sql: QQS12, NO_TAX).
     * Код бўйича эмас, айнан id бўйича (Arbitr-072): admin seed КОДини
     * таҳрирлаган бўлса ҳам сатр seed бўлиб қолади - реставрация қилинади.
     */
    private static final String TAX_RATE_SEED_IDS = """
            '019f8a10-0001-7a11-9c01-0000000000a1'::uuid,
            '019f8a10-0002-7a22-9c02-0000000000a2'::uuid
            """;

    /** Хом SQL - ягона ёзиш нуқтаси (модуллараро repository'га тегмайди). */
    private final JdbcClient jdbcClient;

    /**
     * Default chart'ни қайта ўрнатиш порти (ledger имплементация қилади) -
     * счётларда фиксирланган seed UUID йўқ (CSV импорт), TRUNCATE + қайта
     * импорт ягона йўл.
     */
    private final DefaultChartInstaller chartInstaller;

    /**
     * Стандарт UOM гуруҳларини қайта ўрнатиш порти (item имплементация
     * қилади, Arbitr-147) - reset бирликларни seed ҳолатига (гуруҳсиз)
     * келтиргач стандарт гуруҳларни қайта тиклайди, шунда fresh install
     * билан бир хил тайёр ҳолат чиқади.
     */
    private final DefaultUnitsInstaller unitsInstaller;

    /**
     * Аудит event'и учун (Arbitr-062): shared audit'ни import қила олмайди
     * (цикл), шунга reset ўз event'ини эълон қилади - синхрон listener
     * FACTORY_RESET ёзувини ШУ транзакцияда киритади.
     */
    private final ApplicationEventPublisher eventPublisher;

    /** Илова файллари каталоги (attachments.md) - диск тозалаш учун. */
    @Value("${app.attachments.dir}")
    private String attachmentsDir;

    /**
     * Заводга қайтариш: иш маълумотини тозалаб seed каталогларни ва жорий
     * admin'ни қолдиради. Битта транзакция.
     *
     * <p>Оқим: (1) иш жадваллари TRUNCATE; (2) unit'ларни гуруҳдан ажратиб
     * seed'дан бошқасини ўчириш + unit_group тозалаш; (3) каталогларни seed
     * ҳолатига келтириш (нофаол currency қайта нофаол, ортиқча сатрлар
     * ўчади); (4) жорий admin'дан бошқа фойдаланувчилар; (5) company_settings
     * default'га (lazy qayta yaratiladi, setup_done=false); (6) default chart
     * қайта ўрнатилади. COMMIT'дан кейин диск иловалари тозаланади.
     *
     * @param currentAdminId reset қилаётган (ва қоладиган) ягона фойдаланувчи
     */
    @Transactional
    public void reset(UUID currentAdminId) {
        // (0) FK ечиш (Arbitr-101/112): app_user ва company_settings
        // (иккови (4)/(5) қадамда DELETE билан сақланади) attachment ва
        // contact'ни reference қилади (profile_image_id, employee_contact_id,
        // logo_attachment_id). Postgres TRUNCATE FK КОНСТРЕЙНТ мавжудлигини
        // текширади (маълумот NULL бўлса ҲАМ «referenced in a foreign key»
        // беради) - шунинг учун attachment/contact TRUNCATE_WORK_TABLES'дан
        // чиқарилди, қуйида (1b) DELETE билан кетади. FK'ни аввал NULL'га
        // туширамиз (аватар/лого барибир йўқолади - фабрика ҳолати).
        jdbcClient.sql("UPDATE app_user SET profile_image_id = NULL, "
                + "employee_contact_id = NULL").update();
        jdbcClient.sql("UPDATE company_settings SET logo_attachment_id = NULL, "
                + "brand_logo_attachment_id = NULL").update();

        // (1) Барча иш маълумоти - битта TRUNCATE (account ҳам).
        jdbcClient.sql(TRUNCATE_WORK_TABLES).update();

        // (1b) Илова ва контактлар - DELETE (TRUNCATE ЭМАС): app_user/
        // company_settings FK констрейнти уларни TRUNCATE'да рад қиларди.
        // Референс ҳужжатлар (1)да тозаланган ва FK'лар (0)да NULL -
        // DELETE тоза кетади (payroll_run_line.employee_id ҳам бўшатилган).
        jdbcClient.sql("DELETE FROM attachment").update();
        jdbcClient.sql("DELETE FROM contact").update();

        // Аудит (Arbitr-062): TRUNCATE'дан ДАРҲОЛ КЕЙИН - синхрон listener
        // ёзуви тоза журналнинг БИРИНЧИ ёзуви бўлиб қолади (кейинги (6)
        // қадамдаги chart қайта ўрнатиш CHART_IMPORTED бўлиб иккинчи туради).
        // Rollback бўлса ёзув ҳам йўқолади - журнал фақат содир бўлган иш.
        eventPublisher.publishEvent(new FactoryResetEvent());

        // (2) unit -> unit_group FK'ни ечиш: аввал ҳамма unit'ни гуруҳдан
        // ажратиб seed ҳолатига (factor 1, base эмас, фаол), кейин seed'дан
        // бошқа unit'ларни ўчириб unit_group'ни тозалаш (энди референссиз).
        jdbcClient.sql("UPDATE unit SET group_id = NULL, factor = 1, "
                + "is_base = false, active = true").update();
        jdbcClient.sql("DELETE FROM unit WHERE id NOT IN (" + UNIT_SEED_IDS + ")").update();
        // DELETE (TRUNCATE эмас): unit -> unit_group FK констрейнти МАВЖУД
        // бўлгани учун Postgres TRUNCATE'ни рад этади (актуал маълумотга
        // қарамай). unit'лар юқорида detach қилинди - актуал референс йўқ,
        // DELETE бемалол ўтади.
        jdbcClient.sql("DELETE FROM unit_group").update();
        // Seed бирлик НОМЛАРИ ҳам seed қийматига (Arbitr-072, warehouse нақши):
        // import шаблони «дона»га таянади - таҳрирланган ном reset'дан кейин
        // шаблонни синдирмасин. DELETE'дан кейин фақат seed қаторлар қолган -
        // CASE ҳаммасини қамрайди.
        jdbcClient.sql("UPDATE unit SET name = CASE id "
                + "WHEN '019f337a-8414-7469-80d4-c091bbb42b2a'::uuid THEN 'дона' "
                + "WHEN '019f337a-8415-7d1a-a7c3-877ad2ae3fd6'::uuid THEN 'кг' "
                + "WHEN '019f337a-8415-7659-ac04-cb9434ea40f1'::uuid THEN 'литр' "
                + "WHEN '019f337a-8415-7a64-a93a-f849358814d3'::uuid THEN 'метр' "
                + "WHEN '019f337a-8415-7c02-9512-2322dd148b18'::uuid THEN 'соат' "
                + "WHEN '019f337a-8415-77ad-bb54-002766e1b043'::uuid THEN 'хизмат' "
                + "END").update();

        // (2b) Стандарт UOM гуруҳларини қайта ўрнатиш (Arbitr-147): бирликлар
        // энди seed ҳолатида (6 гуруҳсиз) - порт орқали (item имплементацияси)
        // стандарт гуруҳлар тикланиб seed бирликлар (дона/кг/литр/метр/соат)
        // тегишли гуруҳга ютилади. Chart нақши айнан: shared item'га боғлана
        // олмайди (тескари боғлиқлик), шунга DefaultUnitsInstaller порти.
        // unit-reset (2)дан КЕЙИН - у detach/delete/rename қилиб гуруҳларни
        // бўшатади, порт ундан олдин чақирилса ишини йўқотар эди.
        unitsInstaller.installDefaultUnits();

        // (3) Каталогларни seed ҳолатига. Валюта: ортиқчаси ўчади, фаоллик
        // seed'дагидек (фақат UZS/USD фаол - home валюта нофаол бўла олмайди).
        jdbcClient.sql("DELETE FROM currency WHERE code NOT IN "
                + "('UZS','USD','EUR','RUB','GBP','KZT','CNY')").update();
        jdbcClient.sql("UPDATE currency SET active = (code IN ('UZS','USD'))").update();

        // ҚҚС ставкалари фиксирланган seed UUID бўйича (Arbitr-072): аввалги
        // код бўйича DELETE admin таҳрирлаган seed'ни (QQS12→QQS15) ўчириб
        // юборарди - тизимда ҚҚС қолмасди, Excel import BR-IMP-005 билан
        // йиқиларди. Warehouse услубида тўлиқ реставрация (код/ном/фоиз/фаол).
        jdbcClient.sql("DELETE FROM tax_rate WHERE id NOT IN (" + TAX_RATE_SEED_IDS + ")").update();
        jdbcClient.sql("UPDATE tax_rate SET code = 'QQS12', name = 'ҚҚС 12%', rate = 12, "
                + "active = true WHERE id = '019f8a10-0001-7a11-9c01-0000000000a1'::uuid").update();
        jdbcClient.sql("UPDATE tax_rate SET code = 'NO_TAX', name = 'ҚҚСсиз', rate = 0, "
                + "active = true WHERE id = '019f8a10-0002-7a22-9c02-0000000000a2'::uuid").update();

        // Тўлов шарти/усули номлари ҳам seed қийматига (Arbitr-072) - DELETE
        // дан кейин фақат seed қаторлар қолган, CASE ҳаммасини қамрайди.
        jdbcClient.sql("DELETE FROM payment_term WHERE id NOT IN ("
                + PAYMENT_TERM_SEED_IDS + ")").update();
        jdbcClient.sql("UPDATE payment_term SET active = true, name = CASE id "
                + "WHEN '019f337a-8364-7af7-b6f9-cdbb2205cb60'::uuid THEN 'Due on receipt' "
                + "WHEN '019f337a-840a-7911-b618-ead606bae900'::uuid THEN 'Net 15' "
                + "WHEN '019f337a-840a-731d-9e61-c977d33ee0fc'::uuid THEN 'Net 30' "
                + "WHEN '019f337a-840a-78b7-900f-1eaab7041c0c'::uuid THEN 'Net 60' "
                + "END").update();

        jdbcClient.sql("DELETE FROM payment_method WHERE id NOT IN ("
                + PAYMENT_METHOD_SEED_IDS + ")").update();
        jdbcClient.sql("UPDATE payment_method SET active = true, name = CASE id "
                + "WHEN '019f8c30-0001-7c11-9e01-0000000000c1'::uuid THEN 'Нақд' "
                + "WHEN '019f8c30-0002-7c22-9e02-0000000000c2'::uuid THEN 'Банк ўтказмаси' "
                + "WHEN '019f8c30-0003-7c33-9e03-0000000000c3'::uuid THEN 'Пластик карта' "
                + "END").update();

        // «Асосий омбор» seed қатори: ортиқча омборлар ўчади, seed қатори
        // фаол ва номи/коди seed'дагидек (фойдаланувчи таҳрирлаган бўлса ҳам).
        jdbcClient.sql("DELETE FROM warehouse WHERE id <> :main")
                .param("main", WAREHOUSE_MAIN_ID).update();
        jdbcClient.sql("UPDATE warehouse SET active = true, name = 'Асосий омбор', "
                + "code = 'MAIN' WHERE id = :main").param("main", WAREHOUSE_MAIN_ID).update();

        // Ҳужжат рақам счётчиклари seed'га (кейинги рақам 1 дан).
        jdbcClient.sql("UPDATE document_sequence SET next_number = 1").update();

        // (4) Фойдаланувчилар: reset қилаётган admin'дан бошқа ҳамма ўчади
        // (created_by FK эмас - dimension, из осилиб қолмайди).
        jdbcClient.sql("DELETE FROM app_user WHERE id <> :admin")
                .param("admin", currentAdminId).update();

        // (5) Компания созламалари default'га: қаторни ўчирамиз - кейинги
        // CompanySettingsService.get() уни default билан қайта яратади
        // (setup_done=false → setup оқими 056 табиий қайта бошланади).
        jdbcClient.sql("DELETE FROM company_settings").update();

        // (5b) Плагинлар default ҳолатига (Arbitr-113): қатор йўқлиги =
        // ўчиқ - янги ўрнатишда ҳамма плагин ўчиқ бўлгани каби. Плагин
        // ички маълумотини сақлаш қоидаси (спец: toggle-off ўчирмайди)
        // фақат ОДДИЙ ўчиришга тегишли - заводга қайтариш тўлиқ тозалайди.
        jdbcClient.sql("DELETE FROM plugin_state").update();

        // (6) Default chart'ни қайта ўрнатиш (account TRUNCATE қилинган эди) -
        // shared'даги порт, ledger имплементацияси, айнан шу транзакцияда.
        chartInstaller.installDefaultChart();

        // Диск иловалари фақат COMMIT'дан КЕЙИН (rollback бўлса файллар
        // қолсин - база билан мослик сақланади); хато reset'ни йиқитмайди.
        registerAttachmentDiskPurge();
    }

    /**
     * Транзакция муваффақиятли commit бўлса илова файлларини ўчиришни
     * рўйхатга олади. Rollback бўлса {@code afterCommit} умуман чақирилмайди
     * - файллар жойида қолиб база ёзувларига мос бўлаверади.
     */
    private void registerAttachmentDiskPurge() {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        purgeAttachmentFiles();
                    }
                });
    }

    /** Созланган илова каталогига {@link #purgeDirectory(Path)} ни қўллайди. */
    private void purgeAttachmentFiles() {
        purgeDirectory(Path.of(attachmentsDir));
    }

    /**
     * Каталог ичидаги ҳамма файл ва ост-каталогни ўчиради (каталог ўзи
     * қолади - кейинги иловалар учун). Best-effort: биронта файл ўчмаса
     * reset муваффақиятли ҳисобланади (база аллақачон commit бўлган) -
     * фақат WARN ёзилади, қолган orphan файл зарарсиз.
     *
     * <p>Static package-private (Arbitr-072 / Botir-051): {@code afterCommit}
     * синхронизацияси @Transactional тестда ҳеч қачон ишламайди (rollback -
     * commit йўқ), шунга диск мантиғи транзакциядан ажратилган ҳолда шу ерда
     * алоҳида unit тестланади.
     */
    static void purgeDirectory(Path root) {
        if (!Files.isDirectory(root)) {
            return; // ҳали бирор илова юкланмаган - тозалашга нарса йўқ
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> !p.equals(root))
                    // чуқурроқ йўл аввал ўчсин (файллар → ост-каталоглар)
                    .sorted(Comparator.reverseOrder())
                    .forEach(FactoryResetService::deleteQuietly);
        } catch (IOException e) {
            log.warn("Заводга қайтариш: илова каталогини кўздан кечириб бўлмади: {}",
                    root, e);
        }
    }

    /** Битта йўлни ўчиради, хатони фақат WARN билан ютади (reset тугаган). */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Заводга қайтариш: илова файли ўчмади: {}", path, e);
        }
    }
}
