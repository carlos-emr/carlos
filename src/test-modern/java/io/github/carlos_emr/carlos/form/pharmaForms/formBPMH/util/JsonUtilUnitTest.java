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
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;

/**
 * Unit tests for {@link JsonUtil}.
 *
 * <p>Tests JSON serialization and deserialization of POJO objects
 * used in the BPMH (Best Possible Medication History) form.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("JsonUtil")
class JsonUtilUnitTest {

    private static BpmhDrug testDrug;
    private static List<BpmhDrug> drugList;

    @BeforeAll
    static void setUp() {
        testDrug = new BpmhDrug();
        testDrug.setGenericName("Amoxicillin");
        testDrug.setDosage("500");
        testDrug.setUnit("mg");
        testDrug.setFreqCode("BID");
        testDrug.setRoute("PO");
        testDrug.setDrugForm("CAP");

        BpmhDrug drug2 = new BpmhDrug();
        drug2.setGenericName("Ibuprofen");
        drug2.setDosage("400");
        drug2.setUnit("mg");
        drug2.setFreqCode("TID");

        drugList = new ArrayList<>();
        drugList.add(testDrug);
        drugList.add(drug2);
    }

    @Nested
    @DisplayName("pojoToJson")
    class PojoToJson {

        @Test
        @DisplayName("should convert BpmhDrug to JSON ObjectNode")
        void shouldConvertDrug_toJsonObjectNode() {
            ObjectNode json = JsonUtil.pojoToJson(testDrug);
            assertThat(json).isNotNull();
            assertThat(json.has("genericName")).isTrue();
            assertThat(json.get("genericName").asText()).isEqualTo("Amoxicillin");
        }

        @Test
        @DisplayName("should exclude methods specified in ignore list")
        void shouldExcludeMethods_inIgnoreList() {
            String[] ignoreMethods = {"getGenericName"};
            ObjectNode json = JsonUtil.pojoToJson(testDrug, ignoreMethods);
            assertThat(json).isNotNull();
            assertThat(json.has("genericName")).isFalse();
        }
    }

    @Nested
    @DisplayName("pojoCollectionToJson")
    class PojoCollectionToJson {

        @Test
        @DisplayName("should convert drug list to JSON array string")
        void shouldConvertDrugList_toJsonArrayString() {
            String json = JsonUtil.pojoCollectionToJson(drugList);
            assertThat(json).isNotNull();
            assertThat(json).startsWith("[");
            assertThat(json).endsWith("]");
            assertThat(json).contains("Amoxicillin");
            assertThat(json).contains("Ibuprofen");
        }

        @Test
        @DisplayName("should return empty array for empty list")
        void shouldReturnEmptyArray_forEmptyList() {
            String json = JsonUtil.pojoCollectionToJson(new ArrayList<>());
            assertThat(json).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("jsonToPojoList")
    class JsonToPojoList {

        @Test
        @DisplayName("should deserialize JSON array to BpmhDrug list")
        void shouldDeserializeJsonArray_toBpmhDrugList() {
            String json = JsonUtil.pojoCollectionToJson(drugList);
            List<?> result = JsonUtil.jsonToPojoList(json, BpmhDrug.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(BpmhDrug.class);
            BpmhDrug first = (BpmhDrug) result.get(0);
            assertThat(first.getGenericName()).isEqualTo("Amoxicillin");
        }
    }

    @Nested
    @DisplayName("jsonToPojo")
    class JsonToPojo {

        @Test
        @DisplayName("should deserialize JSON object to BpmhDrug")
        void shouldDeserializeJsonObject_toBpmhDrug() {
            ObjectNode json = JsonUtil.pojoToJson(testDrug);
            Object result = JsonUtil.jsonToPojo(json, BpmhDrug.class);
            assertThat(result).isInstanceOf(BpmhDrug.class);
            BpmhDrug drug = (BpmhDrug) result;
            assertThat(drug.getGenericName()).isEqualTo("Amoxicillin");
            assertThat(drug.getDosage()).isEqualTo("500");
            assertThat(drug.getFreqCode()).isEqualTo("BID");
        }

        @Test
        @DisplayName("should round-trip POJO through JSON serialization")
        void shouldRoundTrip_throughJsonSerialization() {
            ObjectNode json = JsonUtil.pojoToJson(testDrug);
            BpmhDrug roundTripped = (BpmhDrug) JsonUtil.jsonToPojo(json, BpmhDrug.class);
            assertThat(roundTripped.getGenericName()).isEqualTo(testDrug.getGenericName());
            assertThat(roundTripped.getDosage()).isEqualTo(testDrug.getDosage());
            assertThat(roundTripped.getUnit()).isEqualTo(testDrug.getUnit());
            assertThat(roundTripped.getFreqCode()).isEqualTo(testDrug.getFreqCode());
            assertThat(roundTripped.getRoute()).isEqualTo(testDrug.getRoute());
        }
    }
}
