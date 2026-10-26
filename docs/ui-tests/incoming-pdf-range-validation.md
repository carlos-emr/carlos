# Incoming PDF extraction range validation

Issue #3777: cancelling the extraction prompt passed `null` to `trim`, and a range such as `1-99999999999` expanded toward the submitted upper bound even after exceeding the document's page count. This could freeze the browser and consume memory.

The editor now returns immediately on Cancel. It validates numeric, safe-integer, ordered endpoints against the page count before expanding any range. Empty and whole-document selections produce the existing translated validation alert. Comma-separated ranges and duplicate selected pages remain supported.

Run the behavioral tests against the actual JSP handler:

```sh
node --test scripts/incoming-pdf-range-regression.test.js
```

All 22 cases pass. The same tests against release baseline `e6738a429a6c` produce five failures, including Cancel, the bounded oversized-range timeout, and a reversed range. The negative control uses a 64 MiB Node heap and a short execution deadline; never enter the oversized range into an unfixed production browser or the validation VM.

Live coverage is being added in PR #3773 alongside incoming-PDF integrity and request-gate workflows. Installed-DEB validation of this fix is still pending; the current validation12 package predates it. This change adds no database migrations or runtime dependencies.
