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
@DisplayName("Atomic provider routing transition")
class ProviderLabRoutingTransitionUnitTest {
    @Test
    @DisplayName("should return the conditional update count scoped to report, type, provider and NEW status")
    void shouldCountChangedRows_whenLeavingNew() {
        ProviderLabRoutingDaoImpl dao = new ProviderLabRoutingDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class, RETURNS_SELF);
        when(dao.entityManager.createQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(2);
        assertThat(dao.transitionNewRoutingRows(170, "HL7", "999998", 'A')).isEqualTo(2);
        verify(dao.entityManager).createQuery("update ProviderLabRoutingModel x set x.status=?4 "
                + "where x.labNo=?1 and x.labType=?2 and x.providerNo=?3 and x.status='N'");
        verify(query).setParameter(1, 170);
        verify(query).setParameter(2, "HL7");
        verify(query).setParameter(3, "999998");
        verify(query).setParameter(4, "A");
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("should not count or mutate routing rows when the destination remains NEW")
    void shouldSkipTransition_whenDestinationIsNew() {
        ProviderLabRoutingDaoImpl dao = new ProviderLabRoutingDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        assertThat(dao.transitionNewRoutingRows(170, "HL7", "999998", 'N')).isZero();
        verifyNoInteractions(dao.entityManager);
    }
}
