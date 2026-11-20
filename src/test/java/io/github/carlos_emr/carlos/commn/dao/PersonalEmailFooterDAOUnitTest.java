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
import io.github.carlos_emr.carlos.commn.model.UserProperty;
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
    @Test void shouldReadNothing_whenPersistingOrRemovingAnyUserProperty(){
        // Footer bookkeeping must not cost every other property write a query: a footer save
        // classifies the recorded ids itself.
        var entities=mock(EntityManager.class);
        var dao=new UserPropertyDAOImpl();
        ReflectionTestUtils.setField(dao,"entityManager",entities);
        var row=new io.github.carlos_emr.carlos.commn.model.UserProperty();
        row.setName("email_footer");
        row.setProviderNo("101");
        doAnswer(call->{row.setId(7);return null;}).when(entities).persist(row);
        when(entities.contains(row)).thenReturn(true);
        dao.persist(row);
        dao.remove(row);
        verify(entities).persist(row);
        verify(entities).remove(row);
        verify(entities,never()).createQuery(anyString());
        verify(entities,never()).createQuery(anyString(),any());
        verify(entities,never()).createNativeQuery(anyString());
    }
    @SuppressWarnings("unchecked")
    @Test void shouldRecordOnlyRemovalsThatHappen_soAnUnseenRowStaysInFooterSave(){
        var entities=mock(EntityManager.class);
        var dao=new UserPropertyDAOImpl();
        ReflectionTestUtils.setField(dao,"entityManager",entities);
        var unseen=new io.github.carlos_emr.carlos.commn.model.UserProperty();
        unseen.setId(8);
        var removed=new io.github.carlos_emr.carlos.commn.model.UserProperty();
        removed.setId(9);
        when(entities.contains(removed)).thenReturn(true);
        // The unseen row is detached and this transaction's lookup cannot find it.
        var factory=mock(EntityManagerFactory.class);
        var util=mock(PersistenceUnitUtil.class);
        when(entities.getEntityManagerFactory()).thenReturn(factory);
        when(factory.getPersistenceUnitUtil()).thenReturn(util);
        when(util.getIdentifier(unseen)).thenReturn(8);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            dao.remove(unseen);
            dao.remove(removed);
            Object writes=org.springframework.transaction.support.TransactionSynchronizationManager
                    .getResource(ReflectionTestUtils.getField(dao,"personalFooterWritesKey"));
            assertThat((java.util.Set<Object>)ReflectionTestUtils.getField(writes,"deleted")).containsExactly(9);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .unbindResourceIfPossible(ReflectionTestUtils.getField(dao,"personalFooterWritesKey"));
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
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


    // Clinic-wide footer settings (required/off, logo shown) are read from rows with no provider only,
    // so no user's own row of the same name can stand in for the clinic's.
    @Test void shouldReadClinicSetting_fromRowsWithoutAProviderOnly(){
        var entities=mock(EntityManager.class);
        @SuppressWarnings("unchecked") jakarta.persistence.TypedQuery<UserProperty> query=mock(jakarta.persistence.TypedQuery.class);
        when(entities.createQuery(anyString(),eq(UserProperty.class))).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(java.util.List.of());
        var dao=new UserPropertyDAOImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(dao,"entityManager",entities);

        assertThat(dao.findClinicProperty("email_footer_clinic_mode")).isNull();

        verify(entities).createQuery(argThat((String jpql)->jpql.contains("p.name = :name")
                &&jpql.contains("(p.providerNo is null or p.providerNo = '')")&&jpql.contains("order by p.id")),eq(UserProperty.class));
        verify(query).setParameter("name","email_footer_clinic_mode");
        verify(query).setMaxResults(1);
    }
}
