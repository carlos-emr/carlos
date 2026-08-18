/**
 * jscalendar Spanish language → Flatpickr shim.
 *
 * @see https://github.com/carlos-emr/carlos/issues/1355
 * @since 2026-04-08
 */

Calendar._DN = ["Domingo","Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"];
Calendar._SDN = ["Dom","Lun","Mar","Mié","Jue","Vie","Sáb","Dom"];
Calendar._FD = 1;
Calendar._MN = ["Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"];
Calendar._SMN = ["Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"];

Calendar._TT = {};
Calendar._TT["DEF_DATE_FORMAT"] = "%d/%m/%Y";
Calendar._TT["TT_DATE_FORMAT"] = "%a, %b %e";
Calendar._TT["WK"] = "sem.";
Calendar._TT["TIME"] = "Hora:";
Calendar._TT["CLOSE"] = "Cerrar";
Calendar._TT["TODAY"] = "Hoy";

Calendar._flatpickrLocale = "es";

/* Defer flatpickr Spanish locale loading until flatpickr is ready.
 * Always use _pendingLocaleUrl so that _ensureFlatpickr / _flushPending
 * load the locale before any Calendar.setup() calls execute, even when
 * flatpickr is already cached. */
(function () {
    var scripts = document.getElementsByTagName("script");
    for (var i = 0; i < scripts.length; i++) {
        var src = scripts[i].src || "";
        var idx = src.indexOf("share/calendar/");
        if (idx !== -1) {
            Calendar._pendingLocaleUrl = src.substring(0, idx) + "library/flatpickr/l10n/es.js";
            return;
        }
    }
})();
