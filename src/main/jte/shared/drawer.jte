<%-- DEC-024: каталог формалари учун ўнгдан чиқувчи drawer (QBO услуби).
     layout/main.jte ҳар саҳифага қўшади; «+ Янги» ва rowMenu «Таҳрир»
     hx-get билан #drawerBody'га форма partial'ини юклайди - swap бўлгач
     очилади. X, backdrop ва ESC ёпади; ёпилганда контент тозаланади
     (эски форма ҳолати қолиб кетмасин). Кўриниш Penguin утилиталарда
     (DEC-121): .on - ФАҚАТ ҳолат маркери (JS қўяди/олади),
     услуб [&.on]/[.on_&] arbitrary variant'ларда. Ички тузилма
     (drawer-head/body/foot) partial'ларда - drawer-body услубсиз
     зона маркери (тест мосламалари унга боғланган). --%>
<div class="drawer pointer-events-none fixed inset-0 z-[90] [&.on]:pointer-events-auto print:hidden" id="drawer">
    <div class="absolute inset-0 bg-surface-dark/45 opacity-0 transition-opacity duration-200 [.on_&]:opacity-100" onclick="drawerClose()"></div>
    <aside class="absolute inset-y-0 right-0 flex w-[480px] max-w-full translate-x-full flex-col bg-surface shadow-[-12px_0_30px_rgba(0,0,0,.18)] transition-transform duration-[220ms] [.on_&]:translate-x-0 max-md:w-full dark:bg-surface-dark" id="drawerBody" role="dialog" aria-modal="true"></aside>
</div>
<script>
    function drawerClose() {
        var drawer = document.getElementById('drawer');
        if (!drawer.classList.contains('on')) return;
        drawer.classList.remove('on');
        // Анимация тугагач тозалаш - кейинги очилиш тоза бошлансин
        setTimeout(function () { document.getElementById('drawerBody').innerHTML = ''; }, 250);
    }
    document.body.addEventListener('htmx:afterSwap', function (e) {
        if (e.target && e.target.id === 'drawerBody' && e.target.innerHTML.trim() !== '') {
            document.getElementById('drawer').classList.add('on');
            // Очиқ қолган row-menu'лар ёпилсин (Alpine click.outside)
            document.body.click();
        }
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') drawerClose();
    });
</script>
