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
package io.github.carlos_emr.carlos.drools;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DroolsHelper}, the central utility class for compiling DRL
 * rule files into Drools 10.x {@link KieBase} instances.
 *
 * <p>{@code DroolsHelper} was introduced during the Drools 2.0 &rarr; 7.74.1 migration
 * to replace the legacy {@code org.drools.io.RuleBaseLoader} API with the modern KIE
 * API. It uses the standard {@code KieServices}/{@code KieFileSystem}/{@code KieBuilder}
 * pipeline to compile DRL text into a {@code KieBase} with unique {@code ReleaseId}
 * per compilation for thread safety.</p>
 *
 * <p>Tests are organized into three nested classes corresponding to the three entry points:</p>
 * <ul>
 *   <li>{@link CreateKieBaseFromDrl} &mdash; compiles DRL from a raw {@code String}</li>
 *   <li>{@link LoadFromInputStream} &mdash; compiles DRL from an {@link InputStream}</li>
 *   <li>{@link LoadFromUrl} &mdash; compiles DRL from a classpath or file {@link URL}</li>
 * </ul>
 *
 * <p>No Spring context is needed because all methods under test are static utilities
 * with no external dependencies beyond the Drools KIE runtime.</p>
 *
 * @see DroolsHelper
 * @see DroolsCompilationException
 * @since 2026-02-17
 */
@Tag("unit")
@Tag("drools")
@DisplayName("DroolsHelper")
class DroolsHelperUnitTest {

    /**
     * A minimal valid DRL that declares a single rule matching an {@link AtomicBoolean}
     * fact and setting it to {@code true}. Used as the standard test fixture across
     * multiple test methods because it compiles quickly and its side effect (flipping
     * the boolean) is trivially verifiable via {@code KieSession.fireAllRules()}.
     */
    private static final String MINIMAL_VALID_DRL =
            "package test;\n" +
            "import java.util.concurrent.atomic.AtomicBoolean;\n" +
            "rule \"test-rule\"\n" +
            "    when\n" +
            "        $b : AtomicBoolean()\n" +
            "    then\n" +
            "        $b.set(true);\n" +
            "end\n";

    /**
     * A deliberately broken DRL with invalid syntax in the {@code when} clause.
     * Used to verify that the compiler produces a meaningful
     * {@link DroolsCompilationException} rather than a generic error.
     */
    private static final String INVALID_DRL =
            "package test;\n" +
            "rule \"broken\"\n" +
            "    when\n" +
            "        THIS IS NOT VALID DRL SYNTAX\n" +
            "    then\n" +
            "end\n";

    /**
     * Tests for {@link DroolsHelper#createKieBaseFromDrl(String)}, the primary
     * entry point that compiles a DRL string into a {@link KieBase}.
     *
     * <p>Covers happy path (valid DRL compiles and fires rules), null/empty/blank
     * input guarding, and syntax error reporting.</p>
     */
    @Nested
    @DisplayName("createKieBaseFromDrl")
    class CreateKieBaseFromDrl {

        /**
         * Happy path: a syntactically valid DRL should compile into a non-null
         * {@link KieBase} without throwing.
         */
        @Test
        @DisplayName("should compile valid DRL with simple rule")
        void shouldCompileValidDrl_withSimpleRule() throws DroolsCompilationException {
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(MINIMAL_VALID_DRL);

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).isNotEmpty();
            int totalRules = kieBase.getKiePackages().stream().mapToInt(p -> p.getRules().size()).sum();
            assertThat(totalRules).isEqualTo(1);
        }

        /**
         * Null input should be rejected immediately with a descriptive message.
         * This guards against callers passing uninitialized DRL strings.
         */
        @Test
        @DisplayName("should throw DroolsCompilationException when DRL is null")
        void shouldThrowDroolsCompilationException_whenDrlIsNull() {
            assertThatThrownBy(() -> DroolsHelper.createKieBaseFromDrl(null))
                    .isInstanceOf(DroolsCompilationException.class)
                    .hasMessageContaining("null or empty");
        }

        /**
         * An empty string is semantically invalid DRL and should be rejected
         * with the same "null or empty" guard as a null value.
         */
        @Test
        @DisplayName("should throw DroolsCompilationException when DRL is empty")
        void shouldThrowDroolsCompilationException_whenDrlIsEmpty() {
            assertThatThrownBy(() -> DroolsHelper.createKieBaseFromDrl(""))
                    .isInstanceOf(DroolsCompilationException.class)
                    .hasMessageContaining("null or empty");
        }

        /**
         * Whitespace-only DRL (spaces, tabs, newlines) should be treated
         * identically to an empty string and rejected before compilation.
         */
        @Test
        @DisplayName("should throw DroolsCompilationException when DRL is blank")
        void shouldThrowDroolsCompilationException_whenDrlIsBlank() {
            assertThatThrownBy(() -> DroolsHelper.createKieBaseFromDrl("   \t\n  "))
                    .isInstanceOf(DroolsCompilationException.class)
                    .hasMessageContaining("null or empty");
        }

        /**
         * A DRL with invalid syntax in the {@code when} clause should produce a
         * {@link DroolsCompilationException} whose message includes the error count
         * (e.g., "DRL contained 2 error(s)") to aid debugging.
         */
        @Test
        @DisplayName("should throw DroolsCompilationException when DRL has syntax error")
        void shouldThrowDroolsCompilationException_whenDrlHasSyntaxError() {
            assertThatThrownBy(() -> DroolsHelper.createKieBaseFromDrl(INVALID_DRL))
                    .isInstanceOf(DroolsCompilationException.class)
                    .hasMessageContaining("error(s)");
        }

        /**
         * End-to-end verification: compiles a DRL, creates a {@link KieSession},
         * inserts an {@link AtomicBoolean} fact, fires all rules, and asserts that
         * the rule's consequence executed (flipping the boolean to {@code true}).
         *
         * <p>This is the most comprehensive single test for the compilation pipeline
         * because it verifies not just that a {@code KieBase} is returned, but that
         * the compiled rules are actually functional.</p>
         */
        @Test
        @DisplayName("should produce KieBase that fires rules")
        void shouldProduceKieBaseThatFiresRules() throws DroolsCompilationException {
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(MINIMAL_VALID_DRL);

            // Create a new stateful session from the compiled rule base
            KieSession session = kieBase.newKieSession();
            try {
                // Insert an AtomicBoolean fact that the rule will match and flip
                AtomicBoolean flag = new AtomicBoolean(false);
                session.insert(flag);
                session.fireAllRules();

                // The rule's consequence sets the boolean to true
                assertThat(flag.get()).isTrue();
            } finally {
                // Always dispose sessions to release native Drools resources
                session.dispose();
            }
        }
    }

    /**
     * Tests for {@link DroolsHelper#loadFromInputStream(InputStream)}, which reads
     * DRL text from an {@link InputStream} and compiles it into a {@link KieBase}.
     *
     * <p>This entry point is used by subsystems that load DRL from classpath resources
     * or dynamically generated streams.</p>
     */
    @Nested
    @DisplayName("loadFromInputStream")
    class LoadFromInputStream {

        /**
         * A valid DRL stream should compile to a non-null {@link KieBase}.
         * Uses {@link ByteArrayInputStream} to create a stream from the test DRL string.
         */
        @Test
        @DisplayName("should compile KieBase from valid InputStream")
        void shouldCompileKieBase_fromValidInputStream() throws DroolsCompilationException {
            // Wrap the valid DRL string in a stream, simulating classpath resource loading
            InputStream is = new ByteArrayInputStream(MINIMAL_VALID_DRL.getBytes(StandardCharsets.UTF_8));

            KieBase kieBase = DroolsHelper.loadFromInputStream(is);

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).isNotEmpty();
            int totalRules = kieBase.getKiePackages().stream().mapToInt(p -> p.getRules().size()).sum();
            assertThat(totalRules).isEqualTo(1);
        }

        /**
         * A stream containing invalid DRL should propagate the compilation error
         * as a {@link DroolsCompilationException}, not a generic IOException.
         */
        @Test
        @DisplayName("should throw DroolsCompilationException when stream contains invalid DRL")
        void shouldThrowDroolsCompilationException_whenStreamContainsInvalidDrl() {
            InputStream is = new ByteArrayInputStream(INVALID_DRL.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> DroolsHelper.loadFromInputStream(is))
                    .isInstanceOf(DroolsCompilationException.class);
        }

        /**
         * When the underlying {@link InputStream} throws an {@link IOException}
         * (e.g., from a corrupted JAR or network failure), the helper should wrap
         * it in a {@link DroolsCompilationException} with a descriptive message
         * and the original IOException preserved as the cause.
         */
        @Test
        @DisplayName("should throw DroolsCompilationException when stream read fails")
        void shouldThrowDroolsCompilationException_whenStreamReadFails() {
            // Create a stream that always throws IOException on read
            InputStream brokenStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("simulated read failure");
                }
            };

            assertThatThrownBy(() -> DroolsHelper.loadFromInputStream(brokenStream))
                    .isInstanceOf(DroolsCompilationException.class)
                    .hasMessageContaining("Failed to read DRL from InputStream")
                    // The original IOException must be preserved as the cause chain
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    /**
     * Tests for {@link DroolsHelper#loadMeasurementRuleBase(String, Class)}, the
     * two-tier DRL loader used by the clinical reporting DroolsNumerator family.
     *
     * <p>In test environments the {@code MEASUREMENT_DS_DIRECTORY} property is typically
     * not set, so the method falls back to loading from the classpath at
     * {@code /oscar/oscarEncounter/oscarMeasurements/flowsheets/decisionSupport/}.</p>
     */
    @Nested
    @DisplayName("loadMeasurementRuleBase")
    class LoadMeasurementRuleBase {

        /**
         * Smoke test: loads a known decision support DRL from the classpath
         * fallback path and verifies it compiles to a non-null KieBase.
         */
        @Test
        @DisplayName("should load diab-A1C DRL from classpath fallback")
        void shouldLoadDiabA1cDrl_fromClasspathFallback() {
            KieBase kieBase = DroolsHelper.loadMeasurementRuleBase("diab-A1C.drl", DroolsHelper.class);

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).isNotEmpty();
            int totalRules = kieBase.getKiePackages().stream().mapToInt(p -> p.getRules().size()).sum();
            assertThat(totalRules).isGreaterThan(0);
        }

        /**
         * Verifies that a non-existent DRL filename returns null rather than
         * throwing, since the production code catches all exceptions internally.
         */
        @Test
        @DisplayName("should return null when DRL file not found")
        void shouldReturnNull_whenDrlFileNotFound() {
            KieBase kieBase = DroolsHelper.loadMeasurementRuleBase("nonexistent-file.drl", DroolsHelper.class);

            assertThat(kieBase).isNull();
        }
    }

    /**
     * Tests for {@link DroolsHelper#loadFromUrl(URL)}, which reads DRL from a
     * {@link URL} (typically a classpath resource) and compiles it.
     *
     * <p>This is the entry point used by the production DRL compilation integration
     * tests in {@link DrlCompilationIntegrationTest}.</p>
     */
    @Nested
    @DisplayName("loadFromUrl")
    class LoadFromUrl {

        /**
         * Loads the production {@code prevention.drl} file from the classpath and
         * verifies it compiles successfully. This serves as a smoke test that the
         * classpath URL loading mechanism works with real DRL resources.
         */
        @Test
        @DisplayName("should compile KieBase from classpath URL")
        void shouldCompileKieBase_fromClasspathUrl() throws DroolsCompilationException {
            // Load a real production DRL from the classpath to verify the URL-based path
            URL url = getClass().getResource("/oscar/oscarPrevention/prevention.drl");
            assertThat(url).as("prevention.drl must be on classpath").isNotNull();

            KieBase kieBase = DroolsHelper.loadFromUrl(url);

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).isNotEmpty();
            int totalRules = kieBase.getKiePackages().stream().mapToInt(p -> p.getRules().size()).sum();
            assertThat(totalRules).isGreaterThan(0);
        }

        /**
         * A URL pointing to a non-existent file should produce a
         * {@link DroolsCompilationException} with a descriptive message rather
         * than a raw {@link java.io.FileNotFoundException}.
         */
        @Test
        @DisplayName("should throw DroolsCompilationException when URL cannot be read")
        void shouldThrowDroolsCompilationException_whenUrlCannotBeRead() throws Exception {
            // Point to a file:// URL that does not exist on disk
            URL badUrl = new URL("file:///nonexistent/path/to/rules.drl");

            assertThatThrownBy(() -> DroolsHelper.loadFromUrl(badUrl))
                    .isInstanceOf(DroolsCompilationException.class)
                    .hasMessageContaining("Failed to read DRL from URL");
        }

        /**
         * Integration smoke test: loads the production {@code prevention.drl},
         * compiles it, and verifies a {@link KieSession} can be created from
         * the resulting {@link KieBase}.
         *
         * <p>This goes one step beyond the classpath URL test by exercising the
         * full KieBase-to-KieSession lifecycle, confirming that the compiled rule
         * base is structurally valid (not just non-null).</p>
         */
        @Test
        @DisplayName("should compile KieBase from prevention DRL")
        void shouldCompileKieBase_fromPreventionDrl() throws DroolsCompilationException {
            URL url = getClass().getResource("/oscar/oscarPrevention/prevention.drl");
            assertThat(url).as("prevention.drl must be on classpath").isNotNull();

            KieBase kieBase = DroolsHelper.loadFromUrl(url);

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).isNotEmpty();

            // Verify we can create a functional session from the compiled base
            KieSession session = kieBase.newKieSession();
            try {
                assertThat(session).isNotNull();
            } finally {
                // Always dispose to avoid leaking Drools-internal resources
                session.dispose();
            }
        }
    }
}
