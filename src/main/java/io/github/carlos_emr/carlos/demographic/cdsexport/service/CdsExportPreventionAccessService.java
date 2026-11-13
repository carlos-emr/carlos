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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded program-restriction preflight for a chart's preventions, read in its own short
 * transaction before the prevention source snapshot: only prevention ids and their programs,
 * never a prevention value. This neither replaces account, category or patient authorization nor
 * grants program access.
 *
 * <p>{@code preventions.restrictToProgram}/{@code programNo} are legacy OSCAR 19/CAISI columns
 * that no CARLOS screen writes or reads. A row restricted to a program is exported under the rule
 * the chart applies to a document restricted to a program: when every current exporting identity
 * works in that program ({@link Scope}). A program nobody exporting works in holds the patient
 * with {@code CDS_EXPORT_PREVENTION_PROGRAM_SCOPE}.
 *
 * @since 2026-11-09
 */
@Service
@Transactional(
        readOnly = true,
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED,
        timeout = 30)
public class CdsExportPreventionAccessService {
    private final PreventionDao source;

    public CdsExportPreventionAccessService(PreventionDao source) {
        this.source = Objects.requireNonNull(source);
    }

    /** Prevention id to program, for each of the chart's prevention rows restricted to a program. */
    public SortedMap<Integer, Integer> restrictions(int patient) {
        if (patient < 1) throw new IllegalArgumentException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        return source.readCdsExportProgramRestrictions(patient);
    }

    /**
     * The program restrictions one prevention reader authorized for one chart, outside the
     * source transaction. Immutable and per capture: a prepared scope is never retained on a
     * singleton reader. The unprepared scope reads nothing restricted, as before restricted rows
     * could be authorized.
     */
    static final class Scope {
        static final Scope UNPREPARED = new Scope(0, Collections.emptySortedMap());

        private final int patient;
        private final SortedMap<Integer, Integer> restrictions;

        private Scope(int patient, SortedMap<Integer, Integer> restrictions) {
            this.patient = patient;
            this.restrictions = Collections.unmodifiableSortedMap(new TreeMap<>(restrictions));
        }

        /** Reads the chart's restrictions in their own short transaction, before any value. */
        static Scope prepare(
                CdsExportPreventionAccessService access, int patient, CdsExportRunControl control)
                throws IOException {
            if (patient < 1) throw new IOException("CDS_EXPORT_PREVENTION_CAPTURE_SCOPE_INVALID");
            control.checkpoint();
            var read = access.restrictions(patient);
            control.checkpoint();
            if (read == null) throw new IOException("CDS_EXPORT_PREVENTION_CAPTURE_SCOPE_INVALID");
            return new Scope(patient, read);
        }

        boolean prepared() {
            return patient != 0;
        }

        SortedMap<Integer, Integer> restrictions() {
            return restrictions;
        }

        /**
         * Live program check, outside the source transaction, for every program a prevention of
         * this chart is restricted to and every category the reader serves. A restored capture is
         * checked again when its preventions are mapped.
         */
        void authorize(int requested, Set<CdsCategory> categories, CdsExportRunControl control) {
            if (!prepared()) return;
            if (patient != requested)
                throw new SecurityException("CDS_EXPORT_PREVENTION_CAPTURE_SCOPE_INVALID");
            for (int program : new TreeSet<>(restrictions.values()))
                for (var category : new TreeSet<>(categories)) {
                    control.checkpoint();
                    if (!control.restrictedProgramReadable(
                            patient, category, Integer.toString(program))) throw hold();
                }
        }

        /**
         * Inside the source snapshot, the chart's restrictions must be exactly the ones this scope
         * authorized ({@code CDS_EXPORT_SOURCE_CHANGED} otherwise). An unprepared scope reads
         * nothing restricted.
         */
        void requireSnapshot(int requested, SortedMap<Integer, Integer> current) {
            Objects.requireNonNull(current);
            if (!prepared()) {
                if (!current.isEmpty()) throw hold();
                return;
            }
            if (patient != requested)
                throw new IllegalStateException("CDS_EXPORT_PREVENTION_CAPTURE_NOT_PREPARED");
            if (!restrictions.equals(current))
                throw new CdsExportReviewException(CdsExportReviewException.Reason.SOURCE_CHANGED);
        }

        @Override
        public String toString() {
            return "CdsExportPreventionAccessService.Scope[protected]";
        }
    }

    static CdsExportReviewException hold() {
        return new CdsExportReviewException(
                CdsExportReviewException.Reason.PREVENTION_PROGRAM_SCOPE);
    }
}
