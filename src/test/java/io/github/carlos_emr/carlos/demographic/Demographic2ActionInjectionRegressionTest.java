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
    /**
     * Matches a field declaration initialized from {@code SpringUtils.getBean(...)}.
     *
     * <p>Applied to a whole normalized statement (see {@link #fieldGetBeanViolations(Path, String)}),
     * not to one physical line, so wrapping the initializer onto its own line does not hide the shim.
     */
    private static final Pattern FIELD_GET_BEAN = Pattern.compile(
            "^(?:(?:private|protected|public)\\s+)?(?:(?:static|final|transient)\\s+)*"
                    + "[A-Z][^;=]+\\s+\\w+\\s*=\\s*(?:\\([^)]*\\)\\s*)?SpringUtils\\.getBean\\(.*$");

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

    @Test
    @DisplayName("should detect SpringUtils field shims split across lines")
    void shouldDetectFieldShim_whenDeclarationSpansLines(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("WrappedDeclaration2Action.java");
        Files.writeString(sourceFile, """
                class WrappedDeclaration2Action {
                    @SuppressFBWarnings(value = "X", justification = "wraps (parens) and a ; too")
                    private final transient SecurityInfoManager securityInfoManager =
                            SpringUtils.getBean(SecurityInfoManager.class);
                }
                """, StandardCharsets.UTF_8);

        assertThat(fieldGetBeanViolations(sourceFile))
                .containsExactly(sourceFile + ":3");
    }

    @Test
    @DisplayName("should ignore SpringUtils lookups inside comments")
    void shouldIgnoreFieldShim_whenOnlyInAComment(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("CommentedOut2Action.java");
        Files.writeString(sourceFile, """
                class CommentedOut2Action {
                    // private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
                    /* private SecurityInfoManager other = SpringUtils.getBean(SecurityInfoManager.class); */
                }
                """, StandardCharsets.UTF_8);

        assertThat(fieldGetBeanViolations(sourceFile))
                .isEmpty();
    }

    private static Stream<String> fieldGetBeanViolations(Path path) {
        try {
            return fieldGetBeanViolations(path, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }

    /**
     * Reports every class-body field declaration initialized from {@code SpringUtils.getBean(...)}.
     *
     * <p>The scan works on statements, not physical lines. A declaration a formatter wrapped onto
     * two lines, or one carrying an annotation, is the same banned shim, so a line-at-a-time matcher
     * would let the prohibited pattern back in unnoticed. Comments and string/char literals are
     * skipped so their contents cannot supply a stray {@code ;} or brace, and only statements at
     * brace depth 1 count: the same lookup inside a method body is a permitted local lookup.
     *
     * @param path source file the violations are reported against
     * @param source that file's contents
     * @return {@code path:line} for each violation, line being where the declaration starts
     */
    private static Stream<String> fieldGetBeanViolations(Path path, String source) {
        List<String> violations = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        int statementLine = 0;
        int line = 1;
        int braceDepth = 0;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (current == '\n') {
                line++;
                appendSeparator(statement);
                continue;
            }
            if (current == '/' && next == '/') {
                int newline = source.indexOf('\n', index);
                // Stop one short of the newline so the branch above still counts the line.
                index = (newline < 0 ? source.length() : newline) - 1;
                continue;
            }
            if (current == '/' && next == '*') {
                int close = source.indexOf("*/", index + 2);
                int end = close < 0 ? source.length() - 1 : close + 1;
                line += countNewlines(source, index, end);
                index = end;
                appendSeparator(statement);
                continue;
            }
            if (current == '"' || current == '\'') {
                int end = endOfLiteral(source, index);
                line += countNewlines(source, index, end);
                index = end;
                // The literal's contents cannot be part of a declaration; keep the statement
                // well-formed (balanced parens for annotation skipping) without them.
                appendSeparator(statement);
                continue;
            }
            if (braceDepth == 1 && statement.isEmpty() && current == '@') {
                // Annotations precede the declaration they document; drop them so the reported
                // line is the declaration's own and the pattern still anchors at its start.
                int end = endOfAnnotation(source, index);
                line += countNewlines(source, index, end);
                index = end;
                continue;
            }
            if (current == '{' || current == '}') {
                braceDepth += current == '{' ? 1 : -1;
                // A member body or initializer block is not the single field declaration scanned for.
                statement.setLength(0);
                continue;
            }
            if (current == ';') {
                if (braceDepth == 1 && FIELD_GET_BEAN.matcher(statement.toString().trim()).matches()) {
                    violations.add(path + ":" + statementLine);
                }
                statement.setLength(0);
                continue;
            }
            if (braceDepth == 1) {
                if (statement.isEmpty()) {
                    if (Character.isWhitespace(current)) {
                        continue;
                    }
                    statementLine = line;
                }
                statement.append(current);
            }
        }

        return violations.stream();
    }

    private static void appendSeparator(StringBuilder statement) {
        if (!statement.isEmpty() && statement.charAt(statement.length() - 1) != ' ') {
            statement.append(' ');
        }
    }

    /** Returns the index of the last character of the literal starting at {@code start}. */
    private static int endOfLiteral(String source, int start) {
        char quote = source.charAt(start);
        for (int index = start + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '\\') {
                index++;
            } else if (current == quote) {
                return index;
            }
        }
        return source.length() - 1;
    }

    /**
     * Returns the index of the last character of the annotation starting at {@code start},
     * including its parenthesized arguments when present.
     */
    private static int endOfAnnotation(String source, int start) {
        int index = start + 1;
        while (index < source.length() && (Character.isJavaIdentifierPart(source.charAt(index)) || source.charAt(index) == '.')) {
            index++;
        }
        int argumentStart = index;
        while (argumentStart < source.length() && Character.isWhitespace(source.charAt(argumentStart))) {
            argumentStart++;
        }
        if (argumentStart >= source.length() || source.charAt(argumentStart) != '(') {
            return index - 1;
        }
        int depth = 0;
        for (index = argumentStart; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '"' || current == '\'') {
                // Justification strings routinely contain parentheses; skip them wholesale.
                index = endOfLiteral(source, index);
            } else if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        return source.length() - 1;
    }

    private static int countNewlines(String source, int start, int end) {
        int count = 0;
        for (int index = start; index <= end && index < source.length(); index++) {
            if (source.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }
}
