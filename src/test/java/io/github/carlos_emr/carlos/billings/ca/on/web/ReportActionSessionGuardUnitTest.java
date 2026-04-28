/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingReportControlDataAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingReportFragmentDataAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnBillingDiskService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnGenRAsettleService;
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
                Arguments.of("ViewOngenreport2Action",
                        (Callable<String>) () -> new ViewOngenreport2Action(
                                securityInfoManager,
                                mock(OnBillingDiskService.class)).execute()),
                Arguments.of("ViewOnregenreport2Action",
                        (Callable<String>) () -> new ViewOnregenreport2Action(
                                securityInfoManager,
                                mock(OnBillingDiskService.class)).execute()),
                Arguments.of("ViewOnGenRAsettle2Action",
                        (Callable<String>) () -> new ViewOnGenRAsettle2Action(
                                securityInfoManager,
                                mock(OnGenRAsettleService.class)).execute()),
                Arguments.of("ViewOnGenRAsettle352Action",
                        (Callable<String>) () -> new ViewOnGenRAsettle352Action(
                                securityInfoManager,
                                mock(OnGenRAsettleService.class)).execute()),
                Arguments.of("ViewBillingReportCenter2Action",
                        (Callable<String>) () -> new ViewBillingReportCenter2Action(
                                securityInfoManager,
                                mock(ReportProviderDao.class)).execute()),
                Arguments.of("ViewBillingReportControl2Action",
                        (Callable<String>) () -> new ViewBillingReportControl2Action(
                                securityInfoManager,
                                mock(BillingReportFragmentDataAssembler.class),
                                mock(BillingReportControlDataAssembler.class)).execute()));
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
