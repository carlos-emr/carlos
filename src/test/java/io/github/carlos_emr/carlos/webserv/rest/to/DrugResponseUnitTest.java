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
package io.github.carlos_emr.carlos.webserv.rest.to;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;
import java.io.Serializable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the legacy single-drug response wrapper.
 *
 * @since 2026-05-07
 */
@DisplayName("DrugResponse regression tests")
@Tag("unit")
@Tag("rest")
@Tag("regression")
class DrugResponseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should retain serializable response inheritance")
    void shouldRetainSerializableResponse_inheritance() {
        assertThat(DrugResponse.class).isAssignableTo(Serializable.class);
    }

    @Test
    @DisplayName("should serialize with drug field")
    void shouldSerialize_withDrugField() throws Exception {
        DrugTo1 drug = new DrugTo1();
        drug.setDrugId(42);
        drug.setBrandName("Test Brand");

        DrugResponse response = new DrugResponse();
        response.setSuccess(true);
        response.setMessage("ok");
        response.setDrug(drug);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("message").asText()).isEqualTo("ok");
        assertThat(json.get("drug").get("drugId").asInt()).isEqualTo(42);
        assertThat(json.get("drug").get("brandName").asText()).isEqualTo("Test Brand");
    }
}
