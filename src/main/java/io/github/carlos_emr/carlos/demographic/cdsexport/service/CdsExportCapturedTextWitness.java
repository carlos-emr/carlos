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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact captured textual field values and their separately captured native row witness. */
final class CdsExportCapturedTextWitness {
    static void require(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness, Runnable checkpoint)throws IOException{
        require(row, witness, "id", checkpoint);
    }
    /** Non-id primary keys must be selected explicitly by the owning table reader. */
    static void require(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness, String identityColumn, Runnable checkpoint)throws IOException{
        require(row, witness, identityColumn, false, checkpoint);
    }
    /** The selected demographic note projection omits the unselected alert column explicitly. */
    static void require(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness, String identityColumn,
            boolean partial, Runnable checkpoint)throws IOException {
        Objects.requireNonNull(checkpoint); checkpoint.run();
        if (identityColumn==null||row==null||witness==null||witness.state()==null||witness.partial()!=partial||!row.table().equals(witness.table())||!row.recordKey().equals(witness.recordKey())||!witness.id().equals(row.fields().get(identityColumn))||!row.exactNumbers().isEmpty()||!row.fields().keySet().equals(Set.copyOf(witness.state().columns().stream().map(column-> column.name()).toList())))throw new IOException("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
        for (var column:witness.state().columns()){checkpoint.run(); if (!matches(witness.state(), column.name(), row.fields().get(column.name())))throw new IOException("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID"); }
    }
    static boolean matches(CdsNativeRowState state, String column, String value)throws IOException{
        int index=-1; for (int n=0; n<state.columns().size(); n++)if (state.columns().get(n).name().equals(column))index=n; if (index<0)throw new IOException("CDS_EXPORT_CAPTURED_TEXT_WITNESS_COLUMN_REQUIRED"); var cell=state.cells().get(index); if (value==null)return cell.sqlNull(); byte[] bytes=value.getBytes(StandardCharsets.UTF_8); return !cell.sqlNull()&&cell.bytes()==bytes.length&&cell.sha256().equals(HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
    }
}
