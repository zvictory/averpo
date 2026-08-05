/**
 * ХАВФСИЗЛИК МОДУЛИ. Form login + роллар (ADMIN/ACCOUNTANT/VIEWER),
 * фойдаланувчи бошқаруви, login lockout (BR-USR-*). Тўлиқ spec:
 * docs/modules/user-management.md.
 *
 * <p>Боғлиқлик шартномаси: security → audit РУХСАТЛИ - LOCKOUT ва
 * user-management ҳодисалари AuditLogService.record орқали ёзилади;
 * тескариси ТАҚИҚ (цикл) - audit бу модулни import қилмайди, кириш
 * уринишларини framework event'ларидан ўзи тинглайди.
 */
package com.averpo.erp.security;
