/**
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Report by Template group views to "store the name raw, encode it at output" (#5639).
 *
 * <p>While {@code RBTGroupManager} ran names through {@code Encode.forJava} the views printed them
 * unencoded; with raw storage every output site must carry its own context encoder.</p>
 *
 * @since 2026-11-05
 */
@DisplayName("RBT group name output encoding JSP regressions")
@Tag("unit")
@Tag("report")
@Tag("security")
class RBTGroupNameOutputEncodingJspRegressionUnitTest extends CarlosUnitTestBase {

    private static final Path GROUPS_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/oscarReport/reportByTemplate/templateGroups.jsp");
    private static final Path HOME_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/oscarReport/reportByTemplate/homePage.jsp");
    private static final Path MANAGER_JAVA =
            Path.of("src/main/java/io/github/carlos_emr/carlos/managers/RBTGroupManager.java");

    /** Any EL expression that prints a group name: the loop variable or an RBTGroup's groupName. */
    private static final Pattern GROUP_NAME_EL =
            Pattern.compile("\\$\\{[^}]*(?:\\bgroupName\\b|\\.groupName)[^}]*}");

    @Test
    @DisplayName("should keep the manager from Java-escaping the stored label")
    void shouldNotJavaEscapeLabel_inManager() throws IOException {
        String source = Files.readString(MANAGER_JAVA, StandardCharsets.UTF_8);

        assertThat(source).doesNotContain("Encode.forJava(").doesNotContain("import org.owasp.encoder.Encode;");
    }

    @Test
    @DisplayName("should wrap every group name expression in a CARLOS encoder")
    void shouldEncodeEveryGroupNameExpression_inTemplateGroupsJsp() throws IOException {
        String jsp = Files.readString(GROUPS_JSP, StandardCharsets.UTF_8);

        Matcher matcher = GROUP_NAME_EL.matcher(jsp);
        int found = 0;
        while (matcher.find()) {
            found++;
            assertThat(matcher.group()).as("group name output must be encoded").contains("carlos:for");
        }
        assertThat(found).isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("should use the context-correct encoder for attribute, url and body output")
    void shouldUseContextEncoders_inTemplateGroupsJsp() throws IOException {
        String jsp = Files.readString(GROUPS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("title=\"${carlos:forHtmlAttribute(groupName)}\"")
                .contains("tempInGroup?groupName=${carlos:forUriComponent(groupName)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(groupName)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(temp.groupName)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(templatesInGroup[0].groupName)}\"")
                .contains("Add Selected Template(s) to ${carlos:forHtmlContent(templatesInGroup[0].groupName)}")
                .doesNotContain("groupName=${ groupName }");
    }

    @Test
    @DisplayName("should encode group names in the template library selector")
    void shouldEncodeGroupName_inHomePageSelector() throws IOException {
        String jsp = Files.readString(HOME_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<%=SafeEncode.forHtmlContent(templateGroups.get(i))%>")
                .doesNotContain("<%=templateGroups.get(i)%>");
    }
}
