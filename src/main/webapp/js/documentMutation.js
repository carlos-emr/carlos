/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function (root) {
    'use strict';
    if (root.CarlosDocumentMutation) return;

    // One immutable POST at a time. A timeout/network error is never evidence
    // that a clinical mutation was unaccepted, so only the explicit contract retries.
    function create(options) {
        var state = 'idle', active = true, timer = null, generation = 0;
        var payload, attempt = 0, deferredSuccess = null, dispatched = false;
        var setTimer = options.setTimeout || root.setTimeout.bind(root);
        var clearTimer = options.clearTimeout || root.clearTimeout.bind(root);
        var random = options.random || function () {
            var sample = Date.now() % 10000;
            try {
                var bytes = new Uint32Array(1);
                root.crypto.getRandomValues(bytes);
                return bytes[0] / 4294967296;
            } catch (error) {
                // Clock phase still spreads retries in older browser contexts.
                return sample / 10000;
            }
        };
        function notify(name, value) {
            try { if (options[name]) options[name](value); return true; }
            catch (error) { return false; }
        }
        function uncertain() {
            state = 'uncertain';
            if (timer !== null) { clearTimer(timer); timer = null; }
            // Broken UI callbacks must never unlock an unconfirmed mutation or
            // escape as an unhandled promise rejection. Each notification is best effort.
            notify('onState', state);
            notify('onUncertain');
        }
        function update(next) {
            state = next;
            if (notify('onState', next)) return true;
            uncertain();
            return false;
        }
        function idleNotification(name, data) {
            if (update('idle') && !notify(name, data)) uncertain();
        }
        function complete(data) {
            if (!active) { deferredSuccess = data; return; }
            deferredSuccess = null;
            idleNotification('onSuccess', data);
        }
        function dispatch(current) {
            if (current !== generation || !active) return;
            dispatched = false;
            if (!update('pending')) return;
            Promise.resolve().then(function () {
                if (current !== generation || state !== 'pending') return;
                if (!active) { idleNotification('onCancelled'); return; }
                // A failed local guard proves no transport occurred. In particular,
                // an old session's queued intent must not acquire a new CSRF token.
                var allowed = true;
                try { if (options.beforeSend) allowed = options.beforeSend(payload) === true; }
                catch (error) { allowed = false; }
                if (current !== generation || state !== 'pending') return;
                if (!active) { idleNotification('onCancelled'); return; }
                if (!allowed) {
                    idleNotification('onRejected', {success: false, accepted: false, retryable: false, local: true});
                    return;
                }
                dispatched = true;
                return options.send(payload);
            }).then(function (result) {
                if (current !== generation || state !== 'pending') return;
                var status = result && result.status, data = result && result.data;
                if (status >= 200 && status < 300 && data && data.success === true && data.accepted === true
                        && options.isSuccess(data)) {
                    complete(data);
                } else if (status === 503 && data && data.success === false && data.accepted === false && data.retryable === true) {
                    if (!active) { idleNotification('onCancelled'); return; }
                    if (!update('waiting')) return;
                    if (!notify('onWaiting', data)) { uncertain(); return; }
                    if (current !== generation || state !== 'waiting' || !active) return;
                    var advertised = Number(result.retryAfter);
                    var minimum = Number.isFinite(advertised) && advertised > 0 ? Math.min(8000, advertised * 1000) : 1000;
                    var delay = Math.min(8000, Math.max(minimum, 1000 * Math.pow(2, Math.min(attempt++, 3))))
                            * (1 + random() * .25);
                    timer = setTimer(function () { timer = null; dispatch(current); }, delay);
                } else if (status >= 400 && data && data.success === false && data.accepted === false && data.retryable === false) {
                    idleNotification('onRejected', data);
                } else {
                    uncertain();
                }
            }).catch(function () {
                if (current === generation && state !== 'uncertain') uncertain();
            });
        }
        function cancelWaiting() {
            if (state !== 'waiting') return false;
            clearTimer(timer);
            timer = null;
            generation++;
            idleNotification('onCancelled');
            return true;
        }
        return {
            start: function (body) {
                if (state !== 'idle' || !active || typeof body !== 'string' || !body) return false;
                payload = body;
                attempt = 0;
                dispatch(++generation);
                return true;
            },
            isLocked: function () { return state !== 'idle'; },
            cancelWaiting: cancelWaiting,
            hide: function () {
                active = false;
                if (state === 'pending' && !dispatched) {
                    generation++;
                    idleNotification('onCancelled');
                } else cancelWaiting();
            },
            show: function () {
                active = true;
                if (deferredSuccess !== null) complete(deferredSuccess);
            }
        };
    }
    root.CarlosDocumentMutation = {create: create};
})(window);
