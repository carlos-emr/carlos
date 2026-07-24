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
package io.github.carlos_emr.carlos.providers.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProEditPhoneNum2Action} server-side fax-number validation.
 *
 * <p>The browser-side validation is bypassable via a direct POST, so the action constrains the
 * value to telephone punctuation before persisting it (and later rendering it back into the page).
 * A value containing markup is rejected: the action sets the {@code phoneError} request attribute
 * and returns without ever calling {@link UserPropertyDAO#saveProp}. A well-formed value persists.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("ProEditPhoneNum2Action server-side fax-number validation")
class ProEditPhoneNum2ActionUnitTest {

    private static final String PROVIDER_NO = "999998";

    @Test
    @DisplayName("should set phoneError and never persist when POST fax number contains markup")
    void shouldSetPhoneError_whenPostFaxNumberContainsMarkup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        UserPropertyDAO propertyDao = mock(UserPropertyDAO.class);

        try (MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class)) {

            servletCtx.when(ServletActionContext::getRequest).thenReturn(request);
            servletCtx.when(ServletActionContext::getResponse).thenReturn(response);
            wireBeans(springUtils, securityInfoManager, propertyDao);
            LoggedInInfo sessionInfo = grantSession(loggedInInfo, securityInfoManager);

            ProEditPhoneNum2Action action = new ProEditPhoneNum2Action();
            action.setFaxNumber("<script>alert(1)</script>");

            String result = action.execute();

            // Rejected before any write: markup fails the telephone-punctuation constraint.
            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("phoneError")).isEqualTo(Boolean.TRUE);
            verify(propertyDao, never()).saveProp(any(UserProperty.class));
        }
    }

    @Test
    @DisplayName("should persist the property when POST fax number is a valid telephone value")
    void shouldPersistProperty_whenPostFaxNumberIsValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        UserPropertyDAO propertyDao = mock(UserPropertyDAO.class);
        // No existing rxPhone property: the action creates and saves a new one.
        when(propertyDao.getProp(PROVIDER_NO, "rxPhone")).thenReturn(null);

        try (MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class)) {

            servletCtx.when(ServletActionContext::getRequest).thenReturn(request);
            servletCtx.when(ServletActionContext::getResponse).thenReturn(response);
            wireBeans(springUtils, securityInfoManager, propertyDao);
            grantSession(loggedInInfo, securityInfoManager);

            ProEditPhoneNum2Action action = new ProEditPhoneNum2Action();
            action.setFaxNumber("(416) 555-0100 x12");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("phoneError")).isNull();
            assertThat(request.getAttribute("status")).isEqualTo("complete");
            verify(propertyDao).saveProp(any(UserProperty.class));
        }
    }

    private static void wireBeans(MockedStatic<SpringUtils> springUtils,
            SecurityInfoManager securityInfoManager, UserPropertyDAO propertyDao) {
        springUtils.when(() -> SpringUtils.getBean(any(Class.class)))
                .thenAnswer(inv -> {
                    Class<?> beanType = inv.getArgument(0);
                    if (beanType.equals(SecurityInfoManager.class)) {
                        return securityInfoManager;
                    }
                    if (beanType.equals(UserPropertyDAO.class)) {
                        return propertyDao;
                    }
                    return mock(beanType);
                });
    }

    private static LoggedInInfo grantSession(MockedStatic<LoggedInInfo> loggedInInfo,
            SecurityInfoManager securityInfoManager) {
        LoggedInInfo sessionInfo = mock(LoggedInInfo.class);
        when(sessionInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(sessionInfo);
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_pref"), eq("w"), nullable(String.class)))
                .thenReturn(true);
        return sessionInfo;
    }
}
