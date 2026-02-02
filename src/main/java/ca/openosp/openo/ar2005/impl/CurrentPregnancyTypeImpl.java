package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.YesNoNullType;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.CurrentPregnancyType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the CurrentPregnancyType interface for managing current pregnancy data in antenatal records.
 *
 * This class is part of the British Columbia Antenatal Record (BCAR) AR2005 form implementation and handles
 * XML serialization/deserialization of current pregnancy health assessment data. It tracks critical prenatal
 * health indicators including lifestyle factors, nutritional status, and environmental risk exposures that
 * may affect maternal and fetal health outcomes.
 *
 * The implementation extends Apache XMLBeans framework classes to provide XML schema binding functionality
 * for the AR2005 antenatal record system. All data elements are stored as YesNoNullType values to support
 * the standard yes/no/unknown clinical documentation pattern used in obstetric care.
 *
 * Healthcare context: This implementation supports standardized prenatal risk assessment and documentation
 * required for comprehensive antenatal care in British Columbia, Canada. The data collected helps healthcare
 * providers identify and manage pregnancy-related health risks early in prenatal care.
 *
 * @see CurrentPregnancyType
 * @see YesNoNullType
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-24
 */
public class CurrentPregnancyTypeImpl extends XmlComplexContentImpl implements CurrentPregnancyType
{
    private static final long serialVersionUID = 1L;
    private static final QName BLEEDING$0;
    private static final QName NAUSEA$2;
    private static final QName SMOKING$4;
    private static final QName CIGSPERDAY$6;
    private static final QName ALCOHOLDRUGS$8;
    private static final QName OCCENVRISKS$10;
    private static final QName DIETARYRES$12;
    private static final QName CALCIUMADEQUATE$14;
    private static final QName FOLATE$16;

    /**
     * Constructs a new CurrentPregnancyType implementation instance with the specified schema type.
     *
     * This constructor is called by the XMLBeans framework during XML deserialization to create
     * a new instance bound to the AR2005 schema definition for current pregnancy data elements.
     *
     * @param sType SchemaType the XML schema type definition for current pregnancy elements
     */
    public CurrentPregnancyTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the bleeding status indicator for the current pregnancy.
     *
     * Bleeding during pregnancy can be a sign of complications such as miscarriage, placental issues,
     * or cervical changes. This field documents whether the patient has experienced any vaginal bleeding
     * during the current pregnancy, which is critical for risk assessment and clinical management.
     *
     * @return YesNoNullType the bleeding status (yes/no/unknown), or null if not set
     */
    public YesNoNullType getBleeding() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.BLEEDING$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the bleeding status indicator for the current pregnancy.
     *
     * Updates the patient's bleeding status in the antenatal record. Healthcare providers use this
     * to document any episodes of vaginal bleeding reported or observed during prenatal care.
     *
     * @param bleeding YesNoNullType the bleeding status to set (yes/no/unknown)
     */
    public void setBleeding(final YesNoNullType bleeding) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.BLEEDING$0, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.BLEEDING$0);
            }
            target.set((XmlObject)bleeding);
        }
    }

    /**
     * Creates and adds a new bleeding status element to the XML structure.
     *
     * This factory method creates a new YesNoNullType element for the bleeding field and adds it
     * to the underlying XML store. Used during initial form creation or XML document building.
     *
     * @return YesNoNullType a new bleeding status element that can be configured
     */
    public YesNoNullType addNewBleeding() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.BLEEDING$0);
            return target;
        }
    }

    /**
     * Retrieves the nausea status indicator for the current pregnancy.
     *
     * Nausea and vomiting are common pregnancy symptoms, but severe cases (hyperemesis gravidarum)
     * can lead to dehydration and require medical intervention. This field documents whether the
     * patient is experiencing nausea, which helps assess nutritional status and need for intervention.
     *
     * @return YesNoNullType the nausea status (yes/no/unknown), or null if not set
     */
    public YesNoNullType getNausea() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.NAUSEA$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the nausea status indicator for the current pregnancy.
     *
     * Updates the patient's nausea status in the antenatal record. Healthcare providers use this
     * to monitor pregnancy-related symptoms and determine if intervention is needed.
     *
     * @param nausea YesNoNullType the nausea status to set (yes/no/unknown)
     */
    public void setNausea(final YesNoNullType nausea) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.NAUSEA$2, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.NAUSEA$2);
            }
            target.set((XmlObject)nausea);
        }
    }

    /**
     * Creates and adds a new nausea status element to the XML structure.
     *
     * This factory method creates a new YesNoNullType element for the nausea field and adds it
     * to the underlying XML store. Used during initial form creation or XML document building.
     *
     * @return YesNoNullType a new nausea status element that can be configured
     */
    public YesNoNullType addNewNausea() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.NAUSEA$2);
            return target;
        }
    }

    /**
     * Retrieves the smoking status indicator for the current pregnancy.
     *
     * Smoking during pregnancy is a major risk factor for complications including low birth weight,
     * preterm birth, and sudden infant death syndrome (SIDS). This field documents whether the
     * patient is currently smoking, which is essential for risk assessment and counseling.
     *
     * @return YesNoNullType the smoking status (yes/no/unknown), or null if not set
     */
    public YesNoNullType getSmoking() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.SMOKING$4, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the smoking status indicator for the current pregnancy.
     *
     * Updates the patient's smoking status in the antenatal record. Healthcare providers use this
     * to identify patients who need smoking cessation counseling and support during pregnancy.
     *
     * @param smoking YesNoNullType the smoking status to set (yes/no/unknown)
     */
    public void setSmoking(final YesNoNullType smoking) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.SMOKING$4, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.SMOKING$4);
            }
            target.set((XmlObject)smoking);
        }
    }

    /**
     * Creates and adds a new smoking status element to the XML structure.
     *
     * This factory method creates a new YesNoNullType element for the smoking field and adds it
     * to the underlying XML store. Used during initial form creation or XML document building.
     *
     * @return YesNoNullType a new smoking status element that can be configured
     */
    public YesNoNullType addNewSmoking() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.SMOKING$4);
            return target;
        }
    }

    /**
     * Retrieves the cigarettes per day enumeration value for the current pregnancy.
     *
     * For patients who smoke, this field quantifies the daily cigarette consumption level.
     * The quantity of smoking is directly correlated with the severity of health risks to
     * both the mother and fetus, making this important for risk stratification and counseling.
     *
     * @return CigsPerDay.Enum the cigarettes per day enumeration value, or null if not set
     */
    public CigsPerDay.Enum getCigsPerDay() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(CurrentPregnancyTypeImpl.CIGSPERDAY$6, 0);
            if (target == null) {
                return null;
            }
            return (CigsPerDay.Enum)target.getEnumValue();
        }
    }

    /**
     * Retrieves the cigarettes per day element as an XMLBeans type.
     *
     * This method returns the low-level XMLBeans representation of the cigarettes per day field,
     * providing access to the underlying XML structure and schema validation capabilities.
     *
     * @return CigsPerDay the XMLBeans type representation of cigarettes per day, or null if not set
     */
    public CigsPerDay xgetCigsPerDay() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CigsPerDay target = null;
            target = (CigsPerDay)this.get_store().find_element_user(CurrentPregnancyTypeImpl.CIGSPERDAY$6, 0);
            return target;
        }
    }

    /**
     * Sets the cigarettes per day enumeration value for the current pregnancy.
     *
     * Updates the patient's daily cigarette consumption level in the antenatal record. This
     * quantitative data helps healthcare providers tailor smoking cessation interventions.
     *
     * @param cigsPerDay CigsPerDay.Enum the cigarettes per day enumeration value to set
     */
    public void setCigsPerDay(final CigsPerDay.Enum cigsPerDay) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(CurrentPregnancyTypeImpl.CIGSPERDAY$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(CurrentPregnancyTypeImpl.CIGSPERDAY$6);
            }
            target.setEnumValue((StringEnumAbstractBase)cigsPerDay);
        }
    }

    /**
     * Sets the cigarettes per day element using an XMLBeans type.
     *
     * This method accepts the low-level XMLBeans representation of the cigarettes per day field,
     * allowing direct manipulation of the underlying XML structure with schema validation.
     *
     * @param cigsPerDay CigsPerDay the XMLBeans type representation to set
     */
    public void xsetCigsPerDay(final CigsPerDay cigsPerDay) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CigsPerDay target = null;
            target = (CigsPerDay)this.get_store().find_element_user(CurrentPregnancyTypeImpl.CIGSPERDAY$6, 0);
            if (target == null) {
                target = (CigsPerDay)this.get_store().add_element_user(CurrentPregnancyTypeImpl.CIGSPERDAY$6);
            }
            target.set((XmlObject)cigsPerDay);
        }
    }

    /**
     * Retrieves the alcohol and drugs usage indicator for the current pregnancy.
     *
     * Alcohol and drug use during pregnancy can cause fetal alcohol spectrum disorders (FASD),
     * birth defects, and developmental delays. This field documents whether the patient is
     * using alcohol or drugs, which is critical for risk assessment and intervention planning.
     *
     * @return YesNoNullType the alcohol/drugs usage status (yes/no/unknown), or null if not set
     */
    public YesNoNullType getAlcoholDrugs() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.ALCOHOLDRUGS$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the alcohol and drugs usage indicator for the current pregnancy.
     *
     * Updates the patient's alcohol and drug usage status in the antenatal record. Healthcare
     * providers use this to identify patients who need counseling and support services.
     *
     * @param alcoholDrugs YesNoNullType the alcohol/drugs usage status to set (yes/no/unknown)
     */
    public void setAlcoholDrugs(final YesNoNullType alcoholDrugs) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.ALCOHOLDRUGS$8, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.ALCOHOLDRUGS$8);
            }
            target.set((XmlObject)alcoholDrugs);
        }
    }

    /**
     * Creates and adds a new alcohol/drugs usage element to the XML structure.
     *
     * This factory method creates a new YesNoNullType element for the alcohol/drugs field and
     * adds it to the underlying XML store. Used during initial form creation or XML document building.
     *
     * @return YesNoNullType a new alcohol/drugs usage element that can be configured
     */
    public YesNoNullType addNewAlcoholDrugs() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.ALCOHOLDRUGS$8);
            return target;
        }
    }

    /**
     * Retrieves the occupational and environmental risks indicator for the current pregnancy.
     *
     * Exposure to workplace or environmental hazards such as chemicals, radiation, heavy metals,
     * or infectious agents can pose risks to fetal development. This field documents whether the
     * patient has identified occupational or environmental risk exposures that require monitoring.
     *
     * @return YesNoNullType the occupational/environmental risks status (yes/no/unknown), or null if not set
     */
    public YesNoNullType getOccEnvRisks() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.OCCENVRISKS$10, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the occupational and environmental risks indicator for the current pregnancy.
     *
     * Updates the patient's occupational/environmental risk exposure status in the antenatal record.
     * Healthcare providers use this to identify patients who may need workplace accommodations or
     * environmental exposure reduction strategies.
     *
     * @param occEnvRisks YesNoNullType the occupational/environmental risks status to set (yes/no/unknown)
     */
    public void setOccEnvRisks(final YesNoNullType occEnvRisks) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.OCCENVRISKS$10, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.OCCENVRISKS$10);
            }
            target.set((XmlObject)occEnvRisks);
        }
    }

    /**
     * Creates and adds a new occupational/environmental risks element to the XML structure.
     *
     * This factory method creates a new YesNoNullType element for the occupational/environmental
     * risks field and adds it to the underlying XML store. Used during initial form creation or
     * XML document building.
     *
     * @return YesNoNullType a new occupational/environmental risks element that can be configured
     */
    public YesNoNullType addNewOccEnvRisks() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.OCCENVRISKS$10);
            return target;
        }
    }

    /**
     * Retrieves the dietary restrictions indicator for the current pregnancy.
     *
     * Dietary restrictions (religious, cultural, medical, or personal) can affect nutritional
     * adequacy during pregnancy. This field documents whether the patient has dietary restrictions
     * that may require nutritional counseling or supplementation to ensure adequate maternal and
     * fetal nutrition.
     *
     * @return YesNoNullType the dietary restrictions status (yes/no/unknown), or null if not set
     */
    public YesNoNullType getDietaryRes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.DIETARYRES$12, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the dietary restrictions indicator for the current pregnancy.
     *
     * Updates the patient's dietary restrictions status in the antenatal record. Healthcare
     * providers use this to identify patients who may need specialized nutritional guidance
     * to ensure pregnancy nutrition requirements are met.
     *
     * @param dietaryRes YesNoNullType the dietary restrictions status to set (yes/no/unknown)
     */
    public void setDietaryRes(final YesNoNullType dietaryRes) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.DIETARYRES$12, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.DIETARYRES$12);
            }
            target.set((XmlObject)dietaryRes);
        }
    }

    /**
     * Creates and adds a new dietary restrictions element to the XML structure.
     *
     * This factory method creates a new YesNoNullType element for the dietary restrictions field
     * and adds it to the underlying XML store. Used during initial form creation or XML document building.
     *
     * @return YesNoNullType a new dietary restrictions element that can be configured
     */
    public YesNoNullType addNewDietaryRes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.DIETARYRES$12);
            return target;
        }
    }

    /**
     * Retrieves the calcium adequacy indicator for the current pregnancy.
     *
     * Adequate calcium intake is essential during pregnancy for fetal skeletal development and
     * to prevent maternal bone density loss. This field documents whether the patient's dietary
     * calcium intake is adequate, which helps determine if calcium supplementation is needed.
     *
     * @return YesNoNullType the calcium adequacy status (yes/no/unknown), or null if not set
     */
    public YesNoNullType getCalciumAdequate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.CALCIUMADEQUATE$14, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the calcium adequacy indicator for the current pregnancy.
     *
     * Updates the patient's calcium adequacy status in the antenatal record. Healthcare providers
     * use this to determine if calcium supplementation or dietary counseling is needed.
     *
     * @param calciumAdequate YesNoNullType the calcium adequacy status to set (yes/no/unknown)
     */
    public void setCalciumAdequate(final YesNoNullType calciumAdequate) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.CALCIUMADEQUATE$14, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.CALCIUMADEQUATE$14);
            }
            target.set((XmlObject)calciumAdequate);
        }
    }

    /**
     * Creates and adds a new calcium adequacy element to the XML structure.
     *
     * This factory method creates a new YesNoNullType element for the calcium adequacy field
     * and adds it to the underlying XML store. Used during initial form creation or XML document building.
     *
     * @return YesNoNullType a new calcium adequacy element that can be configured
     */
    public YesNoNullType addNewCalciumAdequate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.CALCIUMADEQUATE$14);
            return target;
        }
    }

    /**
     * Retrieves the folate supplementation indicator for the current pregnancy.
     *
     * Folate (folic acid) supplementation is critical during pregnancy to prevent neural tube defects
     * such as spina bifida. This field documents whether the patient is taking folate supplements,
     * which is a standard recommendation for all pregnant women to reduce birth defect risk.
     *
     * @return YesNoNullType the folate supplementation status (yes/no/unknown), or null if not set
     */
    public YesNoNullType getFolate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.FOLATE$16, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the folate supplementation indicator for the current pregnancy.
     *
     * Updates the patient's folate supplementation status in the antenatal record. Healthcare
     * providers use this to ensure patients are following the standard recommendation for folate
     * intake during pregnancy to prevent neural tube defects.
     *
     * @param folate YesNoNullType the folate supplementation status to set (yes/no/unknown)
     */
    public void setFolate(final YesNoNullType folate) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(CurrentPregnancyTypeImpl.FOLATE$16, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.FOLATE$16);
            }
            target.set((XmlObject)folate);
        }
    }

    /**
     * Creates and adds a new folate supplementation element to the XML structure.
     *
     * This factory method creates a new YesNoNullType element for the folate supplementation field
     * and adds it to the underlying XML store. Used during initial form creation or XML document building.
     *
     * @return YesNoNullType a new folate supplementation element that can be configured
     */
    public YesNoNullType addNewFolate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(CurrentPregnancyTypeImpl.FOLATE$16);
            return target;
        }
    }
    
    static {
        BLEEDING$0 = new QName("http://www.oscarmcmaster.org/AR2005", "bleeding");
        NAUSEA$2 = new QName("http://www.oscarmcmaster.org/AR2005", "nausea");
        SMOKING$4 = new QName("http://www.oscarmcmaster.org/AR2005", "smoking");
        CIGSPERDAY$6 = new QName("http://www.oscarmcmaster.org/AR2005", "cigsPerDay");
        ALCOHOLDRUGS$8 = new QName("http://www.oscarmcmaster.org/AR2005", "alcoholDrugs");
        OCCENVRISKS$10 = new QName("http://www.oscarmcmaster.org/AR2005", "occEnvRisks");
        DIETARYRES$12 = new QName("http://www.oscarmcmaster.org/AR2005", "dietaryRes");
        CALCIUMADEQUATE$14 = new QName("http://www.oscarmcmaster.org/AR2005", "calciumAdequate");
        FOLATE$16 = new QName("http://www.oscarmcmaster.org/AR2005", "folate");
    }

    /**
     * Implementation of the CigsPerDay enumeration for cigarette consumption quantification.
     *
     * This inner class provides the XMLBeans implementation for the cigarettes per day enumeration,
     * which categorizes daily smoking levels for prenatal risk assessment. The enumeration supports
     * standardized smoking quantity documentation used in antenatal care protocols.
     *
     * @see CigsPerDay
     * @since 2026-01-24
     */
    public static class CigsPerDayImpl extends JavaStringEnumerationHolderEx implements CigsPerDay
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new CigsPerDay enumeration instance with the specified schema type.
         *
         * This constructor is called by the XMLBeans framework during XML deserialization to create
         * a new cigarettes per day enumeration instance bound to the AR2005 schema definition.
         *
         * @param sType SchemaType the XML schema type definition for the cigarettes per day enumeration
         */
        public CigsPerDayImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new CigsPerDay enumeration instance with the specified schema type and validation flag.
         *
         * This protected constructor is used internally by the XMLBeans framework for specialized
         * instantiation scenarios, including cases where validation may be deferred or customized.
         *
         * @param sType SchemaType the XML schema type definition for the cigarettes per day enumeration
         * @param b boolean flag controlling validation or initialization behavior
         */
        protected CigsPerDayImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }
}
