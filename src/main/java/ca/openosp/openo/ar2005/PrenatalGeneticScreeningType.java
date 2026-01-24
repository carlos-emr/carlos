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
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Represents prenatal genetic screening data for the British Columbia Antenatal Record (BCAR) 2005 form.
 *
 * This interface provides access to various prenatal genetic screening test results and configurations
 * used in prenatal care to assess risk of genetic abnormalities and birth defects. The screening
 * includes multiple types of tests performed at different stages of pregnancy:
 *
 * <ul>
 * <li>MS/SIPS/FTS - Maternal Serum Screening, Serum Integrated Prenatal Screening, or First Trimester Screening</li>
 * <li>EDB CVS - Estimated Date of Birth Chorionic Villus Sampling</li>
 * <li>MSAFP - Maternal Serum Alpha-Fetoprotein screening for neural tube defects</li>
 * <li>Custom Lab - Additional laboratory investigations specific to the pregnancy</li>
 * <li>Declined - Patient opt-out status for genetic screening</li>
 * </ul>
 *
 * This type is generated from XML schema definitions and uses Apache XMLBeans for XML binding.
 * It supports serialization and deserialization of prenatal genetic screening data in compliance
 * with British Columbia healthcare standards for antenatal records.
 *
 * @see CustomLab
 * @see CurrentPregnancyType
 * @see InitialLaboratoryInvestigations
 * @see ARRecord
 * @since 2026-01-24
 */
public interface PrenatalGeneticScreeningType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(PrenatalGeneticScreeningType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("prenatalgeneticscreeningtype87f7type");

    /**
     * Gets the MS/SIPS/FTS screening test result or status.
     *
     * MS/SIPS/FTS represents Maternal Serum Screening, Serum Integrated Prenatal Screening,
     * or First Trimester Screening used to assess risk of chromosomal abnormalities and
     * neural tube defects during pregnancy.
     *
     * @return String the screening test result or status
     */
    String getMSSIPSFTS();

    /**
     * Gets the MS/SIPS/FTS screening test result as an XmlString object.
     *
     * This method provides access to the underlying XML representation of the screening
     * test data, allowing for advanced XML manipulation and validation.
     *
     * @return XmlString the XML representation of the screening test result
     */
    XmlString xgetMSSIPSFTS();

    /**
     * Sets the MS/SIPS/FTS screening test result or status.
     *
     * @param p0 String the screening test result or status to set
     */
    void setMSSIPSFTS(final String p0);

    /**
     * Sets the MS/SIPS/FTS screening test result using an XmlString object.
     *
     * This method allows setting the value using the underlying XML representation
     * for advanced XML manipulation scenarios.
     *
     * @param p0 XmlString the XML representation of the screening test result
     */
    void xsetMSSIPSFTS(final XmlString p0);

    /**
     * Gets the EDB CVS (Estimated Date of Birth Chorionic Villus Sampling) result or status.
     *
     * Chorionic Villus Sampling is an invasive prenatal test performed typically between
     * 10-13 weeks of pregnancy to diagnose chromosomal abnormalities and genetic disorders.
     *
     * @return String the CVS test result or estimated date
     */
    String getEDBCVS();

    /**
     * Gets the EDB CVS result as an XmlString object.
     *
     * This method provides access to the underlying XML representation of the CVS test data.
     *
     * @return XmlString the XML representation of the CVS test result
     */
    XmlString xgetEDBCVS();

    /**
     * Sets the EDB CVS (Estimated Date of Birth Chorionic Villus Sampling) result or status.
     *
     * @param p0 String the CVS test result or estimated date to set
     */
    void setEDBCVS(final String p0);

    /**
     * Sets the EDB CVS result using an XmlString object.
     *
     * This method allows setting the value using the underlying XML representation.
     *
     * @param p0 XmlString the XML representation of the CVS test result
     */
    void xsetEDBCVS(final XmlString p0);

    /**
     * Gets the MSAFP (Maternal Serum Alpha-Fetoprotein) screening test result.
     *
     * MSAFP is a blood test typically performed between 15-20 weeks of pregnancy to screen
     * for neural tube defects (such as spina bifida) and abdominal wall defects. Abnormal
     * levels may indicate increased risk of birth defects or chromosomal abnormalities.
     *
     * @return String the MSAFP test result or status
     */
    String getMSAFP();

    /**
     * Gets the MSAFP screening test result as an XmlString object.
     *
     * This method provides access to the underlying XML representation of the MSAFP test data.
     *
     * @return XmlString the XML representation of the MSAFP test result
     */
    XmlString xgetMSAFP();

    /**
     * Sets the MSAFP (Maternal Serum Alpha-Fetoprotein) screening test result.
     *
     * @param p0 String the MSAFP test result or status to set
     */
    void setMSAFP(final String p0);

    /**
     * Sets the MSAFP screening test result using an XmlString object.
     *
     * This method allows setting the value using the underlying XML representation.
     *
     * @param p0 XmlString the XML representation of the MSAFP test result
     */
    void xsetMSAFP(final XmlString p0);

    /**
     * Gets the first custom laboratory investigation for this prenatal genetic screening.
     *
     * Custom labs allow for additional screening tests or investigations beyond the
     * standard MS/SIPS/FTS, CVS, and MSAFP tests, tailored to specific patient needs
     * or clinical indications.
     *
     * @return CustomLab the custom laboratory investigation object
     */
    CustomLab getCustomLab1();

    /**
     * Sets the first custom laboratory investigation for this prenatal genetic screening.
     *
     * @param p0 CustomLab the custom laboratory investigation object to set
     */
    void setCustomLab1(final CustomLab p0);

    /**
     * Adds and returns a new custom laboratory investigation object.
     *
     * This method creates a new CustomLab instance and associates it with this
     * prenatal genetic screening record, allowing for additional test data to be recorded.
     *
     * @return CustomLab the newly created custom laboratory investigation object
     */
    CustomLab addNewCustomLab1();

    /**
     * Gets the declined status indicating whether the patient declined genetic screening.
     *
     * Patients have the right to decline prenatal genetic screening. This field
     * documents the patient's decision for informed consent and medical record purposes.
     *
     * @return boolean true if the patient declined screening, false otherwise
     */
    boolean getDeclined();

    /**
     * Gets the declined status as an XmlBoolean object.
     *
     * This method provides access to the underlying XML representation of the declined status.
     *
     * @return XmlBoolean the XML representation of the declined status
     */
    XmlBoolean xgetDeclined();

    /**
     * Sets the declined status indicating whether the patient declined genetic screening.
     *
     * @param p0 boolean true if the patient declined screening, false otherwise
     */
    void setDeclined(final boolean p0);

    /**
     * Sets the declined status using an XmlBoolean object.
     *
     * This method allows setting the value using the underlying XML representation.
     *
     * @param p0 XmlBoolean the XML representation of the declined status
     */
    void xsetDeclined(final XmlBoolean p0);

    /**
     * Factory class for creating and parsing PrenatalGeneticScreeningType instances.
     *
     * This factory provides methods to create new instances of PrenatalGeneticScreeningType
     * and parse existing XML data from various sources (strings, files, streams, etc.) into
     * PrenatalGeneticScreeningType objects. It uses Apache XMLBeans for XML binding and parsing.
     */
    public static final class Factory
    {
        /**
         * Creates a new instance of PrenatalGeneticScreeningType with default options.
         *
         * @return PrenatalGeneticScreeningType a new instance
         */
        public static PrenatalGeneticScreeningType newInstance() {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().newInstance(PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Creates a new instance of PrenatalGeneticScreeningType with specified XML options.
         *
         * @param options XmlOptions the XML parsing and creation options to use
         * @return PrenatalGeneticScreeningType a new instance configured with the specified options
         */
        public static PrenatalGeneticScreeningType newInstance(final XmlOptions options) {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().newInstance(PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from an XML string.
         *
         * @param xmlAsString String the XML string to parse
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PrenatalGeneticScreeningType parse(final String xmlAsString) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(xmlAsString, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from an XML string with specified options.
         *
         * @param xmlAsString String the XML string to parse
         * @param options XmlOptions the XML parsing options to use
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PrenatalGeneticScreeningType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(xmlAsString, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from an XML file.
         *
         * @param file File the XML file to parse
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the file cannot be read
         */
        public static PrenatalGeneticScreeningType parse(final File file) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(file, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from an XML file with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML parsing options to use
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the file cannot be read
         */
        public static PrenatalGeneticScreeningType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(file, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from a URL pointing to XML content.
         *
         * @param u URL the URL to the XML content to parse
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the URL cannot be accessed or read
         */
        public static PrenatalGeneticScreeningType parse(final URL u) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(u, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from a URL pointing to XML content with specified options.
         *
         * @param u URL the URL to the XML content to parse
         * @param options XmlOptions the XML parsing options to use
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the URL cannot be accessed or read
         */
        public static PrenatalGeneticScreeningType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(u, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from an InputStream containing XML content.
         *
         * @param is InputStream the input stream containing XML content to parse
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the stream cannot be read
         */
        public static PrenatalGeneticScreeningType parse(final InputStream is) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(is, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from an InputStream containing XML content with specified options.
         *
         * @param is InputStream the input stream containing XML content to parse
         * @param options XmlOptions the XML parsing options to use
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the stream cannot be read
         */
        public static PrenatalGeneticScreeningType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(is, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from a Reader containing XML content.
         *
         * @param r Reader the reader containing XML content to parse
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the reader cannot be read
         */
        public static PrenatalGeneticScreeningType parse(final Reader r) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(r, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from a Reader containing XML content with specified options.
         *
         * @param r Reader the reader containing XML content to parse
         * @param options XmlOptions the XML parsing options to use
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the reader cannot be read
         */
        public static PrenatalGeneticScreeningType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(r, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from an XMLStreamReader.
         *
         * @param sr XMLStreamReader the XML stream reader positioned at the element to parse
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PrenatalGeneticScreeningType parse(final XMLStreamReader sr) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(sr, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from an XMLStreamReader with specified options.
         *
         * @param sr XMLStreamReader the XML stream reader positioned at the element to parse
         * @param options XmlOptions the XML parsing options to use
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PrenatalGeneticScreeningType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(sr, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from a DOM Node.
         *
         * @param node Node the DOM node to parse
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PrenatalGeneticScreeningType parse(final Node node) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(node, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from a DOM Node with specified options.
         *
         * @param node Node the DOM node to parse
         * @param options XmlOptions the XML parsing options to use
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PrenatalGeneticScreeningType parse(final Node node, final XmlOptions options) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(node, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from a deprecated XMLInputStream.
         *
         * @param xis XMLInputStream the XML input stream to parse
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error in the XML stream
         * @deprecated XMLInputStream is deprecated. Use {@link #parse(InputStream)} or {@link #parse(XMLStreamReader)} instead.
         */
        @Deprecated
        public static PrenatalGeneticScreeningType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(xis, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses a PrenatalGeneticScreeningType from a deprecated XMLInputStream with specified options.
         *
         * @param xis XMLInputStream the XML input stream to parse
         * @param options XmlOptions the XML parsing options to use
         * @return PrenatalGeneticScreeningType the parsed object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error in the XML stream
         * @deprecated XMLInputStream is deprecated. Use {@link #parse(InputStream, XmlOptions)} or {@link #parse(XMLStreamReader, XmlOptions)} instead.
         */
        @Deprecated
        public static PrenatalGeneticScreeningType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(xis, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Creates a validating XMLInputStream from a deprecated XMLInputStream.
         *
         * This method wraps the input stream with validation against the PrenatalGeneticScreeningType schema.
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if the validation setup fails
         * @throws XMLStreamException if there is an error in the XML stream
         * @deprecated XMLInputStream is deprecated. Use standard XML validation techniques with {@link #parse(InputStream)} instead.
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream from a deprecated XMLInputStream with specified options.
         *
         * This method wraps the input stream with validation against the PrenatalGeneticScreeningType schema
         * using the provided XML options for validation configuration.
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation
         * @param options XmlOptions the XML validation options to use
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if the validation setup fails
         * @throws XMLStreamException if there is an error in the XML stream
         * @deprecated XMLInputStream is deprecated. Use standard XML validation techniques with {@link #parse(InputStream, XmlOptions)} instead.
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of the Factory class.
         *
         * The Factory class provides only static methods for creating and parsing
         * PrenatalGeneticScreeningType instances and should not be instantiated.
         */
        private Factory() {
        }
    }
}
