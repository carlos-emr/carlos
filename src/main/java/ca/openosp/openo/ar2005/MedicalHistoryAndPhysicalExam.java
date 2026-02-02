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
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * XMLBeans interface representing the Medical History and Physical Examination section
 * of the British Columbia Antenatal Record (BCAR/AR2005) form.
 *
 * This interface provides structured access to comprehensive prenatal care documentation including:
 * - Current pregnancy details and status
 * - Patient medical history and conditions
 * - Generic health history information
 * - Infectious disease screening and history
 * - Psychosocial assessment and risk factors
 * - Family medical history
 * - Physical examination findings
 *
 * The AR2005 form is a standardized provincial healthcare document used across British Columbia
 * for antenatal care tracking and must comply with BC Ministry of Health data standards.
 *
 * This interface is auto-generated from XML schema definitions using Apache XMLBeans framework,
 * providing type-safe XML serialization/deserialization for healthcare data exchange.
 *
 * @see CurrentPregnancyType
 * @see MedicalHistoryType
 * @see GenericHistoryType
 * @see InfectiousDiseaseType
 * @see PsychosocialType
 * @see FamilyHistoryType
 * @see PhysicalExaminationType
 * @since 2026-01-23
 */
public interface MedicalHistoryAndPhysicalExam extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(MedicalHistoryAndPhysicalExam.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("medicalhistoryandphysicalexam176ftype");

    /**
     * Gets the current pregnancy information including gestational details, prenatal visits,
     * and pregnancy-specific medical conditions.
     *
     * @return CurrentPregnancyType the current pregnancy data, or null if not set
     */
    CurrentPregnancyType getCurrentPregnancy();

    /**
     * Sets the current pregnancy information for this antenatal record.
     *
     * @param p0 CurrentPregnancyType containing pregnancy details including EDC, gestational age, and prenatal care
     */
    void setCurrentPregnancy(final CurrentPregnancyType p0);

    /**
     * Creates and adds a new current pregnancy element to this medical history record.
     *
     * @return CurrentPregnancyType newly created pregnancy element ready for population
     */
    CurrentPregnancyType addNewCurrentPregnancy();

    /**
     * Gets the patient's medical history including pre-existing conditions, surgeries,
     * hospitalizations, and chronic diseases relevant to antenatal care.
     *
     * @return MedicalHistoryType the medical history data, or null if not set
     */
    MedicalHistoryType getMedicalHistory();

    /**
     * Sets the medical history for this patient record.
     *
     * @param p0 MedicalHistoryType containing comprehensive medical background including conditions, medications, and allergies
     */
    void setMedicalHistory(final MedicalHistoryType p0);

    /**
     * Creates and adds a new medical history element to this record.
     *
     * @return MedicalHistoryType newly created medical history element ready for population
     */
    MedicalHistoryType addNewMedicalHistory();

    /**
     * Gets the generic health history information including lifestyle factors,
     * general health status, and non-specific medical background.
     *
     * @return GenericHistoryType the generic health history data, or null if not set
     */
    GenericHistoryType getGenericHistory();

    /**
     * Sets the generic health history for this patient record.
     *
     * @param p0 GenericHistoryType containing general health information and lifestyle factors
     */
    void setGenericHistory(final GenericHistoryType p0);

    /**
     * Creates and adds a new generic history element to this record.
     *
     * @return GenericHistoryType newly created generic history element ready for population
     */
    GenericHistoryType addNewGenericHistory();

    /**
     * Gets the infectious disease history and screening results including HIV, Hepatitis B/C,
     * syphilis, and other communicable diseases relevant to maternal-fetal health.
     *
     * @return InfectiousDiseaseType the infectious disease data, or null if not set
     */
    InfectiousDiseaseType getInfectiousDisease();

    /**
     * Sets the infectious disease information for this patient record.
     *
     * @param p0 InfectiousDiseaseType containing screening results and infectious disease history
     */
    void setInfectiousDisease(final InfectiousDiseaseType p0);

    /**
     * Creates and adds a new infectious disease element to this record.
     *
     * @return InfectiousDiseaseType newly created infectious disease element ready for population
     */
    InfectiousDiseaseType addNewInfectiousDisease();

    /**
     * Gets the psychosocial assessment including mental health status, social support,
     * substance use, domestic violence screening, and other social determinants of health.
     *
     * @return PsychosocialType the psychosocial assessment data, or null if not set
     */
    PsychosocialType getPsychosocial();

    /**
     * Sets the psychosocial assessment for this patient record.
     *
     * @param p0 PsychosocialType containing mental health, social support, and risk factor assessment
     */
    void setPsychosocial(final PsychosocialType p0);

    /**
     * Creates and adds a new psychosocial assessment element to this record.
     *
     * @return PsychosocialType newly created psychosocial element ready for population
     */
    PsychosocialType addNewPsychosocial();

    /**
     * Gets the family medical history including hereditary conditions, genetic disorders,
     * and familial disease patterns that may affect pregnancy outcomes.
     *
     * @return FamilyHistoryType the family history data, or null if not set
     */
    FamilyHistoryType getFamilyHistory();

    /**
     * Sets the family medical history for this patient record.
     *
     * @param p0 FamilyHistoryType containing hereditary conditions and family health patterns
     */
    void setFamilyHistory(final FamilyHistoryType p0);

    /**
     * Creates and adds a new family history element to this record.
     *
     * @return FamilyHistoryType newly created family history element ready for population
     */
    FamilyHistoryType addNewFamilyHistory();

    /**
     * Gets the physical examination findings including vital signs, anatomical assessments,
     * pelvic examination results, and clinical observations from prenatal visits.
     *
     * @return PhysicalExaminationType the physical examination data, or null if not set
     */
    PhysicalExaminationType getPhysicalExamination();

    /**
     * Sets the physical examination findings for this patient record.
     *
     * @param p0 PhysicalExaminationType containing clinical examination results and vital signs
     */
    void setPhysicalExamination(final PhysicalExaminationType p0);

    /**
     * Creates and adds a new physical examination element to this record.
     *
     * @return PhysicalExaminationType newly created physical examination element ready for population
     */
    PhysicalExaminationType addNewPhysicalExamination();

    /**
     * Factory class providing static methods for creating and parsing MedicalHistoryAndPhysicalExam
     * XML documents in compliance with BC Antenatal Record schema standards.
     *
     * Provides multiple parsing options for different input sources (String, File, URL, InputStream,
     * Reader, XMLStreamReader, DOM Node) to support flexible healthcare data integration scenarios.
     *
     * All parse methods validate against the AR2005 XML schema to ensure data integrity and
     * compliance with provincial healthcare data exchange standards.
     */
    public static final class Factory
    {
        /**
         * Creates a new empty instance of MedicalHistoryAndPhysicalExam with default options.
         *
         * @return MedicalHistoryAndPhysicalExam newly created instance ready for population
         */
        public static MedicalHistoryAndPhysicalExam newInstance() {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().newInstance(MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty instance of MedicalHistoryAndPhysicalExam with custom XML options.
         *
         * @param options XmlOptions for controlling XML generation behavior (character set, namespace handling, validation)
         * @return MedicalHistoryAndPhysicalExam newly created instance with specified options
         */
        public static MedicalHistoryAndPhysicalExam newInstance(final XmlOptions options) {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().newInstance(MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Parses XML string into MedicalHistoryAndPhysicalExam object with schema validation.
         *
         * @param xmlAsString String containing XML representation of medical history and physical exam data
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         */
        public static MedicalHistoryAndPhysicalExam parse(final String xmlAsString) throws XmlException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(xmlAsString, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Parses XML string into MedicalHistoryAndPhysicalExam object with custom options.
         *
         * @param xmlAsString String containing XML representation of medical history and physical exam data
         * @param options XmlOptions for controlling parsing behavior (error handling, validation level, namespace resolution)
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         */
        public static MedicalHistoryAndPhysicalExam parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(xmlAsString, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Parses XML file into MedicalHistoryAndPhysicalExam object with schema validation.
         *
         * @param file File containing XML document with antenatal record data
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws IOException if file cannot be read or accessed
         */
        public static MedicalHistoryAndPhysicalExam parse(final File file) throws XmlException, IOException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(file, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Parses XML file into MedicalHistoryAndPhysicalExam object with custom options.
         *
         * @param file File containing XML document with antenatal record data
         * @param options XmlOptions for controlling parsing behavior
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws IOException if file cannot be read or accessed
         */
        public static MedicalHistoryAndPhysicalExam parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(file, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Parses XML from URL into MedicalHistoryAndPhysicalExam object with schema validation.
         * Useful for fetching antenatal records from remote healthcare systems or web services.
         *
         * @param u URL pointing to XML document with medical history data
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws IOException if URL cannot be accessed or content cannot be read
         */
        public static MedicalHistoryAndPhysicalExam parse(final URL u) throws XmlException, IOException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(u, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Parses XML from URL into MedicalHistoryAndPhysicalExam object with custom options.
         *
         * @param u URL pointing to XML document with medical history data
         * @param options XmlOptions for controlling parsing behavior
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws IOException if URL cannot be accessed or content cannot be read
         */
        public static MedicalHistoryAndPhysicalExam parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(u, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Parses XML from InputStream into MedicalHistoryAndPhysicalExam object with schema validation.
         * Supports streaming processing of large healthcare documents.
         *
         * @param is InputStream containing XML data
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws IOException if stream cannot be read
         */
        public static MedicalHistoryAndPhysicalExam parse(final InputStream is) throws XmlException, IOException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(is, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Parses XML from InputStream into MedicalHistoryAndPhysicalExam object with custom options.
         *
         * @param is InputStream containing XML data
         * @param options XmlOptions for controlling parsing behavior
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws IOException if stream cannot be read
         */
        public static MedicalHistoryAndPhysicalExam parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(is, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Parses XML from Reader into MedicalHistoryAndPhysicalExam object with schema validation.
         * Supports character-based XML processing with encoding control.
         *
         * @param r Reader containing XML character data
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws IOException if reader cannot be read
         */
        public static MedicalHistoryAndPhysicalExam parse(final Reader r) throws XmlException, IOException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(r, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Parses XML from Reader into MedicalHistoryAndPhysicalExam object with custom options.
         *
         * @param r Reader containing XML character data
         * @param options XmlOptions for controlling parsing behavior
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws IOException if reader cannot be read
         */
        public static MedicalHistoryAndPhysicalExam parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(r, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Parses XML from XMLStreamReader into MedicalHistoryAndPhysicalExam object with schema validation.
         * Supports StAX-based XML processing for efficient memory usage with large documents.
         *
         * @param sr XMLStreamReader positioned at the start of medical history element
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         */
        public static MedicalHistoryAndPhysicalExam parse(final XMLStreamReader sr) throws XmlException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(sr, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Parses XML from XMLStreamReader into MedicalHistoryAndPhysicalExam object with custom options.
         *
         * @param sr XMLStreamReader positioned at the start of medical history element
         * @param options XmlOptions for controlling parsing behavior
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         */
        public static MedicalHistoryAndPhysicalExam parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(sr, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Parses XML from DOM Node into MedicalHistoryAndPhysicalExam object with schema validation.
         * Supports integration with DOM-based XML processing workflows.
         *
         * @param node Node DOM node containing medical history XML structure
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         */
        public static MedicalHistoryAndPhysicalExam parse(final Node node) throws XmlException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(node, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Parses XML from DOM Node into MedicalHistoryAndPhysicalExam object with custom options.
         *
         * @param node Node DOM node containing medical history XML structure
         * @param options XmlOptions for controlling parsing behavior
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         */
        public static MedicalHistoryAndPhysicalExam parse(final Node node, final XmlOptions options) throws XmlException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(node, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Parses XML from XMLInputStream into MedicalHistoryAndPhysicalExam object with schema validation.
         *
         * @deprecated XMLInputStream is deprecated in Apache XMLBeans; use XMLStreamReader-based methods instead
         * @param xis XMLInputStream containing XML data
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws XMLStreamException if stream processing error occurs
         */
        @Deprecated
        public static MedicalHistoryAndPhysicalExam parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(xis, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Parses XML from XMLInputStream into MedicalHistoryAndPhysicalExam object with custom options.
         *
         * @deprecated XMLInputStream is deprecated in Apache XMLBeans; use XMLStreamReader-based methods instead
         * @param xis XMLInputStream containing XML data
         * @param options XmlOptions for controlling parsing behavior
         * @return MedicalHistoryAndPhysicalExam parsed and validated object
         * @throws XmlException if XML is malformed or fails schema validation
         * @throws XMLStreamException if stream processing error occurs
         */
        @Deprecated
        public static MedicalHistoryAndPhysicalExam parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (MedicalHistoryAndPhysicalExam)XmlBeans.getContextTypeLoader().parse(xis, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Creates a validating XMLInputStream wrapper for schema validation.
         *
         * @deprecated XMLInputStream is deprecated in Apache XMLBeans; use XMLStreamReader-based validation instead
         * @param xis XMLInputStream to wrap with validation
         * @return XMLInputStream validating stream wrapper
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if stream processing error occurs
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, MedicalHistoryAndPhysicalExam.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream wrapper for schema validation with custom options.
         *
         * @deprecated XMLInputStream is deprecated in Apache XMLBeans; use XMLStreamReader-based validation instead
         * @param xis XMLInputStream to wrap with validation
         * @param options XmlOptions for controlling validation behavior
         * @return XMLInputStream validating stream wrapper
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if stream processing error occurs
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, MedicalHistoryAndPhysicalExam.type, options);
        }

        /**
         * Private constructor to prevent instantiation of this utility class.
         */
        private Factory() {
        }
    }
}
