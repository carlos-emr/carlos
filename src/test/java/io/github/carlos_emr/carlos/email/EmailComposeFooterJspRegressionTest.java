/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.email.core.EmailData;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the compose screen's Footer box (issue #3981): its limit, its encoding, its help line and
 * the sender-switch prefill.
 *
 * @since 2026-09-29
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Email compose footer rendering")
class EmailComposeFooterJspRegressionTest {

    private static final Path EMAIL_COMPOSE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp");
    private static final List<String> LOCALES = List.of("en", "es", "fr", "pl", "pt_BR");

    @Test
    @DisplayName("should post the footer from a limited textarea filled through the CARLOS encoder")
    void shouldRenderFooterTextarea_withLimitAndEncodedValue() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        int textarea = jsp.indexOf("<textarea class=\"form-control\" name=\"footerEmail\" id=\"footerEmail\"");
        assertThat(textarea).isGreaterThanOrEqualTo(0);
        String element = jsp.substring(textarea, jsp.indexOf("</textarea>", textarea) + "</textarea>".length());
        assertThat(element)
                .contains("maxlength=\"" + EmailData.FOOTER_MAX_LENGTH + "\"")
                .contains("maxlength=\"2000\"")
                .endsWith("><carlos:encode value=\"${footerEmail}\"/></textarea>");
        assertThat(jsp)
                .doesNotContain(">${footerEmail}<")
                .doesNotContain("e:forHtmlContent value=\"${footerEmail}\"");
        // Inside the send form, after the message box.
        assertThat(textarea)
                .isGreaterThan(jsp.indexOf("name=\"message\" id=\"message\""))
                .isLessThan(jsp.indexOf("</form>"));
    }

    @Test
    @DisplayName("should show the help line from the bundle in every locale")
    void shouldShowFooterHelpLine_fromBundleKey() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<fmt:message key=\"email.compose.footer.help\" var=\"emailComposeFooterHelp\"/>")
                .contains("<fmt:message key=\"email.compose.footer.heading\" var=\"emailComposeFooterLabel\"/>")
                .contains("id=\"footerEmailHelp\"")
                .contains("${emailComposeFooterHelp}")
                .contains("aria-describedby=\"footerEmailHelp\"");
        String english = bundle("en").getProperty("email.compose.footer.help");
        assertThat(english).contains("plain text", "not saved to the chart", "Do not include patient information");
        for (String locale : LOCALES) {
            // New keys carry the English text in every locale until a translator supplies one.
            assertThat(bundle(locale).getProperty("email.compose.footer.help")).as(locale).isEqualTo(english);
            assertThat(bundle(locale).getProperty("email.compose.footer.heading")).as(locale).isEqualTo("Footer");
        }
    }

    @Test
    @DisplayName("should swap in the new account's default only while the footer follows the sender")
    void shouldApplySenderDefault_onlyWhileFooterFollowsSender() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("data-default-footer=\"${carlos:forHtmlAttribute(senderDefaultFooters[senderAccount.id])}\"")
                .contains("onchange=\"showAdditionalParamOption(); applySenderDefaultFooter()\"")
                .contains("data-follows-sender=\"${footerFollowsSender ? 'true' : 'false'}\"")
                .contains("oninput=\"this.dataset.followsSender = 'false'\"")
                .contains("footer.dataset.followsSender !== 'true'")
                .contains("footer.value = selectedSender.getAttribute('data-default-footer') || '';");
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
