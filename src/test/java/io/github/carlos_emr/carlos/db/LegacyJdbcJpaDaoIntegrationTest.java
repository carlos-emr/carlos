/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The retained compatibility boundary must share the JPA transaction, including when its
 * connection wrapper closes. Runs on H2 by default and physical MariaDB with
 * CARLOS_TEST_MARIADB_DAO=true. Suspending the test transaction makes commits and rollbacks real.
 */
@Tag("integration")
@Tag("dao")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LegacyJdbcJpaDaoIntegrationTest extends CarlosTestBase {
    @Autowired private PropertyDao propertyDao;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    private final String key = "jdbc-5507-" + UUID.randomUUID();

    @AfterEach
    void cleanFixtures() {
        // Cleanup remains necessary for legacy callers, even after the raw accessor is gone.
        DbConnectionFilter.releaseAllThreadDbResources();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            propertyDao.removeByName(key + "-jpa");
            propertyDao.removeByName(key + "-jdbc");
        });
    }

    @Test
    void shouldCommitBothWrites_whenLegacyConnectionWrapperCloses() throws Exception {
        Integer id = new TransactionTemplate(transactionManager).execute(status -> writeBoth());

        assertThat(id).isPositive();
        assertThat(countCommittedRows()).isEqualTo(2);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(propertyDao.findByName(key + "-jpa")).singleElement()
                    .satisfies(row -> {
                        assertThat(row.getId()).isEqualTo(id);
                        assertThat(row.getValue()).isNull();
                    });
            assertThat(propertyDao.findByName(key + "-jdbc")).singleElement()
                    .satisfies(row -> assertThat(row.getValue()).isEqualTo("synthetic ' bound value"));
        });
    }

    @Test
    void shouldRollBackBothWrites_whenFailureFollowsEarlierWrites() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            assertThat(writeBoth()).isPositive();
            // Observe through another physical connection: neither write has auto-committed.
            try {
                assertThat(countCommittedRows()).isZero();
            } catch (SQLException failure) {
                throw new IllegalStateException(failure);
            }
            throw new IllegalStateException("synthetic failure after both writes");
        })).isInstanceOf(IllegalStateException.class).hasMessage("synthetic failure after both writes");

        assertThat(countCommittedRows()).isZero();
    }

    private Integer writeBoth() {
        Property property = new Property(key + "-jpa");
        propertyDao.persist(property);
        try (Connection connection = LegacyJdbcQuery.getConnection();
             var statement = connection.prepareStatement("INSERT INTO property (name, value) VALUES (?, ?)")) {
            assertThat(connection.getAutoCommit()).isFalse();
            statement.setString(1, key + "-jdbc");
            statement.setString(2, "synthetic ' bound value");
            assertThat(statement.executeUpdate()).isEqualTo(1);
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
        // A new DAO operation after the wrapper closes must still reach the open transaction.
        assertThat(propertyDao.findByName(key + "-jdbc")).hasSize(1);
        return property.getId();
    }

    private int countCommittedRows() throws SQLException {
        try (Connection observer = dataSource.getConnection();
             var statement = observer.prepareStatement("SELECT COUNT(*) FROM property WHERE name IN (?, ?)")) {
            statement.setString(1, key + "-jpa");
            statement.setString(2, key + "-jdbc");
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }
}
