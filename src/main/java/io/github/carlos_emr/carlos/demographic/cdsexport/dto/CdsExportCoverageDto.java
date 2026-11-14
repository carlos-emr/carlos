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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.List;
import java.util.Objects;

/**
 * Source accounting only. Matching accounting does not prove clinical semantics, output-file
 * integrity, schema/profile conformance, snapshot coherence, or permission to publish a package.
 */
public record CdsExportCoverageDto(String captureDigest, long expected, long emitted, long blocked, long missing,
        long unexpected, List<IssueDto> issues) {
    public static final int MAXIMUM_ISSUE_PREVIEWS = 16;
    public enum Issue { MISSING, UNEXPECTED }
    public record IssueDto(CdsExportIdentityDto identity, Issue issue) {
        public IssueDto {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(issue, "issue");
        }
        @Override public String toString() { return "CdsExportCoverageIssueDto[protected]"; }
    }

    public CdsExportCoverageDto {
        if (captureDigest == null || !captureDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_INVALID");
        }
        issues = List.copyOf(issues);
        if (expected < 0 || emitted < 0 || blocked < 0 || missing < 0 || unexpected < 0
                || issues.size() > MAXIMUM_ISSUE_PREVIEWS
                || Math.addExact(Math.addExact(emitted, blocked), missing) != expected) {
            throw new IllegalArgumentException("CDS_EXPORT_COVERAGE_INVALID");
        }
    }

    /** An empty inventory cannot certify that a requested export found all of its source data. */
    public boolean sourceCoverageComplete() {
        return expected > 0 && expected == emitted && blocked == 0 && missing == 0 && unexpected == 0;
    }

    public boolean issuePreviewsLimited() {
        return missing > issues.size() || unexpected > issues.size() - missing;
    }

    @Override public String toString() { return "CdsExportCoverageDto[protected]"; }
}
