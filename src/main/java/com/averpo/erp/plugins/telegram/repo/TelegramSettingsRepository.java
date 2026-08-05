package com.averpo.erp.plugins.telegram.repo;

import com.averpo.erp.plugins.telegram.domain.TelegramSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Telegram созламаси singleton қатори - ФАҚАТ {@code TelegramService}
 * ишлатади (модуллараро мурожаат public service орқали, темир қоида 6).
 *
 * @author Zafar
 */
public interface TelegramSettingsRepository extends JpaRepository<TelegramSettings, UUID> {

    /**
     * Ягона қатор (CompanySettingsRepository.findFirstBy нақши) - жадвалда
     * кўпи билан битта сатр бўлади, service уни lazy яратади.
     */
    Optional<TelegramSettings> findFirstBy();
}
