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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockPart;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BC Teleplan report upload ({@code servlet/oscar.DocumentTeleplanReportUploadServlet}): the page's form posts, and a
 * file reaches the document directory only for a billing administrator.
 *
 * @since 2026-10-17
 */
@DisplayName("Teleplan report upload checks the request before writing a file")
@Tag("unit")
@Tag("billing")
class DocumentTeleplanReportUploadServletUnitTest extends CarlosUnitTestBase {

    private static final String UPLOAD = "/servlet/oscar.DocumentTeleplanReportUploadServlet";

    private SecurityInfoManager security;
    private MockedStatic<LoggedInInfo> loggedIn;
    private final LoggedInInfo login = mock(LoggedInInfo.class);
    private DocumentTeleplanReportUploadServlet servlet;
    private Path documentDir;
    private String previousForward;
    private String previousDocumentDir;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        security = createAndRegisterMock(SecurityInfoManager.class);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
        documentDir = Files.createDirectories(tempDir.resolve("document"));
        previousForward = CarlosProperties.getInstance().getProperty("TA_FORWARD");
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("TA_FORWARD", "/billing/CA/BC/ViewGenTA");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
        servlet = new DocumentTeleplanReportUploadServlet();
        servlet.init(new MockServletConfig(new MockServletContext()));
    }

    @AfterEach
    void tearDown() {
        restore("TA_FORWARD", previousForward);
        restore("DOCUMENT_DIR", previousDocumentDir);
        if (loggedIn != null) loggedIn.close();
    }

    private static void restore(String key, String value) {
        if (value == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, value);
        }
    }

    private MockHttpServletRequest upload(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, UPLOAD);
        request.setContentType("multipart/form-data; boundary=FAKE");
        MockPart part = new MockPart("file1", "FAKE-teleplan.txt", "FAKE report".getBytes(StandardCharsets.UTF_8));
        request.addPart(part);
        return request;
    }

    private void grant(String object, boolean granted) {
        when(security.hasPrivilege(eq(login), eq(object), eq("w"), isNull())).thenReturn(granted);
    }

    @Test
    @DisplayName("should answer 405 and write nothing for a GET")
    void shouldWriteNothing_whenGet() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.service(upload("GET"), response);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(documentDir).isEmptyDirectory();
        verify(security, never()).hasPrivilege(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should answer 401 and write nothing without a signed-in user")
    void shouldWriteNothing_whenNotSignedIn() throws Exception {
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.service(upload("POST"), response);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(documentDir).isEmptyDirectory();
    }

    @Test
    @DisplayName("should answer 403 and write nothing without billing or system administration")
    void shouldWriteNothing_withoutBillingAdministration() throws Exception {
        grant("_admin.billing", false);
        grant("_admin", false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.service(upload("POST"), response);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(documentDir).isEmptyDirectory();
    }

    @Test
    @DisplayName("should store the report and forward to the report page for a billing administrator")
    void shouldStoreReport_forBillingAdministrator() throws Exception {
        grant("_admin.billing", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.service(upload("POST"), response);

        assertThat(documentDir.resolve("FAKE-teleplan.txt")).hasContent("FAKE report");
        assertThat(response.getForwardedUrl()).isEqualTo("/billing/CA/BC/ViewGenTA");
    }

    @Test
    @DisplayName("should also store the report for a system administrator, as the report page allows")
    void shouldStoreReport_forSystemAdministrator() throws Exception {
        grant("_admin.billing", false);
        grant("_admin", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.service(upload("POST"), response);

        assertThat(documentDir.resolve("FAKE-teleplan.txt")).hasContent("FAKE report");
        assertThat(response.getForwardedUrl()).isEqualTo("/billing/CA/BC/ViewGenTA");
    }
}
