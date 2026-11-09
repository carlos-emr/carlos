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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportVerifiedRangesUnitTest {
    @TempDir Path root;
    private static final String RUN = "22222222-2222-4222-8222-222222222222";
    private static final int CHUNK = 65536, HEADER = 80;
    private final byte[] key = new byte[32];
    private CdsExportStagingService store;

    @BeforeEach
    void setup() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Arrays.fill(key, (byte) 37);
        store = store(() -> {});
    }

    private CdsExportStagingService store(Runnable checkpoint) {
        return new CdsExportStagingService(
                root, RUN, 1, new SecretKeySpec(key, "AES"), 16L << 20, 0, checkpoint);
    }

    private byte[] bytes(int length) {
        var result = new byte[length];
        new Random(483).nextBytes(result);
        return result;
    }

    private CdsExportStagedFileDto stage(byte[] bytes) throws IOException {
        return store.stage("RETAINED_VALUES", output -> output.write(bytes));
    }

    private Path path(CdsExportStagedFileDto file) {
        return root.resolve(file.memberId() + ".stage");
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void change(CdsExportStagedFileDto file, int position) throws Exception {
        byte[] encrypted = Files.readAllBytes(path(file));
        encrypted[position] ^= 1;
        Files.write(path(file), encrypted);
    }

    @Test
    void readsExactRanges_inAnyOrderAcrossEveryBoundary() throws Exception {
        byte[] original = bytes(CHUNK * 3 + 29);
        var file = stage(original);
        try (var ranges = store.verifiedRanges(file)) {
            for (int[] pair :
                    new int[][] {
                        {CHUNK * 3, 29},
                        {0, 1},
                        {CHUNK - 5, 17},
                        {CHUNK, CHUNK},
                        {0, 0},
                        {original.length, 0},
                        {2, original.length - 2}
                    }) {
                byte[] expected = Arrays.copyOfRange(original, pair[0], pair[0] + pair[1]);
                try (var input = ranges.open(pair[0], pair[1], sha(expected))) {
                    assertThat(input.readAllBytes()).isEqualTo(expected);
                    assertThat(input.read()).isEqualTo(-1);
                }
            }
        }
    }

    @Test
    void authenticatesAnEmptyMemberAndEmptyRange() throws Exception {
        var file = stage(new byte[0]);
        try (var ranges = store.verifiedRanges(file);
                var input = ranges.open(0, 0, sha(new byte[0]))) {
            assertThat(input.read()).isEqualTo(-1);
        }
    }

    @Test
    void authenticatesUnrequestedSuffixBeforeOpening() throws Exception {
        var file = stage(bytes(CHUNK * 3));
        change(file, HEADER + 2 * (CHUNK + 20) + 12);
        assertThatThrownBy(() -> store.verifiedRanges(file))
                .hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
    }

    @Test
    void authenticatesUnrequestedSuffixAgainBeforeAcceptingScope() throws Exception {
        byte[] original = bytes(CHUNK * 3);
        var file = stage(original);
        var ranges = store.verifiedRanges(file);
        change(file, HEADER + 2 * (CHUNK + 20) + 12);
        try (var input = ranges.open(0, 1, sha(Arrays.copyOf(original, 1)))) {
            assertThat(input.readAllBytes()).containsExactly(original[0]);
        }
        assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
    }

    @Test
    void authenticatesUnrequestedPrefixAgainBeforeAcceptingScope() throws Exception {
        byte[] original = bytes(CHUNK * 3);
        var file = stage(original);
        var ranges = store.verifiedRanges(file);
        change(file, HEADER + 12);
        byte[] last = Arrays.copyOfRange(original, CHUNK * 2, CHUNK * 2 + 1);
        try (var input = ranges.open(CHUNK * 2, 1, sha(last))) {
            assertThat(input.readAllBytes()).isEqualTo(last);
        }
        assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
    }

    @Test
    void rejectsTamperedRequestedChunkBeforeReleasingPlaintext_andFailureSticks() throws Exception {
        byte[] original = bytes(CHUNK * 2);
        var file = stage(original);
        var ranges = store.verifiedRanges(file);
        change(file, HEADER + 12);
        var input = ranges.open(0, 1, sha(Arrays.copyOf(original, 1)));
        assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
        assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
        input.close();
        assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
    }

    @Test
    void rejectsWrongFieldHash_evenOnEarlyClose() throws Exception {
        var file = stage(bytes(CHUNK * 2));
        var ranges = store.verifiedRanges(file);
        var input = ranges.open(3, CHUNK, "a".repeat(64));
        assertThat(input.read()).isBetween(0, 255);
        assertThatThrownBy(input::close).hasMessage("CDS_EXPORT_VALUE_RANGE_DIGEST_MISMATCH");
        assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
    }

    @Test
    void earlyCloseAuthenticatesWholeField_thenAllowsNextField() throws Exception {
        byte[] original = bytes(CHUNK * 3);
        var file = stage(original);
        try (var ranges = store.verifiedRanges(file)) {
            try (var input = ranges.open(0, original.length, sha(original))) {
                assertThat(input.read()).isBetween(0, 255);
            }
            try (var input = ranges.open(0, 0, sha(new byte[0]))) {
                assertThat(input.read()).isEqualTo(-1);
            }
        }
    }

    @Test
    void rejectsBoundsAndDoesNotRecoverAfterCaughtInvalidRequest() throws Exception {
        var file = stage(bytes(9));
        for (long[] pair :
                new long[][] {
                    {-1, 1}, {0, -1}, {10, 0}, {8, 2}, {Long.MAX_VALUE, 1}, {0, Long.MAX_VALUE}
                }) {
            var ranges = store.verifiedRanges(file);
            assertThatThrownBy(() -> ranges.open(pair[0], pair[1], "a".repeat(64)))
                    .hasMessage("CDS_EXPORT_VALUE_RANGE_INVALID");
            assertThatThrownBy(() -> ranges.open(0, 0, "a".repeat(64)))
                    .hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
            assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
        }
    }

    @Test
    void rejectsUnclosedFieldEvenIfItReachedEof() throws Exception {
        var file = stage(new byte[0]);
        var ranges = store.verifiedRanges(file);
        var input = ranges.open(0, 0, sha(new byte[0]));
        assertThat(input.read()).isEqualTo(-1);
        assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_VERIFIED_RANGE_INCOMPLETE");
        assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
        input.close();
    }

    @Test
    void rejectsOverlappingOpenFields() throws Exception {
        var file = stage(new byte[0]);
        var ranges = store.verifiedRanges(file);
        var input = ranges.open(0, 0, sha(new byte[0]));
        assertThatThrownBy(() -> ranges.open(0, 0, sha(new byte[0])))
                .hasMessage("CDS_EXPORT_VALUE_RANGE_INVALID");
        input.close();
        assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
    }

    @Test
    void rejectsScopeUseAfterClose() throws Exception {
        var file = stage(bytes(3));
        var ranges = store.verifiedRanges(file);
        ranges.close();
        ranges.close();
        assertThatThrownBy(() -> ranges.open(0, 0, sha(new byte[0])))
                .hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
    }

    @Test
    void rejectsForeignThreadAndCannotSilentlyRecover() throws Exception {
        var file = stage(bytes(3));
        var ranges = store.verifiedRanges(file);
        var failure = new AtomicReference<Throwable>();
        Thread other =
                new Thread(
                        () -> {
                            try {
                                ranges.open(0, 0, "a".repeat(64));
                            } catch (Throwable caught) {
                                failure.set(caught);
                            }
                        });
        other.start();
        other.join();
        assertThat(failure.get()).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
        assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
    }

    @Test
    void rejectsAppendedOrTruncatedFileDuringScope() throws Exception {
        for (boolean append : new boolean[] {true, false}) {
            var file = stage(bytes(CHUNK));
            var ranges = store.verifiedRanges(file);
            byte[] encrypted = Files.readAllBytes(path(file));
            Files.write(path(file), Arrays.copyOf(encrypted, encrypted.length + (append ? 1 : -1)));
            assertThatThrownBy(() -> ranges.open(0, 1, "a".repeat(64)))
                    .hasMessage("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
            assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
        }
    }

    @Test
    void rejectsWrongWholeHashOrLengthBeforeOpening() throws Exception {
        var file = stage(bytes(CHUNK + 3));
        for (var wrong :
                new CdsExportStagedFileDto[] {
                    new CdsExportStagedFileDto(
                            file.memberId(), file.purpose(), "a".repeat(64), file.plaintextBytes()),
                    new CdsExportStagedFileDto(
                            file.memberId(),
                            file.purpose(),
                            file.plaintextSha256(),
                            file.plaintextBytes() - 1)
                }) {
            assertThatThrownBy(() -> store.verifiedRanges(wrong))
                    .hasMessage("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
        }
    }

    @Test
    void rejectsWrongPurposeEpochAndKey() throws Exception {
        var file = stage(bytes(3));
        var wrongPurpose =
                new CdsExportStagedFileDto(
                        file.memberId(), "REPORT", file.plaintextSha256(), file.plaintextBytes());
        assertThatThrownBy(() -> store.verifiedRanges(wrongPurpose))
                .hasMessage("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
        var wrongEpoch =
                new CdsExportStagingService(
                        root, RUN, 2, new SecretKeySpec(key, "AES"), 16L << 20, 0, () -> {});
        assertThatThrownBy(() -> wrongEpoch.verifiedRanges(file))
                .hasMessage("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
        var wrongKey =
                new CdsExportStagingService(
                        root,
                        RUN,
                        1,
                        new SecretKeySpec(new byte[32], "AES"),
                        16L << 20,
                        0,
                        () -> {});
        assertThatThrownBy(() -> wrongKey.verifiedRanges(file))
                .hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
    }

    @Test
    void pinsOriginalDescriptorThroughPathReplacement() throws Exception {
        byte[] original = bytes(CHUNK + 3);
        var file = stage(original);
        try (var ranges = store.verifiedRanges(file)) {
            Files.move(path(file), root.resolve("original.stage"));
            Files.write(path(file), new byte[3], StandardOpenOption.CREATE_NEW);
            try (var input = ranges.open(0, original.length, sha(original))) {
                assertThat(input.readAllBytes()).isEqualTo(original);
            }
        }
    }

    @Test
    void propagatesCancellationAfterInitialAuthentication_andFailureSticks() throws Exception {
        var file = stage(bytes(CHUNK * 2));
        var cancelled = new AtomicBoolean();
        var checked =
                store(
                        () -> {
                            if (cancelled.get()) throw new IllegalStateException("FENCED");
                        });
        var ranges = checked.verifiedRanges(file);
        cancelled.set(true);
        assertThatThrownBy(() -> ranges.open(0, 1, "a".repeat(64))).hasMessage("FENCED");
        cancelled.set(false);
        assertThatThrownBy(ranges::close).hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
    }

    @Test
    void finalAuthenticationRemainsCancellable() throws Exception {
        var file = stage(bytes(CHUNK * 2));
        var cancelled = new AtomicBoolean();
        var checked =
                store(
                        () -> {
                            if (cancelled.get()) throw new IllegalStateException("FENCED");
                        });
        var ranges = checked.verifiedRanges(file);
        cancelled.set(true);
        assertThatThrownBy(ranges::close).hasMessage("FENCED");
        assertThatThrownBy(() -> ranges.open(0, 0, "a".repeat(64)))
                .hasMessage("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
    }

    @Test
    void manySmallFieldsHaveBoundedChunkWork_insteadOfOneWholeScanPerField() throws Exception {
        byte[] original = bytes(CHUNK * 32);
        var file = stage(original);
        var checks = new AtomicInteger();
        var checked = store(checks::incrementAndGet);
        try (var ranges = checked.verifiedRanges(file)) {
            for (int i = 0; i < 128; i++) {
                int start = (i * 13007) % (original.length - 20);
                byte[] expected = Arrays.copyOfRange(original, start, start + 20);
                try (var input = ranges.open(start, 20, sha(expected))) {
                    assertThat(input.readAllBytes()).isEqualTo(expected);
                }
            }
        }
        assertThat(checks.get()).isLessThan(1500);
    }
}
