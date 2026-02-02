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
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * XML interface for additional laboratory investigations in antenatal care records.
 *
 * <p>This interface represents the additional laboratory tests and investigations performed
 * during pregnancy as part of the British Columbia Antenatal Record (BCAR) AR2005 form.
 * It provides type-safe access to common prenatal laboratory values including hemoglobin,
 * blood typing, glucose testing, and Group B Streptococcus screening.</p>
 *
 * <p>The interface supports both standard Java getters/setters and XMLBeans-specific
 * xget/xset methods for direct XML manipulation. Custom laboratory tests can be recorded
 * using four configurable custom lab slots.</p>
 *
 * <p>This is an Apache XMLBeans-generated interface bound to the AR2005 antenatal record
 * XML schema, providing automatic XML serialization/deserialization and type safety for
 * healthcare data exchange.</p>
 *
 * @since 2026-01-24
 * @see CustomLab
 * @see XmlObject
 */
public interface AdditionalLabInvestigationsType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(AdditionalLabInvestigationsType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("additionallabinvestigationstypef776type");

    /**
     * Gets the hemoglobin (Hb) laboratory value.
     *
     * @return String the hemoglobin value, typically measured in g/L or g/dL
     */
    String getHb();

    /**
     * Gets the hemoglobin value as an XMLBeans XmlString for direct XML manipulation.
     *
     * @return XmlString the hemoglobin value wrapped in an XmlString object
     */
    XmlString xgetHb();

    /**
     * Sets the hemoglobin (Hb) laboratory value.
     *
     * @param p0 String the hemoglobin value to set, typically measured in g/L or g/dL
     */
    void setHb(final String p0);

    /**
     * Sets the hemoglobin value using an XMLBeans XmlString for direct XML manipulation.
     *
     * @param p0 XmlString the hemoglobin value to set as an XmlString object
     */
    void xsetHb(final XmlString p0);

    /**
     * Gets the patient's blood group type (ABO system).
     *
     * @return BloodGroup.Enum the blood group enumeration value (A, B, AB, O, UN, or ND)
     */
    BloodGroup.Enum getBloodGroup();

    /**
     * Gets the blood group as an XMLBeans type for direct XML manipulation.
     *
     * @return BloodGroup the blood group wrapped in an XMLBeans type
     */
    BloodGroup xgetBloodGroup();

    /**
     * Sets the patient's blood group type (ABO system).
     *
     * @param p0 BloodGroup.Enum the blood group enumeration value to set (A, B, AB, O, UN, or ND)
     */
    void setBloodGroup(final BloodGroup.Enum p0);

    /**
     * Sets the blood group using an XMLBeans type for direct XML manipulation.
     *
     * @param p0 BloodGroup the blood group to set as an XMLBeans type
     */
    void xsetBloodGroup(final BloodGroup p0);

    /**
     * Gets the patient's Rh factor (Rhesus blood group system).
     *
     * @return Rh.Enum the Rh factor enumeration value (POS, WPOS, NEG, NDONE, or UNK)
     */
    Rh.Enum getRh();

    /**
     * Gets the Rh factor as an XMLBeans type for direct XML manipulation.
     *
     * @return Rh the Rh factor wrapped in an XMLBeans type
     */
    Rh xgetRh();

    /**
     * Sets the patient's Rh factor (Rhesus blood group system).
     *
     * @param p0 Rh.Enum the Rh factor enumeration value to set (POS, WPOS, NEG, NDONE, or UNK)
     */
    void setRh(final Rh.Enum p0);

    /**
     * Sets the Rh factor using an XMLBeans type for direct XML manipulation.
     *
     * @param p0 Rh the Rh factor to set as an XMLBeans type
     */
    void xsetRh(final Rh p0);

    /**
     * Gets the repeat antibody screen (ABS) laboratory value.
     *
     * @return String the repeat antibody screen result
     */
    String getRepeatABS();

    /**
     * Gets the repeat antibody screen value as an XMLBeans XmlString for direct XML manipulation.
     *
     * @return XmlString the repeat antibody screen value wrapped in an XmlString object
     */
    XmlString xgetRepeatABS();

    /**
     * Sets the repeat antibody screen (ABS) laboratory value.
     *
     * @param p0 String the repeat antibody screen result to set
     */
    void setRepeatABS(final String p0);

    /**
     * Sets the repeat antibody screen value using an XMLBeans XmlString for direct XML manipulation.
     *
     * @param p0 XmlString the repeat antibody screen value to set as an XmlString object
     */
    void xsetRepeatABS(final XmlString p0);

    /**
     * Gets the glucose challenge test (GCT) result for gestational diabetes screening.
     *
     * @return String the GCT result value, typically measured in mmol/L or mg/dL
     */
    String getGCT();

    /**
     * Gets the GCT result as an XMLBeans XmlString for direct XML manipulation.
     *
     * @return XmlString the GCT result value wrapped in an XmlString object
     */
    XmlString xgetGCT();

    /**
     * Sets the glucose challenge test (GCT) result for gestational diabetes screening.
     *
     * @param p0 String the GCT result value to set, typically measured in mmol/L or mg/dL
     */
    void setGCT(final String p0);

    /**
     * Sets the GCT result using an XMLBeans XmlString for direct XML manipulation.
     *
     * @param p0 XmlString the GCT result value to set as an XmlString object
     */
    void xsetGCT(final XmlString p0);

    /**
     * Gets the glucose tolerance test (GTT) result for gestational diabetes diagnosis.
     *
     * @return String the GTT result value, typically measured in mmol/L or mg/dL
     */
    String getGTT();

    /**
     * Gets the GTT result as an XMLBeans XmlString for direct XML manipulation.
     *
     * @return XmlString the GTT result value wrapped in an XmlString object
     */
    XmlString xgetGTT();

    /**
     * Sets the glucose tolerance test (GTT) result for gestational diabetes diagnosis.
     *
     * @param p0 String the GTT result value to set, typically measured in mmol/L or mg/dL
     */
    void setGTT(final String p0);

    /**
     * Sets the GTT result using an XMLBeans XmlString for direct XML manipulation.
     *
     * @param p0 XmlString the GTT result value to set as an XmlString object
     */
    void xsetGTT(final XmlString p0);

    /**
     * Gets the Group B Streptococcus (GBS) screening result.
     *
     * @return GBS.Enum the GBS screening result enumeration value (NDONE, POSSWAB, POSURINE, NEGSWAB, DONEUNK, or UNK)
     */
    GBS.Enum getGBS();

    /**
     * Gets the GBS screening result as an XMLBeans type for direct XML manipulation.
     *
     * @return GBS the GBS screening result wrapped in an XMLBeans type
     */
    GBS xgetGBS();

    /**
     * Sets the Group B Streptococcus (GBS) screening result.
     *
     * @param p0 GBS.Enum the GBS screening result enumeration value to set (NDONE, POSSWAB, POSURINE, NEGSWAB, DONEUNK, or UNK)
     */
    void setGBS(final GBS.Enum p0);

    /**
     * Sets the GBS screening result using an XMLBeans type for direct XML manipulation.
     *
     * @param p0 GBS the GBS screening result to set as an XMLBeans type
     */
    void xsetGBS(final GBS p0);

    /**
     * Gets the first custom laboratory investigation entry.
     *
     * @return CustomLab the first custom lab investigation object
     */
    CustomLab getCustomLab1();

    /**
     * Sets the first custom laboratory investigation entry.
     *
     * @param p0 CustomLab the custom lab investigation object to set
     */
    void setCustomLab1(final CustomLab p0);

    /**
     * Adds and returns a new first custom laboratory investigation entry.
     *
     * @return CustomLab a new custom lab investigation object
     */
    CustomLab addNewCustomLab1();

    /**
     * Gets the second custom laboratory investigation entry.
     *
     * @return CustomLab the second custom lab investigation object
     */
    CustomLab getCustomLab2();

    /**
     * Sets the second custom laboratory investigation entry.
     *
     * @param p0 CustomLab the custom lab investigation object to set
     */
    void setCustomLab2(final CustomLab p0);

    /**
     * Adds and returns a new second custom laboratory investigation entry.
     *
     * @return CustomLab a new custom lab investigation object
     */
    CustomLab addNewCustomLab2();

    /**
     * Gets the third custom laboratory investigation entry.
     *
     * @return CustomLab the third custom lab investigation object
     */
    CustomLab getCustomLab3();

    /**
     * Sets the third custom laboratory investigation entry.
     *
     * @param p0 CustomLab the custom lab investigation object to set
     */
    void setCustomLab3(final CustomLab p0);

    /**
     * Adds and returns a new third custom laboratory investigation entry.
     *
     * @return CustomLab a new custom lab investigation object
     */
    CustomLab addNewCustomLab3();

    /**
     * Gets the fourth custom laboratory investigation entry.
     *
     * @return CustomLab the fourth custom lab investigation object
     */
    CustomLab getCustomLab4();

    /**
     * Sets the fourth custom laboratory investigation entry.
     *
     * @param p0 CustomLab the custom lab investigation object to set
     */
    void setCustomLab4(final CustomLab p0);

    /**
     * Adds and returns a new fourth custom laboratory investigation entry.
     *
     * @return CustomLab a new custom lab investigation object
     */
    CustomLab addNewCustomLab4();

    /**
     * XML interface for blood group (ABO system) enumeration values.
     *
     * <p>Represents the patient's blood type in the ABO blood group system.
     * This interface provides type-safe access to the standard blood group values
     * used in prenatal care and blood transfusion compatibility.</p>
     *
     * <p>Valid blood group values:</p>
     * <ul>
     *   <li>A - Blood type A</li>
     *   <li>B - Blood type B</li>
     *   <li>AB - Blood type AB</li>
     *   <li>O - Blood type O</li>
     *   <li>UN - Unknown blood type</li>
     *   <li>ND - Not Done (test not performed)</li>
     * </ul>
     *
     * @since 2026-01-24
     * @see XmlString
     */
    public interface BloodGroup extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(BloodGroup.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("bloodgroup51f7elemtype");
        public static final Enum A = Enum.forString("A");
        public static final Enum B = Enum.forString("B");
        public static final Enum AB = Enum.forString("AB");
        public static final Enum O = Enum.forString("O");
        public static final Enum UN = Enum.forString("UN");
        public static final Enum ND = Enum.forString("ND");
        public static final int INT_A = 1;
        public static final int INT_B = 2;
        public static final int INT_AB = 3;
        public static final int INT_O = 4;
        public static final int INT_UN = 5;
        public static final int INT_ND = 6;

        /**
         * Gets the underlying enum value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the blood group enum value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the blood group value using a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the blood group enum value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        /**
         * Enumeration class for blood group values.
         *
         * <p>This class provides type-safe enum representation for blood group values
         * with string and integer representations for XML serialization.</p>
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_A = 1;
            static final int INT_B = 2;
            static final int INT_AB = 3;
            static final int INT_O = 4;
            static final int INT_UN = 5;
            static final int INT_ND = 6;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;

            /**
             * Returns the enum constant for the given string value.
             *
             * @param s String the blood group string value (e.g., "A", "B", "AB", "O", "UN", "ND")
             * @return Enum the corresponding blood group enum constant
             */
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }

            /**
             * Returns the enum constant for the given integer value.
             *
             * @param i int the blood group integer value (1-6)
             * @return Enum the corresponding blood group enum constant
             */
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }

            /**
             * Private constructor for enum instances.
             *
             * @param s String the blood group string value
             * @param i int the blood group integer value
             */
            private Enum(final String s, final int i) {
                super(s, i);
            }

            /**
             * Resolves the enum instance during deserialization.
             *
             * @return Object the resolved enum instance
             */
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("A", 1), new Enum("B", 2), new Enum("AB", 3), new Enum("O", 4), new Enum("UN", 5), new Enum("ND", 6) });
            }
        }

        /**
         * Factory class for creating BloodGroup instances.
         *
         * <p>Provides static factory methods for creating and instantiating
         * BloodGroup objects with or without XMLBeans options.</p>
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new BloodGroup value from the given object.
             *
             * @param obj Object the object to convert to a BloodGroup value
             * @return BloodGroup the created BloodGroup instance
             */
            public static BloodGroup newValue(final Object obj) {
                return (BloodGroup)BloodGroup.type.newValue(obj);
            }

            /**
             * Creates a new BloodGroup instance with default XML options.
             *
             * @return BloodGroup a new BloodGroup instance
             */
            public static BloodGroup newInstance() {
                return (BloodGroup)XmlBeans.getContextTypeLoader().newInstance(BloodGroup.type, (XmlOptions)null);
            }

            /**
             * Creates a new BloodGroup instance with the specified XML options.
             *
             * @param options XmlOptions the XML options to use for instance creation
             * @return BloodGroup a new BloodGroup instance
             */
            public static BloodGroup newInstance(final XmlOptions options) {
                return (BloodGroup)XmlBeans.getContextTypeLoader().newInstance(BloodGroup.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             */
            private Factory() {
            }
        }
    }

    /**
     * XML interface for Rh factor (Rhesus blood group system) enumeration values.
     *
     * <p>Represents the patient's Rh factor status, a critical component of blood typing
     * that indicates the presence or absence of the RhD antigen. Rh factor is essential
     * for preventing hemolytic disease in pregnancy and ensuring blood transfusion compatibility.</p>
     *
     * <p>Valid Rh factor values:</p>
     * <ul>
     *   <li>POS - Rh positive (RhD antigen present)</li>
     *   <li>WPOS - Weak positive (weak RhD antigen expression)</li>
     *   <li>NEG - Rh negative (RhD antigen absent)</li>
     *   <li>NDONE - Not Done (test not performed)</li>
     *   <li>UNK - Unknown Rh factor</li>
     * </ul>
     *
     * @since 2026-01-24
     * @see XmlString
     */
    public interface Rh extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Rh.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("rh52c8elemtype");
        public static final Enum POS = Enum.forString("POS");
        public static final Enum WPOS = Enum.forString("WPOS");
        public static final Enum NEG = Enum.forString("NEG");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_POS = 1;
        public static final int INT_WPOS = 2;
        public static final int INT_NEG = 3;
        public static final int INT_NDONE = 4;
        public static final int INT_UNK = 5;

        /**
         * Gets the underlying enum value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the Rh factor enum value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the Rh factor value using a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the Rh factor enum value to set
         */
        void set(final StringEnumAbstractBase p0);

        /**
         * Enumeration class for Rh factor values.
         *
         * <p>This class provides type-safe enum representation for Rh factor values
         * with string and integer representations for XML serialization.</p>
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_POS = 1;
            static final int INT_WPOS = 2;
            static final int INT_NEG = 3;
            static final int INT_NDONE = 4;
            static final int INT_UNK = 5;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;

            /**
             * Returns the enum constant for the given string value.
             *
             * @param s String the Rh factor string value (e.g., "POS", "WPOS", "NEG", "NDONE", "UNK")
             * @return Enum the corresponding Rh factor enum constant
             */
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }

            /**
             * Returns the enum constant for the given integer value.
             *
             * @param i int the Rh factor integer value (1-5)
             * @return Enum the corresponding Rh factor enum constant
             */
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }

            /**
             * Private constructor for enum instances.
             *
             * @param s String the Rh factor string value
             * @param i int the Rh factor integer value
             */
            private Enum(final String s, final int i) {
                super(s, i);
            }

            /**
             * Resolves the enum instance during deserialization.
             *
             * @return Object the resolved enum instance
             */
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("POS", 1), new Enum("WPOS", 2), new Enum("NEG", 3), new Enum("NDONE", 4), new Enum("UNK", 5) });
            }
        }

        /**
         * Factory class for creating Rh instances.
         *
         * <p>Provides static factory methods for creating and instantiating
         * Rh objects with or without XMLBeans options.</p>
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new Rh value from the given object.
             *
             * @param obj Object the object to convert to an Rh value
             * @return Rh the created Rh instance
             */
            public static Rh newValue(final Object obj) {
                return (Rh)Rh.type.newValue(obj);
            }

            /**
             * Creates a new Rh instance with default XML options.
             *
             * @return Rh a new Rh instance
             */
            public static Rh newInstance() {
                return (Rh)XmlBeans.getContextTypeLoader().newInstance(Rh.type, (XmlOptions)null);
            }

            /**
             * Creates a new Rh instance with the specified XML options.
             *
             * @param options XmlOptions the XML options to use for instance creation
             * @return Rh a new Rh instance
             */
            public static Rh newInstance(final XmlOptions options) {
                return (Rh)XmlBeans.getContextTypeLoader().newInstance(Rh.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             */
            private Factory() {
            }
        }
    }

    /**
     * XML interface for Group B Streptococcus (GBS) screening result enumeration values.
     *
     * <p>Represents the result of Group B Streptococcus screening performed during pregnancy.
     * GBS screening is critical in prenatal care as it helps identify carriers who require
     * intrapartum antibiotic prophylaxis to prevent neonatal GBS infection.</p>
     *
     * <p>Valid GBS screening result values:</p>
     * <ul>
     *   <li>NDONE - Not Done (screening not performed)</li>
     *   <li>POSSWAB - Positive swab result (GBS detected via vaginal/rectal swab)</li>
     *   <li>POSURINE - Positive urine result (GBS detected in urine culture)</li>
     *   <li>NEGSWAB - Negative swab result (GBS not detected)</li>
     *   <li>DONEUNK - Done but unknown result</li>
     *   <li>UNK - Unknown screening status</li>
     * </ul>
     *
     * @since 2026-01-24
     * @see XmlString
     */
    public interface GBS extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(GBS.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("gbs77baelemtype");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum POSSWAB = Enum.forString("POSSWAB");
        public static final Enum POSURINE = Enum.forString("POSURINE");
        public static final Enum NEGSWAB = Enum.forString("NEGSWAB");
        public static final Enum DONEUNK = Enum.forString("DONEUNK");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_NDONE = 1;
        public static final int INT_POSSWAB = 2;
        public static final int INT_POSURINE = 3;
        public static final int INT_NEGSWAB = 4;
        public static final int INT_DONEUNK = 5;
        public static final int INT_UNK = 6;

        /**
         * Gets the underlying enum value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the GBS screening result enum value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the GBS screening result value using a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the GBS screening result enum value to set
         */
        void set(final StringEnumAbstractBase p0);

        /**
         * Enumeration class for GBS screening result values.
         *
         * <p>This class provides type-safe enum representation for GBS screening results
         * with string and integer representations for XML serialization.</p>
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_NDONE = 1;
            static final int INT_POSSWAB = 2;
            static final int INT_POSURINE = 3;
            static final int INT_NEGSWAB = 4;
            static final int INT_DONEUNK = 5;
            static final int INT_UNK = 6;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;

            /**
             * Returns the enum constant for the given string value.
             *
             * @param s String the GBS screening result string value (e.g., "NDONE", "POSSWAB", "POSURINE", "NEGSWAB", "DONEUNK", "UNK")
             * @return Enum the corresponding GBS screening result enum constant
             */
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }

            /**
             * Returns the enum constant for the given integer value.
             *
             * @param i int the GBS screening result integer value (1-6)
             * @return Enum the corresponding GBS screening result enum constant
             */
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }

            /**
             * Private constructor for enum instances.
             *
             * @param s String the GBS screening result string value
             * @param i int the GBS screening result integer value
             */
            private Enum(final String s, final int i) {
                super(s, i);
            }

            /**
             * Resolves the enum instance during deserialization.
             *
             * @return Object the resolved enum instance
             */
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("NDONE", 1), new Enum("POSSWAB", 2), new Enum("POSURINE", 3), new Enum("NEGSWAB", 4), new Enum("DONEUNK", 5), new Enum("UNK", 6) });
            }
        }

        /**
         * Factory class for creating GBS instances.
         *
         * <p>Provides static factory methods for creating and instantiating
         * GBS objects with or without XMLBeans options.</p>
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new GBS value from the given object.
             *
             * @param obj Object the object to convert to a GBS value
             * @return GBS the created GBS instance
             */
            public static GBS newValue(final Object obj) {
                return (GBS)GBS.type.newValue(obj);
            }

            /**
             * Creates a new GBS instance with default XML options.
             *
             * @return GBS a new GBS instance
             */
            public static GBS newInstance() {
                return (GBS)XmlBeans.getContextTypeLoader().newInstance(GBS.type, (XmlOptions)null);
            }

            /**
             * Creates a new GBS instance with the specified XML options.
             *
             * @param options XmlOptions the XML options to use for instance creation
             * @return GBS a new GBS instance
             */
            public static GBS newInstance(final XmlOptions options) {
                return (GBS)XmlBeans.getContextTypeLoader().newInstance(GBS.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             */
            private Factory() {
            }
        }
    }

    /**
     * Factory class for creating and parsing AdditionalLabInvestigationsType instances.
     *
     * <p>Provides comprehensive static factory methods for creating new instances and
     * parsing XML data from various sources (String, File, URL, InputStream, Reader,
     * XMLStreamReader, DOM Node, XMLInputStream).</p>
     *
     * <p>All parse methods support optional XmlOptions for controlling the parsing
     * behavior, including validation, error handling, and namespace processing.</p>
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new AdditionalLabInvestigationsType instance with default XML options.
         *
         * @return AdditionalLabInvestigationsType a new instance
         */
        public static AdditionalLabInvestigationsType newInstance() {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().newInstance(AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Creates a new AdditionalLabInvestigationsType instance with the specified XML options.
         *
         * @param options XmlOptions the XML options to use for instance creation
         * @return AdditionalLabInvestigationsType a new instance
         */
        public static AdditionalLabInvestigationsType newInstance(final XmlOptions options) {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().newInstance(AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an XML string with default options.
         *
         * @param xmlAsString String the XML content as a string
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AdditionalLabInvestigationsType parse(final String xmlAsString) throws XmlException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(xmlAsString, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an XML string with the specified options.
         *
         * @param xmlAsString String the XML content as a string
         * @param options XmlOptions the XML options to use for parsing
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AdditionalLabInvestigationsType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(xmlAsString, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an XML file with default options.
         *
         * @param file File the XML file to parse
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading the file
         */
        public static AdditionalLabInvestigationsType parse(final File file) throws XmlException, IOException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(file, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an XML file with the specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading the file
         */
        public static AdditionalLabInvestigationsType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(file, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from a URL with default options.
         *
         * @param u URL the URL to the XML resource
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading from the URL
         */
        public static AdditionalLabInvestigationsType parse(final URL u) throws XmlException, IOException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(u, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from a URL with the specified options.
         *
         * @param u URL the URL to the XML resource
         * @param options XmlOptions the XML options to use for parsing
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading from the URL
         */
        public static AdditionalLabInvestigationsType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(u, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an InputStream with default options.
         *
         * @param is InputStream the input stream containing XML data
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading from the stream
         */
        public static AdditionalLabInvestigationsType parse(final InputStream is) throws XmlException, IOException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(is, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an InputStream with the specified options.
         *
         * @param is InputStream the input stream containing XML data
         * @param options XmlOptions the XML options to use for parsing
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading from the stream
         */
        public static AdditionalLabInvestigationsType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(is, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from a Reader with default options.
         *
         * @param r Reader the reader containing XML data
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading
         */
        public static AdditionalLabInvestigationsType parse(final Reader r) throws XmlException, IOException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(r, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from a Reader with the specified options.
         *
         * @param r Reader the reader containing XML data
         * @param options XmlOptions the XML options to use for parsing
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading
         */
        public static AdditionalLabInvestigationsType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(r, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an XMLStreamReader with default options.
         *
         * @param sr XMLStreamReader the XML stream reader
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AdditionalLabInvestigationsType parse(final XMLStreamReader sr) throws XmlException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(sr, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an XMLStreamReader with the specified options.
         *
         * @param sr XMLStreamReader the XML stream reader
         * @param options XmlOptions the XML options to use for parsing
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AdditionalLabInvestigationsType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(sr, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from a DOM Node with default options.
         *
         * @param node Node the DOM node containing XML data
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AdditionalLabInvestigationsType parse(final Node node) throws XmlException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(node, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from a DOM Node with the specified options.
         *
         * @param node Node the DOM node containing XML data
         * @param options XmlOptions the XML options to use for parsing
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static AdditionalLabInvestigationsType parse(final Node node, final XmlOptions options) throws XmlException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(node, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an XMLInputStream with default options.
         *
         * @param xis XMLInputStream the XML input stream
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if an error occurs while processing the XML stream
         * @deprecated XMLInputStream is deprecated; use XMLStreamReader instead
         */
        @Deprecated
        public static AdditionalLabInvestigationsType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(xis, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Parses an AdditionalLabInvestigationsType from an XMLInputStream with the specified options.
         *
         * @param xis XMLInputStream the XML input stream
         * @param options XmlOptions the XML options to use for parsing
         * @return AdditionalLabInvestigationsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if an error occurs while processing the XML stream
         * @deprecated XMLInputStream is deprecated; use XMLStreamReader instead
         */
        @Deprecated
        public static AdditionalLabInvestigationsType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (AdditionalLabInvestigationsType)XmlBeans.getContextTypeLoader().parse(xis, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with default options.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if the XML is invalid
         * @throws XMLStreamException if an error occurs while processing the XML stream
         * @deprecated XMLInputStream is deprecated; use XMLStreamReader instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, AdditionalLabInvestigationsType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with the specified options.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @param options XmlOptions the XML options to use for validation
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if the XML is invalid
         * @throws XMLStreamException if an error occurs while processing the XML stream
         * @deprecated XMLInputStream is deprecated; use XMLStreamReader instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, AdditionalLabInvestigationsType.type, options);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private Factory() {
        }
    }
}
