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

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewProvenanceDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionState;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsUploadException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Clinical, category-local access to preserved logical source values; no import dashboard privilege substitutes for chart access. */
@Service
public class CdsClinicalSourceService {
    public static final Set<String> CATEGORIES = Set.of("Demographics", "PersonalHistory", "FamilyHistory", "PastHealth",
            "ProblemList", "RiskFactors", "AllergiesAndAdverseReactions", "MedicationsAndTreatments", "Immunizations",
            "LaboratoryResults", "Appointments", "ClinicalNotes", "Reports", "CareElements", "AlertsAndSpecialNeeds", "NewCategory");
    private final CdsImportAccessService access;
    private final CdsImportFieldPersister fields;
    private final CdsImportPatientDao patients;
    private final CdsImportMaintenanceService maintenance;
    private final CdsLaboratoryReadService laboratory;
    private final CdsImportAdmissionDao admission;
    private final CdsImportConfigurationService configuration;
    private final CdsCorrectionLookupService corrections;
    private final TransactionTemplate transaction;

    public CdsClinicalSourceService(CdsImportAccessService access, CdsImportFieldPersister fields, CdsImportPatientDao patients,
            CdsImportMaintenanceService maintenance, CdsLaboratoryReadService laboratory, CdsImportAdmissionDao admission,
            CdsImportConfigurationService configuration, CdsCorrectionLookupService corrections, PlatformTransactionManager manager) {
        this.access=access; this.fields=fields; this.patients=patients; this.maintenance=maintenance; this.laboratory=laboratory;
        this.admission=admission; this.configuration=configuration; this.corrections=corrections;
        transaction=new TransactionTemplate(manager); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15); transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public List<CdsImportFieldDto> page(LoggedInInfo actor, int patient, String category, String after, int limit) {
        if (limit<1 || limit>50 || (after!=null && !after.isEmpty() && !after.matches("[0-9a-f]{64}"))) throw problem("SOURCE_FIELD_PAGE_INVALID", 400);
        return read(actor, patient, category, ()-> fields.fields(patient, category, after, limit));
    }

    public List<CdsImportReviewProvenanceDto> provenance(LoggedInInfo actor, int patient, String category, String after, int limit) {
        if (limit<1 || limit>50 || after!=null && !after.isEmpty() && !after.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw problem("SOURCE_FIELD_REVIEW_PAGE_INVALID", 400);
        return read(actor, patient, category, ()-> corrections.pageByDemographic(patient, category, after, limit));
    }

    public CdsImportFieldDto original(LoggedInInfo actor, int patient, String category, String fieldId) throws IOException {
        if (fieldId==null || !fieldId.matches("[0-9a-f]{64}")) throw problem("SOURCE_FIELD_ID_INVALID", 400);
        CdsImportFieldDto field=read(actor, patient, category, ()-> fields.field(patient, category, fieldId));
        // Immutable payload I/O occurs after the short patient/admission authorization transaction ends.
        if (field.payloadLocation()!=null) verify(field);
        return field;
    }

    public long contentLength(CdsImportFieldDto field) {
        return field.payloadLocation()==null ? field.originalValue().getBytes(StandardCharsets.UTF_8).length : field.payloadLength();
    }

    public void writeOriginal(CdsImportFieldDto field, OutputStream output) throws IOException {
        if (field.payloadLocation()==null) { output.write(field.originalValue().getBytes(StandardCharsets.UTF_8)); return; }
        stream(field, output, null);
    }

    private <T> T read(LoggedInInfo actor, int patient, String category, Supplier<T> operation) {
        if (patient<1 || !CATEGORIES.contains(category)) throw problem("SOURCE_FIELD_CATEGORY_INVALID", 400);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw problem("SOURCE_FIELD_AMBIENT_TRANSACTION_REFUSED", 409);
        return transaction.execute(status-> {
            // Field tables are an administrative JDBC exception, so explicitly share the physical admission barrier.
            var barrier=admission.readLock(CdsJdbcAdmissionState.ROW);
            if (barrier==null || barrier.isMaintenanceActive() || maintenance.isClinicalAccessBlocked() || patients.held(patient)) {
                throw problem("SOURCE_FIELD_IMPORT_HOLD", 409);
            }
            access.requireClinicalRead(actor, patient, category);
            if (category.equals("LaboratoryResults")) laboratory.requireSourceFieldsReadable(actor, patient);
            return operation.get();
        });
    }

    private void verify(CdsImportFieldDto field) throws IOException {
        if (field.payloadDigest()==null || !field.payloadDigest().matches("[0-9a-f]{64}")) throw problem("SOURCE_FIELD_PAYLOAD_INVALID", 503);
        MessageDigest digest=CdsPaths.sha256(); stream(field, null, digest);
        if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(field.payloadDigest()))) throw problem("SOURCE_FIELD_PAYLOAD_CHANGED", 503);
    }

    private void stream(CdsImportFieldDto field, OutputStream output, MessageDigest digest) throws IOException {
        Path path=CdsPaths.within(Path.of(field.payloadLocation()), configuration.stagingRoot());
        if (field.payloadOffset()<0 || field.payloadLength()<0 || field.payloadLength()>Long.MAX_VALUE-field.payloadOffset()) throw problem("SOURCE_FIELD_PAYLOAD_INVALID", 503);
        try (FileChannel channel=FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size()<field.payloadOffset()+field.payloadLength()) throw problem("SOURCE_FIELD_PAYLOAD_INCOMPLETE", 503);
            channel.position(field.payloadOffset()); ByteBuffer buffer=ByteBuffer.allocate(65536); long left=field.payloadLength();
            while (left>0) {
                if (Thread.currentThread().isInterrupted()) throw problem("SOURCE_FIELD_READ_INTERRUPTED", 503);
                buffer.clear(); buffer.limit((int) Math.min(buffer.capacity(), left)); int count=channel.read(buffer);
                if (count<1) throw problem("SOURCE_FIELD_PAYLOAD_INCOMPLETE", 503);
                if (digest!=null) digest.update(buffer.array(), 0, count); if (output!=null) output.write(buffer.array(), 0, count); left-=count;
            }
        }
    }
    private static CdsUploadException problem(String code, int status) { return new CdsUploadException(code, status); }
}
