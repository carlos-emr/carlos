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
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * XML Type interface representing current pregnancy health indicators and risk factors
 * for the British Columbia Antenatal Record (BCAR) AR2005 form.
 *
 * <p>This interface provides structured data capture for key prenatal health assessment
 * criteria including:</p>
 * <ul>
 *   <li>Physical symptoms (bleeding, nausea)</li>
 *   <li>Behavioral risk factors (smoking, alcohol/drug use)</li>
 *   <li>Environmental and occupational risks</li>
 *   <li>Nutritional status (dietary restrictions, calcium, folate intake)</li>
 * </ul>
 *
 * <p>This is an Apache XMLBeans-generated interface that provides type-safe access to
 * XML data conforming to the BCAR AR2005 schema. All data elements support yes/no/null
 * responses to accommodate incomplete assessments during prenatal care.</p>
 *
 * <p><strong>Healthcare Context:</strong> The BCAR form is a standardized provincial
 * prenatal care record used throughout British Columbia to track pregnancy progression,
 * identify risk factors, and ensure continuity of care across multiple healthcare providers.</p>
 *
 * @see YesNoNullType
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-24
 */
public interface CurrentPregnancyType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(CurrentPregnancyType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("currentpregnancytype53a5type");

    /**
     * Gets the bleeding status indicator for the current pregnancy.
     *
     * <p>Vaginal bleeding during pregnancy can indicate potential complications
     * such as miscarriage, placental issues, or other conditions requiring
     * immediate medical assessment.</p>
     *
     * @return YesNoNullType the bleeding status (yes/no/null)
     */
    YesNoNullType getBleeding();

    /**
     * Sets the bleeding status indicator for the current pregnancy.
     *
     * @param p0 YesNoNullType the bleeding status to set (yes/no/null)
     */
    void setBleeding(final YesNoNullType p0);

    /**
     * Adds a new bleeding status indicator element.
     *
     * @return YesNoNullType a new bleeding status element
     */
    YesNoNullType addNewBleeding();

    /**
     * Gets the nausea status indicator for the current pregnancy.
     *
     * <p>Nausea and vomiting are common pregnancy symptoms, particularly in the
     * first trimester. Severe cases (hyperemesis gravidarum) may require medical
     * intervention to prevent dehydration and nutritional deficiencies.</p>
     *
     * @return YesNoNullType the nausea status (yes/no/null)
     */
    YesNoNullType getNausea();

    /**
     * Sets the nausea status indicator for the current pregnancy.
     *
     * @param p0 YesNoNullType the nausea status to set (yes/no/null)
     */
    void setNausea(final YesNoNullType p0);

    /**
     * Adds a new nausea status indicator element.
     *
     * @return YesNoNullType a new nausea status element
     */
    YesNoNullType addNewNausea();

    /**
     * Gets the smoking status indicator for the current pregnancy.
     *
     * <p>Smoking during pregnancy is associated with increased risks including
     * low birth weight, preterm birth, stillbirth, and sudden infant death syndrome (SIDS).
     * Smoking cessation support is a critical component of prenatal care.</p>
     *
     * @return YesNoNullType the smoking status (yes/no/null)
     */
    YesNoNullType getSmoking();

    /**
     * Sets the smoking status indicator for the current pregnancy.
     *
     * @param p0 YesNoNullType the smoking status to set (yes/no/null)
     */
    void setSmoking(final YesNoNullType p0);

    /**
     * Adds a new smoking status indicator element.
     *
     * @return YesNoNullType a new smoking status element
     */
    YesNoNullType addNewSmoking();

    /**
     * Gets the cigarettes per day consumption level as an enumeration value.
     *
     * <p>Quantifying smoking levels helps assess pregnancy risk and guide
     * cessation interventions. Categories include less than 10, up to 20,
     * and over 20 cigarettes per day.</p>
     *
     * @return CigsPerDay.Enum the cigarette consumption level enumeration
     */
    CigsPerDay.Enum getCigsPerDay();

    /**
     * Gets the cigarettes per day consumption level as an XmlBeans type.
     *
     * <p>This method returns the underlying XMLBeans type representation
     * for the cigarettes per day value.</p>
     *
     * @return CigsPerDay the cigarette consumption level as XmlBeans type
     */
    CigsPerDay xgetCigsPerDay();

    /**
     * Sets the cigarettes per day consumption level using an enumeration value.
     *
     * @param p0 CigsPerDay.Enum the cigarette consumption level to set
     */
    void setCigsPerDay(final CigsPerDay.Enum p0);

    /**
     * Sets the cigarettes per day consumption level using an XmlBeans type.
     *
     * @param p0 CigsPerDay the cigarette consumption level to set as XmlBeans type
     */
    void xsetCigsPerDay(final CigsPerDay p0);

    /**
     * Gets the alcohol and drug use status indicator for the current pregnancy.
     *
     * <p>Alcohol and drug use during pregnancy can cause fetal alcohol spectrum
     * disorders (FASD), developmental delays, birth defects, and neonatal abstinence
     * syndrome. Assessment enables appropriate support and referral services.</p>
     *
     * @return YesNoNullType the alcohol/drug use status (yes/no/null)
     */
    YesNoNullType getAlcoholDrugs();

    /**
     * Sets the alcohol and drug use status indicator for the current pregnancy.
     *
     * @param p0 YesNoNullType the alcohol/drug use status to set (yes/no/null)
     */
    void setAlcoholDrugs(final YesNoNullType p0);

    /**
     * Adds a new alcohol and drug use status indicator element.
     *
     * @return YesNoNullType a new alcohol/drug use status element
     */
    YesNoNullType addNewAlcoholDrugs();

    /**
     * Gets the occupational and environmental risks status indicator.
     *
     * <p>Exposure to workplace hazards (chemicals, radiation, heavy lifting) or
     * environmental toxins during pregnancy may increase risk of birth defects,
     * miscarriage, or developmental issues. Documentation supports workplace
     * accommodations and risk mitigation.</p>
     *
     * @return YesNoNullType the occupational/environmental risks status (yes/no/null)
     */
    YesNoNullType getOccEnvRisks();

    /**
     * Sets the occupational and environmental risks status indicator.
     *
     * @param p0 YesNoNullType the occupational/environmental risks status to set (yes/no/null)
     */
    void setOccEnvRisks(final YesNoNullType p0);

    /**
     * Adds a new occupational and environmental risks status indicator element.
     *
     * @return YesNoNullType a new occupational/environmental risks status element
     */
    YesNoNullType addNewOccEnvRisks();

    /**
     * Gets the dietary restrictions status indicator.
     *
     * <p>Dietary restrictions (vegetarian, vegan, religious, allergies) may impact
     * maternal and fetal nutrition during pregnancy. Assessment enables appropriate
     * dietary counseling and supplementation recommendations.</p>
     *
     * @return YesNoNullType the dietary restrictions status (yes/no/null)
     */
    YesNoNullType getDietaryRes();

    /**
     * Sets the dietary restrictions status indicator.
     *
     * @param p0 YesNoNullType the dietary restrictions status to set (yes/no/null)
     */
    void setDietaryRes(final YesNoNullType p0);

    /**
     * Adds a new dietary restrictions status indicator element.
     *
     * @return YesNoNullType a new dietary restrictions status element
     */
    YesNoNullType addNewDietaryRes();

    /**
     * Gets the calcium adequacy status indicator for the current pregnancy.
     *
     * <p>Adequate calcium intake (1000-1300 mg/day) is essential for fetal bone
     * development and maternal bone health. Inadequate intake may require
     * supplementation to prevent maternal bone loss and support fetal skeletal growth.</p>
     *
     * @return YesNoNullType the calcium adequacy status (yes/no/null)
     */
    YesNoNullType getCalciumAdequate();

    /**
     * Sets the calcium adequacy status indicator for the current pregnancy.
     *
     * @param p0 YesNoNullType the calcium adequacy status to set (yes/no/null)
     */
    void setCalciumAdequate(final YesNoNullType p0);

    /**
     * Adds a new calcium adequacy status indicator element.
     *
     * @return YesNoNullType a new calcium adequacy status element
     */
    YesNoNullType addNewCalciumAdequate();

    /**
     * Gets the folate supplementation status indicator for the current pregnancy.
     *
     * <p>Folic acid (folate) supplementation (400-800 mcg/day) before and during
     * early pregnancy significantly reduces the risk of neural tube defects such
     * as spina bifida and anencephaly. This is a critical prenatal care measure.</p>
     *
     * @return YesNoNullType the folate supplementation status (yes/no/null)
     */
    YesNoNullType getFolate();

    /**
     * Sets the folate supplementation status indicator for the current pregnancy.
     *
     * @param p0 YesNoNullType the folate supplementation status to set (yes/no/null)
     */
    void setFolate(final YesNoNullType p0);

    /**
     * Adds a new folate supplementation status indicator element.
     *
     * @return YesNoNullType a new folate supplementation status element
     */
    YesNoNullType addNewFolate();

    /**
     * XML Type interface for cigarettes per day consumption levels.
     *
     * <p>Defines enumerated values for categorizing smoking intensity during pregnancy:</p>
     * <ul>
     *   <li>Empty/null - No data or not applicable</li>
     *   <li>LESS10 - Less than 10 cigarettes per day (light smoking)</li>
     *   <li>UP20 - 10-20 cigarettes per day (moderate smoking)</li>
     *   <li>OVER20 - More than 20 cigarettes per day (heavy smoking)</li>
     * </ul>
     *
     * <p>These categories align with clinical risk stratification where higher
     * consumption levels correlate with increased pregnancy complications and
     * inform the intensity of smoking cessation interventions.</p>
     *
     * @see CurrentPregnancyType#getSmoking()
     * @see CurrentPregnancyType#getCigsPerDay()
     * @since 2026-01-24
     */
    public interface CigsPerDay extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(CigsPerDay.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("cigsperday4d38elemtype");
        public static final Enum X = Enum.forString("");
        public static final Enum LESS_10 = Enum.forString("LESS10");
        public static final Enum UP_20 = Enum.forString("UP20");
        public static final Enum OVER_20 = Enum.forString("OVER20");
        public static final int INT_X = 1;
        public static final int INT_LESS_10 = 2;
        public static final int INT_UP_20 = 3;
        public static final int INT_OVER_20 = 4;

        /**
         * Gets the current enumeration value.
         *
         * @return StringEnumAbstractBase the current enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);

        /**
         * Enumeration implementation for cigarettes per day consumption levels.
         *
         * <p>This class provides the concrete enumeration implementation for
         * smoking intensity categories used in pregnancy risk assessment.</p>
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_X = 1;
            static final int INT_LESS_10 = 2;
            static final int INT_UP_20 = 3;
            static final int INT_OVER_20 = 4;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;

            /**
             * Returns the enumeration value for the given string representation.
             *
             * @param s String the string representation (e.g., "LESS10", "UP20", "OVER20")
             * @return Enum the corresponding enumeration value, or null if not found
             */
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }

            /**
             * Returns the enumeration value for the given integer code.
             *
             * @param i int the integer code (1=empty, 2=LESS10, 3=UP20, 4=OVER20)
             * @return Enum the corresponding enumeration value, or null if not found
             */
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }

            /**
             * Private constructor for creating enumeration instances.
             *
             * @param s String the string representation
             * @param i int the integer code
             */
            private Enum(final String s, final int i) {
                super(s, i);
            }

            /**
             * Ensures deserialization returns the canonical enumeration instance.
             *
             * @return Object the canonical enumeration instance for this value
             */
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("", 1), new Enum("LESS10", 2), new Enum("UP20", 3), new Enum("OVER20", 4) });
            }
        }

        /**
         * Factory class for creating CigsPerDay instances.
         *
         * <p>Provides static factory methods for instantiating CigsPerDay objects
         * from various sources (values, XML options).</p>
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new CigsPerDay instance from the given object value.
             *
             * @param obj Object the value to convert to CigsPerDay
             * @return CigsPerDay a new CigsPerDay instance
             */
            public static CigsPerDay newValue(final Object obj) {
                return (CigsPerDay)CigsPerDay.type.newValue(obj);
            }

            /**
             * Creates a new CigsPerDay instance with default options.
             *
             * @return CigsPerDay a new CigsPerDay instance
             */
            public static CigsPerDay newInstance() {
                return (CigsPerDay)XmlBeans.getContextTypeLoader().newInstance(CigsPerDay.type, (XmlOptions)null);
            }

            /**
             * Creates a new CigsPerDay instance with the specified XML options.
             *
             * @param options XmlOptions the XML options to use during creation
             * @return CigsPerDay a new CigsPerDay instance
             */
            public static CigsPerDay newInstance(final XmlOptions options) {
                return (CigsPerDay)XmlBeans.getContextTypeLoader().newInstance(CigsPerDay.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             */
            private Factory() {
            }
        }
    }

    /**
     * Factory class for creating and parsing CurrentPregnancyType instances.
     *
     * <p>Provides static factory methods for creating new instances and parsing
     * XML data from various sources (strings, files, URLs, streams, DOM nodes).</p>
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new CurrentPregnancyType instance with default options.
         *
         * @return CurrentPregnancyType a new CurrentPregnancyType instance
         */
        public static CurrentPregnancyType newInstance() {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().newInstance(CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Creates a new CurrentPregnancyType instance with the specified XML options.
         *
         * @param options XmlOptions the XML options to use during creation
         * @return CurrentPregnancyType a new CurrentPregnancyType instance
         */
        public static CurrentPregnancyType newInstance(final XmlOptions options) {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().newInstance(CurrentPregnancyType.type, options);
        }

        /**
         * Parses a CurrentPregnancyType from an XML string.
         *
         * @param xmlAsString String the XML string to parse
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         */
        public static CurrentPregnancyType parse(final String xmlAsString) throws XmlException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(xmlAsString, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Parses a CurrentPregnancyType from an XML string with specified options.
         *
         * @param xmlAsString String the XML string to parse
         * @param options XmlOptions the XML options to use during parsing
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         */
        public static CurrentPregnancyType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(xmlAsString, CurrentPregnancyType.type, options);
        }

        /**
         * Parses a CurrentPregnancyType from an XML file.
         *
         * @param file File the XML file to parse
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static CurrentPregnancyType parse(final File file) throws XmlException, IOException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(file, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Parses a CurrentPregnancyType from an XML file with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML options to use during parsing
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static CurrentPregnancyType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(file, CurrentPregnancyType.type, options);
        }

        /**
         * Parses a CurrentPregnancyType from a URL.
         *
         * @param u URL the URL to parse XML from
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws IOException if an I/O error occurs reading from the URL
         */
        public static CurrentPregnancyType parse(final URL u) throws XmlException, IOException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(u, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Parses a CurrentPregnancyType from a URL with specified options.
         *
         * @param u URL the URL to parse XML from
         * @param options XmlOptions the XML options to use during parsing
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws IOException if an I/O error occurs reading from the URL
         */
        public static CurrentPregnancyType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(u, CurrentPregnancyType.type, options);
        }

        /**
         * Parses a CurrentPregnancyType from an input stream.
         *
         * @param is InputStream the input stream to parse XML from
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static CurrentPregnancyType parse(final InputStream is) throws XmlException, IOException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(is, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Parses a CurrentPregnancyType from an input stream with specified options.
         *
         * @param is InputStream the input stream to parse XML from
         * @param options XmlOptions the XML options to use during parsing
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static CurrentPregnancyType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(is, CurrentPregnancyType.type, options);
        }

        /**
         * Parses a CurrentPregnancyType from a character reader.
         *
         * @param r Reader the reader to parse XML from
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static CurrentPregnancyType parse(final Reader r) throws XmlException, IOException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(r, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Parses a CurrentPregnancyType from a character reader with specified options.
         *
         * @param r Reader the reader to parse XML from
         * @param options XmlOptions the XML options to use during parsing
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static CurrentPregnancyType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(r, CurrentPregnancyType.type, options);
        }

        /**
         * Parses a CurrentPregnancyType from an XML stream reader.
         *
         * @param sr XMLStreamReader the stream reader to parse XML from
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         */
        public static CurrentPregnancyType parse(final XMLStreamReader sr) throws XmlException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(sr, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Parses a CurrentPregnancyType from an XML stream reader with specified options.
         *
         * @param sr XMLStreamReader the stream reader to parse XML from
         * @param options XmlOptions the XML options to use during parsing
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         */
        public static CurrentPregnancyType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(sr, CurrentPregnancyType.type, options);
        }

        /**
         * Parses a CurrentPregnancyType from a DOM node.
         *
         * @param node Node the DOM node to parse XML from
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         */
        public static CurrentPregnancyType parse(final Node node) throws XmlException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(node, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Parses a CurrentPregnancyType from a DOM node with specified options.
         *
         * @param node Node the DOM node to parse XML from
         * @param options XmlOptions the XML options to use during parsing
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         */
        public static CurrentPregnancyType parse(final Node node, final XmlOptions options) throws XmlException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(node, CurrentPregnancyType.type, options);
        }

        /**
         * Parses a CurrentPregnancyType from an XML input stream.
         *
         * @deprecated Use {@link #parse(InputStream)} instead
         * @param xis XMLInputStream the XML input stream to parse from
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws XMLStreamException if an XML stream error occurs
         */
        @Deprecated
        public static CurrentPregnancyType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(xis, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Parses a CurrentPregnancyType from an XML input stream with specified options.
         *
         * @deprecated Use {@link #parse(InputStream, XmlOptions)} instead
         * @param xis XMLInputStream the XML input stream to parse from
         * @param options XmlOptions the XML options to use during parsing
         * @return CurrentPregnancyType the parsed instance
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws XMLStreamException if an XML stream error occurs
         */
        @Deprecated
        public static CurrentPregnancyType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (CurrentPregnancyType)XmlBeans.getContextTypeLoader().parse(xis, CurrentPregnancyType.type, options);
        }

        /**
         * Creates a validating XML input stream from the given XML input stream.
         *
         * @deprecated XMLInputStream is deprecated
         * @param xis XMLInputStream the XML input stream to validate
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws XMLStreamException if an XML stream error occurs
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, CurrentPregnancyType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XML input stream from the given XML input stream with specified options.
         *
         * @deprecated XMLInputStream is deprecated
         * @param xis XMLInputStream the XML input stream to validate
         * @param options XmlOptions the XML options to use during validation
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if the XML is invalid or doesn't conform to the schema
         * @throws XMLStreamException if an XML stream error occurs
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, CurrentPregnancyType.type, options);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private Factory() {
        }
    }
}
