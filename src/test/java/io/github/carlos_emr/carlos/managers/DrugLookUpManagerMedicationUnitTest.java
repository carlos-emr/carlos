/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DrugLookUpManagerMedicationUnitTest {
    @Test
    void retainsOrderAndRecordedUnitsFromXmlRpcComponents() {
        var result = new DrugSearchTo1();
        DrugLookUpManager.populateComponents(result, List.of(
                Map.of("name", "First", "strength", 250.0d, "unit", "mg/5 mL"),
                Map.of("name", "Second", "strength", "62.50", "unit", "mg/5 mL"),
                Map.of("name", "Third", "strength", "unavailable", "unit", "IU"),
                Map.of("name", "Fourth", "unit", "mL")));
        assertThat(result.getComponents()).extracting(DrugSearchTo1.DrugComponentTo1::getStrength)
                .containsExactly(250.0d, 62.5d, null, null);
        assertThat(result.getComponents()).extracting(DrugSearchTo1.DrugComponentTo1::getUnit)
                .containsExactly("mg/5 mL", "mg/5 mL", "IU", "mL");
        assertThat(result.getComponents().get(2).getRawStrength()).isEqualTo("unavailable");
        assertThat(result.getComponentDataComplete()).isFalse();
    }
}
