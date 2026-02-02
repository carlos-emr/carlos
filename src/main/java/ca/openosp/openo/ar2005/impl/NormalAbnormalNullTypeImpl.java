package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.NormalAbnormalNullType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the NormalAbnormalNullType XML schema type for the BC AR2005 Antenatal Record system.
 *
 * <p>This class provides XML data binding functionality for tri-state clinical observation results
 * used in British Columbia's Antenatal Record 2005 (BCAR) form. It represents clinical findings
 * that can be classified as normal, abnormal, or null (not assessed/not applicable).</p>
 *
 * <p>The tri-state model is commonly used in prenatal care documentation to record:
 * <ul>
 *   <li><strong>Normal</strong> - Clinical finding is within expected parameters for pregnancy care</li>
 *   <li><strong>Abnormal</strong> - Clinical finding requires attention or follow-up</li>
 *   <li><strong>Null</strong> - Assessment not performed, not applicable, or data unavailable</li>
 * </ul>
 * </p>
 *
 * <p>This implementation extends Apache XMLBeans' XmlComplexContentImpl to provide thread-safe
 * XML serialization and deserialization for healthcare data exchange. All accessor methods are
 * synchronized to ensure data integrity in multi-threaded healthcare application environments.</p>
 *
 * <p><strong>XML Namespace:</strong> http://www.oscarmcmaster.org/AR2005</p>
 *
 * @see NormalAbnormalNullType
 * @see ca.openosp.openo.ar2005 AR2005 package documentation
 * @since 2026-01-23
 */
public class NormalAbnormalNullTypeImpl extends XmlComplexContentImpl implements NormalAbnormalNullType
{
    private static final long serialVersionUID = 1L;
    private static final QName NORMAL$0;
    private static final QName ABNORMAL$2;
    private static final QName NULL$4;

    /**
     * Constructs a new NormalAbnormalNullTypeImpl instance with the specified schema type.
     *
     * <p>This constructor is typically invoked by the Apache XMLBeans framework during
     * XML deserialization or when creating new instances via the Factory pattern. It
     * initializes the underlying XML object store with the appropriate schema type
     * definition for the AR2005 normal/abnormal/null type.</p>
     *
     * @param sType SchemaType the XML schema type definition for this instance
     */
    public NormalAbnormalNullTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the normal status indicator as a primitive boolean value.
     *
     * <p>This method returns the boolean value of the "normal" element in the XML document,
     * indicating whether the clinical observation falls within normal parameters for prenatal care.
     * The method is thread-safe and synchronized on the underlying XML object monitor.</p>
     *
     * @return boolean true if the observation is marked as normal, false if the element is not set or is false
     */
    public boolean getNormal() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.NORMAL$0, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the normal status indicator as an XmlBoolean object.
     *
     * <p>This method provides access to the underlying XML representation of the "normal"
     * element, allowing for XML schema validation and manipulation. Returns the XmlBoolean
     * object directly from the XML store, or null if the element is not set.</p>
     *
     * @return XmlBoolean the XML representation of the normal indicator, or null if not set
     */
    public XmlBoolean xgetNormal() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.NORMAL$0, 0);
            return target;
        }
    }

    /**
     * Checks whether the normal element has been explicitly set in the XML document.
     *
     * <p>This method determines if the "normal" element is present in the underlying XML store,
     * regardless of its boolean value. Useful for distinguishing between "false" and "not set"
     * states in tri-state clinical observation recording.</p>
     *
     * @return boolean true if the normal element is present in the XML document, false otherwise
     */
    public boolean isSetNormal() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(NormalAbnormalNullTypeImpl.NORMAL$0) != 0;
        }
    }

    /**
     * Sets the normal status indicator to the specified boolean value.
     *
     * <p>This method updates the "normal" element in the XML document to indicate whether
     * the clinical observation is within normal parameters for prenatal care. If the element
     * does not exist, it is created in the XML store. The operation is thread-safe.</p>
     *
     * @param normal boolean true to mark the observation as normal, false otherwise
     */
    public void setNormal(final boolean normal) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.NORMAL$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(NormalAbnormalNullTypeImpl.NORMAL$0);
            }
            target.setBooleanValue(normal);
        }
    }

    /**
     * Sets the normal status indicator using an XmlBoolean object.
     *
     * <p>This method provides XML schema-aware setting of the "normal" element by accepting
     * an XmlBoolean object. This allows for preserving XML attributes and schema validation
     * metadata during the update. If the element does not exist, it is created in the XML store.</p>
     *
     * @param normal XmlBoolean the XML representation of the normal indicator to set
     */
    public void xsetNormal(final XmlBoolean normal) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.NORMAL$0, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(NormalAbnormalNullTypeImpl.NORMAL$0);
            }
            target.set((XmlObject)normal);
        }
    }

    /**
     * Removes the normal element from the XML document.
     *
     * <p>This method deletes the "normal" element from the underlying XML store, effectively
     * resetting this component of the tri-state observation. After calling this method,
     * isSetNormal() will return false. Use this to indicate that the normal status has not
     * been assessed or is not applicable.</p>
     */
    public void unsetNormal() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(NormalAbnormalNullTypeImpl.NORMAL$0, 0);
        }
    }

    /**
     * Retrieves the abnormal status indicator as a primitive boolean value.
     *
     * <p>This method returns the boolean value of the "abnormal" element in the XML document,
     * indicating whether the clinical observation falls outside normal parameters and requires
     * attention or follow-up care. The method is thread-safe and synchronized on the underlying
     * XML object monitor.</p>
     *
     * @return boolean true if the observation is marked as abnormal, false if the element is not set or is false
     */
    public boolean getAbnormal() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.ABNORMAL$2, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the abnormal status indicator as an XmlBoolean object.
     *
     * <p>This method provides access to the underlying XML representation of the "abnormal"
     * element, allowing for XML schema validation and manipulation. Returns the XmlBoolean
     * object directly from the XML store, or null if the element is not set.</p>
     *
     * @return XmlBoolean the XML representation of the abnormal indicator, or null if not set
     */
    public XmlBoolean xgetAbnormal() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.ABNORMAL$2, 0);
            return target;
        }
    }

    /**
     * Checks whether the abnormal element has been explicitly set in the XML document.
     *
     * <p>This method determines if the "abnormal" element is present in the underlying XML store,
     * regardless of its boolean value. Useful for distinguishing between "false" and "not set"
     * states in tri-state clinical observation recording.</p>
     *
     * @return boolean true if the abnormal element is present in the XML document, false otherwise
     */
    public boolean isSetAbnormal() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(NormalAbnormalNullTypeImpl.ABNORMAL$2) != 0;
        }
    }

    /**
     * Sets the abnormal status indicator to the specified boolean value.
     *
     * <p>This method updates the "abnormal" element in the XML document to indicate whether
     * the clinical observation falls outside normal parameters and requires attention or
     * follow-up care. If the element does not exist, it is created in the XML store.
     * The operation is thread-safe.</p>
     *
     * @param abnormal boolean true to mark the observation as abnormal, false otherwise
     */
    public void setAbnormal(final boolean abnormal) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.ABNORMAL$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(NormalAbnormalNullTypeImpl.ABNORMAL$2);
            }
            target.setBooleanValue(abnormal);
        }
    }

    /**
     * Sets the abnormal status indicator using an XmlBoolean object.
     *
     * <p>This method provides XML schema-aware setting of the "abnormal" element by accepting
     * an XmlBoolean object. This allows for preserving XML attributes and schema validation
     * metadata during the update. If the element does not exist, it is created in the XML store.</p>
     *
     * @param abnormal XmlBoolean the XML representation of the abnormal indicator to set
     */
    public void xsetAbnormal(final XmlBoolean abnormal) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.ABNORMAL$2, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(NormalAbnormalNullTypeImpl.ABNORMAL$2);
            }
            target.set((XmlObject)abnormal);
        }
    }

    /**
     * Removes the abnormal element from the XML document.
     *
     * <p>This method deletes the "abnormal" element from the underlying XML store, effectively
     * resetting this component of the tri-state observation. After calling this method,
     * isSetAbnormal() will return false. Use this to indicate that the abnormal status has not
     * been assessed or is not applicable.</p>
     */
    public void unsetAbnormal() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(NormalAbnormalNullTypeImpl.ABNORMAL$2, 0);
        }
    }

    /**
     * Retrieves the null status indicator as a primitive boolean value.
     *
     * <p>This method returns the boolean value of the "null" element in the XML document,
     * indicating whether the clinical observation was not assessed, is not applicable, or
     * data is unavailable. The method is thread-safe and synchronized on the underlying
     * XML object monitor.</p>
     *
     * @return boolean true if the observation is marked as null/not assessed, false if the element is not set or is false
     */
    public boolean getNull() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.NULL$4, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the null status indicator as an XmlBoolean object.
     *
     * <p>This method provides access to the underlying XML representation of the "null"
     * element, allowing for XML schema validation and manipulation. Returns the XmlBoolean
     * object directly from the XML store, or null if the element is not set.</p>
     *
     * @return XmlBoolean the XML representation of the null indicator, or null if not set
     */
    public XmlBoolean xgetNull() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.NULL$4, 0);
            return target;
        }
    }

    /**
     * Checks whether the null element has been explicitly set in the XML document.
     *
     * <p>This method determines if the "null" element is present in the underlying XML store,
     * regardless of its boolean value. Useful for distinguishing between "false" and "not set"
     * states in tri-state clinical observation recording.</p>
     *
     * @return boolean true if the null element is present in the XML document, false otherwise
     */
    public boolean isSetNull() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(NormalAbnormalNullTypeImpl.NULL$4) != 0;
        }
    }

    /**
     * Sets the null status indicator to the specified boolean value.
     *
     * <p>This method updates the "null" element in the XML document to indicate whether
     * the clinical observation was not assessed, is not applicable, or data is unavailable.
     * If the element does not exist, it is created in the XML store. The operation is thread-safe.</p>
     *
     * @param xnull boolean true to mark the observation as null/not assessed, false otherwise
     */
    public void setNull(final boolean xnull) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.NULL$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(NormalAbnormalNullTypeImpl.NULL$4);
            }
            target.setBooleanValue(xnull);
        }
    }

    /**
     * Sets the null status indicator using an XmlBoolean object.
     *
     * <p>This method provides XML schema-aware setting of the "null" element by accepting
     * an XmlBoolean object. This allows for preserving XML attributes and schema validation
     * metadata during the update. If the element does not exist, it is created in the XML store.</p>
     *
     * @param xnull XmlBoolean the XML representation of the null indicator to set
     */
    public void xsetNull(final XmlBoolean xnull) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(NormalAbnormalNullTypeImpl.NULL$4, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(NormalAbnormalNullTypeImpl.NULL$4);
            }
            target.set((XmlObject)xnull);
        }
    }

    /**
     * Removes the null element from the XML document.
     *
     * <p>This method deletes the "null" element from the underlying XML store, effectively
     * resetting this component of the tri-state observation. After calling this method,
     * isSetNull() will return false. Use this when removing a previously set null/not assessed
     * status.</p>
     */
    public void unsetNull() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(NormalAbnormalNullTypeImpl.NULL$4, 0);
        }
    }
    
    static {
        NORMAL$0 = new QName("http://www.oscarmcmaster.org/AR2005", "normal");
        ABNORMAL$2 = new QName("http://www.oscarmcmaster.org/AR2005", "abnormal");
        NULL$4 = new QName("http://www.oscarmcmaster.org/AR2005", "null");
    }
}
