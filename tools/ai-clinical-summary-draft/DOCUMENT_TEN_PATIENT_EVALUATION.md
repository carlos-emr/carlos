# Document summarizer: ten new NHS synthetic patients

On 2026-09-23, the optimized Parasail/Qwen3.5-35B-A3B document summarizer was tested on
NHSSYN004–NHSSYN013, excluding the three patients used during optimization. Each patient's
longest complete note was selected before any outputs were observed and summarized twice.
This tests ten separate documents, not ten whole-chart summaries. No prompt, model, runtime
configuration, or validator changes were made during this evaluation.

**20/20 drafts passed the existing provenance/format checks.** Median latency was **3.83 s**
(range **2.66–5.22 s**); provider-reported cost was **$0.0181225** for all 20 attempts.
All calls returned from Parasail. Reasoning and output caching were disabled; provider input
caching could occur. The longer-note selection makes these latency results unsuitable for a
direct before/after comparison with the shorter optimization set.

| Synthetic patient | Note index (zero-based) | Characters | Accepted | Latencies, repeats 1 / 2 |
| --- | ---: | ---: | ---: | ---: |
| NHSSYN004 | 4 | 2,151 | 2/2 | 2.87 / 2.66 s |
| NHSSYN005 | 5 | 3,624 | 2/2 | 4.07 / 4.35 s |
| NHSSYN006 | 0 | 1,760 | 2/2 | 3.48 / 3.15 s |
| NHSSYN007 | 5 | 2,498 | 2/2 | 3.89 / 4.31 s |
| NHSSYN008 | 5 | 2,515 | 2/2 | 3.78 / 4.19 s |
| NHSSYN009 | 0 | 2,290 | 2/2 | 3.62 / 4.93 s |
| NHSSYN010 | 2 | 2,791 | 2/2 | 4.12 / 4.80 s |
| NHSSYN011 | 5 | 3,388 | 2/2 | 3.66 / 5.22 s |
| NHSSYN012 | 0 | 2,014 | 2/2 | 3.74 / 4.42 s |
| NHSSYN013 | 0 | 2,234 | 2/2 | 3.19 / 3.53 s |

## Fidelity review

All ten first-repeat drafts were read against their source notes, with selected second-repeat
checks for medication plans, uncertainty, omissions, and the errors below. This is an exploratory
review, not a blinded or clinically adjudicated accuracy score.

| Patient / document | Observations from the first repeat |
| --- | --- |
| 004 — appendicitis assessment | Preserved ultrasound findings, antibiotic doses, NBM, and planned surgery. Included a clinician name despite instructions to use roles. |
| 005 — confusion after a fall | Preserved sodium 132, GCS 14/15, fluid volume/rate, paracetamol dose, and monitoring intervals. Added staff identity details. |
| 006 — hip preoperative assessment | Preserved analgesic doses, iron dose/duration, and ibuprofen cessation timing. Silently changed the unclear maternal history “AO” to “OA”. |
| 007 — pneumonia/sepsis | Preserved initial versus current inflammatory markers, antibiotic doses, oxygen target, conditional fluid bolus, and planned repeat lactate. Retained the source's unusual clarithromycin 50 mg dose rather than correcting it. This assesses fidelity, not whether the source treatment is appropriate. |
| 008 — suspected CTEPH | Preserved diagnostic uncertainty, planned apixaban, CTPA, and echo. Omitted the pending D-dimer and pollen allergy; called FBC/U&Es normal although the source only gives values. Included a clinician name. These omissions and the normality claim recurred in repeat 2. |
| 009 — shoulder preoperative assessment | Preserved nut allergy, medication doses, anemia, iron plan, and surgery date. |
| 010 — hip consent | Preserved penicillin/aspirin allergies, analgesic doses, completed consent, planned surgery, and the 48-hour ibuprofen hold. Included a clinician name. |
| 011 — delirium/UTI assessment | Point-level antibiotic transition and fluid volume/rate were preserved, but the overview incorrectly said antibiotics and fluids had already started. It also invented an awaiting-transfer-of-care status and described the patient as elderly, although this note gives no age. Pending urine culture was omitted. |
| 012 — knee preoperative assessment | Preserved pending blood tests, the distinction between current and planned paracetamol doses, amlodipine dose, ibuprofen hold, and surgery date; flagged the source spelling “Ibup rofen” as unclear. |
| 013 — hip preoperative assessment | Preserved penicillin allergy, current medication doses, vitamin D value, and planned colecalciferol 800 IU daily. |

The clearest semantic failure is NHSSYN011. Its source says “Start IVceftriaxone”, “Commence IV
0.9% sodium chloride”, and “Liaise with the geriatric team”. The first overview instead says the
patient “has been started on intravenous antibiotics and fluids” and is “awaiting transfer of
care to the geriatric team”. The second repeat restores planned-treatment wording but still
invents an awaiting-transfer status. Both drafts passed the validator.

**The citation mechanism generalized to these ten patients; semantic fidelity is still a blocker
to treating drafts as verified clinical summaries.** Exact source text and lexical overlap do not
establish that the prose is entailed by its evidence. The next quality work should focus on the
overview's planned/completed distinctions, unsupported status claims, and pending-result omissions.
No prompt tuning or automatic repairs were applied to conceal the observed failures.

## Reproduce

```bash
python3 tools/ai-clinical-summary-draft/compare_document_parasail.py \
  --new-patients --modes references --repeats 2 \
  --output /path/to/private/ten-patients.json
```

This incurs API charges and uses the existing owner-only runtime credential file. Requests must
match complete committed synthetic notes before any network access. The private report contains
source text, outputs, and usage, but no credentials or reasoning traces.
[Committed metrics](quality/2026-09-23/parasail-document-ten-patients.json) retain source hashes,
the prompt hash, raw-report hash, and per-attempt usage/latency. All 20 saved outputs were rechecked
with the unchanged resolver and validator; all ten selections were verified against the corpus.
The evaluation runner passed Python compilation and CLI checks. Application code was unchanged.
