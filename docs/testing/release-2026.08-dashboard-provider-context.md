# Dashboard provider selection per window

The singleton dashboard manager no longer keeps a login-wide provider map.
An initial provider selection belongs to its HTTP request; later requests carry
that window's explicit `dashboardProviderNo`. Indicator panels, drilldown,
back/refresh navigation, exports, tickler modals and selected-patient actions
retain the selected window scope without changing their POST bodies or CSRF
fields. Own-provider access needs no switching privilege; another provider
requires the existing `_dashboardChgUser` read permission on every request.
Calls outside an HTTP request keep no provider selection.

On the release source, four of six reproduction cases fail: sequential windows,
a new request without a selection, an explicitly selected follow-up and two
concurrent windows of one login. The corrected setup uses the current Struts
ActionContext factory; the initial obsolete-constructor compilation failure is
retained separately and is not a bug reproduction. Valid before JUnit SHA-256:
`84f9287dec6e4c5684844e98f908af1ecd841d72a82c44c6871a9c03e876f6c7`.

All 25 Java controls pass, including concurrent isolation, missing identities,
blank selection, permission refusal/revocation and existing encrypted shared
links. Changed manager lines/instructions/branches have 100% coverage.
All 27 focused client controls pass; the complete Node retake passes all 8,119
cases with zero failures, skips, cancellations or TODOs and unchanged test-source
fingerprints. Its generated case inventory is retained. The first full run's
16 failures came from the existing controller fixture omitting the new helper;
the fixture now loads the real helper and adds bulk-action window-scope controls.

- Java JUnit SHA-256: `12a39f59e926b42322768906830dfdd49378d3de2640ce97c22e87d4d942cde0`.
- Source-bound coverage SHA-256: `cdb2849e7b1e15c64b29dd5dd4f2062477bddd3e2197f127b9e02fa1b0b54c84`.
- Complete Node report SHA-256: `14e9aa273135cba754cef45178ecfacff485c2c74bdfaad5db0b989c4cc31643`.

The native dashboard workflow now creates two owned providers and five patients,
opens two windows under one login, switches the second provider, and verifies
both patient lists/CSV exports, the second window’s native exclusion provider,
and first-window navigation. Its existing bulk
and authorization controls remain selected. New rebuilt-DEB/browser qualification
is pending the next combined batch; the JVM/client proofs above do not claim
that browser execution has already occurred.
