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
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsJdbcAdmissionGuardUnitTest {
    @ParameterizedTest
    @ValueSource(strings={"SELECT VERſION()","SELECT weeK('2026-10-29') FROM cds_import_run"})
    void shouldRefuseUnicodeLookalikeFunctions_afterTheSqlParserAcceptsTheirOriginalSpelling(String sql) throws Exception {
        var parsed=CCJSqlParserUtil.parseStatements(sql).getStatements();
        assertThat(parsed).hasSize(1);
        var expression=((net.sf.jsqlparser.statement.select.Select)parsed.getFirst())
                .getPlainSelect().getSelectItems().getFirst().getExpression();
        assertThat(expression).isInstanceOf(net.sf.jsqlparser.expression.Function.class);
        assertThat(((net.sf.jsqlparser.expression.Function)expression).getName()).containsAnyOf("ſ","K");
        assertThatThrownBy(()->CdsJdbcAdmissionGuard.clinical(sql)).isInstanceOf(SQLException.class);
    }

    @ParameterizedTest
    @ValueSource(strings={"SELECT * FROM cds_import_checKpoint","SELECT * FROM `cds_import_checKpoint`"})
    void shouldKeepUnicodeLookalikeTablesClinical_afterTheSqlParserAcceptsTheirOriginalSpelling(String sql) throws Exception {
        var parsed=CCJSqlParserUtil.parseStatements(sql).getStatements();
        assertThat(parsed).hasSize(1);
        assertThat(((net.sf.jsqlparser.statement.select.Select)parsed.getFirst()).getPlainSelect().getFromItem().toString())
                .contains("cds_import_checKpoint");
        assertThat(CdsJdbcAdmissionGuard.clinical(sql)).isTrue();
    }

    @Test
    void shouldPreserveAsciiBuiltinAndTableCaseRecognition_withoutPromotingUnknownAsciiNames() throws Exception {
        for(String sql:new String[]{"select version()","SELECT VERSION()","select connection_id()",
                "SELECT WeEk('2026-10-29') FROM CDS_IMPORT_CHECKPOINT",
                "select upper(source_namespace) from cDs_ImPoRt_RuN"})
            assertThat(CdsJdbcAdmissionGuard.clinical(sql)).as(sql).isFalse();
        assertThat(CdsJdbcAdmissionGuard.clinical("select * from cds_import_checkpoint_extra")).isTrue();
        assertThatThrownBy(()->CdsJdbcAdmissionGuard.clinical("select unknown_ascii_function() from cds_import_run"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldPreserveUnicodeSqlValuesAndIdentifiers_withoutChangingTheExecutedStatement() throws Exception {
        String sql="select upper(source_namespace) from cds_import_run where source_namespace='ı K café 漢字 🩺'";
        assertThat(CCJSqlParserUtil.parseStatements(sql).getStatements()).hasSize(1);
        assertThat(CdsJdbcAdmissionGuard.clinical(sql)).isFalse();
        String clinical="select * from `患者` where `名字`='Éloïse'";
        assertThat(CCJSqlParserUtil.parseStatements(clinical).getStatements()).hasSize(1);
        assertThat(CdsJdbcAdmissionGuard.clinical(clinical)).isTrue();
        Connection connection=mock(Connection.class);Statement statement=mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        CdsJdbcAdmissionGuard.wrap(connection,null,()->false).createStatement().execute(sql);
        verify(statement).execute(sql);
    }

    @Test
    void shouldPreserveDescribeMetadataReads_whenAsciiWhitespaceVaries() throws Exception {
        for (String keyword : new String[] {"DESCRIBE", "desc"}) {
            for (String whitespace : new String[] {" ", "\t", "\n", "\r\n", " \t\r\n "}) {
                assertThat(CdsJdbcAdmissionGuard.clinical(keyword + whitespace + "demographic")).isFalse();
            }
        }
    }

    @Test
    void shouldPreserveDescribeRefusals_whenCommentsOrStatementBoundariesChange() throws Exception {
        for (String sql : new String[] {
                "DESC demographic\n-- trailing comment", "DESCRIBE demographic /* first\nsecond */"}) {
            assertThat(CCJSqlParserUtil.parseStatements(sql).getStatements()).hasSize(1);
            assertThatThrownBy(() -> CdsJdbcAdmissionGuard.clinical(sql)).isInstanceOf(SQLException.class);
        }
        for (String sql : new String[] {"DESC demographic; COMMIT", "DESCRIBE demographic; SELECT 1"}) {
            assertThatThrownBy(() -> CdsJdbcAdmissionGuard.clinical(sql)).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void shouldBoundDescribeRefusalWork_whenLongWhitespacePrecedesAMultilineComment() {
        String sql = "DESC" + " ".repeat(128 * 1024) + " demographic /* first\nsecond */";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThat(CCJSqlParserUtil.parseStatements(sql).getStatements()).hasSize(1);
            assertThatThrownBy(() -> CdsJdbcAdmissionGuard.clinical(sql)).isInstanceOf(SQLException.class);
        });
    }

    @Test
    void shouldNormalizeSuffixesInLinearTime_whenLongRunsDoNotTerminateTheStatement() {
        String semicolons = "select id from cds_import_admission" + ";".repeat(200000) + " commit";
        String wrongLock = "select id from cds_import_admission" + " ".repeat(200000) + "LOCK IN SHARE MODX";
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            assertThat(CdsJdbcAdmissionGuard.stripTerminalSemicolons(semicolons)).isEqualTo(semicolons);
            assertThat(CdsJdbcAdmissionGuard.normalizeSharedLock(semicolons)).isEqualTo(semicolons);
            assertThat(CdsJdbcAdmissionGuard.normalizeSharedLock(wrongLock)).isEqualTo(wrongLock);
        });
    }

    @Test
    void shouldRetainExactSuffixSemantics_whenWhitespaceAndCaseVary() throws Exception {
        assertThat(CdsJdbcAdmissionGuard.stripTerminalSemicolons("\u2003select 1;;;\u2003")).isEqualTo("select 1");
        assertThat(CdsJdbcAdmissionGuard.stripTerminalSemicolons("select 1;; ;")).isEqualTo("select 1;; ");
        assertThat(CdsJdbcAdmissionGuard.normalizeSharedLock("select id from cds_import_admission \tLoCk\tIn\fShArE\rMoDe \n"))
                .isEqualTo("select id from cds_import_admission FOR SHARE");
        for (String suffix : new String[] {"LOCK IN SHARE MODEX", "LOCK IN SHARE MOD", "LOCK IN SHARE MODE NOWAIT",
                "LOCK\u2003IN SHARE MODE", "LOC\u212a IN SHARE MODE", "LOCK INSHARE MODE"}) {
            String sql = "select id from cds_import_admission " + suffix;
            assertThat(CdsJdbcAdmissionGuard.normalizeSharedLock(sql)).isEqualTo(sql);
        }
        String prefix = "select id from cds_import_admission";
        assertThat(CdsJdbcAdmissionGuard.clinical(prefix + " ".repeat(200000) + "lock in share mode;;;")).isFalse();
        assertThatThrownBy(() -> CdsJdbcAdmissionGuard.clinical(prefix + ";".repeat(10000) + "commit"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldExecuteTheOriginalSharedLockSql_whenClassificationNormalizesItsSuffix() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        String original = "select id from cds_import_admission \tLoCk\tIn ShArE MoDe;;;";
        CdsJdbcAdmissionGuard.wrap(connection, null, () -> false).createStatement().execute(original);
        verify(statement).execute(original);
    }

    @Test
    void shouldReadAdministrativeProgramPreferences_whenMaintenanceIsActive() throws Exception {
        assertThat(CdsJdbcAdmissionGuard.clinical("select p.program_id from provider_default_program p "
                + "where p.provider_no=? order by p.id")).isFalse();
        assertThat(CdsJdbcAdmissionGuard.clinical("select p.id,p.name from program p join provider_default_program d "
                + "on p.id=d.program_id where d.provider_no=?")).isFalse();
    }

    @Test
    void shouldGuardPreferenceWritesAndClinicalProgramGraphs_whenPreferenceReadsAreAllowed() throws Exception {
        for (String sql : new String[] {
                "update provider_default_program set program_id=? where provider_no=?",
                "insert into provider_default_program (provider_no,program_id) values (?,?)",
                "delete from provider_default_program where provider_no=?",
                "select d.program_id from provider_default_program d join admission a on a.program_id=d.program_id",
                "select d.program_id from provider_default_program d join program_queue q on q.program_id=d.program_id",
                "select d.program_id,(select count(*) from admission a where a.program_id=d.program_id) from provider_default_program d",
                "select * from provider_default_program d join demographic p on p.provider_no=d.provider_no"}) {
            assertThat(CdsJdbcAdmissionGuard.clinical(sql)).as(sql).isTrue();
        }
    }

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
    void shouldAdmitExactProviderBinaryCasts_withoutExemptingNestedClinicalOrRoutineSql() throws Exception {
        for (String sql : new String[] {
                "select provider_no from provider where ohip_no=? and CAST(ohip_no AS BINARY)=CAST(? AS BINARY) order by provider_no limit ?",
                "select provider_no from provider where practitionerNoType='CPSO' and practitionerNo=? and CAST(practitionerNoType AS BINARY)=CAST('CPSO' AS BINARY) and CAST(practitionerNo AS BINARY)=CAST(? AS BINARY) order by provider_no limit ?",
                "select provider_no from provider where first_name=CONVERT(? USING latin1) and last_name=CONVERT(? USING latin1) and CAST(CONVERT(first_name USING utf8mb4) AS BINARY)=CAST(CONVERT(? USING utf8mb4) AS BINARY) and CAST(CONVERT(last_name USING utf8mb4) AS BINARY)=CAST(CONVERT(? USING utf8mb4) AS BINARY) order by provider_no limit ?"}) {
            assertThat(CdsJdbcAdmissionGuard.clinical(sql)).isFalse();
        }
        assertThat(CdsJdbcAdmissionGuard.clinical("select CAST((select last_name from demographic limit 1) AS BINARY) from provider")).isTrue();
        for (String sql : new String[] {
                "select CAST(rewrite_chart() AS BINARY) from provider",
                "select CAST(clinic.rewrite_chart() AS BINARY) from provider",
                "select provider_no from provider where CAST(first_name AS BINARY)=CAST(? AS BINARY); commit"}) {
            assertThatThrownBy(() -> CdsJdbcAdmissionGuard.clinical(sql)).isInstanceOf(SQLException.class);
        }
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
