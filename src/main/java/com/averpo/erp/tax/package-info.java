/**
 * ҚҚС каталоги ва ҳужжат солиғи (docs/modules/tax.md). QBO global (VAT)
 * услуби: TaxRate каталоги + Bill/Invoice сатрида ставка snapshot + GL'да
 * битта контрол счёт (SALES_TAX_PAYABLE). Bill/Invoice service'лари бу
 * модулга фақат public TaxRateService/TaxAmounts орқали боғланади
 * (темир қоида №6). GL - фақат PostingService (№2), tax модули GL'га
 * тўғридан-тўғри ёзмайди.
 */
package com.averpo.erp.tax;
