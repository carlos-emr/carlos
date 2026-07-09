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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("DocumentAttachmentManagerImpl")
@Tag("unit")
class DocumentAttachmentManagerImplUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo loggedInInfo;
    private LabManager labManager;
    private ConsultationRequestDao consultationRequestDao;
    private ConsultationManager consultationManager;
    private DocumentManager documentManager;
    private EformDataManager eformDataManager;
    private FormsManager formsManager;
    private NioFileManager nioFileManager;
    private SecurityInfoManager securityInfoManager;
    private DocumentAttachmentManagerImpl manager;
    private Path basePdf;
    private Path outputPdf;

    @BeforeEach
    void setUp() throws Exception {
        request = new MockHttpServletRequest("POST", "/encounter/RequestConsultation");
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        labManager = mock(LabManager.class);
        consultationRequestDao = mock(ConsultationRequestDao.class);
        consultationManager = mock(ConsultationManager.class);
        documentManager = mock(DocumentManager.class);
        eformDataManager = mock(EformDataManager.class);
        formsManager = mock(FormsManager.class);
        nioFileManager = mock(NioFileManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);

        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, securityInfoManager);

        manager = new DocumentAttachmentManagerImpl(labManager);
        ReflectionTestUtils.setField(manager, "consultDocsDao", mock(ConsultDocsDao.class));
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

    private MockedConstruction<CommonLabResultData> mockCommonLabResultData(List<LabResultData> labs) {
        return mockConstruction(CommonLabResultData.class,
                (mock, context) -> when(mock.populateLabResultsData(any(LoggedInInfo.class), anyString(), anyString(), eq(true)))
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
}
