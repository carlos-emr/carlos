/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportAppointmentMapperUnitTest {
    static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private final CdsExportAppointmentMapper mapper = new CdsExportAppointmentMapper();

    @Test
    void shouldAccountForEveryNativeField_andUseExactCapturedPublicDefinitions() throws Exception {
        var mapped = mapper.map(27, appointment(baseline()), lookups(), ZONE);
        assertThat(mapped.nativeRowRepresentable()).isTrue();
        assertThat(mapped.fields()).hasSize(41);
        assertThat(mapped.value().status()).isEqualTo("Exact booked Ω");
        assertThat(mapped.value().provider().ohipNumber()).isEqualTo("001234");
        assertThat(mapped.value().provider().firstName()).isEqualTo("Zoë😀");
        assertThat(mapped.value().timing().durationMinutes()).isEqualTo(75);
        assertThat(mapped.value().notes()).isEqualTo("Literal café 😀\r\nnotes");
        assertThat(mapped.fields().get(key("name")).presence()).isEqualTo(Presence.SQL_NULL);
        assertThatThrownBy(() -> mapped.fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRefuseMissingDuplicateAndFirstCharacterOnlyStatusMatches_forAppointmentMapperContract() throws Exception {
        var original = lookups();
        var definition = status();
        for (var candidates :
                List.of(
                        Map.<String, List<CdsExportCapturedTextRowDto>>of(),
                        Map.of("Q", List.of(definition)),
                        Map.of("Q1", List.of(definition, definition)))) {
            var mapped =
                    mapper.map(
                            27,
                            appointment(baseline()),
                            new CdsExportAppointmentLookupsDto(
                                    "a".repeat(64), original.providers(), candidates),
                            ZONE);
            assertThat(mapped.nativeRowRepresentable()).isFalse();
            assertThat(mapped.fields().get(key("status")).rule())
                    .isEqualTo("EXACT_STATUS_DEFINITION_REQUIRED");
        }
    }

    @Test
    void shouldRefuseAProviderCollationAlias_insteadOfNormalizingItsIdentity() throws Exception {
        var fields = baseline();
        fields.put("provider_no", "0É27");
        var mapped = mapper.map(27, appointment(fields), lookups(), ZONE);
        assertThat(mapped.nativeRowRepresentable()).isFalse();
        assertThat(mapped.fields().get(key("provider_no")).rule())
                .isEqualTo("EXACT_PUBLIC_PROVIDER_REFERENCE_REQUIRED");
    }

    @Test
    void shouldNeverFallBackToImportedStatus_afterTheCurrentStatusWasClearedOrChanged()
            throws Exception {
        for (String current : List.of("", "Q1")) {
            var fields = baseline();
            fields.put("status", current);
            fields.put("imported_status", "Prior imported status");
            var mapped = mapper.map(27, appointment(fields), lookups(), ZONE);
            assertThat(mapped.nativeRowRepresentable()).isFalse();
            assertThat(mapped.value()).isNull();
            assertThat(mapped.fields().get(key("imported_status")).rule())
                    .isEqualTo("RETAINED_APPOINTMENT_STATUS_REQUIRES_RECONCILIATION");
        }
    }

    @Test
    void shouldRejectAnUnreviewedPhysicalColumn_beforeMappingAnyValues() throws Exception {
        var fields = baseline();
        fields.put("locationCode", "17");
        assertThatThrownBy(() -> mapper.map(27, appointment(fields), lookups(), ZONE))
                .isInstanceOf(java.io.IOException.class)
                .hasMessage("CDS_EXPORT_APPOINTMENT_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldKeepUnmappedExtrasAndOversizeValuesExplicit_withoutAppendingThemToNotes()
            throws Exception {
        for (var extra :
                Map.of(
                                "location",
                                "Clinic A",
                                "name",
                                "Different patient display",
                                "remarks",
                                "Additional clinical information",
                                "reason",
                                "x".repeat(251))
                        .entrySet()) {
            var fields = baseline();
            fields.put(extra.getKey(), extra.getValue());
            var mapped = mapper.map(27, appointment(fields), lookups(), ZONE);
            assertThat(mapped.nativeRowRepresentable()).isFalse();
            assertThat(mapped.fields().get(key(extra.getKey())).outcome())
                    .isEqualTo(Outcome.REQUIRES_REVIEW);
        }
    }

    @Test
    void shouldRefuseOvernightAndAmbiguousTimes_withoutRoundingOrInventingTheNextDay()
            throws Exception {
        var fields = baseline();
        fields.put("start_time", "23:30:00");
        fields.put("end_time", "00:30:00");
        var mapped = mapper.map(27, appointment(fields), lookups(), ZONE);
        assertThat(mapped.nativeRowRepresentable()).isFalse();
        assertThat(mapped.fields().get(key("end_time")).outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldPreserveMissingAndEmptyProviderIdentifiers_asNoIdentifierAssertion()
            throws Exception {
        for (String value : Arrays.asList(null, "")) {
            var provider = new LinkedHashMap<>(provider().fields());
            provider.put("ohip_no", value);
            var lookups =
                    new CdsExportAppointmentLookupsDto(
                            "a".repeat(64),
                            Map.of(
                                    "0é27",
                                    new CdsExportCapturedTextRowDto(
                                            "provider",
                                            CdsExportPublicProviderSourceReader.key("0é27"),
                                            provider)),
                            lookups().statuses());
            var mapped = mapper.map(27, appointment(baseline()), lookups, ZONE);
            assertThat(mapped.nativeRowRepresentable()).isTrue();
            assertThat(mapped.value().provider().ohipNumber()).isNull();
            assertThat(
                            mapped.fields()
                                    .get(
                                            new CdsExportNativeFieldKeyDto(
                                                    "provider",
                                                    CdsExportPublicProviderSourceReader.key("0é27"),
                                                    "ohip_no"))
                                    .presence())
                    .isEqualTo(value == null ? Presence.SQL_NULL : Presence.EMPTY);
        }
    }

    @Test
    void shouldRefuseCrossPatientOrForgedReferenceRows_beforeReturningAnyClinicalValue() {
        assertThatThrownBy(() -> mapper.map(28, appointment(baseline()), lookups(), ZONE))
                .hasMessage("CDS_EXPORT_APPOINTMENT_CAPTURE_SCOPE_INVALID");
        var forged =
                new CdsExportCapturedTextRowDto(
                        "provider",
                        CdsExportPublicProviderSourceReader.key("OTHER"),
                        provider().fields());
        var lookups =
                new CdsExportAppointmentLookupsDto(
                        "a".repeat(64), Map.of("0é27", forged), lookups().statuses());
        assertThatThrownBy(() -> mapper.map(27, appointment(baseline()), lookups, ZONE))
                .hasMessage("CDS_EXPORT_APPOINTMENT_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyDictionaryThirtyTwoThousandCharacterLimitCountsUnicode_withoutTruncation()
            throws Exception {
        var fields = baseline();
        String exact = "😀".repeat(32000);
        fields.put("notes", exact);
        var good = mapper.map(27, appointment(fields), lookups(), ZONE);
        assertThat(good.nativeRowRepresentable()).isTrue();
        assertThat(good.value().notes()).isEqualTo(exact);
        fields.put("notes", exact + "x");
        var blocked = mapper.map(27, appointment(fields), lookups(), ZONE);
        assertThat(blocked.nativeRowRepresentable()).isFalse();
        assertThat(blocked.fields().get(key("notes")).outcome()).isEqualTo(Outcome.REQUIRES_REVIEW);
        assertThat(fields.get("notes")).isEqualTo(exact + "x");
    }

    static LinkedHashMap<String, String> baseline() {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.APPOINTMENTS
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "appointment_no",
                        "51",
                        "demographic_no",
                        "27",
                        "appointment_date",
                        "2024-02-29",
                        "start_time",
                        "09:00:30",
                        "end_time",
                        "10:15:30",
                        "status",
                        "Q1",
                        "provider_no",
                        "0é27",
                        "reason",
                        "Exact purpose",
                        "notes",
                        "Literal café 😀\r\nnotes"));
        return fields;
    }

    static CdsExportCapturedTextRowDto appointment(Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto(
                "appointment", CdsExportPatientTableSourceReader.key(51), fields);
    }

    static CdsExportCapturedTextRowDto provider() {
        var fields = new LinkedHashMap<String, String>();
        io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS.forEach(
                name -> fields.put(name, null));
        fields.putAll(
                Map.of(
                        "provider_no",
                        "0é27",
                        "first_name",
                        "Zoë😀",
                        "last_name",
                        "D’Arcy",
                        "ohip_no",
                        "001234"));
        return new CdsExportCapturedTextRowDto(
                "provider", CdsExportPublicProviderSourceReader.key("0é27"), fields);
    }

    static CdsExportCapturedTextRowDto status() {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.APPOINTMENT_STATUSES
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of("id", "91", "status", "Q1", "description", "Exact booked Ω", "active", "0"));
        return new CdsExportCapturedTextRowDto(
                "appointment_status", CdsExportPatientTableSourceReader.key(91), fields);
    }

    static CdsExportAppointmentLookupsDto lookups() {
        return new CdsExportAppointmentLookupsDto(
                "a".repeat(64), Map.of("0é27", provider()), Map.of("Q1", List.of(status())));
    }

    static CdsExportNativeFieldKeyDto key(String column) {
        return new CdsExportNativeFieldKeyDto(
                "appointment", CdsExportPatientTableSourceReader.key(51), column);
    }
}
