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
package io.github.carlos_emr.carlos.email.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Unit tests for {@link APISendGridEmailSender} focused on the request payload.
 *
 * <p>Verifies the leak-channel fix from issue #3112: the SendGrid API key must travel only in the
 * {@code Authorization: Bearer} header and must never appear in the serialized request body.</p>
 */
class APISendGridEmailSenderUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerSecurityManager() {
        createAndRegisterMock(SecurityInfoManager.class);
    }

    @Test
    @Tag("read")
    @DisplayName("should not embed the API key in the SendGrid request body")
    void shouldNotEmbedApiKey_inRequestBody() throws Exception {
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderFirstName("Clinic");
        emailConfig.setSenderLastName("Sender");
        emailConfig.setSenderEmail("clinic@example.com");
        emailConfig.setConfigDetailsJson("{\"api_key\":\"SG.super-secret-key\"}");

        APISendGridEmailSender sender = new APISendGridEmailSender(
                null, emailConfig, new String[] {"patient@example.com"},
                "Subject line", "Body text", Collections.emptyList());

        String payload = sender.createEmailJSON();

        // The body must carry the message but neither the "apiKey" body field nor the key value.
        assertThat(payload).doesNotContain("apiKey");
        assertThat(payload).doesNotContain("SG.super-secret-key");
        assertThat(payload).contains("patient@example.com");
        assertThat(payload).contains("Subject line");
    }
}
