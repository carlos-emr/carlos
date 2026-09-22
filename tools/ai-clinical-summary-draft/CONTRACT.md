# Clinical summary artifact v1

The authoritative rendering example is
`src/main/resources/clinical/summary/synthetic-overview.json`.
Java `ClinicalSummaryArtifact` and Python `validate_artifact.py` enforce the
same structural rendering invariants, except that Java also accepts explicitly
non-synthetic chart extracts. The Python tooling remains synthetic-only.
`output-schema.json` constrains the **model-owned
subset only**, not the full artifact.

| Field | Shape |
| --- | --- |
| schema_version | Integer 1 |
| artifact_id, generated_at, model, workflow | Nonempty text; generated_at is an ISO timestamp with timezone |
| patient_context | id, label, synthetic: boolean (Python and fixture provider require true) |
| sections | Array of id, title, claim_ids |
| claims | Array of id, text, source_ids |
| sources | Array of id, patient_id, title, date, text |
| fact_ledger | Array of id, category, text, source_ids |
| coverage | Array of source_id, status, reason |
| validation | Array of severity, code, message, source_ids |

IDs in sources, claims, ledger and sections must match `[A-Za-z0-9_-]{1,80}`
and be unique within their collection. Every source belongs to patient_context.id.
The source collection must be nonempty. The timestamp records artifact assembly,
not clinical event time. Source dates remain source metadata.

Every claim and ledger entry has at least one distinct, resolvable source ID.
Each claim appears in exactly one section. Empty sections/claims/ledger are valid
empty states and must not be interpreted as negative clinical findings.
Coverage includes every source exactly once; status is `cited`,
`reviewed_not_cited` or `excluded`, with a nonempty reason. Only sources
actually referenced by claims have status `cited`; ledger-only references
do not count as summary coverage. The model writes reviews only for sources
no claim cites; the host records each cited source itself, with the number of
statements citing it, before this rule is checked. A source that is neither
cited nor reviewed is recorded by the host as exactly that ("no statement cites
this note and the model gave no reason") with a `sources_not_cited_without_reason`
validation warning. The host never describes it as reviewed by the model, and it
does not fail the draft: a short draft shows its gaps instead of showing nothing.

Two further host guarantees hold whichever model, provider or local server wrote the
draft, because they use only the note dates and verbatim note text the host already
has. They never rewrite model prose. A vital-sign observation set (at least three of
heart rate, blood pressure, respiratory rate, temperature and oxygen saturation
recorded together) that no single citing claim reports in full is added back as a
claim with ID `host-obs-N` under Results and observations, quoting the recorded
values and saying the host restored it. A date a claim asserts that none of its
cited notes carries (their own date, a date written in them, or a stated tomorrow or
48 hours) is recorded as a `date_not_in_cited_sources` validation warning.

Where the site supplies a drug-class table (`CARLOS_DRUG_CLASSES`, a JSON map of drug
name to ATC code derived from its drug reference database and not committed, because
the ATC classification belongs to the WHO Collaborating Centre), the host also finds
two drugs of one ATC chemical subgroup that are each ordered in the record with no
note recording a stop or switch. If no claim reports that conflict, the host states it
as a claim with ID `host-med-N` under Medications and allergies, and a claim that
describes a switch no note records gets an `undocumented_medication_change` warning.
With no table the check is skipped. The Java host does not apply this check yet.

The gateway may also fold statements whose text is identical into one carrying every
citation, and may make one small further model call to rewrite statements the host
faulted (a date no cited note carries, a named person or patient identifier, an
undocumented medication change, a restatement of another section's statement). A
rewrite is kept only if the host can no longer fault it and the draft still
validates; it keeps its citations and takes the ID prefix `repaired-`, so a viewer
can mark it. A restatement the model omits in its answer is dropped. The host also
records `statement_names_person_or_identifier` and `statements_restate_each_other`
validation warnings. Neither the merge nor the repair rewrites anything silently.

Validation severity is `pass`, `warning` or `error`. Source IDs may be empty
for structural findings; any supplied IDs must resolve. Error findings suppress
the summary. The UI labels artifact findings separately from runtime structural
checks: a recorded pass is not independent evidence of clinical correctness.

Sources, ledger, patient context and provenance are host-owned during generation.
Neither generator accepts these fields or validation findings from the model.
The rendering contract does not depend on Qwen, Ollama, prompts or model response envelopes.
The runtime [agent API](AGENT_API.md) applies this same host-owned validation to
the default Ollama adapter, HTTP agents and in-process Java implementations.
No patient chart selector, model endpoint or filesystem path is part of the
rendering artifact's authority.

The optional generators add stricter readability requirements. Generated output
uses a fixed set of five clinical sections, omits empty sections, contains 1-20
single-paragraph claims of at most 240 characters, and keeps coverage reasons to
160 characters. Normalized duplicate claims and reasons, fixture metadata posed
as clinical claims, and claims without basic lexical overlap with their cited
sources are rejected. A fixed abbreviation map treats common clinical shorthand
such as HR, BP, RR, SpO2 and HF as lexical equivalents of their readable forms.
These checks reduce obvious low-quality output but do not
prove that prose is clinically supported or correct. Generated output accepts
exactly sections, claims and coverage and must pass this contract before rendering.
Chart-derived patient context may contain a
host-assigned `generation_fixture` marker; that marker alone is not authorization.
The generator checks it against pinned identity and every source note text/date
hash, then the action reloads authorized chart evidence after inference. Generated
drafts retain the chart context's `synthetic: false` provenance flag; the UI labels
them explicitly as verified-fixture synthetic testing and unverified AI output.
