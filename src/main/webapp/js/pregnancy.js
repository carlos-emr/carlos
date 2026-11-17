/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
(function (root) {
    'use strict';
    function refreshPregnancy(win, patient) {
        try {
            const opener = win.opener;
            if (!/^[1-9]\d*$/.test(patient) || !opener || opener.closed) return;
            const url = new URL(opener.location.href);
            const identities = [];
            ['demographicNo', 'demographic_no'].forEach(function (name) {
                if (typeof opener[name] !== 'undefined') identities.push(String(opener[name]));
                url.searchParams.getAll(name).forEach(value => identities.push(value));
            });
            opener.document.querySelectorAll('input[type="hidden"][name="demographicNo"], input[type="hidden"][name="demographic_no"]')
                .forEach(field => identities.push(field.value));
            if (!identities.length || identities.some(value => !/^\d+$/.test(value) || String(Number(value)) !== patient)) return;
            if (typeof opener.reloadNav === 'function') opener.reloadNav('pregnancy');
        } catch (ignored) {
            // A closed or navigated opener must not receive another patient's refresh.
        }
    }
    if (typeof module === 'object' && module.exports) module.exports = {refreshPregnancy};
    else if (root.document.body.dataset.saved === 'true') refreshPregnancy(root, root.document.body.dataset.patient);
}(typeof window === 'undefined' ? globalThis : window));
