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
 * XML type interface for psychosocial assessment data in antenatal care.
 *
 * <p>This interface represents psychosocial risk factors and concerns assessed during
 * pregnancy care as part of the British Columbia Antenatal Record (BCAR) form system.
 * It captures critical social determinants of health that may impact maternal and fetal
 * outcomes, including social support, mental health, substance use, and family dynamics.</p>
 *
 * <p>Psychosocial assessments are essential components of comprehensive prenatal care,
 * helping healthcare providers identify patients who may benefit from additional support
 * services, mental health interventions, or social work consultations during pregnancy
 * and postpartum periods.</p>
 *
 * <p>This interface is part of the AR2005 (Antenatal Record 2005) package and follows
 * the Apache XMLBeans framework for XML data binding. It provides getter, setter, and
 * factory methods for managing psychosocial assessment data in a type-safe manner.</p>
 *
 * @see YesNoNullType
 * @see org.apache.xmlbeans.XmlObject
 * @since 2026-01-18
 */
public interface PsychosocialType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(PsychosocialType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("psychosocialtype93f2type");

    /**
     * Gets the poor social support indicator value.
     *
     * <p>Assesses whether the patient has inadequate social support networks during pregnancy.
     * Poor social support is a significant risk factor for maternal depression, anxiety, and
     * adverse pregnancy outcomes. This assessment helps identify patients who may benefit from
     * community resources, support groups, or social work interventions.</p>
     *
     * @return YesNoNullType the poor social support indicator, or null if not set
     */
    YesNoNullType getPoortSocialSupport();

    /**
     * Sets the poor social support indicator value.
     *
     * @param p0 YesNoNullType the poor social support indicator to set
     */
    void setPoortSocialSupport(final YesNoNullType p0);

    /**
     * Creates and adds a new poor social support indicator.
     *
     * <p>This factory method creates a new YesNoNullType instance and sets it as the
     * poor social support value for this psychosocial assessment.</p>
     *
     * @return YesNoNullType the newly created poor social support indicator
     */
    YesNoNullType addNewPoortSocialSupport();
    
    /**
     * Gets the relationship problems indicator value.
     *
     * <p>Assesses whether the patient is experiencing relationship difficulties with their partner
     * or other significant relationships. Relationship problems during pregnancy can contribute to
     * maternal stress, mental health issues, and may impact prenatal care adherence and birth outcomes.
     * This assessment helps identify patients who may benefit from couples counseling or family therapy.</p>
     *
     * @return YesNoNullType the relationship problems indicator, or null if not set
     */
    YesNoNullType getRelationshipProblems();

    /**
     * Sets the relationship problems indicator value.
     *
     * @param p0 YesNoNullType the relationship problems indicator to set
     */
    void setRelationshipProblems(final YesNoNullType p0);

    /**
     * Creates and adds a new relationship problems indicator.
     *
     * <p>This factory method creates a new YesNoNullType instance and sets it as the
     * relationship problems value for this psychosocial assessment.</p>
     *
     * @return YesNoNullType the newly created relationship problems indicator
     */
    YesNoNullType addNewRelationshipProblems();
    
    /**
     * Gets the emotional depression indicator value.
     *
     * <p>Assesses whether the patient is experiencing symptoms of depression during pregnancy.
     * Perinatal depression affects up to 20% of pregnant individuals and is associated with
     * adverse maternal and fetal outcomes. Early identification enables timely interventions
     * including counseling, therapy, or medication management when appropriate. This assessment
     * is critical for maternal mental health screening and prevention of postpartum depression.</p>
     *
     * @return YesNoNullType the emotional depression indicator, or null if not set
     */
    YesNoNullType getEmotionalDepression();

    /**
     * Sets the emotional depression indicator value.
     *
     * @param p0 YesNoNullType the emotional depression indicator to set
     */
    void setEmotionalDepression(final YesNoNullType p0);

    /**
     * Creates and adds a new emotional depression indicator.
     *
     * <p>This factory method creates a new YesNoNullType instance and sets it as the
     * emotional depression value for this psychosocial assessment.</p>
     *
     * @return YesNoNullType the newly created emotional depression indicator
     */
    YesNoNullType addNewEmotionalDepression();
    
    /**
     * Gets the substance abuse indicator value.
     *
     * <p>Assesses whether the patient has current or recent substance use concerns during pregnancy,
     * including alcohol, tobacco, recreational drugs, or misuse of prescription medications. Substance
     * use during pregnancy poses significant risks to fetal development and maternal health. Identification
     * enables appropriate intervention, referral to addiction services, and enhanced monitoring throughout
     * pregnancy. This assessment is conducted in a non-judgmental manner to facilitate honest disclosure
     * and appropriate support.</p>
     *
     * @return YesNoNullType the substance abuse indicator, or null if not set
     */
    YesNoNullType getSubstanceAbuse();

    /**
     * Sets the substance abuse indicator value.
     *
     * @param p0 YesNoNullType the substance abuse indicator to set
     */
    void setSubstanceAbuse(final YesNoNullType p0);

    /**
     * Creates and adds a new substance abuse indicator.
     *
     * <p>This factory method creates a new YesNoNullType instance and sets it as the
     * substance abuse value for this psychosocial assessment.</p>
     *
     * @return YesNoNullType the newly created substance abuse indicator
     */
    YesNoNullType addNewSubstanceAbuse();
    
    /**
     * Gets the family violence indicator value.
     *
     * <p>Assesses whether the patient is experiencing domestic violence, intimate partner violence,
     * or other forms of family violence. Pregnancy is a time of increased risk for domestic violence,
     * which poses serious threats to both maternal and fetal health. Routine screening enables early
     * identification, safety planning, and referral to specialized domestic violence services, counseling,
     * and legal resources. This sensitive assessment is conducted privately and confidentially to ensure
     * patient safety.</p>
     *
     * @return YesNoNullType the family violence indicator, or null if not set
     */
    YesNoNullType getFamilyViolence();

    /**
     * Sets the family violence indicator value.
     *
     * @param p0 YesNoNullType the family violence indicator to set
     */
    void setFamilyViolence(final YesNoNullType p0);

    /**
     * Creates and adds a new family violence indicator.
     *
     * <p>This factory method creates a new YesNoNullType instance and sets it as the
     * family violence value for this psychosocial assessment.</p>
     *
     * @return YesNoNullType the newly created family violence indicator
     */
    YesNoNullType addNewFamilyViolence();
    
    /**
     * Gets the parenting concerns indicator value.
     *
     * <p>Assesses whether the patient has concerns, anxiety, or uncertainty about their ability to
     * parent or care for the newborn. This may include worries about parenting skills, financial
     * resources, childcare arrangements, or management of other children in the household. Identifying
     * parenting concerns allows healthcare providers to offer prenatal education, parenting classes,
     * community resources, and psychosocial support to build parental confidence and competence.</p>
     *
     * @return YesNoNullType the parenting concerns indicator, or null if not set
     */
    YesNoNullType getParentingConcerns();

    /**
     * Sets the parenting concerns indicator value.
     *
     * @param p0 YesNoNullType the parenting concerns indicator to set
     */
    void setParentingConcerns(final YesNoNullType p0);

    /**
     * Creates and adds a new parenting concerns indicator.
     *
     * <p>This factory method creates a new YesNoNullType instance and sets it as the
     * parenting concerns value for this psychosocial assessment.</p>
     *
     * @return YesNoNullType the newly created parenting concerns indicator
     */
    YesNoNullType addNewParentingConcerns();
    
    /**
     * Gets the religious/cultural concerns indicator value.
     *
     * <p>Assesses whether the patient has religious or cultural considerations that may impact
     * their pregnancy care, birth planning, or postpartum care. This may include dietary restrictions,
     * modesty requirements, preferences for same-gender providers, ritual practices, or traditional
     * beliefs about pregnancy and childbirth. Identifying these concerns enables culturally competent,
     * patient-centered care that respects individual values and beliefs while ensuring safe medical
     * management.</p>
     *
     * @return YesNoNullType the religious/cultural concerns indicator, or null if not set
     */
    YesNoNullType getReligiousCultural();

    /**
     * Sets the religious/cultural concerns indicator value.
     *
     * @param p0 YesNoNullType the religious/cultural concerns indicator to set
     */
    void setReligiousCultural(final YesNoNullType p0);

    /**
     * Creates and adds a new religious/cultural concerns indicator.
     *
     * <p>This factory method creates a new YesNoNullType instance and sets it as the
     * religious/cultural concerns value for this psychosocial assessment.</p>
     *
     * @return YesNoNullType the newly created religious/cultural concerns indicator
     */
    YesNoNullType addNewReligiousCultural();

    /**
     * Factory class for creating and parsing PsychosocialType instances.
     *
     * <p>This nested factory class provides static methods for instantiating PsychosocialType
     * objects and parsing XML content into PsychosocialType instances. It follows the Apache
     * XMLBeans factory pattern for type-safe XML data binding.</p>
     *
     * <p>The factory supports multiple input sources including strings, files, URLs, streams,
     * readers, DOM nodes, and XML stream readers. Some methods are deprecated in favor of
     * newer XML processing approaches.</p>
     */
    public static final class Factory
    {
        /**
         * Creates a new empty PsychosocialType instance.
         *
         * @return PsychosocialType a new instance with default XML options
         */
        public static PsychosocialType newInstance() {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().newInstance(PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Creates a new empty PsychosocialType instance with custom XML options.
         *
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType a new instance with the specified options
         */
        public static PsychosocialType newInstance(final XmlOptions options) {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().newInstance(PsychosocialType.type, options);
        }
        
        /**
         * Parses an XML string into a PsychosocialType instance.
         *
         * @param xmlAsString String the XML content to parse
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         */
        public static PsychosocialType parse(final String xmlAsString) throws XmlException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(xmlAsString, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML string into a PsychosocialType instance with custom options.
         *
         * @param xmlAsString String the XML content to parse
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         */
        public static PsychosocialType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(xmlAsString, PsychosocialType.type, options);
        }

        /**
         * Parses an XML file into a PsychosocialType instance.
         *
         * @param file File the XML file to parse
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws IOException if the file cannot be read
         */
        public static PsychosocialType parse(final File file) throws XmlException, IOException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(file, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Parses an XML file into a PsychosocialType instance with custom options.
         *
         * @param file File the XML file to parse
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws IOException if the file cannot be read
         */
        public static PsychosocialType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(file, PsychosocialType.type, options);
        }

        /**
         * Parses XML from a URL into a PsychosocialType instance.
         *
         * @param u URL the URL pointing to the XML content
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws IOException if the URL content cannot be retrieved
         */
        public static PsychosocialType parse(final URL u) throws XmlException, IOException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(u, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a URL into a PsychosocialType instance with custom options.
         *
         * @param u URL the URL pointing to the XML content
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws IOException if the URL content cannot be retrieved
         */
        public static PsychosocialType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(u, PsychosocialType.type, options);
        }
        
        /**
         * Parses XML from an input stream into a PsychosocialType instance.
         *
         * @param is InputStream the input stream containing XML content
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws IOException if the stream cannot be read
         */
        public static PsychosocialType parse(final InputStream is) throws XmlException, IOException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(is, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an input stream into a PsychosocialType instance with custom options.
         *
         * @param is InputStream the input stream containing XML content
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws IOException if the stream cannot be read
         */
        public static PsychosocialType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(is, PsychosocialType.type, options);
        }

        /**
         * Parses XML from a character reader into a PsychosocialType instance.
         *
         * @param r Reader the reader containing XML content
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws IOException if the reader cannot be read
         */
        public static PsychosocialType parse(final Reader r) throws XmlException, IOException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(r, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a character reader into a PsychosocialType instance with custom options.
         *
         * @param r Reader the reader containing XML content
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws IOException if the reader cannot be read
         */
        public static PsychosocialType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(r, PsychosocialType.type, options);
        }

        /**
         * Parses XML from an XML stream reader into a PsychosocialType instance.
         *
         * @param sr XMLStreamReader the stream reader positioned at the start of the XML content
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         */
        public static PsychosocialType parse(final XMLStreamReader sr) throws XmlException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(sr, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from an XML stream reader into a PsychosocialType instance with custom options.
         *
         * @param sr XMLStreamReader the stream reader positioned at the start of the XML content
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         */
        public static PsychosocialType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(sr, PsychosocialType.type, options);
        }

        /**
         * Parses XML from a DOM node into a PsychosocialType instance.
         *
         * @param node Node the DOM node containing the XML content
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         */
        public static PsychosocialType parse(final Node node) throws XmlException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(node, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a DOM node into a PsychosocialType instance with custom options.
         *
         * @param node Node the DOM node containing the XML content
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         */
        public static PsychosocialType parse(final Node node, final XmlOptions options) throws XmlException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(node, PsychosocialType.type, options);
        }
        
        /**
         * Parses XML from a legacy XMLInputStream into a PsychosocialType instance.
         *
         * @param xis XMLInputStream the XML input stream (deprecated API)
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws XMLStreamException if there is an error processing the XML stream
         * @deprecated Use {@link #parse(InputStream)} or {@link #parse(XMLStreamReader)} instead
         */
        @Deprecated
        public static PsychosocialType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(xis, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Parses XML from a legacy XMLInputStream into a PsychosocialType instance with custom options.
         *
         * @param xis XMLInputStream the XML input stream (deprecated API)
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return PsychosocialType the parsed instance
         * @throws XmlException if the XML is malformed or doesn't match the schema
         * @throws XMLStreamException if there is an error processing the XML stream
         * @deprecated Use {@link #parse(InputStream, XmlOptions)} or {@link #parse(XMLStreamReader, XmlOptions)} instead
         */
        @Deprecated
        public static PsychosocialType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (PsychosocialType)XmlBeans.getContextTypeLoader().parse(xis, PsychosocialType.type, options);
        }

        /**
         * Creates a validating XMLInputStream wrapper for schema validation.
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation (deprecated API)
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if there is an error setting up validation
         * @throws XMLStreamException if there is an error processing the XML stream
         * @deprecated Use modern StAX-based validation approaches instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PsychosocialType.type, (XmlOptions)null);
        }

        /**
         * Creates a validating XMLInputStream wrapper for schema validation with custom options.
         *
         * @param xis XMLInputStream the XML input stream to wrap with validation (deprecated API)
         * @param options XmlOptions the XML parsing and validation options to apply
         * @return XMLInputStream a validating wrapper around the input stream
         * @throws XmlException if there is an error setting up validation
         * @throws XMLStreamException if there is an error processing the XML stream
         * @deprecated Use modern StAX-based validation approaches instead
         */
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PsychosocialType.type, options);
        }

        /**
         * Private constructor to prevent instantiation of the factory class.
         *
         * <p>All factory methods are static and should be accessed directly through the Factory class.</p>
         */
        private Factory() {
        }
    }
}
