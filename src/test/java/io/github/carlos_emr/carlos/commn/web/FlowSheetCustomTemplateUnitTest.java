/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.FlowSheetCustomizationDao;
import io.github.carlos_emr.carlos.commn.dao.FlowSheetUserCreatedDao;
import io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization;
import io.github.carlos_emr.carlos.commn.service.FlowSheetCustomizationService;
import io.github.carlos_emr.carlos.commn.service.FlowSheetCustomizationService.CascadeCheckResult;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.jdom2.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("clinical")
class FlowSheetCustomTemplateUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"absent", "0"})
    void shouldRejectUnknownTemplate_beforePersistingAnyCustomization(String count) throws Exception {
        save(count, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "0"})
    void shouldKeepSavingCustomizations_forAnExistingTemplate(String count) throws Exception {
        save(count, true);
    }

    @Test
    void shouldRejectMissingTemplate_beforePersistingAnyCustomization() throws Exception {
        save("absent", false, null);
    }

    private void save(String count, boolean exists) throws Exception {
        save(count, exists, "Synthetic template");
    }

    private void save(String count, boolean exists, String flowsheet) throws Exception {
        var dao = createAndRegisterMock(FlowSheetCustomizationDao.class);
        createAndRegisterMock(FlowSheetUserCreatedDao.class);
        var customization = createAndRegisterMock(FlowSheetCustomizationService.class);
        when(customization.checkCascadingBlocked(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CascadeCheckResult.allowed());
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
        var user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        var request = new MockHttpServletRequest();
        request.setMethod("POST");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        request.setParameter("method", "save");
        if (flowsheet != null) request.setParameter("flowsheet", flowsheet);
        request.setParameter("measurement", "BP");
        request.setParameter("display_name", "Blood pressure");
        request.setParameter("guideline", "");
        request.setParameter("graphable", "yes");
        request.setParameter("value_name", "mmHg");
        if (!"absent".equals(count)) request.setParameter("count", count);
        var response = new MockHttpServletResponse();
        var config = mock(MeasurementTemplateFlowSheetConfig.class);
        when(config.getFlowSheet("Synthetic template")).thenReturn(exists ? mock(MeasurementFlowSheet.class) : null);
        when(config.getItemFromObject(any())).thenReturn(new Element("measurement"));
        try (var servlet = mockStatic(ServletActionContext.class);
             var templates = mockStatic(MeasurementTemplateFlowSheetConfig.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            templates.when(MeasurementTemplateFlowSheetConfig::getInstance).thenReturn(config);
            String result = new FlowSheetCustom2Action().execute();
            if (exists) {
                assertThat(result).isEqualTo(ActionSupport.SUCCESS);
                assertThat(response.getStatus()).isEqualTo(200);
                verify(dao).persist(argThat((FlowSheetCustomization value) ->
                        "Synthetic template".equals(value.getFlowsheet()) && "999998".equals(value.getProviderNo())));
            } else {
                assertThat(result).isEqualTo(ActionSupport.NONE);
                assertThat(response.getStatus()).isEqualTo(404);
                verifyNoInteractions(dao);
            }
        }
    }
}
