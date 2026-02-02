package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.impl.values.JavaStringHolderEx;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlDate;
import org.apache.xmlbeans.SimpleValue;
import java.util.Calendar;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.UltrasoundType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for ultrasound examination data in prenatal care records.
 *
 * <p>This class provides the concrete implementation of the {@link UltrasoundType} interface,
 * managing ultrasound examination data as part of the AR2005 (Antenatal Record 2005) healthcare
 * form used in British Columbia prenatal care. It handles the storage and retrieval of ultrasound
 * examination dates, gestational age (GA) assessments, and clinical results through an XML-based
 * data structure.</p>
 *
 * <p>The implementation uses Apache XMLBeans for XML binding and provides both standard Java
 * accessor methods and XMLBeans-specific methods (prefixed with 'x') for fine-grained control
 * over the XML representation. All operations are thread-safe through synchronization on the
 * internal XML store monitor.</p>
 *
 * <p><b>Healthcare Context:</b> Ultrasound examinations are critical components of prenatal care,
 * used to assess fetal development, estimate gestational age, and identify potential complications.
 * This data is part of the standardized British Columbia Antenatal Record (BCAR) form system.</p>
 *
 * @see UltrasoundType
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-23
 */
public class UltrasoundTypeImpl extends XmlComplexContentImpl implements UltrasoundType
{
    private static final long serialVersionUID = 1L;
    private static final QName DATE$0;
    private static final QName GA$2;
    private static final QName RESULTS$4;
    
    /**
     * Constructs a new UltrasoundTypeImpl instance with the specified schema type.
     *
     * <p>This constructor is typically called by the XMLBeans framework during XML
     * deserialization or when creating new instances through the Factory class.</p>
     *
     * @param sType SchemaType the XMLBeans schema type definition for ultrasound data
     */
    public UltrasoundTypeImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves the date when the ultrasound examination was performed.
     *
     * <p>This method returns the ultrasound examination date as a Calendar object,
     * which includes the full date and time information. The date is critical for
     * tracking the timeline of prenatal care and correlating ultrasound findings
     * with gestational age.</p>
     *
     * @return Calendar the ultrasound examination date, or null if not set
     */
    public Calendar getDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(UltrasoundTypeImpl.DATE$0, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }
    
    /**
     * Retrieves the ultrasound examination date as an XmlDate object.
     *
     * <p>This XMLBeans-specific method provides direct access to the underlying XML
     * date representation, allowing for low-level XML manipulation and validation.
     * Use this method when you need to access XML-specific features or metadata
     * beyond the standard Calendar representation.</p>
     *
     * @return XmlDate the XML representation of the ultrasound examination date, or null if not set
     */
    public XmlDate xgetDate() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(UltrasoundTypeImpl.DATE$0, 0);
            return target;
        }
    }
    
    /**
     * Sets the date when the ultrasound examination was performed.
     *
     * <p>This method stores the ultrasound examination date. If no date element exists
     * in the XML structure, one is created automatically. The date is essential for
     * maintaining accurate prenatal care timelines and is typically set when recording
     * or updating ultrasound examination results.</p>
     *
     * @param date Calendar the ultrasound examination date to set
     */
    public void setDate(final Calendar date) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(UltrasoundTypeImpl.DATE$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(UltrasoundTypeImpl.DATE$0);
            }
            target.setCalendarValue(date);
        }
    }
    
    /**
     * Sets the ultrasound examination date using an XmlDate object.
     *
     * <p>This XMLBeans-specific method allows setting the date using the XML
     * representation directly, preserving all XML-specific metadata and formatting.
     * If no date element exists in the XML structure, one is created automatically.</p>
     *
     * @param date XmlDate the XML representation of the ultrasound examination date to set
     */
    public void xsetDate(final XmlDate date) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(UltrasoundTypeImpl.DATE$0, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(UltrasoundTypeImpl.DATE$0);
            }
            target.set((XmlObject)date);
        }
    }
    
    /**
     * Retrieves the gestational age (GA) determined by the ultrasound examination.
     *
     * <p>Gestational age is a critical prenatal care metric representing the estimated
     * age of the fetus based on ultrasound measurements. This value is typically expressed
     * in weeks and days format (e.g., "28 weeks 3 days") and is used to track fetal
     * development, estimate the due date, and assess whether growth is appropriate for
     * the stage of pregnancy.</p>
     *
     * @return String the gestational age assessment, or null if not set
     */
    public String getGa() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(UltrasoundTypeImpl.GA$2, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }
    
    /**
     * Retrieves the gestational age as a typed Ga object.
     *
     * <p>This XMLBeans-specific method provides access to the gestational age data
     * with its full XML type information, allowing for XML schema validation and
     * type-specific operations beyond simple string access.</p>
     *
     * @return Ga the typed XML representation of the gestational age, or null if not set
     */
    public Ga xgetGa() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Ga target = null;
            target = (Ga)this.get_store().find_element_user(UltrasoundTypeImpl.GA$2, 0);
            return target;
        }
    }
    
    /**
     * Sets the gestational age determined by the ultrasound examination.
     *
     * <p>This method stores the gestational age assessment based on ultrasound measurements.
     * If no GA element exists in the XML structure, one is created automatically. The
     * gestational age is typically calculated from fetal biometric measurements such as
     * crown-rump length, biparietal diameter, femur length, and abdominal circumference.</p>
     *
     * @param ga String the gestational age to set (typically in "weeks days" format)
     */
    public void setGa(final String ga) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(UltrasoundTypeImpl.GA$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(UltrasoundTypeImpl.GA$2);
            }
            target.setStringValue(ga);
        }
    }
    
    /**
     * Sets the gestational age using a typed Ga object.
     *
     * <p>This XMLBeans-specific method allows setting the gestational age using the
     * full XML type representation, preserving XML schema validation and type information.
     * If no GA element exists in the XML structure, one is created automatically.</p>
     *
     * @param ga Ga the typed XML representation of the gestational age to set
     */
    public void xsetGa(final Ga ga) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Ga target = null;
            target = (Ga)this.get_store().find_element_user(UltrasoundTypeImpl.GA$2, 0);
            if (target == null) {
                target = (Ga)this.get_store().add_element_user(UltrasoundTypeImpl.GA$2);
            }
            target.set((XmlObject)ga);
        }
    }
    
    /**
     * Retrieves the clinical results and findings from the ultrasound examination.
     *
     * <p>This method returns the textual description of the ultrasound examination results,
     * which may include fetal measurements, anatomical observations, placental location,
     * amniotic fluid assessment, and any abnormal findings identified during the examination.
     * These results are used for clinical decision-making and monitoring prenatal health.</p>
     *
     * @return String the ultrasound examination results and clinical findings, or null if not set
     */
    public String getResults() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(UltrasoundTypeImpl.RESULTS$4, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }
    
    /**
     * Retrieves the ultrasound results as an XmlString object.
     *
     * <p>This XMLBeans-specific method provides access to the results with full XML
     * type information, allowing for XML schema validation and advanced XML operations
     * beyond simple string access.</p>
     *
     * @return XmlString the XML representation of the ultrasound examination results, or null if not set
     */
    public XmlString xgetResults() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(UltrasoundTypeImpl.RESULTS$4, 0);
            return target;
        }
    }
    
    /**
     * Sets the clinical results and findings from the ultrasound examination.
     *
     * <p>This method stores the textual description of the ultrasound examination findings.
     * If no results element exists in the XML structure, one is created automatically.
     * This field typically contains detailed clinical observations recorded by the
     * ultrasonographer or interpreting physician.</p>
     *
     * @param results String the ultrasound examination results and clinical findings to set
     */
    public void setResults(final String results) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(UltrasoundTypeImpl.RESULTS$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(UltrasoundTypeImpl.RESULTS$4);
            }
            target.setStringValue(results);
        }
    }
    
    /**
     * Sets the ultrasound results using an XmlString object.
     *
     * <p>This XMLBeans-specific method allows setting the results using the XML
     * representation directly, preserving all XML-specific metadata and type information.
     * If no results element exists in the XML structure, one is created automatically.</p>
     *
     * @param results XmlString the XML representation of the ultrasound examination results to set
     */
    public void xsetResults(final XmlString results) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(UltrasoundTypeImpl.RESULTS$4, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(UltrasoundTypeImpl.RESULTS$4);
            }
            target.set((XmlObject)results);
        }
    }
    
    static {
        DATE$0 = new QName("http://www.oscarmcmaster.org/AR2005", "date");
        GA$2 = new QName("http://www.oscarmcmaster.org/AR2005", "ga");
        RESULTS$4 = new QName("http://www.oscarmcmaster.org/AR2005", "results");
    }
    
    /**
     * Inner implementation class for the Ga (Gestational Age) XML type.
     *
     * <p>This nested class provides the concrete XMLBeans implementation for the
     * gestational age string type, extending JavaStringHolderEx to handle XML
     * string values with schema validation. It is used internally by the XMLBeans
     * framework for type-safe gestational age data management.</p>
     *
     * @see Ga
     * @since 2026-01-23
     */
    public static class GaImpl extends JavaStringHolderEx implements Ga
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new GaImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type definition for gestational age
         */
        public GaImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Constructs a new GaImpl instance with the specified schema type and validation flag.
         *
         * @param sType SchemaType the XMLBeans schema type definition for gestational age
         * @param b boolean flag controlling validation behavior
         */
        protected GaImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }
}
