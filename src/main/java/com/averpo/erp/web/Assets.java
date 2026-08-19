package com.averpo.erp.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Статик asset'лар версияси - css/js линкларидаги {@code ?v=...}
 * (DEC-137, cache busting).
 *
 * <p>Нега керак: 131 дан кейин css/js узоқ кэшда юради - deploy'дан
 * кейин браузерда (ва Cloudflare'да) эски нусха туриб қолмаслиги учун
 * ҳар build'да URL'нинг ЎЗИ ўзгариши шарт. Версия манбаи - build
 * вақти (фойдаланувчи қарори): commit hash'дан фарқли
 * равишда у ҳар jar йиғилишида кафолатли ўзгаради, шунга hotfix
 * commit'сиз қайта deploy ҳам янги URL беради. Epoch СЕКУНД (ISO
 * эмас) - URL'да ихчам, иккинуқта/пробел escape қилинмайди.
 *
 * <p>{@link Perms}/{@link LayoutInfo} нақши - JTE layout'лари СТАТИК
 * метод орқали ўқийди: layout'га параметр қўшиш 102 саҳифа шаблонини
 * ўзгартиришни талаб қиларди. Улардан фарқи: версия бутун JVM умри
 * учун ЎЗГАРМАС, шунга request'га боғлиқ манба (RequestContextHolder)
 * керак эмас - қиймат старт пайтида бир марта олинади.
 *
 * <p>{@link BuildProperties} bean оддий classpath'да ва тестда
 * БЎЛМАЙДИ (bootBuildInfo фақат bootJar/bootRun'га build-info.properties
 * қўшади - SettingsController:54 прецеденти), шунинг учун fallback:
 * илова старт вақти. Build ҳеч қачон йиқилмайди (gitCommitHash
 * қолипи) - fallback'да ҳам ҳар қайта ишга туширишда версия ўзгаради,
 * фақат кластердаги нусхалар бир хил қийматни бермайди (dev'да
 * аҳамиятсиз, prod'да BuildProperties доим бор).
 */
@Component
public class Assets {

    /**
     * Жорий версия. Дастлабки қиймат - класс юкланиш (илова старт)
     * вақти: bean конструктори BuildProperties бўлса устидан ёзади.
     * volatile - қиймат старт thread'ида ёзилиб, render thread'ларида
     * ўқилади.
     */
    private static volatile String version = String.valueOf(Instant.now().getEpochSecond());

    /**
     * Старт пайтида версияни build вақтига алмаштиради. Bean фақат шу
     * ёзув учун - қиймат кейин статик ўқилади (JTE'дан).
     */
    Assets(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        if (build != null && build.getTime() != null) {
            version = String.valueOf(build.getTime().getEpochSecond());
        }
    }

    /** Линкларга қўйиладиган версия белгиси ({@code ?v=} қиймати). */
    public static String version() {
        return version;
    }
}
