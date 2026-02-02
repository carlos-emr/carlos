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
import org.apache.xmlbeans.XmlToken;
import org.apache.xmlbeans.XmlInt;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Apache XMLBeans interface representing patient information for AR2005 (British Columbia Antenatal Record) forms.
 *
 * <p>This interface provides access to comprehensive patient demographic and clinical information required for
 * prenatal care documentation in British Columbia. It includes patient identification, contact information,
 * demographics, health insurance details, and clinical data such as allergies and current medications.</p>
 *
 * <p>This is an Apache XMLBeans-generated interface that provides type-safe access to XML data conforming to
 * the AR2005 schema. All nested interfaces and factory classes are auto-generated to support XML serialization
 * and deserialization of patient information.</p>
 *
 * <p><strong>Healthcare Context:</strong> The AR2005 form (British Columbia Antenatal Record) is used for
 * comprehensive prenatal care tracking including patient demographics, medical history, pregnancy progression,
 * and delivery information. This interface supports the electronic management of Protected Health Information
 * (PHI) and must be used in compliance with PIPEDA and provincial privacy regulations.</p>
 *
 * @see ca.openosp.openo.ar2005
 * @see org.apache.xmlbeans.XmlObject
 * @since 2026-01-24
 */
public interface PatientInformation extends XmlObject
{
    /**
     * SchemaType for the PatientInformation interface.
     * Used by Apache XMLBeans for type system resolution and XML binding.
     */
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(PatientInformation.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("patientinformation833etype");

    /**
     * Gets the patient's last name (surname).
     *
     * @return String the patient's last name
     */
    String getLastName();

    /**
     * Gets the patient's last name as an XmlString object.
     *
     * @return XmlString the patient's last name as an XML typed value
     */
    XmlString xgetLastName();

    /**
     * Sets the patient's last name.
     *
     * @param p0 String the patient's last name to set
     */
    void setLastName(final String p0);

    /**
     * Sets the patient's last name using an XmlString object.
     *
     * @param p0 XmlString the patient's last name as an XML typed value
     */
    void xsetLastName(final XmlString p0);

    /**
     * Gets the patient's first name (given name).
     *
     * @return String the patient's first name
     */
    String getFirstName();

    /**
     * Gets the patient's first name as an XmlString object.
     *
     * @return XmlString the patient's first name as an XML typed value
     */
    XmlString xgetFirstName();

    /**
     * Sets the patient's first name.
     *
     * @param p0 String the patient's first name to set
     */
    void setFirstName(final String p0);

    /**
     * Sets the patient's first name using an XmlString object.
     *
     * @param p0 XmlString the patient's first name as an XML typed value
     */
    void xsetFirstName(final XmlString p0);

    /**
     * Gets the patient's street address.
     *
     * @return String the patient's street address
     */
    String getAddress();

    /**
     * Gets the patient's street address as an XmlString object.
     *
     * @return XmlString the patient's street address as an XML typed value
     */
    XmlString xgetAddress();

    /**
     * Sets the patient's street address.
     *
     * @param p0 String the patient's street address to set
     */
    void setAddress(final String p0);

    /**
     * Sets the patient's street address using an XmlString object.
     *
     * @param p0 XmlString the patient's street address as an XML typed value
     */
    void xsetAddress(final XmlString p0);

    /**
     * Gets the patient's apartment or unit number.
     *
     * @return String the patient's apartment/unit number
     */
    String getApt();

    /**
     * Gets the patient's apartment or unit number as an XmlString object.
     *
     * @return XmlString the patient's apartment/unit number as an XML typed value
     */
    XmlString xgetApt();

    /**
     * Sets the patient's apartment or unit number.
     *
     * @param p0 String the patient's apartment/unit number to set
     */
    void setApt(final String p0);

    /**
     * Sets the patient's apartment or unit number using an XmlString object.
     *
     * @param p0 XmlString the patient's apartment/unit number as an XML typed value
     */
    void xsetApt(final XmlString p0);

    /**
     * Gets the patient's city of residence.
     *
     * @return String the patient's city
     */
    String getCity();

    /**
     * Gets the patient's city of residence as an XmlString object.
     *
     * @return XmlString the patient's city as an XML typed value
     */
    XmlString xgetCity();

    /**
     * Sets the patient's city of residence.
     *
     * @param p0 String the patient's city to set
     */
    void setCity(final String p0);

    /**
     * Sets the patient's city of residence using an XmlString object.
     *
     * @param p0 XmlString the patient's city as an XML typed value
     */
    void xsetCity(final XmlString p0);

    /**
     * Gets the patient's province or territory of residence.
     *
     * @return Province.Enum the patient's province/territory code (e.g., CA-ON, CA-BC)
     */
    Province.Enum getProvince();

    /**
     * Gets the patient's province or territory as a Province object.
     *
     * @return Province the patient's province as an XML typed value
     */
    Province xgetProvince();

    /**
     * Sets the patient's province or territory of residence.
     *
     * @param p0 Province.Enum the patient's province/territory code to set
     */
    void setProvince(final Province.Enum p0);

    /**
     * Sets the patient's province or territory using a Province object.
     *
     * @param p0 Province the patient's province as an XML typed value
     */
    void xsetProvince(final Province p0);

    /**
     * Gets the patient's postal code.
     *
     * @return String the patient's postal code (Canadian format: A1A 1A1 or international)
     */
    String getPostalCode();

    /**
     * Gets the patient's postal code as a PostalCode object.
     *
     * @return PostalCode the patient's postal code as an XML typed value
     */
    PostalCode xgetPostalCode();

    /**
     * Sets the patient's postal code.
     *
     * @param p0 String the patient's postal code to set
     */
    void setPostalCode(final String p0);

    /**
     * Sets the patient's postal code using a PostalCode object.
     *
     * @param p0 PostalCode the patient's postal code as an XML typed value
     */
    void xsetPostalCode(final PostalCode p0);

    /**
     * Gets the patient's home phone number.
     *
     * @return String the patient's home phone number
     */
    String getHomePhone();

    /**
     * Gets the patient's home phone number as a HomePhone object.
     *
     * @return HomePhone the patient's home phone number as an XML typed value
     */
    HomePhone xgetHomePhone();

    /**
     * Sets the patient's home phone number.
     *
     * @param p0 String the patient's home phone number to set
     */
    void setHomePhone(final String p0);

    /**
     * Sets the patient's home phone number using a HomePhone object.
     *
     * @param p0 HomePhone the patient's home phone number as an XML typed value
     */
    void xsetHomePhone(final HomePhone p0);

    /**
     * Gets the patient's work phone number.
     *
     * @return String the patient's work phone number
     */
    String getWorkPhone();

    /**
     * Gets the patient's work phone number as a WorkPhone object.
     *
     * @return WorkPhone the patient's work phone number as an XML typed value
     */
    WorkPhone xgetWorkPhone();

    /**
     * Sets the patient's work phone number.
     *
     * @param p0 String the patient's work phone number to set
     */
    void setWorkPhone(final String p0);

    /**
     * Sets the patient's work phone number using a WorkPhone object.
     *
     * @param p0 WorkPhone the patient's work phone number as an XML typed value
     */
    void xsetWorkPhone(final WorkPhone p0);

    /**
     * Gets the patient's preferred language for communication.
     *
     * @return Language.Enum the ISO 639-3 language code (e.g., ENG, FRA, CMN)
     */
    Language.Enum getLanguage();

    /**
     * Gets the patient's preferred language as a Language object.
     *
     * @return Language the patient's language as an XML typed value
     */
    Language xgetLanguage();

    /**
     * Sets the patient's preferred language for communication.
     *
     * @param p0 Language.Enum the ISO 639-3 language code to set
     */
    void setLanguage(final Language.Enum p0);

    /**
     * Sets the patient's preferred language using a Language object.
     *
     * @param p0 Language the patient's language as an XML typed value
     */
    void xsetLanguage(final Language p0);

    /**
     * Gets the patient's date of birth.
     *
     * @return Calendar the patient's date of birth
     */
    Calendar getDob();

    /**
     * Gets the patient's date of birth as an XmlDate object.
     *
     * @return XmlDate the patient's date of birth as an XML typed value
     */
    XmlDate xgetDob();

    /**
     * Sets the patient's date of birth.
     *
     * @param p0 Calendar the patient's date of birth to set
     */
    void setDob(final Calendar p0);

    /**
     * Sets the patient's date of birth using an XmlDate object.
     *
     * @param p0 XmlDate the patient's date of birth as an XML typed value
     */
    void xsetDob(final XmlDate p0);

    /**
     * Gets the patient's age in years.
     *
     * @return int the patient's age in years
     */
    int getAge();

    /**
     * Gets the patient's age as an Age object.
     *
     * @return Age the patient's age as an XML typed value
     */
    Age xgetAge();

    /**
     * Sets the patient's age in years.
     *
     * @param p0 int the patient's age to set
     */
    void setAge(final int p0);

    /**
     * Sets the patient's age using an Age object.
     *
     * @param p0 Age the patient's age as an XML typed value
     */
    void xsetAge(final Age p0);

    /**
     * Gets the patient's occupation information.
     *
     * @return Occupation the patient's occupation details
     */
    Occupation getOccupation();

    /**
     * Sets the patient's occupation information.
     *
     * @param p0 Occupation the patient's occupation details to set
     */
    void setOccupation(final Occupation p0);

    /**
     * Creates and adds a new Occupation object to the patient information.
     *
     * @return Occupation the newly created occupation object
     */
    Occupation addNewOccupation();

    /**
     * Gets the patient's highest level of education completed.
     *
     * @return LevelOfEducation.Enum the patient's education level code
     */
    LevelOfEducation.Enum getLevelOfEducation();

    /**
     * Gets the patient's level of education as a LevelOfEducation object.
     *
     * @return LevelOfEducation the patient's education level as an XML typed value
     */
    LevelOfEducation xgetLevelOfEducation();

    /**
     * Sets the patient's highest level of education completed.
     *
     * @param p0 LevelOfEducation.Enum the patient's education level code to set
     */
    void setLevelOfEducation(final LevelOfEducation.Enum p0);

    /**
     * Sets the patient's level of education using a LevelOfEducation object.
     *
     * @param p0 LevelOfEducation the patient's education level as an XML typed value
     */
    void xsetLevelOfEducation(final LevelOfEducation p0);

    /**
     * Gets the patient's Health Insurance Number (HIN) information.
     *
     * <p>The HIN is the provincial health insurance identifier used for billing and
     * eligibility verification. Format varies by province (e.g., Ontario uses 10 digits
     * plus 2-character version code).</p>
     *
     * @return Hin the patient's health insurance number details
     */
    Hin getHin();

    /**
     * Sets the patient's Health Insurance Number (HIN) information.
     *
     * @param p0 Hin the patient's health insurance number details to set
     */
    void setHin(final Hin p0);

    /**
     * Creates and adds a new HIN object to the patient information.
     *
     * @return Hin the newly created health insurance number object
     */
    Hin addNewHin();

    /**
     * Gets the patient's medical record file number.
     *
     * <p>This is the internal EMR chart number used for patient identification
     * within the healthcare facility.</p>
     *
     * @return String the patient's file number
     */
    String getFileNo();

    /**
     * Gets the patient's medical record file number as an XmlString object.
     *
     * @return XmlString the patient's file number as an XML typed value
     */
    XmlString xgetFileNo();

    /**
     * Sets the patient's medical record file number.
     *
     * @param p0 String the patient's file number to set
     */
    void setFileNo(final String p0);

    /**
     * Sets the patient's medical record file number using an XmlString object.
     *
     * @param p0 XmlString the patient's file number as an XML typed value
     */
    void xsetFileNo(final XmlString p0);

    /**
     * Gets the patient's ethnic background information.
     *
     * <p>Used for cultural competency in healthcare delivery and for tracking
     * health disparities in specific populations.</p>
     *
     * @return EthnicBackground the patient's ethnic background details
     */
    EthnicBackground getEthnicBackground();

    /**
     * Sets the patient's ethnic background information.
     *
     * @param p0 EthnicBackground the patient's ethnic background details to set
     */
    void setEthnicBackground(final EthnicBackground p0);

    /**
     * Creates and adds a new EthnicBackground object to the patient information.
     *
     * @return EthnicBackground the newly created ethnic background object
     */
    EthnicBackground addNewEthnicBackground();

    /**
     * Gets the patient's marital status.
     *
     * @return MaritalStatus.Enum the patient's marital status code (e.g., M=Married, S=Single, D=Divorced)
     */
    MaritalStatus.Enum getMaritalStatus();

    /**
     * Gets the patient's marital status as a MaritalStatus object.
     *
     * @return MaritalStatus the patient's marital status as an XML typed value
     */
    MaritalStatus xgetMaritalStatus();

    /**
     * Sets the patient's marital status.
     *
     * @param p0 MaritalStatus.Enum the patient's marital status code to set
     */
    void setMaritalStatus(final MaritalStatus.Enum p0);

    /**
     * Sets the patient's marital status using a MaritalStatus object.
     *
     * @param p0 MaritalStatus the patient's marital status as an XML typed value
     */
    void xsetMaritalStatus(final MaritalStatus p0);

    /**
     * Gets the patient's allergies information.
     *
     * <p>Critical clinical information documenting known drug and non-drug allergies,
     * reactions, and severity. Must be prominently displayed to prevent adverse events.</p>
     *
     * @return String the patient's allergies description
     */
    String getAllergies();

    /**
     * Gets the patient's allergies information as an XmlString object.
     *
     * @return XmlString the patient's allergies as an XML typed value
     */
    XmlString xgetAllergies();

    /**
     * Sets the patient's allergies information.
     *
     * @param p0 String the patient's allergies description to set
     */
    void setAllergies(final String p0);

    /**
     * Sets the patient's allergies information using an XmlString object.
     *
     * @param p0 XmlString the patient's allergies as an XML typed value
     */
    void xsetAllergies(final XmlString p0);

    /**
     * Gets the patient's current medications list.
     *
     * <p>Documents all current prescription and over-the-counter medications,
     * including dosage, frequency, and route of administration.</p>
     *
     * @return Medications the patient's medications details
     */
    Medications getMedications();

    /**
     * Sets the patient's current medications list.
     *
     * @param p0 Medications the patient's medications details to set
     */
    void setMedications(final Medications p0);

    /**
     * Creates and adds a new Medications object to the patient information.
     *
     * @return Medications the newly created medications object
     */
    Medications addNewMedications();

    /**
     * Province enumeration interface for Canadian provinces/territories and international locations.
     *
     * <p>Provides standardized province and territory codes using ISO 3166-2:CA format for Canadian
     * jurisdictions (e.g., CA-ON for Ontario, CA-BC for British Columbia) plus codes for USA,
     * out-of-province, and out-of-country patients.</p>
     *
     * <p>Used for address information and to determine jurisdiction-specific healthcare rules,
     * billing codes, and regulatory compliance requirements.</p>
     *
     * @since 2026-01-24
     */
    public interface Province extends XmlString
    {
        /**
         * SchemaType for the Province interface.
         */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Province.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("province34f2elemtype");
        public static final Enum CA_AB = Enum.forString("CA-AB");
        public static final Enum CA_BC = Enum.forString("CA-BC");
        public static final Enum CA_MB = Enum.forString("CA-MB");
        public static final Enum CA_NB = Enum.forString("CA-NB");
        public static final Enum CA_NL = Enum.forString("CA-NL");
        public static final Enum CA_NS = Enum.forString("CA-NS");
        public static final Enum CA_NU = Enum.forString("CA-NU");
        public static final Enum CA_ON = Enum.forString("CA-ON");
        public static final Enum CA_PE = Enum.forString("CA-PE");
        public static final Enum CA_QC = Enum.forString("CA-QC");
        public static final Enum CA_SK = Enum.forString("CA-SK");
        public static final Enum CA_YT = Enum.forString("CA-YT");
        public static final Enum USA = Enum.forString("USA");
        public static final Enum OUTP = Enum.forString("OUTP");
        /** Out of country location code. */
        public static final Enum OUTC = Enum.forString("OUTC");

        /** Integer value for Alberta (CA-AB). */
        public static final int INT_CA_AB = 1;
        public static final int INT_CA_BC = 2;
        public static final int INT_CA_MB = 3;
        public static final int INT_CA_NB = 4;
        public static final int INT_CA_NL = 5;
        public static final int INT_CA_NS = 6;
        public static final int INT_CA_NU = 7;
        public static final int INT_CA_ON = 8;
        public static final int INT_CA_PE = 9;
        public static final int INT_CA_QC = 10;
        public static final int INT_CA_SK = 11;
        public static final int INT_CA_YT = 12;
        public static final int INT_USA = 13;
        public static final int INT_OUTP = 14;
        /** Integer value for out of country (OUTC). */
        public static final int INT_OUTC = 15;

        /**
         * Gets the enum value for this province.
         *
         * @return StringEnumAbstractBase the enum value
         */
        StringEnumAbstractBase enumValue();

        /**
         * Sets the enum value for this province.
         *
         * @param p0 StringEnumAbstractBase the enum value to set
         */
        void set(final StringEnumAbstractBase p0);

        /**
         * Enumeration implementation for Province codes.
         *
         * <p>Provides type-safe enumeration values for Canadian provinces, territories,
         * and international locations used in patient addresses.</p>
         *
         * @since 2026-01-24
         */
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_CA_AB = 1;
            static final int INT_CA_BC = 2;
            static final int INT_CA_MB = 3;
            static final int INT_CA_NB = 4;
            static final int INT_CA_NL = 5;
            static final int INT_CA_NS = 6;
            static final int INT_CA_NU = 7;
            static final int INT_CA_ON = 8;
            static final int INT_CA_PE = 9;
            static final int INT_CA_QC = 10;
            static final int INT_CA_SK = 11;
            static final int INT_CA_YT = 12;
            static final int INT_USA = 13;
            static final int INT_OUTP = 14;
            static final int INT_OUTC = 15;
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
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("CA-AB", 1), new Enum("CA-BC", 2), new Enum("CA-MB", 3), new Enum("CA-NB", 4), new Enum("CA-NL", 5), new Enum("CA-NS", 6), new Enum("CA-NU", 7), new Enum("CA-ON", 8), new Enum("CA-PE", 9), new Enum("CA-QC", 10), new Enum("CA-SK", 11), new Enum("CA-YT", 12), new Enum("USA", 13), new Enum("OUTP", 14), new Enum("OUTC", 15) });
            }
        }

        /**
         * Factory class for creating Province instances.
         *
         * <p>Provides static factory methods for creating Province objects from various sources.</p>
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a Province value from an object.
             *
             * @param obj Object the source object
             * @return Province the created province value
             */
            public static Province newValue(final Object obj) {
                return (Province)Province.type.newValue(obj);
            }

            /**
             * Creates a new Province instance with default options.
             *
             * @return Province the new province instance
             */
            public static Province newInstance() {
                return (Province)XmlBeans.getContextTypeLoader().newInstance(Province.type, (XmlOptions)null);
            }

            /**
             * Creates a new Province instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/generation options
             * @return Province the new province instance
             */
            public static Province newInstance(final XmlOptions options) {
                return (Province)XmlBeans.getContextTypeLoader().newInstance(Province.type, options);
            }

            /** Private constructor to prevent instantiation. */
            private Factory() {
            }
        }
    }

    /**
     * PostalCode interface for patient postal/zip codes.
     *
     * <p>Validates and stores postal codes in Canadian format (A1A 1A1) or international formats.</p>
     *
     * @since 2026-01-24
     */
    public interface PostalCode extends XmlString
    {
        /** SchemaType for the PostalCode interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(PostalCode.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("postalcode01caelemtype");

        /**
         * Factory class for creating PostalCode instances.
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a PostalCode value from an object.
             *
             * @param obj Object the source object
             * @return PostalCode the created postal code value
             */
            public static PostalCode newValue(final Object obj) {
                return (PostalCode)PostalCode.type.newValue(obj);
            }

            /**
             * Creates a new PostalCode instance with default options.
             *
             * @return PostalCode the new postal code instance
             */
            public static PostalCode newInstance() {
                return (PostalCode)XmlBeans.getContextTypeLoader().newInstance(PostalCode.type, (XmlOptions)null);
            }

            /**
             * Creates a new PostalCode instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/generation options
             * @return PostalCode the new postal code instance
             */
            public static PostalCode newInstance(final XmlOptions options) {
                return (PostalCode)XmlBeans.getContextTypeLoader().newInstance(PostalCode.type, options);
            }

            /** Private constructor to prevent instantiation. */
            private Factory() {
            }
        }
    }

    /**
     * HomePhone interface for patient home phone numbers.
     *
     * <p>Stores patient home telephone contact information for appointment reminders and follow-up.</p>
     *
     * @since 2026-01-24
     */
    public interface HomePhone extends XmlString
    {
        /** SchemaType for the HomePhone interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(HomePhone.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("homephonee963elemtype");

        /**
         * Factory class for creating HomePhone instances.
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a HomePhone value from an object.
             *
             * @param obj Object the source object
             * @return HomePhone the created home phone value
             */
            public static HomePhone newValue(final Object obj) {
                return (HomePhone)HomePhone.type.newValue(obj);
            }

            /**
             * Creates a new HomePhone instance with default options.
             *
             * @return HomePhone the new home phone instance
             */
            public static HomePhone newInstance() {
                return (HomePhone)XmlBeans.getContextTypeLoader().newInstance(HomePhone.type, (XmlOptions)null);
            }

            /**
             * Creates a new HomePhone instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/generation options
             * @return HomePhone the new home phone instance
             */
            public static HomePhone newInstance(final XmlOptions options) {
                return (HomePhone)XmlBeans.getContextTypeLoader().newInstance(HomePhone.type, options);
            }

            /** Private constructor to prevent instantiation. */
            private Factory() {
            }
        }
    }

    /**
     * WorkPhone interface for patient work phone numbers.
     *
     * <p>Stores patient work telephone contact information for daytime contact during business hours.</p>
     *
     * @since 2026-01-24
     */
    public interface WorkPhone extends XmlString
    {
        /** SchemaType for the WorkPhone interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(WorkPhone.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("workphone1015elemtype");

        /**
         * Factory class for creating WorkPhone instances.
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates a WorkPhone value from an object.
             *
             * @param obj Object the source object
             * @return WorkPhone the created work phone value
             */
            public static WorkPhone newValue(final Object obj) {
                return (WorkPhone)WorkPhone.type.newValue(obj);
            }

            /**
             * Creates a new WorkPhone instance with default options.
             *
             * @return WorkPhone the new work phone instance
             */
            public static WorkPhone newInstance() {
                return (WorkPhone)XmlBeans.getContextTypeLoader().newInstance(WorkPhone.type, (XmlOptions)null);
            }

            /**
             * Creates a new WorkPhone instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/generation options
             * @return WorkPhone the new work phone instance
             */
            public static WorkPhone newInstance(final XmlOptions options) {
                return (WorkPhone)XmlBeans.getContextTypeLoader().newInstance(WorkPhone.type, options);
            }

            /** Private constructor to prevent instantiation. */
            private Factory() {
            }
        }
    }

    /**
     * Language interface for patient preferred language using ISO 639-3 codes.
     *
     * <p>Supports comprehensive language selection for patient communication preferences including
     * major world languages and sign languages. Uses ISO 639-3 three-letter language codes for
     * international standardization (e.g., ENG=English, FRA=French, CMN=Mandarin Chinese).</p>
     *
     * <p>Critical for culturally competent care and ensuring effective patient-provider communication,
     * interpreter services, and translated health education materials.</p>
     *
     * @since 2026-01-24
     */
    public interface Language extends XmlString
    {
        /** SchemaType for the Language interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Language.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("language69caelemtype");

        /** Afar language code (AAR). */
        public static final Enum AAR = Enum.forString("AAR");
        public static final Enum AFR = Enum.forString("AFR");
        public static final Enum AKA = Enum.forString("AKA");
        public static final Enum SQI = Enum.forString("SQI");
        public static final Enum ASE = Enum.forString("ASE");
        public static final Enum AMH = Enum.forString("AMH");
        public static final Enum ARA = Enum.forString("ARA");
        public static final Enum ARG = Enum.forString("ARG");
        public static final Enum HYE = Enum.forString("HYE");
        public static final Enum ASM = Enum.forString("ASM");
        public static final Enum AVA = Enum.forString("AVA");
        public static final Enum AYM = Enum.forString("AYM");
        public static final Enum AZE = Enum.forString("AZE");
        public static final Enum BAM = Enum.forString("BAM");
        public static final Enum BAK = Enum.forString("BAK");
        public static final Enum EUS = Enum.forString("EUS");
        public static final Enum BEL = Enum.forString("BEL");
        public static final Enum BEN = Enum.forString("BEN");
        public static final Enum BIS = Enum.forString("BIS");
        public static final Enum BOS = Enum.forString("BOS");
        public static final Enum BRE = Enum.forString("BRE");
        public static final Enum BUL = Enum.forString("BUL");
        public static final Enum MYA = Enum.forString("MYA");
        public static final Enum CAT = Enum.forString("CAT");
        public static final Enum KHM = Enum.forString("KHM");
        public static final Enum CHA = Enum.forString("CHA");
        public static final Enum CHE = Enum.forString("CHE");
        public static final Enum YUE = Enum.forString("YUE");
        public static final Enum CMN = Enum.forString("CMN");
        public static final Enum CHV = Enum.forString("CHV");
        public static final Enum COR = Enum.forString("COR");
        public static final Enum COS = Enum.forString("COS");
        public static final Enum CRE = Enum.forString("CRE");
        public static final Enum HRV = Enum.forString("HRV");
        public static final Enum CES = Enum.forString("CES");
        public static final Enum DAN = Enum.forString("DAN");
        public static final Enum DIV = Enum.forString("DIV");
        public static final Enum NLD = Enum.forString("NLD");
        public static final Enum DZO = Enum.forString("DZO");
        public static final Enum ENG = Enum.forString("ENG");
        public static final Enum EST = Enum.forString("EST");
        public static final Enum EWE = Enum.forString("EWE");
        public static final Enum FAO = Enum.forString("FAO");
        public static final Enum FIJ = Enum.forString("FIJ");
        public static final Enum FIL = Enum.forString("FIL");
        public static final Enum FIN = Enum.forString("FIN");
        public static final Enum FRA = Enum.forString("FRA");
        public static final Enum FUL = Enum.forString("FUL");
        public static final Enum GLG = Enum.forString("GLG");
        public static final Enum LUG = Enum.forString("LUG");
        public static final Enum KAT = Enum.forString("KAT");
        public static final Enum DEU = Enum.forString("DEU");
        public static final Enum GRN = Enum.forString("GRN");
        public static final Enum GUJ = Enum.forString("GUJ");
        public static final Enum HAT = Enum.forString("HAT");
        public static final Enum HAU = Enum.forString("HAU");
        public static final Enum HEB = Enum.forString("HEB");
        public static final Enum HER = Enum.forString("HER");
        public static final Enum HIN = Enum.forString("HIN");
        public static final Enum HMO = Enum.forString("HMO");
        public static final Enum HUN = Enum.forString("HUN");
        public static final Enum ISL = Enum.forString("ISL");
        public static final Enum IBO = Enum.forString("IBO");
        public static final Enum IND = Enum.forString("IND");
        public static final Enum IKU = Enum.forString("IKU");
        public static final Enum IPK = Enum.forString("IPK");
        public static final Enum GLE = Enum.forString("GLE");
        public static final Enum ITA = Enum.forString("ITA");
        public static final Enum JPN = Enum.forString("JPN");
        public static final Enum JAV = Enum.forString("JAV");
        public static final Enum KAL = Enum.forString("KAL");
        public static final Enum KAN = Enum.forString("KAN");
        public static final Enum KAU = Enum.forString("KAU");
        public static final Enum KAS = Enum.forString("KAS");
        public static final Enum KAZ = Enum.forString("KAZ");
        public static final Enum KIK = Enum.forString("KIK");
        public static final Enum KIN = Enum.forString("KIN");
        public static final Enum KIR = Enum.forString("KIR");
        public static final Enum KOM = Enum.forString("KOM");
        public static final Enum KON = Enum.forString("KON");
        public static final Enum KOR = Enum.forString("KOR");
        public static final Enum KUA = Enum.forString("KUA");
        public static final Enum KUR = Enum.forString("KUR");
        public static final Enum LAO = Enum.forString("LAO");
        public static final Enum LAV = Enum.forString("LAV");
        public static final Enum LIM = Enum.forString("LIM");
        public static final Enum LIN = Enum.forString("LIN");
        public static final Enum LIT = Enum.forString("LIT");
        public static final Enum LUB = Enum.forString("LUB");
        public static final Enum LTZ = Enum.forString("LTZ");
        public static final Enum MKD = Enum.forString("MKD");
        public static final Enum MLG = Enum.forString("MLG");
        public static final Enum MSA = Enum.forString("MSA");
        public static final Enum MAL = Enum.forString("MAL");
        public static final Enum MLT = Enum.forString("MLT");
        public static final Enum GLV = Enum.forString("GLV");
        public static final Enum MRI = Enum.forString("MRI");
        public static final Enum MAR = Enum.forString("MAR");
        public static final Enum MAH = Enum.forString("MAH");
        public static final Enum ELL = Enum.forString("ELL");
        public static final Enum MON = Enum.forString("MON");
        public static final Enum NAU = Enum.forString("NAU");
        public static final Enum NAV = Enum.forString("NAV");
        public static final Enum NDO = Enum.forString("NDO");
        public static final Enum NEP = Enum.forString("NEP");
        public static final Enum NDE = Enum.forString("NDE");
        public static final Enum SME = Enum.forString("SME");
        public static final Enum NOR = Enum.forString("NOR");
        public static final Enum NOB = Enum.forString("NOB");
        public static final Enum NNO = Enum.forString("NNO");
        public static final Enum NYA = Enum.forString("NYA");
        public static final Enum OCI = Enum.forString("OCI");
        public static final Enum OJI = Enum.forString("OJI");
        public static final Enum OJC = Enum.forString("OJC");
        public static final Enum ORI = Enum.forString("ORI");
        public static final Enum ORM = Enum.forString("ORM");
        public static final Enum OSS = Enum.forString("OSS");
        public static final Enum PAN = Enum.forString("PAN");
        public static final Enum FAS = Enum.forString("FAS");
        public static final Enum POL = Enum.forString("POL");
        public static final Enum POR = Enum.forString("POR");
        public static final Enum PUS = Enum.forString("PUS");
        public static final Enum QUE = Enum.forString("QUE");
        public static final Enum RON = Enum.forString("RON");
        public static final Enum ROH = Enum.forString("ROH");
        public static final Enum RUN = Enum.forString("RUN");
        public static final Enum RUS = Enum.forString("RUS");
        public static final Enum SMO = Enum.forString("SMO");
        public static final Enum SAG = Enum.forString("SAG");
        public static final Enum SRD = Enum.forString("SRD");
        public static final Enum GLA = Enum.forString("GLA");
        public static final Enum SRP = Enum.forString("SRP");
        public static final Enum SNA = Enum.forString("SNA");
        public static final Enum III = Enum.forString("III");
        public static final Enum SND = Enum.forString("SND");
        public static final Enum SIN = Enum.forString("SIN");
        public static final Enum SGN = Enum.forString("SGN");
        public static final Enum SLK = Enum.forString("SLK");
        public static final Enum SLV = Enum.forString("SLV");
        public static final Enum SOM = Enum.forString("SOM");
        public static final Enum NBL = Enum.forString("NBL");
        public static final Enum SOT = Enum.forString("SOT");
        public static final Enum SPA = Enum.forString("SPA");
        public static final Enum SUN = Enum.forString("SUN");
        public static final Enum SWA = Enum.forString("SWA");
        public static final Enum SSW = Enum.forString("SSW");
        public static final Enum SWE = Enum.forString("SWE");
        public static final Enum TGL = Enum.forString("TGL");
        public static final Enum TAH = Enum.forString("TAH");
        public static final Enum TGK = Enum.forString("TGK");
        public static final Enum TAM = Enum.forString("TAM");
        public static final Enum TAT = Enum.forString("TAT");
        public static final Enum TEL = Enum.forString("TEL");
        public static final Enum THA = Enum.forString("THA");
        public static final Enum BOD = Enum.forString("BOD");
        public static final Enum TIR = Enum.forString("TIR");
        public static final Enum TON = Enum.forString("TON");
        public static final Enum TSO = Enum.forString("TSO");
        public static final Enum TSN = Enum.forString("TSN");
        public static final Enum TUR = Enum.forString("TUR");
        public static final Enum TUK = Enum.forString("TUK");
        public static final Enum TWI = Enum.forString("TWI");
        public static final Enum UIG = Enum.forString("UIG");
        public static final Enum UKR = Enum.forString("UKR");
        public static final Enum URD = Enum.forString("URD");
        public static final Enum UZB = Enum.forString("UZB");
        public static final Enum VEN = Enum.forString("VEN");
        public static final Enum VIE = Enum.forString("VIE");
        public static final Enum WLN = Enum.forString("WLN");
        public static final Enum CYM = Enum.forString("CYM");
        public static final Enum FRY = Enum.forString("FRY");
        public static final Enum WOL = Enum.forString("WOL");
        public static final Enum XHO = Enum.forString("XHO");
        public static final Enum YID = Enum.forString("YID");
        public static final Enum YOR = Enum.forString("YOR");
        public static final Enum ZHA = Enum.forString("ZHA");
        public static final Enum ZUL = Enum.forString("ZUL");
        public static final Enum OTH = Enum.forString("OTH");
        public static final Enum UN = Enum.forString("UN");
        public static final int INT_AAR = 1;
        public static final int INT_AFR = 2;
        public static final int INT_AKA = 3;
        public static final int INT_SQI = 4;
        public static final int INT_ASE = 5;
        public static final int INT_AMH = 6;
        public static final int INT_ARA = 7;
        public static final int INT_ARG = 8;
        public static final int INT_HYE = 9;
        public static final int INT_ASM = 10;
        public static final int INT_AVA = 11;
        public static final int INT_AYM = 12;
        public static final int INT_AZE = 13;
        public static final int INT_BAM = 14;
        public static final int INT_BAK = 15;
        public static final int INT_EUS = 16;
        public static final int INT_BEL = 17;
        public static final int INT_BEN = 18;
        public static final int INT_BIS = 19;
        public static final int INT_BOS = 20;
        public static final int INT_BRE = 21;
        public static final int INT_BUL = 22;
        public static final int INT_MYA = 23;
        public static final int INT_CAT = 24;
        public static final int INT_KHM = 25;
        public static final int INT_CHA = 26;
        public static final int INT_CHE = 27;
        public static final int INT_YUE = 28;
        public static final int INT_CMN = 29;
        public static final int INT_CHV = 30;
        public static final int INT_COR = 31;
        public static final int INT_COS = 32;
        public static final int INT_CRE = 33;
        public static final int INT_HRV = 34;
        public static final int INT_CES = 35;
        public static final int INT_DAN = 36;
        public static final int INT_DIV = 37;
        public static final int INT_NLD = 38;
        public static final int INT_DZO = 39;
        public static final int INT_ENG = 40;
        public static final int INT_EST = 41;
        public static final int INT_EWE = 42;
        public static final int INT_FAO = 43;
        public static final int INT_FIJ = 44;
        public static final int INT_FIL = 45;
        public static final int INT_FIN = 46;
        public static final int INT_FRA = 47;
        public static final int INT_FUL = 48;
        public static final int INT_GLG = 49;
        public static final int INT_LUG = 50;
        public static final int INT_KAT = 51;
        public static final int INT_DEU = 52;
        public static final int INT_GRN = 53;
        public static final int INT_GUJ = 54;
        public static final int INT_HAT = 55;
        public static final int INT_HAU = 56;
        public static final int INT_HEB = 57;
        public static final int INT_HER = 58;
        public static final int INT_HIN = 59;
        public static final int INT_HMO = 60;
        public static final int INT_HUN = 61;
        public static final int INT_ISL = 62;
        public static final int INT_IBO = 63;
        public static final int INT_IND = 64;
        public static final int INT_IKU = 65;
        public static final int INT_IPK = 66;
        public static final int INT_GLE = 67;
        public static final int INT_ITA = 68;
        public static final int INT_JPN = 69;
        public static final int INT_JAV = 70;
        public static final int INT_KAL = 71;
        public static final int INT_KAN = 72;
        public static final int INT_KAU = 73;
        public static final int INT_KAS = 74;
        public static final int INT_KAZ = 75;
        public static final int INT_KIK = 76;
        public static final int INT_KIN = 77;
        public static final int INT_KIR = 78;
        public static final int INT_KOM = 79;
        public static final int INT_KON = 80;
        public static final int INT_KOR = 81;
        public static final int INT_KUA = 82;
        public static final int INT_KUR = 83;
        public static final int INT_LAO = 84;
        public static final int INT_LAV = 85;
        public static final int INT_LIM = 86;
        public static final int INT_LIN = 87;
        public static final int INT_LIT = 88;
        public static final int INT_LUB = 89;
        public static final int INT_LTZ = 90;
        public static final int INT_MKD = 91;
        public static final int INT_MLG = 92;
        public static final int INT_MSA = 93;
        public static final int INT_MAL = 94;
        public static final int INT_MLT = 95;
        public static final int INT_GLV = 96;
        public static final int INT_MRI = 97;
        public static final int INT_MAR = 98;
        public static final int INT_MAH = 99;
        public static final int INT_ELL = 100;
        public static final int INT_MON = 101;
        public static final int INT_NAU = 102;
        public static final int INT_NAV = 103;
        public static final int INT_NDO = 104;
        public static final int INT_NEP = 105;
        public static final int INT_NDE = 106;
        public static final int INT_SME = 107;
        public static final int INT_NOR = 108;
        public static final int INT_NOB = 109;
        public static final int INT_NNO = 110;
        public static final int INT_NYA = 111;
        public static final int INT_OCI = 112;
        public static final int INT_OJI = 113;
        public static final int INT_OJC = 114;
        public static final int INT_ORI = 115;
        public static final int INT_ORM = 116;
        public static final int INT_OSS = 117;
        public static final int INT_PAN = 118;
        public static final int INT_FAS = 119;
        public static final int INT_POL = 120;
        public static final int INT_POR = 121;
        public static final int INT_PUS = 122;
        public static final int INT_QUE = 123;
        public static final int INT_RON = 124;
        public static final int INT_ROH = 125;
        public static final int INT_RUN = 126;
        public static final int INT_RUS = 127;
        public static final int INT_SMO = 128;
        public static final int INT_SAG = 129;
        public static final int INT_SRD = 130;
        public static final int INT_GLA = 131;
        public static final int INT_SRP = 132;
        public static final int INT_SNA = 133;
        public static final int INT_III = 134;
        public static final int INT_SND = 135;
        public static final int INT_SIN = 136;
        public static final int INT_SGN = 137;
        public static final int INT_SLK = 138;
        public static final int INT_SLV = 139;
        public static final int INT_SOM = 140;
        public static final int INT_NBL = 141;
        public static final int INT_SOT = 142;
        public static final int INT_SPA = 143;
        public static final int INT_SUN = 144;
        public static final int INT_SWA = 145;
        public static final int INT_SSW = 146;
        public static final int INT_SWE = 147;
        public static final int INT_TGL = 148;
        public static final int INT_TAH = 149;
        public static final int INT_TGK = 150;
        public static final int INT_TAM = 151;
        public static final int INT_TAT = 152;
        public static final int INT_TEL = 153;
        public static final int INT_THA = 154;
        public static final int INT_BOD = 155;
        public static final int INT_TIR = 156;
        public static final int INT_TON = 157;
        public static final int INT_TSO = 158;
        public static final int INT_TSN = 159;
        public static final int INT_TUR = 160;
        public static final int INT_TUK = 161;
        public static final int INT_TWI = 162;
        public static final int INT_UIG = 163;
        public static final int INT_UKR = 164;
        public static final int INT_URD = 165;
        public static final int INT_UZB = 166;
        public static final int INT_VEN = 167;
        public static final int INT_VIE = 168;
        public static final int INT_WLN = 169;
        public static final int INT_CYM = 170;
        public static final int INT_FRY = 171;
        public static final int INT_WOL = 172;
        public static final int INT_XHO = 173;
        public static final int INT_YID = 174;
        public static final int INT_YOR = 175;
        public static final int INT_ZHA = 176;
        public static final int INT_ZUL = 177;
        public static final int INT_OTH = 178;
        public static final int INT_UN = 179;
        
        StringEnumAbstractBase enumValue();
        
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_AAR = 1;
            static final int INT_AFR = 2;
            static final int INT_AKA = 3;
            static final int INT_SQI = 4;
            static final int INT_ASE = 5;
            static final int INT_AMH = 6;
            static final int INT_ARA = 7;
            static final int INT_ARG = 8;
            static final int INT_HYE = 9;
            static final int INT_ASM = 10;
            static final int INT_AVA = 11;
            static final int INT_AYM = 12;
            static final int INT_AZE = 13;
            static final int INT_BAM = 14;
            static final int INT_BAK = 15;
            static final int INT_EUS = 16;
            static final int INT_BEL = 17;
            static final int INT_BEN = 18;
            static final int INT_BIS = 19;
            static final int INT_BOS = 20;
            static final int INT_BRE = 21;
            static final int INT_BUL = 22;
            static final int INT_MYA = 23;
            static final int INT_CAT = 24;
            static final int INT_KHM = 25;
            static final int INT_CHA = 26;
            static final int INT_CHE = 27;
            static final int INT_YUE = 28;
            static final int INT_CMN = 29;
            static final int INT_CHV = 30;
            static final int INT_COR = 31;
            static final int INT_COS = 32;
            static final int INT_CRE = 33;
            static final int INT_HRV = 34;
            static final int INT_CES = 35;
            static final int INT_DAN = 36;
            static final int INT_DIV = 37;
            static final int INT_NLD = 38;
            static final int INT_DZO = 39;
            static final int INT_ENG = 40;
            static final int INT_EST = 41;
            static final int INT_EWE = 42;
            static final int INT_FAO = 43;
            static final int INT_FIJ = 44;
            static final int INT_FIL = 45;
            static final int INT_FIN = 46;
            static final int INT_FRA = 47;
            static final int INT_FUL = 48;
            static final int INT_GLG = 49;
            static final int INT_LUG = 50;
            static final int INT_KAT = 51;
            static final int INT_DEU = 52;
            static final int INT_GRN = 53;
            static final int INT_GUJ = 54;
            static final int INT_HAT = 55;
            static final int INT_HAU = 56;
            static final int INT_HEB = 57;
            static final int INT_HER = 58;
            static final int INT_HIN = 59;
            static final int INT_HMO = 60;
            static final int INT_HUN = 61;
            static final int INT_ISL = 62;
            static final int INT_IBO = 63;
            static final int INT_IND = 64;
            static final int INT_IKU = 65;
            static final int INT_IPK = 66;
            static final int INT_GLE = 67;
            static final int INT_ITA = 68;
            static final int INT_JPN = 69;
            static final int INT_JAV = 70;
            static final int INT_KAL = 71;
            static final int INT_KAN = 72;
            static final int INT_KAU = 73;
            static final int INT_KAS = 74;
            static final int INT_KAZ = 75;
            static final int INT_KIK = 76;
            static final int INT_KIN = 77;
            static final int INT_KIR = 78;
            static final int INT_KOM = 79;
            static final int INT_KON = 80;
            static final int INT_KOR = 81;
            static final int INT_KUA = 82;
            static final int INT_KUR = 83;
            static final int INT_LAO = 84;
            static final int INT_LAV = 85;
            static final int INT_LIM = 86;
            static final int INT_LIN = 87;
            static final int INT_LIT = 88;
            static final int INT_LUB = 89;
            static final int INT_LTZ = 90;
            static final int INT_MKD = 91;
            static final int INT_MLG = 92;
            static final int INT_MSA = 93;
            static final int INT_MAL = 94;
            static final int INT_MLT = 95;
            static final int INT_GLV = 96;
            static final int INT_MRI = 97;
            static final int INT_MAR = 98;
            static final int INT_MAH = 99;
            static final int INT_ELL = 100;
            static final int INT_MON = 101;
            static final int INT_NAU = 102;
            static final int INT_NAV = 103;
            static final int INT_NDO = 104;
            static final int INT_NEP = 105;
            static final int INT_NDE = 106;
            static final int INT_SME = 107;
            static final int INT_NOR = 108;
            static final int INT_NOB = 109;
            static final int INT_NNO = 110;
            static final int INT_NYA = 111;
            static final int INT_OCI = 112;
            static final int INT_OJI = 113;
            static final int INT_OJC = 114;
            static final int INT_ORI = 115;
            static final int INT_ORM = 116;
            static final int INT_OSS = 117;
            static final int INT_PAN = 118;
            static final int INT_FAS = 119;
            static final int INT_POL = 120;
            static final int INT_POR = 121;
            static final int INT_PUS = 122;
            static final int INT_QUE = 123;
            static final int INT_RON = 124;
            static final int INT_ROH = 125;
            static final int INT_RUN = 126;
            static final int INT_RUS = 127;
            static final int INT_SMO = 128;
            static final int INT_SAG = 129;
            static final int INT_SRD = 130;
            static final int INT_GLA = 131;
            static final int INT_SRP = 132;
            static final int INT_SNA = 133;
            static final int INT_III = 134;
            static final int INT_SND = 135;
            static final int INT_SIN = 136;
            static final int INT_SGN = 137;
            static final int INT_SLK = 138;
            static final int INT_SLV = 139;
            static final int INT_SOM = 140;
            static final int INT_NBL = 141;
            static final int INT_SOT = 142;
            static final int INT_SPA = 143;
            static final int INT_SUN = 144;
            static final int INT_SWA = 145;
            static final int INT_SSW = 146;
            static final int INT_SWE = 147;
            static final int INT_TGL = 148;
            static final int INT_TAH = 149;
            static final int INT_TGK = 150;
            static final int INT_TAM = 151;
            static final int INT_TAT = 152;
            static final int INT_TEL = 153;
            static final int INT_THA = 154;
            static final int INT_BOD = 155;
            static final int INT_TIR = 156;
            static final int INT_TON = 157;
            static final int INT_TSO = 158;
            static final int INT_TSN = 159;
            static final int INT_TUR = 160;
            static final int INT_TUK = 161;
            static final int INT_TWI = 162;
            static final int INT_UIG = 163;
            static final int INT_UKR = 164;
            static final int INT_URD = 165;
            static final int INT_UZB = 166;
            static final int INT_VEN = 167;
            static final int INT_VIE = 168;
            static final int INT_WLN = 169;
            static final int INT_CYM = 170;
            static final int INT_FRY = 171;
            static final int INT_WOL = 172;
            static final int INT_XHO = 173;
            static final int INT_YID = 174;
            static final int INT_YOR = 175;
            static final int INT_ZHA = 176;
            static final int INT_ZUL = 177;
            static final int INT_OTH = 178;
            static final int INT_UN = 179;
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
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("AAR", 1), new Enum("AFR", 2), new Enum("AKA", 3), new Enum("SQI", 4), new Enum("ASE", 5), new Enum("AMH", 6), new Enum("ARA", 7), new Enum("ARG", 8), new Enum("HYE", 9), new Enum("ASM", 10), new Enum("AVA", 11), new Enum("AYM", 12), new Enum("AZE", 13), new Enum("BAM", 14), new Enum("BAK", 15), new Enum("EUS", 16), new Enum("BEL", 17), new Enum("BEN", 18), new Enum("BIS", 19), new Enum("BOS", 20), new Enum("BRE", 21), new Enum("BUL", 22), new Enum("MYA", 23), new Enum("CAT", 24), new Enum("KHM", 25), new Enum("CHA", 26), new Enum("CHE", 27), new Enum("YUE", 28), new Enum("CMN", 29), new Enum("CHV", 30), new Enum("COR", 31), new Enum("COS", 32), new Enum("CRE", 33), new Enum("HRV", 34), new Enum("CES", 35), new Enum("DAN", 36), new Enum("DIV", 37), new Enum("NLD", 38), new Enum("DZO", 39), new Enum("ENG", 40), new Enum("EST", 41), new Enum("EWE", 42), new Enum("FAO", 43), new Enum("FIJ", 44), new Enum("FIL", 45), new Enum("FIN", 46), new Enum("FRA", 47), new Enum("FUL", 48), new Enum("GLG", 49), new Enum("LUG", 50), new Enum("KAT", 51), new Enum("DEU", 52), new Enum("GRN", 53), new Enum("GUJ", 54), new Enum("HAT", 55), new Enum("HAU", 56), new Enum("HEB", 57), new Enum("HER", 58), new Enum("HIN", 59), new Enum("HMO", 60), new Enum("HUN", 61), new Enum("ISL", 62), new Enum("IBO", 63), new Enum("IND", 64), new Enum("IKU", 65), new Enum("IPK", 66), new Enum("GLE", 67), new Enum("ITA", 68), new Enum("JPN", 69), new Enum("JAV", 70), new Enum("KAL", 71), new Enum("KAN", 72), new Enum("KAU", 73), new Enum("KAS", 74), new Enum("KAZ", 75), new Enum("KIK", 76), new Enum("KIN", 77), new Enum("KIR", 78), new Enum("KOM", 79), new Enum("KON", 80), new Enum("KOR", 81), new Enum("KUA", 82), new Enum("KUR", 83), new Enum("LAO", 84), new Enum("LAV", 85), new Enum("LIM", 86), new Enum("LIN", 87), new Enum("LIT", 88), new Enum("LUB", 89), new Enum("LTZ", 90), new Enum("MKD", 91), new Enum("MLG", 92), new Enum("MSA", 93), new Enum("MAL", 94), new Enum("MLT", 95), new Enum("GLV", 96), new Enum("MRI", 97), new Enum("MAR", 98), new Enum("MAH", 99), new Enum("ELL", 100), new Enum("MON", 101), new Enum("NAU", 102), new Enum("NAV", 103), new Enum("NDO", 104), new Enum("NEP", 105), new Enum("NDE", 106), new Enum("SME", 107), new Enum("NOR", 108), new Enum("NOB", 109), new Enum("NNO", 110), new Enum("NYA", 111), new Enum("OCI", 112), new Enum("OJI", 113), new Enum("OJC", 114), new Enum("ORI", 115), new Enum("ORM", 116), new Enum("OSS", 117), new Enum("PAN", 118), new Enum("FAS", 119), new Enum("POL", 120), new Enum("POR", 121), new Enum("PUS", 122), new Enum("QUE", 123), new Enum("RON", 124), new Enum("ROH", 125), new Enum("RUN", 126), new Enum("RUS", 127), new Enum("SMO", 128), new Enum("SAG", 129), new Enum("SRD", 130), new Enum("GLA", 131), new Enum("SRP", 132), new Enum("SNA", 133), new Enum("III", 134), new Enum("SND", 135), new Enum("SIN", 136), new Enum("SGN", 137), new Enum("SLK", 138), new Enum("SLV", 139), new Enum("SOM", 140), new Enum("NBL", 141), new Enum("SOT", 142), new Enum("SPA", 143), new Enum("SUN", 144), new Enum("SWA", 145), new Enum("SSW", 146), new Enum("SWE", 147), new Enum("TGL", 148), new Enum("TAH", 149), new Enum("TGK", 150), new Enum("TAM", 151), new Enum("TAT", 152), new Enum("TEL", 153), new Enum("THA", 154), new Enum("BOD", 155), new Enum("TIR", 156), new Enum("TON", 157), new Enum("TSO", 158), new Enum("TSN", 159), new Enum("TUR", 160), new Enum("TUK", 161), new Enum("TWI", 162), new Enum("UIG", 163), new Enum("UKR", 164), new Enum("URD", 165), new Enum("UZB", 166), new Enum("VEN", 167), new Enum("VIE", 168), new Enum("WLN", 169), new Enum("CYM", 170), new Enum("FRY", 171), new Enum("WOL", 172), new Enum("XHO", 173), new Enum("YID", 174), new Enum("YOR", 175), new Enum("ZHA", 176), new Enum("ZUL", 177), new Enum("OTH", 178), new Enum("UN", 179) });
            }
        }
        
        public static final class Factory
        {
            public static Language newValue(final Object obj) {
                return (Language)Language.type.newValue(obj);
            }
            
            public static Language newInstance() {
                return (Language)XmlBeans.getContextTypeLoader().newInstance(Language.type, (XmlOptions)null);
            }
            
            public static Language newInstance(final XmlOptions options) {
                return (Language)XmlBeans.getContextTypeLoader().newInstance(Language.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * Age interface for patient age in years.
     *
     * <p>Represents the patient's age as an integer value, typically calculated from date of birth.
     * Used for age-specific clinical decision support, screening recommendations, and billing rules.</p>
     *
     * @since 2026-01-24
     */
    public interface Age extends XmlInt
    {
        /** SchemaType for the Age interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Age.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("agec433elemtype");

        /**
         * Factory class for creating Age instances.
         *
         * @since 2026-01-24
         */
        public static final class Factory
        {
            /**
             * Creates an Age value from an object.
             *
             * @param obj Object the source object
             * @return Age the created age value
             */
            public static Age newValue(final Object obj) {
                return (Age)Age.type.newValue(obj);
            }

            /**
             * Creates a new Age instance with default options.
             *
             * @return Age the new age instance
             */
            public static Age newInstance() {
                return (Age)XmlBeans.getContextTypeLoader().newInstance(Age.type, (XmlOptions)null);
            }
            
            /**
             * Creates a new Age instance with specified XML options.
             *
             * @param options XmlOptions the XML parsing/generation options
             * @return Age the new age instance
             */
            public static Age newInstance(final XmlOptions options) {
                return (Age)XmlBeans.getContextTypeLoader().newInstance(Age.type, options);
            }

            /** Private constructor to prevent instantiation. */
            private Factory() {
            }
        }
    }

    /**
     * Occupation interface for patient occupation information.
     *
     * <p>Captures the patient's current occupation using standardized occupation codes or
     * free-text description. Occupation data is used for occupational health tracking,
     * workplace injury documentation, and social determinants of health analysis.</p>
     *
     * @since 2026-01-24
     */
    public interface Occupation extends XmlObject
    {
        /** SchemaType for the Occupation interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Occupation.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("occupation2d37elemtype");

        /**
         * Gets the occupation value from standardized codes.
         *
         * @return Value.Enum the occupation code
         */
        Value.Enum getValue();

        /**
         * Gets the occupation value as a Value object.
         *
         * @return Value the occupation value as an XML typed value
         */
        Value xgetValue();

        /**
         * Sets the occupation using a standardized code.
         *
         * @param p0 Value.Enum the occupation code to set
         */
        void setValue(final Value.Enum p0);

        /**
         * Sets the occupation using a Value object.
         *
         * @param p0 Value the occupation value as an XML typed value
         */
        void xsetValue(final Value p0);

        /**
         * Gets the free-text occupation description for non-standard occupations.
         *
         * @return String the occupation description text
         */
        String getOther();

        /**
         * Gets the free-text occupation description as an XmlString object.
         *
         * @return XmlString the occupation description as an XML typed value
         */
        XmlString xgetOther();

        /**
         * Sets the free-text occupation description.
         *
         * @param p0 String the occupation description to set
         */
        void setOther(final String p0);

        /**
         * Sets the free-text occupation description using an XmlString object.
         *
         * @param p0 XmlString the occupation description as an XML typed value
         */
        void xsetOther(final XmlString p0);
        
        public interface Value extends XmlString
        {
            public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Value.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("valued354elemtype");
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
            public static final int INT_OTHER = 259;
            
            StringEnumAbstractBase enumValue();
            
            void set(final StringEnumAbstractBase p0);
            
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
                static final int INT_OTHER = 259;
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
                    table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("OCC0005", 1), new Enum("OCC0010", 2), new Enum("OCC0015", 3), new Enum("OCC0020", 4), new Enum("OCC0025", 5), new Enum("OCC0030", 6), new Enum("OCC0035", 7), new Enum("OCC0040", 8), new Enum("OCC0045", 9), new Enum("OCC0050", 10), new Enum("OCC0055", 11), new Enum("OCC0060", 12), new Enum("OCC0065", 13), new Enum("OCC0070", 14), new Enum("OCC0075", 15), new Enum("OCC0080", 16), new Enum("OCC0085", 17), new Enum("OCC0090", 18), new Enum("OCC0095", 19), new Enum("OCC0100", 20), new Enum("OCC0105", 21), new Enum("OCC0110", 22), new Enum("OCC0115", 23), new Enum("OCC0120", 24), new Enum("OCC0125", 25), new Enum("OCC0130", 26), new Enum("OCC0135", 27), new Enum("OCC0140", 28), new Enum("OCC0145", 29), new Enum("OCC0150", 30), new Enum("OCC0155", 31), new Enum("OCC0160", 32), new Enum("OCC0165", 33), new Enum("OCC0170", 34), new Enum("OCC0175", 35), new Enum("OCC0180", 36), new Enum("OCC0185", 37), new Enum("OCC0190", 38), new Enum("OCC0195", 39), new Enum("OCC0200", 40), new Enum("OCC0205", 41), new Enum("OCC0210", 42), new Enum("OCC0215", 43), new Enum("OCC0220", 44), new Enum("OCC0225", 45), new Enum("OCC0230", 46), new Enum("OCC0235", 47), new Enum("OCC0240", 48), new Enum("OCC0245", 49), new Enum("OCC0250", 50), new Enum("OCC0255", 51), new Enum("OCC0260", 52), new Enum("OCC0265", 53), new Enum("OCC0270", 54), new Enum("OCC0275", 55), new Enum("OCC0280", 56), new Enum("OCC0285", 57), new Enum("OCC0290", 58), new Enum("OCC0295", 59), new Enum("OCC0300", 60), new Enum("OCC0305", 61), new Enum("OCC0310", 62), new Enum("OCC0315", 63), new Enum("OCC0320", 64), new Enum("OCC0325", 65), new Enum("OCC0330", 66), new Enum("OCC0335", 67), new Enum("OCC0340", 68), new Enum("OCC0345", 69), new Enum("OCC0350", 70), new Enum("OCC0355", 71), new Enum("OCC0360", 72), new Enum("OCC0365", 73), new Enum("OCC0370", 74), new Enum("OCC0375", 75), new Enum("OCC0380", 76), new Enum("OCC0385", 77), new Enum("OCC0390", 78), new Enum("OCC0395", 79), new Enum("OCC0400", 80), new Enum("OCC0405", 81), new Enum("OCC0410", 82), new Enum("OCC0415", 83), new Enum("OCC0420", 84), new Enum("OCC0425", 85), new Enum("OCC0430", 86), new Enum("OCC0435", 87), new Enum("OCC0440", 88), new Enum("OCC0445", 89), new Enum("OCC0450", 90), new Enum("OCC0455", 91), new Enum("OCC0460", 92), new Enum("OCC0465", 93), new Enum("OCC0470", 94), new Enum("OCC0475", 95), new Enum("OCC0480", 96), new Enum("OCC0485", 97), new Enum("OCC0490", 98), new Enum("OCC0495", 99), new Enum("OCC0500", 100), new Enum("OCC0505", 101), new Enum("OCC0510", 102), new Enum("OCC0515", 103), new Enum("OCC0520", 104), new Enum("OCC0525", 105), new Enum("OCC0530", 106), new Enum("OCC0535", 107), new Enum("OCC0540", 108), new Enum("OCC0545", 109), new Enum("OCC0550", 110), new Enum("OCC0555", 111), new Enum("OCC0560", 112), new Enum("OCC0565", 113), new Enum("OCC0570", 114), new Enum("OCC0575", 115), new Enum("OCC0580", 116), new Enum("OCC0585", 117), new Enum("OCC0590", 118), new Enum("OCC0595", 119), new Enum("OCC0600", 120), new Enum("OCC0605", 121), new Enum("OCC0610", 122), new Enum("OCC0615", 123), new Enum("OCC0620", 124), new Enum("OCC0625", 125), new Enum("OCC0630", 126), new Enum("OCC0635", 127), new Enum("OCC0640", 128), new Enum("OCC0645", 129), new Enum("OCC0650", 130), new Enum("OCC0655", 131), new Enum("OCC0660", 132), new Enum("OCC0665", 133), new Enum("OCC0670", 134), new Enum("OCC0675", 135), new Enum("OCC0680", 136), new Enum("OCC0685", 137), new Enum("OCC0690", 138), new Enum("OCC0695", 139), new Enum("OCC0700", 140), new Enum("OCC0705", 141), new Enum("OCC0710", 142), new Enum("OCC0715", 143), new Enum("OCC0720", 144), new Enum("OCC0725", 145), new Enum("OCC0730", 146), new Enum("OCC0735", 147), new Enum("OCC0740", 148), new Enum("OCC0745", 149), new Enum("OCC0750", 150), new Enum("OCC0755", 151), new Enum("OCC0760", 152), new Enum("OCC0765", 153), new Enum("OCC0770", 154), new Enum("OCC0775", 155), new Enum("OCC0780", 156), new Enum("OCC0785", 157), new Enum("OCC0790", 158), new Enum("OCC0795", 159), new Enum("OCC0800", 160), new Enum("OCC0805", 161), new Enum("OCC0810", 162), new Enum("OCC0815", 163), new Enum("OCC0820", 164), new Enum("OCC0825", 165), new Enum("OCC0830", 166), new Enum("OCC0835", 167), new Enum("OCC0840", 168), new Enum("OCC0845", 169), new Enum("OCC0850", 170), new Enum("OCC0855", 171), new Enum("OCC0860", 172), new Enum("OCC0865", 173), new Enum("OCC0870", 174), new Enum("OCC0875", 175), new Enum("OCC0880", 176), new Enum("OCC0885", 177), new Enum("OCC0890", 178), new Enum("OCC0895", 179), new Enum("OCC0900", 180), new Enum("OCC0905", 181), new Enum("OCC0910", 182), new Enum("OCC0915", 183), new Enum("OCC0920", 184), new Enum("OCC0925", 185), new Enum("OCC0930", 186), new Enum("OCC0935", 187), new Enum("OCC0940", 188), new Enum("OCC0945", 189), new Enum("OCC0950", 190), new Enum("OCC0955", 191), new Enum("OCC0960", 192), new Enum("OCC0965", 193), new Enum("OCC0970", 194), new Enum("OCC0975", 195), new Enum("OCC0980", 196), new Enum("OCC0985", 197), new Enum("OCC0990", 198), new Enum("OCC0995", 199), new Enum("OCC1000", 200), new Enum("OCC1005", 201), new Enum("OCC1010", 202), new Enum("OCC1015", 203), new Enum("OCC1020", 204), new Enum("OCC1025", 205), new Enum("OCC1030", 206), new Enum("OCC1035", 207), new Enum("OCC1040", 208), new Enum("OCC1045", 209), new Enum("OCC1050", 210), new Enum("OCC1055", 211), new Enum("OCC1060", 212), new Enum("OCC1065", 213), new Enum("OCC1070", 214), new Enum("OCC1075", 215), new Enum("OCC1080", 216), new Enum("OCC1085", 217), new Enum("OCC1090", 218), new Enum("OCC1095", 219), new Enum("OCC1100", 220), new Enum("OCC1105", 221), new Enum("OCC1110", 222), new Enum("OCC1115", 223), new Enum("OCC1120", 224), new Enum("OCC1125", 225), new Enum("OCC1130", 226), new Enum("OCC1135", 227), new Enum("OCC1140", 228), new Enum("OCC1145", 229), new Enum("OCC1150", 230), new Enum("OCC1155", 231), new Enum("OCC1160", 232), new Enum("OCC1165", 233), new Enum("OCC1170", 234), new Enum("OCC1175", 235), new Enum("OCC1180", 236), new Enum("OCC1185", 237), new Enum("OCC1190", 238), new Enum("OCC1195", 239), new Enum("OCC1200", 240), new Enum("OCC1205", 241), new Enum("OCC1210", 242), new Enum("OCC1215", 243), new Enum("OCC1220", 244), new Enum("OCC1225", 245), new Enum("OCC1230", 246), new Enum("OCC1235", 247), new Enum("OCC1240", 248), new Enum("OCC1245", 249), new Enum("OCC1250", 250), new Enum("OCC1255", 251), new Enum("OCC1260", 252), new Enum("OCC1265", 253), new Enum("OCC1270", 254), new Enum("OCC1275", 255), new Enum("OCC1280", 256), new Enum("OCC1285", 257), new Enum("OCC1290", 258), new Enum("OTHER", 259) });
                }
            }
            
            public static final class Factory
            {
                public static Value newValue(final Object obj) {
                    return (Value)Value.type.newValue(obj);
                }
                
                public static Value newInstance() {
                    return (Value)XmlBeans.getContextTypeLoader().newInstance(Value.type, (XmlOptions)null);
                }
                
                public static Value newInstance(final XmlOptions options) {
                    return (Value)XmlBeans.getContextTypeLoader().newInstance(Value.type, options);
                }
                
                private Factory() {
                }
            }
        }
        
        public static final class Factory
        {
            public static Occupation newInstance() {
                return (Occupation)XmlBeans.getContextTypeLoader().newInstance(Occupation.type, (XmlOptions)null);
            }
            
            public static Occupation newInstance(final XmlOptions options) {
                return (Occupation)XmlBeans.getContextTypeLoader().newInstance(Occupation.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * LevelOfEducation interface for patient education level classification.
     *
     * <p>Standardized education level codes (ED001-ED006) representing the patient's highest
     * level of completed education. Used for health literacy assessment, patient education material
     * selection, and social determinants of health tracking.</p>
     *
     * @since 2026-01-24
     */
    public interface LevelOfEducation extends XmlString
    {
        /** SchemaType for the LevelOfEducation interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(LevelOfEducation.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("levelofeducationad55elemtype");

        /** Education level code ED001. */
        public static final Enum ED_001 = Enum.forString("ED001");
        public static final Enum ED_002 = Enum.forString("ED002");
        public static final Enum ED_003 = Enum.forString("ED003");
        public static final Enum ED_004 = Enum.forString("ED004");
        public static final Enum ED_005 = Enum.forString("ED005");
        public static final Enum ED_006 = Enum.forString("ED006");
        public static final Enum UN = Enum.forString("UN");
        public static final int INT_ED_001 = 1;
        public static final int INT_ED_002 = 2;
        public static final int INT_ED_003 = 3;
        public static final int INT_ED_004 = 4;
        public static final int INT_ED_005 = 5;
        public static final int INT_ED_006 = 6;
        public static final int INT_UN = 7;
        
        StringEnumAbstractBase enumValue();
        
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_ED_001 = 1;
            static final int INT_ED_002 = 2;
            static final int INT_ED_003 = 3;
            static final int INT_ED_004 = 4;
            static final int INT_ED_005 = 5;
            static final int INT_ED_006 = 6;
            static final int INT_UN = 7;
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
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("ED001", 1), new Enum("ED002", 2), new Enum("ED003", 3), new Enum("ED004", 4), new Enum("ED005", 5), new Enum("ED006", 6), new Enum("UN", 7) });
            }
        }
        
        public static final class Factory
        {
            public static LevelOfEducation newValue(final Object obj) {
                return (LevelOfEducation)LevelOfEducation.type.newValue(obj);
            }
            
            public static LevelOfEducation newInstance() {
                return (LevelOfEducation)XmlBeans.getContextTypeLoader().newInstance(LevelOfEducation.type, (XmlOptions)null);
            }
            
            public static LevelOfEducation newInstance(final XmlOptions options) {
                return (LevelOfEducation)XmlBeans.getContextTypeLoader().newInstance(LevelOfEducation.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * Hin (Health Insurance Number) interface for provincial health insurance information.
     *
     * <p>Stores the patient's provincial health insurance number and type. The HIN is the primary
     * identifier for billing and eligibility verification with provincial health insurance plans.
     * Format and validation rules vary by province (e.g., Ontario: 10 digits + 2-letter version code,
     * British Columbia: 10 digits).</p>
     *
     * <p><strong>Protected Health Information:</strong> HINs are personally identifiable health
     * information and must be protected according to PIPEDA and provincial privacy legislation.</p>
     *
     * @since 2026-01-24
     */
    public interface Hin extends XmlString
    {
        /** SchemaType for the Hin interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Hin.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("hinbd65elemtype");

        /**
         * Gets the HIN type (e.g., provincial plan type).
         *
         * @return Type.Enum the HIN type code
         */
        Type.Enum getType();

        /**
         * Gets the HIN type as a Type object.
         *
         * @return Type the HIN type as an XML typed value
         */
        Type xgetType();

        /**
         * Checks if the HIN type is set.
         *
         * @return boolean true if type is set, false otherwise
         */
        boolean isSetType();

        /**
         * Sets the HIN type.
         *
         * @param p0 Type.Enum the HIN type code to set
         */
        void setType(final Type.Enum p0);

        /**
         * Sets the HIN type using a Type object.
         *
         * @param p0 Type the HIN type as an XML typed value
         */
        void xsetType(final Type p0);

        /**
         * Unsets the HIN type (sets to null).
         */
        void unsetType();
        
        public interface Type extends XmlString
        {
            public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Type.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("typed0cbattrtype");
            public static final Enum OHIP = Enum.forString("OHIP");
            public static final Enum RAMQ = Enum.forString("RAMQ");
            public static final Enum OTHER = Enum.forString("OTHER");
            public static final int INT_OHIP = 1;
            public static final int INT_RAMQ = 2;
            public static final int INT_OTHER = 3;
            
            StringEnumAbstractBase enumValue();
            
            void set(final StringEnumAbstractBase p0);
            
            public static final class Enum extends StringEnumAbstractBase
            {
                static final int INT_OHIP = 1;
                static final int INT_RAMQ = 2;
                static final int INT_OTHER = 3;
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
                    table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("OHIP", 1), new Enum("RAMQ", 2), new Enum("OTHER", 3) });
                }
            }
            
            public static final class Factory
            {
                public static Type newValue(final Object obj) {
                    return (Type)Type.type.newValue(obj);
                }
                
                public static Type newInstance() {
                    return (Type)XmlBeans.getContextTypeLoader().newInstance(Type.type, (XmlOptions)null);
                }
                
                public static Type newInstance(final XmlOptions options) {
                    return (Type)XmlBeans.getContextTypeLoader().newInstance(Type.type, options);
                }
                
                private Factory() {
                }
            }
        }
        
        public static final class Factory
        {
            public static Hin newInstance() {
                return (Hin)XmlBeans.getContextTypeLoader().newInstance(Hin.type, (XmlOptions)null);
            }
            
            public static Hin newInstance(final XmlOptions options) {
                return (Hin)XmlBeans.getContextTypeLoader().newInstance(Hin.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * EthnicBackground interface for patient ethnic and cultural background information.
     *
     * <p>Captures self-identified ethnic background and cultural heritage information. This data
     * supports culturally competent care delivery, health equity research, and tracking of health
     * disparities in specific populations. Multiple values can be selected to reflect mixed heritage.</p>
     *
     * <p>Collection and use of this information must respect patient privacy and follow anti-discrimination
     * policies. Patients should always have the option to decline providing this information.</p>
     *
     * @since 2026-01-24
     */
    public interface EthnicBackground extends XmlObject
    {
        /** SchemaType for the EthnicBackground interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(EthnicBackground.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("ethnicbackgrounda065elemtype");

        /**
         * Gets all ethnic background values as an array.
         *
         * @return Value[] array of ethnic background values
         */
        Value[] getValueArray();
        
        Value getValueArray(final int p0);
        
        int sizeOfValueArray();
        
        void setValueArray(final Value[] p0);
        
        void setValueArray(final int p0, final Value p1);
        
        Value insertNewValue(final int p0);
        
        Value addNewValue();
        
        void removeValue(final int p0);
        
        public interface Value extends EthnicValueType
        {
            public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Value.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("value7cc2elemtype");
            public static final Enum ANC_001 = EthnicValueType.ANC_001;
            public static final Enum ANC_002 = EthnicValueType.ANC_002;
            public static final Enum ANC_005 = EthnicValueType.ANC_005;
            public static final Enum ANC_007 = EthnicValueType.ANC_007;
            public static final Enum OTHER = EthnicValueType.OTHER;
            public static final Enum UN = EthnicValueType.UN;
            public static final int INT_ANC_001 = 1;
            public static final int INT_ANC_002 = 2;
            public static final int INT_ANC_005 = 3;
            public static final int INT_ANC_007 = 4;
            public static final int INT_OTHER = 5;
            public static final int INT_UN = 6;
            
            Parent.Enum getParent();
            
            Parent xgetParent();
            
            boolean isSetParent();
            
            void setParent(final Parent.Enum p0);
            
            void xsetParent(final Parent p0);
            
            void unsetParent();
            
            public interface Parent extends XmlToken
            {
                public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Parent.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("parentef8cattrtype");
                public static final Enum MATERNAL = Enum.forString("maternal");
                public static final Enum PATERNAL = Enum.forString("paternal");
                public static final int INT_MATERNAL = 1;
                public static final int INT_PATERNAL = 2;
                
                StringEnumAbstractBase enumValue();
                
                void set(final StringEnumAbstractBase p0);
                
                public static final class Enum extends StringEnumAbstractBase
                {
                    static final int INT_MATERNAL = 1;
                    static final int INT_PATERNAL = 2;
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
                        table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("maternal", 1), new Enum("paternal", 2) });
                    }
                }
                
                public static final class Factory
                {
                    public static Parent newValue(final Object obj) {
                        return (Parent)Parent.type.newValue(obj);
                    }
                    
                    public static Parent newInstance() {
                        return (Parent)XmlBeans.getContextTypeLoader().newInstance(Parent.type, (XmlOptions)null);
                    }
                    
                    public static Parent newInstance(final XmlOptions options) {
                        return (Parent)XmlBeans.getContextTypeLoader().newInstance(Parent.type, options);
                    }
                    
                    private Factory() {
                    }
                }
            }
            
            public static final class Factory
            {
                public static Value newInstance() {
                    return (Value)XmlBeans.getContextTypeLoader().newInstance(Value.type, (XmlOptions)null);
                }
                
                public static Value newInstance(final XmlOptions options) {
                    return (Value)XmlBeans.getContextTypeLoader().newInstance(Value.type, options);
                }
                
                private Factory() {
                }
            }
        }
        
        public static final class Factory
        {
            public static EthnicBackground newInstance() {
                return (EthnicBackground)XmlBeans.getContextTypeLoader().newInstance(EthnicBackground.type, (XmlOptions)null);
            }
            
            public static EthnicBackground newInstance(final XmlOptions options) {
                return (EthnicBackground)XmlBeans.getContextTypeLoader().newInstance(EthnicBackground.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * MaritalStatus interface for patient marital status classification.
     *
     * <p>Standardized marital status codes representing the patient's current marital or relationship
     * status. Used for demographic reporting, social support assessment, and next-of-kin determination.
     * Codes follow healthcare industry standards (e.g., MS005=Single, MS010=Married, MS015=Common-law).</p>
     *
     * @since 2026-01-24
     */
    public interface MaritalStatus extends XmlString
    {
        /** SchemaType for the MaritalStatus interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(MaritalStatus.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("maritalstatus2b0celemtype");

        /** Marital status code MS005 (typically Single). */
        public static final Enum MS_005 = Enum.forString("MS005");
        public static final Enum MS_010 = Enum.forString("MS010");
        public static final Enum MS_015 = Enum.forString("MS015");
        public static final Enum UN = Enum.forString("UN");
        public static final int INT_MS_005 = 1;
        public static final int INT_MS_010 = 2;
        public static final int INT_MS_015 = 3;
        public static final int INT_UN = 4;
        
        StringEnumAbstractBase enumValue();
        
        void set(final StringEnumAbstractBase p0);
        
        public static final class Enum extends StringEnumAbstractBase
        {
            static final int INT_MS_005 = 1;
            static final int INT_MS_010 = 2;
            static final int INT_MS_015 = 3;
            static final int INT_UN = 4;
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
                table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("MS005", 1), new Enum("MS010", 2), new Enum("MS015", 3), new Enum("UN", 4) });
            }
        }
        
        public static final class Factory
        {
            public static MaritalStatus newValue(final Object obj) {
                return (MaritalStatus)MaritalStatus.type.newValue(obj);
            }
            
            public static MaritalStatus newInstance() {
                return (MaritalStatus)XmlBeans.getContextTypeLoader().newInstance(MaritalStatus.type, (XmlOptions)null);
            }
            
            public static MaritalStatus newInstance(final XmlOptions options) {
                return (MaritalStatus)XmlBeans.getContextTypeLoader().newInstance(MaritalStatus.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * Medications interface for patient current medications list.
     *
     * <p>Comprehensive list of all current prescription medications, over-the-counter drugs, and
     * supplements. Critical for medication reconciliation, drug interaction checking, and clinical
     * decision support. Should be reviewed and updated at every patient encounter.</p>
     *
     * <p><strong>Clinical Safety:</strong> Accurate medication lists are essential for patient safety
     * to prevent adverse drug events, duplicate therapy, and drug-drug interactions.</p>
     *
     * @since 2026-01-24
     */
    public interface Medications extends XmlObject
    {
        /** SchemaType for the Medications interface. */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Medications.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("medications594eelemtype");

        /**
         * Gets all medication values as an array.
         *
         * @return Value.Enum[] array of medication values
         */
        Value.Enum[] getValueArray();
        
        Value.Enum getValueArray(final int p0);
        
        Value[] xgetValueArray();
        
        Value xgetValueArray(final int p0);
        
        int sizeOfValueArray();
        
        void setValueArray(final Value.Enum[] p0);
        
        void setValueArray(final int p0, final Value.Enum p1);
        
        void xsetValueArray(final Value[] p0);
        
        void xsetValueArray(final int p0, final Value p1);
        
        void insertValue(final int p0, final Value.Enum p1);
        
        void addValue(final Value.Enum p0);
        
        Value insertNewValue(final int p0);
        
        Value addNewValue();
        
        void removeValue(final int p0);
        
        String[] getOtherArray();
        
        String getOtherArray(final int p0);
        
        XmlString[] xgetOtherArray();
        
        XmlString xgetOtherArray(final int p0);
        
        int sizeOfOtherArray();
        
        void setOtherArray(final String[] p0);
        
        void setOtherArray(final int p0, final String p1);
        
        void xsetOtherArray(final XmlString[] p0);
        
        void xsetOtherArray(final int p0, final XmlString p1);
        
        void insertOther(final int p0, final String p1);
        
        void addOther(final String p0);
        
        XmlString insertNewOther(final int p0);
        
        XmlString addNewOther();
        
        void removeOther(final int p0);
        
        public interface Value extends XmlString
        {
            public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Value.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("value76d1elemtype");
            public static final Enum MEP_001 = Enum.forString("MEP001");
            public static final Enum MEP_002 = Enum.forString("MEP002");
            public static final Enum MEP_003 = Enum.forString("MEP003");
            public static final Enum MEP_004 = Enum.forString("MEP004");
            public static final Enum MEP_005 = Enum.forString("MEP005");
            public static final Enum MEP_006 = Enum.forString("MEP006");
            public static final Enum MEP_007 = Enum.forString("MEP007");
            public static final Enum MEP_008 = Enum.forString("MEP008");
            public static final Enum MEP_009 = Enum.forString("MEP009");
            public static final Enum MEP_010 = Enum.forString("MEP010");
            public static final Enum MEP_011 = Enum.forString("MEP011");
            public static final Enum MEP_012 = Enum.forString("MEP012");
            public static final Enum MEP_013 = Enum.forString("MEP013");
            public static final Enum MEP_014 = Enum.forString("MEP014");
            public static final Enum MEP_015 = Enum.forString("MEP015");
            public static final Enum MEP_016 = Enum.forString("MEP016");
            public static final Enum MEP_017 = Enum.forString("MEP017");
            public static final Enum NONE = Enum.forString("NONE");
            public static final Enum OTHER = Enum.forString("OTHER");
            public static final Enum MEP_018 = Enum.forString("MEP018");
            public static final Enum MEP_019 = Enum.forString("MEP019");
            public static final Enum MEP_020 = Enum.forString("MEP020");
            public static final Enum MEP_021 = Enum.forString("MEP021");
            public static final Enum MEP_022 = Enum.forString("MEP022");
            public static final Enum MEP_023 = Enum.forString("MEP023");
            public static final Enum MEP_024 = Enum.forString("MEP024");
            public static final Enum UN = Enum.forString("UN");
            public static final int INT_MEP_001 = 1;
            public static final int INT_MEP_002 = 2;
            public static final int INT_MEP_003 = 3;
            public static final int INT_MEP_004 = 4;
            public static final int INT_MEP_005 = 5;
            public static final int INT_MEP_006 = 6;
            public static final int INT_MEP_007 = 7;
            public static final int INT_MEP_008 = 8;
            public static final int INT_MEP_009 = 9;
            public static final int INT_MEP_010 = 10;
            public static final int INT_MEP_011 = 11;
            public static final int INT_MEP_012 = 12;
            public static final int INT_MEP_013 = 13;
            public static final int INT_MEP_014 = 14;
            public static final int INT_MEP_015 = 15;
            public static final int INT_MEP_016 = 16;
            public static final int INT_MEP_017 = 17;
            public static final int INT_NONE = 18;
            public static final int INT_OTHER = 19;
            public static final int INT_MEP_018 = 20;
            public static final int INT_MEP_019 = 21;
            public static final int INT_MEP_020 = 22;
            public static final int INT_MEP_021 = 23;
            public static final int INT_MEP_022 = 24;
            public static final int INT_MEP_023 = 25;
            public static final int INT_MEP_024 = 26;
            public static final int INT_UN = 27;
            
            StringEnumAbstractBase enumValue();
            
            void set(final StringEnumAbstractBase p0);
            
            public static final class Enum extends StringEnumAbstractBase
            {
                static final int INT_MEP_001 = 1;
                static final int INT_MEP_002 = 2;
                static final int INT_MEP_003 = 3;
                static final int INT_MEP_004 = 4;
                static final int INT_MEP_005 = 5;
                static final int INT_MEP_006 = 6;
                static final int INT_MEP_007 = 7;
                static final int INT_MEP_008 = 8;
                static final int INT_MEP_009 = 9;
                static final int INT_MEP_010 = 10;
                static final int INT_MEP_011 = 11;
                static final int INT_MEP_012 = 12;
                static final int INT_MEP_013 = 13;
                static final int INT_MEP_014 = 14;
                static final int INT_MEP_015 = 15;
                static final int INT_MEP_016 = 16;
                static final int INT_MEP_017 = 17;
                static final int INT_NONE = 18;
                static final int INT_OTHER = 19;
                static final int INT_MEP_018 = 20;
                static final int INT_MEP_019 = 21;
                static final int INT_MEP_020 = 22;
                static final int INT_MEP_021 = 23;
                static final int INT_MEP_022 = 24;
                static final int INT_MEP_023 = 25;
                static final int INT_MEP_024 = 26;
                static final int INT_UN = 27;
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
                    table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("MEP001", 1), new Enum("MEP002", 2), new Enum("MEP003", 3), new Enum("MEP004", 4), new Enum("MEP005", 5), new Enum("MEP006", 6), new Enum("MEP007", 7), new Enum("MEP008", 8), new Enum("MEP009", 9), new Enum("MEP010", 10), new Enum("MEP011", 11), new Enum("MEP012", 12), new Enum("MEP013", 13), new Enum("MEP014", 14), new Enum("MEP015", 15), new Enum("MEP016", 16), new Enum("MEP017", 17), new Enum("NONE", 18), new Enum("OTHER", 19), new Enum("MEP018", 20), new Enum("MEP019", 21), new Enum("MEP020", 22), new Enum("MEP021", 23), new Enum("MEP022", 24), new Enum("MEP023", 25), new Enum("MEP024", 26), new Enum("UN", 27) });
                }
            }
            
            public static final class Factory
            {
                public static Value newValue(final Object obj) {
                    return (Value)Value.type.newValue(obj);
                }
                
                public static Value newInstance() {
                    return (Value)XmlBeans.getContextTypeLoader().newInstance(Value.type, (XmlOptions)null);
                }
                
                public static Value newInstance(final XmlOptions options) {
                    return (Value)XmlBeans.getContextTypeLoader().newInstance(Value.type, options);
                }
                
                private Factory() {
                }
            }
        }
        
        public static final class Factory
        {
            public static Medications newInstance() {
                return (Medications)XmlBeans.getContextTypeLoader().newInstance(Medications.type, (XmlOptions)null);
            }
            
            public static Medications newInstance(final XmlOptions options) {
                return (Medications)XmlBeans.getContextTypeLoader().newInstance(Medications.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * Factory class for creating and parsing PatientInformation instances.
     *
     * <p>Provides static factory methods for creating new PatientInformation objects and parsing
     * XML data from various sources (strings, files, URLs, streams). Supports XML validation and
     * custom parsing options through XmlOptions parameter.</p>
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new PatientInformation instance with default options.
         *
         * @return PatientInformation the new patient information instance
         */
        public static PatientInformation newInstance() {
            return (PatientInformation)XmlBeans.getContextTypeLoader().newInstance(PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Creates a new PatientInformation instance with specified XML options.
         *
         * @param options XmlOptions the XML parsing/generation options
         * @return PatientInformation the new patient information instance
         */
        public static PatientInformation newInstance(final XmlOptions options) {
            return (PatientInformation)XmlBeans.getContextTypeLoader().newInstance(PatientInformation.type, options);
        }

        /**
         * Parses XML string to create a PatientInformation instance.
         *
         * @param xmlAsString String the XML string to parse
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PatientInformation parse(final String xmlAsString) throws XmlException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(xmlAsString, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Parses XML string to create a PatientInformation instance with specified options.
         *
         * @param xmlAsString String the XML string to parse
         * @param options XmlOptions the XML parsing options
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PatientInformation parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(xmlAsString, PatientInformation.type, options);
        }

        /**
         * Parses XML file to create a PatientInformation instance.
         *
         * @param file File the XML file to parse
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the file cannot be read
         */
        public static PatientInformation parse(final File file) throws XmlException, IOException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(file, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Parses XML file to create a PatientInformation instance with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML parsing options
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the file cannot be read
         */
        public static PatientInformation parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(file, PatientInformation.type, options);
        }

        /**
         * Parses XML from URL to create a PatientInformation instance.
         *
         * @param u URL the URL to read XML from
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the URL cannot be accessed
         */
        public static PatientInformation parse(final URL u) throws XmlException, IOException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(u, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Parses XML from URL to create a PatientInformation instance with specified options.
         *
         * @param u URL the URL to read XML from
         * @param options XmlOptions the XML parsing options
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the URL cannot be accessed
         */
        public static PatientInformation parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(u, PatientInformation.type, options);
        }

        /**
         * Parses XML from InputStream to create a PatientInformation instance.
         *
         * @param is InputStream the input stream to read XML from
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the stream cannot be read
         */
        public static PatientInformation parse(final InputStream is) throws XmlException, IOException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(is, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Parses XML from InputStream to create a PatientInformation instance with specified options.
         *
         * @param is InputStream the input stream to read XML from
         * @param options XmlOptions the XML parsing options
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the stream cannot be read
         */
        public static PatientInformation parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(is, PatientInformation.type, options);
        }

        /**
         * Parses XML from Reader to create a PatientInformation instance.
         *
         * @param r Reader the reader to read XML from
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the reader cannot be read
         */
        public static PatientInformation parse(final Reader r) throws XmlException, IOException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(r, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Parses XML from Reader to create a PatientInformation instance with specified options.
         *
         * @param r Reader the reader to read XML from
         * @param options XmlOptions the XML parsing options
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if the reader cannot be read
         */
        public static PatientInformation parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(r, PatientInformation.type, options);
        }
        
        /**
         * Parses XML from XMLStreamReader to create a PatientInformation instance.
         *
         * @param sr XMLStreamReader the stream reader to read XML from
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PatientInformation parse(final XMLStreamReader sr) throws XmlException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(sr, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Parses XML from XMLStreamReader to create a PatientInformation instance with specified options.
         *
         * @param sr XMLStreamReader the stream reader to read XML from
         * @param options XmlOptions the XML parsing options
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PatientInformation parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(sr, PatientInformation.type, options);
        }

        /**
         * Parses XML from DOM Node to create a PatientInformation instance.
         *
         * @param node Node the DOM node to parse
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PatientInformation parse(final Node node) throws XmlException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(node, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Parses XML from DOM Node to create a PatientInformation instance with specified options.
         *
         * @param node Node the DOM node to parse
         * @param options XmlOptions the XML parsing options
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PatientInformation parse(final Node node, final XmlOptions options) throws XmlException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(node, PatientInformation.type, options);
        }

        /**
         * Parses XML from XMLInputStream to create a PatientInformation instance.
         *
         * @deprecated Use alternative parsing methods with modern XML APIs
         * @param xis XMLInputStream the XML input stream to parse
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error in the XML stream
         */
        @Deprecated
        public static PatientInformation parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(xis, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Parses XML from XMLInputStream to create a PatientInformation instance with specified options.
         *
         * @deprecated Use alternative parsing methods with modern XML APIs
         * @param xis XMLInputStream the XML input stream to parse
         * @param options XmlOptions the XML parsing options
         * @return PatientInformation the parsed patient information
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error in the XML stream
         */
        @Deprecated
        public static PatientInformation parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (PatientInformation)XmlBeans.getContextTypeLoader().parse(xis, PatientInformation.type, options);
        }

        /**
         * Creates a validating XMLInputStream for patient information XML.
         *
         * @deprecated Use alternative validation methods with modern XML APIs
         * @param xis XMLInputStream the XML input stream to validate
         * @return XMLInputStream the validating XML input stream
         * @throws XmlException if the XML is invalid
         * @throws XMLStreamException if there is an error in the XML stream
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PatientInformation.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream for patient information XML with specified options.
         *
         * @deprecated Use alternative validation methods with modern XML APIs
         * @param xis XMLInputStream the XML input stream to validate
         * @param options XmlOptions the XML validation options
         * @return XMLInputStream the validating XML input stream
         * @throws XmlException if the XML is invalid
         * @throws XMLStreamException if there is an error in the XML stream
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PatientInformation.type, options);
        }

        /** Private constructor to prevent instantiation. */
        private Factory() {
        }
    }
}
