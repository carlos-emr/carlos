/**
 * jscalendar → Flatpickr compatibility shim.
 *
 * This file replaces the legacy jscalendar 1.0 library (calendar.js, ~1841 lines)
 * with a thin adapter that delegates all date-picking to Flatpickr 4.6.13.
 *
 * Existing Calendar.setup() calls continue to work unchanged so that the 99+
 * JSP files and arbitrary eForms that depend on the jscalendar API need no
 * modification. New code should use flatpickr() directly.
 *
 * @see https://github.com/carlos-emr/carlos/issues/1355
 * @since 2026-04-08
 */

/* ── Calendar global ────────────────────────────────────────────────────── */

/**
 * Backward-compatibility stub for the Calendar global.
 * The lang files (calendar-en.js, calendar-fr.js, …) set properties on this
 * object (_DN, _MN, _TT, _FD, etc.). We expose them so those scripts still
 * load without error; the shim reads _FD to pass firstDayOfWeek to Flatpickr.
 */
var Calendar = Calendar || {};

Calendar._DN  = Calendar._DN  || [];
Calendar._SDN = Calendar._SDN || [];
Calendar._MN  = Calendar._MN  || [];
Calendar._SMN = Calendar._SMN || [];
Calendar._TT  = Calendar._TT  || {};
Calendar._FD  = Calendar._FD  || 0;

/**
 * Store the flatpickr locale key set by the lang shim (e.g. "fr", "es").
 * Defaults to null (English / flatpickr default).
 */
Calendar._flatpickrLocale = null;

/**
 * Queue of Calendar.setup() calls that arrived before flatpickr was ready.
 * Flushed by _ensureFlatpickr once the library loads.
 * @private
 */
Calendar._pendingSetups = [];

/**
 * Whether flatpickr has been confirmed available.
 * @private
 */
Calendar._flatpickrReady = false;

/* ── Auto-load flatpickr if not already present ─────────────────────────── */

/**
 * Ensures flatpickr.min.js is loaded. If already loaded, marks ready immediately.
 * If not yet loaded, injects a <script> tag and flushes the pending queue on load.
 * @private
 */
Calendar._ensureFlatpickr = function () {
    if (typeof flatpickr !== "undefined") {
        Calendar._flatpickrReady = true;
        Calendar._flushPending();
        return;
    }

    var scripts = document.getElementsByTagName("script");
    var basePath = "";
    for (var i = 0; i < scripts.length; i++) {
        var src = scripts[i].src || "";
        var idx = src.indexOf("share/calendar/calendar.js");
        if (idx !== -1) {
            basePath = src.substring(0, idx);
            break;
        }
    }

    var js = document.createElement("script");
    js.src = basePath + "library/flatpickr/flatpickr.min.js";
    js.onload = function () {
        Calendar._flatpickrReady = true;
        Calendar._flushPending();
    };
    document.head.appendChild(js);
};

/**
 * Process all queued Calendar.setup() calls once flatpickr is available.
 * @private
 */
Calendar._flushPending = function () {
    while (Calendar._pendingSetups.length > 0) {
        var params = Calendar._pendingSetups.shift();
        Calendar._doSetup(params);
    }
};

/* Kick off flatpickr loading as soon as this script executes */
Calendar._ensureFlatpickr();
