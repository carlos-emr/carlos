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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The one carrier for demographic values that CDS 5.2 Demographics has no element for.
 *
 * <p>The demographic add and edit screens write values that the Demographics section cannot hold:
 * preferred name, alias, pronoun and gender identity, the referral-doctor wrapper, the nurse,
 * resident and midwife selects ({@code demographiccust.cust1}, {@code cust2}, {@code cust4}),
 * and extension properties such as ethnicity, consents or paper-chart status. Demographics has no
 * {@code ResidualInfo}, so these values travel together in one NewCategory record,
 * {@value #CATEGORY_NAME}, whose single {@value #ELEMENT_NAME} residual holds a versioned JSON
 * object in a fixed key order with every space escaped ({@link CdsExportScreeningMapper#tokenSafe}),
 * so {@code xs:token} normalization cannot change it. NewCategory is where this exporter already
 * carries native supporting context (printed prescriptions, laboratory routes, observations).
 *
 * <p>Every deciding mapper marks such a value {@code MAPPED} to {@value #DESTINATION}; when the job
 * did not select NewCategory, {@link #withSelection} turns those decisions into an explicit
 * {@code OPERATIONAL} {@value #NOT_SELECTED_RULE} and the record is not written, which the package
 * ReadMe states. The chart is never held for such a value. A care-team reference carries the
 * stored provider number and, when the captured public provider row matches it exactly, that
 * row's six public lookup columns, as the prescription record carries its author; no private
 * provider field is read. Only values that cannot be written as XML text at all still require
 * review.
 */
public final class CdsExportDemographicSupportingMapper {
    /** NewCategory {@code CategoryName} of the native demographic record. */
    public static final String CATEGORY_NAME = "CARLOS.NativeDemographic.v1";

    /** Residual {@code DataElement} name whose Content is the versioned JSON. */
    public static final String ELEMENT_NAME = "CARLOS.Demographic.v1";

    /** Destination of every value carried by the record. */
    public static final String DESTINATION = "NewCategory/ResidualInfo";

    /** Rule of a carried value when NewCategory is selected. */
    public static final String CARRIED_RULE = "NO_CDS_ELEMENT_CARRIED_IN_NATIVE_DEMOGRAPHIC_RECORD";

    /** Rule of a carried value when the job did not select NewCategory. */
    public static final String NOT_SELECTED_RULE = "NO_CDS_ELEMENT_NEW_CATEGORY_NOT_SELECTED";

    /** Destination of the legacy patient alert ({@code demographiccust.cust3}). */
    public static final String ALERT_DESTINATION = "AlertsAndSpecialNeeds/AlertDescription";

    /**
     * Base-row columns with no Demographics element, in carrier order. Names/OtherNames is not used
     * for {@code alias} or {@code pref_name}: the pinned schema lists the name-purpose codes (HC, L,
     * AL, C) without their meanings, OtherName parts must be typed FAMC or GIV, and the CARLOS
     * importer keeps OtherNames source-only, so the residual is the only exact round trip.
     */
    static final List<String> COLUMNS =
            List.of(
                    "alias",
                    "anonymous",
                    "children",
                    "citizenship",
                    "consentToUseEmailForCare",
                    "country_of_origin",
                    "end_date",
                    "family_doctor",
                    "gender",
                    "genderId",
                    "myOscarUserName",
                    "newsletter",
                    "pcn_indicator",
                    "pref_name",
                    "previousAddress",
                    "pronoun",
                    "pronounId",
                    "sourceOfIncome");

    /**
     * The care-team provider selects of add-form-clinical.jsp and edit-form-clinical.jsp, by
     * column, with the role label those forms show.
     */
    static final Map<String, String> CARE_TEAM = careTeam();

    private static final List<String> PROVIDER_COLUMNS =
            List.of(
                    "provider_no",
                    "first_name",
                    "last_name",
                    "ohip_no",
                    "practitionerNo",
                    "practitionerNoType");

    /**
     * Form placeholders, not patient facts: add-form-personal.jsp preselects newsletter "Unknown"
     * and both forms offer country of origin "-1" ("Not set"); DemographicManagerImpl and the
     * forms write the referral-doctor wrapper with nothing inside it.
     */
    private static final Map<String, List<String>> PLACEHOLDERS =
            Map.of(
                    "newsletter",
                    List.of("Unknown"),
                    "country_of_origin",
                    List.of("-1"),
                    "family_doctor",
                    List.of(
                            "<rdohip></rdohip><rd></rd>",
                            "<rdohip></rdohip><rd></rd><family_doc></family_doc>"));

    private CdsExportDemographicSupportingMapper() {}

    /** Exact demographic rows of one patient that the record may carry. */
    public record Rows(
            int patient,
            CdsExportCapturedTextRowDto current,
            CdsExportCapturedTextRowDto custom,
            List<CdsExportCapturedTextRowDto> properties,
            Map<String, CdsExportCapturedTextRowDto> providers) {
        public Rows {
            Objects.requireNonNull(current);
            properties = List.copyOf(properties);
            providers = Map.copyOf(providers);
            if (patient < 1
                    || !current.table().equals("demographic")
                    || !Integer.toString(patient).equals(current.fields().get("demographic_no"))
                    || custom != null
                            && (!custom.table().equals("demographiccust")
                                    || !Integer.toString(patient)
                                            .equals(custom.fields().get("demographic_no")))
                    || properties.stream()
                            .anyMatch(
                                    row ->
                                            !row.table().equals("demographicExt")
                                                    || !Integer.toString(patient)
                                                            .equals(
                                                                    row.fields()
                                                                            .get(
                                                                                    "demographic_no")))
                    || providers.entrySet().stream()
                            .anyMatch(
                                    entry ->
                                            !entry.getValue().table().equals("provider")
                                                    || !entry.getKey()
                                                            .equals(
                                                                    entry.getValue()
                                                                            .fields()
                                                                            .get("provider_no"))))
                throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_SUPPORTING_SCOPE_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportDemographicSupportingMapper.Rows[protected]";
        }
    }

    /** The record's JSON and the decision of every value it carries. */
    public record Record(
            String json, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Record {
            Objects.requireNonNull(json);
            fields = Map.copyOf(fields);
            if (fields.isEmpty()
                    || fields.values().stream()
                            .anyMatch(
                                    field ->
                                            field.outcome() != Outcome.MAPPED
                                                    || !DESTINATION.equals(field.destination())))
                throw new IllegalArgumentException(
                        "CDS_EXPORT_DEMOGRAPHIC_SUPPORTING_RECORD_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportDemographicSupportingMapper.Record[protected]";
        }
    }

    /**
     * Whether a populated base-row value has no Demographics element and is carried. Placeholders
     * the forms write for "not set" are accounted for separately and never carried alone.
     */
    static boolean carriedColumn(String column, String value) {
        return COLUMNS.contains(column)
                && present(value)
                && !PLACEHOLDERS.getOrDefault(column, List.of()).contains(value);
    }

    /** True when the value is the "not set" placeholder a form writes for this column. */
    static boolean placeholder(String column, String value) {
        return value != null && PLACEHOLDERS.getOrDefault(column, List.of()).contains(value);
    }

    /**
     * The decision for a value the record carries: {@code MAPPED} to the record, or review when
     * the value holds a character no XML document can carry (a lone surrogate, U+FFFE, U+FFFF).
     */
    static CdsExportFieldAssessmentDto carried(String value) {
        Presence presence = presence(value);
        if (presence != Presence.VALUE)
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_SUPPORTING_VALUE_ABSENT");
        return carriable(value)
                ? new CdsExportFieldAssessmentDto(presence, Outcome.MAPPED, DESTINATION, CARRIED_RULE)
                : new CdsExportFieldAssessmentDto(
                        presence,
                        Outcome.REQUIRES_REVIEW,
                        DESTINATION,
                        "SUPPORTING_WIRE_TEXT_REQUIRES_REVIEW");
    }

    /**
     * Applies the job's selection: without NewCategory every value decided for the record is an
     * explicit {@code OPERATIONAL} {@value #NOT_SELECTED_RULE}; with it, decisions are unchanged.
     */
    static <K> Map<K, CdsExportFieldAssessmentDto> withSelection(
            Map<K, CdsExportFieldAssessmentDto> fields, boolean newCategory) {
        if (newCategory) return fields;
        var result = new LinkedHashMap<K, CdsExportFieldAssessmentDto>();
        fields.forEach((key, value) -> result.put(key, withSelection(value, false)));
        return result;
    }

    static CdsExportFieldAssessmentDto withSelection(
            CdsExportFieldAssessmentDto value, boolean newCategory) {
        if (newCategory
                || value.outcome() != Outcome.MAPPED
                || !DESTINATION.equals(value.destination())) return value;
        return new CdsExportFieldAssessmentDto(
                value.presence(), Outcome.OPERATIONAL, "", NOT_SELECTED_RULE);
    }

    /**
     * Decisions for the public provider rows that a populated care-team column names exactly.
     * A collation-only candidate is not this patient's reference and is never named.
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> careTeamProviders(
            CdsExportCapturedTextRowDto custom,
            Map<String, CdsExportCapturedTextRowDto> providers,
            boolean newCategory) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        if (custom == null) return Map.of();
        for (String column : CARE_TEAM.keySet()) {
            if (!custom.fields().containsKey(column)) continue;
            String id = custom.fields().get(column);
            var provider = present(id) ? providers.get(id) : null;
            if (provider == null) continue;
            for (String field : PROVIDER_COLUMNS) {
                String value = provider.fields().get(field);
                result.putIfAbsent(
                        new CdsExportNativeFieldKeyDto(provider.table(), provider.recordKey(), field),
                        withSelection(
                                new CdsExportFieldAssessmentDto(
                                        presence(value),
                                        Outcome.MAPPED,
                                        DESTINATION,
                                        "CARE_TEAM_PUBLIC_PROVIDER_CARRIED"),
                                newCategory));
            }
        }
        return Map.copyOf(result);
    }

    /** Provider numbers the populated care-team columns of this row name. */
    static List<String> careTeamReferences(CdsExportCapturedTextRowDto custom) {
        var result = new ArrayList<String>();
        if (custom == null) return List.of();
        for (String column : CARE_TEAM.keySet()) {
            String id = custom.fields().get(column);
            if (present(id) && !result.contains(id)) result.add(id);
        }
        return List.copyOf(result);
    }

    /**
     * Builds the record from the same decisions the demographic occurrence makes, or returns null
     * when the patient has no carried value.
     *
     * @throws IOException when a carried value cannot be written as XML text, or the rows are not
     *     one patient's exact demographic slice
     */
    public static Record map(Rows rows, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(rows);
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var demographic = new LinkedHashMap<String, String>();
        var current = rows.current();
        for (String column : COLUMNS) {
            String value = current.fields().get(column);
            if (!current.fields().containsKey(column)) scope();
            if (!carriedColumn(column, value)) continue;
            demographic.put(column, value);
            fields.put(
                    new CdsExportNativeFieldKeyDto(current.table(), current.recordKey(), column),
                    carried(value));
        }
        var careTeam = new ArrayList<Map<String, Object>>();
        var custom = rows.custom();
        if (custom != null)
            for (var role : CARE_TEAM.entrySet()) {
                checkpoint.run();
                if (!custom.fields().containsKey(role.getKey())) scope();
                String id = custom.fields().get(role.getKey());
                if (!present(id)) continue;
                fields.put(
                        new CdsExportNativeFieldKeyDto(
                                custom.table(), custom.recordKey(), role.getKey()),
                        carried(id));
                var provider = rows.providers().get(id);
                var entry = new LinkedHashMap<String, Object>();
                entry.put("column", role.getKey());
                entry.put("role", role.getValue());
                entry.put("providerNo", id);
                entry.put("publicProvider", provider == null ? null : publicProvider(provider));
                careTeam.add(entry);
            }
        fields.putAll(careTeamProviders(custom, rows.providers(), true));
        var properties = new ArrayList<Map<String, Object>>();
        if (!rows.properties().isEmpty()) {
            var raw = new LinkedHashMap<>(current.fields());
            raw.remove("demographic_no");
            var row = new CdsExportDemographicRowDto(rows.patient(), raw);
            var phones =
                    new CdsExportDemographicPhonesMapper()
                            .map(
                                    new CdsExportDemographicMapper().map(row),
                                    row,
                                    rows.properties(),
                                    8192,
                                    64L << 20,
                                    checkpoint);
            for (var property :
                    rows.properties().stream()
                            .sorted(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey))
                            .toList()) {
                checkpoint.run();
                var key =
                        new CdsExportNativeFieldKeyDto(
                                property.table(), property.recordKey(), "value");
                var decision = phones.relatedFields().get(key);
                if (decision == null || !DESTINATION.equals(decision.destination())) continue;
                var entry = new LinkedHashMap<String, Object>();
                entry.put("id", property.fields().get("id"));
                entry.put("key", property.fields().get("key_val"));
                entry.put("value", property.fields().get("value"));
                properties.add(entry);
                for (String column : List.of("key_val", "value")) {
                    var field =
                            new CdsExportNativeFieldKeyDto(
                                    property.table(), property.recordKey(), column);
                    fields.put(field, phones.relatedFields().get(field));
                }
            }
        }
        if (demographic.isEmpty() && careTeam.isEmpty() && properties.isEmpty()) return null;
        if (fields.values().stream().anyMatch(field -> field.outcome() != Outcome.MAPPED))
            throw new CdsExportMappingReviewException(
                    "CDS_EXPORT_DEMOGRAPHIC_SUPPORTING_TEXT_REQUIRES_REVIEW");
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("demographic", demographic);
        context.put("careTeam", careTeam);
        context.put("properties", properties);
        String json = JsonMapper.builder().build().writeValueAsString(context);
        if (!CdsExportNativeFieldAssessments.legal(json, 1 << 24))
            throw new CdsExportMappingReviewException(
                    "CDS_EXPORT_DEMOGRAPHIC_SUPPORTING_TEXT_REQUIRES_REVIEW");
        checkpoint.run();
        return new Record(json, fields);
    }

    /** Schema-ordered NewCategory content of the record. */
    public static void write(String json, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "CategoryName", CATEGORY_NAME);
        xml.element(
                "cds",
                "CategoryDescription",
                "Native demographic details without a CDS 5.2 Demographics element");
        xml.start("cds", "ResidualInfo");
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml, ELEMENT_NAME, "string", CdsExportScreeningMapper.tokenSafe(json));
        xml.end();
    }

    private static Map<String, String> publicProvider(CdsExportCapturedTextRowDto provider) {
        var result = new LinkedHashMap<String, String>();
        for (String column : PROVIDER_COLUMNS) result.put(column, provider.fields().get(column));
        return result;
    }

    private static Map<String, String> careTeam() {
        var result = new LinkedHashMap<String, String>();
        result.put("cust1", "Nurse");
        result.put("cust2", "Resident");
        result.put("cust4", "Midwife");
        return java.util.Collections.unmodifiableMap(result);
    }

    static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean carriable(String value) {
        for (int i = 0; i < value.length(); ) {
            int code = value.codePointAt(i);
            if (code >= 0xd800 && code <= 0xdfff || code == 0xfffe || code == 0xffff)
                return false;
            i += Character.charCount(code);
        }
        return true;
    }

    static Presence presence(String value) {
        return value == null ? Presence.SQL_NULL : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_DEMOGRAPHIC_SUPPORTING_SCOPE_INVALID");
    }
}
