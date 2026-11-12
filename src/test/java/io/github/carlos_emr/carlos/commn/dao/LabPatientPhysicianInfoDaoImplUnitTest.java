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
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * The text of the CML version-chain query (issue #4568), for the part of its contract that no row-level test
 * can observe: which order it asks the database for.
 *
 * <p>One ABCD file is one {@code labReportInformation} header, so every report in the file ties on print date and
 * time, and an H2 or MariaDB table scan may happen to return such ties in id order today and in another order
 * after the next index change. {@code LabVersionChain.olderThan} treats whatever precedes the acknowledged report
 * as older and files it, so the order of a tie is a clinical decision that must not be left to the planner.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("LabPatientPhysicianInfoDaoImpl version chain query")
@Tag("unit")
@Tag("dao")
@Tag("lab")
class LabPatientPhysicianInfoDaoImplUnitTest {

    private static String versionChainQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        LabPatientPhysicianInfoDaoImpl dao = new LabPatientPhysicianInfoDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);

        dao.findLabServiceDatesByLabId(42);

        ArgumentCaptor<String> hql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(hql.capture());
        return hql.getValue();
    }

    @Test
    @DisplayName("should order by print date and time and break a tie by report id, the order the reports were imported")
    void shouldBreakPrintTies_byReportId() {
        assertThat(versionChainQuery()).endsWith("ORDER BY tr.printDate, tr.printTime, lpp.id");
    }
}
