package com.averpo.erp.shared.service;

import com.averpo.erp.shared.service.ExcelImportService.ImportResult;

/**
 * Excel'дан бошланғич import муваффақиятли қўлланди -
 * {@code ExcelImportService.apply} якунида эълон қилинади (DEC-062,
 * IMPORT_EXCEL). Синхрон listener туфайли apply rollback бўлса аудит
 * ёзуви ҳам йўқолади (журнал фақат содир бўлган ишни акс эттиради).
 *
 * <p>Event shared ичида туради, тингловчи audit модулида
 * (CompanySettingsChangedEvent изоҳидаги цикл сабаби).
 *
 * @param result туркумлаб яратилган/ўтказилган сонлар - details матнини
 *               listener шундан ясайди
 */
public record ExcelImportedEvent(ImportResult result) {
}
