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

import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OutboundEmailArchiveDaoImpl")
@Tag("unit")
@Tag("dao")
class OutboundEmailArchiveDaoImplUnitTest {

    private static final String FIND_FOR_UPDATE_JPQL =
            "SELECT archive FROM OutboundEmailArchive archive WHERE archive.id = :archiveId";

    private OutboundEmailArchiveDaoImpl dao;
    private EntityManager entityManager;
    private TypedQuery<OutboundEmailArchive> query;

    @BeforeEach
    void setUp() {
        dao = new OutboundEmailArchiveDaoImpl();
        entityManager = mock(EntityManager.class);
        query = mock(TypedQuery.class);

        dao.entityManager = entityManager;
    }

    @Test
    void shouldUsePessimisticWriteLock_whenFindingArchiveForUpdate() {
        OutboundEmailArchive archive = new OutboundEmailArchive();
        when(entityManager.createQuery(FIND_FOR_UPDATE_JPQL, OutboundEmailArchive.class)).thenReturn(query);
        when(query.setParameter("archiveId", 888)).thenReturn(query);
        when(query.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(archive));

        OutboundEmailArchive result = dao.findForUpdate(888);

        assertThat(result).isSameAs(archive);
        verify(entityManager).createQuery(FIND_FOR_UPDATE_JPQL, OutboundEmailArchive.class);
        verify(query).setParameter("archiveId", 888);
        verify(query).setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldReturnNull_whenForUpdateArchiveDoesNotExist() {
        when(entityManager.createQuery(FIND_FOR_UPDATE_JPQL, OutboundEmailArchive.class)).thenReturn(query);
        when(query.setParameter("archiveId", 888)).thenReturn(query);
        when(query.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(dao.findForUpdate(888)).isNull();
    }

    @Test
    void shouldReturnNull_whenForUpdateArchiveIdIsNull() {
        assertThat(dao.findForUpdate(null)).isNull();
        verify(entityManager, never()).createQuery(FIND_FOR_UPDATE_JPQL, OutboundEmailArchive.class);
    }
}
