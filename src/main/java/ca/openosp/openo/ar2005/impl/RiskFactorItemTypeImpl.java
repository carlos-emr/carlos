package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.RiskFactorItemType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Apache XMLBeans implementation for AR2005 (Antenatal Record 2005) risk factor item type.
 *
 * This class provides the concrete implementation of the RiskFactorItemType interface,
 * representing a patient risk factor and its associated plan of management in the context
 * of antenatal care. The AR2005 standard is used for structured electronic antenatal
 * records in Canadian healthcare settings.
 *
 * The implementation uses Apache XMLBeans for XML serialization/deserialization,
 * providing both standard Java String accessors and XMLBeans-specific XmlString
 * accessors for each property. All methods are thread-safe through synchronized
 * access to the underlying XMLBeans store.
 *
 * @since 2026-01-24
 * @see RiskFactorItemType
 * @see XmlComplexContentImpl
 */
public class RiskFactorItemTypeImpl extends XmlComplexContentImpl implements RiskFactorItemType
{
    private static final long serialVersionUID = 1L;
    private static final QName RISKFACTOR$0;
    private static final QName PLANOFMANAGEMENT$2;

    /**
     * Constructs a new RiskFactorItemTypeImpl with the specified schema type.
     *
     * This constructor initializes the XMLBeans implementation with the provided
     * schema type definition, which defines the structure and validation rules
     * for risk factor items in the AR2005 antenatal record format.
     *
     * @param sType SchemaType the schema type definition for this XML element
     */
    public RiskFactorItemTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the risk factor description as a standard Java String.
     *
     * Retrieves the clinical risk factor identified during antenatal assessment
     * (e.g., "gestational diabetes", "hypertension", "previous cesarean section").
     * This method provides thread-safe access to the underlying XML element value.
     *
     * @return String the risk factor description, or null if not set
     */
    public String getRiskFactor() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RiskFactorItemTypeImpl.RISKFACTOR$0, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the risk factor description as an XMLBeans XmlString object.
     *
     * Provides low-level access to the risk factor element as an XmlString,
     * which allows access to XML-specific metadata, attributes, and validation
     * information not available through the standard String accessor.
     *
     * @return XmlString the risk factor as an XmlString object, or null if not set
     */
    public XmlString xgetRiskFactor() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(RiskFactorItemTypeImpl.RISKFACTOR$0, 0);
            return target;
        }
    }

    /**
     * Sets the risk factor description from a standard Java String.
     *
     * Updates the clinical risk factor identified during antenatal assessment.
     * If the element does not exist in the XML structure, it will be created.
     * This method provides thread-safe write access to the underlying XML element.
     *
     * @param riskFactor String the risk factor description to set
     */
    public void setRiskFactor(final String riskFactor) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RiskFactorItemTypeImpl.RISKFACTOR$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(RiskFactorItemTypeImpl.RISKFACTOR$0);
            }
            target.setStringValue(riskFactor);
        }
    }

    /**
     * Sets the risk factor description from an XMLBeans XmlString object.
     *
     * Provides low-level write access to the risk factor element using an XmlString,
     * which preserves XML-specific metadata, attributes, and validation information.
     * If the element does not exist in the XML structure, it will be created.
     *
     * @param riskFactor XmlString the risk factor as an XmlString object to set
     */
    public void xsetRiskFactor(final XmlString riskFactor) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(RiskFactorItemTypeImpl.RISKFACTOR$0, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(RiskFactorItemTypeImpl.RISKFACTOR$0);
            }
            target.set((XmlObject)riskFactor);
        }
    }

    /**
     * Gets the plan of management as a standard Java String.
     *
     * Retrieves the clinical plan for managing the identified risk factor during
     * pregnancy and delivery (e.g., "monitor blood glucose levels weekly",
     * "scheduled cesarean section at 39 weeks", "refer to high-risk obstetrics").
     * This method provides thread-safe access to the underlying XML element value.
     *
     * @return String the plan of management description, or null if not set
     */
    public String getPlanOfManagement() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RiskFactorItemTypeImpl.PLANOFMANAGEMENT$2, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the plan of management as an XMLBeans XmlString object.
     *
     * Provides low-level access to the plan of management element as an XmlString,
     * which allows access to XML-specific metadata, attributes, and validation
     * information not available through the standard String accessor.
     *
     * @return XmlString the plan of management as an XmlString object, or null if not set
     */
    public XmlString xgetPlanOfManagement() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(RiskFactorItemTypeImpl.PLANOFMANAGEMENT$2, 0);
            return target;
        }
    }

    /**
     * Sets the plan of management from a standard Java String.
     *
     * Updates the clinical plan for managing the identified risk factor during
     * pregnancy and delivery. If the element does not exist in the XML structure,
     * it will be created. This method provides thread-safe write access to the
     * underlying XML element.
     *
     * @param planOfManagement String the plan of management description to set
     */
    public void setPlanOfManagement(final String planOfManagement) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RiskFactorItemTypeImpl.PLANOFMANAGEMENT$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(RiskFactorItemTypeImpl.PLANOFMANAGEMENT$2);
            }
            target.setStringValue(planOfManagement);
        }
    }

    /**
     * Sets the plan of management from an XMLBeans XmlString object.
     *
     * Provides low-level write access to the plan of management element using
     * an XmlString, which preserves XML-specific metadata, attributes, and
     * validation information. If the element does not exist in the XML structure,
     * it will be created.
     *
     * @param planOfManagement XmlString the plan of management as an XmlString object to set
     */
    public void xsetPlanOfManagement(final XmlString planOfManagement) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(RiskFactorItemTypeImpl.PLANOFMANAGEMENT$2, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(RiskFactorItemTypeImpl.PLANOFMANAGEMENT$2);
            }
            target.set((XmlObject)planOfManagement);
        }
    }
    
    static {
        RISKFACTOR$0 = new QName("http://www.oscarmcmaster.org/AR2005", "riskFactor");
        PLANOFMANAGEMENT$2 = new QName("http://www.oscarmcmaster.org/AR2005", "planOfManagement");
    }
}
