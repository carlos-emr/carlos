/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.core;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.encounter.data.EctFormData.PatientForm;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.utility.DocumentIdCalculator;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("pdf")
@DisplayName("EmailDocumentManifestComposer")
class EmailDocumentManifestComposerUnitTest {

    /**
     * Base64 of the bytes 1 to 32: a FAKE secret for these tests only. Built here rather than
     * written out, so secret scanners do not mistake a test value for a real one.
     */
    private static final String FAKE_SECRET = Base64.getEncoder().encodeToString(bytesOneToThirtyTwo());
    private static final Instant NOW = Instant.parse("2026-10-21T17:00:00Z");

    private final Object originalSecret = CarlosProperties.getInstance().get(DocumentIdCalculator.SECRET_PROPERTY);
    private final ClinicDAO clinicDao = mock(ClinicDAO.class);
    private final EFormDataDao eFormDataDao = mock(EFormDataDao.class);
    private final DocumentDao documentDao = mock(DocumentDao.class);
    private final Hl7TextInfoDao hl7TextInfoDao = mock(Hl7TextInfoDao.class);
    private final HRMDocumentDao hrmDocumentDao = mock(HRMDocumentDao.class);
    private final FormsManager formsManager = mock(FormsManager.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private final EmailDocumentManifestComposer composer = new EmailDocumentManifestComposer(clinicDao, eFormDataDao,
            documentDao, hl7TextInfoDao, hrmDocumentDao, formsManager, Clock.fixed(NOW, ZoneOffset.UTC));
    private MockedStatic<PatientPortalSettings> portal;

    @BeforeEach
    void setUp() {
        CarlosProperties.getInstance().setProperty(DocumentIdCalculator.SECRET_PROPERTY, FAKE_SECRET);
        Clinic clinic = new Clinic();
        clinic.setClinicName("FAKE Demo Clinic");
        when(clinicDao.getClinic()).thenReturn(clinic);
        portal = mockStatic(PatientPortalSettings.class);
        portal.when(PatientPortalSettings::isConfigured).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        portal.close();
        if (originalSecret == null) {
            CarlosProperties.getInstance().remove(DocumentIdCalculator.SECRET_PROPERTY);
        } else {
            CarlosProperties.getInstance().put(DocumentIdCalculator.SECRET_PROPERTY, originalSecret);
        }
    }

    @Test
    @DisplayName("should build no manifests when the document ID secret is not set")
    void shouldBuildNoManifests_whenTheSecretIsNotSet() {
        CarlosProperties.getInstance().remove(DocumentIdCalculator.SECRET_PROPERTY);

        EmailDocumentManifestComposer.AttachmentManifests manifests = composer.forSend(loggedInInfo, fakeEmailLog());

        assertThat(manifests.forAttachment(attachment(DocumentType.EFORM, 42))).isNull();
        verifyNoInteractions(clinicDao, eFormDataDao);
    }

    @Test
    @DisplayName("should stop the send when the document ID secret is invalid")
    void shouldStopTheSend_whenTheSecretIsInvalid() {
        CarlosProperties.getInstance().setProperty(DocumentIdCalculator.SECRET_PROPERTY, "not base64 !!");

        assertThatThrownBy(() -> composer.forSend(loggedInInfo, fakeEmailLog()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should build an eForm's manifest from the send and the eForm record")
    void shouldBuildTheEFormManifest_fromTheSendAndItsRecord() {
        EFormData eForm = new EFormData();
        eForm.setSubject(" ");
        eForm.setFormName("FAKE Intake Form");
        eForm.setFormDate(localDate(2026, 10, 1));
        when(eFormDataDao.find(42)).thenReturn(eForm);

        DocumentManifest manifest = composer.forSend(loggedInInfo, fakeEmailLog())
                .forAttachment(attachment(DocumentType.EFORM, 42));

        // The known answer of DocumentIdCalculatorUnitTest: the ID hashes "eform:42".
        assertThat(manifest).isEqualTo(new DocumentManifest("NQP6VEqbRs3qDiu8FfzrbHJT80WWArgyZP7gAbGYPFw", 1,
                NOW.getEpochSecond(), null, "FAKE Demo Clinic", "FAKE-Provider, Demo", null,
                "FAKE-Manifest-Patient, Jo", "1985-06-15", "FAKE Intake Form", "eform", "2026-10-01"));
    }

    @Test
    @DisplayName("should take a document's description and observation date")
    void shouldTakeTitleAndDate_fromTheDocumentRecord() {
        Document document = new Document();
        document.setDocdesc("FAKE Referral Letter");
        // Hibernate returns java.sql.Date here, whose toInstant() throws.
        document.setObservationdate(java.sql.Date.valueOf(LocalDate.of(2026, 9, 29)));
        when(documentDao.find(8)).thenReturn(document);

        DocumentManifest manifest = composer.forSend(loggedInInfo, fakeEmailLog())
                .forAttachment(attachment(DocumentType.DOC, 8));

        assertThat(manifest.documentTitle()).isEqualTo("FAKE Referral Letter");
        assertThat(manifest.serviceDate()).isEqualTo("2026-09-29");
        assertThat(manifest.documentType()).isEqualTo("document");
    }

    @Test
    @DisplayName("should take a lab's discipline when it has no label, and the date of its HL7 time")
    void shouldTakeTitleAndDate_fromTheLabRecord() {
        Hl7TextInfo lab = new Hl7TextInfo();
        lab.setDiscipline("FAKE HEMATOLOGY");
        lab.setObrDate("2026-09-30 08:15:00");
        when(hl7TextInfoDao.findLabId(5)).thenReturn(lab);

        DocumentManifest manifest = composer.forSend(loggedInInfo, fakeEmailLog())
                .forAttachment(attachment(DocumentType.LAB, 5));

        assertThat(manifest.documentTitle()).isEqualTo("FAKE HEMATOLOGY");
        assertThat(manifest.serviceDate()).isEqualTo("2026-09-30");
        assertThat(manifest.documentType()).isEqualTo("lab_report");
    }

    @Test
    @DisplayName("should take a hospital report's type when it has no description, and its report date")
    void shouldTakeTitleAndDate_fromTheHospitalReport() {
        HRMDocument report = new HRMDocument();
        report.setDescription("");
        report.setReportType("FAKE Discharge Summary");
        report.setReportDate(localDate(2026, 9, 28));
        when(hrmDocumentDao.find(3)).thenReturn(report);

        DocumentManifest manifest = composer.forSend(loggedInInfo, fakeEmailLog())
                .forAttachment(attachment(DocumentType.HRM, 3));

        assertThat(manifest.documentTitle()).isEqualTo("FAKE Discharge Summary");
        assertThat(manifest.serviceDate()).isEqualTo("2026-09-28");
        assertThat(manifest.documentType()).isEqualTo("hospital_report");
    }

    @Test
    @DisplayName("should take a form's name and leave its service date empty")
    void shouldTakeTheFormName_withoutAServiceDate() {
        when(formsManager.getFormById(loggedInInfo, 7, 1001)).thenReturn(new PatientForm("formAnnual", "Annual", 7, 1001));

        DocumentManifest manifest = composer.forSend(loggedInInfo, fakeEmailLog())
                .forAttachment(attachment(DocumentType.FORM, 7));

        assertThat(manifest.documentTitle()).isEqualTo("Annual");
        assertThat(manifest.serviceDate()).isNull();
        assertThat(manifest.documentType()).isEqualTo("form");
    }

    @Test
    @DisplayName("should give the rendered message text no manifest")
    void shouldGiveNoManifest_toAnAttachmentWithoutARecord() {
        EmailDocumentManifestComposer.AttachmentManifests manifests = composer.forSend(loggedInInfo, fakeEmailLog());

        // The encrypted message body is rendered as message.pdf with record ID -1.
        assertThat(manifests.forAttachment(attachment(DocumentType.DOC, -1))).isNull();
        assertThat(manifests.forAttachment(attachment(null, 4))).isNull();
        verifyNoInteractions(documentDao);
    }

    @Test
    @DisplayName("should keep the document ID but no title or date when the record is gone")
    void shouldKeepTheDocumentId_whenTheRecordIsGone() {
        DocumentManifest manifest = composer.forSend(loggedInInfo, fakeEmailLog())
                .forAttachment(attachment(DocumentType.EFORM, 42));

        assertThat(manifest.documentId()).isEqualTo("NQP6VEqbRs3qDiu8FfzrbHJT80WWArgyZP7gAbGYPFw");
        assertThat(manifest.documentTitle()).isNull();
        assertThat(manifest.serviceDate()).isNull();
    }

    @Test
    @DisplayName("should name the patient last, first without the alias, and drop a birth date that is not a date")
    void shouldNameThePatient_withoutAliasOrInvalidBirthDate() {
        EmailLog emailLog = fakeEmailLog();
        emailLog.getDemographic().setAlias("FAKE-Jojo");
        emailLog.getDemographic().setMonthOfBirth("13");

        DocumentManifest manifest = composer.forSend(loggedInInfo, emailLog)
                .forAttachment(attachment(DocumentType.EFORM, 42));

        assertThat(manifest.patientName()).isEqualTo("FAKE-Manifest-Patient, Jo");
        assertThat(manifest.patientBirthDate()).isNull();
    }

    @Test
    @DisplayName("should carry the portal clinic ID when the portal is on, and none when its settings are broken")
    void shouldCarryThePortalClinicId_whenThePortalIsOn() {
        PatientPortalSettings settings = mock(PatientPortalSettings.class);
        when(settings.clinicId()).thenReturn("clinic-a");
        portal.when(PatientPortalSettings::isConfigured).thenReturn(true);
        portal.when(PatientPortalSettings::fromCarlosProperties).thenReturn(settings);

        assertThat(composer.forSend(loggedInInfo, fakeEmailLog()).forAttachment(attachment(DocumentType.EFORM, 42))
                .clinicId()).isEqualTo("clinic-a");

        portal.when(PatientPortalSettings::fromCarlosProperties)
                .thenThrow(new PatientPortalConfigurationException("FAKE incomplete settings"));

        assertThat(composer.forSend(loggedInInfo, fakeEmailLog()).forAttachment(attachment(DocumentType.EFORM, 42))
                .clinicId()).isNull();
    }

    @Test
    @DisplayName("should keep the document ID sources that every issued ID depends on")
    void shouldKeepTheDocumentIdSources_forEveryType() {
        // Changing any of these gives every document of that type a new ID.
        assertThat(EmailDocumentManifestComposer.documentIdSource(DocumentType.EFORM)).isEqualTo("eform");
        assertThat(EmailDocumentManifestComposer.documentIdSource(DocumentType.DOC)).isEqualTo("document");
        assertThat(EmailDocumentManifestComposer.documentIdSource(DocumentType.LAB)).isEqualTo("lab");
        assertThat(EmailDocumentManifestComposer.documentIdSource(DocumentType.HRM)).isEqualTo("hrm");
        assertThat(EmailDocumentManifestComposer.documentIdSource(DocumentType.FORM)).isEqualTo("form");
    }

    private static byte[] bytesOneToThirtyTwo() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return bytes;
    }

    private static EmailLog fakeEmailLog() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(1001);
        patient.setLastName("FAKE-Manifest-Patient");
        patient.setFirstName("Jo");
        patient.setYearOfBirth("1985");
        patient.setMonthOfBirth("06");
        patient.setDateOfBirth("15");
        Provider provider = new Provider();
        provider.setLastName("FAKE-Provider");
        provider.setFirstName("Demo");
        EmailLog emailLog = new EmailLog();
        emailLog.setDemographic(patient);
        emailLog.setProvider(provider);
        return emailLog;
    }

    private static EmailAttachment attachment(DocumentType type, int recordId) {
        return new EmailAttachment("artifact-1.pdf", "/tmp/artifact-1.pdf", type, recordId);
    }

    private static Date localDate(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
