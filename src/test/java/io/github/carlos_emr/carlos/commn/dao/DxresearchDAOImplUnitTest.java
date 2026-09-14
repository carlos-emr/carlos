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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DxresearchDAOImpl Unit Tests")
@Tag("unit")
@Tag("dao")
class DxresearchDAOImplUnitTest extends CarlosUnitTestBase {

    private DxresearchDAOImpl dao;
    private Query query;

    @BeforeEach
    void setUp() {
        dao = new DxresearchDAOImpl();
        dao.entityManager = mock(EntityManager.class);
        query = mock(Query.class);

        when(dao.entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    @ParameterizedTest(name = "{0} report binds status {1} as a Character")
    @CsvSource({
        "active,A",
        "resolved,C",
        "deleted,D"
    })
    @DisplayName("should bind Disease Registry report statuses using the entity attribute type")
    void shouldBindStatus_whenStatusSpecificReportIsRequested(String report, char expectedStatus) {
        switch (report) {
            case "active" -> dao.patientRegistedActive(null, List.of("*"));
            case "resolved" -> dao.patientRegistedResolve(null, List.of("*"));
            case "deleted" -> dao.patientRegistedDeleted(null, List.of("*"));
            default -> throw new IllegalArgumentException("Unknown report: " + report);
        }

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(query).setParameter(eq("status"), statusCaptor.capture());
        assertThat(statusCaptor.getValue())
            .isInstanceOf(Character.class)
            .isEqualTo(expectedStatus);
    }
}
