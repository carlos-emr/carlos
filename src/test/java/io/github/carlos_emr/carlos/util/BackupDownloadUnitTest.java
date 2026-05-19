/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.util;

import java.io.IOException;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BackupDownload authorization")
@Tag("unit")
@Tag("security")
class BackupDownloadUnitTest extends CarlosUnitTestBase {

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
    @DisplayName("should reject GET when filename is missing")
    void shouldRejectGet_whenFilenameMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/BackupDownload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingBackupDownload servlet = new RecordingBackupDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(servlet.downloadCalled).isFalse();
        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should reject GET when sanitized filename is blank")
    void shouldRejectGet_whenSanitizedFilenameBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/BackupDownload");
        request.addParameter("filename", "///");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingBackupDownload servlet = new RecordingBackupDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(servlet.downloadCalled).isFalse();
        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should reject GET when session is missing")
    void shouldRejectGet_whenSessionMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/BackupDownload");
        request.addParameter("filename", "backup.sql");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingBackupDownload servlet = new RecordingBackupDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(servlet.downloadCalled).isFalse();
        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should reject GET when backup download privilege is missing")
    void shouldRejectGet_whenBackupDownloadPrivilegeMissing() throws Exception {
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingBackupDownload servlet = new RecordingBackupDownload();
        LoggedInInfo loggedInInfo = mockLoggedInInfo(request);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.backup"), eq("r"), isNull()))
                .thenReturn(false);

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(servlet.downloadCalled).isFalse();
        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("r"), isNull());
        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_admin.backup"), eq("r"), isNull());
    }

    @Test
    @DisplayName("should download when admin read privilege is granted")
    void shouldDownload_whenAdminReadPrivilegeGranted() throws Exception {
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingBackupDownload servlet = new RecordingBackupDownload();
        LoggedInInfo loggedInInfo = mockLoggedInInfo(request);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(servlet.downloadCalled).isTrue();
        assertThat(servlet.downloadAllowed).isTrue();
        assertThat(servlet.downloadDir).isEqualTo(tempDir.toString());
        assertThat(servlet.downloadFilename).isEqualTo("backup.sql");
        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("r"), isNull());
        verify(securityInfoManager, never()).hasPrivilege(eq(loggedInInfo), eq("_admin.backup"), eq("r"), isNull());
    }

    @Test
    @DisplayName("should download when backup read privilege is granted")
    void shouldDownload_whenBackupReadPrivilegeGranted() throws Exception {
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingBackupDownload servlet = new RecordingBackupDownload();
        LoggedInInfo loggedInInfo = mockLoggedInInfo(request);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.backup"), eq("r"), isNull()))
                .thenReturn(true);

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(servlet.downloadCalled).isTrue();
        assertThat(servlet.downloadAllowed).isTrue();
        assertThat(servlet.downloadDir).isEqualTo(tempDir.toString());
        assertThat(servlet.downloadFilename).isEqualTo("backup.sql");
        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("r"), isNull());
        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_admin.backup"), eq("r"), isNull());
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/BackupDownload");
        request.addParameter("filename", "backup.sql");
        request.getSession().setAttribute("backupfilepath", tempDir.toString());
        return request;
    }

    private LoggedInInfo mockLoggedInInfo(MockHttpServletRequest request) {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        HttpSession session = request.getSession(false);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(session))
                .thenReturn(loggedInInfo);
        return loggedInInfo;
    }

    private static final class RecordingBackupDownload extends BackupDownload {
        private boolean downloadCalled;
        private boolean downloadAllowed;
        private String downloadDir;
        private String downloadFilename;

        @Override
        public void download(boolean bDownload, HttpServletResponse res, String dir, String filename, String contentType)
                throws IOException {
            downloadCalled = true;
            downloadAllowed = bDownload;
            downloadDir = dir;
            downloadFilename = filename;
        }
    }
}
