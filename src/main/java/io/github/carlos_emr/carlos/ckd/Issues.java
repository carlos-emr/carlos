/**
 * Chronic Kidney Disease (CKD) clinical issues configuration interface.
 *
 * This XMLBeans-generated interface defines the data binding contract for managing
 * clinical issues within the CKD clinical decision support system. Clinical issues
 * represent structured medical conditions, diagnoses, problems, or clinical markers
 * that are relevant to kidney disease screening, monitoring, and treatment protocols.
 *
 * The Issues interface allows healthcare providers to configure arrays of clinical
 * issue identifiers that the EMR system should track for CKD patients. These issues
 * help identify patients who require specific CKD interventions, monitoring schedules,
 * or treatment modifications based on their clinical profile.
 *
 * Common clinical issues for CKD management include:
 * - Diabetes mellitus (primary CKD risk factor)
 * - Hypertension (cause and consequence of CKD)
 * - Cardiovascular disease (common CKD complication)
 * - Proteinuria or albuminuria (CKD progression marker)
 * - Electrolyte imbalances (CKD management target)
 *
 * @since 2010-01-01
 * @see Excludes Exclusion criteria that disqualify patients from CKD protocols
 * @see SearchText Text search criteria for clinical documentation
 * @see Hx Patient history configuration containing issues
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
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * CKD clinical issues configuration interface.
 *
 * Defines the contract for managing clinical issues within the Chronic Kidney Disease
 * clinical decision support system. This interface provides comprehensive array
 * manipulation capabilities for clinical issue identifiers that are relevant to
 * CKD patient care.
 *
 * Clinical issues in this context represent structured medical conditions,
 * diagnoses, or clinical problems that:
 * - Indicate increased risk for kidney disease development
 * - Suggest need for CKD screening or monitoring
 * - Influence CKD treatment decisions or protocols
 * - Represent complications or comorbidities of existing CKD
 *
 * The interface extends XMLBeans XmlObject to provide comprehensive XML serialization
 * and deserialization capabilities for integration with EMR configuration systems.
 *
 * @since 2010-01-01
 */
public interface Issues extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Issues.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("issues297ftype");
    
    /**
     * Retrieves the complete array of clinical issue identifiers.
     *
     * @return String[] array containing all configured clinical issue identifiers
     *         relevant to CKD management, may be empty but never null
     */
    String[] getIssueArray();
    
    /**
     * Retrieves a specific clinical issue identifier by array index.
     *
     * @param index int zero-based index of the clinical issue to retrieve
     * @return String the clinical issue identifier at the specified index
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    String getIssueArray(final int index);
    
    /**
     * Retrieves the complete array of clinical issues as XMLBeans XmlString objects.
     *
     * This method provides access to the underlying XMLBeans representation,
     * preserving XML schema type information and validation capabilities for
     * clinical issue identifiers.
     *
     * @return XmlString[] array of XMLBeans string objects containing clinical issues
     */
    XmlString[] xgetIssueArray();
    
    /**
     * Retrieves a specific clinical issue as an XMLBeans XmlString object.
     *
     * @param index int zero-based index of the clinical issue
     * @return XmlString XMLBeans object preserving type and validation information
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    XmlString xgetIssueArray(final int index);
    
    /**
     * Returns the total number of configured clinical issues.
     *
     * @return int count of clinical issue identifiers in the array (0 or greater)
     */
    int sizeOfIssueArray();
    
    /**
     * Replaces the entire clinical issues array with new values.
     *
     * This method overwrites all existing clinical issue configurations with
     * the provided array. Use with caution as it will remove any previously
     * configured clinical issues for CKD management.
     *
     * @param issues String[] new array of clinical issue identifiers to set
     */
    void setIssueArray(final String[] issues);
    
    /**
     * Updates a specific clinical issue identifier at the given array index.
     *
     * @param index int zero-based index of the clinical issue to update
     * @param issue String new clinical issue identifier value
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void setIssueArray(final int index, final String issue);
    
    /**
     * Replaces the entire clinical issues array using XMLBeans XmlString objects.
     *
     * This method allows setting clinical issue identifiers while preserving XML
     * schema type information and validation capabilities.
     *
     * @param issues XmlString[] array of XMLBeans string objects
     */
    void xsetIssueArray(final XmlString[] issues);
    
    /**
     * Updates a specific clinical issue using an XMLBeans XmlString object.
     *
     * @param index int zero-based index of the clinical issue to update
     * @param issue XmlString XMLBeans object with type and validation information
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void xsetIssueArray(final int index, final XmlString issue);
    
    /**
     * Inserts a new clinical issue identifier at the specified array position.
     *
     * All existing clinical issues at and after the specified index will be
     * shifted one position to the right. The array size will increase by one.
     *
     * @param index int zero-based insertion position
     * @param issue String clinical issue identifier to insert
     */
    void insertIssue(final int index, final String issue);
    
    /**
     * Appends a new clinical issue identifier to the end of the array.
     *
     * This is the most common method for adding new clinical issues to the
     * CKD configuration. The new issue will be added at the end of the
     * existing array.
     *
     * @param issue String clinical issue identifier to add
     */
    void addIssue(final String issue);
    
    /**
     * Inserts a new empty XMLBeans XmlString clinical issue at the specified position.
     *
     * Creates and inserts a new XMLBeans XmlString object that can be populated
     * with clinical issue data while preserving XML schema validation.
     *
     * @param index int zero-based insertion position
     * @return XmlString newly created XMLBeans object for population
     */
    XmlString insertNewIssue(final int index);
    
    /**
     * Appends a new empty XMLBeans XmlString clinical issue to the array.
     *
     * Creates and adds a new XMLBeans XmlString object at the end of the array
     * that can be populated with clinical issue data.
     *
     * @return XmlString newly created XMLBeans object for population
     */
    XmlString addNewIssue();
    
    /**
     * Removes a clinical issue identifier at the specified array index.
     *
     * All clinical issues after the removed element will be shifted one
     * position to the left. The array size will decrease by one.
     *
     * @param index int zero-based index of the clinical issue to remove
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void removeIssue(final int index);
    
    public static final class Factory
    {
        public static Issues newInstance() {
            return (Issues)XmlBeans.getContextTypeLoader().newInstance(Issues.type, (XmlOptions)null);
        }
        
        public static Issues newInstance(final XmlOptions options) {
            return (Issues)XmlBeans.getContextTypeLoader().newInstance(Issues.type, options);
        }
        
        public static Issues parse(final String xmlAsString) throws XmlException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(xmlAsString, Issues.type, (XmlOptions)null);
        }
        
        public static Issues parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(xmlAsString, Issues.type, options);
        }
        
        public static Issues parse(final File file) throws XmlException, IOException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(file, Issues.type, (XmlOptions)null);
        }
        
        public static Issues parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(file, Issues.type, options);
        }
        
        public static Issues parse(final URL u) throws XmlException, IOException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(u, Issues.type, (XmlOptions)null);
        }
        
        public static Issues parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(u, Issues.type, options);
        }
        
        public static Issues parse(final InputStream is) throws XmlException, IOException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(is, Issues.type, (XmlOptions)null);
        }
        
        public static Issues parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(is, Issues.type, options);
        }
        
        public static Issues parse(final Reader r) throws XmlException, IOException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(r, Issues.type, (XmlOptions)null);
        }
        
        public static Issues parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(r, Issues.type, options);
        }
        
        public static Issues parse(final XMLStreamReader sr) throws XmlException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(sr, Issues.type, (XmlOptions)null);
        }
        
        public static Issues parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(sr, Issues.type, options);
        }
        
        public static Issues parse(final Node node) throws XmlException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(node, Issues.type, (XmlOptions)null);
        }
        
        public static Issues parse(final Node node, final XmlOptions options) throws XmlException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(node, Issues.type, options);
        }
        
        @Deprecated
        public static Issues parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(xis, Issues.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static Issues parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (Issues)XmlBeans.getContextTypeLoader().parse(xis, Issues.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Issues.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Issues.type, options);
        }
        
        private Factory() {
        }
    }
}
