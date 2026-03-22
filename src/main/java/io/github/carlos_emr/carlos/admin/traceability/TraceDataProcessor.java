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

import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPOutputStream;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Callable that builds a file-hash trace map of the deployed application and writes it
 * as a GZIP-compressed serialized object to the output stream.
 *
 * <p>The trace map includes SHA-256 hashes for all deployed files, plus metadata
 * entries for the origin date and Git SHA commit hash. Runs in a separate thread
 * connected to a {@link TraceDataConsumer} via piped streams.
 *
 * @see TraceDataConsumer
 * @see GenerateTrace2Action
 * @since 2026-03-17
 */
public class TraceDataProcessor implements Callable<String> {
    private OutputStream outputStream = null;
    private HttpServletRequest request = null;

    /**
     * Constructs a processor that writes the trace map to the given output stream.
     *
     * @param outputStream OutputStream the piped output stream to write compressed data to
     * @param request HttpServletRequest used to resolve the web application's real path
     */
    public TraceDataProcessor(OutputStream outputStream, HttpServletRequest request) {
        this.request = request;
        this.outputStream = outputStream;
    }

    /**
     * Builds the trace map and writes it as a GZIP-compressed serialized Java object.
     *
     * @return String the class name of this processor
     * @throws Exception if file hashing, serialization, or compression fails
     */
    @Override
    public String call() throws Exception {
        Map<String, String> traceMap = GenerateTraceabilityUtil.buildTraceMap(request);
        traceMap.put("origin_date", new Date().toString());
        traceMap.put("git_sha", BuildNumberPropertiesFileReader.getGitSha1());
        ObjectOutputStream serializedStream = new ObjectOutputStream(new GZIPOutputStream(outputStream));
        serializedStream.writeObject(traceMap);
        serializedStream.close();
        return getClass().getName();
    }
}
