# Scratchpad save and recovery workflow

Run `npm run test:scratchpad-workflow-playwright` with the standard live harness
configuration (`BASE_URL`, login credentials, MySQL settings and `CHROME_PATH`).
The manifest name is `scratchpad-workflow`, in the `core` tier. Use an isolated
synthetic test login, with no other tester editing its scratchpad during the run.

The check enters through the schedule Scratch Pad icon. It saves and reopens text
containing literal plus signs, percent sequences, HTML entities, newlines and
whitespace; advances the browser clock to exercise autosave; deliberately clears
the note; and opens/closes version history with an unsaved draft still present.
A routed HTTP 503 tests failure display and Retry through the editor. Two actual
logged-in browser contexts test stale-revision rejection, retention of the local
draft, blocked autosave after conflict, and opening/reconciling the current note.
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

Validation status at PR creation: source-level reproductions confirmed the old
failures and the Node regression tests pass. Local Java and live Ubuntu 26.04
installed-DEB execution remain pending adequate host disk space. This document
does not claim a completed live run until those results are recorded in the PR.
