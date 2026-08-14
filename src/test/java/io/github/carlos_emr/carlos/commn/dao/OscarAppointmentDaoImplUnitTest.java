/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
    void shouldAcquireDatabaseWriteLock_whenFindingAppointmentForUpdate() {
        Appointment appointment = new Appointment();
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, Appointment.class))
                .thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(appointment));

        Appointment result = dao.findForUpdate(42);

        assertThat(result).isSameAs(appointment);
        verify(entityManager).createNativeQuery(FIND_FOR_UPDATE_SQL, Appointment.class);
        verify(query).setParameter(1, 42);
    }

    @Test
    void shouldReturnNull_whenLockedAppointmentDoesNotExist() {
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, Appointment.class))
                .thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(dao.findForUpdate(42)).isNull();
    }

    @Test
    void shouldReturnNullWithoutQuery_whenAppointmentIdIsNull() {
        assertThat(dao.findForUpdate(null)).isNull();
        verify(entityManager, never())
                .createNativeQuery(eq(FIND_FOR_UPDATE_SQL), eq(Appointment.class));
    }
}
