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
 * @returns {Promise<void>} resolves after tokens have been populated;
 *                          rejects if the token cannot be fetched or parsed
 * @since 2026-04-07
 */

/**
 * Set once this document starts going away, so the rejection handler below can
 * tell "the page was torn down mid-request" from "the request really failed".
 * Both pagehide and beforeunload are observed because which one runs first —
 * and whether either runs before the in-flight request is cancelled — varies
 * with how the navigation was initiated.
 */
var carlosCsrfPageUnloading = false;
if (typeof window !== 'undefined' && window.addEventListener) {
    window.addEventListener('pagehide', function () { carlosCsrfPageUnloading = true; }, true);
    window.addEventListener('beforeunload', function () { carlosCsrfPageUnloading = true; }, true);
}

function fetchCsrfToken(contextPath) {
    return fetch(contextPath + '/csrfguard', { credentials: 'same-origin' })
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
