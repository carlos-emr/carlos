/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.email.core.EmailConfigSecrets;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Email credential migration")
@Tag("integration")
@Tag("security")
@Tag("update")
class EmailCredentialMigrationIntegrationTest extends CarlosTestBase {
    private static final String ORIGINAL = "{\"password\":\"old-fixture\"}";

    @Autowired
    private EmailConfigDaoImpl emailConfigDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private EmailManager manager;
    private String originalKey;

    @BeforeEach
    void setUpMigration() throws Exception {
        originalKey = EncryptionKeyTestSupport.seedFreshKey();
        manager = new EmailManager(null, null, null, mock(OutboundEmailArchiveService.class));
        ReflectionTestUtils.setField(manager, "emailConfigDao", emailConfigDao);
    }

    @AfterEach
    void restoreKey() {
        EncryptionKeyTestSupport.restoreKey(originalKey);
    }

    @Test
    @DisplayName("should persist encrypted credentials once without changing account settings")
    void shouldEncryptCredentialsOnce_whenAccountUnchanged() throws Exception {
        EmailConfig config = detachedConfig();

        manager.upgradeConfigCredentialsAtRest(config);

        EmailConfig stored = reload(config);
        String encrypted = stored.getConfigDetailsJson();
        assertThat(encrypted).isEqualTo(config.getConfigDetailsJson()).doesNotContain("old-fixture");
        String password = new ObjectMapper().readTree(encrypted).get("password").asText();
        assertThat(password).startsWith("{ENC}");
        assertThat(EmailConfigSecrets.decryptSecret(password)).isEqualTo("old-fixture");
        assertThat(stored.getActive()).isTrue();
        assertThat(stored.getSenderEmail()).isEqualTo("fixture@example.org");

        manager.upgradeConfigCredentialsAtRest(stored);

        assertThat(reload(stored).getConfigDetailsJson()).isEqualTo(encrypted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rotated-fixture", "OLD-fixture"})
    @DisplayName("should preserve a password rotation, including a change of case")
    void shouldPreserveRotatedCredentials_whenMigrationUsesStaleConfig(String password) {
        EmailConfig stale = detachedConfig();
        String rotated = "{\"password\":\"" + password + "\"}";
        entityManager.createQuery("UPDATE EmailConfig e SET e.configDetailsJson = ?1 WHERE e.id = ?2")
                .setParameter(1, rotated).setParameter(2, stale.getId()).executeUpdate();

        manager.upgradeConfigCredentialsAtRest(stale);

        assertThat(reload(stale).getConfigDetailsJson()).isEqualTo(rotated);
        assertThat(stale.getConfigDetailsJson()).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("should preserve account deactivation after the send configuration was loaded")
    void shouldKeepAccountDisabled_whenMigrationUsesStaleConfig() {
        EmailConfig stale = detachedConfig();
        entityManager.createQuery("UPDATE EmailConfig e SET e.active = false WHERE e.id = ?1")
                .setParameter(1, stale.getId()).executeUpdate();

        manager.upgradeConfigCredentialsAtRest(stale);

        EmailConfig stored = reload(stale);
        assertThat(stored.getActive()).isFalse();
        assertThat(stored.getConfigDetailsJson()).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("should preserve updated sender settings while encrypting unchanged credentials")
    void shouldPreserveSenderSettings_whenAccountEditedAfterLookup() {
        EmailConfig stale = detachedConfig();
        entityManager.createQuery("UPDATE EmailConfig e SET e.senderEmail = ?1 WHERE e.id = ?2")
                .setParameter(1, "updated@example.org").setParameter(2, stale.getId()).executeUpdate();

        manager.upgradeConfigCredentialsAtRest(stale);

        EmailConfig stored = reload(stale);
        assertThat(stored.getSenderEmail()).isEqualTo("updated@example.org");
        assertThat(stored.getConfigDetailsJson()).contains("{ENC}").doesNotContain("old-fixture");
    }

    @Test
    @DisplayName("should leave a deleted account absent when migration uses its stale configuration")
    void shouldKeepAccountDeleted_whenMigrationUsesStaleConfig() {
        EmailConfig stale = detachedConfig();
        entityManager.createQuery("DELETE FROM EmailConfig e WHERE e.id = ?1")
                .setParameter(1, stale.getId()).executeUpdate();

        manager.upgradeConfigCredentialsAtRest(stale);

        assertThat(reload(stale)).isNull();
        assertThat(stale.getConfigDetailsJson()).isEqualTo(ORIGINAL);
    }

    private EmailConfig detachedConfig() {
        EmailConfig config = new EmailConfig(EmailConfig.EmailType.SMTP,
                EmailConfig.EmailProvider.GMAIL, "fixture@example.org");
        config.setActive(true);
        config.setConfigDetailsJson(ORIGINAL);
        emailConfigDao.persist(config);
        entityManager.flush();
        entityManager.clear();
        return config;
    }

    private EmailConfig reload(EmailConfig config) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(EmailConfig.class, config.getId());
    }
}
