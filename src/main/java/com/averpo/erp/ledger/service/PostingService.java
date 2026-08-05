package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.JournalEntry;

import java.time.LocalDate;
import java.util.UUID;

/**
 * GL'га ёзишнинг ЯГОНА нуқтаси - ТЕМИР ҚОИДА №2.
 *
 * <p>Бошқа модуллар JournalEntry'ни ўзи яратмайди/сақламайди - фақат
 * шу интерфейс орқали. Инвариантлар (баланс, XOR, postable ва ҳ.к.)
 * docs/posting-rules.md да, валидация тартиби docs/modules/ledger.md да.
 *
 * <p><b>Икки қатламли модель (SAP фалсафаси)</b>: SAP'да операцион
 * ҳужжатлар ҳар домен бўйича алоҳида сақланади (VBAK/VBAP сотув,
 * EKKO/EKPO харид, S/4HANA'да MATDOC товар ҳаракати) ва account
 * determination орқали АЛОҲИДА бухгалтерия ҳужжатига проводка қилади.
 * Бизда ҳам худди шундай: Invoice/Bill/Payment - операцион ҳужжат,
 * journal_entry - бухгалтерия ҳақиқати, боғловчи - sourceModule +
 * sourceDocumentId. Модул ўз қолдиғини сақламайди; ҳар қолдиқ ва ҳар
 * ҳисобот рақами бош китобдан ҳисобланади (SAP'нинг ACDOCA Universal
 * Journal ғояси - субледжерлар орасида солиштириш зарурати йўқолади).
 *
 * <p><b>Ўзгармаслик (қоида №3) - қиёсий контекст</b>: post қилинган
 * ёзув ҳеч қачон таҳрирланмайди, тузатиш фақат сторно билан.
 * <ul>
 *   <li><b>SAP</b> - худди шундай қатъий: суммалар ўзгармас, тузатиш
 *       мажбурий сабаб коди билан сторно; кейин фақат қиймат бўлмаган
 *       майдонлар (референс, изоҳ) ўзгартирилади.</li>
 *   <li><b>Xero</b> - анча юмшоқ: тасдиқланган, тўланмаган счёт-фактура
 *       суммаси билан таҳрирланади; ўзгармаслик кафолати фақат journal
 *       қатламида (таҳрирлаш = автоматик сторно + янги ID билан қайта
 *       ёзиш).</li>
 *   <li><b>NetSuite</b> - созламага боғлиқ: default'да void оригинални
 *       ЎЗГАРТИРАДИ (суммани нолга туширади); «reversing journal»
 *       опцияси ёқилгандагина оригинал сақланади.</li>
 * </ul>
 * Демак бизнинг reverse-only модели аудит изи жиҳатидан SME
 * сегментидаги маҳсулотлардан қатъийроқ.
 */
public interface PostingService {

    /**
     * DRAFT entry яратади. Структура валидацияси (сатрлар сони, XOR,
     * счёт мавжудлиги, Money инварианти) доим ишлайди; баланс фақат
     * post пайтида текширилади - draft мувозанатсиз сақланиши мумкин.
     *
     * @throws PostingException структура валидацияси бузилса
     */
    JournalEntry createDraft(JournalEntryRequest request);

    /**
     * Тўлиқ валидация + POSTED. Шундан кейин entry ўзгармас.
     *
     * @throws PostingException инвариантлар бузилса ёки entry DRAFT бўлмаса
     */
    JournalEntry post(UUID entryId);

    /** createDraft + post битта транзакцияда - модуллар учун асосий йўл. */
    JournalEntry createAndPost(JournalEntryRequest request);

    /**
     * DRAFT entry'ни ўчиради. GL lifecycle'ининг ўчириш нуқтаси ҳам шу
     * ерда туради (қоида №3: POSTED/REVERSED ўзгармас - ўчирилмайди ҳам);
     * келгуси давр қулфи/audit сиёсатлари controller'ни четлаб ўтмасин
     * деб repository delete service'га кўчирилган.
     *
     * @return ўчирилган entry (рақами фойдаланувчи хабари учун)
     * @throws PostingException BR-LED-013 - entry DRAFT бўлмаса
     */
    JournalEntry deleteDraft(UUID entryId);

    /**
     * Сторно: суммалари тескари янги entry яратиб post қилади,
     * асл entry REVERSED бўлади. Иккаласи ҳам GL'да қолади -
     * нетто таъсир нолга тушади.
     *
     * @throws PostingException entry POSTED бўлмаса
     */
    JournalEntry reverse(UUID entryId, LocalDate reversalDate, String reason);

    /**
     * Манба ҳужжат бўйича сторно: (sourceModule, sourceDocumentId)нинг
     * фаол GL ёзувини топиб {@link #reverse} қилади - ҳужжат модуллари
     * (Bill, Payment...) entry id'сини сақламасдан ўз ҳужжатини
     * қайтара олади (ledger repo'сига тегмасдан, қоида №6).
     *
     * @throws PostingException BR-LED-017 - манба бўйича фаол entry
     *         топилмаса; BR-LED-009 - entry POSTED бўлмаса
     */
    JournalEntry reverseBySource(String sourceModule, UUID sourceDocumentId,
                                 LocalDate reversalDate, String reason);
}
