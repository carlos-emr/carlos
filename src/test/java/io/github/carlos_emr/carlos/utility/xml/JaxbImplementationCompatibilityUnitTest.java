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

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlRegistry;
import org.glassfish.jaxb.runtime.v2.runtime.JAXBContextImpl;
import org.glassfish.jaxb.runtime.v2.model.runtime.RuntimeTypeInfoSet;
import org.glassfish.jaxb.runtime.v2.model.runtime.RuntimeClassInfo;
import org.glassfish.jaxb.runtime.v2.model.runtime.RuntimePropertyInfo;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs both implementations explicitly: Maven's service-loader order never represented Tomcat's
 * MOXy selection (#5476). Discovery includes generated nested types, not just @XmlRootElement.
 * Every case exercises constructor defaults and a recursively populated synthetic object. The
 * read check compares bound state by writing the Glassfish result again, including read-only
 * live lists, rather than relying on DTOs having equals implementations.
 */
@Tag("unit")
@Tag("xml")
@org.junit.jupiter.api.parallel.ResourceLock("java.util.TimeZone")
class JaxbImplementationCompatibilityUnitTest {
    private static final String BASE = "io.github.carlos_emr.carlos.";
    private static final TimeZone TORONTO = TimeZone.getTimeZone("America/Toronto");
    private static TimeZone originalTimeZone;

    @org.junit.jupiter.api.BeforeAll
    static void useTorontoTimeZone() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TORONTO);
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }


    @TestFactory
    Stream<DynamicTest> shouldPreserveXml_forEveryBoundClass() throws Exception {
        List<Class<?>> classes = boundClasses();
        assertThat(classes).as("discovered JAXB types, including nested generated classes")
                .hasSizeGreaterThan(300);
        Files.write(Path.of("target/jaxb-inventory.txt"), classes.stream().map(Class::getName).toList());
        return classes.stream().map(type -> DynamicTest.dynamicTest(type.getName(), () -> {
            try { compare(type); } catch (Exception e) { throw new IllegalStateException(type.getName(), e); }
        }));
    }

    @org.junit.jupiter.api.Test
    void shouldPopulateNestedContainers_withResolvedGenericTypes() throws Exception {
        var type = io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1.class;
        var context = (JAXBContextImpl) context("org.glassfish.jaxb.runtime.v2.ContextFactory", type);
        var value = (io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1)
                populated(type, new HashSet<>(), 0, context.getTypeInfoSet());
        assertThat(value.getAppointmentCodeDurations()).containsEntry('A', 7);
        assertThat(value.getOpenAccessList()).containsExactly('A');
        assertThat(value.getBookingProviders()).isNotEmpty();
        assertThat(value.getApptCodes()).isNotEmpty();
    }

    private static final Map<String, Map<String, String>> BASELINE_XML = baselineXml();

    private static Map<String, Map<String, String>> baselineXml() {
        try (var input = JaxbImplementationCompatibilityUnitTest.class.getResourceAsStream("/xml/jaxb-moxy-baseline.json")) {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(input,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, String>>>() { });
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static final Map<String, String> LEGACY_CONTEXT_ERRORS = legacyContextErrors();
    private static final Set<String> INVALID_DEFAULTS = Set.of(
            BASE + "webserv.rest.to.model.ConsentTypeTo1",
            BASE + "webserv.rest.to.model.FormTo1",
            BASE + "webserv.rest.to.model.PrintPointTo1",
            BASE + "webserv.rest.to.model.PrintRxTo1");

    private static Map<String, String> legacyContextErrors() {
        try (var input = JaxbImplementationCompatibilityUnitTest.class
                .getResourceAsStream("/xml/jaxb-legacy-context-errors.json")) {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(input,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Throwable rootCause(Throwable failure) {
        if (failure == null) return null;
        while (failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private static String causes(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) text.append(cause).append('\n');
        return text.toString();
    }

    private static void compare(Class<?> type) throws Exception {
        JAXBContext moxy = null;
        try {
            moxy = context("org.eclipse.persistence.jaxb.JAXBContextFactory", type);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            String expected = LEGACY_CONTEXT_ERRORS.get(type.getName());
            assertThat(expected).as("unlisted MOXy context failure for " + type).isNotNull();
            String subject = expected.substring(expected.indexOf(": ") + 2);
            assertThat(causes(failure)).contains(subject.startsWith("Allergy.")
                    ? BASE + "commn.model." + subject : subject);
        }
        JAXBContext glassfish;
        try {
            glassfish = context("org.glassfish.jaxb.runtime.v2.ContextFactory", type);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            assertThat(moxy).as("Glassfish must not introduce a context failure").isNull();
            assertThat(LEGACY_CONTEXT_ERRORS).as("both providers reject this pre-existing invalid context")
                    .containsKey(type.getName());
            assertThat(causes(failure)).contains("no-arg default constructor");
            return;
        }
        for (boolean populated : new boolean[]{false, true}) {
            Object value = populated ? populated(type, new HashSet<>(), 0,
                    ((JAXBContextImpl) glassfish).getTypeInfoSet()) : instance(type);
            if (!populated && INVALID_DEFAULTS.contains(type.getName())) {
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> xml(glassfish, type, value)))
                        .satisfies(failure -> assertThat(rootCause(failure)).isInstanceOf(NullPointerException.class));
                JAXBContext old = moxy;
                if (old != null) assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> xml(old, type, value)))
                        .satisfies(failure -> assertThat(rootCause(failure)).isInstanceOf(NullPointerException.class));
                continue;
            }
            String newXml = xml(glassfish, type, value);
            structure(newXml);
            String frozen = BASELINE_XML.getOrDefault(type.getName(), Map.of()).get(populated ? "populated" : "default");
            boolean formatterFix = frozen != null && type.getName().endsWith(".BpmhForm2Bean");
            if (formatterFix) {
                // Run this even when the legacy context fails: the removed formatter must be
                // the only difference from the independently captured production XML.
                assertThat(frozen).contains("<formatter>");
                assertThat(newXml).doesNotContain("<formatter>");
                assertThat(structure(newXml)).isEqualTo(withoutFormatter(frozen));
            }
            if (moxy == null) continue; // No legacy XML existed: exact failure is pinned above.
            String oldXml = frozen == null ? xml(moxy, type, value) : frozen;
            assertThat(structure(newXml)).as(type + " populated=" + populated)
                    .isEqualTo(formatterFix ? withoutFormatter(oldXml) : structure(oldXml));
            Object read = glassfish.createUnmarshaller()
                    .unmarshal(new javax.xml.transform.stream.StreamSource(new StringReader(oldXml)), type).getValue();
            Object oldRead = moxy.createUnmarshaller()
                    .unmarshal(new javax.xml.transform.stream.StreamSource(new StringReader(oldXml)), type).getValue();
            // A few legacy entity setters normalize values or represent aliases for the same field.
            // Compare with the old reader's bound state, not with a DTO's absent equals() method.
            assertThat(structure(xml(glassfish, type, read)))
                    .as(type + " reads MOXy XML, populated=" + populated)
                    .isEqualTo(structure(xml(glassfish, type, oldRead)));
        }
    }

    private static JAXBContext context(String factory, Class<?> type) throws Exception {
        return (JAXBContext) Class.forName(factory).getMethod("createContext", Class[].class, Map.class)
                .invoke(null, new Class<?>[]{type}, Map.of());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String xml(JAXBContext context, Class<?> type, Object value) throws Exception {
        StringWriter writer = new StringWriter();
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        marshaller.marshal(new JAXBElement(new QName("urn:carlos:jaxb-compatibility", "value"), type, value), writer);
        return writer.toString();
    }

    private static List<Class<?>> boundClasses() throws Exception {
        Set<Class<?>> found = new java.util.TreeSet<>(java.util.Comparator.comparing(Class::getName));
        Path root = Path.of("src/main/java");
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith("package-info.java") || relative.endsWith("module-info.java")) continue;
                String source = Files.readString(path);
                if (source.contains("jakarta.xml.bind.annotation")
                        || relative.contains("/webserv/transfer_objects/")
                        || relative.contains("/webserv/rest/to/")
                        || relative.startsWith("ca/ontario/") || relative.startsWith("org/w3/")
                        || relative.contains("/hospitalReportManager/xsd/")) {
                    addType(Class.forName(relative.substring(0, relative.length() - 5).replace('/', '.'), false,
                            JaxbImplementationCompatibilityUnitTest.class.getClassLoader()), found);
                }
            }
        }
        for (Class<?> rootType : List.copyOf(found)) {
            try {
                JAXBContextImpl context = (JAXBContextImpl) context("org.glassfish.jaxb.runtime.v2.ContextFactory", rootType);
                for (Class<?> bound : context.getTypeInfoSet().beans().keySet()) {
                    if (bound.getName().startsWith(BASE) || bound.getName().startsWith("ca.ontario.")) addType(bound, found);
                }
            } catch (Exception _) {
                // compare() reports the specific invalid root; do not lose discovery of other roots.
            }
        }
        return List.copyOf(found);
    }

    private static void addType(Class<?> type, Set<Class<?>> found) {
        if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())
                && !type.isAnnotationPresent(XmlRegistry.class)
                && !jakarta.xml.ws.Service.class.isAssignableFrom(type)
                && !Throwable.class.isAssignableFrom(type)
                && Modifier.isPublic(type.getModifiers())
                && !com.fasterxml.jackson.databind.AnnotationIntrospector.class.isAssignableFrom(type)
                && !jakarta.xml.bind.annotation.adapters.XmlAdapter.class.isAssignableFrom(type)) found.add(type);
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (!nested.isSynthetic() && Stream.of(nested.getAnnotations())
                    .anyMatch(a -> a.annotationType().getPackageName().equals("jakarta.xml.bind.annotation"))) addType(nested, found);
        }
    }

    private static Object instance(Class<?> type) throws Exception {
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.getName().equals(BASE + "demographic.cdsimport.dto.CdsTemporalNativeDateDto"))
            return io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalNativeDateDto.from(null, null);
        if (type.getName().equals(BASE + "prevention.dto.PreventionDateDTO"))
            return io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO.from(null, null);
        if (type.getName().equals(BASE + "webserv.rest.to.model.ConsultationAttachment"))
            return new io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationAttachment(null, null, null, null);
        if (type.getName().equals(BASE + "webserv.rest.to.RestResponse"))
            return io.github.carlos_emr.carlos.webserv.rest.to.RestResponse.successResponse(null, null);
        if (type.getName().equals(BASE + "webserv.rest.to.GenericRestResponse")) {
            var factory = type.getDeclaredConstructor(Object.class, Object.class, Object.class,
                    io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus.class);
            factory.setAccessible(true);
            return factory.newInstance(null, null, null,
                    io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus.SUCCESS);
        }
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object value = constructor.newInstance();
        for (Class<?> owner = type; owner != Object.class; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && Date.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    if (field.get(value) != null) field.set(value, field.getType().getConstructor(long.class).newInstance(1784135655120L));
                }
            }
        }
        return value;
    }

    private static Object populated(Type generic, Set<Class<?>> parents, int depth, RuntimeTypeInfoSet model) throws Exception {
        Class<?> type = generic instanceof Class<?> c ? c
                : generic instanceof ParameterizedType p ? (Class<?>) p.getRawType() : Object.class;
        if (type == String.class || type == Object.class || type == java.io.Serializable.class) return "7";
        if (type == char.class || type == Character.class) return 'A';
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == byte.class || type == Byte.class) return (byte) 7;
        if (type == short.class || type == Short.class) return (short) 7;
        if (type == int.class || type == Integer.class) return 7;
        if (type == long.class || type == Long.class) return 7L;
        if (type == float.class || type == Float.class) return 7.25f;
        if (type == double.class || type == Double.class) return 7.25d;
        if (type == BigInteger.class) return BigInteger.valueOf(7);
        if (type == BigDecimal.class) return new BigDecimal("7.25");
        if (type == QName.class) return new QName("urn:synthetic", "code");
        GregorianCalendar date = new GregorianCalendar(TORONTO);
        date.clear();
        date.set(2026, Calendar.JULY, 15, 13, 14, 15);
        if (Date.class.isAssignableFrom(type)) return type.getConstructor(long.class).newInstance(date.getTimeInMillis());
        if (Calendar.class.isAssignableFrom(type)) return date;
        if (XMLGregorianCalendar.class.isAssignableFrom(type)) return DatatypeFactory.newInstance().newXMLGregorianCalendar(date);
        if (type.isEnum()) return instance(type);
        if (type == JAXBElement.class && generic instanceof ParameterizedType p) {
            Class<?> valueType = (Class<?>) p.getActualTypeArguments()[0];
            return new JAXBElement(new QName("urn:synthetic", "text"), valueType, populated(valueType, parents, depth, model));
        }
        if (java.text.DateFormat.class.isAssignableFrom(type)) return new java.text.SimpleDateFormat("yyyy-MM-dd");
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), 1);
            Array.set(array, 0, populated(type.getComponentType(), parents, depth, model));
            return array;
        }
        if (Collection.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            Collection<Object> values = !type.isInterface() && !Modifier.isAbstract(type.getModifiers())
                    ? (Collection<Object>) instance(type)
                    : Set.class.isAssignableFrom(type) ? new HashSet<>() : new ArrayList<>();
            if (generic instanceof ParameterizedType p) values.add(populated(p.getActualTypeArguments()[0], parents, depth, model));
            return values;
        }
        if (Map.class.isAssignableFrom(type)) {
            Map<Object, Object> values = new LinkedHashMap<>();
            if (generic instanceof ParameterizedType p) values.put(populated(p.getActualTypeArguments()[0], parents, depth, model),
                    populated(p.getActualTypeArguments()[1], parents, depth, model));
            return values;
        }
        if (depth > 5 || parents.contains(type)) return null;
        if (!(type.getName().startsWith(BASE) || type.getName().startsWith("ca.ontario.")
                || type.getName().startsWith("org.w3."))) throw new IllegalArgumentException("Missing fixture for " + generic);
        Set<Class<?>> branch = new HashSet<>(parents);
        branch.add(type);
        Object bean = instance(type);
        if (type.getName().endsWith(".ProfessionalSpecialist")) {
            Field address = backingField(type, "addressArray");
            address.setAccessible(true);
            address.set(bean, new String[]{"7,", "7,", "7,", "7,", "7,"});
        }
        for (RuntimeClassInfo info = model.beans().get(type); info != null; info = info.getBaseClass()) {
            for (RuntimePropertyInfo property : info.getProperties()) {
                // These aliases all mutate the same comma-delimited address. Populate its backing
                // representation once, rather than exercising UI setter normalization as a fixture builder.
                if (type.getName().endsWith(".ProfessionalSpecialist") && Set.of(
                        "address", "address2", "city", "country", "postal", "province", "organizationName")
                        .contains(property.getName())) continue;
                Object value = populated(propertyType(type, property), branch, depth + 1, model);
                if (value instanceof String && property.getName().equals("dateOfBirth")) value = "15";
                if (value instanceof String && property.getName().equals("monthOfBirth")) value = "07";
                if (value instanceof String && property.getName().equals("yearOfBirth")) value = "1980";
                if (type.getName().endsWith(".BillingONItem") && property.getName().equals("fee")) value = "7.25";
                if (type.getName().endsWith(".PhoneNumber") && property.getName().equals("content")) {
                    value = new ArrayList<>(List.of(new io.github.carlos_emr.carlos.hospitalReportManager.xsd.ObjectFactory()
                            .createPhoneNumberNumber("5550100")));
                }
                if (type.getName().endsWith(".ProfessionalSpecialist") && property.getName().equals("streetAddress"))
                    value = "7,7,7,7,7";
                if (type.getName().endsWith(".ProfessionalSpecialist") && property.getName().equals("addressArray"))
                    value = new String[]{"7,", "7,", "7,", "7,", "7,"};
                if (property.getName().equals("content") && type.getName().contains("SearchResponse")) {
                    var document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().newDocument();
                    var element = document.createElementNS("urn:synthetic", "content");
                    element.setTextContent("Synthetic & <value> é");
                    value = new ArrayList<>(List.of(element));
                }
                try {
                    Field backing = backingField(type, property.getName());
                    if (backing != null && backing.getType().isAssignableFrom(value == null ? backing.getType() : value.getClass())) {
                        backing.setAccessible(true);
                        backing.set(bean, value);
                    } else property.getAccessor().setUnadapted(bean, value);
                } catch (IllegalArgumentException mismatch) {
                    throw new IllegalArgumentException(type.getName() + "." + property.getName()
                            + " raw=" + property.getRawType() + " value=" + (value == null ? "null" : value.getClass()), mismatch);
                } catch (org.glassfish.jaxb.runtime.api.AccessorException readOnly) {
                    Object existing = property.getAccessor().getUnadapted(bean);
                    if (existing instanceof Collection collection && value instanceof Collection values) {
                        collection.clear();
                        collection.addAll(values);
                    } else {
                        throw new IllegalStateException(type.getName() + "." + property.getName(), readOnly);
                    }
                }
            }
        }
        return bean;
    }

    private record FixtureType(Class<?> raw, Type[] arguments) implements ParameterizedType {
        @Override public Type[] getActualTypeArguments() { return arguments.clone(); }
        @Override public Type getRawType() { return raw; }
        @Override public Type getOwnerType() { return null; }
    }

    /** Retain collection/map arguments, including type variables resolved against a concrete response. */
    private static Type propertyType(Class<?> owner, RuntimePropertyInfo property) throws Exception {
        Field field = backingField(owner, property.getName());
        org.springframework.core.ResolvableType resolved = field == null ? null
                : org.springframework.core.ResolvableType.forField(field, owner);
        if (resolved == null) {
            for (var descriptor : java.beans.Introspector.getBeanInfo(owner).getPropertyDescriptors()) {
                if (descriptor.getName().equals(property.getName()) && descriptor.getReadMethod() != null) {
                    resolved = org.springframework.core.ResolvableType.forMethodReturnType(descriptor.getReadMethod(), owner);
                    break;
                }
            }
        }
        // For a non-container use JAXB's property type, not a differently typed backing field
        // (e.g. boolean properties backed by a char flag).
        Class<?> raw = resolved == null ? null : resolved.resolve();
        if (raw == null || !(Collection.class.isAssignableFrom(raw) || Map.class.isAssignableFrom(raw)))
            return property.getRawType();
        return fixtureType(resolved);
    }

    private static Type fixtureType(org.springframework.core.ResolvableType type) {
        Class<?> raw = type.resolve(Object.class);
        var generics = type.getGenerics();
        if (generics.length == 0) return raw;
        Type[] arguments = Stream.of(generics).map(JaxbImplementationCompatibilityUnitTest::fixtureType).toArray(Type[]::new);
        return new FixtureType(raw, arguments);
    }

    private static Field backingField(Class<?> type, String property) {
        for (Class<?> owner = type; owner != Object.class; owner = owner.getSuperclass()) {
            try { return owner.getDeclaredField(property); } catch (NoSuchFieldException _) { /* inherited property */ }
        }
        return null;
    }

    private static String withoutFormatter(String xml) throws Exception {
        var factory = XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        Element root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        for (Node child = root.getFirstChild(); child != null; ) {
            Node next = child.getNextSibling();
            if (child instanceof Element element && "formatter".equals(element.getLocalName())) root.removeChild(child);
            child = next;
        }
        return structure(root);
    }

    /** Namespace-aware tree; namespace declaration placement and attribute order are irrelevant. */
    private static String structure(String xml) throws Exception {
        var factory = XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        return structure(factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement());
    }

    private static String structure(Element element) {
        StringBuilder result = new StringBuilder("{" + element.getNamespaceURI() + "}" + element.getLocalName());
        Map<String, String> attributes = new TreeMap<>();
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attribute = element.getAttributes().item(i);
            if ("http://www.w3.org/2000/xmlns/".equals(attribute.getNamespaceURI())) continue;
            String value = attribute.getNodeValue();
            if ("http://www.w3.org/2001/XMLSchema-instance".equals(attribute.getNamespaceURI())
                    && "type".equals(attribute.getLocalName())) {
                int colon = value.indexOf(':');
                value = "{" + element.lookupNamespaceURI(colon < 0 ? null : value.substring(0, colon)) + "}"
                        + value.substring(colon + 1);
            }
            attributes.put("{" + attribute.getNamespaceURI() + "}" + attribute.getLocalName(), value);
        }
        result.append(attributes).append('[');
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element nested) result.append(structure(nested));
            else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
                result.append(child.getNodeValue());
        }
        return result.append(']').toString();
    }
}
