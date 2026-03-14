# Functional Specification: HRM Document-to-Provider Association

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

This specification defines the externally observable behavior of a data entity and its associated data access operations that represent the many-to-many association between Hospital Report Manager (HRM) documents and healthcare providers in an electronic medical records (EMR) system. This association tracks which providers have been assigned to review a given HRM document, whether they have viewed it, and whether they have signed it off.

This specification serves as the sole input for a clean room reimplementation.

**Traceability note:** This spec corresponds to the component identified as `HRMDocumentToProvider` in the existing codebase. This name is provided solely for traceability between the specification and the original system; it does not prescribe a class name, method name, or any internal naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Data entity structure and field semantics
- Data retrieval operations with filtering, pagination, and date range support
- Provider assignment, sign-off tracking, and view tracking for HRM documents
- Query behavior influenced by system-wide date search preference
- Sentinel value conventions for unclaimed document assignments

**Out of scope:**
- Authentication and session management (provided by the surrounding framework)
- HRM document content, parsing, or rendering
- HRM document-to-demographic associations (specified separately)
- The internal implementation approach — any architecture that produces the specified observable behavior is acceptable
- User interface presentation of provider assignments

### 1.3 Document Conventions

- The word **"shall"** indicates a mandatory requirement.
- The word **"should"** indicates a recommended but optional behavior.
- The phrase **"is observed to"** indicates documented behavior of the existing system that the reimplementation should replicate for compatibility, but which is not part of the formal functional contract.
- All lists of independent items are **alphabetically ordered** to prevent structural mirroring.
- Causally dependent steps are numbered sequentially; independent steps use unordered bullets.

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| **Date search preference** | A system-wide configuration that determines which date field is used for date range filtering in inbox queries. The two modes are: "received/created" (uses the timestamp when the HRM document was received by the system) and "service/observation" (uses the clinical report date from the document content). |
| **HRM document** | A Hospital Report Manager document — an electronic clinical report received from an external healthcare facility, stored in the EMR system for provider review. |
| **Provider** | An authenticated healthcare practitioner who uses the EMR system. Identified by a provider number string. |
| **Provider number** | A string identifier for a provider within the EMR system. |
| **Sign-off** | The act of a provider acknowledging they have reviewed an HRM document. Tracked as an integer flag with an associated timestamp. |
| **System user sentinel** | The provider number value `"-1"`, which represents an unclaimed or system-assigned document association that has not yet been matched to a real provider. This is a wire protocol value used across multiple components. |
| **Viewed flag** | An integer flag indicating whether a provider has viewed (opened) the associated HRM document. |

---

## 2. Data Entity Structure

The HRM document-to-provider association shall be a persistent data record with the following fields:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| Document reference | Integer | (required) | The identifier of the associated HRM document |
| Provider number | String | (required) | The provider number of the assigned provider, or `"-1"` for unclaimed associations |
| Record identifier | Integer | Auto-assigned | A unique identifier for this association record |
| Sign-off status | Integer | 0 | Whether the provider has signed off on the document (0 = not signed off, 1 = signed off) |
| Sign-off timestamp | Date/time | null | The date and time when the provider signed off, or null if not yet signed off |
| Viewed status | Integer | 0 | Whether the provider has viewed the document (0 = not viewed) |

The record shall support identity-based equality: two records are considered equal if and only if they have the same record identifier. The record shall be serializable.

---

## 3. Functional Requirements

### 3.1 Count Unsigned by Provider

#### FR-CNT-1: Inputs

The operation shall accept:
- **Provider number** (String, required): the provider to count for

#### FR-CNT-2: Core Behavior

The operation shall return the count of association records where:
1. The provider number matches the supplied value
2. The sign-off status is not signed off (value 0)

#### FR-CNT-3: Response

The operation shall return the count as an integer value.

---

### 3.2 Find All by Document and Provider

#### FR-FAP-1: Inputs

The operation shall accept:
- **Document identifier** (Integer, required): the HRM document identifier
- **Provider number** (String, required): the provider number

#### FR-FAP-2: Core Behavior

The operation shall return all association records matching both the document identifier and provider number.

#### FR-FAP-3: Response

The operation shall return a list of association records. The list may be empty.

---

### 3.3 Find by Document

#### FR-FBD-1: Inputs

The operation shall accept:
- **Document identifier** (Integer, required): the HRM document to look up

#### FR-FBD-2: Core Behavior

The operation shall return all association records that reference the given document identifier.

#### FR-FBD-3: Response

The operation shall return a list of association records. The list may be empty if no associations exist.

---

### 3.4 Find by Document and Provider

#### FR-FDP-1: Inputs

The operation shall accept:
- **Document identifier** (Integer, required): the HRM document identifier
- **Provider number** (String, required): the provider number

#### FR-FDP-2: Core Behavior

The operation shall search for association records matching both the document identifier and the provider number. If multiple records match, the operation shall return the last record from the result set.

#### FR-FDP-3: Response

The operation shall return a single association record, or null if no matching record exists.

#### FR-FDP-4: Error Handling

If an error occurs during retrieval, the operation shall return null.

---

### 3.5 Find by Document Excluding System User

#### FR-FDE-1: Inputs

The operation shall accept:
- **Document identifier** (Integer, required): the HRM document to look up

#### FR-FDE-2: Core Behavior

The operation shall return all association records that reference the given document identifier, excluding any records where the provider number is the system user sentinel value `"-1"`.

#### FR-FDE-3: Response

The operation shall return a list of association records. The list may be empty.

---

### 3.6 Find by Provider with Advanced Filtering

#### FR-FPA-1: Inputs

The operation shall accept the following parameters:
- **Demographic numbers** (List of Integers, optional): patient identifiers to filter by
- **Is paged** (Boolean, required): whether pagination should be applied
- **Newest date** (Date, optional): the upper bound for date filtering
- **Oldest date** (Date, optional): the lower bound for date filtering
- **Page number** (Integer, required if paged): zero-based page index
- **Page size** (Integer, required if paged): records per page
- **Patient search flag** (Boolean, required): indicates whether this is a patient-scoped search
- **Provider number** (String, required): the provider number (supports pattern matching)
- **Sign-off filter** (Integer, required): filter by sign-off status; value 2 means "no filter" (return all)
- **Viewed filter** (Integer, required): filter by viewed status; value 2 means "no filter" (return all)

#### FR-FPA-2: Guard Conditions

If the patient search flag is true AND the demographic numbers list is null or empty, the operation shall immediately return an empty list.

#### FR-FPA-3: Core Behavior

The operation shall retrieve association records by joining with the associated HRM document data, applying the following filters in sequence:

1. **Provider filter**: Records where the provider number matches the supplied value (using pattern matching).

2. **Demographic filter** (if demographic numbers are provided):
   - If the list contains a single entry with value 0: return only records whose HRM documents have NO demographic associations (unmatched documents).
   - Otherwise: join with document-to-demographic associations and filter to records linked to any of the supplied demographic numbers.

3. **Date range filter**: The date field used for comparison is determined by a system-wide preference:
   - If the preference is set to "receivedCreated": filter by the document's received timestamp.
   - Otherwise (default "serviceObservation"): filter by the document's clinical report date.
   - If a newest date is supplied, only include records where the date field is on or before that date.
   - If an oldest date is supplied, only include records where the date field is on or after that date.

4. **Viewed filter**: If the viewed filter value is not 2, only include records matching the specified viewed status.

5. **Sign-off filter**: If the sign-off filter value is not 2, only include records matching the specified sign-off status.

6. **Pagination**: If the is-paged flag is true, skip `page × pageSize` records and return at most `pageSize` records.

#### FR-FPA-4: Response

The operation shall return a list of association records matching all applied filters. The list may be empty.

---

### 3.7 Find by Provider with Pagination

#### FR-FPP-1: Inputs

The operation shall accept:
- **Page number** (Integer, required): zero-based page index
- **Page size** (Integer, required): the number of records per page
- **Provider number** (String, required): the provider to look up

#### FR-FPP-2: Core Behavior

The operation shall return association records where the provider number matches, applying pagination by skipping `page × pageSize` records and returning at most `pageSize` records.

#### FR-FPP-3: Response

The operation shall return a list of association records for the requested page.

---

### 3.8 Find Signed-Off by Document

#### FR-FSO-1: Inputs

The operation shall accept:
- **Document identifier** (Integer, required): the HRM document identifier

#### FR-FSO-2: Core Behavior

The operation shall return all association records for the given document where the sign-off status indicates signed off (value 1).

#### FR-FSO-3: Response

The operation shall return a list of signed-off association records. The list may be empty.

---

### 3.9 Standard CRUD Operations

#### FR-CRD-1: Create

The system shall support creating new association records. A newly created record shall have a unique identifier automatically assigned. Default values shall be applied as specified in §2 for any fields not explicitly set.

#### FR-CRD-2: Read by Identifier

The system shall support retrieving a single association record by its unique record identifier. If no record exists with the given identifier, the operation shall return null.

#### FR-CRD-3: Update

The system shall support updating an existing association record's mutable fields (document reference, provider number, sign-off status, sign-off timestamp, viewed status).

#### FR-CRD-4: Delete

The system shall support deleting an association record by its unique record identifier.

#### FR-CRD-5: List All

The system shall support listing all association records with optional offset and limit pagination.

---

## 4. Non-Functional Requirements

- **NFR-1**: All query operations shall use parameterized queries to prevent injection attacks.
- **NFR-2**: The association record shall be serializable for transport across system boundaries.
- **NFR-3**: The system shall support concurrent access to association records by multiple providers.

---

## 5. External Dependencies

The implementation requires the following capabilities. How these are organized (one service, many services, direct database access, etc.) is an implementation choice.

- **HRM document data access**: Ability to join with HRM document records to access report date and received timestamp fields for date filtering
- **HRM document-to-demographic data access**: Ability to join with document-to-demographic associations to filter by patient demographic numbers, and to identify documents with no demographic associations
- **Persistent storage**: Ability to create, read, update, delete, and query association records with support for pagination, pattern matching, and aggregate counting
- **System preference retrieval**: Ability to look up the system-wide inbox date search type preference to determine which date field to use for range filtering

---

## 6. Response Summary

| Operation | Returns |
|-----------|---------|
| Count unsigned by provider | Integer count of unsigned records for the provider |
| Find all by document and provider | List of all association records matching document and provider |
| Find by document | List of all association records for a document |
| Find by document and provider | Single association record (last from result set), or null |
| Find by document excluding system user | List of association records excluding system user sentinel |
| Find by provider with advanced filtering | Filtered list of association records with optional pagination |
| Find by provider with pagination | Paginated list of association records for a provider |
| Find signed-off by document | List of signed-off association records for a document |
| Standard CRUD (create) | New record with auto-assigned identifier |
| Standard CRUD (delete) | Record removed from persistent storage |
| Standard CRUD (read) | Single record or null |
| Standard CRUD (update) | Existing record modified |

---

## 7. Client Integration Contract

### 7.1 Data Shape

Consumers of this component interact with association records having the following shape:

| Field | Type | Notes |
|-------|------|-------|
| Document reference | Integer | References an HRM document record |
| Provider number | String | Provider identifier or `"-1"` for unclaimed |
| Record identifier | Integer | Unique, auto-assigned |
| Sign-off status | Integer | 0 = not signed off, 1 = signed off |
| Sign-off timestamp | Date/time | null until signed off |
| Viewed status | Integer | 0 = not viewed |

### 7.2 Sentinel Values

- The provider number `"-1"` is a wire protocol value representing an unclaimed or system-assigned association. Consuming components use this value to identify unmatched documents and may reassign the provider number to a real provider during sign-off or manual assignment workflows.

### 7.3 Filter Convention

- For the viewed and sign-off filter parameters in the advanced filtering operation, the value `2` means "no filter applied" (return records regardless of that field's value). Values `0` and `1` filter to records matching that exact status.

### 7.4 Integration with HRM Document

An HRM document record maintains a collection of its associated provider records. This is a bidirectional relationship: the association record references the document by identifier, and the document can enumerate its associated providers.

---

## 8. Assumptions

- **Date search preference availability**: The system-wide inbox date search type preference is always accessible. If the preference is not configured or has an empty value, the default behavior is to use the clinical report date (service/observation mode).
- **Demographic number zero convention**: A demographic numbers list containing a single entry with value 0 is a sentinel indicating a search for documents with no patient associations, not a search for demographic record number zero.
- **Provider number format**: Provider numbers are strings of variable length. The system user sentinel `"-1"` is a valid provider number value in this context.
- **Sign-off state values**: Sign-off status uses integer values where 0 means not signed off and 1 means signed off. The filter convention uses 2 as a "wildcard" meaning no filtering.
- **Uniqueness**: While multiple association records may exist for the same document-provider pair, the "find by document and provider" single-result operation handles this by returning the last record from the result set rather than enforcing uniqueness.

---

## 9. Verification Criteria

| Test ID | Requirement | Verification |
|---------|-------------|-------------|
| V-CNT-1 | FR-CNT-2 | Given a provider with 3 unsigned and 2 signed-off associations, the count operation shall return 3. |
| V-CRD-1 | FR-CRD-1 | A newly created association record shall have a unique, non-null record identifier after creation. |
| V-CRD-2 | FR-CRD-1 | A newly created record with no explicit sign-off status shall default to 0 (not signed off). |
| V-CRD-3 | FR-CRD-1 | A newly created record with no explicit viewed status shall default to 0 (not viewed). |
| V-CRD-4 | FR-CRD-2 | Retrieving a record by a non-existent identifier shall return null. |
| V-CRD-5 | FR-CRD-4 | After deleting a record by identifier, retrieving by that identifier shall return null. |
| V-FBD-1 | FR-FBD-2 | Given 3 association records for document X and 2 for document Y, querying for document X shall return exactly 3 records. |
| V-FDE-1 | FR-FDE-2 | Given associations for document X with provider numbers "100", "200", and "-1", the excluding-system-user operation shall return only the records for "100" and "200". |
| V-FDP-1 | FR-FDP-2 | Given two association records for the same document-provider pair, the operation shall return the last record from the result set (not the first). |
| V-FDP-2 | FR-FDP-3 | Querying for a non-existent document-provider pair shall return null. |
| V-FAP-1 | FR-FAP-2 | Given two records for document X and provider "100", the list operation shall return both records. |
| V-FPA-1 | FR-FPA-2 | When patient search flag is true and demographic numbers list is empty, the operation shall return an empty list. |
| V-FPA-2 | FR-FPA-3 (demographic=0) | When demographic numbers contains only [0], the operation shall return only records whose documents have no demographic associations. |
| V-FPA-3 | FR-FPA-3 (date preference) | When the date search preference is "receivedCreated", date filtering shall use the document's received timestamp, not the report date. |
| V-FPA-4 | FR-FPA-3 (date preference default) | When no date search preference is configured, date filtering shall default to using the document's clinical report date. |
| V-FPA-5 | FR-FPA-3 (viewed filter) | When viewed filter is 0, only records with viewed status 0 shall be returned. When viewed filter is 2, records with any viewed status shall be returned. |
| V-FPA-6 | FR-FPA-3 (sign-off filter) | When sign-off filter is 1, only records with sign-off status 1 shall be returned. When sign-off filter is 2, records with any sign-off status shall be returned. |
| V-FPA-7 | FR-FPA-3 (pagination) | When is-paged is true with page=1 and pageSize=5, the first 5 records shall be skipped and the next 5 returned. |
| V-FPP-1 | FR-FPP-2 | Given 10 records for a provider, requesting page 0 with page size 3 shall return the first 3 records. |
| V-FPP-2 | FR-FPP-2 | Given 10 records for a provider, requesting page 3 with page size 3 shall return the last 1 record. |
| V-FSO-1 | FR-FSO-2 | Given associations for document X where 2 are signed off and 1 is not, the operation shall return exactly 2 records. |

---

## 10. Observed Behaviors (Non-Normative)

- **Database column typing**: The document reference field is observed to be stored as `varchar(20)` in the database schema, despite being treated as an integer in the application layer. An implementation may choose either string or integer storage as long as the observable behavior matches.
- **Filed flag**: The database schema is observed to include an additional boolean field (`filed`) that is not exposed through the application data entity. An implementation may include or omit this field as long as it does not affect the specified observable behavior.
