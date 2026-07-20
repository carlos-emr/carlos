/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("APISendGridEmailSender")
@Tag("unit")
class APISendGridEmailSenderUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String API_KEY = "SG.secret-token";

    @TempDir
    private Path tempDir;

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    @Test
    @DisplayName("should prepare SendGrid payload without API key")
    void shouldPrepareSendGridPayload_withoutApiKey() throws Exception {
        Path attachmentPath = tempDir.resolve("attachment.pdf");
        Files.write(attachmentPath, "pdf-content".getBytes(StandardCharsets.UTF_8));
        EmailAttachment attachment = new EmailAttachment("attachment_001.pdf", attachmentPath.toString(), DocumentType.DOC, 77);
        APISendGridEmailSender sender = new APISendGridEmailSender(
                loggedInInfo,
                sendGridEmailConfig(),
                new String[]{"patient@example.test"},
                "Test subject",
                "Body text",
                null,
                List.of(attachment));

        byte[] payloadBytes = sender.preparePayloadBytes();

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        JsonNode payloadJson = OBJECT_MAPPER.readTree(payload);
        assertThat(payload).doesNotContain(API_KEY);
        assertThat(payloadJson.has("apiKey")).isFalse();
        assertThat(payloadJson.at("/personalizations/0/to/0/email").asText()).isEqualTo("patient@example.test");
        assertThat(payloadJson.at("/from/email").asText()).isEqualTo("provider@example.test");
        assertThat(payloadJson.at("/subject").asText()).isEqualTo("Test subject");
        assertThat(payloadJson.at("/attachments/0/filename").asText()).isEqualTo("attachment_001.pdf");
        assertThat(payloadJson.at("/attachments/0/content").asText()).isNotBlank();
    }

    @Test
    @DisplayName("should prepare SendGrid payload with null attachments")
    void shouldPrepareSendGridPayload_withNullAttachments() throws Exception {
        APISendGridEmailSender sender = new APISendGridEmailSender(
                loggedInInfo,
                sendGridEmailConfig(),
                new String[]{"patient@example.test"},
                "Test subject",
                "Body text",
                null,
                null);

        JsonNode payloadJson = OBJECT_MAPPER.readTree(sender.preparePayloadBytes());

        assertThat(payloadJson.get("attachments")).isEmpty();
    }

    @Test
    @DisplayName("should reject SendGrid payload when attachment path is missing")
    void shouldRejectSendGridPayload_whenAttachmentPathMissing() {
        EmailAttachment attachment = new EmailAttachment("attachment_001.pdf", null, DocumentType.DOC, 77);
        APISendGridEmailSender sender = new APISendGridEmailSender(
                loggedInInfo,
                sendGridEmailConfig(),
                new String[]{"patient@example.test"},
                "Test subject",
                "Body text",
                null,
                List.of(attachment));

        assertThatThrownBy(sender::preparePayloadBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("attachment path is required");
    }

    private EmailConfig sendGridEmailConfig() {
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.API, EmailConfig.EmailProvider.SENDGRID, "provider@example.test");
        emailConfig.setSenderFirstName("Provider");
        emailConfig.setSenderLastName("One");
        emailConfig.setConfigDetailsJson("{\"api_key\":\"" + API_KEY + "\",\"end_point\":\"https://api.sendgrid.test/v3/mail/send\"}");
        return emailConfig;
    }
}
