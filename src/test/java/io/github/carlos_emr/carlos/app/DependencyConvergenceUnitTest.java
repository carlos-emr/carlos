/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guards the library families issue #5711 aligned, in the resolved graph and on the class path.
 *
 * <p>Maven mediates every artifact on its own, so a module that only one library asks for keeps
 * that library's version beside the rest of its family. That is how {@code httpclient5-cache}
 * stayed at 5.3.1 (through OpenSAML's {@code shib-networking}) beside {@code httpclient5} 5.6.5,
 * and {@code jackson-datatype-jsr310} at 2.17.1 beside {@code jackson-databind} 2.22.4. The
 * {@code require-converged-dependencies} enforcer execution in {@code pom.xml} fails the build
 * at {@code validate} on such a split; this test pins the same outcome in
 * {@code dependencies-lock.json}, which records whatever was resolved (the build's lock check
 * refuses any difference), so a lock regenerated with the enforcer skipped cannot carry a split
 * unseen. It also checks the Jackson jars the tests actually load agree.
 *
 * @since 2026-11-26
 */
@Tag("unit")
@DisplayName("Library families resolve to one version (#5711)")
class DependencyConvergenceUnitTest {

    private static final Path PROJECT = Paths.get(System.getProperty("user.dir"));
    /** Far fewer than the lock holds; guards against a parse that silently reads nothing. */
    private static final int MINIMUM_LOCKED_ARTIFACTS = 100;

    private static List<Locked> locked;

    /** One {@code dependencies-lock.json} entry. */
    private record Locked(String groupId, String artifactId, String version, String scope) {
        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version + " (" + scope + ")";
        }
    }

    @BeforeAll
    static void readLock() throws IOException {
        JsonNode lock = new ObjectMapper().readTree(PROJECT.resolve("dependencies-lock.json").toFile());
        List<Locked> artifacts = new ArrayList<>();
        for (JsonNode entry : lock.path("dependencies")) {
            artifacts.add(new Locked(entry.path("groupId").asText(), entry.path("artifactId").asText(),
                    entry.path("version").asText(), entry.path("scope").asText()));
        }
        assertThat(artifacts).as("artifacts in dependencies-lock.json").hasSizeGreaterThan(MINIMUM_LOCKED_ARTIFACTS);
        locked = List.copyOf(artifacts);
    }

    @Test
    @DisplayName("every Jackson module is at the jackson-databind version, annotations at its major.minor")
    void shouldResolveEveryJacksonModule_toTheDatabindVersion() {
        String databind = version("com.fasterxml.jackson.core", "jackson-databind");
        List<Locked> jackson = locked.stream()
                .filter(artifact -> artifact.groupId().startsWith("com.fasterxml.jackson."))
                .toList();
        // The module CDS import and export use, and the one that had split, must be there.
        assertThat(jackson).extracting(Locked::artifactId)
                .contains("jackson-core", "jackson-annotations", "jackson-datatype-jsr310");
        // Jackson 2.20+ versions jackson-annotations as major.minor ("2.22" for 2.22.4).
        String annotations = databind.substring(0, databind.indexOf('.', databind.indexOf('.') + 1));
        for (Locked artifact : jackson) {
            String expected = artifact.artifactId().equals("jackson-annotations") ? annotations : databind;
            assertThat(artifact.version()).as(artifact.toString()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("httpclient5-cache resolves beside httpclient5 at the same version")
    void shouldResolveHttpClientCache_toTheHttpClientVersion() {
        assertThat(version("org.apache.httpcomponents.client5", "httpclient5-cache"))
                .isEqualTo(version("org.apache.httpcomponents.client5", "httpclient5"));
    }

    @Test
    @DisplayName("each HttpComponents 5 group resolves to one version")
    void shouldResolveEachHttpComponentsGroup_toOneVersion() {
        Map<String, TreeSet<String>> versions = locked.stream()
                .filter(artifact -> artifact.groupId().startsWith("org.apache.httpcomponents."))
                .collect(Collectors.groupingBy(Locked::groupId, TreeMap::new,
                        Collectors.mapping(Locked::version, Collectors.toCollection(TreeSet::new))));
        assertThat(versions).containsKeys("org.apache.httpcomponents.client5", "org.apache.httpcomponents.core5");
        versions.forEach((group, found) -> assertThat(found).as(group).hasSize(1));
    }

    @Test
    @DisplayName("the jsr310 module the tests load comes from the same Jackson release as databind")
    void shouldLoadJavaTimeModule_fromTheDatabindRelease() {
        // PackageVersion is generated into each Jackson jar from its own pom, so this reads the
        // version of the jar actually on the class path, whatever the lock says. Version.equals
        // also compares the artifact, so compare the version text.
        String databind = com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION.toString();
        assertThat(com.fasterxml.jackson.datatype.jsr310.PackageVersion.VERSION).hasToString(databind);
        assertThat(com.fasterxml.jackson.core.json.PackageVersion.VERSION).hasToString(databind);
        assertThat(databind).isEqualTo(version("com.fasterxml.jackson.core", "jackson-databind"));
    }

    private static String version(String groupId, String artifactId) {
        List<String> found = locked.stream()
                .filter(artifact -> artifact.groupId().equals(groupId) && artifact.artifactId().equals(artifactId))
                .map(Locked::version)
                .toList();
        assertThat(found).as(groupId + ":" + artifactId + " in dependencies-lock.json").hasSize(1);
        return found.get(0);
    }
}
