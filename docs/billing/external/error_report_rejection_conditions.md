# OHIP Error Report Rejection Conditions / Error Codes

> **Source**: [https://www.health.gov.on.ca/en/pro/programs/ohip/claims_submission/error_report_rejection_conditions.pdf]
> **Fetched**: 2026-04-29
> **Pages**: 14
> **Format**: extracted text from PDF via `pypdfium2`; code/description rows have been re-aligned into GFM tables. Section headings come from the PDF's Table of Contents (multi-line wrapped headings are merged). Where a description wraps across multiple lines in the PDF, those continuations have been merged into a single cell.
> **Authoritative source**: the PDF at the URL above. If this MD and the PDF disagree, the PDF wins.

---

### Error Report Rejection Conditions / Error Codes

The following error rejection conditions / error codes will be reported on the Claims Error Report. These error codes are three characters in length.

### General error codes

| Error Code | Reason(s) for rejection |
|------------|-------------------------|
| A1A | Outside Service Period |
| A2A | Outside of Age Limit - Patient is underage or overage for this service code |
| A2B | Wrong Sex for Service - This service is not normally performed for this sex. Please check your records. |
| A3E | No such service code for date of service |
| A3F | No fee exists for this service code on this date of service |
| A3G | Fee Billed Low |
| A3H | Maximum Number Services per the Fee Schedule Master (FSM) |
| A3I | X-Ray Code - Maximum Number Services per the FSM |
| A3L | Other New Patient Fee Already Paid |
| A34 | Multiple duplicate claims |
| A36 | Claimed by Other Practioner |
| A4D | Invalid specialty for this service code |
| AC1 | Maximum reached-resubmit alternate Fee Schedule Code (FSC) |
| AC4 | Unaccepted Referral Number. Not 6 numerics Equal to the Practitioner billing number Referring number is 722900-744292 (Nurse Practitioner (NP)) and FSC is not eligible for NP referral. Referring number is 700000-722899 (Midwife (MW)) and FSC is not eligible for MW referral. |
| AD3 | Not allowed with visit |
| AD5 | Procedure allowed previously |
| AD8 | Not allowed alone |
| AD9 | Premium not allowed alone |
| ADF | Corresponding Procedure Invalid, Omitted or Paid at zero |
| ADH | Cannot be billed together |
| AH8 | Invalid Admission Date and/or Hospital number. |
| AHF | Concurrent or Supportive Care Same Period |
| AM1 | Service Limit Exceeded |
| AMR | Minimum service requirements have not been met |
| AMS | Multiple Procedures |
| AO2 | Previous Obstetrical Service |
| AO3 | Most Responsible Physician (MRP) Visit Already Paid |
| ARF | Missing Physician Referring Number |
| ARP | Referring Physician Number Required |
| ASP | Not Allowed with Surgical Procedure |
| AT1 | Only One Modality Allowed |
| AT2 | Must Include Video Modality |
| AT3 | No Patient-Physician Relationship |
| AT4 | Modality Not Allowed |
| CNA | Counselling Not Allowed |
| EG1 | Group not Eligible |
| EH1 | Service Date before Eligibility Effective Date |
| EH2 | Mismatched Version Code |
| EH4 | Service Date after Eligibility End Date |
| EH5 | Service Date Not in Eligibility Period |
| EH6 | Eligibility Terminated-Deceased |
| EH9 | Health Number (HN) Not Activated |
| ENP | Invalid FSC for Nurse Practitioner (NP) |
| EPA | Network billing not approved |
| EPC | Patient not rostered/rostered to another Network |
| EPF | Enrolment Date Mismatch |
| EPP | Incorrect Code for Eligibility (Ontario Works/Ontario Disability Support Program) |
| EPS | Patient Not Eligible for Program |
| EP1 | Enrolment Transaction Not Allowed |
| EP2 | Not for Enrolment/ReEnrolment |
| EP3 | Incorrect Service Date – Check Date of Enrolment |
| EP4 | Enrolment Restriction Applied |
| EP5 | Incorrect FSC for Group Type |
| EP6 | HN Not Activated |
| EP7 | Code must be billed alone |
| EQ1 | Clinic/Doctor Not on File - Practitioner not registered with OHIP |
| EQ2 | Specialty mismatch – Specialty Code is inactive or not registered on date of service |
| EQ3 | Claim submitted as Pay Patient - Health care provider is registered as OPTED-IN for date of service |
| EQ4 | Claim submitted as Pay Provider - Health care provider is registered as OPTED-OUT for date of service |
| EQ5 | Lab inactive on Service date |
| EQ6 | Incorrect Referral Number - Referring/requisitioning health care provider number is not registered with the Ministry of Health |
| EQ9 | Lab Number not on File |
| EQB | Solo practitioner inactive on service date Practitioner number is Midwife (700000-722899) referral only Claims submitted by Chiropractors using their Claim Submission Number (CSN) Physician Registered as group billing only |
| EQC | Group not registered |
| EQD | Group inactive on service date |
| EQE | Affiliated Practitioner not in Group - Health care provider is not registered with the Ministry of Health as an affiliate of this group on date of service |
| EQF | Affiliated Practitioner inactive - Health care provider is not actively registered with the Ministry of Health as an affiliate of this group on date of service |
| EQG | Referring laboratory is not registered with the Ministry of Health |
| EQI | Contract characteristics error |
| EQJ | Practitioner Not Eligible On Service Date - New Graduate bills New Patient fee (Q013) or Physician (not a new graduate) bills new Graduate-New Patient fee (Q033). |
| EQK | Master Number (MNI) Does not Meet Criteria - A100 billed with a specialty code other than 00. |
| EQL | Physician Not Eligible to Claim FSC - A100 billed with a speciality code other than 00 or billed by provider with any Emergency Department Alternate Funding arrangement (EDAFA) group number. |
| EQM | Not Registered for Use |
| EQN | Registration Usage Error on Service Date |
| EQP | Enrolment Type Not Eligible |
| EQS | Practitioner Criteria Not Met |
| ERF | Referring physician number is currently ineligible for referrals |
| ESD | APP group affiliation on service date - Hospital Emergency Department is part of an alternative funding agreement |
| ESF | Not eligible to bill |
| ESH | Not Eligible For Blank HN |
| ESN | Invalid Blank HN Claim - No HN required for FSC |
| HCC | Not on Health Care Connect (HCC) database-Not Eligible On HCC database but not Complex-Vulnerable On HCC database but not in 'referred to' status |
| HCE | Patient enrolled to billing physician but later than 3 months from the "referred to" date on HCC database-Enrolment after 3 Months |
| PAA | No Initial Fee Previously Paid - To ensure the smoking cessation initial discussion fee (E079) has been paid within 365 days prior to the smoking cessation counseling fee (Q042) or the smoking cessation follow up fee (K039) |
| PA1 | Invalid PA Service - Physician Assistant (PA) Pilot claim submissions may contain one or more PA Tracking FSC's but other OHIP insured service FSCs are not allowed on the same claim. |
| PA2 | Invalid PA Claim - Physician Assistant Pilot (PA) claim submissions with the PA as the submitting physician must identify the solo billing number of the supervising physician in the "Refer Physician" field. |
| PA3 | Not registered for PA - The physician and/or referring physician fields on the PA Pilot claim submission contain billing numbers which are not affiliated to the PA Pilot group number. |
| PA4 | PA Registration on Service Date Error |
| PA5 | PA Affiliation Error |
| PA6 | PA Affiliation on Service Date Error |
| V02 | Invalid Region Code |
| V05 | Error-Claim Number is less than Service Date |
| V06 | Incorrect Clinic Code |
| V07 | Invalid Practitioner Number |
| V08 | Invalid Specialty Code: • Specialty code is missing/not 2 numerics • Not a valid specialty code • Specialty code is 27 and provider number is not 599993 • Specialty code is 90 and provider number is not 991000 • Specialty code is 49, 50, 51, 52, 53, 54, 55, 70 and 71 and the health care provider number does not begin with 4 • Specialty code is 56 and health care provider number does not begin with 80 or 81 • Specialty code is 80 or 81 and health care provider number does not begin with 82 |
| V09 | Invalid Referral Number |
| V13 | Patient's date of birth is missing/invalid format Month not in the range of 01-12 Not 8 numerics Day is outside acceptable range for month |
| V16 | Unacceptable Diagnostic Code Not numeric |
| V17 | Payee must be 'P' (Provider) or 'S' (Patient) |
| V18 | Invalid Amission/First Visit date |
| V19 | Invalid Chiropractor Diagnostic Code |
| V20 | Unacceptable Age for Diagnostic code - Service code is A007, patient is over 2 years old and diagnostic code is '916' or service code is A003 and the patient is under 16 years old and the diagnostic code is '917' |
| V21 | Diagnostic Code Required |
| V22 | Invalid Diagnostic Code |
| V23 | Check Number Of Services |
| V28 | Invalid Hospital Number |
| V29 | Invalid In-Out-Patient Indicator |
| V30 | FSC/Diagnostic Code Combination Not A Benefit (NAB) |
| V31 | Error in Claim Header - Missing any of the following: group number, health care provider number, specialty code |
| V34 | Invalid Service Code Service Code and Health Care provider type mismatch |
| V35 | Invalid Out-of-Province/Out-of-Country Service |
| V36 | Check input criteria required for sessional billing |
| V39 | Number of items exceeds the maximum (99) |
| V40 | Invalid Fee Schedule Code Service code is missing Service code is not in the format ANNNA where: • A is alphabetic (A-Z) • NNN is numeric (001-999) • A is alphabetic (A-C) |
| V41 | Invalid Fee Billed Fee submitted is missing/not 6 numerics Fee submitted is not in the range '000000'-'500000' ($$$$cc) |
| V42 | Invalid Number of Services Number of services is missing/not 2 numerics Number of services is not in the range '01-99' |
| V47 | Fee not Divisible - Fee submitted is not evenly divisible (to the cent) by the number of services |
| V50 | Service Date Pre Initial Visit - Physiotherapy |
| V51 | Invalid location code - must be blank or four numerics. If present, must be valid based on MOHLTC Residency Code Manual |
| V53 | Invalid FSC-Magnetic Tape/Disk |
| V62 | Invalid service location indicator - hospital diagnostic service billing from a participating hospital physician/group is not of the five valid SLI codes (HDS, HED, HIP, HOP or HRP) |
| V63 | Referring Laboratory Number must start with 5 (5###) |
| V64 | Missing service location indicator |
| V65 | Missing master number - SLI code HDS, HED, HIP, HOP or HRP is included with a diagnostic service billing but a master number was not included |
| V66 | Missing admission date - SLI code HIP is included with a diagnostic service billing but an admission date was not included |
| V67 | Missing master number and admission date - assigned when a SLI code HIP is included with a diagnostic service billing but a master number and admission date were both not included |
| V68 | Incorrect service location indicator - assigned when a diagnostic service is billed with a master number and admission date but the SLI code is not HIP |
| V69 | Service Date Invalid for SLI |
| V70 | Date of service is greater than the file/batch creation date |
| V71 | Invalid Dental Master Number |
| V73 | OTN SLI No Longer Active |
| V98 | Wrong Preventive Care Date of Service |
| VJ5 | Invalid Service Date Date of Service is missing/not 8 numerics Month is not in the range 01-12 Day is outside acceptable range for month Date of Service is greater than Ministry of Health system run date |
| VJ7 | Stale-dated Claim |
| VJ8 | Stale-dated Claim Encounter |
| VHC | SLI required for technical fee |
| VS1 | Invalid SEAMO Provider Code |
| VS2 | Invalid Venue Type |
| VS3 | Invalid Clinic Number |
| VS4 | Invalid Healthcare Item |
| VS5 | Invalid In-Patient/Out-Patient Indicator |
| VS6 | Invalid HC Item Code Format |
| VTC | Virtual Tech Code required |
| VT1 | Only 1 VTC allowed |

### Health Number error codes (VHA to VH9)

| Error Code | Reason(s) for rejection |
|------------|-------------------------|
| VHA | OHIP number not registered with ministry for health number |
| VHB | No HN Required for FSC A non-encounter service claim submitted with a Health Number |
| VH0 | Header 2 and HN Present Claim Header-2 present on MRI claim submitted with Health Number in Claim Header-1 |
| VH1 | Health Number is missing/invalid |
| VH2 | Health Number is Missing Health Number is not present (Payment program is HCP or WCB) |
| VH3 | Invalid Payment Program The payment program is missing or is not equal to HCP, RMB, WCB |
| VH4 | Invalid Version Code |
| VH5 | OHIP Number Required for Service Date |
| VH6 | Mixed Service Dates |
| VH7 | Health number and OHIP number on same claim |
| VH8 | Date of birth does not match the Health Number submitted |
| VH9 | Health Number is not registered with ministry |

### Independent Health Facilities (IHF) error codes (EF1 to EF9)

| Error Code | Reason(s) for rejection |
|------------|-------------------------|
| EF1 | IHF number not approved for billing on the date specified |
| EF2 | IHF not licensed or grandfathered to bill FSC on the date specified |
| EF3 | Insured services are excluded from IHF billings |
| EF4 | Provider is not approved to bill IHF fee on date specified |
| EF5 | IHF practitioner 991000 is not allowed to bill insured services |
| EF7 | Referring physician number is required for the IHF fee billed |
| EF8 | 'I' service codes are exclusive to IHFs |
| EF9 | Mobile site number required |

### Reciprocal Medical Billing (RMB) error codes (R01 to R09)

| Error Code | Reason(s) for rejection |
|------------|-------------------------|
| R01 | Missing Health Service Number (HSN) |
| R02 | Invalid HSN |
| R03 | Invalid/Missing Province Code |
| R04 | Service Excluded from RMBS |
| R05 | Provincial code invalid for RMBS Province code of 'ON' (Ontario) or ‘PQ’ (Quebec) and not an Outaouais claim |
| R06 | Invalid Provider for RMBS |
| R07 | Invalid Payment Type for RMBS |
| R08 | Invalid Referral Number |
| R09 | Claim Header 2 Missing-RMB |
| V10 | Patient's last name is missing/not alphabetic (A-Z) First field position is blank |
| V12 | Patient's first name is missing/not alphabetic (A-Z) First field position is blank |
| V14 | Patient sex must be '1' (male) or '2' (female) |

### Telemedicine error codes (ET1 to ET5 and TM1 to TM8)

| Error Code | Reason(s) for rejection |
|------------|-------------------------|
| ET1 | Not Registered for Telemedicine |
| ET4 | Telemedicine Premium/Tracking Code Missing |
| ET5 | Telemedicine SLI Missing/Invalid - The telemedicine billing is submitted with a telemedicine tracking code but the SLI code is not 'OTN' or is not present. |
| TM1 | Duplicate Telemedicine Claim, Same patient |
| TM2 | Service not Billable for Missed/ Cancelled/Abandoned Appointment |
| TM3 | Service not payable underTelemedicine Program |
| TM4 | Non Telemedicine Claim paid for same patient |
| TM5 | Telemedicine Claim Paid for same patient |
| TM6 | Registration not in effect on Service Date |
| TM7 | Dental Service not eligible for Telemedicine |
| TM8 | Not eligible for Store Forward |

### Workplace Safety and Insurance Board (Workers Compensation Board (WCB)) error codes (VW1)

| Error Code | Reason(s) for rejection |
|------------|-------------------------|
| VW1 | Invalid WCB Service |

### Exemption

This technical publication has been exempted from translation under the French Language Services Act as per Ontario Regulation 671/92.
