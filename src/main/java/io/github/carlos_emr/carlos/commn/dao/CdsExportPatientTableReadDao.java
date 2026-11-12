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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import java.util.List;

/**
 * Read-only export projections owned by each existing native entity DAO. The application
 * owns one authorized, schema-qualified patient snapshot for schema/count/headers/chunks.
 * A maximum 32-row header page carries at most 1MiB inline bytes per row; large text never
 * enters an entity or row DTO. IDs, including historical zero/negative IDs, remain exact.
 */
public interface CdsExportPatientTableReadDao {
    /** Ordered actual JDBC column recipe for reproducing an existing native receipt; empty means unavailable. */
    default java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> readCdsExportReceiptColumns() { return java.util.List.of(); }
    CdsExportTableSchemaDto readCdsExportSourceSchema();
    long countCdsExportRows(int patient);
    List<CdsExportRawRowDto> findCdsExportRowsAfter(int patient, Long afterId, int limit);
    byte[] readCdsExportValueChunk(int patient, long rowId, String column, long offset, int length);
}
