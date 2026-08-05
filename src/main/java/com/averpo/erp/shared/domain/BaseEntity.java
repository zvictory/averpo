package com.averpo.erp.shared.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Барча entity'лар учун асос: UUIDv7 id, optimistic lock, audit саналар.
 *
 * <p>Id {@link Uuid7} орқали конструкцияда тайинланади. Id олдиндан
 * тўлдирилгани учун Spring Data уни «мавжуд» деб ўйлаб {@code merge}
 * қилмаслиги учун {@link Persistable} имплементация қилинган:
 * {@code isNew} байроғи persist/load'дан кейин {@code false} бўлади -
 * шунда {@code save()} янги entity учун тўғри {@code persist} танлайди.
 *
 * <p>Lombok {@code @Getter} Persistable'нинг getId()/isNew() талабларини
 * ҳам қоплайди. equals/hashCode қўлда - фақат id бўйича (identity);
 * Lombok'нинг {@code @EqualsAndHashCode}'и бу семантикани бузар эди,
 * шунинг учун entity'ларда у ТАҚИҚ (CLAUDE.md).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity implements Persistable<UUID> {

    /** UUIDv7 PK - конструкцияда тайинланади, Hibernate генератори йўқ. */
    @Id
    private UUID id = Uuid7.next();

    /** Optimistic lock версияси. */
    @Version
    private int version;

    /** Persistable байроғи: persist/load'дан кейин false бўлади. */
    @Transient
    private boolean isNew = true;

    /** Яратилган вақт (UTC) - JPA auditing тўлдиради. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Ёзувни киритган фойдаланувчи id'си (user-management.md, Eldor-004
     * §7) - SecurityAuditorAware тўлдиради. Auth контекстисиз ёзувларда
     * (scheduler, bootstrap, миграциядан олдинги ёзувлар) NULL қолади -
     * сохта атрибуция қилинмайди. FK атайлаб йўқ - dimension паттерни:
     * shared модул security'га боғланмайди, майдон тоза UUID.
     */
    @org.springframework.data.annotation.CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** Охирги ўзгариш вақти (UTC) - JPA auditing тўлдиради. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Persist/load'дан кейин entity «янги» ҳисобланмайди. */
    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((BaseEntity) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
