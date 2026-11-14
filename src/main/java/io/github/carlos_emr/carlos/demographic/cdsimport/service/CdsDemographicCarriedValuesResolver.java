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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsHealthCardAuthority;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
 * version code the element cannot hold, an optional chart number, e-mail, SIN, address part or
 * language the element leaves out or holds in another form), and carries the stored column. A
 * column is restored only
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

    /** The {@code official_lang} and {@code spoken_lang} columns are varchar(60). */
    private static final int LANGUAGE_LENGTH = 60;

    /** The {@code key_val} column of {@code demographicExt} is a varchar(64). */
    private static final int PROPERTY_KEY_LENGTH = 64;

    /** Address columns by their element part, mailing (type M) and residential (type R). */
    private static final Map<String, String> ADDRESS = address();

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
            String card,
            String version,
            String issuer,
            boolean expiry,
            boolean statusDate,
            Map<String, String> phones,
            String chart,
            String email,
            String sin,
            String official,
            String spoken,
            Map<String, String> address) {}

    private Elements elements(CdsRecordDto record) throws IOException {
        var phoneTypes = new LinkedHashMap<String, String>();
        var phoneNumbers = new HashMap<String, String>();
        var addressTypes = new LinkedHashMap<String, String>();
        var addressParts = new LinkedHashMap<String, Map<String, String>>();
        try {
            fields.forEachField(
                    record,
                    field -> {
                        String path = field.path();
                        boolean phone = path.startsWith("PhoneNumber[");
                        if (!phone && !path.startsWith("Address[")) return;
                        int end = path.indexOf(']');
                        String group = path.substring(0, end + 1);
                        String relative = CdsFieldParser.unindexed(path.substring(end + 1));
                        try {
                            if (phone && relative.equals("/@phoneNumberType"))
                                phoneTypes.put(group, fields.readToken(record, field, 1));
                            else if (phone
                                    && relative.equals("/phoneNumber")
                                    && field.kind().equals("TEXT"))
                                phoneNumbers.put(group, token(record, field, 25));
                            else if (!phone && relative.equals("/@addressType"))
                                addressTypes.put(group, fields.readToken(record, field, 1));
                            else if (!phone && field.kind().equals("TEXT")) {
                                String part =
                                        switch (relative) {
                                            case "/Structured/Line1" -> "Line1";
                                            // The importer joins these into the street line;
                                            // the exporter writes none of them.
                                            case "/Formatted",
                                                    "/Structured/Line2",
                                                    "/Structured/Line3" -> "Other";
                                            case "/Structured/City" -> "City";
                                            case "/Structured/CountrySubdivisionCode" ->
                                                    "CountrySubdivisionCode";
                                            case "/Structured/PostalZipCode/PostalCode",
                                                    "/Structured/PostalZipCode/ZipCode" -> "Postal";
                                            default -> null;
                                        };
                                if (part != null)
                                    addressParts
                                            .computeIfAbsent(group, ignored -> new LinkedHashMap<>())
                                            .put(part, token(record, field, 128));
                            }
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        // By type, the phone's phoneNumber. The exporter writes one phone of a type with a
        // phoneNumber; anything else (a second phone of the type, a number in parts) is UNREAD.
        var phones = new LinkedHashMap<String, String>();
        phoneTypes.forEach(
                (group, type) ->
                        phones.put(
                                type,
                                phones.containsKey(type)
                                        ? UNREAD
                                        : phoneNumbers.getOrDefault(group, UNREAD)));
        // By "M/Line1" and the like, the address part. The exporter writes one structured
        // address of a type; anything else (a second address of the type, a street the importer
        // composes from other lines) is UNREAD.
        var address = new LinkedHashMap<String, String>();
        var types = new HashSet<String>();
        addressTypes.forEach(
                (group, type) -> {
                    var parts = addressParts.getOrDefault(group, Map.of());
                    boolean repeated = !types.add(type);
                    for (String part : List.of("Line1", "City", "CountrySubdivisionCode", "Postal")) {
                        String value =
                                repeated || part.equals("Line1") && parts.containsKey("Other")
                                        ? UNREAD
                                        : parts.get(part);
                        if (value != null) address.put(type + "/" + part, value);
                    }
                });
        return new Elements(
                text(record, "Names/LegalName/FirstName/Part", 50),
                text(record, "Names/LegalName/LastName/Part", 50),
                text(record, "Names/NamePrefix", 20),
                text(record, "DateOfBirth", 64),
                text(record, "Gender", 2),
                text(record, "PersonStatusCode/PersonStatusAsEnum", 1),
                text(record, "PersonStatusCode/PersonStatusAsPlainText", 50),
                text(record, "HealthCard/Number", 20),
                text(record, "HealthCard/Version", 3),
                text(record, "HealthCard/ProvinceCode", 20),
                text(record, "HealthCard/Expirydate", 128) != null,
                text(record, "PersonStatusDate", 128) != null,
                java.util.Collections.unmodifiableMap(phones),
                text(record, "ChartNumber", 15),
                text(record, "Email", 100),
                text(record, "SIN", 15),
                text(record, "PreferredOfficialLanguage", 3),
                text(record, "PreferredSpokenLanguage", 25),
                java.util.Collections.unmodifiableMap(address));
    }

    /** An element value longer than any the exporter writes: it proves nothing. */
    private static final String UNREAD = "\u0000";

    private String text(CdsRecordDto record, String path, int maximum) throws IOException {
        try {
            return fields.firstText(record, path, maximum);
        } catch (io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException
                capacity) {
            if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(capacity.code())) throw capacity;
            return UNREAD;
        }
    }

    private String token(CdsRecordDto record, CdsFieldDto field, int maximum) throws IOException {
        try {
            return fields.readToken(record, field, maximum);
        } catch (io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException
                capacity) {
            if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(capacity.code())) throw capacity;
            return UNREAD;
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
                        // The exporter left the whole card out, or wrote the carried value.
                        case "hin" -> written(elements.card, value);
                        case "hc_type" ->
                                elements.card == null
                                        || value != null
                                                && Objects.equals(
                                                        elements.issuer,
                                                        CdsHealthCardAuthority.wire(value));
                        case "ver" -> elements.card == null || written(elements.version, value);
                        case "hc_renew_date" -> elements.card == null || !elements.expiry;
                        case "patient_status_date" -> !elements.statusDate;
                        case "phone" -> written(elements.phones.get("R"), value);
                        case "phone2" -> written(elements.phones.get("W"), value);
                        // Left out, or written as its xs:token form.
                        case "chart_no" -> written(elements.chart, value);
                        case "email" -> written(elements.email, value);
                        case "sin" ->
                                elements.sin == null
                                        || value != null
                                                && elements.sin.equals(
                                                        value.replace(" ", "").replace("-", ""));
                        // Left out (unlisted), or a stored code written as itself.
                        case "official_lang" -> written(elements.official, value);
                        case "spoken_lang" -> written(elements.spoken, value);
                        case "address", "city", "residentialAddress", "residentialCity" ->
                                written(elements.address.get(ADDRESS.get(column)), value);
                        case "province", "residentialProvince" -> {
                            // Left out, or a CA- code written as stored.
                            String element = elements.address.get(ADDRESS.get(column));
                            yield element == null || element.equals(value);
                        }
                        case "postal", "residentialPostal" -> {
                            String element = elements.address.get(ADDRESS.get(column));
                            yield element == null
                                    || value != null && element.equals(postalElement(value));
                        }
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

    /** An optional element the exporter left out, or wrote as the value's xs:token form. */
    private static boolean written(String element, String value) {
        return element == null
                || value != null && element.equals(CdsDemographicCarrierContract.token(value));
    }

    /** A postal code as PostalCode holds it: a Canadian code without its display space. */
    private static String postalElement(String value) {
        String token = CdsDemographicCarrierContract.token(value);
        return token.matches("[A-Za-z][0-9][A-Za-z] [0-9][A-Za-z][0-9]")
                ? token.substring(0, 3) + token.substring(4)
                : token;
    }

    /** Whether the receiving column can store the carried value exactly. */
    // FindSecBugs IMPROPER_UNICODE: Compares a carried province with the trimmed upper-case form the patient record itself stores, to decide whether the column can hold it exactly; a value kept or reported as not restored either way, not a trust decision.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "compares a carried province with the trimmed upper-case form the patient record itself stores, to decide whether the column can hold it exactly; a value kept or reported as not restored either way, not a trust decision")
    static boolean storable(String column, String value) {
        if (value == null)
            // first_name, last_name and sex are NOT NULL, and the patient record writes a NULL
            // status or phone as empty text; the other restored columns keep SQL NULL.
            return !Set.of(
                            "first_name", "last_name", "sex", "patient_status", "phone", "phone2",
                            "chart_no")
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
            case "chart_no" -> length <= Demographic.CHART_NO_MAX_LENGTH;
            case "email" -> length <= Demographic.EMAIL_MAX_LENGTH;
            case "sin" -> length <= Demographic.SIN_MAX_LENGTH;
            // official_lang and spoken_lang are varchar(60) (V1 baseline schema).
            case "official_lang", "spoken_lang" -> length <= LANGUAGE_LENGTH;
            case "address" -> length <= Demographic.ADDRESS_MAX_LENGTH;
            case "residentialAddress" -> length <= Demographic.RESIDENTIAL_ADDRESS_MAX_LENGTH;
            case "city" -> length <= Demographic.CITY_MAX_LENGTH;
            case "residentialCity" -> length <= Demographic.RESIDENTIAL_CITY_MAX_LENGTH;
            case "postal" -> length <= Demographic.POSTAL_MAX_LENGTH;
            case "residentialPostal" -> length <= Demographic.RESIDENTIAL_POSTAL_MAX_LENGTH;
            // The patient record trims and capitalises the mailing province (empty is NULL).
            case "province" ->
                    length > 0
                            && length <= Demographic.PROVINCE_MAX_LENGTH
                            && value.equals(
                                    org.apache.commons.lang3.StringUtils.trimToEmpty(value)
                                            .toUpperCase(java.util.Locale.ROOT));
            case "residentialProvince" -> length <= Demographic.RESIDENTIAL_PROVINCE_MAX_LENGTH;
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

    private static Map<String, String> address() {
        var result = new LinkedHashMap<String, String>();
        result.put("address", "M/Line1");
        result.put("city", "M/City");
        result.put("province", "M/CountrySubdivisionCode");
        result.put("postal", "M/Postal");
        result.put("residentialAddress", "R/Line1");
        result.put("residentialCity", "R/City");
        result.put("residentialProvince", "R/CountrySubdivisionCode");
        result.put("residentialPostal", "R/Postal");
        return java.util.Collections.unmodifiableMap(result);
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
        result.put("chart_no", "ChartNumber");
        result.put("email", "Email");
        result.put("sin", "SIN");
        result.put("official_lang", "PreferredOfficialLanguage");
        result.put("spoken_lang", "PreferredSpokenLanguage");
        for (String column : ADDRESS.keySet()) result.put(column, "Address");
        return java.util.Collections.unmodifiableMap(result);
    }
}
