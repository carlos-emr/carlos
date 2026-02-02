/**
 * Chronic Kidney Disease (CKD) configuration document interface.
 *
 * This XMLBeans-generated interface serves as the root document wrapper for the
 * complete CKD clinical decision support system configuration. The CkdConfigDocument
 * provides the top-level container for all CKD-related configuration elements,
 * including clinical rules, screening criteria, monitoring protocols, and treatment
 * guidelines that guide automated CKD management within the EMR system.
 *
 * As the main document interface, CkdConfigDocument enables:
 * - Loading and saving complete CKD configuration from XML files
 * - Validating CKD configuration against XML schema definitions
 * - Providing structured access to nested CKD configuration components
 * - Supporting version control and configuration management workflows
 *
 * The document structure supports comprehensive CKD management including:
 * - Patient identification and risk stratification
 * - Automated screening and monitoring schedules
 * - Clinical decision support rules and alerts
 * - Treatment protocol recommendations
 * - Quality measure tracking and reporting
 *
 * This interface is typically used by EMR administrators and clinical informaticists
 * to configure and maintain CKD clinical decision support rules that ensure
 * evidence-based kidney disease management across the healthcare organization.
 *
 * @since 2010-01-01
 * @see CKDConfig Main CKD configuration object contained within this document
 * @see Issues Clinical issues configuration for CKD management
 * @see Excludes Exclusion criteria configuration
 * @see SearchText Text search criteria configuration
 * @see Hx Patient history configuration
 */
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
 * CKD configuration document root interface.
 *
 * Defines the contract for the root document wrapper of the Chronic Kidney Disease
 * clinical decision support system configuration. This interface provides access
 * to the main CKDConfig object that contains all CKD management rules, criteria,
 * and protocols.
 *
 * The document interface supports:
 * - XML serialization/deserialization of complete CKD configuration
 * - Schema validation of CKD configuration data
 * - Version management of CKD clinical rules
 * - Integration with EMR configuration management systems
 *
 * This document structure enables healthcare organizations to maintain centralized,
 * standardized CKD clinical decision support configurations that can be deployed
 * across multiple EMR instances while ensuring consistent, evidence-based kidney
 * disease management practices.
 *
 * The interface extends XMLBeans XmlObject to provide comprehensive XML document
 * processing capabilities for enterprise configuration management workflows.
 *
 * @since 2010-01-01
 */
public interface CkdConfigDocument extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(CkdConfigDocument.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("ckdconfiga2b6doctype");
    
    /**
     * Retrieves the main CKD configuration object from this document.
     *
     * The CKDConfig object contains all the detailed configuration elements
     * for the Chronic Kidney Disease clinical decision support system,
     * including clinical rules, screening criteria, monitoring protocols,
     * and treatment guidelines.
     *
     * @return CKDConfig the main configuration object containing all CKD
     *         management rules and protocols, may be null if not configured
     */
    CKDConfig getCkdConfig();
    
    /**
     * Sets the main CKD configuration object for this document.
     *
     * This method replaces the entire CKD configuration with the provided
     * CKDConfig object. Use with caution as this will overwrite all existing
     * CKD clinical decision support rules and protocols.
     *
     * @param ckdConfig CKDConfig main configuration object containing CKD
     *                  management rules, screening criteria, and protocols
     */
    void setCkdConfig(final CKDConfig ckdConfig);
    
    /**
     * Creates and adds a new CKD configuration object to this document.
     *
     * This method creates a new CKDConfig object that can be populated with
     * CKD clinical decision support rules, screening criteria, monitoring
     * protocols, and treatment guidelines. The newly created CKDConfig will
     * be automatically associated with this document.
     *
     * @return CKDConfig newly created configuration object ready for population
     *         with CKD management rules and protocols
     */
    CKDConfig addNewCkdConfig();
    
    public static final class Factory
    {
        public static CkdConfigDocument newInstance() {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().newInstance(CkdConfigDocument.type, (XmlOptions)null);
        }
        
        public static CkdConfigDocument newInstance(final XmlOptions options) {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().newInstance(CkdConfigDocument.type, options);
        }
        
        public static CkdConfigDocument parse(final String xmlAsString) throws XmlException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(xmlAsString, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        public static CkdConfigDocument parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(xmlAsString, CkdConfigDocument.type, options);
        }
        
        public static CkdConfigDocument parse(final File file) throws XmlException, IOException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(file, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        public static CkdConfigDocument parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(file, CkdConfigDocument.type, options);
        }
        
        public static CkdConfigDocument parse(final URL u) throws XmlException, IOException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(u, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        public static CkdConfigDocument parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(u, CkdConfigDocument.type, options);
        }
        
        public static CkdConfigDocument parse(final InputStream is) throws XmlException, IOException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(is, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        public static CkdConfigDocument parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(is, CkdConfigDocument.type, options);
        }
        
        public static CkdConfigDocument parse(final Reader r) throws XmlException, IOException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(r, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        public static CkdConfigDocument parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(r, CkdConfigDocument.type, options);
        }
        
        public static CkdConfigDocument parse(final XMLStreamReader sr) throws XmlException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(sr, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        public static CkdConfigDocument parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(sr, CkdConfigDocument.type, options);
        }
        
        public static CkdConfigDocument parse(final Node node) throws XmlException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(node, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        public static CkdConfigDocument parse(final Node node, final XmlOptions options) throws XmlException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(node, CkdConfigDocument.type, options);
        }
        
        @Deprecated
        public static CkdConfigDocument parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(xis, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static CkdConfigDocument parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (CkdConfigDocument)XmlBeans.getContextTypeLoader().parse(xis, CkdConfigDocument.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, CkdConfigDocument.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, CkdConfigDocument.type, options);
        }
        
        private Factory() {
        }
    }
}
