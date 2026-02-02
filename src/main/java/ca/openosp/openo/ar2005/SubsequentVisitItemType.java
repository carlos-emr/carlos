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
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * XMLBeans interface representing a subsequent prenatal visit record in the AR2005 (British Columbia Antenatal Record) system.
 *
 * <p>This interface provides structured access to clinical data captured during follow-up prenatal appointments,
 * including maternal vital signs, fetal measurements, and clinical observations. It is part of the BCAR (British
 * Columbia Antenatal Record) form integration used for standardized prenatal care documentation throughout pregnancy.</p>
 *
 * <p>The interface supports tracking of key prenatal health indicators across multiple visits:</p>
 * <ul>
 *   <li>Visit date and gestational age (GA)</li>
 *   <li>Maternal measurements: weight, blood pressure (BP)</li>
 *   <li>Urinalysis results: protein (PR) and glucose (GI)</li>
 *   <li>Symphysis-fundal height (SFH) measurements</li>
 *   <li>Fetal presentation and position</li>
 *   <li>Fetal heart rate (FHR) and fetal movement (Fm)</li>
 *   <li>Clinical comments and observations</li>
 * </ul>
 *
 * <p>This is an XMLBeans-generated interface that provides type-safe access to XML document content
 * conforming to the AR2005 schema. The interface includes nested type definitions (Ga, Weight, Bp)
 * and a Factory class for parsing XML and creating new instances.</p>
 *
 * @since 2026-01-24
 * @see ca.openosp.openo.ar2005
 * @see org.apache.xmlbeans.XmlObject
 */
public interface SubsequentVisitItemType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(SubsequentVisitItemType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("subsequentvisititemtypeb4c8type");

    /**
     * Gets the visit date for this subsequent prenatal appointment.
     *
     * @return Calendar the date and time of the prenatal visit
     */
    Calendar getDate();

    /**
     * Gets the visit date as an XmlDate object for XML serialization.
     *
     * @return XmlDate the visit date in XMLBeans date format
     */
    XmlDate xgetDate();

    /**
     * Checks if the visit date is nil (not set).
     *
     * @return boolean true if the date is nil, false otherwise
     */
    boolean isNilDate();

    /**
     * Sets the visit date for this subsequent prenatal appointment.
     *
     * @param p0 Calendar the date and time of the prenatal visit
     */
    void setDate(final Calendar p0);

    /**
     * Sets the visit date using an XmlDate object for XML deserialization.
     *
     * @param p0 XmlDate the visit date in XMLBeans date format
     */
    void xsetDate(final XmlDate p0);

    /**
     * Sets the visit date to nil (not set).
     */
    void setNilDate();

    /**
     * Gets the gestational age (GA) at the time of this visit.
     *
     * @return String the gestational age value (typically in weeks and days format)
     */
    String getGa();

    /**
     * Gets the gestational age as a Ga type object for XML serialization.
     *
     * @return Ga the gestational age in XMLBeans Ga type format
     */
    Ga xgetGa();

    /**
     * Sets the gestational age at the time of this visit.
     *
     * @param p0 String the gestational age value (typically in weeks and days format)
     */
    void setGa(final String p0);

    /**
     * Sets the gestational age using a Ga type object for XML deserialization.
     *
     * @param p0 Ga the gestational age in XMLBeans Ga type format
     */
    void xsetGa(final Ga p0);

    /**
     * Gets the maternal weight measurement recorded during this visit.
     *
     * @return float the weight value (typically in kilograms)
     */
    float getWeight();

    /**
     * Gets the maternal weight as a Weight type object for XML serialization.
     *
     * @return Weight the weight value in XMLBeans Weight type format
     */
    Weight xgetWeight();

    /**
     * Checks if the weight measurement is nil (not recorded).
     *
     * @return boolean true if the weight is nil, false otherwise
     */
    boolean isNilWeight();

    /**
     * Sets the maternal weight measurement for this visit.
     *
     * @param p0 float the weight value (typically in kilograms)
     */
    void setWeight(final float p0);

    /**
     * Sets the maternal weight using a Weight type object for XML deserialization.
     *
     * @param p0 Weight the weight value in XMLBeans Weight type format
     */
    void xsetWeight(final Weight p0);

    /**
     * Sets the weight measurement to nil (not recorded).
     */
    void setNilWeight();

    /**
     * Gets the maternal blood pressure (BP) measurement recorded during this visit.
     *
     * @return String the blood pressure value (typically in systolic/diastolic format, e.g., "120/80")
     */
    String getBp();

    /**
     * Gets the blood pressure as a Bp type object for XML serialization.
     *
     * @return Bp the blood pressure value in XMLBeans Bp type format
     */
    Bp xgetBp();

    /**
     * Sets the maternal blood pressure measurement for this visit.
     *
     * @param p0 String the blood pressure value (typically in systolic/diastolic format, e.g., "120/80")
     */
    void setBp(final String p0);

    /**
     * Sets the blood pressure using a Bp type object for XML deserialization.
     *
     * @param p0 Bp the blood pressure value in XMLBeans Bp type format
     */
    void xsetBp(final Bp p0);

    /**
     * Gets the urine protein (PR) test result from urinalysis.
     *
     * @return String the protein level in urine (e.g., "negative", "trace", "+", "++", "+++")
     */
    String getUrinePR();

    /**
     * Gets the urine protein test result as an XmlString object for XML serialization.
     *
     * @return XmlString the protein level in XMLBeans string format
     */
    XmlString xgetUrinePR();

    /**
     * Sets the urine protein test result from urinalysis.
     *
     * @param p0 String the protein level in urine (e.g., "negative", "trace", "+", "++", "+++")
     */
    void setUrinePR(final String p0);

    /**
     * Sets the urine protein test result using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the protein level in XMLBeans string format
     */
    void xsetUrinePR(final XmlString p0);

    /**
     * Gets the urine glucose (GI) test result from urinalysis.
     *
     * @return String the glucose level in urine (e.g., "negative", "trace", "+", "++", "+++")
     */
    String getUrineGI();

    /**
     * Gets the urine glucose test result as an XmlString object for XML serialization.
     *
     * @return XmlString the glucose level in XMLBeans string format
     */
    XmlString xgetUrineGI();

    /**
     * Sets the urine glucose test result from urinalysis.
     *
     * @param p0 String the glucose level in urine (e.g., "negative", "trace", "+", "++", "+++")
     */
    void setUrineGI(final String p0);

    /**
     * Sets the urine glucose test result using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the glucose level in XMLBeans string format
     */
    void xsetUrineGI(final XmlString p0);

    /**
     * Gets the symphysis-fundal height (SFH) measurement.
     * <p>SFH is measured from the pubic symphysis to the top of the uterine fundus and is used to
     * assess fetal growth and amniotic fluid volume during pregnancy.</p>
     *
     * @return String the SFH measurement (typically in centimeters)
     */
    String getSFH();

    /**
     * Gets the symphysis-fundal height as an XmlString object for XML serialization.
     *
     * @return XmlString the SFH measurement in XMLBeans string format
     */
    XmlString xgetSFH();

    /**
     * Sets the symphysis-fundal height measurement.
     *
     * @param p0 String the SFH measurement (typically in centimeters)
     */
    void setSFH(final String p0);

    /**
     * Sets the symphysis-fundal height using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the SFH measurement in XMLBeans string format
     */
    void xsetSFH(final XmlString p0);

    /**
     * Gets the fetal presentation and position.
     * <p>This describes how the fetus is positioned in the uterus (e.g., vertex/cephalic, breech)
     * and the specific position (e.g., LOA - left occiput anterior, ROA - right occiput anterior).</p>
     *
     * @return String the fetal presentation and position description
     */
    String getPresentationPosition();

    /**
     * Gets the fetal presentation and position as an XmlString object for XML serialization.
     *
     * @return XmlString the presentation and position in XMLBeans string format
     */
    XmlString xgetPresentationPosition();

    /**
     * Sets the fetal presentation and position.
     *
     * @param p0 String the fetal presentation and position description
     */
    void setPresentationPosition(final String p0);

    /**
     * Sets the fetal presentation and position using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the presentation and position in XMLBeans string format
     */
    void xsetPresentationPosition(final XmlString p0);

    /**
     * Gets the fetal heart rate (FHR) and fetal movement (Fm) observations.
     * <p>FHR is typically measured in beats per minute (bpm). Fetal movement indicates whether
     * the mother reports feeling the baby move (quickening typically begins around 18-25 weeks).</p>
     *
     * @return String the FHR and fetal movement observation (e.g., "140 bpm, +")
     */
    String getFHRFm();

    /**
     * Gets the FHR and fetal movement as an XmlString object for XML serialization.
     *
     * @return XmlString the FHR and fetal movement in XMLBeans string format
     */
    XmlString xgetFHRFm();

    /**
     * Sets the fetal heart rate and fetal movement observations.
     *
     * @param p0 String the FHR and fetal movement observation (e.g., "140 bpm, +")
     */
    void setFHRFm(final String p0);

    /**
     * Sets the FHR and fetal movement using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the FHR and fetal movement in XMLBeans string format
     */
    void xsetFHRFm(final XmlString p0);

    /**
     * Gets clinical comments or notes recorded during this visit.
     * <p>This field captures any additional observations, concerns, patient questions,
     * or clinical notes that don't fit in the structured fields above.</p>
     *
     * @return String the clinical comments or notes
     */
    String getComments();

    /**
     * Gets the clinical comments as an XmlString object for XML serialization.
     *
     * @return XmlString the comments in XMLBeans string format
     */
    XmlString xgetComments();

    /**
     * Sets clinical comments or notes for this visit.
     *
     * @param p0 String the clinical comments or notes
     */
    void setComments(final String p0);

    /**
     * Sets the clinical comments using an XmlString object for XML deserialization.
     *
     * @param p0 XmlString the comments in XMLBeans string format
     */
    void xsetComments(final XmlString p0);

    /**
     * XMLBeans type definition for gestational age (GA) values.
     * <p>This nested interface provides type-safe access to gestational age data within
     * the SubsequentVisitItemType XML structure. Gestational age is typically expressed
     * in weeks and days format (e.g., "32+4" meaning 32 weeks and 4 days).</p>
     *
     * @see org.apache.xmlbeans.XmlString
     */
    public interface Ga extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Ga.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("gab9beelemtype");

        /**
         * Factory class for creating and managing Ga type instances.
         * <p>Provides methods to create new Ga objects from various sources.</p>
         */
        public static final class Factory
        {
            /**
             * Creates a new Ga instance from an existing object value.
             *
             * @param obj Object the source object to create the Ga instance from
             * @return Ga the newly created Ga instance
             */
            public static Ga newValue(final Object obj) {
                return (Ga)Ga.type.newValue(obj);
            }

            /**
             * Creates a new empty Ga instance.
             *
             * @return Ga the newly created Ga instance
             */
            public static Ga newInstance() {
                return (Ga)XmlBeans.getContextTypeLoader().newInstance(Ga.type, (XmlOptions)null);
            }

            /**
             * Creates a new empty Ga instance with the specified XML options.
             *
             * @param options XmlOptions the XML options to use for instance creation
             * @return Ga the newly created Ga instance
             */
            public static Ga newInstance(final XmlOptions options) {
                return (Ga)XmlBeans.getContextTypeLoader().newInstance(Ga.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * XMLBeans type definition for maternal weight values.
     * <p>This nested interface provides type-safe access to weight measurements within
     * the SubsequentVisitItemType XML structure. Weight is typically recorded in kilograms
     * and monitored throughout pregnancy to assess maternal and fetal health.</p>
     *
     * @see org.apache.xmlbeans.XmlFloat
     */
    public interface Weight extends XmlFloat
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Weight.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("weightc2dcelemtype");

        /**
         * Factory class for creating and managing Weight type instances.
         * <p>Provides methods to create new Weight objects from various sources.</p>
         */
        public static final class Factory
        {
            /**
             * Creates a new Weight instance from an existing object value.
             *
             * @param obj Object the source object to create the Weight instance from
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
             * Creates a new empty Weight instance with the specified XML options.
             *
             * @param options XmlOptions the XML options to use for instance creation
             * @return Weight the newly created Weight instance
             */
            public static Weight newInstance(final XmlOptions options) {
                return (Weight)XmlBeans.getContextTypeLoader().newInstance(Weight.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * XMLBeans type definition for maternal blood pressure (BP) values.
     * <p>This nested interface provides type-safe access to blood pressure measurements within
     * the SubsequentVisitItemType XML structure. Blood pressure is typically recorded in systolic/diastolic
     * format (e.g., "120/80") and is a critical vital sign monitored throughout pregnancy to detect
     * conditions such as gestational hypertension or preeclampsia.</p>
     *
     * @see org.apache.xmlbeans.XmlString
     */
    public interface Bp extends XmlString
    {
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Bp.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("bp6632elemtype");

        /**
         * Factory class for creating and managing Bp type instances.
         * <p>Provides methods to create new Bp objects from various sources.</p>
         */
        public static final class Factory
        {
            /**
             * Creates a new Bp instance from an existing object value.
             *
             * @param obj Object the source object to create the Bp instance from
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
             * Creates a new empty Bp instance with the specified XML options.
             *
             * @param options XmlOptions the XML options to use for instance creation
             * @return Bp the newly created Bp instance
             */
            public static Bp newInstance(final XmlOptions options) {
                return (Bp)XmlBeans.getContextTypeLoader().newInstance(Bp.type, options);
            }
            
            private Factory() {
            }
        }
    }

    /**
     * Factory class for creating and parsing SubsequentVisitItemType instances.
     * <p>This factory provides multiple methods to create new instances or parse existing
     * XML documents into SubsequentVisitItemType objects. It supports parsing from various
     * sources including String, File, URL, InputStream, Reader, Node, and XMLStreamReader.</p>
     */
    public static final class Factory
    {
        /**
         * Creates a new empty SubsequentVisitItemType instance.
         *
         * @return SubsequentVisitItemType the newly created instance
         */
        public static SubsequentVisitItemType newInstance() {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().newInstance(SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty SubsequentVisitItemType instance with the specified XML options.
         *
         * @param options XmlOptions the XML options to use for instance creation
         * @return SubsequentVisitItemType the newly created instance
         */
        public static SubsequentVisitItemType newInstance(final XmlOptions options) {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().newInstance(SubsequentVisitItemType.type, options);
        }

        /**
         * Parses an XML string into a SubsequentVisitItemType instance.
         *
         * @param xmlAsString String the XML content as a string
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         */
        public static SubsequentVisitItemType parse(final String xmlAsString) throws XmlException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(xmlAsString, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string into a SubsequentVisitItemType instance with the specified XML options.
         *
         * @param xmlAsString String the XML content as a string
         * @param options XmlOptions the XML options to use for parsing
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         */
        public static SubsequentVisitItemType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(xmlAsString, SubsequentVisitItemType.type, options);
        }

        /**
         * Parses an XML file into a SubsequentVisitItemType instance.
         *
         * @param file File the XML file to parse
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws IOException if there is an error reading the file
         */
        public static SubsequentVisitItemType parse(final File file) throws XmlException, IOException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(file, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file into a SubsequentVisitItemType instance with the specified XML options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML options to use for parsing
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws IOException if there is an error reading the file
         */
        public static SubsequentVisitItemType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(file, SubsequentVisitItemType.type, options);
        }

        /**
         * Parses XML from a URL into a SubsequentVisitItemType instance.
         *
         * @param u URL the URL pointing to the XML content
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws IOException if there is an error reading from the URL
         */
        public static SubsequentVisitItemType parse(final URL u) throws XmlException, IOException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(u, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a URL into a SubsequentVisitItemType instance with the specified XML options.
         *
         * @param u URL the URL pointing to the XML content
         * @param options XmlOptions the XML options to use for parsing
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws IOException if there is an error reading from the URL
         */
        public static SubsequentVisitItemType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(u, SubsequentVisitItemType.type, options);
        }

        /**
         * Parses XML from an InputStream into a SubsequentVisitItemType instance.
         *
         * @param is InputStream the input stream containing XML content
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws IOException if there is an error reading from the input stream
         */
        public static SubsequentVisitItemType parse(final InputStream is) throws XmlException, IOException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(is, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an InputStream into a SubsequentVisitItemType instance with the specified XML options.
         *
         * @param is InputStream the input stream containing XML content
         * @param options XmlOptions the XML options to use for parsing
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws IOException if there is an error reading from the input stream
         */
        public static SubsequentVisitItemType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(is, SubsequentVisitItemType.type, options);
        }

        /**
         * Parses XML from a Reader into a SubsequentVisitItemType instance.
         *
         * @param r Reader the reader containing XML content
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws IOException if there is an error reading from the reader
         */
        public static SubsequentVisitItemType parse(final Reader r) throws XmlException, IOException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(r, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a Reader into a SubsequentVisitItemType instance with the specified XML options.
         *
         * @param r Reader the reader containing XML content
         * @param options XmlOptions the XML options to use for parsing
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws IOException if there is an error reading from the reader
         */
        public static SubsequentVisitItemType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(r, SubsequentVisitItemType.type, options);
        }

        /**
         * Parses XML from an XMLStreamReader into a SubsequentVisitItemType instance.
         *
         * @param sr XMLStreamReader the XML stream reader
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         */
        public static SubsequentVisitItemType parse(final XMLStreamReader sr) throws XmlException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(sr, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLStreamReader into a SubsequentVisitItemType instance with the specified XML options.
         *
         * @param sr XMLStreamReader the XML stream reader
         * @param options XmlOptions the XML options to use for parsing
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         */
        public static SubsequentVisitItemType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(sr, SubsequentVisitItemType.type, options);
        }

        /**
         * Parses XML from a DOM Node into a SubsequentVisitItemType instance.
         *
         * @param node Node the DOM node containing XML content
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         */
        public static SubsequentVisitItemType parse(final Node node) throws XmlException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(node, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a DOM Node into a SubsequentVisitItemType instance with the specified XML options.
         *
         * @param node Node the DOM node containing XML content
         * @param options XmlOptions the XML options to use for parsing
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         */
        public static SubsequentVisitItemType parse(final Node node, final XmlOptions options) throws XmlException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(node, SubsequentVisitItemType.type, options);
        }

        /**
         * Parses XML from an XMLInputStream into a SubsequentVisitItemType instance.
         *
         * @param xis XMLInputStream the XML input stream
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws XMLStreamException if there is an error with the XML stream
         * @deprecated XMLInputStream is deprecated, use alternative parse methods instead
         */
        @Deprecated
        public static SubsequentVisitItemType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(xis, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLInputStream into a SubsequentVisitItemType instance with the specified XML options.
         *
         * @param xis XMLInputStream the XML input stream
         * @param options XmlOptions the XML options to use for parsing
         * @return SubsequentVisitItemType the parsed instance
         * @throws XmlException if the XML is not valid or cannot be parsed
         * @throws XMLStreamException if there is an error with the XML stream
         * @deprecated XMLInputStream is deprecated, use alternative parse methods instead
         */
        @Deprecated
        public static SubsequentVisitItemType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (SubsequentVisitItemType)XmlBeans.getContextTypeLoader().parse(xis, SubsequentVisitItemType.type, options);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @return XMLInputStream the validating XML input stream
         * @throws XmlException if the XML is not valid
         * @throws XMLStreamException if there is an error with the XML stream
         * @deprecated XMLInputStream is deprecated, use alternative validation methods instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, SubsequentVisitItemType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with the specified XML options.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @param options XmlOptions the XML options to use for validation
         * @return XMLInputStream the validating XML input stream
         * @throws XmlException if the XML is not valid
         * @throws XMLStreamException if there is an error with the XML stream
         * @deprecated XMLInputStream is deprecated, use alternative validation methods instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, SubsequentVisitItemType.type, options);
        }
        
        private Factory() {
        }
    }
}
