/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.gate;

import io.github.carlos_emr.carlos.lab.ca.all.web.ManualLabSubmissionReceipt.Outcome;
import io.github.carlos_emr.carlos.lab.ca.all.web.ManualLabSubmissionReceipt;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The redirected view displays only its own recorded outcome after checking lab privileges. */
@Tag("unit")
@Tag("lab")
class ViewCreateLab2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> context;
    private SecurityInfoManager security;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/carlos/oscarMDS/ViewCreateLab");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), mock(LoggedInInfo.class));
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_lab"), eq("w"), isNull())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        context = mockStatic(ServletActionContext.class);
        context.when(ServletActionContext::getRequest).thenReturn(request);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void shouldDisplayRecordedOutcomeOnce_whenFollowingSubmissionRedirect(Outcome outcome) throws Exception {
        request.setParameter("submission", ManualLabSubmissionReceipt.save(request.getSession(), outcome));
        ViewCreateLab2Action action = view();
        assertThat(action.execute()).isEqualTo("success");
        if (outcome == Outcome.STORED) {
            assertThat(action.getActionMessages()).containsExactly("oscarMDS.createLab.submitSuccess");
            assertThat(action.getActionErrors()).isEmpty();
        } else if (outcome == Outcome.ALREADY_RECORDED) {
            assertThat(action.getActionErrors()).containsExactly("oscarMDS.createLab.submitDuplicate");
            assertThat(action.getActionMessages()).isEmpty();
        } else {
            assertThat(action.getActionErrors()).containsExactly("oscarMDS.createLab.submitUnknown");
            assertThat(action.getActionMessages()).isEmpty();
        }
        ViewCreateLab2Action reloaded = view();
        assertThat(reloaded.execute()).isEqualTo("success");
        assertThat(reloaded.getActionMessages()).isEmpty();
        assertThat(reloaded.getActionErrors()).isEmpty();
    }

    @Test
    void shouldNotInventSuccess_whenReceiptIsUnknown() throws Exception {
        request.setParameter("submission", "STORED");
        ViewCreateLab2Action action = view();
        assertThat(action.execute()).isEqualTo("success");
        assertThat(action.getActionMessages()).isEmpty();
        assertThat(action.getActionErrors()).isEmpty();
    }

    @Test
    void shouldKeepReceiptUndisclosed_whenLabPrivilegeIsDenied() {
        String id = ManualLabSubmissionReceipt.save(request.getSession(), Outcome.STORED);
        request.setParameter("submission", id);
        when(security.hasPrivilege(any(), eq("_lab"), eq("w"), isNull())).thenReturn(false);
        ViewCreateLab2Action action = view();
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        assertThat(ManualLabSubmissionReceipt.consume(request.getSession(), id)).isEqualTo(Outcome.STORED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEAD", "POST"})
    void shouldKeepNoticeForGet_whenOtherMethodVisitsView(String method) throws Exception {
        request.setMethod(method);
        String id = ManualLabSubmissionReceipt.save(request.getSession(), Outcome.STORED);
        request.setParameter("submission", id);
        ViewCreateLab2Action action = view();
        assertThat(action.execute()).isEqualTo("success");
        assertThat(action.getActionMessages()).isEmpty();
        assertThat(ManualLabSubmissionReceipt.consume(request.getSession(), id)).isEqualTo(Outcome.STORED);
    }

    private ViewCreateLab2Action view() {
        ViewCreateLab2Action action = spy(new ViewCreateLab2Action());
        doAnswer(invocation -> invocation.getArgument(0)).when(action).getText(anyString());
        return action;
    }
}
