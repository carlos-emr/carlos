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
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * XML Type interface for recommended immunoprophylaxis data in antenatal records.
 *
 * <p>This interface represents the recommended immunoprophylaxis section of the
 * AR2005 (Antenatal Record 2005) form used in Canadian healthcare. It manages
 * critical maternal immunization and prophylaxis data including:</p>
 * <ul>
 *   <li>Rh-negative status and Rh immunoglobulin administration</li>
 *   <li>Rubella vaccination status</li>
 *   <li>Newborn Hepatitis B immunoglobulin requirements</li>
 *   <li>Hepatitis B vaccine recommendations</li>
 * </ul>
 *
 * <p>This is an Apache XMLBeans-generated interface that provides type-safe access
 * to XML data conforming to the AR2005 schema. It includes both standard getters/setters
 * and XMLBeans-specific methods (prefixed with 'x') for direct XML manipulation.</p>
 *
 * <p><strong>Healthcare Context:</strong> Immunoprophylaxis during pregnancy is critical
 * for preventing maternal-fetal disease transmission. This data tracks recommended
 * interventions based on maternal serology and risk factors, ensuring appropriate
 * prophylaxis for conditions like Rh incompatibility and Hepatitis B.</p>
 *
 * @since 2026-01-24
 * @see org.apache.xmlbeans.XmlObject
 * @see ca.openosp.openo.ar2005
 */
public interface RecommendedImmunoprophylaxisType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(RecommendedImmunoprophylaxisType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("recommendedimmunoprophylaxistype0e82type");

    /**
     * Gets the Rh-negative status of the patient.
     *
     * <p>Rh-negative status is critical for pregnancy management. Rh-negative mothers
     * carrying Rh-positive fetuses require Rh immunoglobulin prophylaxis to prevent
     * hemolytic disease of the newborn in subsequent pregnancies.</p>
     *
     * @return boolean true if the patient is Rh-negative, false otherwise
     */
    boolean getRhNegative();

    /**
     * Gets the Rh-negative status as an XMLBeans XmlBoolean object.
     *
     * <p>This method provides direct access to the underlying XML representation
     * of the Rh-negative status, allowing for XML-specific operations such as
     * validation and schema compliance checking.</p>
     *
     * @return XmlBoolean the Rh-negative status as an XML boolean type
     */
    XmlBoolean xgetRhNegative();

    /**
     * Sets the Rh-negative status of the patient.
     *
     * @param p0 boolean true if the patient is Rh-negative, false otherwise
     */
    void setRhNegative(final boolean p0);

    /**
     * Sets the Rh-negative status using an XMLBeans XmlBoolean object.
     *
     * <p>This method allows direct XML manipulation of the Rh-negative status field,
     * useful when working with XML documents or performing schema validation.</p>
     *
     * @param p0 XmlBoolean the Rh-negative status as an XML boolean type
     */
    void xsetRhNegative(final XmlBoolean p0);

    /**
     * Gets the date when Rh immunoglobulin (RhIg) was administered.
     *
     * <p>RhIg is administered to Rh-negative mothers at approximately 28 weeks gestation
     * and within 72 hours postpartum to prevent Rh sensitization. This field tracks
     * when the prophylaxis was given.</p>
     *
     * @return Calendar the date and time when RhIg was administered, or null if not yet given
     */
    Calendar getRhIgGiven();

    /**
     * Gets the RhIg administration date as an XMLBeans XmlDate object.
     *
     * @return XmlDate the RhIg administration date as an XML date type
     */
    XmlDate xgetRhIgGiven();

    /**
     * Checks if the RhIg administration date is nil (explicitly set to null in XML).
     *
     * <p>This method distinguishes between a date that is unset versus a date that
     * is explicitly marked as nil in the XML schema, which has different semantic
     * meaning in healthcare data exchange.</p>
     *
     * @return boolean true if the RhIg date is explicitly nil, false otherwise
     */
    boolean isNilRhIgGiven();

    /**
     * Sets the date when Rh immunoglobulin was administered.
     *
     * @param p0 Calendar the date and time when RhIg was administered
     */
    void setRhIgGiven(final Calendar p0);

    /**
     * Sets the RhIg administration date using an XMLBeans XmlDate object.
     *
     * @param p0 XmlDate the RhIg administration date as an XML date type
     */
    void xsetRhIgGiven(final XmlDate p0);

    /**
     * Sets the RhIg administration date to nil (explicitly null in XML).
     *
     * <p>This method explicitly marks the RhIg date as nil in the XML document,
     * indicating that the value is intentionally absent or not applicable.</p>
     */
    void setNilRhIgGiven();

    /**
     * Gets the rubella vaccination recommendation status.
     *
     * <p>Rubella (German measles) can cause severe congenital defects if contracted
     * during pregnancy. Non-immune mothers are recommended to receive the MMR vaccine
     * postpartum to protect future pregnancies.</p>
     *
     * @return boolean true if rubella vaccination is recommended, false otherwise
     */
    boolean getRubella();

    /**
     * Gets the rubella vaccination status as an XMLBeans XmlBoolean object.
     *
     * @return XmlBoolean the rubella vaccination recommendation as an XML boolean type
     */
    XmlBoolean xgetRubella();

    /**
     * Sets the rubella vaccination recommendation status.
     *
     * @param p0 boolean true if rubella vaccination is recommended, false otherwise
     */
    void setRubella(final boolean p0);

    /**
     * Sets the rubella vaccination status using an XMLBeans XmlBoolean object.
     *
     * @param p0 XmlBoolean the rubella vaccination recommendation as an XML boolean type
     */
    void xsetRubella(final XmlBoolean p0);

    /**
     * Gets the newborn Hepatitis B immunoglobulin recommendation status.
     *
     * <p>Newborns born to Hepatitis B surface antigen-positive mothers require
     * Hepatitis B immunoglobulin (HBIG) within 12 hours of birth to prevent
     * vertical transmission of the virus.</p>
     *
     * @return boolean true if newborn HepB immunoglobulin is recommended, false otherwise
     */
    boolean getNewbornHepIG();

    /**
     * Gets the newborn HepB immunoglobulin status as an XMLBeans XmlBoolean object.
     *
     * @return XmlBoolean the newborn HepB immunoglobulin recommendation as an XML boolean type
     */
    XmlBoolean xgetNewbornHepIG();

    /**
     * Sets the newborn Hepatitis B immunoglobulin recommendation status.
     *
     * @param p0 boolean true if newborn HepB immunoglobulin is recommended, false otherwise
     */
    void setNewbornHepIG(final boolean p0);

    /**
     * Sets the newborn HepB immunoglobulin status using an XMLBeans XmlBoolean object.
     *
     * @param p0 XmlBoolean the newborn HepB immunoglobulin recommendation as an XML boolean type
     */
    void xsetNewbornHepIG(final XmlBoolean p0);

    /**
     * Gets the Hepatitis B vaccine recommendation status.
     *
     * <p>Hepatitis B vaccine is recommended for all newborns, with accelerated schedules
     * for infants born to HBsAg-positive mothers. This field tracks whether the vaccine
     * is recommended as part of the newborn's immunization plan.</p>
     *
     * @return boolean true if Hepatitis B vaccine is recommended, false otherwise
     */
    boolean getHepBVaccine();

    /**
     * Gets the Hepatitis B vaccine status as an XMLBeans XmlBoolean object.
     *
     * @return XmlBoolean the Hepatitis B vaccine recommendation as an XML boolean type
     */
    XmlBoolean xgetHepBVaccine();

    /**
     * Sets the Hepatitis B vaccine recommendation status.
     *
     * @param p0 boolean true if Hepatitis B vaccine is recommended, false otherwise
     */
    void setHepBVaccine(final boolean p0);

    /**
     * Sets the Hepatitis B vaccine status using an XMLBeans XmlBoolean object.
     *
     * @param p0 XmlBoolean the Hepatitis B vaccine recommendation as an XML boolean type
     */
    void xsetHepBVaccine(final XmlBoolean p0);

    /**
     * Factory class for creating and parsing RecommendedImmunoprophylaxisType instances.
     *
     * <p>This inner class provides static factory methods for:</p>
     * <ul>
     *   <li>Creating new instances of RecommendedImmunoprophylaxisType</li>
     *   <li>Parsing XML from various sources (String, File, URL, streams, etc.)</li>
     *   <li>Validating XML against the schema</li>
     * </ul>
     *
     * <p>The Factory follows the XMLBeans pattern for type-safe XML object creation
     * and parsing, ensuring all instances conform to the AR2005 schema.</p>
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new empty instance of RecommendedImmunoprophylaxisType.
         *
         * @return RecommendedImmunoprophylaxisType a new instance with default values
         */
        public static RecommendedImmunoprophylaxisType newInstance() {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().newInstance(RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Creates a new instance with specified XML options.
         *
         * @param options XmlOptions configuration options for XML processing (validation, namespaces, etc.)
         * @return RecommendedImmunoprophylaxisType a new instance with the specified options applied
         */
        public static RecommendedImmunoprophylaxisType newInstance(final XmlOptions options) {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().newInstance(RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Parses an XML string into a RecommendedImmunoprophylaxisType instance.
         *
         * @param xmlAsString String the XML content as a string
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static RecommendedImmunoprophylaxisType parse(final String xmlAsString) throws XmlException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(xmlAsString, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string with specified options.
         *
         * @param xmlAsString String the XML content as a string
         * @param options XmlOptions configuration options for parsing
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static RecommendedImmunoprophylaxisType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(xmlAsString, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Parses an XML file into a RecommendedImmunoprophylaxisType instance.
         *
         * @param file File the XML file to parse
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if there is an error reading the file
         */
        public static RecommendedImmunoprophylaxisType parse(final File file) throws XmlException, IOException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(file, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions configuration options for parsing
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if there is an error reading the file
         */
        public static RecommendedImmunoprophylaxisType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(file, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Parses XML from a URL into a RecommendedImmunoprophylaxisType instance.
         *
         * @param u URL the URL pointing to the XML content
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if there is an error reading from the URL
         */
        public static RecommendedImmunoprophylaxisType parse(final URL u) throws XmlException, IOException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(u, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a URL with specified options.
         *
         * @param u URL the URL pointing to the XML content
         * @param options XmlOptions configuration options for parsing
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if there is an error reading from the URL
         */
        public static RecommendedImmunoprophylaxisType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(u, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Parses XML from an input stream into a RecommendedImmunoprophylaxisType instance.
         *
         * @param is InputStream the input stream containing XML data
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if there is an error reading from the stream
         */
        public static RecommendedImmunoprophylaxisType parse(final InputStream is) throws XmlException, IOException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(is, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an input stream with specified options.
         *
         * @param is InputStream the input stream containing XML data
         * @param options XmlOptions configuration options for parsing
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if there is an error reading from the stream
         */
        public static RecommendedImmunoprophylaxisType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(is, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Parses XML from a character reader into a RecommendedImmunoprophylaxisType instance.
         *
         * @param r Reader the character reader containing XML data
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if there is an error reading from the reader
         */
        public static RecommendedImmunoprophylaxisType parse(final Reader r) throws XmlException, IOException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(r, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a character reader with specified options.
         *
         * @param r Reader the character reader containing XML data
         * @param options XmlOptions configuration options for parsing
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if there is an error reading from the reader
         */
        public static RecommendedImmunoprophylaxisType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(r, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Parses XML from an XMLStreamReader into a RecommendedImmunoprophylaxisType instance.
         *
         * @param sr XMLStreamReader the stream reader positioned at the XML content
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static RecommendedImmunoprophylaxisType parse(final XMLStreamReader sr) throws XmlException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(sr, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLStreamReader with specified options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the XML content
         * @param options XmlOptions configuration options for parsing
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static RecommendedImmunoprophylaxisType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(sr, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Parses XML from a DOM Node into a RecommendedImmunoprophylaxisType instance.
         *
         * @param node Node the DOM node containing the XML data
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static RecommendedImmunoprophylaxisType parse(final Node node) throws XmlException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(node, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a DOM Node with specified options.
         *
         * @param node Node the DOM node containing the XML data
         * @param options XmlOptions configuration options for parsing
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static RecommendedImmunoprophylaxisType parse(final Node node, final XmlOptions options) throws XmlException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(node, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Parses XML from a deprecated XMLInputStream into a RecommendedImmunoprophylaxisType instance.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader instead.
         * @param xis XMLInputStream the XML input stream (deprecated)
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws XMLStreamException if there is an error reading from the stream
         */
        @Deprecated
        public static RecommendedImmunoprophylaxisType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(xis, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a deprecated XMLInputStream with specified options.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader instead.
         * @param xis XMLInputStream the XML input stream (deprecated)
         * @param options XmlOptions configuration options for parsing
         * @return RecommendedImmunoprophylaxisType the parsed instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws XMLStreamException if there is an error reading from the stream
         */
        @Deprecated
        public static RecommendedImmunoprophylaxisType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (RecommendedImmunoprophylaxisType)XmlBeans.getContextTypeLoader().parse(xis, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Creates a validating XMLInputStream from a deprecated XMLInputStream.
         *
         * <p>This method wraps the input stream with validation logic to ensure the XML
         * conforms to the AR2005 schema during parsing.</p>
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader with validation instead.
         * @param xis XMLInputStream the XML input stream to validate (deprecated)
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if the XML does not conform to the schema
         * @throws XMLStreamException if there is an error reading from the stream
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, RecommendedImmunoprophylaxisType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream with specified options.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader with validation instead.
         * @param xis XMLInputStream the XML input stream to validate (deprecated)
         * @param options XmlOptions configuration options for validation
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if the XML does not conform to the schema
         * @throws XMLStreamException if there is an error reading from the stream
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, RecommendedImmunoprophylaxisType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of the Factory class.
         *
         * <p>All methods in this class are static, so instances are not needed.</p>
         */
        private Factory() {
        }
    }
}
