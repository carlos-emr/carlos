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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DemographicImportDaoUnitTest extends CarlosUnitTestBase {
    private DemographicDaoImpl dao;
    private EntityManager entities;
    private ApplicationEventPublisher publisher;

    @BeforeEach
    void prepare() {
        dao = new DemographicDaoImpl();
        entities = mock(EntityManager.class);
        publisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(dao, "entityManager", entities);
        dao.setApplicationEventPublisher(publisher);
    }

    @Test
    void shouldPersistWithoutRegistrationEvents_forNewHistoricalPatient() {
        var patient = new Demographic();
        patient.setFirstName("Historical");
        patient.setLastName("Import");
        doAnswer(invocation -> {
            patient.setDemographicNo(803);
            return null;
        }).when(entities).persist(patient);

        dao.persistImportedDemographic(patient);

        var order = inOrder(entities);
        order.verify(entities).persist(patient);
        order.verify(entities).flush();
        order.verifyNoMoreInteractions();
        assertThat(patient.getDemographicNo()).isEqualTo(803);
        assertThat(patient.getFirstName()).isEqualTo("Historical");
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldRefuseAssignedIdentity_whenImportWouldMergeAChart() {
        var patient = new Demographic();
        patient.setDemographicNo(803);
        assertThatIllegalArgumentException().isThrownBy(() -> dao.persistImportedDemographic(patient));
        verifyNoInteractions(entities, publisher);
    }

    @Test
    void shouldRefuseMissingPatient_beforeAccessingPersistence() {
        assertThatIllegalArgumentException().isThrownBy(() -> dao.persistImportedDemographic(null));
        verifyNoInteractions(entities, publisher);
    }

    @Test
    void shouldRefuseClinicalMutation_withReadOnlyContext() {
        boolean previous = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        try {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            assertThatIllegalStateException().isThrownBy(() -> dao.persistImportedDemographic(new Demographic()));
        } finally {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(previous);
        }
        verifyNoInteractions(entities, publisher);
    }

    @Test
    void shouldRequireExistingTransaction_whenInvokedThroughSpringProxy() {
        DataSource source = mock(DataSource.class);
        var transaction = new TransactionInterceptor();
        transaction.setTransactionManager(new DataSourceTransactionManager(source));
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(dao);
        factory.addAdvice(transaction);
        DemographicDao participating = (DemographicDao) factory.getProxy();

        assertThatThrownBy(() -> participating.persistImportedDemographic(new Demographic()))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(source, entities, publisher);
    }
}
