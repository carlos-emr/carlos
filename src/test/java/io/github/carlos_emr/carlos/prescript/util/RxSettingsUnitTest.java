/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;

/**
 * The Rx settings read under both their {@code prescript.} and their OSCAR {@code rx.} names
 * (issue #4797): the instruction policy and the default instruction of a new prescription.
 */
@Tag("unit")
@DisplayName("Rx settings under prescript.* and rx.* names")
class RxSettingsUnitTest {

    private static final String[] KEYS = {
            "prescript.policy", "rx.policy", "prescript.default_instruction", "rx.default_instruction"};

    /** Original raw values on the shared singleton, restored after each test. */
    private final Map<String, String> original = new HashMap<>();

    @BeforeEach
    void clearKeys() {
        CarlosProperties properties = CarlosProperties.getInstance();
        for (String key : KEYS) {
            // Hashtable read: getProperty(String) would log a missing-key warning and apply defaults.
            original.put(key, (String) properties.get(key));
            properties.remove(key);
        }
    }

    @AfterEach
    void restoreKeys() {
        CarlosProperties properties = CarlosProperties.getInstance();
        for (String key : KEYS) {
            String value = original.get(key);
            if (value == null) {
                properties.remove(key);
            } else {
                properties.setProperty(key, value);
            }
        }
    }

    private static void set(String key, String value) {
        CarlosProperties.getInstance().setProperty(key, value);
    }

    private static String defaultInstruction() {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "999998", 1);
        RxUtil.setDefaultSpecialQuantityRepeat(rx);
        return rx.getSpecial();
    }

    @Test
    @DisplayName("should apply the instruction policy set under the OSCAR name rx.policy")
    void shouldApplyPolicy_whenSetUnderRxName() {
        set("rx.policy", "stjoes");

        assertThat(RxInstructionPolicy.checkInstructions("10 U sc daily")).hasSize(2);
    }

    @Test
    @DisplayName("should apply the instruction policy set under prescript.policy")
    void shouldApplyPolicy_whenSetUnderPrescriptName() {
        set("prescript.policy", "stjoes");

        assertThat(RxInstructionPolicy.checkInstructions("10 U sc daily")).hasSize(2);
    }

    @Test
    @DisplayName("should apply no policy when neither name is set")
    void shouldApplyNoPolicy_whenNeitherNameIsSet() {
        assertThat(RxInstructionPolicy.checkInstructions("10 U sc daily")).isEmpty();
    }

    @Test
    @DisplayName("should prefer prescript.policy when both names are set")
    void shouldPreferPrescriptPolicy_whenBothNamesAreSet() {
        set("prescript.policy", "none");
        set("rx.policy", "stjoes");

        assertThat(RxInstructionPolicy.checkInstructions("10 U sc daily")).isEmpty();
    }

    @Test
    @DisplayName("should still apply rx.policy when prescript.policy is present but blank")
    void shouldApplyRxPolicy_whenPrescriptPolicyIsBlank() {
        set("prescript.policy", " ");
        set("rx.policy", "stjoes");

        assertThat(RxInstructionPolicy.checkInstructions("10 U sc daily")).hasSize(2);
    }

    @Test
    @DisplayName("should prefill a new prescription's instructions from the OSCAR name rx.default_instruction")
    void shouldPrefillInstructions_whenSetUnderRxName() {
        set("rx.default_instruction", "FAKE 1 tab daily");

        assertThat(defaultInstruction()).isEqualTo("FAKE 1 tab daily");
    }

    @Test
    @DisplayName("should prefer prescript.default_instruction when both names are set")
    void shouldPreferPrescriptInstruction_whenBothNamesAreSet() {
        set("prescript.default_instruction", "FAKE new");
        set("rx.default_instruction", "FAKE old");

        assertThat(defaultInstruction()).isEqualTo("FAKE new");
    }

    @Test
    @DisplayName("should leave a new prescription's instructions empty when neither name is set")
    void shouldLeaveInstructionsEmpty_whenNeitherNameIsSet() {
        assertThat(defaultInstruction()).isEmpty();
    }

    @Test
    @DisplayName("should read the new name first, then the OSCAR name")
    void shouldReadNewNameFirst_thenOscarName() {
        assertThat(RxSettings.get("policy")).isNull();
        set("rx.policy", "old");
        assertThat(RxSettings.get("policy")).isEqualTo("old");
        set("prescript.policy", "new");
        assertThat(RxSettings.get("policy")).isEqualTo("new");
        set("prescript.policy", "");
        assertThat(RxSettings.get("policy")).isEqualTo("old");
    }
}
