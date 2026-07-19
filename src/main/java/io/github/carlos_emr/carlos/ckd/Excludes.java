/**
 * Chronic Kidney Disease (CKD) configuration system exclusion criteria interface.
 *
 * This XMLBeans-generated interface defines the data binding contract for managing
 * exclusion criteria within the CKD clinical decision support system. Exclusion
 * criteria allow healthcare providers to specify conditions or parameters that
 * should disqualify patients from certain CKD screening, monitoring, or
 * treatment protocols.
 *
 * The interface provides comprehensive XML parsing and manipulation capabilities
 * for arrays of exclusion criteria strings, enabling flexible configuration of
 * CKD clinical rules and protocols.
 *
 * @since 2010-01-01
 * @see Issues Clinical issues configuration for CKD management
 * @see SearchText Text search criteria configuration
 * @see Hx Patient history configuration
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
 * CKD exclusion criteria configuration interface.
 *
 * Defines the contract for managing exclusion criteria within the Chronic Kidney Disease
 * clinical decision support system. This interface allows configuration of conditions,
 * diagnoses, or other clinical parameters that should exclude patients from specific
 * CKD screening protocols, monitoring guidelines, or treatment recommendations.
 *
 * Common exclusion criteria might include:
 * - Terminal illness diagnoses
 * - Dialysis or kidney transplant status
 * - Pregnancy
 * - Age-related exclusions
 * - Specific medication contraindications
 *
 * The interface extends XMLBeans XmlObject to provide comprehensive XML serialization
 * and deserialization capabilities for integration with EMR configuration systems.
 *
 * @since 2010-01-01
 */
public interface Excludes extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Excludes.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("excludes44a0type");
    
    /**
     * Retrieves the complete array of exclusion criteria strings.
     *
     * @return String[] array containing all configured exclusion criteria,
     *         may be empty but never null
     */
    String[] getExcludeArray();
    
    /**
     * Retrieves a specific exclusion criterion by array index.
     *
     * @param index int zero-based index of the exclusion criterion to retrieve
     * @return String the exclusion criterion at the specified index
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    String getExcludeArray(final int index);
    
    /**
     * Retrieves the complete array of exclusion criteria as XMLBeans XmlString objects.
     *
     * This method provides access to the underlying XMLBeans representation,
     * preserving XML schema type information and validation capabilities.
     *
     * @return XmlString[] array of XMLBeans string objects containing exclusion criteria
     */
    XmlString[] xgetExcludeArray();
    
    /**
     * Retrieves a specific exclusion criterion as an XMLBeans XmlString object.
     *
     * @param index int zero-based index of the exclusion criterion
     * @return XmlString XMLBeans object preserving type and validation information
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    XmlString xgetExcludeArray(final int index);
    
    /**
     * Returns the total number of configured exclusion criteria.
     *
     * @return int count of exclusion criteria in the array (0 or greater)
     */
    int sizeOfExcludeArray();
    
    /**
     * Replaces the entire exclusion criteria array with new values.
     *
     * This method overwrites all existing exclusion criteria with the provided array.
     * Use with caution as it will remove any previously configured exclusions.
     *
     * @param excludes String[] new array of exclusion criteria to set
     */
    void setExcludeArray(final String[] excludes);
    
    /**
     * Updates a specific exclusion criterion at the given array index.
     *
     * @param index int zero-based index of the exclusion criterion to update
     * @param exclude String new exclusion criterion value
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void setExcludeArray(final int index, final String exclude);
    
    /**
     * Replaces the entire exclusion criteria array using XMLBeans XmlString objects.
     *
     * This method allows setting exclusion criteria while preserving XML schema
     * type information and validation capabilities.
     *
     * @param excludes XmlString[] array of XMLBeans string objects
     */
    void xsetExcludeArray(final XmlString[] excludes);
    
    /**
     * Updates a specific exclusion criterion using an XMLBeans XmlString object.
     *
     * @param index int zero-based index of the exclusion criterion to update
     * @param exclude XmlString XMLBeans object with type and validation information
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void xsetExcludeArray(final int index, final XmlString exclude);
    
    /**
     * Inserts a new exclusion criterion at the specified array position.
     *
     * All existing exclusion criteria at and after the specified index will be
     * shifted one position to the right. The array size will increase by one.
     *
     * @param index int zero-based insertion position
     * @param exclude String exclusion criterion to insert
     */
    void insertExclude(final int index, final String exclude);
    
    /**
     * Appends a new exclusion criterion to the end of the array.
     *
     * This is the most common method for adding new exclusion criteria to the
     * CKD configuration. The new criterion will be added at the end of the
     * existing array.
     *
     * @param exclude String exclusion criterion to add
     */
    void addExclude(final String exclude);
    
    /**
     * Inserts a new empty XMLBeans XmlString exclusion criterion at the specified position.
     *
     * Creates and inserts a new XMLBeans XmlString object that can be populated
     * with exclusion criterion data while preserving XML schema validation.
     *
     * @param index int zero-based insertion position
     * @return XmlString newly created XMLBeans object for population
     */
    XmlString insertNewExclude(final int index);
    
    /**
     * Appends a new empty XMLBeans XmlString exclusion criterion to the array.
     *
     * Creates and adds a new XMLBeans XmlString object at the end of the array
     * that can be populated with exclusion criterion data.
     *
     * @return XmlString newly created XMLBeans object for population
     */
    XmlString addNewExclude();
    
    /**
     * Removes an exclusion criterion at the specified array index.
     *
     * All exclusion criteria after the removed element will be shifted one
     * position to the left. The array size will decrease by one.
     *
     * @param index int zero-based index of the exclusion criterion to remove
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void removeExclude(final int index);
    
    public static final class Factory
    {
        public static Excludes newInstance() {
            return (Excludes)XmlBeans.getContextTypeLoader().newInstance(Excludes.type, (XmlOptions)null);
        }
        
        public static Excludes newInstance(final XmlOptions options) {
            return (Excludes)XmlBeans.getContextTypeLoader().newInstance(Excludes.type, options);
        }
        
        public static Excludes parse(final String xmlAsString) throws XmlException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(xmlAsString, Excludes.type, (XmlOptions)null);
        }
        
        public static Excludes parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(xmlAsString, Excludes.type, options);
        }
        
        public static Excludes parse(final File file) throws XmlException, IOException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(file, Excludes.type, (XmlOptions)null);
        }
        
        public static Excludes parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(file, Excludes.type, options);
        }
        
        public static Excludes parse(final URL u) throws XmlException, IOException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(u, Excludes.type, (XmlOptions)null);
        }
        
        public static Excludes parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(u, Excludes.type, options);
        }
        
        public static Excludes parse(final InputStream is) throws XmlException, IOException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(is, Excludes.type, (XmlOptions)null);
        }
        
        public static Excludes parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(is, Excludes.type, options);
        }
        
        public static Excludes parse(final Reader r) throws XmlException, IOException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(r, Excludes.type, (XmlOptions)null);
        }
        
        public static Excludes parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(r, Excludes.type, options);
        }
        
        public static Excludes parse(final XMLStreamReader sr) throws XmlException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(sr, Excludes.type, (XmlOptions)null);
        }
        
        public static Excludes parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(sr, Excludes.type, options);
        }
        
        public static Excludes parse(final Node node) throws XmlException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(node, Excludes.type, (XmlOptions)null);
        }
        
        public static Excludes parse(final Node node, final XmlOptions options) throws XmlException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(node, Excludes.type, options);
        }
        
        @Deprecated
        public static Excludes parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(xis, Excludes.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static Excludes parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (Excludes)XmlBeans.getContextTypeLoader().parse(xis, Excludes.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Excludes.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Excludes.type, options);
        }
        
        private Factory() {
        }
    }
}
