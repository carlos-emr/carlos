# RAG retrieval trial: results (round 1 2026-10-05, round 2 2026-10-06, round 3 2026-10-07)

Retrieval only, on FAKE NHS synthetic patients, with local Ollama embeddings. No text
was generated and no hosted API was called. Full per-probe detail is in
`target/rag/results/` (`<model>-<index folder>-<labels>-eval.json`, `recall-at-k.json`, and
`report-<tag>.md` with every miss; round 1's own files are `<model>-eval.json` and `report.md`).

## Round 3 (2026-10-07): plain words

Same 10 patients, 78 probes, three models and round 2 cutter. Round 3 adds a small,
local, hand-written list (`plain_words.json`, 29 entries, no model) that pairs plain
wording with the chart's own words, for example "collapsed lung" = pneumothorax, PTX;
"go wrong" = complications; "react badly" / "allergic" = allergies, NKA, NKDA; "heart rate"
= HR; "chest film" / "x-ray" = CXR. It is used in two ways:

- **Query side** (`--plain-words query`, on the round 2 index as it is): a question that
  uses a listed plain phrase gets the chart's words added, for example "Was there a
  collapsed lung on the X-ray? (pneumothorax; PTX; CXR; chest radiograph)". 30 of the 78
  questions were expanded.
- **Both sides** (`--plain-words both`, new index folder `r4`): every chunk that uses a
  listed chart word also gets a "Plain words:" line in its embedded text, for example
  "collapsed lung, lung collapse" on a chunk that says "no PTX". 562 of 1,110 chunks
  changed and were embedded again (about 4, 9 and 14 minutes for nomic, bge-m3 and qwen3);
  the stored chunk text is unchanged.

**Important caveat:** the list was written from round 2's misses, so scores on the same 78
probes are optimistic. To measure that, 24 probes got a new, held-out wording
(`heldout.json`, same labels: the 17 that nomic missed at the top 8 in round 2, plus 7 it
found), written before any held-out result was seen; the list was not changed afterwards.

**The 78 probes (vector recall @3 / @8 / @16 / @24):**

| Setup | nomic-embed-text | bge-m3 | qwen3-embedding:0.6b |
|---|---|---|---|
| Round 2 (no plain words) | 0.55 / 0.78 / 0.88 / 0.91 | 0.58 / 0.78 / 0.90 / 0.95 | 0.65 / 0.78 / 0.87 / 0.94 |
| Plain words on questions | 0.69 / 0.92 / 0.94 / 0.97 | 0.72 / 0.87 / 0.95 / 0.96 | 0.73 / 0.88 / 0.97 / 0.99 |
| **Plain words on questions and chunks** | **0.77 / 0.95 / 0.99 / 1.00** | **0.72 / 0.88 / 0.97 / 0.99** | **0.86 / 0.96 / 0.97 / 0.99** |

Rank of the CRITICAL "Complications: None" (question: "Did anything go wrong during the
operation?"): round 2 14 / 10 / 11; questions only 4 / 2 / 4; both 2 / 1 / 1. **It is now in
the top 8 for every model.** Keyword search alone rose from 0.49 to 0.67 at the top 8, and
there were again **0 cross-patient results** in every run.

**The 24 held-out questions (vector recall @3 / @8 / @16 / @24):**

| Setup | nomic-embed-text | bge-m3 | qwen3-embedding:0.6b |
|---|---|---|---|
| Round 2 (no plain words) | 0.29 / 0.46 / 0.67 / 0.75 | 0.46 / 0.58 / 0.79 / 0.83 | 0.42 / 0.63 / 0.75 / 0.79 |
| Plain words on questions | 0.42 / 0.54 / 0.71 / 0.83 | 0.50 / 0.71 / 0.83 / 0.83 | 0.46 / 0.67 / 0.75 / 0.83 |
| **Plain words on questions and chunks** | **0.54 / 0.67 / 0.88 / 0.96** | **0.67 / 0.75 / 0.88 / 0.92** | **0.67 / 0.83 / 0.92 / 0.96** |

These questions are hard on purpose (most rephrase round 2's misses), so their baseline is
much lower than the 78 probes'. Only 7 of the 24 used a listed plain phrase, so the query
side helped little; the chunk side did most of the work. For example, "Was there any blood
when he coughed?" was not expanded, but the chunk that says "haemoptysis" now also carries
"coughing up blood", and it moved from rank 11 to 1 (nomic). "Do any heart or chest problems
run in his family?" moved from 49 to 4.

**Findings:**

- **A plain-word list closes most of the wording gap, mainly from the chunk side.** On new
  wording it lifted nomic from 0.46 to 0.67 at the top 8 and from 0.67 to 0.88 at the top 16;
  qwen3 reached 0.83 / 0.92. That is real but much smaller than the 0.95 on the questions
  the list was written from.
- **"Complications: None" is still fragile.** It reaches the top 8 only when the question uses
  a listed phrase ("go wrong"). The held-out "Did the surgery go smoothly?" left it at rank
  24 / 14 / 23. A critical negative still depends on the exact words someone types.
- **The list costs almost nothing at query time** (a text append) and changes no stored text;
  the chunk side needs one re-embedding of about half the chunks. A few probes moved down
  slightly (for example a family-history question from rank 1 to 2), so expansions add
  some noise.
- **Number-heavy questions are untouched:** "What were his observations on the ward round on
  the day of surgery?" stays around rank 60. Look-alike observation sets need dates or
  structure, not words.

**Recommendation after round 3:** if "ask the chart" is built, use plain words on **both**
sides with a maintained list (reviewed by a clinician, kept small) and show the **top 16**:
on new wording that found 0.88 to 0.92 of the answers. Retrieval is still not fit to be the
only input to a whole-chart summary: about 1 answer in 10 is still missing at the top 16 for
new wording, and a critical negative like "Complications: None" can still be missed.
qwen3-embedding was the best on new wording (0.83 at 8) but is the slowest to index; nomic
stays a reasonable default.

## Round 2 (2026-10-06): direct labels and the form-field fix

Same 10 patients, same 78 probes and the same three models; only the 28 changed chunks
(94 in the split variant) were embedded again, and every other chunk kept its round-1
vector. Round 1's numbers further down are kept as they were published. (Round 1's own
speed files in `target/rag/results/` were overwritten by the round 2 index runs before
speed files were named by folder; the speed table below keeps round 1's figures.)

**What changed:**

1. **Direct labels** (`labels.json`). Each probe now names the exact chart phrase(s)
   that answer it (for example "Complications None", "Allergies: NKA", "BP 124/78" on
   2026-01-05), instead of reusing the summary-fact regexes. The labels are tighter:
   about 2.5 relevant chunks per probe instead of 4. "No allergies" is now one chunk,
   so only 3 probes still span chunks (the two drug conflicts and "no DVT or infection").
2. **Form fields keep their own labels.** A chunk of short form fields is now headed
   with every field label and embedded as "Label: value" lines, so "Complications: None"
   is no longer filed under "Tissue Removed". The stored text is unchanged.
3. **Split variant** (`--form-fields split`): one chunk per form field instead.

**Recall, vector search (recall@8 / recall@3, 78 probes):**

| Setup | nomic-embed-text | bge-m3 | qwen3-embedding:0.6b |
|---|---|---|---|
| Round 1 as published (old cutter, regex labels) | 0.83 / 0.60 | 0.83 / 0.60 | 0.80 / 0.69 |
| Old cutter, direct labels | 0.78 / 0.54 | 0.80 / 0.58 | 0.78 / 0.65 |
| **Round 2 cutter, direct labels** | **0.78 / 0.55** | **0.78 / 0.58** | **0.78 / 0.65** |
| Split form fields, direct labels | 0.76 / 0.53 | 0.78 / 0.58 | 0.80 / 0.67 |

**More results with the round 2 cutter (vector recall, direct labels):**

| Model | @8 | @12 | @16 | @24 | Rank of "Complications: None" (old cutter → round 2 → split) |
|---|---|---|---|---|---|
| nomic-embed-text | 0.78 | 0.83 | 0.89 | 0.91 | 30 → 14 → 37 |
| bge-m3 | 0.78 | 0.87 | 0.90 | 0.95 | 14 → 10 → 63 |
| qwen3-embedding:0.6b | 0.78 | 0.85 | 0.87 | 0.94 | 10 → 11 → **2** |

The deeper recall and the ranks come from `evaluate` itself (`recall_at` and each probe's
`rank_needed` in the result files, and the depth table in `report-<tag>.md`).

Keyword search alone fell to 0.49 and the even hybrid to 0.68-0.72 with direct labels,
so they stay worse than vector search. Every negation and family-history hit kept its
"no …" or Family History context (12/12, 10/10, 10/10), and there were again **0
cross-patient results** in every run.

**Findings:**

- **Round 1 overstated recall by about 1-5 points at the top 8 (3-6 at the top 3).** Its regex labels gave credit for
  chunks that only mentioned the topic. With direct labels all three models find the
  answer in the top 8 about 78% of the time (about 4 facts in 5).
- **The CRITICAL "Complications: None" miss is not fixed at the top 8.** The cutter
  problem itself is fixed: the field now sits under its own label, and that moved it from
  rank 30 to 14 for nomic and from 14 to 10 for bge-m3. But no model ranks it in the
  top 8 with the round 2 cutter. Splitting fields into separate chunks puts it at rank 2
  for qwen3 only, while pushing it to 37 and 63 for the other two models and lowering
  nomic's recall, so splitting is not a general fix. "Anaesthesia Type: General
  anaesthesia" behaves the same way (ranks 26, 9, 14).
- **What is left is mostly wording**, not cutting: 12 of nomic's 17 misses at k = 8 are
  questions whose words the chart never uses ("go wrong" vs "Complications",
  "collapsed lung" vs "no PTX", "trapped air" vs "pneumomediastinum"), plus look-alike
  sections (12 sets of observations, allergy and medication grids). An embedding model
  alone does not bridge these.
- **Showing 16 pieces instead of 8** lifts recall to 0.87-0.90 (0.91-0.95 at 24) and
  brings "Complications: None" in for all three models.

**Recommendation after round 2:** keep the round 2 cutter. At the top 8 it changes at
most one probe per model (bge-m3 loses one fact that spans two chunks), it lifts
"Complications: None" for two of the three models, and it lifts the deeper recall; the
split variant is not worth it. Retrieval is still not fit to be the only input to a
whole-chart summary. For "ask the chart" questions that show their sources, show the top
16. Keep nomic-embed-text as the default; qwen3-embedding stays the best at the top 3
(0.65) but is the slowest.

## Round 1 (2026-10-05)

## Scope

- **Data.** NHSSYN001-010: 251 notes, 1,110 section chunks (median 140 characters).
  - The labelled facts exist only for 001-003, so recall is measured on those three.
  - 004-010 are the "other patients" that per-patient filtering must keep out.
- **Why only 10 patients.** The planned 50-patient index (6,955 chunks) was stopped on
  request. At about 3.5 chunks/s for the smallest model, it held the only working build
  slot of the shared machine for too long. Indexing then ran as short, resumable calls
  of at most 400 chunks each.
- **Probes.** 78 in total: 62 labelled facts, 8 negation probes and 8 family-history
  probes. A probe counts as found at k when a relevant chunk is in the top k.
- **Search.** Exact cosine within one patient, using a sqlite-vec v0.1.9 `vec0` table
  partitioned by patient. The pure-Python fallback was not needed.
- **Isolation.** Every search re-checks the owner of each result. Each model also ran
  90 extra searches covering all 10 patients and all 3 modes. There were **0
  cross-patient results**.

## Recall (78 probes; one probe = 1.3 points)

| Model | Mode | recall@8 | recall@3 | facts @8 (62) | negation @8 (8) | family @8 (8) |
|---|---|---|---|---|---|---|
| nomic-embed-text | vector | **0.83** | 0.60 | 0.85 | 0.62 | 0.88 |
| nomic-embed-text | keyword | 0.54 | 0.45 | 0.50 | 0.62 | 0.75 |
| nomic-embed-text | hybrid | 0.76 | 0.60 | 0.74 | 0.75 | 0.88 |
| bge-m3 | vector | **0.83** | 0.60 | **0.89** | 0.62 | 0.62 |
| bge-m3 | hybrid | 0.76 | 0.56 | 0.76 | 0.75 | 0.75 |
| qwen3-embedding:0.6b | vector | 0.80 | **0.69** | 0.84 | 0.62 | 0.62 |
| qwen3-embedding:0.6b | hybrid | 0.76 | 0.60 | 0.76 | 0.75 | 0.75 |

Keyword search uses no embeddings, so it scores the same for every model.

**Hits@8 per patient (vector):**

| Patient | Probes | nomic | bge-m3 | qwen3 |
|---|---|---|---|---|
| NHSSYN001 | 25 | 24 | 23 | 22 |
| NHSSYN002 | 29 | 26 | 24 | 21 |
| NHSSYN003 | 24 | 15 | 18 | 19 |

Patient 003's notes are terse and heavily abbreviated ("Rpt CXR… Slight ↓
pbeumomediastinum", "Complete res of SPM").

## Speed on this machine

The machine is shared: 4 cores at nice 10, with other builds running. Treat these
numbers as rough.

| Model | Size on disk | RAM when loaded | Dims | Chunks/s | First batch (incl. load) | Query embed (median / p95) | Search per query (vector / keyword / hybrid) | Index file (10 patients) |
|---|---|---|---|---|---|---|---|---|
| nomic-embed-text (137M, F16) | 274 MB | 376 MB | 768 | **3.45** | 5.7 s | **56 / 79 ms** | 1.5 / 0.3 / 2.2 ms | 39 MB |
| bge-m3 (567M, F16) | 1,158 MB | 1,219 MB | 1024 | 1.92 | 12.4 s | 114 / 175 ms | 1.7 / 0.2 / 2.6 ms | 49 MB |
| qwen3-embedding:0.6b (596M, Q8_0) | 639 MB | 2,371 MB | 1024 | 1.19 | 17.4 s | 187 / 286 ms | 1.0 / 0.1 / 1.4 ms | 49 MB |

- **Indexing the 10 patients (1,110 chunks).** About 5 minutes with nomic, 10 with
  bge-m3 and 16 with qwen3. The full 50-patient corpus would take about 34, 60 and 97
  minutes.
- **nomic timing.** It comes from the first 3,824 chunks of the interrupted 50-patient
  run. The other two models were timed on the 1,110 chunks.
- **Batching.** All models used batches of 16 texts, one model loaded at a time, and
  were unloaded after each call.
- **Where query time goes.** The exact per-patient search costs about 1-3 ms. Embedding
  the question is almost all the query time.

## Labels

- **3 of 62 facts span chunks.**
  - The two "conflict" facts (001 nimodipine vs amlodipine at discharge; 002
    tinzaparin vs enoxaparin). Their conflict wording is the summary's own judgement
    and never appears in the chart, so that pattern group was dropped. The drugs sit in
    different notes. Vector search covered both drugs in the top 8 for both facts
    (hybrid missed the second one).
  - 001 `no-allergies`. The chart says "Allergies: NKA" and "Allergies / Nil", and the
    label's words never match. It was missed by every model and mode.
- **1 override.** 001 `potassium-chloride`: the chart writes only "KCl".
- **Some labels are loose.** For example `rcvs-diagnosis` matches 17 chunks, so it is
  easy to find.

## Misses at k = 8, by kind

| Model / mode | Wording mismatch | Split across chunks | Lost heading / context | Date / number | Medication name | Other | Total |
|---|---|---|---|---|---|---|---|
| nomic vector | 9 | 1 | 1 | 1 | 0 | 1 | 13 |
| bge-m3 vector | 6 | 1 | 1 | 1 | 0 | 4 | 13 |
| qwen3 vector | 11 | 1 | 0 | 2 | 1 | 1 | 16 |
| keyword (any model) | 18 | 2 | 0 | 7 | 5 | 4 | 36 |
| hybrid (each model) | 13-14 | 2 | 0 | 3 | 0-1 | 0-1 | 19 |

The kinds are assigned by a simple rule (`classify_miss`). Examples, as expected
chunk → top-3 retrieved:

1. **Wording mismatch.** "Had the trapped air lessened on the second chest film?" The
   answer chunk is "Rpt CXR reviewed. Slight ↓ pbeumomediastinum…". nomic and bge-m3
   retrieved On Examination chunks instead; qwen3 found it. "Was there a collapsed lung
   on the X-ray?" fails for nomic and qwen3 because the chart only says "no PTX".
2. **Lost heading: a chunker problem.** "Was he fully asleep for the operation, or
   awake with a nerve block?" The operation-note form fields "Anaesthesia Type /
   General anaesthesia" are short, so they were folded into a chunk headed "Surgeon".
   "Did anything go wrong during the operation?" has the same problem: "Complications /
   None" sits inside a chunk headed "Tissue Removed". Both were missed by every model
   and every mode.
3. **Date / number.** "What were his heart rate, breathing rate and blood pressure on
   the day-of-surgery ward round?" The answer is the 2026-01-05 "Observations" chunk
   (HR 2, BP 124/78, RR 1). It is one of 12 chunks for this patient that record
   observations or a BP, and it was missed everywhere. Top 3 retrieved: "2025-12-21 Consultant", "2025-12-20 Note
   text", "2026-01-05 Observations on arrival".
4. **Family history.** "Is she known to have hypertension?" Only her father has HTN.
   bge-m3 and qwen3 returned chunks about her own raised blood pressure ("Patient",
   "Note text", "Plan") and never the Family History chunk. Nothing was misattributed, but the one
   relevant line was lost.
5. **Context check caught a false negation.** For "Did she have a stiff neck…?", nomic
   counted a rehab note as a hit. That note says neck stiffness *is* present; its "no"
   belongs to another phrase. The context check flagged it. Every other hit on a
   negation or family probe kept its "no …" or Family History context. That context was
   always inside the chunk text itself, so on this data the date and heading prefix was
   not what preserved it.

## Recommendation

1. **Don't use top-8 retrieval as the only input for whole-chart summaries.** Even the
   best setup missed about 1 labelled fact in 6, and some misses were critical facts
   such as recorded vitals or "no complications". The current whole-chart and
   section-pass approach is safer for summaries. Retrieval fits "ask the chart"
   questions that show their sources, or a check of "did the summary miss something?"
2. **Model choice: nomic-embed-text.** It ties bge-m3 on recall@8 (0.83). It indexes
   about 1.8x faster, embeds a question 2x faster, and uses a quarter of the disk and a
   third of the RAM. qwen3-embedding ranks best in the top 3 (0.69), but it is the
   slowest and needs 2.4 GB of RAM.
3. **Don't use plain equal-weight hybrid.** RRF with keyword search lowered recall@8
   from 0.83 to 0.76, because keyword search fails on paraphrased questions (0.54). It
   helped only where the question repeats the chart's word, as in "smoke" or "diabetes":
   negation probes went from 0.62 to 0.75, and family probes for bge-m3 and qwen3 from
   0.62 to 0.75. If keyword search is used later, use it for
   exact drug names, numbers and dates, or give it a lower weight.
4. **Before any next trial:**
   - stop the chunker folding short form fields under a neighbouring heading;
   - label relevant chunks directly instead of reusing the summary regexes;
   - add a time and encounter cue to Observations chunks.
5. **Isolation held,** and per-patient exact search costs about 1-3 ms. If this is ever
   built for real, the vectors go into CARLOS's own MariaDB as the `VECTOR` type
   (11.8 or newer), filtered by patient, with embeddings from a local model only.
   Nothing is to be built now.

## Known limits

- **Small sample:** 78 probes on 3 patients. Differences of 1-3 probes are noise.
- **Keyword statistics:** BM25 word statistics come from every indexed patient. Ranking
  and results stay per patient.
- **Chunker warts:** inline labels can become headings (for example a nurse narrative
  headed "Consultant"). 2 signature lines in scope became tiny chunks of their own. The
  chunker was frozen during the run, so all models saw identical chunks.
- **Model prefixes:** each model used its model-card prefixes (nomic `search_query:` /
  `search_document:`, and a Qwen3 instruction on queries). No tuning was done.
