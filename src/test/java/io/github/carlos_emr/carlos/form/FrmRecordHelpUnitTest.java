/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Form record helper")
class FrmRecordHelpUnitTest {

    @Test
    @DisplayName("should use MySQL last inserted ID query by default")
    void shouldUseMysqlLastInsertedIdQuery_byDefault() throws SQLException {
        assertThat(FrmRecordHelp.lastInsertedIdSql(null)).isEqualTo("SELECT LAST_INSERT_ID()");
        assertThat(FrmRecordHelp.lastInsertedIdSql("")).isEqualTo("SELECT LAST_INSERT_ID()");
        assertThat(FrmRecordHelp.lastInsertedIdSql("mysql")).isEqualTo("SELECT LAST_INSERT_ID()");
    }

    @Test
    @DisplayName("should use PostgreSQL lastval when database type is PostgreSQL")
    void shouldUsePostgresqlLastval_whenDatabaseTypeIsPostgresql() throws SQLException {
        assertThat(FrmRecordHelp.lastInsertedIdSql("postgresql")).isEqualTo("SELECT LASTVAL()");
    }

    @Test
    @DisplayName("should reject unsupported database types")
    void shouldRejectUnsupportedDatabaseTypes() {
        assertThatThrownBy(() -> FrmRecordHelp.lastInsertedIdSql("oracle"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("oracle");
    }
}
