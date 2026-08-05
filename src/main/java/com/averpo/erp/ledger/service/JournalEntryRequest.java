package com.averpo.erp.ledger.service;

import com.averpo.erp.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * PostingService'га узатиладиган проводка тавсифи.
 *
 * <p>Счётлар id орқали кўрсатилади - код ихтиёрий бўлгани учун (QBO
 * услуби) кодга таяниб бўлмайди. Модуллар тизим счётларини detail type
 * орқали топади (AccountRepository.findByDetailType), кейин id билан
 * проводка қилади.
 *
 * @param entryDate        проводка санаси (ҳужжат санаси)
 * @param description      эркин тавсиф
 * @param sourceModule     MANUAL, SALES, PURCHASE... - ҳужжат манбаси
 * @param sourceDocumentId манба ҳужжат id'си (қўлда проводкада null)
 * @param lines            камида 2 та сатр
 */
public record JournalEntryRequest(
        LocalDate entryDate,
        String description,
        String sourceModule,
        UUID sourceDocumentId,
        List<Line> lines) {

    /** Қўлда киритиладиган проводка учун қисқа йўл. */
    public static JournalEntryRequest manual(LocalDate entryDate, String description,
                                             List<Line> lines) {
        return new JournalEntryRequest(entryDate, description, "MANUAL", null, lines);
    }

    /**
     * Битта сатр: дебет ЁКИ кредит тўлдирилади, иккинчиси null.
     *
     * @param accountId   проводка қилинадиган счёт id'си
     * @param debit       дебет суммаси ёки null
     * @param credit      кредит суммаси ёки null
     * @param contactId   ихтиёрий dimension - контрагент
     * @param warehouseId ихтиёрий dimension - омбор
     * @param itemId      ихтиёрий dimension - товар
     * @param memo        сатр изоҳи
     * @param classId     ихтиёрий Class/Йўналиш теги (class-tracking.md):
     *                    ҳужжат сатридан айнан кўчади; назорат/техник
     *                    сатрларда null
     */
    public record Line(UUID accountId, Money debit, Money credit,
                       UUID contactId, UUID warehouseId, UUID itemId, String memo,
                       UUID classId) {

        /** Эски 7 майдонли имзо - class'сиз чақирувлар (назорат/техник сатрлар). */
        public Line(UUID accountId, Money debit, Money credit,
                    UUID contactId, UUID warehouseId, UUID itemId, String memo) {
            this(accountId, debit, credit, contactId, warehouseId, itemId, memo, null);
        }

        /** Дебет сатри учун қисқа фабрика. */
        public static Line debit(UUID accountId, Money amount, String memo) {
            return new Line(accountId, amount, null, null, null, null, memo);
        }

        /** Кредит сатри учун қисқа фабрика. */
        public static Line credit(UUID accountId, Money amount, String memo) {
            return new Line(accountId, null, amount, null, null, null, memo);
        }
    }
}
