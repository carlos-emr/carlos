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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationLabsNotIncluded;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import org.springframework.mock.web.MockHttpSession;

/**
 * Issue #4693: a consultation whose attachments include an HL7 lab and a lab the PDF renderer cannot
 * draw (here MDS). The HL7 lab must be in the packet; the MDS lab must be named on a page right
 * after the consultation letter, so whoever prints or receives the fax knows it exists, and must
 * never reach the HL7 renderer (its number could be one of the patient's HL7 labs).
 */
@DisplayName("DocumentAttachmentManagerImpl consultation packet with labs it cannot draw")
@Tag("unit")
@Tag("documentManager")
class DocumentAttachmentManagerImplLabsNotIncludedUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 123;
    private static final int REQUEST = 456;
    private static final String MDS_NAME = "MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00";

    @TempDir
    Path tempDir;

    private DocumentAttachmentManagerImpl manager;
    private LoggedInInfo loggedInInfo;
    private ConsultationManager consultationManager;
    private LabManager labManager;
    private NioFileManager nioFileManager;
    private PatientLabRoutingDao patientLabRoutingDao;
    private MockedStatic<EDocUtil> eDocUtil;
    private MockedConstruction<CommonLabResultData> labListing;
    private MockHttpServletRequest request;
    private Path consultationLetter;
    private Path hl7LabPdf;
    private final AtomicReference<List<Object>> packet = new AtomicReference<>();
    private final AtomicReference<byte[]> noticeBytes = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        loggedInInfo = mock(LoggedInInfo.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, PATIENT)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, PATIENT)).thenReturn(true);

        consultationLetter = tempDir.resolve("consultation-letter.pdf");
        hl7LabPdf = tempDir.resolve("hl7-lab-10.pdf");
        consultationManager = mock(ConsultationManager.class);
        ConsultationRequest consultation = new ConsultationRequest();
        consultation.setDemographicId(PATIENT);
        when(consultationManager.getRequest(loggedInInfo, REQUEST)).thenReturn(consultation);
        when(consultationManager.renderConsultationForm(any())).thenReturn(consultationLetter);
        when(consultationManager.getAttachedEForms(String.valueOf(REQUEST))).thenReturn(new ArrayList<>());
        when(consultationManager.getAttachedHRMDocuments(loggedInInfo, String.valueOf(PATIENT), String.valueOf(REQUEST)))
                .thenReturn(new ArrayList<>());
        when(consultationManager.getAttachedForms(loggedInInfo, REQUEST, PATIENT)).thenReturn(new ArrayList<>());

        labManager = mock(LabManager.class);
        when(labManager.renderLab(loggedInInfo, 10)).thenReturn(hl7LabPdf);

        // saveTempFile writes the notice page somewhere real, as the production manager does.
        nioFileManager = mock(NioFileManager.class);
        when(nioFileManager.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenAnswer(invocation -> {
            Path file = Files.createTempFile(tempDir, "notice", ".pdf");
            Files.write(file, ((ByteArrayOutputStream) invocation.getArgument(1)).toByteArray());
            return file;
        });

        // The real ownership filter: lab 10 is the patient's HL7 lab. Number 20 is ALSO one of the
        // patient's HL7 lab numbers, so only the listed type keeps the MDS lab out of the renderer.
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                .thenReturn(List.of(10, 20));
        AttachmentOwnershipService ownership = new AttachmentOwnershipService(mock(CtlDocumentDao.class),
                patientLabRoutingDao, mock(EFormDataDao.class), mock(HRMDocumentToDemographicDao.class),
                mock(ConsultationRequestDao.class), Set::of);

        // CommonLabResultData resolves these in its static initializer.
        registerMock(PatientLabRoutingDao.class, patientLabRoutingDao);
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, securityInfoManager);

        manager = spy(new DocumentAttachmentManagerImpl());
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "consultationManager", consultationManager);
        injectDependency(manager, "labManager", labManager);
        injectDependency(manager, "nioFileManager", nioFileManager);
        injectDependency(manager, "attachmentOwnershipService", ownership);
        doAnswer(invocation -> {
            List<Object> documents = new ArrayList<>(invocation.getArgument(0));
            packet.set(documents);
            if (documents.size() > 1 && Files.exists(Path.of(String.valueOf(documents.get(1))))) {
                noticeBytes.set(Files.readAllBytes(Path.of(String.valueOf(documents.get(1)))));
            }
            return tempDir.resolve("packet.pdf");
        }).when(manager).concatPDF(any(), eq(String.valueOf(PATIENT)));

        eDocUtil = mockStatic(EDocUtil.class);
        eDocUtil.when(() -> EDocUtil.listDocs(any(), any(), any(), anyBoolean())).thenReturn(new ArrayList<EDoc>());

        request = new MockHttpServletRequest("POST", "/encounter/RequestConsultation");
        MockHttpSession session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        request.setSession(session);
        request.setAttribute("reqId", String.valueOf(REQUEST));
        request.setAttribute("demographicId", String.valueOf(PATIENT));
    }

    @AfterEach
    void tearDown() {
        if (labListing != null) {
            labListing.close();
        }
        if (eDocUtil != null) {
            eDocUtil.close();
        }
    }

    private void listAttachedLabs(LabResultData... labs) {
        labListing = mockConstruction(CommonLabResultData.class, (listing, context) ->
                when(listing.populateLabResultsData(any(), eq(String.valueOf(PATIENT)), eq(String.valueOf(REQUEST)), anyBoolean()))
                        .thenReturn(new ArrayList<>(List.of(labs))));
    }

    private static LabResultData hl7Lab(String segmentId) {
        LabResultData lab = new LabResultData(LabResultData.HL7TEXT);
        lab.setSegmentID(segmentId);
        return lab;
    }

    private static LabResultData mdsLab(String segmentId) {
        LabResultData lab = new LabResultData(LabResultData.MDS);
        lab.setSegmentID(segmentId);
        lab.discipline = "Hem/Chem/Other";
        lab.dateTime = "2024-03-05 10:22:00";
        return lab;
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    @DisplayName("should print the HL7 lab and name the MDS lab on a page right after the consultation letter")
    void shouldNameMdsLabAfterConsultationLetter_whenConsultHasHl7AndMdsLabs() throws Exception {
        listAttachedLabs(hl7Lab("10"), mdsLab("20"));

        manager.renderConsultationFormWithAttachments(request, new MockHttpServletResponse());

        assertThat(packet.get()).hasSize(3);
        assertThat(packet.get().get(0)).isEqualTo(consultationLetter.toString());
        assertThat(packet.get().get(2)).isEqualTo(hl7LabPdf.toString());
        assertThat(noticeBytes.get()).as("the page after the consultation letter").isNotNull();
        String notice = pdfText(noticeBytes.get());
        assertThat(notice).contains("Attachments not included");
        assertThat(notice).contains(MDS_NAME);
        // The MDS number collides with an HL7 lab of the patient; it must never be rendered as one.
        verify(labManager).renderLab(loggedInInfo, 10);
        verifyNoMoreInteractions(labManager);
        assertThat(request.getAttribute(ConsultationLabsNotIncluded.REQUEST_ATTRIBUTE)).isEqualTo(List.of(MDS_NAME));
    }

    @Test
    @DisplayName("should add no page and name nothing when every attached lab is HL7")
    void shouldAddNoNoticePage_whenEveryLabIsHl7() throws Exception {
        listAttachedLabs(hl7Lab("10"));

        manager.renderConsultationFormWithAttachments(request, new MockHttpServletResponse());

        assertThat(packet.get()).containsExactly(consultationLetter.toString(), hl7LabPdf.toString());
        assertThat(request.getAttribute(ConsultationLabsNotIncluded.REQUEST_ATTRIBUTE)).isEqualTo(List.of());
        verify(nioFileManager, never()).saveTempFile(anyString(), any(ByteArrayOutputStream.class));
    }

    @Test
    @DisplayName("should delete the page naming the left-out labs when a later attachment cannot be drawn")
    void shouldDeleteNoticePage_whenLaterAttachmentFails() throws Exception {
        // The notice names lab disciplines and dates. The cleanup deletes only files in the CARLOS-owned
        // temp subtree, which is where NioFileManager.saveTempFile puts it in production.
        Path applicationTemp = Files.createDirectories(
                Path.of(System.getProperty("java.io.tmpdir"), PathValidationUtils.APPLICATION_TEMP_ROOT_NAME));
        List<Path> notices = new ArrayList<>();
        when(nioFileManager.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenAnswer(invocation -> {
            Path file = Files.createTempFile(applicationTemp, "consultationLabsNotIncluded", ".pdf");
            Files.write(file, ((ByteArrayOutputStream) invocation.getArgument(1)).toByteArray());
            notices.add(file);
            return file;
        });
        when(labManager.renderLab(loggedInInfo, 10)).thenThrow(new PDFGenerationException("HL7 lab could not be drawn"));
        listAttachedLabs(hl7Lab("10"), mdsLab("20"));

        try {
            assertThatThrownBy(() -> manager.renderConsultationFormWithAttachments(request, new MockHttpServletResponse()))
                    .isInstanceOf(PDFGenerationException.class);

            assertThat(notices).as("the notice page was made before the HL7 lab failed").hasSize(1);
            assertThat(notices.get(0)).doesNotExist();
        } finally {
            for (Path notice : notices) {
                Files.deleteIfExists(notice);
            }
        }
    }
}
