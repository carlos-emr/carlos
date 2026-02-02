package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.DatingMethods;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the DatingMethods interface for pregnancy dating documentation in antenatal records (AR2005).
 *
 * This class provides XMLBeans-based persistence for tracking pregnancy dating methods used in obstetric care.
 * Dating methods are critical for establishing accurate gestational age, which determines appropriate prenatal
 * care schedules, screening timelines, and delivery planning. The AR2005 (Antenatal Record 2005) format is used
 * for standardized pregnancy documentation in Canadian healthcare settings.
 *
 * The implementation manages four dating method indicators:
 * <ul>
 *   <li>Dates - Dating by last menstrual period (LMP) or menstrual dates</li>
 *   <li>T1US - First trimester ultrasound dating (most accurate, typically 8-13 weeks)</li>
 *   <li>T2US - Second trimester ultrasound dating (used when first trimester dating unavailable)</li>
 *   <li>ART - Assisted reproductive technology dating (IVF/embryo transfer provides precise conception date)</li>
 * </ul>
 *
 * Thread-safe implementation using XMLBeans monitor synchronization for concurrent access to XML document store.
 * Each property follows the XMLBeans pattern of providing both primitive and XmlObject accessors (get/xget, set/xset).
 *
 * @see DatingMethods
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-23
 */
public class DatingMethodsImpl extends XmlComplexContentImpl implements DatingMethods
{
    private static final long serialVersionUID = 1L;
    private static final QName DATES$0;
    private static final QName T1US$2;
    private static final QName T2US$4;
    private static final QName ART$6;

    /**
     * Constructs a new DatingMethodsImpl instance with the specified schema type.
     *
     * This constructor is typically called by the XMLBeans framework during XML parsing
     * or when creating new instances through the DatingMethods.Factory methods.
     *
     * @param sType SchemaType the XMLBeans schema type definition for DatingMethods
     */
    public DatingMethodsImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves whether dating by last menstrual period (LMP) or menstrual dates is used.
     *
     * LMP dating is the traditional method of estimating gestational age based on the first day
     * of the patient's last normal menstrual period. While commonly used as an initial dating method,
     * it may be less accurate than ultrasound dating, particularly when menstrual cycles are irregular.
     *
     * @return boolean true if LMP/menstrual dates are used for pregnancy dating, false otherwise
     */
    public boolean getDates() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DatingMethodsImpl.DATES$0, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XMLBeans representation of the dates property.
     *
     * This method provides access to the underlying XmlBoolean object, allowing for advanced
     * XML manipulation such as validation, schema access, and low-level document operations.
     *
     * @return XmlBoolean the XMLBeans object representing the dates element, or null if not set
     */
    public XmlBoolean xgetDates() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DatingMethodsImpl.DATES$0, 0);
            return target;
        }
    }

    /**
     * Sets whether dating by last menstrual period (LMP) or menstrual dates is used.
     *
     * This method updates the pregnancy dating method indicator for LMP-based dating.
     * Multiple dating methods may be marked as true if different methods were used to
     * confirm or establish gestational age during the pregnancy.
     *
     * @param dates boolean true to indicate LMP/menstrual dates are used, false otherwise
     */
    public void setDates(final boolean dates) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DatingMethodsImpl.DATES$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DatingMethodsImpl.DATES$0);
            }
            target.setBooleanValue(dates);
        }
    }

    /**
     * Sets the dates property using an XMLBeans XmlBoolean object.
     *
     * This method allows setting the dates indicator using an XmlBoolean object, which may
     * carry additional XML metadata such as validation state or schema information.
     *
     * @param dates XmlBoolean the XMLBeans object to set as the dates element value
     */
    public void xsetDates(final XmlBoolean dates) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DatingMethodsImpl.DATES$0, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DatingMethodsImpl.DATES$0);
            }
            target.set((XmlObject)dates);
        }
    }

    /**
     * Retrieves whether first trimester ultrasound (T1US) dating is used.
     *
     * First trimester ultrasound dating (typically performed between 8-13 weeks gestation) is considered
     * the most accurate method for establishing gestational age, with accuracy within 5-7 days.
     * Crown-rump length (CRL) measurements obtained during this period provide the gold standard for
     * pregnancy dating. When available, T1US dating typically supersedes LMP-based dating for clinical
     * decision-making.
     *
     * @return boolean true if first trimester ultrasound dating is used, false otherwise
     */
    public boolean getT1US() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DatingMethodsImpl.T1US$2, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XMLBeans representation of the T1US (first trimester ultrasound) property.
     *
     * This method provides access to the underlying XmlBoolean object for advanced XML operations.
     *
     * @return XmlBoolean the XMLBeans object representing the t1US element, or null if not set
     */
    public XmlBoolean xgetT1US() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DatingMethodsImpl.T1US$2, 0);
            return target;
        }
    }

    /**
     * Sets whether first trimester ultrasound (T1US) dating is used.
     *
     * This method updates the indicator for first trimester ultrasound dating. Healthcare providers
     * should document T1US dating when crown-rump length or early gestational sac measurements have
     * been used to establish or confirm gestational age.
     *
     * @param t1US boolean true to indicate first trimester ultrasound dating is used, false otherwise
     */
    public void setT1US(final boolean t1US) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DatingMethodsImpl.T1US$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DatingMethodsImpl.T1US$2);
            }
            target.setBooleanValue(t1US);
        }
    }

    /**
     * Sets the T1US property using an XMLBeans XmlBoolean object.
     *
     * This method allows setting the first trimester ultrasound indicator using an XmlBoolean
     * object with associated XML metadata.
     *
     * @param t1US XmlBoolean the XMLBeans object to set as the t1US element value
     */
    public void xsetT1US(final XmlBoolean t1US) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DatingMethodsImpl.T1US$2, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DatingMethodsImpl.T1US$2);
            }
            target.set((XmlObject)t1US);
        }
    }

    /**
     * Retrieves whether second trimester ultrasound (T2US) dating is used.
     *
     * Second trimester ultrasound dating (typically performed between 14-28 weeks gestation) is used
     * when first trimester dating is unavailable or when initial prenatal care begins later in pregnancy.
     * While less accurate than first trimester dating (accuracy within 7-10 days), T2US dating using
     * biometric parameters (biparietal diameter, femur length, abdominal circumference) provides more
     * reliable gestational age estimation than LMP alone, particularly when menstrual history is uncertain.
     *
     * @return boolean true if second trimester ultrasound dating is used, false otherwise
     */
    public boolean getT2US() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DatingMethodsImpl.T2US$4, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XMLBeans representation of the T2US (second trimester ultrasound) property.
     *
     * This method provides access to the underlying XmlBoolean object for advanced XML operations.
     *
     * @return XmlBoolean the XMLBeans object representing the t2US element, or null if not set
     */
    public XmlBoolean xgetT2US() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DatingMethodsImpl.T2US$4, 0);
            return target;
        }
    }

    /**
     * Sets whether second trimester ultrasound (T2US) dating is used.
     *
     * This method updates the indicator for second trimester ultrasound dating. Healthcare providers
     * should document T2US dating when biometric measurements from mid-pregnancy ultrasound have been
     * used to establish or refine gestational age estimates.
     *
     * @param t2US boolean true to indicate second trimester ultrasound dating is used, false otherwise
     */
    public void setT2US(final boolean t2US) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DatingMethodsImpl.T2US$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DatingMethodsImpl.T2US$4);
            }
            target.setBooleanValue(t2US);
        }
    }

    /**
     * Sets the T2US property using an XMLBeans XmlBoolean object.
     *
     * This method allows setting the second trimester ultrasound indicator using an XmlBoolean
     * object with associated XML metadata.
     *
     * @param t2US XmlBoolean the XMLBeans object to set as the t2US element value
     */
    public void xsetT2US(final XmlBoolean t2US) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DatingMethodsImpl.T2US$4, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DatingMethodsImpl.T2US$4);
            }
            target.set((XmlObject)t2US);
        }
    }

    /**
     * Retrieves whether assisted reproductive technology (ART) dating is used.
     *
     * ART dating is used for pregnancies conceived through assisted reproductive technologies such as
     * in vitro fertilization (IVF), intracytoplasmic sperm injection (ICSI), or embryo transfer.
     * This method provides the most precise gestational age calculation as the exact date of conception
     * or embryo transfer is known. ART dating accuracy is within 1 day and takes precedence over all
     * other dating methods when available. The gestational age is calculated from the embryo transfer
     * date plus the embryo age at transfer.
     *
     * @return boolean true if ART dating is used, false otherwise
     */
    public boolean getArt() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DatingMethodsImpl.ART$6, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Retrieves the XMLBeans representation of the ART (assisted reproductive technology) property.
     *
     * This method provides access to the underlying XmlBoolean object for advanced XML operations.
     *
     * @return XmlBoolean the XMLBeans object representing the art element, or null if not set
     */
    public XmlBoolean xgetArt() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DatingMethodsImpl.ART$6, 0);
            return target;
        }
    }

    /**
     * Sets whether assisted reproductive technology (ART) dating is used.
     *
     * This method updates the indicator for ART dating. Healthcare providers should document ART
     * dating for all pregnancies conceived through assisted reproductive technologies where the
     * precise conception or embryo transfer date is known.
     *
     * @param art boolean true to indicate ART dating is used, false otherwise
     */
    public void setArt(final boolean art) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(DatingMethodsImpl.ART$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(DatingMethodsImpl.ART$6);
            }
            target.setBooleanValue(art);
        }
    }

    /**
     * Sets the ART property using an XMLBeans XmlBoolean object.
     *
     * This method allows setting the ART indicator using an XmlBoolean object with associated
     * XML metadata.
     *
     * @param art XmlBoolean the XMLBeans object to set as the art element value
     */
    public void xsetArt(final XmlBoolean art) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(DatingMethodsImpl.ART$6, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(DatingMethodsImpl.ART$6);
            }
            target.set((XmlObject)art);
        }
    }
    
    static {
        DATES$0 = new QName("http://www.oscarmcmaster.org/AR2005", "dates");
        T1US$2 = new QName("http://www.oscarmcmaster.org/AR2005", "t1US");
        T2US$4 = new QName("http://www.oscarmcmaster.org/AR2005", "t2US");
        ART$6 = new QName("http://www.oscarmcmaster.org/AR2005", "art");
    }
}
