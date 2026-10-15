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
package io.github.carlos_emr.carlos.commn.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Disease Registry report's quick-list selector ({@code oscarReportDxReg.jsp}).
 *
 * <p>The selector is a one-shot Add control: whatever it holds is posted with every Add,
 * including an Add of a single code. Issue #4547: it rendered the shared {@code allQuickLists}
 * session handler and its {@code lastUsed} marker, which the patient Disease Registry sets on a
 * default list, so after that page had been opened the report came back with a list preselected
 * and the next single-code Add added the whole list. The live check is the
 * {@code dx-registry-status-update} Playwright workflow.
 *
 * @since 2026-10-14
 */
@DisplayName("oscarReportDxReg.jsp quick-list selector regressions")
@Tag("unit")
@Tag("dxresearch")
class OscarReportDxRegJspRegressionTest {

    private static final Path REPORT_JSP = resolveProjectPath(Path.of("src", "main", "webapp", "WEB-INF",
            "jsp", "oscarReport", "oscarReportDxReg.jsp"));

    /** The contents of the {@code <select>} named {@code quickListName}, through its closing tag. */
    private static final Pattern QUICK_LIST_SELECT = Pattern.compile(
            "<select\\b[^>]*\\bname=\"quickListName\"[^>]*>(.*?)</select>", Pattern.DOTALL);

    @Test
    @DisplayName("should list the report's own quick lists rather than the shared registry handler")
    void shouldReadReportQuickLists_insteadOfSharedRegistryHandler() throws IOException {
        String select = quickListSelect();

        assertThat(select)
                .contains("items=\"${" + DxresearchReport2Action.REPORT_QUICK_LISTS + ".dxQuickListBeanVector}\"")
                .doesNotContain("allQuickLists");
    }

    @Test
    @DisplayName("should never preselect a quick list in the report's Add control")
    void shouldNotPreselectQuickList_forReportAddControl() throws IOException {
        String select = quickListSelect();

        assertThat(select)
                .contains("<option value=\"\">Add Dx QuickList</option>")
                .doesNotContain("lastUsed")
                .doesNotContainIgnoringCase("selected");
    }

    private static String quickListSelect() throws IOException {
        String source = Files.readString(REPORT_JSP, StandardCharsets.UTF_8);
        Matcher matcher = QUICK_LIST_SELECT.matcher(source);
        assertThat(matcher.find()).as("quickListName select in %s", REPORT_JSP).isTrue();
        String select = matcher.group(1);
        assertThat(matcher.find()).as("exactly one quickListName select").isFalse();
        return select;
    }

    /**
     * Resolves a repo-relative fixture by walking up from the working directory, so the test
     * passes whether Surefire or an IDE picked the module root or a parent as {@code user.dir}.
     */
    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath);
    }
}
