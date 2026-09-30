# Empty-chart trial — September 30, 2026

## Live result

A real **Qwen 3.5 2B** model is now connected through local Ollama in the isolated
morning trial at <http://localhost:8082/carlos/>. The live run completed with these
results; the chart remained empty and nothing was approved:

| Document | Words | Returned by model | Passed source validation | Types returned | Model time |
| --- | ---: | ---: | ---: | --- | --- |
| 43: all 37 notes | 4,537 | 1 | 1 | 1 history, 0 reminders | 1,499.5 seconds |
| 44: longest note | 561 | 1 | 0 | 1 history, 0 reminders | 73.4 seconds |

The large-file suggestion is an exact quotation of the entire first triage note,
including its separator and staff details. It does not separate individual facts
or cover later notes. For example, the document explicitly records a subdural
hygroma and requests neurology follow-up in two weeks and GP follow-up in seven
days, but the model proposed none of these. The 20-proposal ceiling was not reached.
Passing quotation checks does not establish useful categorization or completeness.

The smaller-file suggestion rewrote and joined source lines, including changing
`pprogressive` to `progressive`, so the whole proposal failed exact-source
validation and never appeared as an approvable card. These are extraction-quality
failures, not missing connectivity. This local model has not passed the intended
workflow's quality bar. The hosted model configuration is still absent; this is
not a test of the earlier hosted Qwen 27B configuration.

See the [complete model outputs and metadata](local-model-results.json),
[real browser result](local-model-browser-result.json), and
[large-file modal screenshot](local-model-large-modal.png).
The browser verified original/extracted text, the empty comparison panel, source
highlighting, unchecked approvals, unchanged record counts, Back/Close navigation
and released editing locks. It exits 2 because the smaller document's generation
was rejected. No paid API calls were made. Two model calls completed; an earlier
large-file attempt was stopped while correcting a generic CPU build to use AVX2.
The reported times are observations on a busy shared CPU, not controlled benchmarks.

The [cached browser rerun](local-model-cached-browser-result.json) displayed the
large-file result in about 1.0 seconds without another model call.
Both outputs are cached, including the rejected output. Reopening the same source
uses that response without another inference call. No application validation or
approval guard was relaxed. Twelve focused gateway/contract tests and CI on the
connection/test checkpoint passed; these do not imply that the model's clinical
extraction quality passed.

## Initial fixed-gateway attempt

The chart and attachments were prepared before the live model was available.
At that stage both generation attempts were rejected by the fixed gateway; their
unavailable counts below are historical and are not valid zero-suggestion results.

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

## What prevented the initial count

The hosted gateway configuration at the Git common directory's
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
highlighting through every modal step. The large-document live run exercised this path; the smaller document exercised
rejection.

`browser/prepare_empty_chart.py` (relative to the prototype directory) recreates
these two documents and the empty patient in a fresh morning database copy. Run
it from the repository root after preparing the private morning configuration.
It checks the exact isolated database name and refuses an existing AIFACT005 chart;
it never clears charts or overwrites an earlier trial. Do not rerun it against the
prepared chart. The source texts are rebuilt from the committed synthetic corpus,
so no duplicated clinical source file or credentials need to be committed.

## Live local-model connection follow-up

The follow-up uses the prototype's supported `qwen3.5:2b` model through local
Ollama. [Ollama's model page](https://ollama.com/library/qwen3.5) and
[Linux installation instructions](https://docs.ollama.com/linux) are the upstream
sources. This is a separate model evaluation from the earlier hosted Qwen 27B work.
The application and proposal prompt/schema remain unchanged, including the limit
of 20 proposals.

`browser/local_chart_gateway.py` accepts complete checksum-verified corpus notes
and the exact 37-note NHSSYN005 compilation rebuilt from that corpus. Its only
model endpoint is numeric loopback; it refuses cloud-backed Ollama models. It
validates the request before model access and the returned evidence before CARLOS
receives any proposals. It stores raw responses (including rejected outputs) and
validated results under an ignored local cache, keyed by model digest, runtime
version, source, prompt, schema and generation options. Reopening or retrying an
unchanged request reuses the result. Existing chart content is not sent.

To run the gateway with an already installed local model:

```sh
OLLAMA_HOST=127.0.0.1:11434 OLLAMA_NO_CLOUD=1 ollama serve
# In another terminal:
OLLAMA_HOST=127.0.0.1:11434 ollama pull qwen3.5:2b
python3 tools/ai-clinical-summary-draft/browser/local_chart_gateway.py \
  --cache target/nhs-chart-update-morning/local-model/cache
```

The isolated trial uses HTTP port 11439, request budget 50,000 bytes, timeout
1,800 seconds and agent label `Qwen 3.5 2B - local model (cached)`. Only its private
configuration and Tomcat are changed. The earlier fixed gateway remains available
on 11438. The local model uses 16,384 context tokens, 4,096 output tokens,
temperature zero, thinking disabled and four CPU threads. Ollama is version 0.35.0;
its CPU engine was built from the matching release's llama.cpp b11081 with
AVX2/FMA/F16C/SSE4.2 enabled. No production application code changed for this trial.

The prepared runtime is local to the trial directory, rather than installed on
PATH. To restart it after stopping its existing process:

```sh
TRIAL_MODEL="$PWD/target/nhs-chart-update-morning/local-model"
OLLAMA_HOST=127.0.0.1:11434 OLLAMA_NO_CLOUD=1 \
  OLLAMA_MODELS="$TRIAL_MODEL/models" OLLAMA_NUM_PARALLEL=1 \
  OLLAMA_MAX_LOADED_MODELS=1 OLLAMA_MAX_QUEUE=1 \
  "$TRIAL_MODEL/bin/ollama" serve
```

Model weights, compiled binaries, private configuration and cache stay outside Git.
The synthetic model outputs and trial evidence are committed for review.
