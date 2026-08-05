package com.averpo.erp.contact.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Контактнинг структурали манзили (QBO услуби, old-erp-ideas §3).
 *
 * <p>Ҳар (контакт, тур)да биттагина default - DB'даги
 * ux_contact_address_default partial unique кафолатлайди, алмашув
 * мантиғи ContactService'да (янги default эскисини бўшатади).
 * Таҳрирлаш MVP'да йўқ - ўчириб қайта қўшилади (spec).
 */
@Entity
@Table(name = "contact_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactAddress extends BaseEntity {

    /** Эгаси - манзиллар фақат контакт кесимида ишлатилади. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    /** Манзил тури - BILLING/SHIPPING/LEGAL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType;

    /** Асосий қатор (кўча, уй) - мажбурий (BR-CON-007). 500: эски
     * оддий матн манзиллар мигратсияда шу майдонга сиққан. */
    @Column(name = "address_line1", nullable = false, length = 500)
    private String addressLine1;

    /** Қўшимча қатор (офис, блок). */
    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    /** Шаҳар. */
    @Column(length = 100)
    private String city;

    /** Вилоят/минтақа. */
    @Column(length = 100)
    private String region;

    /** Почта индекси. */
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /** Давлат коди (ISO 3166-1 alpha-2, upper-case). */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** Турдаги default манзил белгиси. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    /** Янги манзил - ҳамма майдонлар билан (валидация service'да). */
    public ContactAddress(Contact contact, AddressType addressType,
                          String addressLine1, String addressLine2,
                          String city, String region, String postalCode,
                          String countryCode, boolean defaultAddress) {
        this.contact = contact;
        this.addressType = addressType;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.region = region;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.defaultAddress = defaultAddress;
    }

    /** Янги default келганда эскисини бўшатиш учун (фақат service чақиради). */
    public void clearDefault() { this.defaultAddress = false; }

    /**
     * Жадвалда бир қаторлик кўриниш: бўш бўлмаган қисмлар вергул билан
     * жамланади - шаблонда null текширувлари такрорланмасин.
     */
    public String shortText() {
        StringBuilder sb = new StringBuilder(addressLine1);
        for (String part : new String[]{addressLine2, city, region, postalCode, countryCode}) {
            if (part != null && !part.isBlank()) {
                sb.append(", ").append(part);
            }
        }
        return sb.toString();
    }
}
