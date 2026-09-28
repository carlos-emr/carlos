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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChartUpdateReceiptStoreUnitTest {
    private final EntityManager em = mock(EntityManager.class);
    private final ChartUpdateReceiptStore store = new ChartUpdateReceiptStore();
    ChartUpdateReceiptStoreUnitTest() { ReflectionTestUtils.setField(store, "entityManager", em); }

    @Test void failsClosedForMissingOrNonTransactionalTables() {
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString(), eq(String.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("InnoDB"));
        store.requireTransactionalTables(true, true);
        verify(query).setParameter("tableName", "casemgmt_note_lock");
        verify(query).setParameter("tableName", "eChart");
        when(query.getResultList()).thenReturn(List.of("MyISAM"));
        assertThatThrownBy(() -> store.requireTransactionalTables(false, false)).hasMessageContaining("transactional");
        when(query.getResultList()).thenReturn(List.of());
        assertThatThrownBy(() -> store.requireTransactionalTables(false, false)).hasMessageContaining("transactional");
    }

    @Test void historyRequiresCurrentPatientAndSessionEditingLock() {
        var user = mock(LoggedInInfo.class);
        var session = new MockHttpSession();
        when(user.getSession()).thenReturn(session);
        assertThatThrownBy(() -> store.requireNoteLock(user, 3001)).hasMessageContaining("editing lock");
        var lock = new CasemgmtNoteLock();
        lock.setId(7L);
        lock.setDemographicNo(3001);
        lock.setSessionId(session.getId());
        session.setAttribute("casemgmtNoteLock3001", lock);
        when(em.find(CasemgmtNoteLock.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(lock);
        store.requireNoteLock(user, 3001);
        lock.setSessionId("another-session");
        assertThatThrownBy(() -> store.requireNoteLock(user, 3001)).hasMessageContaining("lock changed");
        lock.setSessionId(session.getId());
        lock.setDemographicNo(3002);
        assertThatThrownBy(() -> store.requireNoteLock(user, 3001)).hasMessageContaining("lock changed");
    }
}
