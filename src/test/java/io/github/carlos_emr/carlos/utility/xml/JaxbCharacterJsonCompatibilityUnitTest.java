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
package io.github.carlos_emr.carlos.utility.xml;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Frozen JSON from both unmodified production mappers, including NUL in scalar and collection values. */
@Tag("unit")
@Tag("rest")
@org.junit.jupiter.api.parallel.ResourceLock("java.util.TimeZone")
class JaxbCharacterJsonCompatibilityUnitTest {
    private static final List<String> TYPES = List.of(
            "webserv.transfer_objects.ScheduleTemplateCodeTransfer",
            "webserv.transfer_objects.CalendarScheduleCodePairTransfer",
            "webserv.transfer_objects.DocumentTransfer",
            "webserv.rest.to.model.ScheduleTemplateCodeTo",
            "webserv.rest.to.model.PharmacyInfoTo1",
            "webserv.rest.to.model.DocumentTo1",
            "webserv.rest.to.model.PreventionTo1",
            "webserv.rest.to.model.BookingScheduleTemplateCodeTransfer",
            "webserv.rest.to.model.AllowedAppointmentTypeTransfer",
            "webserv.rest.to.model.SearchConfigTo1");

    @Test
    void shouldPreserveJson_withXmlCompatibilityAdapters() throws Exception {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            assertFrozenJson(false);
        } finally { java.util.TimeZone.setDefault(previous); }
    }

    @Test
    void shouldPreserveTheWholeHistoricalJsonPayload_underTheExplicitLegacyDateProfile() throws Exception {
        try (var selected = org.mockito.Mockito.mockStatic(
                io.github.carlos_emr.carlos.webserv.rest.util.JsonDateProfile.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            selected.when(io.github.carlos_emr.carlos.webserv.rest.util.JsonDateProfile::current)
                    .thenReturn(io.github.carlos_emr.carlos.webserv.rest.util.JsonDateProfile.LEGACY_VALUE_INFERENCE);
            assertFrozenJson(true);
        }
    }

    private void assertFrozenJson(boolean legacyDates) throws Exception {
        JsonNode expected;
        try (var input = getClass().getResourceAsStream("/xml/jaxb-character-json-baseline.json")) {
            expected = new ObjectMapper().readTree(input);
        }
        for (var surface : Map.of("oauth", ProductionRestObjectMappers.oauthSurface(),
                "session", ProductionRestObjectMappers.sessionSurface()).entrySet()) {
            for (String name : TYPES) {
                Class<?> type = Class.forName("io.github.carlos_emr.carlos." + name);
                for (String scenario : List.of("default", "null", "ascii", "nul", "unicode")) {
                    JsonNode actual = surface.getValue().valueToTree(fixture(type, scenario));
                    JsonNode contract = expected.path(surface.getKey()).path(name).path(scenario).deepCopy();
                    if (!legacyDates && surface.getKey().equals("oauth") && name.equals("webserv.rest.to.model.DocumentTo1")) {
                        // #5895 deliberately declares this DATE property; every other member stays pinned.
                        ((com.fasterxml.jackson.databind.node.ObjectNode) contract).put("observationDate", "2026-07-15");
                    }
                    assertThat(actual).as("%s %s %s", surface.getKey(), name, scenario).isEqualTo(contract);
                }
            }
        }
    }

    @Test
    void shouldPreserveCalendarJson_inProductionRestMappers() throws Exception {
        java.util.Calendar calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Toronto"));
        calendar.setTimeInMillis(1784135655120L);
        var pair = new io.github.carlos_emr.carlos.webserv.transfer_objects.CalendarScheduleCodePairTransfer();
        pair.setDate(calendar);
        for (ObjectMapper mapper : List.of(ProductionRestObjectMappers.oauthSurface(), ProductionRestObjectMappers.sessionSurface())) {
            JsonNode json = mapper.valueToTree(pair);
            assertThat(json.path("date").longValue()).isEqualTo(calendar.getTimeInMillis());
            var read = mapper.readValue(mapper.writeValueAsString(pair), pair.getClass());
            assertThat(read.getDate().getTimeInMillis()).isEqualTo(calendar.getTimeInMillis());
            var code = mapper.readValue("{\"code\":\"\\u0000\"}",
                    io.github.carlos_emr.carlos.webserv.rest.to.model.ScheduleTemplateCodeTo.class);
            assertThat(code.getCode()).isEqualTo('\0');
        }
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("java.util.TimeZone")
    void shouldPreserveEnvelopeDates_inXmlAndJson() throws Exception {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Toronto"));
            java.util.Calendar date = java.util.Calendar.getInstance();
            date.clear();
            date.set(2026, java.util.Calendar.JULY, 15, 13, 14, 15);
            date.set(java.util.Calendar.MILLISECOND, 120);
            for (Class<?> type : List.of(
                    io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse.class,
                    io.github.carlos_emr.carlos.webserv.rest.to.OscarSearchResponse.class)) {
                Object envelope = type.getConstructor().newInstance();
                type.getMethod("setTimestamp", Date.class).invoke(envelope, date.getTime());
                jakarta.xml.bind.JAXBContext context = jakarta.xml.bind.JAXBContext.newInstance(type);
                @SuppressWarnings({"unchecked", "rawtypes"})
                var root = new jakarta.xml.bind.JAXBElement(new javax.xml.namespace.QName("value"), type, envelope);
                try (var written = new java.io.StringWriter()) {
                    context.createMarshaller().marshal(root, written);
                    assertThat(written.toString()).contains("<timestamp>2026-07-15T13:14:15.12-04:00</timestamp>");
                }
                for (ObjectMapper mapper : List.of(ProductionRestObjectMappers.oauthSurface(), ProductionRestObjectMappers.sessionSurface())) {
                    JsonNode json = mapper.valueToTree(envelope);
                    assertThat(json.path("timestamp").longValue()).isEqualTo(date.getTimeInMillis());
                }
            }
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    private static Object fixture(Class<?> type, String scenario) throws Exception {
        Object bean = type.getConstructor().newInstance();
        Character character = switch (scenario) {
            case "ascii" -> 'A';
            case "nul" -> '\0';
            case "unicode" -> 'é';
            default -> null;
        };
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == Date.class && field.get(bean) != null) {
                field.set(bean, new Date(1784135655000L));
            }
            if (scenario.equals("default")) continue;
            if (field.getType() == Character.class || field.getType() == char.class) {
                field.set(bean, character == null && field.getType() == char.class
                        ? Character.valueOf('\0') : character);
            }
            if (field.getType() == Character[].class) {
                field.set(bean, character == null ? null : new Character[]{character, null, 'B'});
            }
            if (field.getName().equals("openAccessList")) {
                field.set(bean, character == null ? null : Arrays.asList(character, null, 'B'));
            }
            if (field.getName().equals("appointmentCodeDurations")) {
                field.set(bean, character == null ? null : Map.of(character, 15));
            }
        }
        return bean;
    }
}
