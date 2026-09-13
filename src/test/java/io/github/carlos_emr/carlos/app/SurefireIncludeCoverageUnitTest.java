/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Build-time guard against test classes that silently never run in CI.
 *
 * <p>The {@code maven-surefire-plugin} in {@code pom.xml} declares explicit {@code <includes>},
 * which <em>replace</em> Surefire's default {@code **}{@code /*Test.java} discovery. A JUnit class is
 * only selected if its simple name ends in {@code UnitTest}/{@code IntegrationTest}/{@code RegressionTest}
 * (or {@code Tests}), or its package path contains a {@code test}/{@code tickler}/{@code messenger}/
 * {@code provider}/{@code managers} segment. A plain {@code *Test} class anywhere else compiles, is
 * green locally under an explicit {@code -Dtest=...} run, and is <strong>never executed by a plain
 * {@code mvn test}</strong> — the exact trap that hid this PR's own servlet/authorization tests.
 *
 * <p>This test recomputes the set of unmatched test classes from the source tree and fails if it has
 * grown beyond the recorded baseline in {@code src/test/resources/surefire-unmatched-baseline.txt}.
 * A newly added {@code *Test} that falls outside the include patterns therefore fails the build with a
 * pointer to the fix: rename it to {@code *UnitTest}/{@code *IntegrationTest}. The pre-existing
 * baseline is a known backlog tracked for a separate repo-wide cleanup; shrinking it is always allowed
 * (this test only fails on growth), and baseline entries that are later renamed simply drop out.
 */
@Tag("unit")
class SurefireIncludeCoverageUnitTest {

    private static final String BASELINE_RESOURCE = "/surefire-unmatched-baseline.txt";

    @Test
    @DisplayName("should not add test classes that the Surefire includes never select")
    void shouldNotAddTestClasses_thatSurefireNeverSelects() throws IOException {
        Path testSourceRoot = Paths.get(System.getProperty("user.dir"), "src", "test", "java");
        // Only meaningful when building from source (CI and devcontainer both do). If the source tree
        // is absent (e.g. running from a packaged artifact), there is nothing to scan.
        assumeTrue(Files.isDirectory(testSourceRoot),
                "test source tree not present; guard is a build-from-source check");

        Set<String> unmatched = computeUnmatchedTestClasses(testSourceRoot);
        Set<String> baseline = loadBaseline();

        Set<String> newlyUnmatched = new TreeSet<>(unmatched);
        newlyUnmatched.removeAll(baseline);

        assertThat(newlyUnmatched)
                .as("Test classes that the pom.xml Surefire <includes> never select, so a plain "
                        + "`mvn test` (CI) skips them. Rename each to *UnitTest or *IntegrationTest "
                        + "(or, if intentionally excluded, add it to "
                        + "src/test/resources/surefire-unmatched-baseline.txt).")
                .isEmpty();
    }

    /**
     * Recomputes, from the source tree, the JUnit test classes NOT selected by the Surefire
     * {@code <includes>} — mirroring the pom patterns exactly.
     */
    private Set<String> computeUnmatchedTestClasses(Path testSourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(testSourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith("Test.java") || name.endsWith("Tests.java");
                    })
                    .map(p -> toRelativeClassInfo(testSourceRoot, p))
                    .filter(info -> !isSelectedByIncludes(info))
                    .map(info -> info.fqcn)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private ClassInfo toRelativeClassInfo(Path root, Path file) {
        Path relative = root.relativize(file);
        String simpleName = file.getFileName().toString().replaceFirst("\\.java$", "");
        StringBuilder pkg = new StringBuilder();
        // All path elements except the last (the file) form the package.
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            if (pkg.length() > 0) {
                pkg.append('.');
            }
            pkg.append(relative.getName(i).toString());
        }
        String fqcn = pkg.length() == 0 ? simpleName : pkg + "." + simpleName;
        return new ClassInfo(fqcn, simpleName, "/" + pkg.toString().replace('.', '/') + "/");
    }

    /** True if a Surefire {@code <include>} pattern would select this class. */
    private boolean isSelectedByIncludes(ClassInfo info) {
        // Suffix-based includes.
        if (info.simpleName.endsWith("UnitTest")
                || info.simpleName.endsWith("IntegrationTest")
                || info.simpleName.endsWith("RegressionTest")
                || info.simpleName.endsWith("Tests")) {
            return true;
        }
        // Package-segment includes for plain *Test classes.
        return info.packageSegments.contains("/test/")
                || info.packageSegments.contains("/tickler/")
                || info.packageSegments.contains("/messenger/")
                || info.packageSegments.contains("/provider/")
                || info.packageSegments.contains("/managers/");
    }

    private Set<String> loadBaseline() throws IOException {
        try (var in = getClass().getResourceAsStream(BASELINE_RESOURCE)) {
            assertThat(in).as("baseline resource %s must be on the test classpath", BASELINE_RESOURCE).isNotNull();
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList());
            return new TreeSet<>(lines);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static final class ClassInfo {
        final String fqcn;
        final String simpleName;
        /** Package as a slash-delimited path with leading and trailing slashes, e.g. {@code /a/b/}. */
        final String packageSegments;

        ClassInfo(String fqcn, String simpleName, String packageSegments) {
            this.fqcn = fqcn;
            this.simpleName = simpleName;
            this.packageSegments = packageSegments;
        }
    }
}
