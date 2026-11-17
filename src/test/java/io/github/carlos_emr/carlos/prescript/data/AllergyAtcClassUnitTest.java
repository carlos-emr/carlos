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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AllergyAtcClass}: the ATC class the Penicillin and Sulfa shortcuts file
 * an allergy under, and the prefix check of a prescribed drug against it (issue #4576).
 *
 * @since 2026-10-15
 */
@DisplayName("AllergyAtcClass Unit Tests")
@Tag("unit")
@Tag("rx")
class AllergyAtcClassUnitTest {

    @Nested
    @DisplayName("the shortcuts")
    class Shortcuts {

        @Test
        @DisplayName("should file the Penicillin and Sulfa shortcuts under J01C and J01E")
        void shouldFileShortcuts_underTheirAtcClasses() {
            // The class codes the clinical lead specified on #4576.
            assertThat(AllergyAtcClass.Shortcut.PENICILLINS.allergen()).isEqualTo("PENICILLINS");
            assertThat(AllergyAtcClass.Shortcut.PENICILLINS.atcClass()).isEqualTo("J01C");
            assertThat(AllergyAtcClass.Shortcut.SULFONAMIDES.allergen()).isEqualTo("SULFONAMIDES");
            assertThat(AllergyAtcClass.Shortcut.SULFONAMIDES.atcClass()).isEqualTo("J01E");
        }

        @ParameterizedTest(name = "{0}/{1}/{2} -> {3}")
        @CsvSource({
                "44452, 10, PENICILLINS, PENICILLINS",
                "44159, 10, SULFONAMIDES, SULFONAMIDES",
                "' 44452 ', 10, ' PENICILLINS ', PENICILLINS",
        })
        @DisplayName("should recognise the exact identity an old shortcut recorded")
        void shouldRecogniseLegacyShortcut_forItsExactIdentity(String id, Integer type, String description, String expected) {
            assertThat(AllergyAtcClass.Shortcut.recordedByLegacyShortcut(id, type, description))
                    .isEqualTo(AllergyAtcClass.Shortcut.valueOf(expected));
        }

        @ParameterizedTest(name = "{0}/{1}/{2}")
        @CsvSource(value = {
                // The id alone names an unrelated product (a multivitamin brand) in current DrugRef data.
                "44452, 12, PENICILLINS",
                "44452, 10, AMOXIL",
                // The real class row picked from a search is checked by DrugRef, not recognised here.
                "41472, 10, PENICILLINS",
                "44452, 10, SULFONAMIDES",
                "44159, 10, PENICILLINS",
                // Upper case is what the shortcut wrote; anything else was typed or picked elsewhere.
                "44452, 10, penicillins",
                "NULL, 10, PENICILLINS",
                "44452, NULL, PENICILLINS",
                "44452, 10, NULL",
        }, nullValues = "NULL")
        @DisplayName("should not recognise any other identity as an old shortcut")
        void shouldNotRecogniseLegacyShortcut_forAnyOtherIdentity(String id, Integer type, String description) {
            assertThat(AllergyAtcClass.Shortcut.recordedByLegacyShortcut(id, type, description)).isNull();
        }
    }

    @Nested
    @DisplayName("class codes")
    class ClassCodes {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({"J01C, J01C", "J01CA, J01CA", "J01CA04, J01CA04", "' J01E ', J01E"})
        @DisplayName("should accept ATC codes of levels 3 to 5")
        void shouldAcceptClassCode_forLevelsThreeToFive(String raw, String expected) {
            assertThat(AllergyAtcClass.classCode(raw)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"J", "J01", "j01c", "J01CA0", "J01CA045", "J01C%", "J01C' OR '1'='1", "44452", "J0 1C"})
        @DisplayName("should refuse codes that are too broad or not ATC codes at all")
        void shouldRefuseClassCode_forBroadOrMalformedCodes(String raw) {
            assertThat(AllergyAtcClass.classCode(raw)).isNull();
        }
    }

    @Nested
    @DisplayName("drug codes")
    class DrugCodes {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"J", "J01", "J01C", "J01CA", "J01CA04", " J01CA04 "})
        @DisplayName("should accept an ATC code cut after any of its five levels")
        void shouldAcceptDrugCode_atEveryLevel(String code) {
            assertThat(AllergyAtcClass.isDrugCode(code)).isTrue();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"J0", "J01CA0", "J01CA045", "1", "JJ1", "J0AC", "J01C4", "J01CAA4", "j01ca04", "null"})
        @DisplayName("should refuse a code of the wrong length or shape")
        void shouldRefuseDrugCode_forWrongLengthOrShape(String code) {
            assertThat(AllergyAtcClass.isDrugCode(code)).isFalse();
        }
    }

    @Nested
    @DisplayName("the class check")
    class Covers {

        @ParameterizedTest(name = "{1} is under {0}")
        @CsvSource({
                // The clinical lead's test cases (#4576): Amoxil triggers penicillins, Septra sulfonamides.
                "J01C, J01CA04",
                "J01E, J01EE01",
                // Clavulin (amoxicillin and clavulanic acid) and cloxacillin are penicillins too.
                "J01C, J01CR02",
                "J01C, J01CF02",
                "J01C, ' J01CA04 '",
        })
        @DisplayName("should cover a drug whose ATC code is under the class")
        void shouldCoverDrug_whenItsCodeIsUnderTheClass(String classCode, String drugAtc) {
            assertThat(AllergyAtcClass.covers(classCode, drugAtc)).isTrue();
        }

        @ParameterizedTest(name = "{1} is not under {0}")
        @CsvSource(value = {
                "J01C, J01FA09",   // clarithromycin, a macrolide
                "J01C, J01DB01",   // cefalexin, a cephalosporin (J01D, not J01C)
                "J01C, J01EE01",   // Septra is not a penicillin
                "J01E, J01CA04",   // Amoxil is not a sulfonamide
                "J01C, J01",       // a code above the class is not in it
                "J01C, NULL",
                "J01C, ''",
                "J01C, j01ca04",
                "NULL, J01CA04",
                "J, J01CA04",      // too broad to be a class allergen
        }, nullValues = "NULL")
        @DisplayName("should not cover a drug outside the class, or a code that cannot be compared")
        void shouldNotCoverDrug_whenItsCodeIsOutsideTheClass(String classCode, String drugAtc) {
            assertThat(AllergyAtcClass.covers(classCode, drugAtc)).isFalse();
        }
    }

    @Nested
    @DisplayName("whether the class check has an answer")
    class Decides {

        @ParameterizedTest(name = "{1} against {0}")
        @CsvSource({"J01C, J01CA04", "J01C, J01C", "J01C, J01FA09", "J01C, A02BD07", "J01C, J02", "J01E, J01D"})
        @DisplayName("should decide for a drug at or below the class, or in another branch")
        void shouldDecide_whenDrugIsAtOrBelowTheClassOrElsewhere(String classCode, String drugAtc) {
            assertThat(AllergyAtcClass.decides(classCode, drugAtc)).isTrue();
        }

        @ParameterizedTest(name = "{1} against {0}")
        @CsvSource(value = {"J01C, J01", "J01C, J", "J01CA, J01C", "J01C, NULL", "J01C, ''", "NULL, J01CA04"}, nullValues = "NULL")
        @DisplayName("should not decide for a drug coded above the class, or a code that cannot be compared")
        void shouldNotDecide_whenDrugIsCodedAboveTheClass(String classCode, String drugAtc) {
            assertThat(AllergyAtcClass.decides(classCode, drugAtc)).isFalse();
        }
    }

    @Nested
    @DisplayName("an allergy's shortcut")
    class ShortcutOf {

        @Test
        @DisplayName("should name the shortcut an ATC class allergen or an old shortcut's allergy was recorded through")
        void shouldNameShortcut_forShortcutAllergies() {
            assertThat(AllergyAtcClass.shortcutOf(ClassCodeOf.allergy(null, 8, "PENICILLINS", "J01C")))
                    .isEqualTo(AllergyAtcClass.Shortcut.PENICILLINS);
            assertThat(AllergyAtcClass.shortcutOf(ClassCodeOf.allergy("44159", 10, "SULFONAMIDES", null)))
                    .isEqualTo(AllergyAtcClass.Shortcut.SULFONAMIDES);
        }

        @Test
        @DisplayName("should name no shortcut for any other allergy")
        void shouldNameNoShortcut_forOtherAllergies() {
            // A class allergen is a shortcut's only with the shortcut's own name and class.
            assertThat(AllergyAtcClass.shortcutOf(ClassCodeOf.allergy(null, 8, "PENICILLINS", "J01E"))).isNull();
            assertThat(AllergyAtcClass.shortcutOf(ClassCodeOf.allergy(null, 8, "CEPHALOSPORINS", "J01D"))).isNull();
            assertThat(AllergyAtcClass.shortcutOf(ClassCodeOf.allergy("41472", 10, "PENICILLINS", null))).isNull();
            assertThat(AllergyAtcClass.shortcutOf(ClassCodeOf.allergy("100", 13, "AMOXIL", "J01CA04"))).isNull();
            assertThat(AllergyAtcClass.shortcutOf(null)).isNull();
        }
    }

    @Nested
    @DisplayName("an allergy's class")
    class ClassCodeOf {

        @Test
        @DisplayName("should read the class code of an ATC class allergen")
        void shouldReadClassCode_forAtcClassAllergen() {
            assertThat(AllergyAtcClass.classCodeOf(allergy(null, 8, "PENICILLINS", "J01C"))).isEqualTo("J01C");
        }

        @Test
        @DisplayName("should read the shortcut's class for an allergy an old shortcut recorded")
        void shouldReadShortcutClass_forLegacyShortcutAllergy() {
            assertThat(AllergyAtcClass.classCodeOf(allergy("44452", 10, "PENICILLINS", null))).isEqualTo("J01C");
            assertThat(AllergyAtcClass.classCodeOf(allergy("44159", 10, "SULFONAMIDES", null))).isEqualTo("J01E");
        }

        @Test
        @DisplayName("should give no class for an ATC class allergen picked from a search with no code stored")
        void shouldGiveNoClass_forClassAllergenWithoutCode() {
            assertThat(AllergyAtcClass.classCodeOf(allergy("39007", 8, "PENICILLINS WITH EXTENDED SPECTRUM", null))).isNull();
        }

        @Test
        @DisplayName("should give no class for a brand allergen, whose ATC code is its own product's")
        void shouldGiveNoClass_forBrandAllergen() {
            assertThat(AllergyAtcClass.classCodeOf(allergy("100", 13, "AMOXIL", "J01CA04"))).isNull();
        }

        @Test
        @DisplayName("should give no class for a free-text allergen or none at all")
        void shouldGiveNoClass_forFreeTextAllergen() {
            assertThat(AllergyAtcClass.classCodeOf(allergy("0", 0, "PENICILLINS", null))).isNull();
            assertThat(AllergyAtcClass.classCodeOf(allergy(null, null, "PENICILLINS", "J01C"))).isNull();
            assertThat(AllergyAtcClass.classCodeOf(null)).isNull();
        }

        @Test
        @DisplayName("should give no class for an ATC class allergen whose stored code is malformed")
        void shouldGiveNoClass_forMalformedStoredCode() {
            assertThat(AllergyAtcClass.classCodeOf(allergy(null, 8, "ANTI-INFECTIVES", "J"))).isNull();
        }

        static Allergy allergy(String drugrefId, Integer typeCode, String description, String atc) {
            Allergy allergy = new Allergy();
            allergy.setDrugrefId(drugrefId);
            allergy.setTypeCode(typeCode);
            allergy.setDescription(description);
            allergy.setAtc(atc);
            return allergy;
        }
    }
}
