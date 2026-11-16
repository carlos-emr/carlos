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

import io.github.carlos_emr.carlos.commn.model.CdsLaboratoryResult;
import org.springframework.stereotype.Repository;

/** Persistence and bounded restriction lookup for structured CDS laboratory results. */
@Repository
public class CdsLaboratoryResultDao extends AbstractDaoImpl<CdsLaboratoryResult> {
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> readCdsExportReceiptColumns() {
        return CdsExportNativeMetadataReadSupport.read(entityManager, CdsExportNativeMetadataReadSupport.Table.LABORATORY_RESULTS);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto readCdsExportSourceSchema() {
        return CdsExportPatientTableReadSupport.schema(entityManager, CdsExportPatientTableReadSupport.Plan.LABORATORY_RESULTS);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportRows(int patient) {
        CdsExportPatientTableReadSupport.requireLocalLaboratoryAssociations(entityManager, patient);
        return CdsExportPatientTableReadSupport.count(entityManager, CdsExportPatientTableReadSupport.Plan.LABORATORY_RESULTS, patient);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto> findCdsExportRowsAfter(int patient, String afterId, int limit) {
        CdsExportPatientTableReadSupport.requireLocalLaboratoryAssociations(entityManager, patient);
        return CdsExportPatientTableReadSupport.laboratoryPage(entityManager, patient, afterId, limit);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportValueChunk(int patient, String rowId, String column, long offset, int length) {
        CdsExportPatientTableReadSupport.requireLocalLaboratoryAssociations(entityManager, patient);
        return CdsExportPatientTableReadSupport.laboratoryChunk(entityManager, patient, rowId, column, offset, length);
    }

    public CdsLaboratoryResultDao() {
        super(CdsLaboratoryResult.class);
    }

    /** Tests for a restriction without loading result narratives or counting the patient's history. */
    public boolean hasBlockedResult(int demographicNo) {
        if (demographicNo < 1) throw new IllegalArgumentException("Invalid laboratory patient");
        return !entityManager.createQuery("select l.id from CdsLaboratoryResult l "
                        + "where l.demographicNo=:patient and l.sourceBlocked='Y'", String.class)
                .setParameter("patient", demographicNo)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }
}
