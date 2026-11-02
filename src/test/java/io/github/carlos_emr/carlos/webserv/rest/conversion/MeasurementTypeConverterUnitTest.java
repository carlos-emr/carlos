/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.webserv.rest.to.model.MeasurementTypeTo1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Sample data only; no patient data is involved. */
@Tag("unit")
@DisplayName("MeasurementTypeConverter")
class MeasurementTypeConverterUnitTest {

    private final MeasurementTypeConverter converter = new MeasurementTypeConverter();

    private static MeasurementType sample() {
        MeasurementType d = new MeasurementType();
        d.setType("FAKE-BP");
        d.setTypeDisplayName("FAKE Blood Pressure");
        d.setTypeDescription("FAKE description");
        d.setMeasuringInstruction("FAKE sit down");
        d.setValidation("FAKE-VALIDATION-RULE");
        return d;
    }

    @Test
    void shouldMapPublicFields_whenTypeIsConverted() throws Exception {
        MeasurementTypeTo1 t = converter.getAsTransferObject(null, sample());

        assertThat(t.getType()).isEqualTo("FAKE-BP");
        assertThat(t.getTypeDisplayName()).isEqualTo("FAKE Blood Pressure");
        assertThat(t.getTypeDescription()).isEqualTo("FAKE description");
        assertThat(t.getMeasuringInstruction()).isEqualTo("FAKE sit down");
    }

    @Test
    void shouldNotExposeInternalFields_inSerializedJson() throws Exception {
        MeasurementTypeTo1 t = converter.getAsTransferObject(null, sample());

        String json = new ObjectMapper().writeValueAsString(t);
        JsonNode node = new ObjectMapper().readTree(json);

        assertThat(node.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("type", "typeDisplayName", "typeDescription", "measuringInstruction");
        assertThat(json).doesNotContain("FAKE-VALIDATION-RULE").doesNotContain("createDate");
    }

    @Test
    void shouldKeepNulls_whenEntityFieldsAreNull() throws Exception {
        MeasurementTypeTo1 t = converter.getAsTransferObject(null, new MeasurementType());

        assertThat(t.getType()).isNull();
        assertThat(t.getTypeDisplayName()).isNull();
        assertThat(t.getTypeDescription()).isNull();
        assertThat(t.getMeasuringInstruction()).isNull();
    }

    @Test
    void shouldThrowConversionException_whenConvertingToDomainObject() {
        assertThatThrownBy(() -> converter.getAsDomainObject(null, new MeasurementTypeTo1()))
                .isInstanceOf(ConversionException.class);
    }
}
