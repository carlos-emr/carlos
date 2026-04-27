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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingDxCodeDataAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigSearchAjaxViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigSearchAjaxViewModel.Suggestion;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewBillingDigSearchAjax2Action} — the ICD-9
 * dx-code autocomplete JSON endpoint that absorbed the former
 * {@code billingDigSearchAjax.jsp} controller-in-a-JSP.
 *
 * @since 2026-04-26
 */
@DisplayName("ViewBillingDigSearchAjax2Action")
@Tag("unit")
@Tag("billing")
class ViewBillingDigSearchAjax2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private BillingDxCodeDataAssembler mockAssembler;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private ViewBillingDigSearchAjax2Action newAction() {
        return new ViewBillingDigSearchAjax2Action(mockSecurityInfoManager, mockAssembler);
    }

    @Test
    void shouldWriteJsonArray_forEachSuggestion() throws Exception {
        BillingDigSearchAjaxViewModel model = BillingDigSearchAjaxViewModel.builder()
                .suggestions(Arrays.asList(
                        new Suggestion("401", "401 - Hypertension", "401", "Hypertension"),
                        new Suggestion("250", "250 - Diabetes mellitus", "250", "Diabetes mellitus")))
                .build();
        when(mockAssembler.assembleAjax("4")).thenReturn(model);
        mockRequest.setParameter("term", "4");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentType()).contains("application/json");

        JsonNode array = new ObjectMapper().readTree(mockResponse.getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(2);
        assertThat(array.get(0).get("value").asText()).isEqualTo("401");
        assertThat(array.get(0).get("label").asText()).isEqualTo("401 - Hypertension");
        assertThat(array.get(0).get("code").asText()).isEqualTo("401");
        assertThat(array.get(0).get("description").asText()).isEqualTo("Hypertension");
        assertThat(array.get(1).get("code").asText()).isEqualTo("250");
    }

    @Test
    void shouldWriteEmptyArray_whenNoSuggestions() throws Exception {
        when(mockAssembler.assembleAjax(any())).thenReturn(
                BillingDigSearchAjaxViewModel.builder().suggestions(Collections.emptyList()).build());
        mockRequest.setParameter("term", "Z");

        newAction().execute();

        assertThat(mockResponse.getContentAsString()).isEqualTo("[]");
    }

    @Test
    void shouldReturn401_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(mockAssembler, never()).assembleAjax(any());
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("term", "4");

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        verify(mockAssembler, never()).assembleAjax(any());
    }
}
