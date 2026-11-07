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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact native phone extensions/mobile values. Hidden history, duplicate keys and unknown populated properties need review. */
public final class CdsExportDemographicPhonesMapper {
    private static final Map<String, String> KEYS = Map.of("hPhoneExt", "R", "wPhoneExt", "W", "demo_cell", "C");
    public CdsExportDemographicMappingDto map(CdsExportDemographicMappingDto base, CdsExportDemographicRowDto current,
            List<CdsExportCapturedTextRowDto> properties, int maximumRows, long maximumBytes, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint); checkpoint.run();
        if (base == null || current == null || base.patient() != current.demographicNo()
                || !base.phones().equals(new CdsExportDemographicMapper().map(current).phones())) scope();
        if (properties == null || maximumRows < 1 || maximumRows > 8192 || maximumBytes < 1 || maximumBytes > (64L << 20))
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_PHONE_LIMITS_INVALID");
        if (properties.size() > maximumRows) limit();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var seen = new HashSet<String>(); var known = new HashMap<String, CdsExportCapturedTextRowDto>();
        var duplicate = new HashSet<String>(); long bytes = 0;
        for (var row : properties) {
            checkpoint.run(); requireRow(current.demographicNo(), row);
            if (!seen.add(row.recordKey())) scope();
            bytes = Math.addExact(bytes, 2048L);
            for (var entry : row.fields().entrySet()) {
                if (entry.getValue() != null) bytes = Math.addExact(bytes, entry.getValue().getBytes(StandardCharsets.UTF_8).length + 32L);
                fields.put(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), entry.getKey()),
                        new CdsExportFieldAssessmentDto(presence(entry.getValue()), present(entry.getValue()) ? Outcome.REQUIRES_REVIEW : Outcome.ABSENT,
                                "", "UNMAPPED_NATIVE_DEMOGRAPHIC_PROPERTY"));
            }
            if (bytes > maximumBytes) limit();
            for (String column : List.of("id", "demographic_no", "provider_no", "date_time", "dashboard_single_value_key"))
                mark(row, fields, column, Outcome.OPERATIONAL, "", "CAPTURED_NATIVE_PROPERTY_IDENTITY_AND_METADATA");
            String key = row.fields().get("key_val"), hidden = row.fields().get("hidden");
            if (!"0".equals(hidden)) {
                mark(row, fields, "hidden", Outcome.REQUIRES_REVIEW, "", "HIDDEN_OR_UNKNOWN_PROPERTY_HISTORY_REQUIRES_REVIEW"); continue;
            }
            mark(row, fields, "hidden", Outcome.OPERATIONAL, "", "EXACT_VISIBLE_NATIVE_PROPERTY");
            if (key == null || !KEYS.containsKey(key)) continue;
            String path = path(key); mark(row, fields, "key_val", Outcome.MAPPED, path, "EXACT_NATIVE_PHONE_PROPERTY_KEY");
            if (known.putIfAbsent(key, row) != null) duplicate.add(key);
        }
        var phones = new ArrayList<CdsExportDemographicMappingDto.Phone>();
        for (var phone : base.phones()) {
            String key = phone.type().equals("R") ? "hPhoneExt" : "wPhoneExt"; var row = known.get(key); String extension = null;
            if (row != null && !duplicate.contains(key)) {
                String value = row.fields().get("value");
                if (value == null) {
                    mark(row, fields, "value", Outcome.ABSENT, path(key), "EXPLICIT_NULL_PHONE_EXTENSION");
                    mark(row, fields, "key_val", Outcome.OPERATIONAL, "", "PROPERTY_KEY_OF_AN_ABSENT_EXTENSION");
                }
                else try {
                    new CdsExportDemographicMappingDto.Phone(phone.type(), phone.number(), value); extension = value;
                    mark(row, fields, "value", Outcome.MAPPED, path(key), "EXACT_CURRENT_PHONE_EXTENSION");
                } catch (IllegalArgumentException review) { mark(row, fields, "value", Outcome.REQUIRES_REVIEW, path(key), "PHONE_EXTENSION_VALUES_REQUIRES_REVIEW"); }
            }
            phones.add(new CdsExportDemographicMappingDto.Phone(phone.type(), phone.number(), extension));
        }
        for (String key : List.of("hPhoneExt", "wPhoneExt")) {
            var row = known.get(key); if (row == null || duplicate.contains(key)) continue;
            if (base.phones().stream().noneMatch(phone -> phone.type().equals(KEYS.get(key)))) {
                String value = row.fields().get("value");
                mark(row, fields, "value", present(value) ? Outcome.REQUIRES_REVIEW : Outcome.ABSENT, path(key), "EXTENSION_WITHOUT_A_NATIVE_NUMBER");
                if (!present(value)) mark(row, fields, "key_val", Outcome.OPERATIONAL, "", "EMPTY_EXTENSION_WITHOUT_A_NATIVE_NUMBER");
            }
        }
        var mobile = known.get("demo_cell");
        if (mobile != null && !duplicate.contains("demo_cell")) {
            String value = mobile.fields().get("value");
            if (!present(value)) {
                mark(mobile, fields, "value", Outcome.ABSENT, path("demo_cell"), "EXPLICIT_ABSENT_MOBILE_NUMBER");
                mark(mobile, fields, "key_val", Outcome.OPERATIONAL, "", "PROPERTY_KEY_OF_AN_ABSENT_MOBILE_NUMBER");
            }
            else try {
                phones.add(new CdsExportDemographicMappingDto.Phone("C", value, null));
                mark(mobile, fields, "value", Outcome.MAPPED, path("demo_cell"), "EXACT_CURRENT_MOBILE_NUMBER");
            } catch (IllegalArgumentException review) { mark(mobile, fields, "value", Outcome.REQUIRES_REVIEW, path("demo_cell"), "MOBILE_NUMBER_VALUES_REQUIRES_REVIEW"); }
        }
        for (var row : properties) if (duplicate.contains(row.fields().get("key_val"))) {
            mark(row, fields, "key_val", Outcome.REQUIRES_REVIEW, path(row.fields().get("key_val")), "DUPLICATE_NATIVE_PHONE_PROPERTY_REQUIRES_REVIEW");
            mark(row, fields, "value", Outcome.REQUIRES_REVIEW, path(row.fields().get("key_val")), "DUPLICATE_NATIVE_PHONE_PROPERTY_REQUIRES_REVIEW");
        }
        new CdsExportNativeFieldAssessmentValidator().validate(properties, fields, 1_000_000, checkpoint);
        checkpoint.run(); return base.withPhones(phones, fields);
    }
    private static void requireRow(int patient, CdsExportCapturedTextRowDto row) throws IOException {
        if (row == null || !row.table().equals("demographicExt")
                || !row.fields().keySet().equals(Set.copyOf(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_EXT.columns()))
                || !Integer.toString(patient).equals(row.fields().get("demographic_no")) || !row.exactNumbers().isEmpty()) scope();
        try {
            String id = row.fields().get("id"); long value = Long.parseLong(id);
            if (!Long.toString(value).equals(id) || !CdsExportPatientTableSourceReader.key(value).equals(row.recordKey())) scope();
        } catch (NumberFormatException | NullPointerException malformed) { scope(); }
    }
    private static String path(String key) { return "PhoneNumber[" + KEYS.get(key) + "]/" + (key.equals("demo_cell") ? "phoneNumber" : "extension"); }
    private static Presence presence(String value) { return value == null ? Presence.SQL_NULL : value.isEmpty() ? Presence.EMPTY : Presence.VALUE; }
    private static boolean present(String value) { return value != null && !value.isEmpty(); }
    private static void mark(CdsExportCapturedTextRowDto row, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String column, Outcome outcome, String path, String rule) {
        fields.put(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column),
                new CdsExportFieldAssessmentDto(presence(row.fields().get(column)), outcome, path, rule));
    }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_SCOPE_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_RESOURCE_LIMIT"); }
}
