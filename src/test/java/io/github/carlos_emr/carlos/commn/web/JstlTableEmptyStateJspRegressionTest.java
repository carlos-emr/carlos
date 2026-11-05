/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A migrated JSTL table that answers an empty list with a {@code table-empty} message must not
 * also render its header row above that message: displaytag showed only the message, and an
 * orphan header reads as a broken table. The {@code <c:if test="${not empty X}">} guard around the
 * table has to name the same attribute as the {@code ${empty X}} guard around the message.
 */
@Tag("unit")
@Tag("web")
@DisplayName("Empty JSTL tables")
class JstlTableEmptyStateJspRegressionTest {

    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");
    private static final Pattern EMPTY_GUARD = Pattern.compile("<c:if test=\"\\$\\{empty ([A-Za-z_][\\w.]*)\\}\">\\s*<p class=\"table-empty\"");
    private static final Pattern CARLOS_TABLE = Pattern.compile("<table\\b[^>]*\\bdata-carlos-table\\b[^>]*>");

    @Test
    @DisplayName("should hide the table, header included, when its list is empty")
    void shouldGuardTable_whenListIsEmpty() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JSP_ROOT)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".jsp")).toList()) {
                String page = Files.readString(path, StandardCharsets.UTF_8);
                Matcher guard = EMPTY_GUARD.matcher(page);
                while (guard.find()) {
                    String attribute = guard.group(1);
                    String before = page.substring(0, guard.start());
                    // A cell can contain a nested layout table (for example, staff teams).
                    // Find the migrated table itself so nested markup cannot bypass this check.
                    Matcher tables = CARLOS_TABLE.matcher(before);
                    int table = -1;
                    while (tables.find()) table = tables.start();
                    if (table < 0) continue;
                    if (!before.substring(0, table).contains("<c:if test=\"${not empty " + attribute + "}\">")) {
                        problems.add(path + " renders its table header before the empty message for " + attribute);
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }
}
