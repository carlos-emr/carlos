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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Drug-allergy checks of ATC class allergens (issue #4576): the Penicillin and Sulfa shortcuts
 * file an allergy under ATC J01C or J01E, and {@link RxDrugData#getAllergyWarnings} decides it from
 * that code. DrugRef resolves a class by its name, which a DPD extract may not carry, so every
 * test here has DrugRef answer that it could not check the class allergy.
 *
 * <p>None of the allergies has an id: an unsaved Allergy cannot be compared with
 * {@code Allergy.equals}, which throws without one, so the merge of the two answers must not use
 * it. The assertions compare by identity for the same reason.</p>
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("rx")
@DisplayName("RxDrugData ATC class allergy checks")
class RxDrugDataAtcClassAllergyUnitTest {

    private static final String AMOXIL = "J01CA04";
    private static final String SEPTRA = "J01EE01";
    private static final String BIAXIN = "J01FA09";
    private static final String HP_PAC = "A02BD07";
    /** Identity, not Allergy.equals (which throws for an allergy without an id). */
    private static final Comparator<Allergy> SAME =
            (a, b) -> a == b ? 0 : Integer.compare(System.identityHashCode(a), System.identityHashCode(b));

    @Test
    @DisplayName("should warn when Amoxil is prescribed to a patient with a Penicillin shortcut allergy")
    void shouldWarn_whenAmoxilIsPrescribedForPenicillinClassAllergy() throws Exception {
        Allergy penicillins = classAllergy("PENICILLINS", "J01C");
        List<Allergy> missing = new ArrayList<>();

        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of(), List.of("0"))) {
            assertThat(new RxDrugData().getAllergyWarnings(AMOXIL, new Allergy[]{penicillins}, missing))
                    .usingElementComparator(SAME).containsExactly(penicillins);
        }
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("should warn when Septra is prescribed to a patient with a Sulfa shortcut allergy")
    void shouldWarn_whenSeptraIsPrescribedForSulfonamideClassAllergy() throws Exception {
        Allergy sulfonamides = classAllergy("SULFONAMIDES", "J01E");
        List<Allergy> missing = new ArrayList<>();

        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of(), List.of("0"))) {
            assertThat(new RxDrugData().getAllergyWarnings(SEPTRA, new Allergy[]{sulfonamides}, missing))
                    .usingElementComparator(SAME).containsExactly(sulfonamides);
        }
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("should report a class allergy as checked and clear for a drug outside its class")
    void shouldReportUnchecked_whenIngredientsAreUnavailableOutsideTheClass() throws Exception {
        Allergy penicillins = classAllergy("PENICILLINS", "J01C");
        Allergy sulfonamides = classAllergy("SULFONAMIDES", "J01E");
        List<Allergy> missing = new ArrayList<>();

        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of(), List.of("0", "1"))) {
            assertThat(new RxDrugData().getAllergyWarnings(BIAXIN, new Allergy[]{penicillins, sulfonamides}, missing))
                    .isEmpty();
        }
        assertThat(missing).usingElementComparator(SAME).containsExactly(penicillins, sulfonamides);
    }

    @Test
    @DisplayName("should keep a warning DrugRef returns for a class allergy its code does not cover")
    void shouldKeepDrugRefWarning_whenClassCodeDoesNotCoverTheDrug() throws Exception {
        Allergy penicillins = classAllergy("PENICILLINS", "J01C");

        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of("0"), List.of())) {
            assertThat(new RxDrugData().getAllergyWarnings(BIAXIN, new Allergy[]{penicillins}, new ArrayList<>()))
                    .usingElementComparator(SAME).containsExactly(penicillins);
        }
    }

    @Test
    @DisplayName("should check an allergy an old Penicillin shortcut recorded as its ATC class")
    void shouldWarnForLegacyShortcutAllergy_whenDrugIsInItsClass() throws Exception {
        Allergy legacy = new Allergy();
        legacy.setDrugrefId("44452");
        legacy.setTypeCode(10);
        legacy.setDescription("PENICILLINS");
        List<Allergy> missing = new ArrayList<>();

        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of(), List.of("0"))) {
            assertThat(new RxDrugData().getAllergyWarnings(AMOXIL, new Allergy[]{legacy}, missing))
                    .usingElementComparator(SAME).containsExactly(legacy);
        }
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("should leave DrugRef's answer as it is when the prescribed drug has no usable ATC code")
    void shouldLeaveClassAllergyUnchecked_whenDrugHasNoUsableAtcCode() throws Exception {
        for (String code : new String[]{"", " ", "null", "j01ca04"}) {
            Allergy penicillins = classAllergy("PENICILLINS", "J01C");
            List<Allergy> missing = new ArrayList<>();

            try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of(), List.of("0"))) {
                assertThat(new RxDrugData().getAllergyWarnings(code, new Allergy[]{penicillins}, missing)).isEmpty();
            }
            assertThat(missing).as("drug code \"%s\"", code).usingElementComparator(SAME).containsExactly(penicillins);
        }
    }

    @Test
    @DisplayName("should decide only the class allergies and keep DrugRef's answer for the others, in list order")
    void shouldMergeAnswers_whenClassAndOtherAllergiesAreMixed() throws Exception {
        Allergy brand = new Allergy();
        brand.setTypeCode(13);
        brand.setDescription("SOME BRAND");
        Allergy penicillins = classAllergy("PENICILLINS", "J01C");
        Allergy generic = new Allergy();
        generic.setTypeCode(11);
        generic.setDescription("AMOXICILLIN");
        List<Allergy> missing = new ArrayList<>();

        // DrugRef: the generic warns, the brand and the class allergy were not checked.
        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of("2"), List.of("0", "1"))) {
            assertThat(new RxDrugData().getAllergyWarnings(AMOXIL, new Allergy[]{brand, penicillins, generic}, missing))
                    .usingElementComparator(SAME).containsExactly(penicillins, generic);
        }
        assertThat(missing).usingElementComparator(SAME).containsExactly(brand);
    }

    @Test
    @DisplayName("should ask DrugRef about a shortcut allergy under both the AHFS and the ATC class name")
    void shouldAskDrugRefUnderBothClassNames_forShortcutAllergy() throws Exception {
        Allergy legacy = new Allergy();
        legacy.setDrugrefId("44159");
        legacy.setTypeCode(10);
        legacy.setDescription("SULFONAMIDES");

        List<Map<?, ?>> sent = sentToDrugRef(new Allergy[]{classAllergy("PENICILLINS", "J01C"), legacy});

        // The new and the old shortcut's allergy are asked about the same way, each under its own index.
        assertThat(sent).extracting(item -> item.get("id") + "/" + item.get("type") + "/" + item.get("description") + "/" + item.get("ATC"))
                .containsExactly("0/10/PENICILLINS/J01C", "0/8/PENICILLINS/J01C", "1/10/SULFONAMIDES/J01E", "1/8/SULFONAMIDES/J01E");
    }

    @Test
    @DisplayName("should send any other allergy to DrugRef as it is stored")
    void shouldSendAllergyAsStored_whenItIsNotAShortcutAllergy() throws Exception {
        Allergy brand = new Allergy();
        brand.setTypeCode(13);
        brand.setDescription("AMOXIL");
        brand.setAtc("J01CA04");
        brand.setRegionalIdentifier("00012345");

        List<Map<?, ?>> sent = sentToDrugRef(new Allergy[]{brand});

        assertThat(sent).singleElement().satisfies(item -> {
            assertThat(item.get("id")).isEqualTo("0");
            assertThat(item.get("type")).isEqualTo("13");
            assertThat(item.get("description")).isEqualTo("AMOXIL");
            assertThat(item.get("ATC")).isEqualTo("J01CA04");
            assertThat(item.get("uuid")).isEqualTo("00012345");
        });
    }

    @Test
    @DisplayName("should keep DrugRef's AHFS warning for a penicillin product the DPD files outside J01C")
    void shouldWarn_whenHpPacIsPrescribedForPenicillinClassAllergy() throws Exception {
        // HP-PAC (lansoprazole, amoxicillin and clarithromycin) is A02BD07, under AHFS penicillins.
        Allergy penicillins = classAllergy("PENICILLINS", "J01C");

        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of("0"), List.of("0"))) {
            assertThat(new RxDrugData().getAllergyWarnings(HP_PAC, new Allergy[]{penicillins}, new ArrayList<>()))
                    .usingElementComparator(SAME).containsExactly(penicillins);
        }
    }

    @Test
    @DisplayName("should leave DrugRef's answer as it is for a drug coded above the class")
    void shouldLeaveDrugRefAnswer_whenDrugIsCodedAboveTheClass() throws Exception {
        // "J01" (antibacterials) may or may not be a penicillin: the class code cannot say.
        Allergy unresolved = classAllergy("PENICILLINS", "J01C");
        List<Allergy> missing = new ArrayList<>();
        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of(), List.of("0"))) {
            assertThat(new RxDrugData().getAllergyWarnings("J01", new Allergy[]{unresolved}, missing)).isEmpty();
        }
        assertThat(missing).usingElementComparator(SAME).containsExactly(unresolved);

        Allergy cleared = classAllergy("PENICILLINS", "J01C");
        List<Allergy> stillMissing = new ArrayList<>();
        try (MockedConstruction<RxDrugRef> ignored = drugRefAnswers(List.of(), List.of())) {
            assertThat(new RxDrugData().getAllergyWarnings("J01", new Allergy[]{cleared}, stillMissing)).isEmpty();
        }
        assertThat(stillMissing).hasSize(1);
        assertThat(stillMissing.getFirst()).isSameAs(cleared);
    }

    @Test
    @DisplayName("should refuse an allergy list with a null entry before asking DrugRef")
    void shouldRefuseAllergyList_whenAnEntryIsNull() {
        Allergy[] allergies = {classAllergy("PENICILLINS", "J01C"), null};
        RxDrugData drugData = new RxDrugData();

        try (MockedConstruction<RxDrugRef> drugRef = drugRefAnswers(List.of(), List.of())) {
            assertThatThrownBy(() -> drugData.getAllergyWarnings(AMOXIL, allergies, new ArrayList<>()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("allergies must not contain null");
            assertThat(drugRef.constructed()).isEmpty();
        }
    }

    /** The allergy entries RxDrugData sends DrugRef for {@code allergies}, with a clear answer. */
    private static List<Map<?, ?>> sentToDrugRef(Allergy[] allergies) throws Exception {
        AtomicReference<Vector<?>> sent = new AtomicReference<>();
        try (MockedConstruction<RxDrugRef> ignored = mockConstruction(RxDrugRef.class, (mock, context) ->
                when(mock.getAlergyWarnings(anyString(), any())).thenAnswer(invocation -> {
                    sent.set(invocation.getArgument(1));
                    return new Vector<>(List.of(Map.of("warnings", List.of(), "missing", List.of())));
                }))) {
            new RxDrugData().getAllergyWarnings(AMOXIL, allergies, new ArrayList<>());
        }
        List<Map<?, ?>> items = new ArrayList<>();
        for (Object item : sent.get()) items.add((Map<?, ?>) item);
        return items;
    }

    private static Allergy classAllergy(String description, String atc) {
        Allergy allergy = new Allergy();
        allergy.setTypeCode(AllergyAtcClass.TYPE_CODE);
        allergy.setDescription(description);
        allergy.setAtc(atc);
        return allergy;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static MockedConstruction<RxDrugRef> drugRefAnswers(List<String> warnings, List<String> missing) {
        return mockConstruction(RxDrugRef.class, (mock, context) ->
                when(mock.getAlergyWarnings(anyString(), any()))
                        .thenReturn(new Vector(List.of(Map.of("warnings", warnings, "missing", missing)))));
    }
}
