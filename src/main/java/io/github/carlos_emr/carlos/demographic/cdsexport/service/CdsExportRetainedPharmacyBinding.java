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

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPhoneComponents;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.util.*;

/** Proves a retained preferred pharmacy through the parent's exact original association and preparation. */
public final class CdsExportRetainedPharmacyBinding {
    static final String ROOT = "PreferredPharmacy[1]";
    private static final String PREFIX = "/OmdCds[1]/PatientRecord[1]/Demographics[1]/";
    public record Result(String sourceManifestSha256, String parentUnitAddress, Set<String> sourceFields,
            Map<String, Set<String>> groups, Map<String, String> fieldIds, Map<String, String> sourceValues, CdsDemographicRelationsDto.Pharmacy preparation,
            CdsNativeRowState association, CdsNativeRowState pharmacy) {
        public Result {
            sourceFields = Set.copyOf(sourceFields);
            var copy = new LinkedHashMap<String, Set<String>>();
            groups.forEach((key, ids) -> copy.put(key, Set.copyOf(ids))); groups = Map.copyOf(copy);
            fieldIds = Map.copyOf(fieldIds); sourceValues = Map.copyOf(sourceValues);
            if (!fieldIds.keySet().equals(sourceValues.keySet()) || !Set.copyOf(fieldIds.values()).equals(sourceFields)) throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_BINDING_INVALID");
            if ((association == null) != (pharmacy == null)) throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_BINDING_INVALID");
        }
        public boolean projected() { return association != null; }
        Set<String> idsUnder(String path) {
            var ids = new HashSet<String>(); fieldIds.forEach((key, id) -> { if (key.equals(path) || key.startsWith(path + "/")) ids.add(id); }); return Set.copyOf(ids);
        }
        Set<String> idsForColumn(String column) {
            return switch (column) {
                case "address" -> {
                    var ids = new HashSet<>(idsUnder("Address[1]/Formatted[1]"));
                    for (String line : List.of("Line1", "Line2", "Line3")) ids.addAll(idsUnder("Address[1]/Structured[1]/" + line + "[1]")); yield Set.copyOf(ids);
                }
                case "city" -> idsUnder("Address[1]/Structured[1]/City[1]");
                case "province" -> idsUnder("Address[1]/Structured[1]/CountrySubdivisionCode[1]");
                case "postalCode" -> idsUnder("Address[1]/Structured[1]/PostalZipCode[1]");
                default -> Set.of();
            };
        }
        @Override public String toString() { return "CdsExportRetainedPharmacyBinding.Result[protected]"; }
    }

    /** Values remain callback scoped; the returned proof carries identities and bounded prepared values only. */
    public Result bind(int patient, String address, CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto demographic, CdsExportNativeRowReceiptDto witness,
            CdsExportRetainedSectionWriter.Values values, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run();
        var evidence = new CdsExportDemographicProjectionEvidence().verify(patient, address, index, checked, demographic, witness, checkpoint);
        var parent = new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, address, index, checked, allFields, checkpoint);
        var source = new LinkedHashMap<String, String>(); var ids = new HashSet<String>(); var fieldIds = new LinkedHashMap<String, String>();
        var groups = new LinkedHashMap<String, Set<String>>(); long sourceBytes = 0;
        for (var field : parent) {
            checkpoint.run(); if (!field.sourcePath().startsWith(PREFIX + "PreferredPharmacy[")) continue;
            String path = field.sourcePath().substring(PREFIX.length());
            if (!(path.equals(ROOT) || path.startsWith(ROOT + "/"))) invalid();
            verifyType(path, field);
            String relative = path.equals(ROOT) ? "" : path.substring(ROOT.length() + 1);
            if (!ids.add(field.id()) || source.putIfAbsent(relative, read(values, field, checkpoint)) != null) invalid();
            sourceBytes = Math.addExact(sourceBytes, 256L + relative.length() * 2L + source.get(relative).length() * 2L);
            if (sourceBytes > 1L << 20 || ids.size() > 128) throw new IOException("CDS_EXPORT_RETAINED_PHARMACY_RESOURCE_LIMIT");
            fieldIds.put(relative, field.id());
            String group = relative.isEmpty() ? "" : relative.split("/", 2)[0];
            groups.computeIfAbsent(group, ignored -> new HashSet<>()).add(field.id());
        }
        var preparation = evidence.preparation().relations().preferredPharmacy();
        if (!Objects.equals(preparation, projected(source))) throw new IOException("CDS_EXPORT_PHARMACY_SOURCE_PREPARATION_DIFFERS");
        String run = address.substring(0, 36), key = address.substring(37);
        var unit = index.tables().get("cds_import_unit").get(CdsImportKeys.hash(run, key));
        var refs = index.references().get(address);
        if (unit == null || refs == null) invalid();
        if (unit.fields().get("native_baseline") == null) {
            if (refs.stream().anyMatch(ref -> Set.of("pharmacyInfo", "demographicPharmacy").contains(ref.table()))) baseline();
            return result(index, address, ids, groups, fieldIds, source, preparation, null, null, values, checkpoint);
        }
        CdsNativeBaselineCodec.Document document;
        try { document = CdsNativeBaselineCodec.decode(unit.fields().get("native_baseline"), unit.fields().get("target_references"), unit.fields().get("target_digest")); }
        catch (IllegalArgumentException malformed) { throw new IOException("CDS_EXPORT_PHARMACY_FIELD_BASELINE_REQUIRED"); }
        var declared = new HashMap<String, String>(); var actual = new HashMap<String, String>();
        for (var ref : refs) if (declared.putIfAbsent(ref.table() + "/" + ref.id(), ref.digest()) != null) invalid();
        for (var row : document.rows()) if (actual.putIfAbsent(row.table() + "/" + row.id(), row.digest()) != null) invalid();
        if (!declared.equals(actual)) invalid();
        var links = document.rows().stream().filter(row -> row.table().equals("demographicPharmacy")).toList();
        var pharmacies = document.rows().stream().filter(row -> row.table().equals("pharmacyInfo")).toList();
        if (links.isEmpty() && pharmacies.isEmpty()) return result(index, address, ids, groups, fieldIds, source, preparation, null, null, values, checkpoint);
        if (links.size() != 1 || pharmacies.size() != 1 || preparation == null || ids.isEmpty()) baseline();
        var link = links.getFirst(); var pharmacy = pharmacies.getFirst();
        if (!matches(link, "demographic_no", Integer.toString(patient)) || !matches(link, "pharmacyID", pharmacy.id())
                || !matches(link, "status", "1") || !matches(link, "preferredOrder", "1") || !matches(link, "consentToContact", null)
                || !matches(pharmacy, "status", "1")) baseline();
        for (var entry : prepared(preparation).entrySet()) if (!matches(pharmacy, entry.getKey(), entry.getValue())) baseline();
        return result(index, address, ids, groups, fieldIds, source, preparation, link, pharmacy, values, checkpoint);
    }
    static Map<String, String> prepared(CdsDemographicRelationsDto.Pharmacy value) {
        var result = new LinkedHashMap<String, String>();
        result.put("name", value.name()); result.put("address", value.address()); result.put("city", value.city());
        result.put("province", value.province()); result.put("postalCode", value.postal()); result.put("phone1", value.phone());
        result.put("fax", value.fax()); result.put("email", value.email()); return result;
    }
    private static CdsDemographicRelationsDto.Pharmacy projected(Map<String, String> values) throws IOException {
        String name = values.get("Name[1]"); if (name == null || name.isBlank()) return null;
        String formatted = available(values, "Address[1]/Formatted[1]", 255); var lines = new ArrayList<String>();
        for (String part : List.of("Line1", "Line2", "Line3")) {
            String line = values.get("Address[1]/Structured[1]/" + part + "[1]"); if (line != null) lines.add(line);
        }
        String postal = values.get("Address[1]/Structured[1]/PostalZipCode[1]/PostalCode[1]");
        if (postal == null) postal = values.get("Address[1]/Structured[1]/PostalZipCode[1]/ZipCode[1]");
        try {
            return new CdsDemographicRelationsDto.Pharmacy(name, formatted != null ? formatted : lines.isEmpty() ? null : String.join("\n", lines),
                    available(values, "Address[1]/Structured[1]/City[1]", 60), CdsHealthCardAuthority.normalize(values.get("Address[1]/Structured[1]/CountrySubdivisionCode[1]")),
                    postal, phone(values, "PhoneNumber"), phone(values, "FaxNumber"), values.get("EmailAddress[1]"));
        } catch (IllegalArgumentException invalid) { throw new IOException("CDS_EXPORT_PHARMACY_SOURCE_PREPARATION_DIFFERS"); }
    }
    private static String phone(Map<String, String> values, String element) {
        String root = element + "[1]/", number = values.get(root + "phoneNumber[1]");
        if (number == null) number = CdsPhoneComponents.combine(values.get(root + "areaCode[1]"), values.get(root + "exchange[1]"), values.get(root + "number[1]"));
        if (number == null) return null;
        String extension = values.get(root + "extension[1]"); String combined = number + (extension == null || extension.isEmpty() ? "" : " ext " + extension);
        return combined.codePointCount(0, combined.length()) > 20 ? null : combined;
    }
    private static Result result(CdsExportRetainedIndexDto index, String address, Set<String> ids, Map<String, Set<String>> groups,
            Map<String, String> fieldIds, Map<String, String> source, CdsDemographicRelationsDto.Pharmacy preparation, CdsNativeRowState association, CdsNativeRowState pharmacy,
            CdsExportRetainedSectionWriter.Values values, Runnable checkpoint) throws IOException {
        values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, ids, groups, fieldIds, source, preparation, association, pharmacy);
    }
    private static String available(Map<String, String> values, String path, int maximum) {
        String value = values.get(path); return value != null && value.codePointCount(0, value.length()) > maximum ? null : value;
    }
    private static String read(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, Runnable checkpoint) throws IOException {
        var result = new StringBuilder(); char[] buffer = new char[256];
        try (Reader input = values.text(field)) {
            for (int n; (n = input.read(buffer)) != -1; ) { checkpoint.run(); if (n == 0 || result.length() + n > 65536) invalid(); result.append(buffer, 0, n); }
        } finally { Arrays.fill(buffer, '\0'); }
        return result.toString();
    }
    private static void verifyType(String path, CdsExportRetainedFieldDto field) throws IOException {
        var type = CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord").child("cds", "Demographics");
        String namespace = "cds"; String[] parts = path.split("/", -1);
        for (int n = 0; n < parts.length; n++) {
            String part = parts[n]; if (type == null) invalid();
            if (part.startsWith("@")) {
                if (n != parts.length - 1 || !part.matches("@[A-Za-z][A-Za-z0-9]*")) invalid();
                namespace = ""; type = type.attribute(namespace, part.substring(1));
            } else {
                if (!part.matches("[A-Za-z][A-Za-z0-9]*\\[1\\]")) invalid();
                String name = part.substring(0, part.length() - 3); var names = type.childNames().stream().filter(child -> child.local().equals(name)).toList();
                if (names.size() != 1) invalid(); namespace = names.getFirst().namespace(); type = type.child(namespace, name);
            }
        }
        if (type == null || !namespace.equals(field.namespace()) || !("{" + type.getTypeNamespace() + "}" + type.getTypeName()).equals(field.valueType())) invalid();
    }
    private static boolean matches(CdsNativeRowState state, String column, String value) throws IOException { return CdsExportCapturedTextWitness.matches(state, column, value); }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_PHARMACY_SOURCE_BINDING_SCOPE_INVALID"); }
    private static void baseline() throws IOException { throw new IOException("CDS_EXPORT_PHARMACY_PREPARED_PROJECTION_NOT_PROVEN"); }
}
