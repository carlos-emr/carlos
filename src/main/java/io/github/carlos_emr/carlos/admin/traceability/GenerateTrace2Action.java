/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.admin.traceability;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

/**
 * Struts2 action that generates a compressed trace binary file of the current CARLOS EMR deployment.
 *
 * <p>Builds a SHA-256 hash map of all deployed files, serializes and GZIP-compresses the data,
 * and streams it to the client as a downloadable binary file. Uses piped streams with
 * {@link TraceDataProcessor} and {@link TraceDataConsumer} running in parallel threads.
 *
 * <p>Requires {@code _admin.traceability} privilege. The generated trace file can later be
 * uploaded to {@link GenerateTraceabilityReport2Action} for comparison.
 *
 * @see TraceDataProcessor
 * @see TraceDataConsumer
 * @see GenerateTraceabilityReport2Action
 * @since 2026-03-17
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class GenerateTrace2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public static int BUFFER_SIZE = 8192;

    /**
     * Generates the trace binary and streams it to the client as a downloadable file.
     *
     * @return String null (binary data is streamed directly to the response)
     * @throws Exception if piped stream or thread processing fails
     */
    public String execute() throws Exception {
        String userName = (String) request.getSession().getAttribute("user");
        String roleName$ = (String) request.getSession().getAttribute("userrole") + "," + userName;
        if (!GenerateTraceabilityUtil.hasPrivilege("_admin, _admin.traceability", roleName$)) {
            MiscUtils.getLogger().error("Access denied: " + userName);
            return null;
        }
        PipedInputStream pipedInputStream = null;
        PipedOutputStream pipedOutputStream = null;
        ExecutorService executor = null;
        Future<String> futureTRP = null;
        Future<String> futureTRC = null;
        try {
            pipedInputStream = new PipedInputStream(BUFFER_SIZE);
            pipedOutputStream = new PipedOutputStream(pipedInputStream);

            executor = Executors.newFixedThreadPool(2);
            TraceDataProcessor traceDataProcessor = new TraceDataProcessor(pipedOutputStream, request);
            TraceDataConsumer traceDataConsumer = new TraceDataConsumer(pipedInputStream, response);

            futureTRP = executor.submit(traceDataProcessor);
            futureTRC = executor.submit(traceDataConsumer);

            MiscUtils.getLogger().debug(new java.util.Date() + " " + futureTRP.get());
            MiscUtils.getLogger().debug(new java.util.Date() + " " + futureTRC.get());
            LogAction.addLog(userName, LogConst.ADD, "traceability downloaded", "trace_" + InetAddress.getLocalHost().getHostName().replace(' ', '_') + ".bin");
            executor.shutdown();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Not able to create", e);
        } finally {
            pipedInputStream.close();
            pipedOutputStream.close();
        }
        return null;
    }
}
