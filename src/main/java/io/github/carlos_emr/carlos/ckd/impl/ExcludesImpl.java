package io.github.carlos_emr.carlos.ckd.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import java.util.List;
import org.apache.xmlbeans.SimpleValue;
import java.util.ArrayList;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.Excludes;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link Excludes} interface.
 *
 * This class provides the concrete implementation for managing exclusion criteria lists
 * within CKD (Chronic Kidney Disease) configuration documents. It handles the XML
 * serialization and deserialization of exclusion arrays, allowing for the management
 * of multiple exclusion criteria entries used in CKD patient filtering and care protocols.
 *
 * The implementation manages exclusion elements as an array of string values within the
 * "http://www.oscarmcmaster.org/ckd" XML namespace, providing thread-safe access
 * to individual exclusion entries and array operations including adding, removing,
 * and modifying exclusion criteria.
 *
 * Key functionality includes:
 * - Array-based exclusion criteria management with index-based access
 * - Dynamic addition and removal of exclusion entries
 * - Support for both string and XmlString representations
 * - Thread-safe operations for concurrent access
 * - Patient filtering logic integration
 *
 * @see Excludes
 * @since 2010-01-01
 */
public class ExcludesImpl extends XmlComplexContentImpl implements Excludes
{
    private static final long serialVersionUID = 1L;
    private static final QName EXCLUDE$0;
    
    /**
     * Constructs a new ExcludesImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this excludes element
     */
    public ExcludesImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves all exclusion criteria entries as a string array.
     *
     * @return String[] array containing all exclusion criteria entries, empty array if no exclusions are set
     */
    public String[] getExcludeArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(ExcludesImpl.EXCLUDE$0, targetList);
            final String[] result = new String[targetList.size()];
            for (int i = 0, len = targetList.size(); i < len; ++i) {
                result[i] = ((SimpleValue)targetList.get(i)).getStringValue();
            }
            return result;
        }
    }
    
    /**
     * Retrieves a specific exclusion criteria entry by index.
     *
     * @param i int the index of the exclusion criteria entry to retrieve
     * @return String the exclusion criteria entry at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public String getExcludeArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ExcludesImpl.EXCLUDE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target.getStringValue();
        }
    }
    
    /**
     * Retrieves all exclusion criteria entries as an XmlString array.
     *
     * @return XmlString[] array containing all exclusion criteria entries as XML string objects
     */
    public XmlString[] xgetExcludeArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(ExcludesImpl.EXCLUDE$0, targetList);
            final XmlString[] result = new XmlString[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }
    
    /**
     * Retrieves a specific exclusion criteria entry by index as an XmlString object.
     *
     * @param i int the index of the exclusion criteria entry to retrieve
     * @return XmlString the exclusion criteria entry at the specified index as an XML string object
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public XmlString xgetExcludeArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(ExcludesImpl.EXCLUDE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }
    
    /**
     * Returns the number of exclusion criteria entries in the array.
     *
     * @return int the total number of exclusion criteria entries
     */
    public int sizeOfExcludeArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(ExcludesImpl.EXCLUDE$0);
        }
    }
    
    /**
     * Sets the entire exclusion criteria array, replacing all existing entries.
     *
     * @param excludeArray String[] array of exclusion criteria entries to set
     */
    public void setExcludeArray(final String[] excludeArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper(excludeArray, ExcludesImpl.EXCLUDE$0);
        }
    }
    
    /**
     * Sets a specific exclusion criteria entry at the specified index.
     *
     * @param i int the index of the exclusion criteria entry to set
     * @param exclude String the exclusion criteria entry to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setExcludeArray(final int i, final String exclude) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ExcludesImpl.EXCLUDE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.setStringValue(exclude);
        }
    }
    
    /**
     * Sets the entire exclusion criteria array using XmlString objects, replacing all existing entries.
     *
     * @param excludeArray XmlString[] array of exclusion criteria entries as XML string objects to set
     */
    public void xsetExcludeArray(final XmlString[] excludeArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])excludeArray, ExcludesImpl.EXCLUDE$0);
        }
    }
    
    /**
     * Sets a specific exclusion criteria entry at the specified index using an XmlString object.
     *
     * @param i int the index of the exclusion criteria entry to set
     * @param exclude XmlString the exclusion criteria entry as an XML string object to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void xsetExcludeArray(final int i, final XmlString exclude) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(ExcludesImpl.EXCLUDE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)exclude);
        }
    }
    
    /**
     * Inserts a new exclusion criteria entry at the specified index.
     *
     * @param i int the index at which to insert the new exclusion criteria entry
     * @param exclude String the exclusion criteria entry to insert
     */
    public void insertExclude(final int i, final String exclude) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final SimpleValue target = (SimpleValue)this.get_store().insert_element_user(ExcludesImpl.EXCLUDE$0, i);
            target.setStringValue(exclude);
        }
    }
    
    /**
     * Adds a new exclusion criteria entry to the end of the array.
     *
     * @param exclude String the exclusion criteria entry to add
     */
    public void addExclude(final String exclude) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().add_element_user(ExcludesImpl.EXCLUDE$0);
            target.setStringValue(exclude);
        }
    }
    
    /**
     * Inserts a new empty exclusion criteria entry at the specified index and returns it as an XmlString.
     *
     * @param i int the index at which to insert the new exclusion criteria entry
     * @return XmlString the newly created exclusion criteria entry as an XML string object
     */
    public XmlString insertNewExclude(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().insert_element_user(ExcludesImpl.EXCLUDE$0, i);
            return target;
        }
    }
    
    /**
     * Adds a new empty exclusion criteria entry to the end of the array and returns it as an XmlString.
     *
     * @return XmlString the newly created exclusion criteria entry as an XML string object
     */
    public XmlString addNewExclude() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().add_element_user(ExcludesImpl.EXCLUDE$0);
            return target;
        }
    }
    
    /**
     * Removes the exclusion criteria entry at the specified index.
     *
     * @param i int the index of the exclusion criteria entry to remove
     */
    public void removeExclude(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(ExcludesImpl.EXCLUDE$0, i);
        }
    }
    
    static {
        // Initialize QName constant for XML element identification within CKD namespace
        EXCLUDE$0 = new QName("http://www.oscarmcmaster.org/ckd", "exclude");
    }
}
