/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RxSessionFilterUnitTest {

    private static final String CONTEXT_PATH = "/carlos";
    private final RxSessionFilter filter = new RxSessionFilter();

    @Test
    void entryRequestCreatesWorkspaceAndRedirectsWithOpaqueContext() throws Exception {
        MockHttpSession session = session("provider-1");
        MockHttpServletRequest request = request(session, "GET", "/rx/choosePatient");
        request.addParameter("demographicNo", "101");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = {false};

        filter.doFilter(request, response, (req, res) -> invoked[0] = true);

        assertThat(invoked[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(303);
        String contextId = parameter(response.getHeader("Location"), "rxContextId");
        assertThat(contextId).isNotBlank();
        RxWorkspaceRegistry.RxWorkspace workspace = RxWorkspaceRegistry.get(session).find(contextId);
        assertThat(workspace.getDemographicNo()).isEqualTo(101);
        assertThat(workspace.getProviderNo()).isEqualTo("provider-1");
        assertThat(session.getAttribute("RxSessionBean")).isNull();
    }

    @Test
    void twoTabsForSamePatientHaveIndependentLegacyState() {
        MockHttpSession session = session("provider-1");
        RxWorkspaceRegistry registry = RxWorkspaceRegistry.getOrCreate(session);
        RxWorkspaceRegistry.RxWorkspace first = registry.create(101, "provider-1");
        RxWorkspaceRegistry.RxWorkspace second = registry.create(101, "provider-1");
        HttpSession firstFacade = new RxContextSession(session, first);
        HttpSession secondFacade = new RxContextSession(session, second);

        firstFacade.setAttribute("comment", "first-tab");
        firstFacade.setAttribute("RX_ADDR", "4");

        assertThat(secondFacade.getAttribute("comment")).isNull();
        assertThat(secondFacade.getAttribute("RX_ADDR")).isNull();
        assertThat(firstFacade.getAttribute("RxSessionBean"))
                .isNotSameAs(secondFacade.getAttribute("RxSessionBean"));
        assertThat(session.getAttribute("comment")).isNull();
        assertThat(Collections.list(firstFacade.getAttributeNames()))
                .contains("comment", "RX_ADDR", "RxSessionBean", "demographicNo");
    }

    @Test
    void requestUsesOnlyItsWorkspaceAndLeavesRealSessionUntouched() throws Exception {
        MockHttpSession session = session("provider-1");
        RxWorkspaceRegistry registry = RxWorkspaceRegistry.getOrCreate(session);
        RxWorkspaceRegistry.RxWorkspace workspace = registry.create(101, "provider-1");
        MockHttpServletRequest request = request(session, "POST", "/rx/stash");
        request.addParameter("rxContextId", workspace.getContextId());
        request.addParameter("demographicNo", "101");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            HttpSession scoped = ((HttpServletRequest) req).getSession();
            scoped.setAttribute("comment", "isolated");
            scoped.setAttribute("ordinary", "shared");
        });

        assertThat(workspace.getAttribute("comment")).isEqualTo("isolated");
        assertThat(session.getAttribute("comment")).isNull();
        assertThat(session.getAttribute("ordinary")).isEqualTo("shared");
    }

    @Test
    void mismatchedDemographicIsRejectedBeforeAction() throws Exception {
        MockHttpSession session = session("provider-1");
        RxWorkspaceRegistry.RxWorkspace workspace =
                RxWorkspaceRegistry.getOrCreate(session).create(101, "provider-1");
        MockHttpServletRequest request = request(session, "POST", "/rx/WriteScript");
        request.addParameter("rxContextId", workspace.getContextId());
        request.addParameter("demographicNo", "202");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = {false};

        filter.doFilter(request, response, (req, res) -> invoked[0] = true);

        assertThat(invoked[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("rx_context_demographic_mismatch");
    }

    @Test
    void serverRedirectPreservesContext() throws Exception {
        MockHttpSession session = session("provider-1");
        RxWorkspaceRegistry.RxWorkspace workspace =
                RxWorkspaceRegistry.getOrCreate(session).create(101, "provider-1");
        MockHttpServletRequest request = request(session, "POST", "/rx/addAllergy");
        request.addParameter("rxContextId", workspace.getContextId());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res)
                .sendRedirect(CONTEXT_PATH + "/rx/showAllergy?demographicNo=101"));

        assertThat(response.getRedirectedUrl())
                .contains("rxContextId=" + workspace.getContextId());
    }

    @Test
    void differentWorkspacesCanRunConcurrently() throws Exception {
        MockHttpSession session = session("provider-1");
        RxWorkspaceRegistry registry = RxWorkspaceRegistry.getOrCreate(session);
        RxWorkspaceRegistry.RxWorkspace first = registry.create(101, "provider-1");
        RxWorkspaceRegistry.RxWorkspace second = registry.create(202, "provider-1");
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        FilterChain chain = (req, res) -> {
            RxSessionBean bean = (RxSessionBean) ((HttpServletRequest) req)
                    .getSession().getAttribute("RxSessionBean");
            seen.add(bean.getDemographicNo());
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread firstThread = new Thread(() -> filterUnchecked(session, first, chain));
        Thread secondThread = new Thread(() -> filterUnchecked(session, second, chain));
        firstThread.start();
        secondThread.start();

        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        firstThread.join(2000);
        secondThread.join(2000);
        assertThat(seen).containsExactlyInAnyOrder(101, 202);
    }

    private void filterUnchecked(
            MockHttpSession session, RxWorkspaceRegistry.RxWorkspace workspace, FilterChain chain) {
        try {
            MockHttpServletRequest request = request(session, "POST", "/rx/stash");
            request.addHeader("X-Rx-Context", workspace.getContextId());
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static MockHttpSession session(String providerNo) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", providerNo);
        return session;
    }

    private static MockHttpServletRequest request(
            MockHttpSession session, String method, String route) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, CONTEXT_PATH + route);
        request.setContextPath(CONTEXT_PATH);
        request.setSession(session);
        return request;
    }

    private static String parameter(String url, String name) {
        String query = url.substring(url.indexOf('?') + 1);
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair[0].equals(name)) {
                return pair.length == 2 ? pair[1] : "";
            }
        }
        return null;
    }
}
