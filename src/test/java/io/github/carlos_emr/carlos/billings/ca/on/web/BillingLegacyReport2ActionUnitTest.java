/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingLegacyReportViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Unit coverage for {@link BillingLegacyReport2Action}. */
@DisplayName("BillingLegacyReport2Action")
@Tag("unit")
@Tag("billing")
class BillingLegacyReport2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private LoggedInInfo loggedInInfo;
    private SecurityInfoManager securityInfoManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        loggedInInfo = mock(LoggedInInfo.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
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
    void shouldReadSelectedArchiveFolder_whenRenderingLegacyLReport() throws Exception {
        Path inbox = Files.createDirectory(tempDir.resolve("inbox"));
        Path archive = Files.createDirectory(tempDir.resolve("archive"));
        Files.writeString(archive.resolve("L1ES0001.txt"), "archive-report");

        CarlosProperties props = mock(CarlosProperties.class);
        when(props.getProperty("ONEDT_INBOX")).thenReturn(inbox.toString());
        when(props.getProperty("ONEDT_ARCHIVE")).thenReturn(archive.toString());

        request.setParameter("filename", "L1ES0001.txt");
        request.setParameter("folder", "archive");

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            String result = new BillingLegacyReport2Action(securityInfoManager).execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            BillingLegacyReportViewModel model =
                    (BillingLegacyReportViewModel) request.getAttribute("lreportModel");
            assertThat(model.getFileContents()).isEqualTo("archive-report");
        }
    }

    @Test
    void shouldRejectPathComponentFilename_whenRenderingLegacyLReport() throws Exception {
        Path inbox = Files.createDirectory(tempDir.resolve("inbox"));
        Files.writeString(inbox.resolve("outside.txt"), "inside-report");
        Files.writeString(tempDir.resolve("outside.txt"), "outside-report");

        CarlosProperties props = mock(CarlosProperties.class);
        when(props.getProperty("ONEDT_INBOX")).thenReturn(inbox.toString());

        request.setParameter("filename", "../outside.txt");
        request.setParameter("folder", "inbox");

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            String result = new BillingLegacyReport2Action(securityInfoManager).execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            BillingLegacyReportViewModel model =
                    (BillingLegacyReportViewModel) request.getAttribute("lreportModel");
            assertThat(model.getFileContents()).isEmpty();
            assertThat(request.getAttribute("readError"))
                    .isEqualTo("Could not read selected MOH response file.");
        }
    }
}
