package com.averpo.erp.testsupport;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Тест прогонида бажарилган SQL матнларини ушлаб берадиган Hibernate
 * {@link StatementInspector} (OPT-005 query-count тестлари учун).
 *
 * <p>application-test.yml даги {@code hibernate.session_factory.
 * statement_inspector} орқали уланади - фақат тест контекстида ишлайди.
 * Ҳолат статик, чунки инспекторни Hibernate ўзи инстанциялайди - тест
 * унга bean сифатида эга эмас. Тестлар битта JVM оқимида кетма-кет
 * юргани учун оддий тўплам кифоя; {@link #start()} аввалги қолдиқни
 * тозалайди, default ҳолатда ҳеч нарса йиғилмайди (бошқа тестларга
 * харажат қўшмаслик учун).
 */
public class SqlCaptureInspector implements StatementInspector {

    /** Ушланган SQL'лар - фақат {@link #start()} дан кейин йиғилади. */
    private static final List<String> CAPTURED = new CopyOnWriteArrayList<>();

    /** Ёзиб олиш ёқиқми - тест ташқарисидаги SQL йиғилмасин. */
    private static volatile boolean capturing;

    /** Ёзиб олишни бошлайди (аввалги йиғинди тозаланади). */
    public static void start() {
        CAPTURED.clear();
        capturing = true;
    }

    /** Ёзиб олишни тўхтатади ва ушланган SQL'лар нусхасини қайтаради. */
    public static List<String> stop() {
        capturing = false;
        return List.copyOf(CAPTURED);
    }

    /**
     * Берилган жадвалдан ўқиган SELECT'лар сони (query-count ўлчови).
     * «from жадвал» сўз чегараси билан қидирилади - оддий substring эмас,
     * акс ҳолда {@code currency} филтри {@code currency_id} устунига,
     * {@code bank_reconciliation} эса {@code bank_reconciliation_match}
     * жадвалига ҳам мос келиб адаштиради.
     */
    public static long selectCount(List<String> statements, String table) {
        java.util.regex.Pattern from = java.util.regex.Pattern.compile(
                "from\\s+" + java.util.regex.Pattern.quote(table) + "\\b");
        return statements.stream()
                .map(sql -> sql.toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.startsWith("select"))
                .filter(sql -> from.matcher(sql).find())
                .count();
    }

    @Override
    public String inspect(String sql) {
        if (capturing) {
            CAPTURED.add(sql);
        }
        return sql;
    }
}
