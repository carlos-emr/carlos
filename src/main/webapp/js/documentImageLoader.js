/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/**
 * Bounded, capacity-aware GETs for legacy document previews. Initial images use
 * data-document-image-src: a native src would start work before admission here.
 * Never retry document mutations. Blob ownership follows connected images/canvases
 * so the split viewer can clone and rotate a decoded preview without fetching it.
 */
(function () {
    'use strict';
    if (window.CarlosDocumentImages) return; // AJAX inbox fragments may include us again.
    const sourceAttribute = 'data-document-image-src';
    const blobAttribute = 'data-document-image-blob';
    const records = new Map();
    const blobs = new Map();
    let active = 0;
    let suspended = false;
    let scheduled = false;

    function validSource(value) {
        const url = new URL(value, window.location.href);
        if (url.origin !== window.location.origin || !/\/documentManager\/ManageDocument$/.test(url.pathname)
                || !['viewDocPage', 'showPage'].includes(url.searchParams.get('method'))
                || !/^\d+$/.test(url.searchParams.get('doc_no') || '')) {
            throw new Error('Invalid document image request');
        }
        return url.href;
    }

    function wanted(record, task) {
        return !suspended && record.image.isConnected && records.get(record.image) === record
            && record.task === task && record.url === task.url;
    }

    function visible(record) {
        const rect = record.image.getBoundingClientRect();
        return rect.bottom >= 0 && rect.top <= window.innerHeight
            && rect.right >= 0 && rect.left <= window.innerWidth
            && record.image.getClientRects().length > 0;
    }

    function status(record, message, failed) {
        if (!record.image.isConnected) return;
        if (!record.status) {
            record.status = document.createElement('span');
            record.status.className = 'document-image-status';
            record.status.setAttribute('role', 'status');
            record.status.style.display = 'block';
        }
        // Split moves its page image into a wrapper after DOMContentLoaded.
        if (record.status.parentNode !== record.image.parentNode) {
            record.image.parentNode.appendChild(record.status);
        }
        if (record.status.textContent !== message) record.status.textContent = message;
        record.image.setAttribute('aria-busy', message && !failed ? 'true' : 'false');
        record.image.setAttribute('data-document-image-state', failed ? 'failed' : message ? 'waiting' : 'loaded');
    }

    function collectBlobs() {
        const retained = new Set(Array.from(document.querySelectorAll('[' + blobAttribute + ']'),
            element => element.getAttribute(blobAttribute)));
        blobs.forEach((pending, url) => {
            if (!pending && !retained.has(url)) {
                URL.revokeObjectURL(url);
                blobs.delete(url);
            }
        });
    }

    function clearImage(record) {
        record.image.removeAttribute('src');
        record.image.removeAttribute(blobAttribute);
        record.complete = false;
        record.failed = false;
        status(record, 'Waiting to load document image.', false);
        collectBlobs();
    }

    function load(image, value) {
        if (!image) return;
        let record = records.get(image);
        if (!record) {
            record = {image, url: null, task: null, complete: false, failed: false};
            records.set(image, record);
        }
        let url;
        try { url = validSource(value); }
        catch (error) {
            record.url = null;
            clearImage(record);
            record.failed = true;
            status(record, 'Document image could not be loaded. Reload the viewer to try again.', true);
            cancelWait(record);
            return;
        }
        if (record.url === url && !record.failed) return;
        record.url = url;
        image.setAttribute(sourceAttribute, url);
        clearImage(record);
        cancelWait(record);
        schedule();
    }

    function cancelWait(record) {
        if (record.task && record.task.cancelWait) record.task.cancelWait();
    }

    function capacityDelay(response, attempt) {
        const seconds = Number(response.headers.get('Retry-After'));
        const minimum = Number.isFinite(seconds) && seconds > 0 ? Math.min(seconds * 1000, 8000) : 1000;
        const base = Math.min(8000, Math.max(minimum, 1000 * Math.pow(2, attempt)));
        return Math.round(base * (1 + Math.random() * .25));
    }

    function wait(record, task, delay) {
        return new Promise(resolve => {
            const finish = () => {
                window.clearTimeout(timer);
                task.cancelWait = null;
                resolve();
            };
            const timer = window.setTimeout(finish, delay);
            task.cancelWait = finish;
        });
    }

    function decode(task, url) {
        return new Promise((resolve, reject) => {
            // Decode off-DOM. An old image event can never complete a replacement
            // request or display another patient's/page's pixels.
            const preview = new Image();
            const finish = error => {
                preview.onload = null;
                preview.onerror = null;
                task.cancelDecode = null;
                if (error) reject(error); else resolve();
            };
            preview.onload = () => finish();
            preview.onerror = () => finish(new Error('Document image decoding failed'));
            task.cancelDecode = () => { finish(new Error('Document image decoding cancelled')); preview.removeAttribute('src'); };
            preview.src = url;
        });
    }

    async function run(record, task) {
        let objectUrl;
        try {
            let attempt = 0;
            while (wanted(record, task)) {
                // Accepted server work owns its slot through response body and
                // decode, even after navigation. Aborting fetch cannot stop PDFBox.
                const response = await fetch(task.url, {credentials: 'same-origin', redirect: 'error'});
                if (response.status === 503) {
                    await response.text();
                    if (!wanted(record, task) || !visible(record)) return;
                    status(record, 'Waiting for document capacity. This image will load automatically.', false);
                    await wait(record, task, capacityDelay(response, attempt));
                    attempt = Math.min(attempt + 1, 3);
                    if (!wanted(record, task) || !visible(record)) return;
                    continue;
                }
                if (!response.ok || !/^image\//i.test(response.headers.get('Content-Type') || '')) {
                    await response.text();
                    throw new Error('Document image response failed');
                }
                const blob = await response.blob();
                if (!wanted(record, task)) return;
                objectUrl = URL.createObjectURL(blob);
                blobs.set(objectUrl, true);
                await decode(task, objectUrl);
                if (!wanted(record, task)) return;
                record.image.setAttribute(blobAttribute, objectUrl);
                record.image.src = objectUrl;
                record.complete = true;
                status(record, '', false);
                return;
            }
        } catch (error) {
            if (wanted(record, task)) {
                record.failed = true;
                status(record, 'Document image could not be loaded. Reload the viewer to try again.', true);
            }
        } finally {
            if (objectUrl) blobs.set(objectUrl, false);
            record.task = null;
            active--;
            collectBlobs();
            schedule();
        }
    }

    function pump() {
        scheduled = false;
        document.querySelectorAll('img[' + sourceAttribute + ']').forEach(image => {
            if (!records.has(image)) load(image, image.getAttribute(sourceAttribute));
        });
        records.forEach((record, image) => {
            if (!image.isConnected) {
                cancelWait(record);
                if (record.task && record.task.cancelDecode) record.task.cancelDecode();
                if (record.status) record.status.remove();
                records.delete(image);
            } else if (record.task && (!wanted(record, record.task) || !visible(record))) {
                cancelWait(record);
            }
        });
        collectBlobs();
        if (suspended) return;
        for (const record of records.values()) {
            if (active >= 4) break;
            if (record.task || record.complete || record.failed || !record.url || !visible(record)) continue;
            const task = {url: record.url};
            record.task = task;
            active++;
            void run(record, task);
        }
    }

    function schedule() {
        if (scheduled) return;
        scheduled = true;
        window.requestAnimationFrame(pump);
    }

    window.CarlosDocumentImages = {
        load,
        source: image => image.getAttribute(sourceAttribute),
        // A cloned split preview must not auto-fetch its original request again.
        retain: (from, to) => {
            to.removeAttribute(sourceAttribute);
            const url = from.getAttribute(blobAttribute);
            if (url) to.setAttribute(blobAttribute, url);
        }
    };
    const observer = new MutationObserver(schedule);
    observer.observe(document.documentElement, {childList: true, subtree: true});
    window.addEventListener('scroll', schedule, true); // includes the split viewer's inner scrollers
    window.addEventListener('resize', schedule);
    window.addEventListener('pagehide', event => {
        suspended = true;
        records.forEach(record => {
            cancelWait(record);
            if (record.task && record.task.cancelDecode) record.task.cancelDecode();
        });
        if (!event.persisted) {
            blobs.forEach((pending, url) => URL.revokeObjectURL(url));
            blobs.clear();
        }
    });
    window.addEventListener('pageshow', () => { suspended = false; schedule(); });
    document.addEventListener('DOMContentLoaded', schedule);
    schedule();
})();
