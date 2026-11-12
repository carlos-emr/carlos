/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.SchemaOutputResolver;
import jakarta.xml.bind.UnmarshalException;
import java.beans.Introspector;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

/** Audits every Date bean property in the discovered SOAP transfer-object graph, not a hand-picked sample. */
@Tag("unit")
@Tag("xml")
@ResourceLock("java.util.TimeZone")
class SoapOptionalDateInventoryUnitTest {
    private static final List<String> FACTORIES = List.of("org.glassfish.jaxb.runtime.v2.ContextFactory",
            "org.eclipse.persistence.jaxb.JAXBContextFactory");
    private record ContextKey(Class<?> type, String factory) {}
    private static final Map<ContextKey, JAXBContext> CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<ContextKey, List<String>> SCHEMAS = new ConcurrentHashMap<>();
    private static final Date VALUE = Date.from(Instant.parse("2026-11-09T04:12:33.120Z"));
    private record Binding(Class<?> type, String name, Method getter) {
        @Override public String toString() { return type.getSimpleName() + "." + name; }
    }
    @JsonFilter("dateFieldsOnly")
    private abstract static class DateFieldsOnly {}

    static List<Binding> inventory() throws Exception {
        List<Binding> bindings = new ArrayList<>();
        for (Class<?> type : JaxbImplementationCompatibilityUnitTest.boundClasses()) {
            if (!type.getPackageName().equals("io.github.carlos_emr.carlos.webserv.transfer_objects")) continue;
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() != Date.class
                        || !method.getName().startsWith("get")) continue;
                type.getMethod("set" + method.getName().substring(3), Date.class);
                bindings.add(new Binding(type, Introspector.decapitalize(method.getName().substring(3)), method));
            }
        }
        bindings.sort(Comparator.comparing(Binding::toString));
        assertThat(bindings).as("SOAP Date properties discovered from the production JAXB graph").isNotEmpty();
        Files.write(Path.of("target/soap-date-property-inventory.txt"), bindings.stream().map(Binding::toString).toList());
        return bindings;
    }

    static Stream<Binding> properties() throws Exception { return inventory().stream(); }
    static Stream<Arguments> xmlProperties() throws Exception {
        return inventory().stream().flatMap(binding -> FACTORIES.stream().map(factory -> Arguments.of(binding, factory)));
    }
    static Stream<Arguments> blanks() throws Exception {
        return inventory().stream().flatMap(binding -> FACTORIES.stream().flatMap(factory -> Stream.of("", " \t\r\n ")
                .map(value -> Arguments.of(binding, factory, value))));
    }

    private static JAXBContext context(Binding binding, String factory) {
        return CONTEXTS.computeIfAbsent(new ContextKey(binding.type, factory), key -> {
            try { return (JAXBContext) Class.forName(key.factory).getMethod("createContext", Class[].class, Map.class)
                    .invoke(null, new Class<?>[]{key.type}, Map.of()); }
            catch (Exception failure) { throw new IllegalStateException(key.type.getName(), failure); }
        });
    }

    private static Object read(Binding binding, String factory, String lexical) throws Exception {
        var reader = context(binding, factory).createUnmarshaller();
        reader.setEventHandler(event -> false); // CXF SOAP's strict failure policy.
        return reader.unmarshal(new StreamSource(new StringReader("<value><" + binding.name + ">" + lexical
                + "</" + binding.name + "></value>")), binding.type).getValue();
    }

    @ParameterizedTest
    @MethodSource("blanks")
    void shouldReadBlankOptionalDates_asNullWithoutValidationRecovery(Binding binding, String factory, String lexical) throws Exception {
        assertThat(binding.getter.invoke(read(binding, factory, lexical))).isNull();
    }

    @ParameterizedTest
    @MethodSource("xmlProperties")
    void shouldRejectMalformedDates_withoutSubstitutingNull(Binding binding, String factory) {
        Throwable failure = catchThrowable(() -> read(binding, factory, "not-a-date"));
        if (factory.startsWith("org.glassfish.")) assertThat(failure).isInstanceOf(UnmarshalException.class);
        else assertThat(failure).isInstanceOf(org.eclipse.persistence.exceptions.ConversionException.class);
        assertThat(failure).hasRootCauseInstanceOf(XmlCompatibilityValueException.class);
    }

    @ParameterizedTest
    @MethodSource("xmlProperties")
    void shouldPreserveARealInstant_withFractionalSeconds(Binding binding, String factory) throws Exception {
        assertThat(binding.getter.invoke(read(binding, factory, "2026-11-09T04:12:33.120Z"))).isEqualTo(VALUE);
    }

    @ParameterizedTest
    @MethodSource("xmlProperties")
    void shouldRetainPublishedDateTimeTypes_inGeneratedSchemas(Binding binding, String factory) throws Exception {
        // MOXy retains a generated-schema session on its context; capture each complete output once.
        List<String> schemas = SCHEMAS.computeIfAbsent(new ContextKey(binding.type, factory), key -> {
            List<StringWriter> output = new ArrayList<>();
            try {
                context(binding, factory).generateSchema(new SchemaOutputResolver() {
                    @Override public Result createOutput(String namespace, String file) {
                        StringWriter writer = new StringWriter(); output.add(writer);
                        StreamResult result = new StreamResult(writer); result.setSystemId("memory:/" + file); return result;
                    }
                });
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            assertThat(output).isNotEmpty();
            return output.stream().map(StringWriter::toString).toList();
        });
        int matched = 0;
        for (String schema : schemas) {
            var xmlFactory = XmlUtils.createSecureDocumentBuilderFactory(); xmlFactory.setNamespaceAware(true);
            var document = xmlFactory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(schema)));
            var elements = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
            for (int index = 0; index < elements.getLength(); index++) {
                var element = (org.w3c.dom.Element) elements.item(index);
                if (!binding.name.equals(element.getAttribute("name"))) continue;
                String type = element.getAttribute("type");
                assertThat(type.substring(type.indexOf(':') + 1)).isEqualTo("dateTime");
                assertThat(element.lookupNamespaceURI(type.contains(":") ? type.substring(0, type.indexOf(':')) : null))
                        .isEqualTo(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                matched++;
            }
        }
        assertThat(matched).isPositive();
    }

    @ParameterizedTest
    @MethodSource("properties")
    void shouldKeepDeclaredJsonDateRepresentations_whenXmlAdaptersAreApplied(Binding binding) throws Exception {
        Object bean = binding.type.getConstructor().newInstance();
        binding.type.getMethod("set" + binding.getter.getName().substring(3), Date.class).invoke(bean, VALUE);
        for (boolean legacyDates : List.of(false, true)) {
            try (var selected = org.mockito.Mockito.mockStatic(
                    io.github.carlos_emr.carlos.webserv.rest.util.JsonDateProfile.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                selected.when(io.github.carlos_emr.carlos.webserv.rest.util.JsonDateProfile::current).thenReturn(legacyDates
                        ? io.github.carlos_emr.carlos.webserv.rest.util.JsonDateProfile.LEGACY_VALUE_INFERENCE
                        : io.github.carlos_emr.carlos.webserv.rest.util.JsonDateProfile.DECLARED);
                for (boolean oauth : List.of(false, true)) {
                    var mapper = oauth ? ProductionRestObjectMappers.oauthSurface() : ProductionRestObjectMappers.sessionSurface();
                    var reference = mapper.valueToTree(Map.of("expected", VALUE)).get("expected");
                    if (!legacyDates && oauth && binding.type == io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer.class
                            && binding.name.equals("dob")) {
                        // #5895 explicitly declares the OAuth DOB contract; its legacy profile retains the old value.
                        reference = com.fasterxml.jackson.databind.node.TextNode.valueOf(
                                new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(VALUE));
                    }
                    var filtered = mapper.copy().addMixIn(binding.type, DateFieldsOnly.class).setFilterProvider(
                            new SimpleFilterProvider().addFilter("dateFieldsOnly", SimpleBeanPropertyFilter.filterOutAllExcept(binding.name)));
                    var actual = filtered.valueToTree(bean);
                    assertThat(actual.has(binding.name)).isTrue();
                    assertThat(actual.get(binding.name)).isEqualTo(reference);
                }
            }
        }
    }
}
