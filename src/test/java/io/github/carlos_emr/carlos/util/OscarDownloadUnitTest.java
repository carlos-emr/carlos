package io.github.carlos_emr.carlos.util;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao.FileReview;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingImportedFiles;
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
    void shouldRefuseDownload_whenImportedFileIsAmbiguous() throws Exception {
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
    void shouldTransferDownload_whenImportedFileIsReviewed() throws Exception {
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

    /** Replaces the pathname at response opening; only the verified snapshot may be sent. */
    @Test
    void shouldServeReviewedBytes_whenBillingFileIsReplacedBeforeStreaming() throws Exception {
        String filename = "o19-claim-2021-2-0.claim";
        byte[] reviewedBytes = "reviewed submission".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve(filename);
        Files.write(file, reviewedBytes);
        Path replacement = Files.writeString(tempDir.resolve("replacement"), "different unreviewed submission");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reviewedBytes));
        FileReview review = new FileReview("shared.001", filename, "reconciled", sha, "billing-admin", true);
        OscarDownload servlet = new OscarDownload() {
            @Override
            protected BillingImportedFiles.ReviewedFile reviewedBillingFile(String name, Path path) {
                return BillingImportedFiles.openReviewed(List.of(review), name, path);
            }
        };
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", filename);
        request.addParameter("homepath", "ohipdownload");
        request.getSession().setAttribute("ohipdownload", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                try {
                    Files.move(replacement, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
                return super.getOutputStream();
            }
        };

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(reviewedBytes);
        assertThat(response.getContentLengthLong()).isEqualTo(reviewedBytes.length);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(Files.readString(file)).isEqualTo("different unreviewed submission");
    }

    @Test
    void shouldRefuseDownload_whenAlternateSessionKeyPointsToBillingDirectory() throws Exception {
        try (org.mockito.MockedStatic<io.github.carlos_emr.CarlosProperties> properties =
                     org.mockito.Mockito.mockStatic(io.github.carlos_emr.CarlosProperties.class)) {
            io.github.carlos_emr.CarlosProperties props = org.mockito.Mockito.mock(io.github.carlos_emr.CarlosProperties.class);
            org.mockito.Mockito.when(props.getProperty("HOME_DIR")).thenReturn(tempDir.toString());
            properties.when(io.github.carlos_emr.CarlosProperties::getInstance).thenReturn(props);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
            request.addParameter("filename", "shared.001");
            request.addParameter("homepath", "homepath");
            request.getSession().setAttribute("homepath", tempDir.resolve(".").toString());
            request.getSession().setAttribute("user", "999998");
            MockHttpServletResponse response = new MockHttpServletResponse();
            RecordingOscarDownload servlet = new RecordingOscarDownload();
            servlet.rejectBilling = true;
            servlet.service(request, response);
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(servlet.billingChecked).isTrue();
            assertThat(servlet.transferCalled).isFalse();
        }
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
        protected BillingImportedFiles.ReviewedFile reviewedBillingFile(String filename, Path path) {
            requireBillingFile(filename, path);
            return null;
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
