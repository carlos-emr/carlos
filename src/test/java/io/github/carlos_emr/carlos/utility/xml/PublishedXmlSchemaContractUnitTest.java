/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.AppointmentExtResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentExtTo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.BookingProviderTransfer;
import io.github.carlos_emr.carlos.webserv.rest.to.model.FilterDefinitionTransfer;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentArchiveTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.CalendarScheduleCodePairTransfer;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Native Java objects checked by unchanged schemas emitted by the verified beta6 binary.
 * @since 2026-11-07
 */
@Tag("unit")
@Tag("xml")
class PublishedXmlSchemaContractUnitTest extends CarlosUnitTestBase {
    private static final String SCHEMAS = "/xml/published-beta6-glassfish/";

    /** Covers cleared date-only and time-bearing calendars across DST and fractional-hour zones. */
    static Stream<Arguments> calendarCases() {
        return Stream.of(AppointmentTransfer.class, AppointmentArchiveTransfer.class,
                CalendarScheduleCodePairTransfer.class, LookupCodeValue.class).flatMap(type -> Stream.of(
                    Arguments.of(type, "America/Toronto", 6, false),
                    Arguments.of(type, "America/Toronto", 0, false),
                    Arguments.of(type, "America/Toronto", 6, true),
                    Arguments.of(type, "America/Vancouver", 6, true),
                    Arguments.of(type, "UTC", 6, false),
                    Arguments.of(type, "Asia/Kolkata", 6, true)));
    }

    /** Verifies schema validity, the exact offset, strict reading and unchanged caller state. */
    @ParameterizedTest
    @MethodSource("calendarCases")
    void shouldSatisfyPublishedDateTimeSchema_whenCalendarFieldsAreCleared(
            Class<?> type, String zone, int month, boolean time) throws Exception {
        Object bean = type.getConstructor().newInstance();
        var calendar = new GregorianCalendar(TimeZone.getTimeZone(zone));
        calendar.clear();
        calendar.set(2026, month, 15);
        if (time) {
            calendar.set(Calendar.HOUR_OF_DAY, 13);
            calendar.set(Calendar.MINUTE, 14);
            calendar.set(Calendar.SECOND, 15);
        }
        String original = calendar.toString();
        List<java.lang.reflect.Method> setters = Stream.of(type.getMethods()).filter(method ->
                method.getName().startsWith("set") && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Calendar.class).toList();
        assertThat(setters).isNotEmpty();
        for (var setter : setters) setter.invoke(bean, calendar);

        String xml = write(bean);
        validatePublished(type, xml);
        String expected = java.time.ZonedDateTime.of(2026, month + 1, 15, time ? 13 : 0,
                time ? 14 : 0, time ? 15 : 0, 0, java.time.ZoneId.of(zone))
                .format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX"));
        assertThat(xml).contains(expected);
        assertThat(calendar.toString()).isEqualTo(original);
        var reader = context(type).createUnmarshaller();
        reader.setEventHandler(event -> false);
        Object read = reader.unmarshal(new StreamSource(new StringReader(xml)), type).getValue();
        for (var setter : setters) {
            Calendar actual = (Calendar) type.getMethod("get" + setter.getName().substring(3)).invoke(read);
            assertThat(actual.toInstant()).isEqualTo(java.time.OffsetDateTime.parse(expected).toInstant());
        }
        assertThat(calendar.toString()).isEqualTo(original);
    }

    /** Checks the required filter container directly and through its owning provider response. */
    @Test
    void shouldKeepRequiredEmptyMap_whenWritingDirectAndNestedFilters() throws Exception {
        var filter = new FilterDefinitionTransfer();
        String empty = write(filter);
        validatePublished(FilterDefinitionTransfer.class, empty);
        assertThat(empty).contains("<params");
        var provider = new BookingProviderTransfer();
        // The old schema also requires this unrelated map; null fails on beta6 itself.
        provider.setAppointmentDurations(Map.of());
        provider.setFilters(List.of(filter));
        validatePublished(BookingProviderTransfer.class, write(provider));

        Map<String, String> values = new LinkedHashMap<>();
        values.put("é<&", "日本語");
        values.put("empty", "");
        values.put("absent", null);
        filter.setParams(values);
        String populated = write(filter);
        validatePublished(FilterDefinitionTransfer.class, populated);
        var reader = context(FilterDefinitionTransfer.class).createUnmarshaller();
        reader.setEventHandler(event -> false);
        assertThat(reader.unmarshal(new StreamSource(new StringReader(populated)), FilterDefinitionTransfer.class)
                .getValue().getParams()).containsExactlyEntriesOf(values);
        filter.setParams(null);
        assertThat(write(filter)).doesNotContain("<params");
    }

    /** An empty duration map has no character payload, so its container needs no encoding choice. */
    @Test
    void shouldKeepRequiredEmptyDurationMap_whenWritingSearchConfiguration() throws Exception {
        var config = new SearchConfigTo1();
        config.setAppointmentCodeDurations(Map.of());
        String xml = write(config);
        validatePublished(SearchConfigTo1.class, xml);
        assertThat(xml).contains("<appointmentCodeDurations");
        var reader = context(SearchConfigTo1.class).createUnmarshaller();
        reader.setEventHandler(event -> false);
        assertThat(reader.unmarshal(new StreamSource(new StringReader(xml)), SearchConfigTo1.class)
                .getValue().getAppointmentCodeDurations()).isEmpty();
        config.setAppointmentCodeDurations(null);
        assertThat(write(config)).doesNotContain("<appointmentCodeDurations");
    }

    /** Pins the historical appointment sequence both as a root and in its REST response. */
    @Test
    void shouldKeepPublishedSequence_whenWritingDirectAndNestedAppointments() throws Exception {
        var appointment = new AppointmentExtTo();
        field(appointment, "lastName", "Synthetic");
        field(appointment, "hPhoneExt", "7");
        validatePublished(AppointmentExtTo.class, write(appointment));
        var response = new AppointmentExtResponse();
        response.setContent(List.of(appointment));
        validatePublished(AppointmentExtResponse.class, write(response));

    }

    /** Detects accidental edits to the independently captured published schemas. */
    @Test
    void shouldRetainSchemaFixtureIdentity_whenCheckingPublishedContracts() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try (var input = getClass().getResourceAsStream(SCHEMAS + "provenance.json")) {
            var manifest = mapper.readTree(input);
            assertThat(manifest.path("packageSha256").asText())
                    .isEqualTo("839c981b7de78460d89eaba0a8c95d992d88f861cc4739bba2b81753373a3af1");
            for (var entry : manifest.path("schemas").properties()) {
                try (var schema = getClass().getResourceAsStream(SCHEMAS + entry.getKey())) {
                    assertThat(schema).isNotNull();
                    assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(schema.readAllBytes()))).isEqualTo(entry.getValue().asText());
                }
            }
        }
    }

    /** Selects Glassfish explicitly so service-provider discovery cannot change the oracle. */
    private static JAXBContext context(Class<?> type) throws Exception {
        return org.glassfish.jaxb.runtime.v2.ContextFactory.createContext(new Class<?>[]{type}, Map.of());
    }

    /** Supplies only a synthetic root name for a native object lacking XmlRootElement. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String write(Object bean) throws Exception {
        var output = new StringWriter();
        context(bean.getClass()).createMarshaller().marshal(new JAXBElement(new QName("value"), bean.getClass(), bean), output);
        return output.toString();
    }

    /** Validates against the unchanged type schema with a synthetic global root declaration. */
    private static void validatePublished(Class<?> type, String xml) throws Exception {
        var factory = XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        try (var input = type.getResourceAsStream(SCHEMAS + type.getSimpleName() + ".xsd")) {
            assertThat(input).isNotNull();
            var document = factory.newDocumentBuilder().parse(input);
            var root = document.createElementNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "xs:element");
            root.setAttribute("name", "value");
            String name = type.getSimpleName();
            root.setAttribute("type", Character.toLowerCase(name.charAt(0)) + name.substring(1));
            document.getDocumentElement().appendChild(root);
            XmlUtils.createSecureSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(new DOMSource(document)).newValidator()
                    .validate(new StreamSource(new StringReader(xml)));
        }
    }

    /** Populates field-bound DTO values that do not expose setters. */
    private static void field(Object bean, String name, Object value) throws Exception {
        var field = bean.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(bean, value);
    }
}
