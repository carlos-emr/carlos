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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.email.core.EmailConfigSecrets;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;

@DisplayName("EmailManager credential migration")
@Tag("unit")
@Tag("fast")
@Tag("security")
class EmailManagerUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EmailConfigDaoImpl emailConfigDao;
    private EmailManager emailManager;
    private String originalKey;

    @BeforeEach
    void setUp() throws Exception {
        originalKey = EncryptionKeyTestSupport.seedFreshKey();
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailManager = new EmailManager();
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
    }

    @AfterEach
    void restoreEncryptionKey() {
        EncryptionKeyTestSupport.restoreKey(originalKey);
    }

    @Test
    @Tag("update")
    @DisplayName("should encrypt and persist plaintext transport credentials on first use")
    void shouldPersistEncryptedCredentials_whenConfigIsPlaintext() throws Exception {
        EmailConfig config = config("{\"host\":\"smtp.example.com\",\"password\":\"smtp-secret\"}");

        emailManager.upgradeConfigCredentialsAtRest(config);

        verify(emailConfigDao).merge(same(config));
        assertPasswordDecrypts(config, "smtp-secret");
    }

    @Test
    @Tag("read")
    @DisplayName("should not rewrite a configuration whose credentials are already encrypted")
    void shouldNotPersistConfig_whenCredentialAlreadyEncrypted() throws Exception {
        EmailConfig config = config(EmailConfigSecrets.encryptSecrets("{\"password\":\"smtp-secret\"}"));

        emailManager.upgradeConfigCredentialsAtRest(config);

        verify(emailConfigDao, never()).merge(config);
        assertPasswordDecrypts(config, "smtp-secret");
    }

    @Test
    @Tag("update")
    @DisplayName("should restore the usable plaintext value when persistence fails")
    void shouldRestoreOriginalConfig_whenPersistenceFails() {
        String original = "{\"password\":\"smtp-secret\"}";
        EmailConfig config = config(original);
        doThrow(new RuntimeException("database unavailable")).when(emailConfigDao).merge(config);

        emailManager.upgradeConfigCredentialsAtRest(config);

        assertThat(config.getConfigDetailsJson()).isEqualTo(original);
    }

    private EmailConfig config(String details) {
        EmailConfig config = new EmailConfig();
        config.setConfigDetailsJson(details);
        return config;
    }

    private void assertPasswordDecrypts(EmailConfig config, String plaintext) throws Exception {
        JsonNode password = MAPPER.readTree(config.getConfigDetailsJson()).get("password");
        assertThat(password.asText()).startsWith("{ENC}");
        assertThat(EmailConfigSecrets.decryptSecret(password.asText())).isEqualTo(plaintext);
    }
}
