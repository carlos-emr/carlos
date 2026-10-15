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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused method-contract test for {@link CaseManagementEntry2Action}: every {@code method} that
 * changes a note, an issue, a draft or a note lock accepts only POST, and the chart's read paths
 * stay GET. This is the dedicated test that {@code MutatorActionGetRejectionContractUnitTest}
 * requires for a conditional mutator.
 */
@Tag("unit")
@Tag("security")
@DisplayName("CaseManagementEntry2Action save methods accept only POST")
class CaseManagementEntry2ActionPostOnlyUnitTest {

    private static final String PROVIDER_NO = "999998";

    static Stream<Arguments> postOnlyMethodsWithUnsafeVerbs() {
        return CaseManagementEntry2Action.POST_ONLY_METHODS.stream()
                .sorted()
                .flatMap(method -> Stream.of(Arguments.of(method, "GET"), Arguments.of(method, "HEAD")));
    }

    @ParameterizedTest(name = "{1} method={0} is refused with 405")
    @MethodSource("postOnlyMethodsWithUnsafeVerbs")
    void shouldReturn405_whenSaveMethodIsNotPost(String method, String httpMethod) throws Exception {
        Outcome outcome = run(httpMethod, request -> request.addParameter("method", method));

        assertThat(outcome.result).isEqualTo(ActionSupport.NONE);
        assertThat(outcome.response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(outcome.response.getHeader("Allow")).isEqualTo("POST");
        outcome.beans.values().forEach(Mockito::verifyNoInteractions);
    }

    @Test
    void shouldReturn405_whenSaveMethodArrivesAsForwardedRequestAttribute() throws Exception {
        Outcome outcome = run("GET", request -> request.setAttribute("method", "save"));

        assertThat(outcome.result).isEqualTo(ActionSupport.NONE);
        assertThat(outcome.response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        outcome.beans.values().forEach(Mockito::verifyNoInteractions);
    }

    @Test
    void shouldListEveryWriteMethod_forPostOnlySet() {
        assertThat(CaseManagementEntry2Action.POST_ONLY_METHODS).containsExactlyInAnyOrder(
                "save", "saveAndExit", "autosave", "cancel", "ajaxsave",
                "issueNoteSave", "issueNoteSaveJson", "ticklerSaveNote",
                "isNoteEdited", "updateNoteLock", "releaseNoteLock",
                "makeIssue", "issueAdd", "submitChangeDiagnosis", "ajaxChangeDiagnosis",
                "issueChange", "issueDelete");
    }

    @ParameterizedTest(name = "method={0} stays open to GET")
    @ValueSource(strings = {
            "edit", "restore", "setUpMainEncounter", "issueList", "notehistory", "issuehistory",
            "history", "displayNotes", "print", "ticklerGetNote", "changeDiagnosis", "exit",
            "addNewIssue", "issueSearch", "cleanup"})
    void shouldKeepReadMethodOpen_toGet(String method) {
        assertThat(CaseManagementEntry2Action.POST_ONLY_METHODS).doesNotContain(method);
    }

    @Test
    void shouldReleaseTheNoteLock_whenReleaseNoteLockIsPost() throws Exception {
        Outcome outcome = run("POST", request -> {
            request.addParameter("method", "releaseNoteLock");
            request.addParameter("demographicNo", "1");
            request.addParameter("noteId", "5");
            CasemgmtNoteLock lock = new CasemgmtNoteLock();
            lock.setNoteId(5L);
            lock.setSessionId(request.getSession().getId());
            request.getSession().setAttribute("casemgmtNoteLock1", lock);
        });

        assertThat(outcome.response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        CasemgmtNoteLockDao lockDao = (CasemgmtNoteLockDao) outcome.beans.get(CasemgmtNoteLockDao.class);
        verify(lockDao).removeForSession(eq(PROVIDER_NO), eq(1), eq(5L), anyString());
    }

    private static Outcome run(String httpMethod, Consumer<MockHttpServletRequest> customize) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(httpMethod);
        request.setContextPath("/carlos");
        customize.accept(request);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), any(String.class), any(String.class), nullable(String.class)))
                .thenReturn(true);
        Map<Class<?>, Object> beans = new HashMap<>();

        try (MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {

            servletCtx.when(ServletActionContext::getRequest).thenReturn(request);
            servletCtx.when(ServletActionContext::getResponse).thenReturn(response);

            LoggedInInfo sessionInfo = mock(LoggedInInfo.class);
            when(sessionInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
            loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(sessionInfo);

            springUtils.when(() -> SpringUtils.getBean(any(Class.class)))
                    .thenAnswer(inv -> {
                        Class<?> beanType = inv.getArgument(0);
                        if (beanType.equals(SecurityInfoManager.class)) {
                            return securityInfoManager;
                        }
                        return beans.computeIfAbsent(beanType, Mockito::mock);
                    });

            CaseManagementEntry2Action action = new CaseManagementEntry2Action();
            action.withSession(new HashMap<>());
            String result = action.execute();

            verify(securityInfoManager).hasPrivilege(sessionInfo, "_demographic", "r", null);
            return new Outcome(result, response, beans);
        }
    }

    private record Outcome(String result, MockHttpServletResponse response, Map<Class<?>, Object> beans) {
    }
}
