package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.impl.values.JavaFloatHolderEx;
import org.apache.xmlbeans.impl.values.JavaStringHolderEx;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlDate;
import org.apache.xmlbeans.SimpleValue;
import java.util.Calendar;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.SubsequentVisitItemType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for subsequent prenatal visit data tracking in the AR2005 (Antenatal Record 2005) schema.
 *
 * <p>This class provides a complete implementation for managing clinical data collected during routine prenatal
 * follow-up appointments. It handles the storage and retrieval of essential obstetric measurements and assessments
 * performed at each subsequent visit throughout the pregnancy, supporting comprehensive antenatal care documentation
 * in compliance with Canadian healthcare standards.</p>
 *
 * <p>The class manages the following clinical data elements:</p>
 * <ul>
 *   <li><b>Visit Date</b> - Calendar date of the prenatal appointment</li>
 *   <li><b>Gestational Age (GA)</b> - Pregnancy duration at time of visit, typically in weeks+days format</li>
 *   <li><b>Weight</b> - Maternal weight measurement in kilograms for tracking pregnancy weight gain</li>
 *   <li><b>Blood Pressure (BP)</b> - Systolic/diastolic reading for monitoring hypertension and preeclampsia risk</li>
 *   <li><b>Urine Protein (UrinePR)</b> - Protein level in urine sample, indicator for preeclampsia screening</li>
 *   <li><b>Urine Glucose (UrineGI)</b> - Glucose level in urine sample for gestational diabetes monitoring</li>
 *   <li><b>Symphysis-Fundal Height (SFH)</b> - Fundal height measurement in centimeters for fetal growth assessment</li>
 *   <li><b>Presentation/Position</b> - Fetal presentation (cephalic, breech, transverse) and position in uterus</li>
 *   <li><b>FHR/FM</b> - Fetal heart rate and fetal movements assessment</li>
 *   <li><b>Comments</b> - Free-text clinical notes and observations from the visit</li>
 * </ul>
 *
 * <p>This implementation extends {@link XmlComplexContentImpl} to provide XMLBeans-based XML serialization
 * and deserialization capabilities, allowing prenatal visit data to be stored, transmitted, and exchanged
 * in standardized XML format for interoperability with other healthcare systems and electronic health records.</p>
 *
 * <p>Each data element supports both standard getters/setters and XMLBeans-specific xget/xset methods for
 * type-safe XML manipulation. Many fields also support nil/null value handling to distinguish between
 * "not measured" and "measured with zero/empty value" states.</p>
 *
 * <p>The class includes three inner implementation classes for schema-bound types:</p>
 * <ul>
 *   <li>{@link GaImpl} - String-based gestational age representation with schema validation</li>
 *   <li>{@link WeightImpl} - Float-based weight measurement with schema constraints</li>
 *   <li>{@link BpImpl} - String-based blood pressure format with validation rules</li>
 * </ul>
 *
 * @see SubsequentVisitItemType
 * @see XmlComplexContentImpl
 * @since 2026-01-24
 */
public class SubsequentVisitItemTypeImpl extends XmlComplexContentImpl implements SubsequentVisitItemType
{
    private static final long serialVersionUID = 1L;
    private static final QName DATE$0;
    private static final QName GA$2;
    private static final QName WEIGHT$4;
    private static final QName BP$6;
    private static final QName URINEPR$8;
    private static final QName URINEGI$10;
    private static final QName SFH$12;
    private static final QName PRESENTATIONPOSITION$14;
    private static final QName FHRFM$16;
    private static final QName COMMENTS$18;

    /**
     * Constructs a new SubsequentVisitItemTypeImpl instance with the specified schema type.
     *
     * <p>This constructor initializes the XMLBeans implementation object with the provided
     * schema type definition, setting up the internal XML store for managing subsequent
     * prenatal visit data elements according to the AR2005 schema specifications.</p>
     *
     * @param sType SchemaType the schema type definition for this XML element
     */
    public SubsequentVisitItemTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the date of the prenatal visit.
     *
     * <p>Returns the appointment date when this subsequent prenatal visit occurred.
     * This date is essential for tracking the timeline of antenatal care and correlating
     * clinical measurements with gestational age progression.</p>
     *
     * @return Calendar the visit date, or null if not set
     */
    public Calendar getDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.DATE$0, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Retrieves the visit date as an XmlDate object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans XmlDate representation,
     * allowing for type-safe XML manipulation and schema validation of the date element.</p>
     *
     * @return XmlDate the visit date as an XMLBeans date object, or null if not set
     */
    public XmlDate xgetDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.DATE$0, 0);
            return target;
        }
    }

    /**
     * Checks whether the visit date element is explicitly set to nil.
     *
     * <p>Distinguishes between a date that is absent/not set and a date that is explicitly
     * marked as nil in the XML representation, which can be semantically significant in
     * some data exchange scenarios.</p>
     *
     * @return boolean true if the date is explicitly nil, false otherwise
     */
    public boolean isNilDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.DATE$0, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the date of the prenatal visit.
     *
     * <p>Records the appointment date when this subsequent prenatal visit occurred.
     * This establishes the temporal context for all clinical measurements and observations
     * documented in this visit record.</p>
     *
     * @param date Calendar the visit date to set
     */
    public void setDate(final Calendar date) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.DATE$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.DATE$0);
            }
            target.setCalendarValue(date);
        }
    }

    /**
     * Sets the visit date using an XmlDate object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans XmlDate object, allowing for type-safe
     * XML manipulation with schema validation. Useful when working directly with
     * XML documents or performing schema-aware transformations.</p>
     *
     * @param date XmlDate the visit date to set as an XMLBeans date object
     */
    public void xsetDate(final XmlDate date) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.DATE$0, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.DATE$0);
            }
            target.set((XmlObject)date);
        }
    }

    /**
     * Explicitly sets the visit date element to nil.
     *
     * <p>Marks the date element as nil in the XML representation, which is semantically
     * different from simply not setting a value. This can be used to explicitly indicate
     * that the date information is intentionally absent or not applicable.</p>
     */
    public void setNilDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.DATE$0, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.DATE$0);
            }
            target.setNil();
        }
    }

    /**
     * Retrieves the gestational age (GA) at the time of visit.
     *
     * <p>Returns the pregnancy duration typically expressed in weeks and days format (e.g., "28+3"
     * for 28 weeks and 3 days). Gestational age is fundamental for assessing fetal development,
     * determining appropriate screening tests, and identifying deviations from expected growth patterns.</p>
     *
     * @return String the gestational age, or null if not recorded
     */
    public String getGa() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.GA$2, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the gestational age as a schema-typed Ga object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans Ga type representation,
     * allowing for schema validation and type-safe XML manipulation of the gestational
     * age element.</p>
     *
     * @return Ga the gestational age as a schema-typed object, or null if not set
     */
    public Ga xgetGa() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Ga target = null;
            target = (Ga)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.GA$2, 0);
            return target;
        }
    }

    /**
     * Sets the gestational age (GA) at the time of visit.
     *
     * <p>Records the pregnancy duration, typically in weeks and days format. This value is
     * critical for clinical decision-making, including timing of screening tests, assessment
     * of fetal growth adequacy, and planning for delivery.</p>
     *
     * @param ga String the gestational age to set (e.g., "28+3" for 28 weeks 3 days)
     */
    public void setGa(final String ga) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.GA$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.GA$2);
            }
            target.setStringValue(ga);
        }
    }

    /**
     * Sets the gestational age using a schema-typed Ga object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans Ga type object, enabling schema-validated assignment
     * of the gestational age element. Useful for XML document manipulation with type safety.</p>
     *
     * @param ga Ga the gestational age to set as a schema-typed object
     */
    public void xsetGa(final Ga ga) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Ga target = null;
            target = (Ga)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.GA$2, 0);
            if (target == null) {
                target = (Ga)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.GA$2);
            }
            target.set((XmlObject)ga);
        }
    }

    /**
     * Retrieves the maternal weight measurement.
     *
     * <p>Returns the weight in kilograms recorded at this prenatal visit. Weight tracking
     * throughout pregnancy is essential for monitoring maternal health, assessing appropriate
     * weight gain, and identifying potential complications such as inadequate nutrition or
     * excessive fluid retention.</p>
     *
     * @return float the maternal weight in kilograms, or 0.0 if not recorded
     */
    public float getWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4, 0);
            if (target == null) {
                return 0.0f;
            }
            return target.getFloatValue();
        }
    }

    /**
     * Retrieves the maternal weight as a schema-typed Weight object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans Weight type representation,
     * allowing for schema validation and type-safe XML manipulation of the weight element.</p>
     *
     * @return Weight the maternal weight as a schema-typed object, or null if not set
     */
    public Weight xgetWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Weight target = null;
            target = (Weight)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4, 0);
            return target;
        }
    }

    /**
     * Checks whether the weight element is explicitly set to nil.
     *
     * <p>Distinguishes between a weight that is absent/not measured and a weight that is
     * explicitly marked as nil in the XML representation, which can be semantically significant
     * in clinical data exchange.</p>
     *
     * @return boolean true if the weight is explicitly nil, false otherwise
     */
    public boolean isNilWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Weight target = null;
            target = (Weight)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the maternal weight measurement.
     *
     * <p>Records the weight in kilograms measured at this prenatal visit. This data point
     * contributes to the longitudinal tracking of maternal weight gain throughout pregnancy,
     * supporting clinical assessment and patient counseling.</p>
     *
     * @param weight float the maternal weight in kilograms to set
     */
    public void setWeight(final float weight) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4);
            }
            target.setFloatValue(weight);
        }
    }

    /**
     * Sets the maternal weight using a schema-typed Weight object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans Weight type object, enabling schema-validated assignment
     * of the weight element. Useful for XML document manipulation with type safety.</p>
     *
     * @param weight Weight the maternal weight to set as a schema-typed object
     */
    public void xsetWeight(final Weight weight) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Weight target = null;
            target = (Weight)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4, 0);
            if (target == null) {
                target = (Weight)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4);
            }
            target.set((XmlObject)weight);
        }
    }

    /**
     * Explicitly sets the weight element to nil.
     *
     * <p>Marks the weight element as nil in the XML representation, explicitly indicating
     * that the weight measurement is intentionally absent or not applicable for this visit.</p>
     */
    public void setNilWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Weight target = null;
            target = (Weight)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4, 0);
            if (target == null) {
                target = (Weight)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.WEIGHT$4);
            }
            target.setNil();
        }
    }

    /**
     * Retrieves the blood pressure (BP) measurement.
     *
     * <p>Returns the blood pressure reading typically formatted as "systolic/diastolic" (e.g., "120/80").
     * Blood pressure monitoring is critical during pregnancy for early detection of hypertensive disorders
     * including gestational hypertension and preeclampsia, which can pose serious risks to both mother
     * and fetus if not identified and managed promptly.</p>
     *
     * @return String the blood pressure reading, or null if not recorded
     */
    public String getBp() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.BP$6, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the blood pressure as a schema-typed Bp object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans Bp type representation,
     * allowing for schema validation and type-safe XML manipulation of the blood pressure element.</p>
     *
     * @return Bp the blood pressure as a schema-typed object, or null if not set
     */
    public Bp xgetBp() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bp target = null;
            target = (Bp)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.BP$6, 0);
            return target;
        }
    }

    /**
     * Sets the blood pressure (BP) measurement.
     *
     * <p>Records the blood pressure reading for this prenatal visit, typically in "systolic/diastolic"
     * format. This vital sign is essential for ongoing maternal health surveillance and early detection
     * of pregnancy-related hypertensive complications.</p>
     *
     * @param bp String the blood pressure reading to set (e.g., "120/80")
     */
    public void setBp(final String bp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.BP$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.BP$6);
            }
            target.setStringValue(bp);
        }
    }

    /**
     * Sets the blood pressure using a schema-typed Bp object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans Bp type object, enabling schema-validated assignment
     * of the blood pressure element. Useful for XML document manipulation with type safety.</p>
     *
     * @param bp Bp the blood pressure to set as a schema-typed object
     */
    public void xsetBp(final Bp bp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bp target = null;
            target = (Bp)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.BP$6, 0);
            if (target == null) {
                target = (Bp)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.BP$6);
            }
            target.set((XmlObject)bp);
        }
    }

    /**
     * Retrieves the urine protein (UrinePR) test result.
     *
     * <p>Returns the protein level detected in the urine sample, typically reported as negative,
     * trace, 1+, 2+, 3+, or 4+. Proteinuria is a key diagnostic marker for preeclampsia screening
     * and monitoring, as elevated protein levels can indicate kidney dysfunction associated with
     * this serious pregnancy complication.</p>
     *
     * @return String the urine protein level, or null if not tested
     */
    public String getUrinePR() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.URINEPR$8, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the urine protein as an XmlString object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans XmlString representation,
     * allowing for type-safe XML manipulation of the urine protein element.</p>
     *
     * @return XmlString the urine protein level as an XMLBeans string object, or null if not set
     */
    public XmlString xgetUrinePR() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.URINEPR$8, 0);
            return target;
        }
    }

    /**
     * Sets the urine protein (UrinePR) test result.
     *
     * <p>Records the protein level detected in the urine sample. This measurement is a standard
     * component of prenatal screening for preeclampsia and other kidney-related complications
     * during pregnancy.</p>
     *
     * @param urinePR String the urine protein level to set (e.g., "negative", "trace", "1+", "2+")
     */
    public void setUrinePR(final String urinePR) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.URINEPR$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.URINEPR$8);
            }
            target.setStringValue(urinePR);
        }
    }

    /**
     * Sets the urine protein using an XmlString object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for type-safe
     * XML manipulation of the urine protein element.</p>
     *
     * @param urinePR XmlString the urine protein level to set as an XMLBeans string object
     */
    public void xsetUrinePR(final XmlString urinePR) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.URINEPR$8, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.URINEPR$8);
            }
            target.set((XmlObject)urinePR);
        }
    }

    /**
     * Retrieves the urine glucose (UrineGI) test result.
     *
     * <p>Returns the glucose level detected in the urine sample, typically reported as negative
     * or in gradations (trace, 1+, 2+, etc.). Glycosuria can indicate gestational diabetes or
     * poor glycemic control in patients with pre-existing diabetes, warranting further assessment
     * and management to prevent maternal and fetal complications.</p>
     *
     * @return String the urine glucose level, or null if not tested
     */
    public String getUrineGI() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.URINEGI$10, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the urine glucose as an XmlString object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans XmlString representation,
     * allowing for type-safe XML manipulation of the urine glucose element.</p>
     *
     * @return XmlString the urine glucose level as an XMLBeans string object, or null if not set
     */
    public XmlString xgetUrineGI() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.URINEGI$10, 0);
            return target;
        }
    }

    /**
     * Sets the urine glucose (UrineGI) test result.
     *
     * <p>Records the glucose level detected in the urine sample. This screening test helps
     * identify patients who may require formal glucose tolerance testing for gestational
     * diabetes diagnosis or monitoring of glycemic control in diabetic pregnancies.</p>
     *
     * @param urineGI String the urine glucose level to set (e.g., "negative", "trace", "1+")
     */
    public void setUrineGI(final String urineGI) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.URINEGI$10, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.URINEGI$10);
            }
            target.setStringValue(urineGI);
        }
    }

    /**
     * Sets the urine glucose using an XmlString object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for type-safe
     * XML manipulation of the urine glucose element.</p>
     *
     * @param urineGI XmlString the urine glucose level to set as an XMLBeans string object
     */
    public void xsetUrineGI(final XmlString urineGI) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.URINEGI$10, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.URINEGI$10);
            }
            target.set((XmlObject)urineGI);
        }
    }

    /**
     * Retrieves the symphysis-fundal height (SFH) measurement.
     *
     * <p>Returns the fundal height measurement in centimeters, representing the distance from
     * the pubic symphysis to the top of the uterine fundus. This clinical measurement correlates
     * with gestational age and is used to assess fetal growth adequacy. Deviations from expected
     * values may indicate intrauterine growth restriction, macrosomia, polyhydramnios, or
     * oligohydramnios requiring further investigation.</p>
     *
     * @return String the symphysis-fundal height in centimeters, or null if not measured
     */
    public String getSFH() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.SFH$12, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the symphysis-fundal height as an XmlString object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans XmlString representation,
     * allowing for type-safe XML manipulation of the SFH element.</p>
     *
     * @return XmlString the symphysis-fundal height as an XMLBeans string object, or null if not set
     */
    public XmlString xgetSFH() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.SFH$12, 0);
            return target;
        }
    }

    /**
     * Sets the symphysis-fundal height (SFH) measurement.
     *
     * <p>Records the fundal height measurement in centimeters. This value is plotted on growth
     * charts to monitor fetal growth trends over time and identify potential abnormalities
     * requiring ultrasound evaluation or other interventions.</p>
     *
     * @param sfh String the symphysis-fundal height in centimeters to set
     */
    public void setSFH(final String sfh) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.SFH$12, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.SFH$12);
            }
            target.setStringValue(sfh);
        }
    }

    /**
     * Sets the symphysis-fundal height using an XmlString object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for type-safe
     * XML manipulation of the SFH element.</p>
     *
     * @param sfh XmlString the symphysis-fundal height to set as an XMLBeans string object
     */
    public void xsetSFH(final XmlString sfh) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.SFH$12, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.SFH$12);
            }
            target.set((XmlObject)sfh);
        }
    }

    /**
     * Retrieves the fetal presentation and position assessment.
     *
     * <p>Returns the clinical determination of fetal presentation (cephalic/vertex, breech,
     * transverse/oblique) and position within the uterus. This information becomes increasingly
     * important in the third trimester for delivery planning, as non-cephalic presentations
     * may require external cephalic version attempts or cesarean delivery.</p>
     *
     * @return String the fetal presentation and position, or null if not assessed
     */
    public String getPresentationPosition() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.PRESENTATIONPOSITION$14, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the fetal presentation/position as an XmlString object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans XmlString representation,
     * allowing for type-safe XML manipulation of the presentation/position element.</p>
     *
     * @return XmlString the fetal presentation and position as an XMLBeans string object, or null if not set
     */
    public XmlString xgetPresentationPosition() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.PRESENTATIONPOSITION$14, 0);
            return target;
        }
    }

    /**
     * Sets the fetal presentation and position assessment.
     *
     * <p>Records the clinical determination of how the fetus is oriented within the uterus.
     * This assessment guides obstetric decision-making, particularly as term approaches,
     * regarding the feasibility and safety of vaginal delivery.</p>
     *
     * @param presentationPosition String the fetal presentation and position to set (e.g., "cephalic", "breech", "transverse")
     */
    public void setPresentationPosition(final String presentationPosition) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.PRESENTATIONPOSITION$14, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.PRESENTATIONPOSITION$14);
            }
            target.setStringValue(presentationPosition);
        }
    }

    /**
     * Sets the fetal presentation/position using an XmlString object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for type-safe
     * XML manipulation of the presentation/position element.</p>
     *
     * @param presentationPosition XmlString the fetal presentation and position to set as an XMLBeans string object
     */
    public void xsetPresentationPosition(final XmlString presentationPosition) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.PRESENTATIONPOSITION$14, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.PRESENTATIONPOSITION$14);
            }
            target.set((XmlObject)presentationPosition);
        }
    }

    /**
     * Retrieves the fetal heart rate (FHR) and fetal movements (Fm) assessment.
     *
     * <p>Returns the documentation of fetal heart rate (typically 110-160 beats per minute in
     * normal range) and maternal report or clinical observation of fetal movements. These vital
     * signs of fetal well-being are assessed at each prenatal visit to ensure ongoing fetal
     * health. Abnormal heart rates or decreased fetal movement may warrant additional testing
     * such as non-stress testing or biophysical profile.</p>
     *
     * @return String the fetal heart rate and movement assessment, or null if not documented
     */
    public String getFHRFm() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.FHRFM$16, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the FHR/Fm as an XmlString object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans XmlString representation,
     * allowing for type-safe XML manipulation of the fetal heart rate/movements element.</p>
     *
     * @return XmlString the fetal heart rate and movements as an XMLBeans string object, or null if not set
     */
    public XmlString xgetFHRFm() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.FHRFM$16, 0);
            return target;
        }
    }

    /**
     * Sets the fetal heart rate (FHR) and fetal movements (Fm) assessment.
     *
     * <p>Records the fetal heart rate and movement observations from this prenatal visit.
     * This documentation provides a longitudinal record of fetal well-being throughout
     * the pregnancy and can be critical for identifying concerning trends.</p>
     *
     * @param fhrFm String the fetal heart rate and movement assessment to set (e.g., "FHR 140 bpm, active movements")
     */
    public void setFHRFm(final String fhrFm) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.FHRFM$16, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.FHRFM$16);
            }
            target.setStringValue(fhrFm);
        }
    }

    /**
     * Sets the FHR/Fm using an XmlString object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for type-safe
     * XML manipulation of the fetal heart rate/movements element.</p>
     *
     * @param fhrFm XmlString the fetal heart rate and movements to set as an XMLBeans string object
     */
    public void xsetFHRFm(final XmlString fhrFm) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.FHRFM$16, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.FHRFM$16);
            }
            target.set((XmlObject)fhrFm);
        }
    }

    /**
     * Retrieves the clinical comments and observations from the visit.
     *
     * <p>Returns free-text clinical notes documenting any additional findings, patient concerns,
     * care plan discussions, or observations not captured in the structured data fields. This
     * narrative component allows healthcare providers to record clinical context, patient education
     * provided, and any issues requiring follow-up or special attention.</p>
     *
     * @return String the clinical comments, or null if none recorded
     */
    public String getComments() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.COMMENTS$18, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the comments as an XmlString object for XML-specific operations.
     *
     * <p>This method provides access to the underlying XMLBeans XmlString representation,
     * allowing for type-safe XML manipulation of the comments element.</p>
     *
     * @return XmlString the clinical comments as an XMLBeans string object, or null if not set
     */
    public XmlString xgetComments() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.COMMENTS$18, 0);
            return target;
        }
    }

    /**
     * Sets the clinical comments and observations from the visit.
     *
     * <p>Records free-text clinical notes to supplement the structured visit data. This allows
     * documentation of nuanced clinical information, patient-provider discussions, and contextual
     * details important for continuity of care.</p>
     *
     * @param comments String the clinical comments to set
     */
    public void setComments(final String comments) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.COMMENTS$18, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.COMMENTS$18);
            }
            target.setStringValue(comments);
        }
    }

    /**
     * Sets the comments using an XmlString object for XML-specific operations.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for type-safe
     * XML manipulation of the comments element.</p>
     *
     * @param comments XmlString the clinical comments to set as an XMLBeans string object
     */
    public void xsetComments(final XmlString comments) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(SubsequentVisitItemTypeImpl.COMMENTS$18, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(SubsequentVisitItemTypeImpl.COMMENTS$18);
            }
            target.set((XmlObject)comments);
        }
    }
    
    static {
        DATE$0 = new QName("http://www.oscarmcmaster.org/AR2005", "date");
        GA$2 = new QName("http://www.oscarmcmaster.org/AR2005", "ga");
        WEIGHT$4 = new QName("http://www.oscarmcmaster.org/AR2005", "weight");
        BP$6 = new QName("http://www.oscarmcmaster.org/AR2005", "bp");
        URINEPR$8 = new QName("http://www.oscarmcmaster.org/AR2005", "urinePR");
        URINEGI$10 = new QName("http://www.oscarmcmaster.org/AR2005", "urineGI");
        SFH$12 = new QName("http://www.oscarmcmaster.org/AR2005", "SFH");
        PRESENTATIONPOSITION$14 = new QName("http://www.oscarmcmaster.org/AR2005", "presentation_position");
        FHRFM$16 = new QName("http://www.oscarmcmaster.org/AR2005", "FHR_fm");
        COMMENTS$18 = new QName("http://www.oscarmcmaster.org/AR2005", "comments");
    }

    /**
     * Inner implementation class for the gestational age (GA) XML schema type.
     *
     * <p>This class provides the XMLBeans implementation for the Ga schema type, extending
     * {@link JavaStringHolderEx} to handle string-based gestational age values with schema
     * validation. It supports the storage and retrieval of gestational age data in compliance
     * with the AR2005 schema definitions.</p>
     *
     * @see JavaStringHolderEx
     * @see Ga
     */
    public static class GaImpl extends JavaStringHolderEx implements Ga
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new GaImpl instance with the specified schema type.
         *
         * @param sType SchemaType the schema type definition for this XML element
         */
        public GaImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new GaImpl instance with the specified schema type and mutability flag.
         *
         * <p>This protected constructor allows subclasses to control the mutability of the
         * XML value holder.</p>
         *
         * @param sType SchemaType the schema type definition for this XML element
         * @param b boolean the mutability flag
         */
        protected GaImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the weight XML schema type.
     *
     * <p>This class provides the XMLBeans implementation for the Weight schema type, extending
     * {@link JavaFloatHolderEx} to handle floating-point weight values with schema validation.
     * It supports the storage and retrieval of maternal weight measurements in kilograms,
     * ensuring compliance with the AR2005 schema definitions and constraints.</p>
     *
     * @see JavaFloatHolderEx
     * @see Weight
     */
    public static class WeightImpl extends JavaFloatHolderEx implements Weight
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new WeightImpl instance with the specified schema type.
         *
         * @param sType SchemaType the schema type definition for this XML element
         */
        public WeightImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new WeightImpl instance with the specified schema type and mutability flag.
         *
         * <p>This protected constructor allows subclasses to control the mutability of the
         * XML value holder.</p>
         *
         * @param sType SchemaType the schema type definition for this XML element
         * @param b boolean the mutability flag
         */
        protected WeightImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Inner implementation class for the blood pressure (BP) XML schema type.
     *
     * <p>This class provides the XMLBeans implementation for the Bp schema type, extending
     * {@link JavaStringHolderEx} to handle string-based blood pressure values with schema
     * validation. It supports the storage and retrieval of blood pressure measurements in
     * standard systolic/diastolic format (e.g., "120/80"), ensuring compliance with the
     * AR2005 schema definitions.</p>
     *
     * @see JavaStringHolderEx
     * @see Bp
     */
    public static class BpImpl extends JavaStringHolderEx implements Bp
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new BpImpl instance with the specified schema type.
         *
         * @param sType SchemaType the schema type definition for this XML element
         */
        public BpImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new BpImpl instance with the specified schema type and mutability flag.
         *
         * <p>This protected constructor allows subclasses to control the mutability of the
         * XML value holder.</p>
         *
         * @param sType SchemaType the schema type definition for this XML element
         * @param b boolean the mutability flag
         */
        protected BpImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }
}
