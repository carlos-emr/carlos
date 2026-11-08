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

import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import org.springframework.stereotype.Repository;

@Repository
public class MeasurementsDeletedDaoImpl extends AbstractDaoImpl<MeasurementsDeleted> implements MeasurementsDeletedDao {
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> readCdsExportReceiptColumns() {
        return CdsExportNativeMetadataReadSupport.read(entityManager, CdsExportNativeMetadataReadSupport.Table.MEASUREMENTS_DELETED);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto readCdsExportSourceSchema() {
        return CdsExportPatientTableReadSupport.schema(entityManager, CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportRows(int patient) {
        CdsExportPatientTableReadSupport.requireLocalMeasurementAssociations(entityManager, patient);
        return CdsExportPatientTableReadSupport.count(entityManager, CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED, patient);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> findCdsExportRowsAfter(int patient, Long afterId, int limit) {
        CdsExportPatientTableReadSupport.requireLocalMeasurementAssociations(entityManager, patient);
        return CdsExportPatientTableReadSupport.page(entityManager, CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED, patient, afterId, limit);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportValueChunk(int patient, long rowId, String column, long offset, int length) {
        CdsExportPatientTableReadSupport.requireLocalMeasurementAssociations(entityManager, patient);
        return CdsExportPatientTableReadSupport.chunk(entityManager, CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED, patient, rowId, column, offset, length);
    }


    public MeasurementsDeletedDaoImpl() {
        super(MeasurementsDeleted.class);
    }

    @Override
    public List<MeasurementsDeleted> findDeletedAfterDatetime(Date dateTime) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.dateDeleted>?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, dateTime);

        @SuppressWarnings("unchecked")
        List<MeasurementsDeleted> results = query.getResultList();

        return (results);
    }

}
