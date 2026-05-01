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

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingReportControlViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingReportFragmentViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnDiskService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnRaSettlementService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipReportGenerationService;
import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ON report/action session guards")
@Tag("unit")
@Tag("billing")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportActionSessionGuardUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    Stream<Arguments> guardedActions() {
        return Stream.of(
                Arguments.of("ViewGenReport2Action",
                        (Callable<String>) () -> new ViewGenReport2Action(
                                securityInfoManager,
                                mock(OhipReportGenerationService.class)).execute()),
                Arguments.of("ViewGenGroupReport2Action",
                        (Callable<String>) () -> new ViewGenGroupReport2Action(
                                securityInfoManager,
                                mock(OhipReportGenerationService.class)).execute()),
                Arguments.of("ViewOnReportGeneration2Action",
                        (Callable<String>) () -> new ViewOnReportGeneration2Action(
                                securityInfoManager,
                                mock(BillingOnDiskService.class)).execute()),
                Arguments.of("ViewOnReportRegeneration2Action",
                        (Callable<String>) () -> new ViewOnReportRegeneration2Action(
                                securityInfoManager,
                                mock(BillingOnDiskService.class)).execute()),
                Arguments.of("ViewOnGenRaSettle2Action",
                        (Callable<String>) () -> new ViewOnGenRaSettle2Action(
                                securityInfoManager,
                                mock(OnRaSettlementService.class)).execute()),
                Arguments.of("ViewOnGenRaSettle352Action",
                        (Callable<String>) () -> new ViewOnGenRaSettle352Action(
                                securityInfoManager,
                                mock(OnRaSettlementService.class)).execute()),
                Arguments.of("ViewBillingReportCenter2Action",
                        (Callable<String>) () -> new ViewBillingReportCenter2Action(
                                securityInfoManager,
                                mock(ReportProviderDao.class)).execute()),
                Arguments.of("ViewBillingReportControl2Action",
                        (Callable<String>) () -> new ViewBillingReportControl2Action(
                                securityInfoManager,
                                mock(BillingReportFragmentViewModelAssembler.class),
                                mock(BillingReportControlViewModelAssembler.class)).execute()));
    }

    @ParameterizedTest(name = "{0} rejects missing session before privilege check")
    @MethodSource("guardedActions")
    void shouldRejectMissingSession_beforeCallingSecurityManager(String name, Callable<String> execute) {
        assertThatThrownBy(execute::call)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("session");

        verify(securityInfoManager, never()).hasPrivilege(any(), anyString(), anyString(), isNull());
    }
}
