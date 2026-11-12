# Issue #5191: End Year statement invoice selection

Validation date: 2026-10-23. All data was synthetic, on a disposable Ubuntu 26.04 container.

## Finding

No patient or date leak reproduced on the current release head in the tested cases.
The original check searches for an excluded invoice number anywhere in the extracted PDF
text with a word-boundary regular expression. A number in a phone field, date, or decimal
amount can therefore be reported as a leaked invoice. The replacement reads complete
invoice rows and compares their invoice number, date, invoiced amount and paid amount
with the expected selection, including its exact row count and order.

The statement service, its DAO's patient/PAT/date predicates and the embedded Jasper SQL
predicates are unchanged between the reported snapshot
`d029f21c47ef3cbece217909f4ea8fc98ac5686b` and release head
`1363533ec4f2c1f7cbc62388bfe3ed2f40fb1425`. Both paths bind their filter parameters.
The reported branch additionally used an explicit font style; font rendering is separate
from the invoice-selection assertion corrected here. No application query change is proposed.

## Regression coverage

- Node parser tests reject incidental number matches in patient details, dates, amounts
  and summary/page counts; preserve repeated or unexpected invoice rows across page breaks;
  and cannot invent a row from incomplete fields.
- The INR/EOY browser check seeds PAT invoices on both inclusive date boundaries, PAT invoices
  immediately before and after the window, an in-range OHIP claim, and another real patient's
  in-range PAT invoice. The selected patient's phone deliberately contains an excluded invoice
  number, proving the PDF comparison tolerates that collision. All fixture rows are cleaned up.
- The dedicated year-end browser check and the year-end part of the two-window session-isolation
  check use the same exact invoice-row comparison. The latter also checks printing a shown
  statement after editing its date field and after opening another patient's statement.
- The database/Jasper/PDF integration test now creates a real second demographic. Previously
  its other-patient invoice pointed to a nonexistent chart and could be excluded by the JOIN
  even if the patient predicate were missing.

## Build and installation

The unchanged release application's WAR was built from source with JDK 25/Maven 3.9.11.
`dpkg-buildpackage -us -uc -b` assembled CARLOS and DrugRef inside Ubuntu 26.04 using that
WAR, DrugRef built from pin `0d240f07ee5c32464060dd7eecd98f317b6b3a08`, and checksum-verified
Chromium revision `1685886`. The CLI 1.2.0 release was unpublished, so the pin's fallback
`325482a2689f60d49e07d439ce59e57a61a81ebf` was built locally as `1.2.0+git325482a`.
The CLI package build ran 1,597 tests without failures (78 expected skips).

The Debian versions below come from the unchanged packaging changelog. These are local
validation builds, not published release artifacts.

| Package | Version | SHA-256 |
| --- | --- | --- |
| carlos-emr | 2026.09.0~snapshot26 | `1ed5a13c828ee8ce848936fdb7426eb07f5648d549e74a7bcf880ca6a8a3d384` |
| carlos-emr-drugref | 2026.09.0~snapshot26 | `c42659cc74119ebe5bc52334eaa207cff3c7be64e45d8c9d2b0f8b0a28be3d0f` |
| carlos-ctl | 1.2.0+git325482a | `d36d00febe02cc143a1352792ab2e21a5c0c179bdad07cb17b071fd6bdae4597` |

The Ontario install applied 47 migrations, seeded the demonstration dataset, passed
`carlos-ctl check` (including read-only reporting grants), and passed the first-login reset
and DrugRef page checks. The unused standalone Tomcat build dependency was removed before
installing CARLOS's own service, as required by the packages' conflict relationship.

## Validation results

- 33 year-end Java unit and database/Jasper/PDF integration tests: PASS under Ubuntu 26.04's
  OpenJDK 25 and standard fonts. The minimal build image needed `libharfbuzz0b`, which the
  application installation also supplies. The outer host's custom OpenAI Sans mapping clips
  report fields; an isolated DejaVu configuration also passes all 33 tests on that host.
- Full Node script suite: 4,407 PASS, 0 FAIL, 1 existing TODO (4,408 total).
  The outer environment's default restrictive umask initially failed an unrelated executable-mode
  fixture; the complete final suite passes with CI's `umask 0022`.
- Original workflow against the unmodified installed release application: PASS (all 12 steps).
- Controlled collision: the original check fails with its exact reported message,
  `The PDF lists an invoice outside the PAT/date selection`, although the PDF contains only the
  two selected invoice rows. The excluded invoice number occurs in the phone field. A visual
  inspection of the captured PDF independently confirmed both facts.
- INR/EOY and dedicated year-end Playwright checks: 2 PASS, 0 FAIL, 0 SKIP, no residue.
  The strengthened INR/EOY check passed with the controlled phone/invoice-number collision,
  real other-patient invoice, and both date boundaries and adjacent exclusions.
- Two-window session-isolation workflow: all 14 steps PASS, including printing each patient's
  own year-end invoice after the other patient's search and after an unsaved date-field change.
  The first residue audit reported the application's first-use reference data: 63 measurement
  types, 44 validations (documented in `deb-install-validation.md`), and the provider's default
  quick-list preference (`dxQuickListItemsHandler.init`). A repeat left no residue but hit a
  picker-close timing race in the earlier messenger step (`locator.click: Target page, context
  or browser has been closed`). The final complete run passed all 14 steps again with no residue,
  failures or skips; no messaging application code was changed.
- Server-log audit from the start of installed browser testing: PASS.

Browser commands run inside the installed container using its shipped Chromium and Poppler,
with the package's HTTPS front door and generated/reset synthetic administrator login.

```sh
node scripts/run-playwright-suite.js --only billing-on-reports-inr-eoy --only billing-on-year-end-statement \
  --residue-audit --junit /tmp/issue-5191.xml
node scripts/run-playwright-suite.js --only session-state-two-window-isolation \
  --residue-audit --junit /tmp/issue-5191-session.xml
```

The controlled baseline runs the original workflow with its excluded invoice number placed
in the owned synthetic patient's phone field. It captures the PDF's invoice rows before
the original assertion, so the false match can be distinguished from an actual extra row.
