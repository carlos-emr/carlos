/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(() => {
    'use strict';
    const fields = ['entryText', 'dueDate', 'assignee', 'destination'];
    const proposals = Array.from(document.querySelectorAll('.proposal-form'));
    let submitting = false;
    let edited = false;
    proposals.forEach(form => form.addEventListener('input', () => { edited = true; }));
    window.addEventListener('beforeunload', event => {
        if (edited && !submitting) {
            event.preventDefault();
            event.returnValue = '';
        }
    });
    document.querySelectorAll('.proposal-form, .generation-form').forEach(form => {
        form.addEventListener('submit', event => {
            if (submitting) {
                event.preventDefault();
                return;
            }
            // Carry other cards' edits in the same authenticated POST, never browser storage.
            // The server limits these fields to the session's proposals and never retains approval.
            if (form.classList.contains('proposal-form')) {
                proposals.filter(other => other !== form).forEach(other => {
                    const key = other.elements.namedItem('proposalKey').value;
                    fields.forEach(name => {
                        const field = other.elements.namedItem(name);
                        if (!field) return;
                        const input = document.createElement('input');
                        input.type = 'hidden';
                        input.name = `draft.${key}.${name}`;
                        input.value = field.value;
                        form.appendChild(input);
                    });
                });
            }
            submitting = true;
            form.setAttribute('aria-busy', 'true');
            if (form.dataset.busyLabel && event.submitter) event.submitter.textContent = form.dataset.busyLabel;
        });
    });
})();
