/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.xml.bind.SchemaOutputResolver;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compiles the generated binding inventory and distinguishes shared historical failures.
 * @since 2026-11-07
 */
@Tag("unit")
@Tag("xml")
class JaxbGeneratedSchemaUnitTest extends CarlosUnitTestBase {
    // Independently reproduced on the verified beta6 package and the #5710 merge.
    // These shared failures remain visible baseline gaps, not newly accepted schemas.
    private static final Set<String> SHARED_HRM_FAILURES = Set.of("Demographics", "Demographics$Contact",
            "OmdCds", "PatientRecord", "PhoneNumber");
    @TempDir
    Path directory;

    /** Uses the complete binding inventory so nested DTO additions cannot silently escape validation. */
    @TestFactory
    Stream<DynamicTest> shouldCompileGeneratedSchema_forEveryDiscoveredContext() throws Exception {
        List<Class<?>> types = JaxbImplementationCompatibilityUnitTest.boundClasses();
        assertThat(types).hasSizeGreaterThan(330);
        return types.stream().map(type -> DynamicTest.dynamicTest(type.getName(), () -> compile(type)));
    }

    /** Compiles actual generated schemas while retaining precisely identified shared baseline failures. */
    private void compile(Class<?> type) throws Exception {
        if (type == io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationAttachment.class) {
            assertThatThrownBy(() -> org.glassfish.jaxb.runtime.v2.ContextFactory.createContext(
                    new Class<?>[]{type}, Map.of())).hasMessageContaining("no-arg default constructor");
            return;
        }
        var context = org.glassfish.jaxb.runtime.v2.ContextFactory.createContext(new Class<?>[]{type}, Map.of());
        Path root = Files.createTempDirectory(directory, "schema-");
        List<File> schemas = new ArrayList<>();
        context.generateSchema(new SchemaOutputResolver() {
            @Override
            public Result createOutput(String namespace, String name) {
                File output = root.resolve(name).toFile();
                schemas.add(output);
                return new StreamResult(output);
            }
        });
        if (schemas.isEmpty()) {
            // An anonymous type without a root produces no standalone schema. Its owning
            // root contexts are still compiled below; never feed an empty Source[] to JAXP,
            // which would silently create an unconstrained validator.
            assertThat(type.getAnnotation(jakarta.xml.bind.annotation.XmlType.class)).isNotNull();
            assertThat(type.getAnnotation(jakarta.xml.bind.annotation.XmlType.class).name()).isEmpty();
            assertThat(type.isAnnotationPresent(jakarta.xml.bind.annotation.XmlRootElement.class)).isFalse();
            return;
        }
        var validator = SchemaFactory.newDefaultInstance();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        // Imports resolve only among the generated local files; no network schemas are allowed.
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        Source[] sources = schemas.stream().map(StreamSource::new).toArray(Source[]::new);
        if (type.getPackageName().equals("io.github.carlos_emr.carlos.hospitalReportManager.xsd")
                && SHARED_HRM_FAILURES.contains(type.getName().substring(type.getPackageName().length() + 1))) {
            assertThatThrownBy(() -> validator.newSchema(sources)).isInstanceOf(org.xml.sax.SAXParseException.class)
                    .hasMessageContaining("src-resolve:").hasMessageContaining("tns:ContactPurpose")
                    .hasMessageContaining("attribute declaration");
        } else {
            // In particular, do not strip illegal final attributes from anonymous map types.
            validator.newSchema(sources);
        }
    }
}
