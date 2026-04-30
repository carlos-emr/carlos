/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCodeUpdateViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodePersister;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeUpdateViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingCodeUpdate2Action}.
 *
 * <p>Pins the persister-before-assembler ordering. Commit {@code c79814781b}
 * removed the DAO write from {@link BillingCodeUpdateViewModelAssembler}
 * and moved it to {@link ServiceCodePersister#updateDescriptionByServiceCode};
 * the action must invoke the persister BEFORE the assembler so that an
 * audit failure (DAO outage, malformed input) propagates instead of the
 * operator seeing a successful page render with the description silently
 * dropped.</p>
 */
@DisplayName("BillingCodeUpdate2Action")
@Tag("unit")
@Tag("billing")
class BillingCodeUpdate2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private BillingCodeUpdateViewModelAssembler mockAssembler;

    @Mock
    private ServiceCodePersister mockPersister;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private static final BillingCodeUpdateViewModel STUB_MODEL =
            BillingCodeUpdateViewModel.builder().build();

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockAssembler.assemble(any(), any())).thenReturn(STUB_MODEL);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private BillingCodeUpdate2Action newAction() {
        return new BillingCodeUpdate2Action(mockSecurityInfoManager, mockAssembler, mockPersister);
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldReturn405_onGet() throws Exception {
        mockRequest.setMethod("GET");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        // Persist + assemble must not run on a rejected GET.
        verify(mockPersister, never()).updateDescriptionByServiceCode(anyString(), anyString());
        verify(mockAssembler, never()).assemble(any(), any());
    }

    /**
     * Regression armor: a future refactor that swaps the order of the two
     * lines in {@code execute()} would let the assembler render a "saved!"
     * confirmation page even when the persister was about to throw. Pin the
     * ordering with {@code InOrder} so the swap fails this test loudly.
     */
    @Test
    void shouldRunPersister_beforeAssembler_onUpdateBranch() throws Exception {
        // The "update <5char-code>" branch reads the trailing 5 chars as
        // the service code, then reads request.getParameter(code) for the
        // new description. Both must be present for the persister to fire.
        mockRequest.setParameter("update", "updateA001A");
        mockRequest.setParameter("A001A", "Minor consult — revised");

        newAction().execute();

        InOrder ordering = inOrder(mockPersister, mockAssembler);
        ordering.verify(mockPersister).updateDescriptionByServiceCode("A001A", "Minor consult — revised");
        ordering.verify(mockAssembler).assemble(any(), any());
    }

    @Test
    void shouldSkipPersister_onConfirmBranch() throws Exception {
        // The Confirm branch is read-only — the assembler still runs but
        // no description write should happen.
        mockRequest.setParameter("update", "Confirm");

        newAction().execute();

        verify(mockPersister, never()).updateDescriptionByServiceCode(anyString(), anyString());
        verify(mockAssembler).assemble(any(), any());
    }

    @Test
    void shouldSkipPersister_whenUpdateParamMissing() throws Exception {
        // No "update" param at all → render-only flow.
        newAction().execute();

        verify(mockPersister, never()).updateDescriptionByServiceCode(anyString(), anyString());
        verify(mockAssembler).assemble(any(), any());
    }

    @Test
    void shouldExposeViewModel_asRequestAttribute() throws Exception {
        newAction().execute();

        assertThat(mockRequest.getAttribute("codeUpdateModel")).isSameAs(STUB_MODEL);
    }
}
