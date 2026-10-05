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
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChartUpdateReceiptStoreUnitTest {
    private final EntityManager em = mock(EntityManager.class);
    private final ChartUpdateReceiptStore store = new ChartUpdateReceiptStore();
    ChartUpdateReceiptStoreUnitTest() { ReflectionTestUtils.setField(store, "entityManager", em); }

    private Query stubEngines(List<String> result) {
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString(), eq(String.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(result);
        return query;
    }

    private List<Object> checkedTables(boolean history, boolean legacyChart) {
        Query query = stubEngines(List.of("InnoDB"));
        store.requireTransactionalTables(history, legacyChart);
        ArgumentCaptor<Object> tables = ArgumentCaptor.forClass(Object.class);
        verify(query, atLeastOnce()).setParameter(eq("tableName"), tables.capture());
        return tables.getAllValues();
    }

    @Test void shouldFailClosed_forMissingOrNonTransactionalTables() {
        // Every table a write touches is checked; dropping one would let a partial write look atomic.
        List<Object> history = List.of("clinical_chart_update_receipt", "demographic", "casemgmt_note", "casemgmt_issue",
                "casemgmt_issue_notes", "casemgmt_note_link", "casemgmt_note_lock", "hash_audit");
        assertThat(checkedTables(true, false)).containsExactlyElementsOf(history);
        var legacy = new java.util.ArrayList<>(history);
        legacy.add("eChart");
        assertThat(checkedTables(true, true)).containsExactlyElementsOf(legacy);
        for (boolean legacyChart : new boolean[]{false, true}) {
            assertThat(checkedTables(false, legacyChart))
                    .containsExactly("clinical_chart_update_receipt", "demographic", "tickler", "tickler_link");
        }
        stubEngines(List.of("MyISAM"));
        assertThatThrownBy(() -> store.requireTransactionalTables(false, false)).hasMessageContaining("transactional");
        stubEngines(List.of());
        assertThatThrownBy(() -> store.requireTransactionalTables(false, false)).hasMessageContaining("transactional");
    }

    @Test void shouldRequireCurrentPatientAndSessionLock_whenSavingHistory() {
        var user = mock(LoggedInInfo.class);
        var session = new MockHttpSession();
        when(user.getSession()).thenReturn(session);
        assertThatThrownBy(() -> store.requireNoteLock(user, 3001)).hasMessageContaining("editing lock");
        var lock = new CasemgmtNoteLock();
        lock.setId(7L);
        lock.setDemographicNo(3001);
        lock.setSessionId(session.getId());
        session.setAttribute("casemgmtNoteLock3001", lock);
        var current = new CasemgmtNoteLock();
        current.setId(7L);
        current.setDemographicNo(3001);
        current.setSessionId(session.getId());
        when(em.find(CasemgmtNoteLock.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(current);
        store.requireNoteLock(user, 3001);
        current.setSessionId("another-session");
        assertThatThrownBy(() -> store.requireNoteLock(user, 3001)).hasMessageContaining("lock changed");
        current.setSessionId(session.getId());
        lock.setSessionId("another-session");
        assertThatThrownBy(() -> store.requireNoteLock(user, 3001)).hasMessageContaining("lock changed");
        lock.setSessionId(session.getId());
        current.setDemographicNo(3002);
        assertThatThrownBy(() -> store.requireNoteLock(user, 3001)).hasMessageContaining("lock changed");
    }
}
