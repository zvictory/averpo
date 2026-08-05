package com.averpo.erp.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Йўналиш (QBO Class, docs/modules/class-tracking.md) - даромад/
 * харажатни бизнес йўналишлари кесимида кузатувчи таҳлилий тег.
 * Java номи TxnClass - «Class» reserved сўз; UI label i18n орқали
 * (уз «Йўналиш», ru «Класс», en «Class»).
 *
 * <p>Sub-class ота орқали (QBO ParentRef), чуқурлик чекланмаган -
 * счёт дарахти нақши. Ўчириш ЙЎҚ - фақат active=false (GL тарихида
 * ишлатилган бўлиши мумкин). GL суммаларига МУТЛАҚО таъсир қилмайди.
 *
 * @author Zafar
 */
@Entity
@Table(name = "txn_class")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TxnClass extends BaseEntity {

    /** Кўрсатиладиган ном - QBO Name чегараси (100), ота ичида ноёб (BR-CLS-002). */
    @Column(nullable = false, length = 100)
    private String name;

    /** Ота class (sub-class бўлса) - «Ота:Бола» тўлиқ ном шундан ҳисобланади. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private TxnClass parent;

    /** Нофаол class янги ҳужжат select'ида кўринмайди (BR-CLS-001). */
    @Column(nullable = false)
    private boolean active = true;

    /** Янги class - фаол ҳолда, ихтиёрий ота билан (валидация service'да). */
    public TxnClass(String name, TxnClass parent) {
        this.name = name;
        this.parent = parent;
    }

    /** Номлаш (BR-CLS-002 текшируви service'да). */
    public void rename(String name) {
        this.name = name;
    }

    /** Ота алмаштириш - цикл гарови (BR-CLS-003) service'да. */
    public void changeParent(TxnClass parent) {
        this.parent = parent;
    }

    /** Фаолликни алмаштиради (TxnClassService чақиради). */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Тўлиқ ном «Ота:Бола» (QBO FullyQualifiedName) - сақланмайди,
     * ҳисобланади (QBO'да ҳам output-only). Илдиздан бошлаб йиғилади.
     */
    public String fullyQualifiedName() {
        return parent == null ? name : parent.fullyQualifiedName() + ":" + name;
    }
}
