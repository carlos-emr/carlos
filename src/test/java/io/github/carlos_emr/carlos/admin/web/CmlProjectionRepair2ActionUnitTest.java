/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.admin.web;

import org.junit.jupiter.api.*;
import org.apache.struts2.ServletActionContext;
import org.mockito.MockedStatic;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.lab.service.CmlProjectionService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/** HTTP boundary guards around the independently transactional projection repair service.
 * @since 2026-11-09
 */
@Tag("unit")
class CmlProjectionRepair2ActionUnitTest extends CarlosUnitTestBase {
    private MockedStatic<ServletActionContext> context;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CmlProjectionService service;
    private SecurityInfoManager security;
    @BeforeEach void setup() {
        request=new MockHttpServletRequest();response=new MockHttpServletResponse();
        context=mockStatic(ServletActionContext.class);
        context.when(ServletActionContext::getRequest).thenReturn(request);
        context.when(ServletActionContext::getResponse).thenReturn(response);
        service=createAndRegisterMock(CmlProjectionService.class);
        security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(isNull(),eq("_admin"),eq("w"),isNull())).thenReturn(true);
    }
    @AfterEach void close(){context.close();}
    @Test void shouldRefuseWithoutAdminWrite_beforeSourceLookup() throws Exception {
        when(security.hasPrivilege(isNull(),eq("_admin"),eq("w"),isNull())).thenReturn(false);
        assertThat(new CmlProjectionRepair2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);verifyNoInteractions(service);
    }
    @Test void shouldRefuseGetApply_withoutCallingTheService() throws Exception {
        request.setMethod("GET");request.setParameter("operation","apply");request.setParameter("reportNo","17");
        new CmlProjectionRepair2Action().execute();assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");verifyNoInteractions(service);
    }
    @Test void shouldRefuseDuplicateSourceIds_beforePreview() throws Exception {
        request.setParameter("operation","preview");request.setParameter("reportNo",new String[]{"17","18"});
        new CmlProjectionRepair2Action().execute();assertThat(response.getStatus()).isEqualTo(400);verifyNoInteractions(service);
    }
    @Test void shouldPreviewOnly_whenTheOperatorHasNotAppliedThePlan() throws Exception {
        request.setParameter("operation","preview");request.setParameter("reportNo","17");
        var plan=new CmlProjectionService.Summary("a".repeat(64),CmlProjectionService.Reason.READY,1,0,false);
        when(service.preview(17,null)).thenReturn(plan);
        assertThat(new CmlProjectionRepair2Action().execute()).isEqualTo("success");
        assertThat(request.getAttribute("projectionPlan")).isSameAs(plan);
        verify(service).preview(17,null);verifyNoMoreInteractions(service);
    }
    @Test void shouldReturnConflict_whenTheReviewedFingerprintIsStale() throws Exception {
        request.setMethod("POST");request.setParameter("operation","apply");request.setParameter("reportNo","17");
        request.setParameter("fingerprint","a".repeat(64));
        when(service.apply(eq(17),anyString(),isNull(),anyString())).thenThrow(new CmlProjectionService.StalePlan());
        assertThat(new CmlProjectionRepair2Action().execute()).isEqualTo("success");
        assertThat(response.getStatus()).isEqualTo(409);assertThat(request.getAttribute("repairError")).isEqualTo("clinical.cml.stale");
        assertThat(request.getAttribute("repairApplied")).isNull();
    }
    @Test void shouldReturnVisibleFailure_withoutClaimingCommitWhenRepairFails() throws Exception {
        request.setMethod("POST");request.setParameter("operation","apply");request.setParameter("reportNo","17");
        request.setParameter("fingerprint","a".repeat(64));
        when(service.apply(eq(17),anyString(),isNull(),anyString())).thenThrow(new IllegalStateException("injected failure"));
        new CmlProjectionRepair2Action().execute();assertThat(response.getStatus()).isEqualTo(500);
        assertThat(request.getAttribute("repairError")).isEqualTo("clinical.cml.failed");assertThat(request.getAttribute("repairApplied")).isNull();
    }
}
