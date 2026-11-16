/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.util;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.Test;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises both public download servlets with real filesystem IO. */
@Tag("unit")
@Tag("security")
@Isolated
class DownloadMissingFileUnitTest extends CarlosUnitTestBase {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReturnNotFoundWithoutAttachmentHeaders_whenAnAuthorizedFileIsMissing(boolean backup) throws Exception {
        var response = download(backup, "missing.txt");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldStreamTheExistingFile_whenTheCallerIsAuthorized(boolean backup) throws Exception {
        byte[] bytes = new byte[] {0, 1, (byte) 255, 10};
        Files.write(directory.resolve("report.bin"), bytes);
        var response = download(backup, "report.bin");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment;filename=\"report.bin\"");
        assertThat(response.getHeader("Content-Length")).isEqualTo("4");
        assertThat(response.getContentAsByteArray()).containsExactly(bytes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReturnNotFound_whenTheSessionDirectoryCannotBeCanonicalized(boolean backup) throws Exception {
        var response = download(backup, "missing.txt", directory + "\u0000unavailable");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenTheBillingDirectoryCannotBeCanonicalized() throws Exception {
        var config = mock(CarlosProperties.class);
        when(config.getProperty("HOME_DIR")).thenReturn(directory + "\u0000unavailable");
        try (var properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(config);
            var response = download(false, "missing.txt");
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getHeader("Content-Disposition")).isNull();
        }
    }

    private MockHttpServletResponse download(boolean backup, String filename) throws Exception {
        return download(backup, filename, directory.toString());
    }

    private MockHttpServletResponse download(boolean backup, String filename, String serverDirectory) throws Exception {
        var security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        var request = new MockHttpServletRequest("GET", backup ? "/servlet/BackupDownload" : "/servlet/OscarDownload");
        request.addParameter("filename", filename);
        request.addParameter("homepath", "obecdownload");
        var session = request.getSession();
        session.setAttribute("user", "999998");
        session.setAttribute("backupfilepath", serverDirectory);
        session.setAttribute("obecdownload", serverDirectory);
        var response = new MockHttpServletResponse();
        try (var info = mockStatic(LoggedInInfo.class)) {
            info.when(() -> LoggedInInfo.getLoggedInInfoFromSession(session)).thenReturn(mock(LoggedInInfo.class));
            GenericDownload servlet = backup ? new BackupDownload() : new OscarDownload();
            servlet.service(request, response);
        }
        return response;
    }
}
