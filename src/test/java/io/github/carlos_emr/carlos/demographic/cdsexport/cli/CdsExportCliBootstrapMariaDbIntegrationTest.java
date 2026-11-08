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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportState;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportLedgerService;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportService;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Full minimal CLI graph, actual legacy privilege reader and physical transactions; no clinical engine or fake grant. */
@Tag("integration") @Tag("cds-export")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportCliBootstrapMariaDbIntegrationTest {
    @Test void shouldObserveAndCancelOwnedMetadata_andSeeLiveRevocationThroughTheRealPrivilegeGraph() throws Exception {
        Properties jdbc = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) { jdbc.load(input); }
        String url = jdbc.getProperty("jdbcUrl", "");
        assertThat(url).matches("jdbc:mysql://[^/]+/cds_export_test(?:\\?.*)?");
        assertThat(jdbc.getProperty("ownedDisposableDatabase")).isEqualTo("true");
        Properties settings = new Properties(); int slash = url.indexOf('/', "jdbc:mysql://".length());
        settings.setProperty("db_driver", "com.mysql.cj.jdbc.Driver"); settings.setProperty("db_uri", url.substring(0, slash + 1));
        settings.setProperty("db_name", url.substring(slash + 1)); settings.setProperty("db_username", jdbc.getProperty("jdbcUser"));
        settings.setProperty("db_password", jdbc.getProperty("jdbcPassword")); settings.setProperty("billregion", "ON");
        settings.setProperty("cds.export.enabled", "false"); settings.setProperty("cds.export.worker.enabled", "false");
        settings.setProperty("cds.import.worker.enabled", "false"); settings.setProperty("cds.export.operator.bootstrap-fixture.providers", "989876");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("cds-export-cli");
            context.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("owned-cli-fixture", settings));
            context.registerBean("cdsExportProperties", Properties.class, () -> settings); context.register(CdsExportCliSpringConfiguration.class);
            context.refresh();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDisclosurePolicyService.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDemographicOccurrenceService.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNoteSourceReader.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNoteExtensionSourceReader.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNoteIssueLinkSourceReader.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNoteRevisionInventoryService.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportCaseIssueSourceReader.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportIssueCatalogueSourceReader.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNoteScopeProjectionService.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportLaboratorySourceReader.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportLaboratoryMessageSourceReader.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportLaboratoryInfoSourceReader.class)).isNotNull();
            assertThat(context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportLaboratoryRoutingSourceReader.class)).isNotNull();
            var sql = new JdbcTemplate(context.getBean(DataSource.class));
            assertThat(sql.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("cds_export_test");
            var sourceRead = new org.springframework.transaction.support.TransactionTemplate(
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            sourceRead.setReadOnly(true);
            sourceRead.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            sourceRead.execute(status -> {
                var reader = context.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDemographicSourceReader.class);
                try {
                    assertThat(new io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportSourceSchemaValidator()
                            .verify(reader.schema(), reader.columns())).matches("[0-9a-f]{64}");
                } catch (java.io.IOException failed) { throw new java.io.UncheckedIOException(failed); }
                return null;
            });
            assertThat(sql.queryForObject("select count(*) from provider where provider_no='989876'", Long.class)).isZero();
            String run = null;
            try (var spring = mockStatic(SpringUtils.class)) {
                // The privilege reader still resolves this existing core DAO through the published Spring bridge.
                // Its query results and permission decisions are real; no mocked grant can hide a missing registration.
                spring.when(() -> SpringUtils.getBean(SecObjPrivilegeDao.class)).thenReturn(context.getBean(SecObjPrivilegeDao.class));
                sql.update("insert into provider(provider_no,first_name,last_name,provider_type,specialty,sex,status,lastUpdateDate) values('989876','Synthetic','CLI bootstrap','doctor','','U','1',CURRENT_TIMESTAMP)");
                sql.update("insert into secObjPrivilege(roleUserGroup,objectName,privilege,priority) values('989876','_demographic','|r|',10),('989876','_cds_export','|w|',10)");
                var command = new CdsExportCommand(UUID.randomUUID().toString(), "989876", null, CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS, List.of(), List.of(27), EnumSet.allOf(CdsCategory.class), "fixture", "fixture");
                var document = new CdsExportJobDocumentDto(1, new CdsExportActorDto("bootstrap-fixture", "989876", null, null), command,
                        "a".repeat(64), "fixture", "b".repeat(64), "c".repeat(64));
                var ledger = context.getBean(CdsExportLedgerService.class); var facade = context.getBean(CdsExportService.class);
                run = ledger.accept(document).id(); final String id = run;
                assertThat(facade.observeDelegated("bootstrap-fixture", "989876", id).state()).isEqualTo(CdsExportState.ACCEPTED);
                assertThat(facade.submissionDelegated("bootstrap-fixture", "989876", command.submissionKey()).id()).isEqualTo(id);
                assertThat(facade.dispatcherRunning()).isFalse();
                sql.update("delete from secObjPrivilege where roleUserGroup='989876' and objectName='_cds_export'");
                assertThatThrownBy(() -> facade.observeDelegated("bootstrap-fixture", "989876", id)).isInstanceOf(SecurityException.class);
                assertThat(ledger.status(id).state()).isEqualTo(CdsExportState.ACCEPTED);
                sql.update("insert into secObjPrivilege(roleUserGroup,objectName,privilege,priority) values('989876','_cds_export','|w|',10)");
                assertThat(facade.cancelDelegated("bootstrap-fixture", "989876", id).state()).isEqualTo(CdsExportState.CANCELLED);
                assertThat(ledger.status(id).artifactAvailable()).isFalse();
            } finally {
                if (run != null) { sql.update("delete from cds_export_event where run_id=?", run); sql.update("delete from cds_export_run where id=?", run); }
                sql.update("delete from secObjPrivilege where roleUserGroup='989876'"); sql.update("delete from provider where provider_no='989876'");
            }
        }
    }
}
