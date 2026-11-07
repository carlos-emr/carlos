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
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;

/** Real source-range/filesystem/hash/GCM/index restore; only the already verified metadata-row transport is mocked. */
@Tag("unit") @Tag("cds-export")
class CdsExportRetainedPayloadServiceUnitTest {
    private static final Set<String> TABLES = Set.of("cds_import_field", "cds_import_unit", "cds_import_patient", "cds_import_preparation", "cds_import_dependency");
    private final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    private final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    private final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private final CdsExportStagedFileDto sourceManifest = new CdsExportStagedFileDto("e".repeat(64), "SOURCE_MANIFEST", "f".repeat(64), 1);
    private CdsExportLeaseDto lease;
    private CdsExportStagingService store;
    private CdsExportRetainedPayloadService service;
    private Path root, originals, output, spool;
    private byte[] selected;
    private List<CdsExportCapturedTextRowDto> metadata;
    private CdsExportNativeCaptureDto generation;
    private int readCount;
    private java.util.function.IntConsumer beforeRead = ignored -> {};
    private org.mockito.MockedStatic<io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles> ancestors;

    @BeforeEach void prepare() throws Exception {
        String installedRoot = System.getProperty("cds.export.privateSourceRoot");
        root = Files.createTempDirectory(installedRoot == null ? Path.of("target").toAbsolutePath() : Path.of(installedRoot),
                "retained-payload-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        originals = Files.createDirectory(root.resolve("originals"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (installedRoot == null) {
            // The cloud executor maps its filesystem root to nobody. Unit tests substitute
            // only that ancestor trust decision; the installed tier supplies a real owned
            // root and executes the same cases without this substitution. File modes,
            // containment, symlinks, exact ranges, hashes and GCM remain real in both tiers.
            ancestors = mockStatic(io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.class, CALLS_REAL_METHODS);
            ancestors.when(() -> io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.dataDirectory(originals)).thenReturn(originals);
        }
        output = Files.createDirectory(root.resolve("owned"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        selected = "Selected café 😀\r\n".repeat(10000).getBytes(StandardCharsets.UTF_8);
        byte[] prefix = "PRIVATE OTHER PATIENT".getBytes(StandardCharsets.UTF_8), suffix = "UNSELECTED CATEGORY".getBytes(StandardCharsets.UTF_8);
        spool = Files.createFile(originals.resolve("values"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try (var out = Files.newOutputStream(spool)) { out.write(prefix); out.write(selected); out.write(suffix); }
        var payload = new CdsExportRetainedFieldDto.Payload(spool.toString(), HexFormat.of().formatHex(io.github.carlos_emr.carlos.utility.CdsImportKeys.digest().digest(selected)), prefix.length, selected.length);
        metadata = new ArrayList<>(List.of(CdsExportRetainedFieldMapperUnitTest.row(1, null, "accepted correction", payload),
                CdsExportRetainedFieldMapperUnitTest.row(2, "inline original", "", null)));
        metadata.sort(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey));
        var command = new CdsExportCommand("freeze", "999998", null, CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(), List.of(27), Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS), "receiver", "audit");
        lease = new CdsExportLeaseDto(UUID.randomUUID().toString(), 1, "a".repeat(64), new CdsExportJobDocumentDto(2,
                new CdsExportActorDto("agent", "999998", null, null), command, "b".repeat(64), "sender", "c".repeat(64), "d".repeat(64),
                new CdsExportStorageBindingDto("primary", "primary", "a".repeat(64))));
        when(patient.patient()).thenReturn(27); when(patient.cohortSha256()).thenReturn("a".repeat(64));
        store = new CdsExportStagingService(output, lease.runId(), 1, new SecretKeySpec(new byte[32], "AES"), 16L << 20, 0, () -> {});
        when(stores.openExisting(eq(lease.runId()), eq(1L), eq(lease.document().storage()), any())).thenReturn(store);
        generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables()).thenAnswer(ignored -> Map.of("cds_import_field", new CdsExportCapturedTableDto(
                Set.copyOf(io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS.columns()), metadata.size(), "a".repeat(64))));
        when(rows.forEach(eq(lease), eq(patient), eq(TABLES), eq("cds_import_field"), eq(sourceManifest), anyLong(), anyLong(), anyInt(), eq(control), any()))
                .thenAnswer(call -> {
                    beforeRead.accept(++readCount); CdsExportCapturedTextRowsReader.Rows consumer = call.getArgument(9);
                    for (var row : metadata) consumer.accept(row); return generation;
                });
        var properties = new Properties(); properties.setProperty("cds.import.stagingRoot", originals.toString());
        service = new CdsExportRetainedPayloadService(rows, captures, stores, new CdsExportConfigurationService(properties));
    }
    @AfterEach void clean() throws Exception {
        if (ancestors != null) ancestors.close();
        if (root != null) try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
    private CdsExportRetainedPayloadReceiptDto freeze() throws Exception { return service.freeze(lease, patient, TABLES, sourceManifest, 1000, 4L << 20, control); }
    private byte[] restore(CdsExportStagedFileDto manifest) throws Exception {
        var out = new ByteArrayOutputStream();
        service.scan(lease, patient, TABLES, sourceManifest, manifest, 1000, 4L << 20, control, field -> out);
        return out.toByteArray();
    }
    @Test void shouldCopyOnlyTheSelectedRange_andRestoreItAfterTheOriginalWasDeleted() throws Exception {
        var frozen = freeze(); assertThat(frozen.capture().fields()).isEqualTo(2); assertThat(frozen.capture().payloads()).isEqualTo(1);
        assertThat(frozen.capture().payloadBytes()).isEqualTo(selected.length); Files.delete(spool);
        assertThat(restore(frozen.manifest())).isEqualTo(selected);
        try (var files = Files.list(output)) { assertThat(files).hasSize(3); }
        assertThat(new String(restore(frozen.manifest()), StandardCharsets.UTF_8)).doesNotContain("PRIVATE OTHER PATIENT", "UNSELECTED CATEGORY", "inline original");
        verify(captures, times(6)).authorizeSources(lease, patient, TABLES, control);
    }
    @Test void shouldAllowNativeOnlyOrInlineOnlyEvidence_withoutRequiringOriginalImportStorage() throws Exception {
        service = new CdsExportRetainedPayloadService(rows, captures, stores, new CdsExportConfigurationService(new Properties()));
        for (var fields : List.of(List.<CdsExportCapturedTextRowDto>of(), List.of(CdsExportRetainedFieldMapperUnitTest.row(1, "", "", null)))) {
            metadata = fields; var frozen = freeze(); assertThat(frozen.capture().fields()).isEqualTo(fields.size());
            assertThat(frozen.capture().payloads()).isZero(); assertThat(restore(frozen.manifest())).isEmpty();
        }
    }
    @Test void shouldEraseEveryNewMember_whenTheOriginalRangeHashIsWrong() throws Exception {
        try (var file = java.nio.channels.FileChannel.open(spool, StandardOpenOption.WRITE)) { file.position(22); file.write(java.nio.ByteBuffer.wrap(new byte[]{0})); }
        assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_RETAINED_PAYLOAD_HASH_MISMATCH"); assertEmpty();
    }
    @Test void shouldRefuseSymlinkOrOutOfRootPayloads_withoutCopyingAnyBytes() throws Exception {
        var outside = Files.writeString(root.resolve("outside"), "outside", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(outside, PosixFilePermissions.fromString("rw-------"));
        var link = Files.createSymbolicLink(originals.resolve("linked"), outside);
        for (Path path : List.of(outside, link)) {
            var payload = new CdsExportRetainedFieldDto.Payload(path.toString(), "c".repeat(64), 0, 1);
            metadata = List.of(CdsExportRetainedFieldMapperUnitTest.row(1, null, null, payload));
            assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_RETAINED_PAYLOAD_UNAVAILABLE"); assertEmpty();
        }
    }
    @Test void shouldEraseClosedOutput_whenFinalAuthorityIsRevoked() {
        doNothing().doThrow(new SecurityException("ACCESS_REVOKED")).when(captures).authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::freeze).hasMessage("ACCESS_REVOKED"); assertEmpty();
    }
    @Test void shouldKeepCancellationDistinct_fromAStorageFailureAndErasePartialOutput() {
        beforeRead = n -> { if (n == 2) doThrow(new IllegalStateException("CDS_EXPORT_CANCELLED")).when(control).checkpoint(); };
        assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_CANCELLED"); assertEmpty();
    }
    @Test void shouldRefuseResourceExhaustionAndMissingAuxiliaryInputs_beforeProducingAReceipt() {
        assertThatThrownBy(() -> service.freeze(lease, patient, TABLES, sourceManifest, 1000, 10, control)).hasMessage("CDS_EXPORT_RETAINED_RESOURCE_LIMIT");
        assertThatThrownBy(() -> service.freeze(lease, patient, Set.of("cds_import_field"), sourceManifest, 1000, 4L << 20, control))
                .hasMessage("CDS_EXPORT_RETAINED_INPUTS_REQUIRED"); assertEmpty();
    }
    @Test void shouldRefuseAChangedMetadataGeneration_insteadOfPublishingMixedSourceRanges() {
        beforeRead = n -> { if (n == 2) metadata = List.of(metadata.getFirst()); };
        assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_RETAINED_GENERATION_CHANGED"); assertEmpty();
    }
    @Test void shouldRefuseMutationOfFieldIdentity_evenWhenItsBytesAndCountsRemainTheSame() throws Exception {
        var frozen = freeze();
        var payload = new CdsExportRetainedFieldMapper().map(27, lease.document().command().categories(), metadata.stream()
                .filter(row -> row.fields().get("payload_location") != null).findFirst().orElseThrow()).originalPayload();
        metadata = new ArrayList<>(List.of(CdsExportRetainedFieldMapperUnitTest.row(3, null, "accepted correction", payload),
                CdsExportRetainedFieldMapperUnitTest.row(2, "inline original", "", null)));
        metadata.sort(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey));
        assertThatThrownBy(() -> restore(frozen.manifest())).hasMessage("CDS_EXPORT_RETAINED_PAYLOAD_CAPTURE_INVALID");
    }
    @Test void shouldRefuseACorruptedEncryptedIndexAndAMalformedManifest() throws Exception {
        var frozen = freeze();
        Path file = output.resolve(frozen.capture().index().memberId() + ".gcm");
        if (!Files.exists(file)) { try (var files = Files.list(output)) { file = files.filter(path -> path.getFileName().toString().contains(frozen.capture().index().memberId())).findFirst().orElseThrow(); } }
        byte[] bytes = Files.readAllBytes(file); bytes[bytes.length / 2] ^= 1; Files.write(file, bytes);
        assertThatThrownBy(() -> restore(frozen.manifest())).isInstanceOf(java.io.IOException.class);
        var malformed = store.stage("RETAINED_MANIFEST", out -> out.write("{\"protocol\":1,\"protocol\":1}".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> restore(malformed)).isInstanceOf(java.io.IOException.class);
    }
    @Test void shouldReadEffectiveValuesFromOwnedCapture_afterDeletingTheOriginal_andExpireTheValueCapability() throws Exception {
        var frozen = freeze(); Files.delete(spool); var values = valuesService();
        var retained = new CdsExportRetainedSectionWriter.Values[1]; var first = new CdsExportRetainedFieldDto[1];
        var actual = new ArrayList<String>();
        values.withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control, (index, fields, access) -> {
            retained[0] = access; first[0] = fields.getFirst();
            for (var field : fields) try (var input = access.text(field)) {
                var text = new java.io.StringWriter(); input.transferTo(text); actual.add(text.toString());
            }
        });
        assertThat(actual).containsExactlyInAnyOrder("accepted correction", "");
        assertThatThrownBy(() -> retained[0].text(first[0])).hasMessage("CDS_EXPORT_RETAINED_VALUES_SCOPE_INVALID");
    }
    @Test void shouldStreamAndAuthenticateALargeUncorrectedOriginal_throughTheOwnedFrozenRange() throws Exception {
        removeCorrections(); var frozen = freeze(); Files.delete(spool);
        var actual = new ArrayList<String>();
        valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control, (index, fields, access) -> {
            for (var field : fields) try (var input = access.text(field)) {
                var text = new java.io.StringWriter(); input.transferTo(text); actual.add(text.toString());
            }
        });
        assertThat(actual).containsExactlyInAnyOrder(new String(selected, StandardCharsets.UTF_8), "");
    }
    @Test void shouldRefuseCorruptionAfterInitialVerification_whenOnlyAPrefixOfTheOwnedTextIsRead() throws Exception {
        removeCorrections(); var frozen = freeze();
        assertThatThrownBy(() -> valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> {
                    Path valueFile = output.resolve(frozen.capture().values().memberId() + ".stage");
                    byte[] bytes = Files.readAllBytes(valueFile); bytes[bytes.length - 1] ^= 1; Files.write(valueFile, bytes);
                    var field = fields.stream().filter(value -> value.originalPayload() != null).findFirst().orElseThrow();
                    try (var input = access.text(field)) { assertThat(input.read()).isEqualTo('S'); }
                })).isInstanceOf(java.io.IOException.class);
    }
    @Test void shouldValidateAllUtf8OnReaderClose_andRejectSwallowedDecoderFailure() throws Exception {
        selected = new byte[] { 'S', (byte) 0xc0, (byte) 0xaf };
        Files.write(spool, selected);
        var payload = new CdsExportRetainedFieldDto.Payload(spool.toString(), HexFormat.of().formatHex(
                io.github.carlos_emr.carlos.utility.CdsImportKeys.digest().digest(selected)), 0, selected.length);
        metadata = List.of(CdsExportRetainedFieldMapperUnitTest.row(1, null, null, payload)); var frozen = freeze();
        assertThatThrownBy(() -> valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> {
                    try (var input = access.text(fields.getFirst())) { input.read(); }
                    catch (java.io.IOException expected) { /* A decoder failure must poison the whole scoped result. */ }
                })).hasMessage("CDS_EXPORT_RETAINED_VALUES_INCOMPLETE");
    }
    @Test void shouldRefuseAbandonedOrSubstitutedValues_evenWhenTheCallerCatchesTheFailure() throws Exception {
        var frozen = freeze();
        assertThatThrownBy(() -> valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> access.text(fields.getFirst())))
                .hasMessage("CDS_EXPORT_RETAINED_VALUES_INCOMPLETE");
        assertThatThrownBy(() -> valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> {
                    var field = fields.getFirst(); var substituted = new CdsExportRetainedFieldDto(field.id(), field.runId(), field.sourcePatientKey(), field.fieldUnitKey(),
                            field.category(), field.sourcePath(), field.occurrence(), field.namespace(), field.valueType(), field.precision(), field.originalInline(), "substituted", field.originalPayload());
                    try { access.text(substituted); } catch (java.io.IOException expected) { /* A caught capability violation must still poison completion. */ }
                })).hasMessage("CDS_EXPORT_RETAINED_VALUES_INCOMPLETE");
    }
    @Test void shouldRefuseFinalAuthorityLoss_afterReadingOnlyInlineValues() throws Exception {
        var frozen = freeze(); var values = valuesService();
        assertThatThrownBy(() -> values.withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> {
                    for (var field : fields) try (var input = access.text(field)) { input.transferTo(java.io.Writer.nullWriter()); }
                    doThrow(new SecurityException("ACCESS_REVOKED")).when(captures).authorizeSources(lease, patient, TABLES, control);
                })).hasMessage("ACCESS_REVOKED");
    }
    @Test void shouldExpireTheAuthenticatedScopeEvenWithoutOpeningAValue() throws Exception {
        var frozen = freeze(); var escaped = new java.util.concurrent.atomic.AtomicReference<CdsExportRetainedSectionWriter.Values>();
        valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> { access.requireActive(); escaped.set(access); });
        assertThatThrownBy(() -> escaped.get().requireActive()).hasMessage("CDS_EXPORT_RETAINED_VALUES_SCOPE_INVALID");
    }
    @Test void shouldPoisonCompletionIfAnExplicitScopeCheckLosesAuthority_evenWhenTheCallerCatchesTheFailure() throws Exception {
        var frozen = freeze();
        assertThatThrownBy(() -> valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> {
                    doThrow(new SecurityException("ACCESS_REVOKED")).when(captures).authorizeSources(lease, patient, TABLES, control);
                    try { access.requireActive(); } catch (SecurityException expected) { }
                })).hasMessage("CDS_EXPORT_RETAINED_VALUES_INCOMPLETE");
    }
    @Test void shouldRecheckAuthorityAfterFinalWholeMemberAuthentication() throws Exception {
        var frozen = freeze();
        var afterCallback = new java.util.concurrent.atomic.AtomicBoolean();
        var finalScan = new java.util.concurrent.atomic.AtomicBoolean();
        var revoked = new java.util.concurrent.atomic.AtomicBoolean();
        var checked = new CdsExportStagingService(output, lease.runId(), 1, new SecretKeySpec(new byte[32], "AES"), 16L << 20, 0,
                () -> { if (finalScan.get()) revoked.set(true); });
        when(stores.openExisting(eq(lease.runId()), eq(1L), eq(lease.document().storage()), any())).thenReturn(checked);
        doAnswer(call -> {
            if (revoked.get()) throw new SecurityException("ACCESS_REVOKED_AFTER_FINAL_SCAN");
            if (afterCallback.get()) finalScan.set(true);
            return null;
        }).when(captures).authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(() -> valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> {
                    for (var field : fields) try (var input = access.text(field)) { input.transferTo(java.io.Writer.nullWriter()); }
                    afterCallback.set(true);
                })).hasMessage("ACCESS_REVOKED_AFTER_FINAL_SCAN");
        assertThat(revoked.get()).isTrue();
    }
    @Test void shouldAuthenticateUnusedOriginalPayloadEvenWhenEveryEffectiveValueIsInline() throws Exception {
        var frozen = freeze();
        assertThatThrownBy(() -> valuesService().withValues(lease, patient, TABLES, sourceManifest, frozen.manifest(), 1000, 4L << 20, 1000, 1L << 20, control,
                (index, fields, access) -> {
                    for (var field : fields) try (var input = access.text(field)) { input.transferTo(java.io.Writer.nullWriter()); }
                    Path valueFile = output.resolve(frozen.capture().values().memberId() + ".stage");
                    byte[] encrypted = Files.readAllBytes(valueFile); encrypted[encrypted.length - 1] ^= 1; Files.write(valueFile, encrypted);
                })).hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
    }
    private CdsExportRetainedValuesService valuesService() throws Exception {
        var indices = mock(CdsExportRetainedIndexService.class); var fields = new TreeMap<String, CdsExportCapturedTextRowDto>();
        metadata.forEach(row -> fields.put(row.recordKey(), row));
        var index = new CdsExportRetainedIndexDto(sourceManifest.plaintextSha256(), metadata.size(), 4096,
                Map.of("cds_import_field", fields), Set.of(), Map.of(), Map.of());
        when(indices.index(eq(lease), eq(patient), eq(TABLES), eq(sourceManifest), anyLong(), anyLong(), anyInt(), anyLong(), eq(control))).thenReturn(index);
        return new CdsExportRetainedValuesService(indices, service, captures, stores);
    }
    private void removeCorrections() {
        var revised = new ArrayList<CdsExportCapturedTextRowDto>();
        for (var row : metadata) {
            var fields = new TreeMap<>(row.fields()); if (fields.get("payload_location") != null) fields.put("normalized_value", null);
            revised.add(new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields));
        }
        metadata = revised;
    }
    private void assertEmpty() { try (var files = Files.list(output)) { assertThat(files).isEmpty(); } catch (java.io.IOException failure) { throw new AssertionError(failure); } }
}
