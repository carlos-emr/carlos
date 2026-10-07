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
package io.github.carlos_emr.carlos.documentManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMReportParser;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("DocumentAttachmentManagerImpl")
@Tag("unit")
class DocumentAttachmentManagerImplAttachmentResilienceUnitTest extends CarlosUnitTestBase {

    /** A synthetic, schema-valid HRM report shipped with the dev database. */
    private static final Path DEMO_HRM_REPORT = Path.of(".devcontainer/db/db_data/hrm/demo-hrm-diagnostic-imaging.xml");
    private static final String HRM_12_WARNING = "HRM attachment 12 is unavailable and was not included.";

    @TempDir
    Path documentDir;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo loggedInInfo;
    private LabManager labManager;
    private ConsultDocsDao consultDocsDao;
    private ConsultationRequestDao consultationRequestDao;
    private ConsultationManager consultationManager;
    private DocumentManager documentManager;
    private EformDataManager eformDataManager;
    private FormsManager formsManager;
    private NioFileManager nioFileManager;
    private SecurityInfoManager securityInfoManager;
    private HRMDocumentDao hrmDocumentDao;
    private DocumentAttachmentManagerImpl manager;
    private Path basePdf;
    private Path outputPdf;

    @BeforeEach
    void setUp() throws Exception {
        request = new MockHttpServletRequest("POST", "/encounter/RequestConsultation");
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        labManager = mock(LabManager.class);
        consultDocsDao = mock(ConsultDocsDao.class);
        consultationRequestDao = mock(ConsultationRequestDao.class);
        consultationManager = mock(ConsultationManager.class);
        documentManager = mock(DocumentManager.class);
        eformDataManager = mock(EformDataManager.class);
        formsManager = mock(FormsManager.class);
        nioFileManager = mock(NioFileManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        hrmDocumentDao = mock(HRMDocumentDao.class);

        registerMock(HRMDocumentDao.class, hrmDocumentDao);
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, securityInfoManager);

        manager = new DocumentAttachmentManagerImpl(labManager);
        ReflectionTestUtils.setField(manager, "consultDocsDao", consultDocsDao);
        ReflectionTestUtils.setField(manager, "consultationRequestDao", consultationRequestDao);
        ReflectionTestUtils.setField(manager, "eFormDocsDao", mock(EFormDocsDao.class));
        ReflectionTestUtils.setField(manager, "consultationManager", consultationManager);
        ReflectionTestUtils.setField(manager, "documentManager", documentManager);
        ReflectionTestUtils.setField(manager, "eformDataManager", eformDataManager);
        ReflectionTestUtils.setField(manager, "formsManager", formsManager);
        ReflectionTestUtils.setField(manager, "nioFileManager", nioFileManager);
        ReflectionTestUtils.setField(manager, "securityInfoManager", securityInfoManager);

        basePdf = createPdf("consult-base");
        outputPdf = createPdf("consult-output");

        ConsultationRequest consultationRequest = new ConsultationRequest();
        consultationRequest.setDemographicId(1);
        when(consultationRequestDao.find(9)).thenReturn(consultationRequest);
        when(consultationManager.renderConsultationForm(request)).thenReturn(basePdf);
        when(consultationManager.getAttachedEForms("9")).thenReturn(List.of());
        when(consultationManager.getAttachedHRMDocuments(loggedInInfo, "1", "9"))
                .thenReturn(new ArrayList<HashMap<String, ? extends Object>>());
        when(consultationManager.getAttachedForms(loggedInInfo, 9, 1)).thenReturn(List.<EctFormData.PatientForm>of());
        when(nioFileManager.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenReturn(outputPdf);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (basePdf != null) {
            Files.deleteIfExists(basePdf);
        }
        if (outputPdf != null) {
            Files.deleteIfExists(outputPdf);
        }
    }

    @Test
    @DisplayName("coerces request attributes before resolving the consultation patient")
    void shouldCoerceRequestAttributes_whenRenderingConsultationWithAttachments() throws Exception {
        request.setAttribute("reqId", Integer.valueOf(9));
        request.setAttribute("demographicId", Integer.valueOf(999));

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedConstruction<CommonLabResultData> commonLabResultDataMock = mockCommonLabResultData(List.of())) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.listDocs(loggedInInfo, "1", "9", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());

            Path result = manager.renderConsultationFormWithAttachments(request, response);

            assertThat(result).isEqualTo(outputPdf);
            assertThat(request.getAttribute("demographicId")).isEqualTo("1");
            assertThat(commonLabResultDataMock.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("uses the persisted consultation demographic when the request attribute does not match")
    void shouldUsePersistedConsultationDemographic_whenRequestAttributeDoesNotMatch() throws Exception {
        request.setAttribute("reqId", "9");
        request.setAttribute("demographicId", "999");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedConstruction<CommonLabResultData> commonLabResultDataMock = mockCommonLabResultData(List.of())) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.listDocs(loggedInInfo, "1", "9", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());

            Path result = manager.renderConsultationFormWithAttachments(request, response);

            assertThat(result).isEqualTo(outputPdf);
            assertThat(request.getAttribute("demographicId")).isEqualTo("1");
            verify(consultationRequestDao).find(9);
            verify(consultationManager).getAttachedForms(loggedInInfo, 9, 1);
            assertThat(commonLabResultDataMock.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("warns and skips malformed lab segment ids")
    void shouldWarnAndSkipLab_whenSegmentIdIsMalformed() throws Exception {
        LabResultData malformedLab = new LabResultData();
        malformedLab.setSegmentID("BAD");
        request.setAttribute("reqId", "9");
        request.setAttribute("demographicId", "1");
        request.setAttribute(DocumentAttachmentManager.ALLOW_SKIPPED_ATTACHMENTS_ATTRIBUTE, Boolean.TRUE);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedConstruction<CommonLabResultData> ignored = mockCommonLabResultData(List.of(malformedLab))) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.listDocs(loggedInInfo, "1", "9", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());

            Path result = manager.renderConsultationFormWithAttachments(request, response);

            assertThat(result).isEqualTo(outputPdf);
            assertThat(request.getAttribute(DocumentAttachmentManager.ATTACHMENT_WARNINGS_ATTRIBUTE))
                    .asList()
                    .containsExactly("Lab attachment BAD is unavailable and was not included.");
            verify(labManager, never()).renderLab(any(LoggedInInfo.class), any());
        }
    }

    @Test
    @DisplayName("rejects an eForm packet when an attached lab segment id is malformed")
    void shouldRejectEformPacket_whenAttachedLabSegmentIdIsMalformed() throws Exception {
        LabResultData malformedLab = new LabResultData();
        malformedLab.setSegmentID("BAD");
        EFormData storedEform = mock(EFormData.class);
        when(storedEform.getDemographicId()).thenReturn(1);
        request.setAttribute("fdid", "7");
        request.setAttribute("demographicId", "1");
        when(eformDataManager.findByFdid(loggedInInfo, 7)).thenReturn(storedEform);
        when(eformDataManager.createEformPdfWithCompleteness(loggedInInfo, 7, null))
                .thenReturn(new EformDataManager.EformPdfRender(
                        basePdf, EFormRenderCompletenessReport.complete()));
        when(eformDataManager.getHRMDocumentsAttachedToEForm(loggedInInfo, "7", "1"))
                .thenReturn(new ArrayList<HashMap<String, ? extends Object>>());
        when(eformDataManager.getFormsAttachedToEForm(loggedInInfo, "7", "1"))
                .thenReturn(List.of());

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EFormUtil> eFormUtilMock = mockStatic(EFormUtil.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedConstruction<CommonLabResultData> ignored =
                        mockEformCommonLabResultData(List.of(malformedLab))) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eFormUtilMock.when(() -> EFormUtil.listPatientEformsCurrentAttachedToEForm("7"))
                    .thenReturn(List.of());
            eDocUtilMock.when(() -> EDocUtil.listDocsAttachedToEForm(
                            loggedInInfo, "1", "7", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());

            assertThatThrownBy(() -> manager.renderEFormWithAttachments(request, response))
                    .isInstanceOf(PDFGenerationException.class)
                    .hasMessage("Attached lab could not be rendered because its segment id is invalid.")
                    .hasCauseInstanceOf(NumberFormatException.class);
            verify(labManager, never()).renderLab(any(LoggedInInfo.class), any());
        }
    }

    @Test
    @DisplayName("lists unavailable attachment warnings without rendering anything")
    void shouldListUnavailableWarnings_withoutRendering() {
        when(consultDocsDao.findUnavailableActiveConsultAttachments(9))
                .thenReturn(List.of(consultDoc(80, "D"), consultDoc(20, "L")));

        assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactly(
                "Document attachment 80 is unavailable and was not included.",
                "Lab attachment 20 is unavailable and was not included.");
        assertThat(manager.getUnavailableConsultAttachmentWarnings(null)).isEmpty();
        assertThat(request.getAttribute(DocumentAttachmentManager.ATTACHMENT_WARNINGS_ATTRIBUTE)).isNull();
        verifyNoInteractions(consultationManager);
    }

    @Test
    @DisplayName("warns for unavailable consultdoc rows filtered before rendering")
    void shouldWarnForUnavailableConsultDocs_whenRowsAreFilteredBeforeRendering() throws Exception {
        request.setAttribute("reqId", "9");
        request.setAttribute("demographicId", "1");
        when(consultDocsDao.findUnavailableActiveConsultAttachments(9))
                .thenReturn(List.of(
                        consultDoc(80, "D"),
                        consultDoc(915, "E"),
                        consultDoc(20, "L")));

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedConstruction<CommonLabResultData> ignored = mockCommonLabResultData(List.of())) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.listDocs(loggedInInfo, "1", "9", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());

            Path result = manager.renderConsultationFormWithAttachments(request, response);

            assertThat(result).isEqualTo(outputPdf);
            assertThat(request.getAttribute(DocumentAttachmentManager.ATTACHMENT_WARNINGS_ATTRIBUTE))
                    .asList()
                    .containsExactly(
                            "Document attachment 80 is unavailable and was not included.",
                            "eForm attachment 915 is unavailable and was not included.",
                            "Lab attachment 20 is unavailable and was not included.");
            verify(consultDocsDao).findUnavailableActiveConsultAttachments(9);
        }
    }

    @Test
    @DisplayName("warns and skips form attachments that cannot be rendered")
    void shouldWarnAndSkipForm_whenFormRenderingFails() throws Exception {
        request.setAttribute("reqId", "9");
        request.setAttribute("demographicId", "1");
        request.setAttribute(DocumentAttachmentManager.ALLOW_SKIPPED_ATTACHMENTS_ATTRIBUTE, Boolean.TRUE);
        EctFormData.PatientForm form = new EctFormData.PatientForm("Annual", 3, 1, null, null);
        when(consultationManager.getAttachedForms(loggedInInfo, 9, 1)).thenReturn(List.of(form));
        when(formsManager.renderForm(request, response, form))
                .thenThrow(new PDFGenerationException("form route unavailable"));

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedConstruction<CommonLabResultData> ignored = mockCommonLabResultData(List.of())) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.listDocs(loggedInInfo, "1", "9", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());

            Path result = manager.renderConsultationFormWithAttachments(request, response);

            assertThat(result).isEqualTo(outputPdf);
            assertThat(request.getAttribute(DocumentAttachmentManager.ATTACHMENT_WARNINGS_ATTRIBUTE))
                    .asList()
                    .containsExactly("Form attachment 3 is unavailable and was not included.");
            verify(formsManager).renderForm(request, response, form);
        }
    }

    @Test
    @DisplayName("fails the whole render, for print and fax, when an attachment cannot be rendered")
    void shouldFailRender_whenAttachmentFailsAndSkippingIsNotAllowed() throws Exception {
        request.setAttribute("reqId", "9");
        request.setAttribute("demographicId", "1");
        EctFormData.PatientForm form = new EctFormData.PatientForm("Annual", 3, 1, null, null);
        when(consultationManager.getAttachedForms(loggedInInfo, 9, 1)).thenReturn(List.of(form));
        when(formsManager.renderForm(request, response, form))
                .thenThrow(new PDFGenerationException("form route unavailable"));

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedConstruction<CommonLabResultData> ignored = mockCommonLabResultData(List.of())) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.listDocs(loggedInInfo, "1", "9", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());

            // A faxed or printed consult must never go out silently missing an attachment it lists.
            assertThatThrownBy(() -> manager.renderConsultationFormWithAttachments(request, response))
                    .isInstanceOf(PDFGenerationException.class);
        }
    }

    @Test
    @DisplayName("warns, without rendering, for an attached HRM report whose file is missing")
    void shouldWarnForHrmAttachment_whenReportFileIsMissing() {
        attachHrmReport(12, "missing-report.xml");

        try (MockedStatic<CarlosProperties> ignored = documentDirectoryAt(documentDir)) {
            assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactly(HRM_12_WARNING);
        }
        verify(hrmDocumentDao).find(Integer.valueOf(12));
        verifyNoInteractions(consultationManager);
    }

    @Test
    @DisplayName("warns for an attached HRM report whose file is not a readable HRM report")
    void shouldWarnForHrmAttachment_whenReportFileCannotBeParsed() throws Exception {
        Files.writeString(documentDir.resolve("corrupt-report.xml"), "<not-an-hrm-report/>");
        attachHrmReport(12, "corrupt-report.xml");

        try (MockedStatic<CarlosProperties> ignored = documentDirectoryAt(documentDir)) {
            assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactly(HRM_12_WARNING);
        }
    }

    @Test
    @DisplayName("logs why an attached HRM report was left out when its file is missing")
    void shouldLogReadFailureReason_whenHrmReportFileIsMissing() {
        attachHrmReport(12, "missing-report.xml");

        try (MockedStatic<CarlosProperties> ignored = documentDirectoryAt(documentDir);
                LogCapture log = LogCapture.forLogger(DocumentAttachmentManagerImpl.class)) {
            assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactly(HRM_12_WARNING);

            assertThat(log.messages()).anySatisfy(message -> assertThat(message)
                    .contains("type=H id=12")
                    .endsWith("missing or unreadable HRM report file"));
        }
    }

    @Test
    @DisplayName("logs only the exception class when reading an attached HRM report fails unexpectedly")
    void shouldLogExceptionClassOnly_whenHrmParserThrows() {
        when(consultDocsDao.findByRequestIdDocType(9, ConsultDocs.DOCTYPE_HRM)).thenReturn(List.of(consultDoc(12, "H")));

        try (MockedStatic<HRMReportParser> hrmReportParserMock = mockStatic(HRMReportParser.class);
                LogCapture log = LogCapture.forLogger(DocumentAttachmentManagerImpl.class)) {
            // The message stands for one that quotes a file path; it must not reach the log.
            hrmReportParserMock.when(() -> HRMReportParser.parseReport(isNull(), eq(Integer.valueOf(12))))
                    .thenThrow(new IllegalStateException("cannot read /documents/FAKE-hrm-path.xml"));

            assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactly(HRM_12_WARNING);

            assertThat(log.messages()).anySatisfy(message -> assertThat(message)
                    .contains("type=H id=12")
                    .endsWith("IllegalStateException"));
            assertThat(log.messages()).noneSatisfy(message -> assertThat(message).contains("FAKE-hrm-path"));
        }
    }

    @Test
    @DisplayName("warns once, from the database check, for an attached HRM report whose record is gone")
    void shouldWarnOnceForHrmAttachment_whenHrmRecordIsMissing() {
        ConsultDocs missingRecord = consultDoc(990006, "H");
        ConsultDocs duplicate = consultDoc(990006, "H");
        when(consultDocsDao.findUnavailableActiveConsultAttachments(9)).thenReturn(List.of(missingRecord, duplicate));
        when(consultDocsDao.findByRequestIdDocType(9, ConsultDocs.DOCTYPE_HRM)).thenReturn(List.of(missingRecord, duplicate));

        assertThat(manager.getUnavailableConsultAttachmentWarnings(9))
                .containsExactly("HRM attachment 990006 is unavailable and was not included.");
        // The database check already named it; its report file is not looked for as well.
        verifyNoInteractions(hrmDocumentDao);
    }

    @Test
    @DisplayName("does not warn for an attached HRM report that is on file and readable")
    void shouldNotWarnForHrmAttachment_whenReportIsPresentAndReadable() throws Exception {
        Files.copy(DEMO_HRM_REPORT, documentDir.resolve("demo-hrm.xml"));
        attachHrmReport(12, "demo-hrm.xml");

        try (MockedStatic<CarlosProperties> ignored = documentDirectoryAt(documentDir)) {
            assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).isEmpty();
        }
        verify(hrmDocumentDao).find(Integer.valueOf(12));
    }

    @Test
    @DisplayName("keeps a print or fax render going, with a warning, when an attached HRM report cannot be read")
    void shouldKeepRenderingWithWarning_whenAttachedHrmReportCannotBeRead() throws Exception {
        // No ALLOW_SKIPPED_ATTACHMENTS_ATTRIBUTE: this is the fail-closed print and fax render.
        request.setAttribute("reqId", "9");
        request.setAttribute("demographicId", "1");
        when(consultDocsDao.findByRequestIdDocType(9, ConsultDocs.DOCTYPE_HRM)).thenReturn(List.of(consultDoc(12, "H")));
        // setUp's empty HRM list stands for HRMUtil.listHRMDocuments, which leaves out a report it cannot parse.

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedStatic<HRMReportParser> hrmReportParserMock = mockStatic(HRMReportParser.class);
                MockedConstruction<CommonLabResultData> ignored = mockCommonLabResultData(List.of())) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.listDocs(loggedInInfo, "1", "9", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());
            hrmReportParserMock.when(() -> HRMReportParser.parseReport(isNull(), eq(Integer.valueOf(12))))
                    .thenReturn(null);

            Path result = manager.renderConsultationFormWithAttachments(request, response);

            assertThat(result).isEqualTo(outputPdf);
            assertThat(request.getAttribute(DocumentAttachmentManager.ATTACHMENT_WARNINGS_ATTRIBUTE))
                    .asList()
                    .containsExactly(HRM_12_WARNING);
        }
    }

    /** Attaches HRM report 12 (or another id) to consult 9, with its record naming the given file. */
    private void attachHrmReport(int hrmId, String reportFile) {
        HRMDocument hrmDocument = new HRMDocument();
        hrmDocument.setReportFile(reportFile);
        // Integer, not int: the parser calls find(Object), and find(int) is a different overload.
        when(hrmDocumentDao.find(Integer.valueOf(hrmId))).thenReturn(hrmDocument);
        when(consultDocsDao.findByRequestIdDocType(9, ConsultDocs.DOCTYPE_HRM))
                .thenReturn(List.of(consultDoc(hrmId, "H")));
    }

    private MockedStatic<CarlosProperties> documentDirectoryAt(Path directory) {
        MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class);
        CarlosProperties properties = mock(CarlosProperties.class);
        propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(directory.toString());
        return propertiesMock;
    }

    @Test
    @DisplayName("keeps an unavailable attachment a reopened form no longer lists, and the print and fax still warn")
    void shouldKeepUnavailableAttachmentAttached_whenUpdateOmitsIt() throws Exception {
        ConsultDocs deletedDocument = attachedRow(1, 80, ConsultDocs.DOCTYPE_DOC);
        stubConsultAttachments(List.of(deletedDocument), List.of(deletedDocument));

        // The form was opened after document 80 was deleted, so it lists, and submits, no documents.
        manager.attachToConsult(loggedInInfo, DocumentType.DOC, new String[0], "999998", 9, 1);

        assertThat(deletedDocument.getDeleted()).isNull();
        verify(consultDocsDao, never()).merge(any());
        String warning = "Document attachment 80 is unavailable and was not included.";
        // The fax cover page and the "Update And Print Preview" render read the same active row.
        assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactly(warning);
        assertThat(renderConsultationWarnings()).asList().containsExactly(warning);
    }

    @Test
    @DisplayName("still detaches an available attachment the user removed, with no warning")
    void shouldDetachRemovedAvailableAttachment_withoutWarning() throws Exception {
        ConsultDocs removedDocument = attachedRow(2, 81, ConsultDocs.DOCTYPE_DOC);
        stubConsultAttachments(List.of(removedDocument), List.of());

        manager.attachToConsult(loggedInInfo, DocumentType.DOC, new String[0], "999998", 9, 1);

        assertThat(removedDocument.getDeleted()).isEqualTo("Y");
        verify(consultDocsDao).merge(removedDocument);
        assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).isEmpty();
        assertThat(renderConsultationWarnings()).asList().isEmpty();
    }

    @Test
    @DisplayName("keeps and warns for unavailable attachments while detaching removed available ones in the same save")
    void shouldKeepUnavailableAndDetachRemovedAttachments_withMixedUpdate() throws Exception {
        ConsultDocs deletedDocument = attachedRow(1, 80, ConsultDocs.DOCTYPE_DOC);
        ConsultDocs removedDocument = attachedRow(2, 81, ConsultDocs.DOCTYPE_DOC);
        ConsultDocs keptDocument = attachedRow(3, 82, ConsultDocs.DOCTYPE_DOC);
        ConsultDocs movedLab = attachedRow(4, 20, ConsultDocs.DOCTYPE_LAB);
        // Shares its number with the unavailable document 80, but is an available lab: detached.
        ConsultDocs removedLab = attachedRow(5, 80, ConsultDocs.DOCTYPE_LAB);
        ConsultDocs movedEform = attachedRow(6, 915, ConsultDocs.DOCTYPE_EFORM);
        stubConsultAttachments(
                List.of(deletedDocument, removedDocument, keptDocument, movedLab, removedLab, movedEform),
                List.of(deletedDocument, movedLab, movedEform));

        // The reopened form lists document 82 only; the user unticked document 81 and lab 80.
        manager.attachToConsult(loggedInInfo, DocumentType.DOC, new String[] {"82"}, "999998", 9, 1);
        manager.attachToConsult(loggedInInfo, DocumentType.LAB, new String[0], "999998", 9, 1);
        manager.attachToConsult(loggedInInfo, DocumentType.EFORM, new String[0], "999998", 9, 1);

        assertThat(deletedDocument.getDeleted()).isNull();
        assertThat(keptDocument.getDeleted()).isNull();
        assertThat(movedLab.getDeleted()).isNull();
        assertThat(movedEform.getDeleted()).isNull();
        assertThat(removedDocument.getDeleted()).isEqualTo("Y");
        assertThat(removedLab.getDeleted()).isEqualTo("Y");
        verify(consultDocsDao, never()).persist(any());
        List<String> expectedWarnings = List.of(
                "Document attachment 80 is unavailable and was not included.",
                "Lab attachment 20 is unavailable and was not included.",
                "eForm attachment 915 is unavailable and was not included.");
        assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactlyElementsOf(expectedWarnings);
        assertThat(renderConsultationWarnings()).asList().containsExactlyElementsOf(expectedWarnings);
    }

    @Test
    @DisplayName("keeps a missing or rematched HRM attached when a reopened consultation omits it")
    void shouldKeepUnavailableHrmAttached_whenUpdateOmitsIt() throws Exception {
        ConsultDocs missingReport = attachedRow(1, 12, ConsultDocs.DOCTYPE_HRM);
        stubConsultAttachments(List.of(missingReport), List.of(missingReport));

        manager.attachToConsult(loggedInInfo, DocumentType.HRM, new String[0], "999998", 9, 1);

        assertThat(missingReport.getDeleted()).isNull();
        verify(consultDocsDao, never()).merge(any());
        assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactly(HRM_12_WARNING);
        assertThat(renderConsultationWarnings()).asList().containsExactly(HRM_12_WARNING);
        verifyNoInteractions(hrmDocumentDao);
    }

    @Test
    @DisplayName("keeps an unreadable HRM attached but detaches a readable report the user removed")
    void shouldKeepUnreadableHrmAndDetachReadableHrm_whenUpdateOmitsBoth() throws Exception {
        Files.copy(DEMO_HRM_REPORT, documentDir.resolve("readable-hrm.xml"));
        HRMDocument readable = new HRMDocument();
        readable.setReportFile("readable-hrm.xml");
        when(hrmDocumentDao.find(Integer.valueOf(13))).thenReturn(readable);
        HRMDocument unreadable = new HRMDocument();
        unreadable.setReportFile("missing-hrm.xml");
        when(hrmDocumentDao.find(Integer.valueOf(12))).thenReturn(unreadable);
        ConsultDocs missingFile = attachedRow(1, 12, ConsultDocs.DOCTYPE_HRM);
        ConsultDocs removedReport = attachedRow(2, 13, ConsultDocs.DOCTYPE_HRM);
        stubConsultAttachments(List.of(missingFile, removedReport), List.of());

        try (MockedStatic<CarlosProperties> ignored = documentDirectoryAt(documentDir)) {
            manager.attachToConsult(loggedInInfo, DocumentType.HRM, new String[0], "999998", 9, 1);

            assertThat(missingFile.getDeleted()).isNull();
            assertThat(removedReport.getDeleted()).isEqualTo("Y");
            verify(consultDocsDao).merge(removedReport);
            assertThat(manager.getUnavailableConsultAttachmentWarnings(9)).containsExactly(HRM_12_WARNING);
            assertThat(renderConsultationWarnings()).asList().containsExactly(HRM_12_WARNING);
        }
    }

    /**
     * Serves {@code rows} the way the DAO does: only rows not yet detached, so a row this save
     * detaches drops out of every later lookup, the unavailable-attachment one included.
     */
    private void stubConsultAttachments(List<ConsultDocs> rows, List<ConsultDocs> unavailableRows) {
        registerMock(ConsultDocsDao.class, consultDocsDao);
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));
        OutboundEmailArchiveDao outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        when(outboundEmailArchiveDao.findExistingDocumentNos(any())).thenReturn(Set.of());
        ReflectionTestUtils.setField(manager, "outboundEmailArchiveDao", outboundEmailArchiveDao);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 1)).thenReturn(true);

        when(consultDocsDao.findByRequestIdDocType(eq(9), anyString())).thenAnswer(invocation -> rows.stream()
                .filter(row -> row.getDeleted() == null && row.getDocType().equals(invocation.getArgument(1)))
                .toList());
        when(consultDocsDao.findByRequestIdDocNoDocType(eq(9), any(Integer.class), anyString()))
                .thenAnswer(invocation -> rows.stream()
                        .filter(row -> row.getDeleted() == null
                                && row.getDocumentNo() == (Integer) invocation.getArgument(1)
                                && row.getDocType().equals(invocation.getArgument(2)))
                        .toList());
        when(consultDocsDao.findUnavailableActiveConsultAttachments(9)).thenAnswer(invocation -> rows.stream()
                .filter(row -> row.getDeleted() == null
                        && unavailableRows.stream().anyMatch(unavailable -> unavailable == row))
                .toList());
    }

    private Object renderConsultationWarnings() throws Exception {
        request.setAttribute("reqId", "9");
        request.setAttribute("demographicId", "1");
        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
                MockedConstruction<CommonLabResultData> ignored = mockCommonLabResultData(List.of())) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.listDocs(loggedInInfo, "1", "9", EDocUtil.ATTACHED))
                    .thenReturn(new ArrayList<>());

            // Without the preview's skip permission: the print and fax render.
            assertThat(manager.renderConsultationFormWithAttachments(request, response)).isEqualTo(outputPdf);
        }
        return request.getAttribute(DocumentAttachmentManager.ATTACHMENT_WARNINGS_ATTRIBUTE);
    }

    private ConsultDocs attachedRow(int id, int documentNo, String docType) {
        ConsultDocs row = new ConsultDocs(9, documentNo, docType, "999998");
        row.setId(id);
        return row;
    }

    private MockedConstruction<CommonLabResultData> mockCommonLabResultData(List<LabResultData> labs) {
        return mockConstruction(CommonLabResultData.class,
                (mock, context) -> when(mock.populateLabResultsData(any(LoggedInInfo.class), anyString(), anyString(), eq(true)))
                        .thenReturn(new ArrayList<>(labs)));
    }

    private MockedConstruction<CommonLabResultData> mockEformCommonLabResultData(List<LabResultData> labs) {
        return mockConstruction(CommonLabResultData.class,
                (mock, context) -> when(mock.populateLabResultsDataEForm(
                                any(LoggedInInfo.class), anyString(), anyString(), eq(true)))
                        .thenReturn(new ArrayList<>(labs)));
    }

    private Path createPdf(String prefix) throws Exception {
        Path path = Files.createTempFile(prefix, ".pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            document.save(path.toFile());
        }
        return path;
    }

    private io.github.carlos_emr.carlos.commn.model.ConsultDocs consultDoc(int documentNo, String docType) {
        io.github.carlos_emr.carlos.commn.model.ConsultDocs consultDoc = new io.github.carlos_emr.carlos.commn.model.ConsultDocs();
        consultDoc.setDocumentNo(documentNo);
        consultDoc.setDocType(docType);
        return consultDoc;
    }
}
