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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;

import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Real ORM insertion catches field-access NULLs hidden by presentation getters and DB defaults. */
@Tag("integration")
class CdsDemographicStorageMariaDbIntegrationTest {
    @TempDir Path temporary;

    @Test
    void shouldPersistAbsentPreferredNameAsEmpty_underActualStrictMariaDbFieldMapping()
            throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(
                selector != null && !selector.isBlank(),
                "Explicit isolated MariaDB tier requires protected cds.test.properties");
        Properties jdbc = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) {
            jdbc.load(input);
        }
        assertThat(jdbc.getProperty("jdbcUrl"))
                .matches(
                        "jdbc:(mysql|mariadb)://[^/]+/(carlos_cds_test|cds_full_test|cds_ledger_test)(\\?.*)?");
        try (var dataSource = new BasicDataSource()) {
            dataSource.setUrl(jdbc.getProperty("jdbcUrl"));
            dataSource.setUsername(jdbc.getProperty("jdbcUser"));
            dataSource.setPassword(jdbc.getProperty("jdbcPassword"));
            dataSource.setMaxTotal(2);
            Configuration configuration =
                    new Configuration()
                            .addAnnotatedClass(Demographic.class)
                            .addAnnotatedClass(Provider.class);
            configuration.getProperties().put("hibernate.connection.datasource", dataSource);
            configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
            configuration.setProperty("hibernate.hbm2ddl.auto", "none");
            try (var factory = configuration.buildSessionFactory();
                    var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                try {
                    session.doWork(
                            connection -> {
                                try (var query = connection.createStatement();
                                        var rows = query.executeQuery("SELECT @@sql_mode")) {
                                    assertThat(rows.next()).isTrue();
                                    assertThat(rows.getString(1)).contains("STRICT_");
                                }
                            });
                    var command =
                            new CdsImportCommand(
                                    temporary,
                                    "strict-native-demographic",
                                    "999998",
                                    null,
                                    null,
                                    "browser-synthetic",
                                    "pcon-2024-02",
                                    false,
                                    false,
                                    false,
                                    0,
                                    null,
                                    false);
                    String marker = "FAKE-CDS-ORM-" + UUID.randomUUID();
                    var prepared =
                            new CdsDemographicNativeDto(
                                    "strict-source",
                                    "Import",
                                    marker,
                                    "1971-03-04",
                                    null,
                                    null,
                                    "F",
                                    "AC",
                                    Map.of(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    "999998",
                                    List.of());
                    var source = mock(CdsRecordDto.class);
                    when(source.effectiveSha256()).thenReturn("a".repeat(64));
                    var preparation = mock(CdsNativePreparationService.class);
                    when(preparation.load(
                                    anyString(),
                                    anyLong(),
                                    anyString(),
                                    anyString(),
                                    eq(command),
                                    eq(CdsDemographicNativeDto.class)))
                            .thenReturn(prepared);
                    var identity = mock(CdsPatientIdentityResolver.class);
                    when(identity.resolve(any(CdsPatientIdentityDto.class), eq(command)))
                            .thenReturn(
                                    new CdsIdentityDecisionDto(
                                            CdsIdentityDecisionDto.Outcome.NO_MATCH, null));
                    var writer =
                            new CdsDemographicPersister(
                                    mock(CdsFieldParser.class),
                                    identity,
                                    mock(CdsProviderResolver.class),
                                    preparation);
                    ReflectionTestUtils.setField(writer, "entityManager", session);
                    var targets =
                            writer.write(
                                    "11111111-1111-4111-8111-111111111111",
                                    1,
                                    "unit",
                                    source,
                                    command,
                                    notice -> {});
                    int id = Integer.parseInt(targets.get("demographic"));
                    session.flush();
                    session.clear();
                    assertThat(
                                    ReflectionTestUtils.getField(
                                            session.find(Demographic.class, id), "prefName"))
                            .isEqualTo("");
                    session.doWork(
                            connection -> {
                                try (var query =
                                        connection.prepareStatement(
                                                "SELECT pref_name,last_name FROM demographic WHERE"
                                                    + " demographic_no=?")) {
                                    query.setInt(1, id);
                                    try (var rows = query.executeQuery()) {
                                        assertThat(rows.next()).isTrue();
                                        assertThat(rows.getString(1)).isEmpty();
                                        assertThat(rows.getString(2)).isEqualTo(marker);
                                    }
                                }
                            });
                } finally {
                    transaction.rollback();
                }
            }
        }
    }
}
