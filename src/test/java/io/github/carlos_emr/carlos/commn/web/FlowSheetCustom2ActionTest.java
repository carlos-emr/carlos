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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.FlowSheetCustomizationDao;
import io.github.carlos_emr.carlos.commn.dao.FlowSheetUserCreatedDao;
import io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization;
import io.github.carlos_emr.carlos.commn.model.FlowSheetUserCreated;
import io.github.carlos_emr.carlos.commn.service.FlowSheetCustomizationService;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP method and privilege contract tests for flowsheet customization writes.
 *
 * @since 2026-05-05
 */
@DisplayName("FlowSheetCustom2Action - HTTP method and privilege contract tests")
@Tag("integration")
@Tag("clinical")
class FlowSheetCustom2ActionTest extends CarlosWebTestBase {

    @Mock
    private FlowSheetCustomizationService mockFlowSheetCustomizationService;
    @Mock
    private FlowSheetCustomizationDao mockFlowSheetCustomizationDao;
    @Mock
    private FlowSheetUserCreatedDao mockFlowSheetUserCreatedDao;

    @BeforeEach
    void setUpAction() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        replaceSpringUtilsBean(FlowSheetCustomizationService.class, mockFlowSheetCustomizationService);
        replaceSpringUtilsBean(FlowSheetCustomizationDao.class, mockFlowSheetCustomizationDao);
        replaceSpringUtilsBean(FlowSheetUserCreatedDao.class, mockFlowSheetUserCreatedDao);
        mockRequest.setMethod("POST");
        mockSession.setAttribute("user", "999998");
    }

    @Test
    @DisplayName("should deny when _flowsheet write privilege is missing")
    void shouldThrowException_whenFlowsheetWriteMissing() {
        assertThatThrownBy(() -> executeAction(new FlowSheetCustom2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_flowsheet");
    }

    @Test
    @DisplayName("should return 405 on GET before dispatching mutator methods")
    void shouldReturn405_onGetBeforePrivilegeCheck() throws Exception {
        mockRequest.setMethod("GET");
        addRequestParameter("method", "save");
        TestableFlowSheetCustom2Action action = new TestableFlowSheetCustom2Action();
        clearInvocations(mockSecurityInfoManager);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        assertThat(mockResponse.isCommitted()).isTrue();
        assertThat(action.saveCalled).isFalse();
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should return 405 on HEAD before dispatching mutator methods")
    void shouldReturn405_onHead() throws Exception {
        mockRequest.setMethod("HEAD");
        addRequestParameter("method", "save");
        TestableFlowSheetCustom2Action action = new TestableFlowSheetCustom2Action();
        clearInvocations(mockSecurityInfoManager);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        assertThat(mockResponse.isCommitted()).isTrue();
        assertThat(action.saveCalled).isFalse();
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should dispatch save on POST when _flowsheet w is granted")
    void shouldDispatchSave_whenFlowsheetWriteGranted() throws Exception {
        allowPrivilege("_flowsheet", "w");
        addRequestParameter("method", "save");
        TestableFlowSheetCustom2Action action = new TestableFlowSheetCustom2Action();

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.saveCalled).isTrue();
    }

    @Test
    @DisplayName("should return ERROR on unknown POST method without mutator dispatch")
    void shouldReturnError_onUnknownPostMethod() throws Exception {
        allowPrivilege("_flowsheet", "w");
        addRequestParameter("method", "unknownMethod");
        TestableFlowSheetCustom2Action action = new TestableFlowSheetCustom2Action();

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        assertThat(action.saveCalled).isFalse();
        assertThat(mockRequest.getAttribute("errorMessage")).isEqualTo("Unknown flowsheet customization method.");
    }

    @Test
    @DisplayName("should return ERROR when POST method is missing")
    void shouldReturnError_whenPostMethodMissing() throws Exception {
        allowPrivilege("_flowsheet", "w");
        TestableFlowSheetCustom2Action action = new TestableFlowSheetCustom2Action();

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        assertThat(action.saveCalled).isFalse();
        assertThat(mockRequest.getAttribute("errorMessage")).isEqualTo("Unknown flowsheet customization method.");
    }

    @Test
    @DisplayName("should reject save when measurement parameter is missing")
    void shouldReturnError_whenSaveMeasurementMissing() throws Exception {
        allowPrivilege("_flowsheet", "w");
        allowPrivilege("_demographic", "w");
        addRequestParameter("method", "save");
        addRequestParameter("flowsheet", "diabetes");
        addRequestParameter("demographic", "1");

        String result = executeAction(new FlowSheetCustom2Action());

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        assertThat(mockRequest.getAttribute("errorMessage"))
                .isEqualTo("Measurement is required to save a flowsheet customization.");
        verify(mockFlowSheetCustomizationDao, never()).persist(any(FlowSheetCustomization.class));
    }

    @Test
    @DisplayName("should deny createNewFlowSheet when demographic write is missing")
    void shouldDenyCreateNewFlowSheet_whenDemographicWriteMissing() {
        allowPrivilege("_flowsheet", "w");
        addRequestParameter("method", "createNewFlowSheet");
        addRequestParameter("demographic", "0");

        assertThatThrownBy(() -> executeAction(new FlowSheetCustom2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_demographic");
        verify(mockFlowSheetCustomizationService, never()).validateScopePermission(any(LoggedInInfo.class), anyString());
        verify(mockFlowSheetUserCreatedDao, never()).persist(any(FlowSheetUserCreated.class));
    }

    @Test
    @DisplayName("should validate scope before createNewFlowSheet persists")
    void shouldValidateScope_whenCreateNewFlowSheet() throws Exception {
        allowPrivilege("_flowsheet", "w");
        allowPrivilege("_demographic", "w");
        addRequestParameter("method", "createNewFlowSheet");
        addRequestParameter("demographic", "0");
        addRequestParameter("scope", "clinic");
        addRequestParameter("displayName", "Clinic Diabetes Flow");
        addRequestParameter("dxcodeTriggers", "250");
        addRequestParameter("warningColour", "yellow");
        addRequestParameter("recommendationColour", "green");

        String result = executeAction(new TestableCreateNewFlowSheetAction());

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockFlowSheetCustomizationService).validateScopePermission(any(LoggedInInfo.class), eq("clinic"));
        verify(mockFlowSheetUserCreatedDao).persist(any(FlowSheetUserCreated.class));
    }

    private static final class TestableFlowSheetCustom2Action extends FlowSheetCustom2Action {
        private boolean saveCalled;

        @Override
        public String save() {
            saveCalled = true;
            return SUCCESS;
        }
    }

    private static final class TestableCreateNewFlowSheetAction extends FlowSheetCustom2Action {
        @Override
        protected String addFlowSheetToTemplateConfig(
                String dxcodeTriggers,
                String displayName,
                String warningColour,
                String recommendationColour) {
            return "clinic_diabetes_flow";
        }
    }
}
