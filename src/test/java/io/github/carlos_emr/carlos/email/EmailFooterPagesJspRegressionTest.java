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
package io.github.carlos_emr.carlos.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source checks for the email footer pages (follow-up to #3981): the user's own footer page and
 * the clinic footer section of Configure Email. Stored footers are shown encoded, and every change
 * is a POST form to its save action, so CSRFGuard adds its token.
 *
 * @since 2026-10-07
 */
@DisplayName("Email footer pages")
@Tag("unit")
@Tag("fast")
@Tag("email")
class EmailFooterPagesJspRegressionTest {

    private static final Path MY_FOOTER_JSP = Path.of("src/main/webapp/WEB-INF/jsp/email/myEmailFooter.jsp");
    private static final Path CONFIGURE_EMAIL_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/configureEmail.jsp");
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pl", "pt_BR");
    private static final Pattern FOOTER_KEY = Pattern.compile(
            "<fmt:message key=\"((?:admin\\.configureEmail\\.footer|email\\.myFooter|email\\.footer|email\\.compose\\.footer)\\.[A-Za-z]+)\"");

    @Test
    @DisplayName("should show the user's footers encoded and post every change to the save action")
    void shouldEncodeFootersAndPostChanges_onMyFooterPage() throws IOException {
        String jsp = Files.readString(MY_FOOTER_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("aria-describedby=\"myFooterEmptyHelp myFooterHelp\"><carlos:encode value=\"${myFooter}\"/></textarea>")
                // A blank footer means the clinic footer (maintainer decision, 8 Oct), and the page says so.
                .contains("<div id=\"myFooterEmptyHelp\" class=\"form-text\"><fmt:message key=\"email.myFooter.emptyUsesClinic\"/></div>")
                .contains("<carlos:encode value=\"${clinicChangeNotice}\"/>")
                .contains("<carlos:encode value=\"${clinicFooter}\"/>")
                .contains("maxlength=\"<%= EmailData.FOOTER_MAX_LENGTH %>\"")
                .contains("<%@ page import=\"io.github.carlos_emr.carlos.email.core.EmailData\" %>")
                .contains("<fmt:message key=\"email.footer.saveConflict\"/>")
                .doesNotContain("${myFooter}<")
                .doesNotContain(">${clinicFooter}")
                .doesNotContain(">${clinicChangeNotice}");
        assertThat(jsp.split("<form action=\"\\$\\{ctx}/email/saveMyEmailFooter\" method=\"post\"", -1))
                .as("both forms post to the save action").hasSize(3);
        for (String footerAction : new String[] {"save", "useClinicDefault", "restorePrevious", "keepCurrent"}) {
            assertThat(jsp).contains("name=\"footerAction\" value=\"" + footerAction + "\"");
        }
        // With no previous footer there is nothing to put back, so the button is not offered.
        int restore = jsp.indexOf("value=\"restorePrevious\"");
        int guard = jsp.lastIndexOf("<c:if test=\"${not empty clinicChangeNotice}\">", restore);
        assertThat(guard).as("the restore button sits inside a non-empty-notice check").isPositive();
        assertThat(jsp.indexOf("</c:if>", guard)).as("which closes after the button").isGreaterThan(restore);
    }

    @Test
    @DisplayName("should let only _admin writers post the clinic footer, shown encoded either way")
    void shouldGateAndEncodeClinicFooter_onConfigureEmailPage() throws IOException {
        String jsp = Files.readString(CONFIGURE_EMAIL_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<form action=\"${ctx}/admin/saveClinicEmailFooter\" method=\"post\">")
                .contains("aria-describedby=\"clinicFooterHelp\"><carlos:encode value=\"${clinicFooter}\"/></textarea>")
                .contains("maxlength=\"<%= EmailData.FOOTER_MAX_LENGTH %>\"")
                .contains("<%@ page import=\"io.github.carlos_emr.carlos.email.core.EmailData\" %>")
                .contains("<carlos:encode value=\"${clinicFooterCurrent}\"/>")
                .doesNotContain(">${clinicFooter}")
                .doesNotContain(">${clinicFooterCurrent}");
        int form = jsp.indexOf("<form action=\"${ctx}/admin/saveClinicEmailFooter\"");
        int formEnd = jsp.indexOf("</form>", form);
        int writeGate = jsp.lastIndexOf("objectName=\"_admin\" rights=\"w\" reverse=\"<%=false%>\"", form);
        assertThat(writeGate).as("the form opens inside an _admin write check").isPositive();
        assertThat(jsp.indexOf("</security:oscarSec>", writeGate)).as("and closes before the check ends")
                .isGreaterThan(formEnd);
        // The form sends back the fingerprint of the footer it showed, so a stale page cannot overwrite.
        assertThat(jsp.substring(form, formEnd)).contains(
                "<input type=\"hidden\" name=\"clinicFooterFingerprint\" value=\"${carlos:forHtmlAttribute(clinicFooterFingerprint)}\"/>");
    }

    @Test
    @DisplayName("should word the clinic footer section by the clinic-change rule and show every save outcome")
    void shouldShowIntroAndSaveOutcomes_forClinicFooterRule() throws IOException {
        String jsp = Files.readString(CONFIGURE_EMAIL_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<c:when test=\"${ownFootersReplacedOnClinicChange}\"><fmt:message key=\"admin.configureEmail.footer.intro\"/></c:when>")
                .contains("<c:otherwise><fmt:message key=\"admin.configureEmail.footer.introKeep\"/></c:otherwise>")
                .contains("<fmt:message key=\"admin.configureEmail.footer.changedSinceShown\"/>")
                .contains("<fmt:message key=\"email.footer.saveConflict\"/>")
                .contains("<fmt:message key=\"admin.configureEmail.footer.tooLong\"/>")
                .contains("<fmt:message key=\"admin.configureEmail.footer.unchanged\"/>");
    }

    @Test
    @DisplayName("should link the footer page from Preferences")
    void shouldLinkMyFooter_fromPreferences() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/provider/providerpreference.jsp"),
                StandardCharsets.UTF_8);

        assertThat(jsp).contains("href=\"${pageContext.request.contextPath}/email/myEmailFooter\"")
                .contains("provider.providerpreference.link.myEmailFooter");
        int link = jsp.indexOf("/email/myEmailFooter");
        int gate = jsp.lastIndexOf("objectName=\"_email\" rights=\"w\" reverse=\"<%=false%>\"", link);
        assertThat(gate).as("the link is shown only to users who can send patient email").isPositive();
        assertThat(jsp.indexOf("</security:oscarSec>", gate)).isGreaterThan(link);
    }

    @Test
    @DisplayName("should translate every footer message on both pages in every locale")
    void shouldTranslateFooterPageKeys_inEveryLocale() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Path page : List.of(MY_FOOTER_JSP, CONFIGURE_EMAIL_JSP)) {
            Matcher matcher = FOOTER_KEY.matcher(Files.readString(page, StandardCharsets.UTF_8));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        assertThat(keys).contains("admin.configureEmail.footer.intro", "admin.configureEmail.footer.introKeep",
                "admin.configureEmail.footer.changedSinceShown", "email.footer.saveConflict");

        Properties english = bundle("en");
        for (String key : keys) {
            assertThat(english.getProperty(key)).as(key).isNotBlank();
            for (String locale : LOCALES.subList(1, LOCALES.size())) {
                // Each locale carries its own translation, not the English text.
                assertThat(bundle(locale).getProperty(key)).as(locale + " " + key)
                        .isNotBlank().isNotEqualTo(english.getProperty(key));
            }
        }
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
