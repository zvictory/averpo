package com.averpo.erp.plugins.telegram.web;

import com.averpo.erp.plugins.telegram.service.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telegram webhook кириш нуқтаси (Arbitr-138, prod режим): Telegram
 * янгиликларни шу йўлга POST қилади. Дев'да polling ишлатилади - бу
 * endpoint prod учун (лекин route ҳар муҳитда мавжуд; хавфсизлик
 * секрет header'ида).
 *
 * <p><b>permitAll + CSRF ўчиқ</b> (SecurityConfig): Telegram
 * аутентификация ҳам, CSRF токен ҳам юбормайди - ҳимоя ФАҚАТ
 * {@code X-Telegram-Bot-Api-Secret-Token} header'ида (registrar яратган
 * сир билан constant-time таққос).
 *
 * <p><b>Текширув ТАРТИБИ</b> (хавфсизлик, арбитр 2026-07-17): секрет
 * ОЛДИН - плагин on/off ҳолати аутентификациясиз ошкор бўлмасин ва
 * handleUpdate'га секретсиз етиб борилмасин.
 * <ol>
 *   <li>секрет нотўғри/йўқ → <b>401</b> (плагин ҳолатидан қатъи назар);</li>
 *   <li>секрет тўғри, лекин плагин ўчиқ/токен йўқ → <b>200 жим</b>
 *       (Telegram 2xx кутади - акс ҳолда қайта юбориб туради);</li>
 *   <li>секрет тўғри + ёқиқ → handleUpdate → <b>200</b>.</li>
 * </ol>
 * Демак бу endpoint'да плагин гейти 404 бермайди (settings/профил
 * route'ларида қолади) - секрет'нинг ўзи «фақат Telegram» гарови.
 *
 * <p><b>Хавфсизлик</b>: тана ҳам, секрет ҳам ЛОГ/аудитга ёзилмайди
 * (ишончсиз кириш + сир); handleUpdate мавжуд қаттиқлашган мантиқ (103:
 * бир марталик SecureRandom линк код - сохта update фойда бермайди).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    /** Секрет таққос ва хабар ишлови - ягона хизмат. */
    private final TelegramService telegramService;

    /**
     * Telegram webhook POST'и. Секрет ОЛДИН текширилади (401 гейтдан
     * олдин), кейин ишлов. Ишлаб бўлмаган/бегона хабар ҳам 200 -
     * Telegram 2xx кўрмаса ўша update'ни қайта-қайта юборарди.
     */
    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody(required = false) String body) {
        if (!telegramService.webhookSecretValid(secret)) {
            // Сир нотўғри/йўқ - Telegram эмас. Лог'да секрет ҲАМ, тана
            // ҲАМ йўқ (факт кифоя; шовқин бўлмасин деб DEBUG)
            log.debug("Telegram webhook: секрет мос эмас - рад этилди");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Секрет тўғри - тана ишлов (плагин ўчиқ/токен йўқ бўлса жим).
        // handleWebhookBody ҳеч қандай хато отмайди - 200 доим
        telegramService.handleWebhookBody(body);
        return ResponseEntity.ok().build();
    }
}
