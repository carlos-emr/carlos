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
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.caisi_integrator.ws.CodeType;
import io.github.carlos_emr.carlos.caisi_integrator.ws.FacilityIdDemographicIssueCompositePk;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class CaseManagementIssueDAOImpl extends HibernateDaoSupport implements CaseManagementIssueDAO {

    private static Logger log = MiscUtils.getLogger();

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographic(String demographic_no) {
        return (List<CaseManagementIssue>) this.getHibernateTemplate().find(
                "from CaseManagementIssue cmi where cmi.demographic_no = ?1",
                new Object[]{Integer.valueOf(demographic_no)});
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicOrderActive(Integer demographic_no, Boolean resolved) {
        return (List<CaseManagementIssue>) getHibernateTemplate().find(
                "from CaseManagementIssue cmi where cmi.demographic_no = ?1 "
                        + (resolved != null ? " and cmi.resolved=" + resolved : "") + " order by cmi.resolved",
                new Object[]{demographic_no});
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByNote(Integer noteId, Boolean resolved) {
        return (List<CaseManagementIssue>) getHibernateTemplate().find(
                "from CaseManagementIssue cmi where cmi.notes.id = ?1 "
                        + (resolved != null ? " and cmi.resolved=" + resolved : "") + " order by cmi.resolved",
                new Object[]{noteId});
    }

    @SuppressWarnings("unchecked")
    @Override
    public Issue getIssueByCmnId(Integer cmnIssueId) {
        List<Issue> result = (List<Issue>) getHibernateTemplate().find(
                "select issue from CaseManagementIssue cmi where cmi.id = ?1",
                new Object[]{Long.valueOf(cmnIssueId)});
        if (result.size() > 0)
            return result.get(0);
        return null;
    }

    @Override
    public CaseManagementIssue getIssuebyId(String demo, String id) {
        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> list = (List<CaseManagementIssue>) this.getHibernateTemplate().find(
                "from CaseManagementIssue cmi where cmi.issue_id = ?1 and demographic_no = ?2",
                new Object[]{Long.parseLong(id), Integer.valueOf(demo)});
        if (list != null && list.size() == 1)
            return list.get(0);

        return null;
    }

    @Override
    public CaseManagementIssue getIssuebyIssueCode(String demo, String issueCode) {
        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> list = (List<CaseManagementIssue>) this.getHibernateTemplate().find(
                "select cmi from CaseManagementIssue cmi, Issue issue where cmi.issue_id=issue.id and issue.code = ?1 and cmi.demographic_no = ?2",
                new Object[]{issueCode, Integer.valueOf(demo)});

        if (list.size() > 1) {
            log.error("Expected 1 result got more : " + list.size() + "(" + demo + "," + issueCode + ")");
        }

        if (list.size() == 1 || list.size() > 1)
            return list.get(0);

        return null;
    }

    @Override
    public void deleteIssueById(CaseManagementIssue issue) {
        getHibernateTemplate().delete(issue);
        return;

    }

    @Override
    public void saveAndUpdateCaseIssues(List<CaseManagementIssue> issuelist) {
        Iterator<CaseManagementIssue> itr = issuelist.iterator();
        while (itr.hasNext()) {
            CaseManagementIssue cmi = itr.next();
            cmi.setUpdate_date(new Date());
            if (cmi.getId() != null && cmi.getId().longValue() > 0) {
                getHibernateTemplate().update(cmi);
            } else {
                getHibernateTemplate().save(cmi);
            }
        }

    }

    public void saveIssue(CaseManagementIssue issue) {
        issue.setUpdate_date(new Date());
        getHibernateTemplate().saveOrUpdate(issue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getAllCertainIssues() {
        return (List<CaseManagementIssue>) getHibernateTemplate()
                .find("from CaseManagementIssue cmi where cmi.certain = true");
    }

    // for integrator
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

        Session session = currentSession();
        String hql = "select distinct cmi.demographic_no from CaseManagementIssue cmi where cmi.update_date > :updateDate and program_id in (:programIds)";
        List<Integer> results = session.createQuery(hql)
                .setParameter("updateDate", date)
                .setParameterList("programIds", programIds)
                .list();

        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicSince(String demographic_no, Date date) {
        return (List<CaseManagementIssue>) this.getHibernateTemplate().find(
                "from CaseManagementIssue cmi where cmi.demographic_no = ?1 and cmi.update_date > ?2",
                new Object[]{Integer.valueOf(demographic_no), date});
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<FacilityIdDemographicIssueCompositePk> getIssueIdsForIntegrator(Integer facilityId,
                                                                                Integer demographicNo) {
        List<Object[]> rs = (List<Object[]>) this.getHibernateTemplate().find(
                "select i.code,i.type from CaseManagementIssue cmi, Issue i where cmi.issue_id = i.id and cmi.demographic_no = ?1",
                new Object[]{demographicNo});
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
