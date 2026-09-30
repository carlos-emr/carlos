# Reusing the single-document summarizer — September 30, 2026

The requested tool is PR [#3860](https://github.com/carlos-emr/carlos/pull/3860)'s
`balanced-reviewed` workflow: `Gateway.run_document` calls
`document_distill.run(..., protect=True)`, using Qwen3.5-35B-A3B on Parasail for
writing and reviewing. The earlier local Qwen 2B proposal experiment used a
different model and operation.

## Result

A fresh hosted run remains blocked: the expected private configuration at
`<git-common-dir>/ai-summary-runtime/openrouter/config.json` is absent. The
operator has been asked for its location or restoration. No credentials should
be pasted into chat or committed. CARLOS's running trial configuration was not
changed by this investigation; it still uses the local proposal gateway.

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

## Current offline check

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

## Verification and next step

All **77 focused document-summary tests passed**:

```bash
python3 -m unittest discover \
  -s tools/ai-clinical-summary-draft/tests -p 'test_document*.py'
```

Once the private configuration is restored, the existing comparison command can
run only the exact matching note, using the current summarizer and provider:

```bash
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py \
  --patient-range 5 5 --case NHSSYN005 --modes balanced --repeats 1 \
  --output target/nhs-chart-update-morning/same-summarizer-live.json
```

This command makes paid calls and has not been run during this follow-up. A
successful summary still needs separate evaluation for chart-update selection,
duplicate handling and clinician approval; summary points are not automatically
eligible chart entries.
