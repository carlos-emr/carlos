/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/**
 * CSRF-safe submission for forms that CSRFGuard's client script cannot reach.
 *
 * WHY THIS EXISTS (issue #4130). CSRFGuard's injected script adds the hidden
 * CSRF-TOKEN input to forms in two ways, and both miss common CARLOS patterns:
 *
 *   1. On DOMContentLoaded it walks the page's forms. A form built later with
 *      document.createElement('form') is not there yet.
 *   2. Its MutationObserver re-injects only when the ADDED NODE ITSELF is a
 *      <form>, and the observer callback runs after the current task. So a
 *      form that is created, appended and submit()ted in one click handler
 *      leaves before the observer runs, and a form that arrives nested inside
 *      a container (the Administration shell's $("#dynamic-content").load())
 *      is never injected at all.
 *
 * Either way the POST arrives without a token, CarlosCsrfGuardFilter answers
 * 403, and to the user the button does nothing.
 *
 * WHAT IT PROVIDES (all on window):
 *
 *   carlosPostForm(action, fields, options)
 *       Builds a hidden POST form, attaches the session token, submits it.
 *       `fields` is a plain object or an array of [name, value] pairs (use the
 *       array form for repeated names). options.target names a window/frame;
 *       open a popup with window.open('', name) BEFORE calling this, inside the
 *       click handler, so popup blockers see a user gesture even when the
 *       token has to be fetched first.
 *
 *   carlosSubmitForm(form)
 *       Attaches the token to an existing form element and submits it. Use
 *       instead of form.submit() for runtime-built POST forms.
 *
 *   CarlosCsrf.injectIntoForms(root)
 *       Adds the token to every same-origin POST form inside `root` (and
 *       `root` itself when it is a form). Call it after inserting HTML that
 *       contains forms, e.g. in a jQuery .load() completion callback.
 *
 *   CarlosCsrf.token()
 *       A Promise of the session's master token.
 *
 * Loading the script also keeps the page's same-origin POST forms tokenised
 * on its own: one pass after the document is parsed, then every form inside
 * any subtree inserted later (so HTML injected by .load() is covered without
 * an explicit injectIntoForms call).
 *
 * WHERE THE TOKEN COMES FROM, in order: a populated input[name="CSRF-TOKEN"]
 * already on the page (CSRFGuard's own injection, or csrf-token.jspf), the
 * pending csrf-token.jspf bootstrap (window.csrfTokenReady), and finally a
 * fetch of the CSRFGuard servlet, the same request csrfTokenFetch.js makes.
 * Including csrf-token.jspf on a page is still worthwhile because it makes the
 * token available synchronously in most cases.
 *
 * Only forms whose action resolves to this page's origin ever receive the
 * token, so this cannot leak it to another site. GET forms are left alone.
 *
 * Failure is never silent: if no token can be obtained the form is NOT
 * submitted (it would only be refused), the user is told, and the returned
 * Promise rejects.
 *
 * @since 2026-10-01
 */
(function (global) {
    'use strict';

    var TOKEN_NAME = 'CSRF-TOKEN';
    var SCRIPT_PATH_SUFFIX = '/share/javascript/carlosCsrfForm.js';
    var FAILURE_MESSAGE = 'This action could not be sent because the page\'s security token '
        + 'could not be loaded. Please reload the page and try again.';

    function contextPathFromScriptUrl(src) {
        try {
            var path = new URL(src, global.location.href).pathname;
            var at = path.lastIndexOf(SCRIPT_PATH_SUFFIX);
            return at >= 0 ? path.substring(0, at) : null;
        } catch (e) {
            return null;
        }
    }

    // Captured while this script is first executing; document.currentScript
    // is null later. It is also null when jQuery's .load() evaluates the
    // script out of an injected fragment (the Administration shell), which is
    // why contextPath() has further fallbacks.
    var loadedFromPath = document.currentScript && document.currentScript.src
        ? contextPathFromScriptUrl(document.currentScript.src)
        : null;

    /**
     * The application context path, used only for the servlet fetch fallback.
     * A page may pin it by setting window.carlosContextPath.
     */
    function contextPath() {
        if (typeof global.carlosContextPath === 'string') {
            return global.carlosContextPath;
        }
        if (loadedFromPath !== null) {
            return loadedFromPath;
        }
        var scripts = document.querySelectorAll('script[src]');
        for (var i = 0; i < scripts.length; i++) {
            var found = contextPathFromScriptUrl(scripts[i].src);
            if (found !== null) {
                return found;
            }
        }
        // Last resort: CARLOS is deployed under a single context segment
        // (/carlos on packaged installs), so take the first path segment.
        var segment = global.location.pathname.split('/')[1];
        return segment ? '/' + segment : '';
    }

    var pendingFetch = null;

    /** The first non-empty CSRF-TOKEN value on the page, or ''. */
    function currentToken() {
        var inputs = document.querySelectorAll('input[name="' + TOKEN_NAME + '"]');
        for (var i = 0; i < inputs.length; i++) {
            if (inputs[i].value) {
                return inputs[i].value;
            }
        }
        return '';
    }

    function fetchFromServlet() {
        return fetch(contextPath() + '/csrfguard', { credentials: 'same-origin' })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('CSRFGuard request failed with status ' + response.status);
                }
                return response.text();
            })
            .then(function (js) {
                var match = js.match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
                if (!match) {
                    throw new Error('Could not extract masterTokenValue from /csrfguard response');
                }
                return match[1];
            });
    }

    /**
     * Resolves to the session's CSRF token. Never resolves to an empty
     * string: if no token can be found it rejects.
     */
    function token() {
        var existing = currentToken();
        if (existing) {
            return Promise.resolve(existing);
        }
        if (!pendingFetch) {
            var bootstrap = global.csrfTokenReady;
            var source = bootstrap && typeof bootstrap.then === 'function'
                ? bootstrap.then(function () {
                    var value = currentToken();
                    return value || fetchFromServlet();
                }, fetchFromServlet)
                : fetchFromServlet();
            pendingFetch = source.then(function (value) {
                if (!value) {
                    throw new Error('Empty CSRF token');
                }
                // Seed the page's empty bootstrap inputs too, so the page's own
                // fetch() callers and the next lookup find it synchronously.
                // Only standalone inputs (csrf-token.jspf's) and those in forms
                // that post to this origin: an empty token field in a GET or
                // foreign form must stay empty, or submitting it would leak
                // the token into a URL or to another host.
                var inputs = document.querySelectorAll('input[name="' + TOKEN_NAME + '"]');
                for (var i = 0; i < inputs.length; i++) {
                    var owner = owningForm(inputs[i]);
                    if (!inputs[i].value && (!owner || isSameOriginPostForm(owner))) {
                        inputs[i].value = value;
                    }
                }
                return value;
            });
            // A failed lookup must not be cached forever: the next attempt
            // (e.g. after the user dismisses the alert and clicks again)
            // should retry rather than replay the rejection.
            pendingFetch.catch(function () {
                pendingFetch = null;
            });
        }
        return pendingFetch;
    }

    /** The form an input belongs to, or null for a standalone input. */
    function owningForm(input) {
        if (input.form !== undefined) {
            return input.form;
        }
        for (var node = input.parentNode; node; node = node.parentNode) {
            if (node.tagName && node.tagName.toLowerCase() === 'form') {
                return node;
            }
        }
        return null;
    }

    /**
     * True for a submission that posts to this page's own origin. A clicked
     * submitter's formmethod / formaction override the form's own, exactly as
     * the browser applies them, so they are checked when one is given.
     */
    function isSameOriginPostForm(form, submitter) {
        var override = function (name) {
            return submitter && submitter.getAttribute && submitter.hasAttribute
                && submitter.hasAttribute(name) ? submitter.getAttribute(name) : null;
        };
        // An override that is present but empty or invalid means GET, as the
        // browser treats it; only an ABSENT formmethod defers to the form.
        var formMethod = override('formmethod');
        var method = (formMethod !== null ? formMethod : (form.getAttribute('method') || 'get')).toLowerCase();
        if (method !== 'post') {
            return false;
        }
        var formAction = override('formaction');
        var action = formAction !== null ? formAction : form.getAttribute('action');
        if (action && /^\s*javascript:/i.test(action)) {
            return false;
        }
        try {
            // Resolve exactly as the browser will: against document.baseURI,
            // which honours a <base href> (several MCEDT and BC pages set one).
            // Resolving against location instead would hand the token to a
            // form whose base points at another origin. A missing or empty
            // action posts back to the current document.
            var base = document.baseURI || global.location.href;
            var url = new URL(action || global.location.href, base);
            return url.origin === global.location.origin;
        } catch (e) {
            return false;
        }
    }

    /**
     * The CSRF-TOKEN inputs this form will actually submit: its own controls,
     * per form.elements. That honours the form="" attribute (an input inside
     * this form may belong to another, and one outside may belong to this), so
     * the token never goes into a field another form submits. Iterated by
     * index, never form.elements[name]: a form whose controls are named with
     * numbers (the dx code search) makes named lookups resolve as indexes.
     */
    function ownedTokenInputs(form) {
        var owned = [];
        var controls = form.elements;
        if (controls && typeof controls.length === 'number') {
            for (var i = 0; i < controls.length; i++) {
                if (controls[i] && controls[i].name === TOKEN_NAME) {
                    owned.push(controls[i]);
                }
            }
        }
        return owned;
    }

    /** Writes the token into the form, reusing a CSRF-TOKEN input it already owns. */
    function setFormToken(form, value) {
        var inputs = ownedTokenInputs(form);
        if (inputs.length > 0) {
            for (var i = 0; i < inputs.length; i++) {
                inputs[i].value = value;
            }
            return;
        }
        var hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = TOKEN_NAME;
        hidden.value = value;
        form.appendChild(hidden);
    }

    function clearFormToken(form) {
        var inputs = ownedTokenInputs(form);
        for (var i = 0; i < inputs.length; i++) {
            inputs[i].value = '';
        }
    }

    function hasToken(form) {
        var inputs = ownedTokenInputs(form);
        for (var i = 0; i < inputs.length; i++) {
            if (inputs[i].value) {
                return true;
            }
        }
        return false;
    }

    function reportFailure(err) {
        if (typeof console !== 'undefined' && console.error) {
            console.error('CSRF token unavailable; form not submitted.', err);
        }
        global.alert(FAILURE_MESSAGE);
    }

    /**
     * Adds the token to every same-origin POST form in `root`. Synchronous
     * when the page already holds a token; otherwise the forms are filled in
     * as soon as it arrives.
     *
     * @param {ParentNode} [root=document]
     * @param {boolean} [onlyMissing=false] skip forms that already carry a
     *        non-empty token, so a fully tokenised page costs no request
     * @returns {Promise<number>} the number of forms given the token
     */
    function injectIntoForms(root, onlyMissing) {
        root = root || document;
        var forms = [];
        if (root.tagName && root.tagName.toLowerCase() === 'form') {
            forms.push(root);
        }
        if (root.querySelectorAll) {
            var nested = root.querySelectorAll('form');
            for (var i = 0; i < nested.length; i++) {
                forms.push(nested[i]);
            }
        }
        forms = forms.filter(function (form) {
            return isSameOriginPostForm(form) && !(onlyMissing && hasToken(form));
        });
        if (forms.length === 0) {
            return Promise.resolve(0);
        }
        var apply = function (value) {
            // Re-checked at write time: while the token was being fetched a
            // form's method or action may have changed to GET or another
            // origin, and such a form must not receive it.
            var given = 0;
            forms.forEach(function (form) {
                if (isSameOriginPostForm(form)) {
                    setFormToken(form, value);
                    given++;
                }
            });
            return given;
        };
        var existing = currentToken();
        if (existing) {
            return Promise.resolve(apply(existing));
        }
        return token().then(apply);
    }

    /**
     * Attaches the token to `form` and submits it.
     *
     * @param {HTMLFormElement} form
     * @returns {Promise<void>} rejects (after telling the user) if no token
     */
    function submitForm(form) {
        if (!isSameOriginPostForm(form)) {
            // GET or cross-origin: nothing to protect, and a token already in
            // the form (its method or action changed after it was tokenised)
            // would go into a URL or to another host. A native submit() fires
            // no submit event, so the guard below never sees this one.
            clearFormToken(form);
            HTMLFormElement.prototype.submit.call(form);
            return Promise.resolve();
        }
        var existing = currentToken();
        if (existing) {
            setFormToken(form, existing);
            HTMLFormElement.prototype.submit.call(form);
            return Promise.resolve();
        }
        return token().then(function (value) {
            // The form may have been re-pointed while the token was fetched.
            // This native submit() bypasses the submit guard, so check again.
            if (isSameOriginPostForm(form)) {
                setFormToken(form, value);
            } else {
                clearFormToken(form);
            }
            HTMLFormElement.prototype.submit.call(form);
        }, function (err) {
            reportFailure(err);
            throw err;
        });
    }

    /**
     * Builds a hidden form, attaches the token and submits it.
     *
     * @param {string} action URL to post to (same origin)
     * @param {Object|Array} [fields] object or array of [name, value] pairs
     * @param {{target?: string, method?: string}} [options]
     * @returns {Promise<void>}
     */
    function postForm(action, fields, options) {
        options = options || {};
        var form = document.createElement('form');
        form.method = options.method || 'post';
        form.action = action;
        form.style.display = 'none';
        var target = options.target;
        var openedHere = null;
        if (target === '_blank' && !currentToken()) {
            // The token has to be fetched first, and by the time it arrives the
            // click's user activation may have expired, so a _blank submission
            // would be popup-blocked. Open the window now, while the gesture is
            // still live, and post into it by name.
            target = 'carlosPost' + Date.now();
            openedHere = global.open('', target);
        }
        if (target) {
            form.target = target;
        }
        var pairs = [];
        if (Array.isArray(fields)) {
            pairs = fields;
        } else if (fields) {
            Object.keys(fields).forEach(function (name) {
                pairs.push([name, fields[name]]);
            });
        }
        pairs.forEach(function (pair) {
            var value = pair[1];
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = pair[0];
            input.value = value === null || value === undefined ? '' : String(value);
            form.appendChild(input);
        });
        // Left in the document after submitting: detaching a form in the same
        // task as submit() has cancelled the navigation in some browsers, and
        // one hidden form per click is harmless.
        document.body.appendChild(form);
        return submitForm(form).catch(function (err) {
            // Nothing was sent: close the blank window this helper opened so it
            // does not linger beside the alert. A window the caller opened (a
            // named popup target) is the caller's to manage, and is left alone.
            if (openedHere && typeof openedHere.close === 'function') {
                openedHere.close();
            }
            throw err;
        });
    }

    function warnInjectionFailure(err) {
        if (typeof console !== 'undefined' && console.warn) {
            console.warn('CSRF token could not be added to the page\'s forms.', err);
        }
    }

    /**
     * Keeps every same-origin POST form on the page tokenised: once after
     * the document is parsed, then for each subtree inserted later. This
     * covers the two cases CSRFGuard's own pass misses: forms nested inside
     * an inserted container (it only inspects inserted nodes that ARE forms),
     * and pages where its pass throws part-way and stops (a form with
     * numerically-named controls) leaving later forms without a token.
     */
    function installAutoInjection() {
        if (global.__carlosCsrfAutoInjection) {
            return; // already installed by an earlier copy of this script
        }
        global.__carlosCsrfAutoInjection = true;

        // A user can submit a panel form (a native submit button, or
        // requestSubmit()) while the token lookup for it is still pending or
        // after it failed. Hold such a submission until a token is attached,
        // then replay it with the same submitter so its name=value is kept;
        // if no token can be had, stop it and say so rather than letting a
        // doomed POST go out. Capture phase, so page handlers see the replay.
        document.addEventListener('submit', function (event) {
            var form = event.target;
            if (!form || !form.tagName || form.tagName.toLowerCase() !== 'form') {
                return;
            }
            var submitter = event.submitter || null;
            if (!isSameOriginPostForm(form, submitter)) {
                // This submission goes by GET or to another origin (a
                // submitter's formmethod / formaction can do that to a POST
                // form). A token CSRFGuard or this script put in the form
                // would end up in a URL or at another host, so blank it; a
                // later same-origin submission is re-tokenised by this guard.
                clearFormToken(form);
                return;
            }
            if (hasToken(form)) {
                return;
            }
            var existing = currentToken();
            if (existing) {
                setFormToken(form, existing);
                return;
            }
            event.preventDefault();
            event.stopImmediatePropagation();
            token().then(function (value) {
                var canReplay = typeof form.requestSubmit === 'function';
                // Only requestSubmit() carries the submitter; the native fallback
                // submits with the form's own method and action.
                var replaySubmitter = canReplay && submitter && submitter.form === form ? submitter : null;
                // Re-checked now: while the token loaded, the form's method or
                // action may have changed to GET or another origin. Such a
                // submission goes ahead as the browser would send it, without the
                // token. Clearing (rather than skipping the write) also covers a
                // replay that constraint validation blocks: no token is left
                // behind for a later native submit() to carry off.
                if (isSameOriginPostForm(form, replaySubmitter)) {
                    setFormToken(form, value);
                } else {
                    clearFormToken(form);
                }
                if (canReplay) {
                    form.requestSubmit(replaySubmitter || undefined);
                } else {
                    HTMLFormElement.prototype.submit.call(form);
                }
            }, reportFailure);
        }, true);

        var observeInsertions = function () {
            if (typeof global.MutationObserver !== 'function') {
                return;
            }
            new global.MutationObserver(function (mutations) {
                for (var i = 0; i < mutations.length; i++) {
                    var added = mutations[i].addedNodes;
                    for (var j = 0; j < added.length; j++) {
                        var node = added[j];
                        if (node.nodeType !== 1) {
                            continue;
                        }
                        var isForm = node.tagName.toLowerCase() === 'form';
                        if (isForm || (node.querySelector && node.querySelector('form'))) {
                            injectIntoForms(node, true).catch(warnInjectionFailure);
                        }
                    }
                }
            }).observe(document.documentElement, { childList: true, subtree: true });
        };

        // Both halves start one task after DOMContentLoaded. CSRFGuard's own
        // pass is a DOMContentLoaded listener too, so by then it has run and
        // only the forms it left without a token are touched: a healthy page
        // makes no request. Observing earlier would also see every static form
        // the parser inserts, before CSRFGuard had tokenised any of them.
        var start = function () {
            setTimeout(function () {
                injectIntoForms(document, true).catch(warnInjectionFailure);
                observeInsertions();
            }, 0);
        };
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', start);
        } else {
            start();
        }
    }

    installAutoInjection();

    global.CarlosCsrf = {
        token: token,
        currentToken: currentToken,
        injectIntoForms: injectIntoForms,
        submitForm: submitForm,
        postForm: postForm
    };
    global.carlosPostForm = postForm;
    global.carlosSubmitForm = submitForm;
})(window);
