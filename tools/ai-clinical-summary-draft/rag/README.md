# RAG retrieval trial (research only)

This folder holds a small, **retrieval-only** experiment for the CARLOS EMR AI-summary
research tools. It tests one idea: when someone asks a whole-chart question ("Does
this patient have any allergies?"), can a search engine pull out the right parts of
that patient's chart?

Nothing here is part of CARLOS. It does not touch the CARLOS database, Java code or
Tomcat. It generates no text: no chat model is called at all.

## The idea in plain words

"RAG" (retrieval-augmented generation) means: before a language model writes a
summary, a search step picks out the few chart passages most likely to matter, and
only those passages go to the model. This trial measures **only the search step**.
If search misses the passage that holds a fact, no model can summarise that fact
correctly. Search has to be good before generation is worth trying.

The trial compares three ways of searching:

- **Vector search** turns every chart passage and every question into a list of
  numbers (an "embedding") that captures meaning. Passages whose numbers are
  closest to the question's numbers come first. It can match "blood thinner" to
  "tinzaparin" without sharing a word.
- **Keyword search** is ordinary word matching (SQLite FTS5 with BM25 ranking).
  It is exact but has to share words with the question.
- **Hybrid search** merges both result lists with reciprocal rank fusion (RRF,
  k = 60). A passage near the top of either list rises.

Three local embedding models are compared: `nomic-embed-text`, `bge-m3` and
`qwen3-embedding:0.6b`.

## How it works

1. **Load** the committed FAKE NHS synthetic notes (patients NHSSYN001 to NHSSYN050,
   names start with "FAKE-NHS") through `openrouter_agent.SyntheticNotes`. That loader
   checks every note against a checksum manifest. Every patient id must match
   `^NHSSYN\d{3}$` before anything is indexed.
2. **Cut** each note into chunks, one clinical section each (for example "Family
   History" or "Plan"), using the `source_segments` and `source_sections` helpers in
   `chart_updates.py`. Tiny scraps such as signatures and form labels are folded into
   the chunk before them. Long sections are split at sentence boundaries (at most
   1,200 characters). Each chunk keeps its exact character offsets in the note.
   Round 2: a chunk made of several short form fields ("Anaesthesia Type" with
   "General anaesthesia" on the next line, then "Complications" / "None") is headed
   with all its field labels and embedded as "Label: value" lines (`--form-fields
   label`, the default), or cut into one chunk per field (`--form-fields split`).
   `--form-fields none` is round 1's cutter, kept so round 1 can be rebuilt. The
   stored chunk text stays the exact chart slice in every mode.
3. **Prefix** every chunk with its note date and section heading, for example
   `Date: 2026-01-07. Section: Family History.`. That way "Mother has type 2
   diabetes" never travels without "Family History".
   Round 3 (`--plain-words`): a small hand-written list, `plain_words.json`, pairs plain
   wording with the chart's words ("collapsed lung" = pneumothorax, PTX; "go wrong" =
   complications). `query` adds the chart's words to a question that uses a plain phrase;
   `both` also adds a "Plain words:" line to the embedded text of every chunk that uses a
   chart word (for example "collapsed lung, lung collapse" on a chunk that says "no PTX").
   The stored chunk text never changes. No model is involved.
4. **Store** one SQLite file per model in `target/rag/<model>.sqlite`. Each file has a
   `chunks` table, an FTS5 keyword table and a sqlite-vec `vec0` vector table. The
   vector table is partitioned by patient. Raw vectors are also kept as blobs.
5. **Search** always within **one patient**: filter to that patient first, then rank
   by exact cosine distance. There is no approximate index, because one patient's
   chart is only 50 to 400 chunks.
6. **Index in short steps.** `index` embeds at most `--max-chunks` (default 400)
   missing chunks for the patient scope, then stops. Running it again skips every chunk
   that already has a vector, so the work fits into several short `heavy` calls and the
   shared build slot is freed between them. The default scope is NHSSYN001-010.
   Patients 001-003 carry the labelled facts. Patients 004-010 are "other patients"
   that per-patient filtering must keep out.
7. **Score** against `probes.json`:
   - one plain question per labelled fact in `quality/facts/nhssyn00{1,2,3}.json`;
   - eight negation probes ("Does he smoke?" where the chart says "Non-smoker");
   - eight family-history probes ("Does the patient have diabetes?" where only the
     mother does).

   Round 2 labels each probe directly (`labels.json`, the default `--labels direct`):
   the exact chart phrase(s) that answer the question, typos included, with a date
   where only one day's note answers. A chunk counts as relevant when it contains one
   phrase of every evidence group. If no single chunk does, the fact "spans chunks" and
   counts as found when the top 8 results together cover every group. Round 1's
   summary-fact regexes (`--labels regex`) stay available for comparison; they were
   looser (about 4 relevant chunks per probe against 2.5) and so overstated recall.

## How to run it

Run all commands from the worktree root. Heavy steps go through `heavy`, which waits
for a free build slot and enough memory.

```bash
# one-time: sqlite-vec into target/ only (nothing system-wide)
python3 -m pip install --target target/rag/pylib sqlite-vec

T=tools/ai-clinical-summary-draft/rag/rag_trial.py
python3 $T pull --model nomic-embed-text           # also bge-m3, qwen3-embedding:0.6b
# repeat until it reports "remaining_in_scope": 0 (each call embeds at most 400 chunks)
heavy python3 $T index --model nomic-embed-text --patients NHSSYN001-NHSSYN010 --max-chunks 400 \
    --form-fields none                                               # round 1's cutter
heavy python3 $T evaluate --model nomic-embed-text --labels regex   # round 1 as published
# round 2: a new index folder that copies round 1's vector for every unchanged chunk,
# so only the changed chunks are embedded (28 with --form-fields label, 94 with split)
heavy python3 $T index --model nomic-embed-text --index-dir r2 --reuse-from '' --form-fields label
heavy python3 $T evaluate --model nomic-embed-text --index-dir r2 --labels direct
python3 $T report --tag r2-direct                  # writes target/rag/results/report-r2-direct.md
# round 3: plain words on the questions only (reuses the r2 index as it is) ...
heavy python3 $T evaluate --model nomic-embed-text --index-dir r2 --plain-words query
# ... and on the chunks too: a new folder; only chunks that gain a "Plain words:" line are embedded
heavy python3 $T index --model nomic-embed-text --index-dir r4 --reuse-from r2 --plain-words both
heavy python3 $T evaluate --model nomic-embed-text --index-dir r4 --plain-words both
# the held-out wording (heldout.json: 24 rephrased questions, same labels) for any setup
heavy python3 $T evaluate --model nomic-embed-text --index-dir r4 --plain-words both --questions heldout

# offline tests (no Ollama, no network)
cd tools/ai-clinical-summary-draft && python3 -m unittest discover -s rag/tests -t .
```

Outputs go to `target/rag/`:

- the SQLite indexes;
- `results/<model>-<index folder>-index.json` (speed; an index that reused vectors
  times only the chunks it embedded itself, plus the model load);
- `results/<model>-<index folder>-<labels>-eval.json` (every probe, every mode, and
  vector recall at 3, 8, 12, 16 and 24 with the depth each probe needs), for example
  `nomic-embed-text-r2-direct-eval.json` (`r1` = round 1's index folder);
- `results/report-<tag>.md`.

`RESULTS.md` in this folder is the written summary.

If sqlite-vec cannot be installed or loaded, the code falls back to exact cosine in
pure Python. Every results file records which backend was used.

## Safety limits

These limits are enforced in code and covered by the tests:

- **Synthetic data only.** The only data source is the committed, checksum-verified
  FAKE NHS fixture set. Ids other than `NHSSYN###` are refused before indexing and at
  search time.
- **Local embeddings only.** Every HTTP call passes a host guard. It allows only
  `http://127.0.0.1:11434` (or `[::1]`) and only `/api/embed`, `/api/pull`,
  `/api/tags` and `/api/ps`. Hosted APIs (OpenRouter or any other) and the chat and
  generate endpoints are refused before any connection is made. Proxies and
  redirects are disabled, so a request cannot leave the machine.
- **Embeddings are patient data.** An embedding is computed from the note text, and
  research shows text can be partly reconstructed from embeddings. Treat a vector
  index exactly like the notes it came from: never send chart text to a hosted
  embedding service, never log vectors, and keep the index wherever the chart itself
  is allowed to live.
- **One patient per search.** Every search is scoped to one patient id, and results
  are re-checked so that no other patient's chunk can come back. The evaluation runs
  generic questions against every indexed patient to confirm this.
- **One model at a time**, small batches (16 texts per call). Each model is unloaded
  (`keep_alive: 0`) when its step finishes, because memory on this machine is shared.

## If this is ever built for real

Decision: the index lives **inside CARLOS's own MariaDB**, using MariaDB's native
`VECTOR` type and vector functions (**MariaDB 11.8 or newer**). It does not go in a
separate vector database or a hosted service. The reasons:

- the vectors are patient data, so they need the same database, backups, access
  control and audit as the notes they come from;
- per-patient filtering is a plain `WHERE demographic_no = ?` on the same server;
- there is no new service to secure.

Embeddings would come from a model running on the CARLOS host or the clinic's own
network, never a hosted API. **Nothing is to be built now.** This trial only informs
that decision.

## Files

- `rag_trial.py`: chunker, host guard, SQLite storage, the three search modes,
  evaluation and report.
- `probes.json`: the probe questions and relevance rules.
- `labels.json`: round 2's direct labels (the exact chart phrases that answer each probe).
- `plain_words.json`: round 3's plain-word list.
- `heldout.json`: round 3's held-out wording for 24 probes, written before any held-out result was seen.
- `tests/test_rag_trial.py`: offline unit tests.
- `RESULTS.md`: the results of rounds 1 to 3.
