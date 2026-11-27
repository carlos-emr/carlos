/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1.DrugComponentTo1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("rx")
@DisplayName("DrugLookUpManager")
class DrugLookUpManagerUnitTest extends io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase {

    @Test
    @DisplayName("should return component strengths when DrugRef supplies decimal text or numeric values")
    void shouldReadComponentStrengths_whenDrugRefUsesDifferentEncodings() throws Exception {
        var components = new Vector<>(List.of(
                new Hashtable<>(Map.of("name", "Fixture A", "unit", "mg", "strength", "1.25")),
                new Hashtable<>(Map.of("name", "Fixture B", "unit", "mg", "strength", 2.5d)),
                new Hashtable<>(Map.of("name", "Fixture C", "unit", "mg", "strength", 3.0f)),
                new Hashtable<>(Map.of("name", "Fixture D", "unit", "mg"))));
        var details = new Hashtable<>(Map.of("product", "Fixture product", "regional_identifier", "123",
                "components", components));

        try (var drugRef = mockConstruction(RxDrugRef.class, (client, context) -> {
            when(client.getDrug2("123", true)).thenReturn(details);
            when(client.getGenericName("123")).thenReturn(new Hashtable<>(Map.of("name", "Fixture generic")));
        })) {
            var product = new DrugLookUpManager().details("123");

            assertThat(product.getComponents()).extracting(DrugComponentTo1::getStrength)
                    .containsExactly(1.25d, 2.5d, 3.0d, null);
        }
    }
}
