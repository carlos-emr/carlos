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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @Test
    @DisplayName("should show the user's footers encoded and post every change to the save action")
    void shouldEncodeFootersAndPostChanges_onMyFooterPage() throws IOException {
        String jsp = Files.readString(MY_FOOTER_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("aria-describedby=\"myFooterHelp\"><carlos:encode value=\"${myFooter}\"/></textarea>")
                .contains("<carlos:encode value=\"${clinicChangeNotice}\"/>")
                .contains("<carlos:encode value=\"${clinicFooter}\"/>")
                .contains("maxlength=\"2000\"")
                .doesNotContain("${myFooter}<")
                .doesNotContain(">${clinicFooter}")
                .doesNotContain(">${clinicChangeNotice}");
        assertThat(jsp.split("<form action=\"\\$\\{ctx}/email/saveMyEmailFooter\" method=\"post\"", -1))
                .as("both forms post to the save action").hasSize(3);
        for (String footerAction : new String[] {"save", "useClinicDefault", "restorePrevious", "keepCurrent"}) {
            assertThat(jsp).contains("name=\"footerAction\" value=\"" + footerAction + "\"");
        }
    }

    @Test
    @DisplayName("should let only _admin writers post the clinic footer, shown encoded either way")
    void shouldGateAndEncodeClinicFooter_onConfigureEmailPage() throws IOException {
        String jsp = Files.readString(CONFIGURE_EMAIL_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<form action=\"${ctx}/admin/saveClinicEmailFooter\" method=\"post\">")
                .contains("aria-describedby=\"clinicFooterHelp\"><carlos:encode value=\"${clinicFooter}\"/></textarea>")
                .contains("maxlength=\"2000\"")
                .doesNotContain(">${clinicFooter}");
        int form = jsp.indexOf("/admin/saveClinicEmailFooter");
        int writeGate = jsp.lastIndexOf("objectName=\"_admin\" rights=\"w\" reverse=\"<%=false%>\"", form);
        assertThat(writeGate).as("the form sits inside an _admin write check").isPositive();
    }

    @Test
    @DisplayName("should link the footer page from Preferences")
    void shouldLinkMyFooter_fromPreferences() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/provider/providerpreference.jsp"),
                StandardCharsets.UTF_8);

        assertThat(jsp).contains("href=\"${pageContext.request.contextPath}/email/myEmailFooter\"")
                .contains("provider.providerpreference.link.myEmailFooter");
    }
}
