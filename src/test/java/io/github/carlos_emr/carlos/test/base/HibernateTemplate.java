/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.test.base;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

/**
 * Minimal replacement for Spring's {@code org.springframework.orm.hibernate5.HibernateTemplate}
 * which was removed in Spring Framework 7.0.
 *
 * <p>Provides the subset of HibernateTemplate methods used by CARLOS EMR integration tests,
 * delegating directly to the Hibernate {@link SessionFactory#getCurrentSession()} API.
 * Uses Hibernate 7 JPA-standard methods ({@code persist}, {@code merge}, {@code remove})
 * instead of the removed Hibernate-specific methods ({@code save}, {@code update},
 * {@code saveOrUpdate}, {@code delete}).</p>
 *
 * <p>This class is test-infrastructure only and should NOT be used in production code.</p>
 *
 * @since 2026-03-15
 */
public class HibernateTemplate {

    private SessionFactory sessionFactory;

    public HibernateTemplate() {
    }

    public HibernateTemplate(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * No-op in this replacement. The original HibernateTemplate used this to control
     * write operation checks, but we delegate directly to Session which doesn't need it.
     */
    public void setCheckWriteOperations(boolean checkWriteOperations) {
        // No-op — Session handles this internally
    }

    public void flush() {
        sessionFactory.getCurrentSession().flush();
    }

    public void clear() {
        sessionFactory.getCurrentSession().clear();
    }

    /**
     * Persist a new entity. Replaces the removed {@code Session.save()} in Hibernate 7.
     * Returns null since {@code persist()} is void — callers that need the generated ID
     * should access the entity's ID field after persist + flush.
     */
    public Serializable save(Object entity) {
        sessionFactory.getCurrentSession().persist(entity);
        return null;
    }

    /**
     * Merge a detached entity. Replaces the removed {@code Session.update()} in Hibernate 7.
     */
    public void update(Object entity) {
        sessionFactory.getCurrentSession().merge(entity);
    }

    /**
     * Persist or merge an entity. Replaces the removed {@code Session.saveOrUpdate()}
     * in Hibernate 7. Uses {@code merge()} which handles both new and detached entities.
     */
    public void saveOrUpdate(Object entity) {
        sessionFactory.getCurrentSession().merge(entity);
    }

    /**
     * Remove an entity. Replaces the removed {@code Session.delete()} in Hibernate 7.
     */
    public void delete(Object entity) {
        sessionFactory.getCurrentSession().remove(entity);
    }

    public void evict(Object entity) {
        sessionFactory.getCurrentSession().evict(entity);
    }

    /**
     * Find an entity by ID. Uses {@code Session.find()} (JPA standard) since
     * {@code Session.get()} is deprecated in Hibernate 7.
     */
    public <T> T get(Class<T> entityClass, Serializable id) {
        return sessionFactory.getCurrentSession().find(entityClass, id);
    }

    @SuppressWarnings("unchecked")
    public List<?> find(String queryString, Object... values) {
        Session session = sessionFactory.getCurrentSession();
        var query = session.createQuery(queryString);
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                query.setParameter(i + 1, values[i]);
            }
        }
        return query.list();
    }

    /**
     * Execute a callback within the current Hibernate session.
     *
     * @param <T> the return type
     * @param action the callback function
     * @return the result of the callback
     */
    public <T> T execute(Function<Session, T> action) {
        return action.apply(sessionFactory.getCurrentSession());
    }
}
