package ca.openosp.openo.ar2005;

import org.apache.xmlbeans.xml.stream.XMLStreamException;
import org.apache.xmlbeans.xml.stream.XMLInputStream;
import org.w3c.dom.Node;
import javax.xml.stream.XMLStreamReader;
import java.io.Reader;
import java.io.InputStream;
import java.net.URL;
import java.io.IOException;
import java.io.File;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlDateTime;
import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlInt;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * AR1 (Antenatal Record 1) - British Columbia Antenatal Record form interface.
 *
 * This interface represents the primary antenatal record (BCAR) form used in British Columbia
 * for comprehensive pregnancy care documentation. It extends XmlObject to provide XML-based
 * serialization and deserialization capabilities for storing and exchanging antenatal care data.
 *
 * The AR1 form captures critical pregnancy information including:
 * <ul>
 *   <li>Patient demographic and identification information</li>
 *   <li>Partner information for family history and support tracking</li>
 *   <li>Healthcare practitioner details and care provider assignments</li>
 *   <li>Complete pregnancy history including previous pregnancies and outcomes</li>
 *   <li>Obstetrical history with previous delivery and complication records</li>
 *   <li>Comprehensive medical history and physical examination findings</li>
 *   <li>Initial laboratory investigations and diagnostic test results</li>
 *   <li>Clinical comments and additional documentation notes</li>
 *   <li>Provider signatures for regulatory compliance and audit trails</li>
 * </ul>
 *
 * This form is part of the standardized medical forms used across BC healthcare facilities
 * to ensure consistent, high-quality prenatal care documentation. All data is stored in
 * compliance with PIPEDA privacy regulations for protected health information (PHI).
 *
 * @see PatientInformation
 * @see PartnerInformation
 * @see PractitionerInformation
 * @see PregnancyHistory
 * @see ObstetricalHistory
 * @see MedicalHistoryAndPhysicalExam
 * @see InitialLaboratoryInvestigations
 * @see SignatureType
 *
 * @since 2026-01-24
 */
public interface AR1 extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(AR1.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("ar15fa5type");

    /**
     * Gets the unique identifier for this AR1 form record.
     *
     * @return int the unique form identifier
     */
    int getId();

    /**
     * Gets the unique identifier as an XmlInt object for XML serialization.
     *
     * @return XmlInt the form identifier as XML integer type
     */
    XmlInt xgetId();

    /**
     * Sets the unique identifier for this AR1 form record.
     *
     * @param p0 int the unique form identifier to set
     */
    void setId(final int p0);

    /**
     * Sets the unique identifier using an XmlInt object for XML deserialization.
     *
     * @param p0 XmlInt the form identifier as XML integer type
     */
    void xsetId(final XmlInt p0);

    /**
     * Gets the version identifier for form revision tracking.
     * Used to track different versions of the same AR1 record across edits.
     *
     * @return int the version identifier
     */
    int getVersionID();

    /**
     * Gets the version identifier as an XmlInt object for XML serialization.
     *
     * @return XmlInt the version identifier as XML integer type
     */
    XmlInt xgetVersionID();

    /**
     * Sets the version identifier for form revision tracking.
     *
     * @param p0 int the version identifier to set
     */
    void setVersionID(final int p0);

    /**
     * Sets the version identifier using an XmlInt object for XML deserialization.
     *
     * @param p0 XmlInt the version identifier as XML integer type
     */
    void xsetVersionID(final XmlInt p0);

    /**
     * Gets the episode identifier linking this form to a specific care episode.
     * Episodes group related encounters and forms for continuity of care tracking.
     *
     * @return int the episode identifier
     */
    int getEpisodeId();

    /**
     * Gets the episode identifier as an XmlInt object for XML serialization.
     *
     * @return XmlInt the episode identifier as XML integer type
     */
    XmlInt xgetEpisodeId();

    /**
     * Sets the episode identifier linking this form to a specific care episode.
     *
     * @param p0 int the episode identifier to set
     */
    void setEpisodeId(final int p0);

    /**
     * Sets the episode identifier using an XmlInt object for XML deserialization.
     *
     * @param p0 XmlInt the episode identifier as XML integer type
     */
    void xsetEpisodeId(final XmlInt p0);

    /**
     * Gets the demographic number (patient identifier) for the pregnant patient.
     * This links the antenatal record to the patient's demographic information.
     *
     * @return int the patient's demographic number
     */
    int getDemographicNo();

    /**
     * Gets the demographic number as an XmlInt object for XML serialization.
     *
     * @return XmlInt the demographic number as XML integer type
     */
    XmlInt xgetDemographicNo();

    /**
     * Sets the demographic number (patient identifier) for the pregnant patient.
     *
     * @param p0 int the patient's demographic number to set
     */
    void setDemographicNo(final int p0);

    /**
     * Sets the demographic number using an XmlInt object for XML deserialization.
     *
     * @param p0 XmlInt the demographic number as XML integer type
     */
    void xsetDemographicNo(final XmlInt p0);

    /**
     * Gets the provider number identifying the healthcare practitioner responsible for this record.
     *
     * @return String the provider's unique identifier
     */
    String getProviderNo();

    /**
     * Gets the provider number as an XmlString object for XML serialization.
     *
     * @return XmlString the provider number as XML string type
     */
    XmlString xgetProviderNo();

    /**
     * Sets the provider number identifying the healthcare practitioner.
     *
     * @param p0 String the provider's unique identifier to set
     */
    void setProviderNo(final String p0);

    /**
     * Sets the provider number using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the provider number as XML string type
     */
    void xsetProviderNo(final XmlString p0);

    /**
     * Gets the date and time when this AR1 form was originally created.
     *
     * @return Calendar the form creation timestamp
     */
    Calendar getFormCreated();

    /**
     * Gets the form creation date as an XmlDate object for XML serialization.
     *
     * @return XmlDate the creation date as XML date type
     */
    XmlDate xgetFormCreated();

    /**
     * Sets the date and time when this AR1 form was originally created.
     *
     * @param p0 Calendar the form creation timestamp to set
     */
    void setFormCreated(final Calendar p0);

    /**
     * Sets the form creation date using an XmlDate object for XML deserialization.
     *
     * @param p0 XmlDate the creation date as XML date type
     */
    void xsetFormCreated(final XmlDate p0);

    /**
     * Gets the date and time of the most recent edit to this AR1 form.
     * Used for audit trails and tracking form modifications.
     *
     * @return Calendar the last edit timestamp
     */
    Calendar getFormEdited();

    /**
     * Gets the last edit timestamp as an XmlDateTime object for XML serialization.
     *
     * @return XmlDateTime the edit timestamp as XML datetime type
     */
    XmlDateTime xgetFormEdited();

    /**
     * Sets the date and time of the most recent edit to this AR1 form.
     *
     * @param p0 Calendar the last edit timestamp to set
     */
    void setFormEdited(final Calendar p0);

    /**
     * Sets the last edit timestamp using an XmlDateTime object for XML deserialization.
     *
     * @param p0 XmlDateTime the edit timestamp as XML datetime type
     */
    void xsetFormEdited(final XmlDateTime p0);

    /**
     * Gets the patient information section containing demographic and contact details.
     * This includes the pregnant patient's name, date of birth, address, and other
     * identifying information required for the antenatal record.
     *
     * @return PatientInformation the patient information section
     */
    PatientInformation getPatientInformation();

    /**
     * Sets the patient information section.
     *
     * @param p0 PatientInformation the patient information section to set
     */
    void setPatientInformation(final PatientInformation p0);

    /**
     * Creates and adds a new patient information section to this AR1 form.
     *
     * @return PatientInformation the newly created patient information section
     */
    PatientInformation addNewPatientInformation();

    /**
     * Gets the partner information section containing details about the patient's partner.
     * This includes partner's name, contact information, and relevant family history
     * for genetic counseling and support planning.
     *
     * @return PartnerInformation the partner information section
     */
    PartnerInformation getPartnerInformation();

    /**
     * Sets the partner information section.
     *
     * @param p0 PartnerInformation the partner information section to set
     */
    void setPartnerInformation(final PartnerInformation p0);

    /**
     * Creates and adds a new partner information section to this AR1 form.
     *
     * @return PartnerInformation the newly created partner information section
     */
    PartnerInformation addNewPartnerInformation();

    /**
     * Gets the practitioner information section containing healthcare provider details.
     * This includes the primary care provider, obstetrician, and other healthcare
     * professionals involved in the patient's antenatal care.
     *
     * @return PractitionerInformation the practitioner information section
     */
    PractitionerInformation getPractitionerInformation();

    /**
     * Sets the practitioner information section.
     *
     * @param p0 PractitionerInformation the practitioner information section to set
     */
    void setPractitionerInformation(final PractitionerInformation p0);

    /**
     * Creates and adds a new practitioner information section to this AR1 form.
     *
     * @return PractitionerInformation the newly created practitioner information section
     */
    PractitionerInformation addNewPractitionerInformation();

    /**
     * Gets the pregnancy history section containing information about current pregnancy.
     * This includes estimated due date, dating methods, pregnancy symptoms, and
     * current pregnancy progression details.
     *
     * @return PregnancyHistory the pregnancy history section
     */
    PregnancyHistory getPregnancyHistory();

    /**
     * Sets the pregnancy history section.
     *
     * @param p0 PregnancyHistory the pregnancy history section to set
     */
    void setPregnancyHistory(final PregnancyHistory p0);

    /**
     * Creates and adds a new pregnancy history section to this AR1 form.
     *
     * @return PregnancyHistory the newly created pregnancy history section
     */
    PregnancyHistory addNewPregnancyHistory();

    /**
     * Gets the obstetrical history section containing past pregnancy and delivery records.
     * This includes gravida/para status, previous pregnancy outcomes, complications,
     * delivery methods, and birth weights for risk assessment.
     *
     * @return ObstetricalHistory the obstetrical history section
     */
    ObstetricalHistory getObstetricalHistory();

    /**
     * Sets the obstetrical history section.
     *
     * @param p0 ObstetricalHistory the obstetrical history section to set
     */
    void setObstetricalHistory(final ObstetricalHistory p0);

    /**
     * Creates and adds a new obstetrical history section to this AR1 form.
     *
     * @return ObstetricalHistory the newly created obstetrical history section
     */
    ObstetricalHistory addNewObstetricalHistory();

    /**
     * Gets the medical history and physical examination section.
     * This includes comprehensive medical history, current medications, allergies,
     * family history, physical examination findings, and baseline vital signs.
     *
     * @return MedicalHistoryAndPhysicalExam the medical history and physical exam section
     */
    MedicalHistoryAndPhysicalExam getMedicalHistoryAndPhysicalExam();

    /**
     * Sets the medical history and physical examination section.
     *
     * @param p0 MedicalHistoryAndPhysicalExam the medical history and physical exam section to set
     */
    void setMedicalHistoryAndPhysicalExam(final MedicalHistoryAndPhysicalExam p0);

    /**
     * Creates and adds a new medical history and physical examination section to this AR1 form.
     *
     * @return MedicalHistoryAndPhysicalExam the newly created medical history and physical exam section
     */
    MedicalHistoryAndPhysicalExam addNewMedicalHistoryAndPhysicalExam();

    /**
     * Gets the initial laboratory investigations section containing baseline test results.
     * This includes blood type, Rh factor, antibody screens, infectious disease screening,
     * genetic testing results, and other routine prenatal laboratory investigations.
     *
     * @return InitialLaboratoryInvestigations the initial laboratory investigations section
     */
    InitialLaboratoryInvestigations getInitialLaboratoryInvestigations();

    /**
     * Sets the initial laboratory investigations section.
     *
     * @param p0 InitialLaboratoryInvestigations the initial laboratory investigations section to set
     */
    void setInitialLaboratoryInvestigations(final InitialLaboratoryInvestigations p0);

    /**
     * Creates and adds a new initial laboratory investigations section to this AR1 form.
     *
     * @return InitialLaboratoryInvestigations the newly created initial laboratory investigations section
     */
    InitialLaboratoryInvestigations addNewInitialLaboratoryInvestigations();

    /**
     * Gets the primary comments field for clinical notes and observations.
     * This field is used for documenting additional clinical information, care plans,
     * and other notes relevant to the patient's antenatal care.
     *
     * @return String the clinical comments text
     */
    String getComments();

    /**
     * Gets the comments as an XmlString object for XML serialization.
     *
     * @return XmlString the comments as XML string type
     */
    XmlString xgetComments();

    /**
     * Sets the primary comments field for clinical notes.
     *
     * @param p0 String the clinical comments text to set
     */
    void setComments(final String p0);

    /**
     * Sets the comments using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the comments as XML string type
     */
    void xsetComments(final XmlString p0);

    /**
     * Gets the extra comments field for supplementary clinical documentation.
     * This field provides additional space for extended notes beyond the primary
     * comments section when more detailed documentation is required.
     *
     * @return String the extra comments text
     */
    String getExtraComments();

    /**
     * Gets the extra comments as an XmlString object for XML serialization.
     *
     * @return XmlString the extra comments as XML string type
     */
    XmlString xgetExtraComments();

    /**
     * Sets the extra comments field for supplementary documentation.
     *
     * @param p0 String the extra comments text to set
     */
    void setExtraComments(final String p0);

    /**
     * Sets the extra comments using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the extra comments as XML string type
     */
    void xsetExtraComments(final XmlString p0);

    /**
     * Gets the signatures section containing provider signatures for regulatory compliance.
     * This includes electronic or digital signatures from healthcare providers who
     * reviewed and approved the antenatal record for audit trail purposes.
     *
     * @return SignatureType the signatures section
     */
    SignatureType getSignatures();

    /**
     * Sets the signatures section.
     *
     * @param p0 SignatureType the signatures section to set
     */
    void setSignatures(final SignatureType p0);

    /**
     * Creates and adds a new signatures section to this AR1 form.
     *
     * @return SignatureType the newly created signatures section
     */
    SignatureType addNewSignatures();

    /**
     * Factory class for creating and parsing AR1 instances.
     *
     * This factory provides static methods for instantiating new AR1 objects and
     * parsing AR1 data from various XML sources including strings, files, streams,
     * and DOM nodes. All parsing methods support optional XmlOptions for customizing
     * XML processing behavior such as validation, namespace handling, and error reporting.
     */
    public static final class Factory
    {
        /**
         * Creates a new empty AR1 instance with default XML options.
         *
         * @return AR1 a new AR1 instance
         */
        public static AR1 newInstance() {
            return (AR1)XmlBeans.getContextTypeLoader().newInstance(AR1.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty AR1 instance with custom XML options.
         *
         * @param options XmlOptions for customizing XML processing behavior
         * @return AR1 a new AR1 instance
         */
        public static AR1 newInstance(final XmlOptions options) {
            return (AR1)XmlBeans.getContextTypeLoader().newInstance(AR1.type, options);
        }

        /**
         * Parses an AR1 instance from an XML string with default options.
         *
         * @param xmlAsString String containing the XML representation of an AR1 form
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AR1 parse(final String xmlAsString) throws XmlException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(xmlAsString, AR1.type, (XmlOptions)null);
        }

        /**
         * Parses an AR1 instance from an XML string with custom options.
         *
         * @param xmlAsString String containing the XML representation of an AR1 form
         * @param options XmlOptions for customizing XML parsing behavior
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AR1 parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(xmlAsString, AR1.type, options);
        }

        /**
         * Parses an AR1 instance from an XML file with default options.
         *
         * @param file File containing the XML representation of an AR1 form
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading the file
         */
        public static AR1 parse(final File file) throws XmlException, IOException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(file, AR1.type, (XmlOptions)null);
        }

        /**
         * Parses an AR1 instance from an XML file with custom options.
         *
         * @param file File containing the XML representation of an AR1 form
         * @param options XmlOptions for customizing XML parsing behavior
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading the file
         */
        public static AR1 parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(file, AR1.type, options);
        }

        /**
         * Parses an AR1 instance from a URL with default options.
         *
         * @param u URL pointing to the XML representation of an AR1 form
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error accessing the URL
         */
        public static AR1 parse(final URL u) throws XmlException, IOException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(u, AR1.type, (XmlOptions)null);
        }

        /**
         * Parses an AR1 instance from a URL with custom options.
         *
         * @param u URL pointing to the XML representation of an AR1 form
         * @param options XmlOptions for customizing XML parsing behavior
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error accessing the URL
         */
        public static AR1 parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(u, AR1.type, options);
        }

        /**
         * Parses an AR1 instance from an input stream with default options.
         *
         * @param is InputStream containing the XML representation of an AR1 form
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading the stream
         */
        public static AR1 parse(final InputStream is) throws XmlException, IOException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(is, AR1.type, (XmlOptions)null);
        }

        /**
         * Parses an AR1 instance from an input stream with custom options.
         *
         * @param is InputStream containing the XML representation of an AR1 form
         * @param options XmlOptions for customizing XML parsing behavior
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading the stream
         */
        public static AR1 parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(is, AR1.type, options);
        }

        /**
         * Parses an AR1 instance from a character reader with default options.
         *
         * @param r Reader containing the XML representation of an AR1 form
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading from the reader
         */
        public static AR1 parse(final Reader r) throws XmlException, IOException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(r, AR1.type, (XmlOptions)null);
        }

        /**
         * Parses an AR1 instance from a character reader with custom options.
         *
         * @param r Reader containing the XML representation of an AR1 form
         * @param options XmlOptions for customizing XML parsing behavior
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading from the reader
         */
        public static AR1 parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(r, AR1.type, options);
        }

        /**
         * Parses an AR1 instance from an XML stream reader with default options.
         *
         * @param sr XMLStreamReader positioned at the AR1 XML content
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AR1 parse(final XMLStreamReader sr) throws XmlException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(sr, AR1.type, (XmlOptions)null);
        }

        /**
         * Parses an AR1 instance from an XML stream reader with custom options.
         *
         * @param sr XMLStreamReader positioned at the AR1 XML content
         * @param options XmlOptions for customizing XML parsing behavior
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AR1 parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(sr, AR1.type, options);
        }

        /**
         * Parses an AR1 instance from a DOM node with default options.
         *
         * @param node Node containing the AR1 XML document or element
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AR1 parse(final Node node) throws XmlException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(node, AR1.type, (XmlOptions)null);
        }

        /**
         * Parses an AR1 instance from a DOM node with custom options.
         *
         * @param node Node containing the AR1 XML document or element
         * @param options XmlOptions for customizing XML parsing behavior
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AR1 parse(final Node node, final XmlOptions options) throws XmlException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(node, AR1.type, options);
        }

        /**
         * Parses an AR1 instance from an XMLInputStream with default options.
         *
         * @deprecated Use {@link #parse(InputStream)} or {@link #parse(XMLStreamReader)} instead
         * @param xis XMLInputStream containing the AR1 XML content
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error processing the XML stream
         */
        @Deprecated
        public static AR1 parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(xis, AR1.type, (XmlOptions)null);
        }

        /**
         * Parses an AR1 instance from an XMLInputStream with custom options.
         *
         * @deprecated Use {@link #parse(InputStream, XmlOptions)} or {@link #parse(XMLStreamReader, XmlOptions)} instead
         * @param xis XMLInputStream containing the AR1 XML content
         * @param options XmlOptions for customizing XML parsing behavior
         * @return AR1 the parsed AR1 instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error processing the XML stream
         */
        @Deprecated
        public static AR1 parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (AR1)XmlBeans.getContextTypeLoader().parse(xis, AR1.type, options);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with default options.
         *
         * @deprecated XMLInputStream is deprecated in favor of XMLStreamReader
         * @param xis XMLInputStream to validate
         * @return XMLInputStream a validating stream wrapper
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if there is an error processing the XML stream
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, AR1.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with custom options.
         *
         * @deprecated XMLInputStream is deprecated in favor of XMLStreamReader
         * @param xis XMLInputStream to validate
         * @param options XmlOptions for customizing validation behavior
         * @return XMLInputStream a validating stream wrapper
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if there is an error processing the XML stream
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, AR1.type, options);
        }

        /**
         * Private constructor to prevent instantiation of this factory class.
         */
        private Factory() {
        }
    }
}
