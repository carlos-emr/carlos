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

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * The window the MCEDT Upload tab copies generated OHIP claim files in by: from the
 * last open, recorded in {@code <ONEDT_OUTBOX>/.timestamp}, to this one (#4577). The
 * open itself is {@link ActionUtils#stageGeneratedClaimFiles()}, which
 * {@code Upload2Action.execute} calls.
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
        Date lastOpen = Date.from(Instant.parse("2026-10-15T13:41:27.315Z"));

        ActionUtils.setOutboxTimestamp(lastOpen);

        assertThat(ActionUtils.getOutboxTimestamp()).isEqualTo(lastOpen);
        assertThat(outbox.resolve(".timestamp")).hasContent("2026-10-15T13:41:27.315Z");
        assertThat(tempDir.resolve("onEDTDocs/outbox.timestamp")).doesNotExist();
    }

    @Test
    void shouldNotStageASentClaimFileAgain_whenItWasWrittenEarlierInTheMinuteOfTheLastOpen() throws Exception {
        // The open used to be kept to the minute, so the next window began at the top of
        // that minute and copied a file written earlier in it, already staged and sent,
        // back into the outbox, however long after the next open came.
        Instant minute = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        claimFile("HJ123456.001", minute.plusSeconds(30));
        ActionUtils.setOutboxTimestamp(Date.from(minute.plusSeconds(40)));

        ActionUtils.stageGeneratedClaimFiles();

        assertThat(outbox.resolve("HJ123456.001")).doesNotExist();
    }

    @Test
    void shouldStageAClaimFile_whenItWasWrittenInTheMillisecondTheWindowEnds() throws Exception {
        // The next window starts just after this one ends, so the end itself belongs here.
        Instant end = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        claimFile("HJ123456.001", end);

        Date stagedTo = ActionUtils.moveOhipToOutBox(Date.from(end.minus(10, ChronoUnit.MINUTES)), Date.from(end));

        assertThat(outbox.resolve("HJ123456.001")).exists();
        assertThat(stagedTo).isEqualTo(Date.from(end));
    }

    @Test
    void shouldStartTheWindowNow_whenTheLastOpenWasRecordedToTheMinuteByAnEarlierRelease() throws Exception {
        // The open used to be kept as dd-MM-yyyy HH:mm, and an OSCAR import restores the
        // outbox as it was, so such a value may be months old. Staging from it would copy
        // every claim file written since, sent ones too.
        Instant now = Instant.now();
        claimFile("HJ123456.001", now.minus(20, ChronoUnit.MINUTES));
        Files.writeString(outbox.resolve(".timestamp"),
                new SimpleDateFormat("dd-MM-yyyy HH:mm").format(Date.from(now.minus(30, ChronoUnit.MINUTES))));
        Date before = new Date();

        try (LogCapture log = LogCapture.forLogger(ActionUtils.class)) {
            ActionUtils.stageGeneratedClaimFiles();

            assertThat(log.events()).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.ERROR));
            assertThat(log.events()).anyMatch(event -> event.getLevel() == Level.WARN);
        }

        assertThat(outbox.resolve("HJ123456.001")).doesNotExist();
        assertThat(ActionUtils.getOutboxTimestamp()).isAfterOrEqualTo(before);
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

        ActionUtils.stageGeneratedClaimFiles();

        assertThat(outbox.resolve("HJ123456.001")).hasBinaryContent(claim);

        // Submitted and moved out of the outbox: the next open must not stage it again.
        Files.delete(outbox.resolve("HJ123456.001"));
        ActionUtils.stageGeneratedClaimFiles();

        assertThat(outbox.resolve("HJ123456.001")).doesNotExist();
    }

    @Test
    void shouldRetryAClaimFileThatFailedToCopy_withoutCopyingEarlierOnesAgain() throws Exception {
        // Recording the open after a failed copy left that file out of every later
        // window. The open is now recorded only up to the first file that failed. The
        // three share a minute, so that record must keep its seconds too.
        Instant minute = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        ActionUtils.setOutboxTimestamp(Date.from(minute.minus(30, ChronoUnit.MINUTES)));
        claimFile("HJ123456.001", minute.plusSeconds(10));
        claimFile("HJ123456.002", minute.plusSeconds(20));
        claimFile("HJ123456.003", minute.plusSeconds(30));

        try (MockedStatic<FileUtils> files = mockStatic(FileUtils.class, CALLS_REAL_METHODS)) {
            files.when(() -> FileUtils.copyFileToDirectory(
                            argThat((File file) -> file != null && "HJ123456.002".equals(file.getName())),
                            any(File.class), anyBoolean()))
                    .thenThrow(new IOException("No space left on device"));

            ActionUtils.stageGeneratedClaimFiles();
        }

        assertThat(outbox.resolve("HJ123456.001")).exists();
        assertThat(outbox.resolve("HJ123456.002")).doesNotExist();
        assertThat(outbox.resolve("HJ123456.003")).doesNotExist();

        // The first file was sent and moved out of the outbox before the next open.
        Files.delete(outbox.resolve("HJ123456.001"));
        ActionUtils.stageGeneratedClaimFiles();

        assertThat(outbox.resolve("HJ123456.001")).doesNotExist();
        assertThat(outbox.resolve("HJ123456.002")).exists();
        assertThat(outbox.resolve("HJ123456.003")).exists();
    }

    @Test
    void shouldKeepTheWindowOpen_whenTheClaimFolderCannotBeListed() throws Exception {
        Instant now = Instant.now();
        ActionUtils.setOutboxTimestamp(Date.from(now.minus(30, ChronoUnit.MINUTES)));
        claimFile("HJ123456.001", now.minus(20, ChronoUnit.MINUTES));
        CarlosProperties.getInstance().put("HOME_DIR", tempDir.resolve("not-there") + "/");

        ActionUtils.stageGeneratedClaimFiles();

        CarlosProperties.getInstance().put("HOME_DIR", download + "/");
        ActionUtils.stageGeneratedClaimFiles();

        assertThat(outbox.resolve("HJ123456.001")).exists();
    }

    @Test
    void shouldStageClaimFiles_whenTheFolderAlsoHoldsAOneLetterFile() throws Exception {
        // isOHIPFile read two characters of every name, so one stray "x" aborted the
        // whole listing; with the window now held on a failed listing it would never move.
        Instant now = Instant.now();
        ActionUtils.setOutboxTimestamp(Date.from(now.minus(30, ChronoUnit.MINUTES)));
        Files.writeString(download.resolve("x"), "stray");
        claimFile("HJ123456.001", now.minus(20, ChronoUnit.MINUTES));

        ActionUtils.stageGeneratedClaimFiles();

        assertThat(outbox.resolve("HJ123456.001")).exists();
        assertThat(ActionUtils.isOHIPFile("x")).isFalse();
    }

    private Path claimFile(String name, Instant modified) throws IOException {
        Path file = Files.writeString(download.resolve(name), "HEBV03 " + name + "\r", StandardCharsets.US_ASCII);
        Files.setLastModifiedTime(file, FileTime.from(modified));
        return file;
    }

    private static void restore(String key, Object value) {
        if (value == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().put(key, value);
        }
    }
}
