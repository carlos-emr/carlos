/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("security")
@DisplayName("APISendGridEmailSender")
class APISendGridEmailSenderTest extends CarlosUnitTestBase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    private static APISendGridEmailSender createSender(List<EmailAttachment> attachments) {
        EmailConfig emailConfig = new EmailConfig(EmailType.API, EmailProvider.SENDGRID, "clinic@example.com");
        emailConfig.setSenderFirstName("Clinic");
        emailConfig.setSenderLastName("Team");
        emailConfig.setConfigDetailsJson("""
                {
                    "api_key": "not-a-real-test-key",
                    "end_point": "https://api.sendgrid.test/v3/mail/send"
                }
                """);
        return new APISendGridEmailSender(
                null,
                emailConfig,
                new String[]{"patient@example.com"},
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
}
