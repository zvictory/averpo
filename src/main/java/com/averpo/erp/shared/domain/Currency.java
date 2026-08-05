package com.averpo.erp.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Валюта каталоги - алоҳида domain entity (темир қоида №11).
 *
 * <p>{@link Money} ичида JOIN'сиз ишлаш учун ISO код denormalized
 * сақланади; бу каталог эса валидация (мавжуд ва фаолми?) ва UI
 * рўйхатлари учун ягона манба. QBO услуби: керакли валюта
 * фаоллаштирилади, қолганлари рўйхатда туради.
 */
@Entity
@Table(name = "currency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Currency extends BaseEntity {

    /** ISO 4217 коди: UZS, USD, EUR... - unique. */
    @Column(nullable = false, unique = true, length = 3)
    private String code;

    /** Кирилл номи: «АҚШ доллари». */
    @Column(nullable = false)
    private String name;

    /** Белгиси: so'm, $, €... - суммалар ёнида кўрсатилади. */
    @Column(length = 8)
    private String symbol;

    /** Фаол валютагина ҳужжатларда танланади (QBO услуби). */
    @Column(nullable = false)
    private boolean active = true;

    /** Янги валюта - каталогга қўшишда ишлатилади. */
    public Currency(String code, String name, String symbol, boolean active) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.active = active;
    }

    /** Ном/белгини янгилаш - код ўзгармайди (unique идентификатор). */
    public void update(String name, String symbol, boolean active) {
        this.name = name;
        this.symbol = symbol;
        this.active = active;
    }
}
