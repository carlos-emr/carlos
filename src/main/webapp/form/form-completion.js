/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
(function (root) {
    'use strict';

    function patientId(value) {
        if ((typeof value !== 'string' && typeof value !== 'number') || !/^\d{1,10}$/.test(String(value))) return null;
        var number = Number(value);
        return number > 0 && number <= 2147483647 ? String(number) : null;
    }

    function isOwnChart(opener, patient) {
        var identities = [];
        ['demographicNo', 'demographic_no'].forEach(function (name) {
            if (typeof opener[name] !== 'undefined') identities.push(opener[name]);
        });
        var address = new URL(opener.location.href);
        ['demographicNo', 'demographic_no'].forEach(function (name) {
            address.searchParams.getAll(name).forEach(function (value) { identities.push(value); });
        });
        opener.document.querySelectorAll('input[type="hidden"][name="demographicNo"], input[type="hidden"][name="demographic_no"]')
            .forEach(function (field) { identities.push(field.value); });
        // A popup may outlive navigation of its opener. Every available identity must agree;
        // neither a stale global nor a hidden field alone can override the current URL.
        return identities.length > 0 && identities.every(function (value) { return patientId(value) === patient; });
    }

    function refreshChart(opener, patient) {
        if (typeof opener.loadDiv !== 'function' || typeof opener.ctx !== 'string') return;
        ['forms', 'measurements'].forEach(function (panel) {
            try {
                if (!opener.document.getElementById(panel)) return;
                var route = panel === 'forms' ? 'displayForms' : 'displayMeasurements';
                var url = new URL(opener.ctx + '/encounter/' + route, opener.location.href);
                if (url.origin !== opener.location.origin) return;
                url.searchParams.set('demographicNo', patient);
                url.searchParams.set('cmd', panel);
                // Match the chart's own popup-close reloads; without it the panel renders every
                // row in its expanded state instead of the usual first six.
                url.searchParams.set('numToDisplay', '6');
                if (opener.Colour && opener.Colour[panel]) url.searchParams.set('hC', opener.Colour[panel]);
                opener.loadDiv(panel, url.href, 0);
            } catch (ignored) {
                // Refresh is optional on legacy charts; one unavailable panel must not prevent closing.
            }
        });
    }

    function completeEncounterForm(completionWindow, demographicNo, text) {
        try {
            var patient = patientId(demographicNo);
            var opener = completionWindow.opener;
            if (!patient || !opener || opener.closed || !isOwnChart(opener, patient)) return;
            var editor = opener.document.querySelector('textarea[name="caseNote_note"]')
                || opener.document.querySelector('form[name="encForm"] textarea[name="enTextarea"]');
            if (editor && !editor.readOnly && !editor.disabled && typeof text === 'string' && text.length > 0) {
                editor.value += '\n' + text;
                editor.dispatchEvent(new opener.Event('input', {bubbles: true}));
                editor.dispatchEvent(new opener.Event('change', {bubbles: true}));
                editor.scrollTop = editor.scrollHeight;
            }
            refreshChart(opener, patient);
        } catch (ignored) {
            // Closed, cross-origin or navigated openers cannot receive this patient's completion.
        } finally {
            try { completionWindow.close(); } catch (ignored) { /* Already closed. */ }
        }
    }

    if (typeof module === 'object' && module.exports) module.exports = {completeEncounterForm: completeEncounterForm};
    else root.completeEncounterForm = completeEncounterForm;
}(typeof window === 'undefined' ? globalThis : window));
