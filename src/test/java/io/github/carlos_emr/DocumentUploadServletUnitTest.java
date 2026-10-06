/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
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

@DisplayName("DocumentUploadServlet authorization")
@Tag("unit")
@Tag("security")
class DocumentUploadServletUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    @TempDir private Path tempDir;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
    }

    @Test
    @DisplayName("should reject GET before upload processing")
    void shouldRejectGet_beforeUploadProcessing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/servlet/io.github.carlos_emr.DocumentUploadServlet");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DocumentUploadServlet().service(request, response);

        assertThat(response.getStatus()).isEqualTo(405);
        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should reject POST when session is unauthenticated")
    void shouldRejectPost_whenSessionUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/servlet/io.github.carlos_emr.DocumentUploadServlet");
        MockHttpServletResponse response = new MockHttpServletResponse();
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        new DocumentUploadServlet().service(request, response);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should reject POST when upload privilege is missing")
    void shouldRejectPost_whenUploadPrivilegeMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/servlet/io.github.carlos_emr.DocumentUploadServlet");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);

        new DocumentUploadServlet().service(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull());
    }

    @Test
    @DisplayName("should copy inbox document and forward when upload is authorized")
    void shouldCopyInboxDocumentAndForward_whenUploadAuthorized() throws Exception {
        Path documentDir = Files.createDirectories(tempDir.resolve("documents"));
        Path inboxDir = Files.createDirectories(tempDir.resolve("inbox"));
        Path archiveDir = Files.createDirectories(tempDir.resolve("archive"));
        Files.writeString(inboxDir.resolve("claim.txt"), "claim body", StandardCharsets.UTF_8);
        String previousForward = CarlosProperties.getInstance().getProperty("RA_FORWORD");
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousInbox = CarlosProperties.getInstance().getProperty("ONEDT_INBOX");
        String previousArchive = CarlosProperties.getInstance().getProperty("ONEDT_ARCHIVE");
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/servlet/io.github.carlos_emr.DocumentUploadServlet");
        request.addParameter("filename", "claim.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        DocumentUploadServlet servlet = new DocumentUploadServlet();
        servlet.init(new MockServletConfig(new MockServletContext()));

        try {
            CarlosProperties.getInstance().setProperty("RA_FORWORD", "/WEB-INF/jsp/billing/ra-upload.jsp");
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("ONEDT_INBOX", inboxDir.toString());
            CarlosProperties.getInstance().setProperty("ONEDT_ARCHIVE", archiveDir.toString());

            servlet.service(request, response);

            assertThat(Files.readString(documentDir.resolve("claim.txt"), StandardCharsets.UTF_8))
                    .isEqualTo("claim body");
            DocumentBean documentBean = (DocumentBean) request.getAttribute("documentBean");
            assertThat(documentBean.getFilename()).isEqualTo("claim.txt");
            assertThat(response.getForwardedUrl()).isEqualTo("/WEB-INF/jsp/billing/ra-upload.jsp");
        } finally {
            restoreProperty("RA_FORWORD", previousForward);
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("ONEDT_INBOX", previousInbox);
            restoreProperty("ONEDT_ARCHIVE", previousArchive);
        }
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, previousValue);
        }
    }
}
