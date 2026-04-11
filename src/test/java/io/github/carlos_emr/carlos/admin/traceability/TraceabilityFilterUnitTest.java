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
package io.github.carlos_emr.carlos.admin.traceability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@code TRACE_DESERIALIZATION_FILTER} defined in
 * {@link TraceabilityReportProcessor}.
 *
 * <p>Verifies that the filter:
 * <ul>
 *   <li>Allows round-trip deserialization of {@code HashMap<String, String>} — the exact
 *       type serialized by {@link TraceDataProcessor}. In Java 21+, HashMap's
 *       {@code readObject} validates its internal bucket array via
 *       {@code checkArray(s, Map.Entry[].class, cap)}, so the filter must also allow
 *       {@code Map.Entry[]}.</li>
 *   <li>Rejects deserialization of types not on the allowlist (e.g. {@link ArrayList}).</li>
 * </ul>
 *
 * @since 2026-04-10
 */
@Tag("unit")
@Tag("security")
@DisplayName("TRACE_DESERIALIZATION_FILTER")
class TraceabilityFilterUnitTest {

    /**
     * Retrieves the private static {@code TRACE_DESERIALIZATION_FILTER} field from
     * {@link TraceabilityReportProcessor} via reflection for direct filter testing.
     */
    private static ObjectInputFilter getTraceFilter() throws Exception {
        Field field = TraceabilityReportProcessor.class.getDeclaredField("TRACE_DESERIALIZATION_FILTER");
        field.setAccessible(true);
        return (ObjectInputFilter) field.get(null);
    }

    /**
     * Serializes an object to a byte array using standard Java serialization.
     */
    private static byte[] serialize(Object obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    @Nested
    @DisplayName("allowed types")
    class AllowedTypes {

        @Test
        @DisplayName("should deserialize HashMap<String,String> successfully")
        void shouldDeserializeHashMap_whenContainsStringValues() throws Exception {
            ObjectInputFilter filter = getTraceFilter();
            HashMap<String, String> original = new HashMap<>();
            original.put("origin_date", "Thu Apr 10 12:00:00 UTC 2026");
            original.put("git_sha", "abc123def456");
            original.put("some.class.name", "some.value");

            byte[] bytes = serialize(original);

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                ois.setObjectInputFilter(filter);
                @SuppressWarnings("unchecked")
                Map<String, String> result = (Map<String, String>) ois.readObject();
                assertThat(result).isEqualTo(original);
            }
        }

        @Test
        @DisplayName("should deserialize empty HashMap successfully")
        void shouldDeserializeEmptyHashMap_whenMapIsEmpty() throws Exception {
            ObjectInputFilter filter = getTraceFilter();
            HashMap<String, String> original = new HashMap<>();

            byte[] bytes = serialize(original);

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                ois.setObjectInputFilter(filter);
                @SuppressWarnings("unchecked")
                Map<String, String> result = (Map<String, String>) ois.readObject();
                assertThat(result).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("rejected types")
    class RejectedTypes {

        @Test
        @DisplayName("should reject ArrayList deserialization")
        void shouldRejectDeserialization_whenClassIsArrayList() throws Exception {
            ObjectInputFilter filter = getTraceFilter();
            ArrayList<String> list = new ArrayList<>();
            list.add("item");

            byte[] bytes = serialize(list);

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                ois.setObjectInputFilter(filter);
                assertThatThrownBy(ois::readObject)
                    .isInstanceOf(InvalidClassException.class);
            }
        }
    }
}
