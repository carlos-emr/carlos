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
package io.github.carlos_emr.carlos.prescript.data;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Severity filtering and both sides of a clinically relevant interaction. */
@Tag("unit")
@Tag("rx")
class RxInteractionWarningsUnitTest {
    @Test
    void shouldMarkBothStagedDrugs_whenDrugRefReportsAnInteraction() {
        var first = prescription(1, "J01MA02");
        var second = prescription(2, "R03DA04");
        var warning = interaction("3");
        var found = RxInteractionWarnings.forStash(new RxPrescriptionData.Prescription[]{first, second},
                new RxDrugData.Interaction[]{warning}, 3);
        assertThat(found).extracting(RxInteractionWarnings.Warning::cardId).containsExactly("1", "2");
        assertThat(found).allMatch(item -> item.severity().equals("major"));
    }

    @Test
    void shouldHonorSeverityThresholds_withoutHidingUnknownSignificance() {
        var stash = new RxPrescriptionData.Prescription[]{prescription(1, "J01MA02")};
        var interactions = new RxDrugData.Interaction[]{interaction("1"), interaction("2"), interaction("3"), interaction(" ")};
        assertThat(RxInteractionWarnings.forStash(stash, interactions, 2))
                .extracting(RxInteractionWarnings.Warning::severity).containsExactly("moderate", "major", "unknown");
        assertThat(RxInteractionWarnings.forStash(stash, interactions, 4)).isEmpty();
        assertThatThrownBy(() -> RxInteractionWarnings.forStash(stash, null, 1)).isInstanceOf(IllegalStateException.class);
    }

    private RxPrescriptionData.Prescription prescription(int id, String code) {
        var rx = mock(RxPrescriptionData.Prescription.class);
        when(rx.getRandomId()).thenReturn((long) id);
        when(rx.getAtcCode()).thenReturn(code);
        return rx;
    }

    @Test
    void shouldRefuseIncompleteInteractions_insteadOfReturningAnAllClear() {
        var incomplete = interaction("3");
        incomplete.affectingatc = null;
        assertThatThrownBy(() -> RxInteractionWarnings.forStash(
                new RxPrescriptionData.Prescription[]{prescription(1, "J01MA02")},
                new RxDrugData.Interaction[]{incomplete}, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldUseDrugCodes_whenReferenceNamesAreMissing() {
        var unnamed = interaction("3");
        unnamed.affectingdrug = null;
        unnamed.affecteddrug = " ";
        assertThat(RxInteractionWarnings.forStash(new RxPrescriptionData.Prescription[]{prescription(1, "J01MA02")},
                new RxDrugData.Interaction[]{unnamed}, 1)).singleElement()
                .extracting(RxInteractionWarnings.Warning::text).asString().contains("J01MA02 / R03DA04");
    }

    private RxDrugData.Interaction interaction(String significance) {
        var interaction = new RxDrugData().new Interaction();
        interaction.affectingatc = "J01MA02";
        interaction.affectedatc = "R03DA04";
        interaction.affectingdrug = "Ciprofloxacin";
        interaction.affecteddrug = "Theophylline";
        interaction.significance = significance;
        return interaction;
    }
}
