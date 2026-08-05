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
 * @author Zafar
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
