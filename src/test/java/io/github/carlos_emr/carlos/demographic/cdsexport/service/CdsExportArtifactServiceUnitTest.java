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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportEngine;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportArtifactServiceUnitTest {
    @TempDir Path root;
    private final CdsExportLedgerService ledger = mock(CdsExportLedgerService.class);
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private final CdsExportDisclosureScopeService scopes =
            mock(CdsExportDisclosureScopeService.class);
    private final CdsExportDisclosurePolicyService disclosure =
            mock(CdsExportDisclosurePolicyService.class);
    private final CdsExportAccessService access = mock(CdsExportAccessService.class);
    private final CdsExportEngine engine = mock(CdsExportEngine.class);
    private final CdsExportDisclosureScopeService.Verified verified =
            mock(CdsExportDisclosureScopeService.Verified.class);
    private CdsExportArtifactService service;
    private CdsExportLedgerService.Download lease;
    private CdsExportStagingService store;
    private CdsExportActorDto actor;
    private byte[] expected;

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        var run = UUID.randomUUID().toString();
        var command =
                new CdsExportCommand(
                        "download-test",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        "fixture",
                        "fixture");
        actor = new CdsExportActorDto("agent", "999998", null, null);
        var document =
                new CdsExportJobDocumentDto(
                        2,
                        actor,
                        command,
                        "a".repeat(64),
                        "sender",
                        "b".repeat(64),
                        "c".repeat(64),
                        new CdsExportStorageBindingDto("primary", "primary", "d".repeat(64)));
        store =
                new CdsExportStagingService(
                        root,
                        run,
                        1,
                        new SecretKeySpec(new byte[32], "AES"),
                        1L << 20,
                        0,
                        () -> {});
        expected = new byte[130_001];
        new Random(19).nextBytes(expected);
        var cipher = store.stage("DELIVERY_CMS", output -> output.write(expected));
        var zip = new CdsExportPackageDto(file("PACKAGE_ZIP"), 3, 3, "e".repeat(64));
        var c =
                new CdsExportArtifactContextDto(
                        1,
                        run,
                        1,
                        1,
                        document.compatibilityDigest(),
                        document.recipientFingerprint(),
                        document.auditPlanDigest(),
                        file("COHORT_MANIFEST"),
                        file("DISCLOSURE_MANIFEST"),
                        file("ATTEMPT_MEMBERS"),
                        file("SUPPORTING_README"),
                        file("SUPPORTING_EVENT_LOG"),
                        zip,
                        cipher,
                        new CdsExportDeliveryDto(
                                "cms-aes256gcm-rsa-oaep-sha256-v1",
                                document.recipientFingerprint(),
                                zip.archive().plaintextSha256(),
                                zip.archive().plaintextBytes(),
                                cipher.plaintextSha256(),
                                cipher.plaintextBytes()));
        lease =
                new CdsExportLedgerService.Download(
                        run, 1, UUID.randomUUID().toString(), actor.operatorKey(), document, c);
        when(ledger.document(run)).thenReturn(document);
        when(ledger.beginDownload(run, actor.operatorKey())).thenReturn(lease);
        when(stores.openExisting(eq(run), eq(1L), eq(document.storage()), any())).thenReturn(store);
        when(verified.runId()).thenReturn(run);
        when(verified.epoch()).thenReturn(1L);
        when(verified.patients()).thenReturn(1L);
        when(verified.cohortSha256()).thenReturn(c.cohort().plaintextSha256());
        when(verified.authorizationSha256()).thenReturn(c.authorization().plaintextSha256());
        when(scopes.verifyRetained(
                        eq(run),
                        eq(1L),
                        eq(document),
                        eq(c.cohort()),
                        eq(c.authorization()),
                        eq(1L),
                        any()))
                .thenReturn(verified);
        when(engine.compatibilityDigest()).thenReturn(document.compatibilityDigest());
        when(engine.qualifiedProfiles()).thenReturn(Set.of(command.profile()));
        service =
                new CdsExportArtifactService(
                        ledger,
                        stores,
                        scopes,
                        disclosure,
                        access);
    }

    private static CdsExportStagedFileDto file(String purpose) {
        return new CdsExportStagedFileDto(
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                purpose,
                "f".repeat(64),
                1);
    }

    @Test
    void shouldDeliverOnlyAuthenticatedCiphertext_andAuditCompletionOnlyAfterTheAdapterCommits()
            throws Exception {
        try (var download = service.open(actor, lease.runId(), "package")) {
            var output = new ByteArrayOutputStream();
            download.writeTo(output);
            assertThat(output.toByteArray()).isEqualTo(expected);
            assertThat(download.bytes()).isEqualTo(expected.length);
            assertThat(download.sha256()).isEqualTo(lease.context().ciphertext().plaintextSha256());
            verify(ledger, never()).finishDownload(lease, true);
            download.complete();
            verify(ledger).finishDownload(lease, true);
            assertThatThrownBy(() -> download.writeTo(output))
                    .hasMessage("CDS_EXPORT_DOWNLOAD_ALREADY_USED");
        }
        verify(ledger, never()).finishDownload(lease, false);
        verify(scopes, times(3))
                .verifyRetained(any(), anyLong(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void shouldRefuseCallerSuppliedPhysicalMemberIds_beforeAnyMetadataOrFileRead() {
        assertThatThrownBy(
                        () ->
                                service.open(
                                        actor,
                                        lease.runId(),
                                        lease.context().ciphertext().memberId()))
                .hasMessage("CDS_EXPORT_ARTIFACT_ID_INVALID");
        verifyNoInteractions(ledger, stores, access);
    }

    @Test
    void shouldReleaseTheLease_withoutCompletionWhenTheAdapterDoesNotCommit() throws Exception {
        try (var download = service.open(actor, lease.runId(), "package")) {
            download.writeTo(OutputStream.nullOutputStream());
        }
        verify(ledger).finishDownload(lease, false, true);
        verify(ledger, never()).finishDownload(lease, true);
    }

    @Test
    void shouldRefuseACompletion_withoutFullCiphertextEOF() throws Exception {
        try (var download = service.open(actor, lease.runId(), "package")) {
            assertThatThrownBy(download::complete).hasMessage("CDS_EXPORT_DOWNLOAD_NOT_DELIVERED");
        }
        verify(ledger).finishDownload(lease, false, false);
    }

    @Test
    void shouldRejectAnUnboundedDownloadBudget_beforeTakingAnArtifactLease() {
        var settings = new Properties();
        settings.setProperty("cds.export.downloadTimeoutSeconds", "86401");
        service =
                new CdsExportArtifactService(
                        ledger,
                        stores,
                        scopes,
                        disclosure,
                        access,
                        settings);
        assertThatThrownBy(() -> service.open(actor, lease.runId(), "package"))
                .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
        verify(ledger, never()).beginDownload(anyString(), anyString());
    }

    @Test
    void shouldReleaseTheLease_afterAnOutputFailureWithoutClaimingSuccessfulDelivery()
            throws Exception {
        try (var download = service.open(actor, lease.runId(), "package")) {
            assertThatThrownBy(
                            () ->
                                    download.writeTo(
                                            new OutputStream() {
                                                @Override
                                                public void write(int value) throws IOException {
                                                    throw new IOException("OUTPUT_FAILED");
                                                }
                                            }))
                    .hasMessage("OUTPUT_FAILED");
        }
        verify(ledger).finishDownload(lease, false, false);
        verify(ledger, never()).finishDownload(lease, true);
    }

    @Test
    void shouldRefuseAMismatchedRetainedScope_andReleaseTheLeaseBeforeReturningIt() {
        when(verified.authorizationSha256()).thenReturn("a".repeat(64));
        assertThatThrownBy(() -> service.open(actor, lease.runId(), "package"))
                .hasMessage("CDS_EXPORT_DOWNLOAD_SCOPE_INVALID");
        verify(ledger).finishDownload(lease, false, false);
        verify(ledger, never()).finishDownload(lease, true);
    }

    @Test
    void shouldDeliverAReadyPackage_afterTheApplicationBuildOrEngineChanged() throws Exception {
        // Decision 8: an encrypted, published package does not depend on the build that made it.
        when(engine.qualifiedProfiles()).thenReturn(Set.of());
        when(engine.compatibilityDigest()).thenReturn("9".repeat(64));
        try (var download = service.open(actor, lease.runId(), "package")) {
            assertThat(download.bytes()).isPositive();
        }
        verify(ledger).beginDownload(anyString(), anyString());
        verify(disclosure, atLeastOnce()).resolveAccepted(any(), any());
    }

    @Test
    void shouldRefuseSuccessfulDelivery_afterAccessIsRevokedDuringTheOutput() throws Exception {
        try (var download = service.open(actor, lease.runId(), "package")) {
            var output =
                    new ByteArrayOutputStream() {
                        @Override
                        public synchronized void write(byte[] value, int off, int len) {
                            super.write(value, off, len);
                            doThrow(new SecurityException("REVOKED"))
                                    .when(access)
                                    .requireScope(actor, lease.document().command());
                        }
                    };
            assertThatThrownBy(() -> download.writeTo(output)).hasMessage("REVOKED");
        }
        verify(ledger).finishDownload(lease, false, true);
        verify(ledger, never()).finishDownload(lease, true);
    }

    @Test
    void shouldAuthenticateTheFinalCiphertextChunk_beforeCompletingTheDownload() throws Exception {
        var path = root.resolve(lease.context().ciphertext().memberId() + ".stage");
        var bytes = Files.readAllBytes(path);
        bytes[bytes.length - 1] ^= 1;
        Files.write(path, bytes);
        try (var download = service.open(actor, lease.runId(), "package")) {
            assertThatThrownBy(() -> download.writeTo(OutputStream.nullOutputStream()))
                    .isInstanceOf(IOException.class);
        }
        verify(ledger).finishDownload(lease, false, false);
        verify(ledger, never()).finishDownload(lease, true);
    }
}
