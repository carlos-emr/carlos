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
package io.github.carlos_emr.carlos.webserv;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.logging.log4j.Logger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Restricts a web-service endpoint to callers on the host loopback address.
 *
 * <p>Used to keep the unauthenticated {@code SystemInfoService} liveness endpoint
 * reachable by a local health check while refusing every off-host client — a
 * liveness probe cannot present a WS-Security token, so gating the endpoint on
 * authentication would break the very use it exists for. Restricting it to the
 * host itself removes the anonymous reconnaissance surface while preserving local
 * monitoring.
 *
 * <p>The Host-level {@code RemoteIpValve} runs before this interceptor and has
 * already replaced the peer address with the real client address from
 * {@code X-Forwarded-For}. A request that arrived through nginx therefore presents
 * the browser's public address here and is refused, while a local process calling
 * Tomcat directly on 127.0.0.1 presents the loopback address and is allowed. This
 * backs up the nginx rule that 404s the endpoint at the edge, so — as with
 * DrugRef — neither gate is load-bearing on its own.
 */
public class LoopbackOnlyInInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger logger = MiscUtils.getLogger();

    public LoopbackOnlyInInterceptor() {
        // RECEIVE is the earliest phase: reject an off-host caller before the
        // message body is read or unmarshalled.
        super(Phase.RECEIVE);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);
        if (request == null) {
            // Outbound message (there is no servlet request); nothing to gate.
            return;
        }
        if (isLoopback(request.getRemoteAddr())) {
            return;
        }
        logger.warn("Refused off-host request to a loopback-only web service from {}", request.getRemoteAddr());
        // Set the status explicitly: org.apache.cxf.interceptor.Fault defaults to
        // 500, which reads as an outage in monitoring rather than a deliberate
        // refusal. 403 says what actually happened, matching the deliberate
        // status mapping in AuthenticationInWSS4JInterceptor.
        Fault fault = new Fault(new SecurityException("loopback-only endpoint"));
        fault.setStatusCode(HttpServletResponse.SC_FORBIDDEN);
        throw fault;
    }

    /**
     * @param ip the {@code RemoteIpValve}-resolved client address
     * @return true when the address is an IPv4 or IPv6 host loopback address
     */
    private static boolean isLoopback(String ip) {
        if (ip == null) {
            return false;
        }
        return ip.startsWith("127.")
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("::ffff:127.");
    }
}
