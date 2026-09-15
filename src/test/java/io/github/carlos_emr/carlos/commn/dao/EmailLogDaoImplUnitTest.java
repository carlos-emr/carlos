/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EmailLogDaoImpl Unit Tests")
class EmailLogDaoImplUnitTest extends CarlosUnitTestBase {

    private final EmailLogDaoImpl dao = new EmailLogDaoImpl();

    @Test
    @DisplayName("should ignore non-numeric demographicNo when querying")
    void shouldIgnore_whenDemographicNoIsNonNumeric() {
        Date now = new Date();
        Query query = wireQueryMock();

        dao.getEmailStatusByDateDemographicSenderStatus(now, now, "not-a-number", null, null);

        verify(query).setParameter(1, (Object) null);
    }

    @Test
    @DisplayName("should ignore invalid emailStatus when querying")
    void shouldIgnore_whenEmailStatusIsInvalid() {
        Date now = new Date();
        Query query = wireQueryMock();

        dao.getEmailStatusByDateDemographicSenderStatus(now, now, null, null, "BOUNCED");

        verify(query).setParameter(2, (Object) null);
    }

    @Test
    @DisplayName("should include expected status in atomic status transition")
    void shouldCompareCurrentStatus_whenTransitioningEmailStatus() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        Date timestamp = new Date();
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);

        int updated = dao.transitionEmailStatus(
                42, EmailLog.EmailStatus.PENDING, EmailLog.EmailStatus.SUCCESS,
                "", timestamp);

        assertThat(updated).isEqualTo(1);
        verify(entityManager).createQuery(org.mockito.ArgumentMatchers.contains(
                "WHERE e.id = :id AND e.status = :expectedStatus"));
        verify(query).setParameter("id", 42);
        verify(query).setParameter("expectedStatus", EmailLog.EmailStatus.PENDING);
        verify(query).setParameter("newStatus", EmailLog.EmailStatus.SUCCESS);
        verify(query).setParameter("msg", "");
        verify(query).setParameter("ts", timestamp);
    }

    private Query wireQueryMock() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(Object.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        return query;
    }
}
