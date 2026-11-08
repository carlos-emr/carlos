/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.fhir.utils;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps production FHIR JSON encoders behind the compatibility boundary, including JSP fragments. */
@Tag("unit")
@Tag("fhir")
class FhirJsonEncodingPolicyUnitTest {
    private static final List<String> SOURCE_EXTENSIONS = List.of(".java", ".jsp", ".jspf", ".jspx", ".tag", ".tagx");
    private static final String ENCODER_METHOD = "encode(?:Resource)?To(?:String|Writer)\\(";
    private static final Pattern ENCODER = Pattern.compile(ENCODER_METHOD);
    private static final Pattern HAPI = Pattern.compile(
            "\\b(?:IParser|FhirContext|EncodingEnum|newJsonParser|newXmlParser)\\b|(?:ca\\.uhn\\.fhir|org\\.hl7\\.fhir)\\.");
    private static final Pattern EXPLICIT_XML = Pattern.compile(
            "newXmlParser\\(\\)(?:\\.setPrettyPrint\\((?:true|false)\\))?\\." + ENCODER_METHOD);
    private static final Pattern EXPLICIT_XML_ENUM = Pattern.compile(
            "EncodingEnum\\.XML\\.newParser\\([^()]++\\)\\." + ENCODER_METHOD);
    private static final Pattern EXPLICIT_BASE64 = Pattern.compile(
            "Base64\\.get(?:Url|Mime)?Encoder\\(\\)\\.encodeToString\\(");

    private static boolean isSource(Path path) {
        String name = path.getFileName().toString();
        return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static boolean bypassesCompatibility(String source) {
        // Recognize straightforward explicit non-JSON receivers, rather than assuming that every
        // encoder in a file containing a JSON reader also writes JSON. Indirect
        // HAPI encoders must use the adapter or make their XML factory explicit.
        String compact = source.replaceAll("\\s+", "");
        String remaining = EXPLICIT_XML.matcher(compact).replaceAll("");
        remaining = EXPLICIT_XML_ENUM.matcher(remaining).replaceAll("");
        remaining = EXPLICIT_BASE64.matcher(remaining).replaceAll("");
        return HAPI.matcher(source).find() && ENCODER.matcher(remaining).find();
    }

    @Test
    void shouldCentralizeOutboundJsonEncoding_inProductionSources() throws Exception {
        var bypasses = new ArrayList<Path>();
        try (var paths = Files.walk(Path.of("src/main"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(FhirJsonEncodingPolicyUnitTest::isSource)
                    .filter(p -> !p.endsWith("FhirJsonCompatibility.java")).toList()) {
                // The policy only reads ASCII Java tokens; legacy fragment encodings
                // must not make the guard fail before it can inspect their scriptlets.
                if (bypassesCompatibility(Files.readString(path, StandardCharsets.ISO_8859_1))) {
                    bypasses.add(path);
                }
            }
        }
        assertThat(bypasses).as("Outbound FHIR JSON must use the compatibility encoder").isEmpty();
        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/prevention/dhirSubmission.jsp")))
                .contains("DHIR.encodeSubmission(bundle,")
                .contains("catch (IOException | IllegalStateException e)");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ctx.newJsonParser().encodeResourceToString(resource)",
            "ctx.newJsonParser().encodeResourceToWriter(resource, writer)",
            "ctx.newJsonParser().encodeToString(resource)",
            "ctx.newJsonParser().encodeToWriter(resource, writer)",
            "EncodingEnum.JSON.newParser(ctx).encodeToString(resource)",
            "void send(IParser parser) { parser.encodeResourceToString(resource); }",
            "void send(IParser parser) { parser.encodeToWriter(resource, writer); }"
    })
    void shouldRejectBypasses_withEachHapiEncoderApi(String source) {
        assertThat(bypassesCompatibility(source)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ctx.newJsonParser().parseResource(json); ctx.newXmlParser().encodeResourceToString(resource);",
            "ctx.newJsonParser().parseResource(json); ctx.newXmlParser().setPrettyPrint(true).encodeToString(resource);",
            "EncodingEnum.XML.newParser(ctx).encodeToWriter(resource, writer);",
            "IParser parser = ctx.newJsonParser(); parser.parseResource(json); Base64.getEncoder().encodeToString(data);"
    })
    void shouldAllowNonJsonEncoders_whenTheReceiverIsExplicit(String source) {
        assertThat(bypassesCompatibility(source)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Source.java", "Page.jsp", "Fragment.jspf", "Page.jspx", "Component.tag", "Component.tagx"})
    void shouldInspectAllScriptletSources_withSupportedExtensions(String name) {
        assertThat(isSource(Path.of(name))).isTrue();
    }
}
