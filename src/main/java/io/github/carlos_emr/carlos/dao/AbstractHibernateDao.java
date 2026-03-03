/**
 * Copyright (c) 2026. CARLOS EMR. All rights reserved.
 *
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
 */
package io.github.carlos_emr.carlos.dao;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Minimal base class replacing Spring's {@code HibernateDaoSupport} for the
 * Hibernate 5 to 6 migration.
 *
 * <p>Provides {@link #currentSession()} via an injected {@link SessionFactory},
 * matching the same method signature that {@code HibernateDaoSupport} offered.
 * This class has no dependency on the {@code org.springframework.orm.hibernate5}
 * package, which will not exist when Spring drops Hibernate 5 support.</p>
 *
 * <p>Compatible with {@code autowire="byName"} in {@code applicationContext.xml}:
 * the {@link #setSessionFactory(SessionFactory)} setter matches the bean named
 * {@code "sessionFactory"}, so Spring auto-wires it without any XML changes.</p>
 *
 * @since 2026-02-27
 */
public abstract class AbstractHibernateDao {

    private SessionFactory sessionFactory;

    /**
     * Injects the Hibernate {@link SessionFactory}.
     *
     * <p>Named {@code setSessionFactory} to match the {@code autowire="byName"}
     * convention in {@code applicationContext.xml}, where the bean is named
     * {@code "sessionFactory"}.</p>
     *
     * @param sessionFactory the SessionFactory to use for obtaining sessions
     */
    @Autowired
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * Returns the Hibernate {@link SessionFactory} for subclasses that need
     * direct factory access (e.g., opening a new session for background work).
     *
     * @return the injected SessionFactory
     */
    protected SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * Returns the current Hibernate {@link Session} bound to the active transaction.
     *
     * <p>Drop-in replacement for {@code HibernateDaoSupport.currentSession()}.
     * The session lifecycle is managed by Spring's transaction infrastructure
     * — no explicit session close or {@code releaseSession()} call is needed.</p>
     *
     * @return the current transactional Session
     */
    protected Session currentSession() {
        return sessionFactory.getCurrentSession();
    }
}
