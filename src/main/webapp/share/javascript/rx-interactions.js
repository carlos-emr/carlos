/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(function () {
    'use strict';
    const source = document.currentScript;
    const endpoint = source.dataset.endpoint;
    const patient = source.dataset.demographicNo;
    document.addEventListener('DOMContentLoaded', function () {
        const stage = document.getElementById('rxText');
        const status = document.getElementById('interactingDrugErrorMsg');
        if (!stage || !status) return;
        let generation = 0;
        let timer;
        let previous = null;
        function snapshot() {
            return Array.from(stage.querySelectorAll('[id^="set_"]'))
                .map(card => card.id + ':' + (card.querySelector('[name^="draftRevision_"]')?.value || ''))
                .join(',');
        }
        function clear() {
            stage.querySelectorAll('[id^="major_"], [id^="moderate_"], [id^="minor_"], [id^="unknown_"]')
                .forEach(marker => { marker.textContent = ''; marker.style.display = 'none'; });
        }
        function message(text) {
            status.textContent = text;
            status.style.display = text ? 'block' : 'none';
        }
        async function refresh(version, state) {
            try {
                const url = new URL(endpoint, window.location.href);
                url.searchParams.set('demographicNo', patient);
                const response = await fetch(url, {credentials: 'same-origin', cache: 'no-store',
                    headers: {Accept: 'application/json'}});
                if (!response.ok) throw new Error('Interaction request refused');
                const result = await response.json();
                if (version !== generation || state !== snapshot()) return;
                if (result.checkFailed !== false || !Array.isArray(result.warnings)) throw new Error('Incomplete check');
                clear();
                result.warnings.forEach(warning => {
                    if (!['minor', 'moderate', 'major', 'unknown'].includes(warning.severity)) return;
                    const marker = document.getElementById(warning.severity + '_' + warning.cardId);
                    if (marker && stage.contains(marker)) {
                        marker.style.display = 'inline';
                        marker.textContent += (marker.textContent ? '\n' : '') + warning.text;
                    }
                });
                message('');
            } catch (_) {
                if (version !== generation || state !== snapshot()) return;
                clear();
                message('Drug interaction check unavailable. Review interactions before prescribing.');
            }
        }
        function changed() {
            const state = snapshot();
            if (state === previous) return;
            previous = state;
            const version = ++generation;
            clearTimeout(timer);
            clear();
            message(state ? 'Checking drug interactions…' : '');
            if (state) timer = setTimeout(() => refresh(version, state), 100);
        }
        new MutationObserver(changed).observe(stage, {childList: true, subtree: true});
        status.setAttribute('role', 'status');
        changed();
    });
}());
