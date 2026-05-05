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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCodeSearchAjaxViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeSearchAjaxViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeSearchAjaxViewModel.Suggestion;
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
 * Unit tests for {@link ViewBillingCodeSearchAjax2Action} — the OHIP
 * service-code autocomplete JSON endpoint that absorbed the former
 * {@code billingCodeSearchAjax.jsp} controller-in-a-JSP.
 *
 * @since 2026-04-26
 */
@DisplayName("ViewBillingCodeSearchAjax2Action")
@Tag("unit")
@Tag("billing")
class ViewBillingCodeSearchAjax2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private BillingCodeSearchAjaxViewModelAssembler mockAssembler;

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

    private ViewBillingCodeSearchAjax2Action newAction() {
        return new ViewBillingCodeSearchAjax2Action(mockSecurityInfoManager, mockAssembler);
    }

    @Test
    void shouldWriteJsonArray_forEachSuggestion() throws Exception {
        BillingCodeSearchAjaxViewModel model = BillingCodeSearchAjaxViewModel.builder()
                .suggestions(Arrays.asList(
                        new Suggestion("A007", "A007 - Office visit", "A007", "Office visit"),
                        new Suggestion("A008", "A008 - Hospital admission", "A008", "Hospital admission")))
                .build();
        when(mockAssembler.assemble("A00")).thenReturn(model);
        mockRequest.setParameter("term", "A00");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentType()).contains("application/json");

        JsonNode array = new ObjectMapper().readTree(mockResponse.getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(2);
        assertThat(array.get(0).get("value").asText()).isEqualTo("A007");
        assertThat(array.get(0).get("label").asText()).isEqualTo("A007 - Office visit");
        assertThat(array.get(0).get("code").asText()).isEqualTo("A007");
        assertThat(array.get(0).get("description").asText()).isEqualTo("Office visit");
        assertThat(array.get(1).get("code").asText()).isEqualTo("A008");
    }

    @Test
    void shouldWriteEmptyArray_whenNoSuggestions() throws Exception {
        when(mockAssembler.assemble(any())).thenReturn(
                BillingCodeSearchAjaxViewModel.builder().suggestions(Collections.emptyList()).build());
        mockRequest.setParameter("term", "ZZZ");

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
        verify(mockAssembler, never()).assemble(any());
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("term", "A00");

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        verify(mockAssembler, never()).assemble(any());
    }
}
