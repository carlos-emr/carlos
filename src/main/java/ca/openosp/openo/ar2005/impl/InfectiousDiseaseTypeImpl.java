package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.YesNoNullType;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.InfectiousDiseaseType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of InfectiousDiseaseType for tracking infectious disease history in patient records.
 * <p>
 * This class provides XMLBeans-based data binding for infectious disease information as defined
 * in the AR2005 (Antenatal Record 2005) healthcare standard. It manages patient history for
 * specific infectious diseases including varicella (chickenpox), sexually transmitted diseases (STD),
 * tuberculosis, and other infectious conditions.
 * </p>
 * <p>
 * The implementation uses thread-safe synchronized blocks for all data access operations and
 * maintains element state through XMLBeans internal storage mechanisms. Each disease field
 * supports Yes/No/Null tri-state values to distinguish between confirmed negative history,
 * confirmed positive history, and unknown/unrecorded status.
 * </p>
 * <p>
 * This class is typically used within antenatal care forms and patient health history documentation
 * to track infectious disease exposure and status for risk assessment and care planning purposes.
 * </p>
 *
 * @see ca.openosp.openo.ar2005.InfectiousDiseaseType
 * @see ca.openosp.openo.ar2005.YesNoNullType
 * @since 2026-01-24
 */
public class InfectiousDiseaseTypeImpl extends XmlComplexContentImpl implements InfectiousDiseaseType
{
    private static final long serialVersionUID = 1L;
    private static final QName VARICELLA$0;
    private static final QName STD$2;
    private static final QName TUBERCULOSIS$4;
    private static final QName OTHERDESCR$6;
    private static final QName OTHER$8;

    /**
     * Constructs a new InfectiousDiseaseTypeImpl instance with the specified schema type.
     * <p>
     * This constructor is typically invoked by the XMLBeans framework during XML parsing
     * and object instantiation. It initializes the internal XMLBeans complex content structure
     * with the provided schema type definition.
     * </p>
     *
     * @param sType SchemaType the schema type definition for this infectious disease type element
     */
    public InfectiousDiseaseTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the varicella (chickenpox) disease status from the patient's infectious disease history.
     * <p>
     * This method retrieves the current varicella status indicator using thread-safe access
     * to the underlying XMLBeans element store. The returned value indicates whether the
     * patient has a history of varicella infection.
     * </p>
     *
     * @return YesNoNullType the varicella status (Yes/No/Null), or null if not set
     */
    public YesNoNullType getVaricella() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.VARICELLA$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the varicella (chickenpox) disease status in the patient's infectious disease history.
     * <p>
     * This method updates the varicella status indicator using thread-safe access to the
     * underlying XMLBeans element store. If no varicella element exists, it will be created
     * automatically before setting the value.
     * </p>
     *
     * @param varicella YesNoNullType the varicella status to set (Yes/No/Null)
     */
    public void setVaricella(final YesNoNullType varicella) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.VARICELLA$0, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.VARICELLA$0);
            }
            target.set((XmlObject)varicella);
        }
    }

    /**
     * Creates and adds a new varicella element to the infectious disease history.
     * <p>
     * This method creates a new YesNoNullType element for varicella status in the
     * underlying XMLBeans element store using thread-safe operations. The newly created
     * element can then be populated with the appropriate status value.
     * </p>
     *
     * @return YesNoNullType the newly created varicella element
     */
    public YesNoNullType addNewVaricella() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.VARICELLA$0);
            return target;
        }
    }

    /**
     * Gets the sexually transmitted disease (STD) status from the patient's infectious disease history.
     * <p>
     * This method retrieves the current STD status indicator using thread-safe access
     * to the underlying XMLBeans element store. The returned value indicates whether the
     * patient has a history of sexually transmitted disease infection.
     * </p>
     *
     * @return YesNoNullType the STD status (Yes/No/Null), or null if not set
     */
    public YesNoNullType getStd() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.STD$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the sexually transmitted disease (STD) status in the patient's infectious disease history.
     * <p>
     * This method updates the STD status indicator using thread-safe access to the
     * underlying XMLBeans element store. If no STD element exists, it will be created
     * automatically before setting the value.
     * </p>
     *
     * @param std YesNoNullType the STD status to set (Yes/No/Null)
     */
    public void setStd(final YesNoNullType std) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.STD$2, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.STD$2);
            }
            target.set((XmlObject)std);
        }
    }

    /**
     * Creates and adds a new STD element to the infectious disease history.
     * <p>
     * This method creates a new YesNoNullType element for STD status in the
     * underlying XMLBeans element store using thread-safe operations. The newly created
     * element can then be populated with the appropriate status value.
     * </p>
     *
     * @return YesNoNullType the newly created STD element
     */
    public YesNoNullType addNewStd() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.STD$2);
            return target;
        }
    }

    /**
     * Gets the tuberculosis disease status from the patient's infectious disease history.
     * <p>
     * This method retrieves the current tuberculosis status indicator using thread-safe access
     * to the underlying XMLBeans element store. The returned value indicates whether the
     * patient has a history of tuberculosis infection.
     * </p>
     *
     * @return YesNoNullType the tuberculosis status (Yes/No/Null), or null if not set
     */
    public YesNoNullType getTuberculosis() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.TUBERCULOSIS$4, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the tuberculosis disease status in the patient's infectious disease history.
     * <p>
     * This method updates the tuberculosis status indicator using thread-safe access to the
     * underlying XMLBeans element store. If no tuberculosis element exists, it will be created
     * automatically before setting the value.
     * </p>
     *
     * @param tuberculosis YesNoNullType the tuberculosis status to set (Yes/No/Null)
     */
    public void setTuberculosis(final YesNoNullType tuberculosis) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.TUBERCULOSIS$4, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.TUBERCULOSIS$4);
            }
            target.set((XmlObject)tuberculosis);
        }
    }

    /**
     * Creates and adds a new tuberculosis element to the infectious disease history.
     * <p>
     * This method creates a new YesNoNullType element for tuberculosis status in the
     * underlying XMLBeans element store using thread-safe operations. The newly created
     * element can then be populated with the appropriate status value.
     * </p>
     *
     * @return YesNoNullType the newly created tuberculosis element
     */
    public YesNoNullType addNewTuberculosis() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.TUBERCULOSIS$4);
            return target;
        }
    }

    /**
     * Gets the description of other infectious diseases from the patient's history.
     * <p>
     * This method retrieves the free-text description field for other infectious diseases
     * not specifically enumerated in the standard disease fields (varicella, STD, tuberculosis).
     * Uses thread-safe access to the underlying XMLBeans element store.
     * </p>
     *
     * @return String the description of other infectious diseases, or null if not set
     */
    public String getOtherDescr() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.OTHERDESCR$6, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the XML-typed description of other infectious diseases from the patient's history.
     * <p>
     * This method retrieves the XmlString representation of the free-text description field
     * for other infectious diseases. This XMLBeans-specific accessor provides access to the
     * underlying XML element with full type information and validation capabilities.
     * </p>
     *
     * @return XmlString the XML-typed description element, or null if not set
     */
    public XmlString xgetOtherDescr() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.OTHERDESCR$6, 0);
            return target;
        }
    }

    /**
     * Sets the description of other infectious diseases in the patient's history.
     * <p>
     * This method updates the free-text description field for other infectious diseases
     * not specifically enumerated in the standard disease fields. Uses thread-safe access
     * to the underlying XMLBeans element store. If no otherDescr element exists, it will
     * be created automatically before setting the value.
     * </p>
     *
     * @param otherDescr String the description of other infectious diseases to set
     */
    public void setOtherDescr(final String otherDescr) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.OTHERDESCR$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.OTHERDESCR$6);
            }
            target.setStringValue(otherDescr);
        }
    }

    /**
     * Sets the XML-typed description of other infectious diseases in the patient's history.
     * <p>
     * This method updates the free-text description field using an XmlString parameter,
     * providing XMLBeans-specific type information and validation. Uses thread-safe access
     * to the underlying element store. If no otherDescr element exists, it will be created
     * automatically before setting the value.
     * </p>
     *
     * @param otherDescr XmlString the XML-typed description element to set
     */
    public void xsetOtherDescr(final XmlString otherDescr) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.OTHERDESCR$6, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.OTHERDESCR$6);
            }
            target.set((XmlObject)otherDescr);
        }
    }

    /**
     * Gets the presence indicator for other infectious diseases from the patient's history.
     * <p>
     * This method retrieves the Yes/No/Null indicator for other infectious diseases
     * (those not specifically enumerated in the standard disease fields). This complements
     * the otherDescr field by providing a structured tri-state status indicator.
     * Uses thread-safe access to the underlying XMLBeans element store.
     * </p>
     *
     * @return YesNoNullType the other diseases status (Yes/No/Null), or null if not set
     */
    public YesNoNullType getOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.OTHER$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the presence indicator for other infectious diseases in the patient's history.
     * <p>
     * This method updates the Yes/No/Null indicator for other infectious diseases
     * (those not specifically enumerated in the standard disease fields). This complements
     * the otherDescr field by providing a structured tri-state status indicator.
     * Uses thread-safe access to the underlying XMLBeans element store. If no other element
     * exists, it will be created automatically before setting the value.
     * </p>
     *
     * @param other YesNoNullType the other diseases status to set (Yes/No/Null)
     */
    public void setOther(final YesNoNullType other) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(InfectiousDiseaseTypeImpl.OTHER$8, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.OTHER$8);
            }
            target.set((XmlObject)other);
        }
    }

    /**
     * Creates and adds a new other diseases element to the infectious disease history.
     * <p>
     * This method creates a new YesNoNullType element for other diseases status in the
     * underlying XMLBeans element store using thread-safe operations. The newly created
     * element can then be populated with the appropriate status value to indicate the
     * presence or absence of other infectious diseases.
     * </p>
     *
     * @return YesNoNullType the newly created other diseases element
     */
    public YesNoNullType addNewOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(InfectiousDiseaseTypeImpl.OTHER$8);
            return target;
        }
    }
    
    static {
        VARICELLA$0 = new QName("http://www.oscarmcmaster.org/AR2005", "varicella");
        STD$2 = new QName("http://www.oscarmcmaster.org/AR2005", "std");
        TUBERCULOSIS$4 = new QName("http://www.oscarmcmaster.org/AR2005", "tuberculosis");
        OTHERDESCR$6 = new QName("http://www.oscarmcmaster.org/AR2005", "otherDescr");
        OTHER$8 = new QName("http://www.oscarmcmaster.org/AR2005", "other");
    }
}
