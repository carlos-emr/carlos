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

@Tag("unit")
@Tag("cds-export")
class CdsExportPatientDocumentServiceUnitTest {
    @TempDir Path root;

    private CdsExportPatientPipelineFixture fixture(CdsCategory... selected) throws Exception {
        var categories = java.util.EnumSet.of(CdsCategory.DEMOGRAPHICS);
        categories.addAll(java.util.List.of(selected));
        return new CdsExportPatientPipelineFixture(root, categories);
    }

    @Test
    void shouldAllowEveryOccurrenceItsAuditLines_beyondTheIndexBound() {
        // Before, maxIndexBytes alone bounded the audit, and a chart of about 11,000
        // measurements (two receipts and two dispositions each) stopped the whole job here.
        var bounds = new CdsExportClinicalOccurrenceService.Bounds(1000, 1 << 20, 1000, 64L << 20, 8192, 1000);
        assertThat(CdsExportPatientDocumentService.auditLimit(bounds, 0)).isEqualTo(64L << 20);
        assertThat(CdsExportPatientDocumentService.auditLimit(bounds, 131_072))
                .isEqualTo((64L << 20) + 131_072L * 16_384);
        assertThatThrownBy(() -> CdsExportPatientDocumentService.auditLimit(bounds, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReopenAndValidateTheFinishedEncryptedXml_beforeReturningPrivateDocumentEvidence()
            throws Exception {
        var f = fixture();
        var prior = f.members();
        var result = f.compose();
        assertThat(result.runId()).isEqualTo(f.run);
        assertThat(result.patient()).isEqualTo(27);
        assertThat(result.validation().senderPatientId()).isEqualTo("C27");
        assertThat(result.validation().xmlSha256()).isEqualTo(result.xml().plaintextSha256());
        assertThat(result.attachments()).isEmpty();
        assertThat(f.members()).hasSize(prior.size() + 2);
        try (var audit = f.store.open(result.reconciliation())) {
            assertThat(new String(audit.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("nativeId", "outputDigest", "\"expected\":1", "\"emitted\":1");
        }
        verify(f.captures)
                .authorizeCapturedSources(
                        eq(f.lease),
                        eq(f.patient),
                        eq(f.tables),
                        eq(f.source.capture()),
                        eq(f.control));
        assertThat(result.toString()).isEqualTo("CdsExportPatientDocument[protected]");
    }

    @Test
    void shouldGroupFieldDecisions_byTableAndAssessmentWithEveryFieldOnce() throws Exception {
        var identity =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto(
                        "a".repeat(64), CdsCategory.REPORTS, "b".repeat(64));
        var mapped =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto.Presence.VALUE,
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto.Outcome.MAPPED,
                        "Reports/FilePath",
                        "CARLOS_DATA_SUMMARY_EXACT_VALUE");
        var absent =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto.Presence.SQL_NULL,
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto.Outcome.MAPPED,
                        "Reports/FilePath",
                        "CARLOS_DATA_SUMMARY_EXACT_VALUE");
        var fields =
                new java.util.LinkedHashMap<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNativeFieldKeyDto,
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto>();
        for (String row : java.util.List.of("2", "1"))
            for (String column : java.util.List.of("form_name", "field_name", "form_id"))
                fields.put(field("form_boolean_value", row, column), mapped);
        fields.put(field("form_boolean_value", "1", "value"), absent);
        fields.put(field("formRourke2020", "7", "p1_date"), mapped);
        var control =
                mock(io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl.class);

        var lines = CdsExportPatientDocumentService.fieldDecisions(identity, fields, control);

        assertThat(lines)
                .extracting(
                        CdsExportPatientDocumentService.FieldDecisions::table,
                        line -> line.assessment().presence().name(),
                        line -> line.fields().toString())
                .containsExactly(
                        tuple("formRourke2020", "VALUE", "{" + key(7) + "=[p1_date]}"),
                        tuple("form_boolean_value", "SQL_NULL", "{" + key(1) + "=[value]}"),
                        tuple(
                                "form_boolean_value",
                                "VALUE",
                                "{"
                                        + key(1)
                                        + "=[field_name, form_id, form_name], "
                                        + key(2)
                                        + "=[field_name, form_id, form_name]}"));
        assertThat(lines).allSatisfy(line -> assertThat(line.identity()).isEqualTo(identity));
        verify(control, times(fields.size())).checkpoint();
    }

    @Test
    void shouldRefuseFieldDecisions_whenAnyFieldStillRequiresReview() {
        var identity =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto(
                        "a".repeat(64), CdsCategory.REPORTS, "b".repeat(64));
        var review =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto.Presence.VALUE,
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW,
                        "none",
                        "CARLOS_FIELD_REQUIRES_REVIEW");

        assertThatThrownBy(
                        () ->
                                CdsExportPatientDocumentService.fieldDecisions(
                                        identity,
                                        java.util.Map.of(field("tickler", "1", "message"), review),
                                        mock(
                                                io.github.carlos_emr.carlos.demographic.cdsexport
                                                        .engine.CdsExportRunControl.class)))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportReviewException.class);
    }

    private static String key(int row) {
        return CdsExportPatientTableSourceReader.key(row);
    }

    private static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto
            field(String table, String row, String column) {
        return new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto(
                table, CdsExportPatientTableSourceReader.key(Integer.parseInt(row)), column);
    }

    @Test
    void shouldRefuseAnOmittedNativeOccurrence_evenWhenTheRemainingXmlIsValid() throws Exception {
        var f = fixture(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        var prior = f.members();
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "allergies", "11", "11", CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldRefuseAMissingMappingReceipt_andDiscardBothPrivateOutputs() throws Exception {
        var f = fixture();
        var prior = f.members();
        f.receiptDemographic = false;
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldRefuseDuplicateMappingReceipts_forPatientDocumentServiceContract() throws Exception {
        var f = fixture();
        var prior = f.members();
        f.duplicateReceipt = true;
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldRemainFailed_whenAMapperSwallowsAReceiptFailure() throws Exception {
        var f = fixture();
        var prior = f.members();
        f.duplicateReceipt = true;
        f.swallowDuplicate = true;
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldRejectAChangedPatientIdentity_inActualXml() throws Exception {
        var f = fixture();
        var prior = f.members();
        f.senderId = "C28";
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_XML_PATIENT_ID_MISMATCH");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldDiscardAllPatientOutputs_afterFinalAuthorityRevocation() throws Exception {
        var f = fixture();
        var prior = f.members();
        doThrow(new SecurityException("REVOKED"))
                .when(f.captures)
                .authorizeCapturedSources(any(), any(), anySet(), any(), any());
        assertThatThrownBy(f::compose).hasMessage("REVOKED");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldPropagateMapperFailures_withoutLeavingAnyPatientOutput() throws Exception {
        var f = fixture();
        var prior = f.members();
        doThrow(new java.io.IOException("SOURCE_FAILED"))
                .when(f.demographics)
                .append(
                        any(),
                        any(),
                        anyString(),
                        anySet(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
        assertThatThrownBy(f::compose).hasMessage("SOURCE_FAILED");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldBindEachParsedReference_toOneCompleteAuthenticatedAttachment() throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        f.report(true, true);
        var prior = f.members();
        var result = f.compose();
        assertThat(result.validation().externalReferences()).isEqualTo(1);
        assertThat(result.attachments()).hasSize(1);
        assertThat(result.attachments().getFirst().relativePath()).isEqualTo("Reports/example.pdf");
        assertThat(f.members()).hasSize(prior.size() + 3);
    }

    @Test
    void shouldRefuseADanglingReference_insteadOfPublishingXmlWithoutTheFile() throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        f.report(false, true);
        var prior = f.members();
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldRefuseAndRemoveAnUnreferencedAttachment_forPatientDocumentServiceContract() throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        f.report(true, false);
        var prior = f.members();
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldRemoveAnAttachment_whenFinalAuthorizationFailsAfterXmlValidation() throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        f.report(true, true);
        var prior = f.members();
        doThrow(new SecurityException("REVOKED"))
                .when(f.captures)
                .authorizeCapturedSources(any(), any(), anySet(), any(), any());
        assertThatThrownBy(f::compose).hasMessage("REVOKED");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldPoisonThePatientOutput_afterARejectedForeignThreadCallback() throws Exception {
        var f = fixture();
        f.foreignReceipt = true;
        var prior = f.members();
        assertThatThrownBy(f::compose).hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(prior);
    }

    @Test
    void shouldExpireTheReceiptCallback_afterItsPrivateOutputTransaction() throws Exception {
        var f = fixture();
        f.compose();
        var complete = f.members();
        assertThatThrownBy(() -> f.lastSink.accept(f.lastReceipt))
                .hasMessage("CDS_EXPORT_PATIENT_DOCUMENT_INVALID");
        assertThat(f.members()).isEqualTo(complete);
    }
}
