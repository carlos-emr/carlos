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
package io.github.carlos_emr.carlos.commn.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Map.Entry;

import org.apache.logging.log4j.Logger;
import org.hibernate.HibernateException;
import org.joda.time.Days;
import org.joda.time.MutablePeriod;
import org.joda.time.PeriodType;
import io.github.carlos_emr.carlos.PMmodule.utility.DateTimeFormatUtils;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Stay;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.EncounterUtil.EncounterType;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;

import io.github.carlos_emr.carlos.util.SqlUtils;

public class PopulationReportDaoImpl extends HibernateDaoSupport implements PopulationReportDao {


    private static final Logger logger = MiscUtils.getLogger();

    private static final String HQL_CURRENT_POP_SIZE = "select count(distinct a.clientId) from Admission a where " +
    "a.programId in (select p.id from Program p where lower(p.programStatus) = 'active' and lower(p.type) = 'service') and " +
    "a.clientId in (select d.DemographicNo from Demographic d where lower(d.PatientStatus) = 'ac') and " +
    "a.dischargeDate is null";

    private static final String HQL_CURRENT_HISTORICAL_POP_SIZE = "select count(distinct a.clientId) from Admission a where " +
    "a.programId in (select p.id from Program p where lower(p.programStatus) = 'active' and lower(p.type) = 'service') and " +
    "a.clientId in (select d.DemographicNo from Demographic d where lower(d.PatientStatus) = 'ac') and " +
    "(a.dischargeDate is null or a.dischargeDate > ?1)";

    private static final String HQL_GET_USAGES = "select a.clientId, a.admissionDate, a.dischargeDate from ?1 a where " +
    "a.programId in (select p.id from Program p where lower(p.programStatus) = 'active' and lower(p.type) = 'service') and " +
    "a.clientId in (select d.DemographicNo from Demographic d where lower(d.PatientStatus) = 'ac') and " +
    "(a.dischargeDate is null or a.dischargeDate > ?2) " +
    "order by a.clientId, a.admissionDate";

    private static final String HQL_GET_MORTALITIES = "select count(distinct a.clientId) from Admission a where " +
     "a.programId in (select p.id from Program p where lower(p.programStatus) = 'active' and lower(p.type) = 'community' and lower(p.name) = 'deceased') and " +
     "a.admissionDate > ?1 and a.dischargeDate is null";

    private static final String HQL_GET_PREVALENCE = "select count(cmi) from CaseManagementIssue cmi where cmi.resolved = false and " +
    "cmi.demographic_no in (select distinct a.clientId from Admission a where a.programId in (select p.id from Program p where " +
    "lower(p.programStatus) = 'active' and lower(p.type) = 'service') and a.clientId in (select d.DemographicNo from Demographic d where " +
    "lower(d.PatientStatus) = 'ac') and a.dischargeDate is null) and cmi.issue.code in ";

    private static final String HQL_GET_INCIDENCE = "select count(cmi) from CaseManagementIssue cmi where " +
    "cmi.demographic_no in (select distinct a.clientId from Admission a where a.programId in (select p.id from Program p where " +
    "lower(p.programStatus) = 'active' and lower(p.type) = 'service') and a.clientId in (select d.DemographicNo from Demographic d where " +
    "lower(d.PatientStatus) = 'ac') and a.dischargeDate is null) and cmi.issue.code in ";

    public int getCurrentPopulationSize() {

        return ((Long) getHibernateTemplate().find(HQL_CURRENT_POP_SIZE).iterator().next()).intValue();
    }

    @Override
    public int getCurrentAndHistoricalPopulationSize(int numYears) {

        return ((Long) getHibernateTemplate().find(HQL_CURRENT_HISTORICAL_POP_SIZE, DateTimeFormatUtils.getPast(numYears)).iterator().next()).intValue();
    }

    @Override
    public int[] getUsages(int numYears) {

        int[] shelterUsages = new int[3];

        Map<Integer, Set<Stay>> clientIdToStayMap = new HashMap<Integer, Set<Stay>>();

        Calendar instant = Calendar.getInstance();
        Date end = instant.getTime();
        Date start = DateTimeFormatUtils.getPast(instant, numYears);

        for (Object o : getHibernateTemplate().find(HQL_GET_USAGES, start)) {
            Object[] tuple = (Object[]) o;

            Integer clientId = (Integer) tuple[0];
            Date admission = (Date) tuple[1];
            Date discharge = (Date) tuple[2];

            if (!clientIdToStayMap.containsKey(clientId)) {
                clientIdToStayMap.put(clientId, new HashSet<Stay>());
            }

            try {
                Stay stay = new Stay(admission, discharge, start, end);
                clientIdToStayMap.get(clientId).add(stay);
            } catch (IllegalArgumentException e) {
                logger.error("client id: " + clientId);
            }
        }

        for (Entry<Integer, Set<Stay>> entry : clientIdToStayMap.entrySet()) {
            MutablePeriod period = new MutablePeriod(PeriodType.days());

            for (Stay stay : entry.getValue()) {
                period.add(stay.getInterval());
            }

            int days = Days.standardDaysIn(period).getDays();

            if (days <= 10) {
                shelterUsages[LOW] += 1;
            } else if (11 <= days && days <= 179) {
                shelterUsages[MEDIUM] += 1;
            } else if (180 <= days) {
                shelterUsages[HIGH] += 1;
            }
        }

        return shelterUsages;
    }

    @Override
    public int getMortalities(int numYears) {

        return ((Long) getHibernateTemplate().find(HQL_GET_MORTALITIES, new Object[]{DateTimeFormatUtils.getPast(numYears)}).iterator().next()).intValue();
    }

    @Override
    public int getPrevalence(SortedSet<String> icd10Codes) {

        StringBuilder query = new StringBuilder(HQL_GET_PREVALENCE).append("(");

        for (String icd10Code : icd10Codes) {
            query.append("'").append(icd10Code).append("'");

            if (!icd10Codes.last().equals(icd10Code)) {
                query.append(",");
            }
        }

        query.append(")");

        return ((Long) getHibernateTemplate().find(query.toString()).iterator().next()).intValue();
    }

    @Override
    public int getIncidence(SortedSet<String> icd10Codes) {

        StringBuilder query = new StringBuilder(HQL_GET_INCIDENCE).append("(");

        for (String icd10Code : icd10Codes) {
            query.append("'").append(icd10Code).append("'");

            if (!icd10Codes.last().equals(icd10Code)) {
                query.append(",");
            }
        }

        query.append(")");

        return ((Long) getHibernateTemplate().find(query.toString()).iterator().next()).intValue();
    }

    @Override
    public Map<Integer, Integer> getCaseManagementNoteCountGroupedByIssueGroup(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            c = DbConnectionFilter.getThreadLocalDbConnection();
            int paramIndex = 1;
            
            // Start building the SQL query
            String sqlCommand = "select issueGroupId,count(distinct casemgmt_note.note_id) from IssueGroupIssues,casemgmt_issue,casemgmt_issue_notes," +
            "casemgmt_note where IssueGroupIssues.issue_id=casemgmt_issue.issue_id and casemgmt_issue_notes.id=casemgmt_issue.id and " +
            "casemgmt_note.note_id=casemgmt_issue_notes.note_id ";
            
            // Add encounter type condition if provided
            if (encounterType != null) {
                sqlCommand += "and casemgmt_note.encounter_type=?" + paramIndex++ + " ";
            }
            
            // Add program number condition
            sqlCommand += "and casemgmt_note.program_no=?" + paramIndex++ + " ";
            
            // Add role condition if provided
            if (roleId != null) {
                sqlCommand += "and casemgmt_note.reporter_caisi_role=?" + paramIndex++ + " ";
            } 
            
            // Add date range conditions and group by clause
            sqlCommand += "and casemgmt_note.observation_date>=?" + paramIndex++ +
            " and casemgmt_note.observation_date<=?" + paramIndex++ + " group by issueGroupId";
            
            // Prepare the statement
            ps = c.prepareStatement(sqlCommand);
            paramIndex = 1;
            
            // Set parameters for the prepared statement
            if (encounterType != null) ps.setString(paramIndex++, encounterType.getOldDbValue());
            ps.setInt(paramIndex++, programId);
            if (roleId != null) ps.setInt(paramIndex++, roleId);
            ps.setTimestamp(paramIndex++, new Timestamp(startDate != null ? startDate.getTime() : 0));
            ps.setTimestamp(paramIndex++, new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

            // Execute the query and process results
            rs = ps.executeQuery();
            HashMap<Integer, Integer> results = new HashMap<Integer, Integer>();
            while (rs.next())
                results.put(rs.getInt(1), rs.getInt(2));

            return (results);
        } catch (SQLException e) {
            throw (new HibernateException(e));
        } finally {
            SqlUtils.closeResources(c, ps, rs);
        }
    }

    @Override
    public Map<Integer, Integer> getCaseManagementNoteCountGroupedByIssueGroup(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Get database connection
            c = DbConnectionFilter.getThreadLocalDbConnection();
            int paramIndex = 1;
            
            // Build SQL query
            String sqlCommand = "select issueGroupId,count(distinct casemgmt_note.note_id) from IssueGroupIssues," +
            "casemgmt_issue,casemgmt_issue_notes,casemgmt_note where IssueGroupIssues.issue_id=casemgmt_issue.issue_id" +
            "and casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_note.note_id=casemgmt_issue_notes.note_id and " +
            "casemgmt_note.encounter_type=?" + paramIndex++ + " and casemgmt_note.program_no=?" + paramIndex++ +
            " and casemgmt_note.provider_no=?" + paramIndex++ + " and casemgmt_note.observation_date>=?" + paramIndex++ + 
            " and casemgmt_note.observation_date<=?" + paramIndex++ + " group by issueGroupId";
            
            // Prepare statement
            ps = c.prepareStatement(sqlCommand);
            
            // Set query parameters
            paramIndex = 1;
            ps.setString(paramIndex++, encounterType.getOldDbValue());
            ps.setInt(paramIndex++, programId);
            ps.setString(paramIndex++, provider.getProviderNo());
            ps.setTimestamp(paramIndex++, new Timestamp(startDate != null ? startDate.getTime() : 0));
            ps.setTimestamp(paramIndex++, new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

            // Execute query and process results
            rs = ps.executeQuery();
            HashMap<Integer, Integer> results = new HashMap<Integer, Integer>();
            while (rs.next())
                results.put(rs.getInt(1), rs.getInt(2));

            return (results);
        } catch (SQLException e) {
            throw (new HibernateException(e));
        } finally {
            // Close database resources
            SqlUtils.closeResources(c, ps, rs);
        }
    }

    @Override
    public Integer getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Get database connection
            c = DbConnectionFilter.getThreadLocalDbConnection();
            int paramIndex = 1;
            
            // Build SQL query
            String sqlCommand = "select count(distinct casemgmt_note.note_id) from IssueGroupIssues,casemgmt_issue," +
            "casemgmt_issue_notes,casemgmt_note where IssueGroupIssues.issue_id=casemgmt_issue.issue_id and " +
            "casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_note.note_id=casemgmt_issue_notes.note_id ";
            
            // Add encounter type if specified
            if (encounterType != null) {
                sqlCommand += "and casemgmt_note.encounter_type=?" + paramIndex++ + " ";
            }
            sqlCommand += "and casemgmt_note.program_no=?" + paramIndex++ + " ";
            
            // Add caisi role if specified
            if (roleId != null) {
                sqlCommand += "and casemgmt_note.reporter_caisi_role=?" + paramIndex++ + " ";
            }
            sqlCommand += "and casemgmt_note.observation_date>=?" + paramIndex++ + " and casemgmt_note.observation_date<=?" + paramIndex++;
            
            // Prepare statement
            ps = c.prepareStatement(sqlCommand);
            
            // Set query parameters
            paramIndex = 1;
            if (encounterType != null) ps.setString(paramIndex++, encounterType.getOldDbValue());
            ps.setInt(paramIndex++, programId);
            if (roleId != null) ps.setInt(paramIndex++, roleId);
            ps.setTimestamp(paramIndex++, new Timestamp(startDate != null ? startDate.getTime() : 0));
            ps.setTimestamp(paramIndex++, new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

            // Execute query and process results
            rs = ps.executeQuery();
            rs.next();

            return (rs.getInt(1));
        } catch (SQLException e) {
            throw (new HibernateException(e));
        } finally {
            // Close database resources
            SqlUtils.closeResources(c, ps, rs);
        }
    }

    @Override
    public Integer getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Get database connection
            c = DbConnectionFilter.getThreadLocalDbConnection();
            int paramIndex = 1;
            
            // Build SQL query
            ps = c.prepareStatement("select count(distinct casemgmt_note.note_id) from IssueGroupIssues,casemgmt_issue," +
            "casemgmt_issue_notes,casemgmt_note where IssueGroupIssues.issue_id=casemgmt_issue.issue_id and " +
            "casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_note.note_id=casemgmt_issue_notes.note_id and " +
            "casemgmt_note.encounter_type=?" + paramIndex++ + " and casemgmt_note.program_no=?" + paramIndex++ + " and " +
            "casemgmt_note.provider_no=?" + paramIndex++ + " and casemgmt_note.observation_date>=?" + paramIndex++ +
            " and casemgmt_note.observation_date<=?" + paramIndex++);
            
            // Set query parameters
            paramIndex = 1;
            ps.setString(paramIndex++, encounterType.getOldDbValue());
            ps.setInt(paramIndex++, programId);
            ps.setString(paramIndex++, provider.getProviderNo());
            ps.setTimestamp(paramIndex++, new Timestamp(startDate != null ? startDate.getTime() : 0));
            ps.setTimestamp(paramIndex++, new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

            // Execute query and process results
            rs = ps.executeQuery();
            rs.next();

            return (rs.getInt(1));
        } catch (SQLException e) {
            throw (new HibernateException(e));
        } finally {
            // Close database resources
            SqlUtils.closeResources(c, ps, rs);
        }
    }

    @Override
    public Integer getCaseManagementNoteTotalUniqueClientCountInIssueGroups(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Get database connection
            c = DbConnectionFilter.getThreadLocalDbConnection();
            int paramIndex = 1;
            
            // Build SQL query
            String sqlCommand = "select count(distinct casemgmt_note.demographic_no) from IssueGroupIssues,casemgmt_issue," +
            "casemgmt_issue_notes,casemgmt_note where IssueGroupIssues.issue_id=casemgmt_issue.issue_id and " +
            "casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_note.note_id=casemgmt_issue_notes.note_id ";
            
            // Add encounter type condition if provided
            if (encounterType != null) {
                sqlCommand += "and casemgmt_note.encounter_type=?" + paramIndex++ + " ";
            }
            
            // Add program number condition
            sqlCommand += "and casemgmt_note.program_no=?" + paramIndex++ + " ";
            
            // Add role condition if provided
            if (roleId != null) {
                sqlCommand += "and casemgmt_note.reporter_caisi_role=?" + paramIndex++ + " ";
            }
            
            // Add observation date range conditions
            sqlCommand += "and casemgmt_note.observation_date>=?" + paramIndex++ + " and casemgmt_note.observation_date<=?" + paramIndex++;
            
            // Prepare the statement
            ps = c.prepareStatement(sqlCommand);
            
            // Set parameters for the prepared statement
            paramIndex = 1;
            if (encounterType != null) ps.setString(paramIndex++, encounterType.getOldDbValue());
            ps.setInt(paramIndex++, programId);
            if (roleId != null) ps.setInt(paramIndex++, roleId);
            ps.setTimestamp(paramIndex++, new Timestamp(startDate != null ? startDate.getTime() : 0));
            ps.setTimestamp(paramIndex++, new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

            // Execute query and process results
            rs = ps.executeQuery();
            rs.next();

            return (rs.getInt(1));
        } catch (SQLException e) {
            throw (new HibernateException(e));
        } finally {
            // Close database resources
            SqlUtils.closeResources(c, ps, rs);
        }
    }

    @Override
    public Integer getCaseManagementNoteTotalUniqueClientCountInIssueGroups(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Get database connection
            c = DbConnectionFilter.getThreadLocalDbConnection();
            int counter = 1;
            
            // Build SQL query
            String sqlCommand = "select count(distinct casemgmt_note.demographic_no) from IssueGroupIssues,casemgmt_issue," +
            "casemgmt_issue_notes,casemgmt_note where IssueGroupIssues.issue_id=casemgmt_issue.issue_id and " +
            "casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_note.note_id=casemgmt_issue_notes.note_id ";
            
            // Add encounter type condition if provided
            if (encounterType != null) {
                sqlCommand += "and casemgmt_note.encounter_type=?" + counter++ + " ";
            }
            
            // Add program number condition
            sqlCommand += "and casemgmt_note.program_no=?" + counter++ + " ";
            
            // Add providers condition if provided
            if (provider != null) {
                sqlCommand += "and casemgmt_note.provider_no=?" + counter++ + " ";
            }
            
            // Add observation date range conditions
            sqlCommand += "and casemgmt_note.observation_date>=?" + counter++ + " and casemgmt_note.observation_date<=?" + counter++;
            
            // Prepare the statement
            ps = c.prepareStatement(sqlCommand);
            
            // Set parameters for the prepared statement
            counter = 1;
            if (encounterType != null) ps.setString(counter++, encounterType.getOldDbValue());
            ps.setInt(counter++, programId);
            if (provider != null) ps.setString(counter++, provider.getProviderNo());
            ps.setTimestamp(counter++, new Timestamp(startDate != null ? startDate.getTime() : 0));
            ps.setTimestamp(counter++, new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

            // Execute query and process results
            rs = ps.executeQuery();
            rs.next();

            return (rs.getInt(1));
        } catch (SQLException e) {
            throw (new HibernateException(e));
        } finally {
            // Close database resources
            SqlUtils.closeResources(c, ps, rs);
        }
    }

    @Override
    public Integer getCaseManagementNoteCountByIssueGroup(int programId, Integer issueGroupId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Get database connection
            c = DbConnectionFilter.getThreadLocalDbConnection();
            int paramIndex = 1;
            
            // Build SQL query
            String sqlCommand = "select count(distinct casemgmt_note.note_id) from IssueGroupIssues,casemgmt_issue,casemgmt_issue_notes,casemgmt_note where IssueGroupIssues.issue_id=casemgmt_issue.issue_id ";
            
            // Add issue group condition if provided
            if (issueGroupId != null) {
                sqlCommand += "and IssueGroupIssues.issueGroupId=?" + paramIndex++ + " ";
            }

            // Add issue id and note id conditions
            sqlCommand += "and casemgmt_issue_notes.id=casemgmt_issue.id and casemgmt_note.note_id=casemgmt_issue_notes.note_id ";
            
            // Add encounter type condition if provided
            if (encounterType != null) {
                sqlCommand += "and casemgmt_note.encounter_type=?" + paramIndex++ + " ";
            }
            
            // Add program number condition
            sqlCommand += "and casemgmt_note.program_no=?" + paramIndex++ + " ";
            
            // Add role condition if provided
            if (roleId != null) {
                sqlCommand += "and casemgmt_note.reporter_caisi_role=?" + paramIndex++ + " ";
            }
            
            // Add observation date range conditions
            sqlCommand += "and casemgmt_note.observation_date>=?" + paramIndex++ + " and casemgmt_note.observation_date<=?" + paramIndex++;
            
            // Prepare the statement
            ps = c.prepareStatement(sqlCommand);
            
            // Set parameters for the prepared statement
            paramIndex = 1;
            if (issueGroupId != null) ps.setInt(paramIndex++, issueGroupId);
            if (encounterType != null) ps.setString(paramIndex++, encounterType.getOldDbValue());
            ps.setInt(paramIndex++, programId);
            if (roleId != null) ps.setInt(paramIndex++, roleId);
            ps.setTimestamp(paramIndex++, new Timestamp(startDate != null ? startDate.getTime() : 0));
            ps.setTimestamp(paramIndex++, new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

            // Execute query and process results
            rs = ps.executeQuery();
            rs.next();

            return (rs.getInt(1));
        } catch (SQLException e) {
            throw (new HibernateException(e));
        } finally {
            // Close database resources
            SqlUtils.closeResources(c, ps, rs);
        }
    }
}
