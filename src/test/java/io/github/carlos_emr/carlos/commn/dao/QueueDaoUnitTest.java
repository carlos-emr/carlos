/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Queue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class QueueDaoUnitTest {
    private final EntityManager entityManager = mock(EntityManager.class);
    private final QueueDaoImpl dao = new QueueDaoImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
    }

    @Test
    void returnsThePersistedEntityIdWithoutALookup() {
        doAnswer(call -> {
            Queue queue = call.getArgument(0);
            assertThat(queue.getName()).isEqualTo("Doctor's queue");
            ReflectionTestUtils.setField(queue, "id", 42);
            return null;
        }).when(entityManager).persist(any(Queue.class));

        assertThat(dao.addNewQueueAndGetId("Doctor's queue")).isEqualTo(42);

        verify(entityManager).flush();
        verify(entityManager, never()).createQuery(anyString());
    }

    @Test
    void propagatesInsertFailureBeforeReturningAnId() {
        doThrow(new PersistenceException("insert failed")).when(entityManager).persist(any());

        assertThatThrownBy(() -> dao.addNewQueueAndGetId("Mine")).isInstanceOf(PersistenceException.class);
        verify(entityManager, never()).flush();
    }

    @Test
    void propagatesDeferredInsertFailureBeforeReturningAnId() {
        doThrow(new PersistenceException("flush failed")).when(entityManager).flush();

        assertThatThrownBy(() -> dao.addNewQueueAndGetId("Mine")).isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsPersistenceWithoutAnAssignedId() {
        assertThatThrownBy(() -> dao.addNewQueueAndGetId("Mine")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void preservesTheBooleanFailureContract() {
        doThrow(new PersistenceException("insert failed")).when(entityManager).persist(any());

        assertThat(dao.addNewQueue("Mine")).isFalse();
    }
}
