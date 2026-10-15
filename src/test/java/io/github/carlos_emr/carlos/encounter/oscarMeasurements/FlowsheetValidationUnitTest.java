/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.ImportMeasurementTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;

/** Upload validation must reject unrelated XML before importing measurement types. */
@Tag("unit")
class FlowsheetValidationUnitTest extends CarlosUnitTestBase {
    private MeasurementTemplateFlowSheetConfig configuration;

    @BeforeEach
    void createUnregisteredConfiguration() throws Exception {
        var constructor = MeasurementTemplateFlowSheetConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        configuration = constructor.newInstance();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "<unrelated/>", "<flowsheet/>", "<flowsheet name=' '/>", "<x:flowsheet xmlns:x='urn:unrelated' name='fixture'/>",
            "<unrelated name='fixture'><measurement type='NEVER_IMPORT'/></unrelated>", "<flowsheet"})
    void shouldRejectWithoutImports_whenXmlIsNotANamedFlowsheet(String xml) {
        try (var imports = mockConstruction(ImportMeasurementTypes.class)) {
            assertThat(configuration.validateFlowsheet(xml)).isNull();
            assertThat(imports.constructed()).isEmpty();
        }
    }

    @Test
    void shouldAcceptNamedDefinition_whenRootIsFlowsheet() {
        try (var imports = mockConstruction(ImportMeasurementTypes.class)) {
            var flowsheet = configuration.validateFlowsheet("<flowsheet name='fixture' display_name='Fixture'/>");
            assertThat(flowsheet).isNotNull();
            assertThat(flowsheet.getName()).isEqualTo("fixture");
            assertThat(imports.constructed()).hasSize(1);
        }
    }
}
