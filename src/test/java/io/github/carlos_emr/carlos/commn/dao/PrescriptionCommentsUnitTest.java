/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("Exact prescription comment verification")
class PrescriptionCommentsUnitTest {
    @Test
    @DisplayName("should compare fresh scalar comments literally rather than using database collation")
    void shouldCompareExactText_fromScalarQuery() {
        PrescriptionDaoImpl dao = new PrescriptionDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class, RETURNS_SELF);
        when(dao.entityManager.createQuery("SELECT p.comments FROM Prescription p WHERE p.id = ?1")).thenReturn(query);
        for (String stored : java.util.Arrays.asList("Clinical [text] $1", "clinical [text] $1", "Clinical [text] $1 ", null)) {
            when(query.getResultList()).thenReturn(java.util.Collections.singletonList(stored));
            assertThat(dao.hasExactComments(123, "Clinical [text] $1"))
                    .isEqualTo("Clinical [text] $1".equals(stored));
        }
        verify(query, times(4)).setParameter(1, 123);
    }

    @Test
    @DisplayName("should refuse an idempotent success when the prescription no longer exists")
    void shouldRejectMatch_whenRowIsAbsent() {
        PrescriptionDaoImpl dao = new PrescriptionDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class, RETURNS_SELF);
        when(dao.entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(java.util.List.of());
        assertThat(dao.hasExactComments(123, "")).isFalse();
    }
}
