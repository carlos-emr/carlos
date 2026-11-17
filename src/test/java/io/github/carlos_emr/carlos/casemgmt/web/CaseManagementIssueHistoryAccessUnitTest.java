/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import java.util.Date;
import java.util.List;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises {@code CaseManagementEntry?method=issuehistory}: a provider whose role or provider
 * number carries a patient-specific record lock must not see that patient's issue-linked notes,
 * name or issue titles, while an unlocked patient's issue history renders exactly as before.
 */
@Tag("unit")
@Tag("security")
class CaseManagementIssueHistoryAccessUnitTest extends CarlosUnitTestBase {
    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo user;
    private CaseManagementNote note;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest("GET", "/CaseManagementEntry");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        request.getSession().setAttribute("userrole", "doctor");
        request.setParameter("method", "issuehistory");
        request.setParameter("demographicNo", "101");
        request.setParameter("issueIds", "31");
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
        security = mock(SecurityInfoManager.class);
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("101"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(true);

        Date saved = new Date(1_700_000_000_000L);
        note = new CaseManagementNote();
        note.setDemographic_no("101");
        note.setUuid("fake-uuid-1");
        note.setNote("FAKE-ISSUE-NOTE");
        note.setUpdate_date(saved);
        CaseManagementNote latest = new CaseManagementNote();
        latest.setUuid("fake-uuid-1");
        latest.setUpdate_date(saved);
        Issue issue = new Issue();
        issue.setDescription("FAKE-ISSUE-TITLE");
        when(manager.getDemoName("101")).thenReturn("FAKE-PATIENT-NAME");
        when(manager.getIssueHistory("31", "101")).thenReturn(List.of(note));
        when(manager.getMostRecentNote("fake-uuid-1")).thenReturn(latest);
        when(manager.getIssue("31")).thenReturn(issue);
    }

    private void assertRefused() {
        assertThat(action.issuehistory()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(request.getAttribute("history")).isNull();
        assertThat(request.getAttribute("current")).isNull();
        assertThat(request.getAttribute("title")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
    }

    @Test
    void shouldRenderIssueHistory_whenPatientIsUnlocked() {
        assertThat(action.issuehistory()).isEqualTo("showHistory");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("history")).isEqualTo(List.of(note));
        assertThat(request.getAttribute("current")).isEqualTo(List.of(true));
        assertThat(request.getAttribute("demoName")).isEqualTo("FAKE-PATIENT-NAME");
        assertThat((String) request.getAttribute("title")).startsWith("FAKE-ISSUE-TITLE ");
        verify(security).hasPrivilege(user, "_demographic", "r", "101");
        verify(security).hasPrivilege(user, "_casemgmt.notes", "r", "101");
        verify(security).isAllowedAccessToPatientRecord(user, 101);
        verify(manager).getIssueHistory("31", "101");
    }

    @Test
    void shouldRefuseIssueHistory_whenPatientRecordIsLocked() {
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);

        assertRefused();
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_casemgmt.notes"})
    void shouldRefuseIssueHistory_whenPatientReadPrivilegeIsDenied(String object) {
        when(security.hasPrivilege(user, object, "r", "101")).thenReturn(false);

        assertRefused();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseIssueHistory_whenPatientIsOutsideDomainAndHasNoReferral() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);

        assertRefused();
        verify(manager).isClientReferredInProgramDomain("4249", "101");
        verify(manager, never()).getIssueHistory(anyString(), anyString());
        verify(manager, never()).getDemoName(anyString());
        verify(manager, never()).getIssue(anyString());
    }

    @Test
    void shouldRenderIssueHistory_whenPatientIsReferredIntoProviderDomain() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        when(manager.isClientReferredInProgramDomain("4249", "101")).thenReturn(true);

        assertThat(action.issuehistory()).isEqualTo("showHistory");
        assertThat(request.getAttribute("history")).isEqualTo(List.of(note));
    }

    @Test
    void shouldRefuseIssueHistory_whenSessionHasNoLoggedInProvider() {
        request.getSession().removeAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY");

        assertRefused();
        verifyNoInteractions(manager, security);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "0", "-1", "undefined", "2147483648", "999999999999999999999"})
    void shouldRefuseIssueHistory_whenPatientIdentifierIsInvalid(String id) {
        if (id == null) request.removeParameter("demographicNo");
        else request.setParameter("demographicNo", id);

        assertRefused();
        verifyNoInteractions(manager);
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test
    void shouldCheckCanonicalPatient_whenIdentifierHasLeadingZeros() {
        request.setParameter("demographicNo", "000101");

        assertThat(action.issuehistory()).isEqualTo("showHistory");
        verify(security).hasPrivilege(user, "_demographic", "r", "101");
        verify(security).isAllowedAccessToPatientRecord(user, 101);
        verify(manager).getIssueHistory("31", "101");
    }

    @Test
    void shouldRefuseLeadingZeroIdentifier_whenCanonicalPatientIsLocked() {
        request.setParameter("demographicNo", "000101");
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);

        assertRefused();
        verifyNoInteractions(manager);
    }
}
