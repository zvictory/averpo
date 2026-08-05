/**
 * Банк модули (8-босқич, docs/modules/banking.md): банк транзакциялари
 * (DEPOSIT/EXPENSE/TRANSFER + валюта конверсияси) ва QBO Reconcile
 * услубидаги солиштириш.
 *
 * <p>Бошқа модулларга фақат public service'лар орқали мурожаат қилади
 * (ТЕМИР ҚОИДА №6); GL - фақат PostingService (№2). Reconciliation
 * match'лари шу модулда - ledger схемасига тегилмайди.
 */
package com.averpo.erp.bank;
