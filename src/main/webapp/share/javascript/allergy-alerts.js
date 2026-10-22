/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(function (root) {
    'use strict';
    function render(document, id, result) {
        var cell = document.getElementById('alleg_' + id);
        var table = document.getElementById('alleg_tbl_' + id);
        if (!cell || !table) return; // The prescription may have been removed during the request.
        cell.textContent = '';
        function line(text, color) {
            var row = document.createElement('div');
            row.style.color = color;
            row.textContent = text;
            cell.appendChild(row);
        }
        if (result && result.pending) {
            line('Checking allergies…', '#856404');
        } else if (result && result.disabled) {
            line('Allergy checking is disabled. Review the patient’s allergies before prescribing.', '#856404');
        } else if (!result || !Array.isArray(result.results) || !Array.isArray(result.unchecked)
                || result.results.concat(result.unchecked).some(function (item) {
                    return !item || typeof item.DESCRIPTION !== 'string' || typeof item.reaction !== 'string';
                })) {
            line('Allergy check unavailable. Review the patient’s allergies before prescribing.', '#856404');
        } else {
            result.results.forEach(function (allergy) {
                line('Allergy: ' + allergy.DESCRIPTION + ' Reaction: ' + allergy.reaction, 'red');
            });
            result.unchecked.forEach(function (allergy) {
                line('Not checked: ' + allergy.DESCRIPTION + ' Reaction: ' + allergy.reaction
                    + '. Review this allergy before prescribing.', '#856404');
            });
            if (result.checkFailed || (result.checkComplete !== true && result.unchecked.length === 0)) {
                line('Allergy check incomplete. Review the patient’s allergies before prescribing.', '#856404');
            }
        }
        table.style.display = cell.childNodes.length ? 'block' : 'none';
    }
    var api = {render: render};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    else root.CarlosAllergyAlert = api;
}(typeof window === 'undefined' ? globalThis : window));
