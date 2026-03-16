/**
 * DataTables date sorting plugin for MM-DD-YYYY and M-D-YYYY formatted dates.
 * Vanilla JS replacement for datetime-moment.js (which required Moment.js).
 *
 * Registers a custom DataTables type detection and sort order so that columns
 * containing dates in M(M)-D(D)-YYYY format are sorted chronologically
 * instead of lexicographically.
 *
 * Usage (same API as the old datetime-moment.js):
 *   $.fn.dataTable.moment('MM-DD-YYYY');
 *
 * All format strings map to the same M(M)-D(D)-YYYY parser since that is the
 * only format used in the CARLOS EMR dashboard drilldown.
 */
(function ($) {
    'use strict';

    var dateRegex = /^(\d{1,2})[\/-](\d{1,2})[\/-](\d{4})$/;

    /**
     * Parses a date string in M(M)-D(D)-YYYY or M(M)/D(D)/YYYY format
     * and returns its epoch milliseconds for numeric sorting.
     * Returns -Infinity for empty/null values so they sort to the bottom.
     */
    function parseDate(d) {
        if (!d || d === '' || d === '-') {
            return -Infinity;
        }

        if (d.replace) {
            d = d.replace(/(<.*?>)|(\r?\n|\r)/g, '');
        }
        d = $.trim(d);

        if (d === '' || d === '0') {
            return -Infinity;
        }

        var match = dateRegex.exec(d);
        if (match) {
            var month = parseInt(match[1], 10);
            var day = parseInt(match[2], 10);
            var year = parseInt(match[3], 10);
            return new Date(year, month - 1, day).getTime();
        }

        return -Infinity;
    }

    /**
     * Tests whether a string looks like a M(M)-D(D)-YYYY date.
     */
    function isDate(d) {
        if (!d) {
            return true; // null/empty is accepted by the type
        }
        if (d.replace) {
            d = d.replace(/(<.*?>)|(\r?\n|\r)/g, '');
        }
        d = $.trim(d);
        return d === '' || d === '0' || d === '-' || dateRegex.test(d);
    }

    // Backwards-compatible API: $.fn.dataTable.moment(format) registers the
    // type detection and sort order. The format parameter is accepted but
    // ignored since all dashboard dates use the same M-D-YYYY pattern.
    $.fn.dataTable.moment = function (/* format */) {
        var types = $.fn.dataTable.ext.type;
        var typeName = 'date-mdy';

        // Only register once even if called multiple times
        if ($.fn.dataTable._momentRegistered) {
            return;
        }
        $.fn.dataTable._momentRegistered = true;

        // Type detection
        types.detect.unshift(function (d) {
            return isDate(d) ? typeName : null;
        });

        // Sort pre-processing: convert to epoch milliseconds
        types.order[typeName + '-pre'] = function (d) {
            return parseDate(d);
        };
    };

}(jQuery));
