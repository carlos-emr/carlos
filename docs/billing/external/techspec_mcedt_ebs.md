# Technical Specification for Medical Claims Electronic Data Transfer (MCEDT) Service via Electronic Business Services (EBS)

> **Source**: [http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspec_mcedt_ebs.pdf]
> **Fetched**: 2026-04-29  
> **Format**: extracted text from PDF via pypdfium2; tables may be space-aligned rather than GFM.  
> **Authoritative source**: the PDF at the URL above. If this MD and the PDF disagree, the PDF wins.

Page count: 69

---
```xml
<!-- page 1 -->

Technical Specification for Medical
Claims Electronic Data Transfer
(MCEDT) Service via Electronic
Business Services (EBS)
Ministry of Health and Long-Term Care
March 2013
EBS-EDT SOAP Specification
Version 3.0

<!-- page 2 -->

Table of Contents
Techical Specifications for Medical Claims Electronic Data Transfer (MCEDT)
Service Via Electronic Business Services (EBS)........................................................ 4
Glossary......................................................................................................................... 4
Notice and Disclaimer................................................................................................... 6
Intended Audience for this Technical Specification Document................................ 7
About This Document................................................................................................... 8
Introduction ................................................................................................................... 9
Medical Claims Electronic Data Transfer..................................................................... 9
Web Service Interface for MCEDT Service.................................................................. 9
Technical Interface...................................................................................................... 10
SOAP Message:............................................................................................................. 10
The Message WSDL...................................................................................................... 10
WSDL Definitions Table................................................................................................ 10
Validation Message Schema ...................................................................................... 11
Data Specifications for Fields....................................................................................... 11
Testing ......................................................................................................................... 25
APPENDIX A: Error Codes ......................................................................................... 26
APPENDIX B: The Message WSDL ............................................................................ 27
APPENDIX C: Message Schema ................................................................................ 42
APPENDIX D: MSA Model Message Example ........................................................... 53
APPENDIX E: IDP Model Message Example ............................................................. 58
MCEDT Currently Supported File/Resource Types Addendum .............................. 64
Claim File Upload Error Codes Addendum............................................................... 66
OBEC File Upload Error Codes Addendum .............................................................. 67
Stale Dated Claims File Upload Error Codes Addendum ........................................ 68

<!-- page 3 -->

Techical Specifications for Medical
Claims Electronic Data Transfer
(MCEDT) Service Via Electronic
Business Services (EBS)

<!-- page 4 -->

Final March 2013 Version 3.0 Page 4 of 69
Chapter 1
Techical Specifications for Medical Claims Electronic Data
Transfer (MCEDT) Service Via Electronic Business Services
(EBS)
Glossary
Term Definition
Claim Submission Number
(CSN) aka Billing Number
A unique identifier that is assigned to a Health Care
Provider who is registered with MOHLTC for the
purpose of submitting claims for insured services.
MCEDT Service Schedule A Service Schedule to the Master Service agreement
that forms part of the Agreement between the Service
Requestor and MOHLTC and captures the roles and
responsibilities that relate to the MCEDT via EBS.
Health Care Provider (HCP) Individual, group or facility licensed to provide health
care services to eligible residents of Ontario.
Health Information
Custodian (HIC)
Health Information Custodian in or under Personal
Health & Information Privacy Act (PHIPA).
Identity Provider (IDP) A party or organization that creates, maintains, and
manages identity information for principals and
performs principal authentication for other parties or
organizations.
Master Services
Agreement (MSA)
The binding legal agreement through which MOHLTC
accepts the identity of an end user at face value based
on authenticating the end user’s organization at the
time of the service request.
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

<!-- page 5 -->

Final March 2013 Version 3.0 Page 5 of 69
Term Definition
Message Transmission
Optimization Mechanism
(MTOM)
A method of efficiently sending binary data to and from
web services. For more information refer to
http://www.w3.org/TR/soap12-mtom/
Output (Response)
Message Fields
Output (Response) Message fields are those fields
which are returned by the Medical Claims Electronic
Data Transfer web service.
Service Provider (SP) Throughout this document, Service Provider refers
exclusively to MOHLTC, as the provider of the Medical
Claims Electronic Data Transfer service via EBS.
Simple Object Access
Protocol (SOAP)
Simple Object Access Protocol: an Extensible Markup
Language (XML)-based protocol for exchanging
structured information between computer systems. For
more information refer to http://www.w3.org/TR/soap/
Stakeholder Number (SN) A unique identifier that is assigned to stakeholders of
interest who are registered with the MOHLTC. The
unique identifier is either 7 digits long or 8 digits long
depending on the type of stakeholder to which it
belongs.
UUID A version 4 Universally Unique Identifier (UUID).
Web Services Description
Language (WSDL)
Web Services Description Language: an XML-based
language for describing web services and how to
access them. For more information refer to
http://www.w3.org/TR/wsdl

<!-- page 6 -->

Final March 2013 Version 3.0 Page 6 of 69
Notice and Disclaimer
All possible measures are exerted to ensure accuracy of the contents of this manual;
however, the manual may contain typographical, printing or other errors. The reader is
cautioned against complete reliance upon the contents of the manual without confirming
the accuracy and currency of the information contained in it. The Crown in Right of
Ontario, as represented by the Ministry of Health and Long-Term Care (MOHLTC),
assumes no responsibility for errors or omissions in any of the information contained in
this manual, or for any person’s use of the material therein, or for any costs or damages
associated with such use. In no event shall the Crown in Right of Ontario be liable for
any errors or omissions, or for any damages including, without limitation, damages for
direct, indirect, incidental, special, consequential or punitive damages arising out of or
related to the use of information contained in this manual.
This technical specification is intended only to assist and guide the development of
software to access the Medical Claims Electronic Data Transfer (MCEDT) web service
via the MOHLTC Electronic Business Service (EBS).
Revisions to the specification will be made as required. The ministry will make every
effort to give as much advance notice as possible for future revisions. It is essential that
software developers keep current regarding any changes to this specification. The
current version of the technical specification will be available for download at the
following URL:
http://www.health.gov.on.ca/english/providers/pub/pub_menus/pub_ohip.html
For further details about MCEDT via EBS service including enrolment criteria please
refer to the MCEDT Reference Manual posted at:
http://www.health.gov.on.ca/english/providers/pub/pub_menus/pub_ohip.html
This document does not describe the technical specifications of the specific files that
can be uploaded and downloaded by the MCEDT application. For information on
specific file format specifications please refer to the individual technical specification
(EBS-EDT SOAP Specifications) at the following URL:
http://www.health.gov.on.ca/english/providers/pub/pub_menus/pub_ohip.html
Please direct any questions to the Service Support Contact Centre (SSCC) at 1 800
262-6524 or SSContactCentre.MOH@ontario.ca

<!-- page 7 -->

Final March 2013 Version 3.0 Page 7 of 69
Intended Audience for this Technical Specification Document
This document is intended for use by developers of applications and products that
support communication with MOHLTC’s MCEDT via EBS (web service interface) a
service provided by the ministry to support electronic data transfer. This service is built
to the web services standards detailed in this document.
The ministry does not provide any support for automation of the public MCEDT web
page.
This document is also intended to be read in the context of either a service agreement
between the ministry and the Service Requestor or through an accepted IDP. The
service agreement is defined by a Master Service Agreement (MSA) and an MCEDT
Service Schedule between the ministry and the Service Requestor (SR).
This technical specification is also targeted to vendors of various software applications
and products that have or plan to have modules that support MCEDT through a web
service interface within the province of Ontario in Canada.
The document describes the web service, the SOAP message specification and aims to
guide the users in the development of client application to integrate with this web
service.
It is assumed that the reader has knowledge of web services and related protocols,
SOAP and XML message formats/processing, relevant interoperability profiles and
identity assertions and has read the ‘MOHLTC EBS - Generic Security SOAP
Specification’ document.

<!-- page 8 -->

Final March 2013 Version 3.0 Page 8 of 69
About This Document
The Ministry of Health and Long-Term Care Medical Claims Electronic Data Transfer
service allows health care providers (HCP) to send and receive electronic data with the
ministry.
This document is an extension of the “MOHLTC EBS – Generic Security SOAP
Specification” document and is intended to provide the reader with sufficient information
to implement service requestor software that can use the service.
The MCEDT service will only support the IDP security model in its first release.
The introduction provides an overview of the MCEDT service and provides a glossary of
the terminology used throughout the document. Additional functional information and
overview of the MCEDT process is provided in the MCEDT reference manual published
at:
http://www.health.gov.on.ca/english/providers/pub/pub_menus/pub_ohip.html
The Simple Object Access Protocol (SOAP) Message Section provides the technical
specifications of the SOAP message including:
• Message Web Services Description Language (WSDL);
• Validation message schema including the request and the response;
• Data specifications for fields; and
• MTOM will be used to transfer all attachments.
Appendices provide:
• Response codes;
• The Message WSDL; and
• SOAP message examples

<!-- page 9 -->

Final March 2013 Version 3.0 Page 9 of 69
Introduction
The Medical Claims Electronic Data Transfer service adheres to the EBS security
models. As such, it requires that the unique ministry identifier for Service Requestor
(SR) be a Stakeholder Number (SN), and for Service User (SU) be either a SN or a
Claim Submission Number (CSN).
Although the ministry does provide a simple user interface for MCEDT all program to
program interfaces MUST use this web service and should never interface to the user
interface. The user interface can and will change time to time without notification.
Medical Claims Electronic Data Transfer
The MCEDT service is a framework which allows electronic file processing to and from
the ministry’s adjudication and reporting systems. Service users who are authenticated
to the MCEDT service can upload (send) files to the ministry for processing. Related
reports can also be retrieved through this information technology channel by authorized
users or their agents (designates). Service users and their agents must first register and
enroll for a set of new security credentials before they can be authenticated to the
MCEDT service before they can upload (send) or download (receive) reports or files.
The contents and format of files remain exactly as transmitted from the service user or
from the ministry's information technology systems.
For more information refer to the MCEDT Reference manual.
Web Service Interface for MCEDT Service
The Province of Ontario, via the Ministry of Health and Long-Term Care, offers MCEDT
via EBS for users through third party or client software.

<!-- page 10 -->

Final March 2013 Version 3.0 Page 10 of 69
Technical Interface
The Province of Ontario is responsible and accountable for the service provider
component.
The service interface uses the SOAP protocol for communication and the WS-Security
(WSS) protocol for transaction security. There are several implementations of the WSS
protocol available and it is suggested that one of those be used where possible. The
following sections assume appropriate headers are included as defined by the “EBS –
Generic Security SOAP Specification”.
The response message will be signed using the EBS certificate and the results object of
the response will be encrypted using the AES128-CBC encryption algorithm and will
need to be decrypted by the caller before using the returned data.
SOAP Message:
SOAP is an XML-based standard protocol that defines a message specification for
transmitting XML documents via a network. Since this message specification does not
depend on a particular programming language or operating system (OS), data transfer
can be conducted among and between systems that use different languages or
operating systems.
All attachments will be sent and received using the MTOM attachment protocol.
The Message WSDL
A WSDL is a specification for coding web services-related information (access point and
interface specifications, etc.) in XML. Note that while WSDL does not define a protocol
when sending/receiving messages, the ministry is using SOAP via HTTPS as the
protocol for message transmission.
WSDL Definitions Table
The WSDL includes the following standard elements:
Variable Description
MCEDT
Service URI
The URI of the MCEDT web service in the form of:
Internet
Access:https://ws.ebs.health.gov.on.ca:1441/EDTService/EDTService
ENA Access:
https://intra.ws.ebs.health.gov.on.ca:1441/EDTService/EDTService
The complete WSDL is included in Appendix B.

<!-- page 11 -->

Final March 2013 Version 3.0 Page 11 of 69
Validation Message Schema
The MCEDT message schema includes definitions of both the request and the
response elements. Schema elements are described below, and the full schema is
included in Appendix C.
Data Specifications for Fields
The fields described in the message specification are necessarily generic in order to
follow the XML data typing standards. However, in order to pass validation, some of the
fields must be presented in the format expected by the ministry.
WS-Security will be used to encrypt the returning data. All returning attachments will be
encrypted with the public key of the callers signing certificate. For more information
please refer to the ‘MOHLTC EBS – Generic Security Specification’ document. For
information on the currently supported file/resource types refer to the ‘MCEDT
CURRENTLY SUPPORT FILE/RESOURCE TYPES’ addendum.
The maximum attachment size that will be accepted by MCEDT is 10 megabytes.
Methods
Upload
The operation will be used to upload one or up to 5 documents from external
users.
Input (Request) Message Fields
name=”content” type=”xs:base64Binary”
The content of the file being uploaded. The content is sent as an attachment
using the MTOM protocol. The maximum attachment size of 10 megabytes.
name=”description” type=”xs:string”
A custom description of the file. The maximum length is 50 characters.
name=”resourceType” type=”xs:string”
A resource type as specified by the getTypeList method.

<!-- page 12 -->

Final March 2013 Version 3.0 Page 12 of 69
Output (Response) Message Fields
name=”auditID” type=”xs:string” pattern=
”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID).
A UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of
32 hexadecimal digits, displayed in 5 groups separated by hyphens, in the form
8-4-4-4-12 for a total of 36 characters (32 digits and 4 hyphens).
name=”description” type=”xs:string”
The description sent in the input data for the file. The maximum length is 50
characters.
name=”resourceID” type=”xs:integer”
The EDT Identification of the resource uploaded.
name=”code” type=”xs:string”
The response code for the request.
Code Description
IEDTS0001 Success
EEDTS0003 Resource Type Not Found
EEDTS0010 File Upload Failed
EEDTS0012 MOH ID not Valid
NOTE: Specific error response codes can be returned based on the type of
file being uploaded. The error codes for specific file types can be found as
addendums to this technical specification.
name=”msg” type=”xs:string”
The response code message for the request.
name=”status” type=” tns:resourceStatus”
The current status of the resource. One of UPLOADED, SUBMITTED, WIP,
DOWNLOADABLE, DELETED, APPROVED, DENIED.

<!-- page 13 -->

Final March 2013 Version 3.0 Page 13 of 69
Submit
The operation submits a list of documents to be processed by the ministry.
Input (Request) Message Fields
maxoccurs=”100” minOccurs=”1” name=”resourceIDs” type=”xs: integer”
The list of file ids to submit.
Output (Response) Message Fields
name=”auditID” type=”xs:string” pattern=
”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID).
A UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of
32 hexadecimal digits, displayed in 5 groups separated by hyphens, in the form
8-4-4-4-12 for a total of 36 characters (32 digits and 4 hyphens).
name=”description” type=”xs:string”
The custom description sent when the resource was uploaded or updated.
Maximum length is 50 characters.
name=”resourceID” type=”xs:integer”
The file id of the file submitted.
name=”code” type=”xs:string”
The response code for the request.

<!-- page 14 -->

Final March 2013 Version 3.0 Page 14 of 69
Code Description
IEDTS0001 Success
EEDTS0012 MOH ID not Valid
EEDTS0050 User not Allowed
EEDTS0051 No Data for Processing
EEDTS0052 Data Processing failed
EEDTS0054 User that is submitting the resource is not
the same as the user that uploaded it.
EEDTS0055 The resource is not in the upload status so
cannot be submitted
EEDTS0056 The resource id specified cannot be found.
name=”msg” type=”xs:string”
The response code message for the request.
name=”status” type=” tns:resourceStatus”
The current status of the resource. One of UPLOADED, SUBMITTED, WIP,
DOWNLOADABLE, DELETED, APPROVED, DENIED.
Download
The operation downloads a list of up to 5 documents from the ministry.
Input (Request) Message Fields
maxoccurs=”5” minOccurs=”1” name=”resourceIDs” type=”xs:integer”
The list of file ids to download.

<!-- page 15 -->

Final March 2013 Version 3.0 Page 15 of 69
Output (Response) Message Fields
name=”auditID” type=”xs:string” pattern=
”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID).
A UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of
32 hexadecimal digits, displayed in 5 groups separated by hyphens, in the form
8-4-4-4-12 for a total of 36 characters (32 digits and 4 hyphens).
name=”content” type=”xs:base64Binary”
The content of the file being downloaded. The content is received as an
attachment using the MTOM protocol.
name=”resourceID” type=”xs:integer”
The file id of the file dowloaded.
name=”resourceType” type=”xs:string”
A resource type as specified by the getTypeList method.
name=”description” type=”xs:string”
The description sent in the input data for the file. The maximum length is 50
characters.
name=”code” type=”xs:string”
The response code for the request.
Code Description
IEDTS0001 Success
EEDTS0012 MOH ID not Valid
EEDTS0050 User not Allowed
EEDTS0051 No Data for Processing
EEDTS0056 The resource id specified cannot be found.

<!-- page 16 -->

Final March 2013 Version 3.0 Page 16 of 69
name=”msg” type=”xs:string”
The response code message for the request.
List
The operation returns a list of document references and attributes as specified by
the search criteria of the caller. The results are broken into pages of up to 50
items each.
Input (Request) Message Fields
Optional Fields
name=“resourceType” type=”xs:string”
A resource type as specified by the getTypeList method.
name=“status” type=” tns:resourceStatus”
The current status of the resource. One of UPLOADED, SUBMITTED,
WIP, DOWNLOADABLE, DELETED, APPROVED, DENIED.
name=“pageNo” type=“ xs:interger”
The page number of the results page that is to be returned. Page numbers
are 1 based and this should be 1 for the initial request.
Output (Response) Message Fields
name=”auditID” type=”xs:string” pattern=
”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID).
A UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of
32 hexadecimal digits, displayed in 5 groups separated by hyphens, in the form
8-4-4-4-12 for a total of 36 characters (32 digits and 4 hyphens).

<!-- page 17 -->

Final March 2013 Version 3.0 Page 17 of 69
name=”createTimestamp” type=”xs:dateTime”
The timestamp when the file was uploaded.
name=”description” type=”xs:string”
The description sent in the input data when the file was uploaded. The maximum
length is 50 characters.
name=”resourceType” type=”xs:string”
A resource type as specified by the getTypeList method.
name=”modifyTimestamp” type=”xs:dateTime”
The timestamp when the file was last modified.
name=”resourceID” type=”xs:integer”
The file id of the file uploaded/submitted.
name=”code” type=”xs:string”
The response code for the request.
Code Description
IEDTS0001 Success
EEDTS0012 MOH ID not Valid
EEDTS0050 User not Allowed
name=”msg” type=”xs:string”
The response code message for the request.
name=”status” type=” tns:resourceStatus”
The current status of the resource. One of UPLOADED, SUBMITTED, WIP,
DOWNLOADABLE, DELETED, APPROVED, DENIED.

<!-- page 18 -->

Final March 2013 Version 3.0 Page 18 of 69
name=”status” type=”ts:integer”
This represents the total number of pages in the request.
Info
The operation returns a list of document attributes for the specified document ids.
Input (Request) Message Fields
maxoccurs=”100” minOccurs=”1” name=”resourceIDs” type=”xs:integer”
The list of file ids to get info on.
Output (Response) Message Fields
name=”auditID” type=”xs:string” pattern=
”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID).
A UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of
32 hexadecimal digits, displayed in 5 groups separated by hyphens, in the form
8-4-4-4-12 for a total of 36 characters (32 digits and 4 hyphens).
name=”createTimestamp” type=”xs:dateTime”
The timestamp when the file was uploaded.
name=”description” type=”xs:string”
The description sent in the input data for the file when uploaded. The maximum
length is 50 characters.
name=”resourceType” type=”xs:string”
A resource type as specified by the getTypeList method.
name=”modifyTimestamp” type=”xs:dateTime”
The timestamp when the file was last modified.
name=”resourceID” type=”xs:integer”

<!-- page 19 -->

Final March 2013 Version 3.0 Page 19 of 69
The file id of the file uploaded/submitted.
name=”code” type=”xs:string”
The response code for the request.
Code Description
IEDTS0001 Success
EEDTS0012 MOH ID not Valid
EEDTS0050 User not Allowed
EEDTS0051 No Data for Processing
EEDTS0056 The resource id specified cannot be found.
name=”msg” type=”xs:string”
The response code message for the request.
name=”status” type=” tns:resourceStatus”
The current status of the resource. One of UPLOADED, SUBMITTED, WIP,
DOWNLOADABLE, DELETED, APPROVED, DENIED.
Delete
The operation will delete the specified documents from the MCEDT system. Files
can only be deleted if they have been uploaded and/or updated but not
submitted. Downloadable files cannot be deleted.
Input (Request) Message Fields
maxoccurs=”100” minOccurs=”1” name=”resourceIDs” type=”xs:integer”
The list of file ids to delete.

<!-- page 20 -->

Final March 2013 Version 3.0 Page 20 of 69
Output (Response) Message Fields
name=”auditID” type=”xs:string” pattern=
”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID).
A UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of
32 hexadecimal digits, displayed in 5 groups separated by hyphens, in the form
8-4-4-4-12 for a total of 36 characters (32 digits and 4 hyphens).
name=”description” type=”xs:string”
The description sent in the input data for the file when uploaded. The maximum
length is 50 characters.
name=”resourceID” type=”xs:integer”
The file id of the file deleted.
name=”code” type=”xs:string”
The response code for the request.
Code Description
IEDTS0001 Success
EEDTS0012 MOH ID not Valid
EEDTS0050 User not Allowed
EEDTS0051 No Data for Processing
EEDTS0052 Data Processing failed
EEDTS0054 User that is deleting the resource is not the
same as the user that uploaded it.
EEDTS0055 The resource is not in the upload status so
cannot be deleted.
EEDTS0056 The resource id specified cannot be found.
name=”msg” type=”xs:string”
The response code message for the request.

<!-- page 21 -->

Final March 2013 Version 3.0 Page 21 of 69
name=”status” type=” tns:resourceStatus”
The current status of the resource. One of UPLOADED, SUBMITTED, WIP,
DOWNLOADABLE, DELETED, APPROVED, DENIED.
Update
The operation will replace up to 5 documents with the contents and attributes
specified.
Input (Request) Message Fields
name=”content” type=”xs:base64Binary”
The content of the file being uploaded. The content is sent as an attachment
using the MTOM protocol.
name=”resourceID” type=”xs:integer”
The file id of the file updated.
Output (Response) Message Fields
name=”auditID” type=”xs:string” pattern=
”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID).
A UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of
32 hexadecimal digits, displayed in 5 groups separated by hyphens, in the form
8-4-4-4-12 for a total of 36 characters (32 digits and 4 hyphens).
name=”description” type=”xs:string”
The description sent in the input data for the file when uploaded. Maximum
length 50 characters.
name=”resourceID” type=”xs:integer”
The file id of the file updated.

<!-- page 22 -->

Final March 2013 Version 3.0 Page 22 of 69
name=”code” type=”xs:string”
The response code for the request.
Code Description
IEDTS0001 Success
EEDTS0012 MOH ID not Valid
EEDTS0050 User not Allowed
EEDTS0051 No Data for Processing
EEDTS0052 Data Processing failed
EEDTS0054 User that is updating the resource is not
the same as the user that uploaded it.
EEDTS0055 The resource is not in the upload status so
cannot be updated
EEDTS0056 The resource id specified cannot be found.
name=”msg” type=”xs:string”
The response code message for the request.
name=”status” type=” tns:resourceStatus”
The current status of the resource. One of UPLOADED, SUBMITTED, WIP,
DOWNLOADABLE, DELETED, APPROVED, DENIED.
getTypeList
The operation will return the list of resources a caller can access. This will
provide information on how the resource is used. One of UPLOAD, DOWNLOAD
or BOTH processes. It will also specify if additional Primary Group information is
also required as part of those processes.
Input (Request) Message Fields
NA

<!-- page 23 -->

Final March 2013 Version 3.0 Page 23 of 69
Output (Response) Message Fields
name=”auditID” type=”xs:string” pattern=
”[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}”
The audit UID is an identifier created by the service for each SOAP message
processed. The UID returned is a version 4 Universally Unique Identifier (UUID).
A UUID is a 16-byte (128-bit) number. In its canonical form, a UUID consists of
32 hexadecimal digits, displayed in 5 groups separated by hyphens, in the form
8-4-4-4-12 for a total of 36 characters (32 digits and 4 hyphens).
name=”access” type=” tns:resourceAccess”
The access permissions of the resource type. One of UPLOAD, DOWNLOAD,
BOTH.
name=”groupRequired” type=”xs:boolean”
Set to true if a group claim submission number is required to use this file.
name=”descriptionEn” type=”xs:string”
name=”descriptionFr” type=”xs:string”
The description of the resource type specified in the “resource Type” field below.
name=”resourceType” type=”xs:string”
The resource type being definded. Maximum of 3 characters in length. See
MCEDT CURRENTLY SUPPORTED FILE/RESOURCE TYPES ADDENDUM:
for a list of the currently supported types.
name=”csns” type=”tns:csnData”
A list of csn/group pairs that represent what the user must specify to use the
resource. A solo claim submission number will always be provided. A group will
only be provided if access has been restricted to a specific group identity by a
Service User.
name=”code” type=”xs:string”
The response code for the request.

<!-- page 24 -->

Final March 2013 Version 3.0 Page 24 of 69
Code Description
EEDTS00102 MOH ID not Valid
EEDTS0050 User not Allowed
name=”msg” type=”xs:string”
The response code message for the request.

<!-- page 25 -->

Final March 2013 Version 3.0 Page 25 of 69
Testing
Conformance testing must be completed for MCEDT. For more details please refer to
the Testing section in the ‘MOHLTC EBS – Generic Security Specification’ document or
Section 3 Conformance Testing in the Medical Claims Electronic Data Transfer
Reference Manual.

<!-- page 26 -->

Final March 2013 Version 3.0 Page 26 of 69
APPENDIX A: Error Codes
Character based error codes are returned as well as textual descriptions of the error.
All ministry specific error codes are 9 characters.
The following are the ministry specific error codes that may be returned within an EBS
Fault accompanied by brief explanations.
EBS Fault
Code
Error Comment
SMIDL0100 System not initialized correctly; contact your technical support or
software vendor.
SMIDL0203 Service is not available; contact your technical support or software
vendor.
SMIDL0204 General System Error; contact your technical support or software
vendor.

<!-- page 27 -->

Final March 2013 Version 3.0 Page 27 of 69
APPENDIX B: The Message WSDL
<?xml version="1.0" encoding="UTF-8"?><!-- Generated by JAX-WS RI at http://jax-ws.dev.java.net. RI's version is JAX-WS RI 2.1.6
in JDK 6. --><definitions name="EDTCoreService" targetNamespace="http://edt.health.ontario.ca/"
xmlns="http://schemas.xmlsoap.org/wsdl/" xmlns:ebs="http://ebs.health.ontario.ca/" xmlns:edt="http://edt.health.ontario.ca/"
xmlns:idp="http://idp.ebs.health.ontario.ca/" xmlns:msa="http://msa.ebs.health.ontario.ca/"
xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:sp="http://schemas.xmlsoap.org/ws/2005/07/securitypolicy"
xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:wsdlsoap="http://schemas.xmlsoap.org/wsdl/soap/"
xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wsswssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
xmlns:xsd="http://www.w3.org/2001/XMLSchema">
<types>
<xsd:schema>
<xsd:import namespace="http://ebs.health.ontario.ca/" schemaLocation="EBSFault_schema.xsd"/>
<xsd:import namespace="http://ebs.health.ontario.ca/" schemaLocation="EBS_schema.xsd"/>
<xsd:import namespace="http://edt.health.ontario.ca/" schemaLocation="EDTService_schema.xsd"/>
<xsd:import namespace="http://msa.ebs.health.ontario.ca/" schemaLocation="MSA_schema.xsd"/>
<xsd:import namespace="http://idp.ebs.health.ontario.ca/" schemaLocation="IDP_schema.xsd"/>
</xsd:schema>
</types>
 <wsp:Policy wsu:Id="request-policy">
<wsp:ExactlyOne>
<wsp:All>
<wsp:All>
<sp:SignedSupportingTokens>
<sp:UsernameToken>

<!-- page 28 -->

Final March 2013 Version 3.0 Page 28 of 69
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

<!-- page 29 -->

Final March 2013 Version 3.0 Page 29 of 69
<sp:RequiredParts>
<sp:Header Name="UserID" Namespace=""/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="Timestamp"
Namespace="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurityutility-1.0.xsd"/>
</sp:RequiredParts>
</wsp:All>
</wsp:ExactlyOne>
<wsp:ExactlyOne>
<wsp:All>
<sp:SignedParts>
<sp:Header Name="EBS" Namespace="http://ebs.health.ontario.ca/"/>
<sp:Header Name="MSA" Namespace="http://msa.ebs.health.ontario.ca/"/>
<sp:Header Name="Timestamp"
Namespace="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurityutility-1.0.xsd"/>
<sp:Header Name="UsernameToken"
Namespace="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurityutility-1.0.xsd"/>
<sp:Body/>
</sp:SignedParts>
</wsp:All>
</wsp:ExactlyOne>
</wsp:All>
<wsp:All>

<!-- page 30 -->

Final March 2013 Version 3.0 Page 30 of 69
<wsp:All>
<sp:SignedSupportingTokens>
<sp:UsernameToken>
<wsp:Policy>
<wsp:All>
<sp:WssUsernameToken10/>
</wsp:All>
</wsp:Policy>
</sp:UsernameToken>
</sp:SignedSupportingTokens>
</wsp:All>
<wsp:ExactlyOne>
<wsp:All>
<sp:SignedParts>
<sp:Header Name="EBS" Namespace="http://ebs.health.ontario.ca/"/>
<sp:Header Name="IDP" Namespace="http://idp.ebs.health.ontario.ca/"/>
<sp:Header Name="Timestamp"
Namespace="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility1.0.xsd"/>
<sp:Header Name="UsernameToken"
Namespace="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility1.0.xsd"/>
<sp:Body/>
</sp:SignedParts>
</wsp:All>
</wsp:ExactlyOne>

<!-- page 31 -->

Final March 2013 Version 3.0 Page 31 of 69
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
<sp:RequiredParts>
<sp:Header Name="ServiceUserMUID" Namespace=""/>
</sp:RequiredParts>
<sp:RequiredParts>
<sp:Header Name="Timestamp"
Namespace="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurityutility-1.0.xsd"/>
</sp:RequiredParts>
</wsp:All>
</wsp:ExactlyOne>
</wsp:All>
 </wsp:ExactlyOne>

<!-- page 32 -->

Final March 2013 Version 3.0 Page 32 of 69
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
 <message name="upload">
 <part element="edt:upload" name="parameters"/>
 </message>
 <message name="uploadResponse">
 <part element="edt:uploadResponse" name="parameters"/>
 </message>
 <message name="submit">
 <part element="edt:submit" name="parameters"/>
 </message>
 <message name="submitResponse">
 <part element="edt:submitResponse" name="parameters"/>
 </message>
 <message name="list">
 <part element="edt:list" name="parameters"/>

<!-- page 33 -->

Final March 2013 Version 3.0 Page 33 of 69
 </message>
 <message name="listResponse">
 <part element="edt:listResponse" name="parameters"/>
 </message>
 <message name="info">
 <part element="edt:info" name="parameters"/>
 </message>
 <message name="infoResponse">
 <part element="edt:infoResponse" name="parameters"/>
 </message>
 <message name="update">
 <part element="edt:update" name="parameters"/>
 </message>
 <message name="updateResponse">
 <part element="edt:updateResponse" name="parameters"/>
 </message>
 <message name="download">
 <part element="edt:download" name="parameters"/>
 </message>
 <message name="downloadResponse">
 <part element="edt:downloadResponse" name="parameters"/>
 </message>
 <message name="delete">
 <part element="edt:delete" name="parameters"/>
 </message>
 <message name="deleteResponse">

<!-- page 34 -->

Final March 2013 Version 3.0 Page 34 of 69
 <part element="edt:deleteResponse" name="parameters"/>
 </message>
 <message name="getTypeList">
 <part element="edt:getTypeList" name="parameters"/>
 </message>
 <message name="getTypeListResponse">
 <part element="edt:getTypeListResponse" name="parameters"/>
 </message>
 <message name="faultexception">
 <part element="ebs:EBSFault" name="Fault"/>
 </message>
 <portType name="EDTDelegate">
 <operation name="upload">
 <input message="edt:upload"/>
 <output message="edt:uploadResponse"/>
 <fault message="edt:faultexception" name="FaultException"/>
 </operation>
 <operation name="submit">
 <input message="edt:submit"/>
 <output message="edt:submitResponse"/>
 <fault message="edt:faultexception" name="FaultException"/>
 </operation>
 <operation name="list">
 <input message="edt:list"/>
 <output message="edt:listResponse"/>

<!-- page 35 -->

Final March 2013 Version 3.0 Page 35 of 69
 <fault message="edt:faultexception" name="FaultException"/>
 </operation>
 <operation name="info">
 <input message="edt:info"/>
 <output message="edt:infoResponse"/>
 <fault message="edt:faultexception" name="FaultException"/>
 </operation>
 <operation name="update">
 <input message="edt:update"/>
 <output message="edt:updateResponse"/>
 <fault message="edt:faultexception" name="FaultException"/>
 </operation>
 <operation name="download">
 <input message="edt:download"/>
 <output message="edt:downloadResponse"/>
 <fault message="edt:faultexception" name="FaultException"/>
 </operation>
 <operation name="delete">
 <input message="edt:delete"/>
 <output message="edt:deleteResponse"/>
 <fault message="edt:faultexception" name="FaultException"/>
 </operation>
 <operation name="getTypeList">
 <input message="edt:getTypeList"/>
 <output message="edt:getTypeListResponse"/>
 <fault message="edt:faultexception" name="FaultException"/>

<!-- page 36 -->

Final March 2013 Version 3.0 Page 36 of 69
 </operation>
 </portType>
 <binding name="EDTPortBinding" type="edt:EDTDelegate">
 <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>

 <operation name="upload">
 <soap:operation soapAction=""/>
 <input>
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="edt:EBSHeader" part="ebsrequest_header" use="literal"/>
<wsdlsoap:header message="edt:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="edt:IDPHeader" part="idprequest_header" use="literal"/>
 <soap:body parts="parameters" use="literal"/>
 </input>
 <output>
 <soap:body use="literal"/>
 </output>
 <fault name="FaultException">
 <soap:fault name="FaultException" use="literal"/>
 </fault>
 </operation>
 <operation name="submit">
 <soap:operation soapAction=""/>
 <input>
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="edt:EBSHeader" part="ebsrequest_header" use="literal"/>

<!-- page 37 -->

Final March 2013 Version 3.0 Page 37 of 69
<wsdlsoap:header message="edt:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="edt:IDPHeader" part="idprequest_header" use="literal"/>
 <soap:body parts="parameters" use="literal"/>
 </input>
 <output>
 <soap:body use="literal"/>
 </output>
 <fault name="FaultException">
 <soap:fault name="FaultException" use="literal"/>
 </fault>
 </operation>
 <operation name="list">
 <soap:operation soapAction=""/>
 <input>
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="edt:EBSHeader" part="ebsrequest_header" use="literal"/>
<wsdlsoap:header message="edt:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="edt:IDPHeader" part="idprequest_header" use="literal"/>
 <soap:body parts="parameters" use="literal"/>
 </input>
 <output>
 <soap:body use="literal"/>
 </output>
 <fault name="FaultException">
 <soap:fault name="FaultException" use="literal"/>
 </fault>

<!-- page 38 -->

Final March 2013 Version 3.0 Page 38 of 69
 </operation>
 <operation name="info">
 <soap:operation soapAction=""/>
 <input>
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="edt:EBSHeader" part="ebsrequest_header" use="literal"/>
<wsdlsoap:header message="edt:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="edt:IDPHeader" part="idprequest_header" use="literal"/>
 <soap:body parts="parameters" use="literal"/>
 </input>
 <output>
 <soap:body use="literal"/>
 </output>
 <fault name="FaultException">
 <soap:fault name="FaultException" use="literal"/>
 </fault>
 </operation>
 <operation name="update">
 <soap:operation soapAction=""/>
 <input>
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="edt:EBSHeader" part="ebsrequest_header" use="literal"/>
<wsdlsoap:header message="edt:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="edt:IDPHeader" part="idprequest_header" use="literal"/>
 <soap:body parts="parameters" use="literal"/>
 </input>

<!-- page 39 -->

Final March 2013 Version 3.0 Page 39 of 69
 <output>
 <soap:body use="literal"/>
 </output>
 <fault name="FaultException">
 <soap:fault name="FaultException" use="literal"/>
 </fault>
 </operation>
 <operation name="download">
 <soap:operation soapAction=""/>
 <input>
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="edt:EBSHeader" part="ebsrequest_header" use="literal"/>
<wsdlsoap:header message="edt:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="edt:IDPHeader" part="idprequest_header" use="literal"/>
 <soap:body parts="parameters" use="literal"/>
 </input>
 <output>
 <soap:body use="literal"/>
 </output>
 <fault name="FaultException">
 <soap:fault name="FaultException" use="literal"/>
 </fault>
 </operation>
 <operation name="delete">
 <soap:operation soapAction=""/>
 <input>

<!-- page 40 -->

Final March 2013 Version 3.0 Page 40 of 69
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="edt:EBSHeader" part="ebsrequest_header" use="literal"/>
<wsdlsoap:header message="edt:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="edt:IDPHeader" part="idprequest_header" use="literal"/>
 <soap:body parts="parameters" use="literal"/>
 </input>
 <output>
 <soap:body use="literal"/>
 </output>
 <fault name="FaultException">
 <soap:fault name="FaultException" use="literal"/>
 </fault>
 </operation>
 <operation name="getTypeList">
 <soap:operation soapAction=""/>
 <input>
 <wsp:PolicyReference URI="#request-policy"/>
<wsdlsoap:header message="edt:EBSHeader" part="ebsrequest_header" use="literal"/>
<wsdlsoap:header message="edt:MSAHeader" part="msarequest_header" use="literal"/>
<wsdlsoap:header message="edt:IDPHeader" part="idprequest_header" use="literal"/>
 <soap:body parts="parameters" use="literal"/>
 </input>
 <output>
 <soap:body use="literal"/>
 </output>
 <fault name="FaultException">

<!-- page 41 -->

Final March 2013 Version 3.0 Page 41 of 69
 <soap:fault name="FaultException" use="literal"/>
 </fault>
 </operation>
 </binding>
 <service name="EDTService">
 <port binding="edt:EDTPortBinding" name="EDTPort">
 <soap:address location="https://ws.ebs.health.gov.on.ca:1441/EDTService/EDTService"/>
 </port>
 </service>
</definitions>

<!-- page 42 -->

Final March 2013 Version 3.0 Page 42 of 69
APPENDIX C: Message Schema

<?xml version="1.0" encoding="UTF-8"?>
<xs:schema targetNamespace="http://edt.health.ontario.ca/" version="1.0"
xmlns:tns="http://edt.health.ontario.ca/"
xmlns:xs="http://www.w3.org/2001/XMLSchema">
 <xs:element name="delete" type="tns:delete"/>
 <xs:element name="deleteResponse" type="tns:deleteResponse"/>
 <xs:element name="download" type="tns:download"/>
 <xs:element name="downloadResponse" type="tns:downloadResponse"/>
 <xs:element name="getTypeList" type="tns:getTypeList"/>
 <xs:element name="getTypeListResponse" type="tns:getTypeListResponse"/>
 <xs:element name="info" type="tns:info"/>
 <xs:element name="infoResponse" type="tns:infoResponse"/>
 <xs:element name="list" type="tns:list"/>

<!-- page 43 -->

Final March 2013 Version 3.0 Page 43 of 69
 <xs:element name="listResponse" type="tns:listResponse"/>
 <xs:element name="submit" type="tns:submit"/>
 <xs:element name="submitResponse" type="tns:submitResponse"/>
 <xs:element name="update" type="tns:update"/>
 <xs:element name="updateResponse" type="tns:updateResponse"/>
 <xs:element name="upload" type="tns:upload"/>
 <xs:element name="uploadResponse" type="tns:uploadResponse"/>
 <xs:simpleType name="audit">
 <xs:restriction base="xs:string">
 <xs:pattern value="[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}"/>
 </xs:restriction>
 </xs:simpleType>
 <xs:complexType name="upload">
 <xs:sequence>
 <xs:element maxOccurs="5" minOccurs="1" name="upload" type="tns:uploadData"/>
 </xs:sequence>
 </xs:complexType>

<!-- page 44 -->

Final March 2013 Version 3.0 Page 44 of 69
 <xs:complexType name="uploadData">
 <xs:sequence>
 <xs:element minOccurs="1" name="content" type="xs:base64Binary"/>
 <xs:element minOccurs="0" name="description" type="xs:string"/>
 <xs:element minOccurs="1" name="resourceType" type="xs:string"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="uploadResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="return" type="tns:resourceResult"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="resourceResult">
 <xs:sequence>
 <xs:element minOccurs="1" name="auditID" type="tns:audit"/>
 <xs:element maxOccurs="unbounded" minOccurs="1" name="response" type="tns:responseResult"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="responseResult">
 <xs:sequence>
 <xs:element minOccurs="0" name="description" type="xs:string"/>
 <xs:element minOccurs="1" name="resourceID" type="xs:integer"/>
 <xs:element minOccurs="1" name="result" type="tns:commonResult"/>

<!-- page 45 -->

Final March 2013 Version 3.0 Page 45 of 69
 <xs:element minOccurs="1" name="status" type="tns:resourceStatus"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="commonResult">
 <xs:sequence>
 <xs:element minOccurs="1" name="code" type="xs:string"/>
 <xs:element minOccurs="1" name="msg" type="xs:string"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="getTypeList">
 <xs:sequence/>
 </xs:complexType>
 <xs:complexType name="getTypeListResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="return" type="tns:typeListResult"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="typeListResult">
 <xs:sequence>
 <xs:element minOccurs="1" name="auditID" type="tns:audit"/>
 <xs:element maxOccurs="unbounded" minOccurs="1" name="data" type="tns:typeListData"/>
 </xs:sequence>

<!-- page 46 -->

Final March 2013 Version 3.0 Page 46 of 69
 </xs:complexType>
 <xs:complexType name="csnData">
 <xs:sequence>
 <xs:element minOccurs="0" name="soloCsn" type="xs:string"/>
 <xs:element minOccurs="0" name="groupCsn" type="xs:string"/>
 </xs:sequence>
 </xs:complexType>

 <xs:complexType name="typeListData">
 <xs:sequence>
 <xs:element minOccurs="1" name="access" type="tns:resourceAccess"/>
 <xs:element minOccurs="1" name="descriptionEn" type="xs:string"/>
 <xs:element minOccurs="1" name="descriptionFr" type="xs:string"/>
 <xs:element minOccurs="1" name="groupRequired" type="xs:boolean"/>
 <xs:element minOccurs="1" name="resourceType" type="xs:string"/>
 <xs:element minOccurs="1" name="result" type="tns:commonResult"/>
 <xs:element maxOccurs="unbounded" minOccurs="0" name="csns" type="tns:csnData"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="info">
 <xs:sequence>
 <xs:element maxOccurs="100" minOccurs="1" name="resourceIDs" type="xs:integer"/>
 </xs:sequence>
 </xs:complexType>

<!-- page 47 -->

Final March 2013 Version 3.0 Page 47 of 69
 <xs:complexType name="infoResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="return" type="tns:detail"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="detail">
 <xs:sequence>
 <xs:element minOccurs="1" name="auditID" type="tns:audit"/>
 <xs:element maxOccurs="50" minOccurs="0" name="data" type="tns:detailData"/>
 <xs:element minOccurs="1" name="resultSize" type="xs:integer"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="detailData">
 <xs:sequence>
 <xs:element minOccurs="1" name="createTimestamp" type="xs:dateTime"/>
 <xs:element minOccurs="0" name="description" type="xs:string"/>
 <xs:element minOccurs="0" name="resourceType" type="xs:string"/>
 <xs:element minOccurs="0" name="modifyTimestamp" type="xs:dateTime"/>
 <xs:element minOccurs="1" name="resourceID" type="xs:integer"/>
 <xs:element minOccurs="1" name="result" type="tns:commonResult"/>
 <xs:element minOccurs="1" name="status" type="tns:resourceStatus"/>
 </xs:sequence>
 </xs:complexType>

<!-- page 48 -->

Final March 2013 Version 3.0 Page 48 of 69
 <xs:complexType name="list">
 <xs:sequence>
 <xs:element minOccurs="0" name="resourceType" type="xs:string"/>
 <xs:element minOccurs="0" name="status" type="tns:resourceStatus"/>
 <xs:element minOccurs="0" name="pageNo" type="xs:integer"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="listResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="return" type="tns:detail"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="update">
 <xs:sequence>
 <xs:element maxOccurs="5" minOccurs="1" name="updates" type="tns:updateRequest"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="updateRequest">
 <xs:sequence>
 <xs:element minOccurs="1" name="content" type="xs:base64Binary"/>
 <xs:element minOccurs="1" name="resourceID" type="xs:integer"/>
 </xs:sequence>

<!-- page 49 -->

Final March 2013 Version 3.0 Page 49 of 69
 </xs:complexType>
 <xs:complexType name="updateResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="return" type="tns:resourceResult"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="download">
 <xs:sequence>
 <xs:element maxOccurs="5" minOccurs="1" name="resourceIDs" type="xs:integer"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="downloadResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="return" type="tns:downloadResult"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="downloadResult">
 <xs:sequence>
 <xs:element minOccurs="1" name="auditID" type="tns:audit"/>
 <xs:element maxOccurs="5" minOccurs="0" name="data" type="tns:downloadData"/>
 </xs:sequence>
 </xs:complexType>

<!-- page 50 -->

Final March 2013 Version 3.0 Page 50 of 69
 <xs:complexType name="downloadData">
 <xs:sequence>
 <xs:element minOccurs="1" name="content" type="xs:base64Binary"/>
 <xs:element minOccurs="1" name="resourceID" type="xs:integer"/>
 <xs:element minOccurs="1" name="resourceType" type="xs:string"/>
 <xs:element minOccurs="1" name="description" type="xs:string"/>
 <xs:element minOccurs="1" name="result" type="tns:commonResult"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="submit">
 <xs:sequence>
 <xs:element maxOccurs="100" minOccurs="1" name="resourceIDs" type="xs:integer"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="submitResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="return" type="tns:resourceResult"/>
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="delete">
 <xs:sequence>
 <xs:element maxOccurs="100" minOccurs="1" name="resourceIDs" type="xs:integer"/>

<!-- page 51 -->

Final March 2013 Version 3.0 Page 51 of 69
 </xs:sequence>
 </xs:complexType>
 <xs:complexType name="deleteResponse">
 <xs:sequence>
 <xs:element minOccurs="1" name="return" type="tns:resourceResult"/>
 </xs:sequence>
 </xs:complexType>
 <xs:simpleType name="resourceStatus">
 <xs:restriction base="xs:string">
 <xs:enumeration value="UPLOADED"/>
 <xs:enumeration value="SUBMITTED"/>
 <xs:enumeration value="WIP"/>
 <xs:enumeration value="DOWNLOADABLE"/>
 <xs:enumeration value="DELETED"/>
 <xs:enumeration value="APPROVED"/>
 <xs:enumeration value="DENIED"/>
 </xs:restriction>
 </xs:simpleType>
 <xs:simpleType name="resourceAccess">
 <xs:restriction base="xs:string">
 <xs:enumeration value="UPLOAD"/>
 <xs:enumeration value="DOWNLOAD"/>
 <xs:enumeration value="BOTH"/>

<!-- page 52 -->

Final March 2013 Version 3.0 Page 52 of 69
 </xs:restriction>
 </xs:simpleType>
</xs:schema>

<!-- page 53 -->

Final March 2013 Version 3.0 Page 53 of 69
APPENDIX D: MSA Model Message Example
<soapenv:Envelope
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
xmlns:msa="http://msa.ebs.health.ontario.ca/"
xmlns:idp="http://idp.ebs.health.ontario.ca/"
xmlns:edt="http://edt.health.ontario.ca/"
xmlns:ebs=http://ebs.health.ontario.ca/>
 <soapenv:Header>
 <ns2:EBS wsu:Id="id-1" xmlns:ns2="http://ebs.health.ontario.ca/" >
 <SoftwareConformanceKey>444561ee-277f-77b2-c664-7a9923jfgh1b</SoftwareConformanceKey>
 <AuditId>f68e6ff9-74f7-4022-8618-ec2cf0ee4b6a</AuditId>
 </ns2:EBS>
 <ns2:MSA wsu:Id="id-2" xmlns:ns2="http://msa.ebs.health.ontario.ca/" >
 <ServiceUserMUID>4523394</ServiceUserMUID>
 <UserID>johndoe</UserID>
 </ns2:MSA>
 <wsse:Security SOAP-ENV:mustUnderstand="1">
 <wsu:Timestamp wsu:Id="id-3">
 <wsu:Created>2012-06-26T16:18:15.185Z</wsu:Created>
 <wsu:Expires>2012-06-26T16:18:45.185Z</wsu:Expires>
 </wsu:Timestamp>
 <wsse:UsernameToken wsu:Id="id-4">
 <wsse:Username>72214255</wsse:Username>

<!-- page 54 -->

Final March 2013 Version 3.0 Page 54 of 69
 </wsse:UsernameToken>
 <wsse:BinarySecurityToken
 EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"
 ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3"
 wsu:Id="X509-0EE1C2718CEDCA9FC213407274954261">

MIICMzCCAZygAwIBAgIET1e+dDANBgkqhkiG9w0BAQUFADBeMQswCQYDVQQGEwJDQTEQMA4GA1UECBMHT250YXJpbzENMAsGA1UEChMET0hJUD
EVMBMGA1UECxMMUmVnaXN0cmF0aW9uMRcwFQYDVQQDEw4xNDIuMTQ1LjcwLjE3NzAeFw0xMjAzMDcyMDAwNTJaFw0xMzAzMDcyMDAwNTJaMF4x
CzAJBgNVBAYTAkNBMRAwDgYDVQQIEwdPbnRhcmlvMQ0wCwYDVQQKEwRPSElQMRUwEwYDVQQLEwxSZWdpc3RyYXRpb24xFzAVBgNVBAMTDjE0Mi
4xNDUuNzAuMTc3MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCs/JIP6CE5IkfTnD/c56K+QAYqETdLvW1xXJ6ipkVhjjC2ASKuuH4fvhby
xo2B4VugsL9r4E5jHEKoi+GDKOLlLZRfSy0cB8IcpXonAuGqMzhCoEQ1CdxNb9etMyvQGRKEBgniKKxTvpTyZdpYDi92up5E+FYL3jEejhp+1i
DFJQIDAQABMA0GCSqGSIb3DQEBBQUAA4GBAHn8VZS169BJMa4E6SNLnY7u80zSh90mbrTUWjM1dEicv3jQMMsrWHfoCt+nRSqfNLUTLc8U0Lqi
B3jnnNJgJt1T7Sp8eUZPdH0gY3i83ZXA8HDFKMZF3qL8I8ncu8FPcZGYBNhYrGjXXsuqXimiTIjxgm06ErRa/51szOFFxWrB
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

<!-- page 55 -->

Final March 2013 Version 3.0 Page 55 of 69
 </ds:Transforms>
 <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
 <ds:DigestValue>FkhA37COGmsKeEH50LAGhKntvRpD0+xOGsGzXAV210k=</ds:DigestValue>
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
 <ds:DigestValue>3vVj2VEbLKEvGy4yt7k8i2BeWUOaCygnFMduT7EyP3A=</ds:DigestValue>
 </ds:Reference>
 <ds:Reference URI="#id-4">

<!-- page 56 -->

Final March 2013 Version 3.0 Page 56 of 69
 <ds:Transforms>
 <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
 <ec:InclusiveNamespaces PrefixList="SOAP-ENV ebs soap-sec soapenv sp tns wsdl wsp wsu xs xsi"
 xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" />
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
 <ds:DigestValue>zoxMcAQ2WLBIir333NJk52r4axwznflX+KxMQTPFvfQ=</ds:DigestValue>
 </ds:Reference>
 </ds:SignedInfo>
 <ds:SignatureValue>

HmOiZS4gZbxv07+sLjyi7Vfg3Rfpvr3IVnaHfRp4aKvg5yBFlLocPIYwhUhmCCs1LXrJxR0hsbe0K2sz3ML5hH+PDEGetlPKSN9R1x9K95w7V
1JQcTUULiVgNGLCfxgFV2HNy1iNvlTc7COS+7w4xSgsY4KlVgrBw0T1srhHpUA=
 </ds:SignatureValue>
 <ds:KeyInfo Id="KI-0EE1C2718CEDCA9FC213407274954662">
 <wsse:SecurityTokenReference wsu:Id="STR-0EE1C2718CEDCA9FC213407274954663">

<!-- page 57 -->

Final March 2013 Version 3.0 Page 57 of 69
 <wsse:Reference URI="#X509-0EE1C2718CEDCA9FC213407274954261"
 ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3" />
 </wsse:SecurityTokenReference>
 </ds:KeyInfo>
 </ds:Signature>
 </wsse:Security>
 </soapenv:Header>
 <soapenv:Body wsu:Id="id-5"
 xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
 <edt:upload>
 <upload>
 <content>
 <inc:Include href=cid:2341682853256 xmlns:inc="http://www.w3.org/2004/08/xop/include" />
 </content>
 <description>00123</description>
 <resourceType>CL</resourceType>
 </upload>
 </edt:upload>
 </soapenv:Body>
</soapenv:Envelope>

<!-- page 58 -->

Final March 2013 Version 3.0 Page 58 of 69
APPENDIX E: IDP Model Message Example
<soapenv:Envelope
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
xmlns:msa="http://msa.ebs.health.ontario.ca/"
xmlns:idp="http://idp.ebs.health.ontario.ca/"
xmlns:edt="http://edt.health.ontario.ca/"
xmlns:ebs="http://ebs.health.ontario.ca/">
<soapenv:Header>
<ebs:EBS wsu:Id="id-4"
xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
<SoftwareConformanceKey>444361ee-277f-7732-c684-7a9923jfgh1b</SoftwareConformanceKey>
<AuditId>124355467675</AuditId>
</ebs:EBS>
<idp:IDP wsu:Id="id-3"
xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
<ServiceUserMUID>1111222</ServiceUserMUID>
</idp:IDP>
<wsse:Security soapenv:mustUnderstand="1"
xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
<wsse:BinarySecurityToken
EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security1.0#Base64Binary"

<!-- page 59 -->

Final March 2013 Version 3.0 Page 59 of 69
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile1.0#X509v3"
wsu:Id="X509-
04FD51796CB607011413612828891871">MIIF0zCCBLugAwIBAgIBAjANBgkqhkiG9w0BAQUFADCBzzELMAkGA1UEBhMC
Q0ExCzAJBgNVBAgTAm9uMREwDwYDVQQHEwhraW5nc3RvbjEpMCcGA1UEChMgSGVhbHRoIFNvbHV0aW9ucyBEZWxpdmVyeS
BCcmFuY2gxJTAjBgNVBAsTHEVsZWN0cm9uaWMgQnVzaW5lc3MgU2VydmljZXMxJzAlBgNVBAMTHkVCUyBUZXN0IENlcnRp
ZmljYXRlIEF1dGhvcml0eTElMCMGCSqGSIb3DQEJARYWc2Vhbi5jYXJzb25Ab250YXJpby5jYTAeFw0xMjA0MjkxNjAyMj
NaFw0xNDA0MzAxNjAyMjNaMIGUMQswCQYDVQQGEwJDQTELMAkGA1UECBMCb24xETAPBgNVBAcTCGtpbmdzdG9uMSkwJwYD
VQQKEyBIZWFsdGggU29sdXRpb25zIERlbGl2ZXJ5IEJyYW5jaDElMCMGA1UECxMcRWxlY3Ryb25pYyBCdXNpbmVzcyBTZX
J2aWNlczETMBEGA1UEAxQKRUJTX0NsaWVudDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMEEvvZ96t117651
bJXIa8AaE69N1klliJvhrXFxtV2JcKoJHZG19Em6nFtxznvrfjHjQCOJXgREq0YLJmIHgIaIggug9g4oZhZoSXm11b0k+l
9sI0uV1UQxSPvKZbptLw3JuY3E8iHoBTBY4TZDg0yfuuk5kpwT0JCqn8Pcoi2Oq2rQtEdnQ0TG5/lofJAMDRzBpK1ETnNO
jzCeAkR3wHPec++q2nTuY9QFYntpOyk5JksRVuuIsR5OCW6rjFXTF7CJ84qxWloXmWl4M3yKDTi3ouD36Gplgo8fi2HLpN
qVBDLCm7Acv8klkc0EyiFOpBzhEYWAVIIwC9ovybXRjg0CAwEAAaOCAfEwggHtMAkGA1UdEwQCMAAwEQYJYIZIAYb4QgEB
BAQDAgSwMEsGCWCGSAGG+EIBDQQ+FjxTU0wgQ2xpZW50IGNlcnRpZmljYXRlIHZhbGlkIG9ubHkgZm9yIE1PSExUQy9IU0
MgRUJTIFRlc3RpbmcwHQYDVR0OBBYEFKV6tGi2SztsTcIPYFkZcKr4yLJMMIIBBAYDVR0jBIH8MIH5gBT/qI53Ggvfsdz3
4whLQ2gDg+PhW6GB1aSB0jCBzzELMAkGA1UEBhMCQ0ExCzAJBgNVBAgTAm9uMREwDwYDVQQHEwhraW5nc3RvbjEpMCcGA1
UEChMgSGVhbHRoIFNvbHV0aW9ucyBEZWxpdmVyeSBCcmFuY2gxJTAjBgNVBAsTHEVsZWN0cm9uaWMgQnVzaW5lc3MgU2Vy
dmljZXMxJzAlBgNVBAMTHkVCUyBUZXN0IENlcnRpZmljYXRlIEF1dGhvcml0eTElMCMGCSqGSIb3DQEJARYWc2Vhbi5jYX
Jzb25Ab250YXJpby5jYYIJAOnNHCT34+b/MCEGA1UdEgQaMBiBFnNlYW4uY2Fyc29uQG9udGFyaW8uY2EwJgYDVR0RBB8w
HYEbZGVyZXlrLmZlcm5hbmRlc0BvbnRhcmlvLmNhMA4GA1UdDwEB/wQEAwIFoDANBgkqhkiG9w0BAQUFAAOCAQEAJqCht1
81F8rUUNQ8jHa42kdKH+FDF0ISuklbg5MARHo+wt1laltaMeaXdESnLJBNGvcgxPZ4StYMdCH8mOEWYftCy5nkyGQCuOd2
GpaJ3Hj50bjZ9vZUYyUBPUmwIEP2v75QQe62fHTqza/VjA0I5eMGMKa3URHsTdfNdnEJjtmHdxWRjAjjrHpHQWE0e1QtG+
ZV1ved0f5OzDvdylbvrm4S0mgCifk8qEvZtNSoDp37MmSFr5fFmo91BqT23xAgzUKra428dw4T1EKJYEd6pNssNS4XCQ6b
Tx0Au3mW5iINtiaYQP8rlSykwaJ+dFAtBG00kdGpebf1prvq4H91eA==</wsse:BinarySecurityToken>
<ds:Signature Id="SIG-6" xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
<ds:SignedInfo>
<ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
<ec:InclusiveNamespaces PrefixList="ebs edt idp msa soapenv"
xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#"/>
</ds:CanonicalizationMethod>
<ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1" />
<ds:Reference URI="#UsernameToken-2">
<ds:Transforms>

<!-- page 60 -->

Final March 2013 Version 3.0 Page 60 of 69
<ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
<ec:InclusiveNamespaces PrefixList="ebs edt idp msa soapenv"
xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#"/>
</ds:Transform>
</ds:Transforms>
<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
<ds:DigestValue>peTHpiEd5ujPqxNuKGN0k4p7up8c0dFPuRXbpQ+eMwI=</ds:DigestValue>
</ds:Reference>
<ds:Reference URI="#TS-1">
<ds:Transforms>
<ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
<ec:InclusiveNamespaces PrefixList="wsse ebs edt idp msa soapenv"
xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#"/>
</ds:Transform>
</ds:Transforms>
<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
<ds:DigestValue>DqLqNQVHwzHRx7amwoYxEMwxN2g0/rND2I13WPP1Vhw=</ds:DigestValue>
</ds:Reference>
<ds:Reference URI="#id-3">
<ds:Transforms>
<ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
<ec:InclusiveNamespaces PrefixList="ebs edt msa soapenv"
xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#"/>
</ds:Transform>
</ds:Transforms>
<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />

<!-- page 61 -->

Final March 2013 Version 3.0 Page 61 of 69
<ds:DigestValue>K4IrndAA4zBmkumIfgKcluiKA8dmzwgGdKo5aq45LHg=</ds:DigestValue>
</ds:Reference>
<ds:Reference URI="#id-4">
<ds:Transforms>
<ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
<ec:InclusiveNamespaces PrefixList="edt idp msa soapenv"
xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#"/>
</ds:Transform>
</ds:Transforms>
<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
<ds:DigestValue>o92xRJQNwGz0Hv7DX87vSYUScX0qHL/bFGE3GmtUzQg=</ds:DigestValue>
</ds:Reference>
<ds:Reference URI="#id-5">
<ds:Transforms>
<ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
<ec:InclusiveNamespaces PrefixList="ebs edt idp msa"
xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#"/>
</ds:Transform>
</ds:Transforms>
<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
<ds:DigestValue>svNyvvP+MrjIYlZFsg+bgw//8IPNVvIO9px3vYUfW3I=</ds:DigestValue>
</ds:Reference>
</ds:SignedInfo>
<ds:SignatureValue>
qDSZlgY/aTFOzzo1C4tx+1E8ertrbmBySRxEK6sJ1JCt/77TLV5PBGnAme9Ttdmzf6h7/qb4rBGL
76LM0PaCQ9xm3DTsSQOz/So82G+/kX8M9TPY9D44+dvlB+cXm9rPn2BLMSVwtJf0kwI22SmRzMTR
6a6jfNYkGga6ZwZC9NLfG5/KTvsyZ39vOdO3T5GYc15RSjHKVBggoWmKm7x5PHrhU+3gClEbtHP8

<!-- page 62 -->

Final March 2013 Version 3.0 Page 62 of 69
+Fgmmd9PJOtl9WunzR7NpY79xRNGxmDmL8hLvE4+uIc//b6TvrbGB2t8IWb5e5Wdz+ssHgMm0802
wFwGXlVxvSHpEJroHz5OvRgh7PKGlUSZP9fWkg==
</ds:SignatureValue>
<ds:KeyInfo Id="KI-04FD51796CB607011413612828892812">
<wsse:SecurityTokenReference wsu:Id="STR-04FD51796CB607011413612828892813">
<wsse:Reference
URI="#X509-04FD51796CB607011413612828891871"
ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-
token-profile-1.0#X509v3" />
</wsse:SecurityTokenReference>
</ds:KeyInfo>
</ds:Signature>
<wsse:UsernameToken wsu:Id="UsernameToken-2">
<wsse:Username>johndoe@examplemail.com</wsse:Username>
<wsse:Password
Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile1.0#PasswordText">****</wsse:Password>
</wsse:UsernameToken>
<wsu:Timestamp wsu:Id="TS-1">
<wsu:Created>2013-02-19T14:08:08Z</wsu:Created>
<wsu:Expires>2013-02-19T14:08:38Z</wsu:Expires>
</wsu:Timestamp>
</wsse:Security>
</soapenv:Header>
<soapenv:Body wsu:Id="id-5"
xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
<edt:upload>

<!-- page 63 -->

Final March 2013 Version 3.0 Page 63 of 69
<upload>
<content>
<inc:Include href=cid:2341682853256 xmlns:inc="http://www.w3.org/2004/08/xop/include" />
</content>
<description>00123</description>
<resourceType>CL</resourceType>
</upload>
</edt:upload>
</soapenv:Body>
</soapenv:Envelope>

<!-- page 64 -->

Final March 2013 Version 3.0 Page 64 of 69
MCEDT Currently Supported File/Resource Types Addendum
Inbound
File Type Description
CL Claims
OB OBEC
SDC Stale Dated Claims
RHB Reciprocal Hospital Billing
Outbound
File Type Description
OO OBEC Response
ER Error Reports
ES Error Report Extract
RA Remittance Advice
RS Remittance Advice Extract
BE Batch Edit

<!-- page 65 -->

Final March 2013 Version 3.0 Page 65 of 69
AH Academic Health Governance Report
CO EC Outside Use report
CS EC Summary report
NS Northern Specialist APP Governance
MR Claims Mail File Reject Message
MO OBEC Mail File Reject Message
GCM General Ministry Communications

<!-- page 66 -->

Final March 2013 Version 3.0 Page 66 of 69
Claim File Upload Error Codes Addendum
Returned Error
Code
Description
ECLAM0002 Mal Formed Header
ECLAM0003 No CSN specified
ECLAM0004 Invalid Health Number
ECLAM0005 Mal Formed Trailer. Claim Header – 1 header count does not match number of Claim Header – 1
headers in batch
ECLAM0006 Mal Formed Trailer. Claim Header – 2 header count does not match number of Claim Header – 2
headers in batch
ECLAM0007 Mal Formed Trailer. Item Record count does not match number of Item Records in batch
ECLAM0008 Records are not the correct width must be 79 bytes.

<!-- page 67 -->

Final March 2013 Version 3.0 Page 67 of 69
OBEC File Upload Error Codes Addendum
Returned Error
Code
Description
EOBEC0001 OBEC File is empty
EOBEC0002
File is an invalid length
EOBEC0003 Mal Formed Header. The ‘OBE’ in the transaction code field is
invalid.
EOBEC0004 Invalid Health Number length
EOBEC0005 Health Number is not numeric

<!-- page 68 -->

Final March 2013 Version 3.0 Page 68 of 69
Stale Dated Claims File Upload Error Codes Addendum
Returned Error
Code
Description
ESDCL0002 Mal Formed Header
ESDCL0003 No CSN specified
ESDCL0004 Invalid Health Number
ESDCL0005 Mal Formed Trailer. Claim Header – 1 header count does not match number of Claim Header –
1 headers in batch
ESDCL0006 Mal Formed Trailer. Claim Header – 2 header count does not match number of Claim Header –
2 headers in batch
ESDCL0007 Mal Formed Trailer. Item Record count does not match number of Item Records in batch

<!-- page 69 -->

Catalogue # CIB-XXXXXXX Month/Year © Queen’s Printer for Ontario
```
