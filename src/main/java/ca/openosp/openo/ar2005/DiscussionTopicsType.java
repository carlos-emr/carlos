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
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Represents prenatal discussion topics for the Antenatal Record (AR2005) healthcare form.
 *
 * This interface defines a comprehensive set of discussion topics that healthcare providers
 * should cover during prenatal care visits. It includes topics covering pregnancy lifestyle
 * (exercise, work, travel), prenatal education (classes, birth planning), medical concerns
 * (preterm labour, bleeding, fetal movement), labour and delivery planning (pain management,
 * admission timing, labour support), newborn care (breastfeeding, circumcision, car seat safety),
 * and postpartum topics (depression screening, contraception, postpartum care).
 *
 * This type is part of the British Columbia Antenatal Record (BCAR) system and is used to
 * track which prenatal education topics have been discussed with the patient during their
 * pregnancy care. Each topic is represented as a boolean flag indicating whether it has been
 * addressed with the patient.
 *
 * This interface is generated using Apache XMLBeans from an XML schema definition and provides
 * strongly-typed access to prenatal discussion topic data for healthcare documentation and
 * compliance purposes.
 *
 * @see XmlObject
 * @since 2026-01-24
 */
public interface DiscussionTopicsType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(DiscussionTopicsType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("discussiontopicstype115dtype");

    /**
     * Gets the exercise discussion flag indicating whether exercise during pregnancy has been discussed.
     *
     * @return boolean true if exercise topic has been discussed, false otherwise
     */
    boolean getExercise();

    /**
     * Gets the exercise discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the exercise discussion flag as an XML boolean
     */
    XmlBoolean xgetExercise();

    /**
     * Sets the exercise discussion flag.
     *
     * @param p0 boolean true if exercise topic has been discussed, false otherwise
     */
    void setExercise(final boolean p0);

    /**
     * Sets the exercise discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the exercise discussion flag as an XML boolean
     */
    void xsetExercise(final XmlBoolean p0);

    /**
     * Gets the work plan discussion flag indicating whether work planning during pregnancy has been discussed.
     *
     * @return boolean true if work plan topic has been discussed, false otherwise
     */
    boolean getWorkPlan();

    /**
     * Gets the work plan discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the work plan discussion flag as an XML boolean
     */
    XmlBoolean xgetWorkPlan();

    /**
     * Sets the work plan discussion flag.
     *
     * @param p0 boolean true if work plan topic has been discussed, false otherwise
     */
    void setWorkPlan(final boolean p0);

    /**
     * Sets the work plan discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the work plan discussion flag as an XML boolean
     */
    void xsetWorkPlan(final XmlBoolean p0);

    /**
     * Gets the intercourse discussion flag indicating whether sexual intercourse during pregnancy has been discussed.
     *
     * @return boolean true if intercourse topic has been discussed, false otherwise
     */
    boolean getIntercourse();

    /**
     * Gets the intercourse discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the intercourse discussion flag as an XML boolean
     */
    XmlBoolean xgetIntercourse();

    /**
     * Sets the intercourse discussion flag.
     *
     * @param p0 boolean true if intercourse topic has been discussed, false otherwise
     */
    void setIntercourse(final boolean p0);

    /**
     * Sets the intercourse discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the intercourse discussion flag as an XML boolean
     */
    void xsetIntercourse(final XmlBoolean p0);

    /**
     * Gets the travel discussion flag indicating whether travel during pregnancy has been discussed.
     *
     * @return boolean true if travel topic has been discussed, false otherwise
     */
    boolean getTravel();

    /**
     * Gets the travel discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the travel discussion flag as an XML boolean
     */
    XmlBoolean xgetTravel();

    /**
     * Sets the travel discussion flag.
     *
     * @param p0 boolean true if travel topic has been discussed, false otherwise
     */
    void setTravel(final boolean p0);

    /**
     * Sets the travel discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the travel discussion flag as an XML boolean
     */
    void xsetTravel(final XmlBoolean p0);

    /**
     * Gets the prenatal classes discussion flag indicating whether prenatal education classes have been discussed.
     *
     * @return boolean true if prenatal classes topic has been discussed, false otherwise
     */
    boolean getPrenatalClasses();

    /**
     * Gets the prenatal classes discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the prenatal classes discussion flag as an XML boolean
     */
    XmlBoolean xgetPrenatalClasses();

    /**
     * Sets the prenatal classes discussion flag.
     *
     * @param p0 boolean true if prenatal classes topic has been discussed, false otherwise
     */
    void setPrenatalClasses(final boolean p0);

    /**
     * Sets the prenatal classes discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the prenatal classes discussion flag as an XML boolean
     */
    void xsetPrenatalClasses(final XmlBoolean p0);

    /**
     * Gets the birth plan discussion flag indicating whether birth planning has been discussed.
     *
     * @return boolean true if birth plan topic has been discussed, false otherwise
     */
    boolean getBirthPlan();

    /**
     * Gets the birth plan discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the birth plan discussion flag as an XML boolean
     */
    XmlBoolean xgetBirthPlan();

    /**
     * Sets the birth plan discussion flag.
     *
     * @param p0 boolean true if birth plan topic has been discussed, false otherwise
     */
    void setBirthPlan(final boolean p0);

    /**
     * Sets the birth plan discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the birth plan discussion flag as an XML boolean
     */
    void xsetBirthPlan(final XmlBoolean p0);

    /**
     * Gets the on-call providers discussion flag indicating whether on-call provider information has been discussed.
     *
     * @return boolean true if on-call providers topic has been discussed, false otherwise
     */
    boolean getOnCallProviders();

    /**
     * Gets the on-call providers discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the on-call providers discussion flag as an XML boolean
     */
    XmlBoolean xgetOnCallProviders();

    /**
     * Sets the on-call providers discussion flag.
     *
     * @param p0 boolean true if on-call providers topic has been discussed, false otherwise
     */
    void setOnCallProviders(final boolean p0);

    /**
     * Sets the on-call providers discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the on-call providers discussion flag as an XML boolean
     */
    void xsetOnCallProviders(final XmlBoolean p0);

    /**
     * Gets the preterm labour discussion flag indicating whether signs and risks of preterm labour have been discussed.
     *
     * @return boolean true if preterm labour topic has been discussed, false otherwise
     */
    boolean getPretermLabour();

    /**
     * Gets the preterm labour discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the preterm labour discussion flag as an XML boolean
     */
    XmlBoolean xgetPretermLabour();

    /**
     * Sets the preterm labour discussion flag.
     *
     * @param p0 boolean true if preterm labour topic has been discussed, false otherwise
     */
    void setPretermLabour(final boolean p0);

    /**
     * Sets the preterm labour discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the preterm labour discussion flag as an XML boolean
     */
    void xsetPretermLabour(final XmlBoolean p0);

    /**
     * Gets the PROM discussion flag indicating whether Premature Rupture of Membranes has been discussed.
     *
     * @return boolean true if PROM topic has been discussed, false otherwise
     */
    boolean getPROM();

    /**
     * Gets the PROM discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the PROM discussion flag as an XML boolean
     */
    XmlBoolean xgetPROM();

    /**
     * Sets the PROM discussion flag.
     *
     * @param p0 boolean true if PROM topic has been discussed, false otherwise
     */
    void setPROM(final boolean p0);

    /**
     * Sets the PROM discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the PROM discussion flag as an XML boolean
     */
    void xsetPROM(final XmlBoolean p0);

    /**
     * Gets the APH discussion flag indicating whether Antepartum Hemorrhage (bleeding during pregnancy) has been discussed.
     *
     * @return boolean true if APH topic has been discussed, false otherwise
     */
    boolean getAPH();

    /**
     * Gets the APH discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the APH discussion flag as an XML boolean
     */
    XmlBoolean xgetAPH();

    /**
     * Sets the APH discussion flag.
     *
     * @param p0 boolean true if APH topic has been discussed, false otherwise
     */
    void setAPH(final boolean p0);

    /**
     * Sets the APH discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the APH discussion flag as an XML boolean
     */
    void xsetAPH(final XmlBoolean p0);

    /**
     * Gets the fetal movement discussion flag indicating whether monitoring fetal movement has been discussed.
     *
     * @return boolean true if fetal movement topic has been discussed, false otherwise
     */
    boolean getFetalMovement();

    /**
     * Gets the fetal movement discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the fetal movement discussion flag as an XML boolean
     */
    XmlBoolean xgetFetalMovement();

    /**
     * Sets the fetal movement discussion flag.
     *
     * @param p0 boolean true if fetal movement topic has been discussed, false otherwise
     */
    void setFetalMovement(final boolean p0);

    /**
     * Sets the fetal movement discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the fetal movement discussion flag as an XML boolean
     */
    void xsetFetalMovement(final XmlBoolean p0);

    /**
     * Gets the admission timing discussion flag indicating whether when to come to hospital for labour has been discussed.
     *
     * @return boolean true if admission timing topic has been discussed, false otherwise
     */
    boolean getAdmissionTiming();

    /**
     * Gets the admission timing discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the admission timing discussion flag as an XML boolean
     */
    XmlBoolean xgetAdmissionTiming();

    /**
     * Sets the admission timing discussion flag.
     *
     * @param p0 boolean true if admission timing topic has been discussed, false otherwise
     */
    void setAdmissionTiming(final boolean p0);

    /**
     * Sets the admission timing discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the admission timing discussion flag as an XML boolean
     */
    void xsetAdmissionTiming(final XmlBoolean p0);

    /**
     * Gets the pain management discussion flag indicating whether labour pain management options have been discussed.
     *
     * @return boolean true if pain management topic has been discussed, false otherwise
     */
    boolean getPainManagement();

    /**
     * Gets the pain management discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the pain management discussion flag as an XML boolean
     */
    XmlBoolean xgetPainManagement();

    /**
     * Sets the pain management discussion flag.
     *
     * @param p0 boolean true if pain management topic has been discussed, false otherwise
     */
    void setPainManagement(final boolean p0);

    /**
     * Sets the pain management discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the pain management discussion flag as an XML boolean
     */
    void xsetPainManagement(final XmlBoolean p0);

    /**
     * Gets the labour support discussion flag indicating whether support persons during labour have been discussed.
     *
     * @return boolean true if labour support topic has been discussed, false otherwise
     */
    boolean getLabourSupport();

    /**
     * Gets the labour support discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the labour support discussion flag as an XML boolean
     */
    XmlBoolean xgetLabourSupport();

    /**
     * Sets the labour support discussion flag.
     *
     * @param p0 boolean true if labour support topic has been discussed, false otherwise
     */
    void setLabourSupport(final boolean p0);

    /**
     * Sets the labour support discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the labour support discussion flag as an XML boolean
     */
    void xsetLabourSupport(final XmlBoolean p0);

    /**
     * Gets the breastfeeding discussion flag indicating whether breastfeeding and infant feeding options have been discussed.
     *
     * @return boolean true if breastfeeding topic has been discussed, false otherwise
     */
    boolean getBreastFeeding();

    /**
     * Gets the breastfeeding discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the breastfeeding discussion flag as an XML boolean
     */
    XmlBoolean xgetBreastFeeding();

    /**
     * Sets the breastfeeding discussion flag.
     *
     * @param p0 boolean true if breastfeeding topic has been discussed, false otherwise
     */
    void setBreastFeeding(final boolean p0);

    /**
     * Sets the breastfeeding discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the breastfeeding discussion flag as an XML boolean
     */
    void xsetBreastFeeding(final XmlBoolean p0);

    /**
     * Gets the circumcision discussion flag indicating whether newborn circumcision has been discussed.
     *
     * @return boolean true if circumcision topic has been discussed, false otherwise
     */
    boolean getCircumcision();

    /**
     * Gets the circumcision discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the circumcision discussion flag as an XML boolean
     */
    XmlBoolean xgetCircumcision();

    /**
     * Sets the circumcision discussion flag.
     *
     * @param p0 boolean true if circumcision topic has been discussed, false otherwise
     */
    void setCircumcision(final boolean p0);

    /**
     * Sets the circumcision discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the circumcision discussion flag as an XML boolean
     */
    void xsetCircumcision(final XmlBoolean p0);

    /**
     * Gets the discharge planning discussion flag indicating whether hospital discharge planning has been discussed.
     *
     * @return boolean true if discharge planning topic has been discussed, false otherwise
     */
    boolean getDischargePlanning();

    /**
     * Gets the discharge planning discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the discharge planning discussion flag as an XML boolean
     */
    XmlBoolean xgetDischargePlanning();

    /**
     * Sets the discharge planning discussion flag.
     *
     * @param p0 boolean true if discharge planning topic has been discussed, false otherwise
     */
    void setDischargePlanning(final boolean p0);

    /**
     * Sets the discharge planning discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the discharge planning discussion flag as an XML boolean
     */
    void xsetDischargePlanning(final XmlBoolean p0);

    /**
     * Gets the car seat safety discussion flag indicating whether infant car seat safety has been discussed.
     *
     * @return boolean true if car seat safety topic has been discussed, false otherwise
     */
    boolean getCarSeatSafety();

    /**
     * Gets the car seat safety discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the car seat safety discussion flag as an XML boolean
     */
    XmlBoolean xgetCarSeatSafety();

    /**
     * Sets the car seat safety discussion flag.
     *
     * @param p0 boolean true if car seat safety topic has been discussed, false otherwise
     */
    void setCarSeatSafety(final boolean p0);

    /**
     * Sets the car seat safety discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the car seat safety discussion flag as an XML boolean
     */
    void xsetCarSeatSafety(final XmlBoolean p0);

    /**
     * Gets the depression discussion flag indicating whether postpartum depression screening has been discussed.
     *
     * @return boolean true if depression topic has been discussed, false otherwise
     */
    boolean getDepression();

    /**
     * Gets the depression discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the depression discussion flag as an XML boolean
     */
    XmlBoolean xgetDepression();

    /**
     * Sets the depression discussion flag.
     *
     * @param p0 boolean true if depression topic has been discussed, false otherwise
     */
    void setDepression(final boolean p0);

    /**
     * Sets the depression discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the depression discussion flag as an XML boolean
     */
    void xsetDepression(final XmlBoolean p0);

    /**
     * Gets the contraception discussion flag indicating whether postpartum contraception options have been discussed.
     *
     * @return boolean true if contraception topic has been discussed, false otherwise
     */
    boolean getContraception();

    /**
     * Gets the contraception discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the contraception discussion flag as an XML boolean
     */
    XmlBoolean xgetContraception();

    /**
     * Sets the contraception discussion flag.
     *
     * @param p0 boolean true if contraception topic has been discussed, false otherwise
     */
    void setContraception(final boolean p0);

    /**
     * Sets the contraception discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the contraception discussion flag as an XML boolean
     */
    void xsetContraception(final XmlBoolean p0);

    /**
     * Gets the postpartum care discussion flag indicating whether postpartum care and follow-up have been discussed.
     *
     * @return boolean true if postpartum care topic has been discussed, false otherwise
     */
    boolean getPostpartumCare();

    /**
     * Gets the postpartum care discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the postpartum care discussion flag as an XML boolean
     */
    XmlBoolean xgetPostpartumCare();

    /**
     * Sets the postpartum care discussion flag.
     *
     * @param p0 boolean true if postpartum care topic has been discussed, false otherwise
     */
    void setPostpartumCare(final boolean p0);

    /**
     * Sets the postpartum care discussion flag using an XmlBoolean object.
     *
     * @param p0 XmlBoolean the postpartum care discussion flag as an XML boolean
     */
    void xsetPostpartumCare(final XmlBoolean p0);

    /**
     * Factory class for creating and parsing DiscussionTopicsType instances.
     *
     * This class provides static factory methods for creating new instances of DiscussionTopicsType
     * and parsing XML data from various sources (String, File, URL, InputStream, Reader, XMLStreamReader,
     * DOM Node, and XMLInputStream). It follows the Apache XMLBeans factory pattern for XML binding.
     *
     * @since 2026-01-24
     */
    public static final class Factory
    {
        /**
         * Creates a new empty DiscussionTopicsType instance with default options.
         *
         * @return DiscussionTopicsType a new empty instance
         */
        public static DiscussionTopicsType newInstance() {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().newInstance(DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty DiscussionTopicsType instance with specified XML options.
         *
         * @param options XmlOptions the XML options to use when creating the instance
         * @return DiscussionTopicsType a new empty instance
         */
        public static DiscussionTopicsType newInstance(final XmlOptions options) {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().newInstance(DiscussionTopicsType.type, options);
        }

        /**
         * Parses an XML string into a DiscussionTopicsType instance.
         *
         * @param xmlAsString String the XML string to parse
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static DiscussionTopicsType parse(final String xmlAsString) throws XmlException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(xmlAsString, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string into a DiscussionTopicsType instance with specified options.
         *
         * @param xmlAsString String the XML string to parse
         * @param options XmlOptions the XML options to use when parsing
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static DiscussionTopicsType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(xmlAsString, DiscussionTopicsType.type, options);
        }

        /**
         * Parses an XML file into a DiscussionTopicsType instance.
         *
         * @param file File the XML file to parse
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading the file
         */
        public static DiscussionTopicsType parse(final File file) throws XmlException, IOException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(file, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file into a DiscussionTopicsType instance with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML options to use when parsing
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading the file
         */
        public static DiscussionTopicsType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(file, DiscussionTopicsType.type, options);
        }

        /**
         * Parses XML from a URL into a DiscussionTopicsType instance.
         *
         * @param u URL the URL to parse XML from
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading from the URL
         */
        public static DiscussionTopicsType parse(final URL u) throws XmlException, IOException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(u, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a URL into a DiscussionTopicsType instance with specified options.
         *
         * @param u URL the URL to parse XML from
         * @param options XmlOptions the XML options to use when parsing
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading from the URL
         */
        public static DiscussionTopicsType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(u, DiscussionTopicsType.type, options);
        }

        /**
         * Parses XML from an InputStream into a DiscussionTopicsType instance.
         *
         * @param is InputStream the input stream to parse XML from
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading from the stream
         */
        public static DiscussionTopicsType parse(final InputStream is) throws XmlException, IOException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(is, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an InputStream into a DiscussionTopicsType instance with specified options.
         *
         * @param is InputStream the input stream to parse XML from
         * @param options XmlOptions the XML options to use when parsing
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading from the stream
         */
        public static DiscussionTopicsType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(is, DiscussionTopicsType.type, options);
        }

        /**
         * Parses XML from a Reader into a DiscussionTopicsType instance.
         *
         * @param r Reader the reader to parse XML from
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading
         */
        public static DiscussionTopicsType parse(final Reader r) throws XmlException, IOException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(r, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a Reader into a DiscussionTopicsType instance with specified options.
         *
         * @param r Reader the reader to parse XML from
         * @param options XmlOptions the XML options to use when parsing
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if an I/O error occurs while reading
         */
        public static DiscussionTopicsType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(r, DiscussionTopicsType.type, options);
        }

        /**
         * Parses XML from an XMLStreamReader into a DiscussionTopicsType instance.
         *
         * @param sr XMLStreamReader the stream reader to parse XML from
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static DiscussionTopicsType parse(final XMLStreamReader sr) throws XmlException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(sr, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLStreamReader into a DiscussionTopicsType instance with specified options.
         *
         * @param sr XMLStreamReader the stream reader to parse XML from
         * @param options XmlOptions the XML options to use when parsing
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static DiscussionTopicsType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(sr, DiscussionTopicsType.type, options);
        }

        /**
         * Parses XML from a DOM Node into a DiscussionTopicsType instance.
         *
         * @param node Node the DOM node to parse XML from
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static DiscussionTopicsType parse(final Node node) throws XmlException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(node, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a DOM Node into a DiscussionTopicsType instance with specified options.
         *
         * @param node Node the DOM node to parse XML from
         * @param options XmlOptions the XML options to use when parsing
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static DiscussionTopicsType parse(final Node node, final XmlOptions options) throws XmlException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(node, DiscussionTopicsType.type, options);
        }

        /**
         * Parses XML from an XMLInputStream into a DiscussionTopicsType instance.
         *
         * @param xis XMLInputStream the XML input stream to parse from
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if an error occurs while processing the stream
         * @deprecated XMLInputStream is deprecated, use InputStream or XMLStreamReader instead
         */
        @Deprecated
        public static DiscussionTopicsType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(xis, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLInputStream into a DiscussionTopicsType instance with specified options.
         *
         * @param xis XMLInputStream the XML input stream to parse from
         * @param options XmlOptions the XML options to use when parsing
         * @return DiscussionTopicsType the parsed instance
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if an error occurs while processing the stream
         * @deprecated XMLInputStream is deprecated, use InputStream or XMLStreamReader instead
         */
        @Deprecated
        public static DiscussionTopicsType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (DiscussionTopicsType)XmlBeans.getContextTypeLoader().parse(xis, DiscussionTopicsType.type, options);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream.
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if the XML is invalid or cannot be validated
         * @throws XMLStreamException if an error occurs while processing the stream
         * @deprecated XMLInputStream is deprecated, use InputStream or XMLStreamReader instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, DiscussionTopicsType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream from an existing XMLInputStream with specified options.
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation
         * @param options XmlOptions the XML options to use when validating
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if the XML is invalid or cannot be validated
         * @throws XMLStreamException if an error occurs while processing the stream
         * @deprecated XMLInputStream is deprecated, use InputStream or XMLStreamReader instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, DiscussionTopicsType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of the Factory class.
         * All methods in this class are static and should be called directly on the class.
         */
        private Factory() {
        }
    }
}
