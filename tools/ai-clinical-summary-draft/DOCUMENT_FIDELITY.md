# Improving document fidelity with source-selected summaries

The Parasail/Qwen3.5-35B-A3B gateway now returns an **extractive summary**. The model chooses
passage IDs; it cannot supply clinical prose. The gateway builds the points and overview from
original source wording and returns the unchanged Java `overview`/`points`/`evidence` contract.
This is a deliberate tradeoff: more source text and less polished prose, with no model-authored
changes to doses, uncertainty, negation, or whether an action is planned or completed.

The earlier ten-patient evaluation found a draft that turned planned antibiotics and fluids into
completed treatment and invented a transfer. A prose-based revision removed those overview errors
but added an unsupported “no fever” statement in a detail point. Stronger instructions and a derived
overview did not reliably address unsupported wording, so that candidate was not adopted.

## What changed

- The provider response schema permits only `selected_ids`. Unknown, malformed or repeated IDs,
  additional fields, truncation and refused responses are rejected. There is no model prose to
  repair, normalize, or accept on lexical overlap alone.
- Recognizable medication, allergy, investigation/result, impression/diagnosis, referral/disposition,
  and plan sections are retained even if the model omits them. Explicit pending/awaiting wording and
  inline allergy labels also trigger retention. These are bounded lexical rules, not a clinical parser.
- Selecting any chunk of a long paragraph retains the entire paragraph. This prevents a length split
  from separating a statement from its heading, negation, continuation, or condition. Individual
  excerpts still fit the Java host's 800 UTF-16-unit limit; too many points fail closed.
- Points remain in source order. Only exactly repeated display text is deduplicated; similar text with
  different punctuation/values is not silently collapsed. The existing final validator still applies.
- The overview reuses a diagnostic/problem passage and, when available, a plan passage. It introduces
  no separate narrative. CRLF and lone CR become display line breaks, never concatenated values;
  evidence retains the original substring. The page preserves line breaks and wraps long text.
- The complete synthetic-note allow-list, incoming Java prompt/schema checks, provider pinning,
  ZDR/data-collection restrictions, byte budget, disabled reasoning, and no output cache are unchanged.
  Local Ollama and custom gateways are not changed by this bundled-gateway implementation.

## Fresh-patient comparison, 2026-09-23

The final design was compared with the former passage-cited paraphraser on the longest note from
each of **NHSSYN024–NHSSYN033**, twice each, with model order alternated. These ten patients had not
been used to design the change. Both modes used the same Parasail model and generation settings.

| Measure | Former paraphraser | Extractive version |
| --- | ---: | ---: |
| Passed provenance/format validation | 20/20 | 20/20 |
| Median latency | 4.73 s | 0.80 s |
| Generated tokens, 20 drafts | 12,761 | 816 |
| Reported cost, 20 drafts | $0.0162935 | $0.0037026 |

Median latency decreased about 83%, generated tokens about 94%, and reported cost about 77%.
One extractive attempt encountered a bounded rate-limit retry; its delay is included (extractive
range 0.66–3.35 s). Upstream input caching may occur. There is no output-repair pass. These small
measurements are not a latency guarantee or a clinical accuracy score: both modes passed the
existing validator, despite the prior paraphraser's observed semantic errors.

Final regression checks additionally covered NHSSYN004–NHSSYN023 once each and the original six
notes from NHSSYN001–003. **All 46 final extractive drafts passed**. Every draft was independently
reconstructed from its saved IDs and checked for exact source wording, required-passage coverage,
and the unchanged output validator. [Metrics and source hashes](quality/2026-09-23/document-fidelity-evaluation.json)
preserve the final evaluations; private raw reports retain the complete synthetic sources/outputs.

## Specific failures addressed

- NHSSYN011 retains “Start IVceftriaxone”, “Commence IV 0.9% sodium chloride”, and “Liaise with the
  geriatric team” under the original Plan heading, plus the pending urine culture. No invented
  awaiting-transfer status, age classification, or already-started treatment is generated.
- NHSSYN008 retains the pollen allergy, pending D-dimer, planned CTPA/echo and the original blood
  values. The gateway does not add a “normal” interpretation to unlabeled values.
- An intermediate new-patient check found that a completed-transfer referral paragraph could be
  omitted. Referral/disposition retention was added, then the design was tested on the separate
  NHSSYN024–033 group above. NHSSYN015's completed transfer now remains alongside its source plan.
- The original multiline assessment retains both conflicting nausea statements without rewriting
  either. It does not diagnose or explain the contradiction.

## Limits and tradeoffs

On the final fresh-patient set, point text retained a median **82% of source characters**. Some short
documents are reproduced almost entirely. This prioritizes fidelity over compression; it should not
be represented as an equally concise replacement for the former prose summarizer.

Source typos, erroneous doses, contradictions, and embedded names/identifiers remain visible.
Identity-only passages are discouraged in selection, but this is not de-identification. Recognized
sections are forced into the output; content outside those patterns can still be omitted. Selecting
and reordering source passages does not establish completeness, clinical correctness, or safe care.
The output remains an unverified draft to review with the original.

## Verification and reproduction

All **130 Python tests passed**, including unknown/injected selections, mandatory coverage despite
model omission, referral status, long-paragraph continuations, contradictory source wording,
Unicode/CRLF/lone-CR handling, duplicate-dose boundaries, disclosure checks and byte budgets.
Paragraph grouping was also checked against all 1,602 committed corpus notes. Encoder lint and
JavaScript syntax checks passed. The local browser check returned five points in **1.36 s**, with
preserved line breaks, escaped text, expandable evidence, CSRF rejection, POST-only generation,
invalid-ID rejection, no-store responses, extraction warnings and no page errors.

```bash
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py \
  --patient-range 24 33 --modes references fidelity --repeats 2 \
  --output /path/to/private/fidelity-comparison.json
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -p 'test_*.py'
```

`references` reproduces the former paraphraser; `fidelity` selects the current extractive mode.
The command incurs API charges using the existing private credential file. Restart the bundled
gateway and deploy the updated JSP/CSS to use the change elsewhere. The local development instance
already runs the update; model/provider settings and committed feature-flag defaults are unchanged.
