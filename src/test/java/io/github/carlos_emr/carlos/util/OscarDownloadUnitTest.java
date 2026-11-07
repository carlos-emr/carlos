package io.github.carlos_emr.carlos.util;

import java.io.IOException;
import java.nio.file.Path;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileWriteException;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OscarDownload filename validation")
@Tag("unit")
@Tag("security")
class OscarDownloadUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should reject GET when filename is hidden")
    void shouldRejectGet_whenFilenameIsHidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", ".env");
        request.addParameter("homepath", "homepath");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(servlet.transferCalled).isFalse();
    }

    @ParameterizedTest
    @DisplayName("should reject GET when filename contains path components")
    @ValueSource(strings = {"../report.txt", "..\\report.txt", "/tmp/report.txt"})
    void shouldRejectGet_whenFilenameContainsPathComponents(String filename) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", filename);
        request.addParameter("homepath", "homepath");
        request.getSession().setAttribute("homepath", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(servlet.transferCalled).isFalse();
    }

    @Test
    @DisplayName("should use normalized filename when filename contains spaces")
    void shouldUseNormalizedFilename_whenFilenameContainsSpaces() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", "report final.txt");
        request.addParameter("homepath", "homepath");
        request.getSession().setAttribute("homepath", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(servlet.transferCalled).isTrue();
        assertThat(servlet.transferDir).isEqualTo(tempDir.toString());
        assertThat(servlet.transferFilename).isEqualTo("report_final.txt");
    }

    @Test
    void shouldRefuseAmbiguousBillingDownloadBeforeOpeningResponseStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", "o19-claim-2021-2-0.claim");
        request.addParameter("homepath", "ohipdownload");
        request.getSession().setAttribute("ohipdownload", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();
        servlet.rejectBilling = true;
        servlet.service(request, response);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getErrorMessage()).contains("requires reconciliation");
        assertThat(servlet.transferCalled).isFalse();
    }

    @Test
    void shouldTransferReviewedBillingDownloadAfterGuardPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", "o19-claim-2021-2-0.claim");
        request.addParameter("homepath", "ohipdownload");
        request.getSession().setAttribute("ohipdownload", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();
        servlet.service(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(servlet.billingChecked).isTrue();
        assertThat(servlet.transferCalled).isTrue();
    }

    private static final class RecordingOscarDownload extends OscarDownload {
        private boolean rejectBilling;
        private boolean billingChecked;
        private boolean transferCalled;
        private String transferDir;
        private String transferFilename;

        @Override
        protected void requireBillingFile(String filename, Path path) {
            billingChecked = true;
            if (rejectBilling) throw BillingFileWriteException.forReason(
                    BillingFileWriteException.Reason.IMPORTED_FILE_UNRESOLVED, null);
        }

        @Override
        protected void transferFile(HttpServletResponse res, ServletOutputStream stream, String dir, String filename)
                throws IOException {
            transferCalled = true;
            transferDir = dir;
            transferFilename = filename;
        }
    }
}
