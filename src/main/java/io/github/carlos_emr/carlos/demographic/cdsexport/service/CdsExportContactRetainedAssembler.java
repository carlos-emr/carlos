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
import java.util.regex.Pattern;

/**
 * Whole-patient personal-contact changes for one retained demographic parent. Every source
 * occurrence, current row and field is accounted for; missing units/rows, aliases and new
 * associations to an imported contact require review. Other demographic fields need their
 * own reconciliation before the parent may be serialized or clinically published.
 */
public final class CdsExportContactRetainedAssembler {
    private static final Pattern CONTACT =
            Pattern.compile(
                    "^/OmdCds\\[1\\]/PatientRecord\\[1\\]/Demographics\\[1\\]/Contact\\[([1-9][0-9]{0,17})\\](?=/|$)");
    private static final Pattern DESTINATION = Pattern.compile("^Contact\\[([1-9][0-9]{0,17})\\]");

    public record Result(
            String sourceManifestSha256,
            String parentUnitAddress,
            long sourceContacts,
            long currentContacts,
            Set<String> sourceContactFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        public Result {
            sourceContactFields = Set.copyOf(sourceContactFields);
            fields = Map.copyOf(fields);
            changes = List.copyOf(changes);
        }

        @Override
        public String toString() {
            return "CdsExportContactRetainedAssembler.Result[protected]";
        }
    }

    public Result prepare(
            int patient,
            String parentAddress,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            List<CdsExportCapturedTextRowDto> associations,
            List<CdsExportCapturedTextRowDto> contacts,
            List<CdsExportNativeRowReceiptDto> witnesses,
            int maximumRows,
            long maximumBytes,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        checkpoint.run();
        if (parentAddress == null
                || !parentAddress.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                || index == null
                || checked == null
                || !index.appliedUnits().contains(parentAddress)
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().equals(index.references().keySet())
                || !index.appliedUnits().equals(checked.units().keySet())) scope();
        String run = parentAddress.substring(0, 36), parentKey = parentAddress.substring(37);
        var parent =
                index.tables()
                        .getOrDefault("cds_import_unit", Map.of())
                        .get(CdsImportKeys.hash(run, parentKey));
        if (parent == null
                || !"Demographics".equals(parent.fields().get("category"))
                || !run.equals(parent.fields().get("run_id"))
                || !parentKey.equals(parent.fields().get("unit_key"))
                || !"1".equals(parent.fields().get("ordinal"))
                || !("COMMITTED".equals(parent.fields().get("state"))
                        || "ALREADY_COMPLETE".equals(parent.fields().get("state")))) scope();
        var source =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.DEMOGRAPHICS,
                                parentAddress,
                                index,
                                checked,
                                allFields,
                                checkpoint);
        var sourceIds = new HashSet<String>();
        var ordinals = new HashSet<Long>();
        long maximum = 0;
        for (var field : source) {
            checkpoint.run();
            if (!run.equals(field.runId())
                    || field.occurrence() != 1
                    || !Objects.equals(parent.fields().get("patient_key"), field.sourcePatientKey())
                    || !"/OmdCds[1]/PatientRecord[1]/Demographics[1]"
                            .equals(
                                    CdsExportRetainedOccurrencePath.root(
                                            field, CdsCategory.DEMOGRAPHICS))) scope();
            var match = CONTACT.matcher(field.sourcePath());
            if (!match.find()) continue;
            long ordinal = Long.parseLong(match.group(1));
            maximum = Math.max(maximum, ordinal);
            ordinals.add(ordinal);
            sourceIds.add(field.id());
        }
        // This pass validates every raw identity, patient relationship, field and resource bound,
        // including imported rows whose native mapping is intentionally reconciled below.
        new CdsExportPersonalContactMapper()
                .map(patient, associations, contacts, maximumRows, maximumBytes, checkpoint);
        var nativeRows = new HashMap<String, CdsExportCapturedTextRowDto>();
        var remaining = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        for (var row : concat(associations, contacts)) {
            String address = row.table() + ":" + row.fields().get("id");
            if (nativeRows.putIfAbsent(address, row) != null) scope();
            remaining.put(address, row);
        }
        if (witnesses == null || witnesses.size() != nativeRows.size()) scope();
        var proof = new HashMap<String, CdsExportNativeRowReceiptDto>();
        for (var witness : witnesses) {
            checkpoint.run();
            if (witness == null) scope();
            String address = witness.table() + ":" + witness.id();
            var row = nativeRows.get(address);
            if (row == null || proof.putIfAbsent(address, witness) != null) scope();
            CdsExportContactFieldReconciler.requireCapturedWitness(row, witness, checkpoint);
        }
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        var claimed = new HashSet<String>();
        var importedIds = new HashSet<String>();
        var consumedOrdinals = new HashSet<Long>();
        long imported = 0;
        var units =
                index.tables().getOrDefault("cds_import_unit", Map.of()).values().stream()
                        .filter(
                                row ->
                                        run.equals(row.fields().get("run_id"))
                                                && Objects.equals(
                                                        parent.fields().get("patient_key"),
                                                        row.fields().get("patient_key"))
                                                && "Demographics_CONTACT"
                                                        .equals(row.fields().get("category")))
                        .sorted(Comparator.comparing(row -> row.fields().get("unit_key")))
                        .toList();
        for (var child : units) {
            checkpoint.run();
            String address = run + ":" + child.fields().get("unit_key");
            if (!index.appliedUnits().contains(address)) scope();
            var references = index.references().get(address);
            var cs = references.stream().filter(ref -> ref.table().equals("Contact")).toList();
            var links =
                    references.stream()
                            .filter(ref -> ref.table().equals("DemographicContact"))
                            .toList();
            if (cs.size() != 1 || links.size() != 1 || references.size() != 2) scope();
            String contactId = cs.getFirst().id(), linkId = links.getFirst().id();
            if (!importedIds.add(contactId))
                throw new IOException("CDS_EXPORT_CONTACT_ALIAS_REQUIRES_REVIEW");
            var decision =
                    new CdsExportContactFieldReconciler()
                            .reconcile(
                                    patient,
                                    address,
                                    index,
                                    checked,
                                    allFields,
                                    nativeRows.get("Contact:" + contactId),
                                    proof.get("Contact:" + contactId),
                                    nativeRows.get("DemographicContact:" + linkId),
                                    proof.get("DemographicContact:" + linkId),
                                    values,
                                    checkpoint);
            if (!decision.representable())
                throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException(
                        "CDS_EXPORT_CONTACT_MAPPING_REQUIRES_REVIEW");
            if (!parentAddress.equals(decision.binding().parentUnitAddress())
                    || !consumedOrdinals.add(decision.binding().ordinal())) scope();
            for (var field : decision.binding().fields()) if (!claimed.add(field.id())) scope();
            merge(fields, decision.fields());
            changes.addAll(decision.changes());
            imported++;
            if (remaining.remove("Contact:" + contactId) == null
                    || remaining.remove("DemographicContact:" + linkId) == null) scope();
        }
        if (!claimed.equals(sourceIds) || !consumedOrdinals.equals(ordinals))
            throw new IOException("CDS_EXPORT_CONTACT_SOURCE_OCCURRENCE_MISSING");
        var newAssociations = new ArrayList<CdsExportCapturedTextRowDto>();
        var newContacts = new ArrayList<CdsExportCapturedTextRowDto>();
        for (var row : remaining.values()) {
            checkpoint.run();
            if (row.table().equals("Contact")) newContacts.add(row);
            else {
                if ("2".equals(row.fields().get("type"))
                        && importedIds.contains(row.fields().get("contactId")))
                    throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException(
                            "CDS_EXPORT_CONTACT_NEW_ASSOCIATION_REQUIRES_REVIEW");
                newAssociations.add(row);
            }
        }
        var added =
                new CdsExportPersonalContactMapper()
                        .map(
                                patient,
                                newAssociations,
                                newContacts,
                                maximumRows,
                                maximumBytes,
                                checkpoint);
        if (!added.nativeRepresentable())
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException(
                        "CDS_EXPORT_CONTACT_MAPPING_REQUIRES_REVIEW");
        long offset = maximum;
        var addedFields =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        added.fields()
                .forEach(
                        (key, field) -> {
                            var match = DESTINATION.matcher(field.destination());
                            String destination = field.destination();
                            if (match.find())
                                destination =
                                        "Contact["
                                                + (offset + Long.parseLong(match.group(1)))
                                                + "]"
                                                + destination.substring(match.end());
                            addedFields.put(
                                    key,
                                    new CdsExportFieldAssessmentDto(
                                            field.presence(),
                                            field.outcome(),
                                            destination,
                                            field.rule()));
                        });
        merge(fields, addedFields);
        for (var contact : added.contacts()) {
            checkpoint.run();
            if (++maximum > Long.MAX_VALUE - 1) scope();
            String path = "Contact[" + maximum + "]";
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            path,
                            Set.of(),
                            xml -> CdsExportPersonalContactXmlWriter.content(contact, xml)));
        }
        if (changes.size() > 4096) throw new IOException("CDS_EXPORT_CONTACT_RESOURCE_LIMIT");
        new CdsExportNativeFieldAssessmentValidator()
                .validate(concat(associations, contacts), fields, 1_000_000, checkpoint);
        if (imported + added.contacts().size() != contacts.size()) scope();
        values.requireActive();
        checkpoint.run();
        return new Result(
                index.sourceManifestSha256(),
                parentAddress,
                imported,
                contacts.size(),
                claimed,
                fields,
                changes);
    }

    private static List<CdsExportCapturedTextRowDto> concat(
            List<CdsExportCapturedTextRowDto> first, List<CdsExportCapturedTextRowDto> second) {
        var rows = new ArrayList<>(first);
        rows.addAll(second);
        return rows;
    }

    private static void merge(
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> target,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> source)
            throws IOException {
        for (var field : source.entrySet())
            if (target.putIfAbsent(field.getKey(), field.getValue()) != null) scope();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_CONTACT_ASSEMBLY_SCOPE_INVALID");
    }
}
