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
package io.github.carlos_emr.carlos.eform.actions;

import java.util.HashMap;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyClientMatchDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.WaitlistDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The eForm template write must survive a refused eDoc render.
 *
 * <p>{@code EFormUtil.writeEformTemplate} is the only thing that produces an eForm's CPP and
 * encounter notes — EncounterNote, SocHistory, FamHistory, MedHistory, OngoingConcerns, RiskFactors,
 * Reminders, OMeds — plus any template-declared document, prevention, message, tickler or consult
 * request. It has exactly one call site.</p>
 *
 * <p>It used to sit in the final {@code else} of the workflow chain, which the save-as-eDoc branch's
 * approval return jumped over: an eForm saved as an eDoc, refused by the completeness gate and then
 * approved, created the eDoc, reported success, auto-closed, and left the chart notes permanently
 * unwritten. Nothing re-runs them — {@code writeEformTemplate} assigns a fresh UUID and persists
 * unconditionally, so a later retry would duplicate rather than reconcile.</p>
 *
 * <p>The second and third tests are the other half of the contract. Hoisting the write without
 * carrying the chain's condition would run it on the fax/print/download/email paths too, and because
 * it is not idempotent that would duplicate every note — a worse defect than the one being fixed.</p>
 *
 * @since 2026-07-27
 */
@DisplayName("AddEForm2Action template write")
@Tag("unit")
@Tag("eform")
class AddEForm2ActionTemplateWriteUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EFormUtil> eFormUtilMock;
    private MockedStatic<EFormLoader> eFormLoaderMock;
    private MockedStatic<WebApplicationContextUtils> webApplicationContextUtilsMock;
    private AutoCloseable mockitoMocks;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private EformDataManager mockEformDataManager;
    @Mock private DocumentAttachmentManager mockDocumentAttachmentManager;
    @Mock private EmailManager mockEmailManager;
    @Mock private WaitlistDao mockWaitlistDao;
    @Mock private VacancyDao mockVacancyDao;
    @Mock private VacancyClientMatchDao mockVacancyClientMatchDao;
    @Mock private EFormRenderApprovalService mockRenderApprovalService;
    @Mock private DemographicManager mockDemographicManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoMocks = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(EformDataManager.class, mockEformDataManager);
        registerMock(DocumentAttachmentManager.class, mockDocumentAttachmentManager);
        // AddEForm2Action's constructor resolves this via SpringUtils regardless of the path taken.
        registerMock(EmailManager.class, mockEmailManager);
        // Reached through the template-write path this test exists to exercise.
        registerMock(WaitlistDao.class, mockWaitlistDao);
        registerMock(VacancyDao.class, mockVacancyDao);
        registerMock(VacancyClientMatchDao.class, mockVacancyClientMatchDao);
        registerMock(DemographicManager.class, mockDemographicManager);
        // Resolved by offerEDocApproval when the render is refused.
        registerMock(EFormRenderApprovalService.class, mockRenderApprovalService);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("doc1");

        eFormUtilMock = mockStatic(EFormUtil.class);
        HashMap<String, Object> eformData = new HashMap<>();
        eformData.put("formName", "TestForm");
        eformData.put("formHtml", "");
        eformData.put("formSubject", "");
        eformData.put("formDate", "");
        eformData.put("formFileName", "test.html");
        eformData.put("formCreator", "doc1");
        eFormUtilMock.when(() -> EFormUtil.loadEForm(anyString())).thenReturn(eformData);
        eFormUtilMock.when(() -> EFormUtil.addEFormValues(any(), any(), anyInt(), anyInt(), anyInt()))
                .then(invocation -> null);

        eFormLoaderMock = mockStatic(EFormLoader.class);
        eFormLoaderMock.when(EFormLoader::getInstance).thenReturn(mock(EFormLoader.class));
        eFormLoaderMock.when(EFormLoader::getOpener).thenReturn("oscarOPEN=");

        // EctProgram resolves its manager from the SERVLET CONTEXT, not SpringUtils, so
        // registerMock cannot reach it and an unmocked lookup NPEs on the mock request's context.
        // An empty program list makes getProgram(...) return "0" without touching a database.
        EctProgramManager programManager = mock(EctProgramManager.class);
        when(programManager.getProgramBeans(anyString(), isNull())).thenReturn(java.util.List.of());
        WebApplicationContext applicationContext = mock(WebApplicationContext.class);
        when(applicationContext.getBean("ectProgramManager")).thenReturn(programManager);
        webApplicationContextUtilsMock = mockStatic(WebApplicationContextUtils.class);
        webApplicationContextUtilsMock
                .when(() -> WebApplicationContextUtils.getWebApplicationContext(any()))
                .thenReturn(applicationContext);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockEformDataManager.saveEformData(any(LoggedInInfo.class), any())).thenReturn(42);
        // execute() feeds the demographic to MatchManager after the template write; a null one NPEs
        // there, which would mask the assertion this test is actually making.
        Demographic client = new Demographic();
        client.setDemographicNo(123);
        when(mockDemographicManager.getDemographic(any(LoggedInInfo.class), anyString())).thenReturn(client);

        mockRequest.setParameter("efmfid", "1");
        mockRequest.setParameter("efmdemographic_no", "123");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (webApplicationContextUtilsMock != null) webApplicationContextUtilsMock.close();
        if (eFormLoaderMock != null) eFormLoaderMock.close();
        if (eFormUtilMock != null) eFormUtilMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoMocks != null) mockitoMocks.close();
    }

    private void verifyTemplateWritten(boolean expected) {
        eFormUtilMock.verify(
                () -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), anyString(), anyString(), anyString()),
                expected ? org.mockito.Mockito.times(1) : org.mockito.Mockito.never());
    }

    @Test
    @DisplayName("should still write the eForm template when the eDoc render is refused")
    void shouldStillWriteTemplate_whenEdocRenderRefused() throws Exception {
        mockRequest.setParameter("saveAsEdoc", "true");
        doThrow(new EformContentUnavailableException("incomplete", 42,
                new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false, false)))
                .when(mockDocumentAttachmentManager).saveEFormAsEDoc(any(), any());

        AddEForm2Action action = new AddEForm2Action();
        String result = action.execute();

        // The refusal is still offered for approval...
        assertThat(result).isEqualTo("missingContent");
        // ...and the chart notes were written before that return, not skipped by it.
        verifyTemplateWritten(true);
    }

    // There is deliberately no separate "plain save writes the template" test. execute() continues
    // past the write into MatchManager's client matching, which needs real matcher data and NPEs
    // without it — mocking that chain would test PMmodule, not this contract. The eDoc test above
    // already covers the positive case: if the write never ran at all, it would fail.

    @Test
    @DisplayName("should not write the eForm template on the print path")
    void shouldNotWriteTemplate_onPrintPath() throws Exception {
        // Guards the hoist: the write moved above the eDoc block, and it must still be skipped for
        // the workflow paths that never performed it. writeEformTemplate is not idempotent, so
        // running it here would duplicate every CPP note, tickler and consult request.
        // print=true is the legacy printControl.js alias of the save-and-download workflow, so the
        // path now renders and returns the mapped "download" result instead of an unmapped "print".
        mockRequest.setParameter("print", "true");
        when(mockDocumentAttachmentManager.renderEFormPacketWithCompleteness(any(), any(), isNull()))
                .thenReturn(new EformDataManager.EformPdfRender(java.nio.file.Path.of("letter.pdf"),
                        new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, false, false, false)));
        when(mockDocumentAttachmentManager.convertPDFToBase64(any())).thenReturn("JVBERi0=");

        AddEForm2Action action = new AddEForm2Action();
        String result = action.execute();

        assertThat(result).isEqualTo("download");
        verifyTemplateWritten(false);
    }
}
