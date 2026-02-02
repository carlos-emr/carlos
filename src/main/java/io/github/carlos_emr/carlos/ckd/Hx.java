/**
 * Chronic Kidney Disease (CKD) patient history configuration interface.
 *
 * This XMLBeans-generated interface provides the data binding contract for managing
 * patient history-related configuration within the CKD clinical decision support
 * system. The Hx (History) interface allows healthcare providers to configure
 * clinical issues tracking and search text criteria for identifying relevant
 * patient history elements that influence CKD screening, monitoring, and treatment
 * protocols.
 *
 * Patient history elements are critical in CKD management as they help identify:
 * - Risk factors for kidney disease progression
 * - Comorbid conditions affecting treatment plans
 * - Previous interventions and their outcomes
 * - Family history of kidney disease
 * - Medication history relevant to kidney function
 *
 * @since 2010-01-01
 * @see Issues Clinical issues management configuration
 * @see SearchText Text search criteria for history identification
 * @see Excludes Exclusion criteria configuration
 * @see CkdConfigDocument Main CKD configuration document
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
 * CKD patient history configuration interface.
 *
 * Defines the contract for managing patient history configuration within the
 * Chronic Kidney Disease clinical decision support system. This interface
 * provides access to clinical issues tracking and search text configuration
 * that enables the EMR system to identify and categorize relevant patient
 * history elements for CKD management.
 *
 * The history configuration supports two primary components:
 * - Issues: Structured clinical issues that should be tracked for CKD patients
 * - Search Text: Free-text search criteria for identifying relevant history
 *   elements in clinical notes, lab reports, and other documentation
 *
 * This configuration is essential for comprehensive CKD patient assessment,
 * enabling automated identification of risk factors, comorbidities, and
 * historical events that influence current CKD management decisions.
 *
 * @since 2010-01-01
 */
public interface Hx extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Hx.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("hxed89type");
    
    /**
     * Retrieves the clinical issues configuration for CKD patient history tracking.
     *
     * The Issues object contains structured clinical issue definitions that should
     * be monitored and tracked for patients in the CKD management program.
     * These issues typically include diagnoses, conditions, and clinical markers
     * relevant to kidney disease progression and treatment.
     *
     * @return Issues configuration object for clinical issues tracking,
     *         may be null if not configured
     */
    Issues getIssues();
    
    /**
     * Sets the clinical issues configuration for CKD patient history tracking.
     *
     * @param issues Issues configuration object containing clinical issue
     *               definitions for CKD patient monitoring
     */
    void setIssues(final Issues issues);
    
    /**
     * Creates and adds a new clinical issues configuration object.
     *
     * This method creates a new Issues configuration that can be populated
     * with clinical issue definitions for CKD patient history tracking.
     * The newly created Issues object will be automatically associated
     * with this Hx configuration.
     *
     * @return Issues newly created clinical issues configuration object
     */
    Issues addNewIssues();
    
    /**
     * Retrieves the search text configuration for CKD patient history identification.
     *
     * The SearchText object contains free-text search criteria used to identify
     * relevant patient history elements in clinical documentation. This enables
     * automated scanning of clinical notes, lab reports, and other text-based
     * medical records for CKD-relevant information.
     *
     * @return SearchText configuration object for history text search,
     *         may be null if not configured
     */
    SearchText getSearchtext();
    
    /**
     * Sets the search text configuration for CKD patient history identification.
     *
     * @param searchText SearchText configuration object containing text search
     *                   criteria for identifying relevant patient history elements
     */
    void setSearchtext(final SearchText searchText);
    
    /**
     * Creates and adds a new search text configuration object.
     *
     * This method creates a new SearchText configuration that can be populated
     * with text search criteria for identifying relevant patient history elements
     * in clinical documentation. The newly created SearchText object will be
     * automatically associated with this Hx configuration.
     *
     * @return SearchText newly created search text configuration object
     */
    SearchText addNewSearchtext();
    
    public static final class Factory
    {
        public static Hx newInstance() {
            return (Hx)XmlBeans.getContextTypeLoader().newInstance(Hx.type, (XmlOptions)null);
        }
        
        public static Hx newInstance(final XmlOptions options) {
            return (Hx)XmlBeans.getContextTypeLoader().newInstance(Hx.type, options);
        }
        
        public static Hx parse(final String xmlAsString) throws XmlException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(xmlAsString, Hx.type, (XmlOptions)null);
        }
        
        public static Hx parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(xmlAsString, Hx.type, options);
        }
        
        public static Hx parse(final File file) throws XmlException, IOException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(file, Hx.type, (XmlOptions)null);
        }
        
        public static Hx parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(file, Hx.type, options);
        }
        
        public static Hx parse(final URL u) throws XmlException, IOException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(u, Hx.type, (XmlOptions)null);
        }
        
        public static Hx parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(u, Hx.type, options);
        }
        
        public static Hx parse(final InputStream is) throws XmlException, IOException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(is, Hx.type, (XmlOptions)null);
        }
        
        public static Hx parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(is, Hx.type, options);
        }
        
        public static Hx parse(final Reader r) throws XmlException, IOException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(r, Hx.type, (XmlOptions)null);
        }
        
        public static Hx parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(r, Hx.type, options);
        }
        
        public static Hx parse(final XMLStreamReader sr) throws XmlException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(sr, Hx.type, (XmlOptions)null);
        }
        
        public static Hx parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(sr, Hx.type, options);
        }
        
        public static Hx parse(final Node node) throws XmlException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(node, Hx.type, (XmlOptions)null);
        }
        
        public static Hx parse(final Node node, final XmlOptions options) throws XmlException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(node, Hx.type, options);
        }
        
        @Deprecated
        public static Hx parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(xis, Hx.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static Hx parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (Hx)XmlBeans.getContextTypeLoader().parse(xis, Hx.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Hx.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Hx.type, options);
        }
        
        private Factory() {
        }
    }
}
