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
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cdsimport.assembler.CdsImportViewModelAssembler;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportConfigurationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsUploadService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Instant;
import java.util.Map;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CdsImportWebActionsUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CdsImportAccessService access;
    private CdsImportService imports;
    private CdsUploadService uploads;
    private LoggedInInfo actor;

    @BeforeEach void prepare() {
        request = new MockHttpServletRequest(); response = new MockHttpServletResponse();
        request.setMethod("GET"); request.getSession();
        access = mock(CdsImportAccessService.class); imports = mock(CdsImportService.class); uploads = mock(CdsUploadService.class);
        actor = mock(LoggedInInfo.class); Security login = new Security(); login.setSecurityNo(42);
        when(actor.getLoggedInSecurity()).thenReturn(login); when(actor.getLoggedInProviderNo()).thenReturn("999998");
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", actor);
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
    }
    @AfterEach void clear() { ActionContext.clear(); }

    @ParameterizedTest @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectUnsafeMethods_withoutAnyMutationDependency(String method) throws Exception {
        request.setMethod(method);
        var upload = new CdsUpload2Action(access, uploads);
        assertThat(upload.execute()).isEqualTo(ActionSupport.NONE); assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(uploads, imports);
        response = new MockHttpServletResponse(); ActionContext.getContext().withServletResponse(response);
        var control = new CdsImportControl2Action(access, imports);
        assertThat(control.execute()).isEqualTo(ActionSupport.NONE); assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(uploads, imports);
    }

    @Test void shouldRefuseLegacyPostBeforeAcceptance_whenAnOldTabSubmits() throws Exception {
        request.setMethod("POST"); var assembler = mock(CdsImportViewModelAssembler.class);
        assertThat(new ViewCdsImport2Action(access, assembler).execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("IMPORT_CLIENT_REFRESH_REQUIRED");
        verifyNoInteractions(assembler, imports, uploads);
    }

    @Test void shouldRefuseDuplicateAndUnknownParameters_withoutUploadWrites() throws Exception {
        request.setMethod("POST"); request.addParameter("operation", "begin", "finish");
        var action = new CdsUpload2Action(access, uploads); action.setOperation("begin");
        action.execute(); assertThat(response.getStatus()).isEqualTo(400); verifyNoInteractions(uploads);
    }

    @Test void shouldNotExposePhysicalLocations_whenProjectingJobOrSummary() throws Exception {
        var job = new CdsImportJobDto("11111111-1111-4111-8111-111111111111", "web-42", "999998", "clinic", "omd-5.2",
                "RUNNING", "a".repeat(64), "b".repeat(64), "c".repeat(64), null, 1, 2, 1, false,
                Instant.now(), Instant.now(), 1, "/private/Synthetic-Patient/report", "d".repeat(64));
        CdsWeb.json(response, 200, CdsWeb.job(job));
        assertThat(response.getContentAsString()).doesNotContain("Synthetic-Patient", "/private", "reportLocation");
        assertThat(CdsWeb.summary(Map.of("lastCheckpoint", Instant.parse("2026-01-01T00:00:00Z"))))
                .isEqualTo(Map.of("lastCheckpoint", "2026-01-01T00:00:00Z"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CdsWeb.summary(Map.of("reportLocation", "/private")))
                .hasMessage("IMPORT_SUMMARY_INVALID");
    }

    @Test void shouldReturnUnauthorizedWithoutRawExceptionDetails_whenSessionIsAbsent() throws Exception {
        request.setMethod("POST"); request.getSession().invalidate(); request.setSession(null);
        new CdsUpload2Action(access, uploads).execute();
        assertThat(response.getStatus()).isEqualTo(401); verifyNoInteractions(uploads);
        assertThat(response.getContentAsString()).doesNotContain("Exception", "java.");
    }

    @Test void shouldReturnRequiredReportError_withoutAcceptingAPathParameter() throws Exception {
        request.addParameter("path", "/private/secret");
        var report = new CdsImportReport2Action(access, imports); report.setRunId("11111111-1111-4111-8111-111111111111"); report.setType("event");
        report.execute(); assertThat(response.getStatus()).isEqualTo(400); verifyNoInteractions(imports);
        assertThat(response.getContentAsString()).doesNotContain("/private");
    }

    @Test void shouldDeliverOnlyAuthorizedSafeEvidence_byRunAndDiagnosticIdentity() throws Exception {
        String run="11111111-1111-4111-8111-111111111111";
        when(imports.diagnosticEvidence(actor,run,7L)).thenReturn(Map.of("version",1,"ownerEpoch",2,
                "causes",java.util.List.of(Map.of("type","java.sql.SQLException","sqlState","45000","frames",java.util.List.of("Known#method:12")))));
        var action=new CdsImportDiagnostic2Action(access,imports);action.setRunId(run);action.setDiagnosticId(7L);
        action.execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("45000","Known#method:12").doesNotContain("protectedDetailLocation","/private");
        org.mockito.Mockito.verify(access).requireOperatorSession(actor);
        org.mockito.Mockito.verify(imports).diagnosticEvidence(actor,run,7L);
    }

    @Test void shouldRefuseEvidencePathInjection_andUnsafeMethodBeforeEvidenceReads() throws Exception {
        request.addParameter("path","/private/secret");
        var action=new CdsImportDiagnostic2Action(access,imports);action.setRunId("11111111-1111-4111-8111-111111111111");action.setDiagnosticId(7L);
        action.execute();assertThat(response.getStatus()).isEqualTo(400);verifyNoInteractions(imports);
        request.setMethod("POST");response=new MockHttpServletResponse();ActionContext.getContext().withServletResponse(response);
        action.execute();assertThat(response.getStatus()).isEqualTo(405);verifyNoInteractions(imports);
    }

    @Test void shouldAdvertiseSafeFailureEvidence_withoutLeakingItsPrivateLocation() {
        var row=new CdsImportDiagnosticDto(7,null,null,"ERROR","NATIVE_FAILURE","EXECUTION",null,"Safe summary",
                "/private/job/diagnostics/failure-2-11111111-1111-4111-8111-111111111111.json",true,false,Instant.now());
        assertThat(CdsWeb.diagnostic(row)).containsEntry("hasEvidence",true)
                .doesNotContainKeys("protectedDetailLocation","location");
        assertThat(CdsWeb.diagnostic(row).toString()).doesNotContain("/private");
        var source=new CdsImportDiagnosticDto(8,null,null,"ERROR","XML_INVALID","PREFLIGHT",null,"Safe summary",
                "/private/job/source.xml",true,false,Instant.now());
        assertThat(CdsWeb.diagnostic(source)).containsEntry("hasEvidence",false);
    }
}
