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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDaoImpl;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Two administrators saving the SMS settings at once, or one saving from a page that is out of date, against
 * a real database. The mock-based
 * {@link SmsConfigServiceUnitTest} checks which exceptions count as a conflict; this checks that the
 * fixed id and the version column really produce them, so the loser gets an error and the table
 * keeps one row holding the winner's settings.
 */
@Tag("unit")
@Tag("dao")
@DisplayName("SMS settings saves that race each other")
class SmsConfigSaveConflictUnitTest {
    private SessionFactory factory;

    @BeforeEach
    void buildSessionFactory() {
        factory = new Configuration().addAnnotatedClass(SmsConfig.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url",
                        "jdbc:h2:mem:sms-config-conflict-" + UUID.randomUUID() + ";MODE=MySQL")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
    }

    @AfterEach
    void closeSessionFactory() {
        factory.close();
    }

    @Test
    @DisplayName("a second first save is refused instead of adding a second row")
    void shouldRefuseSecondRow_whenTwoFirstSavesRace() {
        try (Session winner = factory.openSession(); Session loser = factory.openSession()) {
            // The loser looked before the winner committed, so it also found no row.
            SmsConfigService loserService = serviceOver(loser, Optional.empty());
            Transaction winnerTransaction = winner.beginTransaction();
            serviceOver(winner, null).save(update(true, null), "111111");
            winnerTransaction.commit();

            Transaction loserTransaction = loser.beginTransaction();
            assertThatThrownBy(() -> loserService.save(update(false, null), "222222"))
                    .isInstanceOf(SmsConfigConflictException.class);
            loserTransaction.rollback();
        }

        assertStored("111111", true);
    }

    @Test
    @DisplayName("an update based on a row another save has since changed is refused instead of overwriting it")
    void shouldRefuseStaleUpdate_whenTwoUpdatesRace() {
        try (Session seed = factory.openSession()) {
            Transaction transaction = seed.beginTransaction();
            serviceOver(seed, null).save(update(false, null), "000000");
            transaction.commit();
        }

        try (Session first = factory.openSession(); Session second = factory.openSession()) {
            Transaction secondTransaction = second.beginTransaction();
            SmsConfig seenBySecond = second.find(SmsConfig.class, SmsConfig.SINGLETON_ID);
            Transaction firstTransaction = first.beginTransaction();
            serviceOver(first, null).save(update(true, 0), "111111");
            firstTransaction.commit();

            SmsConfigService secondService = serviceOver(second, Optional.of(seenBySecond));
            assertThatThrownBy(() -> secondService.save(update(false, 0), "222222"))
                    .isInstanceOf(SmsConfigConflictException.class);
            secondTransaction.rollback();
        }

        assertStored("111111", true);
    }

    @Test
    @DisplayName("a save from a page loaded before another administrator's save is refused (stale tab)")
    void shouldRefuseSave_whenPageShowedOlderVersion() {
        saveCommitted(update(true, null), "000000");
        // Tab B, loaded at version 0, turns sending off.
        saveCommitted(update(false, 0), "222222");

        // Tab A, also loaded at version 0 and still showing sending on, saves afterwards.
        try (Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            assertThatThrownBy(() -> serviceOver(session, null).save(update(true, 0), "111111"))
                    .isInstanceOf(SmsConfigConflictException.class);
            transaction.rollback();
        }

        assertStored("222222", false);
    }

    @Test
    @DisplayName("a save from a page that showed nothing saved is refused once settings exist")
    void shouldRefuseSave_whenPageShowedNothingSavedButRowExists() {
        saveCommitted(update(false, null), "222222");

        try (Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            assertThatThrownBy(() -> serviceOver(session, null).save(update(true, null), "111111"))
                    .isInstanceOf(SmsConfigConflictException.class);
            transaction.rollback();
        }

        assertStored("222222", false);
    }

    @Test
    @DisplayName("a second click on Save is refused but recognized as already saved; another admin's save is not")
    void shouldRecognizeDoubleClick_butNotAnotherAdminsSave() {
        saveCommitted(update(true, null), "111111");

        try (Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            SmsConfigService service = serviceOver(session, null);
            // The second request of a double-click carries the same page version as the first.
            assertThatThrownBy(() -> service.save(update(true, null), "111111"))
                    .isInstanceOf(SmsConfigConflictException.class);
            transaction.rollback();

            assertThat(service.alreadySaved(update(true, null), "111111")).isTrue();
            assertThat(service.alreadySaved(update(true, null), "222222")).isFalse();
            assertThat(service.alreadySaved(update(false, null), "111111")).isFalse();
        }

        assertStored("111111", true);
    }

    private void saveCommitted(SmsConfigUpdateDto update, String providerNo) {
        try (Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            serviceOver(session, null).save(update, providerNo);
            transaction.commit();
        }
    }

    /**
     * @param seen what the save's lookup returns, standing in for a read made before the other save
     *             committed; {@code null} uses the real lookup
     */
    private static SmsConfigService serviceOver(Session session, Optional<SmsConfig> seen) {
        SmsConfigDaoImpl dao = seen == null ? new SmsConfigDaoImpl() : new SmsConfigDaoImpl() {
            @Override
            public Optional<SmsConfig> findCurrent() {
                return seen;
            }
        };
        ReflectionTestUtils.setField(dao, "entityManager", session);
        return new SmsConfigService(dao, SmsConfigServiceUnitTest.providerClients(),
                mock(ApplicationEventPublisher.class), mock(SmsConfigAuditRecorder.class));
    }

    private void assertStored(String updatedBy, boolean enabled) {
        try (Session session = factory.openSession()) {
            assertThat(session.createQuery("SELECT COUNT(c) FROM SmsConfig c", Long.class).getSingleResult())
                    .isEqualTo(1L);
            SmsConfig stored = session.find(SmsConfig.class, SmsConfig.SINGLETON_ID);
            assertThat(stored.getUpdatedBy()).isEqualTo(updatedBy);
            assertThat(stored.isEnabled()).isEqualTo(enabled);
        }
    }

    /** @param expectedVersion the version the page showed; null when it showed nothing saved */
    private static SmsConfigUpdateDto update(boolean enabled, Integer expectedVersion) {
        return new SmsConfigUpdateDto(SmsProviderType.STUB, enabled, false, "", "", false, Map.of(), expectedVersion);
    }
}
