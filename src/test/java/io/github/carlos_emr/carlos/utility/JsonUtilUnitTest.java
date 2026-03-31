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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JsonUtil} JSON serialization utilities.
 *
 * @since 2026-03-31
 */
@DisplayName("JsonUtil Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class JsonUtilUnitTest {

    public static class SimpleBean {
        private String name = "test";
        private int value = 42;
        public SimpleBean() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
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
    @DisplayName("jsonToPojo")
    class JsonToPojo {

        @Test
        @DisplayName("should deserialize JSON string to POJO")
        void shouldDeserializeJson_toPojo() {
            String json = "{\"name\":\"John\",\"value\":99}";
            SimpleBean bean = (SimpleBean) JsonUtil.jsonToPojo(json, SimpleBean.class);
            assertThat(bean).isNotNull();
            assertThat(bean.getName()).isEqualTo("John");
            assertThat(bean.getValue()).isEqualTo(99);
        }
    }
}
