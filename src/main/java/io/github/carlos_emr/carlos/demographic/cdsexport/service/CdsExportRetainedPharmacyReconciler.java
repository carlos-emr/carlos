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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/** Reconciles a retained pharmacy without discarding structured source details flattened by import. */
public final class CdsExportRetainedPharmacyReconciler {
    public record NativeRows(String sourceManifestSha256, List<CdsExportCapturedTextRowDto> links,
            List<CdsExportCapturedTextRowDto> pharmacies, List<CdsExportNativeRowReceiptDto> witnesses) {
        public NativeRows {
            if (sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_NATIVE_GENERATION_INVALID");
            links = List.copyOf(links); pharmacies = List.copyOf(pharmacies); witnesses = List.copyOf(witnesses);
            if ((long) links.size() + pharmacies.size() > 8192 || witnesses.size() > 8192) throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_NATIVE_ROWS_INVALID");
        }
        @Override public String toString() { return "CdsExportRetainedPharmacyReconciler.NativeRows[protected]"; }
    }
    public record Result(String sourceManifestSha256, String parentUnitAddress, Set<String> sourceFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, Set<String> blockers,
            List<CdsExportRetainedSectionWriter.Change> proposedChanges) {
        public Result { sourceFields = Set.copyOf(sourceFields); fields = Map.copyOf(fields); blockers = Set.copyOf(blockers); proposedChanges = List.copyOf(proposedChanges); }
        public boolean representable() { return blockers.isEmpty() && fields.values().stream().noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW); }
        public List<CdsExportRetainedSectionWriter.Change> changes() throws IOException {
            if (!representable()) throw new IOException("CDS_EXPORT_RETAINED_PHARMACY_REQUIRES_REVIEW"); return proposedChanges;
        }
        @Override public String toString() { return "CdsExportRetainedPharmacyReconciler.Result[protected]"; }
    }
    private static final Map<String, List<String>> GROUPS = Map.of(
            "Name[1]", List.of("name"), "Address[1]", List.of("address", "city", "province", "postalCode"),
            "PhoneNumber[1]", List.of("phone1"), "FaxNumber[1]", List.of("fax"), "EmailAddress[1]", List.of("email"));

    /** A new rank or removed/replaced association is reviewable history, never an implicit deletion or same-name match. */
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto demographic, CdsExportNativeRowReceiptDto demographicWitness,
            NativeRows current, int maximumRows, long maximumBytes, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(current); Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run();
        if (!index.sourceManifestSha256().equals(current.sourceManifestSha256())) throw new IOException("CDS_EXPORT_PHARMACY_NATIVE_GENERATION_INVALID");
        var mapping = new CdsExportPharmacyMapper().map(patient, current.links(), current.pharmacies(), maximumRows, maximumBytes, checkpoint);
        var all = new ArrayList<>(current.links()); all.addAll(current.pharmacies());
        var receipts = new HashMap<String, CdsExportNativeRowReceiptDto>(); var rows = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (var receipt : current.witnesses()) if (receipts.putIfAbsent(receipt.table() + "/" + receipt.recordKey(), receipt) != null) scope();
        for (var row : all) {
            checkpoint.run(); String identity = identityColumn(row.table()), id = row.fields().get(identity);
            if (rows.putIfAbsent(row.table() + "/" + id, row) != null) scope();
            CdsExportCapturedTextWitness.require(row, receipts.remove(row.table() + "/" + row.recordKey()), identity, checkpoint);
        }
        if (!receipts.isEmpty()) scope();
        for (var reference : checked.units().getOrDefault(address, List.of())) {
            checkpoint.run(); if (!Set.of("pharmacyInfo", "demographicPharmacy").contains(reference.expected().table())) continue;
            var row = rows.get(reference.expected().table() + "/" + reference.expected().id());
            if (row == null) { if (reference.reason() != CdsExportRetainedReceiptVerificationDto.Reason.ROW_NOT_IN_CAPTURE) scope(); }
            else {
                var receipt = current.witnesses().stream().filter(item -> item.table().equals(row.table()) && item.recordKey().equals(row.recordKey())).findFirst().orElseThrow();
                if (!Objects.equals(reference.currentDigest(), receipt.digest())) scope();
            }
        }
        var binding = new CdsExportRetainedPharmacyBinding().bind(patient, address, index, checked, allFields, demographic, demographicWitness, values, checkpoint);
        var decisions = new LinkedHashMap<>(mapping.fields()); var blockers = new LinkedHashSet<String>();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (binding.projected()) {
            var link = rows.get("demographicPharmacy/" + binding.association().id()); var pharmacy = rows.get("pharmacyInfo/" + binding.pharmacy().id());
            if (link == null || pharmacy == null || !binding.pharmacy().id().equals(link.fields().get("pharmacyID"))) blockers.add("ORIGINAL_PREFERRED_PHARMACY_MISSING_OR_REPLACED");
            else {
                var prepared = CdsExportRetainedPharmacyBinding.prepared(binding.preparation());
                for (var group : GROUPS.entrySet()) {
                    checkpoint.run(); var columns = group.getValue();
                    if (group.getKey().equals("Address[1]")) {
                        var addressChanges = new CdsExportRetainedPharmacyAddressReconciler().reconcile(binding, pharmacy, mapping, decisions);
                        if (addressChanges == null) blockers.add("PREFERRED_PHARMACY_ADDRESS_REQUIRES_REVIEW"); else changes.addAll(addressChanges); continue;
                    }
                    if (columns.stream().allMatch(column -> Objects.equals(prepared.get(column), pharmacy.fields().get(column)))) {
                        for (String column : columns) {
                            var key = new CdsExportNativeFieldKeyDto(pharmacy.table(), pharmacy.recordKey(), column); var prior = decisions.get(key);
                            decisions.put(key, new CdsExportFieldAssessmentDto(prior.presence(), Outcome.MAPPED,
                                    "PreferredPharmacy/" + group.getKey(), "VERIFIED_UNCHANGED_RETAINED_PHARMACY_GROUP"));
                        }
                    } else {
                        boolean unprojected = columns.stream().allMatch(column -> prepared.get(column) == null) && binding.groups().containsKey(group.getKey());
                        boolean invalid = columns.stream().anyMatch(column -> decisions.get(new CdsExportNativeFieldKeyDto(pharmacy.table(), pharmacy.recordKey(), column)).outcome() == Outcome.REQUIRES_REVIEW);
                        if (unprojected || invalid || mapping.preferred() == null) blockers.add("PREFERRED_PHARMACY_GROUP_REQUIRES_REVIEW");
                        else changes.add(change(group.getKey(), binding.groups().getOrDefault(group.getKey(), Set.of()), mapping.preferred()));
                    }
                }
            }
        } else if (!current.links().isEmpty()) {
            if (!binding.sourceFields().isEmpty()) blockers.add("UNPROJECTED_SOURCE_PREFERRED_PHARMACY_CONFLICT");
            else if (mapping.nativeRepresentable() && mapping.preferred() != null) {
                changes.add(new CdsExportRetainedSectionWriter.Change(CdsExportRetainedPharmacyBinding.ROOT, Set.of(), xml -> CdsExportPharmacyXmlWriter.writeContent(mapping.preferred(), xml)));
            }
        }
        if (decisions.values().stream().anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW)) blockers.add("CURRENT_PREFERRED_PHARMACY_FIELDS_REQUIRE_REVIEW");
        if (!blockers.isEmpty()) changes.clear(); values.requireActive(); checkpoint.run();
        return new Result(index.sourceManifestSha256(), address, binding.sourceFields(), decisions, blockers, changes);
    }
    private static CdsExportRetainedSectionWriter.Change change(String group, Set<String> ids, CdsExportPreferredPharmacyDto pharmacy) throws IOException {
        if (pharmacy == null) throw new IOException("CDS_EXPORT_RETAINED_PHARMACY_REQUIRES_REVIEW");
        CdsExportXmlComposer.Content content = switch (group) {
            case "Name[1]" -> xml -> xml.text(pharmacy.name());
            case "Address[1]" -> pharmacy.address() == null ? null : xml -> CdsExportPharmacyXmlWriter.writeAddress(pharmacy.address(), xml);
            case "PhoneNumber[1]" -> pharmacy.phone() == null ? null : xml -> CdsExportPharmacyXmlWriter.writePhone(pharmacy.phone(), xml);
            case "FaxNumber[1]" -> pharmacy.fax() == null ? null : xml -> CdsExportPharmacyXmlWriter.writePhone(pharmacy.fax(), xml);
            case "EmailAddress[1]" -> pharmacy.email() == null ? null : xml -> xml.text(pharmacy.email());
            default -> throw new IOException("CDS_EXPORT_RETAINED_PHARMACY_GROUP_INVALID");
        };
        if (content == null && ids.isEmpty()) throw new IOException("CDS_EXPORT_RETAINED_PHARMACY_GROUP_INVALID");
        return new CdsExportRetainedSectionWriter.Change(CdsExportRetainedPharmacyBinding.ROOT + "/" + group, ids, content);
    }
    private static String identityColumn(String table) throws IOException {
        if (table.equals("pharmacyInfo")) return "recordID"; if (table.equals("demographicPharmacy")) return "id"; scope(); return "";
    }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_PHARMACY_CURRENT_WITNESS_SCOPE_INVALID"); }
}
