//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
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
 * Modifications made by Magenta Health in 2024.
 */

package ca.openosp.openo.casemgmt.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.caisi_integrator.ws.CodeType;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdDemographicIssueCompositePk;
import ca.openosp.openo.casemgmt.model.CaseManagementIssue;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object implementation for CaseManagementIssue entities.
 *
 * Provides database operations for managing case management issues, including
 * retrieval by demographic, note association, and issue code. Supports integrator
 * functionality for cross-facility data exchange.
 *
 * This implementation uses direct Hibernate SessionFactory injection rather than
 * the deprecated HibernateDaoSupport pattern to maintain Spring 6 compatibility.
 *
 * @since 2026-01-23
 */
@Transactional
public class CaseManagementIssueDAOImpl implements CaseManagementIssueDAO {

    private static Logger log = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the SessionFactory.
     *
     * @return Session the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all case management issues for a specific demographic.
     *
     * @param demographic_no String the demographic number
     * @return List&lt;CaseManagementIssue&gt; list of case management issues for the demographic
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographic(String demographic_no) {
        return getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.demographic_no = :demographicNo")
                .setParameter("demographicNo", Integer.valueOf(demographic_no))
                .list();
    }

    /**
     * Retrieves case management issues for a demographic, optionally filtered by resolution status.
     * Results are ordered by resolution status.
     *
     * @param demographic_no Integer the demographic number
     * @param resolved Boolean filter for resolved status (null for all issues)
     * @return List&lt;CaseManagementIssue&gt; list of case management issues ordered by resolved status
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicOrderActive(Integer demographic_no, Boolean resolved) {
        return getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.demographic_no = :demographicNo "
                        + (resolved != null ? " and cmi.resolved = :resolved" : "") + " order by cmi.resolved")
                .setParameter("demographicNo", demographic_no)
                .setParameter("resolved", resolved)
                .list();
    }

    /**
     * Retrieves case management issues associated with a specific note.
     * Results can be filtered by resolution status and are ordered by resolved status.
     *
     * @param noteId Integer the note ID
     * @param resolved Boolean filter for resolved status (null for all issues)
     * @return List&lt;CaseManagementIssue&gt; list of case management issues for the note
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByNote(Integer noteId, Boolean resolved) {
        return getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.notes.id = :noteId "
                        + (resolved != null ? " and cmi.resolved = :resolved" : "") + " order by cmi.resolved")
                .setParameter("noteId", noteId)
                .setParameter("resolved", resolved)
                .list();
    }

    /**
     * Retrieves an Issue entity by case management issue ID.
     *
     * @param cmnIssueId Integer the case management issue ID
     * @return Issue the associated Issue entity, or null if not found
     */
    @SuppressWarnings("unchecked")
    @Override
    public Issue getIssueByCmnId(Integer cmnIssueId) {
        List<Issue> result = getSession().createQuery(
                "select issue from CaseManagementIssue cmi where cmi.id = :cmnIssueId")
                .setParameter("cmnIssueId", Long.valueOf(cmnIssueId))
                .list();
        if (result.size() > 0)
            return result.get(0);
        return null;
    }

    /**
     * Retrieves a case management issue by issue ID and demographic number.
     *
     * @param demo String the demographic number
     * @param id String the issue ID
     * @return CaseManagementIssue the matching issue, or null if not found or multiple matches exist
     */
    @Override
    public CaseManagementIssue getIssuebyId(String demo, String id) {
        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> list = getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.issue_id = :issueId and demographic_no = :demographicNo")
                .setParameter("issueId", Long.parseLong(id))
                .setParameter("demographicNo", Integer.valueOf(demo))
                .list();
        if (list != null && list.size() == 1)
            return list.get(0);

        return null;
    }

    /**
     * Retrieves a case management issue by issue code and demographic number.
     * Logs an error if multiple results are found, but returns the first match.
     *
     * @param demo String the demographic number
     * @param issueCode String the issue code
     * @return CaseManagementIssue the matching issue, or null if not found
     */
    @Override
    public CaseManagementIssue getIssuebyIssueCode(String demo, String issueCode) {
        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> list = getSession().createQuery(
                "select cmi from CaseManagementIssue cmi, Issue issue where cmi.issue_id=issue.id and issue.code = :issueCode and cmi.demographic_no = :demographicNo")
                .setParameter("issueCode", issueCode)
                .setParameter("demographicNo", Integer.valueOf(demo))
                .list();

        if (list.size() > 1) {
            log.error("Expected 1 result got more : " + list.size() + "(" + demo + "," + issueCode + ")");
        }

        if (list.size() == 1 || list.size() > 1)
            return list.get(0);

        return null;
    }

    /**
     * Deletes a case management issue from the database.
     *
     * @param issue CaseManagementIssue the issue to delete
     */
    @Override
    public void deleteIssueById(CaseManagementIssue issue) {
        getSession().delete(issue);
    }

    /**
     * Saves or updates a list of case management issues.
     * Updates the update_date timestamp for each issue before persisting.
     *
     * @param issuelist List&lt;CaseManagementIssue&gt; the list of issues to save or update
     */
    @Override
    public void saveAndUpdateCaseIssues(List<CaseManagementIssue> issuelist) {
        Iterator<CaseManagementIssue> itr = issuelist.iterator();
        while (itr.hasNext()) {
            CaseManagementIssue cmi = itr.next();
            cmi.setUpdate_date(new Date());
            if (cmi.getId() != null && cmi.getId().longValue() > 0) {
                getSession().update(cmi);
            } else {
                getSession().save(cmi);
            }
        }
    }

    /**
     * Saves or updates a single case management issue.
     * Updates the update_date timestamp before persisting.
     *
     * @param issue CaseManagementIssue the issue to save or update
     */
    public void saveIssue(CaseManagementIssue issue) {
        issue.setUpdate_date(new Date());
        getSession().saveOrUpdate(issue);
    }

    /**
     * Retrieves all case management issues marked as certain.
     *
     * @return List&lt;CaseManagementIssue&gt; list of all certain issues
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getAllCertainIssues() {
        return getSession().createQuery("from CaseManagementIssue cmi where cmi.certain = true")
                .list();
    }

    /**
     * Retrieves distinct demographic numbers with issues updated since a given date for specific programs.
     * Used for integrator functionality to identify demographics with recent changes.
     *
     * @param date Date the date threshold for updates
     * @param programs List&lt;Program&gt; list of programs to filter by
     * @return List&lt;Integer&gt; list of distinct demographic numbers
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getIssuesByProgramsSince(Date date, List<Program> programs) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Program p : programs) {
            if (i++ > 0)
                sb.append(",");
            sb.append(p.getId());
        }
        List<Integer> results = getSession().createQuery(
                "select distinct cmi.demographic_no from CaseManagementIssue cmi where cmi.update_date > :updateDate and program_id in ("
                        + sb.toString() + ")")
                .setParameter("updateDate", date)
                .list();

        return results;
    }

    /**
     * Retrieves case management issues for a demographic updated since a given date.
     *
     * @param demographic_no String the demographic number
     * @param date Date the date threshold for updates
     * @return List&lt;CaseManagementIssue&gt; list of issues updated since the specified date
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicSince(String demographic_no, Date date) {
        return getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.demographic_no = :demographicNo and cmi.update_date > :updateDate")
                .setParameter("demographicNo", Integer.valueOf(demographic_no))
                .setParameter("updateDate", date)
                .list();
    }

    /**
     * Retrieves issue composite primary keys for integrator functionality.
     * Constructs composite keys containing facility ID, demographic ID, issue code, and code type
     * for cross-facility data exchange.
     *
     * @param facilityId Integer the facility ID
     * @param demographicNo Integer the demographic number
     * @return List&lt;FacilityIdDemographicIssueCompositePk&gt; list of composite keys for integrator use
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<FacilityIdDemographicIssueCompositePk> getIssueIdsForIntegrator(Integer facilityId,
                                                                                Integer demographicNo) {
        List<Object[]> rs = getSession().createQuery(
                "select i.code,i.type from CaseManagementIssue cmi, Issue i where cmi.issue_id = i.id and cmi.demographic_no = :demographicNo")
                .setParameter("demographicNo", demographicNo)
                .list();
        List<FacilityIdDemographicIssueCompositePk> results = new ArrayList<FacilityIdDemographicIssueCompositePk>();
        for (Object[] item : rs) {
            FacilityIdDemographicIssueCompositePk key = new FacilityIdDemographicIssueCompositePk();
            key.setIntegratorFacilityId(facilityId);
            key.setCaisiDemographicId(demographicNo);
            key.setIssueCode((String) item[0]);

            if ("icd9".equals(item[1])) {
                key.setCodeType(CodeType.ICD_9);
            } else if ("icd10".equals(item[1])) {
                key.setCodeType(CodeType.ICD_10);
            } else {
                key.setCodeType(CodeType.CUSTOM_ISSUE);
            }
            results.add(key);
        }
        return results;
    }

}
