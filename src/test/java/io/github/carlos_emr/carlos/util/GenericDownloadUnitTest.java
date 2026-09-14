/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.util;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GenericDownload")
@Tag("unit")
@Tag("fast")
@Tag("security")
class GenericDownloadUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should stream fixed-type attachment without closing the container response stream")
    void shouldStreamAttachment_withoutClosingResponseStream() throws Exception {
        byte[] content = new byte[] {1, 2, 3, 4};
        Files.write(tempDir.resolve("report.bin"), content);
        TrackingResponse response = new TrackingResponse();

        new GenericDownload().download(true, response, tempDir.toString(), "report.bin");

        assertThat(response.getContentType()).isEqualTo("application/octet-stream");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment;filename=\"report.bin\"");
        assertThat(response.getHeader("Content-Length")).isEqualTo("4");
        assertThat(response.bytes.toByteArray()).containsExactly(content);
        assertThat(response.outputClosed).isFalse();
    }

    private static final class TrackingResponse extends MockHttpServletResponse {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean outputClosed;
        private final ServletOutputStream output = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int value) {
                bytes.write(value);
            }

            @Override
            public void close() throws IOException {
                outputClosed = true;
                super.close();
            }
        };

        @Override
        public ServletOutputStream getOutputStream() {
            return output;
        }
    }
}
