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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Keeps HAPI HL7 v2 entry points that parse XML, or that would change how CARLOS reads and writes
 * HL7 text, behind the classes in this package (issue #5414).
 *
 * <p>HAPI's {@code GenericParser}, {@code DefaultXMLParser}/{@code XMLParser}, its {@code XMLUtils}
 * helpers and the {@code preparser} package all build their own XML parsers; the pre-parser's has
 * no entity protection at all. Production code must parse HL7 that might be XML with
 * {@link UntrustedHl7MessageParser} or {@link HardenedHl7XmlParser}, and must create pipe parsers
 * and new messages through {@link Hl7Parsers}, which keeps the trimming, escaping and absence of
 * format checks CARLOS had with HAPI 1.0.1. A failure names the file and the construct to
 * replace.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@Tag("hl7")
@DisplayName("HL7 parser boundary guard")
class Hl7ParserBoundaryGuardUnitTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /** Files that implement the boundary and so may name the HAPI classes they wrap. */
    private static final Set<String> BOUNDARY_FILES = Set.of(
            "HardenedHl7XmlParser.java", "UntrustedHl7MessageParser.java", "LegacyHl7NoValidation.java",
            "LegacyHl7Escaping.java", "LegacyHl7PipeParser.java", "Hl7Parsers.java");

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("new\\s+(?:ca\\.uhn\\.hl7v2\\.parser\\.)?(?:GenericParser|DefaultXMLParser|XMLParser)\\s*\\("),
                    "UntrustedHl7MessageParser or HardenedHl7XmlParser"),
            new Rule(Pattern.compile("\\.\\s*get(?:GenericParser|XMLParser|PipeParser)\\s*\\("),
                    "Hl7Parsers, UntrustedHl7MessageParser or HardenedHl7XmlParser instead of a HapiContext's default parser"),
            new Rule(Pattern.compile("ca\\.uhn\\.hl7v2\\.preparser"),
                    "no HAPI pre-parser: its XML reader resolves external entities"),
            new Rule(Pattern.compile("ca\\.uhn\\.hl7v2\\.util\\.XMLUtils"),
                    "HardenedHl7XmlParser instead of HAPI's XMLUtils"),
            new Rule(Pattern.compile("extends\\s+(?:ca\\.uhn\\.hl7v2\\.parser\\.)?(?:PipeParser|GenericParser|DefaultXMLParser|XMLParser)\\b"),
                    "Hl7Parsers, UntrustedHl7MessageParser or HardenedHl7XmlParser instead of another HAPI parser subclass"),
            new Rule(Pattern.compile("new\\s+(?:ca\\.uhn\\.hl7v2\\.parser\\.)?PipeParser\\s*\\("),
                    "Hl7Parsers.newPipeParser() or Hl7Parsers.newEncodingPipeParser(), which keep HAPI 1.0.1 trimming and escaping"),
            new Rule(Pattern.compile("new\\s+(?:ca\\.uhn\\.hl7v2\\.validation\\.impl\\.)?NoValidation\\s*\\("),
                    "Hl7Parsers, whose parsers keep HAPI 1.0.1's left-trimming of TX text"),
            new Rule(Pattern.compile("\\.\\s*setValidationContext\\s*\\("),
                    "a parser from Hl7Parsers, which is already configured"),
            new Rule(Pattern.compile("(?<!forBuilding\\()new\\s+(?:[\\w.]+\\.)?[A-Z]{3}_[A-Z][0-9]{2}\\s*\\(\\s*\\)"),
                    "Hl7Parsers.forBuilding(new ...()), so HAPI 2.x does not trim or reject values while the message is built"));

    @Test
    @DisplayName("should keep HL7 XML parsing behind the hardened boundary in all production sources")
    void shouldKeepHl7XmlParsingBehindHardenedBoundary_inAllProductionSources() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (BOUNDARY_FILES.contains(file.getFileName().toString())) {
                    continue;
                }
                String source = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                for (Rule rule : RULES) {
                    Matcher matcher = rule.pattern().matcher(source);
                    if (matcher.find()) {
                        violations.add(MAIN_SOURCES.relativize(file) + ": '" + matcher.group().trim()
                                + "' -> use " + rule.replacement());
                    }
                }
            }
        }
        assertThat(violations).as("HAPI XML/validation entry points outside the boundary").isEmpty();
    }

    @Test
    @DisplayName("should detect a forbidden construct when it appears in code")
    void shouldDetectForbiddenConstruct_whenItAppearsInCode() {
        String offending = "class X { Object p = new GenericParser(); Object v = new NoValidation();"
                + " Object q = new PipeParser(); Object m = new OMP_O09(); }";

        long matches = RULES.stream().filter(rule -> rule.pattern().matcher(stripComments(offending)).find()).count();

        assertThat(matches).isEqualTo(4);
    }

    @Test
    @DisplayName("should detect a subclass of a HAPI parser when it is declared outside the boundary")
    void shouldDetectSubclassOfHapiParser_whenDeclaredOutsideBoundary() {
        // A subclass is created with its own name, so the "new PipeParser(" rule would not see it.
        for (String declaration : List.of("class X extends PipeParser {}", "class X extends ca.uhn.hl7v2.parser.PipeParser {}",
                "class X extends DefaultXMLParser {}", "class X extends GenericParser {}", "class X extends XMLParser {}")) {
            assertThat(RULES.stream().anyMatch(rule -> rule.pattern().matcher(declaration).find()))
                    .as(declaration).isTrue();
        }
    }

    @Test
    @DisplayName("should allow building a message when it is prepared with forBuilding")
    void shouldAllowBuildingMessage_whenPreparedWithForBuilding() {
        String prepared = "class X { Object m = Hl7Parsers.forBuilding(new ADT_A04()); }";

        assertThat(RULES.stream().anyMatch(rule -> rule.pattern().matcher(prepared).find())).isFalse();
    }

    @Test
    @DisplayName("should ignore a forbidden construct when it only appears in a comment")
    void shouldIgnoreForbiddenConstruct_whenItOnlyAppearsInComment() {
        String commented = "// Parser p = new GenericParser();\n/* new DefaultXMLParser() */ class X {}";

        assertThat(RULES.stream().anyMatch(rule -> rule.pattern().matcher(stripComments(commented)).find())).isFalse();
    }

    /** Removes block and line comments so documentation that names the old classes is allowed. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
