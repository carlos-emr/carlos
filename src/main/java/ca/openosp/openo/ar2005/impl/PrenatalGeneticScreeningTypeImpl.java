package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlBoolean;
import ca.openosp.openo.ar2005.CustomLab;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.PrenatalGeneticScreeningType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for prenatal genetic screening data management in the British Columbia Antenatal Record (BCAR) system.
 *
 * <p>This class provides XML-based persistence for prenatal genetic screening test results and patient decisions
 * within the AR2005 (Antenatal Record 2005) form. It manages multiple types of genetic screening tests commonly
 * performed during pregnancy to assess risk for chromosomal abnormalities and neural tube defects.</p>
 *
 * <p><strong>Healthcare Context:</strong></p>
 * <ul>
 *   <li><strong>MSS/IPS/FTS</strong>: Maternal Serum Screening / Integrated Prenatal Screening / First Trimester Screening
 *       - Combined tests that assess risk for Down syndrome and other chromosomal abnormalities</li>
 *   <li><strong>EDB/CVS</strong>: Early Detection of Birth Defects / Chorionic Villus Sampling
 *       - Invasive diagnostic procedures for definitive chromosomal analysis</li>
 *   <li><strong>MSAFP</strong>: Maternal Serum Alpha-Fetoprotein
 *       - Screening test for neural tube defects and other abnormalities</li>
 *   <li><strong>Custom Lab</strong>: Additional laboratory investigations specific to patient needs</li>
 *   <li><strong>Declined</strong>: Patient choice to decline genetic screening</li>
 * </ul>
 *
 * <p>This implementation extends Apache XMLBeans {@link XmlComplexContentImpl} to provide thread-safe
 * XML serialization and deserialization capabilities for integration with BC's provincial health systems.</p>
 *
 * <p><strong>Thread Safety:</strong> All public methods are synchronized using the XMLBeans monitor pattern
 * to ensure safe concurrent access in multi-threaded healthcare applications.</p>
 *
 * @since 2026-01-24
 * @see PrenatalGeneticScreeningType
 * @see CustomLab
 * @see ca.openosp.openo.ar2005.AR1
 */
public class PrenatalGeneticScreeningTypeImpl extends XmlComplexContentImpl implements PrenatalGeneticScreeningType
{
    private static final long serialVersionUID = 1L;
    private static final QName MSSIPSFTS$0;
    private static final QName EDBCVS$2;
    private static final QName MSAFP$4;
    private static final QName CUSTOMLAB1$6;
    private static final QName DECLINED$8;

    /**
     * Constructs a new PrenatalGeneticScreeningTypeImpl instance with the specified schema type.
     *
     * <p>This constructor is typically invoked by the Apache XMLBeans framework during XML parsing
     * and object instantiation. It initializes the underlying XML store with the appropriate schema
     * definition for prenatal genetic screening data.</p>
     *
     * @param sType SchemaType the XMLBeans schema type definition for this complex type
     */
    public PrenatalGeneticScreeningTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the MSS/IPS/FTS screening test result value.
     *
     * <p>MSS/IPS/FTS represents combined prenatal screening tests:
     * <ul>
     *   <li><strong>MSS</strong>: Maternal Serum Screening</li>
     *   <li><strong>IPS</strong>: Integrated Prenatal Screening</li>
     *   <li><strong>FTS</strong>: First Trimester Screening</li>
     * </ul>
     * These tests assess the risk of chromosomal abnormalities such as Down syndrome (Trisomy 21),
     * Edwards syndrome (Trisomy 18), and neural tube defects.</p>
     *
     * @return String the test result value, or null if not set
     */
    public String getMSSIPSFTS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.MSSIPSFTS$0, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the MSS/IPS/FTS screening test result as an XMLBeans XmlString object.
     *
     * <p>This method provides access to the underlying XMLBeans representation, allowing for
     * advanced XML manipulation and schema validation operations.</p>
     *
     * @return XmlString the XMLBeans representation of the test result, or null if not set
     */
    public XmlString xgetMSSIPSFTS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.MSSIPSFTS$0, 0);
            return target;
        }
    }

    /**
     * Sets the MSS/IPS/FTS screening test result value.
     *
     * <p>Stores the result or status of combined prenatal screening tests. The value typically
     * represents test results, risk assessment scores, or completion status as defined by
     * provincial healthcare protocols.</p>
     *
     * @param mssipsfts String the test result value to set
     */
    public void setMSSIPSFTS(final String mssipsfts) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.MSSIPSFTS$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.MSSIPSFTS$0);
            }
            target.setStringValue(mssipsfts);
        }
    }

    /**
     * Sets the MSS/IPS/FTS screening test result using an XMLBeans XmlString object.
     *
     * <p>This method accepts the XMLBeans representation directly, useful when working with
     * pre-validated XML content or performing complex schema operations.</p>
     *
     * @param mssipsfts XmlString the XMLBeans representation of the test result
     */
    public void xsetMSSIPSFTS(final XmlString mssipsfts) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.MSSIPSFTS$0, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.MSSIPSFTS$0);
            }
            target.set((XmlObject)mssipsfts);
        }
    }

    /**
     * Gets the EDB/CVS diagnostic test result value.
     *
     * <p>EDB/CVS represents invasive prenatal diagnostic procedures:
     * <ul>
     *   <li><strong>EDB</strong>: Early Detection of Birth Defects</li>
     *   <li><strong>CVS</strong>: Chorionic Villus Sampling</li>
     * </ul>
     * These are invasive diagnostic tests performed to obtain fetal cells for chromosomal analysis,
     * providing definitive diagnosis rather than risk assessment. CVS is typically performed between
     * 10-13 weeks of gestation.</p>
     *
     * @return String the diagnostic test result value, or null if not set
     */
    public String getEDBCVS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.EDBCVS$2, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the EDB/CVS diagnostic test result as an XMLBeans XmlString object.
     *
     * <p>This method provides access to the underlying XMLBeans representation for advanced
     * XML operations and schema validation.</p>
     *
     * @return XmlString the XMLBeans representation of the diagnostic test result, or null if not set
     */
    public XmlString xgetEDBCVS() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.EDBCVS$2, 0);
            return target;
        }
    }

    /**
     * Sets the EDB/CVS diagnostic test result value.
     *
     * <p>Stores the result or status of invasive prenatal diagnostic procedures. The value typically
     * represents test results, karyotype findings, or procedure completion status.</p>
     *
     * @param edbcvs String the diagnostic test result value to set
     */
    public void setEDBCVS(final String edbcvs) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.EDBCVS$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.EDBCVS$2);
            }
            target.setStringValue(edbcvs);
        }
    }

    /**
     * Sets the EDB/CVS diagnostic test result using an XMLBeans XmlString object.
     *
     * <p>This method accepts the XMLBeans representation directly for use with pre-validated
     * XML content or complex schema operations.</p>
     *
     * @param edbcvs XmlString the XMLBeans representation of the diagnostic test result
     */
    public void xsetEDBCVS(final XmlString edbcvs) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.EDBCVS$2, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.EDBCVS$2);
            }
            target.set((XmlObject)edbcvs);
        }
    }

    /**
     * Gets the MSAFP screening test result value.
     *
     * <p>MSAFP (Maternal Serum Alpha-Fetoprotein) is a prenatal screening test that measures the level
     * of alpha-fetoprotein in the mother's blood. This test is primarily used to screen for:
     * <ul>
     *   <li>Neural tube defects (spina bifida, anencephaly)</li>
     *   <li>Abdominal wall defects</li>
     *   <li>Chromosomal abnormalities when combined with other markers</li>
     * </ul>
     * Elevated or decreased levels may indicate potential fetal abnormalities and typically prompt
     * further diagnostic testing such as ultrasound or amniocentesis.</p>
     *
     * @return String the MSAFP test result value, or null if not set
     */
    public String getMSAFP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.MSAFP$4, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the MSAFP screening test result as an XMLBeans XmlString object.
     *
     * <p>This method provides access to the underlying XMLBeans representation for advanced
     * XML operations and schema validation.</p>
     *
     * @return XmlString the XMLBeans representation of the MSAFP test result, or null if not set
     */
    public XmlString xgetMSAFP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.MSAFP$4, 0);
            return target;
        }
    }

    /**
     * Sets the MSAFP screening test result value.
     *
     * <p>Stores the result or status of the Maternal Serum Alpha-Fetoprotein screening test.
     * The value typically represents test results, AFP levels, risk assessment, or completion status.</p>
     *
     * @param msafp String the MSAFP test result value to set
     */
    public void setMSAFP(final String msafp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.MSAFP$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.MSAFP$4);
            }
            target.setStringValue(msafp);
        }
    }

    /**
     * Sets the MSAFP screening test result using an XMLBeans XmlString object.
     *
     * <p>This method accepts the XMLBeans representation directly for use with pre-validated
     * XML content or complex schema operations.</p>
     *
     * @param msafp XmlString the XMLBeans representation of the MSAFP test result
     */
    public void xsetMSAFP(final XmlString msafp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.MSAFP$4, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.MSAFP$4);
            }
            target.set((XmlObject)msafp);
        }
    }

    /**
     * Gets the custom laboratory investigation entry.
     *
     * <p>This field allows for documentation of additional prenatal genetic screening tests
     * that are not covered by the standard MSS/IPS/FTS, EDB/CVS, or MSAFP categories. This
     * flexibility accommodates:</p>
     * <ul>
     *   <li>Emerging genetic screening technologies</li>
     *   <li>Specialized tests for specific genetic conditions</li>
     *   <li>Province-specific or institution-specific screening protocols</li>
     *   <li>Cell-free DNA testing (NIPT)</li>
     *   <li>Carrier screening for genetic disorders</li>
     * </ul>
     *
     * @return CustomLab the custom laboratory investigation details, or null if not set
     * @see CustomLab
     */
    public CustomLab getCustomLab1() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.CUSTOMLAB1$6, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the custom laboratory investigation entry.
     *
     * <p>Stores details of additional prenatal genetic screening tests beyond the standard
     * screening categories. This allows healthcare providers to document any relevant genetic
     * testing performed during prenatal care.</p>
     *
     * @param customLab1 CustomLab the custom laboratory investigation details to set
     * @see CustomLab
     */
    public void setCustomLab1(final CustomLab customLab1) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.CUSTOMLAB1$6, 0);
            if (target == null) {
                target = (CustomLab)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.CUSTOMLAB1$6);
            }
            target.set((XmlObject)customLab1);
        }
    }

    /**
     * Adds and returns a new custom laboratory investigation entry.
     *
     * <p>Creates a new {@link CustomLab} element in the XML structure and returns it for
     * immediate population with test details. This method is typically used when building
     * new prenatal genetic screening records programmatically.</p>
     *
     * @return CustomLab the newly created custom laboratory investigation object
     * @see CustomLab
     */
    public CustomLab addNewCustomLab1() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CustomLab target = null;
            target = (CustomLab)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.CUSTOMLAB1$6);
            return target;
        }
    }

    /**
     * Gets the declined status indicating whether the patient has refused prenatal genetic screening.
     *
     * <p>This field documents informed patient choice regarding prenatal genetic screening. In accordance
     * with patient autonomy principles and healthcare ethics, pregnant patients have the right to decline
     * any or all prenatal genetic screening tests after being provided with appropriate counseling about
     * the benefits, limitations, and implications of testing.</p>
     *
     * <p><strong>Clinical Significance:</strong> Documentation of declined screening is important for:
     * <ul>
     *   <li>Legal protection for healthcare providers</li>
     *   <li>Demonstrating informed consent was obtained</li>
     *   <li>Continuity of care documentation</li>
     *   <li>Quality assurance and healthcare reporting</li>
     * </ul></p>
     *
     * @return boolean true if prenatal genetic screening was declined by the patient, false otherwise
     */
    public boolean getDeclined() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.DECLINED$8, 0);
            return target != null && target.getBooleanValue();
        }
    }

    /**
     * Gets the declined status as an XMLBeans XmlBoolean object.
     *
     * <p>This method provides access to the underlying XMLBeans representation for advanced
     * XML operations and schema validation.</p>
     *
     * @return XmlBoolean the XMLBeans representation of the declined status, or null if not set
     */
    public XmlBoolean xgetDeclined() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.DECLINED$8, 0);
            return target;
        }
    }

    /**
     * Sets the declined status indicating whether the patient has refused prenatal genetic screening.
     *
     * <p>This should be set to true when a patient makes an informed decision to decline any or all
     * prenatal genetic screening after appropriate counseling. Documentation should include evidence
     * that informed consent discussions occurred.</p>
     *
     * @param declined boolean true to indicate screening was declined, false otherwise
     */
    public void setDeclined(final boolean declined) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.DECLINED$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.DECLINED$8);
            }
            target.setBooleanValue(declined);
        }
    }

    /**
     * Sets the declined status using an XMLBeans XmlBoolean object.
     *
     * <p>This method accepts the XMLBeans representation directly for use with pre-validated
     * XML content or complex schema operations.</p>
     *
     * @param declined XmlBoolean the XMLBeans representation of the declined status
     */
    public void xsetDeclined(final XmlBoolean declined) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlBoolean target = null;
            target = (XmlBoolean)this.get_store().find_element_user(PrenatalGeneticScreeningTypeImpl.DECLINED$8, 0);
            if (target == null) {
                target = (XmlBoolean)this.get_store().add_element_user(PrenatalGeneticScreeningTypeImpl.DECLINED$8);
            }
            target.set((XmlObject)declined);
        }
    }
    
    static {
        MSSIPSFTS$0 = new QName("http://www.oscarmcmaster.org/AR2005", "MSS_IPS_FTS");
        EDBCVS$2 = new QName("http://www.oscarmcmaster.org/AR2005", "EDB_CVS");
        MSAFP$4 = new QName("http://www.oscarmcmaster.org/AR2005", "MSAFP");
        CUSTOMLAB1$6 = new QName("http://www.oscarmcmaster.org/AR2005", "customLab1");
        DECLINED$8 = new QName("http://www.oscarmcmaster.org/AR2005", "declined");
    }
}
