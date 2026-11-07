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

import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric;
import io.github.carlos_emr.carlos.demographic.cds.jdbc.CdsBinaryReads;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import org.hibernate.Session;

/** Owning prescription DAO's exact dose witnesses, bounded by its already captured patient page. */
final class CdsExportExactNumericReadSupport {
    private CdsExportExactNumericReadSupport() { }
    static List<CdsExportRawRowDto> drugs(EntityManager em, int patient, List<CdsExportRawRowDto> page) {
        int timeout = CdsExportPatientTableReadSupport.queryTimeoutMillis();
        if (patient < 1 || page.size() > 32) invalid();
        if (page.isEmpty()) return page;
        return em.unwrap(Session.class).doReturningWork(connection -> {
            var expected = new HashSet<Long>(); for (var row : page) if (!expected.add(row.id())) invalid();
            String sql = "SELECT drugid,takemin,takemax FROM drugs WHERE demographic_no=? AND drugid IN ("
                    + String.join(",", java.util.Collections.nCopies(page.size(), "?")) + ") ORDER BY drugid";
            var actual = new TreeMap<Long, java.util.Map<String, CdsExactNumeric>>();
            try (var query = CdsBinaryReads.prepare(connection, sql)) {
                query.setInt(1, patient); int parameter = 2; for (var row : page) query.setLong(parameter++, row.id());
                query.setMaxRows(33); query.setQueryTimeout((timeout + 999) / 1000);
                try (var rows = query.executeQuery()) {
                    var metadata = rows.getMetaData();
                    if (metadata.getColumnCount() != 3 || CdsExactNumeric.codec(metadata.getColumnTypeName(2)) != CdsExactNumeric.Codec.IEEE754_BINARY32_V1
                            || CdsExactNumeric.codec(metadata.getColumnTypeName(3)) != CdsExactNumeric.Codec.IEEE754_BINARY32_V1) invalid();
                    while (rows.next()) {
                        long id = rows.getLong(1); var values = new TreeMap<String, CdsExactNumeric>();
                        values.put("takemin", CdsExactNumeric.readBinary(rows, 2, CdsExactNumeric.Codec.IEEE754_BINARY32_V1));
                        values.put("takemax", CdsExactNumeric.readBinary(rows, 3, CdsExactNumeric.Codec.IEEE754_BINARY32_V1));
                        if (!expected.contains(id) || actual.putIfAbsent(id, values) != null) invalid();
                    }
                }
            }
            if (!actual.keySet().equals(expected)) invalid();
            var result = new ArrayList<CdsExportRawRowDto>();
            for (var row : page) {
                var cells = new TreeMap<>(row.cells());
                for (var entry : actual.get(row.id()).entrySet()) {
                    var cell = cells.get(entry.getKey()); var value = entry.getValue();
                    if (cell == null || cell.sqlNull() != (value == null)) invalid();
                    if (value != null) cells.put(entry.getKey(), new CdsExportRawCellDto(false, cell.bytes(), cell.sha256(), cell.inlineBytes(), value));
                }
                result.add(new CdsExportRawRowDto(row.id(), cells));
            }
            return List.copyOf(result);
        });
    }
    private static void invalid() { throw new IllegalStateException("CDS_EXPORT_EXACT_NUMERIC_SOURCE_INVALID"); }
}
