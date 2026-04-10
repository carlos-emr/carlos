/**
 * jscalendar Polish language → Flatpickr shim.
 *
 * @see https://github.com/carlos-emr/carlos/issues/1355
 * @since 2026-04-08
 */

Calendar._DN = ["Niedziela","Poniedziałek","Wtorek","Środa","Czwartek","Piątek","Sobota","Niedziela"];
Calendar._SDN = ["Nie","Pon","Wto","Śro","Czw","Pią","Sob","Nie"];
Calendar._FD = 1;
Calendar._MN = ["Styczeń","Luty","Marzec","Kwiecień","Maj","Czerwiec","Lipiec","Sierpień","Wrzesień","Październik","Listopad","Grudzień"];
Calendar._SMN = ["Sty","Lut","Mar","Kwi","Maj","Cze","Lip","Sie","Wrz","Paź","Lis","Gru"];

Calendar._TT = {};
Calendar._TT["DEF_DATE_FORMAT"] = "%Y-%m-%d";
Calendar._TT["TT_DATE_FORMAT"] = "%a, %b %e";
Calendar._TT["WK"] = "tydz.";
Calendar._TT["TIME"] = "Czas:";
Calendar._TT["CLOSE"] = "Zamknij";
Calendar._TT["TODAY"] = "Dziś";

Calendar._flatpickrLocale = "pl";

/* Defer flatpickr Polish locale loading until flatpickr is ready.
 * Always use _pendingLocaleUrl so that _ensureFlatpickr / _flushPending
 * load the locale before any Calendar.setup() calls execute, even when
 * flatpickr is already cached. */
(function () {
    var scripts = document.getElementsByTagName("script");
    for (var i = 0; i < scripts.length; i++) {
        var src = scripts[i].src || "";
        var idx = src.indexOf("share/calendar/");
        if (idx !== -1) {
            Calendar._pendingLocaleUrl = src.substring(0, idx) + "library/flatpickr/l10n/pl.js";
            return;
        }
    }
})();
