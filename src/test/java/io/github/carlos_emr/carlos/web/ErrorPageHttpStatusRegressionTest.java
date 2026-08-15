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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Error page HTTP status source regression")
@Tag("unit")
@Tag("fast")
class ErrorPageHttpStatusRegressionTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path ERROR_PAGE =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/error/errorpage.jsp"));

    @Test
    @DisplayName("should retain the forwarded-error status normalization source guard")
    void shouldRetainForwardedErrorStatusNormalizationSourceGuard() throws Exception {
        String jsp = Files.readString(ERROR_PAGE, StandardCharsets.UTF_8);

        assertThat(jsp)
                .containsPattern("response\\s*\\.\\s*getStatus\\s*\\(\\s*\\)")
                .containsPattern("if\\s*\\([^)]*<\\s*400\\s*\\)")
                .contains("HttpServletResponse.SC_INTERNAL_SERVER_ERROR")
                .containsPattern("response\\s*\\.\\s*setStatus\\s*\\(")
                .containsPattern("\\$\\{\\s*carlos:forHtml\\s*\\([^)]*\\)\\s*}")
                .doesNotContain("pageContext.errorData.statusCode");
    }

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
