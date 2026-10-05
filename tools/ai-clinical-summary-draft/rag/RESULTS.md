# RAG retrieval trial: results (2026-10-05)

Retrieval only, on FAKE NHS synthetic patients, with local Ollama embeddings. No text
was generated and no hosted API was called. Full per-probe detail is in
`target/rag/results/` (`<model>-eval.json`, and `report.md` with every miss).

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
