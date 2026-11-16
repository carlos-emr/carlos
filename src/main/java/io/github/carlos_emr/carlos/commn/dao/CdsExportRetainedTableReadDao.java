/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import java.util.List;
import java.util.Set;

/** Snapshot-only bounded projections; owning DAOs retain exact patient and selected-category scope. */
public interface CdsExportRetainedTableReadDao {
    /** Ordered actual JDBC column recipe for reproducing an existing native receipt; empty means unavailable. */
    default java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> readCdsExportReceiptColumns() { return java.util.List.of(); }
    CdsExportTableSchemaDto readCdsExportRetainedSchema();
    long countCdsExportRetainedRows(int patient, Set<String> categories);
    List<CdsExportRawKeyRowDto> findCdsExportRetainedRowsAfter(int patient, Set<String> categories, String afterId, int limit);
    byte[] readCdsExportRetainedChunk(int patient, Set<String> categories, String rowId, String column, long offset, int length);
}
