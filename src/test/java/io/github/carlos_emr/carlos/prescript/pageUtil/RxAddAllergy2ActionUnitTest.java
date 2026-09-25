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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RxAddAllergy2Action}, focused on the "archive old allergy"
 * branch that regressed to an IDOR (issue #2467): the audit log must only record
 * an archive when {@link RxPatientData.Patient#deleteAllergy(int)} actually
 * archived a record owned by the session patient.
 *
 * @since 2026-07-06
 */
@DisplayName("RxAddAllergy2Action Unit Tests")
@Tag("unit")
@Tag("rx")
class RxAddAllergy2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private RxPatientData.Patient mockRxPatient;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private RxAddAllergy2Action action;

    @BeforeEach
    void setUp() {
        // Bootstrap mock in case this is the first time RxPatientData.Patient is
        // initialized in this JVM/fork: its <clinit> resolves AllergyDao via
        // SpringUtils.getBean, and an unmocked failure here permanently poisons
        // the class (NoClassDefFoundError) for every other test in the same fork.
        registerMock(AllergyDao.class, mock(AllergyDao.class));

        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("w"), isNull()))
                .thenReturn(true);
        // Patient-level Rx access (the shared Rx write check, #3908) is granted unless a test denies it.
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provider1");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        mockRequest.setParameter("type", "1");
        mockRequest.setParameter("startDate", "");
        mockRequest.setParameter("formDemographicNo", "123");
        // The request names its patient; the write resolver refuses one that does not (#3875).
        mockRequest.setParameter("demographicNo", "123");
        openRxForPatient();

        action = new RxAddAllergy2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should throw SecurityException when missing _allergy privilege")
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_allergy");
        verify(mockRxPatient, never()).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should reject a non-POST request before adding an allergy")
    void shouldRejectAdd_whenRequestMethodIsNotPost() throws Exception {
        mockRequest.setMethod("GET");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a lower-case post request before adding an allergy")
    void shouldRejectAdd_whenRequestMethodIsLowerCasePost() throws Exception {
        mockRequest.setMethod("post");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a missing rendered patient context before adding an allergy")
    void shouldRejectAdd_whenFormDemographicNoIsMissing() throws Exception {
        mockRequest.removeParameter("formDemographicNo");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a missing session patient before adding an allergy")
    void shouldRejectAdd_whenSessionPatientIsMissing() throws Exception {
        // Rx/allergies was never opened for the form's patient in this session.
        mockRequest.getSession().removeAttribute(RxSessionBeanResolver.BEANS_ATTRIBUTE);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a stale rendered patient context before adding an allergy")
    void shouldRejectAdd_whenFormDemographicNoDiffersFromSessionPatient() throws Exception {
        mockRequest.setParameter("formDemographicNo", "456");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse when the request names one open patient and the form carries another")
    void shouldRejectAdd_whenRequestPatientDiffersFromFormPatient() throws Exception {
        // Both patients have Rx open: the old code wrote to whichever formDemographicNo named.
        RxSessionBean otherBean = new RxSessionBean();
        otherBean.setDemographicNo(456);
        RxSessionBeanResolver.register(mockRequest.getSession(), otherBean);
        mockRequest.setParameter("demographicNo", "123");
        mockRequest.setParameter("formDemographicNo", "456");
        mockRequest.setParameter("allergyToArchive", "42");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        assertThat(action.getDemographicNo()).isZero();
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse when the form names an open patient but the request names none")
    void shouldRejectAdd_whenRequestNamesNoPatient() throws Exception {
        mockRequest.removeParameter("demographicNo");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a malformed rendered patient context before adding an allergy")
    void shouldRejectAdd_whenFormDemographicNoIsMalformed() throws Exception {
        mockRequest.setParameter("formDemographicNo", "not-a-number");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should add allergy and log ADD when no prior allergy is archived")
    void shouldAddAllergyAndLogAdd_whenNoAllergyToArchive() throws Exception {
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        // The success redirect returns to this patient's allergy page.
        assertThat(action.getDemographicNo()).isEqualTo(123);
        verify(mockRxPatient).addAllergy(any(), any());
        logActionMock.verify(() -> LogAction.addLog(
                eq("provider1"), eq(LogConst.ADD), eq(LogConst.CON_ALLERGY),
                any(String.class), any(String.class), eq("123"), any(String.class)));
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
    }

    @Test
    @DisplayName("should reject a stale or foreign allergy edit before adding its replacement")
    void shouldRejectEdit_whenAllergyDoesNotBelongToPatient() throws Exception {
        mockRequest.setParameter("allergyToArchive", "42");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should log archive when archived allergy belongs to the session patient")
    void shouldLogArchive_whenAllergyBelongsToSessionPatient() throws Exception {
        mockRequest.setParameter("allergyToArchive", "42");
        when(mockRxPatient.getAllergy(42)).thenReturn(new Allergy());
        when(mockRxPatient.deleteAllergy(42)).thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockRxPatient).deleteAllergy(42);
        logActionMock.verify(() -> LogAction.addLog(
                eq("provider1"), eq(LogConst.ARCHIVE), eq(LogConst.CON_ALLERGY),
                eq("42"), any(String.class), eq("123"), isNull()));
    }

    @Test
    @DisplayName("should only audit archival when the previously validated allergy was archived")
    void shouldNotAuditArchive_whenArchiveFailsAfterValidation() throws Exception {
        mockRequest.setParameter("allergyToArchive", "42");
        when(mockRxPatient.getAllergy(42)).thenReturn(new Allergy());
        when(mockRxPatient.deleteAllergy(42)).thenReturn(false);

        action.execute();

        logActionMock.verify(() -> LogAction.addLog(
                any(String.class), eq(LogConst.ARCHIVE), any(String.class),
                any(String.class), any(String.class), any(String.class), any()), never());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "2147483648"})
    @DisplayName("should reject a malformed allergy edit before adding its replacement")
    void shouldRejectEdit_whenAllergyToArchiveIsMalformed(String archiveId) throws Exception {
        mockRequest.setParameter("allergyToArchive", archiveId);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    /**
     * Opens Rx for patient 123 in this session (per-patient Rx state, #3875) and seeds the
     * resolver's per-request patient cache with the mock, so no demographic lookup runs.
     */
    private void openRxForPatient() {
        RxSessionBean rxBean = new RxSessionBean();
        rxBean.setDemographicNo(123);
        RxSessionBeanResolver.register(mockRequest.getSession(), rxBean);
        when(mockRxPatient.getDemographicNo()).thenReturn(123);
        mockRequest.setAttribute(RxSessionBeanResolver.PATIENT_REQUEST_ATTRIBUTE, mockRxPatient);
    }

}
