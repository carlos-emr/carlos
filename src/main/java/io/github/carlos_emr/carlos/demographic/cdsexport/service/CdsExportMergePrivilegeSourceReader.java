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

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportMergePrivilegeRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * The chart privilege row Merge Records writes for a merged chart ({@code secObjPrivilege}, role
 * {@code _all}, object {@code _eChart$} and the chart number), captured for every chart under
 * Demographics as logical table {@value #TABLE}, every column exactly as stored.
 *
 * <p>The Merge Records page writes this row beside the link and the REST path does not; an
 * unmerge removes it on one path and not the other. The row and the link are therefore separate
 * facts and both travel exactly in the patient file's {@code CARLOS.MergeLinks.v1} record. The row
 * grants nothing a chart reader needs in this build and is never an input of the export's own
 * authorization.
 */
@Service
public class CdsExportMergePrivilegeSourceReader implements CdsExportNativeSourceReader {
    /** Logical table name of the capture. */
    public static final String TABLE = "merge_privileges";

    /** The captured columns: every column of {@code secObjPrivilege}. */
    static final Set<String> COLUMNS =
            Set.of("roleUserGroup", "objectName", "privilege", "priority", "provider_no");

    private final SecObjPrivilegeDao privileges;

    public CdsExportMergePrivilegeSourceReader(SecObjPrivilegeDao privileges) {
        this.privileges = Objects.requireNonNull(privileges);
    }

    @Override
    public String table() {
        return TABLE;
    }

    @Override
    public Set<String> columns() {
        return COLUMNS;
    }

    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.DEMOGRAPHICS);
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        return privileges.readCdsExportMergePrivilegeSchema();
    }

    @Override
    public long count(int patient) {
        return rows(patient).size();
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        for (var row : rows(patient).entrySet()) {
            var value = row.getValue();
            output.row(
                    row.getKey(),
                    fields -> {
                        fields.text("roleUserGroup", value.roleUserGroup());
                        fields.text("objectName", value.objectName());
                        fields.text("privilege", value.privilege());
                        fields.text("priority", value.priority());
                        fields.text("provider_no", value.providerNo());
                    });
        }
    }

    /**
     * The chart's rows by record key: the hexadecimal UTF-8 bytes of the role and the object
     * name, which the table's primary key makes unique, in ascending order.
     */
    private TreeMap<String, CdsExportMergePrivilegeRowDto> rows(int patient) {
        if (patient < 1) throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        var rows = new TreeMap<String, CdsExportMergePrivilegeRowDto>();
        List<CdsExportMergePrivilegeRowDto> found = privileges.findCdsExportMergePrivileges(patient);
        for (var row : found) {
            if (row == null || row.roleUserGroup() == null || row.objectName() == null)
                throw new IllegalStateException("CDS_EXPORT_NATIVE_SCALAR_INVALID");
            String key =
                    HexFormat.of()
                            .formatHex(
                                    (row.roleUserGroup() + "\u0000" + row.objectName())
                                            .getBytes(StandardCharsets.UTF_8));
            if (rows.put(key, row) != null)
                throw new IllegalStateException("CDS_EXPORT_NATIVE_SCALAR_INVALID");
        }
        return rows;
    }
}
