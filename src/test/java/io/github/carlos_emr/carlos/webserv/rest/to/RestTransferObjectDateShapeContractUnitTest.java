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

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
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
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import jakarta.ws.rs.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code Date} the OAuth {@code /ws/services} surface can write has one JSON type (issue
 * #3877).
 *
 * <p>That surface's {@code SmartDateSerializer} picks a {@code Date}'s shape from its value: a
 * local-midnight value becomes {@code "yyyy-MM-dd"}, one on 1970-01-01 {@code "HH:mm:ss"},
 * anything else epoch milliseconds. That is stable for a calendar date or a clock time, and wrong
 * for a timestamp, which then changes JSON type from one response to the next.
 *
 * <p>The properties checked are every {@code Date} reachable, through the mapper's own view of
 * the types, from the return type of a resource method of a service the {@code restServices}
 * server in {@code applicationContextREST.xml} publishes (including the entities those responses
 * embed), plus every {@code Date} declared in {@code webserv.rest.to} (which also covers types a
 * method returns through an untyped {@code Response}). Each is set to local midnight and then to a
 * time on 1970-01-01, written alone with the mapper that file builds, and must be:
 * <ul>
 *   <li>an epoch-millisecond number, unless it is listed in {@link #STRING_SHAPED}; and</li>
 *   <li>a string if it is listed, so the list cannot go stale.</li>
 * </ul>
 *
 * <p><b>Adding a {@code Date} that a REST response can carry?</b> If it can hold a time of day,
 * annotate it {@code @JsonFormat(shape = JsonFormat.Shape.NUMBER)}. Only if it is always a
 * calendar date ({@code DATE} column) or always a clock time ({@code TIME} column), add it to
 * {@link #STRING_SHAPED} with that reason.
 *
 * @since 2026-10-19
 */
@DisplayName("REST Date shapes on the OAuth surface")
@Tag("unit")
@Tag("rest")
class RestTransferObjectDateShapeContractUnitTest {

    private static final String BASE_PACKAGE = "io.github.carlos_emr.carlos.";
    private static final String TO_PACKAGE = BASE_PACKAGE + "webserv.rest.to";
    private static final String JAXRS_NAMESPACE = "http://cxf.apache.org/jaxrs";
    private static final String OAUTH_SERVER_ID = "restServices";

    private static final String DATE = "calendar date (DATE column): always local midnight, always \"yyyy-MM-dd\"";
    private static final String TIME = "clock time (TIME column): always on 1970-01-01, always \"HH:mm:ss\"";

    /**
     * The properties that deliberately keep SmartDateSerializer's string shape, keyed by the class
     * name relative to {@code io.github.carlos_emr.carlos.} and the JSON property name. Switching one
     * of these to epoch would change a shape integrations already parse correctly, so only a field
     * whose values are all one kind belongs here.
     */
    private static final Map<String, String> STRING_SHAPED = Map.ofEntries(
            entry("webserv.rest.to.model.AllergyTo1.entryDate", DATE),
            entry("webserv.rest.to.model.AppointmentExtTo.appointmentDate", DATE),
            entry("webserv.rest.to.model.AppointmentExtTo.startTime", TIME),
            entry("webserv.rest.to.model.AppointmentTo1.appointmentDate", DATE),
            // startTime/endTime: the updateAppointment round trip (issue #2957) relies on "HH:mm:ss".
            entry("webserv.rest.to.model.AppointmentTo1.startTime", TIME),
            entry("webserv.rest.to.model.AppointmentTo1.endTime", TIME),
            entry("webserv.rest.to.model.BillingDetailTo1.admissionDate", DATE + "; parsed from a yyyy-MM-dd varchar"),
            entry("webserv.rest.to.model.ConsultationRequestExtTo1.dateCreated", DATE),
            entry("webserv.rest.to.model.ConsultationRequestSearchResult.lastFollowUp", DATE),
            entry("webserv.rest.to.model.ConsultationRequestSearchResult.referralDate", DATE),
            entry("webserv.rest.to.model.ConsultationResponseSearchResult.lastFollowUp", DATE),
            entry("webserv.rest.to.model.ConsultationResponseSearchResult.referralDate", DATE),
            entry("webserv.rest.to.model.ConsultationResponseSearchResult.responseDate", DATE),
            entry("webserv.rest.to.model.ConsultationResponseTo1.appointmentDate", DATE),
            entry("webserv.rest.to.model.ConsultationResponseTo1.appointmentTime", TIME),
            entry("webserv.rest.to.model.ConsultationResponseTo1.followUpDate", DATE),
            entry("webserv.rest.to.model.ConsultationResponseTo1.referralDate", DATE),
            entry("webserv.rest.to.model.ConsultationResponseTo1.responseDate", DATE),
            entry("webserv.rest.to.model.DemographicMergedTo1.lastUpdateDate", DATE),
            entry("webserv.rest.to.model.DemographicTo1.dateJoined", DATE),
            // DateOnlySerializer on the getter writes yyyy-MM-dd on both surfaces (issue #4359).
            entry("webserv.rest.to.model.DemographicTo1.dateOfBirth", DATE + "; DateOnlySerializer"),
            entry("webserv.rest.to.model.DemographicTo1.effDate", DATE),
            entry("webserv.rest.to.model.DemographicTo1.endDate", DATE),
            entry("webserv.rest.to.model.DemographicTo1.hcRenewDate", DATE),
            entry("webserv.rest.to.model.DemographicTo1.patientStatusDate", DATE),
            entry("webserv.rest.to.model.DemographicTo1.rosterDate", DATE),
            entry("webserv.rest.to.model.DemographicTo1.rosterTerminationDate", DATE),
            entry("webserv.rest.to.model.DocumentTo1.observationDate", DATE),
            entry("webserv.rest.to.model.DrugTo1.endDate", DATE),
            entry("webserv.rest.to.model.DrugTo1.rxDate", DATE),
            entry("webserv.rest.to.model.DrugTo1.writtenDate", DATE),
            entry("webserv.rest.to.model.EFormReportToolTo1.expiryDate", DATE + "; its only writer parses yyyy-MM-dd"),
            entry("webserv.rest.to.model.EFormTo1.formDate", DATE),
            entry("webserv.rest.to.model.EFormTo1.formTime", TIME),
            entry("webserv.rest.to.model.FormTo1.date", DATE + " (eform_data.form_date)"),
            entry("webserv.rest.to.model.NoteExtTo1.dateValue", DATE + " (casemgmt_note_ext.date_value)"),
            entry("webserv.rest.to.model.OtnEconsult.importDate", "request body of /consults/importEconsult only; never written"),
            entry("webserv.rest.to.model.PrescriptionTo1.datePrescribed", DATE),
            entry("webserv.rest.to.model.PrescriptionTo1.datePrinted", DATE),
            entry("webserv.rest.to.model.PreventionTo1.nextDate", DATE),
            entry("webserv.rest.to.model.ProviderPeriodAppsTo.appointmentDate", DATE),
            entry("webserv.rest.to.model.ProviderTo1.dob", DATE),
            entry("webserv.transfer_objects.ProviderTransfer.dob", DATE),
            // Entities that /ws/services responses embed (e.g. getAppointment's demographic and provider).
            entry("commn.model.BillingONItem.serviceDate", DATE + " (billing_on_item.service_date)"),
            entry("commn.model.Demographic.dateJoined", DATE),
            entry("commn.model.Demographic.effDate", DATE),
            entry("commn.model.Demographic.endDate", DATE),
            entry("commn.model.Demographic.hcRenewDate", DATE),
            entry("commn.model.Demographic.patientStatusDate", DATE),
            entry("commn.model.Demographic.rosterDate", DATE),
            entry("commn.model.Demographic.rosterTerminationDate", DATE),
            entry("commn.model.Provider.dob", DATE));

    private static Date local(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTime();
    }

    /** A Date property of a serialized type, and the first route the walk reached it by. */
    private record DateProperty(String key, Class<?> owner, BeanPropertyDefinition definition, String via) {
    }

    @Test
    @DisplayName("should write every OAuth-reachable timestamp as epoch milliseconds")
    void shouldWriteEpochMillis_forEveryReachableTimestampProperty() throws Exception {
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
                violations.add(property.key() + " (via " + property.via() + ") is written as " + atMidnight
                        + " at midnight and " + atEpochDay + " on 1970-01-01: annotate it"
                        + " @JsonFormat(shape = JsonFormat.Shape.NUMBER), or, if it is always a calendar date or"
                        + " clock time, list it in STRING_SHAPED with that reason");
            }
        }

        assertThat(properties).as("Date properties reachable from /ws/services").hasSizeGreaterThan(130);
        assertThat(violations).as("REST Date shapes on /ws/services (issue #3877)").isEmpty();
        assertThat(seenStringShaped).as("STRING_SHAPED entries that still name a reachable Date property")
                .containsExactlyInAnyOrderElementsOf(STRING_SHAPED.keySet());
    }

    @Test
    @DisplayName("should reach the services and embedded entities the OAuth server publishes")
    void shouldReachEmbeddedEntities_fromPublishedServices() throws Exception {
        // Guards the walk itself: if it stopped following service return types into nested
        // entities, the test above would pass while those entities flipped shape.
        Set<String> keys = new HashSet<>();
        for (DateProperty property : dateProperties(ProductionRestObjectMappers.oauthSurface())) {
            keys.add(property.key());
        }

        assertThat(restServiceClasses()).hasSizeGreaterThan(30);
        assertThat(keys).contains(
                "commn.model.Measurement.createDate",                // MeasurementResponse's entities
                "commn.model.BillingONItem.lastEditDT",              // appointmentHistory > billingDetail
                "commn.model.Demographic.lastUpdateDate",            // getAppointment > demographic
                "casemgmt.model.CaseManagementIssue.update_date",    // saveNote > NoteTo1.issues
                "webserv.transfer_objects.ProviderTransfer.signedConfidentiality",
                "web.PatientListApptItemBean.date");                  // /schedule/day/{date}
    }

    @Test
    @DisplayName("should keep the session surface writing epoch milliseconds for every timestamp property")
    void shouldWriteEpochMillis_onSessionSurfaceForEveryTimestampProperty() throws Exception {
        // The annotations are a no-op on /ws/rs, which always wrote epoch milliseconds; this pins
        // that adding them changed nothing there.
        ObjectMapper session = ProductionRestObjectMappers.sessionSurface();
        Date midnight = local(2026, Calendar.MARCH, 11, 0, 0);

        List<String> changed = new ArrayList<>();
        for (DateProperty property : dateProperties(ProductionRestObjectMappers.oauthSurface())) {
            if (!STRING_SHAPED.containsKey(property.key()) && !isEpoch(write(session, property, midnight), midnight)) {
                changed.add(property.key());
            }
        }

        assertThat(changed).as("/ws/rs timestamp properties no longer written as epoch milliseconds").isEmpty();
    }

    private static boolean isEpoch(JsonNode node, Date expected) {
        return node != null && node.isIntegralNumber() && node.longValue() == expected.getTime();
    }

    /** The service classes the OAuth {@code restServices} CXF server publishes, read from its XML. */
    private static List<Class<?>> restServiceClasses() throws Exception {
        ClassPathResource resource = new ClassPathResource("applicationContextREST.xml");
        Document document;
        try (InputStream stream = resource.getInputStream()) {
            var parser = XmlUtils.createSecureDocumentBuilderFactory();
            parser.setNamespaceAware(true);
            document = parser.newDocumentBuilder().parse(stream);
        }
        List<Class<?>> services = new ArrayList<>();
        var servers = document.getElementsByTagNameNS(JAXRS_NAMESPACE, "server");
        for (int i = 0; i < servers.getLength(); i++) {
            Element server = (Element) servers.item(i);
            if (!OAUTH_SERVER_ID.equals(server.getAttribute("id"))) {
                continue;
            }
            var beanLists = server.getElementsByTagNameNS(JAXRS_NAMESPACE, "serviceBeans");
            for (int j = 0; j < beanLists.getLength(); j++) {
                for (Node bean = beanLists.item(j).getFirstChild(); bean != null; bean = bean.getNextSibling()) {
                    if (bean instanceof Element element && "bean".equals(element.getLocalName())) {
                        services.add(Class.forName(element.getAttribute("class")));
                    }
                }
            }
        }
        return services;
    }

    private static boolean isResourceMethod(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(HttpMethod.class)) {
                return true;
            }
        }
        return false;
    }

    /** Classes of {@link #TO_PACKAGE} from the main source set (not test helpers sharing the package). */
    private static List<Class<?>> transferObjectClasses() throws ClassNotFoundException {
        URL mainClasses = codeSource(AbstractSearchResponse.class);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        List<Class<?>> types = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(TO_PACKAGE)) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (Objects.equals(codeSource(type), mainClasses)) {
                types.add(type);
            }
        }
        return types;
    }

    private static URL codeSource(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }

    /**
     * Walks the serialized type graph from every published resource method's return type and every
     * transfer object class, following property, element, key and referenced types within CARLOS's
     * own packages, and collects the Date properties found along the way.
     */
    private static List<DateProperty> dateProperties(ObjectMapper mapper) throws Exception {
        Deque<JavaType> pending = new ArrayDeque<>();
        Map<String, String> via = new HashMap<>();
        for (Class<?> service : restServiceClasses()) {
            for (Method method : service.getMethods()) {
                if (isResourceMethod(method)) {
                    JavaType type = mapper.getTypeFactory().constructType(method.getGenericReturnType());
                    via.putIfAbsent(type.toCanonical(), service.getSimpleName() + "." + method.getName() + "()");
                    pending.add(type);
                }
            }
        }
        for (Class<?> type : transferObjectClasses()) {
            JavaType javaType = mapper.constructType(type);
            via.putIfAbsent(javaType.toCanonical(), type.getSimpleName());
            pending.add(javaType);
        }

        Map<String, DateProperty> found = new TreeMap<>();
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            JavaType type = pending.pop();
            String route = via.get(type.toCanonical());
            if (type.isContainerType() || type.isReferenceType()) {
                for (JavaType inner : new JavaType[] {type.getContentType(), type.getKeyType(), type.getReferencedType()}) {
                    if (inner != null) {
                        via.putIfAbsent(inner.toCanonical(), route + "[]");
                        pending.add(inner);
                    }
                }
                continue;
            }
            Class<?> raw = type.getRawClass();
            if (!raw.getName().startsWith(BASE_PACKAGE) || raw.isEnum() || raw.isInterface()
                    || Modifier.isAbstract(raw.getModifiers()) || !visited.add(type.toCanonical())) {
                continue;
            }
            BeanDescription description = mapper.getSerializationConfig().introspect(type);
            for (BeanPropertyDefinition definition : description.findProperties()) {
                if (!definition.couldSerialize()) {
                    continue;
                }
                JavaType propertyType = definition.getPrimaryType();
                if (Date.class.isAssignableFrom(propertyType.getRawClass())) {
                    String key = raw.getName().substring(BASE_PACKAGE.length()) + "." + definition.getName();
                    found.putIfAbsent(key, new DateProperty(key, raw, definition, route));
                } else {
                    via.putIfAbsent(propertyType.toCanonical(), route + " > " + raw.getSimpleName() + "."
                            + definition.getName());
                    pending.add(propertyType);
                }
            }
        }
        return new ArrayList<>(found.values());
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
