/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(() => {
    'use strict';
    document.querySelector('[data-review-back]')?.addEventListener('click', () => window.history.back());
    const fields = ['entryText', 'dueDate', 'assignee', 'destination'];
    const proposals = Array.from(document.querySelectorAll('.proposal-form'));
    let submitting = false;
    let edited = false;
    let discarded = false;
    let workflowFrame;
    try {
        if (window.frameElement?.id === 'chart-update-workflow-frame') workflowFrame = window.frameElement;
    } catch { /* Standalone review still works when framed by another origin. */ }
    window.CarlosChartUpdateReview = {
        get busy() { return submitting; },
        get dirty() { return !discarded && (edited || !!document.getElementById('native-chart-review')?.open); },
        discard() { edited = false; discarded = true; },
    };
    const notifyState = () => workflowFrame?.dispatchEvent(new Event('chart-update-state'));
    if (workflowFrame) document.body.classList.add('review-in-modal');
    const cards = Array.from(document.querySelectorAll('article.proposal'));
    // A history entry takes its chart section's colour and name as the section is chosen.
    // Its summary row follows the edits, so the summary never shows an older draft.
    cards.forEach(card => {
        const key = CSS.escape(card.dataset.proposalKey);
        const row = document.querySelector(`[data-summary-for="${key}"]`);
        card.querySelector('[name="entryText"]')?.addEventListener('input', event => {
            const entry = row?.querySelector('[data-summary-entry]');
            if (entry) entry.textContent = event.target.value;
        });
        card.querySelector('[name="destination"]')?.addEventListener('change', event => {
            const select = event.target;
            const name = select.value ? select.selectedOptions[0].textContent : '';
            card.dataset.section = select.value;
            const step = document.querySelector(`[data-review-step="${key}"]`);
            if (step) step.dataset.section = select.value;
            const chip = card.querySelector('[data-section-chip]');
            if (chip) {
                chip.textContent = name;
                chip.hidden = !select.value;
            }
            if (row) {
                row.dataset.section = select.value;
                row.querySelector('[data-summary-section]').textContent = name || card.querySelector('h3').textContent;
            }
        });
    });
    const strip = document.querySelector('.review-strip');
    const summary = document.getElementById('review-summary');
    if (cards.length && strip && summary) {
        // One suggestion at a time. Without script, every suggestion stays listed with the summary after them.
        document.documentElement.classList.add('review-stepping');
        document.querySelectorAll('[data-step-only]').forEach(element => { element.hidden = false; });
        strip.hidden = false;
        const steps = Array.from(strip.querySelectorAll('[data-review-step]'));
        const count = strip.querySelector('[data-review-position]');
        const summaryButton = strip.querySelector('[data-review-summary]');
        const check = document.getElementById('chart-check');
        const checkBody = check?.querySelector('.chart-check-body');
        // The chart check sits beside the suggestion: each card's notices move there, and only the
        // current card's are shown. The evidence script keeps its own references to them.
        const notices = cards.map(card => {
            const group = card.querySelector('.chart-check-notices');
            if (group && checkBody) checkBody.append(group);
            return group;
        });
        const open = card => !!card.querySelector('.proposal-form');
        const nextOpen = from => {
            for (let next = from + 1; next < cards.length; next++) if (open(cards[next])) return next;
            return -1;
        };
        let index = -1;
        const updateCheck = () => {
            if (!check) return;
            notices.forEach((group, position) => { if (group) group.hidden = position !== index; });
            check.hidden = index < 0 || !open(cards[index]);
            const found = !check.hidden && Array.from(notices[index]?.children || []).some(notice => !notice.hidden);
            if (!check.hidden) check.querySelector('.chart-check-clear').hidden = found;
            cards.forEach((card, position) => {
                const pointer = card.querySelector('.chart-check-pointer');
                if (pointer) pointer.hidden = !(found && position === index);
            });
        };
        // The bar is fixed to the bottom of the window: leave its height free below the page.
        const clearBar = () => {
            const bar = index === -1 ? null : cards[index].querySelector('.review-action-bar');
            document.documentElement.style.setProperty('--review-bar-space', `${bar ? bar.offsetHeight : 0}px`);
        };
        window.addEventListener('resize', clearBar);
        // index -1 is the summary.
        const show = (target, focus) => {
            index = target;
            cards.forEach((card, position) => { card.hidden = position !== index; });
            summary.hidden = index !== -1;
            steps.forEach((step, position) => {
                if (position === index) step.setAttribute('aria-current', 'step');
                else step.removeAttribute('aria-current');
            });
            if (index === -1) summaryButton.setAttribute('aria-current', 'step');
            else summaryButton.removeAttribute('aria-current');
            count.textContent = index === -1 ? strip.dataset.summaryLabel
                : cards[index].querySelector('.proposal-position').textContent.trim();
            if (index === -1) window.CarlosChartUpdateEvidence?.clear();
            else window.CarlosChartUpdateEvidence?.show(cards[index]);
            updateCheck();
            clearBar();
            // The eChart modal focuses this heading when the page loads.
            const heading = index === -1 ? summary.querySelector('h2') : cards[index].querySelector('h3');
            document.querySelector('[data-review-focus]')?.removeAttribute('data-review-focus');
            heading.setAttribute('data-review-focus', '');
            heading.setAttribute('tabindex', '-1');
            if (focus) heading.focus();
        };
        steps.forEach((step, position) => step.addEventListener('click', () => show(position, true)));
        summaryButton.addEventListener('click', () => show(-1, true));
        cards.forEach((card, position) => {
            const previous = card.querySelector('[data-review-previous]');
            if (previous) {
                previous.disabled = position === 0;
                previous.addEventListener('click', () => show(Math.max(0, position - 1), true));
            }
            card.querySelector('[data-review-skip]')?.addEventListener('click', () => show(nextOpen(position), true));
            card.querySelector('[data-review-next]')?.addEventListener('click',
                () => show(position + 1 < cards.length ? position + 1 : -1, true));
            // After the evidence script's own listeners, which show or hide the notices.
            card.addEventListener('input', updateCheck);
            card.addEventListener('change', updateCheck);
        });
        summary.querySelector('[data-review-skipped]')?.addEventListener('click', () => show(nextOpen(-1), true));
        document.querySelectorAll('[data-review-proposal]').forEach(link => {
            link.addEventListener('click', event => {
                // A modified click opens the GET review in a new tab, as the link says.
                if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
                const target = cards.findIndex(card => card.dataset.proposalKey === link.dataset.reviewProposal);
                if (target < 0) return;
                event.preventDefault();
                show(target, true);
            });
        });
        // A refused save or dismissal keeps its suggestion on screen. One that went through redirects to
        // #after-<key>: the next open suggestion after that one, or the summary when none is left after it.
        const at = key => cards.findIndex(card => card.dataset.proposalKey === key);
        let hash = '';
        try { hash = decodeURIComponent(location.hash.slice(1)); } catch { /* A malformed fragment starts at the first open card. */ }
        const refused = document.querySelector('.alert-danger') ? at(document.getElementById('proposals').dataset.currentProposal) : -1;
        let start = nextOpen(-1);
        if (refused >= 0) {
            start = refused;
        } else if (hash.startsWith('after-') && at(hash.slice(6)) >= 0) {
            start = nextOpen(at(hash.slice(6)));
        } else if (hash.startsWith('proposal-') && at(hash.slice(9)) >= 0) {
            start = at(hash.slice(9));
        }
        show(start, false);
    }
    const clearTransferredDrafts = form => form.querySelectorAll('[data-transferred-draft]').forEach(input => input.remove());
    window.addEventListener('pageshow', () => {
        // Back/forward navigation may restore the previous document and its JavaScript state.
        submitting = false;
        discarded = false;
        notifyState();
        document.querySelectorAll('form[aria-busy]').forEach(form => form.removeAttribute('aria-busy'));
        document.querySelectorAll('[data-idle-label]').forEach(button => { button.textContent = button.dataset.idleLabel; });
    });
    proposals.forEach(form => form.addEventListener('input', () => { edited = true; }));
    window.addEventListener('beforeunload', event => {
        if (window.CarlosChartUpdateReview.dirty && !submitting) {
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
            submitting = true;
            notifyState();
            form.setAttribute('aria-busy', 'true');
            if (form.dataset.busyLabel && event.submitter) {
                event.submitter.dataset.idleLabel = event.submitter.textContent;
                event.submitter.textContent = form.dataset.busyLabel;
            }
        });
    });
    const nativeDialog = document.getElementById('native-chart-review');
    if (nativeDialog && typeof nativeDialog.showModal === 'function') {
        const nativeFrame = nativeDialog.querySelector('iframe');
        let nativeTrigger;
        const closeNative = () => {
            if (!window.confirm(nativeDialog.dataset.closeWarning)) return;
            nativeDialog.close();
        };
        nativeDialog.querySelector('[data-native-close]').addEventListener('click', closeNative);
        nativeDialog.addEventListener('cancel', event => { event.preventDefault(); closeNative(); });
        nativeDialog.addEventListener('close', () => {
            nativeFrame.removeAttribute('src');
            nativeTrigger?.focus();
            notifyState();
        });
        document.querySelectorAll('.native-review-open').forEach(button => {
            button.addEventListener('click', () => {
                const url = new URL(button.dataset.nativeUrl, location.href);
                if (url.origin !== location.origin || submitting) return;
                nativeTrigger = button;
                nativeDialog.querySelector('.native-review-source').textContent =
                    button.closest('.proposal').querySelector('.proposal-evidence blockquote').textContent;
                nativeFrame.src = url.href;
                nativeDialog.showModal();
                notifyState();
            });
        });
    }
})();
