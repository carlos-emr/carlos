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
package io.github.carlos_emr.carlos.log;

import java.util.concurrent.ExecutorService;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;

/**
 * Exposes {@link LogAction}'s package-private test hooks to tests in other packages, such as
 * container tests whose audit rows are written from servlet request threads, where a static
 * mock cannot reach.
 */
public final class LogActionTestHooks {

    private LogActionTestHooks() {
    }

    /** Routes audit writes to {@code dao}; {@code null} restores the Spring lookup. */
    public static void useOscarLogDao(OscarLogDao dao) {
        LogAction.setOscarLogDaoForTesting(dao);
    }

    /** Runs asynchronous audit writes on {@code executor}. */
    public static void useExecutorService(ExecutorService executor) {
        LogAction.setExecutorServiceForTesting(executor);
    }

    /** Replaces the audit executor with a fresh default one. */
    public static void resetExecutorService() {
        LogAction.resetExecutorServiceForTesting();
    }
}
