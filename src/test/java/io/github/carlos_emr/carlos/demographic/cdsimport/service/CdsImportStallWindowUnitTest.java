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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code cds.import.maxDurationMillis} is a stall window, not a budget: work that keeps reaching
 * checkpoints is never stopped for how long it takes, and work silent for the whole window stops
 * under {@code IMPORT_WORKER_STALLED}. The clocks are driven by the tests, so nothing waits.
 */
@Tag("unit")
class CdsImportStallWindowUnitTest {
    private static final long MILLIS = TimeUnit.MILLISECONDS.toNanos(1);

    @TempDir Path temp;

    @Test
    void shouldNeverStall_whileCheckpointsKeepComing() {
        var clock = new AtomicLong();
        var window = new CdsImportStallWindow(100, clock::get);
        // Ten thousand checkpoints of 99ms: more than sixteen minutes against a 100ms window.
        for (int i = 0; i < 10_000; i++) {
            clock.addAndGet(99 * MILLIS);
            window.checkpoint();
        }
        // Exactly the window is not longer than the window.
        clock.addAndGet(100 * MILLIS);
        assertFalse(window.stalled());
    }

    @Test
    void shouldStallForGood_afterOneGapLongerThanTheWindow() {
        var clock = new AtomicLong(Long.MAX_VALUE - 50 * MILLIS);
        var window = new CdsImportStallWindow(100, clock::get);
        // The clock wraps between the two checkpoints; only differences are compared.
        clock.addAndGet(101 * MILLIS);
        CdsImportException stalled = assertThrows(CdsImportException.class, window::checkpoint);
        assertEquals("IMPORT_WORKER_STALLED", stalled.getCode());
        // A worker that was silent for the whole window does not resume on its next checkpoint.
        clock.addAndGet(MILLIS);
        assertTrue(window.stalled());
        assertThrows(CdsImportException.class, window::checkpoint);
    }

    @Test
    void shouldAcceptAnyConfiguredWindow_withoutOverflowOrAZeroWindow() {
        var clock = new AtomicLong();
        var widest = new CdsImportStallWindow(Long.MAX_VALUE, clock::get);
        clock.addAndGet(TimeUnit.DAYS.toNanos(365_000));
        assertFalse(widest.stalled());
        assertThrows(IllegalArgumentException.class, () -> new CdsImportStallWindow(0, clock::get));
    }

    @Test
    void shouldSnapshotSlowButMovingArchive_pastTheOldDuration() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Path archive = archive(source, 30);
        Path durable = Files.createDirectory(temp.resolve("durable"));
        var clock = new AtomicLong();
        var checkpoints = new AtomicInteger();
        var service = new CdsSnapshotService(path -> Long.MAX_VALUE, clock::get);
        var snapshot =
                service.snapshot(
                        archive,
                        source,
                        durable,
                        "accepted",
                        limits(1000),
                        () -> false,
                        () -> {
                            // Every checkpoint is 300ms after the last one: never silent for the
                            // one-second window, however many there are.
                            checkpoints.incrementAndGet();
                            clock.addAndGet(300 * MILLIS);
                        });
        assertEquals(31, snapshot.files());
        assertTrue(
                clock.get() > TimeUnit.SECONDS.toNanos(10),
                "The copy took many times the window and was not stopped");
        assertTrue(Files.isRegularFile(durable.resolve("accepted/snapshot.seal")));
        assertTrue(checkpoints.get() > 30);
    }

    @Test
    void shouldEndStalledSnapshot_withoutPublishingAnything() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Path archive = archive(source, 30);
        byte[] original = Files.readAllBytes(archive);
        Path durable = Files.createDirectory(temp.resolve("durable"));
        var clock = new AtomicLong();
        var checkpoints = new AtomicInteger();
        var service = new CdsSnapshotService(path -> Long.MAX_VALUE, clock::get);
        CdsInputException stalled =
                assertThrows(
                        CdsInputException.class,
                        () ->
                                service.snapshot(
                                        archive,
                                        source,
                                        durable,
                                        "stalled",
                                        limits(1000),
                                        () -> false,
                                        () -> {
                                            // One read never answers for longer than the window.
                                            clock.addAndGet(
                                                    (checkpoints.incrementAndGet() == 12
                                                                    ? 1500
                                                                    : 10)
                                                            * MILLIS);
                                        }));
        assertEquals("IMPORT_WORKER_STALLED", stalled.code());
        assertFalse(Files.exists(durable.resolve("stalled")), "Nothing is published");
        assertFalse(Files.exists(durable.resolve("stalled.pending/snapshot.seal")));
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(archive)));
    }

    private static CdsInputLimitsDto limits(long windowMillis) {
        return new CdsInputLimitsDto(256, 65536, 1, windowMillis, 8192);
    }

    private static Path archive(Path source, int notes) throws Exception {
        Path archive = source.resolve("export.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("patient.xml"));
            zip.write(
                    "<OmdCds xmlns='cds'><PatientRecord/></OmdCds>"
                            .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (int i = 0; i < notes; i++) {
                zip.putNextEntry(new ZipEntry("notes/note-" + i + ".txt"));
                zip.write(("note " + i).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }
}
