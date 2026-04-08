/**
 * jscalendar Portuguese language → Flatpickr shim.
 *
 * @see https://github.com/carlos-emr/carlos/issues/1355
 * @since 2026-04-08
 */

Calendar._DN = ["Domingo","Segunda","Terça","Quarta","Quinta","Sexta","Sábado","Domingo"];
Calendar._SDN = ["Dom","Seg","Ter","Qua","Qui","Sex","Sáb","Dom"];
Calendar._FD = 1;
Calendar._MN = ["Janeiro","Fevereiro","Março","Abril","Maio","Junho","Julho","Agosto","Setembro","Outubro","Novembro","Dezembro"];
Calendar._SMN = ["Jan","Fev","Mar","Abr","Mai","Jun","Jul","Ago","Set","Out","Nov","Dez"];

Calendar._TT = {};
Calendar._TT["DEF_DATE_FORMAT"] = "%d/%m/%Y";
Calendar._TT["TT_DATE_FORMAT"] = "%a, %b %e";
Calendar._TT["WK"] = "sem.";
Calendar._TT["TIME"] = "Hora:";
Calendar._TT["CLOSE"] = "Fechar";
Calendar._TT["TODAY"] = "Hoje";

Calendar._flatpickrLocale = "pt";

/* Load the flatpickr Portuguese locale file alongside this script */
(function () {
    var scripts = document.getElementsByTagName("script");
    for (var i = 0; i < scripts.length; i++) {
        var src = scripts[i].src || "";
        var idx = src.indexOf("share/calendar/");
        if (idx !== -1) {
            var js = document.createElement("script");
            js.src = src.substring(0, idx) + "library/flatpickr/l10n/pt.js";
            document.head.appendChild(js);
            return;
        }
    }
})();
