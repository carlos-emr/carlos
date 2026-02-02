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
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlFloat;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlInt;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Represents an obstetrical history item in the British Columbia Antenatal Record (BCAR) system.
 *
 * This XMLBeans-generated interface provides structured access to pregnancy and delivery outcome data
 * collected during prenatal care and obstetrical assessments. It captures essential information about
 * previous pregnancies including delivery details, gestational age, birth weight, and delivery method.
 *
 * The interface is part of the AR2005 (Antenatal Record 2005) form system used in British Columbia
 * for tracking maternal health and pregnancy outcomes. It supports standardized data collection for
 * obstetrical history documentation required for comprehensive prenatal care.
 *
 * Key features:
 * <ul>
 *   <li>Tracks year of delivery and gestational age at birth</li>
 *   <li>Records infant sex, birth weight, and place of birth</li>
 *   <li>Captures delivery method (vaginal, cesarean, assisted, etc.)</li>
 *   <li>Documents length of labour and additional clinical comments</li>
 *   <li>Supports XML serialization/deserialization for data exchange</li>
 * </ul>
 *
 * @since 2026-01-24
 * @see ca.openosp.openo.ar2005 AR2005 form package
 */
public interface ObstetricalHistoryItemList extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(ObstetricalHistoryItemList.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("obstetricalhistoryitemliste7c8type");

    /**
     * Gets the year of delivery.
     *
     * @return int the year when the delivery occurred
     */
    int getYear();

    /**
     * Gets the year value as an XmlInt object.
     *
     * @return XmlInt the year value wrapped in an XmlInt object
     */
    XmlInt xgetYear();

    /**
     * Sets the year of delivery.
     *
     * @param p0 int the year when the delivery occurred
     */
    void setYear(final int p0);

    /**
     * Sets the year value using an XmlInt object.
     *
     * @param p0 XmlInt the year value wrapped in an XmlInt object
     */
    void xsetYear(final XmlInt p0);

    /**
     * Gets the sex of the infant.
     *
     * @return Sex.Enum the infant's sex (M=Male, F=Female, A=Ambiguous, U=Unknown)
     */
    Sex.Enum getSex();

    /**
     * Gets the sex value as a Sex object.
     *
     * @return Sex the infant's sex wrapped in a Sex object
     */
    Sex xgetSex();

    /**
     * Sets the sex of the infant.
     *
     * @param p0 Sex.Enum the infant's sex (M=Male, F=Female, A=Ambiguous, U=Unknown)
     */
    void setSex(final Sex.Enum p0);

    /**
     * Sets the sex value using a Sex object.
     *
     * @param p0 Sex the infant's sex wrapped in a Sex object
     */
    void xsetSex(final Sex p0);

    /**
     * Gets the gestational age at birth in weeks.
     *
     * @return int the gestational age in completed weeks
     */
    int getGestAge();

    /**
     * Gets the gestational age value as an XmlInt object.
     *
     * @return XmlInt the gestational age wrapped in an XmlInt object
     */
    XmlInt xgetGestAge();

    /**
     * Sets the gestational age at birth in weeks.
     *
     * @param p0 int the gestational age in completed weeks
     */
    void setGestAge(final int p0);

    /**
     * Sets the gestational age value using an XmlInt object.
     *
     * @param p0 XmlInt the gestational age wrapped in an XmlInt object
     */
    void xsetGestAge(final XmlInt p0);

    /**
     * Gets the birth weight of the infant.
     *
     * @return String the birth weight, typically in grams or pounds/ounces
     */
    String getBirthWeight();

    /**
     * Gets the birth weight value as an XmlString object.
     *
     * @return XmlString the birth weight wrapped in an XmlString object
     */
    XmlString xgetBirthWeight();

    /**
     * Sets the birth weight of the infant.
     *
     * @param p0 String the birth weight, typically in grams or pounds/ounces
     */
    void setBirthWeight(final String p0);

    /**
     * Sets the birth weight value using an XmlString object.
     *
     * @param p0 XmlString the birth weight wrapped in an XmlString object
     */
    void xsetBirthWeight(final XmlString p0);

    /**
     * Gets the length of labour in hours.
     *
     * @return float the duration of labour in hours
     */
    float getLengthOfLabour();

    /**
     * Gets the length of labour value as an XmlFloat object.
     *
     * @return XmlFloat the labour duration wrapped in an XmlFloat object
     */
    XmlFloat xgetLengthOfLabour();

    /**
     * Checks if the length of labour value is nil (null/not applicable).
     *
     * @return boolean true if the length of labour is nil, false otherwise
     */
    boolean isNilLengthOfLabour();

    /**
     * Sets the length of labour in hours.
     *
     * @param p0 float the duration of labour in hours
     */
    void setLengthOfLabour(final float p0);

    /**
     * Sets the length of labour value using an XmlFloat object.
     *
     * @param p0 XmlFloat the labour duration wrapped in an XmlFloat object
     */
    void xsetLengthOfLabour(final XmlFloat p0);

    /**
     * Sets the length of labour to nil (null/not applicable).
     */
    void setNilLengthOfLabour();

    /**
     * Gets the place where the birth occurred.
     *
     * @return String the birth location (hospital name, home, birthing center, etc.)
     */
    String getPlaceOfBirth();

    /**
     * Gets the place of birth value as an XmlString object.
     *
     * @return XmlString the birth location wrapped in an XmlString object
     */
    XmlString xgetPlaceOfBirth();

    /**
     * Sets the place where the birth occurred.
     *
     * @param p0 String the birth location (hospital name, home, birthing center, etc.)
     */
    void setPlaceOfBirth(final String p0);

    /**
     * Sets the place of birth value using an XmlString object.
     *
     * @param p0 XmlString the birth location wrapped in an XmlString object
     */
    void xsetPlaceOfBirth(final XmlString p0);

    /**
     * Gets the type of delivery method used.
     *
     * @return TypeOfDelivery.Enum the delivery method (AVAG, IND, CS, SVAG, VAG, UN)
     */
    TypeOfDelivery.Enum getTypeOfDelivery();

    /**
     * Gets the type of delivery value as a TypeOfDelivery object.
     *
     * @return TypeOfDelivery the delivery method wrapped in a TypeOfDelivery object
     */
    TypeOfDelivery xgetTypeOfDelivery();

    /**
     * Sets the type of delivery method used.
     *
     * @param p0 TypeOfDelivery.Enum the delivery method (AVAG, IND, CS, SVAG, VAG, UN)
     */
    void setTypeOfDelivery(final TypeOfDelivery.Enum p0);

    /**
     * Sets the type of delivery value using a TypeOfDelivery object.
     *
     * @param p0 TypeOfDelivery the delivery method wrapped in a TypeOfDelivery object
     */
    void xsetTypeOfDelivery(final TypeOfDelivery p0);

    /**
     * Gets additional clinical comments or notes about the delivery.
     *
     * @return String free-text comments about the delivery or pregnancy outcome
     */
    String getComments();

    /**
     * Gets the comments value as an XmlString object.
     *
     * @return XmlString the comments wrapped in an XmlString object
     */
    XmlString xgetComments();

    /**
     * Sets additional clinical comments or notes about the delivery.
     *
     * @param p0 String free-text comments about the delivery or pregnancy outcome
     */
    void setComments(final String p0);

    /**
     * Sets the comments value using an XmlString object.
     *
     * @param p0 XmlString the comments wrapped in an XmlString object
     */
    void xsetComments(final XmlString p0);
    
    /**
     * Enumeration representing the sex of the infant at birth.
     *
     * Defines standard sex categories used in obstetrical records:
     * <ul>
     *   <li>M (Male) - Male infant</li>
     *   <li>F (Female) - Female infant</li>
     *   <li>A (Ambiguous) - Ambiguous genitalia or intersex condition</li>
     *   <li>U (Unknown) - Sex not determined or not yet assigned</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface Sex extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Sex.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("sex4536elemtype");
        public static final Enum M = Enum.forString("M");
        public static final Enum F = Enum.forString("F");
        public static final Enum A = Enum.forString("A");
        public static final Enum U = Enum.forString("U");
        public static final int INT_M = 1;
        public static final int INT_F = 2;
        public static final int INT_A = 3;
        public static final int INT_U = 4;

        /**
         * Gets the enumeration value.
         *
         * @return StringEnumAbstractBase the underlying enum value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the sex value using a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the sex enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        /**
         * Enumeration implementation for Sex values.
         *
         * Provides type-safe enumeration of sex categories with string and integer representations.
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_M = 1;
            static final int INT_F = 2;
            static final int INT_A = 3;
            static final int INT_U = 4;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;

            /**
             * Returns the Sex.Enum constant for the given string value.
             *
             * @param s String the sex code ("M", "F", "A", or "U")
             * @return Enum the corresponding Sex.Enum constant
             */
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }

            /**
             * Returns the Sex.Enum constant for the given integer value.
             *
             * @param i int the sex code (1=M, 2=F, 3=A, 4=U)
             * @return Enum the corresponding Sex.Enum constant
             */
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }

            /**
             * Private constructor for creating Sex.Enum instances.
             *
             * @param s String the string representation of the sex
             * @param i int the integer representation of the sex
             */
            private Enum(final String s, final int i) {
                super(s, i);
            }

            /**
             * Resolves the deserialized object to the corresponding enum constant.
             *
             * @return Object the resolved enum constant
             */
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("M", 1), new Enum("F", 2), new Enum("A", 3), new Enum("U", 4) });
            }
        }
        
        /**
         * Factory class for creating Sex instances.
         *
         * Provides static factory methods for instantiating Sex objects with or without XML options.
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new Sex instance from the given object.
             *
             * @param obj Object the source object to convert to Sex
             * @return Sex the new Sex instance
             */
            public static Sex newValue(final Object obj) {
                return (Sex)Sex.type.newValue(obj);
            }

            /**
             * Creates a new Sex instance with default XML options.
             *
             * @return Sex the new Sex instance
             */
            public static Sex newInstance() {
                return (Sex)XmlBeans.getContextTypeLoader().newInstance(Sex.type, (XmlOptions)null);
            }

            /**
             * Creates a new Sex instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/serialization options to use
             * @return Sex the new Sex instance
             */
            public static Sex newInstance(final XmlOptions options) {
                return (Sex)XmlBeans.getContextTypeLoader().newInstance(Sex.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             */
            private Factory() {
            }
        }
    }
    
    /**
     * Enumeration representing the type of delivery method used during birth.
     *
     * Defines standard delivery categories used in obstetrical records:
     * <ul>
     *   <li>AVAG (Assisted Vaginal) - Vaginal delivery with assistance (forceps or vacuum)</li>
     *   <li>IND (Induced) - Labor was medically induced</li>
     *   <li>CS (Cesarean Section) - Surgical delivery via incision</li>
     *   <li>SVAG (Spontaneous Vaginal) - Unassisted vaginal delivery</li>
     *   <li>VAG (Vaginal) - General vaginal delivery</li>
     *   <li>UN (Unknown) - Delivery method not documented or unknown</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface TypeOfDelivery extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(TypeOfDelivery.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("typeofdelivery6b87elemtype");
        public static final Enum AVAG = Enum.forString("AVAG");
        public static final Enum IND = Enum.forString("IND");
        public static final Enum CS = Enum.forString("CS");
        public static final Enum SVAG = Enum.forString("SVAG");
        public static final Enum VAG = Enum.forString("VAG");
        public static final Enum UN = Enum.forString("UN");
        public static final int INT_AVAG = 1;
        public static final int INT_IND = 2;
        public static final int INT_CS = 3;
        public static final int INT_SVAG = 4;
        public static final int INT_VAG = 5;
        public static final int INT_UN = 6;

        /**
         * Gets the enumeration value.
         *
         * @return StringEnumAbstractBase the underlying enum value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the delivery type value using a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the delivery type enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        /**
         * Enumeration implementation for TypeOfDelivery values.
         *
         * Provides type-safe enumeration of delivery method categories with string and integer representations.
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_AVAG = 1;
            static final int INT_IND = 2;
            static final int INT_CS = 3;
            static final int INT_SVAG = 4;
            static final int INT_VAG = 5;
            static final int INT_UN = 6;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;

            /**
             * Returns the TypeOfDelivery.Enum constant for the given string value.
             *
             * @param s String the delivery type code ("AVAG", "IND", "CS", "SVAG", "VAG", or "UN")
             * @return Enum the corresponding TypeOfDelivery.Enum constant
             */
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }

            /**
             * Returns the TypeOfDelivery.Enum constant for the given integer value.
             *
             * @param i int the delivery type code (1=AVAG, 2=IND, 3=CS, 4=SVAG, 5=VAG, 6=UN)
             * @return Enum the corresponding TypeOfDelivery.Enum constant
             */
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }

            /**
             * Private constructor for creating TypeOfDelivery.Enum instances.
             *
             * @param s String the string representation of the delivery type
             * @param i int the integer representation of the delivery type
             */
            private Enum(final String s, final int i) {
                super(s, i);
            }

            /**
             * Resolves the deserialized object to the corresponding enum constant.
             *
             * @return Object the resolved enum constant
             */
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("AVAG", 1), new Enum("IND", 2), new Enum("CS", 3), new Enum("SVAG", 4), new Enum("VAG", 5), new Enum("UN", 6) });
            }
        }
        
        /**
         * Factory class for creating TypeOfDelivery instances.
         *
         * Provides static factory methods for instantiating TypeOfDelivery objects with or without XML options.
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new TypeOfDelivery instance from the given object.
             *
             * @param obj Object the source object to convert to TypeOfDelivery
             * @return TypeOfDelivery the new TypeOfDelivery instance
             */
            public static TypeOfDelivery newValue(final Object obj) {
                return (TypeOfDelivery)TypeOfDelivery.type.newValue(obj);
            }

            /**
             * Creates a new TypeOfDelivery instance with default XML options.
             *
             * @return TypeOfDelivery the new TypeOfDelivery instance
             */
            public static TypeOfDelivery newInstance() {
                return (TypeOfDelivery)XmlBeans.getContextTypeLoader().newInstance(TypeOfDelivery.type, (XmlOptions)null);
            }

            /**
             * Creates a new TypeOfDelivery instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/serialization options to use
             * @return TypeOfDelivery the new TypeOfDelivery instance
             */
            public static TypeOfDelivery newInstance(final XmlOptions options) {
                return (TypeOfDelivery)XmlBeans.getContextTypeLoader().newInstance(TypeOfDelivery.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             */
            private Factory() {
            }
        }
    }
    
    /**
     * Factory class for creating and parsing ObstetricalHistoryItemList instances.
     *
     * Provides comprehensive static factory methods for:
     * <ul>
     *   <li>Creating new instances with or without XML options</li>
     *   <li>Parsing from various sources (String, File, URL, Stream, Reader, DOM Node)</li>
     *   <li>XML validation and streaming support</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new ObstetricalHistoryItemList instance with default XML options.
         *
         * @return ObstetricalHistoryItemList the new instance
         */
        public static ObstetricalHistoryItemList newInstance() {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().newInstance(ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Creates a new ObstetricalHistoryItemList instance with specified XML options.
         *
         * @param options XmlOptions the XML parsing/serialization options to use
         * @return ObstetricalHistoryItemList the new instance
         */
        public static ObstetricalHistoryItemList newInstance(final XmlOptions options) {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().newInstance(ObstetricalHistoryItemList.type, options);
        }

        /**
         * Parses an XML string into an ObstetricalHistoryItemList instance.
         *
         * @param xmlAsString String the XML content as a string
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static ObstetricalHistoryItemList parse(final String xmlAsString) throws XmlException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(xmlAsString, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string into an ObstetricalHistoryItemList instance with specified options.
         *
         * @param xmlAsString String the XML content as a string
         * @param options XmlOptions the XML parsing options to use
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static ObstetricalHistoryItemList parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(xmlAsString, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Parses an XML file into an ObstetricalHistoryItemList instance.
         *
         * @param file File the XML file to parse
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if the file cannot be read
         */
        public static ObstetricalHistoryItemList parse(final File file) throws XmlException, IOException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(file, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file into an ObstetricalHistoryItemList instance with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML parsing options to use
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if the file cannot be read
         */
        public static ObstetricalHistoryItemList parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(file, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Parses XML from a URL into an ObstetricalHistoryItemList instance.
         *
         * @param u URL the URL pointing to the XML content
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if the URL cannot be accessed
         */
        public static ObstetricalHistoryItemList parse(final URL u) throws XmlException, IOException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(u, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a URL into an ObstetricalHistoryItemList instance with specified options.
         *
         * @param u URL the URL pointing to the XML content
         * @param options XmlOptions the XML parsing options to use
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if the URL cannot be accessed
         */
        public static ObstetricalHistoryItemList parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(u, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Parses XML from an InputStream into an ObstetricalHistoryItemList instance.
         *
         * @param is InputStream the input stream containing XML content
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if the stream cannot be read
         */
        public static ObstetricalHistoryItemList parse(final InputStream is) throws XmlException, IOException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(is, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an InputStream into an ObstetricalHistoryItemList instance with specified options.
         *
         * @param is InputStream the input stream containing XML content
         * @param options XmlOptions the XML parsing options to use
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if the stream cannot be read
         */
        public static ObstetricalHistoryItemList parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(is, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Parses XML from a Reader into an ObstetricalHistoryItemList instance.
         *
         * @param r Reader the reader containing XML content
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if the reader cannot be accessed
         */
        public static ObstetricalHistoryItemList parse(final Reader r) throws XmlException, IOException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(r, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a Reader into an ObstetricalHistoryItemList instance with specified options.
         *
         * @param r Reader the reader containing XML content
         * @param options XmlOptions the XML parsing options to use
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws IOException if the reader cannot be accessed
         */
        public static ObstetricalHistoryItemList parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(r, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Parses XML from an XMLStreamReader into an ObstetricalHistoryItemList instance.
         *
         * @param sr XMLStreamReader the stream reader positioned at the XML content
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static ObstetricalHistoryItemList parse(final XMLStreamReader sr) throws XmlException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(sr, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLStreamReader into an ObstetricalHistoryItemList instance with specified options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the XML content
         * @param options XmlOptions the XML parsing options to use
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static ObstetricalHistoryItemList parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(sr, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Parses XML from a DOM Node into an ObstetricalHistoryItemList instance.
         *
         * @param node Node the DOM node containing XML content
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static ObstetricalHistoryItemList parse(final Node node) throws XmlException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(node, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a DOM Node into an ObstetricalHistoryItemList instance with specified options.
         *
         * @param node Node the DOM node containing XML content
         * @param options XmlOptions the XML parsing options to use
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         */
        public static ObstetricalHistoryItemList parse(final Node node, final XmlOptions options) throws XmlException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(node, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Parses XML from an XMLInputStream into an ObstetricalHistoryItemList instance.
         *
         * @param xis XMLInputStream the XML input stream
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws XMLStreamException if there is an error processing the stream
         * @deprecated Use {@link #parse(InputStream)} or {@link #parse(XMLStreamReader)} instead
         */
        @Deprecated
        public static ObstetricalHistoryItemList parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(xis, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLInputStream into an ObstetricalHistoryItemList instance with specified options.
         *
         * @param xis XMLInputStream the XML input stream
         * @param options XmlOptions the XML parsing options to use
         * @return ObstetricalHistoryItemList the parsed instance
         * @throws XmlException if the XML is malformed or invalid
         * @throws XMLStreamException if there is an error processing the stream
         * @deprecated Use {@link #parse(InputStream, XmlOptions)} or {@link #parse(XMLStreamReader, XmlOptions)} instead
         */
        @Deprecated
        public static ObstetricalHistoryItemList parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (ObstetricalHistoryItemList)XmlBeans.getContextTypeLoader().parse(xis, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream.
         *
         * @param xis XMLInputStream the source XML input stream
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if there is an error processing the stream
         * @deprecated XMLInputStream is deprecated; use XMLStreamReader instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ObstetricalHistoryItemList.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with specified options.
         *
         * @param xis XMLInputStream the source XML input stream
         * @param options XmlOptions the XML validation options to use
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if there is an error processing the stream
         * @deprecated XMLInputStream is deprecated; use XMLStreamReader instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ObstetricalHistoryItemList.type, options);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private Factory() {
        }
    }
}
