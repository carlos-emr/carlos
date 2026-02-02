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
import org.apache.xmlbeans.XmlFloat;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Represents the initial laboratory investigations section of the British Columbia Antenatal Record (AR2005) form.
 *
 * <p>This interface provides comprehensive access to prenatal laboratory test results including blood type,
 * infectious disease screening, and other routine prenatal investigations as required by BC healthcare standards.
 * The interface is generated from XML Schema using Apache XMLBeans and provides type-safe access to all
 * laboratory result fields.</p>
 *
 * <p>Key laboratory investigations included:</p>
 * <ul>
 *   <li>Hemoglobin (Hb) level testing</li>
 *   <li>HIV screening with counseling documentation</li>
 *   <li>Pap smear results and date tracking</li>
 *   <li>Mean Corpuscular Volume (MCV) for anemia assessment</li>
 *   <li>ABO blood type and Rh factor determination</li>
 *   <li>Antibody screening for blood incompatibility</li>
 *   <li>Gonorrhea and Chlamydia screening</li>
 *   <li>Rubella immunity status</li>
 *   <li>Urinalysis results</li>
 *   <li>Hepatitis B surface antigen (HBsAg) screening</li>
 *   <li>VDRL test for syphilis</li>
 *   <li>Sickle cell screening</li>
 *   <li>Prenatal genetic screening options</li>
 *   <li>Custom laboratory test entries</li>
 * </ul>
 *
 * <p>This interface extends {@link XmlObject} and provides both standard Java accessors and XMLBeans-specific
 * xget/xset methods for fine-grained XML manipulation. Result enumerations include standardized values
 * (POS/NEG/NDONE/UNK/IND) to ensure consistent data entry and reporting.</p>
 *
 * @see PrenatalGeneticScreeningType
 * @see CustomLab
 * @see XmlObject
 * @since 2026-01-24
 */
public interface InitialLaboratoryInvestigations extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(InitialLaboratoryInvestigations.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("initiallaboratoryinvestigations708dtype");

    /**
     * Gets the hemoglobin (Hb) test result value.
     *
     * @return String the hemoglobin result value (typically in g/L or g/dL units)
     */
    String getHbResult();

    /**
     * Gets the hemoglobin result as an XMLBeans XmlString object for advanced XML manipulation.
     *
     * @return XmlString the XML representation of the hemoglobin result
     */
    XmlString xgetHbResult();

    /**
     * Sets the hemoglobin (Hb) test result value.
     *
     * @param p0 String the hemoglobin result value to set
     */
    void setHbResult(final String p0);

    /**
     * Sets the hemoglobin result using an XMLBeans XmlString object.
     *
     * @param p0 XmlString the XML representation of the hemoglobin result
     */
    void xsetHbResult(final XmlString p0);
    
    /**
     * Gets the HIV test result enumeration value.
     *
     * @return HivResult.Enum the HIV test result (POS, NEG, IND, NDONE, or UNK)
     */
    HivResult.Enum getHivResult();

    /**
     * Gets the HIV result as an XMLBeans HivResult object for advanced XML manipulation.
     *
     * @return HivResult the XML representation of the HIV result
     */
    HivResult xgetHivResult();

    /**
     * Sets the HIV test result enumeration value.
     *
     * @param p0 HivResult.Enum the HIV test result to set (POS, NEG, IND, NDONE, or UNK)
     */
    void setHivResult(final HivResult.Enum p0);

    /**
     * Sets the HIV result using an XMLBeans HivResult object.
     *
     * @param p0 HivResult the XML representation of the HIV result
     */
    void xsetHivResult(final HivResult p0);

    /**
     * Gets whether HIV counseling was provided to the patient.
     *
     * @return boolean true if HIV counseling was provided, false otherwise
     */
    boolean getHivCounsel();

    /**
     * Gets the HIV counseling status as an XMLBeans XmlBoolean object.
     *
     * @return XmlBoolean the XML representation of the HIV counseling status
     */
    XmlBoolean xgetHivCounsel();

    /**
     * Sets whether HIV counseling was provided to the patient.
     *
     * @param p0 boolean true if HIV counseling was provided, false otherwise
     */
    void setHivCounsel(final boolean p0);

    /**
     * Sets the HIV counseling status using an XMLBeans XmlBoolean object.
     *
     * @param p0 XmlBoolean the XML representation of the HIV counseling status
     */
    void xsetHivCounsel(final XmlBoolean p0);
    
    /**
     * Gets the date of the last Pap smear test.
     *
     * @return Calendar the date of the last Pap smear, or null if not set
     */
    Calendar getLastPapDate();

    /**
     * Gets the last Pap smear date as an XMLBeans XmlDate object.
     *
     * @return XmlDate the XML representation of the last Pap smear date
     */
    XmlDate xgetLastPapDate();

    /**
     * Checks if the last Pap smear date is explicitly set to nil (XML null).
     *
     * @return boolean true if the date is nil, false otherwise
     */
    boolean isNilLastPapDate();

    /**
     * Sets the date of the last Pap smear test.
     *
     * @param p0 Calendar the date of the last Pap smear to set
     */
    void setLastPapDate(final Calendar p0);

    /**
     * Sets the last Pap smear date using an XMLBeans XmlDate object.
     *
     * @param p0 XmlDate the XML representation of the last Pap smear date
     */
    void xsetLastPapDate(final XmlDate p0);

    /**
     * Sets the last Pap smear date to nil (XML null), indicating no date is available.
     */
    void setNilLastPapDate();

    /**
     * Gets the Pap smear test result.
     *
     * @return String the Pap smear result description
     */
    String getPapResult();

    /**
     * Gets the Pap smear result as an XMLBeans XmlString object.
     *
     * @return XmlString the XML representation of the Pap smear result
     */
    XmlString xgetPapResult();

    /**
     * Sets the Pap smear test result.
     *
     * @param p0 String the Pap smear result description to set
     */
    void setPapResult(final String p0);

    /**
     * Sets the Pap smear result using an XMLBeans XmlString object.
     *
     * @param p0 XmlString the XML representation of the Pap smear result
     */
    void xsetPapResult(final XmlString p0);
    
    /**
     * Gets the Mean Corpuscular Volume (MCV) test result value.
     * MCV is used to assess red blood cell size and diagnose different types of anemia.
     *
     * @return float the MCV result value (typically in femtoliters, fL)
     */
    float getMcvResult();

    /**
     * Gets the MCV result as an XMLBeans McvResult object.
     *
     * @return McvResult the XML representation of the MCV result
     */
    McvResult xgetMcvResult();

    /**
     * Checks if the MCV result is explicitly set to nil (XML null).
     *
     * @return boolean true if the result is nil, false otherwise
     */
    boolean isNilMcvResult();

    /**
     * Sets the Mean Corpuscular Volume (MCV) test result value.
     *
     * @param p0 float the MCV result value to set (typically in femtoliters, fL)
     */
    void setMcvResult(final float p0);

    /**
     * Sets the MCV result using an XMLBeans McvResult object.
     *
     * @param p0 McvResult the XML representation of the MCV result
     */
    void xsetMcvResult(final McvResult p0);

    /**
     * Sets the MCV result to nil (XML null), indicating no result is available.
     */
    void setNilMcvResult();
    
    /**
     * Gets the ABO blood type result.
     *
     * @return AboResult.Enum the ABO blood type (A, B, AB, O, NDONE, or UNK)
     */
    AboResult.Enum getAboResult();

    /**
     * Gets the ABO blood type result as an XMLBeans AboResult object.
     *
     * @return AboResult the XML representation of the ABO blood type
     */
    AboResult xgetAboResult();

    /**
     * Sets the ABO blood type result.
     *
     * @param p0 AboResult.Enum the ABO blood type to set (A, B, AB, O, NDONE, or UNK)
     */
    void setAboResult(final AboResult.Enum p0);

    /**
     * Sets the ABO blood type result using an XMLBeans AboResult object.
     *
     * @param p0 AboResult the XML representation of the ABO blood type
     */
    void xsetAboResult(final AboResult p0);

    /**
     * Gets the Rh factor (Rhesus) test result.
     *
     * @return RhResult.Enum the Rh factor result (POS, WPOS, NEG, NDONE, or UNK)
     */
    RhResult.Enum getRhResult();

    /**
     * Gets the Rh factor result as an XMLBeans RhResult object.
     *
     * @return RhResult the XML representation of the Rh factor result
     */
    RhResult xgetRhResult();

    /**
     * Sets the Rh factor (Rhesus) test result.
     *
     * @param p0 RhResult.Enum the Rh factor result to set (POS, WPOS, NEG, NDONE, or UNK)
     */
    void setRhResult(final RhResult.Enum p0);

    /**
     * Sets the Rh factor result using an XMLBeans RhResult object.
     *
     * @param p0 RhResult the XML representation of the Rh factor result
     */
    void xsetRhResult(final RhResult p0);

    /**
     * Gets the antibody screening test result.
     * Used to detect irregular antibodies that may cause blood incompatibility issues.
     *
     * @return String the antibody screening result description
     */
    String getAntibodyResult();

    /**
     * Gets the antibody screening result as an XMLBeans XmlString object.
     *
     * @return XmlString the XML representation of the antibody screening result
     */
    XmlString xgetAntibodyResult();

    /**
     * Sets the antibody screening test result.
     *
     * @param p0 String the antibody screening result description to set
     */
    void setAntibodyResult(final String p0);

    /**
     * Sets the antibody screening result using an XMLBeans XmlString object.
     *
     * @param p0 XmlString the XML representation of the antibody screening result
     */
    void xsetAntibodyResult(final XmlString p0);
    
    /**
     * Gets the gonorrhea screening test result.
     *
     * @return GcResultGonorrhea.Enum the gonorrhea test result (POS, NEG, NDONE, or UNK)
     */
    GcResultGonorrhea.Enum getGcResultGonorrhea();

    /**
     * Gets the gonorrhea test result as an XMLBeans GcResultGonorrhea object.
     *
     * @return GcResultGonorrhea the XML representation of the gonorrhea test result
     */
    GcResultGonorrhea xgetGcResultGonorrhea();

    /**
     * Sets the gonorrhea screening test result.
     *
     * @param p0 GcResultGonorrhea.Enum the gonorrhea test result to set (POS, NEG, NDONE, or UNK)
     */
    void setGcResultGonorrhea(final GcResultGonorrhea.Enum p0);

    /**
     * Sets the gonorrhea test result using an XMLBeans GcResultGonorrhea object.
     *
     * @param p0 GcResultGonorrhea the XML representation of the gonorrhea test result
     */
    void xsetGcResultGonorrhea(final GcResultGonorrhea p0);

    /**
     * Gets the Chlamydia screening test result.
     *
     * @return GcResultChlamydia.Enum the Chlamydia test result (POS, NEG, NDONE, or UNK)
     */
    GcResultChlamydia.Enum getGcResultChlamydia();

    /**
     * Gets the Chlamydia test result as an XMLBeans GcResultChlamydia object.
     *
     * @return GcResultChlamydia the XML representation of the Chlamydia test result
     */
    GcResultChlamydia xgetGcResultChlamydia();

    /**
     * Sets the Chlamydia screening test result.
     *
     * @param p0 GcResultChlamydia.Enum the Chlamydia test result to set (POS, NEG, NDONE, or UNK)
     */
    void setGcResultChlamydia(final GcResultChlamydia.Enum p0);

    /**
     * Sets the Chlamydia test result using an XMLBeans GcResultChlamydia object.
     *
     * @param p0 GcResultChlamydia the XML representation of the Chlamydia test result
     */
    void xsetGcResultChlamydia(final GcResultChlamydia p0);
    
    /**
     * Gets the rubella immunity screening test result.
     * Rubella screening determines immunity status to prevent congenital rubella syndrome.
     *
     * @return String the rubella immunity test result description
     */
    String getRubellaResult();

    /**
     * Gets the rubella test result as an XMLBeans XmlString object.
     *
     * @return XmlString the XML representation of the rubella test result
     */
    XmlString xgetRubellaResult();

    /**
     * Sets the rubella immunity screening test result.
     *
     * @param p0 String the rubella immunity test result description to set
     */
    void setRubellaResult(final String p0);

    /**
     * Sets the rubella test result using an XMLBeans XmlString object.
     *
     * @param p0 XmlString the XML representation of the rubella test result
     */
    void xsetRubellaResult(final XmlString p0);

    /**
     * Gets the urinalysis test result.
     *
     * @return String the urinalysis result description
     */
    String getUrineResult();

    /**
     * Gets the urinalysis result as an XMLBeans XmlString object.
     *
     * @return XmlString the XML representation of the urinalysis result
     */
    XmlString xgetUrineResult();

    /**
     * Sets the urinalysis test result.
     *
     * @param p0 String the urinalysis result description to set
     */
    void setUrineResult(final String p0);

    /**
     * Sets the urinalysis result using an XMLBeans XmlString object.
     *
     * @param p0 XmlString the XML representation of the urinalysis result
     */
    void xsetUrineResult(final XmlString p0);
    
    /**
     * Gets the Hepatitis B surface antigen (HBsAg) screening test result.
     * HBsAg testing identifies active Hepatitis B infection.
     *
     * @return HbsAgResult.Enum the HBsAg test result (POS, NEG, NDONE, or UNK)
     */
    HbsAgResult.Enum getHbsAgResult();

    /**
     * Gets the HBsAg test result as an XMLBeans HbsAgResult object.
     *
     * @return HbsAgResult the XML representation of the HBsAg test result
     */
    HbsAgResult xgetHbsAgResult();

    /**
     * Sets the Hepatitis B surface antigen (HBsAg) screening test result.
     *
     * @param p0 HbsAgResult.Enum the HBsAg test result to set (POS, NEG, NDONE, or UNK)
     */
    void setHbsAgResult(final HbsAgResult.Enum p0);

    /**
     * Sets the HBsAg test result using an XMLBeans HbsAgResult object.
     *
     * @param p0 HbsAgResult the XML representation of the HBsAg test result
     */
    void xsetHbsAgResult(final HbsAgResult p0);

    /**
     * Gets the VDRL (Venereal Disease Research Laboratory) test result.
     * VDRL is a screening test for syphilis.
     *
     * @return VdrlResult.Enum the VDRL test result (POS, NEG, NDONE, or UNK)
     */
    VdrlResult.Enum getVdrlResult();

    /**
     * Gets the VDRL test result as an XMLBeans VdrlResult object.
     *
     * @return VdrlResult the XML representation of the VDRL test result
     */
    VdrlResult xgetVdrlResult();

    /**
     * Sets the VDRL (Venereal Disease Research Laboratory) test result.
     *
     * @param p0 VdrlResult.Enum the VDRL test result to set (POS, NEG, NDONE, or UNK)
     */
    void setVdrlResult(final VdrlResult.Enum p0);

    /**
     * Sets the VDRL test result using an XMLBeans VdrlResult object.
     *
     * @param p0 VdrlResult the XML representation of the VDRL test result
     */
    void xsetVdrlResult(final VdrlResult p0);

    /**
     * Gets the sickle cell screening test result.
     * Sickle cell screening identifies sickle cell trait or disease.
     *
     * @return SickleCellResult.Enum the sickle cell test result (POS, NEG, NDONE, or UNK)
     */
    SickleCellResult.Enum getSickleCellResult();

    /**
     * Gets the sickle cell test result as an XMLBeans SickleCellResult object.
     *
     * @return SickleCellResult the XML representation of the sickle cell test result
     */
    SickleCellResult xgetSickleCellResult();

    /**
     * Sets the sickle cell screening test result.
     *
     * @param p0 SickleCellResult.Enum the sickle cell test result to set (POS, NEG, NDONE, or UNK)
     */
    void setSickleCellResult(final SickleCellResult.Enum p0);

    /**
     * Sets the sickle cell test result using an XMLBeans SickleCellResult object.
     *
     * @param p0 SickleCellResult the XML representation of the sickle cell test result
     */
    void xsetSickleCellResult(final SickleCellResult p0);
    
    /**
     * Gets the prenatal genetic screening information.
     * Includes details about genetic screening tests offered during pregnancy.
     *
     * @return PrenatalGeneticScreeningType the prenatal genetic screening data
     */
    PrenatalGeneticScreeningType getPrenatalGenericScreening();

    /**
     * Sets the prenatal genetic screening information.
     *
     * @param p0 PrenatalGeneticScreeningType the prenatal genetic screening data to set
     */
    void setPrenatalGenericScreening(final PrenatalGeneticScreeningType p0);

    /**
     * Creates and adds a new prenatal genetic screening entry.
     *
     * @return PrenatalGeneticScreeningType the newly created prenatal genetic screening object
     */
    PrenatalGeneticScreeningType addNewPrenatalGenericScreening();

    /**
     * Gets the first custom laboratory test entry.
     * Allows for facility-specific or non-standard laboratory tests.
     *
     * @return CustomLab the first custom laboratory test data
     */
    CustomLab getCustomLab1();

    /**
     * Sets the first custom laboratory test entry.
     *
     * @param p0 CustomLab the first custom laboratory test data to set
     */
    void setCustomLab1(final CustomLab p0);

    /**
     * Creates and adds a new first custom laboratory test entry.
     *
     * @return CustomLab the newly created first custom laboratory test object
     */
    CustomLab addNewCustomLab1();

    /**
     * Gets the second custom laboratory test entry.
     * Allows for facility-specific or non-standard laboratory tests.
     *
     * @return CustomLab the second custom laboratory test data
     */
    CustomLab getCustomLab2();

    /**
     * Sets the second custom laboratory test entry.
     *
     * @param p0 CustomLab the second custom laboratory test data to set
     */
    void setCustomLab2(final CustomLab p0);

    /**
     * Creates and adds a new second custom laboratory test entry.
     *
     * @return CustomLab the newly created second custom laboratory test object
     */
    CustomLab addNewCustomLab2();
    
    /**
     * Enumeration interface for HIV test result values.
     *
     * <p>Defines standardized HIV test result codes used in prenatal screening:</p>
     * <ul>
     *   <li>POS - Positive result (HIV antibodies detected)</li>
     *   <li>NEG - Negative result (no HIV antibodies detected)</li>
     *   <li>IND - Indeterminate result (requires further testing)</li>
     *   <li>NDONE - Not done (test not performed)</li>
     *   <li>UNK - Unknown (result status unknown)</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface HivResult extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(HivResult.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("hivresultd08belemtype");
        public static final Enum POS = Enum.forString("POS");
        public static final Enum NEG = Enum.forString("NEG");
        public static final Enum IND = Enum.forString("IND");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_POS = 1;
        public static final int INT_NEG = 2;
        public static final int INT_IND = 3;
        public static final int INT_NDONE = 4;
        public static final int INT_UNK = 5;

        /**
         * Gets the enumeration value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value from a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);

        /**
         * Enumeration class representing HIV test result values.
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_POS = 1;
            static final int INT_NEG = 2;
            static final int INT_IND = 3;
            static final int INT_NDONE = 4;
            static final int INT_UNK = 5;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;

            /**
             * Converts a string value to the corresponding Enum constant.
             *
             * @param s String the string representation of the HIV result
             * @return Enum the corresponding HIV result enum constant
             */
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }

            /**
             * Converts an integer value to the corresponding Enum constant.
             *
             * @param i int the integer representation of the HIV result
             * @return Enum the corresponding HIV result enum constant
             */
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }

            private Enum(final String s, final int i) {
                super(s, i);
            }

            private Object readResolve() {
                return forInt(this.intValue());
            }

            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("POS", 1), new Enum("NEG", 2), new Enum("IND", 3), new Enum("NDONE", 4), new Enum("UNK", 5) });
            }
        }

        /**
         * Factory class for creating HivResult instances.
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new HivResult from an object value.
             *
             * @param obj Object the object to convert to HivResult
             * @return HivResult the created HivResult instance
             */
            public static HivResult newValue(final Object obj) {
                return (HivResult)HivResult.type.newValue(obj);
            }

            /**
             * Creates a new HivResult instance with default options.
             *
             * @return HivResult the newly created HivResult instance
             */
            public static HivResult newInstance() {
                return (HivResult)XmlBeans.getContextTypeLoader().newInstance(HivResult.type, (XmlOptions)null);
            }

            /**
             * Creates a new HivResult instance with specified XML options.
             *
             * @param options XmlOptions the XML options to use for creation
             * @return HivResult the newly created HivResult instance
             */
            public static HivResult newInstance(final XmlOptions options) {
                return (HivResult)XmlBeans.getContextTypeLoader().newInstance(HivResult.type, options);
            }

            private Factory() {
            }
        }
    }
    
    /**
     * Interface for Mean Corpuscular Volume (MCV) test result values.
     *
     * <p>MCV is a measurement of the average size of red blood cells, expressed in femtoliters (fL).
     * This test is used to classify different types of anemia and other blood disorders.</p>
     *
     * @since 2026-01-24
     */
    public interface McvResult extends XmlFloat
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(McvResult.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("mcvresult5216elemtype");

        /**
         * Factory class for creating McvResult instances.
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new McvResult from an object value.
             *
             * @param obj Object the object to convert to McvResult
             * @return McvResult the created McvResult instance
             */
            public static McvResult newValue(final Object obj) {
                return (McvResult)McvResult.type.newValue(obj);
            }

            /**
             * Creates a new McvResult instance with default options.
             *
             * @return McvResult the newly created McvResult instance
             */
            public static McvResult newInstance() {
                return (McvResult)XmlBeans.getContextTypeLoader().newInstance(McvResult.type, (XmlOptions)null);
            }

            /**
             * Creates a new McvResult instance with specified XML options.
             *
             * @param options XmlOptions the XML options to use for creation
             * @return McvResult the newly created McvResult instance
             */
            public static McvResult newInstance(final XmlOptions options) {
                return (McvResult)XmlBeans.getContextTypeLoader().newInstance(McvResult.type, options);
            }

            private Factory() {
            }
        }
    }
    
    /**
     * Enumeration interface for ABO blood type result values.
     *
     * <p>Defines standardized ABO blood group classifications:</p>
     * <ul>
     *   <li>A - Blood type A</li>
     *   <li>B - Blood type B</li>
     *   <li>AB - Blood type AB</li>
     *   <li>O - Blood type O</li>
     *   <li>NDONE - Not done (test not performed)</li>
     *   <li>UNK - Unknown (result status unknown)</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface AboResult extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(AboResult.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("aboresultefe4elemtype");
        public static final Enum A = Enum.forString("A");
        public static final Enum B = Enum.forString("B");
        public static final Enum AB = Enum.forString("AB");
        public static final Enum O = Enum.forString("O");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_A = 1;
        public static final int INT_B = 2;
        public static final int INT_AB = 3;
        public static final int INT_O = 4;
        public static final int INT_NDONE = 5;
        public static final int INT_UNK = 6;

        /**
         * Gets the enumeration value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value from a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_A = 1;
            static final int INT_B = 2;
            static final int INT_AB = 3;
            static final int INT_O = 4;
            static final int INT_NDONE = 5;
            static final int INT_UNK = 6;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;
            
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }
            
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }
            
            private Enum(final String s, final int i) {
                super(s, i);
            }
            
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("A", 1), new Enum("B", 2), new Enum("AB", 3), new Enum("O", 4), new Enum("NDONE", 5), new Enum("UNK", 6) });
            }
        }
        
        public static final class Factory
        {
            public static AboResult newValue(final Object obj) {
                return (AboResult)AboResult.type.newValue(obj);
            }
            
            public static AboResult newInstance() {
                return (AboResult)XmlBeans.getContextTypeLoader().newInstance(AboResult.type, (XmlOptions)null);
            }
            
            public static AboResult newInstance(final XmlOptions options) {
                return (AboResult)XmlBeans.getContextTypeLoader().newInstance(AboResult.type, options);
            }
            
            private Factory() {
            }
        }
    }
    
    /**
     * Enumeration interface for Rh factor (Rhesus) test result values.
     *
     * <p>Defines standardized Rh factor classifications:</p>
     * <ul>
     *   <li>POS - Positive (Rh+)</li>
     *   <li>WPOS - Weak positive (variant Rh antigen detected)</li>
     *   <li>NEG - Negative (Rh-)</li>
     *   <li>NDONE - Not done (test not performed)</li>
     *   <li>UNK - Unknown (result status unknown)</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface RhResult extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(RhResult.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("rhresultc3dcelemtype");
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
         * Gets the enumeration value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value from a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_POS = 1;
            static final int INT_WPOS = 2;
            static final int INT_NEG = 3;
            static final int INT_NDONE = 4;
            static final int INT_UNK = 5;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;
            
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }
            
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }
            
            private Enum(final String s, final int i) {
                super(s, i);
            }
            
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("POS", 1), new Enum("WPOS", 2), new Enum("NEG", 3), new Enum("NDONE", 4), new Enum("UNK", 5) });
            }
        }
        
        public static final class Factory
        {
            public static RhResult newValue(final Object obj) {
                return (RhResult)RhResult.type.newValue(obj);
            }
            
            public static RhResult newInstance() {
                return (RhResult)XmlBeans.getContextTypeLoader().newInstance(RhResult.type, (XmlOptions)null);
            }
            
            public static RhResult newInstance(final XmlOptions options) {
                return (RhResult)XmlBeans.getContextTypeLoader().newInstance(RhResult.type, options);
            }
            
            private Factory() {
            }
        }
    }
    
    /**
     * Enumeration interface for gonorrhea screening test result values.
     *
     * <p>Defines standardized gonorrhea test result codes:</p>
     * <ul>
     *   <li>POS - Positive (gonorrhea detected)</li>
     *   <li>NEG - Negative (no gonorrhea detected)</li>
     *   <li>NDONE - Not done (test not performed)</li>
     *   <li>UNK - Unknown (result status unknown)</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface GcResultGonorrhea extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(GcResultGonorrhea.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("gcresultgonorrhead61belemtype");
        public static final Enum POS = Enum.forString("POS");
        public static final Enum NEG = Enum.forString("NEG");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_POS = 1;
        public static final int INT_NEG = 2;
        public static final int INT_NDONE = 3;
        public static final int INT_UNK = 4;

        /**
         * Gets the enumeration value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value from a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_POS = 1;
            static final int INT_NEG = 2;
            static final int INT_NDONE = 3;
            static final int INT_UNK = 4;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;
            
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }
            
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }
            
            private Enum(final String s, final int i) {
                super(s, i);
            }
            
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("POS", 1), new Enum("NEG", 2), new Enum("NDONE", 3), new Enum("UNK", 4) });
            }
        }
        
        public static final class Factory
        {
            public static GcResultGonorrhea newValue(final Object obj) {
                return (GcResultGonorrhea)GcResultGonorrhea.type.newValue(obj);
            }
            
            public static GcResultGonorrhea newInstance() {
                return (GcResultGonorrhea)XmlBeans.getContextTypeLoader().newInstance(GcResultGonorrhea.type, (XmlOptions)null);
            }
            
            public static GcResultGonorrhea newInstance(final XmlOptions options) {
                return (GcResultGonorrhea)XmlBeans.getContextTypeLoader().newInstance(GcResultGonorrhea.type, options);
            }
            
            private Factory() {
            }
        }
    }
    
    /**
     * Enumeration interface for Chlamydia screening test result values.
     *
     * <p>Defines standardized Chlamydia test result codes:</p>
     * <ul>
     *   <li>POS - Positive (Chlamydia detected)</li>
     *   <li>NEG - Negative (no Chlamydia detected)</li>
     *   <li>NDONE - Not done (test not performed)</li>
     *   <li>UNK - Unknown (result status unknown)</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface GcResultChlamydia extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(GcResultChlamydia.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("gcresultchlamydiacb16elemtype");
        public static final Enum POS = Enum.forString("POS");
        public static final Enum NEG = Enum.forString("NEG");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_POS = 1;
        public static final int INT_NEG = 2;
        public static final int INT_NDONE = 3;
        public static final int INT_UNK = 4;

        /**
         * Gets the enumeration value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value from a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_POS = 1;
            static final int INT_NEG = 2;
            static final int INT_NDONE = 3;
            static final int INT_UNK = 4;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;
            
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }
            
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }
            
            private Enum(final String s, final int i) {
                super(s, i);
            }
            
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("POS", 1), new Enum("NEG", 2), new Enum("NDONE", 3), new Enum("UNK", 4) });
            }
        }
        
        public static final class Factory
        {
            public static GcResultChlamydia newValue(final Object obj) {
                return (GcResultChlamydia)GcResultChlamydia.type.newValue(obj);
            }
            
            public static GcResultChlamydia newInstance() {
                return (GcResultChlamydia)XmlBeans.getContextTypeLoader().newInstance(GcResultChlamydia.type, (XmlOptions)null);
            }
            
            public static GcResultChlamydia newInstance(final XmlOptions options) {
                return (GcResultChlamydia)XmlBeans.getContextTypeLoader().newInstance(GcResultChlamydia.type, options);
            }
            
            private Factory() {
            }
        }
    }
    
    /**
     * Enumeration interface for Hepatitis B surface antigen (HBsAg) test result values.
     *
     * <p>Defines standardized HBsAg test result codes:</p>
     * <ul>
     *   <li>POS - Positive (active Hepatitis B infection detected)</li>
     *   <li>NEG - Negative (no active Hepatitis B infection)</li>
     *   <li>NDONE - Not done (test not performed)</li>
     *   <li>UNK - Unknown (result status unknown)</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface HbsAgResult extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(HbsAgResult.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("hbsagresultd4b5elemtype");
        public static final Enum POS = Enum.forString("POS");
        public static final Enum NEG = Enum.forString("NEG");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_POS = 1;
        public static final int INT_NEG = 2;
        public static final int INT_NDONE = 3;
        public static final int INT_UNK = 4;

        /**
         * Gets the enumeration value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value from a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_POS = 1;
            static final int INT_NEG = 2;
            static final int INT_NDONE = 3;
            static final int INT_UNK = 4;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;
            
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }
            
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }
            
            private Enum(final String s, final int i) {
                super(s, i);
            }
            
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("POS", 1), new Enum("NEG", 2), new Enum("NDONE", 3), new Enum("UNK", 4) });
            }
        }
        
        public static final class Factory
        {
            public static HbsAgResult newValue(final Object obj) {
                return (HbsAgResult)HbsAgResult.type.newValue(obj);
            }
            
            public static HbsAgResult newInstance() {
                return (HbsAgResult)XmlBeans.getContextTypeLoader().newInstance(HbsAgResult.type, (XmlOptions)null);
            }
            
            public static HbsAgResult newInstance(final XmlOptions options) {
                return (HbsAgResult)XmlBeans.getContextTypeLoader().newInstance(HbsAgResult.type, options);
            }
            
            private Factory() {
            }
        }
    }
    
    /**
     * Enumeration interface for VDRL (Venereal Disease Research Laboratory) test result values.
     *
     * <p>VDRL is a screening test for syphilis. Defines standardized result codes:</p>
     * <ul>
     *   <li>POS - Positive (reactive, indicates possible syphilis infection)</li>
     *   <li>NEG - Negative (non-reactive, no syphilis detected)</li>
     *   <li>NDONE - Not done (test not performed)</li>
     *   <li>UNK - Unknown (result status unknown)</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface VdrlResult extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(VdrlResult.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("vdrlresultce0eelemtype");
        public static final Enum POS = Enum.forString("POS");
        public static final Enum NEG = Enum.forString("NEG");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_POS = 1;
        public static final int INT_NEG = 2;
        public static final int INT_NDONE = 3;
        public static final int INT_UNK = 4;

        /**
         * Gets the enumeration value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value from a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_POS = 1;
            static final int INT_NEG = 2;
            static final int INT_NDONE = 3;
            static final int INT_UNK = 4;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;
            
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }
            
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }
            
            private Enum(final String s, final int i) {
                super(s, i);
            }
            
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("POS", 1), new Enum("NEG", 2), new Enum("NDONE", 3), new Enum("UNK", 4) });
            }
        }
        
        public static final class Factory
        {
            public static VdrlResult newValue(final Object obj) {
                return (VdrlResult)VdrlResult.type.newValue(obj);
            }
            
            public static VdrlResult newInstance() {
                return (VdrlResult)XmlBeans.getContextTypeLoader().newInstance(VdrlResult.type, (XmlOptions)null);
            }
            
            public static VdrlResult newInstance(final XmlOptions options) {
                return (VdrlResult)XmlBeans.getContextTypeLoader().newInstance(VdrlResult.type, options);
            }
            
            private Factory() {
            }
        }
    }
    
    /**
     * Enumeration interface for sickle cell screening test result values.
     *
     * <p>Sickle cell screening identifies sickle cell trait or disease. Defines standardized result codes:</p>
     * <ul>
     *   <li>POS - Positive (sickle cell trait or disease detected)</li>
     *   <li>NEG - Negative (no sickle cell trait or disease)</li>
     *   <li>NDONE - Not done (test not performed)</li>
     *   <li>UNK - Unknown (result status unknown)</li>
     * </ul>
     *
     * @since 2026-01-24
     */
    public interface SickleCellResult extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(SickleCellResult.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("sicklecellresultb65felemtype");
        public static final Enum POS = Enum.forString("POS");
        public static final Enum NEG = Enum.forString("NEG");
        public static final Enum NDONE = Enum.forString("NDONE");
        public static final Enum UNK = Enum.forString("UNK");
        public static final int INT_POS = 1;
        public static final int INT_NEG = 2;
        public static final int INT_NDONE = 3;
        public static final int INT_UNK = 4;

        /**
         * Gets the enumeration value as a StringEnumAbstractBase.
         *
         * @return StringEnumAbstractBase the enumeration value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enumeration value from a StringEnumAbstractBase.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_POS = 1;
            static final int INT_NEG = 2;
            static final int INT_NDONE = 3;
            static final int INT_UNK = 4;
            public static final StringEnumAbstractBase.Table table;
            private static final long serialVersionUID = 1L;
            
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }
            
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }
            
            private Enum(final String s, final int i) {
                super(s, i);
            }
            
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("POS", 1), new Enum("NEG", 2), new Enum("NDONE", 3), new Enum("UNK", 4) });
            }
        }
        
        public static final class Factory
        {
            public static SickleCellResult newValue(final Object obj) {
                return (SickleCellResult)SickleCellResult.type.newValue(obj);
            }
            
            public static SickleCellResult newInstance() {
                return (SickleCellResult)XmlBeans.getContextTypeLoader().newInstance(SickleCellResult.type, (XmlOptions)null);
            }
            
            public static SickleCellResult newInstance(final XmlOptions options) {
                return (SickleCellResult)XmlBeans.getContextTypeLoader().newInstance(SickleCellResult.type, options);
            }
            
            private Factory() {
            }
        }
    }
    
    /**
     * Factory class for creating and parsing InitialLaboratoryInvestigations instances.
     *
     * <p>Provides methods to create new instances and parse from various sources including
     * String, File, URL, InputStream, Reader, XMLStreamReader, and DOM Node.</p>
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new InitialLaboratoryInvestigations instance with default options.
         *
         * @return InitialLaboratoryInvestigations the newly created instance
         */
        public static InitialLaboratoryInvestigations newInstance() {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().newInstance(InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Creates a new InitialLaboratoryInvestigations instance with specified XML options.
         *
         * @param options XmlOptions the XML options to use for creation
         * @return InitialLaboratoryInvestigations the newly created instance
         */
        public static InitialLaboratoryInvestigations newInstance(final XmlOptions options) {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().newInstance(InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from an XML string.
         *
         * @param xmlAsString String the XML content to parse
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static InitialLaboratoryInvestigations parse(final String xmlAsString) throws XmlException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(xmlAsString, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from an XML string with options.
         *
         * @param xmlAsString String the XML content to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static InitialLaboratoryInvestigations parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(xmlAsString, InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from a file.
         *
         * @param file File the file containing XML content to parse
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs reading the file
         */
        public static InitialLaboratoryInvestigations parse(final File file) throws XmlException, IOException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(file, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from a file with options.
         *
         * @param file File the file containing XML content to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs reading the file
         */
        public static InitialLaboratoryInvestigations parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(file, InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from a URL.
         *
         * @param u URL the URL pointing to XML content to parse
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs reading from the URL
         */
        public static InitialLaboratoryInvestigations parse(final URL u) throws XmlException, IOException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(u, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from a URL with options.
         *
         * @param u URL the URL pointing to XML content to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs reading from the URL
         */
        public static InitialLaboratoryInvestigations parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(u, InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from an InputStream.
         *
         * @param is InputStream the input stream containing XML content to parse
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static InitialLaboratoryInvestigations parse(final InputStream is) throws XmlException, IOException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(is, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from an InputStream with options.
         *
         * @param is InputStream the input stream containing XML content to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static InitialLaboratoryInvestigations parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(is, InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from a Reader.
         *
         * @param r Reader the reader providing XML content to parse
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static InitialLaboratoryInvestigations parse(final Reader r) throws XmlException, IOException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(r, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from a Reader with options.
         *
         * @param r Reader the reader providing XML content to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static InitialLaboratoryInvestigations parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(r, InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from an XMLStreamReader.
         *
         * @param sr XMLStreamReader the stream reader providing XML content to parse
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static InitialLaboratoryInvestigations parse(final XMLStreamReader sr) throws XmlException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(sr, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from an XMLStreamReader with options.
         *
         * @param sr XMLStreamReader the stream reader providing XML content to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static InitialLaboratoryInvestigations parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(sr, InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from a DOM Node.
         *
         * @param node Node the DOM node containing XML content to parse
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static InitialLaboratoryInvestigations parse(final Node node) throws XmlException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(node, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from a DOM Node with options.
         *
         * @param node Node the DOM node containing XML content to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static InitialLaboratoryInvestigations parse(final Node node, final XmlOptions options) throws XmlException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(node, InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from an XMLInputStream.
         *
         * @param xis XMLInputStream the input stream containing XML content to parse
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if an XML streaming error occurs
         * @deprecated XMLInputStream is deprecated, use parse(InputStream) or parse(XMLStreamReader) instead
         */
        @Deprecated
        public static InitialLaboratoryInvestigations parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(xis, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Parses an InitialLaboratoryInvestigations document from an XMLInputStream with options.
         *
         * @param xis XMLInputStream the input stream containing XML content to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return InitialLaboratoryInvestigations the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if an XML streaming error occurs
         * @deprecated XMLInputStream is deprecated, use parse(InputStream, XmlOptions) or parse(XMLStreamReader, XmlOptions) instead
         */
        @Deprecated
        public static InitialLaboratoryInvestigations parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (InitialLaboratoryInvestigations)XmlBeans.getContextTypeLoader().parse(xis, InitialLaboratoryInvestigations.type, options);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream.
         *
         * @param xis XMLInputStream the input stream to validate
         * @return XMLInputStream a validating input stream
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if an XML streaming error occurs
         * @deprecated XMLInputStream is deprecated, use XMLStreamReader validation instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, InitialLaboratoryInvestigations.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with options.
         *
         * @param xis XMLInputStream the input stream to validate
         * @param options XmlOptions the XML options to use for validation
         * @return XMLInputStream a validating input stream
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if an XML streaming error occurs
         * @deprecated XMLInputStream is deprecated, use XMLStreamReader validation instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, InitialLaboratoryInvestigations.type, options);
        }

        private Factory() {
        }
    }
}
