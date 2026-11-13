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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.util.*;

/**
 * Version state of a native prevention row. CARLOS never updates a prevention in place:
 * {@code PreventionData.updatetPreventionData} soft-deletes the edited row ({@code deleted='1'})
 * and inserts the new version, and a user delete also only sets {@code deleted='1'}. The legacy
 * CDS exporter read {@code PreventionDao.findNotDeletedByDemographicId}, so those versions were
 * never exported. They stay in the capture, and every field of the version, its extensions and
 * its precision rows receives an explicit not-exported disposition; they are neither an
 * occurrence nor a reason to hold the patient. A legacy NULL flag records no deletion and is
 * current; any other value is unknown and still requires review.
 */
final class CdsExportPreventionVersions {
    static final String SUPERSEDED = "SUPERSEDED_OR_REMOVED_PREVENTION_VERSION";

    private CdsExportPreventionVersions() {}

    /** True only for the native soft-delete flag; NULL, "0" and unknown values are not. */
    static boolean superseded(CdsExportCapturedTextRowDto parent) {
        return "1".equals(parent.fields().get("deleted"));
    }

    /** "0" or a legacy NULL: the current version of a prevention. */
    static boolean current(String deleted) {
        return deleted == null || "0".equals(deleted);
    }

    /** Same decision from an authenticated row witness, without reading the clinical values. */
    static boolean superseded(CdsExportNativeRowReceiptDto witness) throws IOException {
        if (witness.state() == null)
            throw new IOException("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
        return CdsExportImmunizationProjectionEvidence.matches(witness.state(), "deleted", "1");
    }

    /** One explicit not-exported disposition for every captured field of a superseded version. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> dispositions(
            List<CdsExportCapturedTextRowDto> rows) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var row : rows) {
            var fields = new CdsExportNativeFieldAssessments(row, result);
            for (String column : row.fields().keySet()) fields.operational(column, SUPERSEDED);
        }
        return result;
    }
}
