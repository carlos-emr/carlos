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

package io.github.carlos_emr.carlos.log;

import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;

/**
 * Runnable task that persists an {@link OscarLog} entry synchronously and releases
 * database resources afterward.
 *
 * <p>This class is package-private and intended to be used exclusively by
 * {@link LogAction} for asynchronous log persistence via an executor service.
 * After persisting the log entry, it releases all thread-local database resources
 * via {@link DbConnectionFilter#releaseAllThreadDbResources()}.</p>
 *
 * @see LogAction
 * @see OscarLog
 * @since 2026-03-17
 */
class AddLogExecutorTask implements Runnable {

    private OscarLog oscarLog;

    /**
     * Constructs a new task to persist the specified log entry.
     *
     * @param oscarLog OscarLog the log entry to persist
     */
    public AddLogExecutorTask(OscarLog oscarLog) {
        this.oscarLog = oscarLog;
    }

    /**
     * Persists the log entry synchronously and releases thread-local database resources.
     */
    public void run() {
        try {
            LogAction.addLogSynchronous(oscarLog);
        } finally {
            DbConnectionFilter.releaseAllThreadDbResources();
        }
    }
}
