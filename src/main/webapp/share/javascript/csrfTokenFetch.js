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
 * @param {string} contextPath - the application context path (e.g. "/carlos")
 * @returns {Promise<void>} resolves after tokens have been populated;
 *                          rejects if the token cannot be fetched or parsed
 * @since 2026-04-07
 */
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
            console.warn('CSRF token fetch failed:', err);
            throw err;
        });
}
