# Functional Specification: Ontario EDT Folder Locator

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

## 1. Introduction

### 1.1 Purpose

This specification defines the observable behavior of a component that represents the four standard folder locations used by the Ontario Electronic Data Transfer (EDT) system. The component maps folder identifiers to filesystem paths via application configuration, and provides folder-specific access control logic.

**Traceability note:** This spec corresponds to the component identified as `EDTFolder` in the existing codebase. This name is provided solely for traceability; it does not prescribe any naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Determining which folders permit file access (download)
- Mapping folder identifiers to configured filesystem paths
- Resolving a user-supplied folder name to a known folder identifier

**Out of scope:**
- Actual file I/O operations (listing, reading, writing, moving files)
- Creating or managing the physical directories on the filesystem
- Rendering UI elements for folder selection
- Security/authorization checks for folder access

### 1.3 Document Conventions

- **"shall"** = mandatory requirement
- **"should"** = recommended but optional
- **"is observed to"** = existing system behavior (non-normative, §10 only)

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| Archive folder | A storage location for previously processed EDT files that have been moved out of the inbox |
| EDT | Electronic Data Transfer — Ontario's system for exchanging healthcare billing and eligibility data with the Ministry of Health |
| File access | The ability to retrieve (download) individual files from a folder |
| Folder identifier | One of the four recognized folder types: archive, inbox, outbox, sent |
| Inbox folder | The receiving location for incoming EDT files from the Ministry of Health |
| MCEDT | Medical Care Electronic Data Transfer — the broader Ontario healthcare data exchange framework |
| Outbox folder | The staging location for files awaiting upload to the Ministry of Health |
| Sent folder | The location for files that have been successfully uploaded to the Ministry of Health |

## 2. External Interface

### 2.1 Programmatic API

This component is a data type with a fixed set of four folder identifiers. It exposes three capabilities:

| Capability | Input | Output |
|-----------|-------|--------|
| File access check | A folder identifier | Boolean: whether the folder permits file access |
| Folder lookup by name | A string folder name | A folder identifier |
| Path retrieval | A folder identifier | A filesystem path string (or null) |

### 2.2 Configuration Interface

The component reads four configuration properties at initialization time. The property keys follow the wire protocol pattern `ONEDT_` followed by the uppercase folder identifier:

| Property Key | Folder |
|-------------|--------|
| `ONEDT_ARCHIVE` | Archive |
| `ONEDT_INBOX` | Inbox |
| `ONEDT_OUTBOX` | Outbox |
| `ONEDT_SENT` | Sent |

Each property value shall be an absolute filesystem path to the corresponding directory.

## 3. Functional Requirements

### 3.1 File Access Check

#### FR-FAC-1: Input
The operation shall accept a folder identifier.

#### FR-FAC-2: Access determination
The operation shall return true if the folder identifier is archive or inbox. The operation shall return false if the folder identifier is outbox or sent.

#### FR-FAC-3: Response
The operation shall return a boolean value.

### 3.2 Folder Lookup by Name

#### FR-FLN-1: Input
The operation shall accept a string representing a folder name.

#### FR-FLN-2: Case-insensitive matching
The operation shall compare the input string against all known folder identifiers using case-insensitive comparison.

#### FR-FLN-3: Successful match
If the input string matches a known folder identifier (case-insensitively), the operation shall return the corresponding folder identifier.

#### FR-FLN-4: Default on no match
If the input string does not match any known folder identifier, or if the input is null, the operation shall return the inbox folder identifier.

#### FR-FLN-5: Response
The operation shall return a folder identifier.

### 3.3 Path Retrieval

#### FR-PTH-1: Input
The operation shall accept a folder identifier (implicitly — by being invoked on a specific folder).

#### FR-PTH-2: Configuration loading
At initialization time, the component shall read the filesystem path for each folder from the application configuration property corresponding to that folder (see §2.2).

#### FR-PTH-3: Response
The operation shall return the configured filesystem path string. If the property was not configured, the operation shall return null.

## 4. Non-Functional Requirements

### NFR-1: Immutability
The set of folder identifiers shall be fixed at compile time. No folder identifiers may be added or removed at runtime.

### NFR-2: Initialization timing
Path values shall be resolved from configuration once at initialization time, not on every retrieval.

### NFR-3: Thread safety
The component shall be safe to use from multiple threads concurrently, as the path values are immutable after initialization.

## 5. External Dependencies

The component requires the following capabilities:

- Configuration property retrieval: the ability to read string values from the application's configuration store by property key

How these are organized (one service, many services, direct configuration access, etc.) is an implementation choice.

## 6. Response Summary

| Operation | Input | Output |
|-----------|-------|--------|
| File access check | Folder identifier | Boolean |
| Folder lookup by name | String name | Folder identifier |
| Path retrieval | Folder identifier | String filesystem path (or null) |

## 7. Client Integration Contract

### 7.1 Configuration Property Keys

Clients and configuration files shall use the following exact property key strings to configure folder paths. These are wire protocol values forming the external contract:

- `ONEDT_ARCHIVE`
- `ONEDT_INBOX`
- `ONEDT_OUTBOX`
- `ONEDT_SENT`

### 7.2 Folder Name Strings

The folder lookup operation shall accept the following name strings (case-insensitive):

- `archive`
- `inbox`
- `outbox`
- `sent`

Any other string shall resolve to the inbox folder.

### 7.3 File Access Policy

Callers shall rely on the file access check to determine whether individual file retrieval is permitted from a given folder. The policy is:

| Folder | File Access Permitted |
|--------|----------------------|
| Archive | Yes |
| Inbox | Yes |
| Outbox | No |
| Sent | No |

## 8. Assumptions

- **Configuration availability**: The application configuration store is available at the time the component initializes.
- **Path validity**: The configured paths point to valid, accessible filesystem directories. Path validation is the caller's responsibility.
- **Property key stability**: The `ONEDT_*` property key names are stable external contracts shared across multiple system components.

## 9. Verification Criteria

| Test ID | Requirement | Verification |
|---------|-------------|-------------|
| V-FAC-1 | FR-FAC-2 | Given the archive folder identifier, the file access check shall return true |
| V-FAC-2 | FR-FAC-2 | Given the inbox folder identifier, the file access check shall return true |
| V-FAC-3 | FR-FAC-2 | Given the outbox folder identifier, the file access check shall return false |
| V-FAC-4 | FR-FAC-2 | Given the sent folder identifier, the file access check shall return false |
| V-FLN-1 | FR-FLN-2, FR-FLN-3 | Given the input string "INBOX", the folder lookup shall return the inbox folder identifier |
| V-FLN-2 | FR-FLN-2, FR-FLN-3 | Given the input string "inbox", the folder lookup shall return the inbox folder identifier |
| V-FLN-3 | FR-FLN-2, FR-FLN-3 | Given the input string "InBox", the folder lookup shall return the inbox folder identifier |
| V-FLN-4 | FR-FLN-2, FR-FLN-3 | Given the input string "archive", the folder lookup shall return the archive folder identifier |
| V-FLN-5 | FR-FLN-4 | Given the input string "unknown", the folder lookup shall return the inbox folder identifier |
| V-FLN-6 | FR-FLN-4 | Given an empty string, the folder lookup shall return the inbox folder identifier |
| V-FLN-7 | FR-FLN-4 | Given a null input, the folder lookup shall return the inbox folder identifier |
| V-PTH-1 | FR-PTH-2, FR-PTH-3 | When `ONEDT_INBOX` is configured to "/data/edt/inbox/", the path retrieval for the inbox folder shall return "/data/edt/inbox/" |
| V-PTH-2 | FR-PTH-3 | When `ONEDT_INBOX` is not configured, the path retrieval for the inbox folder shall return null |
| V-PTH-3 | FR-PTH-2 | Each folder identifier shall read its path from its corresponding `ONEDT_*` property key |

## 10. Observed Behaviors (Non-Normative)

- The component is observed to be used as a request parameter value in Ontario billing file management workflows, where the folder name is passed via an HTTP query parameter named `folder`.
- The default-to-inbox behavior (FR-FLN-4) is observed to serve as a safety net when callers provide invalid or missing folder names, ensuring the system always navigates to a valid folder.
- The file access restriction on outbox and sent folders (FR-FAC-2) is observed to prevent download of files that are in transit or already submitted, which are managed by automated upload processes rather than manual user access.
