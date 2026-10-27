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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class CdsJdbcAdmissionGuardUnitTest {
    @Test
    void shouldGuardClinicalWrites_acrossNativeAndOrmSqlShapes() throws Exception {
        assertThat(CdsJdbcAdmissionGuard.clinical("update demographic set last_name=? where demographic_no=?")).isTrue();
        assertThat(CdsJdbcAdmissionGuard.clinical("insert into drugs (demographic_no) values (?)")).isTrue();
        assertThat(CdsJdbcAdmissionGuard.clinical("select p.* from preventions p where p.demographic_no=?")).isTrue();
    }

    @Test
    void shouldKeepAdministrativeExemptionsNarrow_whenQueriesJoinClinicalTables() throws Exception {
        assertThat(CdsJdbcAdmissionGuard.clinical("select * from cds_import_run where id=?")).isFalse();
        assertThat(CdsJdbcAdmissionGuard.clinical("update cds_import_run set state=? where id=?")).isFalse();
        for (String table : new String[]{"cds_import_review","cds_import_preparation","cds_import_identity"}) {
            assertThat(CdsJdbcAdmissionGuard.clinical("select * from "+table+" where id=?")).isFalse();
        }
        assertThat(CdsJdbcAdmissionGuard.clinical("select * from cds_import_run r join demographic d on d.demographic_no=r.expected_patients")).isTrue();
        assertThat(CdsJdbcAdmissionGuard.clinical("update security set password=(select last_name from demographic limit 1)")).isTrue();
        assertThat(CdsJdbcAdmissionGuard.clinical("insert into cds_import_unreviewed_table values (?)")).isTrue();
    }

    @Test
    void shouldClassifyMariaDbSharedLocks_withoutChangingClinicalOrStatementBoundaries() throws Exception {
        assertThat(CdsJdbcAdmissionGuard.clinical("select c.id from cds_import_admission c where c.id=? lock in share mode")).isFalse();
        assertThat(CdsJdbcAdmissionGuard.clinical("select d.demographic_no from demographic d where d.demographic_no=? LOCK IN SHARE MODE;")).isTrue();
        assertThat(CdsJdbcAdmissionGuard.clinical("select r.id from cds_import_run r join demographic d on d.demographic_no=r.expected_patients lock in share mode")).isTrue();
        assertThatThrownBy(()->CdsJdbcAdmissionGuard.clinical("select 1; select id from cds_import_admission lock in share mode"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(()->CdsJdbcAdmissionGuard.clinical("select id from cds_import_admission lock in share mode; commit"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldRefuseImplicitCommitAndRoutineEscapes_whenGuardIsInstalled() {
        for (String sql : new String[]{"truncate table demographic", "set autocommit=1", "call rewrite_chart()",
                "select rewrite_chart() from security", "update demographic set last_name='x'; commit",
                "show variables; update demographic set last_name='x'", "select 1; commit"}) {
            assertThatThrownBy(() -> CdsJdbcAdmissionGuard.clinical(sql), sql).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void shouldPreserveAuthenticationAndMetadataReads_duringMaintenance() throws Exception {
        assertThat(CdsJdbcAdmissionGuard.clinical("select provider_no from security where user_name=?")).isFalse();
        assertThat(CdsJdbcAdmissionGuard.clinical("select * from ResourceStorage where active=true")).isFalse();
        assertThat(CdsJdbcAdmissionGuard.clinical("select * from information_schema.tables where table_schema=database()")).isFalse();
        assertThat(CdsJdbcAdmissionGuard.clinical("select INFO from information_schema.PROCESSLIST")).isTrue();
        assertThat(CdsJdbcAdmissionGuard.clinical("show full processlist")).isTrue();
    }
}
