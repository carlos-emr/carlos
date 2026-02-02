package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.NewbornCare;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for the NewbornCare XML schema type.
 *
 * This class provides the concrete implementation for managing newborn care provider information
 * in the British Columbia Antenatal Record (BCAR) AR2005 form. It handles XML-based storage of
 * healthcare provider types who will provide care for the newborn, including pediatricians,
 * family physicians, midwives, and other specified providers.
 *
 * The implementation uses Apache XMLBeans framework to provide type-safe access to XML elements
 * defined in the AR2005 schema. Access to the underlying XML store is synchronized using the
 * XMLBeans monitor(), providing thread-safe access to individual XML operations performed by this
 * class.
 *
 * @since 2026-01-23
 * @see ca.openosp.openo.ar2005.NewbornCare
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 */
public class NewbornCareImpl extends XmlComplexContentImpl implements NewbornCare
{
    private static final long serialVersionUID = 1L;
    private static final QName PED$0;
    private static final QName FP$2;
    private static final QName MIDWIFE$4;
    private static final QName OTHER$6;

    /**
     * Constructs a new NewbornCareImpl instance with the specified schema type.
     *
     * This constructor initializes the XML complex content implementation with the provided
     * schema type, which defines the structure and validation rules for the newborn care
     * provider information in the AR2005 antenatal record.
     *
     * @param sType SchemaType the schema type definition for this XML element
     */
    public NewbornCareImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the pediatrician care indicator.
     *
     * Gets the boolean value indicating whether a pediatrician will provide care for the newborn.
     * This method provides thread-safe access to the underlying XML element.
     *
     * @return boolean true if a pediatrician is designated for newborn care, false otherwise
     */
    public boolean getPed() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NewbornCareImpl.PED$0, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the pediatrician care indicator as an XmlBoolean object.
     *
     * Gets the XmlBoolean representation of the pediatrician care indicator, providing access
     * to the underlying XMLBeans type for advanced XML manipulation. This method is used when
     * direct access to the XML type is needed for validation or type-specific operations.
     *
     * @return XmlBoolean the XML boolean object representing the pediatrician care indicator,
     *         or null if not set
     */
    public XmlBoolean xgetPed() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NewbornCareImpl.PED$0, 0);
            return target;
        }
    }

    /**
     * Sets the pediatrician care indicator.
     *
     * Updates the boolean value indicating whether a pediatrician will provide care for the newborn.
     * This method provides thread-safe access to modify the underlying XML element, creating the
     * element if it does not already exist.
     *
     * @param ped boolean true to designate a pediatrician for newborn care, false otherwise
     */
    public void setPed(final boolean ped) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NewbornCareImpl.PED$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(NewbornCareImpl.PED$0);
            }
            target.setBooleanValue(ped);
        }
    }

    /**
     * Sets the pediatrician care indicator using an XmlBoolean object.
     *
     * Updates the pediatrician care indicator using an XmlBoolean type, providing type-safe
     * XML manipulation. This method is used when setting values from existing XML objects
     * or when XML-specific validation is required.
     *
     * @param ped XmlBoolean the XML boolean object representing the pediatrician care indicator
     */
    public void xsetPed(final XmlBoolean ped) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NewbornCareImpl.PED$0, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(NewbornCareImpl.PED$0);
            }
            target.set((XmlObject)ped);
        }
    }

    /**
     * Retrieves the family physician care indicator.
     *
     * Gets the boolean value indicating whether a family physician will provide care for the newborn.
     * This method provides thread-safe access to the underlying XML element.
     *
     * @return boolean true if a family physician is designated for newborn care, false otherwise
     */
    public boolean getFP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NewbornCareImpl.FP$2, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the family physician care indicator as an XmlBoolean object.
     *
     * Gets the XmlBoolean representation of the family physician care indicator, providing access
     * to the underlying XMLBeans type for advanced XML manipulation. This method is used when
     * direct access to the XML type is needed for validation or type-specific operations.
     *
     * @return XmlBoolean the XML boolean object representing the family physician care indicator,
     *         or null if not set
     */
    public XmlBoolean xgetFP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NewbornCareImpl.FP$2, 0);
            return target;
        }
    }

    /**
     * Sets the family physician care indicator.
     *
     * Updates the boolean value indicating whether a family physician will provide care for the newborn.
     * This method provides thread-safe access to modify the underlying XML element, creating the
     * element if it does not already exist.
     *
     * @param fp boolean true to designate a family physician for newborn care, false otherwise
     */
    public void setFP(final boolean fp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NewbornCareImpl.FP$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(NewbornCareImpl.FP$2);
            }
            target.setBooleanValue(fp);
        }
    }

    /**
     * Sets the family physician care indicator using an XmlBoolean object.
     *
     * Updates the family physician care indicator using an XmlBoolean type, providing type-safe
     * XML manipulation. This method is used when setting values from existing XML objects
     * or when XML-specific validation is required.
     *
     * @param fp XmlBoolean the XML boolean object representing the family physician care indicator
     */
    public void xsetFP(final XmlBoolean fp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NewbornCareImpl.FP$2, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(NewbornCareImpl.FP$2);
            }
            target.set((XmlObject)fp);
        }
    }

    /**
     * Retrieves the midwife care indicator.
     *
     * Gets the boolean value indicating whether a midwife will provide care for the newborn.
     * This method provides thread-safe access to the underlying XML element.
     *
     * @return boolean true if a midwife is designated for newborn care, false otherwise
     */
    public boolean getMidwife() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NewbornCareImpl.MIDWIFE$4, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the midwife care indicator as an XmlBoolean object.
     *
     * Gets the XmlBoolean representation of the midwife care indicator, providing access
     * to the underlying XMLBeans type for advanced XML manipulation. This method is used when
     * direct access to the XML type is needed for validation or type-specific operations.
     *
     * @return XmlBoolean the XML boolean object representing the midwife care indicator,
     *         or null if not set
     */
    public XmlBoolean xgetMidwife() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NewbornCareImpl.MIDWIFE$4, 0);
            return target;
        }
    }

    /**
     * Sets the midwife care indicator.
     *
     * Updates the boolean value indicating whether a midwife will provide care for the newborn.
     * This method provides thread-safe access to modify the underlying XML element, creating the
     * element if it does not already exist.
     *
     * @param midwife boolean true to designate a midwife for newborn care, false otherwise
     */
    public void setMidwife(final boolean midwife) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NewbornCareImpl.MIDWIFE$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(NewbornCareImpl.MIDWIFE$4);
            }
            target.setBooleanValue(midwife);
        }
    }

    /**
     * Sets the midwife care indicator using an XmlBoolean object.
     *
     * Updates the midwife care indicator using an XmlBoolean type, providing type-safe
     * XML manipulation. This method is used when setting values from existing XML objects
     * or when XML-specific validation is required.
     *
     * @param midwife XmlBoolean the XML boolean object representing the midwife care indicator
     */
    public void xsetMidwife(final XmlBoolean midwife) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NewbornCareImpl.MIDWIFE$4, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(NewbornCareImpl.MIDWIFE$4);
            }
            target.set((XmlObject)midwife);
        }
    }

    /**
     * Retrieves the other provider type description.
     *
     * Gets the string value describing other healthcare provider types not covered by the
     * predefined categories (pediatrician, family physician, or midwife). This allows for
     * flexible capture of additional provider information in the antenatal record.
     *
     * @return String the description of other provider types, or null if not set
     */
    public String getOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NewbornCareImpl.OTHER$6, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the other provider type description as an XmlString object.
     *
     * Gets the XmlString representation of the other provider type description, providing access
     * to the underlying XMLBeans type for advanced XML manipulation. This method is used when
     * direct access to the XML type is needed for validation or type-specific operations.
     *
     * @return XmlString the XML string object representing the other provider type description,
     *         or null if not set
     */
    public XmlString xgetOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(NewbornCareImpl.OTHER$6, 0);
            return target;
        }
    }

    /**
     * Sets the other provider type description.
     *
     * Updates the string value describing other healthcare provider types for newborn care.
     * This method provides thread-safe access to modify the underlying XML element, creating the
     * element if it does not already exist.
     *
     * @param other String the description of other provider types for newborn care
     */
    public void setOther(final String other) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NewbornCareImpl.OTHER$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(NewbornCareImpl.OTHER$6);
            }
            target.setStringValue(other);
        }
    }

    /**
     * Sets the other provider type description using an XmlString object.
     *
     * Updates the other provider type description using an XmlString type, providing type-safe
     * XML manipulation. This method is used when setting values from existing XML objects
     * or when XML-specific validation is required.
     *
     * @param other XmlString the XML string object representing the other provider type description
     */
    public void xsetOther(final XmlString other) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(NewbornCareImpl.OTHER$6, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(NewbornCareImpl.OTHER$6);
            }
            target.set((XmlObject)other);
        }
    }
    
    static {
        PED$0 = new QName("http://www.oscarmcmaster.org/AR2005", "Ped");
        FP$2 = new QName("http://www.oscarmcmaster.org/AR2005", "FP");
        MIDWIFE$4 = new QName("http://www.oscarmcmaster.org/AR2005", "Midwife");
        OTHER$6 = new QName("http://www.oscarmcmaster.org/AR2005", "Other");
    }
}
