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
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailInlineImage;
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
    void shouldPreparePayloadAndMatchingAttachmentMetadata_fromSameSnapshot() throws Exception {
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
    void shouldRejectMissingApiKey_beforeProducingArtifact() {
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
    void shouldRejectPrivateEndpoint_beforeProducingArtifact() {
        EmailConfig emailConfig = validConfig();
        emailConfig.setConfigDetailsJson(
                "{\"api_key\":\"test-key\",\"end_point\":\"https://127.0.0.1/v3/mail/send\"}");
        APISendGridEmailSender sender = sender(emailConfig, List.of());

        assertThatThrownBy(sender::prepareArtifactBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("endpoint was rejected");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2})
    void shouldRejectOversizedAttachments_beforePreparingArtifact(int attachmentCount) throws Exception {
        Path source = tempDir.resolve("large.pdf");
        long bytes = attachmentCount == 1 ? 51L * 1024 * 1024 : 20L * 1024 * 1024;
        try (var file = new java.io.RandomAccessFile(source.toFile(), "rw")) {
            file.setLength(bytes);
        }
        var attachment = new EmailAttachment("large.pdf", source.toString(), DocumentType.DOC, 77);
        var sender = sender(validConfig(), java.util.Collections.nCopies(attachmentCount, attachment));
        assertThatThrownBy(sender::prepareArtifactBytes).isInstanceOf(EmailSendingException.class);
        assertThatThrownBy(sender::describePreparedAttachments).isInstanceOf(EmailSendingException.class);
    }

    @Test
    void shouldRejectOversizedJson_whenEscapingExpandsBody() {
        var sender = new APISendGridEmailSender(loggedInInfo, validConfig(),
                new String[]{"patient@example.test"}, "Subject", "\u0001".repeat(9 * 1024 * 1024), List.of());
        assertThatThrownBy(sender::prepareArtifactBytes).isInstanceOf(EmailSendingException.class);
        assertThatThrownBy(sender::describePreparedAttachments).isInstanceOf(EmailSendingException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "clinical.pdf, application/pdf", "notes.txt, text/plain", "scan.png, image/png",
            "data.unknown, application/octet-stream"})
    void shouldMatchAttachmentContentType_inPayloadAndArchive(String fileName, String expectedType) throws Exception {
        Path source = tempDir.resolve("attachment-source.bin");
        Files.writeString(source, "synthetic attachment");
        var attachment = new EmailAttachment(fileName, source.toString(), DocumentType.DOC, 77);
        var sender = sender(validConfig(), List.of(attachment));
        try {
            JsonNode payload = OBJECT_MAPPER.readTree(sender.prepareArtifactBytes());
            assertThat(payload.path("attachments").get(0).path("type").asText()).isEqualTo(expectedType);
            assertThat(sender.describePreparedAttachments()).singleElement()
                    .satisfies(metadata -> assertThat(metadata.getContentType()).isEqualTo(expectedType));
        } finally {
            sender.discardPrepared();
        }
    }

    @Test
    @DisplayName("should archive the SendGrid payload with the footer one blank line below the body")
    void shouldCarryFooter_inArchivedPayloadContent() throws Exception {
        EmailData emailData = new EmailData();
        emailData.setBody("Test body");
        emailData.setFooter("Riverside Clinic<br>Not monitored for urgent issues.");
        APISendGridEmailSender sender = new APISendGridEmailSender(loggedInInfo, validConfig(),
                new String[]{"patient@example.test"}, "Test subject", emailData.getTransmittedBody(), "", List.of());

        try {
            JsonNode payload = OBJECT_MAPPER.readTree(sender.prepareArtifactBytes());

            // No formatted version given: plain text only.
            assertThat(payload.path("content").size()).isEqualTo(1);
            JsonNode content = payload.path("content").get(0);
            assertThat(content.path("type").asText()).isEqualTo("text/plain");
            assertThat(content.path("value").asText())
                    .isEqualTo("Test body\n\nRiverside Clinic\nNot monitored for urgent issues.");
        } finally {
            sender.discardPrepared();
        }
    }

    @Test
    @DisplayName("should send text then HTML, and the clinic logo as an inline attachment named by its Content-ID")
    void shouldAddHtmlAndInlineLogo_whenFormattedVersionSet() throws Exception {
        EmailData emailData = new EmailData();
        emailData.setBody("Test body");
        emailData.setFooter("<b>Riverside Clinic</b>");
        byte[] logo = {(byte) 0xFF, (byte) 0xD8, 1, 2};
        emailData.setFooterLogo(new EmailInlineImage("clinic-logo-0123456789abcdef@carlos-emr", "image/jpeg", logo));
        APISendGridEmailSender sender = new APISendGridEmailSender(loggedInInfo, validConfig(),
                new String[]{"patient@example.test"}, "Test subject", emailData.getTransmittedBody(), "", List.of());
        sender.setFormattedVersion(emailData.getTransmittedHtml(), emailData.getFooterLogo());

        try {
            JsonNode payload = OBJECT_MAPPER.readTree(sender.prepareArtifactBytes());

            JsonNode content = payload.path("content");
            assertThat(content.size()).isEqualTo(2);
            assertThat(content.get(0).path("type").asText()).isEqualTo("text/plain");
            assertThat(content.get(0).path("value").asText()).isEqualTo("Test body\n\nRiverside Clinic");
            assertThat(content.get(1).path("type").asText()).isEqualTo("text/html");
            assertThat(content.get(1).path("value").asText())
                    .contains("<img src=\"cid:clinic-logo-0123456789abcdef@carlos-emr\"")
                    .contains("<b>Riverside Clinic</b>");
            JsonNode attachments = payload.path("attachments");
            assertThat(attachments.size()).isEqualTo(1);
            JsonNode inline = attachments.get(0);
            assertThat(inline.path("disposition").asText()).isEqualTo("inline");
            assertThat(inline.path("content_id").asText()).isEqualTo("clinic-logo-0123456789abcdef@carlos-emr");
            assertThat(inline.path("type").asText()).isEqualTo("image/jpeg");
            assertThat(inline.path("filename").asText()).isEqualTo("clinic-logo-0123456789abcdef.jpg");
            assertThat(Base64.getDecoder().decode(inline.path("content").asText())).isEqualTo(logo);
            // The logo is not one of the patient's documents: no archive attachment record.
            assertThat(sender.describePreparedAttachments()).isEmpty();
        } finally {
            sender.discardPrepared();
        }
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
