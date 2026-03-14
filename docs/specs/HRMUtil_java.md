# Hospital Report Manager Utility — Functional Specification

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

This specification defines the observable behavior of a utility component in the Hospital Report Manager (HRM) subsystem of a Canadian healthcare EMR. The component provides operations to list, retrieve, resolve display names, and render HRM documents as PDFs.

**Traceability note:** This spec corresponds to the component identified as `HRMUtil` in the existing codebase. This name is provided solely for traceability; it does not prescribe any naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Assembling display metadata for HRM documents linked to a patient
- Duplicate detection and filtering across HRM document versions
- Listing subclass mapping records
- PDF rendering of HRM documents to temporary files
- Resolving display names for HRM documents
- Retrieving a single HRM document with resolved display name
- Security authorization enforcement for read access
- Sorting results by configurable fields

**Out of scope:**
- Creating, updating, or deleting HRM document records
- HRM document parsing (XML/HL7 interpretation)
- HRM document reception or ingestion workflows
- Provider routing or demographic matching
- Subclass mapping management (CRUD operations on mappings)

### 1.3 Document Conventions

- **"shall"** = mandatory requirement
- **"should"** = recommended but optional
- **"is observed to"** = existing system behavior (non-normative, §10 only)
- **"document"** = an HRM document record in the system
- **"report"** = the parsed XML content of an HRM document's file

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| Accompanying subclass | A subclass entry from the parsed report's accompanying subclass list, used as fallback when no mapped subclass description exists |
| Cancelled status | A report status indicator value of `"C"` (case-insensitive) signifying the report has been cancelled |
| Diagnostic report types | Reports classified as either "Diagnostic Imaging Report" or "Cardio Respiratory Report" (case-insensitive comparison) |
| Duplicate key | A composite key formed from three report fields: sending facility identifier, sending facility report number, and deliver-to user identifier, separated by colons |
| HRM | Hospital Report Manager — a subsystem for receiving, storing, and viewing clinical reports from external healthcare facilities |
| HRM document | A persistent record representing a received hospital report, including metadata and a reference to the report file |
| HRM security object | The security privilege object `_hrm` used for authorization checks |
| Medical records report | Any report whose class is not a diagnostic report type; subclass resolution uses caret-delimited parsing |
| Ontario billing region | A system configuration flag indicating the installation operates in the Ontario healthcare jurisdiction |
| Subclass mapping | A configuration record that maps a report class, subclass name, subclass mnemonic, and sending facility to a human-readable description |

---

## 2. External Interface

This component exposes a programmatic API (not an HTTP interface). All operations are invoked from other components within the EMR system.

### 2.1 Public Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `DATE` | `"time_received"` | Sort field identifier for time-received sorting |
| `TYPE` | `" report_type"` | Sort field identifier for report-type sorting (note: includes leading space) |

### 2.2 Operation Summary

| Operation | Authorization Required | Returns |
|-----------|----------------------|---------|
| Get document by ID | Yes (`_hrm` read) | Single document record with resolved display name, or null |
| List documents for patient | Yes (`_hrm` read) | List of document metadata maps |
| List subclass mappings | No | List of mapping metadata maps |
| Render document to PDF | Yes (`_hrm` read) | File path to temporary PDF |

---

## 3. Functional Requirements

### 3.1 Get Document by ID

#### FR-GDI-1: Inputs

The operation shall accept:
- **Authenticated session context** identifying the current user
- **Document identifier** (integer) specifying which HRM document to retrieve

#### FR-GDI-2: Guard Conditions

1. The caller shall be checked for read privilege on the HRM security object (`_hrm`).
2. If the caller lacks this privilege, the operation shall raise a security exception with the message `"missing required sec object (_hrm)"`.

#### FR-GDI-3: Document Retrieval

1. The operation shall retrieve the document record by its identifier.
2. The operation shall parse the document's associated report file.
3. If the report file cannot be parsed (returns null), the operation shall return null.

#### FR-GDI-4: Subclass Description Resolution

The operation shall resolve a display subclass description using the same logic defined in §3.2 FR-LDP-6 (Subclass Description Resolution), with one difference: the subclass list shall be sourced from the document record's accompanying subclass collection rather than a separate query by document ID.

#### FR-GDI-5: Display Name Resolution

The operation shall compute a display name using the rules defined in §3.5 (Resolve Display Name) and set it on the returned document record's transient display name field.

#### FR-GDI-6: Response

The operation shall return the document record with its display name set, or null if the report could not be parsed.

---

### 3.2 List Documents for Patient

#### FR-LDP-1: Inputs

The operation shall accept:
- **Authenticated session context** identifying the current user
- **Demographic number** (string) identifying the patient
- **Filter duplicates flag** (boolean) controlling whether duplicate versions are collapsed
- **Sort ascending flag** (boolean) controlling sort direction
- **Sort field** (string, nullable) identifying which field to sort by

#### FR-LDP-2: Guard Conditions — Region Check

If the system is not configured for the Ontario billing region, the operation shall return an empty list immediately.

#### FR-LDP-3: Guard Conditions — Authorization

1. The caller shall be checked for read privilege on the HRM security object (`_hrm`).
2. If the caller lacks this privilege, the operation shall:
   a. Create an audit log entry recording the unauthorized access attempt with: the provider number, the operation identifier, the word "UNAUTHORIZED", a message indicating the missing security object, the caller's IP address, and the demographic number.
   b. Log a warning indicating the missing security object.
   c. Return an empty list.

#### FR-LDP-4: Document Collection

1. The operation shall retrieve all document-to-demographic link records for the specified demographic number.
2. For each link record, the operation shall retrieve the corresponding document record(s) using the link's document identifier (defaulting to 0 if null).

#### FR-LDP-5: Duplicate Filtering

**When filtering is enabled:**

1. For each document, the operation shall parse its report file. If parsing fails, the document shall be skipped.
2. The operation shall set the parsed report's document identifier to match the document record.
3. A duplicate key shall be computed as: `{sending facility ID}:{sending facility report number}:{deliver-to user ID}`.
4. If no document with this key has been seen yet, the document shall be added to the display set.
5. If a document with this key already exists:
   a. The operation shall compare the current report against the previously stored report to determine which is newer (using date comparison of message unique IDs, with fallback to result status and then document ID comparison).
   b. The newer document shall replace the older one in the display set.
   c. The displaced document's identifier shall be added to a list of duplicate identifiers for that key.

**When filtering is disabled:**

Each document shall be added to the display set keyed by its document identifier (as a string). If multiple documents share the same identifier, the last one encountered shall be retained.

#### FR-LDP-6: Subclass Description Resolution

For each document in the display set, the operation shall resolve a display subclass description:

1. The operation shall parse the document's report file. If parsing fails, the document shall be skipped entirely.
2. The operation shall retrieve the document's subclass records by document identifier.

**For diagnostic report types** (class equals "Diagnostic Imaging Report" or "Cardio Respiratory Report", case-insensitive):
   a. If subclass records exist, take the first record and look up its applicable subclass mapping using the report class, subclass name, subclass mnemonic, and sending facility identifier. The mapping lookup shall first attempt a facility-specific match, then fall back to a facility-agnostic match.
   b. If a mapping is found, use its description. Otherwise, use an empty string.
   c. If the description is still empty and the report has accompanying subclasses, use the first accompanying subclass value from the parsed report.

**For medical records reports** (any other class):
   a. Take the report's first subclass value and split it by the caret character (`^`).
   b. If the split produces more than one segment, use the second segment as the description.

#### FR-LDP-7: Category Name Resolution

For each document, if it has a category identifier:
1. The operation shall look up the category record by that identifier.
2. The category name shall be extracted from the record.

If no category identifier is set, the category name shall be an empty string.

#### FR-LDP-8: Display Name Computation

For each document, a display name shall be computed using the rules in §3.5 (Resolve Display Name), using the document's description, the resolved subclass description, the report type, and the report status.

#### FR-LDP-9: Metadata Map Assembly

For each document in the display set, the operation shall produce a map containing the following entries:

| Key | Value |
|-----|-------|
| `"category"` | Resolved category name |
| `"class_subclass"` | Resolved subclass description |
| `"description"` | Document description |
| `"duplicateLabIds"` | (Only when filtering enabled) Comma-separated list of duplicate document identifiers for this key, or empty string if none |
| `"id"` | Document identifier (integer) |
| `"name"` | Computed display name |
| `"report_date"` | Document report date as string, or empty string if null |
| `"report_status"` | Document report status |
| `"report_type"` | Document report type |
| `"time_received"` | Document time-received as string |

The `"duplicateLabIds"` key shall only be included when the filter duplicates flag is enabled.

#### FR-LDP-10: Sorting

The result list shall be sorted based on the sort field value:

| Sort Field Value | Primary Sort Key | Secondary Sort Key |
|-----------------|-----------------|-------------------|
| `"category"` | `"category"` | None |
| `"report_date"` | `"report_date"` | `"time_received"` (when primary values are equal) |
| `"report_name"` | `"report_type"` | None |
| `"time_received"` | `"time_received"` | `"report_date"` (when primary values are equal) |

All sort comparisons shall use natural string ordering.

If the sort ascending flag is false, the sorted list shall be reversed.

If the sort field is null or does not match any of the above values, no sorting shall be applied.

#### FR-LDP-11: Response

The operation shall return the list of metadata maps.

---

### 3.3 List Subclass Mappings

#### FR-LSM-1: Inputs

The operation requires no inputs.

#### FR-LSM-2: Guard Conditions

None. No authorization check is required.

#### FR-LSM-3: Retrieval

The operation shall retrieve all subclass mapping records from the system.

#### FR-LSM-4: Metadata Map Assembly

For each mapping record, the operation shall produce a map containing:

| Key | Value |
|-----|-------|
| `"category"` | The mapping's associated category |
| `"class"` | The mapping's class name |
| `"description"` | The mapping's subclass description, or empty string if null |
| `"id"` | The mapping's sending facility identifier |
| `"mappingId"` | The mapping's unique record identifier |
| `"mnemonic"` | The mapping's subclass mnemonic, or empty string if null |
| `"sub_class"` | The mapping's subclass name |

#### FR-LSM-5: Response

The operation shall return the list of metadata maps.

---

### 3.4 Render Document to PDF

#### FR-RDP-1: Inputs

The operation shall accept:
- **Authenticated session context** identifying the current user
- **Document identifier** (integer) specifying which HRM document to render

#### FR-RDP-2: Guard Conditions

1. The caller shall be checked for read privilege on the HRM security object (`_hrm`).
2. If the caller lacks this privilege, the operation shall raise a security exception with the message `"missing required sec object (_hrm)"`.

#### FR-RDP-3: PDF Generation

1. The operation shall create a PDF rendering component initialized with an output buffer, the document identifier, and the authenticated session context.
2. The operation shall invoke the PDF rendering process.
3. The rendered output shall be saved to a temporary file. The temporary filename shall be composed of the prefix `"temporaryPDF"` followed by the current timestamp in milliseconds.

#### FR-RDP-4: Error Handling

If an I/O error occurs during PDF generation or file saving, the operation shall raise a PDF generation exception with a message in the format:
`"Error Details: HRM [{display name}] could not be converted into a PDF"`
where `{display name}` is computed using the display name resolution rules from §3.5, using the document's description, an empty string for the subclass, the report type, and the report status.

#### FR-RDP-5: Response

The operation shall return the file system path to the temporary PDF file.

---

### 3.5 Resolve Display Name

#### FR-RDN-1: Inputs

The operation shall accept:
- **Description** (string, nullable)
- **Report status** (string, nullable)
- **Report type** (string)
- **Subclass description** (string, nullable)

#### FR-RDN-2: Name Selection Priority

The display name shall be determined by the following priority (first non-empty value wins):
1. Description (if not null, not empty, not whitespace-only, and not the literal string "null" case-insensitively)
2. Subclass description (same emptiness rules)
3. Report type

#### FR-RDN-3: Cancelled Report Prefix

If the report status equals `"C"` (case-insensitive), the display name shall be prefixed with `"(Cancelled) "`.

#### FR-RDN-4: Response

The operation shall return the computed display name string.

---

## 4. Non-Functional Requirements

### NFR-1: Provincial Restriction

The document listing operation (§3.2) shall only function in Ontario billing region configurations. In non-Ontario configurations, it shall return an empty result without error.

### NFR-2: Security Model

All operations that access HRM document content shall enforce read authorization on the `_hrm` security object. Unauthorized access shall either raise a security exception or return an empty result with an audit log entry, depending on the operation.

### NFR-3: Null Safety

- Null document identifiers from link records shall default to 0.
- Null category identifiers, report dates, subclass descriptions, and mnemonics shall be handled gracefully with empty string defaults.
- Unparseable report files shall cause the associated document to be skipped (not raise an error) in listing operations.

---

## 5. External Dependencies

The component requires the following capabilities. How these are organized (one service, many services, direct database access, etc.) is an implementation choice.

- **Audit logging**: Ability to create audit log entries recording unauthorized access attempts with provider, operation, authorization status, message, IP address, and demographic number
- **Category lookup**: Ability to retrieve an HRM category record by its identifier
- **Diagnostic logging**: Ability to log warning-level and debug-level messages
- **Document lookup**: Ability to retrieve HRM document records by identifier
- **Document-to-demographic link query**: Ability to retrieve all document-to-demographic link records for a given demographic number
- **File management**: Ability to save a byte stream as a temporary file and return its file system path
- **Newer-report comparison**: Ability to determine which of two parsed reports is newer, using message unique ID dates, result status, and document ID as tiebreakers
- **PDF rendering**: Ability to generate a PDF representation of an HRM document given its identifier and session context
- **Region configuration**: Ability to determine whether the system is configured for the Ontario billing region
- **Report parsing**: Ability to parse an HRM document's file into a structured report object exposing report class, subclass, accompanying subclasses, sending facility ID, sending facility report number, and deliver-to user ID
- **Security authorization**: Ability to check whether a user has a specific privilege level on a named security object
- **Subclass mapping lookup**: Ability to find an applicable subclass mapping by class, subclass name, mnemonic, and facility (with facility-agnostic fallback)
- **Subclass mapping list**: Ability to retrieve all subclass mapping records
- **Subclass records by document**: Ability to retrieve subclass records associated with a specific document identifier

---

## 6. Response Summary

| Operation | Success Response | Failure Response |
|-----------|-----------------|-----------------|
| Get document by ID | Document record with display name set | Security exception (unauthorized) or null (unparseable report) |
| List documents for patient | List of metadata maps (may be empty) | Empty list (unauthorized, wrong region, or no documents) |
| List subclass mappings | List of mapping metadata maps | Empty list (no mappings) |
| Render document to PDF | File system path to temporary PDF | Security exception (unauthorized) or PDF generation exception (I/O error) |

---

## 7. Client Integration Contract

### 7.1 Metadata Map Keys — Document Listing

Clients consuming the document listing result shall expect maps with the following string keys: `"category"`, `"class_subclass"`, `"description"`, `"id"`, `"name"`, `"report_date"`, `"report_status"`, `"report_type"`, `"time_received"`.

When duplicate filtering is enabled, maps shall additionally contain `"duplicateLabIds"`.

The `"id"` value shall be an integer. All other values shall be strings.

### 7.2 Metadata Map Keys — Subclass Mapping Listing

Clients consuming the mapping listing result shall expect maps with the following string keys: `"category"`, `"class"`, `"description"`, `"id"`, `"mappingId"`, `"mnemonic"`, `"sub_class"`.

The `"mappingId"` value shall be an integer. All other values shall be strings or objects (for `"category"`).

### 7.3 Sort Field Contract

Clients specifying sort fields shall use the following wire values: `"category"`, `"report_date"`, `"report_name"`, `"time_received"`.

### 7.4 Temporary PDF Files

The returned PDF file path is a temporary file. Clients are responsible for cleanup after use. The file name starts with `"temporaryPDF"` followed by a millisecond timestamp.

---

## 8. Assumptions

- **Category record availability**: When a document has a category identifier, the corresponding category record exists and is retrievable.
- **Document record availability**: Document-to-demographic link records reference valid document identifiers that can be retrieved.
- **Ontario-only feature**: HRM document listing is an Ontario-specific feature. Non-Ontario installations shall not use this capability.
- **Report file availability**: Documents reference report files that exist on the file system and are parseable, though the system handles parse failures gracefully.
- **Security context validity**: The authenticated session context provided to operations is valid and contains the necessary provider information for authorization checks.
- **String representations of dates**: Time-received and report-date values, when converted to strings, produce values suitable for natural string comparison (i.e., lexicographic ordering corresponds to chronological ordering).

---

## 9. Verification Criteria

| Test ID | Requirement | Test Description | Expected Outcome |
|---------|-------------|-----------------|-----------------|
| V-GDI-1 | FR-GDI-2 | Invoke get-document-by-ID without `_hrm` read privilege | Security exception raised with message containing `"missing required sec object (_hrm)"` |
| V-GDI-2 | FR-GDI-3 | Invoke get-document-by-ID with a document whose report file cannot be parsed | Operation returns null |
| V-GDI-3 | FR-GDI-5 | Invoke get-document-by-ID for a valid document with description set | Returned document has display name equal to description |
| V-GDI-4 | FR-GDI-4 | Invoke get-document-by-ID for a diagnostic imaging document with subclass mapping | Returned document display name reflects mapped subclass description when no description is set |
| V-LDP-1 | FR-LDP-2 | Invoke list-documents in a non-Ontario configuration | Empty list returned |
| V-LDP-2 | FR-LDP-3 | Invoke list-documents without `_hrm` read privilege | Empty list returned; audit log entry created |
| V-LDP-3 | FR-LDP-5 | Invoke list-documents with duplicate filtering enabled, providing two documents with same duplicate key | Only the newer document appears in results; `"duplicateLabIds"` contains the displaced document's identifier |
| V-LDP-4 | FR-LDP-5 | Invoke list-documents with duplicate filtering disabled, providing two documents with same duplicate key | Both documents appear in results (keyed by their individual IDs) |
| V-LDP-5 | FR-LDP-6 | Invoke list-documents with a diagnostic imaging document that has a subclass mapping | `"class_subclass"` value matches the mapping's description |
| V-LDP-6 | FR-LDP-6 | Invoke list-documents with a diagnostic imaging document that has no mapping but has accompanying subclasses | `"class_subclass"` value matches the first accompanying subclass |
| V-LDP-7 | FR-LDP-6 | Invoke list-documents with a medical records report containing caret-delimited subclass | `"class_subclass"` value equals the second segment after caret split |
| V-LDP-8 | FR-LDP-7 | Invoke list-documents with a document that has a category identifier | `"category"` value matches the category record's name |
| V-LDP-9 | FR-LDP-10 | Invoke list-documents with `sort_by="report_date"` ascending, providing documents with varying dates | Documents sorted by report date ascending; ties broken by time received |
| V-LDP-10 | FR-LDP-10 | Invoke list-documents with `sort_by="time_received"` descending | Documents sorted by time received descending; ties broken by report date |
| V-LDP-11 | FR-LDP-10 | Invoke list-documents with `sort_by="category"` | Documents sorted by category name |
| V-LDP-12 | FR-LDP-10 | Invoke list-documents with `sort_by="report_name"` | Documents sorted by report type |
| V-LDP-13 | FR-LDP-9 | Invoke list-documents with filtering disabled | Result maps do not contain `"duplicateLabIds"` key |
| V-LSM-1 | FR-LSM-3 | Invoke list-subclass-mappings when mappings exist | All mappings returned with correct keys |
| V-LSM-2 | FR-LSM-4 | Invoke list-subclass-mappings for a mapping with null mnemonic and null description | `"mnemonic"` and `"description"` values are empty strings |
| V-RDN-1 | FR-RDN-2 | Invoke display name resolution with non-empty description | Display name equals the description |
| V-RDN-2 | FR-RDN-2 | Invoke display name resolution with empty description and non-empty subclass | Display name equals the subclass description |
| V-RDN-3 | FR-RDN-2 | Invoke display name resolution with empty description and empty subclass | Display name equals the report type |
| V-RDN-4 | FR-RDN-3 | Invoke display name resolution with cancelled report status | Display name starts with `"(Cancelled) "` |
| V-RDN-5 | FR-RDN-3 | Invoke display name resolution with cancelled status (uppercase "C") | Display name starts with `"(Cancelled) "` |
| V-RDP-1 | FR-RDP-2 | Invoke render-document-to-PDF without `_hrm` read privilege | Security exception raised |
| V-RDP-2 | FR-RDP-3 | Invoke render-document-to-PDF for a valid document | File system path returned; file exists and contains valid PDF content |
| V-RDP-3 | FR-RDP-4 | Invoke render-document-to-PDF when I/O error occurs | PDF generation exception raised with message containing `"Error Details: HRM"` and the document display name |

---

## 10. Observed Behaviors (Non-Normative)

The following behaviors are observed in the existing system but are not specified as normative requirements:

- **Duplicate filtering key construction**: When duplicate filtering is enabled, documents with null sending facility ID, null facility report number, or null deliver-to user ID are observed to produce duplicate keys with empty segments (e.g., `"::"` or `"FAC1::USER1"`), which may cause unintended grouping.
- **Leading space in TYPE constant**: The `TYPE` constant value is observed to contain a leading space character (`" report_type"` rather than `"report_type"`). This may be intentional for display formatting or may be a legacy artifact.
- **No-filter keying behavior**: When duplicate filtering is disabled, documents are keyed by their document identifier as a string. If a link record references an identifier that resolves to multiple document records, only the last one is retained per identifier key.
- **Report date string format**: Report dates are converted to strings using the default string representation of the date object, without explicit formatting. The resulting format depends on the runtime environment.
- **Time received null safety**: The time-received field is used without null checking during metadata assembly, suggesting it is assumed to always be non-null for valid documents.
