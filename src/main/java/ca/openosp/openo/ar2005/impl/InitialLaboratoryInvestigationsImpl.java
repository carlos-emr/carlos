package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.impl.values.JavaFloatHolderEx;
import org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx;
import ca.openosp.openo.ar2005.CustomLab;
import ca.openosp.openo.ar2005.PrenatalGeneticScreeningType;
import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.InitialLaboratoryInvestigations;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Apache XMLBeans implementation class for managing initial laboratory investigations data in the British Columbia
 * Antenatal Record 2005 (AR2005) form.
 *
 * This class provides a complete XML-based data structure for storing and managing prenatal laboratory test results
 * that are typically performed during the initial stages of pregnancy care. It encapsulates essential screening tests
 * including infectious disease screening (HIV, Hepatitis B, syphilis, gonorrhea, chlamydia, rubella), hematological
 * tests (hemoglobin, MCV, ABO blood type, Rh factor, antibody screening), sickle cell screening, urinalysis,
 * cervical cancer screening (Pap test), prenatal genetic screening results, and custom laboratory tests.
 *
 * This implementation extends Apache XMLBeans' XmlComplexContentImpl to provide thread-safe XML data binding
 * for healthcare-specific laboratory data types. All accessor methods are synchronized using the XMLBeans
 * monitor pattern to ensure data integrity in multi-threaded healthcare application environments.
 *
 * The class supports both standard Java type accessors (getString, setString, etc.) and XMLBeans-specific
 * accessors (xget*, xset*) that work directly with XML type objects, allowing for flexible data manipulation
 * and XML schema validation.
 *
 * <strong>Healthcare Context:</strong>
 * This component is part of the British Columbia Antenatal Record (BCAR) system, which follows provincial
 * guidelines for prenatal care documentation. The laboratory investigations tracked by this class represent
 * standard screening tests recommended for all pregnant individuals in British Columbia during their first
 * prenatal visit.
 *
 * <strong>Key Laboratory Tests Managed:</strong>
 * <ul>
 *   <li><strong>Infectious Disease Screening:</strong> HIV (with counseling flag), Hepatitis B surface antigen (HBsAg),
 *       VDRL/syphilis, gonorrhea, chlamydia, rubella immunity</li>
 *   <li><strong>Hematological Tests:</strong> Hemoglobin (Hb), mean corpuscular volume (MCV), ABO blood type,
 *       Rh factor, antibody screening</li>
 *   <li><strong>Genetic Screening:</strong> Sickle cell screening, prenatal genetic screening (integrated/sequential
 *       screening for chromosomal abnormalities)</li>
 *   <li><strong>Other Tests:</strong> Urinalysis, Pap test results with date, custom laboratory tests (2 configurable slots)</li>
 * </ul>
 *
 * <strong>Thread Safety:</strong>
 * All public methods use synchronized blocks with XMLBeans' internal monitor to ensure thread-safe access
 * to the underlying XML store, making this class safe for use in concurrent healthcare application environments.
 *
 * @see ca.openosp.openo.ar2005.InitialLaboratoryInvestigations
 * @see ca.openosp.openo.ar2005.CustomLab
 * @see ca.openosp.openo.ar2005.PrenatalGeneticScreeningType
 * @since 2026-01-24
 */
public class InitialLaboratoryInvestigationsImpl extends XmlComplexContentImpl implements InitialLaboratoryInvestigations
{
    private static final long serialVersionUID = 1L;
    private static final QName HBRESULT$0;
    private static final QName HIVRESULT$2;
    private static final QName HIVCOUNSEL$4;
    private static final QName LASTPAPDATE$6;
    private static final QName PAPRESULT$8;
    private static final QName MCVRESULT$10;
    private static final QName ABORESULT$12;
    private static final QName RHRESULT$14;
    private static final QName ANTIBODYRESULT$16;
    private static final QName GCRESULTGONORRHEA$18;
    private static final QName GCRESULTCHLAMYDIA$20;
    private static final QName RUBELLARESULT$22;
    private static final QName URINERESULT$24;
    private static final QName HBSAGRESULT$26;
    private static final QName VDRLRESULT$28;
    private static final QName SICKLECELLRESULT$30;
    private static final QName PRENATALGENERICSCREENING$32;
    private static final QName CUSTOMLAB1$34;
    private static final QName CUSTOMLAB2$36;

    /**
     * Constructs a new InitialLaboratoryInvestigationsImpl instance with the specified schema type.
     *
     * This constructor is typically called by the Apache XMLBeans framework during XML deserialization
     * or when creating new instances programmatically. It initializes the underlying XML data structure
     * according to the provided schema type definition.
     *
     * @param sType SchemaType the XML schema type definition for this laboratory investigations object
     */
    public InitialLaboratoryInvestigationsImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the hemoglobin (Hb) test result value.
     *
     * Hemoglobin is measured to screen for anemia during pregnancy, which is common due to increased
     * blood volume and iron demands. Normal pregnancy ranges are typically 110-140 g/L (11-14 g/dL).
     *
     * @return String the hemoglobin result value, or null if not set
     */
    public String getHbResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HBRESULT$0, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the hemoglobin (Hb) test result as an XMLBeans XmlString object.
     *
     * This method provides access to the XML-typed representation of the hemoglobin result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return XmlString the hemoglobin result as an XML string type, or null if not set
     */
    public XmlString xgetHbResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HBRESULT$0, 0);
            return target;
        }
    }

    /**
     * Sets the hemoglobin (Hb) test result value.
     *
     * @param hbResult String the hemoglobin result value to set
     */
    public void setHbResult(final String hbResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HBRESULT$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.HBRESULT$0);
            }
            target.setStringValue(hbResult);
        }
    }

    /**
     * Sets the hemoglobin (Hb) test result using an XMLBeans XmlString object.
     *
     * This method allows setting the hemoglobin result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param hbResult XmlString the hemoglobin result as an XML string type
     */
    public void xsetHbResult(final XmlString hbResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HBRESULT$0, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.HBRESULT$0);
            }
            target.set((XmlObject)hbResult);
        }
    }

    /**
     * Gets the HIV test result as an enumerated value.
     *
     * HIV screening is a standard component of prenatal care to identify infection early,
     * enable appropriate treatment to prevent mother-to-child transmission, and ensure
     * proper obstetric management.
     *
     * @return HivResult.Enum the HIV test result enumeration (e.g., NEGATIVE, POSITIVE, DECLINED), or null if not set
     */
    public HivResult.Enum getHivResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HIVRESULT$2, 0);
            if (target == null) {
                return null;
            }
            return (HivResult.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the HIV test result as an XMLBeans HivResult type object.
     *
     * This method provides access to the XML-typed representation of the HIV result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return HivResult the HIV test result as an XML enumeration type, or null if not set
     */
    public HivResult xgetHivResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            HivResult target = null;
            target = (HivResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HIVRESULT$2, 0);
            return target;
        }
    }

    /**
     * Sets the HIV test result using an enumerated value.
     *
     * @param hivResult HivResult.Enum the HIV test result enumeration to set (e.g., NEGATIVE, POSITIVE, DECLINED)
     */
    public void setHivResult(final HivResult.Enum hivResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HIVRESULT$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.HIVRESULT$2);
            }
            target.setEnumValue((StringEnumAbstractBase)hivResult);
        }
    }

    /**
     * Sets the HIV test result using an XMLBeans HivResult type object.
     *
     * This method allows setting the HIV result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param hivResult HivResult the HIV test result as an XML enumeration type
     */
    public void xsetHivResult(final HivResult hivResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            HivResult target = null;
            target = (HivResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HIVRESULT$2, 0);
            if (target == null) {
                target = (HivResult)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.HIVRESULT$2);
            }
            target.set((XmlObject)hivResult);
        }
    }

    /**
     * Gets the HIV counseling completion status.
     *
     * Indicates whether pre-test or post-test HIV counseling was provided to the patient.
     * Counseling is an important component of HIV testing protocols in prenatal care,
     * covering topics such as transmission, prevention, and treatment options.
     *
     * @return boolean true if HIV counseling was completed, false otherwise
     */
    public boolean getHivCounsel() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HIVCOUNSEL$4, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the HIV counseling completion status as an XMLBeans XmlBoolean object.
     *
     * This method provides access to the XML-typed representation of the counseling status,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return XmlBoolean the HIV counseling status as an XML boolean type, or null if not set
     */
    public XmlBoolean xgetHivCounsel() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HIVCOUNSEL$4, 0);
            return target;
        }
    }

    /**
     * Sets the HIV counseling completion status.
     *
     * @param hivCounsel boolean true if HIV counseling was completed, false otherwise
     */
    public void setHivCounsel(final boolean hivCounsel) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HIVCOUNSEL$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.HIVCOUNSEL$4);
            }
            target.setBooleanValue(hivCounsel);
        }
    }

    /**
     * Sets the HIV counseling completion status using an XMLBeans XmlBoolean object.
     *
     * This method allows setting the counseling status using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param hivCounsel XmlBoolean the HIV counseling status as an XML boolean type
     */
    public void xsetHivCounsel(final XmlBoolean hivCounsel) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HIVCOUNSEL$4, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.HIVCOUNSEL$4);
            }
            target.set((XmlObject)hivCounsel);
        }
    }

    /**
     * Gets the date of the patient's last Pap test (Papanicolaou test).
     *
     * The Pap test is a cervical cancer screening test. Recording the last Pap test date helps
     * determine if cervical cancer screening is due during the prenatal period, following current
     * screening guidelines.
     *
     * @return Calendar the date of the last Pap test, or null if not set
     */
    public Calendar getLastPapDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Gets the date of the last Pap test as an XMLBeans XmlDate object.
     *
     * This method provides access to the XML-typed representation of the Pap test date,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return XmlDate the last Pap test date as an XML date type, or null if not set
     */
    public XmlDate xgetLastPapDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6, 0);
            return target;
        }
    }

    /**
     * Checks if the last Pap test date is explicitly set to nil (null with schema validation).
     *
     * @return boolean true if the last Pap test date is set to nil, false otherwise
     */
    public boolean isNilLastPapDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the date of the patient's last Pap test.
     *
     * @param lastPapDate Calendar the date of the last Pap test
     */
    public void setLastPapDate(final Calendar lastPapDate) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6);
            }
            target.setCalendarValue(lastPapDate);
        }
    }

    /**
     * Sets the date of the last Pap test using an XMLBeans XmlDate object.
     *
     * This method allows setting the Pap test date using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param lastPapDate XmlDate the last Pap test date as an XML date type
     */
    public void xsetLastPapDate(final XmlDate lastPapDate) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6);
            }
            target.set((XmlObject)lastPapDate);
        }
    }

    /**
     * Sets the last Pap test date to nil (explicitly null with schema validation).
     *
     * This method marks the date element as nil in the XML structure, which is different
     * from simply not having the element present.
     */
    public void setNilLastPapDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.LASTPAPDATE$6);
            }
            target.setNil();
        }
    }

    /**
     * Gets the Pap test (Papanicolaou test) result.
     *
     * The Pap test result indicates findings from cervical cancer screening, such as normal,
     * ASCUS (atypical squamous cells of undetermined significance), LSIL (low-grade squamous
     * intraepithelial lesion), HSIL (high-grade squamous intraepithelial lesion), or other findings.
     *
     * @return String the Pap test result, or null if not set
     */
    public String getPapResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.PAPRESULT$8, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the Pap test result as an XMLBeans XmlString object.
     *
     * This method provides access to the XML-typed representation of the Pap test result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return XmlString the Pap test result as an XML string type, or null if not set
     */
    public XmlString xgetPapResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.PAPRESULT$8, 0);
            return target;
        }
    }

    /**
     * Sets the Pap test result.
     *
     * @param papResult String the Pap test result to set
     */
    public void setPapResult(final String papResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.PAPRESULT$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.PAPRESULT$8);
            }
            target.setStringValue(papResult);
        }
    }

    /**
     * Sets the Pap test result using an XMLBeans XmlString object.
     *
     * This method allows setting the Pap test result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param papResult XmlString the Pap test result as an XML string type
     */
    public void xsetPapResult(final XmlString papResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.PAPRESULT$8, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.PAPRESULT$8);
            }
            target.set((XmlObject)papResult);
        }
    }

    /**
     * Gets the MCV (Mean Corpuscular Volume) test result.
     *
     * MCV measures the average size of red blood cells and helps classify anemia types.
     * Normal range is typically 80-100 femtoliters (fL). Low MCV suggests iron deficiency anemia
     * or thalassemia, while high MCV may indicate vitamin B12 or folate deficiency.
     *
     * @return float the MCV result value in femtoliters, or 0.0f if not set
     */
    public float getMcvResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10, 0);
            if (target == null) {
                return 0.0f;
            }
            return target.getFloatValue();
        }
    }

    /**
     * Gets the MCV result as an XMLBeans McvResult type object.
     *
     * This method provides access to the XML-typed representation of the MCV result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return McvResult the MCV result as an XML float type, or null if not set
     */
    public McvResult xgetMcvResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            McvResult target = null;
            target = (McvResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10, 0);
            return target;
        }
    }

    /**
     * Checks if the MCV result is explicitly set to nil (null with schema validation).
     *
     * @return boolean true if the MCV result is set to nil, false otherwise
     */
    public boolean isNilMcvResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            McvResult target = null;
            target = (McvResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the MCV (Mean Corpuscular Volume) test result.
     *
     * @param mcvResult float the MCV result value in femtoliters
     */
    public void setMcvResult(final float mcvResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10);
            }
            target.setFloatValue(mcvResult);
        }
    }

    /**
     * Sets the MCV result using an XMLBeans McvResult type object.
     *
     * This method allows setting the MCV result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param mcvResult McvResult the MCV result as an XML float type
     */
    public void xsetMcvResult(final McvResult mcvResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            McvResult target = null;
            target = (McvResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10, 0);
            if (target == null) {
                target = (McvResult)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10);
            }
            target.set((XmlObject)mcvResult);
        }
    }

    /**
     * Sets the MCV result to nil (explicitly null with schema validation).
     *
     * This method marks the MCV element as nil in the XML structure, which is different
     * from simply not having the element present.
     */
    public void setNilMcvResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            McvResult target = null;
            target = (McvResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10, 0);
            if (target == null) {
                target = (McvResult)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.MCVRESULT$10);
            }
            target.setNil();
        }
    }

    /**
     * Gets the ABO blood type test result as an enumerated value.
     *
     * ABO blood typing determines the patient's blood group (A, B, AB, or O), which is essential
     * for blood transfusion compatibility and for identifying potential blood group incompatibility
     * issues during pregnancy (such as ABO hemolytic disease of the newborn).
     *
     * @return AboResult.Enum the ABO blood type enumeration (A, B, AB, or O), or null if not set
     */
    public AboResult.Enum getAboResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.ABORESULT$12, 0);
            if (target == null) {
                return null;
            }
            return (AboResult.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the ABO blood type result as an XMLBeans AboResult type object.
     *
     * This method provides access to the XML-typed representation of the ABO blood type,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return AboResult the ABO blood type as an XML enumeration type, or null if not set
     */
    public AboResult xgetAboResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AboResult target = null;
            target = (AboResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.ABORESULT$12, 0);
            return target;
        }
    }

    /**
     * Sets the ABO blood type test result using an enumerated value.
     *
     * @param aboResult AboResult.Enum the ABO blood type enumeration to set (A, B, AB, or O)
     */
    public void setAboResult(final AboResult.Enum aboResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.ABORESULT$12, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.ABORESULT$12);
            }
            target.setEnumValue((StringEnumAbstractBase)aboResult);
        }
    }

    /**
     * Sets the ABO blood type result using an XMLBeans AboResult type object.
     *
     * This method allows setting the ABO blood type using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param aboResult AboResult the ABO blood type as an XML enumeration type
     */
    public void xsetAboResult(final AboResult aboResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AboResult target = null;
            target = (AboResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.ABORESULT$12, 0);
            if (target == null) {
                target = (AboResult)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.ABORESULT$12);
            }
            target.set((XmlObject)aboResult);
        }
    }

    /**
     * Gets the Rh factor (Rhesus factor) test result as an enumerated value.
     *
     * Rh typing determines whether the patient has Rh-positive or Rh-negative blood. This is critical
     * in pregnancy care to identify Rh incompatibility, which can lead to hemolytic disease of the newborn.
     * Rh-negative pregnant individuals carrying Rh-positive fetuses may require RhIG (Rh immune globulin)
     * prophylaxis.
     *
     * @return RhResult.Enum the Rh factor enumeration (POSITIVE or NEGATIVE), or null if not set
     */
    public RhResult.Enum getRhResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.RHRESULT$14, 0);
            if (target == null) {
                return null;
            }
            return (RhResult.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the Rh factor result as an XMLBeans RhResult type object.
     *
     * This method provides access to the XML-typed representation of the Rh factor,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return RhResult the Rh factor as an XML enumeration type, or null if not set
     */
    public RhResult xgetRhResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RhResult target = null;
            target = (RhResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.RHRESULT$14, 0);
            return target;
        }
    }

    /**
     * Sets the Rh factor test result using an enumerated value.
     *
     * @param rhResult RhResult.Enum the Rh factor enumeration to set (POSITIVE or NEGATIVE)
     */
    public void setRhResult(final RhResult.Enum rhResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.RHRESULT$14, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.RHRESULT$14);
            }
            target.setEnumValue((StringEnumAbstractBase)rhResult);
        }
    }

    /**
     * Sets the Rh factor result using an XMLBeans RhResult type object.
     *
     * This method allows setting the Rh factor using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param rhResult RhResult the Rh factor as an XML enumeration type
     */
    public void xsetRhResult(final RhResult rhResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RhResult target = null;
            target = (RhResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.RHRESULT$14, 0);
            if (target == null) {
                target = (RhResult)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.RHRESULT$14);
            }
            target.set((XmlObject)rhResult);
        }
    }

    /**
     * Gets the red blood cell antibody screening test result.
     *
     * Antibody screening detects irregular antibodies (other than ABO) that could cause hemolytic disease
     * of the newborn or transfusion reactions. Common antibodies include anti-D, anti-K, anti-E, and anti-c.
     * A positive result requires antibody identification and may necessitate specialized obstetric management.
     *
     * @return String the antibody screening result, or null if not set
     */
    public String getAntibodyResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.ANTIBODYRESULT$16, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the antibody screening result as an XMLBeans XmlString object.
     *
     * This method provides access to the XML-typed representation of the antibody screening result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return XmlString the antibody screening result as an XML string type, or null if not set
     */
    public XmlString xgetAntibodyResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.ANTIBODYRESULT$16, 0);
            return target;
        }
    }

    /**
     * Sets the red blood cell antibody screening test result.
     *
     * @param antibodyResult String the antibody screening result to set
     */
    public void setAntibodyResult(final String antibodyResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.ANTIBODYRESULT$16, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.ANTIBODYRESULT$16);
            }
            target.setStringValue(antibodyResult);
        }
    }

    /**
     * Sets the antibody screening result using an XMLBeans XmlString object.
     *
     * This method allows setting the antibody screening result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param antibodyResult XmlString the antibody screening result as an XML string type
     */
    public void xsetAntibodyResult(final XmlString antibodyResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.ANTIBODYRESULT$16, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.ANTIBODYRESULT$16);
            }
            target.set((XmlObject)antibodyResult);
        }
    }

    /**
     * Gets the gonorrhea screening test result as an enumerated value.
     *
     * Gonorrhea (Neisseria gonorrhoeae) screening is important in prenatal care to prevent transmission
     * to the newborn during delivery, which can cause neonatal conjunctivitis and systemic infection.
     * Detection allows for appropriate antibiotic treatment before delivery.
     *
     * @return GcResultGonorrhea.Enum the gonorrhea test result enumeration (e.g., NEGATIVE, POSITIVE), or null if not set
     */
    public GcResultGonorrhea.Enum getGcResultGonorrhea() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTGONORRHEA$18, 0);
            if (target == null) {
                return null;
            }
            return (GcResultGonorrhea.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the gonorrhea test result as an XMLBeans GcResultGonorrhea type object.
     *
     * This method provides access to the XML-typed representation of the gonorrhea test result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return GcResultGonorrhea the gonorrhea test result as an XML enumeration type, or null if not set
     */
    public GcResultGonorrhea xgetGcResultGonorrhea() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GcResultGonorrhea target = null;
            target = (GcResultGonorrhea)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTGONORRHEA$18, 0);
            return target;
        }
    }

    /**
     * Sets the gonorrhea screening test result using an enumerated value.
     *
     * @param gcResultGonorrhea GcResultGonorrhea.Enum the gonorrhea test result enumeration to set
     */
    public void setGcResultGonorrhea(final GcResultGonorrhea.Enum gcResultGonorrhea) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTGONORRHEA$18, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTGONORRHEA$18);
            }
            target.setEnumValue((StringEnumAbstractBase)gcResultGonorrhea);
        }
    }

    /**
     * Sets the gonorrhea test result using an XMLBeans GcResultGonorrhea type object.
     *
     * This method allows setting the gonorrhea test result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param gcResultGonorrhea GcResultGonorrhea the gonorrhea test result as an XML enumeration type
     */
    public void xsetGcResultGonorrhea(final GcResultGonorrhea gcResultGonorrhea) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GcResultGonorrhea target = null;
            target = (GcResultGonorrhea)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTGONORRHEA$18, 0);
            if (target == null) {
                target = (GcResultGonorrhea)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTGONORRHEA$18);
            }
            target.set((XmlObject)gcResultGonorrhea);
        }
    }

    /**
     * Gets the chlamydia screening test result as an enumerated value.
     *
     * Chlamydia trachomatis screening is essential in prenatal care to prevent transmission to the newborn
     * during delivery, which can cause neonatal conjunctivitis and pneumonia. Early detection allows for
     * appropriate antibiotic treatment during pregnancy.
     *
     * @return GcResultChlamydia.Enum the chlamydia test result enumeration (e.g., NEGATIVE, POSITIVE), or null if not set
     */
    public GcResultChlamydia.Enum getGcResultChlamydia() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTCHLAMYDIA$20, 0);
            if (target == null) {
                return null;
            }
            return (GcResultChlamydia.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the chlamydia test result as an XMLBeans GcResultChlamydia type object.
     *
     * This method provides access to the XML-typed representation of the chlamydia test result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return GcResultChlamydia the chlamydia test result as an XML enumeration type, or null if not set
     */
    public GcResultChlamydia xgetGcResultChlamydia() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GcResultChlamydia target = null;
            target = (GcResultChlamydia)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTCHLAMYDIA$20, 0);
            return target;
        }
    }

    /**
     * Sets the chlamydia screening test result using an enumerated value.
     *
     * @param gcResultChlamydia GcResultChlamydia.Enum the chlamydia test result enumeration to set
     */
    public void setGcResultChlamydia(final GcResultChlamydia.Enum gcResultChlamydia) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTCHLAMYDIA$20, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTCHLAMYDIA$20);
            }
            target.setEnumValue((StringEnumAbstractBase)gcResultChlamydia);
        }
    }

    /**
     * Sets the chlamydia test result using an XMLBeans GcResultChlamydia type object.
     *
     * This method allows setting the chlamydia test result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param gcResultChlamydia GcResultChlamydia the chlamydia test result as an XML enumeration type
     */
    public void xsetGcResultChlamydia(final GcResultChlamydia gcResultChlamydia) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GcResultChlamydia target = null;
            target = (GcResultChlamydia)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTCHLAMYDIA$20, 0);
            if (target == null) {
                target = (GcResultChlamydia)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.GCRESULTCHLAMYDIA$20);
            }
            target.set((XmlObject)gcResultChlamydia);
        }
    }

    /**
     * Gets the rubella immunity test result.
     *
     * Rubella (German measles) immunity testing determines if the pregnant individual has protective antibodies
     * against rubella. Rubella infection during pregnancy can cause congenital rubella syndrome with serious fetal
     * abnormalities. Non-immune individuals should avoid exposure and may be offered vaccination post-partum.
     *
     * @return String the rubella immunity test result, or null if not set
     */
    public String getRubellaResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.RUBELLARESULT$22, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the rubella immunity test result as an XMLBeans XmlString object.
     *
     * This method provides access to the XML-typed representation of the rubella immunity result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return XmlString the rubella immunity result as an XML string type, or null if not set
     */
    public XmlString xgetRubellaResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.RUBELLARESULT$22, 0);
            return target;
        }
    }

    /**
     * Sets the rubella immunity test result.
     *
     * @param rubellaResult String the rubella immunity test result to set
     */
    public void setRubellaResult(final String rubellaResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.RUBELLARESULT$22, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.RUBELLARESULT$22);
            }
            target.setStringValue(rubellaResult);
        }
    }

    /**
     * Sets the rubella immunity result using an XMLBeans XmlString object.
     *
     * This method allows setting the rubella immunity result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param rubellaResult XmlString the rubella immunity result as an XML string type
     */
    public void xsetRubellaResult(final XmlString rubellaResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.RUBELLARESULT$22, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.RUBELLARESULT$22);
            }
            target.set((XmlObject)rubellaResult);
        }
    }

    /**
     * Gets the urinalysis test result.
     *
     * Urinalysis during prenatal care screens for conditions such as proteinuria (potential preeclampsia),
     * glycosuria (gestational diabetes), bacteriuria (urinary tract infection), and other abnormalities.
     *
     * @return String the urinalysis result, or null if not set
     */
    public String getUrineResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.URINERESULT$24, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the urinalysis result as an XMLBeans XmlString object.
     *
     * This method provides access to the XML-typed representation of the urinalysis result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return XmlString the urinalysis result as an XML string type, or null if not set
     */
    public XmlString xgetUrineResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.URINERESULT$24, 0);
            return target;
        }
    }

    /**
     * Sets the urinalysis test result.
     *
     * @param urineResult String the urinalysis result to set
     */
    public void setUrineResult(final String urineResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.URINERESULT$24, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.URINERESULT$24);
            }
            target.setStringValue(urineResult);
        }
    }

    /**
     * Sets the urinalysis result using an XMLBeans XmlString object.
     *
     * This method allows setting the urinalysis result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param urineResult XmlString the urinalysis result as an XML string type
     */
    public void xsetUrineResult(final XmlString urineResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.URINERESULT$24, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.URINERESULT$24);
            }
            target.set((XmlObject)urineResult);
        }
    }

    /**
     * Gets the Hepatitis B surface antigen (HBsAg) test result as an enumerated value.
     *
     * HBsAg screening identifies Hepatitis B virus infection in pregnant individuals. A positive result
     * requires newborn immunoprophylaxis with Hepatitis B vaccine and immunoglobulin within 12 hours of birth
     * to prevent vertical transmission.
     *
     * @return HbsAgResult.Enum the HBsAg test result enumeration (e.g., NEGATIVE, POSITIVE), or null if not set
     */
    public HbsAgResult.Enum getHbsAgResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HBSAGRESULT$26, 0);
            if (target == null) {
                return null;
            }
            return (HbsAgResult.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the HBsAg test result as an XMLBeans HbsAgResult type object.
     *
     * This method provides access to the XML-typed representation of the HBsAg test result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return HbsAgResult the HBsAg test result as an XML enumeration type, or null if not set
     */
    public HbsAgResult xgetHbsAgResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            HbsAgResult target = null;
            target = (HbsAgResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HBSAGRESULT$26, 0);
            return target;
        }
    }

    /**
     * Sets the Hepatitis B surface antigen (HBsAg) test result using an enumerated value.
     *
     * @param hbsAgResult HbsAgResult.Enum the HBsAg test result enumeration to set
     */
    public void setHbsAgResult(final HbsAgResult.Enum hbsAgResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HBSAGRESULT$26, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.HBSAGRESULT$26);
            }
            target.setEnumValue((StringEnumAbstractBase)hbsAgResult);
        }
    }

    /**
     * Sets the HBsAg test result using an XMLBeans HbsAgResult type object.
     *
     * This method allows setting the HBsAg test result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param hbsAgResult HbsAgResult the HBsAg test result as an XML enumeration type
     */
    public void xsetHbsAgResult(final HbsAgResult hbsAgResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            HbsAgResult target = null;
            target = (HbsAgResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.HBSAGRESULT$26, 0);
            if (target == null) {
                target = (HbsAgResult)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.HBSAGRESULT$26);
            }
            target.set((XmlObject)hbsAgResult);
        }
    }

    /**
     * Gets the VDRL (Venereal Disease Research Laboratory) syphilis test result as an enumerated value.
     *
     * VDRL or similar syphilis screening tests detect Treponema pallidum infection. Untreated syphilis during
     * pregnancy can cause congenital syphilis, stillbirth, or preterm birth. Positive results require confirmatory
     * testing and appropriate antibiotic treatment.
     *
     * @return VdrlResult.Enum the VDRL test result enumeration (e.g., NON_REACTIVE, REACTIVE), or null if not set
     */
    public VdrlResult.Enum getVdrlResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.VDRLRESULT$28, 0);
            if (target == null) {
                return null;
            }
            return (VdrlResult.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the VDRL test result as an XMLBeans VdrlResult type object.
     *
     * This method provides access to the XML-typed representation of the VDRL test result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return VdrlResult the VDRL test result as an XML enumeration type, or null if not set
     */
    public VdrlResult xgetVdrlResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            VdrlResult target = null;
            target = (VdrlResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.VDRLRESULT$28, 0);
            return target;
        }
    }

    /**
     * Sets the VDRL syphilis test result using an enumerated value.
     *
     * @param vdrlResult VdrlResult.Enum the VDRL test result enumeration to set
     */
    public void setVdrlResult(final VdrlResult.Enum vdrlResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.VDRLRESULT$28, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.VDRLRESULT$28);
            }
            target.setEnumValue((StringEnumAbstractBase)vdrlResult);
        }
    }

    /**
     * Sets the VDRL test result using an XMLBeans VdrlResult type object.
     *
     * This method allows setting the VDRL test result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param vdrlResult VdrlResult the VDRL test result as an XML enumeration type
     */
    public void xsetVdrlResult(final VdrlResult vdrlResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            VdrlResult target = null;
            target = (VdrlResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.VDRLRESULT$28, 0);
            if (target == null) {
                target = (VdrlResult)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.VDRLRESULT$28);
            }
            target.set((XmlObject)vdrlResult);
        }
    }

    /**
     * Gets the sickle cell screening test result as an enumerated value.
     *
     * Sickle cell screening identifies hemoglobin S trait or disease. This is particularly important for individuals
     * of African, Mediterranean, Middle Eastern, or South Asian ancestry. Identification allows for genetic counseling,
     * partner testing, and appropriate obstetric management if needed.
     *
     * @return SickleCellResult.Enum the sickle cell test result enumeration (e.g., NEGATIVE, POSITIVE, TRAIT), or null if not set
     */
    public SickleCellResult.Enum getSickleCellResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.SICKLECELLRESULT$30, 0);
            if (target == null) {
                return null;
            }
            return (SickleCellResult.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the sickle cell test result as an XMLBeans SickleCellResult type object.
     *
     * This method provides access to the XML-typed representation of the sickle cell test result,
     * allowing for direct XML manipulation and schema validation.
     *
     * @return SickleCellResult the sickle cell test result as an XML enumeration type, or null if not set
     */
    public SickleCellResult xgetSickleCellResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SickleCellResult target = null;
            target = (SickleCellResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.SICKLECELLRESULT$30, 0);
            return target;
        }
    }

    /**
     * Sets the sickle cell screening test result using an enumerated value.
     *
     * @param sickleCellResult SickleCellResult.Enum the sickle cell test result enumeration to set
     */
    public void setSickleCellResult(final SickleCellResult.Enum sickleCellResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.SICKLECELLRESULT$30, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.SICKLECELLRESULT$30);
            }
            target.setEnumValue((StringEnumAbstractBase)sickleCellResult);
        }
    }

    /**
     * Sets the sickle cell test result using an XMLBeans SickleCellResult type object.
     *
     * This method allows setting the sickle cell test result using an XML-typed value,
     * enabling direct XML manipulation and ensuring schema compliance.
     *
     * @param sickleCellResult SickleCellResult the sickle cell test result as an XML enumeration type
     */
    public void xsetSickleCellResult(final SickleCellResult sickleCellResult) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SickleCellResult target = null;
            target = (SickleCellResult)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.SICKLECELLRESULT$30, 0);
            if (target == null) {
                target = (SickleCellResult)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.SICKLECELLRESULT$30);
            }
            target.set((XmlObject)sickleCellResult);
        }
    }

    /**
     * Gets the prenatal genetic screening information.
     *
     * Prenatal genetic screening (such as integrated prenatal screening, sequential screening, or non-invasive
     * prenatal testing) assesses the risk of chromosomal abnormalities including Down syndrome (trisomy 21),
     * Edwards syndrome (trisomy 18), and neural tube defects. This complex type contains screening test details,
     * results, and risk assessments.
     *
     * @return PrenatalGeneticScreeningType the prenatal genetic screening data, or null if not set
     */
    public PrenatalGeneticScreeningType getPrenatalGenericScreening() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PrenatalGeneticScreeningType target = null;
            target = (PrenatalGeneticScreeningType)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.PRENATALGENERICSCREENING$32, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the prenatal genetic screening information.
     *
     * @param prenatalGenericScreening PrenatalGeneticScreeningType the prenatal genetic screening data to set
     */
    public void setPrenatalGenericScreening(final PrenatalGeneticScreeningType prenatalGenericScreening) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PrenatalGeneticScreeningType target = null;
            target = (PrenatalGeneticScreeningType)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.PRENATALGENERICSCREENING$32, 0);
            if (target == null) {
                target = (PrenatalGeneticScreeningType)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.PRENATALGENERICSCREENING$32);
            }
            target.set((XmlObject)prenatalGenericScreening);
        }
    }

    /**
     * Creates and adds a new prenatal genetic screening element to the XML structure.
     *
     * This factory method creates a new PrenatalGeneticScreeningType instance and adds it to the
     * XML document, allowing programmatic population of screening data.
     *
     * @return PrenatalGeneticScreeningType the newly created prenatal genetic screening object
     */
    public PrenatalGeneticScreeningType addNewPrenatalGenericScreening() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PrenatalGeneticScreeningType target = null;
            target = (PrenatalGeneticScreeningType)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.PRENATALGENERICSCREENING$32);
            return target;
        }
    }

    /**
     * Gets the first custom laboratory test information.
     *
     * Custom lab slots allow for recording additional laboratory tests not covered by the standard
     * prenatal screening panel. This could include specialized tests based on patient history, risk factors,
     * or regional practice variations.
     *
     * @return CustomLab the first custom laboratory test data, or null if not set
     */
    public CustomLab getCustomLab1() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.CUSTOMLAB1$34, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the first custom laboratory test information.
     *
     * @param customLab1 CustomLab the first custom laboratory test data to set
     */
    public void setCustomLab1(final CustomLab customLab1) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.CUSTOMLAB1$34, 0);
            if (target == null) {
                target = (CustomLab)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.CUSTOMLAB1$34);
            }
            target.set((XmlObject)customLab1);
        }
    }

    /**
     * Creates and adds a new first custom laboratory test element to the XML structure.
     *
     * This factory method creates a new CustomLab instance for the first custom lab slot and adds it
     * to the XML document, allowing programmatic population of custom test data.
     *
     * @return CustomLab the newly created first custom laboratory test object
     */
    public CustomLab addNewCustomLab1() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.CUSTOMLAB1$34);
            return target;
        }
    }

    /**
     * Gets the second custom laboratory test information.
     *
     * This provides an additional slot for recording specialized laboratory tests not covered by the
     * standard prenatal screening panel.
     *
     * @return CustomLab the second custom laboratory test data, or null if not set
     */
    public CustomLab getCustomLab2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.CUSTOMLAB2$36, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the second custom laboratory test information.
     *
     * @param customLab2 CustomLab the second custom laboratory test data to set
     */
    public void setCustomLab2(final CustomLab customLab2) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(InitialLaboratoryInvestigationsImpl.CUSTOMLAB2$36, 0);
            if (target == null) {
                target = (CustomLab)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.CUSTOMLAB2$36);
            }
            target.set((XmlObject)customLab2);
        }
    }

    /**
     * Creates and adds a new second custom laboratory test element to the XML structure.
     *
     * This factory method creates a new CustomLab instance for the second custom lab slot and adds it
     * to the XML document, allowing programmatic population of custom test data.
     *
     * @return CustomLab the newly created second custom laboratory test object
     */
    public CustomLab addNewCustomLab2() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().add_element_user(InitialLaboratoryInvestigationsImpl.CUSTOMLAB2$36);
            return target;
        }
    }
    
    static {
        HBRESULT$0 = new QName("http://www.oscarmcmaster.org/AR2005", "hbResult");
        HIVRESULT$2 = new QName("http://www.oscarmcmaster.org/AR2005", "hivResult");
        HIVCOUNSEL$4 = new QName("http://www.oscarmcmaster.org/AR2005", "hivCounsel");
        LASTPAPDATE$6 = new QName("http://www.oscarmcmaster.org/AR2005", "lastPapDate");
        PAPRESULT$8 = new QName("http://www.oscarmcmaster.org/AR2005", "papResult");
        MCVRESULT$10 = new QName("http://www.oscarmcmaster.org/AR2005", "mcvResult");
        ABORESULT$12 = new QName("http://www.oscarmcmaster.org/AR2005", "aboResult");
        RHRESULT$14 = new QName("http://www.oscarmcmaster.org/AR2005", "rhResult");
        ANTIBODYRESULT$16 = new QName("http://www.oscarmcmaster.org/AR2005", "antibodyResult");
        GCRESULTGONORRHEA$18 = new QName("http://www.oscarmcmaster.org/AR2005", "gcResultGonorrhea");
        GCRESULTCHLAMYDIA$20 = new QName("http://www.oscarmcmaster.org/AR2005", "gcResultChlamydia");
        RUBELLARESULT$22 = new QName("http://www.oscarmcmaster.org/AR2005", "rubellaResult");
        URINERESULT$24 = new QName("http://www.oscarmcmaster.org/AR2005", "urineResult");
        HBSAGRESULT$26 = new QName("http://www.oscarmcmaster.org/AR2005", "hbsAgResult");
        VDRLRESULT$28 = new QName("http://www.oscarmcmaster.org/AR2005", "vdrlResult");
        SICKLECELLRESULT$30 = new QName("http://www.oscarmcmaster.org/AR2005", "sickleCellResult");
        PRENATALGENERICSCREENING$32 = new QName("http://www.oscarmcmaster.org/AR2005", "prenatalGenericScreening");
        CUSTOMLAB1$34 = new QName("http://www.oscarmcmaster.org/AR2005", "customLab1");
        CUSTOMLAB2$36 = new QName("http://www.oscarmcmaster.org/AR2005", "customLab2");
    }

    /**
     * Inner implementation class for the HIV test result enumeration type.
     *
     * This class extends Apache XMLBeans' JavaStringEnumerationHolderEx to provide XML binding
     * for the HivResult enumeration, supporting values such as NEGATIVE, POSITIVE, and DECLINED.
     *
     * @since 2026-01-24
     */
    public static class HivResultImpl extends JavaStringEnumerationHolderEx implements HivResult
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new HivResultImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public HivResultImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for HivResultImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected HivResultImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the MCV (Mean Corpuscular Volume) test result type.
     *
     * This class extends Apache XMLBeans' JavaFloatHolderEx to provide XML binding for the
     * MCV result as a floating-point value representing red blood cell size in femtoliters.
     *
     * @since 2026-01-24
     */
    public static class McvResultImpl extends JavaFloatHolderEx implements McvResult
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new McvResultImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public McvResultImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for McvResultImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected McvResultImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the ABO blood type result enumeration type.
     *
     * This class extends Apache XMLBeans' JavaStringEnumerationHolderEx to provide XML binding
     * for the AboResult enumeration, supporting blood type values A, B, AB, and O.
     *
     * @since 2026-01-24
     */
    public static class AboResultImpl extends JavaStringEnumerationHolderEx implements AboResult
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new AboResultImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public AboResultImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for AboResultImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected AboResultImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the Rh factor result enumeration type.
     *
     * This class extends Apache XMLBeans' JavaStringEnumerationHolderEx to provide XML binding
     * for the RhResult enumeration, supporting values POSITIVE and NEGATIVE.
     *
     * @since 2026-01-24
     */
    public static class RhResultImpl extends JavaStringEnumerationHolderEx implements RhResult
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new RhResultImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public RhResultImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for RhResultImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected RhResultImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the gonorrhea test result enumeration type.
     *
     * This class extends Apache XMLBeans' JavaStringEnumerationHolderEx to provide XML binding
     * for the GcResultGonorrhea enumeration, supporting values such as NEGATIVE and POSITIVE.
     *
     * @since 2026-01-24
     */
    public static class GcResultGonorrheaImpl extends JavaStringEnumerationHolderEx implements GcResultGonorrhea
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new GcResultGonorrheaImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public GcResultGonorrheaImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for GcResultGonorrheaImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected GcResultGonorrheaImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the chlamydia test result enumeration type.
     *
     * This class extends Apache XMLBeans' JavaStringEnumerationHolderEx to provide XML binding
     * for the GcResultChlamydia enumeration, supporting values such as NEGATIVE and POSITIVE.
     *
     * @since 2026-01-24
     */
    public static class GcResultChlamydiaImpl extends JavaStringEnumerationHolderEx implements GcResultChlamydia
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new GcResultChlamydiaImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public GcResultChlamydiaImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for GcResultChlamydiaImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected GcResultChlamydiaImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the Hepatitis B surface antigen (HBsAg) test result enumeration type.
     *
     * This class extends Apache XMLBeans' JavaStringEnumerationHolderEx to provide XML binding
     * for the HbsAgResult enumeration, supporting values such as NEGATIVE and POSITIVE.
     *
     * @since 2026-01-24
     */
    public static class HbsAgResultImpl extends JavaStringEnumerationHolderEx implements HbsAgResult
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new HbsAgResultImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public HbsAgResultImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for HbsAgResultImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected HbsAgResultImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the VDRL syphilis test result enumeration type.
     *
     * This class extends Apache XMLBeans' JavaStringEnumerationHolderEx to provide XML binding
     * for the VdrlResult enumeration, supporting values such as NON_REACTIVE and REACTIVE.
     *
     * @since 2026-01-24
     */
    public static class VdrlResultImpl extends JavaStringEnumerationHolderEx implements VdrlResult
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new VdrlResultImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public VdrlResultImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for VdrlResultImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected VdrlResultImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the sickle cell screening test result enumeration type.
     *
     * This class extends Apache XMLBeans' JavaStringEnumerationHolderEx to provide XML binding
     * for the SickleCellResult enumeration, supporting values such as NEGATIVE, POSITIVE, and TRAIT.
     *
     * @since 2026-01-24
     */
    public static class SickleCellResultImpl extends JavaStringEnumerationHolderEx implements SickleCellResult
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new SickleCellResultImpl with the specified schema type.
         *
         * @param sType SchemaType the XML schema type definition
         */
        public SickleCellResultImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for SickleCellResultImpl with schema type and boolean flag.
         *
         * @param sType SchemaType the XML schema type definition
         * @param b boolean flag for XMLBeans internal use
         */
        protected SickleCellResultImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }
}
