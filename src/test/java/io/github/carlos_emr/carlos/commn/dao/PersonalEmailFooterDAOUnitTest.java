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
import io.github.carlos_emr.carlos.commn.model.Provider;
import jakarta.persistence.*;
import java.sql.SQLException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("unit") @Tag("email")
class PersonalEmailFooterDAOUnitTest {
    @Test void shouldMapOnlyStrictSnapshotChangedRow_toOptimisticConflict(){
        var entities=mock(EntityManager.class);
        var dao=new UserPropertyDAOImpl();
        ReflectionTestUtils.setField(dao,"entityManager",entities);
        var changed=new PersistenceException(new SQLException("FAKE changed row","HY000",1020));
        when(entities.find(Provider.class,"101",LockModeType.PESSIMISTIC_WRITE)).thenThrow(changed);
        assertThatThrownBy(()->dao.lockPersonalEmailFooterOwner("101"))
                .isInstanceOf(OptimisticLockException.class).hasCause(changed);
        var other=new PersistenceException(new SQLException("FAKE different error","HY000",1205));
        doThrow(other).when(entities).find(Provider.class,"101",LockModeType.PESSIMISTIC_WRITE);
        assertThatThrownBy(()->dao.lockPersonalEmailFooterOwner("101")).isSameAs(other);
    }
    @Test void shouldDeleteCurrentOwnerRow_evenWhenEarlierViewCannotFindItsId(){
        var entities=mock(EntityManager.class);
        var query=mock(Query.class);
        var dao=new UserPropertyDAOImpl();
        ReflectionTestUtils.setField(dao,"entityManager",entities);
        when(entities.find(io.github.carlos_emr.carlos.commn.model.UserProperty.class,7)).thenReturn(null);
        when(entities.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        dao.deletePersonalEmailFooterRow("101",7);
        verify(query).setParameter("id",7);
        verify(query).setParameter("name","email_footer");
        verify(query).setParameter("provider","101");
        verify(query).executeUpdate();
    }
    @Test void shouldRefuseOtherOwnerBeforeDelete(){
        var entities=mock(EntityManager.class);
        var dao=new UserPropertyDAOImpl();
        ReflectionTestUtils.setField(dao,"entityManager",entities);
        var row=new io.github.carlos_emr.carlos.commn.model.UserProperty();
        row.setName("email_footer");
        row.setProviderNo("202");
        when(entities.find(io.github.carlos_emr.carlos.commn.model.UserProperty.class,7)).thenReturn(row);
        assertThatThrownBy(()->dao.deletePersonalEmailFooterRow("101",7)).isInstanceOf(IllegalArgumentException.class);
        verify(entities,never()).createNativeQuery(anyString());
        verify(entities,never()).detach(any());
    }

    @Test void shouldUpdateCurrentOwnerRow_evenWhenEarlierViewCannotFindItsId(){
        var entities=mock(EntityManager.class);
        var query=mock(Query.class);
        var dao=new UserPropertyDAOImpl();
        ReflectionTestUtils.setField(dao,"entityManager",entities);
        when(entities.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        var row=new io.github.carlos_emr.carlos.commn.model.UserProperty();
        row.setId(7);
        row.setName("email_footer");
        row.setProviderNo("101");
        row.setValue("Current personal");
        dao.savePersonalEmailFooterRow("101",row);
        verify(query).setParameter("id",7);
        verify(query).setParameter("name","email_footer");
        verify(query).setParameter("provider","101");
        verify(query).setParameter("value","Current personal");
        verify(query).executeUpdate();
        verify(entities,never()).merge(any());
    }

}
