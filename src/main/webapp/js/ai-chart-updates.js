/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(() => {
    'use strict';
    document.querySelector('[data-review-back]')?.addEventListener('click', () => window.history.back());
    const fields = ['entryText', 'dueDate', 'assignee', 'destination'];
    const proposals = Array.from(document.querySelectorAll('.proposal-form'));
    let submitting = false;
    let edited = false;
    let workflowFrame;
    try {
        if (window.frameElement?.id === 'chart-update-workflow-frame') workflowFrame = window.frameElement;
    } catch { /* Standalone review still works when framed by another origin. */ }
    window.CarlosChartUpdateReview = {
        get busy() { return submitting; },
        get dirty() { return edited; },
        discard() { edited = false; },
    };
    const notifyState = () => workflowFrame?.dispatchEvent(new Event('chart-update-state'));
    if (workflowFrame) {
        document.body.classList.add('review-in-modal');
        const cards = Array.from(document.querySelectorAll('article.proposal'));
        const steps = document.querySelector('.review-steps');
        if (cards.length && steps) {
            const previous = steps.querySelector('[data-review-previous]');
            const next = steps.querySelector('[data-review-next]');
            let index = Math.max(0, cards.findIndex(card => card.querySelector('.proposal-form')));
            const failed = document.querySelector('.alert-danger') && workflowFrame.dataset.currentProposal;
            if (failed && cards.some(card => card.dataset.proposalKey === failed)) {
                index = cards.findIndex(card => card.dataset.proposalKey === failed);
            }
            const show = focus => {
                cards.forEach((card, position) => { card.hidden = position !== index; });
                window.CarlosChartUpdateEvidence?.show(cards[index]);
                previous.disabled = index === 0;
                next.disabled = index === cards.length - 1;
                steps.querySelector('[data-review-position]').textContent = `${index + 1} / ${cards.length}`;
                if (focus) {
                    const heading = cards[index].querySelector('h3');
                    heading.setAttribute('tabindex', '-1');
                    heading.focus();
                }
            };
            previous.addEventListener('click', () => { if (index > 0) { index--; show(true); } });
            next.addEventListener('click', () => { if (index < cards.length - 1) { index++; show(true); } });
            steps.hidden = false;
            show(false);
        }
    }
    const clearTransferredDrafts = form => form.querySelectorAll('[data-transferred-draft]').forEach(input => input.remove());
    window.addEventListener('pageshow', () => {
        // Back/forward navigation may restore the previous document and its JavaScript state.
        submitting = false;
        notifyState();
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
            if (proposals.length && form.classList.contains('generation-form') &&
                    !window.confirm(form.dataset.discardConfirm)) {
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
            if (workflowFrame) workflowFrame.dataset.currentProposal = form.elements.namedItem('proposalKey')?.value || '';
            submitting = true;
            notifyState();
            form.setAttribute('aria-busy', 'true');
            if (form.dataset.busyLabel && event.submitter) {
                event.submitter.dataset.idleLabel = event.submitter.textContent;
                event.submitter.textContent = form.dataset.busyLabel;
            }
        });
    });
})();
