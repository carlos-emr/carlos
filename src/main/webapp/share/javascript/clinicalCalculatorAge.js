/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Age validation shared by the chart's clinical risk calculators.
 *
 * WHY THIS EXISTS (issue #3665, findings 8 and 9). Both calculators read the
 * age box as free text and fed it straight into a chain of `<=` comparisons.
 * JavaScript coerces "" to 0, so a BLANK age selected the youngest band and
 * printed a real-looking probability; every comparison against NaN is false,
 * so a NON-NUMERIC age fell through every rung to the OLDEST band -- the
 * highest-risk answer on the table, from a typo, with nothing on screen to say
 * so. A wrong number here does not look like a bug, and it is the number a
 * treatment decision is made on. The remedy is to refuse an age the table
 * cannot answer instead of computing one.
 *
 * The bounds are the calling page's: each table states the ages it covers, so
 * the page passes the first row it holds and a plausible upper limit. The
 * parser only decides whether the text is a whole number inside that range.
 *
 * Exposed as window.CarlosCalculatorAge for the JSPs and as a CommonJS module
 * so scripts/clinical-calculators.test.js can exercise the same code.
 */
(function (root) {
    'use strict';

    /**
     * Parse an age typed into a calculator.
     *
     * @param {*} raw       the input's value (anything; coerced to text and trimmed)
     * @param {number} min  lowest age the caller's table covers (inclusive)
     * @param {number} max  highest plausible age (inclusive)
     * @returns {number|null} the age as an integer, or null when it must be refused:
     *   blank, not a whole number (a sign, a decimal point, letters), or outside
     *   [min, max]. Null is the only refusal value, so a caller can test `=== null`.
     */
    function parseAge(raw, min, max) {
        var text = String(raw === undefined || raw === null ? '' : raw).trim();
        // Digits only: a decimal ("54.5"), a sign ("-5") or a stray letter
        // ("54a") is refused rather than truncated to something plausible.
        if (!/^[0-9]{1,3}$/.test(text)) {
            return null;
        }
        var age = parseInt(text, 10);
        if (age < min || age > max) {
            return null;
        }
        return age;
    }

    var api = { parseAge: parseAge };
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    root.CarlosCalculatorAge = api;
}(typeof window !== 'undefined' ? window : this));
