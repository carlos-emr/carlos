package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.impl.values.JavaStringHolderEx;
import org.apache.xmlbeans.impl.values.JavaFloatHolderEx;
import org.apache.xmlbeans.XmlString;
import ca.openosp.openo.ar2005.NormalAbnormalNullType;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.PhysicalExaminationType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the PhysicalExaminationType interface for the British Columbia Antenatal Record (BCAR) 2005 form.
 *
 * This class provides XML binding implementation for physical examination data collected during prenatal care.
 * It manages vital signs and comprehensive physical examination findings including cardiovascular, respiratory,
 * abdominal, and obstetric/gynecological assessments. All data is stored and accessed through Apache XMLBeans
 * framework for XML serialization and deserialization.
 *
 * The physical examination includes:
 * - Vital measurements: height, weight, BMI (Body Mass Index), blood pressure
 * - General examination: thyroid, chest, breasts, cardiovascular system
 * - Abdominal examination: includes assessment for pregnancy-related changes
 * - Obstetric examination: external genitals, cervix, vagina, uterus (size and status), adnexa
 * - Additional findings: varicosities and other observations
 *
 * Each examination component can be marked as Normal, Abnormal, or Null using {@link NormalAbnormalNullType}.
 * Measurements are stored as float values with support for nil/null states to distinguish between zero values
 * and missing data.
 *
 * Thread Safety: All public methods are synchronized on the internal monitor to ensure thread-safe access
 * to the underlying XML store.
 *
 * @see PhysicalExaminationType
 * @see NormalAbnormalNullType
 * @see XmlComplexContentImpl
 * @since 2026-01-24
 */
public class PhysicalExaminationTypeImpl extends XmlComplexContentImpl implements PhysicalExaminationType
{
    private static final long serialVersionUID = 1L;
    private static final QName HEIGHT$0;
    private static final QName WEIGHT$2;
    private static final QName BMI$4;
    private static final QName BP$6;
    private static final QName THYROID$8;
    private static final QName CHEST$10;
    private static final QName BREASTS$12;
    private static final QName CARDIOVASCULAR$14;
    private static final QName ABDOMEN$16;
    private static final QName VARICOSITIES$18;
    private static final QName EXERNALGENITALS$20;
    private static final QName CERVIXVAGINA$22;
    private static final QName UTERUS$24;
    private static final QName UTERUSSIZE$26;
    private static final QName ADNEXA$28;
    private static final QName OTHERDESCR$30;
    private static final QName OTHER$32;

    /**
     * Constructs a new PhysicalExaminationTypeImpl instance.
     *
     * @param sType SchemaType the schema type definition for this XML element
     */
    public PhysicalExaminationTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the patient's height value.
     *
     * @return float the height measurement in centimeters, or 0.0f if not set
     */
    public float getHeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.HEIGHT$0, 0);
            if (target == null) {
                return 0.0f;
            }
            return target.getFloatValue();
        }
    }

    /**
     * Gets the height value as an XML type object.
     *
     * @return Height the XML representation of the height value, or null if not set
     */
    public Height xgetHeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Height target = null;
            target = (Height)this.get_store().find_element_user(PhysicalExaminationTypeImpl.HEIGHT$0, 0);
            return target;
        }
    }

    /**
     * Checks if the height value is explicitly set to nil/null.
     *
     * @return boolean true if the height is explicitly nil, false otherwise
     */
    public boolean isNilHeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Height target = null;
            target = (Height)this.get_store().find_element_user(PhysicalExaminationTypeImpl.HEIGHT$0, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the patient's height value.
     *
     * @param height float the height measurement in centimeters
     */
    public void setHeight(final float height) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.HEIGHT$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PhysicalExaminationTypeImpl.HEIGHT$0);
            }
            target.setFloatValue(height);
        }
    }

    /**
     * Sets the height value using an XML type object.
     *
     * @param height Height the XML representation of the height value
     */
    public void xsetHeight(final Height height) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Height target = null;
            target = (Height)this.get_store().find_element_user(PhysicalExaminationTypeImpl.HEIGHT$0, 0);
            if (target == null) {
                target = (Height)this.get_store().add_element_user(PhysicalExaminationTypeImpl.HEIGHT$0);
            }
            target.set((XmlObject)height);
        }
    }

    /**
     * Sets the height value to nil/null, indicating the value is explicitly absent.
     */
    public void setNilHeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Height target = null;
            target = (Height)this.get_store().find_element_user(PhysicalExaminationTypeImpl.HEIGHT$0, 0);
            if (target == null) {
                target = (Height)this.get_store().add_element_user(PhysicalExaminationTypeImpl.HEIGHT$0);
            }
            target.setNil();
        }
    }

    /**
     * Gets the patient's weight value.
     *
     * @return float the weight measurement in kilograms, or 0.0f if not set
     */
    public float getWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.WEIGHT$2, 0);
            if (target == null) {
                return 0.0f;
            }
            return target.getFloatValue();
        }
    }

    /**
     * Gets the weight value as an XML type object.
     *
     * @return Weight the XML representation of the weight value, or null if not set
     */
    public Weight xgetWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Weight target = null;
            target = (Weight)this.get_store().find_element_user(PhysicalExaminationTypeImpl.WEIGHT$2, 0);
            return target;
        }
    }

    /**
     * Checks if the weight value is explicitly set to nil/null.
     *
     * @return boolean true if the weight is explicitly nil, false otherwise
     */
    public boolean isNilWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Weight target = null;
            target = (Weight)this.get_store().find_element_user(PhysicalExaminationTypeImpl.WEIGHT$2, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the patient's weight value.
     *
     * @param weight float the weight measurement in kilograms
     */
    public void setWeight(final float weight) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.WEIGHT$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PhysicalExaminationTypeImpl.WEIGHT$2);
            }
            target.setFloatValue(weight);
        }
    }

    /**
     * Sets the weight value using an XML type object.
     *
     * @param weight Weight the XML representation of the weight value
     */
    public void xsetWeight(final Weight weight) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Weight target = null;
            target = (Weight)this.get_store().find_element_user(PhysicalExaminationTypeImpl.WEIGHT$2, 0);
            if (target == null) {
                target = (Weight)this.get_store().add_element_user(PhysicalExaminationTypeImpl.WEIGHT$2);
            }
            target.set((XmlObject)weight);
        }
    }

    /**
     * Sets the weight value to nil/null, indicating the value is explicitly absent.
     */
    public void setNilWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Weight target = null;
            target = (Weight)this.get_store().find_element_user(PhysicalExaminationTypeImpl.WEIGHT$2, 0);
            if (target == null) {
                target = (Weight)this.get_store().add_element_user(PhysicalExaminationTypeImpl.WEIGHT$2);
            }
            target.setNil();
        }
    }

    /**
     * Gets the patient's Body Mass Index (BMI) value.
     *
     * @return float the BMI value (weight in kg / height in m²), or 0.0f if not set
     */
    public float getBmi() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BMI$4, 0);
            if (target == null) {
                return 0.0f;
            }
            return target.getFloatValue();
        }
    }

    /**
     * Gets the BMI value as an XML type object.
     *
     * @return Bmi the XML representation of the BMI value, or null if not set
     */
    public Bmi xgetBmi() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bmi target = null;
            target = (Bmi)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BMI$4, 0);
            return target;
        }
    }

    /**
     * Checks if the BMI value is explicitly set to nil/null.
     *
     * @return boolean true if the BMI is explicitly nil, false otherwise
     */
    public boolean isNilBmi() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bmi target = null;
            target = (Bmi)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BMI$4, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the patient's Body Mass Index (BMI) value.
     *
     * @param bmi float the BMI value (weight in kg / height in m²)
     */
    public void setBmi(final float bmi) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BMI$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PhysicalExaminationTypeImpl.BMI$4);
            }
            target.setFloatValue(bmi);
        }
    }

    /**
     * Sets the BMI value using an XML type object.
     *
     * @param bmi Bmi the XML representation of the BMI value
     */
    public void xsetBmi(final Bmi bmi) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bmi target = null;
            target = (Bmi)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BMI$4, 0);
            if (target == null) {
                target = (Bmi)this.get_store().add_element_user(PhysicalExaminationTypeImpl.BMI$4);
            }
            target.set((XmlObject)bmi);
        }
    }

    /**
     * Sets the BMI value to nil/null, indicating the value is explicitly absent.
     */
    public void setNilBmi() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bmi target = null;
            target = (Bmi)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BMI$4, 0);
            if (target == null) {
                target = (Bmi)this.get_store().add_element_user(PhysicalExaminationTypeImpl.BMI$4);
            }
            target.setNil();
        }
    }

    /**
     * Gets the patient's blood pressure value.
     *
     * @return String the blood pressure in format "systolic/diastolic" (e.g., "120/80"), or null if not set
     */
    public String getBp() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BP$6, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the blood pressure value as an XML type object.
     *
     * @return Bp the XML representation of the blood pressure value, or null if not set
     */
    public Bp xgetBp() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bp target = null;
            target = (Bp)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BP$6, 0);
            return target;
        }
    }

    /**
     * Sets the patient's blood pressure value.
     *
     * @param bp String the blood pressure in format "systolic/diastolic" (e.g., "120/80")
     */
    public void setBp(final String bp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BP$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PhysicalExaminationTypeImpl.BP$6);
            }
            target.setStringValue(bp);
        }
    }

    /**
     * Sets the blood pressure value using an XML type object.
     *
     * @param bp Bp the XML representation of the blood pressure value
     */
    public void xsetBp(final Bp bp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bp target = null;
            target = (Bp)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BP$6, 0);
            if (target == null) {
                target = (Bp)this.get_store().add_element_user(PhysicalExaminationTypeImpl.BP$6);
            }
            target.set((XmlObject)bp);
        }
    }

    /**
     * Gets the thyroid examination findings.
     *
     * @return NormalAbnormalNullType the thyroid examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getThyroid() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.THYROID$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the thyroid examination findings.
     *
     * @param thyroid NormalAbnormalNullType the thyroid examination status (Normal/Abnormal/Null)
     */
    public void setThyroid(final NormalAbnormalNullType thyroid) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.THYROID$8, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.THYROID$8);
            }
            target.set((XmlObject)thyroid);
        }
    }

    /**
     * Creates and adds a new thyroid examination finding.
     *
     * @return NormalAbnormalNullType the newly created thyroid examination element
     */
    public NormalAbnormalNullType addNewThyroid() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.THYROID$8);
            return target;
        }
    }

    /**
     * Gets the chest examination findings.
     *
     * @return NormalAbnormalNullType the chest examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getChest() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.CHEST$10, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the chest examination findings.
     *
     * @param chest NormalAbnormalNullType the chest examination status (Normal/Abnormal/Null)
     */
    public void setChest(final NormalAbnormalNullType chest) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.CHEST$10, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.CHEST$10);
            }
            target.set((XmlObject)chest);
        }
    }

    /**
     * Creates and adds a new chest examination finding.
     *
     * @return NormalAbnormalNullType the newly created chest examination element
     */
    public NormalAbnormalNullType addNewChest() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.CHEST$10);
            return target;
        }
    }

    /**
     * Gets the breast examination findings.
     *
     * @return NormalAbnormalNullType the breast examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getBreasts() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BREASTS$12, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the breast examination findings.
     *
     * @param breasts NormalAbnormalNullType the breast examination status (Normal/Abnormal/Null)
     */
    public void setBreasts(final NormalAbnormalNullType breasts) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.BREASTS$12, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.BREASTS$12);
            }
            target.set((XmlObject)breasts);
        }
    }

    /**
     * Creates and adds a new breast examination finding.
     *
     * @return NormalAbnormalNullType the newly created breast examination element
     */
    public NormalAbnormalNullType addNewBreasts() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.BREASTS$12);
            return target;
        }
    }

    /**
     * Gets the cardiovascular system examination findings.
     *
     * @return NormalAbnormalNullType the cardiovascular examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getCardiovascular() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.CARDIOVASCULAR$14, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the cardiovascular system examination findings.
     *
     * @param cardiovascular NormalAbnormalNullType the cardiovascular examination status (Normal/Abnormal/Null)
     */
    public void setCardiovascular(final NormalAbnormalNullType cardiovascular) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.CARDIOVASCULAR$14, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.CARDIOVASCULAR$14);
            }
            target.set((XmlObject)cardiovascular);
        }
    }

    /**
     * Creates and adds a new cardiovascular system examination finding.
     *
     * @return NormalAbnormalNullType the newly created cardiovascular examination element
     */
    public NormalAbnormalNullType addNewCardiovascular() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.CARDIOVASCULAR$14);
            return target;
        }
    }

    /**
     * Gets the abdominal examination findings.
     *
     * @return NormalAbnormalNullType the abdominal examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getAbdomen() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.ABDOMEN$16, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the abdominal examination findings.
     *
     * @param abdomen NormalAbnormalNullType the abdominal examination status (Normal/Abnormal/Null)
     */
    public void setAbdomen(final NormalAbnormalNullType abdomen) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.ABDOMEN$16, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.ABDOMEN$16);
            }
            target.set((XmlObject)abdomen);
        }
    }

    /**
     * Creates and adds a new abdominal examination finding.
     *
     * @return NormalAbnormalNullType the newly created abdominal examination element
     */
    public NormalAbnormalNullType addNewAbdomen() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.ABDOMEN$16);
            return target;
        }
    }

    /**
     * Gets the varicosities examination findings.
     *
     * @return NormalAbnormalNullType the varicosities status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getVaricosities() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.VARICOSITIES$18, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the varicosities examination findings.
     *
     * @param varicosities NormalAbnormalNullType the varicosities status (Normal/Abnormal/Null)
     */
    public void setVaricosities(final NormalAbnormalNullType varicosities) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.VARICOSITIES$18, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.VARICOSITIES$18);
            }
            target.set((XmlObject)varicosities);
        }
    }

    /**
     * Creates and adds a new varicosities examination finding.
     *
     * @return NormalAbnormalNullType the newly created varicosities element
     */
    public NormalAbnormalNullType addNewVaricosities() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.VARICOSITIES$18);
            return target;
        }
    }

    /**
     * Gets the external genitals examination findings.
     *
     * @return NormalAbnormalNullType the external genitals examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getExernalGenitals() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.EXERNALGENITALS$20, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the external genitals examination findings.
     *
     * @param exernalGenitals NormalAbnormalNullType the external genitals examination status (Normal/Abnormal/Null)
     */
    public void setExernalGenitals(final NormalAbnormalNullType exernalGenitals) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.EXERNALGENITALS$20, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.EXERNALGENITALS$20);
            }
            target.set((XmlObject)exernalGenitals);
        }
    }

    /**
     * Creates and adds a new external genitals examination finding.
     *
     * @return NormalAbnormalNullType the newly created external genitals examination element
     */
    public NormalAbnormalNullType addNewExernalGenitals() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.EXERNALGENITALS$20);
            return target;
        }
    }

    /**
     * Gets the cervix and vagina examination findings.
     *
     * @return NormalAbnormalNullType the cervix/vagina examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getCervixVagina() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.CERVIXVAGINA$22, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the cervix and vagina examination findings.
     *
     * @param cervixVagina NormalAbnormalNullType the cervix/vagina examination status (Normal/Abnormal/Null)
     */
    public void setCervixVagina(final NormalAbnormalNullType cervixVagina) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.CERVIXVAGINA$22, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.CERVIXVAGINA$22);
            }
            target.set((XmlObject)cervixVagina);
        }
    }

    /**
     * Creates and adds a new cervix and vagina examination finding.
     *
     * @return NormalAbnormalNullType the newly created cervix/vagina examination element
     */
    public NormalAbnormalNullType addNewCervixVagina() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.CERVIXVAGINA$22);
            return target;
        }
    }

    /**
     * Gets the uterus examination findings.
     *
     * @return NormalAbnormalNullType the uterus examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getUterus() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.UTERUS$24, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the uterus examination findings.
     *
     * @param uterus NormalAbnormalNullType the uterus examination status (Normal/Abnormal/Null)
     */
    public void setUterus(final NormalAbnormalNullType uterus) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.UTERUS$24, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.UTERUS$24);
            }
            target.set((XmlObject)uterus);
        }
    }

    /**
     * Creates and adds a new uterus examination finding.
     *
     * @return NormalAbnormalNullType the newly created uterus examination element
     */
    public NormalAbnormalNullType addNewUterus() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.UTERUS$24);
            return target;
        }
    }

    /**
     * Gets the uterus size description.
     *
     * @return String the textual description of uterus size (e.g., weeks of gestation equivalent), or null if not set
     */
    public String getUterusSize() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.UTERUSSIZE$26, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the uterus size as an XML type object.
     *
     * @return XmlString the XML representation of the uterus size description, or null if not set
     */
    public XmlString xgetUterusSize() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PhysicalExaminationTypeImpl.UTERUSSIZE$26, 0);
            return target;
        }
    }

    /**
     * Sets the uterus size description.
     *
     * @param uterusSize String the textual description of uterus size (e.g., weeks of gestation equivalent)
     */
    public void setUterusSize(final String uterusSize) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.UTERUSSIZE$26, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PhysicalExaminationTypeImpl.UTERUSSIZE$26);
            }
            target.setStringValue(uterusSize);
        }
    }

    /**
     * Sets the uterus size using an XML type object.
     *
     * @param uterusSize XmlString the XML representation of the uterus size description
     */
    public void xsetUterusSize(final XmlString uterusSize) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PhysicalExaminationTypeImpl.UTERUSSIZE$26, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PhysicalExaminationTypeImpl.UTERUSSIZE$26);
            }
            target.set((XmlObject)uterusSize);
        }
    }

    /**
     * Gets the adnexa (ovaries and fallopian tubes) examination findings.
     *
     * @return NormalAbnormalNullType the adnexa examination status (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getAdnexa() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.ADNEXA$28, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the adnexa (ovaries and fallopian tubes) examination findings.
     *
     * @param adnexa NormalAbnormalNullType the adnexa examination status (Normal/Abnormal/Null)
     */
    public void setAdnexa(final NormalAbnormalNullType adnexa) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.ADNEXA$28, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.ADNEXA$28);
            }
            target.set((XmlObject)adnexa);
        }
    }

    /**
     * Creates and adds a new adnexa examination finding.
     *
     * @return NormalAbnormalNullType the newly created adnexa examination element
     */
    public NormalAbnormalNullType addNewAdnexa() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.ADNEXA$28);
            return target;
        }
    }

    /**
     * Gets the description of other examination findings.
     *
     * @return String the textual description of additional findings not covered by specific fields, or null if not set
     */
    public String getOtherDescr() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.OTHERDESCR$30, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the description of other examination findings as an XML type object.
     *
     * @return XmlString the XML representation of other findings description, or null if not set
     */
    public XmlString xgetOtherDescr() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PhysicalExaminationTypeImpl.OTHERDESCR$30, 0);
            return target;
        }
    }

    /**
     * Sets the description of other examination findings.
     *
     * @param otherDescr String the textual description of additional findings not covered by specific fields
     */
    public void setOtherDescr(final String otherDescr) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PhysicalExaminationTypeImpl.OTHERDESCR$30, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PhysicalExaminationTypeImpl.OTHERDESCR$30);
            }
            target.setStringValue(otherDescr);
        }
    }

    /**
     * Sets the description of other examination findings using an XML type object.
     *
     * @param otherDescr XmlString the XML representation of other findings description
     */
    public void xsetOtherDescr(final XmlString otherDescr) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PhysicalExaminationTypeImpl.OTHERDESCR$30, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PhysicalExaminationTypeImpl.OTHERDESCR$30);
            }
            target.set((XmlObject)otherDescr);
        }
    }

    /**
     * Gets the status of other examination findings.
     *
     * @return NormalAbnormalNullType the status of other findings (Normal/Abnormal/Null), or null if not set
     */
    public NormalAbnormalNullType getOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.OTHER$32, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the status of other examination findings.
     *
     * @param other NormalAbnormalNullType the status of other findings (Normal/Abnormal/Null)
     */
    public void setOther(final NormalAbnormalNullType other) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().find_element_user(PhysicalExaminationTypeImpl.OTHER$32, 0);
            if (target == null) {
                target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.OTHER$32);
            }
            target.set((XmlObject)other);
        }
    }

    /**
     * Creates and adds a new other examination finding element.
     *
     * @return NormalAbnormalNullType the newly created other findings element
     */
    public NormalAbnormalNullType addNewOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            NormalAbnormalNullType target = null;
            target = (NormalAbnormalNullType)this.get_store().add_element_user(PhysicalExaminationTypeImpl.OTHER$32);
            return target;
        }
    }
    
    static {
        HEIGHT$0 = new QName("http://www.oscarmcmaster.org/AR2005", "height");
        WEIGHT$2 = new QName("http://www.oscarmcmaster.org/AR2005", "weight");
        BMI$4 = new QName("http://www.oscarmcmaster.org/AR2005", "bmi");
        BP$6 = new QName("http://www.oscarmcmaster.org/AR2005", "bp");
        THYROID$8 = new QName("http://www.oscarmcmaster.org/AR2005", "thyroid");
        CHEST$10 = new QName("http://www.oscarmcmaster.org/AR2005", "chest");
        BREASTS$12 = new QName("http://www.oscarmcmaster.org/AR2005", "breasts");
        CARDIOVASCULAR$14 = new QName("http://www.oscarmcmaster.org/AR2005", "cardiovascular");
        ABDOMEN$16 = new QName("http://www.oscarmcmaster.org/AR2005", "abdomen");
        VARICOSITIES$18 = new QName("http://www.oscarmcmaster.org/AR2005", "varicosities");
        EXERNALGENITALS$20 = new QName("http://www.oscarmcmaster.org/AR2005", "exernalGenitals");
        CERVIXVAGINA$22 = new QName("http://www.oscarmcmaster.org/AR2005", "cervixVagina");
        UTERUS$24 = new QName("http://www.oscarmcmaster.org/AR2005", "uterus");
        UTERUSSIZE$26 = new QName("http://www.oscarmcmaster.org/AR2005", "uterusSize");
        ADNEXA$28 = new QName("http://www.oscarmcmaster.org/AR2005", "adnexa");
        OTHERDESCR$30 = new QName("http://www.oscarmcmaster.org/AR2005", "otherDescr");
        OTHER$32 = new QName("http://www.oscarmcmaster.org/AR2005", "other");
    }

    /**
     * Inner implementation class for the Height XML type.
     *
     * Provides XMLBeans implementation for height measurements stored as float values.
     * Extends {@link JavaFloatHolderEx} to provide XML binding for floating-point height data.
     *
     * @see JavaFloatHolderEx
     * @since 2026-01-24
     */
    public static class HeightImpl extends JavaFloatHolderEx implements Height
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new HeightImpl instance.
         *
         * @param sType SchemaType the schema type definition for this XML element
         */
        public HeightImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new HeightImpl instance with explicit validation control.
         *
         * @param sType SchemaType the schema type definition for this XML element
         * @param b boolean whether to enable validation
         */
        protected HeightImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the Weight XML type.
     *
     * Provides XMLBeans implementation for weight measurements stored as float values.
     * Extends {@link JavaFloatHolderEx} to provide XML binding for floating-point weight data.
     *
     * @see JavaFloatHolderEx
     * @since 2026-01-24
     */
    public static class WeightImpl extends JavaFloatHolderEx implements Weight
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new WeightImpl instance.
         *
         * @param sType SchemaType the schema type definition for this XML element
         */
        public WeightImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new WeightImpl instance with explicit validation control.
         *
         * @param sType SchemaType the schema type definition for this XML element
         * @param b boolean whether to enable validation
         */
        protected WeightImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the BMI (Body Mass Index) XML type.
     *
     * Provides XMLBeans implementation for BMI values stored as float values.
     * Extends {@link JavaFloatHolderEx} to provide XML binding for floating-point BMI data.
     *
     * @see JavaFloatHolderEx
     * @since 2026-01-24
     */
    public static class BmiImpl extends JavaFloatHolderEx implements Bmi
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new BmiImpl instance.
         *
         * @param sType SchemaType the schema type definition for this XML element
         */
        public BmiImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new BmiImpl instance with explicit validation control.
         *
         * @param sType SchemaType the schema type definition for this XML element
         * @param b boolean whether to enable validation
         */
        protected BmiImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the BP (Blood Pressure) XML type.
     *
     * Provides XMLBeans implementation for blood pressure values stored as string values.
     * Extends {@link JavaStringHolderEx} to provide XML binding for blood pressure data
     * in "systolic/diastolic" format (e.g., "120/80").
     *
     * @see JavaStringHolderEx
     * @since 2026-01-24
     */
    public static class BpImpl extends JavaStringHolderEx implements Bp
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new BpImpl instance.
         *
         * @param sType SchemaType the schema type definition for this XML element
         */
        public BpImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new BpImpl instance with explicit validation control.
         *
         * @param sType SchemaType the schema type definition for this XML element
         * @param b boolean whether to enable validation
         */
        protected BpImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }
}
