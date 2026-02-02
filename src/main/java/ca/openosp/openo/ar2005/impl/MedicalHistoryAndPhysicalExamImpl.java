package ca.openosp.openo.ar2005.impl;

import ca.openosp.openo.ar2005.PhysicalExaminationType;
import ca.openosp.openo.ar2005.FamilyHistoryType;
import ca.openosp.openo.ar2005.PsychosocialType;
import ca.openosp.openo.ar2005.InfectiousDiseaseType;
import ca.openosp.openo.ar2005.GenericHistoryType;
import ca.openosp.openo.ar2005.MedicalHistoryType;
import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.CurrentPregnancyType;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.MedicalHistoryAndPhysicalExam;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation for medical history and physical examination data used in the British Columbia
 * Antenatal Record (AR2005) system. This class provides access to comprehensive prenatal care documentation
 * including current pregnancy details, medical history, infectious disease screening, psychosocial assessment,
 * family history, and physical examination findings.
 *
 * <p>This implementation is part of the OpenO EMR's support for standardized prenatal care forms used across
 * Canadian healthcare jurisdictions. The AR2005 format is specifically designed for BC's antenatal care
 * documentation requirements and integrates with provincial health information systems.</p>
 *
 * <p>The class extends Apache XMLBeans {@link XmlComplexContentImpl} to provide XML serialization/deserialization
 * capabilities for healthcare data exchange while maintaining thread safety through synchronized access to the
 * underlying XML store.</p>
 *
 * <p><strong>Key Medical Data Sections:</strong></p>
 * <ul>
 *   <li><strong>Current Pregnancy:</strong> Gestational details, prenatal care timeline, expected delivery date</li>
 *   <li><strong>Medical History:</strong> Previous pregnancies, surgeries, chronic conditions, medications</li>
 *   <li><strong>Generic History:</strong> General health background and relevant medical events</li>
 *   <li><strong>Infectious Disease:</strong> Screening results for conditions affecting pregnancy (HIV, Hepatitis B, etc.)</li>
 *   <li><strong>Psychosocial:</strong> Mental health, substance use, social support assessment</li>
 *   <li><strong>Family History:</strong> Hereditary conditions, genetic risk factors</li>
 *   <li><strong>Physical Examination:</strong> Clinical findings, vital signs, fetal development assessment</li>
 * </ul>
 *
 * @see MedicalHistoryAndPhysicalExam
 * @see CurrentPregnancyType
 * @see MedicalHistoryType
 * @see GenericHistoryType
 * @see InfectiousDiseaseType
 * @see PsychosocialType
 * @see FamilyHistoryType
 * @see PhysicalExaminationType
 * @since 2026-01-24
 */
public class MedicalHistoryAndPhysicalExamImpl extends XmlComplexContentImpl implements MedicalHistoryAndPhysicalExam
{
    private static final long serialVersionUID = 1L;
    private static final QName CURRENTPREGNANCY$0;
    private static final QName MEDICALHISTORY$2;
    private static final QName GENERICHISTORY$4;
    private static final QName INFECTIOUSDISEASE$6;
    private static final QName PSYCHOSOCIAL$8;
    private static final QName FAMILYHISTORY$10;
    private static final QName PHYSICALEXAMINATION$12;

    /**
     * Constructs a new MedicalHistoryAndPhysicalExamImpl instance with the specified schema type.
     * This constructor is typically called by the XMLBeans framework during XML parsing or
     * when creating new instances through the Factory class.
     *
     * @param sType SchemaType the schema type definition for this XML element
     */
    public MedicalHistoryAndPhysicalExamImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the current pregnancy information from the antenatal record.
     * This section contains details about the current pregnancy including gestational age,
     * expected delivery date, prenatal care appointments, and pregnancy-specific health monitoring.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return CurrentPregnancyType the current pregnancy data, or null if not present in the record
     */
    public CurrentPregnancyType getCurrentPregnancy() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CurrentPregnancyType target = null;
            target = (CurrentPregnancyType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.CURRENTPREGNANCY$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the current pregnancy information in the antenatal record.
     * This method either updates an existing current pregnancy element or creates a new one
     * if not already present in the XML document.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @param currentPregnancy CurrentPregnancyType the current pregnancy data to set
     */
    public void setCurrentPregnancy(final CurrentPregnancyType currentPregnancy) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CurrentPregnancyType target = null;
            target = (CurrentPregnancyType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.CURRENTPREGNANCY$0, 0);
            if (target == null) {
                target = (CurrentPregnancyType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.CURRENTPREGNANCY$0);
            }
            target.set((XmlObject)currentPregnancy);
        }
    }

    /**
     * Creates and adds a new current pregnancy element to the antenatal record.
     * This method initializes a new XML element in the underlying store and returns a reference
     * to it for populating pregnancy-specific data.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return CurrentPregnancyType a newly created current pregnancy element ready for data entry
     */
    public CurrentPregnancyType addNewCurrentPregnancy() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CurrentPregnancyType target = null;
            target = (CurrentPregnancyType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.CURRENTPREGNANCY$0);
            return target;
        }
    }

    /**
     * Retrieves the medical history section from the antenatal record.
     * This section contains comprehensive information about the patient's previous pregnancies,
     * surgeries, chronic medical conditions, current medications, allergies, and other relevant
     * medical background that may affect prenatal care.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return MedicalHistoryType the medical history data, or null if not present in the record
     */
    public MedicalHistoryType getMedicalHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            MedicalHistoryType target = null;
            target = (MedicalHistoryType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.MEDICALHISTORY$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the medical history section in the antenatal record.
     * This method either updates an existing medical history element or creates a new one
     * if not already present in the XML document.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @param medicalHistory MedicalHistoryType the medical history data to set
     */
    public void setMedicalHistory(final MedicalHistoryType medicalHistory) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            MedicalHistoryType target = null;
            target = (MedicalHistoryType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.MEDICALHISTORY$2, 0);
            if (target == null) {
                target = (MedicalHistoryType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.MEDICALHISTORY$2);
            }
            target.set((XmlObject)medicalHistory);
        }
    }

    /**
     * Creates and adds a new medical history element to the antenatal record.
     * This method initializes a new XML element in the underlying store and returns a reference
     * to it for populating medical history data.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return MedicalHistoryType a newly created medical history element ready for data entry
     */
    public MedicalHistoryType addNewMedicalHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            MedicalHistoryType target = null;
            target = (MedicalHistoryType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.MEDICALHISTORY$2);
            return target;
        }
    }

    /**
     * Retrieves the generic history section from the antenatal record.
     * This section captures general health background and medical events that do not fit into
     * specific categories but are relevant to prenatal care planning and risk assessment.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return GenericHistoryType the generic history data, or null if not present in the record
     */
    public GenericHistoryType getGenericHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GenericHistoryType target = null;
            target = (GenericHistoryType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.GENERICHISTORY$4, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the generic history section in the antenatal record.
     * This method either updates an existing generic history element or creates a new one
     * if not already present in the XML document.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @param genericHistory GenericHistoryType the generic history data to set
     */
    public void setGenericHistory(final GenericHistoryType genericHistory) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GenericHistoryType target = null;
            target = (GenericHistoryType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.GENERICHISTORY$4, 0);
            if (target == null) {
                target = (GenericHistoryType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.GENERICHISTORY$4);
            }
            target.set((XmlObject)genericHistory);
        }
    }

    /**
     * Creates and adds a new generic history element to the antenatal record.
     * This method initializes a new XML element in the underlying store and returns a reference
     * to it for populating generic history data.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return GenericHistoryType a newly created generic history element ready for data entry
     */
    public GenericHistoryType addNewGenericHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            GenericHistoryType target = null;
            target = (GenericHistoryType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.GENERICHISTORY$4);
            return target;
        }
    }

    /**
     * Retrieves the infectious disease screening section from the antenatal record.
     * This section contains critical screening results for infectious diseases that can affect
     * pregnancy outcomes, including HIV, Hepatitis B, Hepatitis C, syphilis, Group B Streptococcus,
     * and other communicable diseases requiring prenatal monitoring and intervention.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return InfectiousDiseaseType the infectious disease screening data, or null if not present in the record
     */
    public InfectiousDiseaseType getInfectiousDisease() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            InfectiousDiseaseType target = null;
            target = (InfectiousDiseaseType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.INFECTIOUSDISEASE$6, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the infectious disease screening section in the antenatal record.
     * This method either updates an existing infectious disease element or creates a new one
     * if not already present in the XML document.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @param infectiousDisease InfectiousDiseaseType the infectious disease screening data to set
     */
    public void setInfectiousDisease(final InfectiousDiseaseType infectiousDisease) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            InfectiousDiseaseType target = null;
            target = (InfectiousDiseaseType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.INFECTIOUSDISEASE$6, 0);
            if (target == null) {
                target = (InfectiousDiseaseType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.INFECTIOUSDISEASE$6);
            }
            target.set((XmlObject)infectiousDisease);
        }
    }

    /**
     * Creates and adds a new infectious disease screening element to the antenatal record.
     * This method initializes a new XML element in the underlying store and returns a reference
     * to it for populating infectious disease screening results.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return InfectiousDiseaseType a newly created infectious disease element ready for data entry
     */
    public InfectiousDiseaseType addNewInfectiousDisease() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            InfectiousDiseaseType target = null;
            target = (InfectiousDiseaseType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.INFECTIOUSDISEASE$6);
            return target;
        }
    }

    /**
     * Retrieves the psychosocial assessment section from the antenatal record.
     * This section documents mental health status, substance use history, domestic violence screening,
     * social support networks, and other psychosocial factors that may impact pregnancy outcomes
     * and require supportive interventions during prenatal care.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return PsychosocialType the psychosocial assessment data, or null if not present in the record
     */
    public PsychosocialType getPsychosocial() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PsychosocialType target = null;
            target = (PsychosocialType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.PSYCHOSOCIAL$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the psychosocial assessment section in the antenatal record.
     * This method either updates an existing psychosocial element or creates a new one
     * if not already present in the XML document.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @param psychosocial PsychosocialType the psychosocial assessment data to set
     */
    public void setPsychosocial(final PsychosocialType psychosocial) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PsychosocialType target = null;
            target = (PsychosocialType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.PSYCHOSOCIAL$8, 0);
            if (target == null) {
                target = (PsychosocialType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.PSYCHOSOCIAL$8);
            }
            target.set((XmlObject)psychosocial);
        }
    }

    /**
     * Creates and adds a new psychosocial assessment element to the antenatal record.
     * This method initializes a new XML element in the underlying store and returns a reference
     * to it for populating psychosocial assessment data.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return PsychosocialType a newly created psychosocial assessment element ready for data entry
     */
    public PsychosocialType addNewPsychosocial() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PsychosocialType target = null;
            target = (PsychosocialType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.PSYCHOSOCIAL$8);
            return target;
        }
    }

    /**
     * Retrieves the family history section from the antenatal record.
     * This section documents hereditary conditions, genetic disorders, and family health patterns
     * that may indicate increased risk for pregnancy complications or congenital conditions requiring
     * specialized monitoring, genetic counseling, or prenatal testing.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return FamilyHistoryType the family history data, or null if not present in the record
     */
    public FamilyHistoryType getFamilyHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            FamilyHistoryType target = null;
            target = (FamilyHistoryType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.FAMILYHISTORY$10, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the family history section in the antenatal record.
     * This method either updates an existing family history element or creates a new one
     * if not already present in the XML document.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @param familyHistory FamilyHistoryType the family history data to set
     */
    public void setFamilyHistory(final FamilyHistoryType familyHistory) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            FamilyHistoryType target = null;
            target = (FamilyHistoryType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.FAMILYHISTORY$10, 0);
            if (target == null) {
                target = (FamilyHistoryType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.FAMILYHISTORY$10);
            }
            target.set((XmlObject)familyHistory);
        }
    }

    /**
     * Creates and adds a new family history element to the antenatal record.
     * This method initializes a new XML element in the underlying store and returns a reference
     * to it for populating family history data.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return FamilyHistoryType a newly created family history element ready for data entry
     */
    public FamilyHistoryType addNewFamilyHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            FamilyHistoryType target = null;
            target = (FamilyHistoryType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.FAMILYHISTORY$10);
            return target;
        }
    }

    /**
     * Retrieves the physical examination section from the antenatal record.
     * This section contains comprehensive clinical findings from physical examinations performed
     * during prenatal care, including vital signs, fetal development measurements, maternal health
     * assessments, and any abnormal findings requiring medical attention or follow-up monitoring.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return PhysicalExaminationType the physical examination data, or null if not present in the record
     */
    public PhysicalExaminationType getPhysicalExamination() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PhysicalExaminationType target = null;
            target = (PhysicalExaminationType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.PHYSICALEXAMINATION$12, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the physical examination section in the antenatal record.
     * This method either updates an existing physical examination element or creates a new one
     * if not already present in the XML document.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @param physicalExamination PhysicalExaminationType the physical examination data to set
     */
    public void setPhysicalExamination(final PhysicalExaminationType physicalExamination) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PhysicalExaminationType target = null;
            target = (PhysicalExaminationType)this.get_store().find_element_user(MedicalHistoryAndPhysicalExamImpl.PHYSICALEXAMINATION$12, 0);
            if (target == null) {
                target = (PhysicalExaminationType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.PHYSICALEXAMINATION$12);
            }
            target.set((XmlObject)physicalExamination);
        }
    }

    /**
     * Creates and adds a new physical examination element to the antenatal record.
     * This method initializes a new XML element in the underlying store and returns a reference
     * to it for populating physical examination findings.
     *
     * <p>Thread-safe access is provided through synchronization on the internal XML store monitor.</p>
     *
     * @return PhysicalExaminationType a newly created physical examination element ready for data entry
     */
    public PhysicalExaminationType addNewPhysicalExamination() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PhysicalExaminationType target = null;
            target = (PhysicalExaminationType)this.get_store().add_element_user(MedicalHistoryAndPhysicalExamImpl.PHYSICALEXAMINATION$12);
            return target;
        }
    }
    
    static {
        CURRENTPREGNANCY$0 = new QName("http://www.oscarmcmaster.org/AR2005", "currentPregnancy");
        MEDICALHISTORY$2 = new QName("http://www.oscarmcmaster.org/AR2005", "medicalHistory");
        GENERICHISTORY$4 = new QName("http://www.oscarmcmaster.org/AR2005", "genericHistory");
        INFECTIOUSDISEASE$6 = new QName("http://www.oscarmcmaster.org/AR2005", "infectiousDisease");
        PSYCHOSOCIAL$8 = new QName("http://www.oscarmcmaster.org/AR2005", "psychosocial");
        FAMILYHISTORY$10 = new QName("http://www.oscarmcmaster.org/AR2005", "familyHistory");
        PHYSICALEXAMINATION$12 = new QName("http://www.oscarmcmaster.org/AR2005", "physicalExamination");
    }
}
