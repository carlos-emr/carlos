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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportClinicalPackageServiceUnitTest {
    @TempDir Path root;
    private CdsExportPatientPipelineFixture fixture;
    private final CdsExportCohortService cohorts = mock(CdsExportCohortService.class);
    private final CdsExportLedgerService ledger = mock(CdsExportLedgerService.class);
    private final CdsExportRetainedPayloadService payloads =
            mock(CdsExportRetainedPayloadService.class);
    private final CdsExportPatientDocumentService documents =
            mock(CdsExportPatientDocumentService.class);
    private final CdsExportPatientFileBindingService naming =
            mock(CdsExportPatientFileBindingService.class);
    private final CdsExportDisclosurePolicyService disclosure =
            mock(CdsExportDisclosurePolicyService.class);
    private final io.github.carlos_emr.carlos.commn.dao.OscarLogDao auditLogs =
            mock(io.github.carlos_emr.carlos.commn.dao.OscarLogDao.class);
    private final CdsExportDisclosurePolicyService.Resolved policy =
            new CdsExportDisclosurePolicyService.Resolved(
                    "sender",
                    "Owned group",
                    "Owned contact",
                    "a".repeat(64),
                    null,
                    "a".repeat(64),
                    "Owned audit access");
    private CdsExportClinicalPackageService service;
    private final CdsExportClinicalPackageService.Limits limits =
            new CdsExportClinicalPackageService.Limits(
                    100, 1000, 8L << 20, 8L << 20, 8L << 20, 8L << 20);
    private boolean eof = true;

    @BeforeEach
    void prepare() throws Exception {
        fixture = new CdsExportPatientPipelineFixture(root, Set.of(CdsCategory.DEMOGRAPHICS));
        when(ledger.status(fixture.run)).thenAnswer(call -> status(1L));
        when(ledger.cohort(fixture.lease)).thenReturn(fixture.cohort);
        when(cohorts.open(eq(fixture.lease), eq(fixture.control)))
                .thenAnswer(
                        call -> {
                            var cursor = mock(CdsExportCohortService.Cursor.class);
                            when(cursor.next())
                                    .thenReturn(
                                            fixture.patient, (CdsExportCohortService.Patient) null);
                            when(cursor.complete()).thenAnswer(ignored -> eof);
                            return cursor;
                        });
        when(fixture.captures.requiredTables(fixture.lease.document().command()))
                .thenReturn(fixture.tables);
        when(fixture.captures.capture(any(), any(), anySet(), anyLong(), anyLong(), any()))
                .thenReturn(fixture.source);
        var values = fixture.store.stage("RETAINED_VALUES", output -> {});
        var index = fixture.store.stage("RETAINED_INDEX", output -> {});
        var manifest = fixture.store.stage("RETAINED_MANIFEST", output -> output.write(1));
        var retained =
                new CdsExportRetainedPayloadCaptureDto(
                        1,
                        fixture.run,
                        1,
                        27,
                        fixture.cohort.plaintextSha256(),
                        fixture.source.manifest().plaintextSha256(),
                        "a".repeat(64),
                        0,
                        0,
                        0,
                        values,
                        index);
        when(payloads.freeze(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(new CdsExportRetainedPayloadReceiptDto(retained, manifest));
        when(documents.compose(
                        any(), any(), anySet(), any(), any(), any(), anyString(), any(), any()))
                .thenAnswer(call -> fixture.compose());
        when(naming.bind(any(), any(), anySet(), any(), any(), any(), any()))
                .thenReturn(
                        new CdsExportPatientFileDto(
                                "Owned",
                                "Physician",
                                "123456",
                                "Export",
                                "Fixture",
                                "C27",
                                LocalDate.of(1971, 3, 4)));
        when(disclosure.resolveAccepted(any(), any())).thenReturn(policy);
        service =
                new CdsExportClinicalPackageService(
                        fixture.stores,
                        cohorts,
                        ledger,
                        fixture.captures,
                        payloads,
                        documents,
                        naming,
                        new CdsExportPackageInventoryService(),
                        disclosure,
                        new CdsExportAuditPersister(auditLogs));
    }

    private CdsExportJobDto status(Long total) {
        return new CdsExportJobDto(
                fixture.run,
                CdsExportState.CAPTURING,
                0,
                total,
                0,
                0,
                false,
                true,
                1,
                null,
                Instant.now(),
                Instant.now(),
                false);
    }

    private CdsExportClinicalPackageService.Prepared compose() throws IOException {
        return service.compose(
                fixture.lease,
                CdsExportPatientPipelineFixture.VERSION,
                ZoneId.of("America/Toronto"),
                fixture.bounds,
                CdsExportXmlLimitsDto.defaults(),
                limits,
                fixture.control);
    }

    @Test
    void shouldKeepPatientMembershipInTheNormalAudit_afterPrivatePackagePreparation()
            throws Exception {
        compose();
        var entries =
                org.mockito.ArgumentCaptor.forClass(
                        io.github.carlos_emr.carlos.commn.model.OscarLog.class);
        verify(auditLogs).persist(entries.capture());
        verify(auditLogs).flush();
        var entry = entries.getValue();
        assertThat(entry.getDemographicId()).isEqualTo(27);
        assertThat(entry.getContent()).isEqualTo("demographic");
        assertThat(entry.getContentId()).isEqualTo("27");
        assertThat(entry.getAction()).isEqualTo("EXPORT");
        assertThat(entry.getProviderNo()).isEqualTo(fixture.lease.document().actor().providerNo());
        assertThat(entry.getData())
                .contains(
                        "CDS_EXPORT_PACKAGE_PREPARED",
                        fixture.lease.runId(),
                        "epoch=" + fixture.lease.epoch(),
                        "outcomes=cds_export_event");
    }

    @Test
    void shouldWithholdThePrivatePackage_whenThePatientAuditCannotBeCommitted() {
        doThrow(new IllegalStateException("AUDIT_WRITE_FAILED")).when(auditLogs).flush();
        assertThatThrownBy(this::compose).hasMessage("AUDIT_WRITE_FAILED");
    }

    @Test
    void shouldCloseTheWholeCohort_andValidateActualSupportingFilesAndFinishedZip()
            throws Exception {
        var prepared = compose();
        assertThat(prepared.patients()).isEqualTo(1);
        assertThat(prepared.archive().entries()).isEqualTo(3);
        assertThat(prepared.runId()).isEqualTo(fixture.run);
        assertThat(prepared.cohortSha256()).isEqualTo(fixture.cohort.plaintextSha256());
        try (var input = fixture.store.open(prepared.authorization())) {
            var lines = new CdsExportJsonLines(input, 1 << 20, () -> {});
            assertThat(lines.next().path("patients").asLong()).isEqualTo(1);
            assertThat(lines.next().path("patient").asInt()).isEqualTo(27);
            assertThat(lines.next().path("kind").asText()).isEqualTo("END");
            assertThat(lines.next()).isNull();
        }
        try (var input = fixture.store.open(prepared.ownership())) {
            var lines = new CdsExportJsonLines(input, 1 << 20, () -> {});
            var header = lines.next();
            assertThat(header.path("kind").asText()).isEqualTo("OWNERSHIP");
            long count = header.path("files").asLong();
            var ids = new TreeSet<String>();
            for (long i = 0; i < count; i++) {
                var item = lines.next();
                assertThat(ids.add(item.path("memberId").asText())).isTrue();
            }
            assertThat(ids)
                    .contains(
                            prepared.archive().archive().memberId(),
                            prepared.authorization().memberId())
                    .doesNotContain(fixture.cohort.memberId(), prepared.ownership().memberId());
            assertThat(count).isEqualTo(fixture.members().size() - 2L);
            assertThat(lines.next().path("kind").asText()).isEqualTo("END");
            assertThat(lines.next()).isNull();
        }
        var order = inOrder(ledger);
        order.verify(ledger).status(fixture.run);
        order.verify(ledger).cohort(fixture.lease);
        order.verify(ledger).phase(fixture.lease, CdsExportState.RECONCILING);
        order.verify(ledger).phase(fixture.lease, CdsExportState.SERIALIZING);
        order.verify(ledger).phase(fixture.lease, CdsExportState.VALIDATING);
        order.verify(ledger).phase(fixture.lease, CdsExportState.PACKAGING);
        verify(fixture.captures, times(3))
                .authorizeSources(fixture.lease, fixture.patient, fixture.tables, fixture.control);
        assertThat(prepared.toString()).isEqualTo("CdsExportPreparedClinicalPackage[protected]");
    }

    @Test
    void shouldRefuseAnUnfinishedCohort_evenWhenEveryReturnedPatientHasValidXml() throws Exception {
        eof = false;
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_CLINICAL_PACKAGE_INVALID");
        assertThat(fixture.members()).containsExactly(fixture.cohort.memberId() + ".stage");
        verify(ledger, never()).phase(any(), any());
    }

    @Test
    void shouldRefuseAnOmittedPatient_insteadOfPublishingASmallerPackage() throws Exception {
        when(ledger.status(fixture.run)).thenReturn(status(2L));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_CLINICAL_PACKAGE_INVALID");
        assertThat(fixture.members()).hasSize(1);
    }

    @Test
    void shouldRefuseUnsealedSelection_beforeAnyClinicalRead() {
        when(ledger.status(fixture.run)).thenReturn(status(null));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_CLINICAL_PACKAGE_INVALID");
        verifyNoInteractions(payloads, documents, naming);
    }

    @Test
    void shouldDiscardEveryOwnedOutput_whenRosterFileBindingRequiresReview() throws Exception {
        when(naming.bind(any(), any(), anySet(), any(), any(), any(), any()))
                .thenThrow(
                        new CdsExportReviewException(
                                CdsExportReviewException.Reason.SOURCE_CHANGED));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        assertThat(fixture.members()).hasSize(1);
    }

    @Test
    void shouldRecheckPermissions_afterTheWholeCohortWasBuffered() throws Exception {
        doThrow(new SecurityException("REVOKED"))
                .when(fixture.captures)
                .authorizeSources(any(), any(), anySet(), any());
        assertThatThrownBy(this::compose).hasMessage("REVOKED");
        assertThat(fixture.members()).hasSize(1);
        verify(ledger, never()).phase(fixture.lease, CdsExportState.PACKAGING);
    }

    @Test
    void shouldAuditTheChartRead_whenTheJobStopsBeforeAnyPackage() throws Exception {
        doThrow(new SecurityException("REVOKED"))
                .when(fixture.captures)
                .authorizeSources(any(), any(), anySet(), any());
        assertThatThrownBy(this::compose).hasMessage("REVOKED");
        var entries =
                org.mockito.ArgumentCaptor.forClass(
                        io.github.carlos_emr.carlos.commn.model.OscarLog.class);
        verify(auditLogs).persist(entries.capture());
        assertThat(entries.getValue().getAction()).isEqualTo("read");
        assertThat(entries.getValue().getDemographicId()).isEqualTo(27);
        assertThat(entries.getValue().getData())
                .contains("CDS_EXPORT_CHART_ACCESS", "outcome=STOPPED", "outcomes=cds_export_event");
    }

    @Test
    void shouldDeleteTheFinishedZip_whenFinalCohortAuthorityIsRevoked() throws Exception {
        doNothing()
                .doThrow(new SecurityException("REVOKED"))
                .when(fixture.captures)
                .authorizeSources(any(), any(), anySet(), any());
        assertThatThrownBy(this::compose).hasMessage("REVOKED");
        assertThat(fixture.members()).hasSize(1);
        verify(ledger).phase(fixture.lease, CdsExportState.PACKAGING);
    }

    @Test
    void shouldRefusePolicyRotation_afterZipCompletionAndRemoveItsPrivateOutputs()
            throws Exception {
        when(disclosure.resolveAccepted(any(), any()))
                .thenReturn(policy, policy)
                .thenThrow(
                        new CdsExportReviewException(
                                CdsExportReviewException.Reason.SOURCE_CHANGED));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        assertThat(fixture.members()).hasSize(1);
    }

    @Test
    void shouldEnforceTheSupportingByteBudget_whileWritingBothFiles() throws Exception {
        var bounded =
                new CdsExportClinicalPackageService.Limits(
                        100, 1000, 8L << 20, 8L << 20, 1, 8L << 20);
        assertThatThrownBy(
                        () ->
                                service.compose(
                                        fixture.lease,
                                        CdsExportPatientPipelineFixture.VERSION,
                                        ZoneId.of("America/Toronto"),
                                        fixture.bounds,
                                        CdsExportXmlLimitsDto.defaults(),
                                        bounded,
                                        fixture.control))
                .hasMessage("CDS_EXPORT_RESOURCE_LIMIT");
        assertThat(fixture.members()).hasSize(1);
    }

    @Test
    void shouldRefuseAnImpossibleMinimumMemberBudget_beforeCapturingAnyPatient() throws Exception {
        var bounded =
                new CdsExportClinicalPackageService.Limits(
                        100, 2, 8L << 20, 8L << 20, 8L << 20, 8L << 20);
        assertThatThrownBy(
                        () ->
                                service.compose(
                                        fixture.lease,
                                        CdsExportPatientPipelineFixture.VERSION,
                                        ZoneId.of("America/Toronto"),
                                        fixture.bounds,
                                        CdsExportXmlLimitsDto.defaults(),
                                        bounded,
                                        fixture.control))
                .hasMessage("CDS_EXPORT_RESOURCE_LIMIT");
        verify(fixture.captures, never())
                .capture(any(), any(), anySet(), anyLong(), anyLong(), any());
    }

    private CdsExportCohortService.Patient secondPatient() throws IOException {
        var second = mock(CdsExportCohortService.Patient.class);
        when(second.patient()).thenReturn(28);
        when(second.cohortSha256()).thenReturn(fixture.cohort.plaintextSha256());
        when(ledger.status(fixture.run)).thenAnswer(call -> status(2L));
        when(cohorts.open(eq(fixture.lease), eq(fixture.control)))
                .thenAnswer(
                        call -> {
                            var cursor = mock(CdsExportCohortService.Cursor.class);
                            when(cursor.next())
                                    .thenReturn(
                                            fixture.patient,
                                            second,
                                            (CdsExportCohortService.Patient) null);
                            when(cursor.complete()).thenAnswer(ignored -> eof);
                            return cursor;
                        });
        return second;
    }

    @Test
    void shouldHoldOnlyTheChartNeedingReview_andPackageEveryOtherPatient() throws Exception {
        var second = secondPatient();
        when(fixture.captures.capture(any(), eq(second), anySet(), anyLong(), anyLong(), any()))
                .thenThrow(
                        new io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportMappingReviewException(
                                "CDS_EXPORT_NATIVE_REPORT_EVENT_DATES_CONFLICT"));
        var prepared = compose();
        assertThat(prepared.patients()).isEqualTo(2);
        assertThat(prepared.metadata().heldPatients())
                .containsExactly(
                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportHeldPatientDto(
                                28, "CDS_EXPORT_NATIVE_REPORT_EVENT_DATES_CONFLICT"));
        try (var input = fixture.store.open(prepared.authorization())) {
            var lines = new CdsExportJsonLines(input, 1 << 20, () -> {});
            assertThat(lines.next().path("patients").asLong()).isEqualTo(2);
            assertThat(lines.next().path("patient").asInt()).isEqualTo(27);
            var held = lines.next();
            assertThat(held.path("kind").asText()).isEqualTo("HELD");
            assertThat(held.path("patient").asInt()).isEqualTo(28);
            assertThat(held.path("code").asText())
                    .isEqualTo("CDS_EXPORT_NATIVE_REPORT_EVENT_DATES_CONFLICT");
            assertThat(lines.next().path("kind").asText()).isEqualTo("END");
            assertThat(lines.next()).isNull();
        }
        // The held chart's own captures are discarded and it is never authorized or audited
        // as disclosed; its chart read is still audited, as held.
        verify(fixture.captures, never()).authorizeSources(any(), eq(second), anySet(), any());
        var entries =
                org.mockito.ArgumentCaptor.forClass(
                        io.github.carlos_emr.carlos.commn.model.OscarLog.class);
        verify(auditLogs, times(2)).persist(entries.capture());
        assertThat(entries.getAllValues())
                .extracting(
                        io.github.carlos_emr.carlos.commn.model.OscarLog::getDemographicId,
                        io.github.carlos_emr.carlos.commn.model.OscarLog::getAction)
                .containsExactlyInAnyOrder(tuple(27, "EXPORT"), tuple(28, "read"));
        assertThat(
                        entries.getAllValues().stream()
                                .filter(entry -> entry.getDemographicId() == 28)
                                .findFirst()
                                .orElseThrow()
                                .getData())
                .contains("CDS_EXPORT_CHART_ACCESS", "outcome=HELD", fixture.lease.runId());
    }

    @Test
    void shouldStillStopTheWholeJob_whenARefusalConcernsEveryPatient() throws Exception {
        var second = secondPatient();
        when(fixture.captures.capture(any(), eq(second), anySet(), anyLong(), anyLong(), any()))
                .thenThrow(
                        new CdsExportReviewException(
                                CdsExportReviewException.Reason.RESOURCE_LIMIT));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_RESOURCE_LIMIT");
        assertThat(fixture.members()).hasSize(1);
    }

    @Test
    void shouldEndAsTheSharedReason_whenEveryPatientWasHeldForIt() throws Exception {
        var second = secondPatient();
        when(fixture.captures.capture(any(), any(), anySet(), anyLong(), anyLong(), any()))
                .thenThrow(new SecurityException("PATIENT_RESTRICTED"));
        var before = fixture.members();
        assertThatThrownBy(this::compose)
                .isInstanceOf(SecurityException.class)
                .hasMessage("CDS_EXPORT_ACCESS_CHANGED");
        // Nothing the job staged survives: only the fixture's own pre-staged inputs remain.
        assertThat(fixture.members()).isEqualTo(before);
        verify(ledger, never()).phase(fixture.lease, CdsExportState.RECONCILING);
        assertThat(second.patient()).isEqualTo(28);
    }

    @Test
    void shouldReportMixedHeldReasons_asPatientsHeld() throws IOException {
        var refusal =
                CdsExportClinicalPackageService.allHeld(
                        List.of(
                                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportHeldPatientDto(27, "CDS_EXPORT_ACCESS_CHANGED"),
                                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportHeldPatientDto(28, "CDS_EXPORT_UNMAPPED_DATA")));
        assertThat(refusal).hasMessage("CDS_EXPORT_PATIENTS_HELD");
        assertThat(
                        CdsExportClinicalPackageService.allHeld(
                                List.of(
                                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                .CdsExportHeldPatientDto(
                                                27, "CDS_EXPORT_NATIVE_REPORT_EVENT_DATES_CONFLICT"))))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_EVENT_DATES_CONFLICT");
        assertThat(
                        CdsExportClinicalPackageService.heldCode(
                                new CdsExportReviewException(
                                        CdsExportReviewException.Reason.RECIPIENT_UNAVAILABLE)))
                .isNull();
    }
}
