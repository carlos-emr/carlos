package io.github.carlos_emr.carlos.ckd.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import java.util.List;
import org.apache.xmlbeans.SimpleValue;
import java.util.ArrayList;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.SearchText;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link SearchText} interface.
 *
 * This class provides the concrete implementation for managing search text pattern lists
 * within CKD (Chronic Kidney Disease) medical history configuration documents. It handles
 * the XML serialization and deserialization of search text arrays, allowing for the management
 * of multiple text patterns used for automated identification of relevant medical history
 * elements in patient records and clinical notes.
 *
 * The implementation manages search text elements as an array of string values within the
 * "http://www.oscarmcmaster.org/ckd" XML namespace, providing thread-safe access
 * to individual text pattern entries and array operations including adding, removing,
 * and modifying search text patterns.
 *
 * Key functionality includes:
 * - Array-based search text pattern management with index-based access
 * - Dynamic addition and removal of text pattern entries
 * - Support for both string and XmlString representations
 * - Thread-safe operations for concurrent access
 * - Integration with automated medical history identification workflows
 * - Pattern matching for CKD-relevant clinical documentation
 *
 * @see SearchText
 * @since 2010-01-01
 */
public class SearchTextImpl extends XmlComplexContentImpl implements SearchText
{
    private static final long serialVersionUID = 1L;
    private static final QName TEXT$0;
    
    /**
     * Constructs a new SearchTextImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this search text element
     */
    public SearchTextImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves all search text pattern entries as a string array.
     *
     * @return String[] array containing all search text patterns, empty array if no text patterns are set
     */
    public String[] getTextArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(SearchTextImpl.TEXT$0, targetList);
            final String[] result = new String[targetList.size()];
            for (int i = 0, len = targetList.size(); i < len; ++i) {
                result[i] = ((SimpleValue)targetList.get(i)).getStringValue();
            }
            return result;
        }
    }
    
    /**
     * Retrieves a specific search text pattern entry by index.
     *
     * @param i int the index of the search text pattern entry to retrieve
     * @return String the search text pattern entry at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public String getTextArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SearchTextImpl.TEXT$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target.getStringValue();
        }
    }
    
    /**
     * Retrieves all search text pattern entries as an XmlString array.
     *
     * @return XmlString[] array containing all search text patterns as XML string objects
     */
    public XmlString[] xgetTextArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(SearchTextImpl.TEXT$0, targetList);
            final XmlString[] result = new XmlString[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }
    
    /**
     * Retrieves a specific search text pattern entry by index as an XmlString object.
     *
     * @param i int the index of the search text pattern entry to retrieve
     * @return XmlString the search text pattern entry at the specified index as an XML string object
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public XmlString xgetTextArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SearchTextImpl.TEXT$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }
    
    /**
     * Returns the number of search text pattern entries in the array.
     *
     * @return int the total number of search text pattern entries
     */
    public int sizeOfTextArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(SearchTextImpl.TEXT$0);
        }
    }
    
    /**
     * Sets the entire search text pattern array, replacing all existing entries.
     *
     * @param textArray String[] array of search text pattern entries to set
     */
    public void setTextArray(final String[] textArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper(textArray, SearchTextImpl.TEXT$0);
        }
    }
    
    /**
     * Sets a specific search text pattern entry at the specified index.
     *
     * @param i int the index of the search text pattern entry to set
     * @param text String the search text pattern entry to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setTextArray(final int i, final String text) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SearchTextImpl.TEXT$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.setStringValue(text);
        }
    }
    
    /**
     * Sets the entire search text pattern array using XmlString objects, replacing all existing entries.
     *
     * @param textArray XmlString[] array of search text pattern entries as XML string objects to set
     */
    public void xsetTextArray(final XmlString[] textArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])textArray, SearchTextImpl.TEXT$0);
        }
    }
    
    /**
     * Sets a specific search text pattern entry at the specified index using an XmlString object.
     *
     * @param i int the index of the search text pattern entry to set
     * @param text XmlString the search text pattern entry as an XML string object to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void xsetTextArray(final int i, final XmlString text) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SearchTextImpl.TEXT$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)text);
        }
    }
    
    /**
     * Inserts a new search text pattern entry at the specified index.
     *
     * @param i int the index at which to insert the new search text pattern entry
     * @param text String the search text pattern entry to insert
     */
    public void insertText(final int i, final String text) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final SimpleValue target = (SimpleValue)this.get_store().insert_element_user(SearchTextImpl.TEXT$0, i);
            target.setStringValue(text);
        }
    }
    
    /**
     * Adds a new search text pattern entry to the end of the array.
     *
     * @param text String the search text pattern entry to add
     */
    public void addText(final String text) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().add_element_user(SearchTextImpl.TEXT$0);
            target.setStringValue(text);
        }
    }
    
    /**
     * Inserts a new empty search text pattern entry at the specified index and returns it as an XmlString.
     *
     * @param i int the index at which to insert the new search text pattern entry
     * @return XmlString the newly created search text pattern entry as an XML string object
     */
    public XmlString insertNewText(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().insert_element_user(SearchTextImpl.TEXT$0, i);
            return target;
        }
    }
    
    /**
     * Adds a new empty search text pattern entry to the end of the array and returns it as an XmlString.
     *
     * @return XmlString the newly created search text pattern entry as an XML string object
     */
    public XmlString addNewText() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().add_element_user(SearchTextImpl.TEXT$0);
            return target;
        }
    }
    
    /**
     * Removes the search text pattern entry at the specified index.
     *
     * @param i int the index of the search text pattern entry to remove
     */
    public void removeText(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(SearchTextImpl.TEXT$0, i);
        }
    }
    
    static {
        // Initialize QName constant for XML element identification within CKD namespace
        TEXT$0 = new QName("http://www.oscarmcmaster.org/ckd", "text");
    }
}
