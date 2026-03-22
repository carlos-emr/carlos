/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.web;

import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.exception.UserSessionNotFoundException;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import io.github.carlos_emr.carlos.managers.UserSessionManagerImpl;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * HTTP session lifecycle listener for the CARLOS EMR application.
 *
 * <p>Handles session creation and destruction events. On session destruction,
 * releases any case management note locks held by the session and unregisters
 * the user session from the {@link UserSessionManager}.
 *
 * @since 2001-01-01
 */
public class OscarSessionListener implements HttpSessionListener {

    /**
     * {@inheritDoc}
     *
     * <p>Logs the creation of a new session including its session ID.
     */
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        MiscUtils.getLogger().info("Creating new OSCAR session.");
        MiscUtils.getLogger().info("Session id: " + se.getSession().getId());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        String id = se.getSession().getId();
        MiscUtils.getLogger().info("session is being destroyed - " + id);

        CasemgmtNoteLockDao casemgmtNoteLockDao = SpringUtils.getBean(CasemgmtNoteLockDao.class);

        for (CasemgmtNoteLock lock : casemgmtNoteLockDao.findBySession(id)) {
            MiscUtils.getLogger().info("removing note locks for this session - " + lock);

            casemgmtNoteLockDao.remove(lock.getId());
        }

		HttpSession session = se.getSession();
		Integer userSecurityCode = (Integer) session.getAttribute(UserSessionManagerImpl.KEY_USER_SECURITY_CODE);
		if (userSecurityCode != null) {
			try {
				UserSessionManager userSessionManager = SpringUtils.getBean(UserSessionManager.class);
				userSessionManager.unregisterUserSession(userSecurityCode);
			} catch (UserSessionNotFoundException e) {
				MiscUtils.getLogger().warn("Failed to unregister session on destroy: {}", e.getMessage());
			}
		}
    }

}
