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
package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.Icd10Dao;
import io.github.carlos_emr.carlos.commn.dao.Icd9Dao;
import io.github.carlos_emr.carlos.commn.model.Icd9;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DxResearch2Action Unit Tests")
@Tag("unit")
@Tag("dxresearch")
class DxResearch2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    @Mock private DxresearchDAO mockDxresearchDao;
    @Mock private Icd9Dao mockIcd9Dao;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private dxResearch2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DxresearchDAO.class, mockDxresearchDao);
        registerMock(Icd9Dao.class, mockIcd9Dao);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_dxresearch"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(mockDxresearchDao.findByDemographicNoResearchCodeAndCodingSystem(any(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mockIcd9Dao.findByCodingSystem(anyString())).thenReturn(null);
        when(mockIcd9Dao.findByCode(anyString())).thenReturn(null);

        action = new dxResearch2Action() {
            @Override
            public String getText(String key, String[] args) {
                return "code not found";
            }
        };
        action.setSelectedCodingSystem("icd9");
        action.setDemographicNo("12345");
        action.setProviderNo("999998");
        action.setForward("");
        action.setXml_research1("NOT-A-REAL-CODE");
        action.setXml_research2("");
        action.setXml_research3("");
        action.setXml_research4("");
        action.setXml_research5("");
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should send 405 on GET")
    void shouldSend405_onGet() throws Exception {
        mockRequest.setMethod("GET");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("should return failure and expose action errors when code validation fails")
    void shouldReturnFailureAndExposeActionErrors_whenCodeValidationFails() throws Exception {
        String result = action.execute();

        assertThat(result).isEqualTo("failure");
        assertThat(mockResponse.getRedirectedUrl()).isNull();
        Object actionErrors = mockRequest.getAttribute("actionErrors");
        assertThat(actionErrors).isInstanceOf(Collection.class);
        assertThat((Collection<?>) actionErrors).isNotEmpty();
    }

    /** An action whose messages read "key[arg, ...]", so a test can see which message was chosen. */
    private dxResearch2Action actionShowingMessageKeys(String codingSystem, String code) {
        dxResearch2Action keyed = new dxResearch2Action() {
            @Override
            public String getText(String key, String[] args) {
                return key + Arrays.toString(args);
            }
        };
        keyed.setSelectedCodingSystem(codingSystem);
        keyed.setDemographicNo("12345");
        keyed.setProviderNo("999998");
        keyed.setForward("");
        keyed.setXml_research1(code);
        keyed.setXml_research2("");
        keyed.setXml_research3("");
        keyed.setXml_research4("");
        keyed.setXml_research5("");
        return keyed;
    }

    @Test
    @DisplayName("should suggest the ICD-9 code without its decimal point when that code exists, and add nothing")
    void shouldSuggestCodeWithoutDecimal_whenIcd9CodeHasDecimal() throws Exception {
        when(mockIcd9Dao.findByCode("1519")).thenReturn(new Icd9());
        dxResearch2Action keyed = actionShowingMessageKeys("icd9", "151.9");

        assertThat(keyed.execute()).isEqualTo("failure");

        assertThat(keyed.getActionErrors())
                .containsExactly("oscarResearch.oscarDxResearch.error.icd9DidYouMean[151.9, 1519]");
        verify(mockDxresearchDao, never()).persist(any());
    }

    @Test
    @DisplayName("should explain the decimal point when the ICD-9 code is unknown with or without it")
    void shouldExplainDecimalPoint_whenIcd9CodeWithoutDecimalIsUnknown() throws Exception {
        dxResearch2Action keyed = actionShowingMessageKeys("icd9", "999.9");

        assertThat(keyed.execute()).isEqualTo("failure");

        assertThat(keyed.getActionErrors())
                .containsExactly("oscarResearch.oscarDxResearch.error.icd9WithDecimal[999.9]");
        verify(mockIcd9Dao).findByCode("9999");
    }

    @Test
    @DisplayName("should explain the decimal point, and not look up an empty code, when the code is only a point")
    void shouldExplainDecimalPoint_whenIcd9CodeIsOnlyAPoint() throws Exception {
        dxResearch2Action keyed = actionShowingMessageKeys("icd9", ".");

        assertThat(keyed.execute()).isEqualTo("failure");

        assertThat(keyed.getActionErrors())
                .containsExactly("oscarResearch.oscarDxResearch.error.icd9WithDecimal[.]");
        verify(mockIcd9Dao, never()).findByCode("");
    }

    @Test
    @DisplayName("should keep the general message for an unknown ICD-9 code typed without a decimal point")
    void shouldUseGeneralMessage_whenIcd9CodeHasNoDecimal() throws Exception {
        dxResearch2Action keyed = actionShowingMessageKeys("icd9", "9999");

        assertThat(keyed.execute()).isEqualTo("failure");

        assertThat(keyed.getActionErrors()).containsExactly("errors.codeNotFound[9999, icd9]");
    }

    @Test
    @DisplayName("should keep the general message for other coding systems (the decimal-point help is ICD-9 only)")
    void shouldUseGeneralMessage_whenCodingSystemIsNotIcd9() throws Exception {
        Icd10Dao icd10Dao = mock(Icd10Dao.class);
        registerMock(Icd10Dao.class, icd10Dao);
        dxResearch2Action keyed = actionShowingMessageKeys("icd10", "A00.1");

        assertThat(keyed.execute()).isEqualTo("failure");

        assertThat(keyed.getActionErrors()).containsExactly("errors.codeNotFound[A00.1, icd10]");
        verify(icd10Dao, never()).findByCode("A001");
    }
}
