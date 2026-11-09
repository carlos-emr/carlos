/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import java.util.Hashtable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Tests the interval meaning shown to prescribers, including adjoining XML recommendations. */
@Tag("unit")
class DosingRecommendationBoundaryUnitTest {
    @ParameterizedTest
    @CsvSource({"<10,9,true", "<10,10,false", "<10,11,false",
            ">99,98,false", ">99,99,false", ">99,100,true",
            "10-49,9,false", "10-49,10,true", "10-49,49,true", "10-49,50,false",
            "<60,59.7,true", "<15,14.9,true", "<10,9.99,true", "<60,60,false",
            "&lt;15,15,false", "&gt;99,99,false"})
    void shouldRespectDisplayedInterval_whenSelectingDose(String range, double clearance, boolean selected) {
        var dose = new Hashtable<String, String>();
        dose.put("clcrrange", range);
        assertThat(new DosingRecomendation().valueInRangeOfDose(clearance, dose)).isEqualTo(selected);
    }
}
