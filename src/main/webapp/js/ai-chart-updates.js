/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(() => {
    'use strict';
    document.querySelector('[data-review-back]')?.addEventListener('click', () => window.history.back());
    const fields = ['entryText', 'dueDate', 'assignee', 'destination'];
    const proposals = Array.from(document.querySelectorAll('.proposal-form'));
    let submitting = false;
    let edited = false;
    const clearTransferredDrafts = form => form.querySelectorAll('[data-transferred-draft]').forEach(input => input.remove());
    window.addEventListener('pageshow', () => {
        // Back/forward navigation may restore the previous document and its JavaScript state.
        submitting = false;
        document.querySelectorAll('form[aria-busy]').forEach(form => form.removeAttribute('aria-busy'));
        document.querySelectorAll('[data-idle-label]').forEach(button => { button.textContent = button.dataset.idleLabel; });
    });
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
                clearTransferredDrafts(form);
                proposals.filter(other => other !== form).forEach(other => {
                    const key = other.elements.namedItem('proposalKey').value;
                    fields.forEach(name => {
                        const field = other.elements.namedItem(name);
                        if (!field) return;
                        const input = document.createElement('input');
                        input.type = 'hidden';
                        input.dataset.transferredDraft = 'true';
                        input.name = `draft.${key}.${name}`;
                        input.value = field.value;
                        form.appendChild(input);
                    });
                });
            }
            submitting = true;
            form.setAttribute('aria-busy', 'true');
            if (form.dataset.busyLabel && event.submitter) {
                event.submitter.dataset.idleLabel = event.submitter.textContent;
                event.submitter.textContent = form.dataset.busyLabel;
            }
        });
    });
})();
