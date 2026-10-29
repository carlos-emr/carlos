---
description: Use after a clean-room implementer has finished in a clean export - checks the evidence and brings the replacement into the repository (step 4 of the clean-room process)
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, TodoWrite
---

# /cleanroom-spec:cleanroom-audit - Audit and Swap (Dirty Room)

Check that a clean-room replacement was made without contact with the original, then bring
it into the repository with a written record.

**User input:** `$ARGUMENTS` = the export directory, optionally followed by implementer
transcript paths. If the export directory is missing, ask for it.

Run this from the original repository. This session reads both the original and the
replacement, so it is a dirty room: **never change the replacement's logic here.** A defect,
a gap or a contract mismatch goes back as a spec revision and a fresh implementer in a new
export. The only edits allowed are copying the replacement files and writing the record.

Process overview and script details: `.claude/commands/cleanroom-spec/README.md`.

---

## Steps

Use TodoWrite for steps 1–7.

### 1. Read the export's paperwork

- `<export>/CLEANROOM-MANIFEST.txt`: source commit, removed targets with their original blobs,
  spec SHA-256.
- `<export>/CLEANROOM-NOTES.md`: the implementer's questions, assumptions and test results.
  A missing notes file is a finding.
- Check the spec SHA-256 matches the approved spec in the repository (`sha256sum`).
- List the implementer's work: `git -C <export> log --oneline` and
  `git -C <export> diff --stat $(git -C <export> rev-list --max-parents=0 HEAD)`.
  A changed file that is neither a target nor a new test is a finding; callers must not have
  been edited to fit the replacement.

### 2. Audit the implementer's transcript

Find every transcript of every session that worked in the export. Claude Code keeps them
under `~/.claude/projects/<export path, each non-alphanumeric character as "-">/*.jsonl`. A
subagent's transcript sits under its parent session's folder, in
`<session-id>/subagents/agent-<id>.jsonl`; a subagent of a session that read the original
fails the audit anyway. Then run:

```bash
python3 scripts/cleanroom/audit-transcript.py --repo "$(git rev-parse --show-toplevel)" \
    --export <export-dir> <transcript.jsonl>...
```

Judge every finding and write the judgement down:
- **Harmless:** for example `git status` or `git diff` inside the export. Say why.
- **Contamination:** any read of the original repository or its history, any web search or
  fetch, or a session that ran outside the export (its deny rules never applied). **The
  implementation is rejected.** Write the record (step 6) with verdict `Rejected`; the monitor
  then deletes the export, and the work restarts with a new export and a new implementer
  session. Do not salvage parts of it.

No transcript, or an exit status of 2, means there is no evidence: stop and tell the user the
replacement cannot be certified.

### 3. Measure similarity

```bash
bash scripts/cleanroom/cpd-compare.sh <source-commit> <export-dir> <target-path>...
```

CPD (PMD's copy-paste detector, identifiers and literals ignored) reports each token run the
original and the replacement share. For every match, state which §2 contract rows dictate it
(for example declarations of required fields and their accessors). A match not explained by
§2 is a finding for the monitor to decide. A low score is supporting evidence, not proof.

### 4. Check behavior in the export

Confirm the implementer ran the tests the spec's §9 names, from the notes. Re-run them in the
export if the notes lack their output.

### 5. Swap the files

On a feature branch in the repository:
1. Copy each target from `<export>/<path>` over `<path>`, plus any new test files.
2. Check headers: the original licence notice is gone and the CARLOS GPL-2.0-or-later header
   (`docs/copyright-header-carlos.md`) is present in every copied file.
3. Run the targeted tests and a compile (`mvn -q test -Dtest=<Tests>` or
   `make install --run-unit-tests`). A failure caused by the replacement goes back as a spec
   revision; do not patch it here.

### 6. Write the record

Save `docs/specs/<SpecFileName>.record.md` containing:
- Spec path, SHA-256 and approval line
- The manifest's contents and the export commit
- Each transcript's path, SHA-256 and session ID, and where it is archived. Transcripts carry
  account details (for example the user's email), so archive them privately with the
  project's records; never commit them or attach them to a public PR
- `audit-transcript.py` output with a judgement per finding
- `cpd-compare.sh` output with the contract justification per match
- Tests run and their results, in the export and in the repository
- Each replaced file: original blob → new blob (`git rev-parse HEAD:<path>` before, `git
  hash-object <path>` after)
- Verdict: `Pass` or `Rejected`, and a final line `Signed off by: <monitor name> on <date>`
  left blank for the monitor

### 7. Report

Tell the user the verdict, the findings and their judgements, the similarity results, the
tests, and that the monitor must sign the record before the PR merges. Suggest a commit
message:

```
chore(license): replace <Component> with clean-room GPL-2.0-or-later implementation

Clean-room record: docs/specs/<SpecFileName>.record.md
```

---

## Red Flags — Stop

| Thought | Reality |
|---|---|
| "It only read the original once, and the code looks different" | Contamination is about access, not appearance. Reject and redo. |
| "There's a small bug; I'll fix it before swapping" | You have read the original. Revise the spec; a fresh implementer fixes it. |
| "No transcript, but the implementer says it never looked" | No record, no certification. |
| "CPD found 100% on a data class; that's expected" | Only if you show each match is dictated by §2. Write it per match. |
| "The record is paperwork; the code is what matters" | The record is the only evidence the separation held. |
