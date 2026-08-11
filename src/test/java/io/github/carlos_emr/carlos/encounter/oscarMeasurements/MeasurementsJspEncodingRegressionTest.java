/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Verifies the Measurements JSP's canonicalization, context encoding, and localized confirmation contracts.
 */
@DisplayName("Measurements.jsp output encoding regressions")
@Tag("unit")
@Tag("clinical")
class MeasurementsJspEncodingRegressionTest {

    private static final int MAX_PARENT_SEARCH_DEPTH = 8;
    private static final Pattern EL_EXPRESSION = Pattern.compile("\\$\\{([^}]*)}");
    private static final Pattern ATTRIBUTE_START = Pattern.compile("([\\w:-]+)\\s*=\\s*([\"'])");
    private static final Pattern CONTROL_EXPRESSION = Pattern.compile(
            "(?:empty css|not empty css|not empty groupName|not empty measurementType\\.lastMInstrc"
                    + "|fn:split\\(measurementType\\.measuringInstrc\\.substring\\(12\\), ','\\)"
                    + "|measurementType\\.measuringInstrc\\.startsWith\\('Choose radio'\\)"
                    + "|measurementTypes\\.measurementTypeVector|sessionScope\\[attributeName]\\.measuringInstructionList)");
    private static final Pattern SAFE_RENDERED_EXPRESSION = Pattern.compile(
            "(?:ctr\\.index|instructionStatus\\.index|optionStatus\\.index"
                    + "|fn:length\\(measurementTypes\\.measurementTypeVector\\))");
    private static final Path JSP = resolveProjectPath(Path.of("src", "main", "webapp", "WEB-INF", "jsp",
            "encounter", "oscarMeasurements", "Measurements.jsp"));
    private static final Path ADD_MEASUREMENT_JSP = resolveProjectPath(Path.of("src", "main", "webapp", "WEB-INF",
            "jsp", "encounter", "oscarMeasurements", "AddMeasurementData.jsp"));
    private static final Path CASE_MANAGEMENT_RECEIVER = resolveProjectPath(Path.of("src", "main", "webapp", "js",
            "newCaseManagementView.js.jsp"));
    private static final List<Path> RESOURCE_BUNDLES = List.of("en", "es", "fr", "pl", "pt_BR").stream()
            .map(locale -> resolveProjectPath(Path.of("src", "main", "resources", "oscarResources_" + locale + ".properties")))
            .toList();
    private static final String PARENT_CHANGED_KEY =
            "encounter.oscarMeasurements.Measurements.msgParentChanged";

    @Test
    @DisplayName("should canonicalize the demographic identifier before rendering patient data")
    void shouldCanonicalizeDemographicIdentifier_beforeRenderingPatientData() throws Exception {
        String jsp = readJsp();

        assertThat(jsp)
                .contains("ConversionUtils.fromIntString(")
                .contains("getNameAgeString(")
                .doesNotContain("String demo = request.getParameter(\"demographicNo\")")
                .doesNotContain("<oscar:nameage");
    }

    @Test
    @DisplayName("should keep sensitive output expressions behind an encoder")
    void shouldKeepSensitiveOutputExpressions_behindAnEncoder() throws Exception {
        String jsp = readJsp();

        Matcher expressions = EL_EXPRESSION.matcher(jsp);
        while (expressions.find()) {
            String expression = expressions.group(1).trim();
            if (!isNonRenderingExpression(jsp, expressions.start(), expression)
                    && !SAFE_RENDERED_EXPRESSION.matcher(expression).matches()) {
                assertThat(expression)
                        .as("rendered sensitive expression at character %s", expressions.start())
                        .startsWith(expectedEncoderForSink(jsp, expressions.start()));
            }
        }

        assertThat(jsp)
                .doesNotContain("<oscar:nameage")
                .doesNotContain("request.getServerName()")
                .doesNotContain("elements[\"value(parentChanged)\"]")
                .contains("elements[\"parentChanged\"].value = \"true\";")
                .contains("<fmt:param value=\"${patientNameAge}\"/>")
                .contains("<carlos:encode value='<%= error %>' context=\"html\"/>")
                .contains("Trusted clinic decision-support template HTML")
                .contains("<%=measurementManager.getDShtml(groupName)%>");
    }

    @Test
    @DisplayName("should neutralize hostile values in every output context used by the page")
    void shouldNeutralizeHostileValues_inEveryOutputContext() {
        String hostile = "\"><script>alert('measurements')</script>&";

        assertThat(SafeEncode.forHtmlContent(hostile))
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
        assertThat(SafeEncode.forHtmlAttribute(hostile))
                .doesNotContain("\"")
                .doesNotContain("<script>");
        assertThat(SafeEncode.forJavaScript(hostile))
                .doesNotContain("\"")
                .doesNotContain("'")
                .doesNotContain("</script>");

        String encodedUriComponent = SafeEncode.forUriComponent(hostile);
        assertThat(encodedUriComponent)
                .doesNotContain("\"")
                .doesNotContain("<")
                .doesNotContain("&");
        assertThat(SafeEncode.forJavaScriptAttribute(encodedUriComponent))
                .doesNotContain("\"")
                .doesNotContain("'")
                .doesNotContain("<");
    }

    @Test
    @DisplayName("should associate radio controls with their visible labels")
    void shouldAssociateRadioControls_withVisibleLabels() throws Exception {
        String jsp = readJsp();

        assertThat(jsp)
                .contains("id=\"inputMInstrc-${ctr.index}-${instructionStatus.index}\"")
                .contains("for=\"inputMInstrc-${ctr.index}-${instructionStatus.index}\"")
                .contains("id=\"inputValue-${ctr.index}-${optionStatus.index}\"")
                .contains("for=\"inputValue-${ctr.index}-${optionStatus.index}\"");
    }

    @Test
    @DisplayName("should keep measurement messages on the same origin")
    void shouldKeepMeasurementMessages_onSameOrigin() throws Exception {
        String jsp = readJsp();
        String addMeasurementJsp = Files.readString(ADD_MEASUREMENT_JSP, StandardCharsets.UTF_8);
        String receiver = Files.readString(CASE_MANAGEMENT_RECEIVER, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("if (!response.ok)")
                .contains("if (window.opener && !window.opener.closed)")
                .contains("window.opener.postMessage(data, window.location.origin)")
                .doesNotContain("postMessage(data, \"*\")");
        assertThat(addMeasurementJsp)
                .contains("opener.opener.postMessage(data, window.location.origin)")
                .doesNotContain("postMessage(data, \"*\")");
        assertThat(receiver)
                .contains("if (event.origin !== window.location.origin)")
                .contains("String(data.demographicNo) !== String(demographicNo)")
                .contains("try {")
                .contains("data = JSON.parse(data);");
    }

    @Test
    @DisplayName("should let every locale position the patient label in the confirmation")
    void shouldParameterizeParentChangedMessage_forEverySupportedLocale() throws Exception {
        for (Path bundle : RESOURCE_BUNDLES) {
            Properties messages = new Properties();
            try (var reader = Files.newBufferedReader(bundle, StandardCharsets.UTF_8)) {
                messages.load(reader);
            }
            String pattern = messages.getProperty(PARENT_CHANGED_KEY);
            assertThat(pattern).as("parent-changed message in %s", bundle.getFileName()).isNotNull();
            String formatted = MessageFormat.format(pattern, "PATIENT_LABEL");

            assertThat(formatted)
                    .as("parameterized parent-changed message in %s", bundle.getFileName())
                    .contains("PATIENT_LABEL")
                    .doesNotContain("{0}", "&#");
            if (bundle.getFileName().toString().equals("oscarResources_fr.properties")) {
                assertThat(formatted).contains("L'\u00e9cran", "L'information");
            }
            if (bundle.getFileName().toString().equals("oscarResources_pl.properties")) {
                assertThat(formatted).contains("si\u0119 ju\u017c");
            }
        }
    }

    private static String readJsp() throws Exception {
        return Files.readString(JSP, StandardCharsets.UTF_8);
    }

    private static boolean isNonRenderingExpression(String jsp, int expressionStart, String expression) {
        if (CONTROL_EXPRESSION.matcher(expression).matches()) {
            return true;
        }
        int tagStart = jsp.lastIndexOf('<', expressionStart);
        int tagEnd = jsp.indexOf('>', tagStart);
        return tagStart >= 0 && tagEnd >= expressionStart
                && jsp.substring(tagStart, expressionStart).startsWith("<fmt:param ");
    }

    private static String expectedEncoderForSink(String jsp, int expressionStart) {
        Matcher attributes = ATTRIBUTE_START.matcher(jsp);
        String containingAttribute = "";
        while (attributes.find() && attributes.start() < expressionStart) {
            char quote = attributes.group(2).charAt(0);
            int attributeEnd = jsp.indexOf(quote, attributes.end());
            if (attributeEnd >= expressionStart) {
                containingAttribute = attributes.group(1);
            }
        }
        if (!containingAttribute.isEmpty()) {
            return containingAttribute.toLowerCase().startsWith("on")
                    ? "carlos:forJavaScriptAttribute("
                    : "carlos:forHtmlAttribute(";
        }

        int scriptStart = jsp.lastIndexOf("<script", expressionStart);
        int scriptEnd = jsp.lastIndexOf("</script", expressionStart);
        return scriptStart > scriptEnd ? "carlos:forJavaScript(" : "carlos:forHtmlContent(";
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }
}
