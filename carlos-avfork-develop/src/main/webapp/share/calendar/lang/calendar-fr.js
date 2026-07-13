/**
 * jscalendar French language → Flatpickr shim.
 *
 * @see https://github.com/carlos-emr/carlos/issues/1355
 * @since 2026-04-08
 */

Calendar._DN = ["Dimanche","Lundi","Mardi","Mercredi","Jeudi","Vendredi","Samedi","Dimanche"];
Calendar._SDN = ["Dim","Lun","Mar","Mer","Jeu","Ven","Sam","Dim"];
Calendar._FD = 1;
Calendar._MN = ["Janvier","Février","Mars","Avril","Mai","Juin","Juillet","Août","Septembre","Octobre","Novembre","Décembre"];
Calendar._SMN = ["janv.","févr.","mars","avr.","mai","juin","juil.","août","sept.","oct.","nov.","déc."];

Calendar._TT = {};
Calendar._TT["DEF_DATE_FORMAT"] = "%d.%m.%Y";
Calendar._TT["TT_DATE_FORMAT"] = "%A, %e %B";
Calendar._TT["WK"] = "sem.";
Calendar._TT["TIME"] = "Heure:";
Calendar._TT["CLOSE"] = "Fermer";
Calendar._TT["TODAY"] = "Aujourd'hui";

Calendar._flatpickrLocale = "fr";

/* Defer flatpickr French locale loading until flatpickr is ready.
 * Always use _pendingLocaleUrl so that _ensureFlatpickr / _flushPending
 * load the locale before any Calendar.setup() calls execute, even when
 * flatpickr is already cached. */
(function () {
    var scripts = document.getElementsByTagName("script");
    for (var i = 0; i < scripts.length; i++) {
        var src = scripts[i].src || "";
        var idx = src.indexOf("share/calendar/");
        if (idx !== -1) {
            Calendar._pendingLocaleUrl = src.substring(0, idx) + "library/flatpickr/l10n/fr.js";
            return;
        }
    }
})();
