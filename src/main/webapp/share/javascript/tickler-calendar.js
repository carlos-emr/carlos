/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
var CarlosTicklerCalendar = {
    /** Clone a local date, clamp the combined month offset, then apply days without mutating the base. */
    offset: function (base, months, days) {
        var result = new Date(base.getTime());
        var day = result.getDate();
        result.setDate(1);
        result.setMonth(result.getMonth() + months);
        var end = new Date(result.getTime());
        end.setMonth(end.getMonth() + 1, 0);
        result.setDate(Math.min(day, end.getDate()));
        result.setDate(result.getDate() + days);
        return result;
    }
};
if (typeof module !== 'undefined') module.exports = CarlosTicklerCalendar;
