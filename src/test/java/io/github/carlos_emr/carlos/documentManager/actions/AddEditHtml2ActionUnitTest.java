/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.data.AddEditDocument2Form;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddEditHtml2Action}'s lowercase parameter alias.
 *
 * <p>The Add-Link and Add-HTML forms carry the same case-variant duplicate that orphaned eDocs
 * uploads: they post both {@code functionId} and {@code functionid}, Struts 7's case-insensitive
 * {@code HttpParameters} collapses the pair into the lowercase key, and the case-sensitive
 * {@code @StrutsParameter} lookup then matched no member and dropped the patient id — filing the
 * document against no chart at all.
 *
 * <p>{@link AddEditDocument2Action} got both the fix and a test; this class got the fix only, so
 * deleting its alias re-orphaned link and HTML documents with every check still green.
 *
 * @since 2026-08-30
 */
@DisplayName("AddEditHtml2Action parameter binding")
@Tag("unit")
@Tag("document")
class AddEditHtml2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void setUp() {
        // The action resolves SecurityInfoManager and the servlet request/response in field
        // initializers, so both have to be in place before the constructor runs.
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should bind lowercase functionid to the same property as functionId")
    void shouldBindLowercaseFunctionid_toSameProperty() throws NoSuchMethodException {
        AddEditHtml2Action action = new AddEditHtml2Action();

        action.setFunctionid("42");

        assertThat(action.getFunctionId()).isEqualTo("42");

        // Delegation alone is not enough: Struts binds a request parameter only through a setter
        // carrying @StrutsParameter, so removing just the annotation reopens the defect while
        // leaving the delegation assertion above green. Pin the annotation too.
        assertThat(AddEditHtml2Action.class.getMethod("setFunctionid", String.class)
                .isAnnotationPresent(StrutsParameter.class))
                .as("setFunctionid must be @StrutsParameter-annotated or Struts will not bind it")
                .isTrue();
    }

    @Test
    @DisplayName("should preserve every submitted field the retry form re-renders")
    void shouldPreserveSubmittedMetadata_whenBuildingRetryForm() {
        AddEditHtml2Action action = new AddEditHtml2Action();
        action.setFunction("demographic");
        action.setFunctionId("42");
        action.setDocType("Lab");
        action.setDocClass("Consultant Report");
        action.setDocSubClass("Cardiology");
        action.setDocDesc("Echocardiogram");
        action.setDocCreator("101");
        action.setResponsibleId("202");
        action.setSource("Referring MD");
        action.setSourceFacility("General Hospital");
        action.setObservationDate("2026/08/30");
        action.setContentDateTime("2026/08/30 09:15:00");
        action.setDocPublic("checked");
        action.setReviewerId("303");
        action.setReviewDateTime("2026-08-31 10:00:00");
        action.setHtml("<p>report body</p>");

        AddEditDocument2Form retry = action.submittedForm();

        // addedithtmldocument.jsp rebuilds each of these as a visible or hidden form input on the
        // validation-retry render, so a field dropped from the retry bean is re-POSTed blank and
        // then persisted over the eDoc's real creator/date/source/classification/visibility.
        // Assert the whole set, not just the field the user was fixing.
        assertThat(retry.getFunction()).isEqualTo("demographic");
        assertThat(retry.getFunctionId()).isEqualTo("42");
        assertThat(retry.getDocType()).isEqualTo("Lab");
        assertThat(retry.getDocClass()).isEqualTo("Consultant Report");
        assertThat(retry.getDocSubClass()).isEqualTo("Cardiology");
        assertThat(retry.getDocDesc()).isEqualTo("Echocardiogram");
        assertThat(retry.getDocCreator()).isEqualTo("101");
        assertThat(retry.getResponsibleId()).isEqualTo("202");
        assertThat(retry.getSource()).isEqualTo("Referring MD");
        assertThat(retry.getSourceFacility()).isEqualTo("General Hospital");
        assertThat(retry.getObservationDate()).isEqualTo("2026/08/30");
        assertThat(retry.getContentDateTime()).isEqualTo("2026/08/30 09:15:00");
        assertThat(retry.getDocPublic()).isEqualTo("checked");
        assertThat(retry.getReviewerId()).isEqualTo("303");
        assertThat(retry.getReviewDateTime()).isEqualTo("2026-08-31 10:00:00");
        assertThat(retry.getHtml()).isEqualTo("<p>report body</p>");
    }

    @Test
    @DisplayName("should keep scheduleNav on the add-link redirect")
    void shouldKeepScheduleNav_whenAddLinkSucceedsInScheduleShell() {
        request.addParameter("scheduleNav", "1");

        assertThat(addLinkAndCaptureRedirect()).contains("scheduleNav=1");
    }

    @Test
    @DisplayName("should not add scheduleNav to the add-link redirect outside the schedule shell")
    void shouldOmitScheduleNav_whenAddLinkSucceedsOutsideScheduleShell() {
        assertThat(addLinkAndCaptureRedirect()).doesNotContain("scheduleNav");
    }

    /** Drives one successful Add Link through {@code execute()} and returns the redirect it sent. */
    private String addLinkAndCaptureRedirect() {
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), isNull())).thenReturn(true);
        request.addParameter("function", "demographic");
        request.addParameter("functionid", "42");

        ProgramManager2 programManager = mock(ProgramManager2.class);
        registerMock(ProgramManager2.class, programManager);

        AddEditHtml2Action action = new AddEditHtml2Action();
        action.setMode("addLink");
        action.setFunction("demographic");
        action.setFunctionId("42");
        action.setDocType("Lab");
        action.setDocDesc("Reference site");
        action.setDocCreator("999998");
        action.setResponsibleId("999998");
        action.setObservationDate("2026-09-06");
        action.setHtml("http://example.invalid/report");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class)) {
            LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);
            eDocUtilMock.when(() -> EDocUtil.getDoctypes("demographic"))
                    .thenReturn(new ArrayList<>(List.of("Lab")));
            eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenReturn("777");

            assertThat(action.execute()).isEqualTo(AddEditHtml2Action.NONE);
            return response.getRedirectedUrl();
        }
    }
}
