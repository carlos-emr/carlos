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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTableDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Captures an explicit installed native-table scope into three encrypted, independently read-back
 * members. All schema checks, counts and rows share one bounded patient snapshot; permissions are
 * freshly checked outside it before and after. Only metadata ownership probes use another
 * transaction. No renderer/network call/publication occurs here. This bounded encrypted-spooling
 * transaction is deliberate. Unknown engines/schema/columns refuse capture instead of losing data.
 * This is not a complete clinical patient capture until the complete clinical reader/inventory
 * catalogue, associated files and retained-source reconciliation have separately been qualified.
 */
@Service
public class CdsExportNativeCaptureService {
    private static final int MAX_MANIFEST_BYTES = 4_194_304;
    private static final ObjectMapper JSON = com.fasterxml.jackson.databind.json.JsonMapper.builder(
            com.fasterxml.jackson.core.JsonFactory.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(24)
                            .maxStringLength(MAX_MANIFEST_BYTES).maxNumberLength(20).build()).build())
            .addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final Map<String, CdsExportNativeSourceReader> readers;
    private final CdsExportStagingFactory stores;
    private final CdsExportRunDao clock;
    private final TransactionTemplate read;
    @Autowired public CdsExportNativeCaptureService(ObjectProvider<CdsExportNativeSourceReader> installed,
            CdsExportStagingFactory stores, CdsExportRunDao clock, PlatformTransactionManager transactions) {
        this(installed.orderedStream().toList(), stores, clock, transactions);
    }
    CdsExportNativeCaptureService(List<CdsExportNativeSourceReader> installed, CdsExportStagingFactory stores,
            CdsExportRunDao clock, PlatformTransactionManager transactions) {
        var registered = new TreeMap<String, CdsExportNativeSourceReader>();
        for (var reader : List.copyOf(installed)) {
            if (reader.table() == null || !reader.table().matches("[A-Za-z0-9_]{1,64}")
                    || registered.putIfAbsent(reader.table(), reader) != null || reader.categories().isEmpty()) {
                throw new IllegalStateException("CDS_EXPORT_SOURCE_READER_AMBIGUOUS");
            }
        }
        readers = Map.copyOf(registered); this.stores = Objects.requireNonNull(stores); this.clock = Objects.requireNonNull(clock);
        read = new TransactionTemplate(transactions); read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); read.setTimeout(120);
    }
    public CdsExportPatientCaptureDto capture(CdsExportLeaseDto lease, CdsExportCohortService.Patient patientScope, Set<String> selectedTables,
            long maximumFields, long maximumValueBytes, CdsExportRunControl control) throws IOException {
        Objects.requireNonNull(lease); Objects.requireNonNull(patientScope); patientScope.require(lease); int patient = patientScope.patient();
        Objects.requireNonNull(control); var tables = Set.copyOf(selectedTables);
        if (patient < 1 || tables.isEmpty() || tables.size() > 256 || !readers.keySet().containsAll(tables)
                || maximumFields < 1 || maximumFields > 100_000_000 || maximumValueBytes < 1) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        if (lease.document().protocol() != 2 || lease.document().storage() == null) throw new IOException("CDS_EXPORT_STORAGE_BINDING_REQUIRED");
        List<CdsExportNativeSourceReader> selected = selected(lease, tables);
        authorize(patient, selected, control); control.checkpoint();
        Runnable probe = () -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) control.captureCheckpoint();
            else control.checkpoint();
        };
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), probe);
        var owned = new ArrayList<CdsExportStagedFileDto>(3); boolean complete = false; Throwable primary = null;
        try {
            var captured = read.execute(status -> {
                control.captureCheckpoint(); var started = clock.now(); var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
                try {
                    for (var reader : selected) {
                        control.captureCheckpoint(); var columns = Set.copyOf(reader.columns());
                        String schema = new CdsExportSourceSchemaValidator().verify(reader.table(), reader.schema(), columns);
                        long count = reader.count(patient, probe);
                        if (count < 0 || count > maximumFields / columns.size()) throw new IOException("CDS_EXPORT_CAPTURE_FIELD_LIMIT");
                        if (reader.table().equals("demographic") && count != 1) throw new IOException("CDS_EXPORT_SELECTED_PATIENT_MISSING");
                        var receiptColumns = List.copyOf(reader.receiptColumns());
                        verifyReceiptColumns(reader.table(), columns, receiptColumns);
                        scopes.put(reader.table(), new CdsExportCapturedTableDto(columns, count, schema, receiptColumns));
                    }
                    var result = new CdsExportNativeCaptureComposer.Result[1]; var index = new CdsExportStagedFileDto[1];
                    var values = store.stage("SOURCE_VALUES", valueOutput -> {
                        index[0] = store.stage("SOURCE_INDEX", indexOutput -> {
                            result[0] = new CdsExportNativeCaptureComposer().compose(valueOutput, indexOutput, tables,
                                    maximumFields, maximumValueBytes, control::captureCheckpoint, writer -> {
                                        for (var reader : selected) {
                                            var scope = scopes.get(reader.table());
                                            writer.beginTable(reader.table(), scope.columns(), scope.rows());
                                            reader.capture(patient, writer, probe); writer.endTable();
                                        }
                                    });
                        }); owned.add(index[0]);
                    }); owned.add(values); control.captureCheckpoint();
                    return new CdsExportNativeCaptureDto(2, lease.runId(), lease.epoch(), patient, patientScope.cohortSha256(), started, clock.now(), scopes,
                            result[0].fields(), result[0].valueBytes(), values, index[0]);
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            });
            Objects.requireNonNull(captured); patientScope.require(lease); authorize(patient, selected, control); control.checkpoint();
            var plan = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
            captured.tables().forEach((table, scope) -> plan.put(table, new CdsExportNativeCaptureReader.Table(scope.columns(), scope.rows())));
            try (var values = store.open(captured.values()); var index = store.open(captured.index())) {
                var proof = new CdsExportNativeCaptureReader().verify(values, index, plan, maximumFields, maximumValueBytes, control::checkpoint);
                if (proof.fields() != captured.fieldCount() || proof.valueBytes() != captured.valueBytes()) throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
            }
            byte[] manifestBytes = JSON.writeValueAsBytes(captured);
            if (manifestBytes.length > MAX_MANIFEST_BYTES) throw new IOException("CDS_EXPORT_CAPTURE_MANIFEST_LIMIT");
            var manifest = store.stage("SOURCE_MANIFEST", output -> output.write(manifestBytes)); owned.add(manifest);
            authorize(patient, selected, control); control.checkpoint();
            patientScope.require(lease); var receipt = new CdsExportPatientCaptureDto(captured, manifest); complete = true; return receipt;
        } catch (UncheckedIOException failure) { primary = failure.getCause(); throw failure.getCause(); }
        catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            if (!complete) for (var member : owned.reversed()) try { store.discardUnpublished(member); }
            catch (IOException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
        }
    }
    /**
     * Restores a captured generation for mapping using its protected manifest and current cohort
     * patient capability. Clinical rows are never queried here. The caller supplies the independently
     * required table recipe; a manifest cannot shrink it. Mapper sinks stay private until this read,
     * field-level clinical reconciliation and finished-output validation all complete successfully.
     */
    public CdsExportNativeCaptureDto scan(CdsExportLeaseDto lease, CdsExportCohortService.Patient patientScope,
            Set<String> requiredTables, CdsExportStagedFileDto manifest, long maximumFields, long maximumValueBytes,
            CdsExportRunControl control, CdsExportNativeCaptureReader.Fields fields) throws IOException {
        Objects.requireNonNull(lease); Objects.requireNonNull(patientScope); Objects.requireNonNull(manifest);
        Objects.requireNonNull(control); Objects.requireNonNull(fields); patientScope.require(lease);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        var tables = Set.copyOf(requiredTables);
        if (tables.isEmpty() || tables.size() > 256 || !readers.keySet().containsAll(tables)
                || maximumFields < 1 || maximumFields > 100_000_000 || maximumValueBytes < 1) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        }
        if (!manifest.purpose().equals("SOURCE_MANIFEST") || manifest.plaintextBytes() < 1 || manifest.plaintextBytes() > MAX_MANIFEST_BYTES) {
            throw new IOException("CDS_EXPORT_CAPTURE_MANIFEST_INVALID");
        }
        var selected = selected(lease, tables);
        authorize(patientScope.patient(), selected, control); control.checkpoint();
        if (lease.document().protocol() != 2 || lease.document().storage() == null) throw new IOException("CDS_EXPORT_STORAGE_BINDING_REQUIRED");
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), control::checkpoint);
        CdsExportNativeCaptureDto captured;
        try (var input = store.open(manifest)) {
            byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (bytes.length > MAX_MANIFEST_BYTES || input.read() != -1) throw new IOException("CDS_EXPORT_CAPTURE_MANIFEST_LIMIT");
            captured = JSON.readValue(bytes, CdsExportNativeCaptureDto.class);
        }
        if (!captured.runId().equals(lease.runId()) || captured.epoch() != lease.epoch() || captured.patient() != patientScope.patient()
                || !captured.cohortSha256().equals(patientScope.cohortSha256()) || !captured.tables().keySet().equals(tables)
                || captured.fieldCount() > maximumFields || captured.valueBytes() > maximumValueBytes) {
            throw new IOException("CDS_EXPORT_CAPTURE_SCOPE_MISMATCH");
        }
        var plan = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
        for (var reader : selected) {
            var table = captured.tables().get(reader.table());
            if (!reader.columns().equals(table.columns()) || !new CdsExportSourceSchemaValidator().expectedSha256(reader.table(), reader.columns())
                    .equals(table.schemaSha256())) throw new IOException("CDS_EXPORT_CAPTURE_SCHEMA_MISMATCH");
            verifyReceiptColumns(reader.table(), table.columns(), table.receiptColumns());
            plan.put(reader.table(), new CdsExportNativeCaptureReader.Table(table.columns(), table.rows()));
        }
        try (var values = store.open(captured.values()); var index = store.open(captured.index())) {
            var proof = new CdsExportNativeCaptureReader().scan(values, index, plan, maximumFields, maximumValueBytes, control::checkpoint, fields);
            if (proof.fields() != captured.fieldCount() || proof.valueBytes() != captured.valueBytes()) {
                throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
            }
        }
        patientScope.require(lease); authorize(patientScope.patient(), selected, control); control.checkpoint(); return captured;
    }
    /** The existing receipt's SELECT * omits this specific reviewed virtual/invisible derived key. */
    private static void verifyReceiptColumns(String table, Set<String> captured,
            List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> recipe) throws IOException {
        if (recipe.isEmpty()) return; // Legacy/partial captures remain explicitly unverifiable.
        var expected = new java.util.HashSet<>(captured);
        if (table.equals("demographicExt")) expected.remove("dashboard_single_value_key");
        // This epoch is captured but explicitly absent from the importer's sealed file recipe.
        if (table.equals("cds_import_file")) expected.remove("verified_epoch");
        var actual = recipe.stream().map(io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column::name).toList();
        if (actual.size() != expected.size() || !Set.copyOf(actual).equals(expected)) {
            throw new IOException("CDS_EXPORT_CAPTURE_RECEIPT_RECIPE_INVALID");
        }
    }
    private List<CdsExportNativeSourceReader> selected(CdsExportLeaseDto lease, Set<String> tables) {
        return tables.stream().sorted().map(table -> {
            var reader = readers.get(table).forRequest(lease.document().command());
            if (reader == null || !table.equals(reader.table()) || reader.categories().isEmpty()
                    || !lease.document().command().categories().containsAll(reader.categories()) || !tables.containsAll(reader.requiredTables())) {
                throw new IllegalStateException("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
            }
            return reader;
        }).toList();
    }
    /** Fresh access check for processing captured source/files, without rereading mutable clinical values. */
    public void authorizeSources(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient,
            Set<String> requiredTables, CdsExportRunControl control) {
        Objects.requireNonNull(control); patient.require(lease); var tables = Set.copyOf(requiredTables);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        if (tables.isEmpty() || tables.size() > 256 || !readers.keySet().containsAll(tables)) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        }
        authorize(patient.patient(), selected(lease, tables), control); control.checkpoint();
    }
    private static void authorize(int patient, List<CdsExportNativeSourceReader> readers, CdsExportRunControl control) {
        control.authorizePatient(patient);
        var required = new java.util.EnumMap<io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory, Boolean>(
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.class);
        for (var reader : readers) for (var category : reader.categories()) required.merge(category,
                category == io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.LABORATORY_RESULTS
                        && reader.requiresSensitiveLaboratoryRead(), Boolean::logicalOr);
        required.forEach((category, sensitive) -> control.authorizeCategory(patient, category, sensitive));
        for (var reader : readers) reader.authorizeReadScope(patient, control);
    }
}
