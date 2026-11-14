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
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The review records of the patient's documents ({@code document_review}), exported as
 * ReportReviewed beside the document's first reviewer. Ambiguous or dangling document ownership
 * is refused by the DAO first.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportDocumentReviewSourceReader extends CdsExportPatientTableSourceReader {
    public CdsExportDocumentReviewSourceReader(CdsExportReviewerReadDao source) {
        super(
                reviewers(source, CdsExportPatientTableReadSupport.Plan.DOCUMENT_REVIEWS),
                CdsExportPatientTableReadSupport.Plan.DOCUMENT_REVIEWS,
                Set.of(CdsCategory.REPORTS));
    }

    @Override
    public Set<String> requiredTables() {
        return required();
    }

    /** The documents, their links and every reviewer capture read with them. */
    static Set<String> required() {
        var result = new HashSet<>(CdsExportDocumentRouteSourceReader.REVIEWER_TABLES);
        result.addAll(Set.of("document", "ctl_document"));
        return Set.copyOf(result);
    }
}
