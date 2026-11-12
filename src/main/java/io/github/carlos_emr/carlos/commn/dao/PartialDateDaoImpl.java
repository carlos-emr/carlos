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
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;

import org.apache.commons.lang3.math.NumberUtils;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

@Repository
public class PartialDateDaoImpl extends AbstractDaoImpl<PartialDate> implements PartialDateDao {
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> readCdsExportReceiptColumns() {
        return CdsExportNativeMetadataReadSupport.read(entityManager, CdsExportNativeMetadataReadSupport.Table.PARTIAL_DATE);
    }


    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto readCdsExportSourceSchema() {
        return CdsExportPatientTableReadSupport.schema(entityManager, CdsExportPatientTableReadSupport.Plan.PARTIAL_DATE_ALLERGIES);
    }

    /** Current finite source binding: partial dates belonging to this patient's allergy rows. */
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportRows(int patient) {
        return CdsExportPatientTableReadSupport.count(entityManager, CdsExportPatientTableReadSupport.Plan.PARTIAL_DATE_ALLERGIES, patient);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> findCdsExportRowsAfter(int patient, Long afterId, int limit) {
        return CdsExportPatientTableReadSupport.page(entityManager, CdsExportPatientTableReadSupport.Plan.PARTIAL_DATE_ALLERGIES, patient, afterId, limit);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportValueChunk(int patient, long rowId, String column, long offset, int length) {
        return CdsExportPatientTableReadSupport.chunk(entityManager, CdsExportPatientTableReadSupport.Plan.PARTIAL_DATE_ALLERGIES, patient, rowId, column, offset, length);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportScopedRows(int patient, java.util.Set<io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory> categories) {
        return CdsExportPatientTableReadSupport.count(entityManager, CdsExportPatientTableReadSupport.Plan.partialDates(categories), patient);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> findCdsExportScopedRowsAfter(
            int patient, java.util.Set<io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory> categories, Long afterId, int limit) {
        return CdsExportPatientTableReadSupport.page(entityManager, CdsExportPatientTableReadSupport.Plan.partialDates(categories), patient, afterId, limit);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportScopedChunk(int patient, java.util.Set<io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory> categories,
            long rowId, String column, long offset, int length) {
        return CdsExportPatientTableReadSupport.chunk(entityManager, CdsExportPatientTableReadSupport.Plan.partialDates(categories), patient, rowId, column, offset, length);
    }

    public PartialDateDaoImpl() {
        super(PartialDate.class);
    }

    @Override
    public PartialDate getPartialDate(Integer tableName, Integer tableId, Integer fieldName) {

        String sqlCommand = "select x from PartialDate x where x.tableName=?1 and x.tableId=?2 and x.fieldName=?3 order by x.id desc";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, tableName);
        query.setParameter(2, tableId);
        query.setParameter(3, fieldName);
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        PartialDate result = null;
        try {
            result = (PartialDate) query.getSingleResult();
        } catch (NoResultException ex) {
            MiscUtils.getLogger().debug("Note", ex);
        }

        return result;
    }

    @Override
    public String getDatePartial(Date fieldDate, Integer tableName, Integer tableId, Integer fieldName) {
        String dateString = UtilDateUtilities.DateToString(fieldDate, "yyyy-MM-dd");
        return getDatePartial(dateString, tableName, tableId, fieldName);
    }

    @Override
    public String getDatePartial(String fieldDate, Integer tableName, Integer tableId, Integer fieldName) {
        return getDatePartial(fieldDate, getFormat(tableName, tableId, fieldName));
    }

    @Override
    public String getDatePartial(Date partialDate, String format) {
        String dateString = UtilDateUtilities.DateToString(partialDate, "yyyy-MM-dd");
        return getDatePartial(dateString, format);
    }

    @Override
    public String getDatePartial(String dateString, String format) {
        if (dateString == null || dateString.length() < 10) return dateString;

        if (PartialDate.YEARONLY.equals(format)) {
            dateString = dateString.substring(0, 4);
        } else if (PartialDate.YEARMONTH.equals(format)) {
            dateString = dateString.substring(0, 7);
        }
        return dateString;
    }

    @Override
    public void setPartialDate(String fieldDate, Integer tableName, Integer tableId, Integer fieldName) {
        String format = getFormat(fieldDate);
        setPartialDate(tableName, tableId, fieldName, format);
    }

    @Override
    public void setPartialDate(Integer tableName, Integer tableId, Integer fieldName, String format) {
        if (tableName == null || fieldName == null || tableId == null || tableId.equals(0)) return;

        PartialDate partialDate = getPartialDate(tableName, tableId, fieldName);
        if (partialDate == null) {
            if (StringUtils.filled(format)) partialDate = new PartialDate(tableName, tableId, fieldName, format);
        } else partialDate.setFormat(format);

        if (partialDate != null) persist(partialDate);
    }

    @Override
    public String getFormat(Integer tableName, Integer tableId, Integer fieldName) {
        PartialDate partialDate = getPartialDate(tableName, tableId, fieldName);
        if (partialDate != null) return partialDate.getFormat();

        return null;
    }

    @Override
    public String getFormat(String dateValue) {
        if (StringUtils.empty(dateValue)) return null;

        dateValue = dateValue.trim();
        dateValue = dateValue.replace("/", "-");
        if (dateValue.length() == 4 && NumberUtils.isDigits(dateValue)) return PartialDate.YEARONLY;

        String[] dateParts = dateValue.split("-");
        if (dateParts.length == 2 && NumberUtils.isDigits(dateParts[0]) && NumberUtils.isDigits(dateParts[1])) {
            if (dateParts[0].length() == 4 && dateParts[1].length() >= 1 && dateParts[1].length() <= 2)
                return PartialDate.YEARMONTH;
        }
        return null;
    }

    @Override
    public String getFullDate(String partialDate) {
        String format = getFormat(partialDate);

        if (PartialDate.YEARONLY.equals(format)) partialDate += "-01-01";
        else if (PartialDate.YEARMONTH.equals(format)) partialDate += "-01";

        return partialDate;
    }

    @Override
    public Date StringToDate(String partialDate) {
        return UtilDateUtilities.StringToDate(getFullDate(partialDate), "yyyy-MM-dd");
    }

    /** Explicit patient-owned immunization selection; no unselected prevention payload is read. */
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportPreventionRows(int patient, java.util.Set<Integer> selected){if (CdsExportPatientTableReadSupport.unresolvedPreventionProgram(entityManager, patient))throw new IllegalStateException("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE"); return CdsExportPatientTableReadSupport.preventionCount(entityManager, CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION, patient, selected); }
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> findCdsExportPreventionRowsAfter(int patient, java.util.Set<Integer> selected, Long after, int limit){if (CdsExportPatientTableReadSupport.unresolvedPreventionProgram(entityManager, patient))throw new IllegalStateException("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE"); return CdsExportPatientTableReadSupport.preventionPage(entityManager, CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION, patient, selected, after, limit); }
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportPreventionChunk(int patient, java.util.Set<Integer> selected, long row, String column, long offset, int length){if (CdsExportPatientTableReadSupport.unresolvedPreventionProgram(entityManager, patient))throw new IllegalStateException("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE"); return CdsExportPatientTableReadSupport.preventionChunk(entityManager, CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION, patient, selected, row, column, offset, length); }
}
