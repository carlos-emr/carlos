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
 * Read-only CDS export projections of a patient's Hospital Report Manager reports, in the caller's
 * patient snapshot: the patient links ({@code HRMDocumentToDemographic}), the reports they name
 * ({@code HRMDocument}), and each report's comments, accompanying subclasses and provider routes.
 * One owner for these projections, so the report scope is written once
 * ({@link CdsExportPatientTableReadSupport.Plan#HRM_REPORTS}). Each plan is a fixed compile-time
 * projection; no report body is read here (the stored message file is read by the file capture).
 *
 * <p>No association is refused at this level: a report linked to two charts belongs to both, and
 * a link naming a report that no longer exists is captured and accounted by the report mapper.
 *
 * @since 2026-11-13
 */
@Repository
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class CdsExportHospitalReportReadDao {
    private static final Set<CdsExportPatientTableReadSupport.Plan> PLANS =
            Set.of(
                    CdsExportPatientTableReadSupport.Plan.HRM_REPORT_LINKS,
                    CdsExportPatientTableReadSupport.Plan.HRM_REPORTS,
                    CdsExportPatientTableReadSupport.Plan.HRM_REPORT_FILES,
                    CdsExportPatientTableReadSupport.Plan.HRM_REPORT_COMMENTS,
                    CdsExportPatientTableReadSupport.Plan.HRM_REPORT_SUBCLASSES,
                    CdsExportPatientTableReadSupport.Plan.HRM_REPORT_ROUTES);

    @PersistenceContext private EntityManager entityManager;

    public CdsExportTableSchemaDto schema(CdsExportPatientTableReadSupport.Plan plan) {
        return CdsExportPatientTableReadSupport.schema(entityManager, owned(plan));
    }

    public long count(CdsExportPatientTableReadSupport.Plan plan, int patient) {
        return CdsExportPatientTableReadSupport.count(entityManager, owned(plan), patient);
    }

    public List<CdsExportRawRowDto> page(
            CdsExportPatientTableReadSupport.Plan plan, int patient, Long after, int limit) {
        return CdsExportPatientTableReadSupport.page(
                entityManager, owned(plan), patient, after, limit);
    }

    public byte[] chunk(
            CdsExportPatientTableReadSupport.Plan plan,
            int patient,
            long row,
            String column,
            long offset,
            int length) {
        return CdsExportPatientTableReadSupport.chunk(
                entityManager, owned(plan), patient, row, column, offset, length);
    }

    private static CdsExportPatientTableReadSupport.Plan owned(
            CdsExportPatientTableReadSupport.Plan plan) {
        if (plan == null || !PLANS.contains(plan))
            throw new IllegalArgumentException("CDS_EXPORT_HOSPITAL_REPORT_PLAN_INVALID");
        return plan;
    }
}
