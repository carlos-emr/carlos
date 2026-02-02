package ca.openosp.openo.ar2005.impl;

import ca.openosp.openo.ar2005.AR2;
import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.AR1;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.ARRecord;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for ARRecord XML schema type, providing access to BC Antenatal Record data.
 *
 * <p>This class implements the ARRecord interface using Apache XMLBeans framework to handle
 * XML serialization and deserialization of British Columbia Antenatal Record (BCAR) forms.
 * It manages two primary elements: AR1 (patient demographics and pregnancy history) and
 * AR2 (clinical assessments and prenatal care tracking).</p>
 *
 * <p>The implementation extends XmlComplexContentImpl to provide thread-safe access to XML
 * element store operations. All getter and setter methods are synchronized using the XMLBeans
 * monitor pattern to ensure data consistency in concurrent environments.</p>
 *
 * <p><strong>Healthcare Context:</strong> BCAR forms are standardized prenatal care records
 * used throughout British Columbia to track maternal health, fetal development, and pregnancy
 * outcomes. This implementation supports electronic data capture and exchange of antenatal
 * records between healthcare providers and facilities.</p>
 *
 * @see ca.openosp.openo.ar2005.ARRecord
 * @see ca.openosp.openo.ar2005.AR1
 * @see ca.openosp.openo.ar2005.AR2
 * @since 2026-01-24
 */
public class ARRecordImpl extends XmlComplexContentImpl implements ARRecord
{
    private static final long serialVersionUID = 1L;
    private static final QName AR1$0;
    private static final QName AR2$2;

    /**
     * Constructs a new ARRecordImpl instance with the specified schema type.
     *
     * <p>This constructor is typically invoked by the XMLBeans framework during
     * XML parsing or programmatic document creation. It initializes the underlying
     * XML store with the appropriate schema type definition for ARRecord validation.</p>
     *
     * @param sType SchemaType the schema type definition for ARRecord, must not be null
     */
    public ARRecordImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the AR1 (demographics and pregnancy history) element from this antenatal record.
     *
     * <p>AR1 contains patient identification, demographic information, obstetric history,
     * and initial pregnancy assessment data. This method is thread-safe and uses the XMLBeans
     * monitor pattern to synchronize access to the underlying XML store.</p>
     *
     * @return AR1 the AR1 element containing patient demographics and pregnancy history,
     *         or null if the element has not been set
     */
    public AR1 getAR1() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AR1 target = null;
            target = (AR1)this.get_store().find_element_user(ARRecordImpl.AR1$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the AR1 (demographics and pregnancy history) element for this antenatal record.
     *
     * <p>This method replaces any existing AR1 element with the provided value. If no AR1
     * element exists in the XML store, a new element is created. The operation is thread-safe
     * and synchronized using the XMLBeans monitor pattern.</p>
     *
     * @param ar1 AR1 the AR1 element containing patient demographics and pregnancy history
     *            to set in this record, must not be null
     */
    public void setAR1(final AR1 ar1) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AR1 target = null;
            target = (AR1)this.get_store().find_element_user(ARRecordImpl.AR1$0, 0);
            if (target == null) {
                target = (AR1)this.get_store().add_element_user(ARRecordImpl.AR1$0);
            }
            target.set((XmlObject)ar1);
        }
    }

    /**
     * Creates and adds a new AR1 element to this antenatal record.
     *
     * <p>This method creates a new, empty AR1 element in the XML store and returns it
     * for population with patient demographics and pregnancy history data. The caller
     * is responsible for setting the appropriate values on the returned AR1 object.
     * The operation is thread-safe and synchronized using the XMLBeans monitor pattern.</p>
     *
     * @return AR1 a new, empty AR1 element that has been added to this record
     */
    public AR1 addNewAR1() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AR1 target = null;
            target = (AR1)this.get_store().add_element_user(ARRecordImpl.AR1$0);
            return target;
        }
    }

    /**
     * Retrieves the AR2 (clinical assessments and prenatal care) element from this antenatal record.
     *
     * <p>AR2 contains ongoing prenatal visits, clinical assessments, laboratory results,
     * ultrasound findings, and pregnancy complications tracking. This method is thread-safe
     * and uses the XMLBeans monitor pattern to synchronize access to the underlying XML store.</p>
     *
     * @return AR2 the AR2 element containing clinical assessments and prenatal care data,
     *         or null if the element has not been set
     */
    public AR2 getAR2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AR2 target = null;
            target = (AR2)this.get_store().find_element_user(ARRecordImpl.AR2$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the AR2 (clinical assessments and prenatal care) element for this antenatal record.
     *
     * <p>This method replaces any existing AR2 element with the provided value. If no AR2
     * element exists in the XML store, a new element is created. The operation is thread-safe
     * and synchronized using the XMLBeans monitor pattern.</p>
     *
     * @param ar2 AR2 the AR2 element containing clinical assessments and prenatal care data
     *            to set in this record, must not be null
     */
    public void setAR2(final AR2 ar2) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AR2 target = null;
            target = (AR2)this.get_store().find_element_user(ARRecordImpl.AR2$2, 0);
            if (target == null) {
                target = (AR2)this.get_store().add_element_user(ARRecordImpl.AR2$2);
            }
            target.set((XmlObject)ar2);
        }
    }

    /**
     * Creates and adds a new AR2 element to this antenatal record.
     *
     * <p>This method creates a new, empty AR2 element in the XML store and returns it
     * for population with clinical assessment and prenatal care data. The caller is
     * responsible for setting the appropriate values on the returned AR2 object.
     * The operation is thread-safe and synchronized using the XMLBeans monitor pattern.</p>
     *
     * @return AR2 a new, empty AR2 element that has been added to this record
     */
    public AR2 addNewAR2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AR2 target = null;
            target = (AR2)this.get_store().add_element_user(ARRecordImpl.AR2$2);
            return target;
        }
    }
    
    static {
        AR1$0 = new QName("http://www.oscarmcmaster.org/AR2005", "AR1");
        AR2$2 = new QName("http://www.oscarmcmaster.org/AR2005", "AR2");
    }
}
