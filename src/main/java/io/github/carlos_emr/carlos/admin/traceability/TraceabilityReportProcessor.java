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

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

/**
 * Build 'traceability report' from compressed serialized stream
 * Send to output stream for later consuming
 *
 * @author oscar
 */
public class TraceabilityReportProcessor implements Callable<String> {
    private OutputStream outputStream = null;
    private File uploadedFile;
    HttpServletRequest request = null;

    private String newLine = System.getProperty("line.separator");

    /**
     * Restricts deserialization to HashMap and String only, which are the
     * types written by {@link TraceDataProcessor} for traceability data.
     */
    private static final ObjectInputFilter TRACE_DESERIALIZATION_FILTER = filterInfo -> {
        if (filterInfo.serialClass() != null) {
            String name = filterInfo.serialClass().getName();
            if (name.equals("java.util.HashMap") ||
                name.equals("java.lang.String") ||
                name.equals("java.lang.Integer")) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            return ObjectInputFilter.Status.REJECTED;
        }
        return ObjectInputFilter.Status.UNDECIDED;
    };

    public TraceabilityReportProcessor(OutputStream outputStream, File uploadedFile, HttpServletRequest request) {
        this.request = request;
        this.uploadedFile = uploadedFile;
        this.outputStream = outputStream;
    }

    @Override
    public String call() throws Exception {
        ClinicDAO dao = SpringUtils.getBean(ClinicDAO.class);
        Clinic clinic = dao.getClinic();
        String clinicName = clinic.getClinicName();
        String originDate = null;
        String gitSHA = null;

        try (GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(uploadedFile));
         ObjectInputStream ios = new ObjectInputStream(gzip);
         PrintWriter pw = new PrintWriter(outputStream)) {

            ios.setObjectInputFilter(TRACE_DESERIALIZATION_FILTER);

            @SuppressWarnings("unchecked")
            Map<String, String> sourceMap = (Map<String, String>) ios.readObject();

            originDate = sourceMap.getOrDefault("origin_date", "n/a");
            gitSHA = sourceMap.getOrDefault("git_sha", "n/a");
            sourceMap.remove("origin_date");
            sourceMap.remove("git_sha");

            // build local 'trace'
            Map<String, String> targetMap = GenerateTraceabilityUtil.buildTraceMap(request);
            // find the difference between incoming and local 'trace'
            MapDifference<String, String> diff = Maps.difference(sourceMap, targetMap);
            // modified, for the same keys
            Map<String, MapDifference.ValueDifference<String>> differing = diff.entriesDiffering();

            pw.write("---------------------------------------------------------------------------------------");
            pw.write(newLine);
            pw.write("----------------------------TRACEABILITY REPORT----------------------------------------");
            pw.write(newLine);
            pw.write("---------------------------------------------------------------------------------------");
            pw.write(newLine);
            pw.write(newLine);
            pw.write("Started: " + new java.util.Date());
            pw.write(newLine);
            pw.write(newLine);
            pw.write("Trace Generated On Date: " + originDate);
            pw.write(newLine);
            pw.write("Clinic Name: " + clinicName);
            pw.write(newLine);
            pw.write("Git SHA: " + gitSHA);
            pw.write(newLine);
            pw.write(newLine);
            pw.write("Changed:");
            pw.write(newLine);
            pw.write("-----------------------------------------");
            pw.write(newLine);
            pw.write(newLine);
            for (Map.Entry<String, MapDifference.ValueDifference<String>> entry : differing.entrySet()) {
                String key = entry.getKey();
                pw.write(key);
                pw.write(newLine);
                pw.write(newLine);
            }
            //to check equality
            //boolean mapsEqual = diff.areEqual();

            pw.write(newLine);
            pw.write("Removed:");
            pw.write(newLine);
            pw.write("-----------------------------------------");
            pw.write(newLine);
            pw.write(newLine);
            Map<String, String> left_ = diff.entriesOnlyOnLeft();
            for (Map.Entry<String, String> entry : left_.entrySet()) {
                String key = entry.getKey();
                pw.write(key);
                pw.write(newLine);
            }

            pw.write(newLine);
            pw.write("Added:");
            pw.write(newLine);
            pw.write("-----------------------------------------");
            pw.write(newLine);
            pw.write(newLine);
            Map<String, String> right_ = diff.entriesOnlyOnRight();
            for (Map.Entry<String, String> entry : right_.entrySet()) {
                String key = entry.getKey();
                pw.write(key);
                pw.write(newLine);
            }

            pw.write(newLine);
            pw.write("Unchanged:");
            pw.write(newLine);
            pw.write("-----------------------------------------");
            pw.write(newLine);
            pw.write(newLine);
            Map<String, String> common = diff.entriesInCommon();
            for (Map.Entry<String, String> entry : common.entrySet()) {
                String key = entry.getKey();
                pw.write(key);
                pw.write(newLine);
            }

            pw.write("Finished: " + new java.util.Date());
            pw.write(newLine);
            pw.write("---------------------------------------------------------------------------------------");
            pw.write(newLine);
            pw.write("--------------------------------END OF REPORT------------------------------------------");
            pw.write(newLine);
            pw.write("---------------------------------------------------------------------------------------");
            pw.write(newLine);
            pw.flush();
        }
        return getClass().getName();
    }
}
