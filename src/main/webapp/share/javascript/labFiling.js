/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function () {
    'use strict';
    window.openLabFilingDialog = function (labNo, acknowledge) {
        const dialog = document.getElementById('labFiling_' + labNo);
        if (!dialog) return false;
        dialog.dataset.acknowledge = String(acknowledge);
        dialog.querySelector('[data-filing-comment]').hidden = !acknowledge;
        dialog.querySelector('details').open = true;
        dialog.querySelector('[data-filing-error]').textContent = '';
        dialog.showModal();
        return false;
    };
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('dialog[data-lab-filing]').forEach(function (dialog) {
            const form = dialog.querySelector('form');
            const error = dialog.querySelector('[data-filing-error]');
            dialog.querySelector('[data-filing-cancel]').addEventListener('click', function () { dialog.close(); });
            form.addEventListener('submit', async function (event) {
                event.preventDefault();
                const submit = dialog.querySelector('[type="submit"]');
                const selected = Array.from(dialog.querySelectorAll('input[name="providerNo"]:checked:not(:disabled)'));
                const acknowledge = dialog.dataset.acknowledge === 'true';
                if (!acknowledge && selected.length === 0) {
                    error.textContent = dialog.dataset.selectMessage;
                    return;
                }
                submit.disabled = true;
                error.textContent = '';
                async function fileSelected() {
                    if (selected.length === 0) { dialog.close(); return; }
                    const token = Array.from(document.querySelectorAll('input[name="CSRF-TOKEN"]'))
                        .map(function (input) { return input.value; }).find(Boolean);
                    if (!token) throw new Error(dialog.dataset.errorMessage);
                    const data = new URLSearchParams({labNo: dialog.dataset.labFiling, labType: 'HL7'});
                    selected.forEach(function (input) { data.append('providerNo', input.value); });
                    if (acknowledge) data.set('comment', form.elements.comment.value);
                    const response = await fetch(dialog.dataset.endpoint, {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded',
                            'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': token},
                        body: data.toString()
                    });
                    const json = await response.json();
                    if (!response.ok || json.success !== true) throw new Error(dialog.dataset.errorMessage);
                    // Do not permit a second submission with the now-stale provider list.
                    dialog.querySelectorAll('input[name="providerNo"]').forEach(function (input) {
                        if (selected.includes(input)) { input.checked = false; input.disabled = true; }
                    });
                    dialog.close();
                    if (!acknowledge) window.location.reload();
                }
                try {
                    if (acknowledge) {
                        const ackForm = document.forms['acknowledgeForm_' + dialog.dataset.labFiling];
                        ackForm.elements.comment.value = form.elements.comment.value;
                        await window.handleLab(ackForm.id, dialog.dataset.labFiling, 'ackLab', fileSelected);
                    } else {
                        await fileSelected();
                    }
                } catch (failure) {
                    error.textContent = dialog.dataset.errorMessage;
                } finally {
                    submit.disabled = false;
                }
            });
        });
    });
}());
