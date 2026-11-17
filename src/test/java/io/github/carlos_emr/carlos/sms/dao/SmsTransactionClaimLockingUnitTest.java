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
package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the SQL that the SMS queue claims send through the production dialect.
 *
 * <p>Both claims must lock with {@code FOR UPDATE SKIP LOCKED}. A plain {@code FOR UPDATE} lets two
 * concurrent claims deadlock on MariaDB when they reach a row through different indexes (#3913). The
 * mock-based {@link SmsTransactionDaoImplUnitTest} checks that the hint is set; this test checks that
 * Hibernate and {@link OscarMySQL5Dialect} turn it into SQL, so a dialect or Hibernate change that
 * silently drops SKIP LOCKED fails here. H2 in MySQL mode executes the same syntax.
 */
@Tag("unit")
@Tag("dao")
@DisplayName("SMS queue claims lock with SKIP LOCKED through the production dialect")
class SmsTransactionClaimLockingUnitTest {
    private final List<String> statements = new ArrayList<>();
    private SessionFactory factory;

    @BeforeEach
    void buildSessionFactory() {
        Configuration configuration = new Configuration().addAnnotatedClass(SmsTransaction.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url",
                        "jdbc:h2:mem:sms-claim-locking-" + UUID.randomUUID() + ";MODE=MySQL")
                .setProperty("hibernate.dialect", OscarMySQL5Dialect.class.getName())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false");
        configuration.setStatementInspector((StatementInspector) sql -> {
            statements.add(sql);
            return sql;
        });
        factory = configuration.buildSessionFactory();
    }

    @AfterEach
    void closeSessionFactory() {
        factory.close();
    }

    @Test
    @DisplayName("should claim due queued rows with FOR UPDATE SKIP LOCKED")
    void shouldSkipLockedRows_whenClaimingDueQueue() {
        SmsTransaction queued = newOutboundAttempt();

        List<SmsTransaction> claimed = claimAfterPersisting(queued, dao -> dao.claimDueOutboundQueue(
                SmsProviderType.STUB, new Date(System.currentTimeMillis() + 60_000), 10));

        assertThat(claimed).hasSize(1);
        assertLocksTheOrderedCappedClaimQuery();
    }

    @Test
    @DisplayName("should claim stale sending rows for recovery with FOR UPDATE SKIP LOCKED")
    void shouldSkipLockedRows_whenClaimingStaleSending() {
        SmsTransaction stale = newOutboundAttempt();
        stale.markSending(new Date(System.currentTimeMillis() - 3_600_000));
        Date now = new Date();

        List<SmsTransaction> claimed = claimAfterPersisting(stale, dao -> dao.claimStaleOutboundSendingForRecovery(
                SmsProviderType.STUB, new Date(now.getTime() - 60_000), now, 10));

        assertThat(claimed).hasSize(1);
        assertLocksTheOrderedCappedClaimQuery();
    }

    private static SmsTransaction newOutboundAttempt() {
        return SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "synthetic claim locking test", "999998"),
                SmsProviderType.STUB);
    }

    private List<SmsTransaction> claimAfterPersisting(
            SmsTransaction row,
            Function<SmsTransactionDaoImpl, List<SmsTransaction>> claim
    ) {
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            session.persist(row);
            session.flush();
            session.clear();
            statements.clear();
            SmsTransactionDaoImpl dao = new SmsTransactionDaoImpl();
            ReflectionTestUtils.setField(dao, "entityManager", session);
            List<SmsTransaction> claimed = claim.apply(dao);
            transaction.rollback();
            return claimed;
        }
    }

    /**
     * The lock must sit on the claim query itself. Follow-on locking (a plain select, then a lock by id)
     * would also end in SKIP LOCKED but is the read-then-lock pattern that fails on MariaDB with error 1020.
     */
    private void assertLocksTheOrderedCappedClaimQuery() {
        assertThat(lockingStatements()).singleElement().satisfies(sql -> assertThat(sql)
                .contains(" order by ")
                .contains(" limit ")
                .endsWith(" for update skip locked"));
    }

    private List<String> lockingStatements() {
        return statements.stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT).strip())
                .filter(sql -> sql.contains(" for update"))
                .toList();
    }
}
