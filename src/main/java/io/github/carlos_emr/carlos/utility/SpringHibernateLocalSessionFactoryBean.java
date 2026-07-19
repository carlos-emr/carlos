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
import org.hibernate.engine.spi.SessionFactoryImplementor;
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

    public static class TrackingSessionFactory implements SessionFactory {
        private SessionFactory sessionFactory = null;

        public TrackingSessionFactory(SessionFactory sessionFactory) {
            System.out.println("TrackingSessionFactory constructor called");
            this.sessionFactory = sessionFactory;
        }

        public void close() throws HibernateException {
            sessionFactory.close();
        }

        @Override
        public Map<String, Object> getProperties() {
            return Collections.emptyMap();
        }

//		public void evict(Class arg0, Serializable arg1) throws HibernateException {
//	        sessionFactory.evict(arg0, arg1);
//        }
//
//		public void evict(Class arg0) throws HibernateException {
//	        sessionFactory.evict(arg0);
//        }
//
//		public void evictCollection(String arg0, Serializable arg1) throws HibernateException {
//	        sessionFactory.evictCollection(arg0, arg1);
//        }
//
//		public void evictCollection(String arg0) throws HibernateException {
//	        sessionFactory.evictCollection(arg0);
//        }
//
//		public void evictEntity(String arg0, Serializable arg1) throws HibernateException {
//	        sessionFactory.evictEntity(arg0, arg1);
//        }
//
//		public void evictEntity(String arg0) throws HibernateException {
//	        sessionFactory.evictEntity(arg0);
//        }
//
//		public void evictQueries() throws HibernateException {
//	        sessionFactory.evictQueries();
//        }
//
//		public void evictQueries(String arg0) throws HibernateException {
//	        sessionFactory.evictQueries(arg0);
//        }

        public Map getAllClassMetadata() throws HibernateException {
            return sessionFactory.getAllClassMetadata();
        }

        public Map getAllCollectionMetadata() throws HibernateException {
            return sessionFactory.getAllCollectionMetadata();
        }

        public ClassMetadata getClassMetadata(Class arg0) throws HibernateException {
            return sessionFactory.getClassMetadata(arg0);
        }

        public ClassMetadata getClassMetadata(String arg0) throws HibernateException {
            return sessionFactory.getClassMetadata(arg0);
        }

        public CollectionMetadata getCollectionMetadata(String arg0) throws HibernateException {
            return sessionFactory.getCollectionMetadata(arg0);
        }

        // public Session getCurrentSession() throws HibernateException {
        //     return(trackSession(sessionFactory.getCurrentSession()));
        // }

        public Set getDefinedFilterNames() {
            return sessionFactory.getDefinedFilterNames();
        }

        public FilterDefinition getFilterDefinition(String arg0) throws HibernateException {
            return sessionFactory.getFilterDefinition(arg0);
        }

        public Reference getReference() throws NamingException {
            return sessionFactory.getReference();
        }

        public Statistics getStatistics() {
            return sessionFactory.getStatistics();
        }

        public boolean isClosed() {
            return sessionFactory.isClosed();
        }

        @Override
        public Session openSession() throws HibernateException {
            return (trackSession(sessionFactory.openSession()));
        }

		/*public Session openSession(Connection arg0, Interceptor arg1) {
	        return(trackSession(sessionFactory.openSession(arg0, arg1)));
        }

		public Session openSession(Connection arg0) {
	        return(trackSession(sessionFactory.openSession(arg0)));
        }

		public Session openSession(Interceptor arg0) throws HibernateException {
			return(trackSession(sessionFactory.openSession(arg0)));
        }*/

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

        @Override
        public TypeHelper getTypeHelper() {
            return ((SessionFactoryImplementor) this).getTypeHelper();
        }

        @Override
        public boolean containsFetchProfileDefinition(String s) {
            return false;
        }

        @Override
        public Cache getCache() {
            return null;
        }

        @Override
        public PersistenceUnitUtil getPersistenceUnitUtil() {
            return null;
        }

        @Override
        public void addNamedQuery(String name, javax.persistence.Query query) {

        }

        @Override
        public <T> T unwrap(Class<T> cls) {
            return null;
        }

        @Override
        public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {

        }

//		@Override
//		public SessionFactoryOptions getSessionFactoryOptions() {
//			// TODO Auto-generated method stub
//			return null;
//		}

        @Override
        public SessionFactoryOptions getSessionFactoryOptions() {
            return null;
        }

        @Override
        public SessionBuilder withOptions() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public StatelessSessionBuilder withStatelessOptions() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public <T> List<EntityGraph<? super T>> findEntityGraphsByType(Class<T> aClass) {
            return Collections.emptyList();
        }

        @Override
        public EntityManager createEntityManager() {
            return null;
        }

        @Override
        public EntityManager createEntityManager(Map map) {
            return null;
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType) {
            return null;
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
            return null;
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            return null;
        }

        @Override
        public Metamodel getMetamodel() {
            return null;
        }

        @Override
        public boolean isOpen() {
            return false;
        }
    }
	
	/*@Override
	public SessionFactory newSessionFactory(Configuration config)
	{
		SessionFactory sf=super.newSessionFactory(config);
		
		return(new TrackingSessionFactory(sf));
	}*/

    @Override
    protected SessionFactory buildSessionFactory(LocalSessionFactoryBuilder sfb) {
        StandardServiceRegistryBuilder serviceRegistryBuilder = new StandardServiceRegistryBuilder()
                .applySettings(sfb.getProperties());
        ServiceRegistry serviceRegistry = serviceRegistryBuilder.build();
        SessionFactory sessionFactory = sfb.buildSessionFactory(serviceRegistry);
        System.out.println("Output of building session factory: " + sessionFactory);
        return new TrackingSessionFactory(sessionFactory);
    }


}
