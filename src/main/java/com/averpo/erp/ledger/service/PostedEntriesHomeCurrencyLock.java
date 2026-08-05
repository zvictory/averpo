package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.shared.service.HomeCurrencyLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Home currency қулфи: GL'да биттагина POSTED (ёки REVERSED) entry
 * пайдо бўлиши билан валюта ўзгартириш ёпилади - мавжуд baseAmount'лар
 * бошқа валютада қайта талқин қилиниб кетмасин.
 *
 * @author Zafar
 */
@Component
@RequiredArgsConstructor
class PostedEntriesHomeCurrencyLock implements HomeCurrencyLock {

    /** POSTED entry борлигини текшириш учун. */
    private final JournalEntryRepository entryRepository;

    /** {@inheritDoc} */
    @Override
    public boolean locked() {
        return entryRepository.existsByStatusIn(
                List.of(EntryStatus.POSTED, EntryStatus.REVERSED));
    }
}
