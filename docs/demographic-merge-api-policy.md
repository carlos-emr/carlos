# Demographic merge API policy

The REST merge API changes the demographic link graph. Both merge and unmerge
require `_demographic w` for the role and every participating patient at the
manager boundary. The same patient locks, transaction and strict audit writer
cover validation and link changes. Each changed row records the authenticated
provider and current date using the existing table column types.

Unmerge selects current (`deleted = 0`) links only. It checks every unique
requested pair before changing any link; a pair with no active link raises a
conflict, mapped to HTTP 409. The whole transaction rolls back if a link write
or strict audit fails. Historical rows keep their original actor/date, and
repeated unmerge requests do not create new success audit events. The historical
DAO lookup remains available to existing history consumers.

## Chart permissions

The API retains its existing separation between merge links and chart access
rules: it neither creates nor removes `_all` / `_eChart$<child>` privilege rows.
The administrative merge workflow additionally creates a read-only chart rule
when absent; its unmerge workflow removes that rule. This difference is now an
explicit API policy, rather than an unrecorded side effect of separate paths.
Changing graph links through REST does not grant write access or discard a
patient-specific restriction configured through another workflow.

The report's claim that only legacy `encounter/Index2.jsp` consumes that rule is
incorrect. Modern chart/module access also checks `_eChart$` objects, and the
`|or|` privilege means **only read**, not no access. Removing such a row can
therefore restore writes. Existing rows have no creator/ownership marker to
distinguish an administrative merge rule from an independently configured
restriction; identical privilege text cannot establish provenance. Automatic
cleanup based solely on its name/value would risk changing valid access rules.

Use the existing administrative workflow when its chart permission behavior is
required. A future unified behavior needs explicit rule ownership and an
upgrade policy for existing rows before API unmerge can safely delete generated
rules. This PR deliberately preserves the API's established chart-permission
behavior while correcting authorization, link state, actor/date and truthful
success outcomes. It makes no claim that an API merge renders the source chart
read-only.

This policy dispositions the privilege-row questions in
[#6084](https://github.com/carlos-emr/carlos/issues/6084) and
[#6079](https://github.com/carlos-emr/carlos/issues/6079), carried from
[#5830](https://github.com/carlos-emr/carlos/issues/5830). Physical/installed
qualification and final PR review remain separate acceptance gates.
