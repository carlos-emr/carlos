package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.YesNoNullType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for YesNoNullType XML schema element used in the AR2005 (Antenatal Record 2005) system.
 *
 * <p>This class provides XMLBeans-based implementation for representing a tri-state boolean value commonly used
 * in obstetrical and prenatal care documentation. The tri-state design allows healthcare providers to distinguish
 * between affirmative responses (yes), negative responses (no), and explicitly null/unknown responses, which is
 * critical for accurate medical record keeping in pregnancy care.</p>
 *
 * <p>The AR2005 package contains XML schema types and implementations for the British Columbia Antenatal Record
 * (BCAR) system, which is used to track pregnancy history, obstetrical information, physical examinations, and
 * other prenatal care data in compliance with Canadian healthcare standards.</p>
 *
 * <p>This implementation extends {@link XmlComplexContentImpl} from Apache XMLBeans and provides thread-safe
 * access to the underlying XML element data through synchronized methods. All operations use the internal
 * XMLBeans store with orphan checking to ensure data integrity.</p>
 *
 * @see YesNoNullType
 * @see XmlComplexContentImpl
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-23
 */
public class YesNoNullTypeImpl extends XmlComplexContentImpl implements YesNoNullType
{
    private static final long serialVersionUID = 1L;
    private static final QName YES$0;
    private static final QName NO$2;
    private static final QName NULL$4;

    /**
     * Constructs a new YesNoNullTypeImpl instance with the specified schema type.
     *
     * <p>This constructor initializes the XMLBeans complex content implementation with the provided
     * schema type definition. It is typically invoked by the XMLBeans framework during XML parsing
     * or when creating new instances through the Factory class.</p>
     *
     * @param sType SchemaType the schema type definition for this XML element
     */
    public YesNoNullTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the boolean value of the "yes" element.
     *
     * <p>This method provides thread-safe access to the "yes" element value in the XML structure.
     * In the context of prenatal care documentation, this represents an affirmative response to
     * a clinical question or observation.</p>
     *
     * @return boolean true if the "yes" element exists and contains a true value, false otherwise
     */
    public boolean getYes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(YesNoNullTypeImpl.YES$0, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XmlBoolean representation of the "yes" element.
     *
     * <p>This method provides access to the underlying XMLBeans XmlBoolean object, which allows
     * for more advanced XML manipulation including access to XML metadata, validation state,
     * and schema type information.</p>
     *
     * @return XmlBoolean the XMLBeans representation of the "yes" element, or null if not set
     */
    public XmlBoolean xgetYes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(YesNoNullTypeImpl.YES$0, 0);
            return target;
        }
    }

    /**
     * Checks whether the "yes" element is present in the XML structure.
     *
     * <p>This method determines if the "yes" element has been explicitly set, which is important
     * for tri-state logic in medical records where the distinction between "not set", "yes", and "no"
     * carries clinical significance.</p>
     *
     * @return boolean true if the "yes" element is present, false otherwise
     */
    public boolean isSetYes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(YesNoNullTypeImpl.YES$0) != 0;
        }
    }

    /**
     * Sets the boolean value of the "yes" element.
     *
     * <p>This method provides thread-safe modification of the "yes" element. If the element does not
     * exist, it will be created. In prenatal care workflows, this is used to record affirmative
     * responses to clinical assessments, screening questions, or examination findings.</p>
     *
     * @param yes boolean the value to set for the "yes" element
     */
    public void setYes(final boolean yes) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(YesNoNullTypeImpl.YES$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(YesNoNullTypeImpl.YES$0);
            }
            target.setBooleanValue(yes);
        }
    }

    /**
     * Sets the "yes" element using an XmlBoolean object.
     *
     * <p>This method allows setting the "yes" element using an XMLBeans XmlBoolean object,
     * which preserves XML metadata and schema validation information. This is useful when
     * copying or transforming XML structures while maintaining full XMLBeans context.</p>
     *
     * @param yes XmlBoolean the XMLBeans object to set for the "yes" element
     */
    public void xsetYes(final XmlBoolean yes) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(YesNoNullTypeImpl.YES$0, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(YesNoNullTypeImpl.YES$0);
            }
            target.set((XmlObject)yes);
        }
    }

    /**
     * Removes the "yes" element from the XML structure.
     *
     * <p>This method provides thread-safe removal of the "yes" element. In medical documentation,
     * unsetting an element is different from setting it to false, as it represents the absence of
     * a recorded response rather than a negative response.</p>
     */
    public void unsetYes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(YesNoNullTypeImpl.YES$0, 0);
        }
    }

    /**
     * Retrieves the boolean value of the "no" element.
     *
     * <p>This method provides thread-safe access to the "no" element value in the XML structure.
     * In the context of prenatal care documentation, this represents a negative response to
     * a clinical question or observation.</p>
     *
     * @return boolean true if the "no" element exists and contains a true value, false otherwise
     */
    public boolean getNo() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(YesNoNullTypeImpl.NO$2, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XmlBoolean representation of the "no" element.
     *
     * <p>This method provides access to the underlying XMLBeans XmlBoolean object, which allows
     * for more advanced XML manipulation including access to XML metadata, validation state,
     * and schema type information.</p>
     *
     * @return XmlBoolean the XMLBeans representation of the "no" element, or null if not set
     */
    public XmlBoolean xgetNo() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(YesNoNullTypeImpl.NO$2, 0);
            return target;
        }
    }

    /**
     * Checks whether the "no" element is present in the XML structure.
     *
     * <p>This method determines if the "no" element has been explicitly set, which is important
     * for tri-state logic in medical records where the distinction between "not set", "yes", and "no"
     * carries clinical significance.</p>
     *
     * @return boolean true if the "no" element is present, false otherwise
     */
    public boolean isSetNo() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(YesNoNullTypeImpl.NO$2) != 0;
        }
    }

    /**
     * Sets the boolean value of the "no" element.
     *
     * <p>This method provides thread-safe modification of the "no" element. If the element does not
     * exist, it will be created. In prenatal care workflows, this is used to record negative
     * responses to clinical assessments, screening questions, or examination findings.</p>
     *
     * @param no boolean the value to set for the "no" element
     */
    public void setNo(final boolean no) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(YesNoNullTypeImpl.NO$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(YesNoNullTypeImpl.NO$2);
            }
            target.setBooleanValue(no);
        }
    }

    /**
     * Sets the "no" element using an XmlBoolean object.
     *
     * <p>This method allows setting the "no" element using an XMLBeans XmlBoolean object,
     * which preserves XML metadata and schema validation information. This is useful when
     * copying or transforming XML structures while maintaining full XMLBeans context.</p>
     *
     * @param no XmlBoolean the XMLBeans object to set for the "no" element
     */
    public void xsetNo(final XmlBoolean no) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(YesNoNullTypeImpl.NO$2, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(YesNoNullTypeImpl.NO$2);
            }
            target.set((XmlObject)no);
        }
    }

    /**
     * Removes the "no" element from the XML structure.
     *
     * <p>This method provides thread-safe removal of the "no" element. In medical documentation,
     * unsetting an element is different from setting it to false, as it represents the absence of
     * a recorded response rather than a positive response.</p>
     */
    public void unsetNo() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(YesNoNullTypeImpl.NO$2, 0);
        }
    }

    /**
     * Retrieves the boolean value of the "null" element.
     *
     * <p>This method provides thread-safe access to the "null" element value in the XML structure.
     * In the context of prenatal care documentation, this represents an explicitly unknown or
     * not-applicable response to a clinical question or observation, which is distinct from
     * simply not providing an answer.</p>
     *
     * @return boolean true if the "null" element exists and contains a true value, false otherwise
     */
    public boolean getNull() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(YesNoNullTypeImpl.NULL$4, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XmlBoolean representation of the "null" element.
     *
     * <p>This method provides access to the underlying XMLBeans XmlBoolean object, which allows
     * for more advanced XML manipulation including access to XML metadata, validation state,
     * and schema type information.</p>
     *
     * @return XmlBoolean the XMLBeans representation of the "null" element, or null if not set
     */
    public XmlBoolean xgetNull() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(YesNoNullTypeImpl.NULL$4, 0);
            return target;
        }
    }

    /**
     * Checks whether the "null" element is present in the XML structure.
     *
     * <p>This method determines if the "null" element has been explicitly set, which is important
     * for tri-state logic in medical records where explicitly recording "unknown" or "not applicable"
     * is clinically different from not recording a response at all.</p>
     *
     * @return boolean true if the "null" element is present, false otherwise
     */
    public boolean isSetNull() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(YesNoNullTypeImpl.NULL$4) != 0;
        }
    }

    /**
     * Sets the boolean value of the "null" element.
     *
     * <p>This method provides thread-safe modification of the "null" element. If the element does not
     * exist, it will be created. In prenatal care workflows, this is used to explicitly record when
     * information is unknown, not applicable, or when a healthcare provider chooses to mark a field
     * as null rather than leaving it unanswered.</p>
     *
     * @param xnull boolean the value to set for the "null" element
     */
    public void setNull(final boolean xnull) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(YesNoNullTypeImpl.NULL$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(YesNoNullTypeImpl.NULL$4);
            }
            target.setBooleanValue(xnull);
        }
    }

    /**
     * Sets the "null" element using an XmlBoolean object.
     *
     * <p>This method allows setting the "null" element using an XMLBeans XmlBoolean object,
     * which preserves XML metadata and schema validation information. This is useful when
     * copying or transforming XML structures while maintaining full XMLBeans context.</p>
     *
     * @param xnull XmlBoolean the XMLBeans object to set for the "null" element
     */
    public void xsetNull(final XmlBoolean xnull) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(YesNoNullTypeImpl.NULL$4, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(YesNoNullTypeImpl.NULL$4);
            }
            target.set((XmlObject)xnull);
        }
    }

    /**
     * Removes the "null" element from the XML structure.
     *
     * <p>This method provides thread-safe removal of the "null" element. In medical documentation,
     * unsetting the null element reverts the field to an unrecorded state, which is different from
     * having explicitly marked it as null/unknown.</p>
     */
    public void unsetNull() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(YesNoNullTypeImpl.NULL$4, 0);
        }
    }
    
    static {
        YES$0 = new QName("http://www.oscarmcmaster.org/AR2005", "yes");
        NO$2 = new QName("http://www.oscarmcmaster.org/AR2005", "no");
        NULL$4 = new QName("http://www.oscarmcmaster.org/AR2005", "null");
    }
}
