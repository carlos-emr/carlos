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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeRowReceiptDto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit") @Tag("cds-export")
class CdsExportPatientDocumentServiceUnitTest {
    @TempDir Path root;
    private CdsExportPatientPipelineFixture fixture(CdsCategory... selected) throws Exception {
        var categories = java.util.EnumSet.of(CdsCategory.DEMOGRAPHICS); categories.addAll(java.util.List.of(selected));
        return new CdsExportPatientPipelineFixture(root, categories);
    }
    @Test void shouldReopenAndValidateTheFinishedEncryptedXml_beforeReturningPrivateDocumentEvidence() throws Exception {
        var f = fixture(); var prior = f.members(); var result = f.compose();
        assertThat(result.runId()).isEqualTo(f.run); assertThat(result.patient()).isEqualTo(27);
        assertThat(result.validation().senderPatientId()).isEqualTo("C27");
        assertThat(result.validation().xmlSha256()).isEqualTo(result.xml().plaintextSha256());
        assertThat(result.attachments()).isEmpty(); assertThat(f.members()).hasSize(prior.size() + 2);
        try (var audit = f.store.open(result.reconciliation())) {
            assertThat(new String(audit.readAllBytes(), StandardCharsets.UTF_8)).contains("nativeId", "outputDigest", "\"expected\":1", "\"emitted\":1");
        }
        verify(f.captures).authorizeCapturedSources(eq(f.lease), eq(f.patient), eq(f.tables), eq(f.source.capture()), eq(f.control));
        assertThat(result.toString()).isEqualTo("CdsExportPatientDocument[protected]");
    }
    @Test void shouldRefuseAnOmittedNativeOccurrence_evenWhenTheRemainingXmlIsValid() throws Exception {
        var f = fixture(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS); var prior = f.members();
        f.nativeRows.add(new CdsExportNativeRowReceiptDto("allergies", "11", "11", CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldRefuseAMissingMappingReceipt_andDiscardBothPrivateOutputs() throws Exception {
        var f = fixture(); var prior = f.members(); f.receiptDemographic = false;
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldRefuseDuplicateMappingReceipts() throws Exception {
        var f = fixture(); var prior = f.members(); f.duplicateReceipt = true;
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldRemainFailed_whenAMapperSwallowsAReceiptFailure() throws Exception {
        var f = fixture(); var prior = f.members(); f.duplicateReceipt = true; f.swallowDuplicate = true;
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldRejectAChangedPatientIdentity_inActualXml() throws Exception {
        var f = fixture(); var prior = f.members(); f.senderId = "C28";
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_XML_PATIENT_ID_MISMATCH"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldDiscardAllPatientOutputs_afterFinalAuthorityRevocation() throws Exception {
        var f = fixture(); var prior = f.members();
        doThrow(new SecurityException("REVOKED")).when(f.captures).authorizeCapturedSources(any(), any(), anySet(), any(), any());
        assertThatThrownBy(f::compose).hasMessage("REVOKED"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldPropagateMapperFailures_withoutLeavingAnyPatientOutput() throws Exception {
        var f = fixture(); var prior = f.members();
        doThrow(new java.io.IOException("SOURCE_FAILED")).when(f.demographics)
                .append(any(), any(), anyString(), anySet(), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(f::compose).hasMessage("SOURCE_FAILED"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldBindEachParsedReference_toOneCompleteAuthenticatedAttachment() throws Exception {
        var f = fixture(CdsCategory.REPORTS); f.report(true, true); var prior = f.members(); var result = f.compose();
        assertThat(result.validation().externalReferences()).isEqualTo(1); assertThat(result.attachments()).hasSize(1);
        assertThat(result.attachments().getFirst().relativePath()).isEqualTo("Reports/example.pdf");
        assertThat(f.members()).hasSize(prior.size() + 3);
    }
    @Test void shouldRefuseADanglingReference_insteadOfPublishingXmlWithoutTheFile() throws Exception {
        var f = fixture(CdsCategory.REPORTS); f.report(false, true); var prior = f.members();
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldRefuseAndRemoveAnUnreferencedAttachment() throws Exception {
        var f = fixture(CdsCategory.REPORTS); f.report(true, false); var prior = f.members();
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldRemoveAnAttachment_whenFinalAuthorizationFailsAfterXmlValidation() throws Exception {
        var f = fixture(CdsCategory.REPORTS); f.report(true, true); var prior = f.members();
        doThrow(new SecurityException("REVOKED")).when(f.captures).authorizeCapturedSources(any(), any(), anySet(), any(), any());
        assertThatThrownBy(f::compose).hasMessage("REVOKED"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldPoisonThePatientOutput_afterARejectedForeignThreadCallback() throws Exception {
        var f = fixture(); f.foreignReceipt = true; var prior = f.members();
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID"); assertThat(f.members()).isEqualTo(prior);
    }
    @Test void shouldExpireTheReceiptCallback_afterItsPrivateOutputTransaction() throws Exception {
        var f = fixture(); f.compose(); var complete = f.members();
        assertThatThrownBy(() -> f.lastSink.accept(f.lastReceipt)).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(complete);
    }
}
