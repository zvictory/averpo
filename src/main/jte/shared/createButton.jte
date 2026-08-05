@import com.averpo.erp.i18n.Msg
@param Msg msg
@param String href = null
@param String hxGet = null

<%-- DEC-133: entity рўйхат саҳифаларининг ЯГОНА яратиш тугмаси -
     тепа ўнгда, plus иконка + бир сўзли лабел (common.create, «+ Янги»
     бўлиб кўринади). Кўриниш ва матн ҳамма рўйхат саҳифасида бир хил.
     Penguin primary канони + иконка учун inline-flex/gap.
     canEdit гейти БУ ЕРДА ЭМАС - чақирувчи саҳифада қолади (рухсат
     мантиғи саҳифаники, фрагмент фақат кўриниш).

     href режими: /new саҳифага ўтади. hxGet берилса drawer оқими
     (accounts/items/contacts) - href JS'сиз fallback бўлиб қолади.
     hxGet null бўлса JTE hx-* атрибутларини умуман чиқармайди. --%>
@if(href != null)
    <a href="${href}" hx-get="${hxGet}" hx-target="${hxGet == null ? null : "#drawerBody"}" hx-swap="${hxGet == null ? null : "innerHTML"}"
       class="inline-flex items-center gap-1.5 no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">
        @template.icons.plus(cls = "size-4 shrink-0")
        <span>${msg.localize("common.create")}</span>
    </a>
@endif
