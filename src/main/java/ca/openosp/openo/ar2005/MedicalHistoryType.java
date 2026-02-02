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
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * XML Type representation for Medical History data structure in the AR2005 (British Columbia Antenatal Record) system.
 *
 * This interface represents a patient's comprehensive medical history including various medical conditions,
 * surgical history, and treatment information. It is part of the BC Antenatal Record form system used for
 * pregnancy care documentation in British Columbia healthcare facilities.
 *
 * The medical history captures yes/no/null responses for common medical conditions including:
 * <ul>
 *   <li>Cardiovascular conditions (hypertension, cardiac)</li>
 *   <li>Metabolic and endocrine disorders</li>
 *   <li>Urinary tract conditions</li>
 *   <li>Liver and hematological conditions</li>
 *   <li>Gynecological history</li>
 *   <li>Surgical history and anesthetics exposure</li>
 *   <li>Psychiatric and neurological conditions</li>
 *   <li>Blood transfusion history</li>
 * </ul>
 *
 * This is a generated XML Beans interface that provides strongly-typed access to XML data conforming
 * to the AR2005 schema. The interface extends {@link XmlObject} to provide XML serialization and
 * deserialization capabilities.
 *
 * @see YesNoNullType
 * @see XmlObject
 * @since 2026-01-23
 */
public interface MedicalHistoryType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(MedicalHistoryType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("medicalhistorytype49f4type");

    /**
     * Gets the hypertension status from the medical history.
     *
     * @return YesNoNullType the hypertension status (yes/no/null)
     */
    YesNoNullType getHypertension();

    /**
     * Sets the hypertension status in the medical history.
     *
     * @param p0 YesNoNullType the hypertension status to set (yes/no/null)
     */
    void setHypertension(final YesNoNullType p0);

    /**
     * Adds a new hypertension element to the medical history.
     *
     * @return YesNoNullType the newly created hypertension status object
     */
    YesNoNullType addNewHypertension();

    /**
     * Gets the endocrine condition status from the medical history.
     *
     * @return YesNoNullType the endocrine condition status (yes/no/null)
     */
    YesNoNullType getEndorince();

    /**
     * Sets the endocrine condition status in the medical history.
     *
     * @param p0 YesNoNullType the endocrine condition status to set (yes/no/null)
     */
    void setEndorince(final YesNoNullType p0);

    /**
     * Adds a new endocrine condition element to the medical history.
     *
     * @return YesNoNullType the newly created endocrine condition status object
     */
    YesNoNullType addNewEndorince();

    /**
     * Gets the urinary tract condition status from the medical history.
     *
     * @return YesNoNullType the urinary tract condition status (yes/no/null)
     */
    YesNoNullType getUrinaryTract();

    /**
     * Sets the urinary tract condition status in the medical history.
     *
     * @param p0 YesNoNullType the urinary tract condition status to set (yes/no/null)
     */
    void setUrinaryTract(final YesNoNullType p0);

    /**
     * Adds a new urinary tract condition element to the medical history.
     *
     * @return YesNoNullType the newly created urinary tract condition status object
     */
    YesNoNullType addNewUrinaryTract();

    /**
     * Gets the cardiac condition status from the medical history.
     *
     * @return YesNoNullType the cardiac condition status (yes/no/null)
     */
    YesNoNullType getCardiac();

    /**
     * Sets the cardiac condition status in the medical history.
     *
     * @param p0 YesNoNullType the cardiac condition status to set (yes/no/null)
     */
    void setCardiac(final YesNoNullType p0);

    /**
     * Adds a new cardiac condition element to the medical history.
     *
     * @return YesNoNullType the newly created cardiac condition status object
     */
    YesNoNullType addNewCardiac();

    /**
     * Gets the liver condition status from the medical history.
     *
     * @return YesNoNullType the liver condition status (yes/no/null)
     */
    YesNoNullType getLiver();

    /**
     * Sets the liver condition status in the medical history.
     *
     * @param p0 YesNoNullType the liver condition status to set (yes/no/null)
     */
    void setLiver(final YesNoNullType p0);

    /**
     * Adds a new liver condition element to the medical history.
     *
     * @return YesNoNullType the newly created liver condition status object
     */
    YesNoNullType addNewLiver();

    /**
     * Gets the gynecological condition status from the medical history.
     *
     * @return YesNoNullType the gynecological condition status (yes/no/null)
     */
    YesNoNullType getGynaecology();

    /**
     * Sets the gynecological condition status in the medical history.
     *
     * @param p0 YesNoNullType the gynecological condition status to set (yes/no/null)
     */
    void setGynaecology(final YesNoNullType p0);

    /**
     * Adds a new gynecological condition element to the medical history.
     *
     * @return YesNoNullType the newly created gynecological condition status object
     */
    YesNoNullType addNewGynaecology();

    /**
     * Gets the hematological (blood disorder) condition status from the medical history.
     *
     * @return YesNoNullType the hematological condition status (yes/no/null)
     */
    YesNoNullType getHem();

    /**
     * Sets the hematological condition status in the medical history.
     *
     * @param p0 YesNoNullType the hematological condition status to set (yes/no/null)
     */
    void setHem(final YesNoNullType p0);

    /**
     * Adds a new hematological condition element to the medical history.
     *
     * @return YesNoNullType the newly created hematological condition status object
     */
    YesNoNullType addNewHem();

    /**
     * Gets the surgical history status from the medical history.
     *
     * @return YesNoNullType the surgical history status indicating if patient has had surgeries (yes/no/null)
     */
    YesNoNullType getSurgeries();

    /**
     * Sets the surgical history status in the medical history.
     *
     * @param p0 YesNoNullType the surgical history status to set (yes/no/null)
     */
    void setSurgeries(final YesNoNullType p0);

    /**
     * Adds a new surgical history element to the medical history.
     *
     * @return YesNoNullType the newly created surgical history status object
     */
    YesNoNullType addNewSurgeries();

    /**
     * Gets the blood transfusion history status from the medical history.
     *
     * @return YesNoNullType the blood transfusion history status (yes/no/null)
     */
    YesNoNullType getBloodTransfusion();

    /**
     * Sets the blood transfusion history status in the medical history.
     *
     * @param p0 YesNoNullType the blood transfusion history status to set (yes/no/null)
     */
    void setBloodTransfusion(final YesNoNullType p0);

    /**
     * Adds a new blood transfusion history element to the medical history.
     *
     * @return YesNoNullType the newly created blood transfusion history status object
     */
    YesNoNullType addNewBloodTransfusion();

    /**
     * Gets the anesthetics exposure history status from the medical history.
     *
     * @return YesNoNullType the anesthetics exposure history status (yes/no/null)
     */
    YesNoNullType getAnesthetics();

    /**
     * Sets the anesthetics exposure history status in the medical history.
     *
     * @param p0 YesNoNullType the anesthetics exposure history status to set (yes/no/null)
     */
    void setAnesthetics(final YesNoNullType p0);

    /**
     * Adds a new anesthetics exposure history element to the medical history.
     *
     * @return YesNoNullType the newly created anesthetics exposure history status object
     */
    YesNoNullType addNewAnesthetics();

    /**
     * Gets the psychiatric condition history status from the medical history.
     *
     * @return YesNoNullType the psychiatric condition history status (yes/no/null)
     */
    YesNoNullType getPsychiatry();

    /**
     * Sets the psychiatric condition history status in the medical history.
     *
     * @param p0 YesNoNullType the psychiatric condition history status to set (yes/no/null)
     */
    void setPsychiatry(final YesNoNullType p0);

    /**
     * Adds a new psychiatric condition history element to the medical history.
     *
     * @return YesNoNullType the newly created psychiatric condition history status object
     */
    YesNoNullType addNewPsychiatry();

    /**
     * Gets the epilepsy condition status from the medical history.
     *
     * @return YesNoNullType the epilepsy condition status (yes/no/null)
     */
    YesNoNullType getEpilepsy();

    /**
     * Sets the epilepsy condition status in the medical history.
     *
     * @param p0 YesNoNullType the epilepsy condition status to set (yes/no/null)
     */
    void setEpilepsy(final YesNoNullType p0);

    /**
     * Adds a new epilepsy condition element to the medical history.
     *
     * @return YesNoNullType the newly created epilepsy condition status object
     */
    YesNoNullType addNewEpilepsy();

    /**
     * Gets the description text for other medical conditions not covered by specific categories.
     *
     * @return String the free-text description of other medical conditions
     */
    String getOtherDescr();

    /**
     * Gets the description text for other medical conditions as an XmlString object.
     *
     * @return XmlString the XML representation of the other medical conditions description
     */
    XmlString xgetOtherDescr();

    /**
     * Sets the description text for other medical conditions.
     *
     * @param p0 String the free-text description to set for other medical conditions
     */
    void setOtherDescr(final String p0);

    /**
     * Sets the description text for other medical conditions using an XmlString object.
     *
     * @param p0 XmlString the XML representation of the description to set
     */
    void xsetOtherDescr(final XmlString p0);

    /**
     * Gets the status indicating presence of other medical conditions not categorized above.
     *
     * @return YesNoNullType the status indicating if other medical conditions exist (yes/no/null)
     */
    YesNoNullType getOther();

    /**
     * Sets the status indicating presence of other medical conditions.
     *
     * @param p0 YesNoNullType the status to set for other medical conditions (yes/no/null)
     */
    void setOther(final YesNoNullType p0);

    /**
     * Adds a new element for other medical conditions to the medical history.
     *
     * @return YesNoNullType the newly created other medical conditions status object
     */
    YesNoNullType addNewOther();

    /**
     * Factory class for creating and parsing MedicalHistoryType instances.
     *
     * This inner class provides static factory methods for:
     * <ul>
     *   <li>Creating new MedicalHistoryType instances</li>
     *   <li>Parsing MedicalHistoryType from various input sources (String, File, URL, Stream, etc.)</li>
     *   <li>XML validation operations</li>
     * </ul>
     *
     * The Factory uses Apache XMLBeans context type loader to handle XML serialization
     * and deserialization operations.
     */
    public static final class Factory
    {
        /**
         * Creates a new MedicalHistoryType instance with default options.
         *
         * @return MedicalHistoryType a new instance of MedicalHistoryType
         */
        public static MedicalHistoryType newInstance() {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().newInstance(MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Creates a new MedicalHistoryType instance with specified XML options.
         *
         * @param options XmlOptions the XML parsing/generation options to use
         * @return MedicalHistoryType a new instance of MedicalHistoryType
         */
        public static MedicalHistoryType newInstance(final XmlOptions options) {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().newInstance(MedicalHistoryType.type, options);
        }

        /**
         * Parses a MedicalHistoryType from an XML string with default options.
         *
         * @param xmlAsString String the XML content as a string
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static MedicalHistoryType parse(final String xmlAsString) throws XmlException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(xmlAsString, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Parses a MedicalHistoryType from an XML string with specified options.
         *
         * @param xmlAsString String the XML content as a string
         * @param options XmlOptions the XML parsing options to use
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static MedicalHistoryType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(xmlAsString, MedicalHistoryType.type, options);
        }

        /**
         * Parses a MedicalHistoryType from an XML file with default options.
         *
         * @param file File the file containing XML content
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the file cannot be read
         */
        public static MedicalHistoryType parse(final File file) throws XmlException, IOException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(file, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Parses a MedicalHistoryType from an XML file with specified options.
         *
         * @param file File the file containing XML content
         * @param options XmlOptions the XML parsing options to use
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the file cannot be read
         */
        public static MedicalHistoryType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(file, MedicalHistoryType.type, options);
        }

        /**
         * Parses a MedicalHistoryType from a URL with default options.
         *
         * @param u URL the URL pointing to XML content
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the URL cannot be accessed
         */
        public static MedicalHistoryType parse(final URL u) throws XmlException, IOException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(u, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Parses a MedicalHistoryType from a URL with specified options.
         *
         * @param u URL the URL pointing to XML content
         * @param options XmlOptions the XML parsing options to use
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the URL cannot be accessed
         */
        public static MedicalHistoryType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(u, MedicalHistoryType.type, options);
        }

        /**
         * Parses a MedicalHistoryType from an input stream with default options.
         *
         * @param is InputStream the input stream containing XML content
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the stream cannot be read
         */
        public static MedicalHistoryType parse(final InputStream is) throws XmlException, IOException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(is, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Parses a MedicalHistoryType from an input stream with specified options.
         *
         * @param is InputStream the input stream containing XML content
         * @param options XmlOptions the XML parsing options to use
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the stream cannot be read
         */
        public static MedicalHistoryType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(is, MedicalHistoryType.type, options);
        }

        /**
         * Parses a MedicalHistoryType from a Reader with default options.
         *
         * @param r Reader the reader providing XML content
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the reader cannot be accessed
         */
        public static MedicalHistoryType parse(final Reader r) throws XmlException, IOException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(r, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Parses a MedicalHistoryType from a Reader with specified options.
         *
         * @param r Reader the reader providing XML content
         * @param options XmlOptions the XML parsing options to use
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the reader cannot be accessed
         */
        public static MedicalHistoryType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(r, MedicalHistoryType.type, options);
        }

        /**
         * Parses a MedicalHistoryType from an XMLStreamReader with default options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the XML content
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static MedicalHistoryType parse(final XMLStreamReader sr) throws XmlException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(sr, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Parses a MedicalHistoryType from an XMLStreamReader with specified options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the XML content
         * @param options XmlOptions the XML parsing options to use
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static MedicalHistoryType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(sr, MedicalHistoryType.type, options);
        }

        /**
         * Parses a MedicalHistoryType from a DOM Node with default options.
         *
         * @param node Node the DOM node containing XML content
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static MedicalHistoryType parse(final Node node) throws XmlException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(node, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Parses a MedicalHistoryType from a DOM Node with specified options.
         *
         * @param node Node the DOM node containing XML content
         * @param options XmlOptions the XML parsing options to use
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static MedicalHistoryType parse(final Node node, final XmlOptions options) throws XmlException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(node, MedicalHistoryType.type, options);
        }

        /**
         * Parses a MedicalHistoryType from an XMLInputStream with default options.
         *
         * @param xis XMLInputStream the XML input stream
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error processing the stream
         * @deprecated XMLInputStream is deprecated in favor of XMLStreamReader
         */
        @Deprecated
        public static MedicalHistoryType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(xis, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Parses a MedicalHistoryType from an XMLInputStream with specified options.
         *
         * @param xis XMLInputStream the XML input stream
         * @param options XmlOptions the XML parsing options to use
         * @return MedicalHistoryType the parsed MedicalHistoryType instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error processing the stream
         * @deprecated XMLInputStream is deprecated in favor of XMLStreamReader
         */
        @Deprecated
        public static MedicalHistoryType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (MedicalHistoryType)XmlBeans.getContextTypeLoader().parse(xis, MedicalHistoryType.type, options);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with default options.
         *
         * @param xis XMLInputStream the source XML input stream
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if there is an error processing the stream
         * @deprecated XMLInputStream is deprecated in favor of XMLStreamReader
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, MedicalHistoryType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with specified options.
         *
         * @param xis XMLInputStream the source XML input stream
         * @param options XmlOptions the XML validation options to use
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if there is an error processing the stream
         * @deprecated XMLInputStream is deprecated in favor of XMLStreamReader
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, MedicalHistoryType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of this factory class.
         */
        private Factory() {
        }
    }
}
