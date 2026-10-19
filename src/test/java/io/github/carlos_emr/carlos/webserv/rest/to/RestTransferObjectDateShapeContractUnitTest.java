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
package io.github.carlos_emr.carlos.webserv.rest.to;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code Date} a REST transfer object exposes has one JSON type on the OAuth
 * {@code /ws/services} surface (issue #3877).
 *
 * <p>That surface's {@code SmartDateSerializer} picks a {@code Date}'s shape from its value: a
 * local-midnight value becomes {@code "yyyy-MM-dd"}, one on 1970-01-01 {@code "HH:mm:ss"},
 * anything else epoch milliseconds. That is stable for a calendar date or a clock time, and wrong
 * for a timestamp, which then changes JSON type from one response to the next. This test walks
 * every {@code Date} property of every class in {@code webserv.rest.to} (and the two types outside
 * it that REST responses carry), sets it to local midnight, serializes that one property with the
 * mapper the deployed surface builds from {@code applicationContextREST.xml}, and requires:
 * <ul>
 *   <li>an epoch-millisecond number, unless the property is listed in {@link #STRING_SHAPED}; and</li>
 *   <li>a string for every listed property, so the list cannot go stale.</li>
 * </ul>
 *
 * <p><b>Adding a {@code Date} to a REST transfer object?</b> If it can carry a time of day, annotate
 * it {@code @JsonFormat(shape = JsonFormat.Shape.NUMBER)}. Only if it is always a calendar date
 * ({@code DATE} column) or always a clock time ({@code TIME} column), add it to
 * {@link #STRING_SHAPED} with that reason.
 *
 * @since 2026-10-19
 */
@DisplayName("REST transfer object Date shapes")
@Tag("unit")
@Tag("rest")
class RestTransferObjectDateShapeContractUnitTest {

    private static final String TO_PACKAGE = "io.github.carlos_emr.carlos.webserv.rest.to";

    /**
     * Types outside {@link #TO_PACKAGE} that /ws/services responses serialize as they are:
     * {@code MeasurementResponse} carries {@code Measurement} entities, and
     * {@code ProviderService.getProviders} returns {@code ProviderTransfer}s.
     */
    private static final List<Class<?>> TYPES_OUTSIDE_PACKAGE = List.of(Measurement.class, ProviderTransfer.class);

    private static final String DATE = "calendar date (DATE column): always local midnight, always \"yyyy-MM-dd\"";
    private static final String TIME = "clock time (TIME column): always on 1970-01-01, always \"HH:mm:ss\"";

    /**
     * The properties that deliberately keep SmartDateSerializer's string shape, keyed by the class
     * name relative to {@link #TO_PACKAGE} (simple name for {@link #TYPES_OUTSIDE_PACKAGE}) and the
     * JSON property name. Switching one of these to epoch would change a shape integrations
     * already parse correctly, so only a field whose values are all one kind belongs here.
     */
    private static final Map<String, String> STRING_SHAPED = Map.ofEntries(
            entry("model.AllergyTo1.entryDate", DATE),
            entry("model.AppointmentExtTo.appointmentDate", DATE),
            entry("model.AppointmentExtTo.startTime", TIME),
            entry("model.AppointmentTo1.appointmentDate", DATE),
            // startTime/endTime: the updateAppointment round trip (issue #2957) relies on "HH:mm:ss".
            entry("model.AppointmentTo1.startTime", TIME),
            entry("model.AppointmentTo1.endTime", TIME),
            entry("model.BillingDetailTo1.admissionDate", DATE + "; parsed from a yyyy-MM-dd varchar"),
            entry("model.ConsultationRequestExtTo1.dateCreated", DATE),
            entry("model.ConsultationRequestSearchResult.lastFollowUp", DATE),
            entry("model.ConsultationRequestSearchResult.referralDate", DATE),
            entry("model.ConsultationResponseSearchResult.lastFollowUp", DATE),
            entry("model.ConsultationResponseSearchResult.referralDate", DATE),
            entry("model.ConsultationResponseSearchResult.responseDate", DATE),
            entry("model.ConsultationResponseTo1.appointmentDate", DATE),
            entry("model.ConsultationResponseTo1.appointmentTime", TIME),
            entry("model.ConsultationResponseTo1.followUpDate", DATE),
            entry("model.ConsultationResponseTo1.referralDate", DATE),
            entry("model.ConsultationResponseTo1.responseDate", DATE),
            entry("model.DemographicMergedTo1.lastUpdateDate", DATE),
            entry("model.DemographicTo1.dateJoined", DATE),
            // DateOnlySerializer on the getter writes yyyy-MM-dd on both surfaces (issue #4359).
            entry("model.DemographicTo1.dateOfBirth", DATE + "; DateOnlySerializer"),
            entry("model.DemographicTo1.effDate", DATE),
            entry("model.DemographicTo1.endDate", DATE),
            entry("model.DemographicTo1.hcRenewDate", DATE),
            entry("model.DemographicTo1.patientStatusDate", DATE),
            entry("model.DemographicTo1.rosterDate", DATE),
            entry("model.DemographicTo1.rosterTerminationDate", DATE),
            entry("model.DocumentTo1.observationDate", DATE),
            entry("model.DrugTo1.endDate", DATE),
            entry("model.DrugTo1.rxDate", DATE),
            entry("model.DrugTo1.writtenDate", DATE),
            entry("model.EFormReportToolTo1.expiryDate", DATE + "; its only writer parses yyyy-MM-dd"),
            entry("model.EFormTo1.formDate", DATE),
            entry("model.EFormTo1.formTime", TIME),
            entry("model.FormTo1.date", DATE + " (eform_data.form_date)"),
            entry("model.NoteExtTo1.dateValue", DATE + " (casemgmt_note_ext.date_value)"),
            entry("model.OtnEconsult.importDate", "request body of /consults/importEconsult only; never written"),
            entry("model.PrescriptionTo1.datePrescribed", DATE),
            entry("model.PrescriptionTo1.datePrinted", DATE),
            entry("model.PreventionTo1.nextDate", DATE),
            entry("model.ProviderPeriodAppsTo.appointmentDate", DATE),
            entry("model.ProviderTo1.dob", DATE),
            entry("ProviderTransfer.dob", DATE));

    private static Date local(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTime();
    }

    private record DateProperty(String key, Class<?> owner, BeanPropertyDefinition definition) {
    }

    @Test
    @DisplayName("should write every REST transfer object timestamp as epoch milliseconds")
    void shouldWriteEpochMillis_forEveryTimestampProperty() throws Exception {
        ObjectMapper mapper = ProductionRestObjectMappers.oauthSurface();
        Date midnight = local(2026, Calendar.MARCH, 11, 0, 0);
        Date epochDayTime = local(1970, Calendar.JANUARY, 1, 9, 30);

        List<String> violations = new ArrayList<>();
        Set<String> seenStringShaped = new LinkedHashSet<>();
        List<DateProperty> properties = dateProperties(mapper);
        for (DateProperty property : properties) {
            JsonNode atMidnight = write(mapper, property, midnight);
            if (STRING_SHAPED.containsKey(property.key())) {
                seenStringShaped.add(property.key());
                if (atMidnight == null || !atMidnight.isTextual()) {
                    violations.add(property.key() + " is listed in STRING_SHAPED but is written as " + atMidnight
                            + "; remove it from the list");
                }
                continue;
            }
            JsonNode atEpochDay = write(mapper, property, epochDayTime);
            if (!isEpoch(atMidnight, midnight) || !isEpoch(atEpochDay, epochDayTime)) {
                violations.add(property.key() + " is written as " + atMidnight + " at midnight and " + atEpochDay
                        + " on 1970-01-01: annotate it @JsonFormat(shape = JsonFormat.Shape.NUMBER), or, if it is"
                        + " always a calendar date or clock time, list it in STRING_SHAPED with that reason");
            }
        }

        assertThat(properties).as("Date properties found under %s", TO_PACKAGE).hasSizeGreaterThan(100);
        assertThat(violations).as("REST transfer object Date shapes (issue #3877)").isEmpty();
        assertThat(seenStringShaped).as("STRING_SHAPED entries that still name a Date property")
                .containsExactlyInAnyOrderElementsOf(STRING_SHAPED.keySet());
    }

    @Test
    @DisplayName("should keep the session surface writing epoch milliseconds for every timestamp property")
    void shouldWriteEpochMillis_onSessionSurfaceForEveryTimestampProperty() throws Exception {
        // The annotations are a no-op on /ws/rs, which always wrote epoch milliseconds; this pins
        // that adding them changed nothing there.
        ObjectMapper session = ProductionRestObjectMappers.sessionSurface();
        Date midnight = local(2026, Calendar.MARCH, 11, 0, 0);

        List<String> changed = new ArrayList<>();
        for (DateProperty property : dateProperties(session)) {
            if (!STRING_SHAPED.containsKey(property.key()) && !isEpoch(write(session, property, midnight), midnight)) {
                changed.add(property.key());
            }
        }

        assertThat(changed).as("/ws/rs timestamp properties no longer written as epoch milliseconds").isEmpty();
    }

    private static boolean isEpoch(JsonNode node, Date expected) {
        return node != null && node.isIntegralNumber() && node.longValue() == expected.getTime();
    }

    private static List<DateProperty> dateProperties(ObjectMapper mapper) throws ClassNotFoundException {
        Map<String, Class<?>> types = new TreeMap<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        for (BeanDefinition definition : scanner.findCandidateComponents(TO_PACKAGE)) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (!type.isInterface() && !type.isEnum() && !Modifier.isAbstract(type.getModifiers())) {
                types.put(type.getName().substring(TO_PACKAGE.length() + 1), type);
            }
        }
        for (Class<?> type : TYPES_OUTSIDE_PACKAGE) {
            types.put(type.getSimpleName(), type);
        }

        List<DateProperty> properties = new ArrayList<>();
        types.forEach((name, type) -> {
            BeanDescription description = mapper.getSerializationConfig().introspect(mapper.constructType(type));
            for (BeanPropertyDefinition definition : description.findProperties()) {
                if (definition.couldSerialize() && Date.class.isAssignableFrom(definition.getRawPrimaryType())) {
                    properties.add(new DateProperty(name + "." + definition.getName(), type, definition));
                }
            }
        });
        return properties;
    }

    /**
     * Serializes only this property of a fresh instance holding {@code value}. Writing the whole
     * bean would call every other getter too, and some (e.g. FormTo1's) fail on an empty instance.
     */
    private static JsonNode write(ObjectMapper mapper, DateProperty property, Date value) throws Exception {
        Object bean = instantiate(property.owner());
        set(property, bean, value);
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        JsonSerializer<Object> serializer = provider.findTypedValueSerializer(property.owner(), true, null);
        assertThat(serializer).as("serializer for %s", property.owner()).isInstanceOf(BeanSerializerBase.class);
        for (Iterator<PropertyWriter> writers = ((BeanSerializerBase) serializer).properties(); writers.hasNext(); ) {
            PropertyWriter writer = writers.next();
            if (writer.getName().equals(property.definition().getName())) {
                try (TokenBuffer buffer = new TokenBuffer(mapper, false)) {
                    buffer.writeStartObject();
                    writer.serializeAsField(bean, buffer, provider);
                    buffer.writeEndObject();
                    JsonNode written = mapper.readTree(buffer.asParser());
                    return written.get(writer.getName());
                }
            }
        }
        throw new AssertionError(property.key() + " has no property writer");
    }

    private static Object instantiate(Class<?> type) throws Exception {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            return new ObjenesisStd().newInstance(type);
        }
    }

    private static void set(DateProperty property, Object bean, Date value) throws Exception {
        BeanPropertyDefinition definition = property.definition();
        AnnotatedMember mutator = definition.getMutator();
        if (mutator instanceof AnnotatedMethod method && method.getParameterCount() == 1) {
            method.fixAccess(true);
            method.callOnWith(bean, value);
            return;
        }
        AnnotatedField field = definition.getField();
        if (field == null && mutator instanceof AnnotatedField mutatorField) {
            field = mutatorField;
        }
        if (field == null) {
            throw new AssertionError(property.key() + " has no setter or field to drive; classify it by hand");
        }
        field.fixAccess(true);
        field.setValue(bean, value);
    }
}
