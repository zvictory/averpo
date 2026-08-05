@param String message = null
@param String error = null

<%-- Arbitr-119 (Penguin alert, реф: docs/penguin-refs/alert.html): флеш
     хабар жуфти - controller redirect'дан келадиган message (success) /
     error (danger). Ихчам бир қаторли шакл (иконка/сарлавҳасиз - Averpo
     мослашуви реф файлида изоҳланган); role="alert" скрин-ридерга дарҳол
     ўқилади. Ҳолат ранглари @theme'да dark эгизсиз - ўрамнинг ўзи
     bg-surface dark жуфти билан. --%>
@if(message != null)
    <div class="relative mb-4 w-full overflow-hidden rounded-radius border border-success bg-surface text-on-surface dark:bg-surface-dark dark:text-on-surface-dark" role="alert">
        <div class="bg-success/10 p-3 text-sm font-medium text-success">${message}</div>
    </div>
@endif
@if(error != null)
    <div class="relative mb-4 w-full overflow-hidden rounded-radius border border-danger bg-surface text-on-surface dark:bg-surface-dark dark:text-on-surface-dark" role="alert">
        <div class="bg-danger/10 p-3 text-sm font-medium text-danger">${error}</div>
    </div>
@endif
