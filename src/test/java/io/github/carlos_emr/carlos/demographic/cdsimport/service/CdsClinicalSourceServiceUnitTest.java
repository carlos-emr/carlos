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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportAdmission;
import io.github.carlos_emr.carlos.commn.model.CdsImportField;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionState;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Real range hashing/streaming and ownership checks; mocked admission transactions do not replace MariaDB race tests. */
class CdsClinicalSourceServiceUnitTest extends CarlosUnitTestBase {
    @TempDir Path temporary;
    private CdsClinicalSourceService source;
    private CdsImportAccessService access;
    private CdsImportFieldPersister fields;
    private CdsImportPatientDao patients;
    private CdsImportMaintenanceService maintenance;
    private CdsLaboratoryReadService laboratory;
    private CdsImportAdmissionDao admission;
    private CdsCorrectionLookupService corrections;
    private CdsImportAdmission barrier;
    private PlatformTransactionManager manager;
    private LoggedInInfo actor;
    private final String id="a".repeat(64);
    @BeforeEach void prepare(){
        access=mock(CdsImportAccessService.class);fields=mock(CdsImportFieldPersister.class);patients=mock(CdsImportPatientDao.class);
        maintenance=mock(CdsImportMaintenanceService.class);laboratory=mock(CdsLaboratoryReadService.class);admission=mock(CdsImportAdmissionDao.class);
        barrier=new CdsImportAdmission();barrier.setId(CdsJdbcAdmissionState.ROW);when(admission.readLock(CdsJdbcAdmissionState.ROW)).thenReturn(barrier);
        manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        actor=mock(LoggedInInfo.class);corrections=mock(CdsCorrectionLookupService.class);Properties properties=new Properties();properties.setProperty("cds.import.stagingRoot",temporary.toString());
        source=new CdsClinicalSourceService(access,fields,patients,maintenance,laboratory,admission,new CdsImportConfigurationService(properties),corrections,manager);
    }
    @Test void shouldDenyHeldPartialPatient_beforeReadingAnySourceValues(){
        when(patients.held(42)).thenReturn(true);
        assertThatThrownBy(()->source.page(actor,42,"ClinicalNotes","",50)).hasMessage("SOURCE_FIELD_IMPORT_HOLD");
        verifyNoInteractions(fields,laboratory);
    }
    @Test void shouldDenyGlobalMaintenanceOrMissingBarrier_withoutReadingSource(){
        barrier.setMaintenanceActive(true);
        assertThatThrownBy(()->source.page(actor,42,"ClinicalNotes","",50)).hasMessage("SOURCE_FIELD_IMPORT_HOLD");
        barrier.setMaintenanceActive(false);when(admission.readLock(anyString())).thenReturn(null);
        assertThatThrownBy(()->source.page(actor,42,"ClinicalNotes","",50)).hasMessage("SOURCE_FIELD_IMPORT_HOLD");verifyNoInteractions(fields);
    }
    @Test void shouldRequireFreshPatientCategoryAndSensitiveLabAccess_beforeFetchingPage(){
        when(fields.fields(42,"LaboratoryResults","",50)).thenReturn(List.of());
        source.page(actor,42,"LaboratoryResults","",50);
        var order=inOrder(admission,access,laboratory,fields);
        order.verify(admission).readLock(CdsJdbcAdmissionState.ROW);order.verify(access).requireClinicalRead(actor,42,"LaboratoryResults");
        order.verify(laboratory).requireSourceFieldsReadable(actor,42);order.verify(fields).fields(42,"LaboratoryResults","",50);
        doThrow(new SecurityException("denied")).when(laboratory).requireSourceFieldsReadable(actor,42);
        clearInvocations(fields);
        assertThatThrownBy(()->source.page(actor,42,"LaboratoryResults","",50)).isInstanceOf(SecurityException.class);verifyNoInteractions(fields);
    }
    @Test void shouldApplyPatientCategoryHoldAndSensitivityPolicy_toReviewProvenanceAsWell() {
        when(corrections.pageByDemographic(42,"ClinicalNotes","",50)).thenReturn(List.of());
        assertThat(source.provenance(actor,42,"ClinicalNotes","",50)).isEmpty();
        verify(access).requireClinicalRead(actor,42,"ClinicalNotes");verify(corrections).pageByDemographic(42,"ClinicalNotes","",50);
        clearInvocations(corrections);when(patients.held(42)).thenReturn(true);
        assertThatThrownBy(()->source.provenance(actor,42,"ClinicalNotes","",50)).hasMessage("SOURCE_FIELD_IMPORT_HOLD");verifyNoInteractions(corrections);
        when(patients.held(42)).thenReturn(false);doThrow(new SecurityException("restricted")).when(laboratory).requireSourceFieldsReadable(actor,42);
        assertThatThrownBy(()->source.provenance(actor,42,"LaboratoryResults","",50)).isInstanceOf(SecurityException.class);verifyNoInteractions(corrections);
        assertThatThrownBy(()->source.provenance(actor,42,"ClinicalNotes","../../file",50)).hasMessage("SOURCE_FIELD_REVIEW_PAGE_INVALID");
    }
    @Test void shouldRejectOversizedPagesForgedCategoryAndCursor_beforeAnyDatabaseRead(){
        assertThatThrownBy(()->source.page(actor,42,"ClinicalNotes","",51)).hasMessage("SOURCE_FIELD_PAGE_INVALID");
        assertThatThrownBy(()->source.page(actor,42,"Unknown","",50)).hasMessage("SOURCE_FIELD_CATEGORY_INVALID");
        assertThatThrownBy(()->source.page(actor,42,"ClinicalNotes","../source",50)).hasMessage("SOURCE_FIELD_PAGE_INVALID");
        verifyNoInteractions(admission,fields);
    }
    @Test void shouldCheckExactPatientAndCategory_whenOriginalFieldIdIsForged(){
        CdsImportFieldDao dao=mock(CdsImportFieldDao.class);
        CdsImportField field=new CdsImportField();field.setId(id);field.setDemographicNo(41);field.setCategory("ClinicalNotes");
        when(dao.get(id)).thenReturn(field);
        var real=new CdsImportFieldPersister(mock(CdsImportLedgerService.class),dao,manager);
        assertThatThrownBy(()->real.field(42,"ClinicalNotes",id)).hasMessage("SOURCE_FIELD_NOT_FOUND");
        field.setDemographicNo(42);
        assertThatThrownBy(()->real.field(42,"MedicationsAndTreatments",id)).hasMessage("SOURCE_FIELD_NOT_FOUND");
        assertThat(real.field(42,"ClinicalNotes",id).id()).isEqualTo(id);
    }
    @Test void shouldVerifyAndStreamOnlyAuthorizedRange_withoutIncludingAdjacentPatientValues() throws Exception {
        byte[] value=new byte[2*1024*1024+7];java.util.Arrays.fill(value,(byte)7);
        Path payload=temporary.resolve("opaque-values.dat");
        try(var out=Files.newOutputStream(payload)){out.write("prior patient".getBytes(java.nio.charset.StandardCharsets.UTF_8));out.write(value);out.write("next patient".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        var field=new CdsImportFieldDto(id,"b".repeat(64),"ClinicalNotes","/PatientRecord/ClinicalNotes[1]/Content[1]",1,"cds","text",null,
                null,null,payload.toString(),digest,13,value.length);
        when(fields.field(42,"ClinicalNotes",id)).thenReturn(field);
        var original=source.original(actor,42,"ClinicalNotes",id);var output=new ByteArrayOutputStream();source.writeOriginal(original,output);
        assertThat(source.contentLength(original)).isEqualTo(value.length);assertThat(output.toByteArray()).isEqualTo(value);
        Files.write(payload,new byte[8]);
        assertThatThrownBy(()->source.original(actor,42,"ClinicalNotes",id)).hasMessage("SOURCE_FIELD_PAYLOAD_INCOMPLETE");
    }
    @Test void shouldRefuseAlteredPayload_beforeWritingAByte() throws Exception {
        Path payload=temporary.resolve("opaque-values.dat");Files.writeString(payload,"altered");
        var field=new CdsImportFieldDto(id,"b".repeat(64),"ClinicalNotes","/PatientRecord/ClinicalNotes[1]/Content[1]",1,"cds","text",null,
                null,null,payload.toString(),"0".repeat(64),0,7);
        when(fields.field(42,"ClinicalNotes",id)).thenReturn(field);
        assertThatThrownBy(()->source.original(actor,42,"ClinicalNotes",id)).hasMessage("SOURCE_FIELD_PAYLOAD_CHANGED");
    }
}
