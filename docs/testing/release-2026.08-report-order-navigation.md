# Report ordering browser navigation

The installed `b4048148a18a26efbd2bc4173336d693f5270672` qualification failed
`report-typed-queries` at "report field move rendered a blank page". Its HTTP
responses were successful. The workflow waited for `framenavigated`, which fires
at document commit, then immediately read the body before parsing completed.
The failed run and its JUnit report are retained; it is not counted as passing.

A separate diagnostic run observed six ordering documents in `loading` state,
with zero body text and zero ordering inputs, followed by completed documents
with 51 body characters and all three inputs. That diagnostic run passed; the
extra observation changed its timing and does not replace the original failure.

The move operation now waits for `domcontentloaded` after the main-frame
navigation. It retains every existing blank/error-page, stored metadata, stored
ordering, and rendered ordering assertion. No delay or assertion waiver is used.

Six deterministic controls execute the workflow's actual nested move operation
with document commit preceding DOM readiness. They fail before the repair and
pass afterward. Negative controls retain refusals for a completed blank page,
an error page, changed metadata, wrong stored order, and wrong rendered order.

All 8,117 complete Node cases pass on Node 22.23.5, with zero failures, skips,
cancellations, or TODOs; all 571 test-file fingerprints remain unchanged. The
tracked case inventory is generated from that actual run. Report SHA-256:
`7ad0f731329f3d6bf3342acb0ebe532961c0cc0d2ab2151681accca3b097d11d`.

The repaired installed Chromium 154.0.8025.0 workflow passes every step with
strict release mode and fixture residue audit. Only this test entrypoint was
replaced; the installed application remains the exact `b4048148` package.
Native JUnit SHA-256:
`af58271bf1b439c57030171e69b91e06c170cc782e27b2092eeeeda17dbfe506`.
