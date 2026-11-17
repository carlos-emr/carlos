/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
(function (root, factory) {
    'use strict';
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.CarlosDocumentPreviewRetry = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    function replaceApproval(parameters, token) {
        const values = new URLSearchParams(parameters);
        values.delete('renderApproval');
        if (typeof token === 'string' && token.length > 0) values.set('renderApproval', token);
        return values.toString();
    }

    /** A newly requested attachment must not expose the previous document while it waits. */
    function clearDisplay(document, revokeObjectURL, previousUrl) {
        const frame = document.getElementById('pdfObject');
        frame.classList.add('d-none');
        frame.removeAttribute('src');
        const advisory = document.getElementById('preview-advisory');
        advisory.classList.add('d-none');
        advisory.textContent = '';
        document.getElementById('preview-filler').classList.remove('d-none');
        if (previousUrl) revokeObjectURL(previousUrl);
        return null;
    }

    /**
     * Owns one preview request or retry timer. Only the server's explicit pre-render capacity
     * response is retryable: network errors and other POST failures may have uncertain outcomes.
     * An active attachment view supplies isCurrent so removing/hiding it also stops polling.
     */
    function create(options) {
        const schedule = options.schedule || setTimeout;
        const unschedule = options.unschedule || clearTimeout;
        const random = options.random || Math.random;
        let active = null;

        function cancel() {
            const job = active;
            active = null; // Invalidate before abort: jQuery can synchronously call error on abort.
            if (!job) return;
            if (job.timer !== null) unschedule(job.timer);
            if (job.request && typeof job.request.abort === 'function') job.request.abort();
        }

        function current(job) {
            if (active !== job) return false;
            if (options.isCurrent && !options.isCurrent()) {
                cancel();
                return false;
            }
            return true;
        }

        function start(parameters, handlers) {
            cancel();
            if (options.beforeStart) options.beforeStart();
            const job = { parameters, handlers, timer: null, request: null };
            active = job;

            function attempt() {
                job.timer = null;
                if (!current(job)) return;
                let completed = false;
                const request = options.send(job.parameters, {
                    success: function (data) {
                        completed = true;
                        if (!current(job)) return;
                        active = null;
                        job.request = null;
                        handlers.success(data, job.parameters);
                    },
                    error: function (xhr, status, error) {
                        completed = true;
                        if (!current(job)) return;
                        job.request = null;
                        const data = xhr && xhr.responseJSON;
                        const seconds = data && data.retryAfterSeconds;
                        const method = new URLSearchParams(job.parameters).get('method');
                        if (method === 'renderEFormPDF' && xhr.status === 503 && data
                                && data.errorCode === 'eform_render_busy' && data.retryable === true
                                && Number.isInteger(seconds) && seconds >= 1 && seconds <= 30) {
                            // A null token means that the original approval expired while waiting.
                            // Restart unapproved, allowing the normal exact-omission prompt to run.
                            job.parameters = replaceApproval(job.parameters, data.renderApproval);
                            const delay = seconds * 1000 + Math.floor(random() * 500);
                            if (handlers.waiting) handlers.waiting();
                            if (current(job)) job.timer = schedule(attempt, delay);
                            return;
                        }
                        active = null;
                        handlers.error(xhr, status, error);
                    }
                });
                // Also works with synchronous test transports and immediate local failures.
                if (!completed && current(job)) job.request = request;
            }

            attempt();
        }

        return { start, cancel };
    }

    return { create, replaceApproval, clearDisplay };
}));
