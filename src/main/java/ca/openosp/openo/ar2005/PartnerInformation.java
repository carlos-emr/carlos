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
import org.apache.xmlbeans.XmlInt;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Partner demographic information interface for the British Columbia Antenatal Record (BCAR) AR2005 form.
 *
 * <p>This interface provides comprehensive demographic and socioeconomic data collection for the patient's
 * partner during prenatal care. Partner information is critical for complete family health assessment,
 * genetic risk evaluation, and social determinants of health documentation in maternal care.</p>
 *
 * <p>The interface is generated from XML schema using Apache XMLBeans and provides type-safe access to
 * partner demographic elements including personal identification, occupation classification, educational
 * attainment, and age data. This information supports comprehensive prenatal risk assessment and care planning.</p>
 *
 * <p>Key demographic categories captured:</p>
 * <ul>
 *   <li>Personal identification (first name, last name)</li>
 *   <li>Occupation classification (standardized occupation codes OCC0005-OCC1290)</li>
 *   <li>Educational level (ED001-ED006 classification)</li>
 *   <li>Age (integer value)</li>
 * </ul>
 *
 * <p>This interface is part of the British Columbia provincial healthcare data standards for antenatal
 * record documentation and integrates with the broader BCAR forms system for comprehensive maternal health tracking.</p>
 *
 * @see org.apache.xmlbeans.XmlObject
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-24
 */
public interface PartnerInformation extends XmlObject
{
    /**
     * XMLBeans schema type for partner information.
     * Used internally by Apache XMLBeans for type resolution and validation.
     */
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(PartnerInformation.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("partnerinformationbf1btype");

    /**
     * Gets the partner's last name (surname/family name).
     *
     * @return String the partner's last name
     */
    String getLastName();

    /**
     * Gets the partner's last name as an XMLBeans XmlString object.
     * Provides access to the underlying XML type with schema validation capabilities.
     *
     * @return XmlString the partner's last name with XML type information
     */
    XmlString xgetLastName();

    /**
     * Sets the partner's last name (surname/family name).
     *
     * @param p0 String the last name to set
     */
    void setLastName(final String p0);

    /**
     * Sets the partner's last name using an XMLBeans XmlString object.
     * Allows setting with full XML type validation.
     *
     * @param p0 XmlString the last name to set with XML type information
     */
    void xsetLastName(final XmlString p0);

    /**
     * Gets the partner's first name (given name).
     *
     * @return String the partner's first name
     */
    String getFirstName();

    /**
     * Gets the partner's first name as an XMLBeans XmlString object.
     * Provides access to the underlying XML type with schema validation capabilities.
     *
     * @return XmlString the partner's first name with XML type information
     */
    XmlString xgetFirstName();

    /**
     * Sets the partner's first name (given name).
     *
     * @param p0 String the first name to set
     */
    void setFirstName(final String p0);

    /**
     * Sets the partner's first name using an XMLBeans XmlString object.
     * Allows setting with full XML type validation.
     *
     * @param p0 XmlString the first name to set with XML type information
     */
    void xsetFirstName(final XmlString p0);

    /**
     * Gets the partner's occupation classification information.
     * Includes standardized occupation code and optional free-text "other" field.
     *
     * @return Occupation the occupation data structure containing code and description
     */
    Occupation getOccupation();

    /**
     * Sets the partner's occupation classification information.
     *
     * @param p0 Occupation the occupation data to set (includes code and optional other field)
     */
    void setOccupation(final Occupation p0);

    /**
     * Creates and adds a new Occupation object to this partner information.
     * Factory method for building occupation data structures.
     *
     * @return Occupation a new occupation instance ready for population
     */
    Occupation addNewOccupation();

    /**
     * Gets the partner's education level classification enumeration value.
     * Returns one of the standardized education level codes (ED001-ED006 or UN for unknown).
     *
     * @return EducationLevel.Enum the education level classification
     */
    EducationLevel.Enum getEducationLevel();

    /**
     * Gets the partner's education level as an XMLBeans EducationLevel object.
     * Provides access to the underlying XML type with schema validation capabilities.
     *
     * @return EducationLevel the education level with XML type information
     */
    EducationLevel xgetEducationLevel();

    /**
     * Sets the partner's education level classification.
     *
     * @param p0 EducationLevel.Enum the education level code to set (ED001-ED006 or UN)
     */
    void setEducationLevel(final EducationLevel.Enum p0);

    /**
     * Sets the partner's education level using an XMLBeans EducationLevel object.
     * Allows setting with full XML type validation.
     *
     * @param p0 EducationLevel the education level to set with XML type information
     */
    void xsetEducationLevel(final EducationLevel p0);

    /**
     * Gets the partner's age in years.
     *
     * @return int the partner's age
     */
    int getAge();

    /**
     * Gets the partner's age as an XMLBeans Age object.
     * Provides access to the underlying XML type with schema validation capabilities.
     *
     * @return Age the partner's age with XML type information
     */
    Age xgetAge();

    /**
     * Sets the partner's age in years.
     *
     * @param p0 int the age to set
     */
    void setAge(final int p0);

    /**
     * Sets the partner's age using an XMLBeans Age object.
     * Allows setting with full XML type validation.
     *
     * @param p0 Age the age to set with XML type information
     */
    void xsetAge(final Age p0);

    /**
     * Occupation classification interface for partner demographic information.
     *
     * <p>Provides standardized occupation coding for the patient's partner using a comprehensive
     * classification system with 259 defined occupation categories (OCC0005-OCC1290) plus an "OTHER"
     * option for occupations not covered by the standard codes.</p>
     *
     * <p>Occupation data is important for:</p>
     * <ul>
     *   <li>Socioeconomic status assessment in prenatal care</li>
     *   <li>Environmental and occupational exposure risk evaluation</li>
     *   <li>Social determinants of health documentation</li>
     *   <li>Family support and resource planning</li>
     * </ul>
     *
     * <p>When the partner's occupation does not match any of the 259 predefined codes, the "OTHER"
     * enumeration value should be selected and the specific occupation described in the "other" free-text field.</p>
     *
     * @see PartnerInformation
     * @since 2026-01-24
     */
    public interface Occupation extends XmlObject
    {
        /**
         * XMLBeans schema type for occupation classification.
         * Used internally by Apache XMLBeans for type resolution and validation.
         */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Occupation.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("occupation6914elemtype");

        /**
         * Gets the standardized occupation classification code enumeration value.
         * Returns one of 259 occupation codes (OCC0005-OCC1290) or OTHER.
         *
         * @return Value.Enum the occupation classification code
         */
        Value.Enum getValue();

        /**
         * Gets the occupation classification code as an XMLBeans Value object.
         * Provides access to the underlying XML type with schema validation capabilities.
         *
         * @return Value the occupation code with XML type information
         */
        Value xgetValue();

        /**
         * Sets the standardized occupation classification code.
         *
         * @param p0 Value.Enum the occupation code to set (OCC0005-OCC1290 or OTHER)
         */
        void setValue(final Value.Enum p0);

        /**
         * Sets the occupation classification code using an XMLBeans Value object.
         * Allows setting with full XML type validation.
         *
         * @param p0 Value the occupation code to set with XML type information
         */
        void xsetValue(final Value p0);

        /**
         * Gets the free-text occupation description for non-standard occupations.
         * Used when the occupation value is set to OTHER to provide specific occupation details.
         *
         * @return String the free-text occupation description, or null if not applicable
         */
        String getOther();

        /**
         * Gets the free-text occupation description as an XMLBeans XmlString object.
         * Provides access to the underlying XML type with schema validation capabilities.
         *
         * @return XmlString the occupation description with XML type information
         */
        XmlString xgetOther();

        /**
         * Sets the free-text occupation description for non-standard occupations.
         * Should be populated when the occupation value is set to OTHER.
         *
         * @param p0 String the occupation description to set
         */
        void setOther(final String p0);

        /**
         * Sets the free-text occupation description using an XMLBeans XmlString object.
         * Allows setting with full XML type validation.
         *
         * @param p0 XmlString the occupation description to set with XML type information
         */
        void xsetOther(final XmlString p0);

        /**
         * Standardized occupation classification code enumeration for partner demographics.
         *
         * <p>Defines 259 distinct occupation categories using codes OCC0005 through OCC1290 (incremented by 5),
         * plus an "OTHER" category for occupations not covered by the standard classification system.</p>
         *
         * <p>Each occupation code maps to both an enumeration constant and an integer value for database
         * storage and data interchange. The occupation classification system provides granular categorization
         * of employment types for socioeconomic and occupational health risk assessment in maternal care.</p>
         *
         * <p>The enumeration provides type-safe occupation code handling with built-in validation through
         * the XMLBeans framework, ensuring data integrity in BCAR form submissions.</p>
         *
         * @see Occupation
         * @since 2026-01-24
         */
        public interface Value extends XmlString
        {
            /**
             * XMLBeans schema type for occupation value enumeration.
             * Used internally by Apache XMLBeans for type resolution and validation.
             */
            public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Value.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("value0f31elemtype");
            public static final Enum OCC_0005 = Enum.forString("OCC0005");
            public static final Enum OCC_0010 = Enum.forString("OCC0010");
            public static final Enum OCC_0015 = Enum.forString("OCC0015");
            public static final Enum OCC_0020 = Enum.forString("OCC0020");
            public static final Enum OCC_0025 = Enum.forString("OCC0025");
            public static final Enum OCC_0030 = Enum.forString("OCC0030");
            public static final Enum OCC_0035 = Enum.forString("OCC0035");
            public static final Enum OCC_0040 = Enum.forString("OCC0040");
            public static final Enum OCC_0045 = Enum.forString("OCC0045");
            public static final Enum OCC_0050 = Enum.forString("OCC0050");
            public static final Enum OCC_0055 = Enum.forString("OCC0055");
            public static final Enum OCC_0060 = Enum.forString("OCC0060");
            public static final Enum OCC_0065 = Enum.forString("OCC0065");
            public static final Enum OCC_0070 = Enum.forString("OCC0070");
            public static final Enum OCC_0075 = Enum.forString("OCC0075");
            public static final Enum OCC_0080 = Enum.forString("OCC0080");
            public static final Enum OCC_0085 = Enum.forString("OCC0085");
            public static final Enum OCC_0090 = Enum.forString("OCC0090");
            public static final Enum OCC_0095 = Enum.forString("OCC0095");
            public static final Enum OCC_0100 = Enum.forString("OCC0100");
            public static final Enum OCC_0105 = Enum.forString("OCC0105");
            public static final Enum OCC_0110 = Enum.forString("OCC0110");
            public static final Enum OCC_0115 = Enum.forString("OCC0115");
            public static final Enum OCC_0120 = Enum.forString("OCC0120");
            public static final Enum OCC_0125 = Enum.forString("OCC0125");
            public static final Enum OCC_0130 = Enum.forString("OCC0130");
            public static final Enum OCC_0135 = Enum.forString("OCC0135");
            public static final Enum OCC_0140 = Enum.forString("OCC0140");
            public static final Enum OCC_0145 = Enum.forString("OCC0145");
            public static final Enum OCC_0150 = Enum.forString("OCC0150");
            public static final Enum OCC_0155 = Enum.forString("OCC0155");
            public static final Enum OCC_0160 = Enum.forString("OCC0160");
            public static final Enum OCC_0165 = Enum.forString("OCC0165");
            public static final Enum OCC_0170 = Enum.forString("OCC0170");
            public static final Enum OCC_0175 = Enum.forString("OCC0175");
            public static final Enum OCC_0180 = Enum.forString("OCC0180");
            public static final Enum OCC_0185 = Enum.forString("OCC0185");
            public static final Enum OCC_0190 = Enum.forString("OCC0190");
            public static final Enum OCC_0195 = Enum.forString("OCC0195");
            public static final Enum OCC_0200 = Enum.forString("OCC0200");
            public static final Enum OCC_0205 = Enum.forString("OCC0205");
            public static final Enum OCC_0210 = Enum.forString("OCC0210");
            public static final Enum OCC_0215 = Enum.forString("OCC0215");
            public static final Enum OCC_0220 = Enum.forString("OCC0220");
            public static final Enum OCC_0225 = Enum.forString("OCC0225");
            public static final Enum OCC_0230 = Enum.forString("OCC0230");
            public static final Enum OCC_0235 = Enum.forString("OCC0235");
            public static final Enum OCC_0240 = Enum.forString("OCC0240");
            public static final Enum OCC_0245 = Enum.forString("OCC0245");
            public static final Enum OCC_0250 = Enum.forString("OCC0250");
            public static final Enum OCC_0255 = Enum.forString("OCC0255");
            public static final Enum OCC_0260 = Enum.forString("OCC0260");
            public static final Enum OCC_0265 = Enum.forString("OCC0265");
            public static final Enum OCC_0270 = Enum.forString("OCC0270");
            public static final Enum OCC_0275 = Enum.forString("OCC0275");
            public static final Enum OCC_0280 = Enum.forString("OCC0280");
            public static final Enum OCC_0285 = Enum.forString("OCC0285");
            public static final Enum OCC_0290 = Enum.forString("OCC0290");
            public static final Enum OCC_0295 = Enum.forString("OCC0295");
            public static final Enum OCC_0300 = Enum.forString("OCC0300");
            public static final Enum OCC_0305 = Enum.forString("OCC0305");
            public static final Enum OCC_0310 = Enum.forString("OCC0310");
            public static final Enum OCC_0315 = Enum.forString("OCC0315");
            public static final Enum OCC_0320 = Enum.forString("OCC0320");
            public static final Enum OCC_0325 = Enum.forString("OCC0325");
            public static final Enum OCC_0330 = Enum.forString("OCC0330");
            public static final Enum OCC_0335 = Enum.forString("OCC0335");
            public static final Enum OCC_0340 = Enum.forString("OCC0340");
            public static final Enum OCC_0345 = Enum.forString("OCC0345");
            public static final Enum OCC_0350 = Enum.forString("OCC0350");
            public static final Enum OCC_0355 = Enum.forString("OCC0355");
            public static final Enum OCC_0360 = Enum.forString("OCC0360");
            public static final Enum OCC_0365 = Enum.forString("OCC0365");
            public static final Enum OCC_0370 = Enum.forString("OCC0370");
            public static final Enum OCC_0375 = Enum.forString("OCC0375");
            public static final Enum OCC_0380 = Enum.forString("OCC0380");
            public static final Enum OCC_0385 = Enum.forString("OCC0385");
            public static final Enum OCC_0390 = Enum.forString("OCC0390");
            public static final Enum OCC_0395 = Enum.forString("OCC0395");
            public static final Enum OCC_0400 = Enum.forString("OCC0400");
            public static final Enum OCC_0405 = Enum.forString("OCC0405");
            public static final Enum OCC_0410 = Enum.forString("OCC0410");
            public static final Enum OCC_0415 = Enum.forString("OCC0415");
            public static final Enum OCC_0420 = Enum.forString("OCC0420");
            public static final Enum OCC_0425 = Enum.forString("OCC0425");
            public static final Enum OCC_0430 = Enum.forString("OCC0430");
            public static final Enum OCC_0435 = Enum.forString("OCC0435");
            public static final Enum OCC_0440 = Enum.forString("OCC0440");
            public static final Enum OCC_0445 = Enum.forString("OCC0445");
            public static final Enum OCC_0450 = Enum.forString("OCC0450");
            public static final Enum OCC_0455 = Enum.forString("OCC0455");
            public static final Enum OCC_0460 = Enum.forString("OCC0460");
            public static final Enum OCC_0465 = Enum.forString("OCC0465");
            public static final Enum OCC_0470 = Enum.forString("OCC0470");
            public static final Enum OCC_0475 = Enum.forString("OCC0475");
            public static final Enum OCC_0480 = Enum.forString("OCC0480");
            public static final Enum OCC_0485 = Enum.forString("OCC0485");
            public static final Enum OCC_0490 = Enum.forString("OCC0490");
            public static final Enum OCC_0495 = Enum.forString("OCC0495");
            public static final Enum OCC_0500 = Enum.forString("OCC0500");
            public static final Enum OCC_0505 = Enum.forString("OCC0505");
            public static final Enum OCC_0510 = Enum.forString("OCC0510");
            public static final Enum OCC_0515 = Enum.forString("OCC0515");
            public static final Enum OCC_0520 = Enum.forString("OCC0520");
            public static final Enum OCC_0525 = Enum.forString("OCC0525");
            public static final Enum OCC_0530 = Enum.forString("OCC0530");
            public static final Enum OCC_0535 = Enum.forString("OCC0535");
            public static final Enum OCC_0540 = Enum.forString("OCC0540");
            public static final Enum OCC_0545 = Enum.forString("OCC0545");
            public static final Enum OCC_0550 = Enum.forString("OCC0550");
            public static final Enum OCC_0555 = Enum.forString("OCC0555");
            public static final Enum OCC_0560 = Enum.forString("OCC0560");
            public static final Enum OCC_0565 = Enum.forString("OCC0565");
            public static final Enum OCC_0570 = Enum.forString("OCC0570");
            public static final Enum OCC_0575 = Enum.forString("OCC0575");
            public static final Enum OCC_0580 = Enum.forString("OCC0580");
            public static final Enum OCC_0585 = Enum.forString("OCC0585");
            public static final Enum OCC_0590 = Enum.forString("OCC0590");
            public static final Enum OCC_0595 = Enum.forString("OCC0595");
            public static final Enum OCC_0600 = Enum.forString("OCC0600");
            public static final Enum OCC_0605 = Enum.forString("OCC0605");
            public static final Enum OCC_0610 = Enum.forString("OCC0610");
            public static final Enum OCC_0615 = Enum.forString("OCC0615");
            public static final Enum OCC_0620 = Enum.forString("OCC0620");
            public static final Enum OCC_0625 = Enum.forString("OCC0625");
            public static final Enum OCC_0630 = Enum.forString("OCC0630");
            public static final Enum OCC_0635 = Enum.forString("OCC0635");
            public static final Enum OCC_0640 = Enum.forString("OCC0640");
            public static final Enum OCC_0645 = Enum.forString("OCC0645");
            public static final Enum OCC_0650 = Enum.forString("OCC0650");
            public static final Enum OCC_0655 = Enum.forString("OCC0655");
            public static final Enum OCC_0660 = Enum.forString("OCC0660");
            public static final Enum OCC_0665 = Enum.forString("OCC0665");
            public static final Enum OCC_0670 = Enum.forString("OCC0670");
            public static final Enum OCC_0675 = Enum.forString("OCC0675");
            public static final Enum OCC_0680 = Enum.forString("OCC0680");
            public static final Enum OCC_0685 = Enum.forString("OCC0685");
            public static final Enum OCC_0690 = Enum.forString("OCC0690");
            public static final Enum OCC_0695 = Enum.forString("OCC0695");
            public static final Enum OCC_0700 = Enum.forString("OCC0700");
            public static final Enum OCC_0705 = Enum.forString("OCC0705");
            public static final Enum OCC_0710 = Enum.forString("OCC0710");
            public static final Enum OCC_0715 = Enum.forString("OCC0715");
            public static final Enum OCC_0720 = Enum.forString("OCC0720");
            public static final Enum OCC_0725 = Enum.forString("OCC0725");
            public static final Enum OCC_0730 = Enum.forString("OCC0730");
            public static final Enum OCC_0735 = Enum.forString("OCC0735");
            public static final Enum OCC_0740 = Enum.forString("OCC0740");
            public static final Enum OCC_0745 = Enum.forString("OCC0745");
            public static final Enum OCC_0750 = Enum.forString("OCC0750");
            public static final Enum OCC_0755 = Enum.forString("OCC0755");
            public static final Enum OCC_0760 = Enum.forString("OCC0760");
            public static final Enum OCC_0765 = Enum.forString("OCC0765");
            public static final Enum OCC_0770 = Enum.forString("OCC0770");
            public static final Enum OCC_0775 = Enum.forString("OCC0775");
            public static final Enum OCC_0780 = Enum.forString("OCC0780");
            public static final Enum OCC_0785 = Enum.forString("OCC0785");
            public static final Enum OCC_0790 = Enum.forString("OCC0790");
            public static final Enum OCC_0795 = Enum.forString("OCC0795");
            public static final Enum OCC_0800 = Enum.forString("OCC0800");
            public static final Enum OCC_0805 = Enum.forString("OCC0805");
            public static final Enum OCC_0810 = Enum.forString("OCC0810");
            public static final Enum OCC_0815 = Enum.forString("OCC0815");
            public static final Enum OCC_0820 = Enum.forString("OCC0820");
            public static final Enum OCC_0825 = Enum.forString("OCC0825");
            public static final Enum OCC_0830 = Enum.forString("OCC0830");
            public static final Enum OCC_0835 = Enum.forString("OCC0835");
            public static final Enum OCC_0840 = Enum.forString("OCC0840");
            public static final Enum OCC_0845 = Enum.forString("OCC0845");
            public static final Enum OCC_0850 = Enum.forString("OCC0850");
            public static final Enum OCC_0855 = Enum.forString("OCC0855");
            public static final Enum OCC_0860 = Enum.forString("OCC0860");
            public static final Enum OCC_0865 = Enum.forString("OCC0865");
            public static final Enum OCC_0870 = Enum.forString("OCC0870");
            public static final Enum OCC_0875 = Enum.forString("OCC0875");
            public static final Enum OCC_0880 = Enum.forString("OCC0880");
            public static final Enum OCC_0885 = Enum.forString("OCC0885");
            public static final Enum OCC_0890 = Enum.forString("OCC0890");
            public static final Enum OCC_0895 = Enum.forString("OCC0895");
            public static final Enum OCC_0900 = Enum.forString("OCC0900");
            public static final Enum OCC_0905 = Enum.forString("OCC0905");
            public static final Enum OCC_0910 = Enum.forString("OCC0910");
            public static final Enum OCC_0915 = Enum.forString("OCC0915");
            public static final Enum OCC_0920 = Enum.forString("OCC0920");
            public static final Enum OCC_0925 = Enum.forString("OCC0925");
            public static final Enum OCC_0930 = Enum.forString("OCC0930");
            public static final Enum OCC_0935 = Enum.forString("OCC0935");
            public static final Enum OCC_0940 = Enum.forString("OCC0940");
            public static final Enum OCC_0945 = Enum.forString("OCC0945");
            public static final Enum OCC_0950 = Enum.forString("OCC0950");
            public static final Enum OCC_0955 = Enum.forString("OCC0955");
            public static final Enum OCC_0960 = Enum.forString("OCC0960");
            public static final Enum OCC_0965 = Enum.forString("OCC0965");
            public static final Enum OCC_0970 = Enum.forString("OCC0970");
            public static final Enum OCC_0975 = Enum.forString("OCC0975");
            public static final Enum OCC_0980 = Enum.forString("OCC0980");
            public static final Enum OCC_0985 = Enum.forString("OCC0985");
            public static final Enum OCC_0990 = Enum.forString("OCC0990");
            public static final Enum OCC_0995 = Enum.forString("OCC0995");
            public static final Enum OCC_1000 = Enum.forString("OCC1000");
            public static final Enum OCC_1005 = Enum.forString("OCC1005");
            public static final Enum OCC_1010 = Enum.forString("OCC1010");
            public static final Enum OCC_1015 = Enum.forString("OCC1015");
            public static final Enum OCC_1020 = Enum.forString("OCC1020");
            public static final Enum OCC_1025 = Enum.forString("OCC1025");
            public static final Enum OCC_1030 = Enum.forString("OCC1030");
            public static final Enum OCC_1035 = Enum.forString("OCC1035");
            public static final Enum OCC_1040 = Enum.forString("OCC1040");
            public static final Enum OCC_1045 = Enum.forString("OCC1045");
            public static final Enum OCC_1050 = Enum.forString("OCC1050");
            public static final Enum OCC_1055 = Enum.forString("OCC1055");
            public static final Enum OCC_1060 = Enum.forString("OCC1060");
            public static final Enum OCC_1065 = Enum.forString("OCC1065");
            public static final Enum OCC_1070 = Enum.forString("OCC1070");
            public static final Enum OCC_1075 = Enum.forString("OCC1075");
            public static final Enum OCC_1080 = Enum.forString("OCC1080");
            public static final Enum OCC_1085 = Enum.forString("OCC1085");
            public static final Enum OCC_1090 = Enum.forString("OCC1090");
            public static final Enum OCC_1095 = Enum.forString("OCC1095");
            public static final Enum OCC_1100 = Enum.forString("OCC1100");
            public static final Enum OCC_1105 = Enum.forString("OCC1105");
            public static final Enum OCC_1110 = Enum.forString("OCC1110");
            public static final Enum OCC_1115 = Enum.forString("OCC1115");
            public static final Enum OCC_1120 = Enum.forString("OCC1120");
            public static final Enum OCC_1125 = Enum.forString("OCC1125");
            public static final Enum OCC_1130 = Enum.forString("OCC1130");
            public static final Enum OCC_1135 = Enum.forString("OCC1135");
            public static final Enum OCC_1140 = Enum.forString("OCC1140");
            public static final Enum OCC_1145 = Enum.forString("OCC1145");
            public static final Enum OCC_1150 = Enum.forString("OCC1150");
            public static final Enum OCC_1155 = Enum.forString("OCC1155");
            public static final Enum OCC_1160 = Enum.forString("OCC1160");
            public static final Enum OCC_1165 = Enum.forString("OCC1165");
            public static final Enum OCC_1170 = Enum.forString("OCC1170");
            public static final Enum OCC_1175 = Enum.forString("OCC1175");
            public static final Enum OCC_1180 = Enum.forString("OCC1180");
            public static final Enum OCC_1185 = Enum.forString("OCC1185");
            public static final Enum OCC_1190 = Enum.forString("OCC1190");
            public static final Enum OCC_1195 = Enum.forString("OCC1195");
            public static final Enum OCC_1200 = Enum.forString("OCC1200");
            public static final Enum OCC_1205 = Enum.forString("OCC1205");
            public static final Enum OCC_1210 = Enum.forString("OCC1210");
            public static final Enum OCC_1215 = Enum.forString("OCC1215");
            public static final Enum OCC_1220 = Enum.forString("OCC1220");
            public static final Enum OCC_1225 = Enum.forString("OCC1225");
            public static final Enum OCC_1230 = Enum.forString("OCC1230");
            public static final Enum OCC_1235 = Enum.forString("OCC1235");
            public static final Enum OCC_1240 = Enum.forString("OCC1240");
            public static final Enum OCC_1245 = Enum.forString("OCC1245");
            public static final Enum OCC_1250 = Enum.forString("OCC1250");
            public static final Enum OCC_1255 = Enum.forString("OCC1255");
            public static final Enum OCC_1260 = Enum.forString("OCC1260");
            public static final Enum OCC_1265 = Enum.forString("OCC1265");
            public static final Enum OCC_1270 = Enum.forString("OCC1270");
            public static final Enum OCC_1275 = Enum.forString("OCC1275");
            public static final Enum OCC_1280 = Enum.forString("OCC1280");
            public static final Enum OCC_1285 = Enum.forString("OCC1285");
            public static final Enum OCC_1290 = Enum.forString("OCC1290");
            public static final Enum OTHER = Enum.forString("OTHER");
            public static final int INT_OCC_0005 = 1;
            public static final int INT_OCC_0010 = 2;
            public static final int INT_OCC_0015 = 3;
            public static final int INT_OCC_0020 = 4;
            public static final int INT_OCC_0025 = 5;
            public static final int INT_OCC_0030 = 6;
            public static final int INT_OCC_0035 = 7;
            public static final int INT_OCC_0040 = 8;
            public static final int INT_OCC_0045 = 9;
            public static final int INT_OCC_0050 = 10;
            public static final int INT_OCC_0055 = 11;
            public static final int INT_OCC_0060 = 12;
            public static final int INT_OCC_0065 = 13;
            public static final int INT_OCC_0070 = 14;
            public static final int INT_OCC_0075 = 15;
            public static final int INT_OCC_0080 = 16;
            public static final int INT_OCC_0085 = 17;
            public static final int INT_OCC_0090 = 18;
            public static final int INT_OCC_0095 = 19;
            public static final int INT_OCC_0100 = 20;
            public static final int INT_OCC_0105 = 21;
            public static final int INT_OCC_0110 = 22;
            public static final int INT_OCC_0115 = 23;
            public static final int INT_OCC_0120 = 24;
            public static final int INT_OCC_0125 = 25;
            public static final int INT_OCC_0130 = 26;
            public static final int INT_OCC_0135 = 27;
            public static final int INT_OCC_0140 = 28;
            public static final int INT_OCC_0145 = 29;
            public static final int INT_OCC_0150 = 30;
            public static final int INT_OCC_0155 = 31;
            public static final int INT_OCC_0160 = 32;
            public static final int INT_OCC_0165 = 33;
            public static final int INT_OCC_0170 = 34;
            public static final int INT_OCC_0175 = 35;
            public static final int INT_OCC_0180 = 36;
            public static final int INT_OCC_0185 = 37;
            public static final int INT_OCC_0190 = 38;
            public static final int INT_OCC_0195 = 39;
            public static final int INT_OCC_0200 = 40;
            public static final int INT_OCC_0205 = 41;
            public static final int INT_OCC_0210 = 42;
            public static final int INT_OCC_0215 = 43;
            public static final int INT_OCC_0220 = 44;
            public static final int INT_OCC_0225 = 45;
            public static final int INT_OCC_0230 = 46;
            public static final int INT_OCC_0235 = 47;
            public static final int INT_OCC_0240 = 48;
            public static final int INT_OCC_0245 = 49;
            public static final int INT_OCC_0250 = 50;
            public static final int INT_OCC_0255 = 51;
            public static final int INT_OCC_0260 = 52;
            public static final int INT_OCC_0265 = 53;
            public static final int INT_OCC_0270 = 54;
            public static final int INT_OCC_0275 = 55;
            public static final int INT_OCC_0280 = 56;
            public static final int INT_OCC_0285 = 57;
            public static final int INT_OCC_0290 = 58;
            public static final int INT_OCC_0295 = 59;
            public static final int INT_OCC_0300 = 60;
            public static final int INT_OCC_0305 = 61;
            public static final int INT_OCC_0310 = 62;
            public static final int INT_OCC_0315 = 63;
            public static final int INT_OCC_0320 = 64;
            public static final int INT_OCC_0325 = 65;
            public static final int INT_OCC_0330 = 66;
            public static final int INT_OCC_0335 = 67;
            public static final int INT_OCC_0340 = 68;
            public static final int INT_OCC_0345 = 69;
            public static final int INT_OCC_0350 = 70;
            public static final int INT_OCC_0355 = 71;
            public static final int INT_OCC_0360 = 72;
            public static final int INT_OCC_0365 = 73;
            public static final int INT_OCC_0370 = 74;
            public static final int INT_OCC_0375 = 75;
            public static final int INT_OCC_0380 = 76;
            public static final int INT_OCC_0385 = 77;
            public static final int INT_OCC_0390 = 78;
            public static final int INT_OCC_0395 = 79;
            public static final int INT_OCC_0400 = 80;
            public static final int INT_OCC_0405 = 81;
            public static final int INT_OCC_0410 = 82;
            public static final int INT_OCC_0415 = 83;
            public static final int INT_OCC_0420 = 84;
            public static final int INT_OCC_0425 = 85;
            public static final int INT_OCC_0430 = 86;
            public static final int INT_OCC_0435 = 87;
            public static final int INT_OCC_0440 = 88;
            public static final int INT_OCC_0445 = 89;
            public static final int INT_OCC_0450 = 90;
            public static final int INT_OCC_0455 = 91;
            public static final int INT_OCC_0460 = 92;
            public static final int INT_OCC_0465 = 93;
            public static final int INT_OCC_0470 = 94;
            public static final int INT_OCC_0475 = 95;
            public static final int INT_OCC_0480 = 96;
            public static final int INT_OCC_0485 = 97;
            public static final int INT_OCC_0490 = 98;
            public static final int INT_OCC_0495 = 99;
            public static final int INT_OCC_0500 = 100;
            public static final int INT_OCC_0505 = 101;
            public static final int INT_OCC_0510 = 102;
            public static final int INT_OCC_0515 = 103;
            public static final int INT_OCC_0520 = 104;
            public static final int INT_OCC_0525 = 105;
            public static final int INT_OCC_0530 = 106;
            public static final int INT_OCC_0535 = 107;
            public static final int INT_OCC_0540 = 108;
            public static final int INT_OCC_0545 = 109;
            public static final int INT_OCC_0550 = 110;
            public static final int INT_OCC_0555 = 111;
            public static final int INT_OCC_0560 = 112;
            public static final int INT_OCC_0565 = 113;
            public static final int INT_OCC_0570 = 114;
            public static final int INT_OCC_0575 = 115;
            public static final int INT_OCC_0580 = 116;
            public static final int INT_OCC_0585 = 117;
            public static final int INT_OCC_0590 = 118;
            public static final int INT_OCC_0595 = 119;
            public static final int INT_OCC_0600 = 120;
            public static final int INT_OCC_0605 = 121;
            public static final int INT_OCC_0610 = 122;
            public static final int INT_OCC_0615 = 123;
            public static final int INT_OCC_0620 = 124;
            public static final int INT_OCC_0625 = 125;
            public static final int INT_OCC_0630 = 126;
            public static final int INT_OCC_0635 = 127;
            public static final int INT_OCC_0640 = 128;
            public static final int INT_OCC_0645 = 129;
            public static final int INT_OCC_0650 = 130;
            public static final int INT_OCC_0655 = 131;
            public static final int INT_OCC_0660 = 132;
            public static final int INT_OCC_0665 = 133;
            public static final int INT_OCC_0670 = 134;
            public static final int INT_OCC_0675 = 135;
            public static final int INT_OCC_0680 = 136;
            public static final int INT_OCC_0685 = 137;
            public static final int INT_OCC_0690 = 138;
            public static final int INT_OCC_0695 = 139;
            public static final int INT_OCC_0700 = 140;
            public static final int INT_OCC_0705 = 141;
            public static final int INT_OCC_0710 = 142;
            public static final int INT_OCC_0715 = 143;
            public static final int INT_OCC_0720 = 144;
            public static final int INT_OCC_0725 = 145;
            public static final int INT_OCC_0730 = 146;
            public static final int INT_OCC_0735 = 147;
            public static final int INT_OCC_0740 = 148;
            public static final int INT_OCC_0745 = 149;
            public static final int INT_OCC_0750 = 150;
            public static final int INT_OCC_0755 = 151;
            public static final int INT_OCC_0760 = 152;
            public static final int INT_OCC_0765 = 153;
            public static final int INT_OCC_0770 = 154;
            public static final int INT_OCC_0775 = 155;
            public static final int INT_OCC_0780 = 156;
            public static final int INT_OCC_0785 = 157;
            public static final int INT_OCC_0790 = 158;
            public static final int INT_OCC_0795 = 159;
            public static final int INT_OCC_0800 = 160;
            public static final int INT_OCC_0805 = 161;
            public static final int INT_OCC_0810 = 162;
            public static final int INT_OCC_0815 = 163;
            public static final int INT_OCC_0820 = 164;
            public static final int INT_OCC_0825 = 165;
            public static final int INT_OCC_0830 = 166;
            public static final int INT_OCC_0835 = 167;
            public static final int INT_OCC_0840 = 168;
            public static final int INT_OCC_0845 = 169;
            public static final int INT_OCC_0850 = 170;
            public static final int INT_OCC_0855 = 171;
            public static final int INT_OCC_0860 = 172;
            public static final int INT_OCC_0865 = 173;
            public static final int INT_OCC_0870 = 174;
            public static final int INT_OCC_0875 = 175;
            public static final int INT_OCC_0880 = 176;
            public static final int INT_OCC_0885 = 177;
            public static final int INT_OCC_0890 = 178;
            public static final int INT_OCC_0895 = 179;
            public static final int INT_OCC_0900 = 180;
            public static final int INT_OCC_0905 = 181;
            public static final int INT_OCC_0910 = 182;
            public static final int INT_OCC_0915 = 183;
            public static final int INT_OCC_0920 = 184;
            public static final int INT_OCC_0925 = 185;
            public static final int INT_OCC_0930 = 186;
            public static final int INT_OCC_0935 = 187;
            public static final int INT_OCC_0940 = 188;
            public static final int INT_OCC_0945 = 189;
            public static final int INT_OCC_0950 = 190;
            public static final int INT_OCC_0955 = 191;
            public static final int INT_OCC_0960 = 192;
            public static final int INT_OCC_0965 = 193;
            public static final int INT_OCC_0970 = 194;
            public static final int INT_OCC_0975 = 195;
            public static final int INT_OCC_0980 = 196;
            public static final int INT_OCC_0985 = 197;
            public static final int INT_OCC_0990 = 198;
            public static final int INT_OCC_0995 = 199;
            public static final int INT_OCC_1000 = 200;
            public static final int INT_OCC_1005 = 201;
            public static final int INT_OCC_1010 = 202;
            public static final int INT_OCC_1015 = 203;
            public static final int INT_OCC_1020 = 204;
            public static final int INT_OCC_1025 = 205;
            public static final int INT_OCC_1030 = 206;
            public static final int INT_OCC_1035 = 207;
            public static final int INT_OCC_1040 = 208;
            public static final int INT_OCC_1045 = 209;
            public static final int INT_OCC_1050 = 210;
            public static final int INT_OCC_1055 = 211;
            public static final int INT_OCC_1060 = 212;
            public static final int INT_OCC_1065 = 213;
            public static final int INT_OCC_1070 = 214;
            public static final int INT_OCC_1075 = 215;
            public static final int INT_OCC_1080 = 216;
            public static final int INT_OCC_1085 = 217;
            public static final int INT_OCC_1090 = 218;
            public static final int INT_OCC_1095 = 219;
            public static final int INT_OCC_1100 = 220;
            public static final int INT_OCC_1105 = 221;
            public static final int INT_OCC_1110 = 222;
            public static final int INT_OCC_1115 = 223;
            public static final int INT_OCC_1120 = 224;
            public static final int INT_OCC_1125 = 225;
            public static final int INT_OCC_1130 = 226;
            public static final int INT_OCC_1135 = 227;
            public static final int INT_OCC_1140 = 228;
            public static final int INT_OCC_1145 = 229;
            public static final int INT_OCC_1150 = 230;
            public static final int INT_OCC_1155 = 231;
            public static final int INT_OCC_1160 = 232;
            public static final int INT_OCC_1165 = 233;
            public static final int INT_OCC_1170 = 234;
            public static final int INT_OCC_1175 = 235;
            public static final int INT_OCC_1180 = 236;
            public static final int INT_OCC_1185 = 237;
            public static final int INT_OCC_1190 = 238;
            public static final int INT_OCC_1195 = 239;
            public static final int INT_OCC_1200 = 240;
            public static final int INT_OCC_1205 = 241;
            public static final int INT_OCC_1210 = 242;
            public static final int INT_OCC_1215 = 243;
            public static final int INT_OCC_1220 = 244;
            public static final int INT_OCC_1225 = 245;
            public static final int INT_OCC_1230 = 246;
            public static final int INT_OCC_1235 = 247;
            public static final int INT_OCC_1240 = 248;
            public static final int INT_OCC_1245 = 249;
            public static final int INT_OCC_1250 = 250;
            public static final int INT_OCC_1255 = 251;
            public static final int INT_OCC_1260 = 252;
            public static final int INT_OCC_1265 = 253;
            public static final int INT_OCC_1270 = 254;
            public static final int INT_OCC_1275 = 255;
            public static final int INT_OCC_1280 = 256;
            public static final int INT_OCC_1285 = 257;
            public static final int INT_OCC_1290 = 258;
            /** Integer constant for OTHER occupation category (259). */
            public static final int INT_OTHER = 259;

            /**
             * Gets the enumeration value as a StringEnumAbstractBase.
             * Provides access to the underlying enumeration base for advanced type operations.
             *
             * @return StringEnumAbstractBase the enumeration value base object
             */
            StringEnumAbstractBase enumValue();

            /**
             * Sets the occupation value using a StringEnumAbstractBase enumeration.
             * Allows setting with base enumeration type for framework interoperability.
             *
             * @param p0 StringEnumAbstractBase the enumeration value to set
             */
            void set(final StringEnumAbstractBase p0);

            /**
             * Enumeration implementation for occupation classification values.
             *
             * <p>Provides concrete enumeration instances for all 259 occupation codes, extending
             * StringEnumAbstractBase to integrate with the Apache XMLBeans type system.</p>
             *
             * <p>Each enum instance maps to both a string representation (e.g., "OCC0005") and an
             * integer value (1-259) for efficient storage and comparison operations.</p>
             *
             * @since 2026-01-24
             */
            public static final class Enum extends StringEnumAbstractBase
            {
                static final int INT_OCC_0005 = 1;
                static final int INT_OCC_0010 = 2;
                static final int INT_OCC_0015 = 3;
                static final int INT_OCC_0020 = 4;
                static final int INT_OCC_0025 = 5;
                static final int INT_OCC_0030 = 6;
                static final int INT_OCC_0035 = 7;
                static final int INT_OCC_0040 = 8;
                static final int INT_OCC_0045 = 9;
                static final int INT_OCC_0050 = 10;
                static final int INT_OCC_0055 = 11;
                static final int INT_OCC_0060 = 12;
                static final int INT_OCC_0065 = 13;
                static final int INT_OCC_0070 = 14;
                static final int INT_OCC_0075 = 15;
                static final int INT_OCC_0080 = 16;
                static final int INT_OCC_0085 = 17;
                static final int INT_OCC_0090 = 18;
                static final int INT_OCC_0095 = 19;
                static final int INT_OCC_0100 = 20;
                static final int INT_OCC_0105 = 21;
                static final int INT_OCC_0110 = 22;
                static final int INT_OCC_0115 = 23;
                static final int INT_OCC_0120 = 24;
                static final int INT_OCC_0125 = 25;
                static final int INT_OCC_0130 = 26;
                static final int INT_OCC_0135 = 27;
                static final int INT_OCC_0140 = 28;
                static final int INT_OCC_0145 = 29;
                static final int INT_OCC_0150 = 30;
                static final int INT_OCC_0155 = 31;
                static final int INT_OCC_0160 = 32;
                static final int INT_OCC_0165 = 33;
                static final int INT_OCC_0170 = 34;
                static final int INT_OCC_0175 = 35;
                static final int INT_OCC_0180 = 36;
                static final int INT_OCC_0185 = 37;
                static final int INT_OCC_0190 = 38;
                static final int INT_OCC_0195 = 39;
                static final int INT_OCC_0200 = 40;
                static final int INT_OCC_0205 = 41;
                static final int INT_OCC_0210 = 42;
                static final int INT_OCC_0215 = 43;
                static final int INT_OCC_0220 = 44;
                static final int INT_OCC_0225 = 45;
                static final int INT_OCC_0230 = 46;
                static final int INT_OCC_0235 = 47;
                static final int INT_OCC_0240 = 48;
                static final int INT_OCC_0245 = 49;
                static final int INT_OCC_0250 = 50;
                static final int INT_OCC_0255 = 51;
                static final int INT_OCC_0260 = 52;
                static final int INT_OCC_0265 = 53;
                static final int INT_OCC_0270 = 54;
                static final int INT_OCC_0275 = 55;
                static final int INT_OCC_0280 = 56;
                static final int INT_OCC_0285 = 57;
                static final int INT_OCC_0290 = 58;
                static final int INT_OCC_0295 = 59;
                static final int INT_OCC_0300 = 60;
                static final int INT_OCC_0305 = 61;
                static final int INT_OCC_0310 = 62;
                static final int INT_OCC_0315 = 63;
                static final int INT_OCC_0320 = 64;
                static final int INT_OCC_0325 = 65;
                static final int INT_OCC_0330 = 66;
                static final int INT_OCC_0335 = 67;
                static final int INT_OCC_0340 = 68;
                static final int INT_OCC_0345 = 69;
                static final int INT_OCC_0350 = 70;
                static final int INT_OCC_0355 = 71;
                static final int INT_OCC_0360 = 72;
                static final int INT_OCC_0365 = 73;
                static final int INT_OCC_0370 = 74;
                static final int INT_OCC_0375 = 75;
                static final int INT_OCC_0380 = 76;
                static final int INT_OCC_0385 = 77;
                static final int INT_OCC_0390 = 78;
                static final int INT_OCC_0395 = 79;
                static final int INT_OCC_0400 = 80;
                static final int INT_OCC_0405 = 81;
                static final int INT_OCC_0410 = 82;
                static final int INT_OCC_0415 = 83;
                static final int INT_OCC_0420 = 84;
                static final int INT_OCC_0425 = 85;
                static final int INT_OCC_0430 = 86;
                static final int INT_OCC_0435 = 87;
                static final int INT_OCC_0440 = 88;
                static final int INT_OCC_0445 = 89;
                static final int INT_OCC_0450 = 90;
                static final int INT_OCC_0455 = 91;
                static final int INT_OCC_0460 = 92;
                static final int INT_OCC_0465 = 93;
                static final int INT_OCC_0470 = 94;
                static final int INT_OCC_0475 = 95;
                static final int INT_OCC_0480 = 96;
                static final int INT_OCC_0485 = 97;
                static final int INT_OCC_0490 = 98;
                static final int INT_OCC_0495 = 99;
                static final int INT_OCC_0500 = 100;
                static final int INT_OCC_0505 = 101;
                static final int INT_OCC_0510 = 102;
                static final int INT_OCC_0515 = 103;
                static final int INT_OCC_0520 = 104;
                static final int INT_OCC_0525 = 105;
                static final int INT_OCC_0530 = 106;
                static final int INT_OCC_0535 = 107;
                static final int INT_OCC_0540 = 108;
                static final int INT_OCC_0545 = 109;
                static final int INT_OCC_0550 = 110;
                static final int INT_OCC_0555 = 111;
                static final int INT_OCC_0560 = 112;
                static final int INT_OCC_0565 = 113;
                static final int INT_OCC_0570 = 114;
                static final int INT_OCC_0575 = 115;
                static final int INT_OCC_0580 = 116;
                static final int INT_OCC_0585 = 117;
                static final int INT_OCC_0590 = 118;
                static final int INT_OCC_0595 = 119;
                static final int INT_OCC_0600 = 120;
                static final int INT_OCC_0605 = 121;
                static final int INT_OCC_0610 = 122;
                static final int INT_OCC_0615 = 123;
                static final int INT_OCC_0620 = 124;
                static final int INT_OCC_0625 = 125;
                static final int INT_OCC_0630 = 126;
                static final int INT_OCC_0635 = 127;
                static final int INT_OCC_0640 = 128;
                static final int INT_OCC_0645 = 129;
                static final int INT_OCC_0650 = 130;
                static final int INT_OCC_0655 = 131;
                static final int INT_OCC_0660 = 132;
                static final int INT_OCC_0665 = 133;
                static final int INT_OCC_0670 = 134;
                static final int INT_OCC_0675 = 135;
                static final int INT_OCC_0680 = 136;
                static final int INT_OCC_0685 = 137;
                static final int INT_OCC_0690 = 138;
                static final int INT_OCC_0695 = 139;
                static final int INT_OCC_0700 = 140;
                static final int INT_OCC_0705 = 141;
                static final int INT_OCC_0710 = 142;
                static final int INT_OCC_0715 = 143;
                static final int INT_OCC_0720 = 144;
                static final int INT_OCC_0725 = 145;
                static final int INT_OCC_0730 = 146;
                static final int INT_OCC_0735 = 147;
                static final int INT_OCC_0740 = 148;
                static final int INT_OCC_0745 = 149;
                static final int INT_OCC_0750 = 150;
                static final int INT_OCC_0755 = 151;
                static final int INT_OCC_0760 = 152;
                static final int INT_OCC_0765 = 153;
                static final int INT_OCC_0770 = 154;
                static final int INT_OCC_0775 = 155;
                static final int INT_OCC_0780 = 156;
                static final int INT_OCC_0785 = 157;
                static final int INT_OCC_0790 = 158;
                static final int INT_OCC_0795 = 159;
                static final int INT_OCC_0800 = 160;
                static final int INT_OCC_0805 = 161;
                static final int INT_OCC_0810 = 162;
                static final int INT_OCC_0815 = 163;
                static final int INT_OCC_0820 = 164;
                static final int INT_OCC_0825 = 165;
                static final int INT_OCC_0830 = 166;
                static final int INT_OCC_0835 = 167;
                static final int INT_OCC_0840 = 168;
                static final int INT_OCC_0845 = 169;
                static final int INT_OCC_0850 = 170;
                static final int INT_OCC_0855 = 171;
                static final int INT_OCC_0860 = 172;
                static final int INT_OCC_0865 = 173;
                static final int INT_OCC_0870 = 174;
                static final int INT_OCC_0875 = 175;
                static final int INT_OCC_0880 = 176;
                static final int INT_OCC_0885 = 177;
                static final int INT_OCC_0890 = 178;
                static final int INT_OCC_0895 = 179;
                static final int INT_OCC_0900 = 180;
                static final int INT_OCC_0905 = 181;
                static final int INT_OCC_0910 = 182;
                static final int INT_OCC_0915 = 183;
                static final int INT_OCC_0920 = 184;
                static final int INT_OCC_0925 = 185;
                static final int INT_OCC_0930 = 186;
                static final int INT_OCC_0935 = 187;
                static final int INT_OCC_0940 = 188;
                static final int INT_OCC_0945 = 189;
                static final int INT_OCC_0950 = 190;
                static final int INT_OCC_0955 = 191;
                static final int INT_OCC_0960 = 192;
                static final int INT_OCC_0965 = 193;
                static final int INT_OCC_0970 = 194;
                static final int INT_OCC_0975 = 195;
                static final int INT_OCC_0980 = 196;
                static final int INT_OCC_0985 = 197;
                static final int INT_OCC_0990 = 198;
                static final int INT_OCC_0995 = 199;
                static final int INT_OCC_1000 = 200;
                static final int INT_OCC_1005 = 201;
                static final int INT_OCC_1010 = 202;
                static final int INT_OCC_1015 = 203;
                static final int INT_OCC_1020 = 204;
                static final int INT_OCC_1025 = 205;
                static final int INT_OCC_1030 = 206;
                static final int INT_OCC_1035 = 207;
                static final int INT_OCC_1040 = 208;
                static final int INT_OCC_1045 = 209;
                static final int INT_OCC_1050 = 210;
                static final int INT_OCC_1055 = 211;
                static final int INT_OCC_1060 = 212;
                static final int INT_OCC_1065 = 213;
                static final int INT_OCC_1070 = 214;
                static final int INT_OCC_1075 = 215;
                static final int INT_OCC_1080 = 216;
                static final int INT_OCC_1085 = 217;
                static final int INT_OCC_1090 = 218;
                static final int INT_OCC_1095 = 219;
                static final int INT_OCC_1100 = 220;
                static final int INT_OCC_1105 = 221;
                static final int INT_OCC_1110 = 222;
                static final int INT_OCC_1115 = 223;
                static final int INT_OCC_1120 = 224;
                static final int INT_OCC_1125 = 225;
                static final int INT_OCC_1130 = 226;
                static final int INT_OCC_1135 = 227;
                static final int INT_OCC_1140 = 228;
                static final int INT_OCC_1145 = 229;
                static final int INT_OCC_1150 = 230;
                static final int INT_OCC_1155 = 231;
                static final int INT_OCC_1160 = 232;
                static final int INT_OCC_1165 = 233;
                static final int INT_OCC_1170 = 234;
                static final int INT_OCC_1175 = 235;
                static final int INT_OCC_1180 = 236;
                static final int INT_OCC_1185 = 237;
                static final int INT_OCC_1190 = 238;
                static final int INT_OCC_1195 = 239;
                static final int INT_OCC_1200 = 240;
                static final int INT_OCC_1205 = 241;
                static final int INT_OCC_1210 = 242;
                static final int INT_OCC_1215 = 243;
                static final int INT_OCC_1220 = 244;
                static final int INT_OCC_1225 = 245;
                static final int INT_OCC_1230 = 246;
                static final int INT_OCC_1235 = 247;
                static final int INT_OCC_1240 = 248;
                static final int INT_OCC_1245 = 249;
                static final int INT_OCC_1250 = 250;
                static final int INT_OCC_1255 = 251;
                static final int INT_OCC_1260 = 252;
                static final int INT_OCC_1265 = 253;
                static final int INT_OCC_1270 = 254;
                static final int INT_OCC_1275 = 255;
                static final int INT_OCC_1280 = 256;
                static final int INT_OCC_1285 = 257;
                static final int INT_OCC_1290 = 258;
                /** Integer constant for OTHER occupation category (259). */
                static final int INT_OTHER = 259;
                /** Lookup table mapping string and integer values to enumeration instances. */
                public static final StringEnumAbstractBase.Table table;
                /** Serialization version identifier. */
                private static final long serialVersionUID = 1L;

                /**
                 * Looks up an enumeration instance by its string representation.
                 *
                 * @param s String the occupation code string (e.g., "OCC0005", "OTHER")
                 * @return Enum the corresponding enumeration instance, or null if not found
                 */
                public static Enum forString(final String s) {
                    return (Enum)Enum.table.forString(s);
                }

                /**
                 * Looks up an enumeration instance by its integer value.
                 *
                 * @param i int the occupation code integer (1-259)
                 * @return Enum the corresponding enumeration instance, or null if not found
                 */
                public static Enum forInt(final int i) {
                    return (Enum)Enum.table.forInt(i);
                }

                /**
                 * Private constructor for enumeration instances.
                 * Called during static initialization to create all 259 occupation code instances.
                 *
                 * @param s String the occupation code string representation
                 * @param i int the occupation code integer value
                 */
                private Enum(final String s, final int i) {
                    super(s, i);
                }

                /**
                 * Resolves the enumeration instance during deserialization.
                 * Ensures singleton semantics by returning the canonical instance from the lookup table.
                 *
                 * @return Object the canonical enumeration instance for this integer value
                 */
                private Object readResolve() {
                    return forInt(this.intValue());
                }
                
                static {
                    table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("OCC0005", 1), new Enum("OCC0010", 2), new Enum("OCC0015", 3), new Enum("OCC0020", 4), new Enum("OCC0025", 5), new Enum("OCC0030", 6), new Enum("OCC0035", 7), new Enum("OCC0040", 8), new Enum("OCC0045", 9), new Enum("OCC0050", 10), new Enum("OCC0055", 11), new Enum("OCC0060", 12), new Enum("OCC0065", 13), new Enum("OCC0070", 14), new Enum("OCC0075", 15), new Enum("OCC0080", 16), new Enum("OCC0085", 17), new Enum("OCC0090", 18), new Enum("OCC0095", 19), new Enum("OCC0100", 20), new Enum("OCC0105", 21), new Enum("OCC0110", 22), new Enum("OCC0115", 23), new Enum("OCC0120", 24), new Enum("OCC0125", 25), new Enum("OCC0130", 26), new Enum("OCC0135", 27), new Enum("OCC0140", 28), new Enum("OCC0145", 29), new Enum("OCC0150", 30), new Enum("OCC0155", 31), new Enum("OCC0160", 32), new Enum("OCC0165", 33), new Enum("OCC0170", 34), new Enum("OCC0175", 35), new Enum("OCC0180", 36), new Enum("OCC0185", 37), new Enum("OCC0190", 38), new Enum("OCC0195", 39), new Enum("OCC0200", 40), new Enum("OCC0205", 41), new Enum("OCC0210", 42), new Enum("OCC0215", 43), new Enum("OCC0220", 44), new Enum("OCC0225", 45), new Enum("OCC0230", 46), new Enum("OCC0235", 47), new Enum("OCC0240", 48), new Enum("OCC0245", 49), new Enum("OCC0250", 50), new Enum("OCC0255", 51), new Enum("OCC0260", 52), new Enum("OCC0265", 53), new Enum("OCC0270", 54), new Enum("OCC0275", 55), new Enum("OCC0280", 56), new Enum("OCC0285", 57), new Enum("OCC0290", 58), new Enum("OCC0295", 59), new Enum("OCC0300", 60), new Enum("OCC0305", 61), new Enum("OCC0310", 62), new Enum("OCC0315", 63), new Enum("OCC0320", 64), new Enum("OCC0325", 65), new Enum("OCC0330", 66), new Enum("OCC0335", 67), new Enum("OCC0340", 68), new Enum("OCC0345", 69), new Enum("OCC0350", 70), new Enum("OCC0355", 71), new Enum("OCC0360", 72), new Enum("OCC0365", 73), new Enum("OCC0370", 74), new Enum("OCC0375", 75), new Enum("OCC0380", 76), new Enum("OCC0385", 77), new Enum("OCC0390", 78), new Enum("OCC0395", 79), new Enum("OCC0400", 80), new Enum("OCC0405", 81), new Enum("OCC0410", 82), new Enum("OCC0415", 83), new Enum("OCC0420", 84), new Enum("OCC0425", 85), new Enum("OCC0430", 86), new Enum("OCC0435", 87), new Enum("OCC0440", 88), new Enum("OCC0445", 89), new Enum("OCC0450", 90), new Enum("OCC0455", 91), new Enum("OCC0460", 92), new Enum("OCC0465", 93), new Enum("OCC0470", 94), new Enum("OCC0475", 95), new Enum("OCC0480", 96), new Enum("OCC0485", 97), new Enum("OCC0490", 98), new Enum("OCC0495", 99), new Enum("OCC0500", 100), new Enum("OCC0505", 101), new Enum("OCC0510", 102), new Enum("OCC0515", 103), new Enum("OCC0520", 104), new Enum("OCC0525", 105), new Enum("OCC0530", 106), new Enum("OCC0535", 107), new Enum("OCC0540", 108), new Enum("OCC0545", 109), new Enum("OCC0550", 110), new Enum("OCC0555", 111), new Enum("OCC0560", 112), new Enum("OCC0565", 113), new Enum("OCC0570", 114), new Enum("OCC0575", 115), new Enum("OCC0580", 116), new Enum("OCC0585", 117), new Enum("OCC0590", 118), new Enum("OCC0595", 119), new Enum("OCC0600", 120), new Enum("OCC0605", 121), new Enum("OCC0610", 122), new Enum("OCC0615", 123), new Enum("OCC0620", 124), new Enum("OCC0625", 125), new Enum("OCC0630", 126), new Enum("OCC0635", 127), new Enum("OCC0640", 128), new Enum("OCC0645", 129), new Enum("OCC0650", 130), new Enum("OCC0655", 131), new Enum("OCC0660", 132), new Enum("OCC0665", 133), new Enum("OCC0670", 134), new Enum("OCC0675", 135), new Enum("OCC0680", 136), new Enum("OCC0685", 137), new Enum("OCC0690", 138), new Enum("OCC0695", 139), new Enum("OCC0700", 140), new Enum("OCC0705", 141), new Enum("OCC0710", 142), new Enum("OCC0715", 143), new Enum("OCC0720", 144), new Enum("OCC0725", 145), new Enum("OCC0730", 146), new Enum("OCC0735", 147), new Enum("OCC0740", 148), new Enum("OCC0745", 149), new Enum("OCC0750", 150), new Enum("OCC0755", 151), new Enum("OCC0760", 152), new Enum("OCC0765", 153), new Enum("OCC0770", 154), new Enum("OCC0775", 155), new Enum("OCC0780", 156), new Enum("OCC0785", 157), new Enum("OCC0790", 158), new Enum("OCC0795", 159), new Enum("OCC0800", 160), new Enum("OCC0805", 161), new Enum("OCC0810", 162), new Enum("OCC0815", 163), new Enum("OCC0820", 164), new Enum("OCC0825", 165), new Enum("OCC0830", 166), new Enum("OCC0835", 167), new Enum("OCC0840", 168), new Enum("OCC0845", 169), new Enum("OCC0850", 170), new Enum("OCC0855", 171), new Enum("OCC0860", 172), new Enum("OCC0865", 173), new Enum("OCC0870", 174), new Enum("OCC0875", 175), new Enum("OCC0880", 176), new Enum("OCC0885", 177), new Enum("OCC0890", 178), new Enum("OCC0895", 179), new Enum("OCC0900", 180), new Enum("OCC0905", 181), new Enum("OCC0910", 182), new Enum("OCC0915", 183), new Enum("OCC0920", 184), new Enum("OCC0925", 185), new Enum("OCC0930", 186), new Enum("OCC0935", 187), new Enum("OCC0940", 188), new Enum("OCC0945", 189), new Enum("OCC0950", 190), new Enum("OCC0955", 191), new Enum("OCC0960", 192), new Enum("OCC0965", 193), new Enum("OCC0970", 194), new Enum("OCC0975", 195), new Enum("OCC0980", 196), new Enum("OCC0985", 197), new Enum("OCC0990", 198), new Enum("OCC0995", 199), new Enum("OCC1000", 200), new Enum("OCC1005", 201), new Enum("OCC1010", 202), new Enum("OCC1015", 203), new Enum("OCC1020", 204), new Enum("OCC1025", 205), new Enum("OCC1030", 206), new Enum("OCC1035", 207), new Enum("OCC1040", 208), new Enum("OCC1045", 209), new Enum("OCC1050", 210), new Enum("OCC1055", 211), new Enum("OCC1060", 212), new Enum("OCC1065", 213), new Enum("OCC1070", 214), new Enum("OCC1075", 215), new Enum("OCC1080", 216), new Enum("OCC1085", 217), new Enum("OCC1090", 218), new Enum("OCC1095", 219), new Enum("OCC1100", 220), new Enum("OCC1105", 221), new Enum("OCC1110", 222), new Enum("OCC1115", 223), new Enum("OCC1120", 224), new Enum("OCC1125", 225), new Enum("OCC1130", 226), new Enum("OCC1135", 227), new Enum("OCC1140", 228), new Enum("OCC1145", 229), new Enum("OCC1150", 230), new Enum("OCC1155", 231), new Enum("OCC1160", 232), new Enum("OCC1165", 233), new Enum("OCC1170", 234), new Enum("OCC1175", 235), new Enum("OCC1180", 236), new Enum("OCC1185", 237), new Enum("OCC1190", 238), new Enum("OCC1195", 239), new Enum("OCC1200", 240), new Enum("OCC1205", 241), new Enum("OCC1210", 242), new Enum("OCC1215", 243), new Enum("OCC1220", 244), new Enum("OCC1225", 245), new Enum("OCC1230", 246), new Enum("OCC1235", 247), new Enum("OCC1240", 248), new Enum("OCC1245", 249), new Enum("OCC1250", 250), new Enum("OCC1255", 251), new Enum("OCC1260", 252), new Enum("OCC1265", 253), new Enum("OCC1270", 254), new Enum("OCC1275", 255), new Enum("OCC1280", 256), new Enum("OCC1285", 257), new Enum("OCC1290", 258), new Enum("OTHER", 259) });
                }
            }

            /**
             * Factory class for creating occupation Value instances.
             *
             * <p>Provides static factory methods for creating new Value objects with proper
             * XMLBeans type initialization and schema validation.</p>
             *
             * @since 2026-01-24
             */
            public static final class Factory
            {
                /**
                 * Creates a new Value instance from an object.
                 * Converts the object to the appropriate occupation code value.
                 *
                 * @param obj Object the source object to convert to a Value
                 * @return Value a new occupation value instance
                 */
                public static Value newValue(final Object obj) {
                    return (Value)Value.type.newValue(obj);
                }

                /**
                 * Creates a new empty Value instance with default options.
                 *
                 * @return Value a new occupation value instance
                 */
                public static Value newInstance() {
                    return (Value)XmlBeans.getContextTypeLoader().newInstance(Value.type, (XmlOptions)null);
                }

                /**
                 * Creates a new empty Value instance with specified XMLBeans options.
                 *
                 * @param options XmlOptions the XML options for instance creation
                 * @return Value a new occupation value instance
                 */
                public static Value newInstance(final XmlOptions options) {
                    return (Value)XmlBeans.getContextTypeLoader().newInstance(Value.type, options);
                }

                /**
                 * Private constructor to prevent instantiation.
                 * Factory methods are static.
                 */
                private Factory() {
                }
            }
        }

        /**
         * Factory class for creating Occupation instances.
         *
         * <p>Provides static factory methods for creating new Occupation objects with proper
         * XMLBeans type initialization and schema validation.</p>
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new empty Occupation instance with default options.
             *
             * @return Occupation a new occupation instance
             */
            public static Occupation newInstance() {
                return (Occupation)XmlBeans.getContextTypeLoader().newInstance(Occupation.type, (XmlOptions)null);
            }

            /**
             * Creates a new empty Occupation instance with specified XMLBeans options.
             *
             * @param options XmlOptions the XML options for instance creation
             * @return Occupation a new occupation instance
             */
            public static Occupation newInstance(final XmlOptions options) {
                return (Occupation)XmlBeans.getContextTypeLoader().newInstance(Occupation.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             * Factory methods are static.
             */
            private Factory() {
            }
        }
    }

    /**
     * Education level classification interface for partner demographic information.
     *
     * <p>Provides standardized education attainment coding for the patient's partner using a classification
     * system with 6 defined education levels (ED001-ED006) plus a UN (unknown) option.</p>
     *
     * <p>Education level data is important for:</p>
     * <ul>
     *   <li>Socioeconomic status assessment in prenatal care</li>
     *   <li>Health literacy and education needs evaluation</li>
     *   <li>Social determinants of health documentation</li>
     *   <li>Patient education and communication strategy planning</li>
     * </ul>
     *
     * <p>The education level classification supports comprehensive family assessment and helps healthcare
     * providers tailor education materials and communication approaches appropriately.</p>
     *
     * @see PartnerInformation
     * @since 2026-01-24
     */
    public interface EducationLevel extends XmlString
    {
        /**
         * XMLBeans schema type for education level classification.
         * Used internally by Apache XMLBeans for type resolution and validation.
         */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(EducationLevel.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("educationlevel26c3elemtype");
        /** Education level code ED001 enumeration constant. */
        public static final Enum ED_001 = Enum.forString("ED001");
        /** Education level code ED002 enumeration constant. */
        public static final Enum ED_002 = Enum.forString("ED002");
        /** Education level code ED003 enumeration constant. */
        public static final Enum ED_003 = Enum.forString("ED003");
        /** Education level code ED004 enumeration constant. */
        public static final Enum ED_004 = Enum.forString("ED004");
        /** Education level code ED005 enumeration constant. */
        public static final Enum ED_005 = Enum.forString("ED005");
        /** Education level code ED006 enumeration constant. */
        public static final Enum ED_006 = Enum.forString("ED006");
        /** Unknown education level (UN) enumeration constant. */
        public static final Enum UN = Enum.forString("UN");
        /** Integer constant for ED001 education level (1). */
        public static final int INT_ED_001 = 1;
        /** Integer constant for ED002 education level (2). */
        public static final int INT_ED_002 = 2;
        /** Integer constant for ED003 education level (3). */
        public static final int INT_ED_003 = 3;
        /** Integer constant for ED004 education level (4). */
        public static final int INT_ED_004 = 4;
        /** Integer constant for ED005 education level (5). */
        public static final int INT_ED_005 = 5;
        /** Integer constant for ED006 education level (6). */
        public static final int INT_ED_006 = 6;
        /** Integer constant for UN (unknown) education level (7). */
        public static final int INT_UN = 7;

        /**
         * Gets the enumeration value as a StringEnumAbstractBase.
         * Provides access to the underlying enumeration base for advanced type operations.
         *
         * @return StringEnumAbstractBase the enumeration value base object
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the education level value using a StringEnumAbstractBase enumeration.
         * Allows setting with base enumeration type for framework interoperability.
         *
         * @param p0 StringEnumAbstractBase the enumeration value to set
         */
        void set(final StringEnumAbstractBase p0);

        /**
         * Enumeration implementation for education level classification values.
         *
         * <p>Provides concrete enumeration instances for all 7 education level codes (ED001-ED006 and UN),
         * extending StringEnumAbstractBase to integrate with the Apache XMLBeans type system.</p>
         *
         * <p>Each enum instance maps to both a string representation (e.g., "ED001") and an
         * integer value (1-7) for efficient storage and comparison operations.</p>
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            /** Integer constant for ED001 education level (1). */
            static final int INT_ED_001 = 1;
            /** Integer constant for ED002 education level (2). */
            static final int INT_ED_002 = 2;
            /** Integer constant for ED003 education level (3). */
            static final int INT_ED_003 = 3;
            /** Integer constant for ED004 education level (4). */
            static final int INT_ED_004 = 4;
            /** Integer constant for ED005 education level (5). */
            static final int INT_ED_005 = 5;
            /** Integer constant for ED006 education level (6). */
            static final int INT_ED_006 = 6;
            /** Integer constant for UN (unknown) education level (7). */
            static final int INT_UN = 7;
            /** Lookup table mapping string and integer values to enumeration instances. */
            public static final StringEnumAbstractBase.Table table;
            /** Serialization version identifier. */
            private static final long serialVersionUID = 1L;

            /**
             * Looks up an enumeration instance by its string representation.
             *
             * @param s String the education level code string (e.g., "ED001", "UN")
             * @return Enum the corresponding enumeration instance, or null if not found
             */
            public static Enum forString(final String s) {
                return (Enum)Enum.table.forString(s);
            }

            /**
             * Looks up an enumeration instance by its integer value.
             *
             * @param i int the education level code integer (1-7)
             * @return Enum the corresponding enumeration instance, or null if not found
             */
            public static Enum forInt(final int i) {
                return (Enum)Enum.table.forInt(i);
            }

            /**
             * Private constructor for enumeration instances.
             * Called during static initialization to create all 7 education level code instances.
             *
             * @param s String the education level code string representation
             * @param i int the education level code integer value
             */
            private Enum(final String s, final int i) {
                super(s, i);
            }

            /**
             * Resolves the enumeration instance during deserialization.
             * Ensures singleton semantics by returning the canonical instance from the lookup table.
             *
             * @return Object the canonical enumeration instance for this integer value
             */
            private Object readResolve() {
                return forInt(this.intValue());
            }
            
            static {
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("ED001", 1), new Enum("ED002", 2), new Enum("ED003", 3), new Enum("ED004", 4), new Enum("ED005", 5), new Enum("ED006", 6), new Enum("UN", 7) });
            }
        }

        /**
         * Factory class for creating EducationLevel instances.
         *
         * <p>Provides static factory methods for creating new EducationLevel objects with proper
         * XMLBeans type initialization and schema validation.</p>
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new EducationLevel instance from an object.
             * Converts the object to the appropriate education level code value.
             *
             * @param obj Object the source object to convert to an EducationLevel
             * @return EducationLevel a new education level instance
             */
            public static EducationLevel newValue(final Object obj) {
                return (EducationLevel)EducationLevel.type.newValue(obj);
            }

            /**
             * Creates a new empty EducationLevel instance with default options.
             *
             * @return EducationLevel a new education level instance
             */
            public static EducationLevel newInstance() {
                return (EducationLevel)XmlBeans.getContextTypeLoader().newInstance(EducationLevel.type, (XmlOptions)null);
            }

            /**
             * Creates a new empty EducationLevel instance with specified XMLBeans options.
             *
             * @param options XmlOptions the XML options for instance creation
             * @return EducationLevel a new education level instance
             */
            public static EducationLevel newInstance(final XmlOptions options) {
                return (EducationLevel)XmlBeans.getContextTypeLoader().newInstance(EducationLevel.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             * Factory methods are static.
             */
            private Factory() {
            }
        }
    }

    /**
     * Age interface for partner demographic information.
     *
     * <p>Provides type-safe integer representation of the partner's age in years. Age data is important
     * for comprehensive family assessment, genetic risk evaluation, and social determinants of health
     * documentation in maternal care.</p>
     *
     * <p>The Age interface extends XmlInt to provide XMLBeans type validation and ensures proper
     * schema compliance for age data in BCAR form submissions.</p>
     *
     * @see PartnerInformation
     * @since 2026-01-24
     */
    public interface Age extends XmlInt
    {
        /**
         * XMLBeans schema type for age data.
         * Used internally by Apache XMLBeans for type resolution and validation.
         */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Age.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("age0010elemtype");

        /**
         * Factory class for creating Age instances.
         *
         * <p>Provides static factory methods for creating new Age objects with proper
         * XMLBeans type initialization and schema validation.</p>
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a new Age instance from an object.
             * Converts the object to an integer age value.
             *
             * @param obj Object the source object to convert to an Age
             * @return Age a new age instance
             */
            public static Age newValue(final Object obj) {
                return (Age)Age.type.newValue(obj);
            }

            /**
             * Creates a new empty Age instance with default options.
             *
             * @return Age a new age instance
             */
            public static Age newInstance() {
                return (Age)XmlBeans.getContextTypeLoader().newInstance(Age.type, (XmlOptions)null);
            }

            /**
             * Creates a new empty Age instance with specified XMLBeans options.
             *
             * @param options XmlOptions the XML options for instance creation
             * @return Age a new age instance
             */
            public static Age newInstance(final XmlOptions options) {
                return (Age)XmlBeans.getContextTypeLoader().newInstance(Age.type, options);
            }

            /**
             * Private constructor to prevent instantiation.
             * Factory methods are static.
             */
            private Factory() {
            }
        }
    }

    /**
     * Factory class for creating PartnerInformation instances.
     *
     * <p>Provides comprehensive static factory methods for creating new PartnerInformation objects
     * with proper XMLBeans type initialization and schema validation. Includes methods for parsing
     * partner information data from various sources including strings, files, URLs, streams, readers,
     * and DOM nodes.</p>
     *
     * <p>The factory supports both default and custom XmlOptions for fine-grained control over
     * parsing behavior, validation, and error handling during partner information deserialization.</p>
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new empty PartnerInformation instance with default options.
         *
         * @return PartnerInformation a new partner information instance
         */
        public static PartnerInformation newInstance() {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().newInstance(PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty PartnerInformation instance with specified XMLBeans options.
         *
         * @param options XmlOptions the XML options for instance creation
         * @return PartnerInformation a new partner information instance
         */
        public static PartnerInformation newInstance(final XmlOptions options) {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().newInstance(PartnerInformation.type, options);
        }

        /**
         * Parses partner information from an XML string with default options.
         *
         * @param xmlAsString String the XML document to parse
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static PartnerInformation parse(final String xmlAsString) throws XmlException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(xmlAsString, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Parses partner information from an XML string with specified options.
         *
         * @param xmlAsString String the XML document to parse
         * @param options XmlOptions the parsing options
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static PartnerInformation parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(xmlAsString, PartnerInformation.type, options);
        }

        /**
         * Parses partner information from a File with default options.
         *
         * @param file File the file containing the XML document
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static PartnerInformation parse(final File file) throws XmlException, IOException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(file, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Parses partner information from a File with specified options.
         *
         * @param file File the file containing the XML document
         * @param options XmlOptions the parsing options
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static PartnerInformation parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(file, PartnerInformation.type, options);
        }

        /**
         * Parses partner information from a URL with default options.
         *
         * @param u URL the URL pointing to the XML document
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs accessing the URL
         */
        public static PartnerInformation parse(final URL u) throws XmlException, IOException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(u, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Parses partner information from a URL with specified options.
         *
         * @param u URL the URL pointing to the XML document
         * @param options XmlOptions the parsing options
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs accessing the URL
         */
        public static PartnerInformation parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(u, PartnerInformation.type, options);
        }

        /**
         * Parses partner information from an InputStream with default options.
         *
         * @param is InputStream the input stream containing the XML document
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading the stream
         */
        public static PartnerInformation parse(final InputStream is) throws XmlException, IOException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(is, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Parses partner information from an InputStream with specified options.
         *
         * @param is InputStream the input stream containing the XML document
         * @param options XmlOptions the parsing options
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading the stream
         */
        public static PartnerInformation parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(is, PartnerInformation.type, options);
        }

        /**
         * Parses partner information from a Reader with default options.
         *
         * @param r Reader the reader providing the XML document
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static PartnerInformation parse(final Reader r) throws XmlException, IOException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(r, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Parses partner information from a Reader with specified options.
         *
         * @param r Reader the reader providing the XML document
         * @param options XmlOptions the parsing options
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static PartnerInformation parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(r, PartnerInformation.type, options);
        }

        /**
         * Parses partner information from an XMLStreamReader with default options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the XML document
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static PartnerInformation parse(final XMLStreamReader sr) throws XmlException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(sr, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Parses partner information from an XMLStreamReader with specified options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the XML document
         * @param options XmlOptions the parsing options
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static PartnerInformation parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(sr, PartnerInformation.type, options);
        }

        /**
         * Parses partner information from a DOM Node with default options.
         *
         * @param node Node the DOM node representing the XML element
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static PartnerInformation parse(final Node node) throws XmlException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(node, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Parses partner information from a DOM Node with specified options.
         *
         * @param node Node the DOM node representing the XML element
         * @param options XmlOptions the parsing options
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         */
        public static PartnerInformation parse(final Node node, final XmlOptions options) throws XmlException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(node, PartnerInformation.type, options);
        }

        /**
         * Parses partner information from an XMLInputStream with default options.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader instead.
         * @param xis XMLInputStream the input stream containing the XML document
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws XMLStreamException if an error occurs processing the XML stream
         */
        @Deprecated
        public static PartnerInformation parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(xis, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Parses partner information from an XMLInputStream with specified options.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader instead.
         * @param xis XMLInputStream the input stream containing the XML document
         * @param options XmlOptions the parsing options
         * @return PartnerInformation the parsed partner information instance
         * @throws XmlException if the XML is malformed or does not conform to the schema
         * @throws XMLStreamException if an error occurs processing the XML stream
         */
        @Deprecated
        public static PartnerInformation parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (PartnerInformation)XmlBeans.getContextTypeLoader().parse(xis, PartnerInformation.type, options);
        }

        /**
         * Creates a validating XMLInputStream wrapper with default options.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader with validation instead.
         * @param xis XMLInputStream the input stream to wrap with validation
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if an error occurs setting up the validation stream
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PartnerInformation.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream wrapper with specified options.
         *
         * @deprecated XMLInputStream is deprecated in XMLBeans. Use XMLStreamReader with validation instead.
         * @param xis XMLInputStream the input stream to wrap with validation
         * @param options XmlOptions the validation options
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if an error occurs setting up the validation stream
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PartnerInformation.type, options);
        }

        /**
         * Private constructor to prevent instantiation.
         * Factory methods are static.
         */
        private Factory() {
        }
    }
}
