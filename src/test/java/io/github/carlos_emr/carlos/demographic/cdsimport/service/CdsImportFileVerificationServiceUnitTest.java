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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

@Tag("unit")
class CdsImportFileVerificationServiceUnitTest {
    @TempDir Path root;

    @Test
    void shouldVerifyExactBytesAndPublishProof_whenSmallChunksRemainOwned() throws Exception {
        Fixture f = new Fixture();
        f.slowRead(1_000_000, ignored -> {});
        verify(f.files).verifiedIfOwned("run", 1, "file", f.file.digest(), f.file.byteLength());
        assertThat(f.reads).hasValueGreaterThan(2);
        verify(f.fields, atLeast(f.reads.get())).checkpoint();
    }

    @Test
    void shouldStopBeforeSixteenMiB_whenCancellationArrivesDuringSlowSmallReads() throws Exception {
        Fixture f = new Fixture();
        when(f.job.cancelRequested()).thenAnswer(call -> f.reads.get() >= 2);
        assertThatThrownBy(() -> f.slowRead(300_000_000, ignored -> {}))
                .hasMessage("IMPORT_CANCELLED");
        assertThat(f.reads).hasValue(2);
        f.noProof();
    }

    @Test
    void shouldStopBeforeSixteenMiB_whenEpochChangesDuringSlowSmallReads() throws Exception {
        Fixture f = new Fixture();
        when(f.job.ownerEpoch()).thenAnswer(call -> f.reads.get() >= 2 ? 2L : 1L);
        assertThatThrownBy(() -> f.slowRead(300_000_000, ignored -> {})).hasMessage("OWNER_FENCED");
        assertThat(f.reads).hasValue(2);
        f.noProof();
    }

    @Test
    void shouldStopBeforeSixteenMiB_whenAdmissionChangesWithoutJobEpochChange() throws Exception {
        Fixture f = new Fixture();
        when(f.ledger.ownsAdmission("run", 1)).thenAnswer(call -> f.reads.get() < 2);
        assertThatThrownBy(() -> f.slowRead(300_000_000, ignored -> {})).hasMessage("OWNER_FENCED");
        assertThat(f.reads).hasValue(2);
        f.noProof();
    }

    @Test
    void shouldStopBeforeSixteenMiB_whenSlowSmallReadsExhaustElapsedBudget() throws Exception {
        Fixture f = new Fixture();
        assertThatThrownBy(() -> f.slowRead(300_000_000, ignored -> {}))
                .hasMessage("IMPORT_FILE_VERIFICATION_TIMEOUT");
        assertThat(f.reads).hasValue(4);
        f.noProof();
    }

    @Test
    void shouldStopOnNextBoundedRead_whenScopedCoordinatorFails() throws Exception {
        Fixture f = new Fixture();
        doAnswer(
                        call -> {
                            if (f.reads.get() >= 2)
                                throw new CdsImportException("IMPORT_COORDINATOR_LOST");
                            return null;
                        })
                .when(f.fields)
                .checkpoint();
        assertThatThrownBy(() -> f.slowRead(1_000_000, ignored -> {}))
                .hasMessage("IMPORT_COORDINATOR_LOST");
        assertThat(f.reads).hasValue(2);
        f.noProof();
    }

    @Test
    void shouldStopOnNextBoundedRead_whenThreadIsInterrupted() throws Exception {
        Fixture f = new Fixture();
        try {
            assertThatThrownBy(
                            () ->
                                    f.slowRead(
                                            1_000_000,
                                            count -> {
                                                if (count == 2) Thread.currentThread().interrupt();
                                            }))
                    .hasMessage("IMPORT_CANCELLED");
            assertThat(f.reads).hasValue(2);
            f.noProof();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldRefuseVerificationProof_whenImmutableContentDigestMismatches() throws Exception {
        Fixture f = new Fixture();
        f.file =
                new CdsImportFileDto(
                        "file",
                        f.path.toString(),
                        "0".repeat(64),
                        f.file.byteLength(),
                        "PUBLISHED",
                        1);
        when(f.files.referencedFiles("run", "", 100)).thenReturn(List.of(f.file));
        assertThatThrownBy(() -> f.slowRead(1_000_000, ignored -> {}))
                .hasMessage("REFERENCED_FILE_DIGEST_MISMATCH");
        f.noProof();
    }

    private final class Fixture {
        final CdsImportFileService files = mock(CdsImportFileService.class);
        final CdsImportLedgerService ledger = mock(CdsImportLedgerService.class);
        final CdsImportConfigurationService configuration =
                mock(CdsImportConfigurationService.class);
        final CdsFieldParser fields = mock(CdsFieldParser.class);
        final CdsImportJobDto job = mock(CdsImportJobDto.class);
        final AtomicLong clock = new AtomicLong();
        final AtomicInteger reads = new AtomicInteger();
        final Path path;
        CdsImportFileDto file;

        Fixture() throws Exception {
            path =
                    Files.writeString(
                            root.resolve("value.bin"),
                            "Synthetic immutable bytes read in deliberately tiny chunks.");
            byte[] content = Files.readAllBytes(path);
            var digest = CdsPaths.sha256();
            digest.update(content);
            file =
                    new CdsImportFileDto(
                            "file",
                            path.toString(),
                            CdsPaths.hex(digest.digest()),
                            content.length,
                            "PUBLISHED",
                            1);
            when(configuration.stagingRoot()).thenReturn(root);
            var limits = mock(CdsInputLimitsDto.class);
            when(limits.maxDurationMillis()).thenReturn(1000L);
            when(configuration.limits()).thenReturn(limits);
            when(files.referencedFiles("run", "", 100)).thenReturn(List.of(file));
            when(files.referencedFiles("run", "file", 100)).thenReturn(List.of());
            when(ledger.status("run")).thenReturn(job);
            when(job.ownerEpoch()).thenReturn(1L);
            when(ledger.ownsAdmission("run", 1)).thenReturn(true);
        }

        void slowRead(long nanosPerRead, IntConsumer afterRead) throws Exception {
            InputStream physical =
                    Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            InputStream slow =
                    new FilterInputStream(physical) {
                        @Override
                        public int read(byte[] bytes, int offset, int length) throws IOException {
                            int count = super.read(bytes, offset, Math.min(length, 7));
                            if (count > 0) {
                                clock.addAndGet(nanosPerRead);
                                afterRead.accept(reads.incrementAndGet());
                            }
                            return count;
                        }
                    };
            try (slow;
                    var nativePaths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
                    var fileIo = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                nativePaths
                        .when(PathValidationUtils::getRequiredDocumentDirectory)
                        .thenReturn(root.toFile());
                fileIo.when(
                                () ->
                                        Files.newInputStream(
                                                path,
                                                StandardOpenOption.READ,
                                                LinkOption.NOFOLLOW_LINKS))
                        .thenReturn(slow);
                new CdsImportFileVerificationService(
                                files, ledger, configuration, fields, clock::get)
                        .verify("run", 1);
            }
        }

        void noProof() {
            verify(files, never())
                    .verifiedIfOwned(anyString(), anyLong(), anyString(), anyString(), anyLong());
        }
    }
}
