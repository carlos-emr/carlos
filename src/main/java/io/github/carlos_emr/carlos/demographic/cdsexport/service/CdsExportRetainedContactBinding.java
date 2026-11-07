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
import java.util.*;

/**
 * Binds one native Contact child to its original subtree within the parent's verified field
 * pages. This proves source ownership/coordinates only: clinical projection/reconciliation,
 * effective values, native row receipts and final output still require their own checks.
 * No import staging path is reopened and no original value is replayed here.
 */
public final class CdsExportRetainedContactBinding {
    public record Result(String sourceManifestSha256, String childUnitAddress, String parentUnitAddress,
            String sourceRoot, long ordinal, List<CdsExportRetainedFieldDto> fields) {
        public Result { fields = List.copyOf(fields); }
        @Override public String toString() { return "CdsExportRetainedContactBinding.Result[protected]"; }
    }
    public Result bind(int patient, String address, CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> supplied,
            Runnable checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint); checkpoint.run();
        if (patient < 1 || index == null || checked == null || supplied == null || address == null
                || !address.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().equals(checked.units().keySet()) || !index.appliedUnits().contains(address)) scope();
        String run = address.substring(0, 36), key = address.substring(37); var units = index.tables().getOrDefault("cds_import_unit", Map.of());
        if (units.size() > 131_072 || supplied.size() > 1_000_000) throw new IOException("CDS_EXPORT_CONTACT_BINDING_RESOURCE_LIMIT");
        var child = units.get(CdsImportKeys.hash(run, key)); requireUnit(child, run, key, "Demographics_CONTACT");
        long ordinal = positive(child.fields().get("ordinal")); String patientKey = child.fields().get("patient_key");
        if (patientKey == null || !patientKey.matches("[0-9a-f]{64}")) scope();
        var patientRow = index.tables().getOrDefault("cds_import_patient", Map.of()).get(CdsImportKeys.hash(run, patientKey));
        var origin = index.tables().getOrDefault("cds_import_run", Map.of()).get(run);
        if (patientRow == null || origin == null || !run.equals(origin.fields().get("id")) || !run.equals(patientRow.fields().get("run_id")) || !patientKey.equals(patientRow.fields().get("patient_key"))
                || !Integer.toString(patient).equals(patientRow.fields().get("target_demographic_no")) || !"0".equals(patientRow.fields().get("held"))) scope();
        String lineage = patientRow.fields().get("lineage_key");
        if (lineage == null || !lineage.matches("[0-9a-f]{64}") || !lineage.equals(child.fields().get("lineage_key"))
                || !lineage.equals(hash(origin.fields().get("source_namespace"), patientRow.fields().get("source_patient_id"), patientRow.fields().get("copy_key")))) scope();
        var parents = new LinkedHashSet<String>();
        for (var edge : index.tables().getOrDefault("cds_import_dependency", Map.of()).values()) {
            checkpoint.run(); if (!run.equals(edge.fields().get("run_id")) || !key.equals(edge.fields().get("unit_key"))) continue;
            String parent = edge.fields().get("depends_on_unit_key");
            if (parent == null || !parent.matches("[0-9a-f]{64}")) scope(); parents.add(parent);
        }
        // The importer emits both native and demographic dependencies, which are the same
        // parent for Demographics/Contact. A different recipe needs explicit qualification.
        if (parents.size() != 1) scope(); String parentKey = parents.iterator().next(), parentAddress = run + ":" + parentKey;
        var parent = units.get(CdsImportKeys.hash(run, parentKey)); requireUnit(parent, run, parentKey, "Demographics");
        if (!index.appliedUnits().contains(parentAddress) || !patientKey.equals(parent.fields().get("patient_key"))
                || !lineage.equals(parent.fields().get("lineage_key")) || positive(parent.fields().get("ordinal")) != 1) scope();
        var all = new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, parentAddress, index, checked, supplied, checkpoint);
        String root = null; var children = new ArrayList<CdsExportRetainedFieldDto>();
        for (var field : all) {
            checkpoint.run(); String candidate = CdsExportRetainedOccurrencePath.root(field, CdsCategory.DEMOGRAPHICS);
            if (!run.equals(field.runId()) || !patientKey.equals(field.sourcePatientKey()) || field.occurrence() != 1
                    || !candidate.equals("/OmdCds[1]/PatientRecord[1]/Demographics[1]")) scope();
            if (root == null) root = candidate; else if (!root.equals(candidate)) scope();
            String prefix = root + "/Contact[" + ordinal + "]";
            if (field.sourcePath().equals(prefix) || field.sourcePath().startsWith(prefix + "/")) children.add(field);
        }
        if (root == null || children.isEmpty()) throw new IOException("CDS_EXPORT_CONTACT_SOURCE_FIELDS_REQUIRED");
        String childRoot = root + "/Contact[" + ordinal + "]";
        if (!parentKey.equals(CdsImportKeys.hash(lineage, root, "NATIVE", "0"))
                || !key.equals(CdsImportKeys.hash(lineage, childRoot, "_CONTACT", Long.toString(ordinal)))) scope();
        checkpoint.run(); return new Result(index.sourceManifestSha256(), address, parentAddress, childRoot, ordinal, children);
    }
    private static String hash(String namespace, String sourcePatient, String copy) throws IOException {
        if (namespace == null || sourcePatient == null || copy == null) scope(); return CdsImportKeys.hash(namespace, sourcePatient, copy);
    }
    private static void requireUnit(CdsExportCapturedTextRowDto row, String run, String key, String category) throws IOException {
        if (row == null || !row.table().equals("cds_import_unit") || !CdsImportKeys.hash(run, key).equals(row.recordKey())
                || !run.equals(row.fields().get("run_id")) || !key.equals(row.fields().get("unit_key")) || !category.equals(row.fields().get("category"))
                || !("COMMITTED".equals(row.fields().get("state")) || "ALREADY_COMPLETE".equals(row.fields().get("state")))) scope();
    }
    private static long positive(String value) throws IOException {
        try { long number = Long.parseLong(value); if (number < 1 || value.length() > 18 || !Long.toString(number).equals(value)) scope(); return number; }
        catch (NumberFormatException | NullPointerException invalid) { scope(); return 0; }
    }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID"); }
}
