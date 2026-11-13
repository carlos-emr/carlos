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

    private final CdsExportPreventionAccessService access;
    private final Partition partition;

    CdsExportSelectedPreventionSourceReader(
            CdsExportPreventionDataReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            CdsExportPreventionSnapshotService snapshots,
            CdsExportPreventionAccessService access) {
        this(source, plan, snapshots, access, Partition.IMMUNIZATIONS);
    }

    CdsExportSelectedPreventionSourceReader(
            CdsExportPreventionDataReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            CdsExportPreventionSnapshotService snapshots,
            CdsExportPreventionAccessService access,
            Partition partition) {
        super(
                new Selected(source, plan, snapshots, partition),
                plan,
                Set.of(partition.category));
        this.access = Objects.requireNonNull(access);
        this.partition = partition;
    }

    @Override
    public void authorizeReadScope(int patient, CdsExportRunControl control) {
        access.requireUnrestricted(patient);
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

    private record Selected(
            CdsExportPreventionDataReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            CdsExportPreventionSnapshotService snapshots,
            Partition partition)
            implements CdsExportPatientTableReadDao {
        @Override
        public CdsExportTableSchemaDto readCdsExportSourceSchema() {
            return source.readCdsExportSourceSchema();
        }

        @Override
        public List<CdsNativeRowDigest.Column> readCdsExportReceiptColumns() {
            return source.readCdsExportReceiptColumns();
        }

        private Set<Integer> ids(int patient) {
            var state = snapshots.current(patient);
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
