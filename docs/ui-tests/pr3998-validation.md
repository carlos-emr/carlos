# PR #3998 unbilled appointment reports

Fixes #3960 and the pre-existing report defects tracked in #4031. The status filter design
comes from Chitrank Davé's Open-O PRs #134/#186 (commits 62fc595f7 and baf8e88ad).

## Review decisions

- No-Show and Cancelled appointments remain excluded by default, with independent explicit
  opt-ins; duplicated parameters keep the exclusion default. Billed and demographic-zero appointments remain excluded in all combinations.
  Bound query parameters preserve the database's case-sensitive status behavior.
- Preserve the existing three-argument DAO entry point; the new five-argument query uses
  the conventional `findUnbilledAppointments` name. Controls use current markup and localized
  labels in all five resource bundles.
- Correct appointment times in both report rows and billing URLs. The initial focused
  regression failed because `09:40:00` was rendered as a date; it passes with time formatting.
- BC omitted dates use the existing blank-date defaults. Build the BC billing URL with
  UTF-8 query encoding and JavaScript-attribute encoding, preserve nullable names/status,
  tolerate an unset billing form and pin the link to the BC billing route. Cancel the anchor
  default navigation: the BC base URL otherwise sends the report tab home when opening a bill
  or another report popup. Billing and provider-management failures were reproduced by
  installed browser checks; Begin/End calendar links share the same cause and are also fixed.
  Calendar checks exercise the actual date picker and verify its date reaches the report.
- Extend the installed browser matrix with combined `NV`/`CS` statuses, exact displayed and
  linked times, province/name parameters, omitted dates and hostile display text. All fixtures
  are owned and removed with the existing workflow cleanup.

## Validation

- Full Java suite: 13,363 tests, zero failures/errors, 51 existing skips; focused DAO,
  filter, view-model and bundle tests passed. Changed executable Java coverage: 33/33 (100%).
- Node suite: 1,034 tests passed. BDD naming, security-message, encoder, JSP taglib and
  i18n checks passed. All 982 JSPs compiled; WAR and Javadoc builds passed.
- Built all three DEBs as `2026.08.0~alpha16~pr3998.4`, installed on Ubuntu 26.04,
  and matched 6,671 packaged/installed classes and web files to tested output.
- Installed service health and all four selected Playwright checks passed: application health,
  unbilled reports, flu billing and third-party billing. The unbilled check completed all twelve
  steps: eight ON/BC status combinations, omitted BC dates, provider management, and both calendars.
- The matrix checks `t`, lowercase `c`, `N`, `C`, `B`, `NV` and `CS`; exact displayed/link times;
  province and patient-name URL parameters; names containing apostrophes, ampersands and markup;
  literal reason text with an inert image/event payload; and preservation of the report tab.
  Billing/provider popups use neutral intercepted responses to test real clicks and URLs without
  invoking a different province's editor. Date pickers load the real endpoint and return a selected
  date to the real report form.
- Third-party billing passed 30 assertions; its optional new-report Bill-link assertion reported
  no eligible row in this dataset. The changed ON/BC report links are explicitly exercised by the
  owned-fixture matrix. Flu billing passed all listed provider/year/mapping assertions.
- Installed testing caught and corrected the BC Bill and Manage Provider anchors navigating the
  report tab home. The calendar links shared the cause; the final checks verify both picker flows.
  The harness also now waits for the popup's destination URL before reading it.
- Cleanup verified removal of owned appointments, the synthetic patient and any owned report
  provider row. An additional final query found zero test-name appointments. Original VM schema
  and configuration were unchanged, and the VM was stopped. Builds and VM checks ran serially.

## Scanner review

CodeRabbit reported no actionable inline comments. Its tool summary also flagged parameter
pollution, which is covered by rejecting duplicate values. SQL interpolation in the browser
fixture uses the harness SQL string encoder and validated positive integer IDs; the filesystem
contract reads only four literal repository-relative paths, with no request input. These
scanner warnings do not identify an injection or traversal path. New deprecated markup and
the nonconventional new DAO method name were corrected rather than suppressed.
