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

/**
 * URL for a flatpickr locale file that should be loaded once flatpickr is ready.
 * Set by the lang shims (calendar-fr.js, etc.) when flatpickr hasn't loaded yet.
 * @private
 */
Calendar._pendingLocaleUrl = null;

/* ── Auto-load flatpickr if not already present ─────────────────────────── */

/**
 * Ensures flatpickr.min.js is loaded. If already loaded, marks ready immediately.
 * If not yet loaded, injects a <script> tag and flushes the pending queue on load.
 * @private
 */
Calendar._ensureFlatpickr = function () {
    if (typeof flatpickr !== "undefined") {
        Calendar._flatpickrReady = true;
        /* If a locale URL is pending (lang shim ran before us but flatpickr was
         * already cached), load the locale before flushing so setups get the
         * correct l10n. */
        if (Calendar._pendingLocaleUrl) {
            var locJs = document.createElement("script");
            locJs.src = Calendar._pendingLocaleUrl;
            Calendar._pendingLocaleUrl = null;
            locJs.onload = function () { Calendar._flushPending(); };
            locJs.onerror = function () {
                if (typeof console !== "undefined") {
                    console.warn("Calendar shim: failed to load flatpickr locale");
                }
                Calendar._flushPending();
            };
            document.head.appendChild(locJs);
        } else {
            Calendar._flushPending();
        }
        return;
    }

    /* Resolve the webapp base path from this script's own URL */
    var basePath = "";
    var currentSrc = (document.currentScript && document.currentScript.src) || "";
    var idx = currentSrc.indexOf("share/calendar/calendar.js");
    if (idx !== -1) {
        basePath = currentSrc.substring(0, idx);
    } else {
        /* Fallback: scan all script tags */
        var scripts = document.getElementsByTagName("script");
        for (var i = 0; i < scripts.length; i++) {
            var src = scripts[i].src || "";
            idx = src.indexOf("share/calendar/calendar.js");
            if (idx !== -1) {
                basePath = src.substring(0, idx);
                break;
            }
        }
    }

    var js = document.createElement("script");
    js.src = basePath + "library/flatpickr/flatpickr.min.js";
    js.onload = function () {
        Calendar._flatpickrReady = true;
        /* Load any pending locale before flushing Calendar.setup() calls */
        if (Calendar._pendingLocaleUrl) {
            var locJs = document.createElement("script");
            locJs.src = Calendar._pendingLocaleUrl;
            Calendar._pendingLocaleUrl = null;
            locJs.onload = function () { Calendar._flushPending(); };
            locJs.onerror = function () {
                if (typeof console !== "undefined") {
                    console.warn("Calendar shim: failed to load flatpickr locale");
                }
                Calendar._flushPending();
            };
            document.head.appendChild(locJs);
        } else {
            Calendar._flushPending();
        }
    };
    js.onerror = function () {
        if (typeof console !== "undefined") {
            console.error("Calendar shim: failed to load flatpickr.min.js from " + js.src);
        }
        /* Fall back to native <input type="date"> so date fields remain usable */
        Calendar._flatpickrReady = false;
        while (Calendar._pendingSetups.length > 0) {
            var p = Calendar._pendingSetups.shift();
            var el = (typeof p.inputField === "string")
                ? document.getElementById(p.inputField) : p.inputField;
            if (el && el.type === "text") {
                el.type = "date";
            }
        }
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
