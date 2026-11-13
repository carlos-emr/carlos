/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.reportByTemplate.actions;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportCsvDownloads;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportFactory;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportObject;
import io.github.carlos_emr.carlos.report.reportByTemplate.Reporter;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Running a Report by Template report keeps its CSV on the server and gives the result page only a token.
 */
@DisplayName("GenerateReport2Action CSV downloads")
@Tag("unit")
@Tag("report")
class GenerateReport2ActionUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER = "999998";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<ReportFactory> reportFactoryMock;
    private MockHttpServletRequest request;
    private Reporter reporter;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)).thenReturn(true);

        request = new MockHttpServletRequest("POST", "/oscarReport/reportByTemplate/GenerateReportAction");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("type", "sql");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        reporter = mock(Reporter.class);
        reportFactoryMock = mockStatic(ReportFactory.class);
        reportFactoryMock.when(() -> ReportFactory.getReporter("sql")).thenReturn(reporter);
    }

    @AfterEach
    void tearDown() {
        reportFactoryMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should keep the report's CSV for this session and give the result page only its token")
    void shouldKeepCsvAndPublishToken_whenReportSucceeds() throws Exception {
        ReportObject report = mock(ReportObject.class);
        when(reporter.generateReport(any())).thenAnswer(invocation -> {
            request.setAttribute("reportobject", report);
            request.setAttribute("csv", "name\r\nFAKE-synthetic\r\n");
            return true;
        });

        assertThat(new GenerateReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        String token = (String) request.getAttribute(ReportCsvDownloads.TOKEN_ATTRIBUTE);
        assertThat(token).isNotBlank();
        assertThat(request.getAttribute("csv")).isNull();
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        ReportCsvDownloads.find(request.getSession(false), PROVIDER, token, "0").orElseThrow().writeTo(kept);
        assertThat(kept.toString(StandardCharsets.UTF_8)).isEqualTo("name\r\nFAKE-synthetic\r\n");
    }

    @Test
    @DisplayName("should keep nothing when the report is refused")
    void shouldKeepNothing_whenReportFails() throws Exception {
        when(reporter.generateReport(any())).thenAnswer(invocation -> {
            request.setAttribute("csv", "");
            return false;
        });

        assertThat(new GenerateReport2Action().execute()).isEqualTo("fail");

        assertThat(request.getAttribute(ReportCsvDownloads.TOKEN_ATTRIBUTE)).isNull();
        assertThat(request.getAttribute(ReportCsvDownloads.DOWNLOADS_ATTRIBUTE)).isNull();
    }
}
