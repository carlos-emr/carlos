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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailFieldLengthException;
import io.github.carlos_emr.carlos.email.core.EmailFieldLengthValidator;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@code emailLog} length guard in {@link EmailManager#sendEmail}: an
 * over-length field must be rejected before anything is persisted or sent, and fields that
 * {@code EmailManager} clears before storage must not be reported (issue #3905).
 *
 * @since 2026-09-24
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("manager")
@DisplayName("EmailManager field length guard")
class EmailManagerFieldLengthUnitTest {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EmailConfigDaoImpl emailConfigDao;
    @Mock private EmailLogDaoImpl emailLogDao;
    @Mock private LoggedInInfo loggedInInfo;
    @InjectMocks private EmailManager emailManager;

    @BeforeEach
    void authorize() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    private static EmailData emailData() {
        EmailData data = new EmailData();
        data.setSenderConfigId(1);
        data.setSubject("Subject");
        data.setBody("Body");
        data.setEncryptedMessage("");
        data.setPassword("");
        data.setPasswordClue("");
        data.setInternalComment("");
        data.setAdditionalParams("");
        data.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        data.setAttachments(Collections.emptyList());
        return data;
    }

    @Test
    @DisplayName("should reject an over-length subject before persisting the email log")
    void shouldThrowBeforePersisting_whenSubjectTooLong() {
        EmailData data = emailData();
        data.setSubject("s".repeat(EmailFieldLengthValidator.MAX_SUBJECT_CHARS + 1));

        assertThatThrownBy(() -> emailManager.sendEmail(loggedInInfo, data))
                .isInstanceOf(EmailFieldLengthException.class)
                .satisfies(e -> assertThat(((EmailFieldLengthException) e).getViolations())
                        .extracting("messageKey")
                        .containsExactly("email.compose.msg.subjectTooLong"));
        verifyNoInteractions(emailConfigDao, emailLogDao);
    }

    @Test
    @DisplayName("should reject an over-length body without truncating it")
    void shouldThrowWithoutTruncating_whenBodyTooLong() {
        EmailData data = emailData();
        String body = "b".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES + 1);
        data.setBody(body);

        assertThatThrownBy(() -> emailManager.sendEmail(loggedInInfo, data))
                .isInstanceOf(EmailFieldLengthException.class);
        assertThat(data.getBody()).isEqualTo(body);
        verifyNoInteractions(emailLogDao);
    }

    @Test
    @DisplayName("should ignore an over-length password when encryption is not used")
    void shouldNotRejectPassword_whenEncryptionFieldsAreCleared() {
        EmailData data = emailData();
        // No encrypted message and no attachments: sanitizeEmailFields clears the password, so it
        // is never stored and must not block the send. The send then stops at the missing config.
        data.setPassword("p".repeat(EmailFieldLengthValidator.MAX_PASSWORD_CHARS + 1));

        assertThatThrownBy(() -> emailManager.sendEmail(loggedInInfo, data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active email configuration");
        assertThat(data.getPassword()).isEmpty();
    }

    @Test
    @DisplayName("should strip line breaks from a subject that did not come through the eForm setup")
    void shouldStripLineBreaks_whenSubjectComesFromDirectCompose() {
        EmailData data = emailData();
        // The direct compose POST hands the raw request value to the manager.
        data.setSubject("Results\r\nBcc: attacker@example.com\u2028x");

        assertThatThrownBy(() -> emailManager.sendEmail(loggedInInfo, data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active email configuration");
        assertThat(data.getSubject()).isEqualTo("ResultsBcc: attacker@example.comx");
    }

    @Test
    @DisplayName("should strip control characters from a direct-compose password and clue before checking them")
    void shouldStripControlCharacters_whenPasswordComesFromDirectCompose() {
        EmailData data = emailData();
        data.setIsEncrypted(true);
        data.setEncryptedMessage("Secret");
        // 50 visible characters plus line breaks and a tab: fits once sanitized, as on the eForm path.
        data.setPassword("p".repeat(EmailFieldLengthValidator.MAX_PASSWORD_CHARS - 2) + "\r\n" + "pp\t");
        data.setPasswordClue("Your date\r\nof birth");

        assertThatThrownBy(() -> emailManager.sendEmail(loggedInInfo, data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active email configuration");
        assertThat(data.getPassword()).isEqualTo("p".repeat(EmailFieldLengthValidator.MAX_PASSWORD_CHARS));
        assertThat(data.getPasswordClue()).isEqualTo("Your dateof birth");
    }

    @Test
    @DisplayName("should clear an encrypted message instead of rejecting it when encryption is off")
    void shouldClearEncryptedMessage_whenEncryptionIsOff() {
        EmailData data = emailData();
        data.setIsEncrypted(false);
        // Typed before encryption was switched off: never sent, so it is neither stored nor checked.
        data.setEncryptedMessage("e".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES + 1));
        data.setPassword("p".repeat(EmailFieldLengthValidator.MAX_PASSWORD_CHARS + 1));

        assertThatThrownBy(() -> emailManager.sendEmail(loggedInInfo, data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active email configuration");
        assertThat(data.getEncryptedMessage()).isEmpty();
        assertThat(data.getPassword()).isEmpty();
        assertThat(data.getPasswordClue()).isEmpty();
    }

    @Test
    @DisplayName("should still reject an over-length encrypted message when encryption is on")
    void shouldRejectEncryptedMessage_whenEncryptionIsOn() {
        EmailData data = emailData();
        data.setIsEncrypted(true);
        data.setPassword("pass12345");
        data.setEncryptedMessage("e".repeat(EmailFieldLengthValidator.MAX_BLOB_TEXT_UTF8_BYTES + 1));

        assertThatThrownBy(() -> emailManager.sendEmail(loggedInInfo, data))
                .isInstanceOf(EmailFieldLengthException.class)
                .satisfies(e -> assertThat(((EmailFieldLengthException) e).getViolations())
                        .extracting("messageKey")
                        .containsExactly("email.compose.msg.encryptedMessageTooLong"));
        verifyNoInteractions(emailLogDao);
    }
}
