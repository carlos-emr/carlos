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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.dao.SmsConfigDao;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The scheduler's settings listener runs after a commit, while the saving transaction's session is still
 * bound. This checks that {@link SmsConfigService#committedSchedulerEnabled()} answers from the database
 * there, where the ordinary read answers from the session.
 */
@Tag("integration")
@Tag("service")
@Isolated
class SmsConfigCommittedReadIntegrationTest extends CarlosTestBase {
    @Autowired
    private SmsConfigDao dao;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceUnit(unitName = "entityManagerFactory")
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("the committed read sees a later save that the session's own read does not")
    void shouldReadCommittedSetting_whenSessionHoldsAnOlderOne() {
        SmsConfigService service = (SmsConfigService) transactional(new SmsConfigService(dao,
                new SmsProviderClientResolver(List.of()), mock(ApplicationEventPublisher.class),
                mock(SmsConfigAuditRecorder.class)));
        Integer id = null;
        try {
            // The reads below return the first row, so this test needs the table to itself.
            assertThat(service.current()).isEmpty();
            id = commitNewConfig(true);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            // Loads the row into this transaction's session, as a save does.
            assertThat(service.storedSchedulerEnabled()).contains(true);

            commitSchedulerEnabled(id, false);

            assertThat(service.storedSchedulerEnabled()).contains(true);
            assertThat(service.committedSchedulerEnabled()).contains(false);
        } finally {
            if (id != null) {
                try (EntityManager cleanup = entityManagerFactory.createEntityManager()) {
                    cleanup.getTransaction().begin();
                    cleanup.createQuery("DELETE FROM SmsConfig c WHERE c.id = :id")
                            .setParameter("id", id).executeUpdate();
                    cleanup.getTransaction().commit();
                }
            }
        }
    }

    private Integer commitNewConfig(boolean schedulerEnabled) {
        try (EntityManager other = entityManagerFactory.createEntityManager()) {
            other.getTransaction().begin();
            SmsConfig config = new SmsConfig();
            // Sending stays on, so the row changes nothing for anything else that reads the settings.
            config.setEnabled(true);
            config.setSchedulerEnabled(schedulerEnabled);
            config.markUpdated("999998");
            other.persist(config);
            other.getTransaction().commit();
            return config.getId();
        }
    }

    private void commitSchedulerEnabled(Integer id, boolean schedulerEnabled) {
        try (EntityManager other = entityManagerFactory.createEntityManager()) {
            other.getTransaction().begin();
            other.find(SmsConfig.class, id).setSchedulerEnabled(schedulerEnabled);
            other.getTransaction().commit();
        }
    }

    private Object transactional(Object target) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return factory.getProxy();
    }
}
