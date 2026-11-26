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
        if (opener.encounterConfig) identities.push(opener.encounterConfig.demographicNo);
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
        if (opener.location.pathname.endsWith('/encounter/ViewFormlist')) {
            opener.location.reload();
            return;
        }
        var context = typeof opener.ctx === 'string' ? opener.ctx : opener.encounterConfig && opener.encounterConfig.ctx;
        if (typeof opener.loadDiv !== 'function' || typeof context !== 'string') return;
        ['forms', 'measurements'].forEach(function (panel) {
            try {
                if (!opener.document.getElementById(panel)) return;
                var route = panel === 'forms' ? 'displayForms' : 'displayMeasurements';
                var url = new URL(context + '/encounter/' + route, opener.location.href);
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

    function refreshEncounterForms(formWindow, demographicNo) {
        try {
            var patient = patientId(demographicNo);
            var opener = formWindow.opener;
            if (patient && opener && !opener.closed && isOwnChart(opener, patient)) refreshChart(opener, patient);
        } catch (ignored) { /* Closed, cross-origin or navigated opener. */ }
    }

    function completeEncounterForm(completionWindow, demographicNo, text) {
        var keepOpen = typeof text === 'string' && text.length > 0;
        var refreshing = true;
        try {
            var patient = patientId(demographicNo);
            var opener = completionWindow.opener;
            if (!patient || !opener || opener.closed || !isOwnChart(opener, patient)) return;
            var modernEditor = opener.document.querySelector('textarea[name="caseNote_note"]');
            var editor = modernEditor || opener.document.querySelector('form[name="encForm"] textarea[name="enTextarea"]');
            if (keepOpen) {
                // The form has already been saved. Keep its reference available until the
                // intended editor actually receives it, including during an asynchronous save.
                var inserted = function (target) {
                    keepOpen = false;
                    target.dispatchEvent(new opener.Event('input', {bubbles: true}));
                    target.dispatchEvent(new opener.Event('change', {bubbles: true}));
                    target.scrollTop = target.scrollHeight;
                    if (!refreshing) completionWindow.close();
                };
                if (editor && !editor.readOnly) {
                    if (modernEditor && typeof opener.pasteToEncounterNote === 'function') {
                        opener.pasteToEncounterNote(text, editor, inserted);
                    } else if (!editor.disabled) {
                        editor.value += '\n' + text;
                        inserted(editor);
                    }
                }
            }
            refreshChart(opener, patient);
        } catch (ignored) {
            // The opener may have closed or navigated while the form was saving.
        } finally {
            refreshing = false;
            if (keepOpen) {
                completionWindow.document.getElementById('encounterFormReference').value = text;
                completionWindow.document.getElementById('encounterFormRecovery').hidden = false;
            } else {
                try { completionWindow.close(); } catch (ignored) { /* Already closed. */ }
            }
        }
    }

    if (typeof module === 'object' && module.exports) module.exports = {completeEncounterForm: completeEncounterForm, refreshEncounterForms: refreshEncounterForms};
    else {
        root.completeEncounterForm = completeEncounterForm;
        root.refreshEncounterForms = refreshEncounterForms;
    }
}(typeof window === 'undefined' ? globalThis : window));
