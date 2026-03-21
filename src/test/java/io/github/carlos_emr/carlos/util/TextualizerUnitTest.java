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
package io.github.carlos_emr.carlos.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.SortedMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.utility.TextualizerTestClass;

/**
 * Unit tests for {@link Textualizer}.
 *
 * <p>Tests round-trip conversion of POJOs to/from maps, covering all
 * Java primitive and wrapper types.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("Textualizer")
class TextualizerUnitTest {

    private final Textualizer textualizer = new Textualizer();

    private TextualizerTestClass createPopulatedTestObject() {
        TextualizerTestClass obj = new TextualizerTestClass();
        obj.setDateObj(new Date(1704067200000L)); // 2024-01-01
        obj.setIntegerObj(42);
        obj.setBooleanObj(true);
        obj.setLongObj(123456789L);
        obj.setStringObj("test string");
        obj.setCharacterObj('A');
        obj.setByteObj((byte) 7);
        obj.setShortObj((short) 100);
        obj.setFloatObj(3.14f);
        obj.setDoubleObj(2.71828);
        obj.setIntPrim(99);
        obj.setBooleanPrim(true);
        obj.setLongPrim(987654321L);
        obj.setCharPrim('Z');
        obj.setBytePrim((byte) 3);
        obj.setShortPrim((short) 50);
        obj.setFloatPrim(1.23f);
        obj.setDoublePrim(4.56);
        return obj;
    }

    @Nested
    @DisplayName("toMap")
    class ToMap {

        @Test
        @DisplayName("should convert all property types to map entries")
        void shouldConvertAllPropertyTypes_toMapEntries() throws Exception {
            TextualizerTestClass obj = createPopulatedTestObject();
            SortedMap<String, String> map = textualizer.toMap(obj);

            assertThat(map).isNotEmpty();
            assertThat(map).containsKey("stringObj");
            assertThat(map).containsKey("integerObj");
            assertThat(map).containsKey("booleanObj");
            assertThat(map).containsKey("longObj");
            assertThat(map).containsKey("floatObj");
            assertThat(map).containsKey("doubleObj");
            assertThat(map).containsKey("intPrim");
            assertThat(map).containsKey("booleanPrim");
            assertThat(map).containsKey("longPrim");
            assertThat(map).containsKey("floatPrim");
            assertThat(map).containsKey("doublePrim");
            assertThat(map).containsKey("byteObj");
            assertThat(map).containsKey("shortObj");
            assertThat(map).containsKey("characterObj");
            assertThat(map).containsKey("charPrim");
            assertThat(map).containsKey("bytePrim");
            assertThat(map).containsKey("shortPrim");
        }

        @Test
        @DisplayName("should use column name annotation when present")
        void shouldUseColumnName_whenAnnotationPresent() throws Exception {
            TextualizerTestClass obj = createPopulatedTestObject();
            SortedMap<String, String> map = textualizer.toMap(obj);

            assertThat(map).containsKey("fancyColunmNameForDateObj");
        }

        @Test
        @DisplayName("should contain expected number of properties")
        void shouldContainExpectedPropertyCount() throws Exception {
            TextualizerTestClass obj = createPopulatedTestObject();
            SortedMap<String, String> map = textualizer.toMap(obj);

            assertThat(map).hasSize(18);
        }
    }

    @Nested
    @DisplayName("fromMap")
    class FromMap {

        @Test
        @DisplayName("should round-trip object through map conversion")
        void shouldRoundTrip_throughMapConversion() throws Exception {
            TextualizerTestClass original = createPopulatedTestObject();
            SortedMap<String, String> map = textualizer.toMap(original);

            TextualizerTestClass restored = new TextualizerTestClass();
            textualizer.fromMap(restored, map);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("should preserve string values through round-trip")
        void shouldPreserveStringValues_throughRoundTrip() throws Exception {
            TextualizerTestClass original = createPopulatedTestObject();
            SortedMap<String, String> map = textualizer.toMap(original);

            TextualizerTestClass restored = new TextualizerTestClass();
            textualizer.fromMap(restored, map);

            assertThat(restored.getStringObj()).isEqualTo("test string");
            assertThat(restored.getIntegerObj()).isEqualTo(42);
            assertThat(restored.getBooleanObj()).isTrue();
            assertThat(restored.getLongObj()).isEqualTo(123456789L);
        }

        @Test
        @DisplayName("should preserve primitive values through round-trip")
        void shouldPreservePrimitiveValues_throughRoundTrip() throws Exception {
            TextualizerTestClass original = createPopulatedTestObject();
            SortedMap<String, String> map = textualizer.toMap(original);

            TextualizerTestClass restored = new TextualizerTestClass();
            textualizer.fromMap(restored, map);

            assertThat(restored.getIntPrim()).isEqualTo(99);
            assertThat(restored.isBooleanPrim()).isTrue();
            assertThat(restored.getLongPrim()).isEqualTo(987654321L);
            assertThat(restored.getCharPrim()).isEqualTo('Z');
        }
    }

    @Nested
    @DisplayName("isPrimitive")
    class IsPrimitive {

        @Test
        @DisplayName("should return true for wrapper types")
        void shouldReturnTrue_forWrapperTypes() {
            assertThat(Textualizer.isPrimitive(String.class)).isTrue();
            assertThat(Textualizer.isPrimitive(Integer.class)).isTrue();
            assertThat(Textualizer.isPrimitive(Boolean.class)).isTrue();
            assertThat(Textualizer.isPrimitive(Date.class)).isTrue();
            assertThat(Textualizer.isPrimitive(Long.class)).isTrue();
        }

        @Test
        @DisplayName("should return true for primitive types")
        void shouldReturnTrue_forPrimitiveTypes() {
            assertThat(Textualizer.isPrimitive(int.class)).isTrue();
            assertThat(Textualizer.isPrimitive(boolean.class)).isTrue();
            assertThat(Textualizer.isPrimitive(long.class)).isTrue();
            assertThat(Textualizer.isPrimitive(double.class)).isTrue();
        }

        @Test
        @DisplayName("should return false for non-primitive complex types")
        void shouldReturnFalse_forComplexTypes() {
            assertThat(Textualizer.isPrimitive(Object.class)).isFalse();
            assertThat(Textualizer.isPrimitive(TextualizerTestClass.class)).isFalse();
        }
    }
}
