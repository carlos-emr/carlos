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
package io.github.carlos_emr.carlos.integration.mcedt.mailbox;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window the MCEDT Upload tab copies generated OHIP claim files in by: from the
 * last open, recorded in {@code <ONEDT_OUTBOX>/.timestamp}, to this one (#4577). The
 * open itself is {@code Upload2Action.execute}: read the timestamp, copy, write it.
 */
@DisplayName("ActionUtils outbox staging window")
@Tag("unit")
@Tag("fast")
class ActionUtilsOutboxTimestampUnitTest {

    @TempDir
    Path tempDir;

    private Path outbox;
    private Path download;
    private Object oldOutbox;
    private Object oldHome;

    @BeforeEach
    void configure() throws Exception {
        outbox = Files.createDirectories(tempDir.resolve("onEDTDocs/outbox"));
        download = Files.createDirectories(tempDir.resolve("billing/download"));
        // The packaged install configures both with a trailing slash.
        oldOutbox = CarlosProperties.getInstance().put("ONEDT_OUTBOX", outbox + "/");
        oldHome = CarlosProperties.getInstance().put("HOME_DIR", download + "/");
    }

    @AfterEach
    void restore() {
        restore("ONEDT_OUTBOX", oldOutbox);
        restore("HOME_DIR", oldHome);
    }

    @Test
    void shouldReadBackTheLastOpen_fromTheFileItWasWrittenTo() throws Exception {
        Date lastOpen = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-10-15 09:41");

        ActionUtils.setOutboxTimestamp(lastOpen);

        assertThat(ActionUtils.getOutboxTimestamp()).isEqualTo(lastOpen);
        assertThat(outbox.resolve(".timestamp")).hasContent("15-10-2026 09:41");
        assertThat(tempDir.resolve("onEDTDocs/outbox.timestamp")).doesNotExist();
    }

    @Test
    void shouldStartTheWindowNowWithoutAnError_whenNoOpenWasRecorded() throws Exception {
        // Every open before the fix left this file empty.
        Files.createFile(outbox.resolve(".timestamp"));
        try (LogCapture log = LogCapture.forLogger(ActionUtils.class)) {
            Date before = new Date();

            Date start = ActionUtils.getOutboxTimestamp();

            assertThat(start).isBetween(before, new Date(), true, true);
            assertThat(log.events()).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.ERROR));
        }
    }

    @Test
    void shouldStageClaimFileWrittenSinceTheLastOpen_andNotCopyItAgainOnTheNextOpen() throws Exception {
        Instant now = Instant.now();
        ActionUtils.setOutboxTimestamp(Date.from(now.minus(10, ChronoUnit.MINUTES)));
        byte[] claim = "HEBV03J000000000000      0000123456  \r".getBytes(StandardCharsets.US_ASCII);
        Path generated = Files.write(download.resolve("HJ123456.001"), claim);
        Files.setLastModifiedTime(generated, FileTime.from(now.minus(5, ChronoUnit.MINUTES)));

        openUploadTab();

        assertThat(outbox.resolve("HJ123456.001")).hasBinaryContent(claim);

        // Submitted and moved out of the outbox: the next open must not stage it again.
        Files.delete(outbox.resolve("HJ123456.001"));
        openUploadTab();

        assertThat(outbox.resolve("HJ123456.001")).doesNotExist();
    }

    /** The three calls {@code Upload2Action.execute} makes when the Upload tab opens. */
    private static void openUploadTab() {
        Date start = ActionUtils.getOutboxTimestamp();
        Date end = new Date();
        ActionUtils.moveOhipToOutBox(start, end);
        ActionUtils.setOutboxTimestamp(end);
    }

    private static void restore(String key, Object value) {
        if (value == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().put(key, value);
        }
    }
}
