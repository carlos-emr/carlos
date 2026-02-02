/**
 * Chronic Kidney Disease (CKD) text search configuration interface.
 *
 * This XMLBeans-generated interface defines the data binding contract for managing
 * text search criteria within the CKD clinical decision support system. The SearchText
 * interface allows healthcare providers to configure arrays of search terms, phrases,
 * or patterns that should be used to identify relevant CKD-related information in
 * clinical documentation, lab reports, and other text-based medical records.
 *
 * Text search capabilities are essential for comprehensive CKD patient assessment,
 * enabling automated scanning of:
 * - Clinical notes for CKD-related observations
 * - Laboratory reports for kidney function markers
 * - Pathology reports for renal biopsy findings
 * - Medication lists for nephrotoxic agents
 * - Radiology reports for kidney imaging findings
 * - Consultation notes for specialist recommendations
 *
 * The search text configuration supports flexible pattern matching to identify
 * both structured terminology and free-text descriptions related to kidney disease
 * management and monitoring.
 *
 * @since 2010-01-01
 * @see Issues Clinical issues configuration for structured CKD tracking
 * @see Excludes Exclusion criteria configuration
 * @see Hx Patient history configuration containing search text
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
 * CKD text search configuration interface.
 *
 * Defines the contract for managing text search criteria within the Chronic Kidney
 * Disease clinical decision support system. This interface provides comprehensive
 * array manipulation capabilities for search terms and patterns used to identify
 * CKD-relevant information in clinical documentation.
 *
 * The SearchText configuration enables:
 * - Automated scanning of clinical notes for CKD terminology
 * - Identification of kidney function-related laboratory values
 * - Detection of nephrotoxic medications in medication lists
 * - Recognition of CKD complications in clinical documentation
 * - Discovery of family history of kidney disease
 *
 * Search terms can include:
 * - Medical terminology (creatinine, GFR, proteinuria, etc.)
 * - Drug names (ACE inhibitors, ARBs, diuretics, etc.)
 * - Procedure codes (dialysis, transplant, biopsy, etc.)
 * - Diagnostic patterns (chronic kidney disease, renal failure, etc.)
 *
 * The interface extends XMLBeans XmlObject to provide comprehensive XML serialization
 * and deserialization capabilities for integration with EMR configuration systems.
 *
 * @since 2010-01-01
 */
public interface SearchText extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(SearchText.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("searchtext4124type");
    
    /**
     * Retrieves the complete array of text search criteria.
     *
     * @return String[] array containing all configured search terms and patterns
     *         for CKD-related text identification, may be empty but never null
     */
    String[] getTextArray();
    
    /**
     * Retrieves a specific text search criterion by array index.
     *
     * @param index int zero-based index of the search text to retrieve
     * @return String the search text criterion at the specified index
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    String getTextArray(final int index);
    
    /**
     * Retrieves the complete array of search text as XMLBeans XmlString objects.
     *
     * This method provides access to the underlying XMLBeans representation,
     * preserving XML schema type information and validation capabilities for
     * text search criteria.
     *
     * @return XmlString[] array of XMLBeans string objects containing search terms
     */
    XmlString[] xgetTextArray();
    
    /**
     * Retrieves a specific search text criterion as an XMLBeans XmlString object.
     *
     * @param index int zero-based index of the search text
     * @return XmlString XMLBeans object preserving type and validation information
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    XmlString xgetTextArray(final int index);
    
    /**
     * Returns the total number of configured text search criteria.
     *
     * @return int count of search text criteria in the array (0 or greater)
     */
    int sizeOfTextArray();
    
    /**
     * Replaces the entire text search criteria array with new values.
     *
     * This method overwrites all existing search text configurations with
     * the provided array. Use with caution as it will remove any previously
     * configured search terms for CKD identification.
     *
     * @param searchTerms String[] new array of text search criteria to set
     */
    void setTextArray(final String[] searchTerms);
    
    /**
     * Updates a specific text search criterion at the given array index.
     *
     * @param index int zero-based index of the search text to update
     * @param searchTerm String new text search criterion value
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void setTextArray(final int index, final String searchTerm);
    
    /**
     * Replaces the entire text search array using XMLBeans XmlString objects.
     *
     * This method allows setting search text criteria while preserving XML
     * schema type information and validation capabilities.
     *
     * @param searchTerms XmlString[] array of XMLBeans string objects
     */
    void xsetTextArray(final XmlString[] searchTerms);
    
    /**
     * Updates a specific search text criterion using an XMLBeans XmlString object.
     *
     * @param index int zero-based index of the search text to update
     * @param searchTerm XmlString XMLBeans object with type and validation information
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void xsetTextArray(final int index, final XmlString searchTerm);
    
    /**
     * Inserts a new text search criterion at the specified array position.
     *
     * All existing search text criteria at and after the specified index will be
     * shifted one position to the right. The array size will increase by one.
     *
     * @param index int zero-based insertion position
     * @param searchTerm String text search criterion to insert
     */
    void insertText(final int index, final String searchTerm);
    
    /**
     * Appends a new text search criterion to the end of the array.
     *
     * This is the most common method for adding new search terms to the
     * CKD text search configuration. The new criterion will be added at
     * the end of the existing array.
     *
     * @param searchTerm String text search criterion to add
     */
    void addText(final String searchTerm);
    
    /**
     * Inserts a new empty XMLBeans XmlString search criterion at the specified position.
     *
     * Creates and inserts a new XMLBeans XmlString object that can be populated
     * with search text data while preserving XML schema validation.
     *
     * @param index int zero-based insertion position
     * @return XmlString newly created XMLBeans object for population
     */
    XmlString insertNewText(final int index);
    
    /**
     * Appends a new empty XMLBeans XmlString search criterion to the array.
     *
     * Creates and adds a new XMLBeans XmlString object at the end of the array
     * that can be populated with search text data.
     *
     * @return XmlString newly created XMLBeans object for population
     */
    XmlString addNewText();
    
    /**
     * Removes a text search criterion at the specified array index.
     *
     * All search text criteria after the removed element will be shifted one
     * position to the left. The array size will decrease by one.
     *
     * @param index int zero-based index of the search text to remove
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void removeText(final int index);
    
    public static final class Factory
    {
        public static SearchText newInstance() {
            return (SearchText)XmlBeans.getContextTypeLoader().newInstance(SearchText.type, (XmlOptions)null);
        }
        
        public static SearchText newInstance(final XmlOptions options) {
            return (SearchText)XmlBeans.getContextTypeLoader().newInstance(SearchText.type, options);
        }
        
        public static SearchText parse(final String xmlAsString) throws XmlException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(xmlAsString, SearchText.type, (XmlOptions)null);
        }
        
        public static SearchText parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(xmlAsString, SearchText.type, options);
        }
        
        public static SearchText parse(final File file) throws XmlException, IOException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(file, SearchText.type, (XmlOptions)null);
        }
        
        public static SearchText parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(file, SearchText.type, options);
        }
        
        public static SearchText parse(final URL u) throws XmlException, IOException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(u, SearchText.type, (XmlOptions)null);
        }
        
        public static SearchText parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(u, SearchText.type, options);
        }
        
        public static SearchText parse(final InputStream is) throws XmlException, IOException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(is, SearchText.type, (XmlOptions)null);
        }
        
        public static SearchText parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(is, SearchText.type, options);
        }
        
        public static SearchText parse(final Reader r) throws XmlException, IOException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(r, SearchText.type, (XmlOptions)null);
        }
        
        public static SearchText parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(r, SearchText.type, options);
        }
        
        public static SearchText parse(final XMLStreamReader sr) throws XmlException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(sr, SearchText.type, (XmlOptions)null);
        }
        
        public static SearchText parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(sr, SearchText.type, options);
        }
        
        public static SearchText parse(final Node node) throws XmlException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(node, SearchText.type, (XmlOptions)null);
        }
        
        public static SearchText parse(final Node node, final XmlOptions options) throws XmlException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(node, SearchText.type, options);
        }
        
        @Deprecated
        public static SearchText parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(xis, SearchText.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static SearchText parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (SearchText)XmlBeans.getContextTypeLoader().parse(xis, SearchText.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, SearchText.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, SearchText.type, options);
        }
        
        private Factory() {
        }
    }
}
