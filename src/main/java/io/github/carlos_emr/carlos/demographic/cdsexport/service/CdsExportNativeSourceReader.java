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

import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.IOException;
import java.util.Set;

/**
 * One explicit native-table reader. Schema, independent count and rows execute in one owning
 * read-only patient transaction. Implementations must not open another source transaction,
 * materialize an unbounded row/LOB collection, or read unauthorized sensitive occurrences.
 * Readers alone do not qualify an export profile or establish the complete source catalogue.
 */
public interface CdsExportNativeSourceReader {
    /** Immutable request-specific projection. Unselected category payloads must not enter source capture. */
    default CdsExportNativeSourceReader forRequest(io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand command) { return this; }
    /** Additional live fact-scope checks, after ordinary category/patient rights and outside the captured source transaction. */
    default void authorizeReadScope(int patient, io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl control) { }
    /** Explicit evidence dependencies; selection cannot omit a configuration/parent/lookup required by this reader. */
    default Set<String> requiredTables(){return Set.of(); }
    String table();
    Set<String> columns();
    Set<CdsCategory> categories();
    /** True when this projection can contain retained sensitive laboratory content without per-row classification. */
    default boolean requiresSensitiveLaboratoryRead() { return false; }
    /** No inference from SQL type strings: only actual ordered driver metadata can reproduce a historical receipt. */
    default java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> receiptColumns() { return java.util.List.of(); }
    CdsExportTableSchemaDto schema();
    long count(int patient);
    /** Bounded pre-capture discovery uses the same cancellation/ownership control as value streaming. */
    default long count(int patient, Runnable checkpoint){return count(patient); }
    /** Bounded source/file work can observe cancellation before a row is emitted. */
    default void capture(int patient, CdsExportNativeCaptureComposer.Writer output, Runnable checkpoint)throws IOException {
        java.util.Objects.requireNonNull(checkpoint).run(); capture(patient, output); checkpoint.run();
    }
    void capture(int patient, CdsExportNativeCaptureComposer.Writer output) throws IOException;
}
