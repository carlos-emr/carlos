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
package io.github.carlos_emr.carlos.webserv.rest.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@DisplayName("AuthenticationInInterceptor authorization audit")
@Tag("unit")
@Tag("security")
class AuthenticationInInterceptorUnitTest extends CarlosUnitTestBase {

    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
    }

    @Test
    @DisplayName("should log only consumer key when rejecting unauthorized requests")
    void shouldLogOnlyConsumerKey_whenRejectingUnauthorizedRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/rest/patient");
        request.setRemoteAddr("127.0.0.1");
        request.addParameter("oauth_consumer_key", "client\r\nforged");
        request.addParameter("demographicNo", "12345");
        request.addParameter("appointmentNo", "67890");

        Message message = mock(Message.class);
        Exchange exchange = mock(Exchange.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);
        when(message.get(Message.QUERY_STRING)).thenReturn(null);
        when(message.get(AbstractHTTPDestination.HTTP_REQUEST)).thenReturn(request);
        when(message.getExchange()).thenReturn(exchange);

        new AuthenticationInInterceptor().handleMessage(message);

        ArgumentCaptor<OscarLog> auditLogCaptor = ArgumentCaptor.forClass(OscarLog.class);
        logActionMock.verify(() -> LogAction.addLogSynchronous(auditLogCaptor.capture()));
        OscarLog auditLog = auditLogCaptor.getValue();
        assertThat(auditLog.getAction()).isEqualTo("REST WS: NOT AUTHORIZED");
        assertThat(auditLog.getIp()).isEqualTo("127.0.0.1");
        assertThat(auditLog.getContent()).isEqualTo("http://localhost/ws/rest/patient");
        String escapedConsumerKey = "client\\r\\nforged";
        assertThat(auditLog.getData()).isEqualTo("consumer_key=" + escapedConsumerKey);
        assertThat(auditLog.getData()).doesNotContain("demographicNo", "12345", "appointmentNo", "67890");

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(exchange).put(eq(Response.class), responseCaptor.capture());
        Response response = responseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("<error>Not authorized</error>");
    }
}
