# PR #3992 validation

Validated on 2026-09-27 against release/2026.08
`25a867f07b9bda663aaa70aab8e5de225605f999`.
Issues: #3949 (Add Link URLs), #4014 (metadata writes before validation),
#4015 (document metadata and HTML editor round trips).

## Review outcome

The URL parser requires a parsed server host, a web scheme and a valid port.
Digit-leading opaque schemes such as `javascript:1` are rejected. Dotted hosts,
localhost and IP literals support numeric-port shorthand; other single-label
hosts with ports require an explicit web scheme or `//` authority prefix.
Existing stored link documents retain their existing display behavior.

Scheme matching uses ASCII rules. UTF-8 percent encoding preserves the exact
Unicode path/query spelling and existing escapes, without URI.toASCIIString's
Unicode normalization. Invalid Unicode is rejected instead of throwing. The
stored redirect uses encoded attributes and no inline script; browser checks
verify the destination and absence of a referrer.

Invalid submissions no longer create shared document types. Link/HTML documents
retain their source facility; new appointment associations require an existing
appointment for the same patient, and validation retries retain the submitted
identifier. Legacy HTML editing keeps raw form values and encodes once at the
textarea output, covering both initial loads and validation retries.

The browser check owns its patient, appointment and documents. Cleanup verifies
ownership and removal through the shared workflow cleanup helper. WAF exceptions
are restricted to the expected 403 POST and corresponding console entry; other
errors on the same page still fail the check. Both core and front-door suites
include this check.

## Automated validation

- Full Java 25 unit/integration suite: 13,410 tests reported, zero failures/errors,
  51 skips. The focused URL/action regressions were also run during development.
- Changed executable Java lines: 73/73 covered (100%); no unmapped changed files.
- Final complete Node script suite: 1,035 passed, zero failures/skips.
- Encoder null-safety, BDD naming and translation parity checks passed.
- Final JSP compilation: 982 pages; WAR packaging and Javadoc generation passed.
- All three DEBs built as `2026.08.0~alpha16~pr3992.2`, with the VM stopped and a
  single packaging worker. All 6,667 checked class/web files matched both package
  contents and the installed Ubuntu 26.04 deployment. Package identity was checked.
- Installation and final health checks passed, including HTTPS, WAF blocking,
  database and DrugRef. CPU and memory limits were maintained.

## Installed browser validation

The complete `document-add-link` check passed against both HTTPS/nginx and direct
Tomcat on loopback port 18080:

- HTTPS, schemeless and decomposed-Unicode links stored correctly; navigation
  reached the exact intercepted URL without leaking a referrer.
- Ten invalid inputs stored nothing. The front door blocked three probes and
  the application rejected the other seven; direct Tomcat rejected all ten.
- Source facility and appointment association survived save/reload.
- Hostile stored textarea text remained inert and exact. On direct Tomcat, a
  missing-description POST also proved the server-side validation retry kept
  hostile HTML inert and left stored metadata unchanged.
- Owned patient, appointment and document fixtures were removed after each run.

Existing neighboring checks passed: `document-upload`,
`edoc-schedule-navigation`, `demographic-gates`, and `document-pagination`.
Pagination initially skipped for absent PDFs, then passed with an owned patient
and valid one-page/three-page PDFs (two documents, four navigation transitions).
Those fixtures were removed and the prior display preference restored.

The VM was stopped after final health validation. Logs and hashes are retained
under `~/work/pr3985-4000-evidence/` on the development machine.

## SHA-256 package hashes

- `carlos-emr-drugref_2026.08.0~alpha16~pr3992.2_all.deb`: `73cbbf9b6324ab6648376cf09b0a2dd1cdf64afb5bb099836518302374530fa5`
- `carlos-emr-eform-renderer_2026.08.0~alpha16~pr3992.2_all.deb`: `20dc2a17889939fd3b79c1e3b8ee43899310c373e2480e45e6deffd8f0a37006`
- `carlos-emr_2026.08.0~alpha16~pr3992.2_amd64.deb`: `f3dc9d450ebc59b1649e8bbb507f3c04484c848dfe99d888aeee8a0c564ac7d5`

## Follow-up review validation

The shorthand host/port check now uses a linear scan with constant stack usage,
removing the nested regex identified by Sonar. URI server-authority validation
still checks the host and port range, and ambiguous single-label schemes remain
rejected. Regressions cover 20,000-label hosts, malformed ports, missing ports,
non-ASCII digits and attempted user-info insertion.

The browser check compares decoded meta-refresh and anchor attributes using the
browser's HTML parser. It no longer recreates production HTML escaping with a
single replacement. Both ordinary and decomposed-Unicode URLs contain multiple
query separators, and navigation must reach their exact expected destinations.

- Clean focused Java URL/action tests: 84 passed, zero failures/errors/skips.
- Full Node suite: 1,035 passed; focused refusal/registration tests: five passed.
- All 982 JSPs, WAR and Javadoc compile/package successfully.
- All three DEBs built and installed as `2026.08.0~alpha16~pr3992.3`;
  6,667 packaged and installed payload files match the tested output.
- Health and the complete Add Link workflow passed through HTTPS/nginx; the
  complete workflow also passed directly against Tomcat. All ten invalid inputs
  were rejected by the application in the direct run. Metadata, Unicode URLs,
  multiple query parameters and hostile HTML retry behavior passed.
- Owned fixtures were removed. Final health and installed hashes passed,
  `NRestarts=0`, and the VM was stopped. No configuration or schema changes remain.
