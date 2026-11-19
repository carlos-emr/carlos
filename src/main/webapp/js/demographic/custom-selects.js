/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
(function () {
    const form = document.getElementById('updatedelete');
    if (!form) return;

    const cleared = new Set();
    form.addEventListener('change', (event) => {
        const select = event.target;
        if (!select.matches('select[multiple]:enabled')
                || select.form !== form || !select.name
                || select.closest('[data-custom-select-name]')?.dataset.customSelectName !== select.name) {
            return;
        }
        if (select.selectedOptions.length === 0) {
            cleared.add(select);
        } else {
            cleared.delete(select);
        }
    });

    form.addEventListener('formdata', ({ formData }) => {
        // An emptied multiple select submits no value. Express that edit as the same empty value
        // as a blank option. An untouched disabled choice keeps its normal omitted submission.
        for (const select of cleared) {
            if (select.form === form && select.matches(':enabled') && select.selectedOptions.length === 0
                    && !formData.has(select.name)) {
                formData.append(select.name, '');
            }
        }
    });
}());
