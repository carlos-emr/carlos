package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.YesNoNullType;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.FamilyHistoryType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for the FamilyHistoryType XML schema type used in AR2005 (Antenatal Record 2005) forms.
 *
 * <p>This class provides the concrete implementation of the FamilyHistoryType interface, which is used to
 * capture and manage family medical history information as part of antenatal care documentation in the
 * OpenO EMR system. It specifically handles the "atRisk" element that indicates whether a patient has
 * family history risk factors relevant to pregnancy and maternal care.</p>
 *
 * <p>The implementation extends Apache XMLBeans' {@link XmlComplexContentImpl} to provide XML serialization
 * and deserialization capabilities, ensuring that family history data can be stored and retrieved in a
 * standardized XML format compliant with the AR2005 schema (http://www.oscarmcmaster.org/AR2005).</p>
 *
 * <p>This class is thread-safe as all accessor methods are synchronized on the internal monitor object
 * provided by the XMLBeans framework.</p>
 *
 * @see ca.openosp.openo.ar2005.FamilyHistoryType
 * @see ca.openosp.openo.ar2005.YesNoNullType
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-23
 */
public class FamilyHistoryTypeImpl extends XmlComplexContentImpl implements FamilyHistoryType
{
    private static final long serialVersionUID = 1L;
    private static final QName ATRISK$0;

    /**
     * Constructs a new FamilyHistoryTypeImpl instance with the specified schema type.
     *
     * <p>This constructor is typically called by the XMLBeans framework during XML deserialization
     * or when creating new instances programmatically. It initializes the underlying XML store
     * with the appropriate schema type definition for family history data.</p>
     *
     * @param sType SchemaType the schema type definition for this family history element,
     *              which defines the structure and validation rules for the XML content
     */
    public FamilyHistoryTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the family history risk indicator for the patient.
     *
     * <p>This method returns the "atRisk" element which indicates whether the patient has
     * family history factors that pose a risk for pregnancy or maternal health outcomes.
     * This information is critical for antenatal care planning and risk assessment.</p>
     *
     * <p>The method is thread-safe, using synchronization on the internal monitor to ensure
     * consistent reads in multi-threaded environments. The underlying XML store is checked
     * to verify the element has not been orphaned before retrieval.</p>
     *
     * @return YesNoNullType the family history risk indicator (Yes, No, or Null if not set),
     *         or null if the atRisk element has not been initialized
     */
    public YesNoNullType getAtRisk() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(FamilyHistoryTypeImpl.ATRISK$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the family history risk indicator for the patient.
     *
     * <p>This method updates the "atRisk" element to indicate whether the patient has
     * family history factors that pose a risk for pregnancy or maternal health outcomes.
     * If the element does not exist in the XML store, it will be created automatically.</p>
     *
     * <p>The method is thread-safe, using synchronization on the internal monitor to ensure
     * consistent writes in multi-threaded environments. The underlying XML store is checked
     * to verify the element has not been orphaned before modification.</p>
     *
     * @param atRisk YesNoNullType the family history risk indicator to set (Yes, No, or Null)
     */
    public void setAtRisk(final YesNoNullType atRisk) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(FamilyHistoryTypeImpl.ATRISK$0, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(FamilyHistoryTypeImpl.ATRISK$0);
            }
            target.set((XmlObject)atRisk);
        }
    }

    /**
     * Creates and adds a new family history risk indicator element to this family history record.
     *
     * <p>This method creates a new "atRisk" element in the XML store and returns it for further
     * configuration. This is useful when you need to initialize the risk indicator and then set
     * its value programmatically. The newly created element is immediately persisted to the
     * underlying XML store.</p>
     *
     * <p>The method is thread-safe, using synchronization on the internal monitor to ensure
     * consistent operations in multi-threaded environments. The underlying XML store is checked
     * to verify the element has not been orphaned before the new element is added.</p>
     *
     * @return YesNoNullType a newly created and initialized family history risk indicator element
     *         that can be configured with Yes, No, or Null values
     */
    public YesNoNullType addNewAtRisk() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(FamilyHistoryTypeImpl.ATRISK$0);
            return target;
        }
    }
    
    static {
        ATRISK$0 = new QName("http://www.oscarmcmaster.org/AR2005", "atRisk");
    }
}
