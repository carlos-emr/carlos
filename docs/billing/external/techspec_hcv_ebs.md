# Technical Specification for Health Card Validation (HCV) Service via Electronic Business Services (EBS)

> **Source**: [http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_hcv_ebs.pdf]
> **Fetched**: 2026-04-29  
> **Format**: extracted text from PDF via pypdfium2; tables may be space-aligned rather than GFM.  
> **Authoritative source**: the PDF at the URL above. If this MD and the PDF disagree, the PDF wins.

Page count: 52

---
```xml
<!-- page 1 -->

Technical Specification for Health Card
Validation (HCV) Service via Electronic
Business Services (EBS)
Ministry of Health and Long-Term Care
EBS – HCV SOAP Specification
Version 4.0

<!-- page 2 -->

Table of Contents
Chapter 1 Health Card Validation (HCV) Service via Electronic
Business Sercies (EBS) ...........................................................................3
Glossary .......................................................................................................................... 4
Notice to Reader ............................................................................................................. 6
Intended Audience for this Technical Specification Document........................................ 7
About This Document...................................................................................................... 8
Introduction ..................................................................................................................... 9
Health Card Validation.................................................................................................. 9
Web Service Interface for HCV Service........................................................................ 9
Technical Interface........................................................................................................ 10
SOAP Message: ......................................................................................................... 10
The Message WSDL................................................................................................... 10
WSDL Definitions Table.............................................................................................. 10
Validation Message Schema......................................................................................... 11
Data Specifications for Fields ..................................................................................... 11
Input (Request) Message Fields............................................................................... 11
Output (Response) Message Fields ......................................................................... 12
Testing .......................................................................................................................... 15
APPENDIX A: Response Codes ................................................................................... 16
APPENDIX B: Time Limited Fee Service Code - Return Codes ................................... 20
APPENDIX C: Error Codes ........................................................................................... 21
Appendix D: The Message WSDL................................................................................. 22
Appendix E: Message Schema ..................................................................................... 27
APPENDIX F: MSA Model Request Message Example................................................ 34
APPENDIX G: IDP Model Request Message Example................................................. 34
APPENDIX H: MSA Response Message Example ....................................................... 39
APPENDIX I: IDP Response Message Example........................................................... 44

<!-- page 3 -->

Chapter 1 Health Card Validation (HCV) Service via Electronic
Business Services (EBS)

<!-- page 4 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 4 of 52
Chapter 1 Health Card Validation (HCV) Service via Electronic
Business Services (EBS)
Glossary
Term Definition
Claim Submission Number
(CSN) aka Billing Number
A unique identifier that is assigned to a Health Care
Provider who is registered with Ministry of Health and
Long-Term Care (MOHLTC) for the purpose of
submitting claims for insured services.
Health Card Validation
(HCV)
Service provided by MOHLTC that checks the status
and validity of a health card version, presented to
determine an individual’s eligibility for health care
coverage.
HCV Service Schedule A Service Schedule to the Master Service agreement
that forms part of the Agreement between the Service
Requestor and MOHLTC and captures the roles and
responsibilities that relate to the HCV via EBS.
Health Care Provider (HCP) Individual, group or facility authorized to provide health
care services to residents of Ontario.
Health Information
Custodian (HIC)
Health Information Custodian in or under Personal
Health & Information Privacy Act (PHIPA).
Health Number (HN) Health number consists of a 10 digit personal health
number. A version code identifies the specific health
card of the card holder.
Identity Provider (IDP) A party or organization that creates, maintains, and
manages identity information for principals and
performs principal authentication for other parties or
organizations.
MOHLTC The Ontario Ministry of Health and Long-Term Care.
MOHLTC Electronic
Business Services
(EBS)
The Electronic Business Service is a framework which
provides an electronic business gateway that exposes
MOHLTC services to the Broader Health Sector and
provides a full featured IAM suite of provisioning,
business enrolment, business and IT federation
agreements, technical specifications and terms of
acceptable use governance.
Master Services
Agreement (MSA)
The binding legal agreement through which MOHLTC
accepts the identity of an end user at face value based
on authenticating the end user’s organization at the
time of the service request.

<!-- page 5 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 5 of 52
Term Definition
Output (Response)
Message Fields
Output (Response) Message fields are those fields
which are returned by the Health Card Validation
(HCV) web service.
Response Code Code returned in an HCV response identifying the
outcome of the transaction – either the eligibility status
of the individual if applicable, or an explanatory failure
code otherwise.
Service Provider (SP) Throughout this document, Service Provider refers
exclusively to MOHLTC, as the provider of the Health
Card Validation service via EBS.
Simple Object Access
Protocol (SOAP)
Simple Object Access Protocol: an Extensible Markup
Language (XML) -based protocol for exchanging
structured information between computer systems. For
more information refer to http://www.w3.org/TR/soap/.
Stakeholder Number (SN) A unique identifier that is assigned to stakeholders of
interest who are registered with the MOHLTC. The
unique identifier is either 7 digits long or 8 digits long
depending on the type of stakeholder to which it
belongs.
UID It is a version 4 Universally Unique Identifier (UUID).
Web Services Description
Language (WSDL)
An XML-based language for describing web services
and how to access them. For more information refer to
http://www.w3.org/TR/wsdl.
Web Services Security
(WS-Security)
An XML based framework for ensuring secure
transmission of electronic messages. It will be used
for: identification; authentication; and authorization of
parties using EBS as well as ensuring message
integrity by means of a digital signature applied to each
message. For more information refer to:
http://www.oasisopen.org/committees/tc_home.php?wg_abbrev=wss.

<!-- page 6 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 6 of 52
Notice to Reader
All possible measures are exerted to ensure the accuracy of the contents of this
manual; however, the manual may contain typographical or printing or other errors. The
reader is cautioned against complete reliance upon the contents of the manual without
confirming the accuracy and currency of the information contained in it. The Crown in
Right of Ontario, as represented by the Ministry of Health and Long-Term Care
(MOHLTC), assumes no responsibility for errors or omissions in any of the information
contained in this manual, or for any person’s use of the material therein, or for any costs
or damages associated with such use. In no event shall the Crown in Right of Ontario
be liable for any errors or omissions, or for any damages including, without limitation,
damages for direct, indirect, incidental, special, consequential or punitive damages
arising out of or related to the use of information contained in this manual.
This technical specification is intended only to assist and guide the development of
software to access the Health Card Validation (HCV) web service via the MOHLTC
Electronic Business Services (EBS).
Revisions to the specification will be made as required. The ministry will make every
effort to give as much advance noticed as possible of future revisions. It is essential that
software developers keep current regarding any changes to this specification. The
current version of the technical specification will be available for download at the
following URL:
http://www.health.gov.on.ca/English/providers/pub/pub_menus/pub_ohip.html
For further details about HCV via EBS service including enrolment criteria please refer
to the Health Card Validation Reference Manual posted at:
http://www.health.gov.on.ca/english/providers/pub/ohip/ohipvalid_manual/ohipvalid_ma
nual_mn.html
Before use of HCV via EBS, please ensure that you conform to the ministry’s technical
specifications explained in this document and service eligibility criteria outlined in the
Health Card Validation Reference Manual.
• Please direct any questions to the Service Support Contact Centre (SSCC) at
1 800 262-6524 or SSContactCentre.MOH@ontario.ca

<!-- page 7 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 7 of 52
Intended Audience for this Technical Specification Document
This document is intended for use by developers of applications and products that
support communication with MOHLTC’s HCV via EBS (web service interface), a service
provided by the ministry to support health card validation. This service is built to the web
services standards detailed in this document.
This document is also intended to be read in the context of either a service agreement
between the ministry and the Service Requestor or through an accepted IDP. The
service agreement is defined by a Master Service Agreement (MSA) and a Health Card
Validation Service Schedule between the ministry and the Service Requestor (SR).
This technical specification is also targeted to vendors of various software applications
and products that have or plan to have modules that support HCV through a web
service interface within the province of Ontario in Canada.
The document describes the web service, the Simple Object Access Protocol (SOAP)
message specification and aims to guide the users in the development of client
application to integrate with this web service.
It is assumed that the reader has knowledge of web services and related protocols,
SOAP and XML message formats/processing, WS-Security 1.1, relevant interoperability
profiles and has read the ‘MOHLTC EBS - Generic Security Specification’ document.

<!-- page 8 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 8 of 52
About This Document
The Ministry of Health and Long-Term Care Health Card Validation (HCV) service
allows health care providers (HCP) to validate the eligibility of a health card holder and
the status of his or her health card version.
This document is an extension of the “MOHLTC EBS – Generic Security Specification”
document and is intended to provide the reader with sufficient information to implement
service requestor software that can use the service. The HCV service supports both the
Master Services Agreement (MSA) and Identity Provider (IDP) security models.
The introduction provides an overview of the HCV service and provides a glossary of
the terminology used throughout the document. Additional functional information and
overview of the HCV process is provided in the Health Card Validation Reference
Manual published at:
http://www.health.gov.on.ca/english/providers/pub/ohip/ohipvalid_manual/ohipvalid_ma
nual_mn.html
The Simple Object Access Protocol (SOAP) Message Section provides the technical
specifications of the SOAP message including:
• Message Web Services Description Language (WSDL);
• Validation message schema including the request and the response; and
• Data specifications for fields.
Appendices provide:
• Response codes;
• Time Limited Fee Service codes
• The Message WSDL;
• SOAP message examples

<!-- page 9 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 9 of 52
Introduction
The Health Card Validation service adheres to the EBS security models and as such
requires that the ministry unique identifier for Service Requestor (SR) be a Stakeholder
Number (SN) and for Service User (SU) be either a SN or a Claim Submission Number
(CSN).
Health Card Validation
Each eligible resident of the Province of Ontario that registers for the Ontario Health
Insurance Plan (OHIP) is assigned a unique 10-digit health number. A health card is
provided to the insured person for the purpose of obtaining insured health services in
Ontario. The health card version is identified by a version code. Photo health cards are
assigned a two-letter version code; standard “red & white” cards might have no version
code, a single letter or a two letter version code. An insured person presents his/her
health card at each visit to a health care provider.
Health Care Providers (HCPs) can and should validate this information at the time a
health card is presented and prior to the health services being rendered. The health
card must be valid and belong to the patient who is presenting it. The HCV service will
not, however, guarantee payment of any claim submitted.
Web Service Interface for HCV Service
The Province of Ontario, via the Ministry of Health and Long-Term Care offers
EBS to the HCV service for users through third party or client software.
HCV via EBS is being provided in addition to the existing HCV methods.

<!-- page 10 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 10 of 52
Technical Interface
The Province of Ontario is responsible and accountable for the service provider
component.
The service interface uses the SOAP protocol for communication and the WS-Security
(WSS) protocol for transaction security. There are several implementations of the WSS
protocol available and it is suggested that one of those be used where possible. The
following sections assume appropriate headers are included as defined by the “EBS –
Generic Security SOAP Specification”.
The results object of the response will be encrypted with the EBS private certificate with
the AES128-CBC encryption algorithm and will need to be decrypted by the caller
before using the returned data.
SOAP Message:
SOAP is an XML-based standard protocol that defines a message specification for
transmitting XML documents via a network. Since this message specification does not
depend on a particular programming language or operating system, data transfer can
be conducted among and between systems that use different languages or operating
systems.
The Message WSDL
A WSDL is a specification for coding web services-related information (access point and
interface specifications, etc.) in XML. Note that while WSDL does not define a protocol
when sending/receiving messages, the ministry is using SOAP via HTTPS as the
protocol for message transmission.
WSDL Definitions Table
The WSDL includes the following standard elements:
Variable Description
HCV Service
URL
The URL of the HCV web service
Internet Access:
https://ws.ebs.health.gov.on.ca:1440/HCVService/HCValidationService
ENA Access:
https://intra
ws.ebs.health.gov.on.ca:1440/HCVService/HCValidationService
The complete WSDL is included in Appendix D.

<!-- page 11 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 11 of 52
Validation Message Schema
The validation message schema includes definitions of both the request and the
response elements. Schema elements are described below, and the full schema is
included in Appendix E.
The locale parameter for the validate method must be one of empty, spaces, en or fr. If
the field is empty or spaces then the English locale will be used for all returning text.
Data Specifications for Fields
The fields described in the message specification are necessarily generic in order to
follow the XML data typing standards. However, in order to pass validation, some of the
fields must be presented in the format expected by the ministry.
Input (Request) Message Fields
name=”healthNumber” type=”xs:string” pattern=”[1-9]\d{9}”
The health number is sent to the ministry as a string. The health number is a ten-digit
number that appears on the face of every health card.
name=”versionCode” type=”xs:string” pattern=” [A-Z]{0, 2}”
Version code is an alphabetic identifier that along with the health number uniquely
identifies a health card version. This field appears on the face of all photo cards and
some standard (“red & white”) cards. When present on the card, a version code is one
letter or two letters.
OPTIONAL FIELDS
maxOccurs="10" minOccurs="0" name=”feeServiceCodes”
type=”xs:string” pattern=”[A-Z]\d{3}”
A list of the time limited fee service codes that are to be checked.
The list of supported time limited fee service codes can be found in the HCV reference
manual at:
http://www.health.gov.on.ca/english/providers/pub/ohip/ohipvalid_manual/ohipvalid_ma
nual_mn.html

<!-- page 12 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 12 of 52
Output (Response) Message Fields
The Output Messages identified below have been classed into Mandatory and
Optional Response Fields – Category I, Category II and Category III. The output
message fields that can be included in the interface you are developing are
determined by the type of HCV service you are enrolled for. Please refer to the
HCV Reference Manual for details.
MANDATORY FIELDS:
The following mandatory fields are returned for each validation request
submitted.
name=”auditUID” type=”xs:string”
pattern=
 ”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID). A
UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of 32
hexadecimal digits, displayed in 5 groups separated by hyphens, in the form 8-4-4-4-12
for a total of 36 characters (32 digits and 4 hyphens).
Name=”responseCode” type=”xs:string”
A two character representation of the validation response code for given health number
and/or version code. (See ‘Response Codes’ in Appendix A for more details)
Name=”responseID” type=”xs:responseID”
A mnemonic representation of the validation response code for given health number
and/or version code. (See ‘Response Codes’ in Appendix A for more details)

<!-- page 13 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 13 of 52
OPTIONAL FIELDS
The following are optional fields and can be returned for each validation request
submitted.
Category I
Name=”responseAction” type=”xs:string”
The action required of the caller for the returned response code.
Name=”responseDescription” type=”xs:string”
A description for the validation response code for given health number and/or version
code.
Category II
name=”healthNumber” type=”xs:string” pattern=”[1-9]\d{9}”
The health number is sent to the ministry as a string. The health number is a ten-digit
number that appears on the face of every health card.
name=”versionCode” type=”xs:string” pattern=” [A-Z]{0, 2}”
Version code is an alphabetic identifier that along with the health number uniquely
identifies a health card version. This field appears on the face of all photo cards and
some standard (“red & white”) cards. When present on the card, a version code is one
letter or two letters.
name=”firstName” type=”xs:string”
MOHLTC stores this value as upper case characters. A maximum of 20 characters are
kept on file. No accents or other diacritic marks are stored or returned.
Name=”secondName” type=”xs:string”
MOHLTC stores this value as upper case characters. A maximum of 20 characters are
kept on file. No accents or other diacritic marks are stored or returned.
Name=”lastName” type=”xs:string”
MOHLTC stores this value as upper case characters. A maximum of 30 characters are
kept on file. No accents or other diacritic marks are stored or returned.
Name=”gender” type=”xs:string”
The gender is returned as either an M or F, for male or female respectively.

<!-- page 14 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 14 of 52
Name=”dateOfBirth” type=”xs:dateTime”
The card holder’s date of birth.
Name=”expiryDate” type=”xs:dateTime”
The date the card expires.
Category III
name="feeServiceCode" type="xs:string" pattern=”[A-Z]\d{3}”
The time limited fee service code passed in to be queried.
name="feeServiceDate" type="xs:dateTime"
The last date the service was issued.
name="feeServiceResponseCode" type="xs:string"
The return code for the requested time limited fee service code. For more details on
response codes refer to Appendix B.
name="feeServiceResponseDescription" type="xs:string"
The return code for the requested time limited fee service code.

<!-- page 15 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 15 of 52
Testing
Conformance testing must be completed for HCV. For more details please refer to the
Testing section in the ‘MOHLTC EBS – Generic Security Specification’ document.

<!-- page 16 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 16 of 52
APPENDIX A: Response Codes
Character based response codes are returned as well as character constants for
Response IDs to provide more information to developers what codes and data has been
returned. All response codes are two characters.
The following is an overview of valid response codes:
• between 00 and 49 indicates the health card is invalid; cardholder not eligible
• between 50 and 59 indicates a valid health card; cardholder is eligible
• between 60 and 89 indicates health card is invalid; cardholder is eligible
The following are the constants that will be returned and their mapping to character
based response codes and comments about their use.
Response
Code
Response ID Descriptive
Text
Comments
05 NOT_10_DIGITS The Health
Number
submitted is
not 10
numeric
digits.
Response Code is
returned but no
Personal
Characteristics are
available.
10 FAILED_MOD10 The Health
Number
submitted
does not exist
on the
ministry’s
system.
Response Code is
returned but no
Personal
Characteristics are
available.
15 IS_IN_DISTRIBUTED_STATUS Pre-assigned
newborn
Health
Number.
Response Code is
returned but no
Personal
Characteristics are
available.
20 IS_NOT_ELIGIBLE Eligibility does
not exist for
this Health
Number.
20 IS_NOT_ELIGIBLE_ND Eligibility does
not exist for
this Health
Number.
Response Code is
returned but no
Personal
Characteristics are
available.

<!-- page 17 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 17 of 52
Response
Code
Response ID Descriptive
Text
Comments
20 IS_NOT_ELIGIBLE_ND Eligibility does
not exist for
this Health
Number.
Response Code is
returned but no
Personal
Characteristics are
available.
50 NOT_ON_ACTIVE_ROSTER Health card
passed
validation.
50 NOT_ON_ACTIVE_ROSTER_ND Health card
passed
validation.
Response Code is
returned but no
Personal
Characteristics are
available.
51 IS_ON_ACTIVE_ROSTER Health card
passed
validation.
51 IS_ON_ACTIVE_ROSTER_ND Health card
passed
validation.
Response Code is
returned but no
Personal
Characteristics are
available.
52 HAS_NOTICE Health card
passed
validation.
52 HAS_NOTICE_ND Health card
passed
validation.
Response Code is
returned but no
Personal
Characteristics are
available.
53 IS_RQ_HAS_EXPIRED Health card
passed
validation;
card is
expired.

<!-- page 18 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 18 of 52
Response
Code
Response ID Descriptive
Text
Comments
53 IS_RQ_HAS_EXPIRED_ND Health card
passed
validation
card is
expired.
Response Code is
returned but no
Personal
Characteristics are
available.
53 IS_THC Health card
passed
validation;
card is
expired.
53 IS_THC_ND Health card
passed
validation;
card is
expired.
Response Code is
returned but no
Personal
Characteristics are
available.
54 IS_RQ_FUTURE_ISSUE Health card
passed
validation;
card is future
dated.
54 IS_RQ_FUTURE_ISSUE_ND Health card
passed
validation;
card is future
dated.
Response Code is
returned but no
Personal
Characteristics are
available.
55 RETURNED_MAIL Health card
passed
validation;
cardholder
required to
update
address with
ministry.

<!-- page 19 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 19 of 52
Response
Code
Response ID Descriptive
Text
Comments
55 RETURNED_MAIL_ND Health card
passed
validation;
cardholder
required to
update
address with
ministry.
Response Code is
returned but no
Personal
Characteristics are
available.
65 INVALID_VERSION_CODE Invalid
version code.
65 INVALID_VERSION_CODE_ND Invalid
version code.
Response Code is
returned but no
Personal
Characteristics are
available.
70 IS_STOLEN Health card
reported
stolen.
70 IS_STOLEN_ND Health card
reported
stolen.
Response Code is
returned but no
Personal
Characteristics are
available.
75 IS_CANCELLED_OR_VOIDED Health card
cancelled or
voided.
75 IS_CANCELLED_OR_VOIDED_ND Health card
cancelled or
voided.
Response Code is
returned but no
Personal
Characteristics are
available.
75 IS_VOID_NEVER_ISS Health card
cancelled or
voided.
75 IS_VOID_NEVER_ISS_ND Health card
cancelled or
voided.
Response Code is
returned but no
Personal
Characteristics are
available.

<!-- page 20 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 20 of 52
Response
Code
Response ID Descriptive
Text
Comments
80 DAMAGED_STATE Health card
reported
damaged.
80 DAMAGED_STATE_ND Health card
reported
damaged.
Response Code is
returned but no
Personal
Characteristics are
available.
83 LOST_STATE Health card
reported lost.
83 LOST_STATE_ND Health card
reported lost.
Response Code is
returned but no
Personal
Characteristics are
available.
90 INFO_NOT_AVAIL Information is
not available.
APPENDIX B: Time Limited Fee Service Code - Return Codes
Code Description
101 No information available
102 Invalid Fee Service Code
201 Oculo-visual assessment or major eye exam performed
202 Bone mineral density measurement performed
203 Sleep study performed
99 System unavailable
Refer to the HCV Reference Manual at:
http://www.health.gov.on.ca/english/providers/pub/ohip/ohipvalid_manual/ohipvalid_ma
nual_mn.html for more details.
.

<!-- page 21 -->

Health Card Validation (HCV) Service
Final February 2013 Version 4.0 Page 21 of 52
APPENDIX C: Error Codes
Character based error codes are returned as well as textual descriptions of the error.
All ministry specific error codes are 9 characters.
The following are the ministry specific error codes that may be returned within a EBS
Fault accompanied by brief explanations.
EBS Fault
Codes
Error Comment
SMIDL0100 System not initialized correctly; contact your technical support or
software vendor.
SMIDL0203 Service is not available; contact your technical support or software
vendor.
SMIDL0204 General System Error; contact your technical support or software
vendor.

<!-- page 22 -->

Final February 2013 Version 4.0 Page 22 of 52
Appendix D: The Message WSDL
<?xml version="1.0" encoding="UTF-8"?><definitions name="HCValidationService" targetNamespace="http://hcv.health.ontario.ca/"
xmlns="http://schemas.xmlsoap.org/wsdl/"
xmlns:ebs="http://ebs.health.ontario.ca/"
xmlns:hcv="http://hcv.health.ontario.ca/"
xmlns:msa="http://msa.ebs.health.ontario.ca/"
xmlns:idp="http://idp.ebs.health.ontario.ca/"
xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
xmlns:sp="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200512"
xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
xmlns:wsdlsoap="http://schemas.xmlsoap.org/wsdl/soap/"
xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy"
xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
xmlns:xsd="http://www.w3.org/2001/XMLSchema">
<types>
<xsd:schema>
<xsd:import namespace="http://hcv.health.ontario.ca/" schemaLocation="HCValidationService_schema.xsd"/>
<xsd:import namespace="http://ebs.health.ontario.ca/" schemaLocation="EBS_schema.xsd"/>
<xsd:import namespace="http://msa.ebs.health.ontario.ca/" schemaLocation="MSA_schema.xsd"/>
<xsd:import namespace="http://idp.ebs.health.ontario.ca/" schemaLocation="IDP_schema.xsd"/>
</xsd:schema>
</types>
 <wsp:Policy wsu:Id="request-policy">
<wsp:ExactlyOne>
<wsp:All>
<wsp:All>
<sp:SignedSupportingTokens>

<!-- page 23 -->

Final February 2013 Version 4.0 Page 23 of 52
<sp:UsernameToken>
<wsp:Policy>
<wsp:All>
<sp:NoPassword/>
<sp:WssUsernameToken10/>
</wsp:All>
</wsp:Policy>
</sp:UsernameToken>
</sp:SignedSupportingTokens>
</wsp:All>
<wsp:ExactlyOne>
<wsp:All>
<sp:RequiredParts>
<sp:Header Name="EBS" Namespace="http://ebs.health.ontario.ca/"/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="MSA" Namespace="http://msa.ebs.health.ontario.ca/"/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="SoftwareConformanceKey" Namespace=""/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="AuditId" Namespace=""/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="ServiceUserMUID" Namespace=""/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="UserID" Namespace=""/>

<!-- page 24 -->

Final February 2013 Version 4.0 Page 24 of 52
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="Timestamp" Namespace="http://docs.oasis-open.org/wss/2004/01/oasis200401-wss-wssecurity-utility-1.0.xsd"/>
</sp:RequiredParts>
</wsp:All>
</wsp:ExactlyOne>
<wsp:ExactlyOne>
<wsp:All>
<sp:SignedParts>
<sp:Header Name="EBS" Namespace="http://ebs.health.ontario.ca/"/>
<sp:Header Name="MSA" Namespace="http://msa.ebs.health.ontario.ca/"/>
<sp:Header Name="Timestamp" Namespace="http://docs.oasis-open.org/wss/2004/01/oasis200401-wss-wssecurity-utility-1.0.xsd"/>
<sp:Header Name="UsernameToken" Namespace="http://docs.oasis-open.org/wss/2004/01/oasis200401-wss-wssecurity-utility-1.0.xsd"/>
<sp:Body/>
</sp:SignedParts>
</wsp:All>
</wsp:ExactlyOne>
</wsp:All>
<wsp:All>
<wsp:All>
<sp:SignedSupportingTokens>
<sp:UsernameToken>
<wsp:Policy>
<wsp:All>
<sp:WssUsernameToken10/>
</wsp:All>
</wsp:Policy>

<!-- page 25 -->

Final February 2013 Version 4.0 Page 25 of 52
</sp:UsernameToken>
</sp:SignedSupportingTokens>
</wsp:All>
<wsp:ExactlyOne>
<wsp:All>
<sp:SignedParts>
<sp:Header Name="EBS" Namespace="http://ebs.health.ontario.ca/"/>
<sp:Header Name="IDP" Namespace="http://idp.ebs.health.ontario.ca/"/>
<sp:Header Name="Timestamp" Namespace="http://docs.oasis-open.org/wss/2004/01/oasis-200401-
wss-wssecurity-utility-1.0.xsd"/>
<sp:Header Name="UsernameToken" Namespace="http://docs.oasis-open.org/wss/2004/01/oasis200401-wss-wssecurity-utility-1.0.xsd"/>
<sp:Body/>
</sp:SignedParts>
</wsp:All>
</wsp:ExactlyOne>
<wsp:ExactlyOne>
<wsp:All>
<sp:RequiredParts>
<sp:Header Name="EBS" Namespace="http://ebs.health.ontario.ca/"/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="IDP" Namespace="http://idp.ebs.health.ontario.ca/"/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="SoftwareConformanceKey" Namespace=""/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="AuditId" Namespace=""/>
</sp:RequiredParts>

<!-- page 26 -->

Final February 2013 Version 4.0 Page 26 of 52
<sp:RequiredParts>
<sp:Header Name="ServiceUserMUID" Namespace=""/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="Timestamp" Namespace="http://docs.oasis-open.org/wss/2004/01/oasis200401-wss-wssecurity-utility-1.0.xsd"/>
</sp:RequiredParts>
</wsp:All>
</wsp:ExactlyOne>
</wsp:All>
 </wsp:ExactlyOne>
 </wsp:Policy>
<wsdl:message name="EBSHeader">
<wsdl:part element="ebs:EBS" name="ebsrequest_header"/>
</wsdl:message>
<wsdl:message name="MSAHeader">
<wsdl:part element="msa:MSA" name="msarequest_header"/>
</wsdl:message>
<wsdl:message name="IDPHeader">
<wsdl:part element="idp:IDP" name="idprequest_header"/>
</wsdl:message>
<message name="validate">
<wsdl:part element="hcv:validate" name="parameters"/>
</message>
<message name="validateResponse">
<part element="hcv:validateResponse" name="parameters"/>

<!-- page 27 -->

Final February 2013 Version 4.0 Page 27 of 52
<message name="faultexception">
 <part element="ebs:EBSFault" name="Fault"/>
</message>
<portType name="HCValidation">
<operation name="validate">
<input message="hcv:validate"/>
<output message="hcv:validateResponse"/>
<fault message="hcv:dfault" name="Fault"/>
</operation>
</portType>
<binding name="HCValidationPortBinding" type="hcv:HCValidation">
<soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
<operation name="validate">
<soap:operation soapAction=""/>
<input>
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="hcv:EBSHeader" part="ebsrequest_header" use="literal"/>
<wsdlsoap:header message="hcv:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="hcv:IDPHeader" part="idprequest_header" use="literal"/>
<wsdlsoap:body parts="parameters" use="literal"/>
</input>
<output>
<soap:body use="literal"/>
</output>
 <fault name="FaultException">
<soap:fault name="FaultException" use="literal"/>

<!-- page 28 -->

Final February 2013 Version 4.0 Page 28 of 52
 </fault>
</operation>
</binding>
<service name="HCValidationService">
<port binding="hcv:HCValidationPortBinding" name="HCValidationPort">
<soap:address location="https://ws.ebs.health.gov.on.ca:1440/HCVService/HCValidationService"/>
</port>
</service>
</definitions>

<!-- page 29 -->

Final February 2013 Version 4.0 Page 29 of 52
Appendix E: Message Schema
<?xml version="1.0" encoding="UTF-8"?><xs:schema
targetNamespace="http://hcv.health.ontario.ca/"
version="1.0"
xmlns:tns="http://hcv.health.ontario.ca/"
xmlns:xs="http://www.w3.org/2001/XMLSchema">
 <xs:element name="validate" type="tns:validate"/>
 <xs:element name="validateResponse" type="tns:validateResponse"/>
 <xs:complexType name="validate">
 <xs:sequence>
 <xs:element name="requests" type="tns:requests"/>
 <xs:element minOccurs="0" name="locale" type="tns:lc" />
 </xs:sequence>
 </xs:complexType>
 <xs:simpleType name="lc">
 <xs:restriction base="xs:string">
 <xs:pattern value=""/>
 <xs:pattern value=" "/>
 <xs:pattern value="en"/>
 <xs:pattern value="fr"/>

 </xs:restriction>
 </xs:simpleType>
 <xs:complexType name="requests">
 <xs:sequence>
 <xs:element maxOccurs="unbounded" name="hcvRequest" type="tns:hcvRequest"/>
 </xs:sequence>
 </xs:complexType>

 <xs:complexType name="hcvRequest">
 <xs:sequence>
 <xs:element minOccurs="1" name="healthNumber" type="tns:hn"/>

<!-- page 30 -->

Final February 2013 Version 4.0 Page 30 of 52
 <xs:element minOccurs="1" name="versionCode" type="tns:vc"/>
 <xs:element maxOccurs="unbounded" minOccurs="0" name="feeServiceCodes" nillable="true" type="xs:string"/>
 </xs:sequence>
 </xs:complexType>
 <xs:simpleType name="hn">
 <xs:restriction base="xs:string">
 <xs:pattern value="[1-9]\d{9}"/>
 </xs:restriction>
 </xs:simpleType>
 <xs:simpleType name="vc">
 <xs:restriction base="xs:string">
 <xs:pattern value="[A-Z]{0,2}"/>
 </xs:restriction>
 </xs:simpleType>
 <xs:complexType name="validateResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="results" type="tns:hcvResults"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="hcvResults">
 <xs:sequence>
 <xs:element minOccurs="1" name="auditUID" type="xs:string"/>
 <xs:element maxOccurs="unbounded" minOccurs="1" name="results" nillable="true" type="tns:person"/>
 </xs:sequence>
 </xs:complexType>

<!-- page 31 -->

Final February 2013 Version 4.0 Page 31 of 52
 <xs:simpleType name="sex">
 <xs:restriction base="xs:string">
 <xs:pattern value="M"/>
 <xs:pattern value="F"/>
 </xs:restriction>
 </xs:simpleType>
 <xs:complexType name="person">
 <xs:sequence>
 <xs:element minOccurs="0" name="dateOfBirth" type="xs:dateTime"/>
 <xs:element minOccurs="0" name="expiryDate" type="xs:dateTime"/>
 <xs:element minOccurs="0" name="firstName" type="xs:string"/>
 <xs:element minOccurs="0" name="gender" type="tns:sex"/>
 <xs:element minOccurs="1" name="healthNumber" type="tns:hn"/>
 <xs:element minOccurs="0" name="lastName" type="xs:string"/>
 <xs:element minOccurs="1" name="responseAction" type="xs:string"/>
 <xs:element minOccurs="1" name="responseCode" type="xs:string"/>
 <xs:element minOccurs="1" name="responseDescription" type="xs:string"/>
 <xs:element minOccurs="1" name="responseID" type="tns:responseID"/>
 <xs:element minOccurs="0" name="secondName" type="xs:string"/>
 <xs:element minOccurs="1" name="versionCode" type="tns:vc"/>
 <xs:element maxOccurs="unbounded" minOccurs="0" name="feeServiceDetails" type="tns:feeServiceDetails"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="feeServiceDetails">
 <xs:sequence>
 <xs:element minOccurs="1" name="feeServiceCode" type="xs:string"/>

<!-- page 32 -->

Final February 2013 Version 4.0 Page 32 of 52
 <xs:element minOccurs="0" name="feeServiceDate" type="xs:dateTime"/>
 <xs:element minOccurs="1" name="feeServiceResponseCode" type="xs:string"/>
 <xs:element minOccurs="1" name="feeServiceResponseDescription" type="xs:string"/>
 </xs:sequence>
 </xs:complexType>
 <xs:element name="dfault" type="tns:dfault"/>
 <xs:complexType name="dfault">
 <xs:sequence>
 <xs:element name="faultcode" type="xs:string"/>
 <xs:element name="faultstring" type="xs:string"/>
 </xs:sequence>
 </xs:complexType>

 <xs:simpleType name="responseID">
 <xs:restriction base="xs:string">
 <xs:enumeration value="INFO_NOT_AVAIL"/>
 <xs:enumeration value="LOST_STATE_ND"/>
 <xs:enumeration value="LOST_STATE"/>
 <xs:enumeration value="DAMAGED_STATE_ND"/>
 <xs:enumeration value="DAMAGED_STATE"/>
 <xs:enumeration value="IS_VOID_NEVER_ISS_ND"/>
 <xs:enumeration value="IS_VOID_NEVER_ISS"/>
 <xs:enumeration value="IS_CANCELLED_OR_VOIDED_ND"/>
 <xs:enumeration value="IS_CANCELLED_OR_VOIDED"/>
 <xs:enumeration value="IS_STOLEN_ND"/>
 <xs:enumeration value="IS_STOLEN"/>
 <xs:enumeration value="INVALID_VERSION_CODE_ND"/>

<!-- page 33 -->

Final February 2013 Version 4.0 Page 33 of 52
 <xs:enumeration value="INVALID_VERSION_CODE"/>
 <xs:enumeration value="RETURNED_MAIL_ND"/>
 <xs:enumeration value="RETURNED_MAIL"/>
 <xs:enumeration value="IS_THC_ND"/>
 <xs:enumeration value="IS_THC"/>
 <xs:enumeration value="IS_RQ_HAS_EXPIRED_ND"/>
 <xs:enumeration value="IS_RQ_HAS_EXPIRED"/>
 <xs:enumeration value="IS_RQ_FUTURE_ISSUE"/>
 <xs:enumeration value="IS_RQ_FUTURE_ISSUE_ND"/>
 <xs:enumeration value="HAS_NOTICE_ND"/>
 <xs:enumeration value="HAS_NOTICE"/>
 <xs:enumeration value="IS_ON_ACTIVE_ROSTER_ND"/>
 <xs:enumeration value="IS_ON_ACTIVE_ROSTER"/>
 <xs:enumeration value="NOT_ON_ACTIVE_ROSTER_ND"/>
 <xs:enumeration value="NOT_ON_ACTIVE_ROSTER"/>
 <xs:enumeration value="IS_NOT_ELIGIBLE_ND"/>
 <xs:enumeration value="IS_NOT_ELIGIBLE"/>
 <xs:enumeration value="IS_IN_DISTRIBUTED_STATUS"/>
 <xs:enumeration value="FAILED_MOD10"/>
 <xs:enumeration value="NOT_10_DIGITS"/>
 <xs:enumeration value="SUCCESS"/>
 </xs:restriction>
 </xs:simpleType>
</xs:schema>

<!-- page 34 -->

Final February 2013 Version 4.0 Page 34 of 52
APPENDIX F: MSA Model Request Message Example
<soapenv:Envelope
xmlns:soap-sec="http://schemas.xmlsoap.org/security/2000-12"
xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xmlns:sp="http://schemas.xmlsoap.org/ws/2005/07/securitypolicy"
xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
xmlns:xs="http://www.w3.org/2001/XMLSchema"
xmlns:tns="http://hcv.health.ontario.ca/"
xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
xmlns:ebs="http://ebs.health.ontario.ca/security/2012-03"
xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy"
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
 <soapenv:Header>
 <ns2:EBS wsu:Id="id-1" xmlns:ns2="http://ebs.health.ontario.ca/" >
 <SoftwareConformanceKey>444561ee-277f-77b2-c664-7a9923jfgh1b</SoftwareConformanceKey>
 <AuditId>73b7051e-6126-4b41-9ae0-21b707ca8a53</AuditId>
 </ns2:EBS>
 <ns2:MSA wsu:Id="id-2" xmlns:ns2="http://msa.ebs.health.ontario.ca/" >
 <ServiceUserMUID>4523394</ServiceUserMUID>
 <UserID>johndoe</UserID>
 </ns2:MSA>
 <wsse:Security SOAP-ENV:mustUnderstand="1">
 <wsu:Timestamp wsu:Id="id-3">
 <wsu:Created>2012-06-20T17:59:14.026Z</wsu:Created>
 <wsu:Expires>2012-06-20T17:59:44.026Z</wsu:Expires>

<!-- page 35 -->

Final February 2013 Version 4.0 Page 35 of 52
 </wsu:Timestamp>
 <wsse:UsernameToken wsu:Id="id-4">
 <wsse:Username>72214255</wsse:Username>
 </wsse:UsernameToken>
 <wsse:BinarySecurityToken
EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"
 ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3"
wsu:Id="X509-
ABDCFEC7595B7819C213402151542661">MIICMzCCAZygAwIBAgIET1e+dDANBgkqhkiG9w0BAQUFADBeMQswCQYDVQQGEwJDQTEQMA4GA1U
ECBMHT250YXJpbzENMAsGA1UEChMET0hJUDEVMBMGA1UECxMMUmVnaXN0cmF0aW9uMRcwFQYDVQQDEw4xNDIuMTQ1LjcwLjE3NzAeFw0xMjAz
MDcyMDAwNTJaFw0xMzAzMDcyMDAwNTJaMF4xCzAJBgNVBAYTAkNBMRAwDgYDVQQIEwdPbnRhcmlvMQ0wCwYDVQQKEwRPSElQMRUwEwYDVQQLE
wxSZWdpc3RyYXRpb24xFzAVBgNVBAMTDjE0Mi4xNDUuNzAuMTc3MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCs/JIP6CE5IkfTnD/c56
K+QAYqETdLvW1xXJ6ipkVhjjC2ASKuuH4fvhbyxo2B4VugsL9r4E5jHEKoi+GDKOLlLZRfSy0cB8IcpXonAuGqMzhCoEQ1CdxNb9etMyvQGRK
EBgniKKxTvpTyZdpYDi92up5E+FYL3jEejhp+1iDFJQIDAQABMA0GCSqGSIb3DQEBBQUAA4GBAHn8VZS169BJMa4E6SNLnY7u80zSh90mbrTU
WjM1dEicv3jQMMsrWHfoCt+nRSqfNLUTLc8U0LqiB3jnnNJgJt1T7Sp8eUZPdH0gY3i83ZXA8HDFKMZF3qL8I8ncu8FPcZGYBNhYrGjXXsuqX
imiTIjxgm06ErRa/51szOFFxWrB
 </wsse:BinarySecurityToken>
 <ds:Signature Id="SIG-6" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" >
 <ds:SignedInfo>
 <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsse wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:CanonicalizationMethod>
 <ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1" />
 <ds:Reference URI="#id-1">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces
PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsse wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:Transform>
 </ds:Transforms>

<!-- page 36 -->

Final February 2013 Version 4.0 Page 36 of 52
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>BLZqHgJB3aF+ldsn7KjFkP3OqZggSIkPqarO3cmqNos=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-2">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces
PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsse wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:Transform>
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>RA1O1voUNDV9+hi6IzNNxkTHfEdu2pu6fppiwN23JGI=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-3">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsse xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:Transform>
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>yuUH5tg5nKJwJ1UvzbbVAxWq4JidSmZEbdINlmSPoDE=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-4">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />

<!-- page 37 -->

Final February 2013 Version 4.0 Page 37 of 52
 </ds:Transform>
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>yFOmFgMDHMBooWIEsB3azib2EX7fR+Ich03J19kFMVE=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-5">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec sp tns wsdl wsp wsse wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:Transform>
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>lGKOfXxmbsLds9+tD4eaCObTCdGNXDF/PY9LjDUPl9Y=</ds:DigestValue>
 </ds:Reference>
 </ds:SignedInfo>
 <ds:SignatureValue>

ieyaxUVyrLbEdnd3jw1nRgnXABFEhkfi6o/QT9Nz/S/h8Sxjy43/qyd/6KufZ5D5GvMfSO2S8jlg9QR3SKS3wdalXmOqJ7yivokrrMEcl5
RqnzgzFQMXi9pn9UI+W8god3UBvBU6ZAOqYwP5xHRR0k6wHoEdAtmpuZwoMLdULco=
 </ds:SignatureValue>
 <ds:KeyInfo Id="KI-ABDCFEC7595B7819C213402151542862">
 <wsse:SecurityTokenReference wsu:Id="STR-ABDCFEC7595B7819C213402151542863">
 <wsse:Reference URI="#X509-ABDCFEC7595B7819C213402151542661"
 ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3" />
 </wsse:SecurityTokenReference>
 </ds:KeyInfo>
 </ds:Signature>
 </wsse:Security>
 </soapenv:Header>

<!-- page 38 -->

Final February 2013 Version 4.0 Page 38 of 52
 <soapenv:Body wsu:Id="id-5">
 <ns5:validate xmlns:ns4="http://msa.ebs.health.ontario.ca/" xmlns:ns3="http://idp.ebs.health.ontario.ca/"
 xmlns:ns2="http://ebs.health.ontario.ca/"
 xmlns:ns5="http://hcv.health.ontario.ca/" >
 <requests>
 <hcvRequest>
 <healthNumber>2222211122</healthNumber>
 <versionCode>WW</versionCode>
 </hcvRequest>
 </requests>
 <locale>en</locale>
 </ns5:validate>
 </soapenv:Body>
</soapenv:Envelope>

<!-- page 39 -->

Final February 2013 Version 4.0 Page 39 of 52
APPENDIX G: IDP Model Request Message Example
<soapenv:Envelope
xmlns:soap-sec="http://schemas.xmlsoap.org/security/2000-12"
xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xmlns:sp="http://schemas.xmlsoap.org/ws/2005/07/securitypolicy"
xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
xmlns:xs="http://www.w3.org/2001/XMLSchema"
xmlns:tns="http://hcv.health.ontario.ca/"
xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
xmlns:ebs="http://ebs.health.ontario.ca/security/2012-03"
xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy"
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" >
 <soapenv:Header>
 <ns2:EBS wsu:Id="id-1" xmlns:ns2="http://ebs.health.ontario.ca/" >
 <SoftwareConformanceKey>444561ee-277f-77b2-c664-7a9923jfgh1b</SoftwareConformanceKey>
 <AuditId>73b7051e-6126-4b41-9ae0-21b707ca8a53</AuditId>
 </ns2:EBS>
 <ns2:IDP wsu:Id="id-2" xmlns:ns2="http://idp.ebs.health.ontario.ca/" >
 <ServiceUserMUID>4523394</ServiceUserMUID>
 </ns2:IDP>
 <wsse:Security SOAP-ENV:mustUnderstand="1">
 <wsu:Timestamp wsu:Id="id-3">
 <wsu:Created>2012-06-20T17:58:42.580Z</wsu:Created>
 <wsu:Expires>2012-06-20T17:59:12.580Z</wsu:Expires>
 </wsu:Timestamp>
 <wsse:UsernameToken wsu:Id="id-4">

<!-- page 40 -->

Final February 2013 Version 4.0 Page 40 of 52
 <wsse:Username>JOHNDOE@YAHOO.CA</wsse:Username>
 <wsse:Password Type="wsse:PasswordText">Password</wsse:Password>
 </wsse:UsernameToken>
 <wsse:BinarySecurityToken
 EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"
 ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3"
 wsu:Id="X509-02F859690D5C74E20913402151228211">

MIICMzCCAZygAwIBAgIET1e+dDANBgkqhkiG9w0BAQUFADBeMQswCQYDVQQGEwJDQTEQMA4GA1UECBMHT250YXJpbzENMAsGA1UEChMET0hJ
UDEVMBMGA1UECxMMUmVnaXN0cmF0aW9uMRcwFQYDVQQDEw4xNDIuMTQ1LjcwLjE3NzAeFw0xMjAzMDcyMDAwNTJaFw0xMzAzMDcyMDAwNTJaM
F4xCzAJBgNVBAYTAkNBMRAwDgYDVQQIEwdPbnRhcmlvMQ0wCwYDVQQKEwRPSElQMRUwEwYDVQQLEwxSZWdpc3RyYXRpb24xFzAVBgNVBAMTDj
E0Mi4xNDUuNzAuMTc3MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCs/JIP6CE5IkfTnD/c56K+QAYqETdLvW1xXJ6ipkVhjjC2ASKuuH4
fvhbyxo2B4VugsL9r4E5jHEKoi+GDKOLlLZRfSy0cB8IcpXonAuGqMzhCoEQ1CdxNb9etMyvQGRKEBgniKKxTvpTyZdpYDi92up5E+FYL3jEe
jhp+1iDFJQIDAQABMA0GCSqGSIb3DQEBBQUAA4GBAHn8VZS169BJMa4E6SNLnY7u80zSh90mbrTUWjM1dEicv3jQMMsrWHfoCt+nRSqfNLUTL
c8U0LqiB3jnnNJgJt1T7Sp8eUZPdH0gY3i83ZXA8HDFKMZF3qL8I8ncu8FPcZGYBNhYrGjXXsuqXimiTIjxgm06ErRa/51szOFFxWrB
 </wsse:BinarySecurityToken>
 <ds:Signature Id="SIG-6" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" >
 <ds:SignedInfo>
 <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsse wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:CanonicalizationMethod>
 <ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1" />
 <ds:Reference URI="#id-1">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces
PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsse wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:Transform>
 </ds:Transforms>

<!-- page 41 -->

Final February 2013 Version 4.0 Page 41 of 52
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>gpejbitTQxuMOhUirdbGNtHjsGhAArhAp3ByFuG9cHs=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-2">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces
PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsse wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:Transform>
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>ZWKvgN+eB0NFmQHPGYN5RoSZzbuboqKLzLcV6PEOz3E=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-3">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsse xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:Transform>
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>1AvUG2EE6+bgpJBe1TB4teUkKD4lRsw69BozDFQMGGE=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-4">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />

<!-- page 42 -->

Final February 2013 Version 4.0 Page 42 of 52
 </ds:Transform>
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>Lw6C0//TpU0uuta+9pjDPfD0aOokdgbVOEM9eaWcGjo=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-5">
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec sp tns wsdl wsp wsse wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </ds:Transform>
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>lGKOfXxmbsLds9+tD4eaCObTCdGNXDF/PY9LjDUPl9Y=</ds:DigestValue>
 </ds:Reference>
 </ds:SignedInfo>
 <ds:SignatureValue>

Yn5iRnjs/T2+nNgW8pArIgqc445RwL2wYPHZaydVJk0oUXV5B4nzU4fgX/sQTcY0O5vuReP8th4QZoGG6tSnxuBfqiDd2rkRZDrdgotJT++W
zhMLdt1J0Kah0aZVCWabQrxeGY2N3QDuMWr5PSlm1RWbkA3W5B4YLaD+S/j3QKc=
 </ds:SignatureValue>
 <ds:KeyInfo Id="KI-02F859690D5C74E20913402151228312">
 <wsse:SecurityTokenReference wsu:Id="STR-02F859690D5C74E20913402151228413">
 <wsse:Reference URI="#X509-02F859690D5C74E20913402151228211"
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3" />
 </wsse:SecurityTokenReference>
 </ds:KeyInfo>
 </ds:Signature>
 </wsse:Security>
 </soapenv:Header>

<!-- page 43 -->

Final February 2013 Version 4.0 Page 43 of 52
 <soapenv:Body wsu:Id="id-5">
 <ns5:validate xmlns:ns4="http://msa.ebs.health.ontario.ca/" xmlns:ns3="http://idp.ebs.health.ontario.ca/"
 xmlns:ns2="http://ebs.health.ontario.ca/"
 xmlns:ns5="http://hcv.health.ontario.ca/" >
 <requests>
 <hcvRequest>
 <healthNumber>2222211122</healthNumber>
 <versionCode>WW</versionCode>
 </hcvRequest>
 </requests>
 <locale>en</locale>
 </ns5:validate>
 </soapenv:Body>
</soapenv:Envelope>

<!-- page 44 -->

Final February 2013 Version 4.0 Page 44 of 52
APPENDIX H: MSA Response Message Example
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" >
 <soapenv:Header>
 <wsse:Security soapenv:mustUnderstand="1"
 xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" >
 <xenc:EncryptedKey xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" >
 <xenc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#rsa-1_5"
 xmlns:dsig="http://www.w3.org/2000/09/xmldsig#" />
 <dsig:KeyInfo xmlns:dsig="http://www.w3.org/2000/09/xmldsig#" >
 <wsse:SecurityTokenReference>
 <wsse:KeyIdentifier
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile1.0#X509SubjectKeyIdentifier"
EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security1.0#Base64Binary">
MyXZQNYrNkOJu5c9ZOAH7su3vEM=
</wsse:KeyIdentifier>
 </wsse:SecurityTokenReference>
 </dsig:KeyInfo>
 <xenc:CipherData xmlns:dsig="http://www.w3.org/2000/09/xmldsig#" >
<xenc:CipherValue>
cVnBMyBtyf6ZxGVxcSxxIE/OOnxO3UeE+3g8WhgpE3T0y6Iaon4Hql+NbglmQtT3uS4VzR1vIDw+mXkJrJ58NPEUNruR4SVEV4BwjUo
fFpK5w5n/EtqNKHGmodnO7jt5JdQx5C+SQhUWgFaAm7g2yXuVjYQHYaOXoDWyjc/oZ5o=
</xenc:CipherValue>
 </xenc:CipherData>
 </xenc:CipherData>
 <xenc:ReferenceList>
 <xenc:DataReference URI="#G0xc40aa1f0-46D" />
 </xenc:ReferenceList>

<!-- page 45 -->

Final February 2013 Version 4.0 Page 45 of 52
 </xenc:EncryptedKey>
 <wsu:Timestamp wsu:Id="Timestamp-8f77724a-5ce1-46a2-951d-e5de094f48fe"
 xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" >
 <wsu:Created>2012-04-26T13:57:45Z</wsu:Created>
 <wsu:Expires>2012-04-26T14:02:45Z</wsu:Expires>
 </wsu:Timestamp>
 <wsse:BinarySecurityToken wsu:Id="SecurityToken-34b8f2ce-9815-431b-a8b4-1607e4649733"
EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3"
 xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" >
MIICaTCCAdKgAwIBAgIET2c7BjANBgkqhkiG9w0BAQUFADB5MQswCQYDVQQGEwJDQTEPMA0GA1UEERMGSzdLNlIxMRAwDgYDVQQIEwdPbnRhcm
lvMREwDwYDVQQHEwhraW5nc3RvbjENMAsGA1UEChMET0hJUDEMMAoGA1UECxMDUmVnMRcwFQYDVQQDEw4xNDIuMTQ1LjcwLjE3NzAeFw0xMjAz
MTkxMzU2MjJaFw0xMzAzMTkxMzU2MjJaMHkxCzAJBgNVBAYTAkNBMQ8wDQYDVQQREwZLN0s2UjExEDAOBgNVBAgTB09udGFyaW8xETAPBgNVBA
cTCGtpbmdzdG9uMQ0wCwYDVQQKEwRPSElQMQwwCgYDVQQLEwNSZWcxFzAVBgNVBAMTDjE0Mi4xNDUuNzAuMTc3MIGfMA0GCSqGSIb3DQEBAQUA
A4GNADCBiQKBgQCUXFSVaeqj68PC0gsyPXqyHokOYRHZbJ0C60pPtRrxXVSbYa22xxJiF3k4ZCHT8vrTH3Y/4cmh9am2x0QhWhLolYpraV+mAm
xC/h3qmuPwsnZwiOcVLlc/pCXJVNR/5pHG13WsmMASaun5JojCI7vlFKqSaJ0gxfsQqQTHoBXhWQIDAQABMA0GCSqGSIb3DQEBBQUAA4GBAEBk
OcmZjV3BRjdEZV8bUGXuifXcZpjTcu+q9IorDhqtoaRc6a6S8MnXxfmX6Ye+oVKMbTFknlhwPtwXceW/o39gHYB+/hsXnYRLnIC8sQsHolo2py
BEAUJFyG5IOZrGuceoF+rf51ZSXBJadKvPs/RyUxkaBDBjbB7woPwCD4wz
</wsse:BinarySecurityToken>
 <Signature xmlns="http://www.w3.org/2000/09/xmldsig#" >
 <SignedInfo>
 <CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
 <SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" />
 <Reference URI="#Timestamp-8f77724a-5ce1-46a2-951d-e5de094f48fe">
 <Transforms>
 <Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </Transforms>
 <DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1" />
 <DigestValue>BMKSgG7Xl9JCWsRlQXald6LiXSQ=</DigestValue>
 </Reference>
 <Reference URI="#Body-5843f7a8-8cc3-4a18-86dd-c77edfe5acb0">

<!-- page 46 -->

Final February 2013 Version 4.0 Page 46 of 52
 <Transforms>
 <Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </Transforms>
 <DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1" />
 <DigestValue>qgJj5jlCMnROE+nEUIt+6lJn4gU=</DigestValue>
 </Reference>
 </SignedInfo>
 <SignatureValue>
Hoq4Z2cqi9vrVzpdDZBseSbyqHn+Dq4KHO56MgbpRZ5NqOETrorQHiwM83cGaAQPSM2yBqfFYgHnOkOUD+yAl3VtTG1EUKpk35V08yl
MWE/Qie9j8/kVZCbvIGDiCub0vcmbVVKGRkffPBxFqzLD0IRe6K+Mns1ipi2mnMUh/40=
</SignatureValue>
 <KeyInfo>
 <wsse:SecurityTokenReference>
 <wsse:Reference URI="#SecurityToken-34b8f2ce-9815-431b-a8b4-1607e4649733"
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3" />
 </wsse:SecurityTokenReference>
 </KeyInfo>
 </Signature>
 </wsse:Security>
 </soapenv:Header>
 <soapenv:Body wsu:Id="Body-5843f7a8-8cc3-4a18-86dd-c77edfe5acb0"
 xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" >
 <ns3:validateResponse xmlns:ns3="http://hcv.health.ontario.ca/" xmlns:ns2="http://msa.ebs.health.ontario.ca/" >
 <xenc:EncryptedData Id="G0xc40aa1f0-46D" Type="http://www.w3.org/2001/04/xmlenc#Element"
 xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" >
 <xenc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc" />
 <xenc:CipherData>
 <xenc:CipherValue>
fzu/QbpnT/Mky3F3cWWkPooBsb86/04Ql862htLoYP0e7/EK4Ef/tRR1WmwZUIsnbUVamriL5DAaD9AajckbHV3idsRb1Kpb1na2driS
EWSbqc2wKNa656wpDxXXAfBTGPVs3BAoGOrH7PdoBTO3nY2qsekxN9Acgt1frQvVyup2x2twBgMA7d1N8bJr4D78aOdvuCIIAt9/YGMz

<!-- page 47 -->

Final February 2013 Version 4.0 Page 47 of 52
rsdg+8QauW6kgFGy7uA+E/tzhIpSPwIAW81R7RDdo76P8w24lINsRa3mjO3DJc5fsMGGjWykVVPSeNuQX7Yx/fuuumE7PJ7if69qPN+h
sjyP2EC5EFfZdOf7OZfw1jWBZj63FBXcYi42cSs0uQfx6vgrrw/JZyY6xgTtzMmR8HF1//jneN1r7LlmkbbRlm1xrTD1L7Ye10NOgrSg
FO/eYWM+M45zh7LeNSoxdlVDAHSmfv0ZdTmlR6X9RvQkhlVZ2bCkH5bflwiS0t65A2x8GnLRsRfaVyLpXIr0kDWwEb84vaqA3RZSeaXf
9p07tl0/9V0xud8Ufm76LlpEQdDXR0I8EBgM1WLmRQoEmdXbrmDS+PNoUrzV7NTkcdlH5TL4cFoRL1viVGJ3Q+zGoPd32ybVyvO/iZf6
Mbhvu9YMcbWsQR1U4NvMpNyvEf715E/79edRVu9wRKDH7094LBSQRNoibW/xUyuHWyHpdR5Fic/ViIVSvREjPfRR0ClU1sB6DHRf6SFg
HNM61VXVARNiqobnhVboDSLkqgoebSHL7OuV/0f1wWLqPAzEif+MMjboVlVWfzE+mJZsoW8evNWLP8l9ZmzK2BvHX3FFQ3ToSlgj1bYR
iOV7XPZ9t1O+IkTLqDXiO0puxvFqmFVMEpIJqEJkzSZkyI/iemAl/9ImPAYXfS76dK0feU8crmXJ3nAEMZZfYHUzjQZk05HMAGfXMdW8
w0v9XDkUMTcZ7rypR50jsHTbzTTcA8xJ8DIm4ut28hpVUH8palC8DDun173iTScS3ES5WUiYBtsKQxdagJyiEHVUyjSgS336mYXtb2Ue
T2S43lpcgVU5xEJsqSmcXVy4GCWeoKs+ocGE8236RWJhyz/Q6m2iizEAEANuMXxqQ0okFYM8BwCfLH3NzS1EuF1s2Dz1+54fAY3Nh+Ed
iSHjWJP0E4kcVLivqWp8yRccLVdhZGj9GDVQuoFPxU2JXcGH6Ul5PcoL8YyP5G5Gi+RMNFAxUR5wpNZDroE5fwPh2ACHOQ==
</xenc:CipherValue>
 </xenc:CipherData>
 </xenc:EncryptedData>
 </ns3:validateResponse>
 </soapenv:Body>
</soapenv:Envelope>

<!-- page 48 -->

Final February 2013 Version 4.0 Page 48 of 52
APPENDIX I: IDP Response Message Example
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" >
 <soapenv:Header>
 <wsse:Security soapenv:mustUnderstand="1"
xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" >
 <xenc:EncryptedKey xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" >
 <xenc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#rsa-1_5"
 xmlns:dsig="http://www.w3.org/2000/09/xmldsig#" />
 <dsig:KeyInfo xmlns:dsig="http://www.w3.org/2000/09/xmldsig#" >
 <wsse:SecurityTokenReference>
 <wsse:KeyIdentifier
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile1.0#X509SubjectKeyIdentifier"
EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security1.0#Base64Binary">
MyXZQNYrNkOJu5c9ZOAH7su3vEM=
</wsse:KeyIdentifier>
 </wsse:SecurityTokenReference>
 </dsig:KeyInfo>
 <xenc:CipherData xmlns:dsig="http://www.w3.org/2000/09/xmldsig#" >
<xenc:CipherValue>
cVnBMyBtyf6ZxGVxcSxxIE/OOnxO3UeE+3g8WhgpE3T0y6Iaon4Hql+NbglmQtT3uS4VzR1vIDw+mXkJrJ58NPEUNruR4SVEV4BwjUo
fFpK5w5n/EtqNKHGmodnO7jt5JdQx5C+SQhUWgFaAm7g2yXuVjYQHYaOXoDWyjc/oZ5o=
</xenc:CipherValue>
 </xenc:CipherData>
 <xenc:ReferenceList>
 <xenc:DataReference URI="#G0xc40aa1f0-46D" />

<!-- page 49 -->

Final February 2013 Version 4.0 Page 49 of 52
 </xenc:ReferenceList>
 </xenc:EncryptedKey>
 <wsu:Timestamp wsu:Id="Timestamp-8f77724a-5ce1-46a2-951d-e5de094f48fe"
 xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" >
 <wsu:Created>2012-04-26T13:57:45Z</wsu:Created>
 <wsu:Expires>2012-04-26T14:02:45Z</wsu:Expires>
 </wsu:Timestamp>
 <wsse:BinarySecurityToken wsu:Id="SecurityToken-34b8f2ce-9815-431b-a8b4-1607e4649733"
EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3"
 xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" >
MIICaTCCAdKgAwIBAgIET2c7BjANBgkqhkiG9w0BAQUFADB5MQswCQYDVQQGEwJDQTEPMA0GA1UEERMGSzdLNlIxMRAwDgYDVQQIEwdPbnRhcm
lvMREwDwYDVQQHEwhraW5nc3RvbjENMAsGA1UEChMET0hJUDEMMAoGA1UECxMDUmVnMRcwFQYDVQQDEw4xNDIuMTQ1LjcwLjE3NzAeFw0xMjAz
MTkxMzU2MjJaFw0xMzAzMTkxMzU2MjJaMHkxCzAJBgNVBAYTAkNBMQ8wDQYDVQQREwZLN0s2UjExEDAOBgNVBAgTB09udGFyaW8xETAPBgNVBA
cTCGtpbmdzdG9uMQ0wCwYDVQQKEwRPSElQMQwwCgYDVQQLEwNSZWcxFzAVBgNVBAMTDjE0Mi4xNDUuNzAuMTc3MIGfMA0GCSqGSIb3DQEBAQUA
A4GNADCBiQKBgQCUXFSVaeqj68PC0gsyPXqyHokOYRHZbJ0C60pPtRrxXVSbYa22xxJiF3k4ZCHT8vrTH3Y/4cmh9am2x0QhWhLolYpraV+mAm
xC/h3qmuPwsnZwiOcVLlc/pCXJVNR/5pHG13WsmMASaun5JojCI7vlFKqSaJ0gxfsQqQTHoBXhWQIDAQABMA0GCSqGSIb3DQEBBQUAA4GBAEBk
OcmZjV3BRjdEZV8bUGXuifXcZpjTcu+q9IorDhqtoaRc6a6S8MnXxfmX6Ye+oVKMbTFknlhwPtwXceW/o39gHYB+/hsXnYRLnIC8sQsHolo2py
BEAUJFyG5IOZrGuceoF+rf51ZSXBJadKvPs/RyUxkaBDBjbB7woPwCD4wz
</wsse:BinarySecurityToken>
 <Signature xmlns="http://www.w3.org/2000/09/xmldsig#" >
 <SignedInfo>
 <CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
 <SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" />
 <Reference URI="#Timestamp-8f77724a-5ce1-46a2-951d-e5de094f48fe">
 <Transforms>
 <Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </Transforms>
 <DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1" />
 <DigestValue>BMKSgG7Xl9JCWsRlQXald6LiXSQ=</DigestValue>
 </Reference>

<!-- page 50 -->

Final February 2013 Version 4.0 Page 50 of 52
 <Reference URI="#Body-5843f7a8-8cc3-4a18-86dd-c77edfe5acb0">
 <Transforms>
 <Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
 </Transforms>
 <DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1" />
 <DigestValue>qgJj5jlCMnROE+nEUIt+6lJn4gU=</DigestValue>
 </Reference>
 </SignedInfo>
 <SignatureValue>
Hoq4Z2cqi9vrVzpdDZBseSbyqHn+Dq4KHO56MgbpRZ5NqOETrorQHiwM83cGaAQPSM2yBqfFYgHnOkOUD+yAl3VtTG1EUKpk35V08yl
MWE/Qie9j8/kVZCbvIGDiCub0vcmbVVKGRkffPBxFqzLD0IRe6K+Mns1ipi2mnMUh/40=
</SignatureValue>
 <KeyInfo>
 <wsse:SecurityTokenReference>
 <wsse:Reference URI="#SecurityToken-34b8f2ce-9815-431b-a8b4-1607e4649733"
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3" />
 </wsse:SecurityTokenReference>
 </KeyInfo>
 </Signature>
 </wsse:Security>
 </soapenv:Header>
 <soapenv:Body wsu:Id="Body-5843f7a8-8cc3-4a18-86dd-c77edfe5acb0"
 xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" >
 <ns3:validateResponse xmlns:ns3="http://hcv.health.ontario.ca/" xmlns:ns2="http://idp.ebs.health.ontario.ca/" >
 <xenc:EncryptedData Id="G0xc40aa1f0-46D" Type="http://www.w3.org/2001/04/xmlenc#Element"
 xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" >
 <xenc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc" />
 <xenc:CipherData>
 <xenc:CipherValue>

<!-- page 51 -->

Final February 2013 Version 4.0 Page 51 of 52
fzu/QbpnT/Mky3F3cWWkPooBsb86/04Ql862htLoYP0e7/EK4Ef/tRR1WmwZUIsnbUVamriL5DAaD9AajckbHV3idsRb1Kpb1na2driS
EWSbqc2wKNa656wpDxXXAfBTGPVs3BAoGOrH7PdoBTO3nY2qsekxN9Acgt1frQvVyup2x2twBgMA7d1N8bJr4D78aOdvuCIIAt9/YGMz
rsdg+8QauW6kgFGy7uA+E/tzhIpSPwIAW81R7RDdo76P8w24lINsRa3mjO3DJc5fsMGGjWykVVPSeNuQX7Yx/fuuumE7PJ7if69qPN+h
sjyP2EC5EFfZdOf7OZfw1jWBZj63FBXcYi42cSs0uQfx6vgrrw/JZyY6xgTtzMmR8HF1//jneN1r7LlmkbbRlm1xrTD1L7Ye10NOgrSg
FO/eYWM+M45zh7LeNSoxdlVDAHSmfv0ZdTmlR6X9RvQkhlVZ2bCkH5bflwiS0t65A2x8GnLRsRfaVyLpXIr0kDWwEb84vaqA3RZSeaXf
9p07tl0/9V0xud8Ufm76LlpEQdDXR0I8EBgM1WLmRQoEmdXbrmDS+PNoUrzV7NTkcdlH5TL4cFoRL1viVGJ3Q+zGoPd32ybVyvO/iZf6
Mbhvu9YMcbWsQR1U4NvMpNyvEf715E/79edRVu9wRKDH7094LBSQRNoibW/xUyuHWyHpdR5Fic/ViIVSvREjPfRR0ClU1sB6DHRf6SFg
HNM61VXVARNiqobnhVboDSLkqgoebSHL7OuV/0f1wWLqPAzEif+MMjboVlVWfzE+mJZsoW8evNWLP8l9ZmzK2BvHX3FFQ3ToSlgj1bYR
iOV7XPZ9t1O+IkTLqDXiO0puxvFqmFVMEpIJqEJkzSZkyI/iemAl/9ImPAYXfS76dK0feU8crmXJ3nAEMZZfYHUzjQZk05HMAGfXMdW8
w0v9XDkUMTcZ7rypR50jsHTbzTTcA8xJ8DIm4ut28hpVUH8palC8DDun173iTScS3ES5WUiYBtsKQxdagJyiEHVUyjSgS336mYXtb2Ue
T2S43lpcgVU5xEJsqSmcXVy4GCWeoKs+ocGE8236RWJhyz/Q6m2iizEAEANuMXxqQ0okFYM8BwCfLH3NzS1EuF1s2Dz1+54fAY3Nh+Ed
iSHjWJP0E4kcVLivqWp8yRccLVdhZGj9GDVQuoFPxU2JXcGH6Ul5PcoL8YyP5G5Gi+RMNFAxUR5wpNZDroE5fwPh2ACHOQ==
</xenc:CipherValue>
 </xenc:CipherData>
 </xenc:EncryptedData>
 </ns3:validateResponse>
 </soapenv:Body>
</soapenv:Envelope>

<!-- page 52 -->

Catalogue # CIB-XXXXXXX Month/Year © Queen’s Printer for Ontario
```
