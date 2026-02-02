package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import ca.openosp.openo.ar2005.NewbornCare;
import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.BirthAttendants;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.PractitionerInformation;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for the PractitionerInformation element in the AR2005 (BC Antenatal Record) schema.
 *
 * This class provides XMLBeans-based access to practitioner information captured during prenatal and birth care,
 * including details about birth attendants, newborn care providers, and the family physician. It is part of the
 * British Columbia Antenatal Record (BCAR) form system used for standardized pregnancy and birth documentation
 * in OpenO EMR.
 *
 * The implementation extends XMLBeans' XmlComplexContentImpl to provide thread-safe access to XML element data
 * through synchronized accessor methods. All data manipulation operations use the underlying XML store with
 * orphan checking to ensure data integrity.
 *
 * @see ca.openosp.openo.ar2005.PractitionerInformation
 * @see ca.openosp.openo.ar2005.BirthAttendants
 * @see ca.openosp.openo.ar2005.NewbornCare
 * @since 2026-01-24
 */
public class PractitionerInformationImpl extends XmlComplexContentImpl implements PractitionerInformation
{
    private static final long serialVersionUID = 1L;
    private static final QName BIRTHATTENDANTS$0;
    private static final QName NEWBORNCARE$2;
    private static final QName FAMILYPHYSICIAN$4;

    /**
     * Constructs a new PractitionerInformationImpl instance with the specified schema type.
     *
     * This constructor is typically invoked by the XMLBeans framework during XML document parsing
     * or when creating new instances programmatically. It initializes the underlying XML store
     * with the appropriate schema type definition for practitioner information elements.
     *
     * @param sType SchemaType the schema type definition for this element
     */
    public PractitionerInformationImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the birth attendants information from the antenatal record.
     *
     * This method provides thread-safe access to the BirthAttendants element, which contains
     * information about healthcare providers who attended the birth, including physicians,
     * midwives, and other medical personnel involved in the delivery.
     *
     * @return BirthAttendants the birth attendants element, or null if not set
     */
    public BirthAttendants getBirthAttendants() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            BirthAttendants target = null;
            target = (BirthAttendants)this.get_store().find_element_user(PractitionerInformationImpl.BIRTHATTENDANTS$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the birth attendants information in the antenatal record.
     *
     * This method updates the BirthAttendants element with information about healthcare providers
     * who attended the birth. If the element does not exist, it will be created. The operation is
     * thread-safe and ensures proper XML store synchronization.
     *
     * @param birthAttendants BirthAttendants the birth attendants element to set
     */
    public void setBirthAttendants(final BirthAttendants birthAttendants) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            BirthAttendants target = null;
            target = (BirthAttendants)this.get_store().find_element_user(PractitionerInformationImpl.BIRTHATTENDANTS$0, 0);
            if (target == null) {
                target = (BirthAttendants)this.get_store().add_element_user(PractitionerInformationImpl.BIRTHATTENDANTS$0);
            }
            target.set((XmlObject)birthAttendants);
        }
    }

    /**
     * Creates and adds a new birth attendants element to the antenatal record.
     *
     * This method creates a new BirthAttendants element in the XML store and returns it for
     * population with healthcare provider information. The returned object can be used to set
     * details about physicians, midwives, and other medical personnel involved in the birth.
     *
     * @return BirthAttendants the newly created birth attendants element
     */
    public BirthAttendants addNewBirthAttendants() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            BirthAttendants target = null;
            target = (BirthAttendants)this.get_store().add_element_user(PractitionerInformationImpl.BIRTHATTENDANTS$0);
            return target;
        }
    }

    /**
     * Retrieves the newborn care information from the antenatal record.
     *
     * This method provides thread-safe access to the NewbornCare element, which contains
     * information about healthcare providers responsible for newborn care, including
     * pediatricians and other medical personnel providing immediate postnatal care.
     *
     * @return NewbornCare the newborn care element, or null if not set
     */
    public NewbornCare getNewbornCare() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NewbornCare target = null;
            target = (NewbornCare)this.get_store().find_element_user(PractitionerInformationImpl.NEWBORNCARE$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the newborn care information in the antenatal record.
     *
     * This method updates the NewbornCare element with information about healthcare providers
     * responsible for newborn care. If the element does not exist, it will be created. The
     * operation is thread-safe and ensures proper XML store synchronization.
     *
     * @param newbornCare NewbornCare the newborn care element to set
     */
    public void setNewbornCare(final NewbornCare newbornCare) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NewbornCare target = null;
            target = (NewbornCare)this.get_store().find_element_user(PractitionerInformationImpl.NEWBORNCARE$2, 0);
            if (target == null) {
                target = (NewbornCare)this.get_store().add_element_user(PractitionerInformationImpl.NEWBORNCARE$2);
            }
            target.set((XmlObject)newbornCare);
        }
    }

    /**
     * Creates and adds a new newborn care element to the antenatal record.
     *
     * This method creates a new NewbornCare element in the XML store and returns it for
     * population with healthcare provider information. The returned object can be used to set
     * details about pediatricians and other medical personnel providing postnatal care.
     *
     * @return NewbornCare the newly created newborn care element
     */
    public NewbornCare addNewNewbornCare() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NewbornCare target = null;
            target = (NewbornCare)this.get_store().add_element_user(PractitionerInformationImpl.NEWBORNCARE$2);
            return target;
        }
    }

    /**
     * Retrieves the family physician name from the antenatal record.
     *
     * This method provides thread-safe access to the family physician element, which contains
     * the name of the primary care physician responsible for ongoing patient care and prenatal
     * follow-up. The value is returned as a String.
     *
     * @return String the family physician name, or null if not set
     */
    public String getFamilyPhysician() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PractitionerInformationImpl.FAMILYPHYSICIAN$4, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the family physician element as an XmlString object.
     *
     * This method provides thread-safe access to the family physician element in its raw
     * XMLBeans form. This allows access to XML-specific features such as validation state,
     * schema type information, and XML formatting.
     *
     * @return XmlString the family physician element as an XmlString, or null if not set
     */
    public XmlString xgetFamilyPhysician() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PractitionerInformationImpl.FAMILYPHYSICIAN$4, 0);
            return target;
        }
    }

    /**
     * Sets the family physician name in the antenatal record.
     *
     * This method updates the family physician element with the name of the primary care
     * physician. If the element does not exist, it will be created. The operation is
     * thread-safe and ensures proper XML store synchronization.
     *
     * @param familyPhysician String the family physician name to set
     */
    public void setFamilyPhysician(final String familyPhysician) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PractitionerInformationImpl.FAMILYPHYSICIAN$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PractitionerInformationImpl.FAMILYPHYSICIAN$4);
            }
            target.setStringValue(familyPhysician);
        }
    }

    /**
     * Sets the family physician element using an XmlString object.
     *
     * This method updates the family physician element using an XmlString object, which
     * preserves XML-specific features such as validation state and schema type information.
     * If the element does not exist, it will be created. The operation is thread-safe and
     * ensures proper XML store synchronization.
     *
     * @param familyPhysician XmlString the family physician element to set
     */
    public void xsetFamilyPhysician(final XmlString familyPhysician) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PractitionerInformationImpl.FAMILYPHYSICIAN$4, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PractitionerInformationImpl.FAMILYPHYSICIAN$4);
            }
            target.set((XmlObject)familyPhysician);
        }
    }
    
    static {
        BIRTHATTENDANTS$0 = new QName("http://www.oscarmcmaster.org/AR2005", "birthAttendants");
        NEWBORNCARE$2 = new QName("http://www.oscarmcmaster.org/AR2005", "newbornCare");
        FAMILYPHYSICIAN$4 = new QName("http://www.oscarmcmaster.org/AR2005", "familyPhysician");
    }
}
