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
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportDisclosureScopeServiceUnitTest {
    @TempDir Path root;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String run = UUID.randomUUID().toString();
    private final Set<String> tables = Set.of("demographic", "provider", "casemgmt_note");
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private final CdsExportLedgerService ledger = mock(CdsExportLedgerService.class);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private CdsExportLeaseDto lease;
    private CdsExportStagingService store;
    private CdsExportCohortService.Sealed cohort;
    private CdsExportDisclosureScopeService service;
    private final List<Object> patients = new ArrayList<>();

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        var categories = EnumSet.copyOf(CdsExportNoteSourceReader.CATEGORIES);
        categories.add(CdsCategory.DEMOGRAPHICS);
        var command =
                new CdsExportCommand(
                        "scope-proof",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27, 28),
                        categories,
                        "receiver",
                        "audit");
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
                                "b".repeat(64),
                                "c".repeat(64),
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
        when(captures.requiredTables(command)).thenReturn(tables);
        var cohorts = new CdsExportCohortService(stores, ledger);
        var selection =
                store.stage(
                        "PATIENT_SELECTION",
                        output ->
                                output.write(
                                        ("{\"demographicNo\":27,\"physicianNo\":null}\n{\"demographicNo\":28,\"physicianNo\":null}\n")
                                                .getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)));
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        cohort = cohorts.prepare(lease, new CdsExportSelectionDto(now, now, 2, selection), control);
        patients.add(patient(27, List.of(new CdsExportNoteAccessDto(10, 27, "0", "0"))));
        patients.add(patient(28, List.of(new CdsExportNoteAccessDto(11, 28, "", "0"))));
        service = new CdsExportDisclosureScopeService(stores, cohorts, captures, ledger);
    }

    private Map<String, Object> patient(int patient, List<CdsExportNoteAccessDto> notes) {
        return Map.of(
                "kind",
                "PATIENT",
                "patient",
                patient,
                "sourceManifestSha256",
                "d".repeat(64),
                "notes",
                notes);
    }

    private CdsExportStagedFileDto manifest() throws Exception {
        return store.stage(
                "DISCLOSURE_MANIFEST",
                output -> {
                    output.write(
                            JSON.writeValueAsBytes(
                                    Map.of(
                                            "kind",
                                            "HEADER",
                                            "protocol",
                                            1,
                                            "runId",
                                            run,
                                            "epoch",
                                            1,
                                            "cohortSha256",
                                            cohort.member().plaintextSha256(),
                                            "compatibilityDigest",
                                            lease.document().compatibilityDigest(),
                                            "patients",
                                            2,
                                            "tables",
                                            new TreeSet<>(tables))));
                    output.write('\n');
                    for (var patient : patients) {
                        output.write(JSON.writeValueAsBytes(patient));
                        output.write('\n');
                    }
                    output.write(JSON.writeValueAsBytes(Map.of("kind", "END", "patients", 2)));
                    output.write('\n');
                });
    }

    private CdsExportDisclosureScopeService.Verified checkScope(CdsExportStagedFileDto file)
            throws Exception {
        return service.verifyRetained(run, 1, lease.document(), cohort.member(), file, 2, control);
    }

    @Test
    void shouldRecheckEveryRetainedPatientAndExactLegacyNoteScope_withoutAnExecutionLease()
            throws Exception {
        var result = checkScope(manifest());
        assertThat(result.patients()).isEqualTo(2);
        assertThat(result.notes()).isEqualTo(2);
        assertThat(result.cohortSha256()).isEqualTo(cohort.member().plaintextSha256());
        verify(captures).authorizeScope(lease.document().command(), 27, tables, control);
        verify(captures).authorizeScope(lease.document().command(), 28, tables, control);
        verify(control)
                .authorizeNote(
                        CdsCategory.CLINICAL_NOTES, new CdsExportNoteAccessDto(10, 27, "0", "0"));
        verify(control)
                .authorizeNote(
                        CdsCategory.CLINICAL_NOTES, new CdsExportNoteAccessDto(11, 28, "", "0"));
        verifyNoInteractions(ledger);
        verify(control, never()).captureCheckpoint();
        assertThat(result.toString()).isEqualTo("CdsExportVerifiedDisclosureScope[protected]");
    }

    @Test
    void shouldRejectAMissingPatient_insteadOfAuthorizingAPartialPackage() throws Exception {
        patients.removeLast();
        assertThatThrownBy(() -> checkScope(manifest()))
                .hasMessage("CDS_EXPORT_DISCLOSURE_SCOPE_INVALID");
    }

    @Test
    void shouldRejectDuplicateAndForeignPatients_againstTheIndependentlySealedSelection()
            throws Exception {
        patients.set(1, patients.getFirst());
        assertThatThrownBy(() -> checkScope(manifest()))
                .hasMessage("CDS_EXPORT_DISCLOSURE_SCOPE_INVALID");
        patients.set(1, patient(29, List.of()));
        assertThatThrownBy(() -> checkScope(manifest()))
                .hasMessage("CDS_EXPORT_DISCLOSURE_SCOPE_INVALID");
    }

    @Test
    void shouldRejectAnExtraPatient_beforeAnyForeignPatientAuthorization() throws Exception {
        patients.add(patient(29, List.of()));
        assertThatThrownBy(() -> checkScope(manifest()))
                .hasMessage("CDS_EXPORT_DISCLOSURE_SCOPE_INVALID");
        verify(captures, never()).authorizeScope(any(), eq(29), anySet(), any());
    }

    @Test
    void shouldRejectANoteFromAnotherPatient_insteadOfBorrowingItsProgramScope() throws Exception {
        patients.set(0, patient(27, List.of(new CdsExportNoteAccessDto(10, 28, "0", "0"))));
        assertThatThrownBy(() -> checkScope(manifest()))
                .hasMessage("CDS_EXPORT_DISCLOSURE_SCOPE_INVALID");
        verify(control, never()).authorizeNote(any(), any());
    }

    @Test
    void shouldRejectDuplicateOrUnorderedNoteScopeIdentities() throws Exception {
        patients.set(
                0,
                patient(
                        27,
                        List.of(
                                new CdsExportNoteAccessDto(10, 27, "0", "0"),
                                new CdsExportNoteAccessDto(10, 27, "0", "0"))));
        assertThatThrownBy(() -> checkScope(manifest()))
                .hasMessage("CDS_EXPORT_DISCLOSURE_SCOPE_INVALID");
    }

    @Test
    void shouldNotCacheAFormerAuthorization_afterPatientRightsAreRevoked() throws Exception {
        var manifest = manifest();
        checkScope(manifest);
        doThrow(new SecurityException("REVOKED"))
                .when(captures)
                .authorizeScope(any(), anyInt(), anySet(), any());
        assertThatThrownBy(() -> checkScope(manifest)).hasMessage("REVOKED");
    }

    @Test
    void shouldPropagateExactNoteRoleRevocation_insteadOfReturningAPermissionReceipt()
            throws Exception {
        doThrow(new SecurityException("NOTE_REVOKED")).when(control).authorizeNote(any(), any());
        assertThatThrownBy(() -> checkScope(manifest())).hasMessage("NOTE_REVOKED");
    }

    @Test
    void shouldAuthenticateTheCompleteScopeTerminator_beforeReturningAReceipt() throws Exception {
        var file = manifest();
        Path path = root.resolve(file.memberId() + ".stage");
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 1] ^= 1;
        Files.write(path, bytes);
        assertThatThrownBy(() -> checkScope(file)).isInstanceOf(java.io.IOException.class);
    }
}
