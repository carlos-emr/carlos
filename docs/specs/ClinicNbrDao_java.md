# Clinic Number Data Access — Functional Specification

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

This specification defines the observable behavior of a data access component responsible for managing clinic number records. Clinic numbers are administrative identifiers used in clinic and billing contexts within a healthcare EMR system.

**Traceability note:** This spec corresponds to the component identified as `ClinicNbrDao` in the existing codebase. This name is provided solely for traceability; it does not prescribe any naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Adding new clinic number records
- Listing all non-deleted clinic number records
- Soft-deleting clinic number records by identifier

**Out of scope:**
- Authentication and authorization (handled by calling layers)
- Editing existing clinic number records (no update-in-place operation exists)
- Hard deletion of records from the database
- User interface rendering

### 1.3 Document Conventions

- **"shall"** = mandatory requirement
- **"should"** = recommended but optional
- **"is observed to"** = existing system behavior (non-normative, §10 only)
- All field names in this document refer to the logical data model, not physical column names

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| Clinic number record | A record associating a numeric or alphanumeric value with a human-readable description, used as an administrative identifier in clinic and billing workflows |
| Deleted status | A logical state indicating the record has been soft-deleted and should be excluded from active queries |
| Soft delete | Marking a record as logically deleted without physically removing it from storage |

## 2. External Interface

This component provides a programmatic data access interface. It is not accessed via HTTP directly; it is consumed by other application components (such as administrative pages and billing forms).

The interface exposes three domain-specific operations plus standard inherited operations from a generic data access base (see §3).

## 3. Functional Requirements

### 3.1 Add Clinic Number Record

#### FR-ADD-1: Inputs

The operation shall accept two string inputs:
- **value**: the clinic number value (whitespace-trimmed; if the result is empty, stored as null)
- **description**: the human-readable label for the clinic number (whitespace-trimmed; if the result is empty, stored as null)

#### FR-ADD-2: Core behavior

1. A new clinic number record shall be created with the provided value and description.
2. The record's status shall be set to active by default.
3. The record shall be assigned a unique identifier upon creation.

#### FR-ADD-3: Response

- On success, the operation shall return the unique identifier (integer) of the newly created record.
- On failure (any error during creation), the operation shall return zero (0).

#### FR-ADD-4: Error handling

Any error during record creation shall be caught. The operation shall not propagate exceptions to the caller; instead, it shall return zero (0) to indicate failure.

### 3.2 List Active Clinic Number Records

#### FR-LIST-1: Inputs

This operation takes no inputs.

#### FR-LIST-2: Core behavior

The operation shall retrieve all clinic number records whose status is NOT deleted.

#### FR-LIST-3: Ordering

Results shall be sorted in ascending order by the clinic number value field.

#### FR-LIST-4: Response

The operation shall return a list of clinic number records. Each record contains:
- **description**: the human-readable label
- **identifier**: the unique record identifier (integer)
- **status**: the record's current status
- **value**: the clinic number value

If no matching records exist, the operation shall return an empty list.

### 3.3 Remove Clinic Number Record (Soft Delete)

#### FR-REM-1: Inputs

The operation shall accept one input:
- **identifier**: the unique integer identifier of the clinic number record to remove

#### FR-REM-2: Core behavior

1. The operation shall look up the clinic number record by the given identifier.
2. The record's status shall be changed to deleted.
3. The change shall be saved to the database.

#### FR-REM-3: Response

- On success, the operation shall return the identifier that was passed in.
- On failure (record not found, or any error during the operation), the operation shall return zero (0).

#### FR-REM-4: Error handling

Any error during the lookup or status update shall be caught. The operation shall not propagate exceptions to the caller; instead, it shall return zero (0) to indicate failure.

### 3.4 Inherited Base Operations

This component inherits standard data access operations from a generic base. These include:

- **Batch create**: Create multiple records in batches (default batch size: 25)
- **Batch remove**: Physically remove multiple records in batches (default batch size: 25)
- **Check existence**: Determine whether a given record is managed in the current transaction context
- **Count all**: Return the total count of all records in the underlying table
- **Find by identifier**: Retrieve a single record by its primary key; return null if not found
- **Find by identifier (detached)**: Retrieve a single record by its primary key and disconnect it from change tracking; return null if not found
- **Find all (paginated)**: Retrieve records with optional offset and a mandatory limit (maximum 5000 records per query)
- **Physical remove by identifier**: Remove a record by its primary key; return true if removed, false if not found
- **Physical remove by record**: Remove a given record from storage (record must be managed in the current context)
- **Refresh**: Reload a record's state from the database
- **Save or update**: Create the record if it has no identifier; otherwise update the existing record
- **Synchronize pending changes**: Force all pending changes to be written to the database immediately

## 4. Non-Functional Requirements

- **NFR-1**: The soft-delete approach for removal (§3.3) shall preserve data integrity by retaining the record in storage.
- **NFR-2**: The component shall operate within the transaction boundaries established by the calling context.
- **NFR-3**: Input strings for value and description shall be whitespace-trimmed before storage; strings consisting entirely of whitespace shall be stored as null.

## 5. External Dependencies

The component requires the following capabilities:

- Ability to create new records in persistent storage with auto-generated unique identifiers
- Ability to query records from persistent storage with filtering and ordering
- Ability to update existing records in persistent storage
- Ability to retrieve individual records by primary key
- Transaction management provided by the calling context

How these are organized (one service, many services, direct database access, etc.) is an implementation choice.

## 6. Response Summary

| Operation | Success Response | Failure Response |
|-----------|-----------------|-----------------|
| Add clinic number record | Integer: newly assigned unique identifier | Integer: 0 |
| List active clinic number records | List of clinic number records (may be empty) | N/A (always returns a list) |
| Remove clinic number record | Integer: the input identifier | Integer: 0 |

## 7. Client Integration Contract

### 7.1 JSON API Contract

The component is consumed by a JSON endpoint that dispatches operations based on a `method` parameter:

**Request parameters:**
- `method`: `"add"` or `"remove"` (wire protocol values — exact strings)
- For `"add"`: `nbr` (the clinic number value), `nbrDesc` (the description)
- For `"remove"`: `nbr` (the integer identifier of the record to remove)

**Response format** (JSON, content type `application/json`):

| Field | Type | Description |
|-------|------|-------------|
| `error` | string | Error message (empty string on success) |
| `method` | string | Echo of the input method parameter |
| `nbr` | string or integer | Echo of the input `nbr` parameter |
| `nbrDesc` | string | Echo of the input `nbrDesc` parameter (add only) |
| `success` | boolean | `true` if the operation succeeded, `false` otherwise |

**Error messages** (exact wire protocol strings):
- `"Add Failure: Cannot add NRB with empty value."` — when add is called with an empty value
- `"Add Failure: Could not add entry to database."` — when the add operation returns 0
- `"Invalid method supplied."` — when the method parameter is not `"add"` or `"remove"`
- `"Remove Failure: Could not remove entry from database."` — when the remove operation returns 0

### 7.2 Display Integration

The component is also consumed directly by administrative and billing pages that call the list operation to populate dropdown selectors. Each record's display format is: `{value} | {description}`.

## 8. Assumptions

- **Active status default**: New records are created with active status by default without the caller needing to specify it.
- **Identifier generation**: The storage system provides automatic unique identifier generation for new records.
- **Soft delete convention**: The "remove" operation performs a soft delete (status change), not a physical deletion. Records marked as deleted remain in storage but are excluded from the list operation.
- **Transaction context**: The calling layer provides appropriate transaction boundaries; this component does not manage its own transactions.
- **Trimming behavior**: Both the value and description inputs are trimmed of leading and trailing whitespace before storage, with all-whitespace strings converted to null.

## 9. Verification Criteria

| Test ID | Requirement | Verification |
|---------|-------------|-------------|
| V-ADD-1 | FR-ADD-1 | Given a value `"123"` and description `"Test Clinic"`, the add operation shall return a non-zero integer identifier |
| V-ADD-2 | FR-ADD-3 | Given a value and description that cause a storage error, the add operation shall return 0 |
| V-ADD-3 | FR-ADD-2 | After adding a record, listing all active records shall include the newly added record with active status |
| V-ADD-4 | NFR-3 | Given a value `"  456  "` and description `"  Desc  "`, the stored record shall have value `"456"` and description `"Desc"` |
| V-ADD-5 | NFR-3 | Given a value `"   "` (all whitespace), the stored record shall have a null value field |
| V-LIST-1 | FR-LIST-2 | Given a mix of active and deleted records, the list operation shall return only records that are not deleted |
| V-LIST-2 | FR-LIST-3 | Given multiple active records with values `"300"`, `"100"`, `"200"`, the list shall return them in order: `"100"`, `"200"`, `"300"` |
| V-LIST-3 | FR-LIST-4 | Given no records in storage, the list operation shall return an empty list |
| V-REM-1 | FR-REM-2 | After removing a record by identifier, the record's status shall be changed to deleted |
| V-REM-2 | FR-REM-3 | Given a valid identifier, the remove operation shall return that same identifier |
| V-REM-3 | FR-REM-3 | Given a non-existent identifier, the remove operation shall return 0 |
| V-REM-4 | FR-REM-2 | After removing a record, the list operation shall no longer include that record |

## 10. Observed Behaviors (Non-Normative)

- The ascending sort by value (§3.2) is observed to be lexicographic, not numeric. For example, `"10"` sorts before `"9"`.
- The database table underlying this component is observed to have a maximum value length of 11 characters and a single-character status field.
- When a record is soft-deleted and then a new record is added with the same value, both records coexist in storage; only the active one appears in the list results.
