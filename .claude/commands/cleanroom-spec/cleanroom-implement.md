---
description: Use when starting a clean-room implementation from an approved spec inside a clean export made by scripts/cleanroom/make-clean-export.sh (step 3 of the clean-room process)
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, TodoWrite
---

# /cleanroom-spec:cleanroom-implement - Clean-Room Implementation

Write the replacement for the component described in `CLEANROOM-SPEC.md`, working only from
that spec and the rest of the export. You are the **clean room**: you have never seen the
original and must not see it now. The audit afterwards checks this session's transcript, so
every file you open and every command you run is part of the record.

**User input:** `$ARGUMENTS` (normally empty; everything you need is in the export).

Process overview: `.claude/commands/cleanroom-spec/README.md`.

---

## Step 1: Check You Are in a Clean Room

Run, from the session's working directory:

```bash
test -f CLEANROOM-SPEC.md && test -f CLEANROOM-MANIFEST.txt && echo files-ok
grep -m1 '^Status: Approved' CLEANROOM-SPEC.md
git rev-list --count HEAD    # must print 1, or 1 + your own commits from this session
sed -n '/^removed/,$p' CLEANROOM-MANIFEST.txt
```

All of these must hold:
- `CLEANROOM-SPEC.md` and `CLEANROOM-MANIFEST.txt` are in the working directory
- The spec's status line says approved
- The git history is the single export commit (plus only your own commits)
- None of the files listed under `removed` exist yet in the export
- This session has never been given the original's text, and is not a fork of a session that
  wrote the spec or read the original

If any fails, **stop**. Tell the user the clean room is not set up and give them the setup
from the README (approve the spec, run `make-clean-export.sh`, start a new session in the
export). Do not continue "carefully" in the original repository instead.

## Step 2: Read Only What the Wall Allows

**Allowed:**
- `CLEANROOM-SPEC.md` (read it completely first) and `CLEANROOM-MANIFEST.txt`
- Any other file inside the export: callers, base classes, entities, schema, configuration,
  existing tests
- Your general knowledge of Java, the frameworks and the domain

**Not allowed, even once:**
- Anything outside the export directory, including other checkouts of this project
- Web search, web fetch, or downloading code or documents (upstream OSCAR copies of the
  original are published online)
- Downloading tools or runtimes outside the project's own build. If the build needs something
  that is missing, record it and stop that check; do not fetch it
- Recalling the original from memory. If you recognize the component and remember how an
  upstream project wrote it, do not use that memory; write from the spec
- Asking the user to paste, describe or confirm the original's code
- Editing `.claude/` settings, the spec, or the manifest

## Step 3: Confirm the Contract

For each row of the spec's §2 Interface Contract, open the caller or artifact it cites and
confirm what it needs (signature, field name, stored value). If a caller needs something §2
does not list, that is a spec gap: record it as a question (Step 6).

## Step 4: Tests First

Turn the spec's §9 verification criteria into tests in the export's test tree (follow the
project's test conventions, e.g. `CLAUDE.md` and `docs/test/`). Keep every test you write in
the export: the tests are part of the deliverable and the evidence, so do not write throwaway
checks elsewhere. Give each new file the CARLOS GPL-2.0-or-later header from
`docs/copyright-header-carlos.md`.

## Step 5: Implement

Create each file listed under `removed` in the manifest, at that exact path, with:
- The CARLOS GPL-2.0-or-later header for new files, and no other licence notice
- The names and signatures from §2 exactly; everything else is your own design
- Behavior from §3 only

Do not change callers or other existing files to fit your implementation. If a caller cannot
work with what §2 allows, that is a spec question.

Build and run the tests the spec names plus your own. Record exactly what ran and its result.
If the environment cannot run something (missing JDK version, no database), record that and
what you ran instead; do not work around it by downloading.

## Step 6: Questions and Decisions

- **The spec decides it:** follow the spec.
- **The spec is silent, and a caller, a test or stored data could observe the difference:**
  do not guess. Add it to the notes as an open question and leave that behavior for the
  monitor; the spec is revised and the work resumes.
- **The spec is silent, and nothing outside the component could observe it** (for example a
  stable tie-break order the spec calls unspecified): choose, and record the choice and why.

## Step 7: Write `CLEANROOM-NOTES.md`

At the export root:
- **Files read:** every path you opened or searched, in order
- **Commands run:** every command, in order, with outcomes
- **Contract check:** each §2 row and the caller you confirmed it against
- **Decisions:** each choice from Step 6 and its reason
- **Open questions:** each spec gap, with the observable behavior it affects
- **Tests:** what ran, where, results; anything that could not run and why

## Step 8: Commit Locally and Report

```bash
git add -A && git commit -m "clean-room implementation of <component>"
```

The export has no remote; never add one or push from it. Report to the user: the export path,
the commit, the files created, test results, open questions, and the next step: run
`/cleanroom-spec:cleanroom-audit <export-dir>` from the original repository. If there are open
questions, say the spec needs revising before the audit.

---

## Red Flags — Stop

| Thought | Reality |
|---|---|
| "The original is right there; one look would settle this" | One look rejects the whole implementation. Record a question. |
| "I'll check git history to see how it used to work" | The export has no history on purpose. History is the original. |
| "I remember how OSCAR implemented this" | Memory of the original is the original. Write from the spec. |
| "I'll quickly search the web for the class name" | Upstream copies are online. No web access. |
| "The spec is ambiguous; I'll pick what the original probably did" | If a caller could notice, it's an open question for the monitor. |
| "My extra checks can live in a scratch folder" | Tests in the export are part of the evidence. Keep them. |
| "The build needs JDK 25; I'll download one" | Record the gap and what you ran instead. No downloads. |
| "The setup isn't quite a clean export, but I'll be careful" | Careful is not evidence. Stop and ask for a clean export. |
