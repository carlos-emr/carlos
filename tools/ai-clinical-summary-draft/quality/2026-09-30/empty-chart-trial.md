# Empty-chart trial — September 30, 2026

The test chart and documents are ready in the isolated morning CARLOS instance at
<http://localhost:8082/carlos/>. **The model suggestion count is not available.**
Both generation attempts were rejected by the currently configured fixed gateway;
this is not a valid result of zero suggestions.

Search for **FAKE-EMPTY-CHART**, chart **AIFACT005**, patient **3051**. Open eChart,
choose **Review chart updates**, then choose a document:

| Document | Content | Words | Characters | Model suggestions |
| --- | --- | ---: | ---: | --- |
| 43 | All 37 source notes from synthetic NHSSYN005, with dated separators | 4,537 | 30,037 | Unavailable |
| 44 | Longest complete clerking note from synthetic NHSSYN005 | 561 | 3,624 | Unavailable |

Only basic demographics and the two attachments were added. Notes, reminders,
medications, allergies and approval receipts were all zero before and after the
browser check. Existing patients were unchanged. No AI calls or approvals occurred.
The original NHS fixture remains intact. Source bodies retain their original
spelling, encoding and clinical inconsistencies.

The browser entered from patient search and eChart, opened the review modal,
verified both original downloads and every character of the extracted source,
confirmed an empty chart-comparison panel, and attempted generation. Back and
Close retained the parent eChart and tab count. The editing lock was released.
The [machine-readable result](empty-chart-result.json) records both failures and
baseline/final counts. Screenshots are in the local ignored trial browser folder.

## What prevents the count

The live gateway configuration at the Git common directory's
`ai-summary-runtime/openrouter/config.json` is absent. No model was listening on
local ports 11434, 11436 or 11437. Port 11438 is the fixed NHS integration gateway;
it recognizes only the three earlier documents and always returns three manually
selected proposals for each. It correctly rejected these new sources. Those fixed
three-item results cannot answer how many suggestions a model would produce.

The existing live Python gateway accepts only complete individual committed
synthetic notes. Document 44 already meets that rule. Document 43 would need a
narrow, checksum-verified allowance for this exact synthetic compilation before
using that hosted gateway. No disclosure checks were weakened for this trial.

The current proposal contract permits **at most 20 suggestions** per document.
It selects diagnosis/history and explicit future follow-up; it does not inventory
all clinical facts. The current chart is not sent to the model, so an empty chart
does not change extraction. Duplicate checks happen locally during review/save.
For the compiled document, relative dates can refer to different source-note
dates: the current default-date code uses the attachment's single observation date
(January 9). Those defaults need review before any approval.

## Reproduce or continue

The local `target/nhs-chart-update-morning/empty-chart-fixture.json` records patient
and document IDs, paths and hashes without credentials. To rerun the browser check
with the private morning wrapper:

```sh
CHART_EMPTY_FIXTURE=target/nhs-chart-update-morning/empty-chart-fixture.json \
  python3 target/nhs-chart-update-morning/run-browser.py \
  tools/ai-clinical-summary-draft/browser/empty_chart_check.cjs
```

The runner records a generation failure as `proposals: null` and exits 2 when the
model cannot generate. It never approves changes; it requires an empty chart.
When generation succeeds, it counts history/reminder cards and checks source
highlighting through every modal step. That success path has not been exercised
in this trial.

`browser/prepare_empty_chart.py` (relative to the prototype directory) recreates
these two documents and the empty patient in a fresh morning database copy. Run
it from the repository root after preparing the private morning configuration.
It checks the exact isolated database name and refuses an existing AIFACT005 chart;
it never clears charts or overwrites an earlier trial. Do not rerun it against the
prepared chart. The source texts are rebuilt from the committed synthetic corpus,
so no duplicated clinical source file or credentials need to be committed.
