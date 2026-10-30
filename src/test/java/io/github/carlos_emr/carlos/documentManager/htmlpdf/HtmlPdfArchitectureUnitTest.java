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
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guardrails for the HTML-to-PDF module, read from its source files the way
 * {@code OntarioAssemblerArchitectureUnitTest} reads the billing assemblers. They pin the dependency graph
 * ({@code service} sees everything; {@code support} sees {@code dto} and {@code engine}; {@code dto} and
 * {@code engine} see nothing else of the module), the one public entry point, and what the module may not
 * import. They check {@code import} lines only, so a comment may still name a forbidden type.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF module architecture")
class HtmlPdfArchitectureUnitTest {

    private static final String MODULE_PACKAGE = "io.github.carlos_emr.carlos.documentManager.htmlpdf";
    private static final Path MODULE = Path.of("src/main/java/io/github/carlos_emr/carlos/documentManager/htmlpdf");
    private static final List<String> SUB_PACKAGES = List.of("dto", "engine", "service", "support");

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;.*$");
    private static final Pattern PUBLIC_TYPE = Pattern.compile("^public\\s+(?:(?:final|abstract|sealed|non-sealed)\\s+)*(?:class|interface|enum|record)\\s+\\w+.*$");

    private record SourceLine(Path file, int number, String text) {
        @Override
        public String toString() {
            return MODULE.relativize(file) + ":" + number + ": " + text.trim();
        }
    }

    private static List<Path> javaFiles(String subPackage) throws IOException {
        Path directory = subPackage.isEmpty() ? MODULE : MODULE.resolve(subPackage);
        try (Stream<Path> files = Files.walk(directory, subPackage.isEmpty() ? 1 : Integer.MAX_VALUE)) {
            return files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<Path> allJavaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MODULE)) {
            return files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<SourceLine> lines(List<Path> files) throws IOException {
        List<SourceLine> lines = new ArrayList<>();
        for (Path file : files) {
            List<String> content = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < content.size(); i++) {
                lines.add(new SourceLine(file, i + 1, content.get(i)));
            }
        }
        return lines;
    }

    /** The imported name of an {@code import} line, or {@code null} for any other line. */
    private static String importedName(SourceLine line) {
        var matcher = IMPORT.matcher(line.text());
        return matcher.matches() ? matcher.group(2) : null;
    }

    private static List<String> importsOfModulePackage(List<Path> files, String... packages) throws IOException {
        List<String> violations = new ArrayList<>();
        for (SourceLine line : lines(files)) {
            String imported = importedName(line);
            if (imported == null) {
                continue;
            }
            for (String name : packages) {
                if (imported.startsWith(MODULE_PACKAGE + "." + name + ".") || imported.equals(MODULE_PACKAGE + "." + name + ".*")) {
                    violations.add(line.toString());
                }
            }
        }
        return violations;
    }

    @Test
    @DisplayName("should keep dto free of every other package of the module")
    void shouldKeepDtoIndependent_ofModule() throws IOException {
        assertThat(importsOfModulePackage(javaFiles("dto"), "engine", "service", "support"))
                .as("dto depends on nothing else in the module").isEmpty();
    }

    @Test
    @DisplayName("should keep engine free of dto, service and support")
    void shouldKeepEngineIndependent_ofModule() throws IOException {
        assertThat(importsOfModulePackage(javaFiles("engine"), "dto", "service", "support"))
                .as("engine depends on nothing else in the module").isEmpty();
    }

    @Test
    @DisplayName("should keep support free of service")
    void shouldKeepSupportFree_ofService() throws IOException {
        assertThat(importsOfModulePackage(javaFiles("support"), "service")).as("support uses dto and engine value types only").isEmpty();
    }

    @Test
    @DisplayName("should import no servlet, Struts, DAO, manager or legacy facade type")
    void shouldImportNoServletStrutsDaoOrFacade_inModule() throws IOException {
        Pattern forbidden = Pattern.compile("^(jakarta\\.servlet\\..*|javax\\.servlet\\..*|org\\.apache\\.struts2\\..*|com\\.opensymphony\\..*"
                + "|.*\\.dao\\..*|.*\\.commn\\.dao\\..*|.*Dao|.*Manager|.*ManagerImpl|.*\\.ConvertToEdoc|.*\\.Doc2PDF"
                + "|.*\\.SpringUtils|.*\\.LoggedInInfo|.*\\.SecurityInfoManager)$");
        List<String> violations = new ArrayList<>();
        for (SourceLine line : lines(allJavaFiles())) {
            String imported = importedName(line);
            if (imported != null && forbidden.matcher(imported).matches()) {
                violations.add(line.toString());
            }
        }

        assertThat(violations).as("the module renders; controllers decide responses, and beans are constructor-injected").isEmpty();
    }

    @Test
    @DisplayName("should use no wildcard imports, which would hide the dependency checks")
    void shouldUseNoWildcardImports_inModule() throws IOException {
        List<String> violations = new ArrayList<>();
        for (SourceLine line : lines(allJavaFiles())) {
            String imported = importedName(line);
            if (imported != null && imported.endsWith(".*")) {
                violations.add(line.toString());
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should expose only the render service publicly in the service package")
    void shouldExposeOnlyRenderService_publiclyInService() throws IOException {
        List<String> publicTypes = new ArrayList<>();
        for (SourceLine line : lines(javaFiles("service"))) {
            if (PUBLIC_TYPE.matcher(line.text()).matches()) {
                publicTypes.add(MODULE.relativize(line.file()).toString());
            }
        }

        assertThat(publicTypes).containsExactly("service/HtmlPdfRenderService.java");
    }

    @Test
    @DisplayName("should have a package-info.java in the module and in each of its packages")
    void shouldHavePackageInfo_inTheModuleAndEveryPackage() {
        assertThat(MODULE.resolve("package-info.java")).isRegularFile();
        for (String subPackage : SUB_PACKAGES) {
            assertThat(MODULE.resolve(subPackage).resolve("package-info.java")).as(subPackage).isRegularFile();
        }
    }

    @Test
    @DisplayName("should hold only the four packages the dependency rules describe")
    void shouldHoldOnlyTheFourPackages_theDependencyRulesDescribe() throws IOException {
        List<String> packages = new ArrayList<>();
        try (Stream<Path> children = Files.list(MODULE)) {
            for (Path child : children.filter(Files::isDirectory).sorted().toList()) {
                packages.add(child.getFileName().toString());
            }
        }

        assertThat(packages).containsExactlyElementsOf(SUB_PACKAGES);
    }

    @Test
    @DisplayName("should not decode an image with ImageIO anywhere but ImageDecoding, which guards every format")
    void shouldDecodeImages_onlyThroughImageDecoding() throws IOException {
        Pattern decoding = Pattern.compile("ImageIO\\s*\\.\\s*(read|getImageReaders\\w*|createImageInputStream|getImageReader\\w*)\\s*\\(");
        List<String> violations = new ArrayList<>();
        for (Path file : allJavaFiles()) {
            if (file.getFileName().toString().equals("ImageDecoding.java")) {
                continue;
            }
            for (SourceLine line : lines(List.of(file))) {
                if (decoding.matcher(line.text()).find()) {
                    violations.add(line.toString());
                }
            }
        }

        assertThat(violations).as("a reader found any other way would decode a format ImageDecoding has not vetted").isEmpty();
    }

    @Test
    @DisplayName("should use no retired class-name suffix or SpringUtils lookup in the module")
    void shouldUseNoRetiredSuffix_orSpringUtilsLookupInTheModule() throws IOException {
        Pattern retired = Pattern.compile(".*(Prep|Manager|ManagerImpl|Helper|Utils|Util)\\.java$");
        List<String> badNames = new ArrayList<>();
        for (Path file : allJavaFiles()) {
            if (retired.matcher(file.getFileName().toString()).matches()) {
                badNames.add(MODULE.relativize(file).toString());
            }
        }
        List<String> springUtils = new ArrayList<>();
        for (SourceLine line : lines(allJavaFiles())) {
            if (line.text().contains("SpringUtils.")) {
                springUtils.add(line.toString());
            }
        }

        assertThat(badNames).as("docs/architecture/layer-names.md").isEmpty();
        assertThat(springUtils).as("beans are constructor-injected").isEmpty();
    }
}
