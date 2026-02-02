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
import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * XML type interface for handling signature and date information in the British Columbia Antenatal Record (AR2005) forms.
 * This interface represents a signature complex type that captures healthcare provider signatures and their associated dates
 * for medical documentation and audit purposes.
 *
 * <p>The signature type supports both a primary signature/date pair and an optional secondary signature/date pair,
 * allowing for scenarios requiring multiple healthcare provider sign-offs such as witness signatures or
 * supervisory approvals.</p>
 *
 * <p>This class is generated from XML schema definitions and uses Apache XMLBeans for XML binding.
 * It provides both standard Java property accessors and XMLBeans-specific accessor methods (prefixed with 'x')
 * for advanced XML manipulation.</p>
 *
 * <p><b>Healthcare Context:</b> In British Columbia's antenatal care system, signatures serve as legal attestations
 * for clinical assessments, treatment plans, and patient consent. The dual signature capability supports
 * collaborative care models where multiple providers may need to authenticate the same document.</p>
 *
 * @see XmlObject
 * @see org.apache.xmlbeans.XmlString
 * @see org.apache.xmlbeans.XmlDate
 * @since 2026-01-24
 */
public interface SignatureType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(SignatureType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("signaturetypee7b7type");

    /**
     * Gets the primary signature value as a String.
     *
     * @return String the primary signature value
     */
    String getSignature();

    /**
     * Gets the primary signature as an XmlString object for advanced XML manipulation.
     *
     * @return XmlString the primary signature as an XMLBeans type
     */
    XmlString xgetSignature();

    /**
     * Sets the primary signature value.
     *
     * @param p0 String the signature value to set
     */
    void setSignature(final String p0);

    /**
     * Sets the primary signature using an XmlString object for advanced XML manipulation.
     *
     * @param p0 XmlString the signature value to set as an XMLBeans type
     */
    void xsetSignature(final XmlString p0);

    /**
     * Gets the primary signature date.
     *
     * @return Calendar the date associated with the primary signature
     */
    Calendar getDate();

    /**
     * Gets the primary signature date as an XmlDate object for advanced XML manipulation.
     *
     * @return XmlDate the date associated with the primary signature as an XMLBeans type
     */
    XmlDate xgetDate();

    /**
     * Sets the primary signature date.
     *
     * @param p0 Calendar the date to associate with the primary signature
     */
    void setDate(final Calendar p0);

    /**
     * Sets the primary signature date using an XmlDate object for advanced XML manipulation.
     *
     * @param p0 XmlDate the date to associate with the primary signature as an XMLBeans type
     */
    void xsetDate(final XmlDate p0);

    /**
     * Gets the secondary (witness or co-signer) signature value as a String.
     * This field is optional and may not be set for all signature instances.
     *
     * @return String the secondary signature value, or null if not set
     */
    String getSignature2();

    /**
     * Gets the secondary signature as an XmlString object for advanced XML manipulation.
     *
     * @return XmlString the secondary signature as an XMLBeans type
     */
    XmlString xgetSignature2();

    /**
     * Checks if the secondary signature field has been set.
     *
     * @return boolean true if the secondary signature is present, false otherwise
     */
    boolean isSetSignature2();

    /**
     * Sets the secondary signature value.
     *
     * @param p0 String the secondary signature value to set
     */
    void setSignature2(final String p0);

    /**
     * Sets the secondary signature using an XmlString object for advanced XML manipulation.
     *
     * @param p0 XmlString the secondary signature value to set as an XMLBeans type
     */
    void xsetSignature2(final XmlString p0);

    /**
     * Removes the secondary signature value, making it unset.
     */
    void unsetSignature2();

    /**
     * Gets the secondary signature date.
     * This field is optional and may not be set for all signature instances.
     *
     * @return Calendar the date associated with the secondary signature, or null if not set
     */
    Calendar getDate2();

    /**
     * Gets the secondary signature date as an XmlDate object for advanced XML manipulation.
     *
     * @return XmlDate the date associated with the secondary signature as an XMLBeans type
     */
    XmlDate xgetDate2();

    /**
     * Checks if the secondary signature date field has been set.
     *
     * @return boolean true if the secondary signature date is present, false otherwise
     */
    boolean isSetDate2();

    /**
     * Sets the secondary signature date.
     *
     * @param p0 Calendar the date to associate with the secondary signature
     */
    void setDate2(final Calendar p0);

    /**
     * Sets the secondary signature date using an XmlDate object for advanced XML manipulation.
     *
     * @param p0 XmlDate the date to associate with the secondary signature as an XMLBeans type
     */
    void xsetDate2(final XmlDate p0);

    /**
     * Removes the secondary signature date value, making it unset.
     */
    void unsetDate2();

    /**
     * Factory class for creating and parsing SignatureType instances from various XML sources.
     * Provides static methods for XML deserialization from strings, files, streams, and DOM nodes.
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new empty SignatureType instance with default settings.
         *
         * @return SignatureType a new instance with no signature or date values set
         */
        public static SignatureType newInstance() {
            return (SignatureType)XmlBeans.getContextTypeLoader().newInstance(SignatureType.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty SignatureType instance with specified XML options.
         *
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType a new instance with no signature or date values set
         */
        public static SignatureType newInstance(final XmlOptions options) {
            return (SignatureType)XmlBeans.getContextTypeLoader().newInstance(SignatureType.type, options);
        }

        /**
         * Parses a SignatureType instance from an XML string.
         *
         * @param xmlAsString String the XML string to parse
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         */
        public static SignatureType parse(final String xmlAsString) throws XmlException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(xmlAsString, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Parses a SignatureType instance from an XML string with specified options.
         *
         * @param xmlAsString String the XML string to parse
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         */
        public static SignatureType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(xmlAsString, SignatureType.type, options);
        }

        /**
         * Parses a SignatureType instance from an XML file.
         *
         * @param file File the XML file to parse
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static SignatureType parse(final File file) throws XmlException, IOException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(file, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Parses a SignatureType instance from an XML file with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static SignatureType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(file, SignatureType.type, options);
        }

        /**
         * Parses a SignatureType instance from a URL pointing to an XML resource.
         *
         * @param u URL the URL of the XML resource to parse
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws IOException if an I/O error occurs reading from the URL
         */
        public static SignatureType parse(final URL u) throws XmlException, IOException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(u, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Parses a SignatureType instance from a URL pointing to an XML resource with specified options.
         *
         * @param u URL the URL of the XML resource to parse
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws IOException if an I/O error occurs reading from the URL
         */
        public static SignatureType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(u, SignatureType.type, options);
        }

        /**
         * Parses a SignatureType instance from an InputStream containing XML data.
         *
         * @param is InputStream the input stream containing XML data
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static SignatureType parse(final InputStream is) throws XmlException, IOException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(is, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Parses a SignatureType instance from an InputStream containing XML data with specified options.
         *
         * @param is InputStream the input stream containing XML data
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static SignatureType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(is, SignatureType.type, options);
        }

        /**
         * Parses a SignatureType instance from a Reader containing XML character data.
         *
         * @param r Reader the reader containing XML character data
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static SignatureType parse(final Reader r) throws XmlException, IOException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(r, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Parses a SignatureType instance from a Reader containing XML character data with specified options.
         *
         * @param r Reader the reader containing XML character data
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static SignatureType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(r, SignatureType.type, options);
        }

        /**
         * Parses a SignatureType instance from an XMLStreamReader.
         *
         * @param sr XMLStreamReader the stream reader positioned at the SignatureType element
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         */
        public static SignatureType parse(final XMLStreamReader sr) throws XmlException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(sr, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Parses a SignatureType instance from an XMLStreamReader with specified options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the SignatureType element
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         */
        public static SignatureType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(sr, SignatureType.type, options);
        }

        /**
         * Parses a SignatureType instance from a DOM Node.
         *
         * @param node Node the DOM node representing the SignatureType element
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         */
        public static SignatureType parse(final Node node) throws XmlException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(node, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Parses a SignatureType instance from a DOM Node with specified options.
         *
         * @param node Node the DOM node representing the SignatureType element
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         */
        public static SignatureType parse(final Node node, final XmlOptions options) throws XmlException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(node, SignatureType.type, options);
        }

        /**
         * Parses a SignatureType instance from an XMLInputStream.
         *
         * @param xis XMLInputStream the XML input stream to parse
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws XMLStreamException if an error occurs processing the XML stream
         * @deprecated XMLInputStream is deprecated in XMLBeans; use alternative parse methods with standard Java streams
         */
        @Deprecated
        public static SignatureType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(xis, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Parses a SignatureType instance from an XMLInputStream with specified options.
         *
         * @param xis XMLInputStream the XML input stream to parse
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return SignatureType the parsed signature instance
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws XMLStreamException if an error occurs processing the XML stream
         * @deprecated XMLInputStream is deprecated in XMLBeans; use alternative parse methods with standard Java streams
         */
        @Deprecated
        public static SignatureType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (SignatureType)XmlBeans.getContextTypeLoader().parse(xis, SignatureType.type, options);
        }

        /**
         * Creates a validating XMLInputStream wrapper for signature validation.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws XMLStreamException if an error occurs processing the XML stream
         * @deprecated XMLInputStream is deprecated in XMLBeans; use alternative validation methods
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, SignatureType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream wrapper for signature validation with specified options.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @param options XmlOptions configuration options for XML parsing and validation
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if the XML is invalid or does not conform to the SignatureType schema
         * @throws XMLStreamException if an error occurs processing the XML stream
         * @deprecated XMLInputStream is deprecated in XMLBeans; use alternative validation methods
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, SignatureType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of this factory class.
         */
        private Factory() {
        }
    }
}
