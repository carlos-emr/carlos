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
import org.apache.xmlbeans.XmlFloat;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Represents a comprehensive physical examination record type for antenatal care documentation.
 *
 * <p>This XMLBeans-generated interface defines the structure for physical examination data
 * collected during prenatal care visits, including vital signs, body measurements, and
 * systematic examination findings across multiple organ systems.</p>
 *
 * <p>The physical examination type supports both numerical measurements (height, weight, BMI,
 * blood pressure) and categorical assessments (normal/abnormal classifications) for various
 * anatomical regions including thyroid, cardiovascular, chest, abdomen, gynecological
 * structures, and other relevant systems.</p>
 *
 * <p>This type is part of the British Columbia Antenatal Record 2005 (BCAR) form suite,
 * used to standardize and document maternal health assessments throughout pregnancy.
 * All examination findings can be marked as nil/null when data is not available or not assessed.</p>
 *
 * @see NormalAbnormalNullType
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-24
 */
public interface PhysicalExaminationType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(PhysicalExaminationType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("physicalexaminationtypeb2d9type");

    /**
     * Gets the height measurement value.
     *
     * @return float the patient's height measurement
     */
    float getHeight();

    /**
     * Gets the height measurement as an XMLBeans Height object.
     *
     * @return Height the height value wrapped in an XMLBeans type
     */
    Height xgetHeight();

    /**
     * Checks if the height value is nil (not provided or not applicable).
     *
     * @return boolean true if height is nil, false otherwise
     */
    boolean isNilHeight();

    /**
     * Sets the height measurement value.
     *
     * @param p0 float the height value to set
     */
    void setHeight(final float p0);

    /**
     * Sets the height measurement using an XMLBeans Height object.
     *
     * @param p0 Height the height value to set
     */
    void xsetHeight(final Height p0);

    /**
     * Sets the height value to nil (marks as not provided or not applicable).
     */
    void setNilHeight();

    /**
     * Gets the weight measurement value.
     *
     * @return float the patient's weight measurement
     */
    float getWeight();

    /**
     * Gets the weight measurement as an XMLBeans Weight object.
     *
     * @return Weight the weight value wrapped in an XMLBeans type
     */
    Weight xgetWeight();

    /**
     * Checks if the weight value is nil (not provided or not applicable).
     *
     * @return boolean true if weight is nil, false otherwise
     */
    boolean isNilWeight();

    /**
     * Sets the weight measurement value.
     *
     * @param p0 float the weight value to set
     */
    void setWeight(final float p0);

    /**
     * Sets the weight measurement using an XMLBeans Weight object.
     *
     * @param p0 Weight the weight value to set
     */
    void xsetWeight(final Weight p0);

    /**
     * Sets the weight value to nil (marks as not provided or not applicable).
     */
    void setNilWeight();

    /**
     * Gets the Body Mass Index (BMI) value.
     *
     * @return float the calculated BMI value
     */
    float getBmi();

    /**
     * Gets the BMI value as an XMLBeans Bmi object.
     *
     * @return Bmi the BMI value wrapped in an XMLBeans type
     */
    Bmi xgetBmi();

    /**
     * Checks if the BMI value is nil (not provided or not applicable).
     *
     * @return boolean true if BMI is nil, false otherwise
     */
    boolean isNilBmi();

    /**
     * Sets the Body Mass Index (BMI) value.
     *
     * @param p0 float the BMI value to set
     */
    void setBmi(final float p0);

    /**
     * Sets the BMI value using an XMLBeans Bmi object.
     *
     * @param p0 Bmi the BMI value to set
     */
    void xsetBmi(final Bmi p0);

    /**
     * Sets the BMI value to nil (marks as not provided or not applicable).
     */
    void setNilBmi();

    /**
     * Gets the blood pressure measurement as a string.
     *
     * @return String the blood pressure value (typically in systolic/diastolic format)
     */
    String getBp();

    /**
     * Gets the blood pressure measurement as an XMLBeans Bp object.
     *
     * @return Bp the blood pressure value wrapped in an XMLBeans type
     */
    Bp xgetBp();

    /**
     * Sets the blood pressure measurement value.
     *
     * @param p0 String the blood pressure value to set
     */
    void setBp(final String p0);

    /**
     * Sets the blood pressure measurement using an XMLBeans Bp object.
     *
     * @param p0 Bp the blood pressure value to set
     */
    void xsetBp(final Bp p0);

    /**
     * Gets the thyroid examination finding.
     *
     * @return NormalAbnormalNullType the thyroid examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getThyroid();

    /**
     * Sets the thyroid examination finding.
     *
     * @param p0 NormalAbnormalNullType the thyroid examination status to set
     */
    void setThyroid(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new thyroid examination finding.
     *
     * @return NormalAbnormalNullType the newly created thyroid examination object
     */
    NormalAbnormalNullType addNewThyroid();

    /**
     * Gets the chest examination finding.
     *
     * @return NormalAbnormalNullType the chest examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getChest();

    /**
     * Sets the chest examination finding.
     *
     * @param p0 NormalAbnormalNullType the chest examination status to set
     */
    void setChest(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new chest examination finding.
     *
     * @return NormalAbnormalNullType the newly created chest examination object
     */
    NormalAbnormalNullType addNewChest();

    /**
     * Gets the breast examination finding.
     *
     * @return NormalAbnormalNullType the breast examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getBreasts();

    /**
     * Sets the breast examination finding.
     *
     * @param p0 NormalAbnormalNullType the breast examination status to set
     */
    void setBreasts(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new breast examination finding.
     *
     * @return NormalAbnormalNullType the newly created breast examination object
     */
    NormalAbnormalNullType addNewBreasts();

    /**
     * Gets the cardiovascular examination finding.
     *
     * @return NormalAbnormalNullType the cardiovascular examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getCardiovascular();

    /**
     * Sets the cardiovascular examination finding.
     *
     * @param p0 NormalAbnormalNullType the cardiovascular examination status to set
     */
    void setCardiovascular(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new cardiovascular examination finding.
     *
     * @return NormalAbnormalNullType the newly created cardiovascular examination object
     */
    NormalAbnormalNullType addNewCardiovascular();

    /**
     * Gets the abdominal examination finding.
     *
     * @return NormalAbnormalNullType the abdominal examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getAbdomen();

    /**
     * Sets the abdominal examination finding.
     *
     * @param p0 NormalAbnormalNullType the abdominal examination status to set
     */
    void setAbdomen(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new abdominal examination finding.
     *
     * @return NormalAbnormalNullType the newly created abdominal examination object
     */
    NormalAbnormalNullType addNewAbdomen();

    /**
     * Gets the varicosities examination finding.
     *
     * @return NormalAbnormalNullType the varicosities examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getVaricosities();

    /**
     * Sets the varicosities examination finding.
     *
     * @param p0 NormalAbnormalNullType the varicosities examination status to set
     */
    void setVaricosities(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new varicosities examination finding.
     *
     * @return NormalAbnormalNullType the newly created varicosities examination object
     */
    NormalAbnormalNullType addNewVaricosities();

    /**
     * Gets the external genitals examination finding.
     *
     * @return NormalAbnormalNullType the external genitals examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getExernalGenitals();

    /**
     * Sets the external genitals examination finding.
     *
     * @param p0 NormalAbnormalNullType the external genitals examination status to set
     */
    void setExernalGenitals(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new external genitals examination finding.
     *
     * @return NormalAbnormalNullType the newly created external genitals examination object
     */
    NormalAbnormalNullType addNewExernalGenitals();

    /**
     * Gets the cervix and vagina examination finding.
     *
     * @return NormalAbnormalNullType the cervix/vagina examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getCervixVagina();

    /**
     * Sets the cervix and vagina examination finding.
     *
     * @param p0 NormalAbnormalNullType the cervix/vagina examination status to set
     */
    void setCervixVagina(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new cervix and vagina examination finding.
     *
     * @return NormalAbnormalNullType the newly created cervix/vagina examination object
     */
    NormalAbnormalNullType addNewCervixVagina();

    /**
     * Gets the uterus examination finding.
     *
     * @return NormalAbnormalNullType the uterus examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getUterus();

    /**
     * Sets the uterus examination finding.
     *
     * @param p0 NormalAbnormalNullType the uterus examination status to set
     */
    void setUterus(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new uterus examination finding.
     *
     * @return NormalAbnormalNullType the newly created uterus examination object
     */
    NormalAbnormalNullType addNewUterus();

    /**
     * Gets the uterus size description.
     *
     * @return String the uterus size measurement or description
     */
    String getUterusSize();

    /**
     * Gets the uterus size as an XMLBeans XmlString object.
     *
     * @return XmlString the uterus size wrapped in an XMLBeans type
     */
    XmlString xgetUterusSize();

    /**
     * Sets the uterus size description.
     *
     * @param p0 String the uterus size measurement or description to set
     */
    void setUterusSize(final String p0);

    /**
     * Sets the uterus size using an XMLBeans XmlString object.
     *
     * @param p0 XmlString the uterus size to set
     */
    void xsetUterusSize(final XmlString p0);

    /**
     * Gets the adnexa examination finding.
     *
     * @return NormalAbnormalNullType the adnexa examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getAdnexa();

    /**
     * Sets the adnexa examination finding.
     *
     * @param p0 NormalAbnormalNullType the adnexa examination status to set
     */
    void setAdnexa(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new adnexa examination finding.
     *
     * @return NormalAbnormalNullType the newly created adnexa examination object
     */
    NormalAbnormalNullType addNewAdnexa();

    /**
     * Gets the description for other examination findings.
     *
     * @return String the textual description of other examination findings
     */
    String getOtherDescr();

    /**
     * Gets the other examination description as an XMLBeans XmlString object.
     *
     * @return XmlString the other examination description wrapped in an XMLBeans type
     */
    XmlString xgetOtherDescr();

    /**
     * Sets the description for other examination findings.
     *
     * @param p0 String the textual description to set
     */
    void setOtherDescr(final String p0);

    /**
     * Sets the other examination description using an XMLBeans XmlString object.
     *
     * @param p0 XmlString the description to set
     */
    void xsetOtherDescr(final XmlString p0);

    /**
     * Gets the other examination finding status.
     *
     * @return NormalAbnormalNullType the other examination status (normal, abnormal, or null)
     */
    NormalAbnormalNullType getOther();

    /**
     * Sets the other examination finding status.
     *
     * @param p0 NormalAbnormalNullType the other examination status to set
     */
    void setOther(final NormalAbnormalNullType p0);

    /**
     * Creates and adds a new other examination finding.
     *
     * @return NormalAbnormalNullType the newly created other examination object
     */
    NormalAbnormalNullType addNewOther();

    /**
     * Represents a height measurement as an XML floating-point type.
     *
     * <p>This inner interface provides XMLBeans-specific type handling for height values,
     * extending XmlFloat to support XML schema validation and serialization.</p>
     *
     * @since 2026-01-24
     */
    public interface Height extends XmlFloat
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Height.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("heightf75celemtype");

        /**
         * Factory class for creating Height instances.
         *
         * <p>Provides static methods for constructing new Height objects with or without
         * XMLBeans parsing options.</p>
         */
        public static final class Factory
        {
            /**
             * Creates a new Height instance from an object value.
             *
             * @param obj Object the value to convert to a Height type
             * @return Height the newly created Height instance
             */
            public static Height newValue(final Object obj) {
                return (Height)Height.type.newValue(obj);
            }

            /**
             * Creates a new empty Height instance.
             *
             * @return Height the newly created Height instance
             */
            public static Height newInstance() {
                return (Height)XmlBeans.getContextTypeLoader().newInstance(Height.type, (XmlOptions)null);
            }

            /**
             * Creates a new empty Height instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/validation options
             * @return Height the newly created Height instance
             */
            public static Height newInstance(final XmlOptions options) {
                return (Height)XmlBeans.getContextTypeLoader().newInstance(Height.type, options);
            }

            /**
             * Private constructor to prevent instantiation of factory class.
             */
            private Factory() {
            }
        }
    }

    /**
     * Represents a weight measurement as an XML floating-point type.
     *
     * <p>This inner interface provides XMLBeans-specific type handling for weight values,
     * extending XmlFloat to support XML schema validation and serialization.</p>
     *
     * @since 2026-01-24
     */
    public interface Weight extends XmlFloat
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Weight.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("weightc0edelemtype");

        /**
         * Factory class for creating Weight instances.
         *
         * <p>Provides static methods for constructing new Weight objects with or without
         * XMLBeans parsing options.</p>
         */
        public static final class Factory
        {
            /**
             * Creates a new Weight instance from an object value.
             *
             * @param obj Object the value to convert to a Weight type
             * @return Weight the newly created Weight instance
             */
            public static Weight newValue(final Object obj) {
                return (Weight)Weight.type.newValue(obj);
            }

            /**
             * Creates a new empty Weight instance.
             *
             * @return Weight the newly created Weight instance
             */
            public static Weight newInstance() {
                return (Weight)XmlBeans.getContextTypeLoader().newInstance(Weight.type, (XmlOptions)null);
            }

            /**
             * Creates a new empty Weight instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/validation options
             * @return Weight the newly created Weight instance
             */
            public static Weight newInstance(final XmlOptions options) {
                return (Weight)XmlBeans.getContextTypeLoader().newInstance(Weight.type, options);
            }

            /**
             * Private constructor to prevent instantiation of factory class.
             */
            private Factory() {
            }
        }
    }

    /**
     * Represents a Body Mass Index (BMI) measurement as an XML floating-point type.
     *
     * <p>This inner interface provides XMLBeans-specific type handling for BMI values,
     * extending XmlFloat to support XML schema validation and serialization.</p>
     *
     * @since 2026-01-24
     */
    public interface Bmi extends XmlFloat
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Bmi.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("bmi6343elemtype");

        /**
         * Factory class for creating Bmi instances.
         *
         * <p>Provides static methods for constructing new Bmi objects with or without
         * XMLBeans parsing options.</p>
         */
        public static final class Factory
        {
            /**
             * Creates a new Bmi instance from an object value.
             *
             * @param obj Object the value to convert to a Bmi type
             * @return Bmi the newly created Bmi instance
             */
            public static Bmi newValue(final Object obj) {
                return (Bmi)Bmi.type.newValue(obj);
            }

            /**
             * Creates a new empty Bmi instance.
             *
             * @return Bmi the newly created Bmi instance
             */
            public static Bmi newInstance() {
                return (Bmi)XmlBeans.getContextTypeLoader().newInstance(Bmi.type, (XmlOptions)null);
            }

            /**
             * Creates a new empty Bmi instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/validation options
             * @return Bmi the newly created Bmi instance
             */
            public static Bmi newInstance(final XmlOptions options) {
                return (Bmi)XmlBeans.getContextTypeLoader().newInstance(Bmi.type, options);
            }

            /**
             * Private constructor to prevent instantiation of factory class.
             */
            private Factory() {
            }
        }
    }

    /**
     * Represents a blood pressure measurement as an XML string type.
     *
     * <p>This inner interface provides XMLBeans-specific type handling for blood pressure values,
     * extending XmlString to support XML schema validation and serialization. Blood pressure
     * is typically stored in systolic/diastolic format (e.g., "120/80").</p>
     *
     * @since 2026-01-24
     */
    public interface Bp extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Bp.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("bp6443elemtype");

        /**
         * Factory class for creating Bp instances.
         *
         * <p>Provides static methods for constructing new Bp objects with or without
         * XMLBeans parsing options.</p>
         */
        public static final class Factory
        {
            /**
             * Creates a new Bp instance from an object value.
             *
             * @param obj Object the value to convert to a Bp type
             * @return Bp the newly created Bp instance
             */
            public static Bp newValue(final Object obj) {
                return (Bp)Bp.type.newValue(obj);
            }

            /**
             * Creates a new empty Bp instance.
             *
             * @return Bp the newly created Bp instance
             */
            public static Bp newInstance() {
                return (Bp)XmlBeans.getContextTypeLoader().newInstance(Bp.type, (XmlOptions)null);
            }

            /**
             * Creates a new empty Bp instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/validation options
             * @return Bp the newly created Bp instance
             */
            public static Bp newInstance(final XmlOptions options) {
                return (Bp)XmlBeans.getContextTypeLoader().newInstance(Bp.type, options);
            }

            /**
             * Private constructor to prevent instantiation of factory class.
             */
            private Factory() {
            }
        }
    }

    /**
     * Factory class for creating and parsing PhysicalExaminationType instances.
     *
     * <p>Provides static methods for constructing new PhysicalExaminationType objects,
     * parsing XML from various sources (strings, files, URLs, streams), and validating
     * XML input streams.</p>
     */
    public static final class Factory
    {
        /**
         * Creates a new empty PhysicalExaminationType instance.
         *
         * @return PhysicalExaminationType the newly created instance
         */
        public static PhysicalExaminationType newInstance() {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().newInstance(PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty PhysicalExaminationType instance with specified XML options.
         *
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the newly created instance
         */
        public static PhysicalExaminationType newInstance(final XmlOptions options) {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().newInstance(PhysicalExaminationType.type, options);
        }

        /**
         * Parses an XML string into a PhysicalExaminationType instance.
         *
         * @param xmlAsString String the XML document as a string
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static PhysicalExaminationType parse(final String xmlAsString) throws XmlException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(xmlAsString, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string into a PhysicalExaminationType instance with specified options.
         *
         * @param xmlAsString String the XML document as a string
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static PhysicalExaminationType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(xmlAsString, PhysicalExaminationType.type, options);
        }

        /**
         * Parses an XML file into a PhysicalExaminationType instance.
         *
         * @param file File the XML file to parse
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if an I/O error occurs while reading the file
         */
        public static PhysicalExaminationType parse(final File file) throws XmlException, IOException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(file, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file into a PhysicalExaminationType instance with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if an I/O error occurs while reading the file
         */
        public static PhysicalExaminationType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(file, PhysicalExaminationType.type, options);
        }

        /**
         * Parses XML from a URL into a PhysicalExaminationType instance.
         *
         * @param u URL the URL pointing to the XML document
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if an I/O error occurs while reading from the URL
         */
        public static PhysicalExaminationType parse(final URL u) throws XmlException, IOException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(u, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a URL into a PhysicalExaminationType instance with specified options.
         *
         * @param u URL the URL pointing to the XML document
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if an I/O error occurs while reading from the URL
         */
        public static PhysicalExaminationType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(u, PhysicalExaminationType.type, options);
        }

        /**
         * Parses XML from an InputStream into a PhysicalExaminationType instance.
         *
         * @param is InputStream the input stream containing the XML document
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if an I/O error occurs while reading from the stream
         */
        public static PhysicalExaminationType parse(final InputStream is) throws XmlException, IOException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(is, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an InputStream into a PhysicalExaminationType instance with specified options.
         *
         * @param is InputStream the input stream containing the XML document
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if an I/O error occurs while reading from the stream
         */
        public static PhysicalExaminationType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(is, PhysicalExaminationType.type, options);
        }

        /**
         * Parses XML from a Reader into a PhysicalExaminationType instance.
         *
         * @param r Reader the reader providing the XML document
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if an I/O error occurs while reading from the reader
         */
        public static PhysicalExaminationType parse(final Reader r) throws XmlException, IOException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(r, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a Reader into a PhysicalExaminationType instance with specified options.
         *
         * @param r Reader the reader providing the XML document
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if an I/O error occurs while reading from the reader
         */
        public static PhysicalExaminationType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(r, PhysicalExaminationType.type, options);
        }

        /**
         * Parses XML from an XMLStreamReader into a PhysicalExaminationType instance.
         *
         * @param sr XMLStreamReader the stream reader positioned at the start of the document
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static PhysicalExaminationType parse(final XMLStreamReader sr) throws XmlException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(sr, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLStreamReader into a PhysicalExaminationType instance with specified options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the start of the document
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static PhysicalExaminationType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(sr, PhysicalExaminationType.type, options);
        }

        /**
         * Parses XML from a DOM Node into a PhysicalExaminationType instance.
         *
         * @param node Node the DOM node representing the XML document or element
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static PhysicalExaminationType parse(final Node node) throws XmlException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(node, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a DOM Node into a PhysicalExaminationType instance with specified options.
         *
         * @param node Node the DOM node representing the XML document or element
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static PhysicalExaminationType parse(final Node node, final XmlOptions options) throws XmlException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(node, PhysicalExaminationType.type, options);
        }

        /**
         * Parses XML from an XMLInputStream into a PhysicalExaminationType instance.
         *
         * @param xis XMLInputStream the XML input stream
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws XMLStreamException if an XML streaming error occurs
         * @deprecated XMLInputStream is deprecated. Use InputStream or Reader instead.
         */
        @Deprecated
        public static PhysicalExaminationType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(xis, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLInputStream into a PhysicalExaminationType instance with specified options.
         *
         * @param xis XMLInputStream the XML input stream
         * @param options XmlOptions the XML parsing/validation options
         * @return PhysicalExaminationType the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws XMLStreamException if an XML streaming error occurs
         * @deprecated XMLInputStream is deprecated. Use InputStream or Reader instead.
         */
        @Deprecated
        public static PhysicalExaminationType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (PhysicalExaminationType)XmlBeans.getContextTypeLoader().parse(xis, PhysicalExaminationType.type, options);
        }

        /**
         * Creates a validating XMLInputStream wrapper for the given input stream.
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if the XML is malformed or invalid
         * @throws XMLStreamException if an XML streaming error occurs
         * @deprecated XMLInputStream is deprecated. Use InputStream or Reader instead.
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PhysicalExaminationType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream wrapper for the given input stream with specified options.
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation
         * @param options XmlOptions the XML parsing/validation options
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if the XML is malformed or invalid
         * @throws XMLStreamException if an XML streaming error occurs
         * @deprecated XMLInputStream is deprecated. Use InputStream or Reader instead.
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PhysicalExaminationType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of factory class.
         */
        private Factory() {
        }
    }
}
