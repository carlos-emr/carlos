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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.EFormReportTool;

@Tag("unit")
@Tag("fast")
@DisplayName("EForm Report Tool record count unit tests")
class EFormReportToolRecordCountUnitTest {

    @Test
    @DisplayName("should return record count when native query produces a Long")
    void shouldReturnRecordCount_whenNativeQueryProducesLong() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery("select count(*) from ERT_safe")).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(0L));

        TestEFormReportToolDao dao = new TestEFormReportToolDao(entityManager);
        EFormReportTool reportTool = new EFormReportTool();
        reportTool.setTableName("ERT_safe");

        assertThat(dao.getNumRecords(reportTool)).isZero();
    }

    private static final class TestEFormReportToolDao extends EFormReportToolDaoImpl {

        private TestEFormReportToolDao(EntityManager entityManager) {
            this.entityManager = entityManager;
        }
    }
}
