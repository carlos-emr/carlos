/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("security")
@DisplayName("APISendGridEmailSender")
class APISendGridEmailSenderTest extends CarlosUnitTestBase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should not include API key in SendGrid JSON payload")
    void shouldNotIncludeApiKey_whenCreatingEmailPayload() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        APISendGridEmailSender sender = createSender(List.of());

        JsonNode payload = createEmailJson(sender);

        assertThat(payload.has("apiKey")).isFalse();
        assertThat(payload.at("/from/email").asText()).isEqualTo("clinic@example.com");
        assertThat(payload.at("/personalizations/0/to/0/email").asText()).isEqualTo("patient@example.com");
    }

    @Test
    @DisplayName("should treat null attachments as empty when creating SendGrid JSON payload")
    void shouldTreatNullAttachmentsAsEmpty_whenCreatingEmailPayload() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        APISendGridEmailSender sender = createSender(null);

        JsonNode payload = createEmailJson(sender);

        assertThat(payload.path("attachments")).isEmpty();
    }

    @Test
    @DisplayName("should encode attachments and snapshot mutable inputs in SendGrid JSON payload")
    void shouldEncodeAttachmentsAndSnapshotMutableInputs_whenCreatingEmailPayload() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        Path attachmentFile = tempDir.resolve("report.pdf");
        Files.writeString(attachmentFile, "pdf-content", StandardCharsets.UTF_8);
        String[] recipients = {"first@example.com"};
        List<EmailAttachment> attachments = new ArrayList<>();
        attachments.add(new EmailAttachment("report.pdf", attachmentFile.toString(), DocumentType.DOC, 5));
        APISendGridEmailSender sender = createSender(recipients, attachments);

        recipients[0] = "changed@example.com";
        attachments.clear();

        JsonNode payload = createEmailJson(sender);

        assertThat(payload.at("/personalizations/0/to/0/email").asText()).isEqualTo("first@example.com");
        assertThat(payload.at("/attachments/0/filename").asText()).isEqualTo("report.pdf");
        assertThat(payload.at("/attachments/0/type").asText()).isEqualTo("application/pdf");
        assertThat(payload.at("/attachments/0/disposition").asText()).isEqualTo("attachment");
        assertThat(payload.at("/attachments/0/content").asText()).isEqualTo(
                Base64.getEncoder().encodeToString("pdf-content".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("should reject send when user is missing email privilege")
    void shouldRejectSend_whenUserMissingEmailPrivilege() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        APISendGridEmailSender sender = createSender(List.of());

        assertThatThrownBy(sender::send)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("missing required sec object (_email)");
    }

    @Test
    @DisplayName("should fail before network call when SendGrid config JSON is invalid")
    void shouldFailBeforeNetworkCall_whenConfigJsonInvalid() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(null, "_email", SecurityInfoManager.WRITE, null))
                .thenReturn(true);
        APISendGridEmailSender sender = createSender(
                new String[]{"patient@example.com"},
                List.of(),
                "{invalid-json");

        assertThatThrownBy(sender::send)
                .isInstanceOf(EmailSendingException.class)
                .hasMessage("Invalid credentials configured for clinic@example.com");
    }

    @Test
    @DisplayName("should support short constructor with null recipients and attachments")
    void shouldSupportShortConstructor_withNullRecipientsAndAttachments() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        APISendGridEmailSender sender = new APISendGridEmailSender(
                null,
                createEmailConfig(defaultConfigDetailsJson()),
                null,
                "Subject",
                "Body",
                null);

        JsonNode payload = createEmailJson(sender);

        assertThat(payload.at("/personalizations/0/to")).isEmpty();
        assertThat(payload.path("attachments")).isEmpty();
    }

    @Test
    @DisplayName("should fail payload creation when attachment file cannot be read")
    void shouldFailPayloadCreation_whenAttachmentFileCannotBeRead() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        APISendGridEmailSender sender = createSender(List.of(
                new EmailAttachment("missing.pdf", tempDir.resolve("missing.pdf").toString(), DocumentType.DOC, 5)));

        assertThatThrownBy(() -> createEmailJson(sender))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(EmailSendingException.class);
    }

    @Test
    @DisplayName("should read API key and default endpoint from SendGrid config")
    void shouldReadApiKeyAndDefaultEndpoint_fromSendGridConfig() throws Exception {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        APISendGridEmailSender sender = createSender(
                new String[]{"patient@example.com"},
                List.of(),
                """
                        {
                            "api_key": "test-api-key"
                        }
                        """);

        assertThat(invokeStringMethod(sender, "getAPIKey")).isEqualTo("test-api-key");
        assertThat(invokeStringMethod(sender, "getEndPoint"))
                .isEqualTo("https://api.sendgrid.com/v3/mail/send");
    }

    private static APISendGridEmailSender createSender(List<EmailAttachment> attachments) {
        return createSender(new String[]{"patient@example.com"}, attachments);
    }

    private static APISendGridEmailSender createSender(String[] recipients, List<EmailAttachment> attachments) {
        return createSender(recipients, attachments, defaultConfigDetailsJson());
    }

    private static String defaultConfigDetailsJson() {
        return """
                {
                    "api_key": "not-a-real-test-key",
                    "end_point": "https://api.sendgrid.test/v3/mail/send"
                }
                """;
    }

    private static APISendGridEmailSender createSender(
            String[] recipients,
            List<EmailAttachment> attachments,
            String configDetailsJson
    ) {
        return new APISendGridEmailSender(
                null,
                createEmailConfig(configDetailsJson),
                recipients,
                "Subject",
                "Body",
                "",
                attachments);
    }

    private static JsonNode createEmailJson(APISendGridEmailSender sender) throws Exception {
        Method createEmailJSON = APISendGridEmailSender.class.getDeclaredMethod("createEmailJSON");
        createEmailJSON.setAccessible(true);
        return OBJECT_MAPPER.readTree((String) createEmailJSON.invoke(sender));
    }

    private static String invokeStringMethod(APISendGridEmailSender sender, String methodName) throws Exception {
        Method method = APISendGridEmailSender.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (String) method.invoke(sender);
    }

    private static EmailConfig createEmailConfig(String configDetailsJson) {
        EmailConfig emailConfig = new EmailConfig(EmailType.API, EmailProvider.SENDGRID, "clinic@example.com");
        emailConfig.setSenderFirstName("Clinic");
        emailConfig.setSenderLastName("Team");
        emailConfig.setConfigDetailsJson(configDetailsJson);
        return emailConfig;
    }
}
