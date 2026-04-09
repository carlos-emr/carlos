/**
 * jscalendar English language → Flatpickr shim.
 *
 * Maintains backward-compatible Calendar._DN, _MN, _TT, _FD properties
 * for any code that reads them directly, while setting the flatpickr locale
 * to English (flatpickr default — no separate locale file needed).
 *
 * @see https://github.com/carlos-emr/carlos/issues/1355
 * @since 2026-04-08
 */

Calendar._DN = ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"];
Calendar._SDN = ["Sun","Mon","Tue","Wed","Thu","Fri","Sat","Sun"];
Calendar._FD = 0;
Calendar._MN = ["January","February","March","April","May","June","July","August","September","October","November","December"];
Calendar._SMN = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

Calendar._TT = {};
Calendar._TT["DEF_DATE_FORMAT"] = "%Y-%m-%d";
Calendar._TT["TT_DATE_FORMAT"] = "%a, %b %e";
Calendar._TT["WK"] = "wk";
Calendar._TT["TIME"] = "Time:";
Calendar._TT["CLOSE"] = "Close";
Calendar._TT["TODAY"] = "Today";

/* English is the flatpickr default — no locale file to load */
Calendar._flatpickrLocale = null;
