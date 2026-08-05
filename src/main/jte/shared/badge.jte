@import com.averpo.erp.i18n.Msg
@param String value
@param Msg msg
@param String family = "status"
@param String label = null

<%-- Arbitr-119 (Penguin badge, реф: docs/penguin-refs/badge.html):
     статус чипи. РАНГ ХАРИТАСИ БИР ЖОЙДА - шу ерда; экранлар ўз ранг
     танламайди. value - статус/тур enum номи (POSTED, PAID, OPEN...),
     family - i18n калит префикси ва оила: status / paystatus /
     est.status / po.status / rcn.status / plugin.state / classification.
     Тон харитаси (098 меросхўри) - status оилалари:
       success (яшил)    POSTED, PAID, ACCEPTED, OPEN, COMPLETED, ENABLED
       warning (сариқ)   DRAFT, PARTIAL, PENDING, IN_PROGRESS
       danger (қизил)    UNPAID, REJECTED
       нейтрал (кулранг) REVERSED, CLOSED, DISABLED + номаълум қиймат
                         (хавфсиз default)
     classification оиласи (Arbitr-149, CoA тур badge'и) family бўйича
     АЛОҲИДА, ЮМШОҚ (soft) харита - status'нинг тўлиқ рангидан фарқли
     (фойдаланувчи 2026-07-18: жадвалда юмшоқроқ): фон /15, матн тўлиқ
     ранг, чегара /30:
       primary (кўк)     ASSET
       warning (сариқ)   LIABILITY
       success (яшил)    REVENUE
       danger (қизил)    EXPENSE
       нейтрал (кулранг) EQUITY
     Status тўлиқ ранглари (success/warning/danger) @theme'да dark эгизсиз;
     dark:* нейтрал ва primary (ASSET) soft вариантларида бор - primary
     @theme'да dark эгизли (createButton қолипи). label параметри берилса
     i18n localize ЎРНИГА ўша матн кўрсатилади (classification badge'да
     инглизча ном - фойдаланувчи қарори). whitespace-nowrap - чип жадвал
     катагида синмасин (Averpo мослашуви, реф файлида изоҳланган). --%>
!{var tone = "classification".equals(family)
        ? ("ASSET".equals(value) ? "border-primary/30 bg-primary/15 text-primary dark:border-primary-dark/30 dark:bg-primary-dark/20 dark:text-primary-dark"
                : "LIABILITY".equals(value) ? "border-warning/30 bg-warning/15 text-warning"
                : "REVENUE".equals(value) ? "border-success/30 bg-success/15 text-success"
                : "EXPENSE".equals(value) ? "border-danger/30 bg-danger/15 text-danger"
                : "border-outline bg-surface-alt text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark")
        : "POSTED".equals(value) || "PAID".equals(value) || "ACCEPTED".equals(value) || "OPEN".equals(value) || "COMPLETED".equals(value) || "ENABLED".equals(value) ? "border-success bg-success text-on-success"
        : "DRAFT".equals(value) || "PARTIAL".equals(value) || "PENDING".equals(value) || "IN_PROGRESS".equals(value) ? "border-warning bg-warning text-on-warning"
        : "UNPAID".equals(value) || "REJECTED".equals(value) ? "border-danger bg-danger text-on-danger"
        : "border-outline bg-surface-alt text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark";}
<%-- lookup (String) - localize() Content қайтаргани учун label (String)
     билан тернар Object'га тушиб JTE writeUserContent'ни бузарди; i18n
     матни оддий (HTML йўқ), ${display} escape'и кифоя --%>
!{var display = label != null ? label : msg.lookup(family + "." + value);}
<span class="w-fit whitespace-nowrap rounded-radius border px-2 py-1 text-xs font-medium ${tone}">${display}</span>
