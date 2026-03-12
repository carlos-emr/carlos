---
description: Write a clean room IEEE 830 functional specification from source code using Chinese Wall methodology
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Agent, WebSearch, WebFetch
model: opus
---

# /cleanroom-spec - Clean Room Functional Specification Generator

Write a black-box IEEE 830 functional specification for a given source file or component. The specification describes **observable behavior only** and contains no source code, no internal implementation details, and no copyrightable expression from the original. It serves as the sole input for a clean room reimplementation per the Chinese Wall methodology.

---

## 0. Input

The user provides one of:
- A file path to a Java source file (e.g., `src/main/java/.../SomeAction.java`)
- A class name to locate (e.g., `SplitDocument2Action`)
- A component description (e.g., "the document split action in documentManager")

If the user does not provide a target, ask which source file or component to specify.

---

## 1. Legal and Methodological Foundation

### 1.1 What Is Clean Room Design?

Clean room design (also called "Chinese Wall" methodology) is a legal technique for reverse engineering and reimplementation that avoids copyright infringement. It separates the work into two teams:

- **Dirty room team** (analyst): Reads the original source code and produces a functional specification describing only observable behavior — inputs, outputs, side effects, and external contracts.
- **Clean room team** (implementer): Reads ONLY the specification and writes new code from scratch. They never see the original source.

The specification document IS the Chinese Wall. It must contain zero copyrightable expression from the original.

**Legal precedent:**
- *Computer Associates v. Altai* (1992) — Established the Abstraction-Filtration-Comparison (AFC) test
- *Whelan v. Jaslow* — Structure-Sequence-Organization (SSO) test
- *Sega v. Accolade* (1992) — Clean room reimplementation is lawful
- *Lotus v. Borland* (1995) — Functional elements (menus, command structures) not copyrightable

### 1.2 What Is Copyrightable vs. Not

**NOT copyrightable (safe to include in spec):**
- Functional ideas, algorithms described at an abstract level
- Externally-dictated interface contracts (HTTP parameters, JSON field names, wire protocol values)
- Efficiency-driven sequences where only one reasonable way exists (merger doctrine)
- Facts: database column semantics, business rules, observable behavior
- Standard patterns dictated by frameworks or protocols

**Copyrightable (MUST NOT appear in spec):**
- Source code or pseudocode derived from the original's structure
- Variable names, method names, class names (except as disclaimed traceability references)
- The specific organizational structure of the original code
- Creative architectural choices (how modules are composed internally)
- Comments or documentation text from the original

### 1.3 The AFC Test Applied

For every element in the specification, apply this three-step filter:

1. **Abstraction**: Identify the level of abstraction — is this an idea/function or specific expression?
2. **Filtration**: Remove non-protectable elements:
   - Ideas and functional requirements (not protectable)
   - Elements dictated by external factors (framework requirements, wire protocols, hardware)
   - Elements dictated by efficiency (only one reasonable way to do it — merger doctrine)
   - Elements taken from the public domain (standard patterns, common algorithms)
3. **Comparison**: What remains after filtration? If anything in the spec matches protectable expression from the original, remove or rewrite it.

---

## 2. Research Phase

### 2.1 Read the Source Code

Read the target source file completely. Also read:
- Any parent/base classes it extends
- Key interfaces it implements
- Model/entity classes it uses (to understand data shapes)
- Configuration files that affect its behavior (Struts XML, Spring config)
- Related utility classes it calls (to understand side effects)

### 2.2 Extract Observable Behavior

For each public entry point (method, endpoint, action), document:

- **Inputs**: HTTP parameters, method arguments, session state, configuration values
- **Outputs**: HTTP responses (body, content type, status), return values, view names
- **Side effects**: Database writes (creates, updates, deletes), filesystem changes, cache operations
- **Guard conditions**: When the component takes no action or returns early
- **Error behavior**: What happens on failure — error responses, exception propagation, silent handling
- **External contracts**: Wire protocol values (parameter names, JSON fields, discriminator strings) that clients depend on

### 2.3 Identify Wire Protocol Values

Wire protocol values are exact strings that form the external contract between client and server. These MUST be preserved exactly in the specification because they are functional requirements, not implementation details:

- HTTP parameter names (`method`, `document`, `page`, `queueID`)
- HTTP parameter values that select behavior (`"split"`, `"rotate180"`)
- JSON response field names (`"newDocNum"`)
- Database discriminator values used in routing (`"DOC"`)
- Content type strings (`"application/json"`)
- Default values (`"1"` for default queue)

### 2.4 Identify Internal Implementation Details

These MUST NOT appear in the specification:

- Class names, method names, variable names from the source
- DAO/service/manager class names or bean names
- JPA/Hibernate/ORM terminology (persist, merge, flush, entity, session)
- Specific framework classes (PDFParser, PDDocument, PDPage)
- Internal control flow (if/else structure, loop patterns, try/catch arrangement)
- The number or organization of private helper methods
- Import statements or dependency injection patterns

---

## 3. Specification Writing Phase

### 3.1 Document Structure (IEEE 830)

Use this structure, adapted from IEEE 830-1998 / ISO/IEC/IEEE 29148:2011:

```
## 1. Introduction
### 1.1 Purpose
### 1.2 Scope (In scope / Out of scope)
### 1.3 Document Conventions
### 1.4 Definitions and Glossary

## 2. HTTP Interface (or External Interface)
### 2.1 Endpoint
### 2.2 HTTP Method
### 2.3 Method Dispatch (if applicable)

## 3. Functional Requirements
### 3.N <Operation Name> (one subsection per operation, alphabetical by operation name)
#### FR-XXX-N: Inputs
#### FR-XXX-N: Guard conditions (if any)
#### FR-XXX-N: <Core behavior>
#### FR-XXX-N: Side effects
#### FR-XXX-N: Response
#### FR-XXX-N: Error handling

## 4. Non-Functional Requirements
### 4.1 Security
### 4.2 Data Integrity
### 4.3 Reliability

## 5. External Dependencies

## 6. Response Summary

## 7. Client Integration Contract

## 8. Assumptions

## 9. Verification Criteria

## 10. Observed Behaviors (Non-Normative)
```

### 3.2 Header Block

Every specification MUST begin with this header block immediately after the title:

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

### 3.3 Writing Conventions

**Language precision:**
- **"shall"** = mandatory requirement
- **"should"** = recommended but optional
- **"is observed to"** = documented behavior of existing system (non-normative, §10 only)

**Ordering principle (CRITICAL):**
- ALL lists of independent items MUST be alphabetically ordered
- This includes: glossary terms, scope lists, capability lists, routing tasks, metadata fields, verification criteria, assumptions, response summary rows, observed behaviors
- Causally dependent steps (where B depends on A's output) use numbered sequential ordering
- Independent steps within a numbered sequence use unordered bullets
- This prevents accidental structural mirroring of the original source code's ordering

**Traceability notes:**
- The §1.1 Purpose section SHOULD include a traceability note linking to the original source component name
- This note MUST disclaim that the name is for traceability only and does not prescribe naming:

```markdown
**Traceability note:** This spec corresponds to the component identified as
`OriginalClassName` in the existing codebase. This name is provided solely for
traceability between the specification and the original system; it does not
prescribe a class name, method name, or any internal naming convention for
the reimplementation.
```

### 3.4 Describing Side Effects Without Implying Architecture

When describing database operations and other side effects:

**DO (functional capability):**
- "The component shall create a new document metadata record"
- "The component shall link the new document to the specified queue"
- "The component shall invalidate cached rendered versions"

**DO NOT (implied architecture):**
- "The component shall call DocumentDao.persist()" — leaks class/method names
- "The component shall merge the entity into the persistence context" — JPA jargon
- "The DocumentService shall save the record" — prescribes service name
- "The component shall use the QueueDocumentLinkDao to create the association" — prescribes DAO name

**External Dependencies section (§5):**
- List capabilities as a flat, alphabetically-ordered list
- Describe WHAT is needed, not HOW it is organized
- Explicitly state: "How these are organized (one service, many services, direct database access, etc.) is an implementation choice."
- No service names, no DAO names, no bean names
- No JPA/ORM terminology (persist, merge, flush, session, entity manager)

### 3.5 Wire Protocol vs. Internal Values

**Include exact values when they are wire protocol / external contract:**
- Parameter name `"method"` with value `"split"` — clients send this
- JSON field `"newDocNum"` — clients parse this
- Content type `"application/json"` — HTTP standard
- Database discriminator `"DOC"` — used across routing tables as a type identifier

**Use semantic descriptions for internal values:**
- Status: "active" (not `'A'` — that's an implementation encoding choice)
- Visibility: "private (not public)" (not `"0"` — that's an implementation encoding choice)
- Module: describe functionally, e.g., `"demographic"` if it is a well-known domain concept

**Judgment call:** If a value appears in database routing tables shared across multiple components, it IS an external contract (like `"DOC"`). If it's an internal field encoding (like `'A'` for active), describe it semantically.

### 3.6 Rotation and Transformation Semantics

When specifying transformation operations, be precise about whether values are:
- **Absolute**: "set rotation TO 90 degrees" (regardless of current state)
- **Additive**: "rotate BY 90 degrees from current orientation"

This distinction is a functional requirement — different behavior, different results. Always clarify which semantic applies and add a Note if both appear in the same component.

### 3.7 Verification Criteria

Write testable verification criteria for every functional requirement. Each criterion describes an observable test:

```markdown
| Test ID | Operation | Verification |
|---------|-----------|-------------|
| V-XXX-1 | Operation | Observable test description |
```

- Test IDs use a prefix derived from the operation (alphabetical within operations)
- Within each operation prefix, number sequentially
- Sort all rows by Test ID (which achieves alphabetical by operation, then numeric within)
- Every FR-xxx requirement should have at least one corresponding V-xxx test
- Tests describe observable outcomes, not implementation steps

### 3.8 Observed Behaviors (Non-Normative)

Section 10 captures behaviors that:
- ARE observable in the existing system
- Are NOT captured by the normative requirements above
- An implementer SHOULD replicate for compatibility unless there is reason to improve

**Rules for §10:**
- Must NOT contradict or duplicate normative requirements in §3
- Must NOT describe behavior already fully specified by a shall/should requirement
- Use "is observed to" language
- These are compatibility notes, not requirements

---

## 4. Review Phase (Iterative)

After writing the initial specification, perform these review passes IN ORDER. Each pass may require edits. Re-read the spec after each pass before proceeding to the next.

### Pass 1: Correctness Review

Compare every requirement against the source code:

- [ ] Every public entry point is specified
- [ ] Every input parameter is documented with correct type and format
- [ ] Every output/response is documented
- [ ] Every side effect (DB write, file change, cache operation) is captured
- [ ] Every guard condition is captured
- [ ] Every error handling path is captured
- [ ] Default values match the source
- [ ] Wire protocol values (parameter names, JSON fields) are exact
- [ ] Conditional logic (if/else branches) is captured as behavioral requirements, not as control flow
- [ ] Rotation/transformation semantics are correct (absolute vs. additive)

### Pass 2: Clean Room Compliance

Check for copyrightable expression leakage:

- [ ] No source class names appear without a disclaimed traceability note
- [ ] No method names from the source appear (e.g., `addDocumentSQL`, `getCtrlDocument`)
- [ ] No variable names from the source appear
- [ ] No DAO, service, manager, or bean names appear
- [ ] No JPA/ORM terminology (persist, merge, flush, entity, session, EntityManager)
- [ ] No framework class names (PDDocument, PDFParser, PDPage, EDoc, ActionSupport)
- [ ] No pseudocode that mirrors the source's control flow
- [ ] The structural organization (section order, requirement grouping) does NOT mirror the source code's method order or class structure
- [ ] Internal encoding values are described semantically (not as literal characters or numbers)

### Pass 3: Over-Specification Review

Check for unnecessary implementation prescription:

- [ ] Metadata field lists: are all empty/default fields individually listed? Collapse to summary line
- [ ] §5 External Dependencies: are capabilities listed as named services? Use flat capability list
- [ ] §7 Client Integration: does it describe UI behavior (thumbnails, drag-and-drop, popups)? Strip to wire format only
- [ ] FR-SPL-7 or equivalent: does it prescribe how IDs are generated? Describe postcondition instead
- [ ] Are there architecture-prescribing statements ("the service layer shall...", "using dependency injection...")?
- [ ] Does the spec dictate the number of classes, methods, or modules?

### Pass 4: Ordering Compliance

Verify the alphabetical ordering principle throughout:

- [ ] §1.2 Scope: in-scope and out-of-scope lists alphabetical
- [ ] §1.4 Glossary: terms alphabetical
- [ ] §2.3 Method dispatch table: alphabetical by method value
- [ ] §3 Operations: subsections alphabetical by operation name
- [ ] §3.x Metadata fields: alphabetical by field name
- [ ] §3.x Routing tasks: alphabetical by task name (if independent)
- [ ] §5 External Dependencies: capabilities alphabetical
- [ ] §6 Response Summary: rows alphabetical by operation name
- [ ] §7 Client Integration: response types alphabetical (if independent)
- [ ] §8 Assumptions: alphabetical by key concept
- [ ] §9 Verification Criteria: sorted by Test ID
- [ ] §10 Observed Behaviors: alphabetical by behavior name

### Pass 5: Final Leakage Scan

One last pass looking for subtle leakage:

- [ ] §10 Observed Behaviors: does any item describe behavior already captured normatively? Remove it — §10 says "not captured by normative requirements"
- [ ] Are there any source-derived ordering patterns? (e.g., listing operations in the same order as methods in the source file)
- [ ] Do requirement IDs accidentally mirror source method ordering?
- [ ] Are there any implied service boundaries that mirror the original's DAO/manager structure?
- [ ] Does the spec mention specific exception types from the source?

---

## 5. Output

### 5.1 File Location

Save the specification to:
```
docs/specs/<SourceFileName>.md
```

For example: `SplitDocument2Action.java` → `docs/specs/SplitDocument2Action_java.md`

### 5.2 Commit Message

```
docs: add clean room functional specification for <ComponentName>

IEEE 830 black-box specification describing observable behavior only.
Serves as clean room reimplementation input per Chinese Wall methodology.
```

---

## 6. Reference: Existing Specification Example

For a complete example of a finished clean room specification, see:
`docs/specs/SplitDocument2Action_java.md`

This specification covers a PDF document manipulation action with four operations (remove first page, rotate 180, rotate 90, split) and demonstrates all conventions described in this skill.

---

## 7. Common Mistakes to Avoid

1. **Listing DAO/service names in §5** — Use capability descriptions instead
2. **Using JPA terminology** — Say "create a record" not "persist an entity"
3. **Prescribing ID generation** — Say "shall have a unique ID" not "the database generates an auto-increment ID"
4. **Mirroring source method order** — Use alphabetical ordering for operations
5. **Including UI behavior in §7** — Only describe wire format and response handling
6. **Forgetting the traceability disclaimer** — Every source name mention needs one
7. **Listing empty fields individually** — Collapse to "all other fields shall be empty/default"
8. **Mixing normative and observed** — §10 must not duplicate §3 requirements
9. **Using `'A'` for active status** — Describe semantically: "active"
10. **Ambiguous rotation semantics** — Always specify absolute vs. additive
