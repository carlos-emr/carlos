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
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.ArgumentMatchers.anyList;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RxShowAllergy2Action}.
 *
 * @since 2026-05-29
 */
@DisplayName("RxShowAllergy2Action Unit Tests")
@Tag("unit")
@Tag("rx")
class RxShowAllergy2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private AllergyDao mockAllergyDao;

    @Mock
    private SystemPreferencesDao mockSystemPreferencesDao;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private RxShowAllergy2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(AllergyDao.class, mockAllergyDao);
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(SystemPreferencesDao.class, mockSystemPreferencesDao);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("u"), isNull()))
                .thenReturn(false);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        action = new RxShowAllergy2Action();
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
    @DisplayName("should reject reorder when only read allergy privilege is granted")
    void shouldRejectReorder_whenOnlyReadAllergyPrivilegeIsGranted() {
        mockRequest.setParameter("method", "reorder");
        mockRequest.setParameter("demographicNo", "123");
        mockRequest.setParameter("allergyId", "456");
        mockRequest.setParameter("direction", "up");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_allergy");

        verify(mockSecurityInfoManager, atLeastOnce()).hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("r"), isNull());
        verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("u"), isNull());
        verify(mockAllergyDao, never()).merge(any(AbstractModel.class));
    }
    private Allergy allergy(String name, String severity) {
        Allergy allergy = new Allergy();
        allergy.setId(name.hashCode());
        allergy.setDescription(name);
        allergy.setReaction("test reaction");
        allergy.setSeverityOfReaction(severity);
        return allergy;
    }

    @Test
    @DisplayName("should expose unresolved allergies alongside confirmed matches")
    void shouldExposeUnresolvedAllergies_whenDrugRefCannotResolveAName() throws Exception {
        Allergy matched = allergy("PENICILLINS", "3");
        Allergy unresolved = allergy("MACROLIDES", "1");
        JsonNode json = check(new Allergy[]{matched, unresolved}, new Allergy[]{matched}, List.of(unresolved), false, false);
        assertThat(json.path("results").get(0).path("DESCRIPTION").asText()).isEqualTo("PENICILLINS");
        assertThat(json.path("unchecked").get(0).path("DESCRIPTION").asText()).isEqualTo("MACROLIDES");
        assertThat(json.path("checkComplete").asBoolean()).isFalse();
        assertThat(mockResponse.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    @DisplayName("should preserve unresolved allergies when only the highest severity match is requested")
    void shouldPreserveUnresolvedAllergies_whenHighestSeverityOnlyIsEnabled() throws Exception {
        Allergy mild = allergy("mild", "1");
        Allergy severe = allergy("severe", "3");
        Allergy unresolved = allergy("unresolved", null);
        Allergy noReaction = allergy("none", "5");
        JsonNode json = check(new Allergy[]{mild, severe, noReaction, unresolved}, new Allergy[]{mild, severe, noReaction}, List.of(unresolved), true, false);
        assertThat(json.path("results")).hasSize(1);
        assertThat(json.path("results").get(0).path("DESCRIPTION").asText()).isEqualTo("severe");
        assertThat(json.path("unchecked")).hasSize(1);
    }

    @Test
    @DisplayName("should report failed checks rather than an empty all-clear result")
    void shouldReportFailedChecks_whenDrugRefThrows() throws Exception {
        JsonNode json = check(new Allergy[]{allergy("PENICILLINS", "3")}, new Allergy[0], List.of(), false, true);
        assertThat(json.path("checkFailed").asBoolean()).isTrue();
        assertThat(json.path("checkComplete").asBoolean()).isFalse();
        assertThat(json.path("unchecked")).hasSize(1);
    }

    @Test
    @DisplayName("should identify a complete negative check when every allergy was compared")
    void shouldIdentifyCompleteNegativeCheck_whenNoAllergiesMatch() throws Exception {
        JsonNode json = check(new Allergy[]{allergy("unrelated", "1")}, new Allergy[0], List.of(), false, false);
        assertThat(json.path("checkComplete").asBoolean()).isTrue();
        assertThat(json.path("results")).isEmpty();
        assertThat(json.path("unchecked")).isEmpty();
    }

    private JsonNode check(Allergy[] allergies, Allergy[] matches, List<Allergy> unresolved,
                           boolean highestOnly, boolean failed) throws Exception {
        mockRequest.setParameter("method", "allergyData");
        mockRequest.setParameter("atcCode", "J01FA09");
        mockRequest.setParameter("id", "7");
        RxSessionBean session = mock(RxSessionBean.class);
        when(session.getDemographicNo()).thenReturn(2);
        mockRequest.getSession().setAttribute("RxSessionBean", session);
        RxPatientData.Patient patient = mock(RxPatientData.Patient.class);
        when(patient.getActiveAllergies()).thenReturn(allergies);
        when(mockSystemPreferencesDao.isReadBooleanPreference(any())).thenReturn(highestOnly);
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("rx.disable_allergy_warnings", "false")).thenReturn("false");
        try (MockedStatic<CarlosProperties> propertyMock = mockStatic(CarlosProperties.class);
             MockedStatic<RxPatientData> patients = mockStatic(RxPatientData.class);
             MockedConstruction<RxDrugData> drugs = mockConstruction(RxDrugData.class, (mock, context) -> {
                 when(mock.getAllergyWarnings(eq("J01FA09"), eq(allergies), anyList())).thenAnswer(invocation -> {
                     if (failed) throw new IllegalStateException("reference unavailable");
                     invocation.<List<Allergy>>getArgument(2).addAll(unresolved);
                     return matches;
                 });
             })) {
            propertyMock.when(CarlosProperties::getInstance).thenReturn(properties);
            patients.when(() -> RxPatientData.getPatient(mockLoggedInInfo, 2)).thenReturn(patient);
            action.execute();
            return new ObjectMapper().readTree(mockResponse.getContentAsByteArray());
        }
    }

}
