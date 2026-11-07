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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
class CdsLaboratoryResultDaoUnitTest {
    @Test
    void shouldBoundRestrictionLookup_whenPatientHasBlockedResults() {
        EntityManager entity = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<String> query = mock(TypedQuery.class);
        when(entity.createQuery(anyString(), eq(String.class))).thenReturn(query);
        when(query.setParameter("patient", 42)).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("blocked-result"), List.of());
        var dao = new CdsLaboratoryResultDao();
        ReflectionTestUtils.setField(dao, "entityManager", entity);

        assertThat(dao.hasBlockedResult(42)).isTrue();
        var statement = ArgumentCaptor.forClass(String.class);
        verify(entity).createQuery(statement.capture(), eq(String.class));
        assertThat(statement.getValue()).startsWith("select l.id ")
                .contains("l.demographicNo=:patient", "l.sourceBlocked='Y'")
                .doesNotContain("count(");
        verify(query).setMaxResults(1);
        assertThat(dao.hasBlockedResult(42)).isFalse();
    }

    @Test
    void shouldRefuseInvalidPatient_beforeReadingRestrictions() {
        EntityManager entity = mock(EntityManager.class);
        var dao = new CdsLaboratoryResultDao();
        ReflectionTestUtils.setField(dao, "entityManager", entity);
        assertThatThrownBy(() -> dao.hasBlockedResult(0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(entity);
    }
}
