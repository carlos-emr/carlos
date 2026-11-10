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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.CancellationException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;

/** Real protected config files, GCM storage and frozen classification; only the authenticated native row transport is mocked. */
@Tag("unit")
@Tag("cds-export")
class CdsExportPreventionConfigurationServiceUnitTest {
    private static final Set<String> TABLES = Set.of("CVCImmunization", "CVCMapping");
    private static final String XML =
            "<preventions><item name='PAP' layout='PAPMAM' headingName='Screenings'/><item name='H1N1' layout='h1n1' healthCanadaType='Inf'/></preventions>";
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportCapturedTextRowsReader rows =
            mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private CdsExportLeaseDto lease;
    private CdsExportPatientCaptureDto source;
    private CdsExportStagingService store;
    private CdsExportPreventionConfigurationService service;
    private Path root, output, configuration;
    private CdsExportNativeCaptureDto generation;
    private boolean readComplete, readFailure;

    @BeforeEach
    void prepare() throws Exception {
        String installed = System.getProperty("cds.export.privateSourceRoot");
        root =
                Files.createTempDirectory(
                        installed == null ? Path.of("target").toAbsolutePath() : Path.of(installed),
                        "prevention-config-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        output =
                Files.createDirectory(
                        root.resolve("owned"),
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        configuration =
                Files.writeString(root.resolve("prevention.xml"), XML, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(configuration, PosixFilePermissions.fromString("rw-------"));
        var command =
                new CdsExportCommand(
                        "prevention-config",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS),
                        "receiver",
                        "audit");
        lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        "a".repeat(64),
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                "b".repeat(64),
                                "sender",
                                "c".repeat(64),
                                "d".repeat(64),
                                new CdsExportStorageBindingDto(
                                        "primary", "primary", "a".repeat(64))));
        when(patient.patient()).thenReturn(27);
        when(patient.cohortSha256()).thenReturn("a".repeat(64));
        store =
                new CdsExportStagingService(
                        output,
                        lease.runId(),
                        1,
                        new SecretKeySpec(new byte[32], "AES"),
                        8L << 20,
                        0,
                        () -> {});
        when(stores.openExisting(eq(lease.runId()), eq(1L), eq(lease.document().storage()), any()))
                .thenReturn(store);
        generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables())
                .thenReturn(
                        Map.of(
                                "CVCImmunization",
                                new CdsExportCapturedTableDto(
                                        vaccine().fields().keySet(), 1, "a".repeat(64)),
                                "CVCMapping",
                                new CdsExportCapturedTableDto(
                                        mapping().fields().keySet(), 1, "a".repeat(64))));
        when(generation.values())
                .thenReturn(
                        new CdsExportStagedFileDto(
                                "e".repeat(64), "SOURCE_VALUES", "f".repeat(64), 1));
        when(generation.index())
                .thenReturn(
                        new CdsExportStagedFileDto(
                                "f".repeat(64), "SOURCE_INDEX", "a".repeat(64), 1));
        source =
                new CdsExportPatientCaptureDto(
                        generation,
                        new CdsExportStagedFileDto(
                                "a".repeat(64), "SOURCE_MANIFEST", "b".repeat(64), 1));
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(TABLES),
                        eq(TABLES),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows sink = call.getArgument(9);
                            sink.accept(vaccine());
                            sink.accept(mapping());
                            if (readFailure) throw new IOException("CDS_EXPORT_SOURCE_EOF_FAILED");
                            readComplete = true;
                            return generation;
                        });
        service =
                new CdsExportPreventionConfigurationService(
                        captures, stores, rows, configuration.toString());
    }

    @AfterEach
    void cleanup() throws Exception {
        if (root != null)
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
    }

    private CdsExportPreventionConfigurationDto freeze() throws Exception {
        return service.freeze(lease, patient, TABLES, source, 1000, 4L << 20, control);
    }

    private CdsExportPreventionClassifier restore(CdsExportPreventionConfigurationDto receipt)
            throws Exception {
        return service.restore(lease, patient, TABLES, source, receipt, 1000, 4L << 20, control);
    }

    @Test
    void shouldVerifyExactFrozenConfigurationAndCatalogueSurviveOriginalFileDeletion_forPreventionConfigurationServiceContract() throws Exception {
        var receipt = freeze();
        Files.delete(configuration);
        var value = restore(receipt);
        assertThat(value.configurationSha256()).isEqualTo(receipt.configurationSha256());
        assertThat(value.sourceManifestSha256()).isEqualTo(source.manifest().plaintextSha256());
        assertThat(value.classify("H1N1", null).kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.IMMUNIZATION);
        assertThat(value.classify("PAP", null).kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.NON_IMMUNIZATION);
        assertThat(value.classify("HistoricName", null).kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.IMMUNIZATION);
        assertThat(
                        new String(
                                Files.readAllBytes(
                                        output.resolve(receipt.member().memberId() + ".stage")),
                                StandardCharsets.ISO_8859_1))
                .doesNotContain(XML, "HistoricName");
        verify(captures, times(5)).authorizeSources(lease, patient, TABLES, control);
    }

    @Test
    void shouldVerifyRestoreNeverReinterpretsAChangedCurrentConfiguration_forPreventionConfigurationServiceContract() throws Exception {
        var receipt = freeze();
        Files.writeString(
                configuration,
                "<preventions><item name='PAP' layout='injection' healthCanadaType='Td'/><item name='H1N1' layout='PAPMAM'/></preventions>");
        assertThat(restore(receipt).classify("PAP", null).kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.NON_IMMUNIZATION);
        assertThat(restore(receipt).classify("H1N1", null).kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.IMMUNIZATION);
    }

    @Test
    void shouldVerifyMissingOrUnselectedVocabularyScopeCannotProduceAConfigReceipt_forPreventionConfigurationServiceContract() {
        assertThatThrownBy(
                        () ->
                                service.freeze(
                                        lease,
                                        patient,
                                        Set.of("CVCMapping"),
                                        source,
                                        1000,
                                        4L << 20,
                                        control))
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
        assertEmpty();
        verifyNoInteractions(rows);
    }

    @Test
    void shouldVerifyAdmissionRevocationPrecedesAnyConfigurationOrCapturedValueRead_forPreventionConfigurationServiceContract() {
        doThrow(new SecurityException("ACCESS_REVOKED"))
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::freeze).hasMessage("ACCESS_REVOKED");
        assertEmpty();
        verifyNoInteractions(rows);
    }

    @Test
    void shouldVerifyAuthorityRevoked_afterAuthenticatedNativeEofErasesNewOutput() {
        doAnswer(
                        call -> {
                            if (readComplete)
                                throw new SecurityException("ACCESS_REVOKED_AFTER_EOF");
                            return null;
                        })
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::freeze).hasMessage("ACCESS_REVOKED_AFTER_EOF");
        assertEmpty();
    }

    @Test
    void shouldVerifyLateSourceFailureNeverReturnsAClassifierOrLeavesNewOutput_forPreventionConfigurationServiceContract() {
        readFailure = true;
        assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_SOURCE_EOF_FAILED");
        assertEmpty();
    }

    @Test
    void shouldVerifyChangedCapturedGenerationErasesNewConfig_insteadOfMixingInputs() throws Exception {
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(TABLES),
                        eq(TABLES),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenReturn(mock(CdsExportNativeCaptureDto.class));
        assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
        assertEmpty();
    }

    @Test
    void shouldVerifyInvalidXmlIsRejected_afterReadbackAndNewMemberIsErased() throws Exception {
        Files.writeString(
                configuration,
                "<!DOCTYPE preventions [<!ENTITY x SYSTEM 'file:///must-not-read'>]><preventions><item name='&x;' layout='injection'/></preventions>");
        assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_PREVENTION_CLASSIFICATION_INVALID");
        assertEmpty();
    }

    @Test
    void shouldVerifyOversizedAndSymlinkConfigurationAreRejected_beforeCreatingOwnedOutput() throws Exception {
        Files.write(configuration, new byte[1_048_577]);
        assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_PREVENTION_CONFIG_UNAVAILABLE");
        assertEmpty();
        Files.delete(configuration);
        Files.createSymbolicLink(configuration, Files.writeString(root.resolve("other.xml"), XML));
        assertThatThrownBy(this::freeze).hasMessage("CDS_EXPORT_PREVENTION_CONFIG_UNAVAILABLE");
        assertEmpty();
    }

    @Test
    void shouldVerifyForgedConfigurationHashCannotReturnACapturedClassifier_forPreventionConfigurationServiceContract() throws Exception {
        var good = freeze();
        var forged =
                new CdsExportPreventionConfigurationDto(
                        good.sourceManifestSha256(),
                        "c".repeat(64),
                        good.xmlBytes(),
                        good.member());
        clearInvocations(rows);
        assertThatThrownBy(() -> restore(forged))
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
        verifyNoInteractions(rows);
    }

    @Test
    void shouldVerifyAForeignSourceBindingIsRefused_beforeReadingCapturedRows() throws Exception {
        var good = freeze();
        var forged =
                new CdsExportPreventionConfigurationDto(
                        "c".repeat(64), good.configurationSha256(), good.xmlBytes(), good.member());
        clearInvocations(rows);
        assertThatThrownBy(() -> restore(forged))
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
        verifyNoInteractions(rows);
    }

    @Test
    void shouldVerifyTamperedCiphertextIsAuthenticatedEven_withValidReceiptHashes() throws Exception {
        var receipt = freeze();
        var file = output.resolve(receipt.member().memberId() + ".stage");
        byte[] encrypted = Files.readAllBytes(file);
        encrypted[encrypted.length - 1] ^= 1;
        Files.write(file, encrypted);
        assertThatThrownBy(() -> restore(receipt))
                .hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
    }

    @Test
    void shouldVerifyInvalidPurposeOrLengthCannotBeLabeledAConfigurationReceipt_forPreventionConfigurationServiceContract() throws Exception {
        var receipt = freeze();
        assertThatThrownBy(
                        () ->
                                new CdsExportPreventionConfigurationDto(
                                        receipt.sourceManifestSha256(),
                                        receipt.configurationSha256(),
                                        receipt.xmlBytes() + 1,
                                        receipt.member()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new CdsExportPreventionConfigurationDto(
                                        receipt.sourceManifestSha256(),
                                        receipt.configurationSha256(),
                                        receipt.xmlBytes(),
                                        new CdsExportStagedFileDto(
                                                receipt.member().memberId(),
                                                "SOURCE_VALUES",
                                                receipt.member().plaintextSha256(),
                                                receipt.member().plaintextBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldVerifyCancellationIsPreservedAndDoesNotCreateAConfigurationMember_forPreventionConfigurationServiceContract() {
        doThrow(new CancellationException("CANCELLED")).when(control).checkpoint();
        assertThatThrownBy(this::freeze)
                .isInstanceOf(CancellationException.class)
                .hasMessage("CANCELLED");
        assertEmpty();
    }

    private Set<String> embedded(byte[] xml, String declared) throws Exception {
        var expanded = new java.util.HashSet<>(TABLES);
        expanded.add("prevention_configuration");
        var tables = Set.copyOf(expanded);
        var plans = new TreeMap<>(generation.tables());
        plans.put(
                "prevention_configuration",
                new CdsExportCapturedTableDto(Set.of("sha256", "xml"), 1, "a".repeat(64)));
        when(generation.tables()).thenReturn(Map.copyOf(plans));
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(tables),
                        eq(TABLES),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows sink = call.getArgument(9);
                            sink.accept(vaccine());
                            sink.accept(mapping());
                            readComplete = true;
                            return generation;
                        });
        when(captures.scan(
                        eq(lease),
                        eq(patient),
                        eq(tables),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportNativeCaptureReader.Fields sink = call.getArgument(7);
                            byte[] text = declared.getBytes(StandardCharsets.US_ASCII);
                            var sha =
                                    new CdsExportCapturedFieldDto(
                                            "prevention_configuration",
                                            "configuration",
                                            "sha256",
                                            CdsExportCapturedFieldDto.Kind.UTF8,
                                            0,
                                            text.length,
                                            hash(text));
                            var body =
                                    new CdsExportCapturedFieldDto(
                                            "prevention_configuration",
                                            "configuration",
                                            "xml",
                                            CdsExportCapturedFieldDto.Kind.BINARY,
                                            text.length,
                                            xml.length,
                                            hash(xml));
                            sink.output(sha).write(text);
                            sink.verified(sha);
                            sink.output(body).write(xml);
                            sink.verified(body);
                            return generation;
                        });
        return tables;
    }

    @Test
    void shouldVerifyEmbeddedConfigurationSurvivesDeletionOfTheCurrentFileAndNeverFallsBack_toIt()
            throws Exception {
        byte[] xml = XML.getBytes(StandardCharsets.UTF_8);
        var tables = embedded(xml, hash(xml));
        Files.delete(configuration);
        var receipt = service.freeze(lease, patient, tables, source, 1000, 4L << 20, control);
        assertThat(receipt.configurationSha256()).isEqualTo(hash(xml));
        assertThat(
                        service.restore(
                                        lease, patient, tables, source, receipt, 1000, 4L << 20,
                                        control)
                                .classify("H1N1", null)
                                .kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.IMMUNIZATION);
    }

    @Test
    void shouldVerifyExactEmbeddedEncodingIsPreservedRatherThanTranscodedOrLoaded_fromChangedSettings()
            throws Exception {
        byte[] xml = XML.getBytes(StandardCharsets.UTF_16);
        var tables = embedded(xml, hash(xml));
        Files.writeString(
                configuration, "<preventions><item name='H1N1' layout='PAPMAM'/></preventions>");
        var receipt = service.freeze(lease, patient, tables, source, 1000, 4L << 20, control);
        assertThat(receipt.xmlBytes()).isEqualTo(xml.length);
        assertThat(receipt.configurationSha256()).isEqualTo(hash(xml));
        assertThat(
                        service.restore(
                                        lease, patient, tables, source, receipt, 1000, 4L << 20,
                                        control)
                                .classify("H1N1", null)
                                .kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.IMMUNIZATION);
    }

    @Test
    void shouldVerifyInconsistentEmbeddedDigestCannotBeReplaced_withAValidCurrentFile() throws Exception {
        var tables = embedded(XML.getBytes(StandardCharsets.UTF_8), "a".repeat(64));
        assertThatThrownBy(
                        () ->
                                service.freeze(
                                        lease, patient, tables, source, 1000, 4L << 20, control))
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
        assertEmpty();
    }

    @Test
    void shouldVerifyLateEmbeddedSourceFailureCannotProduceAConfigurationMember_forPreventionConfigurationServiceContract() throws Exception {
        byte[] xml = XML.getBytes(StandardCharsets.UTF_8);
        var tables = embedded(xml, hash(xml));
        when(captures.scan(
                        eq(lease),
                        eq(patient),
                        eq(tables),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        eq(control),
                        any()))
                .thenThrow(new IOException("LATE_EMBEDDED_SOURCE_EOF"));
        assertThatThrownBy(
                        () ->
                                service.freeze(
                                        lease, patient, tables, source, 1000, 4L << 20, control))
                .hasMessage("LATE_EMBEDDED_SOURCE_EOF");
        assertEmpty();
    }

    @Test
    void shouldVerifyAnEmbeddedScope_withoutItsFieldsCannotUseLiveConfigurationAsAFallback() throws Exception {
        byte[] xml = XML.getBytes(StandardCharsets.UTF_8);
        var tables = embedded(xml, hash(xml));
        when(captures.scan(
                        eq(lease),
                        eq(patient),
                        eq(tables),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        eq(control),
                        any()))
                .thenReturn(generation);
        assertThatThrownBy(
                        () ->
                                service.freeze(
                                        lease, patient, tables, source, 1000, 4L << 20, control))
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
        assertEmpty();
    }

    @Test
    void shouldVerifyEmbeddedReadUsesExactSnapshotEncoding_withoutOpeningAStoreOrCurrentFile() throws Exception {
        byte[] xml = XML.getBytes(StandardCharsets.UTF_16);
        var tables = embedded(xml, hash(xml));
        Files.delete(configuration);
        clearInvocations(stores);
        var classifier =
                service.readEmbedded(lease, patient, tables, source, 1000, 4L << 20, control);
        assertThat(classifier.configurationSha256()).isEqualTo(hash(xml));
        assertThat(classifier.classify("H1N1", null).kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.IMMUNIZATION);
        verifyNoInteractions(stores);
        assertEmpty();
    }

    @Test
    void shouldVerifyExplicitEmbeddedReadRejectsLegacyScope_insteadOfReadingCurrentSettings() {
        assertThatThrownBy(
                        () ->
                                service.readEmbedded(
                                        lease, patient, TABLES, source, 1000, 4L << 20, control))
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
        verifyNoInteractions(rows, stores);
        assertEmpty();
    }

    @Test
    void shouldVerifyEmbeddedReadCannotReturn_afterLateCatalogueFailure() throws Exception {
        byte[] xml = XML.getBytes(StandardCharsets.UTF_8);
        var tables = embedded(xml, hash(xml));
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(tables),
                        eq(TABLES),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenThrow(new IOException("LATE_CATALOGUE_EOF"));
        assertThatThrownBy(
                        () ->
                                service.readEmbedded(
                                        lease, patient, tables, source, 1000, 4L << 20, control))
                .hasMessage("LATE_CATALOGUE_EOF");
        verifyNoInteractions(stores);
        assertEmpty();
    }

    @Test
    void shouldVerifyEmbeddedReadRequiresFreshAuthority_afterTheLastCatalogueCallback() throws Exception {
        byte[] xml = XML.getBytes(StandardCharsets.UTF_8);
        var tables = embedded(xml, hash(xml));
        doAnswer(
                        call -> {
                            if (readComplete)
                                throw new SecurityException("EMBEDDED_AUTHORITY_REVOKED");
                            return null;
                        })
                .when(captures)
                .authorizeSources(lease, patient, tables, control);
        assertThatThrownBy(
                        () ->
                                service.readEmbedded(
                                        lease, patient, tables, source, 1000, 4L << 20, control))
                .hasMessage("EMBEDDED_AUTHORITY_REVOKED");
        verifyNoInteractions(stores);
        assertEmpty();
    }

    @Test
    void shouldVerifyEmbeddedReadRequiresTheExactCapturedGeneration_forPreventionConfigurationServiceContract() throws Exception {
        byte[] xml = XML.getBytes(StandardCharsets.UTF_8);
        var tables = embedded(xml, hash(xml));
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(tables),
                        eq(TABLES),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenReturn(mock(CdsExportNativeCaptureDto.class));
        assertThatThrownBy(
                        () ->
                                service.readEmbedded(
                                        lease, patient, tables, source, 1000, 4L << 20, control))
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
        verifyNoInteractions(stores);
        assertEmpty();
    }

    private static String hash(byte[] value) {
        return java.util.HexFormat.of()
                .formatHex(
                        io.github.carlos_emr.carlos.utility.CdsImportKeys.digest().digest(value));
    }

    private static CdsExportCapturedTextRowDto vaccine() {
        var fields = new TreeMap<String, String>();
        fields.put("id", "1");
        fields.put("displayName", "Frozen vaccine");
        fields.put("picklistName", "VaccineName");
        fields.put("snomedConceptId", "123456789");
        fields.put("generic", "1");
        fields.put("ispa", "0");
        fields.put("prevalence", null);
        fields.put("parentConceptId", null);
        fields.put("versionId", "42");
        return new CdsExportCapturedTextRowDto(
                "CVCImmunization", CdsExportPatientTableSourceReader.key(1), fields);
    }

    private static CdsExportCapturedTextRowDto mapping() {
        return new CdsExportCapturedTextRowDto(
                "CVCMapping",
                CdsExportPatientTableSourceReader.key(1),
                Map.of(
                        "id",
                        "1",
                        "oscarName",
                        "HistoricName",
                        "cvcSnomedId",
                        "123456789",
                        "preferCVC",
                        "0"));
    }

    private void assertEmpty() {
        try (var files = Files.list(output)) {
            assertThat(files).isEmpty();
        } catch (IOException failed) {
            throw new AssertionError(failed);
        }
    }
}
