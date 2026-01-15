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
import org.hibernate.query.Query;
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
 * <p>This DAO provides CRUD operations and specialized queries for managing case management issues
 * associated with patient demographics. It handles issue tracking, linking, and retrieval for the
 * case management module.</p>
 * 
 * <p>The implementation uses direct Hibernate Session access for database operations, following
 * Spring 5.3+ best practices to prepare for Jakarta EE migration.</p>
 * 
 * @since 2.0
 */
@Transactional
public class CaseManagementIssueDAOImpl implements CaseManagementIssueDAO {

    private static Logger log = MiscUtils.getLogger();
    
    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Retrieves the current Hibernate session.
     * 
     * @return the current session bound to the transaction context
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all case management issues for a specific demographic.
     * 
     * @param demographic_no the demographic number as a string
     * @return list of case management issues for the demographic
     */
    @Override
    public List<CaseManagementIssue> getIssuesByDemographic(String demographic_no) {
        Query<CaseManagementIssue> query = getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.demographic_no = :demographicNo",
                CaseManagementIssue.class);
        query.setParameter("demographicNo", Integer.valueOf(demographic_no));
        return query.list();
    }

    /**
     * Retrieves case management issues for a demographic, ordered by resolved status.
     * 
     * @param demographic_no the demographic number
     * @param resolved filter for resolved status (null for all)
     * @return list of case management issues ordered by resolved status
     */
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicOrderActive(Integer demographic_no, Boolean resolved) {
        String hql = "from CaseManagementIssue cmi where cmi.demographic_no = :demographicNo"
                + (resolved != null ? " and cmi.resolved = :resolved" : "") + " order by cmi.resolved";
        Query<CaseManagementIssue> query = getSession().createQuery(hql, CaseManagementIssue.class);
        query.setParameter("demographicNo", demographic_no);
        if (resolved != null) {
            query.setParameter("resolved", resolved);
        }
        return query.list();
    }

    /**
     * Retrieves case management issues associated with a specific note.
     * 
     * @param noteId the note ID
     * @param resolved filter for resolved status (null for all)
     * @return list of case management issues for the note
     */
    @Override
    public List<CaseManagementIssue> getIssuesByNote(Integer noteId, Boolean resolved) {
        String hql = "from CaseManagementIssue cmi where cmi.notes.id = :noteId"
                + (resolved != null ? " and cmi.resolved = :resolved" : "") + " order by cmi.resolved";
        Query<CaseManagementIssue> query = getSession().createQuery(hql, CaseManagementIssue.class);
        query.setParameter("noteId", noteId);
        if (resolved != null) {
            query.setParameter("resolved", resolved);
        }
        return query.list();
    }

    /**
     * Retrieves an Issue entity by case management issue ID.
     * 
     * @param cmnIssueId the case management issue ID
     * @return the Issue entity, or null if not found
     */
    @Override
    public Issue getIssueByCmnId(Integer cmnIssueId) {
        Query<Issue> query = getSession().createQuery(
                "select issue from CaseManagementIssue cmi where cmi.id = :id",
                Issue.class);
        query.setParameter("id", Long.valueOf(cmnIssueId));
        List<Issue> result = query.list();
        if (result.size() > 0)
            return result.get(0);
        return null;
    }

    /**
     * Retrieves a case management issue by issue ID and demographic number.
     * 
     * @param demo the demographic number as a string
     * @param id the issue ID as a string
     * @return the case management issue, or null if not found or multiple results
     */
    @Override
    public CaseManagementIssue getIssuebyId(String demo, String id) {
        Query<CaseManagementIssue> query = getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.issue_id = :issueId and demographic_no = :demographicNo",
                CaseManagementIssue.class);
        query.setParameter("issueId", Long.parseLong(id));
        query.setParameter("demographicNo", Integer.valueOf(demo));
        List<CaseManagementIssue> list = query.list();
        if (list != null && list.size() == 1)
            return list.get(0);

        return null;
    }

    /**
     * Retrieves a case management issue by issue code and demographic number.
     * 
     * @param demo the demographic number as a string
     * @param issueCode the issue code
     * @return the first matching case management issue, or null if not found
     */
    @Override
    public CaseManagementIssue getIssuebyIssueCode(String demo, String issueCode) {
        Query<CaseManagementIssue> query = getSession().createQuery(
                "select cmi from CaseManagementIssue cmi, Issue issue where cmi.issue_id=issue.id and issue.code = :issueCode and cmi.demographic_no = :demographicNo",
                CaseManagementIssue.class);
        query.setParameter("issueCode", issueCode);
        query.setParameter("demographicNo", Integer.valueOf(demo));
        List<CaseManagementIssue> list = query.list();

        if (list.size() > 1) {
            log.error("Expected 1 result got more : " + list.size() + "(" + demo + "," + issueCode + ")");
        }

        if (list.size() == 1 || list.size() > 1)
            return list.get(0);

        return null;
    }

    /**
     * Deletes a case management issue.
     * 
     * @param issue the case management issue to delete
     */
    @Override
    public void deleteIssueById(CaseManagementIssue issue) {
        getSession().delete(issue);
    }

    /**
     * Saves new issues and updates existing issues in the provided list.
     * 
     * <p>Issues with a non-null and positive ID are updated, while new issues are saved.
     * The update date is set to the current time for all issues in the list.</p>
     * 
     * @param issuelist the list of case management issues to save or update
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
     * 
     * <p>The update date is set to the current time before the save/update operation.</p>
     * 
     * @param issue the case management issue to save or update
     */
    @Override
    public void saveIssue(CaseManagementIssue issue) {
        issue.setUpdate_date(new Date());
        getSession().saveOrUpdate(issue);
    }

    /**
     * Retrieves all case management issues marked as certain.
     * 
     * @return list of all certain case management issues
     */
    @Override
    public List<CaseManagementIssue> getAllCertainIssues() {
        Query<CaseManagementIssue> query = getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.certain = true",
                CaseManagementIssue.class);
        return query.list();
    }

    /**
     * Retrieves demographic numbers with issues updated since a specific date for given programs.
     * 
     * <p>Used for integrator functionality to track changes across programs.</p>
     * 
     * @param date the date to filter updates from
     * @param programs the list of programs to check
     * @return list of distinct demographic numbers with updated issues
     */
    @Override
    public List<Integer> getIssuesByProgramsSince(Date date, List<Program> programs) {
        // Validate that all program IDs are valid integers to prevent SQL injection
        List<Integer> programIds = new ArrayList<>(programs.size());
        for (Program p : programs) {
            if (p.getId() == null) {
                throw new IllegalArgumentException("Program ID cannot be null");
            }
            programIds.add(p.getId());
        }
        
        // Build HQL with parameterized IN clause
        Query<Integer> query = getSession().createQuery(
                "select distinct cmi.demographic_no from CaseManagementIssue cmi where cmi.update_date > :date and cmi.program_id in (:programIds)",
                Integer.class);
        query.setParameter("date", date);
        query.setParameter("programIds", programIds);
        return query.list();
    }

    /**
     * Retrieves case management issues for a demographic updated since a specific date.
     * 
     * @param demographic_no the demographic number as a string
     * @param date the date to filter updates from
     * @return list of case management issues updated since the date
     */
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicSince(String demographic_no, Date date) {
        Query<CaseManagementIssue> query = getSession().createQuery(
                "from CaseManagementIssue cmi where cmi.demographic_no = :demographicNo and cmi.update_date > :date",
                CaseManagementIssue.class);
        query.setParameter("demographicNo", Integer.valueOf(demographic_no));
        query.setParameter("date", date);
        return query.list();
    }

    /**
     * Retrieves issue IDs formatted for integrator use.
     * 
     * <p>Converts issue codes and types into composite keys for integration with external systems.</p>
     * 
     * @param facilityId the integrator facility ID
     * @param demographicNo the demographic number
     * @return list of composite primary keys for integrator use
     */
    @Override
    public List<FacilityIdDemographicIssueCompositePk> getIssueIdsForIntegrator(Integer facilityId,
                                                                                Integer demographicNo) {
        Query<Object[]> query = getSession().createQuery(
                "select i.code,i.type from CaseManagementIssue cmi, Issue i where cmi.issue_id = i.id and cmi.demographic_no = :demographicNo",
                Object[].class);
        query.setParameter("demographicNo", demographicNo);
        List<Object[]> rs = query.list();
        List<FacilityIdDemographicIssueCompositePk> results = new ArrayList<>(rs.size());
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
