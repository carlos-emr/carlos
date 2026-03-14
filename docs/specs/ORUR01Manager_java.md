# Functional Specification: HL7 ORU^R01 Message Version Accessor Utility

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

---

## 1. Introduction

### 1.1 Purpose

This specification defines the externally observable behavior of a utility component that provides type-safe access to HL7 v2 ORU^R01 (Observation Result / Unsolicited) messages across multiple HL7 versions, and a version string parser. It serves as the sole input for a clean room reimplementation.

**Traceability note:** This spec corresponds to the component identified as `ORUR01Manager` in the existing codebase. This name is provided solely for traceability between the specification and the original system; it does not prescribe a class name, method name, or any internal naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Parsing HL7 version strings into integer representations
- Type-safe casting of generic message objects to version-specific ORU^R01 message types

**Out of scope:**
- Construction, modification, or serialization of HL7 messages
- HL7 message parsing from raw text or network streams
- Routing, storing, or processing the content of HL7 messages
- The internal implementation approach — any architecture that produces the specified observable behavior is acceptable

### 1.3 Document Conventions

- The word **"shall"** indicates a mandatory requirement.
- The word **"should"** indicates a recommended but optional behavior.
- The phrase **"is observed to"** indicates documented behavior of the existing system that the reimplementation should replicate for compatibility, but which is not part of the formal functional contract.
- All lists of independent items are **alphabetically ordered** to prevent structural mirroring.
- Causally dependent steps are numbered sequentially; independent steps use unordered bullets.

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| **HL7 v2** | Health Level Seven International version 2.x messaging standard, used for exchanging clinical and administrative data between healthcare systems. |
| **ORU^R01** | An HL7 v2 message type representing an "Observation Result / Unsolicited" transmission, commonly used for laboratory results. The message structure varies by HL7 version. |
| **Version string** | A dot-separated numeric string representing an HL7 version (e.g., `"2.2"`, `"2.3"`, `"2.5"`, `"2.6"`). |

---

## 2. Programmatic Interface

This component provides a stateless programmatic interface. It has no HTTP endpoint, no configuration, and no persistent state. All operations are independent and require no instance state.

### 2.1 Operations

| Operation | Description |
|-----------|-------------|
| Cast to version-specific ORU^R01 | Accepts a generic object and returns it typed as a version-specific ORU^R01 message |
| Parse version string | Accepts a version string and returns its integer representation |

---

## 3. Functional Requirements

### 3.1 Cast to Version-Specific ORU^R01

#### FR-CAST-1: Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| message | Generic object | Yes | An HL7 message object that is expected to be of the target ORU^R01 version type |
| target version | Determined by which cast operation is invoked | Yes | The HL7 version to cast to |

#### FR-CAST-2: Supported versions

The component shall support casting to ORU^R01 message types for the following HL7 versions (alphabetical):

- HL7 v2.2
- HL7 v2.3
- HL7 v2.5
- HL7 v2.6

#### FR-CAST-3: Behavior

The component shall return the input object typed as the version-specific ORU^R01 message type corresponding to the requested version. No transformation or validation of the object's contents shall occur — this is a pure type-narrowing operation.

#### FR-CAST-4: Error handling

If the input object is not actually an instance of the target ORU^R01 version type, the component shall propagate the resulting class cast error to the caller.

---

### 3.2 Parse Version String

#### FR-PVS-1: Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| version | String | Yes | A dot-separated HL7 version string (e.g., `"2.5"`) |

#### FR-PVS-2: Behavior

1. The component shall remove all dot characters (`.`) from the input string.
2. The component shall parse the resulting string as an integer and return it.

For example, `"2.5"` shall produce `25`, and `"2.6"` shall produce `26`.

#### FR-PVS-3: Empty input handling

If removing dots from the input results in an empty string, the component shall return `0`.

#### FR-PVS-4: Error handling

If the input string (after dot removal) contains non-numeric characters, the component shall propagate the resulting number format error to the caller.

---

## 4. Non-Functional Requirements

### 4.1 Statelessness

- **NFR-SL-1:** The component shall maintain no state between invocations. All operations shall be pure functions.

### 4.2 Thread Safety

- **NFR-TS-1:** The component shall be safe to call from multiple threads concurrently, as it maintains no mutable state.

---

## 5. External Dependencies

The component requires the following capabilities from the surrounding system. How these are organized (one library, many libraries, etc.) is an implementation choice.

- HL7 v2 message type definitions for ORU^R01 in versions 2.2, 2.3, 2.5, and 2.6

---

## 6. Response Summary

| Operation | Return Value |
|-----------|-------------|
| Cast to ORU^R01 (valid input) | The input object typed as the version-specific ORU^R01 message |
| Cast to ORU^R01 (wrong type) | Class cast error propagated to caller |
| Parse version string (empty after dot removal) | `0` |
| Parse version string (non-numeric after dot removal) | Number format error propagated to caller |
| Parse version string (valid) | Integer with dots removed (e.g., `"2.5"` → `25`) |

---

## 7. Client Integration Contract

### 7.1 Version-Specific Casting

Callers shall determine the HL7 version of a message through external means (e.g., inspecting the message header) and invoke the appropriate cast operation for that version. The caller is responsible for ensuring the input object is of the correct type.

### 7.2 Version String Parsing

Callers shall provide a dot-separated version string (e.g., `"2.5"`). The returned integer can be used for version-based dispatch logic (e.g., comparing `25` vs `23` to select behavior).

---

## 8. Assumptions

- Callers provide objects that are genuinely instances of the target ORU^R01 version type when invoking cast operations.
- Version strings follow the HL7 convention of dot-separated numeric components (e.g., `"2.3"`, `"2.5"`).

---

## 9. Verification Criteria

An implementation shall be considered correct if it satisfies all of the following observable tests:

| Test ID | Operation | Verification |
|---------|-----------|-------------|
| V-CAST-1 | Cast to ORU^R01 v2.2 | Given a valid HL7 v2.2 ORU^R01 message object, the return value shall be the same object typed as the v2.2 ORU^R01 type. |
| V-CAST-2 | Cast to ORU^R01 v2.3 | Given a valid HL7 v2.3 ORU^R01 message object, the return value shall be the same object typed as the v2.3 ORU^R01 type. |
| V-CAST-3 | Cast to ORU^R01 v2.5 | Given a valid HL7 v2.5 ORU^R01 message object, the return value shall be the same object typed as the v2.5 ORU^R01 type. |
| V-CAST-4 | Cast to ORU^R01 v2.6 | Given a valid HL7 v2.6 ORU^R01 message object, the return value shall be the same object typed as the v2.6 ORU^R01 type. |
| V-CAST-5 | Cast (type mismatch) | Given an object that is not the target ORU^R01 type, a class cast error shall be raised. |
| V-PVS-1 | Parse version `"2.2"` | Return value shall be `22`. |
| V-PVS-2 | Parse version `"2.3"` | Return value shall be `23`. |
| V-PVS-3 | Parse version `"2.5"` | Return value shall be `25`. |
| V-PVS-4 | Parse version `"2.6"` | Return value shall be `26`. |
| V-PVS-5 | Parse version `"."` | Return value shall be `0` (empty string after dot removal). |
| V-PVS-6 | Parse version `"abc"` | A number format error shall be raised. |

---

## 10. Observed Behaviors (Non-Normative)

These notes document externally observable characteristics of the existing system that are not captured by the normative requirements above. They are provided for compatibility reference. An implementer should replicate these behaviors unless there is a clear reason to improve upon them.

- **No caller references** — The component is observed to have no active callers in the current codebase. It may be a candidate for removal, or it may be used by dynamically loaded or external code not visible in static analysis.
- **Regex-based dot removal** — The version string parser is observed to use a regular expression pattern for removing dots. The pattern also matches newline characters adjacent to dots, meaning a string like `"2\n.\n5"` would produce `25`. Whether to preserve this behavior is an implementation choice.
- **Unsupported version gaps** — The component supports versions 2.2, 2.3, 2.5, and 2.6 but not 2.1 or 2.4. The version string parser can produce integer values for any version, but type-safe cast operations exist only for the four supported versions.
