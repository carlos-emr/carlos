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
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlString;

/**
 * Ethnic value type enumeration for British Columbia Antenatal Record (BCAR) AR2005 forms.
 *
 * <p>This interface represents standardized ethnic background codes used in prenatal and maternal healthcare
 * documentation within the OpenO EMR system. It provides a controlled vocabulary for recording patient
 * ethnicity information in accordance with BC healthcare reporting requirements.</p>
 *
 * <p>The ethnic value codes defined in this type include:</p>
 * <ul>
 *   <li>ANC001 - Ethnic background category 1</li>
 *   <li>ANC002 - Ethnic background category 2</li>
 *   <li>ANC005 - Ethnic background category 5</li>
 *   <li>ANC007 - Ethnic background category 7</li>
 *   <li>OTHER - Other ethnic backgrounds not covered by predefined categories</li>
 *   <li>UN - Unknown or not disclosed</li>
 * </ul>
 *
 * <p>This type is generated from XML schema definitions and implements the Apache XMLBeans framework
 * for XML serialization and deserialization of antenatal record data.</p>
 *
 * @see XmlString
 * @see StringEnumAbstractBase
 * @since 2026-01-24
 */
public interface EthnicValueType extends XmlString
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(EthnicValueType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("ethnicvaluetype7021type");
    public static final Enum ANC_001 = Enum.forString("ANC001");
    public static final Enum ANC_002 = Enum.forString("ANC002");
    public static final Enum ANC_005 = Enum.forString("ANC005");
    public static final Enum ANC_007 = Enum.forString("ANC007");
    public static final Enum OTHER = Enum.forString("OTHER");
    public static final Enum UN = Enum.forString("UN");
    public static final int INT_ANC_001 = 1;
    public static final int INT_ANC_002 = 2;
    public static final int INT_ANC_005 = 3;
    public static final int INT_ANC_007 = 4;
    public static final int INT_OTHER = 5;
    public static final int INT_UN = 6;

    /**
     * Retrieves the current enumeration value as a StringEnumAbstractBase object.
     *
     * @return StringEnumAbstractBase the current ethnic value enumeration
     */
    StringEnumAbstractBase enumValue();

    /**
     * Sets the ethnic value to the specified enumeration.
     *
     * @param p0 StringEnumAbstractBase the ethnic value enumeration to set
     */
    void set(final StringEnumAbstractBase p0);

    /**
     * Enumeration class for ethnic value types in the BCAR AR2005 system.
     *
     * <p>This class provides type-safe enumeration of ethnic background codes used in
     * British Columbia antenatal records. It extends {@link StringEnumAbstractBase} to
     * provide XML serialization support and bidirectional mapping between string codes
     * and integer values.</p>
     *
     * <p>The enumeration supports conversion operations between:</p>
     * <ul>
     *   <li>String codes (e.g., "ANC001") and Enum instances</li>
     *   <li>Integer values (e.g., 1) and Enum instances</li>
     * </ul>
     *
     * @see StringEnumAbstractBase
     * @since 2026-01-24
     */
    public static final class Enum extends StringEnumAbstractBase
    {
        static final int INT_ANC_001 = 1;
        static final int INT_ANC_002 = 2;
        static final int INT_ANC_005 = 3;
        static final int INT_ANC_007 = 4;
        static final int INT_OTHER = 5;
        static final int INT_UN = 6;
        public static final StringEnumAbstractBase.Table table;
        private static final long serialVersionUID = 1L;

        /**
         * Converts a string code to its corresponding Enum instance.
         *
         * @param s String the ethnic value code (e.g., "ANC001", "ANC002", "OTHER", "UN")
         * @return Enum the corresponding enumeration instance, or null if the string is not recognized
         */
        public static Enum forString(final String s) {
            return (Enum)Enum.table.forString(s);
        }

        /**
         * Converts an integer value to its corresponding Enum instance.
         *
         * @param i int the integer representation of the ethnic value (1-6)
         * @return Enum the corresponding enumeration instance, or null if the integer is out of range
         */
        public static Enum forInt(final int i) {
            return (Enum)Enum.table.forInt(i);
        }

        /**
         * Private constructor for creating Enum instances.
         *
         * @param s String the string code for this ethnic value
         * @param i int the integer value for this ethnic value
         */
        private Enum(final String s, final int i) {
            super(s, i);
        }

        /**
         * Resolves the deserialized object to the canonical Enum instance.
         *
         * <p>This method ensures that deserialized Enum instances are resolved to the
         * singleton instances maintained by the enumeration table, preserving object
         * identity and enabling safe use of reference equality (==) comparisons.</p>
         *
         * @return Object the canonical Enum instance corresponding to this object's integer value
         */
        private Object readResolve() {
            return forInt(this.intValue());
        }
        
        static {
            table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("ANC001", 1), new Enum("ANC002", 2), new Enum("ANC005", 3), new Enum("ANC007", 4), new Enum("OTHER", 5), new Enum("UN", 6) });
        }
    }

    /**
     * Factory class for creating and parsing EthnicValueType instances.
     *
     * <p>This class provides comprehensive factory methods for creating EthnicValueType objects
     * and parsing ethnic value data from various sources including XML strings, files, URLs,
     * streams, and DOM nodes. It leverages the Apache XMLBeans framework for XML processing
     * and schema validation.</p>
     *
     * <p>The factory supports multiple input formats:</p>
     * <ul>
     *   <li>String-based XML content</li>
     *   <li>File-based XML documents</li>
     *   <li>URL-based remote XML resources</li>
     *   <li>InputStream and Reader-based data sources</li>
     *   <li>XMLStreamReader for streaming XML parsing</li>
     *   <li>DOM Node for in-memory XML manipulation</li>
     *   <li>XMLInputStream (deprecated legacy format)</li>
     * </ul>
     *
     * <p>All parsing methods include overloaded versions accepting {@link XmlOptions} for
     * customizing parsing behavior such as validation, error handling, and namespace processing.</p>
     *
     * @see XmlBeans
     * @see XmlOptions
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new EthnicValueType instance from an existing object.
         *
         * @param obj Object the source object to create the ethnic value from
         * @return EthnicValueType a new instance initialized with the provided object's value
         */
        public static EthnicValueType newValue(final Object obj) {
            return (EthnicValueType)EthnicValueType.type.newValue(obj);
        }

        /**
         * Creates a new empty EthnicValueType instance with default configuration.
         *
         * @return EthnicValueType a new uninitialized instance
         */
        public static EthnicValueType newInstance() {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().newInstance(EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty EthnicValueType instance with custom XML processing options.
         *
         * @param options XmlOptions the configuration options for XML processing
         * @return EthnicValueType a new uninitialized instance
         */
        public static EthnicValueType newInstance(final XmlOptions options) {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().newInstance(EthnicValueType.type, options);
        }

        /**
         * Parses an XML string to create an EthnicValueType instance.
         *
         * @param xmlAsString String the XML content as a string
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static EthnicValueType parse(final String xmlAsString) throws XmlException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(xmlAsString, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string to create an EthnicValueType instance with custom options.
         *
         * @param xmlAsString String the XML content as a string
         * @param options XmlOptions the configuration options for parsing
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static EthnicValueType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(xmlAsString, EthnicValueType.type, options);
        }

        /**
         * Parses an XML file to create an EthnicValueType instance.
         *
         * @param file File the file containing XML content
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static EthnicValueType parse(final File file) throws XmlException, IOException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(file, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file to create an EthnicValueType instance with custom options.
         *
         * @param file File the file containing XML content
         * @param options XmlOptions the configuration options for parsing
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static EthnicValueType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(file, EthnicValueType.type, options);
        }

        /**
         * Parses XML from a URL to create an EthnicValueType instance.
         *
         * @param u URL the URL pointing to XML content
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs retrieving the URL content
         */
        public static EthnicValueType parse(final URL u) throws XmlException, IOException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(u, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a URL to create an EthnicValueType instance with custom options.
         *
         * @param u URL the URL pointing to XML content
         * @param options XmlOptions the configuration options for parsing
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs retrieving the URL content
         */
        public static EthnicValueType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(u, EthnicValueType.type, options);
        }

        /**
         * Parses XML from an input stream to create an EthnicValueType instance.
         *
         * @param is InputStream the input stream containing XML content
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static EthnicValueType parse(final InputStream is) throws XmlException, IOException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(is, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an input stream to create an EthnicValueType instance with custom options.
         *
         * @param is InputStream the input stream containing XML content
         * @param options XmlOptions the configuration options for parsing
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static EthnicValueType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(is, EthnicValueType.type, options);
        }

        /**
         * Parses XML from a character reader to create an EthnicValueType instance.
         *
         * @param r Reader the character reader containing XML content
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static EthnicValueType parse(final Reader r) throws XmlException, IOException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(r, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a character reader to create an EthnicValueType instance with custom options.
         *
         * @param r Reader the character reader containing XML content
         * @param options XmlOptions the configuration options for parsing
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static EthnicValueType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(r, EthnicValueType.type, options);
        }

        /**
         * Parses XML from a streaming XML reader to create an EthnicValueType instance.
         *
         * @param sr XMLStreamReader the streaming XML reader positioned at the element to parse
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static EthnicValueType parse(final XMLStreamReader sr) throws XmlException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(sr, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a streaming XML reader to create an EthnicValueType instance with custom options.
         *
         * @param sr XMLStreamReader the streaming XML reader positioned at the element to parse
         * @param options XmlOptions the configuration options for parsing
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static EthnicValueType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(sr, EthnicValueType.type, options);
        }

        /**
         * Parses XML from a DOM node to create an EthnicValueType instance.
         *
         * @param node Node the DOM node containing XML content
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static EthnicValueType parse(final Node node) throws XmlException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(node, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a DOM node to create an EthnicValueType instance with custom options.
         *
         * @param node Node the DOM node containing XML content
         * @param options XmlOptions the configuration options for parsing
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static EthnicValueType parse(final Node node, final XmlOptions options) throws XmlException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(node, EthnicValueType.type, options);
        }

        /**
         * Parses XML from an XMLInputStream to create an EthnicValueType instance.
         *
         * @param xis XMLInputStream the XML input stream (legacy format)
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws XMLStreamException if an error occurs during stream processing
         * @deprecated XMLInputStream is deprecated. Use {@link XMLStreamReader} instead.
         */
        @Deprecated
        public static EthnicValueType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(xis, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLInputStream to create an EthnicValueType instance with custom options.
         *
         * @param xis XMLInputStream the XML input stream (legacy format)
         * @param options XmlOptions the configuration options for parsing
         * @return EthnicValueType the parsed ethnic value instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws XMLStreamException if an error occurs during stream processing
         * @deprecated XMLInputStream is deprecated. Use {@link XMLStreamReader} instead.
         */
        @Deprecated
        public static EthnicValueType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (EthnicValueType)XmlBeans.getContextTypeLoader().parse(xis, EthnicValueType.type, options);
        }

        /**
         * Creates a validating XMLInputStream for schema validation.
         *
         * @param xis XMLInputStream the source XML input stream to validate
         * @return XMLInputStream a validating stream wrapper
         * @throws XmlException if the stream cannot be validated
         * @throws XMLStreamException if an error occurs during stream processing
         * @deprecated XMLInputStream is deprecated. Use {@link XMLStreamReader} for validation instead.
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, EthnicValueType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream for schema validation with custom options.
         *
         * @param xis XMLInputStream the source XML input stream to validate
         * @param options XmlOptions the configuration options for validation
         * @return XMLInputStream a validating stream wrapper
         * @throws XmlException if the stream cannot be validated
         * @throws XMLStreamException if an error occurs during stream processing
         * @deprecated XMLInputStream is deprecated. Use {@link XMLStreamReader} for validation instead.
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, EthnicValueType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of this utility class.
         *
         * <p>The Factory class provides only static methods and should not be instantiated.</p>
         */
        private Factory() {
        }
    }
}
