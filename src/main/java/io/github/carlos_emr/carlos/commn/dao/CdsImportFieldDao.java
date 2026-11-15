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

import io.github.carlos_emr.carlos.commn.model.CdsImportField;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportFieldDao extends AbstractDaoImpl<CdsImportField>
        implements CdsExportRetainedTableReadDao {
    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column>
            readCdsExportReceiptColumns() {
        return CdsExportNativeMetadataReadSupport.read(
                entityManager, CdsExportNativeMetadataReadSupport.Table.RETAINED_FIELDS);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto
            readCdsExportRetainedSchema() {
        return CdsExportPatientTableReadSupport.schema(
                entityManager, CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportRetainedRows(int patient, java.util.Set<String> categories) {
        return CdsExportPatientTableReadSupport.retainedCount(
                entityManager,
                CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS,
                patient,
                categories);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto>
            findCdsExportRetainedRowsAfter(
                    int patient, java.util.Set<String> categories, String afterId, int limit) {
        return CdsExportPatientTableReadSupport.retainedPage(
                entityManager,
                CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS,
                patient,
                categories,
                afterId,
                limit);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportRetainedChunk(
            int patient,
            java.util.Set<String> categories,
            String rowId,
            String column,
            long offset,
            int length) {
        return CdsExportPatientTableReadSupport.retainedChunk(
                entityManager,
                CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS,
                patient,
                categories,
                rowId,
                column,
                offset,
                length);
    }

    public CdsImportFieldDao() {
        super(CdsImportField.class);
    }

    public CdsImportField lock(String id) {
        return entityManager.find(CdsImportField.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public CdsImportField get(String id) {
        return entityManager.find(CdsImportField.class, id);
    }

    public void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Categories in which the patient has at least one preserved source field.
     * Answered from {@code idx_cds_field_category (demographic_no, category, id)} without reading a field
     * row, so an ordinary chart render can ask it for a patient no import ever touched.
     *
     * @param demographicNo int the patient's demographic number
     * @return List&lt;String&gt; the distinct source categories, empty when the patient has none
     */
    public List<String> categories(int demographicNo) {
        return entityManager
                .createQuery(
                        "select distinct f.category from CdsImportField f where f.demographicNo=:demo",
                        String.class)
                .setParameter("demo", demographicNo)
                .getResultList();
    }

    public List<CdsImportField> page(
            int demographicNo, String category, String afterId, int limit) {
        return entityManager
                .createQuery(
                        "select f from CdsImportField f where f.demographicNo=:demo and f.category=:category and f.id>:after order by f.id",
                        CdsImportField.class)
                .setParameter("demo", demographicNo)
                .setParameter("category", category)
                .setParameter("after", afterId)
                .setMaxResults(limit)
                .getResultList();
    }
}
