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
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * XMLBeans interface for the British Columbia Antenatal Record 2005 (AR2) form.
 *
 * <p>This interface provides programmatic access to the AR2 form data structure, which is used
 * in British Columbia for standardized antenatal care documentation. The AR2 form captures
 * comprehensive pregnancy-related information including risk factors, immunoprophylaxis
 * recommendations, subsequent visit records, ultrasound results, laboratory investigations,
 * and discussion topics with patients.</p>
 *
 * <p>This interface is generated from an XML Schema Definition (XSD) and provides type-safe
 * access to all elements and attributes defined in the AR2 schema. It extends XmlObject to
 * provide standard XMLBeans functionality for parsing, validation, and serialization.</p>
 *
 * <p>Healthcare providers use this interface to programmatically read and write AR2 form data,
 * ensuring compliance with BC provincial antenatal care documentation standards.</p>
 *
 * @see RiskFactorItemType
 * @see RecommendedImmunoprophylaxisType
 * @see SubsequentVisitItemType
 * @see UltrasoundType
 * @see AdditionalLabInvestigationsType
 * @see DiscussionTopicsType
 * @see SignatureType
 * @since 2026-01-24
 */
public interface AR2 extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(AR2.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("ar23326type");

    /**
     * Retrieves the complete array of pregnancy risk factors.
     *
     * @return RiskFactorItemType array containing all documented risk factors for the pregnancy
     */
    RiskFactorItemType[] getRiskFactorListArray();

    /**
     * Retrieves a specific risk factor item by index position.
     *
     * @param p0 int the zero-based index of the risk factor to retrieve
     * @return RiskFactorItemType the risk factor at the specified index
     */
    RiskFactorItemType getRiskFactorListArray(final int p0);

    /**
     * Returns the number of risk factor items in the list.
     *
     * @return int the total count of risk factors documented
     */
    int sizeOfRiskFactorListArray();

    /**
     * Replaces the entire risk factor list with a new array.
     *
     * @param p0 RiskFactorItemType array the new risk factor list to set
     */
    void setRiskFactorListArray(final RiskFactorItemType[] p0);

    /**
     * Replaces a specific risk factor item at the given index.
     *
     * @param p0 int the zero-based index where the risk factor should be set
     * @param p1 RiskFactorItemType the risk factor item to set at the specified position
     */
    void setRiskFactorListArray(final int p0, final RiskFactorItemType p1);

    /**
     * Inserts a new risk factor item at the specified index position.
     *
     * @param p0 int the zero-based index where the new risk factor should be inserted
     * @return RiskFactorItemType the newly created risk factor item
     */
    RiskFactorItemType insertNewRiskFactorList(final int p0);

    /**
     * Appends a new risk factor item to the end of the list.
     *
     * @return RiskFactorItemType the newly created risk factor item
     */
    RiskFactorItemType addNewRiskFactorList();

    /**
     * Removes the risk factor item at the specified index position.
     *
     * @param p0 int the zero-based index of the risk factor to remove
     */
    void removeRiskFactorList(final int p0);

    /**
     * Retrieves the recommended immunoprophylaxis information for the pregnancy.
     *
     * @return RecommendedImmunoprophylaxisType the immunization and prophylaxis recommendations
     */
    RecommendedImmunoprophylaxisType getRecommendedImmunoprophylaxis();

    /**
     * Sets the recommended immunoprophylaxis information.
     *
     * @param p0 RecommendedImmunoprophylaxisType the immunization and prophylaxis recommendations to set
     */
    void setRecommendedImmunoprophylaxis(final RecommendedImmunoprophylaxisType p0);

    /**
     * Creates and adds a new recommended immunoprophylaxis element.
     *
     * @return RecommendedImmunoprophylaxisType the newly created immunoprophylaxis element
     */
    RecommendedImmunoprophylaxisType addNewRecommendedImmunoprophylaxis();

    /**
     * Retrieves the complete array of subsequent antenatal visit records.
     *
     * @return SubsequentVisitItemType array containing all documented follow-up visits
     */
    SubsequentVisitItemType[] getSubsequentVisitListArray();

    /**
     * Retrieves a specific subsequent visit record by index position.
     *
     * @param p0 int the zero-based index of the visit record to retrieve
     * @return SubsequentVisitItemType the visit record at the specified index
     */
    SubsequentVisitItemType getSubsequentVisitListArray(final int p0);

    /**
     * Returns the number of subsequent visit records in the list.
     *
     * @return int the total count of documented follow-up visits
     */
    int sizeOfSubsequentVisitListArray();

    /**
     * Replaces the entire subsequent visit list with a new array.
     *
     * @param p0 SubsequentVisitItemType array the new visit list to set
     */
    void setSubsequentVisitListArray(final SubsequentVisitItemType[] p0);

    /**
     * Replaces a specific subsequent visit record at the given index.
     *
     * @param p0 int the zero-based index where the visit record should be set
     * @param p1 SubsequentVisitItemType the visit record to set at the specified position
     */
    void setSubsequentVisitListArray(final int p0, final SubsequentVisitItemType p1);

    /**
     * Inserts a new subsequent visit record at the specified index position.
     *
     * @param p0 int the zero-based index where the new visit record should be inserted
     * @return SubsequentVisitItemType the newly created visit record
     */
    SubsequentVisitItemType insertNewSubsequentVisitList(final int p0);

    /**
     * Appends a new subsequent visit record to the end of the list.
     *
     * @return SubsequentVisitItemType the newly created visit record
     */
    SubsequentVisitItemType addNewSubsequentVisitList();

    /**
     * Removes the subsequent visit record at the specified index position.
     *
     * @param p0 int the zero-based index of the visit record to remove
     */
    void removeSubsequentVisitList(final int p0);

    /**
     * Retrieves the complete array of ultrasound examination records.
     *
     * @return UltrasoundType array containing all documented ultrasound results
     */
    UltrasoundType[] getUltrasoundArray();

    /**
     * Retrieves a specific ultrasound examination record by index position.
     *
     * @param p0 int the zero-based index of the ultrasound record to retrieve
     * @return UltrasoundType the ultrasound record at the specified index
     */
    UltrasoundType getUltrasoundArray(final int p0);

    /**
     * Returns the number of ultrasound examination records in the array.
     *
     * @return int the total count of documented ultrasound examinations
     */
    int sizeOfUltrasoundArray();

    /**
     * Replaces the entire ultrasound examination array with a new array.
     *
     * @param p0 UltrasoundType array the new ultrasound records to set
     */
    void setUltrasoundArray(final UltrasoundType[] p0);

    /**
     * Replaces a specific ultrasound examination record at the given index.
     *
     * @param p0 int the zero-based index where the ultrasound record should be set
     * @param p1 UltrasoundType the ultrasound record to set at the specified position
     */
    void setUltrasoundArray(final int p0, final UltrasoundType p1);

    /**
     * Inserts a new ultrasound examination record at the specified index position.
     *
     * @param p0 int the zero-based index where the new ultrasound record should be inserted
     * @return UltrasoundType the newly created ultrasound record
     */
    UltrasoundType insertNewUltrasound(final int p0);

    /**
     * Appends a new ultrasound examination record to the end of the array.
     *
     * @return UltrasoundType the newly created ultrasound record
     */
    UltrasoundType addNewUltrasound();

    /**
     * Removes the ultrasound examination record at the specified index position.
     *
     * @param p0 int the zero-based index of the ultrasound record to remove
     */
    void removeUltrasound(final int p0);

    /**
     * Retrieves the additional laboratory investigations section.
     *
     * @return AdditionalLabInvestigationsType the laboratory test results and investigations
     */
    AdditionalLabInvestigationsType getAdditionalLabInvestigations();

    /**
     * Sets the additional laboratory investigations section.
     *
     * @param p0 AdditionalLabInvestigationsType the laboratory test results to set
     */
    void setAdditionalLabInvestigations(final AdditionalLabInvestigationsType p0);

    /**
     * Creates and adds a new additional laboratory investigations element.
     *
     * @return AdditionalLabInvestigationsType the newly created laboratory investigations element
     */
    AdditionalLabInvestigationsType addNewAdditionalLabInvestigations();

    /**
     * Retrieves the discussion topics section documenting patient education and counseling.
     *
     * @return DiscussionTopicsType the topics discussed with the patient during antenatal visits
     */
    DiscussionTopicsType getDiscussionTopics();

    /**
     * Sets the discussion topics section for patient education documentation.
     *
     * @param p0 DiscussionTopicsType the discussion topics to set
     */
    void setDiscussionTopics(final DiscussionTopicsType p0);

    /**
     * Creates and adds a new discussion topics element.
     *
     * @return DiscussionTopicsType the newly created discussion topics element
     */
    DiscussionTopicsType addNewDiscussionTopics();

    /**
     * Retrieves the signatures section containing healthcare provider authorization.
     *
     * @return SignatureType the provider signatures and authentication information
     */
    SignatureType getSignatures();

    /**
     * Sets the signatures section for healthcare provider authorization.
     *
     * @param p0 SignatureType the signature information to set
     */
    void setSignatures(final SignatureType p0);

    /**
     * Creates and adds a new signatures element.
     *
     * @return SignatureType the newly created signatures element
     */
    SignatureType addNewSignatures();

    /**
     * Factory class providing static methods for creating and parsing AR2 instances.
     *
     * <p>This factory provides various methods to instantiate AR2 objects from different sources
     * including XML strings, files, URLs, input streams, readers, DOM nodes, and XML stream readers.
     * It uses XMLBeans context type loader to handle parsing and validation according to the
     * AR2 schema definition.</p>
     *
     * <p>Healthcare applications should use these factory methods to load existing AR2 form data
     * from XML sources or create new blank AR2 instances for data entry.</p>
     */
    public static final class Factory
    {
        /**
         * Creates a new empty AR2 instance with default options.
         *
         * @return AR2 a new AR2 instance ready for data population
         */
        public static AR2 newInstance() {
            return (AR2)XmlBeans.getContextTypeLoader().newInstance(AR2.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty AR2 instance with specified XML options.
         *
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 a new AR2 instance ready for data population
         */
        public static AR2 newInstance(final XmlOptions options) {
            return (AR2)XmlBeans.getContextTypeLoader().newInstance(AR2.type, options);
        }

        /**
         * Parses an AR2 instance from an XML string representation.
         *
         * @param xmlAsString String the XML string containing AR2 form data
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         */
        public static AR2 parse(final String xmlAsString) throws XmlException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(xmlAsString, AR2.type, (XmlOptions)null);
        }

        /**
         * Parses an AR2 instance from an XML string with specified parsing options.
         *
         * @param xmlAsString String the XML string containing AR2 form data
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         */
        public static AR2 parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(xmlAsString, AR2.type, options);
        }

        /**
         * Parses an AR2 instance from an XML file.
         *
         * @param file File the file containing AR2 form data in XML format
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static AR2 parse(final File file) throws XmlException, IOException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(file, AR2.type, (XmlOptions)null);
        }

        /**
         * Parses an AR2 instance from an XML file with specified parsing options.
         *
         * @param file File the file containing AR2 form data in XML format
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws IOException if an I/O error occurs reading the file
         */
        public static AR2 parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(file, AR2.type, options);
        }

        /**
         * Parses an AR2 instance from a URL pointing to an XML resource.
         *
         * @param u URL the URL pointing to AR2 form data in XML format
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws IOException if an I/O error occurs reading from the URL
         */
        public static AR2 parse(final URL u) throws XmlException, IOException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(u, AR2.type, (XmlOptions)null);
        }

        /**
         * Parses an AR2 instance from a URL with specified parsing options.
         *
         * @param u URL the URL pointing to AR2 form data in XML format
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws IOException if an I/O error occurs reading from the URL
         */
        public static AR2 parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(u, AR2.type, options);
        }

        /**
         * Parses an AR2 instance from an input stream containing XML data.
         *
         * @param is InputStream the input stream containing AR2 form data in XML format
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static AR2 parse(final InputStream is) throws XmlException, IOException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(is, AR2.type, (XmlOptions)null);
        }

        /**
         * Parses an AR2 instance from an input stream with specified parsing options.
         *
         * @param is InputStream the input stream containing AR2 form data in XML format
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws IOException if an I/O error occurs reading from the stream
         */
        public static AR2 parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(is, AR2.type, options);
        }

        /**
         * Parses an AR2 instance from a character reader containing XML data.
         *
         * @param r Reader the character reader containing AR2 form data in XML format
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static AR2 parse(final Reader r) throws XmlException, IOException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(r, AR2.type, (XmlOptions)null);
        }

        /**
         * Parses an AR2 instance from a character reader with specified parsing options.
         *
         * @param r Reader the character reader containing AR2 form data in XML format
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws IOException if an I/O error occurs reading from the reader
         */
        public static AR2 parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(r, AR2.type, options);
        }

        /**
         * Parses an AR2 instance from an XML stream reader.
         *
         * @param sr XMLStreamReader the XML stream reader positioned at AR2 form data
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         */
        public static AR2 parse(final XMLStreamReader sr) throws XmlException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(sr, AR2.type, (XmlOptions)null);
        }

        /**
         * Parses an AR2 instance from an XML stream reader with specified parsing options.
         *
         * @param sr XMLStreamReader the XML stream reader positioned at AR2 form data
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         */
        public static AR2 parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(sr, AR2.type, options);
        }

        /**
         * Parses an AR2 instance from a W3C DOM node.
         *
         * @param node Node the DOM node containing AR2 form data
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         */
        public static AR2 parse(final Node node) throws XmlException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(node, AR2.type, (XmlOptions)null);
        }

        /**
         * Parses an AR2 instance from a W3C DOM node with specified parsing options.
         *
         * @param node Node the DOM node containing AR2 form data
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         */
        public static AR2 parse(final Node node, final XmlOptions options) throws XmlException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(node, AR2.type, options);
        }

        /**
         * Parses an AR2 instance from a deprecated XMLInputStream.
         *
         * @param xis XMLInputStream the XML input stream containing AR2 form data
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws XMLStreamException if an error occurs during XML stream processing
         * @deprecated XMLInputStream is deprecated; use InputStream or XMLStreamReader instead
         */
        @Deprecated
        public static AR2 parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(xis, AR2.type, (XmlOptions)null);
        }

        /**
         * Parses an AR2 instance from a deprecated XMLInputStream with specified parsing options.
         *
         * @param xis XMLInputStream the XML input stream containing AR2 form data
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return AR2 the parsed AR2 instance
         * @throws XmlException if the XML is malformed or does not conform to the AR2 schema
         * @throws XMLStreamException if an error occurs during XML stream processing
         * @deprecated XMLInputStream is deprecated; use InputStream or XMLStreamReader instead
         */
        @Deprecated
        public static AR2 parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (AR2)XmlBeans.getContextTypeLoader().parse(xis, AR2.type, options);
        }

        /**
         * Creates a validating XMLInputStream wrapper around the provided stream.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @return XMLInputStream a validating stream wrapper
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if an error occurs during XML stream processing
         * @deprecated XMLInputStream is deprecated; use XMLStreamReader validation instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, AR2.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream wrapper with specified validation options.
         *
         * @param xis XMLInputStream the XML input stream to validate
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return XMLInputStream a validating stream wrapper
         * @throws XmlException if validation setup fails
         * @throws XMLStreamException if an error occurs during XML stream processing
         * @deprecated XMLInputStream is deprecated; use XMLStreamReader validation instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, AR2.type, options);
        }
        
        private Factory() {
        }
    }
}
