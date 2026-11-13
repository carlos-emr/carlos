/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
(function (global) {
    'use strict';
    const API_VERSION = 1;
    const API_HEADER = 'X-CARLOS-CDS-Export-API-Version';
    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
    const FINAL = new Set(['READY', 'REVIEW_REQUIRED', 'FAILED', 'CANCELLED', 'EXPIRED']);
    /**
     * English defaults; the page replaces them with its language's text from #cdsExportText
     * (useMessages). Positional {0}/{1} markers are filled by text().
     */
    const TEXT = {
        stateAccepted: 'Accepted — waiting for processing', stateDiscovering: 'Finding the selected records',
        stateCapturing: 'Capturing the selected records', stateReconciling: 'Checking retained and current records',
        stateSerializing: 'Preparing patient files', stateValidating: 'Checking patient files and coverage',
        statePackaging: 'Preparing the transfer archive', stateEncrypting: 'Encrypting and checking delivery',
        stateReady: 'Transfer package ready', stateReviewRequired: 'Review required — package unavailable',
        stateFailed: 'Export stopped — package unavailable', stateCancelled: 'Export cancelled', stateExpired: 'Transfer package expired',
        heldBack: 'Transfer package ready without {0} held patient(s) — not a complete migration',
        readyUnavailable: 'Package unavailable — check job status',
        countUnknown: 'Patient total is not yet known.', countProgress: '{0} of {1} patients processed.',
        countWorking: 'Exporting {0} patients. The count of exported and held patients is shown when the job ends.',
        heldListed: 'Held back: {0} (listed in the package ReadMe and the report).', heldNone: 'Held back: {0}.',
        workerBusy: 'A worker is processing this job now, so it cannot be resumed or changed. Refresh to see its progress.',
        resumeInvalid: 'This job cannot be resumed in its current state.',
        cancelInvalid: 'This job cannot be cancelled in its current state.',
        runtimeChangedControl: 'The export implementation changed since this job was accepted. Start a new export.',
        statusChanged: 'The job changed since it was shown. Refresh it and try again.',
        admissionDisabled: 'New exports are turned off on this server. Nothing was submitted.',
        profileNotQualified: 'This server cannot export the selected profile. Nothing was submitted.',
        controlUnconfirmed: 'The control outcome could not be confirmed. Refresh the saved job before retrying.',
        missingRight: 'Export refused: the export provider\'s roles lack read access to {0}. Grant that right, or leave out the categories that need it, then submit again.',
        submissionUnconfirmed: 'Submission could not be confirmed ({0}). Check saved key {1} before starting another export.',
        keepKey: 'Keep submission key {0}. This browser could not save it for recovery.',
        waiting: 'Saved; waiting for a compatible worker.', confirmed: 'Status confirmed by the server.',
        runtimeChanged: 'The export implementation changed while this job was waiting or running. Start a new export.',
        refreshBeforeDownload: 'Refresh job status before downloading.',
        downloadFailed: 'The download could not start. Refresh the page and try again.',
        stale: 'Status could not be refreshed. The job may still be processing; check again before acting.',
        sessionEnded: 'Status could not be refreshed several times. Your session may have ended: reload the page (and sign in again) to check the job.',
        invalidJobId: 'Enter a valid job ID.', checking: 'Checking saved job',
        historyFailed: 'Saved job history could not be loaded. Try again.',
        confirming: 'Confirming submission {0}. Keep this key if the connection is interrupted.',
        accepted: 'Export accepted. Job {0}. Acceptance does not mean the package is ready.',
        savedUnconfirmed: 'The saved submission could not be confirmed. Keep its key and check again before submitting another export.',
        cancelRecorded: 'Cancellation recorded. Check the final job outcome.',
        resumeRequested: 'Resume requested. Processing may still be waiting.',
        confirmNew: 'Start a separate export? Check the saved submission first if its outcome is unknown.',
        newKey: 'New submission key created. Earlier jobs remain saved.',
        storageUnavailable: 'Browser recovery storage is unavailable. Keep the submission key shown on this page.',
        noRandomUuid: 'This browser cannot create a submission key. Use a current browser.',
        missingOperatorRight: 'Export refused: your account lacks {0}. Ask an administrator to grant that right, then submit again.',
        submissionConflict: 'Submission key {0} was already used for a different export. Open that job with Check submission, or start another submission.',
        requestInvalid: 'The export request was refused ({0}): check the patient selection, categories and recipient. Nothing was submitted.',
        submissionRefused: 'The export request was refused ({0}). Nothing was submitted.',
        clientRefresh: 'This page is out of date. Reload it, then try again.',
        jobNotFound: 'No export job with this ID was found.',
        jobAccessDenied: 'You cannot view this export job.',
        submissionNotFound: 'No job uses submission key {0}. Submitting with this key is safe: the server accepts it only once.',
        downloadStarted: 'The transfer opened in a new window. Check the browser\'s downloads; this button is available again in a few seconds.',
        confirmCancel: 'Cancel this export? It cannot be resumed, and a ready package is withdrawn.'
    };
    /** Replaces known keys with page text; unknown keys and non-text values are ignored. */
    function useMessages(messages) {
        for (const [key, value] of Object.entries(messages || {}))
            if (Object.hasOwn(TEXT, key) && typeof value === 'string' && value.length > 0) TEXT[key] = value;
    }
    function text(key, ...values) {
        return TEXT[key].replace(/\{(\d)\}/g, (marker, index) => index < values.length ? String(values[index]) : marker);
    }
    const STATES = ['ACCEPTED', 'DISCOVERING', 'CAPTURING', 'RECONCILING', 'SERIALIZING', 'VALIDATING', 'PACKAGING',
        'ENCRYPTING', 'READY', 'REVIEW_REQUIRED', 'FAILED', 'CANCELLED', 'EXPIRED'];
    function stateLabel(state) {
        return text('state' + state.toLowerCase().replace(/(^|_)([a-z])/g, (all, sep, letter) => letter.toUpperCase()));
    }
    function checkedJob(job) {
        if (!job || !UUID.test(job.id || '') || !STATES.includes(job.state)
                || !Number.isSafeInteger(job.revision) || job.revision < 0 || !Number.isSafeInteger(job.epoch) || job.epoch < 0
                || !(job.totalPatients === null || Number.isSafeInteger(job.totalPatients) && job.totalPatients >= 0)
                || ![job.processedPatients, job.blockedPatients].every(n => Number.isSafeInteger(n) && n >= 0)
                || ![job.cancelRequested, job.workerOwned, job.artifactAvailable, job.complete, job.heldBack, job.waiting].every(v => typeof v === 'boolean')
                || !(job.safeCode === null || /^CDS_EXPORT_[A-Z0-9_]{1,85}$/.test(job.safeCode))) {
            throw new Error('CDS_EXPORT_STATUS_INVALID');
        }
        const complete = job.state === 'READY' && job.artifactAvailable && !job.cancelRequested
            && job.totalPatients !== null && job.totalPatients > 0 && job.processedPatients === job.totalPatients && job.blockedPatients === 0;
        const heldBack = job.state === 'READY' && job.artifactAvailable && !job.cancelRequested
            && job.totalPatients !== null && job.totalPatients > 0 && job.processedPatients === job.totalPatients
            && job.blockedPatients > 0 && job.blockedPatients < job.totalPatients;
        if (job.complete !== complete || job.heldBack !== heldBack
                || job.waiting !== (!FINAL.has(job.state) && !job.workerOwned)
                || job.totalPatients !== null && job.processedPatients > job.totalPatients) throw new Error('CDS_EXPORT_STATUS_INVALID');
        return job;
    }
    function newest(current, incoming) {
        checkedJob(incoming);
        if (!current || current.id !== incoming.id) return incoming;
        if (incoming.epoch < current.epoch || incoming.revision < current.revision) return current;
        return incoming;
    }
    function running(job) {
        return !FINAL.has(job.state) && job.workerOwned;
    }
    function presentation(job) {
        checkedJob(job);
        const label = job.heldBack
            ? text('heldBack', job.blockedPatients)
            : job.state === 'READY' && !job.complete ? text('readyUnavailable') : stateLabel(job.state);
        return {label, complete: job.complete, downloadable: job.complete || job.heldBack, terminal: FINAL.has(job.state),
            // The ledger counts a patient as processed only when the package is published, so a job a
            // worker is running shows its selection, never a frozen "0 of N". A job no worker owns
            // (waiting, or stopped by a restart) is not shown as working.
            count: job.totalPatients === null ? text('countUnknown')
                : running(job) ? text('countWorking', job.totalPatients)
                : text('countProgress', job.processedPatients, job.totalPatients),
            running: running(job),
            canCancel: !job.cancelRequested && (!FINAL.has(job.state) || job.state === 'READY'),
            canResume: !job.workerOwned && !['READY', 'EXPIRED'].includes(job.state)};
    }
    /**
     * The page error for a refused or unconfirmed response; only a fixed code, a fixed right name
     * and the HTTP status are kept.
     */
    function responseFailure(data, status) {
        const failure = new Error(/^CDS_EXPORT_[A-Z0-9_]+$/.test(data && data.code || '') ? data.code : 'CDS_EXPORT_RESPONSE_UNCONFIRMED');
        if (/^_[A-Za-z0-9_.]{1,64}$/.test(data && data.missingRight || '')) failure.missingRight = data.missingRight;
        if (Number.isInteger(status)) failure.status = status;
        return failure;
    }
    /**
     * A fixed-code 4xx answer is the server refusing before anything was accepted (408 and 429
     * are not answers); every other failure leaves the outcome unknown.
     */
    function refusedBeforeAcceptance(error) {
        return !!error && Number.isInteger(error.status) && error.status >= 400 && error.status < 500
            && error.status !== 408 && error.status !== 429 && /^CDS_EXPORT_[A-Z0-9_]+$/.test(error.message)
            && error.message !== 'CDS_EXPORT_RESPONSE_UNCONFIRMED';
    }
    /** Poll every 5 s; back off after failures and stop after six consecutive ones (review W7). */
    function pollDelay(failures) {
        if (failures >= 6) return null;
        return failures === 0 ? 5000 : Math.min(60000, 5000 * 2 ** failures);
    }
    /** Known definitive control refusals; anything else stays an unconfirmed outcome. */
    const CONTROL_REFUSALS = {
        CDS_EXPORT_WORKER_BUSY: 'workerBusy',
        CDS_EXPORT_RESUME_INVALID: 'resumeInvalid',
        CDS_EXPORT_CANCEL_INVALID: 'cancelInvalid',
        CDS_EXPORT_RUNTIME_CHANGED: 'runtimeChangedControl',
        CDS_EXPORT_STATUS_CHANGED: 'statusChanged',
        CDS_EXPORT_ADMISSION_DISABLED: 'admissionDisabled',
        CDS_EXPORT_PROFILE_NOT_QUALIFIED: 'profileNotQualified'
    };
    function controlNotice(error) {
        const key = error && Object.hasOwn(CONTROL_REFUSALS, error.message) ? CONTROL_REFUSALS[error.message] : null;
        return key ? text(key) : text('controlUnconfirmed');
    }
    /** Rights of the signed-in account itself, which leaving out categories cannot avoid. */
    const OPERATOR_RIGHTS = new Set(['_cds_export', '_admin']);
    function submissionNotice(error, key) {
        if (error && error.missingRight)
            return text(OPERATOR_RIGHTS.has(error.missingRight) ? 'missingOperatorRight' : 'missingRight', error.missingRight);
        const code = error && error.message;
        if (code === 'CDS_EXPORT_ADMISSION_DISABLED' || code === 'CDS_EXPORT_PROFILE_NOT_QUALIFIED')
            return text(CONTROL_REFUSALS[code]);
        if (code === 'CDS_EXPORT_SUBMISSION_CONFLICT') return text('submissionConflict', key);
        if (code === 'CDS_EXPORT_CLIENT_REFRESH_REQUIRED') return text('clientRefresh');
        if (refusedBeforeAcceptance(error)) return text(error.status === 400 ? 'requestInvalid' : 'submissionRefused', code);
        return text('submissionUnconfirmed', code, key);
    }
    /**
     * The answer that ends a job lookup for good (an unknown or foreign job, an outdated page), or
     * null for a failure worth retrying.
     */
    function lookupNotice(error) {
        const code = error && error.message;
        if (code === 'CDS_EXPORT_RUN_NOT_FOUND') return text('jobNotFound');
        if (code === 'CDS_EXPORT_ACCESS_DENIED') return text('jobAccessDenied');
        if (code === 'CDS_EXPORT_CLIENT_REFRESH_REQUIRED') return text('clientRefresh');
        if (refusedBeforeAcceptance(error) && error.status === 400) return text('invalidJobId');
        return null;
    }
    /** An RFC 4122 version 4 identifier, also where randomUUID is withheld (plain HTTP). */
    function randomKey(crypto) {
        if (crypto && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
        if (!crypto || typeof crypto.getRandomValues !== 'function') return null;
        const bytes = crypto.getRandomValues(new Uint8Array(16));
        bytes[6] = (bytes[6] & 0x0f) | 0x40; bytes[8] = (bytes[8] & 0x3f) | 0x80;
        const hex = [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
        return hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16) + '-' + hex.slice(16, 20) + '-' + hex.slice(20);
    }
    const api = {checkedJob, newest, presentation, responseFailure, submissionNotice, lookupNotice, randomKey, controlNotice, pollDelay, useMessages};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    global.CdsExportPage = api;
    if (!global.document) return;
    document.addEventListener('DOMContentLoaded', function () {
        const root = document.getElementById('cdsExport'); if (!root) return;
        const pageText = {};
        for (const entry of document.getElementById('cdsExportText')?.querySelectorAll('[data-key]') || []) pageText[entry.dataset.key] = entry.dataset.value;
        useMessages(pageText);
        const byId = id => document.getElementById(id), context = root.dataset.context;
        if (!/^(?:\/[A-Za-z0-9_.-]+)*$/.test(context) || context.split('/').some(v => ['.', '..'].includes(v))) return;
        let current = null, view = 0, timer = null, fresh = false, busy = false, cursor = '', historyGeneration = 0;
        // An artifact request runs in its own window; the page cannot see it finish.
        let transferring = false, transferWindows = 0, lookupGeneration = 0;
        const storageKey = 'carlos.cds.export.submission.' + root.dataset.owner;
        function notice(message, failure = false) {
            // A failure is announced at once (role=alert); progress notes stay polite.
            const node = byId('exportNotice');
            node.setAttribute('role', failure ? 'alert' : 'status');
            node.setAttribute('aria-live', failure ? 'assertive' : 'polite');
            node.textContent = message;
            node.className = 'alert ' + (failure ? 'alert-danger' : 'alert-info'); node.hidden = false;
        }
        function store(key) {
            byId('submissionKey').value = key; byId('savedSubmission').value = key;
            try { global.sessionStorage.setItem(storageKey, key); }
            catch (_) { notice(text('keepKey', key)); }
        }
        async function request(route, values = {}, post = false) {
            const params = new URLSearchParams({...values, apiVersion: String(API_VERSION)});
            const headers = {'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest'};
            if (post) {
                if (global.csrfTokenReady) await global.csrfTokenReady;
                const token = document.querySelector('input[name="CSRF-TOKEN"]')?.value;
                if (!token) throw new Error('CDS_EXPORT_CSRF_UNAVAILABLE');
                headers['CSRF-TOKEN'] = token; headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
            }
            const abort = new AbortController(), timeout = setTimeout(() => abort.abort(), 15000);
            try {
                const response = await fetch(context + '/form/' + route + (post ? '' : '?' + params), {
                    method: post ? 'POST' : 'GET', body: post ? params.toString() : undefined,
                    headers, credentials: 'same-origin', cache: 'no-store', signal: abort.signal
                });
                if (response.headers.get(API_HEADER) !== String(API_VERSION)
                        || !response.headers.get('Content-Type')?.startsWith('application/json')) throw new Error('CDS_EXPORT_RESPONSE_UNCONFIRMED');
                const data = await response.json();
                if (data.apiVersion !== API_VERSION || !response.ok || data.ok !== true) {
                    throw responseFailure(data, response.status);
                }
                return data;
            } finally { clearTimeout(timeout); }
        }
        function controls() {
            const shown = current ? presentation(current) : null;
            byId('reportExport').disabled = !fresh || busy || transferring || !current;
            byId('downloadExport').disabled = !fresh || busy || transferring || !shown?.downloadable;
            byId('cancelExport').disabled = !fresh || busy || !shown?.canCancel;
            byId('resumeExport').disabled = !fresh || busy || !shown?.canResume || root.dataset.canResume !== 'true';
        }
        function render(job) {
            current = newest(current, job); const shown = presentation(current); fresh = true;
            byId('reportRunId').value = current.id; byId('downloadRunId').value = current.id; byId('exportStatus').hidden = false;
            byId('exportState').textContent = shown.label; byId('exportCounts').textContent = shown.count + ' '
                + text(current.blockedPatients > 0 ? 'heldListed' : 'heldNone', current.blockedPatients);
            byId('exportConnection').textContent = current.waiting ? text('waiting') : text('confirmed');
            byId('exportCode').textContent = current.safeCode === 'CDS_EXPORT_RUNTIME_CHANGED'
                ? text('runtimeChanged')
                : current.safeCode || '';
            const progress = byId('exportProgress');
            // Indeterminate while a worker runs it: per-patient progress is recorded at publication.
            if (current.totalPatients === null || shown.running) progress.removeAttribute('value');
            else { progress.max = Math.max(1, current.totalPatients); progress.value = current.processedPatients; }
            controls();
        }
        function artifactForm(id, csrfId, needsPackage) {
            byId(id).addEventListener('submit', async function (event) {
                event.preventDefault();
                if (!fresh || busy || !current || needsPackage && !(current.complete || current.heldBack)) { notice(text('refreshBeforeDownload'), true); return; }
                try {
                    if (global.csrfTokenReady) await global.csrfTokenReady;
                    const token = document.querySelector('input[name="CSRF-TOKEN"]')?.value;
                    if (!token) throw new Error('CDS_EXPORT_CSRF_UNAVAILABLE');
                    byId(csrfId).value = token;
                    // A second request into the same named window would abort a transfer still being
                    // prepared, so each request gets its own window.
                    this.target = this.dataset.window + '-' + (++transferWindows);
                    HTMLFormElement.prototype.submit.call(this);
                    transferring = true; controls(); notice(text('downloadStarted'));
                    const id = current.id;
                    setTimeout(() => {
                        transferring = false; controls();
                        // The package may have expired or been withdrawn meanwhile.
                        if (current && current.id === id) poll(id, view);
                    }, 10000);
                } catch (_) { notice(text('downloadFailed'), true); }
            });
        }
        artifactForm('exportDownloadForm', 'downloadCsrf', true);
        artifactForm('exportReportForm', 'reportCsrf', false);
        function stale() {
            fresh = false; controls();
            byId('exportConnection').textContent = text('stale');
        }
        let failures = 0;
        async function poll(id, generation, background = false) {
            if (generation !== view) return;
            try {
                const data = await request('cdsExportStatus', {runId: id, ...(background ? {autoRefresh: true} : {})});
                if (generation !== view) return; failures = 0; render(data.job);
            } catch (error) {
                if (generation !== view) return;
                const ended = lookupNotice(error);
                if (ended) {
                    fresh = false; controls();
                    if (!current) byId('exportState').textContent = ended;
                    byId('exportConnection').textContent = ended; notice(ended, true);
                    return;
                }
                stale(); failures++;
            }
            if (generation !== view || current && FINAL.has(current.state) && fresh) return;
            const delay = pollDelay(failures);
            // After repeated failures (for example an inactivity logout) stop polling and say so.
            if (delay === null) {
                byId('exportConnection').textContent = text('sessionEnded');
                return;
            }
            timer = setTimeout(() => poll(id, generation, true), delay);
        }
        async function open(id) {
            if (!UUID.test(id || '')) { notice(text('invalidJobId'), true); return; }
            clearTimeout(timer); const generation = ++view; current = null; fresh = false; failures = 0; controls();
            byId('runId').value = id;
            byId('exportStatus').hidden = false; byId('exportState').textContent = text('checking');
            byId('exportCounts').textContent = ''; byId('exportCode').textContent = '';
            byId('exportProgress').removeAttribute('value'); await poll(id, generation);
        }
        async function history(next = false) {
            const generation = ++historyGeneration;
            try {
                const data = await request('cdsExportStatus', {after: next ? cursor : '', limit: '25'});
                if (generation !== historyGeneration) return;
                const entries = data.jobs.map(checkedJob), nodes = entries.map(job => {
                    const button = document.createElement('button'); button.type = 'button'; button.className = 'btn btn-outline-secondary';
                    button.textContent = job.id + ' — ' + presentation(job).label; button.addEventListener('click', () => open(job.id)); return button;
                });
                byId('exportHistory').replaceChildren(...nodes); cursor = data.nextAfter;
                byId('nextExports').disabled = data.mayHaveMore !== true;
            } catch (_) { if (generation === historyGeneration) notice(text('historyFailed'), true); }
        }
        function selection() {
            const roster = byId('selection').value === 'PHYSICIAN_ROSTER';
            byId('physicianNos').disabled = !roster; byId('patientNos').disabled = roster;
            byId('physicianNos').required = roster; byId('patientNos').required = !roster;
        }
        byId('selection').addEventListener('change', selection);
        byId('exportForm').addEventListener('submit', async event => {
            event.preventDefault(); if (busy) return; busy = true; byId('submitExport').disabled = true; controls();
            try {
                const params = Object.fromEntries(new FormData(event.target));
                delete params['CSRF-TOKEN'];
                params.categories = [...root.querySelectorAll('.export-category:checked')].map(input => input.value).join(',');
                notice(text('confirming', params.submissionKey));
                const data = await request('cdsExportSubmit', params, true);
                notice(text('accepted', data.job.id));
                await open(checkedJob(data.job).id); history();
            } catch (error) {
                notice(submissionNotice(error, byId('submissionKey').value), true);
            } finally { busy = false; byId('submitExport').disabled = false; controls(); }
        });
        byId('findSubmission').addEventListener('click', async () => {
            // The job on screen keeps refreshing until a job is found: open() replaces it then.
            const lookup = ++lookupGeneration, key = byId('savedSubmission').value;
            try {
                const data = await request('cdsExportStatus', {submissionKey: key});
                if (lookup === lookupGeneration) await open(checkedJob(data.job).id);
            } catch (error) {
                if (lookup !== lookupGeneration) return;
                notice(error && error.message === 'CDS_EXPORT_RUN_NOT_FOUND' ? text('submissionNotFound', key) : text('savedUnconfirmed'), true);
            }
        });
        async function control(operation) {
            if (!fresh || busy || !current) return;
            // Fence in-flight polls as well as scheduled ones before starting a control.
            const id = current.id, revision = current.revision, generation = ++view;
            clearTimeout(timer); busy = true; controls();
            try {
                const data = await request('cdsExportControl', {runId: id, operation, expectedRevision: String(revision)}, true);
                if (generation !== view) return; render(data.job);
                notice(operation === 'cancel' ? text('cancelRecorded') : text('resumeRequested'));
            } catch (error) {
                if (generation === view) { stale(); notice(controlNotice(error), true); }
            } finally {
                busy = false; controls();
                if (generation === view) { clearTimeout(timer); await poll(id, generation); }
            }
        }
        byId('cancelExport').addEventListener('click', () => global.confirm(text('confirmCancel')) ? control('cancel') : undefined);
        byId('resumeExport').addEventListener('click', () => control('resume'));
        byId('openExport').addEventListener('click', () => open(byId('runId').value));
        byId('refreshExports').addEventListener('click', () => history());
        byId('nextExports').addEventListener('click', () => history(true));
        byId('newSubmission').addEventListener('click', () => {
            const key = busy ? null : randomKey(global.crypto);
            if (!key) return;
            if (!global.confirm(text('confirmNew'))) return;
            store(key); notice(text('newKey'));
        });
        let saved = '';
        try { saved = global.sessionStorage.getItem(storageKey) || ''; }
        catch (_) { notice(text('storageUnavailable')); }
        const generated = randomKey(global.crypto);
        if (!generated) {
            byId('exportOptions').disabled = true; notice(text('noRandomUuid'), true);
        } else store(/^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(saved) ? saved : generated);
        selection(); history();
        const initial = new URLSearchParams(global.location.search).get('runId'); if (initial) open(initial);
        global.addEventListener('pagehide', () => { view++; historyGeneration++; clearTimeout(timer); });
    });
})(typeof window === 'undefined' ? globalThis : window);
