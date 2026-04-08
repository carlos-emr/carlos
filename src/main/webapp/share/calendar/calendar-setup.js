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

/**
 * Format a Date object using jscalendar strftime tokens.
 *
 * Provides the legacy Date.print(fmt) behaviour that jscalendar patched onto
 * Date.prototype. Used by the onClose shim to give callbacks a compatible
 * cal.multiple[n].print() method (e.g. billingShortcutPg1.jsp).
 *
 * @param {Date}   date  the date to format
 * @param {string} fmt   strftime-style format string
 * @returns {string} formatted date string
 * @private
 */
Calendar._formatDate = function (date, fmt) {
    var y  = date.getFullYear();
    var mo = date.getMonth() + 1;
    var d  = date.getDate();
    var h  = date.getHours();
    var mi = date.getMinutes();
    var s  = date.getSeconds();
    return fmt
        .replace(/%Y/g, y)
        .replace(/%y/g, String(y).slice(-2))
        .replace(/%m/g, ("0" + mo).slice(-2))
        .replace(/%d/g, ("0" + d).slice(-2))
        .replace(/%e/g, d)
        .replace(/%H/g, ("0" + h).slice(-2))
        .replace(/%I/g, ("0" + ((h % 12) || 12)).slice(-2))
        .replace(/%M/g, ("0" + mi).slice(-2))
        .replace(/%S/g, ("0" + s).slice(-2))
        .replace(/%P/g, (h < 12 ? "AM" : "PM"))
        .replace(/%p/g, (h < 12 ? "am" : "pm"));
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

    if (!Calendar._flatpickrReady) {
        Calendar._pendingSetups.push(params);
        return null;
    }

    /* If a locale file is still pending (lang shim set _pendingLocaleUrl after
     * _ensureFlatpickr already ran because flatpickr was cached), load the
     * locale now and queue this call + any future calls until it arrives. */
    if (Calendar._pendingLocaleUrl) {
        Calendar._pendingSetups.push(params);
        var url = Calendar._pendingLocaleUrl;
        Calendar._pendingLocaleUrl = null;
        var js = document.createElement("script");
        js.src = url;
        js.onload = function () { Calendar._flushPending(); };
        js.onerror = function () {
            if (typeof console !== "undefined") {
                console.warn("Calendar shim: failed to load flatpickr locale");
            }
            Calendar._flushPending();
        };
        document.head.appendChild(js);
        return null;
    }

    return Calendar._doSetup(params);
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

    /* Multiple date selection (used by billingShortcutPg1.jsp) */
    if (params.multiple) {
        fpOpts.mode = "multiple";
        var initDates = [];
        if (Array.isArray(params.multiple)) {
            for (var mi = 0; mi < params.multiple.length; mi++) {
                if (params.multiple[mi]) initDates.push(params.multiple[mi]);
            }
        }
        if (initDates.length) {
            fpOpts.defaultDate = initDates;
        }
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

    /* onClose → flatpickr onClose with legacy cal shim.
     * jscalendar passed the calendar instance to onClose(cal), where cal had
     * .multiple (hash of Date objects with .print(fmt)), .hide(), .date, and
     * .params. billingShortcutPg1.jsp relies on cal.multiple and cal.hide(). */
    if (typeof params.onClose === "function") {
        fpOpts.onClose = function (selectedDates, dateStr, instance) {
            var calShim = {
                hide: function () { instance.close(); },
                multiple: {},
                date: selectedDates[0] || new Date(),
                params: params
            };
            for (var ci = 0; ci < selectedDates.length; ci++) {
                var wrapped = new Date(selectedDates[ci].getTime());
                wrapped.print = function (fmt) {
                    return Calendar._formatDate(this, fmt);
                };
                calShim.multiple[ci] = wrapped;
            }
            params.onClose(calShim);
        };
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
