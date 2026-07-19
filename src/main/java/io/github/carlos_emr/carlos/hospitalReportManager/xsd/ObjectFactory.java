package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.Address;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.AddressStructured;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.BloodPressure;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.Code;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DateFullOrPartial;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.Demographics;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DiabetesComplicationScreening;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DiabetesEducationalSelfManagement;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DiabetesMotivationalCounselling;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DiabetesSelfManagementChallenges;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DiabetesSelfManagementCollaborative;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DrugMeasure;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.EnrollmentInfo;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.HealthCard;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.Height;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.HypoglycemicEpisodes;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.OmdCds;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PatientRecord;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimple;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimpleWithMiddleName;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameStandard;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PhoneNumber;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PostalZipCode;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportContent;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportsReceived;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ResidualInformation;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.SelfMonitoringBloodGlucose;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.SmokingPacks;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.SmokingStatus;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.TransactionInformation;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.WaistCircumference;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.Weight;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.YnIndicator;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.YnIndicatorAndBlank;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlRegistry
public class ObjectFactory {
    private static final QName _PhoneNumberNumber_QNAME = new QName("cds_dt", "number");
    private static final QName _PhoneNumberExchange_QNAME = new QName("cds_dt", "exchange");
    private static final QName _PhoneNumberExtension_QNAME = new QName("cds_dt", "extension");
    private static final QName _PhoneNumberPhoneNumber_QNAME = new QName("cds_dt", "phoneNumber");
    private static final QName _PhoneNumberAreaCode_QNAME = new QName("cds_dt", "areaCode");

    public PatientRecord createPatientRecord() {
        return new PatientRecord();
    }

    public SelfMonitoringBloodGlucose createSelfMonitoringBloodGlucose() {
        return new SelfMonitoringBloodGlucose();
    }

    public PersonNameSimpleWithMiddleName createPersonNameSimpleWithMiddleName() {
        return new PersonNameSimpleWithMiddleName();
    }

    public ReportsReceived.OBRContent createReportsReceivedOBRContent() {
        return new ReportsReceived.OBRContent();
    }

    public DiabetesComplicationScreening createDiabetesComplicationScreening() {
        return new DiabetesComplicationScreening();
    }

    public Weight createWeight() {
        return new Weight();
    }

    public PersonNameStandard.LegalName.FirstName createPersonNameStandardLegalNameFirstName() {
        return new PersonNameStandard.LegalName.FirstName();
    }

    public Demographics.PrimaryPhysician createDemographicsPrimaryPhysician() {
        return new Demographics.PrimaryPhysician();
    }

    public PersonNameStandard.OtherNames.OtherName createPersonNameStandardOtherNamesOtherName() {
        return new PersonNameStandard.OtherNames.OtherName();
    }

    public DrugMeasure createDrugMeasure() {
        return new DrugMeasure();
    }

    public PersonNameStandard createPersonNameStandard() {
        return new PersonNameStandard();
    }

    public Demographics createDemographics() {
        return new Demographics();
    }

    public PostalZipCode createPostalZipCode() {
        return new PostalZipCode();
    }

    public ResidualInformation createResidualInformation() {
        return new ResidualInformation();
    }

    public ReportsReceived createReportsReceived() {
        return new ReportsReceived();
    }

    public Address createAddress() {
        return new Address();
    }

    public DiabetesMotivationalCounselling createDiabetesMotivationalCounselling() {
        return new DiabetesMotivationalCounselling();
    }

    public Height createHeight() {
        return new Height();
    }

    public DateFullOrPartial createDateFullOrPartial() {
        return new DateFullOrPartial();
    }

    public EnrollmentInfo createEnrollmentInfo() {
        return new EnrollmentInfo();
    }

    public HypoglycemicEpisodes createHypoglycemicEpisodes() {
        return new HypoglycemicEpisodes();
    }

    public SmokingStatus createSmokingStatus() {
        return new SmokingStatus();
    }

    public AddressStructured createAddressStructured() {
        return new AddressStructured();
    }

    public TransactionInformation createTransactionInformation() {
        return new TransactionInformation();
    }

    public PersonNameStandard.OtherNames createPersonNameStandardOtherNames() {
        return new PersonNameStandard.OtherNames();
    }

    public YnIndicatorAndBlank createYnIndicatorAndBlank() {
        return new YnIndicatorAndBlank();
    }

    public PersonNameStandard.LegalName createPersonNameStandardLegalName() {
        return new PersonNameStandard.LegalName();
    }

    public PersonNameSimple createPersonNameSimple() {
        return new PersonNameSimple();
    }

    public Code createCode() {
        return new Code();
    }

    public PersonNameStandard.LegalName.OtherName createPersonNameStandardLegalNameOtherName() {
        return new PersonNameStandard.LegalName.OtherName();
    }

    public DiabetesEducationalSelfManagement createDiabetesEducationalSelfManagement() {
        return new DiabetesEducationalSelfManagement();
    }

    public PhoneNumber createPhoneNumber() {
        return new PhoneNumber();
    }

    public OmdCds createOmdCds() {
        return new OmdCds();
    }

    public YnIndicator createYnIndicator() {
        return new YnIndicator();
    }

    public PersonNameStandard.LegalName.LastName createPersonNameStandardLegalNameLastName() {
        return new PersonNameStandard.LegalName.LastName();
    }

    public ReportContent createReportContent() {
        return new ReportContent();
    }

    public DiabetesSelfManagementCollaborative createDiabetesSelfManagementCollaborative() {
        return new DiabetesSelfManagementCollaborative();
    }

    public Demographics.Contact createDemographicsContact() {
        return new Demographics.Contact();
    }

    public DiabetesSelfManagementChallenges createDiabetesSelfManagementChallenges() {
        return new DiabetesSelfManagementChallenges();
    }

    public BloodPressure createBloodPressure() {
        return new BloodPressure();
    }

    public WaistCircumference createWaistCircumference() {
        return new WaistCircumference();
    }

    public ResidualInformation.DataElement createResidualInformationDataElement() {
        return new ResidualInformation.DataElement();
    }

    public HealthCard createHealthCard() {
        return new HealthCard();
    }

    public SmokingPacks createSmokingPacks() {
        return new SmokingPacks();
    }

    @XmlElementDecl(namespace="cds_dt", name="number", scope=PhoneNumber.class)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    public JAXBElement<String> createPhoneNumberNumber(String value) {
        return new JAXBElement(_PhoneNumberNumber_QNAME, String.class, PhoneNumber.class, (Object)value);
    }

    @XmlElementDecl(namespace="cds_dt", name="exchange", scope=PhoneNumber.class)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    public JAXBElement<String> createPhoneNumberExchange(String value) {
        return new JAXBElement(_PhoneNumberExchange_QNAME, String.class, PhoneNumber.class, (Object)value);
    }

    @XmlElementDecl(namespace="cds_dt", name="extension", scope=PhoneNumber.class)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    public JAXBElement<String> createPhoneNumberExtension(String value) {
        return new JAXBElement(_PhoneNumberExtension_QNAME, String.class, PhoneNumber.class, (Object)value);
    }

    @XmlElementDecl(namespace="cds_dt", name="phoneNumber", scope=PhoneNumber.class)
    public JAXBElement<String> createPhoneNumberPhoneNumber(String value) {
        return new JAXBElement(_PhoneNumberPhoneNumber_QNAME, String.class, PhoneNumber.class, (Object)value);
    }

    @XmlElementDecl(namespace="cds_dt", name="areaCode", scope=PhoneNumber.class)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    public JAXBElement<String> createPhoneNumberAreaCode(String value) {
        return new JAXBElement(_PhoneNumberAreaCode_QNAME, String.class, PhoneNumber.class, (Object)value);
    }
}
