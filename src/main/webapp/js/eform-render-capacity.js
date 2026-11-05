/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(() => {
    'use strict';
    const form = document.getElementById('eform-render-capacity');
    if (!form) return;
    const status = document.getElementById('eform-render-capacity-status');
    const cancel = document.getElementById('eform-render-capacity-cancel');
    const proceed = document.getElementById('eform-render-capacity-continue');
    let submitted = false;
    let timer = null;
    function stopWaiting() {
        if (timer !== null) window.clearTimeout(timer);
        timer = null;
    }
    cancel.addEventListener('click', () => {
        stopWaiting();
        if (!submitted) status.textContent = form.dataset.cancelledMessage;
    });
    form.addEventListener('submit', event => {
        stopWaiting();
        if (submitted) { event.preventDefault(); return; }
        submitted = true;
        proceed.disabled = true;
        cancel.disabled = true;
        status.textContent = form.dataset.continuingMessage;
    });
    window.addEventListener('pagehide', stopWaiting);
    window.addEventListener('pageshow', event => {
        if (!event.persisted) return;
        stopWaiting();
        // History restoration must never replay a POST whose acceptance is unknown.
        status.textContent = submitted ? form.dataset.uncertainMessage : form.dataset.cancelledMessage;
    });
    function retryDelay() {
        // This spreads scheduling only; it never generates an approval or security token.
        // Keep waiting usable if an older/restricted browser cannot provide random bytes.
        let jitter = Date.now() % 3000;
        try {
            const sample = new Uint32Array(1);
            window.crypto.getRandomValues(sample);
            jitter = sample[0] % 3000;
        } catch (unavailable) {
            // Arrival-time phase still varies the delay without disabling automatic waiting.
        }
        return 2000 + jitter;
    }
    // The server emits this page only for a typed pre-admission refusal. Each later
    // busy page may wait again; network failures and other server responses cannot.
    timer = window.setTimeout(() => {
        timer = null;
        if (!submitted) form.requestSubmit();
    }, retryDelay());
})();
