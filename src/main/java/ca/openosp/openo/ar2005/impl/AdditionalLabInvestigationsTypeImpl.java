package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx;
import ca.openosp.openo.ar2005.CustomLab;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.AdditionalLabInvestigationsType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for additional laboratory investigations data in British Columbia Antenatal Record (BCAR) forms.
 *
 * This class provides XML binding and data access methods for prenatal laboratory test results including hemoglobin,
 * blood group, Rh factor, antibody screening, glucose challenge test (GCT), glucose tolerance test (GTT),
 * Group B Streptococcus (GBS) screening, and up to four custom laboratory investigations. It supports the AR2005
 * XML schema used for British Columbia prenatal care documentation.
 *
 * All public methods are thread-safe through synchronization on the internal monitor object. The class follows
 * the XMLBeans pattern with paired getter/setter methods for both String values and XMLBeans typed values.
 *
 * @see ca.openosp.openo.ar2005.AdditionalLabInvestigationsType
 * @see ca.openosp.openo.ar2005.CustomLab
 * @since 2026-01-24
 */
public class AdditionalLabInvestigationsTypeImpl extends XmlComplexContentImpl implements AdditionalLabInvestigationsType
{
    private static final long serialVersionUID = 1L;
    private static final QName HB$0;
    private static final QName BLOODGROUP$2;
    private static final QName RH$4;
    private static final QName REPEATABS$6;
    private static final QName GCT$8;
    private static final QName GTT$10;
    private static final QName GBS$12;
    private static final QName CUSTOMLAB1$14;
    private static final QName CUSTOMLAB2$16;
    private static final QName CUSTOMLAB3$18;
    private static final QName CUSTOMLAB4$20;

    /**
     * Constructs a new AdditionalLabInvestigationsTypeImpl instance with the specified schema type.
     *
     * @param sType SchemaType the XMLBeans schema type for this implementation
     */
    public AdditionalLabInvestigationsTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the hemoglobin (Hb) value.
     *
     * @return String the hemoglobin level value, or null if not set
     */
    public String getHb() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.HB$0, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the hemoglobin (Hb) value as an XMLBeans XmlString.
     *
     * @return XmlString the hemoglobin XML element, or null if not set
     */
    public XmlString xgetHb() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.HB$0, 0);
            return target;
        }
    }

    /**
     * Sets the hemoglobin (Hb) value.
     *
     * @param hb String the hemoglobin level value to set
     */
    public void setHb(final String hb) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.HB$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.HB$0);
            }
            target.setStringValue(hb);
        }
    }

    /**
     * Sets the hemoglobin (Hb) value as an XMLBeans XmlString.
     *
     * @param hb XmlString the hemoglobin XML element to set
     */
    public void xsetHb(final XmlString hb) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.HB$0, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.HB$0);
            }
            target.set((XmlObject)hb);
        }
    }

    /**
     * Gets the blood group value.
     *
     * @return BloodGroup.Enum the blood group enumeration value (A, B, AB, O), or null if not set
     */
    public BloodGroup.Enum getBloodGroup() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.BLOODGROUP$2, 0);
            if (target == null) {
                return null;
            }
            return (BloodGroup.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the blood group value as an XMLBeans BloodGroup type.
     *
     * @return BloodGroup the blood group XML element, or null if not set
     */
    public BloodGroup xgetBloodGroup() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            BloodGroup target = null;
            target = (BloodGroup)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.BLOODGROUP$2, 0);
            return target;
        }
    }

    /**
     * Sets the blood group value.
     *
     * @param bloodGroup BloodGroup.Enum the blood group enumeration value to set (A, B, AB, O)
     */
    public void setBloodGroup(final BloodGroup.Enum bloodGroup) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.BLOODGROUP$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.BLOODGROUP$2);
            }
            target.setEnumValue((StringEnumAbstractBase)bloodGroup);
        }
    }

    /**
     * Sets the blood group value as an XMLBeans BloodGroup type.
     *
     * @param bloodGroup BloodGroup the blood group XML element to set
     */
    public void xsetBloodGroup(final BloodGroup bloodGroup) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            BloodGroup target = null;
            target = (BloodGroup)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.BLOODGROUP$2, 0);
            if (target == null) {
                target = (BloodGroup)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.BLOODGROUP$2);
            }
            target.set((XmlObject)bloodGroup);
        }
    }

    /**
     * Gets the Rh factor value.
     *
     * @return Rh.Enum the Rh factor enumeration value (positive or negative), or null if not set
     */
    public Rh.Enum getRh() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.RH$4, 0);
            if (target == null) {
                return null;
            }
            return (Rh.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the Rh factor value as an XMLBeans Rh type.
     *
     * @return Rh the Rh factor XML element, or null if not set
     */
    public Rh xgetRh() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Rh target = null;
            target = (Rh)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.RH$4, 0);
            return target;
        }
    }

    /**
     * Sets the Rh factor value.
     *
     * @param rh Rh.Enum the Rh factor enumeration value to set (positive or negative)
     */
    public void setRh(final Rh.Enum rh) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.RH$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.RH$4);
            }
            target.setEnumValue((StringEnumAbstractBase)rh);
        }
    }

    /**
     * Sets the Rh factor value as an XMLBeans Rh type.
     *
     * @param rh Rh the Rh factor XML element to set
     */
    public void xsetRh(final Rh rh) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Rh target = null;
            target = (Rh)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.RH$4, 0);
            if (target == null) {
                target = (Rh)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.RH$4);
            }
            target.set((XmlObject)rh);
        }
    }

    /**
     * Gets the repeat antibody screen (ABS) value.
     *
     * @return String the repeat antibody screen result value, or null if not set
     */
    public String getRepeatABS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.REPEATABS$6, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the repeat antibody screen (ABS) value as an XMLBeans XmlString.
     *
     * @return XmlString the repeat antibody screen XML element, or null if not set
     */
    public XmlString xgetRepeatABS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.REPEATABS$6, 0);
            return target;
        }
    }

    /**
     * Sets the repeat antibody screen (ABS) value.
     *
     * @param repeatABS String the repeat antibody screen result value to set
     */
    public void setRepeatABS(final String repeatABS) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.REPEATABS$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.REPEATABS$6);
            }
            target.setStringValue(repeatABS);
        }
    }

    /**
     * Sets the repeat antibody screen (ABS) value as an XMLBeans XmlString.
     *
     * @param repeatABS XmlString the repeat antibody screen XML element to set
     */
    public void xsetRepeatABS(final XmlString repeatABS) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.REPEATABS$6, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.REPEATABS$6);
            }
            target.set((XmlObject)repeatABS);
        }
    }

    /**
     * Gets the Glucose Challenge Test (GCT) value.
     *
     * @return String the glucose challenge test result value, or null if not set
     */
    public String getGCT() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GCT$8, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the Glucose Challenge Test (GCT) value as an XMLBeans XmlString.
     *
     * @return XmlString the glucose challenge test XML element, or null if not set
     */
    public XmlString xgetGCT() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GCT$8, 0);
            return target;
        }
    }

    /**
     * Sets the Glucose Challenge Test (GCT) value.
     *
     * @param gct String the glucose challenge test result value to set
     */
    public void setGCT(final String gct) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GCT$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.GCT$8);
            }
            target.setStringValue(gct);
        }
    }

    /**
     * Sets the Glucose Challenge Test (GCT) value as an XMLBeans XmlString.
     *
     * @param gct XmlString the glucose challenge test XML element to set
     */
    public void xsetGCT(final XmlString gct) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GCT$8, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.GCT$8);
            }
            target.set((XmlObject)gct);
        }
    }

    /**
     * Gets the Glucose Tolerance Test (GTT) value.
     *
     * @return String the glucose tolerance test result value, or null if not set
     */
    public String getGTT() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GTT$10, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the Glucose Tolerance Test (GTT) value as an XMLBeans XmlString.
     *
     * @return XmlString the glucose tolerance test XML element, or null if not set
     */
    public XmlString xgetGTT() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GTT$10, 0);
            return target;
        }
    }

    /**
     * Sets the Glucose Tolerance Test (GTT) value.
     *
     * @param gtt String the glucose tolerance test result value to set
     */
    public void setGTT(final String gtt) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GTT$10, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.GTT$10);
            }
            target.setStringValue(gtt);
        }
    }

    /**
     * Sets the Glucose Tolerance Test (GTT) value as an XMLBeans XmlString.
     *
     * @param gtt XmlString the glucose tolerance test XML element to set
     */
    public void xsetGTT(final XmlString gtt) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GTT$10, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.GTT$10);
            }
            target.set((XmlObject)gtt);
        }
    }

    /**
     * Gets the Group B Streptococcus (GBS) screening value.
     *
     * @return GBS.Enum the GBS screening result enumeration value, or null if not set
     */
    public GBS.Enum getGBS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GBS$12, 0);
            if (target == null) {
                return null;
            }
            return (GBS.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the Group B Streptococcus (GBS) screening value as an XMLBeans GBS type.
     *
     * @return GBS the GBS screening XML element, or null if not set
     */
    public GBS xgetGBS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GBS target = null;
            target = (GBS)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GBS$12, 0);
            return target;
        }
    }

    /**
     * Sets the Group B Streptococcus (GBS) screening value.
     *
     * @param gbs GBS.Enum the GBS screening result enumeration value to set
     */
    public void setGBS(final GBS.Enum gbs) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GBS$12, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.GBS$12);
            }
            target.setEnumValue((StringEnumAbstractBase)gbs);
        }
    }

    /**
     * Sets the Group B Streptococcus (GBS) screening value as an XMLBeans GBS type.
     *
     * @param gbs GBS the GBS screening XML element to set
     */
    public void xsetGBS(final GBS gbs) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GBS target = null;
            target = (GBS)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.GBS$12, 0);
            if (target == null) {
                target = (GBS)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.GBS$12);
            }
            target.set((XmlObject)gbs);
        }
    }

    /**
     * Gets the first custom laboratory investigation.
     *
     * @return CustomLab the first custom lab configuration and results, or null if not set
     */
    public CustomLab getCustomLab1() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB1$14, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the first custom laboratory investigation.
     *
     * @param customLab1 CustomLab the first custom lab configuration and results to set
     */
    public void setCustomLab1(final CustomLab customLab1) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB1$14, 0);
            if (target == null) {
                target = (CustomLab)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB1$14);
            }
            target.set((XmlObject)customLab1);
        }
    }

    /**
     * Adds a new first custom laboratory investigation element.
     *
     * @return CustomLab the newly created custom lab element
     */
    public CustomLab addNewCustomLab1() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB1$14);
            return target;
        }
    }

    /**
     * Gets the second custom laboratory investigation.
     *
     * @return CustomLab the second custom lab configuration and results, or null if not set
     */
    public CustomLab getCustomLab2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB2$16, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the second custom laboratory investigation.
     *
     * @param customLab2 CustomLab the second custom lab configuration and results to set
     */
    public void setCustomLab2(final CustomLab customLab2) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB2$16, 0);
            if (target == null) {
                target = (CustomLab)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB2$16);
            }
            target.set((XmlObject)customLab2);
        }
    }

    /**
     * Adds a new second custom laboratory investigation element.
     *
     * @return CustomLab the newly created custom lab element
     */
    public CustomLab addNewCustomLab2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB2$16);
            return target;
        }
    }

    /**
     * Gets the third custom laboratory investigation.
     *
     * @return CustomLab the third custom lab configuration and results, or null if not set
     */
    public CustomLab getCustomLab3() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB3$18, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the third custom laboratory investigation.
     *
     * @param customLab3 CustomLab the third custom lab configuration and results to set
     */
    public void setCustomLab3(final CustomLab customLab3) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB3$18, 0);
            if (target == null) {
                target = (CustomLab)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB3$18);
            }
            target.set((XmlObject)customLab3);
        }
    }

    /**
     * Adds a new third custom laboratory investigation element.
     *
     * @return CustomLab the newly created custom lab element
     */
    public CustomLab addNewCustomLab3() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB3$18);
            return target;
        }
    }

    /**
     * Gets the fourth custom laboratory investigation.
     *
     * @return CustomLab the fourth custom lab configuration and results, or null if not set
     */
    public CustomLab getCustomLab4() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB4$20, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the fourth custom laboratory investigation.
     *
     * @param customLab4 CustomLab the fourth custom lab configuration and results to set
     */
    public void setCustomLab4(final CustomLab customLab4) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB4$20, 0);
            if (target == null) {
                target = (CustomLab)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB4$20);
            }
            target.set((XmlObject)customLab4);
        }
    }

    /**
     * Adds a new fourth custom laboratory investigation element.
     *
     * @return CustomLab the newly created custom lab element
     */
    public CustomLab addNewCustomLab4() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().add_element_user(AdditionalLabInvestigationsTypeImpl.CUSTOMLAB4$20);
            return target;
        }
    }
    
    static {
        HB$0 = new QName("http://www.oscarmcmaster.org/AR2005", "hb");
        BLOODGROUP$2 = new QName("http://www.oscarmcmaster.org/AR2005", "bloodGroup");
        RH$4 = new QName("http://www.oscarmcmaster.org/AR2005", "rh");
        REPEATABS$6 = new QName("http://www.oscarmcmaster.org/AR2005", "repeatABS");
        GCT$8 = new QName("http://www.oscarmcmaster.org/AR2005", "GCT");
        GTT$10 = new QName("http://www.oscarmcmaster.org/AR2005", "GTT");
        GBS$12 = new QName("http://www.oscarmcmaster.org/AR2005", "GBS");
        CUSTOMLAB1$14 = new QName("http://www.oscarmcmaster.org/AR2005", "customLab1");
        CUSTOMLAB2$16 = new QName("http://www.oscarmcmaster.org/AR2005", "customLab2");
        CUSTOMLAB3$18 = new QName("http://www.oscarmcmaster.org/AR2005", "customLab3");
        CUSTOMLAB4$20 = new QName("http://www.oscarmcmaster.org/AR2005", "customLab4");
    }

    /**
     * XMLBeans implementation class for the BloodGroup enumeration type.
     *
     * This class provides the XMLBeans binding for blood group enumeration values (A, B, AB, O).
     *
     * @since 2026-01-24
     */
    public static class BloodGroupImpl extends JavaStringEnumerationHolderEx implements BloodGroup
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new BloodGroupImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type for this enumeration
         */
        public BloodGroupImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new BloodGroupImpl instance with the specified schema type and validation flag.
         *
         * @param sType SchemaType the XMLBeans schema type for this enumeration
         * @param b boolean flag controlling validation behavior
         */
        protected BloodGroupImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * XMLBeans implementation class for the Rh factor enumeration type.
     *
     * This class provides the XMLBeans binding for Rh factor enumeration values (positive or negative).
     *
     * @since 2026-01-24
     */
    public static class RhImpl extends JavaStringEnumerationHolderEx implements Rh
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new RhImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type for this enumeration
         */
        public RhImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new RhImpl instance with the specified schema type and validation flag.
         *
         * @param sType SchemaType the XMLBeans schema type for this enumeration
         * @param b boolean flag controlling validation behavior
         */
        protected RhImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * XMLBeans implementation class for the GBS (Group B Streptococcus) enumeration type.
     *
     * This class provides the XMLBeans binding for GBS screening result enumeration values.
     *
     * @since 2026-01-24
     */
    public static class GBSImpl extends JavaStringEnumerationHolderEx implements GBS
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new GBSImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type for this enumeration
         */
        public GBSImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new GBSImpl instance with the specified schema type and validation flag.
         *
         * @param sType SchemaType the XMLBeans schema type for this enumeration
         * @param b boolean flag controlling validation behavior
         */
        protected GBSImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }
}
