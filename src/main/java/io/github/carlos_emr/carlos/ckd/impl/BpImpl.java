package io.github.carlos_emr.carlos.ckd.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.Bp;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link Bp} interface.
 *
 * This class provides the concrete implementation for managing blood pressure (BP) data
 * elements in CKD (Chronic Kidney Disease) configuration documents. It handles the XML
 * serialization and deserialization of systolic and diastolic blood pressure values
 * using the Apache XMLBeans framework.
 *
 * The class manages BP elements within the "http://www.oscarmcmaster.org/ckd" XML namespace
 * and provides thread-safe access to both systolic and diastolic pressure values through
 * synchronized methods.
 *
 * @see Bp
 * @since 2010-01-01
 */
public class BpImpl extends XmlComplexContentImpl implements Bp
{
    private static final long serialVersionUID = 1L;
    private static final QName SYSTOLIC$0;
    private static final QName DIASTOLIC$2;
    
    /**
     * Constructs a new BpImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this BP element
     */
    public BpImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves the systolic blood pressure value.
     *
     * @return String the systolic blood pressure value, or null if not set
     */
    public String getSystolic() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BpImpl.SYSTOLIC$0, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }
    
    /**
     * Retrieves the systolic blood pressure value as an XmlString object.
     *
     * @return XmlString the systolic blood pressure as an XML string object, or null if not set
     */
    public XmlString xgetSystolic() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(BpImpl.SYSTOLIC$0, 0);
            return target;
        }
    }
    
    /**
     * Sets the systolic blood pressure value.
     *
     * @param systolic String the systolic blood pressure value to set
     */
    public void setSystolic(final String systolic) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BpImpl.SYSTOLIC$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(BpImpl.SYSTOLIC$0);
            }
            target.setStringValue(systolic);
        }
    }
    
    /**
     * Sets the systolic blood pressure value using an XmlString object.
     *
     * @param systolic XmlString the systolic blood pressure value as an XML string object
     */
    public void xsetSystolic(final XmlString systolic) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(BpImpl.SYSTOLIC$0, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(BpImpl.SYSTOLIC$0);
            }
            target.set((XmlObject)systolic);
        }
    }
    
    /**
     * Retrieves the diastolic blood pressure value.
     *
     * @return String the diastolic blood pressure value, or null if not set
     */
    public String getDiastolic() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BpImpl.DIASTOLIC$2, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }
    
    /**
     * Retrieves the diastolic blood pressure value as an XmlString object.
     *
     * @return XmlString the diastolic blood pressure as an XML string object, or null if not set
     */
    public XmlString xgetDiastolic() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(BpImpl.DIASTOLIC$2, 0);
            return target;
        }
    }
    
    /**
     * Sets the diastolic blood pressure value.
     *
     * @param diastolic String the diastolic blood pressure value to set
     */
    public void setDiastolic(final String diastolic) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BpImpl.DIASTOLIC$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(BpImpl.DIASTOLIC$2);
            }
            target.setStringValue(diastolic);
        }
    }
    
    /**
     * Sets the diastolic blood pressure value using an XmlString object.
     *
     * @param diastolic XmlString the diastolic blood pressure value as an XML string object
     */
    public void xsetDiastolic(final XmlString diastolic) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(BpImpl.DIASTOLIC$2, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(BpImpl.DIASTOLIC$2);
            }
            target.set((XmlObject)diastolic);
        }
    }
    
    static {
        // Initialize QName constants for XML element identification within CKD namespace
        SYSTOLIC$0 = new QName("http://www.oscarmcmaster.org/ckd", "systolic");
        DIASTOLIC$2 = new QName("http://www.oscarmcmaster.org/ckd", "diastolic");
    }
}
