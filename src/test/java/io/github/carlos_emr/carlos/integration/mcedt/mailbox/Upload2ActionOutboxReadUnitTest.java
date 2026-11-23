/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.integration.mcedt.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import ca.ontario.health.edt.UploadData;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * An MCEDT upload sends the outbox file as it is on disk. Both builders sized the buffer from
 * {@code available()} and kept whatever one {@code read} returned, so a file the stream did not
 * hand over in one call reached the ministry truncated or zero-filled (#3027). They now read to
 * end of file.
 */
@Tag("unit")
@Tag("mcedt")
@DisplayName("MCEDT outbox upload reads")
class Upload2ActionOutboxReadUnitTest extends CarlosUnitTestBase {

    /** Larger than any single-read buffer a file stream is likely to fill in one call. */
    private static final int LARGE = 512 * 1024 + 7;

    @TempDir
    Path outbox;

    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<CarlosProperties> properties;

    @BeforeEach
    void setUp() {
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(new MockHttpServletRequest());
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        CarlosProperties config = mock(CarlosProperties.class);
        when(config.getProperty("ONEDT_OUTBOX", "")).thenReturn(outbox.toString());
        properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(config);
    }

    @AfterEach
    void tearDown() {
        if (properties != null) {
            properties.close();
        }
        if (servlet != null) {
            servlet.close();
        }
    }

    @Test
    @DisplayName("should send every byte of a large outbox file")
    void shouldReadWholeOutboxFile_forSingleUpload() throws Exception {
        byte[] content = pattern(LARGE, 3);
        Files.write(outbox.resolve("FAKE-CLAIM.txt"), content);
        Upload2Action action = new Upload2Action();
        action.setFileName("FAKE-CLAIM.txt");
        action.setDescription("FAKE-CLAIM.txt");
        action.setResourceType("CL");

        UploadData upload = action.toUpload();

        assertThat(upload.getContent()).isEqualTo(content);
        assertThat(upload.getDescription()).isEqualTo("FAKE-CLAIM.txt");
        assertThat(upload.getResourceType()).isEqualTo("CL");
    }

    @Test
    @DisplayName("should send every byte of each file in a multiple upload")
    void shouldReadWholeOutboxFiles_forMultipleUpload() throws Exception {
        byte[] first = pattern(LARGE, 5);
        byte[] second = pattern(17, 11);
        Files.write(outbox.resolve("FAKE-A.txt"), first);
        Files.write(outbox.resolve("FAKE-B.txt"), second);
        Upload2Action action = new Upload2Action();
        action.setFileName("FAKE-A.txt,FAKE-B.txt");
        action.setResourceType("CL,ER");

        List<UploadData> uploads = action.toUploadMultipe();

        assertThat(uploads).hasSize(2);
        assertThat(uploads.get(0).getContent()).isEqualTo(first);
        assertThat(uploads.get(0).getResourceType()).isEqualTo("CL");
        assertThat(uploads.get(1).getContent()).isEqualTo(second);
        assertThat(uploads.get(1).getResourceType()).isEqualTo("ER");
    }

    @Test
    @DisplayName("should fail instead of sending an empty upload when the outbox file is missing")
    void shouldFailUpload_whenOutboxFileIsMissing() {
        Upload2Action single = new Upload2Action();
        single.setFileName("FAKE-MISSING.txt");
        Upload2Action multiple = new Upload2Action();
        multiple.setFileName("FAKE-MISSING.txt");
        multiple.setResourceType("CL");

        assertThatThrownBy(single::toUpload)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unable to read upload file");
        assertThatThrownBy(multiple::toUploadMultipe)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unable to read upload file");
    }

    /** Non-zero bytes, so a zero-filled tail cannot pass for the real content. */
    private static byte[] pattern(int length, int seed) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (1 + (i * seed) % 251);
        }
        return bytes;
    }
}
