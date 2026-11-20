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
package io.github.carlos_emr.carlos.www;

import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.daos.DefaultIssueDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.DefaultIssue;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Default Encounter Issue assigns and removes exactly the issues whose codes are entered: the
 * earlier text search matched a code anywhere in any code or description, so ICSW100 also took
 * ICSW1000 (issue #4855).
 */
@Tag("unit")
@Tag("admin")
@DisplayName("Default Encounter Issue matches whole codes")
class DefaultEncounterIssue2ActionUnitTest extends CarlosUnitTestBase {

    private static final long ICSW100 = 47L;
    private static final long ICSW1000 = 64L;
    private static final long OMEDS = 12L;

    private IssueDAO issues;
    private DefaultIssueDao defaults;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfo;

    @BeforeEach
    void setUp() {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
        issues = createAndRegisterMock(IssueDAO.class);
        defaults = createAndRegisterMock(DefaultIssueDao.class);
        when(issues.findIssueByCode(aryEq(new String[]{"ICSW100"}))).thenReturn(List.of(issue(ICSW100)));
        when(issues.findIssueByCode(aryEq(new String[]{"ICSW1000"}))).thenReturn(List.of(issue(ICSW1000)));
        when(issues.findIssueByCode(aryEq(new String[]{"OMeds"}))).thenReturn(List.of(issue(OMEDS)));

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        loggedInInfo = mockStatic(LoggedInInfo.class);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
    }

    @AfterEach
    void tearDown() {
        // LogAction is silenced by CarlosUnitTestBase.
        if (loggedInInfo != null) {
            loggedInInfo.close();
        }
        if (servletActionContext != null) {
            servletActionContext.close();
        }
    }

    private static Issue issue(long id) {
        Issue issue = new Issue();
        issue.setId(id);
        return issue;
    }

    private DefaultEncounterIssue2Action actionFor(String method, String issueNames) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/DefaultEncounterIssue");
        request.setParameter("method", method);
        request.setParameter("issueNames", issueNames);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        return new DefaultEncounterIssue2Action();
    }

    private static String[] sortedIds(String issueIds) {
        String[] ids = issueIds.split(",");
        Arrays.sort(ids);
        return ids;
    }

    @Test
    void shouldAssignExactlyTheIssuesOfTheCodesEntered_whenSaving() {
        actionFor("save", "ICSW100, OMeds ,NOPE").execute();

        ArgumentCaptor<DefaultIssue> saved = ArgumentCaptor.forClass(DefaultIssue.class);
        verify(defaults).saveDefaultIssue(saved.capture());
        assertThat(sortedIds(saved.getValue().getIssueIds())).containsExactly("12", "47");
        verify(issues, never()).findIssueBySearch(anyString());
    }

    @Test
    void shouldAssignEveryIssueWithTheCode_whenTwoIssueTypesShareIt() {
        // issue.code is not unique: an exact code can name issues of more than one type.
        when(issues.findIssueByCode(aryEq(new String[]{"SHARED"}))).thenReturn(List.of(issue(101L), issue(102L)));

        actionFor("save", "SHARED").execute();

        ArgumentCaptor<DefaultIssue> saved = ArgumentCaptor.forClass(DefaultIssue.class);
        verify(defaults).saveDefaultIssue(saved.capture());
        assertThat(sortedIds(saved.getValue().getIssueIds())).containsExactly("101", "102");
    }

    @Test
    void shouldSaveNothing_whenNoEnteredCodeIsAnIssueCode() {
        actionFor("save", "NOPE, ICSW10").execute();

        verify(defaults, never()).saveDefaultIssue(any());
        verify(issues, never()).findIssueBySearch(anyString());
    }

    @Test
    void shouldRemoveOnlyTheIssueOfTheCodeEntered_whenRemoving() {
        DefaultIssue current = new DefaultIssue();
        current.setIssueIds(ICSW100 + "," + ICSW1000 + "," + OMEDS);
        when(defaults.getLastestDefaultIssue()).thenReturn(current);

        actionFor("remove", "ICSW100").execute();

        ArgumentCaptor<DefaultIssue> saved = ArgumentCaptor.forClass(DefaultIssue.class);
        verify(defaults).saveDefaultIssue(saved.capture());
        assertThat(saved.getValue().getIssueIds()).isEqualTo(ICSW1000 + "," + OMEDS);
        verify(issues, never()).findIssueBySearch(anyString());
    }
}
