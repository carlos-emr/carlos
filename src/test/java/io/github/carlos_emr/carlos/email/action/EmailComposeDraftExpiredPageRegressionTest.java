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
package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the page an eForm email window shows when its draft is gone (expired, reused or unknown key):
 * mapped to its own result, worded from the bundle in every shipped language, and showing nothing
 * from the request, so no patient, eForm or key can reach it.
 *
 * @since 2026-10-08
 */
@DisplayName("eForm email draft-expired page")
@Tag("unit")
@Tag("email")
class EmailComposeDraftExpiredPageRegressionTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/email/emailComposeDraftExpired.jsp");
    private static final Path STRUTS_PROVIDER = Path.of("src/main/webapp/WEB-INF/classes/struts-provider.xml");
    private static final List<String> KEYS =
            List.of("email.compose.draftExpired.title", "email.compose.draftExpired.message");
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pl", "pt_BR");

    @Test
    @DisplayName("compose action should map the draft-expired result to its own page, not the eForm error page")
    void shouldMapDraftExpiredResult_toItsOwnPage() throws IOException {
        String struts = Files.readString(STRUTS_PROVIDER, StandardCharsets.UTF_8);

        assertThat(struts).containsPattern(Pattern.compile(
                "<action name=\"email/emailComposeAction\"[^>]*>(?:(?!</action>).)*"
                        + "<result name=\"" + EmailCompose2Action.DRAFT_EXPIRED_RESULT + "\">"
                        + Pattern.quote("/WEB-INF/jsp/email/emailComposeDraftExpired.jsp") + "</result>",
                Pattern.DOTALL));
    }

    @Test
    @DisplayName("page should show only bundle text and nothing taken from the request")
    void shouldShowOnlyBundleText_andNothingFromRequest() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8);

        assertThat(page)
                .contains("<fmt:message key=\"email.compose.draftExpired.title\"/>")
                .contains("<fmt:message key=\"email.compose.draftExpired.message\"/>")
                .contains("<fmt:message key=\"global.btnClose\"/>");
        // Only the context path and the reader's language come from the request.
        Matcher expression = Pattern.compile("\\$\\{[^}]*}").matcher(page);
        while (expression.find()) {
            assertThat(expression.group()).isIn("${pageContext.request.contextPath}",
                    "${pageContext.request.locale.language}");
        }
        // No scriptlet, expression or other tag library could print a request value either: the only
        // JSP elements are directives and comments, and the only tag library is fmt.
        Matcher element = Pattern.compile("<%[^@-]").matcher(page);
        assertThat(element.find()).as("scriptlet or expression in the page").isFalse();
        Matcher taglib = Pattern.compile("<%@\\s*taglib\\b[^%]*%>").matcher(page);
        List<String> taglibs = new ArrayList<>();
        while (taglib.find()) {
            taglibs.add(taglib.group().replaceAll("\\s+", " "));
        }
        assertThat(taglibs).containsExactly("<%@ taglib uri=\"jakarta.tags.fmt\" prefix=\"fmt\" %>");
        assertThat(page).doesNotContain("errorMessage");
    }

    @Test
    @DisplayName("draft-expired text should be translated in every shipped locale, with no placeholder")
    void shouldTranslateDraftExpiredText_inEveryLocale() throws IOException {
        Properties english = loadBundle("en");
        assertThat(english.getProperty("email.compose.draftExpired.message"))
                .isEqualTo("This email draft has expired. Close this window and open the email again from the eForm.");
        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            for (String key : KEYS) {
                String text = bundle.getProperty(key);
                assertThat(text).as("oscarResources_%s.properties defines %s", locale, key).isNotBlank();
                // Static text: nothing about the window's patient, eForm or draft can be filled in.
                assertThat(text).as("%s %s has no placeholder", locale, key).doesNotContain("{");
                if (!"en".equals(locale)) {
                    assertThat(text).as("oscarResources_%s.properties translates %s", locale, key)
                            .isNotEqualTo(english.getProperty(key));
                }
            }
        }
    }

    private static Properties loadBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        Path bundlePath = Path.of("src/main/resources/oscarResources_" + locale + ".properties");
        try (var reader = Files.newBufferedReader(bundlePath, StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        return bundle;
    }
}
