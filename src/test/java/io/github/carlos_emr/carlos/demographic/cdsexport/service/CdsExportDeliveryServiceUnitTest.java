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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.jcajce.JceKeyTransAuthEnvelopedRecipient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;

@Tag("unit")
@Tag("cds-export")
class CdsExportDeliveryServiceUnitTest {
    @TempDir Path root;
    private static CdsExportRecipientFixture recipient;
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private final CdsExportLedgerService ledger = mock(CdsExportLedgerService.class);
    private final CdsExportDisclosureScopeService scopes =
            mock(CdsExportDisclosureScopeService.class);
    private final CdsExportDisclosurePolicyService disclosure =
            mock(CdsExportDisclosurePolicyService.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportClinicalPackageService.Prepared prepared =
            mock(CdsExportClinicalPackageService.Prepared.class);
    private final CdsExportDisclosureScopeService.Verified verified =
            mock(CdsExportDisclosureScopeService.Verified.class);
    private CdsExportStagingService store;
    private CdsExportLeaseDto lease;
    private CdsExportDeliveryService service;
    private CdsExportDisclosurePolicyService.Resolved policy;
    private CdsExportPackageDto archive;

    @BeforeAll
    static void recipient() throws Exception {
        recipient = new CdsExportRecipientFixture(Instant.now());
    }

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        String run = UUID.randomUUID().toString();
        var command =
                new CdsExportCommand(
                        "delivery-proof",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        "fixture",
                        "fixture");
        var storage = new CdsExportStorageBindingDto("primary", "primary", "a".repeat(64));
        lease =
                new CdsExportLeaseDto(
                        run,
                        1,
                        UUID.randomUUID().toString(),
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                "a".repeat(64),
                                "sender",
                                recipient.fingerprint,
                                "b".repeat(64),
                                storage));
        store =
                new CdsExportStagingService(
                        root,
                        run,
                        1,
                        new SecretKeySpec(new byte[32], "AES"),
                        8L << 20,
                        0,
                        () -> {});
        when(stores.openExisting(eq(run), eq(1L), eq(storage), any())).thenReturn(store);
        var member =
                store.stage(
                        "PATIENT_XML",
                        output ->
                                output.write(
                                        "SYNTHETIC PRIVATE PATIENT BYTES"
                                                .getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)));
        var members =
                new CdsExportPackageInventoryService()
                        .seal(
                                store,
                                10,
                                1L << 20,
                                () -> {},
                                output ->
                                        output.accept(
                                                new CdsExportPackageMemberDto(
                                                        "Patient/file.xml", member)));
        archive = new CdsExportPackageComposer().compose(store, members, 1L << 20, 10, () -> {});
        var authorization = store.stage("DISCLOSURE_MANIFEST", output -> output.write(1));
        when(prepared.runId()).thenReturn(run);
        when(prepared.epoch()).thenReturn(1L);
        when(prepared.patients()).thenReturn(1L);
        when(prepared.cohortSha256()).thenReturn("c".repeat(64));
        when(prepared.authorization()).thenReturn(authorization);
        when(prepared.archive()).thenReturn(archive);
        when(prepared.metadata())
                .thenReturn(
                        new CdsExportPackageMetadataDto(
                                "OwnedExport",
                                "Owned group",
                                "Owned contact",
                                "2026.11.0-test",
                                Instant.now(),
                                ZoneId.of("America/Toronto"),
                                command.profile(),
                                command.categories(),
                                "Owned audit access"));
        when(verified.runId()).thenReturn(run);
        when(verified.epoch()).thenReturn(1L);
        when(verified.patients()).thenReturn(1L);
        when(verified.cohortSha256()).thenReturn("c".repeat(64));
        when(verified.authorizationSha256()).thenReturn(authorization.plaintextSha256());
        when(scopes.verify(lease, prepared, control)).thenReturn(verified);
        policy =
                new CdsExportDisclosurePolicyService.Resolved(
                        "sender",
                        "Owned group",
                        "Owned contact",
                        recipient.fingerprint,
                        recipient.certificate,
                        "b".repeat(64),
                        "Owned audit access");
        when(disclosure.resolveAccepted(any(), any())).thenReturn(policy);
        service = new CdsExportDeliveryService(stores, ledger, scopes, disclosure);
    }

    private long files() throws Exception {
        try (var files = Files.list(root)) {
            return files.count();
        }
    }

    @Test
    void shouldEncryptTheExactValidatedArchive_andIndependentlyDecryptToTheSameBytes()
            throws Exception {
        long before = files();
        var result = service.encrypt(lease, prepared, 8L << 20, control);
        byte[] ciphertext;
        try (var input = store.open(result.ciphertext())) {
            ciphertext = input.readAllBytes();
        }
        byte[] expected;
        try (var input = store.open(archive.archive())) {
            expected = input.readAllBytes();
        }
        var cms = new CMSAuthEnvelopedData(ciphertext);
        var info = cms.getRecipientInfos().getRecipients().iterator().next();
        assertThat(
                        info.getContent(
                                new JceKeyTransAuthEnvelopedRecipient(recipient.keys.getPrivate())
                                        .setProvider(new BouncyCastleProvider())))
                .isEqualTo(expected);
        assertThat(result.proof().ciphertextSha256())
                .isEqualTo(result.ciphertext().plaintextSha256());
        assertThat(files()).isEqualTo(before + 1);
        verify(scopes, times(2)).verify(lease, prepared, control);
        verify(disclosure, times(2)).resolveAccepted(eq(lease.document()), any());
        verify(ledger).phase(lease, CdsExportState.ENCRYPTING);
        assertThat(result.toString()).isEqualTo("CdsExportEncryptedDelivery[protected]");
    }

    @Test
    void shouldDeleteCompletedCiphertext_afterFinalPermissionRevocation() throws Exception {
        long before = files();
        when(scopes.verify(lease, prepared, control))
                .thenReturn(verified)
                .thenThrow(new SecurityException("REVOKED"));
        assertThatThrownBy(() -> service.encrypt(lease, prepared, 8L << 20, control))
                .hasMessage("REVOKED");
        assertThat(files()).isEqualTo(before);
        try (var input = store.open(archive.archive())) {
            assertThat(input.readAllBytes()).isNotEmpty();
        }
    }

    @Test
    void shouldDeleteCiphertext_whenPolicyChangesDuringEncryption() throws Exception {
        long before = files();
        when(disclosure.resolveAccepted(any(), any()))
                .thenReturn(policy)
                .thenThrow(
                        new CdsExportReviewException(
                                CdsExportReviewException.Reason.SOURCE_CHANGED));
        assertThatThrownBy(() -> service.encrypt(lease, prepared, 8L << 20, control))
                .hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        assertThat(files()).isEqualTo(before);
    }

    @Test
    void shouldRefuseAnUnrelatedScopeReceipt_beforeStartingEncryption() throws Exception {
        when(verified.authorizationSha256()).thenReturn("f".repeat(64));
        long before = files();
        assertThatThrownBy(() -> service.encrypt(lease, prepared, 8L << 20, control))
                .hasMessage("CDS_EXPORT_DELIVERY_BINDING_INVALID");
        assertThat(files()).isEqualTo(before);
        verifyNoInteractions(ledger);
    }

    @Test
    void shouldRefuseDifferentAuditTerms_insteadOfEncryptingUnderAnUnacceptedArrangement()
            throws Exception {
        var m = prepared.metadata();
        when(prepared.metadata())
                .thenReturn(
                        new CdsExportPackageMetadataDto(
                                m.exportRoot(),
                                m.physicianGroup(),
                                m.groupContactInformation(),
                                m.applicationVersion(),
                                m.exportTime(),
                                m.clinicalZone(),
                                m.profile(),
                                m.categories(),
                                "Different audit terms"));
        assertThatThrownBy(() -> service.encrypt(lease, prepared, 8L << 20, control))
                .hasMessage("CDS_EXPORT_DELIVERY_BINDING_INVALID");
        verifyNoInteractions(scopes);
    }

    @Test
    void shouldRefuseTheCiphertextBudget_withoutRetainingAPartialArtifact() throws Exception {
        long before = files();
        assertThatThrownBy(() -> service.encrypt(lease, prepared, 10, control))
                .hasMessage("CDS_EXPORT_DELIVERY_BYTE_LIMIT");
        assertThat(files()).isEqualTo(before);
    }

    @Test
    void shouldRejectChangedArchiveEvidence_andLeaveNoCiphertextBehind() throws Exception {
        var original = archive.archive();
        var changed =
                new CdsExportStagedFileDto(
                        original.memberId(),
                        original.purpose(),
                        "f".repeat(64),
                        original.plaintextBytes());
        when(prepared.archive())
                .thenReturn(
                        new CdsExportPackageDto(
                                changed,
                                archive.entries(),
                                archive.expandedBytes(),
                                archive.membersSha256()));
        long before = files();
        assertThatThrownBy(() -> service.encrypt(lease, prepared, 8L << 20, control))
                .isInstanceOf(java.io.IOException.class);
        assertThat(files()).isEqualTo(before);
    }
}
