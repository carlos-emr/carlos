package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx;
import org.apache.xmlbeans.XmlFloat;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlInt;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.ObstetricalHistoryItemList;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the ObstetricalHistoryItemList interface for managing obstetrical history data
 * in British Columbia Antenatal Record (BCAR) forms.
 *
 * <p>This class provides XMLBeans-based persistence for individual obstetrical history items, which
 * track details of previous pregnancies and deliveries. This information is critical for prenatal
 * care assessment and risk stratification in maternal healthcare.</p>
 *
 * <p>The implementation manages the following obstetrical data elements:</p>
 * <ul>
 *   <li>Year of delivery</li>
 *   <li>Sex of infant (Male/Female)</li>
 *   <li>Gestational age at delivery (in weeks)</li>
 *   <li>Birth weight of infant</li>
 *   <li>Length of labour (in hours)</li>
 *   <li>Place of birth (hospital, home, etc.)</li>
 *   <li>Type of delivery (vaginal, cesarean, assisted, etc.)</li>
 *   <li>Clinical comments and notes</li>
 * </ul>
 *
 * <p>This class is part of the AR2005 (Antenatal Record 2005) XML schema implementation used in
 * British Columbia for standardized maternal health record keeping. All data access is thread-safe
 * through synchronized access to the underlying XMLBeans store.</p>
 *
 * @see ca.openosp.openo.ar2005.ObstetricalHistoryItemList
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-24
 */
public class ObstetricalHistoryItemListImpl extends XmlComplexContentImpl implements ObstetricalHistoryItemList
{
    private static final long serialVersionUID = 1L;
    private static final QName YEAR$0;
    private static final QName SEX$2;
    private static final QName GESTAGE$4;
    private static final QName BIRTHWEIGHT$6;
    private static final QName LENGTHOFLABOUR$8;
    private static final QName PLACEOFBIRTH$10;
    private static final QName TYPEOFDELIVERY$12;
    private static final QName COMMENTS$14;

    /**
     * Constructs a new ObstetricalHistoryItemListImpl instance with the specified schema type.
     *
     * <p>This constructor initializes the XMLBeans object structure for storing obstetrical
     * history data according to the AR2005 schema definition.</p>
     *
     * @param sType SchemaType the XMLBeans schema type definition for this object
     */
    public ObstetricalHistoryItemListImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the year of delivery for this obstetrical history item.
     *
     * <p>Returns the calendar year when the delivery occurred, which is used to track
     * the temporal sequence of previous pregnancies in the patient's obstetrical history.</p>
     *
     * @return int the year of delivery, or 0 if not set
     */
    public int getYear() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.YEAR$0, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Retrieves the year of delivery as an XMLBeans XmlInt object.
     *
     * <p>This method provides access to the underlying XMLBeans representation of the year value,
     * allowing for XML schema validation and manipulation.</p>
     *
     * @return XmlInt the XMLBeans representation of the year, or null if not set
     */
    public XmlInt xgetYear() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.YEAR$0, 0);
            return target;
        }
    }

    /**
     * Sets the year of delivery for this obstetrical history item.
     *
     * <p>Stores the calendar year when the delivery occurred. This value is used to maintain
     * chronological ordering of the patient's pregnancy history.</p>
     *
     * @param year int the year of delivery to set
     */
    public void setYear(final int year) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.YEAR$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.YEAR$0);
            }
            target.setIntValue(year);
        }
    }

    /**
     * Sets the year of delivery using an XMLBeans XmlInt object.
     *
     * <p>This method accepts an XMLBeans XmlInt object, allowing for XML schema-compliant
     * setting of the year value with validation.</p>
     *
     * @param year XmlInt the XMLBeans representation of the year to set
     */
    public void xsetYear(final XmlInt year) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.YEAR$0, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.YEAR$0);
            }
            target.set((XmlObject)year);
        }
    }

    /**
     * Retrieves the sex of the infant from this obstetrical history item.
     *
     * <p>Returns the biological sex of the infant at birth as recorded in the obstetrical history.
     * This information is used for comprehensive pregnancy outcome tracking.</p>
     *
     * @return Sex.Enum the sex of the infant (Male/Female), or null if not set
     */
    public Sex.Enum getSex() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.SEX$2, 0);
            if (target == null) {
                return null;
            }
            return (Sex.Enum)target.getEnumValue();
        }
    }

    /**
     * Retrieves the sex of the infant as an XMLBeans Sex object.
     *
     * <p>This method provides access to the underlying XMLBeans representation of the sex value,
     * allowing for XML schema validation and manipulation.</p>
     *
     * @return Sex the XMLBeans representation of the infant's sex, or null if not set
     */
    public Sex xgetSex() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Sex target = null;
            target = (Sex)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.SEX$2, 0);
            return target;
        }
    }

    /**
     * Sets the sex of the infant for this obstetrical history item.
     *
     * <p>Records the biological sex of the infant at birth. This data is part of the comprehensive
     * pregnancy outcome information tracked in the obstetrical history.</p>
     *
     * @param sex Sex.Enum the sex of the infant to set (Male/Female)
     */
    public void setSex(final Sex.Enum sex) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.SEX$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.SEX$2);
            }
            target.setEnumValue((StringEnumAbstractBase)sex);
        }
    }

    /**
     * Sets the sex of the infant using an XMLBeans Sex object.
     *
     * <p>This method accepts an XMLBeans Sex object, allowing for XML schema-compliant
     * setting of the sex value with validation.</p>
     *
     * @param sex Sex the XMLBeans representation of the infant's sex to set
     */
    public void xsetSex(final Sex sex) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Sex target = null;
            target = (Sex)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.SEX$2, 0);
            if (target == null) {
                target = (Sex)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.SEX$2);
            }
            target.set((XmlObject)sex);
        }
    }

    /**
     * Retrieves the gestational age at delivery for this obstetrical history item.
     *
     * <p>Returns the gestational age (in weeks) at the time of delivery. This is a critical
     * parameter for assessing pregnancy outcomes and identifying preterm or post-term deliveries
     * in the patient's obstetrical history.</p>
     *
     * @return int the gestational age in weeks, or 0 if not set
     */
    public int getGestAge() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.GESTAGE$4, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Retrieves the gestational age at delivery as an XMLBeans XmlInt object.
     *
     * <p>This method provides access to the underlying XMLBeans representation of the gestational
     * age value, allowing for XML schema validation and manipulation.</p>
     *
     * @return XmlInt the XMLBeans representation of the gestational age, or null if not set
     */
    public XmlInt xgetGestAge() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.GESTAGE$4, 0);
            return target;
        }
    }

    /**
     * Sets the gestational age at delivery for this obstetrical history item.
     *
     * <p>Records the gestational age (in weeks) at the time of delivery. This value is essential
     * for tracking pregnancy outcomes and identifying patterns of preterm or post-term deliveries.</p>
     *
     * @param gestAge int the gestational age in weeks to set
     */
    public void setGestAge(final int gestAge) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.GESTAGE$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.GESTAGE$4);
            }
            target.setIntValue(gestAge);
        }
    }

    /**
     * Sets the gestational age at delivery using an XMLBeans XmlInt object.
     *
     * <p>This method accepts an XMLBeans XmlInt object, allowing for XML schema-compliant
     * setting of the gestational age value with validation.</p>
     *
     * @param gestAge XmlInt the XMLBeans representation of the gestational age to set
     */
    public void xsetGestAge(final XmlInt gestAge) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.GESTAGE$4, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.GESTAGE$4);
            }
            target.set((XmlObject)gestAge);
        }
    }

    /**
     * Retrieves the birth weight of the infant from this obstetrical history item.
     *
     * <p>Returns the birth weight of the infant, typically recorded in grams or pounds/ounces.
     * Birth weight is a key indicator of fetal growth and development, and is used to assess
     * neonatal health outcomes and identify low birth weight or macrosomia patterns in the
     * patient's obstetrical history.</p>
     *
     * @return String the birth weight of the infant, or null if not set
     */
    public String getBirthWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.BIRTHWEIGHT$6, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the birth weight of the infant as an XMLBeans XmlString object.
     *
     * <p>This method provides access to the underlying XMLBeans representation of the birth weight
     * value, allowing for XML schema validation and manipulation.</p>
     *
     * @return XmlString the XMLBeans representation of the birth weight, or null if not set
     */
    public XmlString xgetBirthWeight() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.BIRTHWEIGHT$6, 0);
            return target;
        }
    }

    /**
     * Sets the birth weight of the infant for this obstetrical history item.
     *
     * <p>Records the birth weight of the infant. This value is critical for tracking fetal growth
     * patterns and identifying risk factors such as low birth weight or macrosomia in previous
     * pregnancies.</p>
     *
     * @param birthWeight String the birth weight of the infant to set
     */
    public void setBirthWeight(final String birthWeight) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.BIRTHWEIGHT$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.BIRTHWEIGHT$6);
            }
            target.setStringValue(birthWeight);
        }
    }

    /**
     * Sets the birth weight of the infant using an XMLBeans XmlString object.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for XML schema-compliant
     * setting of the birth weight value with validation.</p>
     *
     * @param birthWeight XmlString the XMLBeans representation of the birth weight to set
     */
    public void xsetBirthWeight(final XmlString birthWeight) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.BIRTHWEIGHT$6, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.BIRTHWEIGHT$6);
            }
            target.set((XmlObject)birthWeight);
        }
    }

    /**
     * Retrieves the length of labour for this obstetrical history item.
     *
     * <p>Returns the duration of labour (in hours) for this delivery. Length of labour is an
     * important clinical parameter for assessing delivery patterns and identifying potential
     * risk factors such as prolonged labour in the patient's obstetrical history.</p>
     *
     * @return float the length of labour in hours, or 0.0f if not set
     */
    public float getLengthOfLabour() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8, 0);
            if (target == null) {
                return 0.0f;
            }
            return target.getFloatValue();
        }
    }

    /**
     * Retrieves the length of labour as an XMLBeans XmlFloat object.
     *
     * <p>This method provides access to the underlying XMLBeans representation of the length of
     * labour value, allowing for XML schema validation and manipulation.</p>
     *
     * @return XmlFloat the XMLBeans representation of the length of labour, or null if not set
     */
    public XmlFloat xgetLengthOfLabour() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlFloat target = null;
            target = (XmlFloat)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8, 0);
            return target;
        }
    }

    /**
     * Checks if the length of labour value is explicitly set to nil.
     *
     * <p>This method determines whether the length of labour has been explicitly marked as nil
     * in the XML structure, which is different from simply not being set.</p>
     *
     * @return boolean true if the length of labour is explicitly nil, false otherwise
     */
    public boolean isNilLengthOfLabour() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlFloat target = null;
            target = (XmlFloat)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the length of labour for this obstetrical history item.
     *
     * <p>Records the duration of labour (in hours) for this delivery. This information is used
     * to track labour patterns and identify potential complications such as prolonged or
     * precipitous labour in previous pregnancies.</p>
     *
     * @param lengthOfLabour float the length of labour in hours to set
     */
    public void setLengthOfLabour(final float lengthOfLabour) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8);
            }
            target.setFloatValue(lengthOfLabour);
        }
    }

    /**
     * Sets the length of labour using an XMLBeans XmlFloat object.
     *
     * <p>This method accepts an XMLBeans XmlFloat object, allowing for XML schema-compliant
     * setting of the length of labour value with validation.</p>
     *
     * @param lengthOfLabour XmlFloat the XMLBeans representation of the length of labour to set
     */
    public void xsetLengthOfLabour(final XmlFloat lengthOfLabour) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlFloat target = null;
            target = (XmlFloat)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8, 0);
            if (target == null) {
                target = (XmlFloat)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8);
            }
            target.set((XmlObject)lengthOfLabour);
        }
    }

    /**
     * Explicitly sets the length of labour value to nil.
     *
     * <p>This method marks the length of labour as explicitly nil in the XML structure,
     * indicating that the value is intentionally absent rather than simply not set.</p>
     */
    public void setNilLengthOfLabour() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlFloat target = null;
            target = (XmlFloat)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8, 0);
            if (target == null) {
                target = (XmlFloat)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.LENGTHOFLABOUR$8);
            }
            target.setNil();
        }
    }

    /**
     * Retrieves the place of birth for this obstetrical history item.
     *
     * <p>Returns the location where the delivery occurred (e.g., hospital, home birth center).
     * Place of birth is tracked to assess delivery setting patterns and potential risk factors
     * associated with different birth locations in the patient's obstetrical history.</p>
     *
     * @return String the place of birth, or null if not set
     */
    public String getPlaceOfBirth() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.PLACEOFBIRTH$10, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the place of birth as an XMLBeans XmlString object.
     *
     * <p>This method provides access to the underlying XMLBeans representation of the place of
     * birth value, allowing for XML schema validation and manipulation.</p>
     *
     * @return XmlString the XMLBeans representation of the place of birth, or null if not set
     */
    public XmlString xgetPlaceOfBirth() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.PLACEOFBIRTH$10, 0);
            return target;
        }
    }

    /**
     * Sets the place of birth for this obstetrical history item.
     *
     * <p>Records the location where the delivery occurred. This information is used to track
     * delivery setting preferences and assess outcomes associated with different birth locations.</p>
     *
     * @param placeOfBirth String the place of birth to set
     */
    public void setPlaceOfBirth(final String placeOfBirth) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.PLACEOFBIRTH$10, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.PLACEOFBIRTH$10);
            }
            target.setStringValue(placeOfBirth);
        }
    }

    /**
     * Sets the place of birth using an XMLBeans XmlString object.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for XML schema-compliant
     * setting of the place of birth value with validation.</p>
     *
     * @param placeOfBirth XmlString the XMLBeans representation of the place of birth to set
     */
    public void xsetPlaceOfBirth(final XmlString placeOfBirth) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.PLACEOFBIRTH$10, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.PLACEOFBIRTH$10);
            }
            target.set((XmlObject)placeOfBirth);
        }
    }

    /**
     * Retrieves the type of delivery for this obstetrical history item.
     *
     * <p>Returns the delivery method used (e.g., vaginal delivery, cesarean section, assisted
     * delivery with forceps or vacuum). Type of delivery is a critical parameter for assessing
     * delivery patterns and identifying risk factors for future pregnancies.</p>
     *
     * @return TypeOfDelivery.Enum the type of delivery, or null if not set
     */
    public TypeOfDelivery.Enum getTypeOfDelivery() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.TYPEOFDELIVERY$12, 0);
            if (target == null) {
                return null;
            }
            return (TypeOfDelivery.Enum)target.getEnumValue();
        }
    }

    /**
     * Retrieves the type of delivery as an XMLBeans TypeOfDelivery object.
     *
     * <p>This method provides access to the underlying XMLBeans representation of the type of
     * delivery value, allowing for XML schema validation and manipulation.</p>
     *
     * @return TypeOfDelivery the XMLBeans representation of the type of delivery, or null if not set
     */
    public TypeOfDelivery xgetTypeOfDelivery() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            TypeOfDelivery target = null;
            target = (TypeOfDelivery)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.TYPEOFDELIVERY$12, 0);
            return target;
        }
    }

    /**
     * Sets the type of delivery for this obstetrical history item.
     *
     * <p>Records the delivery method used for this birth. This information is essential for
     * tracking delivery patterns and assessing risk factors for current and future pregnancies.</p>
     *
     * @param typeOfDelivery TypeOfDelivery.Enum the type of delivery to set
     */
    public void setTypeOfDelivery(final TypeOfDelivery.Enum typeOfDelivery) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.TYPEOFDELIVERY$12, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.TYPEOFDELIVERY$12);
            }
            target.setEnumValue((StringEnumAbstractBase)typeOfDelivery);
        }
    }

    /**
     * Sets the type of delivery using an XMLBeans TypeOfDelivery object.
     *
     * <p>This method accepts an XMLBeans TypeOfDelivery object, allowing for XML schema-compliant
     * setting of the type of delivery value with validation.</p>
     *
     * @param typeOfDelivery TypeOfDelivery the XMLBeans representation of the type of delivery to set
     */
    public void xsetTypeOfDelivery(final TypeOfDelivery typeOfDelivery) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            TypeOfDelivery target = null;
            target = (TypeOfDelivery)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.TYPEOFDELIVERY$12, 0);
            if (target == null) {
                target = (TypeOfDelivery)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.TYPEOFDELIVERY$12);
            }
            target.set((XmlObject)typeOfDelivery);
        }
    }

    /**
     * Retrieves the clinical comments for this obstetrical history item.
     *
     * <p>Returns any additional clinical notes, observations, or complications associated with
     * this delivery. Comments provide important contextual information that may not be captured
     * in structured data fields and can highlight significant events or outcomes.</p>
     *
     * @return String the clinical comments, or null if not set
     */
    public String getComments() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.COMMENTS$14, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the clinical comments as an XMLBeans XmlString object.
     *
     * <p>This method provides access to the underlying XMLBeans representation of the comments
     * value, allowing for XML schema validation and manipulation.</p>
     *
     * @return XmlString the XMLBeans representation of the comments, or null if not set
     */
    public XmlString xgetComments() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.COMMENTS$14, 0);
            return target;
        }
    }

    /**
     * Sets the clinical comments for this obstetrical history item.
     *
     * <p>Records additional clinical notes, observations, or complications associated with this
     * delivery. This field captures important contextual information and significant clinical events.</p>
     *
     * @param comments String the clinical comments to set
     */
    public void setComments(final String comments) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.COMMENTS$14, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.COMMENTS$14);
            }
            target.setStringValue(comments);
        }
    }

    /**
     * Sets the clinical comments using an XMLBeans XmlString object.
     *
     * <p>This method accepts an XMLBeans XmlString object, allowing for XML schema-compliant
     * setting of the comments value with validation.</p>
     *
     * @param comments XmlString the XMLBeans representation of the comments to set
     */
    public void xsetComments(final XmlString comments) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(ObstetricalHistoryItemListImpl.COMMENTS$14, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(ObstetricalHistoryItemListImpl.COMMENTS$14);
            }
            target.set((XmlObject)comments);
        }
    }
    
    static {
        YEAR$0 = new QName("http://www.oscarmcmaster.org/AR2005", "year");
        SEX$2 = new QName("http://www.oscarmcmaster.org/AR2005", "sex");
        GESTAGE$4 = new QName("http://www.oscarmcmaster.org/AR2005", "gestAge");
        BIRTHWEIGHT$6 = new QName("http://www.oscarmcmaster.org/AR2005", "birthWeight");
        LENGTHOFLABOUR$8 = new QName("http://www.oscarmcmaster.org/AR2005", "lengthOfLabour");
        PLACEOFBIRTH$10 = new QName("http://www.oscarmcmaster.org/AR2005", "placeOfBirth");
        TYPEOFDELIVERY$12 = new QName("http://www.oscarmcmaster.org/AR2005", "typeOfDelivery");
        COMMENTS$14 = new QName("http://www.oscarmcmaster.org/AR2005", "comments");
    }

    /**
     * Implementation of the Sex enumeration for obstetrical history items.
     *
     * <p>This inner class provides XMLBeans-based persistence for the sex enumeration,
     * which represents the biological sex of the infant (Male/Female) in the obstetrical
     * history record.</p>
     *
     * @see ca.openosp.openo.ar2005.ObstetricalHistoryItemList.Sex
     * @since 2026-01-24
     */
    public static class SexImpl extends JavaStringEnumerationHolderEx implements Sex
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new SexImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this enumeration
         */
        public SexImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new SexImpl instance with the specified schema type and validation flag.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this enumeration
         * @param b boolean flag for XMLBeans internal validation control
         */
        protected SexImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * Implementation of the TypeOfDelivery enumeration for obstetrical history items.
     *
     * <p>This inner class provides XMLBeans-based persistence for the type of delivery enumeration,
     * which represents the delivery method (e.g., vaginal, cesarean, assisted delivery) in the
     * obstetrical history record.</p>
     *
     * @see ca.openosp.openo.ar2005.ObstetricalHistoryItemList.TypeOfDelivery
     * @since 2026-01-24
     */
    public static class TypeOfDeliveryImpl extends JavaStringEnumerationHolderEx implements TypeOfDelivery
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new TypeOfDeliveryImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this enumeration
         */
        public TypeOfDeliveryImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new TypeOfDeliveryImpl instance with the specified schema type and validation flag.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this enumeration
         * @param b boolean flag for XMLBeans internal validation control
         */
        protected TypeOfDeliveryImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }
}
