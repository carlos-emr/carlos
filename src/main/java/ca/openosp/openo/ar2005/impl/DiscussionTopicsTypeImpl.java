package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.DiscussionTopicsType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of prenatal discussion topics for the Antenatal Record (AR2005) healthcare form.
 *
 * This class provides the concrete implementation of the DiscussionTopicsType interface, representing
 * a comprehensive set of prenatal discussion topics that healthcare providers should cover during
 * prenatal care visits in the British Columbia Antenatal Record (BCAR) system.
 *
 * The implementation uses Apache XMLBeans framework to provide XML-based persistence and type-safe
 * access to 21 different discussion topic categories covering the full spectrum of prenatal care:
 *
 * <h3>Pregnancy Lifestyle Topics:</h3>
 * <ul>
 *   <li>Exercise during pregnancy</li>
 *   <li>Work planning and maternity leave</li>
 *   <li>Sexual intercourse safety</li>
 *   <li>Travel restrictions and guidelines</li>
 * </ul>
 *
 * <h3>Prenatal Education:</h3>
 * <ul>
 *   <li>Prenatal classes availability</li>
 *   <li>Birth plan development</li>
 *   <li>On-call provider information</li>
 * </ul>
 *
 * <h3>Medical Warning Signs:</h3>
 * <ul>
 *   <li>Preterm labour recognition</li>
 *   <li>PROM (Premature Rupture of Membranes)</li>
 *   <li>APH (Antepartum Hemorrhage)</li>
 *   <li>Fetal movement monitoring</li>
 * </ul>
 *
 * <h3>Labour and Delivery:</h3>
 * <ul>
 *   <li>Admission timing to hospital</li>
 *   <li>Pain management options</li>
 *   <li>Labour support persons</li>
 * </ul>
 *
 * <h3>Newborn Care:</h3>
 * <ul>
 *   <li>Breastfeeding and infant feeding</li>
 *   <li>Circumcision decision-making</li>
 *   <li>Car seat safety requirements</li>
 *   <li>Discharge planning</li>
 * </ul>
 *
 * <h3>Postpartum Topics:</h3>
 * <ul>
 *   <li>Depression screening and mental health</li>
 *   <li>Contraception options</li>
 *   <li>Postpartum care and follow-up</li>
 * </ul>
 *
 * Each topic is represented as a boolean flag stored in XML format, allowing healthcare providers
 * to track which educational discussions have been completed with each patient. This tracking
 * supports comprehensive prenatal care documentation and helps ensure all important topics are
 * addressed during the course of pregnancy.
 *
 * The class extends XmlComplexContentImpl to leverage Apache XMLBeans' XML serialization,
 * deserialization, and validation capabilities. All property access is thread-safe through
 * synchronization on internal monitors, and changes are persisted to the underlying XML store.
 *
 * This implementation is generated from XML schema definitions and should not be modified directly.
 * Changes to the discussion topics structure should be made in the XML schema and regenerated.
 *
 * @see DiscussionTopicsType
 * @see XmlComplexContentImpl
 * @see XmlBoolean
 * @since 2026-01-24
 */
public class DiscussionTopicsTypeImpl extends XmlComplexContentImpl implements DiscussionTopicsType
{
    private static final long serialVersionUID = 1L;
    private static final QName EXERCISE$0;
    private static final QName WORKPLAN$2;
    private static final QName INTERCOURSE$4;
    private static final QName TRAVEL$6;
    private static final QName PRENATALCLASSES$8;
    private static final QName BIRTHPLAN$10;
    private static final QName ONCALLPROVIDERS$12;
    private static final QName PRETERMLABOUR$14;
    private static final QName PROM$16;
    private static final QName APH$18;
    private static final QName FETALMOVEMENT$20;
    private static final QName ADMISSIONTIMING$22;
    private static final QName PAINMANAGEMENT$24;
    private static final QName LABOURSUPPORT$26;
    private static final QName BREASTFEEDING$28;
    private static final QName CIRCUMCISION$30;
    private static final QName DISCHARGEPLANNING$32;
    private static final QName CARSEATSAFETY$34;
    private static final QName DEPRESSION$36;
    private static final QName CONTRACEPTION$38;
    private static final QName POSTPARTUMCARE$40;

    /**
     * Constructs a new DiscussionTopicsTypeImpl instance with the specified schema type.
     *
     * This constructor initializes the XMLBeans complex content implementation with the provided
     * schema type, enabling type-safe XML processing for prenatal discussion topics.
     *
     * @param sType SchemaType the schema type definition for this XML complex type
     */
    public DiscussionTopicsTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the exercise discussion flag indicating whether exercise during pregnancy has been discussed.
     *
     * This method retrieves the boolean value from the underlying XML store indicating whether
     * the healthcare provider has discussed exercise and physical activity guidelines during pregnancy
     * with the patient.
     *
     * @return boolean true if exercise topic has been discussed, false otherwise
     */
    public boolean getExercise() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.EXERCISE$0, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the exercise discussion flag as an XmlBoolean object.
     *
     * This method retrieves the exercise discussion flag as an Apache XMLBeans XmlBoolean object,
     * providing access to the underlying XML representation and additional XMLBeans functionality.
     *
     * @return XmlBoolean the exercise discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetExercise() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.EXERCISE$0, 0);
            return target;
        }
    }

    /**
     * Sets the exercise discussion flag.
     *
     * This method updates the boolean value in the underlying XML store to indicate whether
     * the exercise topic has been discussed with the patient. If the element does not exist
     * in the XML document, it will be created.
     *
     * @param exercise boolean true if exercise topic has been discussed, false otherwise
     */
    public void setExercise(final boolean exercise) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.EXERCISE$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.EXERCISE$0);
            }
            target.setBooleanValue(exercise);
        }
    }

    /**
     * Sets the exercise discussion flag using an XmlBoolean object.
     *
     * This method updates the exercise discussion flag using an Apache XMLBeans XmlBoolean object,
     * allowing for XML-aware updates with schema validation. If the element does not exist
     * in the XML document, it will be created.
     *
     * @param exercise XmlBoolean the exercise discussion flag as an XML boolean
     */
    public void xsetExercise(final XmlBoolean exercise) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.EXERCISE$0, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.EXERCISE$0);
            }
            target.set((XmlObject)exercise);
        }
    }

    /**
     * Gets the work plan discussion flag indicating whether work planning during pregnancy has been discussed.
     *
     * This method retrieves the boolean value from the underlying XML store indicating whether
     * the healthcare provider has discussed work planning, maternity leave, and workplace safety
     * during pregnancy with the patient.
     *
     * @return boolean true if work plan topic has been discussed, false otherwise
     */
    public boolean getWorkPlan() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.WORKPLAN$2, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the work plan discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the work plan discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetWorkPlan() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.WORKPLAN$2, 0);
            return target;
        }
    }

    /**
     * Sets the work plan discussion flag.
     *
     * @param workPlan boolean true if work plan topic has been discussed, false otherwise
     */
    public void setWorkPlan(final boolean workPlan) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.WORKPLAN$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.WORKPLAN$2);
            }
            target.setBooleanValue(workPlan);
        }
    }

    /**
     * Sets the work plan discussion flag using an XmlBoolean object.
     *
     * @param workPlan XmlBoolean the work plan discussion flag as an XML boolean
     */
    public void xsetWorkPlan(final XmlBoolean workPlan) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.WORKPLAN$2, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.WORKPLAN$2);
            }
            target.set((XmlObject)workPlan);
        }
    }

    /**
     * Gets the intercourse discussion flag indicating whether sexual intercourse during pregnancy has been discussed.
     *
     * @return boolean true if intercourse topic has been discussed, false otherwise
     */
    public boolean getIntercourse() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.INTERCOURSE$4, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the intercourse discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the intercourse discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetIntercourse() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.INTERCOURSE$4, 0);
            return target;
        }
    }

    /**
     * Sets the intercourse discussion flag.
     *
     * @param intercourse boolean true if intercourse topic has been discussed, false otherwise
     */
    public void setIntercourse(final boolean intercourse) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.INTERCOURSE$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.INTERCOURSE$4);
            }
            target.setBooleanValue(intercourse);
        }
    }

    /**
     * Sets the intercourse discussion flag using an XmlBoolean object.
     *
     * @param intercourse XmlBoolean the intercourse discussion flag as an XML boolean
     */
    public void xsetIntercourse(final XmlBoolean intercourse) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.INTERCOURSE$4, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.INTERCOURSE$4);
            }
            target.set((XmlObject)intercourse);
        }
    }

    /**
     * Gets the travel discussion flag indicating whether travel during pregnancy has been discussed.
     *
     * @return boolean true if travel topic has been discussed, false otherwise
     */
    public boolean getTravel() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.TRAVEL$6, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the travel discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the travel discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetTravel() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.TRAVEL$6, 0);
            return target;
        }
    }

    /**
     * Sets the travel discussion flag.
     *
     * @param travel boolean true if travel topic has been discussed, false otherwise
     */
    public void setTravel(final boolean travel) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.TRAVEL$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.TRAVEL$6);
            }
            target.setBooleanValue(travel);
        }
    }

    /**
     * Sets the travel discussion flag using an XmlBoolean object.
     *
     * @param travel XmlBoolean the travel discussion flag as an XML boolean
     */
    public void xsetTravel(final XmlBoolean travel) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.TRAVEL$6, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.TRAVEL$6);
            }
            target.set((XmlObject)travel);
        }
    }

    /**
     * Gets the prenatal classes discussion flag indicating whether prenatal education classes have been discussed.
     *
     * @return boolean true if prenatal classes topic has been discussed, false otherwise
     */
    public boolean getPrenatalClasses() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PRENATALCLASSES$8, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the prenatal classes discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the prenatal classes discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetPrenatalClasses() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PRENATALCLASSES$8, 0);
            return target;
        }
    }

    /**
     * Sets the prenatal classes discussion flag.
     *
     * @param prenatalClasses boolean true if prenatal classes topic has been discussed, false otherwise
     */
    public void setPrenatalClasses(final boolean prenatalClasses) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PRENATALCLASSES$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.PRENATALCLASSES$8);
            }
            target.setBooleanValue(prenatalClasses);
        }
    }

    /**
     * Sets the prenatal classes discussion flag using an XmlBoolean object.
     *
     * @param prenatalClasses XmlBoolean the prenatal classes discussion flag as an XML boolean
     */
    public void xsetPrenatalClasses(final XmlBoolean prenatalClasses) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PRENATALCLASSES$8, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.PRENATALCLASSES$8);
            }
            target.set((XmlObject)prenatalClasses);
        }
    }

    /**
     * Gets the birth plan discussion flag indicating whether birth planning has been discussed.
     *
     * @return boolean true if birth plan topic has been discussed, false otherwise
     */
    public boolean getBirthPlan() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.BIRTHPLAN$10, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the birth plan discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the birth plan discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetBirthPlan() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.BIRTHPLAN$10, 0);
            return target;
        }
    }

    /**
     * Sets the birth plan discussion flag.
     *
     * @param birthPlan boolean true if birth plan topic has been discussed, false otherwise
     */
    public void setBirthPlan(final boolean birthPlan) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.BIRTHPLAN$10, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.BIRTHPLAN$10);
            }
            target.setBooleanValue(birthPlan);
        }
    }

    /**
     * Sets the birth plan discussion flag using an XmlBoolean object.
     *
     * @param birthPlan XmlBoolean the birth plan discussion flag as an XML boolean
     */
    public void xsetBirthPlan(final XmlBoolean birthPlan) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.BIRTHPLAN$10, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.BIRTHPLAN$10);
            }
            target.set((XmlObject)birthPlan);
        }
    }

    /**
     * Gets the on-call providers discussion flag indicating whether on-call provider information has been discussed.
     *
     * @return boolean true if on-call providers topic has been discussed, false otherwise
     */
    public boolean getOnCallProviders() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.ONCALLPROVIDERS$12, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the on-call providers discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the on-call providers discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetOnCallProviders() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.ONCALLPROVIDERS$12, 0);
            return target;
        }
    }

    /**
     * Sets the on-call providers discussion flag.
     *
     * @param onCallProviders boolean true if on-call providers topic has been discussed, false otherwise
     */
    public void setOnCallProviders(final boolean onCallProviders) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.ONCALLPROVIDERS$12, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.ONCALLPROVIDERS$12);
            }
            target.setBooleanValue(onCallProviders);
        }
    }

    /**
     * Sets the on-call providers discussion flag using an XmlBoolean object.
     *
     * @param onCallProviders XmlBoolean the on-call providers discussion flag as an XML boolean
     */
    public void xsetOnCallProviders(final XmlBoolean onCallProviders) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.ONCALLPROVIDERS$12, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.ONCALLPROVIDERS$12);
            }
            target.set((XmlObject)onCallProviders);
        }
    }

    /**
     * Gets the preterm labour discussion flag indicating whether signs and risks of preterm labour have been discussed.
     *
     * @return boolean true if preterm labour topic has been discussed, false otherwise
     */
    public boolean getPretermLabour() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PRETERMLABOUR$14, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the preterm labour discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the preterm labour discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetPretermLabour() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PRETERMLABOUR$14, 0);
            return target;
        }
    }

    /**
     * Sets the preterm labour discussion flag.
     *
     * @param pretermLabour boolean true if preterm labour topic has been discussed, false otherwise
     */
    public void setPretermLabour(final boolean pretermLabour) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PRETERMLABOUR$14, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.PRETERMLABOUR$14);
            }
            target.setBooleanValue(pretermLabour);
        }
    }

    /**
     * Sets the preterm labour discussion flag using an XmlBoolean object.
     *
     * @param pretermLabour XmlBoolean the preterm labour discussion flag as an XML boolean
     */
    public void xsetPretermLabour(final XmlBoolean pretermLabour) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PRETERMLABOUR$14, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.PRETERMLABOUR$14);
            }
            target.set((XmlObject)pretermLabour);
        }
    }

    /**
     * Gets the PROM discussion flag indicating whether Premature Rupture of Membranes has been discussed.
     *
     * @return boolean true if PROM topic has been discussed, false otherwise
     */
    public boolean getPROM() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PROM$16, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the PROM discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the PROM discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetPROM() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PROM$16, 0);
            return target;
        }
    }

    /**
     * Sets the PROM discussion flag.
     *
     * @param prom boolean true if PROM topic has been discussed, false otherwise
     */
    public void setPROM(final boolean prom) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PROM$16, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.PROM$16);
            }
            target.setBooleanValue(prom);
        }
    }

    /**
     * Sets the PROM discussion flag using an XmlBoolean object.
     *
     * @param prom XmlBoolean the PROM discussion flag as an XML boolean
     */
    public void xsetPROM(final XmlBoolean prom) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PROM$16, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.PROM$16);
            }
            target.set((XmlObject)prom);
        }
    }

    /**
     * Gets the APH discussion flag indicating whether Antepartum Hemorrhage has been discussed.
     *
     * @return boolean true if APH topic has been discussed, false otherwise
     */
    public boolean getAPH() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.APH$18, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the APH discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the APH discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetAPH() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.APH$18, 0);
            return target;
        }
    }

    /**
     * Sets the APH discussion flag.
     *
     * @param aph boolean true if APH topic has been discussed, false otherwise
     */
    public void setAPH(final boolean aph) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.APH$18, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.APH$18);
            }
            target.setBooleanValue(aph);
        }
    }

    /**
     * Sets the APH discussion flag using an XmlBoolean object.
     *
     * @param aph XmlBoolean the APH discussion flag as an XML boolean
     */
    public void xsetAPH(final XmlBoolean aph) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.APH$18, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.APH$18);
            }
            target.set((XmlObject)aph);
        }
    }

    /**
     * Gets the fetal movement discussion flag indicating whether monitoring fetal movement has been discussed.
     *
     * @return boolean true if fetal movement topic has been discussed, false otherwise
     */
    public boolean getFetalMovement() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.FETALMOVEMENT$20, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the fetal movement discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the fetal movement discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetFetalMovement() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.FETALMOVEMENT$20, 0);
            return target;
        }
    }

    /**
     * Sets the fetal movement discussion flag.
     *
     * @param fetalMovement boolean true if fetal movement topic has been discussed, false otherwise
     */
    public void setFetalMovement(final boolean fetalMovement) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.FETALMOVEMENT$20, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.FETALMOVEMENT$20);
            }
            target.setBooleanValue(fetalMovement);
        }
    }

    /**
     * Sets the fetal movement discussion flag using an XmlBoolean object.
     *
     * @param fetalMovement XmlBoolean the fetal movement discussion flag as an XML boolean
     */
    public void xsetFetalMovement(final XmlBoolean fetalMovement) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.FETALMOVEMENT$20, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.FETALMOVEMENT$20);
            }
            target.set((XmlObject)fetalMovement);
        }
    }

    /**
     * Gets the admission timing discussion flag indicating whether when to come to hospital for labour has been discussed.
     *
     * @return boolean true if admission timing topic has been discussed, false otherwise
     */
    public boolean getAdmissionTiming() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.ADMISSIONTIMING$22, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the admission timing discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the admission timing discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetAdmissionTiming() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.ADMISSIONTIMING$22, 0);
            return target;
        }
    }

    /**
     * Sets the admission timing discussion flag.
     *
     * @param admissionTiming boolean true if admission timing topic has been discussed, false otherwise
     */
    public void setAdmissionTiming(final boolean admissionTiming) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.ADMISSIONTIMING$22, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.ADMISSIONTIMING$22);
            }
            target.setBooleanValue(admissionTiming);
        }
    }

    /**
     * Sets the admission timing discussion flag using an XmlBoolean object.
     *
     * @param admissionTiming XmlBoolean the admission timing discussion flag as an XML boolean
     */
    public void xsetAdmissionTiming(final XmlBoolean admissionTiming) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.ADMISSIONTIMING$22, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.ADMISSIONTIMING$22);
            }
            target.set((XmlObject)admissionTiming);
        }
    }

    /**
     * Gets the pain management discussion flag indicating whether labour pain management options have been discussed.
     *
     * @return boolean true if pain management topic has been discussed, false otherwise
     */
    public boolean getPainManagement() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PAINMANAGEMENT$24, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the pain management discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the pain management discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetPainManagement() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PAINMANAGEMENT$24, 0);
            return target;
        }
    }

    /**
     * Sets the pain management discussion flag.
     *
     * @param painManagement boolean true if pain management topic has been discussed, false otherwise
     */
    public void setPainManagement(final boolean painManagement) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PAINMANAGEMENT$24, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.PAINMANAGEMENT$24);
            }
            target.setBooleanValue(painManagement);
        }
    }

    /**
     * Sets the pain management discussion flag using an XmlBoolean object.
     *
     * @param painManagement XmlBoolean the pain management discussion flag as an XML boolean
     */
    public void xsetPainManagement(final XmlBoolean painManagement) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.PAINMANAGEMENT$24, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.PAINMANAGEMENT$24);
            }
            target.set((XmlObject)painManagement);
        }
    }

    /**
     * Gets the labour support discussion flag indicating whether support persons during labour have been discussed.
     *
     * @return boolean true if labour support topic has been discussed, false otherwise
     */
    public boolean getLabourSupport() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.LABOURSUPPORT$26, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the labour support discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the labour support discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetLabourSupport() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.LABOURSUPPORT$26, 0);
            return target;
        }
    }

    /**
     * Sets the labour support discussion flag.
     *
     * @param labourSupport boolean true if labour support topic has been discussed, false otherwise
     */
    public void setLabourSupport(final boolean labourSupport) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.LABOURSUPPORT$26, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.LABOURSUPPORT$26);
            }
            target.setBooleanValue(labourSupport);
        }
    }

    /**
     * Sets the labour support discussion flag using an XmlBoolean object.
     *
     * @param labourSupport XmlBoolean the labour support discussion flag as an XML boolean
     */
    public void xsetLabourSupport(final XmlBoolean labourSupport) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.LABOURSUPPORT$26, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.LABOURSUPPORT$26);
            }
            target.set((XmlObject)labourSupport);
        }
    }

    /**
     * Gets the breastfeeding discussion flag indicating whether breastfeeding and infant feeding options have been discussed.
     *
     * @return boolean true if breastfeeding topic has been discussed, false otherwise
     */
    public boolean getBreastFeeding() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.BREASTFEEDING$28, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the breastfeeding discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the breastfeeding discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetBreastFeeding() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.BREASTFEEDING$28, 0);
            return target;
        }
    }

    /**
     * Sets the breastfeeding discussion flag.
     *
     * @param breastFeeding boolean true if breastfeeding topic has been discussed, false otherwise
     */
    public void setBreastFeeding(final boolean breastFeeding) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.BREASTFEEDING$28, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.BREASTFEEDING$28);
            }
            target.setBooleanValue(breastFeeding);
        }
    }

    /**
     * Sets the breastfeeding discussion flag using an XmlBoolean object.
     *
     * @param breastFeeding XmlBoolean the breastfeeding discussion flag as an XML boolean
     */
    public void xsetBreastFeeding(final XmlBoolean breastFeeding) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.BREASTFEEDING$28, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.BREASTFEEDING$28);
            }
            target.set((XmlObject)breastFeeding);
        }
    }

    /**
     * Gets the circumcision discussion flag indicating whether newborn circumcision has been discussed.
     *
     * @return boolean true if circumcision topic has been discussed, false otherwise
     */
    public boolean getCircumcision() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CIRCUMCISION$30, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the circumcision discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the circumcision discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetCircumcision() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CIRCUMCISION$30, 0);
            return target;
        }
    }

    /**
     * Sets the circumcision discussion flag.
     *
     * @param circumcision boolean true if circumcision topic has been discussed, false otherwise
     */
    public void setCircumcision(final boolean circumcision) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CIRCUMCISION$30, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.CIRCUMCISION$30);
            }
            target.setBooleanValue(circumcision);
        }
    }

    /**
     * Sets the circumcision discussion flag using an XmlBoolean object.
     *
     * @param circumcision XmlBoolean the circumcision discussion flag as an XML boolean
     */
    public void xsetCircumcision(final XmlBoolean circumcision) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CIRCUMCISION$30, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.CIRCUMCISION$30);
            }
            target.set((XmlObject)circumcision);
        }
    }

    /**
     * Gets the discharge planning discussion flag indicating whether hospital discharge planning has been discussed.
     *
     * @return boolean true if discharge planning topic has been discussed, false otherwise
     */
    public boolean getDischargePlanning() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.DISCHARGEPLANNING$32, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the discharge planning discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the discharge planning discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetDischargePlanning() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.DISCHARGEPLANNING$32, 0);
            return target;
        }
    }

    /**
     * Sets the discharge planning discussion flag.
     *
     * @param dischargePlanning boolean true if discharge planning topic has been discussed, false otherwise
     */
    public void setDischargePlanning(final boolean dischargePlanning) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.DISCHARGEPLANNING$32, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.DISCHARGEPLANNING$32);
            }
            target.setBooleanValue(dischargePlanning);
        }
    }

    /**
     * Sets the discharge planning discussion flag using an XmlBoolean object.
     *
     * @param dischargePlanning XmlBoolean the discharge planning discussion flag as an XML boolean
     */
    public void xsetDischargePlanning(final XmlBoolean dischargePlanning) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.DISCHARGEPLANNING$32, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.DISCHARGEPLANNING$32);
            }
            target.set((XmlObject)dischargePlanning);
        }
    }

    /**
     * Gets the car seat safety discussion flag indicating whether infant car seat safety has been discussed.
     *
     * @return boolean true if car seat safety topic has been discussed, false otherwise
     */
    public boolean getCarSeatSafety() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CARSEATSAFETY$34, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the car seat safety discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the car seat safety discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetCarSeatSafety() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CARSEATSAFETY$34, 0);
            return target;
        }
    }

    /**
     * Sets the car seat safety discussion flag.
     *
     * @param carSeatSafety boolean true if car seat safety topic has been discussed, false otherwise
     */
    public void setCarSeatSafety(final boolean carSeatSafety) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CARSEATSAFETY$34, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.CARSEATSAFETY$34);
            }
            target.setBooleanValue(carSeatSafety);
        }
    }

    /**
     * Sets the car seat safety discussion flag using an XmlBoolean object.
     *
     * @param carSeatSafety XmlBoolean the car seat safety discussion flag as an XML boolean
     */
    public void xsetCarSeatSafety(final XmlBoolean carSeatSafety) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CARSEATSAFETY$34, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.CARSEATSAFETY$34);
            }
            target.set((XmlObject)carSeatSafety);
        }
    }

    /**
     * Gets the depression discussion flag indicating whether postpartum depression screening has been discussed.
     *
     * @return boolean true if depression topic has been discussed, false otherwise
     */
    public boolean getDepression() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.DEPRESSION$36, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the depression discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the depression discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetDepression() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.DEPRESSION$36, 0);
            return target;
        }
    }

    /**
     * Sets the depression discussion flag.
     *
     * @param depression boolean true if depression topic has been discussed, false otherwise
     */
    public void setDepression(final boolean depression) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.DEPRESSION$36, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.DEPRESSION$36);
            }
            target.setBooleanValue(depression);
        }
    }

    /**
     * Sets the depression discussion flag using an XmlBoolean object.
     *
     * @param depression XmlBoolean the depression discussion flag as an XML boolean
     */
    public void xsetDepression(final XmlBoolean depression) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.DEPRESSION$36, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.DEPRESSION$36);
            }
            target.set((XmlObject)depression);
        }
    }

    /**
     * Gets the contraception discussion flag indicating whether postpartum contraception options have been discussed.
     *
     * @return boolean true if contraception topic has been discussed, false otherwise
     */
    public boolean getContraception() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CONTRACEPTION$38, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the contraception discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the contraception discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetContraception() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CONTRACEPTION$38, 0);
            return target;
        }
    }

    /**
     * Sets the contraception discussion flag.
     *
     * @param contraception boolean true if contraception topic has been discussed, false otherwise
     */
    public void setContraception(final boolean contraception) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CONTRACEPTION$38, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.CONTRACEPTION$38);
            }
            target.setBooleanValue(contraception);
        }
    }

    /**
     * Sets the contraception discussion flag using an XmlBoolean object.
     *
     * @param contraception XmlBoolean the contraception discussion flag as an XML boolean
     */
    public void xsetContraception(final XmlBoolean contraception) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.CONTRACEPTION$38, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.CONTRACEPTION$38);
            }
            target.set((XmlObject)contraception);
        }
    }

    /**
     * Gets the postpartum care discussion flag indicating whether postpartum care and follow-up have been discussed.
     *
     * @return boolean true if postpartum care topic has been discussed, false otherwise
     */
    public boolean getPostpartumCare() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.POSTPARTUMCARE$40, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the postpartum care discussion flag as an XmlBoolean object.
     *
     * @return XmlBoolean the postpartum care discussion flag as an XML boolean, or null if not set
     */
    public XmlBoolean xgetPostpartumCare() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.POSTPARTUMCARE$40, 0);
            return target;
        }
    }

    /**
     * Sets the postpartum care discussion flag.
     *
     * @param postpartumCare boolean true if postpartum care topic has been discussed, false otherwise
     */
    public void setPostpartumCare(final boolean postpartumCare) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DiscussionTopicsTypeImpl.POSTPARTUMCARE$40, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DiscussionTopicsTypeImpl.POSTPARTUMCARE$40);
            }
            target.setBooleanValue(postpartumCare);
        }
    }

    /**
     * Sets the postpartum care discussion flag using an XmlBoolean object.
     *
     * @param postpartumCare XmlBoolean the postpartum care discussion flag as an XML boolean
     */
    public void xsetPostpartumCare(final XmlBoolean postpartumCare) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DiscussionTopicsTypeImpl.POSTPARTUMCARE$40, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DiscussionTopicsTypeImpl.POSTPARTUMCARE$40);
            }
            target.set((XmlObject)postpartumCare);
        }
    }
    
    static {
        EXERCISE$0 = new QName("http://www.oscarmcmaster.org/AR2005", "exercise");
        WORKPLAN$2 = new QName("http://www.oscarmcmaster.org/AR2005", "workPlan");
        INTERCOURSE$4 = new QName("http://www.oscarmcmaster.org/AR2005", "intercourse");
        TRAVEL$6 = new QName("http://www.oscarmcmaster.org/AR2005", "travel");
        PRENATALCLASSES$8 = new QName("http://www.oscarmcmaster.org/AR2005", "prenatalClasses");
        BIRTHPLAN$10 = new QName("http://www.oscarmcmaster.org/AR2005", "birthPlan");
        ONCALLPROVIDERS$12 = new QName("http://www.oscarmcmaster.org/AR2005", "onCallProviders");
        PRETERMLABOUR$14 = new QName("http://www.oscarmcmaster.org/AR2005", "pretermLabour");
        PROM$16 = new QName("http://www.oscarmcmaster.org/AR2005", "PROM");
        APH$18 = new QName("http://www.oscarmcmaster.org/AR2005", "APH");
        FETALMOVEMENT$20 = new QName("http://www.oscarmcmaster.org/AR2005", "fetalMovement");
        ADMISSIONTIMING$22 = new QName("http://www.oscarmcmaster.org/AR2005", "admissionTiming");
        PAINMANAGEMENT$24 = new QName("http://www.oscarmcmaster.org/AR2005", "painManagement");
        LABOURSUPPORT$26 = new QName("http://www.oscarmcmaster.org/AR2005", "labourSupport");
        BREASTFEEDING$28 = new QName("http://www.oscarmcmaster.org/AR2005", "breastFeeding");
        CIRCUMCISION$30 = new QName("http://www.oscarmcmaster.org/AR2005", "circumcision");
        DISCHARGEPLANNING$32 = new QName("http://www.oscarmcmaster.org/AR2005", "dischargePlanning");
        CARSEATSAFETY$34 = new QName("http://www.oscarmcmaster.org/AR2005", "carSeatSafety");
        DEPRESSION$36 = new QName("http://www.oscarmcmaster.org/AR2005", "depression");
        CONTRACEPTION$38 = new QName("http://www.oscarmcmaster.org/AR2005", "contraception");
        POSTPARTUMCARE$40 = new QName("http://www.oscarmcmaster.org/AR2005", "postpartumCare");
    }
}
