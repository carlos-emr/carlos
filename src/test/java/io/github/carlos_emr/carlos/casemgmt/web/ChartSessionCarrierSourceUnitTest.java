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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No code keeps one chart's working state in a login-wide session attribute any more (issue
 * #4673). Each of these attributes was written by one chart window and read by a request from
 * another: the patient a request fell back to, the read-only right, the remembered note filters
 * the print applies, the text the last save wrote into the legacy eChart, the draft-restore
 * signal, a patient's message and lab lists, and the legacy editor's bean. The scan covers all
 * main Java and JSP sources, with comments removed, so a reintroduction anywhere fails here.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("No login-wide chart state in the session")
class ChartSessionCarrierSourceUnitTest extends CarlosUnitTestBase {

    private static final Path ROOT = Path.of(System.getProperty("basedir", "")).toAbsolutePath();

    /**
     * Each pattern is a session read or write of a login-wide name that the fix replaced. A
     * receiver other than {@code request} is a session ({@code session}, {@code se},
     * {@code request.getSession()}); request attributes live for one request and are allowed.
     */
    private static final String SESSION_ACCESS = "(?<!request)\\.(get|set|remove)Attribute\\(\\s*\"";
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile(SESSION_ACCESS + "casemgmt_DemoNo\""),
            Pattern.compile("sessionScope\\.readonly"),
            Pattern.compile("(se|session|getSession\\(\\))\\.setAttribute\\(\\s*\"readonly\""),
            Pattern.compile(SESSION_ACCESS + "lastSavedNoteString\""),
            Pattern.compile(SESSION_ACCESS + "restoring\""),
            Pattern.compile(SESSION_ACCESS + "CaseManagementViewAction_filter_"),
            Pattern.compile(SESSION_ACCESS + "casemgmt_msgBeans\""),
            Pattern.compile(SESSION_ACCESS + "casemgmt_labsbeans\""),
            Pattern.compile("(?<!request)\\.setAttribute\\(\\s*\"casemgmt_oscar_bean\"\\s*,"));

    @Test
    void shouldFindNoLoginWideChartState_inAnyMainSource() {
        List<String> offenders = new ArrayList<>();
        for (Path source : sources()) {
            String code = withoutComments(read(source), source.toString().endsWith(".java"));
            for (Pattern pattern : FORBIDDEN) {
                if (pattern.matcher(code).find()) {
                    offenders.add(ROOT.relativize(source) + " matches " + pattern.pattern());
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void shouldReadTheReadOnlyRightFromTheRequest_onTheOldChartViewAndNoteSearch() {
        for (String page : new String[]{"CaseManagementView.jsp", "search.jsp"}) {
            String jsp = read(ROOT.resolve("src/main/webapp/WEB-INF/jsp/casemgmt").resolve(page));
            assertThat(jsp).as(page).contains("requestScope.readonly");
        }
        String action = read(ROOT.resolve("src/main/java/io/github/carlos_emr/carlos/casemgmt/web/CaseManagementView2Action.java"));
        assertThat(action).contains("request.setAttribute(\"readonly\"");
    }

    @Test
    void shouldListThisPatientsLabsInPageScope_onTheLegacyNavigation() {
        String jsp = read(ROOT.resolve("src/main/webapp/WEB-INF/jsp/casemgmt/navigation.jsp"));
        assertThat(jsp).contains("pageContext.setAttribute(\"casemgmtLabsbeans\"")
                .contains("bean.demographicNo, \"\", \"\", \"\", \"U\")")
                // LabResultData exposes isMDS()/isCML(): the JavaBeans names are MDS and CML.
                .contains("${labrst.MDS}").contains("${labrst.CML}")
                .doesNotContain("${labrst.mds}").doesNotContain("${labrst.cml}");
    }

    private static List<Path> sources() {
        List<Path> files = new ArrayList<>();
        for (String dir : new String[]{"src/main/java", "src/main/webapp"}) {
            try (Stream<Path> walk = Files.walk(ROOT.resolve(dir))) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".jsp")
                                || p.toString().endsWith(".jspf"))
                        .forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        assertThat(files).hasSizeGreaterThan(1000);
        return files;
    }

    private static String withoutComments(String code, boolean java) {
        String stripped = code.replaceAll("(?s)<%--.*?--%>", "");
        stripped = stripped.replaceAll("(?s)/\\*.*?\\*/", "");
        if (java) {
            stripped = stripped.replaceAll("(?m)^\\s*//.*$", "");
        }
        return stripped;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
