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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lookup entity copy factories")
@Tag("unit")
@Tag("fast")
class LookupEntityCopyFactoryUnitTest {

    @Test
    @DisplayName("should copy all AppointmentType fields")
    void shouldCopyAllAppointmentTypeFields_forAllInstanceFields() throws Exception {
        assertCopyFactoryCopiesAllFields(AppointmentType.class, AppointmentType::copyOf);
    }

    @Test
    @DisplayName("should copy all Facility fields")
    void shouldCopyAllFacilityFields_forAllInstanceFields() throws Exception {
        assertCopyFactoryCopiesAllFields(Facility.class, Facility::copyOf);
    }

    @Test
    @DisplayName("should copy all ScheduleTemplateCode fields")
    void shouldCopyAllScheduleTemplateCodeFields_forAllInstanceFields() throws Exception {
        assertCopyFactoryCopiesAllFields(ScheduleTemplateCode.class, ScheduleTemplateCode::copyOf);
    }

    @Test
    @DisplayName("should copy all AppointmentStatus fields")
    void shouldCopyAllAppointmentStatusFields_forAllInstanceFields() throws Exception {
        assertCopyFactoryCopiesAllFields(AppointmentStatus.class, AppointmentStatus::copyOf);
    }

    @Test
    @DisplayName("should copy all MeasurementType fields")
    void shouldCopyAllMeasurementTypeFields_forAllInstanceFields() throws Exception {
        assertCopyFactoryCopiesAllFields(MeasurementType.class, MeasurementType::copyOf);
    }

    @Test
    @DisplayName("should copy all LookupListItem fields")
    void shouldCopyAllLookupListItemFields_forAllInstanceFields() throws Exception {
        assertCopyFactoryCopiesAllFields(LookupListItem.class, LookupListItem::copyOf);
    }

    @Test
    @DisplayName("should copy all LookupList fields")
    void shouldCopyAllLookupListFields_forAllInstanceFields() throws Exception {
        assertCopyFactoryCopiesAllFields(LookupList.class, LookupList::copyOf);
    }

    @Test
    @DisplayName("should deep copy LookupList items into a new list of new items in the same order")
    void shouldDeepCopyLookupListItems_inSourceOrder() throws Exception {
        LookupList source = populated(LookupList.class);
        List<LookupListItem> sourceItems = source.getItems();

        LookupList copy = LookupList.copyOf(source);

        assertThat(copy.getItems())
                .as("the copy must not share the source's item list")
                .isNotSameAs(sourceItems)
                .hasSameSizeAs(sourceItems);
        for (int i = 0; i < sourceItems.size(); i++) {
            LookupListItem sourceItem = sourceItems.get(i);
            LookupListItem copiedItem = copy.getItems().get(i);
            assertThat(copiedItem).as("item %s", i).isNotSameAs(sourceItem);
            for (Field field : instanceFieldsIn(LookupListItem.class)) {
                field.setAccessible(true);
                assertThat(field.get(copiedItem))
                        .as("item %s field: %s", i, field.getName())
                        .isEqualTo(field.get(sourceItem));
            }
            assertThat(copiedItem.getDateCreated()).isNotSameAs(sourceItem.getDateCreated());
        }

        copy.getItems().get(0).setLabel("changed");
        copy.getItems().remove(1);
        copy.getItems().add(new LookupListItem());
        assertThat(sourceItems).hasSize(2);
        assertThat(sourceItems.get(0).getLabel()).isNotEqualTo("changed");
    }

    @Test
    @DisplayName("should keep a null LookupList item list null in the copy")
    void shouldKeepNullItemList_whenLookupListItemsAreNull() {
        LookupList source = new LookupList();
        source.setItems(null);

        assertThat(LookupList.copyOf(source).getItems()).isNull();
    }

    @Test
    @DisplayName("should copy raw MeasurementType and LookupListItem values without setter normalisation")
    void shouldCopyRawValues_withoutSetterNormalisation() throws Exception {
        MeasurementType type = new MeasurementType();
        setField(MeasurementType.class, type, "measuringInstruction", null);
        setField(MeasurementType.class, type, "typeDescription", "  padded  ");
        LookupListItem item = new LookupListItem();
        item.setLabel(null);

        MeasurementType copiedType = MeasurementType.copyOf(type);
        LookupListItem copiedItem = LookupListItem.copyOf(item);

        assertThat(getField(MeasurementType.class, copiedType, "measuringInstruction"))
                .as("setMeasuringInstruction would turn null into an empty string")
                .isNull();
        assertThat(copiedType.getTypeDescription())
                .as("setTypeDescription would trim the value")
                .isEqualTo("  padded  ");
        assertThat(getField(LookupListItem.class, copiedItem, "label"))
                .as("getLabel turns null into an empty string; the copy keeps the stored null")
                .isNull();
    }

    @Test
    @DisplayName("should clone creation dates keeping their runtime type and nanoseconds")
    void shouldCloneCreationDates_keepingRuntimeTypeAndNanos() throws Exception {
        Timestamp created = new Timestamp(1700000000000L);
        created.setNanos(123456789);
        MeasurementType type = new MeasurementType();
        setField(MeasurementType.class, type, "createDate", created);
        LookupListItem item = new LookupListItem();
        item.setDateCreated(created);
        LookupList list = new LookupList();
        list.setDateCreated(created);

        Date copiedTypeDate = MeasurementType.copyOf(type).getCreateDate();
        Date copiedItemDate = LookupListItem.copyOf(item).getDateCreated();
        Date copiedListDate = LookupList.copyOf(list).getDateCreated();

        for (Date copied : List.of(copiedTypeDate, copiedItemDate, copiedListDate)) {
            assertThat(copied).isNotSameAs(created).isInstanceOf(Timestamp.class).isEqualTo(created);
            assertThat(((Timestamp) copied).getNanos()).isEqualTo(123456789);
        }
        copiedTypeDate.setTime(0L);
        assertThat(created.getTime()).isEqualTo(1700000000123L);
    }

    @Test
    @DisplayName("should defensively copy Facility lastUpdated")
    void shouldDefensivelyCopyFacilityLastUpdated_withIndependentDateInstance() throws Exception {
        Facility source = populated(Facility.class);

        Facility copy = Facility.copyOf(source);

        Field lastUpdated = Facility.class.getDeclaredField("lastUpdated");
        lastUpdated.setAccessible(true);
        Date sourceLastUpdated = (Date) lastUpdated.get(source);
        Date copyLastUpdated = (Date) lastUpdated.get(copy);
        assertThat(copyLastUpdated)
                .isEqualTo(sourceLastUpdated)
                .isNotSameAs(sourceLastUpdated);
    }

    @Test
    @DisplayName("should return null when source is null")
    void shouldReturnNull_whenSourceIsNull() {
        assertThat(AppointmentType.copyOf(null)).isNull();
        assertThat(Facility.copyOf(null)).isNull();
        assertThat(ScheduleTemplateCode.copyOf(null)).isNull();
        assertThat(AppointmentStatus.copyOf(null)).isNull();
        assertThat(MeasurementType.copyOf(null)).isNull();
        assertThat(LookupList.copyOf(null)).isNull();
        assertThat(LookupListItem.copyOf(null)).isNull();
    }

    private <T> void assertCopyFactoryCopiesAllFields(Class<T> modelClass, CopyFactory<T> copyFactory) throws Exception {
        T source = populated(modelClass);

        T copy = copyFactory.copyOf(source);

        assertThat(copy).isNotSameAs(source);
        for (Field field : instanceFieldsIn(modelClass)) {
            field.setAccessible(true);
            assertThat(field.get(copy))
                    .as("field: %s", field.getName())
                    .isEqualTo(field.get(source));
        }
    }

    private <T> T populated(Class<T> modelClass) throws Exception {
        T instance = modelClass.getDeclaredConstructor().newInstance();
        int fieldIndex = 1;
        for (Field field : instanceFieldsIn(modelClass)) {
            field.setAccessible(true);
            field.set(instance, valueFor(field, fieldIndex));
            fieldIndex++;
        }
        return instance;
    }

    private List<Field> instanceFieldsIn(Class<?> modelClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = modelClass;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return fields;
    }

    private Object valueFor(Field field, int fieldIndex) throws Exception {
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
        if (fieldType == List.class && field.getDeclaringClass() == LookupList.class) {
            List<LookupListItem> items = new ArrayList<>();
            items.add(populated(LookupListItem.class));
            LookupListItem second = populated(LookupListItem.class);
            second.setId(second.getId() + 1);
            second.setLabel("second-label");
            items.add(second);
            return items;
        }
        throw new AssertionError("Unsupported field type " + fieldType.getName() + " for " + field.getName());
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @FunctionalInterface
    private interface CopyFactory<T> {
        T copyOf(T source);
    }
}
