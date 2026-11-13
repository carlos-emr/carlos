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
package io.github.carlos_emr.carlos.demographic.cdsexport.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cdsexport.assembler.CdsExportViewModelAssembler;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportState;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CdsExportWebActionsUnitTest {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CdsExportService exports;
    private CdsExportAccessService access;
    private CdsExportWebService web;
    private SecurityInfoManager security;
    private LoggedInInfo viewer;

    @BeforeEach
    void prepare() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.addParameter("apiVersion", "1");
        viewer = mock(LoggedInInfo.class);
        var account = new Security();
        account.setSecurityNo(7);
        when(viewer.getLoggedInSecurity()).thenReturn(account);
        when(viewer.getLoggedInProviderNo()).thenReturn("999998");
        request.getSession()
                .setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", viewer);
        exports = mock(CdsExportService.class);
        access = mock(CdsExportAccessService.class);
        web = new CdsExportWebService(access);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class)))
                .thenReturn(true);
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
    }

    @AfterEach
    void clear() {
        ActionContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "revoked"})
    void shouldRefuseEveryExportRoute_whenTheSessionIsMissingOrItsLiveIdentityIsRevoked(
            String state) throws Exception {
        if (state.equals("absent")) request.getSession().invalidate();
        else
            org.mockito.Mockito.doThrow(new SecurityException("private identity context"))
                    .when(access)
                    .operatorSession(viewer);
        var artifacts =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportArtifactService.class);
        for (ActionSupport action :
                java.util.List.of(
                        submission(),
                        new CdsExportControl2Action(web, exports, security),
                        new CdsExportStatus2Action(web, exports, security),
                        new CdsExportDownload2Action(web, exports, artifacts, security),
                        new CdsExportReport2Action(web, exports, security))) {
            request.setMethod(action instanceof CdsExportStatus2Action ? "GET" : "POST");
            response = new MockHttpServletResponse();
            ActionContext.getContext().withServletResponse(response);
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).doesNotContain("private identity context");
        }
        request.setMethod("GET");
        var assembler = mock(CdsExportViewModelAssembler.class);
        assertThatThrownBy(() -> new ViewCdsExport2Action(web, assembler, security).execute())
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(exports, artifacts, assembler);
        if (state.equals("absent")) verifyNoInteractions(access, security);
    }

    @Test
    void shouldOfferTheExportLink_onlyToUsersTheExportPageAdmits() {
        var actor = mock(LoggedInInfo.class);
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(actor, "_demographic", "r", (String) null)).thenReturn(true);
        assertThat(CdsExportWebService.mayOpen(actor, security)).isFalse();
        when(security.hasPrivilege(actor, "_cds_export", "w", (String) null)).thenReturn(true);
        assertThat(CdsExportWebService.mayOpen(actor, security)).isTrue();
        when(security.hasPrivilege(actor, "_demographic", "r", (String) null)).thenReturn(false);
        assertThat(CdsExportWebService.mayOpen(actor, security)).isFalse();
        var administrator = mock(LoggedInInfo.class);
        when(security.hasPrivilege(administrator, "_demographic", "r", (String) null)).thenReturn(true);
        when(security.hasPrivilege(administrator, "_admin", "w", (String) null)).thenReturn(true);
        assertThat(CdsExportWebService.mayOpen(administrator, security)).isTrue();
        assertThat(CdsExportWebService.mayOpen(null, security)).isFalse();
    }

    @Test
    void shouldResumeOnlyTheObservedJobRevision_andReturnItsDurableState() throws Exception {
        request.setMethod("POST");
        var job = job();
        var action = new CdsExportControl2Action(web, exports, security);
        action.setRunId(job.id());
        action.setOperation("resume");
        action.setExpectedRevision(12);
        when(exports.resume(viewer, job.id(), 12)).thenReturn(job);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"ok\":true", job.id());
        verify(exports).resume(viewer, job.id(), 12);
        verify(exports, org.mockito.Mockito.never()).cancel(any(), anyString(), anyLong());
    }

    @Test
    void shouldReturnTheAuthorizedDiagnosticReport_withSafeDownloadHeaders() throws Exception {
        request.setMethod("POST");
        var id = job().id();
        var report =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDiagnosticDto(
                        1,
                        "REPORT",
                        id,
                        "COMPLETED",
                        1,
                        1L,
                        1,
                        0,
                        false,
                        true,
                        true,
                        null,
                        "2026-11-10T00:00:00Z",
                        "2026-11-10T00:00:01Z",
                        false,
                        java.util.List.of());
        when(exports.report(viewer, id)).thenReturn(report);
        var action = new CdsExportReport2Action(web, exports, security);
        action.setRunId(id);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"cds-export-diagnostic.json\"");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).contains("\"report\":", "\"complete\":true");
        verify(exports).report(viewer, id);
    }

    @Test
    void shouldExposeOnlyQualifiedProfilesAndCurrentDispatcherState_usingTheRealAssembler() {
        var configuration =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportConfigurationService.class);
        var assembler = new CdsExportViewModelAssembler(configuration, exports);
        when(configuration.enabled()).thenReturn(true);
        when(exports.dispatcherRunning()).thenReturn(true);
        when(exports.qualifiedProfiles())
                .thenReturn(
                        java.util.Set.of(
                                io.github.carlos_emr.carlos.demographic.cds.contract
                                        .CdsLogicalProfile.ONTARIO_MD_5_2));
        var model = assembler.assemble(viewer);
        assertThat(model.enabled()).isTrue();
        assertThat(model.dispatcherRunning()).isTrue();
        assertThat(model.operatorKey()).isEqualTo("web-7");
        assertThat(model.providerNo()).isEqualTo("999998");
        assertThat(model.facilityId()).isEmpty();
        assertThat(model.profiles())
                .extracting(
                        io.github.carlos_emr.carlos.demographic.cdsexport.viewmodel
                                        .CdsExportViewModel.Option
                                ::value)
                .containsExactly("omd-5.2");
        assertThat(model.categories()).hasSize(16);
        when(exports.qualifiedProfiles()).thenReturn(java.util.Set.of());
        assertThat(assembler.assemble(viewer).enabled()).isFalse();
        when(configuration.enabled()).thenReturn(false);
        assertThat(assembler.assemble(viewer).enabled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRejectNonPostSubmissionAndControl_beforeAnyService(String method) throws Exception {
        request.setMethod(method);
        var parser = mock(CdsExportWebCommandParser.class);
        assertThat(new SubmitCdsExport2Action(web, parser, exports, security).execute())
                .isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        response = new MockHttpServletResponse();
        ActionContext.getContext().withServletResponse(response);
        assertThat(new CdsExportControl2Action(web, exports, security).execute())
                .isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(exports, access, parser, security);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_cds_export"})
    void shouldRefuseEveryExportRoute_beforeServicesWhenRequestPrivilegesAreMissing(String denied)
            throws Exception {
        request.setMethod("POST");
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(
                        call -> {
                            String object = call.getArgument(1);
                            return denied.equals("_demographic")
                                    ? !object.equals("_demographic")
                                    : !object.equals("_admin") && !object.equals("_cds_export");
                        });
        var artifacts =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportArtifactService.class);
        for (ActionSupport action :
                java.util.List.of(
                        submission(),
                        new CdsExportControl2Action(web, exports, security),
                        new CdsExportStatus2Action(web, exports, security),
                        new CdsExportDownload2Action(web, exports, artifacts, security),
                        new CdsExportReport2Action(web, exports, security))) {
            request.setMethod(action instanceof CdsExportStatus2Action ? "GET" : "POST");
            response = new MockHttpServletResponse();
            ActionContext.getContext().withServletResponse(response);
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
        }
        request.setMethod("GET");
        var assembler = mock(CdsExportViewModelAssembler.class);
        assertThatThrownBy(() -> new ViewCdsExport2Action(web, assembler, security).execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + denied + ")");
        verifyNoInteractions(exports, access, artifacts, assembler);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CDS_EXPORT_RUN_NOT_FOUND",
                "CDS_EXPORT_CANCEL_INVALID",
                "CDS_EXPORT_WORKER_BUSY",
                "CDS_EXPORT_RESUME_INVALID",
                "CDS_EXPORT_ADMISSION_DISABLED",
                "CDS_EXPORT_PROFILE_NOT_QUALIFIED",
                "private patient /path"
            })
    void shouldClassifyKnownStateErrors_withoutExposingUnexpectedFailureDetails(String code)
            throws Exception {
        web.failure(response, new IllegalStateException(code));
        assertThat(response.getStatus())
                .isEqualTo(
                        code.equals("CDS_EXPORT_RUN_NOT_FOUND")
                                ? 404
                                : code.startsWith("CDS_EXPORT_") ? 409 : 503);
        assertThat(response.getContentAsString()).doesNotContain("private patient /path");
    }

    @Test
    void shouldNameOnlyAFixedMissingRight_whenAccessIsRefused() throws Exception {
        web.failure(
                response, new SecurityException("missing required sec object (_lab_sensitive)"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"CDS_EXPORT_ACCESS_DENIED\"", "\"missingRight\":\"_lab_sensitive\"");
        response = new org.springframework.mock.web.MockHttpServletResponse();
        web.failure(response, new SecurityException("patient 27 private path"));
        assertThat(response.getContentAsString())
                .contains("CDS_EXPORT_ACCESS_DENIED")
                .doesNotContain("missingRight", "27", "private path");
    }

    @ParameterizedTest
    @ValueSource(strings = {"write", "complete", "close"})
    void shouldKeepCommittedDownloadBytes_withoutEnteringStrutsErrorRendering(String phase)
            throws Exception {
        request.setMethod("POST");
        var artifacts =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportArtifactService.class);
        var download =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportArtifactService.Download.class);
        when(artifacts.open(any(), any(), any())).thenReturn(download);
        when(download.bytes()).thenReturn(3L);
        org.mockito.Mockito.doAnswer(
                        call -> {
                            ((java.io.OutputStream) call.getArgument(0))
                                    .write(new byte[] {1, 2, 3});
                            response.flushBuffer();
                            if (phase.equals("write"))
                                throw new java.io.IOException("synthetic private path");
                            return null;
                        })
                .when(download)
                .writeTo(any());
        if (phase.equals("complete"))
            org.mockito.Mockito.doThrow(new IllegalStateException("synthetic private path"))
                    .when(download)
                    .complete();
        if (phase.equals("close"))
            org.mockito.Mockito.doThrow(new java.io.IOException("synthetic private path"))
                    .when(download)
                    .close();
        var action = new CdsExportDownload2Action(web, exports, artifacts, security);
        action.setRunId(job().id());
        action.setArtifact("package");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3);
        verify(download).close();
        if (phase.equals("write"))
            org.mockito.Mockito.verify(download, org.mockito.Mockito.never()).complete();
    }

    @Test
    void shouldReturnDurableAcceptance_withoutClaimingCompletion() throws Exception {
        request.setMethod("POST");
        var job = job();
        var action = submission();
        when(exports.submit(any(), any())).thenReturn(job);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getHeader(CdsExportWebService.API_HEADER)).isEqualTo("1");
        assertThat(response.getContentAsString())
                .contains(
                        "\"waiting\":true",
                        "\"complete\":false",
                        "\"totalPatients\":null",
                        job.id());
        verify(access).operatorSession(viewer);
    }

    @Test
    void shouldRefuseDuplicatedParameters_andNeverAcceptASmallerSelection() throws Exception {
        request.setMethod("POST");
        request.addParameter("patientNos", "27", "28");
        var action = submission();
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(exports);
    }

    @Test
    void shouldRefuseUnknownParameters_beforeSubmitting() throws Exception {
        request.setMethod("POST");
        request.addParameter("patientLimit", "1");
        var action = submission();
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(exports);
    }

    @Test
    void shouldRequireExplicitApiVersion_beforeSubmitting() throws Exception {
        request.setMethod("POST");
        request.removeParameter("apiVersion");
        submission().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(exports);
    }

    @Test
    void shouldRefuseRevokedScope_withoutExposingIntentOrExceptionDetails() throws Exception {
        request.setMethod("POST");
        when(exports.submit(any(), any()))
                .thenThrow(new SecurityException("Patient 27 at /private/clinical-store"));
        submission().execute();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .contains("CDS_EXPORT_ACCESS_DENIED")
                .doesNotContain("Patient 27", "clinical-store");
    }

    @Test
    void shouldRequireARevision_beforeAnyDurableControl() throws Exception {
        request.setMethod("POST");
        var action = new CdsExportControl2Action(web, exports, security);
        action.setRunId(job().id());
        action.setOperation("cancel");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(exports);
    }

    @Test
    void shouldPassTheObservedRevision_andReportAConflictWithoutSuccess() throws Exception {
        request.setMethod("POST");
        var action = new CdsExportControl2Action(web, exports, security);
        var job = job();
        action.setRunId(job.id());
        action.setOperation("cancel");
        action.setExpectedRevision(3);
        when(exports.cancel(viewer, job.id(), 3))
                .thenThrow(
                        new io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportStaleControlException());
        action.execute();
        verify(exports).cancel(viewer, job.id(), 3);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString())
                .contains("CDS_EXPORT_STATUS_CHANGED", "\"ok\":false");
    }

    @Test
    void shouldRecoverSubmissionByKey_withoutRecreatingTheIntent() throws Exception {
        var action = new CdsExportStatus2Action(web, exports, security);
        action.setSubmissionKey("saved-key");
        when(exports.submission(viewer, "saved-key")).thenReturn(job());
        action.execute();
        verify(exports).submission(viewer, "saved-key");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldKeepObservationParametersUnambiguous_forWebActionsContract() throws Exception {
        var action = new CdsExportStatus2Action(web, exports, security);
        action.setSubmissionKey("saved-key");
        action.setRunId(job().id());
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(exports);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"true", "false"})
    void shouldObserveManualAndBackgroundStatus_withoutChangingTheJob(String background)
            throws Exception {
        if (background != null) request.addParameter("autoRefresh", background);
        var job = job();
        var action = new CdsExportStatus2Action(web, exports, security);
        action.setRunId(job.id());
        when(exports.observe(viewer, job.id())).thenReturn(job);
        action.execute();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(exports).observe(viewer, job.id());
        assertThat(response.getContentAsString())
                .contains(job.id(), "\"waiting\":true", "\"complete\":false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "1", "invalid", ""})
    void shouldRejectMalformedBackgroundFlags_beforeReadingJobData(String flag) throws Exception {
        request.addParameter("autoRefresh", flag);
        var action = new CdsExportStatus2Action(web, exports, security);
        action.setRunId(job().id());
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(exports);
    }

    @Test
    void shouldRejectRepeatedBackgroundFlags_beforeReadingJobData() throws Exception {
        request.addParameter("autoRefresh", "true", "false");
        var action = new CdsExportStatus2Action(web, exports, security);
        action.setRunId(job().id());
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(exports);
    }

    @Test
    void shouldRenderExactlyOneAssembledPageModel_withoutClinicalReads() throws Exception {
        var assembler = mock(CdsExportViewModelAssembler.class);
        var model =
                new io.github.carlos_emr.carlos.demographic.cdsexport.viewmodel.CdsExportViewModel(
                        false,
                        false,
                        "web-7",
                        "999998",
                        "",
                        java.util.List.of(),
                        java.util.List.of());
        when(assembler.assemble(viewer)).thenReturn(model);
        assertThat(new ViewCdsExport2Action(web, assembler, security).execute())
                .isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("model")).isSameAs(model);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verifyNoInteractions(exports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"27,27", "27,", ",27", "027", "27.0", "-1", "2147483648"})
    void shouldRefuseInvalidSelectedPatientLists_withoutCoercingOrDroppingRows(String selected) {
        assertThatThrownBy(
                        () ->
                                new CdsExportWebCommandParser()
                                        .parse(
                                                "submission",
                                                "999998",
                                                "",
                                                "omd-5.2",
                                                "SELECTED_PATIENTS",
                                                "",
                                                selected,
                                                "Demographics",
                                                "recipient",
                                                "audit"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRetainEverySelectedCategory_andRefuseDuplicates() {
        var parser = new CdsExportWebCommandParser();
        var command =
                parser.parse(
                        "submission",
                        "999998",
                        "",
                        "omd-5.2",
                        "SELECTED_PATIENTS",
                        "",
                        "28,27",
                        "Demographics,ClinicalNotes",
                        "recipient",
                        "audit");
        assertThat(command.patientNos()).containsExactly(27, 28);
        assertThat(command.categories()).hasSize(2);
        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        "submission",
                                        "999998",
                                        "",
                                        "omd-5.2",
                                        "SELECTED_PATIENTS",
                                        "",
                                        "27",
                                        "Demographics,Demographics",
                                        "recipient",
                                        "audit"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRejectNonPostArtifactRequests_beforeAuthorizationOrFileAccess(String method)
            throws Exception {
        request.setMethod(method);
        var artifacts =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportArtifactService.class);
        new CdsExportDownload2Action(web, exports, artifacts, security).execute();
        assertThat(response.getStatus()).isEqualTo(405);
        response = new MockHttpServletResponse();
        ActionContext.getContext().withServletResponse(response);
        new CdsExportReport2Action(web, exports, security).execute();
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(exports, access, artifacts);
    }

    @Test
    void shouldSendOnlyTheAuthorizedRecipientCiphertext_withSafeBinaryHeaders() throws Exception {
        request.setMethod("POST");
        var artifacts =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportArtifactService.class);
        var download =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportArtifactService.Download.class);
        var actor =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto(
                        "web-7", "999998", 7, null);
        var id = job().id();
        when(exports.downloadActor(viewer, id)).thenReturn(actor);
        when(artifacts.open(actor, id, "package")).thenReturn(download);
        when(download.bytes()).thenReturn(3L);
        org.mockito.Mockito.doAnswer(
                        call -> {
                            ((java.io.OutputStream) call.getArgument(0))
                                    .write(new byte[] {1, 2, 3});
                            return null;
                        })
                .when(download)
                .writeTo(any());
        var action = new CdsExportDownload2Action(web, exports, artifacts, security);
        action.setRunId(id);
        action.setArtifact("package");
        action.execute();
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3);
        assertThat(response.getContentType()).isEqualTo("application/pkcs7-mime");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"cds-export.p7m\"");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(download).complete();
        verify(download).close();
    }

    @Test
    void shouldReturnAnExplicitUnavailableError_withoutAnAttachmentOrInternalPaths()
            throws Exception {
        request.setMethod("POST");
        var artifacts =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportArtifactService.class);
        var id = job().id();
        when(artifacts.open(any(), anyString(), anyString()))
                .thenThrow(
                        new io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportArtifactUnavailableException(
                                io.github.carlos_emr.carlos.demographic.cdsexport.support
                                        .CdsExportArtifactUnavailableException.Kind.PACKAGE));
        var action = new CdsExportDownload2Action(web, exports, artifacts, security);
        action.setRunId(id);
        action.setArtifact("package");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentAsString()).contains("CDS_EXPORT_ARTIFACT_UNAVAILABLE");
    }

    @Test
    void shouldReplaceTheAttachmentHeader_whenADownloadFailsBeforeItsFirstByte() throws Exception {
        response.setContentType("application/pkcs7-mime");
        response.setHeader("Content-Disposition", "attachment; filename=\"cds-export.p7m\"");
        response.setContentLengthLong(4096);

        web.failure(response, new java.io.IOException("synthetic package read failure"));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("CDS_EXPORT_REQUEST_UNAVAILABLE");
    }

    @Test
    void shouldAnswerADownloadWindowWithAPageInTheViewersLanguage_whenThePackageIsUnavailable()
            throws Exception {
        request.addPreferredLocale(java.util.Locale.CANADA_FRENCH);

        web.navigationFailure(
                request,
                response,
                new io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportArtifactUnavailableException(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportArtifactUnavailableException.Kind.PACKAGE));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).startsWith("text/html");
        assertThat(response.getContentAsString())
                .contains("<html lang=\"fr\">")
                .contains("Le paquet de transfert n'est pas disponible")
                .contains("CDS_EXPORT_ARTIFACT_UNAVAILABLE")
                .doesNotContain("\"ok\"");
    }

    @Test
    void shouldKeepTheJsonAnswer_forAScriptCallerOfAWindowRoute() throws Exception {
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        web.navigationFailure(
                request, response, new IllegalStateException("CDS_EXPORT_ARTIFACT_BUSY"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"CDS_EXPORT_ARTIFACT_BUSY\"");
    }

    @Test
    void shouldNameTheParsersFixedCode_withoutEchoingFreeText() throws Exception {
        web.failure(response, new IllegalArgumentException("CDS_EXPORT_SELECTION_INVALID"));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":\"CDS_EXPORT_SELECTION_INVALID\"");

        response = new MockHttpServletResponse();
        web.failure(response, new NumberFormatException("For input string: \"FAKE-42\""));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"CDS_EXPORT_PARAMETERS_INVALID\"")
                .doesNotContain("FAKE-42");
    }

    private SubmitCdsExport2Action submission() {
        var action =
                new SubmitCdsExport2Action(web, new CdsExportWebCommandParser(), exports, security);
        action.setSubmissionKey("submission");
        action.setProviderNo("999998");
        action.setProfile("omd-5.2");
        action.setSelection("SELECTED_PATIENTS");
        action.setPatientNos("27");
        action.setCategories("Demographics");
        action.setRecipientId("recipient");
        action.setAuditPlanId("audit");
        return action;
    }

    private CdsExportJobDto job() {
        return new CdsExportJobDto(
                UUID.randomUUID().toString(),
                CdsExportState.ACCEPTED,
                0,
                null,
                0,
                0,
                false,
                false,
                0,
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"),
                false);
    }
}
