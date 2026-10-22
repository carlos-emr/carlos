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
package io.github.carlos_emr.carlos.login;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps both pages that write the acceptable-use agreement on its sanitized form.
 *
 * <p>The agreement is written unencoded because it is markup. Administration can publish it,
 * and the login page shows it before sign-in, so writing the raw file text there would let an
 * {@code _admin} account run script on the login page. The sanitizer itself is covered by
 * {@code UploadLoginText2ActionUnitTest}; this pins the pages to it.</p>
 *
 * @since 2026-10-22
 */
@DisplayName("Acceptable-use agreement JSP regressions")
@Tag("unit")
@Tag("security")
@Tag("login")
class AcceptableUseAgreementJspRegressionTest {

    private static final Path LOGIN_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/login/index.jsp");
    private static final Path UPLOAD_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/uploadEntryText.jsp");

    @Test
    @DisplayName("login page should show only the sanitized agreement and only while it is enabled")
    void shouldRenderSanitizedAgreement_onlyWhenAgreementIsEnabled() throws IOException {
        String loginJsp = Files.readString(LOGIN_JSP, StandardCharsets.UTF_8);

        assertThat(loginJsp)
                .contains("${ LoginResourceBean.acceptableUseAgreementManager.html }")
                .doesNotContain("acceptableUseAgreementManager.text");
        int agreementDiv = loginJsp.indexOf("<div id=\"auaText\"");
        assertThat(agreementDiv).isPositive();
        String beforeDiv = loginJsp.substring(0, agreementDiv);
        String guard = "<c:if test=\"${ LoginResourceBean.acceptableUseAgreementManager.auaAvailable }\">";
        assertThat(beforeDiv.lastIndexOf(guard))
                .as("the agreement block must sit inside the show_aua guard")
                .isGreaterThan(beforeDiv.lastIndexOf("</c:if>"));
    }

    @Test
    @DisplayName("upload page should preview the sanitized agreement and encode the stored date")
    void shouldPreviewSanitizedAgreement_withEncodedValidityDate() throws IOException {
        String uploadJsp = Files.readString(UPLOAD_JSP, StandardCharsets.UTF_8);

        assertThat(uploadJsp)
                .contains("AcceptableUseAgreementManager.getAUAHtml()")
                .doesNotContain("getAUAText()%>")
                .doesNotContain("<%=AcceptableUseAgreementManager.getAUAText()")
                .contains("value=\"<%=SafeEncode.forHtmlAttribute(fromDate)%>\"")
                .doesNotContain("value=\"<%=fromDate%>\"");
    }
}
