package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.RecommendedImmunoprophylaxisType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XML Bean implementation for recommended immunoprophylaxis tracking in antenatal care.
 *
 * <p>This class implements the RecommendedImmunoprophylaxisType interface and provides
 * XML binding functionality for storing and managing maternal immunoprophylaxis recommendations
 * as part of the British Columbia Antenatal Record (BCAR) AR2005 form. It handles critical
 * pregnancy-related immunoprophylaxis data including Rh immunoglobulin, rubella vaccination,
 * and hepatitis B prophylaxis.</p>
 *
 * <p>The implementation extends Apache XMLBeans XmlComplexContentImpl to provide thread-safe
 * XML serialization and deserialization capabilities. All accessor methods are synchronized
 * to ensure data integrity in concurrent access scenarios.</p>
 *
 * <p><strong>Healthcare Context:</strong> Immunoprophylaxis during pregnancy is critical for
 * preventing maternal-fetal complications such as Rh disease, congenital rubella syndrome,
 * and vertical transmission of hepatitis B virus. This class tracks:</p>
 * <ul>
 *   <li>Rh negative status and Rh immunoglobulin (RhIg) administration dates</li>
 *   <li>Rubella vaccination status and recommendations</li>
 *   <li>Newborn hepatitis B immunoglobulin (HepIG) recommendations</li>
 *   <li>Maternal hepatitis B vaccine administration</li>
 * </ul>
 *
 * @see ca.openosp.openo.ar2005.RecommendedImmunoprophylaxisType
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-23
 */
public class RecommendedImmunoprophylaxisTypeImpl extends XmlComplexContentImpl implements RecommendedImmunoprophylaxisType
{
    private static final long serialVersionUID = 1L;
    private static final QName RHNEGATIVE$0;
    private static final QName RHIGGIVEN$2;
    private static final QName RUBELLA$4;
    private static final QName NEWBORNHEPIG$6;
    private static final QName HEPBVACCINE$8;

    /**
     * Constructs a new RecommendedImmunoprophylaxisTypeImpl instance with the specified schema type.
     *
     * <p>This constructor is called by the XMLBeans framework during XML deserialization
     * to create a properly typed instance bound to the AR2005 schema definition.</p>
     *
     * @param sType SchemaType the XML schema type definition for this element
     */
    public RecommendedImmunoprophylaxisTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the Rh negative status flag.
     *
     * <p>Indicates whether the mother has Rh negative blood type (D-negative).
     * Rh negative mothers carrying Rh positive fetuses require Rh immunoglobulin (RhIg)
     * prophylaxis to prevent maternal sensitization and hemolytic disease of the newborn
     * in subsequent pregnancies.</p>
     *
     * @return boolean true if the mother is Rh negative, false otherwise
     */
    public boolean getRhNegative() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHNEGATIVE$0, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the Rh negative status as an XmlBoolean object.
     *
     * <p>Provides access to the underlying XMLBeans representation of the boolean value,
     * allowing for XML-specific operations and schema validation.</p>
     *
     * @return XmlBoolean the XMLBeans object wrapping the Rh negative status, or null if not set
     */
    public XmlBoolean xgetRhNegative() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHNEGATIVE$0, 0);
            return target;
        }
    }

    /**
     * Sets the Rh negative status flag.
     *
     * <p>Records whether the mother has Rh negative blood type. This information
     * is critical for determining the need for RhIg prophylaxis during pregnancy
     * (typically at 28 weeks gestation) and within 72 hours postpartum.</p>
     *
     * @param rhNegative boolean true if the mother is Rh negative, false otherwise
     */
    public void setRhNegative(final boolean rhNegative) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHNEGATIVE$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.RHNEGATIVE$0);
            }
            target.setBooleanValue(rhNegative);
        }
    }

    /**
     * Sets the Rh negative status using an XmlBoolean object.
     *
     * <p>Allows setting the value using the XMLBeans representation, preserving
     * XML schema metadata and validation constraints.</p>
     *
     * @param rhNegative XmlBoolean the XMLBeans object containing the Rh negative status
     */
    public void xsetRhNegative(final XmlBoolean rhNegative) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHNEGATIVE$0, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.RHNEGATIVE$0);
            }
            target.set((XmlObject)rhNegative);
        }
    }

    /**
     * Gets the date when Rh immunoglobulin (RhIg) was administered.
     *
     * <p>Returns the date of RhIg administration for Rh negative mothers. Standard protocols
     * recommend RhIg prophylaxis at 28 weeks gestation and within 72 hours after delivery
     * of an Rh positive infant. Additional doses may be required after events that could
     * cause fetomaternal hemorrhage (amniocentesis, trauma, bleeding).</p>
     *
     * @return Calendar the date RhIg was given, or null if not administered or not set
     */
    public Calendar getRhIgGiven() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Gets the RhIg administration date as an XmlDate object.
     *
     * <p>Provides access to the underlying XMLBeans date representation, allowing
     * for XML-specific operations and schema validation.</p>
     *
     * @return XmlDate the XMLBeans date object, or null if not set
     */
    public XmlDate xgetRhIgGiven() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2, 0);
            return target;
        }
    }

    /**
     * Checks if the RhIg given date element is explicitly set to nil.
     *
     * <p>In XML schema, a nil value indicates the element exists but is explicitly empty,
     * which is semantically different from the element being absent or having a null value.</p>
     *
     * @return boolean true if the date element is explicitly nil, false otherwise
     */
    public boolean isNilRhIgGiven() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the date when Rh immunoglobulin (RhIg) was administered.
     *
     * <p>Records the administration date for RhIg prophylaxis. This is a critical
     * clinical data point for tracking compliance with prenatal care protocols and
     * preventing Rh isoimmunization.</p>
     *
     * @param rhIgGiven Calendar the date RhIg was administered, or null to clear the value
     */
    public void setRhIgGiven(final Calendar rhIgGiven) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2);
            }
            target.setCalendarValue(rhIgGiven);
        }
    }

    /**
     * Sets the RhIg administration date using an XmlDate object.
     *
     * <p>Allows setting the date using the XMLBeans representation, preserving
     * XML schema metadata and validation constraints.</p>
     *
     * @param rhIgGiven XmlDate the XMLBeans date object containing the administration date
     */
    public void xsetRhIgGiven(final XmlDate rhIgGiven) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2);
            }
            target.set((XmlObject)rhIgGiven);
        }
    }

    /**
     * Sets the RhIg given date element to nil.
     *
     * <p>Explicitly marks the date element as nil in the XML document, indicating
     * the data is intentionally empty or not applicable, distinct from being absent.</p>
     */
    public void setNilRhIgGiven() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.RHIGGIVEN$2);
            }
            target.setNil();
        }
    }

    /**
     * Gets the rubella vaccination recommendation status.
     *
     * <p>Indicates whether rubella vaccination is recommended for the mother.
     * Rubella immunity is routinely screened during pregnancy. Non-immune women
     * cannot receive the live attenuated vaccine during pregnancy but should be
     * vaccinated immediately postpartum to prevent congenital rubella syndrome
     * in future pregnancies.</p>
     *
     * @return boolean true if rubella vaccination is recommended, false otherwise
     */
    public boolean getRubella() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RUBELLA$4, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the rubella vaccination recommendation as an XmlBoolean object.
     *
     * <p>Provides access to the underlying XMLBeans representation of the boolean value,
     * allowing for XML-specific operations and schema validation.</p>
     *
     * @return XmlBoolean the XMLBeans object wrapping the rubella vaccination recommendation, or null if not set
     */
    public XmlBoolean xgetRubella() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RUBELLA$4, 0);
            return target;
        }
    }

    /**
     * Sets the rubella vaccination recommendation status.
     *
     * <p>Records whether rubella vaccination is recommended for postpartum administration.
     * This is typically set to true when prenatal screening shows non-immune status.</p>
     *
     * @param rubella boolean true if rubella vaccination is recommended, false otherwise
     */
    public void setRubella(final boolean rubella) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RUBELLA$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.RUBELLA$4);
            }
            target.setBooleanValue(rubella);
        }
    }

    /**
     * Sets the rubella vaccination recommendation using an XmlBoolean object.
     *
     * <p>Allows setting the value using the XMLBeans representation, preserving
     * XML schema metadata and validation constraints.</p>
     *
     * @param rubella XmlBoolean the XMLBeans object containing the rubella vaccination recommendation
     */
    public void xsetRubella(final XmlBoolean rubella) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.RUBELLA$4, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.RUBELLA$4);
            }
            target.set((XmlObject)rubella);
        }
    }

    /**
     * Gets the newborn hepatitis B immunoglobulin (HepIG) recommendation status.
     *
     * <p>Indicates whether hepatitis B immunoglobulin is recommended for the newborn.
     * HepIG is administered to infants born to hepatitis B surface antigen (HBsAg)
     * positive mothers within 12 hours of birth, combined with hepatitis B vaccine,
     * to prevent vertical transmission of hepatitis B virus.</p>
     *
     * @return boolean true if newborn HepIG is recommended, false otherwise
     */
    public boolean getNewbornHepIG() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.NEWBORNHEPIG$6, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the newborn HepIG recommendation as an XmlBoolean object.
     *
     * <p>Provides access to the underlying XMLBeans representation of the boolean value,
     * allowing for XML-specific operations and schema validation.</p>
     *
     * @return XmlBoolean the XMLBeans object wrapping the newborn HepIG recommendation, or null if not set
     */
    public XmlBoolean xgetNewbornHepIG() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.NEWBORNHEPIG$6, 0);
            return target;
        }
    }

    /**
     * Sets the newborn hepatitis B immunoglobulin (HepIG) recommendation status.
     *
     * <p>Records whether HepIG is recommended for the newborn. This is typically set
     * to true when maternal hepatitis B screening indicates HBsAg positive status,
     * requiring immediate postpartum prophylaxis for the infant.</p>
     *
     * @param newbornHepIG boolean true if newborn HepIG is recommended, false otherwise
     */
    public void setNewbornHepIG(final boolean newbornHepIG) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.NEWBORNHEPIG$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.NEWBORNHEPIG$6);
            }
            target.setBooleanValue(newbornHepIG);
        }
    }

    /**
     * Sets the newborn HepIG recommendation using an XmlBoolean object.
     *
     * <p>Allows setting the value using the XMLBeans representation, preserving
     * XML schema metadata and validation constraints.</p>
     *
     * @param newbornHepIG XmlBoolean the XMLBeans object containing the newborn HepIG recommendation
     */
    public void xsetNewbornHepIG(final XmlBoolean newbornHepIG) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.NEWBORNHEPIG$6, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.NEWBORNHEPIG$6);
            }
            target.set((XmlObject)newbornHepIG);
        }
    }

    /**
     * Gets the maternal hepatitis B vaccine recommendation status.
     *
     * <p>Indicates whether hepatitis B vaccination is recommended for the mother.
     * Hepatitis B vaccine can be safely administered during pregnancy to non-immune
     * women, particularly those at high risk of infection. The vaccine series consists
     * of three doses administered over 6 months.</p>
     *
     * @return boolean true if maternal HepB vaccine is recommended, false otherwise
     */
    public boolean getHepBVaccine() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.HEPBVACCINE$8, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the maternal HepB vaccine recommendation as an XmlBoolean object.
     *
     * <p>Provides access to the underlying XMLBeans representation of the boolean value,
     * allowing for XML-specific operations and schema validation.</p>
     *
     * @return XmlBoolean the XMLBeans object wrapping the maternal HepB vaccine recommendation, or null if not set
     */
    public XmlBoolean xgetHepBVaccine() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.HEPBVACCINE$8, 0);
            return target;
        }
    }

    /**
     * Sets the maternal hepatitis B vaccine recommendation status.
     *
     * <p>Records whether hepatitis B vaccination is recommended for the mother during
     * or after pregnancy. This is based on screening results and risk factor assessment.</p>
     *
     * @param hepBVaccine boolean true if maternal HepB vaccine is recommended, false otherwise
     */
    public void setHepBVaccine(final boolean hepBVaccine) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.HEPBVACCINE$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.HEPBVACCINE$8);
            }
            target.setBooleanValue(hepBVaccine);
        }
    }

    /**
     * Sets the maternal HepB vaccine recommendation using an XmlBoolean object.
     *
     * <p>Allows setting the value using the XMLBeans representation, preserving
     * XML schema metadata and validation constraints.</p>
     *
     * @param hepBVaccine XmlBoolean the XMLBeans object containing the maternal HepB vaccine recommendation
     */
    public void xsetHepBVaccine(final XmlBoolean hepBVaccine) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(RecommendedImmunoprophylaxisTypeImpl.HEPBVACCINE$8, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(RecommendedImmunoprophylaxisTypeImpl.HEPBVACCINE$8);
            }
            target.set((XmlObject)hepBVaccine);
        }
    }
    
    static {
        RHNEGATIVE$0 = new QName("http://www.oscarmcmaster.org/AR2005", "rhNegative");
        RHIGGIVEN$2 = new QName("http://www.oscarmcmaster.org/AR2005", "rhIgGiven");
        RUBELLA$4 = new QName("http://www.oscarmcmaster.org/AR2005", "rubella");
        NEWBORNHEPIG$6 = new QName("http://www.oscarmcmaster.org/AR2005", "newbornHepIG");
        HEPBVACCINE$8 = new QName("http://www.oscarmcmaster.org/AR2005", "hepBVaccine");
    }
}
