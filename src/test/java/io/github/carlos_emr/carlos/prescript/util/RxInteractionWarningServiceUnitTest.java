/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.Result;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.Severity;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.StagedDrug;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.Status;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.Warning;

/**
 * Mapping DrugRef's Holbrook interaction records onto the staged Rx cards (issue #4506).
 *
 * <p>The fixture pair is the one {@code rx-interactions-renal-luc} stages on the packaged
 * install: ciprofloxacin (J01MA02) and theophylline (R03DA04), significance 3 in DrugRef.</p>
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("rx")
@DisplayName("RxInteractionWarningService")
class RxInteractionWarningServiceUnitTest {

    private static final String CIPRO = "J01MA02";
    private static final String THEOPHYLLINE = "R03DA04";
    private static final String WARFARIN = "B01AA03";

    /** Records every lookup and answers with fixed records. */
    private static final class FixedLookup implements RxInteractionWarningService.InteractionLookup {
        private final List<Map<?, ?>> answer;
        private final List<List<String>> calls = new ArrayList<>();

        FixedLookup(List<Map<?, ?>> answer) {
            this.answer = answer;
        }

        @Override
        public List<Map<?, ?>> interactionsAmong(List<String> atcCodes) {
            calls.add(atcCodes);
            return answer;
        }
    }

    private static Map<?, ?> interaction(String affectingAtc, String affectingDrug, String effect,
                                         String affectedAtc, String affectedDrug, String significance) {
        // Hashtable, as the XML-RPC client builds it: no null values.
        Hashtable<String, Object> entry = new Hashtable<>();
        entry.put("affectingatc", affectingAtc);
        entry.put("affectingdrug", affectingDrug);
        entry.put("effect", effect);
        entry.put("affectedatc", affectedAtc);
        entry.put("affecteddrug", affectedDrug == null ? "" : affectedDrug);
        entry.put("significance", significance);
        entry.put("evidence", "G");
        entry.put("comment", "Monitor theophylline levels.");
        return entry;
    }

    private static Map<?, ?> ciproTheophylline(String significance) {
        return interaction(CIPRO, "CIPROFLOXACIN", "A", THEOPHYLLINE, "THEOPHYLLINE", significance);
    }

    @Test
    @DisplayName("should warn both staged cards of a major interaction between them")
    void shouldWarnBothCards_whenTwoStagedDrugsInteract() {
        FixedLookup lookup = new FixedLookup(List.of(ciproTheophylline("3")));

        Result result = new RxInteractionWarningService(lookup).check(0,
                List.of(new StagedDrug(11L, CIPRO), new StagedDrug(22L, THEOPHYLLINE)), List.of());

        assertThat(result.status()).isEqualTo(Status.CHECKED);
        assertThat(result.warnings())
                .extracting(Warning::randomId, Warning::severity, Warning::partner, Warning::summary, Warning::evidence)
                .containsExactlyInAnyOrder(
                        tuple(11L, Severity.MAJOR, "THEOPHYLLINE", "CIPROFLOXACIN augments THEOPHYLLINE", "good"),
                        tuple(22L, Severity.MAJOR, "CIPROFLOXACIN", "CIPROFLOXACIN augments THEOPHYLLINE", "good"));
        assertThat(result.warnings()).allSatisfy(warning ->
                assertThat(warning.comment()).isEqualTo("Monitor theophylline levels."));
        assertThat(lookup.calls).containsExactly(List.of(CIPRO, THEOPHYLLINE));
    }

    @Test
    @DisplayName("should warn a staged card about a current medication without warning on a card that does not exist")
    void shouldWarnStagedCard_whenItInteractsWithACurrentMedication() {
        FixedLookup lookup = new FixedLookup(List.of(ciproTheophylline("2")));

        Result result = new RxInteractionWarningService(lookup).check(0,
                List.of(new StagedDrug(11L, CIPRO)), List.of(THEOPHYLLINE));

        assertThat(result.warnings()).extracting(Warning::randomId, Warning::severity, Warning::partner)
                .containsExactly(tuple(11L, Severity.MODERATE, "THEOPHYLLINE"));
        assertThat(lookup.calls).containsExactly(List.of(CIPRO, THEOPHYLLINE));
    }

    @Test
    @DisplayName("should not report interactions between two current medications")
    void shouldIgnoreInteraction_betweenCurrentMedicationsOnly() {
        FixedLookup lookup = new FixedLookup(List.of(
                interaction(WARFARIN, "WARFARIN", "A", THEOPHYLLINE, "THEOPHYLLINE", "3")));

        Result result = new RxInteractionWarningService(lookup).check(0,
                List.of(new StagedDrug(11L, CIPRO)), List.of(WARFARIN, THEOPHYLLINE));

        assertThat(result.status()).isEqualTo(Status.CHECKED);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("should hide interactions below the warning level and keep ungraded ones")
    void shouldFilterWarnings_byWarningLevel() {
        FixedLookup lookup = new FixedLookup(List.of(
                ciproTheophylline("2"),
                interaction(CIPRO, "CIPROFLOXACIN", "A", WARFARIN, "WARFARIN", "3"),
                interaction(WARFARIN, "WARFARIN", " ", CIPRO, "CIPROFLOXACIN", " ")));

        Result result = new RxInteractionWarningService(lookup).check(RxInteractionWarningLevelResolver.HIGH,
                List.of(new StagedDrug(11L, CIPRO)), List.of(THEOPHYLLINE, WARFARIN));

        assertThat(result.level()).isEqualTo(RxInteractionWarningLevelResolver.HIGH);
        assertThat(result.warnings()).extracting(Warning::severity, Warning::partner, Warning::summary)
                .containsExactly(
                        tuple(Severity.MAJOR, "WARFARIN", "CIPROFLOXACIN augments WARFARIN"),
                        tuple(Severity.UNKNOWN, "WARFARIN", "WARFARIN has an unknown effect on CIPROFLOXACIN"));
    }

    @Test
    @DisplayName("should not warn about a pair DrugRef records as having no effect")
    void shouldIgnoreInteraction_whenDrugRefRecordsNoEffect() {
        // As shipped: CIPROFLOXACIN N CALCIUM, no significance, evidence poor.
        FixedLookup lookup = new FixedLookup(List.of(
                interaction(CIPRO, "CIPROFLOXACIN", "N", "A12AA", "CALCIUM", ""),
                interaction("A12AA", "CALCIUM", "n", CIPRO, "CIPROFLOXACIN", "")));

        Result result = new RxInteractionWarningService(lookup).check(0,
                List.of(new StagedDrug(11L, CIPRO)), List.of("A12AA"));

        assertThat(result.status()).isEqualTo(Status.CHECKED);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("should not ask DrugRef when the warning level is none")
    void shouldReportOff_whenLevelIsNone() {
        FixedLookup lookup = new FixedLookup(List.of(ciproTheophylline("3")));

        Result result = new RxInteractionWarningService(lookup).check(RxInteractionWarningLevelResolver.NONE,
                List.of(new StagedDrug(11L, CIPRO)), List.of(THEOPHYLLINE));

        assertThat(result.status()).isEqualTo(Status.OFF);
        assertThat(result.warnings()).isEmpty();
        assertThat(lookup.calls).isEmpty();
    }

    @Test
    @DisplayName("should report the check unavailable, not clean, when DrugRef fails")
    void shouldReportUnavailable_whenLookupThrows() {
        RxInteractionWarningService service = new RxInteractionWarningService(codes -> {
            throw new ConnectException("Connection refused");
        });

        Result result = service.check(0, List.of(new StagedDrug(11L, CIPRO)), List.of(THEOPHYLLINE));

        assertThat(result.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("should report the check unavailable when DrugRef gives no answer at all")
    void shouldReportUnavailable_whenLookupAnswersNull() {
        Result result = new RxInteractionWarningService(codes -> null)
                .check(0, List.of(new StagedDrug(11L, CIPRO)), List.of(THEOPHYLLINE));

        assertThat(result.status()).isEqualTo(Status.UNAVAILABLE);
    }

    @Test
    @DisplayName("should report a staged drug with no ATC code as not checked rather than clean")
    void shouldReportUnchecked_whenAStagedDrugHasNoAtcCode() {
        FixedLookup lookup = new FixedLookup(List.of(ciproTheophylline("3")));
        RxInteractionWarningService service = new RxInteractionWarningService(lookup);

        Result checked = service.check(0,
                List.of(new StagedDrug(11L, CIPRO), new StagedDrug(33L, null), new StagedDrug(44L, " ")), List.of(THEOPHYLLINE));
        Result alone = service.check(0, List.of(new StagedDrug(33L, "")), List.of(THEOPHYLLINE));
        Result off = service.check(RxInteractionWarningLevelResolver.NONE, List.of(new StagedDrug(33L, null)), List.of());
        Result failed = new RxInteractionWarningService(codes -> null)
                .check(0, List.of(new StagedDrug(11L, CIPRO), new StagedDrug(33L, null)), List.of(THEOPHYLLINE));

        assertThat(checked.status()).isEqualTo(Status.CHECKED);
        assertThat(checked.unchecked()).containsExactly(33L, 44L);
        assertThat(checked.warnings()).extracting(Warning::randomId).containsExactly(11L);
        assertThat(alone.status()).isEqualTo(Status.CHECKED);
        assertThat(alone.unchecked()).containsExactly(33L);
        // Off and unavailable say nothing card by card: the page shows nothing, or the global notice.
        assertThat(off.unchecked()).isEmpty();
        assertThat(failed.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(failed.unchecked()).isEmpty();
    }

    @Test
    @DisplayName("should not ask DrugRef when there is no second drug to compare")
    void shouldSkipLookup_whenFewerThanTwoDistinctCodes() {
        FixedLookup lookup = new FixedLookup(List.of(ciproTheophylline("3")));
        RxInteractionWarningService service = new RxInteractionWarningService(lookup);

        Result single = service.check(0, List.of(new StagedDrug(11L, CIPRO)), List.of(CIPRO, " "));
        Result noCodes = service.check(0, List.of(new StagedDrug(11L, null), new StagedDrug(22L, "")), List.of(THEOPHYLLINE));
        Result noCards = service.check(0, List.of(), List.of(CIPRO, THEOPHYLLINE));

        assertThat(List.of(single, noCodes, noCards)).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(Status.CHECKED);
            assertThat(result.warnings()).isEmpty();
        });
        assertThat(lookup.calls).isEmpty();
    }

    @Test
    @DisplayName("should send each ATC code once and report a repeated interaction once")
    void shouldDeduplicateCodesAndWarnings_whenDrugsRepeat() {
        FixedLookup lookup = new FixedLookup(List.of(ciproTheophylline("3"), ciproTheophylline("3")));

        Result result = new RxInteractionWarningService(lookup).check(0,
                List.of(new StagedDrug(11L, " " + CIPRO + " "), new StagedDrug(12L, CIPRO)), List.of(THEOPHYLLINE, CIPRO));

        assertThat(lookup.calls).containsExactly(List.of(CIPRO, THEOPHYLLINE));
        assertThat(result.warnings()).extracting(Warning::randomId).containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    @DisplayName("should never report a drug interacting with itself")
    void shouldSkipInteraction_whenBothSidesAreTheCardsDrug() {
        FixedLookup lookup = new FixedLookup(List.of(interaction(CIPRO, "CIPROFLOXACIN", "A", CIPRO, "CIPROFLOXACIN", "3")));

        Result result = new RxInteractionWarningService(lookup).check(0,
                List.of(new StagedDrug(11L, CIPRO)), List.of(THEOPHYLLINE));

        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("should name the partner by its ATC code when DrugRef gives no drug name")
    void shouldNamePartnerByAtcCode_whenDrugNameIsBlank() {
        FixedLookup lookup = new FixedLookup(List.of(interaction(CIPRO, "CIPROFLOXACIN", "I", THEOPHYLLINE, null, "1")));

        Result result = new RxInteractionWarningService(lookup).check(0,
                List.of(new StagedDrug(11L, CIPRO)), List.of(THEOPHYLLINE));

        assertThat(result.warnings()).extracting(Warning::severity, Warning::partner, Warning::summary)
                .containsExactly(tuple(Severity.MINOR, THEOPHYLLINE, "CIPROFLOXACIN inhibits " + THEOPHYLLINE));
    }

    @Test
    @DisplayName("should order each card's warnings most serious first")
    void shouldOrderWarnings_mostSeriousFirst() {
        FixedLookup lookup = new FixedLookup(List.of(
                interaction(CIPRO, "CIPROFLOXACIN", "a", WARFARIN, "WARFARIN", "1"),
                ciproTheophylline("3")));

        Result result = new RxInteractionWarningService(lookup).check(0,
                List.of(new StagedDrug(11L, CIPRO)), List.of(WARFARIN, THEOPHYLLINE));

        assertThat(result.warnings()).extracting(Warning::severity)
                .containsExactly(Severity.MAJOR, Severity.MINOR);
        assertThat(result.warnings().get(1).summary()).isEqualTo("CIPROFLOXACIN augments (no clinical effect) WARFARIN");
    }

    @Test
    @DisplayName("should read a numeric significance as well as a textual one")
    void shouldGradeSeverity_fromNumericSignificance() {
        assertThat(Severity.of(3)).isEqualTo(Severity.MAJOR);
        assertThat(Severity.of("2")).isEqualTo(Severity.MODERATE);
        assertThat(Severity.of(" 1 ")).isEqualTo(Severity.MINOR);
        assertThat(Severity.of(null)).isEqualTo(Severity.UNKNOWN);
        assertThat(Severity.of("7")).isEqualTo(Severity.UNKNOWN);
    }
}
