package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.YesNoNullType;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.MedicalHistoryType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the MedicalHistoryType interface for tracking patient medical history
 * as part of the British Columbia Antenatal Record (AR2005) system.
 *
 * <p>This class provides XML-based storage and retrieval of significant medical conditions
 * that may impact pregnancy and prenatal care. It tracks yes/no/null responses for various
 * medical conditions including hypertension, cardiac issues, liver disease, psychiatric
 * conditions, and surgical history. The data structure supports the BC Antenatal Record
 * form used for comprehensive pregnancy documentation.</p>
 *
 * <p>The implementation uses Apache XMLBeans for XML serialization and deserialization,
 * providing thread-safe access to medical history data through synchronized accessor methods.
 * All medical condition fields use the YesNoNullType enumeration to represent affirmative,
 * negative, or unknown medical history responses.</p>
 *
 * <p>Medical conditions tracked include:</p>
 * <ul>
 *   <li>Hypertension (high blood pressure)</li>
 *   <li>Endocrine disorders</li>
 *   <li>Urinary tract conditions</li>
 *   <li>Cardiac (heart) conditions</li>
 *   <li>Liver disease</li>
 *   <li>Gynaecological conditions</li>
 *   <li>Hematological disorders</li>
 *   <li>Previous surgeries</li>
 *   <li>Blood transfusion history</li>
 *   <li>Anesthetic complications</li>
 *   <li>Psychiatric conditions</li>
 *   <li>Epilepsy</li>
 *   <li>Other conditions (with free-text description)</li>
 * </ul>
 *
 * @see ca.openosp.openo.ar2005.MedicalHistoryType
 * @see ca.openosp.openo.ar2005.YesNoNullType
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-24
 */
public class MedicalHistoryTypeImpl extends XmlComplexContentImpl implements MedicalHistoryType
{
    private static final long serialVersionUID = 1L;
    private static final QName HYPERTENSION$0;
    private static final QName ENDORINCE$2;
    private static final QName URINARYTRACT$4;
    private static final QName CARDIAC$6;
    private static final QName LIVER$8;
    private static final QName GYNAECOLOGY$10;
    private static final QName HEM$12;
    private static final QName SURGERIES$14;
    private static final QName BLOODTRANSFUSION$16;
    private static final QName ANESTHETICS$18;
    private static final QName PSYCHIATRY$20;
    private static final QName EPILEPSY$22;
    private static final QName OTHERDESCR$24;
    private static final QName OTHER$26;

    /**
     * Constructs a new MedicalHistoryTypeImpl instance with the specified schema type.
     *
     * <p>This constructor initializes the XMLBeans complex content implementation
     * with the provided schema type definition. It is typically called by the
     * XMLBeans framework during XML parsing or object instantiation.</p>
     *
     * @param sType SchemaType the schema type definition for this medical history type
     */
    public MedicalHistoryTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the hypertension medical history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of hypertension (high blood pressure). This is a critical indicator for
     * prenatal care as hypertension can lead to complications during pregnancy.</p>
     *
     * @return YesNoNullType the hypertension status, or null if not set
     */
    public YesNoNullType getHypertension() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.HYPERTENSION$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the hypertension medical history indicator.
     *
     * <p>Records whether the patient has a history of hypertension. This information
     * is essential for risk assessment and care planning during pregnancy.</p>
     *
     * @param hypertension YesNoNullType the hypertension status to set
     */
    public void setHypertension(final YesNoNullType hypertension) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.HYPERTENSION$0, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.HYPERTENSION$0);
            }
            target.set((XmlObject)hypertension);
        }
    }

    /**
     * Creates and adds a new hypertension element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording hypertension status.
     * This method is used when adding hypertension data to a previously empty field.</p>
     *
     * @return YesNoNullType the newly created hypertension element
     */
    public YesNoNullType addNewHypertension() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.HYPERTENSION$0);
            return target;
        }
    }

    /**
     * Retrieves the endocrine disorder medical history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of endocrine disorders (such as thyroid disease, diabetes). Endocrine conditions
     * require special monitoring during pregnancy.</p>
     *
     * @return YesNoNullType the endocrine disorder status, or null if not set
     */
    public YesNoNullType getEndorince() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.ENDORINCE$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the endocrine disorder medical history indicator.
     *
     * <p>Records whether the patient has a history of endocrine disorders.
     * This information is important for pregnancy management and risk assessment.</p>
     *
     * @param endorince YesNoNullType the endocrine disorder status to set
     */
    public void setEndorince(final YesNoNullType endorince) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.ENDORINCE$2, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.ENDORINCE$2);
            }
            target.set((XmlObject)endorince);
        }
    }

    /**
     * Creates and adds a new endocrine disorder element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording endocrine disorder status.</p>
     *
     * @return YesNoNullType the newly created endocrine disorder element
     */
    public YesNoNullType addNewEndorince() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.ENDORINCE$2);
            return target;
        }
    }

    /**
     * Retrieves the urinary tract condition medical history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of urinary tract conditions (such as recurrent infections, kidney disease).
     * Urinary tract issues are monitored closely during pregnancy.</p>
     *
     * @return YesNoNullType the urinary tract condition status, or null if not set
     */
    public YesNoNullType getUrinaryTract() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.URINARYTRACT$4, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the urinary tract condition medical history indicator.
     *
     * <p>Records whether the patient has a history of urinary tract conditions.</p>
     *
     * @param urinaryTract YesNoNullType the urinary tract condition status to set
     */
    public void setUrinaryTract(final YesNoNullType urinaryTract) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.URINARYTRACT$4, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.URINARYTRACT$4);
            }
            target.set((XmlObject)urinaryTract);
        }
    }

    /**
     * Creates and adds a new urinary tract element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording urinary tract condition status.</p>
     *
     * @return YesNoNullType the newly created urinary tract element
     */
    public YesNoNullType addNewUrinaryTract() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.URINARYTRACT$4);
            return target;
        }
    }

    /**
     * Retrieves the cardiac condition medical history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of cardiac (heart) conditions. Heart disease requires specialized care and
     * monitoring during pregnancy to ensure maternal and fetal safety.</p>
     *
     * @return YesNoNullType the cardiac condition status, or null if not set
     */
    public YesNoNullType getCardiac() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.CARDIAC$6, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the cardiac condition medical history indicator.
     *
     * <p>Records whether the patient has a history of cardiac conditions.</p>
     *
     * @param cardiac YesNoNullType the cardiac condition status to set
     */
    public void setCardiac(final YesNoNullType cardiac) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.CARDIAC$6, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.CARDIAC$6);
            }
            target.set((XmlObject)cardiac);
        }
    }

    /**
     * Creates and adds a new cardiac element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording cardiac condition status.</p>
     *
     * @return YesNoNullType the newly created cardiac element
     */
    public YesNoNullType addNewCardiac() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.CARDIAC$6);
            return target;
        }
    }

    /**
     * Retrieves the liver disease medical history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of liver disease. Liver conditions can affect medication metabolism and
     * pregnancy outcomes.</p>
     *
     * @return YesNoNullType the liver disease status, or null if not set
     */
    public YesNoNullType getLiver() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.LIVER$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the liver disease medical history indicator.
     *
     * <p>Records whether the patient has a history of liver disease.</p>
     *
     * @param liver YesNoNullType the liver disease status to set
     */
    public void setLiver(final YesNoNullType liver) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.LIVER$8, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.LIVER$8);
            }
            target.set((XmlObject)liver);
        }
    }

    /**
     * Creates and adds a new liver element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording liver disease status.</p>
     *
     * @return YesNoNullType the newly created liver element
     */
    public YesNoNullType addNewLiver() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.LIVER$8);
            return target;
        }
    }

    /**
     * Retrieves the gynaecological condition medical history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of gynaecological conditions (such as fibroids, endometriosis, previous
     * pregnancy complications). This history is particularly relevant for pregnancy care.</p>
     *
     * @return YesNoNullType the gynaecological condition status, or null if not set
     */
    public YesNoNullType getGynaecology() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.GYNAECOLOGY$10, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the gynaecological condition medical history indicator.
     *
     * <p>Records whether the patient has a history of gynaecological conditions.</p>
     *
     * @param gynaecology YesNoNullType the gynaecological condition status to set
     */
    public void setGynaecology(final YesNoNullType gynaecology) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.GYNAECOLOGY$10, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.GYNAECOLOGY$10);
            }
            target.set((XmlObject)gynaecology);
        }
    }

    /**
     * Creates and adds a new gynaecology element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording gynaecological condition status.</p>
     *
     * @return YesNoNullType the newly created gynaecology element
     */
    public YesNoNullType addNewGynaecology() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.GYNAECOLOGY$10);
            return target;
        }
    }

    /**
     * Retrieves the hematological disorder medical history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of hematological disorders (blood disorders such as anemia, clotting disorders,
     * sickle cell disease). Blood disorders require careful monitoring during pregnancy.</p>
     *
     * @return YesNoNullType the hematological disorder status, or null if not set
     */
    public YesNoNullType getHem() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.HEM$12, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the hematological disorder medical history indicator.
     *
     * <p>Records whether the patient has a history of hematological disorders.</p>
     *
     * @param hem YesNoNullType the hematological disorder status to set
     */
    public void setHem(final YesNoNullType hem) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.HEM$12, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.HEM$12);
            }
            target.set((XmlObject)hem);
        }
    }

    /**
     * Creates and adds a new hematological element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording hematological disorder status.</p>
     *
     * @return YesNoNullType the newly created hematological element
     */
    public YesNoNullType addNewHem() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.HEM$12);
            return target;
        }
    }

    /**
     * Retrieves the surgical history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of surgeries. Previous surgeries, especially abdominal or pelvic procedures,
     * can affect pregnancy and delivery planning.</p>
     *
     * @return YesNoNullType the surgical history status, or null if not set
     */
    public YesNoNullType getSurgeries() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.SURGERIES$14, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the surgical history indicator.
     *
     * <p>Records whether the patient has a history of surgeries.</p>
     *
     * @param surgeries YesNoNullType the surgical history status to set
     */
    public void setSurgeries(final YesNoNullType surgeries) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.SURGERIES$14, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.SURGERIES$14);
            }
            target.set((XmlObject)surgeries);
        }
    }

    /**
     * Creates and adds a new surgeries element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording surgical history status.</p>
     *
     * @return YesNoNullType the newly created surgeries element
     */
    public YesNoNullType addNewSurgeries() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.SURGERIES$14);
            return target;
        }
    }

    /**
     * Retrieves the blood transfusion history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of blood transfusions. This information is important for assessing potential
     * blood-borne infection risks and antibody formation.</p>
     *
     * @return YesNoNullType the blood transfusion history status, or null if not set
     */
    public YesNoNullType getBloodTransfusion() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.BLOODTRANSFUSION$16, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the blood transfusion history indicator.
     *
     * <p>Records whether the patient has a history of blood transfusions.</p>
     *
     * @param bloodTransfusion YesNoNullType the blood transfusion history status to set
     */
    public void setBloodTransfusion(final YesNoNullType bloodTransfusion) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.BLOODTRANSFUSION$16, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.BLOODTRANSFUSION$16);
            }
            target.set((XmlObject)bloodTransfusion);
        }
    }

    /**
     * Creates and adds a new blood transfusion element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording blood transfusion history status.</p>
     *
     * @return YesNoNullType the newly created blood transfusion element
     */
    public YesNoNullType addNewBloodTransfusion() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.BLOODTRANSFUSION$16);
            return target;
        }
    }

    /**
     * Retrieves the anesthetic complication history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of anesthetic complications. This is critical information for anesthesiologists
     * when planning pain management during labor and delivery.</p>
     *
     * @return YesNoNullType the anesthetic complication history status, or null if not set
     */
    public YesNoNullType getAnesthetics() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.ANESTHETICS$18, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the anesthetic complication history indicator.
     *
     * <p>Records whether the patient has a history of anesthetic complications.</p>
     *
     * @param anesthetics YesNoNullType the anesthetic complication history status to set
     */
    public void setAnesthetics(final YesNoNullType anesthetics) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.ANESTHETICS$18, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.ANESTHETICS$18);
            }
            target.set((XmlObject)anesthetics);
        }
    }

    /**
     * Creates and adds a new anesthetics element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording anesthetic complication history status.</p>
     *
     * @return YesNoNullType the newly created anesthetics element
     */
    public YesNoNullType addNewAnesthetics() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.ANESTHETICS$18);
            return target;
        }
    }

    /**
     * Retrieves the psychiatric condition history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of psychiatric conditions (such as depression, anxiety, bipolar disorder).
     * Mental health history is important for comprehensive perinatal care and
     * screening for postpartum depression risk.</p>
     *
     * @return YesNoNullType the psychiatric condition history status, or null if not set
     */
    public YesNoNullType getPsychiatry() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.PSYCHIATRY$20, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the psychiatric condition history indicator.
     *
     * <p>Records whether the patient has a history of psychiatric conditions.</p>
     *
     * @param psychiatry YesNoNullType the psychiatric condition history status to set
     */
    public void setPsychiatry(final YesNoNullType psychiatry) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.PSYCHIATRY$20, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.PSYCHIATRY$20);
            }
            target.set((XmlObject)psychiatry);
        }
    }

    /**
     * Creates and adds a new psychiatry element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording psychiatric condition history status.</p>
     *
     * @return YesNoNullType the newly created psychiatry element
     */
    public YesNoNullType addNewPsychiatry() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.PSYCHIATRY$20);
            return target;
        }
    }

    /**
     * Retrieves the epilepsy history indicator.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has a history
     * of epilepsy or seizure disorders. This requires careful medication management
     * during pregnancy to balance seizure control with fetal safety.</p>
     *
     * @return YesNoNullType the epilepsy history status, or null if not set
     */
    public YesNoNullType getEpilepsy() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.EPILEPSY$22, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the epilepsy history indicator.
     *
     * <p>Records whether the patient has a history of epilepsy or seizure disorders.</p>
     *
     * @param epilepsy YesNoNullType the epilepsy history status to set
     */
    public void setEpilepsy(final YesNoNullType epilepsy) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.EPILEPSY$22, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.EPILEPSY$22);
            }
            target.set((XmlObject)epilepsy);
        }
    }

    /**
     * Creates and adds a new epilepsy element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording epilepsy history status.</p>
     *
     * @return YesNoNullType the newly created epilepsy element
     */
    public YesNoNullType addNewEpilepsy() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.EPILEPSY$22);
            return target;
        }
    }

    /**
     * Retrieves the free-text description of other medical conditions.
     *
     * <p>Gets the string value containing additional medical history information
     * not captured by the predefined condition fields. This allows healthcare
     * providers to document any relevant medical conditions that don't fit into
     * the standard categories.</p>
     *
     * @return String the description of other medical conditions, or null if not set
     */
    public String getOtherDescr() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(MedicalHistoryTypeImpl.OTHERDESCR$24, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the XML representation of the other medical conditions description.
     *
     * <p>Gets the XmlString object containing the description of other medical
     * conditions. This method provides access to the underlying XML structure
     * for advanced XML processing scenarios.</p>
     *
     * @return XmlString the XML representation of the description, or null if not set
     */
    public XmlString xgetOtherDescr() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(MedicalHistoryTypeImpl.OTHERDESCR$24, 0);
            return target;
        }
    }

    /**
     * Sets the free-text description of other medical conditions.
     *
     * <p>Stores additional medical history information not covered by the
     * predefined condition fields.</p>
     *
     * @param otherDescr String the description of other medical conditions to set
     */
    public void setOtherDescr(final String otherDescr) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(MedicalHistoryTypeImpl.OTHERDESCR$24, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(MedicalHistoryTypeImpl.OTHERDESCR$24);
            }
            target.setStringValue(otherDescr);
        }
    }

    /**
     * Sets the XML representation of the other medical conditions description.
     *
     * <p>Stores the description using an XmlString object, providing direct
     * XML-level access for advanced XML processing scenarios.</p>
     *
     * @param otherDescr XmlString the XML representation of the description to set
     */
    public void xsetOtherDescr(final XmlString otherDescr) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(MedicalHistoryTypeImpl.OTHERDESCR$24, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(MedicalHistoryTypeImpl.OTHERDESCR$24);
            }
            target.set((XmlObject)otherDescr);
        }
    }

    /**
     * Retrieves the "other conditions" indicator flag.
     *
     * <p>Gets the yes/no/null status indicating whether the patient has other
     * medical conditions beyond the predefined categories. When set to yes,
     * details should be provided in the otherDescr field.</p>
     *
     * @return YesNoNullType the other conditions flag status, or null if not set
     */
    public YesNoNullType getOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.OTHER$26, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the "other conditions" indicator flag.
     *
     * <p>Records whether the patient has other medical conditions. This flag
     * is typically set to yes when additional conditions are described in otherDescr.</p>
     *
     * @param other YesNoNullType the other conditions flag status to set
     */
    public void setOther(final YesNoNullType other) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(MedicalHistoryTypeImpl.OTHER$26, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.OTHER$26);
            }
            target.set((XmlObject)other);
        }
    }

    /**
     * Creates and adds a new "other conditions" element to the medical history.
     *
     * <p>Initializes a new YesNoNullType element for recording the other conditions flag status.</p>
     *
     * @return YesNoNullType the newly created other conditions element
     */
    public YesNoNullType addNewOther() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(MedicalHistoryTypeImpl.OTHER$26);
            return target;
        }
    }
    
    static {
        HYPERTENSION$0 = new QName("http://www.oscarmcmaster.org/AR2005", "hypertension");
        ENDORINCE$2 = new QName("http://www.oscarmcmaster.org/AR2005", "endorince");
        URINARYTRACT$4 = new QName("http://www.oscarmcmaster.org/AR2005", "urinaryTract");
        CARDIAC$6 = new QName("http://www.oscarmcmaster.org/AR2005", "cardiac");
        LIVER$8 = new QName("http://www.oscarmcmaster.org/AR2005", "liver");
        GYNAECOLOGY$10 = new QName("http://www.oscarmcmaster.org/AR2005", "gynaecology");
        HEM$12 = new QName("http://www.oscarmcmaster.org/AR2005", "hem");
        SURGERIES$14 = new QName("http://www.oscarmcmaster.org/AR2005", "surgeries");
        BLOODTRANSFUSION$16 = new QName("http://www.oscarmcmaster.org/AR2005", "bloodTransfusion");
        ANESTHETICS$18 = new QName("http://www.oscarmcmaster.org/AR2005", "anesthetics");
        PSYCHIATRY$20 = new QName("http://www.oscarmcmaster.org/AR2005", "psychiatry");
        EPILEPSY$22 = new QName("http://www.oscarmcmaster.org/AR2005", "epilepsy");
        OTHERDESCR$24 = new QName("http://www.oscarmcmaster.org/AR2005", "otherDescr");
        OTHER$26 = new QName("http://www.oscarmcmaster.org/AR2005", "other");
    }
}
