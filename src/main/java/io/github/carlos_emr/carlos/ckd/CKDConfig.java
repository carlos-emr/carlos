package io.github.carlos_emr.carlos.ckd;

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
 * Main configuration interface for Chronic Kidney Disease (CKD) management system.
 *
 * This XMLBeans-generated interface represents the root configuration element for the
 * CKD clinical decision support system. It aggregates all configuration components
 * needed for CKD patient monitoring, including diagnostic codes, blood pressure targets,
 * patient history parameters, drug configurations, and exclusion criteria.
 *
 * The CKD configuration system enables healthcare providers to customize clinical
 * guidelines and monitoring protocols based on local practice patterns and patient
 * populations. This interface serves as the primary entry point for accessing and
 * modifying CKD management settings.
 *
 * Configuration components:
 * - DxCodes: ICD diagnostic codes for CKD and related conditions
 * - Bp: Blood pressure target values for CKD patients
 * - Hx: Patient history parameters and risk factors
 * - Drugs: Medication configurations for CKD treatment
 * - Excludes: Exclusion criteria for CKD protocols
 *
 * @since 2010-01-01
 * @see DxCodes
 * @see Bp
 * @see Hx
 * @see Drugs
 * @see Excludes
 * @see io.github.carlos_emr.carlos.ckd.impl.CKDConfigImpl
 */
public interface CKDConfig extends XmlObject
{
    /**
     * SchemaType system definition for XMLBeans type mapping.
     * Defines the schema type used for CKD configuration serialization.
     */
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(CKDConfig.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("ckdconfig3b4dtype");

    /**
     * Gets the diagnostic codes configuration.
     *
     * Returns the DxCodes component containing ICD-9/ICD-10 diagnostic codes
     * used to identify CKD and related conditions. These codes are essential
     * for automated patient identification and risk stratification.
     *
     * @return DxCodes configuration containing diagnostic code mappings
     */
    DxCodes getDxCodes();

    /**
     * Sets the diagnostic codes configuration.
     *
     * Updates the diagnostic codes used for CKD patient identification.
     * This affects how the system recognizes and categorizes CKD patients
     * based on their diagnostic history.
     *
     * @param p0 DxCodes configuration to set
     */
    void setDxCodes(final DxCodes p0);

    /**
     * Creates and adds a new diagnostic codes configuration.
     *
     * Creates a new empty DxCodes instance and sets it as the current
     * diagnostic codes configuration. The returned instance can be
     * populated with specific diagnostic code mappings.
     *
     * @return DxCodes newly created diagnostic codes configuration
     */
    DxCodes addNewDxCodes();

    /**
     * Gets the blood pressure configuration.
     *
     * Returns the Bp component containing systolic and diastolic blood
     * pressure targets for CKD patients. These targets guide clinical
     * management and alert thresholds.
     *
     * @return Bp blood pressure configuration with target values
     */
    Bp getBp();

    /**
     * Sets the blood pressure configuration.
     *
     * Updates the blood pressure targets used for CKD patient monitoring.
     * Changes affect clinical alerts and treatment recommendations.
     *
     * @param p0 Bp blood pressure configuration to set
     */
    void setBp(final Bp p0);

    /**
     * Creates and adds a new blood pressure configuration.
     *
     * Creates a new empty Bp instance and sets it as the current
     * blood pressure configuration. The instance can be populated
     * with systolic and diastolic target values.
     *
     * @return Bp newly created blood pressure configuration
     */
    Bp addNewBp();

    /**
     * Gets the patient history configuration.
     *
     * Returns the Hx component containing patient history parameters
     * and risk factors used in CKD assessment. This includes medical
     * history elements relevant to kidney disease progression.
     *
     * @return Hx patient history configuration
     */
    Hx getHx();

    /**
     * Sets the patient history configuration.
     *
     * Updates the patient history parameters used for CKD risk assessment
     * and disease progression monitoring.
     *
     * @param p0 Hx patient history configuration to set
     */
    void setHx(final Hx p0);

    /**
     * Creates and adds a new patient history configuration.
     *
     * Creates a new empty Hx instance for configuring patient history
     * parameters relevant to CKD management.
     *
     * @return Hx newly created patient history configuration
     */
    Hx addNewHx();

    /**
     * Gets the drugs configuration.
     *
     * Returns the Drugs component containing medication configurations
     * for CKD treatment protocols. This includes nephrotoxic drugs to
     * avoid and recommended medications for CKD management.
     *
     * @return Drugs medication configuration, or null if not set
     */
    Drugs getDrugs();

    /**
     * Checks if drugs configuration is set.
     *
     * Determines whether a drugs configuration component exists in the
     * current CKD configuration. This is an optional component.
     *
     * @return boolean true if drugs configuration exists, false otherwise
     */
    boolean isSetDrugs();

    /**
     * Sets the drugs configuration.
     *
     * Updates the medication configuration for CKD treatment protocols.
     * This affects drug interaction checking and prescribing recommendations.
     *
     * @param p0 Drugs medication configuration to set
     */
    void setDrugs(final Drugs p0);

    /**
     * Creates and adds a new drugs configuration.
     *
     * Creates a new empty Drugs instance for configuring medication
     * parameters in CKD management protocols.
     *
     * @return Drugs newly created medication configuration
     */
    Drugs addNewDrugs();

    /**
     * Removes the drugs configuration.
     *
     * Clears the drugs configuration component from the CKD configuration.
     * After this call, isSetDrugs() will return false.
     */
    void unsetDrugs();

    /**
     * Gets the exclusion criteria configuration.
     *
     * Returns the Excludes component containing criteria for excluding
     * patients from CKD protocols. This may include contraindications
     * or conditions that require alternative management approaches.
     *
     * @return Excludes exclusion criteria configuration, or null if not set
     */
    Excludes getExcludes();

    /**
     * Checks if exclusion criteria configuration is set.
     *
     * Determines whether an exclusion criteria component exists in the
     * current CKD configuration. This is an optional component.
     *
     * @return boolean true if exclusion criteria exists, false otherwise
     */
    boolean isSetExcludes();

    /**
     * Sets the exclusion criteria configuration.
     *
     * Updates the exclusion criteria for CKD protocol eligibility.
     * This affects which patients are included in CKD management workflows.
     *
     * @param p0 Excludes exclusion criteria configuration to set
     */
    void setExcludes(final Excludes p0);

    /**
     * Creates and adds a new exclusion criteria configuration.
     *
     * Creates a new empty Excludes instance for defining patient
     * exclusion criteria in CKD protocols.
     *
     * @return Excludes newly created exclusion criteria configuration
     */
    Excludes addNewExcludes();

    /**
     * Removes the exclusion criteria configuration.
     *
     * Clears the exclusion criteria from the CKD configuration.
     * After this call, isSetExcludes() will return false.
     */
    void unsetExcludes();

    /**
     * Factory class for creating and parsing CKDConfig instances.
     *
     * Provides static factory methods for instantiating and deserializing
     * CKD configuration from various sources. Follows the XMLBeans factory
     * pattern for flexible configuration loading.
     */
    public static final class Factory
    {
        /**
         * Creates a new empty CKDConfig instance.
         *
         * Creates a new CKD configuration with no initial values.
         * Components can be added and configured programmatically.
         *
         * @return CKDConfig new empty configuration instance
         */
        public static CKDConfig newInstance() {
            return (CKDConfig)XmlBeans.getContextTypeLoader().newInstance(CKDConfig.type, (XmlOptions)null);
        }

        /**
         * Creates a new CKDConfig instance with XML options.
         *
         * Creates a new configuration with custom XML processing options.
         *
         * @param options XmlOptions for XML processing configuration
         * @return CKDConfig new configuration instance
         */
        public static CKDConfig newInstance(final XmlOptions options) {
            return (CKDConfig)XmlBeans.getContextTypeLoader().newInstance(CKDConfig.type, options);
        }

        /**
         * Parses CKD configuration from an XML string.
         *
         * Deserializes a complete CKD configuration from XML text.
         *
         * @param xmlAsString String containing XML configuration
         * @return CKDConfig parsed configuration object
         * @throws XmlException if XML is malformed or invalid
         */
        public static CKDConfig parse(final String xmlAsString) throws XmlException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(xmlAsString, CKDConfig.type, (XmlOptions)null);
        }

        /**
         * Parses CKD configuration from XML string with options.
         *
         * @param xmlAsString String containing XML configuration
         * @param options XmlOptions for parsing control
         * @return CKDConfig parsed configuration object
         * @throws XmlException if XML is malformed or invalid
         */
        public static CKDConfig parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(xmlAsString, CKDConfig.type, options);
        }

        /**
         * Parses CKD configuration from a file.
         *
         * Loads configuration from an XML file on the filesystem.
         *
         * @param file File containing XML configuration
         * @return CKDConfig parsed configuration object
         * @throws XmlException if XML is malformed or invalid
         * @throws IOException if file cannot be read
         */
        public static CKDConfig parse(final File file) throws XmlException, IOException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(file, CKDConfig.type, (XmlOptions)null);
        }
        
        public static CKDConfig parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(file, CKDConfig.type, options);
        }
        
        public static CKDConfig parse(final URL u) throws XmlException, IOException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(u, CKDConfig.type, (XmlOptions)null);
        }
        
        public static CKDConfig parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(u, CKDConfig.type, options);
        }
        
        public static CKDConfig parse(final InputStream is) throws XmlException, IOException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(is, CKDConfig.type, (XmlOptions)null);
        }
        
        public static CKDConfig parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(is, CKDConfig.type, options);
        }
        
        public static CKDConfig parse(final Reader r) throws XmlException, IOException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(r, CKDConfig.type, (XmlOptions)null);
        }
        
        public static CKDConfig parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(r, CKDConfig.type, options);
        }
        
        public static CKDConfig parse(final XMLStreamReader sr) throws XmlException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(sr, CKDConfig.type, (XmlOptions)null);
        }
        
        public static CKDConfig parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(sr, CKDConfig.type, options);
        }
        
        public static CKDConfig parse(final Node node) throws XmlException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(node, CKDConfig.type, (XmlOptions)null);
        }
        
        public static CKDConfig parse(final Node node, final XmlOptions options) throws XmlException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(node, CKDConfig.type, options);
        }
        
        @Deprecated
        public static CKDConfig parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(xis, CKDConfig.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static CKDConfig parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (CKDConfig)XmlBeans.getContextTypeLoader().parse(xis, CKDConfig.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, CKDConfig.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, CKDConfig.type, options);
        }

        /**
         * Private constructor prevents instantiation.
         *
         * Factory is a utility class with static methods only.
         */
        private Factory() {
        }
    }
}
