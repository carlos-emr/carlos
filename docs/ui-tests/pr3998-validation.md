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
  or the provider-management popup. Both defects were reproduced by installed browser checks.
- Extend the installed browser matrix with combined `NV`/`CS` statuses, exact displayed and
  linked times, province/name parameters, omitted dates and hostile display text. All fixtures
  are owned and removed with the existing workflow cleanup.

## Validation

Final full-suite, package and installed browser results will be recorded after completion.

## Scanner review

CodeRabbit reported no actionable inline comments. Its tool summary also flagged parameter
pollution, which is covered by rejecting duplicate values. SQL interpolation in the browser
fixture uses the harness SQL string encoder and validated positive integer IDs; the filesystem
contract reads only four literal repository-relative paths, with no request input. These
scanner warnings do not identify an injection or traversal path. New deprecated markup and
the nonconventional new DAO method name were corrected rather than suppressed.
