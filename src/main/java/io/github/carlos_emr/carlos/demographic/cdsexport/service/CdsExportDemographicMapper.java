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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsDemographicCarrierContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsHealthCardAuthority;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps exact captured base-row values, without querying mutable clinical rows or applying
 * entity/UI defaults. Every column receives a disposition, and no value holds the chart.
 *
 * <p>A value its element cannot hold exactly is decided by a documented rule
 * ({@link CdsDemographicCarrierContract}): a required element (a legal name part, DateOfBirth,
 * Gender, PersonStatusCode) gets the value the rule gives, an optional element is left out, and
 * the exact stored column travels in the native demographic NewCategory record
 * ({@link CdsExportDemographicSupportingMapper}), where the CARLOS importer restores it. Nothing
 * is discarded or concatenated into clinical notes.
 */
public final class CdsExportDemographicMapper {
    /** Rule of a missing or blank legal name part, written as the empty Part. */
    static final String MISSING_NAME = "MISSING_NAME_WRITTEN_AS_AN_EMPTY_PART_EXACT_VALUE_CARRIED";

    /** Rule of a name longer than a name part, written as its first 50 characters. */
    static final String CUT_NAME = "NAME_CUT_TO_THE_PART_LENGTH_EXACT_VALUE_CARRIED";

    /** Rule of untrimmed or doubled white space in a token, written collapsed. */
    static final String COLLAPSED = "XS_TOKEN_WHITESPACE_COLLAPSED_EXACT_VALUE_CARRIED";

    /** Rule of a stored sex no Administrative Gender code holds, written as U. */
    static final String UNKNOWN_SEX = "UNKNOWN_OR_MISSING_SEX_WRITTEN_AS_U_EXACT_VALUE_CARRIED";

    /** Rule of stored birth columns that are not one calendar date, written as 0001-01-01. */
    static final String NO_BIRTH_DATE =
            "BIRTH_DATE_NOT_A_CALENDAR_DATE_WRITTEN_AS_0001_01_01_EXACT_VALUE_CARRIED";

    /** Rule of a birth column stored without its leading zero, written in full. */
    static final String BIRTH_COLUMN_IN_FULL = "BIRTH_DATE_WRITTEN_IN_FULL_EXACT_VALUE_CARRIED";

    /** Rule of a missing patient status, written as an empty PersonStatusAsPlainText. */
    static final String MISSING_STATUS =
            "MISSING_PATIENT_STATUS_WRITTEN_AS_EMPTY_PLAIN_TEXT_EXACT_VALUE_CARRIED";

    public CdsExportDemographicMappingDto map(CdsExportDemographicRowDto row) {
        var state = new State(row.fields());
        String first = name(state, "first_name", "Names/LegalName/FirstName/Part");
        String last = name(state, "last_name", "Names/LegalName/LastName/Part");
        String middle = middleNames(state);
        String title = title(state);
        LocalDate birth = birth(state);
        String gender = gender(state);
        var health = health(state);
        String chart = state.optionalToken("chart_no", "ChartNumber", 15);
        var addresses = new ArrayList<Address>();
        address(state, addresses, "M", "address", "city", "province", "postal");
        address(
                state,
                addresses,
                "R",
                "residentialAddress",
                "residentialCity",
                "residentialProvince",
                "residentialPostal");
        var phones = new ArrayList<Phone>();
        phone(state, phones, "R", "phone");
        phone(state, phones, "W", "phone2");
        String official =
                state.optionalCode(
                        "official_lang",
                        "PreferredOfficialLanguage",
                        Map.of("English", "ENG", "French", "FRE", "ENG", "ENG", "FRE", "FRE"));
        String spoken = spoken(state);
        String email = state.optionalToken("email", "Email", 50);
        // The forms' e-mail box is free text (several addresses, "+" tags, apostrophes, up to 100
        // characters); a value the CDS e-mail pattern cannot hold travels exactly in the record.
        if (email != null && !CdsExportEmailLexical.matches(email)) {
            state.carry("email");
            email = null;
        }
        var status = CdsDemographicCarrierContract.personStatus(state.value("patient_status"));
        status(state, status);
        LocalDate statusDate = state.date("patient_status_date", "PersonStatusDate");
        String sin = sin(state);
        // These columns are retained in the authenticated source capture, not asserted as
        // clinical history or substituted for the actual historical audit export.
        for (String column :
                Set.of(
                        "lastUpdateDate",
                        "lastUpdateUser",
                        "cds_identity_hin",
                        "cds_identity_issuer")) {
            state.operational(column, "CAPTURED_OPERATIONAL_OR_DERIVED_IDENTITY");
        }
        return new CdsExportDemographicMappingDto(
                row.demographicNo(),
                new Names(title, first, last, middle),
                birth,
                health,
                chart,
                gender,
                addresses,
                phones,
                official,
                spoken,
                email,
                status.text(),
                status.enumerated(),
                statusDate,
                sin,
                state.assessments);
    }

    /**
     * Exact captured primary-provider join; lookup matching never uses a name or
     * collation-normalized ID.
     *
     * <p>A stored number that names no captured provider record exactly (a number the REST API
     * wrote, or one that differs from the record's only by letter case or trailing space) is
     * exported without PrimaryPhysician, as a prescription is without its prescriber; the
     * stored number travels in the native demographic record. A provider number is local to
     * the sending CARLOS, so a CARLOS importer never restores it: it matches the physician from
     * the PrimaryPhysician element, as for any sender.
     */
    public CdsExportDemographicMappingDto map(
            CdsExportDemographicRowDto row,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto
                    provider)
            throws java.io.IOException {
        var base = map(row);
        String id = row.fields().get("provider_no");
        if (id == null || id.isEmpty()) {
            if (provider != null)
                throw new java.io.IOException("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_SCOPE_INVALID");
            return base;
        }
        if (provider == null || !id.equals(provider.fields().get("provider_no"))) {
            return base.withPrimaryPhysician(
                    null,
                    Map.of(),
                    new Assessment(
                            Presence.VALUE,
                            Outcome.MAPPED,
                            CdsExportDemographicSupportingMapper.DESTINATION,
                            PRIMARY_PHYSICIAN_WITHOUT_A_PROVIDER_RECORD));
        }
        var mapped =
                new CdsExportPublicProviderMapper()
                        .map(provider, CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
        // Provider first_name and last_name are varchar(30) NOT NULL (V1 baseline), so a
        // captured provider row always forms a CDS name: PUBLIC_PROVIDER_VALUES_REQUIRES_REVIEW
        // is kept for a row that no CARLOS schema can store.
        return base.withPrimaryPhysician(
                mapped.value(),
                mapped.fields(),
                new Assessment(
                        Presence.VALUE,
                        mapped.representable() ? Outcome.MAPPED : Outcome.REQUIRES_REVIEW,
                        "PrimaryPhysician",
                        mapped.representable()
                                ? "EXACT_CAPTURED_PUBLIC_PROVIDER"
                                : "PUBLIC_PROVIDER_VALUES_REQUIRES_REVIEW"));
    }

    /** Rule of a primary-physician number that names no captured provider record. */
    static final String PRIMARY_PHYSICIAN_WITHOUT_A_PROVIDER_RECORD =
            "PRIMARY_PHYSICIAN_NUMBER_WITHOUT_A_PROVIDER_RECORD_CARRIED";

    /**
     * The SIN box is free text (15 characters): "123 456 789" and "123-456-789" are its ordinary
     * forms. Nine digits after removing spaces and hyphens are the SIN; the exact typed value also
     * travels in the native demographic record. Anything else is not a SIN the element can hold
     * and travels only in the record.
     */
    private static String sin(State state) {
        if (!state.present("sin")) return null;
        String value = state.value("sin");
        if (value.matches("[0-9]{9}")) {
            state.mapped("sin", "SIN", "EXACT_NATIVE_TEXT");
            return value;
        }
        String digits = value.replace(" ", "").replace("-", "");
        if (digits.matches("[0-9]{9}")) {
            state.alsoCarried("sin", "SIN", "SIN_SEPARATORS_REMOVED_EXACT_VALUE_CARRIED");
            return digits;
        }
        state.carry("sin");
        return null;
    }

    /**
     * A required legal name part (FirstName, LastName). The demographic forms require both names,
     * but the REST demographic service and the PHS/STAR registration feed save what they are
     * sent, an empty name included. The Part is {@link CdsDemographicCarrierContract#namePart};
     * whenever it is not the stored name exactly (a missing or blank name, untrimmed or doubled
     * white space, a character XML cannot carry), the stored column is carried.
     */
    private static String name(State state, String column, String path) {
        String stored = state.value(column);
        String part = CdsDemographicCarrierContract.namePart(stored);
        if (!part.isEmpty() && part.equals(stored)) {
            state.mapped(column, path, "EXACT_NATIVE_TEXT");
            return part;
        }
        String token = stored == null ? "" : CdsDemographicCarrierContract.token(stored);
        String shown = CdsNativeExtensionContract.xmlElementText(token);
        state.alsoCarried(
                column,
                path,
                token.isEmpty()
                        ? MISSING_NAME
                        : !shown.equals(part)
                                ? CUT_NAME
                                : !shown.equals(token)
                                        ? CdsExportNativeFieldAssessments.REPLACED
                                        : COLLAPSED);
        return part;
    }

    /**
     * Middle names are OtherName parts of type GIV ({@link
     * CdsDemographicCarrierContract#middleNameParts}). The form and the REST service accept 100
     * characters and a part holds 50: longer middle names are written as their space-separated
     * names, one part each, which the CARLOS importer joins with single spaces, and a single name
     * longer than a part is written as its first 50 characters. Nothing is appended inside a
     * name. Whenever the joined parts are not the stored column exactly, the column is carried
     * and the CARLOS importer restores it. Blank middle names have no part and are carried.
     *
     * @return the middle names as the element shows them, or null when no part is written
     */
    private static String middleNames(State state) {
        String path = "Names/LegalName/OtherName/Part";
        if (!state.present("middleNames")) return null;
        String stored = state.value("middleNames");
        var parts = CdsDemographicCarrierContract.middleNameParts(stored);
        if (parts.isEmpty()) {
            state.carry("middleNames");
            return null;
        }
        String token = CdsDemographicCarrierContract.token(stored);
        String shown = CdsNativeExtensionContract.xmlElementText(token);
        if (String.join(" ", parts).equals(stored))
            state.mapped(
                    "middleNames",
                    path,
                    parts.size() == 1
                            ? "EXACT_NATIVE_TEXT"
                            : "SPACE_SEPARATED_MIDDLE_NAMES_AS_ORDERED_PARTS");
        else
            state.alsoCarried(
                    "middleNames",
                    path,
                    !String.join(" ", parts).equals(shown)
                            ? "MIDDLE_NAME_CUT_TO_THE_PART_LENGTH_EXACT_VALUE_CARRIED"
                            : !shown.equals(token)
                                    ? CdsExportNativeFieldAssessments.REPLACED
                                    : COLLAPSED);
        return shown;
    }

    /** The OtherName parts that carry a mapped middleNames value; see {@link #middleNames}. */
    static java.util.List<String> middleNameParts(String middle) {
        return CdsDemographicCarrierContract.middleNameParts(middle);
    }

    /**
     * NamePrefix is optional and coded. The add form stores the codes in capitals (DR, MRS,
     * RT_HON); the element holds the schema's spelling and the stored code is carried, so a
     * CARLOS import keeps the form's code. A title outside the list (the REST service accepts any
     * text) is left out and carried.
     */
    private static String title(State state) {
        if (!state.present("title")) return null;
        String stored = state.value("title");
        String code = CdsDemographicCarrierContract.title(stored);
        if (code == null) state.carry("title");
        else if (code.equals(stored)) state.mapped("title", "Names/NamePrefix", "REVIEWED_NATIVE_CODE");
        else
            state.alsoCarried(
                    "title", "Names/NamePrefix", "REVIEWED_NATIVE_CODE_EXACT_VALUE_CARRIED");
        return code;
    }

    /**
     * Gender is required ({@link CdsDemographicCarrierContract#gender}). The add form offers "X"
     * (Intersex, commn.Gender), which is O (Other); the REST service and the PHS/STAR feed (PID-8)
     * can store any one-character code or none, which is U, the schema's unknown gender. The
     * stored code is carried whenever the element is not that code.
     */
    private static String gender(State state) {
        String stored = state.value("sex");
        String gender = CdsDemographicCarrierContract.gender(stored);
        if (gender.equals(stored)) state.mapped("sex", "Gender", "REVIEWED_NATIVE_CODE");
        else if ("X".equals(stored))
            state.alsoCarried("sex", "Gender", "INTERSEX_AS_OTHER_WITH_EXACT_CODE_CARRIED");
        else state.alsoCarried("sex", "Gender", UNKNOWN_SEX);
        return gender;
    }

    /**
     * PersonStatusCode is required ({@link CdsDemographicCarrierContract#personStatus}). The
     * REST demographic update saves the status it is sent, none included.
     */
    private static void status(State state, CdsDemographicCarrierContract.PersonStatus status) {
        String stored = state.value("patient_status");
        if (!state.present("patient_status")) {
            state.alsoCarried(
                    "patient_status", "PersonStatusCode/PersonStatusAsPlainText", MISSING_STATUS);
            return;
        }
        if (status.enumerated()) {
            state.mapped("patient_status", "PersonStatusCode/PersonStatusAsEnum", "NATIVE_STATUS_CODE");
            return;
        }
        String path = "PersonStatusCode/PersonStatusAsPlainText";
        // CARLOS's own non-CDS statuses travel by meaning, not as a bare two-letter code (the
        // add/edit forms' "MO - Moved", "FI - Fired"); CdsPatientIdentityResolver maps the label
        // back. A clinic-defined status is exported as it is stored.
        String label =
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsPatientStatusLabels.LABELS
                        .getOrDefault(stored, stored);
        if (status.text().equals(label)) state.mapped("patient_status", path, "EXACT_NATIVE_TEXT");
        else state.alsoCarried("patient_status", path, CdsExportNativeFieldAssessments.REPLACED);
    }

    /**
     * DateOfBirth is required. The demographic forms build it from three selects, but the REST
     * demographic service checks it with a lenient calendar (a month 13 or day 40 is saved) and
     * accepts none at all. Stored columns that are not one calendar date ({@link
     * CdsDemographicCarrierContract#birthDate}) are written as {@link
     * CdsDemographicCarrierContract#NO_BIRTH_DATE}, a date nobody can mistake for a birth date,
     * and carried. A real date stored without a leading zero ("2" for February) is written in
     * full and carried.
     */
    private static LocalDate birth(State state) {
        String[] columns = {"year_of_birth", "month_of_birth", "date_of_birth"};
        LocalDate date =
                CdsDemographicCarrierContract.birthDate(
                        state.value(columns[0]), state.value(columns[1]), state.value(columns[2]));
        if (date == null) {
            for (String column : columns) state.alsoCarried(column, "DateOfBirth", NO_BIRTH_DATE);
            return LocalDate.parse(CdsDemographicCarrierContract.NO_BIRTH_DATE);
        }
        var written = CdsDemographicCarrierContract.birthColumns(date);
        for (int i = 0; i < columns.length; i++)
            if (written.get(i).equals(state.value(columns[i])))
                state.mapped(columns[i], "DateOfBirth", "EXACT_NATIVE_CALENDAR_DATE");
            else state.alsoCarried(columns[i], "DateOfBirth", BIRTH_COLUMN_IN_FULL);
        return date;
    }

    /**
     * HealthCard is optional and its Number required. A card whose number no Number can hold (a
     * version code or expiry typed without a number, a blank number, a character XML cannot
     * carry) is left out whole, and its populated columns are carried. A version longer than the
     * element's two characters (the form box takes three) or an expiry that is not a calendar
     * date leaves only that element out, carried.
     */
    private static HealthCard health(State state) {
        boolean card = Set.of("hin", "ver", "hc_renew_date").stream().anyMatch(state::present);
        if (!card) {
            // The add and edit forms always submit an issuer: add-form-personal.jsp:865-866
            // preselects the clinic's default province, or "OT" (Other). Without a number,
            // version or expiry there is no health card to describe, and HealthCard is optional,
            // so the issuer alone states no patient fact and must not hold the chart.
            if (state.present("hc_type"))
                state.operational("hc_type", "HEALTH_CARD_ISSUER_WITHOUT_A_CARD");
            return null;
        }
        String province = CdsHealthCardAuthority.wire(state.value("hc_type"));
        String number = State.usableToken(state.value("hin"), 20);
        if (province == null || number == null) {
            // The forms default the issuer to "OT" (Other) and offer "US"; ProvinceCode is
            // required and the pinned schema's -50/-70/-90 codes carry no documented meaning, so
            // a card of an unlisted issuer travels whole in the native demographic record, as
            // does a card without a usable number.
            for (String column : List.of("hin", "ver", "hc_renew_date", "hc_type"))
                if (state.present(column)) state.carry(column);
            return null;
        }
        state.token("hin", "HealthCard/Number", number);
        String version = state.optionalToken("ver", "HealthCard/Version", 2);
        state.mapped("hc_type", "HealthCard/ProvinceCode", "PINNED_HEALTH_CARD_AUTHORITY");
        LocalDate expiry = state.date("hc_renew_date", "HealthCard/Expirydate");
        return new HealthCard(number, version, expiry, province);
    }

    private static void address(
            State state,
            java.util.List<Address> output,
            String type,
            String streetColumn,
            String cityColumn,
            String provinceColumn,
            String postalColumn) {
        if (Set.of(streetColumn, cityColumn, provinceColumn, postalColumn).stream()
                .noneMatch(state::present)) return;
        if (Set.of(streetColumn, cityColumn, postalColumn).stream().noneMatch(state::present)) {
            // add-form-personal.jsp:325-327 and 603-608 preselect the clinic's default province
            // (or "OT") for both the mailing and the residential address, so every saved patient
            // has a province even when no address was entered. A province alone is that form
            // default, not a recorded address; emitting it would assert a residence.
            state.operational(provinceColumn, "PROVINCE_WITHOUT_AN_ADDRESS_IS_A_FORM_DEFAULT");
            return;
        }
        String path = "Address[" + type + "]/Structured/";
        // The address column holds 60 characters and Line1 50; a longer line travels exactly in
        // the native demographic record instead of being truncated.
        String street = state.optionalToken(streetColumn, path + "Line1", 50);
        String city = state.optionalToken(cityColumn, path + "City", 80);
        String subdivision = subdivision(state, provinceColumn, path + "CountrySubdivisionCode");
        boolean zip = subdivision != null && subdivision.startsWith("US-");
        String postalPath = path + "PostalZipCode/" + (zip ? "ZipCode" : "PostalCode");
        String postal = state.optionalToken(postalColumn, postalPath, 10);
        // Without a pinned jurisdiction PostalCode and ZipCode cannot be told apart.
        if (postal != null && subdivision == null) {
            state.carry(postalColumn);
            postal = null;
        }
        if (postal != null) {
            if (subdivision != null
                    && subdivision.startsWith("CA-")
                    && postal.matches("[A-Za-z][0-9][A-Za-z] [0-9][A-Za-z][0-9]")) {
                boolean collapsed = !postal.equals(state.value(postalColumn));
                postal = postal.substring(0, 3) + postal.substring(4);
                if (collapsed)
                    state.alsoCarried(
                            postalColumn, postalPath, "CANADIAN_POSTAL_DISPLAY_SEPARATOR");
                else state.mapped(postalColumn, postalPath, "CANADIAN_POSTAL_DISPLAY_SEPARATOR");
            }
            if (!postal.matches(zip ? "[0-9-]*" : "[a-zA-Z0-9-]*")) {
                state.carry(postalColumn);
                postal = null;
            }
        }
        if (street != null || city != null || subdivision != null || postal != null)
            output.add(new Address(type, street, city, subdivision, postal));
    }

    private static String subdivision(State state, String column, String path) {
        String value = state.value(column);
        if (!state.present(column)) return null;
        if (CdsHealthCardAuthority.CANADIAN.contains(value)) {
            state.mapped(column, path, "CANADIAN_SUBDIVISION_CODE");
            return "CA-" + value;
        }
        if (value.startsWith("CA-") && CdsHealthCardAuthority.CANADIAN.contains(value.substring(3))
                || CdsHealthCardAuthority.PINNED_US.contains(value)) {
            state.mapped(column, path, "EXACT_SUBDIVISION_CODE");
            return value;
        }
        // "OT" (Other, the forms' default without a clinic province), "US" and any code outside
        // the pinned list: CountrySubdivisionCode is optional, so the value travels exactly in
        // the native demographic record.
        state.carry(column);
        return null;
    }

    /**
     * Native spoken_lang stores the display name that add-form-personal.jsp offers from
     * SpokenLangProperties (spoken_languages_codes.properties). That same table gives its ISO
     * 639-2 code, and the CARLOS importer reverses it with SpokenLangProperties.getLangByCode
     * (CdsDemographicDetailsService), so the name survives a round trip. An arbitrary
     * three-letter string or an unlisted name is not evidence of an ISO 639-2 code.
     */
    private static String spoken(State state) {
        if (!state.present("spoken_lang")) return null;
        String value = state.value("spoken_lang");
        String reviewed =
                Map.of("English", "eng", "French", "fre", "eng", "eng", "fre", "fre", "fra", "fra")
                        .get(value);
        if (reviewed != null) {
            state.mapped("spoken_lang", "PreferredSpokenLanguage", "REVIEWED_NATIVE_CODE");
            return reviewed;
        }
        String code =
                io.github.carlos_emr.carlos.demographic.pageUtil.SpokenLangProperties.getInstance()
                        .getCodeByLang(value);
        if (code == null || !code.matches("[A-Z]{3}")) {
            state.carry("spoken_lang");
            return null;
        }
        state.mapped(
                "spoken_lang", "PreferredSpokenLanguage", "CARLOS_SPOKEN_LANGUAGE_ISO_639_2_CODE");
        return code.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * PhoneNumber is optional: a number its 25-character token cannot hold (blank, or with a
     * character XML cannot carry; the REST service saves what it is sent) is left out and
     * carried. An extension left without its number travels in the record
     * ({@link CdsExportDemographicPhonesMapper}).
     */
    private static void phone(
            State state, java.util.List<Phone> phones, String type, String column) {
        String number = state.optionalToken(column, "PhoneNumber[" + type + "]/phoneNumber", 25);
        if (number != null) phones.add(new Phone(type, number));
    }

    private static final class State {
        private final Map<String, String> values;
        private final Map<String, Assessment> assessments = new LinkedHashMap<>();

        private State(Map<String, String> values) {
            this.values = values;
            values.forEach(
                    (column, value) ->
                            assessments.put(
                                    column,
                                    new Assessment(
                                            presence(value),
                                            value == null || value.isEmpty()
                                                    ? Outcome.ABSENT
                                                    : Outcome.REQUIRES_REVIEW,
                                            "",
                                            "UNMAPPED_NATIVE_VALUE")));
        }

        private String value(String column) {
            return values.get(column);
        }

        private boolean present(String column) {
            String value = value(column);
            return value != null && !value.isEmpty();
        }

        private static Presence presence(String value) {
            return value == null
                    ? Presence.SQL_NULL
                    : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
        }

        private void set(String column, Outcome outcome, String path, String rule) {
            assessments.put(column, new Assessment(presence(value(column)), outcome, path, rule));
        }

        private void mapped(String column, String path, String rule) {
            set(column, Outcome.MAPPED, path, rule);
        }

        private void operational(String column, String rule) {
            set(column, Outcome.OPERATIONAL, "", rule);
        }

        /** The value has no element that can hold it: it travels in the native record. */
        private void carry(String column) {
            var decision = CdsExportDemographicSupportingMapper.carried(value(column));
            assessments.put(
                    column,
                    new Assessment(
                            presence(value(column)),
                            Outcome.valueOf(decision.outcome().name()),
                            decision.destination(),
                            decision.rule()));
        }

        /**
         * The element holds the value a documented rule gives for it; the exact stored value
         * (SQL NULL and empty text included) also travels in the native demographic record.
         */
        private void alsoCarried(String column, String path, String rule) {
            set(
                    column,
                    Outcome.MAPPED,
                    path + "," + CdsExportDemographicSupportingMapper.DESTINATION,
                    rule);
        }

        /**
         * The xs:token form of a value when an element of {@code maximum} characters can hold it
         * (not blank, no character XML cannot carry), else null.
         */
        private static String usableToken(String value, int maximum) {
            if (value == null) return null;
            String collapsed = CdsDemographicCarrierContract.token(value);
            return collapsed.isEmpty()
                            || collapsed.codePointCount(0, collapsed.length()) > maximum
                            || !CdsNativeExtensionContract.xmlElementText(collapsed)
                                    .equals(collapsed)
                    ? null
                    : collapsed;
        }

        /**
         * Decides a column written as {@code written}, its xs:token form: untrimmed or doubled
         * white space is collapsed, as every schema reader sees an xs:token, and the exact stored
         * value is carried.
         */
        private void token(String column, String path, String written) {
            if (written.equals(value(column))) mapped(column, path, "EXACT_NATIVE_TEXT");
            else alsoCarried(column, path, COLLAPSED);
        }

        /**
         * An optional token element: a value it cannot hold (blank, longer than the element, or
         * with a character XML cannot carry) is left out and carried, never a hold.
         */
        private String optionalToken(String column, String path, int maximum) {
            if (!present(column)) return null;
            String written = usableToken(value(column), maximum);
            if (written == null) {
                carry(column);
                return null;
            }
            token(column, path, written);
            return written;
        }

        /** An optional code element: an unlisted value is carried, never a hold. */
        private String optionalCode(String column, String path, Map<String, String> mapping) {
            if (!present(column)) return null;
            String result = mapping.get(value(column));
            if (result == null) carry(column);
            else mapped(column, path, "REVIEWED_NATIVE_CODE");
            return result;
        }

        /**
         * An optional date element. A DATE column is captured as yyyy-MM-dd; a stored value that
         * is not a calendar date (the zero date, which a session without strict SQL mode stores)
         * is left out and carried.
         */
        private LocalDate date(String column, String path) {
            if (!present(column)) return null;
            try {
                String value = value(column);
                if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                    throw new DateTimeException("lexical");
                LocalDate date = LocalDate.parse(value);
                if (date.getYear() < 1) throw new DateTimeException("range");
                mapped(column, path, "EXACT_NATIVE_CALENDAR_DATE");
                return date;
            } catch (DateTimeException failure) {
                carry(column);
                return null;
            }
        }
    }
}
