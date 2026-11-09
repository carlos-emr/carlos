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

import java.util.HashMap;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import org.springframework.stereotype.Repository;

@Repository
public class PreventionExtDaoImpl extends AbstractDaoImpl<PreventionExt> implements PreventionExtDao {

    public PreventionExtDaoImpl() {
        super(PreventionExt.class);
    }

    @Override
    public List<PreventionExt> findByPreventionId(Integer preventionId) {
        Query query = entityManager.createQuery("select x from PreventionExt x where preventionId=?1");
        query.setParameter(1, preventionId);

        @SuppressWarnings("unchecked")
        List<PreventionExt> results = query.getResultList();

        return (results);
    }

    @Override
    public List<PreventionExt> findByKeyAndValue(String key, String value) {
        Query query = entityManager.createQuery("select x from PreventionExt x where keyval=?1 and val=?2");
        query.setParameter(1, key);
        query.setParameter(2, value);

        @SuppressWarnings("unchecked")
        List<PreventionExt> results = query.getResultList();

        return (results);
    }

    @Override
    public List<PreventionExt> findByPreventionIdAndKey(Integer preventionId, String key) {
        Query query = entityManager.createQuery("select x from PreventionExt x where preventionId=?1 and keyval=?2");
        query.setParameter(1, preventionId);
        query.setParameter(2, key);

        @SuppressWarnings("unchecked")
        List<PreventionExt> results = query.getResultList();

        return (results);
    }

    @Override
    public HashMap<String, String> getPreventionExt(Integer preventionId) {
        HashMap<String, String> results = new HashMap<String, String>();

        List<PreventionExt> preventionExts = findByPreventionId(preventionId);
        for (PreventionExt preventionExt : preventionExts) {
            results.put(preventionExt.getkeyval(), preventionExt.getVal());
        }

        return results;
    }

    /** Explicit patient-owned immunization selection; no unselected prevention payload is read. */
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto readCdsExportSourceSchema(){return CdsExportPatientTableReadSupport.schema(entityManager, CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT); }
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> readCdsExportReceiptColumns(){return CdsExportNativeMetadataReadSupport.read(entityManager, CdsExportNativeMetadataReadSupport.Table.PREVENTIONS_EXT); }
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportPreventionRows(int patient, java.util.Set<Integer> selected){if (CdsExportPatientTableReadSupport.unresolvedPreventionProgram(entityManager, patient))throw new IllegalStateException("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE"); return CdsExportPatientTableReadSupport.preventionCount(entityManager, CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT, patient, selected); }
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> findCdsExportPreventionRowsAfter(int patient, java.util.Set<Integer> selected, Long after, int limit){if (CdsExportPatientTableReadSupport.unresolvedPreventionProgram(entityManager, patient))throw new IllegalStateException("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE"); return CdsExportPatientTableReadSupport.preventionPage(entityManager, CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT, patient, selected, after, limit); }
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportPreventionChunk(int patient, java.util.Set<Integer> selected, long row, String column, long offset, int length){if (CdsExportPatientTableReadSupport.unresolvedPreventionProgram(entityManager, patient))throw new IllegalStateException("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE"); return CdsExportPatientTableReadSupport.preventionChunk(entityManager, CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT, patient, selected, row, column, offset, length); }
}
