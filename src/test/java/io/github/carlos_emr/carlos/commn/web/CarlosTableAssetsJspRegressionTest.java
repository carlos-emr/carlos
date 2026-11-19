/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every page that declares a {@code data-carlos-table} must get the shared table assets
 * ({@code carlosTableAssets.jspf}) from a live static include, exactly once: either in the page
 * itself or, for a fragment, in the page that composes it with {@code <jsp:include>}. An include
 * inside a JSP comment is stripped at translation time and leaves the table unenhanced; an include
 * repeated by every fragment of a composed page loads the script and stylesheet once per fragment.
 */
@Tag("unit")
@Tag("web")
@DisplayName("Shared table asset includes")
class CarlosTableAssetsJspRegressionTest {

    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");
    private static final Path ASSETS = JSP_ROOT.resolve("common/carlosTableAssets.jspf");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern INCLUDE = Pattern.compile(
            "<%@\\s*include\\s+file=\"/WEB-INF/jsp/common/carlosTableAssets\\.jspf\"\\s*%>");
    private static final Pattern COMPOSES = Pattern.compile("<jsp:include\\s+page=\"([^\"]+)\"");

    private record Page(Path path, boolean table, int includesRaw, int includesLive, List<Path> composes) { }

    private static Map<Path, Page> pages() throws IOException {
        Map<Path, Page> pages = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(JSP_ROOT)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".jsp") || file.toString().endsWith(".jspf")).toList()) {
                if (path.equals(ASSETS)) continue;
                String raw = Files.readString(path, StandardCharsets.UTF_8);
                String live = JSP_COMMENT.matcher(raw).replaceAll("");
                List<Path> composes = new ArrayList<>();
                Matcher composed = COMPOSES.matcher(live);
                while (composed.find()) {
                    composes.add(Path.of("src/main/webapp" + composed.group(1)).normalize());
                }
                pages.put(path.normalize(), new Page(path.normalize(), live.contains("data-carlos-table"),
                        count(INCLUDE, raw), count(INCLUDE, live), composes));
            }
        }
        return pages;
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int n = 0;
        while (matcher.find()) n++;
        return n;
    }

    @Test
    @DisplayName("should include the table assets live, never inside a JSP comment")
    void shouldIncludeTableAssets_outsideJspComments() throws IOException {
        List<String> commented = new ArrayList<>();
        for (Page page : pages().values()) {
            if (page.includesRaw() != page.includesLive()) commented.add(page.path().toString());
        }
        assertThat(commented).as("includes swallowed by a JSP comment").isEmpty();
    }

    @Test
    @DisplayName("should give every table page its assets exactly once, directly or through its composing page")
    void shouldProvideTableAssets_exactlyOncePerRenderedPage() throws IOException {
        Map<Path, Page> pages = pages();
        List<String> problems = new ArrayList<>();
        for (Page page : pages.values()) {
            List<Page> parents = pages.values().stream()
                    .filter(candidate -> candidate.composes().contains(page.path())).toList();
            if (page.table() && page.includesLive() == 0) {
                boolean covered = !parents.isEmpty() && parents.stream().allMatch(parent -> parent.includesLive() > 0);
                if (!covered) problems.add(page.path() + " declares a table but no live include reaches it");
            }
            if (page.includesLive() > 1) problems.add(page.path() + " includes the assets " + page.includesLive() + " times");
            long viaFragments = page.composes().stream().filter(child -> pages.containsKey(child) && pages.get(child).includesLive() > 0).count();
            if (viaFragments + page.includesLive() > 1) {
                problems.add(page.path() + " renders the assets " + (viaFragments + page.includesLive()) + " times through its fragments");
            }
        }
        assertThat(problems).isEmpty();
    }
}
