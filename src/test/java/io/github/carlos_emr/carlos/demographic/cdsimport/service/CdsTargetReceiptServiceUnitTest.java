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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A receipt names exactly the rows its unit wrote, and a target table widened locally to any
 * width MariaDB allows is receipted and witnessed whole, its digest statement as long as its
 * columns make it.
 */
@Tag("unit")
class CdsTargetReceiptServiceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111";
    private static final String UNIT = "c".repeat(64);

    /** Every statement the receipt service prepares, in order. */
    private final List<String> statements = new ArrayList<>();

    @Test
    void shouldNameOnlyTheRowsTheUnitWrote_whenOtherDateRowsCarryTheDrugId() throws Exception {
        // The medication writer returns its drug and its two date rows. Other date rows carrying
        // the same drug id (left by a deleted drug, since partial_date has no foreign key) are
        // never looked up, so they cannot enter the receipt or stop the unit.
        var service = service(Map.of("drugs", 1, "partial_date", 1));
        Map<String, String> written = new LinkedHashMap<>();
        written.put("drugs", "81");
        written.put("partial_date#written", "501");
        written.put("partial_date#start", "502");
        var receipt = service.capture(written, RUN, UNIT);
        var refs = new ObjectMapper().readTree(receipt.targetReferences()).path("refs");
        assertThat(refs).hasSize(3);
        assertThat(refs.get(0).path("table").asText()).isEqualTo("drugs");
        assertThat(refs.get(0).path("id").asText()).isEqualTo("81");
        assertThat(refs.get(1).path("table").asText()).isEqualTo("partial_date");
        assertThat(refs.get(1).path("id").asText()).isEqualTo("501");
        assertThat(refs.get(2).path("id").asText()).isEqualTo("502");
        assertThat(statements)
                .as("no row is found by key: only the unit's own file references are looked up")
                .noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("table_id"))
                .filteredOn(sql -> !sql.startsWith("SELECT"))
                .containsExactly(
                        "select id,file_id from cds_import_file_reference where run_id=? and"
                                + " unit_key=? order by id");
        assertThat(service.verify(receipt)).isTrue();
    }

    @Test
    void shouldCaptureAndVerifyAReceipt_whenTargetTableHasMoreThan512Columns() throws Exception {
        // drugs widened locally to 1,000 columns with long names: past the 512 columns and the
        // 65,536-character digest statement earlier builds refused.
        var service = service(Map.of("drugs", 1000));
        var receipt = service.capture(Map.of("drugs", "81"), RUN, UNIT);
        assertThat(statements)
                .filteredOn(sql -> sql.startsWith("SELECT `"))
                .allSatisfy(sql -> assertThat(sql.length()).isGreaterThan(65_536));
        var witness =
                CdsNativeBaselineCodec.unseal(
                        receipt.nativeBaseline(),
                        receipt.targetReferences(),
                        receipt.targetDigest(),
                        CdsNativeBaselineTestKeys.keys());
        assertThat(witness.rows()).singleElement().satisfies(row -> {
            assertThat(row.columns()).hasSize(1000);
            assertThat(row.cells()).hasSize(1000);
        });
        assertThat(service.verify(receipt)).isTrue();
    }

    @Test
    void shouldReceiptATargetTable_atMariaDbsColumnLimit() throws Exception {
        var service = service(Map.of("drugs", 4096));
        var receipt = service.capture(Map.of("drugs", "81"), RUN, UNIT);
        assertThat(service.verify(receipt)).isTrue();
        var wider = service(Map.of("drugs", 4097));
        assertThatThrownBy(() -> wider.capture(Map.of("drugs", "81"), RUN, UNIT))
                .isInstanceOf(CdsImportException.class)
                .hasMessage("TARGET_COLUMN_COUNT_REQUIRES_REVIEW");
    }

    /** A receipt service over a mocked owning connection whose tables have these widths. */
    private CdsTargetReceiptService service(Map<String, Integer> widths) throws Exception {
        statements.clear();
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString()))
                .thenAnswer(call -> statement(call.getArgument(0), widths));
        var entity = mock(EntityManager.class);
        var session = mock(Session.class);
        when(entity.unwrap(Session.class)).thenReturn(session);
        when(session.doReturningWork(any()))
                .thenAnswer(call -> ((ReturningWork<?>) call.getArgument(0)).execute(connection));
        var service = new CdsTargetReceiptService(CdsNativeBaselineTestKeys.keys());
        ReflectionTestUtils.setField(service, "entityManager", entity);
        return service;
    }

    private PreparedStatement statement(String sql, Map<String, Integer> widths) throws Exception {
        statements.add(sql);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(rows);
        if (sql.startsWith("SELECT * FROM `")) {
            String table = sql.substring(15, sql.indexOf('`', 15));
            int width = widths.get(table);
            ResultSetMetaData metadata = mock(ResultSetMetaData.class);
            when(rows.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(width);
            when(metadata.getColumnName(anyInt()))
                    .thenAnswer(
                            call ->
                                    String.format(
                                            "locally_added_clinic_column_%04d",
                                            (Integer) call.getArgument(0)));
            when(metadata.getColumnTypeName(anyInt())).thenReturn("INT");
            when(metadata.getPrecision(anyInt())).thenReturn(10);
        } else if (sql.startsWith("SELECT `")) {
            // One row: every column non-null, four bytes long, with the same digest.
            when(rows.next()).thenReturn(true, false);
            when(rows.getBoolean(anyInt())).thenReturn(false);
            when(rows.getLong(anyInt())).thenReturn(4L);
            when(rows.getString(anyInt())).thenReturn("a".repeat(64));
        } else when(rows.next()).thenReturn(false);
        return statement;
    }
}
