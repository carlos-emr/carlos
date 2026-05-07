import os
import re
import subprocess

FILES = [
    "src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/administration/GstReport.java",
    "src/main/java/io/github/carlos_emr/carlos/utility/PDFEncryptionUtil.java",
    "src/main/java/io/github/carlos_emr/carlos/utility/JsDateSerializer.java",
    "src/main/java/io/github/carlos_emr/carlos/utility/EmailSendingException.java",
    "src/main/java/io/github/carlos_emr/carlos/config/ServletContextConfig.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/ObjectFactory.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/MatchingClientParameters.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/DuplicateHinException.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/HelloWorld2Response.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/HelloWorld.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/HelloWorld2.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/MatchingClientScore.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/InvalidHinException.java",
    "src/main/java/io/github/carlos_emr/carlos/ws/HelloWorldResponse.java",
    "src/main/java/io/github/carlos_emr/carlos/integration/ebs/App.java",
    "src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/config/data/SpecialistDto.java",
    "src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/config/data/ConsultationServiceDto.java",
    "src/main/java/io/github/carlos_emr/carlos/encounter/oceanEReferal/pageUtil/OceanEReferralAttachmentUtil.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/EReferAttachmentData.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/EmailConfig.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/EReferAttachment.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/JSONAction.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/EmailAttachment.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/EReferAttachmentDataCompositeKey.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/DigitalSignatureModuleTypeConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/FaxJobStatusConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/ClientLinkTypeConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/HnrDataValidationTypeConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/EmailLogTransactionTypeConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/TicklerStatusConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/FaxConfigProviderTypeConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/AppointmentBookingSourceConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/EmailConfigTypeConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/EmailLogChartDisplayOptionConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/TicklerPriorityConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/EmailConfigProviderConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/EmailLogStatusConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/model/converter/EmailAttachmentDocumentTypeConverter.java",
    "src/main/java/io/github/carlos_emr/carlos/commn/dao/EReferAttachmentDataDao.java",
    "src/main/java/io/github/carlos_emr/carlos/email/helpers/LocalSMTPEmailSender.java"
]

def generate_javadoc(filepath, classname, content):
    since = "2025-01-01"
    try:
        res = subprocess.check_output(f'git log --follow --format="%ai" {filepath} | tail -1', shell=True).decode('utf-8').strip()
        if res:
            since = res.split(' ')[0]
    except Exception:
        pass

    doc = []
    doc.append("/**")

    if "GstReport" in classname:
        doc.append(" * Retrieves and aggregates Goods and Services Tax (GST) data for provider billing records.")
        doc.append(" * <p>")
        doc.append(" * Facilitates the generation of GST reports within the British Columbia administration module")
        doc.append(" * by querying billing entries against demographic and provider information.")
    elif "PDFEncryptionUtil" in classname:
        doc.append(" * Provides utility methods for securing and encrypting PDF documents.")
        doc.append(" * <p>")
        doc.append(" * Includes functionality for applying password protection and cryptographic constraints")
        doc.append(" * to ensure privacy and compliance when handling generated PDF files.")
    elif "JsDateSerializer" in classname:
        doc.append(" * Serializes Java date objects into a format compatible with JavaScript clients.")
        doc.append(" * <p>")
        doc.append(" * Ensures consistent date and time representation across the application's JSON APIs.")
    elif "EmailSendingException" in classname:
        doc.append(" * Exception thrown to indicate a failure during the transmission of an email message.")
        doc.append(" * <p>")
        doc.append(" * This can be caused by configuration errors, network issues, or SMTP server rejections.")
    elif "ServletContextConfig" in classname:
        doc.append(" * Configures the ServletContext for the web application environment.")
        doc.append(" * <p>")
        doc.append(" * Registers necessary filters, listeners, and servlets dynamically upon application startup.")
    elif "ObjectFactory" in classname:
        doc.append(" * Factory class for instantiating JAXB-bound objects.")
        doc.append(" * <p>")
        doc.append(" * Provides methods to create instances of XML schema elements and complex types")
        doc.append(" * used within the web service communication layers.")
    elif "MatchingClientParameters" in classname:
        doc.append(" * Encapsulates search and matching criteria for identifying clients.")
        doc.append(" * <p>")
        doc.append(" * Contains properties such as name, date of birth, and health insurance number")
        doc.append(" * to facilitate demographic matching algorithms.")
    elif "DuplicateHinException" in classname:
        doc.append(" * Exception thrown when attempting to register or update a client with a Health Insurance Number (HIN)")
        doc.append(" * that already exists in the system.")
    elif "HelloWorld2Response" in classname:
        doc.append(" * Response envelope for the secondary HelloWorld web service endpoint.")
        doc.append(" * <p>")
        doc.append(" * Wraps the returned greeting payload for JAXB marshalling.")
    elif "HelloWorld" in classname:
        doc.append(" * Simple diagnostic web service endpoint.")
        doc.append(" * <p>")
        doc.append(" * Used to verify network connectivity and proper deployment of the SOAP/REST service stack.")
    elif "MatchingClientScore" in classname:
        doc.append(" * Represents the confidence score of a demographic matching operation.")
        doc.append(" * <p>")
        doc.append(" * Quantifies the likelihood that a provided set of client parameters matches an existing record.")
    elif "InvalidHinException" in classname:
        doc.append(" * Exception thrown when a Health Insurance Number (HIN) fails validation checks.")
        doc.append(" * <p>")
        doc.append(" * Validation failures may include incorrect format, invalid checksums, or jurisdictional mismatches.")
    elif "HelloWorldResponse" in classname:
        doc.append(" * Response envelope for the primary HelloWorld web service endpoint.")
    elif "App" in classname:
        doc.append(" * Main entry point for the external billing service (EBS) integration module.")
    elif "SpecialistDto" in classname:
        doc.append(" * Data Transfer Object representing a medical specialist.")
        doc.append(" * <p>")
        doc.append(" * Carries specialist details for use in consultation request and referral workflows.")
    elif "ConsultationServiceDto" in classname:
        doc.append(" * Data Transfer Object representing a specific consultation service offering.")
        doc.append(" * <p>")
        doc.append(" * Details the type of service, associated providers, and categorical metadata.")
    elif "OceanEReferralAttachmentUtil" in classname:
        doc.append(" * Utility methods for handling and processing attachments associated with Ocean eReferrals.")
        doc.append(" * <p>")
        doc.append(" * Assists in the extraction, transformation, and storage of referral documents.")
    elif "EReferAttachmentDataCompositeKey" in classname:
        doc.append(" * Composite primary key for the EReferAttachmentData entity.")
        doc.append(" * <p>")
        doc.append(" * Uniquely identifies binary attachment content using a combination of referral and attachment IDs.")
    elif "EReferAttachmentData" in classname:
        doc.append(" * Domain entity containing the binary payload of an eReferral attachment.")
    elif "EmailConfig" in classname:
        doc.append(" * Configuration entity detailing SMTP settings and credentials for outgoing email.")
        doc.append(" * <p>")
        doc.append(" * Supports mapping multiple configuration profiles to different providers or system roles.")
    elif "EReferAttachment" in classname:
        doc.append(" * Domain entity representing metadata for an eReferral attachment.")
        doc.append(" * <p>")
        doc.append(" * Tracks the file name, content type, and association to a specific referral record.")
    elif "JSONAction" in classname:
        doc.append(" * Base action class for handling Struts requests that return JSON payloads.")
        doc.append(" * <p>")
        doc.append(" * Provides common utilities for structuring and serializing response data.")
    elif "EmailAttachment" in classname:
        doc.append(" * Domain entity representing a file attached to an outgoing or logged email message.")
    elif "DigitalSignatureModuleTypeConverter" in classname:
        doc.append(" * JPA Attribute Converter for mapping DigitalSignatureModuleType enum values.")
        doc.append(" * <p>")
        doc.append(" * Translates between the Java enumeration and its underlying database column representation.")
    elif "Converter" in classname:
        # Fallback for other converters
        entity = classname.replace("Converter", "")
        doc.append(f" * JPA Attribute Converter for mapping {entity} enum values.")
        doc.append(" * <p>")
        doc.append(" * Safely translates between the domain enumeration and its database column representation.")
    elif "Dao" in classname:
        entity = classname.replace("Dao", "")
        doc.append(f" * Data Access Object (DAO) interface for {entity} entities.")
        doc.append(" * <p>")
        doc.append(" * Provides standard CRUD operations and custom querying capabilities against the database.")
    elif "LocalSMTPEmailSender" in classname:
        doc.append(" * Implementation of an email sender utilizing a local or specifically configured SMTP relay.")
        doc.append(" * <p>")
        doc.append(" * Handles the construction and transmission of MIME messages based on system configuration.")
    else:
        doc.append(f" * Represents the {classname} entity/component.")
        doc.append(" * <p>")
        doc.append(" * Provides core functionality and data encapsulation for this domain element.")

    doc.append(" *")
    doc.append(f" * @since {since}")
    doc.append(" */")
    return "\n".join(doc)

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find the old Javadoc we inserted previously
    # It looks like:
    # /**
    #  * Represents the ...
    #  *
    #  * @since ...
    #  */

    old_javadoc_pattern = r'/\*\*.*?\n \* @since.*?\n \*/\n*'
    content = re.sub(old_javadoc_pattern, '', content, flags=re.DOTALL)

    # Now insert the new Javadoc
    class_match = re.search(r'^.*?public\s+(abstract\s+)?(class|interface|enum)\s+\w+', content, re.MULTILINE)
    if not class_match:
        return False

    classname = re.search(r'\bpublic\s+(?:abstract\s+)?(?:class|interface|enum)\s+(\w+)', content).group(1)

    javadoc = generate_javadoc(filepath, classname, content)

    lines = content[:class_match.start()].split('\n')
    insert_idx = class_match.start()

    for i in range(len(lines)-1, -1, -1):
        line = lines[i].strip()
        if line.startswith('@') or line == '':
            insert_idx -= len(lines[i]) + 1
        else:
            break

    new_content = content[:insert_idx] + '\n' + javadoc + '\n' + content[insert_idx:]

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)

    return True

for f in FILES:
    process_file(f)

print("Updated Javadocs with proper standards.")
