/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(() => {
    'use strict';
    // Pages loaded inside this workflow navigate within its frame, without nesting dialogs.
    try { if (window.frameElement?.id === 'chart-update-workflow-frame') return; }
    catch { return; }
    const dialog = document.getElementById('chart-update-workflow-dialog');
    if (!dialog || typeof dialog.showModal !== 'function') return;
    const frame = dialog.querySelector('iframe');
    const close = dialog.querySelector('[data-close-chart-update-workflow]');
    const status = dialog.querySelector('[role="status"]');
    let trigger;
    let timer;
    let previous;
    const state = () => {
        try { return frame.contentWindow.CarlosChartUpdateReview; }
        catch { return undefined; }
    };
    const requestClose = () => {
        const review = state();
        if (review?.busy) {
            status.textContent = dialog.dataset.wait;
            status.hidden = false;
            return;
        }
        if (review?.dirty && !window.confirm(dialog.dataset.unsaved)) return;
        review?.discard();
        dialog.close();
    };
    close.addEventListener('click', requestClose);
    dialog.addEventListener('cancel', event => { event.preventDefault(); requestClose(); });
    dialog.addEventListener('close', () => {
        clearTimeout(timer);
        frame.removeAttribute('src');
        frame.hidden = true;
        trigger?.focus();
    });
    frame.addEventListener('load', () => {
        const doc = frame.contentDocument;
        if (!dialog.open || !frame.hasAttribute('src')) return;
        // A late load after a fast close and reopen belongs to the document shown before this open,
        // or to the blank page left by close. Every new navigation gets a new document.
        if (doc && (doc === previous || doc.URL === 'about:blank')) return;
        previous = undefined;
        clearTimeout(timer);
        status.hidden = true;
        frame.hidden = false;
        close.disabled = false;
        try {
            doc.addEventListener('keydown', event => {
                if (event.key === 'Escape' && !doc.querySelector('dialog[open]')) {
                    event.preventDefault();
                    requestClose();
                }
            });
            const heading = Array.from(doc.querySelectorAll('h1, h2')).find(element => element.getClientRects().length);
            if (heading) { heading.setAttribute('tabindex', '-1'); heading.focus(); }
        } catch {
            status.textContent = dialog.dataset.error;
            status.hidden = false;
            frame.hidden = true;
        }
    });
    frame.addEventListener('chart-update-state', () => { close.disabled = !!state()?.busy; });
    window.CarlosChartUpdateModal = {
        open(href, link) {
            const url = new URL(href, location.href);
            const paths = ['ViewDocumentReport', 'AiChartUpdates'].map(path => `${dialog.dataset.context}/documentManager/${path}`);
            if (url.origin !== location.origin || !paths.includes(url.pathname)) return false;
            if (dialog.open) return true;
            trigger = link;
            status.textContent = dialog.dataset.loading;
            status.hidden = false;
            frame.hidden = true;
            close.disabled = false;
            previous = frame.contentDocument;
            frame.src = url.href;
            dialog.showModal();
            timer = setTimeout(() => {
                status.textContent = dialog.dataset.error;
                status.hidden = false;
            }, 30000);
            return true;
        },
    };
    document.addEventListener('click', event => {
        const link = event.target.closest('a.chart-update-workflow-link');
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        if (window.CarlosChartUpdateModal.open(link.href, link)) event.preventDefault();
    });
})();
