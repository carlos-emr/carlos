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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xml.sax.InputSource;

import cds.LaboratoryResultsDocument.LaboratoryResults;
import cds.OmdCdsDocument;
import cds.PatientRecordDocument.PatientRecord;
import cds.ReportsDocument.Reports;
import cdsDt.ReportClass;
import cdsDt.ReportFormat;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PathL7EmbeddedDocumentMessage;
import io.github.carlos_emr.carlos.utility.XmlUtils;

/**
 * Drives {@link DemographicExportAction42Action#exportHl7LabResults(PatientRecord, String)} with a
 * real PATHL7 message that mixes discrete results and an embedded PDF (#3946).
 *
 * <p>Before the fix the PDF was written as a {@code LaboratoryResults} value (the whole base64
 * blob), a 150-character ordinary result escaped the 120-character limit because it looked like
 * base64, and a vertical tab in a result was exported as {@code '?'}.</p>
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("demographic")
@Tag("lab")
class DemographicExportAction42ActionLabDocumentUnitTest extends DemographicExportActionUnitTestBase {

    private static final int LAB_NO = 3946;

    @Mock
    private ProviderLabRoutingDao providerLabRoutingDao;
    @Mock
    private ProviderDataDao providerDataDao;

    private PatientRecord patientRecord;

    @BeforeEach
    void seedLab() {
        registerMock(ProviderLabRoutingDao.class, providerLabRoutingDao);
        registerMock(ProviderDataDao.class, providerDataDao);

        Hl7TextInfo info = new Hl7TextInfo();
        info.setLabNumber(LAB_NO);
        List<Object[]> infos = new ArrayList<>();
        infos.add(new Object[] {info, null});
        when(hl7TextInfoDao.findByDemographicId(LAB_NO)).thenReturn(infos);

        Hl7TextMessage message = new Hl7TextMessage();
        message.setType("PATHL7");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(
                PathL7EmbeddedDocumentMessage.message().getBytes(StandardCharsets.UTF_8)));
        when(hl7TextMessageDao.find(LAB_NO)).thenReturn(message);

        action.exportError = new ArrayList<>();
        patientRecord = OmdCdsDocument.Factory.newInstance().addNewOmdCds().addNewPatientRecord();
    }

    private void acknowledgedBy(String providerNo) {
        ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
        routing.setProviderNo(providerNo);
        routing.setTimestamp(new Date(1_788_000_000_000L));
        when(providerLabRoutingDao.findByLabNoAndLabType(LAB_NO, "HL7")).thenReturn(routing);

        ProviderData provider = new ProviderData();
        provider.setFirstName("FAKE-Pat");
        provider.setLastName("FAKE-Reviewer");
        provider.setOhipNo("123456");
        when(providerDataDao.findByProviderNo(anyString())).thenReturn(provider);
    }

    @Test
    @DisplayName("should export the ED segment as a binary Lab Report and not as a lab result")
    void shouldExportEmbeddedPdfAsReport_notAsLabResult() {
        acknowledgedBy("999998");

        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));

        String pdfBase64 = Base64.getEncoder().encodeToString(PathL7EmbeddedDocumentMessage.PDF);
        assertThat(patientRecord.getLaboratoryResultsArray()).hasSize(3)
                .allSatisfy(lab -> assertThat(lab.getResult().getValue()).doesNotContain(pdfBase64));

        Reports[] reports = patientRecord.getReportsArray();
        assertThat(reports).hasSize(1);
        Reports report = reports[0];
        assertThat(report.getFormat()).isEqualTo(ReportFormat.BINARY);
        assertThat(report.getContent().getMedia()).isEqualTo(PathL7EmbeddedDocumentMessage.PDF);
        assertThat(report.getFileExtensionAndVersion()).isEqualTo(".pdf");
        assertThat(report.getClass1()).isEqualTo(ReportClass.LAB_REPORT);
        assertThat(report.getSubClass()).isEqualTo("Pathology Report");
        assertThat(report.getMessageUniqueID()).isEqualTo(PathL7EmbeddedDocumentMessage.ACCESSION);

        // The acknowledgement the lab result path would have exported travels with the report.
        assertThat(report.getReportReviewedArray()).hasSize(1);
        assertThat(report.getReportReviewedArray(0).getName().getLastName()).isEqualTo("FAKE-Reviewer");
        assertThat(report.getReportReviewedArray(0).getReviewingOHIPPhysicianId()).isEqualTo("123456");

        List<XmlError> errors = new ArrayList<>();
        report.validate(new XmlOptions().setErrorListener(errors));
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("should truncate a long result to 120 characters even when it looks like base64")
    void shouldTruncateLongResult_whenTextLooksLikeBase64() {
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));

        LaboratoryResults interpretation = Arrays.stream(patientRecord.getLaboratoryResultsArray())
                .filter(lab -> "NOTE".equals(lab.getLabTestCode())).findFirst().orElseThrow();
        assertThat(interpretation.getResult().getValue())
                .isEqualTo(PathL7EmbeddedDocumentMessage.LONG_TEXT_RESULT.substring(0, 120));
        assertThat(action.exportError).anyMatch(message -> message.contains("truncated") && message.contains("NOTE"));
    }

    @Test
    @DisplayName("should strip characters XML 1.0 forbids instead of exporting them as question marks")
    void shouldStripInvalidXmlCharacters_fromResultValues() throws Exception {
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));

        LaboratoryResults specimen = Arrays.stream(patientRecord.getLaboratoryResultsArray())
                .filter(lab -> "SPEC".equals(lab.getLabTestCode())).findFirst().orElseThrow();
        assertThat(specimen.getResult().getValue()).isEqualTo("Hemolysedsample");

        StringWriter xml = new StringWriter();
        patientRecord.save(xml);
        assertThat(xml.toString()).contains("Hemolysedsample").doesNotContain("Hemolysed?sample");
        DocumentBuilderFactory factory = XmlUtils.createSecureDocumentBuilderFactory();
        assertThat(factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml.toString())))).isNotNull();
    }

    @Test
    @DisplayName("should export labs without a reviewer when the lab has no routing row")
    void shouldExportWithoutReviewer_whenLabHasNoRoutingRow() {
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));

        assertThat(patientRecord.getReportsArray()).hasSize(1);
        assertThat(patientRecord.getReportsArray(0).getReportReviewedArray()).isEmpty();
        assertThat(patientRecord.getLaboratoryResultsArray())
                .allSatisfy(lab -> assertThat(lab.getResultReviewerArray()).isEmpty());
    }
    private void replaceMessage(String body) {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setType("PATHL7");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        when(hl7TextMessageDao.find(LAB_NO)).thenReturn(message);
    }

    @Test
    void shouldWarnAndContinueExport_whenEdValueFieldIsEntirelyAbsent() {
        replaceMessage(PathL7EmbeddedDocumentMessage.message().replace("OBX|1|ED|PDF^Pathology Report||",
                "OBX|1|ED|MISSING^Unavailable report||||||||F|||20260901100000\r"
                + "NTE|1||Document pending\rOBX|2|ED|PDF^Pathology Report||"));
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));
        assertThat(patientRecord.getReportsArray()).hasSize(2);
        assertThat(patientRecord.getReportsArray(0).getContent().getTextContent()).isEmpty();
        assertThat(patientRecord.getReportsArray(0).getNotes()).contains("Document pending");
        assertThat(patientRecord.getReportsArray(1).getContent().getMedia())
                .isEqualTo(PathL7EmbeddedDocumentMessage.PDF);
        assertThat(action.exportError).anyMatch(message -> message.contains("no payload"));
    }

    @Test
    void shouldKeepEmptyEdAsReportAndWarnInsteadOfDroppingIt() {
        replaceMessage(PathL7EmbeddedDocumentMessage.message().replace(
                Base64.getEncoder().encodeToString(PathL7EmbeddedDocumentMessage.PDF), ""));
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));
        assertThat(patientRecord.getLaboratoryResultsArray()).hasSize(3);
        assertThat(patientRecord.getReportsArray()).hasSize(1);
        assertThat(patientRecord.getReportsArray(0).getContent().getTextContent()).isEmpty();
        assertThat(action.exportError).anyMatch(message -> message.contains("no payload"));
    }

    @Test
    void shouldRouteCommentsOnEmptyEdToReportNotesNotAResult() {
        replaceMessage(PathL7EmbeddedDocumentMessage.message().replace(
                Base64.getEncoder().encodeToString(PathL7EmbeddedDocumentMessage.PDF), "") + "NTE|1||Document pending\r");
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));
        assertThat(patientRecord.getLaboratoryResultsArray()).hasSize(3);
        assertThat(patientRecord.getReportsArray(0).getNotes()).contains("Document pending");
        assertThat(patientRecord.getReportsArray(0).getContent().getTextContent()).isEmpty();
    }

    @Test
    void shouldPropagateUnencodedEdMetadataThroughTheExporter() {
        String text = "NEGATIVE".repeat(20);
        replaceMessage(PathL7EmbeddedDocumentMessage.message().replace(
                "^TEXT^PDF^Base64^" + Base64.getEncoder().encodeToString(PathL7EmbeddedDocumentMessage.PDF),
                "^TEXT^^A^" + text));
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));
        assertThat(patientRecord.getReportsArray(0).getContent().getTextContent()).isEqualTo(text);
    }

    @Test
    void shouldWarnWhenPhysicianAnnotationExceedsRemainingNotesSpace() {
        replaceMessage(PathL7EmbeddedDocumentMessage.message() + "NTE|1||" + "x".repeat(31999) + "\r");
        var link = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink();
        link.setNoteId(99L);
        var note = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote();
        note.setNote("Physician annotation");
        when(caseManagementManager.getLinkByTableIdDesc(
                io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink.LABTEST,
                (long) LAB_NO, "1-0")).thenReturn(List.of(link));
        when(caseManagementManager.getNote("99")).thenReturn(note);
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));
        assertThat(patientRecord.getReportsArray(0).getNotes()).hasSize(32000);
        assertThat(action.exportError).anyMatch(message -> message.contains("Report notes truncated"));
    }

    @Test
    void shouldValidateCompleteRecordWithLabsReportsAppointmentsAndClinicalNotes() throws Exception {
        var demographics = patientRecord.addNewDemographics();
        var name = demographics.addNewNames().addNewLegalName();
        var first = name.addNewFirstName();
        first.setPart("FAKE");
        first.setPartType(cdsDt.PersonNamePartTypeCode.GIV);
        var last = name.addNewLastName();
        last.setPart("Export");
        last.setPartType(cdsDt.PersonNamePartTypeCode.FAMC);
        name.setNamePurpose(cdsDt.PersonNamePurposeCode.L);
        demographics.setDateOfBirth(Util.calDate("1980-01-02"));
        demographics.setGender(cdsDt.Gender.F);
        demographics.setUniqueVendorIdSequence("3946");
        demographics.addNewPersonStatusCode().setPersonStatusAsEnum(cdsDt.PersonStatus.A);
        // Match execute(): clinical notes precede labs; appointments are added after reports exist.
        patientRecord.addNewClinicalNotes().setMyClinicalNotesContent("FAKE clinical note");
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));
        var appointment = patientRecord.addNewAppointments();
        appointment.setAppointmentTime(Util.calDate("2026-09-01 10:00:00"));
        appointment.addNewAppointmentDate().setFullDate(Util.calDate("2026-09-01"));
        // Validate serialized/reparsed output too: XMLBeans inserts typed elements in schema order.
        StringWriter xml = new StringWriter();
        var exported = cds.PatientRecordDocument.Factory.newInstance();
        exported.setPatientRecord(patientRecord);
        exported.save(xml);
        var reparsed = cds.PatientRecordDocument.Factory.parse(xml.toString());
        List<XmlError> errors = new ArrayList<>();
        assertThat(reparsed.validate(new XmlOptions().setErrorListener(errors))).as(errors.toString()).isTrue();
        var factory = XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml.toString())));
        List<String> sections = new ArrayList<>();
        for (var node = document.getDocumentElement().getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) sections.add(node.getLocalName());
        }
        assertThat(sections).containsExactly("Demographics", "LaboratoryResults", "LaboratoryResults",
                "LaboratoryResults", "Appointments", "ClinicalNotes", "Reports");
    }

    @Test
    void shouldPreserveRawCellpathRtfFromEdSourceApplication() {
        String rtf = "{\\rtf1\\ansi FAKE pathology report}";
        replaceMessage(PathL7EmbeddedDocumentMessage.message().replace("||PATH|F", "||CELLPATHR|F")
                .replace("^TEXT^PDF^Base64^" + Base64.getEncoder().encodeToString(PathL7EmbeddedDocumentMessage.PDF),
                        rtf.replace("\\", "\\E\\") + "^TEXT^RTF^A^"));
        action.exportHl7LabResults(patientRecord, String.valueOf(LAB_NO));
        Reports report = patientRecord.getReportsArray(0);
        assertThat(report.getFileExtensionAndVersion()).isEqualTo(".rtf");
        assertThat(report.getContent().getMedia()).isEqualTo(rtf.getBytes(StandardCharsets.UTF_8));
    }

}
