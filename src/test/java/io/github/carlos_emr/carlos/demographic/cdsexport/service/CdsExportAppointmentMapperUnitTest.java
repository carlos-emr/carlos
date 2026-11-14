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
        assertThat(mapped.value().timing().durationMinutes()).isEqualTo(76);
        assertThat(mapped.value().notes()).isEqualTo("Literal café 😀\r\nnotes");
        assertThat(mapped.fields().get(key("name")).presence()).isEqualTo(Presence.SQL_NULL);
        assertThatThrownBy(() -> mapped.fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void
            shouldRefuseMissingDuplicateAndFirstCharacterOnlyStatusMatches_forAppointmentMapperContract()
                    throws Exception {
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
                                    "a".repeat(64),
                                    original.providers(),
                                    candidates,
                                    Map.of(),
                                    Set.of()),
                            ZONE);
            assertThat(mapped.nativeRowRepresentable()).isFalse();
            assertThat(mapped.fields().get(key("status")).rule())
                    .isEqualTo("EXACT_STATUS_DEFINITION_REQUIRED");
        }
    }

    @Test
    void shouldExportASignedOrVerifiedAppointment_asTheChartShowsItsStatus() throws Exception {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.APPOINTMENT_STATUSES
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(Map.of("id", "3", "status", "H", "description", "Here", "active", "1"));
        var here =
                new CdsExportCapturedTextRowDto(
                        "appointment_status", CdsExportPatientTableSourceReader.key(3), fields);
        var original = lookups();
        var lookups =
                new CdsExportAppointmentLookupsDto(
                        "a".repeat(64),
                        original.providers(),
                        Map.of("H", List.of(here)),
                        Map.of(),
                        Set.of());
        // Sign & Exit appends S, verifying appends V; the chart shows "Here/Signed" and
        // "Here/Verified" (ApptStatusData), with no status row of its own.
        for (var expected : Map.of("HS", "Here/Signed", "HV", "Here/Verified").entrySet()) {
            var appointment = baseline();
            appointment.put("status", expected.getKey());
            var mapped = mapper.map(27, appointment(appointment), lookups, ZONE);
            assertThat(mapped.nativeRowRepresentable()).isTrue();
            assertThat(mapped.value().status()).isEqualTo(expected.getValue());
            assertThat(mapped.fields().get(key("status")).rule())
                    .isEqualTo("CAPTURED_STATUS_DEFINITION_WITH_SIGNED_OR_VERIFIED_MARK");
        }
        // Any other second letter still needs an exact definition.
        var unknown = baseline();
        unknown.put("status", "HX");
        var mapped = mapper.map(27, appointment(unknown), lookups, ZONE);
        assertThat(mapped.nativeRowRepresentable()).isFalse();
        assertThat(mapped.fields().get(key("status")).rule())
                .isEqualTo("EXACT_STATUS_DEFINITION_REQUIRED");
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
    void shouldExportABookingNameThatIsNotThePatientsAndRemarks_asLabelledNotesLines()
            throws Exception {
        // The Edit Appointment form stores whatever its name box holds
        // (AppointmentUpdateRecord2Action: keyword), and remarks are filled by the SOAP schedule
        // service and OSCAR 19 data (its reminder mailer's "Emailed:" marks). Appointments has no
        // ResidualInfo, so each is a labelled line after the notes; neither holds the chart.
        var demographic = demographic("Fixture", "Synthetic", null);
        var fields = baseline();
        fields.put("notes", "Bring results");
        fields.put("name", "Fixture, Synthetic - needs interpreter");
        fields.put("remarks", "Emailed:2019-03-04");
        var mapped = mapper.map(27, appointment(fields), lookups(), ZONE, demographic);
        assertThat(mapped.nativeRowRepresentable()).isTrue();
        assertThat(mapped.value().notes())
                .isEqualTo(
                        "Bring results\nBooking name: Fixture, Synthetic - needs interpreter"
                                + "\nRemarks: Emailed:2019-03-04");
        assertThat(mapped.fields().get(key("name")).destination()).isEqualTo("AppointmentNotes");
        assertThat(mapped.fields().get(key("remarks")).rule())
                .isEqualTo(CdsExportAppointmentNativeMetadata.REMARKS_IN_NOTES);
        // With no notes the lines are the notes; the program a booking was made under (OSCAR 19
        // data) is scheduler context and holds no one.
        fields.put("notes", null);
        fields.put("name", "Fixture,Synthetic");
        fields.put("program_id", "10016");
        var remarks = mapper.map(27, appointment(fields), lookups(), ZONE, demographic);
        assertThat(remarks.value().notes()).isEqualTo("Remarks: Emailed:2019-03-04");
        assertThat(remarks.fields().get(key("program_id")).outcome())
                .isEqualTo(Outcome.OPERATIONAL);
        // A reason longer than its element is still explicit.
        fields.put("reason", "x".repeat(251));
        assertThat(mapper.map(27, appointment(fields), lookups(), ZONE, demographic)
                        .nativeRowRepresentable())
                .isFalse();
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
                            lookups().statuses(), Map.of(), Set.of());
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
                        "a".repeat(64),
                        Map.of("0é27", forged),
                        lookups().statuses(),
                        Map.of(),
                        Set.of());
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

    @Test
    void shouldClassifySchedulerAndBookingContextAsOperational_forUiBookedAppointment()
            throws Exception {
        var fields = baseline();
        var context =
                Map.of(
                        "style", "1",
                        "billing", "B",
                        "location", "Main Clinic",
                        "resources", "Room 2",
                        "urgency", "critical",
                        "type", "Follow-up visit",
                        "bookingSource", "OSCAR");
        fields.putAll(context);
        var mapped = mapper.map(27, appointment(fields), lookups(), ZONE);
        assertThat(mapped.nativeRowRepresentable()).isTrue();
        for (String column : context.keySet()) {
            assertThat(mapped.fields().get(key(column)).outcome()).isEqualTo(Outcome.OPERATIONAL);
            assertThat(mapped.fields().get(key(column)).rule())
                    .endsWith("_NOT_A_CDS_APPOINTMENT_FIELD");
        }
        assertThat(mapped.value().notes()).isEqualTo("Literal café 😀\r\nnotes");
        assertThat(mapped.value().purpose()).isEqualTo("Exact purpose");
    }

    @Test
    void shouldPrefixTheReasonWithTheScheduleLabel_whenTheReasonCodeNamesACapturedItem()
            throws Exception {
        var fields = baseline();
        fields.put("reason", " Cough for 3 days ");
        fields.put("reasonCode", "2");
        var mapped =
                mapper.map(27, appointment(fields), lookups(reasonItem("2", " Follow-Up ")), ZONE);
        assertThat(mapped.nativeRowRepresentable()).isTrue();
        assertThat(mapped.value().purpose()).isEqualTo("Follow-Up Cough for 3 days");
        assertThat(mapped.fields()).hasSize(49);
        assertThat(mapped.fields().get(key("reasonCode")).outcome()).isEqualTo(Outcome.MAPPED);
        assertThat(mapped.fields().get(key("reason")).destination())
                .isEqualTo("AppointmentPurpose");
        assertThat(mapped.fields().get(itemKey(2, "label")).outcome()).isEqualTo(Outcome.MAPPED);
        for (String column :
                List.of(
                        "active",
                        "createdBy",
                        "dateCreated",
                        "displayOrder",
                        "id",
                        "lookupListId",
                        "value"))
            assertThat(mapped.fields().get(itemKey(2, column)).outcome())
                    .isEqualTo(Outcome.OPERATIONAL);
        fields.put("reason", null);
        assertThat(
                        mapper.map(27, appointment(fields), lookups(reasonItem("2", "Follow-Up")), ZONE)
                                .value()
                                .purpose())
                .isEqualTo("Follow-Up");
    }

    @Test
    void shouldExportTheExactReasonAlone_whenTheScheduleShowsNoLabel() throws Exception {
        // "-1" is the add form's placeholder when no reason list exists, "99" is an id outside
        // the captured reasonCode list and item 1 is the seeded list's blank first entry.
        for (String code : List.of("-1", "99", "1")) {
            var fields = baseline();
            fields.put("reason", " Exact purpose ");
            fields.put("reasonCode", code);
            var mapped = mapper.map(27, appointment(fields), lookups(reasonItem("1", "")), ZONE);
            assertThat(mapped.nativeRowRepresentable()).isTrue();
            assertThat(mapped.value().purpose()).isEqualTo(" Exact purpose ");
            assertThat(mapped.fields().get(key("reasonCode")).outcome())
                    .isEqualTo(Outcome.OPERATIONAL);
            assertThat(mapped.fields().get(key("reason")).rule()).isEqualTo("EXACT_NATIVE_TEXT");
        }
    }

    @Test
    void shouldMoveTheReasonLabelToNotes_whenTheLabelledPurposeExceedsItsElement() throws Exception {
        var fields = baseline();
        fields.put("reason", "r".repeat(80));
        fields.put("reasonCode", "2");
        fields.put("notes", "Bring results");
        var fits =
                mapper.map(27, appointment(fields), lookups(reasonItem("2", "L".repeat(169))), ZONE);
        assertThat(fits.value().purpose()).hasSize(250);
        // One character more than AppointmentPurpose holds: nothing is cut and nobody is held.
        var moved =
                mapper.map(27, appointment(fields), lookups(reasonItem("2", "L".repeat(170))), ZONE);
        assertThat(moved.nativeRowRepresentable()).isTrue();
        assertThat(moved.value().purpose()).isEqualTo("r".repeat(80));
        assertThat(moved.value().notes())
                .isEqualTo("Reason: " + "L".repeat(170) + "\nBring results");
        assertThat(moved.fields().get(key("reasonCode")).rule())
                .isEqualTo(CdsExportAppointmentFieldReconciler.LABEL_IN_NOTES);
        assertThat(moved.fields().get(key("reason")).outcome()).isEqualTo(Outcome.MAPPED);
    }

    @Test
    void shouldRequireReview_whenTheReferencedReasonItemHasNoLabel() throws Exception {
        var fields = baseline();
        fields.put("reasonCode", "2");
        var mapped = mapper.map(27, appointment(fields), lookups(reasonItem("2", null)), ZONE);
        assertThat(mapped.nativeRowRepresentable()).isFalse();
        assertThat(mapped.fields().get(key("reasonCode")).rule())
                .isEqualTo("REASON_CODE_ITEM_HAS_NO_LABEL");
    }

    @Test
    void shouldRefuseAReasonItemWhoseIdentityDiffersFromTheStoredCode_beforeMappingIt() {
        var fields = baseline();
        fields.put("reasonCode", "2");
        var forged =
                new CdsExportAppointmentLookupsDto(
                        "a".repeat(64),
                        Map.of("0é27", provider()),
                        Map.of("Q1", List.of(status())),
                        Map.of("2", List.of(reasonItem("3", "Other"))),
                        Set.of());
        assertThatThrownBy(() -> mapper.map(27, appointment(fields), forged, ZONE))
                .hasMessage("CDS_EXPORT_APPOINTMENT_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldAcceptOnlyTheExactDisplayFormsCarlosStores_forThePatientName() throws Exception {
        var demographic = demographic("Fixture", "Synthetic", "Syn");
        // Add form (AppointmentAddRecord2Action), then Demographic.getFormattedName() from the
        // edit form's patient search and the booking web service, with and without the alias.
        for (String name :
                List.of("Fixture,Synthetic", "Fixture, Synthetic", "Fixture, Synthetic (Syn)")) {
            var fields = baseline();
            fields.put("name", name);
            var mapped = mapper.map(27, appointment(fields), lookups(), ZONE, demographic);
            assertThat(mapped.nativeRowRepresentable()).isTrue();
            assertThat(mapped.fields().get(key("name")).rule())
                    .isEqualTo("EXACT_CURRENT_PATIENT_DISPLAY_NOT_APPOINTMENT_NOTE");
        }
        for (String name :
                List.of(
                        "fixture,synthetic",
                        "Fixture ,Synthetic",
                        "Fixture,Synthetic (Syn)",
                        "Synthetic Fixture",
                        "Maiden,Synthetic")) {
            var fields = baseline();
            fields.put("name", name);
            // Any other text is the booking's own name text: a labelled line of the notes.
            var mapped = mapper.map(27, appointment(fields), lookups(), ZONE, demographic);
            assertThat(mapped.nativeRowRepresentable()).isTrue();
            assertThat(mapped.value().notes())
                    .endsWith(CdsExportAppointmentNativeMetadata.NAME_LABEL + name);
            assertThat(mapped.fields().get(key("name")).rule())
                    .isEqualTo(CdsExportAppointmentNativeMetadata.NAME_IN_NOTES);
        }
    }

    @Test
    void shouldAcceptAnEarlierNameOfTheSamePatient_fromItsCapturedArchiveSnapshot()
            throws Exception {
        var earlier =
                CdsExportAppointmentNativeMetadata.archivedDisplays(
                        27, demographicArchive(5, 27, "Maiden", "Synthetic"));
        assertThat(earlier).containsExactlyInAnyOrder("Maiden,Synthetic", "Maiden, Synthetic");
        var fields = baseline();
        fields.put("name", "Maiden,Synthetic");
        var lookups =
                new CdsExportAppointmentLookupsDto(
                        "a".repeat(64),
                        Map.of("0é27", provider()),
                        Map.of("Q1", List.of(status())),
                        Map.of(),
                        earlier);
        var mapped =
                mapper.map(
                        27,
                        appointment(fields),
                        lookups,
                        ZONE,
                        demographic("Fixture", "Synthetic", null));
        assertThat(mapped.nativeRowRepresentable()).isTrue();
        assertThat(mapped.fields().get(key("name")).rule())
                .isEqualTo("EXACT_EARLIER_PATIENT_DISPLAY_FROM_CAPTURED_ARCHIVE_NOT_APPOINTMENT_NOTE");
        assertThatThrownBy(
                        () ->
                                CdsExportAppointmentNativeMetadata.archivedDisplays(
                                        27, demographicArchive(6, 28, "Other", "Patient")))
                .hasMessage("CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_SCOPE_INVALID");
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
                "a".repeat(64),
                Map.of("0é27", provider()),
                Map.of("Q1", List.of(status())),
                Map.of(),
                Set.of());
    }

    /** A reasonCode list item as the seeded list stores it; {@code label} may be null. */
    static CdsExportCapturedTextRowDto reasonItem(String id, String label) {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.APPOINTMENT_REASON_CODES
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "id",
                        id,
                        "lookupListId",
                        "1",
                        "value",
                        label == null ? "" : label.trim(),
                        "displayOrder",
                        id,
                        "active",
                        "1",
                        "createdBy",
                        "oscar",
                        "dateCreated",
                        "2026-07-08 19:53:16"));
        fields.put("label", label);
        return new CdsExportCapturedTextRowDto(
                "LookupListItem",
                CdsExportPatientTableSourceReader.key(Integer.parseInt(id)),
                fields);
    }

    static CdsExportAppointmentLookupsDto lookups(CdsExportCapturedTextRowDto reasonItem) {
        return new CdsExportAppointmentLookupsDto(
                "a".repeat(64),
                Map.of("0é27", provider()),
                Map.of("Q1", List.of(status())),
                Map.of(reasonItem.fields().get("id"), List.of(reasonItem)),
                Set.of());
    }

    static CdsExportCapturedTextRowDto demographic(String last, String first, String alias) {
        var fields = new TreeMap<String, String>();
        io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto.COLUMNS.forEach(
                column -> fields.put(column, null));
        fields.put("demographic_no", "27");
        fields.put("last_name", last);
        fields.put("first_name", first);
        fields.put("alias", alias);
        return new CdsExportCapturedTextRowDto("demographic", "0000000027", fields);
    }

    static CdsExportCapturedTextRowDto demographicArchive(
            int id, int patient, String last, String first) {
        var fields = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_ARCHIVE
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.put("id", Integer.toString(id));
        fields.put("demographic_no", Integer.toString(patient));
        fields.put("last_name", last);
        fields.put("first_name", first);
        return new CdsExportCapturedTextRowDto(
                "demographicArchive", CdsExportPatientTableSourceReader.key(id), fields);
    }

    static CdsExportNativeFieldKeyDto itemKey(int id, String column) {
        return new CdsExportNativeFieldKeyDto(
                "LookupListItem", CdsExportPatientTableSourceReader.key(id), column);
    }

    static CdsExportNativeFieldKeyDto key(String column) {
        return new CdsExportNativeFieldKeyDto(
                "appointment", CdsExportPatientTableSourceReader.key(51), column);
    }
}
