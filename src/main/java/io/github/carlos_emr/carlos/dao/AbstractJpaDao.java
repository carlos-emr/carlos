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
 * Minimal base class for DAOs migrated from {@link AbstractHibernateDao} to JPA
 * {@link EntityManager}.
 *
 * <p>Provides {@link #entityManager()} as the JPA counterpart to
 * {@code AbstractHibernateDao.currentSession()}, enabling a mechanical
 * migration: replace {@code currentSession()} calls with {@code entityManager()}
 * and {@code HqlQueryHelper} calls with {@code JpqlQueryHelper}.</p>
 *
 * <p>Compatible with {@code autowire="byName"} in {@code applicationContext.xml}:
 * since this class has no {@code setSessionFactory} setter, Spring's byName
 * autowiring harmlessly finds no match for the {@code "sessionFactory"} bean.
 * The {@link EntityManager} is injected independently via
 * {@link PersistenceContext}.</p>
 *
 * @since 2026-04-11
 * @see AbstractHibernateDao
 * @see io.github.carlos_emr.carlos.utility.JpqlQueryHelper
 */
public abstract class AbstractJpaDao {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Returns the JPA {@link EntityManager} bound to the active transaction.
     *
     * <p>Drop-in replacement for {@code AbstractHibernateDao.currentSession()}.
     * The EntityManager lifecycle is managed by Spring's transaction
     * infrastructure — no explicit close is needed.</p>
     *
     * @return the current transactional EntityManager
     */
    protected EntityManager entityManager() {
        return entityManager;
    }
}
