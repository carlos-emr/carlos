/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
(function (root) {
    'use strict';
    const fields = [
        ['keyword', 'name', 255, false], ['reason', 'reason', 80, true],
        ['notes', 'notes', 32768, true], ['resources', 'resources', 255, false],
    ];
    const logicalLines = value => String(value).replace(/\r\n?/g, '\n');
    function validUnicode(value) {
        for (const point of value) {
            const code = point.codePointAt(0);
            if (code >= 0xD800 && code <= 0xDFFF) return false;
        }
        return true;
    }
    /** Validate only saving fields. Never shorten input or rewrite a saved legacy value. */
    function validate(form, showError) {
        const config = form.dataset || {};
        for (const [name, label, maximum, textarea] of fields) {
            const control = form.elements.namedItem(name);
            if (!control) continue;
            // Add resolves a linked patient's actual name on the server; the search display
            // may contain extra formatting and is not the persisted appointment name.
            if (name === 'keyword' && form.name === 'ADDAPPT'
                    && form.elements.namedItem('demographic_no')?.value
                    && !/^0+$/.test(form.elements.namedItem('demographic_no').value)) continue;
            const value = textarea ? logicalLines(control.value) : String(control.value);
            const original = control.dataset?.appointmentOriginal;
            const unchanged = form.name === 'EDITAPPT' && original !== undefined
                && value === (textarea ? logicalLines(original) : original);
            const valid = validUnicode(value);
            if (!valid || (!unchanged && Array.from(value).length > maximum)) {
                const template = valid
                    ? config.appointmentTooLong || '{0} exceeds the maximum length of {1} characters. Your entries have been kept.'
                    : config.appointmentInvalid || '{0} contains an invalid Unicode character. Your entries have been kept.';
                const labelName = config['appointment' + label[0].toUpperCase() + label.slice(1)] || label;
                showError(template.replace(/\{0\}/g, () => labelName).replace(/\{1\}/g, String(maximum)));
                control.focus();
                return false;
            }
        }
        return true;
    }
    const api = {validate};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    else root.CarlosAppointmentTextBounds = api;
}(typeof window === 'undefined' ? globalThis : window));
