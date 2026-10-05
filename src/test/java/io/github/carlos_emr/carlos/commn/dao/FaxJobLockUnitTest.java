/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("Fax resend row locking")
class FaxJobLockUnitTest {
    @Test
    @DisplayName("should refresh cached fax state under a write lock in the caller transaction")
    void shouldRefreshLockedRow_whenPresent() throws Exception {
        FaxJobDaoImpl dao = new FaxJobDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        FaxJob job = new FaxJob();
        when(dao.entityManager.find(FaxJob.class, 42, LockModeType.PESSIMISTIC_WRITE)).thenReturn(job);
        assertThat(dao.findForUpdate(42)).isSameAs(job);
        var order = inOrder(dao.entityManager);
        order.verify(dao.entityManager).find(FaxJob.class, 42, LockModeType.PESSIMISTIC_WRITE);
        order.verify(dao.entityManager).refresh(job, LockModeType.PESSIMISTIC_WRITE);
        assertThat(FaxJobDaoImpl.class.getMethod("findForUpdate", int.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.MANDATORY);
    }

    @Test
    @DisplayName("should return null without refreshing when the fax row is absent")
    void shouldSkipRefresh_whenAbsent() {
        FaxJobDaoImpl dao = new FaxJobDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        assertThat(dao.findForUpdate(42)).isNull();
        verify(dao.entityManager, never()).refresh(any(), any(LockModeType.class));
    }
}
