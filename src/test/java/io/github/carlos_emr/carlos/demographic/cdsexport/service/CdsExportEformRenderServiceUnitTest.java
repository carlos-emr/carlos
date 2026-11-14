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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportEformRenderService.Reason;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService.RenderedEformPdf;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The export renders eForms one at a time with no approval, measures every attempt, and turns
 * every failure into a reason for the data summary's note: nothing about a render holds a patient.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportEformRenderServiceUnitTest {
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @TempDir Path directory;

    private final LoggedInInfo who = new LoggedInInfo();
    private final Spool spool = new Spool();

    /** An in-memory stand-in for the capture's encrypted spool. */
    static final class Spool implements CdsExportNativeSourceReader.Spool {
        final Map<String, byte[]> members = new HashMap<>();

        @Override
        public CdsExportStagedFileDto stage(String purpose, CdsExportStagingService.Content content)
                throws IOException {
            var bytes = new ByteArrayOutputStream();
            content.write(bytes);
            String id = HexFormat.of().formatHex(sha256(String.valueOf(members.size()).getBytes()));
            members.put(id, bytes.toByteArray());
            return new CdsExportStagedFileDto(
                    id, purpose, HexFormat.of().formatHex(sha256(bytes.toByteArray())), bytes.size());
        }

        @Override
        public InputStream open(CdsExportStagedFileDto file) {
            return new ByteArrayInputStream(members.get(file.memberId()));
        }
    }

    static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private RenderedEformPdf output(byte[] bytes) {
        try {
            Path file = Files.createTempFile(directory, "eform-browser-render-", ".pdf");
            Files.write(file, bytes);
            return new RenderedEformPdf(file);
        } catch (IOException failed) {
            throw new java.io.UncheckedIOException(failed);
        }
    }

    private static CdsExportEformRenderService service(
            CdsExportEformRenderService.Renderer renderer, String intervalMillis) {
        var settings = new Properties();
        settings.setProperty("cds.export.eformRenderIntervalMillis", intervalMillis);
        // Retired: a rendered form of any size is spooled.
        settings.setProperty("cds.export.maxDocumentBytes", "1024");
        return new CdsExportEformRenderService(renderer, settings, System::nanoTime);
    }

    @Test
    void shouldSpoolTheCompleteRenderAndDeleteTheRendererFile_withItsRenderTime() throws Exception {
        var made = new RenderedEformPdf[1];
        LoggedInInfo[] seen = new LoggedInInfo[1];
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            seen[0] = identity;
                            made[0] = output(PDF);
                            return made[0];
                        },
                        "0");

        var outcome = service.render(42, who, spool, () -> {}, () -> {});

        assertThat(outcome.rendered()).isTrue();
        assertThat(outcome.reason()).isNull();
        assertThat(outcome.millis()).isNotNegative();
        assertThat(spool.members.get(outcome.file().memberId())).isEqualTo(PDF);
        assertThat(seen[0]).isSameAs(who);
        assertThat(made[0].path()).doesNotExist();
    }

    @Test
    void shouldNeverApproveAnIncompleteForm_andReportItAsIncomplete() throws Exception {
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            throw new EformContentUnavailableException(
                                    "omitted", fdid, EFormRenderCompletenessReport.complete());
                        },
                        "0");

        var outcome = service.render(42, who, spool, () -> {}, () -> {});

        assertThat(outcome.rendered()).isFalse();
        assertThat(outcome.reason()).isEqualTo(Reason.INCOMPLETE);
        assertThat(spool.members).isEmpty();
    }

    @Test
    void shouldClassifyTimeoutsAndNonPdfOutput_andSpoolARenderOfAnySize() throws Exception {
        assertThat(
                        service(
                                        (identity, fdid, waiting) -> {
                                            throw new PDFGenerationException(
                                                    "Browser rendering timed out while generating the eForm PDF.");
                                        },
                                        "0")
                                .render(1, who, spool, () -> {}, () -> {})
                                .reason())
                .isEqualTo(Reason.TIMEOUT);
        // Before: a render larger than cds.export.maxDocumentBytes was left out (TOO_LARGE).
        byte[] large = java.util.Arrays.copyOf(PDF, 4096);
        var whole = service((identity, fdid, waiting) -> output(large), "0").render(1, who, spool, () -> {}, () -> {});
        assertThat(whole.rendered()).isTrue();
        assertThat(spool.members.get(whole.file().memberId())).isEqualTo(large);
        assertThat(
                        service((identity, fdid, waiting) -> output("<html>".getBytes(StandardCharsets.US_ASCII)), "0")
                                .render(1, who, spool, () -> {}, () -> {})
                                .reason())
                .isEqualTo(Reason.BROWSER_FAILURE);
        assertThat(new CdsExportEformRenderService(null, new Properties(), System::nanoTime)
                        .render(1, who, spool, () -> {}, () -> {})
                        .reason())
                .isEqualTo(Reason.NOT_IN_THIS_PROCESS);
    }

    /** A clock the service reads and its pauses advance: a five-minute wait takes no time. */
    static final class FakeTime implements java.util.function.LongSupplier, CdsExportEformRenderService.Pause {
        long now = 1_000_000_000L;
        final java.util.List<Long> pauses = new java.util.ArrayList<>();

        @Override
        public long getAsLong() {
            return now;
        }

        @Override
        public void pause(long nanos) {
            pauses.add(nanos);
            now += nanos;
        }

        long paused() {
            return pauses.stream().mapToLong(Long::longValue).sum();
        }
    }

    private static CdsExportEformRenderService service(
            CdsExportEformRenderService.Renderer renderer, FakeTime time) {
        var settings = new Properties();
        settings.setProperty("cds.export.eformRenderIntervalMillis", "0");
        return new CdsExportEformRenderService(renderer, settings, time, time);
    }

    private static final String BROWSER_FAILED = "Browser rendering failed while generating the eForm PDF.";

    @Test
    void shouldWaitOutAnOpenBreaker_andRenderTheFormOnceItCloses() throws Exception {
        // Before: after three browser failures in a row every eForm of the next five minutes was
        // marked "the rendering browser was unavailable" without being tried.
        var time = new FakeTime();
        var calls = new AtomicInteger();
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            if (calls.incrementAndGet() <= 3)
                                throw new PDFGenerationException(BROWSER_FAILED);
                            return output(PDF);
                        },
                        time);
        for (int i = 0; i < 3; i++)
            assertThat(service.render(i + 1, who, spool, () -> {}, () -> {}).reason())
                    .isEqualTo(Reason.BROWSER_FAILURE);
        assertThat(time.paused()).isZero();
        var waits = new AtomicInteger();

        var outcome = service.render(4, who, spool, () -> {}, waits::incrementAndGet);

        assertThat(outcome.rendered()).isTrue();
        assertThat(calls).hasValue(4);
        // The five minutes were waited out in pauses of at most a second, each after a check.
        assertThat(time.paused()).isEqualTo(CdsExportEformRenderService.BREAKER_OPEN_NANOS);
        assertThat(time.pauses).allMatch(nanos -> nanos <= CdsExportEformRenderService.CHECK_NANOS);
        assertThat(waits.get()).isGreaterThanOrEqualTo(time.pauses.size());
    }

    @Test
    void shouldWaitForABusyRenderer_andRenderTheFormWithoutAFallback() throws Exception {
        // Before: a render slot not free within 30 seconds left the form "the renderer stayed
        // busy". Now the export keeps its place in the renderer's queue, and when the queue itself
        // is full it asks again later: 1 s, then 2 s.
        var time = new FakeTime();
        var calls = new AtomicInteger();
        var slotChecks = new AtomicInteger();
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            if (calls.incrementAndGet() <= 2)
                                throw new PDFGenerationException("at capacity", true);
                            // Queued: the renderer runs the export's check while it waits.
                            for (int i = 0; i < 3; i++) waiting.run();
                            return output(PDF);
                        },
                        time);
        var waits = new AtomicInteger();

        var outcome =
                service.render(
                        42,
                        who,
                        spool,
                        () -> {},
                        () -> {
                            waits.incrementAndGet();
                            if (calls.get() == 3) slotChecks.incrementAndGet();
                        });

        assertThat(outcome.rendered()).isTrue();
        assertThat(outcome.reason()).isNull();
        assertThat(spool.members.get(outcome.file().memberId())).isEqualTo(PDF);
        assertThat(calls).hasValue(3);
        assertThat(slotChecks).hasValue(3);
        assertThat(time.pauses)
                .containsExactly(
                        CdsExportEformRenderService.CHECK_NANOS,
                        CdsExportEformRenderService.CHECK_NANOS,
                        CdsExportEformRenderService.CHECK_NANOS);
        // A full queue is not the renderer failing: it opens no breaker.
        assertThat(service.render(43, who, spool, () -> {}, () -> {}).rendered()).isTrue();
        assertThat(time.paused()).isEqualTo(TimeUnit.SECONDS.toNanos(3));
    }

    @Test
    void shouldEndTheWaitForTheExportsTurn_atOnceWhenTheJobIsCancelled() throws Exception {
        var calls = new AtomicInteger();
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            calls.incrementAndGet();
                            return output(PDF);
                        },
                        "0");
        var waits = new AtomicInteger();
        // Another export holds the turn.
        CdsExportEformRenderService.EXPORT_RENDERS.acquire();
        try {
            long started = System.nanoTime();
            assertThatThrownBy(
                            () ->
                                    service.render(
                                            1,
                                            who,
                                            spool,
                                            () -> {},
                                            () -> {
                                                if (waits.incrementAndGet() == 2)
                                                    throw new java.util.concurrent
                                                            .CancellationException(
                                                            "CDS_EXPORT_CANCELLED");
                                            }))
                    .isInstanceOf(java.util.concurrent.CancellationException.class);
            // The check runs every second while the export waits for its turn.
            assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)).isLessThan(5);
        } finally {
            CdsExportEformRenderService.EXPORT_RENDERS.release();
        }
        assertThat(calls).hasValue(0);
        assertThat(CdsExportEformRenderService.EXPORT_RENDERS.availablePermits()).isEqualTo(1);
    }

    @Test
    void shouldEndTheWaitForARenderSlot_atOnceWhenTheJobIsCancelled() throws Exception {
        var time = new FakeTime();
        var cancelled = new java.util.concurrent.CancellationException("CDS_EXPORT_CANCELLED");
        var calls = new AtomicInteger();
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            // Queued for a slot until the export's check ends the wait.
                            if (calls.incrementAndGet() == 1) while (true) waiting.run();
                            throw new PDFGenerationException(BROWSER_FAILED);
                        },
                        time);
        var waits = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                service.render(
                                        1,
                                        who,
                                        spool,
                                        () -> {},
                                        () -> {
                                            if (waits.incrementAndGet() == 4) throw cancelled;
                                        }))
                .isSameAs(cancelled);

        // The export's own wait ended: not a browser failure, so it took no step toward the
        // breaker. Three real failures still find it closed (had the cancelled attempt counted,
        // the third would have waited five minutes first).
        assertThat(spool.members).isEmpty();
        assertThat(CdsExportEformRenderService.EXPORT_RENDERS.availablePermits()).isEqualTo(1);
        for (int i = 0; i < 3; i++)
            assertThat(service.render(i + 2, who, spool, () -> {}, () -> {}).reason())
                    .isEqualTo(Reason.BROWSER_FAILURE);
        assertThat(time.paused()).isZero();
    }

    @Test
    void shouldEndTheWaitForAnOpenBreaker_atOnceWhenTheJobIsCancelled() throws Exception {
        var time = new FakeTime();
        var calls = new AtomicInteger();
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            calls.incrementAndGet();
                            throw new PDFGenerationException(BROWSER_FAILED);
                        },
                        time);
        for (int i = 0; i < 3; i++) service.render(i + 1, who, spool, () -> {}, () -> {});
        var waits = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                service.render(
                                        4,
                                        who,
                                        spool,
                                        () -> {},
                                        () -> {
                                            // The turn's check, then one before each pause.
                                            if (waits.incrementAndGet() == 4)
                                                throw new java.util.concurrent.CancellationException(
                                                        "CDS_EXPORT_CANCELLED");
                                        }))
                .isInstanceOf(java.util.concurrent.CancellationException.class);

        // Two seconds of the five minutes, and the renderer was not asked again.
        assertThat(calls).hasValue(3);
        assertThat(time.paused()).isEqualTo(TimeUnit.SECONDS.toNanos(2));
        assertThat(CdsExportEformRenderService.EXPORT_RENDERS.availablePermits()).isEqualTo(1);
    }

    @Test
    void shouldEndThePauseAfterAFullQueue_atOnceWhenTheJobIsCancelled() throws Exception {
        var time = new FakeTime();
        var calls = new AtomicInteger();
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            calls.incrementAndGet();
                            throw new PDFGenerationException("at capacity", true);
                        },
                        time);
        var waits = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                service.render(
                                        1,
                                        who,
                                        spool,
                                        () -> {},
                                        () -> {
                                            if (waits.incrementAndGet() == 3)
                                                throw new java.util.concurrent.CancellationException(
                                                        "CDS_EXPORT_CANCELLED");
                                        }))
                .isInstanceOf(java.util.concurrent.CancellationException.class);

        // The turn's check, the 1 s pause's check, then the first check of the 2 s pause.
        assertThat(calls).hasValue(2);
        assertThat(time.paused()).isEqualTo(TimeUnit.SECONDS.toNanos(1));
        assertThat(CdsExportEformRenderService.EXPORT_RENDERS.availablePermits()).isEqualTo(1);
    }

    @Test
    void shouldTreatAnInterruptedWait_asACancellation() throws Exception {
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            throw new PDFGenerationException("at capacity", true);
                        },
                        "0");
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> service.render(1, who, spool, () -> {}, () -> {}))
                    .isInstanceOf(java.util.concurrent.CancellationException.class);
        } finally {
            Thread.interrupted();
        }
        assertThat(CdsExportEformRenderService.EXPORT_RENDERS.availablePermits()).isEqualTo(1);
    }

    @Test
    void shouldPropagateACancellationAfterTheRender_withoutCountingItAsABrowserFailure()
            throws Exception {
        var made = new RenderedEformPdf[1];
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            made[0] = output(PDF);
                            return made[0];
                        },
                        "0");
        var checks = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                service.render(
                                        1,
                                        who,
                                        spool,
                                        () -> {
                                            if (checks.incrementAndGet() == 1)
                                                throw new java.util.concurrent.CancellationException(
                                                        "CDS_EXPORT_WAIT_INTERRUPTED");
                                        },
                                        () -> {}))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(made[0].path()).doesNotExist();
        assertThat(spool.members).isEmpty();
        assertThat(CdsExportEformRenderService.EXPORT_RENDERS.availablePermits()).isEqualTo(1);
        assertThat(service.render(2, who, spool, () -> {}, () -> {}).rendered()).isTrue();
    }

    @Test
    void shouldPassARefusedIdentityThrough_asAnAuthorizationChange() {
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            throw new SecurityException("missing required sec object (_eform)");
                        },
                        "0");

        assertThatThrownBy(() -> service.render(1, who, spool, () -> {}, () -> {}))
                .isInstanceOf(SecurityException.class);
        assertThat(CdsExportEformRenderService.EXPORT_RENDERS.availablePermits()).isEqualTo(1);
    }

    @Test
    void shouldRenderOneAtATime_andKeepTheConfiguredIntervalBetweenRenders() throws Exception {
        var active = new AtomicInteger();
        var most = new AtomicInteger();
        var started = new CountDownLatch(1);
        var finishedAt = new long[2];
        var startedAt = new long[2];
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            startedAt[fdid - 1] = System.nanoTime();
                            most.accumulateAndGet(active.incrementAndGet(), Math::max);
                            started.countDown();
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            active.decrementAndGet();
                            finishedAt[fdid - 1] = System.nanoTime();
                            return output(PDF);
                        },
                        "150");
        var second =
                new Thread(
                        () -> {
                            try {
                                started.await();
                                service.render(2, who, new Spool(), () -> {}, () -> {});
                            } catch (Exception failed) {
                                throw new IllegalStateException(failed);
                            }
                        });
        second.start();
        service.render(1, who, spool, () -> {}, () -> {});
        second.join(10_000);

        assertThat(most).hasValue(1);
        assertThat(TimeUnit.NANOSECONDS.toMillis(startedAt[1] - finishedAt[0]))
                .isGreaterThanOrEqualTo(140);
    }

    @Test
    void shouldStopTheJob_whenTheRenderersOwnReadLosesTheDatabase() {
        // Not a browser failure: the database is gone, and the job stops under its own code.
        var lost =
                new org.hibernate.exception.JDBCConnectionException(
                        "gone", new java.sql.SQLException("gone", "08S01"));
        var service =
                service(
                        (identity, fdid, waiting) -> {
                            throw lost;
                        },
                        "0");
        assertThatThrownBy(() -> service.render(42, who, spool, () -> {}, () -> {})).isSameAs(lost);
        assertThat(spool.members).isEmpty();
    }

    @Test
    void shouldRunEachOfTheRenderersReadsAsAUnit_andReadItAgainWhenTheServerEndedOne()
            throws Exception {
        var server = new io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitTestServer(60_000);
        var reads = CdsExportEformRenderService.reads(server.manager());
        // The renderer's _eform privilege read: ended at the unit's deadline once, which the
        // shared check answers as a refusal; the read is made again, in a new transaction.
        Boolean allowed =
                reads.run(
                        () -> {
                            if (server.transactions() == 1) {
                                server.endStatement();
                                return false;
                            }
                            return true;
                        });
        assertThat(allowed).isTrue();
        assertThat(server.transactions()).isEqualTo(2);
        assertThat(server.deadlines()).containsExactly(30, 0.0d, 60, 0.0d);
    }
}
