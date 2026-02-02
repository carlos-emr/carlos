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
 * Represents prenatal genetic screening information for the British Columbia Antenatal Record (BCAR) 2005 form.
 * <p>
 * This interface provides access to prenatal genetic screening test results and patient consent information
 * collected during pregnancy care. It includes data for various screening tests used to assess risk of
 * chromosomal abnormalities and neural tube defects in the developing fetus.
 * </p>
 * <p>
 * Screening tests tracked include:
 * <ul>
 *   <li>MS/SIPS/FTS - Maternal Serum/Integrated Prenatal Screening/First Trimester Screening</li>
 *   <li>EDB CVS - Estimated Due By Chorionic Villus Sampling</li>
 *   <li>MSAFP - Maternal Serum Alpha-Fetoprotein (for neural tube defects)</li>
 *   <li>Custom laboratory tests</li>
 * </ul>
 * </p>
 * <p>
 * This interface is part of the AR2005 package which implements data structures for the British Columbia
 * Antenatal Record form used in prenatal care documentation. It follows the XMLBeans framework for
 * XML data binding and serialization.
 * </p>
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
     * Gets the MS/SIPS/FTS (Maternal Serum/Integrated Prenatal Screening/First Trimester Screening) test result.
     * <p>
     * This field stores results from prenatal screening tests used to assess the risk of chromosomal
     * abnormalities such as Down syndrome (Trisomy 21) and Edwards syndrome (Trisomy 18).
     * </p>
     *
     * @return String the MS/SIPS/FTS test result value
     */
    String getMSSIPSFTS();

    /**
     * Gets the MS/SIPS/FTS test result as an XmlString object.
     * <p>
     * This method provides access to the underlying XMLBeans string object, allowing for
     * validation and XML-specific operations.
     * </p>
     *
     * @return XmlString the MS/SIPS/FTS test result as an XMLBeans string object
     */
    XmlString xgetMSSIPSFTS();

    /**
     * Sets the MS/SIPS/FTS (Maternal Serum/Integrated Prenatal Screening/First Trimester Screening) test result.
     * <p>
     * This method updates the prenatal screening test result value. The value should represent
     * the screening outcome or risk assessment.
     * </p>
     *
     * @param p0 String the MS/SIPS/FTS test result value to set
     */
    void setMSSIPSFTS(final String p0);

    /**
     * Sets the MS/SIPS/FTS test result using an XmlString object.
     * <p>
     * This method allows setting the value using an XMLBeans string object for XML-specific operations.
     * </p>
     *
     * @param p0 XmlString the MS/SIPS/FTS test result as an XMLBeans string object
     */
    void xsetMSSIPSFTS(final XmlString p0);

    /**
     * Gets the EDB CVS (Estimated Due By Chorionic Villus Sampling) date or result.
     * <p>
     * Chorionic villus sampling is a prenatal diagnostic procedure performed between 10-13 weeks
     * of pregnancy to detect chromosomal abnormalities and genetic disorders by sampling placental tissue.
     * This field may contain the estimated due date based on CVS results or related diagnostic information.
     * </p>
     *
     * @return String the EDB CVS value
     */
    String getEDBCVS();

    /**
     * Gets the EDB CVS value as an XmlString object.
     * <p>
     * This method provides access to the underlying XMLBeans string object for XML-specific operations.
     * </p>
     *
     * @return XmlString the EDB CVS value as an XMLBeans string object
     */
    XmlString xgetEDBCVS();

    /**
     * Sets the EDB CVS (Estimated Due By Chorionic Villus Sampling) date or result.
     * <p>
     * This method updates the CVS-related information, which may include the estimated due date
     * or diagnostic findings from chorionic villus sampling.
     * </p>
     *
     * @param p0 String the EDB CVS value to set
     */
    void setEDBCVS(final String p0);

    /**
     * Sets the EDB CVS value using an XmlString object.
     * <p>
     * This method allows setting the value using an XMLBeans string object for XML-specific operations.
     * </p>
     *
     * @param p0 XmlString the EDB CVS value as an XMLBeans string object
     */
    void xsetEDBCVS(final XmlString p0);

    /**
     * Gets the MSAFP (Maternal Serum Alpha-Fetoprotein) test result.
     * <p>
     * The MSAFP test is a prenatal screening blood test that measures the level of alpha-fetoprotein
     * in the mother's blood. It is used to screen for neural tube defects (such as spina bifida),
     * abdominal wall defects, and chromosomal abnormalities. This test is typically performed
     * between 15-20 weeks of pregnancy.
     * </p>
     *
     * @return String the MSAFP test result value
     */
    String getMSAFP();

    /**
     * Gets the MSAFP test result as an XmlString object.
     * <p>
     * This method provides access to the underlying XMLBeans string object for XML-specific operations.
     * </p>
     *
     * @return XmlString the MSAFP test result as an XMLBeans string object
     */
    XmlString xgetMSAFP();

    /**
     * Sets the MSAFP (Maternal Serum Alpha-Fetoprotein) test result.
     * <p>
     * This method updates the MSAFP test result value, which should represent the screening
     * outcome or measured AFP level.
     * </p>
     *
     * @param p0 String the MSAFP test result value to set
     */
    void setMSAFP(final String p0);

    /**
     * Sets the MSAFP test result using an XmlString object.
     * <p>
     * This method allows setting the value using an XMLBeans string object for XML-specific operations.
     * </p>
     *
     * @param p0 XmlString the MSAFP test result as an XMLBeans string object
     */
    void xsetMSAFP(final XmlString p0);

    /**
     * Gets the first custom laboratory test entry.
     * <p>
     * This method retrieves a custom laboratory test result that is not part of the standard
     * prenatal genetic screening panel. Custom labs allow for documenting additional screening
     * or diagnostic tests ordered by the healthcare provider.
     * </p>
     *
     * @return CustomLab the first custom laboratory test entry
     */
    CustomLab getCustomLab1();

    /**
     * Sets the first custom laboratory test entry.
     * <p>
     * This method updates the custom laboratory test information with a new or modified entry.
     * </p>
     *
     * @param p0 CustomLab the custom laboratory test entry to set
     */
    void setCustomLab1(final CustomLab p0);

    /**
     * Creates and adds a new first custom laboratory test entry.
     * <p>
     * This method instantiates a new CustomLab object, adds it to the screening record,
     * and returns it for further configuration. This is useful for building the XML structure
     * programmatically.
     * </p>
     *
     * @return CustomLab a newly created custom laboratory test entry
     */
    CustomLab addNewCustomLab1();

    /**
     * Gets the declined status indicating whether the patient declined genetic screening.
     * <p>
     * This field documents patient consent decisions regarding prenatal genetic screening.
     * When true, it indicates the patient has declined one or more genetic screening tests
     * after being offered and counseled about the options. This is an important part of
     * informed consent documentation in prenatal care.
     * </p>
     *
     * @return boolean true if genetic screening was declined by the patient, false otherwise
     */
    boolean getDeclined();

    /**
     * Gets the declined status as an XmlBoolean object.
     * <p>
     * This method provides access to the underlying XMLBeans boolean object for XML-specific operations.
     * </p>
     *
     * @return XmlBoolean the declined status as an XMLBeans boolean object
     */
    XmlBoolean xgetDeclined();

    /**
     * Sets the declined status indicating whether the patient declined genetic screening.
     * <p>
     * This method updates the patient's consent decision regarding prenatal genetic screening.
     * Setting this to true documents that the patient has declined testing after appropriate
     * counseling.
     * </p>
     *
     * @param p0 boolean true to indicate screening was declined, false otherwise
     */
    void setDeclined(final boolean p0);

    /**
     * Sets the declined status using an XmlBoolean object.
     * <p>
     * This method allows setting the value using an XMLBeans boolean object for XML-specific operations.
     * </p>
     *
     * @param p0 XmlBoolean the declined status as an XMLBeans boolean object
     */
    void xsetDeclined(final XmlBoolean p0);

    /**
     * Factory class for creating and parsing PrenatalGeneticScreeningType instances.
     * <p>
     * This inner class provides static factory methods following the XMLBeans pattern for
     * creating new instances and parsing XML data into PrenatalGeneticScreeningType objects.
     * It supports parsing from various sources including strings, files, URLs, streams, and DOM nodes.
     * </p>
     */
    public static final class Factory
    {
        /**
         * Creates a new empty PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method instantiates a new object that can be populated with prenatal genetic
         * screening data programmatically.
         * </p>
         *
         * @return PrenatalGeneticScreeningType a new empty instance
         */
        public static PrenatalGeneticScreeningType newInstance() {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().newInstance(PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control parsing behavior, validation,
         * and other XML processing settings.
         * </p>
         *
         * @param options XmlOptions the XML processing options to use
         * @return PrenatalGeneticScreeningType a new empty instance configured with the specified options
         */
        public static PrenatalGeneticScreeningType newInstance(final XmlOptions options) {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().newInstance(PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses an XML string into a PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method deserializes XML content from a string representation into a strongly-typed
         * PrenatalGeneticScreeningType object.
         * </p>
         *
         * @param xmlAsString String the XML content to parse
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         */
        public static PrenatalGeneticScreeningType parse(final String xmlAsString) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(xmlAsString, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string into a PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control validation, error handling,
         * and other parsing behaviors.
         * </p>
         *
         * @param xmlAsString String the XML content to parse
         * @param options XmlOptions the XML processing options to use during parsing
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         */
        public static PrenatalGeneticScreeningType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(xmlAsString, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses an XML file into a PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method reads and deserializes XML content from a file into a strongly-typed object.
         * </p>
         *
         * @param file File the XML file to parse
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws IOException if an I/O error occurs while reading the file
         */
        public static PrenatalGeneticScreeningType parse(final File file) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(file, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file into a PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control validation, error handling,
         * and other parsing behaviors when reading from a file.
         * </p>
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML processing options to use during parsing
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws IOException if an I/O error occurs while reading the file
         */
        public static PrenatalGeneticScreeningType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(file, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses XML content from a URL into a PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method retrieves and deserializes XML content from a URL resource into a strongly-typed object.
         * </p>
         *
         * @param u URL the URL pointing to the XML content to parse
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws IOException if an I/O error occurs while reading from the URL
         */
        public static PrenatalGeneticScreeningType parse(final URL u) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(u, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses XML content from a URL into a PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control validation, error handling,
         * and other parsing behaviors when reading from a URL.
         * </p>
         *
         * @param u URL the URL pointing to the XML content to parse
         * @param options XmlOptions the XML processing options to use during parsing
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws IOException if an I/O error occurs while reading from the URL
         */
        public static PrenatalGeneticScreeningType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(u, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses XML content from an InputStream into a PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method deserializes XML content from an input stream into a strongly-typed object.
         * </p>
         *
         * @param is InputStream the input stream containing XML content to parse
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws IOException if an I/O error occurs while reading from the stream
         */
        public static PrenatalGeneticScreeningType parse(final InputStream is) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(is, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses XML content from an InputStream into a PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control validation, error handling,
         * and other parsing behaviors when reading from an input stream.
         * </p>
         *
         * @param is InputStream the input stream containing XML content to parse
         * @param options XmlOptions the XML processing options to use during parsing
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws IOException if an I/O error occurs while reading from the stream
         */
        public static PrenatalGeneticScreeningType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(is, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses XML content from a Reader into a PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method deserializes XML content from a character stream reader into a strongly-typed object.
         * </p>
         *
         * @param r Reader the reader containing XML content to parse
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws IOException if an I/O error occurs while reading from the reader
         */
        public static PrenatalGeneticScreeningType parse(final Reader r) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(r, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses XML content from a Reader into a PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control validation, error handling,
         * and other parsing behaviors when reading from a character stream reader.
         * </p>
         *
         * @param r Reader the reader containing XML content to parse
         * @param options XmlOptions the XML processing options to use during parsing
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws IOException if an I/O error occurs while reading from the reader
         */
        public static PrenatalGeneticScreeningType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(r, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses XML content from an XMLStreamReader into a PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method deserializes XML content from a StAX stream reader into a strongly-typed object,
         * providing efficient streaming access to XML data.
         * </p>
         *
         * @param sr XMLStreamReader the XML stream reader positioned at the element to parse
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         */
        public static PrenatalGeneticScreeningType parse(final XMLStreamReader sr) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(sr, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses XML content from an XMLStreamReader into a PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control validation, error handling,
         * and other parsing behaviors when reading from a StAX stream reader.
         * </p>
         *
         * @param sr XMLStreamReader the XML stream reader positioned at the element to parse
         * @param options XmlOptions the XML processing options to use during parsing
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         */
        public static PrenatalGeneticScreeningType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(sr, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses XML content from a DOM Node into a PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method deserializes XML content from a DOM (Document Object Model) node into a
         * strongly-typed object, enabling integration with DOM-based XML processing.
         * </p>
         *
         * @param node Node the DOM node containing the XML element to parse
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         */
        public static PrenatalGeneticScreeningType parse(final Node node) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(node, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses XML content from a DOM Node into a PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control validation, error handling,
         * and other parsing behaviors when reading from a DOM node.
         * </p>
         *
         * @param node Node the DOM node containing the XML element to parse
         * @param options XmlOptions the XML processing options to use during parsing
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         */
        public static PrenatalGeneticScreeningType parse(final Node node, final XmlOptions options) throws XmlException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(node, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Parses XML content from an XMLInputStream into a PrenatalGeneticScreeningType instance with default options.
         * <p>
         * This method deserializes XML content from an XMLBeans-specific input stream.
         * </p>
         *
         * @param xis XMLInputStream the XML input stream to parse
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws XMLStreamException if an error occurs during XML stream processing
         * @deprecated XMLInputStream is deprecated in XMLBeans; use alternative parsing methods
         */
        @Deprecated
        public static PrenatalGeneticScreeningType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(xis, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Parses XML content from an XMLInputStream into a PrenatalGeneticScreeningType instance with custom XML options.
         * <p>
         * This method allows specifying XMLBeans options to control validation, error handling,
         * and other parsing behaviors when reading from an XMLBeans-specific input stream.
         * </p>
         *
         * @param xis XMLInputStream the XML input stream to parse
         * @param options XmlOptions the XML processing options to use during parsing
         * @return PrenatalGeneticScreeningType the parsed instance
         * @throws XmlException if the XML is malformed or does not match the expected schema
         * @throws XMLStreamException if an error occurs during XML stream processing
         * @deprecated XMLInputStream is deprecated in XMLBeans; use alternative parsing methods
         */
        @Deprecated
        public static PrenatalGeneticScreeningType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (PrenatalGeneticScreeningType)XmlBeans.getContextTypeLoader().parse(xis, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Creates a validating XMLInputStream wrapper with default options.
         * <p>
         * This method wraps an XMLInputStream with schema validation capabilities to ensure
         * the XML content conforms to the PrenatalGeneticScreeningType schema.
         * </p>
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if validation setup fails or the XML is invalid
         * @throws XMLStreamException if an error occurs during XML stream processing
         * @deprecated XMLInputStream is deprecated in XMLBeans; use alternative parsing methods with validation options
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PrenatalGeneticScreeningType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream wrapper with custom XML options.
         * <p>
         * This method wraps an XMLInputStream with schema validation capabilities, allowing custom
         * validation and error handling options to be specified.
         * </p>
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation
         * @param options XmlOptions the XML processing options to control validation behavior
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if validation setup fails or the XML is invalid
         * @throws XMLStreamException if an error occurs during XML stream processing
         * @deprecated XMLInputStream is deprecated in XMLBeans; use alternative parsing methods with validation options
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PrenatalGeneticScreeningType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of the Factory class.
         * <p>
         * This class provides only static methods and should not be instantiated.
         * </p>
         */
        private Factory() {
        }
    }
}
