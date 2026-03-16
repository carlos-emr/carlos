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

import java.util.*;

import org.apache.logging.log4j.Logger;
import org.hibernate.*;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.stat.Statistics;
import org.springframework.orm.jpa.hibernate.LocalSessionFactoryBean;

/**
 * Session-tracking SessionFactory wrapper for Hibernate session leak detection.
 *
 * <p>Wraps a real SessionFactory to track opened sessions. Delegates all
 * operations to the wrapped factory.</p>
 *
 * <p>Note: This class extends Spring's LocalSessionFactoryBean from the
 * {@code org.springframework.orm.jpa.hibernate} package (Spring 7.0+).
 * Previously in the {@code hibernate5} package, it was relocated as part
 * of Spring Framework 7.0's Hibernate ORM 7.x alignment.</p>
 */
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

}
