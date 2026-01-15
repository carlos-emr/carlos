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
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.OscarProperties;
import ca.openosp.openo.util.SqlUtils;

@Transactional
public class CaseManagementNoteDAOImpl extends HibernateDaoSupport implements CaseManagementNoteDAO {

    private static Logger log = MiscUtils.getLogger();

    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> findAll() {
        logger.warn(
                "A METHOD THAT IS LIKELY TO CAUSE A CRASH HAS BEEN INVOKED. PLEASE LIMIT THE USE OF THIS METHOD, AS IT'S LIKELY TO EXHAUST MEMORY AND MAY LEAD TO A SERVER CRASH. CONSIDER PAGINATING THE INVOCATION INSTEAD");
        return (List<CaseManagementNote>) this.getHibernateTemplate().find("FROM CaseManagementNote");
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getEditors(CaseManagementNote note) {
        String uuid = note.getUuid();
        String hql = "select distinct p from Provider p, CaseManagementNote cmn where p.ProviderNo = cmn.providerNo and cmn.uuid = ?0";
        return (List<Provider>) this.getHibernateTemplate().find(hql, uuid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getAllEditors(String demographicNo) {
        String hql = "select distinct p from Provider p, CaseManagementNote cmn where p.ProviderNo = cmn.providerNo and cmn.demographic_no = ?0";
        return (List<Provider>) this.getHibernateTemplate().find(hql, demographicNo);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getHistory(CaseManagementNote note) {
        String uuid = note.getUuid();
        return (List<CaseManagementNote>) this.getHibernateTemplate()
                .find("from CaseManagementNote cmn where cmn.uuid = ?0 order by cmn.update_date asc", uuid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getIssueHistory(String issueIds, String demoNo) {
        String hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id in (" + issueIds
                + ") and cmn.demographic_no= ?0 ORDER BY cmn.observation_date asc";

        List<CaseManagementNote> issueListReturn = new ArrayList<CaseManagementNote>();

        List<CaseManagementNote> issueList = (List<CaseManagementNote>) this.getHibernateTemplate().find(hql, demoNo);

        hql = "select max(cmn.id) from CaseManagementNote cmn join cmn.issues i where i.issue_id in (" + issueIds
                + ") and cmn.demographic_no = ?0 group by cmn.uuid order by max(cmn.id)";
        List<Integer> currNoteList = (List<Integer>) this.getHibernateTemplate().find(hql, demoNo);
        for (CaseManagementNote issueNote : issueList) {
            if (currNoteList.contains(issueNote.getId())) {
                issueListReturn.add(issueNote);
            }
        }

        return issueListReturn;
    }

    @Override
    public CaseManagementNote getNote(Long id) {
        CaseManagementNote note = this.getHibernateTemplate().get(CaseManagementNote.class, id);
        getHibernateTemplate().initialize(note.getIssues());
        return note;
    }

    @Override
    public List<CaseManagementNote> getNotes(List<Long> ids) {
        if (ids.size() == 0)
            return new ArrayList<CaseManagementNote>();
        @SuppressWarnings("unchecked")
        List<CaseManagementNote> notes = (List<CaseManagementNote>) this.getHibernateTemplate()
                .find("SELECT n FROM CaseManagementNote n WHERE n.id IN (?0)", new Object[]{ids});
        return notes;
    }

    @Override
    public CaseManagementNote getMostRecentNote(String uuid) {
        String hql = "select cmn from CaseManagementNote cmn " +
                    "where cmn.uuid = :uuid and cmn.id = (" +
                    "select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = :uuid)";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> tmp = (List<CaseManagementNote>) this.getHibernateTemplate()
            .findByNamedParam(hql, "uuid", uuid);

        if (tmp == null || tmp.isEmpty())
            return null;

        return tmp.get(0);
    }

    @Override
    public List<CaseManagementNote> getNotesByUUID(String uuid) {
        String hql = "select cmn from CaseManagementNote cmn where cmn.uuid = ?0 order by cmn.id";
        @SuppressWarnings("unchecked")
        List<CaseManagementNote> ret = (List<CaseManagementNote>) this.getHibernateTemplate().find(hql, uuid);
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

        String hql = "select distinct cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = ?0 and cmn.demographic_no = ?1 and cmn.observation_date >= ?2  and cmn.id in (select max(cmn.id) from cmn where cmn.demographic_no = ?3 GROUP BY uuid) ORDER BY cmn.observation_date asc";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> result = (List<CaseManagementNote>) getHibernateTemplate().find(hql,
                new Object[]{issueId, demoNo, d, demoNo});
        return result;
    }

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
                + ") and cmn.demographic_no = ?0  and cmn.id in (select max(cmn.id) from cmn where cmn.observation_date >= ?1 GROUP BY uuid) ORDER BY cmn.observation_date asc";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> result = (List<CaseManagementNote>) getHibernateTemplate().find(hql,
                new Object[]{demographic_no, d});
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String staleDate) {
        if (OscarProperties.getInstance().getDbType().equals("oracle")) {
            return (List<CaseManagementNote>) getHibernateTemplate().findByNamedQuery("mostRecentTimeOra",
                    new Object[]{demographic_no, staleDate});
        } else {
            return (List<CaseManagementNote>) getHibernateTemplate().findByNamedQuery("mostRecentTime",
                    new Object[]{demographic_no, staleDate});
        }
    }

    // This was created by OSCAR. if all notes' UUID are same like null, it will
    // only get one note.
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no) {
        if (OscarProperties.getInstance().getDbType().equals("oracle")) {
            return (List<CaseManagementNote>) getHibernateTemplate().findByNamedQuery("mostRecentOra",
                    new Object[]{demographic_no});
        } else {
            String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?0 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
            return (List<CaseManagementNote>) getHibernateTemplate().find(hql, demographic_no);
            // return getHibernateTemplate().findByNamedQuery("mostRecent", new Object[] {
            // demographic_no });

        }
    }

    @Override
    public List<CaseManagementNote> getNotesByDemographicSince(String demographic_no, Date date) {

        String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?0 and cmn.update_date > ?1 and cmn.locked != '1' and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        return (List<CaseManagementNote>) getHibernateTemplate().find(hql, demographic_no, date);
    }

    @Override
    public long getNotesCountByDemographicId(String demographic_no) {
        String hql = "select count(*) from CaseManagementNote cmm where cmm.demographic_no = ?0";
        return ((Long) getHibernateTemplate().find(hql, demographic_no).get(0)).longValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Object[]> getRawNoteInfoByDemographic(String demographic_no) {
        String hql = "select cmn.id,cmn.observation_date,cmn.providerNo,cmn.program_no,cmn.reporter_caisi_role,cmn.uuid from CaseManagementNote cmn where cmn.demographic_no = ?0 order by cmn.update_date DESC";
        return (List<Object[]>) getHibernateTemplate().find(hql, demographic_no);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> getRawNoteInfoMapByDemographic(String demographic_no) {
        String hql = "select new map(cmn.id as id,cmn.observation_date as observation_date,cmn.providerNo as providerNo,cmn.program_no as program_no,cmn.reporter_caisi_role as reporter_caisi_role,cmn.uuid as uuid, cmn.update_date as update_date) from CaseManagementNote cmn where cmn.demographic_no = ?0 order by cmn.update_date DESC";
        return (List<Map<String, Object>>) getHibernateTemplate().find(hql, demographic_no);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> getUnsignedRawNoteInfoMapByDemographic(String demographic_no) {
        String hql = "select new map(cmn.id as id,cmn.observation_date as observation_date,cmn.providerNo as providerNo,cmn.program_no as program_no,cmn.reporter_caisi_role as reporter_caisi_role,cmn.uuid as uuid, cmn.update_date as update_date) from CaseManagementNote cmn where cmn.demographic_no = ?0 and cmn.signed=?1 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.update_date DESC";
        return (List<Map<String, Object>>) getHibernateTemplate().find(hql, new Object[]{demographic_no, false});
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, Integer maxNotes) {
        if (OscarProperties.getInstance().getDbType().equals("oracle")) {
            return (List<CaseManagementNote>) getHibernateTemplate().findByNamedQuery("mostRecentOra",
                    new Object[]{demographic_no});
        } else {
            String hql = "select cmn from CaseManagementNote cmn where cmn.demographic_no = ?0 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date desc";

            HibernateTemplate Hibernatetemplate = getHibernateTemplate();
            if (maxNotes != -1) {
                Hibernatetemplate.setMaxResults(maxNotes);
            }

            List<CaseManagementNote> list = (List<CaseManagementNote>) Hibernatetemplate.find(hql, demographic_no);
            Hibernatetemplate.setMaxResults(0);
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
                        + ") and cmn.demographic_no = ?0 and cmn.archived = 0 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid) ORDER BY cmn.position, cmn.observation_date desc";
                return (List<CaseManagementNote>) this.getHibernateTemplate().find(hql, demographic_no);

            } else if (issues.length == 1) {
                long id = Long.parseLong(issues[0]);

                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = ?0 and cmn.demographic_no= ?1 and cmn.archived=0 order by cmn.position, cmn.observation_date desc";

                List<CaseManagementNote> issueList = (List<CaseManagementNote>) this.getHibernateTemplate().find(hql,
                        new Object[]{id, demographic_no});

                hql = "select  max(cmn.id) from CaseManagementNote cmn where cmn.demographic_no = ?0 group by cmn.uuid order by max(cmn.id)";
                List<Integer> currNoteList = (List<Integer>) this.getHibernateTemplate().find(hql,
                        new Object[]{demographic_no});

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

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographic(String demographic_no, String[] issueIds, Integer maxNotes) {

        HibernateTemplate hibernateTemplate = getHibernateTemplate();
        if (maxNotes != -1) {
            hibernateTemplate.setMaxResults(maxNotes);
        }
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
                        + ") and cmn.demographic_no = ?0 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid) order by cmn.observation_date desc ";
                retList = (List<CaseManagementNote>) this.getHibernateTemplate().find(hql, demographic_no);

            } else if (issueIds.length == 1) {
                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = ?0 and cmn.demographic_no = ?1 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid) order by cmn.observation_date desc";
                long id = Long.parseLong(issueIds[0]);
                retList = (List<CaseManagementNote>) this.getHibernateTemplate().find(hql,
                        new Object[]{id, demographic_no});
            }
        }

        hibernateTemplate.setMaxResults(0);
        // String hql = "select distinct cmn from CaseManagementNote cmn where
        // cmn.demographic_no = ? and cmn.issues.issue_id in (" + list +
        // ") and cmn.id in (select max(cmn.id) from cmn GROUP BY uuid) ORDER BY
        // cmn.observation_date asc";
        return retList;
    }

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
                        + ") and cmn.demographic_no = ?0 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid)";
                return (List<CaseManagementNote>) this.getHibernateTemplate().find(hql, demographic_no);

            } else if (issueIds.length == 1) {
                hql = "select cmn from CaseManagementNote cmn join cmn.issues i where i.issue_id = ?0 and cmn.demographic_no = ?1 and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn.uuid = cmn2.uuid)";
                long id = Long.parseLong(issueIds[0]);
                return (List<CaseManagementNote>) this.getHibernateTemplate().find(hql,
                        new Object[]{id, demographic_no});
            }
        }
        // String hql = "select distinct cmn from CaseManagementNote cmn where
        // cmn.demographic_no = ? and cmn.issues.issue_id in (" + list +
        // ") and cmn.id in (select max(cmn.id) from cmn GROUP BY uuid) ORDER BY
        // cmn.observation_date asc";
        return new ArrayList<CaseManagementNote>();
    }

    @Override
    public Collection<CaseManagementNote> findNotesByDemographicAndIssueCode(Integer demographic_no,
                                                                             String[] issueCodes) {
        Session session = currentSession();
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


    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographicDateRange(String demographic_no, Date startDate,
                                                                   Date endDate) {
        return (List<CaseManagementNote>) getHibernateTemplate().findByNamedQuery("mostRecentDateRange",
                new Object[]{demographic_no, startDate, endDate});
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNote> getNotesByDemographicLimit(String demographic_no, Integer offset,
                                                               Integer numToReturn) {
        return (List<CaseManagementNote>) getHibernateTemplate().findByNamedQuery("mostRecentLimit",
                new Object[]{demographic_no, offset, numToReturn});
    }

    @Override
    @Transactional(readOnly = false)
    public void updateNote(CaseManagementNote note) {
        note.setUpdate_date(new Date());
        this.getHibernateTemplate().update(note);
        this.getHibernateTemplate().flush();
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
        this.getHibernateTemplate().save(note);
        this.getHibernateTemplate().flush();
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
        return this.getHibernateTemplate().save(note);
    }

    @Override
    public List<CaseManagementNote> search(CaseManagementSearchBean searchBean) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        // Session session = getSession();
        Session session = currentSession();

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

    @Override
    public List<Long> getAllNoteIds() {
        @SuppressWarnings("unchecked")
        List<Long> results = (List<Long>) this.getHibernateTemplate().find("select n.id from CaseManagementNote n");
        return results;
    }

    @Override
    public boolean haveIssue(Long issid, String demoNo) {
        // Session session = getSession();
        Session session = currentSession();
        try {
            SQLQuery query = session.createSQLQuery("select * from casemgmt_issue_notes where id=" + issid.longValue());
            List results = query.list();
            // log.info("haveIssue - DAO - # of results = " + results.size());
            if (results.size() > 0)
                return true;
            return false;
        } finally {
            //session.close();
        }
    }

    @Override
    public boolean haveIssue(String issueCode, Integer demographicId) {
        // Session session=getSession();
        Session session = currentSession();
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

    /*
     * select issue_id from issue where code = 'Concerns';
     */

    @Override
    public int getNoteCountForProviderForDateRange(String providerNo, Date startDate, Date endDate) {
        try {
            Session session = currentSession();
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

    // used by decision support to search through the notes for a string
    @Override
    public List<CaseManagementNote> searchDemographicNotes(String demographic_no, String searchString) {
        String hql = "select distinct cmn from CaseManagementNote cmn where cmn.id in (select max(cmn.id) from cmn where cmn.demographic_no = ?0 GROUP BY uuid) and cmn.demographic_no = ?1 and cmn.note like ?2 and cmn.archived = 0";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> result = (List<CaseManagementNote>) getHibernateTemplate().find(hql,
                new Object[]{demographic_no, demographic_no, searchString});
        return result;
    }

    @Override
    public List<CaseManagementNote> getCaseManagementNoteByProgramIdAndObservationDate(Integer programId,
                                                                                       Date minObservationDate, Date maxObservationDate) {
        String queryStr = "FROM CaseManagementNote x WHERE x.program_no=?0 and x.observation_date>=?1 and x.observation_date<=?2";

        @SuppressWarnings("unchecked")
        List<CaseManagementNote> rs = (List<CaseManagementNote>) getHibernateTemplate().find(queryStr,
                new Object[]{programId.toString(), minObservationDate, maxObservationDate});

        return rs;
    }

    @Override
    public List<CaseManagementNote> getMostRecentNotesByAppointmentNo(int appointmentNo) {
        String hql = "select distinct cmn.uuid from CaseManagementNote cmn where cmn.appointmentNo = ?0";
        @SuppressWarnings("unchecked")
        List<String> tmp = (List<String>) this.getHibernateTemplate().find(hql, appointmentNo);
        List<CaseManagementNote> mostRecents = new ArrayList<CaseManagementNote>();
        for (String uuid : tmp) {
            mostRecents.add(this.getMostRecentNote(uuid));
        }
        return mostRecents;
    }

    @Override
    public List<CaseManagementNote> getMostRecentNotes(Integer demographicNo) {
        String hql = "select distinct cmn.uuid from CaseManagementNote cmn where cmn.demographic_no = ?0";
        @SuppressWarnings("unchecked")
        List<String> tmp = (List<String>) this.getHibernateTemplate().find(hql,
                new Object[]{String.valueOf(demographicNo)});
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
        List<Object> r = (List<Object>) getHibernateTemplate().find(sql);
        if (r.isEmpty()) {
            return 0L;
        }
        return (Long) r.get(0);

    }

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
                + ") and cmn.update_date > ?0 and cmn.locked != '1' and cmn.id = (select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.uuid = cmn.uuid) order by cmn.observation_date";
        List<String> results = (List<String>) getHibernateTemplate().find(hql, date);

        List<Integer> results2 = new ArrayList<Integer>();
        for (String r : results) {
            results2.add(Integer.parseInt(r));
        }
        return results2;
    }
}
