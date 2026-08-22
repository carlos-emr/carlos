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
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the three configuration/view preconditions the prescription fax flow depends on,
 * each of which regressed silently at some point because nothing asserted them:
 *
 * <ul>
 *   <li>Routing — {@code /form/createcustomedpdf} is a plain servlet declared in
 *       {@code web.xml} ({@code FrmCustomedPDFServlet}); the Rx print and fax flows POST
 *       to it. Struts' global {@code struts.action.excludePattern} has to let it through,
 *       otherwise the Struts filter claims the request and answers 404 before the servlet
 *       runs — prescription faxing dies with "CARLOS Error: 404".</li>
 *   <li>Rx body newlines — {@code Preview2.jsp} posts the prescription text in the
 *       {@code rx} parameter, which the servlet splits on the platform line separator.
 *       Writing the {@code replaceAll} with a {@code "\\\n"} literal inline in a JSP tag
 *       attribute goes through tag-attribute unquoting and degrades the newline to a
 *       literal {@code "n"}, so the generated PDF faxes with an empty prescription body.
 *       The conversion must happen in a scriptlet block, where no unquoting applies.</li>
 *   <li>CSRF token freshness — {@code ViewScript2.jsp} must read the CSRFGuard token at
 *       call time ({@code getCsrfToken()}), not capture it at script-parse time: the
 *       hidden input is only populated on DOMContentLoaded, so a parse-time capture is
 *       always empty and every fetch() (including the digital-signature attach the fax
 *       button depends on) is rejected with 403.</li>
 * </ul>
 *
 * @since 2026-08-21
 */
@DisplayName("Prescription fax pipeline configuration")
@Tag("unit")
@Tag("prescription")
class RxFaxPipelineRegressionUnitTest {

    private static final Path STRUTS_XML =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/classes/struts.xml"));
    private static final Path WEB_XML =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/web.xml"));
    private static final Path PREVIEW2_JSP =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/rx/Preview2.jsp"));
    private static final Path VIEW_SCRIPT2_JSP =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp"));

    private static final Pattern STRUTS_ACTION_EXCLUDE_PATTERN = Pattern.compile(
            "<constant name=\"struts\\.action\\.excludePattern\" value=\"([^\"]+)\"\\s*/>");
    private static final Pattern CUSTOMED_PDF_SERVLET_MAPPING = Pattern.compile(
            "<servlet-mapping>\\s*<servlet-name>pdfCustomedCreator</servlet-name>\\s*"
                    + "<url-pattern>/form/createcustomedpdf</url-pattern>\\s*</servlet-mapping>",
            Pattern.DOTALL);

    @Test
    @DisplayName("should exclude the prescription PDF servlet URL from Struts action mapping")
    void shouldExcludeCustomedPdfServletUrl_fromStrutsActionMapping() throws IOException {
        String strutsXml = Files.readString(STRUTS_XML);
        Matcher matcher = STRUTS_ACTION_EXCLUDE_PATTERN.matcher(strutsXml);
        assertThat(matcher.find()).isTrue();

        Pattern excludePattern = Pattern.compile(matcher.group(1));
        assertThat(excludePattern.matcher("/form/createcustomedpdf").matches()).isTrue();
        assertThat(excludePattern.matcher("/carlos/form/createcustomedpdf").matches()).isTrue();
    }

    @Test
    @DisplayName("should keep the prescription PDF servlet mapping in web.xml")
    void shouldKeepCustomedPdfServletMapping_inWebXml() throws IOException {
        String webXml = Files.readString(WEB_XML);
        assertThat(CUSTOMED_PDF_SERVLET_MAPPING.matcher(webXml).find()).isTrue();
    }

    @Test
    @DisplayName("should build the rx parameter with real line separators in a scriptlet")
    void shouldBuildRxParameter_withScriptletLineSeparators() throws IOException {
        String preview2 = Files.readString(PREVIEW2_JSP);
        // The conversion must run inside a scriptlet block (no JSP tag-attribute unquoting).
        assertThat(preview2).contains("strRx.replace(\";\", System.getProperty(\"line.separator\"))");
        // The broken inline form was strRx.replaceAll with an escaped-newline literal inside
        // a JSP tag attribute — unquoting degrades it to a literal "n" and empties the faxed
        // body. The fixed code uses replace() in the scriptlet, so no strRx.replaceAll remains.
        assertThat(preview2).doesNotContain("strRx.replaceAll(");
    }

    @Test
    @DisplayName("should read the CSRF token at call time in ViewScript2")
    void shouldReadCsrfToken_atCallTimeInViewScript() throws IOException {
        String viewScript2 = Files.readString(VIEW_SCRIPT2_JSP);
        assertThat(viewScript2).contains("function getCsrfToken()");
        // A parse-time capture ("var csrfToken = ...") runs before CSRFGuard populates the
        // hidden input on DOMContentLoaded, so every fetch() would send an empty token.
        assertThat(viewScript2).doesNotContain("'CSRF-TOKEN': csrfToken");
    }

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
