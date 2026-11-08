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

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportEmailLexical;

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsHealthCardAuthority;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.Address;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.Assessment;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.HealthCard;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.Names;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.Phone;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.Presence;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps exact captured base-row values, without querying mutable clinical rows or applying
 * entity/UI defaults. Every column receives a disposition. Unsupported populated values
 * remain explicit blockers; they are never discarded or concatenated into clinical notes.
 */
public final class CdsExportDemographicMapper {
    private static final Map<String, String> TITLES = titles();

    public CdsExportDemographicMappingDto map(CdsExportDemographicRowDto row) {
        var state = new State(row.fields());
        String first = state.token("first_name", "Names/LegalName/FirstName/Part", 50, true);
        String last = state.token("last_name", "Names/LegalName/LastName/Part", 50, true);
        String middle = state.token("middleNames", "Names/LegalName/OtherName/Part", 50, false);
        String title = state.code("title", "Names/NamePrefix", TITLES);
        LocalDate birth = birth(state);
        String gender = state.code("sex", "Gender", Map.of("M", "M", "F", "F", "O", "O", "U", "U"));
        if (!state.present("sex")) state.review("sex", "Gender", "REQUIRED_VALUE_MISSING");
        var health = health(state);
        String chart = state.token("chart_no", "ChartNumber", 15, false);
        var addresses = new ArrayList<Address>();
        address(state, addresses, "M", "address", "city", "province", "postal");
        address(state, addresses, "R", "residentialAddress", "residentialCity", "residentialProvince", "residentialPostal");
        var phones = new ArrayList<Phone>();
        phone(state, phones, "R", "phone"); phone(state, phones, "W", "phone2");
        String official = state.code("official_lang", "PreferredOfficialLanguage",
                Map.of("English", "ENG", "French", "FRE", "ENG", "ENG", "FRE", "FRE"));
        // Native spoken_lang stores a display language. Only reviewed translations are used;
        // an arbitrary three-letter string is not evidence of membership in ISO 639-2.
        String spoken = state.code("spoken_lang", "PreferredSpokenLanguage",
                Map.of("English", "eng", "French", "fre", "eng", "eng", "fre", "fre", "fra", "fra"));
        String email = state.token("email", "Email", 50, false);
        if (email != null && !CdsExportEmailLexical.matches(email)) {
            state.review("email", "Email", "WIRE_EMAIL_REQUIRES_REVIEW"); email = null;
        }
        String status = state.value("patient_status"); boolean enumerated = true;
        if (!state.present("patient_status")) { state.review("patient_status", "PersonStatusCode", "REQUIRED_VALUE_MISSING"); status = null; }
        else {
            String mapped = Map.of("AC", "A", "IN", "I", "DE", "D").get(status);
            if (mapped != null) { status = mapped; state.mapped("patient_status", "PersonStatusCode/PersonStatusAsEnum", "NATIVE_STATUS_CODE"); }
            else { status = state.text("patient_status", "PersonStatusCode/PersonStatusAsPlainText", 50); enumerated = false; }
        }
        LocalDate statusDate = state.date("patient_status_date", "PersonStatusDate");
        String sin = state.token("sin", "SIN", 9, false);
        if (sin != null && sin.codePointCount(0, sin.length()) != 9) { state.review("sin", "SIN", "WIRE_LENGTH_REQUIRES_REVIEW"); sin = null; }
        // These columns are retained in the authenticated source capture, not asserted as
        // clinical history or substituted for the actual historical audit export.
        for (String column : Set.of("lastUpdateDate", "lastUpdateUser", "cds_identity_hin", "cds_identity_issuer")) {
            state.operational(column, "CAPTURED_OPERATIONAL_OR_DERIVED_IDENTITY");
        }
        return new CdsExportDemographicMappingDto(row.demographicNo(), new Names(title, first, last, middle), birth,
                health, chart, gender, addresses, phones, official, spoken, email, status, enumerated, statusDate, sin, state.assessments);
    }
    /** Exact captured primary-provider join; lookup matching never uses a name or collation-normalized ID. */
    public CdsExportDemographicMappingDto map(CdsExportDemographicRowDto row,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto provider) throws java.io.IOException {
        var base = map(row); String id = row.fields().get("provider_no");
        if (id == null || id.isEmpty()) {
            if (provider != null) throw new java.io.IOException("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_SCOPE_INVALID");
            return base;
        }
        if (provider == null || !id.equals(provider.fields().get("provider_no"))) {
            return base.withPrimaryPhysician(null, Map.of(), new Assessment(Presence.VALUE, Outcome.REQUIRES_REVIEW,
                    "PrimaryPhysician", "EXACT_PRIMARY_PHYSICIAN_REFERENCE_REQUIRED"));
        }
        var mapped = new CdsExportPublicProviderMapper().map(provider, CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
        return base.withPrimaryPhysician(mapped.value(), mapped.fields(), new Assessment(Presence.VALUE,
                mapped.representable() ? Outcome.MAPPED : Outcome.REQUIRES_REVIEW, "PrimaryPhysician",
                mapped.representable() ? "EXACT_CAPTURED_PUBLIC_PROVIDER" : "PUBLIC_PROVIDER_VALUES_REQUIRES_REVIEW"));
    }
    private static LocalDate birth(State state) {
        String[] columns = {"year_of_birth", "month_of_birth", "date_of_birth"};
        try {
            String year = state.value(columns[0]), month = state.value(columns[1]), day = state.value(columns[2]);
            if (year == null || !year.matches("[0-9]{4}") || month == null || !month.matches("[0-9]{1,2}")
                    || day == null || !day.matches("[0-9]{1,2}")) throw new DateTimeException("incomplete");
            LocalDate date = LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            if (date.getYear() < 1) throw new DateTimeException("range");
            for (String column : columns) state.mapped(column, "DateOfBirth", "EXACT_NATIVE_CALENDAR_DATE");
            return date;
        } catch (DateTimeException failure) {
            for (String column : columns) state.review(column, "DateOfBirth", "EXACT_BIRTH_DATE_REQUIRED");
            return null;
        }
    }
    private static HealthCard health(State state) {
        boolean any = Set.of("hin", "ver", "hc_type", "hc_renew_date").stream().anyMatch(state::present);
        if (!any) return null;
        String number = state.token("hin", "HealthCard/Number", 20, true);
        String version = state.token("ver", "HealthCard/Version", 2, false);
        String province = CdsHealthCardAuthority.wire(state.value("hc_type"));
        if (province != null) state.mapped("hc_type", "HealthCard/ProvinceCode", "PINNED_HEALTH_CARD_AUTHORITY");
        else state.review("hc_type", "HealthCard/ProvinceCode", "HEALTH_CARD_ISSUER_REQUIRES_REVIEW");
        LocalDate expiry = state.date("hc_renew_date", "HealthCard/Expirydate");
        return new HealthCard(number, version, expiry, province);
    }
    private static void address(State state, java.util.List<Address> output, String type,
            String streetColumn, String cityColumn, String provinceColumn, String postalColumn) {
        if (Set.of(streetColumn, cityColumn, provinceColumn, postalColumn).stream().noneMatch(state::present)) return;
        String path = "Address[" + type + "]/Structured/";
        String street = state.token(streetColumn, path + "Line1", 50, false);
        String city = state.token(cityColumn, path + "City", 80, false);
        String subdivision = subdivision(state, provinceColumn, path + "CountrySubdivisionCode");
        boolean zip = subdivision != null && subdivision.startsWith("US-");
        String postalPath = path + "PostalZipCode/" + (zip ? "ZipCode" : "PostalCode");
        String postal = state.token(postalColumn, postalPath, 10, false);
        if (postal != null && subdivision == null) {
            state.review(postalColumn, path + "PostalZipCode", "POSTAL_JURISDICTION_REQUIRES_REVIEW"); postal = null;
        }
        if (postal != null) {
            if (subdivision != null && subdivision.startsWith("CA-") && postal.matches("[A-Za-z][0-9][A-Za-z] [0-9][A-Za-z][0-9]")) {
                postal = postal.substring(0, 3) + postal.substring(4);
                state.mapped(postalColumn, postalPath, "CANADIAN_POSTAL_DISPLAY_SEPARATOR");
            }
            if (!postal.matches(zip ? "[0-9-]*" : "[a-zA-Z0-9-]*")) { state.review(postalColumn, postalPath, "POSTAL_REPRESENTATION_REQUIRES_REVIEW"); postal = null; }
        }
        output.add(new Address(type, street, city, subdivision, postal));
    }
    private static String subdivision(State state, String column, String path) {
        String value = state.value(column); if (!state.present(column)) return null;
        if (CdsHealthCardAuthority.CANADIAN.contains(value)) { state.mapped(column, path, "CANADIAN_SUBDIVISION_CODE"); return "CA-" + value; }
        if (value.startsWith("CA-") && CdsHealthCardAuthority.CANADIAN.contains(value.substring(3)) || CdsHealthCardAuthority.PINNED_US.contains(value)) {
            state.mapped(column, path, "EXACT_SUBDIVISION_CODE"); return value;
        }
        state.review(column, path, "SUBDIVISION_REQUIRES_REVIEW"); return null;
    }
    private static void phone(State state, java.util.List<Phone> phones, String type, String column) {
        String number = state.token(column, "PhoneNumber[" + type + "]/phoneNumber", 25, false);
        if (number != null) phones.add(new Phone(type, number));
    }
    private static Map<String, String> titles() {
        var result = new LinkedHashMap<String, String>();
        for (String title : Set.of("Bro", "Capt", "Chief", "Cst", "Corp", "Dr", "Fr", "Hon", "Lt", "Madam", "Mme", "Mlle", "Major", "Mayor", "Miss", "Mr", "Mssr", "Mrs", "Ms", "Prof", "Reeve", "Rev", "RtHon", "Sen", "Sgt", "Sr")) {
            result.put(title, title); result.put(title.toUpperCase(java.util.Locale.ROOT), title);
        }
        return Map.copyOf(result);
    }
    private static final class State {
        private final Map<String, String> values;
        private final Map<String, Assessment> assessments = new LinkedHashMap<>();
        private State(Map<String, String> values) {
            this.values = values;
            values.forEach((column, value) -> assessments.put(column, new Assessment(presence(value),
                    value == null || value.isEmpty() ? Outcome.ABSENT : Outcome.REQUIRES_REVIEW, "", "UNMAPPED_NATIVE_VALUE")));
        }
        private String value(String column) { return values.get(column); }
        private boolean present(String column) { String value = value(column); return value != null && !value.isEmpty(); }
        private static Presence presence(String value) { return value == null ? Presence.SQL_NULL : value.isEmpty() ? Presence.EMPTY : Presence.VALUE; }
        private void set(String column, Outcome outcome, String path, String rule) { assessments.put(column, new Assessment(presence(value(column)), outcome, path, rule)); }
        private void review(String column, String path, String rule) { set(column, Outcome.REQUIRES_REVIEW, path, rule); }
        private void mapped(String column, String path, String rule) { set(column, Outcome.MAPPED, path, rule); }
        private void operational(String column, String rule) { set(column, Outcome.OPERATIONAL, "", rule); }
        private String token(String column, String path, int maximum, boolean required) {
            if (!present(column)) { if (required) review(column, path, "REQUIRED_VALUE_MISSING"); return null; }
            String value = value(column);
            if (value.startsWith(" ") || value.endsWith(" ") || value.contains("  ") || value.indexOf('\t') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) { review(column, path, "TOKEN_WHITESPACE_REQUIRES_REVIEW"); return null; }
            return text(column, path, maximum);
        }
        private String text(String column, String path, int maximum) {
            String value = value(column); if (!present(column)) return null;
            if (value.codePointCount(0, value.length()) > maximum) { review(column, path, "WIRE_LENGTH_REQUIRES_REVIEW"); return null; }
            if (value.codePoints().anyMatch(code -> code < 0x20 && code != '\t' && code != '\n' && code != '\r'
                    || code >= 0xd800 && code <= 0xdfff || code == 0xfffe || code == 0xffff)) { review(column, path, "XML_CHARACTER_REQUIRES_REVIEW"); return null; }
            mapped(column, path, "EXACT_NATIVE_TEXT"); return value;
        }
        private String code(String column, String path, Map<String, String> mapping) {
            if (!present(column)) return null; String result = mapping.get(value(column));
            if (result == null) review(column, path, "CODE_REQUIRES_REVIEW"); else mapped(column, path, "REVIEWED_NATIVE_CODE");
            return result;
        }
        private LocalDate date(String column, String path) {
            if (!present(column)) return null;
            try {
                String value = value(column); if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException("lexical");
                LocalDate date = LocalDate.parse(value); if (date.getYear() < 1) throw new DateTimeException("range");
                mapped(column, path, "EXACT_NATIVE_CALENDAR_DATE"); return date;
            } catch (DateTimeException failure) { review(column, path, "EXACT_CALENDAR_DATE_REQUIRED"); return null; }
        }
    }
}
