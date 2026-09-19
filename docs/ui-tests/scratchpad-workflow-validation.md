# Scratchpad save and recovery workflow

Run `npm run test:scratchpad-workflow-playwright` with the standard live harness
configuration (`BASE_URL`, login credentials, MySQL settings and `CHROME_PATH`).
The manifest name is `scratchpad-workflow`, in the `core` tier. Use an isolated
synthetic test login, with no other tester editing its scratchpad during the run.

The check enters through the schedule Scratch Pad icon. It saves and reopens text
containing literal plus signs, percent sequences, HTML entities, newlines and
whitespace; waits for the real interval to exercise autosave; deliberately clears
the note; and opens/closes version history with an unsaved draft still present.
A routed HTTP 503 tests failure display and Retry through the editor. A second
probe lets the save commit before replacing its response with 503, then checks
that Retry acknowledges the committed version without inserting a duplicate. Two actual
logged-in browser contexts test stale-revision rejection, retention of the local
draft, blocked autosave after conflict, and opening/reconciling the current note. A browser-request barrier also releases
two UI saves together and requires exactly one committed version and one visible
conflict.
Only the exact expected negative HTTP responses are consumed; other browser
errors still fail the check. The routed failure is not a simulated server outage.

Each saved version is checked against the database. Marker-owned versions and
acknowledged empty versions are removed on completion. The provider's original
history IDs, text hashes and statuses must match the initial snapshot after
cleanup; the check fails if unexpected data remains or fixture ownership changes.
It does not delete or rewrite pre-existing scratchpad versions.

Related defect: #3767. Server saves now compare revisions atomically under a
provider-row lock, including the first save. Text in the JSON response is literal;
only the HTML rendering boundaries encode it. A stale request receives HTTP 409
without advancing the editor revision. The user keeps their draft and can open
the current scratchpad to compare and combine notes. A save failure retains the
editor and offers Retry; it does not reload away the unsaved text.

Validated on Ubuntu 26.04 / 8 GiB with the installed validation12 DEBs. All
seven live steps above passed, including real-interval autosave, lost-response
retry, stale-window rejection and simultaneous saves. The baseline installed
DEB reproduced skipped literal edits, false saved status on stale writes and
editor loss on failure before the fix.

The integration tree containing the isolated release fixes passed 12,458 Java
tests (zero failures/errors, 51 skips), 771 Node tests and 1,587 packaging/CLI
tests. Focused coverage includes 61 Java action/DAO tests, 21 editor cases and
7 browser-helper cases. Form submissions normalize LF to CRLF, so the editor
normalizes only acknowledgment line endings before comparing textarea text;
all other whitespace and literal characters remain significant.

All three matched packages installed with Flyway validation and preserved clinic
counts and credentials. Builds ran with the VM stopped under memory guards;
browser tests ran serially. CI and reviewer follow-ups are recorded in #3768.
