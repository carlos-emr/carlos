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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
    private static final Pattern FIELD_GET_BEAN = Pattern.compile(
            "^\\s*private\\s+(?:static\\s+)?(?:final\\s+)?[^;=]+\\s+\\w+\\s*=\\s*(?:\\([^)]*\\)\\s*)?SpringUtils\\.getBean\\([^;]+;\\s*$");

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

    private static Stream<String> fieldGetBeanViolations(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return Stream.iterate(0, index -> index + 1)
                    .limit(lines.size())
                    .filter(index -> FIELD_GET_BEAN.matcher(lines.get(index)).matches())
                    .map(index -> path + ":" + (index + 1));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }
}
