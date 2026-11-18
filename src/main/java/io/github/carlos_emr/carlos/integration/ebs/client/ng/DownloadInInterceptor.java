/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import org.apache.cxf.message.Message;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Optional, payload-free EBS transport diagnostics.
 *
 * <p>The historical class name is retained for existing client configurations. SOAP bodies,
 * headers, message-map values and attachment identifiers can contain credentials or clinical
 * data, so this interceptor records only fixed transport events and a validated numeric HTTP
 * status. It never reads, copies or replaces a message stream, nor enumerates attachments:
 * even asking a lazy attachment collection for its size can consume transport data.</p>
 */
public class DownloadInInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOGGER = LogManager.getLogger(DownloadInInterceptor.class);
    private final boolean outbound;

    /** Creates the response diagnostic without taking ownership of payload or attachment streams. */
    public DownloadInInterceptor() {
        this(false);
    }

    private DownloadInInterceptor(boolean outbound) {
        super(outbound ? Phase.PRE_STREAM : Phase.POST_INVOKE);
        this.outbound = outbound;
    }

    /** Provides the corresponding request event without installing a CXF payload logger. */
    static DownloadInInterceptor outbound() {
        return new DownloadInInterceptor(true);
    }

    /** Records transport metadata only; payload failures remain owned by the transport consumer. */
    @Override
    public void handleMessage(Message message) throws Fault {
        if (outbound) {
            LOGGER.info("EBS request prepared");
        } else {
            Object configuredStatus = message.get(Message.RESPONSE_CODE);
            int status = configuredStatus instanceof Integer code && code >= 100 && code <= 599 ? code : -1;
            LOGGER.info("EBS response processed: httpStatus={}", status);
        }
    }
}
