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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportDemographicClosureValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Scoped demographic occurrence assembly from one authenticated patient capture. The XML
 * emitter consumes retained values synchronously within their verified scope. Missing native
 * or retained closure refuses output. Multiple applied demographic parents,
 * active merges and unresolved associated fields require their explicit future reconciliation;
 * native charts use the same typed components with exact independent field closure. This service does not qualify a profile.
 */
@Service
public class CdsExportDemographicOccurrenceService {
    private static final Set<String> TABLES = Set.of("demographic", "demographicExt", "demographiccust", "demographicArchive", "provider",
            "Contact", "DemographicContact", "pharmacyInfo", "demographicPharmacy");
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportDemographicOccurrenceService(CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification, CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures) {
        this(values, verification, witnesses, captures, new CdsExportCapturedTextRowsReader(captures));
    }

    CdsExportDemographicOccurrenceService(CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification, CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures, CdsExportCapturedTextRowsReader rows) {
        this.values = Objects.requireNonNull(values); this.verification = Objects.requireNonNull(verification);
        this.witnesses = Objects.requireNonNull(witnesses); this.captures = Objects.requireNonNull(captures);
        this.rows = Objects.requireNonNull(rows);
    }

    /** Output remains private until complete source authentication, closure, serialization and final authority checks succeed. */
    public CdsExportClinicalOccurrenceService.Result append(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient,
            String patientKey, Set<String> requiredTables, CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest, CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control, CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output) throws IOException {
        Objects.requireNonNull(xml);
        var result = new CdsExportClinicalOccurrenceService.Result[1];
        xml.source(emitter -> result[0] = assemble(lease, patient, patientKey, requiredTables, source,
                payloadManifest, bounds, control, emitter, output));
        return Objects.requireNonNull(result[0]);
    }

    private CdsExportClinicalOccurrenceService.Result assemble(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient,
            String patientKey, Set<String> requiredTables, CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest, CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control, CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output) throws IOException {
        Objects.requireNonNull(xml); Objects.requireNonNull(output); patient.require(lease);
        var tables = Set.copyOf(requiredTables);
        if (patientKey == null || !patientKey.matches("[0-9a-f]{64}") || !tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(TABLES) || !lease.document().command().categories().contains(CdsCategory.DEMOGRAPHICS)) scope();
        if (!source.capture().runId().equals(lease.runId()) || source.capture().epoch() != lease.epoch()
                || source.capture().patient() != patient.patient()) scope();
        new CdsExportPatientIdentityReconciler().identity(lease.document().senderNamespace(), patient.patient());
        for (String table : TABLES) if (source.capture().tables().get(table).rows() > bounds.nativeRows()) limit();
        new CdsExportMergeScopeValidator(rows).requireUnmerged(lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var collected = new LinkedHashMap<String, List<CdsExportCapturedTextRowDto>>();
        long[] totalBytes = {0};
        for (String table : new TreeSet<>(TABLES)) {
            var items = new ArrayList<CdsExportCapturedTextRowDto>();
            var actual = rows.forEach(lease, patient, tables, table, source.manifest(), bounds.fields(), bounds.bytes(), 1 << 20, control, row -> {
                if (items.size() >= bounds.nativeRows()) limit();
                totalBytes[0] = Math.addExact(totalBytes[0], 2048L);
                for (String value : row.fields().values()) if (value != null) totalBytes[0] = Math.addExact(totalBytes[0], 32L + value.getBytes(StandardCharsets.UTF_8).length);
                if (totalBytes[0] > bounds.indexBytes()) limit(); items.add(row);
            });
            if (!actual.equals(source.capture()) || items.size() != source.capture().tables().get(table).rows()) scope();
            collected.put(table, List.copyOf(items));
        }
        var receipts = new LinkedHashMap<String, List<CdsExportNativeRowReceiptDto>>(); TABLES.forEach(table -> receipts.put(table, new ArrayList<>()));
        var scanned = witnesses.scan(lease, patient, tables, source, bounds.fields(), bounds.bytes(), control, witness -> {
            var selected = receipts.get(witness.table()); if (selected == null) return;
            if (selected.size() >= bounds.nativeRows()) limit(); selected.add(witness);
        });
        if (!scanned.sourceManifestSha256().equals(source.manifest().plaintextSha256()) || collected.get("demographic").size() != 1 || receipts.get("demographic").size() != 1
                || collected.get("demographiccust").size() > 1 || collected.get("demographiccust").size() != receipts.get("demographiccust").size()) scope();
        for (String table : TABLES) if (!table.equals("provider")
                && receipts.get(table).size() != collected.get(table).size()) scope();
        var completed = new CdsExportClinicalOccurrenceService.Result[1];
        xml.source(emitter -> values.withValues(lease, patient, tables, source.manifest(), payloadManifest,
                bounds.fields(), bounds.bytes(), bounds.indexRows(), bounds.indexBytes(), control, (index, retained, streams) -> {
            if (!index.sourceManifestSha256().equals(source.manifest().plaintextSha256())) scope();
            var checked = verification.verify(lease, patient, tables, source, index, bounds.fields(), bounds.bytes(), 1_000_000, control);
            var parents = index.tables().get("cds_import_unit").values().stream().filter(row -> "Demographics".equals(row.fields().get("category")))
                    .map(row -> row.fields().get("run_id") + ":" + row.fields().get("unit_key")).filter(index.appliedUnits()::contains).toList();
            if (parents.isEmpty()) {
                if (retained.stream().anyMatch(field -> field.category() == CdsCategory.DEMOGRAPHICS)) scope();
                streams.requireActive(); requireNativeWitnesses(collected, receipts, lease.document().command().categories(), control::checkpoint);
                var nativeResult = new CdsExportNativeDemographicAssembler().assemble(patient.patient(), lease.document().command().categories(),
                        index.sourceManifestSha256(), collected, bounds.nativeRows(), Math.min(bounds.indexBytes(), 64L << 20), control::checkpoint);
                var identity = new CdsExportIdentityDto(patientKey, CdsCategory.DEMOGRAPHICS,
                        CdsImportKeys.hash("carlos-native-occurrence-v1", "demographic", Integer.toString(patient.patient())));
                String senderId = new CdsExportPatientIdentityReconciler().identity(lease.document().senderNamespace(), patient.patient()).senderPatientId();
                var nativeFields = new LinkedHashMap<>(nativeResult.fields());
                nativeFields.put(new CdsExportNativeFieldKeyDto("demographic", collected.get("demographic").getFirst().recordKey(), "demographic_no"),
                        new CdsExportFieldAssessmentDto(CdsExportFieldAssessmentDto.Presence.VALUE, CdsExportFieldAssessmentDto.Outcome.MAPPED,
                                "UniqueVendorIdSequence", "EXACT_REGISTERED_NATIVE_PATIENT_IDENTITY"));
                emitter.emit(new CdsExportXmlComposer.Section(identity, writer -> new CdsExportDemographicXmlWriter().writeWithRelated(
                        nativeResult.demographic(), nativeResult.pharmacy(), nativeResult.contacts(), senderId, writer)));
                output.accept(new CdsExportClinicalOccurrenceService.Receipt(identity, index.sourceManifestSha256(), "demographic",
                        Integer.toString(patient.patient()), null, nativeFields, Set.of(), Set.of()));
                captures.authorizeSources(lease, patient, tables, control); streams.requireActive(); patient.require(lease); control.checkpoint();
                completed[0] = new CdsExportClinicalOccurrenceService.Result(index.sourceManifestSha256(), CdsCategory.DEMOGRAPHICS, 1, 0, 1, 0);
                return;
            }
            if (parents.size() != 1) throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PARENT_RECONCILIATION_REQUIRED");
            var providers = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
            for (var provider : collected.get("provider")) if (providers.putIfAbsent(provider.fields().get("provider_no"), provider) != null) scope();
            var nativeRows = new CdsExportDemographicAssembler.NativeRows(collected.get("demographic").getFirst(), receipts.get("demographic").getFirst(),
                    collected.get("demographicExt"), receipts.get("demographicExt"), collected.get("demographiccust").isEmpty() ? null : collected.get("demographiccust").getFirst(),
                    receipts.get("demographiccust").isEmpty() ? null : receipts.get("demographiccust").getFirst(), collected.get("demographicArchive"), receipts.get("demographicArchive"),
                    new CdsExportPublicProviderLookupsDto(index.sourceManifestSha256(), providers),
                    new CdsExportRetainedProfessionalContactReconciler.NativeRows(index.sourceManifestSha256(), collected.get("DemographicContact"), collected.get("Contact"), concat(receipts.get("DemographicContact"), receipts.get("Contact"))),
                    new CdsExportRetainedPharmacyReconciler.NativeRows(index.sourceManifestSha256(), collected.get("demographicPharmacy"), collected.get("pharmacyInfo"), concat(receipts.get("demographicPharmacy"), receipts.get("pharmacyInfo"))));
            var assembled = new CdsExportDemographicAssembler().assemble(patient.patient(), lease.document().command().categories(), parents.getFirst(), index, checked, retained,
                    nativeRows, bounds.nativeRows(), Math.min(bounds.indexBytes(), 64L << 20), streams, control::checkpoint);
            var result = new CdsExportPatientIdentityReconciler().reconcile(assembled.demographic(), patient.patient(), lease.document().senderNamespace(), index, checked, retained, streams, control::checkpoint);
            var parent = new CdsExportRetainedOccurrenceFields().fieldsFor(patient.patient(), CdsCategory.DEMOGRAPHICS, parents.getFirst(), index, checked, retained, control::checkpoint);
            var expectedSource = new HashSet<String>(); parent.forEach(field -> expectedSource.add(field.id()));
            var allDemographicSource = new HashSet<String>(); retained.stream().filter(field -> field.category() == CdsCategory.DEMOGRAPHICS).forEach(field -> allDemographicSource.add(field.id()));
            if (!expectedSource.equals(allDemographicSource)) scope();
            var expectedRows = new ArrayList<CdsExportCapturedTextRowDto>(); collected.values().forEach(expectedRows::addAll);
            new CdsExportDemographicClosureValidator().validate(index.sourceManifestSha256(), expectedRows, expectedSource, result, 1_000_000, control::checkpoint);
            var plan = new CdsExportRetainedSectionWriter().prepareChanges(parent, streams, result.coreChanges(), control::checkpoint);
            var accounted = new HashSet<>(plan.retainedFields()); if (!Collections.disjoint(accounted, plan.replacedFields())) scope(); accounted.addAll(plan.replacedFields());
            if (!accounted.equals(expectedSource)) scope();
            var identity = new CdsExportIdentityDto(patientKey, CdsCategory.DEMOGRAPHICS, CdsImportKeys.hash("carlos-native-occurrence-v1", "demographic", Integer.toString(patient.patient())));
            emitter.emit(new CdsExportXmlComposer.Section(identity, plan.content()));
            output.accept(new CdsExportClinicalOccurrenceService.Receipt(identity, index.sourceManifestSha256(), "demographic", Integer.toString(patient.patient()), parents.getFirst(), result.fields(), plan.retainedFields(), plan.replacedFields()));
            captures.authorizeSources(lease, patient, tables, control); streams.requireActive(); patient.require(lease); control.checkpoint();
            completed[0] = new CdsExportClinicalOccurrenceService.Result(index.sourceManifestSha256(), CdsCategory.DEMOGRAPHICS, 1, 1, 0, expectedSource.size());
        }));
        return Objects.requireNonNull(completed[0]);
    }

    /** Exact selected projections and independently captured witnesses; no native occurrence can borrow a retained receipt. */
    static void requireNativeWitnesses(Map<String, List<CdsExportCapturedTextRowDto>> rows,
            Map<String, List<CdsExportNativeRowReceiptDto>> witnesses, Set<CdsCategory> selected, Runnable checkpoint) throws IOException {
        var primary = Map.of("demographic", "demographic_no", "demographicExt", "id", "demographiccust", "demographic_no",
                "demographicArchive", "id", "provider", "provider_no", "Contact", "id", "DemographicContact", "id", "pharmacyInfo", "recordID", "demographicPharmacy", "id");
        if (!rows.keySet().equals(TABLES) || !witnesses.keySet().equals(TABLES)) scope();
        for (String table : TABLES) {
            // Public provider candidates are a six-column authenticated lookup, never a whole private account-row receipt.
            // Their exact selected fields and owner references still undergo typed mapping and independent field closure.
            if (table.equals("provider")) { if (!witnesses.get(table).isEmpty()) scope(); continue; }
            var byKey = new HashMap<String, CdsExportNativeRowReceiptDto>();
            for (var witness : witnesses.get(table)) if (!table.equals(witness.table()) || byKey.putIfAbsent(witness.recordKey(), witness) != null) scope();
            for (var row : rows.get(table)) {
                checkpoint.run(); var witness = byKey.remove(row.recordKey()); if (witness == null || !table.equals(row.table())) scope();
                boolean partial = table.equals("demographiccust") && !selected.contains(CdsCategory.ALERTS_AND_SPECIAL_NEEDS);
                CdsExportCapturedTextWitness.require(row, witness, primary.get(table), partial, checkpoint);
            }
            if (!byKey.isEmpty()) scope();
        }
    }
    private static <T> List<T> concat(List<T> first, List<T> second) { var result = new ArrayList<>(first); result.addAll(second); return List.copyOf(result); }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_DEMOGRAPHIC_OCCURRENCE_SCOPE_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_DEMOGRAPHIC_OCCURRENCE_RESOURCE_LIMIT"); }
}
