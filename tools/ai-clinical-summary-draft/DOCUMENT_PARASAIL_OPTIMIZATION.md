# Parasail document summarizer optimization — 2026-09-23

This records the earlier passage-cited paraphraser. The current gateway uses the subsequent
[extractive fidelity design](DOCUMENT_FIDELITY.md), which also supplies summary wording from the source.

The bundled CARLOS EMR gateway now asks Qwen3.5-35B-A3B on Parasail to select numbered source
passages instead of generating quotations. The gateway inserts the exact original passages
into the existing response contract. This removes a copying task that caused frequent rejections
and consumes fewer generated tokens. The Java validator, provider, model, privacy routing,
single completion, 4,096-token cap, and reasoning-disabled setting remain unchanged.

## Fresh paired comparison

Each of 12 complete synthetic notes was tested twice with each approach: 48 requests. Six notes
were the development cases from the earlier model comparison; six additional notes were selected
before testing and evaluated after the final paragraph/prompt design was frozen. Model order was
alternated. No output cache, repair pass, or fallback was used. Provider input-prefix caching may
still occur. All measured calls returned from Parasail.

| Measure | Copied quotations | Resolved passage IDs |
| --- | ---: | ---: |
| Accepted drafts, development notes | 7/12 | 12/12 |
| Accepted drafts, separate evaluation notes | 10/12 | 12/12 |
| Accepted drafts, combined | 17/24 | 24/24 |
| Median latency, combined | 4.26 s | 2.95 s |
| Generated tokens, combined | 15,684 | 9,549 |
| Reported cost for 24 attempts | $0.0179625 | $0.0123480 |

This measured about **31% lower median latency**, **39% fewer generated tokens**, and **31% lower
cost**. Fresh baseline results differ slightly from the earlier comparison; hosted outputs and
latency vary even at temperature zero. The small sample does not establish a reliability guarantee.
[Recorded metrics and prompt snapshots](quality/2026-09-23/parasail-document-optimization.json)
include each attempt, source hashes, and hashes of the private raw reports.

A [subsequent test on ten new patients](DOCUMENT_TEN_PATIENT_EVALUATION.md) passed all 20
provenance checks, but exposed additional errors in treatment status and pending-result coverage.

## Implementation and boundaries

- Paragraphs retain nearby headings, exact spelling, encoding, and internal whitespace. This keeps
  “Medications / Nil” distinct from “Allergies / Nil”. Long paragraphs are split at whitespace when
  possible, within the Java host's 800 UTF-16-unit excerpt limit; source text is not corrected.
- The provider schema enumerates host-generated passage IDs. The resolver independently rejects
  unknown, incorrectly typed, excessive or repeated IDs and unexpected fields. The existing Python
  and Java validators still check the resolved excerpts and generated text.
- The entire original note must match a committed synthetic fixture before request construction
  or network access. The transformed request, including the ID schema, must also fit the byte budget.
  Caller-provided prompts, schemas, or extra metadata cannot override this behavior.
- The provider prompt asks for concise clinical points, preserves planned/completed distinctions,
  and discourages names, unsupported interpretations, and unacknowledged contradictions.
- There is no fuzzy matching, quote repair, or extra model call. Reasoning remains disabled.
  The optimization is in the bundled OpenRouter gateway; the local Ollama adapter is unchanged.

Exact citation text is now obtained by construction. **That does not prove that the citation
supports the claim**, and broader paragraph citations can contain irrelevant or identifying text.
This is neither de-identification nor clinical validation.

## Manual review and remaining limitations

Review of the first repeat against all 12 source notes found useful preservation of medication
doses/frequencies, “if tolerated”, pending imaging, and tentative discharge. On the multiline
development note, the selected candidate described acceptance for transfer rather than claiming
transfer was completed. On the separate notes it retained nimodipine 30 mg every four hours,
sodium 132 mmol/L, preoperative consent/fasting, and the planned thromboprophylaxis/analgesia.
It retained source measurements such as “BP 120/7”, “HR 2”, and “RR 1” rather than repairing them.

However, actual errors remain despite the stronger prompt: some outputs included clinician names;
the multiline note's contradictory nausea statements were not explicitly highlighted; CRP 8 mg/L
was called normal without that specific classification in the source; and the ambiguous “no OB”
was expanded to shortness of breath. These drafts can pass provenance validation. The acceptance
improvement must not be described as a clinical accuracy score. The feature remains experimental
and requires review against the original document.

## Reasoning experiment

A separate probe requested a 1,024-token reasoning budget with reasoning excluded from the returned
message. It instead reported 3,903 reasoning tokens, exhausted the 4,096-token total limit, and
returned no usable draft after 18.6 seconds. We did not adopt this setting. An initial probe had the
same output-limit failure; the recorded repeat above excludes reasoning text entirely. See
[OpenRouter's reasoning documentation](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens)
for the distinction between reasoning and total output budgets. The observed provider behavior
is the reason to retain reasoning-disabled requests here.

The first development candidate used individual lines; it was discarded because identical “Nil”
lines lost their headings and produced duplicate excerpts. Its results and the reasoning probes
are excluded from the final paired comparison.

## Reproduce and verification

These commands use the existing private credential file, incur API charges, and write private
reports containing only the committed synthetic source text, outputs, and usage metadata:

```bash
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py \
  --output /path/to/private/development.json
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py --holdout \
  --output /path/to/private/holdout.json
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -p 'test_*.py'
```

All 119 Python tests passed, including malformed/fabricated references, duplicate references,
Unicode excerpt limits, heading preservation, expanded request budgets, synthetic disclosure
checks, and HTTP response compatibility. All 48 saved outputs were independently rechecked against
the final resolver and validator. After refreshing an expired development login and starting the
updated gateway, the live browser check passed: 5 points in 2.53 seconds, expandable original-text
evidence, POST-only generation, CSRF rejection, invalid-ID rejection, no-store responses, retained
extraction notices, and no page errors. The local gateway is running the updated implementation.
Other installations must restart the bundled gateway; no Java rebuild or property change is needed.
