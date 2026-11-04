/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for the blank-template and /email/ relative-link failure in #4477. */
@Tag("unit")
@Tag("email")
@DisplayName("eForm email preparation error page")
class EmailComposeErrorPageRegressionTest {

    @Test
    void shouldDisplayPreparationFailure_withoutDispatchingToEFormViewer() throws IOException {
        String struts = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-provider.xml"));
        assertThat(struts).containsPattern(Pattern.compile(
                "<action name=\"email/emailComposeAction\"[^>]*>(?:(?!</action>).)*"
                        + "<result name=\"eFormError\">/WEB-INF/jsp/email/emailComposeError.jsp</result>",
                Pattern.DOTALL));

        String page = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/email/emailComposeError.jsp"));
        assertThat(page).contains("<c:out value=\"${requestScope.errorMessage}\"/>",
                        "onclick=\"window.location.reload()\"",
                        "${pageContext.request.contextPath}/library/bootstrap/")
                .doesNotContain("efmshowform_data", "efmformrtl_templates", "param.fid", "param.fdid");
    }

    @Test
    void shouldTranslateRecoveryInstructions_inEveryShippedLocale() throws IOException {
        for (String locale : List.of("en", "fr", "es", "pl", "pt_BR")) {
            Properties bundle = new Properties();
            try (var reader = Files.newBufferedReader(
                    Path.of("src/main/resources/oscarResources_" + locale + ".properties"), StandardCharsets.UTF_8)) {
                bundle.load(reader);
            }
            for (String key : List.of("title", "saved", "retry")) {
                assertThat(bundle.getProperty("email.compose.error." + key)).as("%s: %s", locale, key).isNotBlank();
            }
        }
    }
}
