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
package io.github.carlos_emr.carlos.admin;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for admin CSS style JSP encoding.
 *
 * @since 2026-05-30
 */
@DisplayName("Manage CSS styles JSP encoding")
@Tag("unit")
@Tag("security")
class ManageCssStylesJspEncodingRegressionTest {
    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path MANAGE_CSS_STYLES_JSP_PATH =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/manageCSSStyles.jsp");

    @Test
    void shouldEncodeSavedStyleOptions_inSelectDropdown() throws Exception {
        String jsp = Files.readString(resolveProjectPath(MANAGE_CSS_STYLES_JSP_PATH));
        int selectStart = jsp.indexOf("<select name=\"selectedStyle\" id=\"style\">");
        int selectEnd = jsp.indexOf("</select>", selectStart);

        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        assertThat(selectEnd).isGreaterThan(selectStart);
        String savedStylesSelect = jsp.substring(selectStart, selectEnd);
        assertThat(jsp).contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>");
        assertThat(savedStylesSelect)
                .contains("<option value=\"${carlos:forHtmlAttribute(style.style)}\">${carlos:forHtml(style.name)}</option>")
                .doesNotContain("<option value=\"${style.style}\">${style.name}</option>")
                .doesNotContain("value=\"${style.style}")
                .doesNotContain(">${style.name}</option>");
    }

    /**
     * Resolves a project-relative path from the Maven {@code basedir} property or
     * current working directory, walking parent directories for IDE and CLI runs.
     *
     * @param relativePath path relative to the project root
     * @return resolved regular file or directory path
     */
    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
