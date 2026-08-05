# Penguin UI реф-нусхалари (вендоринг)

Спец қоидаси (docs/modules/ui-framework.md «Билим манбалари»):
ишлатилган Penguin компонентлари реф сифатида репога кўчирилади -
сайтга боғланиб қолмаймиз. Ҳар файл - Penguin'нинг «modern» (semantic
токенли) варианти, класс йиғимлари сайтдан айнан; биздаги мослашишлар
(роль гейти, i18n, HTMX, рейл режими) JTE файлларда изоҳланган.

| Файл | Penguin компоненти | Қаерда ишлатилган |
|---|---|---|
| sidebar-collapsible.html | Sidebar → Sidebar with collapsible menus | layout/sidebar.jte |
| profile-menu.html | Sidebar → Sidebar with menu (Profile Menu қисми) | layout/sidebar.jte (паст зона) |
| text-input.html | Text Input (default) | security/login.jte, sidebar қидируви |
| buttons.html | Buttons (primary) | login submit, sidebar «+ Янги» (success) |
| badge.html | Badge (solid + нейтрал) | shared/badge.jte (статус чиплари) |
| alert.html | Alert (success/danger) | shared/alert.jte (флеш хабарлар) |
| card.html | Card (ўрам) | view инфо-панеллари, dashboard карталари |
| modal.html | Modal (+палитра мослашуви) | layout/main.jte қидирув command-palette |
| table.html | Table (default + striped) | shared/table.jte фрагменти - барча жадваллар (DEC-120) |
| pagination.html | Paginations (next & preview) | shared/pagination.jte |
| dropdown-menu.html | Dropdowns (click) | shared/rowMenu.jte ⋮ менюси, ledger/accounts.jte |
| select.html | Select (default) | барча форма/филтр select'лари (DEC-121) |
| checkbox-radio.html | Checkbox + Radio | форма checkbox'лари (native+accent мослашуви) |
| combobox.html | Combobox (simple + with search) | shared/combobox.jte фрагменти - қидирувли танлагичлар (DEC-123) |

Semantic токенлар `src/main/css/app.css` @theme'дагилар (arctic):
surface / surface-alt / on-surface(-strong) / primary / on-primary /
secondary / outline + dark жуфтлари, radius-radius. Dark режим -
`<html class="dark">` (@custom-variant).
