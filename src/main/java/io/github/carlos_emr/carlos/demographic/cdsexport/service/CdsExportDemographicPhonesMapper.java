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
import java.util.*;

/**
 * Exact native phone extensions/mobile values. Every other visible populated property, and a
 * phone value the CDS phone element cannot hold, is carried in the native demographic NewCategory
 * record ({@link CdsExportDemographicSupportingMapper}). No property holds the chart:
 *
 * <ul>
 *   <li>A row whose {@code hidden} flag is set is carried whole, flag included. Only the REST
 *       demographic service writes the flag ({@code DemographicExtConverter}) and no CARLOS screen
 *       or report reads it, so the row is not a phone value the element states.
 *   <li>A populated value without a key (the REST service saves an extra without one) is carried
 *       as it is stored.
 *   <li>Two visible rows of one phone key (a table adopted without the one-row-per-key index of
 *       V1.0.54, where the REST service or older data can repeat a key): the row the chart shows,
 *       the latest {@code date_time} and then the highest id as
 *       {@code DemographicExtDao.getLatestDemographicExt} orders them, is decided as a single row
 *       would be, and every other row of that key is carried.
 * </ul>
 */
public final class CdsExportDemographicPhonesMapper {
    private static final Map<String, String> KEYS =
            Map.of("hPhoneExt", "R", "wPhoneExt", "W", "demo_cell", "C");

    /** Rule of the flag of a hidden property row, carried with the row. */
    static final String HIDDEN_PROPERTY = "HIDDEN_PROPERTY_CARRIED_WITH_ITS_FLAG";

    public CdsExportDemographicMappingDto map(
            CdsExportDemographicMappingDto base,
            CdsExportDemographicRowDto current,
            List<CdsExportCapturedTextRowDto> properties,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (base == null
                || current == null
                || base.patient() != current.demographicNo()
                || !base.phones().equals(new CdsExportDemographicMapper().map(current).phones()))
            scope();
        if (properties == null)
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_PHONE_LIMITS_INVALID");
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var seen = new HashSet<String>();
        var rowsOfKey = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        for (var row : properties) {
            checkpoint.run();
            requireRow(current.demographicNo(), row);
            if (!seen.add(row.recordKey())) scope();
            for (var entry : row.fields().entrySet()) {
                fields.put(
                        new CdsExportNativeFieldKeyDto(
                                row.table(), row.recordKey(), entry.getKey()),
                        new CdsExportFieldAssessmentDto(
                                presence(entry.getValue()),
                                present(entry.getValue())
                                        ? Outcome.REQUIRES_REVIEW
                                        : Outcome.ABSENT,
                                "",
                                "UNMAPPED_NATIVE_DEMOGRAPHIC_PROPERTY"));
            }
            for (String column :
                    List.of(
                            "id",
                            "demographic_no",
                            "provider_no",
                            "date_time",
                            "dashboard_single_value_key"))
                mark(
                        row,
                        fields,
                        column,
                        Outcome.OPERATIONAL,
                        "",
                        "CAPTURED_NATIVE_PROPERTY_IDENTITY_AND_METADATA");
            String key = row.fields().get("key_val"), hidden = row.fields().get("hidden");
            if (!"0".equals(hidden)) {
                // Written only by the REST API and read by nothing in CARLOS: the row travels
                // whole in the record, its flag included, and a CARLOS import restores it.
                carryPopulated(row, fields, "key_val", "value");
                mark(
                        row,
                        fields,
                        "hidden",
                        Outcome.MAPPED,
                        CdsExportDemographicSupportingMapper.DESTINATION,
                        HIDDEN_PROPERTY);
                continue;
            }
            mark(row, fields, "hidden", Outcome.OPERATIONAL, "", "EXACT_VISIBLE_NATIVE_PROPERTY");
            if (key == null || !KEYS.containsKey(key)) {
                String value = row.fields().get("value");
                // DemographicUpdate2Action saves one row for every key the Master Record form
                // lists (DemographicEditVersion.editedExtensionKeys()), with a NULL or empty
                // value for each field left blank. An empty row states no patient fact, so its
                // key is accounted for here.
                if (key != null && !present(value))
                    mark(
                            row,
                            fields,
                            "key_val",
                            Outcome.OPERATIONAL,
                            "",
                            "PROPERTY_KEY_OF_AN_EMPTY_EXTENSION");
                // Every other visible property the Master Record form or a site's configured
                // extension writes (ethnicity, consents, paper-chart status, First Nations
                // details...) has no Demographics element: the key and value travel in the
                // native demographic NewCategory record. So does the warning level "0" ("Not
                // specified") that edit-form-clinical.jsp:167-168 and 1282-1285 select for a
                // patient without one: every save writes it, and a CARLOS import restores it.
                // A value saved without a key (the REST API accepts one) travels as it is stored.
                else carryPopulated(row, fields, "key_val", "value");
                continue;
            }
            rowsOfKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        // The row of each phone key the chart shows; any other row of the key is carried.
        var known = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (var entry : rowsOfKey.entrySet()) {
            var shown =
                    entry.getValue().stream()
                            .max(CdsExportDemographicPhonesMapper::compareShown)
                            .orElseThrow();
            known.put(entry.getKey(), shown);
            String path = path(entry.getKey());
            for (var row : entry.getValue()) {
                checkpoint.run();
                if (row == shown)
                    mark(
                            row,
                            fields,
                            "key_val",
                            Outcome.MAPPED,
                            path,
                            "EXACT_NATIVE_PHONE_PROPERTY_KEY");
                else if (present(row.fields().get("value"))) carry(row, fields);
                else
                    mark(
                            row,
                            fields,
                            "key_val",
                            Outcome.OPERATIONAL,
                            "",
                            "PROPERTY_KEY_OF_AN_EMPTY_EXTENSION");
            }
        }
        var phones = new ArrayList<CdsExportDemographicMappingDto.Phone>();
        for (var phone : base.phones()) {
            String key = phone.type().equals("R") ? "hPhoneExt" : "wPhoneExt";
            var row = known.get(key);
            String extension = null;
            if (row != null) {
                String value = row.fields().get("value");
                if (value == null || value.isEmpty()) {
                    mark(
                            row,
                            fields,
                            "value",
                            Outcome.ABSENT,
                            path(key),
                            "ABSENT_NATIVE_PHONE_EXTENSION");
                    mark(
                            row,
                            fields,
                            "key_val",
                            Outcome.OPERATIONAL,
                            "",
                            "PROPERTY_KEY_OF_AN_ABSENT_EXTENSION");
                } else
                    try {
                        new CdsExportDemographicMappingDto.Phone(
                                phone.type(), phone.number(), value);
                        extension = value;
                        mark(
                                row,
                                fields,
                                "value",
                                Outcome.MAPPED,
                                path(key),
                                "EXACT_CURRENT_PHONE_EXTENSION");
                    } catch (IllegalArgumentException review) {
                        // The form's extension box has no length limit; an extension the CDS
                        // five-character token cannot hold travels exactly in the record.
                        carry(row, fields);
                    }
            }
            phones.add(
                    new CdsExportDemographicMappingDto.Phone(
                            phone.type(), phone.number(), extension));
        }
        for (String key : List.of("hPhoneExt", "wPhoneExt")) {
            var row = known.get(key);
            if (row == null) continue;
            if (base.phones().stream().noneMatch(phone -> phone.type().equals(KEYS.get(key)))) {
                String value = row.fields().get("value");
                // An extension left behind after its number was cleared has no PhoneNumber to
                // join; it travels in the record instead of being dropped.
                if (present(value)) carry(row, fields);
                else
                    mark(
                            row,
                            fields,
                            "value",
                            Outcome.ABSENT,
                            path(key),
                            "EXTENSION_WITHOUT_A_NATIVE_NUMBER");
                if (!present(value))
                    mark(
                            row,
                            fields,
                            "key_val",
                            Outcome.OPERATIONAL,
                            "",
                            "EMPTY_EXTENSION_WITHOUT_A_NATIVE_NUMBER");
            }
        }
        var mobile = known.get("demo_cell");
        if (mobile != null) {
            String value = mobile.fields().get("value");
            if (!present(value)) {
                mark(
                        mobile,
                        fields,
                        "value",
                        Outcome.ABSENT,
                        path("demo_cell"),
                        "EXPLICIT_ABSENT_MOBILE_NUMBER");
                mark(
                        mobile,
                        fields,
                        "key_val",
                        Outcome.OPERATIONAL,
                        "",
                        "PROPERTY_KEY_OF_AN_ABSENT_MOBILE_NUMBER");
            } else
                try {
                    phones.add(new CdsExportDemographicMappingDto.Phone("C", value, null));
                    mark(
                            mobile,
                            fields,
                            "value",
                            Outcome.MAPPED,
                            path("demo_cell"),
                            "EXACT_CURRENT_MOBILE_NUMBER");
                } catch (IllegalArgumentException review) {
                    // The mobile box has no length limit; a number the CDS 25-character token
                    // cannot hold travels exactly in the record.
                    carry(mobile, fields);
                }
        }
        new CdsExportNativeFieldAssessmentValidator()
                .validate(properties, fields, 1_000_000, checkpoint);
        checkpoint.run();
        return base.withPhones(phones, fields);
    }

    private static void requireRow(int patient, CdsExportCapturedTextRowDto row)
            throws IOException {
        if (row == null
                || !row.table().equals("demographicExt")
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_EXT
                                                .columns()))
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                || !row.exactNumbers().isEmpty()) scope();
        try {
            String id = row.fields().get("id");
            long value = Long.parseLong(id);
            if (!Long.toString(value).equals(id)
                    || !CdsExportPatientTableSourceReader.key(value).equals(row.recordKey()))
                scope();
        } catch (NumberFormatException | NullPointerException malformed) {
            scope();
        }
    }

    private static String path(String key) {
        return "PhoneNumber["
                + KEYS.get(key)
                + "]/"
                + (key.equals("demo_cell") ? "phoneNumber" : "extension");
    }

    private static Presence presence(String value) {
        return value == null
                ? Presence.SQL_NULL
                : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    /** Key and value of a property carried by the native demographic NewCategory record. */
    private static void carry(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        for (String column : List.of("key_val", "value"))
            fields.put(
                    new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column),
                    CdsExportDemographicSupportingMapper.carried(row.fields().get(column)));
    }

    /**
     * The populated columns of a property row the record carries whole; a NULL or empty one keeps
     * its presence, and the record states it as stored.
     */
    private static void carryPopulated(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String... columns) {
        for (String column : columns) {
            String value = row.fields().get(column);
            var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
            fields.put(
                    key,
                    present(value)
                            ? CdsExportDemographicSupportingMapper.carried(value)
                            : new CdsExportFieldAssessmentDto(
                                    presence(value),
                                    Outcome.ABSENT,
                                    "",
                                    "ABSENT_VALUE_OF_A_CARRIED_PROPERTY"));
        }
    }

    /**
     * Orders two rows of one phone key as the chart chooses between them, the greater being the
     * row it shows: the later {@code date_time} (captured as yyyy-MM-dd HH:mm:ss; none sorts
     * first), then the higher id.
     */
    static int compareShown(CdsExportCapturedTextRowDto first, CdsExportCapturedTextRowDto second) {
        String a = first.fields().get("date_time"), b = second.fields().get("date_time");
        int time = a == null ? (b == null ? 0 : -1) : b == null ? 1 : a.compareTo(b);
        if (time != 0) return time;
        return Long.compare(
                Long.parseLong(first.fields().get("id")), Long.parseLong(second.fields().get("id")));
    }

    private static void mark(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String column,
            Outcome outcome,
            String path,
            String rule) {
        fields.put(
                new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column),
                new CdsExportFieldAssessmentDto(
                        presence(row.fields().get(column)), outcome, path, rule));
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_SCOPE_INVALID");
    }
}
