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
    @DisplayName("should atomically lock a report key without rewriting clinical routing rows")
    void shouldLockMissingReportKey_untilOuterTransactionEnds() throws Exception {
        ProviderLabRoutingDaoImpl dao = new ProviderLabRoutingDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class, RETURNS_SELF);
        String sql = "INSERT INTO providerLabRoutingLock (lab_no) VALUES (?1) ON DUPLICATE KEY UPDATE lab_no=VALUES(lab_no)";
        when(dao.entityManager.createNativeQuery(sql)).thenReturn(query);
        dao.lockRoutingReport(170);
        verify(query).setParameter(1, 170);
        verify(query).executeUpdate();
        assertThat(ProviderLabRoutingDaoImpl.class.getMethod("lockRoutingReport", int.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.MANDATORY);
    }

    @Test
    @DisplayName("should use a current locking read and refresh previously managed routing rows")
    void shouldReadCommittedRouting_whenEarlierSnapshotExists() throws Exception {
        ProviderLabRoutingDaoImpl dao = new ProviderLabRoutingDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class, RETURNS_SELF);
        var row = new io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel();
        org.springframework.test.util.ReflectionTestUtils.setField(row, "id", 170);
        when(dao.entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(java.util.List.of(row));
        assertThat(dao.findRoutingForUpdate(170, "HL7", "999998")).containsExactly(row);
        verify(query).setParameter(1, 170);
        verify(query).setParameter(2, "HL7");
        verify(query).setParameter(3, "999998");
        verify(query).setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        verify(dao.entityManager).refresh(row, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertThat(ProviderLabRoutingDaoImpl.class.getMethod("findRoutingForUpdate", int.class, String.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.MANDATORY);
    }

    @Test
    @DisplayName("should return the conditional update count scoped to report, type, provider and NEW status")
    void shouldCountChangedRows_whenLeavingNew() throws Exception {
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
        assertThat(ProviderLabRoutingDaoImpl.class.getMethod("transitionNewRoutingRows", int.class, String.class, String.class, char.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.MANDATORY);
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
