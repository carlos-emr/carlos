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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsDemographicCarrierContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The exact Demographics values a CARLOS sender carries in its native demographic record, proven
 * against the elements it wrote.
 *
 * <p>A CARLOS export writes a Demographics element by the rules of {@link
 * CdsDemographicCarrierContract} whenever it cannot hold the stored value exactly (an empty or
 * over-long name, middle names longer than a part, an unlisted title or sex, a birth date that is
 * not a calendar date, a missing status, a health card without a usable number, a phone or
 * version code the element cannot hold), and carries the stored column. A column is restored only
 * when its element is exactly what those rules write from the carried value, and only when the
 * receiving column can store it; otherwise the element's value stands and the report names the
 * element ({@link #NOT_RESTORED}). The record's {@code demographicExt} rows are restored as
 * stored, except a key the patient already has a row for (CARLOS keeps one row per key) and a
 * flag CARLOS does not write.
 *
 * <p>The record used is the last one in the patient file: a CARLOS export writes the sending
 * chart's own record after any retained copy of an earlier sender's, and always writes one when it
 * re-exports such a copy, so an earlier sender's values are never taken for this chart's. A
 * provider number is never restored: it is local to the sender, and the physician is matched from
 * the PrimaryPhysician element.
 */
final class CdsDemographicCarriedValuesResolver {
    /** Reported once when a carried value was restored into the new chart. */
    static final String RESTORED = "CDS_DEMOGRAPHIC_CARRIED_VALUES_RESTORED";

    /** Reported for each carried value that does not prove itself or that no column can store. */
    static final String NOT_RESTORED = "CDS_DEMOGRAPHIC_CARRIED_VALUE_NOT_RESTORED";

    /** Reported when the sender's native demographic record cannot be read. */
    static final String NOT_APPLIED = "CDS_DEMOGRAPHIC_CARRIER_NOT_APPLIED";

    /** The {@code title} column is a varchar(10) (V1 baseline schema). */
    private static final int TITLE_LENGTH = 10;

    /** The {@code key_val} column of {@code demographicExt} is a varchar(64). */
    private static final int PROPERTY_KEY_LENGTH = 64;

    /** The base-row columns whose exact value the importer restores, with their element. */
    static final Map<String, String> COLUMNS = columns();

    /** The columns that also decide the patient's identity (planning and replay compare them). */
    static final Set<String> IDENTITY =
            Set.of(
                    "first_name",
                    "last_name",
                    "sex",
                    "patient_status",
                    "year_of_birth",
                    "month_of_birth",
                    "date_of_birth",
                    "hin",
                    "hc_type");

    private final CdsFieldParser fields;

    CdsDemographicCarriedValuesResolver(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    /** What the importer restores over the element projection of one new chart. */
    record Restored(Map<String, String> columns, List<CdsDemographicNativeDto.Property> properties) {
        static final Restored NONE = new Restored(Map.of(), List.of());

        Restored {
            columns = java.util.Collections.unmodifiableMap(new TreeMap<>(columns));
            properties = List.copyOf(properties);
        }

        @Override
        public String toString() {
            return "CdsDemographicCarriedValuesResolver.Restored[protected]";
        }
    }

    /**
     * The identity columns the record restores (names, sex, status, birth date, health card
     * number and issuer), so planning and a replay compare the chart the import writes.
     */
    Map<String, String> identity(CdsRecordDto record) throws IOException {
        var carried =
                new CdsDemographicResidualReader(fields).carried(record, IDENTITY);
        if (!carried.readable() || carried.columns().isEmpty()) return Map.of();
        var elements = elements(record);
        var result = new TreeMap<String, String>();
        decide(carried.columns(), elements, null, result, ignored -> {});
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * The columns and rows to restore over the element projection of a new chart.
     *
     * @param middleNames the middle names the importer projects from the OtherName parts, or
     *     null when it projects none
     * @param phoneKeys the {@code demographicExt} keys the importer writes from the elements
     */
    Restored resolve(
            CdsRecordDto record,
            String middleNames,
            Set<String> phoneKeys,
            Consumer<CdsClinicalDiagnosticDto> notice)
            throws IOException {
        var carried =
                new CdsDemographicResidualReader(fields).carried(record, COLUMNS.keySet());
        if (!carried.readable()) {
            notice.accept(new CdsClinicalDiagnosticDto(NOT_APPLIED, "NewCategory"));
            return Restored.NONE;
        }
        if (carried.columns().isEmpty()
                && carried.properties() != null
                && carried.properties().isEmpty()) return Restored.NONE;
        var elements = elements(record);
        var columns = new TreeMap<String, String>();
        decide(carried.columns(), elements, middleNames, columns, notice);
        var properties = new ArrayList<CdsDemographicNativeDto.Property>();
        if (carried.properties() == null)
            notice.accept(new CdsClinicalDiagnosticDto(NOT_RESTORED, "NewCategory"));
        else {
            var keys = new HashSet<>(phoneKeys);
            for (var property : carried.properties()) {
                String key = property.key();
                boolean hidden = "1".equals(property.hidden());
                // CARLOS keeps one row per key (uk_demo_ext_single_value, V1.0.54) except the
                // dashboard's exclusion history.
                boolean repeated =
                        key != null && !"excludeIndicator".equals(key) && !keys.add(key);
                if (!hidden && !"0".equals(property.hidden())
                        || repeated
                        || key != null
                                && (!CdsNativeExtensionContract.storable(key)
                                        || key.codePointCount(0, key.length())
                                                > PROPERTY_KEY_LENGTH)
                        || property.value() != null
                                && !CdsNativeExtensionContract.storable(property.value()))
                    notice.accept(new CdsClinicalDiagnosticDto(NOT_RESTORED, "NewCategory"));
                else
                    properties.add(
                            new CdsDemographicNativeDto.Property(key, property.value(), hidden));
            }
        }
        if (!columns.isEmpty() || !properties.isEmpty())
            notice.accept(new CdsClinicalDiagnosticDto(RESTORED, "Demographics"));
        return new Restored(columns, properties);
    }

    /** The element values the proofs compare, read from the Demographics record. */
    private record Elements(
            String first,
            String last,
            String title,
            String birth,
            String gender,
            String statusEnum,
            String statusText,
            boolean card,
            boolean version,
            boolean expiry,
            boolean statusDate,
            Set<String> phones) {}

    private Elements elements(CdsRecordDto record) throws IOException {
        var phones = new HashSet<String>();
        var phoneTypes = new LinkedHashMap<String, String>();
        try {
            fields.forEachField(
                    record,
                    field -> {
                        String path = field.path();
                        if (!path.startsWith("PhoneNumber[")) return;
                        int end = path.indexOf(']');
                        String relative = CdsFieldParser.unindexed(path.substring(end + 1));
                        if (!relative.equals("/@phoneNumberType")) return;
                        try {
                            phoneTypes.put(
                                    path.substring(0, end + 1), fields.readToken(record, field, 1));
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        phones.addAll(phoneTypes.values());
        return new Elements(
                text(record, "Names/LegalName/FirstName/Part", 50),
                text(record, "Names/LegalName/LastName/Part", 50),
                text(record, "Names/NamePrefix", 20),
                text(record, "DateOfBirth", 64),
                text(record, "Gender", 2),
                text(record, "PersonStatusCode/PersonStatusAsEnum", 1),
                text(record, "PersonStatusCode/PersonStatusAsPlainText", 50),
                text(record, "HealthCard/Number", 20) != null,
                text(record, "HealthCard/Version", 3) != null,
                text(record, "HealthCard/Expirydate", 128) != null,
                text(record, "PersonStatusDate", 128) != null,
                Set.copyOf(phones));
    }

    private String text(CdsRecordDto record, String path, int maximum) throws IOException {
        try {
            return fields.firstText(record, path, maximum);
        } catch (io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException
                capacity) {
            if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(capacity.code())) throw capacity;
            return "\u0000"; // Longer than any element the exporter writes: proves nothing.
        }
    }

    /**
     * Restores each carried column whose element is exactly what the export rules write from it
     * and whose receiving column can store it.
     */
    private static void decide(
            Map<String, String> carried,
            Elements elements,
            String middleNames,
            Map<String, String> result,
            Consumer<CdsClinicalDiagnosticDto> notice) {
        for (var entry : carried.entrySet()) {
            String column = entry.getKey(), value = entry.getValue();
            if (!COLUMNS.containsKey(column)) continue;
            if (column.endsWith("_of_birth")) continue;
            boolean proven =
                    switch (column) {
                        case "first_name" ->
                                value != null
                                        && nonNull(elements.first)
                                                .equals(CdsDemographicCarrierContract.namePart(value));
                        case "last_name" ->
                                value != null
                                        && nonNull(elements.last)
                                                .equals(CdsDemographicCarrierContract.namePart(value));
                        case "middleNames" -> {
                            var parts = CdsDemographicCarrierContract.middleNameParts(value);
                            yield Objects.equals(
                                    middleNames, parts.isEmpty() ? null : String.join(" ", parts));
                        }
                        case "title" ->
                                Objects.equals(
                                        elements.title, CdsDemographicCarrierContract.title(value));
                        case "sex" ->
                                value != null
                                        && CdsDemographicCarrierContract.gender(value)
                                                .equals(elements.gender);
                        case "patient_status" -> {
                            var status = CdsDemographicCarrierContract.personStatus(value);
                            yield status.enumerated()
                                    ? status.text().equals(elements.statusEnum)
                                    : elements.statusEnum == null
                                            && status.text().equals(nonNull(elements.statusText));
                        }
                        // The exporter left the whole card out; a written card keeps its number.
                        case "hin", "hc_type" -> !elements.card;
                        case "ver" -> !elements.card || !elements.version;
                        case "hc_renew_date" -> !elements.card || !elements.expiry;
                        case "patient_status_date" -> !elements.statusDate;
                        case "phone" -> !elements.phones.contains("R");
                        case "phone2" -> !elements.phones.contains("W");
                        default -> false;
                    };
            if (proven && storable(column, value)) result.put(column, value);
            else notice.accept(new CdsClinicalDiagnosticDto(NOT_RESTORED, COLUMNS.get(column)));
        }
        birth(carried, elements, result, notice);
    }

    /**
     * The three birth columns: restored together from what the record carries, each column it
     * does not carry taken as the importer writes it from the element, when they give the element
     * (or, for columns that state no calendar date, when the element is 0001-01-01).
     */
    private static void birth(
            Map<String, String> carried,
            Elements elements,
            Map<String, String> result,
            Consumer<CdsClinicalDiagnosticDto> notice) {
        String[] columns = {"year_of_birth", "month_of_birth", "date_of_birth"};
        boolean any = false;
        for (String column : columns) any |= carried.containsKey(column);
        if (!any) return;
        LocalDate element;
        try {
            element = elements.birth == null ? null : LocalDate.parse(elements.birth.strip());
        } catch (DateTimeException invalid) {
            element = null;
        }
        if (element == null) {
            notice.accept(new CdsClinicalDiagnosticDto(NOT_RESTORED, "DateOfBirth"));
            return;
        }
        var written = CdsDemographicCarrierContract.birthColumns(element);
        var values = new String[3];
        for (int i = 0; i < 3; i++)
            values[i] = carried.containsKey(columns[i]) ? carried.get(columns[i]) : written.get(i);
        LocalDate stated = CdsDemographicCarrierContract.birthDate(values[0], values[1], values[2]);
        boolean proven =
                stated == null
                        ? element.toString().equals(CdsDemographicCarrierContract.NO_BIRTH_DATE)
                        : stated.equals(element);
        for (int i = 0; i < 3; i++)
            proven &= values[i] == null || storable(columns[i], values[i]);
        if (!proven) {
            notice.accept(new CdsClinicalDiagnosticDto(NOT_RESTORED, "DateOfBirth"));
            return;
        }
        for (int i = 0; i < 3; i++)
            if (carried.containsKey(columns[i])) result.put(columns[i], values[i]);
    }

    /** Whether the receiving column can store the carried value exactly. */
    static boolean storable(String column, String value) {
        if (value == null)
            // first_name, last_name and sex are NOT NULL, and the patient record writes a NULL
            // status or phone as empty text; the other restored columns keep SQL NULL.
            return !Set.of("first_name", "last_name", "sex", "patient_status", "phone", "phone2")
                    .contains(column);
        if (!CdsNativeExtensionContract.storable(value)) return false;
        // The patient record trims the birth columns, so only a trimmed value is kept exactly.
        if (column.endsWith("_of_birth")
                && !value.equals(org.apache.commons.lang3.StringUtils.trimToNull(value)))
            return false;
        int length = value.codePointCount(0, value.length());
        return switch (column) {
            case "first_name" -> length <= Demographic.FIRST_NAME_MAX_LENGTH;
            case "last_name" -> length <= Demographic.LAST_NAME_MAX_LENGTH;
            case "middleNames" -> length <= Demographic.MIDDLE_NAMES_MAX_LENGTH;
            case "title" -> length <= TITLE_LENGTH;
            case "sex" -> length <= Demographic.SEX_MAX_LENGTH;
            case "patient_status" -> length <= Demographic.PATIENT_STATUS_MAX_LENGTH;
            case "year_of_birth" -> length <= Demographic.YEAR_OF_BIRTH_MAX_LENGTH;
            case "month_of_birth" -> length <= Demographic.MONTH_OF_BIRTH_MAX_LENGTH;
            case "date_of_birth" -> length <= Demographic.DATE_OF_BIRTH_MAX_LENGTH;
            case "hin" -> length <= Demographic.HIN_MAX_LENGTH;
            case "ver" -> length <= Demographic.VER_MAX_LENGTH;
            case "hc_type" -> length <= Demographic.HC_TYPE_MAX_LENGTH;
            case "phone", "phone2" -> length <= Demographic.PHONE_MAX_LENGTH;
            // DATE columns: only a calendar date can be stored through the patient record (a
            // zero date cannot be written back).
            case "hc_renew_date", "patient_status_date" -> date(value);
            default -> false;
        };
    }

    private static boolean date(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        try {
            return LocalDate.parse(value).getYear() >= 1;
        } catch (DateTimeException invalid) {
            return false;
        }
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> columns() {
        var result = new LinkedHashMap<String, String>();
        result.put("first_name", "Names/LegalName/FirstName");
        result.put("last_name", "Names/LegalName/LastName");
        result.put("middleNames", "Names/LegalName/OtherName");
        result.put("title", "Names/NamePrefix");
        result.put("year_of_birth", "DateOfBirth");
        result.put("month_of_birth", "DateOfBirth");
        result.put("date_of_birth", "DateOfBirth");
        result.put("sex", "Gender");
        result.put("patient_status", "PersonStatusCode");
        result.put("patient_status_date", "PersonStatusDate");
        result.put("hin", "HealthCard");
        result.put("ver", "HealthCard");
        result.put("hc_renew_date", "HealthCard");
        result.put("hc_type", "HealthCard");
        result.put("phone", "PhoneNumber");
        result.put("phone2", "PhoneNumber");
        return java.util.Collections.unmodifiableMap(result);
    }
}
