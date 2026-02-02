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
import org.apache.xmlbeans.XmlInt;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Represents the pregnancy history component of the British Columbia Antenatal Record (BCAR AR2005).
 * This interface provides access to comprehensive pregnancy-related data including menstrual history,
 * estimated delivery dates, gravidity/parity information, and contraceptive history.
 *
 * <p>This is an XMLBeans-generated interface that provides strongly-typed access to pregnancy history
 * data stored in XML format. It includes information critical for prenatal care such as:
 * <ul>
 *   <li>Last Menstrual Period (LMP) and menstrual cycle characteristics</li>
 *   <li>Estimated Due Date (EDB) calculations using various dating methods</li>
 *   <li>Obstetric history (Gravida, Term, Premature, Abortuses, Living children)</li>
 *   <li>Contraceptive usage history</li>
 * </ul>
 *
 * <p>This interface supports the BC Ministry of Health's standardized antenatal record forms
 * used across British Columbia healthcare facilities for maternity care documentation.
 *
 * @see YesNoNullType
 * @see DatingMethods
 * @since 2026-01-23
 */
public interface PregnancyHistory extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(PregnancyHistory.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("pregnancyhistory853etype");

    /**
     * Gets the Last Menstrual Period (LMP) date.
     * The LMP is used as the primary method for calculating gestational age and estimated due date.
     *
     * @return Calendar the date of the last menstrual period, or null if not set or nil
     */
    Calendar getLMP();

    /**
     * Gets the Last Menstrual Period (LMP) as an XmlDate object.
     * This provides access to the underlying XML representation of the date.
     *
     * @return XmlDate the LMP date as an XML date object
     */
    XmlDate xgetLMP();

    /**
     * Checks if the LMP field is explicitly set to nil (XML nil attribute).
     *
     * @return boolean true if the LMP is nil, false otherwise
     */
    boolean isNilLMP();

    /**
     * Sets the Last Menstrual Period (LMP) date.
     *
     * @param p0 Calendar the date of the last menstrual period
     */
    void setLMP(final Calendar p0);

    /**
     * Sets the Last Menstrual Period (LMP) using an XmlDate object.
     *
     * @param p0 XmlDate the LMP date as an XML date object
     */
    void xsetLMP(final XmlDate p0);

    /**
     * Sets the LMP field to nil (XML nil attribute true).
     * Use this when the LMP is unknown or not applicable.
     */
    void setNilLMP();
    
    /**
     * Gets whether the patient is certain about the LMP date.
     * This information affects the reliability of gestational age calculations.
     *
     * @return YesNoNullType the certainty status of the LMP date
     */
    YesNoNullType getLMPCertain();

    /**
     * Sets whether the patient is certain about the LMP date.
     *
     * @param p0 YesNoNullType the certainty status (yes, no, or null)
     */
    void setLMPCertain(final YesNoNullType p0);

    /**
     * Creates and adds a new LMPCertain element.
     *
     * @return YesNoNullType a new LMPCertain element instance
     */
    YesNoNullType addNewLMPCertain();

    /**
     * Gets the length of the menstrual cycle in days.
     * Normal menstrual cycles range from 21-35 days, with 28 days being average.
     *
     * @return String the menstrual cycle length as a string representation
     */
    String getMenCycle();

    /**
     * Gets the menstrual cycle length as an XmlString object.
     *
     * @return XmlString the menstrual cycle length as an XML string object
     */
    XmlString xgetMenCycle();

    /**
     * Sets the length of the menstrual cycle.
     *
     * @param p0 String the menstrual cycle length in days
     */
    void setMenCycle(final String p0);

    /**
     * Sets the menstrual cycle length using an XmlString object.
     *
     * @param p0 XmlString the menstrual cycle length as an XML string object
     */
    void xsetMenCycle(final XmlString p0);

    /**
     * Gets whether the menstrual cycle is regular.
     * Regular cycles are important for accurate EDB calculation using LMP.
     *
     * @return YesNoNullType whether the menstrual cycle is regular
     */
    YesNoNullType getMenCycleRegular();

    /**
     * Sets whether the menstrual cycle is regular.
     *
     * @param p0 YesNoNullType the regularity status (yes, no, or null)
     */
    void setMenCycleRegular(final YesNoNullType p0);

    /**
     * Creates and adds a new MenCycleRegular element.
     *
     * @return YesNoNullType a new MenCycleRegular element instance
     */
    YesNoNullType addNewMenCycleRegular();
    
    /**
     * Gets the type of contraceptive method last used before pregnancy.
     * This information is relevant for pregnancy planning and risk assessment.
     *
     * @return String the contraceptive type (e.g., "OCP", "IUD", "Barrier", "None")
     */
    String getContraceptiveType();

    /**
     * Gets the contraceptive type as an XmlString object.
     *
     * @return XmlString the contraceptive type as an XML string object
     */
    XmlString xgetContraceptiveType();

    /**
     * Checks if the contraceptive type field has been set.
     *
     * @return boolean true if the contraceptive type is set, false otherwise
     */
    boolean isSetContraceptiveType();

    /**
     * Sets the type of contraceptive method used.
     *
     * @param p0 String the contraceptive type
     */
    void setContraceptiveType(final String p0);

    /**
     * Sets the contraceptive type using an XmlString object.
     *
     * @param p0 XmlString the contraceptive type as an XML string object
     */
    void xsetContraceptiveType(final XmlString p0);

    /**
     * Removes the contraceptive type field, setting it to unset state.
     */
    void unsetContraceptiveType();

    /**
     * Gets the date when contraceptive was last used.
     * This helps determine if pregnancy was planned and affects early pregnancy dating.
     *
     * @return Calendar the date contraceptive was last used, or null if not set or nil
     */
    Calendar getContraceptiveLastUsed();

    /**
     * Gets the contraceptive last used date as an XmlDate object.
     *
     * @return XmlDate the contraceptive last used date as an XML date object
     */
    XmlDate xgetContraceptiveLastUsed();

    /**
     * Checks if the contraceptive last used field is explicitly set to nil.
     *
     * @return boolean true if the field is nil, false otherwise
     */
    boolean isNilContraceptiveLastUsed();

    /**
     * Sets the date when contraceptive was last used.
     *
     * @param p0 Calendar the date contraceptive was last used
     */
    void setContraceptiveLastUsed(final Calendar p0);

    /**
     * Sets the contraceptive last used date using an XmlDate object.
     *
     * @param p0 XmlDate the date as an XML date object
     */
    void xsetContraceptiveLastUsed(final XmlDate p0);

    /**
     * Sets the contraceptive last used field to nil.
     * Use this when the information is not available or not applicable.
     */
    void setNilContraceptiveLastUsed();
    
    /**
     * Gets the Estimated Date of Birth (EDB) calculated from menstrual history.
     * Typically calculated using Naegele's rule: LMP + 280 days (40 weeks).
     *
     * @return Calendar the menstrual-based estimated due date, or null if not set or nil
     */
    Calendar getMenstrualEDB();

    /**
     * Gets the menstrual EDB as an XmlDate object.
     *
     * @return XmlDate the menstrual EDB as an XML date object
     */
    XmlDate xgetMenstrualEDB();

    /**
     * Checks if the menstrual EDB field is explicitly set to nil.
     *
     * @return boolean true if the menstrual EDB is nil, false otherwise
     */
    boolean isNilMenstrualEDB();

    /**
     * Sets the Estimated Date of Birth calculated from menstrual history.
     *
     * @param p0 Calendar the menstrual-based estimated due date
     */
    void setMenstrualEDB(final Calendar p0);

    /**
     * Sets the menstrual EDB using an XmlDate object.
     *
     * @param p0 XmlDate the menstrual EDB as an XML date object
     */
    void xsetMenstrualEDB(final XmlDate p0);

    /**
     * Sets the menstrual EDB field to nil.
     * Use this when menstrual dating is not reliable or not possible.
     */
    void setNilMenstrualEDB();

    /**
     * Gets the final Estimated Date of Birth (EDB) used for clinical care.
     * This may be based on ultrasound, menstrual history, or other dating methods.
     * The final EDB is the official due date used throughout the pregnancy.
     *
     * @return Calendar the final estimated due date
     */
    Calendar getFinalEDB();

    /**
     * Gets the final EDB as an XmlDate object.
     *
     * @return XmlDate the final EDB as an XML date object
     */
    XmlDate xgetFinalEDB();

    /**
     * Sets the final Estimated Date of Birth used for clinical care.
     *
     * @param p0 Calendar the final estimated due date
     */
    void setFinalEDB(final Calendar p0);

    /**
     * Sets the final EDB using an XmlDate object.
     *
     * @param p0 XmlDate the final EDB as an XML date object
     */
    void xsetFinalEDB(final XmlDate p0);

    /**
     * Gets the dating methods used to determine the final EDB.
     * This includes methods such as ultrasound measurements, LMP calculation, etc.
     *
     * @return DatingMethods the dating methods object containing details of how EDB was determined
     */
    DatingMethods getDatingMethods();

    /**
     * Sets the dating methods used to determine the final EDB.
     *
     * @param p0 DatingMethods the dating methods object
     */
    void setDatingMethods(final DatingMethods p0);

    /**
     * Creates and adds a new DatingMethods element.
     *
     * @return DatingMethods a new DatingMethods element instance
     */
    DatingMethods addNewDatingMethods();
    
    /**
     * Gets the gravida value (total number of pregnancies including current).
     * This is part of the GTPAL (Gravida, Term, Preterm, Abortions, Living) obstetric notation.
     *
     * @return int the total number of pregnancies
     */
    int getGravida();

    /**
     * Gets the gravida value as an XmlInt object.
     *
     * @return XmlInt the gravida value as an XML integer object
     */
    XmlInt xgetGravida();

    /**
     * Sets the gravida value (total number of pregnancies).
     *
     * @param p0 int the total number of pregnancies including current
     */
    void setGravida(final int p0);

    /**
     * Sets the gravida value using an XmlInt object.
     *
     * @param p0 XmlInt the gravida value as an XML integer object
     */
    void xsetGravida(final XmlInt p0);

    /**
     * Gets the number of term deliveries (pregnancies delivered at 37+ weeks).
     * This is the "T" in GTPAL notation.
     *
     * @return int the number of term deliveries
     */
    int getTerm();

    /**
     * Gets the term delivery count as an XmlInt object.
     *
     * @return XmlInt the term delivery count as an XML integer object
     */
    XmlInt xgetTerm();

    /**
     * Sets the number of term deliveries.
     *
     * @param p0 int the number of pregnancies delivered at term (37+ weeks)
     */
    void setTerm(final int p0);

    /**
     * Sets the term delivery count using an XmlInt object.
     *
     * @param p0 XmlInt the term delivery count as an XML integer object
     */
    void xsetTerm(final XmlInt p0);

    /**
     * Gets the number of premature/preterm deliveries (delivered before 37 weeks).
     * This is the "P" in GTPAL notation.
     *
     * @return int the number of premature deliveries
     */
    int getPremature();

    /**
     * Gets the premature delivery count as an XmlInt object.
     *
     * @return XmlInt the premature delivery count as an XML integer object
     */
    XmlInt xgetPremature();

    /**
     * Sets the number of premature/preterm deliveries.
     *
     * @param p0 int the number of pregnancies delivered before 37 weeks
     */
    void setPremature(final int p0);

    /**
     * Sets the premature delivery count using an XmlInt object.
     *
     * @param p0 XmlInt the premature delivery count as an XML integer object
     */
    void xsetPremature(final XmlInt p0);

    /**
     * Gets the number of abortions (spontaneous or induced pregnancy losses before 20 weeks).
     * This is the "A" in GTPAL notation.
     *
     * @return int the number of abortions
     */
    int getAbortuses();

    /**
     * Gets the abortion count as an XmlInt object.
     *
     * @return XmlInt the abortion count as an XML integer object
     */
    XmlInt xgetAbortuses();

    /**
     * Sets the number of abortions (pregnancy losses before 20 weeks).
     *
     * @param p0 int the number of spontaneous or induced abortions
     */
    void setAbortuses(final int p0);

    /**
     * Sets the abortion count using an XmlInt object.
     *
     * @param p0 XmlInt the abortion count as an XML integer object
     */
    void xsetAbortuses(final XmlInt p0);

    /**
     * Gets the number of living children.
     * This is the "L" in GTPAL notation and may differ from total births due to neonatal deaths.
     *
     * @return int the number of living children
     */
    int getLiving();

    /**
     * Gets the living children count as an XmlInt object.
     *
     * @return XmlInt the living children count as an XML integer object
     */
    XmlInt xgetLiving();

    /**
     * Sets the number of living children.
     *
     * @param p0 int the number of living children
     */
    void setLiving(final int p0);

    /**
     * Sets the living children count using an XmlInt object.
     *
     * @param p0 XmlInt the living children count as an XML integer object
     */
    void xsetLiving(final XmlInt p0);
    
    /**
     * Factory class for creating and parsing PregnancyHistory instances.
     * This class provides static factory methods for creating new instances and
     * parsing XML data from various sources into PregnancyHistory objects.
     *
     * @since 2026-01-23
     */
    public static final class Factory
    {
        /**
         * Creates a new empty PregnancyHistory instance.
         *
         * @return PregnancyHistory a new empty instance
         */
        public static PregnancyHistory newInstance() {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().newInstance(PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty PregnancyHistory instance with specified XML options.
         *
         * @param options XmlOptions the XML options to use during creation
         * @return PregnancyHistory a new empty instance
         */
        public static PregnancyHistory newInstance(final XmlOptions options) {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().newInstance(PregnancyHistory.type, options);
        }

        /**
         * Parses an XML string into a PregnancyHistory instance.
         *
         * @param xmlAsString String the XML content as a string
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PregnancyHistory parse(final String xmlAsString) throws XmlException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(xmlAsString, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string into a PregnancyHistory instance with specified options.
         *
         * @param xmlAsString String the XML content as a string
         * @param options XmlOptions the XML options to use during parsing
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PregnancyHistory parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(xmlAsString, PregnancyHistory.type, options);
        }

        /**
         * Parses an XML file into a PregnancyHistory instance.
         *
         * @param file File the XML file to parse
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading the file
         */
        public static PregnancyHistory parse(final File file) throws XmlException, IOException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(file, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file into a PregnancyHistory instance with specified options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML options to use during parsing
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading the file
         */
        public static PregnancyHistory parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(file, PregnancyHistory.type, options);
        }

        /**
         * Parses XML from a URL into a PregnancyHistory instance.
         *
         * @param u URL the URL pointing to the XML content
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading from the URL
         */
        public static PregnancyHistory parse(final URL u) throws XmlException, IOException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(u, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a URL into a PregnancyHistory instance with specified options.
         *
         * @param u URL the URL pointing to the XML content
         * @param options XmlOptions the XML options to use during parsing
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading from the URL
         */
        public static PregnancyHistory parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(u, PregnancyHistory.type, options);
        }

        /**
         * Parses XML from an input stream into a PregnancyHistory instance.
         *
         * @param is InputStream the input stream containing XML data
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading from the stream
         */
        public static PregnancyHistory parse(final InputStream is) throws XmlException, IOException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(is, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an input stream into a PregnancyHistory instance with specified options.
         *
         * @param is InputStream the input stream containing XML data
         * @param options XmlOptions the XML options to use during parsing
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading from the stream
         */
        public static PregnancyHistory parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(is, PregnancyHistory.type, options);
        }

        /**
         * Parses XML from a reader into a PregnancyHistory instance.
         *
         * @param r Reader the reader containing XML data
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading from the reader
         */
        public static PregnancyHistory parse(final Reader r) throws XmlException, IOException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(r, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a reader into a PregnancyHistory instance with specified options.
         *
         * @param r Reader the reader containing XML data
         * @param options XmlOptions the XML options to use during parsing
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws IOException if there is an error reading from the reader
         */
        public static PregnancyHistory parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(r, PregnancyHistory.type, options);
        }

        /**
         * Parses XML from an XML stream reader into a PregnancyHistory instance.
         *
         * @param sr XMLStreamReader the XML stream reader
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PregnancyHistory parse(final XMLStreamReader sr) throws XmlException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(sr, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XML stream reader into a PregnancyHistory instance with specified options.
         *
         * @param sr XMLStreamReader the XML stream reader
         * @param options XmlOptions the XML options to use during parsing
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PregnancyHistory parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(sr, PregnancyHistory.type, options);
        }

        /**
         * Parses XML from a DOM node into a PregnancyHistory instance.
         *
         * @param node Node the DOM node containing XML data
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PregnancyHistory parse(final Node node) throws XmlException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(node, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a DOM node into a PregnancyHistory instance with specified options.
         *
         * @param node Node the DOM node containing XML data
         * @param options XmlOptions the XML options to use during parsing
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         */
        public static PregnancyHistory parse(final Node node, final XmlOptions options) throws XmlException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(node, PregnancyHistory.type, options);
        }

        /**
         * Parses XML from an XMLInputStream into a PregnancyHistory instance.
         *
         * @param xis XMLInputStream the XML input stream
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error with the XML stream
         * @deprecated XMLInputStream is deprecated in XMLBeans
         */
        @Deprecated
        public static PregnancyHistory parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(xis, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XMLInputStream into a PregnancyHistory instance with specified options.
         *
         * @param xis XMLInputStream the XML input stream
         * @param options XmlOptions the XML options to use during parsing
         * @return PregnancyHistory the parsed pregnancy history object
         * @throws XmlException if the XML is invalid or cannot be parsed
         * @throws XMLStreamException if there is an error with the XML stream
         * @deprecated XMLInputStream is deprecated in XMLBeans
         */
        @Deprecated
        public static PregnancyHistory parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (PregnancyHistory)XmlBeans.getContextTypeLoader().parse(xis, PregnancyHistory.type, options);
        }

        /**
         * Creates a new validating XMLInputStream for PregnancyHistory.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if there is an error creating the validating stream
         * @throws XMLStreamException if there is an error with the XML stream
         * @deprecated XMLInputStream is deprecated in XMLBeans
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PregnancyHistory.type, (XmlOptions)null);
        }

        /**
         * Creates a new validating XMLInputStream for PregnancyHistory with specified options.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @param options XmlOptions the XML options to use during validation
         * @return XMLInputStream a validating XML input stream
         * @throws XmlException if there is an error creating the validating stream
         * @throws XMLStreamException if there is an error with the XML stream
         * @deprecated XMLInputStream is deprecated in XMLBeans
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PregnancyHistory.type, options);
        }

        /**
         * Private constructor to prevent instantiation.
         * This class only provides static factory methods.
         */
        private Factory() {
        }
    }
}
