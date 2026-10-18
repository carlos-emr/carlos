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
package io.github.carlos_emr.carlos.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.carlos_emr.carlos.billing.CA.BC.model.Wcb;

/**
 * The two {@code wcb} entities and the table's nullable {@code w_duration} and {@code formNeeded} columns (issue
 * #4864).
 *
 * <p>Both entities map by field, so Hibernate loads a row by setting each field reflectively. A NULL column put
 * into a primitive {@code int} field fails the whole query, and with it the patient's WCB form list. Each case
 * sets the field to null the same way and reads it back through its getter.</p>
 */
@Tag("unit")
@Tag("billing")
@DisplayName("wcb entities with NULL columns")
class WcbNullColumnsUnitTest {

    static Stream<Arguments> nullableColumns() {
        return Stream.of(
                Arguments.of("WCB.w_duration", new WCB(), "w_duration", (ToIntFunction<Object>) e -> ((WCB) e).getW_duration()),
                Arguments.of("WCB.formNeeded", new WCB(), "formNeeded", (ToIntFunction<Object>) e -> ((WCB) e).getFormNeeded()),
                Arguments.of("Wcb.duration", new Wcb(), "duration", (ToIntFunction<Object>) e -> ((Wcb) e).getDuration()),
                Arguments.of("Wcb.formNeeded", new Wcb(), "formNeeded", (ToIntFunction<Object>) e -> ((Wcb) e).getFormNeeded()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullableColumns")
    @DisplayName("should load a NULL column and read it as 0")
    void shouldReadZero_forNullColumn(String label, Object entity, String fieldName, ToIntFunction<Object> getter)
            throws Exception {
        Field field = entity.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);

        field.set(entity, null);

        assertThat(getter.applyAsInt(entity)).as(label).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullableColumns")
    @DisplayName("should still read a stored value")
    void shouldReadStoredValue_forNonNullColumn(String label, Object entity, String fieldName,
            ToIntFunction<Object> getter) throws Exception {
        Field field = entity.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);

        field.set(entity, 2);

        assertThat(getter.applyAsInt(entity)).as(label).isEqualTo(2);
    }
}
