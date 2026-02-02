package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.SignatureType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of SignatureType for AR2005 (Antenatal Record 2005) XML schema handling.
 *
 * <p>This class provides the concrete implementation of the SignatureType interface, managing
 * healthcare provider signatures and associated dates for medical documentation. The AR2005
 * standard is used in Canadian healthcare for antenatal records, supporting dual signature
 * capabilities (primary and secondary) with corresponding date fields.</p>
 *
 * <p>The implementation extends Apache XMLBeans' XmlComplexContentImpl to provide XML
 * serialization/deserialization capabilities for signature data, ensuring proper handling
 * of healthcare documentation requirements including audit trails and compliance tracking.</p>
 *
 * <p>Thread Safety: All public methods are synchronized on the underlying XMLBeans monitor
 * to ensure thread-safe access to the XML store.</p>
 *
 * @see SignatureType
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-23
 */
public class SignatureTypeImpl extends XmlComplexContentImpl implements SignatureType
{
    private static final long serialVersionUID = 1L;
    private static final QName SIGNATURE$0;
    private static final QName DATE$2;
    private static final QName SIGNATURE2$4;
    private static final QName DATE2$6;

    /**
     * Constructs a new SignatureTypeImpl with the specified schema type.
     *
     * @param sType SchemaType the schema type definition for this signature element
     */
    public SignatureTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the primary signature value.
     *
     * <p>Returns the string representation of the primary healthcare provider's signature.
     * This is typically used to capture the electronic signature of the attending physician
     * or primary care provider.</p>
     *
     * @return String the primary signature value, or null if not set
     */
    public String getSignature() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SignatureTypeImpl.SIGNATURE$0, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the primary signature as an XmlString object.
     *
     * <p>Provides access to the underlying XMLBeans representation of the signature field,
     * allowing for advanced XML manipulation and validation operations.</p>
     *
     * @return XmlString the primary signature XML object, or null if not set
     */
    public XmlString xgetSignature() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SignatureTypeImpl.SIGNATURE$0, 0);
            return target;
        }
    }

    /**
     * Sets the primary signature value.
     *
     * <p>Stores the electronic signature of the primary healthcare provider. This method
     * creates the signature element if it doesn't exist, or updates it if already present.</p>
     *
     * @param signature String the signature value to set
     */
    public void setSignature(final String signature) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SignatureTypeImpl.SIGNATURE$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SignatureTypeImpl.SIGNATURE$0);
            }
            target.setStringValue(signature);
        }
    }

    /**
     * Sets the primary signature using an XmlString object.
     *
     * <p>Advanced setter method that accepts an XmlString for scenarios requiring
     * detailed XML validation or when the signature comes from another XMLBeans object.</p>
     *
     * @param signature XmlString the XML string object containing the signature value
     */
    public void xsetSignature(final XmlString signature) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SignatureTypeImpl.SIGNATURE$0, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(SignatureTypeImpl.SIGNATURE$0);
            }
            target.set((XmlObject)signature);
        }
    }

    /**
     * Retrieves the date associated with the primary signature.
     *
     * <p>Returns the timestamp when the primary signature was captured, essential for
     * audit trails and legal compliance in healthcare documentation.</p>
     *
     * @return Calendar the date/time of the primary signature, or null if not set
     */
    public Calendar getDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SignatureTypeImpl.DATE$2, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Retrieves the primary signature date as an XmlDate object.
     *
     * <p>Provides access to the underlying XMLBeans date representation for advanced
     * date validation and XML manipulation operations.</p>
     *
     * @return XmlDate the primary signature date XML object, or null if not set
     */
    public XmlDate xgetDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(SignatureTypeImpl.DATE$2, 0);
            return target;
        }
    }

    /**
     * Sets the date associated with the primary signature.
     *
     * <p>Records the timestamp when the primary healthcare provider signed the document.
     * Creates the date element if it doesn't exist, or updates it if already present.</p>
     *
     * @param date Calendar the date/time to associate with the primary signature
     */
    public void setDate(final Calendar date) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SignatureTypeImpl.DATE$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SignatureTypeImpl.DATE$2);
            }
            target.setCalendarValue(date);
        }
    }

    /**
     * Sets the primary signature date using an XmlDate object.
     *
     * <p>Advanced setter method for scenarios requiring detailed XML date validation
     * or when the date comes from another XMLBeans object.</p>
     *
     * @param date XmlDate the XML date object containing the signature date
     */
    public void xsetDate(final XmlDate date) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(SignatureTypeImpl.DATE$2, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(SignatureTypeImpl.DATE$2);
            }
            target.set((XmlObject)date);
        }
    }

    /**
     * Retrieves the secondary signature value.
     *
     * <p>Returns the string representation of a secondary healthcare provider's signature.
     * This optional field supports scenarios requiring co-signatures or witness signatures
     * in medical documentation.</p>
     *
     * @return String the secondary signature value, or null if not set
     */
    public String getSignature2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SignatureTypeImpl.SIGNATURE2$4, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the secondary signature as an XmlString object.
     *
     * <p>Provides access to the underlying XMLBeans representation of the secondary
     * signature field for advanced XML operations.</p>
     *
     * @return XmlString the secondary signature XML object, or null if not set
     */
    public XmlString xgetSignature2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SignatureTypeImpl.SIGNATURE2$4, 0);
            return target;
        }
    }

    /**
     * Checks whether the secondary signature field has been set.
     *
     * <p>Useful for distinguishing between an explicitly null signature and one that
     * has never been set, important for validation and compliance checking.</p>
     *
     * @return boolean true if the secondary signature element exists, false otherwise
     */
    public boolean isSetSignature2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(SignatureTypeImpl.SIGNATURE2$4) != 0;
        }
    }

    /**
     * Sets the secondary signature value.
     *
     * <p>Stores the electronic signature of a secondary healthcare provider, supervisor,
     * or witness. Creates the signature element if it doesn't exist, or updates it if
     * already present.</p>
     *
     * @param signature2 String the secondary signature value to set
     */
    public void setSignature2(final String signature2) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SignatureTypeImpl.SIGNATURE2$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SignatureTypeImpl.SIGNATURE2$4);
            }
            target.setStringValue(signature2);
        }
    }

    /**
     * Sets the secondary signature using an XmlString object.
     *
     * <p>Advanced setter method for XML-aware signature assignment with validation
     * capabilities.</p>
     *
     * @param signature2 XmlString the XML string object containing the secondary signature value
     */
    public void xsetSignature2(final XmlString signature2) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SignatureTypeImpl.SIGNATURE2$4, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(SignatureTypeImpl.SIGNATURE2$4);
            }
            target.set((XmlObject)signature2);
        }
    }

    /**
     * Removes the secondary signature field.
     *
     * <p>Completely removes the secondary signature element from the XML document,
     * useful for scenarios where a co-signature is revoked or no longer applicable.</p>
     */
    public void unsetSignature2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(SignatureTypeImpl.SIGNATURE2$4, 0);
        }
    }

    /**
     * Retrieves the date associated with the secondary signature.
     *
     * <p>Returns the timestamp when the secondary signature was captured, supporting
     * audit requirements for co-signed medical documentation.</p>
     *
     * @return Calendar the date/time of the secondary signature, or null if not set
     */
    public Calendar getDate2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SignatureTypeImpl.DATE2$6, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Retrieves the secondary signature date as an XmlDate object.
     *
     * <p>Provides access to the underlying XMLBeans date representation for the
     * secondary signature timestamp.</p>
     *
     * @return XmlDate the secondary signature date XML object, or null if not set
     */
    public XmlDate xgetDate2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(SignatureTypeImpl.DATE2$6, 0);
            return target;
        }
    }

    /**
     * Checks whether the secondary signature date field has been set.
     *
     * <p>Determines if the secondary signature date element exists in the XML document,
     * essential for validation workflows.</p>
     *
     * @return boolean true if the secondary signature date element exists, false otherwise
     */
    public boolean isSetDate2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(SignatureTypeImpl.DATE2$6) != 0;
        }
    }

    /**
     * Sets the date associated with the secondary signature.
     *
     * <p>Records the timestamp when the secondary healthcare provider signed the document.
     * Creates the date element if it doesn't exist, or updates it if already present.</p>
     *
     * @param date2 Calendar the date/time to associate with the secondary signature
     */
    public void setDate2(final Calendar date2) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SignatureTypeImpl.DATE2$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SignatureTypeImpl.DATE2$6);
            }
            target.setCalendarValue(date2);
        }
    }

    /**
     * Sets the secondary signature date using an XmlDate object.
     *
     * <p>Advanced setter method for XML-aware date assignment with validation capabilities.</p>
     *
     * @param date2 XmlDate the XML date object containing the secondary signature date
     */
    public void xsetDate2(final XmlDate date2) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(SignatureTypeImpl.DATE2$6, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(SignatureTypeImpl.DATE2$6);
            }
            target.set((XmlObject)date2);
        }
    }

    /**
     * Removes the secondary signature date field.
     *
     * <p>Completely removes the secondary signature date element from the XML document.</p>
     */
    public void unsetDate2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(SignatureTypeImpl.DATE2$6, 0);
        }
    }

    static {
        SIGNATURE$0 = new QName("http://www.oscarmcmaster.org/AR2005", "signature");
        DATE$2 = new QName("http://www.oscarmcmaster.org/AR2005", "date");
        SIGNATURE2$4 = new QName("http://www.oscarmcmaster.org/AR2005", "signature2");
        DATE2$6 = new QName("http://www.oscarmcmaster.org/AR2005", "date2");
    }
}
