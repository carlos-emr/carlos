---
description: Use when a CARLOS file must be replaced by a clean-room reimplementation (for example GPL-2.0-only code being relicensed) - writes the black-box spec, step 1 of the clean-room process
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Agent, TodoWrite
model: opus
---

# /cleanroom-spec - Clean Room Functional Specification (Dirty Room)

Write a black-box functional specification for a source file or component that is being
replaced by a clean-room reimplementation. The spec describes **observable behavior and the
externally dictated interface only**. It is the only thing that crosses the wall to the
implementer.

**User input:** `$ARGUMENTS`

If `$ARGUMENTS` is empty, ask the user which source file or component to specify.
Otherwise, resolve the argument as a file path, class name, or component description.

---

## The Clean-Room Process

This command is step 1. The session that runs it has read the original, so it is the
**dirty room** for this component from now on.

| Step | Who | Sees the original? | How |
|---|---|---|---|
| 1. Spec | This session (dirty room) | Yes | `/cleanroom-spec:cleanroom-spec <target>` |
| 2. Approve | A named human (the monitor) | Yes | Reviews the spec, sets its `Status:` line to approved |
| 3. Implement | A fresh session in a clean export | **Never** | `/cleanroom-spec:cleanroom-implement` |
| 4. Audit and swap | Dirty room, then the monitor | Yes | `/cleanroom-spec:cleanroom-audit <export-dir>` |

**You have read the original. You must not write, edit, review line by line, or suggest code
for its replacement, in this session or in any session that inherits this context (forks
included).** Your only output is the spec. Questions from the implementer are answered by
revising the spec, never by sending code, snippets, or "the original does X internally".

The overview, scripts and evidence trail for the whole process are in
`.claude/commands/cleanroom-spec/README.md`.

---

## Execution Overview

Use TodoWrite to track each phase.

```
Phase 0: SCOPE     — Coupled files, licence holders, choose Light or Full track
Phase 1: RESEARCH  — Observable behavior + interface contract derived from callers
Phase 2: WRITE     — Spec with Status: Draft, saved to docs/specs/
Phase 3: REVIEW    — Light: 2 passes. Full: 5 passes. Re-read the file before each pass
Phase 4: HANDOFF   — Stop. Report to the human; do not implement
```

---

## Phase 0: Scope

1. **Confirm the licence.** Record the target's licence header and copyright holders. If the
   goal is relicensing, tell the user once that asking the holders for consent can be cheaper
   than a clean room (projects such as Dolphin, KDE and OpenSSL relicensed that way and
   rewrote only code whose authors refused). Then continue.
2. **Find coupled files with the same licence.** List files the target cannot work without, or
   that exist only to serve it (its entity/model, interface, implementation, JSP, form bean),
   and check their headers. Report any with the same licence. Recommend specifying them
   together as one unit, or record in §1.2 which stay out of scope and why.
3. **Choose the track** from the target's code (excluding header, imports and blank lines):

| Track | When | Spec sections | Review passes |
|---|---|---|---|
| **Light** | Data class (fields + accessors), pure markup/script page, interface, or ≤ 60 code lines with no branching beyond null/default handling | §1.1, §1.2, §2, §3, §9 (others optional) | 2 (A and B below) |
| **Full** | Everything else | All sections | 5 (1–5 below) |

---

## Phase 1: Research

Read the target source file completely. Also read:
- Parent/base classes it extends and interfaces it implements
- Model/entity classes it uses (data shapes)
- Configuration that routes to it (Struts XML, Spring config, `web.xml`)
- Utility classes it calls (side effects)
- The database schema for any table it touches (`database/mysql/migration/**`)

For each public entry point, extract:
- **Inputs**: HTTP parameters, method arguments, session state, configuration values
- **Outputs**: HTTP responses (body, content type, status), return values, view names
- **Side effects**: Database writes, filesystem changes, cache operations
- **Guard conditions**: When the component takes no action or returns early
- **Error behavior**: What happens on failure
- **External contracts**: Values that clients, callers or stored data depend on

### Interface Contract: Derive It From the Callers

A CARLOS replacement must drop in without changing its callers, so some names are not
optional. Derive them from **the code that uses the target**, not from the target itself:

```bash
# Every caller, test, config and page that names the target (run per class/page name)
grep -rln --include=*.java --include=*.jsp --include=*.jspf --include=*.xml \
     --include=*.js --include=*.properties '<SimpleName>' src/
```

For each caller, record which members it uses. Also check:
- **Query strings and EL** that name the target's fields (`x.fieldName`, `${bean.property}`)
- **Struts results, action names and JSP paths** that route to the target
- **Database table and column names** from the schema
- **Persisted values** the target reads or writes (status codes, flags, discriminators)
- **Test code** that stubs, mocks or reflects on the target (field names reached by reflection
  are part of the contract)

Every item goes into spec §2 with the caller that requires it. A name, member or value that
no caller, configuration, schema or stored data requires is **not** part of the contract and
must not appear in the spec.

### Contract Values vs. Internal Values

**Contract values** appear exactly (they are functional requirements):
- Names and signatures required by callers (see above)
- HTTP parameter names and values that select behavior; JSON field names; content types
- Values **stored in the database or exchanged with other components** (existing rows and
  other code depend on them, e.g. a status column holding `A` or `D`)

**Internal values** (used only inside the target, never stored or exchanged) are described
semantically: "the active state", not the literal that encodes it.

### What MUST NOT Appear in the Spec

- Any name, signature or value not justified by an entry in §2
- Private helper names, local variable names, or the number/organization of private methods
- ORM/framework mechanics (persist, merge, flush, session, EntityManager) except where a
  caller-visible contract requires naming a shared base type
- Internal control flow (if/else structure, loop patterns, try/catch arrangement)
- The original's comments, Javadoc wording, log messages or exception messages (unless a
  message is shown to users or matched by callers, which makes it a contract value)
- Import statements or dependency-injection patterns

---

## Phase 2: Write the Specification

### Document Structure

```
# Functional Specification: <Component Display Name>

Status: Draft
<header block>

## 1. Introduction
### 1.1 Purpose (with traceability note)
### 1.2 Scope (In scope / Out of scope, including coupled files left out)
### 1.3 Document Conventions          (Full)
### 1.4 Definitions and Glossary      (Full)

## 2. Interface Contract (Externally Dictated)

## 3. Functional Requirements
### 3.N <Operation Name> (one subsection per operation, alphabetical)
#### FR-XXX-1: Inputs
#### FR-XXX-2: Guard conditions (if any)
#### FR-XXX-3: <Core behavior steps>
#### FR-XXX-N: Side effects
#### FR-XXX-N: Response
#### FR-XXX-N: Error handling

## 4. Non-Functional Requirements     (Full)
## 5. External Dependencies           (Full)
## 6. Response Summary                (Full)
## 7. Client Integration Contract     (Full)
## 8. Assumptions                     (Full)
## 9. Verification Criteria
## 10. Observed Behaviors (Non-Normative)  (Full, optional)
## 11. Provenance (Non-Normative)
```

### Status Line

The line directly under the title is `Status: Draft`. Only the human monitor changes it, to
`Status: Approved by <name> on <YYYY-MM-DD>`. `scripts/cleanroom/make-clean-export.sh` refuses
a spec without that approved line. Any later revision resets it to `Status: Draft`.

### Header Block

Every specification has this block after the status line:

```markdown
> **Clean Room Specification**
>
> This document is a black-box functional specification. It describes observable behavior and
> the interface that existing callers, stored data and configuration dictate. It contains no
> source code and no other expression from any existing implementation, and it is the only
> input given to the clean-room implementer.
>
> **Filtration:** Elements dictated by external factors (existing callers, database schema,
> stored values, wire protocols) are listed in §2 with the caller or artifact that requires
> each one; they are functional requirements, not expression. Everything else describes
> behavior and is organized independently (alphabetical or causal ordering), not in the order
> or structure of any prior implementation.
>
> **Standards applied:** Document structure adapted from IEEE 830-1998 / ISO/IEC/IEEE
> 29148:2011. Two-team clean-room (Chinese Wall) method.
>
> **Methodology references:**
> - [AI Could Be Your Next Team for Clean Room Development (Copyleft Currents)](https://heathermeeker.com/2025/03/28/ai-could-be-your-next-team-for-clean-room-development/)
> - [Can coding agents relicense open source through a "clean room" implementation? (Simon Willison)](https://simonw.substack.com/p/can-coding-agents-relicense-open)
> - [Clean-room design (Wikipedia)](https://en.wikipedia.org/wiki/Clean-room_design)
> - [IEEE 830-1998 SRS Structure (Rebus Press)](https://press.rebus.community/requirementsengineering/back-matter/appendix-c-ieee-830-template/)
> - [Preventing an IP Infection: Clean Room Development Procedure (IPWatchdog)](https://ipwatchdog.com/2023/04/29/preventing-an-ip-infection-clean-room-development-procedure/id=160187/)
```

### §1.1 Traceability Note

```markdown
**Traceability note:** This spec replaces the component at `<repo-relative path>`. The names
listed in §2 are required by existing callers, stored data or configuration and shall be kept
exactly. No other naming, structure or organization is prescribed.
```

### §2 Interface Contract

One table per kind of contract item that applies. Every row names what requires it.

```markdown
| Contract item | Exact value | Required by |
|---|---|---|
| Fully qualified class name | `io.github.carlos_emr.carlos.example.ExampleDao` | `ExampleAction.java` (imports it), Spring scan |
| Method | `public List<Example> findOpen(Integer)` | `ExampleAction.java`, `example.jsp` |
| Stored value: closed state | `D` | `example` table rows; `ExampleReport.java` query |
```

Sort rows alphabetically by contract item. Then add one sentence: this contract is dictated by
compatibility with existing code and data, so reproducing it reproduces no protectable
expression.

### Writing Rules

**Language precision:**
- **"shall"** = mandatory requirement
- **"should"** = recommended but optional
- **"is observed to"** = existing system behavior (non-normative, §10 only)

**Ordering principle (CRITICAL):**
- ALL independent lists are alphabetical (glossary, scope, capabilities, metadata fields,
  routing tasks, verification criteria, assumptions, response rows, observed behaviors,
  contract rows)
- Causally dependent steps (B depends on A's output) use numbered sequential ordering
- This prevents accidental structural mirroring of the source code's ordering

**Side effects — describe capabilities, not architecture:**

| DO (functional) | DO NOT (leaks implementation) |
|---|---|
| "shall create a new document record" | "shall call DocumentDao.persist()" |
| "shall link the document to the queue" | "shall merge the entity into the persistence context" |
| "shall invalidate cached versions" | "shall use the QueueDocumentLinkDao" |

**§5 External Dependencies:** Flat alphabetical capability list. Describe WHAT is needed, not
HOW organized. Include: "How these are organized (one service, many services, direct database
access, etc.) is an implementation choice." Shared base types appear only if §2 lists them.

**Transformation semantics:** "set value TO X" (absolute) vs. "change value BY X" (additive).

**§9 Verification Criteria:** Every FR-xxx needs at least one V-xxx test. Sort by Test ID.
Tests describe observable outcomes, not implementation steps. Name existing tests that must
keep passing (tests are callers too).

**§10 Observed Behaviors:** Observable but not normative. Must not contradict or duplicate §3.
Use "is observed to" language.

**§11 Provenance (non-normative):** Target path(s); the git blob of each original
(`git rev-parse HEAD:<path>`) and the commit it was read at; coupled same-licence files and
whether they are in this unit; the session that wrote the spec; date.

### Save the Spec

`mkdir -p docs/specs/`, then save to `docs/specs/<SourceFileName>.md`
(`SomeAction.java` → `docs/specs/SomeAction_java.md`). For a multi-file unit, name it after
the main file and list every target in §1.2 and §11.

---

## Phase 3: Review

**Protocol for EVERY pass:** READ the whole spec from disk with the Read tool, REVIEW against
the checklist, EDIT every issue, REPORT "Pass N complete. Found X issues, fixed Y." Do not
review from memory and do not combine passes; edits in one pass can introduce problems only a
fresh read shows.

### Light track: 2 passes

**Pass A (Correctness and Contract):** re-read the source and every caller found in Phase 1.
Every caller-used member, routed path, schema name and stored value is in §2 with its
requirer; nothing in §2 lacks one. Every behavior a caller or test can observe is in §3; every
FR has a V test; defaults and guard conditions match the source.

**Pass B (Leakage and Ordering):** every name and literal outside §2 is justified as a contract
value or removed; no private names, control flow, comments or messages from the original;
every independent list is alphabetical.

### Full track: 5 passes

**Pass 1: Correctness.** Re-read the source and callers (context may have been compacted).
- [ ] Every public entry point is specified
- [ ] Every input documented with correct type and format
- [ ] Every output/response documented
- [ ] Every side effect captured (DB write, file change, cache op)
- [ ] Every guard condition and error path captured
- [ ] Default values and contract values are exact
- [ ] Conditional logic captured as behavioral requirements, not control flow
- [ ] Transformation semantics correct (absolute vs. additive)
- [ ] §2 complete against the callers, and every §2 row names its requirer
- [ ] §9 covers every FR-xxx

**Pass 2: Clean Room Compliance.** Zero expression from the source outside §2.
- [ ] Every class, method, field and table name outside §2 is removed or moved into §2 with a requirer
- [ ] No private helper or local variable names
- [ ] No ORM mechanics beyond what §2 requires
- [ ] No framework class names beyond what §2 requires
- [ ] No pseudocode mirroring the source's control flow
- [ ] No text copied from the source's comments, Javadoc, or log/exception messages
- [ ] Internal-only values described semantically; stored/exchanged values exact and in §2

**Pass 3: Over-Specification.** Only observable behavior, never architecture.
- [ ] Empty/default metadata fields collapsed to a summary line
- [ ] §5 is a flat capability list with the implementation-choice disclaimer
- [ ] §7 describes call patterns and wire format only, no UI behavior
- [ ] No requirement prescribes HOW IDs are generated ("shall have a unique ID")
- [ ] No architecture-prescribing statements ("the service layer shall...", "using DI...")
- [ ] Spec does not dictate the number of classes, methods, or modules beyond §2

**Pass 4: Ordering Compliance.** For EVERY list and table, extract the sort keys and verify
A→Z: scope, glossary, §2 rows, §3 subsections, fields within each operation, §5, §6, §7, §8,
§9 (by Test ID), §10. Only causal sequences are exempt.

**Pass 5: Final Leakage Scan.** Fresh eyes.
- [ ] §10 does not duplicate §3
- [ ] Operations and FR IDs are not in source method order
- [ ] No implied service boundaries mirror the source's DAO/manager structure
- [ ] No source exception types unless §2 lists them
- [ ] Every term is understandable without reading the source

### Review Summary

```
## Review Summary (<Light|Full> track)
- Pass <N> (<name>): X issues found, Y fixed
- Total: X issues found across N passes
```

---

## Phase 4: Handoff

Stop here. Do not implement, do not create the export for an implementer you will brief, and
do not offer to "just write it". Report to the user:

1. Spec path, track, and `Status: Draft`
2. The §2 contract rows a human should sanity-check, and any names you were unsure were really
   required
3. Coupled same-licence files left out of scope
4. Next steps, verbatim:
   - A named human reviews the spec and changes its status line to
     `Status: Approved by <name> on <YYYY-MM-DD>`, then commits it.
   - Create the clean export **outside** the repository:
     `bash scripts/cleanroom/make-clean-export.sh <export-dir> <spec> <target-path>...`
   - Start a **new** Claude Code session in `<export-dir>` (not a fork of this one) and run
     `/cleanroom-spec:cleanroom-implement`.
   - When it finishes, run `/cleanroom-spec:cleanroom-audit <export-dir>` from the repository.

### Commit Message (for the approved spec)

```
docs: add clean room functional specification for <ComponentName>

Black-box specification describing observable behavior and the caller-dictated
interface only. Sole input for the clean-room reimplementation.
```

### Reference Example

`docs/specs/SplitDocument2Action_java.md` shows the behavior sections in full. It predates the
§2 Interface Contract, Status line and §11 Provenance requirements; add those when following it.

---

## Red Flags — Stop

| Thought | Reality |
|---|---|
| "I already know the code; writing the replacement myself is faster" | You are the dirty room. Code you write is tainted by what you read, however different it looks. Hand off. |
| "I'll send the implementer a quick snippet to unblock them" | Code crossing the wall contaminates the result. Revise the spec instead. |
| "The skill bans class names, so I'll rename things and let callers break" | Caller-required names are contract. Put them in §2 with their requirers. |
| "I'll include this helper's name, it's clearer" | Not required by a caller → not in the spec. Describe the behavior. |
| "Five passes is overkill for a 2-field class" | Correct: use the Light track. Do not skip Pass A or B. |
| "The approval step is a formality; I'll mark it approved" | Only the human monitor changes the status line. |

---

## Clean Room Legal Background

Reference material, not execution instructions. Not legal advice; get counsel for anything
large or contested.

- **What it is:** a two-team method. The dirty room reads the original and writes a functional
  spec; the clean room writes new code from the spec alone. It is evidence of independent
  creation, not a legal requirement, and its value depends on the records that show the
  separation held (who saw what, when, and what crossed the wall).
- **Key doctrine:** *Computer Associates v. Altai* (1992) abstraction-filtration-comparison
  filters out ideas, externally dictated interfaces, efficiency-driven sequences and public
  domain material; *Whelan v. Jaslow* treats structure, sequence and organization as
  potentially protectable; the merger doctrine removes protection where there is only one
  reasonable way to express a function; *Sega v. Accolade* (1992) and *Lotus v. Borland*
  (1995) support reimplementing functional elements; *Google v. Oracle* (2021) decided
  reuse of API declarations on fair use without ruling on their copyrightability.
- **Historical practice:** Phoenix's IBM BIOS clone used engineers who had seen the original to
  write specs for engineers who had not. In *NEC v. Intel* a clean-room rewrite was accepted as
  evidence that NEC's microcode was not copied.
- **AI clean rooms (unsettled):** no court has ruled on AI-run clean rooms. The 2026 chardet
  relicensing showed the weak points: a rewriter with long exposure to the original, an agent
  that opened one original file during the rewrite, and models likely trained on the original.
  Its strengths were an empty starting repository, a written plan, and a similarity score.
  CARLOS's process answers these with a separate session in an export that holds no copy or
  history of the original, a transcript audit, a similarity check, and a human approval gate.
  Upstream OSCAR code is public, so assume any model may have seen it; the records are what
  make the claim credible.
- **Copyrightable (never in the spec):** source code, private names, the original's
  organization and creative architecture, comments and documentation text.
- **Not copyrightable (allowed):** functional behavior, facts and business rules, interfaces
  that callers and stored data dictate, framework-dictated patterns.

---

## Common Mistakes

1. **Banning caller-required names** — breaks the drop-in; put them in §2 with their requirers
2. **Listing names no caller needs** — leaks expression; describe the behavior instead
3. **Literal internal encodings** — semantic unless the value is stored or exchanged (then §2)
4. **Missing coupled files** — the entity or interface next to the target often has the same licence
5. **Implementing after writing the spec** — the spec writer is the dirty room, permanently
6. **JPA terminology in §5** — "create a record", not "persist an entity"
7. **Prescribing ID generation** — "shall have a unique ID", not "database auto-increment"
8. **Mirroring source method order** — operations alphabetical
9. **Mixing normative and observed** — §10 must not duplicate §3
10. **Ambiguous transformation semantics** — absolute vs. additive
11. **Self-approving** — only the human monitor sets `Status: Approved`
