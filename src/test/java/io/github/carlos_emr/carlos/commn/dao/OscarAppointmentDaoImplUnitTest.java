/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OscarAppointmentDaoImpl")
@Tag("unit")
@Tag("dao")
class OscarAppointmentDaoImplUnitTest {

    private static final String FIND_FOR_UPDATE_SQL =
            "SELECT * FROM appointment WHERE appointment_no = ?1 FOR UPDATE";

    private OscarAppointmentDaoImpl dao;
    private EntityManager entityManager;
    private Query query;

    @BeforeEach
    void setUp() {
        dao = new OscarAppointmentDaoImpl();
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        dao.entityManager = entityManager;
    }

    @Test
    void shouldRefreshCachedStateWithAWriteLock_whenFindingAppointmentForUpdate() {
        Appointment appointment = new Appointment();
        appointment.setStatus("t");
        appointment.setDemographicNo(101);
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, Appointment.class))
                .thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(appointment));
        doAnswer(call -> {
            appointment.setStatus("H");
            appointment.setDemographicNo(202);
            return null;
        }).when(entityManager).refresh(appointment, LockModeType.PESSIMISTIC_WRITE);

        Appointment result = dao.findForUpdate(42);

        assertThat(result).isSameAs(appointment);
        verify(entityManager).refresh(appointment, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertThat(result.getStatus()).isEqualTo("H");
        assertThat(result.getDemographicNo()).isEqualTo(202);
        verify(entityManager).createNativeQuery(FIND_FOR_UPDATE_SQL, Appointment.class);
        verify(query).setParameter(1, 42);
        var order = inOrder(query, entityManager);
        order.verify(query).getResultList();
        order.verify(entityManager).refresh(appointment, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager, never()).refresh(appointment);
    }

    @Test
    void shouldPropagateLockingRefreshFailure_withoutReturningTheCachedAppointment() {
        Appointment appointment = new Appointment();
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, Appointment.class)).thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(appointment));
        var failure = new PessimisticLockException("locking refresh failed");
        doThrow(failure).when(entityManager).refresh(appointment, LockModeType.PESSIMISTIC_WRITE);

        assertThatThrownBy(() -> dao.findForUpdate(42)).isSameAs(failure);
        verify(entityManager, never()).refresh(appointment);
    }

    @Test
    void shouldReturnNull_whenLockedAppointmentDoesNotExist() {
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, Appointment.class))
                .thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(dao.findForUpdate(42)).isNull();
        verify(entityManager, never()).refresh(any(), any(LockModeType.class));
    }

    @Test
    void shouldReturnNullWithoutQuery_whenAppointmentIdIsNull() {
        assertThat(dao.findForUpdate(null)).isNull();
        verify(entityManager, never())
                .createNativeQuery(eq(FIND_FOR_UPDATE_SQL), eq(Appointment.class));
        verify(entityManager, never()).refresh(any(), any(LockModeType.class));
    }
}
