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
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for shared REST service request context handling.
 */
@DisplayName("REST AbstractServiceImpl")
@Tag("unit")
class AbstractServiceImplUnitTest {

    @Test
    @DisplayName("should use request scoped login info when session info is missing")
    void shouldUseRequestScopedLoginInfo_whenSessionInfoMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoggedInInfo expected = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoRequest(request, expected);

        LoggedInInfo actual = resolveCurrentLoggedInInfo(request);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    @DisplayName("should prefer request scoped login info when session provider is missing")
    void shouldPreferRequestScopedLoginInfo_whenSessionProviderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoggedInInfo sessionInfo = new LoggedInInfo();
        LoggedInInfo requestInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), sessionInfo);
        LoggedInInfo.setLoggedInInfoIntoRequest(request, requestInfo);

        LoggedInInfo actual = resolveCurrentLoggedInInfo(request);

        assertThat(actual).isSameAs(requestInfo);
    }

    private static LoggedInInfo resolveCurrentLoggedInInfo(MockHttpServletRequest request) {
        Message message = new MessageImpl();
        message.put(AbstractHTTPDestination.HTTP_REQUEST, request);
        PhaseInterceptorChain chain = new PhaseInterceptorChain(phases());
        AtomicReference<LoggedInInfo> resolved = new AtomicReference<>();
        chain.add(new AbstractPhaseInterceptor<Message>(Phase.RECEIVE) {
            @Override
            public void handleMessage(Message message) {
                resolved.set(new TestService().currentLoggedInInfo());
            }
        });
        chain.doIntercept(message);
        return resolved.get();
    }

    private static SortedSet<Phase> phases() {
        SortedSet<Phase> phases = new TreeSet<>();
        phases.add(new Phase(Phase.RECEIVE, 1000));
        return phases;
    }

    private static final class TestService extends AbstractServiceImpl {
        private LoggedInInfo currentLoggedInInfo() {
            return getLoggedInInfo();
        }
    }
}
