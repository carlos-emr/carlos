/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.prescription.util.DrugrefUtil;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Vector;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Patient isolation, preference filtering and explicit failure on the live interaction JSON route. */
@Tag("unit")
class RxInteractionWarnings2ActionUnitTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnExplicitCheckStatus_whenDrugRefSucceedsOrFails(boolean failed) throws Exception {
        var request = new MockHttpServletRequest();
        request.setParameter("demographicNo", "42");
        var response = new MockHttpServletResponse();
        var security = mock(SecurityInfoManager.class);
        var info = mock(LoggedInInfo.class);
        var bean = mock(RxSessionBean.class);
        when(bean.getDemographicNo()).thenReturn(42);
        when(bean.getAtcCodes()).thenReturn(new Vector<>(List.of("J01MA02", "R03DA04")));
        when(bean.getStash()).thenReturn(new RxPrescriptionData.Prescription[0]);
        when(security.hasPrivilege(info, "_rx", "r", null)).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class);
             var access = mockStatic(RxRequestedPatientAccess.class);
             var prefs = mockConstruction(DrugrefUtil.class, (value, context) ->
                     when(value.getWarningLevel(info, 42)).thenReturn(3));
             var data = mockConstruction(RxDrugData.class, (value, context) -> {
                 if (failed) when(value.getInteractions(any())).thenThrow(new IllegalStateException());
                 else when(value.getInteractions(any())).thenReturn(new RxDrugData.Interaction[0]);
             })) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            access.when(() -> RxRequestedPatientAccess.resolveAuthorised(request, "_rx", "r")).thenReturn(bean);
            assertThat(new RxInteractionWarnings2Action(security).execute()).isEqualTo("none");
            assertThat(response.getContentType()).startsWith("application/json");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString())
                    .get("checkFailed").asBoolean()).isEqualTo(failed);
        }
    }

    @Test
    void shouldRefuseMissingPatient_withoutUsingTheActivePatientFallback() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_rx"), eq("r"), isNull())).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class);
             var data = mockConstruction(RxDrugData.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new RxInteractionWarnings2Action(security).execute()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(data.constructed()).isEmpty();
        }
    }
}
