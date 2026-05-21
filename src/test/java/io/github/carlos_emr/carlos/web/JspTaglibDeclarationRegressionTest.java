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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for JSPs that previously used a tag-library prefix
 * (e.g. {@code <security:>}, {@code <oscar:>}, {@code <bean:>}) without
 * declaring the corresponding {@code <%@ taglib %>} directive.
 *
 * <p>Each of these JSPs is dispatched standalone (as a Struts action result
 * or via {@code <jsp:include>}), so they cannot inherit taglib declarations
 * from a parent page; the missing directive would cause a JSP translation
 * failure or render the tag as literal text at runtime.
 *
 * @since 2026-05-21
 */
@DisplayName("JSP taglib declarations")
@Tag("unit")
class JspTaglibDeclarationRegressionTest {
    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path JSP_ROOT = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp"));

    @Test
    @DisplayName("should declare security taglib when listTemplates.jsp uses <security:oscarSec>")
    void shouldDeclareSecurityTaglib_forListTemplates() throws Exception {
        String jsp = readJsp("oscarReport/reportByTemplate/listTemplates.jsp");

        assertThat(jsp)
                .contains("<security:oscarSec")
                .contains("<%@ taglib uri=\"/WEB-INF/security.tld\" prefix=\"security\" %>");
    }

    @Test
    @DisplayName("should declare oscar taglib when zdemographicfulltitlesearch.jsp uses <oscar:oscarPropertiesCheck>")
    void shouldDeclareOscarTaglib_forZdemographicFullTitleSearch() throws Exception {
        String jsp = readJsp("demographic/zdemographicfulltitlesearch.jsp");

        assertThat(jsp)
                .contains("<oscar:oscarPropertiesCheck")
                .contains("<%@ taglib uri=\"/WEB-INF/oscar-tag.tld\" prefix=\"oscar\" %>");
    }

    @Test
    @DisplayName("should use fmt:message instead of removed Struts 1 bean:message in queue.jsp")
    void shouldNotUseStruts1BeanMessage_inProgramViewQueue() throws Exception {
        String jsp = readJsp("PMmodule/Admin/ProgramView/queue.jsp");

        // Struts 1 <bean:message> has no taglib in Struts 7; modern usage is <fmt:message>.
        // Both i18n keys that were previously bound to <bean:message> must now use <fmt:message>
        // against the oscarResources bundle. Match keys independently of source-line wrapping.
        assertThat(jsp)
                .doesNotContain("<bean:message")
                .contains("<fmt:setBundle basename=\"oscarResources\"/>")
                .contains("<fmt:message key=\"global.encounter\"/>")
                .containsPattern("<fmt:message\\s+key=\"provider\\.appointmentProviderAdminDay\\.btnE\"\\s*/>");
    }

    private static String readJsp(String relativePath) throws Exception {
        return Files.readString(JSP_ROOT.resolve(relativePath));
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
