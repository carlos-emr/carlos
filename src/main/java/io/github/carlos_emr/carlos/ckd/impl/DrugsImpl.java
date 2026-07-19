package io.github.carlos_emr.carlos.ckd.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import java.util.List;
import org.apache.xmlbeans.SimpleValue;
import java.util.ArrayList;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.Drugs;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link Drugs} interface.
 *
 * This class provides the concrete implementation for managing drug/medication lists
 * within CKD (Chronic Kidney Disease) configuration documents. It handles the XML
 * serialization and deserialization of drug arrays, allowing for the management
 * of multiple drug entries used in CKD patient care and medication tracking.
 *
 * The implementation manages drug elements as an array of string values within the
 * "http://www.oscarmcmaster.org/ckd" XML namespace, providing thread-safe access
 * to individual drug entries and array operations including adding, removing,
 * and modifying drug entries.
 *
 * Key functionality includes:
 * - Array-based drug management with index-based access
 * - Dynamic addition and removal of drug entries
 * - Support for both string and XmlString representations
 * - Thread-safe operations for concurrent access
 *
 * @see Drugs
 * @since 2010-01-01
 */
public class DrugsImpl extends XmlComplexContentImpl implements Drugs
{
    private static final long serialVersionUID = 1L;
    private static final QName DRUG$0;
    
    /**
     * Constructs a new DrugsImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this drugs element
     */
    public DrugsImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves all drug entries as a string array.
     *
     * @return String[] array containing all drug entries, empty array if no drugs are set
     */
    public String[] getDrugArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(DrugsImpl.DRUG$0, targetList);
            final String[] result = new String[targetList.size()];
            for (int i = 0, len = targetList.size(); i < len; ++i) {
                result[i] = ((SimpleValue)targetList.get(i)).getStringValue();
            }
            return result;
        }
    }
    
    /**
     * Retrieves a specific drug entry by index.
     *
     * @param i int the index of the drug entry to retrieve
     * @return String the drug entry at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public String getDrugArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DrugsImpl.DRUG$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target.getStringValue();
        }
    }
    
    /**
     * Retrieves all drug entries as an XmlString array.
     *
     * @return XmlString[] array containing all drug entries as XML string objects
     */
    public XmlString[] xgetDrugArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(DrugsImpl.DRUG$0, targetList);
            final XmlString[] result = new XmlString[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }
    
    /**
     * Retrieves a specific drug entry by index as an XmlString object.
     *
     * @param i int the index of the drug entry to retrieve
     * @return XmlString the drug entry at the specified index as an XML string object
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public XmlString xgetDrugArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(DrugsImpl.DRUG$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }
    
    /**
     * Returns the number of drug entries in the array.
     *
     * @return int the total number of drug entries
     */
    public int sizeOfDrugArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(DrugsImpl.DRUG$0);
        }
    }
    
    /**
     * Sets the entire drug array, replacing all existing entries.
     *
     * @param drugArray String[] array of drug entries to set
     */
    public void setDrugArray(final String[] drugArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper(drugArray, DrugsImpl.DRUG$0);
        }
    }
    
    /**
     * Sets a specific drug entry at the specified index.
     *
     * @param i int the index of the drug entry to set
     * @param drug String the drug entry to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setDrugArray(final int i, final String drug) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DrugsImpl.DRUG$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.setStringValue(drug);
        }
    }
    
    /**
     * Sets the entire drug array using XmlString objects, replacing all existing entries.
     *
     * @param drugArray XmlString[] array of drug entries as XML string objects to set
     */
    public void xsetDrugArray(final XmlString[] drugArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])drugArray, DrugsImpl.DRUG$0);
        }
    }
    
    /**
     * Sets a specific drug entry at the specified index using an XmlString object.
     *
     * @param i int the index of the drug entry to set
     * @param drug XmlString the drug entry as an XML string object to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void xsetDrugArray(final int i, final XmlString drug) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(DrugsImpl.DRUG$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)drug);
        }
    }
    
    /**
     * Inserts a new drug entry at the specified index.
     *
     * @param i int the index at which to insert the new drug entry
     * @param drug String the drug entry to insert
     */
    public void insertDrug(final int i, final String drug) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final SimpleValue target = (SimpleValue)this.get_store().insert_element_user(DrugsImpl.DRUG$0, i);
            target.setStringValue(drug);
        }
    }
    
    /**
     * Adds a new drug entry to the end of the array.
     *
     * @param drug String the drug entry to add
     */
    public void addDrug(final String drug) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().add_element_user(DrugsImpl.DRUG$0);
            target.setStringValue(drug);
        }
    }
    
    /**
     * Inserts a new empty drug entry at the specified index and returns it as an XmlString.
     *
     * @param i int the index at which to insert the new drug entry
     * @return XmlString the newly created drug entry as an XML string object
     */
    public XmlString insertNewDrug(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().insert_element_user(DrugsImpl.DRUG$0, i);
            return target;
        }
    }
    
    /**
     * Adds a new empty drug entry to the end of the array and returns it as an XmlString.
     *
     * @return XmlString the newly created drug entry as an XML string object
     */
    public XmlString addNewDrug() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().add_element_user(DrugsImpl.DRUG$0);
            return target;
        }
    }
    
    /**
     * Removes the drug entry at the specified index.
     *
     * @param i int the index of the drug entry to remove
     */
    public void removeDrug(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(DrugsImpl.DRUG$0, i);
        }
    }
    
    static {
        // Initialize QName constant for XML element identification within CKD namespace
        DRUG$0 = new QName("http://www.oscarmcmaster.org/ckd", "drug");
    }
}
