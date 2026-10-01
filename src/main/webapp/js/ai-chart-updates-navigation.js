/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(() => {
    'use strict';
    const dialog = document.getElementById('chart-update-error-dialog');
    if (!dialog || typeof dialog.showModal !== 'function') return;
    let busy = false;
    let trigger;
    const close = () => dialog.close();
    dialog.querySelector('[data-close-chart-update-dialog]').addEventListener('click', close);
    dialog.addEventListener('close', () => { if (trigger?.isConnected) trigger.focus(); });
    const showError = (link, message, originalAvailable = false) => {
        trigger = link;
        dialog.querySelector('.chart-update-error-document').textContent = link.dataset.documentTitle || '';
        dialog.querySelector('#chart-update-error-message').textContent = message;
        const original = dialog.querySelector('.chart-update-original');
        original.hidden = true;
        original.removeAttribute('href');
        if (originalAvailable && link.dataset.originalUrl) {
            const url = new URL(link.dataset.originalUrl, location.href);
            if (url.origin === location.origin && url.protocol === location.protocol) {
                original.href = url.href;
                original.hidden = false;
            }
        }
        dialog.showModal();
    };
    // Delegation also covers rows redrawn by the document list's pagination/search.
    document.addEventListener('click', async event => {
        const link = event.target.closest('a.chart-update-document-link');
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        const url = new URL(link.href);
        if (url.origin !== location.origin) return;
        if (busy) return;
        event.preventDefault();
        busy = true;
        const label = link.textContent;
        link.setAttribute('aria-busy', 'true');
        link.textContent = dialog.dataset.checkingLabel;
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 30000);
        try {
            const response = await fetch(url.href, { credentials: 'same-origin', cache: 'no-store',
                headers: { Accept: 'application/json' }, signal: controller.signal });
            if (!response.ok || response.redirected || !response.headers.get('Content-Type')?.includes('application/json')) {
                throw new Error('Review check unavailable');
            }
            const result = await response.json();
            if (result.available === true) {
                if (!window.CarlosChartUpdateModal?.open(url.href, link)) location.assign(url.href);
            } else if (result.available === false && typeof result.message === 'string' && result.message.trim()) {
                showError(link, result.message, result.originalAvailable === true);
            } else {
                showError(link, dialog.dataset.fallbackMessage);
            }
        } catch {
            showError(link, dialog.dataset.fallbackMessage);
        } finally {
            clearTimeout(timeout);
            link.textContent = label;
            link.removeAttribute('aria-busy');
            busy = false;
        }
    });
})();
