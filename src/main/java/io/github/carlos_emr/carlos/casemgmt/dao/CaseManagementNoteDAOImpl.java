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

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementSearchBean;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

@Transactional
public class CaseManagementNoteDAOImpl extends AbstractHibernateDao implements CaseManagementNoteDAO {

    private static Logger log = MiscUtils.getLogger();

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> findAll() {
        log.warn(
                "A METHOD THAT IS LIKELY TO CAUSE A CRASH HAS BEEN INVOKED. PLEASE LIMIT THE USE OF THIS METHOD, AS IT'S LIKELY TO EXHAUST MEMORY AND MAY LEAD TO A SERVER CRASH. CONSIDER PAGINATING THE INVOCATION INSTEAD");
        return (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), "FROM CaseManagementNote");
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getEditors(CaseManagementNote note) {
        String uuid = note.getUuid();
        if (uuid == null) return Collections.emptyList();
        String hql = "select distinct p from Provider p, CaseManagementNote cmn where p.ProviderNo = cmn.providerNo and cmn.uuid = ?1";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), hql, uuid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getAllEditors(String demographicNo) {
        if (demographicNo == null) return Collections.emptyList();
        String hql = "select distinct p from Provider p, CaseManagementNote cmn where p.ProviderNo = cmn.providerNo and cmn.demographic_no = ?1";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), hql, demographicNo);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getHistory(CaseManagementNote note) {
        String uuid = note.getUuid();
        if (uuid == null) return Collections.emptyList();
        return (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(),
                "from CaseManagementNote cmn where cmn.uuid = ?1 order by cmn.update_date asc", uuid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getIssueHistory(String issueIds, String demoNo) {
        List<CaseManagementNote> issueListReturn = new ArrayList<CaseManagementNote>();

        List<Long> issueIdList = parseIssueIds(issueIds.split(","));

        Map<String, Object> params = new HashMap<>();
        params.put("issueIds", issueIdList);
        params.put("demoNo", demoNo);

        String hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (:issueIds) and cmn.demographic_no = :demoNo ORDER BY cmn.observation_date asc";
        List<CaseManagementNote> issueList = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, params);

        hql = "select max(cmn.id) from CaseManagementNote cmn join cmn.issues i where i.issue_id in (:issueIds) and cmn.demographic_no = :demoNo group by cmn.uuid order by max(cmn.id)";
        List<Long> currNoteList = (List<Long>) HqlQueryHelper.find(currentSession(), hql, params);

        for (CaseManagementNote issueNote : issueList) {
            if (currNoteList.contains(issueNote.getId())) {
                issueListReturn.add(issueNote);
            }
        }

        return issueListReturn;
    }

    @Override
    public CaseManagementNote getNote(Long id) {
        CaseManagementNote note = currentSession().get(CaseManagementNote.class, id);
        // currentSession().get() returns null when no record exists for the given id;
        // guard prevents NPE on lazy-collection initialization for deleted or missing notes
        if (note != null) {
            Hibernate.initialize(note.getIssues());
        }
        return note;
    }

    @Override
    public List<CaseManagementNote> getNotes(List<Long> ids) {
        if (ids.size() == 0)
            return new ArrayList<CaseManagementNote>();
        String hql = "SELECT n FROM CaseManagementNote n WHERE n.id IN (:ids)";
        Map<String, Object> params = new HashMap<>();
        params.put("ids", ids);
        @SuppressWarnings("unchecked")
        List<CaseManagementNote> notes = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, params);
        return notes;
    }

    @Override
    public CaseManagementNote getMostRecentNote(String uuid) {
        String hql = "select cmn from CaseManagementNote cmn " +
                    "where cmn.uuid = :uuid and cmn.id = (" +
                    "select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = :uuid)";

        Map<String, Object> params = new HashMap<>();
        params.put("uuid", uuid);
        @SuppressWarnings("unchecked")
        List<CaseManagementNote> tmp = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, params);

        if (tmp == null || tmp.isEmpty())
            return null;

        return tmp.get(0);
    }

    @Override
    public List<CaseManagementNote> getNotesByUUID(String uuid) {
        String hql = "select cmn from CaseManagementNote cmn where cmn.uuid = ?1 order by cmn.id";
        @SuppressWarnings("unchecked")
        List<CaseManagementNote> ret = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, uuid);
        return ret;
    }

    @Override
    public List<CaseManagementNote> getCPPNotes(String demoNo, long issueId, String staleDate) {
        Date d;
        GregorianCalendar cal = new GregorianCalendar(1970, 1, 1);
        if (staleDate != null) {

            try {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                d = formatter.parse(staleDate);
            } catch (ParseException e) {
                d = cal.getTime();
                MiscUtils.getLogger().error("Error", e);
            }
        } else {
            d = cal.getTime();
        }

        String hql = "select distinct cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = ?1 and cmn.demographic_no = ?2 and cmn.observation_date >= ?3  and cmn.id in (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.demographic_no = ?4 GROUP BY cmn2.uuid) ORDER BY cmn.observation_date asc";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> result = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql,
                issueId, demoNo, d, demoNo);
        return result;
    }

    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String[] issues, String staleDate) {
        if (issues == null || issues.length == 0) {
            return new ArrayList<CaseManagementNote>();
        }

        List<Long> issueIdList = parseIssueIds(issues);

        Date d;
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            d = formatter.parse(staleDate);
        } catch (ParseException e) {
            GregorianCalendar cal = new GregorianCalendar(1970, 1, 1);
            d = cal.getTime();
            MiscUtils.getLogger().error("Error", e);
        }

        String hql = "select distinct cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (:issueIds) and cmn.demographic_no = :demoNo and cmn.id in (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.observation_date >= :staleDate GROUP BY cmn2.uuid) ORDER BY cmn.observation_date asc";
        Map<String, Object> params = new HashMap<>();
        params.put("issueIds", issueIdList);
        params.put("demoNo", demographic_no);
        params.put("staleDate", d);

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> result = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, params);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String staleDate) {
        Query<?> q = currentSession().createNamedQuery("mostRecentTime");
        q.setParameter("demographicNo", demographic_no);
        q.setParameter("staleDate", staleDate);
        return (List<CaseManagementNote>) q.list();
    }

    // This was created by OSCAR. if all notes' UUID are same like null, it will
    // only get one note.
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no) {
        String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?1 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        return (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, demographic_no);
    }

    @Override
    public List<CaseManagementNote> getNotesByDemographicSince(String demographic_no, Date date) {
        if (demographic_no == null || date == null) return Collections.emptyList();
        String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?1 and cmn.update_date > ?2 and cmn.locked = false and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        return (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, demographic_no, date);
    }

    @Override
    public long getNotesCountByDemographicId(String demographic_no) {
        String hql = "select count(*) from CaseManagementNote cmm where cmm.demographic_no = ?1";
        return ((Long) HqlQueryHelper.find(currentSession(), hql, demographic_no).get(0)).longValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Object[]> getRawNoteInfoByDemographic(String demographic_no) {
        String hql = "select cmn.id,cmn.observation_date,cmn.providerNo,cmn.program_no,cmn.reporter_caisi_role,cmn.uuid from CaseManagementNote cmn where cmn.demographic_no = ?1 order by cmn.update_date DESC";
        return (List<Object[]>) HqlQueryHelper.find(currentSession(), hql, demographic_no);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> getRawNoteInfoMapByDemographic(String demographic_no) {
        String hql = "select new map(cmn.id as id,cmn.observation_date as observation_date,cmn.providerNo as providerNo,cmn.program_no as program_no,cmn.reporter_caisi_role as reporter_caisi_role,cmn.uuid as uuid, cmn.update_date as update_date) from CaseManagementNote cmn where cmn.demographic_no = ?1 order by cmn.update_date DESC";
        return (List<Map<String, Object>>) HqlQueryHelper.find(currentSession(), hql, demographic_no);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> getUnsignedRawNoteInfoMapByDemographic(String demographic_no) {
        String hql = "select new map(cmn.id as id,cmn.observation_date as observation_date,cmn.providerNo as providerNo,cmn.program_no as program_no,cmn.reporter_caisi_role as reporter_caisi_role,cmn.uuid as uuid, cmn.update_date as update_date) from CaseManagementNote cmn where cmn.demographic_no = ?1 and cmn.signed=?2 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.update_date DESC";
        return (List<Map<String, Object>>) HqlQueryHelper.find(currentSession(), hql, demographic_no, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, Integer maxNotes) {
        String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?1 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date desc";
        return (List<CaseManagementNote>) HqlQueryHelper.findWithLimit(currentSession(), hql, maxNotes, demographic_no);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getActiveNotesByDemographic(String demographic_no, String[] issues) {
        List<CaseManagementNote> issueListReturn = new ArrayList<CaseManagementNote>();

        if (issues == null || issues.length == 0) {
            return issueListReturn;
        }

        if (issues.length > 1) {
            List<Long> issueIdList = parseIssueIds(issues);
            String hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (:issueIds) and cmn.demographic_no = :demoNo and cmn.archived = false and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid) ORDER BY cmn.position, cmn.observation_date desc";
            Map<String, Object> params = new HashMap<>();
            params.put("issueIds", issueIdList);
            params.put("demoNo", demographic_no);
            return (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, params);

        } else {
            long id = Long.parseLong(issues[0]);

            String hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = :issueId and cmn.demographic_no = :demoNo and cmn.archived=false order by cmn.position, cmn.observation_date desc";
            Map<String, Object> params = new HashMap<>();
            params.put("issueId", id);
            params.put("demoNo", demographic_no);
            List<CaseManagementNote> issueList = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, params);

            hql = "select max(cmn.id) from CaseManagementNote cmn where cmn.demographic_no = :demoNo group by cmn.uuid order by max(cmn.id)";
            Map<String, Object> params2 = new HashMap<>();
            params2.put("demoNo", demographic_no);
            List<Long> currNoteList = (List<Long>) HqlQueryHelper.find(currentSession(), hql, params2);

            for (CaseManagementNote issueNote : issueList) {
                if (currNoteList.contains(issueNote.getId())) {
                    issueListReturn.add(issueNote);
                }
            }
            return issueListReturn;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String[] issueIds, Integer maxNotes) {
        if (issueIds == null || issueIds.length == 0) {
            return new ArrayList<CaseManagementNote>();
        }

        List<Long> issueIdList = parseIssueIds(issueIds);
        String hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (:issueIds) and cmn.demographic_no = :demoNo and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid) order by cmn.observation_date desc";
        Map<String, Object> params = new HashMap<>();
        params.put("issueIds", issueIdList);
        params.put("demoNo", demographic_no);
        return (List<CaseManagementNote>) HqlQueryHelper.findWithLimit(currentSession(), hql, maxNotes, params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String[] issueIds) {
        if (issueIds == null || issueIds.length == 0) {
            return new ArrayList<CaseManagementNote>();
        }

        List<Long> issueIdList = parseIssueIds(issueIds);
        String hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (:issueIds) and cmn.demographic_no = :demoNo and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid)";
        Map<String, Object> params = new HashMap<>();
        params.put("issueIds", issueIdList);
        params.put("demoNo", demographic_no);
        return (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql, params);
    }

    @Override
    public Collection<CaseManagementNote> findNotesByDemographicAndIssueCode(Integer demographic_no,
                                                                             String[] issueCodes) {
        Session session = currentSession();
        List<CaseManagementNote> notes = new ArrayList<CaseManagementNote>();
        StringBuilder sqlCommand = new StringBuilder(
                "select distinct casemgmt_note.note_id from issue,casemgmt_issue,casemgmt_issue_notes,casemgmt_note " +
                "where casemgmt_issue.issue_id=issue.issue_id and casemgmt_issue.demographic_no=:demographicNo ");
            
            if (issueCodes != null && issueCodes.length > 0) {
                sqlCommand.append("and issue.code in (:issueCodes) ");
            }
            
            sqlCommand.append("and casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_issue_notes.note_id=casemgmt_note.note_id");
            
            NativeQuery<?> query = session.createNativeQuery(sqlCommand.toString());
            query.setParameter("demographicNo", demographic_no);

            if (issueCodes != null && issueCodes.length > 0) {
                query.setParameterList("issueCodes", issueCodes);
            }

            @SuppressWarnings("unchecked")
            List<?> ids = query.list();
            for (Object id : ids) {
                if (id instanceof Number) {
                    notes.add(getNote(((Number) id).longValue()));
                } else {
                    log.warn("findNotesByDemographicAndIssueCode: unexpected non-Number id type: {}", id == null ? "null" : id.getClass().getName());
                }
            }

        // make unique for uuid
        HashMap<String, CaseManagementNote> uniqueForUuid = new HashMap<String, CaseManagementNote>();
        for (CaseManagementNote note : notes) {
            CaseManagementNote existingNote = uniqueForUuid.get(note.getUuid());
            if (existingNote == null || note.getUpdate_date().after(existingNote.getUpdate_date()))
                uniqueForUuid.put(note.getUuid(), note);
        }

        // sort by observationdate
        TreeMap<Date, CaseManagementNote> sortedResults = new TreeMap<Date, CaseManagementNote>();
        for (CaseManagementNote note : uniqueForUuid.values()) {
            sortedResults.put(note.getObservation_date(), note);
        }

        return (sortedResults.values());
    }


    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographicDateRange(String demographic_no, Date startDate,
                                                                   Date endDate) {
        Query<?> q = currentSession().createNamedQuery("mostRecentDateRange");
        q.setParameter("demographicNo", demographic_no);
        q.setParameter("startDate", startDate);
        q.setParameter("endDate", endDate);
        return (List<CaseManagementNote>) q.list();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographicLimit(String demographic_no, Integer offset,
                                                               Integer numToReturn) {
        Query<?> q = currentSession().createNamedQuery("mostRecentLimit");
        q.setParameter("demographicNo", demographic_no);
        q.setFirstResult(offset);
        q.setMaxResults(numToReturn);
        return (List<CaseManagementNote>) q.list();
    }

    @Override
    @Transactional(readOnly = false)
    public void updateNote(CaseManagementNote note) {
        note.setUpdate_date(new Date());
        currentSession().merge(note);
        currentSession().flush();
    }

    @Override
    @Transactional(readOnly = false)
    public void saveNote(CaseManagementNote note) {
        if (note.getUuid() == null) {
            UUID uuid = UUID.randomUUID();
            note.setUuid(uuid.toString());
        }
        if (note.getUpdate_date() == null) {
            note.setUpdate_date(new Date());
        }
        currentSession().persist(note);
        currentSession().flush();
    }

    @Override
    @Transactional(readOnly = false)
    public Object saveAndReturn(CaseManagementNote note) {
        if (note.getUuid() == null) {
            UUID uuid = UUID.randomUUID();
            note.setUuid(uuid.toString());
        }
        if (note.getUpdate_date() == null) {
            note.setUpdate_date(new Date());
        }
        currentSession().persist(note);
        return note;
    }

    @Override
    public List<CaseManagementNote> search(CaseManagementSearchBean searchBean) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        try {
            String hql = "FROM CaseManagementNote cmn WHERE cmn.demographic_no = :demoNo";
            Map<String, Object> params = new HashMap<>();
            params.put("demoNo", searchBean.getDemographicNo());

            if (searchBean.getSearchRoleId() > 0) {
                hql += " AND cmn.reporter_caisi_role = :roleId";
                params.put("roleId", String.valueOf(searchBean.getSearchRoleId()));
            }

            if (searchBean.getSearchProgramId() > 0) {
                hql += " AND cmn.program_no = :programId";
                params.put("programId", String.valueOf(searchBean.getSearchProgramId()));
            }

            Date startDate;
            Date endDate;
            if (searchBean.getSearchStartDate().length() > 0) {
                startDate = formatter.parse(searchBean.getSearchStartDate());
            } else {
                startDate = formatter.parse("1970-01-01");
            }
            if (searchBean.getSearchEndDate().length() > 0) {
                endDate = formatter.parse(searchBean.getSearchEndDate());
            } else {
                endDate = new Date();
            }
            hql += " AND cmn.update_date BETWEEN :startDate AND :endDate";
            params.put("startDate", startDate);
            params.put("endDate", endDate);

            hql += " ORDER BY cmn.update_date DESC";

            @SuppressWarnings("unchecked")
            List<CaseManagementNote> results = (List<CaseManagementNote>) HqlQueryHelper.find(
                    currentSession(), hql, params);
            return results;

        } catch (ParseException e) {
            log.warn("search: failed to parse date range from search bean", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Long> getAllNoteIds() {
        @SuppressWarnings("unchecked")
        List<Long> results = (List<Long>) HqlQueryHelper.find(currentSession(), "select n.id from CaseManagementNote n");
        return results;
    }

    @Override
    public boolean haveIssue(Long issid, String demoNo) {
        NativeQuery<?> query = currentSession().createNativeQuery(
                "SELECT * FROM casemgmt_issue_notes WHERE id = :issId");
        query.setParameter("issId", issid);
        List<?> results = query.list();
        return !results.isEmpty();
    }

    @Override
    public boolean haveIssue(String issueCode, Integer demographicId) {
        NativeQuery<?> query = currentSession().createNativeQuery(
                "SELECT casemgmt_issue.id FROM casemgmt_issue_notes, casemgmt_issue, issue WHERE issue.issue_id = casemgmt_issue.issue_id AND casemgmt_issue.id = casemgmt_issue_notes.id AND demographic_no = :demographicId AND issue.code = :issueCode");
        query.setParameter("demographicId", demographicId);
        query.setParameter("issueCode", issueCode);
        List<?> results = query.list();
        return !results.isEmpty();
    }

    /*
     * select issue_id from issue where code = 'Concerns';
     */

    @Override
    public int getNoteCountForProviderForDateRange(String providerNo, Date startDate, Date endDate) {
        try {
            Session session = currentSession();
            String sqlCommand = "select count(distinct uuid) from casemgmt_note where provider_no = :providerNo and observation_date >= :startDate and observation_date <= :endDate";

            @SuppressWarnings("unchecked")
            NativeQuery<Number> query = session.createNativeQuery(sqlCommand);
            query.setParameter("providerNo", providerNo);
            query.setParameter("startDate", new Timestamp(startDate.getTime()));
            query.setParameter("endDate", new Timestamp(endDate.getTime()));

            Number result = (Number) query.uniqueResultOptional().orElse(null);
            return result != null ? result.intValue() : 0;
        } catch (RuntimeException e) {
            log.error("getNoteCountForProviderForDateRange failed for providerNo={}", providerNo, e);
            throw e;
        }
    }

    @Override
    public int getNoteCountForProviderForDateRangeWithIssueId(String providerNo, Date startDate, Date endDate,
                                                              String issueCode) {
        try {
            Session session = currentSession();

            // Step 1: Get issue_id from issue code
            String getIssueIdSql = "SELECT issue_id FROM issue WHERE code = :issueCode";
            log.debug(getIssueIdSql);

            @SuppressWarnings("unchecked")
            NativeQuery<Integer> issueQuery = session.createNativeQuery(getIssueIdSql);
            issueQuery.setParameter("issueCode", issueCode);

            Integer issueId = (Integer) issueQuery.uniqueResultOptional().orElse(null);
            if (issueId == null) {
                log.debug("Could not find issueCode: " + issueCode);
                return 0;
            }

            log.debug("issue Code " + issueCode + " id :" + issueId);

            // Step 2: Count notes with the issue_id
            String sqlCommand = "select count(distinct uuid) from casemgmt_issue c, casemgmt_issue_notes cin, casemgmt_note cn where c.issue_id = :issueId and c.id = cin.id and cin.note_id = cn.note_id and cn.provider_no = :providerNo and observation_date >= :startDate and observation_date <= :endDate";
            log.debug(sqlCommand);

            @SuppressWarnings("unchecked")
            NativeQuery<Number> countQuery = session.createNativeQuery(sqlCommand);
            countQuery.setParameter("issueId", issueId);
            countQuery.setParameter("providerNo", providerNo);
            countQuery.setParameter("startDate", new Timestamp(startDate.getTime()));
            countQuery.setParameter("endDate", new Timestamp(endDate.getTime()));

            Number result = (Number) countQuery.uniqueResultOptional().orElse(null);
            int finalCount = result != null ? result.intValue() : 0;
            return finalCount;
        } catch (RuntimeException e) {
            log.error("getNoteCountForProviderForDateRangeWithIssueId failed for issueCode={}", issueCode, e);
            throw e;
        }
    }

    // used by decision support to search through the notes for a string
    @Override
    public List<CaseManagementNote> searchDemographicNotes(String demographic_no, String searchString) {
        String hql = "select distinct cmn from CaseManagementNote cmn where cmn.id in (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.demographic_no = ?1 GROUP BY cmn2.uuid) and cmn.demographic_no = ?2 and cmn.note like ?3 and cmn.archived = false";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> result = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), hql,
                demographic_no, demographic_no, searchString);
        return result;
    }

    @Override
    public List<CaseManagementNote> getCaseManagementNoteByProgramIdAndObservationDate(Integer programId,
                                                                                       Date minObservationDate, Date maxObservationDate) {
        String queryStr = "FROM CaseManagementNote x WHERE x.program_no=?1 and x.observation_date>=?2 and x.observation_date<=?3";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> rs = (List<CaseManagementNote>) HqlQueryHelper.find(currentSession(), queryStr,
                programId.toString(), minObservationDate, maxObservationDate);

        return rs;
    }

    @Override
    public List<CaseManagementNote> getMostRecentNotesByAppointmentNo(int appointmentNo) {
        String hql = "select distinct cmn.uuid from CaseManagementNote cmn where cmn.appointmentNo = ?1";
        @SuppressWarnings("unchecked")
        List<String> tmp = (List<String>) HqlQueryHelper.find(currentSession(), hql, appointmentNo);
        List<CaseManagementNote> mostRecents = new ArrayList<CaseManagementNote>();
        for (String uuid : tmp) {
            mostRecents.add(this.getMostRecentNote(uuid));
        }
        return mostRecents;
    }

    @Override
    public List<CaseManagementNote> getMostRecentNotes(Integer demographicNo) {
        String hql = "select distinct cmn.uuid from CaseManagementNote cmn where cmn.demographic_no = ?1";
        @SuppressWarnings("unchecked")
        List<String> tmp = (List<String>) HqlQueryHelper.find(currentSession(), hql,
                String.valueOf(demographicNo));
        List<CaseManagementNote> mostRecents = new ArrayList<CaseManagementNote>();
        for (String uuid : tmp) {
            mostRecents.add(this.getMostRecentNote(uuid));
        }
        return mostRecents;
    }

    @Override
    public Long findMaxNoteId() {
        String sql = "select max(c.id) from CaseManagementNote c";
        @SuppressWarnings("unchecked")
        List<Object> r = (List<Object>) HqlQueryHelper.find(currentSession(), sql);
        if (r.isEmpty()) {
            return 0L;
        }
        return (Long) r.get(0);

    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getNotesByFacilitySince(Date date, List<Program> programs) {
        if (programs == null || programs.isEmpty()) {
            return new ArrayList<Integer>();
        }

        List<String> programIds = new ArrayList<String>();
        for (Program p : programs) {
            programIds.add(String.valueOf(p.getId()));
        }

        String hql = "select distinct cmn.demographic_no from CaseManagementNote cmn where cmn.program_no in (:programIds) and cmn.update_date > :updateDate and cmn.locked = false and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        Map<String, Object> params = new HashMap<>();
        params.put("programIds", programIds);
        params.put("updateDate", date);
        List<String> results = (List<String>) HqlQueryHelper.find(currentSession(), hql, params);

        List<Integer> results2 = new ArrayList<Integer>();
        for (String r : results) {
            results2.add(Integer.parseInt(r));
        }
        return results2;
    }

    private static List<Long> parseIssueIds(String[] ids) {
        List<Long> issueIdList = new ArrayList<Long>();
        for (String id : ids) {
            issueIdList.add(Long.parseLong(id.trim()));
        }
        return issueIdList;
    }
}
