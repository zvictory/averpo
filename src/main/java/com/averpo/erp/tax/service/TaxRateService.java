package com.averpo.erp.tax.service;

import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.tax.domain.TaxRate;
import com.averpo.erp.tax.repo.TaxRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * ҚҚС ставкалари каталогининг ягона public API'си
 * (docs/modules/tax.md). Бошқа модуллар (purchase/sales) фақат шу
 * орқали мурожаат қилади (темир қоида №6): ставка танлаш, snapshot
 * қиймат ва мавжудлик/фаоллик текшируви.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaxRateService {

    /** Ставкалар репозиторийси. */
    private final TaxRateRepository repository;

    /** Созламалар экрани учун барчаси (нофаоллар билан), код тартибида. */
    @Transactional(readOnly = true)
    public List<TaxRate> all() {
        return repository.findAllByOrderByCode();
    }

    /** Ҳужжат формаси select'и учун фаоллар. */
    @Transactional(readOnly = true)
    public List<TaxRate> activeRates() {
        return repository.findByActiveTrueOrderByCode();
    }

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public TaxRate get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ставка топилмади: " + id));
    }

    /**
     * Янги ставка яратади - код unique (BR-TAX-001), ном бўш эмас
     * (BR-TAX-005), фоиз 0..100 (BR-TAX-002).
     */
    public TaxRate create(String code, String name, BigDecimal rate) {
        String normalizedCode = requireCode(code, null);
        return repository.save(new TaxRate(normalizedCode,
                requireName(name), requireRate(rate)));
    }

    /**
     * Ставкани таҳрирлайди - код/ном/фоиз/фаоллик. Фоиз ўзгариши
     * тарихий ҳужжатларни бузмайди (улар snapshot сақлайди).
     *
     * @throws BusinessRuleException BR-TAX-001/002/005
     */
    public TaxRate update(UUID id, String code, String name,
                          BigDecimal rate, boolean active) {
        TaxRate taxRate = get(id);
        taxRate.update(requireCode(code, id), requireName(name),
                requireRate(rate), active);
        return taxRate;
    }

    /**
     * Ҳужжат сатри учун ставка қийматини қайтаради - snapshot паттерни
     * (Money.exchangeRate / UoM unit_factor). Bill/Invoice валидацияси
     * шу орқали: мавжудлик (BR-TAX-004) ва фаоллик (BR-TAX-003)
     * текширилади, кейин snapshot қиймати (draft'да сақланган) ёки
     * каталог қиймати қайтади.
     *
     * <p>Нега snapshot устун: draft яратилгандан кейин каталогда ставка
     * ўзгарса, пост draft'даги қийматни ишлатиши шарт (spec «Ставка
     * snapshot»). Фаоллик эса ҳар сафар қайта текширилади - draft'дан
     * кейин ставка нофаол қилинса пост тўсилади (BR-TAX-003 «post'да
     * қайта текширилади»).
     *
     * @param taxRateId ставка id'си ёки null - солиқсиз (null қайтади)
     * @param snapshot  draft сатрдаги сақланган қиймат ёки null - янги сатр
     * @return ҳисоб учун ставка фоизи ёки null (солиқсиз)
     * @throws BusinessRuleException BR-TAX-004 (топилмади), BR-TAX-003 (нофаол)
     */
    @Transactional(readOnly = true)
    public BigDecimal documentRateValue(UUID taxRateId, BigDecimal snapshot) {
        if (taxRateId == null) {
            return null;
        }
        TaxRate taxRate = repository.findById(taxRateId)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_TAX_004,
                        "Танланган ставка каталогда йўқ: " + taxRateId));
        if (!taxRate.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_TAX_003,
                    "Нофаол ставка танланмайди: " + taxRate.getCode());
        }
        return snapshot != null ? snapshot : taxRate.getRate();
    }

    // ---- инвариантлар ----

    /** BR-TAX-001: код бўш эмас ва unique (ўзидан бошқада банд эмас). */
    private String requireCode(String code, UUID selfId) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_TAX_001,
                    "Ставка коди бўш бўлмайди");
        }
        String normalized = code.strip().toUpperCase();
        repository.findByCode(normalized)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_TAX_001,
                            "Бу код банд: " + normalized);
                });
        return normalized;
    }

    /** BR-TAX-005: ном бўш эмас. */
    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_TAX_005,
                    "Ставка номи бўш бўлмайди");
        }
        return name.strip();
    }

    /** BR-TAX-002: фоиз 0..100 оралиғида. */
    private BigDecimal requireRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0
                || rate.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_TAX_002,
                    "Ставка 0..100 оралиғида бўлиши шарт: " + rate);
        }
        return rate;
    }
}
