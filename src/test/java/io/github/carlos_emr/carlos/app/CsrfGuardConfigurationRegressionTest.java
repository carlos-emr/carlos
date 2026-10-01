/*
 * This file is part of the CARLOS project.
 *
 * Copyright (c) 2026 CARLOS Contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Project repository: https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import jakarta.servlet.ServletConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.owasp.csrfguard.config.PropertiesConfigurationProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage for the CSRFGuard properties file.
 *
 * @since 2026-05-31
 */
@Tag("unit")
@Tag("security")
@DisplayName("CSRFGuard configuration")
class CsrfGuardConfigurationRegressionTest {

    private static final Path CSRF_GUARD_PROPERTIES =
            Path.of("src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties");
    private static final String PRNG_PROPERTY = "org.owasp.csrfguard.PRNG";
    private static final String PRNG_PROVIDER_PROPERTY = "org.owasp.csrfguard.PRNG.Provider";
    private static final String SOURCE_FILE_PROPERTY = "org.owasp.csrfguard.JavascriptServlet.sourceFile";
    private static final String CLASSPATH_PREFIX = "classpath:";
    /** The template shipped inside the CSRFGuard jar, which the CARLOS copy is patched from. */
    private static final String UPSTREAM_TEMPLATE = "META-INF/csrfguard.js";
    /** JavaScriptServlet placeholders: %NAME%, some written inside single quotes. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%[A-Z_]+%");

    @Test
    @DisplayName("should use DRBG without provider constraint")
    void shouldUseDrbgWithoutProviderConstraint_whenCsrfGuardConfigLoaded() throws IOException {
        Properties properties = loadCsrfGuardProperties();

        assertThat(properties.getProperty(PRNG_PROPERTY)).isEqualTo("DRBG");
        assertThat(properties).doesNotContainKey(PRNG_PROVIDER_PROPERTY);
    }

    @Test
    @DisplayName("should resolve configured PRNG without provider constraint")
    void shouldResolveConfiguredPrng_withoutProviderConstraint() throws IOException {
        Properties properties = loadCsrfGuardProperties();

        assertThatCode(() -> SecureRandom.getInstance(properties.getProperty(PRNG_PROPERTY)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should serve the CARLOS-patched client template from the classpath")
    void shouldServePatchedClientTemplate_fromClasspath() throws IOException {
        String sourceFile = loadCsrfGuardProperties().getProperty(SOURCE_FILE_PROPERTY);

        // The classpath: form is what makes JavaScriptServlet read WEB-INF/classes
        // rather than the jar's META-INF copy (issue #4130).
        assertThat(sourceFile).startsWith(CLASSPATH_PREFIX);
        String template = readClasspathResource(sourceFile.substring(CLASSPATH_PREFIX.length()).trim());

        assertThat(template)
                .as("each issue #4130 patch must still be wired in")
                .contains("function carlosTokenFields(form, tokenName)")
                .contains("injectToElements(carlosWithNestedForms(addedNodes)")
                .contains("carlosHookFormSubmission();")
                .doesNotContain("Object.keys(form.elements).filter");
    }

    @Test
    @DisplayName("should resolve the configured sourceFile through CSRFGuard's own loader")
    void shouldResolveConfiguredSourceFile_throughCsrfGuardLoader() throws Exception {
        String sourceFile = loadCsrfGuardProperties().getProperty(SOURCE_FILE_PROPERTY);

        // The same private resolver JavaScriptServlet's configuration uses, so a
        // spelling CSRFGuard does not understand fails here rather than as a
        // missing /csrfguard script (and a 403 on every form) after deployment.
        // Reflection is deliberate: the method is private upstream, and if a
        // CSRFGuard upgrade renames it this test must be revisited with it.
        Method resolver = PropertiesConfigurationProvider.class
                .getDeclaredMethod("retrieveJavaScriptTemplateCode", ServletConfig.class, String.class);
        resolver.setAccessible(true);
        String template = (String) resolver.invoke(null, mock(ServletConfig.class), sourceFile);

        assertThat(template)
                .as("CSRFGuard must load " + sourceFile + " itself")
                .isNotBlank()
                .contains("function carlosHookFormSubmission()");
    }

    @Test
    @DisplayName("should keep every placeholder of the upstream CSRFGuard template")
    void shouldKeepEveryUpstreamPlaceholder_inPatchedTemplate() throws IOException {
        String sourceFile = loadCsrfGuardProperties().getProperty(SOURCE_FILE_PROPERTY);
        Set<String> patched = placeholders(
                readClasspathResource(sourceFile.substring(CLASSPATH_PREFIX.length()).trim()));
        Set<String> upstream = placeholders(readClasspathResource(UPSTREAM_TEMPLATE));

        // A CSRFGuard upgrade that adds a placeholder (a new configurable option)
        // fails here until the CARLOS copy is rebased onto the new template.
        assertThat(upstream).isNotEmpty();
        assertThat(patched).containsExactlyInAnyOrderElementsOf(upstream);
    }

    private static Set<String> placeholders(String template) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private static String readClasspathResource(String name) throws IOException {
        try (InputStream in = CsrfGuardConfigurationRegressionTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("classpath resource " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Properties loadCsrfGuardProperties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CSRF_GUARD_PROPERTIES, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
