/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Helpers for workflow checks that must prove a request reached (or was refused by) the packaged
// nginx + ModSecurity front door rather than CSRFGuard or bare Tomcat.
const h = require('./playwright-harness');

/**
 * Reads this session's CSRFGuard master token. Without a token CSRFGuard itself answers 403,
 * which would let a WAF-refusal probe "pass" without ever reaching the WAF, so an empty token
 * fails the check.
 */
async function csrfToken(page, baseUrl) {
  const token = await page.evaluate(async (tokenUrl) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- tokenUrl is a Playwright argument built by appUrl from the validated base URL
    const response = await fetch(tokenUrl, { credentials: 'same-origin' });
    const match = (await response.text()).match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
    return match ? match[1] : '';
  }, h.appUrl(baseUrl, '/csrfguard'));
  h.assert(token, 'No CSRF token could be read for this session');
  return token;
}

/**
 * With EXPECT_FRONT_DOOR=true the run must actually go through the packaged nginx front door
 * (an nginx Server header), or a green run against bare Tomcat would say nothing about the WAF.
 * Call early in the workflow; call the returned function at the end to assert.
 */
function watchFrontDoor(s) {
  const seen = { nginx: false };
  s.context.on('response', response => {
    if (/nginx/i.test(response.headers()['server'] || '')) seen.nginx = true;
  });
  return () => h.assert(!s.config.expectFrontDoor || seen.nginx,
    'EXPECT_FRONT_DOOR is set but no response carried an nginx Server header; the run did not go through the front door');
}

module.exports = { csrfToken, watchFrontDoor };
