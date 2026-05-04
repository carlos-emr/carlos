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
package io.github.carlos_emr.carlos.commn.dao;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Map.Entry;

import jakarta.persistence.Query;

import org.apache.logging.log4j.Logger;
import java.time.Duration;
import io.github.carlos_emr.carlos.PMmodule.utility.DateTimeFormatUtils;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Stay;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.EncounterUtil.EncounterType;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;

@Transactional
public class PopulationReportDaoImpl extends AbstractJpaDao implements PopulationReportDao {


    private static final Logger logger = MiscUtils.getLogger();

    private static final String HQL_CURRENT_POP_SIZE = "select count(distinct a.clientId) from Admission a where " +
    "a.programId in (select p.id from Program p where lower(p.programStatus) = 'active' and lower(p.type) = 'service') and " +
    "a.clientId in (select d.DemographicNo from Demographic d where lower(d.PatientStatus) = 'ac') and " +
    "a.dischargeDate is null";

    private static final String HQL_CURRENT_HISTORICAL_POP_SIZE = "select count(distinct a.clientId) from Admission a where " +
    "a.programId in (select p.id from Program p where lower(p.programStatus) = 'active' and lower(p.type) = 'service') and " +
    "a.clientId in (select d.DemographicNo from Demographic d where lower(d.PatientStatus) = 'ac') and " +
    "(a.dischargeDate is null or a.dischargeDate > :cutoff)";

    private static final String HQL_GET_USAGES = "select a.clientId, a.admissionDate, a.dischargeDate from Admission a where a.programId in (select p.id from Program p where lower(p.programStatus) = 'active' and lower(p.type) = 'service') and a.clientId in (select d.DemographicNo from Demographic d where lower(d.PatientStatus) = 'ac') and (a.dischargeDate is null or a.dischargeDate > :cutoff) order by a.clientId, a.admissionDate";

    private static final String HQL_GET_MORTALITIES = "select count(distinct a.clientId) from Admission a where " +
     "a.programId in (select p.id from Program p where lower(p.programStatus) = 'active' and lower(p.type) = 'community' and lower(p.name) = 'deceased') and " +
     "a.admissionDate > :cutoff and a.dischargeDate is null";

    private static final String HQL_GET_PREVALENCE = "select count(cmi) from CaseManagementIssue cmi where cmi.resolved = false and " +
    "cmi.demographic_no in (select distinct a.clientId from Admission a where a.programId in (select p.id from Program p where " +
    "lower(p.programStatus) = 'active' and lower(p.type) = 'service') and a.clientId in (select d.DemographicNo from Demographic d where " +
    "lower(d.PatientStatus) = 'ac') and a.dischargeDate is null) and cmi.issue.code in (:codes)";

    private static final String HQL_GET_INCIDENCE = "select count(cmi) from CaseManagementIssue cmi where " +
    "cmi.demographic_no in (select distinct a.clientId from Admission a where a.programId in (select p.id from Program p where " +
    "lower(p.programStatus) = 'active' and lower(p.type) = 'service') and a.clientId in (select d.DemographicNo from Demographic d where " +
    "lower(d.PatientStatus) = 'ac') and a.dischargeDate is null) and cmi.issue.code in (:codes)";

    public int getCurrentPopulationSize() {
        List<?> results = JpqlQueryHelper.find(entityManager(), HQL_CURRENT_POP_SIZE);
        return extractCount(results);
    }

    @Override
    public int getCurrentAndHistoricalPopulationSize(int numYears) {
        Map<String, Object> params = new HashMap<>();
        params.put("cutoff", DateTimeFormatUtils.getPast(numYears));
        return extractCount(JpqlQueryHelper.find(entityManager(), HQL_CURRENT_HISTORICAL_POP_SIZE, params));
    }

    @Override
    public int[] getUsages(int numYears) {

        int[] shelterUsages = new int[3];

        Map<Integer, Set<Stay>> clientIdToStayMap = new HashMap<Integer, Set<Stay>>();

        Calendar instant = Calendar.getInstance();
        Date end = instant.getTime();
        Date start = DateTimeFormatUtils.getPast(instant, numYears);

        Map<String, Object> usageParams = new HashMap<>();
        usageParams.put("cutoff", start);
        for (Object o : JpqlQueryHelper.find(entityManager(), HQL_GET_USAGES, usageParams)) {
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
            Duration totalDuration = Duration.ZERO;

            for (Stay stay : entry.getValue()) {
                totalDuration = totalDuration.plus(stay.getDuration());
            }

            int days = (int) totalDuration.toDays();

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
        Map<String, Object> params = new HashMap<>();
        params.put("cutoff", DateTimeFormatUtils.getPast(numYears));
        return extractCount(JpqlQueryHelper.find(entityManager(), HQL_GET_MORTALITIES, params));
    }

    @Override
    public int getPrevalence(SortedSet<String> icd10Codes) {
        if (icd10Codes == null || icd10Codes.isEmpty()) {
            return 0;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("codes", icd10Codes);
        return extractCount(JpqlQueryHelper.find(entityManager(), HQL_GET_PREVALENCE, params));
    }

    @Override
    public int getIncidence(SortedSet<String> icd10Codes) {
        if (icd10Codes == null || icd10Codes.isEmpty()) {
            return 0;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("codes", icd10Codes);
        return extractCount(JpqlQueryHelper.find(entityManager(), HQL_GET_INCIDENCE, params));
    }

    @Override
    public Map<Integer, Integer> getCaseManagementNoteCountGroupedByIssueGroup(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate) {
        String sql = String.join(" ",
                "select issueGroupId, count(distinct casemgmt_note.note_id)",
                "from IssueGroupIssues, casemgmt_issue, casemgmt_issue_notes, casemgmt_note",
                "where IssueGroupIssues.issue_id = casemgmt_issue.issue_id",
                "and casemgmt_issue_notes.id = casemgmt_issue.id",
                "and casemgmt_note.note_id = casemgmt_issue_notes.note_id",
                (encounterType != null ? "and casemgmt_note.encounter_type = :encounterType" : ""),
                "and casemgmt_note.program_no = :programId",
                (roleId != null ? "and casemgmt_note.reporter_caisi_role = :roleId" : ""),
                "and casemgmt_note.observation_date >= :startDate",
                "and casemgmt_note.observation_date <= :endDate",
                "group by issueGroupId");

        Query query = entityManager().createNativeQuery(sql);
        if (encounterType != null) query.setParameter("encounterType", encounterType.getOldDbValue());
        query.setParameter("programId", programId);
        if (roleId != null) query.setParameter("roleId", roleId);
        query.setParameter("startDate", new Timestamp(startDate != null ? startDate.getTime() : 0));
        query.setParameter("endDate", new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        HashMap<Integer, Integer> results = new HashMap<>();
        for (Object[] row : rows) {
            results.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }
        return results;
    }

    @Override
    public Map<Integer, Integer> getCaseManagementNoteCountGroupedByIssueGroup(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate) {
        String sql = String.join(" ",
                "select issueGroupId, count(distinct casemgmt_note.note_id)",
                "from IssueGroupIssues, casemgmt_issue, casemgmt_issue_notes, casemgmt_note",
                "where IssueGroupIssues.issue_id = casemgmt_issue.issue_id",
                "and casemgmt_issue_notes.id = casemgmt_issue.id",
                "and casemgmt_note.note_id = casemgmt_issue_notes.note_id",
                "and casemgmt_note.encounter_type = :encounterType",
                "and casemgmt_note.program_no = :programId",
                "and casemgmt_note.provider_no = :providerNo",
                "and casemgmt_note.observation_date >= :startDate",
                "and casemgmt_note.observation_date <= :endDate",
                "group by issueGroupId");

        Query query = entityManager().createNativeQuery(sql);
        query.setParameter("encounterType", encounterType.getOldDbValue());
        query.setParameter("programId", programId);
        query.setParameter("providerNo", provider.getProviderNo());
        query.setParameter("startDate", new Timestamp(startDate != null ? startDate.getTime() : 0));
        query.setParameter("endDate", new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        HashMap<Integer, Integer> results = new HashMap<>();
        for (Object[] row : rows) {
            results.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }
        return results;
    }

    @Override
    public Integer getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate) {
        String sql = String.join(" ",
                "select count(distinct casemgmt_note.note_id)",
                "from IssueGroupIssues, casemgmt_issue, casemgmt_issue_notes, casemgmt_note",
                "where IssueGroupIssues.issue_id = casemgmt_issue.issue_id",
                "and casemgmt_issue_notes.id = casemgmt_issue.id",
                "and casemgmt_note.note_id = casemgmt_issue_notes.note_id",
                (encounterType != null ? "and casemgmt_note.encounter_type = :encounterType" : ""),
                "and casemgmt_note.program_no = :programId",
                (roleId != null ? "and casemgmt_note.reporter_caisi_role = :roleId" : ""),
                "and casemgmt_note.observation_date >= :startDate",
                "and casemgmt_note.observation_date <= :endDate");

        Query query = entityManager().createNativeQuery(sql);
        if (encounterType != null) query.setParameter("encounterType", encounterType.getOldDbValue());
        query.setParameter("programId", programId);
        if (roleId != null) query.setParameter("roleId", roleId);
        query.setParameter("startDate", new Timestamp(startDate != null ? startDate.getTime() : 0));
        query.setParameter("endDate", new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

        return ((Number) query.getSingleResult()).intValue();
    }

    @Override
    public Integer getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate) {
        String sql = String.join(" ",
                "select count(distinct casemgmt_note.note_id)",
                "from IssueGroupIssues, casemgmt_issue, casemgmt_issue_notes, casemgmt_note",
                "where IssueGroupIssues.issue_id = casemgmt_issue.issue_id",
                "and casemgmt_issue_notes.id = casemgmt_issue.id",
                "and casemgmt_note.note_id = casemgmt_issue_notes.note_id",
                "and casemgmt_note.encounter_type = :encounterType",
                "and casemgmt_note.program_no = :programId",
                "and casemgmt_note.provider_no = :providerNo",
                "and casemgmt_note.observation_date >= :startDate",
                "and casemgmt_note.observation_date <= :endDate");

        Query query = entityManager().createNativeQuery(sql);
        query.setParameter("encounterType", encounterType.getOldDbValue());
        query.setParameter("programId", programId);
        query.setParameter("providerNo", provider.getProviderNo());
        query.setParameter("startDate", new Timestamp(startDate != null ? startDate.getTime() : 0));
        query.setParameter("endDate", new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

        return ((Number) query.getSingleResult()).intValue();
    }

    @Override
    public Integer getCaseManagementNoteTotalUniqueClientCountInIssueGroups(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate) {
        String sql = String.join(" ",
                "select count(distinct casemgmt_note.demographic_no)",
                "from IssueGroupIssues, casemgmt_issue, casemgmt_issue_notes, casemgmt_note",
                "where IssueGroupIssues.issue_id = casemgmt_issue.issue_id",
                "and casemgmt_issue_notes.id = casemgmt_issue.id",
                "and casemgmt_note.note_id = casemgmt_issue_notes.note_id",
                (encounterType != null ? "and casemgmt_note.encounter_type = :encounterType" : ""),
                "and casemgmt_note.program_no = :programId",
                (roleId != null ? "and casemgmt_note.reporter_caisi_role = :roleId" : ""),
                "and casemgmt_note.observation_date >= :startDate",
                "and casemgmt_note.observation_date <= :endDate");

        Query query = entityManager().createNativeQuery(sql);
        if (encounterType != null) query.setParameter("encounterType", encounterType.getOldDbValue());
        query.setParameter("programId", programId);
        if (roleId != null) query.setParameter("roleId", roleId);
        query.setParameter("startDate", new Timestamp(startDate != null ? startDate.getTime() : 0));
        query.setParameter("endDate", new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

        return ((Number) query.getSingleResult()).intValue();
    }

    @Override
    public Integer getCaseManagementNoteTotalUniqueClientCountInIssueGroups(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate) {
        String sql = String.join(" ",
                "select count(distinct casemgmt_note.demographic_no)",
                "from IssueGroupIssues, casemgmt_issue, casemgmt_issue_notes, casemgmt_note",
                "where IssueGroupIssues.issue_id = casemgmt_issue.issue_id",
                "and casemgmt_issue_notes.id = casemgmt_issue.id",
                "and casemgmt_note.note_id = casemgmt_issue_notes.note_id",
                (encounterType != null ? "and casemgmt_note.encounter_type = :encounterType" : ""),
                "and casemgmt_note.program_no = :programId",
                (provider != null ? "and casemgmt_note.provider_no = :providerNo" : ""),
                "and casemgmt_note.observation_date >= :startDate",
                "and casemgmt_note.observation_date <= :endDate");

        Query query = entityManager().createNativeQuery(sql);
        if (encounterType != null) query.setParameter("encounterType", encounterType.getOldDbValue());
        query.setParameter("programId", programId);
        if (provider != null) query.setParameter("providerNo", provider.getProviderNo());
        query.setParameter("startDate", new Timestamp(startDate != null ? startDate.getTime() : 0));
        query.setParameter("endDate", new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

        return ((Number) query.getSingleResult()).intValue();
    }

    @Override
    public Integer getCaseManagementNoteCountByIssueGroup(int programId, Integer issueGroupId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate) {
        String sql = String.join(" ",
                "select count(distinct casemgmt_note.note_id)",
                "from IssueGroupIssues, casemgmt_issue, casemgmt_issue_notes, casemgmt_note",
                "where IssueGroupIssues.issue_id = casemgmt_issue.issue_id",
                (issueGroupId != null ? "and IssueGroupIssues.issueGroupId = :issueGroupId" : ""),
                "and casemgmt_issue_notes.id = casemgmt_issue.id",
                "and casemgmt_note.note_id = casemgmt_issue_notes.note_id",
                (encounterType != null ? "and casemgmt_note.encounter_type = :encounterType" : ""),
                "and casemgmt_note.program_no = :programId",
                (roleId != null ? "and casemgmt_note.reporter_caisi_role = :roleId" : ""),
                "and casemgmt_note.observation_date >= :startDate",
                "and casemgmt_note.observation_date <= :endDate");

        Query query = entityManager().createNativeQuery(sql);
        if (issueGroupId != null) query.setParameter("issueGroupId", issueGroupId);
        if (encounterType != null) query.setParameter("encounterType", encounterType.getOldDbValue());
        query.setParameter("programId", programId);
        if (roleId != null) query.setParameter("roleId", roleId);
        query.setParameter("startDate", new Timestamp(startDate != null ? startDate.getTime() : 0));
        query.setParameter("endDate", new Timestamp(endDate != null ? endDate.getTime() : System.currentTimeMillis()));

        return ((Number) query.getSingleResult()).intValue();
    }

    /**
     * Safely extracts a count value from an HQL aggregate result list.
     *
     * @param results the result list from an HQL COUNT query
     * @return the count as an int, or 0 if the list is empty or the value is null
     */
    private static int extractCount(List<?> results) {
        if (results.isEmpty() || results.get(0) == null) {
            return 0;
        }
        return ((Long) results.get(0)).intValue();
    }
}
