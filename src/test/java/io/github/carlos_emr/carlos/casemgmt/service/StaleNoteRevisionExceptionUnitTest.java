/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import com.mysql.cj.exceptions.MysqlErrorNumbers;
import com.mysql.cj.jdbc.exceptions.SQLError;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import org.hibernate.JDBCException;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.exception.SnapshotIsolationException;
import org.hibernate.exception.internal.SQLExceptionTypeDelegate;
import org.hibernate.exception.internal.SQLStateConversionDelegate;
import org.hibernate.exception.internal.StandardSQLExceptionConverter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionSystemException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the production JDBC/Hibernate/Spring exception conversion, without a database connection.
 *
 * @since 2026-10-14
 */
@Tag("unit")
@Tag("casemgmt")
class StaleNoteRevisionExceptionUnitTest {
    private JDBCException convert(int errorCode) {
        var dialect = new MariaDBDialect(DatabaseVersion.make(11, 8));
        var converter = new StandardSQLExceptionConverter(dialect.buildSQLExceptionConversionDelegate(),
                new SQLExceptionTypeDelegate(dialect), new SQLStateConversionDelegate(dialect));
        SQLException sql = SQLError.createSQLException("conversion test",
                MysqlErrorNumbers.mysqlToSqlstate(errorCode), errorCode, null);
        return converter.convert(sql, "locking read", "select 1");
    }

    @Test
    void shouldRecognizeSnapshotConflict_throughActualHibernateAndSpringConversion() {
        JDBCException hibernate = convert(1020);
        assertThat(hibernate).isInstanceOf(SnapshotIsolationException.class);
        RuntimeException spring = new HibernateJpaDialect().translateExceptionIfPossible(hibernate);
        assertThat(spring).isInstanceOf(JpaSystemException.class);
        assertThat(StaleNoteRevisionException.isConflict(hibernate)).isTrue();
        assertThat(StaleNoteRevisionException.isConflict(spring)).isTrue();
        assertThat(StaleNoteRevisionException.isConflict(new TransactionSystemException(
                "commit failed", new RollbackException("rolled back", spring)))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1205, 1213})
    void shouldRecognizeLockTimeoutAndDeadlock_throughActualConversion(int errorCode) {
        JDBCException hibernate = convert(errorCode);
        RuntimeException spring = new HibernateJpaDialect().translateExceptionIfPossible(hibernate);
        assertThat(spring).isInstanceOf(CannotAcquireLockException.class);
        assertThat(StaleNoteRevisionException.isConflict(hibernate)).isTrue();
        assertThat(StaleNoteRevisionException.isConflict(spring)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1045, 1062})
    void shouldNotHideAuthenticationOrConstraintFailures_asRevisionConflicts(int errorCode) {
        JDBCException hibernate = convert(errorCode);
        RuntimeException spring = new HibernateJpaDialect().translateExceptionIfPossible(hibernate);
        assertThat(spring).isNotNull();
        assertThat(StaleNoteRevisionException.isConflict(hibernate)).isFalse();
        assertThat(StaleNoteRevisionException.isConflict(spring)).isFalse();
    }

    @Test
    void shouldRecognizeTypedFailures_withoutGuessingFromSqlMessagesOrVendorCodes() {
        assertThat(StaleNoteRevisionException.isConflict(new StaleNoteRevisionException())).isTrue();
        assertThat(StaleNoteRevisionException.isConflict(new OptimisticLockException())).isTrue();
        assertThat(StaleNoteRevisionException.isConflict(new PersistenceException(
                new SQLException("record changed; 1020", "HY000", 1020)))).isFalse();
        assertThat(StaleNoteRevisionException.isConflict(new IllegalStateException("database unavailable"))).isFalse();
    }
}
