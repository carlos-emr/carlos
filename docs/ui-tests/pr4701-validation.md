# PR #4701: CPP save refresh through the blocking WAF

Validated 2026-10-15 for [issue #4699](https://github.com/carlos-emr/carlos/issues/4699),
targeting `release/2026.08`.

## Deployment

- Fresh disposable Ubuntu 26.04 systemd container, Ontario demonstration data,
  HTTPS front door at `https://127.0.0.1/carlos` inside the container.
- CARLOS and DrugRef packages: `2026.09.0~snapshot26+pr4699.1`. The full packaged
  Chromium renderer was included (pinned revision 1685886, Chromium 154.0.8025.0;
  both downloaded archives passed their pinned SHA256 checks).
- Application source: `59d8073cf7bd2aae2d5600073422763cb2b4b80e`, based on release
  `b5495ea20578500225312dd459e17faca9b04261`. Both changed JSPs in the installed
  webapp matched the tested source byte for byte. Subsequent changes are browser
  regression coverage and these notes.
- DrugRef built from the release pin `1941e1426a52c2a129c96ec4d32a0f3499974739`.
  carlos-ctl built from the pin's fallback commit
  `9ad4938898f2baf7a1eecb4f22dfda1c3b32c7e9` and stamped `1.1.2` in the temporary
  packaging tree. Repository version/SCM metadata was restored after packaging.
- nginx 1.28.3, libnginx-mod-http-modsecurity 1.0.3-2build6, OWASP CRS 3.3.8,
  MariaDB 11.8.6, runtime OpenJDK 25.0.4.1.

`carlos-ctl check` passed. It confirmed `SecRuleEngine On` and rejected its live
SQL-injection probe with HTTP 403. No WAF policy or exclusion was changed for
these tests. The installer replaced the initial administrator credential, and
the browser completed the mandatory first-login password reset.

The original Git-fetch failure was a command-sandbox restriction on access to
the configured proxy. With the required command access granted, Git, Maven,
Ubuntu package downloads and Docker worked through that proxy with CA trust.

## Browser results

Run with the package's Chromium, `EXPECT_FRONT_DOOR=true`, the HTTPS base URL,
and the runbook's local database credentials:

```sh
node scripts/echart-playwright-checks.js
ECHART_ISSUES_PIN=editor node scripts/echart-issues-filter-playwright-checks.js
```

- Strengthened `echart`: **PASS, 7/7 steps**, repeated on the restored fixed
  package. It checks both older-note batches, assigns an issue to an unsaved
  encounter note, saves WAF-scored CPP and encounter text, verifies that the
  pending assignment survives the issue refresh, discards the draft, and archives
  the CPP item.
- `echart-issues-filter` with `editor`: **PASS, 5/5 steps**. New-note assignment
  persists; a separate new note does not inherit it; assigning to an edited note
  persists on its latest revision.
- The CPP save and archive POSTs returned HTTP 302 to their display route;
  the active-note `edit` refresh, Unresolved Issues request, and scored-text
  draft autosave returned HTTP 200. Responses carried nginx's server header.
  The existing smoke assertions check stored CPP text and draft text, not just
  response status, and require the WAF-scored autosave request itself.
- All browser workflows used owned synthetic patients and removed their chart
  rows and fixtures through their registered cleanup paths.

## Controls

The binaries, database setup, WAF policy and revised browser check stayed the
same. Only `js/newCaseManagementView.js.jsp` was replaced for each control;
the application was stopped, that JSP's generated Java/class cache cleared,
and the application restarted before testing.

| Callback source | Result |
| --- | --- |
| Original release `b5495ea20578` | **FAIL**: CPP save succeeds, but the post-save Unresolved Issues request is absent; the response wait times out after 15 seconds, reproducing #4699. No HTTP, page or console error is recorded. |
| Initial PR `0fee1665e0cc` | **FAIL**: both issue refreshes return 200, but the pending encounter issue becomes unchecked. The new browser assertion catches the review finding. |
| Corrected `59d8073cf7bd` | **PASS**: refreshes complete and the pending encounter issue stays selected. The package source was restored and its hash verified before final runs. |

## Smoke-check correction uncovered by the live run

Once the missing refresh was fixed, the check reached its archive assertion.
That assertion expected only one matching `casemgmt_note` row across all history.
The current release preserves immutable CPP revisions: the live run stored two
rows after archive, one historical unarchived revision and one current archived
revision (`2/1` total/archived). The UI correctly removed the current item.

The assertion now selects the latest row in each UUID chain and still requires
exactly one current archived revision. It also waits for the archive's issue
refresh before ending the workflow. No application archive behavior was changed.

## Other validation

- Full WAR build passed with dependency-lock verification, main/test compilation,
  Checkstyle, packaging and Javadoc; tests were skipped in that build.
- Focused Node tests: **33 passed**, including both refresh stages, encounter-save
  ordering, failed-save recovery, preservation of the latest checkbox choices by
  issue ID across reordered indexes, and ordinary editor-load isolation.
- Encoder null-safety lint, JavaScript syntax check and `git diff --check` passed.
- Final browser-window server-log audit passed: **0 events, 0 unexplained
  signatures** since `2026-10-15 19:22:47 UTC`. The final `carlos-ctl check`
  also passed, including the live blocking-WAF probe, and installed JSP hashes
  still matched the corrected source after both controls were removed.
- Debian package assembly used `DEB_BUILD_OPTIONS=nocheck`; it is not evidence of
  a full unit-suite run. This validation covers the focused workflows above,
  rather than the complete packaged Playwright suite.
