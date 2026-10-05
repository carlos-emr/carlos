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
package io.github.carlos_emr.carlos.lab.ca.all.util;

import io.github.carlos_emr.CarlosProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Exercises real lab upload file writes and cleanup inside configured directories.
 * @since 2026-09-20
 */
@Tag("unit")
@Tag("lab")
class UtilitiesUploadUnitTest {
    @TempDir Path documentDir;
    private MockedStatic<CarlosProperties> configuration;

    @BeforeEach
    void setUpConfiguration() {
        CarlosProperties properties = mock(CarlosProperties.class);
        configuration = mockStatic(CarlosProperties.class);
        configuration.when(CarlosProperties::getInstance).thenReturn(properties);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        when(properties.getProperty("OMD_hrm")).thenReturn(documentDir.toString());
    }

    @AfterEach
    void closeConfiguration() {
        configuration.close();
    }

    @Test
    void shouldWriteLabContentAndCloseStream_whenSaveFileSucceeds() {
        TrackedStream input = new TrackedStream("MSH|fixture".getBytes(StandardCharsets.UTF_8));

        String saved = Utilities.saveFile(input, "lab.hl7.enc");

        assertThat(saved).isNotNull();
        assertThat(Path.of(saved).getFileName().toString()).matches("LabUpload\\.lab\\.hl7\\.[0-9]+");
        assertThat(Path.of(saved)).hasBinaryContent("MSH|fixture".getBytes(StandardCharsets.UTF_8));
        assertThat(input.closed).isTrue();
    }

    @Test
    // Belt and braces: if the injected failure is ever swallowed again, fail fast instead of
    // writing an endless stream into @TempDir.
    @Timeout(30)
    void shouldDeletePartialLabUploadAndCloseStream_whenReadFails() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream input = new InputStream() {
            private int reads;
            @Override public int read() throws IOException {
                // Fail on every read from the fourth onwards, not just once: a buffered caller reads
                // through InputStream.read(byte[],int,int), which swallows an IOException thrown after
                // the first byte of a bulk read. A one-shot failure was therefore lost and this stream
                // returned 'A' forever, filling the CI runner's disk until the runner died.
                if (reads++ >= 3) throw new IOException("injected read failure");
                return 'A';
            }
            @Override public void close() { closed.set(true); }
        };

        assertThat(Utilities.saveFile(input, "partial.hl7")).isNull();
        assertThat(closed.get()).isTrue();

        try (var children = Files.list(documentDir)) {
            assertThat(children.toList()).isEmpty();
        }
    }

    @Test
    void shouldWritePdfAndCloseStream_whenSavePdfSucceeds() {
        TrackedStream input = new TrackedStream("%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        String saved = Utilities.savePdfFile(input, "report.pdf");

        assertThat(saved).isNotNull();
        assertThat(Path.of(saved).getFileName().toString()).matches("DocUpload\\.report\\.[0-9]+\\.pdf");
        assertThat(Path.of(saved)).hasBinaryContent("%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        assertThat(input.closed).isTrue();
    }

    @Test
    void shouldCloseStreamWithoutWriting_whenPdfNameIsBlank() throws Exception {
        TrackedStream input = new TrackedStream(new byte[]{1, 2, 3});

        assertThat(Utilities.savePdfFile(input, " ")).isNull();

        assertThat(input.closed).isTrue();
        try (var children = Files.list(documentDir)) {
            assertThat(children.toList()).isEmpty();
        }
    }

    @Test
    void shouldWriteHrmAndCloseStream_whenSaveHrmSucceeds() {
        TrackedStream input = new TrackedStream("MSH|HRM".getBytes(StandardCharsets.UTF_8));

        String saved = Utilities.saveHRMFile(input, "hrm.hl7");

        assertThat(saved).isNotNull();
        assertThat(Path.of(saved).getFileName().toString()).matches("KeyUpload\\.hrm\\.hl7\\.[0-9]+");
        assertThat(Path.of(saved)).hasBinaryContent("MSH|HRM".getBytes(StandardCharsets.UTF_8));
        assertThat(input.closed).isTrue();
    }

    @Test
    void shouldSeparateMessages_whenFileContainsTwoHl7Headers() throws Exception {
        Path source = Files.writeString(documentDir.resolve("messages.hl7"),
                "MSH|^~\\&|A\nPID|1|A\nOBX|A\nMSH|^~\\&|B\nPID|1|B\nOBX|B\n");

        List<String> messages = Utilities.separateMessages(source.toString());

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).contains("MSH|^~\\&|A", "PID|1|A").doesNotContain("PID|1|B");
        assertThat(messages.get(1)).contains("MSH|^~\\&|B", "PID|1|B").doesNotContain("PID|1|A");
    }

    private static final class TrackedStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackedStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
