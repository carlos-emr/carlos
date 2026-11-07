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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Consent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins the locks {@link ConsentDaoImpl} takes for consent writes. H2 cannot show InnoDB's
 * locking, so these check the requests made of the entity manager.
 *
 * @since 2026-09-28
 */
@Tag("unit")
@DisplayName("Consent write locking")
class ConsentDaoLockingUnitTest {

    @Test
    @DisplayName("should lock the patient's row by primary key, without loading the Demographic entity")
    void shouldLockPatientRowByPrimaryKey_whenLockingForConsentChange() {
        ConsentDaoImpl dao = new ConsentDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(dao.entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(1, 100)).thenReturn(query);

        dao.lockPatientForConsentChange(100);

        verify(dao.entityManager).createNativeQuery(
                "SELECT demographic_no FROM demographic WHERE demographic_no = ?1 FOR UPDATE");
        verify(query).setParameter(1, 100);
        verify(query).getResultList();
    }

    @Test
    @DisplayName("should write-lock the live consent rows it reads for update")
    @SuppressWarnings("unchecked")
    void shouldWriteLockLiveRows_whenReadingForUpdate() {
        ConsentDaoImpl dao = new ConsentDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        TypedQuery<Consent> query = mock(TypedQuery.class);
        when(dao.entityManager.createQuery(anyString(), eq(Consent.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(dao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).isEmpty();

        verify(query).setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("should re-read each locked row, so a row loaded before the lock is not edited stale")
    @SuppressWarnings("unchecked")
    void shouldRereadLockedRows_whenReadingForUpdate() {
        ConsentDaoImpl dao = new ConsentDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        TypedQuery<Consent> query = mock(TypedQuery.class);
        Consent first = new Consent();
        Consent second = new Consent();
        when(dao.entityManager.createQuery(anyString(), eq(Consent.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(first, second));

        // Compared by identity: Consent's equals() needs an id, which an unsaved record lacks.
        assertThat(dao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).hasSize(2);

        verify(dao.entityManager).refresh(same(first));
        verify(dao.entityManager).refresh(same(second));
    }

    @Test
    @DisplayName("should require the caller's transaction for both locks, so neither is released at once")
    void shouldRequireCallersTransaction_forBothLocks() throws NoSuchMethodException {
        assertThat(ConsentDaoImpl.class.getMethod("lockPatientForConsentChange", int.class)
                .getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(ConsentDaoImpl.class.getMethod("findLiveByDemographicAndConsentTypeIdForUpdate", int.class, int.class)
                .getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
    }
}
