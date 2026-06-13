# Technical Specifications Questions and Answers — MCEDT, HCV, e-Business Services and Conformance Test

> **Source**: [http://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/faq_tech_specs.aspx]
> **Fetched**: 2026-04-29 (via Wayback Machine snapshot 2023-02-17, since the live MOH HTTPS endpoint is currently returning HTTP 502 from this network)
> **Format**: HTML page captured via Wayback Machine and reformatted as Markdown. Layout simplified.
> **Authoritative source**: the live page at the URL above. If this MD and the page disagree, the live page wins.

## Technical Specifications Questions and Answers for Medical Claims Electronic Data Transfer (MC EDT), Health Card Validation (HCV), e-Business Services and Conformance Test

This information requires knowledgeable interpretation and is intended primarily for members of the professional health care community. Some of these publications are available in English only due to their technical nature. To obtain more information, please contact [ServiceOntario](https://www.health.gov.on.ca/en/pro/publications/ohip/tech_specific/faq_tech_specs.aspx#ServiceOntario)

### e-Business Services Timelines

#### What are the dates for the new web services?

- June 2013 - Conformance testing environment available for Medical Claims Electronic Data Transfer (MC EDT) web service;
- June 2013 - MC EDT web service
- November 2013 – Scheduling for conformance testing of Health Card Validation

#### When will the ministry decommission the current modem based Electronic Data Transfer system?

No later than December 31, 2014.

### Medical Claims Electronic Data Transfer (MC EDT) and Health Card Validation Reference Manuals.

#### When will the updated MC EDT and HCV Reference Manuals be published?

Both the MC EDT and HCV Reference Manuals are published on the ministry web site under OHIP publications at:  
[http://www.health.gov.on.ca/en/pro/publications/ohip/]

### Master Service Agreement (MSA) and Identity Provider (IDP) Security Models

#### Which security models are available for which service?

The Master Services Agreement (MSA) and Identity Provider (IDP) models are available for Health Card Validation. Only the IDP model is available at this time for MC EDT. In order to use the IDP model, the health care provider must have a GO Secure Account.

#### What security model is for web service and web interface?

The Identity Provider (IDP) model is designed to be used for both the web service and the web interface. The MSA model is suited to organizations who can adequately manage their own identity and access management controls. The IDP model is better suited to software/Electronic Medical Records (EMR) solutions that do not provide adequate identity and access management controls. The MSA model is not available at this time for MC EDT but is available for the HCV web service.

#### Are the requirements different for the Master Services Agreement (MSA) or Identity Provider (IDP) authentication methods?

Yes, the Technical Specifications for Electronic Business Services (EBS) contains details on both models. The MSA is suited to organizations with their own IDP. The IDP model is better suited to software/Electronic Medical Records (EMR) solutions that do not provide adequate identity and access management controls. The MSA model is not available at this time for MC EDT but is available for HCV.

### PKI Certificates

#### What does the ministry consider a valid acceptable certificate?

Commercial certificates, such as Verisign are accepted. Self-signed certificates are also valid.

#### Are there any requirements for the storage of certificates?

Refer to the Government of Ontario Information & Technology Standards (GO-ITS) published standards: [http://www.mgs.gov.on.ca/en/IAndIT/STEL02_047295.html]

#### Are there parameters for certificate keys?

Yes, they are published in the Government of Ontario –Information & Technology Standards standards: [http://www.mgs.gov.on.ca/en/IAndIT/STEL02_047295.html]

#### What should we be signing our messages with for development and testing?

For conformance testing and production, each software installation requires a Secure Socket Layer (SSL) certificate. It must conform with GO-ITS key/cipher strength standards found at: [http://www.mgs.gov.on.ca/en/IAndIT/STEL02_047295.html]. Vendors can use any well known certificate authority, but the ministry recommends the same certificate used for development be used for conformance testing.

#### What are the certificate requirements we must have to access the web page interface?

No certificate is required for the web page/interface.

#### What do I do with ARM files provided in the conformance and production WSDL Zip files?

Import the ARM files into the key store and trust store that will be distributed with your application.

#### How are the three ARM files used?

The `go-pki_cacert.arm` is the CA signing certificate for the (OPS) GO-PKI Certificate Authority. It signs the certificate used by the service for encryption and verification. The public key for the ministry, included in the certificate we return in a response, is signed by GO-PKI CA.

The Entrust L1 Chain Certificate.arm is the Entrust certificate that constitutes the certificate signer chain that authenticates the service's SSL certificate. The L1C is an intermediate signer of the EBS SSL certificate.

The Entrust.netCertificationAuthority(2048).arm is the Entrust root CA signing certificate.

#### How do I get the public key for my clients?

You can use a CA Authority that is known to the ministry or a self-signed certificate. The key should be placed in the key store and/or trust store so the private key can be used in the decryption process of the response the ministry sends.

#### Do we need to exchange private keys?

No.

#### Which keys should I use for encryption/decryption?

The sending party uses the receiving party's public key to encrypt.

You send the transaction/request to the ministry using the keys provided (ministry public info/keys) and the ministry responds or sends using (in general) the public key provided in the request. For encryption use the ministry's public key and for decryption use your private key.

#### I am using a self-signed certificate. Why am I unable to decrypt the response I receive?

The tool that you are using to create the certificate is not adding the extensions, you need to decrypt. The missing element(s) may be the subject key identifier and the key usage.

#### Why do I get this error message: "The private key is not present in the X.509 certificate".

The Arm files provided by the ministry do not contain private keys. The only private key (excluding the secret key that the ministry sends in the response) that you need is the corresponding private key that pairs with the public key you sent in the request.

#### Why do I get this error message: "The Identity check failed for the outgoing message. The remote endpoint did not provide a domain name system (DNS) claim and therefore did not satisfied DNS identity 'ws.conf.ebs.health.gov.on.ca'. This may be caused by lack of DNS or CN name in the remote endpoint X.509 certificate's distinguished name".

You are using the wrong certificate to verify SSL trust and the intermediate chaining certificate to the service.

### e-Business Services Audit Trail

#### Who should have access to audit trails?

Audit trails could be subject to *Freedom of Information* requests or requested by the ministry to monitor suspicious activity. In order to maintain the integrity and security of the audit trail, access should be given to a system administrator type function.

#### Is the audit trail kept on the client side and provided to the ministry on request?

Yes. The logs for the audit trail should be kept on the system from which the request was submitted.

#### How long must the audit trail be kept?

Seven years is the government standard.

#### Does all data submitted need to be put in an audit log?

No. Please view the Audit Log Data Elements and Format contained in the technical specifications for Electronic Business Services.

#### Will responses come back encrypted?

Responses will be digitally signed and encrypted at the message level.

#### Who creates the audit ID?

An audit ID is generated by the sender of the request or the sender of the response.

### Medical Claims Electronic Data Transfer Web Page

#### Will a test environment be available for the Web Page?

No.

#### Can a vendor get a test ID for the MC EDT Web Page?

No, only Health Care Providers can register and use the MC EDT Web Page.

#### Is testing required for the MC EDT Web Page?

No.

#### Who do my clients contact when they experience problems with the MC EDT Upload/Download functionality?

All questions related to MC EDT Upload/Download should be directed to the ministry by calling 1-800-262-6524 or online at:  
[SSContactCentre.MOH@ontario.ca](mailto:SSContactCentre.MOH@ontario.ca)

#### Is software development required for the MC EDT Web Page?

No, however certain billing software may need to be updated.

### Medical Claims Electronic Data Transfer Files

#### Is Overnight Batch Eligibility Checking (OBEC) still available?

Yes.

#### Are the inbound and outbound file formats changing?

No changes to existing file layouts.

#### Will the file naming conventions change?

No.

#### Are all outbound files sent as zip files?

The new Medical Claims Electronic Data Transfer web service does not support zipped or compressed files at this time.

#### Why are only five files per transaction allowed for upload and download functions using the Web Service?

Five files per transaction were determined for the Web Service based on averages by usage of the current EDT service.

#### Is there a limit to the number of times an account can be accessed?

No, there are no restrictions.

### Medical Claims Electronic Data Transfer File Storage

#### Will the ministry store input and output files?

Yes, all files will be accessible for 12 months.

#### Do we need to keep track of the users download files?

Yes, the expectation is you will implement this requirement to produce a list of files with a download status.

### Medical Claims Electronic Data Transfer Sequence / List

#### Are there best practises to follow when determining what files are different between Monday and Tuesday?

All required metadata is returned by list call. Your implementation is dependent upon how you want to display this list of files to users.

#### Where is the list of resource types?

The list of currently supported file types is described in the Medical Claims Electronic Data Transfer (MC EDT) Currently Supported File Type Addendum of the MC EDT Technical Specifications.

#### What comes back in the list?

Metadata on viewable files comes back in the list. For example, all of the documents the user is able to access. Use the Resource ID to download those individual files.

#### How many files can I upload?

Five files per transaction, but there is no limit to the number of transactions.

#### Is there a limit on payload size?

Yes.

#### Is access to the web service language specific?

No, the schema and web services development language (WSDL) is to industry standard.

#### Will any sample code be available?

No.

### Web Service Conformance Testing MC EDT and HCV

#### Why do we need to undergo conformance testing?

Conformance testing ensures your software conforms to the technical specifications published by the ministry and also to obtain a production key before being granted access to production for each service.

#### What is the procedure to initiate conformance testing?

All questions related to conformance testing should be directed to the ministry's Help Desk by calling 1-800-262-6524 or online at: [SSContactCentre.MOH@ontario.ca](mailto:SSContactCentre.MOH@ontario.ca)

#### Who do I contact for any help/clarification during conformance testing?

Please send emails to: [HSC.MCEDT.Conformance.moh@ontario.ca](mailto:HSC.MCEDT.Conformance.moh@ontario.ca) for MC EDT and [HSC.Conformance.moh@ontario.ca](mailto:HSC.Conformance.moh@ontario.ca) for HCV.

#### Will vendors be provided with test data?

Yes, test scenarios and test data will be provided.

#### How will I get access to the HCV and MC EDT conformance testing?

Access to the conformance environment will be via the internet. The test package will contain the URL to the service, test data, test cases and expected results.

#### At what point in development should I schedule my conformance test?

Conformance testing should be scheduled when the development and testing of your system is completed, based on the ministry's published technical specifications before your client implementation.

#### What happens if I don't complete my testing in the scheduled time?

If you exceed your testing window and are granted an extension, your scheduled priority may be lowered due to other scheduled vendors.

#### What happens if I don't pass conformance testing?

You will be notified the reasons for the unsuccessful conformance test. You can then debug and correct your software and reapply for conformance testing.

#### Will schemas/WSDLs be available for testing of specifications?

Schemas/WSDLs are available for both the MC EDT and HCV web service. The published (public) WSDL is for production and can be found on the ministry website. A conformance WSDL is provided with the vendor package.

#### Do I need a production key for each service?

Yes.

#### Will testing be onsite or remote?

Testing will be done remotely using an Internet connection.

#### Do I have to demonstrate my software at a client pilot site?

No, there are no plans to do so at this time.

#### After successful conformance testing, when can my client go live?

Your clients can go live with a service in your software only if that service has passed conformance testing with the ministry and a production key for each service is provided.

#### When will I get my production key?

You will be emailed the production key on successful completion of conformance testing.

#### What happens to the conformance key assigned when conformance testing is completed?

The conformance key will be ended for each service.

#### Can the ministry provide help with the configuration of the platform I am using and/or assist with debugging during the development process?

No.

#### Why am I receiving “General System Error during conformance testing?

Please forward details to: [HSC.MCEDT.Conformance.moh@ontario.ca](mailto:HSC.MCEDT.Conformance.moh@ontario.ca) for MC EDT and [HSC.Conformance.moh@ontario.ca](mailto:HSC.Conformance.moh@ontario.ca) for HCV.

#### Where can I find information about available service methods?

Please refer to the MC EDT Technical Specifications at: [http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspecmcedt_ebs.pdf]

### MC EDT Specific

#### Will testing simulate full transactions, for example, sending a claims file and then receiving Remittance Advice (RAs)?

We are providing the output you would normally receive from a full transaction. The RAs we send you will not reflect the claims you submitted.

#### Do I have to execute all tests in the MC EDT Test Plan?

Yes. Please refer to ‘About This Test Plan’ tab in the MC EDT Test Plan.

#### Is the MOH ID the same as ServiceUserMUID element in the IDP header?

Yes, these terms are synonymous.

#### How many results are returned in the list method?

List method results are returned one page at a time to a maximum of 50 items per page.

#### What are the steps to download new documents available for a service user in conformance testing?

There are three (3) steps:
• Send getTypeList request
• If returned access type is DOWNLOAD or BOTH, send List request with status DOWNLOADABLE
• When received number of resourceIDs send download request

#### What is the best way to avoid downloading files in conformance that have already been downloaded and only download the new files?

It is the responsibility of the application to determine and track what files have already been downloaded.

#### How is the list method results sorted?

The list method returns results in descending order by create timestamp.

#### I am having difficulty decrypting MC EDT MTOM response attachments the ministry sends. How is it constructed?

The response is encrypted with a unique symmetric key (secret key). It is returned in the response message as the EncryptedKey->CipherData->CipherValue.

The secret key is returned encrypted using the public key retrieved from the certificate that was used to sign the incoming message and the corresponding private key must be used to decrypt it.

All bits of the public key are used.

The secret key is encrypted using an RSA cipher with PKCS1 padding. The secret key itself is 128 bits long, but encrypts to 128 bytes then Base64 encodes to 172 bytes (octets).

The initialization vector is the first 16 bytes/octets of the cipher value in the body. To recover the IV, Base64 decode the CipherValue and take the first 16 bytes from it; the remainder is the encrypted message.

#### What is the format of physician and groups Claim Submission Numbers (CSN) aka billing number and where is it stored?

CSN is stored in the “ServiceuserMUID” field in the EBS header. The minimum field length is 5 and the maximum field length is 6. A physician CSN is 6 numeric digits. A group billing number must begin with a leading zero and must be in this format 0{A-Z0-9}{4}.

### HCV Specific

#### Will testing simulate full transaction, for example, sending an HCV soap request and getting back a soap response?

Yes

#### When will HCV conformance testing be available?

HCV conformance testing is currently available for MSA users. Scheduling for conformance testing of IdP will begin in late November 2013

#### Do we need an action for each of the error codes for HCV conformance testing?

Testing for error codes for HCV conformance testing is not mandatory. This decision is up to your discretion but not all will be possible for conformance testing.

#### Is the conformance testing process for the HCV web service the same as that for legacy HCV conformance testing?

This conformance testing process is not applicable to the legacy HCV conformance testing process. The legacy HCV conformance testing process is status quo.

The response message encryption scheme is AES cipher with CBC block mechanism and PKCS5 padding.

#### Will test data or test patient information be provided during Health Card Validation conformance testing?

Yes, test scenarios and test data will be provided.

#### How do we know what the response is for each request for HCV basic?

At this time, each HCV basic request can only include a single Health Number (submit a single Health Number at a time). Details will be outlined in the revised HCV Reference Manual.

#### Is the conformance testing process for the HCV web service the same as that for conformance testing legacy HCV?

This conformance testing process is not applicable to the legacy HCV conformance testing process. The legacy HCV conformance testing process is status quo.

### Health Card Validation (HCV)

#### Are there plans to decommission or phase out the Health Card Validation Information Management Systems (IMS) listener and connect technology?

There are no plans to decommission IMS listener at this time.

#### Can I continue to use an External Network Access (ENA) connection for the web service?

Yes, at this time both the MC EDT and HCV web services in production can be accessed from an ENA or Internet connection. However for the conformance testing, the web services can only be accessed via the Internet.

#### Are the response code descriptions in the HCV reference manual still applicable to the corresponding response codes?

Yes, there are no changes to the response codes or descriptions for regular HCV transactions.

#### Will the new HCV web service allow for validation of the special fee codes avoiding the need to use the IVR?

Yes, the HCV web service will include functionality for time limited codes for ocular visual, bone density and sleep studies.

##### For More Information

Call **ServiceOntario**, Infoline at 1-866-532-3161  
In Toronto, 416-314-5518  
TTY 1-800-387-5559  
In Toronto, TTY 416-327-4282  
Hours of operation : 8:30am - 5:00pm

## Linked resources

- [Technical Specifications for Electronic Business Services - MCEDT (PDF)](http://www.health.gov.on.ca/en/pro/publications/ohip/docs/techspecmcedt_ebs.pdf)
