/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JsonUtil} JSON serialization utilities.
 *
 * @since 2026-03-31
 */
@DisplayName("JsonUtil Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class JsonUtilUnitTest {

    static class SimpleBean {
        private String name = "test";
        private int value = 42;
        public String getName() { return name; }
        public int getValue() { return value; }
    }

    @Nested
    @DisplayName("pojoToJson")
    class PojoToJson {

        @Test
        @DisplayName("should convert POJO to ObjectNode")
        void shouldConvertPojo_toObjectNode() {
            ObjectNode node = JsonUtil.pojoToJson(new SimpleBean());
            assertThat(node).isNotNull();
            assertThat(node.get("name").asText()).isEqualTo("test");
            assertThat(node.get("value").asInt()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("pojoCollectionToJson")
    class PojoCollectionToJson {

        @Test
        @DisplayName("should convert list of POJOs to JSON array string")
        void shouldConvertList_toJsonArray() {
            List<SimpleBean> list = List.of(new SimpleBean(), new SimpleBean());
            String json = JsonUtil.pojoCollectionToJson(list);
            assertThat(json).startsWith("[");
            assertThat(json).endsWith("]");
            assertThat(json).contains("\"name\":\"test\"");
        }

        @Test
        @DisplayName("should return empty array for null list")
        void shouldReturnEmptyArray_forNull() {
            String json = JsonUtil.pojoCollectionToJson(null);
            assertThat(json).isEqualTo("[]");
        }

        @Test
        @DisplayName("should return empty array for empty list")
        void shouldReturnEmptyArray_forEmpty() {
            String json = JsonUtil.pojoCollectionToJson(Collections.emptyList());
            assertThat(json).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("jsonStringToHashMap")
    class JsonStringToHashMap {

        @Test
        @DisplayName("should parse JSON object to HashMap")
        void shouldParseJson_toHashMap() {
            String json = "{\"name\":\"John\",\"age\":\"30\"}";
            HashMap<String, String> map = JsonUtil.jsonStringToHashMap(json);
            assertThat(map).containsEntry("name", "John");
            assertThat(map).containsEntry("age", "30");
        }

        @Test
        @DisplayName("should return empty map for empty JSON object")
        void shouldReturnEmptyMap_forEmptyJson() {
            HashMap<String, String> map = JsonUtil.jsonStringToHashMap("{}");
            assertThat(map).isEmpty();
        }
    }
}
