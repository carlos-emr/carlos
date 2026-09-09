/** Propagates the opaque prescription workspace context across legacy Rx requests. */
(function () {
    'use strict';

    var contextMeta = document.querySelector('meta[name="rx-context-id"]');
    var pathMeta = document.querySelector('meta[name="rx-context-path"]');
    var contextId = contextMeta ? contextMeta.getAttribute('content') : '';
    var contextPath = pathMeta ? pathMeta.getAttribute('content') : '';
    if (!contextId) return;
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
})();
