/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.decisionSupport.model;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.EnumSet;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every Decision Support condition type either lists the patient's values on the guideline
 * detail page or says it has none, without an ERROR (#6328). Iterating {@link DSDemographicAccess.Module}
 * makes a new type decide which it is.
 */
@Tag("unit")
class DSDemographicAccessDisplayValuesUnitTest extends CarlosUnitTestBase {

    private static final Set<DSDemographicAccess.Module> LISTED = EnumSet.of(
            DSDemographicAccess.Module.dxcodes, DSDemographicAccess.Module.drugs, DSDemographicAccess.Module.age,
            DSDemographicAccess.Module.sex, DSDemographicAccess.Module.notes);

    /** Stubs the patient reads, so the listing itself is what is tested. */
    private final DSDemographicAccess patient = new DSDemographicAccess(null, "0") {
        @Override public String getDxCodesStr() { return "dx"; }
        @Override public String getRxCodesStr() { return "rx"; }
        @Override public String getAge() { return "40"; }
        @Override public String getSex() { return "F"; }
    };

    @ParameterizedTest
    @EnumSource(DSDemographicAccess.Module.class)
    void shouldListValuesOrReportNone_withoutLoggingAnError(DSDemographicAccess.Module module) {
        String values;
        try (var logs = LogCapture.forLogger(DSDemographicAccess.class)) {
            values = patient.getDemogrpahicValues(module);
            assertThat(logs.events()).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.ERROR));
        }
        assertThat(DSDemographicAccess.hasDisplayValue(module)).isEqualTo(LISTED.contains(module));
        if (LISTED.contains(module)) {
            assertThat(values).isNotNull();
        } else {
            assertThat(values).isNull();
        }
    }

    @Test
    void shouldHaveNoDisplayValue_forMissingConditionType() {
        assertThat(DSDemographicAccess.hasDisplayValue(null)).isFalse();
    }
}
