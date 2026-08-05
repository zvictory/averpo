package com.averpo.erp.shared.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.averpo.erp.shared.domain.CompanySettings;

import java.util.Optional;
import java.util.UUID;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {

    Optional<CompanySettings> findFirstBy();
}
