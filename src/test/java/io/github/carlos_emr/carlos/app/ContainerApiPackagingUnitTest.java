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
package io.github.carlos_emr.carlos.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Build-time guard that the APIs Tomcat 11 supplies are compiled against but never packaged.
 *
 * <p>Tomcat always loads {@code jakarta.el}, {@code jakarta.servlet}, {@code jakarta.annotation}
 * and its own {@code org.apache.*} packages from its {@code lib/} directory, so a copy in
 * {@code WEB-INF/lib} is never used inside the container; it only misleads tests and tools that
 * run outside it. Issue #5416 found the Expression Language 5.0.1 API shipping that way, at
 * compile scope through displaytag's JSTL API, beside Tomcat 11's EL 6.0.
 *
 * <p>{@code dependencies-lock.json} is the resolved dependency graph (the build's lock check
 * refuses any difference), so its {@code compile} and {@code runtime} jars are exactly what the
 * WAR plugin copies into {@code WEB-INF/lib}. This test opens each of those jars from the test
 * class path and applies the rules in {@code scripts/fixtures/tomcat-container-packages.json},
 * which {@code scripts/check-war-container-apis.py} also applies to the built WAR during the
 * Debian package build. Reading the classes, not only the coordinates, also catches an API
 * copied into another jar. See {@code docs/container-provided-apis.md}.
 *
 * @since 2026-10-29
 */
@Tag("unit")
@DisplayName("Container-supplied Jakarta APIs are compiled against, never packaged")
class ContainerApiPackagingUnitTest {

    private static final Path PROJECT = Paths.get(System.getProperty("user.dir"));
    private static final Set<String> PACKAGED_SCOPES = Set.of("compile", "runtime");
    /** Multi-release jars keep per-JDK copies of a class under this prefix. */
    private static final Pattern VERSIONED = Pattern.compile("^META-INF/versions/[0-9]+/");
    /** Far fewer than the WAR ships; guards against a lookup that silently scans nothing. */
    private static final int MINIMUM_PACKAGED_JARS = 100;

    private static List<LockedArtifact> locked;
    private static Rules rules;

    /** One {@code dependencies-lock.json} entry. */
    private record LockedArtifact(String groupId, String artifactId, String version, String scope,
                                  String type, String classifier) {

        /** Whether the WAR plugin copies this artifact into {@code WEB-INF/lib}. */
        boolean packaged() {
            return PACKAGED_SCOPES.contains(scope) && "jar".equals(type);
        }

        /** The artifact's path below a Maven repository root. */
        String repositoryPath() {
            return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version
                    + (classifier == null ? "" : "-" + classifier) + ".jar";
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version + (classifier == null ? "" : ":" + classifier)
                    + " (" + scope + ")";
        }
    }

    /** The shared rules file, as {@code scripts/check-war-container-apis.py} reads it. */
    private record Rules(List<String> containerPackages, List<String> applicationPackages,
                         List<String> containerServices, List<String> requiredClasses) {

        /** The dotted container package an entry belongs to, or {@code null} for an application class. */
        String containerPackage(String entry) {
            if (!entry.endsWith(".class")) {
                return null;
            }
            String name = VERSIONED.matcher(entry).replaceFirst("");
            if (name.endsWith("module-info.class") || applicationPackages.stream().anyMatch(name::startsWith)) {
                return null;
            }
            for (String prefix : containerPackages) {
                if (name.startsWith(prefix)) {
                    return name.substring(0, name.lastIndexOf('/')).replace('/', '.');
                }
            }
            return null;
        }
    }

    @BeforeAll
    static void readLockAndRules() throws IOException {
        ObjectMapper json = new ObjectMapper();
        JsonNode lock = json.readTree(PROJECT.resolve("dependencies-lock.json").toFile());
        List<LockedArtifact> artifacts = new ArrayList<>();
        for (JsonNode entry : lock.path("dependencies")) {
            artifacts.add(new LockedArtifact(entry.path("groupId").asText(), entry.path("artifactId").asText(),
                    entry.path("version").asText(), entry.path("scope").asText(), entry.path("type").asText(),
                    entry.hasNonNull("classifier") ? entry.get("classifier").asText() : null));
        }
        locked = Collections.unmodifiableList(artifacts);

        JsonNode rulesJson = json.readTree(PROJECT.resolve("scripts/fixtures/tomcat-container-packages.json").toFile());
        rules = new Rules(strings(rulesJson, "containerPackages"), strings(rulesJson, "applicationPackages"),
                strings(rulesJson, "containerServices"), strings(rulesJson, "requiredClasses"));
        assertThat(locked).as("dependencies-lock.json lists no dependencies").isNotEmpty();
        assertThat(rules.containerPackages()).as("the rules name no container packages").isNotEmpty();
        assertThat(rules.requiredClasses()).as("the rules name no required classes").isNotEmpty();
    }

    @Test
    @DisplayName("should compile against Expression Language 6, the version Tomcat 11 implements, without packaging it")
    void shouldCompileAgainstExpressionLanguageSix_withoutPackagingIt() {
        List<LockedArtifact> expressionLanguage = locked.stream()
                .filter(artifact -> artifact.groupId().equals("jakarta.el"))
                .toList();

        assertThat(expressionLanguage)
                .as("jakarta.el artifacts in dependencies-lock.json")
                .singleElement()
                .satisfies(api -> {
                    assertThat(api.artifactId()).isEqualTo("jakarta.el-api");
                    assertThat(api.version()).as("EL API version; Tomcat 11 implements EL 6.0").startsWith("6.");
                    assertThat(api.scope()).as("EL API scope; Tomcat supplies it").isEqualTo("provided");
                });
    }

    @Test
    @DisplayName("should package no class Tomcat loads from its own lib, nor a competing EL implementation")
    void shouldPackageNoContainerApi_inAnyBundledJar() throws IOException {
        Map<String, Set<String>> bundled = new TreeMap<>();
        for (LockedArtifact artifact : packagedArtifacts()) {
            Set<String> found = new TreeSet<>();
            try (ZipFile jar = new ZipFile(jarOnTestClassPath(artifact).toFile())) {
                for (ZipEntry entry : Collections.list(jar.entries())) {
                    String containerPackage = rules.containerPackage(entry.getName());
                    if (containerPackage != null) {
                        found.add(containerPackage);
                    }
                    if (rules.containerServices().contains(entry.getName())) {
                        found.add(entry.getName() + " (competing implementation)");
                    }
                }
            }
            if (!found.isEmpty()) {
                bundled.put(artifact.toString(), found);
            }
        }

        assertThat(bundled)
                .as("Packaged dependencies that carry an API Tomcat supplies. Declare the API "
                        + "<scope>provided</scope> (see docs/container-provided-apis.md).")
                .isEmpty();
    }

    @Test
    @DisplayName("should package the JSTL API and implementation, because Tomcat provides no JSTL")
    void shouldPackageJstl_becauseTomcatProvidesNone() throws IOException {
        Set<String> missing = new TreeSet<>(rules.requiredClasses());
        for (LockedArtifact artifact : packagedArtifacts()) {
            try (ZipFile jar = new ZipFile(jarOnTestClassPath(artifact).toFile())) {
                missing.removeIf(required -> jar.getEntry(required) != null);
            }
        }

        assertThat(missing).as("JSTL classes no packaged dependency provides").isEmpty();
    }

    @Test
    @DisplayName("should treat Tomcat's filtered packages as container packages, minus the filter's own exceptions")
    void shouldClassifyPackages_asTomcatClassLoaderFilterDoes() {
        assertThat(rules.containerPackage("jakarta/el/ExpressionFactory.class")).isEqualTo("jakarta.el");
        assertThat(rules.containerPackage("META-INF/versions/11/jakarta/el/ELContext.class")).isEqualTo("jakarta.el");
        assertThat(rules.containerPackage("jakarta/annotation/PostConstruct.class")).isEqualTo("jakarta.annotation");
        assertThat(rules.containerPackage("jakarta/servlet/http/HttpServlet.class")).isEqualTo("jakarta.servlet.http");
        assertThat(rules.containerPackage("org/apache/el/ExpressionFactoryImpl.class")).isEqualTo("org.apache.el");
        assertThat(rules.containerPackage("jakarta/servlet/jsp/jstl/core/Config.class")).isNull();
        assertThat(rules.containerPackage("org/apache/tomcat/jdbc/pool/DataSource.class")).isNull();
        assertThat(rules.containerPackage("org/glassfish/expressly/ExpressionFactoryImpl.class")).isNull();
        assertThat(rules.containerPackage("jakarta/el/LocalStrings.properties")).isNull();
        assertThat(rules.containerPackage("module-info.class")).isNull();
    }

    private static List<LockedArtifact> packagedArtifacts() {
        List<LockedArtifact> packaged = locked.stream().filter(LockedArtifact::packaged).toList();
        assertThat(packaged.size())
                .as("packaged jars in dependencies-lock.json (a much smaller number means the lock "
                        + "file was not read as expected)")
                .isGreaterThan(MINIMUM_PACKAGED_JARS);
        return packaged;
    }

    /** The jar Maven resolved for a locked artifact; every packaged jar is also on the test class path. */
    private static Path jarOnTestClassPath(LockedArtifact artifact) {
        String suffix = "/" + artifact.repositoryPath();
        return testClassPath().stream()
                .filter(element -> element.replace(File.separatorChar, '/').endsWith(suffix))
                .map(Paths::get)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Packaged dependency " + artifact
                        + " is not on the test class path, so its classes cannot be checked"));
    }

    private static List<String> testClassPath() {
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return Arrays.asList(classPath.split(Pattern.quote(File.pathSeparator)));
    }

    private static List<String> strings(JsonNode rulesJson, String key) {
        JsonNode values = rulesJson.path(key);
        assertThat(values.isArray()).as("rules '%s' is a list", key).isTrue();
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }
}
