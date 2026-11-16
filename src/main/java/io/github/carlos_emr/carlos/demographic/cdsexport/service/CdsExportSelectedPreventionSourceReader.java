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

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.*;

/**
 * Reuses the bounded streaming reader with a typed, immutable snapshot selection adapter. One
 * reader reads either the vaccine selection (Immunizations) or the screening selection
 * (PastHealth) of the same frozen partition; the two never share a logical table.
 *
 * <p>A selected row may be restricted to a program (legacy OSCAR 19/CAISI columns). The reader
 * reads the chart's restrictions first, in their own short transaction ({@link #forCapture});
 * authorizes each program live before any value is read ({@link #authorizeReadScope}); and,
 * before every read inside the snapshot, proves that the restrictions the selection proved in
 * that snapshot are exactly the ones it authorized. A reader never prepared reads nothing
 * restricted. The mappers check each program again, live, before the row is written.
 */
abstract class CdsExportSelectedPreventionSourceReader extends CdsExportPatientTableSourceReader {
    /** Which frozen partition of the patient's prevention rows a reader may read. */
    enum Partition {
        IMMUNIZATIONS(CdsCategory.IMMUNIZATIONS),
        SCREENINGS(CdsCategory.PAST_HEALTH);

        private final CdsCategory category;

        Partition(CdsCategory category) {
            this.category = category;
        }
    }

    private final CdsExportPreventionDataReadDao source;
    private final CdsExportPatientTableReadSupport.Plan plan;
    private final CdsExportPreventionSnapshotService snapshots;
    private final CdsExportPreventionAccessService access;
    private final Partition partition;
    private final String table;
    private final CdsExportPreventionAccessService.Scope scope;

    CdsExportSelectedPreventionSourceReader(
            CdsExportPreventionDataReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            CdsExportPreventionSnapshotService snapshots,
            CdsExportPreventionAccessService access) {
        this(source, plan, snapshots, access, Partition.IMMUNIZATIONS, plan.table());
    }

    /**
     * @param table the logical table captured; its schema contract names the physical table the
     *     plan reads
     */
    CdsExportSelectedPreventionSourceReader(
            CdsExportPreventionDataReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            CdsExportPreventionSnapshotService snapshots,
            CdsExportPreventionAccessService access,
            Partition partition,
            String table) {
        this(
                source,
                plan,
                snapshots,
                access,
                partition,
                table,
                CdsExportPreventionAccessService.Scope.UNPREPARED);
    }

    private CdsExportSelectedPreventionSourceReader(
            CdsExportPreventionDataReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            CdsExportPreventionSnapshotService snapshots,
            CdsExportPreventionAccessService access,
            Partition partition,
            String table,
            CdsExportPreventionAccessService.Scope scope) {
        super(
                new Selected(source, plan, snapshots, partition, scope),
                plan,
                Set.of(partition.category),
                table);
        this.source = Objects.requireNonNull(source);
        this.plan = Objects.requireNonNull(plan);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.access = Objects.requireNonNull(access);
        this.partition = Objects.requireNonNull(partition);
        this.table = Objects.requireNonNull(table);
        this.scope = Objects.requireNonNull(scope);
    }

    /** The chart's program restrictions, read in their own short transaction before capture. */
    @Override
    public CdsExportNativeSourceReader forCapture(int patient, CdsExportRunControl control)
            throws java.io.IOException {
        if (scope.prepared())
            throw new java.io.IOException("CDS_EXPORT_PREVENTION_CAPTURE_SCOPE_INVALID");
        return new Prepared(
                source,
                plan,
                snapshots,
                access,
                partition,
                table,
                CdsExportPreventionAccessService.Scope.prepare(access, patient, control));
    }

    /** Live program check for every restricted program, outside the source transaction. */
    @Override
    public void authorizeReadScope(int patient, CdsExportRunControl control) {
        scope.authorize(patient, Set.of(partition.category), control);
        control.checkpoint();
    }

    @Override
    public Set<String> requiredTables() {
        return partition == Partition.IMMUNIZATIONS
                ? CdsExportPreventionSources.union(
                        CdsExportPreventionSources.SHARED,
                        CdsExportPreventionSources.IMMUNIZATION_TABLES)
                : CdsExportPreventionSources.SCREENING_REQUIRED;
    }

    /** One capture's prepared copy of a registered reader; never a Spring bean. */
    private static final class Prepared extends CdsExportSelectedPreventionSourceReader {
        private Prepared(
                CdsExportPreventionDataReadDao source,
                CdsExportPatientTableReadSupport.Plan plan,
                CdsExportPreventionSnapshotService snapshots,
                CdsExportPreventionAccessService access,
                Partition partition,
                String table,
                CdsExportPreventionAccessService.Scope scope) {
            super(source, plan, snapshots, access, partition, table, scope);
        }
    }

    private record Selected(
            CdsExportPreventionDataReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            CdsExportPreventionSnapshotService snapshots,
            Partition partition,
            CdsExportPreventionAccessService.Scope scope)
            implements CdsExportPatientTableReadDao {
        @Override
        public CdsExportTableSchemaDto readCdsExportSourceSchema() {
            return source.readCdsExportSourceSchema();
        }

        @Override
        public List<CdsNativeRowDigest.Column> readCdsExportReceiptColumns() {
            return source.readCdsExportReceiptColumns();
        }

        /**
         * The selected ids, after proving that the restrictions the selection proved in this
         * snapshot are exactly the ones this reader authorized.
         */
        private Set<Integer> ids(int patient) {
            var state = snapshots.current(patient);
            scope.requireSnapshot(patient, state.restrictions());
            return partition == Partition.IMMUNIZATIONS
                    ? state.clinicalIds()
                    : state.screeningIds();
        }

        @Override
        public long countCdsExportRows(int patient) {
            var selected = ids(patient);
            long actual = source.countCdsExportPreventionRows(patient, selected);
            if (plan == CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL
                    && actual != selected.size())
                throw new IllegalStateException("CDS_EXPORT_PREVENTION_SELECTION_CHANGED");
            return actual;
        }

        @Override
        public List<CdsExportRawRowDto> findCdsExportRowsAfter(int patient, Long after, int limit) {
            return source.findCdsExportPreventionRowsAfter(patient, ids(patient), after, limit);
        }

        @Override
        public byte[] readCdsExportValueChunk(
                int patient, long row, String column, long offset, int length) {
            return source.readCdsExportPreventionChunk(
                    patient, ids(patient), row, column, offset, length);
        }
    }
}
