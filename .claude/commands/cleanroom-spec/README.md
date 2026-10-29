---
description: Clean Room Reimplementation Commands
---

# Clean Room Reimplementation Commands

Commands for replacing a CARLOS file with a clean-room reimplementation, for example to
replace GPL-2.0-only code with GPL-2.0-or-later code. The original's text never reaches the
person or session that writes the replacement; only a black-box spec does.

Before starting a relicensing effort, consider asking the copyright holders for consent. That
is often cheaper than rewriting, and a clean room is then needed only for holders who refuse
or cannot be reached.

## Available Commands

- `/cleanroom-spec:cleanroom-spec <target>` — step 1, dirty room: write the spec
- `/cleanroom-spec:cleanroom-implement` — step 3, clean room: write the replacement from the spec
- `/cleanroom-spec:cleanroom-audit <export-dir>` — step 4, dirty room: check the evidence and swap the files

## The Process

| Step | Who | Sees the original? | What |
|---|---|---|---|
| 1. Spec | A session in the repository | Yes | `/cleanroom-spec:cleanroom-spec <target>` writes `docs/specs/<Name>.md` with `Status: Draft` |
| 2. Approve | A named human (the monitor) | Yes | Reviews the spec; sets `Status: Approved by <name> on <date>`; commits it |
| 3a. Export | The monitor | — | `bash scripts/cleanroom/make-clean-export.sh <export-dir> <spec> <target>...` |
| 3b. Implement | A **new** session started in the export | **Never** | `/cleanroom-spec:cleanroom-implement` |
| 4. Audit and swap | A session in the repository, then the monitor | Yes | `/cleanroom-spec:cleanroom-audit <export-dir>`; the monitor signs the record |

Rules that hold the wall up:
- A session that has read the original never writes or fixes the replacement, and neither
  does any fork of it.
- The implementer's only window into the old behavior is the approved spec. Its questions
  are answered by revising the spec (which resets it to Draft), never with code.
- A transcript finding that shows contact with the original rejects the replacement. Start
  again with a new export and a new session; do not salvage.

## Scripts (`scripts/cleanroom/`)

| Script | Used in | What it does |
|---|---|---|
| `make-clean-export.sh` | Step 3a | Copies the committed tree (no `.git`) outside the repo, removes the targets, adds the spec as `CLEANROOM-SPEC.md`, writes `CLEANROOM-MANIFEST.txt`, denies the implementer read access to the original repo and the web, and makes a one-commit git repo so its work is a clean diff. Refuses an unapproved spec. |
| `audit-transcript.py` | Step 4 | Lists every tool call in the implementer's transcript that touched the original repo, used the web, ran git history or download commands, or ran outside the export. |
| `cpd-compare.sh` | Step 4 | Runs PMD's copy-paste detector between each original and its replacement, ignoring identifiers and literals, and reports the shared token runs. |

The export is a full copy of the tree (about 1 GB). Delete it after the record is written.

## Evidence Trail

Each replaced component ends up with:
- `docs/specs/<Name>.md` — the approved spec (with §11 Provenance)
- `docs/specs/<Name>.record.md` — manifest, transcript hashes, audit findings and judgements,
  similarity results, tests, replaced blobs, verdict, and the monitor's sign-off
- The implementer transcript(s), archived with the PR

## Limits

- Not legal advice. No court has ruled on AI-run clean rooms; for large or contested
  components, get counsel.
- Upstream OSCAR code is public, so any model may have seen it in training. The separation
  and the records are what support the independence claim.
- A spec writer who later implements, or an implementer who reads the original even once,
  breaks the process for that component.
