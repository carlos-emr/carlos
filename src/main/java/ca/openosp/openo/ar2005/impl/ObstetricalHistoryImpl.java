package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import java.util.List;
import java.util.ArrayList;
import ca.openosp.openo.ar2005.ObstetricalHistoryItemList;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.ObstetricalHistory;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for managing obstetrical history data in the AR2005 antenatal record format.
 *
 * <p>This class provides data access and manipulation methods for obstetrical history information,
 * which is a critical component of prenatal care documentation. It manages a collection of
 * {@link ObstetricalHistoryItemList} elements that track previous pregnancy outcomes, complications,
 * and other relevant obstetrical events for comprehensive maternal health assessment.</p>
 *
 * <p>The implementation uses Apache XMLBeans to handle XML serialization/deserialization of obstetrical
 * history records according to the AR2005 schema (http://www.oscarmcmaster.org/AR2005). All data access
 * operations are thread-safe through internal synchronization mechanisms.</p>
 *
 * <p><strong>Healthcare Context:</strong> Obstetrical history is essential for:</p>
 * <ul>
 *   <li>Risk assessment during current pregnancy</li>
 *   <li>Identification of potential complications based on past outcomes</li>
 *   <li>Care planning and delivery method decisions</li>
 *   <li>Continuity of care across multiple pregnancies</li>
 * </ul>
 *
 * @see ObstetricalHistory
 * @see ObstetricalHistoryItemList
 *
 * @since 2026-01-24
 */
public class ObstetricalHistoryImpl extends XmlComplexContentImpl implements ObstetricalHistory
{
    private static final long serialVersionUID = 1L;
    private static final QName OBSLIST$0;

    /**
     * Constructs a new ObstetricalHistoryImpl instance with the specified XMLBeans schema type.
     *
     * <p>This constructor is typically invoked by the XMLBeans framework during XML parsing
     * or programmatic object creation. It initializes the internal XML store structure
     * according to the AR2005 schema definition for obstetrical history data.</p>
     *
     * @param sType SchemaType the XMLBeans schema type definition for this obstetrical history object
     */
    public ObstetricalHistoryImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves all obstetrical history items as an array.
     *
     * <p>This method returns a complete array of all {@link ObstetricalHistoryItemList} elements
     * stored in this obstetrical history record. Each item represents a distinct obstetrical event
     * or pregnancy outcome from the patient's medical history.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe through internal synchronization.</p>
     *
     * @return ObstetricalHistoryItemList[] array containing all obstetrical history items;
     *         returns an empty array if no items exist
     */
    public ObstetricalHistoryItemList[] getObsListArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(ObstetricalHistoryImpl.OBSLIST$0, targetList);
            final ObstetricalHistoryItemList[] result = new ObstetricalHistoryItemList[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }

    /**
     * Retrieves a specific obstetrical history item by its zero-based index position.
     *
     * <p>This method provides direct access to individual obstetrical history items using
     * array-style indexing. The index is zero-based, so valid values range from 0 to
     * {@code sizeOfObsListArray() - 1}.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe through internal synchronization.</p>
     *
     * @param i int the zero-based index of the obstetrical history item to retrieve
     * @return ObstetricalHistoryItemList the obstetrical history item at the specified index
     * @throws IndexOutOfBoundsException if the index is negative or greater than or equal to
     *         the number of items in the collection
     */
    public ObstetricalHistoryItemList getObsListArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ObstetricalHistoryItemList target = null;
            target = (ObstetricalHistoryItemList)this.get_store().find_element_user(ObstetricalHistoryImpl.OBSLIST$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }

    /**
     * Returns the total number of obstetrical history items in this collection.
     *
     * <p>This method provides the count of all {@link ObstetricalHistoryItemList} elements
     * currently stored in this obstetrical history record. The returned value can be used
     * to iterate through items or validate index bounds before accessing specific elements.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe through internal synchronization.</p>
     *
     * @return int the total number of obstetrical history items; returns 0 if the collection is empty
     */
    public int sizeOfObsListArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(ObstetricalHistoryImpl.OBSLIST$0);
        }
    }

    /**
     * Replaces the entire collection of obstetrical history items with a new array.
     *
     * <p>This method performs a complete replacement of all existing obstetrical history items
     * with the provided array. All previously stored items are removed, and the new items from
     * the provided array are added in their place. This is useful for bulk updates or when
     * reconstructing the obstetrical history from external data sources.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe through internal synchronization.</p>
     *
     * @param obsListArray ObstetricalHistoryItemList[] the new array of obstetrical history items
     *        to replace the current collection; may be an empty array to clear all items
     */
    public void setObsListArray(final ObstetricalHistoryItemList[] obsListArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])obsListArray, ObstetricalHistoryImpl.OBSLIST$0);
        }
    }

    /**
     * Replaces a specific obstetrical history item at the given zero-based index position.
     *
     * <p>This method updates an existing obstetrical history item at the specified index with
     * new data. The index must correspond to an existing item in the collection. This operation
     * preserves the ordering of all other items in the collection.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe through internal synchronization.</p>
     *
     * @param i int the zero-based index of the item to replace
     * @param obsList ObstetricalHistoryItemList the new obstetrical history item data to set at the specified position
     * @throws IndexOutOfBoundsException if the index is negative or greater than or equal to
     *         the number of items in the collection
     */
    public void setObsListArray(final int i, final ObstetricalHistoryItemList obsList) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ObstetricalHistoryItemList target = null;
            target = (ObstetricalHistoryItemList)this.get_store().find_element_user(ObstetricalHistoryImpl.OBSLIST$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)obsList);
        }
    }

    /**
     * Inserts a new obstetrical history item at the specified zero-based index position.
     *
     * <p>This method creates and inserts a new, empty {@link ObstetricalHistoryItemList} at the
     * specified index position. All existing items at or after this index are shifted to the right
     * (their indices are incremented by one). The newly created item is returned for immediate
     * population with obstetrical data.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe through internal synchronization.</p>
     *
     * @param i int the zero-based index position where the new item should be inserted;
     *        must be between 0 and {@code sizeOfObsListArray()} (inclusive)
     * @return ObstetricalHistoryItemList the newly created and inserted obstetrical history item,
     *         ready to be populated with data
     * @throws IndexOutOfBoundsException if the index is negative or greater than the current size
     */
    public ObstetricalHistoryItemList insertNewObsList(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ObstetricalHistoryItemList target = null;
            target = (ObstetricalHistoryItemList)this.get_store().insert_element_user(ObstetricalHistoryImpl.OBSLIST$0, i);
            return target;
        }
    }

    /**
     * Appends a new obstetrical history item to the end of the collection.
     *
     * <p>This method creates and adds a new, empty {@link ObstetricalHistoryItemList} to the end
     * of the current collection of obstetrical history items. The newly created item is returned
     * for immediate population with obstetrical data. This is the preferred method for adding new
     * obstetrical history entries in chronological or sequential order.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe through internal synchronization.</p>
     *
     * @return ObstetricalHistoryItemList the newly created obstetrical history item added to the
     *         end of the collection, ready to be populated with data
     */
    public ObstetricalHistoryItemList addNewObsList() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ObstetricalHistoryItemList target = null;
            target = (ObstetricalHistoryItemList)this.get_store().add_element_user(ObstetricalHistoryImpl.OBSLIST$0);
            return target;
        }
    }

    /**
     * Removes an obstetrical history item at the specified zero-based index position.
     *
     * <p>This method deletes the obstetrical history item at the given index from the collection.
     * All items after the removed item are shifted to the left (their indices are decremented by one).
     * The size of the collection is reduced by one after this operation.</p>
     *
     * <p><strong>Clinical Note:</strong> Removal of obstetrical history data should be performed
     * with caution as this information is medically significant for patient care and may be required
     * for audit trail purposes.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe through internal synchronization.</p>
     *
     * @param i int the zero-based index of the obstetrical history item to remove
     * @throws IndexOutOfBoundsException if the index is negative or greater than or equal to
     *         the number of items in the collection
     */
    public void removeObsList(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(ObstetricalHistoryImpl.OBSLIST$0, i);
        }
    }
    
    static {
        OBSLIST$0 = new QName("http://www.oscarmcmaster.org/AR2005", "obsList");
    }
}
