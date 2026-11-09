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
package io.github.carlos_emr.carlos.demographic.cdsexport.validator;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCoverageDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCoverageDto.Issue;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCoverageDto.IssueDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto.Disposition;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Merge-reconciles independently captured source inventory with serialization dispositions.
 * Inputs must use CdsExportIdentityDto ordering and contain exactly one row per occurrence.
 * Only the current two rows and a bounded preview are retained; callers own and close input
 * streams. Query/iterator failures, cancellation and quota failures propagate without a receipt.
 */
public final class CdsExportCoverageValidator {
    /**
     * @param expected independently sealed in-scope source identities, never derived from output
     * @param captureDigest identity of the sealed capture providing the expected inventory
     * @param actual dispositions collected from actual serialization, including blockers
     * @param maximumOccurrences server-owned bound applied separately to both inputs
     * @param checkpoint caller's deadline, cancellation and physical-ownership probe
     */
    public CdsExportCoverageDto validate(String captureDigest, Iterator<CdsExportIdentityDto> expected,
            Iterator<CdsExportDispositionDto> actual, long maximumOccurrences, Runnable checkpoint) {
        if (captureDigest == null || !captureDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_INVALID");
        }
        if (maximumOccurrences < 1) throw new IllegalArgumentException("CDS_EXPORT_COVERAGE_LIMIT_INVALID");
        Objects.requireNonNull(checkpoint, "checkpoint");
        OrderedInput<CdsExportIdentityDto> inventory = new OrderedInput<>(expected,
                Function.identity(), maximumOccurrences, checkpoint);
        OrderedInput<CdsExportDispositionDto> dispositions = new OrderedInput<>(actual,
                CdsExportDispositionDto::identity, maximumOccurrences, checkpoint);
        long emitted = 0, blocked = 0, missing = 0, unexpected = 0;
        List<IssueDto> issues = new ArrayList<>();
        CdsExportIdentityDto wanted = inventory.next();
        CdsExportDispositionDto found = dispositions.next();
        while (wanted != null || found != null) {
            if (found != null && !captureDigest.equals(found.captureDigest())) {
                throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_MISMATCH");
            }
            int comparison = wanted == null ? 1 : found == null ? -1 : wanted.compareTo(found.identity());
            if (comparison < 0) {
                missing++;
                preview(issues, wanted, Issue.MISSING);
                wanted = inventory.next();
            } else if (comparison > 0) {
                unexpected++;
                preview(issues, found.identity(), Issue.UNEXPECTED);
                found = dispositions.next();
            } else {
                if (found.disposition() == Disposition.EMITTED) emitted++;
                else blocked++;
                wanted = inventory.next();
                found = dispositions.next();
            }
        }
        checkpoint.run();
        return new CdsExportCoverageDto(captureDigest, inventory.count, emitted, blocked, missing, unexpected, issues);
    }

    private static void preview(List<IssueDto> issues, CdsExportIdentityDto identity, Issue issue) {
        if (issues.size() < CdsExportCoverageDto.MAXIMUM_ISSUE_PREVIEWS) issues.add(new IssueDto(identity, issue));
    }

    private static final class OrderedInput<T> {
        private final Iterator<T> input;
        private final Function<T, CdsExportIdentityDto> identity;
        private final long maximum;
        private final Runnable checkpoint;
        private CdsExportIdentityDto previous;
        private long count;

        private OrderedInput(Iterator<T> input, Function<T, CdsExportIdentityDto> identity,
                long maximum, Runnable checkpoint) {
            this.input = Objects.requireNonNull(input, "input");
            this.identity = identity;
            this.maximum = maximum;
            this.checkpoint = checkpoint;
        }

        private T next() {
            checkpoint.run();
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("CDS_EXPORT_COVERAGE_INTERRUPTED");
            }
            if (!input.hasNext()) return null;
            if (count == maximum) throw new IllegalStateException("CDS_EXPORT_COVERAGE_LIMIT_EXCEEDED");
            T value = Objects.requireNonNull(input.next(), "inventory occurrence");
            CdsExportIdentityDto current = Objects.requireNonNull(identity.apply(value), "occurrence identity");
            if (previous != null) {
                int order = previous.compareTo(current);
                if (order == 0) throw new IllegalArgumentException("CDS_EXPORT_COVERAGE_DUPLICATE_IDENTITY");
                if (order > 0) throw new IllegalArgumentException("CDS_EXPORT_COVERAGE_ORDER_INVALID");
            }
            previous = current;
            count++;
            return value;
        }
    }
}
