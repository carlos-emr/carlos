# PR #3995 prevention validation

Validated on 2026-09-27 against `release/2026.08` at
`25a867f07b9bda663aaa70aab8e5de225605f999`. Related issues: #3975 and #4023;
epic #3969. The original row-link port is attributed to Deval Italiya,
openo-beta/Open-O #2508.

The prevention panel selects the newest clinical date across primary and merged
histories, with a numeric record-ID tie-breaker. Undated records cannot inherit
another row's displayed date. Both row and heading popups register panel-only
refreshes, preserve unsaved notes, and retain working reload URLs across repeated
refreshes. Read-only providers retain the full-list route.

The existing clinical save path now rolls back the record, extension fields and
partial-date metadata together. Failed replacements retain their predecessor.
Save/delete ownership accepts a current merged child but rejects unrelated or
deleted records. Interactive saves reject missing or impossible dates; CDS imports
retain support for an unknown date. Failed saves return an unsuccessful response
and preserve the form instead of closing as though successful. Clinical payload
and field logging was removed from the save action.

## Automated validation

- Full Java unit/integration suite: **13,374 tests, zero failures/errors, 51 skips**.
- Final focused prevention display/action/database suite: **41 passed**. This
  includes real transaction rollback after an extension write, successful atomic
  replacement, partial dates, unknown-date import compatibility, merged ownership,
  rejected deletes, invalid dates, and visible save failures.
- Changed Java executable lines: **119/121 covered (98.3%)**; only the Spring
  no-argument display-action constructor remains uncovered.
- Full Node suite: **1,036 passed, zero skipped**; final popup-helper rerun: six passed.
- Encoder null-safety, BDD naming and i18n key/encoding checks: passed.
- **982 JSPs**, WAR packaging and Javadocs: passed.

## Installed Ubuntu 26.04 validation

Built all three DEBs as `2026.08.0~alpha16~pr3995.2` with one packaging worker
and the LXD VM stopped. Installed them into `carlos-val` using the existing Ontario
test database. **6,666 class/web payload hashes** matched both the package and
installed files. `carlos-ctl check` passed before and after browser tests.

`echart-prevention-row-links` passed seven scenarios through the HTTPS front door:

1. Each writable row opens its prevention or the appropriate CVC disambiguation route.
2. Row save retains the unsaved encounter note and refreshes only the panel.
3. A recorded row opens the selected record with its clinical date.
4. Heading-list edit/close refreshes the panel without replacing the chart.
5. The plus-link list behaves the same across another edit and refresh.
6. Existing unrelated-patient, invalid-record and impossible-date requests return
   HTTP 400 and leave the clinical history unchanged.
7. Merged histories choose the newest date, then numeric ID for ties; a merged
   child can be amended from its parent chart with partial-date metadata intact.

Five existing neighboring checks passed: `prevention-lifecycle`,
`prevention-add-data`, `prevention-brand-picker`, `echart-navbar-modules`, and
`echart-note-editor`. The navbar check opened 11 module links and retained its
13 policy exclusions. Read-only routing is covered by Java display/gate tests.

An earlier installed run exposed the missing reload URL after a heading refresh
(HTTP 405 on a malformed panel request). That failure was fixed, rebuilt, and the
complete target scenario rerun successfully. Another earlier probe failed before
sending a request because a named submit control shadowed the form's `action`
property; the probe now reads the actual attribute.

Owned patient, merge, prevention, extension, partial-date and draft fixtures were
cleaned up. The VM was stopped after final health checks. Final VM root free space:
4.5 GiB. Package hashes and detailed logs are retained in the session's
`pr3985-4000-evidence` directory; no credentials or patient data are included here.
