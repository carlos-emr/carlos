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
package io.github.carlos_emr.carlos.demographic.cds.security;

import jakarta.persistence.EntityManagerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * An Open-EntityManager-in-View holder can survive several successful read transactions and
 * retain an old account/provider/role. Suspend only that nontransactional view holder before
 * CDS authorization advice begins; the normal transaction manager owns a fresh context.
 * Active transactions/synchronizations retain their existing Spring suspension semantics.
 * Restore the view holder without clearing/detaching another operation's managed entities.
 */
@Aspect @Component @Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class CdsFreshAuthorizationReadAspect {
    private final EntityManagerFactory factory;
    public CdsFreshAuthorizationReadAspect(@Qualifier("entityManagerFactory") EntityManagerFactory factory) { this.factory = factory; }
    @Around("execution(public * io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService.*(..))"
            + " || execution(public * io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService.*(..))")
    public Object freshViewRead(ProceedingJoinPoint operation) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.hasResource(factory)) return operation.proceed();
        Object view = TransactionSynchronizationManager.unbindResource(factory); Throwable primary = null;
        try { return operation.proceed(); }
        catch (Throwable failure) { primary = failure; throw failure; }
        finally {
            Object unretired = TransactionSynchronizationManager.unbindResourceIfPossible(factory);
            try {
                if (unretired != null) {
                    // This boundary is read-only. Never retain a leaked authorization context or
                    // silently let it replace the request's unrelated view context.
                    if (unretired instanceof EntityManagerHolder holder) EntityManagerFactoryUtils.closeEntityManager(holder.getEntityManager());
                    var failure = new IllegalStateException("CDS_AUTHORIZATION_CONTEXT_NOT_RETIRED");
                    if (primary != null) primary.addSuppressed(failure); else throw failure;
                }
            } finally { TransactionSynchronizationManager.bindResource(factory, view); }
        }
    }
}
