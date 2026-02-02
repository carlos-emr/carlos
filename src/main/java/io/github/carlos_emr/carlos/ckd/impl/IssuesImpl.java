package io.github.carlos_emr.carlos.ckd.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import java.util.List;
import org.apache.xmlbeans.SimpleValue;
import java.util.ArrayList;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.Issues;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link Issues} interface.
 *
 * This class provides the concrete implementation for managing patient issue lists
 * within CKD (Chronic Kidney Disease) medical history configuration documents. It handles
 * the XML serialization and deserialization of issue arrays, allowing for the management
 * of multiple patient medical problems, conditions, and health issues used in
 * comprehensive CKD patient care and medical history tracking.
 *
 * The implementation manages issue elements as an array of string values within the
 * "http://www.oscarmcmaster.org/ckd" XML namespace, providing thread-safe access
 * to individual issue entries and array operations including adding, removing,
 * and modifying patient medical issues.
 *
 * Key functionality includes:
 * - Array-based medical issue management with index-based access
 * - Dynamic addition and removal of patient issue entries
 * - Support for both string and XmlString representations
 * - Thread-safe operations for concurrent access
 * - Integration with medical history workflows and CKD care protocols
 *
 * @see Issues
 * @since 2010-01-01
 */
public class IssuesImpl extends XmlComplexContentImpl implements Issues
{
    private static final long serialVersionUID = 1L;
    private static final QName ISSUE$0;
    
    /**
     * Constructs a new IssuesImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this issues element
     */
    public IssuesImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves all patient issue entries as a string array.
     *
     * @return String[] array containing all patient medical issues, empty array if no issues are set
     */
    public String[] getIssueArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(IssuesImpl.ISSUE$0, targetList);
            final String[] result = new String[targetList.size()];
            for (int i = 0, len = targetList.size(); i < len; ++i) {
                result[i] = ((SimpleValue)targetList.get(i)).getStringValue();
            }
            return result;
        }
    }
    
    /**
     * Retrieves a specific patient issue entry by index.
     *
     * @param i int the index of the patient issue entry to retrieve
     * @return String the patient issue entry at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public String getIssueArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(IssuesImpl.ISSUE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target.getStringValue();
        }
    }
    
    /**
     * Retrieves all patient issue entries as an XmlString array.
     *
     * @return XmlString[] array containing all patient medical issues as XML string objects
     */
    public XmlString[] xgetIssueArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(IssuesImpl.ISSUE$0, targetList);
            final XmlString[] result = new XmlString[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }
    
    /**
     * Retrieves a specific patient issue entry by index as an XmlString object.
     *
     * @param i int the index of the patient issue entry to retrieve
     * @return XmlString the patient issue entry at the specified index as an XML string object
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public XmlString xgetIssueArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(IssuesImpl.ISSUE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }
    
    /**
     * Returns the number of patient issue entries in the array.
     *
     * @return int the total number of patient issue entries
     */
    public int sizeOfIssueArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(IssuesImpl.ISSUE$0);
        }
    }
    
    /**
     * Sets the entire patient issue array, replacing all existing entries.
     *
     * @param issueArray String[] array of patient issue entries to set
     */
    public void setIssueArray(final String[] issueArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper(issueArray, IssuesImpl.ISSUE$0);
        }
    }
    
    /**
     * Sets a specific patient issue entry at the specified index.
     *
     * @param i int the index of the patient issue entry to set
     * @param issue String the patient issue entry to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setIssueArray(final int i, final String issue) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(IssuesImpl.ISSUE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.setStringValue(issue);
        }
    }
    
    /**
     * Sets the entire patient issue array using XmlString objects, replacing all existing entries.
     *
     * @param issueArray XmlString[] array of patient issue entries as XML string objects to set
     */
    public void xsetIssueArray(final XmlString[] issueArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])issueArray, IssuesImpl.ISSUE$0);
        }
    }
    
    /**
     * Sets a specific patient issue entry at the specified index using an XmlString object.
     *
     * @param i int the index of the patient issue entry to set
     * @param issue XmlString the patient issue entry as an XML string object to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void xsetIssueArray(final int i, final XmlString issue) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(IssuesImpl.ISSUE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)issue);
        }
    }
    
    /**
     * Inserts a new patient issue entry at the specified index.
     *
     * @param i int the index at which to insert the new patient issue entry
     * @param issue String the patient issue entry to insert
     */
    public void insertIssue(final int i, final String issue) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final SimpleValue target = (SimpleValue)this.get_store().insert_element_user(IssuesImpl.ISSUE$0, i);
            target.setStringValue(issue);
        }
    }
    
    /**
     * Adds a new patient issue entry to the end of the array.
     *
     * @param issue String the patient issue entry to add
     */
    public void addIssue(final String issue) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().add_element_user(IssuesImpl.ISSUE$0);
            target.setStringValue(issue);
        }
    }
    
    /**
     * Inserts a new empty patient issue entry at the specified index and returns it as an XmlString.
     *
     * @param i int the index at which to insert the new patient issue entry
     * @return XmlString the newly created patient issue entry as an XML string object
     */
    public XmlString insertNewIssue(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().insert_element_user(IssuesImpl.ISSUE$0, i);
            return target;
        }
    }
    
    /**
     * Adds a new empty patient issue entry to the end of the array and returns it as an XmlString.
     *
     * @return XmlString the newly created patient issue entry as an XML string object
     */
    public XmlString addNewIssue() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().add_element_user(IssuesImpl.ISSUE$0);
            return target;
        }
    }
    
    /**
     * Removes the patient issue entry at the specified index.
     *
     * @param i int the index of the patient issue entry to remove
     */
    public void removeIssue(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(IssuesImpl.ISSUE$0, i);
        }
    }
    
    static {
        // Initialize QName constant for XML element identification within CKD namespace
        ISSUE$0 = new QName("http://www.oscarmcmaster.org/ckd", "issue");
    }
}
