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
package io.github.carlos_emr.carlos.web;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code request} is not a JSP EL implicit object, so {@code ${request.contextPath}} renders as an
 * empty string and a link built from it starts at the host root instead of the application. Pages must
 * use {@code ${pageContext.request.contextPath}}.
 */
@DisplayName("JSP links built from the request context path")
@Tag("unit")
class JspRequestContextPathRegressionTest {

    private static final Path WEBAPP_ROOT = resolveProjectPath(Path.of("src/main/webapp"));
    private static final Path JSP_ROOT = WEBAPP_ROOT.resolve("WEB-INF/jsp");
    // An EL expression that reads the nonexistent "request" object, e.g. ${request.contextPath}.
    private static final Pattern REQUEST_EL = Pattern.compile("\\$\\{\\s*request\\.");
    // A stylesheet link made from the context path, capturing the path after it.
    private static final Pattern CONTEXT_STYLESHEET = Pattern.compile(
            "\\$\\{pageContext\\.request\\.contextPath\\}(/[\\w./-]+\\.css)");
    /*
     * casemgmt/attachClient.jsp is left to #4775 (the attach-client popup's own fix), which rewrites its
     * links. The exemption stays after that merges: #4775's explanatory JSP comment quotes the
     * ${request.contextPath} form, and this line scan does not skip comments.
     */
    private static final Set<String> FIXED_ELSEWHERE = Set.of("casemgmt/attachClient.jsp");

    @Test
    @DisplayName("should not read the request EL object in any JSP")
    void shouldNotReadRequestElObject_inAnyJsp() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(WEBAPP_ROOT)) {
            for (Path path : files.filter(Files::isRegularFile).filter(JspRequestContextPathRegressionTest::isJsp).toList()) {
                if (FIXED_ELSEWHERE.contains(JSP_ROOT.relativize(path).toString().replace('\\', '/'))) {
                    continue;
                }
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (REQUEST_EL.matcher(lines.get(i)).find()) {
                        offenders.add(WEBAPP_ROOT.relativize(path) + ":" + (i + 1));
                    }
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin/facility/EditFacility.jsp", "admin/facility/ListFacilities.jsp",
        "admin/sitesAdmin.jsp", "admin/sitesAdminDetail.jsp"})
    @DisplayName("should link stylesheets that the webapp ships")
    void shouldLinkShippedStylesheets_forFixedPage(String page) throws IOException {
        Matcher stylesheet = CONTEXT_STYLESHEET.matcher(Files.readString(JSP_ROOT.resolve(page), StandardCharsets.UTF_8));
        int found = 0;
        while (stylesheet.find()) {
            found++;
            assertThat(WEBAPP_ROOT.resolve(stylesheet.group(1).substring(1))).as("%s links %s", page, stylesheet.group(1))
                    .isRegularFile();
        }
        assertThat(found).as("%s links its stylesheets from the context path", page).isPositive();
    }

    private static boolean isJsp(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".tag");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot find " + relativePath);
    }
}
