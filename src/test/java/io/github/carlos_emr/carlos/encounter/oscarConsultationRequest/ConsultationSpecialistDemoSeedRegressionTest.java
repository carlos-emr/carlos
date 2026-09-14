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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the demo consultation service-to-specialist links in
 * {@code .devcontainer/db/scripts/demo-specialists.sql}.
 *
 * <p>The dev snapshot ({@code development.sql}) replaces the provincial consultation-service
 * catalog with six fictional labels and links every one of them to the same two legacy
 * specialists, which makes a broken service filter indistinguishable from a working one in the
 * consultation request form and in Administration &gt; Edit Specialists Listing. The demo seed
 * gives each of those services a distinct slice of the 9001-9060 fake roster; these tests pin
 * that contract against both snapshot drift and a relapse into a
 * {@code database/mysql/updates/} patch, which no demo loader applies.
 *
 * @since 2026-06-01
 */
@DisplayName("Consultation specialist demo seed regressions")
@Tag("unit")
@Tag("database")
class ConsultationSpecialistDemoSeedRegressionTest {

    private static final Path DEMO_SPECIALISTS =
            Path.of(".devcontainer", "db", "scripts", "demo-specialists.sql");
    private static final Path DEVELOPMENT_SEED =
            Path.of(".devcontainer", "db", "scripts", "development.sql");
    private static final Path RETIRED_UPDATES_PATCH = Path.of("database", "mysql", "updates",
            "update-2026-06-01-varied-consult-specialist-demo-data.sql");

    /** {@code SELECT 'Acarology' AS serviceDesc, 9006 AS specId} and its bare continuation rows. */
    private static final Pattern DEMO_LINK = Pattern.compile(
            "SELECT\\s+'([A-Za-z]+)'\\s*(?:AS\\s+serviceDesc\\s*)?,\\s*(\\d+)");

    /** A {@code (1,'Radiology','1')} tuple inside the snapshot's consultationServices insert. */
    private static final Pattern SNAPSHOT_SERVICE = Pattern.compile("\\(\\d+,'([^']*)'");

    /** The one fictional label that is also a real provincial specialty, so Flyway owns it. */
    private static final String PROVINCIAL_LABEL = "Cardiology";

    /** Reserved for the clearly-fake demo roster; see the demo-specialists.sql header. */
    private static final int ROSTER_FIRST_SPEC_ID = 9001;
    private static final int ROSTER_LAST_SPEC_ID = 9060;

    @Test
    @DisplayName("every dev-snapshot service should get its own specialist slice")
    void shouldSeedDistinctSpecialists_forEveryDevSnapshotService() throws IOException {
        Map<String, Set<Integer>> links = demoLinks();

        assertThat(links).isNotEmpty();
        assertThat(links.values()).allSatisfy(slice -> assertThat(slice).isNotEmpty());
        // The whole point of the seed: no two services may render the same list.
        assertThat(links.values()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("demo links should only reference the fake specialist roster")
    void shouldLinkOnlyFakeRosterSpecialists_whenSeedingDevSnapshotServices() throws IOException {
        String seedSql = Files.readString(DEMO_SPECIALISTS, StandardCharsets.UTF_8);
        Set<Integer> linked = new TreeSet<>();
        demoLinks().values().forEach(linked::addAll);

        assertThat(linked).allMatch(specId ->
                specId >= ROSTER_FIRST_SPEC_ID && specId <= ROSTER_LAST_SPEC_ID);
        // Every linked specialist must be one this same file inserts, or the link is a dangling
        // row in the demo database.
        assertThat(linked).allMatch(specId -> seedSql.contains("  (" + specId + ", 'FAKE-"));
    }

    @Test
    @DisplayName("demo links should cover the dev-snapshot service catalog")
    void shouldCoverTheSnapshotServiceCatalog_withTheDemoSeed() throws IOException {
        Set<String> snapshotServices = snapshotConsultationServices();
        Set<String> seeded = new LinkedHashSet<>(demoLinks().keySet());
        seeded.add(PROVINCIAL_LABEL); // covered by the specType join earlier in the same file

        // A snapshot refresh that renames or adds a service fails here rather than silently
        // leaving that service showing the uniform legacy list again.
        assertThat(seeded).containsExactlyInAnyOrderElementsOf(snapshotServices);
    }

    @Test
    @DisplayName("the uniform-link cleanup should spare real provincial service labels")
    void shouldSpareProvincialServiceLabels_whenClearingUniformLinks() throws IOException {
        String seedSql = Files.readString(DEMO_SPECIALISTS, StandardCharsets.UTF_8);
        int deleteStart = seedSql.indexOf("DELETE ss");
        assertThat(deleteStart).isNotNegative();
        String deleteStatement = seedSql.substring(deleteStart, seedSql.indexOf(';', deleteStart));

        // 'Cardiology' is seeded by both provinces' migrations; deleting its links would let a
        // demo load undo Flyway data. The other five labels exist in no provincial catalog.
        assertThat(deleteStatement).doesNotContain("'" + PROVINCIAL_LABEL + "'");
        // Scoped away from the roster so a re-run cannot delete the links inserted just below.
        assertThat(deleteStatement).contains(
                "ss.specId NOT BETWEEN " + ROSTER_FIRST_SPEC_ID + " AND " + ROSTER_LAST_SPEC_ID);
    }

    @Test
    @DisplayName("the demo seed should not move back into database/mysql/updates")
    void shouldNotResurrectTheRetiredUpdatesPatch_forDemoSeeding() {
        // database/mysql/updates is frozen legacy: no demo loader applies this file, so a seed
        // parked there is dead code that silently never runs.
        assertThat(RETIRED_UPDATES_PATCH).doesNotExist();
    }

    /** Parses the demo-only {@code serviceDesc -> specIds} table out of the seed script. */
    private static Map<String, Set<Integer>> demoLinks() throws IOException {
        String seedSql = Files.readString(DEMO_SPECIALISTS, StandardCharsets.UTF_8);
        Map<String, Set<Integer>> links = new LinkedHashMap<>();
        Matcher matcher = DEMO_LINK.matcher(seedSql);
        while (matcher.find()) {
            links.computeIfAbsent(matcher.group(1), key -> new TreeSet<>())
                    .add(Integer.valueOf(matcher.group(2)));
        }
        return links;
    }

    /** Reads the consultation-service labels the dev snapshot truncate-reloads. */
    private static Set<String> snapshotConsultationServices() throws IOException {
        String insert;
        // development.sql is a ~54MB dump; stream it and stop at the one line we need rather
        // than pulling the whole snapshot into the heap.
        try (Stream<String> lines = Files.lines(DEVELOPMENT_SEED, StandardCharsets.UTF_8)) {
            Optional<String> line = lines
                    .filter(candidate -> candidate.startsWith("INSERT INTO `consultationServices` VALUES"))
                    .findFirst();
            assertThat(line).as("consultationServices insert in %s", DEVELOPMENT_SEED).isPresent();
            insert = line.orElseThrow();
        }

        Set<String> services = new LinkedHashSet<>();
        Matcher matcher = SNAPSHOT_SERVICE.matcher(insert);
        while (matcher.find()) {
            services.add(matcher.group(1));
        }
        return services;
    }
}
