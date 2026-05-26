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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SQL construction safeguards in {@link Hl7LinkDao}.
 *
 * @since 2026-05-26
 */
@DisplayName("Hl7LinkDao Unit Tests")
@Tag("unit")
@Tag("dao")
@Tag("billing-bc")
class Hl7LinkDaoUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should bind providerNo when provider contains malicious input")
    void shouldBindProviderNo_whenProviderContainsMaliciousInput() {
        Hl7LinkDao dao = new Hl7LinkDao();
        Query query = wireNativeQueryMock(dao);
        String providerNo = "DOC01' OR '1'='1";

        dao.findReports(null, null, providerNo, "patient_name", "search");

        String sql = captureNativeSql(dao);
        assertThat(sql).contains("demographic.provider_no = :providerNo");
        assertThat(sql).doesNotContain(providerNo);
        assertThat(sql).contains("ORDER BY hl7_pid.patient_name");
        verify(query).setParameter("providerNo", providerNo);
    }

    @Test
    @DisplayName("should bind blank providerNo when unassigned patients sentinel is selected")
    void shouldBindBlankProviderNo_whenUnassignedPatientsSentinelIsSelected() {
        Hl7LinkDao dao = new Hl7LinkDao();
        Query query = wireNativeQueryMock(dao);

        dao.findReports(null, null, "-UAP", "pid_id", "search");

        verify(query).setParameter("providerNo", "");
    }

    @Test
    @DisplayName("should skip providerNo binding when unlinked labs sentinel is selected")
    void shouldSkipProviderNoBinding_whenUnlinkedLabsSentinelIsSelected() {
        Hl7LinkDao dao = new Hl7LinkDao();
        Query query = wireNativeQueryMock(dao);

        dao.findReports(null, null, "-ULL", "pid_id", "search");

        String sql = captureNativeSql(dao);
        assertThat(sql).doesNotContain(":providerNo");
        verify(query, never()).setParameter(eq("providerNo"), any());
    }

    @Test
    @DisplayName("should skip providerNo binding when all provider labs sentinel is selected")
    void shouldSkipProviderNoBinding_whenAllProviderLabsSentinelIsSelected() {
        Hl7LinkDao dao = new Hl7LinkDao();
        Query query = wireNativeQueryMock(dao);

        dao.findReports(null, null, "-APL", "pid_id", "search");

        String sql = captureNativeSql(dao);
        assertThat(sql).doesNotContain(":providerNo");
        verify(query, never()).setParameter(eq("providerNo"), any());
    }

    @Test
    @DisplayName("should bind date range when dates are provided")
    void shouldBindDateRange_whenDatesAreProvided() {
        Hl7LinkDao dao = new Hl7LinkDao();
        Query query = wireNativeQueryMock(dao);

        Date start = Timestamp.valueOf("2026-05-25 14:30:00");
        Date end = Timestamp.valueOf("2026-05-26 06:15:00");

        dao.findReports(start, end, "-APL", "date_time", "search");

        String sql = captureNativeSql(dao);
        assertThat(sql).contains("hl7_message.date_time >= :startDate");
        assertThat(sql).contains("hl7_message.date_time <= :endDate");
        assertThat(sql).doesNotContain("2026-05-25");
        assertThat(sql).doesNotContain("2026-05-26");
        verify(query).setParameter("startDate", Timestamp.valueOf("2026-05-25 00:00:00"));
        verify(query).setParameter("endDate", Timestamp.valueOf("2026-05-26 23:59:59"));
    }

    @Test
    @DisplayName("should use default order by when order by is not allowed")
    void shouldUseDefaultOrderBy_whenOrderByIsNotAllowed() {
        Hl7LinkDao dao = new Hl7LinkDao();
        Query query = wireNativeQueryMock(dao);

        dao.findReports(null, null, "-APL", "pid_id,(SELECT SLEEP(5))", "search");

        String sql = captureNativeSql(dao);
        assertThat(sql).contains("ORDER BY hl7_pid.pid_id");
        assertThat(sql).doesNotContain("SLEEP");
        verify(query, never()).setParameter(eq("providerNo"), any());
    }

    /**
     * Wires a mocked persistence layer so tests can inspect SQL construction and
     * parameter binding without executing a database query.
     *
     * @param dao DAO instance receiving the mocked {@link EntityManager}
     * @return mocked {@link Query} used for parameter-binding assertions
     */
    private Query wireNativeQueryMock(Hl7LinkDao dao) {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        return query;
    }

    /**
     * Captures the SQL string passed to {@link EntityManager#createNativeQuery(String)}
     * so tests can assert that request input is not interpolated into SQL structure.
     *
     * @param dao DAO instance whose mocked {@link EntityManager} is inspected
     * @return generated native SQL string
     */
    private String captureNativeSql(Hl7LinkDao dao) {
        EntityManager entityManager = (EntityManager) ReflectionTestUtils.getField(dao, "entityManager");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCaptor.capture());
        return sqlCaptor.getValue();
    }
}
