/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GenericDownload Unit Tests")
@Tag("unit")
class GenericDownloadUnitTest {

    @TempDir
    private Path downloadDir;

    @Test
    @DisplayName("should sanitize Content-Disposition filename without changing file lookup")
    void shouldSanitizeContentDispositionFilename_withoutChangingFileLookup() throws Exception {
        String filename = "report\";final.pdf";
        byte[] content = new byte[]{1, 2, 3};
        Files.write(downloadDir.resolve(filename), content);

        MockHttpServletResponse response = new MockHttpServletResponse();

        new TestGenericDownload().transfer(response, downloadDir.toString(), filename);

        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment;filename=\"report__final.pdf\"");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getContentAsByteArray()).containsExactly(content);
    }

    @Test
    @DisplayName("should derive content type instead of trusting caller supplied value")
    void shouldDeriveContentType_insteadOfTrustingCallerSuppliedValue() throws Exception {
        String filename = "report.bin";
        Files.write(downloadDir.resolve(filename), new byte[]{1});

        MockHttpServletResponse response = new MockHttpServletResponse();

        new TestGenericDownload().transfer(response, downloadDir.toString(), filename, "text/html");

        assertThat(response.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("should reject direct generic download endpoint")
    void shouldRejectDirectGenericDownloadEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession().setAttribute("user", "999998");
        request.setParameter("filename", "report.pdf");
        request.setParameter("dir_property", "DOCUMENT_DIR");

        new GenericDownload().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(410);
    }

    private static final class TestGenericDownload extends GenericDownload {
        private void transfer(MockHttpServletResponse response, String dir, String filename) throws Exception {
            transferFile(response, response.getOutputStream(), dir, filename);
        }

        private void transfer(MockHttpServletResponse response, String dir, String filename, String contentType)
                throws Exception {
            response.setContentType(contentType);
            transferFile(response, response.getOutputStream(), dir, filename);
        }
    }
}
