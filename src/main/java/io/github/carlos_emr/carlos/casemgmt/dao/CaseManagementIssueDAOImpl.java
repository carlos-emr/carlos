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
import java.util.List;
import java.util.Map;

import jakarta.persistence.TypedQuery;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementIssueListDTO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;
import io.github.carlos_emr.carlos.utility.LogSafe;

@Transactional
public class CaseManagementIssueDAOImpl extends AbstractJpaDao implements CaseManagementIssueDAO {

    // One authorized read-only patient snapshot owns these finite scalar projections; no managed issue is loaded.
    @Override
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> readCdsExportReceiptColumns() {
        return io.github.carlos_emr.carlos.commn.dao.CdsExportNativeMetadataReadSupport.read(entityManager(), io.github.carlos_emr.carlos.commn.dao.CdsExportNativeMetadataReadSupport.Table.CASE_ISSUES);
    }
    @Override
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto readCdsExportSourceSchema() {
        return io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.schema(entityManager(), io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan.CASE_ISSUES);
    }
    @Override
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportRows(int patient) {
        return io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.count(entityManager(), io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan.CASE_ISSUES, patient);
    }
    @Override
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> findCdsExportRowsAfter(int patient, Long afterId, int limit) {
        return io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.page(entityManager(), io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan.CASE_ISSUES, patient, afterId, limit);
    }
    @Override
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportValueChunk(int patient, long rowId, String column, long offset, int length) {
        return io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.chunk(entityManager(), io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan.CASE_ISSUES, patient, rowId, column, offset, length);
    }


    private static Logger log = MiscUtils.getLogger();

    @Override
    public List<CaseManagementIssue> findCandidatesByDemographicAndIssue(int demographicNo, Long issueId) {
        return entityManager().createQuery("select i from CaseManagementIssue i where"
                        + " i.demographic_no=:patient and i.issue_id=:issue order by i.id", CaseManagementIssue.class)
                .setParameter("patient", demographicNo).setParameter("issue", issueId)
                .setMaxResults(2).getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographic(String demographic_no) {
        return (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
                "from CaseManagementIssue cmi where cmi.demographic_no = ?1",
                Integer.valueOf(demographic_no));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicOrderActive(Integer demographic_no, Boolean resolved) {
        if (resolved != null) {
            return (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
                    "from CaseManagementIssue cmi where cmi.demographic_no = ?1 and cmi.resolved = ?2 order by cmi.resolved",
                    demographic_no, resolved);
        } else {
            return (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
                    "from CaseManagementIssue cmi where cmi.demographic_no = ?1 order by cmi.resolved",
                    demographic_no);
        }
    }

    /**
     * Finds issues linked to a note, optionally filtered by resolution state.
     * Results are ordered by resolution state, with unresolved issues first;
     * ordering within each state is unspecified.
     *
     * @param noteId Integer identifier of the note; must not be null
     * @param resolved Boolean resolution state to match, or null to return issues
     *                 without filtering by resolution
     * @return non-null {@code List<CaseManagementIssue>} of matching issues, or an
     *         empty list if the note does not exist or has no matching linked issues
     * @throws IllegalArgumentException if noteId is null
     * @throws org.springframework.dao.DataAccessException if persistence access fails
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByNote(Integer noteId, Boolean resolved) {
        if (resolved != null) {
            return (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
                    "select distinct cmi from CaseManagementIssue cmi join cmi.notes n where n.id = ?1 and cmi.resolved = ?2 order by cmi.resolved",
                    noteId, resolved);
        } else {
            return (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
                    "select distinct cmi from CaseManagementIssue cmi join cmi.notes n where n.id = ?1 order by cmi.resolved",
                    noteId);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Issue getIssueByCmnId(Integer cmnIssueId) {
        List<Issue> result = (List<Issue>) JpqlQueryHelper.find(entityManager(),
                "select issue from CaseManagementIssue cmi where cmi.id = ?1",
                Long.valueOf(cmnIssueId));
        if (result.size() > 0)
            return result.get(0);
        return null;
    }

    @Override
    public CaseManagementIssue getIssuebyId(String demo, String id) {
        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> list = (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
                "from CaseManagementIssue cmi where cmi.issue_id = ?1 and cmi.demographic_no = ?2",
                Long.parseLong(id), Integer.valueOf(demo));
        if (list != null && list.size() == 1)
            return list.get(0);

        return null;
    }

    @Override
    public CaseManagementIssue findById(Long id) {
        return id == null ? null : entityManager().find(CaseManagementIssue.class, id);
    }

    @Override
    public CaseManagementIssue getIssuebyIssueCode(String demo, String issueCode) {
        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> list = (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
                "select cmi from CaseManagementIssue cmi, Issue issue where cmi.issue_id=issue.id and issue.code = ?1 and cmi.demographic_no = ?2",
                issueCode, Integer.valueOf(demo));

        if (list.size() > 1) {
            log.error("Expected 1 result got more : {} ({},{})", list.size(), LogSafe.sanitize(demo), LogSafe.sanitize(issueCode)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
        }

        if (list.size() == 1 || list.size() > 1)
            return list.get(0);

        return null;
    }

    @Override
    public void deleteIssueById(CaseManagementIssue issue) {
        // Pre-migration Hibernate Session.delete() accepted detached entities. JPA
        // EntityManager.remove() requires a managed instance, so reattach via merge()
        // when the caller passes a detached entity (mirrors SecProviderDaoImpl.delete).
        CaseManagementIssue managed = entityManager().contains(issue)
                ? issue
                : entityManager().merge(issue);
        entityManager().remove(managed);
    }

    @Override
    public void saveAndUpdateCaseIssues(List<CaseManagementIssue> issuelist) {
        for (CaseManagementIssue cmi : issuelist) {
            write(cmi);
        }
    }

    @Override
    public void saveIssue(CaseManagementIssue issue) {
        write(issue);
    }

    /**
     * Inserts a new issue stamped now, or writes an existing one without moving its date.
     *
     * <p>Both note save paths hand every issue the editor lists to this DAO, almost always as
     * detached copies. Such a copy's {@code update_date} is whatever the caller holds: the value
     * loaded when the editor opened, {@code null} from REST input, or a caller's own
     * {@code new Date()}. It is not the row's change time, so the stored value is adopted before
     * the merge and the date alone never makes the row dirty. {@link CaseManagementIssue#stampChange()}
     * then stamps the row from Hibernate's flush-time dirty check, which compares with the state
     * loaded from the database, not with an instance a caller may already have modified. A
     * managed instance is therefore left entirely to that check.</p>
     */
    private void write(CaseManagementIssue issue) {
        if (issue.getId() == null || issue.getId() <= 0) {
            issue.setUpdate_date(new Date());
            entityManager().persist(issue);
            return;
        }
        if (!entityManager().contains(issue)) {
            // find() never flushes; a query here could auto-flush other pending writes first.
            CaseManagementIssue stored = entityManager().find(CaseManagementIssue.class, issue.getId());
            if (stored != null) issue.setUpdate_date(stored.getUpdate_date());
        }
        entityManager().merge(issue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getAllCertainIssues() {
        return (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
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
        List<Integer> results = (List<Integer>) JpqlQueryHelper.find(entityManager(), hql, params);

        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssue> getIssuesByDemographicSince(String demographic_no, Date date) {
        return (List<CaseManagementIssue>) JpqlQueryHelper.find(entityManager(),
                "from CaseManagementIssue cmi where cmi.demographic_no = ?1 and cmi.update_date > ?2",
                Integer.valueOf(demographic_no), date);
    }

    /**
     * Returns lightweight case management issue DTOs for a demographic, ordered by
     * update date descending. Pre-joins issue code and description via LEFT JOIN.
     *
     * @param demographicNo String the demographic number (parsed to Integer for binding)
     * @return List&lt;CaseManagementIssueListDTO&gt; ordered by update_date descending;
     *         empty list if {@code demographicNo} is not a valid integer
     * @throws org.hibernate.HibernateException if the underlying query fails
     * @since 2026-04-11
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementIssueListDTO> findIssueDTOsByDemographicNo(String demographicNo) {
        Integer demoNoInt;
        try {
            demoNoInt = Integer.valueOf(demographicNo);
        } catch (NumberFormatException e) {
            log.warn("findIssueDTOsByDemographicNo: invalid demographicNo '{}'", LogSafe.sanitize(demographicNo));
            return new ArrayList<>();
        }
        TypedQuery<CaseManagementIssueListDTO> query = entityManager().createQuery("""
                SELECT NEW io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementIssueListDTO(
                    cmi.id, cmi.demographic_no, cmi.issue_id, cmi.type,
                    cmi.acute, cmi.certain, cmi.major, cmi.resolved,
                    cmi.update_date, cmi.program_id,
                    i.code, i.description)
                FROM CaseManagementIssue cmi
                LEFT JOIN cmi.issue i
                WHERE cmi.demographic_no = :demoNo
                ORDER BY cmi.update_date DESC
                """,
                CaseManagementIssueListDTO.class);
        query.setParameter("demoNo", demoNoInt);
        return query.getResultList();
    }

}
