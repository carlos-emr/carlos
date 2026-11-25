/* Preserve surrounding drafts while navigating a server-paged result table. */
(function () {
    'use strict';
    if (globalThis.carlosServerPagingReady) return;
    globalThis.carlosServerPagingReady = true;
    document.addEventListener('submit', async function (event) {
        const form = event.target;
        if (!form.matches('[data-server-page-form]')) return;
        event.preventDefault();
        const region = form.closest('[data-server-page-region]');
        if (!region || region.dataset.loading === 'true') return;
        const error = form.querySelector('[data-server-page-error]');
        const controller = new AbortController();
        const deadline = setTimeout(() => controller.abort(), 30_000);
        region.dataset.loading = 'true';
        error.textContent = '';
        try {
            // The legacy dispatcher posts an input named "method", which shadows form.method.
            const method = (form.getAttribute('method') || 'get').toUpperCase();
            const url = new URL(form.getAttribute('action'), location.href);
            const options = {method, credentials: 'same-origin', headers: {}, signal: controller.signal};
            if (method !== 'GET') {
                let token = form.querySelector('input[name="CSRF-TOKEN"]');
                if (!token) {
                    token = document.createElement('input');
                    token.type = 'hidden'; token.name = 'CSRF-TOKEN'; form.append(token);
                }
                // Fetched HTML has not run CSRFGuard's injection script. Bootstrap the replacement
                // form explicitly, including on the second and later page requests.
                await globalThis.fetchCsrfToken(form.dataset.context, controller.signal);
                if (!token.value) throw new Error('Missing CSRF token');
                options.headers = {'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': token.value};
            }
            const fields = new URLSearchParams(new FormData(form));
            if (event.submitter?.name) fields.set(event.submitter.name, event.submitter.value);
            if (method === 'GET') url.search = fields.toString();
            else options.body = fields;
            const response = await fetch(url, options);
            if (!response.ok || !response.headers.get('content-type')?.includes('text/html')) throw new Error('Page unavailable');
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            const replacement = page.querySelector('[data-server-page-region]');
            if (!replacement?.querySelector('[data-server-page-form]')) throw new Error('Missing results');
            const selectionKey = input => JSON.stringify([input.form.name, input.name, input.type, input.value]);
            const selectionControls = root => root.querySelectorAll(
                '[data-server-page-preserve-selection] input[type="radio"], [data-server-page-preserve-selection] input[type="checkbox"]');
            const selections = new Map(Array.from(selectionControls(region), input => [selectionKey(input), input.checked]));
            const active = document.activeElement;
            const restoreFocus = region.contains(active);
            const focusRole = active?.dataset.serverPageFocus;
            const focusName = active?.name;
            const focusValue = active?.value;
            region.replaceWith(replacement);
            globalThis.CarlosTables?.initializeAll();
            selectionControls(replacement).forEach(input => {
                const key = selectionKey(input);
                if (selections.has(key)) input.checked = selections.get(key);
            });
            if (restoreFocus) {
                const controls = Array.from(replacement.querySelectorAll('button, input, select, a[href]'));
                const equivalent = controls.find(control => focusRole
                    ? control.dataset.serverPageFocus === focusRole
                    : control.name === focusName && control.value === focusValue);
                const target = equivalent && !equivalent.disabled ? equivalent
                    : replacement.querySelector('[data-server-page-form] button:not(:disabled)');
                target?.focus();
            }
        } catch (failure) {
            error.textContent = error.dataset.error;
        } finally {
            clearTimeout(deadline);
            delete region.dataset.loading;
        }
    });
})();
