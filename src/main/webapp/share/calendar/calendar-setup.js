/**
 * jscalendar Calendar.setup() → Flatpickr compatibility shim.
 *
 * Translates legacy Calendar.setup({ inputField, ifFormat, button, ... })
 * calls into flatpickr() initialisations. This file is loaded AFTER
 * calendar.js (which provides the Calendar global and auto-loads flatpickr)
 * and the lang file (which may set Calendar._FD / Calendar._flatpickrLocale).
 *
 * Supported jscalendar params → flatpickr mapping:
 *   inputField  → target element (by ID)
 *   ifFormat    → dateFormat (converted from strftime to flatpickr tokens)
 *   showsTime   → enableTime + time_24hr (uses timeFormat param if present)
 *   button      → external trigger via toggle button click handler
 *   onUpdate    → onChange callback
 *   onClose     → onClose callback
 *   singleClick → (always true in flatpickr — dates are selected on click)
 *   firstDay    → locale.firstDayOfWeek (falls back to Calendar._FD)
 *   range       → minDate / maxDate
 *
 * @see https://github.com/carlos-emr/carlos/issues/1355
 * @since 2026-04-08
 */

/* ── Format conversion ──────────────────────────────────────────────────── */

/**
 * Convert a jscalendar / strftime format string to Flatpickr tokens.
 *
 * Only the tokens actually used across the 562 Calendar.setup() calls in the
 * codebase are mapped. Unmapped tokens pass through as literal text.
 *
 * @param {string} fmt  strftime-style format, e.g. "%Y-%m-%d" or "%d/%m/%Y"
 * @returns {string} Flatpickr format, e.g. "Y-m-d" or "d/m/Y"
 */
Calendar._convertFormat = function (fmt) {
    if (!fmt) return "Y-m-d";
    return fmt
        .replace(/%Y/g, "Y")     // 4-digit year
        .replace(/%y/g, "y")     // 2-digit year
        .replace(/%m/g, "m")     // month 01-12
        .replace(/%d/g, "d")     // day 01-31
        .replace(/%e/g, "j")     // day 1-31 (no leading zero)
        .replace(/%H/g, "H")     // hour 00-23
        .replace(/%I/g, "h")     // hour 01-12
        .replace(/%M/g, "i")     // minute 00-59
        .replace(/%S/g, "S")     // second 00-59
        .replace(/%P/g, "K")     // AM/PM
        .replace(/%p/g, "K")     // am/pm → K in flatpickr
        .replace(/%a/g, "D")     // abbreviated weekday name
        .replace(/%A/g, "l")     // full weekday name
        .replace(/%b/g, "M")     // abbreviated month name
        .replace(/%B/g, "F");    // full month name
};

/* ── Calendar.setup() — public entry point ──────────────────────────────── */

/**
 * Drop-in replacement for the jscalendar Calendar.setup() helper.
 *
 * If flatpickr is not yet loaded (async injection still pending), the call
 * is queued and replayed automatically once the library is available.
 *
 * @param {Object} params  jscalendar-style configuration object
 */
Calendar.setup = function (params) {
    if (!params) return;

    if (Calendar._flatpickrReady) {
        Calendar._doSetup(params);
    } else {
        Calendar._pendingSetups.push(params);
    }
};

/* ── Calendar._doSetup() — internal implementation ──────────────────────── */

/**
 * Actually initialise a flatpickr instance from jscalendar-style params.
 * Called directly when flatpickr is loaded, or deferred via the pending queue.
 *
 * @param {Object} params  jscalendar-style configuration object
 * @private
 */
Calendar._doSetup = function (params) {
    /* ── Resolve element IDs to DOM nodes ────────────────────────────── */
    var inputEl = (typeof params.inputField === "string")
        ? document.getElementById(params.inputField)
        : params.inputField;

    var buttonEl = (typeof params.button === "string")
        ? document.getElementById(params.button)
        : params.button;

    if (!inputEl && !buttonEl) {
        if (typeof console !== "undefined") {
            console.warn("Calendar.setup shim: no inputField or button found – skipping.");
        }
        return;
    }

    /* ── Build flatpickr options ─────────────────────────────────────── */
    var fpOpts = {
        allowInput: true,
        dateFormat: Calendar._convertFormat(params.ifFormat || null)
    };

    /* Time support */
    if (params.showsTime) {
        fpOpts.enableTime = true;
        fpOpts.time_24hr = (params.timeFormat !== "12");
        if (fpOpts.dateFormat.indexOf("H") === -1 &&
            fpOpts.dateFormat.indexOf("h") === -1) {
            fpOpts.dateFormat += " H:i";
        }
    }

    /* Year range */
    if (params.range && params.range.length === 2) {
        fpOpts.minDate = String(params.range[0]) + "-01-01";
        fpOpts.maxDate = String(params.range[1]) + "-12-31";
    }

    /* First day of week (from lang file or explicit param) */
    var firstDay = (typeof params.firstDay === "number")
        ? params.firstDay
        : Calendar._FD;
    var localeObj = {};
    if (Calendar._flatpickrLocale && typeof flatpickr !== "undefined" &&
        flatpickr.l10ns && flatpickr.l10ns[Calendar._flatpickrLocale]) {
        localeObj = flatpickr.l10ns[Calendar._flatpickrLocale];
    }
    if (firstDay != null) {
        fpOpts.locale = Object.assign({}, localeObj, { firstDayOfWeek: firstDay });
    } else if (Calendar._flatpickrLocale) {
        fpOpts.locale = Calendar._flatpickrLocale;
    }

    /* onUpdate → flatpickr onChange */
    if (typeof params.onUpdate === "function") {
        fpOpts.onChange = function () { params.onUpdate(); };
    }

    /* onClose → flatpickr onClose */
    if (typeof params.onClose === "function") {
        fpOpts.onClose = function () { params.onClose(); };
    }

    /* ── Initialise flatpickr on the input element ───────────────────── */
    var target = inputEl || buttonEl;
    var fp = flatpickr(target, fpOpts);

    /* ── Button trigger ──────────────────────────────────────────────── */
    if (buttonEl && buttonEl !== inputEl) {
        buttonEl.addEventListener("click", function (e) {
            e.preventDefault();
            e.stopPropagation();
            fp.toggle();
        });
    }

    return fp;
};
