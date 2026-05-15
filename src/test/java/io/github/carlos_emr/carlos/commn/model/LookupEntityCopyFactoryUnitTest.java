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
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lookup entity copy factories")
@Tag("unit")
@Tag("fast")
class LookupEntityCopyFactoryUnitTest {

    @Test
    @DisplayName("should copy all AppointmentType fields")
    void shouldCopyAllAppointmentTypeFields() throws Exception {
        assertCopyFactoryCopiesAllFields(AppointmentType.class, AppointmentType::copyOf);
    }

    @Test
    @DisplayName("should copy all Facility fields")
    void shouldCopyAllFacilityFields() throws Exception {
        assertCopyFactoryCopiesAllFields(Facility.class, Facility::copyOf);
    }

    @Test
    @DisplayName("should copy all ScheduleTemplateCode fields")
    void shouldCopyAllScheduleTemplateCodeFields() throws Exception {
        assertCopyFactoryCopiesAllFields(ScheduleTemplateCode.class, ScheduleTemplateCode::copyOf);
    }

    @Test
    @DisplayName("should defensively copy Facility lastUpdated")
    void shouldDefensivelyCopyFacilityLastUpdated() throws Exception {
        Facility source = populated(Facility.class);

        Facility copy = Facility.copyOf(source);

        Field lastUpdated = Facility.class.getDeclaredField("lastUpdated");
        lastUpdated.setAccessible(true);
        Date sourceLastUpdated = (Date) lastUpdated.get(source);
        Date copyLastUpdated = (Date) lastUpdated.get(copy);
        assertThat(copyLastUpdated).isEqualTo(sourceLastUpdated);
        assertThat(copyLastUpdated).isNotSameAs(sourceLastUpdated);
    }

    @Test
    @DisplayName("should return null when source is null")
    void shouldReturnNull_whenSourceIsNull() {
        assertThat(AppointmentType.copyOf(null)).isNull();
        assertThat(Facility.copyOf(null)).isNull();
        assertThat(ScheduleTemplateCode.copyOf(null)).isNull();
    }

    private <T> void assertCopyFactoryCopiesAllFields(Class<T> modelClass, CopyFactory<T> copyFactory) throws Exception {
        T source = populated(modelClass);

        T copy = copyFactory.copyOf(source);

        assertThat(copy).isNotSameAs(source);
        for (Field field : modelClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            assertThat(field.get(copy))
                    .as("field: %s", field.getName())
                    .isEqualTo(field.get(source));
        }
    }

    private <T> T populated(Class<T> modelClass) throws Exception {
        T instance = modelClass.getDeclaredConstructor().newInstance();
        int fieldIndex = 1;
        for (Field field : modelClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            field.set(instance, valueFor(field, fieldIndex));
            fieldIndex++;
        }
        return instance;
    }

    private Object valueFor(Field field, int fieldIndex) {
        Class<?> fieldType = field.getType();
        if (fieldType == Integer.class) {
            return 1000 + fieldIndex;
        }
        if (fieldType == int.class) {
            return 2000 + fieldIndex;
        }
        if (fieldType == String.class) {
            return field.getName() + "-value-" + fieldIndex;
        }
        if (fieldType == boolean.class) {
            return fieldIndex % 2 == 0;
        }
        if (fieldType == Character.class) {
            return (char) ('A' + fieldIndex);
        }
        if (fieldType == Date.class) {
            return new Date(1700000000000L + fieldIndex);
        }
        throw new AssertionError("Unsupported field type " + fieldType.getName() + " for " + field.getName());
    }

    @FunctionalInterface
    private interface CopyFactory<T> {
        T copyOf(T source);
    }
}
