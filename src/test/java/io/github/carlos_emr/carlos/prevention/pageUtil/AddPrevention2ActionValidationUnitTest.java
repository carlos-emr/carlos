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
package io.github.carlos_emr.carlos.prevention.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.ConsentDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Request-level regression tests for demographic number validation in
 * {@link AddPrevention2Action}.
 *
 * @since 2026-08-14
 */
@Tag("unit")
@DisplayName("AddPrevention2Action demographic number validation")
class AddPrevention2ActionValidationUnitTest {

    private static final String PREVENTION_TYPE = "Flu";

    @Test
    @DisplayName("should return form without lookup or persistence when demographic_no is missing")
    void shouldReturnForm_withoutLookupOrPersistence_whenDemographicNoIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        request.setParameter("prevention", PREVENTION_TYPE);
        request.setParameter("prevDate", "2026-08-14");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DemographicDao demographicDao = mock(DemographicDao.class);
        PartialDateDao partialDateDao = mock(PartialDateDao.class);
        ConsentDao consentDao = mock(ConsentDao.class);
        CVCImmunizationDao immunizationDao = mock(CVCImmunizationDao.class);
        LoggedInInfo sessionInfo = mock(LoggedInInfo.class);

        when(securityInfoManager.hasPrivilege(
                eq(sessionInfo), eq("_prevention"), eq("w"), nullable(String.class)))
                .thenReturn(true);

        try (MockedStatic<ServletActionContext> servletContext =
                     mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class)) {
            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                    any(HttpServletRequest.class))).thenReturn(sessionInfo);
            wireBeans(springUtils, securityInfoManager, demographicDao, partialDateDao,
                    consentDao, immunizationDao);

            PreventionDisplayConfig displayConfig = mock(PreventionDisplayConfig.class);
            when(displayConfig.getPrevention(PREVENTION_TYPE)).thenReturn(new HashMap<>());

            try (MockedStatic<PreventionDisplayConfig> preventionDisplayConfig =
                         mockStatic(PreventionDisplayConfig.class);
                 MockedStatic<PreventionData> preventionData = mockStatic(PreventionData.class)) {
                preventionDisplayConfig.when(PreventionDisplayConfig::getInstance)
                        .thenReturn(displayConfig);

                String result = new AddPrevention2Action().execute();

                assertThat(result).isEqualTo("form");
                assertThat(request.getAttribute("errors"))
                        .isEqualTo(List.of("Invalid or missing demographic_no"));
                verifyNoInteractions(demographicDao);
                preventionData.verifyNoInteractions();
                verifyNoInteractions(consentDao, immunizationDao);
            }
        }
    }

    private static void wireBeans(MockedStatic<SpringUtils> springUtils,
            SecurityInfoManager securityInfoManager, DemographicDao demographicDao,
            PartialDateDao partialDateDao, ConsentDao consentDao,
            CVCImmunizationDao immunizationDao) {
        springUtils.when(() -> SpringUtils.getBean(any(Class.class)))
                .thenAnswer(invocation -> {
                    Class<?> beanType = invocation.getArgument(0);
                    if (beanType.equals(SecurityInfoManager.class)) {
                        return securityInfoManager;
                    }
                    if (beanType.equals(DemographicDao.class)) {
                        return demographicDao;
                    }
                    if (beanType.equals(PartialDateDao.class)) {
                        return partialDateDao;
                    }
                    if (beanType.equals(ConsentDao.class)) {
                        return consentDao;
                    }
                    if (beanType.equals(CVCImmunizationDao.class)) {
                        return immunizationDao;
                    }
                    return mock(beanType);
                });
    }
}
