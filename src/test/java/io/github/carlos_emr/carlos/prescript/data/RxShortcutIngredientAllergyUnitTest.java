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

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exact-product ingredient checks must work when DrugRef has no AHFS class data (#4680). */
@Tag("unit")
@Tag("rx")
class RxShortcutIngredientAllergyUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({
        "PENICILLINS,J01C,A02BD07,AMOXICILLIN TRIHYDRATE,true",
        "PENICILLINS,J01C,A02BD01,METRONIDAZOLE,false",
        "PENICILLINS,J01C,J01RA01,AMPICILLIN SODIUM,true",
        "PENICILLINS,J01C,A02BD07,PROCAINE BENZYLPENICILLIN,true",
        "PENICILLINS,J01C,A02BD07,TEMOCILLIN DISODIUM,true",
        "SULFONAMIDES,J01E,S01CA02,SULFACETAMIDE SODIUM,true",
        "SULFONAMIDES,J01E,S01CA02,CHLORAMPHENICOL,false",
        "SULFONAMIDES,J01E,A07EC01,SULFASALAZINE,true",
        "SULFONAMIDES,J01E,D06BA01,SILVER SULFADIAZINE,true",
        "SULFONAMIDES,J01E,C03AA03,HYDROCHLOROTHIAZIDE,false",
        "SULFONAMIDES,J01E,M01AH01,CELECOXIB,false",
        "SULFONAMIDES,J01E,S01EC01,ACETAZOLAMIDE,false",
        "PENICILLINS,J01C,J01FA09,CLARITHROMYCIN,false"
    })
    void shouldCheckTheExactIngredients_withoutAhfsData(String name, String atc, String drugAtc,
                                                       String ingredient, boolean warns) throws Exception {
        for (boolean legacy : new boolean[]{false, true}) {
            Allergy allergy = allergy(name, atc, legacy);
            List<Allergy> missing = new ArrayList<>();
            try (var _ = mockConstruction(RxDrugRef.class, (ref, context) -> {
                when(ref.getAlergyWarnings(anyString(), any())).thenReturn(answer(false));
                when(ref.getDrugByDIN("00012345", true)).thenReturn(monograph(ingredient));
            })) {
                var warnings = new RxDrugData().getAllergyWarnings(drugAtc, new Allergy[]{allergy}, missing, "00012345");
                assertThat(warnings).hasSize(warns ? 1 : 0);
                if (warns) assertThat(warnings[0]).isSameAs(allergy);
                assertThat(missing).isEmpty();
            }
        }
    }

    @Test
    void shouldRetainTrimethoprimWarning_asPhc007Requested() throws Exception {
        Allergy sulfa = allergy("SULFONAMIDES", "J01E", false);
        try (var _ = mockConstruction(RxDrugRef.class, (ref, context) ->
                when(ref.getAlergyWarnings(anyString(), any())).thenReturn(answer(false)))) {
            assertThat(new RxDrugData().getAllergyWarnings("J01EA01", new Allergy[]{sulfa}, new ArrayList<>()))
                    .hasSize(1);
        }
    }

    @ParameterizedTest
    @CsvSource({"missing", "empty", "outage"})
    void shouldKeepConfirmedWarnings_whenIngredientDataCannotBeChecked(String failure) throws Exception {
        Allergy penicillin = allergy("PENICILLINS", "J01C", false);
        Allergy sulfa = allergy("SULFONAMIDES", "J01E", false);
        List<Allergy> missing = new ArrayList<>();
        try (var _ = mockConstruction(RxDrugRef.class, (ref, context) -> {
            when(ref.getAlergyWarnings(anyString(), any())).thenReturn(new Vector<>(List.of(
                    Map.of("warnings", List.of("0"), "missing", List.of("1")))));
            if (failure.equals("empty")) when(ref.getDrugByDIN(anyString(), eq(true))).thenReturn(new Hashtable<>());
            if (failure.equals("outage")) when(ref.getDrugByDIN(anyString(), eq(true))).thenThrow(new IllegalStateException());
        })) {
            var warnings = new RxDrugData().getAllergyWarnings("A02BD07", new Allergy[]{penicillin, sulfa}, missing, "00012345");
            assertThat(warnings).hasSize(1);
            assertThat(warnings[0]).isSameAs(penicillin);
            assertThat(missing).hasSize(1);
            assertThat(missing.getFirst()).isSameAs(sulfa);
        }
    }

    private Allergy allergy(String name, String atc, boolean legacy) {
        Allergy allergy = new Allergy();
        allergy.setDescription(name);
        allergy.setTypeCode(legacy ? 10 : 8);
        if (legacy) allergy.setDrugrefId(name.equals("PENICILLINS") ? "44452" : "44159");
        else allergy.setAtc(atc);
        return allergy;
    }

    private Vector<?> answer(boolean warning) {
        return new Vector<>(List.of(Map.of("warnings", warning ? List.of("0") : List.of(), "missing", List.of("0"))));
    }

    private Hashtable<String, Object> monograph(String ingredient) {
        Hashtable<String, Object> result = new Hashtable<>();
        result.put("name", "Synthetic product");
        result.put("components", new Vector<>(List.of(new Hashtable<>(Map.of(
                "name", ingredient, "strength", "1", "unit", "mg")))));
        return result;
    }
}
