# Technical Specification for the Outside Use Report (Patients with Signed Consent)

> **Source**: [http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_outside_use_en.pdf]
> **Fetched**: 2026-04-29  
> **Format**: extracted text from PDF via pypdfium2; tables may be space-aligned rather than GFM.  
> **Authoritative source**: the PDF at the URL above. If this MD and the PDF disagree, the PDF wins.

Page count: 26

---

```xml
<!-- page 1 -->

Technical Specification
for the
Outside Use Report
(Patients with Signed Consent)

Ministry of Health and Long-Term Care

<!-- page 2 -->

Table of Contents
Notice to Reader................................................................................................................... 3
Intended Audience for this Technical Specification Document....................................... 4
Introduction .......................................................................................................................... 4
Outside Use Report (Patients with Signed Consent) ........................................................ 4
XML Schemas....................................................................................................................... 5
Validation of a XML document against the XSD................................................................ 5
Testing .................................................................................................................................. 5
APPENDIX A: Medical Claims Electronic Data Transfer (MC EDT).................................. 6
APPENDIX B: Document Terminology .............................................................................. 8
Appendix C: Glossary of XML Terms ................................................................................. 9
APPENDIX D: Data Models ................................................................................................11
APPENDIX E: Technical Questions and Answers ...........................................................14
APPENDIX F: Sample Reports ..........................................................................................17
APPENDIX G: XML Schema...............................................................................................19
APPENDIX H: XML and XSD Samples ..............................................................................25
Outside Use Report (Patients with Signed Consent) ......................................................25

<!-- page 3 -->

June 2014 Page 3 of 26 Version 1.0
Notice to Reader
All possible measures are exerted to ensure the accuracy of the contents of this
technical specification document; however, the document may contain typographical or
printing or other errors. The reader is cautioned against complete reliance upon the
contents of the document without confirming the accuracy and currency of the
information contained in it. The Crown in Right of Ontario, as represented by the
Ministry of Health and Long-Term Care (MOHLTC), assumes no responsibility for errors
or omissions in any of the information contained in this manual, or for any person’s use
of the material therein, or for any costs or damages associated with such use. In no
event shall the Crown in Right of Ontario be liable for any errors or omissions, or for any
damages including, without limitation, damages for direct, indirect, incidental, special,
consequential or punitive damages arising out of or related to the use of information
contained in this manual.
This technical specification is intended only to assist and guide the development of
software and information management to access the reports outlined in this document.
This document does contain sample reports, XML (Extensible Markup Language) files.
The Ministry of Health and Long-Term Care will make revisions to the specification as
required and will make every effort to give as much advance notice as possible of such
revisions. It is essential that software developers keep current regarding any changes to
this specification.
• Please direct any questions to the Service Support Contact Centre (SSCC) at
1 800 262-6524 or SSContactCentre.MOH@ontario.ca.

<!-- page 4 -->

June 2014 Page 4 of 26 Version 1.0
Intended Audience for this Technical Specification Document
• This document is intended for use by developers of various software applications
and products. It describes the XML schemas for the Outside Use Report
(Patients with Signed Consent).
Introduction
This document is intended to provide the reader with sufficient information to
decompose XML documents related to medical claims payment information. More
specifically, the technical specifications contain annotated XML schema decomposition
information for the Outside Use Report.
The introduction provides an overview of the Outside Use Report, the Medical Claims
Electronic Data Transfer service and provides a glossary of the terminology used
throughout the document.
The reports will be available in PDF and XML format via the Medical Claims Electronic
Data Transfer (MC EDT) service which adheres to the Electronic Business Service
(EBS) security model, Identity Provider (IDP) and as such requires that the unique
ministry identifier for a Service Requestor (SR) has been established.
Although the ministry does provide an MC EDT web user interface for Enrolled Service
Users to also download the XML file, all program to program interfaces to consume
these reports and files MUST use the MC EDT web service and should never interface
to the user interface. The user interface can and will change from time to time without
notification.
See Appendix A within this document for a more information on the MC EDT Service.
Outside Use Report (Patients with Signed Consent)
Outside Use is a core service that is provided to enrolled patients by any family
physician who is not affiliated with the patient’s primary care group. The report includes
outside use details for each physician within a specific primary care group to assist in
the calculation of their Access Bonus payment.
This electronic report replaces the previous paper produced report.
See Appendices within this document for the terminology, abbreviations, glossary of
XML Terms, data models, sample reports, schema and XML file.

<!-- page 5 -->

June 2014 Page 5 of 26 Version 1.0
XML Schemas
The XML Schema definitions (XSD) for the Outside Use Report are well-formed and
valid.
The schema for each report is included in the Appendices.
Validation of a XML document against the XSD
A valid XML document contains a reference to a XML Schema Definition (XSD), and its
elements and attributes follow the grammatical rules that the XSD specifies.
The fields described in the schema definition are necessarily generic in order to follow
the XML data typing standards.
This format consists of a dataset element, which contains a metadata element and a
data element. The metadata element contains the data item information in item
elements. The data element contains all the row and value elements.
Testing
Testing of the XML Schemas has been performed according to W3C Standards. The
W3C organization has an XML Schema (XSD) Validation Tool, which is available online
over the World Wide Web.

<!-- page 6 -->

June 2014 Page 6 of 26 Version 1.0
APPENDIX A: Medical Claims Electronic Data Transfer (MC
EDT)
The MC EDT service is a framework which allows electronic file processing to and from
the ministry’s adjudication and reporting systems. Service users who are authenticated
to the MC EDT service can upload (send) files to the ministry for processing. Related
reports can also be retrieved through this information channel by authorized users or
their agents (designates).
Service users and their agents must first register and enroll for a set of security
credentials and be authenticated to the MC EDT service before they can upload (send)
or download (receive) reports or files. The contents and format of files remain exactly as
transmitted from the service user or from the ministry's information technology systems.
The screen print below illustrates how the Outside Use Reports will appear as menu
selection options via the MC EDT Web Page, and List of Reports. The Report Name will
appear under the Subject heading and there is a two digit identifier assigned to each
report in the file name under the Item heading.
Here’s how you decipher the file name LEOU4567.123 shown under the Item heading
above:
L = First alpha character is a unique identifier referencing the program
area
(L = Primary Care)
E = The next alpha character is the report month
(Where A = Jan, B = Feb, C = March, L = December),
in this example E = May
OU = The next 2 alpha characters are the unique identifiers for the type
of report
(OU = Outside Use Report)
9999 = The 4 digits are the MC EDT Service Tracking Serial Number,
in this example 4567
.999 = The last 3 digits are the file extension,
in this example .123
Therefore, the report file name in the above sample refers to the Primary Care, Outside
Use Report, for the month of May.

<!-- page 7 -->

June 2014 Page 7 of 26 Version 1.0
Listed below is the two digit identifier for each of the reports:
Report Name Report Name
(as seen in MC EDT, and List of
Reports)
Report Type
2 Digit Identifier
Outside Use Report
(Patients with Signed
Consent)
Outside Use OU
For more information on the MC EDT service please refer to the current version of the
technical specification for Medical Claims Electronic Data Transfer (MCEDT) service at
the following URL:
http://www.health.gov.on.ca/english/providers/pub/pub_menus/pub_ohip.html
Please direct any MC EDT questions to the Service Support Contact Centre (SSCC)
at 1 800 262-6524 or SSContactCentre.MOH@ontario.ca.

<!-- page 8 -->

June 2014 Page 8 of 26 Version 1.0
APPENDIX B: Document Terminology
Term Definition
Electronic Business
Services
(EBS)
The Electronic Business Service is a framework which
provides an electronic business gateway that exposes
Ministry of Health and Long-Term Care (MOHLTC)
services to the Broader Health Sector and provides a
full featured IAM suite of provisioning, business
enrolment, business and IT federation agreements,
technical specifications and terms of acceptable use
governance.
Identity Provider (IDP) A party or organization that creates, maintains, and
manages identity information for principals and
performs principal authentication for other parties or
organizations.

<!-- page 9 -->

June 2014 Page 9 of 26 Version 1.0
Appendix C: Glossary of XML Terms
The table below lists each XML field name and its associated field name in the paper
report, along with a short description for the field name.
(From Source:
http://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/electronic_reports/do
cs/glossary_terms.pdf)
GLOSSARY OF XML TERMS
XML Field Name Paper Report
Field Name
Description
Group ID Group A ministry registration number
assigned to organizations to
facilitate payment consolidation.
Group Name Group The name of the organization
which facilitates payment
consolidation.
Group Type Group/Solo Level
Name
Name to describe the type of
group (will be either Group or
Solo).
Patient First Name Patient Name, First
Name
Patient’s first name.
Patient Health Number Health Number,
Patient
HN
The unique 10 digit individual
health identification number
assigned by the ministry to
eligible Ontario residents.
Provider First Name Physician, Provider Provider’s first name.
Provider Last Name Physician, Provider Provider’s last name.
Provider Middle Name Physician, Provider Provider’s middle name.
Provider Number Physician, Provider A ministry registration number
assigned to individual providers
who are lawfully entitled to
provide insured services.
Service Amt Fee Paid Amount of payment.
Service Code Fee Code Code which appears opposite the
description of insured benefits
listed in the various MOHLTC
Schedules of Benefits and Facility
Fee Schedule.

<!-- page 10 -->

June 2014 Page 10 of 26 Version 1.0
GLOSSARY OF XML TERMS
XML Field Name Paper Report
Field Name
Description
Service Date Service Date Date the patient received the
service.
YYYYMMDD
Service Description Description Description of the service code.

<!-- page 11 -->

June 2014 Page 11 of 26 Version 1.0
APPENDIX D: Data Models
From Source
http://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/electronic_reports/el
ectronic.aspx
Option data models and Option “replace the following paper”
http://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/electronic_reports/da
ta_models.aspx
DATA MODEL for Outside Use Report (Patients with Signed Consent)
Models without Descriptions on Reports for Group Health Centre and
Community Sponsored Agreement Models


REPORT
REPORT-ID
REPORT-DATE
REPORT-NAME
REPORT-PERIOD-START
REPORT-PERIOD-END
CHAR (10)
CHAR (10)
CHAR (50)
CHAR (10)
CHAR (10)
GROUP
GROUP-ID
GROUP-TYPE
GROUP-NAME
CHAR (04)
CHAR (03)
CHAR (75)
PROVIDER-NUMBER
PROVIDER-LAST-NAME
PROVIDER-FIRST-NAME
PROVIDER-MIDDLE-NAME
CHAR (06)
CHAR (30)
CHAR (20)
CHAR (20)
PROVIDER

<!-- page 12 -->

June 2014 Page 12 of 26 Version 1.0
DATA MODEL for Outside Use Report (Patients with Signed Consent)
Models without Descriptions on Reports for Group Health Centre and
Community Sponsored Agreement Models (continued)
PATIENT
SERVICE
PATIENT-HEALTH- NUMBER
PATIENT-LAST-NAME
PATIENT-FIRST-NAME
PATIENT–BIRTHDATE
PATIENT-SEX
CHAR (10)
CHAR (30)
CHAR (20)
CHAR (10)
CHAR (01)
SERVICE–DATE
SERVICE–CODE
CHAR (10)
CHAR (05)

<!-- page 13 -->

June 2014 Page 13 of 26 Version 1.0
From Source
http://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/electronic_reports/el
ectronic.aspx
Option data models
http://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/electronic_reports/da
ta_models.aspx
DATA Model for Outside Use Report (Patients with Signed Consent)
Models with Descriptions on Reports for Family Health Network and Family
Health Organization Models
REPORT
REPORT-ID
REPORT-DATE
REPORT-NAME
REPORT-PERIOD-START
REPORT-PERIOD-END
CHAR (10)
CHAR (10)
CHAR (50)
CHAR (10)
CHAR (10)
GROUP
GROUP-ID
GROUP-TYPE
GROUP-NAME
CHAR (04)
CHAR (03)
CHAR (75)
PROVIDER
PROVIDER-NUMBER
PROVIDER-LAST-NAME
PROVIDER-FIRST-NAME
PROVIDER-MIDDLE-NAME
CHAR (06)
CHAR (30)
CHAR (20)
CHAR (20)

<!-- page 14 -->

June 2014 Page 14 of 26 Version 1.0
DATA Model for Outside Use Report (Patients with Signed Consent)
Models with Descriptions on Reports for Family Health Network and Family
Health Organization Models (continued)
PATIENT
PATIENT-HEALTHNUMBER
PATIENT -LAST-NAME
PATIENT-FIRST-NAME
PATIENT –BIRTHDATE
PATIENT-SEX
CHAR (10)
CHAR (30)
CHAR (20)
CHAR (10)
CHAR (01)
SERVICE
SERVICE–DATE
SERVICE–CODE
SERVICE–DESCRIPTION
SERVICE–AMT
CHAR (10)
CHAR (05)
CHAR (39)
DECIMAL (9.2)

<!-- page 15 -->

June 2014 Page 15 of 26 Version 1.0
APPENDIX E: Technical Questions and Answers
Technical questions and answers about the delivery of the Outside Use Report via
MCEDT are listed below:
Question Answer
1. When will these
reports are available
electronically?
The Outside Use Report was available in EDT in the fall
of 2009 and is currently available via MC EDT.
2. Why are these reports
being provided in
electronic format?
This is the first initiative in the ministry’s long term
strategy to provide current and future reports
electronically to allow flexibility to customize reports.
3. Why are reports in
XML format?
Extensible Markup Language (XML) is a recognized
international standard for the encoding of data
exchanged between third parties. It is widely used in
both the private and public sectors and is an official
standard of the Ontario Government. XML was chosen
as the encoding standard due to its ease of
implementation and the flexibility it offers when
modifying reports.
4. What is an XML
parser?
An XML parser is the piece of software that reads an
XML file, recognizing the structure of the data it
contains, and makes the data available to software
applications. It tests whether a document is well- formed and, if given an XML schema, it will also
check for validity (i.e., it determines if the document
follows the syntactic and semantic rules defined by
the schema).
5. What kind of software
packages have an
XML parser built in?
Microsoft Word and Excel both contain XML parsers,
enabling them to recognize and display well-formed
XML documents. Windows® operating systems since
the 2000 version and Internet Explorer browsers
version 5 and subsequent versions include a
validating XML parser.
Most third party internet browsers such as Firefox,
Chrome, Safari, Opera, Lynx, Archne and Epiphany
also have an XML parser built in. To determine if the
software application is XML enabled, have it try to
read one of the schemas or sample report files on the
XML Schemas and Sample Reports link. Otherwise,
contact your vendor.

<!-- page 16 -->

June 2014 Page 16 of 26 Version 1.0
Question Answer
6. Can a Macintosh
computer read
these XML
reports?
Yes, Macintosh computers can read the electronic
report files and are currently in use for the submission
of claims via EDT as well as retrieval of files sent to
EDT mailboxes.
7. Are Clinical
Management Systems
(CMS) equipped to
handle
?
Please follow up with your CMS vendor.
8. When will these
reports be available to
download through the MC EDT test
environment?
The Outside Use Report is available via MC EDT.
9. What are the benefits
of the sample reports?
The sample reports will assist vendors in testing
parsers created from the schemas to read the XML
reports. They are a starting point for vendors to
develop test cases for their parsers and should not be
considered to provide comprehensive test coverage.
10. Have the contents of
the paper reports
been changed?
No, the paper reports have not changed and the XML
reports have all of the same data elements.

<!-- page 17 -->

June 2014 Page 17 of 26 Version 1.0
APPENDIX F: Sample Reports
A. Outside Use Report (Patients with Signed Consent) Models with Descriptions on Reports
for Group Health Centre and Community Sponsored Agreement Models

<!-- page 18 -->

June 2014 Page 18 of 26 Version 1.0
B. Outside Use Report (Patients with Signed Consent) Models with Descriptions on Reports
for Family Health Network and Family Health Organization Models

<!-- page 19 -->

June 2014 Page 19 of 26 Version 1.0
APPENDIX G: XML Schema
<?xml version="1.0" encoding="utf-8"?>
<xs:schema id="REPORT" xmlns="" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:msdata="urn:schemasmicrosoft-com:xml-msdata">
<!--XSD specification created by Don Wood-->
<xs:annotation>
 <xs:documentation>PCRP60R1-C Outside Use (Patients with Signed Consent)</xs:documentation>
</xs:annotation>
<!-- Create a type to restrict the data to a max length of 20 -->
 <xs:simpleType name="maxLenString">
<xs:restriction base="xs:string">
 <xs:maxLength value="20"/>
</xs:restriction>
 </xs:simpleType>
 <xs:element name="REPORT" msdata:IsDataSet="true" msdata:Locale="en-US">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="REPORT-DTL">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="REPORT-ID" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="10"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>

<!-- page 20 -->

June 2014 Page 20 of 26 Version 1.0
 <xs:element name="REPORT-DATE" type="xs:date" minOccurs="1" />
 <xs:element name="REPORT-NAME" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="50"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 <xs:element name="REPORT-PERIOD-START" type="xs:date" minOccurs="1" />
 <xs:element name="REPORT-PERIOD-END" type="xs:date" minOccurs="1" />
 </xs:sequence>
 </xs:complexType>
 </xs:element>
 <xs:element name="GROUP">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="GROUP-DTL" minOccurs="1" maxOccurs="unbounded">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="GROUP-ID" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="4"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 <xs:element name="GROUP-TYPE" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="3"/>

<!-- page 21 -->

June 2014 Page 21 of 26 Version 1.0
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 <xs:element name="GROUP-NAME" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="75"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 </xs:sequence>
 </xs:complexType>
 </xs:element>
 <xs:element name="PROVIDER" minOccurs="1" maxOccurs="unbounded">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="PROVIDER-DTL" minOccurs="1" maxOccurs="unbounded">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="PROVIDER-NUMBER" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="6"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 <xs:element name="PROVIDER-LAST-NAME" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="30"/>
 </xs:restriction>

<!-- page 22 -->

June 2014 Page 22 of 26 Version 1.0
 </xs:simpleType>
 </xs:element>
 <xs:element name="PROVIDER-FIRST-NAME" type="maxLenString" minOccurs="1" />

 <xs:element name="PROVIDER-MIDDLE-NAME" type="maxLenString" minOccurs="1" />

 </xs:sequence>
 </xs:complexType>
 </xs:element>
 <xs:element name="PATIENT" minOccurs="1" maxOccurs="unbounded">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="PATIENT-DTL" minOccurs="1" maxOccurs="unbounded">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="PATIENT-HEALTH-NUMBER" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="10"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>

 <xs:element name="PATIENT-LAST-NAME" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="30"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>

<!-- page 23 -->

June 2014 Page 23 of 26 Version 1.0
 <xs:element name="PATIENT-FIRST-NAME" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="20"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 <xs:element name="PATIENT-BIRTHDATE" type="xs:date" minOccurs="1" />
 <xs:element name="PATIENT-SEX">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:enumeration value="M"/>
 <xs:enumeration value="F"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 </xs:sequence>
 </xs:complexType>
 </xs:element>
 <xs:element name="SERVICE-DTL1" minOccurs="1" maxOccurs="unbounded">
 <xs:complexType>
 <xs:sequence>
 <xs:element name="SERVICE-DATE" type="xs:date" minOccurs="1" />
 <xs:element name="SERVICE-CODE" minOccurs="1">
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="5"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
<xs:element name="SERVICE-DESCRIPTION" minOccurs="0">

<!-- page 24 -->

June 2014 Page 24 of 26 Version 1.0
 <xs:simpleType>
 <xs:restriction base="xs:string">
 <xs:maxLength value="39"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 <xs:element name="SERVICE-AMT" minOccurs="0">
 <xs:simpleType>
 <xs:restriction base="xs:decimal">
 <xs:totalDigits value="9"/>
<xs:fractionDigits value="2"/>
 </xs:restriction>
 </xs:simpleType>
 </xs:element>
 </xs:sequence>
 </xs:complexType>
 </xs:element>
 </xs:sequence>
 </xs:complexType>
 </xs:element>
 </xs:sequence>
 </xs:complexType>
 </xs:element>
 </xs:sequence>
 </xs:complexType>
 </xs:element>
 </xs:sequence>
 </xs:complexType>
 </xs:element>
</xs:schema>

<!-- page 25 -->

June 2014 Page 25 of 26 Version 1.0
APPENDIX H: XML and XSD Samples
Outside Use Report (Patients with Signed Consent)
OU_SAMPLE.xml
Outside Use
(PCRP60R1-C).xsd

<!-- page 26 -->

Catalogue # CIB-XXXXXXX Month/Year © Queen’s Printer for Ontario

```