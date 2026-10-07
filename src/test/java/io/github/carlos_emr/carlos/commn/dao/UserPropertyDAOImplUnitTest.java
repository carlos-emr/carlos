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

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locking the email footer rows by key (follow-up to #3981): a row another transaction removed
 * between the read and the lock must surface as a concurrency failure ("please try again"), not as
 * a missing-object error that the pages would show as a server error.
 *
 * @since 2026-10-07
 */
@DisplayName("UserPropertyDAOImpl row locks")
@Tag("unit")
@Tag("fast")
@Tag("dao")
class UserPropertyDAOImplUnitTest {

    @Test
    @DisplayName("should report a row removed before it could be locked as a concurrency failure")
    void shouldThrowOptimisticLock_whenRowRemovedBeforeLock() {
        UserProperty row = new UserProperty();
        row.setId(7);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row));
        // What Hibernate raises when refresh finds the row gone.
        doThrow(new EntityNotFoundException("No row with the given identifier exists"))
                .when(entityManager).refresh(row, LockModeType.PESSIMISTIC_WRITE);
        UserPropertyDAOImpl dao = new UserPropertyDAOImpl();
        dao.entityManager = entityManager;

        assertThatThrownBy(() -> dao.lockProviderProperties("email_footer"))
                .isInstanceOfSatisfying(OptimisticLockException.class, e -> assertThat(
                        new HibernateJpaDialect().translateExceptionIfPossible(e))
                        .isInstanceOf(ConcurrencyFailureException.class));
        verify(entityManager).refresh(row, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("should lock and re-read each clinic row by its key")
    void shouldLockEachRowByKey_forClinicRows() {
        UserProperty first = new UserProperty();
        first.setId(3);
        UserProperty second = new UserProperty();
        second.setId(9);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(first, second));
        UserPropertyDAOImpl dao = new UserPropertyDAOImpl();
        dao.entityManager = entityManager;

        assertThat(dao.lockClinicProperties("email_footer_clinic_default")).containsExactly(first, second);
        verify(entityManager).refresh(first, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(second, LockModeType.PESSIMISTIC_WRITE);
    }
}
