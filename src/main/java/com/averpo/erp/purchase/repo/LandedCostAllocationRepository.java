package com.averpo.erp.purchase.repo;

import com.averpo.erp.purchase.domain.LandedCostAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Landed cost тақсимоти репозиторийси - фақат purchase модули ичида.
 * JpaSpecificationExecutor - рўйхат филтри учун (Arbitr-068): эски
 * findAllByOrderBy... методи findAll(Specification, Sort)'га алмашди.
 */
public interface LandedCostAllocationRepository
        extends JpaRepository<LandedCostAllocation, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<LandedCostAllocation> {
}
