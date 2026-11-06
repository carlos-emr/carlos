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
package io.github.carlos_emr.carlos.mds.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The Inbox counts and patient list built by {@link CategoryData} must not drop patients whose HIN
 * is NULL when the HIN search field is blank; the result rows already include them.
 *
 * @since 2026-10-01
 */
@Tag("unit")
@Tag("lab")
@DisplayName("CategoryData patient-search HIN filter")
class CategoryDataPatientSearchUnitTest extends CarlosUnitTestBase {

    private final List<String> statements = new ArrayList<>();
    private final List<List<Object>> parameters = new ArrayList<>();
    private EntityManager entityManager;
    private Query query;

    @BeforeEach
    void captureQueries() {
        registerMock(SystemPreferencesDao.class, mock(SystemPreferencesDao.class));
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        entityManager = mock(EntityManager.class);
        registerMock(EntityManagerFactory.class, factory);
        when(factory.createEntityManager()).thenReturn(entityManager);
        query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        var dao = new io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao(mock(SystemPreferencesDao.class));
        org.springframework.test.util.ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        registerMock(io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.class, dao);
        when(entityManager.createNativeQuery(anyString(), eq(Tuple.class))).thenAnswer(invocation -> {
            statements.add(invocation.getArgument(0));
            return query;
        });
    }

    private static CategoryData search(String healthNumber) {
        return new CategoryData("Synthetic", "", healthNumber, true, false, "", "N", "all", null, null);
    }

    private void runPatientSearchQueries(CategoryData data) throws Exception {
        data.getLabCountForPatientSearch();
        data.getAbnormalCount(true);
        data.getDocumentCountForPatientSearch();
        data.getHRMDocumentCountForPatient();
    }

    @Test
    @DisplayName("should not filter on HIN when the HIN field is blank")
    void shouldOmitHinCondition_whenHinFieldIsBlank() throws Exception {
        runPatientSearchQueries(search(""));

        assertThat(statements).hasSize(4).allSatisfy(sql -> assertThat(sql).doesNotContain("d.hin"));

    }

    @Test
    @DisplayName("should match the searched HIN when the HIN field is filled")
    void shouldMatchHin_whenHinFieldIsFilled() throws Exception {
        runPatientSearchQueries(search("12345"));

        assertThat(statements).hasSize(4);
        assertThat(statements.subList(0, 3)).allSatisfy(sql -> assertThat(sql).contains("AND d.hin like ?"));
        assertThat(statements.get(3)).contains("AND d.hin LIKE :patientHealthNumber");
        var values = ArgumentCaptor.forClass(Object.class);
        verify(query, org.mockito.Mockito.atLeast(3)).setParameter(org.mockito.ArgumentMatchers.anyInt(), values.capture());
        assertThat(values.getAllValues()).contains("%12345%");
    }
}
