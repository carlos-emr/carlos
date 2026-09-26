/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Date-of-birth keyword formatting and validation for the patient search form
 * (WEB-INF/jsp/demographic/zdemographicfulltitlesearch.jsp).
 *
 * Grammar (issue #3956): YYYY, YYYY-MM or YYYY-MM-DD, where any whole segment
 * may be the % wildcard (1975-%-05, %-03-05). This is user feedback only; the
 * authoritative parser is io.github.carlos_emr.carlos.demographic.data.DobSearchPattern,
 * and the two must accept the same shapes. scripts/dob-search-keyword.test.js
 * pins that parity.
 *
 * Exposed as window.CarlosDobSearch in the browser and via module.exports for
 * node --test.
 */
(function (root, factory) {
    var api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.CarlosDobSearch = api;
    }
}(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var WILDCARD = '%';
    var SEGMENT_MAX = [4, 2, 2];

    /**
     * Normalizes what the user has typed so far into the DOB grammar.
     *
     * Digits auto-advance into the next segment once a segment is full, a typed
     * separator (- / . or space) closes a non-empty segment early (so 1975-3-5
     * survives), and % fills an empty segment. Malformed input is preserved for
     * validation instead of being silently changed into a different search. A
     * separator typed after a segment is kept, so the field never looks stuck.
     *
     * @param {string} raw the current field value
     * @returns {string} the formatted value
     */
    function format(raw) {
        raw = String(raw == null ? '' : raw);
        var cleaned = raw.trim().replace(/[/. ]/g, '-');
        if (/[^0-9%-]/.test(cleaned)) return raw;
        var segments = [];
        var current = '';

        for (var i = 0; i < cleaned.length; i++) {
            var ch = cleaned.charAt(i);
            if (ch === '-') {
                if (current === '') return raw;
                if (segments.length === 2) return i === cleaned.length - 1 ? cleaned : raw;
                if (segments.length < 2) {
                    segments.push(current);
                    current = '';
                }
                continue;
            }
            if (ch === WILDCARD) {
                // Only a whole segment may be a wildcard; "19%" is not supported.
                if (current !== '') return raw;
                current = WILDCARD;
                continue;
            }
            var full = current === WILDCARD || current.length >= SEGMENT_MAX[segments.length];
            if (full) {
                if (segments.length < 2) {
                    segments.push(current);
                    current = ch;
                } else {
                    return raw;
                }
                continue;
            }
            current += ch;
        }

        if (current === '') {
            return segments.length ? segments.join('-') + '-' : '';
        }
        return segments.concat([current]).join('-');
    }

    function validMonthOrDay(value, max) {
        if (value === WILDCARD) {
            return true;
        }
        if (!/^\d{1,2}$/.test(value)) {
            return false;
        }
        var number = parseInt(value, 10);
        return number >= 1 && number <= max;
    }

    /**
     * Returns true when the keyword is a searchable DOB under the grammar above.
     * Mirrors DobSearchPattern.parse(): a single trailing hyphen is tolerated and
     * a keyword made only of wildcards is refused.
     *
     * @param {string} value the keyword to check
     * @returns {boolean}
     */
    function isValid(value) {
        var keyword = String(value == null ? '' : value).trim().replace(/-$/, '');
        if (keyword === '') {
            return false;
        }
        var parts = keyword.split('-');
        if (parts.length > 3) {
            return false;
        }
        var year = parts[0];
        var month = parts.length > 1 ? parts[1] : WILDCARD;
        var day = parts.length > 2 ? parts[2] : WILDCARD;
        if (year !== WILDCARD && !/^\d{4}$/.test(year)) {
            return false;
        }
        if (!validMonthOrDay(month, 12) || !validMonthOrDay(day, 31)) {
            return false;
        }
        return !(year === WILDCARD && month === WILDCARD && day === WILDCARD);
    }

    /**
     * Reformats an input element in place, keeping the caret next to the same
     * digit or wildcard it was beside so edits in the middle of the field do not
     * jump to the end.
     *
     * @param {HTMLInputElement} input the keyword field
     */
    function formatInput(input) {
        var raw = input.value;
        // Preserve an Ontario card swipe from its second keystroke onward so
        // the search form can extract the HIN and select HIN mode on submit.
        if (raw.indexOf('%b') === 0) {
            return;
        }
        var formatted = format(raw);
        if (formatted === raw) {
            return;
        }
        var caret = typeof input.selectionStart === 'number' ? input.selectionStart : raw.length;
        var atEnd = caret >= raw.length;
        var significantBefore = raw.substring(0, caret).replace(/[^0-9%]/g, '').length;

        input.value = formatted;

        if (atEnd || typeof input.setSelectionRange !== 'function') {
            return;
        }
        var position = 0;
        var seen = 0;
        while (position < formatted.length && seen < significantBefore) {
            if (/[0-9%]/.test(formatted.charAt(position))) {
                seen++;
            }
            position++;
        }
        input.setSelectionRange(position, position);
    }

    return {
        WILDCARD: WILDCARD,
        format: format,
        isValid: isValid,
        formatInput: formatInput
    };
}));
