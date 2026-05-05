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
package io.github.carlos_emr.carlos.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Minimal base class for JPA-backed DAOs using {@link EntityManager}.
 *
 * <p>Provides {@link #entityManager()} for subclasses, which is obtained via
 * {@link PersistenceContext} and bound to the active Spring-managed
 * transaction.</p>
 *
 * <p>Historical note: this class replaced a legacy {@code AbstractHibernateDao}
 * base that exposed a Hibernate {@code Session} via {@code currentSession()}.
 * DAOs were migrated mechanically — {@code currentSession()} became
 * {@link #entityManager()} and {@code HqlQueryHelper} became
 * {@link io.github.carlos_emr.carlos.utility.JpqlQueryHelper}. Those legacy
 * helpers have since been removed.</p>
 *
 * <p>Compatible with {@code autowire="byName"} in {@code applicationContext.xml}:
 * since this class has no {@code setSessionFactory} setter, Spring's byName
 * autowiring harmlessly finds no match for the {@code "sessionFactory"} bean.
 * The {@link EntityManager} is injected independently via
 * {@link PersistenceContext}.</p>
 *
 * @since 2026-04-11
 * @see io.github.carlos_emr.carlos.utility.JpqlQueryHelper
 */
public abstract class AbstractJpaDao {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Returns the JPA {@link EntityManager} bound to the active transaction.
     *
     * <p>The EntityManager lifecycle is managed by Spring's transaction
     * infrastructure — no explicit close is needed.</p>
     *
     * @return the current transactional EntityManager
     */
    protected EntityManager entityManager() {
        return entityManager;
    }
}
