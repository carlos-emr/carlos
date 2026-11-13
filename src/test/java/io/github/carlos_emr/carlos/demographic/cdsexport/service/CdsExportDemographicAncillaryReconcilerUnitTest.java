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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit") @Tag("cds-export")
class CdsExportDemographicAncillaryReconcilerUnitTest {
    private static final String KEY = "0000000027", HASH = "a".repeat(64);
    private final CdsExportDemographicAncillaryReconciler reconciler = new CdsExportDemographicAncillaryReconciler();
    private static CdsExportNativeFieldKeyDto key(String column) { return new CdsExportNativeFieldKeyDto("demographic", KEY, column); }
    private static CdsExportCapturedTextRowDto row(String column, String value) {
        var fields = new LinkedHashMap<String, String>(); fields.put("demographic_no", "27"); fields.put(column, value);
        return new CdsExportCapturedTextRowDto("demographic", KEY, fields);
    }
    private static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>(); var cells = new ArrayList<CdsNativeRowState.Cell>();
        row.fields().forEach((column, value) -> {
            columns.add(new CdsNativeRowDigest.Column(column, "VARCHAR", 255, 0));
            byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
            cells.add(bytes == null ? new CdsNativeRowState.Cell(true, 0, null)
                    : new CdsNativeRowState.Cell(false, bytes.length, HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes))));
        });
        var state = new CdsNativeRowState("demographic", "27", columns, cells);
        return new CdsExportNativeRowReceiptDto("demographic", "27", KEY, state.digest(), state);
    }
    private static CdsExportDemographicRetainedCoreReconciler.Result prior(Set<CdsExportNativeFieldKeyDto> deferred,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        return new CdsExportDemographicRetainedCoreReconciler.Result(HASH, "parent", Set.of("decided"), Set.of("remaining"),
                fields, deferred, Set.of(), List.of(new CdsExportRetainedSectionWriter.Change("ChartNumber[1]", Set.of(), xml -> xml.text("fixture"))));
    }
    private CdsExportDemographicRetainedCoreReconciler.Result reconcile(CdsExportCapturedTextRowDto row) throws Exception {
        var deferred = new HashSet<CdsExportNativeFieldKeyDto>(); row.fields().keySet().stream().filter(column -> !column.equals("demographic_no")).forEach(column -> deferred.add(key(column)));
        return reconciler.reconcile(prior(deferred, Map.of()), 27, row, witness(row), () -> {});
    }
    @Test void shouldPreserveNullAndEmptyAsDistinctAbsentValues_withoutClaimingRemainingSourceCompleteness() throws Exception {
        for (String value : Arrays.asList(null, "")) {
            var result = reconcile(row("alias", value));
            assertThat(result.fields().get(key("alias")).presence()).isEqualTo(value == null ? Presence.SQL_NULL : Presence.EMPTY);
            assertThat(result.fields().get(key("alias")).outcome()).isEqualTo(Outcome.ABSENT);
            assertThat(result.deferredNativeFields()).isEmpty(); assertThat(result.deferredSourceFields()).containsExactly("remaining");
            assertThat(result.demographicComplete()).isFalse(); assertThat(result.coreChanges()).hasSize(1);
        }
    }
    @Test void shouldCarryEveryPopulatedAncillaryValue_includingWhitespaceAndFalseLookingCodes() throws Exception {
        // Values the demographic screens write that CDS 5.2 Demographics has no element for travel in the native
        // demographic NewCategory record; they never hold the chart or withhold the retained XML changes.
        for (String column : List.of("alias", "anonymous", "children", "citizenship", "consentToUseEmailForCare", "country_of_origin",
                "end_date", "family_doctor", "gender", "genderId", "myOscarUserName", "newsletter", "pcn_indicator",
                "pref_name", "previousAddress", "pronoun", "pronounId", "sourceOfIncome")) {
            for (String value : List.of("0", "false", " ", "Clinical 😀 & value")) {
                var result = reconcile(row(column, value));
                assertThat(result.fields().get(key(column))).isEqualTo(new CdsExportFieldAssessmentDto(Presence.VALUE,
                        Outcome.MAPPED, CdsExportDemographicSupportingMapper.DESTINATION, CdsExportDemographicSupportingMapper.CARRIED_RULE));
                assertThat(result.coreRepresentable()).isTrue(); assertThat(result.proposedChanges()).hasSize(1);
            }
        }
    }
    @Test void shouldHoldOnlyAnAncillaryValueNoXmlDocumentCanCarry_forAncillaryReconcilerContract() throws Exception {
        var result = reconcile(row("pronoun", "she\uFFFF"));
        assertThat(result.fields().get(key("pronoun")).outcome()).isEqualTo(Outcome.REQUIRES_REVIEW);
        assertThat(result.coreRepresentable()).isFalse(); assertThat(result.proposedChanges()).isEmpty();
        assertThatThrownBy(result::coreChanges).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CORE_REQUIRES_REVIEW");
    }
    @Test void shouldAccountForMasterRecordPlaceholders_whileStillHoldingAChosenValue() throws Exception {
        // D5: add-form-personal.jsp preselects newsletter "Unknown" and offers country of origin "-1" ("Not set");
        // every save writes them. They are not patient facts; a chosen value has no CDS element and is carried.
        for (var placeholder : List.of(Map.entry("newsletter", "Unknown"), Map.entry("country_of_origin", "-1"))) {
            var result = reconcile(row(placeholder.getKey(), placeholder.getValue()));
            assertThat(result.fields().get(key(placeholder.getKey())).outcome()).isEqualTo(Outcome.OPERATIONAL);
            assertThat(result.fields().get(key(placeholder.getKey())).rule()).isEqualTo("UI_PLACEHOLDER_NOT_A_PATIENT_FACT");
            assertThat(result.coreRepresentable()).isTrue();
        }
        for (var chosen : List.of(Map.entry("newsletter", "Paper"), Map.entry("country_of_origin", "CA"))) {
            var result = reconcile(row(chosen.getKey(), chosen.getValue()));
            assertThat(result.fields().get(key(chosen.getKey())).outcome()).isEqualTo(Outcome.MAPPED);
            assertThat(result.fields().get(key(chosen.getKey())).destination()).isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        }
    }
    @Test void shouldCarryPracticeAdministrativeDates_inTheNativeDemographicRecord() throws Exception {
        // D3: "Date Joined" (add-form-clinical.jsp) and the health card "EFF Date" are user-entered
        // practice-administrative dates with no CDS 5.2 Demographics element. Updated: they used to be decided
        // ADMINISTRATIVE_NOT_EXPORTED; they now travel in the native demographic NewCategory record.
        for (String column : List.of("date_joined", "eff_date")) {
            var result = reconcile(row(column, "2009-04-01"));
            assertThat(result.fields().get(key(column)).outcome()).isEqualTo(Outcome.MAPPED);
            assertThat(result.fields().get(key(column)).destination()).isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
            assertThat(result.coreRepresentable()).isTrue();
            assertThat(reconcile(row(column, null)).fields().get(key(column)).outcome()).isEqualTo(Outcome.ABSENT);
        }
    }
    @Test void shouldAccountForCapturedOperationalIdentity_withoutInventingClinicalHistory() throws Exception {
        // Updated for D3: date_joined is no longer operational identity; it has its own administrative rule above.
        for (String column : List.of("lastUpdateDate", "lastUpdateUser", "cds_identity_hin", "cds_identity_issuer")) {
            var result = reconcile(row(column, "fixture"));
            assertThat(result.fields().get(key(column)).outcome()).isEqualTo(Outcome.OPERATIONAL);
            assertThat(result.fields().get(key(column)).destination()).isEmpty();
            assertThat(result.fields().get(key("demographic_no")).outcome()).isEqualTo(Outcome.OPERATIONAL);
        }
    }
    @Test void shouldRefuseAnUndecidedMappedColumn_insteadOfAssumingAnEmptyValueWasHandled() {
        var current = row("phone", "");
        assertThatThrownBy(() -> reconciler.reconcile(prior(Set.of(key("phone")), Map.of()), 27, current, witness(current), () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ANCILLARY_SCOPE_INVALID");
    }
    @Test void shouldRefuseAnOmittedHandoff_evenWhenTheAncillaryValueIsEmpty() {
        var current = row("alias", "");
        assertThatThrownBy(() -> reconciler.reconcile(prior(Set.of(), Map.of()), 27, current, witness(current), () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ANCILLARY_SCOPE_INVALID");
    }
    @Test void shouldRefuseDuplicateOwnership_insteadOfOverwritingAnEarlierDecision() {
        var current = row("alias", ""); var fields = Map.of(key("alias"), new CdsExportFieldAssessmentDto(Presence.EMPTY, Outcome.ABSENT, "", "EARLIER"));
        assertThatThrownBy(() -> reconciler.reconcile(prior(Set.of(key("alias")), fields), 27, current, witness(current), () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ANCILLARY_SCOPE_INVALID");
    }
    @Test void shouldRejectAnotherPatient_andChangedCapturedBytes() {
        var current = row("alias", "fixture"); var before = row("alias", "different"); var prior = prior(Set.of(key("alias")), Map.of());
        assertThatThrownBy(() -> reconciler.reconcile(prior, 28, current, witness(current), () -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_ANCILLARY_SCOPE_INVALID");
        assertThatThrownBy(() -> reconciler.reconcile(prior, 27, current, witness(before), () -> {})).hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
    }
    @Test void shouldPropagateCancellation_beforeReturningAnyAssessment() {
        var current = row("alias", "");
        assertThatThrownBy(() -> reconciler.reconcile(prior(Set.of(key("alias")), Map.of()), 27, current, witness(current),
                () -> { throw new java.util.concurrent.CancellationException("CANCELLED"); })).isInstanceOf(java.util.concurrent.CancellationException.class);
    }
    @Test void shouldRefuseInventedBaseRowKeys_evenWhenTheirValuesAreEmpty() {
        var current = row("alias", ""); var fields = Map.of(key("invented"), new CdsExportFieldAssessmentDto(Presence.EMPTY, Outcome.ABSENT, "", "EARLIER"));
        assertThatThrownBy(() -> reconciler.reconcile(prior(Set.of(key("alias")), fields), 27, current, witness(current), () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ANCILLARY_SCOPE_INVALID");
    }
    @Test void shouldRejectFalsePresenceClaims_fromAnEarlierMappedField() {
        var current = row("phone", "613-555-0100");
        var fields = Map.of(key("phone"), new CdsExportFieldAssessmentDto(Presence.EMPTY, Outcome.ABSENT, "", "EARLIER"));
        assertThatThrownBy(() -> reconciler.reconcile(prior(Set.of(), fields), 27, current, witness(current), () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ANCILLARY_SCOPE_INVALID");
    }
    @Test void shouldRetainAnEarlierMappedField_andItsExplicitDestination() throws Exception {
        var current = row("phone", "613-555-0100");
        var mapped = new CdsExportFieldAssessmentDto(Presence.VALUE, Outcome.MAPPED, "PhoneNumber", "EARLIER");
        var result = reconciler.reconcile(prior(Set.of(), Map.of(key("phone"), mapped)), 27, current, witness(current), () -> {});
        assertThat(result.fields().get(key("phone"))).isEqualTo(mapped); assertThat(result.coreChanges()).hasSize(1);
    }
    @Test void shouldWithholdACompleteResult_whenCancellationOccursAfterTheWitnessWasRead() {
        var current = row("alias", ""); var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(() -> reconciler.reconcile(prior(Set.of(key("alias")), Map.of()), 27, current, witness(current),
                () -> { if (calls.incrementAndGet() == 9) throw new java.util.concurrent.CancellationException("CANCELLED"); }))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(calls.get()).isEqualTo(9);
    }
    @Test void shouldCloseOnlyTheExactBaseRow_whenThereAreNoRemainingSourceOrAssociatedHandoffs() throws Exception {
        var current = row("alias", "");
        var prior = new CdsExportDemographicRetainedCoreReconciler.Result(HASH, "parent", Set.of(), Set.of(), Map.of(), Set.of(key("alias")), Set.of(), List.of());
        var result = reconciler.reconcile(prior, 27, current, witness(current), () -> {});
        assertThat(result.demographicComplete()).isTrue(); assertThat(result.fields()).hasSize(2);
    }
    @Test void shouldRetainOtherTableHandoffs_andNeverDeclareTheWholeDemographicComplete() throws Exception {
        var current = row("alias", ""); var other = new CdsExportNativeFieldKeyDto("demographiccust", KEY, "cust1");
        var result = reconciler.reconcile(prior(Set.of(key("alias"), other), Map.of()), 27, current, witness(current), () -> {});
        assertThat(result.deferredNativeFields()).containsExactly(other); assertThat(result.demographicComplete()).isFalse();
    }
}
