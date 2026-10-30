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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The encrypted-message PDF of {@link EmailManager#sendEmail}: when the message cannot be converted
 * the email fails with a message of its own and nothing is sent, instead of a
 * {@code NullPointerException} from {@code toString()} on the missing path. Everything here is
 * synthetic.
 *
 * @since 2026-10-30
 */
@ExtendWith(MockitoExtension.class)
@Tag("email")
@Tag("manager")
@DisplayName("EmailManager encrypted message PDF")
class EmailManagerEncryptionUnitTest extends CarlosUnitTestBase {

    private static final String FAILURE_MESSAGE = "Encrypted message could not be converted to PDF";

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EmailConfigDaoImpl emailConfigDao;
    @Mock private EmailLogDaoImpl emailLogDao;
    @Mock private DemographicManager demographicManager;
    @Mock private ProviderManager2 providerManager;
    @Mock private LoggedInInfo loggedInInfo;
    @InjectMocks private EmailManager emailManager;

    @BeforeEach
    void stageOutbox() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(emailConfigDao.findActiveEmailConfigById(1)).thenReturn(new EmailConfig(null, null, "clinic@example.invalid"));
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(7311);
        when(demographicManager.getDemographic(loggedInInfo, Integer.valueOf(7311))).thenReturn(demographic);
        when(providerManager.getProvider(loggedInInfo, "999998")).thenReturn(new Provider());
    }

    private static EmailData encryptedEmail(String message) {
        EmailData data = new EmailData();
        data.setSenderConfigId(1);
        data.setRecipients(new String[] {"patient@example.invalid"});
        data.setSubject("FAKE-subject");
        data.setBody("FAKE-body");
        data.setIsEncrypted(true);
        data.setEncryptedMessage(message);
        data.setPassword("pass12345");
        data.setPasswordClue("FAKE-clue");
        data.setInternalComment("");
        data.setAdditionalParams("");
        data.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        data.setDemographicNo(7311);
        data.setProviderNo("999998");
        data.setAttachments(new ArrayList<>());
        return data;
    }

    @Test
    @DisplayName("should fail the email, and send nothing, when the message PDF cannot be built")
    void shouldFailEmail_whenMessagePdfIsNull() {
        EmailData data = encryptedEmail("FAKE-secret message");

        try (var edoc = mockStatic(ConvertToEdoc.class);
                var senders = mockConstruction(EmailSender.class)) {
            edoc.when(() -> ConvertToEdoc.saveAsTempPDF(any(EmailData.class))).thenReturn(null);

            EmailLog result = emailManager.sendEmail(loggedInInfo, data);

            assertThat(result.getStatus()).isEqualTo(EmailStatus.FAILED);
            assertThat(result.getErrorMessage()).isEqualTo(FAILURE_MESSAGE);
            assertThat(senders.constructed()).as("nothing is sent").isEmpty();
        }
        verify(emailLogDao).updateEmailStatus(isNull(), eq(EmailStatus.FAILED), eq(FAILURE_MESSAGE), any(Date.class));
        assertThat(data.getAttachments()).as("no half-built attachment is left on the email").isEmpty();
    }

    @Test
    @DisplayName("should store a fixed message that names neither the patient nor the text")
    void shouldStoreFixedMessage_whenMessagePdfIsNull() {
        EmailData data = encryptedEmail("FAKE-secret message");

        try (var edoc = mockStatic(ConvertToEdoc.class);
                var senders = mockConstruction(EmailSender.class)) {
            edoc.when(() -> ConvertToEdoc.saveAsTempPDF(any(EmailData.class))).thenReturn(null);

            EmailLog result = emailManager.sendEmail(loggedInInfo, data);

            assertThat(result.getErrorMessage()).doesNotContain("FAKE").doesNotContain("7311");
        }
    }

    @Test
    @DisplayName("should HTML-encode the message and keep its line breaks before converting it")
    void shouldEncodeMessage_beforeConversion() {
        EmailData data = encryptedEmail("Hello <b>FAKE</b>\nsecond line");
        List<String> converted = new ArrayList<>();

        try (var edoc = mockStatic(ConvertToEdoc.class);
                var senders = mockConstruction(EmailSender.class)) {
            edoc.when(() -> ConvertToEdoc.saveAsTempPDF(any(EmailData.class))).thenAnswer(invocation -> {
                converted.add(((EmailData) invocation.getArgument(0)).getEncryptedMessage());
                return null;
            });

            emailManager.sendEmail(loggedInInfo, data);
        }

        assertThat(converted).containsExactly("Hello &lt;b&gt;FAKE&lt;/b&gt;<br>second line");
    }
}
