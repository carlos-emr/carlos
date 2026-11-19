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
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.util.Calendar;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timestamp fields of the REST transfer objects keep one JSON type on the OAuth surface (issue #3877).
 *
 * <p>{@code SmartDateSerializer}, registered only on the {@code /ws/services} mapper, writes a
 * {@code Date} at local midnight as {@code "yyyy-MM-dd"} and one on 1970-01-01 as
 * {@code "HH:mm:ss"}. A field that holds a full timestamp therefore arrived as a number in one
 * response and a string in the next, which broke integrations such as Ocean that read epoch
 * milliseconds. Each field below is annotated {@code @JsonFormat(shape = NUMBER)}; this test drives
 * it through the mapper the deployed OAuth surface builds from {@code applicationContextREST.xml}
 * at exactly the values that used to flip, and checks the session {@code /ws/rs} surface (which
 * always wrote epoch milliseconds) is unchanged.
 *
 * @since 2026-10-19
 */
@DisplayName("REST timestamp fields serialize as epoch milliseconds")
@Tag("unit")
@Tag("rest")
@Tag("fast")
class RestTimestampEpochShapeUnitTest {

    private static Date local(int year, int month, int day, int hour, int minute, int second, int millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, millis);
        return calendar.getTime();
    }

    /** The values SmartDateSerializer special-cases, and an ordinary timestamp as a control. */
    static Stream<Date> flippingValues() {
        return Stream.of(
                local(2026, Calendar.MARCH, 11, 0, 0, 0, 0),      // was "2026-03-11"
                local(1970, Calendar.JANUARY, 1, 9, 30, 0, 0),    // was "09:30:00"
                local(1970, Calendar.JANUARY, 1, 0, 0, 0, 0),     // was "00:00:00"
                local(2026, Calendar.MARCH, 11, 14, 22, 5, 123)); // always a number
    }

    private record TimestampField<T>(String jsonName, Supplier<T> factory, BiConsumer<T, Date> setter) {
        Object withValue(Date value) {
            T bean = factory.get();
            setter.accept(bean, value);
            return bean;
        }

        @Override
        public String toString() {
            return factory.get().getClass().getSimpleName() + "." + jsonName;
        }
    }

    private static <T> TimestampField<T> field(String jsonName, Supplier<T> factory, BiConsumer<T, Date> setter) {
        return new TimestampField<>(jsonName, factory, setter);
    }

    /** The fields issue #3877 names, plus CaseManagementIssueTo1.update_date from the same upstream fix. */
    static Stream<TimestampField<?>> issueFields() {
        return Stream.of(
                field("lastUpdateDate", AllergyTo1::new, AllergyTo1::setLastUpdateDate),
                field("createDateTime", AppointmentTo1::new, AppointmentTo1::setCreateDateTime),
                field("updateDateTime", AppointmentTo1::new, AppointmentTo1::setUpdateDateTime),
                field("lastUpdateDate", DemographicTo1::new, DemographicTo1::setLastUpdateDate),
                field("update_date", IssueTo1::new, IssueTo1::setUpdate_date),
                field("update_date", CaseManagementIssueTo1::new, CaseManagementIssueTo1::setUpdate_date),
                field("createDate", MeasurementTo1::new, MeasurementTo1::setCreateDate),
                field("observationDate", NoteTo1::new, NoteTo1::setObservationDate),
                field("updateDate", NoteTo1::new, NoteTo1::setUpdateDate));
    }

    static Stream<Arguments> issueFieldsAtFlippingValues() {
        return issueFields().flatMap(f -> flippingValues().map(v -> Arguments.of(f, v)));
    }

    private static JsonNode written(ObjectMapper mapper, Object bean, String jsonName) {
        JsonNode tree = mapper.valueToTree(bean);
        assertThat(tree.has(jsonName)).as("%s has a JSON property %s", bean.getClass().getSimpleName(), jsonName)
                .isTrue();
        return tree.get(jsonName);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("issueFieldsAtFlippingValues")
    @DisplayName("should write epoch milliseconds on the OAuth surface for every timestamp value")
    void shouldWriteEpochMillis_onOauthSurfaceForEveryTimestampValue(TimestampField<?> field, Date value) {
        JsonNode node = written(ProductionRestObjectMappers.oauthSurface(), field.withValue(value), field.jsonName());

        assertThat(node.isIntegralNumber()).as("%s written as %s", field, node).isTrue();
        assertThat(node.longValue()).isEqualTo(value.getTime());
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("issueFieldsAtFlippingValues")
    @DisplayName("should write the same epoch milliseconds on both REST surfaces")
    void shouldWriteSameEpochMillis_onBothRestSurfaces(TimestampField<?> field, Date value) {
        Object bean = field.withValue(value);

        JsonNode oauth = written(ProductionRestObjectMappers.oauthSurface(), bean, field.jsonName());
        JsonNode session = written(ProductionRestObjectMappers.sessionSurface(), bean, field.jsonName());

        assertThat(oauth).as("%s on /ws/services vs /ws/rs", field).isEqualTo(session);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("issueFieldsAtFlippingValues")
    @DisplayName("should read back the epoch milliseconds it writes on the OAuth surface")
    void shouldRoundTripEpochMillis_onOauthSurface(TimestampField<?> field, Date value) throws Exception {
        ObjectMapper mapper = ProductionRestObjectMappers.oauthSurface();
        Object bean = field.withValue(value);
        // Only the field under test: some TOs expose read-only getters that the production mapper
        // (FAIL_ON_UNKNOWN_PROPERTIES on) would refuse on the way back in.
        String json = mapper.createObjectNode()
                .set(field.jsonName(), written(mapper, bean, field.jsonName())).toString();

        Object back = mapper.readValue(json, bean.getClass());

        assertThat(written(mapper, back, field.jsonName()).longValue()).isEqualTo(value.getTime());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("issueFields")
    @DisplayName("should keep writing null for an unset timestamp")
    void shouldWriteNull_forUnsetTimestamp(TimestampField<?> field) {
        JsonNode node = written(ProductionRestObjectMappers.oauthSurface(), field.withValue(null), field.jsonName());

        assertThat(node.isNull()).as("%s written as %s", field, node).isTrue();
    }

    @Test
    @DisplayName("should still accept the string shapes integrations were sent before the fix")
    void shouldAcceptLegacyStringShapes_whenIntegrationEchoesOldPayload() throws Exception {
        // An integration that cached a pre-fix response may POST the old string back; the
        // deserializer is unchanged, so both shapes still parse to the same instants.
        ObjectMapper mapper = ProductionRestObjectMappers.oauthSurface();

        NoteTo1 dateOnly = mapper.readValue("{\"observationDate\":\"2026-03-11\"}", NoteTo1.class);
        AppointmentTo1 timeOnly = mapper.readValue("{\"updateDateTime\":\"09:30:00\"}", AppointmentTo1.class);

        assertThat(dateOnly.getObservationDate()).isEqualTo(local(2026, Calendar.MARCH, 11, 0, 0, 0, 0));
        assertThat(timeOnly.getUpdateDateTime()).isEqualTo(local(1970, Calendar.JANUARY, 1, 9, 30, 0, 0));
    }

    @Test
    @DisplayName("should keep the calendar-date and clock-time shapes of appointment slots")
    void shouldKeepStringShapes_forAppointmentDateAndTimes() {
        // Pins the boundary of the fix: appointmentDate (a DATE column) and startTime/endTime
        // (TIME columns) always had one stable string shape, and the updateAppointment round trip
        // (issue #2957) depends on it. Only the timestamps beside them changed.
        AppointmentTo1 appointment = new AppointmentTo1();
        appointment.setAppointmentDate(local(2026, Calendar.MARCH, 11, 0, 0, 0, 0));
        appointment.setStartTime(local(1970, Calendar.JANUARY, 1, 9, 30, 0, 0));
        appointment.setEndTime(local(1970, Calendar.JANUARY, 1, 9, 45, 0, 0));
        appointment.setCreateDateTime(local(2026, Calendar.MARCH, 11, 0, 0, 0, 0));

        JsonNode tree = ProductionRestObjectMappers.oauthSurface().valueToTree(appointment);

        assertThat(tree.get("appointmentDate").textValue()).isEqualTo("2026-03-11");
        assertThat(tree.get("startTime").textValue()).isEqualTo("09:30:00");
        assertThat(tree.get("endTime").textValue()).isEqualTo("09:45:00");
        assertThat(tree.get("createDateTime").isIntegralNumber()).isTrue();
    }

    @Test
    @DisplayName("should load the OAuth mapper with SmartDateModule registered")
    void shouldRegisterSmartDateModule_onLoadedOauthMapper() {
        // Guards the loader itself: without the module an unannotated midnight Date would be a
        // number too, and every assertion above would pass against a mapper production never uses.
        JsonNode node = ProductionRestObjectMappers.oauthSurface()
                .valueToTree(local(2026, Calendar.MARCH, 11, 0, 0, 0, 0));

        assertThat(node.textValue()).isEqualTo("2026-03-11");
        assertThat(ProductionRestObjectMappers.sessionSurface()
                .valueToTree(local(2026, Calendar.MARCH, 11, 0, 0, 0, 0)).isIntegralNumber()).isTrue();
    }
}
