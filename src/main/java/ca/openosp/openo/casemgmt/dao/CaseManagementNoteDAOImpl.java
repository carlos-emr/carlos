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

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.criterion.Expression;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;

import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementSearchBean;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.OscarProperties;
import ca.openosp.openo.util.SqlUtils;

/**
 * Data Access Object implementation for CaseManagementNote entities.
 * <p>
 * This DAO provides methods for managing case management notes, including creation, retrieval,
 * updating, and querying notes associated with patient demographics. It handles complex queries
 * for note history, issue-based filtering, and provider-specific note tracking.
 * </p>
 * <p>
 * This implementation uses direct Hibernate SessionFactory injection for database operations,
 * migrated from the deprecated HibernateDaoSupport pattern to support Spring 6 compatibility.
 * </p>
 *
 * @since 2026-01-15
 */
@Transactional
public class CaseManagementNoteDAOImpl implements CaseManagementNoteDAO {

    private static Logger log = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Returns the current Hibernate session.
     * <p>
     * This method retrieves the session bound to the current transaction context.
     * All database operations should use this session to ensure proper transaction management.
     * </p>
     *
     * @return the current Hibernate Session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all case management notes from the database.
     * <p>
     * <strong>WARNING:</strong> This method loads all notes into memory and may cause
     * performance issues or server crashes. Use pagination methods instead.
     * </p>
     *
     * @return list of all CaseManagementNote entities
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> findAll() {
        log.warn(
                "A METHOD THAT IS LIKELY TO CAUSE A CRASH HAS BEEN INVOKED. PLEASE LIMIT THE USE OF THIS METHOD, AS IT'S LIKELY TO EXHAUST MEMORY AND MAY LEAD TO A SERVER CRASH. CONSIDER PAGINATING THE INVOCATION INSTEAD");
        Query<CaseManagementNote> query = getSession().createQuery("FROM CaseManagementNote");
        return query.list();
    }

    /**
     * Retrieves all providers who have edited a specific note.
     *
     * @param note the CaseManagementNote to find editors for
     * @return list of Provider entities who have edited the note
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getEditors(CaseManagementNote note) {
        String uuid = note.getUuid();
        String hql = "select distinct p from Provider p, CaseManagementNote cmn where p.ProviderNo = cmn.providerNo and cmn.uuid = :uuid";
        Query<Provider> query = getSession().createQuery(hql);
        query.setParameter("uuid", uuid);
        return query.list();
    }

    /**
     * Retrieves all providers who have edited notes for a specific demographic.
     *
     * @param demographicNo the demographic number to search for
     * @return list of Provider entities who have edited notes for this demographic
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getAllEditors(String demographicNo) {
        String hql = "select distinct p from Provider p, CaseManagementNote cmn where p.ProviderNo = cmn.providerNo and cmn.demographic_no = :demographicNo";
        Query<Provider> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", demographicNo);
        return query.list();
    }

    /**
     * Retrieves the complete history of a note, ordered by update date.
     *
     * @param note the CaseManagementNote to get history for
     * @return list of all versions of the note, ordered chronologically
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getHistory(CaseManagementNote note) {
        String uuid = note.getUuid();
        String hql = "from CaseManagementNote cmn where cmn.uuid = :uuid order by cmn.update_date asc";
        Query<CaseManagementNote> query = getSession().createQuery(hql);
        query.setParameter("uuid", uuid);
        return query.list();
    }

    /**
     * Retrieves note history for specific issues and demographic.
     * <p>
     * Note: This method uses string concatenation for issueIds in the HQL query.
     * This is acceptable here as issueIds are numeric values validated elsewhere.
     * </p>
     *
     * @param issueIds comma-separated list of issue IDs
     * @param demoNo the demographic number
     * @return list of most recent notes for each UUID related to the specified issues
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getIssueHistory(String issueIds, String demoNo) {
        String hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (" + issueIds
                + ") and cmn.demographic_no= :demoNo ORDER BY cmn.observation_date asc";

        List<CaseManagementNote> issueListReturn = new ArrayList<CaseManagementNote>();

        Query<CaseManagementNote> query1 = getSession().createQuery(hql);
        query1.setParameter("demoNo", demoNo);
        List<CaseManagementNote> issueList = query1.list();

        hql = "select max(cmn.id) from CaseManagementNote cmn join cmn.issues i where i.issue_id in (" + issueIds
                + ") and cmn.demographic_no = :demoNo group by cmn.uuid order by max(cmn.id)";
        Query<Integer> query2 = getSession().createQuery(hql);
        query2.setParameter("demoNo", demoNo);
        List<Integer> currNoteList = query2.list();
        
        for (CaseManagementNote issueNote : issueList) {
            if (currNoteList.contains(issueNote.getId())) {
                issueListReturn.add(issueNote);
            }
        }

        return issueListReturn;
    }

    /**
     * Retrieves a single case management note by ID with initialized issues collection.
     *
     * @param id the note ID
     * @return the CaseManagementNote with issues eagerly loaded, or null if not found
     */
    @Override
    public CaseManagementNote getNote(Long id) {
        CaseManagementNote note = getSession().get(CaseManagementNote.class, id);
        if (note != null) {
            // Force initialization of lazy-loaded issues collection
            org.hibernate.Hibernate.initialize(note.getIssues());
        }
        return note;
    }

    /**
     * Retrieves multiple notes by their IDs.
     *
     * @param ids list of note IDs to retrieve
     * @return list of CaseManagementNote entities matching the IDs
     */
    @Override
    public List<CaseManagementNote> getNotes(List<Long> ids) {
        if (ids.size() == 0)
            return new ArrayList<CaseManagementNote>();
        @SuppressWarnings("unchecked")
        Query<CaseManagementNote> query = getSession().createQuery("SELECT n FROM CaseManagementNote n WHERE n.id IN (:ids)");
        query.setParameter("ids", ids);
        List<CaseManagementNote> notes = query.list();
        return notes;
    }

    /**
     * Retrieves the most recent version of a note by UUID.
     *
     * @param uuid the note UUID
     * @return the most recent CaseManagementNote with this UUID, or null if not found
     */
    @Override
    public CaseManagementNote getMostRecentNote(String uuid) {
        String hql = "select cmn from CaseManagementNote cmn " +
                    "where cmn.uuid = :uuid and cmn.id = (" +
                    "select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = :uuid)";

        @SuppressWarnings("unchecked")
        Query<CaseManagementNote> query = getSession().createQuery(hql);
        query.setParameter("uuid", uuid);
        List<CaseManagementNote> tmp = query.list();

        if (tmp == null || tmp.isEmpty())
            return null;

        return tmp.get(0);
    }

    /**
     * Retrieves all notes with a specific UUID, ordered by ID.
     *
     * @param uuid the note UUID
     * @return list of all CaseManagementNote versions with this UUID
     */
    @Override
    public List<CaseManagementNote> getNotesByUUID(String uuid) {
        String hql = "select cmn from CaseManagementNote cmn where cmn.uuid = :uuid order by cmn.id";
        @SuppressWarnings("unchecked")
        Query<CaseManagementNote> query = getSession().createQuery(hql);
        query.setParameter("uuid", uuid);
        List<CaseManagementNote> ret = query.list();
        return ret;
    }

    /**
     * Retrieves CPP (Cumulative Patient Profile) notes for a demographic and issue.
     *
     * @param demoNo the demographic number
     * @param issueId the issue ID
     * @param staleDate the date after which notes are considered current (format: yyyy-MM-dd)
     * @return list of most recent notes for each UUID after the stale date
     */
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

        String hql = "select distinct cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = :issueId and cmn.demographic_no = :demoNo and cmn.observation_date >= :date  and cmn.id in (select max(cmn.id) from cmn where cmn.demographic_no = :demoNo2 GROUP BY uuid) ORDER BY cmn.observation_date asc";

        @SuppressWarnings("unchecked")
        Query<CaseManagementNote> query = getSession().createQuery(hql);
        query.setParameter("issueId", issueId);
        query.setParameter("demoNo", demoNo);
        query.setParameter("date", d);
        query.setParameter("demoNo2", demoNo);
        List<CaseManagementNote> result = query.list();
        return result;
    }

    /**
     * Retrieves notes for a demographic filtered by issues and stale date.
     *
     * @param demographic_no the demographic number
     * @param issues array of issue IDs to filter by
     * @param staleDate the date after which notes are considered current (format: yyyy-MM-dd)
     * @return list of most recent notes for each UUID matching criteria
     */
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String[] issues, String staleDate) {
        String list = null;
        if (issues != null && issues.length > 0) {
            list = "";
            for (int x = 0; x < issues.length; x++) {
                if (x != 0) {
                    list += ",";
                }
                list += issues[x];
            }
        }

        Date d;
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            d = formatter.parse(staleDate);
        } catch (ParseException e) {
            GregorianCalendar cal = new GregorianCalendar(1970, 1, 1);
            d = cal.getTime();
            MiscUtils.getLogger().error("Error", e);
        }
        String hql = "select distinct cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (" + list
                + ") and cmn.demographic_no = :demographicNo  and cmn.id in (select max(cmn.id) from cmn where cmn.observation_date >= :date GROUP BY uuid) ORDER BY cmn.observation_date asc";

        @SuppressWarnings("unchecked")
        Query<CaseManagementNote> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", demographic_no);
        query.setParameter("date", d);
        List<CaseManagementNote> result = query.list();
        return result;
    }

    /**
     * Retrieves notes for a demographic after a specific stale date.
     * Uses named queries that differ based on database type (Oracle vs others).
     *
     * @param demographic_no the demographic number
     * @param staleDate the date after which notes are considered current (format: yyyy-MM-dd)
     * @return list of most recent notes for each UUID
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String staleDate) {
        if (OscarProperties.getInstance().getDbType().equals("oracle")) {
            Query<CaseManagementNote> query = getSession().getNamedQuery("mostRecentTimeOra");
            query.setParameter(0, demographic_no);
            query.setParameter(1, staleDate);
            return query.list();
        } else {
            Query<CaseManagementNote> query = getSession().getNamedQuery("mostRecentTime");
            query.setParameter(0, demographic_no);
            query.setParameter(1, staleDate);
            return query.list();
        }
    }

    /**
     * Retrieves the most recent notes for a demographic.
     * <p>
     * Note: If all notes have the same UUID (e.g., all null), this will only return one note.
     * Uses named queries that differ based on database type (Oracle vs others).
     * </p>
     *
     * @param demographic_no the demographic number
     * @return list of most recent notes for each unique UUID
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no) {
        if (OscarProperties.getInstance().getDbType().equals("oracle")) {
            Query<CaseManagementNote> query = getSession().getNamedQuery("mostRecentOra");
            query.setParameter(0, demographic_no);
            return query.list();
        } else {
            String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = :demographicNo and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
            Query<CaseManagementNote> query = getSession().createQuery(hql);
            query.setParameter("demographicNo", demographic_no);
            return query.list();
            // return getHibernateTemplate().findByNamedQuery("mostRecent", new Object[] {
            // demographic_no });

        }
    }

    /**
     * Retrieves notes for a demographic that have been updated since a specific date.
     *
     * @param demographic_no the demographic number
     * @param date the date to filter by (notes updated after this date)
     * @return list of unlocked, most recent notes updated since the date
     */
    @Override
    public List<CaseManagementNote> getNotesByDemographicSince(String demographic_no, Date date) {

        String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = :demographicNo and cmn.update_date > :date and cmn.locked != '1' and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        Query<CaseManagementNote> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", demographic_no);
        query.setParameter("date", date);
        return query.list();
    }

    /**
     * Returns the count of notes for a specific demographic.
     *
     * @param demographic_no the demographic number
     * @return the count of notes
     */
    @Override
    public long getNotesCountByDemographicId(String demographic_no) {
        String hql = "select count(*) from CaseManagementNote cmm where cmm.demographic_no = :demographicNo";
        Query<Long> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", demographic_no);
        return query.list().get(0).longValue();
    }

    /**
     * Retrieves raw note information (selected fields only) for a demographic.
     *
     * @param demographic_no the demographic number
     * @return list of Object arrays containing note fields
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Object[]> getRawNoteInfoByDemographic(String demographic_no) {
        String hql = "select cmn.id,cmn.observation_date,cmn.providerNo,cmn.program_no,cmn.reporter_caisi_role,cmn.uuid from CaseManagementNote cmn where cmn.demographic_no = :demographicNo order by cmn.update_date DESC";
        Query<Object[]> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", demographic_no);
        return query.list();
    }

    /**
     * Retrieves raw note information as a map for a demographic.
     *
     * @param demographic_no the demographic number
     * @return list of maps containing note information
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> getRawNoteInfoMapByDemographic(String demographic_no) {
        String hql = "select new map(cmn.id as id,cmn.observation_date as observation_date,cmn.providerNo as providerNo,cmn.program_no as program_no,cmn.reporter_caisi_role as reporter_caisi_role,cmn.uuid as uuid, cmn.update_date as update_date) from CaseManagementNote cmn where cmn.demographic_no = :demographicNo order by cmn.update_date DESC";
        Query<Map<String, Object>> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", demographic_no);
        return query.list();
    }

    /**
     * Retrieves unsigned raw note information as a map for a demographic.
     *
     * @param demographic_no the demographic number
     * @return list of maps containing unsigned note information
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> getUnsignedRawNoteInfoMapByDemographic(String demographic_no) {
        String hql = "select new map(cmn.id as id,cmn.observation_date as observation_date,cmn.providerNo as providerNo,cmn.program_no as program_no,cmn.reporter_caisi_role as reporter_caisi_role,cmn.uuid as uuid, cmn.update_date as update_date) from CaseManagementNote cmn where cmn.demographic_no = :demographicNo and cmn.signed=:signed and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.update_date DESC";
        Query<Map<String, Object>> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", demographic_no);
        query.setParameter("signed", false);
        return query.list();
    }

    /**
     * Retrieves the most recent notes for a demographic with optional limit.
     *
     * @param demographic_no the demographic number
     * @param maxNotes maximum number of notes to return (-1 for unlimited)
     * @return list of most recent notes for each unique UUID
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, Integer maxNotes) {
        if (OscarProperties.getInstance().getDbType().equals("oracle")) {
            Query<CaseManagementNote> query = getSession().getNamedQuery("mostRecentOra");
            query.setParameter(0, demographic_no);
            return query.list();
        } else {
            String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = :demographicNo and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date desc";

            Query<CaseManagementNote> query = getSession().createQuery(hql);
            query.setParameter("demographicNo", demographic_no);
            if (maxNotes != -1) {
                query.setMaxResults(maxNotes);
            }

            List<CaseManagementNote> list = query.list();
            return list;
            // return getHibernateTemplate().findByNamedQuery("mostRecent", new Object[] {
            // demographic_no });

        }
    }

    // This is the original method. It was created by CAISI, to get all notes for
    // each client.
    /*
     * public List getNotesByDemographic(String demographic_no) { return
     * this.getHibernateTemplate().
     * find("from CaseManagementNote cmn where cmn.demographic_no = ? ORDER BY cmn.update_date DESC"
     * , new Object[] {demographic_no}); }
     */

    /**
     * Retrieves active (non-archived) notes for a demographic filtered by issues.
     *
     * @param demographic_no the demographic number
     * @param issues array of issue IDs to filter by
     * @return list of active notes matching the criteria
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getActiveNotesByDemographic(String demographic_no, String[] issues) {
        String list = null;
        String hql;

        List<CaseManagementNote> issueListReturn = new ArrayList<CaseManagementNote>();

        if (issues != null) {
            if (issues.length > 1) {
                list = "";
                for (int x = 0; x < issues.length; x++) {
                    if (x != 0) {
                        list += ",";
                    }
                    list += issues[x];
                }
                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (" + list
                        + ") and cmn.demographic_no = :demographicNo and cmn.archived = 0 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid) ORDER BY cmn.position, cmn.observation_date desc";
                Query<CaseManagementNote> query = getSession().createQuery(hql);
                query.setParameter("demographicNo", demographic_no);
                return query.list();

            } else if (issues.length == 1) {
                long id = Long.parseLong(issues[0]);

                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = :issueId and cmn.demographic_no= :demographicNo and cmn.archived=0 order by cmn.position, cmn.observation_date desc";

                Query<CaseManagementNote> query1 = getSession().createQuery(hql);
                query1.setParameter("issueId", id);
                query1.setParameter("demographicNo", demographic_no);
                List<CaseManagementNote> issueList = query1.list();

                hql = "select  max(cmn.id) from CaseManagementNote cmn where cmn.demographic_no = :demographicNo group by cmn.uuid order by max(cmn.id)";
                Query<Integer> query2 = getSession().createQuery(hql);
                query2.setParameter("demographicNo", demographic_no);
                List<Integer> currNoteList = query2.list();

                for (CaseManagementNote issueNote : issueList) {
                    if (currNoteList.contains(issueNote.getId())) {
                        issueListReturn.add(issueNote);
                    }
                }
                return issueListReturn;
            }
        }

        return issueListReturn;
    }

    /**
     * Retrieves notes for a demographic filtered by issues with optional limit.
     *
     * @param demographic_no the demographic number
     * @param issueIds array of issue IDs to filter by
     * @param maxNotes maximum number of notes to return (-1 for unlimited)
     * @return list of most recent notes matching criteria
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String[] issueIds, Integer maxNotes) {

        List<CaseManagementNote> retList = new ArrayList<CaseManagementNote>();
        String list = null;
        String hql;
        if (issueIds != null) {
            if (issueIds.length > 1) {
                list = "";
                for (int x = 0; x < issueIds.length; x++) {
                    if (x != 0) {
                        list += ",";
                    }
                    list += issueIds[x];
                }
                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (" + list
                        + ") and cmn.demographic_no = :demographicNo and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid) order by cmn.observation_date desc ";
                Query<CaseManagementNote> query = getSession().createQuery(hql);
                query.setParameter("demographicNo", demographic_no);
                if (maxNotes != -1) {
                    query.setMaxResults(maxNotes);
                }
                retList = query.list();

            } else if (issueIds.length == 1) {
                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = :issueId and cmn.demographic_no = :demographicNo and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid) order by cmn.observation_date desc";
                long id = Long.parseLong(issueIds[0]);
                Query<CaseManagementNote> query = getSession().createQuery(hql);
                query.setParameter("issueId", id);
                query.setParameter("demographicNo", demographic_no);
                if (maxNotes != -1) {
                    query.setMaxResults(maxNotes);
                }
                retList = query.list();
            }
        }

        // String hql = "select distinct cmn from CaseManagementNote cmn where
        // cmn.demographic_no = ? and cmn.issues.issue_id in (" + list +
        // ") and cmn.id in (select max(cmn.id) from cmn GROUP BY uuid) ORDER BY
        // cmn.observation_date asc";
        return retList;
    }

    /**
     * Retrieves notes for a demographic filtered by issues.
     *
     * @param demographic_no the demographic number
     * @param issueIds array of issue IDs to filter by
     * @return list of most recent notes matching criteria
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String[] issueIds) {
        String list = null;
        String hql;
        if (issueIds != null) {
            if (issueIds.length > 1) {
                list = "";
                for (int x = 0; x < issueIds.length; x++) {
                    if (x != 0) {
                        list += ",";
                    }
                    list += issueIds[x];
                }
                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (" + list
                        + ") and cmn.demographic_no = :demographicNo and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid)";
                Query<CaseManagementNote> query = getSession().createQuery(hql);
                query.setParameter("demographicNo", demographic_no);
                return query.list();

            } else if (issueIds.length == 1) {
                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = :issueId and cmn.demographic_no = :demographicNo and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid)";
                long id = Long.parseLong(issueIds[0]);
                Query<CaseManagementNote> query = getSession().createQuery(hql);
                query.setParameter("issueId", id);
                query.setParameter("demographicNo", demographic_no);
                return query.list();
            }
        }
        // String hql = "select distinct cmn from CaseManagementNote cmn where
        // cmn.demographic_no = ? and cmn.issues.issue_id in (" + list +
        // ") and cmn.id in (select max(cmn.id) from cmn GROUP BY uuid) ORDER BY
        // cmn.observation_date asc";
        return new ArrayList<CaseManagementNote>();
    }

    /**
     * Finds notes for a demographic filtered by issue codes.
     * <p>
     * Returns unique notes (by UUID) sorted by observation date. Uses native SQL query
     * to join across issue, case management issue, and note tables.
     * </p>
     *
     * @param demographic_no the demographic number
     * @param issueCodes array of issue codes to filter by (optional)
     * @return collection of notes sorted by observation date
     */
    @Override
    public Collection<CaseManagementNote> findNotesByDemographicAndIssueCode(Integer demographic_no,
                                                                             String[] issueCodes) {
        Session session = getSession();
        List<CaseManagementNote> notes = new ArrayList<CaseManagementNote>();
        try {
            StringBuilder sqlCommand = new StringBuilder(
                "select distinct casemgmt_note.note_id from issue,casemgmt_issue,casemgmt_issue_notes,casemgmt_note " +
                "where casemgmt_issue.issue_id=issue.issue_id and casemgmt_issue.demographic_no=:demographicNo ");
            
            if (issueCodes != null && issueCodes.length > 0) {
                sqlCommand.append("and issue.code in (:issueCodes) ");
            }
            
            sqlCommand.append("and casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_issue_notes.note_id=casemgmt_note.note_id");
            
            SQLQuery query = session.createSQLQuery(sqlCommand.toString());
            query.setParameter("demographicNo", demographic_no);
            
            if (issueCodes != null && issueCodes.length > 0) {
                query.setParameterList("issueCodes", issueCodes);
            }
            
            @SuppressWarnings("unchecked")
            List<Integer> ids = query.list();
            for (Integer id : ids)
                notes.add(getNote(id.longValue()));
        } finally {
            //session.close();
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


    /**
     * Retrieves notes for a demographic within a specific date range.
     *
     * @param demographic_no the demographic number
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @return list of most recent notes within the date range
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographicDateRange(String demographic_no, Date startDate,
                                                                   Date endDate) {
        Query<CaseManagementNote> query = getSession().getNamedQuery("mostRecentDateRange");
        query.setParameter(0, demographic_no);
        query.setParameter(1, startDate);
        query.setParameter(2, endDate);
        return query.list();
    }

    /**
     * Retrieves a limited number of notes for a demographic with pagination.
     *
     * @param demographic_no the demographic number
     * @param offset the starting offset for pagination
     * @param numToReturn the number of notes to return
     * @return list of notes with pagination applied
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographicLimit(String demographic_no, Integer offset,
                                                               Integer numToReturn) {
        Query<CaseManagementNote> query = getSession().getNamedQuery("mostRecentLimit");
        query.setParameter(0, demographic_no);
        query.setParameter(1, offset);
        query.setParameter(2, numToReturn);
        return query.list();
    }

    /**
     * Updates an existing case management note.
     * <p>
     * The update date is automatically set to the current time before updating.
     * </p>
     *
     * @param note the CaseManagementNote to update
     */
    @Override
    @Transactional(readOnly = false)
    public void updateNote(CaseManagementNote note) {
        note.setUpdate_date(new Date());
        getSession().update(note);
        getSession().flush();
    }

    /**
     * Saves a new case management note.
     * <p>
     * If the note doesn't have a UUID, one is automatically generated.
     * If the update date is not set, it's set to the current time.
     * </p>
     *
     * @param note the CaseManagementNote to save
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
        getSession().save(note);
        getSession().flush();
    }

    /**
     * Saves a new case management note and returns the generated identifier.
     * <p>
     * If the note doesn't have a UUID, one is automatically generated.
     * If the update date is not set, it's set to the current time.
     * </p>
     *
     * @param note the CaseManagementNote to save
     * @return the generated identifier for the saved note
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
        return getSession().save(note);
    }

    /**
     * Searches for case management notes based on various criteria.
     * <p>
     * Uses Hibernate Criteria API to build a dynamic query based on the search parameters
     * provided in the search bean.
     * </p>
     *
     * @param searchBean the search criteria containing demographic number, role ID, program ID, and date range
     * @return list of notes matching the search criteria, or null if parsing errors occur
     */
    @Override
    public List<CaseManagementNote> search(CaseManagementSearchBean searchBean) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        Session session = getSession();

        List<CaseManagementNote> results = null;

        try {
            Criteria criteria = session.createCriteria(CaseManagementNote.class);

            criteria.add(Expression.eq("demographic_no", searchBean.getDemographicNo()));

            if (searchBean.getSearchRoleId() > 0) {
                criteria.add(Expression.eq("reporter_caisi_role", String.valueOf(searchBean.getSearchRoleId())));
            }

            if (searchBean.getSearchProgramId() > 0) {
                criteria.add(Expression.eq("program_no", String.valueOf(searchBean.getSearchProgramId())));
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
            criteria.add(Restrictions.between("update_date", startDate, endDate));

            criteria.addOrder(Order.desc("update_date"));
            results = criteria.list();

        } catch (ParseException e) {
            log.warn("Warning", e);
        } finally {
            //session.close();
        }

        return results;

    }

    /**
     * Retrieves all note IDs from the database.
     *
     * @return list of all note IDs
     */
    @Override
    public List<Long> getAllNoteIds() {
        @SuppressWarnings("unchecked")
        Query<Long> query = getSession().createQuery("select n.id from CaseManagementNote n");
        List<Long> results = query.list();
        return results;
    }

    /**
     * Checks if an issue exists for a note.
     *
     * @param issid the issue ID
     * @param demoNo the demographic number (unused parameter)
     * @return true if the issue exists, false otherwise
     */
    @Override
    public boolean haveIssue(Long issid, String demoNo) {
        Session session = getSession();
        try {
            SQLQuery query = session.createSQLQuery("select * from casemgmt_issue_notes where id=:issueId");
            query.setParameter("issueId", issid.longValue());
            List results = query.list();
            // log.info("haveIssue - DAO - # of results = " + results.size());
            if (results.size() > 0)
                return true;
            return false;
        } finally {
            //session.close();
        }
    }

    /**
     * Checks if a specific issue code exists for a demographic.
     *
     * @param issueCode the issue code to check
     * @param demographicId the demographic ID
     * @return true if the issue exists for the demographic, false otherwise
     */
    @Override
    public boolean haveIssue(String issueCode, Integer demographicId) {
        Session session = getSession();
        try {
            SQLQuery query = session.createSQLQuery(
                    "select casemgmt_issue.id from casemgmt_issue_notes,casemgmt_issue,issue " +
                    "where issue.issue_id=casemgmt_issue.issue_id and casemgmt_issue.id=casemgmt_issue_notes.id " +
                    "and demographic_no=:demographicId and issue.code=:issueCode");
            query.setParameter("demographicId", demographicId);
            query.setParameter("issueCode", issueCode);
            List results = query.list();
            // log.info("haveIssue - DAO - # of results = " + results.size());
            if (results.size() > 0)
                return true;
            return false;
        } finally {
            //session.close();
        }
    }

    /**
     * Counts distinct notes (by UUID) for a provider within a date range.
     *
     * @param providerNo the provider number
     * @param startDate the start date of the range (inclusive)
     * @param endDate the end date of the range (inclusive)
     * @return count of distinct notes, or 0 if an error occurs
     */
    @Override
    public int getNoteCountForProviderForDateRange(String providerNo, Date startDate, Date endDate) {
        try {
            Session session = getSession();
            String sqlCommand = "select count(distinct uuid) from casemgmt_note where provider_no = :providerNo and observation_date >= :startDate and observation_date <= :endDate";

            @SuppressWarnings("unchecked")
            NativeQuery<BigInteger> query = session.createNativeQuery(sqlCommand);
            query.setParameter("providerNo", providerNo);
            query.setParameter("startDate", new Timestamp(startDate.getTime()));
            query.setParameter("endDate", new Timestamp(endDate.getTime()));

            BigInteger result = query.uniqueResult();
            return result != null ? result.intValue() : 0;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return 0;
        }
    }

    /**
     * Counts distinct notes (by UUID) for a provider within a date range filtered by issue code.
     *
     * @param providerNo the provider number
     * @param startDate the start date of the range (inclusive)
     * @param endDate the end date of the range (inclusive)
     * @param issueCode the issue code to filter by
     * @return count of distinct notes matching criteria, or 0 if issue not found or error occurs
     */
    @Override
    public int getNoteCountForProviderForDateRangeWithIssueId(String providerNo, Date startDate, Date endDate,
                                                              String issueCode) {
        try {
            Session session = getSession();

            // Step 1: Get issue_id from issue code
            String getIssueIdSql = "SELECT issue_id FROM issue WHERE code = :issueCode";
            log.debug(getIssueIdSql);

            @SuppressWarnings("unchecked")
            NativeQuery<Integer> issueQuery = session.createNativeQuery(getIssueIdSql);
            issueQuery.setParameter("issueCode", issueCode);

            Integer issueId = issueQuery.uniqueResult();
            if (issueId == null) {
                log.debug("Could not find issueCode: " + issueCode);
                return 0;
            }

            log.debug("issue Code " + issueCode + " id :" + issueId);

            // Step 2: Count notes with the issue_id
            String sqlCommand = "select count(distinct uuid) from casemgmt_issue c, casemgmt_issue_notes cin, casemgmt_note cn where c.issue_id = :issueId and c.id = cin.id and cin.note_id = cn.note_id and cn.provider_no = :providerNo and observation_date >= :startDate and observation_date <= :endDate";
            log.debug(sqlCommand);

            @SuppressWarnings("unchecked")
            NativeQuery<BigInteger> countQuery = session.createNativeQuery(sqlCommand);
            countQuery.setParameter("issueId", issueId);
            countQuery.setParameter("providerNo", providerNo);
            countQuery.setParameter("startDate", new Timestamp(startDate.getTime()));
            countQuery.setParameter("endDate", new Timestamp(endDate.getTime()));

            BigInteger result = countQuery.uniqueResult();
            int finalCount = result != null ? result.intValue() : 0;
            return finalCount;
        } catch (Exception e) {
            log.error("Error counting notes for issue :" + issueCode, e);
            return 0;
        }
    }

    /**
     * Searches for notes containing a specific string in the note text.
     * Used by decision support to search through notes.
     *
     * @param demographic_no the demographic number
     * @param searchString the string to search for in note text
     * @return list of most recent non-archived notes containing the search string
     */
    @Override
    public List<CaseManagementNote> searchDemographicNotes(String demographic_no, String searchString) {
        String hql = "select distinct cmn from CaseManagementNote cmn where cmn.id in (select max(cmn.id) from cmn where cmn.demographic_no = :demographicNo GROUP BY uuid) and cmn.demographic_no = :demographicNo2 and cmn.note like :searchString and cmn.archived = 0";

        @SuppressWarnings("unchecked")
        Query<CaseManagementNote> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", demographic_no);
        query.setParameter("demographicNo2", demographic_no);
        query.setParameter("searchString", searchString);
        List<CaseManagementNote> result = query.list();
        return result;
    }

    /**
     * Retrieves case management notes by program ID and observation date range.
     *
     * @param programId the program ID to filter by
     * @param minObservationDate the minimum observation date
     * @param maxObservationDate the maximum observation date
     * @return list of notes matching the criteria
     */
    @Override
    public List<CaseManagementNote> getCaseManagementNoteByProgramIdAndObservationDate(Integer programId,
                                                                                       Date minObservationDate, Date maxObservationDate) {
        String queryStr = "FROM CaseManagementNote x WHERE x.program_no=:programId and x.observation_date>=:minDate and x.observation_date<=:maxDate";

        @SuppressWarnings("unchecked")
        Query<CaseManagementNote> query = getSession().createQuery(queryStr);
        query.setParameter("programId", programId.toString());
        query.setParameter("minDate", minObservationDate);
        query.setParameter("maxDate", maxObservationDate);
        List<CaseManagementNote> rs = query.list();

        return rs;
    }

    /**
     * Retrieves the most recent notes for a specific appointment.
     *
     * @param appointmentNo the appointment number
     * @return list of most recent notes associated with the appointment
     */
    @Override
    public List<CaseManagementNote> getMostRecentNotesByAppointmentNo(int appointmentNo) {
        String hql = "select distinct cmn.uuid from CaseManagementNote cmn where cmn.appointmentNo = :appointmentNo";
        @SuppressWarnings("unchecked")
        Query<String> query = getSession().createQuery(hql);
        query.setParameter("appointmentNo", appointmentNo);
        List<String> tmp = query.list();
        List<CaseManagementNote> mostRecents = new ArrayList<CaseManagementNote>();
        for (String uuid : tmp) {
            mostRecents.add(this.getMostRecentNote(uuid));
        }
        return mostRecents;
    }

    /**
     * Retrieves the most recent notes for a specific demographic.
     *
     * @param demographicNo the demographic number
     * @return list of most recent notes for each unique UUID
     */
    @Override
    public List<CaseManagementNote> getMostRecentNotes(Integer demographicNo) {
        String hql = "select distinct cmn.uuid from CaseManagementNote cmn where cmn.demographic_no = :demographicNo";
        @SuppressWarnings("unchecked")
        Query<String> query = getSession().createQuery(hql);
        query.setParameter("demographicNo", String.valueOf(demographicNo));
        List<String> tmp = query.list();
        List<CaseManagementNote> mostRecents = new ArrayList<CaseManagementNote>();
        for (String uuid : tmp) {
            mostRecents.add(this.getMostRecentNote(uuid));
        }
        return mostRecents;
    }

    /**
     * Finds the maximum note ID in the database.
     *
     * @return the maximum note ID, or 0 if no notes exist
     */
    @Override
    public Long findMaxNoteId() {
        String sql = "select max(c.id) from CaseManagementNote c";
        @SuppressWarnings("unchecked")
        Query<Object> query = getSession().createQuery(sql);
        List<Object> r = query.list();
        if (r.isEmpty()) {
            return 0L;
        }
        return (Long) r.get(0);

    }

    /**
     * Retrieves notes for a facility (programs) updated since a specific date.
     * <p>
     * Note: This method uses string concatenation for program IDs in the HQL query.
     * This is acceptable here as program IDs are numeric values from trusted sources.
     * </p>
     *
     * @param date the date to filter by (notes updated after this date)
     * @param programs list of programs to filter by
     * @return list of demographic numbers with notes matching the criteria
     */
    @Override
    public List<Integer> getNotesByFacilitySince(Date date, List<Program> programs) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Program p : programs) {
            if (i++ > 0)
                sb.append(",");
            sb.append(p.getId());
        }
        String hql = "select distinct cmn.demographic_no from CaseManagementNote cmn where cmn.program_no in ("
                + sb.toString()
                + ") and cmn.update_date > :date and cmn.locked != '1' and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        Query<String> query = getSession().createQuery(hql);
        query.setParameter("date", date);
        List<String> results = query.list();

        List<Integer> results2 = new ArrayList<Integer>();
        for (String r : results) {
            results2.add(Integer.parseInt(r));
        }
        return results2;
    }
}
