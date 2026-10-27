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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportField;
import io.github.carlos_emr.carlos.commn.model.CdsImportPatient;
import io.github.carlos_emr.carlos.commn.model.CdsImportUnit;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Category-local logical fields persist with their native clinical unit, never as a preflight side effect. */
@Service
public class CdsImportFieldPersister {
    private final CdsImportLedgerService ledger;
    private final CdsImportFieldDao fields;
    private final TransactionTemplate transaction;

    public CdsImportFieldPersister(CdsImportLedgerService ledger, CdsImportFieldDao fields, PlatformTransactionManager manager) {
        this.ledger=ledger; this.fields=fields; transaction=CdsImportLedgerService.template(manager);
    }

    /** Original/normalized scalars are bounded; longer values use immutable protected spool references. */
    public String preserve(String runId, long epoch, String unitKey, String sourcePath, long occurrence, String valueType,
            String precision, String originalValue, String normalizedValue, String payloadLocation, String payloadDigest,
            long payloadOffset, long payloadLength) {
        return preserve(runId, epoch, unitKey, sourcePath, occurrence, "cds", valueType, precision, originalValue, normalizedValue, payloadLocation, payloadDigest, payloadOffset, payloadLength);
    }

    public String preserve(String runId, long epoch, String unitKey, String sourcePath, long occurrence, String namespace, String valueType,
            String precision, String originalValue, String normalizedValue, String payloadLocation, String payloadDigest,
            long payloadOffset, long payloadLength) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("FIELD_WRITE_REQUIRES_UNIT");
        CdsImportUnitService.requireActive(runId, epoch, unitKey);
        ledger.lockOwned(runId, epoch);
        CdsImportUnit unit=ledger.unit(runId, unitKey);
        CdsImportPatient patient=ledger.patient(runId, unit.getPatientKey());
        if (patient.getTargetDemographicNo()==null || !patient.isHeld()) throw new CdsImportException("FIELD_TARGET_HOLD_MISSING");
        CdsImportKeys.required(sourcePath, "source XPath", 2048);
        if (namespace==null || namespace.length()>250) throw new IllegalArgumentException("Invalid source namespace");
        // Actual pinned TypeInfo is an expanded QName, e.g. {cds_dt}text32K, not an identifier token.
        CdsImportKeys.required(valueType, "value type", 4608);
        if (valueType.chars().anyMatch(c -> c<32 || c==127)) throw new IllegalArgumentException("Invalid value type");
        CdsImportKeys.optional(precision, "date precision", 32); CdsImportKeys.optional(originalValue, "inline original", 32768);
        CdsImportKeys.optional(normalizedValue, "inline normalized", 65536); CdsImportKeys.optional(payloadLocation, "payload location", 1024);
        if (occurrence<0 || payloadOffset<0 || payloadLength<0) throw new IllegalArgumentException("Invalid field occurrence/payload bounds");
        if (payloadLocation==null && originalValue==null) throw new IllegalArgumentException("Missing original field representation");
        if (payloadLocation!=null) CdsImportKeys.digestValue(payloadDigest, "payload digest");
        else if (payloadDigest!=null || payloadOffset!=0 || payloadLength!=0) throw new IllegalArgumentException("Payload metadata without a payload");
        String id=CdsImportKeys.hash(unitKey, namespace, sourcePath, Long.toString(occurrence));
        CdsImportField existing=fields.get(id);
        if (existing!=null) {
            if (!existing.getRunId().equals(runId) || !Objects.equals(existing.getOriginalValue(), originalValue)
                    || !Objects.equals(existing.getNormalizedValue(), normalizedValue) || !Objects.equals(existing.getPayloadDigest(), payloadDigest)
                    || existing.getPayloadOffset()!=payloadOffset || existing.getPayloadLength()!=payloadLength
                    || !Objects.equals(existing.getValueType(), valueType) || !Objects.equals(existing.getPrecision(), precision)) throw new CdsImportException("FIELD_REVISION_REVIEW_REQUIRED");
            return id;
        }
        CdsImportField field=new CdsImportField(); field.setId(id); field.setRunId(runId);
        field.setPatientKey(unit.getPatientKey()); field.setUnitKey(unitKey); field.setDemographicNo(patient.getTargetDemographicNo());
        field.setCategory(CdsImportLedgerService.clinicalCategory(unit.getCategory())); field.setSourceNamespace(namespace); field.setSourcePath(sourcePath); field.setOccurrence(occurrence);
        field.setValueType(valueType); field.setPrecision(precision); field.setOriginalValue(originalValue);
        field.setNormalizedValue(normalizedValue); field.setPayloadLocation(payloadLocation); field.setPayloadDigest(payloadDigest);
        field.setPayloadOffset(payloadOffset); field.setPayloadLength(payloadLength); fields.persist(field); return id;
    }

    /** Caller must enforce target/category read authorization and the import hold before displaying these values. */
    public List<CdsImportFieldDto> fields(int demographicNo, String category, String afterId, int limit) {
        CdsImportKeys.pageSize(limit);
        CdsImportKeys.token(category, "category", 64);
        return transaction.execute(status -> fields.page(demographicNo, category, afterId==null?"":afterId, limit).stream()
                .map(f -> new CdsImportFieldDto(f.getId(), f.getUnitKey(), f.getCategory(), f.getSourcePath(), f.getOccurrence(), f.getSourceNamespace(), f.getValueType(), f.getPrecision(), f.getOriginalValue(), f.getNormalizedValue(), f.getPayloadLocation(), f.getPayloadDigest(), f.getPayloadOffset(), f.getPayloadLength())).toList());
    }

    /** Caller supplies the same patient/category/hold/sensitivity read policy as for the bounded page. */
    public CdsImportFieldDto field(int demographicNo, String category, String fieldId) {
        CdsImportKeys.token(category, "category", 64); CdsImportKeys.token(fieldId, "field id", 64);
        return transaction.execute(status -> {
            CdsImportField f=fields.get(fieldId);
            if (f==null || f.getDemographicNo()!=demographicNo || !category.equals(f.getCategory())) throw new CdsImportException("SOURCE_FIELD_NOT_FOUND");
            return new CdsImportFieldDto(f.getId(), f.getUnitKey(), f.getCategory(), f.getSourcePath(), f.getOccurrence(), f.getSourceNamespace(), f.getValueType(), f.getPrecision(), f.getOriginalValue(), f.getNormalizedValue(), f.getPayloadLocation(), f.getPayloadDigest(), f.getPayloadOffset(), f.getPayloadLength());
        });
    }
}
