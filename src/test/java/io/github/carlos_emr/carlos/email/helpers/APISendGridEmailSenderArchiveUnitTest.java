/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("APISendGridEmailSender outbound archive")
@Tag("unit")
@Tag("fast")
class APISendGridEmailSenderArchiveUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    @Test
    @DisplayName("should prepare SendGrid JSON and matching attachment metadata from one byte snapshot")
    void shouldPreparePayloadAndMatchingAttachmentMetadata() throws Exception {
        byte[] attachmentBytes = "clinical-pdf-content".getBytes(StandardCharsets.UTF_8);
        Path attachmentPath = tempDir.resolve("clinical.pdf");
        Files.write(attachmentPath, attachmentBytes);
        EmailAttachment attachment = new EmailAttachment(
                "clinical.pdf", attachmentPath.toString(), DocumentType.DOC, 77);
        APISendGridEmailSender sender = sender(validConfig(), List.of(attachment));

        byte[] artifactBytes = sender.prepareArtifactBytes();

        JsonNode payload = OBJECT_MAPPER.readTree(artifactBytes);
        JsonNode payloadAttachment = payload.path("attachments").get(0);
        assertThat(Base64.getDecoder().decode(payloadAttachment.path("content").asText()))
                .containsExactly(attachmentBytes);
        assertThat(payloadAttachment.path("filename").asText()).isEqualTo("clinical.pdf");
        assertThat(sender.getArchiveContentType()).isEqualTo("application/json");
        assertThat(sender.getArchiveArtifactType())
                .isEqualTo(OutboundEmailArchive.ARTIFACT_TYPE_API_PAYLOAD);
        assertThat(sender.getArchiveFileName(new EmailLog())).endsWith("-sendgrid.json");

        assertThat(sender.describePreparedAttachments()).singleElement().satisfies(metadata -> {
            assertThat(metadata.getFileName()).isEqualTo("clinical.pdf");
            assertThat(metadata.getContentType()).isEqualTo("application/pdf");
            assertThat(metadata.getByteSize()).isEqualTo((long) attachmentBytes.length);
            assertThat(metadata.getSha256Hash()).isEqualTo(sha256Hex(attachmentBytes));
            assertThat(metadata.getSourceDocumentType()).isEqualTo("DOC");
            assertThat(metadata.getSourceDocumentId()).isEqualTo(77);
        });
        assertThatThrownBy(sender::prepareArtifactBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("already been prepared");

        sender.discardPrepared();
        assertThatThrownBy(sender::describePreparedAttachments)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("must be prepared");
    }

    @Test
    @DisplayName("should reject missing API key before producing an archive artifact")
    void shouldRejectMissingApiKeyBeforeProducingArtifact() {
        EmailConfig emailConfig = validConfig();
        emailConfig.setConfigDetailsJson("{\"end_point\":\"https://203.0.113.10/v3/mail/send\"}");
        APISendGridEmailSender sender = sender(emailConfig, List.of());

        assertThatThrownBy(sender::prepareArtifactBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessage("Invalid credentials configured for provider@example.test");
        assertThatThrownBy(sender::describePreparedAttachments)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("must be prepared");
    }

    @Test
    @DisplayName("should reject a private endpoint before producing an archive artifact")
    void shouldRejectPrivateEndpointBeforeProducingArtifact() {
        EmailConfig emailConfig = validConfig();
        emailConfig.setConfigDetailsJson(
                "{\"api_key\":\"test-key\",\"end_point\":\"https://127.0.0.1/v3/mail/send\"}");
        APISendGridEmailSender sender = sender(emailConfig, List.of());

        assertThatThrownBy(sender::prepareArtifactBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("endpoint was rejected");
    }

    private APISendGridEmailSender sender(EmailConfig emailConfig, List<EmailAttachment> attachments) {
        return new APISendGridEmailSender(
                loggedInInfo,
                emailConfig,
                new String[]{"patient@example.test"},
                "Test subject",
                "Test body",
                "",
                attachments);
    }

    private EmailConfig validConfig() {
        EmailConfig emailConfig = new EmailConfig(
                EmailConfig.EmailType.API,
                EmailConfig.EmailProvider.SENDGRID,
                "provider@example.test");
        emailConfig.setSenderFirstName("Provider");
        emailConfig.setSenderLastName("One");
        emailConfig.setConfigDetailsJson(
                "{\"api_key\":\"test-key\",\"end_point\":\"https://203.0.113.10/v3/mail/send\"}");
        return emailConfig;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
