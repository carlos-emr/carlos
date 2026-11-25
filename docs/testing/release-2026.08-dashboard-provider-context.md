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

All 41 Java controls pass, including concurrent isolation, missing identities,
blank selection, permission refusal/revocation and existing encrypted shared
links. Twelve action-to-template controls additionally preserve `all`, `%` and
`loggedInProvider` defaults independently of the displayed heading, while
explicit selections override those defaults for indicators and drilldowns.
Provider refusals use the existing Struts `SecurityException` to return 403.
Changed manager lines/instructions/branches have 100% coverage.
All 27 focused client controls pass; the complete Node retake passes all 8,119
cases with zero failures, skips, cancellations or TODOs and unchanged test-source
fingerprints. Its generated case inventory is retained. The first full run's
16 failures came from the existing controller fixture omitting the new helper;
the fixture now loads the real helper and adds bulk-action window-scope controls.

- Java JUnit SHA-256: `5a2c5e8d31a1bbd2c746bb4b6fbbc39e97a0bfaac4a9837a7222726c9f1ce69a`.
- Source-bound coverage SHA-256: `e31426f4b42b72872e5a3f64ede9de6e40f44856391551c0ee0a8d0892ec70a5`.
- Complete Node report SHA-256: `14e9aa273135cba754cef45178ecfacff485c2c74bdfaad5db0b989c4cc31643`.

The native dashboard workflow now creates two owned providers and five patients,
opens two windows under one login, switches the second provider, and verifies
both patient lists/CSV exports, clinic-wide defaults confined to the five owned
IDs, explicit overrides, the second window’s native exclusion provider,
and first-window navigation. Its existing bulk
and authorization controls remain selected. New rebuilt-DEB/browser qualification
is pending the next combined batch; the JVM/client proofs above do not claim
that browser execution has already occurred.

### Shared-metric display and export follow-up

The shared whole-metric and slice actions now validate their `providerNo` through
request-local provider selection and carry it into the displayed form, provider
heading, Export and Dashboard navigation. A caller cannot switch providers without
`_dashboardChgUser`; selecting their own provider remains supported. The regression
executes the actual shared action and subsequent Export action with the form model:
eight of 12 cases fail before repair, including all/% template defaults broadening
the export. All 53 selected dashboard controls pass after repair; changed-line,
instruction and branch coverage remain 100%. The new JUnit SHA-256 is
`54a68c674596ddecf39b2acebff0ab2fc9230f23d7085400e30dffa2a390b4e2`.

The native workflow adds owned shared callbacks followed by real Export and
Dashboard controls. Its second login now uses the fixture's required exact
16-hex marker. The first installed retake failed that marker guard, and concurrent
ENOSPC invalidated three other workflows; that run is retained as incomplete.
This shared-action repair requires the next rebuilt DEB/native qualification.

Both concurrent shared-provider repair histories and regression suites are retained.
The merged implementation passes all 57 dashboard cases; JUnit SHA-256
`61a96b0657752ad32e8ee458b3caa6392d89297897817c5a0c36cf3c767e8dd7`.
All three tracked production sources retain 100% changed-line/instruction/branch
coverage; merged evidence SHA-256
`1e031222fd034deb6d3007d2c977c61e44e19774eb4ad0f01a165dd34cb92181`.
