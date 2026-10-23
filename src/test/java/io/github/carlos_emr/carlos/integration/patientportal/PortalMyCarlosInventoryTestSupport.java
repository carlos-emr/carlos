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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Unit transaction lifecycle only; actual MariaDB/read-view proof remains mandatory. */
final class PortalMyCarlosInventoryTestSupport {
    static PortalMyCarlosKeyInventoryService service(PropertyDao properties, ProviderLabRoutingDao coordination) {
        return fixture(properties, coordination).service;
    }
    static Fixture fixture(PropertyDao properties, ProviderLabRoutingDao coordination) {
        return new Fixture(properties, coordination);
    }
    static final class Fixture {
        final EntityManagerFactory factory = mock(EntityManagerFactory.class);
        final Manager manager = new Manager(factory);
        final PortalMyCarlosInventoryTransactions transactions;
        final PortalMyCarlosKeyInventoryService service;
        Fixture(PropertyDao properties, ProviderLabRoutingDao coordination) {
            transactions = new PortalMyCarlosInventoryTransactions(manager, factory, coordination);
            service = new PortalMyCarlosKeyInventoryService(new PortalMyCarlosKeyInventoryWorker(properties), transactions);
        }
    }
    static final class Manager extends AbstractPlatformTransactionManager {
        final EntityManagerFactory factory;
        final List<EntityManager> entityManagers = new ArrayList<>();
        int begins;
        int commits;
        int rollbacks;
        Runnable onBegin = () -> {};
        Runnable onCommit = () -> {};
        Runnable onRollback = () -> {};
        boolean reuseEntityManager;
        Manager(EntityManagerFactory factory) { this.factory = factory; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            begins++;
            EntityManager em;
            if (reuseEntityManager && !entityManagers.isEmpty()) em = entityManagers.getFirst();
            else {
                em = mock(EntityManager.class);
                when(em.isOpen()).thenReturn(true);
                entityManagers.add(em);
            }
            var holder = new EntityManagerHolder(em);
            holder.setSynchronizedWithTransaction(true);
            TransactionSynchronizationManager.bindResource(factory, holder);
            onBegin.run();
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; onCommit.run(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; onRollback.run(); }
        @Override protected void doCleanupAfterCompletion(Object transaction) {
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.unbindResource(factory);
            holder.getEntityManager().close();
        }
    }
}
