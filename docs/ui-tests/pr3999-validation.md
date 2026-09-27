# PR #3999 validation

Validated on 2026-09-27 against `release/2026.08` (`25a867f07b9b`), using
Ubuntu 26.04 VM `carlos-val`. Related issues: #3942, #4032, #4033, #4034,
and #4035. The original zero-value group-member correction is credited to
Sebastian Ibanez in openo-beta/Open-O#2510.

## Behavior reviewed

- Group membership uses claim-item count for creation and regeneration, including
  zero-value and net-zero batches. Empty providers are omitted from output and
  finalization; an entirely empty regeneration preserves the existing file.
- Selecting one group provider processes only that provider. Complete group
  rendering precedes any file publication.
- Optional legacy referral, facility, laboratory, manual-review and location
  NULLs become spaces in the fixed-width export. Required-field validation is
  retained.
- Regeneration prepares batch metadata without changing the database. Its audit
  and editable metadata are finalized with claim status and summaries in the
  same transaction. Stored batch identity and the prior audit value are retained.
- Existing OHIP files and HTML previews are restored after a confirmed failure.
  Failed restoration retains rollback copies and reports reconciliation guidance.
  A lost commit acknowledgement retains output rather than deleting files for
  claims that may already be billed. Preview backup names identify the original
  preview for reconciliation.
- The existing diskette R button now adds the session CSRF token to its POST.
  Missing tokens produce a localized visible error; cancellation sends nothing.
  Existing method signatures and POST-only action enforcement remain available.

## Automated checks

| Check | Result |
| --- | --- |
| Full Java suite after service changes | 13,367 tests; zero failures/errors; 51 existing skips |
| MRI JSP encoding and mutation-method contracts after CSRF fix | 48 passed |
| Node suite after final browser/JSP changes | 1,040 passed, no skips |
| JSP compilation | 982 JSPs, zero errors |
| Javadoc, BDD naming, JSP taglibs, locale consistency, security-exception convention and encoder lint | Passed |
| Changed executable Java lines | 143/166 covered (86.1%) |

Five new group-integrity cases reproduced the original failures before their
fixes. The optional-NULL export test also reproduced the original exception.
Regression coverage includes exact fixed-width zero-fee records, original-preview
restoration, partial group publication and failed restoration, and real Spring
transaction proxies with H2 for commit, rollback and lost commit acknowledgement.
Orchestration tests verify that uncertain outcomes retain files.

Seven cleanup tests exercise partial setup, lost insert acknowledgement, credential
disposal even when cleanup fails, path/symlink rejection and unrelated-file
preservation. Three JavaScript tests execute the R-button handler for token-bearing
submission, missing tokens and cancellation.

## Packages and installed checks

All three packages were built serially and installed:

- `carlos-emr_2026.08.0~alpha16~pr3999.2_amd64.deb`
- `carlos-emr-drugref_2026.08.0~alpha16~pr3999.2_all.deb`
- `carlos-emr-eform-renderer_2026.08.0~alpha16~pr3999.2_all.deb`

6,669 packaged and installed classes/web resources matched the tested build.
`carlos-ctl check` passed, including front-door/WAF checks, live DrugRef lookup
and the existing 432-table database with 29 successful migrations.

The initial installed run successfully generated ZERO's disk but exposed the
pre-existing R-button HTTP 403: native form submission omitted the CSRF token.
That failure is tracked in #4035. After the fix and a complete package rebuild,
`billing-on-group-disk-zero-total` passed without skips:

1. ZERO exports two zero-fee items with NULL optional fields; PAID stays unbilled.
2. The actual R button regenerates ZERO with legacy EMPTY-member metadata, emitting
   only ZERO's batch and leaving EMPTY unfinalized.
3. PAID exports separately with the correct count and total.
4. Selecting EMPTY allocates no OHIP output file.

The check downloads the page's generated files and verifies records, billed status,
batch association and persisted summaries. Both the failed and successful runs
restored all six billing-table checksums, the original empty output-file inventory,
and the configuration fingerprint. Owned group providers and claims were removed.
`HOME_DIR` is derived by application startup from `BASE_DOCUMENT_DIR` and the
`carlos` context on this VM; the browser check received that verified local path.

Neighboring installed `application-health`, `billing-on-submit` and
`billing-payment-types` checks all passed without skips. The three neighboring
submission claims were also verified removed. The service reported zero automatic
restarts and approximately 1.6 GB memory usage at the end.

The VM was stopped for compilation; local builds and browser checks ran serially.
Disk space was monitored and superseded packages removed. The VM was stopped after
validation. No GitHub merge was performed. Final batch review and bot/CI convergence
are tracked separately from these local validation results.
