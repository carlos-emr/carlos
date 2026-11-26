/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prescript.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Hashtable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * DrugRef holds an ingredient strength as a float and sends it as the text of that float, so a
 * 250 mg capsule arrives as "250.0". The Rx pages copy the component's strength into the staged
 * generic name and dosage, which are stored with the prescription and shown wherever it is: a
 * trailing zero that ISMP Canada's <i>Do Not Use</i> list bans ("250.0 mg" read as 2500 mg) (#6185).
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("RxDrugData ingredient strength text")
class RxDrugDataComponentStrengthUnitTest {

    private static RxDrugData.DrugMonograph.DrugComponent component(Object strength) {
        Hashtable<String, Object> wire = new Hashtable<>();
        wire.put("name", "AMOXICILLIN");
        wire.put("unit", "MG");
        wire.put("strength", strength);
        return new RxDrugData().new DrugMonograph().new DrugComponent(wire);
    }

    @ParameterizedTest(name = "DrugRef text \"{0}\" -> {1}")
    @CsvSource({
        "250.0, 250",
        "1.0, 1",
        "0.5, 0.5",
        "0.0625, 0.0625",
        "500.0, 500",
        "1.0E7, 10000000",
        "0.0, 0",
        "2.5, 2.5",
        "250, 250"
    })
    @DisplayName("should drop the trailing zero and exponent of the float text DrugRef sends")
    void shouldWritePlainDecimal_forStrengthSentAsText(String sent, String expected) {
        assertThat(component(sent).getStrength()).isEqualTo(expected);
        assertThat(component(sent).strength).isEqualTo(expected);
    }

    @Test
    @DisplayName("should write a plain decimal when the strength arrives as a number")
    void shouldWritePlainDecimal_forStrengthSentAsNumber() {
        // An XML-RPC <double> is read as a Double; an older DrugRef sent the strength that way.
        assertThat(component(Double.valueOf(250.0d)).getStrength()).isEqualTo("250");
        assertThat(component(Double.valueOf(0.5d)).getStrength()).isEqualTo("0.5");
        assertThat(component(Float.valueOf(500.0f)).getStrength()).isEqualTo("500");
        assertThat(component(Float.valueOf(0.1f)).getStrength()).isEqualTo("0.1");
        assertThat(component(Integer.valueOf(5)).getStrength()).isEqualTo("5");
    }

    @Test
    @DisplayName("should refuse a component that has no strength member, and say why")
    void shouldRefuseComponent_whenStrengthMemberIsMissing() {
        // A Hashtable holds no null: a member DrugRef did not send is absent. Such a component was
        // always refused (the original line called toString() on it). It still is, so that no page
        // stages a product whose strength is unknown as if it had none, or writes the word "null".
        Hashtable<String, Object> wire = new Hashtable<>();
        wire.put("name", "SYNTHETIC BASE");
        wire.put("unit", "MG");
        RxDrugData.DrugMonograph monograph = new RxDrugData().new DrugMonograph();

        assertThatNullPointerException()
                .isThrownBy(() -> monograph.new DrugComponent(wire))
                .withMessage("DrugRef sent a component with no strength");
    }

    @Test
    @DisplayName("should leave strength text that is not a single number as DrugRef sent it")
    void shouldKeepText_whenStrengthIsNotASingleNumber() {
        assertThat(component("2.5 mg/mL").getStrength()).isEqualTo("2.5 mg/mL");
        assertThat(component("").getStrength()).isEmpty();
    }
}
