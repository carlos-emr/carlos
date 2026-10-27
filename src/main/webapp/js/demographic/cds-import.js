/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
(function (global) {
    'use strict';

    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
    const COMPLETE = new Set(['COMPLETE', 'COMPLETE_WITH_NONLOSSY_WARNINGS', 'ALREADY_COMPLETE', 'DRY_RUN_COMPLETE']);
    const FINAL = new Set([...COMPLETE, 'FAILED_BEFORE_WRITE', 'REFUSED', 'CANCELLED_BEFORE_WRITE',
        'PARTIAL_COMMITTED', 'RECONCILIATION_REQUIRED', 'CANCELLED', 'FAILED']);

    function statusPresentation(job, summary) {
        if (!job || !UUID.test(job.id || '') || typeof job.state !== 'string') {
            return {state: 'UNAVAILABLE', clean: false, final: false};
        }
        const outcomes = summary?.outcomes;
        const expected = summary?.expectedPatients;
        const entries = outcomes && !Array.isArray(outcomes) && typeof outcomes === 'object' ? Object.entries(outcomes) : [];
        const accounted = entries.reduce((sum, [, count]) => Number.isSafeInteger(count) && count >= 0 ? sum + count : NaN, 0);
        const allComplete = entries.length > 0 && entries.every(([state, count]) => count === 0
            || ['COMPLETE', 'COMPLETE_WITH_NONLOSSY_WARNINGS', 'ALREADY_COMPLETE'].includes(state));
        const clean = COMPLETE.has(job.state) && job.state !== 'DRY_RUN_COMPLETE' && summary && summary.cleanComplete === true
            && summary.clinicalComplete === true && summary.discoveryComplete === true && summary.reportsDelivered === true
            && summary.cleanupComplete === true && summary.pendingUnits === 0 && summary.unresolvedReviewItems === 0
            && summary.inactiveDraftCount === 0
            && Number.isSafeInteger(expected) && expected > 0 && accounted === expected && allComplete;
        return {state: job.state, clean: Boolean(clean), final: FINAL.has(job.state)};
    }

    function boundedIds(values, maximum = 20) {
        return [...new Set(values.filter(value => typeof value === 'string' && UUID.test(value)))].slice(-maximum);
    }

    function latestUploadAttempt(upload) {
        // Execution supersedes its preview in navigation; the API keeps both bindings distinct.
        for (const id of [upload?.runId, upload?.dryRunId]) {
            if (typeof id === 'string' && UUID.test(id)) return id;
        }
        return null;
    }

    function mayStopPolling(summary) {
        // Clinical finality does not prove reports or cleanup finished; only the durable operation receipt does.
        return summary?.operationTerminal === true;
    }

    function recoveryControls(summary = {}) {
        const fatalReview = summary.reviewErrorCode === 'REVIEW_WORKER_FATAL'
            || summary.workerAlert === 'REVIEW_WORKER_FATAL';
        const pendingReview = Number.isSafeInteger(summary.pendingReviewCount) && summary.pendingReviewCount > 0;
        const inactiveDraft = Number.isSafeInteger(summary.inactiveDraftCount) && summary.inactiveDraftCount > 0;
        return {fatalReview, resumeDisabled: Boolean(summary.supervisorAlert)
            || (!fatalReview && (pendingReview || inactiveDraft))};
    }

    function indexedSourceValuePath(value) {
        if (typeof value !== 'string' || value.length > 2048) return false;
        // This only validates the indexed address shape. The backend must locate an exact scalar leaf.
        return /^\/OmdCds\[1\]\/PatientRecord\[[1-9][0-9]*\](?:\/[A-Za-z_][A-Za-z0-9_.-]*\[[1-9][0-9]*\]){2,}(?:\/@[A-Za-z_][A-Za-z0-9_.-]*)?$/.test(value);
    }

    function reviewEligibility(diagnostic, summary = {}) {
        return {canDecline: /^[0-9a-f]{64}$/.test(diagnostic?.patientKey || ''),
            canApproveRevision: diagnostic?.code === 'SOURCE_REVISION_REVIEW_REQUIRED'
                && /^[0-9a-f]{64}$/.test(diagnostic?.patientKey || '')
                && summary.unwrittenRevisionApprovalAllowed === true,
            initialXPath: indexedSourceValuePath(diagnostic?.sourceXPath) ? diagnostic.sourceXPath : ''};
    }

    function effectiveOptions(values) {
        const result = {providerNo: String(values.providerNo || ''), sourceNamespace: String(values.sourceNamespace || ''),
            sourceProfile: String(values.sourceProfile || ''), mergeExisting: Boolean(values.mergeExisting),
            dryRun: Boolean(values.dryRun), matchProviderNames: Boolean(values.matchProviderNames),
            synthetic: Boolean(values.synthetic), timeshiftInDays: values.synthetic ? Number(values.timeshiftInDays || 0) : 0};
        if (!/^[A-Za-z0-9_-]{1,16}$/.test(result.providerNo) || !result.sourceNamespace || !result.sourceProfile
                || !Number.isSafeInteger(result.timeshiftInDays) || (result.synthetic && result.mergeExisting)) {
            throw new Error('IMPORT_OPTIONS_INVALID');
        }
        for (const name of ['programId', 'facilityId', ...(result.synthetic ? ['courseId'] : [])]) {
            if (values[name] !== undefined && values[name] !== '') {
                const number = Number(values[name]);
                if (!Number.isSafeInteger(number) || number < 1) throw new Error('IMPORT_OPTIONS_INVALID');
                result[name] = number;
            }
        }
        if (result.synthetic && !result.courseId) throw new Error('IMPORT_SYNTHETIC_COURSE_REQUIRED');
        return Object.freeze(result);
    }

    async function sha256(blob, cryptoApi = global.crypto) {
        if (!cryptoApi?.subtle) throw new Error('UPLOAD_SECURE_CONTEXT_REQUIRED');
        const bytes = await blob.arrayBuffer();
        const digest = await cryptoApi.subtle.digest('SHA-256', bytes);
        return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
    }

    async function boundedJson(response, maximumBytes = 2 * 1024 * 1024) {
        if (!response.body || !Number.isSafeInteger(maximumBytes) || maximumBytes < 1) throw new Error('IMPORT_RESPONSE_UNAVAILABLE');
        const decoder = new global.TextDecoder('utf-8', {fatal: true});
        let bytes = 0; let text = '';
        // pipeTo owns stream completion/backpressure; write failures cancel the network source.
        await response.body.pipeTo(new global.WritableStream({
            write(chunk) {
                bytes += chunk.byteLength;
                if (bytes > maximumBytes) throw new Error('IMPORT_RESPONSE_CAPACITY_EXCEEDED');
                text += decoder.decode(chunk, {stream: true});
            }
        }));
        text += decoder.decode();
        return JSON.parse(text);
    }

    async function verifyPrefix(file, receipt, readPage, hash = sha256, progress = () => {}) {
        if (!Number.isSafeInteger(receipt.receivedBytes) || receipt.receivedBytes < 0
                || receipt.expectedBytes !== file.size || receipt.receivedBytes > file.size
                || receipt.fileName !== file.name || !Number.isSafeInteger(receipt.committedChunks)
                || receipt.committedChunks < 0 || !Number.isSafeInteger(receipt.chunkBytes)
                || receipt.chunkBytes < 65536 || receipt.chunkBytes > (16 * 1024 * 1024)) {
            throw new Error('UPLOAD_RESELECTED_FILE_MISMATCH');
        }
        let index = 0;
        let offset = 0;
        while (index < receipt.committedChunks) {
            const page = await readPage(index);
            if (!Array.isArray(page) || page.length < 1 || page.length > 100) throw new Error('UPLOAD_PREFIX_RECEIPT_MISSING');
            for (const chunk of page) {
                if (index >= receipt.committedChunks || chunk.index !== index || chunk.offset !== offset
                        || !Number.isSafeInteger(chunk.bytes) || chunk.bytes < 1 || chunk.bytes > receipt.chunkBytes
                        || chunk.offset + chunk.bytes > receipt.receivedBytes) throw new Error('UPLOAD_PREFIX_RECEIPT_CONFLICT');
                if (await hash(file.slice(chunk.offset, chunk.offset + chunk.bytes)) !== chunk.sha256) {
                    throw new Error('UPLOAD_RESELECTED_FILE_MISMATCH');
                }
                offset += chunk.bytes; index++;
                progress(offset);
            }
        }
        if (offset !== receipt.receivedBytes) throw new Error('UPLOAD_PREFIX_RECEIPT_MISSING');
        return offset;
    }

    const api = {statusPresentation, boundedIds, latestUploadAttempt, mayStopPolling, recoveryControls, indexedSourceValuePath, reviewEligibility,
        effectiveOptions, sha256, boundedJson, verifyPrefix};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    global.CarlosCdsImport = api;
    if (!global.document) return;
    global.document.addEventListener('DOMContentLoaded', () => {
        const document = global.document;
        const root = document.getElementById('cdsImport');
        if (!root) return;
        const byId = id => document.getElementById(id);
        const fr = document.documentElement.lang.toLowerCase().startsWith('fr');
        const text = fr ? {
            failed: 'La demande n’a pas abouti. Vérifier l’état durable avant de réessayer.',
            choose: 'Sélectionner au moins un fichier XML/ZIP.', maxFiles: 'Au plus 128 fichiers par sélection; utiliser un ZIP pour les grands lots.',
            accepted: 'Accepté; l’importation clinique n’est pas encore confirmée.', verify: 'Vérification du préfixe déjà reçu',
            transfer: 'Transfert', state: 'État durable', unavailable: 'État indisponible ou périmé; reconnecter après réauthentification.',
            queued: 'fichier(s) accepté(s). Vérifier les reçus durables de chaque tâche.', resume: 'Sélectionner pour reprise',
            open: 'Ouvrir la tâche', abandon: 'Abandonner le transfert non soumis', review: 'Réviser',
            evidence: 'Preuve technique',
            reviewVersion: 'Aucune version de révision sûre n’est disponible. Recharger l’état ou consulter le rapport.',
            reviewPath: 'Indiquer le XPath indexé exact d’une valeur scalaire, pas un patient ou une section.',
            reviewPatient: 'Le diagnostic ne désigne aucune copie de patient vérifiable. Le refus est indisponible; consulter le rapport protégé ou corriger la source.',
            revisionUnavailable: 'L’approbation de remplacement exige un conflit source identifié et la preuve que les deux tâches n’ont aucune donnée validée, aucun blocage ni aucune incertitude. Réconcilier les tâches précédentes; ne pas changer l’espace de noms pour contourner ce contrôle.',
            inactiveDrafts: 'Corrections provisoires inactives : compléter les corrections de validation XML avant de reprendre. Nombre : ',
            release: 'La libération du mode maintenance reste incomplète. Les validations cliniques restent enregistrées. Réconcilier les blocages, puis utiliser Reprendre pour réessayer la libération et les rapports, sans rejouer les unités cliniques. État : ',
            reviewFatal: 'Le traitement de révision s’est arrêté; Reprendre réessaie cette révision avec ses preuves conservées avant toute importation.',
            supervisor: 'Le superviseur d’importation nécessite une réparation et un redémarrage du service par un administrateur. Reprendre ne répare pas le superviseur.',
            page: 'Cette page est limitée; les rapports complets restent disponibles.', current: 'Courant',
            selectOne: 'Resélectionner exactement un fichier pour reprendre un transfert.',
            housekeeping: 'Le nettoyage des transferts nécessite une intervention administrative. Vérifier les transferts en attente et les réservations du disque.',
            worker: 'Le processus d’importation s’est arrêté. La récupération et la réconciliation des validations sont en attente.',
            secure: 'Une connexion sécurisée au navigateur est requise pour vérifier les octets transférés. Ouvrir CARLOS par son adresse HTTPS approuvée.',
        } : {
            failed: 'The request did not complete. Check durable status before retrying.',
            choose: 'Select at least one XML/ZIP file.', maxFiles: 'Select at most 128 files at once; use a ZIP for larger batches.',
            accepted: 'Accepted; clinical completion has not yet been confirmed.', verify: 'Verifying previously received prefix',
            transfer: 'Transfer', state: 'Durable state', unavailable: 'Status unavailable or stale; reconnect after reauthentication.',
            queued: 'file(s) accepted. Check each durable run receipt.', resume: 'Select for resume',
            open: 'Open run', abandon: 'Abandon unsubmitted upload', review: 'Review',
            evidence: 'Technical evidence',
            reviewVersion: 'No safe review version is available. Reload status or inspect the protected report.',
            reviewPath: 'Enter the exact indexed XPath of a scalar value, rather than a patient or section.',
            reviewPatient: 'This diagnostic has no verifiable patient copy. Decline is unavailable; inspect the protected report or correct the source.',
            revisionUnavailable: 'Replacement approval requires an identified source conflict and proof that both attempts have no committed data, holds or uncertainty. Reconcile prior work; do not change namespaces to bypass this check.',
            inactiveDrafts: 'Inactive correction drafts need further XML validation corrections before resume. Count: ',
            release: 'Maintenance release is incomplete. Clinical commits remain recorded. Reconcile held runs, then use Resume to retry release and reports without replaying clinical units. State: ',
            reviewFatal: 'The review worker stopped; Resume retries that preserved review before any import execution.',
            supervisor: 'The import supervisor needs administrator repair and service restart. Resume cannot repair the supervisor.',
            page: 'This is a bounded page; complete protected reports remain available.', current: 'Current',
            selectOne: 'Reselect exactly one file to resume an upload.',
            housekeeping: 'Upload cleanup needs administrator attention. Check pending uploads and disk reservations.',
            worker: 'The import worker stopped. Recovery and commit reconciliation are pending.',
            secure: 'A secure browser connection is required to verify uploaded bytes. Open CARLOS at its approved HTTPS address.',
        };
        let activeRun = '';
        let timer;
        let busy = false;
        let patientCursor = '';
        let diagnosticCursor = 0;
        let patientPageStart = '';
        let diagnosticPageStart = 0;
        let lastRunUpdated = '';
        let uploadCursor = '';
        let latestSummary = {};
        const storageKey = 'carlos-cds-import:' + root.dataset.owner;

        function notice(message, danger = false) {
            const node = byId('cdsNotice'); node.hidden = false; node.textContent = message;
            node.className = 'alert ' + (danger ? 'alert-danger' : 'alert-info');
        }
        function token() {
            const field = document.querySelector('input[name="CSRF-TOKEN"]');
            if (!field || !field.value) throw new Error('IMPORT_CSRF_TOKEN_UNAVAILABLE');
            return field.value;
        }
        async function request(route, params = {}, options = {}) {
            const method = options.method || 'GET';
            const headers = {'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': token(), ...(options.headers || {})};
            const url = new URL(root.dataset.context + '/form/' + route, global.location.origin);
            let body = options.body;
            if (method === 'GET' || body !== undefined) {
                for (const [name, value] of Object.entries(params)) url.searchParams.set(name, String(value));
            } else {
                body = new URLSearchParams();
                for (const [name, value] of Object.entries(params)) if (value !== undefined && value !== null) body.set(name, String(value));
            }
            const controller = new global.AbortController();
            const deadline = global.setTimeout(() => controller.abort(), options.body !== undefined ? 135000 : 30000);
            try {
                const response = await global.fetch(url, {method, headers, body, signal: controller.signal,
                    credentials: 'same-origin', cache: 'no-store'});
                const type = response.headers.get('content-type') || '';
                if (response.redirected || !type.includes('application/json')) {
                    await response.body?.cancel().catch(() => {});
                    throw new Error('IMPORT_RESPONSE_UNAVAILABLE_HTTP_' + response.status);
                }
                const data = await boundedJson(response);
                if (!response.ok || data?.ok !== true) {
                    const failure = new Error(data?.code || 'IMPORT_RESPONSE_FAILED'); failure.status = response.status; throw failure;
                }
                return data;
            } finally {
                global.clearTimeout(deadline);
            }
        }
        function failure(error) {
            notice(error.message === 'UPLOAD_SECURE_CONTEXT_REQUIRED' ? text.secure
                : text.failed + ' ' + String(error.message || 'IMPORT_UNAVAILABLE').slice(0, 160), true);
        }
        function safe(action) { return () => Promise.resolve().then(action).catch(failure); }
        function readOptions() {
            const selection = byId('cdsSource').selectedOptions[0];
            return effectiveOptions({providerNo: byId('providerNo').value, programId: byId('programId').value,
                facilityId: byId('facilityId').value, sourceNamespace: selection?.dataset.namespace,
                sourceProfile: selection?.dataset.profile, mergeExisting: byId('mergeExisting').checked,
                dryRun: byId('dryRun').checked, matchProviderNames: byId('matchProviderNames').checked,
                synthetic: byId('synthetic')?.checked || false, courseId: byId('courseId')?.value,
                timeshiftInDays: byId('timeshiftInDays')?.value || 0});
        }
        function progress(receipt, label = text.transfer) {
            byId('uploadProgress').max = receipt.expectedBytes || 1;
            byId('uploadProgress').value = receipt.receivedBytes || 0;
            byId('currentUpload').textContent = label + ': ' + receipt.fileName + ' — ' + receipt.receivedBytes + ' / ' + receipt.expectedBytes;
        }
        function rememberRun(id) {
            if (!UUID.test(id || '')) throw new Error('IMPORT_RUN_ID_INVALID');
            let saved = [];
            try { saved = JSON.parse(global.localStorage.getItem(storageKey) || '[]'); } catch (_) { /* Storage is a convenience, never authoritative. */ }
            const ids = boundedIds([...(Array.isArray(saved) ? saved : []), id]);
            try { global.localStorage.setItem(storageKey, JSON.stringify(ids)); } catch (_) { /* Run remains discoverable through its owner-scoped upload row. */ }
            renderRecent(ids);
        }
        function button(label, action) {
            const node = document.createElement('button'); node.type = 'button'; node.className = 'btn btn-sm btn-outline-secondary me-1';
            node.textContent = label; node.addEventListener('click', safe(action)); return node;
        }
        function renderRecent(ids) {
            byId('recentRuns').replaceChildren(...boundedIds(ids).map(id => button(id, () => openRun(id))));
        }
        function cell(row, value) { const td = document.createElement('td'); td.textContent = value == null ? '' : String(value); row.append(td); return td; }

        async function submitUpload(id, options) {
            const response = await request('cdsImportSubmit', {...options, uploadId: id}, {method: 'POST'});
            if (!response.accepted || !response.job || !UUID.test(response.job.id || '')) throw new Error('IMPORT_ACCEPTANCE_UNAVAILABLE');
            rememberRun(response.job.id); notice(text.accepted + ' ' + response.job.id);
            await openRun(response.job.id);
            return response.job.id;
        }

        async function transfer(file, resumeId) {
            if (!global.crypto?.subtle) throw new Error('UPLOAD_SECURE_CONTEXT_REQUIRED');
            if (!Number.isSafeInteger(file.size) || file.size < 1 || file.size > Number(root.dataset.maxFileBytes)
                    || !/\.(xml|zip)$/i.test(file.name)) throw new Error('UPLOAD_FILE_SIZE_OR_TYPE_REFUSED');
            let receipt;
            if (resumeId) {
                receipt = (await request('cdsImportStatus', {section: 'upload', uploadId: resumeId})).upload;
                byId('currentUpload').textContent = text.verify;
                await verifyPrefix(file, receipt, async afterId => (await request('cdsImportStatus',
                    {section: 'chunks', uploadId: receipt.id, afterId, limit: 100})).rows, sha256,
                receivedBytes => progress({...receipt, receivedBytes}, text.verify));
            } else {
                receipt = (await request('cdsUpload', {operation: 'begin', fileName: file.name, expectedBytes: file.size}, {method: 'POST'})).upload;
            }
            byId('uploadResumeId').value = receipt.id;
            progress(receipt);
            while (receipt.receivedBytes < file.size) {
                const offset = receipt.receivedBytes;
                const chunk = file.slice(offset, Math.min(file.size, offset + receipt.chunkBytes));
                const hash = await sha256(chunk);
                let last;
                for (let attempt = 0; attempt < 3; attempt++) {
                    try {
                        last = await request('cdsUpload', {operation: 'chunk', uploadId: receipt.id, offset,
                            bytes: chunk.size, sha256: hash}, {method: 'POST', body: chunk, headers: {'Content-Type': 'application/octet-stream'}});
                        break;
                    } catch (error) {
                        if (attempt === 2 || (error.status && ![408, 409, 429, 503].includes(error.status))) throw error;
                        // Retry only this exact offset/hash; the server verifies its atomic chunk receipt.
                        await new Promise(resolve => global.setTimeout(resolve, 500 * (2 ** attempt)));
                    }
                }
                const next = last.upload;
                if (!next || next.id !== receipt.id || next.expectedBytes !== file.size
                        || !Number.isSafeInteger(next.receivedBytes) || next.receivedBytes < offset + chunk.size
                        || next.receivedBytes > file.size || next.chunkBytes !== receipt.chunkBytes) {
                    throw new Error('UPLOAD_RECEIPT_CONFLICT');
                }
                receipt = next;
                if (receipt.receivedBytes > offset + chunk.size) {
                    await verifyPrefix(file, receipt, async afterId => (await request('cdsImportStatus',
                        {section: 'chunks', uploadId: receipt.id, afterId, limit: 100})).rows);
                }
                progress(receipt);
            }
            receipt = (await request('cdsUpload', {operation: 'finish', uploadId: receipt.id}, {method: 'POST'})).upload;
            progress(receipt);
            return receipt;
        }

        byId('cdsImportForm').addEventListener('submit', event => {
            event.preventDefault();
            if (busy) return;
            safe(async () => {
                const files = byId('importFile').files;
                if (!files.length) throw new Error(text.choose);
                if (files.length > 128) throw new Error(text.maxFiles);
                const options = readOptions();
                const resumeId = byId('uploadResumeId').value.trim();
                if (resumeId && files.length !== 1) throw new Error(text.selectOne);
                busy = true; byId('cdsOptions').disabled = true;
                let accepted = 0;
                try {
                    for (const file of files) {
                        const receipt = await transfer(file, resumeId);
                        await submitUpload(receipt.id, options); accepted++;
                        byId('uploadResumeId').value = '';
                    }
                    notice(accepted + ' ' + text.queued);
                    byId('importFile').value = '';
                    await loadUploads(true);
                } finally { busy = false; byId('cdsOptions').disabled = false; }
            })();
        });

        async function loadUploads(first) {
            if (first) uploadCursor = '';
            const data = await request('cdsImportStatus', {section: 'uploads', afterKey: uploadCursor, limit: 50});
            const rows = Array.isArray(data.rows) ? data.rows : [];
            if (rows.length > 50) throw new Error('IMPORT_PAGE_LIMIT_VIOLATED');
            const nodes = rows.map(upload => {
                const row = document.createElement('tr'); cell(row, upload.fileName + ' — ' + upload.id); cell(row, upload.state);
                cell(row, upload.receivedBytes + ' / ' + upload.expectedBytes);
                const actions = cell(row, '');
                const latestAttempt = latestUploadAttempt(upload);
                if (latestAttempt) actions.append(button(text.open, () => openRun(latestAttempt)));
                actions.append(button(text.resume, () => { byId('uploadResumeId').value = upload.id; progress(upload); }));
                if (['OPEN', 'COMPLETE'].includes(upload.state)) actions.append(button(text.abandon, async () => {
                    await request('cdsUpload', {operation: 'abandon', uploadId: upload.id}, {method: 'POST'}); await loadUploads(true);
                }));
                return row;
            });
            byId('uploadRows').replaceChildren(...nodes);
            if (rows.length) uploadCursor = rows[rows.length - 1].id;
            byId('nextUploads').disabled = rows.length < 50;
        }
        async function updateStatus(id) {
            const data = await request('cdsImportStatus', {runId: id});
            if (id !== activeRun) return;
            if (!data.job || data.job.id !== id) throw new Error('IMPORT_STATUS_RUN_CONFLICT');
            latestSummary = data.summary || {};
            const administrativeAlert = byId('cdsAdministrativeAlert');
            administrativeAlert.hidden = !latestSummary.administrativeAlert;
            administrativeAlert.textContent = latestSummary.administrativeAlert
                ? text.housekeeping + ' ' + String(latestSummary.administrativeAlert).slice(0, 96) : '';
            const workerAlert = byId('cdsWorkerAlert'); workerAlert.hidden = !latestSummary.workerAlert;
            workerAlert.textContent = latestSummary.workerAlert
                ? text.worker + ' ' + String(latestSummary.workerAlert).slice(0, 96) : '';
            const draftAlert = byId('cdsDraftAlert');
            draftAlert.hidden = !(Number.isSafeInteger(latestSummary.inactiveDraftCount) && latestSummary.inactiveDraftCount > 0);
            draftAlert.textContent = draftAlert.hidden ? '' : text.inactiveDrafts + latestSummary.inactiveDraftCount;
            const operational = [];
            if (latestSummary.maintenanceReleasePending === true) operational.push(text.release
                + String(latestSummary.maintenanceReleaseState || 'UNKNOWN').slice(0, 96));
            if (recoveryControls(latestSummary).fatalReview) operational.push(text.reviewFatal);
            if (latestSummary.supervisorAlert) operational.push(text.supervisor
                + ' ' + String(latestSummary.supervisorAlert).slice(0, 96));
            byId('cdsOperationalAlert').hidden = operational.length === 0;
            byId('cdsOperationalAlert').textContent = operational.join(' ');
            const presentation = statusPresentation(data.job, latestSummary);
            byId('runState').textContent = text.state + ': ' + presentation.state + ' — ' + id + ' — ' + (data.job.updatedAt || '');
            byId('runState').className = presentation.clean ? 'text-success' : 'text-body';
            byId('cancelRun').disabled = false; byId('resumeRun').disabled = false;
            byId('resumeRun').disabled = recoveryControls(latestSummary).resumeDisabled;
            const summary = JSON.stringify({job: data.job, summary: latestSummary}, null, 2);
            byId('runSummary').textContent = summary.length > 32000 ? summary.slice(0, 32000) + '\n' + text.page : summary;
            for (const [element, type] of [['eventReport', 'event'], ['errorReport', 'error'], ['receiptReport', 'receipt']]) {
                const link = byId(element);
                link.hidden = !data.job.reportsAvailable;
                link.href = root.dataset.context + '/form/cdsImportReport?' + new URLSearchParams({runId: id, type});
            }
            if (lastRunUpdated !== data.job.updatedAt) {
                lastRunUpdated = data.job.updatedAt;
                await Promise.all([loadPatients(), loadDiagnostics()]);
            }
            if (id !== activeRun) return;
            if (!mayStopPolling(latestSummary)) timer = global.setTimeout(() => poll(id), 2000);
        }
        async function poll(id) {
            try { await updateStatus(id); }
            catch (error) { if (id === activeRun) {
                byId('runState').textContent = text.unavailable;
                byId('cancelRun').disabled = true; byId('resumeRun').disabled = true; failure(error);
            } }
        }
        async function openRun(id) {
            if (!UUID.test(id || '')) throw new Error('IMPORT_RUN_ID_INVALID');
            global.clearTimeout(timer); activeRun = id; byId('runId').value = id;
            latestSummary = {}; byId('runState').textContent = text.unavailable; byId('runState').className = 'text-body';
            byId('runSummary').textContent = ''; byId('patientRows').replaceChildren(); byId('diagnosticRows').replaceChildren();
            byId('cdsReviewForm').hidden = true; byId('cancelRun').disabled = true; byId('resumeRun').disabled = true;
            byId('diagnosticEvidencePanel').hidden = true; byId('diagnosticEvidence').textContent = '';
            for (const name of ['eventReport', 'errorReport', 'receiptReport']) byId(name).hidden = true;
            patientCursor = ''; diagnosticCursor = 0;
            patientPageStart = ''; diagnosticPageStart = 0; lastRunUpdated = '';
            await updateStatus(id);
        }
        async function loadPatients(next = false) {
            const run = activeRun;
            const start = next ? patientCursor : patientPageStart;
            const data = await request('cdsImportStatus', {section: 'patients', runId: run, afterKey: start, limit: 50});
            if (run !== activeRun) return;
            if (!Array.isArray(data.rows) || data.rows.length > 50) throw new Error('IMPORT_PAGE_LIMIT_VIOLATED');
            patientPageStart = start;
            byId('patientRows').replaceChildren(...data.rows.map(patient => {
                const row = document.createElement('tr'); cell(row, (patient.sourcePatientId || patient.patientKey) + (patient.copyKey ? ' / ' + patient.copyKey : ''));
                cell(row, patient.state); cell(row, patient.held); cell(row, patient.expectedUnits); return row;
            }));
            patientCursor = data.nextCursor || patientCursor;
            byId('nextPatients').disabled = data.rows.length < 50;
        }
        async function loadDiagnostics(next = false) {
            const run = activeRun;
            const start = next ? diagnosticCursor : diagnosticPageStart;
            const data = await request('cdsImportStatus', {section: 'diagnostics', runId: run, afterId: start, limit: 50});
            if (run !== activeRun) return;
            if (!Array.isArray(data.rows) || data.rows.length > 50) throw new Error('IMPORT_PAGE_LIMIT_VIOLATED');
            diagnosticPageStart = start;
            byId('diagnosticRows').replaceChildren(...data.rows.map(item => {
                const row = document.createElement('tr'); cell(row, item.id); cell(row, item.code + ' / ' + item.stage);
                cell(row, item.summary + (item.sourceXPath ? ' — ' + item.sourceXPath : ''));
                const actions = cell(row, item.resolved ? 'RESOLVED' : item.severity);
                if (item.hasEvidence === true) actions.append(button(text.evidence, async () => {
                    const evidence = await request('cdsImportDiagnostic', {runId: run, diagnosticId: item.id});
                    if (run !== activeRun) return;
                    const rendered = JSON.stringify(evidence.evidence, null, 2);
                    if (rendered.length > 131072) throw new Error('IMPORT_EVIDENCE_CAPACITY_EXCEEDED');
                    byId('diagnosticEvidence').textContent = rendered;
                    byId('diagnosticEvidencePanel').hidden = false; byId('diagnosticEvidencePanel').open = true;
                }));
                if (item.blocking && !item.resolved) actions.append(button(text.review, () => {
                    if (!Number.isSafeInteger(latestSummary.reviewVersion) || latestSummary.reviewVersion < 0) throw new Error(text.reviewVersion);
                    const eligibility = reviewEligibility(item, latestSummary);
                    byId('reviewDiagnosticId').value = item.id; byId('reviewPatientKey').value = item.patientKey || '';
                    byId('reviewDiagnosticCode').value = item.code || '';
                    byId('reviewSourceContext').value = item.sourceXPath || '';
                    byId('reviewUnitKey').value = item.unitKey || ''; byId('reviewVersion').value = latestSummary.reviewVersion;
                    byId('reviewKind').value = eligibility.canApproveRevision ? 'APPROVE_UNWRITTEN_REVISION' : 'CORRECT_VALUE';
                    byId('reviewDeclineOption').disabled = !eligibility.canDecline;
                    byId('reviewRevisionOption').disabled = !eligibility.canApproveRevision;
                    byId('reviewPatientHelp').textContent = item.code === 'SOURCE_REVISION_REVIEW_REQUIRED'
                        && !eligibility.canApproveRevision ? text.revisionUnavailable : eligibility.canDecline ? '' : text.reviewPatient;
                    byId('reviewXPath').value = eligibility.initialXPath; byId('reviewValue').value = ''; byId('reviewReason').value = '';
                    updateReviewKind();
                    byId('cdsReviewForm').hidden = false; byId('reviewReason').focus();
                }));
                return row;
            }));
            diagnosticCursor = data.nextCursor || diagnosticCursor;
            byId('nextDiagnostics').disabled = data.rows.length < 50;
        }

        byId('refreshUploads').addEventListener('click', safe(() => loadUploads(true)));
        byId('nextUploads').addEventListener('click', safe(() => loadUploads(false)));
        byId('submitCompletedUpload').addEventListener('click', safe(() => submitUpload(byId('uploadResumeId').value.trim(), readOptions())));
        byId('openRun').addEventListener('click', safe(() => openRun(byId('runId').value.trim())));
        byId('nextPatients').addEventListener('click', safe(() => loadPatients(true)));
        byId('nextDiagnostics').addEventListener('click', safe(() => loadDiagnostics(true)));
        for (const [id, operation] of [['cancelRun', 'cancel'], ['resumeRun', 'resume']]) {
            byId(id).addEventListener('click', safe(async () => {
                await request('cdsImportControl', {operation, runId: activeRun}, {method: 'POST'}); await openRun(activeRun);
            }));
        }
        function updateReviewKind() {
            const correction = byId('reviewKind').value === 'CORRECT_VALUE';
            byId('reviewXPath').disabled = !correction; byId('reviewXPath').required = correction;
            byId('reviewValue').disabled = !correction;
        }
        byId('reviewKind').addEventListener('change', updateReviewKind);
        byId('cdsReviewForm').addEventListener('submit', event => {
            event.preventDefault();
            safe(async () => {
                if (byId('reviewKind').value === 'DECLINE_PATIENT'
                        && !reviewEligibility({patientKey: byId('reviewPatientKey').value}).canDecline) throw new Error(text.reviewPatient);
                if (byId('reviewKind').value === 'CORRECT_VALUE'
                        && !indexedSourceValuePath(byId('reviewXPath').value)) throw new Error(text.reviewPath);
                if (byId('reviewKind').value === 'APPROVE_UNWRITTEN_REVISION'
                        && !reviewEligibility({code: byId('reviewDiagnosticCode').value,
                            patientKey: byId('reviewPatientKey').value}, latestSummary).canApproveRevision) {
                    throw new Error(text.revisionUnavailable);
                }
                const correction = byId('reviewKind').value === 'CORRECT_VALUE';
                await request('cdsImportControl', {operation: 'review', runId: activeRun, kind: byId('reviewKind').value,
                    diagnosticId: byId('reviewDiagnosticId').value, patientKey: byId('reviewPatientKey').value,
                    unitKey: byId('reviewUnitKey').value, expectedVersion: byId('reviewVersion').value,
                    sourceXPath: correction ? byId('reviewXPath').value : byId('reviewSourceContext').value,
                    replacementValue: correction ? byId('reviewValue').value : '',
                    reason: byId('reviewReason').value}, {method: 'POST'});
                byId('cdsReviewForm').hidden = true; await openRun(activeRun);
            })();
        });
        try { renderRecent(JSON.parse(global.localStorage.getItem(storageKey) || '[]')); } catch (_) { renderRecent([]); }
        const requestedRun = new URL(global.location.href).searchParams.get('runId');
        // Delay until CSRFGuard has injected the real form token; initial read never bypasses the filter.
        let retries = 0;
        const initial = () => {
            if (document.querySelector('input[name="CSRF-TOKEN"]')) {
                safe(() => loadUploads(true))();
                if (requestedRun) safe(() => openRun(requestedRun))();
            } else if (++retries < 30) global.setTimeout(initial, 200);
            else notice('IMPORT_CSRF_TOKEN_UNAVAILABLE', true);
        };
        initial();
    });
})(typeof globalThis !== 'undefined' ? globalThis : this);
