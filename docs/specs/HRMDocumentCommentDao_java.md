# HRM Document Comment Data Access — Functional Specification

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

This specification defines the observable behavior of a data access component responsible for managing comments associated with Hospital Report Manager (HRM) documents in a healthcare EMR system. It describes the operations for creating, retrieving, updating, and soft-deleting document comments.

**Traceability note:** This spec corresponds to the component identified as `HRMDocumentCommentDao` in the existing codebase. This name is provided solely for traceability; it does not prescribe any naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Counting all comment records
- Creating new comment records
- Hard-deleting comment records
- Retrieving a comment record by unique identifier
- Retrieving comments for a specific document (excluding soft-deleted)
- Soft-deleting a comment record by unique identifier
- Updating existing comment records

**Out of scope:**
- Access control or privilege checking (handled by calling components)
- Comment text validation or sanitization
- Full-text search across comment content
- HRM document lifecycle management
- Notification of comment changes to other users
- User interface rendering of comments

### 1.3 Document Conventions

- **"shall"** = mandatory requirement
- **"should"** = recommended but optional
- **"is observed to"** = existing system behavior (non-normative, §10 only)
- All field names are logical descriptors; actual column or property names are implementation choices

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| Comment record | A data record representing a textual annotation attached to an HRM document |
| Comment timestamp | The date and time when the comment was created |
| Document identifier | An integer that uniquely identifies the HRM document to which a comment belongs |
| HRM document | A hospital report received and managed within the Hospital Report Manager module |
| Provider number | A string identifier for the healthcare provider who authored a comment |
| Soft delete | Marking a record as logically deleted without physically removing it from storage |
| Unique identifier | An auto-generated integer primary key assigned to each comment record |

---

## 2. External Interface

This component provides a programmatic data access interface (not an HTTP or CLI interface). It is invoked by other application components to manage comment records in persistent storage.

### 2.1 Data Record Structure

Each comment record shall contain the following fields:

| Field | Type | Description |
|-------|------|-------------|
| Comment text | String | The body of the comment |
| Comment timestamp | Date/time | When the comment was authored |
| Deleted flag | Boolean | Whether the record is soft-deleted; defaults to not deleted |
| Document identifier | Integer | The HRM document this comment belongs to |
| Provider number | String | Identifier of the provider who authored the comment |
| Unique identifier | Integer | Auto-generated primary key |

---

## 3. Functional Requirements

### 3.1 Count All Records

#### FR-CNT-1: Inputs
This operation shall accept no input parameters.

#### FR-CNT-2: Core behavior
The component shall count the total number of comment records in storage, including soft-deleted records.

#### FR-CNT-3: Response
The operation shall return the total count as an integer.

---

### 3.2 Create Comment Record

#### FR-CRE-1: Inputs
The operation shall accept a comment record populated with:
- Comment text
- Comment timestamp
- Document identifier
- Provider number

The deleted flag shall default to not deleted if not explicitly set.

#### FR-CRE-2: Core behavior
The component shall store the comment record in persistent storage. Upon successful storage, the record shall be assigned a unique identifier.

#### FR-CRE-3: Response
The operation shall make the record available for subsequent retrieval. The record shall have a unique identifier after creation.

---

### 3.3 Hard-Delete Comment Record

#### FR-HRD-1: Inputs
The operation shall accept either:
- A comment record instance, or
- A unique identifier (integer or object)

#### FR-HRD-2: Guard conditions
When a unique identifier is provided and no matching record exists, the operation shall return a result indicating no record was removed.

#### FR-HRD-3: Core behavior
The component shall permanently remove the comment record from persistent storage.

#### FR-HRD-4: Response
When invoked with a unique identifier, the operation shall return a boolean: true if a record was removed, false otherwise.

---

### 3.4 Retrieve All Records

#### FR-ALL-1: Inputs
The operation shall accept two optional parameters:
- **Offset**: an integer indicating how many records to skip (applied when greater than zero)
- **Limit**: an integer indicating the maximum number of records to return

#### FR-ALL-2: Guard conditions
If the requested limit exceeds the system maximum (5000 records), the operation shall signal an error indicating the limit has been exceeded.

#### FR-ALL-3: Core behavior
The component shall return comment records from persistent storage, including soft-deleted records. When no limit is specified, the system maximum (5000) shall be applied.

#### FR-ALL-4: Response
The operation shall return a list of comment records. The list may be empty if no records exist.

---

### 3.5 Retrieve Comment by Identifier

#### FR-RBI-1: Inputs
The operation shall accept a unique identifier (integer or object).

#### FR-RBI-2: Core behavior
The component shall look up the comment record matching the provided identifier, including soft-deleted records.

#### FR-RBI-3: Response
The operation shall return the matching comment record, or null if no record matches the identifier.

---

### 3.6 Retrieve Comments for Document

#### FR-RCD-1: Inputs
The operation shall accept a document identifier (integer).

#### FR-RCD-2: Core behavior
The component shall retrieve all comment records matching the given document identifier where the deleted flag indicates the record is not deleted.

#### FR-RCD-3: Ordering
Results shall be ordered by comment timestamp in descending order (most recent first).

#### FR-RCD-4: Response
The operation shall return a list of non-deleted comment records. The list may be empty if no matching non-deleted comments exist.

---

### 3.7 Save or Update Comment Record

#### FR-SAV-1: Inputs
The operation shall accept a comment record.

#### FR-SAV-2: Core behavior
- If the record has a unique identifier (i.e., it has been previously stored), the component shall update the existing record in storage.
- If the record does not have a unique identifier, the component shall create a new record in storage.

#### FR-SAV-3: Response
The operation shall return the comment record.

---

### 3.8 Soft-Delete Comment Record

#### FR-SDC-1: Inputs
The operation shall accept a unique identifier (integer) of the comment to soft-delete.

#### FR-SDC-2: Guard conditions
If no comment record exists with the provided identifier, the operation shall take no action.

#### FR-SDC-3: Core behavior
1. The component shall retrieve the comment record by its unique identifier.
2. The component shall set the deleted flag TO deleted (absolute, not toggled).
3. The component shall save the updated record to persistent storage.

#### FR-SDC-4: Side effects
The record shall remain in storage but shall no longer appear in results from the "Retrieve Comments for Document" operation (§3.6).

---

### 3.9 Update Comment Record

#### FR-UPD-1: Inputs
The operation shall accept a comment record with modified field values.

#### FR-UPD-2: Core behavior
The component shall update the existing record in persistent storage with the provided field values.

#### FR-UPD-3: Response
The operation shall synchronize the updated record to persistent storage.

---

## 4. Non-Functional Requirements

| ID | Requirement |
|----|------------|
| NFR-1 | All operations shall execute within a transactional context to ensure data consistency |
| NFR-2 | The component shall support concurrent access from multiple callers |
| NFR-3 | The system maximum for list retrieval shall be 5000 records |

---

## 5. External Dependencies

The component requires the following capabilities:

- Counting records in persistent storage
- Generating unique integer identifiers for new records
- Persistent storage and retrieval of comment records
- Querying records by field values with filtering and ordering
- Transactional record creation, update, and deletion

How these are organized (one service, many services, direct database access, etc.) is an implementation choice.

---

## 6. Response Summary

| Operation | Response |
|-----------|----------|
| Count All Records | Integer count |
| Create Comment Record | Record available with unique identifier |
| Hard-Delete Comment Record | Boolean (true if removed, false otherwise) when called by identifier |
| Retrieve All Records | List of comment records (may be empty) |
| Retrieve Comment by Identifier | Comment record or null |
| Retrieve Comments for Document | List of non-deleted comment records, ordered by timestamp descending |
| Save or Update Comment Record | The comment record |
| Soft-Delete Comment Record | No return value |
| Update Comment Record | No return value |

---

## 7. Client Integration Contract

### 7.1 Data Record Contract

Clients shall interact with comment records containing the fields described in §2.1. All fields are readable. The unique identifier is assigned by the system upon creation and shall not be set by the caller.

### 7.2 Soft-Delete Semantics

Clients should be aware that:
- The "Retrieve All Records" operation (§3.4) and "Retrieve Comment by Identifier" operation (§3.5) include soft-deleted records
- The "Retrieve Comments for Document" operation (§3.6) excludes soft-deleted records
- The "Soft-Delete Comment Record" operation (§3.8) is idempotent — calling it on an already-deleted record sets the flag to deleted again with no observable difference

### 7.3 Ordering Guarantee

The "Retrieve Comments for Document" operation guarantees descending timestamp ordering. No ordering guarantee is provided for other retrieval operations.

---

## 8. Assumptions

| Assumption | Detail |
|-----------|--------|
| Caller responsibility | Callers are responsible for populating comment text, document identifier, provider number, and timestamp before creating a record |
| Deleted flag default | New records default to not-deleted unless explicitly set otherwise |
| Document existence | The document identifier refers to a valid HRM document; referential integrity enforcement is an implementation choice |
| Transactional context | Operations are invoked within an active transaction managed by the calling infrastructure |
| Unique identifier generation | The persistent storage system generates unique integer identifiers automatically |

---

## 9. Verification Criteria

| Test ID | Requirement | Verification |
|---------|-------------|-------------|
| V-ALL-1 | FR-ALL-1 | Invoke retrieve-all with offset and limit; verify the returned list respects both parameters |
| V-ALL-2 | FR-ALL-2 | Invoke retrieve-all with a limit exceeding 5000; verify an error is signaled |
| V-ALL-3 | FR-ALL-3 | Create multiple records, invoke retrieve-all with default limit; verify all records are returned including soft-deleted ones |
| V-CNT-1 | FR-CNT-2 | Create several records (some soft-deleted), invoke count; verify the count includes all records |
| V-CRE-1 | FR-CRE-2 | Create a comment record with all fields populated; verify it can be retrieved by its assigned unique identifier |
| V-CRE-2 | FR-CRE-2 | Create a comment record without setting deleted flag; verify the record's deleted flag defaults to not deleted |
| V-HRD-1 | FR-HRD-3 | Create a record, hard-delete it, attempt retrieval by identifier; verify null is returned |
| V-HRD-2 | FR-HRD-2 | Invoke hard-delete with a non-existent identifier; verify false is returned |
| V-RBI-1 | FR-RBI-2 | Create a record, retrieve by identifier; verify the returned record matches all stored fields |
| V-RBI-2 | FR-RBI-3 | Retrieve with a non-existent identifier; verify null is returned |
| V-RCD-1 | FR-RCD-2 | Create multiple comments for one document, soft-delete one; invoke retrieve-for-document; verify the soft-deleted comment is excluded |
| V-RCD-2 | FR-RCD-3 | Create comments with different timestamps for one document; invoke retrieve-for-document; verify results are in descending timestamp order |
| V-RCD-3 | FR-RCD-4 | Invoke retrieve-for-document with a document identifier that has no comments; verify an empty list is returned |
| V-SAV-1 | FR-SAV-2 | Create a new record via save-or-update (no identifier set); verify it receives a unique identifier |
| V-SAV-2 | FR-SAV-2 | Modify a previously-saved record and invoke save-or-update; verify the changes are reflected on retrieval |
| V-SDC-1 | FR-SDC-3 | Create a record, soft-delete it; verify the deleted flag is set to deleted |
| V-SDC-2 | FR-SDC-2 | Invoke soft-delete with a non-existent identifier; verify no error occurs and no records are affected |
| V-SDC-3 | FR-SDC-4 | Soft-delete a record, then invoke retrieve-for-document; verify the record is excluded from results |
| V-SDC-4 | FR-SDC-3 | Soft-delete an already-soft-deleted record; verify no error occurs (idempotent behavior) |
| V-UPD-1 | FR-UPD-2 | Create a record, modify its comment text, invoke update; verify the change is persisted |

---

## 10. Observed Behaviors (Non-Normative)

| Observation | Detail |
|------------|--------|
| Arbitrary parameterized queries | The component is observed to support executing arbitrary parameterized native SQL queries, returning raw result sets |
| Batch operations | The component is observed to support batch creation and batch hard-deletion of multiple records with configurable batch sizes, defaulting to 25 records per batch |
| Detached retrieval | The component is observed to support retrieving a record by identifier in a detached state, meaning subsequent modifications to the returned record are not automatically synchronized to storage |
| Flush capability | The component is observed to support forcing pending changes to be immediately synchronized to persistent storage |
| Record existence check | The component is observed to support checking whether a given record is currently managed by the active transactional context |
| Refresh capability | The component is observed to support refreshing a managed record's state from persistent storage, discarding any in-memory modifications |
