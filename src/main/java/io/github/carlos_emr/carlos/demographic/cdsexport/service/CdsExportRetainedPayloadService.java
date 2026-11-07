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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Copies only the original field ranges referenced by a verified captured generation.
 * Unselected bytes sharing a source spool never enter export storage. Independently checked
 * values/index and identity/count/hash evidence bind every field, including inline/empty
 * fields. Failures erase all newly owned output. Consumers keep scan outputs private until
 * the complete call succeeds; source transport evidence is not clinical reconciliation.
 */
@Service
public class CdsExportRetainedPayloadService {
    private static final String TABLE = "retained_payload", COLUMN = "original";
    private static final Set<String> INPUTS = Set.of("cds_import_field", "cds_import_unit", "cds_import_patient", "cds_import_preparation", "cds_import_dependency");
    private static final int MAX_MANIFEST = 131_072;
    private static final JsonMapper JSON = JsonMapper.builder(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(16)
                    .maxStringLength(8192).maxNumberLength(20).build()).build())
            .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportStagingFactory stores;
    private final CdsExportConfigurationService configuration;
    private final CdsExportRetainedFieldMapper fields = new CdsExportRetainedFieldMapper();
    @Autowired public CdsExportRetainedPayloadService(CdsExportNativeCaptureService captures, CdsExportStagingFactory stores,
            CdsExportConfigurationService configuration) { this(new CdsExportCapturedTextRowsReader(captures), captures, stores, configuration); }
    CdsExportRetainedPayloadService(CdsExportCapturedTextRowsReader rows, CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores, CdsExportConfigurationService configuration) {
        this.rows = Objects.requireNonNull(rows); this.captures = Objects.requireNonNull(captures);
        this.stores = Objects.requireNonNull(stores); this.configuration = Objects.requireNonNull(configuration);
    }
    @FunctionalInterface private interface Fields { void accept(CdsExportRetainedFieldDto field) throws IOException; }
    private record Inspection(CdsExportNativeCaptureDto source, long fields, long payloads, long bytes, String sha256) { }

    public CdsExportRetainedPayloadReceiptDto freeze(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient,
            Set<String> requiredTables, CdsExportStagedFileDto sourceManifest, long maximumFields, long maximumBytes, CdsExportRunControl control) throws IOException {
        scope(requiredTables); patient.require(lease); captures.authorizeSources(lease, patient, requiredTables, control);
        var expected = inspect(lease, patient, requiredTables, sourceManifest, maximumFields, maximumBytes, control, field -> {});
        Path originalRoot = expected.payloads() == 0 ? null : configuration.retainedImportRoot();
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), control::checkpoint);
        var owned = new ArrayList<CdsExportStagedFileDto>(3); boolean complete = false; Throwable primary = null;
        try {
            var index = new CdsExportStagedFileDto[1]; var result = new CdsExportNativeCaptureComposer.Result[1];
            var values = store.stage("RETAINED_VALUES", valueOutput -> {
                index[0] = store.stage("RETAINED_INDEX", indexOutput -> {
                    result[0] = new CdsExportNativeCaptureComposer().compose(valueOutput, indexOutput, Set.of(TABLE), maximumFields, maximumBytes,
                            control::checkpoint, writer -> {
                        writer.beginTable(TABLE, Set.of(COLUMN), expected.fields());
                        var actual = inspect(lease, patient, requiredTables, sourceManifest, maximumFields, maximumBytes, control, field -> {
                            writer.row(field.id(), value -> {
                                if (field.originalPayload() == null) value.text(COLUMN, (String) null);
                                else try (var input = new CdsExportRetainedPayloadInputStream(originalRoot, field.originalPayload(), control::checkpoint)) {
                                    value.binary(COLUMN, input);
                                }
                            });
                        });
                        if (!expected.equals(actual)) throw new IOException("CDS_EXPORT_RETAINED_GENERATION_CHANGED");
                        writer.endTable();
                    });
                }); owned.add(index[0]);
            }); owned.add(values);
            var capture = new CdsExportRetainedPayloadCaptureDto(1, lease.runId(), lease.epoch(), patient.patient(), patient.cohortSha256(),
                    sourceManifest.plaintextSha256(), expected.sha256(), expected.fields(), expected.payloads(), expected.bytes(), values, index[0]);
            if (result[0].fields() != expected.fields() || result[0].valueBytes() != expected.bytes()) invalid();
            verify(store, capture, maximumFields, maximumBytes, control, field -> OutputStream.nullOutputStream());
            byte[] metadata = JSON.writeValueAsBytes(capture); if (metadata.length > MAX_MANIFEST) invalid();
            var manifest = store.stage("RETAINED_MANIFEST", output -> output.write(metadata)); owned.add(manifest);
            captures.authorizeSources(lease, patient, requiredTables, control); patient.require(lease); control.checkpoint();
            var receipt = new CdsExportRetainedPayloadReceiptDto(capture, manifest); complete = true; return receipt;
        } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            if (!complete) for (var member : owned.reversed()) try { store.discardUnpublished(member); }
            catch (IOException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
        }
    }
    /** Restores owned encrypted copies; original import files are neither queried nor reopened here. */
    public CdsExportRetainedPayloadCaptureDto scan(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient,
            Set<String> requiredTables, CdsExportStagedFileDto sourceManifest, CdsExportStagedFileDto payloadManifest,
            long maximumFields, long maximumBytes, CdsExportRunControl control, CdsExportNativeCaptureReader.Fields output) throws IOException {
        scope(requiredTables); Objects.requireNonNull(output); patient.require(lease);
        captures.authorizeSources(lease, patient, requiredTables, control);
        if (!payloadManifest.purpose().equals("RETAINED_MANIFEST")) invalid();
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), control::checkpoint);
        CdsExportRetainedPayloadCaptureDto capture;
        try (var input = store.open(payloadManifest)) {
            byte[] bytes = input.readNBytes(MAX_MANIFEST + 1); if (bytes.length > MAX_MANIFEST || bytes.length == 0 || bytes[0] != '{') invalid();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            capture = JSON.readValue(text, CdsExportRetainedPayloadCaptureDto.class);
        }
        if (!capture.runId().equals(lease.runId()) || capture.epoch() != lease.epoch() || capture.patient() != patient.patient()
                || !capture.cohortSha256().equals(patient.cohortSha256()) || !capture.sourceManifestSha256().equals(sourceManifest.plaintextSha256())) invalid();
        var expected = inspect(lease, patient, requiredTables, sourceManifest, maximumFields, maximumBytes, control, field -> {});
        if (expected.fields() != capture.fields() || expected.payloads() != capture.payloads() || expected.bytes() != capture.payloadBytes()
                || !expected.sha256().equals(capture.fieldsSha256())) invalid();
        verify(store, capture, maximumFields, maximumBytes, control, output);
        captures.authorizeSources(lease, patient, requiredTables, control); patient.require(lease); control.checkpoint(); return capture;
    }
    private Inspection inspect(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportStagedFileDto sourceManifest, long maximumFields, long maximumBytes, CdsExportRunControl control, Fields output) throws IOException {
        long[] counts = new long[3]; MessageDigest digest = CdsImportKeys.digest();
        var source = rows.forEach(lease, patient, requiredTables, "cds_import_field", sourceManifest, maximumFields, maximumBytes, 1_048_576, control, row -> {
            var field = fields.map(patient.patient(), lease.document().command().categories(), row); counts[0] = Math.addExact(counts[0], 1);
            var payload = field.originalPayload();
            if (payload != null) { counts[1] = Math.addExact(counts[1], 1); counts[2] = Math.addExact(counts[2], payload.bytes()); }
            if (counts[0] > maximumFields || counts[2] > maximumBytes) throw new IOException("CDS_EXPORT_RETAINED_RESOURCE_LIMIT");
            add(digest, field.id(), payload == null ? CdsExportCapturedFieldDto.Kind.SQL_NULL : CdsExportCapturedFieldDto.Kind.BINARY,
                    payload == null ? 0 : payload.bytes(), payload == null ? io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto.EMPTY_SHA256 : payload.sha256());
            output.accept(field);
        });
        if (counts[0] != source.tables().get("cds_import_field").rows()) invalid();
        return new Inspection(source, counts[0], counts[1], counts[2], HexFormat.of().formatHex(digest.digest()));
    }
    private static void verify(CdsExportStagingService store, CdsExportRetainedPayloadCaptureDto capture, long maximumFields,
            long maximumBytes, CdsExportRunControl control, CdsExportNativeCaptureReader.Fields output) throws IOException {
        long[] counts = new long[3]; MessageDigest digest = CdsImportKeys.digest();
        try (var values = store.open(capture.values()); var index = store.open(capture.index())) {
            new CdsExportNativeCaptureReader().scan(values, index,
                    Map.of(TABLE, new CdsExportNativeCaptureReader.Table(Set.of(COLUMN), capture.fields())), maximumFields, maximumBytes, control::checkpoint,
                    new CdsExportNativeCaptureReader.Fields() {
                        @Override public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
                            if (field.kind() == CdsExportCapturedFieldDto.Kind.UTF8) invalid();
                            counts[0] = Math.addExact(counts[0], 1);
                            if (field.kind() == CdsExportCapturedFieldDto.Kind.BINARY) { counts[1]++; counts[2] = Math.addExact(counts[2], field.bytes()); }
                            add(digest, field.recordKey(), field.kind(), field.bytes(), field.sha256()); return output.output(field);
                        }
                        @Override public void verified(CdsExportCapturedFieldDto field) throws IOException { output.verified(field); }
                    });
        }
        if (counts[0] != capture.fields() || counts[1] != capture.payloads() || counts[2] != capture.payloadBytes()
                || !HexFormat.of().formatHex(digest.digest()).equals(capture.fieldsSha256())) invalid();
    }
    private static void add(MessageDigest digest, String id, CdsExportCapturedFieldDto.Kind kind, long bytes, String sha256) {
        CdsImportKeys.add(digest, id); CdsImportKeys.add(digest, kind.name()); CdsImportKeys.add(digest, Long.toString(bytes)); CdsImportKeys.add(digest, sha256);
    }
    private static void scope(Set<String> tables) { if (!tables.containsAll(INPUTS)) throw new IllegalArgumentException("CDS_EXPORT_RETAINED_INPUTS_REQUIRED"); }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_CAPTURE_INVALID"); }
}
