# Balancing coverage, readability and length

The bundled single-document gateway now uses **balanced-reviewed**. It preserves recognized
clinical facts verbatim and condenses the surrounding narrative into a few readable points.
The objective is to include relevant detail at useful length, rather than hit a deletion quota.
The model/provider remain Qwen3.5-35B-A3B on Parasail; no routing or privacy restrictions changed.

## How it works

1. The existing full-note synthetic allow-list and Java request-contract checks run before any
   external call. The host indexes source paragraphs, preserving length-split continuations.
   Strictly recognized flat identity/clinical-field blocks can be split so retaining allergies
   and medication fields need not retain identity fields in that template.
2. The host reserves recognized medications, allergies, results, impression, procedure, observations,
   disposition, plans, medical/family history and systems-review sections for verbatim display.
   Explicit pending/follow-up wording, recognized inline observations, treatment-status patterns
   and qualified negation also trigger retention. These are bounded lexical rules, not a complete
   clinical parser. For example, “no fever with rigors” must not become “no fever”.
3. The writer receives the remaining passages and first-line cues for sections already included.
   It groups relevant symptom course, examination and functional/social context into short prose.
   It cannot replace or cite a reserved passage. Withholding the complete reserved sections avoids
   requests to rewrite facts that the host will already display in full.
4. The host combines the prose and reserved source passages, resolves exact evidence, and applies
   the unchanged public output validator. Numeric values must occur in the point's cited passages;
   additional lexical checks reject certain unsupported physiological labels and broad symptom
   generalizations. These checks establish limited provenance, not semantic equivalence.
5. A separate call reviews the **entire source and combined draft** for missing relevant detail,
   unsupported meaning, altered status/conditions and contradictions. It can request one revision;
   the revised draft must pass another review. Unresolved review, invalid output, timeout or refusal
   releases no draft. A document consisting entirely of reserved passages needs no model call.

The normal path uses two calls; a repair uses four. There is no unbounded repair loop or document
output cache. Each call retains the configured provider pin, no fallback, ZDR/data-collection
restrictions and byte budget; the shared overall deadline still applies. Repeated identical IDs
within one point are canonicalized without altering prose or accepting unknown IDs; final evidence
remains distinct and verbatim. The optional overview reuses the first point and stays collapsed.
The page now uses a compact note layout with smaller gaps, rather than large separated cards.

## What the experiments taught us

A fully rewritten draft was much shorter, but the small-model reviewer approved omissions and
unsupported interpretations. An early confusion summary used 189 of 523 source words yet introduced
physiological labels and missed detail. It was not adopted. A reasoning-enabled review timed out;
a larger Qwen reviewer on the same pinned provider was rate-limited and supplied no quality result.
Neither experiment changed the running model configuration.

The mixed approach initially still expanded the ambiguous family-history abbreviation “AO” and
lost reduced urinary output in a broad negative statement. Medical/family history and systems review
are now retained directly. A targeted contradiction case exposed “no fever with rigors” becoming
“no fever” despite review approval. Qualified negation is now retained directly too. This illustrates
why an empty model issues list is **not clinical verification**.

Regression checks cover planned versus completed antibiotics/fluids, pending cultures and D-dimer,
borderline sodium and leucocytosis, conditional physiotherapy, ambiguous family-history wording,
qualified fever negation, completed treatment under an unexpected heading, and follow-up under a
misspelled heading. An ear-pain check also retained the mastoid/pre-auricular negatives that the
earlier selection-only version omitted. The host does not correct erroneous source doses or resolve
conflicting accounts.

## Measurements and remaining limits

The earlier development comparison on ten longest patient notes retained approximately the same
median word fraction as the selection-only version (73% in each), while reserving more clinical
content. A separate comparison on second-longest notes from ten other patients retained a median
82% versus 85% previously. Each comparison had one provider timeout; the nine other mixed drafts
passed. Typical median generation latency was 3.6–4.2 seconds versus 0.6–0.8 seconds for selection.
These are development measurements, not an untouched clinical validation study or a latency promise.

After the final protection changes, all ten longest-note drafts from NHSSYN004–013 passed the
structural/provenance, protected-passage and model-review checks. Their median point length was
**72% of source words**, with median generation time **15.76 seconds** (range 13.01–51.22 seconds).
Reported cost for ten drafts was $0.0080404. The separate qualified-negation regression passed with
85% of source words. Generation latency varied substantially between runs; one final case retried
a provider rate limit. These are not clinical-completeness measurements.

Final measurements and source hashes are
recorded in [the evaluation artifact](quality/2026-09-23/document-balanced-evaluation.json).
Failed attempts and separate rechecks are retained, not silently counted as first-attempt success.
Reading volume is measured using whitespace-delimited point words including headings; it is not
measured clinician reading time or comprehension. Short, already dense notes may remain nearly intact.
Some summaries become longer than the aggressive selector because facts are retained.

Relevant information outside recognized sections can still be omitted or misrepresented by the
writer and missed by review. Unknown headings, ambiguous language, source typos and embedded names
remain limitations. This is not de-identification and not a clinically validated replacement for
the original. The draft remains unsaved, with the original and supporting excerpts available.

## Verification and reproduction

The Python suite has **162 passing tests**, including disclosure rejection before any model call,
protected source retention, review/revision limits, malformed reviews, invalid citations, numerical
provenance, unsupported labels, qualified negation, field splitting and HTTP routing. All 1,602
corpus notes were checked for exact source excerpts, grouping, bounds and initial writer request
budgets. The isolated browser check passed with seven points in 11.07 seconds, including live
generation, compact styling, a collapsed overview, expandable evidence, CSRF/invalid-ID rejection,
POST-only generation, no-store responses and extraction warnings. No Java logic changed.

```bash
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py \
  --new-patients --modes brief balanced --repeats 1 \
  --output /path/to/private/balanced-comparison.json
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py \
  --patient-range 14 23 --note-rank 2 --modes brief balanced --repeats 1 \
  --output /path/to/private/second-notes-comparison.json
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -p 'test_*.py'
```

`brief` reproduces the previous selector; `balanced` is the current gateway workflow. `distill`
retains the fully rewritten experiment for comparison and is not selectable through the HTTP API.
API comparisons incur charges using the private runtime credentials. Restart the bundled gateway
and deploy the updated JSP/CSS to enable this version elsewhere. Ollama/custom gateways retain
their own generation behavior and share the more compact page layout.
