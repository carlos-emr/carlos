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
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@DisplayName("RxSearchPatient2Action Tests")
@Tag("unit")
@Tag("prescript")
class RxSearchPatient2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<RxPatientData> patientDataMock;
    private AutoCloseable mocks;
    private final Map<String, Object> requestAttributes = new HashMap<>();

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private HttpServletRequest mockRequest;

    private RxSearchPatient2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        // Static class initialization still needs these beans when this suite runs first.
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(AllergyDao.class, mock(AllergyDao.class));
        // Isolate the action from RxPatientData's application-lifetime static bean cache.
        // Other action suites may initialize that cache with their own mock manager.
        patientDataMock = mockStatic(RxPatientData.class);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
        doAnswer(invocation -> {
            requestAttributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(mockRequest).setAttribute(anyString(), any());

        action = new RxSearchPatient2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (patientDataMock != null) {
            patientDataMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void shouldRejectBlankSurnameSearchWithoutCallingPatientLookup() {
        when(mockRequest.getParameter("surname")).thenReturn("   ");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_PERFORMED)).isEqualTo(Boolean.TRUE);
        assertThat(requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_SURNAME)).isEqualTo("");
        assertThat((RxPatientData.Patient[]) requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_RESULTS)).isEmpty();
        patientDataMock.verifyNoInteractions();
    }

    @Test
    void shouldPopulateSearchResultsForMatchingSurname() {
        RxPatientData.Patient first = mock(RxPatientData.Patient.class);
        RxPatientData.Patient second = mock(RxPatientData.Patient.class);
        when(mockRequest.getParameter("surname")).thenReturn(" Smith ");
        patientDataMock.when(() -> RxPatientData.PatientSearch(mockLoggedInInfo, "Smith", ""))
                .thenReturn(new RxPatientData.Patient[] {first, second});

        String result = action.execute();
        RxPatientData.Patient[] results =
                (RxPatientData.Patient[]) requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_RESULTS);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_PERFORMED)).isEqualTo(Boolean.TRUE);
        assertThat(requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_SURNAME)).isEqualTo("Smith");
        assertThat(results).containsExactly(first, second);
        patientDataMock.verify(() -> RxPatientData.PatientSearch(mockLoggedInInfo, "Smith", ""));
    }

    @Test
    void shouldExposeEmptyResultsForNoMatches() {
        when(mockRequest.getParameter("surname")).thenReturn("NoMatches");
        patientDataMock.when(() -> RxPatientData.PatientSearch(mockLoggedInInfo, "NoMatches", ""))
                .thenReturn(new RxPatientData.Patient[0]);

        String result = action.execute();
        RxPatientData.Patient[] results =
                (RxPatientData.Patient[]) requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_RESULTS);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_PERFORMED)).isEqualTo(Boolean.TRUE);
        assertThat(requestAttributes.get(RxSearchPatient2Action.ATTR_SEARCH_SURNAME)).isEqualTo("NoMatches");
        assertThat(results).isEmpty();
    }

    @Test
    void shouldThrowWhenDemographicReadPrivilegeDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("_demographic");
        patientDataMock.verifyNoInteractions();
    }
}
