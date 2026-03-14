# Functional Specification: Hospital Report Parser

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

This specification defines the externally observable behavior of a hospital report parser component within an electronic medical records (EMR) system. The component is responsible for parsing XML-based hospital reports conforming to the Ontario MD HRM (Hospital Report Manager) schema, and for routing parsed reports to the appropriate patients and healthcare providers within the system.

**Traceability note:** This spec corresponds to the component identified as `HRMReportParser` in the existing codebase. This name is provided solely for traceability between the specification and the original system; it does not prescribe a class name, method name, or any internal naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Date extraction logic for different report classes
- Demographic (patient) routing based on health card number matching
- Parsing XML report files against a versioned XSD schema
- Provider routing based on practitioner identifiers and forwarding rules
- Similar report detection and parent-child report linking
- Sub-classification routing for applicable report types

**Out of scope:**
- Authentication and session management (provided by the surrounding framework)
- HRM document lifecycle management beyond initial parsing and routing
- The internal implementation approach — any architecture that produces the specified observable behavior is acceptable
- UI display of parsed reports (handled by separate viewer components)
- The XSD schema definition itself (externally governed by Ontario MD)

### 1.3 Document Conventions

- The word **"shall"** indicates a mandatory requirement.
- The word **"should"** indicates a recommended but optional behavior.
- The phrase **"is observed to"** indicates documented behavior of the existing system that the reimplementation should replicate for compatibility, but which is not part of the formal functional contract.
- All lists of independent items are **alphabetically ordered** to prevent structural mirroring.
- Causally dependent steps are numbered sequentially; independent steps use unordered bullets.

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| **Alternate care providers** | Additional healthcare providers (midwife, nurse, resident) associated with a patient's extended care record, distinct from the most responsible provider. |
| **Document directory** | A server-side filesystem directory, configured at the application level via the `DOCUMENT_DIR` property, where HRM report files may be stored. |
| **Forwarding rules** | System-configurable rules that cause reports routed to one provider to be automatically forwarded to additional providers. Rules include a type filter (e.g., `HRM`) to control which report types are forwarded. |
| **Health card number (HCN)** | A provincial health insurance identifier used to uniquely identify patients. Also referred to as HIN (Health Insurance Number). |
| **HRM report** | An XML document conforming to the Ontario MD Hospital Report Manager Clinical Document Standard (CDS), containing clinical information sent from a hospital or external facility. |
| **Most responsible provider (MRP)** | The primary healthcare provider assigned to a patient in the EMR system. |
| **Parent report** | An earlier version of a report that the current report supersedes. Used to link report versions for display purposes. |
| **Parsed report object** | An in-memory representation of a parsed HRM XML document, containing extracted patient demographics, report content, metadata, and transaction information. |
| **Practitioner number** | A regulatory identifier assigned to a healthcare provider, used in HRM reports to identify the intended recipient. The first character is a prefix that shall be stripped before lookup. |
| **Report class** | A classification of the report type. Known classes include "Cardio Respiratory Report", "Diagnostic Imaging Report", and "Medical Records Report". |
| **Report content hash (excluding demographics)** | A hash of the report content with patient demographic information removed. Used for detecting reports with the same clinical content but different patient routing. |
| **Sign-off status** | A flag on a provider-to-document association indicating whether the provider has acknowledged the report. New associations shall be created with sign-off in the unsigned state. |
| **Similarity score** | A numeric score computed from comparing two reports across multiple dimensions to determine if a new report is a revised version of an existing one. |
| **Strict demographic matching** | An optional matching mode (controlled by the `omd_hrm_demo_matching_criteria` property) that requires date of birth, gender, and last name to match in addition to health card number. |
| **Sub-classification** | For Diagnostic Imaging and Cardio Respiratory reports, an individual test or examination within the report, described by a code, mnemonic, description, and date/time. |
| **XSD schema** | The XML Schema Definition used to validate HRM report files. The system uses version `1.1.2` of the Ontario MD HRM schema, loaded from a classpath resource at `/xsd/hrm/1.1.2/ontariomd_hrm.xsd`. |

---

## 2. Programmatic Interface

This component provides a programmatic interface rather than an HTTP interface. All operations are stateless entry points invoked by other system components.

### 2.1 Operation Dispatch

The component exposes the following operations, grouped by functional category:

| Operation | Description |
|-----------|-------------|
| Get appropriate date from report | Extracts the most relevant date from a parsed report based on its class |
| Get appropriate date string from report | Extracts the most relevant date as a formatted string based on report class |
| Parse report by document ID | Parses an HRM report file given a stored document identifier |
| Parse report by file path | Parses an HRM report file given a filesystem path |
| Parse report by file path with error collection | Parses an HRM report file, collecting parse errors |
| Route report to demographic | Creates a link between a report and a patient record |
| Route report to provider (by report and document) | Routes a report to providers using an existing document record |
| Route report to provider (by report and ID) | Routes a report to providers based on report metadata |
| Route report to provider (direct) | Creates a direct link between a report and a specific provider |
| Route report to sub-class | Creates sub-classification records for applicable report types |

### 2.2 Construction

The component shall not be directly instantiable. All operations shall be accessible as stateless entry points.

---

## 3. Functional Requirements

### 3.1 Get Appropriate Date from Report

#### FR-DAT-1: Inputs

The operation shall accept a single parsed report object.

#### FR-DAT-2: Date extraction by report class

1. The operation shall examine the report class of the parsed report.
2. If the report class is "Cardio Respiratory Report" or "Diagnostic Imaging Report" (case-insensitive comparison), the operation shall return the date/time from the first sub-classification entry of the report.
3. For all other report classes (e.g., "Medical Records Report"), the operation shall return the event time from the first report section.

#### FR-DAT-3: Response

The operation shall return a date value.

---

### 3.2 Get Appropriate Date String from Report

#### FR-DST-1: Inputs

The operation shall accept a single parsed report object.

#### FR-DST-2: Date string extraction by report class

1. The operation shall examine the report class of the parsed report.
2. If the report class is "Cardio Respiratory Report" or "Diagnostic Imaging Report" (case-insensitive comparison), the operation shall return the pre-formatted date string from the first sub-classification entry (the fifth element of the sub-classification tuple).
3. For all other report classes, the operation shall:
   1. Obtain the event time from the first report section.
   2. Format it using the pattern `EEE MMM dd HH:mm:ss z yyyy` (e.g., "Mon Jan 15 14:30:00 EST 2024"), preserving the original timezone.

#### FR-DST-3: Response

The operation shall return a formatted date string.

---

### 3.3 Parse Report by Document ID

#### FR-PID-1: Inputs

The operation shall accept:
- An authenticated session context
- An integer document identifier

#### FR-PID-2: Guard conditions

If no document record exists for the given identifier, the operation shall return null.

#### FR-PID-3: Core behavior

1. The operation shall look up the stored document record by its identifier.
2. The operation shall retrieve the report file path from the document record.
3. The operation shall delegate to the file-path-based parse operation (§3.4) using the retrieved file path.

#### FR-PID-4: Response

The operation shall return a parsed report object, or null if the document was not found.

---

### 3.4 Parse Report by File Path

#### FR-PFP-1: Inputs

The operation shall accept:
- An authenticated session context
- A string representing the report file path

#### FR-PFP-2: Core behavior

The operation shall delegate to the parse-with-error-collection operation (§3.5) with a null error collection.

#### FR-PFP-3: Response

The operation shall return a parsed report object, or null on failure.

---

### 3.5 Parse Report by File Path with Error Collection

#### FR-PEC-1: Inputs

The operation shall accept:
- An authenticated session context
- A string representing the report file path
- An optional list for collecting parse errors (may be null)

#### FR-PEC-2: Guard conditions

If the file path is null, the operation shall return null without attempting any file operations.

#### FR-PEC-3: File resolution

1. The operation shall first attempt to locate the file at the exact path provided.
2. If the file does not exist at the exact path, the operation shall attempt to locate it by combining the configured document directory (`DOCUMENT_DIR` property) with the provided path as a filename.
3. If the file still cannot be found at either location, the operation shall log a warning.

#### FR-PEC-4: File reading

If the file exists, the operation shall read its entire contents as a UTF-8 encoded string.

#### FR-PEC-5: Schema validation and XML parsing

1. The operation shall load the XSD schema from the classpath resource at `/xsd/hrm/1.1.2/ontariomd_hrm.xsd`.
2. The operation shall create an XML parser configured with the loaded schema for validation.
3. The operation shall parse the report file using the schema-validating parser, producing a typed XML object model.

#### FR-PEC-6: Response (success)

If parsing succeeds, the operation shall return a new parsed report object constructed from:
- The typed XML root element
- The original file path string
- The UTF-8 file content string

#### FR-PEC-7: Error handling

If any of the following errors occur, the operation shall:
- Log the error
- If the error collection parameter is non-null, add the error to the collection
- Return null

Error conditions (alphabetical):
- File not found at either resolution path
- I/O error reading the schema resource
- XML schema validation failure
- XML structure/binding parse failure

---

### 3.6 Route Report to Demographic (Direct)

#### FR-RDD-1: Inputs

The operation shall accept:
- An integer report identifier
- An integer patient demographic number

#### FR-RDD-2: Core behavior

The operation shall create a new document-to-demographic association record with:
- The demographic number set to the provided patient number
- The document identifier set to the provided report identifier
- The assignment timestamp set to the current date/time

#### FR-RDD-3: Side effects

The operation shall save the new association record to the database.

---

### 3.7 Route Report to Provider (by Report and Document)

#### FR-RPD-1: Inputs

The operation shall accept:
- An existing document record
- A parsed report object

#### FR-RPD-2: Core behavior

The operation shall delegate to the report-and-ID-based provider routing operation (§3.8) using the document record's identifier and the parsed report.

---

### 3.8 Route Report to Provider (by Report and ID)

#### FR-RPR-1: Inputs

The operation shall accept:
- A parsed report object
- An integer report identifier

#### FR-RPR-2: Guard conditions

If the parsed report is null, the operation shall log an informational message and return false.

#### FR-RPR-3: Primary provider resolution

1. The operation shall extract the delivery-to user identifier from the parsed report.
2. The operation shall strip the first character from this identifier to obtain the practitioner number.
3. The operation shall look up the provider record by practitioner number.
4. If a matching provider is found, it shall be added to the routing target list.

#### FR-RPR-4: Extended provider tagging (conditional)

If the `queens_resident_tagging` system property is active:

1. The operation shall look up patients by health card number from the parsed report.
2. If a matching patient is found:
   - If the primary provider was resolved (non-null), and the primary provider's number differs from the patient's most responsible provider number, and the patient has an assigned MRP, the MRP shall be added to the routing target list.
   - The operation shall look up the patient's extended care record and retrieve the alternate care provider identifiers (midwife, nurse, resident).
   - For each non-empty alternate care provider identifier, the operation shall look up the corresponding provider record and add it to the routing target list.

#### FR-RPR-5: Provider routing creation

For each provider in the routing target list:

1. The operation shall check whether a document-to-provider association already exists for this report and provider combination.
2. If no existing association is found, the operation shall create a new document-to-provider association with:
   - The document identifier set to the provided report identifier
   - The provider number set to the provider's identifier
   - The sign-off status set to unsigned

#### FR-RPR-6: Forwarding rule application

For each provider in the routing target list:

1. The operation shall retrieve the active forwarding rules for that provider.
2. For each forwarding rule whose type filter includes `HRM`:
   1. The operation shall extract the forward-to provider number from the rule.
   2. The operation shall check whether a document-to-provider association already exists for this report and the forward-to provider.
   3. If no existing association is found, the operation shall create and save a new document-to-provider association with unsigned sign-off status.

#### FR-RPR-7: Response

The operation shall return true if at least one provider was in the routing target list, false otherwise.

---

### 3.9 Route Report to Provider (Direct)

#### FR-RPX-1: Inputs

The operation shall accept:
- An integer report identifier
- A string provider number

#### FR-RPX-2: Core behavior

The operation shall create a new document-to-provider association record with:
- The document identifier set to the provided report identifier
- The provider number set to the provided provider number

#### FR-RPX-3: Side effects

The operation shall save the new association record to the database.

---

### 3.10 Route Report to Sub-Class

#### FR-RSC-1: Inputs

The operation shall accept:
- A parsed report object
- An integer report identifier

#### FR-RSC-2: Guard conditions

If the parsed report is null, the operation shall log an informational message and return without action.

#### FR-RSC-3: Report class filter

The operation shall only create sub-classification records if the report class is "Cardio Respiratory Report" or "Diagnostic Imaging Report" (case-insensitive comparison). For all other report classes, the operation shall take no action.

#### FR-RSC-4: Sub-classification creation

For each sub-classification entry in the report's sub-classification list:

The operation shall create a new sub-classification record with the following fields:
- **Active flag**: The first sub-classification entry shall be marked as active; all subsequent entries shall not be marked active.
- **Description**: Set from the third element of the sub-classification tuple.
- **Document identifier**: Set to the provided report identifier.
- **Mnemonic**: Set from the second element of the sub-classification tuple.
- **Observation date/time**: Set from the fourth element of the sub-classification tuple.
- **Sending facility identifier**: Set from the parsed report's sending facility identifier.
- **Sub-class code**: Set from the first element of the sub-classification tuple.

#### FR-RSC-5: Side effects

Each sub-classification record shall be saved to the database.

---

## 4. Non-Functional Requirements

### NFR-1: Character encoding

All file reading operations shall use UTF-8 encoding.

### NFR-2: Logging

The component shall log informational messages when beginning major operations (parsing, routing) and warning/error messages on failures. Log messages shall not contain patient health information (PHI).

### NFR-3: Schema validation

XML parsing shall validate against the Ontario MD HRM XSD schema. Reports that fail schema validation shall be rejected.

### NFR-4: Thread safety

The component shall be safe for concurrent use from multiple threads, as all operations are stateless.

---

## 5. External Dependencies

The component requires the following capabilities. How these are organized (one service, many services, direct database access, etc.) is an implementation choice.

- **Alternate care provider lookup**: Retrieve midwife, nurse, and resident provider identifiers associated with a patient's extended care record.
- **Document record lookup**: Retrieve a stored HRM document record by its integer identifier, including its associated report file path.
- **Document record update**: Update an existing HRM document record (e.g., to set the parent report reference).
- **Forwarding rule lookup**: Retrieve active forwarding rules for a given provider, including the rule's type filter and forward-to provider number.
- **Patient lookup by health card number**: Search for patient records matching a given health card number.
- **Patient lookup by name**: Search for patient records matching a given name string.
- **Provider lookup by practitioner number**: Resolve a healthcare provider from their regulatory practitioner number.
- **Provider lookup by provider number**: Resolve a healthcare provider from their system provider number.
- **Report-to-demographic association management**: Create and query associations between HRM documents and patient records, including assignment timestamps.
- **Report-to-provider association management**: Create, query, and check existence of associations between HRM documents and providers, including sign-off status tracking.
- **Report hash lookup**: Find all document identifiers sharing the same content hash (excluding demographic information).
- **Sub-classification record management**: Create sub-classification records linked to an HRM document, with active/inactive status tracking.
- **System property access**: Read system configuration properties (`DOCUMENT_DIR`, `omd_hrm_demo_matching_criteria`, `queens_resident_tagging`).
- **XSD schema resource loading**: Load an XML schema definition from a classpath resource path.

---

## 6. Response Summary

| Operation | Success Response | Failure Response |
|-----------|-----------------|------------------|
| Get appropriate date from report | Date value from first sub-class or event time | N/A (assumes valid input) |
| Get appropriate date string from report | Formatted date string | N/A (assumes valid input) |
| Parse report by document ID | Parsed report object | null (document not found) |
| Parse report by file path | Parsed report object | null (parse failure) |
| Parse report by file path with error collection | Parsed report object | null (errors added to collection if provided) |
| Route report to demographic (direct) | void (association created) | N/A |
| Route report to provider (by report and document) | Delegates to §3.8 | Delegates to §3.8 |
| Route report to provider (by report and ID) | true (providers routed) | false (no providers found) or void return on null guard |
| Route report to provider (direct) | void (association created) | N/A |
| Route report to sub-class | void (sub-classes created) | void (null guard or non-applicable class) |

---

## 7. Client Integration Contract

### 7.1 Parsed Report Object

The parse operations return an in-memory object encapsulating the parsed XML content with the following accessible data (alphabetical):

- **Address information**: Line 1, line 2, city, country subdivision code, phone number, postal code, zip code
- **Delivery information**: Delivery-to user identifier, first name, last name, formatted name
- **File information**: File location path, raw file content string
- **Health card information**: Health card number (HCN), HCN expiry date, HCN province code, HCN version code
- **Lifecycle identifiers**: Document ID, parent document ID (both mutable after construction)
- **Patient demographics**: Date of birth (as date components and formatted string), enrollment status, gender, legal first name, legal last name, legal full name ("LastName, FirstName"), person status
- **Report content**: Binary content (as byte array), binary flag, file extension, first report text content (Base64-encoded for binary, text otherwise), media type
- **Report metadata**: First report author physician (parsed name components), first report class, first report sub-class, message unique identifier, result status, sending author name, sending facility identifier, sending facility report number
- **Sub-classification data**: List of sub-classification tuples (code, mnemonic, description, date, formatted date string), first sub-class description, first sub-class date/time
- **Timeline**: First report event time
- **Vendor information**: Unique vendor ID sequence

### 7.2 Sub-classification Tuple Format

Each sub-classification entry is a list containing five elements in positional order:
1. Sub-class code (string)
2. Mnemonic (string)
3. Description (string)
4. Observation date/time (date)
5. Formatted date string (string)

### 7.3 System Properties

| Property Name | Type | Effect |
|---------------|------|--------|
| `DOCUMENT_DIR` | String (directory path) | Fallback directory for locating report files when the provided path does not resolve directly |
| `omd_hrm_demo_matching_criteria` | Boolean (active/inactive) | When active, enables strict demographic matching (HCN + date of birth + gender + last name). When inactive, uses basic matching (HCN + date of birth only). |
| `queens_resident_tagging` | Boolean (active/inactive) | When active, extends provider routing to include the patient's MRP and alternate care providers (midwife, nurse, resident) in addition to the addressed provider. |

---

## 8. Assumptions

- **Concurrent access**: Multiple threads may invoke parse and routing operations simultaneously; the implementation shall ensure thread safety.
- **Document directory configuration**: The `DOCUMENT_DIR` system property is expected to contain a valid filesystem path when report files are not found at their stored absolute paths.
- **HRM report schema availability**: The XSD schema file shall be available on the classpath at the specified resource path.
- **Practitioner number format**: The delivery-to user identifier in HRM reports contains a single-character prefix followed by the practitioner number. The first character shall always be stripped.
- **Report file encoding**: All HRM report files are XML documents encoded in UTF-8.
- **Sub-classification data presence**: Reports classified as "Cardio Respiratory Report" or "Diagnostic Imaging Report" are expected to have at least one sub-classification entry.

---

## 9. Verification Criteria

| Test ID | Requirement | Verification |
|---------|-------------|--------------|
| V-DAT-1 | FR-DAT-2 | Given a report with class "Diagnostic Imaging Report", verify the returned date matches the date/time from the first sub-classification entry. |
| V-DAT-2 | FR-DAT-2 | Given a report with class "Medical Records Report", verify the returned date matches the event time from the first report section. |
| V-DST-1 | FR-DST-2 | Given a "Cardio Respiratory Report", verify the returned string matches the pre-formatted date string from the first sub-classification. |
| V-DST-2 | FR-DST-2 | Given a "Medical Records Report" with a known event time, verify the returned string matches the expected `EEE MMM dd HH:mm:ss z yyyy` format. |
| V-PEC-1 | FR-PEC-2 | Given a null file path, verify the operation returns null without file system access. |
| V-PEC-2 | FR-PEC-3 | Given a file path that does not exist at the exact path but does exist in the document directory, verify the file is found and parsed successfully. |
| V-PEC-3 | FR-PEC-5 | Given a valid HRM XML file, verify it is parsed against the XSD schema and a parsed report object is returned containing the expected patient demographics. |
| V-PEC-4 | FR-PEC-7 | Given an XML file that fails schema validation, verify the operation returns null and the error is added to the error collection (if provided). |
| V-PEC-5 | FR-PEC-7 | Given a non-existent file path (at both resolution locations), verify the operation returns null. |
| V-PID-1 | FR-PID-2 | Given a non-existent document identifier, verify the operation returns null. |
| V-PID-2 | FR-PID-3 | Given a valid document identifier, verify the operation returns a parsed report object with content matching the stored report file. |
| V-RDD-1 | FR-RDD-2 | Verify that after routing, a document-to-demographic association exists with the correct demographic number, document ID, and a non-null assignment timestamp. |
| V-RPR-1 | FR-RPR-2 | Given a null parsed report, verify the operation returns false. |
| V-RPR-2 | FR-RPR-3 | Given a report with a valid delivery-to user identifier, verify the practitioner number is resolved by stripping the first character, and the corresponding provider receives a routing association. |
| V-RPR-3 | FR-RPR-4 | With `queens_resident_tagging` active, verify that the patient's MRP and alternate care providers receive routing associations when they differ from the primary provider. |
| V-RPR-4 | FR-RPR-5 | Verify that duplicate provider routing associations are not created when a provider is already linked to the report. |
| V-RPR-5 | FR-RPR-6 | Given a provider with an active forwarding rule of type `HRM`, verify a routing association is created for the forward-to provider. |
| V-RPR-6 | FR-RPR-6 | Given a provider with a forwarding rule whose type filter does not include `HRM`, verify no additional routing association is created. |
| V-RPR-7 | FR-RPR-7 | Verify the operation returns true when at least one provider is routed, and false when no provider is resolved. |
| V-RPX-1 | FR-RPX-2 | Verify that after direct provider routing, a document-to-provider association exists with the correct provider number and document ID. |
| V-RSC-1 | FR-RSC-2 | Given a null parsed report, verify no sub-classification records are created. |
| V-RSC-2 | FR-RSC-3 | Given a "Medical Records Report", verify no sub-classification records are created. |
| V-RSC-3 | FR-RSC-4 | Given a "Diagnostic Imaging Report" with three sub-classification entries, verify three records are created, with only the first marked as active. |
| V-RSC-4 | FR-RSC-4 | Verify each sub-classification record contains the correct code, mnemonic, description, date/time, sending facility ID, and document ID. |

---

## 10. Observed Behaviors (Non-Normative)

The following behaviors are observed in the existing system. They are documented for compatibility but are not mandated by the normative requirements.

- **Demographic routing is a private operation**: The demographic auto-matching logic (matching patients by HCN, optionally with gender/DOB/last name verification) is observed to be invoked only internally during the report intake workflow, not as a public entry point. A reimplementation may choose to expose or encapsulate this differently.

- **Error collection is pass-through**: When the error collection parameter is null, errors are logged but not accumulated. When non-null, each caught error object is appended to the collection in the order encountered. Only one error is typically appended per failed parse attempt.

- **File reference nullification**: After parsing, the file reference used during parsing is observed to be explicitly nullified. This has no functional effect on the returned result and appears to be a resource management hint.

- **Practitioner number prefix stripping is unconditional**: The first character of the delivery-to user identifier is always stripped regardless of its value. No validation is performed on the prefix character.

- **Report file path stored as-is**: The file path string passed to the parse operation is stored in the returned parsed report object exactly as provided, even if the file was actually resolved from the document directory fallback location.

- **Similar report detection is a private operation**: The logic for detecting similar reports and establishing parent-child relationships (using content hash comparison and multi-dimensional similarity scoring) is observed to be invoked only internally during the report intake workflow. The similarity scoring uses a threshold-based algorithm comparing report content, status, class, and date, with different weights for match vs. non-match on each dimension.

- **Similarity null status handling**: When comparing result status between two reports, if the new report's result status is null, the comparison is observed to treat status as matching regardless of the loaded report's status value.

- **Similarity scoring weights**: The similarity algorithm assigns weighted points across four dimensions. Content match contributes the highest weight, followed by date match, then class and status matches. Notably, some dimensions assign higher points for a non-match than for a match. The threshold for considering two reports as similar is 45 points.
