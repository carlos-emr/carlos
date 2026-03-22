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

import java.io.InputStream;
import java.util.concurrent.Callable;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Callable that reads a traceability report from a piped input stream and streams it
 * to the HTTP response as a downloadable text file (trace_report.txt).
 *
 * <p>Runs in a separate thread from the {@link TraceabilityReportProcessor} producer,
 * connected via piped streams for memory-efficient streaming.
 *
 * @see TraceabilityReportProcessor
 * @see GenerateTraceabilityReport2Action
 * @since 2026-03-17
 */
public class TraceabilityReportConsumer implements Callable<String> {
    private InputStream inputStream;
    private HttpServletResponse response;

    /**
     * Constructs a consumer that reads from the given input stream and writes to the response.
     *
     * @param inputStream InputStream the piped input stream from the report producer
     * @param response HttpServletResponse the HTTP response to stream the report to
     */
    public TraceabilityReportConsumer(InputStream inputStream, HttpServletResponse response) {
        this.inputStream = inputStream;
        this.response = response;
    }

    /**
     * Reads the report data from the input stream and writes it to the servlet output stream.
     *
     * @return String the class name of this consumer
     * @throws Exception if an I/O error occurs during streaming
     */
    @Override
    public String call() throws Exception {
        //setting content type to 'binary'
        response.setContentType("application/octet-stream");
        //the file is not created on server, users will see the file name after download
        String fileName = "trace_report.txt";
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        response.setHeader("Cache-Control", "no-cache");
        ServletOutputStream servletOutputStream = response.getOutputStream();
        int byte_;
        while ((byte_ = inputStream.read()) != -1) {
            servletOutputStream.write(byte_);
        }
        return getClass().getName();
    }
}
