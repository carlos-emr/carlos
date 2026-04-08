/**
 * Fetches the CSRF master token from the CSRFGuard servlet and populates
 * all hidden inputs with name="CSRF-TOKEN" on the current page.
 *
 * Usage: include this script, then call fetchCsrfToken(contextPath).
 *
 * @param {string} contextPath - the application context path (e.g. "/carlos")
 * @since 2026-04-07
 */
function fetchCsrfToken(contextPath) {
    fetch(contextPath + '/csrfguard', { credentials: 'same-origin' })
        .then(function(r) { return r.text(); })
        .then(function(js) {
            var match = js.match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
            if (match) {
                var inputs = document.querySelectorAll('input[name="CSRF-TOKEN"]');
                for (var i = 0; i < inputs.length; i++) {
                    inputs[i].value = match[1];
                }
            } else {
                console.warn('CSRF token: could not extract masterTokenValue from /csrfguard response');
            }
        })
        .catch(function(err) {
            console.warn('CSRF token fetch failed:', err);
        });
}
