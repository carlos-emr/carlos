/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.util.persistence;

import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.cfg.Configuration;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Shared MySQL and MariaDB pessimistic locking syntax")
class OscarMySQL5DialectUnitTest {
    @Test
    @DisplayName("should omit alias lists from write locks and use portable shared locks")
    void shouldRenderPortableLocks_whenAliasesAreSupplied() {
        var dialect = new OscarMySQL5Dialect();
        assertThat(dialect.getForUpdateString("routing_alias")).isEqualTo(" for update");
        assertThat(dialect.getWriteLockString("routing_alias", -1)).isEqualTo(" for update");
        assertThat(dialect.getReadLockString("routing_alias", -1)).isEqualTo(" lock in share mode");
        assertThat(dialect.getForUpdateNowaitString("routing_alias")).doesNotContain(" of ");
        assertThat(dialect.getForUpdateSkipLockedString("routing_alias")).doesNotContain(" of ");
    }

    @Test
    @DisplayName("should generate unqualified FOR UPDATE for actual routing queries and entity refresh")
    void shouldUsePortableSql_whenHibernateLocksRouting() {
        List<String> statements = new ArrayList<>();
        var configuration = new Configuration().addAnnotatedClass(ProviderLabRoutingModel.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:dialect-routing-" + java.util.UUID.randomUUID() + ";MODE=MySQL")
                .setProperty("hibernate.dialect", OscarMySQL5Dialect.class.getName())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false");
        configuration.setStatementInspector((StatementInspector) sql -> { statements.add(sql); return sql; });
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            var row = new ProviderLabRoutingModel();
            row.setLabNo(170);
            row.setLabType("HL7");
            row.setProviderNo("999998");
            row.setStatus("A");
            session.persist(row);
            session.flush();
            statements.clear();
            var rows = session.createQuery("from ProviderLabRoutingModel p where p.labNo = :id", ProviderLabRoutingModel.class)
                    .setParameter("id", 170).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            assertThat(rows).hasSize(1);
            session.refresh(rows.get(0), LockModeType.PESSIMISTIC_WRITE);
            assertThat(statements).anyMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains(" for update"));
            assertThat(statements).noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains(" for update of "));
            transaction.rollback();
        }
    }
}
