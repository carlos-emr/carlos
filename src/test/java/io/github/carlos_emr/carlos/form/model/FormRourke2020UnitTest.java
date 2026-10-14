/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.model;

import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class FormRourke2020UnitTest {
    static Properties recordProperties() {
        Properties values = new Properties();
        values.setProperty("provider_no", "999998");
        values.setProperty("demographic_no", "1");
        values.setProperty("formCreated", "14/10/2026");
        return values;
    }

    @ParameterizedTest
    @CsvSource(value = {"x|", "|x", "|"}, delimiter = '|', nullValues = "", emptyValue = "")
    void shouldPreserveSexMarkers_whenConvertingToPrintProperties(String male, String female) {
        Properties values = recordProperties();
        values.setProperty("c_male", male == null ? "" : male);
        values.setProperty("c_female", female == null ? "" : female);
        assertThat(new FormRourke2020(values).toProperties())
                .containsEntry("c_male", values.getProperty("c_male"))
                .containsEntry("c_female", values.getProperty("c_female"));
    }

    @Test
    void shouldRetainImmunizationNotes_whenSavingAndRedisplayingAllPages() {
        Properties values = recordProperties();
        values.setProperty("p3_immunization", "Page III note\nsecond line <&>");
        values.setProperty("p4_immunization", "Page IV note");
        Properties saved = new FormRourke2020(values).toProperties();
        assertThat(saved).containsEntry("p3_immunization", values.getProperty("p3_immunization"))
                .containsEntry("p4_immunization", values.getProperty("p4_immunization"));
        FormRourke2020 imported = new FormRourke2020(values);
        imported.setP3Immunization(null);
        imported.setP4Immunization(null);
        assertThat(imported.toProperties()).containsEntry("p3_immunization", "").containsEntry("p4_immunization", "");
    }
}
