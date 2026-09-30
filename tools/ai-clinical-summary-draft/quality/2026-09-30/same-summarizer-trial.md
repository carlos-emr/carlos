# Reusing the single-document summarizer — September 30, 2026

The requested tool is PR [#3860](https://github.com/carlos-emr/carlos/pull/3860)'s
`balanced-reviewed` workflow: `Gateway.run_document` calls
`document_distill.run(..., protect=True)`, using Qwen3.5-35B-A3B on Parasail for
writing and reviewing. The earlier local Qwen 2B proposal experiment used a
different model and operation.

## Live result after configuration was restored

The operator restored the private OpenRouter configuration. The key check passed,
and live response metadata confirmed `qwen/qwen3.5-35b-a3b` on **Parasail**.

The existing comparison command below ran the same `balanced-reviewed` summarizer
on document 44. It produced **14 points / 434 words from 561 source words in
3.781 seconds**. Both model calls completed; exact evidence, protected-passage
and public output checks passed, and the model reviewer returned no issues.
Reported cost was **$0.0007962**. The
[saved live output](same-summarizer-live.json) can be inspected without another call.

Manual source comparison found that the generated examination point omitted the
source's absence of external head injury and normal cardiovascular, respiratory
and abdominal examination. The retained clinical sections preserved the source
wording. Passing the model review and provenance checks does not prove complete
coverage.

### Separate chart-update operation on the same provider

The summary's 14 points are not 14 chart-update proposals. A separate call to the
existing `Gateway.run_chart_updates` operation was made for each attachment,
using the same hosted model/provider and unchanged proposal prompt/schema:

| Document | Raw candidates | Accepted for review | Model time |
| --- | ---: | ---: | ---: |
| 43: 4,537-word compilation | 20 | 0 | 4.250 seconds |
| 44: 561-word note | 3 | 0 | 1.371 seconds |

Both responses failed `Invalid proposal evidence`. In document 43, candidates
2, 3 and 5 were not verbatim excerpts; all three candidates for document 44 were
non-verbatim. The model flattened multiline headings and bullets into prose,
and one large-document candidate added punctuation. The entire response was
rejected each time. No partial list was accepted.

The raw candidates also included family-member history despite its exclusion in
the prompt. The large-document response reached the 20-candidate limit and
included several inpatient follow-up plans across different dates. Provenance
repair alone would not establish appropriate classification, deduplication or
current relevance. The single-document summarizer's host-resolved citation IDs
avoid the quote-copying problem, but chart proposals currently use a separate
direct-quotation operation.

For this isolated probe only, the complete-note allow-list was extended with the
exact NHSSYN005 compilation rebuilt by the existing `local_chart_gateway.allowed_notes()`
helper from the checksum-verified corpus. Arbitrary uploaded or modified text
remained ineligible. The production gateway and summary operation were not changed.
The 37-note attachment was not sent through the single-document summarizer, for
the contract/point-limit reasons recorded below.

See [both raw proposal responses and usage](same-provider-proposals-live.json).
The follow-up made **four completed model calls**, costing **$0.00323475** in total
according to provider usage, plus the key check. It made no chart writes and did
not run a browser test or change the running CARLOS trial's local gateway setting.
This establishes that the restored hosted connection and summarizer work; the
chart-update extraction path still fails this test and is not ready on this model.

## Earlier preflight before configuration was restored

A fresh hosted run was initially blocked because the expected private configuration
at `<git-common-dir>/ai-summary-runtime/openrouter/config.json` was absent. The
operator subsequently restored it through the hidden terminal prompt. CARLOS's
running trial configuration was not changed by this investigation; it still uses
the local proposal gateway.

The [recorded September 23 evaluation](../2026-09-23/document-balanced-evaluation.json)
contains an exact source-hash match for document 44, the 561-word NHSSYN005 note:

| Measurement | Recorded result |
| --- | --- |
| Source SHA-256 | `47f35c2bca1e2d6553b2bcc0d2c87b1081f96cba441aa5aa9adf441c80f1e8db` |
| Phase | `final_ten_patients`, current implementation |
| Summary words | 402 |
| Generation time | 15.624 seconds |
| Successful API calls | 2 |
| Reported cost | $0.0007182 |
| Protected source passages verified | 10 |
| Exact evidence and public validation | Passed |

These are historical measurements, not a new inference or proof of clinical
completeness. The artifact does not contain the generated summary text or a
summary-point count, so it cannot supply a replayable response. Ten protected
passages are not ten proposed chart changes.

## Earlier offline check

The [preflight artifact](same-summarizer-preflight.json) records an invocation of
the existing gateway operation for both attachments and all 37 constituent notes.
The completion callback stopped at the first model request; it supplied no
simulated model output. Source text came from the checksum-verified synthetic
corpus, and attachment hashes matched the fixture manifest.

- **Document 44:** passes the complete-source contract, then needs hosted
  generation. Its 18 indexed passages include 10 protected passages.
- **Document 43:** the concatenated 37-note attachment is rejected by the
  existing complete-single-note allow-list before any model access. Locally
  indexing it also yields 57 protected passages, already exceeding the public
  limit of 50 summary points when this workflow emits one point per protected
  passage. Raising the separate 20 chart-proposal limit would not resolve this.
- **Individual notes:** 6 completed through the existing protected-source-only
  path, each producing one verbatim point; 31 reached the model boundary and
  remain incomplete. The six successful outputs retain all their source words.
  They are not an aggregate summary or classified chart-update suggestions.

No network/model calls, chart writes, or approval actions occurred. The source
allow-list and output limits remain unchanged. Processing the original notes
individually is compatible with the existing tool; supporting their combined
attachment requires additional design and validation.

## Verification and reproduction

All **77 focused document-summary tests passed**:

```bash
python3 -m unittest discover \
  -s tools/ai-clinical-summary-draft/tests -p 'test_document*.py'
```

The existing comparison command runs only the exact matching note, using the
current summarizer and provider:

```bash
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py \
  --patient-range 5 5 --case NHSSYN005 --modes balanced --repeats 1 \
  --output target/nhs-chart-update-morning/same-summarizer-live.json
```

This command made the two summary calls recorded above; rerunning incurs new
calls because this comparison has no output cache. A
successful summary still needs separate evaluation for chart-update selection,
duplicate handling and clinician approval; summary points are not automatically
eligible chart entries.
