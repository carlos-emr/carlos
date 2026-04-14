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
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.input.BoundedInputStream;

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

    /** Maximum object graph depth for traceability deserialization. */
    private static final int TRACE_MAX_DEPTH = 10;
    /** Maximum object reference count for traceability deserialization. */
    private static final long TRACE_MAX_REFS = 500_000L;
    /** Maximum object stream byte count (50 MB) for traceability deserialization. */
    private static final long TRACE_MAX_BYTES = 50_000_000L;
    /** Maximum array length for traceability deserialization. */
    private static final int TRACE_MAX_ARRAY = 100_000;
    /**
     * Maximum uncompressed bytes (100 MB) read from the GZIP stream to prevent
     * decompression bomb (DoS) attacks before ObjectInputStream is reached.
     */
    private static final long TRACE_MAX_GZIP_UNCOMPRESSED_BYTES = 100_000_000L;

    /**
     * Restricts deserialization to {@link HashMap} and {@link String} only, which are the
     * exact types written by {@link TraceDataProcessor} for traceability data
     * ({@code Map<String, String>}).
     *
     * <p>In Java 21+, {@code HashMap.readObject()} validates its internal bucket array
     * allocation via {@code ObjectInputStream.checkArray(s, Map.Entry[].class, cap)},
     * so {@code Map.Entry[]} must also be allowed through the filter.
     *
     * <p>Also enforces resource bounds (depth, references, stream bytes, array length)
     * to mitigate deserialization bomb (DoS) attacks from uploaded files.
     */
    private static final ObjectInputFilter TRACE_DESERIALIZATION_FILTER = filterInfo -> {
        if (filterInfo.depth() > TRACE_MAX_DEPTH ||
            filterInfo.references() > TRACE_MAX_REFS ||
            filterInfo.streamBytes() > TRACE_MAX_BYTES ||
            (filterInfo.arrayLength() >= 0 && filterInfo.arrayLength() > TRACE_MAX_ARRAY)) {
            return ObjectInputFilter.Status.REJECTED;
        }
        if (filterInfo.serialClass() != null) {
            String name = filterInfo.serialClass().getName();
            if (name.equals(HashMap.class.getName()) ||
                name.equals(String.class.getName())) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            // Java 21+: HashMap.readObject() calls checkArray(s, Map.Entry[].class, cap)
            // to validate internal bucket array allocation through the filter.
            if (filterInfo.serialClass().isArray() &&
                filterInfo.serialClass().getComponentType() == Map.Entry.class) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            return ObjectInputFilter.Status.REJECTED;
        }
        // Non-class invocations (metrics-only updates for depth/refs/bytes) —
        // resource bounds already checked above, so allow them to proceed.
        return ObjectInputFilter.Status.ALLOWED;
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

        // Wrap GZIPInputStream output in BoundedInputStream to prevent decompression
        // bomb (DoS) attacks — limits uncompressed bytes before ObjectInputStream reads.
        // FileInputStream is declared separately so it is closed if GZIPInputStream
        // construction throws (e.g. not-a-gzip file).
        try (FileInputStream fis = new FileInputStream(uploadedFile);
             GZIPInputStream gzip = new GZIPInputStream(fis);
             InputStream bounded = BoundedInputStream.builder()
                 .setInputStream(gzip)
                 .setMaxCount(TRACE_MAX_GZIP_UNCOMPRESSED_BYTES)
                 .get();
             ObjectInputStream ios = new ObjectInputStream(bounded);
             PrintWriter pw = new PrintWriter(outputStream)) {

            ios.setObjectInputFilter(TRACE_DESERIALIZATION_FILTER);

            // FP for deserialization scanners (CodeQL java/UnsafeDeserialization, Semgrep
            // object-deserialization): TRACE_DESERIALIZATION_FILTER is an exceptionally tight
            // allowlist of HashMap/String/Map.Entry[] only, and the stream is wrapped in a
            // BoundedInputStream above to prevent decompression-bomb DoS.
            @SuppressWarnings("unchecked")
            Map<String, String> sourceMap = (Map<String, String>) ios.readObject(); // nosemgrep: java.lang.security.audit.object-deserialization.object-deserialization -- filter+bounded // lgtm[java/unsafe-deserialization]

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
