/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
(function (global) {
    'use strict';
    const API_VERSION = 1;
    const API_HEADER = 'X-CARLOS-CDS-Export-API-Version';
    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
    const FINAL = new Set(['READY', 'REVIEW_REQUIRED', 'FAILED', 'CANCELLED', 'EXPIRED']);
    const LABELS = {
        ACCEPTED: 'Accepted — waiting for processing', DISCOVERING: 'Finding the selected records',
        CAPTURING: 'Capturing the selected records', RECONCILING: 'Checking retained and current records',
        SERIALIZING: 'Preparing patient files', VALIDATING: 'Checking patient files and coverage',
        PACKAGING: 'Preparing the transfer archive', ENCRYPTING: 'Encrypting and checking delivery',
        READY: 'Transfer package ready', REVIEW_REQUIRED: 'Review required — package unavailable',
        FAILED: 'Export stopped — package unavailable', CANCELLED: 'Export cancelled', EXPIRED: 'Transfer package expired'
    };
    function checkedJob(job) {
        if (!job || !UUID.test(job.id || '') || !Object.hasOwn(LABELS, job.state)
                || !Number.isSafeInteger(job.revision) || job.revision < 0 || !Number.isSafeInteger(job.epoch) || job.epoch < 0
                || !(job.totalPatients === null || Number.isSafeInteger(job.totalPatients) && job.totalPatients >= 0)
                || ![job.processedPatients, job.blockedPatients].every(n => Number.isSafeInteger(n) && n >= 0)
                || ![job.cancelRequested, job.workerOwned, job.artifactAvailable, job.complete, job.waiting].every(v => typeof v === 'boolean')
                || !(job.safeCode === null || /^CDS_EXPORT_[A-Z0-9_]{1,85}$/.test(job.safeCode))) {
            throw new Error('CDS_EXPORT_STATUS_INVALID');
        }
        const complete = job.state === 'READY' && job.artifactAvailable && !job.cancelRequested
            && job.totalPatients !== null && job.totalPatients > 0 && job.processedPatients === job.totalPatients && job.blockedPatients === 0;
        if (job.complete !== complete
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
    function presentation(job) {
        checkedJob(job);
        return {label: job.state === 'READY' && !job.complete ? 'Package unavailable — check job status' : LABELS[job.state],
            complete: job.complete, terminal: FINAL.has(job.state),
            count: job.totalPatients === null ? 'Patient total is not yet known.' : job.processedPatients + ' of ' + job.totalPatients + ' patients processed.',
            canCancel: !job.cancelRequested && (!FINAL.has(job.state) || job.state === 'READY'),
            canResume: !job.workerOwned && !['READY', 'EXPIRED'].includes(job.state)};
    }
    const api = {checkedJob, newest, presentation};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    global.CdsExportPage = api;
    if (!global.document) return;
    document.addEventListener('DOMContentLoaded', function () {
        const root = document.getElementById('cdsExport'); if (!root) return;
        const byId = id => document.getElementById(id), context = root.dataset.context;
        if (!/^(?:\/[A-Za-z0-9_.-]+)*$/.test(context) || context.split('/').some(v => ['.', '..'].includes(v))) return;
        let current = null, view = 0, timer = null, fresh = false, busy = false, cursor = '', historyGeneration = 0;
        const storageKey = 'carlos.cds.export.submission.' + root.dataset.owner;
        function notice(message, failure = false) {
            const node = byId('exportNotice'); node.textContent = message;
            node.className = 'alert ' + (failure ? 'alert-danger' : 'alert-info'); node.hidden = false;
        }
        function store(key) {
            byId('submissionKey').value = key; byId('savedSubmission').value = key;
            try { global.sessionStorage.setItem(storageKey, key); }
            catch (_) { notice('Keep submission key ' + key + '. This browser could not save it for recovery.'); }
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
                    throw new Error(/^CDS_EXPORT_[A-Z0-9_]+$/.test(data.code || '') ? data.code : 'CDS_EXPORT_RESPONSE_UNCONFIRMED');
                }
                return data;
            } finally { clearTimeout(timeout); }
        }
        function controls() {
            const shown = current ? presentation(current) : null;
            byId('reportExport').disabled = !fresh || busy || !current;
            byId('downloadExport').disabled = !fresh || busy || !shown?.complete;
            byId('cancelExport').disabled = !fresh || busy || !shown?.canCancel;
            byId('resumeExport').disabled = !fresh || busy || !shown?.canResume || root.dataset.canResume !== 'true';
        }
        function render(job) {
            current = newest(current, job); const shown = presentation(current); fresh = true;
            byId('reportRunId').value = current.id; byId('downloadRunId').value = current.id; byId('runId').value = current.id; byId('exportStatus').hidden = false;
            byId('exportState').textContent = shown.label; byId('exportCounts').textContent = shown.count + ' Blocked: ' + current.blockedPatients + '.';
            byId('exportConnection').textContent = current.waiting ? 'Saved; waiting for a compatible worker.' : 'Status confirmed by the server.';
            byId('exportCode').textContent = current.safeCode || '';
            const progress = byId('exportProgress');
            if (current.totalPatients === null) progress.removeAttribute('value');
            else { progress.max = Math.max(1, current.totalPatients); progress.value = current.processedPatients; }
            controls();
        }
        function artifactForm(id, csrfId, needsPackage) {
            byId(id).addEventListener('submit', async function (event) {
                event.preventDefault();
                if (!fresh || busy || !current || needsPackage && !current.complete) { notice('Refresh job status before downloading.', true); return; }
                try {
                    if (global.csrfTokenReady) await global.csrfTokenReady;
                    const token = document.querySelector('input[name="CSRF-TOKEN"]')?.value;
                    if (!token) throw new Error('CDS_EXPORT_CSRF_UNAVAILABLE');
                    byId(csrfId).value = token;
                    HTMLFormElement.prototype.submit.call(this);
                } catch (_) { notice('The download could not start. Refresh the page and try again.', true); }
            });
        }
        artifactForm('exportDownloadForm', 'downloadCsrf', true);
        artifactForm('exportReportForm', 'reportCsrf', false);
        function stale() {
            fresh = false; controls();
            byId('exportConnection').textContent = 'Status could not be refreshed. The job may still be processing; check again before acting.';
        }
        async function poll(id, generation, background = false) {
            if (generation !== view) return;
            try {
                const data = await request('cdsExportStatus', {runId: id, ...(background ? {autoRefresh: true} : {})});
                if (generation !== view) return; render(data.job);
            } catch (_) {
                if (generation !== view) return; stale();
            }
            if (generation === view && (!current || !FINAL.has(current.state) || !fresh)) timer = setTimeout(() => poll(id, generation, true), 5000);
        }
        async function open(id) {
            if (!UUID.test(id || '')) { notice('Enter a valid job ID.', true); return; }
            clearTimeout(timer); const generation = ++view; current = null; fresh = false; controls();
            byId('exportStatus').hidden = false; byId('exportState').textContent = 'Checking saved job';
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
            } catch (_) { if (generation === historyGeneration) notice('Saved job history could not be loaded. Try again.', true); }
        }
        function selection() {
            const roster = byId('selection').value === 'PHYSICIAN_ROSTER';
            byId('physicianNos').disabled = !roster; byId('patientNos').disabled = roster;
        }
        byId('selection').addEventListener('change', selection);
        byId('exportForm').addEventListener('submit', async event => {
            event.preventDefault(); if (busy) return; busy = true; byId('submitExport').disabled = true; controls();
            try {
                const params = Object.fromEntries(new FormData(event.target));
                delete params['CSRF-TOKEN'];
                params.categories = [...root.querySelectorAll('.export-category:checked')].map(input => input.value).join(',');
                notice('Confirming submission ' + params.submissionKey + '. Keep this key if the connection is interrupted.');
                const data = await request('cdsExportSubmit', params, true);
                notice('Export accepted. Job ' + data.job.id + '. Acceptance does not mean the package is ready.');
                await open(checkedJob(data.job).id); history();
            } catch (error) {
                notice('Submission could not be confirmed (' + error.message + '). Check saved key ' + byId('submissionKey').value + ' before starting another export.', true);
            } finally { busy = false; byId('submitExport').disabled = false; controls(); }
        });
        byId('findSubmission').addEventListener('click', async () => {
            const generation = ++view; clearTimeout(timer); fresh = false; controls();
            try {
                const data = await request('cdsExportStatus', {submissionKey: byId('savedSubmission').value});
                if (generation === view) await open(checkedJob(data.job).id);
            } catch (_) { if (generation === view) notice('The saved submission could not be confirmed. Keep its key and check again before submitting another export.', true); }
        });
        async function control(operation) {
            if (!fresh || busy || !current) return;
            const id = current.id, revision = current.revision, generation = view; busy = true; controls();
            try {
                const data = await request('cdsExportControl', {runId: id, operation, expectedRevision: String(revision)}, true);
                if (generation !== view) return; render(data.job);
                notice(operation === 'cancel' ? 'Cancellation recorded. Check the final job outcome.' : 'Resume requested. Processing may still be waiting.');
            } catch (_) {
                if (generation === view) { stale(); notice('The control outcome could not be confirmed. Refresh the saved job before retrying.', true); }
            } finally {
                busy = false; controls();
                if (generation === view) { clearTimeout(timer); await poll(id, generation); }
            }
        }
        byId('cancelExport').addEventListener('click', () => control('cancel'));
        byId('resumeExport').addEventListener('click', () => control('resume'));
        byId('openExport').addEventListener('click', () => open(byId('runId').value));
        byId('refreshExports').addEventListener('click', () => history());
        byId('nextExports').addEventListener('click', () => history(true));
        byId('newSubmission').addEventListener('click', () => {
            if (busy || !global.crypto?.randomUUID) return;
            if (!global.confirm('Start a separate export? Check the saved submission first if its outcome is unknown.')) return;
            store(global.crypto.randomUUID()); notice('New submission key created. Earlier jobs remain saved.');
        });
        let saved = '';
        try { saved = global.sessionStorage.getItem(storageKey) || ''; }
        catch (_) { notice('Browser recovery storage is unavailable. Keep the submission key shown on this page.'); }
        if (!global.crypto?.randomUUID) {
            byId('exportOptions').disabled = true; notice('This browser cannot safely create a submission key. Use a current browser.', true);
        } else store(/^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(saved) ? saved : global.crypto.randomUUID());
        selection(); history();
        const initial = new URLSearchParams(global.location.search).get('runId'); if (initial) open(initial);
        global.addEventListener('pagehide', () => { view++; historyGeneration++; clearTimeout(timer); });
    });
})(typeof window === 'undefined' ? globalThis : window);
