# Functional Specification: Consultation Request PDF Generator

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

This specification defines the externally observable behavior of a component that generates a PDF document representing a clinical consultation request (referral letter) within an electronic medical records (EMR) system. The PDF is used for printing or fax transmission of specialist referral requests.

**Traceability note:** This spec corresponds to the component identified as `ConsultationPDFCreator` in the existing codebase. This name is provided solely for traceability between the specification and the original system; it does not prescribe a class name, method name, or any internal naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Clinic letterhead and logo rendering in the PDF header
- Clinical detail sections (allergies, concurrent problems, medications, reason for consultation)
- Digital signature image embedding
- Fax copy-to recipient display when used in fax transmission mode
- Localized field labels via resource bundles
- Multi-site letterhead configuration support
- Patient demographic information display
- PDF document generation with letter-sized page format
- Referring practitioner and most responsible provider (MRP) footer
- Reply instruction header with configurable booking modes
- Specialist information display (contact details, service, urgency)

**Out of scope:**
- Authentication and session management (provided by the surrounding framework)
- Consultation request data entry and persistence (handled by a separate form component)
- Fax transmission mechanics (handled by a separate fax subsystem)
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
| **Clinic data** | The default practice location's contact information (name, address, phone, fax), used as fallback when no letterhead override is configured. |
| **Consultation request** | A clinical referral from a referring provider to a specialist, containing patient information, clinical details, urgency, and appointment scheduling data. Identified by a unique request ID. |
| **Digital signature** | A stored image (as binary data) representing a provider's handwritten or electronic signature, identified by a numeric ID. |
| **Fax copy-to recipient** | An additional recipient (name and fax number) who should receive a copy of the consultation request when transmitted via fax. |
| **Health insurance number (HIN)** | A patient's provincial health card number, displayed with a type code prefix and optional version code suffix. |
| **Letterhead** | The header portion of the PDF identifying the sending clinic or provider. Can be the clinic name, a program name, or an individual provider name depending on configuration. |
| **Most responsible provider (MRP)** | The primary physician responsible for a patient's overall care, also referred to as "family doctor." Identified by a provider number stored on the patient demographic record. |
| **Multi-site mode** | A system configuration where the EMR operates across multiple physical clinic locations. When enabled, logos and letterhead may vary by site. |
| **Patient will book (PWB)** | A referral mode where the patient is responsible for scheduling their own appointment with the specialist, rather than the referring clinic booking on their behalf. Indicated by the flag value `"1"`. |
| **Professional specialist** | The healthcare professional to whom the consultation referral is directed. Contains formatted title, contact details, and street address. |
| **Referring practitioner** | The provider who created the consultation request. Their name and billing number are displayed in the PDF footer. |
| **Resource bundle** | A locale-specific properties file providing translated labels for all text fields in the PDF. Keys follow the pattern `oscarEncounter.oscarConsultationRequest.consultationFormPrint.*`. |
| **Urgency level** | A classification of the consultation's priority: urgent (`"1"`), non-urgent (`"2"`), or return visit (`"3"`). |

---

## 2. External Interface

### 2.1 Programmatic API

The component shall be instantiated programmatically and invoked to write a PDF to a provided output stream. It is not directly accessible via HTTP — it is called by other components (print actions, fax actions) that handle HTTP request/response.

### 2.2 Construction Modes

The component shall support two construction modes:

| Mode | Inputs | Description |
|------|--------|-------------|
| Fax transmission | Fax form data (wrapping HTTP request) + output stream | Generates a consultation PDF with additional fax copy-to recipient information |
| Print/standard | HTTP request + output stream | Generates a consultation PDF for printing or direct download |

### 2.3 PDF Generation Entry Point

The component shall expose a single entry point to generate the PDF. This entry point requires an authenticated session context.

---

## 3. Functional Requirements

### 3.1 Clinic Header

#### FR-CLH-1: Inputs

The clinic header section uses the following data from the consultation request:
- Letterhead address (optional override)
- Letterhead fax number (optional override)
- Letterhead name identifier (determines which name to display)
- Letterhead phone number (optional override)
- Letterhead title (e.g., "Dr")

#### FR-CLH-2: Letterhead name resolution

The component shall resolve the displayed letterhead name using the following precedence (evaluated in order, first match wins):

1. **Program name**: If the letterhead name identifier begins with `"prog_"`, the component shall extract the numeric suffix and look up the corresponding program name.
2. **Provider name**: If the letterhead name identifier is not `"-1"` and does not match the default clinic name, the component shall look up the provider record for that identifier. If a valid provider is found:
   - If the letterhead title equals `"Dr"`, the full first name shall be used.
   - Otherwise, the prefix "Dr. " shall be stripped from the first name (if present).
   - The display name shall be formatted as `"FirstName LastName"`.
   - If no valid provider is found, the component shall fall back to the default clinic name.
3. **Default clinic name**: In all other cases, the component shall use the default clinic name.

#### FR-CLH-3: Address resolution

- If a letterhead address override is provided (non-empty after trimming), the component shall display that address.
- Otherwise, the component shall display the default clinic address formatted as: street address on one line, then `"City, Province. PostalCode"`.

#### FR-CLH-4: Telecom resolution

- **Phone**: If a letterhead phone override is provided (non-empty after trimming), display `"Phone: {override}"`. Otherwise, display `"Phone: {clinicPhone}"`.
- **Fax**: If a letterhead fax override is provided (non-empty after trimming), display `"Fax: {override}"`. Otherwise, display `"Fax: {clinicFax}"`.

### 3.2 Clinical Detail Sections

#### FR-CDS-1: Section display rules

The clinical detail sections shall be rendered in the following causal order, with each section displayed only if its content is non-null and meets a minimum length:

1. **Reason for consultation**: Always displayed.
2. **Clinical information**: Displayed only if content length exceeds 1 character.
3. **Significant concurrent problems**: Displayed only if content length exceeds 0 characters (non-empty).
4. **Current medications**: Displayed only if content length exceeds 1 character.
5. **Allergies**: Displayed only if content length exceeds 1 character.

#### FR-CDS-2: Section heading customization

- The **concurrent problems** heading shall use the value of the `significantConcurrentProblemsTitle` configuration property if that property is set and its length exceeds 1 character. Otherwise, the localized default label shall be used.
- The **current medications** heading shall use the value of the `currentMedicationsTitle` configuration property if that property is set and its length exceeds 1 character. Otherwise, the localized default label shall be used.
- All other section headings shall use localized resource bundle values.

#### FR-CDS-3: HTML-to-text conversion

All clinical detail text shall have HTML line break tags (including variants like `<br>`, `<br/>`, `<br />`) converted to newline characters, and HTML non-breaking space entities (`&nbsp;`) converted to regular spaces.

### 3.3 Consultation Request Heading

#### FR-CRH-1: Heading display

The PDF shall include a centered heading row between the clinic header and the date line, displaying the localized consultation request heading text in bold 12-point font.

### 3.4 Date Line

#### FR-DTL-1: Date display

The date line shall display a label followed by either:
- The localized "Patient Will Book" text, if the PWB flag is `"1"`.
- The referral date from the consultation request, otherwise.

### 3.5 Digital Signature

#### FR-SIG-1: Guard condition

The digital signature section shall only be rendered if the consultation request has a non-empty signature image identifier.

#### FR-SIG-2: Signature retrieval

The component shall retrieve the digital signature record using the numeric signature image identifier. If the identifier is not a valid number, or the signature record is not found, the signature section shall be silently omitted (no error displayed).

#### FR-SIG-3: Signature rendering

When a valid digital signature is found, the component shall:
- Display a localized "Signature:" label.
- Render the signature image data scaled to 80% of its original size.

### 3.6 Fax Copy-To Recipients

#### FR-FAX-1: Guard condition

The fax copy-to section shall only be rendered when the component was constructed in fax transmission mode.

#### FR-FAX-2: Recipient display

When in fax transmission mode, the specialist section shall include an additional subsection with:
- A "Fax Copy(s) to:" label.
- For each copy-to recipient: the recipient's fax number and name.

### 3.7 Logo Header

#### FR-LGO-1: Guard condition

The logo section shall only be rendered when the `faxLogoInConsultation` configuration property is set (non-null).

#### FR-LGO-2: Logo resolution in multi-site mode

When the `multisites` configuration property is set and its value is `"on"` (case-insensitive):
1. The component shall look up the site record using the site identifier from the consultation request.
2. If the site has a logo document ID configured, the component shall retrieve the corresponding document record and construct the file path from the configured document storage directory and the document filename.
3. If no site-specific logo is configured, the component shall fall back to the path specified in the `faxLogoInConsultation` configuration property.

#### FR-LGO-3: Logo resolution in single-site mode

When the `multisites` property is not `"on"`, the component shall use the file path specified in the `faxLogoInConsultation` configuration property.

#### FR-LGO-4: Logo rendering

If the resolved logo file exists on disk, the component shall render it as an image scaled to fit within half the page width and 50 points in height. If the file does not exist, the logo area shall be empty.

### 3.8 Patient Information

#### FR-PAT-1: Patient fields

The patient section shall display the following fields in the order listed (this order is causally determined by the layout convention of placing identity fields first, then contact, then administrative):

1. Patient name
2. Address (with HTML-to-text conversion applied)
3. Phone
4. Cell phone
5. Work phone
6. Email
7. Date of birth, followed by the literal suffix `" (y/m/d)"`
8. Sex
9. Health card number, formatted as `"(CardType) HealthNumber VersionCode"`

#### FR-PAT-2: Conditional appointment fields

If the PWB flag is NOT `"1"`, the following additional fields shall be displayed:
- Appointment date
- Appointment time, formatted as `"Hour:Minute Period"` where the colon is omitted if the minute value is empty, and the period indicates AM/PM.

#### FR-PAT-3: Chart number

The chart number field shall always be displayed after the appointment fields (or after the health card number if PWB is active).

### 3.9 PDF Document Properties

#### FR-PDF-1: Page format

The PDF shall use letter-sized pages (8.5 × 11 inches).

#### FR-PDF-2: Document metadata

- The PDF title shall be set to the localized consultation request label.
- The PDF creator shall be set to `"CARLOS EMR"`.

#### FR-PDF-3: Font specification

The PDF shall use the Helvetica font family with CP1252 encoding (Western Latin). Two sizes shall be used:
- 10-point normal weight for body text.
- 12-point normal and bold weight for headings.

### 3.10 Referring Practitioner and MRP Footer

#### FR-RPM-1: Referring practitioner display

If the `printPDF_referring_prac` configuration property evaluates to a boolean-positive value (e.g., `"yes"`):
- The component shall display the localized referring practitioner label followed by the referring provider's name.
- If the referring provider has a billing number (non-empty), it shall be appended in parentheses: `"ProviderName (BillingNumber)"`.

If the property evaluates to boolean-negative, the referring practitioner field shall be blank.

#### FR-RPM-2: MRP / family doctor display

If the `mrp_model` configuration property evaluates to a boolean-positive value (e.g., `"yes"`):
- The component shall look up the patient's demographic record to find the assigned MRP provider.
- The component shall display the localized family doctor label followed by the family doctor's name.
- If the MRP provider has a billing number (non-empty), it shall be appended in parentheses: `"DoctorName (BillingNumber)"`.

If the property evaluates to boolean-negative, the family doctor field shall be blank.

### 3.11 Reply Instructions Header

#### FR-RPL-1: Reply text resolution

The reply instruction header shall display text determined by the following precedence (evaluated in order, first match wins):

1. **Patient will book**: If the PWB flag is `"1"`, display the localized "patient will book" reply message.
2. **Custom appointment instructions**: If the `CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP` configuration property evaluates to true (defaults to `"true"`), display the appointment instructions label from the consultation request data.
3. **Multi-site mode**: If multi-site mode is enabled, display `"Please reply"`.
4. **Default**: Display the localized message parts combined as `"{PleaseReplyPart1} {ClinicName} {PleaseReplyPart2}"`.

### 3.12 Specialist Information

#### FR-SPC-1: Specialist fields

The specialist section shall display the following fields (this order is causally determined by the convention of placing identification first, then classification, then contact):

1. **Consultant name**: The specialist's formatted title (including salutation, name, and professional letters). If no specialist record is available, display empty.
2. **Urgency**: Mapped from the urgency code:
   - `"1"` → localized "Urgent" label
   - `"2"` → localized "Non-Urgent" label
   - `"3"` → localized "Return Visit" label
   - Any other value → two spaces (effectively blank)
3. **Service**: The service name looked up from the service identifier on the consultation request.
4. **Phone**: The specialist's phone number. Empty if no specialist record.
5. **Fax**: The specialist's fax number. Empty if no specialist record.
6. **Address**: The specialist's street address (with HTML-to-text conversion applied). Empty if no specialist record.

---

## 4. Non-Functional Requirements

### 4.1 Localization

- **NFR-L10N-1:** All field labels in the PDF shall be sourced from a locale-specific resource bundle, resolved using the locale from the originating HTTP request.
- **NFR-L10N-2:** The resource bundle key prefix for consultation form print labels shall be `oscarEncounter.oscarConsultationRequest.consultationFormPrint.`.

### 4.2 Reliability

- **NFR-REL-1:** If font initialization fails during construction, the component shall throw an error and refuse to produce a malformed PDF.
- **NFR-REL-2:** If a logo image file cannot be read or is malformed, the error shall be logged and the logo area shall be silently omitted.
- **NFR-REL-3:** If the digital signature image cannot be rendered, the error shall be logged and the signature area shall be silently omitted.

### 4.3 Security

The following security behaviors are **required for any reimplementation** per CARLOS EMR standards:

- **NFR-SEC-1:** The component shall require an authenticated session context before generating a PDF.
- **NFR-SEC-2:** The component shall validate all filesystem paths constructed from configuration or document metadata to prevent path traversal attacks.

---

## 5. External Dependencies

The component requires the following capabilities from the surrounding system. How these are organized (one service, many services, direct database access, etc.) is an implementation choice.

- Determine if multi-site mode is enabled
- Load consultation request data by request ID (patient demographics, specialist info, clinical details, appointment data, urgency, provider references, letterhead configuration, signature references)
- Look up a digital signature record by numeric ID and retrieve its image data
- Look up a patient demographic record by numeric ID to obtain the assigned MRP provider number
- Look up a program name by numeric program ID
- Look up a provider record by provider number to obtain name and billing number
- Look up a site record by site ID to obtain site-specific logo document ID
- Look up document metadata by document ID to obtain the filename (for site logo resolution)
- Read application configuration properties (logo path, multi-site mode, section heading overrides, referring practitioner display, MRP display, appointment instructions lookup)
- Read locale-specific resource bundles for label translation
- Render a PDF document to an output stream with configurable page size, fonts, tables, images, and document metadata
- Resolve provider name and formatted title details (first name, surname)
- Resolve the default clinic's contact information (name, address, city, province, postal code, phone, fax)
- Retrieve the document storage directory path from application configuration

---

## 6. Response Summary

| Operation | Output |
|-----------|--------|
| Generate PDF (fax mode) | PDF written to output stream, includes fax copy-to recipients in specialist section |
| Generate PDF (standard mode) | PDF written to output stream |

---

## 7. Client Integration Contract

### 7.1 Construction

The calling component shall provide:
- An HTTP request object containing either a `reqId` request parameter or a `reqId` request attribute (parameter takes precedence) identifying the consultation request to render.
- An output stream to which the PDF bytes shall be written.
- Optionally, a fax form data object wrapping the HTTP request, which provides copy-to recipient information.

### 7.2 PDF Generation

The calling component shall invoke the generation entry point with an authenticated session context. Upon return, the output stream shall contain a complete, valid PDF document in letter format.

### 7.3 Output Format

- Content type: `application/pdf`
- Page size: US Letter (8.5 × 11 inches)
- Font: Helvetica, CP1252 encoding
- Creator metadata: `"CARLOS EMR"`

---

## 8. Assumptions

- The authenticated session context is valid and contains a provider identity.
- The consultation request ID provided via the HTTP request resolves to a valid consultation request record.
- The default clinic data is configured and accessible.
- The document storage directory (for site logos) exists and is readable by the application server process.
- The locale-specific resource bundle contains all required keys for the consultation form print labels.
- The output stream provided by the caller is open and writable.

---

## 9. Verification Criteria

An implementation shall be considered correct if it satisfies all of the following observable tests:

| Test ID | Requirement | Verification |
|---------|-------------|-------------|
| V-CDS-1 | FR-CDS-1 | A consultation request with all clinical sections populated produces a PDF containing all five sections with their respective headings. |
| V-CDS-2 | FR-CDS-1 | A consultation request with empty allergies and empty medications produces a PDF with only the reason for consultation section and any non-empty sections. |
| V-CDS-3 | FR-CDS-2 | When `significantConcurrentProblemsTitle` is configured with a custom value longer than 1 character, the PDF uses that value as the section heading instead of the localized default. |
| V-CDS-4 | FR-CDS-3 | Text containing `<br/>` tags and `&nbsp;` entities renders with line breaks and regular spaces in the PDF, not HTML markup. |
| V-CLH-1 | FR-CLH-2 | A consultation request with a letterhead name starting with `"prog_"` followed by a valid program ID displays the program name in the header. |
| V-CLH-2 | FR-CLH-2 | A consultation request with a provider-based letterhead name displays `"FirstName LastName"` in the header. |
| V-CLH-3 | FR-CLH-2 | When the letterhead title is not `"Dr"` and the provider's first name starts with `"Dr. "`, the prefix is stripped from the display name. |
| V-CLH-4 | FR-CLH-3 | A consultation request with a non-empty letterhead address override displays that address instead of the clinic default. |
| V-CLH-5 | FR-CLH-4 | A consultation request with letterhead phone and fax overrides displays the override values instead of the clinic defaults. |
| V-CRH-1 | FR-CRH-1 | The PDF contains a centered bold heading row displaying the localized consultation request heading text between the clinic header and date line. |
| V-DTL-1 | FR-DTL-1 | When PWB is `"1"`, the date line displays the localized "Patient Will Book" text instead of a date. |
| V-DTL-2 | FR-DTL-1 | When PWB is not `"1"`, the date line displays the referral date. |
| V-FAX-1 | FR-FAX-1, FR-FAX-2 | A PDF generated in fax mode with two copy-to recipients shows both recipients' names and fax numbers in the specialist section. |
| V-FAX-2 | FR-FAX-1 | A PDF generated in standard mode does not contain any fax copy-to information. |
| V-LGO-1 | FR-LGO-1 | When `faxLogoInConsultation` is not set, the PDF contains no logo. |
| V-LGO-2 | FR-LGO-2 | In multi-site mode with a site-specific logo configured, the PDF displays that site's logo. |
| V-LGO-3 | FR-LGO-3 | In single-site mode with `faxLogoInConsultation` pointing to a valid image, the PDF displays that logo. |
| V-LGO-4 | FR-LGO-4 | When the logo file does not exist on disk, the PDF renders without a logo and no error is visible to the user. |
| V-PAT-1 | FR-PAT-1 | The patient section displays name, address, phone, cell phone, work phone, email, date of birth with `"(y/m/d)"` suffix, sex, and health card number. |
| V-PAT-2 | FR-PAT-2 | When PWB is not `"1"`, the appointment date and time fields are visible. When PWB is `"1"`, they are absent. |
| V-PAT-3 | FR-PAT-1 | The health card number is formatted as `"(CardType) HealthNumber VersionCode"`. |
| V-PDF-1 | FR-PDF-1, FR-PDF-2 | The generated PDF has letter page size, title set to the localized consultation request label, and creator set to `"CARLOS EMR"`. |
| V-RPL-1 | FR-RPL-1 | When PWB is `"1"`, the reply header shows the localized patient-will-book reply message. |
| V-RPL-2 | FR-RPL-1 | When custom appointment instructions lookup is enabled and PWB is not `"1"`, the reply header shows the appointment instructions label. |
| V-RPL-3 | FR-RPL-1 | When multi-site mode is active and neither PWB nor custom instructions apply, the reply header shows `"Please reply"`. |
| V-RPL-4 | FR-RPL-1 | In the default case (single site, no PWB, no custom instructions), the reply header shows `"{Part1} {ClinicName} {Part2}"`. |
| V-RPM-1 | FR-RPM-1 | When `printPDF_referring_prac` is enabled and the provider has a billing number, the footer shows `"ProviderName (BillingNumber)"`. |
| V-RPM-2 | FR-RPM-2 | When `mrp_model` is enabled and the patient's MRP has a billing number, the footer shows `"DoctorName (BillingNumber)"`. |
| V-RPM-3 | FR-RPM-1 | When `printPDF_referring_prac` is disabled, the referring practitioner area is blank. |
| V-SIG-1 | FR-SIG-1 | When the consultation request has no signature image identifier, no signature section appears in the PDF. |
| V-SIG-2 | FR-SIG-2, FR-SIG-3 | When a valid signature image identifier is provided, the PDF contains a "Signature:" label followed by the signature image scaled to 80%. |
| V-SIG-3 | FR-SIG-2 | When the signature image identifier is invalid (non-numeric or not found), the signature section is silently omitted. |
| V-SPC-1 | FR-SPC-1 | The specialist section displays the formatted title, urgency, service name, phone, fax, and address for a fully populated specialist record. |
| V-SPC-2 | FR-SPC-1 | When no specialist record is available, the specialist name, phone, fax, and address fields are empty. |
| V-SPC-3 | FR-SPC-1 | Urgency code `"1"` displays the localized "Urgent" label; `"2"` displays "Non-Urgent"; `"3"` displays "Return Visit". |

---

## 10. Observed Behaviors (Non-Normative)

These notes document externally observable characteristics of the existing system that are not captured by the normative requirements above. They are provided for compatibility reference. An implementer should replicate these behaviors unless there is a clear reason to improve upon them.

- **Address format in clinic header** — The default clinic address is observed to format as `"Street"` on one line, then `"City, Province. PostalCode"` with a period after the province abbreviation and a space before the postal code.
- **Fax copy-to column ordering** — In the copy-to recipient listing, the fax number is observed to appear in the label column and the recipient name in the data column, which is reversed from the typical label-then-data convention.
- **Footer colon handling** — When a footer field's data value ends with a colon character, an additional colon is observed to be appended before the data. This appears to be an unintentional formatting artifact.
- **Image buffer size** — Logo images are observed to be read into a fixed-size 256 KB buffer, which silently truncates images larger than this size. The resulting image may be corrupt or partially rendered.
- **Layout with logo** — When a logo is configured, the header is observed to use a two-column layout with the logo in the left column and clinic information in the right column. Without a logo, the clinic information spans the full width.
- **Multi-site check inconsistency** — The logo resolution is observed to check only for the `multisites` property value `"on"` (case-insensitive), while the reply header uses a broader check that also accepts `"yes"` and `"true"`. A deployment with `multisites=yes` would show the multi-site reply text but use the single-site logo path.
