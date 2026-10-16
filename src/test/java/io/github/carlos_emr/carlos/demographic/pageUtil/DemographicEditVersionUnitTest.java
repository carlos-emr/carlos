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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.demographic.pageUtil.DemographicEditVersion.Difference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DemographicEditVersion}: the Master Record form's record version changes
 * whenever a value the form writes changes (issue #4225), and the conflict view lists exactly
 * the fields a refused form differs in.
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicEditVersion")
class DemographicEditVersionUnitTest {

    private static Demographic patient() {
        Demographic demographic = new Demographic(123);
        demographic.setLastName("FAKE-PATIENT");
        demographic.setFirstName("Check");
        demographic.setCity("Toronto");
        demographic.setPhone("");
        demographic.setLastUpdateUser("999998");
        demographic.setLastUpdateDate(new Date(1_700_000_000_000L));
        return demographic;
    }

    private static Map<String, String> extensions(String cell) {
        Map<String, String> rows = new HashMap<>();
        rows.put("demo_cell", cell);
        rows.put("demo_cell_id", "77");
        return rows;
    }

    private static DemographicCust cust(String alert) {
        DemographicCust cust = new DemographicCust();
        cust.setAlert(alert);
        cust.setNotes("<unotes>standing note</unotes>");
        return cust;
    }

    @Test
    void shouldBeStable_forTheSameRecord() {
        String version = DemographicEditVersion.of(patient(), extensions("555-0100"), cust("none"));

        assertThat(version).matches("[0-9a-f]{64}");
        assertThat(DemographicEditVersion.of(patient(), extensions("555-0100"), cust("none"))).isEqualTo(version);
        assertThat(DemographicEditVersion.isPresent(version)).isTrue();
    }

    @Test
    void shouldChange_whenAFieldTheFormWritesChanges() {
        String before = DemographicEditVersion.of(patient(), extensions("555-0100"), cust("none"));
        Demographic changed = patient();
        changed.setPhone("555-0187");

        assertThat(DemographicEditVersion.of(changed, extensions("555-0100"), cust("none"))).isNotEqualTo(before);
    }

    @Test
    void shouldChange_whenTheRecordIsSavedAgainWithoutVisibleChanges() {
        String before = DemographicEditVersion.of(patient(), null, null);
        Demographic resaved = patient();
        resaved.setLastUpdateDate(new Date(1_700_000_001_000L));

        assertThat(DemographicEditVersion.of(resaved, null, null)).isNotEqualTo(before);
    }

    @Test
    void shouldTrackNameUsed_fromTheAliasTheFormShows() {
        Demographic shown = patient();
        shown.setAlias("CHECKY");
        shown.setPrefName("CHECKY");
        String before = DemographicEditVersion.of(shown, null, null);
        // An alias-only writer cleared the alias; getPrefName() now falls back to the unchanged column.
        Demographic aliasCleared = patient();
        aliasCleared.setAlias("");
        aliasCleared.setPrefName("CHECKY");

        assertThat(DemographicEditVersion.of(aliasCleared, null, null)).isNotEqualTo(before);
        assertThat(DemographicEditVersion.differences(shown, Map.of(), null, aliasCleared, Map.of(), null))
                .extracting(Difference::labelKey, Difference::yours, Difference::current)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "demographic.demographicaddrecordhtm.formNameUsed", "CHECKY", ""));
    }

    @Test
    void shouldChange_whenThePreferredNameBehindABlankAliasChanges() {
        Demographic shown = patient();
        shown.setAlias("");
        shown.setPrefName("CHECKY");
        Demographic changed = patient();
        changed.setAlias("");
        changed.setPrefName("CHUCK");

        // The save overwrites pref_name with Name Used, so a change to it makes the form stale,
        // but the form never shows it, so it adds no conflict row.
        assertThat(DemographicEditVersion.of(changed, null, null)).isNotEqualTo(DemographicEditVersion.of(shown, null, null));
        assertThat(DemographicEditVersion.differences(shown, Map.of(), null, changed, Map.of(), null)).isEmpty();
    }

    @Test
    void shouldChange_whenAnEditedExtensionRowChangesOrIsCreated() {
        String before = DemographicEditVersion.of(patient(), extensions("555-0100"), null);

        assertThat(DemographicEditVersion.of(patient(), extensions("555-0199"), null)).isNotEqualTo(before);
        // The ext-insert race: the form was rendered before the rows existed.
        assertThat(DemographicEditVersion.of(patient(), Map.of(), null)).isNotEqualTo(before);
    }

    @Test
    void shouldIgnore_extensionKeysTheFormDoesNotWrite() {
        Map<String, String> withDashboardRow = extensions("555-0100");
        withDashboardRow.put("dashboard_exclusion_test", "1");
        withDashboardRow.put("dashboard_exclusion_test_id", "900");

        assertThat(DemographicEditVersion.of(patient(), withDashboardRow, null))
                .isEqualTo(DemographicEditVersion.of(patient(), extensions("555-0100"), null));
    }

    @Test
    void shouldChange_whenTheCustomRowChangesOrAppears() {
        String before = DemographicEditVersion.of(patient(), null, cust("none"));

        assertThat(DemographicEditVersion.of(patient(), null, cust("allergic"))).isNotEqualTo(before);
        assertThat(DemographicEditVersion.of(patient(), null, null)).isNotEqualTo(before);
    }

    @Test
    void shouldAcceptOnlyWellFormedVersions_asPresent() {
        assertThat(DemographicEditVersion.isPresent(null)).isFalse();
        assertThat(DemographicEditVersion.isPresent("")).isFalse();
        assertThat(DemographicEditVersion.isPresent("abc")).isFalse();
        assertThat(DemographicEditVersion.isPresent("A".repeat(64))).isFalse();
        assertThat(DemographicEditVersion.isPresent("a".repeat(64))).isTrue();
    }

    @Test
    void shouldListTheEditedExtensionKeys_withoutBlanksOrDuplicates() {
        List<String> keys = DemographicEditVersion.editedExtensionKeys();

        assertThat(keys).contains("demo_cell", "hPhoneExt", "wPhoneExt", "phoneComment", "PHU");
        assertThat(keys).doesNotHaveDuplicates().allSatisfy(key -> assertThat(key).isNotBlank());
    }

    @Test
    void shouldListOnlyTheDifferingFields_forTheConflictView() {
        Demographic yours = patient();
        yours.setCity("CITY-A");
        Demographic current = patient();
        current.setPhone("555-0187");
        Map<String, String> yourExtensions = new LinkedHashMap<>();
        yourExtensions.put("demo_cell", "555-0100");
        yourExtensions.put("clinicKey", "mine");
        Map<String, String> currentExtensions = extensions("555-0100");

        List<Difference> differences = DemographicEditVersion.differences(yours, yourExtensions, cust("none"),
                current, currentExtensions, cust("none"));

        assertThat(differences).extracting(Difference::labelKey, Difference::name, Difference::yours, Difference::current)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("demographic.demographiceditdemographic.formCity", null, "CITY-A", "Toronto"),
                        org.assertj.core.groups.Tuple.tuple("demographic.demographiceditdemographic.formPhoneH", null, "", "555-0187"),
                        org.assertj.core.groups.Tuple.tuple(null, "clinicKey", "mine", ""));
    }

    @Test
    void shouldShowTheNotesText_withoutTheStorageWrapper() {
        DemographicCust yours = cust("none");
        yours.setNotes("<unotes>new instructions</unotes>");

        List<Difference> differences = DemographicEditVersion.differences(patient(), Map.of(), yours,
                patient(), Map.of(), cust("none"));

        assertThat(differences).singleElement().satisfies(difference -> {
            assertThat(difference.labelKey()).isEqualTo("demographic.demographiceditdemographic.formNotes");
            assertThat(difference.yours()).isEqualTo("new instructions");
            assertThat(difference.current()).isEqualTo("standing note");
        });
    }
}
