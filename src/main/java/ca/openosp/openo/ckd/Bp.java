package ca.openosp.openo.ckd;

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
 * Blood Pressure configuration interface for Chronic Kidney Disease (CKD) management.
 *
 * This XMLBeans-generated interface represents the blood pressure configuration component
 * within the CKD configuration system. It provides methods to manage systolic and diastolic
 * blood pressure target values used in CKD patient monitoring and treatment protocols.
 *
 * Blood pressure control is critical in CKD management as hypertension can accelerate kidney
 * function decline. This interface enables configuration of BP targets that are used by the
 * system to monitor patient compliance and treatment effectiveness.
 *
 * @since 2010-01-01
 * @see CKDConfig
 * @see ca.openosp.openo.ckd.impl.BpImpl
 */
public interface Bp extends XmlObject
{
    /**
     * SchemaType system definition for XMLBeans type mapping.
     * This constant defines the schema type used by XMLBeans for serialization
     * and deserialization of blood pressure configuration data.
     */
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Bp.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("bp4e0btype");

    /**
     * Gets the systolic blood pressure target value.
     *
     * Systolic pressure represents the pressure in arteries when the heart beats.
     * For CKD patients, typical target is &lt;130 mmHg, though this may vary based on
     * individual patient factors and comorbidities.
     *
     * @return String representation of the systolic BP target in mmHg, or null if not set
     */
    String getSystolic();

    /**
     * Gets the systolic blood pressure as an XML string object.
     *
     * This method provides access to the underlying XMLBeans XmlString representation,
     * allowing for more advanced XML manipulation if needed. This is primarily used
     * internally by the XMLBeans framework.
     *
     * @return XmlString containing the systolic BP value with full XML type information
     */
    XmlString xgetSystolic();

    /**
     * Sets the systolic blood pressure target value.
     *
     * Updates the systolic BP target for CKD patient monitoring. The value should
     * be a numeric string representing mmHg. This setting influences alerts and
     * treatment recommendations in the CKD management workflow.
     *
     * @param p0 String representation of systolic BP in mmHg (e.g., "130")
     */
    void setSystolic(final String p0);

    /**
     * Sets the systolic blood pressure using an XML string object.
     *
     * This method allows setting the systolic value using an XmlString object,
     * providing type-safe XML manipulation. Primarily used by XMLBeans framework
     * for deserialization operations.
     *
     * @param p0 XmlString containing the systolic BP value with XML type information
     */
    void xsetSystolic(final XmlString p0);

    /**
     * Gets the diastolic blood pressure target value.
     *
     * Diastolic pressure represents the pressure in arteries between heartbeats.
     * For CKD patients, typical target is &lt;80 mmHg. Proper diastolic control is
     * essential for preventing further kidney damage and cardiovascular complications.
     *
     * @return String representation of the diastolic BP target in mmHg, or null if not set
     */
    String getDiastolic();

    /**
     * Gets the diastolic blood pressure as an XML string object.
     *
     * Provides access to the underlying XMLBeans XmlString representation for
     * advanced XML operations. This method is primarily used internally by the
     * XMLBeans serialization framework.
     *
     * @return XmlString containing the diastolic BP value with full XML type information
     */
    XmlString xgetDiastolic();

    /**
     * Sets the diastolic blood pressure target value.
     *
     * Updates the diastolic BP target for CKD patient monitoring. The value should
     * be a numeric string representing mmHg. This configuration affects clinical
     * decision support and patient care recommendations.
     *
     * @param p0 String representation of diastolic BP in mmHg (e.g., "80")
     */
    void setDiastolic(final String p0);

    /**
     * Sets the diastolic blood pressure using an XML string object.
     *
     * Allows setting the diastolic value using an XmlString object for type-safe
     * XML manipulation. This method is primarily used by the XMLBeans framework
     * during deserialization processes.
     *
     * @param p0 XmlString containing the diastolic BP value with XML type information
     */
    void xsetDiastolic(final XmlString p0);

    /**
     * Factory class for creating and parsing Bp instances.
     *
     * This nested factory class provides static methods for creating new Bp instances
     * and parsing BP configuration from various sources (files, streams, XML strings).
     * It follows the XMLBeans factory pattern, enabling flexible instantiation and
     * deserialization of blood pressure configuration data.
     *
     * The factory supports multiple input formats and provides both simple and
     * configurable parsing options through XmlOptions parameters.
     */
    public static final class Factory
    {
        /**
         * Creates a new empty Bp instance.
         *
         * This method creates a new blood pressure configuration object with no initial
         * values set. The returned instance can be populated programmatically before
         * being serialized to XML or used in the CKD configuration system.
         *
         * @return Bp a new empty blood pressure configuration instance
         */
        public static Bp newInstance() {
            return (Bp)XmlBeans.getContextTypeLoader().newInstance(Bp.type, (XmlOptions)null);
        }

        /**
         * Creates a new Bp instance with specified XML options.
         *
         * Creates a new blood pressure configuration with custom XML processing options.
         * Options can control validation, namespace handling, and other XML behaviors.
         *
         * @param options XmlOptions to configure XML processing behavior
         * @return Bp a new blood pressure configuration instance
         */
        public static Bp newInstance(final XmlOptions options) {
            return (Bp)XmlBeans.getContextTypeLoader().newInstance(Bp.type, options);
        }

        /**
         * Parses a blood pressure configuration from an XML string.
         *
         * Deserializes BP configuration from an XML string representation. The string
         * must contain valid XML conforming to the BP schema definition.
         *
         * @param xmlAsString String containing XML representation of BP configuration
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         */
        public static Bp parse(final String xmlAsString) throws XmlException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(xmlAsString, Bp.type, (XmlOptions)null);
        }

        /**
         * Parses a blood pressure configuration from an XML string with options.
         *
         * Deserializes BP configuration with custom XML processing options for
         * validation, error handling, and namespace processing.
         *
         * @param xmlAsString String containing XML representation of BP configuration
         * @param options XmlOptions to control parsing behavior
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         */
        public static Bp parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(xmlAsString, Bp.type, options);
        }

        /**
         * Parses a blood pressure configuration from an XML file.
         *
         * Reads and deserializes BP configuration from a file on the filesystem.
         * Useful for loading configuration from external configuration files.
         *
         * @param file File containing XML BP configuration
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws IOException if there are file reading errors
         */
        public static Bp parse(final File file) throws XmlException, IOException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(file, Bp.type, (XmlOptions)null);
        }

        /**
         * Parses a blood pressure configuration from an XML file with options.
         *
         * Reads and deserializes BP configuration with custom XML processing options.
         *
         * @param file File containing XML BP configuration
         * @param options XmlOptions to control parsing behavior
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws IOException if there are file reading errors
         */
        public static Bp parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(file, Bp.type, options);
        }

        /**
         * Parses a blood pressure configuration from a URL.
         *
         * Fetches and deserializes BP configuration from a remote URL. Useful for
         * loading configuration from web services or remote configuration servers.
         *
         * @param u URL pointing to XML BP configuration
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws IOException if there are network or reading errors
         */
        public static Bp parse(final URL u) throws XmlException, IOException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(u, Bp.type, (XmlOptions)null);
        }

        /**
         * Parses a blood pressure configuration from a URL with options.
         *
         * Fetches and deserializes BP configuration with custom XML processing options.
         *
         * @param u URL pointing to XML BP configuration
         * @param options XmlOptions to control parsing behavior
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws IOException if there are network or reading errors
         */
        public static Bp parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(u, Bp.type, options);
        }

        /**
         * Parses a blood pressure configuration from an input stream.
         *
         * Deserializes BP configuration from any input stream source. Commonly used
         * when reading from database BLOBs or processing uploaded configuration files.
         *
         * @param is InputStream containing XML BP configuration
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws IOException if there are stream reading errors
         */
        public static Bp parse(final InputStream is) throws XmlException, IOException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(is, Bp.type, (XmlOptions)null);
        }

        /**
         * Parses a blood pressure configuration from an input stream with options.
         *
         * Deserializes BP configuration with custom XML processing options.
         *
         * @param is InputStream containing XML BP configuration
         * @param options XmlOptions to control parsing behavior
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws IOException if there are stream reading errors
         */
        public static Bp parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(is, Bp.type, options);
        }

        /**
         * Parses a blood pressure configuration from a Reader.
         *
         * Deserializes BP configuration from a character stream. Useful when dealing
         * with character encodings or when the XML source is text-based.
         *
         * @param r Reader providing XML BP configuration
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws IOException if there are reading errors
         */
        public static Bp parse(final Reader r) throws XmlException, IOException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(r, Bp.type, (XmlOptions)null);
        }

        /**
         * Parses a blood pressure configuration from a Reader with options.
         *
         * Deserializes BP configuration with custom XML processing options.
         *
         * @param r Reader providing XML BP configuration
         * @param options XmlOptions to control parsing behavior
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws IOException if there are reading errors
         */
        public static Bp parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(r, Bp.type, options);
        }

        /**
         * Parses a blood pressure configuration from an XML stream reader.
         *
         * Deserializes BP configuration using StAX (Streaming API for XML) for
         * efficient, pull-based XML parsing. Ideal for large XML documents.
         *
         * @param sr XMLStreamReader positioned at the BP element
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         */
        public static Bp parse(final XMLStreamReader sr) throws XmlException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(sr, Bp.type, (XmlOptions)null);
        }

        /**
         * Parses a blood pressure configuration from an XML stream reader with options.
         *
         * Deserializes BP configuration using StAX with custom XML processing options.
         *
         * @param sr XMLStreamReader positioned at the BP element
         * @param options XmlOptions to control parsing behavior
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         */
        public static Bp parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(sr, Bp.type, options);
        }

        /**
         * Parses a blood pressure configuration from a DOM Node.
         *
         * Deserializes BP configuration from a W3C DOM Node. Useful when integrating
         * with systems that use DOM-based XML processing.
         *
         * @param node DOM Node containing BP configuration XML
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         */
        public static Bp parse(final Node node) throws XmlException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(node, Bp.type, (XmlOptions)null);
        }

        /**
         * Parses a blood pressure configuration from a DOM Node with options.
         *
         * Deserializes BP configuration from a DOM Node with custom XML processing options.
         *
         * @param node DOM Node containing BP configuration XML
         * @param options XmlOptions to control parsing behavior
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         */
        public static Bp parse(final Node node, final XmlOptions options) throws XmlException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(node, Bp.type, options);
        }

        /**
         * Parses a blood pressure configuration from an XMLInputStream.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader instead
         *             for StAX-based parsing or other parse methods for different sources.
         *
         * @param xis XMLInputStream containing BP configuration
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws XMLStreamException if there are stream processing errors
         */
        @Deprecated
        public static Bp parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(xis, Bp.type, (XmlOptions)null);
        }

        /**
         * Parses a blood pressure configuration from an XMLInputStream with options.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader instead
         *             for StAX-based parsing or other parse methods for different sources.
         *
         * @param xis XMLInputStream containing BP configuration
         * @param options XmlOptions to control parsing behavior
         * @return Bp parsed blood pressure configuration object
         * @throws XmlException if the XML is malformed or doesn't conform to schema
         * @throws XMLStreamException if there are stream processing errors
         */
        @Deprecated
        public static Bp parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (Bp)XmlBeans.getContextTypeLoader().parse(xis, Bp.type, options);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader with
         *             validation options instead for schema validation during parsing.
         *
         * @param xis XMLInputStream to wrap with validation
         * @return XMLInputStream validating XMLInputStream that validates against BP schema
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if there are stream processing errors
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Bp.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream with custom options.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader with
         *             validation options instead for schema validation during parsing.
         *
         * @param xis XMLInputStream to wrap with validation
         * @param options XmlOptions to control validation behavior
         * @return XMLInputStream validating XMLInputStream that validates against BP schema
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if there are stream processing errors
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Bp.type, options);
        }

        /**
         * Private constructor to prevent instantiation.
         *
         * The Factory class is designed as a utility class with only static methods.
         * This private constructor ensures that the Factory cannot be instantiated,
         * enforcing the static factory pattern.
         */
        private Factory() {
        }
    }
}
