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

import io.github.carlos_emr.carlos.commn.model.ProviderInboxItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the difference between the two inbox-routing entry points: addToProviderInbox swallows a
 * failure, routeToProviderInbox lets it reach a caller whose transaction must then roll back.
 *
 * @since 2026-09-25
 */
@Tag("unit")
@Tag("dao")
@Tag("inbox")
class ProviderInboxRoutingDaoImplUnitTest {
    private ProviderInboxRoutingDaoImpl dao;
    private EntityManager entityManager;

    @BeforeEach
    void setUpDao() {
        dao = new ProviderInboxRoutingDaoImpl();
        entityManager = mock(EntityManager.class);
        dao.entityManager = entityManager;
    }

    @Test
    void shouldPropagateFailure_whenRoutingCannotReachDatabase() {
        when(entityManager.createQuery(anyString())).thenThrow(new PersistenceException("database unavailable"));

        // A handler running inside FileUploadCheck.storeIfNew turns this into a rollback.
        assertThatThrownBy(() -> dao.routeToProviderInbox("999998", 7, "DOC"))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void shouldSwallowFailure_whenAddingToProviderInboxCannotReachDatabase() {
        when(entityManager.createQuery(anyString())).thenThrow(new PersistenceException("database unavailable"));

        // The legacy entry point keeps its contract for callers outside any transaction.
        assertThatCode(() -> dao.addToProviderInbox("999998", 7, "DOC")).doesNotThrowAnyException();
    }

    @Test
    void shouldPersistNewInboxItem_whenRoutingSucceeds() {
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(any(Integer.class), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        dao.routeToProviderInbox("999998", 7, "DOC");

        ArgumentCaptor<ProviderInboxItem> item = ArgumentCaptor.forClass(ProviderInboxItem.class);
        verify(entityManager).persist(item.capture());
        assertThat(item.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(item.getValue().getLabNo()).isEqualTo(7);
        assertThat(item.getValue().getLabType()).isEqualTo("DOC");
        assertThat(item.getValue().getStatus()).isEqualTo(ProviderInboxItem.NEW);
    }
}
