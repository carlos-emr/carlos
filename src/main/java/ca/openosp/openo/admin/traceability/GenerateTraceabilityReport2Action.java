//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.admin.traceability;

import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;

/**
 * Upload 'trace', process it,
 * compare with the local 'trace'
 * and create report
 *
 * @author oscar
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class GenerateTraceabilityReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    public static int BUFFER_SIZE = 8192;

    private File file;

    @Override
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

            TraceabilityReportProcessor traceabilityReportProcessor = new TraceabilityReportProcessor(pipedOutputStream, file, request);
            TraceabilityReportConsumer traceabilityReportConsumer = new TraceabilityReportConsumer(pipedInputStream, response);

            futureTRP = executor.submit(traceabilityReportProcessor);
            futureTRC = executor.submit(traceabilityReportConsumer);

            MiscUtils.getLogger().debug(new java.util.Date() + " " + futureTRP.get());
            MiscUtils.getLogger().debug(new java.util.Date() + " " + futureTRC.get());
            LogAction.addLog(userName, LogConst.ADD, "traceability report downloaded", "trace_report.txt");
            executor.shutdown();

        } catch (Exception e) {
            MiscUtils.getLogger().error("Not able to create file", e);
        } finally {
            pipedInputStream.close();
            pipedOutputStream.close();
        }
        return null;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
