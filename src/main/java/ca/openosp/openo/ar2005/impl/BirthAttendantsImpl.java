package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.BirthAttendants;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XML Beans implementation for the BirthAttendants element in the British Columbia Antenatal Record (BCAR) AR2005 form.
 *
 * <p>This class provides XML serialization and deserialization capabilities for tracking the healthcare professionals
 * who attended a birth. The BCAR AR2005 form is used throughout British Columbia for standardized prenatal care
 * documentation and captures essential information about pregnancy, delivery, and postnatal care.</p>
 *
 * <p>Birth attendants tracked by this implementation include:</p>
 * <ul>
 *   <li>OBS (Obstetrician) - Medical doctor specializing in pregnancy and childbirth</li>
 *   <li>FP (Family Physician) - General practitioner providing maternity care</li>
 *   <li>Midwife - Licensed midwife providing maternity care</li>
 *   <li>Other - Free-text field for other healthcare providers</li>
 * </ul>
 *
 * <p>This implementation extends Apache XMLBeans' XmlComplexContentImpl to provide type-safe access to the
 * XML structure defined in the AR2005 schema. All accessor methods are thread-safe through monitor synchronization
 * and interact with the underlying XML store through the XMLBeans framework.</p>
 *
 * @see ca.openosp.openo.ar2005.BirthAttendants
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-23
 */
public class BirthAttendantsImpl extends XmlComplexContentImpl implements BirthAttendants
{
    private static final long serialVersionUID = 1L;
    private static final QName OBS$0;
    private static final QName FP$2;
    private static final QName MIDWIFE$4;
    private static final QName OTHER$6;

    /**
     * Constructs a new BirthAttendantsImpl instance with the specified schema type.
     *
     * <p>This constructor is typically called by the Apache XMLBeans framework during XML parsing
     * and deserialization. It initializes the underlying XML store with the appropriate schema type
     * definition for the BirthAttendants element.</p>
     *
     * @param sType SchemaType the schema type definition for this XML element, must not be null
     */
    public BirthAttendantsImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves whether an obstetrician was present as a birth attendant.
     *
     * <p>Obstetricians are medical doctors who specialize in pregnancy, childbirth, and the postpartum period.
     * This method returns the primitive boolean value indicating presence or absence of an obstetrician
     * at the birth.</p>
     *
     * @return boolean true if an obstetrician attended the birth, false otherwise
     */
    public boolean getOBS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BirthAttendantsImpl.OBS$0, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XML representation of the OBS (obstetrician) birth attendant indicator.
     *
     * <p>This method returns the underlying XmlBoolean object rather than the primitive boolean value,
     * allowing access to XML-specific features such as nil values, validation state, and schema type information.
     * Used primarily for XML processing and serialization tasks.</p>
     *
     * @return XmlBoolean the XML boolean object representing obstetrician attendance, or null if not set
     */
    public XmlBoolean xgetOBS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(BirthAttendantsImpl.OBS$0, 0);
            return target;
        }
    }

    /**
     * Sets whether an obstetrician was present as a birth attendant.
     *
     * <p>This method updates the primitive boolean value in the underlying XML store. If the XML element
     * does not exist, it will be created automatically. This is thread-safe through monitor synchronization.</p>
     *
     * @param obs boolean true to indicate an obstetrician attended the birth, false otherwise
     */
    public void setOBS(final boolean obs) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BirthAttendantsImpl.OBS$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(BirthAttendantsImpl.OBS$0);
            }
            target.setBooleanValue(obs);
        }
    }

    /**
     * Sets the XML representation of the OBS (obstetrician) birth attendant indicator.
     *
     * <p>This method allows setting the value using an XmlBoolean object rather than a primitive boolean,
     * preserving XML-specific attributes such as nil state and validation metadata. The provided XmlBoolean
     * is copied into the underlying XML store. If the element does not exist, it will be created.</p>
     *
     * @param obs XmlBoolean the XML boolean object to set, must not be null
     */
    public void xsetOBS(final XmlBoolean obs) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(BirthAttendantsImpl.OBS$0, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(BirthAttendantsImpl.OBS$0);
            }
            target.set((XmlObject)obs);
        }
    }

    /**
     * Retrieves whether a family physician was present as a birth attendant.
     *
     * <p>Family physicians (FPs) are general practitioners who provide comprehensive maternity care including
     * prenatal visits, delivery, and postnatal care. This method returns the primitive boolean value
     * indicating presence or absence of a family physician at the birth.</p>
     *
     * @return boolean true if a family physician attended the birth, false otherwise
     */
    public boolean getFP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BirthAttendantsImpl.FP$2, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XML representation of the FP (family physician) birth attendant indicator.
     *
     * <p>This method returns the underlying XmlBoolean object rather than the primitive boolean value,
     * allowing access to XML-specific features such as nil values, validation state, and schema type information.
     * Used primarily for XML processing and serialization tasks.</p>
     *
     * @return XmlBoolean the XML boolean object representing family physician attendance, or null if not set
     */
    public XmlBoolean xgetFP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(BirthAttendantsImpl.FP$2, 0);
            return target;
        }
    }

    /**
     * Sets whether a family physician was present as a birth attendant.
     *
     * <p>This method updates the primitive boolean value in the underlying XML store. If the XML element
     * does not exist, it will be created automatically. This is thread-safe through monitor synchronization.</p>
     *
     * @param fp boolean true to indicate a family physician attended the birth, false otherwise
     */
    public void setFP(final boolean fp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BirthAttendantsImpl.FP$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(BirthAttendantsImpl.FP$2);
            }
            target.setBooleanValue(fp);
        }
    }

    /**
     * Sets the XML representation of the FP (family physician) birth attendant indicator.
     *
     * <p>This method allows setting the value using an XmlBoolean object rather than a primitive boolean,
     * preserving XML-specific attributes such as nil state and validation metadata. The provided XmlBoolean
     * is copied into the underlying XML store. If the element does not exist, it will be created.</p>
     *
     * @param fp XmlBoolean the XML boolean object to set, must not be null
     */
    public void xsetFP(final XmlBoolean fp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(BirthAttendantsImpl.FP$2, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(BirthAttendantsImpl.FP$2);
            }
            target.set((XmlObject)fp);
        }
    }

    /**
     * Retrieves whether a midwife was present as a birth attendant.
     *
     * <p>Midwives are healthcare professionals who specialize in pregnancy, childbirth, postpartum care,
     * and newborn care. In British Columbia, registered midwives are licensed primary care providers for
     * women during pregnancy and birth. This method returns the primitive boolean value indicating
     * presence or absence of a midwife at the birth.</p>
     *
     * @return boolean true if a midwife attended the birth, false otherwise
     */
    public boolean getMidwife() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BirthAttendantsImpl.MIDWIFE$4, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XML representation of the midwife birth attendant indicator.
     *
     * <p>This method returns the underlying XmlBoolean object rather than the primitive boolean value,
     * allowing access to XML-specific features such as nil values, validation state, and schema type information.
     * Used primarily for XML processing and serialization tasks.</p>
     *
     * @return XmlBoolean the XML boolean object representing midwife attendance, or null if not set
     */
    public XmlBoolean xgetMidwife() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(BirthAttendantsImpl.MIDWIFE$4, 0);
            return target;
        }
    }

    /**
     * Sets whether a midwife was present as a birth attendant.
     *
     * <p>This method updates the primitive boolean value in the underlying XML store. If the XML element
     * does not exist, it will be created automatically. This is thread-safe through monitor synchronization.</p>
     *
     * @param midwife boolean true to indicate a midwife attended the birth, false otherwise
     */
    public void setMidwife(final boolean midwife) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BirthAttendantsImpl.MIDWIFE$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(BirthAttendantsImpl.MIDWIFE$4);
            }
            target.setBooleanValue(midwife);
        }
    }

    /**
     * Sets the XML representation of the midwife birth attendant indicator.
     *
     * <p>This method allows setting the value using an XmlBoolean object rather than a primitive boolean,
     * preserving XML-specific attributes such as nil state and validation metadata. The provided XmlBoolean
     * is copied into the underlying XML store. If the element does not exist, it will be created.</p>
     *
     * @param midwife XmlBoolean the XML boolean object to set, must not be null
     */
    public void xsetMidwife(final XmlBoolean midwife) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(BirthAttendantsImpl.MIDWIFE$4, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(BirthAttendantsImpl.MIDWIFE$4);
            }
            target.set((XmlObject)midwife);
        }
    }

    /**
     * Retrieves the free-text description of other birth attendants not covered by standard categories.
     *
     * <p>This field allows documentation of healthcare providers or support persons who attended the birth
     * but do not fall into the standard categories of obstetrician, family physician, or midwife. Examples
     * might include doulas, nurse practitioners, resident physicians, or traditional birth attendants.</p>
     *
     * @return String the description of other birth attendants, or null if not specified
     */
    public String getOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BirthAttendantsImpl.OTHER$6, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the XML representation of the other birth attendants description.
     *
     * <p>This method returns the underlying XmlString object rather than the primitive String value,
     * allowing access to XML-specific features such as nil values, validation state, and schema type information.
     * Used primarily for XML processing and serialization tasks.</p>
     *
     * @return XmlString the XML string object representing other birth attendants, or null if not set
     */
    public XmlString xgetOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(BirthAttendantsImpl.OTHER$6, 0);
            return target;
        }
    }

    /**
     * Sets the free-text description of other birth attendants not covered by standard categories.
     *
     * <p>This method updates the string value in the underlying XML store. If the XML element does not exist,
     * it will be created automatically. This is thread-safe through monitor synchronization.</p>
     *
     * @param other String the description of other birth attendants, may be null
     */
    public void setOther(final String other) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(BirthAttendantsImpl.OTHER$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(BirthAttendantsImpl.OTHER$6);
            }
            target.setStringValue(other);
        }
    }

    /**
     * Sets the XML representation of the other birth attendants description.
     *
     * <p>This method allows setting the value using an XmlString object rather than a primitive String,
     * preserving XML-specific attributes such as nil state and validation metadata. The provided XmlString
     * is copied into the underlying XML store. If the element does not exist, it will be created.</p>
     *
     * @param other XmlString the XML string object to set, must not be null
     */
    public void xsetOther(final XmlString other) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(BirthAttendantsImpl.OTHER$6, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(BirthAttendantsImpl.OTHER$6);
            }
            target.set((XmlObject)other);
        }
    }
    
    static {
        OBS$0 = new QName("http://www.oscarmcmaster.org/AR2005", "OBS");
        FP$2 = new QName("http://www.oscarmcmaster.org/AR2005", "FP");
        MIDWIFE$4 = new QName("http://www.oscarmcmaster.org/AR2005", "Midwife");
        OTHER$6 = new QName("http://www.oscarmcmaster.org/AR2005", "Other");
    }
}
