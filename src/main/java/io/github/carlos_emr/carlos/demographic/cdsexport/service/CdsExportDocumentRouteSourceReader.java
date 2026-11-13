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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dao.CdsExportReviewerReadDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The inbox routes of the patient's documents ({@code providerLabRouting} rows of type DOC),
 * every status. They are captured under their own logical name because LaboratoryResults reads
 * the HL7 routes of the same physical table; the schema contract names the physical table. The
 * report mapper exports each acknowledgement as a ReportReviewed and accounts for every other
 * route explicitly. Ambiguous or dangling document ownership is refused by the DAO first.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportDocumentRouteSourceReader extends CdsExportPatientTableSourceReader {
    /** Logical capture name of the document routes. */
    static final String TABLE = "providerLabRouting_documents";

    /** Every capture a native document report reads its reviewers from. */
    static final Set<String> REVIEWER_TABLES =
            Set.of(TABLE, "document_review", "DocumentExtraReviewer", "provider");

    public CdsExportDocumentRouteSourceReader(CdsExportReviewerReadDao source) {
        super(
                reviewers(source, CdsExportPatientTableReadSupport.Plan.DOCUMENT_ROUTES),
                CdsExportPatientTableReadSupport.Plan.DOCUMENT_ROUTES,
                Set.of(CdsCategory.REPORTS),
                TABLE);
    }

    @Override
    public Set<String> requiredTables() {
        return CdsExportDocumentReviewSourceReader.required();
    }
}
