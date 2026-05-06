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
package io.github.carlos_emr.carlos.fax.hylafax;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.commn.model.FaxJob;

/**
 * Parses HylaFax command output into CARLOS fax domain objects.
 *
 * @since 2026-05-05
 */
public class HylaFaxJobParser {

    /**
     * Maps provider status text from {@code faxstat} to CARLOS fax status values.
     *
     * @param statusText status text returned by HylaFax
     * @return internal fax job status
     */
    public FaxJob.STATUS mapStatus(String statusText) {
        if (statusText == null || statusText.trim().isEmpty()) {
            return FaxJob.STATUS.UNKNOWN;
        }
        String normalized = statusText.toLowerCase(Locale.ROOT);
        if (normalized.contains("done")
                || normalized.contains("sent")
                || normalized.contains("complete")
                || normalized.contains("successful")) {
            return FaxJob.STATUS.COMPLETE;
        }
        if (normalized.contains("failed")
                || normalized.contains("no answer")
                || normalized.contains("busy")
                || normalized.contains("reject")
                || normalized.contains("kill")
                || normalized.contains("error")) {
            return FaxJob.STATUS.ERROR;
        }
        if (normalized.contains("cancel")) {
            return FaxJob.STATUS.CANCELLED;
        }
        if (normalized.contains("waiting")
                || normalized.contains("pending")
                || normalized.contains("queued")
                || normalized.contains("send")
                || normalized.contains("retry")
                || normalized.contains("blocked")
                || normalized.contains("running")) {
            return FaxJob.STATUS.SENT;
        }
        return FaxJob.STATUS.UNKNOWN;
    }

    /**
     * Parses the provider job id from sendfax command output.
     *
     * @param output sendfax output
     * @return parsed job id or {@code null} when no numeric id is present
     */
    public Long parseSubmittedJobId(String output) {
        if (output == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(?:jobid|job\\s+id|request\\s+id|job)\\D{0,20}(\\d+)")
                .matcher(output);
        if (matcher.find()) {
            return Long.valueOf(matcher.group(1));
        }
        return null;
    }

    /**
     * Parses a single {@code faxstat -s} row for a specific job id.
     *
     * @param output faxstat output
     * @param jobId provider job id
     * @return fax job containing status information
     */
    public FaxJob parseStatus(String output, Long jobId) {
        FaxJob faxJob = new FaxJob();
        faxJob.setJobId(jobId);
        String statusText = findStatusText(output, jobId);
        faxJob.setStatus(mapStatus(statusText));
        faxJob.setStatusString(statusText == null ? "HylaFax status unavailable" : statusText);
        return faxJob;
    }

    /**
     * Parses tab-delimited recvq listing output.
     *
     * @param output listing output with epoch millis, size, filename and optional sender columns
     * @return inbound fax metadata
     */
    public List<FaxJob> parseRecvqListing(String output) {
        List<FaxJob> result = new ArrayList<>();
        if (output == null || output.trim().isEmpty()) {
            return result;
        }
        for (String line : output.split("\\R")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\t", 4);
            if (parts.length < 3) {
                continue;
            }
            FaxJob faxJob = new FaxJob();
            faxJob.setFile_name(Path.of(parts[2]).getFileName().toString());
            faxJob.setStatus(FaxJob.STATUS.RECEIVED);
            faxJob.setStatusString("Ready for HylaFax download");
            faxJob.setStamp(parseEpochMillis(parts[0]));
            if (parts.length > 3 && !parts[3].isBlank()) {
                faxJob.setRecipient(parts[3].trim());
            }
            parseNumericFilenameStem(faxJob);
            result.add(faxJob);
        }
        return result;
    }

    private Date parseEpochMillis(String value) {
        try {
            return Date.from(Instant.ofEpochMilli(Long.parseLong(value)));
        } catch (RuntimeException e) {
            return new Date();
        }
    }

    private String findStatusText(String output, Long jobId) {
        if (output == null || output.trim().isEmpty()) {
            return null;
        }
        String jobIdText = jobId == null ? null : String.valueOf(jobId);
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.toLowerCase(Locale.ROOT).startsWith("jid")) {
                continue;
            }
            if (jobIdText == null || trimmed.startsWith(jobIdText + " ") || trimmed.equals(jobIdText)) {
                String[] columns = trimmed.split("\\s+", 8);
                if (columns.length >= 8) {
                    return columns[7].trim();
                }
                return trimmed;
            }
        }
        return null;
    }

    private void parseNumericFilenameStem(FaxJob faxJob) {
        String fileName = faxJob.getFile_name();
        if (fileName == null) {
            return;
        }
        String stem = fileName;
        int dot = stem.indexOf('.');
        if (dot > 0) {
            stem = stem.substring(0, dot);
        }
        String digits = stem.replaceAll("\\D", "");
        if (!digits.isEmpty()) {
            try {
                faxJob.setJobId(Long.parseLong(digits));
            } catch (NumberFormatException e) {
                // Ignore oversized numeric stems; filenames remain sufficient identifiers for inbound faxes.
            }
        }
    }
}
