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

import java.util.concurrent.ThreadFactory;

/**
 * A {@link ThreadFactory} that creates daemon threads with a specified name and priority.
 *
 * <p>All threads created by this factory are daemon threads, meaning they will not
 * prevent JVM shutdown. Used for background tasks such as scheduled jobs and
 * cache maintenance.
 *
 * @since 2026-03-17
 */
public final class DeamonThreadFactory implements ThreadFactory {

    private String threadName;
    private int threadPriority;

    /**
     * Creates a new factory that produces daemon threads with the given name and priority.
     *
     * @param threadName     String the name to assign to created threads
     * @param threadPriority int the thread priority (e.g., {@link Thread#NORM_PRIORITY})
     */
    public DeamonThreadFactory(String threadName, int threadPriority) {
        this.threadName = threadName;
        this.threadPriority = threadPriority;
    }

    /**
     * Creates a new daemon thread with the configured name and priority.
     *
     * @param r Runnable the task to execute in the new thread
     * @return Thread a new daemon thread ready to run the given task
     */
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, threadName);
        thread.setDaemon(true);
        thread.setPriority(threadPriority);
        return (thread);
    }

}
