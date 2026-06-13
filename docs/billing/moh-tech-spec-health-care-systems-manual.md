# Interface to Health Care Systems Technical Specifications

> **Source PDF**: [moh-tech-spec-health-care-systems-manual-en-2023-06-12.pdf](moh-tech-spec-health-care-systems-manual-en-2023-06-12.pdf) — Ontario Ministry of Health, Claims Services Branch, OHIP, Pharmaceuticals and Devices Division.
> **Manual version**: 6.2, effective April 1, 2023.
> **Conversion date**: 2026-04-29.
> **Tool**: marker-pdf 1.10.2 (`marker_single --output_format markdown --paginate_output --disable_image_extraction`), followed by a manual page-by-page cleanup pass (heading-level normalization, OCR-confusable fixes, TOC reconstruction on p.3, missing record-type subheadings inserted on pp.20/27/31).
> **Authoritative source**: the PDF at the link above. If this Markdown and the PDF disagree, **the PDF wins**. Do not rely on this Markdown alone for billing-format decisions in production code — open the PDF for the field-by-field truth.
> **External hyperlinks**: the PDF references several supplementary OHIP code tables and tech specs that are not included inline. Snapshots of those resources have been captured under [`external/`](external/) and indexed in [`README.md`](README.md). MOH itself was returning HTTP 502 with an expired TLS cert at conversion time, so the snapshots were taken via the Internet Archive Wayback Machine.
> **Related docs**: implementation of the Ontario billing module that consumes this spec is documented in [`../billing-ontario-module.md`](../billing-ontario-module.md). Key Java touchpoints: `OhipClaimFileService` (fixed-width claim-file generation), `BillingOnRaService` and `OnRaImportService`/`OnRaSettlementService` (RA parsing, import, settlement), `BillingDiskCreationService` (batch assembly), and the claim record DTOs under `billings/ca/on/dto/`.

Page boundaries from the source PDF are marked with `<!-- page NNN -->` HTML comments throughout this document. Use them to cross-reference the PDF when the Markdown rendering is ambiguous.

---

```xml
<!-- page 001 -->

Claims Services Branch, OHIP, Pharmaceuticals and Devices Division, Ministry of Health Version 6.2

April 1, 2023

<!-- page 002 -->

All possible measures are exerted to ensure accuracy of the contents of this manual; however, the manual may contain typographical or printing errors. The public is cautioned against complete reliance upon the contents hereof without confirming the accuracy and currency of the information contained herein. The Crown in Right of Ontario, as represented by the Minister of Health and Long-Term Care, assumes no responsibility for any person's use of the material herein or any costs or damages associated with such use.

<!-- page 003 -->

## **Table of Contents**

|         | Chapter 1 Introduction                                | 5  |
|---------|-------------------------------------------------------|----|
| 1.      | Introduction                                          | 6  |
| 1.1.    | Introduction                                          | 6  |
| 1.2.    | Contact Number and Email                              | 6  |
|         | Chapter 2 General information                         | 7  |
| 2.      | General Information                                   | 8  |
| 2.1.    | Processing Schedules                                  | 8  |
| 2.2.    | Medical Claims Electronic Data Transfer (MCEDT)       | 8  |
|         | Chapter 3 Claims Submission                           | 9  |
| 3.      | Claims Submission                                     | 10 |
| 3.1.    | Claims Submission References                          | 10 |
| 3.2.    | Other Technical Specifications                        | 10 |
|         | Chapter 4 Electronic Input Specifications             | 11 |
| 4.      | Electronic Input Specifications                       | 12 |
| 4.1.    | Media Types                                           | 12 |
| 4.2.    | File Naming Convention                                | 12 |
| 4.3.    | Claim Submission                                      | 13 |
| 4.4.    | Format Summary                                        | 13 |
| 4.5.    | Batch File Submission Sample                          | 14 |
| 4.6.    | Summary of Data Requirements                          | 15 |
| 4.7.    | Electronic Input (EI) Record Layout                   | 17 |
| 4.8.    | Specialty Codes                                       | 37 |
| 4.9.    | Services Requiring Diagnostic Codes                   | 37 |
| 4.10.   | Fee Schedule Code Relationships                       | 39 |
| 4.11.   | Fee Schedule Code Suffix B/C Exceptions               | 65 |
| 4.12.   | Service Codes Requiring Specialized Submissions       | 67 |
| 4.13.   | Service Location Indicator Codes                      | 68 |
| 4.14.   | MOD 10 Check Digit                                    | 78 |
| 4.15.   | Province Code and Numbering                           | 79 |
| 4.16.   | Valid Payment Program/Payee Combinations              | 79 |
| 4.16.1. | Legend                                                | 79 |
| 4.17.   | Workplace Safety and Insurance Board (WSIB)           | 80 |

<!-- page 004 -->

|              | Chapter 5 Electronic Output Specifications for Reports<br>                            | 81        |
|--------------|---------------------------------------------------------------------------------------|-----------|
| 5.           | Electronic Output (EO) Specifications for Reports<br>                                 | 82        |
| 5.1.         | Claims Batch Edit Reports                                                             | 82        |
| 5.2.         | Remittance Advice (RA)<br>                                                            | 82        |
| 5.3.         | Remittance Advice Data Sequences<br>                                                  | 83        |
| 5.4.         | File Naming Convention –<br>Remittance Advice                                         | 85        |
| 5.5.         | Format Summary                                                                        | 86        |
| 5.6.<br>5.7. | Remittance Advice (RA) Record Layout<br><br>Accounting Transactions for Record Type 7 | 89<br>105 |
| 5.8.         | Remittance Advice Explanatory Codes                                                   | 106       |
| 5.9.         | Generic Governance Report                                                             | 106       |
|              | Chapter 6 Rejection Conditions                                                        | 113       |
| 6.           | Rejection Conditions<br>                                                              | 114       |
| 6.1.         | Correction of Errors<br>                                                              | 114       |
| 6.2.         | Rejection Categories<br>                                                              | 114       |
| 6.3.         | Error Report Explanatory Message Codes<br>                                            | 137       |
| 6.4.         | Error Report Rejection Conditions –<br>Error Codes                                    | 137       |
|              | Chapter 7 Health Card Magnetic Stripe Specifications                                  | 138       |
| 7.           | Health Card Magnetic Stripe Specifications<br>                                        | 139       |
| 7.1.         | Health Card Types                                                                     | 139       |
| 7.2.         | Magnetic Stripe Specifications for Photo Health Card<br>                              | 141       |
|              | Chapter 8 Information Management System (IMS) Connect<br>                             | 145       |
| 8.           | Infrmation Management System (IMS) Connect<br>                                        | 146       |
| 8.1.         | Information Management System Connect                                                 | 146       |
|              | 8.1.1 TCP/IP Data Specifications for use with IMS Connect<br>                         | 149       |
|              | 8.1.2 TCP/IP Socket Troubleshooting<br>                                               | 151       |
|              | 8.1.3 IMS Connect Information                                                         | 152       |
| 8.2.         | GO Net TCP/IP Data Specifications for use with Information Management                 |           |
|              | System (IMS)<br>Listener<br>                                                          | 160       |
|              | Chapter 9 Glossary                                                                    | 166       |
| Glossary     |                                                                                       | 167       |

<!-- page 005 -->

## **Chapter 1 Introduction**

<!-- page 006 -->

## **1. Introduction**

## **1.1. Introduction**

This manual is provided for developers of computer systems used by health care providers.

It specifies the content and format of the information exchanged with the Ministry of Health (ministry) and the operational procedures to be followed.

The technical specifications contained in this text are subject to change by the ministry. The ministry will attempt to provide 60 days' notice of any change.

## **1.2. Contact Number and Email**

Any questions or concerns regarding the content of this manual should be directed to the Ministry of Health Service Support Contact Centre at 1 800-262-6524 or email at [SSContactCentre.MOH@ontario.ca.](mailto:SSContactCentre.MOH@ontario.ca) The desk is staffed from 8:00 a.m. to 5:00 p.m., Monday to Friday, except holidays.

<!-- page 007 -->

## **Chapter 2 General information**

<!-- page 008 -->

## **2. General Information**

## **2.1. Processing Schedules**

Claims should be submitted frequently, for example, daily or weekly throughout the month to facilitate smooth processing and timely correction of errors.

## **2.2. Medical Claims Electronic Data Transfer (MCEDT)**

The ministry operates on a monthly processing cycle. Submissions received by the 18th of the month will typically be processed for approval the following month. When the 18th falls on a weekend or a holiday, the deadline will be extended to the next business day.

MCEDT submissions received after the 18th may not be approved until the next monthly processing cycle (i.e. submissions received on November 18th will appear on the December Remittance Advice (RA) submissions received after November 18th may not appear until the January RA).

Please see the [Medical Claims Electronic Data Transfer Reference Manual](http://www.health.gov.on.ca/en/pro/publications/ohip/mcedt_mn.aspx) for additional information.

<!-- page 009 -->

## **Chapter 3 Claims Submission**

<!-- page 010 -->

## **3. Claims Submission**

## 3.1. **Claims Submission References**

[Master Numbering System](http://www.health.gov.on.ca/en/common/ministry/publications/reports/master_numsys/master_numsys.aspx)

[Medical Claims Electronic Data Transfer Reference Manual](http://www.health.gov.on.ca/en/pro/publications/ohip/mcedt_mn.aspx)

[Resources for Physicians](https://www.health.gov.on.ca/en/pro/programs/ohip/default.aspx)

[Schedule of Benefits for Physician Services](http://www.health.gov.on.ca/english/providers/program/ohip/sob/physserv/physserv_mn.html)

**Service codes** requiring diagnostic codes, prior authorization or supporting documentation are located in [Services Requiring Diagnostic Codes](#page-36-0) and [Service Codes](#page-66-0)  [Requiring Specialized Submissions.](#page-66-0)

## 3.2. **Other Technical Specifications**

[Technical Specification for Medical Claims Electronic Data Transfer \(MCEDT\) Service](http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_mcedt_ebs.pdf)  [via Electronic Business Services \(EBS\) MCEDT](http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_mcedt_ebs.pdf)

[Technical Specifications for Electronic Business Services \(EBS\)](http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_ebs.pdf)

[Technical Specification for Health Card Validation \(HCV\) Service via Electronic](http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_hcv_ebs.pdf)  [Business Services \(EBS\)](http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_hcv_ebs.pdf)

[Technical Specifications for the Outside Use Report](http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_outside_use_en.pdf) (Patients with Signed Consent)

[Technical Specifications Questions and Answers for Medical Claims Electronic Data](http://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/faq_tech_specs.aspx)  [Transfer \(M](http://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/faq_tech_specs.aspx)CEDT), Health Card Validation (HCV), e-Business Services and Conformance Test

<!-- page 011 -->

## **Chapter 4 Electronic Input Specifications**

<!-- page 012 -->

## **4. Electronic Input Specifications**

## **4.1. Media Types**

### **MCEDT**

ASCII Data Content Logical Record Length must be 79 characters Internet connection

## **4.2. File Naming Convention**

The Input Claims Submission must have file names in the following format:

**H Month Group Number or Provider Number Sequence Number**

Example: HA123456.001 or HA1234.001

- Field 1 H represents the claims input billing
- Field 2 Alpha representation for current processing cycle (e.g. A for January, B for February)
- Field 3 Health care provider's **registered group number or solo health care provider number**
- Field 4 Three digit sequence number assigned by the health care provider

Each input file must have a Batch Trailer Record at the end of the file(s). The file names must have a unique sequence number when there is more than one file per submission.

There must be a carriage return (hex value 0D) at the end of each record. The end of the file must be indicated by a CTRL Z (hex value of 1A) or CTRL D (hex value of 04).

<!-- page 013 -->

## **4.3. Claim Submission**

Submissions include:

- In-province medical claims detailed in the Schedule of Benefits, including services that require additional information or prior authorization (referred to as Health Claim Payment (HCP) Claims)
- Reciprocal Medical Billing (RMB) claims
- Workplace Safety and Insurance Board (formally WCB now WSIB) claims

These categories are identified as Payment Programs HCP, RMB, and WCB respectively. Other types of submissions may be included in the future (refer to

Section 4.16 – [Valid Payment Program/Payee Combinations\).](#page-78-0)

Billing software must allow for electronic submission of all payment programs.

## **4.4. Format Summary**

| Record Type                        | Description                                                                                                                                                                                                         |  |  |  |
|------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--|--|--|
| B -<br>Batch Header Record         | The first record of each batch must be a Batch<br>Header Record.<br>In multiple batch submissions, the<br>first record of each subsequent batch must always<br>be a Batch Header Record.                            |  |  |  |
| H<br>-<br>Claim Header-1<br>Record | A Claim Header-1<br>Record must always follow<br>each Batch Header Record and must always be<br>present for each claim.                                                                                             |  |  |  |
| R<br>-<br>Claim Header-2 Record    | A Claim Header-2 Record is required only for<br>reciprocal claims.<br>If required, a Claim Header-2<br>Record must follow the Claim Header-1<br>Record.                                                             |  |  |  |
| T<br>-<br>Item Record              | An option of having two items per Item Record<br>has been provided and may be utilized.                                                                                                                             |  |  |  |
| E -<br>Batch Trailer Record        | A Batch Trailer Record must be present at the end<br>of every batch and contain the appropriate counts<br>of the number of Claim<br>Header-1<br>Records (H),<br>Claim Header-2 Records (R) and Item Records<br>(T). |  |  |  |

<!-- page 014 -->

## **4.5. Batch File Submission Sample**

Fixed Record Length: must be 79 Characters

The above illustration is a visual representation of the record types outlined on the previous page to assist in understanding the record types and information contained within.

<!-- page 015 -->

## **4.6. Summary of Data Requirements**

|                                   | Payment Program | Payment Program | Non Patient      |
|-----------------------------------|-----------------|-----------------|------------------|
| Record<br>Field                   | HCP / WCB       | RMB             | Encounter Claims |
| Claim Header-1                    | Mandatory       | Mandatory       |                  |
| Health Number                     | Mandatory       |                 | Not Required     |
| Version Code                      | Mandatory       | Not Required    | Not Required     |
| Patient Birthdate                 | Mandatory       | Mandatory       | Not Required     |
| Accounting Number                 | Optional        | Optional        | Optional         |
| Payment Program                   | Mandatory       | Mandatory       | Mandatory        |
| Payee                             | Conditional     | Conditional     |                  |
| Ref./Reg. Provider<br>No.         | Conditional     | Conditional     |                  |
| Master Number                     | Conditional     | Conditional     |                  |
| In-Pat. Admission<br>Date         | Conditional     | Conditional     |                  |
| Ref.Laboratory No.<br>Conditional |                 | Conditional     |                  |
| Manual Review<br>Indicator        | Conditional     | Conditional     |                  |
| Service Location<br>Indicator *   | Conditional     | Conditional     |                  |
| Claim Header-2                    | Not Required    | Mandatory       |                  |
| Registration Number               | Not Required    | Mandatory       |                  |
| Patient Last Name                 | Not Required    | Mandatory       |                  |
| Patient First Name                | Not Required    | Mandatory       |                  |
| Patient Sex                       | Not Required    | Mandatory       |                  |
| Province Code                     | Not Required    | Mandatory       |                  |
| Item                              | Mandatory       | Mandatory       |                  |

<!-- page 016 -->

|                    | Payment Program | Payment Program | Non Patient      |
|--------------------|-----------------|-----------------|------------------|
| Record<br>Field    | HCP / WCB       | RMB             | Encounter Claims |
| Service Code       | Mandatory       | Mandatory       |                  |
| Fee Submitted      | Mandatory       | Mandatory       |                  |
| Number of Services | Mandatory       | Mandatory       |                  |
| Service Date       | Mandatory       | Mandatory       |                  |
| Diagnostic Code    | Conditional     | Conditional     |                  |

<sup>\*</sup> Effective April 1, 2006

<!-- page 017 -->

## <span id="page-16-0"></span>**4.7. Electronic Input (EI) Record Layout**

#### **Health Encounter**

#### **Format Legend**

A = Alphabetic

N = Numeric

X = Alphanumeric

D = Date (YYYYMMDD)

S = Spaces

#### **Data Requirements**

M = Mandatory

O = Optional

C = Conditional

N/R = Not Required

#### **Note:**

If a field is 'Not Required' it should be spaces unless otherwise indicated.

All alphabetic characters must be upper-case.

The last 2 digits of all the amount fields are cents (¢¢).

<!-- page 018 -->

### **Batch Header Record – Health Encounter First Record of Every Batch**

| Field Name                         | Field Start<br>Position | Field<br>Length | Format | Data Req | Field Description                                                                                     |
|------------------------------------|-------------------------|-----------------|--------|----------|-------------------------------------------------------------------------------------------------------|
| Transaction<br>Identifier          | 1                       | 2               | A      | M        | 'HE'                                                                                                  |
| Record<br>Identification           | 3                       | 1               | A      | M        | 'B'                                                                                                   |
| Tech Spec<br>Release<br>Identifier | 4                       | 3               | X      | M        | 'V03'                                                                                                 |
| MOH Office<br>Code                 | 7                       | 1               | A or S | N/R*     | (space) a value will be<br>ignored                                                                    |
| Batch                              | 8                       | 12              | N      | M        | 'YYYYMMDD####'                                                                                        |
| Identification                     |                         |                 |        |          | First 8 digits are the<br>Creation Date (the date<br>the input file is<br>created).                   |
|                                    |                         |                 |        |          | Last 4 digits are a<br>sequential number<br>assigned by the Health<br>Care Provider/Billing<br>Agent. |
|                                    |                         |                 |        |          | Service Date on the<br>Item Records cannot<br>be greater than the<br>Creation Date.                   |
| Operator<br>Number                 | 20                      | 6               |        | N/R      | Zero fill                                                                                             |

<!-- page 019 -->

| Field Name                                                                                              | Field Start<br>Position | Field<br>Length | Format | Data Req | Field Description                                                                                                                               |
|---------------------------------------------------------------------------------------------------------|-------------------------|-----------------|--------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Group<br>Number or<br>Laboratory<br>Licence<br>Number or<br>Independent<br>Health<br>Facility<br>Number | 26                      | 4               | X      | M        | A group number<br>registered with the<br>ministry or '0000'<br>(zeros) for a solo<br>Health Care<br>Provider/Private<br>Physiotherapy Facility. |

<sup>\*</sup>N/R = Not required

<!-- page 020 -->

### **Batch Header Record – Health Encounter First Record of Every Batch**

| Field Name                                                                                                                                           | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                              |
|------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|--------------|--------|----------|--------------------------------------------------------------------------------|
| Health Care<br>Provider/<br>Private<br>Physio<br>Facility/<br>Laboratory<br>Director/<br>Independent<br>Health<br>Facility<br>Practitioner<br>Number | 30                      | 6            | N      | M        | A ministry assigned<br>registration number<br>for the Health Care<br>Provider. |
| Specialty                                                                                                                                            | 36                      | 2            | N      | M        | Refer to Specialty<br>Codes                                                    |
| Reserved for<br>MOH Use                                                                                                                              | 38                      | 42           | S      |          | Spaces                                                                         |

**Note:** All claims in a batch must be for the same Health Care Provider. The first record in a batch must be a Batch Header Record. A Batch Header Record must always be followed by a Claim Header-1 Record.

### **Claim Header – 1 Record – Health Encounter**

#### **Required for All Claims**

| Field Name                | Field Start<br>Position | Field Length | Format | Data Req | Field<br>Description |
|---------------------------|-------------------------|--------------|--------|----------|----------------------|
| Transaction<br>Identifier | 1                       | 2            | A      | M        | 'HE'                 |
| Record<br>Identification  | 3                       | 1            | A      | M        | 'H'                  |

<!-- page 021 -->

### **Batch Header Record – Health Encounter First Record of Every Batch**

| Field Name       | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                                                                                                                                                                                    |
|------------------|-------------------------|--------------|--------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Health<br>Number | 4                       | 10           | N or S | M        | Satisfies the Mod<br>10 Check Digit<br>routine (refer to<br>MOD 10 Check<br>Digit).                                                                                                                                                  |
|                  |                         |              |        | N/R      | Not required for<br>RMB Claims and<br>blank for non<br>patient encounter<br>claims.                                                                                                                                                  |
| Version<br>Code  | 14                      | 2            | A or S | M        | Version of health<br>card (can be 1 or<br>2 alpha<br>characters).<br>A one character<br>version code may<br>be left or right<br>justified.<br>Required for HCP<br>claims.<br>Must be present if<br>version code<br>appears on health |
|                  |                         |              |        | N/R      | card.<br>Not required for<br>RMB claims and<br>blank for non<br>patient encounter<br>claims.                                                                                                                                         |

<!-- page 022 -->

| Field Name             | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                                          |
|------------------------|-------------------------|--------------|--------|----------|--------------------------------------------------------------------------------------------|
| Patient's<br>Birthdate | 16                      | 8            | D or S | M        | Required for all<br>claims except<br>must be blank for<br>non-patient<br>encounter claims. |

<!-- page 023 -->

| Field Name                                                        | Field Start<br>Position | Field<br>Length | Format | Data Req | Field Description                                                                                                |
|-------------------------------------------------------------------|-------------------------|-----------------|--------|----------|------------------------------------------------------------------------------------------------------------------|
| Accounting<br>Number                                              | 24                      | 8               | X      | O        | Available for use by<br>the health care<br>provider for claim<br>identification.                                 |
| Payment<br>Program                                                | 32                      | 3               | A      | M        | HCP, WCB or RMB<br>HCP for non-patient<br>encounter claims<br>(refer to Valid<br>Payment/Payee<br>Combinations). |
| Payee                                                             | 35                      | 1               | A      | M        | P (Provider) or S<br>(Patient).<br>P (Provider) for<br>non-patient<br>encounter claims.                          |
| Referring/<br>Requisitioning<br>Health Care<br>Provider<br>Number | 36                      | 6               | N      | C        | A ministry assigned<br>health care provider<br>number.                                                           |

<!-- page 024 -->

| Field Name       | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                                                                                                                                                                                                                                                                |
|------------------|-------------------------|--------------|--------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Master<br>Number | 42                      | 4            | X/N    | C        | A valid Master<br>Number as<br>assigned by the<br>ministry in the<br>current Master<br>Numbering<br>System<br>book.<br>(Fee Schedule<br>Code<br>Relationships).<br>Must be present<br>for C, H and W<br>prefix codes<br>and/or if the<br>Service Location<br>Indicator is HDS,<br>HED, HIP, HOP,<br>HRP, or RTF. |

<!-- page 025 -->

| Field Name                      | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                                                                                                                                                                                                                                                                                                |
|---------------------------------|-------------------------|--------------|--------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| In-Patient<br>Admission<br>Date | 46                      | 8            | D      | C        | If present,<br>Admission Date<br>must be the<br>same as or prior<br>to Service Date<br>(refer to Fee<br>Schedule Code<br>Relationships).<br>Must<br>be present<br>if Service<br>Location<br>Indicator is HIP<br>or RTF and for<br>long-term care<br>facility admission<br>assessment fee<br>codes.<br>Not applicable to<br>laboratory<br>claims. |

| Field Name              | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                    |
|-------------------------|-------------------------|--------------|--------|----------|----------------------------------------------------------------------|
| Referring<br>Laboratory | 54                      | 4            | N      | C        | For laboratory<br>claims if referred.                                |
| Licence<br>Number       |                         |              |        |          | Must be Laboratory<br>Licence Number<br>assigned by the<br>ministry. |

<!-- page 026 -->

| Field Name                    | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                                                                                                                                                                                                                                                  |
|-------------------------------|-------------------------|--------------|--------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Manual<br>Review<br>Indicator | 58                      | 1            | A      | C        | Must be blank or<br>'Y'.<br>A 'Y' brings the<br>claim to the<br>attention of the<br>ministry.<br>Supporting<br>documentation<br>required (e.g.<br>can<br>be used to<br>suppress health<br>services verification<br>letters) (refer to<br>Service Codes<br>Requiring<br>Specialized<br>Submissions) |

<!-- page 027 -->

### **Claim Header – 1 Record – Health Encounter**

#### **Required for All Claims**

| Field Name                       | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                                                                                                                                                                                                                                                                                              |
|----------------------------------|-------------------------|--------------|--------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Service<br>Location<br>Indicator | 59                      | 4            | X or S | C        | Required for hospital diagnostic services and for telemedicine billings.  Must be three alphas and left justified.  Ministry identifier of the location where the insured diagnostic service was provided (refer to Service Location Indicator Codes).  Four numeric characters continue to be acceptable for non-hospital diagnostic service. |

### **Claim Header – 2 Record – Health Encounter**

#### **Required for RMB Claims Only**

| Field Name             | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                 |
|------------------------|-------------------------|--------------|--------|----------|---------------------------------------------------|
| Reserved for OOC       | 63                      | 11           | S      |          | Must be spaces unless authorized by the ministry. |
| Reserved for MOH Use   | 74                      | 6            | S      |          | Must be spaces.                                   |
| Transaction Identifier | 1                       | 2            | A      | M        | 'HE'                                              |

<!-- page 028 -->



<!-- page 029 -->

| Field Name               | Field Start<br>Position | Field Length | Format | Data Req | Field<br>Description                                                                                                                                    |
|--------------------------|-------------------------|--------------|--------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| Record<br>Identification | 3                       | 1            | A      | M        | 'R'                                                                                                                                                     |
| Registration<br>Number   | 4                       | 12           | X      | M        | Registration<br>numbers less<br>than 12 digits<br>must be left<br>justified and<br>blank filled<br>(refer<br>to<br>Province and<br>Territory<br>Codes). |
| Patient's<br>Last Name   | 16                      | 9            | A      | M        | Special<br>characters not<br>accepted (e.g.<br>quotes,<br>hyphens,<br>imbedded<br>spaces).<br>Left justified.                                           |
|                          |                         |              |        |          | From health<br>card.                                                                                                                                    |
| Patient's<br>First Name  | 25                      | 5            | A      | M        | Special<br>characters not<br>accepted (e.g.<br>quotes,<br>hyphens,<br>imbedded<br>spaces).                                                              |
|                          |                         |              |        |          | Left justified.                                                                                                                                         |
|                          |                         |              |        |          | From health<br>card or from<br>patient.                                                                                                                 |

<!-- page 030 -->

| Field Name       | Field Start<br>Position | Field Length | Format | Data Req | Field<br>Description               |
|------------------|-------------------------|--------------|--------|----------|------------------------------------|
| Patient's<br>Sex | 30                      | 1            | N      | M        | '1' for Male or<br>'2' for Female. |

<!-- page 031 -->

| Field Name                 | Field Start<br>Position | Field Length | Format | Data Req | Field<br>Description                             |
|----------------------------|-------------------------|--------------|--------|----------|--------------------------------------------------|
| Province<br>Code           | 31                      | 2            | A      | M        | (refer to<br>Province<br>Codes and<br>Numbering) |
| Reserved<br>for<br>MOH Use | 33                      | 47           | S      |          | Must be<br>spaces                                |

### **Item Record – Health Encounter**

#### **Required for All Claims There must be at least one item per claim (Item 1)**

| Field Name                | Field Start<br>Position | Field Length | Format | Data Req | Field<br>Description |
|---------------------------|-------------------------|--------------|--------|----------|----------------------|
| Transaction<br>Identifier | 1                       | 2            | A      | M        | 'HE'                 |
| Record<br>Identification  | 3                       | 1            | A      | M        | 'T'                  |

<!-- page 032 -->

### **Item Record – Health Encounter Required for All Claims There must be at least one item per claim (Item 1)**

#### **Item 1**

| Field Name              | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                                                                                                                                         |
|-------------------------|-------------------------|--------------|--------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Service<br>Code         | 4                       | 5            | X      | M        | Present for all claims<br>in the format<br>'ANNNA'.<br>Prefix must be alpha,<br>except I, O, or U.                                                                                        |
|                         |                         |              |        |          | 'NNN' must be<br>numeric.                                                                                                                                                                 |
|                         |                         |              |        |          | Suffix must be A, B,<br>or C.                                                                                                                                                             |
|                         |                         |              |        |          | For Laboratory<br>Claims, Prefix must<br>be L, Suffix must be<br>A.                                                                                                                       |
|                         |                         |              |        |          | 'NNN' must not be<br>700 if Referring<br>Laboratory Licence<br>Number is present<br>(refer to OHIP<br>Schedule of Benefits<br>and Fees).                                                  |
| Reserved for<br>MOH Use | 9                       | 2            | S      |          | Must be spaces                                                                                                                                                                            |
| Fee<br>Submitted        | 11                      | 6            | N      | M        | Required for all<br>claims except<br>laboratory claims.<br>Must be in the range<br>000000 to 500000<br>(\$\$\$\$cc).<br>Fee submitted must<br>be a multiple of the<br>Number of Services. |

<!-- page 033 -->

| Field Name            | Field Start<br>Position | Field Length | Format | Data Req | Field Description                                                          |
|-----------------------|-------------------------|--------------|--------|----------|----------------------------------------------------------------------------|
| Number of<br>Services | 17                      | 2            | N      | M        | Within the range 01<br>to 99.<br>Must divide into Fee<br>Submitted evenly. |

<!-- page 034 -->

### **Item Record – Health Encounter Required for All Claims There must be at least one item per claim (Item 1)**

| Field Name                 | Field Start<br>Position | Field Length | Format | Data Req | Field<br>Description                                                                                                                                                                                                                    |
|----------------------------|-------------------------|--------------|--------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Service<br>Date            | 19                      | 8            | D      | M        | Less than or<br>equal to the<br>Creation<br>Date (Batch<br>Identification<br>field in Batch<br>Header).                                                                                                                                 |
| Diagnostic<br>Code         | 27                      | 4            | X      | C        | If required,<br>must be a<br>valid<br>Diagnostic<br>Code (refer<br>to<br>Services<br>Requiring<br>Diagnostic<br>Codes).<br>Left justify if<br>3 digit<br>diagnostic<br>code is<br>used.<br>Not required<br>for<br>laboratory<br>claims. |
| Reserved<br>for OOC        | 31                      | 10           | S      |          | Must be<br>spaces<br>unless<br>authorized<br>by ministry.                                                                                                                                                                               |
| Reserved<br>for MOH<br>Use | 41                      | 1            |        |          | Must be<br>spaces.                                                                                                                                                                                                                      |

<!-- page 035 -->

### **Item Record – Health Encounter Required for All Claims There must be at least one item per claim (Item 1)**

#### **Item 2 – Optional**

| Field Name                 | Field Start<br>Position | Field Length | Format | Data Req | Field<br>Description |
|----------------------------|-------------------------|--------------|--------|----------|----------------------|
| Service<br>Code            | 42                      | 5            | A      | M        |                      |
| Reserved<br>for<br>MOH Use | 47                      | 2            | S      |          |                      |
| Fee<br>Submitted           | 49                      | 6            | N      | M        |                      |
| Number of<br>Services      | 55                      | 2            | N      | M        |                      |
| Service<br>Date            | 57                      | 8            | D      | M        |                      |
| Diagnostic<br>Code         | 65                      | 4            | X      | C        |                      |
| Reserved<br>for OOC        | 69                      | 10           | S      |          |                      |
| Reserved<br>for<br>MOH Use | 79                      | 1            | S      |          |                      |

**Note:** Field Descriptions are the same as listed under Item 1.

All fields must be spaces if this optional Item 2 is not used.

<!-- page 036 -->

### **Batch Trailer Record – Health Encounter Last Record of Every Batch**

| Field Name                | Field Start<br>Position | Field Length | Format | Data Req | Field<br>Description                                                                     |
|---------------------------|-------------------------|--------------|--------|----------|------------------------------------------------------------------------------------------|
| Transaction<br>Identifier | 1                       | 2            | A      | M        | 'HE'                                                                                     |
| Record<br>Identification  | 3                       | 1            | A      | M        | 'E'                                                                                      |
| H Count                   | 4                       | 4            | N      | M        | Right justified<br>with leading<br>zeros                                                 |
|                           |                         |              |        |          | Total of Claim<br>Header –<br>1<br>Records within<br>the batch                           |
| R Count                   | 8                       | 4            | N      | M        | Right justified<br>with leading<br>zeros                                                 |
|                           |                         |              |        |          | Total of Claim<br>Header –<br>2<br>Records within<br>the batch                           |
| T Count                   | 12                      | 5            | N      | M        | Right justified<br>with leading<br>zeros<br>Total of Item<br>Records within<br>the batch |
| Reserved for<br>MOH Use   | 17                      | 63           | S      |          | Must be<br>spaces                                                                        |

<!-- page 037 -->

## **4.8. Specialty Codes**

[Find a full list of Specialty Codes by visiting our website](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/specialty_codes.pdf)

## <span id="page-36-0"></span>**4.9. Services Requiring Diagnostic Codes**

| Fee Schedule Codes                                                                                                                                                                                                                 | Exceptions                                                                                                         |  |  |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|--|--|
| A—A                                                                                                                                                                                                                                | A330A, A331A, A332A, A335A, A338A,<br>A585A, A960A, A962A, A963A, A964A,<br>A990A, A994A, A996A, A998A             |  |  |
| B—A                                                                                                                                                                                                                                | B400                                                                                                               |  |  |
| C—A                                                                                                                                                                                                                                | C101A -<br>C110A, C330A, C332A, C335A,<br>C585A, C903A, C960A<br>-<br>C964A, C986A,<br>C987A, C989A -<br>C997A     |  |  |
| D—A                                                                                                                                                                                                                                |                                                                                                                    |  |  |
| E077A, E078A, E102A -<br>E359A,<br>E687A, E985A                                                                                                                                                                                    |                                                                                                                    |  |  |
| F—A                                                                                                                                                                                                                                |                                                                                                                    |  |  |
| G390A, G391A, G395A, G400A<br>-<br>G402A,G405A -<br>G407A, G423A,<br>G424A, G460A, G461A, G521A -<br>G523A, G557A -<br>G559A, G600A -<br>G602A, G610A, G611A, G620A,<br>G621A, G800A -<br>G805A, G814A,<br>G870A -<br>G875A, G880A |                                                                                                                    |  |  |
| H—A                                                                                                                                                                                                                                | H001A, H007A, H112A, H113A, H261A,<br>H267A, H350A -<br>H355A, H400A –<br>H408A,<br>H960A –<br>H964A, H980A -H989A |  |  |

<!-- page 038 -->

| Fee Schedule Codes                                                                                                                                            | Exceptions                                                                                                                                               |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| K—A                                                                                                                                                           | K017A, K018A, K021A, K035A, K036A,<br>K038A, K050A -<br>K056A, K061A, K112A,<br>K130A<br>-<br>K132A, K267A, K269A, K960A<br>-<br>K964A, K990A -<br>K999A |
| M—A                                                                                                                                                           |                                                                                                                                                          |
| N—A                                                                                                                                                           |                                                                                                                                                          |
| P—A                                                                                                                                                           | P001A -<br>P008A, P016A, P018A, P020A,<br>P025A, P030A, P041A, P042A                                                                                     |
| For groups G000 -<br>G999, GA00 -<br>GA99,<br>GB00 -<br>GB99 and service dates on/after<br>July 1, 2011 only: Q601A –<br>Q604A,<br>Q619A, Q620A, Q628A, Q629A |                                                                                                                                                          |
| R—A                                                                                                                                                           |                                                                                                                                                          |
| S—A                                                                                                                                                           |                                                                                                                                                          |
| T100A -<br>T999A                                                                                                                                              |                                                                                                                                                          |
| U021A, U023A, U025A, U026A, U231A,<br>U233A, U235A, U236A                                                                                                     |                                                                                                                                                          |
| V302A -<br>V305A, V404A<br>-<br>V409A,<br>V450A, V451A, **V829A, V842A -<br>V850A                                                                             |                                                                                                                                                          |
| W—A                                                                                                                                                           | W010A, W109A, W239A, W269A, W279A,<br>W419A, W903A, W960A –<br>W964A,<br>W990A –<br>W997A, W998A, W999A                                                  |
| Z—A                                                                                                                                                           |                                                                                                                                                          |

<sup>\*\*</sup> These ranges require valid physiotherapy diagnostic codes. Diagnostic Codes are detailed on [Diagnostic Codes](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/diagnostic_codes.pdf)

<!-- page 039 -->

## <span id="page-38-0"></span>**4.10. Fee Schedule Code Relationships**

#### **Summary**

The following requirement(s) must be present for the type(s) of services outlined below:

| Type of Service                                                                                                                                                                                                                                                                             | Requirement                                |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| All consultations, repeat<br>consultations and limited<br>consultations rendered in any<br>location.                                                                                                                                                                                        | Referring Physician or NP<br>Provider No.  |
| All non-emergency hospital in<br>patient services except<br>consultations, repeat<br>consultations and limited<br>consultations.                                                                                                                                                            | Master Number<br>In-Patient Admission Date |
| All consultations in hospital,                                                                                                                                                                                                                                                              | Master Number                              |
| repeat consultations and limited<br>consultations.                                                                                                                                                                                                                                          | Referring Physicain or NP Provider<br>No.  |
| All long-term institutional care,<br>emergency department visits,<br>neo-natal care, respiratory care,<br>low birth weight baby care and<br>attendance at maternal delivery<br>for the care of a high-risk baby.<br>All claims for Group<br>Psychotherapy for<br>In-Patients of a Hospital. | Master Number                              |
| All special-visit premiums to the<br>Out-Patient Emergency<br>Department.<br>All special visit premiums to<br>long-term institutional care.                                                                                                                                                 | Master Number                              |
| All special-visit premiums to a                                                                                                                                                                                                                                                             | Master Number                              |
| hospital in-patient.                                                                                                                                                                                                                                                                        | In-Patient Admission Date                  |
| All dental services.                                                                                                                                                                                                                                                                        | Master Number                              |

<!-- page 040 -->

| Type of Service                                                                                                                                     | Requirement                                       |  |
|-----------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|--|
| All physiotherapy services.                                                                                                                         | Referring Physician or NP Provider<br>No          |  |
| All claims for Laboratory<br>Services, X-rays and other<br>diagnostic procedures rendered<br>in a hospital or a health facility<br>(including IHF). | Referring/Requisitioning Health Care Provider No. |  |
| All claims for Laboratory<br>Services referred from one<br>laboratory to another.                                                                   | Referring Laboratory Licence No.                  |  |
| Midwife Requested<br>Assessments and Optometrist<br>Requested Assessments                                                                           | Referring Midwife or Optometrist Provider No      |  |

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| A005A                | Yes                                                            | No            | No                           |
| A006A                | Yes                                                            | No            | No                           |
| A015A                | Yes                                                            | No            | No                           |
| A016A                | Yes                                                            | No            | No                           |
| A020A                | Yes                                                            | No            | No                           |
| A025A                | Yes                                                            | No            | No                           |
| A026A                | Yes                                                            | No            | No                           |
| A035A                | Yes                                                            | No            | No                           |
| A036A                | Yes                                                            | No            | No                           |
| A045A                | Yes                                                            | No            | No                           |
| A046A                | Yes                                                            | No            | No                           |
| A050A                | Yes                                                            | No            | No                           |
| A055A                | Yes                                                            | No            | No                           |
| A065A                | Yes                                                            | No            | No                           |
| A066A                | Yes                                                            | No            | No                           |
| A070A                | Yes                                                            | No            | No                           |
| A075A                | Yes                                                            | No            | No                           |
| A076A                | Yes                                                            | No            | No                           |
| A085A                | Yes                                                            | No            | No                           |
| A086A                | Yes                                                            | No            | No                           |
| A095A                | Yes                                                            | No            | No                           |

<!-- page 041 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| A096A                | Yes                                                            | No            | No                           |
| A100A                | No                                                             | Yes           | No                           |
| A115A                | Yes                                                            | No            | No                           |
| A135A                | Yes                                                            | No            | No                           |
| A136A                | Yes                                                            | No            | No                           |
| A145A                | Yes                                                            | No            | No                           |
| A150A                | Yes                                                            | No            | No                           |
| A155A                | Yes                                                            | No            | No                           |
| A156A                | Yes                                                            | No            | No                           |
| A160A                | Yes                                                            | No            | No                           |
| A165A                | Yes                                                            | No            | No                           |
| A166A                | Yes                                                            | No            | No                           |
| A175A                | Yes                                                            | No            | No                           |
| A176A                | Yes                                                            | No            | No                           |
| A180A                | Yes                                                            | No            | No                           |
| A185A                | Yes                                                            | No            | No                           |
| A186A                | Yes                                                            | No            | No                           |
| A190A                | Yes                                                            | No            | No                           |
| A191A                | Yes                                                            | No            | No                           |
| A192A                | Yes                                                            | No            | No                           |
| A195A                | Yes                                                            | No            | No                           |
| A196A                | Yes                                                            | No            | No                           |
| A197A                | Yes                                                            | No            | No                           |
| A198A                | Yes                                                            | No            | No                           |
| A205A                | Yes                                                            | No            | No                           |
| A206A                | Yes                                                            | No            | No                           |
| A215A                | Yes                                                            | No            | No                           |
| A220A                | Yes                                                            | No            | No                           |
| A225A                | Yes                                                            | No            | No                           |
| A226A                | Yes                                                            | No            | No                           |
| A235A                | Yes                                                            | No            | No                           |
| A236A                | Yes                                                            | No            | No                           |
| A250A                | Yes                                                            | No            | No                           |
| A253A                | Yes                                                            | No            | No                           |
| A256A                | Yes                                                            | No            | No                           |
| A265A                | Yes                                                            | No            | No                           |
| A266A                | Yes                                                            | No            | No                           |
| A275A                | Yes                                                            | No            | No                           |

<!-- page 042 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| A285A                | Yes                                                            | No            | No                           |
| A286A                | Yes                                                            | No            | No                           |
| A315A                | Yes                                                            | No            | No                           |
| A316A                | Yes                                                            | No            | No                           |
| A325A                | Yes                                                            | No            | No                           |
| A330A                | Yes                                                            | No            | No                           |
| A332A                | Yes                                                            | No            | No                           |
| A335A                | Yes                                                            | No            | No                           |
| A345A                | Yes                                                            | No            | No                           |
| A346A                | Yes                                                            | No            | No                           |
| A355A                | Yes                                                            | No            | No                           |
| A356A                | Yes                                                            | No            | No                           |
| A365A                | Yes                                                            | No            | No                           |
| A375A                | Yes                                                            | No            | No                           |
| A385A                | Yes                                                            | No            | No                           |
| A395A                | Yes                                                            | No            | No                           |
| A400A                | Yes                                                            | No            | No                           |
| A405A                | Yes                                                            | No            | No                           |
| A415A                | Yes                                                            | No            | No                           |
| A416A                | Yes                                                            | No            | No                           |
| A425A                | Yes                                                            | No            | No                           |
| A435A                | Yes                                                            | No            | No                           |
| A445A                | Yes                                                            | No            | No                           |
| A446A                | Yes                                                            | No            | No                           |
| A460A                | Yes                                                            | No            | No                           |
| A465A                | Yes                                                            | No            | No                           |
| A466A                | Yes                                                            | No            | No                           |
| A470A                | Yes                                                            | No            | No                           |
| A475A                | Yes                                                            | No            | No                           |
| A476A                | Yes                                                            | No            | No                           |
| A480A                | Yes                                                            | No            | No                           |
| A485A                | Yes                                                            | No            | No                           |
| A486A                | Yes                                                            | No            | No                           |
| A515A                | Yes                                                            | No            | No                           |
| A525A                | Yes                                                            | No            | No                           |
| A545A                | Yes                                                            | No            | No                           |
| A565A                | Yes                                                            | No            | No                           |
| A575A                | Yes                                                            | No            | No                           |

<!-- page 043 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| A585A                | Yes                                                            | No            | No                           |
| A586A                | Yes                                                            | No            | No                           |
| A595A                | Yes                                                            | No            | No                           |
| A600A                | Yes                                                            | No            | No                           |
| A605A                | Yes                                                            | No            | No                           |
| A606A                | Yes                                                            | No            | No                           |
| A615A                | Yes                                                            | No            | No                           |
| A616A                | Yes                                                            | No            | No                           |
| A625A                | Yes                                                            | No            | No                           |
| A626A                | Yes                                                            | No            | No                           |
| A635A                | Yes                                                            | No            | No                           |
| A636A                | Yes                                                            | No            | No                           |
| A645A                | Yes                                                            | No            | No                           |
| A646A                | Yes                                                            | No            | No                           |
| A655A                | Yes                                                            | No            | No                           |
| A665A                | Yes                                                            | No            | No                           |
| A667A                | Yes                                                            | No            | No                           |
| A675A                | Yes                                                            | No            | No                           |
| A695A                | Yes                                                            | No            | No                           |
| A735A                | Yes                                                            | No            | No                           |
| A745A                | Yes                                                            | No            | No                           |
| A765A                | Yes                                                            | No            | No                           |
| A770A                | Yes                                                            | No            | No                           |
| A775A                | Yes                                                            | No            | No                           |
| A795A                | Yes                                                            | No            | No                           |
| A800A                | Yes                                                            | No            | No                           |
| A801A                | Yes                                                            | No            | No                           |
| A802A                | Yes                                                            | No            | No                           |
| A813A                | Yes                                                            | No            | No                           |
| A815A                | Yes                                                            | No            | No                           |
| A816A                | Yes                                                            | No            | No                           |
| A835A                | Yes                                                            | No            | No                           |
| A845A                | Yes                                                            | No            | No                           |
| A865A                | Yes                                                            | No            | No                           |
| A895A                | Yes                                                            | No            | No                           |
| A905A                | Yes                                                            | No            | No                           |
| A911A                | Yes                                                            | No            | No                           |
| A912A                | Yes                                                            | No            | No                           |

<!-- page 044 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| A933A                | No                                                             | Yes           | Yes                          |
| A935A                | Yes                                                            | No            | No                           |
| A945A                | Yes                                                            | No            | No                           |
| C002A                | No                                                             | Yes           | Yes                          |
| C003A                | No                                                             | Yes           | Yes                          |
| C004A                | No                                                             | Yes           | Yes                          |
| C005A                | Yes                                                            | Yes           | No                           |
| C006A                | Yes                                                            | Yes           | No                           |
| C007A                | No                                                             | Yes           | Yes                          |
| C008A                | No                                                             | Yes           | Yes                          |
| C009A                | No                                                             | Yes           | Yes                          |
| C010A                | No                                                             | Yes           | Yes                          |
| C012A                | No                                                             | Yes           | Yes                          |
| C013A                | No                                                             | Yes           | Yes                          |
| C014A                | No                                                             | Yes           | Yes                          |
| C015A                | Yes                                                            | Yes           | No                           |
| C016A                | Yes                                                            | Yes           | No                           |
| C017A                | No                                                             | Yes           | Yes                          |
| C018A                | No                                                             | Yes           | Yes                          |
| C019A                | No                                                             | Yes           | Yes                          |
| C020A                | No                                                             | Yes           | Yes                          |
| C022A                | No                                                             | Yes           | Yes                          |
| C023A                | No                                                             | Yes           | Yes                          |
| C024A                | No                                                             | Yes           | Yes                          |
| C025A                | Yes                                                            | Yes           | No                           |
| C026A                | Yes                                                            | Yes           | No                           |
| C027A                | No                                                             | Yes           | Yes                          |
| C028A                | No                                                             | Yes           | Yes                          |
| C029A                | No                                                             | Yes           | Yes                          |
| C032A                | No                                                             | Yes           | Yes                          |
| C033A                | No                                                             | Yes           | Yes                          |
| C034A                | No                                                             | Yes           | Yes                          |
| C035A                | Yes                                                            | Yes           | No                           |
| C036A                | Yes                                                            | Yes           | No                           |
| C037A                | No                                                             | Yes           | Yes                          |
| C038A                | No                                                             | Yes           | Yes                          |
| C039A                | No                                                             | Yes           | Yes                          |
| C042A                | No                                                             | Yes           | Yes                          |

<!-- page 045 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C043A                | No                                                             | Yes           | Yes                          |
| C044A                | No                                                             | Yes           | Yes                          |
| C045A                | Yes                                                            | Yes           | No                           |
| C046A                | Yes                                                            | Yes           | No                           |
| C047A                | No                                                             | Yes           | Yes                          |
| C048A                | No                                                             | Yes           | Yes                          |
| C049A                | No                                                             | Yes           | Yes                          |
| C050A                | Yes                                                            | Yes           | No                           |
| C051A                | No                                                             | Yes           | Yes                          |
| C052A                | No                                                             | Yes           | Yes                          |
| C053A                | No                                                             | Yes           | Yes                          |
| C054A                | No                                                             | Yes           | Yes                          |
| C055A                | Yes                                                            | Yes           | Yes                          |
| C056A                | Yes                                                            | Yes           | No                           |
| C057A                | No                                                             | Yes           | Yes                          |
| C058A                | No                                                             | Yes           | Yes                          |
| C059A                | No                                                             | Yes           | Yes                          |
| C062A                | No                                                             | Yes           | Yes                          |
| C063A                | No                                                             | Yes           | Yes                          |
| C064A                | No                                                             | Yes           | Yes                          |
| C065A                | Yes                                                            | Yes           | No                           |
| C066A                | Yes                                                            | Yes           | No                           |
| C067A                | No                                                             | Yes           | Yes                          |
| C068A                | No                                                             | Yes           | Yes                          |
| C069A                | No                                                             | Yes           | Yes                          |
| C071A                | No                                                             | Yes           | Yes                          |
| C072A                | No                                                             | Yes           | Yes                          |
| C073A                | No                                                             | Yes           | Yes                          |
| C074A                | No                                                             | Yes           | Yes                          |
| C075A                | Yes                                                            | Yes           | No                           |
| C076A                | Yes                                                            | Yes           | No                           |
| C077A                | No                                                             | Yes           | Yes                          |
| C078A                | No                                                             | Yes           | Yes                          |
| C079A                | No                                                             | Yes           | Yes                          |
| C082A                | No                                                             | Yes           | Yes                          |
| C083A                | No                                                             | Yes           | Yes                          |
| C084A                | No                                                             | Yes           | Yes                          |
| C085A                | Yes                                                            | Yes           | No                           |

<!-- page 046 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C086A                | Yes                                                            | Yes           | No                           |
| C087A                | No                                                             | Yes           | Yes                          |
| C088A                | No                                                             | Yes           | Yes                          |
| C089A                | No                                                             | Yes           | Yes                          |
| C092A                | No                                                             | Yes           | Yes                          |
| C093A                | No                                                             | Yes           | Yes                          |
| C094A                | No                                                             | Yes           | Yes                          |
| C095A                | Yes                                                            | Yes           | No                           |
| C096A                | Yes                                                            | Yes           | No                           |
| C097A                | Yes                                                            | Yes           | No                           |
| C098A                | No                                                             | Yes           | Yes                          |
| C099A                | No                                                             | Yes           | Yes                          |
| C101A                | No                                                             | Yes           | No                           |
| C102A                | No                                                             | Yes           | No                           |
| C103A                | No                                                             | Yes           | No                           |
| C104A                | No                                                             | Yes           | No                           |
| C105A                | No                                                             | Yes           | No                           |
| C106A                | No                                                             | Yes           | No                           |
| C107A                | No                                                             | Yes           | No                           |
| C108A                | No                                                             | Yes           | No                           |
| C109A                | No                                                             | Yes           | No                           |
| C110A                | No                                                             | Yes           | No                           |
| C113A                | No                                                             | No            | Yes                          |
| C121A                | No                                                             | Yes           | Yes                          |
| C122A                | No                                                             | Yes           | Yes                          |
| C123A                | No                                                             | Yes           | Yes                          |
| C124A                | No                                                             | Yes           | Yes                          |
| C130A                | Yes                                                            | Yes           | No                           |
| C131A                | No                                                             | Yes           | Yes                          |
| C132A                | No                                                             | Yes           | Yes                          |
| C133A                | No                                                             | Yes           | Yes                          |
| C134A                | No                                                             | Yes           | Yes                          |
| C135A                | Yes                                                            | Yes           | No                           |
| C136A                | Yes                                                            | Yes           | No                           |
| C137A                | No                                                             | Yes           | Yes                          |
| C138A                | No                                                             | Yes           | Yes                          |
| C139A                | No                                                             | Yes           | Yes                          |
| C142A                | No                                                             | Yes           | Yes                          |

<!-- page 047 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C143A                | No                                                             | Yes           | Yes                          |
| C150A                | Yes                                                            | Yes           | No                           |
| C151A                | No                                                             | Yes           | Yes                          |
| C152A                | No                                                             | Yes           | Yes                          |
| C153A                | No                                                             | Yes           | Yes                          |
| C154A                | No                                                             | Yes           | Yes                          |
| C155A                | Yes                                                            | Yes           | No                           |
| C156A                | Yes                                                            | Yes           | No                           |
| C157A                | No                                                             | Yes           | Yes                          |
| C158A                | No                                                             | Yes           | Yes                          |
| C159A                | No                                                             | Yes           | Yes                          |
| C160A                | Yes                                                            | Yes           | No                           |
| C161A                | No                                                             | Yes           | Yes                          |
| C162A                | No                                                             | Yes           | Yes                          |
| C163A                | No                                                             | Yes           | Yes                          |
| C164A                | No                                                             | Yes           | Yes                          |
| C165A                | Yes                                                            | Yes           | No                           |
| C166A                | Yes                                                            | Yes           | No                           |
| C167A                | No                                                             | Yes           | Yes                          |
| C168A                | No                                                             | Yes           | Yes                          |
| C169A                | No                                                             | Yes           | Yes                          |
| C172A                | No                                                             | Yes           | Yes                          |
| C173A                | No                                                             | Yes           | Yes                          |
| C174A                | No                                                             | Yes           | No                           |
| C175A                | Yes                                                            | Yes           | No                           |
| C176A                | Yes                                                            | Yes           | No                           |
| C177A                | No                                                             | Yes           | Yes                          |
| C178A                | No                                                             | Yes           | Yes                          |
| C179A                | No                                                             | Yes           | Yes                          |
| C180A                | Yes                                                            | Yes           | No                           |
| C181A                | No                                                             | Yes           | Yes                          |
| C182A                | No                                                             | Yes           | Yes                          |
| C183A                | No                                                             | Yes           | Yes                          |
| C184A                | No                                                             | Yes           | Yes                          |
| C185A                | Yes                                                            | Yes           | No                           |
| C186A                | Yes                                                            | Yes           | No                           |
| C187A                | No                                                             | Yes           | Yes                          |
| C188A                | No                                                             | Yes           | Yes                          |

<!-- page 048 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C189A                | No                                                             | Yes           | Yes                          |
| C190A                | Yes                                                            | Yes           | No                           |
| C192A                | No                                                             | Yes           | Yes                          |
| C193A                | No                                                             | Yes           | Yes                          |
| C194A                | No                                                             | Yes           | Yes                          |
| C195A                | Yes                                                            | Yes           | No                           |
| C196A                | Yes                                                            | Yes           | No                           |
| C197A                | No                                                             | Yes           | Yes                          |
| C198A                | No                                                             | Yes           | Yes                          |
| C199A                | No                                                             | Yes           | Yes                          |
| C202A                | No                                                             | Yes           | Yes                          |
| C203A                | No                                                             | Yes           | Yes                          |
| C204A                | No                                                             | Yes           | Yes                          |
| C205A                | Yes                                                            | Yes           | No                           |
| C206A                | Yes                                                            | Yes           | No                           |
| C207A                | No                                                             | Yes           | Yes                          |
| C208A                | No                                                             | Yes           | Yes                          |
| C209A                | No                                                             | Yes           | Yes                          |
| C215A                | Yes                                                            | Yes           | Yes                          |
| C220A                | Yes                                                            | Yes           | No                           |
| C222A                | No                                                             | Yes           | Yes                          |
| C223A                | Yes                                                            | Yes           | Yes                          |
| C225A                | Yes                                                            | Yes           | No                           |
| C226A                | Yes                                                            | Yes           | No                           |
| C227A                | No                                                             | Yes           | Yes                          |
| C229A                | No                                                             | Yes           | Yes                          |
| C231A                | Yes                                                            | Yes           | No                           |
| C232A                | No                                                             | Yes           | Yes                          |
| C233A                | No                                                             | Yes           | Yes                          |
| C234A                | No                                                             | Yes           | Yes                          |
| C235A                | Yes                                                            | Yes           | No                           |
| C236A                | Yes                                                            | Yes           | No                           |
| C237A                | No                                                             | Yes           | Yes                          |
| C238A                | No                                                             | Yes           | Yes                          |
| C239A                | No                                                             | Yes           | Yes                          |
| C242A                | No                                                             | Yes           | Yes                          |
| C243A                | No                                                             | Yes           | Yes                          |
| C244A                | No                                                             | Yes           | Yes                          |

<!-- page 049 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C245A                | Yes                                                            | Yes           | No                           |
| C246A                | Yes                                                            | Yes           | No                           |
| C247A                | No                                                             | Yes           | Yes                          |
| C248A                | No                                                             | Yes           | Yes                          |
| C249A                | No                                                             | Yes           | Yes                          |
| C250A                | No                                                             | Yes           | Yes                          |
| C255A                | Yes                                                            | Yes           | No                           |
| C260A                | Yes                                                            | Yes           | No                           |
| C262A                | No                                                             | Yes           | Yes                          |
| C263A                | No                                                             | Yes           | Yes                          |
| C264A                | No                                                             | Yes           | Yes                          |
| C265A                | Yes                                                            | Yes           | No                           |
| C266A                | Yes                                                            | Yes           | No                           |
| C268A                | No                                                             | Yes           | Yes                          |
| C275A                | Yes                                                            | Yes           | No                           |
| C283A                | No                                                             | Yes           | Yes                          |
| C285A                | Yes                                                            | Yes           | No                           |
| C286A                | Yes                                                            | Yes           | No                           |
| C288A                | No                                                             | Yes           | Yes                          |
| C311A                | No                                                             | Yes           | Yes                          |
| C312A                | No                                                             | Yes           | Yes                          |
| C313A                | No                                                             | Yes           | Yes                          |
| C314A                | No                                                             | Yes           | Yes                          |
| C315A                | Yes                                                            | Yes           | No                           |
| C316A                | Yes                                                            | Yes           | No                           |
| C317A                | No                                                             | Yes           | Yes                          |
| C318A                | No                                                             | Yes           | Yes                          |
| C319A                | No                                                             | Yes           | Yes                          |
| C325A                | Yes                                                            | Yes           | No                           |
| C330A                | Yes                                                            | Yes           | No                           |
| C332A                | Yes                                                            | Yes           | No                           |
| C335A                | Yes                                                            | Yes           | No                           |
| C341A                | No                                                             | Yes           | Yes                          |
| C342A                | No                                                             | Yes           | Yes                          |
| C343A                | No                                                             | Yes           | Yes                          |
| C344A                | No                                                             | Yes           | Yes                          |
| C345A                | Yes                                                            | Yes           | No                           |
| C346A                | Yes                                                            | Yes           | No                           |

<!-- page 050 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C347A                | No                                                             | Yes           | Yes                          |
| C348A                | No                                                             | Yes           | Yes                          |
| C349A                | No                                                             | Yes           | Yes                          |
| C352A                | No                                                             | Yes           | Yes                          |
| C353A                | No                                                             | Yes           | Yes                          |
| C354A                | No                                                             | Yes           | Yes                          |
| C355A                | Yes                                                            | Yes           | No                           |
| C356A                | Yes                                                            | Yes           | No                           |
| C357A                | No                                                             | Yes           | Yes                          |
| C358A                | No                                                             | Yes           | Yes                          |
| C359A                | No                                                             | Yes           | Yes                          |
| C365A                | Yes                                                            | Yes           | Yes                          |
| C375A                | Yes                                                            | Yes           | No                           |
| C384A                | Yes                                                            | Yes           | No                           |
| C385A                | Yes                                                            | Yes           | No                           |
| C395A                | Yes                                                            | Yes           | No                           |
| C400A                | Yes                                                            | Yes           | No                           |
| C405A                | Yes                                                            | Yes           | Yes                          |
| C411A                | No                                                             | Yes           | Yes                          |
| C412A                | No                                                             | Yes           | Yes                          |
| C413A                | No                                                             | Yes           | Yes                          |
| C414A                | No                                                             | Yes           | Yes                          |
| C415A                | Yes                                                            | Yes           | No                           |
| C416A                | Yes                                                            | Yes           | No                           |
| C417A                | No                                                             | Yes           | Yes                          |
| C418A                | No                                                             | Yes           | Yes                          |
| C419A                | No                                                             | Yes           | Yes                          |
| C425A                | Yes                                                            | Yes           | Yes                          |
| C435A                | Yes                                                            | Yes           | No                           |
| C441A                | No                                                             | Yes           | Yes                          |
| C442A                | No                                                             | Yes           | Yes                          |
| C443A                | No                                                             | Yes           | Yes                          |
| C444A                | No                                                             | Yes           | Yes                          |
| C445A                | Yes                                                            | Yes           | No                           |
| C446A                | Yes                                                            | Yes           | No                           |
| C447A                | No                                                             | Yes           | Yes                          |
| C448A                | No                                                             | Yes           | Yes                          |
| C449A                | No                                                             | Yes           | Yes                          |

<!-- page 051 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C460A                | Yes                                                            | Yes           | No                           |
| C461A                | No                                                             | Yes           | Yes                          |
| C462A                | No                                                             | Yes           | Yes                          |
| C463A                | No                                                             | Yes           | Yes                          |
| C464A                | No                                                             | Yes           | Yes                          |
| C465A                | Yes                                                            | Yes           | No                           |
| C466A                | Yes                                                            | Yes           | No                           |
| C467A                | No                                                             | Yes           | Yes                          |
| C468A                | No                                                             | Yes           | Yes                          |
| C469A                | No                                                             | Yes           | Yes                          |
| C470A                | Yes                                                            | Yes           | No                           |
| C471A                | No                                                             | Yes           | Yes                          |
| C472A                | No                                                             | Yes           | Yes                          |
| C473A                | No                                                             | Yes           | Yes                          |
| C474A                | No                                                             | Yes           | Yes                          |
| C475A                | Yes                                                            | Yes           | No                           |
| C476A                | Yes                                                            | Yes           | No                           |
| C477A                | No                                                             | Yes           | Yes                          |
| C478A                | No                                                             | Yes           | Yes                          |
| C479A                | No                                                             | Yes           | Yes                          |
| C480A                | No                                                             | Yes           | Yes                          |
| C481A                | No                                                             | Yes           | Yes                          |
| C482A                | No                                                             | Yes           | Yes                          |
| C483A                | No                                                             | Yes           | Yes                          |
| C484A                | No                                                             | Yes           | Yes                          |
| C485A                | Yes                                                            | Yes           | No                           |
| C486A                | Yes                                                            | Yes           | No                           |
| C487A                | No                                                             | Yes           | Yes                          |
| C488A                | No                                                             | Yes           | Yes                          |
| C489A                | No                                                             | Yes           | Yes                          |
| C510A                | No                                                             | Yes           | Yes                          |
| C511A                | No                                                             | Yes           | Yes                          |
| C515A                | Yes                                                            | Yes           | No                           |
| C525A                | Yes                                                            | Yes           | No                           |
| C545A                | Yes                                                            | Yes           | No                           |
| C565A                | Yes                                                            | Yes           | No                           |
| C570A                | No                                                             | Yes           | Yes                          |
| C575A                | Yes                                                            | Yes           | No                           |

<!-- page 052 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C585A                | Yes                                                            | Yes           | No                           |
| C586A                | Yes                                                            | Yes           | Yes                          |
| C590A                | Yes                                                            | Yes           | No                           |
| C595A                | Yes                                                            | Yes           | No                           |
| C600A                | Yes                                                            | Yes           | No                           |
| C601A                | No                                                             | Yes           | Yes                          |
| C602A                | No                                                             | Yes           | Yes                          |
| C603A                | No                                                             | Yes           | Yes                          |
| C604A                | No                                                             | Yes           | Yes                          |
| C605A                | Yes                                                            | Yes           | No                           |
| C606A                | Yes                                                            | Yes           | No                           |
| C607A                | No                                                             | Yes           | Yes                          |
| C608A                | No                                                             | Yes           | Yes                          |
| C609A                | No                                                             | Yes           | Yes                          |
| C611A                | No                                                             | Yes           | Yes                          |
| C612A                | No                                                             | Yes           | Yes                          |
| C613A                | No                                                             | Yes           | Yes                          |
| C614A                | No                                                             | Yes           | Yes                          |
| C615A                | Yes                                                            | Yes           | No                           |
| C616A                | Yes                                                            | Yes           | No                           |
| C617A                | No                                                             | Yes           | Yes                          |
| C618A                | No                                                             | Yes           | Yes                          |
| C619A                | No                                                             | Yes           | Yes                          |
| C621A                | No                                                             | Yes           | Yes                          |
| C622A                | No                                                             | Yes           | Yes                          |
| C623A                | No                                                             | Yes           | Yes                          |
| C624A                | No                                                             | Yes           | Yes                          |
| C625A                | Yes                                                            | Yes           | No                           |
| C626A                | Yes                                                            | Yes           | No                           |
| C627A                | No                                                             | Yes           | Yes                          |
| C628A                | No                                                             | Yes           | Yes                          |
| C629A                | No                                                             | Yes           | Yes                          |
| C635A                | Yes                                                            | Yes           | No                           |
| C636A                | Yes                                                            | Yes           | No                           |
| C642A                | No                                                             | Yes           | Yes                          |
| C643A                | No                                                             | Yes           | Yes                          |
| C644A                | No                                                             | Yes           | Yes                          |
| C645A                | Yes                                                            | Yes           | No                           |

<!-- page 053 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C646A                | Yes                                                            | Yes           | No                           |
| C647A                | No                                                             | Yes           | Yes                          |
| C648A                | No                                                             | Yes           | Yes                          |
| C649A                | No                                                             | Yes           | Yes                          |
| C655A                | Yes                                                            | Yes           | No                           |
| C661A                | No                                                             | Yes           | Yes                          |
| C662A                | Yes                                                            | Yes           | No                           |
| C665A                | Yes                                                            | Yes           | No                           |
| C667A                | Yes                                                            | Yes           | Yes                          |
| C675A                | Yes                                                            | Yes           | No                           |
| C680A                | No                                                             | Yes           | Yes                          |
| C682A                | Yes                                                            | Yes           | No                           |
| C695A                | Yes                                                            | Yes           | Yes                          |
| C735A                | Yes                                                            | Yes           | No                           |
| C745A                | Yes                                                            | Yes           | No                           |
| C760A                | No                                                             | Yes           | Yes                          |
| C765A                | Yes                                                            | Yes           | No                           |
| C770A                | Yes                                                            | Yes           | No                           |
| C771A                | No                                                             | Yes           | Yes                          |
| C775A                | Yes                                                            | Yes           | Yes                          |
| C777A                | No                                                             | Yes           | Yes                          |
| C795A                | Yes                                                            | Yes           | Yes                          |
| C800A                | Yes                                                            | Yes           | Yes                          |
| C801A                | Yes                                                            | Yes           | Yes                          |
| C802A                | Yes                                                            | Yes           | Yes                          |
| C813A                | Yes                                                            | Yes           | Yes                          |
| C815A                | Yes                                                            | Yes           | Yes                          |
| C816A                | Yes                                                            | Yes           | Yes                          |
| C835A                | Yes                                                            | Yes           | Yes                          |
| C845A                | Yes                                                            | Yes           | No                           |
| C865A                | Yes                                                            | Yes           | No                           |
| C882A                | No                                                             | Yes           | Yes                          |
| C895A                | Yes                                                            | Yes           | No                           |
| C903A                | No                                                             | Yes           | Yes                          |
| C904A                | No                                                             | Yes           | Yes                          |
| C905A                | Yes                                                            | Yes           | No                           |
| C911A                | Yes                                                            | Yes           | No                           |
| C912A                | Yes                                                            | Yes           | No                           |

<!-- page 054 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| C933A                | No                                                             | Yes           | Yes                          |
| C935A                | Yes                                                            | Yes           | No                           |
| C945A                | Yes                                                            | Yes           | No                           |
| C960A                | No                                                             | Yes           | No                           |
| C961A                | No                                                             | Yes           | No                           |
| C962A                | No                                                             | Yes           | No                           |
| C963A                | No                                                             | Yes           | No                           |
| C964A                | No                                                             | Yes           | No                           |
| C982A                | No                                                             | Yes           | Yes                          |
| C983B                | No                                                             | Yes           | No                           |
| C985C                | No                                                             | Yes           | No                           |
| C987A                | No                                                             | Yes           | No                           |
| C988B                | No                                                             | Yes           | No                           |
| C989A                | No                                                             | Yes           | No                           |
| C990A                | No                                                             | Yes           | No                           |
| C991A                | No                                                             | Yes           | No                           |
| C992A                | No                                                             | Yes           | No                           |
| C993A                | No                                                             | Yes           | No                           |
| C994A                | No                                                             | Yes           | No                           |
| C995A                | No                                                             | Yes           | No                           |
| C996A                | No                                                             | Yes           | No                           |
| C997A                | No                                                             | Yes           | No                           |
| C998B/C              | No                                                             | Yes           | No                           |
| C999B/C              | No                                                             | Yes           | No                           |
| E032A                | No                                                             | Yes           | No                           |
| E101B                | No                                                             | Yes           | No                           |
| E082A                | No                                                             | Yes           | Yes                          |
| E083A                | No                                                             | Yes           | Yes                          |
| E084A                | No                                                             | Yes           | Yes                          |
| E475A                | No                                                             | Yes           | No                           |
| E515A                | No                                                             | Yes           | No                           |
| E530A                | No                                                             | Yes           | No                           |
| E986A                | No                                                             | Yes           | Yes                          |
| G185A                | No                                                             | Yes           | No                           |
| G254A                | No                                                             | Yes           | Yes                          |
| G400A                | No                                                             | Yes           | No                           |
| G401A                | No                                                             | Yes           | No                           |
| G402A                | No                                                             | Yes           | No                           |

<!-- page 055 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| G405A                | No                                                             | Yes           | No                           |
| G406A                | No                                                             | Yes           | No                           |
| G407A                | No                                                             | Yes           | No                           |
| G408A                | No                                                             | Yes           | No                           |
| G409A                | No                                                             | Yes           | No                           |
| G412A                | No                                                             | Yes           | No                           |
| G557A                | No                                                             | Yes           | No                           |
| G558A                | No                                                             | Yes           | No                           |
| G559A                | No                                                             | Yes           | No                           |
| G600A                | No                                                             | Yes           | Yes                          |
| G601A                | No                                                             | Yes           | Yes                          |
| G602A                | No                                                             | Yes           | Yes                          |
| G603A                | No                                                             | Yes           | Yes                          |
| G604A                | No                                                             | Yes           | Yes                          |
| G610A                | No                                                             | Yes           | No                           |
| G611A                | No                                                             | Yes           | No                           |
| G620A                | No                                                             | Yes           | No                           |
| G621A                | No                                                             | Yes           | No                           |
| G790A                | No                                                             | Yes           | Yes                          |
| G791A                | No                                                             | Yes           | Yes                          |
| G792A                | No                                                             | Yes           | Yes                          |
| H002A                | No                                                             | Yes           | No                           |
| H003A                | No                                                             | Yes           | No                           |
| H007A                | No                                                             | Yes           | No                           |
| H055A                | Yes                                                            | Yes           | No                           |
| H065A                | Yes                                                            | Yes           | No                           |
| H100A                | ?                                                              | Yes           | No                           |
| H101A                | No                                                             | Yes           | No                           |
| H102A                | No                                                             | Yes           | No                           |
| H103A                | No                                                             | Yes           | No                           |
| H104A                | No                                                             | Yes           | No                           |
| H105A                | No                                                             | Yes           | No                           |
| H112A                | No                                                             | Yes           | No                           |
| H113A                | No                                                             | Yes           | No                           |
| H121A                | No                                                             | Yes           | No                           |
| H122A                | No                                                             | Yes           | No                           |
| H123A                | No                                                             | Yes           | No                           |
| H124A                | No                                                             | Yes           | No                           |

<!-- page 056 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| H131A                | No                                                             | Yes           | No                           |
| H132A                | No                                                             | Yes           | No                           |
| H133A                | No                                                             | Yes           | No                           |
| H134A                | No                                                             | Yes           | No                           |
| H151A                | No                                                             | Yes           | No                           |
| H152A                | No                                                             | Yes           | No                           |
| H153A                | No                                                             | Yes           | No                           |
| H154A                | No                                                             | Yes           | No                           |
| H262A                | No                                                             | Yes           | No                           |
| H263A                | No                                                             | Yes           | No                           |
| H267A                | No                                                             | Yes           | No                           |
| H312A                | No                                                             | Yes           | Yes                          |
| H317A                | No                                                             | Yes           | Yes                          |
| H319A                | No                                                             | Yes           | Yes                          |
| H960A                | No                                                             | Yes           | No                           |
| H961A                | No                                                             | Yes           | No                           |
| H962A                | No                                                             | Yes           | No                           |
| H963A                | No                                                             | Yes           | No                           |
| H964A                | No                                                             | Yes           | No                           |
| H980A                | No                                                             | Yes           | No                           |
| H981A                | No                                                             | Yes           | No                           |
| H984A                | No                                                             | Yes           | No                           |
| H985A                | No                                                             | Yes           | No                           |
| H985A                | No                                                             | Yes           | No                           |
| H986A                | No                                                             | Yes           | No                           |
| H987A                | No                                                             | Yes           | No                           |
| H988A                | No                                                             | Yes           | No                           |
| H989A                | No                                                             | Yes           | No                           |
| K061A                | No                                                             | Yes           | No                           |
| K121A                | No                                                             | Yes           | Yes                          |
| K191A                | No                                                             | Yes           | Yes                          |
| K199A                | No                                                             | Yes           | Yes                          |
| K705A                | No                                                             | Yes           | No                           |
| K960A                | No                                                             | Yes           | No                           |
| K961A                | No                                                             | Yes           | No                           |
| K962A                | No                                                             | Yes           | No                           |
| K963A                | No                                                             | Yes           | No                           |
| K964A                | No                                                             | Yes           | No                           |

<!-- page 057 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| K990A                | No                                                             | Yes           | No                           |
| K991A                | No                                                             | Yes           | No                           |
| K992A                | No                                                             | Yes           | No                           |
| K993A                | No                                                             | Yes           | No                           |
| K994A                | No                                                             | Yes           | No                           |
| K995A                | No                                                             | Yes           | No                           |
| K996A                | No                                                             | Yes           | No                           |
| K997A                | No                                                             | Yes           | No                           |
| K998A                | No                                                             | Yes           | No                           |
| K999A                | No                                                             | Yes           | No                           |
| R731A                | No                                                             | Yes           | Yes                          |
| R766A                | No                                                             | Yes           | Yes                          |
| R767A                | No                                                             | Yes           | Yes                          |
| S152A                | No                                                             | Yes           | Yes                          |
| S207A                | No                                                             | Yes           | No                           |
| S752A                | No                                                             | Yes           | No                           |
| S756A                | No                                                             | Yes           | No                           |
| S785A                | No                                                             | Yes           | No                           |
| S900C                | No                                                             | Yes           | No                           |
| TA                   | No                                                             | Yes           | No                           |
| T652A                | No                                                             | Yes           | Yes                          |
| T657A                | No                                                             | Yes           | Yes                          |
| U960A                | No                                                             | Yes           | No                           |
| U961A                | No                                                             | Yes           | No                           |
| U962A                | No                                                             | Yes           | No                           |
| U963A                | No                                                             | Yes           | No                           |
| U964A                | No                                                             | Yes           | No                           |
| U990A                | No                                                             | Yes           | No                           |
| U991A                | No                                                             | Yes           | No                           |
| U992A                | No                                                             | Yes           | No                           |
| U993A                | No                                                             | Yes           | No                           |
| U994A                | No                                                             | Yes           | No                           |
| U995A                | No                                                             | Yes           | No                           |
| U996A                | No                                                             | Yes           | No                           |
| U997A                | No                                                             | Yes           | No                           |
| U998A                | No                                                             | Yes           | No                           |
| U999A                | No                                                             | Yes           | No                           |
| V844A                | Yes                                                            | Yes           | No                           |

<!-- page 058 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| V848A                | Yes                                                            | Yes           | No                           |
| W001A                | No                                                             | Yes           | No                           |
| W002A                | No                                                             | Yes           | No                           |
| W003A                | No                                                             | Yes           | No                           |
| W004A                | No                                                             | Yes           | No                           |
| W008A                | No                                                             | Yes           | No                           |
| W010A                | No                                                             | Yes           | Yes                          |
| W021A                | No                                                             | Yes           | No                           |
| W022A                | No                                                             | Yes           | No                           |
| W023A                | No                                                             | Yes           | No                           |
| W025A                | Yes                                                            | Yes           | No                           |
| W026A                | Yes                                                            | Yes           | No                           |
| W028A                | No                                                             | Yes           | No                           |
| W031A                | No                                                             | Yes           | No                           |
| W032A                | No                                                             | Yes           | No                           |
| W033A                | No                                                             | Yes           | No                           |
| W035A                | Yes                                                            | Yes           | No                           |
| W036A                | Yes                                                            | Yes           | No                           |
| W038A                | No                                                             | Yes           | No                           |
| W045A                | Yes                                                            | Yes           | No                           |
| W046A                | Yes                                                            | Yes           | No                           |
| W055A                | Yes                                                            | Yes           | No                           |
| W058A                | No                                                             | Yes           | No                           |
| W061A                | No                                                             | Yes           | No                           |
| W062A                | No                                                             | Yes           | No                           |
| W063A                | No                                                             | Yes           | No                           |
| W065A                | Yes                                                            | Yes           | No                           |
| W066A                | Yes                                                            | Yes           | No                           |
| W068A                | No                                                             | Yes           | No                           |
| W071A                | No                                                             | Yes           | No                           |
| W072A                | No                                                             | Yes           | No                           |
| W073A                | No                                                             | Yes           | No                           |
| W074A                | No                                                             | Yes           | No                           |
| W075A                | Yes                                                            | Yes           | No                           |
| W076A                | Yes                                                            | Yes           | No                           |
| W078A                | No                                                             | Yes           | No                           |
| W085A                | Yes                                                            | Yes           | No                           |
| W086A                | Yes                                                            | Yes           | No                           |

<!-- page 059 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| W095A                | Yes                                                            | Yes           | No                           |
| W096A                | Yes                                                            | Yes           | No                           |
| W102A                | No                                                             | Yes           | Yes                          |
| W104A                | No                                                             | Yes           | Yes                          |
| W105A                | Yes                                                            | Yes           | No                           |
| W106A                | Yes                                                            | Yes           | No                           |
| W107A                | No                                                             | Yes           | Yes                          |
| W109A                | No                                                             | Yes           | Yes                          |
| W113A                | No                                                             | Yes           | No                           |
| W121A                | No                                                             | Yes           | No                           |
| W130A                | Yes                                                            | Yes           | No                           |
| W131A                | No                                                             | Yes           | No                           |
| W132A                | No                                                             | Yes           | No                           |
| W133A                | No                                                             | Yes           | No                           |
| W134A                | No                                                             | Yes           | No                           |
| W138A                | No                                                             | Yes           | No                           |
| W150A                | Yes                                                            | Yes           | No                           |
| W151A                | No                                                             | Yes           | No                           |
| W152A                | No                                                             | Yes           | No                           |
| W153A                | No                                                             | Yes           | No                           |
| W154A                | No                                                             | Yes           | No                           |
| W155A                | Yes                                                            | Yes           | No                           |
| W156A                | Yes                                                            | Yes           | No                           |
| W158A                | No                                                             | Yes           | No                           |
| W160A                | Yes                                                            | Yes           | No                           |
| W161A                | No                                                             | Yes           | No                           |
| W162A                | No                                                             | Yes           | No                           |
| W163A                | No                                                             | Yes           | No                           |
| W164A                | No                                                             | Yes           | No                           |
| W165A                | Yes                                                            | Yes           | No                           |
| W166A                | Yes                                                            | Yes           | No                           |
| W168A                | No                                                             | Yes           | Yes                          |
| W171A                | No                                                             | Yes           | No                           |
| W172A                | No                                                             | Yes           | No                           |
| W173A                | No                                                             | Yes           | No                           |
| W175A                | Yes                                                            | Yes           | No                           |
| W176A                | Yes                                                            | Yes           | No                           |
| W180A                | Yes                                                            | Yes           | No                           |

<!-- page 060 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| W181A                | No                                                             | Yes           | No                           |
| W182A                | No                                                             | Yes           | No                           |
| W183A                | No                                                             | Yes           | No                           |
| W184A                | No                                                             | Yes           | No                           |
| W185A                | Yes                                                            | Yes           | No                           |
| W186A                | Yes                                                            | Yes           | No                           |
| W188A                | No                                                             | Yes           | No                           |
| W190A                | Yes                                                            | Yes           | No                           |
| W196A                | Yes                                                            | Yes           | No                           |
| W220A                | Yes                                                            | Yes           | No                           |
| W221A                | No                                                             | Yes           | No                           |
| W222A                | No                                                             | Yes           | No                           |
| W223A                | No                                                             | Yes           | No                           |
| W224A                | No                                                             | Yes           | No                           |
| W225A                | Yes                                                            | Yes           | No                           |
| W226A                | Yes                                                            | Yes           | No                           |
| W228A                | No                                                             | Yes           | No                           |
| W231A                | Yes                                                            | Yes           | No                           |
| W232A                | No                                                             | Yes           | Yes                          |
| W234A                | No                                                             | Yes           | Yes                          |
| W235A                | Yes                                                            | Yes           | No                           |
| W236A                | Yes                                                            | Yes           | No                           |
| W237A                | No                                                             | Yes           | Yes                          |
| W239A                | No                                                             | Yes           | No                           |
| W252A                | No                                                             | Yes           | Yes                          |
| W254A                | No                                                             | Yes           | Yes                          |
| W255A                | Yes                                                            | Yes           | No                           |
| W257A                | No                                                             | Yes           | Yes                          |
| W261A                | No                                                             | Yes           | No                           |
| W262A                | No                                                             | Yes           | No                           |
| W265A                | Yes                                                            | Yes           | No                           |
| W266A                | Yes                                                            | Yes           | No                           |
| W269A                | No                                                             | Yes           | No                           |
| W272A                | No                                                             | Yes           | Yes                          |
| W274A                | No                                                             | Yes           | Yes                          |
| W275A                | Yes                                                            | Yes           | No                           |
| W277A                | No                                                             | Yes           | Yes                          |
| W279A                | No                                                             | Yes           | No                           |

<!-- page 061 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| W292A                | No                                                             | Yes           | Yes                          |
| W294A                | No                                                             | Yes           | Yes                          |
| W297A                | No                                                             | Yes           | Yes                          |
| W299A                | No                                                             | Yes           | No                           |
| W305A                | Yes                                                            | Yes           | No                           |
| W306A                | Yes                                                            | Yes           | No                           |
| W310A                | Yes                                                            | Yes           | No                           |
| W311A                | No                                                             | Yes           | No                           |
| W312A                | No                                                             | Yes           | No                           |
| W313A                | No                                                             | Yes           | No                           |
| W314A                | No                                                             | Yes           | No                           |
| W318A                | No                                                             | Yes           | No                           |
| W325A                | Yes                                                            | Yes           | No                           |
| W345A                | Yes                                                            | Yes           | No                           |
| W346A                | Yes                                                            | Yes           | No                           |
| W355A                | Yes                                                            | Yes           | No                           |
| W356A                | Yes                                                            | Yes           | No                           |
| W375A                | Yes                                                            | Yes           | No                           |
| W385A                | Yes                                                            | Yes           | No                           |
| W395A                | Yes                                                            | Yes           | No                           |
| W400A                | Yes                                                            | Yes           | No                           |
| W402A                | No                                                             | Yes           | Yes                          |
| W404A                | No                                                             | Yes           | Yes                          |
| W405A                | Yes                                                            | Yes           | No                           |
| W407A                | No                                                             | Yes           | No                           |
| W409A                | No                                                             | Yes           | No                           |
| W419A                | No                                                             | Yes           | No                           |
| W435A                | Yes                                                            | Yes           | No                           |
| W441A                | No                                                             | Yes           | No                           |
| W442A                | No                                                             | Yes           | Yes                          |
| W443A                | No                                                             | Yes           | Yes                          |
| W444A                | No                                                             | Yes           | No                           |
| W445A                | Yes                                                            | Yes           | No                           |
| W446A                | Yes                                                            | Yes           | No                           |
| W448A                | No                                                             | Yes           | No                           |
| W460A                | Yes                                                            | Yes           | No                           |
| W461A                | No                                                             | Yes           | No                           |
| W462A                | No                                                             | Yes           | No                           |

<!-- page 062 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| W463A                | No                                                             | Yes           | No                           |
| W464A                | No                                                             | Yes           | No                           |
| W465A                | Yes                                                            | Yes           | No                           |
| W466A                | Yes                                                            | Yes           | No                           |
| W468A                | No                                                             | Yes           | No                           |
| W510A                | No                                                             | Yes           | No                           |
| W511A                | No                                                             | Yes           | No                           |
| W512A                | No                                                             | Yes           | Yes                          |
| W514A                | No                                                             | Yes           | Yes                          |
| W515A                | Yes                                                            | Yes           | No                           |
| W516A                | Yes                                                            | Yes           | No                           |
| W517A                | No                                                             | Yes           | Yes                          |
| W535A                | Yes                                                            | Yes           | No                           |
| W536A                | Yes                                                            | Yes           | No                           |
| W562A                | No                                                             | Yes           | Yes                          |
| W564A                | No                                                             | Yes           | Yes                          |
| W565A                | Yes                                                            | Yes           | No                           |
| W567A                | No                                                             | Yes           | Yes                          |
| W645A                | Yes                                                            | Yes           | No                           |
| W646A                | Yes                                                            | Yes           | No                           |
| W662A                | Yes                                                            | Yes           | No                           |
| W667A                | Yes                                                            | Yes           | No                           |
| W682A                | Yes                                                            | Yes           | No                           |
| W695A                | Yes                                                            | Yes           | No                           |
| W760A                | No                                                             | Yes           | No                           |
| W765A                | Yes                                                            | Yes           | No                           |
| W770A                | Yes                                                            | Yes           | No                           |
| W771A                | No                                                             | Yes           | No                           |
| W775A                | Yes                                                            | Yes           | No                           |
| W777A                | No                                                             | Yes           | No                           |
| W795A                | Yes                                                            | Yes           | No                           |
| W842A                | No                                                             | Yes           | Yes                          |
| W844A                | No                                                             | Yes           | Yes                          |
| W845A                | Yes                                                            | Yes           | No                           |
| W847A                | No                                                             | Yes           | Yes                          |
| W849A                | No                                                             | Yes           | Yes                          |
| W862A                | No                                                             | Yes           | Yes                          |
| W864A                | No                                                             | Yes           | Yes                          |

<!-- page 063 -->

| Fee Schedule<br>Code | Referring/<br>Requisitioning<br>Health Care<br>Provider Number | Master Number | In-Patient<br>Admission Date |
|----------------------|----------------------------------------------------------------|---------------|------------------------------|
| W865A                | No                                                             | Yes           | Yes                          |
| W867A                | No                                                             | Yes           | Yes                          |
| W869A                | No                                                             | Yes           | No                           |
| W872A                | No                                                             | Yes           | Yes                          |
| W882A                | No                                                             | Yes           | No                           |
| W895A                | Yes                                                            | Yes           | No                           |
| W903A                | No                                                             | Yes           | No                           |
| W904A                | No                                                             | Yes           | No                           |
| W911A                | Yes                                                            | Yes           | No                           |
| W912A                | Yes                                                            | Yes           | No                           |
| W960A                | No                                                             | Yes           | No                           |
| W961A                | No                                                             | Yes           | No                           |
| W962A                | No                                                             | Yes           | No                           |
| W963A                | No                                                             | Yes           | No                           |
| W964A                | No                                                             | Yes           | No                           |
| W972A                | No                                                             | Yes           | No                           |
| W982A                | No                                                             | Yes           | No                           |
| W990A                | No                                                             | Yes           | No                           |
| W991A                | No                                                             | Yes           | No                           |
| W992A                | No                                                             | Yes           | No                           |
| W993A                | No                                                             | Yes           | No                           |
| W994A                | No                                                             | Yes           | No                           |
| W995A                | No                                                             | Yes           | No                           |
| W996A                | No                                                             | Yes           | No                           |
| W997A                | No                                                             | Yes           | No                           |
| W998A                | No                                                             | Yes           | No                           |
| W999A                | No                                                             | Yes           | No                           |
| Z777A                | No                                                             | Yes           | No                           |

#### **Note:**

- 1. A referring/requisitioning Health Care Provider number is required for all claims that are billed by Independent Health Facilities that have legacy agreements, or licensed with group numbers within the series AAAA – A999.
- 2. A referring/requisitioning Health Care Provider number is required for claims that are billed by groups with the following numbers, or such claims will reject under Review Error Condition V09 – Invalid Referral Number: Begins with 5 or 7; Within the series 8000 – 8599, 8600 – 8999; 6008, 6100 or 9xxx.

<!-- page 064 -->

The aforementioned list does not include the entire Ministry of Health insured services. The Fee Schedule Code Relationships Table only lists those Fee Schedule Codes, which require a referring/requisitioning health care provider number, a master number, and/or an in-patient admission date.

<!-- page 065 -->

## **4.11. Fee Schedule Code Suffix B/C Exceptions**

When the Fee Schedule Code Suffix is 'B' or 'C' the number of services must be greater than '01'.

| Exceptions to the above are: |  |  |  |  |  |  |
|------------------------------|--|--|--|--|--|--|
|------------------------------|--|--|--|--|--|--|

| C983B   | C985C   | C988B   | C998B,C   | C999B,C |
|---------|---------|---------|-----------|---------|
| E005C   | E008C   |         | E049C     |         |
| E052C   | E054C   | E055C   | E056C     | E100C   |
| E101B   | E400B,C | E401B,C | E450B,C   |         |
| E451B,C | E475C   | E505C   |           |         |
|         |         |         |           |         |
| G176B   | G177B   | G178B   | G179B     | G249B   |
| G254B   | G261B   | G262B   | G263B     | G265B   |
| G266B   | G267B   | G286B   | G288B     | G289B   |
| G290B   | G291B   | G292B   |           | G294B   |
| G296B   | G297B   | G298B   | G299B     | G300B   |
| G301B   | G305B   | G306B   | G321B     | G322B   |
| G366B   | G509B   | G518B   | G519B     |         |
| J100B,C | TO      | J399B,C | INCLUSIVE |         |
| J400C   | J402B,C | J403B,C | J405B,C   | J406B,C |
| J407B,C | J408B,C | J422B,C | J425B,C   | J427B,C |
| J428B,C | J435B,C | J438B,C | J459B,C   | J462B,C |
| J463B,C | J464B,C | J480B,C | J482B,C   | J483B,C |
| J489C   | TO      | J498C   |           |         |
| J490B   | TO      | J498B   |           |         |
| J500B,C | TO      | J507B,C |           |         |
| J602B,C | TO      | J689B,C | INCLUSIVE |         |
| J802B,C | TO      | J889B,C | INCLUSIVE |         |
| J894B   |         |         |           |         |

<!-- page 066 -->

| P015C   |       |         |           |       |
|---------|-------|---------|-----------|-------|
| XB      | XC    |         |           |       |
| Y602B,C | TO    | Y689B,C | INCLUSIVE |       |
| Y802B,C | TO    | Y889B,C | INCLUSIVE |       |
| Z431B   | Z434B | Z439B   | Z440B     | Z441B |
| Z442B   | Z443B | Z448B   | Z449B     |       |

<!-- page 067 -->

## <span id="page-66-0"></span>**4.12. Service Codes Requiring Specialized Submissions**

### **Prior Authorization**

The following is a list of service codes requiring specialized submissions for which prior authorization is required:

| E200      | E201      | M013 | M014 | M019 | M024 |
|-----------|-----------|------|------|------|------|
| R026-R028 | R110      | R112 | R319 | R320 | S318 |
| T901-T912 | T925-T928 | T936 | T950 |      |      |

### **Supporting Documentation**

The following is a list of service codes requiring specialized submissions for which supporting documentation (e.g. clinical records, operative reports) may be requested:

| A935      | C121      | E304      | E307      | E308 | E409 |
|-----------|-----------|-----------|-----------|------|------|
| E410      | E411      | E531      | E532      | E540 | E544 |
| E555      | E556      | E564      | E569      | E586 | E906 |
| E911      | E925      | E958      | E977      | F124 | F125 |
| F131      | F146      | G272      | G383      | G423 |      |
| G424      | G800-G805 | J041      | K001      | K018 | K021 |
| K101      | L299      | L585      | L611      | L690 | L693 |
| M011      | M033      | M109      | M110      | M400 | R004 |
| R007      | R025      | R029      | R051      | R057 | R058 |
| R064-R069 | R074      | R081-R083 | R086-R088 | R091 | R104 |
| R106      | R113      | R114      | R118      | R120 | R121 |
| R125-R139 | R150-R154 | R214      | R272      | R352 | R360 |
| R434      | R523      | R528      | R604      | R605 | R635 |
| R637      | R638      | R671      | R674      | R829 | R990 |
| R993      | S015      | S021      | S293      | S316 | S418 |
| S619      | S708      | S726      | S900      | T230 | T371 |
| T525      | T565      | T567-T570 | T618      | T800 | T809 |
| T810      | W121      | X486      | Z100      | Z148 | Z152 |
| Z155      | Z165      | Z191      | Z845      |      |      |

<!-- page 068 -->

## <span id="page-67-0"></span>**4.13. Service Location Indicator Codes**

Effective November 1, 2013 the acceptable Service Location Indicator (SLI) codes are:

| SLI Code | Description                       | Effective Date   |
|----------|-----------------------------------|------------------|
| HDS      | Hospital Day Surgery              | April 1, 2006    |
| HED      | Hospital Emergency<br>Department  | April 1, 2006    |
| HIP      | Hospital In-Patient               | April 1, 2006    |
| HOP      | Hospital Out-Patient              | April 1, 2006    |
| HRP      | Hospital Referred Patient         | August 1, 2006   |
| IHF      | Independent Health Facility       | July 1, 2011     |
| OFF      | Office of community physician     | July 1, 2011     |
| OTN      | Ontario Telemedicine<br>Network   | January 1, 2008  |
| PDF      | Private Diagnostic Facility       | November 1, 2013 |
| RTF      | Rehabilitation Treatment Facility | November 1, 2013 |

The Service Location Indicator is a "generic" field and the ministry may introduce SLI codes for other settings in the future to support data collection for planning and forecasting purposes.

#### **Telemedicine Codes**

The Service Location Indicator code "OTN" (Ontario Telemedicine Network) is required to identify telemedicine accounts to be processed by the OHIP claims payment processing system.

All accounts submitted to OHIP for telemedicine services from dentists and physicians **must** include the telemedicine SLI code which must be:

Located in field positions 59-62 of the Claim Header-1 Electronic Input Record of the billing

Left justified

Three alpha characters: OTN

<!-- page 069 -->

The SLI code will be reported in field positions 70-73 of the Claim Header Electronic Output Record.

### **Diagnostic Services Fee Codes**

The professional fee codes that can be billed as of April 1, 2006 by physicians for diagnostic services rendered to hospital in-patients and that require the HIP Service Location Indicator code are listed in the [Schedule of Benefits for Physician Services](http://www.health.gov.on.ca/english/providers/program/ohip/sob/physserv/physserv_mn.html) in the following sections:

- Nuclear Medicine In Vivo (Section B)
- Diagnostic Radiology (Section D)
- Magnetic Resonance Imaging (Section F)
- Diagnostic Ultrasound (Section G)
- Pulmonary Function Studies (Section H)
- Diagnostic and Therapeutic Procedures (Section J)

Hospital diagnostic services that will require a Service Location Indicator commencing April 1, 2006 and no later than October 1, 2006:

### **Hospital Diagnostic Services April 1, 2006 – October 1, 2006**

**A1: Nuclear Medicine – In Vivo**

**A2: Diagnostic Radiology**

**A3: Diagnostic Ultrasound**

**A4: Pulmonary Function Studies**

**A5: Magnetic Resonance Imaging**

**A6: Diagnostic and Therapeutic Procedures**

**A7: Technical Fee Codes**

<!-- page 070 -->

**A1: Nuclear Medicine – in Vivo**

### **Service Location Indicator Codes**

| J602C | J604C | J606C | J607C | J608C | J609C |
|-------|-------|-------|-------|-------|-------|
| J610C | J611C | J612C | J613C | J614C | J615C |
| J616C | J617C | J618C | J619C | J620C | J621C |
| J623C | J624C | J625C | J626C | J627C | J629C |
| J630C | J631C | J632C | J633C | J634C | J635C |
| J636C | J637C | J638C | J639C | J640C | J641C |
| J643C | J647C | J648C | J649C | J650C | J651C |
| J652C | J653C | J657C | J658C | J659C | J660C |
| J661C | J662C | J663C | J664C | J665C | J671C |
| J672C | J673C | J674C | J675C | J676C | J677C |
| J678C | J679C | J680C | J681C | J682C | J683C |
| J684C | J685C | J686C | J687C | J700C | J701C |
| J702C | J703C | J704C | J705C | J706C | J707C |
| J708C | J709C | J710C | J711C | J712C | J713C |
| J802C | J804C | J806C | J807C | J808C | J809C |
| J810C | J811C | J812C | J813C | J814C | J815C |
| J816C | J817C | J818C | J819C | J820C | J821C |
| J823C | J824C | J825C | J826C | J827C | J829C |
| J830C | J831C | J832C | J833C | J834C | J835C |
| J836C | J837C | J838C | J839C | J840C | J841C |
| J843C | J847C | J848C | J849C | J850C | J851C |
| J852C | J853C | J857C | J858C | J859C | J860C |
| J861C | J862C | J863C | J864C | J865C | J866C |
| J867C | J868C | J869C | J870C | J871C | J872C |
| J873C | J874C | J875C | J876C | J877C | J878C |
| J879C | J880C | J881C | J882C | J883C | J884C |
| J885C | J886C | J887C | Y602C | Y604C | Y606C |
| Y607C | Y608C | Y609C | Y610C | Y611C | Y612C |
|       |       |       |       |       |       |

<!-- page 071 -->

| Y613C                            | Y614C                | Y615C | Y616C | Y617C | Y618C |  |  |
|----------------------------------|----------------------|-------|-------|-------|-------|--|--|
| Y620C                            | Y621C                | Y623C | Y624C | Y625C | Y626C |  |  |
| Y627C                            | Y629C                | Y630C | Y631C | Y632C | Y633C |  |  |
| Y634C                            | Y635C                | Y636C | Y637C | Y638C | Y639C |  |  |
| Y640C                            | Y641C                | Y643C | Y647C | Y648C | Y649C |  |  |
| Y650C                            | Y651C                | Y652C | Y653C | Y657C | Y658C |  |  |
| Y659C                            | Y660C                | Y661C | Y662C | Y663C | Y664C |  |  |
| Y665C                            | Y667C                | Y668C | Y669C | Y670C | Y671C |  |  |
| Y672C                            | Y673C                | Y674C | Y675C | Y676C | Y677C |  |  |
| Y678C                            | Y679C                | Y681C | Y682C | Y683C | Y684C |  |  |
| Y685C                            | Y686C                | Y687C | Y802C | Y804C | Y806C |  |  |
| Y807C                            | Y808C                | Y810C | Y811C | Y812C | Y813C |  |  |
| Y814C                            | Y815C                | Y816C | Y817C | Y820C | Y823C |  |  |
| Y824C                            | Y825C                | Y826C | Y827C | Y829C | Y830C |  |  |
| Y831C                            | Y832C                | Y833C | Y836C | Y837C | Y838C |  |  |
| Y839C                            | Y840C                | Y841C | Y843C | Y847C | Y848C |  |  |
| Y849C                            | Y850C                | Y851C | Y852C | Y853C | Y857C |  |  |
| Y858C                            | Y859C                | Y860C | Y861C | Y862C | Y864C |  |  |
| Y865C                            | Y867C                | Y868C | Y869C | Y870C | Y871C |  |  |
| Y872C                            | Y873C                | Y874C | Y875C | Y876C | Y877C |  |  |
| Y878C                            | Y879C                | Y880C | Y881C | Y882C | Y883C |  |  |
| Y884C                            | Y885C                | Y886C | Y887C |       |       |  |  |
| A2:                              | Diagnostic Radiology |       |       |       |       |  |  |
| Service Location Indicator Codes |                      |       |       |       |       |  |  |
| X001C                            | X003C                | X004C | X005C | X006C | X007C |  |  |
| X008C                            | X009C                | X010C | X011C | X012C | X016C |  |  |
| X017C                            | X018C                | X019C | X020C | X025C | X027C |  |  |
| X028C                            | X031C                | X032C | X033C | X034C | X035C |  |  |
| X036C                            | X037C                | X038C | X039C | X040C | X045C |  |  |
| X046C                            | X047C                | X048C | X049C | X050C | X051C |  |  |
|                                  |                      |       |       |       |       |  |  |

<!-- page 072 -->

| X052C | X053C | X054C | X055C | X056C | X057C |
|-------|-------|-------|-------|-------|-------|
| X058C | X060C | X063C | X064C | X065C | X066C |
| X067C | X068C | X069C | X072C | X080C | X081C |
| X090C | X091C | X092C | X096C | X100C | X101C |
| X103C | X104C | X109C | X110C | X111C |       |
| X112C | X113C | X114C | X116C | X117C | X120C |
| X121C | X122C | X123C | X124C | X125C | X126C |
| X127C | X128C | X129C | X130C | X131C | X132C |
| X133C | X134C | X135C | X136C | X137C | X138C |
| X139C | X140C | X141C | X142C | X145C | X146C |
| X147C | X148C | X149C | X150C | X151C | X152C |
| X153C | X154C | X155C | X156C | X158C | X159C |
| X160C | X161C | X162C | X163C | X164C | X165C |
| X166C | X167C | X168C | X169C | X170C | X171C |
| X172C | X173C | X174C | X176C | X177C | X178C |
| X179C | X180C | X181C | X182C | X183C | X184C |
| X185C | X188C | X189C | X190C | X191C | X192C |
| X193C | X194C | X195C | X196C | X197C | X198C |
| X199C | X200C | X201C | X202C | X203C | X204C |
| X205C | X206C | X207C | X208C | X209C | X210C |
| X211C | X212C | X213C | X214C | X215C | X216C |
| X217C | X218C | X219C | X220C | X221C | X223C |
| X224C | X225C | X226C | X227C | X228C | X229C |
| X230C | X231C | X232C | X233C | X234C | X235C |
| X400C | X401C | X402C | X403C | X404C | X405C |
| X406C | X407C | X408C | X409C | X410C | X412C |
| X413C | X415C | X416C | X417C |       |       |
|       |       |       |       |       |       |

<!-- page 073 -->

| A3:                               | Diagnostic Ultrasound |       |       |       |       |  |  |
|-----------------------------------|-----------------------|-------|-------|-------|-------|--|--|
| E475                              | J102C                 | J103C | J105C | J107C | J108C |  |  |
| J122C                             | J125C                 | J127C | J128C | J135C | J138C |  |  |
| J149C                             | J151C                 | J157C | J158C | J159C | J160C |  |  |
| J161C                             | J162C                 | J163C | J164C | J165C | J166C |  |  |
| J167C                             | J168C                 | J169C | J180C | J182C | J183C |  |  |
| J186C                             | J187C                 | J188C | J189C | J190C | J193C |  |  |
| J196C                             | J197C                 | J198C | J199C | J200C | J201C |  |  |
| J202C                             | J203C                 | J204C | J205C | J206C | J207C |  |  |
| J290C                             | J402C                 | J403C | J405C | J407C | J408C |  |  |
| J422C                             | J425C                 | J427C | J428C | J435C | J438C |  |  |
| J457C                             | J458C                 | J459C | J460C | J461C | J462C |  |  |
| J463C                             | J464C                 | J466C | J468C | J469C | J476C |  |  |
| J480C                             | J482C                 | J483C | J486C | J487C | J488C |  |  |
| J489C                             | J490C                 | J493C | J496C | J497C | J498C |  |  |
| J499C                             | J500C                 | J501C | J502C | J503C | J504C |  |  |
| J505C                             | J506C                 | J507C |       |       |       |  |  |
| A4:<br>Pulmonary Function Studies |                       |       |       |       |       |  |  |
| E450                              | E451                  | J301C | J303C | J304C | J305C |  |  |
| J306C                             | J307C                 | J308C | J309C | J310C | J311C |  |  |
| J313C                             | J315C                 | J316C | J317C | J318C | J319C |  |  |
| J320C                             | J322C                 | J323C | J324C | J327C | J330C |  |  |
| J331C                             | J332C                 | J333C | J334C | J335C | J336C |  |  |
| J340C                             |                       |       |       |       |       |  |  |

<!-- page 074 -->

### **A5: Magnetic Resonance Imaging (MRI)**

### **Service Location Indicator Codes**

| X421C | X425C | X431C | X435C | X441C | X445C |
|-------|-------|-------|-------|-------|-------|
| X446C | X447C | X451C | X455C | X461C | X465C |
| X471C | X475C | X480C | X481C | X486C | X487C |
| X488C | X489C | X490C | X492C | X493C | X495C |
| X496C | X498C | X499C |       |       |       |

### **A6: Diagnostic and Therapeutic Procedures**

#### **Service Location Indicator Codes**

| G105A | G112A | G120A | G126A | G138A | G139A |
|-------|-------|-------|-------|-------|-------|
| G141A | G142A | G144A | G145A | G147A | G148A |
| G150A | G151A | G166A | G180A | G197A | G251A |
| G252A | G253A | G283A | G307A | G313A | G317A |
| G319A | G320A | G321A | G332A | G343A | G346A |
| G350A | G351A | G353A | G354A | G415A | G418A |
| G425A | G428A | G432A | G433A | G436A | G437A |
| G438A | G439A | G444A | G450A | G456A | G457A |
| G459A | G469A | G473A | G477A | G516A | G518A |
| G524A | G525A | G526A | G529A | G530A | G533A |
| G543A | G545A | G546A | G555A | G571A | G572A |
| G575A | G578A | G581A | G583A | G584A | G649A |
| G650A | G653A | G656A | G657A | G658A | G659A |
| G660A | G690A | G816A |       |       |       |

<!-- page 075 -->

#### **A7: Technical Fee Codes**

The following technical-fee diagnostics services are **not** billable for hospital in-patient (HIP) services but can be submitted with all other SLI codes as applicable:

### **Service Location Indicator Codes**

| E450B | E451B | G104A | G111A | G121A | G127A |
|-------|-------|-------|-------|-------|-------|
| G140A | G143A | G146A | G149A | G152A | G153A |
| G167A | G174A | G181A | G209A | G284A | G308A |
| G310A | G311A | G315A | G414A | G440A | G441A |
| G442A | G443A | G448A | G451A | G455A | G466A |
| G471A | G519A | G540A | G541A | G542A | G544A |
| G554A | G560A | G570A | G574A | G582A | G685A |
| G647A | G648A | G651A | G652A | G654A | G655A |
| G682A | G683A | G684A | G685A | G686A | G687A |
| G688A | G689A | G694A | G695A | G815A | G850A |
| G851A | G852A | G853A | G854A | G855A | G856A |
| G857A | G858A | J102B | J103B | J105B | J107B |
| J108B | J122B | J125B | J127B | J128B | J135B |
| J138B | J149B | J157B | J158B | J159B | J160B |
| J161B | J162B | J163B | J164B | J165B | J166B |
| J167B | J168B | J169B | J180B | J182B | J183B |
| J190B | J193B | J196B | J197B | J198B | J199B |
| J200B | J201B | J202B | J203B | J204B | J205B |
|       | J206B | J207B | J301B | J303B | J304B |
| J305B | J306B | J307B | J308B | J310B | J311B |
| J313B | J315B | J316B | J318B | J319B | J320B |
| J322B | J323B | J324B | J327B | J330B | J331B |
| J332B | J333B | J334B | J335B | J336B | J340B |
| J476B | J480B | J482B | J483B | J802B | J804B |
| J806B | J807B | J808B | J809B | J810B | J811B |
|       |       |       |       |       |       |

<!-- page 076 -->

| J812B | J813B | J814B | J815B | J816B | J817B |
|-------|-------|-------|-------|-------|-------|
| J818B | J819B | J820B | J821B | J823B | J824B |
| J825B | J826B | J827B | J829B | J830B | J831B |
| J832B | J833B | J834B | J835B | J836B | J837B |
| J838B | J839B | J840B | J841B | J843B | J847B |
| J848B | J849B | J850B | J851B | J852B | J853B |
| J857B | J858B | J859B | J860B | J861B | J862B |
| J863B | J864B | J865B | J866B | J867B | J868B |
| J689B | J870B | J871B | J872B | J873B | J874B |
| J875B | J876B | J877B | J878B | J879B | J880B |
| J881B | J882B | J883B | J884B | J885B | J886B |
| J887B | J889B | J890B | J893B | J894B | J895B |
| J896B | J897B | J898B | J899B | J900B | J901B |
| X001B | X003B | X004B | X005B | X006B | X007B |
| X009B | X010B | X011B | X012B | X016B | X017B |
| X018B | X019B | X020B | X025B | X027B | X028B |
| X031B | X032B | X033B | X034B | X035B | X036B |
| X037B | X038B | X039B | X040B | X045B | X046B |
| X047B | X048B | X049B | X050B | X051B | X052B |
| X053B | X054B | X055B | X056B | X057B | X058B |
| X060B | X063B | X064B | X065B | X066B | X067B |
| X068B | X069B | X072B | X080B | X081B | X090B |
| X091B | X092B | X096B | X100B | X101B | X103B |
| X104B | X105B | X106B | X107B | X108B | X109B |
| X110B | X111B | X112B | X113B | X114B | X116B |
| X117B | X120B | X121B | X122B | X123B | X124B |
| X125B | X126B | X127B | X128B | X129B | X130B |
| X131B | X132B | X133B | X134B | X135B | X136B |
| X137B | X138B | X139B | X140B | X141B | X142B |
|       |       |       |       |       |       |

<!-- page 077 -->

| X145B | X146B | X147B | X149B | X150B | X151B |
|-------|-------|-------|-------|-------|-------|
| X152B | X153B | X154B | X155B | X156B | X157B |
| X158B | X159B | X160B | X161B | X162B | X163B |
| X164B | X165B | X166B | X167B | X168B | X169B |
| X170B | X171B | X172B | X173B | X174B | X175B |
| X176B | X177B | X178B | X179B | X180B | X181B |
| X182B | X183B | X184B | X185B | X188B | X189B |
| X190B | X191B | X192B | X193B | X194B | X195B |
| X196B | X197B | X198B | X199B | X200B | X201B |
| X202B | X203B | X204B | X205B | X206B | X207B |
| X208B | X209B | X210B | X211B | X212B | X213B |
| X214B | X215B | X216B | X217B | X218B | X219B |
| X220B | X221B | X223B | X224B | X225B | X226B |
| X227B | X228B | X229B | X230B | Y602B | Y802B |
| Y804B | Y806B | Y807B | Y808B | Y810B | Y811B |
| Y812B | Y813B | Y814B | Y815B | Y816B | Y817B |
| Y820B | Y823B | Y824B | Y825B | Y826B | Y827B |
| Y829B | Y830B | Y831B | Y832B | Y833B | Y834B |
| Y836B | Y837B | Y838B | Y839B | Y840B | Y841B |
| Y843B | Y847B | Y848B | Y849B | Y850B | Y851B |
| Y852B | Y853B | Y857B | Y858B | Y859B | Y860B |
| Y861B | Y862B | Y864B | Y865B | Y867B | Y868B |
| Y869B | Y870B | Y871B | Y872B | Y873B | Y874B |
| Y875B | Y876B | Y877B | Y878B | Y879B | Y881B |
| Y882B | Y883B | Y884B | Y885B | Y886B | Y887B |
|       |       |       |       |       |       |

<!-- page 078 -->

## <span id="page-77-0"></span>**4.14. MOD 10 Check Digit**

To reduce the number of rejected claims, it is recommended that the health number is verified by the MOD 10 Check Digit.

### **Health Number Example**

| DIGIT POSITION                                 | 1         | 2 | 3         | 4 | 5         | 6 | 7 | 8 | 9 | 10                    |
|------------------------------------------------|-----------|---|-----------|---|-----------|---|---|---|---|-----------------------|
| Health Number<br>Validation                    | 9         | 8 | 7         | 6 | 5         | 4 | 3 | 2 | 1 | Check<br>(7)<br>Digit |
| Double 1st, 3rd,<br>5th, 7th and 9th<br>Digits | (1+<br>8) | 8 | (1+<br>4) | 6 | (1+<br>0) | 4 | 6 | 2 | 2 |                       |
| Add The Unit<br>Position<br>Numbers Across     | 9         | 8 | 5         | 6 | 1         | 4 | 6 | 2 | 2 | =<br>4(3)             |

Subtract The Unit Position From Ten

10

-3

The Check Digit is (**7**) therefore the Health Number 987654321**7** is valid (7)

<!-- page 079 -->

## **4.15. Province Codes and Numbering**

[Find a full list of Province and Territo](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/province_and_territory_codes.pdf)ry Codes by visiting our website

## <span id="page-78-0"></span>**4.16. Valid Payment Program/Payee Combinations**

| Payment Program | Payee |
|-----------------|-------|
| HCP             | P     |
| HCP             | S     |
| WCB             | P     |
| RMB             | P     |

All other combinations are invalid.

## **4.16.1. Legend**

### **Payment Program Types**

HCP = Health Claims Payment

WCB = Worker's Compensation Board (Workplace Safety and Insurance Board)

RMB = Reciprocal Medical Billing

### **Payee Types**

P = Provider

S = Patient

<!-- page 080 -->

## **4.17. Workplace Safety and Insurance Board (WSIB)**

### **Input Conditions**

WSIB related medical services can be submitted to the ministry for payment under the "WCB" payment program.

The following services are excluded from WSIB (WCB) submissions:

- Service codes prefixed by T or V
- Laboratory services provided by private medical laboratory facilities (health care provider group number range 5000 – 5999)
- Services provided by hospital diagnostic departments (health care provider clinic number range 8600 – 9999)
- Services provided by OPTED-OUT health care providers

For further information, refer to [Service Codes Requiring Specialized Submissions.](#page-66-0)

<!-- page 081 -->

## **Chapter 5 Electronic Output Specifications for Reports**

<!-- page 082 -->

## **5. Electronic Output (EO) Specifications for Reports**

## **5.1. Claims Batch Edit Reports**

If a file is accepted, a Claims Batch Edit Report is sent to acknowledge receipt of each batch submitted. This report is sent to the user ID and notes whether or not the batch is accepted or rejected (refer to [Rejection Categories\)](#page-113-0). If a Batch Edit Report is not received either the ministry did not receive the file or month end processing is underway.

## **5.2. Remittance Advice (RA)**

A remittance advice is a monthly statement of approved claims and is issued at the time of payment. The remittance advice file contains accounting details of claims approved during the ministry's previous claims processing cycle. It will also contain explanatory codes to clarify payment exceptions (refer to [Remittance Advice Explanatory Codes\)](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/remittance_advice_explanatory_codes.pdf).

The remittance advice may also contain general bulletins or messages from the ministry. The file is available in several different sort sequences, such as accounting number.

<!-- page 083 -->

## **5.3. Remittance Advice Data Sequences**

The remittance advice is available in 4 sequences as follows:

| Sort Keys                                     | RA     | RA     | RA     | RA     |
|-----------------------------------------------|--------|--------|--------|--------|
|                                               | Type 4 | Type 5 | Type 6 | Type 7 |
| Health Care Provider Group<br>Number          | 1      |        |        | 1      |
| MOH<br>Office Code                            | 2      | 1      |        |        |
| Patient's Last Name (not<br>available for EO) |        |        |        | (3)    |
| Health Care Provider Accounting<br>Number     | 3      | 2      |        | 2      |
| Health/Registration Number                    | 4      | 3      | 1      | 4      |
| Claim Number                                  | 5      | 4      | 2      | 5      |

**Note:**

**1 = primary sort field**

<!-- page 084 -->

RA Type 4: ACCOUNTING NUMBER Sort for Health Care Provider Groups

The file is sorted by Health Care Provider within the Group. If the Health Care Provider had service encounters processed in more than one ministry office, the service encounters are further sorted by ministry Office Code. Within the above sorts, the service encounters are sorted by: Health Care Provider Accounting Number, Health/Registration Number and Service Encounter Number.

RA Type 5: ACCOUNTING NUMBER Sort for Solo Health Care Providers

If the Health Care Provider had service encounters processed in more than one ministry office, the service encounters are sorted by ministry Office Code (will be supplied by the ministry's processing system). Within the above sort, the service encounters are sorted by: Health Care Provider Accounting Number, Health/Registration Number and Service Encounter Number.

RA Type 6: HEALTH/REGISTRATION NUMBER

The file is sorted by: Health/Registration Number and Service Encounter Number.

RA Type 7: ACCOUNTING NUMBER Sort for Health Care Provider Groups

The file is sorted by Heath Care Provider within the Group. Within the above sort, the service encounters are sorted by: Health Care Provider Accounting Number, Health/Registration Number and Service Encounter Number. The sort hierarchy within the Accounting Number is: blanks, alphas, numerics.

One remittance advice file is created for each health care provider for every claims processing cycle regardless of the number of submissions within that cycle.

<!-- page 085 -->

## **5.4. File Naming Convention – Remittance Advice**

#### **MCEDT**

Output file will have file names in the following format:

| P        | Month                                                                                    | Group Number or Provider Number                         | Sequence Number               |
|----------|------------------------------------------------------------------------------------------|---------------------------------------------------------|-------------------------------|
| Example: |                                                                                          | PA123456.001 or PA1234.001                              |                               |
| Field 1  |                                                                                          | P represents the output indicator                       |                               |
| Field 2  | February)                                                                                | Alpha representation for current processing cycle       | (e.g.<br>A for January, B for |
| Field 3  | Health care provider's registered group number<br>or solo health care<br>provider number |                                                         |                               |
| Field 4  |                                                                                          | Three digit sequence number assigned by<br>the ministry |                               |

<!-- page 086 -->

## **5.5. Format Summary**

| Record Type | Description                                                                                                                                                                                                                 |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1           | File Header                                                                                                                                                                                                                 |
|             | Health care provider information                                                                                                                                                                                            |
| 2           | Address Record 1                                                                                                                                                                                                            |
|             | Name and address Line 1 of billing agent as<br>recorded with the ministry                                                                                                                                                   |
|             | or                                                                                                                                                                                                                          |
|             | Address Line 1 of the health care provider as<br>recorded with the ministry                                                                                                                                                 |
| 3           | Address Record 2                                                                                                                                                                                                            |
|             | Address Lines 2 and 3 of billing agent (if billing<br>agent's name present in Address Record 1) or of<br>health care provider                                                                                               |
| 4           | Claim Header                                                                                                                                                                                                                |
|             | Common control information for each claim                                                                                                                                                                                   |
| 5           | Claim Item                                                                                                                                                                                                                  |
|             | Detailed information for each item of service<br>within a claim (e.g.<br>service code, service date,<br>amounts)                                                                                                            |
| 6           | Balance Forward                                                                                                                                                                                                             |
|             | This record is present only if the previous month's<br>remittance was NEGATIVE.<br>It indicates any<br>amounts brought forward from the previous month<br>by category (e.g.<br>claim adjustments, advances,<br>reductions). |
| 7           | Accounting Transaction                                                                                                                                                                                                      |
|             | This record is present only if an accounting<br>transaction is posted to the remittance advice<br>(e.g.<br>advance, reduction, special payment).                                                                            |
|             | The sum of the fees paid for approved RMB<br>claims will also appear as an accounting<br>transaction.                                                                                                                       |

<!-- page 087 -->

| Record Type | Description                                                                                                                                                                                 |  |  |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--|--|
| 8           | Message Facility                                                                                                                                                                            |  |  |
|             | A facility for the ministry to send messages to all<br>or selected health care providers.<br>This record may<br>or may not be present.<br>If present, can have up to<br>99,999 occurrences. |  |  |

Claims that are processed in the Reciprocal Medical Billing (RMB) system will be included with the regular remittance advice data. The RMB records (claim headers and items) appear at the end of the file, after all other non-RMB records.

<!-- page 088 -->

### **Health Reconciliation Sample**

Fixed Record Length: 79 Characters

The illustration is a visual representation of the record types outlined on the previous page to assist in understanding the record types and information contained within

<!-- page 089 -->

## **5.6. Remittance Advice (RA) Record Layout**

### **Health Reconciliation**

### **Format Legend**

**A = Alphabetic**

**N = Numeric**

**X = Alphanumeric**

**D = Date (YYYYMMDD)**

**S = Spaces**

#### **Note:**

All alphabetic characters will be upper-case unless otherwise stated. The last 2 digits of all the amount fields are cents (¢¢).

Refer to [EI Record Layouts](#page-16-0) for additional field description details, where applicable.

<!-- page 090 -->

### **File Header Record – Health Reconciliation**

#### Occurs **Once** in Every File – Always the **First** Record

| Field Name                                                                 | Field Start<br>Position | Field Length | Format | Field Description                                                    |
|----------------------------------------------------------------------------|-------------------------|--------------|--------|----------------------------------------------------------------------|
| Transaction<br>Identifier                                                  | 1                       | 2            | A      | Always 'HR'                                                          |
| Record Type                                                                | 3                       | 1            | X      | Always '1'                                                           |
| Tech Spec<br>Release<br>Identifier                                         | 4                       | 3            | X      | Always 'V03'                                                         |
| Reserved for<br>MOH Use                                                    | 7                       | 1            | X      | Always '0'<br>(zero)                                                 |
| Group Number<br>or Laboratory<br>Licence No.                               | 8                       | 4            | X      |                                                                      |
| Health Care<br>Provider/<br>Physio Facility/<br>Laboratory<br>Director No. | 12                      | 6            | N      |                                                                      |
| Specialty                                                                  | 18                      | 2            | X      | A space if no HR<br>4/5 records,<br>otherwise it will<br>be numeric. |
| MOH Office<br>Code                                                         | 20                      | 1            | A      | 'A', 'B', 'C', 'H',<br>'K', 'L', 'M', 'S' or<br>'T'                  |
| Remittance<br>Advice Data<br>Sequence                                      | 21                      | 1            | N      | Number<br>representing sort<br>sequence.                             |
| Payment Date                                                               | 22                      | 8            | D      | Cheque or direct<br>bank deposit date                                |

<!-- page 091 -->

| Field Name | Field Start<br>Position | Field Length | Format | Field Description                                                                                                                                                                     |
|------------|-------------------------|--------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Payee Name | 30                      | 30           | X      | Name of Payee<br>as registered with<br>the ministry -<br>Subdivided for<br>solo Health Care<br>Providers as<br>follows:<br>-<br>Last Name (25)<br>-<br>Title (3)<br>-<br>Initials (2) |

<!-- page 092 -->

### **File Header Record – Health Reconciliation**

#### Occurs **Once** in Every File – Always the **First** Record

| Field Name                   | Field Start<br>Position | Field Length | Format | Field Description                                                                                                                                                                            |
|------------------------------|-------------------------|--------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Total Amount<br>Payable      | 60                      | 9            | N      | Accumulation of<br>the Amount Paid<br>for all claim items<br>appearing on the<br>remittance advice<br>Plus and/or Minus<br>any Accounting<br>Transactions and<br>Balance Forward<br>amounts. |
| Total Amount<br>Payable Sign | 69                      | 1            | S or X | Space if Total<br>Amount Payable<br>is positive.<br>Negative (-) sign if<br>Total Amount<br>Payable is<br>negative.                                                                          |
| Cheque<br>Number             | 70                      | 8            | X      | Pay Provider:<br>number of the<br>cheque or all '9's<br>if Direct Bank<br>Deposit.<br>Pay Patient:<br>spaces                                                                                 |
| Reserved for<br>MOH Use      | 78                      | 2            | S      | Spaces                                                                                                                                                                                       |

<!-- page 093 -->

### **Address Record One – Health Reconciliation**

Occurs Once in Every File – Always the Second Record

| Field Name                | Field Start<br>Position | Field Length | Format | Field<br>Description                                                                            |
|---------------------------|-------------------------|--------------|--------|-------------------------------------------------------------------------------------------------|
| Transaction<br>Identifier | 1                       | 2            | A      | Always 'HR'                                                                                     |
| Record Type               | 3                       | 1            | X      | Always '2'                                                                                      |
| Billing Agent's<br>Name   | 4                       | 30           | X      | Spaces if a Billing Agent is not registered for this Health Care Provider/ group.               |
| Address Line<br>One       | 34                      | 25           | X      | Address Line 1<br>of Health Care<br>Provider/group<br>or Address Line<br>1 of Billing<br>Agent. |
|                           |                         |              |        | As registered with the ministry.                                                                |
| Reserved for MOH Use      | 59                      | 21           | S      | Spaces                                                                                          |

### **Address Record Two – Health Reconciliation**

Occurs Once in Every File - Always the Third Record

| Field Name                | Field Start<br>Position | Field Length | Format | Field<br>Description             |
|---------------------------|-------------------------|--------------|--------|----------------------------------|
| Transaction<br>Identifier | 1                       | 2            | A      | Always 'HR'                      |
| Record Type               | 3                       | 1            | X      | Always '3'                       |
| Address Line 2            | 4                       | 25           | X      | As registered with the ministry. |
| Address Line 3            | 29                      | 25           | X      | As registered with the ministry. |

<!-- page 094 -->

| Field Name           | Field Start<br>Position | Field Length | Format | Field<br>Description |
|----------------------|-------------------------|--------------|--------|----------------------|
| Reserved for MOH Use | 54                      | 26           | S      | Spaces               |

<!-- page 095 -->

### **Claim Header Record – Health Reconciliation**

#### **Multiple Records – Occurs Once for Each Claim in a File**

| Field Name                                                                 | Field Start<br>Position | Field Length | Format | Field<br>Description                                                                           |
|----------------------------------------------------------------------------|-------------------------|--------------|--------|------------------------------------------------------------------------------------------------|
| Transaction<br>Identifier                                                  | 1                       | 2            | A      | Always 'HR'                                                                                    |
| Record Type                                                                | 3                       | 1            | X      | Always '4'                                                                                     |
| Claim Number                                                               | 4                       | 11           | X      | Ministry<br>reference<br>number.                                                               |
| Transaction<br>Type                                                        | 15                      | 1            | N      | 1 (original claim) or 2 (adjustment to original claim).                                        |
| Health Care<br>Provider/<br>Physio Facility/<br>Laboratory<br>Director No. | 16                      | 6            | N      |                                                                                                |
| Specialty                                                                  | 22                      | 2            | N      | Health Care<br>Provider's<br>Specialty Code<br>as on Health<br>Encounter<br>Claim Header-<br>1 |
| Accounting<br>Number                                                       | 24                      | 8            | X      | Accounting number as on Health Encounter Claim Header –                                        |
| Patient's Last<br>Name                                                     | 32                      | 14           | S or A | Spaces except for RMB claims                                                                   |
| Patient's First<br>Name (First<br>five characters)                         | 46                      | 5            | S or A | Spaces except for RMB claims.                                                                  |
| Province Code                                                              | 51                      | 2            | A      | Refer to Province Codes and Numbering.                                                         |

<!-- page 096 -->

| Field Name                       | Field Start<br>Position | Field Length | Format | Field<br>Description |
|----------------------------------|-------------------------|--------------|--------|----------------------|
| Health<br>Registration<br>Number | 53                      | 12           | X or S | Left justified       |

<!-- page 097 -->

### **Claim Header Record – Health Reconciliation**

#### **Multiple Records – Occurs Once for Each Claim in a File**

| Field Name                       | Field Start<br>Position | Field Length | Format | Field<br>Description                                                                               |
|----------------------------------|-------------------------|--------------|--------|----------------------------------------------------------------------------------------------------|
| Version Code                     | 65                      | 2            | A or S | Version code<br>as on Health<br>Encounter<br>Claim Header –<br>1.                                  |
| Payment<br>Program               | 67                      | 3            | A      | Payment<br>program as on<br>Health<br>Encounter<br>Claim Header –<br>1.                            |
| Service<br>Location<br>Indicator | 70                      | 4            | N or S | 4 numerics or spaces                                                                               |
|                                  |                         |              |        | Service Location Indicator (SLI) as on Health Encounter Claim Header – 1.                          |
| MOH Group<br>Identifier          | 74                      | 4            | X      | MOH Group<br>Number<br>Identifier<br>Information for<br>redirection to<br>Health Care<br>Provider. |
| Reserved for MOH Use             | 78                      | 2            | S      | Spaces                                                                                             |

<!-- page 098 -->

### **Claim Item Record – Health Reconciliation**

#### **Multiple Records – Occurs Once for Each Item in a Claim**

| Field Name                | Field Start<br>Position | Field Length | Format | Field<br>Description                                                   |
|---------------------------|-------------------------|--------------|--------|------------------------------------------------------------------------|
| Transaction<br>Identifier | 1                       | 2            | A      | Always 'HR'                                                            |
| Record Type               | 3                       | 1            | X      | Always '5'                                                             |
| Claim Number              | 4                       | 11           | X      | Ministry<br>reference<br>number.                                       |
| Transaction<br>Type       | 15                      | 1            | N      | 1 (original claim) or 2 (adjustment to original claim).                |
| Service Date              | 16                      | 8            | D      | Service date as on Health Encounter Item Record.                       |
| Number of<br>Services     | 24                      | 2            | N      | Number of<br>Services as on<br>Health<br>Encounter Item<br>Record.     |
| Service Code              | 26                      | 5            | X      |                                                                        |
| Reserved for MOH Use      | 31                      | 1            | S      | Spaces                                                                 |
| Amount<br>Submitted       | 32                      | 6            | N      | Amount submitted as on Health Encounter Item Record.                   |
| Amount Paid               | 38                      | 6            | N      |                                                                        |
| Amount Paid<br>Sign       | 44                      | 1            | S or X | Space if Amount Paid is positive.  Negative (-) sign if Amount Paid is |
|                           |                         |              |        | negative.                                                              |

<!-- page 099 -->

### **Claim Item Record – Health Reconciliation**

#### **Multiple Records – Occurs Once for Each Item in a Claim**

| Field Name           | Field Start<br>Position | Field Length | Format | Field<br>Description                         |
|----------------------|-------------------------|--------------|--------|----------------------------------------------|
| Explanatory<br>Code  | 45                      | 2            | X      | Refer to Remittance Advice Explanatory Codes |
| Reserved for MOH Use | 47                      | 33           | S      | Spaces                                       |

<!-- page 100 -->

### **Balance Forward Record – Health Reconciliation**

#### **Occurs Once for Each File**

(only if previous month's payment was negative)

| Field Name                                                     | Field Start<br>Position | Field Length | Format | Field Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|----------------------------------------------------------------|-------------------------|--------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Transaction<br>Identifier                                      | 1                       | 2            | A      | Always 'HR'                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Record Type                                                    | 3                       | 1            | X      | Always '6'                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Amount<br>Brought<br>Forward –<br>Claims<br>Adjustment         | 4                       | 9            | N      | Field will contain a<br>value other than zeros<br>when the Total<br>Remittance Payable<br>does not exceed the<br>total debit items for<br>adjusted claims.<br>The<br>debit items are<br>deducted from the Total<br>Remittance Payable<br>starting with the oldest<br>debit.<br>If the Total<br>Remittance Payable is<br>reduced to ZERO,<br>the<br>remaining debits are<br>summarized and<br>appear as a Record<br>Type 6 (Amount<br>Brought Forward –<br>Claims Adjustments) on<br>the next month's<br>remittance.<br>This<br>amount is always<br>negative. |
| Amount<br>Brought<br>Forward –<br>Claims<br>Adjustment<br>Sign | 13                      | 1            | S or X | Field will be a space if<br>the Claims Adjustment<br>field contains zeros,<br>otherwise, it will be a<br>negative (-) sign.                                                                                                                                                                                                                                                                                                                                                                                                                                  |

<!-- page 101 -->

### **Balance Forward Record – Health Reconciliation**

#### **Occurs Once for Each File**

(only if previous month's payment was negative)

| Field Name                                              | Field Start<br>Position | Field Length | Format | Field Description                                                                                                                                                                                                                                                                               |
|---------------------------------------------------------|-------------------------|--------------|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Amount Brought<br>Forward –<br>Advances                 | 14                      | 9            | N      | Field will contain a value other than<br>zeros when a Record Type 7<br>(Transaction Code 10 –<br>Advance)<br>on a previous Remittance Advice<br>fails to recover the full value of an<br>advance. The Amount Brought<br>Forward is the unrecovered amount<br>and is always negative.            |
| Amount Brought<br>Forward –<br>Advances Sign            | 23                      | 1            | S or X | Field will be a space if the<br>Advances field contains zeros,<br>otherwise it will be a negative (-)<br>sign.                                                                                                                                                                                  |
| Amount Brought<br>Forward –<br>Reductions               | 24                      | 9            | N      | Field will contain a value other<br>than zeros when a Record Type 7<br>(Transaction Code 20 –<br>Reduction) on a previous<br>Remittance Advice cannot be<br>satisfied by the Total Remittance<br>Payable.<br>The Amount Brought<br>Forward is the unrecovered<br>amount and is always negative. |
| Amount Brought<br>Forward –<br>Reductions Sign          | 33                      | 1            | S or X | Field will be a space if the<br>Reductions field contains zeros,<br>otherwise it will be a negative (-)<br>sign.                                                                                                                                                                                |
| Amount Brought<br>Forward –<br>Other<br>Deductions      | 34                      | 9            | N      | For future use (presently zero<br>filled).                                                                                                                                                                                                                                                      |
| Amount Brought<br>Forward –<br>Other<br>Deductions Sign | 43                      | 1            | S      | For future use (presently a<br>space).                                                                                                                                                                                                                                                          |
| Reserved for<br>MOH Use                                 | 44                      | 36           | S      | Spaces                                                                                                                                                                                                                                                                                          |

**Note: Priority of Deductions**

Claim adjustments Advances Reductions

<!-- page 102 -->

### **Accounting Transaction Record – Health Reconciliation**

Occurs **Once** for Each Accounting Transaction

| Field Name                | Field Start<br>Position | Field Length | Format | Field Description                                                                                                                                                                                                                      |
|---------------------------|-------------------------|--------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Transaction<br>Identifier | 1                       | 2            | A      | Always 'HR'                                                                                                                                                                                                                            |
| Record Type               | 3                       | 1            | X      | Always '7'                                                                                                                                                                                                                             |
| Transaction<br>Code       | 4                       | 2            | X      | 10 –<br>Recovery of<br>Advance<br>20 –<br>Reduction<br>30 –<br>Unused<br>40 –<br>Payment<br>50 –<br>Estimated<br>Payment for<br>Unprocessed<br>Claims<br>70 –<br>Unused<br>Refer to<br>Accounting<br>Transactions for<br>Record Type 7 |
| Cheque<br>Indicator       | 6                       | 1            | X      | Ministry use:<br>M –<br>Manual<br>Cheque issued<br>C –<br>Computer<br>Cheque issued<br>I –<br>Interim<br>payment<br>Cheque/ Direct<br>Bank Deposit<br>issued                                                                           |
| Transaction<br>Date       | 7                       | 8            | D      | Date of transaction<br>created                                                                                                                                                                                                         |
| Transaction<br>Amount     | 15                      | 8            | N      |                                                                                                                                                                                                                                        |

<!-- page 103 -->

### **Accounting Transaction Record – Health Reconciliation**

### Occurs **Once** for Each Accounting Transaction

| Field Name                 | Field Start<br>Position | Field Length | Format | Field<br>Description                                |
|----------------------------|-------------------------|--------------|--------|-----------------------------------------------------|
| Transaction<br>Amount Sign | 23                      | 1            | S or X | A space if<br>Transaction<br>Amount is<br>positive  |
|                            |                         |              |        | Negative (-) sign if Transaction Amount is negative |
| Transaction<br>Message     | 24                      | 50           | S or X | Description of transaction                          |
| Reserved for MOH Use       | 74                      | 6            | S      | Spaces                                              |

<!-- page 104 -->

### **Message Facility Record – Health Reconciliation**

May be present

| Field Name                | Field Start<br>Position | Field Length | Format | Field<br>Description                                  |
|---------------------------|-------------------------|--------------|--------|-------------------------------------------------------|
| Transaction<br>Identifier | 1                       | 2            | A      | Always 'HR'                                           |
| Record Type               | 3                       | 1            | X      | Always '8'                                            |
| Message Text              | 4                       | 70           | X      | Message<br>(contains upper<br>case and lower<br>case) |
| Reserved for<br>MOH Use   | 74                      | 6            | S      | Spaces                                                |

**Note:** If there is more than one message, they will be separated by a record containing asterisks (e.g. position 4 to 73 of one record type 8).

<!-- page 105 -->

## <span id="page-104-0"></span>**5.7. Accounting Transactions for Record Type 7**

#### **Transaction Code 10 – Recovery of Advance is created to:**

• Recover an advance payment.

This amount is always negative and is deducted from the total remittance payable. If it exceeds the total remittance payable it is carried forward to the next month's remittance as a Record Type 6 or part of it (Amount Brought Forward - Advances) with a negative value.

#### **Transaction Code 20 - Reduction is created when:**

- A debit is required for claim items purged by the system.
- The Private Medical Laboratory Utilization Discount System requires a deduction.
- Automated estimated payment(s) are recovered.
- Other deductions as requested by various ministry branches.

This amount is always negative and is deducted from the total remittance payable. If the reduction exceeds the total remittance payable, it is carried forward to the next month's remittance as a Record Type 6 or part of it (Amount Brought Forward - Reductions) with a negative value.

#### **Transaction Code 40 - Payment is created when:**

- A capitation, premium, or administration payment is required.
- A summary payment or special payment is required.

This amount is always positive and is added to the total remittance payable. Transaction Code 40 is also used to identify RMB accounting transactions.

#### **Transaction Code 50 Estimated Payment for Unprocessed Claims is created when:**

• Claims submitted prior to cut-off do not get fully processed for payment (e.g. Automated Estimated Payments).

This amount is always positive and is added to the total remittance payable.

<!-- page 106 -->

## <span id="page-105-0"></span>**5.8. Remittance Advice Explanatory Codes**

[Find a full list of Remittance Advice Explanatory Codes by visiting our website](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/remittance_advice_explanatory_codes.pdf)

## **5.9. Generic Governance Report**

A Governance Summary Report is generated monthly for all governances that are eligible to use Medical Claims Electronic Data Transfer (MCEDT).

Each of the governance will receive a Governance Summary Report that includes the name and billing number of each affiliated group and the fee for service conversion amount paid to the governance for that month.

For governances that have opted to receive their report at a solo summary level, they will receive a Governance Summary Report as well as a Governance Detail Report which provides a breakdown for each affiliated group including the name and billing number of each affiliated physician within the group and the fee for service conversion amount paid to the governance for that month.

For governances that have opted to receive their report at a group summary level, but some of their affiliated groups have opted for solo level remittance advice, they will receive the Governance Summary Report as well as a Governance Detail Report for each group with a solo level remittance advice.

The following report record layouts are for all generic governance reports including but not limited to Academic Health Science Centres (AHSC), Northern Specialists (NS), Medical Oncology (MO), and Southeastern Ontario Academic Medical Organization (SEAMO).

<!-- page 107 -->

### **File Header Record – Generic Governance Report**

#### **Governance Fixed Payment**

| Field Name                   | Field Start<br>Position | Field Length | Format | Field<br>Description                                                                               |
|------------------------------|-------------------------|--------------|--------|----------------------------------------------------------------------------------------------------|
| Transaction ID               | 1                       | 2            | X      | Always A1                                                                                          |
| Record ID<br>Type            | 3                       | 1            | X      | Always F                                                                                           |
| Reserved for MOH Use         | 4                       | 1            | X      |                                                                                                    |
| Governance #                 | 5                       | 4            | X      |                                                                                                    |
| Reserved for MOH Use         | 9                       | 16           | X      |                                                                                                    |
| Reserved for MOH Use         | 9                       | 16           | X      |                                                                                                    |
| Governance<br>Name           | 15                      | 75           | X      | Name of<br>Governance as<br>registered.                                                            |
| Sign Field                   | 90                      | 1            | X      | Space if Total<br>Amount<br>Payable is<br>positive.                                                |
|                              |                         |              |        | Negative (-) sign if Total Amount Payable is negative.                                             |
| Monthly<br>Payment<br>Amount | 91                      | 11           | N      | Nine (9) digits<br>to the left of the<br>decimal and<br>two (2) digits<br>right of the<br>decimal. |
| Reserved for MOH Use         | 102                     | 5            |        |                                                                                                    |
| Reporting Date               | 107                     | 6            | X      | Year and<br>Month.                                                                                 |
| Tech Spec<br>Release         | 113                     | 3            | X      | VO1                                                                                                |

<!-- page 108 -->

| Field Name           | Field Start<br>Position | Field Length | Format | Field<br>Description |
|----------------------|-------------------------|--------------|--------|----------------------|
| Reserved for MOH Use | 116                     | 20           | X      |                      |

<!-- page 109 -->

### **Governance Conversion Detail**

| Field Name                              | Field Start<br>Position | Field Length | Format | Field Description                                                                               |
|-----------------------------------------|-------------------------|--------------|--------|-------------------------------------------------------------------------------------------------|
| Transaction ID                          | 1                       | 2            | X      | Always A2                                                                                       |
| Record ID Type                          | 3                       | 1            | X      |                                                                                                 |
| Reserved for<br>MOH Use                 | 4                       | 1            | X      |                                                                                                 |
| Group Billing<br>Number                 | 5                       | 4            | X      |                                                                                                 |
| Solo Billing<br>Number                  | 9                       | 6            | X      |                                                                                                 |
| Full Name                               | 15                      | 75           | X      | Name of<br>Governance.                                                                          |
| Sign Field                              | 90                      | 1            | X      | Space if Total<br>Amount Payable<br>is positive.                                                |
|                                         |                         |              |        | Negative (-) sign if<br>Total Amount<br>Payable is<br>negative.                                 |
| Conversion<br>Payment<br>Amount         | 91                      | 11           | N      | Nine (9) digits to<br>the left of the<br>decimal and two<br>(2) digits right of<br>the decimal. |
| Conversion<br>Percentage                | 102                     | 5            | N      |                                                                                                 |
| Approved<br>Claims Amount<br>Sign Field | 107                     | 1            | X      | Space if Total<br>Amount Payable<br>is positive.                                                |
|                                         |                         |              |        | Negative (-) sign if<br>Total Amount<br>Payable is<br>negative.                                 |
| Approved<br>Claims Amount               | 108                     | 11           | N      | Nine (9) digits to<br>the left of the<br>decimal and two<br>(2) digits right of<br>the decimal. |

<!-- page 110 -->

| Field Name              | Field Start<br>Position | Field Length | Format | Field Description |
|-------------------------|-------------------------|--------------|--------|-------------------|
| Reserved for<br>MOH Use | 119                     | 17           | X      |                   |

<!-- page 111 -->

### **Governance Total Conversion Payment**

| Field Name                               | Field Start<br>Position | Field Length | Format | Field<br>Description                                                                               |
|------------------------------------------|-------------------------|--------------|--------|----------------------------------------------------------------------------------------------------|
| Transaction ID                           | 1                       | 2            | X      | Always A3                                                                                          |
| Record ID<br>Type                        | 3                       | 1            | X      | Always C                                                                                           |
| Reserved for MOH Use                     | 4                       | 86           | X      |                                                                                                    |
| Sign Field                               | 90                      | 1            | X      | Space if Total<br>Amount<br>Payable is<br>positive.                                                |
|                                          |                         |              |        | Negative (-)<br>sign if Total<br>Amount<br>Payable is<br>negative.                                 |
| Total<br>Conversion<br>Payment<br>Amount | 91                      | 11           | N      | Nine (9) digits<br>to the left of the<br>decimal and<br>two (2) digits<br>right of the<br>decimal. |
| Reserved for MOH Use                     | 102                     | 34           | X      |                                                                                                    |

<!-- page 112 -->

### **Governance Total Payment**

| Field Name              | Field Start<br>Position | Field Length | Format | Field<br>Description                                                                               |
|-------------------------|-------------------------|--------------|--------|----------------------------------------------------------------------------------------------------|
| Transaction ID          | 1                       | 2            | X      | Always A4                                                                                          |
| Record ID<br>Type       | 3                       | 1            | X      | Always T                                                                                           |
| Reserved for MOH Use    | 4                       | 86           | X      |                                                                                                    |
| Sign Field              | 90                      | 1            | X      | Space if Total<br>Amount<br>Payable is<br>positive.                                                |
|                         |                         |              |        | Negative (-) sign if Total Amount Payable is negative.                                             |
| Total Payment<br>Amount | 91                      | 11           | N      | Nine (9) digits<br>to the left of the<br>decimal and<br>two (2) digits<br>right of the<br>decimal. |
| Reserved for MOH Use    | 102                     | 34           | X      |                                                                                                    |

<!-- page 113 -->

## **Chapter 6 Rejection Conditions**

<!-- page 114 -->

## **6. Rejection Conditions**

## **6.1. Correction of Errors**

An entire batch or file may be rejected; consequently, it is recommended that batches be maintained at a manageable size (i.e., batches should not exceed 500 claims).

Rejected individual claims/items to be corrected by the health care provider will appear on an Error Report with the appropriate error code(s). Once corrected, the claims may be resubmitted on a subsequent EI file.

## <span id="page-113-0"></span>**6.2. Rejection Categories**

Claims data in electronic input form may be subject to rejection by the ministry at three levels:

- Rejection of entire file submission
- Rejection of batch within a file
- Rejection of a claim within a batch

Warning messages will be issued when the fields designated as fillers are not spaces.

### **Rejection of Entire Submission**

The entire unprocessed file will be returned to the originator if any of the following conditions exist:

- 1.1 Not an acceptable media type
- 1.2 Not readable
- 1.3 First record in the file is not a Batch Header Record
- 1.4 Data records not 79 bytes
- 1.5 Record too long / Record too short

<!-- page 115 -->

The Claim File Reject Message identifies the file rejected and the reasons for rejection.

File reject messages are sent with a file subject of "Mail File Reject". These messages have a filename in the following format:

| X        | Month        | File Number                                                                                                    | Sequence Number |
|----------|--------------|----------------------------------------------------------------------------------------------------------------|-----------------|
| Example: | XA000001.123 |                                                                                                                |                 |
| Field 1  |              | X is a constant used to identify the File Reject Message                                                       |                 |
| Field 2  |              | Alpha representation for current processing cycle                                                              |                 |
|          | (e.g.        | A for January, B for February)                                                                                 |                 |
| Field 3  |              | Sequential six-digit file number that indicates the position of<br>the file sending container (e.g.<br>000001) |                 |
| Field 4  |              | Three digit sequence number that indicates the container the file<br>was delivered in (e.g.<br>123)            |                 |

The File Reject Message consists of two record types of 118 characters each: M01 Message Record 1 and M02 Message Record 2.

<!-- page 116 -->

### Reject Message Record 1 (MO1) Claims File

Occurs **once** per message

| Field Name               | Field Start<br>Position | Field<br>Length | Format | Field<br>Description                                                            |
|--------------------------|-------------------------|-----------------|--------|---------------------------------------------------------------------------------|
| Transaction Identifier   | 1                       | 01              | X      | Always 'M'                                                                      |
| Message<br>Reason        | 4                       | 20              | X      | Reason for file reject                                                          |
| Invalid Record<br>Length | 24                      | 05              | X      | Actual record length submitted                                                  |
| Message Type             | 29                      | 03              | X      | Always to indicate that the first record on the file was not an HEB record      |
| Reserved for MOH Use     | 32                      | 01              | X      | Spaces                                                                          |
| Filler                   | 33                      | 07              | X      | Always<br>RECORD=                                                               |
| Record Image             | 40                      | 37              | X      | First 37<br>characters of<br>the first record<br>in the rejected<br>claims file |
| Reserved for MOH Use     | 77                      | 42              | X      | Spaces                                                                          |

<!-- page 117 -->

### Reject Message Record 2 (MO2) Claims File

Occurs **once** per message

| Field Name                | Field Start<br>Position | Field<br>Length | Format | Field<br>Description                                                         |
|---------------------------|-------------------------|-----------------|--------|------------------------------------------------------------------------------|
| Transaction<br>Identifier | 1                       | 1               | X      | Always 'M'                                                                   |
| Record<br>Identifier      | 2                       | 2               | X      | Always '02'                                                                  |
| Filler                    | 4                       | 5               | X      | Always FILE:                                                                 |
| Provider File<br>Name     | 9                       | 12              | X      | The file name used to submit the file                                        |
| Filler                    | 21                      | 5               | X      | Always DATE:                                                                 |
| Mail File Date            | 26                      | 8               | D      | Date file was<br>uploaded to the<br>MCEDT<br>service, in<br>format<br>HHMMSS |
| Filler                    | 34                      | 5               | X      | Always TIME=                                                                 |
| Mail File Time            | 39                      | 6               | T      | Time file was uploaded to the MCEDT service in format HHMMSS                 |
| Filler                    | 45                      | 6               | X      | Always<br>PDATE:                                                             |
| Process Date              | 51                      | 8               | D      | Date file was processed by MOH in format YYYYMMDD                            |
| Reserved for MOH Use      | 59                      | 60              | X      | Spaces                                                                       |

<!-- page 118 -->

#### **Rejection of a Batch**

Batches will be rejected to the Batch Edit Report if any of the following error conditions occur:

- FIRST REC ON FILE NOT BATCH HDR
- INVALID DIST CODE ON BATCH HDR
- NO CLAIMS ENCOUNTERED ON FILE
- CLM HDR1 DOES NOT FOLLOW BATCH HEADER
- TRAILER RECORD MISSING
- BATCH HEADER MISSING
- CLM HDR2 REC NOT AFTER REC TYPE H
- TRANSACTION IDENTIFIER MUST BE HE
- RECORD IDENTIFIER MUST BE B, H, R, T, E
- INVALID COUNTS IN TRAILER RECORD
- GROUP# MISSING OR NOT ZEROS
- PROVIDER# MISSING
- GROUP/PROVIDER# BOTH MISSING OR ZEROS
- CREATION DATE INVALID OR NOT YYYYMMDD
- GROUP/PROVIDER NOT APPROVED FOR MRI
- GROUP/PROVIDER OPERATOR NUMBER INVALID
- ITEM REC NOT AFTER REC TYPE H, R OR T
- SOLO PROVIDER NOT APPROVED FOR MRI
- CLM HDR1 NOT AFTER REC TYPE B, OR T
- INVALID CREATION DATE NOT NUMERIC
- TRAILER REC NOT AFTER REC TYPE T
- CREATION DATE>SYSTEM DATE
- GROUP/PROVIDER NOT APPROVED FOR MCEDT
- UNSUPPORTED TECH SPEC REL. IDENTIFIER

#### **Note:**

Whenever a large number of claims are submitted in a single batch there is the possibility that the entire submission may reject due to any of the reasons listed above. We recommend that you attempt to maintain the batch input to a manageable size (e.g. no more than 500 claims per batch).

<!-- page 119 -->

### **Claims Batch Edit Report**

The Claims Batch Edit Report acknowledges receipt of each batch in a claims file and notes if the batch was accepted or rejected.

Claims Batch Edit Reports are sent with a file subject of Batch Edit. These messages have a filename in the following format.

| B        | Month<br>Code          | File Number                                                               | Sequence Number |
|----------|------------------------|---------------------------------------------------------------------------|-----------------|
| Example: | BA00001.123            |                                                                           |                 |
| Field 1  |                        | B is a constant used to identify the Claims Batch Edit Report             |                 |
| Field 2  |                        | Alpha representation for current processing cycle                         |                 |
|          | (e.g.                  | A for January, B for February)                                            |                 |
| Field 3  | (e.g.<br>00001)        | Sequential five-digit batch control number assigned by the ministry       |                 |
| Field 4  | was delivered in (e.g. | Three digit sequence number that indicates the container the file<br>123) |                 |

<!-- page 120 -->

### **Batch Edit Report Record – Claims File**

Consists of One Record Type of 132 Characters

| Field Name                          | Field Start<br>Position | Field<br>Length | Format | Field<br>Description                                                                  |
|-------------------------------------|-------------------------|-----------------|--------|---------------------------------------------------------------------------------------|
| Transaction<br>Identifier           | 1                       | 2               | X      | Always 'HB'                                                                           |
| Record<br>Identifier                | 3                       | 1               | X      | Always '1'                                                                            |
| Tech. Spec<br>Release<br>Identifier | 4                       | 3               | X      | Always 'V03'                                                                          |
| Batch Number                        | 7                       | 5               | X      | A number assigned by ministry                                                         |
| Operator<br>Number                  | 12                      | 6               | X      | From batch header record                                                              |
| Batch Create<br>Date                | 18                      | 8               | D      | From batch header record format YYYYMMDD                                              |
| Batch<br>Sequence<br>Number         | 26                      | 4               | X      | From batch header record                                                              |
| Micro Start                         | 30                      | 11              | X      | Assigned by ministry: identifies the first record in a batch, blank if batch rejected |

<!-- page 121 -->

| Field Name         | Field Start<br>Position | Field<br>Length | Format | Field<br>Description                                                                 |
|--------------------|-------------------------|-----------------|--------|--------------------------------------------------------------------------------------|
| Micro End          | 41                      | 5               | X      | Assigned by ministry: identifies the last record in a batch, blank if batch rejected |
| Micro Type         | 46                      | 7               | X      | Always<br>'HCP/WCB' or<br>'RMB'                                                      |
| Group Number       | 53                      | 4               | X      | From batch header record                                                             |
| Provider<br>Number | 57                      | 6               | X      | From batch header record                                                             |

<!-- page 122 -->

### **Batch Edit Report Record – Claims File**

Consists of One Record Type of 132 Characters

| Field Name            | Field Start<br>Position | Field<br>Length | Format | Field<br>Description                                                                                                                                                                                                              |
|-----------------------|-------------------------|-----------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Number of<br>Claims   | 63                      | 5               | X      | Total number of claims in the batch as calculated by the ministry – see Note 1                                                                                                                                                    |
| Number of<br>Records  | 68                      | 6               | X      | Total number of records in the batch as calculated by the ministry                                                                                                                                                                |
| Batch Process<br>Date | 74                      | 8               | D      | Date batch was processed by MOH format YYYYMMDD                                                                                                                                                                                   |
| Edit Message          | 82                      | 40              | X      | 'BATCH TOTALS' left justified in the field to indicate an accepted batch or blank\nif a sub-total line or 'R' at position 40 to\nindicate a rejected batch,\npreceded by a reason for the batch rejection – see Note 1 and Note 3 |
| Reserved for MOH Use  | 122                     | 11              | X      | Spaces                                                                                                                                                                                                                            |

<!-- page 123 -->

#### **Note 1**

Batch edit reports for accepted batches which contain both HCP/WCP and RMB claims will show three lines:

- one line with HCP/WCB totals
- one line with RMB totals
- one line with batch totals

#### **Note 2**

Record count will be zeros if it is a sub-total record**.**

#### **Note 3**

When a batch has an error, two or more records will be produced. One record for each error encountered will indicate an error message and the claim and record counts pointing to the error position within the batch. The last record will indicate 'BATCH TOTALS' with a count of the total claims and total records within the batch.

### **Rejection of a Claim**

Claims within a batch will be rejected to the Claims Error Report for any of the following reasons:

- Missing/invalid data as per the field description specified in this manual (error code(s) prefixed with V)
- Ineligible patient/health care provider data (error code(s) prefixed with E)
- Missing/invalid data as specified in the Schedules of Benefits (error code(s) prefixed with A)

#### **Note:**

Once corrected, these claims may be resubmitted for payment on a subsequent file.

<!-- page 124 -->

#### **Claims Error Report**

The Claims Error Report lists rejected claims, with the appropriate error codes, for correction. These claims are deleted from the ministry's system and must be corrected and resubmitted in order to be considered for payment.

Claim Error Reports will be sent with a file subject of Error Reports. These messages will have a filename in the following format.

#### **E/F Month Code Provider, Group or Operator Number Sequence Number**

| Example: | EA123456.123 or EA1234.123 or FA123456.123                                                          |
|----------|-----------------------------------------------------------------------------------------------------|
| Field 1  | E identifies Regular Claims Error Report                                                            |
|          | F identifies Individual Claims Error Report Extract                                                 |
| Field 2  | Alpha representation for current processing cycle                                                   |
|          | (e.g.<br>A for January, B for February)                                                             |
| Field 3  | Health care provider's solo provider numbers or registered group<br>(e.g.<br>123456 or 1234)        |
| Field 4  | Three digit sequence number that indicates the container the file<br>was delivered in (e.g.<br>123) |

<!-- page 125 -->

The Claims Error Report consists of 6 record types of 79 characters:

HX1 Group/Provider Header Record

HXH Claims Header 1 Record

HXR Claims Header 2 Record (RMB claims only)

HXT Claim Item Record

HX8 Explan Code Message Record (optional)

HX9 Group/Provider Trailer Record

#### **Note:**

- Typically there is one HX1 record per individual solo provider or one HX1 for each member of a group. The HX1 record will precede one or more rejected claim records for that individual. However, if within a group of rejected claims for a particular provider the SPECIALTY CODE changes, then another HX1 record is created to show the different specialty code.
- HXH records will be created for each claim. HXH and HXR records will be created for RMB claims.
- HXT records will be created for each item within the claim. The error report explanatory code will be added to the HXT record and HX8 records will carry the explanatory code description. From one to four HX8 message records will be present if there is an explanatory code on the item level record.
- There will only be one HX9 (trailer) record created for each unique group/provider number that appears in the file. If a provider has rejected claims under two specialties, even though there will be two HX1 records (as noted above), only one HX9 record will be produced.

<!-- page 126 -->

### Error Report Header Record (HX1)

| Field Name                          | Field Start<br>Position | Field<br>Length | Format | Field<br>Description                                |
|-------------------------------------|-------------------------|-----------------|--------|-----------------------------------------------------|
| Transaction<br>Identifier           | 1                       | 2               | X      | Always 'HX'                                         |
| Record<br>Identifier                | 3                       | 1               | X      | Always '1'                                          |
| Tech. Spec<br>Release<br>Identifier | 4                       | 3               | X      | Always 'V03'                                        |
| MOH Office<br>Code                  | 7                       | 1               | A      | 'A', 'B', 'C', 'H',<br>'K', 'L', 'M', 'S'<br>OR 'T' |
| Reserved for MOH Use                | 8                       | 10              | X      | Spaces                                              |
| Operator<br>Number                  | 18                      | 6               | x      | From batch<br>header                                |
| Group Number                        | 24                      | 4               | X      | From batch<br>header                                |
| Provider<br>Number                  | 28                      | 6               | x      | From batch<br>header                                |
| Specialty Code                      | 34                      | 2               | ×      | From batch<br>header                                |
| Station<br>Number                   | 36                      | 3               | ×      | Ministry<br>assigned                                |
| Claim Process<br>Date               | 39                      | 8               | D      | Date claim was processed                            |
| Reserved for MOH Use                | 47                      | 33              | X      | Spaces                                              |

<!-- page 127 -->

Error Report Claim Header 1 Record (HXH)
Multiple Records Occurs Once for Each Claim in a File

| Field Name                      | Field Start<br>Position | Field<br>Length | Format | Field<br>Description |
|---------------------------------|-------------------------|-----------------|--------|----------------------|
| Transaction<br>Identifier       | 1                       | 2               | X      | Always 'HX'          |
| Record<br>Identifier            | 3                       | 1               | X      | Always 'H'           |
| Health Number                   | 4                       | 10              | X      | From claim<br>header |
| Version Code                    | 14                      | 2               | X      | From claim<br>header |
| Patient<br>Birthdate            | 16                      | 8               | X      | From claim<br>header |
| Accounting<br>Number            | 24                      | 8               | X      | From claim<br>header |
| Payment<br>Program              | 32                      | 3               | X      | From claim<br>header |
| Payee                           | 35                      | 1               | x      | From claim<br>header |
| Referring<br>Provider<br>Number | 36                      | 6               | X      | From claim<br>header |
| Master Number                   | 42                      | 4               | X      | From claim<br>header |
| Patient<br>Admission<br>Date    | 46                      | 8               | x      | From claim<br>header |
| Referring<br>Lab Licence        | 54                      | 4               | X      | From claim<br>header |

<!-- page 128 -->

| Field Name                       | Field Start<br>Position | Field<br>Length | Format | Field<br>Description |
|----------------------------------|-------------------------|-----------------|--------|----------------------|
| Service<br>Location<br>Indicator | 58                      | 4               | X      | From claim<br>header |
| Reserved for MOH Use             | 62                      | 3               | ×      | Spaces               |

<!-- page 129 -->

#### **Error Report Claim Header 1 Record (HXH)**

Multiple Records Occurs Once for Each Claim in a File

| Field Name   | Field Start<br>Position | Field<br>Length | Format | Field<br>Description     |
|--------------|-------------------------|-----------------|--------|--------------------------|
| Error Code 1 | 65                      | 3               | ×      | Refer to error code list |
| Error Code 2 | 68                      | 3               | X      | Refer to error code list |
| Error Code 3 | 71                      | 3               | X      | Refer to error code list |
| Error Code 4 | 74                      | 3               | x      | Refer to error code list |
| Error Code 5 | 77                      | 3               | x      | Refer to error code list |

<!-- page 130 -->

#### **Error Report Claim Header 2 Record (HXR)**

RMB Claims Only – Occurs Once Per Each RMB Claim

| Field Name                | Field Start<br>Position | Field<br>Length | Format | Field<br>Description   |
|---------------------------|-------------------------|-----------------|--------|------------------------|
| Transaction<br>Identifier | 1                       | 2               | X      | Always 'HX'            |
| Record<br>Identifier      | 3                       | 1               | x      | Always 'R'             |
| Registration<br>Number    | 4                       | 12              | X      | From claim<br>header 2 |
| Patient's Last<br>Name    | 16                      | 9               | X      | From claim<br>header 2 |
| Patient's First<br>Name   | 25                      | 5               | X      | From claim<br>header 2 |
| Patient Sex               | 30                      | 1               | X      | From claim<br>header 2 |
| Province Code             | 31                      | 2               | x      | From claim<br>header 2 |
| Reserved for MOH Use      | 33                      | 32              | x      | Spaces                 |
| Patient Sex               | 30                      | 1               | X      | From claim<br>header 2 |
| Province Code             | 31                      | 2               | X      | From claim<br>header 2 |
| Reserved for MOH Use      | 33                      | 32              | X      | Spaces                 |
| Patient Sex               | 30                      | 1               | X      | From claim<br>header 2 |
| Province Code             | 31                      | 2               | X      | From claim<br>header 2 |

<!-- page 131 -->

### **Error Report Item Record (HXT)**

Multiple Records Occurs Once for Each Item in a Claim

| Field Name             | Field Start<br>Position | Field<br>Length | Format | Field<br>Description          |
|------------------------|-------------------------|-----------------|--------|-------------------------------|
| Transaction Identifier | 1                       | 2               | X      | Always 'HX'                   |
| Record<br>Identifier   | 3                       | 1               | X      | Always 'T'                    |
| Service Code           | 4                       | 5               | X      | From claim item record        |
| Reserved for MOH Use   | 9                       | 2               | X      | Spaces                        |
| Fee Submitted          | 11                      | 6               | X      | From claim item record        |
| Number of<br>Services  | 17                      | 2               | X      | From claim item record        |
| Service Date           | 19                      | 8               | X      | From claim item record        |
| Diagnostic<br>Code     | 27                      | 4               | X      | From claim item record        |
| Reserved for MOH Use   | 31                      | 32              |        | Spaces                        |
| Explan Code            | 63                      | 2               |        | Error report explanation code |
| Error Code 1           | 65                      | 3               | X      | Refer to error code list      |
| Error Code 2           | 68                      | 3               | X      | Refer to error code list      |
| Error Code 3           | 71                      | 3               | X      | Refer to error code list      |
| Error Code 4           | 74                      | 3               | X      | Refer to error code list      |

<!-- page 132 -->

| Field Name   | Field Start<br>Position | Field<br>Length | Format | Field<br>Description     |
|--------------|-------------------------|-----------------|--------|--------------------------|
| Error Code 5 | 77                      | 3               | ×      | Refer to error code list |

<!-- page 133 -->

#### **Error Report Explanation Code Message Record (HX8)**

Optional – Occurs 1 to 4 Times Per Claim Item

| Field Name                | Field Start<br>Position | Field<br>Length | Format | Field<br>Description          |
|---------------------------|-------------------------|-----------------|--------|-------------------------------|
| Transaction<br>Identifier | 1                       | 2               | X      | Always 'HX'                   |
| Record<br>Identifier      | 3                       | 1               | ×      | Always '8'                    |
| Explan Code               | 4                       | 2               | X      | Error report explanatory code |
| Explan<br>Description     | 6                       | 55              | X      | Explanatory code description  |
| Reserved for MOH Use      | 61                      | 19              | X      | Spaces                        |

<!-- page 134 -->

### **Error Report Trailer Record (HX9)**

Occurs Once Per File or Once Per Provider for Groups

| Field Name             | Field Start<br>Position | Field<br>Length | Format | Field<br>Description |
|------------------------|-------------------------|-----------------|--------|----------------------|
| Transaction Identifier | 1                       | 2               | X      | Always 'HX'          |
| Record<br>Identifier   | 3                       | 1               | X      | Always '9'           |
| Header 1<br>Count      | 4                       | 7               | N      | Count of HXH records |
| Header 2<br>Count      | 11                      | 7               | N      | Count of HXR records |
| Item Count             | 18                      | 7               | N      | Count of HXT records |
| Message<br>Count       | 25                      | 7               | N      | Count of HX8 records |
| Reserved for MOH Use   | 32                      | 48              | X      | Spaces               |

<!-- page 135 -->

### **Error Report Samples for Solo Providers**

The following sample shows two rejected claims for the same provider. The first claim has two items. The second claim is an RMB claim that has one item.

- HX1 Group/Provider Header Record
- HXH Claim Header 1
- HXT Claim Item
- HX8 Explan Code Message Record
- HX8 Explan Code Message Record
- HXT Claim Item
- HX8 Explan Code Message Record
- HXH Claim Header 1
- HXR Claim Header 2
- HXT Claim Item
- HX8 Explan Code Message Record
- HX8 Explan Code Message Record
- HX8 Explan Code Message Record
- HX8 Explan Code Message Record
- HX9 Group/Provider Trailer Record

<!-- page 136 -->

#### **Error Report Samples for Group Providers**

The following sample shows three rejected claims for two different providers. The first provider has one claim that has two items. The second provider has an RMB claim with one item under one specialty and a second claim with one item under another specialty.

- HX1 Group/Provider Header Record
- HXH Claim Header 1
- HXT Claim Item
- HX8 Explan Code Message Record
- HX8 Explan Code Message Record
- HXT Claim Item
- HX8 Explan Code Message Record
- HX9 Group/Provider Trailer Record
- HX1 Group/Provider Header Record
- HXH Claim Header 1
- HXR Claim Header 2
- HXT Claim Item
- HX8 Explan Code Message Record
- HX8 Explan Code Message Record
- HX8 Explan Code Message Record
- HX8 Explan Code Message Record
- HX1 Group/Provider Header Record (change in specialty)
- HXH Claim Header 1
- HXT Claim Item
- HX8 Explan Code Message Record
- HX9 Group/Provider Trailer Record

<!-- page 137 -->

## **6.3. Error Report Explanatory Message Codes**

[Find a full list of Error Report Explanatory Codes/Error Report Messages](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/error_report_explanatory_codes.pdf) by visiting [our website](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/error_report_explanatory_codes.pdf)

## **6.4. Error Report Rejection Conditions – Error Codes**

[Find a full list of Error Report Rejection Conditions/Error Codes](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/error_report_rejection_conditions.pdf) by visiting our [website](https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/error_report_rejection_conditions.pdf)

<!-- page 138 -->

## **Chapter 7 Health Card Magnetic Stripe Specifications**

<!-- page 139 -->

## <span id="page-138-0"></span>**7. Health Card Magnetic Stripe Specifications**

## **7.1. Health Card Types**

<!-- page 140 -->

### **Health Card Magnetic Stripe Specifications**

- 1. Health Number
- 2. Name
- 3. OHIP Number
- 4. Expiry date of coverage (month/year) not on all cards
- 5. Version Code
- 6. Health 65 Indicator signifies eligibility for Ontario Drug Benefit (available only in Ontario)
- 7. Date of Birth
- 8. Sex

Cards must be signed. Red cards are signed on the back while a photo card has a digitized signature on the front.

<!-- page 141 -->

## **7.2. Magnetic Stripe Specifications for Photo Health Card**

**Track I** Recording density 210 bpi 7 bits per character, 79 alphanumeric characters

| Field | Field Name                                   | Size | Comments/Values                               |  |
|-------|----------------------------------------------|------|-----------------------------------------------|--|
| 1     | Start Sentinel                               | 1    | Value = "%"                                   |  |
| 2     | Format Code                                  | 1    | Value = "b"                                   |  |
| 3     | Issuer Identification                        | 6    | Value = "610054"                              |  |
| 4     | Health Number                                | 10   |                                               |  |
| 5     | Field Separator                              | 1    | Value = "^"                                   |  |
| 6     | Name                                         | 26   | As per ISO standards.<br>Separated by "/"     |  |
| 7     | Field Separator                              | 1    | Value = "^"                                   |  |
| 8     | Expiry Date                                  | 4    | YYMM or zero filled                           |  |
| 9     | Interchange Code                             | 1    | 7                                             |  |
| 10    | Service Code                                 | 2    | Value = "99"                                  |  |
| 11    | Sex                                          | 1    | 1 = Male<br>2 = Female                        |  |
| 12    | Date of Birth                                | 8    | YYYYMMDD                                      |  |
| 13    | Card Version Number                          | 2    | XX (may be blank)                             |  |
| 14    | First Name-Short                             | 5    | First 5 characters of first or<br>middle name |  |
| 15    | Issue Date                                   | 6    | YYMMDD                                        |  |
| 16    | Language Preference                          | 2    | 01=EN<br>02=FR                                |  |
| 17    | End Sentinel                                 | 1    | Value = "?"                                   |  |
| 18    | Longitudinal<br>Redundancy Check<br>(Parity) | 1    | As per ISO standards                          |  |

<!-- page 142 -->

Magnetic stripe of original photo health card

Magnetic stripe of the enhanced photo health

<!-- page 143 -->

**Track II** Recording density 75 bpi 5 bits per character, 40 numeric characters

| Field | Field Name                                   | Size                     | Comments/Values         |  |
|-------|----------------------------------------------|--------------------------|-------------------------|--|
| 1     | Start Sentinel                               | 1                        | Value = ";"             |  |
| 2     | Issuer Identification                        | 6                        | Value = "610054"        |  |
| 3     | Health Number                                | 10                       |                         |  |
| 4     | Field Separator                              | 1                        | Value = "="             |  |
| 5     | Expiry Date                                  | 4<br>YYMM or zero filled |                         |  |
| 6     | Interchange Code                             | 1                        | Value = "7"             |  |
| 7     | Service Code                                 | 2                        | Value = "99             |  |
| 8     | Filler                                       | 4                        | Value = "0000"          |  |
| 9     | Card Type                                    | 1                        | 1 = REG<br>2 = 65       |  |
| 10    | OHIP Number<br>8                             |                          | Number or<br>"00000000" |  |
| 11    | End Sentinel                                 | 1                        | Value = "?"             |  |
| 12    | Longitudinal Redundancy<br>Check<br>(Parity) | 1                        | As per ISO standards    |  |

For the Expiry Date on Track I & II and the Issue Date on Track I the year remains as a two digit character:

- if the year is 30 or less, then the century is "20"
- if the year is greater than 30, then the century is "19"

#### Example

| Field       | Value   | Calendar Date |
|-------------|---------|---------------|
| Expiry Date | 3001    | =<br>203001   |
| Expiry Date | 2901    | =<br>202901   |
| Expiry Date | 3101    | =<br>193101   |
| Issue Date  | 000101  | =<br>20000101 |
| Issue Date  | 980101  | =<br>19980101 |
| Issue Date  | 8901010 | =<br>19890101 |

<!-- page 144 -->

**Track III** Recording density 210 bpi 5 bits per 980 characters, 107 numeric characters

| Field | Field Name                                | Size | Comments/Values      |
|-------|-------------------------------------------|------|----------------------|
| 1     | Start Sentinel                            | 1    | Value = ";"          |
| 2     | Format Code                               | 2    | Value = "90"         |
| 3     | Issuer Identification<br>6                |      | Value = "610054"     |
| 4     | Health Number                             | 10   |                      |
| 5     | Field Separator                           | 1    | Value = "="          |
| 6     | Filler                                    |      | Value = "0"          |
| 7     | End Sentinel                              |      | Value = "?"          |
| 8     | Longitudinal redundancy Check<br>(Parity) | 1    | As per ISO standards |

**Note:** Track III is reserved for possible future use.

<!-- page 145 -->

## **Chapter 8 Information Management System (IMS) Connect**

<!-- page 146 -->

## **8. Inf**o**rmation Management System (IMS) Connect**

## **8.1. Information Management System Connect**

The ministry upgraded the connection software to ministry applications through IMS Listener to IMS Connect.

**Note to Programmers***:* It is recommended that existing IMS Connect applications and all new developments be upgraded to conform to the web enabled service technical specifications.

Please direct any questions you have for the integration of your computer system with the standardized, Internet based protocols, assertions and communication methods described in the web enabled service technical specifications by contacting the Service Support Contact Centre (SSCC) at:

#### **1 800-262-6524**

#### **General Message Formats**

- Both keyed and swiped transactions are supported.
- Health number/version code fields must be blank for card swipe transactions.
- Magnetic stripe fields must be blank for keyed transactions.
- All fields must be transmitted to the host.
- All fields are considered MANDATORY unless noted to be OPTIONAL.
- MANDATORY fields are subject to audit.
- Fields marked as OPTIONAL are not required for successful processing and must contain spaces if the desired information is unavailable.
- Date format is always YYYYMMDD.
- All data must be left justified.
- Input message character data may be either upper or lower case.
- Output message character data is returned in upper case only.

<!-- page 147 -->

#### **Resource Access Control Facility – Password Information**

The Resource Access Control Facility (RACF) is a software security program that resides on the MOH mainframe computer and limits a user's access to specific areas of the ministry systems and transactions.

RACF limits access to the system as well as to various levels of information on the system based on a user's need.

Passwords must be changed every 35 days and there is a restriction that a password cannot be repeated within 14 occurrences.

Client systems should not perform edits on input passwords that are sensitive to the published rules (e.g. minimum length), and must provide a facility for manually entering any arbitrary password value. Failure to do so will likely render a client system unusable at some point in time.

#### **Password Guidelines**

- Organization and/or each user registered and authorized for HCV are assigned a RACF ID and an initial password by the ministry.
- Initial passwords may be up to 8 characters long.
- An initial password is issued in an expired state and clients are required to change initial passwords prior to processing any HCV transactions.
- Subsequent passwords must be 6 to 8 characters long.
- Password changes resulting from ministry reset or revocation will be up to 8 characters long.
- Passwords must be changed every 35 days.
- The system maintains a history of the last 12 passwords and these passwords will not be permitted for re-use during the next 12 password changes.
- Passwords cannot contain your RACF ID.
- If your RACF ID is HEZZXX then these letters cannot be present in your password (e.g. HEZZXX, HEZZXX01, 01HEZZXX).
- These common 3 character abbreviations cannot appear anywhere in the password (e.g. GOV, ONT, JAN, FEB).
- The first 4 characters of the new password cannot match the first 4 characters of the current password.
- The 4th 8th characters of the new password cannot match the 4th 8th characters of the old password (e.g. OLDPASSWORD: SPSTST NEWPASSWORD: CONTST).

<!-- page 148 -->

▪ Passwords will be checked against a confidential list of passwords commonly used by computer hackers. Passwords found on the list will not be permitted.

Unsuccessful attempts to log on with a RACF ID will result in a "lock-out" from the system. A call to the Service Support Contact Centre at **1 800-262-6524** is required for a "reset".

**Note:** A password reset occurs when the ministry reverts a password back to the system default password (e.g. a user forgets the current password or a RACF ID has been revoked and then re-issued).

<!-- page 149 -->

## **8.1.1 TCP/IP Data Specifications for use with IMS Connect**

The following instructions are for use in developing the client access portion of the application used to access the HCV service using TCP/IP over the integrated network.

#### **TCP/IP Client Access Instructions for IMS Connect**

**Note:** To be used in conjunction with the TCP/IP Data Specifications on the following pages: 8.13 – 8.16.

Every transaction message begins with an IRM header segment and ends with an EOM segment.

The validation message includes the Input Transaction, whereas the other two (User ID/Password Authentication and Password Change) do not.

| Step | Name    | Description                                                                                                                                                                                                                                                     |
|------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Socket  | Obtain a socket descriptor.                                                                                                                                                                                                                                     |
| 2    | Connect | Request connection to host address.                                                                                                                                                                                                                             |
|      |         | Specific host name/URL to be provided during<br>conformance testing process.                                                                                                                                                                                    |
| 3    | Write   | Fill a character buffer with (in sequence):                                                                                                                                                                                                                     |
|      |         | 1.<br>The appropriate IRM header<br>(Note 1);                                                                                                                                                                                                                   |
|      |         | 2.<br>The input transaction record (if required)<br>(Note 2);                                                                                                                                                                                                   |
|      |         | 3.<br>The EOM segment.                                                                                                                                                                                                                                          |
| 4    | Read    | Receive response:                                                                                                                                                                                                                                               |
|      |         | If a Request Status Message (RSM) is returned it means<br>the submission was rejected, or you have used Data<br>Specification 1 or 2, which only return RSM responses<br>(refer to Health Card Validation Reference Manual,<br>Appendix A -<br>Response Codes). |
|      |         | If an HCV Output Transaction is returned, process as you<br>desire.                                                                                                                                                                                             |
|      |         | If a CSM message is received, all available output has<br>been received.                                                                                                                                                                                        |
|      |         | If an EOM message is received, output may have been<br>discarded -<br>go to step 6.                                                                                                                                                                             |

<!-- page 150 -->

| Step | Name   | Description                                                                |
|------|--------|----------------------------------------------------------------------------|
| 5    | Repeat | Repeat IDENTIFIED VOLUME USERS ONLY: repeat<br>process starting at step 3. |
| 6    | Close  | Terminate connection and release socket resources.                         |

#### Notes

- 1) Check the Validity of the User ID" "Change the Password of the User ID" or "send a Regular Validation Transaction".
- 2) Input transactions required only when a validation is being submitted.

<!-- page 151 -->

## **8.1.2 TCP/IP Socket Troubleshooting**

**Refer to the steps below before contacting Service Support Contact Centre for assistance.**

The first troubleshooting step should always be to ensure that the transaction data has been assembled correctly by referring to the IRM Header and Input Data Specification – ensure all fields are of correct width and are correctly ordered. Some troubleshooting steps are outlined below for steps 1, 2, and 3 of the TCP/IP Client Access for IMS Connect Data Specification.

| Step       | Symptom                                    | Items to Check                                                                                                                           | Follow-up                                                                                                                                                                                                    |
|------------|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1. Socket  | Unable to<br>initialize<br>socket          | Ensure:<br>Development environment<br>supports sockets<br>Required libraries and modules<br>are available in your runtime<br>environment | Address further questions to<br>vendor of development<br>environment                                                                                                                                         |
| 2. Connect | Host<br>connection<br>fails                | Ensure:<br>Client machine has active<br>network connection<br>Host address and port are<br>correctly set<br>Host is responding (ping)    | Contact your local system<br>Administrator<br>If client machine has active<br>connection, and host<br>parameters are correctly<br>set,<br>but ping<br>still fails, call<br>Service Support Contact<br>Centre |
|            | Host<br>connection<br>rejected<br>(Note 1) | Ensure:<br>User ID and password entered<br>correctly, and that password has<br>not expired<br>(Note 2)                                   | Change password, continue<br>If problem persists<br>call<br>Service Support Contact<br>Centre                                                                                                                |

<!-- page 152 -->

| Step    | Symptom                         | Items to Check                                                                                           | Follow-up                                                                                                   |
|---------|---------------------------------|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| 3. Read | Return<br>message<br>appears to | Ensure:<br>Output record is being parsed                                                                 | Ensure that client<br>application always tests<br>type of return record                                     |
|         | be<br>nonsense                  | correctly<br>Correct character set is being<br>used (IMS Connect sends and<br>receives ASCII characters) |                                                                                                             |
|         |                                 | Read buffer correctly initialized<br>between read calls                                                  |                                                                                                             |
|         |                                 | Validation returns a response<br>code greater than 90 indicating<br>system problems                      | Refer to description in<br>Health Card Validation<br>Reference Manual,<br>Appendix A –<br>Response<br>Codes |

#### Notes:

If connection is rejected, host returns a 20-byte Request Status Message (RSM), documenting the source of failure. Ensure that RSMRetCode is set to "8" then evaluate the RSMReasCode to determine the source of the error An expired password causes RSMReasCode "105"

## **8.1.3 IMS Connect Information**

**1. Check the Validity of the User ID:** Information Management System (IMS) Request Message (IRM)

| Description | Length  | Notes                                  |
|-------------|---------|----------------------------------------|
| IRMLLLL     | 4 Bytes | Set to x'00000034' (decimal 52)        |
| IRMLen      | 2 Bytes | Set to x'002C' (decimal 44)            |
| IRMRsv      | 2 Bytes | Set to x'0000' (decimal zero)          |
| IRMId       | 8 Bytes | *HCVREQ*                               |
| IRMTrnCod   | 8 Bytes | &&PWDCHK                               |
| IRMUsrID    | 8 Bytes | User ID assigned by Ministry of Health |
| IRMRsv2     | 8 Bytes | ' ' (8 blanks)                         |
| IRMPassw    | 8 Bytes | Password for the User ID above         |

**2. Change the Password of the User ID:** IMS Request Message (IRM)

<!-- page 153 -->

| Description | Length  | Notes                                                               |
|-------------|---------|---------------------------------------------------------------------|
| IRMLLLL     | 4 Bytes | Set to x'00000044'<br>(decimal 68)                                  |
| IRMLen      | 2 Bytes | Set to x'003C' (decimal 60)                                         |
| IRMRsv      | 2 Bytes | Set to x'0000' (decimal<br>zero)                                    |
| IRMRsv      | 2 Bytes | Set to x'0000' (decimal<br>zero)                                    |
| IRMId       | 8 Bytes | *HCVREQ*                                                            |
| IRMTrnCod   | 8 Bytes | &&PWDCHG                                                            |
| IRMUsrID    | 8 Bytes | User ID assigned by<br>ministry                                     |
| IRMRsv2     | 8 Bytes | ' ' (8 blanks)                                                      |
| IRMPassw    | 8 Bytes | Password for the User ID<br>above                                   |
| IRMNewPW    | 8 Bytes | A new password that is<br>either desired or mandated<br>by the host |
| IRMNwPwC    | 8 Bytes | A confirmation of the new<br>password                               |

#### **Note:**

For information on [Passwords](#page-169-0) or for [User IDs,](#page-170-0) [refer to the Glossary](#page-166-0)*.*

<!-- page 154 -->

#### **3. Send a Regular Validation Transaction:** IMS Request Message (IRM)

| Description | Length  | Notes                                  |
|-------------|---------|----------------------------------------|
| IRMLLLL     | 4 Bytes | Set to x'00000101' (decimal 257)       |
| IRMLen      | 2 Bytes | Set to x'002C' (decimal 44)            |
| IRMRsv      | 2 Bytes | Set to x'0000' (decimal zero)          |
| IRMId       | 8 Bytes | *HCVREQ*                               |
| IRMTrnCod   | 8 Bytes | RPVR0300                               |
| IRMUsrID    | 8 Bytes | User ID assigned by Ministry of Health |
| IRMRsv2     | 8 Bytes | ' ' (8 blanks)                         |
| IRMPassw    | 8 Bytes | Password for the User ID above         |

#### **4. End of Message Segment (EOM):**

| Description | Length  | Notes                    |
|-------------|---------|--------------------------|
| EOMLen      | 2 Bytes | Set to x'0004' decimal 4 |
| EOMRsv      | 2 Bytes | Reserved (x'0000')       |

### **5. Completion Status Message (CSM):**

| Description | Length  | Notes                      |
|-------------|---------|----------------------------|
| CSMLen      | 2 Bytes | Will be x'000C' decimal 12 |
| CSMRsv      | 2 Bytes | Reserved (x'0000')         |
| CSMId       | 8 Bytes | '*CSMOKY*'                 |

<!-- page 155 -->

#### **6. Request-Status Message (RSM):**

| Description | Length  | Notes                      |
|-------------|---------|----------------------------|
| RSMLen      | 2 Bytes | Will be x'0014' decimal 20 |
| RSMRsv      | 2 Bytes | Reserved (x'0000')         |
| RSMId       | 8 Bytes | '*REQSTS*'                 |
| RSMRetCod   | 4 Bytes | RSM Return Code*           |
| RSMRsnCod   | 4 Bytes | RSM Reason Code*           |

If RSMRetCod has been set to 4, the RSMRsnCod may have the following values:

| Info #200 | The password has been successfully changed. This is only returned in |
|-----------|----------------------------------------------------------------------|
|           | response to a transaction of "&&PWDCHG".                             |

Info #201 Successful sign-on (User ID and password are good). This is only returned in response to a transaction of "&&PWDCHK".

If RSMRetCod has been set to 8, the RSMRsnCod may have the following values:

| Error #1   | The transaction was not defined to IMS Connect.                                                                |
|------------|----------------------------------------------------------------------------------------------------------------|
| Error #2   | An IMS error occurred<br>and the transaction was unable to be started.                                         |
| Error #3   | The transaction failed to perform TAKESOCKET call within the 3-<br>minute timeframe.                           |
| Error #4   | The input buffer is full, as the client has sent more than 32KB of data<br>for an implicit transaction.        |
| Error #5   | An AIB error occurred when the IMS Connect tried to confirm if the<br>transaction was available to be started. |
| Error #6   | The transaction is not defined to IMS or is unavailable to be started.                                         |
| Error #7   | The IMS-request message (IRM) segment not in correct format.                                                   |
| Error #101 | User ID/Password is missing.                                                                                   |
| Error #102 | Invalid length of User ID/Group/Password data.                                                                 |
| Error #103 | User ID not defined to the system.                                                                             |
| Error #104 | Invalid password for this User ID.                                                                             |
| Error #105 | Password has expired.                                                                                          |

<!-- page 156 -->

| Error #106 | New password supplied is not a valid one.                                |
|------------|--------------------------------------------------------------------------|
| Error #107 | User ID does not belong to Group.                                        |
| Error #108 | User ID has been revoked –<br>call the Service Support Contact Centre.   |
| Error #109 | Access to Group is revoked –<br>call the Service Support Contact Centre. |
| Error #110 | Authorization error.                                                     |
| Error #111 | Internal error.                                                          |
| Error #112 | Some other error.                                                        |
| Error #114 | New password and confirmation of new password do not match.              |
| Error #115 | Internal error.                                                          |

<!-- page 157 -->

#### **TCP/IP Input Transaction**

\*Optional fields

| Description                  | Start | End | Length | Notes                                                                             |
|------------------------------|-------|-----|--------|-----------------------------------------------------------------------------------|
| EITHER<br>MOH Facility<br>ID | 34    | 40  | 07     | Represents the<br>ministry issued<br>facility or<br>provider<br>number.           |
|                              |       |     |        | At least one of<br>these fields<br>must be<br>present on all<br>transactions.     |
|                              |       |     |        | Data must be<br>left justified<br>and, if<br>necessary,<br>padded with<br>spaces. |
| OR<br>MOH Provider<br>ID     | 41    | 50  | 10     | Represents the<br>ministry issued<br>facility or<br>provider<br>number.           |
|                              |       |     |        | At least one of<br>these fields<br>must be<br>present on all<br>transactions.     |
|                              |       |     |        | Data must be<br>left justified<br>and, if<br>necessary,<br>padded with<br>spaces. |

<!-- page 158 -->

#### **TCP/IP Input Transaction (continued)**

| Description         | Start | End | Length | Notes                                                                                                                                       |
|---------------------|-------|-----|--------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Local User ID       | 51    | 58  | 08     | In the case where a client is routing through<br>another facility, the ministry assigned ID # to<br>the client will be used (HCNP # # # #). |
|                     |       |     |        | For a single hospital or provider, this will be<br>the ID assigned by the ministry (HECS # # #<br>#).                                       |
| Local Device<br>ID* | 59    | 66  | 08     | Optionally, Local Device ID may identify<br>where the transaction came from within a<br>facility (e.g.<br>Emergency Department).            |
| Client Text*        | 67    | 86  | 20     | Optionally, Client Text is echoed back<br>unedited and unchanged.                                                                           |
|                     |       |     |        | Recommended that the field include a unique<br>identifier assigned to each transaction to<br>facilitate message sequencing.                 |
| Magnetic<br>Stripe  |       |     |        | (refer to Health Card Magnetic Stripe<br>Specifications)                                                                                    |
| Track 1             | 87    | 165 | 79     | Mandatory for a card swipe transaction.                                                                                                     |
|                     |       |     |        | Ontario health cards conform to ISO 7811/12.                                                                                                |
|                     |       |     |        | Data must be left justified and if necessary,<br>padded with spaces.                                                                        |
| Track 2             | 166   | 205 | 40     | Mandatory for a card swipe transaction.                                                                                                     |
|                     |       |     |        | Ontario health cards conform to ISO 7811/12.                                                                                                |
|                     |       |     |        | Data must be left justified and if necessary,<br>padded with spaces.                                                                        |

#### **TCP/IP Output Transaction**

| Description         | Start | End | Length | Notes                        |
|---------------------|-------|-----|--------|------------------------------|
| Length              | 01    | 02  | 02     | x'0099' (153)                |
| Reserved            | 03    | 04  | 02     | x'0000' (0)                  |
| Transaction<br>Code | 05    | 13  | 09     | RPVR0300 followed by 1 space |
| Local User ID       | 14    | 21  | 08     |                              |

<!-- page 159 -->

| Description                   | Start | End | Length | Notes                                                                                                                                                                                                                                  |
|-------------------------------|-------|-----|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Local Device<br>ID            | 22    | 29  | 08     |                                                                                                                                                                                                                                        |
| Health<br>Number              | 30    | 39  | 10     |                                                                                                                                                                                                                                        |
| Version Code                  | 40    | 41  | 02     |                                                                                                                                                                                                                                        |
| Response<br>Code              | 42    | 43  | 02     | Values may be found in Health Card<br>Validation Reference Manual, Appendix A –<br>Response Codes<br>At a minimum, the Response Code numbers<br>provided in Appendix A must be echoed to<br>the<br>client for troubleshooting purposes |
| Gender Code                   | 44    | 44  | 01     | Values are M or F                                                                                                                                                                                                                      |
|                               |       |     |        | Values represent the data as retained on the<br>ministry database.                                                                                                                                                                     |
| Birth Date                    | 45    | 52  | 08     | Values represent the data as retained on the<br>ministry database.                                                                                                                                                                     |
| Expiry Date                   | 53    | 60  | 08     | Values represent the data as retained on the<br>ministry database.                                                                                                                                                                     |
| Client Text                   | 61    | 80  | 20     | Output as received on input.                                                                                                                                                                                                           |
| Last Name                     | 81    | 110 | 30     |                                                                                                                                                                                                                                        |
| First Name                    | 111   | 130 | 20     |                                                                                                                                                                                                                                        |
| Second<br>Name                | 131   | 150 | 20     |                                                                                                                                                                                                                                        |
| Redundant<br>Response<br>Code | 151   | 152 | 02     | Available for message delivery verification.                                                                                                                                                                                           |
| Carriage<br>Return            | 153   | 153 | 01     | Indicates the end of the output message.                                                                                                                                                                                               |

<!-- page 160 -->

## **8.2. GO Net TCP/IP Data Specifications for use with Information Management System (IMS) Listener**

The following instructions are for accessing the HCV TCP/IP Socket Server using GONet's Multi-Protocol Router (MPR) network.

Data lengths are indicated as H (half-word - 2 bytes), F (full-word - 4 bytes), and CL8 (8 bytes).

#### **Data Specifications**

Transaction Request Message **(TRM**):

| Description | Length | Notes                                               |
|-------------|--------|-----------------------------------------------------|
| TRMLen      | H      | Binary length inclusive (high-endian) i.e. x '001C' |
| TRMRsv      | H      | Reserved (x'0000')                                  |
| TRMRId      | CL8    | '*TRNREQ*'                                          |
| TRMTrnCod   | CL8    | 'RPVR0500'                                          |
| TRMUsrID    | CL8    | User ID assigned by Ministry of Health.             |

### End of Message Segment (**EOM**):

| Description | Length | Notes                                               |
|-------------|--------|-----------------------------------------------------|
| EOMLen      | H      | Binary length inclusive (high-endian) i.e. x '0004' |
| EOMRsv      | H      | Reserved (x'0000')                                  |

### Completion Status Message (**CSM**):

| Description | Length | Notes                                 |
|-------------|--------|---------------------------------------|
| CSMLen      | H      | Binary length inclusive (high-endian) |
| CSMRsv      | H      | Reserved                              |
| CSMId       | CL8    | '*CSMOKY*'                            |

<!-- page 161 -->

#### Request-Status Message (**RSM**):

| Description | Length | Notes                                 |
|-------------|--------|---------------------------------------|
| RSMLen      | H      | Binary length inclusive (high-endian) |
| RSMRsv      | H      | Reserved                              |
| RSMId       | CL8    | '*REQSTS*'                            |
| RSMRetCod   | F      | RSM Return Code                       |
| REMRsnCod   | F      | RSM Reason Code*                      |

<sup>\*</sup>If RSMRetCod has been set to 8, RSMRsnCod may have the following values:

| Error #1   | The transaction was not defined to the IMS Listener.                                                            |
|------------|-----------------------------------------------------------------------------------------------------------------|
| Error #2   | An IMS error occurred<br>and the transaction was unable to be started.                                          |
| Error #3   | The transaction failed to perform the TAKESOCKET call within the 3<br>minute timeframe.                         |
| Error #4   | The input buffer is full as the client has sent more than 32KB of data<br>for an implicit transaction.          |
| Error #5   | An AIB error occurred when the IMS Listener tried to confirm if the<br>transaction was available to be started. |
| Error #6   | The transaction is not defined to IMS or is unavailable to be started.                                          |
| Error #7   | The transaction-requested message (TRM) segment was not in the<br>correct format.                               |
| Error #101 | Unauthorized user or network address                                                                            |
| Error #102 | Invalid user specification                                                                                      |
| Error #110 | Authorization error                                                                                             |

<!-- page 162 -->

| Description       | Status    | Start | End | Length | Notes |
|-------------------|-----------|-------|-----|--------|-------|
| Length            | Mandatory | 01    | 02  | 02     | 9     |
| Reserved          | Mandatory | 03    | 04  | 02     | 10    |
| Transaction Code  | Mandatory | 05    | 13  | 09     | 1     |
| Health Number     |           | 14    | 23  | 10     | 2     |
| Version Code      |           | 24    | 25  | 02     | 2     |
| MOH User ID       | Mandatory | 26    | 33  | 08     | 3     |
| MOH Facility ID * | Mandatory | 34    | 40  | 07     | 4     |
| MOH Provider ID * | Mandatory | 41    | 50  | 10     | 4     |
| Local User ID     | Mandatory | 51    | 58  | 08     | 5     |
| Local Device ID   | Optional  | 59    | 66  | 08     | 6     |
| Client Text       | Optional  | 67    | 86  | 20     | 7     |
| Magnetic Stripe   |           |       |     |        |       |
| Track 1           |           | 87    | 165 | 79     | 8     |
| Track 2           |           | 166   | 205 | 40     | 8     |

#### **Notes:**

- 1. Transaction code: enter RPVR0500 followed by a space.
- 2. Health Number/Version Code must be provided for a keyed transaction and omitted for a swiped transaction. Refer to the Message Rules for more information.
- 3. MOH User ID will be the authorization ID (HECSnnnn) issued by the ministry. In the case of a network provider, this will be the same for all of the networked sites.
- \*4. MOH Facility ID and Provider ID represent the ministry's issued values. At least one of these fields must be present on all transactions. Data must be left justified and, if necessary, padded with spaces.
- 5. Local User ID should contain the client's authorization ID (HECSnnnn). In the case of a network provider, this will be the ID assigned by the ministry to client of the network provider.
- 6. Local Device ID may identify where the transaction came from within a facility (e.g. Emergency Department).

<!-- page 163 -->

- 7. Client Text is echoed back unedited and unchanged. It is recommended that the field include a unique identifier assigned to each transaction to facilitate message sequencing.
- 8. Track 1 and Track 2 are mandatory for a card swipe transaction. Ontario health cards conform to ISO 7811/12. Data must be left justified and, if necessary, padded with spaces.
- 9. Set to x'00CD'.
- 10. Set to x'0000'.

<!-- page 164 -->

#### **Output Transaction**

| Description                | Start | End | Length | Notes |
|----------------------------|-------|-----|--------|-------|
| Length                     | 01    | 02  | 02     | 9     |
| Reserved                   | 03    | 04  | 02     | 10    |
| Transaction Code           | 05    | 13  | 09     | 1     |
| Local User ID              | 14    | 21  | 08     | 2     |
| Local Device ID            | 22    | 29  | 08     | 2     |
| Health Number              | 30    | 39  | 10     | 3     |
| Version Code               | 40    | 41  | 02     | 3     |
| Response Code              | 42    | 43  | 02     | 4     |
| Sex Code                   | 44    | 44  | 01     | 5,6   |
| Birth Date                 | 45    | 52  | 08     | 6     |
| Expiry Date                | 53    | 60  | 08     | 6     |
| Client Text                | 61    | 80  | 20     | 7     |
| Last Name                  | 81    | 110 | 30     |       |
| First Name                 | 111   | 130 | 20     |       |
| Second Name                | 131   | 150 | 20     |       |
| Redundant<br>Response Code | 151   | 152 | 02     | 8     |
| Carriage Return            | 153   | 153 | 01     | 9     |

#### **Notes:**

- 1. Transaction Code: RPVR0500 followed by a space.
- 2. Health Number/Version Code must be provided for a keyed transaction and omitted for a swiped transaction. Refer to the Message Rules for more information.

<!-- page 165 -->

- 3. MOH User ID will be the authorization ID (HECSnnnn) issued by the ministry. In the case of a network provider, this will be the same for all of the networked sites.
- 4. Response code values may be found in Appendix A Response Code Descriptions.
- 5. Sex code values are M or F.
- 6. Sex code, birth date and expiry date values represent the data as retained on the ministry database.
- 7. Client Text will be output as received on input.
- 8. The Redundant Response Code is available for message delivery verification.
- 9. Carriage Return indicates the end of the output message.
- 10. Set to '0x0000'.

#### **Client Procedures**

| 1. | SOCKET           | Obtain a socket descriptor                                                                                                                                                            |
|----|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 2. | CON              | NECT<br>Request connection to server port                                                                                                                                             |
| 3. | WRITE            | Send transaction request message (TRM)                                                                                                                                                |
| 4. | WRITE<br>n times | Send one or more Health Card Validation input transactions                                                                                                                            |
| 5. | WRITE            | Send EOM segment                                                                                                                                                                      |
| 6. | READ             | Receive first response.<br>If a request status message (RSM),<br>response was rejected, go to step 8.                                                                                 |
| 7. | READ n times     | Receive a Health Card Validation output transaction unless<br>CSM or EOM is received. If a CSM, all available output has<br>been received. If an EOM, output may have been discarded. |
| 8. | CLOSE            | Terminate connection and release socket resources.                                                                                                                                    |

<!-- page 166 -->

## **Chapter 9 Glossary**

<!-- page 167 -->

## <span id="page-166-0"></span>**Glossary**

#### **Accounting Number**

An eight (8) character, alpha-numeric field which may be used by the health care provider or billing agent for claim identification. If used, this identifier will be reported on the Remittance Advice (hard copy, or EDT). This may also be identified as invoice number, provider reference number or file number.

#### **Address**

A computer system location identified by a name, number, or code label. The address can be specified by the user or by a program.

#### **ASCII File**

A file that contains data made up of ASCII characters. Each byte in the file contains one character that conforms to the standard ASCII code. Program source code, DOS batch files, macros and scripts are written as straight text and stored as ASCII files.

#### **Billing Agent**

An agent authorized by a health care provider, or a group of health care providers, to prepare their claims data for processing by the ministry and/or to reconcile payment data provided by the ministry.

#### **Communication Software**

A type of software used to establish a connection and exchange data with another computer.

#### **Facility Number**

Refer to Master Number

#### **Fee Schedule Code**

The codes appearing opposite the description of insured benefits listed in the various Ministry of Health Schedules of Benefits and Facility Fee Schedule. The instructions pertaining to its use are included in the Preambles of the Schedule of Benefits. Used inter-changeably with service code.

<!-- page 168 -->

#### **Government of Ontario Network (GONet)**

The interface designed by the Ontario Government that is used to upload and download (send/receive) files.

#### **Group Numbers**

A four (4) digit alpha-numeric ministry registration number assigned to organizations to facilitate payment consolidation.

#### **HCP Claim**

A regular in-province medical claim (includes Independent Health Facility claims).

#### **Health Care Provider**

Any provider, group, licensed laboratory, private physiotherapy facility, or independent health facility that is registered with the ministry to bill for rendering insured services.

#### **Health Care Provider Number**

The six (6) digit Ministry of Health registration number assigned to individual providers, private physiotherapy facilities, laboratory directors and independent health facility practitioners who are lawfully entitled to provide insured services.

#### **Health Encounters**

A health encounter marks the occurrence of a service by a health care provider for a patient. This service may be billable to the ministry in the format outlined in the MRI specifications section.

#### **Health Numbers**

The unique ten (10) digit individual health identification number assigned by the ministry to eligible Ontario residents.

#### **Health Reconciliation**

Health reconciliation is the Remittance Advice information supplied by the ministry in the format outlined in section 5.5, to be reconciled with claims for health encounters.

### **Independent Health Facility Number**

A four (4) digit alpha-numeric Ministry of Health registration number identifying each Independent Health Facility (IHF).

### **Independent Health Facility Practitioner Number**

A unique six (6) digit number issued by the Ministry of Health to identify persons lawfully entitled to provide insured services or assigned for non-medical operators of licensed Independent Health Facilities.

<!-- page 169 -->

#### **In-Patient Admission Date**

The date of admission for in-patients to a health care facility. Previously referred to as hospital admission date.

#### **Laboratory Director Number**

The unique six (6) digit number issued by the Ministry of Health to persons lawfully entitled to provide insured services, or the unique six (6) digit number assigned for nonmedical laboratory directors.

#### **Laboratory Licence Number**

Each licensed location of a laboratory facility is registered with the ministry and is assigned a four (4) digit registration number, which is the same as the licence number issued by the Laboratory and Diagnostics Branch.

#### **Log Off**

The process of terminating a connection with a computer system or peripheral device in an orderly fashion.

### **Log On**

The process of establishing a connection with, or gaining access to, a computer system or peripheral device.

#### **Mainframe**

A multi-user computer designed to meet the computing needs of a large organization.

#### **Manual Review Indicator**

A trigger on a Health Encounter Claim Header-1 Record, used to force review by the ministry of additional documentation related to the claim.

#### **Master Number**

A four (4) digit number assigned by the ministry to identify specific health care facilities, including hospitals and sites for mobile diagnostic IHF services.

#### **Medical Claims Electronic Data Transfer**

Medical Claims Electronic Data Transfer service is a secure method of transferring electronic files to and from an authorized MCEDT user and the ministry.

#### **Medical Consultant**

A physician or dentist employed by the Ministry of Health to adjudicate complex or independent consideration (IC) claims, to institute or advise on claims payment policy, to institute and interpret the Schedule of Benefits and to liaise with health care providers and the public.

#### **MOD 10 Check Digit**

A program check that validates health numbers.

<!-- page 170 -->

#### **Modem**

A device that allows communication between two computers through telephone lines.

#### **Modulation**

The conversion of a digital signal to its analog equivalent, especially for the purposes of transmitting signals via telecommunications.

#### **MOH Office Code**

Alpha character which represents the registered practice location of the provider as determined by the ministry.

#### **Operator Number**

A six (6) digit number assigned by the Ministry of Health to uniquely identify the processing installation used by health care providers for the EI/EO interface. Refer to Billing Agent definition for further details.

#### **Output**

A file sent from the ministry's mainframe in response to an input file.

#### <span id="page-169-0"></span>**Password**

A security tool used to identify authorized users of a computer program or computer network and to define their privileges, such as: read-only, reading and writing or file copying.

#### **Payee**

**Pay Provider** (**P**): A provider who accepts payment for insured services directly from the ministry (OPTED-IN).

**Pay Patient** (**S**): A provider who accepts payment from the patient and submits a claim to the ministry on the patient's behalf (OPTED-OUT).

### **Payment Program**

The program that is responsible for the payment of the claim (e.g. Health Claims Payment (HCP), Workers' Compensation Board (WCB) and Reciprocal Medical Billing (RMB).

#### **Peripheral**

A device, such as a printer or disk drive, connected to and controlled by a computer, but external to the computer's central processing unit (CPU).

### **Private Physiotherapy Facility (Number)**

A six (6) digit number assigned by the ministry to a facility which has been registered by the ministry to lawfully provide publicly-funded physiotherapy services.

<!-- page 171 -->

#### **Protocol**

A set of standards for exchanging information between two computer systems or two computer devices.

#### **Province Code**

A code that is required for reciprocal claims to identify the province of the patient's registration/address.

#### **Reciprocal Medical Billing Claim**

A service rendered by an Ontario health care provider to a patient registered with another provincial health plan.

#### **Referring/Requisitioning Health Care Provider**

The six-digit number of the health care provider who is referring a patient to another health care provider for consultation or who is requisitioning diagnostic services (e.g. laboratory tests).

#### **Registration Number**

The equivalent health number of residents registered in provinces other than Ontario.

#### **Report**

A printed output that usually is formatted with page numbers and headings.

### **Service Location Indicator (SLI)**

An SLI is used to identify the setting of insured diagnostic services.

### **Specialty Codes**

The two (2) numerics assigned to a provider depending on area of specialty.

#### **TCP/IP**

Transmission Control Protocol/Internet Protocol

### **Upload**

The process of sending a file to another computer.

### <span id="page-170-0"></span>**User Identification (User ID)**

Access to the MCEDT services is restricted to authorized users with the appropriate ID and password.

#### **Workplace Safety and Insurance Board (WSIB) Claim**

A claim for a service to which WSIB benefits are applicable. This board was formerly referred to as the Workers' Compensation Board (WCB).
```