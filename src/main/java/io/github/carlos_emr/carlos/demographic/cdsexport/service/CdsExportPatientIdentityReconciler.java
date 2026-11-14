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
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Replaces a previous sender's vendor ID with this registered sender's exact native patient
 * identity. IDs are injective within one stable namespace and fit the wire's 20-character
 * limit without hash truncation. Namespace changes, restore/copy and merge ownership remain
 * separate admission checks; this component cannot authorize disclosure or resolve aliases.
 */
public final class CdsExportPatientIdentityReconciler {
    private static final String PATH = "/OmdCds[1]/PatientRecord[1]/Demographics[1]/UniqueVendorIdSequence[1]";

    public record Identity(String senderNamespace, String senderPatientId) {
        public Identity {
            if (senderNamespace == null || !senderNamespace.matches("[A-Za-z0-9_][A-Za-z0-9_.:-]{0,127}")
                    || senderPatientId == null || !senderPatientId.matches("C[1-9][0-9]{0,9}")) {
                throw new IllegalArgumentException("CDS_EXPORT_PATIENT_IDENTITY_INVALID");
            }
            try {
                if (Integer.parseInt(senderPatientId.substring(1)) < 1) throw new NumberFormatException();
            } catch (NumberFormatException invalid) { throw new IllegalArgumentException("CDS_EXPORT_PATIENT_IDENTITY_INVALID"); }
        }
        @Override public String toString() { return "CdsExportPatientIdentityReconciler.Identity[protected]"; }
    }

    /** The server-owned namespace is part of accepted intent and must remain stable for this database lineage. */
    public Identity identity(String registeredNamespace, int patient) {
        if (registeredNamespace == null || !registeredNamespace.matches("[A-Za-z0-9_][A-Za-z0-9_.:-]{0,127}") || patient < 1) {
            throw new IllegalArgumentException("CDS_EXPORT_PATIENT_IDENTITY_INVALID");
        }
        return new Identity(registeredNamespace, "C" + Integer.toString(patient));
    }

    /** Requires the independently verified retained parent before deciding its previous vendor ID field. */
    public CdsExportDemographicRetainedCoreReconciler.Result reconcile(
            CdsExportDemographicRetainedCoreReconciler.Result prior, int patient, String registeredNamespace,
            CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields, CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint) throws IOException {
        Objects.requireNonNull(prior); Objects.requireNonNull(values); Objects.requireNonNull(checkpoint);
        values.requireActive(); checkpoint.run();
        if (!prior.sourceManifestSha256().equals(index.sourceManifestSha256())) scope();
        var parent = new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS,
                prior.unitAddress(), index, checked, allFields, checkpoint);
        var original = parent.stream().filter(field -> field.sourcePath().equals(PATH)).toList();
        if (original.size() != 1) scope();
        String run = prior.unitAddress().substring(0, 36), unitKey = prior.unitAddress().substring(37);
        var unit = index.tables().getOrDefault("cds_import_unit", Map.of()).get(CdsImportKeys.hash(run, unitKey));
        if (unit == null || !run.equals(unit.fields().get("run_id")) || !unitKey.equals(unit.fields().get("unit_key"))
                || !"Demographics".equals(unit.fields().get("category")) || !"1".equals(unit.fields().get("ordinal"))) scope();
        String patientKey = unit.fields().get("patient_key");
        if (patientKey == null || !patientKey.matches("[0-9a-f]{64}")) scope();
        var imported = index.tables().getOrDefault("cds_import_patient", Map.of())
                .get(CdsImportKeys.hash(run, patientKey));
        if (imported == null || !run.equals(imported.fields().get("run_id")) || !patientKey.equals(imported.fields().get("patient_key"))
                || !Integer.toString(patient).equals(imported.fields().get("target_demographic_no"))) scope();
        var field = original.getFirst();
        if (!field.namespace().equals("cds") || !field.valueType().equals("{cds_dt}vendorId")) scope();
        StringBuilder originalText = new StringBuilder();
        try (var input = values.text(field)) {
            char[] buffer = new char[256]; int count;
            while ((count = input.read(buffer)) != -1) {
                checkpoint.run(); if (count == 0) continue;
                if (originalText.length() + count > 4096) scope();
                originalText.append(buffer, 0, count);
            }
        }
        // vendorId derives from xs:token; apply only its defined XML whitespace collapse.
        String sourceId = originalText.toString().replaceAll("[\\t\\r\\n ]+", " ");
        if (sourceId.startsWith(" ")) sourceId = sourceId.substring(1);
        if (sourceId.endsWith(" ")) sourceId = sourceId.substring(0, sourceId.length() - 1);
        if (sourceId.isEmpty() || !CdsExportNativeFieldAssessments.legal(sourceId, 20)) scope();
        if (!Objects.equals(sourceId, imported.fields().get("source_patient_id"))) scope();
        var result = combine(prior, field.id(), identity(registeredNamespace, patient));
        values.requireActive(); checkpoint.run(); return result;
    }

    static CdsExportDemographicRetainedCoreReconciler.Result combine(
            CdsExportDemographicRetainedCoreReconciler.Result prior, String sourceField, Identity identity) throws IOException {
        if (!prior.deferredSourceFields().contains(sourceField) || prior.decidedSourceFields().contains(sourceField)) scope();
        var decided = new HashSet<>(prior.decidedSourceFields()); decided.add(sourceField);
        var deferred = new HashSet<>(prior.deferredSourceFields()); deferred.remove(sourceField);
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (prior.coreRepresentable()) {
            changes.addAll(prior.coreChanges());
            changes.add(new CdsExportRetainedSectionWriter.Change("UniqueVendorIdSequence[1]", Set.of(sourceField), xml -> xml.text(identity.senderPatientId())));
        }
        return new CdsExportDemographicRetainedCoreReconciler.Result(prior.sourceManifestSha256(), prior.unitAddress(), decided,
                deferred, prior.fields(), prior.deferredNativeFields(), prior.blockers(), changes, prior.providerUses());
    }

    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_PATIENT_IDENTITY_SCOPE_INVALID"); }
}
