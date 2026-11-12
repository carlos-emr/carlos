/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Input checks for the prescribing card (SearchDrug3.jsp / prescribe.jsp).
 *
 * The server repeats the date and dose checks (RxWriteScript2Action); these give the prescriber
 * the answer on the field they just left, before anything is sent.
 *
 *  - pickupDate / pickupTime: the pick-up fields are optional, so a blank value is valid and
 *    means "no pick-up" (#5002). A non-blank value must be a real yyyy-mm-dd day / HH:MM time.
 *  - fullDateIsReal: a full yyyy-mm-dd written or start date must exist on the calendar; the
 *    old day-range check let 2026-02-31 through and the server rolled it to 2026-03-03 (#5003).
 *  - isDoseAmount: inline Min/Max must be a plain non-negative decimal; anything else used to
 *    reach Float.parseFloat on the server and come back as HTTP 500 (#5003).
 */
var RxInputValidation = (function () {
    'use strict';

    var FULL_DATE = /^(\d{4})[-\/](\d{1,2})[-\/](\d{1,2})$/;
    var TIME = /^(\d{1,2}):(\d{2})$/;
    var DOSE = /^(\d{1,9}(\.\d{0,9})?|\.\d{1,9})$/;

    function trimmed(value) {
        return value == null ? '' : String(value).trim();
    }

    /** Whether year-month-day names a day that exists (months are 1-based). */
    function isCalendarDate(year, month, day) {
        if (!(month >= 1 && month <= 12 && day >= 1 && day <= 31)) return false;
        var date = new Date(0);
        // setFullYear, not the constructor: new Date(26, ...) means 1926.
        date.setFullYear(year, month - 1, day);
        return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
    }

    /**
     * A full yyyy-mm-dd (or yyyy/mm/dd) date must be a real day. Anything else, including blank
     * and the partial yyyy / yyyy-mm forms, is left to the caller's own checks.
     */
    function fullDateIsReal(value) {
        var match = FULL_DATE.exec(trimmed(value));
        if (!match) return true;
        return isCalendarDate(Number(match[1]), Number(match[2]), Number(match[3]));
    }

    /** @return {{ok: boolean, value: string}} the trimmed value, ok when blank or a real date */
    function pickupDate(value) {
        var text = trimmed(value);
        if (text === '') return {ok: true, value: ''};
        var match = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(text);
        return {ok: !!match && isCalendarDate(Number(match[1]), Number(match[2]), Number(match[3])), value: text};
    }

    /** @return {{ok: boolean, value: string}} the trimmed value, ok when blank or HH:MM, 00:00-23:59 */
    function pickupTime(value) {
        var text = trimmed(value);
        if (text === '') return {ok: true, value: ''};
        var match = TIME.exec(text);
        return {ok: !!match && Number(match[1]) <= 23 && Number(match[2]) <= 59, value: text};
    }

    /** Whether an inline Min/Max value is a dose the server accepts; blank means "unchanged". */
    function isDoseAmount(value) {
        var text = trimmed(value);
        return text === '' || DOSE.test(text);
    }

    /** Fills {0} in a translated message with the prescriber's own input. */
    function format(message, input) {
        return String(message).split('{0}').join(trimmed(input));
    }

    return {
        isCalendarDate: isCalendarDate,
        fullDateIsReal: fullDateIsReal,
        pickupDate: pickupDate,
        pickupTime: pickupTime,
        isDoseAmount: isDoseAmount,
        format: format
    };
}());
if (typeof module !== 'undefined' && module.exports) module.exports = RxInputValidation;
