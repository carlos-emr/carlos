package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.Address;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ContactPersonPurpose;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DateFullOrPartial;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.Gender;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.HealthCard;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.OfficialSpokenLanguageCode;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimple;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimpleWithMiddleName;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameStandard;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonStatus;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PhoneNumber;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PhoneNumberType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="", propOrder={"names", "dateOfBirth", "healthCard", "chartNumber", "gender", "uniqueVendorIdSequence", "address", "phoneNumber", "preferredPhone", "preferredOfficialLanguage", "preferredSpokenLanguage", "contact", "noteAboutPatient", "patientWarningFlags", "enrollmentStatus", "enrollmentDate", "enrollmentTerminationDate", "terminationReason", "primaryPhysician", "email", "familyMemberLink", "personStatusCode", "personStatusDate", "sin"})
@XmlRootElement(name="Demographics", namespace="cds")
public class Demographics {
    @XmlElement(name="Names", namespace="cds", required=true)
    protected PersonNameStandard names;
    @XmlElement(name="DateOfBirth", namespace="cds", required=true)
    protected DateFullOrPartial dateOfBirth;
    @XmlElement(name="HealthCard", namespace="cds")
    protected HealthCard healthCard;
    @XmlElement(name="ChartNumber", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String chartNumber;
    @XmlElement(name="Gender", namespace="cds", required=true)
    protected Gender gender;
    @XmlElement(name="UniqueVendorIdSequence", namespace="cds", required=true)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String uniqueVendorIdSequence;
    @XmlElement(name="Address", namespace="cds")
    protected List<Address> address;
    @XmlElement(name="PhoneNumber", namespace="cds")
    protected List<PhoneNumber> phoneNumber;
    @XmlElement(name="PreferredPhone", namespace="cds")
    protected PhoneNumberType preferredPhone;
    @XmlElement(name="PreferredOfficialLanguage", namespace="cds")
    protected OfficialSpokenLanguageCode preferredOfficialLanguage;
    @XmlElement(name="PreferredSpokenLanguage", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String preferredSpokenLanguage;
    @XmlElement(name="Contact", namespace="cds")
    protected List<Contact> contact;
    @XmlElement(name="NoteAboutPatient", namespace="cds")
    protected String noteAboutPatient;
    @XmlElement(name="PatientWarningFlags", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String patientWarningFlags;
    @XmlElement(name="EnrollmentStatus", namespace="cds")
    protected String enrollmentStatus;
    @XmlElement(name="EnrollmentDate", namespace="cds")
    protected DateFullOrPartial enrollmentDate;
    @XmlElement(name="EnrollmentTerminationDate", namespace="cds")
    protected DateFullOrPartial enrollmentTerminationDate;
    @XmlElement(name="TerminationReason", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String terminationReason;
    @XmlElement(name="PrimaryPhysician", namespace="cds")
    protected PrimaryPhysician primaryPhysician;
    @XmlElement(name="Email", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String email;
    @XmlElement(name="FamilyMemberLink", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String familyMemberLink;
    @XmlElement(name="PersonStatusCode", namespace="cds", required=true)
    protected PersonStatus personStatusCode;
    @XmlElement(name="PersonStatusDate", namespace="cds")
    protected DateFullOrPartial personStatusDate;
    @XmlElement(name="SIN", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String sin;

    public PersonNameStandard getNames() {
        return this.names;
    }

    public void setNames(PersonNameStandard value) {
        this.names = value;
    }

    public DateFullOrPartial getDateOfBirth() {
        return this.dateOfBirth;
    }

    public void setDateOfBirth(DateFullOrPartial value) {
        this.dateOfBirth = value;
    }

    public HealthCard getHealthCard() {
        return this.healthCard;
    }

    public void setHealthCard(HealthCard value) {
        this.healthCard = value;
    }

    public String getChartNumber() {
        return this.chartNumber;
    }

    public void setChartNumber(String value) {
        this.chartNumber = value;
    }

    public Gender getGender() {
        return this.gender;
    }

    public void setGender(Gender value) {
        this.gender = value;
    }

    public String getUniqueVendorIdSequence() {
        return this.uniqueVendorIdSequence;
    }

    public void setUniqueVendorIdSequence(String value) {
        this.uniqueVendorIdSequence = value;
    }

    public List<Address> getAddress() {
        if (this.address == null) {
            this.address = new ArrayList<Address>();
        }
        return this.address;
    }

    public List<PhoneNumber> getPhoneNumber() {
        if (this.phoneNumber == null) {
            this.phoneNumber = new ArrayList<PhoneNumber>();
        }
        return this.phoneNumber;
    }

    public PhoneNumberType getPreferredPhone() {
        return this.preferredPhone;
    }

    public void setPreferredPhone(PhoneNumberType value) {
        this.preferredPhone = value;
    }

    public OfficialSpokenLanguageCode getPreferredOfficialLanguage() {
        return this.preferredOfficialLanguage;
    }

    public void setPreferredOfficialLanguage(OfficialSpokenLanguageCode value) {
        this.preferredOfficialLanguage = value;
    }

    public String getPreferredSpokenLanguage() {
        return this.preferredSpokenLanguage;
    }

    public void setPreferredSpokenLanguage(String value) {
        this.preferredSpokenLanguage = value;
    }

    public List<Contact> getContact() {
        if (this.contact == null) {
            this.contact = new ArrayList<Contact>();
        }
        return this.contact;
    }

    public String getNoteAboutPatient() {
        return this.noteAboutPatient;
    }

    public void setNoteAboutPatient(String value) {
        this.noteAboutPatient = value;
    }

    public String getPatientWarningFlags() {
        return this.patientWarningFlags;
    }

    public void setPatientWarningFlags(String value) {
        this.patientWarningFlags = value;
    }

    public String getEnrollmentStatus() {
        return this.enrollmentStatus;
    }

    public void setEnrollmentStatus(String value) {
        this.enrollmentStatus = value;
    }

    public DateFullOrPartial getEnrollmentDate() {
        return this.enrollmentDate;
    }

    public void setEnrollmentDate(DateFullOrPartial value) {
        this.enrollmentDate = value;
    }

    public DateFullOrPartial getEnrollmentTerminationDate() {
        return this.enrollmentTerminationDate;
    }

    public void setEnrollmentTerminationDate(DateFullOrPartial value) {
        this.enrollmentTerminationDate = value;
    }

    public String getTerminationReason() {
        return this.terminationReason;
    }

    public void setTerminationReason(String value) {
        this.terminationReason = value;
    }

    public PrimaryPhysician getPrimaryPhysician() {
        return this.primaryPhysician;
    }

    public void setPrimaryPhysician(PrimaryPhysician value) {
        this.primaryPhysician = value;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String value) {
        this.email = value;
    }

    public String getFamilyMemberLink() {
        return this.familyMemberLink;
    }

    public void setFamilyMemberLink(String value) {
        this.familyMemberLink = value;
    }

    public PersonStatus getPersonStatusCode() {
        return this.personStatusCode;
    }

    public void setPersonStatusCode(PersonStatus value) {
        this.personStatusCode = value;
    }

    public DateFullOrPartial getPersonStatusDate() {
        return this.personStatusDate;
    }

    public void setPersonStatusDate(DateFullOrPartial value) {
        this.personStatusDate = value;
    }

    public String getSIN() {
        return this.sin;
    }

    public void setSIN(String value) {
        this.sin = value;
    }

    @XmlAccessorType(value=XmlAccessType.FIELD)
    @XmlType(name="", propOrder={})
    public static class PrimaryPhysician {
        @XmlElement(name="Name", namespace="cds")
        protected PersonNameSimple name;
        @XmlElement(name="OHIPPhysicianId", namespace="cds")
        @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
        protected String ohipPhysicianId;

        public PersonNameSimple getName() {
            return this.name;
        }

        public void setName(PersonNameSimple value) {
            this.name = value;
        }

        public String getOHIPPhysicianId() {
            return this.ohipPhysicianId;
        }

        public void setOHIPPhysicianId(String value) {
            this.ohipPhysicianId = value;
        }
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    @XmlAccessorType(value=XmlAccessType.FIELD)
    @XmlType(name="", propOrder={"name", "phoneNumber", "emailAddress", "note"})
    public static class Contact {
        @XmlElement(name="Name", namespace="cds", required=true)
        protected PersonNameSimpleWithMiddleName name;
        @XmlElement(name="PhoneNumber", namespace="cds")
        protected List<PhoneNumber> phoneNumber;
        @XmlElement(name="EmailAddress", namespace="cds")
        @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
        protected String emailAddress;
        @XmlElement(name="Note", namespace="cds")
        protected String note;
        @XmlAttribute(name="ContactPurpose", namespace="cds", required=true)
        protected ContactPersonPurpose contactPurpose;

        public PersonNameSimpleWithMiddleName getName() {
            return this.name;
        }

        public void setName(PersonNameSimpleWithMiddleName value) {
            this.name = value;
        }

        public List<PhoneNumber> getPhoneNumber() {
            if (this.phoneNumber == null) {
                this.phoneNumber = new ArrayList<PhoneNumber>();
            }
            return this.phoneNumber;
        }

        public String getEmailAddress() {
            return this.emailAddress;
        }

        public void setEmailAddress(String value) {
            this.emailAddress = value;
        }

        public String getNote() {
            return this.note;
        }

        public void setNote(String value) {
            this.note = value;
        }

        public ContactPersonPurpose getContactPurpose() {
            return this.contactPurpose;
        }

        public void setContactPurpose(ContactPersonPurpose value) {
            this.contactPurpose = value;
        }
    }
}
