/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentPreview2Action}.
 *
 * <p>Verifies that the consultation attachment pane uses consultation privileges
 * instead of the electronic document privilege gate when loading the attachment
 * selector.</p>
 *
 * @since 2026-04-20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentPreview2Action Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("documentManager")
class DocumentPreview2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EDocUtil> eDocUtilMock;
    private MockedStatic<EFormUtil> eFormUtilMock;
    private MockedStatic<HRMUtil> hrmUtilMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private DocumentAttachmentManager mockDocumentAttachmentManager;

    @Mock
    private FormsManager mockFormsManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private EDoc mockEDoc;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DocumentPreview2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DocumentAttachmentManager.class, mockDocumentAttachmentManager);
        registerMock(FormsManager.class, mockFormsManager);

        // lenient: not every test exercises a privilege check (e.g. unsupported-method returns 400
        // before any hasPrivilege call), so this shared default must not trip strict-stub checks.
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any())).thenReturn(true);

        action = spy(new DocumentPreview2Action());
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (eDocUtilMock != null) {
            eDocUtilMock.close();
        }
        if (eFormUtilMock != null) {
            eFormUtilMock.close();
        }
        if (hrmUtilMock != null) {
            hrmUtilMock.close();
        }
    }

    @Test
    @DisplayName("should use consult write privilege when fetching consult documents")
    void shouldUseConsultWritePrivilege_whenFetchingConsultDocuments() {
        request.setParameter("method", "fetchConsultDocuments");
        doReturn("fetchDocuments").when(action).fetchConsultDocuments();

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should default to consult write privilege when method is missing")
    void shouldDefaultToConsultWritePrivilege_whenMethodIsMissing() {
        doReturn("fetchDocuments").when(action).fetchConsultDocuments();

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, null);
    }

    @Test
    @DisplayName("should throw security exception when consult write privilege is denied")
    void shouldThrowSecurityException_whenConsultWritePrivilegeDenied() {
        request.setParameter("method", "fetchConsultDocuments");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, null)).thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_con");
    }

    @Test
    @DisplayName("should return bad request when method is unsupported")
    void shouldReturnBadRequest_whenMethodIsUnsupported() {
        request.setParameter("method", "notARealMethod");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should fall back to zero demographic when fetch consult demographic is invalid")
    void shouldFallBackToZeroDemographic_whenFetchConsultDemographicIsInvalid() {
        request.setParameter("method", "fetchConsultDocuments");
        request.setParameter("demographicNo", "not-a-number");

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);
        eDocUtilMock.when(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "0", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE))
                .thenReturn(new ArrayList<>());
        eFormUtilMock.when(() -> EFormUtil.listPatientEformsCurrent(0, true)).thenReturn(new ArrayList<>());
        hrmUtilMock.when(() -> HRMUtil.listHRMDocuments(mockLoggedInInfo, "report_date", false, "0", false))
                .thenReturn(new ArrayList<>());

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        eDocUtilMock.verify(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "0", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE));
        eFormUtilMock.verify(() -> EFormUtil.listPatientEformsCurrent(0, true));
    }

    @Test
    @DisplayName("should hide protected metadata when consult access lacks read privileges")
    void shouldHideProtectedMetadata_whenConsultAccessLacksReadPrivileges() {
        request.setParameter("method", "fetchConsultDocuments");
        request.setParameter("demographicNo", "123");

        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null)).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, null)).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, null)).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, null)).thenReturn(false);
        // fetchConsultDocuments also gates eForms on _eform; deny it too so "lacks all access"
        // truly fetches nothing and verifyNoInteractions(EFormUtil) holds.
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, null)).thenReturn(false);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        assertThat(request.getAttribute("allDocuments")).isEqualTo(List.of());
        assertThat(request.getAttribute("allHRMDocuments")).isEqualTo(List.of());
        assertThat(request.getAttribute("allLabsSortedByVersions")).isEqualTo(List.of());
        assertThat(request.getAttribute("allForms")).isEqualTo(List.of());
        assertThat(request.getAttribute("allEForms")).isEqualTo(List.of());

        eDocUtilMock.verifyNoInteractions();
        eFormUtilMock.verifyNoInteractions();
        hrmUtilMock.verifyNoInteractions();
        verify(mockDocumentAttachmentManager, never()).getAllLabsSortedByVersions(any(), any());
        verify(mockFormsManager, never()).getEncounterFormsbyDemographicNumber(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("should populate edoc list when edoc read privilege is granted")
    void shouldPopulateEdocList_whenEdocReadPrivilegeIsGranted() {
        request.setParameter("method", "fetchConsultDocuments");
        request.setParameter("demographicNo", "123");

        List<EDoc> expectedDocuments = List.of(mockEDoc);

        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, null)).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, null)).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, null)).thenReturn(false);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);
        eDocUtilMock.when(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "123", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE))
                .thenReturn(new ArrayList<>(expectedDocuments));

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        assertThat(request.getAttribute("allDocuments")).isEqualTo(expectedDocuments);
        eDocUtilMock.verify(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "123", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE));
    }

    @Test
    @DisplayName("should use edoc read privilege when fetching eform documents")
    void shouldUseEdocReadPrivilege_whenFetchingEformDocuments() {
        request.setParameter("method", "fetchEFormDocuments");
        doReturn("fetchDocuments").when(action).fetchEFormDocuments();

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, null);
    }

    @Test
    @DisplayName("should fall back to zero when fetch eform demographic is invalid")
    void shouldFallBackToZero_whenFetchingEformDemographicWithInvalidValue() {
        request.setParameter("method", "fetchEFormDocuments");
        request.setParameter("demographicNo", "abc");
        request.setParameter("fdid", "not-a-number");

        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, null)).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, null)).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, null)).thenReturn(false);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);
        when(mockDocumentAttachmentManager.getAllEFormsExpectFdid(mockLoggedInInfo, 0, 0)).thenReturn(List.of());

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        verify(mockDocumentAttachmentManager).getAllEFormsExpectFdid(mockLoggedInInfo, 0, 0);
        eDocUtilMock.verify(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "0", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE));
    }

    @Test
    @DisplayName("should use edoc read privilege when rendering edoc pdf")
    void shouldUseEdocReadPrivilege_whenRenderingEdocPdf() {
        request.setParameter("method", "renderEDocPDF");
        doNothing().when(action).renderEDocPDF();

        String result = action.execute();

        assertThat(result).isNull();
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, null);
    }

    @Test
    @DisplayName("should return bad request when render edoc pdf id is invalid")
    void shouldReturnBadRequest_whenRenderEdocPdfIdIsInvalid() throws Exception {
        request.setParameter("method", "renderEDocPDF");
        request.setParameter("eDocId", "invalid");

        String result = action.execute();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("Invalid eDocId");
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.DOC), any());
    }
    @Test
    @DisplayName("should return error json when render eform pdf generation fails")
    void shouldReturnErrorJson_whenRenderEformPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderEFormPDF");
        request.setParameter("eFormId", "42");

        when(mockDocumentAttachmentManager.renderDocument(mockLoggedInInfo, DocumentType.EFORM, 42))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("render failed"));

        String result = action.execute();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("errorMessage").doesNotContain("render failed");
    }

    @Test
    @DisplayName("should return error json when render edoc pdf generation fails")
    void shouldReturnErrorJson_whenRenderEdocPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderEDocPDF");
        request.setParameter("eDocId", "42");
        when(mockDocumentAttachmentManager.renderDocument(mockLoggedInInfo, DocumentType.DOC, 42))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("edoc failed"));

        String result = action.execute();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("errorMessage").doesNotContain("edoc failed");
    }

    @Test
    @DisplayName("should return error json when render hrm pdf generation fails")
    void shouldReturnErrorJson_whenRenderHrmPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderHrmPDF");
        request.setParameter("hrmId", "43");
        when(mockDocumentAttachmentManager.renderDocument(mockLoggedInInfo, DocumentType.HRM, 43))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("hrm failed"));

        String result = action.execute();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("errorMessage").doesNotContain("hrm failed");
    }

    @Test
    @DisplayName("should return error json when render lab pdf generation fails")
    void shouldReturnErrorJson_whenRenderLabPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderLabPDF");
        request.setParameter("segmentId", "44");
        when(mockDocumentAttachmentManager.renderDocument(mockLoggedInInfo, DocumentType.LAB, 44))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("lab failed"));

        String result = action.execute();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("errorMessage").doesNotContain("lab failed");
    }

    @Test
    @DisplayName("should return error json when render form pdf generation fails")
    void shouldReturnErrorJson_whenRenderFormPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderFormPDF");
        when(mockDocumentAttachmentManager.renderDocument(request, response, DocumentType.FORM))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("form failed"));

        String result = action.execute();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("errorMessage").doesNotContain("form failed");
    }

}
