/**
 * ИШ ҲАҚИ МОДУЛИ (Payroll Lite - docs/modules/payroll.md). Ойлик
 * ҳисоблаш (PayrollRun), аванс/ойлик тўлови (PayrollPayment) ва
 * ведомость. QBO ядросида payroll ЙЎҚ - бу лойиҳанинг иккинчи атайлаб
 * фарқи; лекин ядро нақшлари (PostingService орқали GL, detail type
 * счёт resolve, contact dimension, POSTED/reverse) тўлиқ қайта
 * ишлатилади. Ҳамма ҳужжатлар фақат home валютада (BR-PYR-001).
 *
 * <p>Боғлиқлик шартномаси: payroll → contact/ledger/shared фақат
 * public service орқали (қоида №6); ходим кесимидаги қолдиқ
 * PAYROLL_CLEARING субледжеридан (GL contact кесими) ўқилади.
 */
package com.averpo.erp.payroll;
