/** Propagates the opaque prescription workspace context across legacy Rx requests. */
(function () {
    'use strict';

    var contextMeta = document.querySelector('meta[name="rx-context-id"]');
    var pathMeta = document.querySelector('meta[name="rx-context-path"]');
    var demographicMeta = document.querySelector('meta[name="rx-demographic-no"]');
    var appointmentMeta = document.querySelector('meta[name="rx-appointment-no"]');
    var programMeta = document.querySelector('meta[name="rx-program-id"]');
    var ownerMeta = document.querySelector('meta[name="rx-context-owner"]');
    var csrfMeta = document.querySelector('meta[name="rx-csrf-token"]');
    var contextId = contextMeta ? contextMeta.getAttribute('content') : '';
    var contextPath = pathMeta ? pathMeta.getAttribute('content') : '';
    if (!contextId) return;
    if (window.__carlosRxContextInterceptorLoaded) return;
    window.__carlosRxContextInterceptorLoaded = true;
    if (window.RxContext && window.RxContext.id === contextId) return;

    function rxUrl(value) {
        if (!value || /^(javascript:|mailto:|tel:|#)/i.test(value)) return null;
        try {
            var parsed = new URL(value, document.baseURI);
            var rxRoot = contextPath + '/rx/';
            if (parsed.origin !== window.location.origin || parsed.pathname.indexOf(rxRoot) !== 0) return null;
            if (parsed.pathname === contextPath + '/rx/choosePatient') return null;
            return parsed;
        } catch (e) {
            return null;
        }
    }

    function addToUrl(value) {
        var parsed = rxUrl(value);
        if (!parsed) return value;
        parsed.searchParams.set('rxContextId', contextId);
        return parsed.href;
    }

    function addToParameters(parameters) {
        if (!parameters) return 'rxContextId=' + encodeURIComponent(contextId);
        if (typeof parameters === 'string') {
            var search = new URLSearchParams(parameters);
            search.set('rxContextId', contextId);
            return search.toString();
        }
        if (typeof FormData !== 'undefined' && parameters instanceof FormData) {
            parameters.set('rxContextId', contextId);
            return parameters;
        }
        if (typeof URLSearchParams !== 'undefined' && parameters instanceof URLSearchParams) {
            parameters.set('rxContextId', contextId);
            return parameters;
        }
        parameters.rxContextId = contextId;
        return parameters;
    }

    function addToForm(form) {
        if (!form) return;
        var action = form.getAttribute('action') || window.location.href;
        if (!rxUrl(action)) return;
        var input = form.querySelector('input[name="rxContextId"]');
        if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'rxContextId';
            form.appendChild(input);
        }
        input.value = contextId;
    }

    function rewriteElement(element) {
        if (!element || !element.getAttribute) return;
        if (element.tagName === 'FORM') {
            addToForm(element);
        } else if (element.tagName === 'A' && element.hasAttribute('href')) {
            var href = element.getAttribute('href');
            var contextualHref = addToUrl(href);
            if (contextualHref !== href) element.setAttribute('href', contextualHref);
        } else if ((element.tagName === 'IFRAME' || element.tagName === 'FRAME')
                && element.hasAttribute('src')) {
            var src = element.getAttribute('src');
            var contextualSrc = addToUrl(src);
            if (contextualSrc !== src) element.setAttribute('src', contextualSrc);
        }
        var descendants = element.querySelectorAll
                ? element.querySelectorAll('form, a[href], iframe[src], frame[src]') : [];
        for (var i = 0; i < descendants.length; i++) rewriteElement(descendants[i]);
    }

    window.RxContext = Object.freeze({
        id: contextId,
        addToUrl: addToUrl,
        addToParameters: addToParameters
    });

    function metaValue(meta) {
        return meta ? (meta.getAttribute('content') || '').trim() : '';
    }

    function freshWorkspaceUrl() {
        var url = new URL(contextPath + '/rx/choosePatient', window.location.origin);
        url.searchParams.set('demographicNo', metaValue(demographicMeta));
        if (metaValue(appointmentMeta)) {
            url.searchParams.set('appointmentNo', metaValue(appointmentMeta));
        }
        if (metaValue(programMeta) && metaValue(programMeta) !== '0') {
            url.searchParams.set('programId', metaValue(programMeta));
        }
        url.searchParams.set('rxDuplicate', '1');
        return url.href;
    }

    function showDuplicateNotice() {
        var current = new URL(window.location.href);
        if (current.searchParams.get('rxDuplicate') !== '1') return;
        current.searchParams.delete('rxDuplicate');
        window.history.replaceState(window.history.state, '', current.href);
        window.alert('This duplicated prescription tab was opened as a new, independent draft.');
    }

    function ownerId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
    }

    function markOwnerCheckPending() {
        if (!document.documentElement) return;
        document.documentElement.setAttribute('data-rx-owner-check', 'pending');
        var style = document.createElement('style');
        style.setAttribute('data-rx-owner-style', 'true');
        style.textContent = 'html[data-rx-owner-check="pending"] body{pointer-events:none;opacity:.65}';
        document.documentElement.appendChild(style);
    }

    function finishOwnerCheck() {
        if (document.documentElement) document.documentElement.removeAttribute('data-rx-owner-check');
    }

    function reopenDuplicate() {
        window.location.replace(freshWorkspaceUrl());
    }

    function claimWithLocalStorage(id) {
        var key = 'carlos.rx.owner.' + contextId;
        var leaseDuration = 5000;
        var existing = null;
        try {
            existing = JSON.parse(window.localStorage.getItem(key));
        } catch (e) {
            existing = null;
        }
        if (existing && existing.id !== id && Date.now() - existing.updatedAt < leaseDuration) {
            reopenDuplicate();
            return;
        }

        function refreshLease() {
            try {
                window.localStorage.setItem(key, JSON.stringify({id: id, updatedAt: Date.now()}));
                return true;
            } catch (e) {
                return false;
            }
        }
        if (!refreshLease()) {
            finishOwnerCheck();
            return;
        }
        var interval = window.setInterval(refreshLease, 2000);
        window.addEventListener('pagehide', function () {
            window.clearInterval(interval);
            try {
                var lease = JSON.parse(window.localStorage.getItem(key));
                if (lease && lease.id === id) window.localStorage.removeItem(key);
            } catch (e) {
                try {
                    window.localStorage.removeItem(key);
                } catch (ignored) {
                    // Storage is unavailable; the short lease will expire naturally if it was written.
                }
            }
        }, {once: true});
        finishOwnerCheck();
    }

    function claimWorkspaceOwnership() {
        if (metaValue(ownerMeta) !== 'true' || window.top !== window) return;
        showDuplicateNotice();
        markOwnerCheckPending();
        var id = ownerId();

        if (typeof window.BroadcastChannel !== 'function') {
            claimWithLocalStorage(id);
            return;
        }

        var probeId = ownerId();
        var duplicate = false;
        var channel;
        try {
            channel = new window.BroadcastChannel('carlos-rx-owner-' + contextId);
        } catch (e) {
            claimWithLocalStorage(id);
            return;
        }
        channel.onmessage = function (event) {
            var message = event.data || {};
            if (message.type === 'probe') {
                channel.postMessage({type: 'alive', probeId: message.probeId});
            } else if (message.type === 'alive' && message.probeId === probeId) {
                duplicate = true;
            }
        };
        channel.postMessage({type: 'probe', probeId: probeId});
        window.setTimeout(function () {
            if (duplicate) {
                channel.close();
                reopenDuplicate();
                return;
            }
            finishOwnerCheck();
        }, 200);
        window.addEventListener('pagehide', function () { channel.close(); }, {once: true});
    }

    claimWorkspaceOwnership();

    function csrfToken() {
        var metaToken = metaValue(csrfMeta);
        if (metaToken) return metaToken;
        var input = document.querySelector('input[name="CSRF-TOKEN"]');
        if (input && input.value) return input.value;
        try {
            return window.CarlosAjax && typeof window.CarlosAjax.getCsrfToken === 'function'
                    ? window.CarlosAjax.getCsrfToken() : '';
        } catch (e) {
            return '';
        }
    }

    function heartbeatWorkspace() {
        if (!window.fetch) return;
        window.fetch(addToUrl(contextPath + '/rx/workspaceHeartbeat'), {
            method: 'GET',
            credentials: 'same-origin',
            cache: 'no-store',
            headers: {'X-Requested-With': 'XMLHttpRequest'}
        }).catch(function () {
            // Ordinary Rx requests also refresh the lease; a missed heartbeat is recoverable.
        });
    }

    function markWorkspaceClosing() {
        var token = csrfToken();
        if (!token || !window.navigator || typeof window.navigator.sendBeacon !== 'function') return;
        var body = 'CSRF-TOKEN=' + encodeURIComponent(token);
        window.navigator.sendBeacon(
                addToUrl(contextPath + '/rx/workspaceClose'),
                new Blob([body], {type: 'application/x-www-form-urlencoded'}));
    }

    function startWorkspaceLease() {
        if (window.top !== window) return;
        heartbeatWorkspace();
        var heartbeatTimer = window.setInterval(heartbeatWorkspace, 60000);
        window.addEventListener('pagehide', function () {
            window.clearInterval(heartbeatTimer);
            markWorkspaceClosing();
        }, {once: true});
    }

    if (window.fetch) {
        var originalFetch = window.fetch;
        window.fetch = function (input, init) {
            var value = typeof input === 'string' || input instanceof URL ? String(input) : input.url;
            if (!rxUrl(value)) return originalFetch.call(this, input, init);
            var options = Object.assign({}, init || {});
            var headers = new Headers(options.headers || (input instanceof Request ? input.headers : undefined));
            headers.set('X-Rx-Context', contextId);
            headers.set('X-Requested-With', 'XMLHttpRequest');
            options.headers = headers;
            return input instanceof Request
                    ? originalFetch.call(this, new Request(input, options))
                    : originalFetch.call(this, input, options);
        };
    }

    if (window.XMLHttpRequest) {
        var originalOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (method, url) {
            var args = Array.prototype.slice.call(arguments);
            args[1] = addToUrl(url);
            return originalOpen.apply(this, args);
        };
    }

    if (window.CarlosAjax) {
        ['request', 'updater'].forEach(function (method) {
            if (typeof CarlosAjax[method] !== 'function') return;
            var original = CarlosAjax[method];
            CarlosAjax[method] = function () {
                var args = Array.prototype.slice.call(arguments);
                var optionsIndex = method === 'updater' ? 2 : 1;
                args[optionsIndex] = args[optionsIndex] || {};
                args[optionsIndex].parameters = addToParameters(args[optionsIndex].parameters);
                return original.apply(CarlosAjax, args);
            };
        });
    }

    if (window.Ajax && Ajax.Request && Ajax.Request.prototype.initialize) {
        var originalRequestInitialize = Ajax.Request.prototype.initialize;
        Ajax.Request.prototype.initialize = function (url, options) {
            options = options || {};
            options.parameters = addToParameters(options.parameters);
            return originalRequestInitialize.call(this, addToUrl(url), options);
        };
    }

    if (window.jQuery) {
        jQuery.ajaxPrefilter(function (options) {
            if (!rxUrl(options.url)) return;
            options.headers = options.headers || {};
            options.headers['X-Rx-Context'] = contextId;
            options.headers['X-Requested-With'] = 'XMLHttpRequest';
        });
    }

    var originalWindowOpen = window.open;
    window.open = function (url) {
        var args = Array.prototype.slice.call(arguments);
        args[0] = addToUrl(url);
        return originalWindowOpen.apply(window, args);
    };

    var originalSubmit = HTMLFormElement.prototype.submit;
    HTMLFormElement.prototype.submit = function () {
        addToForm(this);
        return originalSubmit.call(this);
    };
    document.addEventListener('submit', function (event) { addToForm(event.target); }, true);

    function initialize() {
        rewriteElement(document.documentElement);
        new MutationObserver(function (mutations) {
            mutations.forEach(function (mutation) {
                if (mutation.type === 'attributes') rewriteElement(mutation.target);
                for (var i = 0; i < mutation.addedNodes.length; i++) {
                    rewriteElement(mutation.addedNodes[i]);
                }
            });
        }).observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['action', 'href', 'src'],
            childList: true,
            subtree: true
        });
    }

    if (document.documentElement) {
        initialize();
    } else {
        document.addEventListener('DOMContentLoaded', initialize);
    }

    startWorkspaceLease();
})();
