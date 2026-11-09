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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportStagingServiceUnitTest {
    @TempDir Path root;
    private CdsExportStagingService store;
    private static final String RUN = "22222222-2222-4222-8222-222222222222";
    private final byte[] key = new byte[32];

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Arrays.fill(key, (byte) 37);
        store =
                new CdsExportStagingService(
                        root, RUN, 1, new SecretKeySpec(key, "AES"), 16L << 20, 0, () -> {});
    }

    @Test
    void shouldAuthenticateLargeFiles_inBoundedIndependentChunks() throws Exception {
        byte[] bytes =
                ("CONFIDENTIAL synthetic café 😀\r\n")
                        .repeat(100000)
                        .getBytes(StandardCharsets.UTF_8);
        var file = store.stage("PATIENT_XML", output -> output.write(bytes));
        assertThat(file.plaintextBytes()).isEqualTo(bytes.length);
        assertThat(file.plaintextSha256())
                .isEqualTo(
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        try (var input = store.open(file)) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
        byte[] encrypted = Files.readAllBytes(path(file));
        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1))
                .doesNotContain("CONFIDENTIAL");
        assertThat(Files.getPosixFilePermissions(path(file)))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void shouldAuthenticateEmptyFiles_withATerminatorAndIdentity() throws Exception {
        var file = store.stage("EMPTY", output -> {});
        try (var input = store.open(file)) {
            assertThat(input.read()).isEqualTo(-1);
        }
        assertThat(file.plaintextBytes()).isZero();
    }

    @Test
    void shouldRejectTamperedChunks_beforeReleasingTheirPlaintext() throws Exception {
        var file =
                store.stage(
                        "PATIENT_XML",
                        output -> output.write("private".getBytes(StandardCharsets.UTF_8)));
        byte[] bytes = Files.readAllBytes(path(file));
        bytes[85] ^= 1;
        Files.write(path(file), bytes);
        try (var input = store.open(file)) {
            assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
        }
    }

    @Test
    void shouldRejectWrongDigestOrLength_evenWhenChunksAuthenticate() throws Exception {
        var file = store.stage("PATIENT_XML", output -> output.write(new byte[200000]));
        for (var wrong :
                new CdsExportStagedFileDto[] {
                    new CdsExportStagedFileDto(
                            file.memberId(), file.purpose(), "a".repeat(64), file.plaintextBytes()),
                    new CdsExportStagedFileDto(
                            file.memberId(), file.purpose(), file.plaintextSha256(), 1)
                }) {
            try (var input = store.open(wrong)) {
                assertThatThrownBy(input::readAllBytes)
                        .hasMessage("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
            }
        }
    }

    @Test
    void shouldRejectTruncationOrAppend_withoutAcceptingAnAuthenticatedPrefix() throws Exception {
        var file = store.stage("PATIENT_XML", output -> output.write(new byte[100000]));
        byte[] bytes = Files.readAllBytes(path(file));
        Files.write(path(file), Arrays.copyOf(bytes, bytes.length - 1));
        try (var input = store.open(file)) {
            assertThatThrownBy(input::readAllBytes).hasMessage("CDS_EXPORT_STAGED_TRUNCATED");
        }
        Files.write(path(file), Arrays.copyOf(bytes, bytes.length + 1));
        try (var input = store.open(file)) {
            assertThatThrownBy(input::readAllBytes)
                    .hasMessage("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
        }
    }

    @Test
    void shouldBindEncryptedFiles_toJobEpochMemberAndPurpose() throws Exception {
        var file = store.stage("PATIENT_XML", output -> output.write(1));
        var wrongEpoch =
                new CdsExportStagingService(
                        root, RUN, 2, new SecretKeySpec(key, "AES"), 16L << 20, 0, () -> {});
        assertThatThrownBy(() -> wrongEpoch.open(file))
                .hasMessage("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
        var wrongPurpose =
                new CdsExportStagedFileDto(
                        file.memberId(), "REPORT", file.plaintextSha256(), file.plaintextBytes());
        assertThatThrownBy(() -> store.open(wrongPurpose))
                .hasMessage("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
        String another = "b".repeat(64);
        Files.copy(path(file), root.resolve(another + ".stage"));
        var wrongMember =
                new CdsExportStagedFileDto(
                        another, file.purpose(), file.plaintextSha256(), file.plaintextBytes());
        assertThatThrownBy(() -> store.open(wrongMember))
                .hasMessage("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
    }

    @Test
    void shouldEraseFailedWrites_withoutReturningAnArtifactHandle() throws Exception {
        assertThatThrownBy(
                        () ->
                                store.stage(
                                        "PATIENT_XML",
                                        output -> {
                                            output.write(new byte[100000]);
                                            throw new IOException("COPY_FAILED");
                                        }))
                .hasMessage("COPY_FAILED");
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldRefuseResourceLimits_withoutTruncatingTheStoredFile() throws Exception {
        var small =
                new CdsExportStagingService(
                        root, RUN, 1, new SecretKeySpec(key, "AES"), 3, 0, () -> {});
        assertThatThrownBy(() -> small.stage("PATIENT_XML", output -> output.write(new byte[4])))
                .hasMessage("CDS_EXPORT_STAGED_BYTE_LIMIT");
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldPropagateCheckpointFailure_beforeReturningStoredBytes() throws Exception {
        var file = store.stage("PATIENT_XML", output -> output.write(1));
        var cancelled =
                new CdsExportStagingService(
                        root,
                        RUN,
                        1,
                        new SecretKeySpec(key, "AES"),
                        16L << 20,
                        0,
                        () -> {
                            throw new IllegalStateException("FENCED");
                        });
        assertThatThrownBy(() -> cancelled.open(file)).hasMessage("FENCED");
    }

    @Test
    void shouldEraseOnlyAnAuthenticatedOwnedUnpublishedMember_andAllowIdempotentCleanup()
            throws Exception {
        var file =
                store.stage(
                        "PATIENT_XML",
                        output -> output.write("private".getBytes(StandardCharsets.UTF_8)));
        var wrong =
                new CdsExportStagingService(
                        root, RUN, 2, new SecretKeySpec(key, "AES"), 16L << 20, 0, () -> {});
        assertThatThrownBy(() -> wrong.discardUnpublished(file))
                .hasMessage("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
        assertThat(Files.exists(path(file))).isTrue();
        store.discardUnpublished(file);
        store.discardUnpublished(file);
        assertThat(Files.exists(path(file))).isFalse();
    }

    @Test
    void shouldRetainAnUnverifiableMember_forOwnedReconciliation() throws Exception {
        var file = store.stage("PATIENT_XML", output -> output.write(new byte[100000]));
        byte[] bytes = Files.readAllBytes(path(file));
        bytes[85] ^= 1;
        Files.write(path(file), bytes);
        assertThatThrownBy(() -> store.discardUnpublished(file))
                .hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
        assertThat(Files.exists(path(file))).isTrue();
    }

    @Test
    void shouldCleanUpOwnedUnpublishedBytes_afterCancellation() throws Exception {
        var file = store.stage("PATIENT_XML", output -> output.write(1));
        var cancelled =
                new CdsExportStagingService(
                        root,
                        RUN,
                        1,
                        new SecretKeySpec(key, "AES"),
                        16L << 20,
                        0,
                        () -> {
                            throw new IllegalStateException("CDS_EXPORT_CANCELLED");
                        });
        cancelled.discardUnpublished(file);
        assertThat(Files.exists(path(file))).isFalse();
    }

    @Test
    void shouldNeverAttestAWrite_whenTheProducerSwallowsItsFailure() throws Exception {
        var small =
                new CdsExportStagingService(
                        root, RUN, 1, new SecretKeySpec(key, "AES"), 3, 0, () -> {});
        assertThatThrownBy(
                        () ->
                                small.stage(
                                        "PATIENT_XML",
                                        output -> {
                                            output.write(1);
                                            try {
                                                output.write(new byte[4]);
                                            } catch (IOException swallowed) {
                                                /* deliberately broken producer */
                                            }
                                        }))
                .hasMessage("CDS_EXPORT_STAGED_WRITE_FAILED");
        assertThatThrownBy(
                        () ->
                                small.stage(
                                        "PATIENT_XML",
                                        output -> {
                                            output.write(1);
                                            output.close();
                                            try {
                                                output.write(2);
                                            } catch (IOException swallowed) {
                                                /* deliberately broken producer */
                                            }
                                        }))
                .hasMessage("CDS_EXPORT_STAGED_WRITE_FAILED");
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldNeverReturnEof_afterAnAuthenticationOrIdentityFailure() throws Exception {
        var file = store.stage("PATIENT_XML", output -> output.write(1));
        var wrong = new CdsExportStagedFileDto(file.memberId(), file.purpose(), "a".repeat(64), 1);
        try (var input = store.open(wrong)) {
            assertThat(input.read()).isEqualTo(1);
            assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
            assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_STAGED_READ_FAILED");
        }
        byte[] bytes = Files.readAllBytes(path(file));
        bytes[85] ^= 1;
        Files.write(path(file), bytes);
        try (var input = store.open(file)) {
            assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
            assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_STAGED_READ_FAILED");
        }
    }

    private Path path(CdsExportStagedFileDto file) {
        return root.resolve(file.memberId() + ".stage");
    }
}
