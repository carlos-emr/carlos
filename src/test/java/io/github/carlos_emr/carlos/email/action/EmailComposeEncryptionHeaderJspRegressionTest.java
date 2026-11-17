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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The compose page's Encryption header must be rendered from the same flag as its switch.
 *
 * <p>The page script corrects the ON/OFF label and hides the encryption options only when its
 * load handler reaches {@code showEncryptionOptions()}, and that handler returns early when the
 * page shows an error (a resend whose attachments failed) or a send result. On those pages an
 * unencrypted email showed "ON" beside a switch that was off, with the encrypted-message options
 * open. This source guard pins the server-side rendering; the switch and the "encryption is off"
 * warning already used the flag.</p>
 *
 * @since 2026-10-14
 */
@Tag("unit")
@Tag("email")
@DisplayName("Email compose Encryption header")
class EmailComposeEncryptionHeaderJspRegressionTest {

    private static String jsp() throws IOException {
        return Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp"), StandardCharsets.UTF_8);
    }

    private static String element(String jsp, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(jsp);
        assertThat(matcher.find()).as("element %s is on the page", regex).isTrue();
        return matcher.group();
    }

    @Test
    @DisplayName("should word and style the ON/OFF label from the email's encryption flag, not always ON")
    void shouldRenderLabelFromEncryptionFlag_forEveryPageState() throws IOException {
        String label = element(jsp(), "<label[^>]*id=\"isEncryption\"[^>]*>.*?</label>");

        assertThat(label)
                .contains("${ isEmailEncrypted ? emailComposeStateOn : emailComposeStateOff }")
                .contains("class=\"form-check-label${ isEmailEncrypted ? '' : ' off' }\"")
                .doesNotContain(">${emailComposeStateOn}</label>");
    }

    @Test
    @DisplayName("should hide the encryption options on the server when the email is not encrypted")
    void shouldHideEncryptionOptions_whenEmailNotEncrypted() throws IOException {
        String options = element(jsp(), "<div[^>]*id=\"encryptionOptions\"[^>]*>");

        assertThat(options).contains("${ isEmailEncrypted ? '' : 'd-none' }");
    }

    @Test
    @DisplayName("should keep the switch and the encryption-off warning on the same flag")
    void shouldKeepSwitchAndWarningOnSameFlag_forConsistency() throws IOException {
        String jsp = jsp();

        assertThat(element(jsp, "<input[^>]*id=\"encryptionSwitch\"[^>]*>")).contains("${ isEmailEncrypted ? 'checked' : '' }");
        assertThat(element(jsp, "<div[^>]*id=\"encryptionDisabledWarning\"[^>]*>")).contains("${ isEmailEncrypted ? 'd-none' : '' }");
    }
}
