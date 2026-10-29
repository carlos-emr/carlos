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
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the SonarCloud path-traversal exclusions in {@code pom.xml} to individual, justified files
 * (issue #5316).
 *
 * <p>The POM used to ignore {@code javasecurity:S2083} for whole packages ({@code **}{@code /eform/**},
 * {@code **}{@code /lab/**}, ...), so a new unvalidated file operation anywhere in those packages was
 * hidden along with the known false positives. SonarCloud offers no sanitizer configuration, so the
 * remaining false positives are excluded one file at a time. This test cannot run SonarCloud; it
 * evaluates the POM criteria the way the scanner does (Sonar wildcard patterns against the
 * project-relative path) and checks:</p>
 * <ul>
 *   <li>every path-traversal criterion names exactly one existing main-source file, with no
 *       directory or name wildcard, and is preceded by a justification comment;</li>
 *   <li>every named file still calls {@code PathValidationUtils} and still performs a file
 *       operation, so a stale entry is noticed when the code it excused changes;</li>
 *   <li>controlled unsafe fixtures &mdash; new files in each formerly excluded package &mdash; are
 *       not matched by any criterion, although the retired package glob matched them;</li>
 *   <li>safe fixtures &mdash; known {@code PathValidationUtils}-guarded files &mdash; are still
 *       matched, so their false positives stay quiet.</li>
 * </ul>
 *
 * @since 2026-10-29
 */
@DisplayName("SonarCloud path-traversal exclusion policy")
@Tag("unit")
@Tag("security")
@Tag("regression")
class SonarPathTraversalExclusionPolicyUnitTest {

    /** Taint rules about file paths built from untrusted input; none may be excluded by package. */
    private static final Set<String> PATH_RULES = Set.of(
            "javasecurity:S2083", "javasecurity:S6549", "javasecurity:S6096");

    private static final String MAIN_SOURCE_ROOT = "src/main/java/";
    private static final String CARLOS_PACKAGE = "io/github/carlos_emr/carlos/";

    /** A path-rule resource key must be one literal Java file path below the CARLOS package. */
    private static final Pattern SINGLE_FILE_KEY = Pattern.compile(
            "\\*\\*/" + Pattern.quote(CARLOS_PACKAGE) + "(?:[A-Za-z0-9_]+/)*[A-Za-z0-9_]+\\.java");

    /** The packages the POM used to exclude wholesale before issue #5316. */
    private static final List<String> FORMERLY_EXCLUDED_PACKAGES = List.of(
            "eform", "documentManager", "lab", "integration", "form",
            "fax", "encounter", "dxresearch", "demographic", "billings");

    /**
     * Listed files whose file operations take paths their callers have already validated. Each must
     * document that contract and name the caller-side helpers; the test also fails if the file
     * starts calling PathValidationUtils itself, so this list cannot go stale.
     */
    private static final Map<String, String> CALLER_VALIDATED = Map.of(
            "documentManager/IncomingDocumentPublication.java",
            "ManageDocument2Action and IncomingDocUtil validate both paths (validateChildPath, validatePath)"
                    + " before calling move(); the class Javadoc makes that the caller's contract");

    /** A call to (or static import of) a PathValidationUtils helper, not a mention in a comment. */
    private static final Pattern PATH_VALIDATION_CALL = Pattern.compile(
            "PathValidationUtils\\s*\\.\\s*[a-z][A-Za-z0-9]*\\s*\\(|import\\s+static\\s+[\\w.]*PathValidationUtils\\.");

    /** File-system operations that S2083 reports when their path is tainted. */
    private static final Pattern FILE_SINK = Pattern.compile(
            "new File(?:InputStream|OutputStream|Reader|Writer)?\\(|new RandomAccessFile\\(|new PdfReader\\("
                    + "|Paths\\.get\\(|Path\\.of\\(|\\bFiles\\.[a-zA-Z]+\\(|FileUtils\\.[a-zA-Z]+\\(|\\.renameTo\\("
                    + "|\\.transferTo\\(|\\.save\\(|\\.parse\\(");

    private static Path projectBase;
    private static String pomText;
    private static Map<String, String> properties;
    private static Map<String, Criterion> criteria;

    private record Criterion(String id, String ruleKey, String resourceKey) {
        boolean isPathRule() {
            return PATH_RULES.contains(ruleKey);
        }
    }

    @BeforeAll
    static void loadPom() throws Exception {
        projectBase = projectBaseDirectory();
        pomText = Files.readString(projectBase.resolve("pom.xml"), StandardCharsets.UTF_8);
        properties = readProperties(pomText);
        criteria = readCriteria(properties);
    }

    @Test
    @DisplayName("should declare a rule and a resource for every listed criterion, and list every declared one")
    void shouldDeclareCompleteCriteria_forEveryListedId() {
        Set<String> declared = new LinkedHashSet<>();
        Pattern declaredKey = Pattern.compile("sonar\\.issue\\.ignore\\.multicriteria\\.([^.]+)\\.(ruleKey|resourceKey)");
        for (String name : properties.keySet()) {
            Matcher matcher = declaredKey.matcher(name);
            if (matcher.matches()) {
                declared.add(matcher.group(1));
            }
        }

        assertThat(criteria).isNotEmpty();
        assertThat(declared).as("criteria declared in pom.xml but missing from the id list")
                .containsExactlyInAnyOrderElementsOf(criteria.keySet());
        criteria.values().forEach(criterion -> {
            assertThat(criterion.ruleKey()).as("ruleKey of %s", criterion.id()).isNotBlank();
            assertThat(criterion.resourceKey()).as("resourceKey of %s", criterion.id()).isNotBlank();
        });
    }

    @Test
    @DisplayName("should exclude path-traversal rules only from single, existing main-source files")
    void shouldExcludePathRules_onlyFromSingleExistingFiles() throws IOException {
        List<Path> mainSources = mainJavaSources();
        List<Criterion> pathCriteria = pathCriteria();
        assertThat(pathCriteria).isNotEmpty();

        for (Criterion criterion : pathCriteria) {
            assertThat(criterion.resourceKey())
                    .as("%s must name one file by its full package path (no package or name wildcard)", criterion.id())
                    .matches(SINGLE_FILE_KEY);
            List<String> matched = mainSources.stream()
                    .map(SonarPathTraversalExclusionPolicyUnitTest::projectRelative)
                    .filter(path -> sonarMatches(criterion.resourceKey(), path))
                    .toList();
            assertThat(matched).as("files matched by %s (%s)", criterion.id(), criterion.resourceKey()).hasSize(1);
        }
    }

    @Test
    @DisplayName("should keep each excluded file a PathValidationUtils-guarded file operation")
    void shouldKeepEachExcludedFile_guardedByPathValidationUtils() throws IOException {
        for (Criterion criterion : pathCriteria()) {
            Path file = sourceFor(criterion);
            String source = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(FILE_SINK.matcher(source).find())
                    .as("%s no longer performs a file operation; drop its exclusion", file.getFileName())
                    .isTrue();
            String packagePath = criterion.resourceKey().substring(("**/" + CARLOS_PACKAGE).length());
            if (packagePath.equals("utility/PathValidationUtils.java")) {
                continue;
            }
            if (CALLER_VALIDATED.containsKey(packagePath)) {
                assertThat(PATH_VALIDATION_CALL.matcher(source).find())
                        .as("%s now validates in-file; move it off CALLER_VALIDATED", file.getFileName())
                        .isFalse();
                assertThat(source).as("%s must document that callers validate its paths", file.getFileName())
                        .containsIgnoringCase("validated by the caller");
            } else {
                assertThat(PATH_VALIDATION_CALL.matcher(source).find())
                        .as("%s no longer calls PathValidationUtils; its S2083 exclusion is unjustified", file.getFileName())
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("should list every caller-validated file in pom.xml")
    void shouldListEveryCallerValidatedFile_inPom() {
        for (String packagePath : CALLER_VALIDATED.keySet()) {
            String file = MAIN_SOURCE_ROOT + CARLOS_PACKAGE + packagePath;
            assertThat(matchingPathCriteria(file)).as("criteria for %s", file).hasSize(1);
        }
    }

    @Test
    @DisplayName("should precede every path-traversal criterion with a justification comment")
    void shouldPrecedeEveryPathCriterion_withJustificationComment() {
        List<String> lines = pomText.lines().toList();
        for (Criterion criterion : pathCriteria()) {
            String ruleLine = "<sonar.issue.ignore.multicriteria." + criterion.id() + ".ruleKey>";
            int index = indexOfLineContaining(lines, ruleLine);
            assertThat(index).as("ruleKey line for %s", criterion.id()).isPositive();
            String previous = lines.get(index - 1).trim();
            assertThat(previous)
                    .as("%s needs a comment stating its source and sanitizer on the line above", criterion.id())
                    .endsWith("-->");
            assertThat(previous.replace("<!--", "").replace("-->", "").trim())
                    .as("justification for %s", criterion.id())
                    .hasSizeGreaterThan(20);
        }
    }

    static List<String> formerlyExcludedPackages() {
        return FORMERLY_EXCLUDED_PACKAGES;
    }

    @ParameterizedTest(name = "unsafe fixture in {0}")
    @MethodSource("formerlyExcludedPackages")
    @DisplayName("should leave a new unvalidated file operation in a formerly excluded package visible")
    void shouldLeaveNewFileVisible_inFormerlyExcludedPackage(String packageName) {
        // A controlled unsafe fixture: a file that does not exist yet and so cannot have been
        // reviewed. Under the retired package glob it was suppressed; it must not be now.
        String fixture = MAIN_SOURCE_ROOT + CARLOS_PACKAGE + packageName + "/Issue5316UnvalidatedPathFixture.java";
        String nestedFixture = MAIN_SOURCE_ROOT + CARLOS_PACKAGE + packageName
                + "/nested/web/Issue5316UnvalidatedPath2Action.java";

        String retiredGlob = "**/" + packageName + "/**/*.java";
        assertThat(sonarMatches(retiredGlob, fixture)).as("the retired glob hid this fixture").isTrue();
        assertThat(sonarMatches(retiredGlob, nestedFixture)).as("the retired glob hid this fixture").isTrue();

        assertThat(matchingPathCriteria(fixture)).as("criteria suppressing %s", fixture).isEmpty();
        assertThat(matchingPathCriteria(nestedFixture)).as("criteria suppressing %s", nestedFixture).isEmpty();
    }

    @Test
    @DisplayName("should leave the eForm PDF servlet under the rule after its template fix")
    void shouldLeaveEFormPdfServletVisible_afterTemplateFix() {
        // The eForm PDF servlet opened a template named by a request parameter without validating
        // it, and the retired eform package glob hid that. It is fixed now and stays under the rule.
        String servlet = MAIN_SOURCE_ROOT + CARLOS_PACKAGE + "eform/util/EFormPDFServlet.java";
        assertThat(projectBase.resolve(servlet)).exists();
        assertThat(sonarMatches("**/eform/**/*.java", servlet)).isTrue();
        assertThat(matchingPathCriteria(servlet)).isEmpty();
    }

    @ParameterizedTest(name = "safe fixture {0}")
    @ValueSource(strings = {
            "eform/actions/DisplayImage2Action.java",
            "documentManager/actions/ManageDocument2Action.java",
            "lab/ca/all/util/Utilities.java",
            "form/pdfservlet/FrmPDFServlet.java",
            "integration/mcedt/mailbox/Upload2Action.java",
            "billings/ca/on/web/MoveMohFiles2Action.java",
            "utility/PathValidationUtils.java"})
    @DisplayName("should keep known PathValidationUtils-guarded files quiet")
    void shouldKeepKnownSafeFilesQuiet_whenGuardedByPathValidationUtils(String relativeToPackage) {
        String safeFixture = MAIN_SOURCE_ROOT + CARLOS_PACKAGE + relativeToPackage;
        assertThat(projectBase.resolve(safeFixture)).exists();
        assertThat(matchingPathCriteria(safeFixture))
                .as("S2083 criteria matching %s", safeFixture)
                .singleElement()
                .extracting(Criterion::ruleKey)
                .isEqualTo("javasecurity:S2083");
    }

    @Test
    @DisplayName("should match Sonar wildcard semantics used by issue exclusions")
    void shouldMatchSonarWildcardSemantics_forResourceKeys() {
        assertThat(sonarMatches("**/a/B.java", "src/main/java/a/B.java")).isTrue();
        assertThat(sonarMatches("**/a/B.java", "a/B.java")).isTrue();
        assertThat(sonarMatches("**/a/B.java", "src/main/java/xa/B.java")).isFalse();
        assertThat(sonarMatches("**/a/**/*.java", "src/a/b/c/D.java")).isTrue();
        assertThat(sonarMatches("**/a/*.java", "src/a/b/D.java")).isFalse();
        assertThat(sonarMatches("**/form*.jsp", "x/formbc1.jsp")).isTrue();
    }

    // ---------------------------------------------------------------------------------------------

    private static List<Criterion> pathCriteria() {
        return criteria.values().stream().filter(Criterion::isPathRule).toList();
    }

    private static List<Criterion> matchingPathCriteria(String projectRelativePath) {
        return pathCriteria().stream()
                .filter(criterion -> sonarMatches(criterion.resourceKey(), projectRelativePath))
                .toList();
    }

    private static Path sourceFor(Criterion criterion) {
        String relative = criterion.resourceKey().substring("**/".length());
        return projectBase.resolve(MAIN_SOURCE_ROOT).resolve(relative);
    }

    private static List<Path> mainJavaSources() throws IOException {
        try (Stream<Path> files = Files.walk(projectBase.resolve(MAIN_SOURCE_ROOT))) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String projectRelative(Path file) {
        return projectBase.relativize(file).toString().replace('\\', '/');
    }

    /**
     * Matches a SonarQube/SonarCloud wildcard pattern: {@code **} spans directories, {@code *} and
     * {@code ?} stay inside one path segment. Exclusion resource keys are matched against the
     * project-relative file path.
     */
    static boolean sonarMatches(String pattern, String path) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (pattern.startsWith("**/", i)) {
                regex.append("(?:.*/)?");
                i += 2;
            } else if (pattern.startsWith("**", i)) {
                regex.append(".*");
                i += 1;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return path.matches(regex.toString());
    }

    private static int indexOfLineContaining(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, String> readProperties(String pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The POM has no DOCTYPE; refusing one rules out external entities with any parser on the
        // test classpath (Xerces does not accept the JAXP accessExternal* attributes).
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Element root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(pom))).getDocumentElement();

        Map<String, String> values = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && element.getTagName().equals("properties")) {
                NodeList props = element.getChildNodes();
                for (int j = 0; j < props.getLength(); j++) {
                    if (props.item(j) instanceof Element property) {
                        values.put(property.getTagName(), property.getTextContent().trim());
                    }
                }
            }
        }
        assertThat(values).as("pom.xml <properties>").containsKey("sonar.issue.ignore.multicriteria");
        return values;
    }

    private static Map<String, Criterion> readCriteria(Map<String, String> values) {
        Map<String, Criterion> result = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (String id : values.get("sonar.issue.ignore.multicriteria").split(",")) {
            ids.add(id.trim());
        }
        assertThat(ids).as("duplicate criterion ids").doesNotHaveDuplicates();
        for (String id : ids) {
            String prefix = "sonar.issue.ignore.multicriteria." + id + ".";
            result.put(id, new Criterion(id, values.get(prefix + "ruleKey"), values.get(prefix + "resourceKey")));
        }
        return result;
    }

    private static Path projectBaseDirectory() {
        String baseDirectory = System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("basedir", System.getProperty("user.dir")));
        Path base = Path.of(baseDirectory).toAbsolutePath().normalize();
        assertThat(base.resolve("pom.xml")).as("CARLOS project base directory should contain pom.xml").exists();
        assertThat(base.resolve(MAIN_SOURCE_ROOT)).as("CARLOS main sources").isDirectory();
        return base;
    }
}
