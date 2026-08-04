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

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
class DocumentPreview2ActionUnitTest extends CarlosUnitTestBase {

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
    private EFormRenderApprovalService mockEFormRenderApprovalService;

    @Mock
    private PdfPreviewCapabilityService mockPdfPreviewCapabilityService;

    @Mock
    private FormsManager mockFormsManager;

    @Mock
    private EFormDataDao mockEFormDataDao;

    @Mock
    private PatientLabRoutingDao mockPatientLabRoutingDao;

    @Mock
    private HRMDocumentToDemographicDao mockHrmDocumentToDemographicDao;

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
        registerMock(EFormRenderApprovalService.class, mockEFormRenderApprovalService);
        registerMock(PdfPreviewCapabilityService.class, mockPdfPreviewCapabilityService);
        registerMock(FormsManager.class, mockFormsManager);
        registerMock(EFormDataDao.class, mockEFormDataDao);
        registerMock(PatientLabRoutingDao.class, mockPatientLabRoutingDao);
        registerMock(HRMDocumentToDemographicDao.class, mockHrmDocumentToDemographicDao);

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
    @DisplayName("should defer consult privilege check to fetch consult documents")
    void shouldDeferConsultPrivilegeCheck_whenFetchingConsultDocuments() {
        request.setParameter("method", "fetchConsultDocuments");
        doReturn("fetchDocuments").when(action).fetchConsultDocuments();

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        verify(action).fetchConsultDocuments();
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should default to fetch consult documents when method is missing")
    void shouldDefaultToFetchConsultDocuments_whenMethodIsMissing() {
        doReturn("fetchDocuments").when(action).fetchConsultDocuments();

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        verify(action).fetchConsultDocuments();
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should allow consult document fetch when write is denied but read is granted")
    void shouldAllowFetchConsultDocuments_whenConsultWriteDeniedButReadGranted() {
        request.setParameter("method", "fetchConsultDocuments");
        request.setParameter("demographicNo", "123");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.READ, "123")).thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "123")).thenReturn(false);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        assertThat(request.getAttribute("attachmentSecurityObject")).isEqualTo("_con");
        assertThat(request.getAttribute("canManageAttachments")).isEqualTo(false);
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.READ, null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
        eDocUtilMock.verifyNoInteractions();
        eFormUtilMock.verifyNoInteractions();
        hrmUtilMock.verifyNoInteractions();
        verify(mockDocumentAttachmentManager, never()).getAllLabsSortedByVersions(any(LoggedInInfo.class), any(String.class));
        verify(mockFormsManager, never()).getEncounterFormsbyDemographicNumber(any(LoggedInInfo.class), any(Integer.class), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("should throw security exception when demographic consult read privilege is denied")
    void shouldThrowSecurityException_whenDemographicConsultReadPrivilegeDenied() {
        request.setParameter("method", "fetchConsultDocuments");
        request.setParameter("demographicNo", "123");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.READ, "123")).thenReturn(false);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_con");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.READ, null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, "123");
        eDocUtilMock.verifyNoInteractions();
        eFormUtilMock.verifyNoInteractions();
        hrmUtilMock.verifyNoInteractions();
        verify(mockDocumentAttachmentManager, never()).getAllLabsSortedByVersions(any(LoggedInInfo.class), any(String.class));
        verify(mockFormsManager, never()).getEncounterFormsbyDemographicNumber(any(LoggedInInfo.class), any(Integer.class), anyBoolean(), anyBoolean());
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
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.READ, "0");
        eDocUtilMock.verify(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "0", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE));
        eFormUtilMock.verify(() -> EFormUtil.listPatientEformsCurrent(0, true));
    }

    @Test
    @DisplayName("should hide protected metadata when consult access lacks read privileges")
    void shouldHideProtectedMetadata_whenConsultAccessLacksReadPrivileges() {
        request.setParameter("method", "fetchConsultDocuments");
        request.setParameter("demographicNo", "123");

        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, "123")).thenReturn(false);
        // fetchConsultDocuments also gates eForms on _eform; deny it too so "lacks all access"
        // truly fetches nothing and verifyNoInteractions(EFormUtil) holds.
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "123")).thenReturn(false);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        assertThat(request.getAttribute("attachmentSecurityObject")).isEqualTo("_con");
        assertThat(request.getAttribute("canManageAttachments")).isEqualTo(false);
        assertThat(request.getAttribute("allDocuments")).isEqualTo(List.of());
        assertThat(request.getAttribute("allHRMDocuments")).isEqualTo(List.of());
        assertThat(request.getAttribute("allLabsSortedByVersions")).isEqualTo(List.of());
        assertThat(request.getAttribute("allForms")).isEqualTo(List.of());
        assertThat(request.getAttribute("allEForms")).isEqualTo(List.of());

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
        eDocUtilMock.verifyNoInteractions();
        eFormUtilMock.verifyNoInteractions();
        hrmUtilMock.verifyNoInteractions();
        verify(mockDocumentAttachmentManager, never()).getAllLabsSortedByVersions(any(LoggedInInfo.class), any(String.class));
        verify(mockFormsManager, never()).getEncounterFormsbyDemographicNumber(any(LoggedInInfo.class), any(Integer.class), anyBoolean(), anyBoolean());
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, "123");
    }

    @Test
    @DisplayName("should populate edoc list when edoc read privilege is granted")
    void shouldPopulateEdocList_whenEdocReadPrivilegeIsGranted() {
        request.setParameter("method", "fetchConsultDocuments");
        request.setParameter("demographicNo", "123");

        List<EDoc> expectedDocuments = List.of(mockEDoc);

        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, "123")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, "123")).thenReturn(false);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);
        eDocUtilMock.when(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "123", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE))
                .thenReturn(new ArrayList<>(expectedDocuments));

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        assertThat(request.getAttribute("allDocuments")).isEqualTo(expectedDocuments);
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
        eDocUtilMock.verify(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "123", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE));
    }

    @Test
    @DisplayName("should defer eForm privilege check to fetch eForm documents")
    void shouldDeferEformPrivilegeCheck_whenFetchingEformDocuments() {
        request.setParameter("method", "fetchEFormDocuments");
        doReturn("fetchDocuments").when(action).fetchEFormDocuments();

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should throw security exception when demographic eForm read privilege is denied")
    void shouldThrowSecurityException_whenDemographicEformReadPrivilegeDenied() {
        request.setParameter("method", "fetchEFormDocuments");
        request.setParameter("demographicNo", "123");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "123")).thenReturn(false);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eform");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_con", SecurityInfoManager.WRITE, null);
        eDocUtilMock.verifyNoInteractions();
        eFormUtilMock.verifyNoInteractions();
        hrmUtilMock.verifyNoInteractions();
        verify(mockDocumentAttachmentManager, never()).getAllEFormsExpectFdid(any(LoggedInInfo.class), any(), any());
    }

    @Test
    @DisplayName("should fall back to zero when fetch eform demographic is invalid")
    void shouldFallBackToZero_whenFetchingEformDemographicWithInvalidValue() {
        request.setParameter("method", "fetchEFormDocuments");
        request.setParameter("demographicNo", "abc");
        request.setParameter("fdid", "not-a-number");

        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, "0")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, "0")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, "0")).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.WRITE, "0")).thenReturn(true);

        eDocUtilMock = mockStatic(EDocUtil.class);
        eFormUtilMock = mockStatic(EFormUtil.class);
        hrmUtilMock = mockStatic(HRMUtil.class);
        when(mockDocumentAttachmentManager.getAllEFormsExpectFdid(mockLoggedInInfo, 0, 0)).thenReturn(List.of());

        String result = action.execute();

        assertThat(result).isEqualTo("fetchDocuments");
        assertThat(request.getAttribute("attachmentSecurityObject")).isEqualTo("_eform");
        assertThat(request.getAttribute("canManageAttachments")).isEqualTo(true);
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "0");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.WRITE, "0");
        verify(mockDocumentAttachmentManager).getAllEFormsExpectFdid(mockLoggedInInfo, 0, 0);
        eDocUtilMock.verify(() -> EDocUtil.listDocs(mockLoggedInInfo, "demographic", "0", null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE));
    }

    @Test
    @DisplayName("should defer eDoc render privilege check to render eDoc pdf")
    void shouldDeferEdocRenderPrivilegeCheck_whenRenderingEdocPdf() {
        request.setParameter("method", "renderEDocPDF");
        doNothing().when(action).renderEDocPDF();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should defer eForm render privilege check to render eForm pdf")
    void shouldDeferEformRenderPrivilegeCheck_whenRenderingEformPdf() {
        request.setParameter("method", "renderEFormPDF");
        doNothing().when(action).renderEFormPDF();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should defer HRM render privilege check to render HRM pdf")
    void shouldDeferHrmRenderPrivilegeCheck_whenRenderingHrmPdf() {
        request.setParameter("method", "renderHrmPDF");
        doNothing().when(action).renderHrmPDF();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should defer lab render privilege check to render lab pdf")
    void shouldDeferLabRenderPrivilegeCheck_whenRenderingLabPdf() {
        request.setParameter("method", "renderLabPDF");
        doNothing().when(action).renderLabPDF();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should defer form render privilege check to render form pdf")
    void shouldDeferFormRenderPrivilegeCheck_whenRenderingFormPdf() {
        request.setParameter("method", "renderFormPDF");
        doNothing().when(action).renderFormPDF();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should return bad request when render edoc pdf id is invalid")
    void shouldReturnBadRequest_whenRenderEdocPdfIdIsInvalid() throws Exception {
        request.setParameter("method", "renderEDocPDF");
        request.setParameter("eDocId", "invalid");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"errorCode\":\"invalid_request\","
                        + "\"errorMessage\":\"Invalid preview request.\"}");
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.DOC), any());
    }

    @Test
    @DisplayName("should return bad request when render edoc pdf demographic is missing")
    void shouldReturnBadRequest_whenRenderEdocPdfDemographicIsMissing() throws Exception {
        request.setParameter("method", "renderEDocPDF");
        request.setParameter("eDocId", "42");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"errorCode\":\"invalid_request\","
                        + "\"errorMessage\":\"Invalid preview request.\"}");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.DOC), any());
    }

    @Test
    @DisplayName("should deny eDoc preview before document lookup when read is missing")
    void shouldDenyEdocPreview_beforeDocumentLookupWhenReadMissing() throws Exception {
        request.setParameter("method", "renderEDocPDF");
        request.setParameter("eDocId", "42");
        request.setParameter("demographicNo", "123");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, "123"))
                .thenReturn(false);
        eDocUtilMock = mockStatic(EDocUtil.class);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_edoc)");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, "123");
        eDocUtilMock.verifyNoInteractions();
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.DOC), any());
    }

    @Test
    @DisplayName("should deny eForm preview before eForm lookup when read is missing")
    void shouldDenyEformPreview_beforeEformLookupWhenReadMissing() throws Exception {
        request.setParameter("method", "renderEFormPDF");
        request.setParameter("eFormId", "42");
        request.setParameter("demographicNo", "123");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "123"))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        verify(mockEFormDataDao, never()).find(42);
        verify(mockDocumentAttachmentManager, never()).renderEform(eq(mockLoggedInInfo), any(), any());
    }

    @Test
    @DisplayName("should deny HRM preview before HRM lookup when read is missing")
    void shouldDenyHrmPreview_beforeHrmLookupWhenReadMissing() throws Exception {
        request.setParameter("method", "renderHrmPDF");
        request.setParameter("hrmId", "43");
        request.setParameter("demographicNo", "123");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, "123"))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_hrm)");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, "123");
        verify(mockHrmDocumentToDemographicDao, never()).findByHrmDocumentId(43);
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.HRM), any());
    }

    @Test
    @DisplayName("should deny lab preview before lab lookup when read is missing")
    void shouldDenyLabPreview_beforeLabLookupWhenReadMissing() throws Exception {
        request.setParameter("method", "renderLabPDF");
        request.setParameter("segmentId", "44");
        request.setParameter("demographicNo", "123");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, "123"))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_lab)");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, "123");
        verify(mockPatientLabRoutingDao, never()).findDemographicByLabId(44);
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.LAB), any());
    }

    @Test
    @DisplayName("should return error json when render eform pdf generation fails")
    void shouldReturnErrorJson_whenRenderEformPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderEFormPDF");
        request.setParameter("eFormId", "42");
        request.setParameter("demographicNo", "123");
        when(mockEFormDataDao.find(42)).thenReturn(eFormData(123));

        // No approval token is present, so the manager receives no incomplete-render capability.
        when(mockDocumentAttachmentManager.renderEform(mockLoggedInInfo, 42, (EFormRenderApproval) null))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("render failed"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"errorCode\":\"eform_render_failed\","
                        + "\"errorMessage\":\"Failed to render eForm PDF.\"}");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should disclose every incomplete eForm issue before issuing approval")
    void shouldDiscloseEveryIncompleteEformIssue_beforeIssuingApproval() throws Exception {
        request.setParameter("method", "renderEFormPDF");
        request.setParameter("eFormId", "42");
        request.setParameter("demographicNo", "123");
        when(mockEFormDataDao.find(42)).thenReturn(eFormData(123));
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(2, 3, 0, 0, true, true, false, false);
        when(mockDocumentAttachmentManager.renderEform(mockLoggedInInfo, 42, (EFormRenderApproval) null))
                .thenThrow(new EformContentUnavailableException("incomplete", 42, report));
        when(mockEFormRenderApprovalService.issue(
                request, mockLoggedInInfo, 42, "123",
                EFormRenderApprovalService.Operation.PREVIEW, report, null, 42))
                .thenReturn("approval-token");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .contains("\"missingContent\":true")
                .contains("\"renderApproval\":\"approval-token\"")
                .contains("\"failedContentResources\":2")
                .contains("\"excludedContentElements\":3")
                .contains("\"signatureMissing\":true")
                .contains("\"timerCompatibilityFailure\":true")
                .doesNotContain("\"errorMessage\":\"incomplete\"");
    }

    @Test
    @DisplayName("should deliver the eForm PDF and disclose a contained-interaction advisory")
    void shouldDeliverEformPdf_andDiscloseContainedInteractionAdvisory() throws Exception {
        // Suppressed dialogs are advisory because they remove no PDF content, but the client still
        // receives the count. Severe page-script errors are blocking and never reach this success
        // response without an exact approval.
        request.setParameter("method", "renderEFormPDF");
        request.setParameter("eFormId", "42");
        request.setParameter("demographicNo", "123");
        when(mockEFormDataDao.find(42)).thenReturn(eFormData(123));
        EFormRenderCompletenessReport advisoryOnly =
                new EFormRenderCompletenessReport(0, 0, 0, 1, false, false, false, false);
        when(mockDocumentAttachmentManager.renderEform(mockLoggedInInfo, 42, (EFormRenderApproval) null))
                .thenReturn(new EformDataManager.EformPdfRender(
                        java.nio.file.Path.of("eform-browser-render-1.pdf"), advisoryOnly));
        when(mockDocumentAttachmentManager.convertPDFToBase64(any())).thenReturn("QUJD");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .contains("\"base64Data\":\"QUJD\"")
                .contains("\"advisoryIssues\":1")
                .doesNotContain("missingContent");
    }

    @Test
    @DisplayName("should omit the advisory field when the eForm rendered cleanly")
    void shouldOmitAdvisoryField_whenEformRenderedCleanly() throws Exception {
        request.setParameter("method", "renderEFormPDF");
        request.setParameter("eFormId", "42");
        request.setParameter("demographicNo", "123");
        when(mockEFormDataDao.find(42)).thenReturn(eFormData(123));
        when(mockDocumentAttachmentManager.renderEform(mockLoggedInInfo, 42, (EFormRenderApproval) null))
                .thenReturn(new EformDataManager.EformPdfRender(
                        java.nio.file.Path.of("eform-browser-render-1.pdf"),
                        EFormRenderCompletenessReport.complete()));
        when(mockDocumentAttachmentManager.convertPDFToBase64(any())).thenReturn("QUJD");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .contains("\"base64Data\":\"QUJD\"")
                .doesNotContain("advisoryIssues");
    }

    @Test
    @DisplayName("should return error json when render edoc pdf generation fails")
    void shouldReturnErrorJson_whenRenderEdocPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderEDocPDF");
        request.setParameter("eDocId", "42");
        request.setParameter("demographicNo", "123");
        stubEDocDemographic(42, "123");

        when(mockDocumentAttachmentManager.renderDocument(mockLoggedInInfo, DocumentType.DOC, 42))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("edoc failed"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .contains("errorMessage")
                .contains("Failed to render document PDF.")
                .doesNotContain("edoc failed");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should return error json when render hrm pdf generation fails")
    void shouldReturnErrorJson_whenRenderHrmPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderHrmPDF");
        request.setParameter("hrmId", "43");
        request.setParameter("demographicNo", "123");
        when(mockHrmDocumentToDemographicDao.findByHrmDocumentId(43)).thenReturn(List.of(hrmDemographic(43, 123)));

        when(mockDocumentAttachmentManager.renderDocument(mockLoggedInInfo, DocumentType.HRM, 43))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("hrm failed"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .contains("errorMessage")
                .contains("Failed to render HRM PDF.")
                .doesNotContain("hrm failed");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should return error json when render lab pdf generation fails")
    void shouldReturnErrorJson_whenRenderLabPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderLabPDF");
        request.setParameter("segmentId", "44");
        request.setParameter("demographicNo", "123");
        when(mockPatientLabRoutingDao.findDemographicByLabId(44)).thenReturn(new PatientLabRouting(44, "HL7", 123));

        when(mockDocumentAttachmentManager.renderDocument(mockLoggedInInfo, DocumentType.LAB, 44))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("lab failed"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .contains("errorMessage")
                .contains("Failed to render lab PDF.")
                .doesNotContain("lab failed");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should return error json when render form pdf generation fails")
    void shouldReturnErrorJson_whenRenderFormPdfGenerationFails() throws Exception {
        request.setParameter("method", "renderFormPDF");
        request.setParameter("formId", "45");
        request.setParameter("formName", "Annual");
        request.setParameter("demographicNo", "123");
        when(mockFormsManager.getEncounterFormsbyDemographicNumber(mockLoggedInInfo, 123, true, true))
                .thenReturn(List.of(new EctFormData.PatientForm("formAnnual", "Annual", 45, 123)));

        when(mockDocumentAttachmentManager.renderDocument(request, response, DocumentType.FORM))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException("form failed"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .contains("errorMessage")
                .contains("Failed to render form PDF.")
                .doesNotContain("form failed");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should deny render edoc pdf when document demographic does not match request")
    void shouldDenyRenderEdocPdf_whenDocumentDemographicDoesNotMatchRequest() throws Exception {
        request.setParameter("method", "renderEDocPDF");
        request.setParameter("eDocId", "42");
        request.setParameter("demographicNo", "123");
        stubEDocDemographic(42, "456");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("document does not match demographic");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_edoc", SecurityInfoManager.READ, "123");
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.DOC), any());
    }

    @Test
    @DisplayName("should deny render eform pdf when eForm demographic does not match request")
    void shouldDenyRenderEformPdf_whenEformDemographicDoesNotMatchRequest() throws Exception {
        request.setParameter("method", "renderEFormPDF");
        request.setParameter("eFormId", "42");
        request.setParameter("demographicNo", "123");
        when(mockEFormDataDao.find(42)).thenReturn(eFormData(456));

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("eForm does not match demographic");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        verify(mockDocumentAttachmentManager, never()).renderEform(eq(mockLoggedInInfo), any(), any());
    }

    @Test
    @DisplayName("should deny render hrm pdf when HRM demographic does not match request")
    void shouldDenyRenderHrmPdf_whenHrmDemographicDoesNotMatchRequest() throws Exception {
        request.setParameter("method", "renderHrmPDF");
        request.setParameter("hrmId", "43");
        request.setParameter("demographicNo", "123");
        when(mockHrmDocumentToDemographicDao.findByHrmDocumentId(43)).thenReturn(List.of(hrmDemographic(43, 456)));

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("HRM document does not match demographic");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_hrm", SecurityInfoManager.READ, "123");
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.HRM), any());
    }

    @Test
    @DisplayName("should deny render lab pdf when lab demographic does not match request")
    void shouldDenyRenderLabPdf_whenLabDemographicDoesNotMatchRequest() throws Exception {
        request.setParameter("method", "renderLabPDF");
        request.setParameter("segmentId", "44");
        request.setParameter("demographicNo", "123");
        when(mockPatientLabRoutingDao.findDemographicByLabId(44)).thenReturn(new PatientLabRouting(44, "HL7", 456));

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("lab does not match demographic");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_lab", SecurityInfoManager.READ, "123");
        verify(mockDocumentAttachmentManager, never()).renderDocument(eq(mockLoggedInInfo), eq(DocumentType.LAB), any());
    }

    @Test
    @DisplayName("should deny render form pdf when form is not in demographic forms")
    void shouldDenyRenderFormPdf_whenFormDoesNotMatchDemographic() throws Exception {
        request.setParameter("method", "renderFormPDF");
        request.setParameter("formId", "45");
        request.setParameter("formName", "Annual");
        request.setParameter("demographicNo", "123");
        when(mockFormsManager.getEncounterFormsbyDemographicNumber(mockLoggedInInfo, 123, true, true))
                .thenReturn(List.of(new EctFormData.PatientForm("formAnnual", "Annual", 45, 456)));

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("form does not match demographic");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(mockDocumentAttachmentManager, never()).renderDocument(request, response, DocumentType.FORM);
    }

    @Test
    @DisplayName("should return bad request when render form pdf name is missing")
    void shouldReturnBadRequest_whenRenderFormPdfNameIsMissing() throws Exception {
        request.setParameter("method", "renderFormPDF");
        request.setParameter("formId", "45");
        request.setParameter("demographicNo", "123");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"errorCode\":\"invalid_request\","
                        + "\"errorMessage\":\"Invalid preview request.\"}");
        verify(mockDocumentAttachmentManager, never()).renderDocument(request, response, DocumentType.FORM);
    }

    @Test
    @DisplayName("should serve pdf bytes only when a preview capability resolves the exact file")
    void shouldServePdfBytes_whenPreviewCapabilityResolves() throws Exception {
        java.nio.file.Path tempRoot =
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "carlos-eform-browser-pdf-temp");
        java.nio.file.Files.createDirectories(tempRoot);
        java.nio.file.Path pdf = java.nio.file.Files.createTempFile(tempRoot, "eform-browser-render-", ".pdf");
        try {
            byte[] pdfBytes = "%PDF-1.4 unit-test".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            java.nio.file.Files.write(pdf, pdfBytes);
            request.setParameter("method", "renderPDF");
            request.setParameter("previewToken", "opaque-token");
            when(mockPdfPreviewCapabilityService.resolve(request, mockLoggedInInfo, "opaque-token"))
                    .thenReturn(pdf.toRealPath());

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray())
                    .startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        } finally {
            java.nio.file.Files.deleteIfExists(pdf);
        }
    }

    @Test
    @DisplayName("should reject a raw server path even when it names an application temp file")
    void shouldRejectRawPath_withoutPreviewCapability() {
        request.setParameter("method", "renderPDF");
        request.setParameter("pdfPath",
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                        "carlos-eform-browser-pdf-temp", "eform-browser-render-existing.pdf").toString());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(403);
        verify(mockPdfPreviewCapabilityService).resolve(request, mockLoggedInInfo, null);
    }

    private void stubEDocDemographic(Integer eDocId, String demographicNo) {
        eDocUtilMock = mockStatic(EDocUtil.class);
        eDocUtilMock.when(() -> EDocUtil.getEDocFromDocId(String.valueOf(eDocId))).thenReturn(mockEDoc);
        when(mockEDoc.getModule()).thenReturn("demographic");
        when(mockEDoc.getModuleId()).thenReturn(demographicNo);
    }

    private HRMDocumentToDemographic hrmDemographic(Integer hrmId, Integer demographicNo) {
        HRMDocumentToDemographic hrmDocumentToDemographic = new HRMDocumentToDemographic();
        hrmDocumentToDemographic.setHrmDocumentId(hrmId);
        hrmDocumentToDemographic.setDemographicNo(demographicNo);
        return hrmDocumentToDemographic;
    }

    private EFormData eFormData(Integer demographicNo) {
        EFormData eFormData = new EFormData();
        eFormData.setDemographicId(demographicNo);
        return eFormData;
    }

}
