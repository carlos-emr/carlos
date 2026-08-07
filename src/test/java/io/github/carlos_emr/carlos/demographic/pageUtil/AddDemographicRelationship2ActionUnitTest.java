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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.dao.RelationshipsDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddDemographicRelationship2Action}'s GET/POST mutation gate.
 *
 * <p>The "Add Relation" popup (edit-view.jsp) opens this action with a plain GET carrying only
 * {@code demo} to render the contact-search form. {@code linkingDemo}/{@code relation} are only
 * present once the form is actually submitted. The action must render on the parameter-less GET
 * without persisting anything, must reject a GET that carries save data (linkingDemo + relation)
 * with 405, and must persist only on a genuine POST save. See issue #3352.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("AddDemographicRelationship2Action GET/POST mutation gate")
class AddDemographicRelationship2ActionUnitTest {

    @Test
    @DisplayName("should render contact-search form and never persist when GET has no mutation params")
    void shouldRenderContactSearchForm_whenGetWithoutMutationParams() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("demo", "1373");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        RelationshipsDao relationshipsDao = mock(RelationshipsDao.class);
        CtlRelationshipsDao ctlRelationshipsDao = mock(CtlRelationshipsDao.class);

        try (MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class)) {

            servletCtx.when(ServletActionContext::getRequest).thenReturn(request);
            servletCtx.when(ServletActionContext::getResponse).thenReturn(response);
            wireBeans(springUtils, securityInfoManager, relationshipsDao, ctlRelationshipsDao);
            grantSession(loggedInInfo, securityInfoManager);

            AddDemographicRelationship2Action action = new AddDemographicRelationship2Action();

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verifyNoInteractions(relationshipsDao);
            verifyNoInteractions(ctlRelationshipsDao);
        }
    }

    @Test
    @DisplayName("should reject GET carrying linkingDemo+relation mutation intent with 405 and never persist")
    void shouldReject405_whenGetCarriesLinkingDemoAndRelationMutationIntent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        // A GET carrying save data is the CSRF-via-GET attempt the gate must stop.
        request.setParameter("origDemo", "1373");
        request.setParameter("linkingDemo", "1374");
        request.setParameter("relation", "Spouse");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        RelationshipsDao relationshipsDao = mock(RelationshipsDao.class);
        CtlRelationshipsDao ctlRelationshipsDao = mock(CtlRelationshipsDao.class);

        try (MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class)) {

            servletCtx.when(ServletActionContext::getRequest).thenReturn(request);
            servletCtx.when(ServletActionContext::getResponse).thenReturn(response);
            wireBeans(springUtils, securityInfoManager, relationshipsDao, ctlRelationshipsDao);
            grantSession(loggedInInfo, securityInfoManager);

            AddDemographicRelationship2Action action = new AddDemographicRelationship2Action();

            String result = action.execute();

            // Rejected with 405 before any persist — the method gate runs ahead of the DAO calls.
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(relationshipsDao);
            verifyNoInteractions(ctlRelationshipsDao);
        }
    }

    @Test
    @DisplayName("should return pmmClient and never persist when GET carries the Finished param")
    void shouldReturnPmmClient_whenGetCarriesFinishedParam() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("origDemo", "1373");
        request.setParameter("pmmClient", "Finished");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        RelationshipsDao relationshipsDao = mock(RelationshipsDao.class);
        CtlRelationshipsDao ctlRelationshipsDao = mock(CtlRelationshipsDao.class);

        try (MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class)) {

            servletCtx.when(ServletActionContext::getRequest).thenReturn(request);
            servletCtx.when(ServletActionContext::getResponse).thenReturn(response);
            wireBeans(springUtils, securityInfoManager, relationshipsDao, ctlRelationshipsDao);
            grantSession(loggedInInfo, securityInfoManager);

            AddDemographicRelationship2Action action = new AddDemographicRelationship2Action();

            String result = action.execute();

            assertThat(result).isEqualTo("pmmClient");
            verifyNoInteractions(relationshipsDao);
            verifyNoInteractions(ctlRelationshipsDao);
        }
    }

    @Test
    @DisplayName("should persist the relationship when POST carries linkingDemo and relation")
    void shouldPersistRelationship_whenPostCarriesLinkingDemoAndRelation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("origDemo", "1373");
        request.setParameter("linkingDemo", "1374");
        request.setParameter("relation", "Spouse");
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        RelationshipsDao relationshipsDao = mock(RelationshipsDao.class);
        CtlRelationshipsDao ctlRelationshipsDao = mock(CtlRelationshipsDao.class);
        // No configured inverse relation: the inverse-linking branch is skipped cleanly.
        when(ctlRelationshipsDao.findByValue("Spouse")).thenReturn(null);

        try (MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class)) {

            servletCtx.when(ServletActionContext::getRequest).thenReturn(request);
            servletCtx.when(ServletActionContext::getResponse).thenReturn(response);
            wireBeans(springUtils, securityInfoManager, relationshipsDao, ctlRelationshipsDao);
            grantSession(loggedInInfo, securityInfoManager);

            AddDemographicRelationship2Action action = new AddDemographicRelationship2Action();

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(relationshipsDao).persist(any());
        }
    }

    private static void wireBeans(MockedStatic<SpringUtils> springUtils,
            SecurityInfoManager securityInfoManager, RelationshipsDao relationshipsDao,
            CtlRelationshipsDao ctlRelationshipsDao) {
        springUtils.when(() -> SpringUtils.getBean(any(Class.class)))
                .thenAnswer(inv -> {
                    Class<?> beanType = inv.getArgument(0);
                    if (beanType.equals(SecurityInfoManager.class)) {
                        return securityInfoManager;
                    }
                    if (beanType.equals(RelationshipsDao.class)) {
                        return relationshipsDao;
                    }
                    if (beanType.equals(CtlRelationshipsDao.class)) {
                        return ctlRelationshipsDao;
                    }
                    if (beanType.equals(DemographicManager.class)) {
                        return mock(DemographicManager.class);
                    }
                    return mock(beanType);
                });
    }

    private static void grantSession(MockedStatic<LoggedInInfo> loggedInInfo,
            SecurityInfoManager securityInfoManager) {
        LoggedInInfo sessionInfo = mock(LoggedInInfo.class);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(sessionInfo);
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("w"), nullable(String.class)))
                .thenReturn(true);
    }
}
