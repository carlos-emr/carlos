package ca.openosp.openo.ar2005.impl;

import ca.openosp.openo.ar2005.SignatureType;
import ca.openosp.openo.ar2005.InitialLaboratoryInvestigations;
import ca.openosp.openo.ar2005.MedicalHistoryAndPhysicalExam;
import ca.openosp.openo.ar2005.ObstetricalHistory;
import ca.openosp.openo.ar2005.PregnancyHistory;
import ca.openosp.openo.ar2005.PractitionerInformation;
import ca.openosp.openo.ar2005.PartnerInformation;
import ca.openosp.openo.ar2005.PatientInformation;
import org.apache.xmlbeans.XmlDateTime;
import org.apache.xmlbeans.XmlDate;
import java.util.Calendar;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlInt;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.AR1;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the AR1 (Antenatal Record 1) interface for British Columbia Antenatal Record (BCAR) forms.
 *
 * This class provides the concrete implementation of the AR1 interface, which represents the primary
 * antenatal record form used in British Columbia healthcare facilities for comprehensive pregnancy
 * care documentation. It extends Apache XMLBeans' XmlComplexContentImpl to provide XML binding
 * capabilities for serialization, deserialization, and XML document manipulation.
 *
 * <p>The AR1 form is a standardized medical form used throughout BC to ensure consistent, high-quality
 * prenatal care documentation. This implementation handles the complete lifecycle of antenatal record
 * data including:</p>
 *
 * <ul>
 *   <li><b>Patient Demographics:</b> Patient identification, contact information, and health insurance details</li>
 *   <li><b>Partner Information:</b> Partner demographics and family history for genetic counseling</li>
 *   <li><b>Healthcare Providers:</b> Primary care provider, obstetrician, and care team assignments</li>
 *   <li><b>Pregnancy Details:</b> Current pregnancy information, estimated due date, and dating methods</li>
 *   <li><b>Obstetrical History:</b> Previous pregnancies, deliveries, complications, and risk factors</li>
 *   <li><b>Medical History:</b> Comprehensive medical history, medications, allergies, and physical examination</li>
 *   <li><b>Laboratory Results:</b> Blood type, infectious disease screening, genetic testing, and prenatal labs</li>
 *   <li><b>Clinical Documentation:</b> Provider notes, care plans, and ongoing clinical observations</li>
 *   <li><b>Regulatory Compliance:</b> Provider signatures and audit trails for legal and compliance requirements</li>
 * </ul>
 *
 * <p><b>XML Binding Architecture:</b></p>
 * <p>This implementation uses Apache XMLBeans for XML-to-Java binding. Each element in the AR1 XML schema
 * is mapped to QName constants (e.g., ID$0, PATIENTINFORMATION$14) which are used to access and manipulate
 * the underlying XML store. The implementation provides both strongly-typed methods (getId(), getPatientInformation())
 * and XML-aware methods (xgetId(), xsetId()) for flexible data access.</p>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>All accessor methods are synchronized on the XMLBeans monitor to ensure thread-safe access to the
 * underlying XML document store. This prevents concurrent modification issues when multiple threads
 * access the same AR1 form instance.</p>
 *
 * <p><b>Healthcare Data Protection:</b></p>
 * <p>This class handles Protected Health Information (PHI) and must comply with PIPEDA privacy regulations
 * for Canadian healthcare data. All form data is considered confidential patient information and must be
 * handled with appropriate security measures including access controls, audit logging, and encryption
 * during storage and transmission.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create new AR1 form
 * AR1 ar1Form = AR1.Factory.newInstance();
 *
 * // Set basic identifiers
 * ar1Form.setId(12345);
 * ar1Form.setDemographicNo(67890);
 * ar1Form.setProviderNo("P001");
 * ar1Form.setFormCreated(Calendar.getInstance());
 *
 * // Add patient information
 * PatientInformation patient = ar1Form.addNewPatientInformation();
 * patient.setFirstName("Jane");
 * patient.setLastName("Doe");
 *
 * // Add pregnancy history
 * PregnancyHistory pregnancy = ar1Form.addNewPregnancyHistory();
 * pregnancy.setEstimatedDueDate(Calendar.getInstance());
 *
 * // Serialize to XML
 * String xmlString = ar1Form.xmlText();
 * }</pre>
 *
 * @see AR1
 * @see PatientInformation
 * @see PartnerInformation
 * @see PractitionerInformation
 * @see PregnancyHistory
 * @see ObstetricalHistory
 * @see MedicalHistoryAndPhysicalExam
 * @see InitialLaboratoryInvestigations
 * @see SignatureType
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 *
 * @since 2026-01-24
 */
public class AR1Impl extends XmlComplexContentImpl implements AR1
{
    private static final long serialVersionUID = 1L;
    private static final QName ID$0;
    private static final QName VERSIONID$2;
    private static final QName EPISODEID$4;
    private static final QName DEMOGRAPHICNO$6;
    private static final QName PROVIDERNO$8;
    private static final QName FORMCREATED$10;
    private static final QName FORMEDITED$12;
    private static final QName PATIENTINFORMATION$14;
    private static final QName PARTNERINFORMATION$16;
    private static final QName PRACTITIONERINFORMATION$18;
    private static final QName PREGNANCYHISTORY$20;
    private static final QName OBSTETRICALHISTORY$22;
    private static final QName MEDICALHISTORYANDPHYSICALEXAM$24;
    private static final QName INITIALLABORATORYINVESTIGATIONS$26;
    private static final QName COMMENTS$28;
    private static final QName EXTRACOMMENTS$30;
    private static final QName SIGNATURES$32;

    /**
     * Constructs a new AR1Impl instance with the specified schema type.
     *
     * This constructor is called by the XMLBeans framework during XML parsing and object
     * instantiation. It initializes the underlying XML complex content implementation with
     * the AR1 schema type definition.
     *
     * @param sType SchemaType the XMLBeans schema type for AR1 elements
     */
    public AR1Impl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the unique identifier for this AR1 form record.
     *
     * This method retrieves the integer ID value from the underlying XML store. The ID uniquely
     * identifies this antenatal record within the EMR system and is used for database persistence
     * and record retrieval operations.
     *
     * @return int the unique form identifier, or 0 if not set
     */
    public int getId() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.ID$0, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the unique identifier as an XmlInt object for XML serialization.
     *
     * This method returns the ID as an XMLBeans XmlInt type, which preserves XML-specific
     * metadata such as validation state and allows for more fine-grained XML manipulation.
     *
     * @return XmlInt the form identifier as XML integer type, or null if not set
     */
    public XmlInt xgetId() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(AR1Impl.ID$0, 0);
            return target;
        }
    }

    /**
     * Sets the unique identifier for this AR1 form record.
     *
     * This method sets the integer ID value in the underlying XML store. If the ID element
     * does not exist in the XML document, it will be created automatically.
     *
     * @param id int the unique form identifier to set
     */
    public void setId(final int id) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.ID$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.ID$0);
            }
            target.setIntValue(id);
        }
    }

    /**
     * Sets the unique identifier using an XmlInt object for XML deserialization.
     *
     * This method sets the ID from an XMLBeans XmlInt type, which is typically used during
     * XML deserialization or when copying XML elements between documents.
     *
     * @param id XmlInt the form identifier as XML integer type
     */
    public void xsetId(final XmlInt id) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(AR1Impl.ID$0, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(AR1Impl.ID$0);
            }
            target.set((XmlObject)id);
        }
    }

    /**
     * Gets the version identifier for form revision tracking.
     *
     * This method retrieves the version ID which tracks different versions of the same AR1
     * record across edits. It enables version history tracking and supports concurrent editing
     * scenarios where multiple healthcare providers may access the same patient record.
     *
     * @return int the version identifier, or 0 if not set
     */
    public int getVersionID() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.VERSIONID$2, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the version identifier as an XmlInt object for XML serialization.
     *
     * @return XmlInt the version identifier as XML integer type, or null if not set
     */
    public XmlInt xgetVersionID() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(AR1Impl.VERSIONID$2, 0);
            return target;
        }
    }

    /**
     * Sets the version identifier for form revision tracking.
     *
     * This method should be incremented each time the AR1 form is edited to maintain
     * an audit trail of changes and support optimistic locking for concurrent updates.
     *
     * @param versionID int the version identifier to set
     */
    public void setVersionID(final int versionID) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.VERSIONID$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.VERSIONID$2);
            }
            target.setIntValue(versionID);
        }
    }

    /**
     * Sets the version identifier using an XmlInt object for XML deserialization.
     *
     * @param versionID XmlInt the version identifier as XML integer type
     */
    public void xsetVersionID(final XmlInt versionID) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(AR1Impl.VERSIONID$2, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(AR1Impl.VERSIONID$2);
            }
            target.set((XmlObject)versionID);
        }
    }

    /**
     * Gets the episode identifier linking this form to a specific care episode.
     *
     * Episodes group related encounters, forms, and clinical data for continuity of care
     * tracking. This allows healthcare providers to view all pregnancy-related documentation
     * as part of a single care episode spanning the entire prenatal period.
     *
     * @return int the episode identifier, or 0 if not set
     */
    public int getEpisodeId() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.EPISODEID$4, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the episode identifier as an XmlInt object for XML serialization.
     *
     * @return XmlInt the episode identifier as XML integer type, or null if not set
     */
    public XmlInt xgetEpisodeId() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(AR1Impl.EPISODEID$4, 0);
            return target;
        }
    }

    /**
     * Sets the episode identifier linking this form to a specific care episode.
     *
     * @param episodeId int the episode identifier to set
     */
    public void setEpisodeId(final int episodeId) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.EPISODEID$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.EPISODEID$4);
            }
            target.setIntValue(episodeId);
        }
    }

    /**
     * Sets the episode identifier using an XmlInt object for XML deserialization.
     *
     * @param episodeId XmlInt the episode identifier as XML integer type
     */
    public void xsetEpisodeId(final XmlInt episodeId) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(AR1Impl.EPISODEID$4, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(AR1Impl.EPISODEID$4);
            }
            target.set((XmlObject)episodeId);
        }
    }

    /**
     * Gets the demographic number (patient identifier) for the pregnant patient.
     *
     * This method retrieves the patient's unique demographic identifier which links the
     * antenatal record to the patient's master demographic information including name,
     * date of birth, health insurance number, and contact details.
     *
     * @return int the patient's demographic number, or 0 if not set
     */
    public int getDemographicNo() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.DEMOGRAPHICNO$6, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the demographic number as an XmlInt object for XML serialization.
     *
     * @return XmlInt the demographic number as XML integer type, or null if not set
     */
    public XmlInt xgetDemographicNo() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(AR1Impl.DEMOGRAPHICNO$6, 0);
            return target;
        }
    }

    /**
     * Sets the demographic number (patient identifier) for the pregnant patient.
     *
     * @param demographicNo int the patient's demographic number to set
     */
    public void setDemographicNo(final int demographicNo) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.DEMOGRAPHICNO$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.DEMOGRAPHICNO$6);
            }
            target.setIntValue(demographicNo);
        }
    }

    /**
     * Sets the demographic number using an XmlInt object for XML deserialization.
     *
     * @param demographicNo XmlInt the demographic number as XML integer type
     */
    public void xsetDemographicNo(final XmlInt demographicNo) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(AR1Impl.DEMOGRAPHICNO$6, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(AR1Impl.DEMOGRAPHICNO$6);
            }
            target.set((XmlObject)demographicNo);
        }
    }

    /**
     * Gets the provider number identifying the healthcare practitioner responsible for this record.
     *
     * The provider number is the unique identifier for the healthcare practitioner (physician,
     * midwife, or nurse practitioner) who created or is primarily responsible for this antenatal
     * record. This links the form to the provider's credentials and billing information.
     *
     * @return String the provider's unique identifier, or null if not set
     */
    public String getProviderNo() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.PROVIDERNO$8, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the provider number as an XmlString object for XML serialization.
     *
     * @return XmlString the provider number as XML string type, or null if not set
     */
    public XmlString xgetProviderNo() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AR1Impl.PROVIDERNO$8, 0);
            return target;
        }
    }

    /**
     * Sets the provider number identifying the healthcare practitioner.
     *
     * @param providerNo String the provider's unique identifier to set
     */
    public void setProviderNo(final String providerNo) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.PROVIDERNO$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.PROVIDERNO$8);
            }
            target.setStringValue(providerNo);
        }
    }

    /**
     * Sets the provider number using an XmlString object for XML deserialization.
     *
     * @param providerNo XmlString the provider number as XML string type
     */
    public void xsetProviderNo(final XmlString providerNo) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AR1Impl.PROVIDERNO$8, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(AR1Impl.PROVIDERNO$8);
            }
            target.set((XmlObject)providerNo);
        }
    }

    /**
     * Gets the date and time when this AR1 form was originally created.
     *
     * This timestamp records when the antenatal record was first initiated in the system,
     * providing an audit trail for form creation and supporting regulatory compliance
     * requirements for medical record keeping.
     *
     * @return Calendar the form creation timestamp, or null if not set
     */
    public Calendar getFormCreated() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.FORMCREATED$10, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Gets the form creation date as an XmlDate object for XML serialization.
     *
     * @return XmlDate the creation date as XML date type, or null if not set
     */
    public XmlDate xgetFormCreated() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(AR1Impl.FORMCREATED$10, 0);
            return target;
        }
    }

    /**
     * Sets the date and time when this AR1 form was originally created.
     *
     * @param formCreated Calendar the form creation timestamp to set
     */
    public void setFormCreated(final Calendar formCreated) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.FORMCREATED$10, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.FORMCREATED$10);
            }
            target.setCalendarValue(formCreated);
        }
    }

    /**
     * Sets the form creation date using an XmlDate object for XML deserialization.
     *
     * @param formCreated XmlDate the creation date as XML date type
     */
    public void xsetFormCreated(final XmlDate formCreated) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(AR1Impl.FORMCREATED$10, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(AR1Impl.FORMCREATED$10);
            }
            target.set((XmlObject)formCreated);
        }
    }

    /**
     * Gets the date and time of the most recent edit to this AR1 form.
     *
     * This timestamp is updated each time the antenatal record is modified, providing
     * an audit trail for form modifications and supporting regulatory compliance
     * requirements. It enables tracking of when clinical information was last updated.
     *
     * @return Calendar the last edit timestamp, or null if the form has never been edited
     */
    public Calendar getFormEdited() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.FORMEDITED$12, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Gets the last edit timestamp as an XmlDateTime object for XML serialization.
     *
     * @return XmlDateTime the edit timestamp as XML datetime type, or null if not set
     */
    public XmlDateTime xgetFormEdited() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDateTime target = null;
            target = (XmlDateTime)this.get_store().find_element_user(AR1Impl.FORMEDITED$12, 0);
            return target;
        }
    }

    /**
     * Sets the date and time of the most recent edit to this AR1 form.
     *
     * @param formEdited Calendar the last edit timestamp to set
     */
    public void setFormEdited(final Calendar formEdited) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.FORMEDITED$12, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.FORMEDITED$12);
            }
            target.setCalendarValue(formEdited);
        }
    }

    /**
     * Sets the last edit timestamp using an XmlDateTime object for XML deserialization.
     *
     * @param formEdited XmlDateTime the edit timestamp as XML datetime type
     */
    public void xsetFormEdited(final XmlDateTime formEdited) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDateTime target = null;
            target = (XmlDateTime)this.get_store().find_element_user(AR1Impl.FORMEDITED$12, 0);
            if (target == null) {
                target = (XmlDateTime)this.get_store().add_element_user(AR1Impl.FORMEDITED$12);
            }
            target.set((XmlObject)formEdited);
        }
    }

    /**
     * Gets the patient information section containing demographic and contact details.
     *
     * This section includes the pregnant patient's complete demographic information such as
     * name, date of birth, address, phone numbers, emergency contacts, and other identifying
     * information required for the antenatal record. This is Protected Health Information (PHI)
     * and must be handled in compliance with PIPEDA privacy regulations.
     *
     * @return PatientInformation the patient information section, or null if not set
     */
    public PatientInformation getPatientInformation() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PatientInformation target = null;
            target = (PatientInformation)this.get_store().find_element_user(AR1Impl.PATIENTINFORMATION$14, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the patient information section.
     *
     * @param patientInformation PatientInformation the patient information section to set
     */
    public void setPatientInformation(final PatientInformation patientInformation) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PatientInformation target = null;
            target = (PatientInformation)this.get_store().find_element_user(AR1Impl.PATIENTINFORMATION$14, 0);
            if (target == null) {
                target = (PatientInformation)this.get_store().add_element_user(AR1Impl.PATIENTINFORMATION$14);
            }
            target.set((XmlObject)patientInformation);
        }
    }

    /**
     * Creates and adds a new patient information section to this AR1 form.
     *
     * This method creates a new empty PatientInformation object, adds it to the XML document,
     * and returns it for population with patient demographic data. Use this method when
     * initially creating an AR1 form or when the patient information section needs to be replaced.
     *
     * @return PatientInformation the newly created patient information section
     */
    public PatientInformation addNewPatientInformation() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PatientInformation target = null;
            target = (PatientInformation)this.get_store().add_element_user(AR1Impl.PATIENTINFORMATION$14);
            return target;
        }
    }

    /**
     * Gets the partner information section containing details about the patient's partner.
     *
     * This section includes the partner's name, contact information, and relevant family history
     * for genetic counseling and support planning. Partner information is important for
     * identifying genetic risk factors and planning family-centered care during pregnancy.
     *
     * @return PartnerInformation the partner information section, or null if not set
     */
    public PartnerInformation getPartnerInformation() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PartnerInformation target = null;
            target = (PartnerInformation)this.get_store().find_element_user(AR1Impl.PARTNERINFORMATION$16, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the partner information section.
     *
     * @param partnerInformation PartnerInformation the partner information section to set
     */
    public void setPartnerInformation(final PartnerInformation partnerInformation) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PartnerInformation target = null;
            target = (PartnerInformation)this.get_store().find_element_user(AR1Impl.PARTNERINFORMATION$16, 0);
            if (target == null) {
                target = (PartnerInformation)this.get_store().add_element_user(AR1Impl.PARTNERINFORMATION$16);
            }
            target.set((XmlObject)partnerInformation);
        }
    }

    /**
     * Creates and adds a new partner information section to this AR1 form.
     *
     * This method creates a new empty PartnerInformation object and adds it to the form
     * for subsequent population with partner demographic and family history data.
     *
     * @return PartnerInformation the newly created partner information section
     */
    public PartnerInformation addNewPartnerInformation() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PartnerInformation target = null;
            target = (PartnerInformation)this.get_store().add_element_user(AR1Impl.PARTNERINFORMATION$16);
            return target;
        }
    }

    /**
     * Gets the practitioner information section containing healthcare provider details.
     *
     * This section includes information about the primary care provider, obstetrician,
     * and other healthcare professionals involved in the patient's antenatal care.
     * It supports care coordination by documenting all members of the prenatal care team.
     *
     * @return PractitionerInformation the practitioner information section, or null if not set
     */
    public PractitionerInformation getPractitionerInformation() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PractitionerInformation target = null;
            target = (PractitionerInformation)this.get_store().find_element_user(AR1Impl.PRACTITIONERINFORMATION$18, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the practitioner information section.
     *
     * @param practitionerInformation PractitionerInformation the practitioner information section to set
     */
    public void setPractitionerInformation(final PractitionerInformation practitionerInformation) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PractitionerInformation target = null;
            target = (PractitionerInformation)this.get_store().find_element_user(AR1Impl.PRACTITIONERINFORMATION$18, 0);
            if (target == null) {
                target = (PractitionerInformation)this.get_store().add_element_user(AR1Impl.PRACTITIONERINFORMATION$18);
            }
            target.set((XmlObject)practitionerInformation);
        }
    }

    /**
     * Creates and adds a new practitioner information section to this AR1 form.
     *
     * This method creates a new empty PractitionerInformation object for documenting
     * the healthcare providers involved in the patient's prenatal care.
     *
     * @return PractitionerInformation the newly created practitioner information section
     */
    public PractitionerInformation addNewPractitionerInformation() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PractitionerInformation target = null;
            target = (PractitionerInformation)this.get_store().add_element_user(AR1Impl.PRACTITIONERINFORMATION$18);
            return target;
        }
    }

    /**
     * Gets the pregnancy history section containing information about the current pregnancy.
     *
     * This section includes estimated due date, dating methods (LMP, ultrasound), pregnancy
     * symptoms, and current pregnancy progression details. It is essential for tracking
     * the timeline and development of the current pregnancy.
     *
     * @return PregnancyHistory the pregnancy history section, or null if not set
     */
    public PregnancyHistory getPregnancyHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PregnancyHistory target = null;
            target = (PregnancyHistory)this.get_store().find_element_user(AR1Impl.PREGNANCYHISTORY$20, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the pregnancy history section.
     *
     * @param pregnancyHistory PregnancyHistory the pregnancy history section to set
     */
    public void setPregnancyHistory(final PregnancyHistory pregnancyHistory) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PregnancyHistory target = null;
            target = (PregnancyHistory)this.get_store().find_element_user(AR1Impl.PREGNANCYHISTORY$20, 0);
            if (target == null) {
                target = (PregnancyHistory)this.get_store().add_element_user(AR1Impl.PREGNANCYHISTORY$20);
            }
            target.set((XmlObject)pregnancyHistory);
        }
    }

    /**
     * Creates and adds a new pregnancy history section to this AR1 form.
     *
     * This method creates a new empty PregnancyHistory object for documenting the
     * current pregnancy timeline, symptoms, and progression.
     *
     * @return PregnancyHistory the newly created pregnancy history section
     */
    public PregnancyHistory addNewPregnancyHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            PregnancyHistory target = null;
            target = (PregnancyHistory)this.get_store().add_element_user(AR1Impl.PREGNANCYHISTORY$20);
            return target;
        }
    }

    /**
     * Gets the obstetrical history section containing past pregnancy and delivery records.
     *
     * This section includes gravida/para status, previous pregnancy outcomes, complications,
     * delivery methods, birth weights, and other historical obstetrical information. This
     * data is critical for risk assessment and planning appropriate care for the current pregnancy.
     *
     * @return ObstetricalHistory the obstetrical history section, or null if not set
     */
    public ObstetricalHistory getObstetricalHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ObstetricalHistory target = null;
            target = (ObstetricalHistory)this.get_store().find_element_user(AR1Impl.OBSTETRICALHISTORY$22, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the obstetrical history section.
     *
     * @param obstetricalHistory ObstetricalHistory the obstetrical history section to set
     */
    public void setObstetricalHistory(final ObstetricalHistory obstetricalHistory) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ObstetricalHistory target = null;
            target = (ObstetricalHistory)this.get_store().find_element_user(AR1Impl.OBSTETRICALHISTORY$22, 0);
            if (target == null) {
                target = (ObstetricalHistory)this.get_store().add_element_user(AR1Impl.OBSTETRICALHISTORY$22);
            }
            target.set((XmlObject)obstetricalHistory);
        }
    }

    /**
     * Creates and adds a new obstetrical history section to this AR1 form.
     *
     * This method creates a new empty ObstetricalHistory object for documenting
     * previous pregnancies, deliveries, and obstetrical outcomes.
     *
     * @return ObstetricalHistory the newly created obstetrical history section
     */
    public ObstetricalHistory addNewObstetricalHistory() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ObstetricalHistory target = null;
            target = (ObstetricalHistory)this.get_store().add_element_user(AR1Impl.OBSTETRICALHISTORY$22);
            return target;
        }
    }

    /**
     * Gets the medical history and physical examination section.
     *
     * This section includes comprehensive medical history, current medications, allergies,
     * family history of genetic conditions, physical examination findings, and baseline
     * vital signs. This comprehensive medical assessment forms the foundation for
     * pregnancy care planning and risk stratification.
     *
     * @return MedicalHistoryAndPhysicalExam the medical history and physical exam section, or null if not set
     */
    public MedicalHistoryAndPhysicalExam getMedicalHistoryAndPhysicalExam() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            MedicalHistoryAndPhysicalExam target = null;
            target = (MedicalHistoryAndPhysicalExam)this.get_store().find_element_user(AR1Impl.MEDICALHISTORYANDPHYSICALEXAM$24, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the medical history and physical examination section.
     *
     * @param medicalHistoryAndPhysicalExam MedicalHistoryAndPhysicalExam the medical history and physical exam section to set
     */
    public void setMedicalHistoryAndPhysicalExam(final MedicalHistoryAndPhysicalExam medicalHistoryAndPhysicalExam) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            MedicalHistoryAndPhysicalExam target = null;
            target = (MedicalHistoryAndPhysicalExam)this.get_store().find_element_user(AR1Impl.MEDICALHISTORYANDPHYSICALEXAM$24, 0);
            if (target == null) {
                target = (MedicalHistoryAndPhysicalExam)this.get_store().add_element_user(AR1Impl.MEDICALHISTORYANDPHYSICALEXAM$24);
            }
            target.set((XmlObject)medicalHistoryAndPhysicalExam);
        }
    }

    /**
     * Creates and adds a new medical history and physical examination section to this AR1 form.
     *
     * This method creates a new empty MedicalHistoryAndPhysicalExam object for documenting
     * the patient's medical background and baseline physical assessment.
     *
     * @return MedicalHistoryAndPhysicalExam the newly created medical history and physical exam section
     */
    public MedicalHistoryAndPhysicalExam addNewMedicalHistoryAndPhysicalExam() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            MedicalHistoryAndPhysicalExam target = null;
            target = (MedicalHistoryAndPhysicalExam)this.get_store().add_element_user(AR1Impl.MEDICALHISTORYANDPHYSICALEXAM$24);
            return target;
        }
    }

    /**
     * Gets the initial laboratory investigations section containing baseline test results.
     *
     * This section includes blood type, Rh factor, antibody screens, infectious disease
     * screening (rubella, hepatitis, HIV, syphilis), genetic testing results, and other
     * routine prenatal laboratory investigations. These baseline tests are essential for
     * identifying risk factors and planning appropriate prenatal care.
     *
     * @return InitialLaboratoryInvestigations the initial laboratory investigations section, or null if not set
     */
    public InitialLaboratoryInvestigations getInitialLaboratoryInvestigations() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            InitialLaboratoryInvestigations target = null;
            target = (InitialLaboratoryInvestigations)this.get_store().find_element_user(AR1Impl.INITIALLABORATORYINVESTIGATIONS$26, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the initial laboratory investigations section.
     *
     * @param initialLaboratoryInvestigations InitialLaboratoryInvestigations the initial laboratory investigations section to set
     */
    public void setInitialLaboratoryInvestigations(final InitialLaboratoryInvestigations initialLaboratoryInvestigations) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            InitialLaboratoryInvestigations target = null;
            target = (InitialLaboratoryInvestigations)this.get_store().find_element_user(AR1Impl.INITIALLABORATORYINVESTIGATIONS$26, 0);
            if (target == null) {
                target = (InitialLaboratoryInvestigations)this.get_store().add_element_user(AR1Impl.INITIALLABORATORYINVESTIGATIONS$26);
            }
            target.set((XmlObject)initialLaboratoryInvestigations);
        }
    }

    /**
     * Creates and adds a new initial laboratory investigations section to this AR1 form.
     *
     * This method creates a new empty InitialLaboratoryInvestigations object for documenting
     * baseline prenatal laboratory test results.
     *
     * @return InitialLaboratoryInvestigations the newly created initial laboratory investigations section
     */
    public InitialLaboratoryInvestigations addNewInitialLaboratoryInvestigations() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            InitialLaboratoryInvestigations target = null;
            target = (InitialLaboratoryInvestigations)this.get_store().add_element_user(AR1Impl.INITIALLABORATORYINVESTIGATIONS$26);
            return target;
        }
    }

    /**
     * Gets the primary comments field for clinical notes and observations.
     *
     * This field is used for documenting additional clinical information, care plans,
     * provider observations, and other notes relevant to the patient's antenatal care.
     * It provides a free-text area for healthcare providers to record information that
     * doesn't fit into the structured sections of the form.
     *
     * @return String the clinical comments text, or null if not set
     */
    public String getComments() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.COMMENTS$28, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the comments as an XmlString object for XML serialization.
     *
     * @return XmlString the comments as XML string type, or null if not set
     */
    public XmlString xgetComments() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AR1Impl.COMMENTS$28, 0);
            return target;
        }
    }

    /**
     * Sets the primary comments field for clinical notes.
     *
     * @param comments String the clinical comments text to set
     */
    public void setComments(final String comments) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.COMMENTS$28, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.COMMENTS$28);
            }
            target.setStringValue(comments);
        }
    }

    /**
     * Sets the comments using an XmlString object for XML deserialization.
     *
     * @param comments XmlString the comments as XML string type
     */
    public void xsetComments(final XmlString comments) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AR1Impl.COMMENTS$28, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(AR1Impl.COMMENTS$28);
            }
            target.set((XmlObject)comments);
        }
    }

    /**
     * Gets the extra comments field for supplementary clinical documentation.
     *
     * This field provides additional space for extended notes beyond the primary comments
     * section when more detailed documentation is required. It can be used for documenting
     * complex clinical situations, detailed care plans, or additional observations that
     * require more space than the primary comments field provides.
     *
     * @return String the extra comments text, or null if not set
     */
    public String getExtraComments() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.EXTRACOMMENTS$30, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the extra comments as an XmlString object for XML serialization.
     *
     * @return XmlString the extra comments as XML string type, or null if not set
     */
    public XmlString xgetExtraComments() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AR1Impl.EXTRACOMMENTS$30, 0);
            return target;
        }
    }

    /**
     * Sets the extra comments field for supplementary documentation.
     *
     * @param extraComments String the extra comments text to set
     */
    public void setExtraComments(final String extraComments) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(AR1Impl.EXTRACOMMENTS$30, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(AR1Impl.EXTRACOMMENTS$30);
            }
            target.setStringValue(extraComments);
        }
    }

    /**
     * Sets the extra comments using an XmlString object for XML deserialization.
     *
     * @param extraComments XmlString the extra comments as XML string type
     */
    public void xsetExtraComments(final XmlString extraComments) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(AR1Impl.EXTRACOMMENTS$30, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(AR1Impl.EXTRACOMMENTS$30);
            }
            target.set((XmlObject)extraComments);
        }
    }

    /**
     * Gets the signatures section containing provider signatures for regulatory compliance.
     *
     * This section includes electronic or digital signatures from healthcare providers who
     * reviewed and approved the antenatal record. Provider signatures are required for
     * regulatory compliance and provide an audit trail of who created and reviewed the
     * clinical documentation. Signatures verify the authenticity and accuracy of the
     * documented information.
     *
     * @return SignatureType the signatures section, or null if not set
     */
    public SignatureType getSignatures() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SignatureType target = null;
            target = (SignatureType)this.get_store().find_element_user(AR1Impl.SIGNATURES$32, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the signatures section.
     *
     * @param signatures SignatureType the signatures section to set
     */
    public void setSignatures(final SignatureType signatures) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SignatureType target = null;
            target = (SignatureType)this.get_store().find_element_user(AR1Impl.SIGNATURES$32, 0);
            if (target == null) {
                target = (SignatureType)this.get_store().add_element_user(AR1Impl.SIGNATURES$32);
            }
            target.set((XmlObject)signatures);
        }
    }

    /**
     * Creates and adds a new signatures section to this AR1 form.
     *
     * This method creates a new empty SignatureType object for collecting provider
     * signatures and approvals for the antenatal record.
     *
     * @return SignatureType the newly created signatures section
     */
    public SignatureType addNewSignatures() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SignatureType target = null;
            target = (SignatureType)this.get_store().add_element_user(AR1Impl.SIGNATURES$32);
            return target;
        }
    }

    static {
        ID$0 = new QName("http://www.oscarmcmaster.org/AR2005", "id");
        VERSIONID$2 = new QName("http://www.oscarmcmaster.org/AR2005", "VersionID");
        EPISODEID$4 = new QName("http://www.oscarmcmaster.org/AR2005", "episodeId");
        DEMOGRAPHICNO$6 = new QName("http://www.oscarmcmaster.org/AR2005", "demographicNo");
        PROVIDERNO$8 = new QName("http://www.oscarmcmaster.org/AR2005", "providerNo");
        FORMCREATED$10 = new QName("http://www.oscarmcmaster.org/AR2005", "formCreated");
        FORMEDITED$12 = new QName("http://www.oscarmcmaster.org/AR2005", "formEdited");
        PATIENTINFORMATION$14 = new QName("http://www.oscarmcmaster.org/AR2005", "patientInformation");
        PARTNERINFORMATION$16 = new QName("http://www.oscarmcmaster.org/AR2005", "partnerInformation");
        PRACTITIONERINFORMATION$18 = new QName("http://www.oscarmcmaster.org/AR2005", "practitionerInformation");
        PREGNANCYHISTORY$20 = new QName("http://www.oscarmcmaster.org/AR2005", "pregnancyHistory");
        OBSTETRICALHISTORY$22 = new QName("http://www.oscarmcmaster.org/AR2005", "obstetricalHistory");
        MEDICALHISTORYANDPHYSICALEXAM$24 = new QName("http://www.oscarmcmaster.org/AR2005", "medicalHistoryAndPhysicalExam");
        INITIALLABORATORYINVESTIGATIONS$26 = new QName("http://www.oscarmcmaster.org/AR2005", "initialLaboratoryInvestigations");
        COMMENTS$28 = new QName("http://www.oscarmcmaster.org/AR2005", "comments");
        EXTRACOMMENTS$30 = new QName("http://www.oscarmcmaster.org/AR2005", "extraComments");
        SIGNATURES$32 = new QName("http://www.oscarmcmaster.org/AR2005", "signatures");
    }
}
