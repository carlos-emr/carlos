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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

@Transactional
public class CaseManagementIssueDAOImpl extends AbstractHibernateDao implements CaseManagementIssueDAO {

    private static Logger log = MiscUtils.getLogger();

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographic(String demographic_no) {
        return (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                "from CaseManagementIssue cmi where cmi.demographic_no = ?1",
                Integer.valueOf(demographic_no));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicOrderActive(Integer demographic_no, Boolean resolved) {
        if (resolved != null) {
            return (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                    "from CaseManagementIssue cmi where cmi.demographic_no = ?1 and cmi.resolved = ?2 order by cmi.resolved",
                    demographic_no, resolved);
        } else {
            return (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                    "from CaseManagementIssue cmi where cmi.demographic_no = ?1 order by cmi.resolved",
                    demographic_no);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByNote(Integer noteId, Boolean resolved) {
        if (resolved != null) {
            return (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                    "from CaseManagementIssue cmi where cmi.notes.id = ?1 and cmi.resolved = ?2 order by cmi.resolved",
                    noteId, resolved);
        } else {
            return (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                    "from CaseManagementIssue cmi where cmi.notes.id = ?1 order by cmi.resolved",
                    noteId);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Issue getIssueByCmnId(Integer cmnIssueId) {
        List<Issue> result = (List<Issue>) HqlQueryHelper.find(currentSession(),
                "select issue from CaseManagementIssue cmi where cmi.id = ?1",
                Long.valueOf(cmnIssueId));
        if (result.size() > 0)
            return result.get(0);
        return null;
    }

    @Override
    public CaseManagementIssue getIssuebyId(String demo, String id) {
        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> list = (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                "from CaseManagementIssue cmi where cmi.issue_id = ?1 and cmi.demographic_no = ?2",
                Long.parseLong(id), Integer.valueOf(demo));
        if (list != null && list.size() == 1)
            return list.get(0);

        return null;
    }

    @Override
    public CaseManagementIssue getIssuebyIssueCode(String demo, String issueCode) {
        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> list = (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                "select cmi from CaseManagementIssue cmi, Issue issue where cmi.issue_id=issue.id and issue.code = ?1 and cmi.demographic_no = ?2",
                issueCode, Integer.valueOf(demo));

        if (list.size() > 1) {
            log.error("Expected 1 result got more : " + list.size() + "(" + demo + "," + issueCode + ")");
        }

        if (list.size() == 1 || list.size() > 1)
            return list.get(0);

        return null;
    }

    @Override
    public void deleteIssueById(CaseManagementIssue issue) {
        currentSession().delete(issue);
        return;

    }

    @Override
    public void saveAndUpdateCaseIssues(List<CaseManagementIssue> issuelist) {
        Iterator<CaseManagementIssue> itr = issuelist.iterator();
        while (itr.hasNext()) {
            CaseManagementIssue cmi = itr.next();
            cmi.setUpdate_date(new Date());
            if (cmi.getId() != null && cmi.getId().longValue() > 0) {
                currentSession().update(cmi);
            } else {
                currentSession().save(cmi);
            }
        }

    }

    public void saveIssue(CaseManagementIssue issue) {
        issue.setUpdate_date(new Date());
        currentSession().saveOrUpdate(issue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getAllCertainIssues() {
        return (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                "from CaseManagementIssue cmi where cmi.certain = true");
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getIssuesByProgramsSince(Date date, List<Program> programs) {
        if (programs == null || programs.isEmpty()) {
            return new ArrayList<Integer>();
        }

        List<Integer> programIds = new ArrayList<Integer>();
        for (Program p : programs) {
            programIds.add(p.getId());
        }

        String hql = "select distinct cmi.demographic_no from CaseManagementIssue cmi where cmi.update_date > :updateDate and program_id in (:programIds)";
        Map<String, Object> params = new HashMap<>();
        params.put("updateDate", date);
        params.put("programIds", programIds);
        List<Integer> results = (List<Integer>) HqlQueryHelper.find(currentSession(), hql, params);

        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicSince(String demographic_no, Date date) {
        return (List<CaseManagementIssue>) HqlQueryHelper.find(currentSession(),
                "from CaseManagementIssue cmi where cmi.demographic_no = ?1 and cmi.update_date > ?2",
                Integer.valueOf(demographic_no), date);
    }

}
