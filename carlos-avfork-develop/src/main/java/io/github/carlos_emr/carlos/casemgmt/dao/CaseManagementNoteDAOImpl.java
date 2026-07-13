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

import java.util.Arrays;

import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementNoteListDTO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementSearchBean;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import io.github.carlos_emr.carlos.utility.EncounterUtil;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;

@Transactional
public class CaseManagementNoteDAOImpl extends AbstractJpaDao implements CaseManagementNoteDAO {

    private static Logger log = MiscUtils.getLogger();

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> findAll() {
        log.warn(
                "A METHOD THAT IS LIKELY TO CAUSE A CRASH HAS BEEN INVOKED. PLEASE LIMIT THE USE OF THIS METHOD, AS IT'S LIKELY TO EXHAUST MEMORY AND MAY LEAD TO A SERVER CRASH. CONSIDER PAGINATING THE INVOCATION INSTEAD");
        return (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), "FROM CaseManagementNote");
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getEditors(CaseManagementNote note) {
        String uuid = note.getUuid();
        if (uuid == null) return Collections.emptyList();
        String hql = "select distinct p from Provider p, CaseManagementNote cmn where p.providerNo = cmn.providerNo and cmn.uuid = ?1";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), hql, uuid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getAllEditors(String demographicNo) {
        if (demographicNo == null) return Collections.emptyList();
        String hql = "select distinct p from Provider p, CaseManagementNote cmn where p.providerNo = cmn.providerNo and cmn.demographic_no = ?1";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), hql, demographicNo);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getHistory(CaseManagementNote note) {
        String uuid = note.getUuid();
        if (uuid == null) return Collections.emptyList();
        return (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(),
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
        List<CaseManagementNote> issueList = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, params);

        hql = "select max(cmn.id) from CaseManagementNote cmn join cmn.issues i where i.issue_id in (:issueIds) and cmn.demographic_no = :demoNo group by cmn.uuid order by max(cmn.id)";
        List<Long> currNoteList = (List<Long>) JpqlQueryHelper.find(entityManager(), hql, params);

        for (CaseManagementNote issueNote : issueList) {
            if (currNoteList.contains(issueNote.getId())) {
                issueListReturn.add(issueNote);
            }
        }

        return issueListReturn;
    }

    @Override
    public CaseManagementNote getNote(Long id) {
        CaseManagementNote note = entityManager().find(CaseManagementNote.class, id);
        // entityManager().find() returns null when no record exists for the given id;
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
        List<CaseManagementNote> notes = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, params);
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
        List<CaseManagementNote> tmp = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, params);

        if (tmp == null || tmp.isEmpty())
            return null;

        return tmp.get(0);
    }

    @Override
    public List<CaseManagementNote> getNotesByUUID(String uuid) {
        String hql = "select cmn from CaseManagementNote cmn where cmn.uuid = ?1 order by cmn.id";
        @SuppressWarnings("unchecked")
        List<CaseManagementNote> ret = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, uuid);
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
        List<CaseManagementNote> result = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql,
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
        List<CaseManagementNote> result = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, params);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String staleDate) {
        Query q = entityManager().createNamedQuery("mostRecentTime");
        q.setParameter("demographicNo", demographic_no);
        q.setParameter("staleDate", staleDate);
        return (List<CaseManagementNote>) q.getResultList();
    }

    // This was created by OSCAR. if all notes' UUID are same like null, it will
    // only get one note.
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no) {
        String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?1 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        return (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, demographic_no);
    }

    @Override
    public List<CaseManagementNote> getNotesByDemographicSince(String demographic_no, Date date) {
        if (demographic_no == null || date == null) return Collections.emptyList();
        String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?1 and cmn.update_date > ?2 and cmn.locked = false and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        return (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, demographic_no, date);
    }

    @Override
    public long getNotesCountByDemographicId(String demographic_no) {
        String hql = "select count(*) from CaseManagementNote cmm where cmm.demographic_no = ?1";
        return ((Long) JpqlQueryHelper.find(entityManager(), hql, demographic_no).get(0)).longValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Object[]> getRawNoteInfoByDemographic(String demographic_no) {
        String hql = "select cmn.id,cmn.observation_date,cmn.providerNo,cmn.program_no,cmn.reporter_caisi_role,cmn.uuid from CaseManagementNote cmn where cmn.demographic_no = ?1 order by cmn.update_date DESC";
        return (List<Object[]>) JpqlQueryHelper.find(entityManager(), hql, demographic_no);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> getRawNoteInfoMapByDemographic(String demographic_no) {
        String hql = "select new map(cmn.id as id,cmn.observation_date as observation_date,cmn.providerNo as providerNo,cmn.program_no as program_no,cmn.reporter_caisi_role as reporter_caisi_role,cmn.uuid as uuid, cmn.update_date as update_date) from CaseManagementNote cmn where cmn.demographic_no = ?1 order by cmn.update_date DESC";
        return (List<Map<String, Object>>) JpqlQueryHelper.find(entityManager(), hql, demographic_no);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> getUnsignedRawNoteInfoMapByDemographic(String demographic_no) {
        String hql = "select new map(cmn.id as id,cmn.observation_date as observation_date,cmn.providerNo as providerNo,cmn.program_no as program_no,cmn.reporter_caisi_role as reporter_caisi_role,cmn.uuid as uuid, cmn.update_date as update_date) from CaseManagementNote cmn where cmn.demographic_no = ?1 and cmn.signed=?2 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.update_date DESC";
        return (List<Map<String, Object>>) JpqlQueryHelper.find(entityManager(), hql, demographic_no, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, Integer maxNotes) {
        String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?1 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date desc";
        return (List<CaseManagementNote>) JpqlQueryHelper.findWithLimit(entityManager(), hql, maxNotes, demographic_no);
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
            return (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, params);

        } else {
            long id = Long.parseLong(issues[0]);

            String hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = :issueId and cmn.demographic_no = :demoNo and cmn.archived=false order by cmn.position, cmn.observation_date desc";
            Map<String, Object> params = new HashMap<>();
            params.put("issueId", id);
            params.put("demoNo", demographic_no);
            List<CaseManagementNote> issueList = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, params);

            hql = "select max(cmn.id) from CaseManagementNote cmn where cmn.demographic_no = :demoNo group by cmn.uuid order by max(cmn.id)";
            Map<String, Object> params2 = new HashMap<>();
            params2.put("demoNo", demographic_no);
            List<Long> currNoteList = (List<Long>) JpqlQueryHelper.find(entityManager(), hql, params2);

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
        return (List<CaseManagementNote>) JpqlQueryHelper.findWithLimit(entityManager(), hql, maxNotes, params);
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
        return (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql, params);
    }

    @Override
    public Collection<CaseManagementNote> findNotesByDemographicAndIssueCode(Integer demographic_no,
                                                                             String[] issueCodes) {
        List<CaseManagementNote> notes = new ArrayList<CaseManagementNote>();
        StringBuilder sqlCommand = new StringBuilder(
                "select distinct casemgmt_note.note_id from issue,casemgmt_issue,casemgmt_issue_notes,casemgmt_note " +
                "where casemgmt_issue.issue_id=issue.issue_id and casemgmt_issue.demographic_no=:demographicNo ");
            
            if (issueCodes != null && issueCodes.length > 0) {
                sqlCommand.append("and issue.code in (:issueCodes) ");
            }
            
            sqlCommand.append("and casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_issue_notes.note_id=casemgmt_note.note_id");
            
            Query query = entityManager().createNativeQuery(sqlCommand.toString());
            query.setParameter("demographicNo", demographic_no);

            if (issueCodes != null && issueCodes.length > 0) {
                // Hibernate 7 extension: setParameter with a List on a native query expands the collection
                // into the IN clause — this is not guaranteed by the JPA spec but is supported by Hibernate
                // as NativeQueryImplementor (replaces the pre-migration setParameterList() call).
                query.setParameter("issueCodes", Arrays.asList(issueCodes));
            }

            @SuppressWarnings("unchecked")
            List<?> ids = query.getResultList();
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
        Query q = entityManager().createNamedQuery("mostRecentDateRange");
        q.setParameter("demographicNo", demographic_no);
        q.setParameter("startDate", startDate);
        q.setParameter("endDate", endDate);
        return (List<CaseManagementNote>) q.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographicLimit(String demographic_no, Integer offset,
                                                               Integer numToReturn) {
        Query q = entityManager().createNamedQuery("mostRecentLimit");
        q.setParameter("demographicNo", demographic_no);
        q.setFirstResult(offset);
        q.setMaxResults(numToReturn);
        return (List<CaseManagementNote>) q.getResultList();
    }

    @Override
    @Transactional(readOnly = false)
    public void updateNote(CaseManagementNote note) {
        note.setUpdate_date(new Date());
        entityManager().merge(note);
        entityManager().flush();
    }

    /**
     * Saves a case management note, handling both new and existing (detached) entities.
     *
     * <p>New notes (null or zero ID) are persisted; existing notes (positive ID) are
     * merged back into the persistence context. A UUID and update_date are assigned
     * automatically if not already set.</p>
     *
     * @param note CaseManagementNote the note entity to save; must not be null
     */
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
        // Callers (e.g. saveCaseManagementNote) may pass detached entities with
        // existing IDs — merge reattaches them. persist is for new notes only.
        // updateNote() also exists but callers historically use saveNote for both.
        if (note.getId() != null && note.getId() > 0) {
            entityManager().merge(note);
        } else {
            entityManager().persist(note);
        }
        entityManager().flush();
    }

    /**
     * Saves a case management note and returns the managed entity.
     *
     * <p>For new notes (null or zero ID), the original instance is persisted and returned.
     * For existing notes (positive ID), the detached entity is merged and the newly managed
     * instance is returned — callers should use the returned reference, not the original.</p>
     *
     * @param note CaseManagementNote the note entity to save; must not be null
     * @return Object the managed CaseManagementNote instance after persist or merge
     */
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
        if (note.getId() != null && note.getId() > 0) {
            return entityManager().merge(note);
        } else {
            entityManager().persist(note);
            return note;
        }
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
            List<CaseManagementNote> results = (List<CaseManagementNote>) JpqlQueryHelper.find(
                    entityManager(), hql, params);
            return results;

        } catch (ParseException e) {
            log.warn("search: failed to parse date range from search bean", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Long> getAllNoteIds() {
        @SuppressWarnings("unchecked")
        List<Long> results = (List<Long>) JpqlQueryHelper.find(entityManager(), "select n.id from CaseManagementNote n");
        return results;
    }

    @Override
    public boolean haveIssue(Long issid, String demoNo) {
        Query query = entityManager().createNativeQuery(
                "SELECT * FROM casemgmt_issue_notes WHERE id = :issId");
        query.setParameter("issId", issid);
        List<?> results = query.getResultList();
        return !results.isEmpty();
    }

    @Override
    public boolean haveIssue(String issueCode, Integer demographicId) {
        Query query = entityManager().createNativeQuery(
                "SELECT casemgmt_issue.id FROM casemgmt_issue_notes, casemgmt_issue, issue WHERE issue.issue_id = casemgmt_issue.issue_id AND casemgmt_issue.id = casemgmt_issue_notes.id AND demographic_no = :demographicId AND issue.code = :issueCode");
        query.setParameter("demographicId", demographicId);
        query.setParameter("issueCode", issueCode);
        List<?> results = query.getResultList();
        return !results.isEmpty();
    }

    /*
     * select issue_id from issue where code = 'Concerns';
     */

    @Override
    public int getNoteCountForProviderForDateRange(String providerNo, Date startDate, Date endDate) {
        try {
            String sqlCommand = "select count(distinct uuid) from casemgmt_note where provider_no = :providerNo and observation_date >= :startDate and observation_date <= :endDate";

            Query query = entityManager().createNativeQuery(sqlCommand);
            query.setParameter("providerNo", providerNo);
            query.setParameter("startDate", new Timestamp(startDate.getTime()));
            query.setParameter("endDate", new Timestamp(endDate.getTime()));

            Number result = (Number) query.getSingleResult();
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
            // Step 1: Get issue_id from issue code
            String getIssueIdSql = "SELECT issue_id FROM issue WHERE code = :issueCode";
            log.debug(getIssueIdSql);

            Query issueQuery = entityManager().createNativeQuery(getIssueIdSql);
            issueQuery.setParameter("issueCode", issueCode);

            List<?> issueResults = issueQuery.getResultList();
            if (issueResults.isEmpty()) {
                log.debug("Could not find issueCode: " + issueCode);
                return 0;
            }
            int issueId = ((Number) issueResults.get(0)).intValue();

            log.debug("issue Code " + issueCode + " id :" + issueId);

            // Step 2: Count notes with the issue_id
            String sqlCommand = "select count(distinct uuid) from casemgmt_issue c, casemgmt_issue_notes cin, casemgmt_note cn where c.issue_id = :issueId and c.id = cin.id and cin.note_id = cn.note_id and cn.provider_no = :providerNo and observation_date >= :startDate and observation_date <= :endDate";
            log.debug(sqlCommand);

            Query countQuery = entityManager().createNativeQuery(sqlCommand);
            countQuery.setParameter("issueId", issueId);
            countQuery.setParameter("providerNo", providerNo);
            countQuery.setParameter("startDate", new Timestamp(startDate.getTime()));
            countQuery.setParameter("endDate", new Timestamp(endDate.getTime()));

            Number result = (Number) countQuery.getSingleResult();
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
        List<CaseManagementNote> result = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), hql,
                demographic_no, demographic_no, searchString);
        return result;
    }

    @Override
    public List<CaseManagementNote> getCaseManagementNoteByProgramIdAndObservationDate(Integer programId,
                                                                                       Date minObservationDate, Date maxObservationDate) {
        String queryStr = "FROM CaseManagementNote x WHERE x.program_no=?1 and x.observation_date>=?2 and x.observation_date<=?3";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> rs = (List<CaseManagementNote>) JpqlQueryHelper.find(entityManager(), queryStr,
                programId.toString(), minObservationDate, maxObservationDate);

        return rs;
    }

    @Override
    public List<CaseManagementNote> getMostRecentNotesByAppointmentNo(int appointmentNo) {
        String hql = "select distinct cmn.uuid from CaseManagementNote cmn where cmn.appointmentNo = ?1";
        @SuppressWarnings("unchecked")
        List<String> tmp = (List<String>) JpqlQueryHelper.find(entityManager(), hql, appointmentNo);
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
        List<String> tmp = (List<String>) JpqlQueryHelper.find(entityManager(), hql,
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
        List<Object> r = (List<Object>) JpqlQueryHelper.find(entityManager(), sql);
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
        List<String> results = (List<String>) JpqlQueryHelper.find(entityManager(), hql, params);

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

    /**
     * Returns lightweight case management note list DTOs for a demographic, ordered by
     * observation date descending. Pre-joins provider name (HBM PascalCase:
     * {@code p.lastName}, {@code p.firstName}) via LEFT JOIN. Eliminates 3 {@code lazy=false}
     * collections on the full {@code CaseManagementNote} entity.
     *
     * @param demographicNo String the demographic number
     * @return List&lt;CaseManagementNoteListDTO&gt; ordered by observation_date descending; empty if none found
     * @throws org.hibernate.HibernateException if the underlying query fails
     * @since 2026-04-11
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNoteListDTO> findNoteDTOsByDemographicNo(String demographicNo) {
        // Provider is annotation-mapped with JavaBean property names; HQL uses providerNo, lastName, and firstName.
        TypedQuery<CaseManagementNoteListDTO> query = entityManager().createQuery("""
                SELECT NEW io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementNoteListDTO(
                    cmn.id, cmn.update_date, cmn.observation_date, cmn.demographic_no,
                    cmn.signed, cmn.providerNo, cmn.signing_provider_no, cmn.encounter_type,
                    cmn.billing_code, cmn.program_no, cmn.uuid,
                    cmn.locked, cmn.archived, cmn.appointmentNo,
                    p.lastName, p.firstName)
                FROM CaseManagementNote cmn
                LEFT JOIN Provider p ON p.providerNo = cmn.providerNo
                WHERE cmn.demographic_no = :demoNo
                ORDER BY cmn.observation_date DESC
                """,
                CaseManagementNoteListDTO.class);
        query.setParameter("demoNo", demographicNo);
        return query.getResultList();
    }

    /**
     * Returns encounter counts grouped by {@link EncounterUtil.EncounterType} for the
     * notes in the given role (and optionally program) in the date range.
     *
     * <p>Migrated from the legacy static JDBC helper on {@link CaseManagementNoteDAO}:
     * converting to an instance method lets this query participate in the Spring
     * {@link Transactional @Transactional} context via {@link #entityManager()},
     * rather than running outside Spring transactions on an autoCommit connection.</p>
     *
     * @param programId Integer the program number, or {@code null} to span all programs
     * @param roleId int the reporter_caisi_role to match
     * @param startDate Date inclusive lower bound on observation_date
     * @param endDate Date exclusive upper bound on observation_date
     * @return EncounterCounts non-null aggregate, zero-initialised for types with no matches
     */
    @Override
    @Transactional(readOnly = true)
    public EncounterCounts getDemographicEncounterCountsByProgramAndRoleId(Integer programId, int roleId,
                                                                           Date startDate, Date endDate) {
        EncounterCounts results = new EncounterCounts();

        // Broken down by encounter type
        String breakdownSql = String.join(" ",
                "select encounter_type, count(demographic_no), count(distinct demographic_no)",
                "from casemgmt_note",
                "where reporter_caisi_role = :roleId",
                "and observation_date >= :startDate",
                "and observation_date < :endDate",
                (programId == null ? "" : "and program_no = :programId"),
                "group by encounter_type");

        Query breakdownQuery = entityManager().createNativeQuery(breakdownSql);
        breakdownQuery.setParameter("roleId", roleId);
        breakdownQuery.setParameter("startDate", new Timestamp(startDate.getTime()));
        breakdownQuery.setParameter("endDate", new Timestamp(endDate.getTime()));
        if (programId != null) breakdownQuery.setParameter("programId", programId);

        @SuppressWarnings("unchecked")
        List<Object[]> breakdownRows = breakdownQuery.getResultList();
        for (Object[] row : breakdownRows) {
            EncounterUtil.EncounterType encounterType = EncounterUtil
                    .getEncounterTypeFromOldDbValue((String) row[0]);
            results.nonUniqueCounts.put(encounterType, ((Number) row[1]).intValue());
            results.uniqueCounts.put(encounterType, ((Number) row[2]).intValue());
        }

        // Total unique count (not broken down)
        String totalSql = String.join(" ",
                "select count(distinct demographic_no)",
                "from casemgmt_note",
                "where reporter_caisi_role = :roleId",
                "and observation_date >= :startDate",
                "and observation_date < :endDate",
                (programId == null ? "" : "and program_no = :programId"));

        Query totalQuery = entityManager().createNativeQuery(totalSql);
        totalQuery.setParameter("roleId", roleId);
        totalQuery.setParameter("startDate", new Timestamp(startDate.getTime()));
        totalQuery.setParameter("endDate", new Timestamp(endDate.getTime()));
        if (programId != null) totalQuery.setParameter("programId", programId);

        results.totalUniqueCount = ((Number) totalQuery.getSingleResult()).intValue();

        return results;
    }
}
