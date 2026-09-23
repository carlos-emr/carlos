# Reducing document-summary reading volume

The bundled Parasail gateway now uses `extractive-brief`: select fewer source passages, with
individual bullets available for selection in recognized lists. The page shows key points once;
the repeated overview is available in a closed disclosure. Evidence remains expandable beside
each point. This reduces reading volume without letting the model rewrite clinical statements.

## Final comparison, 2026-09-23

The longest complete note from each of NHSSYN034–050 was summarized with both the previous
paragraph selector (`fidelity`) and the final shorter selector (`brief`), alternating order.
These 17 patients had already been examined during development: **this final comparison is a
regression evaluation, not an untouched holdout or a clinical accuracy study**.

| Measure | Previous selector | Shorter selector |
| --- | ---: | ---: |
| Passed format/provenance validation | 17/17 | 17/17 |
| Median point words as fraction of source words | 81% | 70% |
| Median generation latency | 0.89 s | 0.77 s |
| Generated tokens, 17 drafts | 680 | 305 |
| Reported cost, 17 drafts | $0.00303965 | $0.00326915 |

The median **paired** reduction in point words was **11%**. Including the page change, the median
reduction in initially displayed summary words was **28%**: the old view displayed overview plus
points; the new view displays points and keeps the overview closed. These paired statistics are
not calculated by dividing the two median source fractions in the table.

Word counts use whitespace-delimited tokens, including retained headings. They exclude shared UI
labels/warnings and closed evidence. They are a **reading-volume proxy**, not a measured reduction
in clinician reading time. We did not time clinicians or test comprehension. The 40% source-word
prompt target remains aspirational; protection rules and indivisible paragraphs often exceed it.
Some short notes are reproduced entirely. More input structure slightly increased cost despite
fewer output tokens; shortening was the objective, not an API-cost claim.

Additional final runs covered NHSSYN004–013 once each and the original six documents from
NHSSYN001–003. **All 33 final brief drafts passed**. Saved selections were independently resolved
again; every mandatory excerpt was retained and every displayed word matched the ordered cited
source excerpts. [Metrics and source hashes](quality/2026-09-23/document-readtime-evaluation.json)
contain no document text or credentials. Raw synthetic reports remain in private runtime storage.

## Selection and rendering

- The model still returns only distinct supplied IDs. It receives a soft word target and the
  number of words already required by the host. It is asked to omit repeated symptom histories,
  routine normal reviews and unrelated background, while retaining decisive findings and context.
- Recognized examination, history, investigation/result and triage lists can be selected by bullet.
  A heading stays attached. Wrapped lines and nested bullets stay with their parent. Lead-in
  qualifiers, dependent bullets, unknown headings and ordinary prose remain whole paragraphs.
  Selecting a length-split continuation retains its entire original paragraph or list item.
- A narrowly recognized flat identity/clinical-field block can be split by field, so retaining
  allergies and medications need not retain the patient's name and identifiers in that template.
  This is **not de-identification**; embedded names and unsupported templates remain.
- Adjacent selected bullets from the same source paragraph share one display heading, with
  separate verbatim evidence for each selected item. The host never joins sentence fragments.
  Existing limits of five excerpts per point and bounded point/evidence lengths still apply.
- Recognized medication, allergy, impression, plan and disposition sections remain mandatory.
  Pending/awaiting and follow-up wording, observations (including recognized inline vital labels),
  consciousness scores, and treatment-status verbs paired with dose/route/fluid wording also
  trigger retention. Investigation sections are no longer retained wholesale solely by heading.
- Simple same-word negation contrasts retain both source passages. This is a bounded lexical
  safeguard, not a general contradiction detector: synonyms, scope and temporal distinctions
  remain unresolved. The host does not add an interpretation or correct source errors.
- Public Java schema, authorization, CSRF, feature defaults, synthetic-note disclosure boundary,
  provider pinning/privacy restrictions, disabled reasoning and no document-output cache remain
  unchanged. Local Ollama/custom gateway behavior is unchanged; their overview is also collapsed
  by the shared JSP.

## Review findings and limits

Early versions shortened more aggressively. Inspection found omitted GCS in NHSSYN011, already
administered fluids under Investigations in NHSSYN038, inline observations in NHSSYN040, and
follow-up under the misspelled heading “Next Tseps” in NHSSYN049. The final rules retain these.
Repeated generation also exposed omission of a conflicting nausea account in the original
multiline assessment; simple negation-contrast retention now keeps both source accounts. This
also retains NHSSYN047's differing lethargy statements without resolving their timing.

These protections deliberately reduced the early compression gains. The earlier ten-patient
54% source-word result is **not the final result**. Selecting exact text prevents invented wording
but does not establish clinical completeness. Relevant negative findings can still be omitted,
as seen with mastoid findings in NHSSYN043; a repeated measurement may survive without every
source interpretation, as with PEF in NHSSYN036. A clinician must still review the original.
Unknown headings, source typos, dense prose, embedded identifiers and redundant plans limit
compression. The output remains an unsaved, unverified prototype draft.

## Verification and reproduction

The Python suite has **145 passing tests**, including list context, nested/continued bullets,
separate evidence after merging, repeated rendering, unchanged historical mode, completed
treatment, inline observations, flat fields, follow-up and simple negation contrasts. All 1,602
complete corpus notes were checked for exact excerpt provenance, UTF-16 excerpt limits, required
passage counts and provider request-byte limits. These structural checks are not clinical review.
The final browser check returned six points in **2.07 s**, with live generation, a collapsed overview, visible points, expandable
evidence, preserved line breaks, POST-only generation, CSRF/invalid-ID rejection and extraction
warnings. No Java service logic changed in this pass.

```bash
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py \
  --patient-range 34 50 --modes fidelity brief --repeats 1 \
  --output /path/to/private/readtime-comparison.json
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -p 'test_*.py'
```

`fidelity` preserves the earlier paragraph-selector behavior; `brief` uses the current gateway
behavior. API comparisons incur charges with the existing private credentials. Restart the
bundled gateway and deploy the updated JSP to use the change elsewhere.
