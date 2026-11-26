/**
 * Fetches the CSRF master token from the CSRFGuard servlet and populates
 * all hidden inputs with name="CSRF-TOKEN" on the current page.
 *
 * Usage: include this script, then call fetchCsrfToken(contextPath).
 * The returned promise resolves once tokens have been populated and
 * rejects if the fetch, parse, or population step fails. Callers that
 * chain work on the token (e.g. .then(...) to issue an AJAX POST) must
 * either await resolution or handle rejection so they do not submit
 * with an empty token.
 *
 * A navigation that starts while the fetch is still in flight cancels it, and
 * the resulting rejection is reported as a plain "TypeError: Failed to fetch"
 * — indistinguishable from a real network failure. That case is not worth
 * warning about: the document that would have used the token is already being
 * destroyed. The promise still rejects (callers depend on that), but the
 * console stays quiet, so a genuine warning means a genuine failure.
 *
 * @param {string} contextPath - the application context path (e.g. "/carlos")
 * @param {AbortSignal} [signal] - cancels this request when its caller times out
 * @returns {Promise<void>} resolves after tokens have been populated;
 *                          rejects if the token cannot be fetched or parsed
 * @since 2026-04-07
 */

/**
 * Set once this document starts going away, so the rejection handler below can
 * tell "the page was torn down mid-request" from "the request really failed".
 *
 * pagehide only, deliberately NOT beforeunload. A beforeunload listener makes
 * the page ineligible for the back/forward cache in some browsers, and this
 * script is included on every JSP that bootstraps a token — a real cost paid on
 * every page for no benefit here, since pagehide fires in every teardown case
 * this cares about. Ordering is not what the flag is for anyway: the rejection
 * is frequently delivered before ANY unload event runs, which is why the
 * warning below is also deferred a task.
 *
 * Cleared again on pageshow. A document entering the back/forward cache fires
 * pagehide WITHOUT being destroyed, and coming back does not re-run this
 * script — so without the reset the flag would stay set for the rest of that
 * document's life and silence every later genuine failure on it, including the
 * eForm manager's retry.
 */
var carlosCsrfPageUnloading = false;
if (typeof window !== 'undefined' && window.addEventListener) {
    window.addEventListener('pagehide', function () { carlosCsrfPageUnloading = true; }, true);
    window.addEventListener('pageshow', function () { carlosCsrfPageUnloading = false; }, true);
}

function fetchCsrfToken(contextPath, signal) {
    return fetch(contextPath + '/csrfguard', { credentials: 'same-origin', signal: signal })
        .then(function(r) {
            if (!r.ok) {
                throw new Error('CSRFGuard request failed with status ' + r.status);
            }
            return r.text();
        })
        .then(function(js) {
            var match = js.match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
            if (!match) {
                throw new Error('Could not extract masterTokenValue from /csrfguard response');
            }
            var inputs = document.querySelectorAll('input[name="CSRF-TOKEN"]');
            if (inputs.length === 0) {
                throw new Error('No input[name="CSRF-TOKEN"] element found on page');
            }
            for (var i = 0; i < inputs.length; i++) {
                inputs[i].value = match[1];
            }
        })
        .catch(function(err) {
            if (!carlosCsrfPageUnloading) {
                // Deferred by one task on purpose. A navigation cancels this
                // request and destroys the document a moment later, and the
                // rejection can be delivered before either unload event runs —
                // so the flag above is not on its own enough. A task queued on
                // a document that is being destroyed is discarded with it, so
                // the warning survives only if the page is still alive to act
                // on it. The rejection itself is not deferred.
                setTimeout(function () {
                    if (!carlosCsrfPageUnloading) {
                        console.warn('CSRF token fetch failed:', err);
                    }
                }, 0);
            }
            throw err;
        });
}

/**
 * Returns the first non-empty CSRF-TOKEN input value on the page, or '' when none
 * has a token yet. A page can hold several such inputs (the csrf-token.jspf
 * bootstrap input and the ones CSRFGuard adds to forms); the first one in the
 * document is often an empty placeholder, so reading only that one misses a
 * token another input already carries.
 *
 * @returns {string} the CSRF token, or '' if no input carries one
 */
function carlosFirstCsrfToken() {
    var inputs = document.querySelectorAll('input[name="CSRF-TOKEN"]');
    for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].value) {
            return inputs[i].value;
        }
    }
    return '';
}

/**
 * Puts a usable CSRF token into a fetch() POST body before it is sent (#1408).
 *
 * Bodies built with {@code new URLSearchParams(new FormData(form))} carry the
 * form's hidden CSRF-TOKEN input even while it is still empty, so a
 * {@code params.has('CSRF-TOKEN')} fallback never ran and a click made before
 * the page's token fetch finished posted an empty token, which CSRFGuard
 * rejects. This uses the body's own token when it has one, else the first
 * token on the page, and otherwise fetches the token first.
 *
 * @param {URLSearchParams} params - the request body; its CSRF-TOKEN is set in place
 * @param {string} contextPath - the application context path (e.g. "/carlos")
 * @param {number} [timeoutMs=15000] - how long to wait for a token fetch before rejecting
 * @returns {Promise<URLSearchParams>} resolves with params once it carries a token;
 *                                     rejects when no token can be obtained
 */
function carlosEnsureCsrfParam(params, contextPath, timeoutMs) {
    var token = params.get('CSRF-TOKEN') || carlosFirstCsrfToken();
    if (token) {
        params.set('CSRF-TOKEN', token);
        return Promise.resolve(params);
    }
    // A /csrfguard request that never answers must not leave the click silently waiting: give up
    // after the deadline so the caller's error handling tells the user.
    var deadline = typeof timeoutMs === 'number' ? timeoutMs : 15000;
    var timer;
    var timedOut = new Promise(function (resolve, reject) {
        timer = setTimeout(function () {
            reject(new Error('CSRF token request timed out'));
        }, deadline);
    });
    return Promise.race([fetchCsrfToken(contextPath), timedOut]).then(function () {
        clearTimeout(timer);
        var fetched = carlosFirstCsrfToken();
        if (!fetched) {
            throw new Error('CSRF token unavailable after fetch');
        }
        params.set('CSRF-TOKEN', fetched);
        return params;
    }, function (err) {
        clearTimeout(timer);
        throw err;
    });
}
