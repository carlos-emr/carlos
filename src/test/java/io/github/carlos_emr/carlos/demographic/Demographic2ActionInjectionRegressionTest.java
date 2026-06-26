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
package io.github.carlos_emr.carlos.demographic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for demographic 2Action dependency injection conventions.
 *
 * @since 2026-05-31
 */
@DisplayName("Demographic 2Action injection regression tests")
@Tag("unit")
@Tag("demographic")
class Demographic2ActionInjectionRegressionTest {

    private static final Path DEMOGRAPHIC_SOURCE_DIR =
            Path.of("src/main/java/io/github/carlos_emr/carlos/demographic");
    private static final Path STRUTS_CONFIG = Path.of("src/main/webapp/WEB-INF/classes/struts.xml");
    private static final Pattern FIELD_GET_BEAN = Pattern.compile(
            "^\\s*(?:(?:private|protected|public)\\s+)?(?:(?:static|final|transient)\\s+)*[A-Z][^;=]+\\s+\\w+\\s*=\\s*(?:\\([^)]*\\)\\s*)?SpringUtils\\.getBean\\([^;]+;\\s*$");

    @Test
    @DisplayName("should use constructor injection instead of SpringUtils field shims")
    void shouldUseConstructorInjection_whenDemographic2ActionsNeedSpringBeans() throws IOException {
        List<String> violations;
        try (Stream<Path> sourceFiles = Files.walk(DEMOGRAPHIC_SOURCE_DIR)) {
            violations = sourceFiles
                    .filter(path -> path.getFileName().toString().endsWith("2Action.java"))
                    .flatMap(Demographic2ActionInjectionRegressionTest::fieldGetBeanViolations)
                    .toList();
        }

        assertThat(violations)
                .as("demographic 2Actions should receive Spring beans through constructors")
                .isEmpty();
    }

    @Test
    @DisplayName("should use the Spring object factory without the startup-breaking constructor autowire strategy")
    void shouldUseSpringObjectFactory_withoutGlobalConstructorAutowire() throws IOException {
        String strutsConfig = Files.readString(STRUTS_CONFIG, StandardCharsets.UTF_8);

        // The Spring object factory is required so actions are Spring-managed.
        assertThat(strutsConfig)
                .contains("<constant name=\"struts.objectFactory\" value=\"spring\"/>");

        // Setting the global autowire strategy to "constructor" makes Struts attempt
        // AUTOWIRE_CONSTRUCTOR on already-instantiated framework objects (interceptors,
        // results, validators), which Spring rejects ("AUTOWIRE_CONSTRUCTOR not supported
        // for existing bean instance") and the whole webapp fails to start. Actions are
        // instead instantiated via their no-arg constructor (delegating to the injected
        // constructor through SpringUtils), so this strategy must never be set globally.
        assertThat(strutsConfig)
                .as("global constructor autowire strategy breaks webapp startup")
                .doesNotContain("struts.objectFactory.spring.autoWire");
    }

    @Test
    @DisplayName("should detect SpringUtils field shims with nonstandard indentation")
    void shouldDetectFieldShim_whenIndentationVaries(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("TabIndented2Action.java");
        Files.writeString(sourceFile, """
                class TabIndented2Action {
                \tprivate final transient SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
                }
                """, StandardCharsets.UTF_8);

        assertThat(fieldGetBeanViolations(sourceFile))
                .containsExactly(sourceFile + ":2");
    }

    @Test
    @DisplayName("should ignore SpringUtils method-local lookups")
    void shouldIgnoreLocalLookup_whenInsideMethod(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("LocalLookup2Action.java");
        Files.writeString(sourceFile, """
                class LocalLookup2Action {
                    void execute() {
                        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
                    }
                }
                """, StandardCharsets.UTF_8);

        assertThat(fieldGetBeanViolations(sourceFile))
                .isEmpty();
    }

    private static Stream<String> fieldGetBeanViolations(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> violations = new ArrayList<>();
            int braceDepth = 0;
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (braceDepth == 1 && FIELD_GET_BEAN.matcher(line).matches()) {
                    violations.add(path + ":" + (index + 1));
                }
                braceDepth += countOccurrences(line, '{') - countOccurrences(line, '}');
            }
            return violations.stream();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }

    private static int countOccurrences(String line, char target) {
        int count = 0;
        for (int index = 0; index < line.length(); index++) {
            if (line.charAt(index) == target) {
                count++;
            }
        }
        return count;
    }
}
