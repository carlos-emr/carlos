/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * CARLOS EMR - Ajax Utility
 *
 * Wraps fetch() / XMLHttpRequest in an API that preserves the exact
 * Prototype.js callback execution order. This is critical — changing
 * callback timing will break dependent code.
 *
 * Prototype.js Callback Contract (MUST preserve):
 *   Ajax.Request:  onSuccess/onFailure -> onComplete
 *   Ajax.Updater:  onSuccess -> updateContent -> onComplete
 *
 * See docs/carlos-ajax.md for full API reference.
 *
 * @since 2026-03-15
 */

/* global carlosExtractAndExecScripts */

var CarlosAjax = (function () {
    'use strict';

    /**
     * Get the CSRF token from the DOM (injected by CSRFGuard).
     * @returns {string} The CSRF token value, or empty string if not found
     */
    function getCsrfToken() {
        var el = document.querySelector('input[name="CSRF-TOKEN"]');
        return el ? el.value : '';
    }

    /**
     * Methods that require CSRF token injection.
     */
    var MUTATING_METHODS = ['POST', 'PUT', 'DELETE', 'PATCH'];

    /**
     * Maps Prototype.js insertion position names to insertAdjacentHTML positions.
     */
    var POS_MAP = {
        'bottom': 'beforeend',
        'top': 'afterbegin',
        'before': 'beforebegin',
        'after': 'afterend'
    };

    /**
     * Build standard headers for all AJAX requests.
     * @param {string} method - HTTP method
     * @param {Object} extraHeaders - Additional headers from caller
     * @returns {Object} Merged headers object
     */
    function buildHeaders(method, extraHeaders) {
        // Apply caller headers first, then security headers on top
        // so that callers cannot override security-critical headers
        var headers = {};

        if (extraHeaders) {
            for (var key in extraHeaders) {
                if (extraHeaders.hasOwnProperty(key)) {
                    headers[key] = extraHeaders[key];
                }
            }
        }

        // Do NOT set X-Requested-With or CSRF-TOKEN headers here.
        // CSRFGuard 4.5's XHR onsend interceptor automatically injects both
        // headers into every XMLHttpRequest.send() call. Setting them here
        // causes duplicate setRequestHeader() calls, which per the XHR spec
        // APPENDS values with a comma (e.g. "token, token") instead of
        // replacing — CSRFGuard then rejects the request because the combined
        // header value doesn't match the master token.
        //
        // CSRF tokens for the POST body are still injected in request() below.

        return headers;
    }

    /**
     * Create a transport-like object matching Prototype's XHR shape.
     * Callbacks receive this instead of the raw Response.
     * @param {number} status - HTTP status code
     * @param {string} responseText - Response body text
     * @param {string} [responseURL] - Final URL after redirects
     * @returns {Object} Transport object with .status, .responseText, .responseURL
     */
    function makeTransport(status, responseText, responseURL, statusText) {
        var transport = {
            status: status,
            statusText: statusText || '',
            responseText: responseText,
            responseURL: responseURL || '',
            responseJSON: null
        };
        if (responseText) {
            try { transport.responseJSON = JSON.parse(responseText); } catch (e) { /* not JSON */ }
        }
        return transport;
    }

    /**
     * Check if a response was redirected to the CSRF error page.
     * CSRFGuard returns 302 -> errorpage.jsp instead of 403.
     * @param {Response|Object} response - fetch Response or transport with responseURL
     * @returns {boolean} True if this is a CSRF rejection redirect
     */
    function isCsrfRedirect(response) {
        if (response.redirected && response.url && response.url.indexOf('errorpage.jsp') !== -1) {
            return true;
        }
        if (response.responseURL && response.responseURL.indexOf('errorpage.jsp') !== -1) {
            return true;
        }
        return false;
    }

    /**
     * Determine if a status code is a success (2xx).
     * @param {number} status - HTTP status code
     * @returns {boolean}
     */
    function isSuccess(status) {
        return status >= 200 && status < 300;
    }

    /**
     * Encode parameters for POST body.
     * Accepts string (pass-through), URLSearchParams, FormData, or plain object.
     * @param {*} params - Parameters to encode
     * @returns {string} URL-encoded string
     */
    function encodeParams(params) {
        if (typeof params === 'string') return params;
        if (params instanceof URLSearchParams) return params.toString();
        if (params instanceof FormData) return new URLSearchParams(params).toString();
        if (params && typeof params === 'object') return new URLSearchParams(params).toString();
        return '';
    }

    /**
     * Run an AJAX request (replaces Prototype's Ajax.Request).
     *
     * Callback order: onSuccess/onFailure -> onComplete (same as Prototype)
     *
     * Options:
     *   method:       HTTP method (default: 'POST' — matches Prototype default)
     *   parameters:   Object, string, URLSearchParams, or FormData (for POST body)
     *   postBody:     Raw string body (takes precedence over parameters)
     *   contentType:  Content-Type header (default: 'application/x-www-form-urlencoded')
     *   requestHeaders: Additional headers object
     *   synchronous:  If true, uses synchronous XMLHttpRequest (default: false)
     *   evalScripts:  Ignored by request() — Prototype's Ajax.Request did not support this.
     *                  Use updater() for evalScripts, or call .update() in onSuccess callback.
     *   onSuccess:    function(transport) — called on 2xx
     *   onFailure:    function(transport) — called on non-2xx or network error
     *   onComplete:   function(transport) — always called last
     *
     * @param {string} url - Request URL
     * @param {Object} [options] - Request options
     */
    function request(url, options) {
        options = options || {};
        var method = (options.method || 'POST').toUpperCase();
        var contentType = options.contentType || 'application/x-www-form-urlencoded';
        var isSynchronous = options.synchronous === true ||
                            options.asynchronous === false;

        // Build body — inject CSRF token as a form parameter for POST requests.
        // CSRFGuard 4.5 validates tokens from both request parameters and headers.
        // The header is injected automatically by CSRFGuard's XHR onsend interceptor.
        // The body parameter provides a second source for token validation.
        var body = null;
        if (method !== 'GET' && method !== 'HEAD') {
            if (options.postBody != null) {
                body = options.postBody;
            } else if (options.parameters != null) {
                body = encodeParams(options.parameters);
            }
            // Append CSRF token to body for form-encoded requests only.
            // Non-form bodies (e.g. JSON) receive the token via the CSRF-TOKEN header in buildHeaders().
            var csrfToken = getCsrfToken();
            if (csrfToken && contentType.indexOf('application/x-www-form-urlencoded') !== -1) {
                if (body != null && typeof body === 'string' && !/(^|[&])CSRF-TOKEN=/.test(body)) {
                    body += (body ? '&' : '') + 'CSRF-TOKEN=' + encodeURIComponent(csrfToken);
                } else if (body == null) {
                    body = 'CSRF-TOKEN=' + encodeURIComponent(csrfToken);
                }
            }
        } else if (options.parameters) {
            // Append parameters to URL for GET requests
            var paramStr = encodeParams(options.parameters);
            if (paramStr) {
                url += (url.indexOf('?') === -1 ? '?' : '&') + paramStr;
            }
        }

        // Merge headers
        var allHeaders = buildHeaders(method, options.requestHeaders);
        if (method !== 'GET' && method !== 'HEAD') {
            allHeaders['Content-Type'] = contentType;
        }

        if (isSynchronous) {
            return requestSync(url, method, allHeaders, body, options);
        }

        return requestAsync(url, method, allHeaders, body, options);
    }

    /**
     * Synchronous request using XMLHttpRequest.
     * Required for lock release on page unload, synchronous return values, etc.
     */
    function requestSync(url, method, headers, body, options) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, url, false);

        for (var key in headers) {
            if (headers.hasOwnProperty(key)) {
                xhr.setRequestHeader(key, headers[key]);
            }
        }

        try {
            xhr.send(body);
        } catch (e) {
            var errorTransport = makeTransport(0, 'Network error: ' + e.message);
            if (options.onFailure) options.onFailure(errorTransport);
            if (options.onComplete) options.onComplete(errorTransport);
            return errorTransport;
        }

        var transport = makeTransport(xhr.status, xhr.responseText, xhr.responseURL, xhr.statusText);

        // Check for CSRF redirect
        if (xhr.responseURL && xhr.responseURL.indexOf('errorpage.jsp') !== -1) {
            transport.status = 403;
            transport.responseText = 'CSRF validation failed — request was rejected by the server.';
            if (options.onFailure) options.onFailure(transport);
            if (options.onComplete) options.onComplete(transport);
            return transport;
        }

        if (isSuccess(xhr.status)) {
            if (options.onSuccess) options.onSuccess(transport);
            // Note: evalScripts is NOT processed here. In Prototype.js, Ajax.Request
            // did NOT support evalScripts — only Ajax.Updater did. Script execution
            // for request() callers happens via .update() in the shim or manually.
            // CarlosAjax.updater() handles evalScripts through insertContent().
        } else {
            if (options.onFailure) options.onFailure(transport);
        }

        if (options.onComplete) options.onComplete(transport);
        return transport;
    }

    /**
     * Asynchronous request using XMLHttpRequest.
     *
     * <p>Uses XHR instead of fetch() so that CSRFGuard 4.5's JavaScript interceptor
     * can automatically inject CSRF tokens into outgoing requests. CSRFGuard patches
     * XMLHttpRequest.prototype.send() but cannot intercept the fetch() API.</p>
     */
    function requestAsync(url, method, headers, body, options) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, url, true);

        // Set caller-provided headers (Content-Type, etc.)
        // CSRFGuard's onsend interceptor adds X-Requested-With and CSRF-TOKEN
        // automatically — do NOT set those here or they will be duplicated.
        for (var key in headers) {
            if (headers.hasOwnProperty(key)) {
                xhr.setRequestHeader(key, headers[key]);
            }
        }

        xhr.onload = function () {
            var transport = makeTransport(xhr.status, xhr.responseText, xhr.responseURL, xhr.statusText);

            // Detect CSRF rejection (redirect to error page)
            if (isCsrfRedirect(transport)) {
                transport.status = 403;
                transport.responseText = 'CSRF validation failed — request was rejected by the server.';
                if (options.onFailure) {
                    try { options.onFailure(transport); } catch (e) {
                        console.error('CarlosAjax onFailure error:', e);
                    }
                }
                if (options.onComplete) {
                    try { options.onComplete(transport); } catch (e) {
                        console.error('CarlosAjax onComplete error:', e);
                    }
                }
                return;
            }

            // Wrap callbacks in try-catch so that exceptions thrown inside
            // onSuccess do NOT propagate to onerror and trigger onFailure.
            // Prototype's Ajax.Request isolated callback errors the same way.
            if (isSuccess(xhr.status)) {
                if (options.onSuccess) {
                    try { options.onSuccess(transport); } catch (e) {
                        console.error('CarlosAjax onSuccess error:', e);
                    }
                }
            } else {
                if (options.onFailure) {
                    try { options.onFailure(transport); } catch (e) {
                        console.error('CarlosAjax onFailure error:', e);
                    }
                }
            }

            if (options.onComplete) {
                try { options.onComplete(transport); } catch (e) {
                    console.error('CarlosAjax onComplete error:', e);
                }
            }
        };

        xhr.onerror = function () {
            // Network errors (DNS, connection refused, etc.)
            var transport = makeTransport(0, 'Network error');
            if (options.onFailure) {
                try { options.onFailure(transport); } catch (e) {
                    console.error('CarlosAjax onFailure error:', e);
                }
            }
            if (options.onComplete) {
                try { options.onComplete(transport); } catch (e) {
                    console.error('CarlosAjax onComplete error:', e);
                }
            }
        };

        xhr.send(body);
    }

    /**
     * Run an AJAX request and update a DOM element (replaces Ajax.Updater).
     *
     * Callback order: onSuccess -> DOM update -> onComplete (same as Prototype)
     *
     * @param {string|Object} container - Element ID, DOM element, or {success: id, failure: id}
     * @param {string} url - Request URL
     * @param {Object} [options] - Same options as request(), plus:
     *   insertion:   'bottom'|'top'|'before'|'after' (default: replace innerHTML)
     *   evalScripts: If true, extract and run scripts from response HTML
     */
    function updater(container, url, options) {
        options = options || {};

        // Determine target element(s) from container spec
        var successContainer = null;
        var failureContainer = null;

        if (typeof container === 'string') {
            successContainer = document.getElementById(container);
            failureContainer = successContainer;
        } else if (container && container.nodeType) {
            successContainer = container;
            failureContainer = container;
        } else if (container && typeof container === 'object') {
            if (container.success) {
                successContainer = typeof container.success === 'string'
                    ? document.getElementById(container.success) : container.success;
            }
            if (container.failure) {
                failureContainer = typeof container.failure === 'string'
                    ? document.getElementById(container.failure) : container.failure;
            }
        }

        // Wrap callbacks to insert DOM content between onSuccess and onComplete
        var origOnSuccess = options.onSuccess;
        var origOnFailure = options.onFailure;
        var origOnComplete = options.onComplete;
        var insertion = options.insertion;
        var evalScripts = options.evalScripts;

        // Clone options to prevent double-fire of callbacks
        var updaterOptions = {};
        for (var key in options) {
            if (options.hasOwnProperty(key)) {
                updaterOptions[key] = options[key];
            }
        }

        updaterOptions.onSuccess = function (transport) {
            // 1. Fire original onSuccess first
            if (origOnSuccess) origOnSuccess(transport);

            // 2. Update DOM (between onSuccess and onComplete)
            if (successContainer && transport.responseText != null) {
                insertContent(successContainer, transport.responseText, insertion, evalScripts);
            }
        };

        updaterOptions.onFailure = function (transport) {
            if (origOnFailure) origOnFailure(transport);

            if (failureContainer && transport.responseText != null) {
                insertContent(failureContainer, transport.responseText, insertion, evalScripts);
            }
        };

        updaterOptions.onComplete = function (transport) {
            // 3. Fire original onComplete AFTER DOM update
            if (origOnComplete) origOnComplete(transport);
        };

        // Don't double-eval scripts (updater handles it in insertContent)
        updaterOptions.evalScripts = false;

        return request(url, updaterOptions);
    }

    /**
     * Insert HTML content into an element, optionally running scripts.
     * @param {HTMLElement} element - Target element
     * @param {string} html - HTML content to insert
     * @param {string} [insertion] - Position: 'bottom', 'top', 'before', 'after'
     * @param {boolean} [evalScripts] - Whether to extract and run scripts
     */
    function insertContent(element, html, insertion, evalScripts) {
        if (evalScripts) {
            // CodeQL js/bad-tag-filter: The regex-based script tag extraction below is
            // flagged by CodeQL as bypassable. This is acceptable because the HTML comes
            // exclusively from same-origin server AJAX responses (not from user input).
            // The script extraction is a legacy Prototype.js behavior maintained for
            // backward compatibility. Long-term: migrate to JSON AJAX responses.
            var scriptPattern = /<script[\s\S]*?>([\s\S]*?)<\/\s*script\s*>/gi;
            var scripts = [];
            var match;
            while ((match = scriptPattern.exec(html)) !== null) {
                scripts.push(match[1]);
            }
            scriptPattern.lastIndex = 0;
            // Strip script tags (loop to handle nested/overlapping patterns).
            // Extraction above is single-pass; stripping here is multi-pass. Reconstituted
            // scripts (from nested patterns like <scr<script>ipt>) are stripped but NOT
            // executed — this is intentional as they indicate an injection attempt.
            var cleanHtml = html;
            var prev;
            do {
                prev = cleanHtml;
                cleanHtml = cleanHtml.replace(scriptPattern, '');
            } while (cleanHtml !== prev);

            if (insertion) {
                element.insertAdjacentHTML(POS_MAP[insertion] || 'beforeend', cleanHtml);
            } else {
                element.innerHTML = cleanHtml;
            }

            // Run extracted scripts by creating dynamic script elements
            scripts.forEach(function (scriptContent) {
                if (scriptContent.trim()) {
                    var script = document.createElement('script');
                    script.textContent = scriptContent;
                    document.head.appendChild(script);
                    document.head.removeChild(script);
                }
            });
        } else {
            if (insertion) {
                element.insertAdjacentHTML(POS_MAP[insertion] || 'beforeend', html);
            } else {
                element.innerHTML = html;
            }
        }
    }

    // Public API
    return {
        request: request,
        updater: updater,
        getCsrfToken: getCsrfToken
    };
})();
