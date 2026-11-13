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
        settings.setProperty("cds.export.maxDocumentBytes", "1024");
        return new CdsExportEformRenderService(renderer, settings, System::nanoTime);
    }

    @Test
    void shouldSpoolTheCompleteRenderAndDeleteTheRendererFile_withItsRenderTime() throws Exception {
        var made = new RenderedEformPdf[1];
        LoggedInInfo[] seen = new LoggedInInfo[1];
        var service =
                service(
                        (identity, fdid) -> {
                            seen[0] = identity;
                            made[0] = output(PDF);
                            return made[0];
                        },
                        "0");

        var outcome = service.render(42, who, spool, () -> {});

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
                        (identity, fdid) -> {
                            throw new EformContentUnavailableException(
                                    "omitted", fdid, EFormRenderCompletenessReport.complete());
                        },
                        "0");

        var outcome = service.render(42, who, spool, () -> {});

        assertThat(outcome.rendered()).isFalse();
        assertThat(outcome.reason()).isEqualTo(Reason.INCOMPLETE);
        assertThat(spool.members).isEmpty();
    }

    @Test
    void shouldClassifyTimeoutsCapacityOversizeAndNonPdfOutput_asNotedReasons() throws Exception {
        assertThat(
                        service(
                                        (identity, fdid) -> {
                                            throw new PDFGenerationException(
                                                    "Browser rendering timed out while generating the eForm PDF.");
                                        },
                                        "0")
                                .render(1, who, spool, () -> {})
                                .reason())
                .isEqualTo(Reason.TIMEOUT);
        assertThat(
                        service(
                                        (identity, fdid) -> {
                                            throw new PDFGenerationException("at capacity", true);
                                        },
                                        "0")
                                .render(1, who, spool, () -> {})
                                .reason())
                .isEqualTo(Reason.RENDERER_BUSY);
        assertThat(service((identity, fdid) -> output(new byte[2048]), "0").render(1, who, spool, () -> {}).reason())
                .isEqualTo(Reason.TOO_LARGE);
        assertThat(
                        service((identity, fdid) -> output("<html>".getBytes(StandardCharsets.US_ASCII)), "0")
                                .render(1, who, spool, () -> {})
                                .reason())
                .isEqualTo(Reason.BROWSER_FAILURE);
        assertThat(new CdsExportEformRenderService(null, new Properties(), System::nanoTime)
                        .render(1, who, spool, () -> {})
                        .reason())
                .isEqualTo(Reason.NOT_IN_THIS_PROCESS);
    }

    @Test
    void shouldStopCallingABrokenRenderer_afterThreeBrowserFailuresInARow() throws Exception {
        var calls = new AtomicInteger();
        var service =
                service(
                        (identity, fdid) -> {
                            calls.incrementAndGet();
                            throw new PDFGenerationException("Browser rendering failed while generating the eForm PDF.");
                        },
                        "0");

        for (int i = 0; i < 3; i++)
            assertThat(service.render(i + 1, who, spool, () -> {}).reason())
                    .isEqualTo(Reason.BROWSER_FAILURE);
        var skipped = service.render(4, who, spool, () -> {});

        assertThat(skipped.reason()).isEqualTo(Reason.RENDERER_UNAVAILABLE);
        assertThat(calls).hasValue(3);
    }

    @Test
    void shouldPropagateACancellationAfterTheRender_withoutCountingItAsABrowserFailure()
            throws Exception {
        var made = new RenderedEformPdf[1];
        var service =
                service(
                        (identity, fdid) -> {
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
                                            if (checks.incrementAndGet() == 2)
                                                throw new java.util.concurrent.CancellationException(
                                                        "CDS_EXPORT_WAIT_INTERRUPTED");
                                        }))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(made[0].path()).doesNotExist();
        assertThat(spool.members).isEmpty();
        assertThat(CdsExportEformRenderService.EXPORT_RENDERS.availablePermits()).isEqualTo(1);
        assertThat(service.render(2, who, spool, () -> {}).rendered()).isTrue();
    }

    @Test
    void shouldPassARefusedIdentityThrough_asAnAuthorizationChange() {
        var service =
                service(
                        (identity, fdid) -> {
                            throw new SecurityException("missing required sec object (_eform)");
                        },
                        "0");

        assertThatThrownBy(() -> service.render(1, who, spool, () -> {}))
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
                        (identity, fdid) -> {
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
                                service.render(2, who, new Spool(), () -> {});
                            } catch (Exception failed) {
                                throw new IllegalStateException(failed);
                            }
                        });
        second.start();
        service.render(1, who, spool, () -> {});
        second.join(10_000);

        assertThat(most).hasValue(1);
        assertThat(TimeUnit.NANOSECONDS.toMillis(startedAt[1] - finishedAt[0]))
                .isGreaterThanOrEqualTo(140);
    }
}
