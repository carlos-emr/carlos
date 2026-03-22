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

package io.github.carlos_emr.carlos.services;

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Service manager for administering clinical issue definitions in the
 * CARLOS EMR case management module.
 *
 * <p>Clinical issues represent standardized medical problems, conditions,
 * or concerns that can be associated with patient case management notes.
 * This manager provides CRUD operations for the issue administration
 * interface used by clinical staff.</p>
 *
 * @see io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO
 * @see io.github.carlos_emr.carlos.casemgmt.model.Issue
 * @since 2026-03-17
 */
public class IssueAdminManager {
    private static Logger log = MiscUtils.getLogger();

    @Autowired
    private IssueDAO dao;

    /**
     * Retrieves all clinical issue definitions.
     *
     * @return List of all Issue records in the system
     */
    public List<Issue> getIssueAdmins() {
        return dao.getIssues();
    }

    /**
     * Retrieves a single clinical issue by its identifier.
     * Logs a warning if the issue is not found.
     *
     * @param issueAdminId String the unique identifier of the issue (parsed to Long)
     * @return Issue the matching issue record, or null if not found
     */
    public Issue getIssueAdmin(String issueAdminId) {
        Issue issueAdmin = dao.getIssue(Long.valueOf(issueAdminId));
        if (issueAdmin == null) {
            log.warn("UserId '" + issueAdminId + "' not found in database.");
        }
        return issueAdmin;
    }

    /**
     * Persists a new or updated clinical issue definition.
     *
     * @param issueAdmin Issue the issue record to save
     * @return Issue the saved issue instance
     */
    public Issue saveIssueAdmin(Issue issueAdmin) {
        dao.saveIssue(issueAdmin);
        return issueAdmin;
    }

    /**
     * Removes a clinical issue definition by its identifier.
     *
     * @param issueAdminId String the unique identifier of the issue to remove (parsed to Long)
     * @deprecated Direct deletion of clinical issues may impact existing case management
     *             notes that reference this issue. Consider deactivation instead.
     */
    @Deprecated
    public void removeIssueAdmin(String issueAdminId) {
        dao.delete(Long.valueOf(issueAdminId));
    }
}
