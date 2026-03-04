/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.utility;

import java.sql.Connection;
import java.util.*;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.SynchronizationType;
import javax.persistence.criteria.CriteriaBuilder;

import org.apache.logging.log4j.Logger;
import org.hibernate.*;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.metadata.CollectionMetadata;
import org.hibernate.stat.Statistics;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.ServiceRegistry;

import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;

public class SpringHibernateLocalSessionFactoryBean extends LocalSessionFactoryBean {

    private static final Logger logger = MiscUtils.getLogger();

    public static final Map<Session, StackTraceElement[]> debugMap = Collections.synchronizedMap(new WeakHashMap<Session, StackTraceElement[]>());

    // This is a fake weak hash set, the value is actually ignored, put null or what ever in it.
    private static ThreadLocal<WeakHashMap<Session, Object>> sessions = new ThreadLocal<WeakHashMap<Session, Object>>();

    public static Session trackSession(Session session) {
        Thread currentThread = Thread.currentThread();
        debugMap.put(session, currentThread.getStackTrace());

        WeakHashMap<Session, Object> map = sessions.get();
        if (map == null) {
            map = new WeakHashMap<Session, Object>();
            sessions.set(map);
        }

        map.put(session, null);

        return (session);
    }

    public static void releaseThreadSessions() {
        try {
            WeakHashMap<Session, Object> map = sessions.get();
            if (map != null) {
                for (Session session : map.keySet()) {
                    try {
                        if (session.isOpen()) {
                            session.close();
                            logger.warn("Found lingering hibernate session. Closing session now.");
                        }
                    } catch (Exception e) {
                        logger.error("Error closing hibernate session. (single instance)", e);
                    }
                }

                sessions.remove();
            }
        } catch (Exception e) {
            logger.error("Error closing hibernate sessions. (outter loop)", e);
        }
    }

    /**
     * Session-tracking decorator for SessionFactory.
     *
     * <p>Wraps a real SessionFactory to track opened sessions for leak detection.
     * Delegates all operations to the wrapped factory. Metadata pass-through methods
     * (getAllClassMetadata, getClassMetadata, getCollectionMetadata, getTypeHelper)
     * are deprecated in H5 and retained with deprecation suppression until the
     * Hibernate 6 migration.</p>
     *
     * <p>H6-MIGRATE: When migrating to Spring 6 + Hibernate 6, the base class
     * ({@code LocalSessionFactoryBean}) will move to a different package. The
     * TrackingSessionFactory itself only uses Hibernate's SessionFactory interface
     * and should need minimal changes.</p>
     */
    public static class TrackingSessionFactory implements SessionFactory {
        private final SessionFactory sessionFactory;

        public TrackingSessionFactory(SessionFactory sessionFactory) {
            logger.info("TrackingSessionFactory wrapping: {}", sessionFactory.getClass().getName());
            this.sessionFactory = sessionFactory;
        }

        @Override
        public void close() throws HibernateException {
            sessionFactory.close();
        }

        @Override
        public Map<String, Object> getProperties() {
            return sessionFactory.getProperties();
        }

        // H6-MIGRATE: These metadata methods are removed in Hibernate 6.
        // They are deprecated in H5 but still required by the SessionFactory interface.
        @SuppressWarnings("deprecation")
        public Map getAllClassMetadata() throws HibernateException {
            return sessionFactory.getAllClassMetadata();
        }

        @SuppressWarnings("deprecation")
        public Map getAllCollectionMetadata() throws HibernateException {
            return sessionFactory.getAllCollectionMetadata();
        }

        @SuppressWarnings("deprecation")
        public ClassMetadata getClassMetadata(Class arg0) throws HibernateException {
            return sessionFactory.getClassMetadata(arg0);
        }

        @SuppressWarnings("deprecation")
        public ClassMetadata getClassMetadata(String arg0) throws HibernateException {
            return sessionFactory.getClassMetadata(arg0);
        }

        @SuppressWarnings("deprecation")
        public CollectionMetadata getCollectionMetadata(String arg0) throws HibernateException {
            return sessionFactory.getCollectionMetadata(arg0);
        }

        public Set getDefinedFilterNames() {
            return sessionFactory.getDefinedFilterNames();
        }

        public FilterDefinition getFilterDefinition(String arg0) throws HibernateException {
            return sessionFactory.getFilterDefinition(arg0);
        }

        public Reference getReference() throws NamingException {
            return sessionFactory.getReference();
        }

        @Override
        public Statistics getStatistics() {
            return sessionFactory.getStatistics();
        }

        @Override
        public boolean isClosed() {
            return sessionFactory.isClosed();
        }

        @Override
        public Session openSession() throws HibernateException {
            return (trackSession(sessionFactory.openSession()));
        }

        @Override
        public StatelessSession openStatelessSession() {
            return sessionFactory.openStatelessSession();
        }

        @Override
        public Session getCurrentSession() {
            return sessionFactory.getCurrentSession();
        }

        @Override
        public StatelessSession openStatelessSession(Connection arg0) {
            return sessionFactory.openStatelessSession(arg0);
        }

        // H6-MIGRATE: getTypeHelper() is removed in Hibernate 6.
        // Delegate to wrapped factory instead of self-cast (which caused StackOverflowError).
        @Override
        public TypeHelper getTypeHelper() {
            return sessionFactory.getTypeHelper();
        }

        @Override
        public boolean containsFetchProfileDefinition(String s) {
            return sessionFactory.containsFetchProfileDefinition(s);
        }

        @Override
        public Cache getCache() {
            return sessionFactory.getCache();
        }

        @Override
        public PersistenceUnitUtil getPersistenceUnitUtil() {
            return sessionFactory.getPersistenceUnitUtil();
        }

        @Override
        public void addNamedQuery(String name, javax.persistence.Query query) {
            sessionFactory.addNamedQuery(name, query);
        }

        @Override
        public <T> T unwrap(Class<T> cls) {
            return sessionFactory.unwrap(cls);
        }

        @Override
        public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {
            sessionFactory.addNamedEntityGraph(graphName, entityGraph);
        }

        @Override
        public SessionFactoryOptions getSessionFactoryOptions() {
            return sessionFactory.getSessionFactoryOptions();
        }

        @Override
        public SessionBuilder withOptions() {
            return sessionFactory.withOptions();
        }

        @Override
        public StatelessSessionBuilder withStatelessOptions() {
            return sessionFactory.withStatelessOptions();
        }

        @Override
        public <T> List<EntityGraph<? super T>> findEntityGraphsByType(Class<T> aClass) {
            return sessionFactory.findEntityGraphsByType(aClass);
        }

        @Override
        public EntityManager createEntityManager() {
            return sessionFactory.createEntityManager();
        }

        @Override
        public EntityManager createEntityManager(Map map) {
            return sessionFactory.createEntityManager(map);
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType) {
            return sessionFactory.createEntityManager(synchronizationType);
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
            return sessionFactory.createEntityManager(synchronizationType, map);
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            return sessionFactory.getCriteriaBuilder();
        }

        @Override
        public Metamodel getMetamodel() {
            return sessionFactory.getMetamodel();
        }

        @Override
        public boolean isOpen() {
            return sessionFactory.isOpen();
        }
    }

    @Override
    protected SessionFactory buildSessionFactory(LocalSessionFactoryBuilder sfb) {
        StandardServiceRegistryBuilder serviceRegistryBuilder = new StandardServiceRegistryBuilder()
                .applySettings(sfb.getProperties());
        ServiceRegistry serviceRegistry = serviceRegistryBuilder.build();
        SessionFactory sessionFactory = sfb.buildSessionFactory(serviceRegistry);
        logger.info("Built SessionFactory: {}", sessionFactory);
        return new TrackingSessionFactory(sessionFactory);
    }

}
