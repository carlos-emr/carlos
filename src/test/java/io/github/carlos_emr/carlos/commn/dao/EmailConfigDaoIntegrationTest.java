// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the release schema and real JPA queries; each test rolls back its owned rows. */
@Tag("integration")
@Tag("dao")
class EmailConfigDaoIntegrationTest extends CarlosTestBase {
    @Autowired private EmailConfigDao dao;
    @PersistenceContext private EntityManager entityManager;

    private EmailConfig save(String sender, EmailType type, EmailProvider provider, boolean active) {
        EmailConfig config = new EmailConfig(type, provider, sender);
        config.setActive(active);
        config.setSenderFirstName("Coverage");
        config.setSenderLastName("Fixture");
        config.setConfigDetailsJson("{}");
        dao.persist(config);
        return config;
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void shouldMatchAllCriteria_whenConfigurationsShareSender() {
        String sender = "shared@example.invalid";
        EmailConfig expected = save(sender, EmailType.SMTP, EmailProvider.LOCAL, true);
        save(sender, EmailType.API, EmailProvider.LOCAL, true);
        save(sender, EmailType.SMTP, EmailProvider.GMAIL, true);
        save("other@example.invalid", EmailType.SMTP, EmailProvider.LOCAL, true);
        save(sender, EmailType.SMTP, EmailProvider.LOCAL, false);
        reload();
        assertThat(dao.findActiveEmailConfig(new EmailConfig(EmailType.SMTP, EmailProvider.LOCAL, sender)).getId())
                .isEqualTo(expected.getId());
    }

    @Test
    void shouldExcludeInactiveConfigurations_fromEveryActiveLookup() {
        EmailConfig inactive = save("inactive@example.invalid", EmailType.SMTP, EmailProvider.LOCAL, false);
        EmailConfig active = save("active@example.invalid", EmailType.SMTP, EmailProvider.LOCAL, true);
        reload();
        assertThat(dao.findActiveEmailConfig(inactive)).isNull();
        assertThat(dao.findActiveEmailConfig(inactive.getSenderEmail())).isNull();
        assertThat(dao.findActiveEmailConfigById(inactive.getId())).isNull();
        assertThat(dao.fillAllActiveEmailConfigs()).extracting(EmailConfig::getId)
                .contains(active.getId()).doesNotContain(inactive.getId());
        assertThat(dao.findActiveEmailConfig(active.getSenderEmail()).getId()).isEqualTo(active.getId());
    }

    @Test
    void shouldReturnNull_whenNoConfigurationMatches() {
        assertThat(dao.findActiveEmailConfig("missing@example.invalid")).isNull();
        assertThat(dao.findActiveEmailConfigById(-1)).isNull();
        assertThat(dao.findActiveEmailConfig(new EmailConfig(EmailType.API, EmailProvider.SENDGRID,
                "missing@example.invalid"))).isNull();
    }

    @Test
    void shouldPersistLongJson_withoutTruncation() {
        // Synthetic payload exceeds VARCHAR(255), exercising this release's TEXT mapping.
        EmailConfig config = save("long@example.invalid", EmailType.SMTP, EmailProvider.LOCAL, true);
        String json = "{\"fixture\":\"" + "synthetic-only-".repeat(1000) + "\"}";
        config.setConfigDetailsJson(json);
        dao.merge(config);
        reload();
        assertThat(dao.findActiveEmailConfigById(config.getId()).getConfigDetailsJson()).isEqualTo(json);
    }

    @ParameterizedTest
    @EnumSource(EmailProvider.class)
    void shouldRoundTripProvider_whenReloadedFromDatabase(EmailProvider provider) {
        EmailConfig config = save("provider@example.invalid", EmailType.SMTP, provider, true);
        reload();
        EmailConfig loaded = dao.findActiveEmailConfigById(config.getId());
        assertThat(loaded.getEmailProvider()).isEqualTo(provider);
        assertThat(loaded.getEmailType()).isEqualTo(EmailType.SMTP);
    }

    @ParameterizedTest
    @EnumSource(EmailType.class)
    void shouldRoundTripType_whenReloadedFromDatabase(EmailType type) {
        EmailConfig config = save("type@example.invalid", type, EmailProvider.LOCAL, true);
        reload();
        assertThat(dao.findActiveEmailConfigById(config.getId()).getEmailType()).isEqualTo(type);
    }

    @Test
    void shouldStopSelectingConfiguration_afterDeactivation() {
        EmailConfig config = save("toggle@example.invalid", EmailType.SMTP, EmailProvider.LOCAL, true);
        reload();
        EmailConfig loaded = dao.findActiveEmailConfigById(config.getId());
        loaded.setActive(false);
        dao.merge(loaded);
        reload();
        assertThat(dao.findActiveEmailConfigById(config.getId())).isNull();
        assertThat(dao.findActiveEmailConfig(config.getSenderEmail())).isNull();
    }

    @Test
    void shouldTreatQuotedSenderAsData_whenQuerying() {
        String sender = "quote' OR '1'='1@example.invalid";
        EmailConfig config = save(sender, EmailType.SMTP, EmailProvider.LOCAL, true);
        save("unrelated@example.invalid", EmailType.SMTP, EmailProvider.LOCAL, true);
        reload();
        assertThat(dao.findActiveEmailConfig(sender).getId()).isEqualTo(config.getId());
    }
}
