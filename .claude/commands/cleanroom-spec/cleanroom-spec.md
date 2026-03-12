---
description: Write a clean room IEEE 830 functional specification from source code using Chinese Wall methodology
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Agent, TodoWrite
model: opus
---

# /cleanroom-spec - Clean Room Functional Specification Generator

Write a black-box IEEE 830 functional specification for a given source file or component.
The specification describes **observable behavior only** — no source code, no internal
implementation details, no copyrightable expression from the original. It serves as the
sole input for a clean room reimplementation per the Chinese Wall methodology.

**User input:** `$ARGUMENTS`

If `$ARGUMENTS` is empty, ask the user which source file or component to specify.
Otherwise, resolve the argument as a file path, class name, or component description.

---

## Execution Overview

This skill has 3 phases. Use TodoWrite to track progress through each phase.

```
Phase 1: RESEARCH  — Read source code, extract observable behavior
Phase 2: WRITE     — Produce IEEE 830 spec, save to docs/specs/
Phase 3: REVIEW    — 5 mandatory passes with file re-reads between each
```

---

## Phase 1: Research

Read the target source file completely. Also read:
- Parent/base classes it extends
- Key interfaces it implements
- Model/entity classes it uses (to understand data shapes)
- Configuration files that affect its behavior (Struts XML, Spring config)
- Related utility classes it calls (to understand side effects)

For each public entry point, extract:
- **Inputs**: HTTP parameters, method arguments, session state, configuration values
- **Outputs**: HTTP responses (body, content type, status), return values, view names
- **Side effects**: Database writes (creates, updates, deletes), filesystem changes, cache operations
- **Guard conditions**: When the component takes no action or returns early
- **Error behavior**: What happens on failure
- **External contracts**: Wire protocol values that clients depend on

### Wire Protocol vs. Internal Values

**Wire protocol values** are exact strings forming the external contract. Include them
exactly in the spec — they are functional requirements:
- HTTP parameter names and values that select behavior
- JSON response field names
- Database discriminator values used in routing tables across components
- Content type strings, default values

**Internal values** must NOT appear as literals. Describe them semantically:
- Status encodings → "active" (not `'A'`)
- Visibility flags → "private" (not `"0"`)
- Internal field encodings are implementation choices, not external contracts

**Judgment call:** If a value appears in routing tables shared across multiple components,
it IS an external contract. If it's an internal field encoding, describe it semantically.

### What MUST NOT Appear in the Spec

- Class names, method names, variable names from the source
- DAO/service/manager class names or bean names
- JPA/Hibernate/ORM terminology (persist, merge, flush, entity, session)
- Specific framework library classes
- Internal control flow (if/else structure, loop patterns, try/catch arrangement)
- The number or organization of private helper methods
- Import statements or dependency injection patterns

---

## Phase 2: Write the Specification

### Document Structure (IEEE 830)

```
## 1. Introduction
### 1.1 Purpose
### 1.2 Scope (In scope / Out of scope)
### 1.3 Document Conventions
### 1.4 Definitions and Glossary

## 2. External Interface (HTTP Interface, CLI, etc.)

## 3. Functional Requirements
### 3.N <Operation Name> (one subsection per operation, alphabetical)

## 4. Non-Functional Requirements

## 5. External Dependencies

## 6. Response Summary

## 7. Client Integration Contract

## 8. Assumptions

## 9. Verification Criteria

## 10. Observed Behaviors (Non-Normative)
```

### Header Block

Every specification MUST begin with this block immediately after the title:

```markdown
> **Clean Room Specification**
>
> This document is a black-box functional specification describing observable behavior only.
> It contains no source code, no internal implementation details, and no copyrightable
> expression from any existing implementation. It is intended to serve as the sole input
> for a clean room reimplementation per the Chinese Wall methodology.
>
> **Ordering principle:** All lists, tables, and enumerated items in this specification
> follow deterministic ordering: alphabetical by display name, or causal (where step B
> depends on step A's output). Independent items are always alphabetical. This prevents
> accidental structural mirroring of any prior implementation.
>
> **SSO/AFC compliance:** This specification has been audited against the
> Structure-Sequence-Organization test (*Whelan v. Jaslow*) and the
> Abstraction-Filtration-Comparison test (*Computer Associates v. Altai*, 1992).
> All non-protectable elements (functional ideas, externally-dictated interface
> contracts, efficiency-driven sequences) have been identified. Remaining content
> describes only observable behavior, using independent organizational choices
> (alphabetical ordering, phase-based grouping, unordered sets for independent
> operations) that do not mirror the structure of any prior implementation.
>
> **Standards applied:**
> - Document structure adapted from IEEE 830-1998 / ISO/IEC/IEEE 29148:2011
> - Clean room methodology per Chinese Wall technique
>
> **Methodology references:**
> - [AI Could Be Your Next Team for Clean Room Development (Copyleft Currents)](https://heathermeeker.com/2025/03/28/ai-could-be-your-next-team-for-clean-room-development/)
> - [Clean-room design (Wikipedia)](https://en.wikipedia.org/wiki/Clean-room_design)
> - [How Clean Room Reverse Engineering Built the Modern Tech Industry (NTARI)](https://www.ntari.org/post/how-clean-room-reverse-engineering-built-the-modern-tech-industry)
> - [IEEE 830-1998 SRS Structure (Rebus Press)](https://press.rebus.community/requirementsengineering/back-matter/appendix-c-ieee-830-template/)
> - [Preventing an IP Infection: Clean Room Development Procedure (IPWatchdog)](https://ipwatchdog.com/2023/04/29/preventing-an-ip-infection-clean-room-development-procedure/id=160187/)
```

### Writing Rules

**Language precision:**
- **"shall"** = mandatory requirement
- **"should"** = recommended but optional
- **"is observed to"** = existing system behavior (non-normative, §10 only)

**Ordering principle (CRITICAL):**
- ALL independent lists MUST be alphabetically ordered (glossary, scope, capabilities,
  metadata fields, routing tasks, verification criteria, assumptions, response rows,
  observed behaviors)
- Causally dependent steps (B depends on A's output) use numbered sequential ordering
- This prevents accidental structural mirroring of the source code's ordering

**Traceability:** §1.1 SHOULD include a traceability note with a disclaimer:

```markdown
**Traceability note:** This spec corresponds to the component identified as
`OriginalClassName` in the existing codebase. This name is provided solely for
traceability; it does not prescribe any naming convention for the reimplementation.
```

**Side effects — describe capabilities, not architecture:**

| DO (functional) | DO NOT (leaks implementation) |
|---|---|
| "shall create a new document record" | "shall call DocumentDao.persist()" |
| "shall link the document to the queue" | "shall merge the entity into the persistence context" |
| "shall invalidate cached versions" | "shall use the QueueDocumentLinkDao" |

**§5 External Dependencies:** Flat alphabetical capability list. Describe WHAT is needed,
not HOW organized. Include: "How these are organized (one service, many services, direct
database access, etc.) is an implementation choice." No service/DAO/bean names. No JPA terms.

**Transformation semantics:** When operations transform data, be precise:
- **Absolute**: "set value TO X" (regardless of current state)
- **Additive**: "change value BY X from current" (relative to current state)

**§9 Verification Criteria:** Every FR-xxx needs at least one V-xxx test. Sort by Test ID.
Tests describe observable outcomes, not implementation steps.

**§10 Observed Behaviors:** Captures behaviors that ARE observable but are NOT covered by
normative requirements. Must NOT contradict or duplicate §3. Use "is observed to" language.

### Save the Spec

Save to `docs/specs/<SourceFileName>.md` (e.g., `SomeAction.java` → `docs/specs/SomeAction_java.md`).

---

## Phase 3: Review (5 Mandatory Passes)

**CRITICAL EXECUTION PROTOCOL**: Each pass is a distinct operation. You MUST re-read the
spec file from disk between passes. The purpose is to review the ACTUAL FILE STATE after
edits, not your memory of what you wrote.

**Why:** Edits during one pass can introduce new problems only visible to a fresh read.

**Protocol for EVERY pass:**

```
1. READ    — Use the Read tool to read the ENTIRE spec file from disk
2. REVIEW  — Apply the pass checklist against the content you just read
3. EDIT    — Fix every issue found using the Edit tool
4. REPORT  — Output: "Pass N (Name) complete. Found X issues, fixed Y." List each fix.
```

**DO NOT** skip the Read step. **DO NOT** review from memory. **DO NOT** combine passes.

Update the TodoWrite tracker as you complete each pass.

---

### Pass 1: Correctness

**Goal**: Every observable behavior in the source is accurately captured.

Read the spec from disk. Compare every requirement against the source code:

- [ ] Every public entry point is specified
- [ ] Every input parameter documented with correct type and format
- [ ] Every output/response documented
- [ ] Every side effect captured (DB write, file change, cache op)
- [ ] Every guard condition captured
- [ ] Every error handling path captured
- [ ] Default values match the source
- [ ] Wire protocol values are exact
- [ ] Conditional logic captured as behavioral requirements, not control flow
- [ ] Transformation semantics are correct (absolute vs. additive, if applicable)
- [ ] Verification criteria (§9) cover every FR-xxx requirement

Fix issues. Report.

---

### Pass 2: Clean Room Compliance

**Goal**: Zero copyrightable expression from the source.

Read the spec from disk. Scan every line for leakage:

- [ ] No source class names without a disclaimed traceability note
- [ ] No method names from the source
- [ ] No variable names from the source
- [ ] No DAO, service, manager, or bean names
- [ ] No JPA/ORM terminology (persist, merge, flush, entity, session, EntityManager)
- [ ] No framework library class names
- [ ] No pseudocode mirroring the source's control flow
- [ ] Structural organization does NOT mirror source method order or class structure
- [ ] Internal encoding values described semantically, not as literal chars/numbers

Fix issues — replace leaked names with functional descriptions. Report.

---

### Pass 3: Over-Specification

**Goal**: Spec prescribes only observable behavior, never architecture or implementation.

Read the spec from disk. Check:

- [ ] Empty/default metadata fields collapsed to summary line (not individually listed)
- [ ] §5 uses flat capability list, not named services
- [ ] §7 describes wire format only, no UI behavior (thumbnails, drag-and-drop, popups)
- [ ] No requirement prescribes HOW IDs are generated (use postcondition: "shall have a unique ID")
- [ ] No architecture-prescribing statements ("the service layer shall...", "using DI...")
- [ ] Spec does not dictate number of classes, methods, or modules
- [ ] §5 has no JPA/ORM terminology
- [ ] §5 does not organize capabilities into groups that mirror source DAO/manager structure
- [ ] §5 includes the implementation-choice disclaimer

Fix issues. Report.

---

### Pass 4: Ordering Compliance

**Goal**: Every independent list is alphabetically ordered.

Read the spec from disk. Check EVERY list, table, and enumeration:

- [ ] §1.2 Scope lists alphabetical
- [ ] §1.4 Glossary alphabetical
- [ ] §2 dispatch/routing tables alphabetical
- [ ] §3 operation subsections alphabetical by name
- [ ] §3.x metadata fields alphabetical within each operation
- [ ] §3.x independent routing/linking tasks alphabetical
- [ ] §5 capabilities alphabetical
- [ ] §6 response summary rows alphabetical by operation
- [ ] §7 response types alphabetical (if independent)
- [ ] §8 assumptions alphabetical by key concept
- [ ] §9 verification rows sorted by Test ID
- [ ] §10 observed behaviors alphabetical

For each list: extract sort keys, verify A→Z. Only exception: causally dependent sequences.

Fix violations. Report.

---

### Pass 5: Final Leakage Scan

**Goal**: Catch subtle leakage that earlier passes may have missed or introduced.

Read the spec from disk. Fresh-eyes scan:

- [ ] §10 items do not duplicate normative requirements from §3
- [ ] Operations are NOT in source method order (should be alphabetical)
- [ ] FR-xxx IDs do not accidentally mirror source method ordering
- [ ] No implied service boundaries mirror the source's DAO/manager structure
- [ ] No source exception types mentioned
- [ ] §5 reads as a capability list, not a DAO inventory
- [ ] No "Note" blockquotes reference source method names
- [ ] Every term is understandable without reading the source

Fix issues. Report.

---

### Post-Review

Output a summary:

```
## Review Summary
- Pass 1 (Correctness): X issues found, Y fixed
- Pass 2 (Clean Room): X issues found, Y fixed
- Pass 3 (Over-Specification): X issues found, Y fixed
- Pass 4 (Ordering): X issues found, Y fixed
- Pass 5 (Leakage Scan): X issues found, Y fixed
- Total: X issues found across 5 passes
```

---

## Output

### Commit Message

```
docs: add clean room functional specification for <ComponentName>

IEEE 830 black-box specification describing observable behavior only.
Serves as clean room reimplementation input per Chinese Wall methodology.
```

### Reference Example

For a complete finished specification, see `docs/specs/SplitDocument2Action_java.md`.

---

## Clean Room Legal Background

This section provides legal context. It is reference material — not execution instructions.

**Clean room design** (Chinese Wall methodology) separates work into two teams:
- **Dirty room** (analyst): Reads source, produces functional spec with observable behavior only
- **Clean room** (implementer): Reads ONLY the spec, writes new code from scratch

The spec IS the Chinese Wall. Zero copyrightable expression may cross it.

**Key legal tests:**
- **AFC test** (*Computer Associates v. Altai*, 1992): Abstraction → Filtration → Comparison.
  Filter out non-protectable elements (ideas, externally-dictated interfaces, efficiency-driven
  sequences, public domain patterns). Only what remains after filtration is protectable.
- **SSO test** (*Whelan v. Jaslow*): Structure-Sequence-Organization can be protectable
- **Merger doctrine**: When only one reasonable way to express a function exists, the expression
  merges with the idea and is not protectable
- *Sega v. Accolade* (1992): Clean room reimplementation is lawful
- *Lotus v. Borland* (1995): Functional elements not copyrightable

**Not copyrightable** (safe in spec): functional ideas, external interface contracts,
efficiency-driven sequences, facts (business rules, observable behavior), framework-dictated patterns.

**Copyrightable** (MUST NOT appear): source code, variable/method/class names (except disclaimed
traceability), organizational structure of the original, creative architectural choices, original comments/docs.

---

## Common Mistakes

1. **Listing DAO/service names in §5** — Use capability descriptions
2. **JPA terminology** — "create a record" not "persist an entity"
3. **Prescribing ID generation** — "shall have a unique ID" not "database auto-increment"
4. **Mirroring source method order** — Alphabetical ordering for operations
5. **UI behavior in §7** — Wire format and response handling only
6. **Missing traceability disclaimer** — Every source name needs one
7. **Listing empty fields individually** — Collapse to "all other fields shall be empty/default"
8. **Mixing normative and observed** — §10 must not duplicate §3
9. **Literal internal encodings** — Describe semantically: "active" not `'A'`
10. **Ambiguous transformation semantics** — Specify absolute vs. additive
