/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.decisionSupport.model;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercise the real list operators with an independently specified demographic value. */
@Tag("unit")
class DSDemographicSexOperatorsUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"F, 'F,M', false, true", "M, 'F,M', false, true",
        "F, F, true, true", "M, F, false, false", "F, 'F,F', true, true",
        "F, 'male,female', false, true", "F, female, true, true"})
    void shouldApplyConjunctionAndItsNegation(String sex, String candidates, boolean all, boolean any)
            throws Exception {
        DSDemographicAccess demographic = new DSDemographicAccess(null, "0") {
            @Override public String getSex() { return sex; }
        };
        assertThat(demographic.isSexAll(candidates)).isEqualTo(all);
        assertThat(demographic.isSexNotall(candidates)).isEqualTo(!all);
        assertThat(demographic.isSexAny(candidates)).isEqualTo(any);
        assertThat(demographic.isSexNotany(candidates)).isEqualTo(!any);
    }
}
