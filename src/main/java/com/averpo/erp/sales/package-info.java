/**
 * Сотув модули (7-босқич, docs/modules/sales.md): Invoice - омбордан
 * чиқим (COGS) - AR - InvoicePayment (тушум, allocation билан) -
 * AR aging. Purchases (6-босқич)нинг кўзгу акси.
 *
 * <p>Бошқа модулларга фақат public service'лар орқали мурожаат қилади
 * (ТЕМИР ҚОИДА №6); GL - фақат PostingService (№2); омбор - фақат
 * InventoryService.
 */
package com.averpo.erp.sales;
