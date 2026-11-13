/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only CDS export projections of the reviewer and inbox routing rows of a patient's lab and
 * document reports, in the caller's patient snapshot. One owner for these projections: the inbox
 * routing table serves both laboratory and document reports, and {@code document_review} has no
 * entity DAO. Each plan is a fixed compile-time projection; the laboratory plan proves the
 * patient's HL7 lab associations first, exactly as the laboratory DAOs do, and the document plans
 * prove the patient's document associations.
 *
 * @since 2026-11-13
 */
@Repository
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class CdsExportReviewerReadDao {
    private static final Set<CdsExportPatientTableReadSupport.Plan> PLANS =
            Set.of(
                    CdsExportPatientTableReadSupport.Plan.LABORATORY_REVIEWERS,
                    CdsExportPatientTableReadSupport.Plan.DOCUMENT_ROUTES,
                    CdsExportPatientTableReadSupport.Plan.DOCUMENT_REVIEWS,
                    CdsExportPatientTableReadSupport.Plan.DOCUMENT_EXTRA_REVIEWERS);

    @PersistenceContext private EntityManager entityManager;

    public CdsExportTableSchemaDto schema(CdsExportPatientTableReadSupport.Plan plan) {
        return CdsExportPatientTableReadSupport.schema(entityManager, owned(plan));
    }

    public long count(CdsExportPatientTableReadSupport.Plan plan, int patient) {
        associations(owned(plan), patient);
        return CdsExportPatientTableReadSupport.count(entityManager, plan, patient);
    }

    public List<CdsExportRawRowDto> page(
            CdsExportPatientTableReadSupport.Plan plan, int patient, Long after, int limit) {
        associations(owned(plan), patient);
        return CdsExportPatientTableReadSupport.page(entityManager, plan, patient, after, limit);
    }

    public byte[] chunk(
            CdsExportPatientTableReadSupport.Plan plan,
            int patient,
            long row,
            String column,
            long offset,
            int length) {
        associations(owned(plan), patient);
        return CdsExportPatientTableReadSupport.chunk(
                entityManager, plan, patient, row, column, offset, length);
    }

    private void associations(CdsExportPatientTableReadSupport.Plan plan, int patient) {
        if (plan == CdsExportPatientTableReadSupport.Plan.LABORATORY_REVIEWERS)
            CdsExportPatientTableReadSupport.requireLocalLaboratoryAssociations(
                    entityManager, patient);
        else
            CdsExportPatientTableReadSupport.requireLocalDocumentAssociations(
                    entityManager, patient);
    }

    private static CdsExportPatientTableReadSupport.Plan owned(
            CdsExportPatientTableReadSupport.Plan plan) {
        if (plan == null || !PLANS.contains(plan))
            throw new IllegalArgumentException("CDS_EXPORT_REVIEWER_PLAN_INVALID");
        return plan;
    }
}
